package com.omergenc.mobilenews.processing

import com.omergenc.mobilenews.model.NewsItem
import kotlin.math.max
import kotlin.math.min

/**
 * Groups items that describe the same event by title similarity. Titles are reduced to
 * a set of crude Turkish "stems" (first 6 letters of each content word), which copes
 * reasonably with suffixes ("Galatasaray'ın", "Galatasaray'dan").
 */
object Clusterer {
    private const val THRESHOLD = 0.45
    private const val MAX_GAP_MS = 18L * 60 * 60 * 1000

    private val STOP = setOf(
        "ve", "ile", "bir", "bu", "da", "de", "icin", "olarak", "gibi", "daha", "en", "mi", "mu",
        "ne", "su", "cok", "son", "dakika", "sondakika", "haber", "haberi", "haberler", "video",
        "izle", "yeni", "iste", "nasil", "neden", "kim", "oldu", "olan", "olacak", "etti", "dedi",
        "aciklama", "acikladi", "ilk", "sonra", "once", "kadar", "var", "yok", "ama", "veya",
        "her", "tum", "gore", "bugun", "dun", "yarin", "saat", "the", "and",
    )

    fun signature(title: String): Set<String> =
        words(title).filter { it.length >= 3 && it !in STOP }.map { it.take(6) }.toSet()

    fun similarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.count { it in b }
        if (inter < 2) return 0.0
        val jaccard = inter.toDouble() / (a.size + b.size - inter)
        val overlap = inter.toDouble() / min(a.size, b.size)
        return if (inter >= 3) max(jaccard, overlap * 0.8) else jaccard
    }

    /** Returns clusters; each cluster is ordered oldest first. */
    fun cluster(items: List<NewsItem>): List<List<NewsItem>> {
        val clusters = mutableListOf<MutableList<Pair<NewsItem, Set<String>>>>()
        for (item in items.sortedBy { it.publishedAt }) {
            val sig = signature(item.title)
            var bestIndex = -1
            var bestScore = 0.0
            for ((index, cluster) in clusters.withIndex()) {
                if (item.publishedAt - cluster.last().first.publishedAt > MAX_GAP_MS) continue
                for ((_, memberSig) in cluster) {
                    val s = similarity(sig, memberSig)
                    if (s > bestScore) {
                        bestScore = s
                        bestIndex = index
                    }
                }
            }
            if (bestIndex >= 0 && bestScore >= THRESHOLD) {
                clusters[bestIndex].add(item to sig)
            } else {
                clusters.add(mutableListOf(item to sig))
            }
        }
        return clusters.map { c -> c.map { it.first } }
    }
}
