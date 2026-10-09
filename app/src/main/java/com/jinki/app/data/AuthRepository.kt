package com.jinki.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Sign-in state and sign-in actions.
 *
 * Plain language: this is the "who is using the app" helper. Screens watch
 * [currentUser] to know if someone is signed in; sign-in and sign-out go
 * through here. No email, password, or uid is stored in this file — they
 * are always passed in as parameters by the caller.
 */
class AuthRepository(private val auth: FirebaseAuth) {

    /** The signed-in user, or null. Updates itself on sign in/out. */
    val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** The signed-in user's id right now, or null when signed out. */
    fun currentUid(): String? = auth.currentUser?.uid

    /** Sign in with email + password. Never stores the credentials. */
    suspend fun signIn(email: String, password: String): Result<FirebaseUser> {
        return try {
            val user = auth.signInWithEmailAndPassword(email, password).await().user
                ?: return Result.failure(IllegalStateException("Sign-in succeeded but returned no user."))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() = auth.signOut()

    /**
     * Return a signed-in user, signing in anonymously first if needed.
     * Retries after a short delay until it works — never throws, never
     * logs tokens or ids. Callers await this before any database write.
     */
    suspend fun ensureSignedIn(): FirebaseUser {
        auth.currentUser?.let { return it }
        while (true) {
            try {
                auth.signInAnonymously().await().user?.let { return it }
            } catch (e: CancellationException) {
                throw e // scope went away: stop retrying, don't swallow it
            } catch (_: Exception) { /* retry below */ }
            delay(SIGN_IN_RETRY_MS)
        }
    }

    companion object {
        private const val SIGN_IN_RETRY_MS = 5_000L
    }
}
