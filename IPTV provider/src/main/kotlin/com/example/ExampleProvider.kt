package com.thathollowdude  // Use your exact namespace from gradle (lowercase recommended: com.thathollowdude)

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

class IptvOrgProvider : MainAPI() {
    override var name = "IPTV Org"
    override var lang = "en"  // Multilingual, but base as en
    override val supportedTypes = setOf(TvType.Live)
    override val hasMainPage = true
    override val mainUrl = "https://iptv-org.github.io"

    // Data classes for the IPTV-Org API
    data class Country(
        val code: String,
        val name: String
    )

    data class Category(
        val name: String
    )

    data class CountryRef(
        val code: String,
        val name: String
    )

    data class Language(
        val code: String,
        val name: String
    )

    data class Channel(
        val name: String,
        val url: String,
        val logo: String?,
        val category: String?,
        @JsonProperty("countries") val countries: List<CountryRef>?,
        @JsonProperty("languages") val languages: List<Language>?
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val countries = app.get("$mainUrl/api/countries.json").parsedSafe<List<Country>>() ?: emptyList()
        val categories = app.get("$mainUrl/api/categories.json").parsedSafe<List<Category>>() ?: emptyList()

        val countryItems = countries.map {
            LiveSearchResponse(
                name = it.name,
                url = "country:${it.code}",
                apiName = this.name,
                type = TvType.Live,
                posterUrl = "https://flagcdn.com/w320/${it.code.lowercase()}.png"  // Nice flag poster
            )
        }

        val categoryItems = categories.map {
            LiveSearchResponse(
                name = it.name,
                url = "category:${it.name}",
                apiName = this.name,
                type = TvType.Live
            )
        }

        val homePages = listOf(
            HomePageList("Countries", countryItems),
            HomePageList("Categories", categoryItems)
        )

        return HomePageResponse(homePages)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val allChannels = app.get("$mainUrl/api/channels.json").parsedSafe<List<Channel>>() ?: return emptyList()
        return allChannels
            .filter { it.name.contains(query, ignoreCase = true) }
            .map {
                LiveSearchResponse(
                    name = it.name,
                    url = it.url,
                    apiName = this.name,
                    type = TvType.Live,
                    posterUrl = it.logo
                )
            }
    }

    override suspend fun load(url: String): LoadResponse {
        if (url.startsWith("http")) {
            // Direct channel link from search
            return newMovieLoadResponse("Live Stream", url, TvType.Live, url) {
                posterUrl = null  // No poster for single
                plot = "Direct live stream"
            }
        }

        val allChannels = app.get("$mainUrl/api/channels.json").parsedSafe<List<Channel>>() ?: return LoadResponse.empty()

        val filtered = if (url.startsWith("country:")) {
            val code = url.substringAfter("country:").uppercase()
            allChannels.filter { channel -> channel.countries?.any { it.code == code } == true }
        } else if (url.startsWith("category:")) {
            val cat = url.substringAfter("category:")
            allChannels.filter { channel -> channel.category.equals(cat, ignoreCase = true) }
        } else {
            return LoadResponse.empty()
        }

        if (filtered.isEmpty()) return LoadResponse.empty()

        val title = if (url.startsWith("country:")) {
            // Find country name
            val code = url.substringAfter("country:").uppercase()
            app.get("$mainUrl/api/countries.json").parsedSafe<List<Country>>()
                ?.find { it.code == code }?.name ?: "Country Channels"
        } else {
            url.substringAfter("category:")
        }

        val episodes = filtered.map { channel ->
            Episode(
                data = channel.url,
                name = channel.name,
                posterUrl = channel.logo,
                description = "Category: ${channel.category ?: "Unknown"}"
            )
        }

        return newTvSeriesLoadResponse(title, url, TvType.Live, episodes)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Direct M3U8/HLS stream – most IPTV-Org links are .m3u8
        callback.invoke(
            ExtractorLink(
                source = this.name,
                name = this.name,
                url = data,
                referer = "",
                quality = Qualities.Unknown.value,
                isM3u8 = true
            )
        )
        return true
    }
}