package com.omergenc.mobilenews.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omergenc.mobilenews.NewsApp
import com.omergenc.mobilenews.model.SourceConfig
import com.omergenc.mobilenews.model.Story
import com.omergenc.mobilenews.work.RefreshScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NewsApp
    private val repository = app.repository
    val settings = app.settings

    val store = repository.store

    val stories: StateFlow<List<Story>> = repository.store
        .map { repository.buildStories(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val sources: List<SourceConfig> get() = repository.sources

    init {
        if (System.currentTimeMillis() - repository.store.value.lastRefresh > STALE_MS) refresh()
    }

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                repository.refresh()
            } catch (e: Exception) {
                // Errors per source are stored in the store; nothing else to surface here.
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun saveSettings(
        anthropicKey: String,
        youtubeKey: String,
        telegramBridge: String,
        notifications: Boolean,
        intervalMinutes: Int,
    ) {
        val intervalChanged = settings.refreshIntervalMinutes != intervalMinutes
        settings.anthropicApiKey = anthropicKey
        settings.youtubeApiKey = youtubeKey
        settings.telegramBridgeUrl = telegramBridge
        settings.notificationsEnabled = notifications
        settings.refreshIntervalMinutes = intervalMinutes
        if (intervalChanged) RefreshScheduler.schedule(app, intervalMinutes, replaceExisting = true)
    }

    companion object {
        private const val STALE_MS = 5L * 60 * 1000
    }
}
