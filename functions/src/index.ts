/**
 * Jinki Cloud Functions (Node 22, region us-central1).
 *
 * What these do, in plain language:
 * - verifySyncPassword: a signed-in user proves they know another user's
 *   sync password. If the password is right, the server writes a 1-hour
 *   permission slip (a "grant") so the viewer may read that user's playback.
 * - adminSetUserPassword: an admin sets (or resets) any user's sync
 *   password. The password itself is never stored — only a bcrypt hash.
 */

import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import * as bcrypt from "bcryptjs";

admin.initializeApp();

const REGION = "us-central1";

const BCRYPT_COST = 10;
const MIN_PASSWORD_LENGTH = 8;
// bcrypt silently ignores everything past 72 bytes, so a longer password
// would be checked as just its first 72 bytes. Reject those instead of
// accepting a password the user didn't quite type.
// Firebase user ids only contain letters, numbers, "-" and "_". This also
// blocks path tricks like "../" from ever reaching the database.
const MAX_PASSWORD_LENGTH = 72;
const UID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/;
// One precomputed dummy hash, made once when the server starts. Used only
// on the "this user never set a password" path so its timing matches a
// real compare without doing expensive work on every request.
const DUMMY_HASH = bcrypt.hashSync("jinki-no-password-set", BCRYPT_COST);
const MAX_FAILURES = 5; // lock out after this many wrong tries
const LOCKOUT_MS = 15 * 60 * 1000; // 15 minutes
const GRANT_TTL_MS = 60 * 60 * 1000; // grants last 1 hour

interface SyncAttempt {
  count?: number;
  lockedUntil?: number;
}

export const verifySyncPassword = onCall({ region: REGION }, async (request) => {
  try {
    // 1. Must be signed in. The viewer is ALWAYS the caller's own uid —
    //    we never accept a viewerUid from the client, so nobody can
    //    pretend to be someone else.
    const viewerUid = request.auth?.uid;
    if (!viewerUid) {
      throw new HttpsError("unauthenticated", "Sign in first.");
    }

    // 2. Validate inputs: both must be non-empty strings, and you cannot
    //    sync with yourself.
    const targetUid = request.data?.targetUid;
    const password = request.data?.password;
    if (
      typeof targetUid !== "string" ||
      targetUid.trim() === "" ||
      typeof password !== "string" ||
      password === ""
    ) {
      throw new HttpsError("invalid-argument", "A target user and a password are required.");
    }
    if (!UID_PATTERN.test(targetUid)) {
      throw new HttpsError("invalid-argument", "That user id doesn't look right.");
    }
    if (targetUid === viewerUid) {
      throw new HttpsError("invalid-argument", "You cannot sync with yourself.");
    }

    // Longer than 72 bytes can never match (see MAX_PASSWORD_LENGTH), so
    // fail here with the same generic error — before touching the database
    // at all.
    if (Buffer.byteLength(password, "utf8") > MAX_PASSWORD_LENGTH) {
      throw new HttpsError("permission-denied", "Incorrect password.");
    }

    const targetRef = admin.database().ref(`private/${targetUid}`);
    // Failures are counted separately for each viewer, so one user's wrong
    // tries can never lock another user out — your tries only affect you.
    const attemptsRef = targetRef.child(`attempts/${viewerUid}`);

    // 3. If this user never set a password, still run one bcrypt compare
    //    (against the precomputed dummy hash) so the timing doesn't reveal
    //    that — then fail closed with the same generic error as a wrong
    //    password, and write nothing to the database.
    const stored = await targetRef.child("hash").get();
    const storedHash = stored.val();
    if (typeof storedHash !== "string" || storedHash === "") {
      await bcrypt.compare(password, DUMMY_HASH);
      throw new HttpsError("permission-denied", "Incorrect password.");
    }

    // 4. Check the lockout. Too many wrong tries = wait 15 minutes. If a
    //    previous lockout has already expired, the counter starts over at
    //    zero — the next wrong try is failure 1, not failure 6.
    const attemptsSnap = await attemptsRef.get();
    const attempts = (attemptsSnap.val() as SyncAttempt | null) ?? {};
    if (typeof attempts.lockedUntil === "number" && attempts.lockedUntil > Date.now()) {
      throw new HttpsError(
        "failed-precondition",
        "Too many wrong tries. Wait a while and try again."
      );
    }

    // 5. Compare the password.
    const ok = await bcrypt.compare(password, storedHash);

    if (!ok) {
      // 6a. Wrong password: count it inside a transaction (safe when two
      //    tries land at the same moment), and lock out after 5 failures.
      //    An expired lockout resets the counter here too, so a retry of
      //    this transaction can't resurrect an old count.
      await attemptsRef.transaction((current) => {
        const prev = (current as SyncAttempt | null) ?? {};
        const expired =
          typeof prev.lockedUntil === "number" && prev.lockedUntil <= Date.now();
        const before = expired ? 0 : typeof prev.count === "number" ? prev.count : 0;
        const count = before + 1;
        const next: SyncAttempt = { count };
        if (count >= MAX_FAILURES) {
          next.lockedUntil = Date.now() + LOCKOUT_MS;
        }
        return next;
      });
      throw new HttpsError("permission-denied", "Incorrect password.");
    }

    // 6b. Right password: clear this viewer's failure counter and write a
    //    1-hour grant so the viewer may read this user's playback.
    await attemptsRef.remove();
    const expiresAt = Date.now() + GRANT_TTL_MS;
    await admin.database().ref(`syncGrants/${targetUid}/${viewerUid}`).set({ expiresAt });
    return { success: true, expiresAt };
  } catch (err) {
    if (err instanceof HttpsError) throw err;
    throw new HttpsError("internal", "Something went wrong. Try again.");
  }
});

export const adminSetUserPassword = onCall({ region: REGION }, async (request) => {
  try {
    // 1. Only admins. The `admin` flag is a custom claim put on the account
    //    by scripts/set-admin.js — clients cannot give it to themselves.
    if (request.auth?.token?.admin !== true) {
      throw new HttpsError("permission-denied", "Admins only.");
    }

    // 2. Validate inputs: a well-formed user id, and a password of at
    //    least 8 characters and at most 72 bytes (bcrypt can't see past
    //    72 bytes, so a longer password would be checked truncated).
    const uid = request.data?.uid;
    const password = request.data?.password;
    if (
      typeof uid !== "string" ||
      !UID_PATTERN.test(uid) ||
      typeof password !== "string" ||
      password.length < MIN_PASSWORD_LENGTH ||
      Buffer.byteLength(password, "utf8") > MAX_PASSWORD_LENGTH
    ) {
      throw new HttpsError(
        "invalid-argument",
        `A valid user id and a password of at least ${MIN_PASSWORD_LENGTH} characters (max ${MAX_PASSWORD_LENGTH} bytes) are required.`
      );
    }

    // 3. Hash (cost 10 ≈ a tenth of a second: fast for a rare admin call,
    //    slow enough to resist guessing) and store only the hash — never
    //    the password itself.
    const hash = await bcrypt.hash(password, BCRYPT_COST);
    await admin.database().ref(`private/${uid}/hash`).set(hash);
    // A new password means a clean slate: clear every viewer's failure
    // counter so nobody stays locked out by tries against the old password.
    await admin.database().ref(`private/${uid}/attempts`).remove();
    return { success: true };
  } catch (err) {
    if (err instanceof HttpsError) throw err;
    throw new HttpsError("internal", "Something went wrong. Try again.");
  }
});
