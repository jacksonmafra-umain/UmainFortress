package com.umain.fortress

import android.app.Application
import com.umain.fortress.di.appModule
import com.umain.fortress.di.authModule
import com.umain.fortress.di.networkModule
import com.umain.fortress.di.securityModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber

class FortressApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.ERROR)
            androidContext(this@FortressApplication)
            modules(appModule, securityModule, networkModule, authModule)
        }
    }
}
