package com.example.thoughtvault

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class ThoughtVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (com.example.thoughtvault.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
