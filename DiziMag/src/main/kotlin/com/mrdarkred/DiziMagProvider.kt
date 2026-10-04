@file:Suppress("DEPRECATION_ERROR", "DEPRECATION")
package com.mrdarkred

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

/**
 * DiziMag Provider (ddizimag.com)
 *
 * URL DEĞİŞTİĞİNDE: Sadece mainUrl'i güncelle, sonra git push yap.
 */
class DiziMagProvider : MainAPI() {

    // ================================================================
    // BURASI DEĞİŞEBİLİR — Site domain değişirse sadece bunu güncelle
    override var mainUrl = "https://ddizimag.com"
    // ================================================================

    override var name           = "DiziMag"
    override var lang           = "tr"
    override val hasMainPage    = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/"                  to "Son Eklenen",
        "$mainUrl/diziler"           to "Tüm Diziler",
        "$mainUrl/tur/aksiyon"       to "Aksiyon",
        "$mainUrl/tur/bilim-kurgu"   to "Bilim-Kurgu",
        "$mainUrl/tur/komedi"        to "Komedi",
        "$mainUrl/tur/dram"          to "Dram",
        "$mainUrl/tur/korku"         to "Korku",
        "$mainUrl/tur/romantik"      to "Romantik",
        "$mainUrl/tur/animasyon"     to "Animasyon",
        "$mainUrl/tur/suc"           to "Suç"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page > 1) {
            if (request.data.contains("?")) "${request.data}&page=$page" else "${request.data}?page=$page"
        } else {
            request.data
        }
        val document = app.get(pageUrl).document

        val items = document.select(
            "a.group:has(img), a[href*='/dizi/']:has(img), div.dizi-item, article.dizi"
        ).mapNotNull { it.toSearchResponse() }.distinctBy { it.url }

        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = java.net.URLEncoder.encode(query, "utf-8")
        val document = app.get("$mainUrl/ara?q=$encodedQuery").document
        return document.select(
            "a.group:has(img), a[href*='/dizi/']:has(img)"
        ).mapNotNull { it.toSearchResponse() }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.aspect-\\[2\\/3\\] img, img[alt*='$title'], div.poster img, img")?.attr("src"))
        val description = document.selectFirst("p.leading-relaxed, div.dizi-aciklama p, div.entry-content p, .plot, .ozet")?.text()?.trim()
        val year = document.selectFirst("span.yil, span.yapim-yili, .year, p.text-content-muted")
            ?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
        val tags = document.select("a[href*='/tur/']").map { it.text().trim() }

        val episodes = mutableListOf<Episode>()
        document.select("a[href*='/sezon-'][href*='/bolum-']").forEach { ep ->
            val epUrl = fixUrlNull(ep.attr("href")) ?: return@forEach
            val epText = ep.text().trim()
            val season = Regex("""(?i)(\d+)\.\s*[Ss]ezon|[Ss]ezon\s*(\d+)|[Ss](\d+)""").find(epUrl)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull()
                ?: Regex("""(?i)(\d+)\.\s*[Ss]ezon""").find(epText)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val episode = Regex("""(?i)(\d+)\.\s*[Bb]ölüm|[Bb]olum\s*(\d+)|[Ee](\d+)""").find(epUrl)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull()
                ?: Regex("""(?i)(\d+)\.\s*[Bb]ölüm""").find(epText)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val name = epText.replace(Regex("""(?i)\d+\.\s*Sezon\s*\d+\.\s*Bölüm"""), "").trim().ifEmpty { epText }

            episodes.add(newEpisode(epUrl) {
                this.season = season
                this.episode = episode
                this.name = name
            })
        }

        document.select("a[href*='?sezon=']").forEach { sTab ->
            val sUrl = fixUrlNull(sTab.attr("href")) ?: return@forEach
            if (sUrl == url || sUrl.substringBefore("#") == url.substringBefore("#")) return@forEach
            try {
                val sDoc = app.get(sUrl).document
                sDoc.select("a[href*='/sezon-'][href*='/bolum-']").forEach { ep ->
                    val epUrl = fixUrlNull(ep.attr("href")) ?: return@forEach
                    if (episodes.any { it.data == epUrl }) return@forEach
                    val epText = ep.text().trim()
                    val season = Regex("""(?i)(\d+)\.\s*[Ss]ezon|[Ss]ezon\s*(\d+)|[Ss](\d+)""").find(epUrl)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull()
                        ?: Regex("""(?i)(\d+)\.\s*[Ss]ezon""").find(epText)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val episode = Regex("""(?i)(\d+)\.\s*[Bb]ölüm|[Bb]olum\s*(\d+)|[Ee](\d+)""").find(epUrl)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull()
                        ?: Regex("""(?i)(\d+)\.\s*[Bb]ölüm""").find(epText)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val name = epText.replace(Regex("""(?i)\d+\.\s*Sezon\s*\d+\.\s*Bölüm"""), "").trim().ifEmpty { epText }

                    episodes.add(newEpisode(epUrl) {
                        this.season = season
                        this.episode = episode
                        this.name = name
                    })
                }
            } catch (_: Exception) {}
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinctBy { it.data }) {
            this.posterUrl = poster
            this.plot      = description
            this.year      = year
            this.tags      = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val pvSlug = document.selectFirst("[data-pv]")?.attr("data-pv")
        val scriptSrc = document.selectFirst("script[src*='core.js'], script[src*='/e/c.js']")?.attr("src")
        if (!pvSlug.isNullOrEmpty() && !scriptSrc.isNullOrEmpty()) {
            val base = scriptSrc.replace(Regex("""/(?:e/c|assets/js/core)\.js.*$"""), "")
            val playerUrl = "$base/assets/js/s.php?s=${java.net.URLEncoder.encode(pvSlug, "utf-8")}"
            try {
                val playerDoc = app.get(playerUrl, referer = "$mainUrl/").text
                val playerJson = Regex("""window\.__PLAYER__\s*=\s*(\{.*?\});""").find(playerDoc)?.groupValues?.get(1)
                if (playerJson != null) {
                    val streamUrl = org.json.JSONObject(playerJson).optString("stream")
                    if (streamUrl.isNotEmpty()) {
                        callback(
                            ExtractorLink(
                                source  = this.name,
                                name    = this.name,
                                url     = streamUrl,
                                referer = playerUrl,
                                quality = Qualities.P1080.value,
                                isM3u8  = true
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        document.select("iframe[src], iframe[data-src]").forEach { iframe ->
            val src = (iframe.attr("src").ifEmpty { iframe.attr("data-src") })
                .let { if (it.startsWith("//")) "https:$it" else it }
            if (src.isNotEmpty()) loadExtractor(src, data, subtitleCallback, callback)
        }

        document.select("source[src*=.m3u8], video[src*=.m3u8]").forEach { source ->
            val src = source.attr("src")
            if (src.isNotEmpty()) {
                callback(
                    ExtractorLink(
                        source  = this.name,
                        name    = this.name,
                        url     = src,
                        referer = data,
                        quality = Qualities.Unknown.value,
                        isM3u8  = true
                    )
                )
            }
        }

        document.select("track[kind=subtitles], track[kind=captions]").forEach { track ->
            subtitleCallback(
                SubtitleFile(
                    lang = track.attr("srclang").ifEmpty { "tr" },
                    url  = track.attr("src")
                )
            )
        }

        return true
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val a = if (this.tagName() == "a") this else this.selectFirst("a[href*='/dizi/']") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        if (!href.contains("/dizi/")) return null
        val img = a.selectFirst("img")
        val title = (a.selectFirst("h3")?.text()
            ?: img?.attr("alt")?.replace(Regex("(?i)dizi\\s*izle"), "")
            ?: a.attr("title")
            ?: return null).trim()
        if (title.isEmpty()) return null
        val poster = fixUrlNull(img?.attr("srcset")?.split(" ")?.firstOrNull())
            ?: fixUrlNull(img?.attr("src"))
            ?: fixUrlNull(img?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = poster
        }
    }
}
