package com.example.nsense

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize

class NSenseApp : Application() {

    override fun onCreate() {
        super.onCreate()

        Firebase.initialize(context = this)

        // DEBUG ONLY: lets Firebase trust this test build.
        // Must be replaced with a production provider (Play Integrity) before real release.
        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )

        NotificationHelper.createChannel(this)
    }
}