package com.omergenc.mobilenews.processing

import com.omergenc.mobilenews.model.Category
import com.omergenc.mobilenews.model.NewsItem
import com.omergenc.mobilenews.model.SourceConfig

/** Lower-cases and folds Turkish letters to ASCII so "Maç", "MAÇ", "mac" all match. */
fun foldTurkish(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) {
        sb.append(
            when (c) {
                'İ', 'I', 'ı', 'Î', 'î' -> 'i'
                'Ç', 'ç' -> 'c'
                'Ğ', 'ğ' -> 'g'
                'Ö', 'ö' -> 'o'
                'Ş', 'ş' -> 's'
                'Ü', 'ü', 'Û', 'û' -> 'u'
                'Â', 'â' -> 'a'
                else -> c.lowercaseChar()
            }
        )
    }
    return sb.toString()
}

private val NON_WORD = Regex("[^a-z0-9]+")

fun words(s: String): List<String> = foldTurkish(s).split(NON_WORD).filter { it.isNotEmpty() }

/**
 * Keyword rules. A keyword matches at the start of a word ("enflasyon" matches
 * "enflasyonu"); a trailing '$' requires the whole word ("kur$" does not match "kurul").
 */
object Categorizer {
    private val EKONOMI = listOf(
        "enflasyon", "faiz", "merkez bankas", "tcmb", "dolar", "euro$", "avro", "kur$", "kurlar",
        "borsa", "bist", "ihracat", "ithalat", "buyume", "gsyh", "asgari ucret", "vergi", "zam$",
        "zamm", "akaryakit", "benzin", "motorin", "emekli", "ekonomi", "piyasa", "altin$",
        "altin fiyat", "gram altin", "kredi", "issizlik", "butce", "hazine", "tuik", "fiyat",
        "kira$", "kiralar", "rezerv", "cari acik", "mehmet simsek", "yatirim", "sirket", "iflas",
        "konkordato", "tahvil", "eurobond", "doviz", "maas", "ucret", "ihale", "banka",
    )
    private val SIYASET = listOf(
        "erdogan", "akp$", "ak parti", "chp$", "mhp$", "dem parti", "iyi parti", "ozgur ozel",
        "imamoglu", "bahceli", "tbmm", "meclis", "secim", "bakan", "cumhurbaskan", "muhalefet",
        "iktidar", "gozalti", "tutukla", "mahkeme", "anayasa", "belediye baskan", "kayyum",
        "kayyim", "milletvekil", "yasa teklif", "kanun teklif", "savci", "hukumet", "siyas",
        "protesto", "ysk$", "disisleri", "iddianame", "durusma", "yargitay", "danistay",
        "valilik", "sozcu chp", "genel baskan",
    )
    private val SPOR = listOf(
        "mac$", "maci$", "macta$", "mactan$", "maca$", "gol$", "golu$", "goller", "futbol",
        "super lig", "transfer", "teknik direktor", "basketbol", "voleybol", "sampiyonlar ligi",
        "avrupa ligi", "konferans ligi", "uefa", "fifa", "fenerbahce", "besiktas", "trabzonspor",
        "milli takim", "derbi", "hakem", "tff$", "puan", "stadyum", "forvet", "kaleci",
        "antrenor", "futbolcu", "lig$", "ligi$", "sampiyon", "kadro",
    )
    private val GALATASARAY = listOf(
        "galatasaray", "cimbom", "gs$", "okan buruk", "ali sami yen", "rams park", "dursun ozbek",
        "icardi", "osimhen", "sari kirmizili",
    )

    private fun countHits(padded: String, keywords: List<String>): Int = keywords.count { kw ->
        if (kw.endsWith("$")) padded.contains(" ${kw.dropLast(1)} ") else padded.contains(" $kw")
    }

    private fun pad(s: String) = " " + words(s).joinToString(" ") + " "

    fun isGalatasaray(text: String): Boolean = countHits(pad(text), GALATASARAY) > 0

    fun categorize(item: NewsItem, config: SourceConfig?): NewsItem {
        val title = pad(item.title)
        val body = pad(item.snippet)
        fun score(list: List<String>) = countHits(title, list) * 2 + countHits(body, list)

        val gs = config?.galatasaray == true || countHits(title, GALATASARAY) + countHits(body, GALATASARAY) > 0
        val scores = mutableMapOf(
            Category.EKONOMI to score(EKONOMI),
            Category.SIYASET to score(SIYASET),
            Category.SPOR to score(SPOR),
        )
        config?.categoryHint?.let { hint -> if (hint != Category.GENEL) scores[hint] = (scores[hint] ?: 0) + 2 }

        val category = when {
            gs -> Category.SPOR
            else -> scores.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: Category.GENEL
        }
        return item.copy(category = category, galatasaray = gs)
    }
}
