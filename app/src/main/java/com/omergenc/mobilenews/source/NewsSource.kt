package com.omergenc.mobilenews.source

import com.omergenc.mobilenews.data.AppSettings
import com.omergenc.mobilenews.model.NewsItem
import com.omergenc.mobilenews.model.SourceConfig
import okhttp3.OkHttpClient

class SourceContext(
    val http: OkHttpClient,
    val settings: AppSettings,
    val now: Long,
)

/**
 * A pluggable news source. To add a new platform (e.g. X API, Instagram Business
 * Discovery, or a future backend), implement this interface and register the
 * type string in [SourceRegistry].
 */
interface NewsSource {
    val config: SourceConfig

    /** Returns recent items. Throw on failure; the error is shown in the app. */
    suspend fun fetch(ctx: SourceContext): List<NewsItem>
}

object SourceRegistry {
    fun create(config: SourceConfig): NewsSource? = when (config.type) {
        "rss" -> RssSource(config)
        "bluesky" -> BlueskySource(config)
        "youtube" -> YouTubeSource(config)
        "telegram" -> TelegramSource(config)
        "x" -> XSource(config)
        "instagram" -> InstagramSource(config)
        else -> null
    }
}

/** Placeholder: X API is paid; planned for a later version. */
class XSource(override val config: SourceConfig) : NewsSource {
    override suspend fun fetch(ctx: SourceContext): List<NewsItem> =
        throw UnsupportedOperationException("X kaynağı henüz desteklenmiyor")
}

/** Placeholder: Instagram Business Discovery API; planned for a later version. */
class InstagramSource(override val config: SourceConfig) : NewsSource {
    override suspend fun fetch(ctx: SourceContext): List<NewsItem> =
        throw UnsupportedOperationException("Instagram kaynağı henüz desteklenmiyor")
}
