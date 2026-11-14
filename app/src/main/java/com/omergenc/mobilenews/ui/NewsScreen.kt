package com.omergenc.mobilenews.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omergenc.mobilenews.model.Category
import com.omergenc.mobilenews.model.Story
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FeedTab(val label: String, val matches: (Story) -> Boolean) {
    ALL("Tümü", { true }),
    ECONOMY("Ekonomi", { it.category == Category.EKONOMI }),
    POLITICS("Siyaset", { it.category == Category.SIYASET }),
    GALATASARAY("Galatasaray", { it.galatasaray }),
    SPORTS("Spor", { it.category == Category.SPOR }),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(vm: MainViewModel, onOpenSettings: () -> Unit) {
    val stories by vm.stories.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val store by vm.store.collectAsStateWithLifecycle()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = FeedTab.entries[tabIndex]
    val visible = remember(stories, tab) { stories.filter(tab.matches) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Haber Derleyici") },
                actions = {
                    IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Yenile") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Ayarlar") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 8.dp) {
                FeedTab.entries.forEachIndexed { index, t ->
                    Tab(selected = index == tabIndex, onClick = { tabIndex = index }, text = { Text(t.label) })
                }
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (store.lastRefresh > 0) {
                        item(key = "status") {
                            Text(
                                "Son tarama: ${formatTime(store.lastRefresh)}" +
                                    if (store.lastErrors.isNotEmpty()) " · ${store.lastErrors.size} kaynakta hata" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    if (visible.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                if (refreshing) "Kaynaklar taranıyor…" else "Bu kategoride haber yok. Yenilemek için aşağı çekin.",
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    items(visible, key = { it.id }) { story -> StoryCard(story) }
                }
            }
        }
    }
}

@Composable
private fun StoryCard(story: Story) {
    val uriHandler = LocalUriHandler.current
    var expanded by rememberSaveable(story.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(
                    if (story.galatasaray) "Galatasaray" else story.category.label,
                    colors.primaryContainer,
                    colors.onPrimaryContainer,
                )
                if (story.unconfirmed) {
                    Pill("Teyit edilmedi", colors.errorContainer, colors.onErrorContainer)
                } else {
                    Pill("${story.outletCount} kaynak", colors.secondaryContainer, colors.onSecondaryContainer)
                }
                Spacer(Modifier.weight(1f))
                Text(relativeTime(story.latestAt), style = MaterialTheme.typography.labelSmall, color = colors.outline)
            }

            Text(
                story.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )

            val body = story.summary ?: story.items.first().snippet.takeIf { it != story.title }.orEmpty()
            if (body.isNotBlank()) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (story.summary != null) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (story.summary != null) {
                Text(
                    "Claude Haiku özeti — kaynaklardan kontrol edin",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Text(
                "Kaynaklar",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
            )
            val shown = if (expanded) story.items else story.items.take(3)
            shown.forEach { item ->
                Text(
                    "• ${item.outlet}: ${item.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = item.url.isNotBlank()) { runCatching { uriHandler.openUri(item.url) } }
                        .padding(vertical = 4.dp),
                )
            }
            if (story.items.size > 3) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Daha az göster" else "+${story.items.size - 3} kaynak daha")
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, background: Color, foreground: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = foreground,
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("dd.MM HH:mm", Locale("tr")).format(Date(millis))

private fun relativeTime(millis: Long): String {
    val minutes = (System.currentTimeMillis() - millis) / 60_000
    return when {
        minutes < 1 -> "az önce"
        minutes < 60 -> "$minutes dk önce"
        minutes < 24 * 60 -> "${minutes / 60} sa önce"
        else -> formatTime(millis)
    }
}
