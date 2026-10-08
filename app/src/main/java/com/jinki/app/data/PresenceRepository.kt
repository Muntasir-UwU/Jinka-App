package com.jinki.app.data

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Presence smoke test.
 *
 * Plain language: this proves the whole chain works — sign-in, login
 * session, and database write permission. It signs in, double-checks the
 * app really holds a signed-in user, then writes a marker at
 * presence/{your uid}. No email, password, or uid is stored here; the
 * caller passes email and password in, and the uid comes from the sign-in.
 */
class PresenceRepository(
    private val auth: AuthRepository,
    private val database: FirebaseDatabase
) {

    /** Sign in, confirm the session, then write presence/{uid}. */
    suspend fun testPresence(email: String, password: String): Result<Unit> {
        val signIn = auth.signIn(email, password)
        if (signIn.isFailure) {
            return Result.failure(
                signIn.exceptionOrNull() ?: IllegalStateException("Sign-in failed.")
            )
        }
        val uid = auth.currentUid()
            ?: return Result.failure(
                IllegalStateException("Signed in, but no current user found.")
            )
        return try {
            database.getReference("presence").child(uid).setValue(true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
