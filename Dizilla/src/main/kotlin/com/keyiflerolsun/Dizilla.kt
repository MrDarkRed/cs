@file:Suppress("DEPRECATION_ERROR", "DEPRECATION")
// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Dizilla : MainAPI() {
    override var mainUrl              = "https://dizilla.now"
    override var name                 = "Dizilla"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries)

    override var sequentialMainPage = true

    override val mainPage = mainPageOf(
        mainUrl                                  to "Güncel Bölümler",
        "${mainUrl}/arsiv"                       to "Yeni Eklenenler",
        "${mainUrl}/yabanci-dizi-izle"           to "Yabancı Diziler",
        "${mainUrl}/dizi-turu/aksiyon"           to "Aksiyon",
        "${mainUrl}/dizi-turu/bilim-kurgu"       to "Bilim Kurgu",
        "${mainUrl}/dizi-turu/komedi"            to "Komedi",
        "${mainUrl}/dizi-turu/dram"              to "Dram",
        "${mainUrl}/dizi-turu/korku"             to "Korku",
        "${mainUrl}/dizi-turu/romantik"          to "Romantik",
        "${mainUrl}/dizi-turu/animasyon"         to "Animasyon",
        "${mainUrl}/dizi-turu/aile"              to "Aile"
    )

    private fun decryptDizilla(encryptedB64: String): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val shaBytes = md.digest("!!22xx!!90!!".toByteArray(Charsets.UTF_8))
            val b64Sha = Base64.encodeToString(shaBytes, Base64.NO_WRAP).substring(0, 32)
            val keySpec = SecretKeySpec(b64Sha.toByteArray(Charsets.UTF_8), "AES")
            val ivSpec = IvParameterSpec(ByteArray(16))
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decoded = Base64.decode(encryptedB64, Base64.DEFAULT)
            String(cipher.doFinal(decoded), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("DZL", "Decryption error: ${e.message}")
            null
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page > 1) {
            if (request.data.contains("?")) "${request.data}&page=$page" else "${request.data}?page=$page"
        } else {
            request.data
        }
        val document = app.get(pageUrl).document

        val home = if (request.data == mainUrl || request.data == "$mainUrl/") {
            val items = mutableListOf<SearchResponse>()
            document.select("a.relative[href*='-sezon-']").forEach {
                val title = it.attr("title").ifEmpty { it.text().trim() }
                val href = fixUrlNull(it.attr("href")) ?: return@forEach
                val poster = fixUrlNull(it.selectFirst("img")?.attr("src")) ?: fixUrlNull(it.selectFirst("img")?.attr("data-src"))
                items.add(newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster })
            }
            if (items.isEmpty()) {
                document.select("span.watchlistitem-, a[href^='/dizi/']").mapNotNull { it.diziler() }.forEach { items.add(it) }
            }
            items
        } else {
            document.select("span.watchlistitem-, a.group:has(img), a[href^='/dizi/']").mapNotNull { it.diziler() }
        }.distinctBy { it.url }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.diziler(): SearchResponse? {
        val a = if (this.tagName() == "a") this else this.selectFirst("a[href^='/dizi/']") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val img = a.selectFirst("img")
        val title = img?.attr("alt")?.ifEmpty { null }
            ?: this.selectFirst("span.line-clamp-1, h3, h2")?.text()?.trim()
            ?: a.text().trim().ifEmpty { null }
            ?: return null
        val cleanTitle = title.replace(Regex("(?i)\\s*-\\s*\\d{4}\\s*izle"), "").replace(Regex("(?i)izle"), "").trim()
        val posterUrl = fixUrlNull(img?.attr("data-src")) ?: fixUrlNull(img?.attr("src"))

        return newTvSeriesSearchResponse(cleanTitle, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val encodedQuery = java.net.URLEncoder.encode(query, "utf-8")
            val searchRes = app.post(
                "${mainUrl}/api/bg/searchContent?searchterm=$encodedQuery",
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Accept"     to "application/json, text/plain, */*"
                )
            ).text

            val encryptedB64 = JSONObject(searchRes).optString("response")
            if (encryptedB64.isEmpty()) return emptyList()

            val decryptedJson = decryptDizilla(encryptedB64) ?: return emptyList()
            val parsedObj = JSONObject(decryptedJson)
            val results = parsedObj.optJSONArray("result") ?: return emptyList()

            val list = mutableListOf<SearchResponse>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val slug = item.optString("used_slug")
                val name = item.optString("object_name")
                val poster = item.optString("object_poster_url")
                if (slug.isNotEmpty() && name.isNotEmpty()) {
                    list.add(newTvSeriesSearchResponse(name, "${mainUrl}/$slug", TvType.TvSeries) {
                        this.posterUrl = fixUrlNull(poster)
                    })
                }
            }
            list
        } catch (e: Exception) {
            Log.e("DZL", "search error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        var title: String? = null
        var poster: String? = null
        var description: String? = null
        var year: Int? = null
        var rating: Int? = null
        val tags = mutableListOf<String>()
        val actors = mutableListOf<Actor>()
        val episodeList = mutableListOf<Episode>()

        // Try decrypting secureData from __NEXT_DATA__
        val nextDataScript = document.selectFirst("script#__NEXT_DATA__")?.data()
        if (!nextDataScript.isNullOrEmpty()) {
            try {
                val nextJson = JSONObject(nextDataScript)
                val pageProps = nextJson.optJSONObject("props")?.optJSONObject("pageProps")
                val secureData = pageProps?.optString("secureData")
                if (!secureData.isNullOrEmpty()) {
                    val decrypted = decryptDizilla(secureData)
                    if (decrypted != null) {
                        val dJson = JSONObject(decrypted)
                        val contentItem = dJson.optJSONObject("contentItem")
                        if (contentItem != null) {
                            title = contentItem.optString("original_title").ifEmpty { contentItem.optString("culture_title") }
                            description = contentItem.optString("description")
                            val posterObj = contentItem.optString("poster_url").ifEmpty { contentItem.optString("backdrop_url") }
                            if (posterObj.isNotEmpty()) poster = posterObj
                            val pt = contentItem.optDouble("imdb_point", 0.0)
                            if (pt > 0) rating = (pt * 10).toInt()
                            val yr = contentItem.optInt("release_year", 0)
                            if (yr > 0) year = yr
                        }

                        val relResults = dJson.optJSONObject("RelatedResults")
                        val seResults = relResults?.optJSONObject("getSerieSeasonAndEpisodes")?.optJSONArray("result")
                        if (seResults != null) {
                            for (sIdx in 0 until seResults.length()) {
                                val seasonObj = seResults.getJSONObject(sIdx)
                                val sNo = seasonObj.optInt("season_no", sIdx + 1)
                                val eps = seasonObj.optJSONArray("episodes") ?: continue
                                for (eIdx in 0 until eps.length()) {
                                    val epObj = eps.getJSONObject(eIdx)
                                    val epNo = epObj.optInt("episode_no", eIdx + 1)
                                    val epSlug = epObj.optString("used_slug")
                                    val epSub = epObj.optString("episode_subtitle")
                                    val epDesc = epObj.optString("episode_description")
                                    val epName = if (epSub.isNotEmpty() && epSub != "$epNo. Bölüm") epSub else "$epNo. Bölüm"
                                    if (epSlug.isNotEmpty()) {
                                        episodeList.add(newEpisode("${mainUrl}/$epSlug") {
                                            this.name = epName
                                            this.season = sNo
                                            this.episode = epNo
                                            this.description = epDesc
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DZL", "load secureData parse error: ${e.message}")
            }
        }

        // HTML Fallback if fields are missing
        if (title.isNullOrEmpty()) {
            title = document.selectFirst("h1")?.text()?.replace(Regex("(?i)izle"), "")?.trim() ?: return null
        }
        if (poster.isNullOrEmpty()) {
            poster = fixUrlNull(document.selectFirst("div.aspect-video img, div.page-top img, img")?.attr("src"))
        }
        if (description.isNullOrEmpty()) {
            description = document.selectFirst("div.text-base, div.mv-det-p")?.text()?.trim()
        }
        if (year == null) {
            year = document.selectXpath("//span[text()='Yayın tarihi']//following-sibling::span").text().trim().split(" ").last().toIntOrNull()
        }
        if (rating == null) {
            rating = document.selectFirst("a[href*='imdb.com'] span")?.text()?.trim()?.toRatingInt()
        }
        document.select("[href*='dizi-turu']").forEach { tags.add(it.text().trim()) }
        document.select("[href*='oyuncu']").forEach { actors.add(Actor(it.text().trim())) }

        if (episodeList.isEmpty()) {
            document.select("a[href*='-sezon-'][href*='-bolum']").forEach {
                val epHref = fixUrlNull(it.attr("href")) ?: return@forEach
                if (epHref.contains("tum-bolumler") || it.text().contains("Son Bölüm")) return@forEach
                if (episodeList.any { ep -> ep.data == epHref }) return@forEach
                val epText = it.text().trim()
                val season = Regex("""-([0-9]+)-sezon""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""(\d+)\.\s*[Ss]ezon""").find(epText)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val episode = Regex("""-([0-9]+)-bolum""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""(\d+)\.\s*[Bb]ölüm""").find(epText)?.groupValues?.get(1)?.toIntOrNull()
                episodeList.add(newEpisode(epHref) {
                    this.name = epText
                    this.season = season
                    this.episode = episode
                })
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList.distinctBy { it.data }) {
            this.posterUrl = poster
            this.year      = year
            this.plot      = description
            this.tags      = tags
            this.rating    = rating
            addActors(actors)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("DZL", "data » $data")
        val document = app.get(data).document
        val iframes  = mutableSetOf<String>()

        suspend fun handleIframe(frameUrl: String) {
            var src = fixUrlNull(frameUrl) ?: return
            if (src.startsWith("//")) src = "https:$src"
            if (src in iframes) return
            iframes.add(src)
            Log.d("DZL", "iframe » $src")
            loadExtractor(src, "${mainUrl}/", subtitleCallback, callback)
        }

        // 1. Try secureData from __NEXT_DATA__
        val nextDataScript = document.selectFirst("script#__NEXT_DATA__")?.data()
        if (!nextDataScript.isNullOrEmpty()) {
            try {
                val nextJson = JSONObject(nextDataScript)
                val pageProps = nextJson.optJSONObject("props")?.optJSONObject("pageProps")
                val secureData = pageProps?.optString("secureData")
                if (!secureData.isNullOrEmpty()) {
                    val decrypted = decryptDizilla(secureData)
                    if (decrypted != null) {
                        val dJson = JSONObject(decrypted)
                        val relResults = dJson.optJSONObject("RelatedResults")
                        val epSources = relResults?.optJSONObject("getEpisodeSources")?.optJSONArray("result")
                        if (epSources != null) {
                            for (i in 0 until epSources.length()) {
                                val sObj = epSources.getJSONObject(i)
                                val sContent = sObj.optString("source_content")
                                val iframeSrc = Regex("""src=["']([^"']+)["']""").find(sContent)?.groupValues?.get(1)
                                if (!iframeSrc.isNullOrEmpty()) {
                                    handleIframe(iframeSrc)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DZL", "loadLinks secureData error: ${e.message}")
            }
        }

        // 2. Direct iframes in HTML
        document.select("div#playerLsDizilla iframe, iframe[src]").forEach {
            val src = it.attr("src")
            if (src.isNotEmpty()) handleIframe(src)
        }

        // 3. Player alternatives links
        document.select("a[href*='player']").forEach {
            try {
                val playerDoc = app.get(fixUrlNull(it.attr("href")) ?: return@forEach).document
                val iframe = fixUrlNull(playerDoc.selectFirst("div#playerLsDizilla iframe, iframe[src]")?.attr("src"))
                if (iframe != null) handleIframe(iframe)
            } catch (_: Exception) {}
        }

        return true
    }
}
