package com.jinki.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.functions.FirebaseFunctions

/**
 * Single place that hands out Firebase entry points.
 *
 * Plain language: this object is the app's front door to Firebase.
 * Anything that needs auth, the realtime database, or cloud functions
 * takes what it needs from here instead of calling Firebase directly.
 */
object FirebaseModule {

    /** Signed-in account, or null when nobody is signed in. */
    val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * The realtime database from google-services.json (US region).
     * Disk persistence stays off (the default): the offline music library
     * already lives in Room, so this database is online-only here.
     */
    val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    /** Cloud Functions in us-central1, where verifySyncPassword lives. */
    val functions: FirebaseFunctions = FirebaseFunctions.getInstance("us-central1")
}
