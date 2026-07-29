package com.huqi.delayedsub

import android.app.Application
import com.huqi.delayedsub.di.AppContainer

class DelayedSubApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
