package com.omergenc.mobilenews

import android.app.Application
import com.omergenc.mobilenews.data.AppSettings
import com.omergenc.mobilenews.data.NewsRepository
import com.omergenc.mobilenews.work.Notifier
import com.omergenc.mobilenews.work.RefreshScheduler

class NewsApp : Application() {
    lateinit var settings: AppSettings
        private set

    val repository: NewsRepository by lazy { NewsRepository(this, settings) }

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        Notifier.createChannel(this)
        RefreshScheduler.schedule(this, settings.refreshIntervalMinutes, replaceExisting = false)
    }
}
