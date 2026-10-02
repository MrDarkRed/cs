@file:Suppress("DEPRECATION_ERROR", "DEPRECATION")
// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class FilmMakinesi : MainAPI() {
    override var mainUrl              = "https://filmmakinesi.co"
    override var name                 = "FilmMakinesi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    override var sequentialMainPage            = true
    override var sequentialMainPageDelay       = 50L
    override var sequentialMainPageScrollDelay = 50L

    override val mainPage = mainPageOf(
        "${mainUrl}/"                                      to "Son Eklenenler",
        "${mainUrl}/film-arsivi/"                          to "Tüm Filmler",
        "${mainUrl}/turkce-dublaj-filmler/"                to "Türkçe Dublaj",
        "${mainUrl}/turkce-altyazili-filmler/"             to "Türkçe Altyazılı",
        "${mainUrl}/film-robotu/?fr_genres=984"            to "Popüler Filmler",
        "${mainUrl}/film-robotu/?fr_imdb=7-10"             to "IMDb 7+ Filmler",
        "${mainUrl}/film-robotu/?fr_genres=976"            to "Netflix",
        "${mainUrl}/film-robotu/?fr_genres=977"            to "Prime Video"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) {
            request.data
        } else {
            if (request.data.contains("?")) {
                val parts = request.data.split("?")
                "${parts[0].trimEnd('/')}/page/$page/?${parts[1]}"
            } else {
                "${request.data.trimEnd('/')}/page/$page/"
            }
        }
        val document = app.get(url).document
        val home = document.select("a.poster, a.mini-poster, div.posters-4-col a.poster, div.slider-slide a.poster")
            .distinctBy { it.attr("href") }
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst(".mini-poster-title, .poster-title, strong, h4, h2")?.text()?.trim()
            ?: this.attr("title").ifEmpty { null }
            ?: return null
        val href = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(
            this.selectFirst("img")?.attr("src")?.ifEmpty { null }
                ?: this.selectFirst("img")?.attr("data-src")
        )

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = java.net.URLEncoder.encode(query, "utf-8")
        val document = app.get("${mainUrl}/?s=${encodedQuery}").document

        return document.select("a.mini-poster, a.poster")
            .distinctBy { it.attr("href") }
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title           = document.selectFirst("h1.section-title, h1")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("meta[property='og:image']")?.attr("content"))
        val description     = document.selectFirst("article.post-info-content p, .post-info-content")?.text()?.trim()
        val tags            = document.select("div.post-info-genres a, div.post-info-cats a").map { it.text().trim() }.filter { it.isNotEmpty() }
        val rating          = document.selectFirst("div.post-info-imdb-rating, .post-info-imdb")?.text()?.filter { it.isDigit() || it == '.' }?.toRatingInt()
        val year            = document.selectFirst("div.post-info-year-country")?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()

        val durationText    = document.selectFirst("div.post-info-duration")?.text()
        val duration        = durationText?.filter { it.isDigit() }?.toIntOrNull() ?: 0

        val recommendations = document.select("div.slider-slide a.poster, div.posters-4-col a.poster")
            .distinctBy { it.attr("href") }
            .mapNotNull { it.toSearchResult() }

        val trailerId       = document.selectFirst("button.fragman-btn")?.attr("data-video")
        val trailer         = if (!trailerId.isNullOrEmpty()) "https://www.youtube.com/watch?v=$trailerId" else null

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.year            = year
            this.plot            = description
            this.tags            = tags
            this.rating          = rating
            this.duration        = duration
            this.recommendations = recommendations
            if (trailer != null) addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("FLMM", "data » $data")
        val document = app.get(data).document

        val iframes = document.select("div.player-container iframe, div.video-player-container-here iframe, iframe[src], iframe[data-src]").mapNotNull {
            val src = it.attr("src").ifEmpty { it.attr("data-src") }
            if (src.isNotEmpty()) src else null
        }.distinct()

        for (iframe in iframes) {
            val fullUrl = fixUrl(iframe)
            Log.d("FLMM", "iframe » $fullUrl")
            loadExtractor(fullUrl, "${mainUrl}/", subtitleCallback, callback)
        }

        return true
    }
}
