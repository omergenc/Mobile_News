package com.omergenc.mobilenews.source

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

/** Minimal RSS 2.0 / RDF (RSS 1.0) / Atom parser. */
object FeedParser {
    data class Entry(val title: String, val link: String, val description: String, val date: Long?)

    private const val FEATURE_RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed"

    fun parse(bytes: ByteArray): List<Entry> {
        val out = mutableListOf<Entry>()
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        runCatching { p.setFeature(FEATURE_RELAXED, true) }
        // null encoding: the parser detects it from the BOM / XML prolog.
        p.setInput(ByteArrayInputStream(bytes), null)

        var inItem = false
        var title = ""
        var link = ""
        var desc = ""
        var date: Long? = null

        try {
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val name = p.name
                    if (name == "item" || name == "entry") {
                        inItem = true
                        title = ""; link = ""; desc = ""; date = null
                    } else if (inItem) {
                        when (name) {
                            "title" -> title = readText(p)
                            "link" -> {
                                val href = p.getAttributeValue(null, "href")
                                if (href != null) {
                                    val rel = p.getAttributeValue(null, "rel")
                                    if (link.isEmpty() || rel == null || rel == "alternate") link = href
                                } else {
                                    val text = readText(p).trim()
                                    if (text.isNotEmpty()) link = text
                                }
                            }
                            "guid", "id" -> {
                                val text = readText(p).trim()
                                if (link.isEmpty() && text.startsWith("http")) link = text
                            }
                            "description", "summary", "content:encoded", "content" -> {
                                val text = readText(p)
                                if (desc.isBlank()) desc = text
                            }
                            "pubDate", "published", "updated", "dc:date" -> {
                                val d = parseDate(readText(p))
                                if (date == null) date = d
                            }
                        }
                    }
                } else if (event == XmlPullParser.END_TAG && (p.name == "item" || p.name == "entry")) {
                    if (inItem && title.isNotBlank()) out += Entry(title, link, desc, date)
                    inItem = false
                }
                event = p.next()
            }
        } catch (e: Exception) {
            // Malformed feed: keep whatever was parsed before the error.
            if (out.isEmpty()) throw e
        }
        return out
    }

    /** Reads all text inside the current element; leaves the parser on its END_TAG. */
    private fun readText(p: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> p.text?.let { sb.append(it) }
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
        return sb.toString()
    }

    private val ISTANBUL: TimeZone = TimeZone.getTimeZone("Europe/Istanbul")
    private val FALLBACK_PATTERNS = listOf(
        "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm Z",
        "d MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd HH:mm:ss",
    )

    fun parseDate(raw: String): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        runCatching { return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(s).toEpochMilli() }
        for (pattern in FALLBACK_PATTERNS) {
            runCatching {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply { timeZone = ISTANBUL }
                return fmt.parse(s)!!.time
            }
        }
        return null
    }
}
