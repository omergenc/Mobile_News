package com.omergenc.mobilenews.source

import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object Http {
    private const val USER_AGENT = "MobileNews/0.1 (Android; personal news reader)"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun getBytes(client: OkHttpClient, url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    suspend fun getString(client: OkHttpClient, url: String): String =
        getBytes(client, url).toString(Charsets.UTF_8)
}

fun stableId(vararg parts: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(parts.joinToString("|").toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(16)
}

private val WHITESPACE = Regex("\\s+")

fun stripHtml(input: String): String {
    if (input.isBlank()) return ""
    val capped = if (input.length > 6000) input.take(6000) else input
    return HtmlCompat.fromHtml(capped, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace('￼', ' ')
        .replace(WHITESPACE, " ")
        .trim()
}
