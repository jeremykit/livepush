package com.livepush.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class LivePushApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initTimber()
    }

    private fun initTimber() {
        if (com.livepush.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
