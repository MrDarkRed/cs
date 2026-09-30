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
        "$mainUrl/populer-diziler/"  to "Popüler Diziler",
        "$mainUrl/turkce-dublaj/"    to "Türkçe Dublaj",
        "$mainUrl/turkce-altyazi/"   to "Türkçe Altyazılı",
        "$mainUrl/tur/aksiyon/"      to "Aksiyon",
        "$mainUrl/tur/dram/"         to "Dram",
        "$mainUrl/tur/kore/"         to "Kore Dizileri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url      = "${request.data}${if (page > 1) "page/$page/" else ""}"
        val document = app.get(url).document

        val items = document.select(
            "div.dizi-item, article.dizi, div.series-card, div.item, div.card"
        ).mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = java.net.URLEncoder.encode(query, "utf-8")
        val document = app.get("$mainUrl/?s=$encodedQuery").document
        return document.select(
            "div.dizi-item, article.dizi, div.series-card, div.item, div.card"
        ).mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("h1, h1.dizi-adi, h1.entry-title")?.text()?.trim() ?: return null
        val poster      = document.selectFirst("img.dizi-poster, div.poster img, .cover img")?.attr("src")
        val description = document.selectFirst("div.dizi-aciklama p, div.entry-content p, .plot, .ozet")?.text()
        val year        = document.selectFirst("span.yil, span.yapim-yili, .year")
            ?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
        val tags        = document.select(".kategoriler a, .genres a, .tags a").map { it.text() }

        val episodes = document.select(
            "div.bolumler a[href], div.episode-list a[href], ul.bolum-listesi li a[href], .episodes a[href]"
        ).mapNotNull { ep ->
            val epUrl   = ep.attr("href").ifEmpty { return@mapNotNull null }
            val epText  = ep.text()
            val season  = Regex("""(?i)(\d+)\.\s*[Ss]ezon|[Ss]eason\s*(\d+)|[Ss](\d+)[Ee]\d+""")
                .find(epText)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull() ?: 1
            val episode = Regex("""(?i)(\d+)\.\s*[Bb]ölüm|[Ee]p(?:isode)?\s*(\d+)|[Ss]\d+[Ee](\d+)""")
                .find(epText)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull() ?: 1

            newEpisode(fixUrl(epUrl)) {
                this.season  = season
                this.episode = episode
                this.name    = epText
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
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

        document.select("iframe[src], iframe[data-src]").forEach { iframe ->
            val src = (iframe.attr("src").ifEmpty { iframe.attr("data-src") })
                .let { if (it.startsWith("//")) "https:$it" else it }
            if (src.isNotEmpty()) loadExtractor(src, data, subtitleCallback, callback)
        }

        document.select("source[src*=.m3u8], video[src*=.m3u8]").forEach { source ->
            val src = source.attr("src")
            if (src.isNotEmpty()) {
                callback(
                    newExtractorLink(
                        source  = this.name,
                        name    = this.name,
                        url     = src,
                        type    = ExtractorLinkType.M3U8
                    ) {
                        this.quality = Qualities.Unknown.value
                    }
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
        val title  = selectFirst("h2, h3, .dizi-adi, .series-name, .title")?.text()?.trim() ?: return null
        val href   = selectFirst("a[href]")?.attr("href") ?: return null
        val poster = selectFirst("img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }
        return newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries) {
            this.posterUrl = poster
        }
    }
}
