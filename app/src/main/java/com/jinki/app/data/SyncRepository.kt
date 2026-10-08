package com.jinki.app.data

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * Talks to the verifySyncPassword Cloud Function.
 *
 * Plain language: when you want to see someone else's playback, this sends
 * their user id plus the password you typed to the server. The server checks
 * it and hands back an expiry time. Your own id is never sent — the server
 * already knows who you are from your sign-in.
 */
class SyncRepository(private val functions: FirebaseFunctions) {

    /**
     * Ask the server to verify [password] for [targetUid].
     * @return success with the grant's expiry time (millis since epoch),
     * or failure with a human-readable message.
     */
    suspend fun verifySyncPassword(targetUid: String, password: String): Result<Long> {
        return try {
            val result = functions
                .getHttpsCallable("verifySyncPassword")
                .call(mapOf("targetUid" to targetUid, "password" to password))
                .await()
            val data = result.data as? Map<*, *>
            val expiresAt = (data?.get("expiresAt") as? Number)?.toLong()
                ?: return Result.failure(
                    IllegalStateException("Server returned no expiry time.")
                )
            Result.success(expiresAt)
        } catch (e: FirebaseFunctionsException) {
            val message = when (e.code) {
                FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                    "Incorrect password."
                FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                    "Too many wrong tries. Wait a while and try again."
                FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                    "Sign in first."
                else -> e.message ?: "Something went wrong. Try again."
            }
            Result.failure(Exception(message, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
