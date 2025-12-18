package com.thathollowdude  // Match your namespace exactly (case-sensitive if you used capitals)

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

class IptvOrgProvider : MainAPI() {
    override val name = "IPTV Org"
    override val lang = "en"
    override val supportedTypes = setOf(TvType.Live)
    override val hasMainPage = true
    override val mainUrl = "https://iptv-org.github.io"

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

        val countryItems = countries.map { country ->
            newLiveSearchResponse(country.name, "country:${country.code}") {
                posterUrl = "https://flagcdn.com/w320/${country.code.lowercase()}.png"
            }
        }

        val categoryItems = categories.map { cat ->
            newLiveSearchResponse(cat.name, "category:${cat.name}")
        }

        return newHomePageResponse(listOf(
            HomePageList("Countries", countryItems),
            HomePageList("Categories", categoryItems)
        ))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val allChannels = app.get("$mainUrl/api/channels.json").parsedSafe<List<Channel>>() ?: return emptyList()
        return allChannels
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { channel ->
                newLiveSearchResponse(channel.name, channel.url) {
                    posterUrl = channel.logo
                }
            }
    }

    override suspend fun load(url: String): LoadResponse {
        if (url.startsWith("http")) {
            // Direct stream from search
            return newLiveLoadResponse("Live Stream", url, TvType.Live, url)
        }

        val allChannels = app.get("$mainUrl/api/channels.json").parsedSafe<List<Channel>>() ?: return LoadResponse.Empty

        val filtered = when {
            url.startsWith("country:") -> {
                val code = url.substringAfter("country:").uppercase()
                allChannels.filter { ch -> ch.countries?.any { it.code.equals(code, ignoreCase = true) } == true }
            }
            url.startsWith("category:") -> {
                val cat = url.substringAfter("category:")
                allChannels.filter { ch -> ch.category.equals(cat, ignoreCase = true) }
            }
            else -> return LoadResponse.Empty
        }

        if (filtered.isEmpty()) return LoadResponse.Empty

        val title = if (url.startsWith("country:")) {
            val code = url.substringAfter("country:").uppercase()
            countriesCache?.find { it.code.equals(code, ignoreCase = true) }?.name ?: "Channels"
        } else {
            url.substringAfter("category:")
        }

        val episodes = filtered.map { channel ->
            newEpisode(channel.url) {
                this.name = channel.name
                this.posterUrl = channel.logo
                this.description = channel.category?.let { "Category: $it" }
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Live, episodes)
    }

    private val countriesCache by lazy {
        app.get("$mainUrl/api/countries.json").parsedSafe<List<Country>>()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(this.name, this.name, data, "", Qualities.Unknown.value, isM3u8 = true)
        )
        return true
    }
}