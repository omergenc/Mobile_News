package com.omergenc.mobilenews.source

import com.omergenc.mobilenews.model.NewsItem
import com.omergenc.mobilenews.model.SourceConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

private val json = Json { ignoreUnknownKeys = true }

private fun JsonElement?.obj(key: String): JsonElement? = (this as? JsonObject)?.get(key)
private fun JsonElement?.str(key: String): String? = (obj(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun JsonElement?.arr(key: String): List<JsonElement> = (obj(key) as? JsonArray) ?: emptyList()

private fun oneLine(text: String, max: Int): String {
    val flat = text.replace(Regex("\\s+"), " ").trim()
    return if (flat.length <= max) flat else flat.take(max - 1).trimEnd() + "…"
}

/**
 * Bluesky via the public AppView (no login).
 * - `actor`: an account's own posts (public.api.bsky.app getAuthorFeed)
 * - `query`: Turkish-language search (api.bsky.app searchPosts; public.api returns 403)
 */
class BlueskySource(override val config: SourceConfig) : NewsSource {
    override suspend fun fetch(ctx: SourceContext): List<NewsItem> {
        val posts: List<JsonElement>
        val searchMode: Boolean
        if (!config.actor.isNullOrBlank()) {
            val url = "https://public.api.bsky.app/xrpc/app.bsky.feed.getAuthorFeed".toHttpUrl().newBuilder()
                .addQueryParameter("actor", config.actor)
                .addQueryParameter("limit", "30")
                .addQueryParameter("filter", "posts_no_replies")
                .build().toString()
            val root = json.parseToJsonElement(Http.getString(ctx.http, url))
            posts = root.arr("feed").mapNotNull { it.obj("post") }
            searchMode = false
        } else if (!config.query.isNullOrBlank()) {
            val url = "https://api.bsky.app/xrpc/app.bsky.feed.searchPosts".toHttpUrl().newBuilder()
                .addQueryParameter("q", config.query)
                .addQueryParameter("lang", "tr")
                .addQueryParameter("sort", "latest")
                .addQueryParameter("limit", "30")
                .build().toString()
            val root = json.parseToJsonElement(Http.getString(ctx.http, url))
            posts = root.arr("posts")
            searchMode = true
        } else {
            throw IOException("'actor' veya 'query' tanımlı değil")
        }

        return posts.mapNotNull { post ->
            val uri = post.str("uri") ?: return@mapNotNull null
            val handle = post.obj("author").str("handle") ?: return@mapNotNull null
            val text = post.obj("record").str("text").orEmpty()
            if (text.isBlank()) return@mapNotNull null
            val created = post.obj("record").str("createdAt") ?: post.str("indexedAt")
            val rkey = uri.substringAfterLast('/')
            NewsItem(
                id = stableId(config.id, uri),
                sourceId = config.id,
                // Search results from random accounts count as one outlet so they cannot
                // "confirm" each other; a followed account is its own outlet.
                outlet = if (searchMode) config.name else "Bluesky @$handle",
                title = oneLine(text, 160),
                snippet = oneLine(text, 400),
                url = "https://bsky.app/profile/$handle/post/$rkey",
                publishedAt = (created?.let { FeedParser.parseDate(it) } ?: ctx.now).coerceAtMost(ctx.now),
                fetchedAt = ctx.now,
                social = true,
            )
        }
    }
}

/**
 * YouTube Data API v3. Reads a channel's uploads playlist (UC… -> UU…), which costs
 * 1 quota unit per call instead of 100 for search.
 */
class YouTubeSource(override val config: SourceConfig) : NewsSource {
    override suspend fun fetch(ctx: SourceContext): List<NewsItem> {
        val key = ctx.settings.youtubeApiKey.trim()
        if (key.isEmpty()) throw IOException("Ayarlardan YouTube API anahtarı girilmedi")
        val channelId = config.channelId ?: throw IOException("'channelId' tanımlı değil")
        if (!channelId.startsWith("UC")) throw IOException("channelId 'UC' ile başlamalı")
        val url = "https://www.googleapis.com/youtube/v3/playlistItems".toHttpUrl().newBuilder()
            .addQueryParameter("part", "snippet")
            .addQueryParameter("maxResults", "15")
            .addQueryParameter("playlistId", "UU" + channelId.removePrefix("UC"))
            .addQueryParameter("key", key)
            .build().toString()
        val root = json.parseToJsonElement(Http.getString(ctx.http, url))
        return root.arr("items").mapNotNull { item ->
            val snippet = item.obj("snippet")
            val title = snippet.str("title") ?: return@mapNotNull null
            val videoId = snippet.obj("resourceId").str("videoId") ?: return@mapNotNull null
            NewsItem(
                id = stableId(config.id, videoId),
                sourceId = config.id,
                outlet = config.name,
                title = oneLine(title, 200),
                snippet = oneLine(snippet.str("description").orEmpty(), 400),
                url = "https://www.youtube.com/watch?v=$videoId",
                publishedAt = (snippet.str("publishedAt")?.let { FeedParser.parseDate(it) } ?: ctx.now)
                    .coerceAtMost(ctx.now),
                fetchedAt = ctx.now,
            )
        }
    }
}
