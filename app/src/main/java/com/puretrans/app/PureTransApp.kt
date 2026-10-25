package com.puretrans.app

import android.app.Application
import com.puretrans.app.data.engine.SmartTranslationRouter
import com.puretrans.app.data.local.HistoryDatabase
import com.puretrans.app.data.local.SettingsManager

class PureTransApp : Application() {

    lateinit var settingsManager: SettingsManager
        private set

    lateinit var historyDatabase: HistoryDatabase
        private set

    lateinit var router: SmartTranslationRouter
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsManager = SettingsManager.getInstance(this)
        historyDatabase = HistoryDatabase.getInstance(this)
        router = SmartTranslationRouter(this, settingsManager, historyDatabase)
    }

    companion object {
        lateinit var instance: PureTransApp
            private set
    }
}
