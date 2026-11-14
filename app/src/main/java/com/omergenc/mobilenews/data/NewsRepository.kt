package com.omergenc.mobilenews.data

import android.content.Context
import android.util.Log
import com.omergenc.mobilenews.model.Category
import com.omergenc.mobilenews.model.NewsItem
import com.omergenc.mobilenews.model.NewsStore
import com.omergenc.mobilenews.model.SourceConfig
import com.omergenc.mobilenews.model.SourcesFile
import com.omergenc.mobilenews.model.Story
import com.omergenc.mobilenews.model.StoredSummary
import com.omergenc.mobilenews.processing.Categorizer
import com.omergenc.mobilenews.processing.Clusterer
import com.omergenc.mobilenews.source.Http
import com.omergenc.mobilenews.source.SourceContext
import com.omergenc.mobilenews.source.SourceRegistry
import com.omergenc.mobilenews.summary.ClaudeSummarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.File

class NewsRepository(
    private val context: Context,
    private val settings: AppSettings,
) {
    data class RefreshResult(
        val newItems: Int,
        val errors: List<String>,
        /** Important stories not notified before; the caller decides whether to notify. */
        val important: List<Story>,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val storeFile = File(context.filesDir, "news_store.json")
    private val mutex = Mutex()
    private val summarizer = ClaudeSummarizer()

    private val _store = MutableStateFlow(loadStore())
    val store: StateFlow<NewsStore> = _store.asStateFlow()

    val sources: List<SourceConfig> by lazy {
        val text = context.assets.open("sources.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        json.decodeFromString(SourcesFile.serializer(), text).sources
    }

    private fun loadStore(): NewsStore = try {
        if (storeFile.exists()) json.decodeFromString(NewsStore.serializer(), storeFile.readText()) else NewsStore()
    } catch (e: Exception) {
        Log.w(TAG, "Store unreadable, starting empty", e)
        NewsStore()
    }

    private fun saveStore(store: NewsStore) {
        val tmp = File(storeFile.parentFile, storeFile.name + ".tmp")
        tmp.writeText(json.encodeToString(NewsStore.serializer(), store))
        if (!tmp.renameTo(storeFile)) {
            storeFile.delete()
            tmp.renameTo(storeFile)
        }
    }

    suspend fun refresh(): RefreshResult = mutex.withLock {
        withContext(Dispatchers.IO) { doRefresh() }
    }

    private suspend fun doRefresh(): RefreshResult {
        val now = System.currentTimeMillis()
        val ctx = SourceContext(Http.client, settings, now)
        val enabled = sources.filter { settings.isSourceEnabled(it) }
        val configById = sources.associateBy { it.id }
        val errors = mutableListOf<String>()

        val fetched: List<Pair<SourceConfig, Result<List<NewsItem>>>> = coroutineScope {
            enabled.map { cfg ->
                async {
                    val source = SourceRegistry.create(cfg)
                    val result: Result<List<NewsItem>> = if (source == null) {
                        Result.failure(IllegalArgumentException("bilinmeyen kaynak türü '${cfg.type}'"))
                    } else {
                        try {
                            withTimeoutOrNull(FETCH_TIMEOUT_MS) { source.fetch(ctx) }
                                ?.let { Result.success(it) }
                                ?: Result.failure(java.io.IOException("zaman aşımı"))
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }
                    cfg to result
                }
            }.awaitAll()
        }

        val old = _store.value
        val byId = LinkedHashMap<String, NewsItem>()
        old.items.forEach { byId[it.id] = it }
        var newCount = 0
        for ((cfg, result) in fetched) {
            result.onSuccess { items ->
                for (item in items) {
                    if (item.id !in byId) {
                        byId[item.id] = Categorizer.categorize(item, cfg)
                        newCount++
                    }
                }
            }.onFailure { e ->
                errors += "${cfg.name}: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        val cutoff = now - RETENTION_MS
        val items = byId.values
            .filter { it.publishedAt >= cutoff }
            .sortedByDescending { it.publishedAt }
            .take(MAX_ITEMS)

        var summaries = old.summaries
        var stories = buildStories(items, summaries)

        // Optional LLM summaries for the most relevant stories that lack an up-to-date one.
        val apiKey = settings.anthropicApiKey
        if (apiKey.isNotBlank()) {
            val candidates = stories
                .filter { it.latestAt >= now - SUMMARY_WINDOW_MS }
                .filter { s -> findSummary(s.items, summaries)?.let { it.itemCount < s.items.size } ?: true }
                .sortedWith(compareByDescending<Story> { it.outletCount }.thenByDescending { it.latestAt })
                .take(MAX_SUMMARIES_PER_REFRESH)
            val fresh = mutableListOf<StoredSummary>()
            for (story in candidates) {
                try {
                    val r = summarizer.summarize(apiKey, story.items) ?: continue
                    fresh += StoredSummary(
                        memberIds = story.items.map { it.id }.toSet(),
                        text = r.text,
                        category = r.category,
                        galatasaray = r.galatasaray,
                        itemCount = story.items.size,
                        createdAt = now,
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errors += "Claude özeti: ${e.message ?: e.javaClass.simpleName}"
                    break // don't hammer the API after an auth / quota error
                }
            }
            if (fresh.isNotEmpty()) {
                val replacedIds = fresh.flatMap { it.memberIds }.toSet()
                summaries = summaries.filter { s -> s.memberIds.none { it in replacedIds } } + fresh
                stories = buildStories(items, summaries)
            }
        }

        // Drop summaries whose items have all expired.
        val liveIds = items.map { it.id }.toSet()
        summaries = summaries.filter { s -> s.memberIds.any { it in liveIds } }

        val important = stories.filter { s ->
            s.latestAt >= now - NOTIFY_WINDOW_MS &&
                (s.outletCount >= 3 || (s.galatasaray && s.outletCount >= 2)) &&
                s.id !in old.notified
        }
        // First run: mark everything as seen instead of flooding notifications.
        val toNotify = if (old.lastRefresh == 0L) emptyList() else important
        val storyIds = stories.map { it.id }.toSet()
        val notified = (old.notified.filter { it in storyIds } + important.map { it.id }).toSet()

        val newStore = NewsStore(
            items = items,
            summaries = summaries,
            notified = notified,
            lastRefresh = now,
            lastErrors = errors,
        )
        saveStore(newStore)
        _store.value = newStore
        return RefreshResult(newCount, errors, toNotify)
    }

    private fun findSummary(items: List<NewsItem>, summaries: List<StoredSummary>): StoredSummary? {
        val ids = items.map { it.id }.toSet()
        return summaries
            .map { s -> s to s.memberIds.count { it in ids } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    fun buildStories(store: NewsStore): List<Story> = buildStories(store.items, store.summaries)

    private fun buildStories(items: List<NewsItem>, summaries: List<StoredSummary>): List<Story> =
        Clusterer.cluster(items).map { members ->
            val summary = findSummary(members, summaries)
            val headline = members.firstOrNull { !it.social } ?: members.first()
            val gs = summary?.galatasaray == true || members.any { it.galatasaray }
            val category = when {
                gs -> Category.SPOR
                summary?.category != null -> summary.category
                else -> members.groupingBy { it.category }.eachCount()
                    .entries
                    .sortedWith(compareByDescending<Map.Entry<Category, Int>> { it.value }
                        .thenBy { if (it.key == Category.GENEL) 1 else 0 })
                    .first().key
            }
            Story(
                id = members.first().id,
                title = headline.title,
                summary = summary?.text,
                category = category,
                galatasaray = gs,
                items = members.sortedByDescending { it.publishedAt },
                outletCount = members.map { it.outlet }.distinct().size,
                firstAt = members.minOf { it.publishedAt },
                latestAt = members.maxOf { it.publishedAt },
            )
        }.sortedByDescending { it.latestAt }

    companion object {
        private const val TAG = "NewsRepository"
        private const val FETCH_TIMEOUT_MS = 45_000L
        private const val RETENTION_MS = 48L * 60 * 60 * 1000
        private const val SUMMARY_WINDOW_MS = 24L * 60 * 60 * 1000
        private const val NOTIFY_WINDOW_MS = 3L * 60 * 60 * 1000
        private const val MAX_ITEMS = 1500
        private const val MAX_SUMMARIES_PER_REFRESH = 12
    }
}
