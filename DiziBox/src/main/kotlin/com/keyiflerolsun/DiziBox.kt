@file:Suppress("DEPRECATION_ERROR", "DEPRECATION")
// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.StringUtils.decodeUri
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class DiziBox : MainAPI() {
    override var mainUrl              = "https://diziboxizle.com"
    override var name                 = "DiziBox"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    // ! CloudFlare bypass
    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 50L  // ? 0.05 saniye
    override var sequentialMainPageScrollDelay = 50L  // ? 0.05 saniye

    // ! CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.text().contains("Güvenlik taramasından geçiriliyorsunuz. Lütfen bekleyiniz..")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/dizi-arsivi/page/SAYFA/?ulke[]=turkiye&yil=&imdb"   to "Yerli",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=aile&yil&imdb"       to "Aile",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=aksiyon&yil&imdb"    to "Aksiyon",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=animasyon&yil&imdb"  to "Animasyon",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=belgesel&yil&imdb"   to "Belgesel",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=bilimkurgu&yil&imdb" to "Bilimkurgu",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=biyografi&yil&imdb"  to "Biyografi",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=dram&yil&imdb"       to "Dram",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=drama&yil&imdb"      to "Drama",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=fantastik&yil&imdb"  to "Fantastik",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=gerilim&yil&imdb"    to "Gerilim",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=gizem&yil&imdb"      to "Gizem",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=komedi&yil&imdb"     to "Komedi",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=korku&yil&imdb"      to "Korku",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=macera&yil&imdb"     to "Macera",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=muzik&yil&imdb"      to "Müzik",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=muzikal&yil&imdb"    to "Müzikal",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=reality-tv&yil&imdb" to "Reality TV",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=romantik&yil&imdb"   to "Romantik",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=savas&yil&imdb"      to "Savaş",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=spor&yil&imdb"       to "Spor",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=suc&yil&imdb"        to "Suç",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=tarih&yil&imdb"      to "Tarih",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=western&yil&imdb"    to "Western",
        "${mainUrl}/dizi-arsivi/page/SAYFA/?tur[0]=yarisma&yil&imdb"    to "Yarışma"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url      = request.data.replace("SAYFA", "$page")
        val document = app.get(
            url,
            cookies     = mapOf(
                "LockUser"      to "true",
                "isTrustedUser" to "true",
                "dbxu"          to "1722403730363"
            ),
            interceptor = interceptor
        ).document
        val home     = document.select("div.single-item, article.detailed-article").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst(".categorytitle a, .cat-title a, h3 a, h2 a")?.text()?.trim() ?: return null
        val href      = fixUrlNull(this.selectFirst(".categorytitle a, .cat-title a, .cat-img a, h3 a, h2 a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst(".cat-img img, img")?.attr("data-src"))
            ?: fixUrlNull(this.selectFirst(".cat-img img, img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get(
            "${mainUrl}/?s=${query}",
            cookies     = mapOf(
                "LockUser"      to "true",
                "isTrustedUser" to "true",
                "dbxu"          to "1722403730363"
            ),
            interceptor = interceptor
        ).document

        return document.select("div.single-item, article.detailed-article").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(
            url,
            cookies     = mapOf(
                "LockUser"      to "true",
                "isTrustedUser" to "true",
                "dbxu"          to "1722403730363"
            ),
            interceptor = interceptor
        ).document

        val title       = (document.selectFirst("#icerikcatright h1, div.tv-overview h1 a, h1")?.text()
            ?.substringBefore("Dizisi izle")
            ?.substringBefore("izle")
            ?: return null).trim()
        val poster      = fixUrlNull(document.selectFirst("#icerikcatleft img, div.tv-overview figure img, figure img")?.attr("src"))
            ?: fixUrlNull(document.selectFirst("#icerikcatleft img, div.tv-overview figure img, figure img")?.attr("data-src"))
        val description = document.selectFirst("div.category_desc, div.tv-story p")?.text()?.trim()
        val year        = document.selectFirst("#icerikcat2 div:contains(Yapım Yılı), a[href*='/yil/']")
            ?.text()?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
        val tags        = document.select(".genres a, a[href*='/tur/']").map { it.text().trim() }
        val rating      = (document.selectFirst("span.imdb-score, span.label-imdb b")?.text()?.trim())?.toRatingInt()
        val actorsList  = mutableListOf<Actor>()
        val actorText   = document.select("#icerikcat2 div:contains(Oyuncular)").text()
        if (actorText.contains("Oyuncular")) {
            actorText.substringAfter("Oyuncular").substringAfter(":").split(",").forEach {
                val act = it.trim()
                if (act.isNotEmpty()) actorsList.add(Actor(act))
            }
        } else {
            document.select("a[href*='/oyuncu/']").forEach {
                actorsList.add(Actor(it.text().trim()))
            }
        }
        val trailer     = document.selectFirst("div.tv-overview iframe, #trailer iframe")?.attr("src")

        val episodeList = mutableListOf<Episode>()

        // 1. New site episode list inside #scrollbar-container or directly on page
        val epElements = document.select("#scrollbar-container a, a[href*='-sezon-']")
        if (epElements.isNotEmpty()) {
            epElements.forEach { epElem ->
                val epHref = fixUrlNull(epElem.attr("href")) ?: return@forEach
                if (epHref.contains("tum-bolumler") || epHref.endsWith("/dizi-arsivi/")) return@forEach
                if (episodeList.any { it.data == epHref }) return@forEach

                val epRawText = epElem.text().trim()
                val epSeason  = Regex("""(\d+)\.\s*[Ss]ezon""").find(epRawText)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""-([0-9]+)-sezon""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1
                val epEpisode = Regex("""(\d+)\.\s*[Bb]ölüm""").find(epRawText)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""-([0-9]+)-bolum""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                val epName    = Regex("""\((.*?)\)""").find(epRawText)?.groupValues?.get(1)?.trim()
                    ?: epRawText.substringBefore("\n").trim().ifEmpty { null }

                episodeList.add(newEpisode(epHref) {
                    this.name    = epName
                    this.season  = epSeason
                    this.episode = epEpisode
                })
            }
        }

        // 2. Old site episode list fallback
        if (episodeList.isEmpty()) {
            document.select("div#seasons-list a").forEach {
                val epUrl = fixUrlNull(it.attr("href")) ?: return@forEach
                val epDoc = app.get(
                    epUrl,
                    cookies     = mapOf(
                        "LockUser"      to "true",
                        "isTrustedUser" to "true",
                        "dbxu"          to "1722403730363"
                    ),
                    interceptor = interceptor
                ).document

                epDoc.select("article.grid-box").forEach ep@ { epElem ->
                    val epTitle   = epElem.selectFirst("div.post-title a")?.text()?.trim() ?: return@ep
                    val epHref    = fixUrlNull(epElem.selectFirst("div.post-title a")?.attr("href")) ?: return@ep
                    val epSeason  = Regex("""(\d+)\. ?Sezon""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val epEpisode = Regex("""(\d+)\. ?Bölüm""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()

                    episodeList.add(newEpisode(epHref) {
                        this.name    = epTitle
                        this.season  = epSeason
                        this.episode = epEpisode
                    })
                }
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
            this.posterUrl = poster
            this.plot      = description
            this.year      = year
            this.tags      = tags
            this.rating    = rating
            addActors(actorsList)
            addTrailer(trailer)
        }
    }

    private suspend fun iframeDecode(data:String, iframe:String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        @Suppress("NAME_SHADOWING") var iframe = iframe

        if (iframe.contains("/player/king/king.php")) {
            iframe = iframe.replace("king.php?v=", "king.php?wmode=opaque&v=")
            val subDoc = app.get(
                iframe,
                referer     = data,
                cookies     = mapOf(
                    "LockUser"      to "true",
                    "isTrustedUser" to "true",
                    "dbxu"          to "1722403730363"
                ),
                interceptor = interceptor
            ).document
            val subFrame = subDoc.selectFirst("div#Player iframe")?.attr("src") ?: return false

            val iDoc          = app.get(subFrame, referer="${mainUrl}/").text
            val cryptData     = Regex("""CryptoJS\.AES\.decrypt\("(.*)","""").find(iDoc)?.groupValues?.get(1) ?: return false
            val cryptPass     = Regex("""","(.*)"\);""").find(iDoc)?.groupValues?.get(1) ?: return false
            val decryptedData = CryptoJS.decrypt(cryptPass, cryptData)
            val decryptedDoc  = Jsoup.parse(decryptedData)
            val vidUrl        = Regex("""file: '(.*)',""").find(decryptedDoc.html())?.groupValues?.get(1) ?: return false

            callback.invoke(
                ExtractorLink(
                    source  = this.name,
                    name    = this.name,
                    url     = vidUrl,
                    referer = vidUrl,
                    quality = getQualityFromName("4k"),
                    isM3u8  = true
                )
            )

        } else if (iframe.contains("/player/moly/moly.php")) {
            iframe = iframe.replace("moly.php?h=", "moly.php?wmode=opaque&h=")
            var subDoc = app.get(
                iframe,
                referer     = data,
                cookies     = mapOf(
                    "LockUser"      to "true",
                    "isTrustedUser" to "true",
                    "dbxu"          to "1722403730363"
                ),
                interceptor = interceptor
            ).document

            val atobData = Regex("""unescape\("(.*)"\)""").find(subDoc.html())?.groupValues?.get(1)
            if (atobData != null) {
                val decodedAtob = atobData.decodeUri()
                val strAtob     = String(Base64.decode(decodedAtob, Base64.DEFAULT), Charsets.UTF_8)
                subDoc          = Jsoup.parse(strAtob)
            }

            val subFrame = subDoc.selectFirst("div#Player iframe")?.attr("src") ?: return false

            loadExtractor(subFrame, "${mainUrl}/", subtitleCallback, callback)

        } else if (iframe.contains("/player/haydi.php")) {
            iframe = iframe.replace("haydi.php?v=", "haydi.php?wmode=opaque&v=")
            var subDoc = app.get(
                iframe,
                referer     = data,
                cookies     = mapOf(
                    "LockUser"      to "true",
                    "isTrustedUser" to "true",
                    "dbxu"          to "1722403730363"
                ),
                interceptor = interceptor
            ).document

            val atobData = Regex("""unescape\("(.*)"\)""").find(subDoc.html())?.groupValues?.get(1)
            if (atobData != null) {
                val decodedAtob = atobData.decodeUri()
                val strAtob     = String(Base64.decode(decodedAtob, Base64.DEFAULT), Charsets.UTF_8)
                subDoc          = Jsoup.parse(strAtob)
            }

            val subFrame = subDoc.selectFirst("div#Player iframe")?.attr("src") ?: return false

            loadExtractor(subFrame, "${mainUrl}/", subtitleCallback, callback)
        }

        return true
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("DZBX", "data » $data")
        val document = app.get(
            data,
            cookies     = mapOf(
                "LockUser"      to "true",
                "isTrustedUser" to "true",
                "dbxu"          to "1722403730363"
            ),
            interceptor = interceptor
        ).document

        val iframes = mutableSetOf<String>()

        suspend fun handleIframe(frameUrl: String, pageUrl: String) {
            val src = fixUrlNull(frameUrl) ?: return
            if (src in iframes) return
            iframes.add(src)
            Log.d("DZBX", "iframe » $src")
            if (src.contains("/player/")) {
                iframeDecode(pageUrl, src, subtitleCallback, callback)
            } else {
                loadExtractor(src, "${mainUrl}/", subtitleCallback, callback)
            }
        }

        document.select("div#video-area iframe, iframe[src], iframe[data-src]").forEach {
            val src = it.attr("src").ifEmpty { it.attr("data-src") }
            handleIframe(src, data)
        }

        document.select("div.sources a.post-page-numbers[href]").forEach {
            val subPage = fixUrlNull(it.attr("href")) ?: return@forEach
            try {
                val subDoc = app.get(
                    subPage,
                    cookies     = mapOf(
                        "LockUser"      to "true",
                        "isTrustedUser" to "true",
                        "dbxu"          to "1722403730363"
                    ),
                    interceptor = interceptor
                ).document
                subDoc.select("div#video-area iframe, iframe[src], iframe[data-src]").forEach { sIfr ->
                    val src = sIfr.attr("src").ifEmpty { sIfr.attr("data-src") }
                    handleIframe(src, subPage)
                }
            } catch (_: Exception) {}
        }

        document.select("div.video-toolbar option[value]").forEach {
            val altLink = fixUrlNull(it.attr("value")) ?: return@forEach
            try {
                val subDoc  = app.get(
                    altLink,
                    cookies     = mapOf(
                        "LockUser"      to "true",
                        "isTrustedUser" to "true",
                        "dbxu"          to "1722403730363"
                    ),
                    interceptor = interceptor
                ).document
                subDoc.select("div#video-area iframe, iframe[src], iframe[data-src]").forEach { sIfr ->
                    val src = sIfr.attr("src").ifEmpty { sIfr.attr("data-src") }
                    handleIframe(src, altLink)
                }
            } catch (_: Exception) {}
        }

        return true
    }
}