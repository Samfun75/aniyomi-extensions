package eu.kanade.tachiyomi.animeextension.all.yomiroll

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

enum class DecryptionType(
    val type: String,
) {
    CRYPTO_KEY("cryptokey"),
    DECRYPTION_KEY("decryption_key"),
    CENC_DECRYPTION_KEY("cenc_decryption_key"),
}

@Serializable
data class AccessToken(
    val access_token: String,
    val token_type: String,
    val policy: String? = null,
    val signature: String? = null,
    val key_pair_id: String? = null,
    val bucket: String? = null,
    val policyExpire: Long? = null,
)

@Serializable
data class TokenError(
    val code: String? = null,
)

@Serializable
data class Policy(
    val cms: Tokens,
) {
    @Serializable
    data class Tokens(
        val policy: String,
        val signature: String,
        val key_pair_id: String,
        val bucket: String,
        val expires: String,
    )
}

@Serializable
data class LinkData(
    val id: String,
    val media_type: String,
)

@Serializable
data class Images(
    val poster_tall: List<ArrayList<Image>>? = null,
) {
    @Serializable
    data class Image(
        val width: Int,
        val height: Int,
        val type: String,
        val source: String,
    )
}

@Serializable
data class Anime(
    val id: String,
    val type: String? = null,
    val title: String,
    val description: String,
    val images: Images,
    @SerialName("keywords")
    val genres: ArrayList<String>? = null,
    val series_metadata: Metadata? = null,
    @SerialName("movie_listing_metadata")
    val movie_metadata: Metadata? = null,
    val content_provider: String? = null,
    val audio_locale: String? = null,
    val audio_locales: ArrayList<String>? = null,
    val subtitle_locales: ArrayList<String>? = null,
    val maturity_ratings: ArrayList<String>? = null,
    val is_dubbed: Boolean? = null,
    val is_subbed: Boolean? = null,
) {
    @Serializable
    data class Metadata(
        val maturity_ratings: ArrayList<String>,
        val is_simulcast: Boolean? = null,
        val audio_locales: ArrayList<String>? = null,
        val subtitle_locales: ArrayList<String>,
        val is_dubbed: Boolean,
        val is_subbed: Boolean,
        @SerialName("tenant_categories")
        val genres: ArrayList<String>? = null,
    )
}

@Serializable
data class AnimeResult(
    val total: Int,
    val data: ArrayList<Anime>,
)

@Serializable
data class SearchAnimeResult(
    val data: ArrayList<SearchAnime>,
) {
    @Serializable
    data class SearchAnime(
        val type: String,
        val count: Int,
        val items: ArrayList<Anime>,
    )
}

@Serializable
data class SeasonResult(
    val total: Int,
    val data: ArrayList<Season>,
) {
    @Serializable
    data class Season(
        val id: String,
        val season_number: Int? = null,
        @SerialName("premium_available_date")
        val date: String? = null,
    )
}

@Serializable
data class EpisodeResult(
    val total: Int,
    val data: ArrayList<Episode>,
) {
    @Serializable
    data class Episode(
        @SerialName("id")
        val episodeId: String,
        @SerialName("audio_locale")
        val audioLocale: String,
        val title: String,
        @SerialName("sequence_number")
        val episodeNumber: Float,
        val episode: String? = null,
        @SerialName("episode_air_date")
        val airDate: String? = null,
        val versions: ArrayList<Version>? = null,
        @SerialName("streams_link")
        val streamsLink: String? = null,
    ) {
        @Serializable
        data class Version(
            @SerialName("audio_locale")
            val audioLocale: String,
            @SerialName("guid")
            val mediaId: String,
            @SerialName("is_premium_only")
            val isPremiumOnly: Boolean,
        )
    }
}

@Serializable
data class EpisodeData(
    val ids: List<Triple<String, String, String>>,
)

@Serializable
data class HosterData(
    val id: Triple<String, String, String>,
)

@Serializable
data class LicenseResponse(
    val license: String,
)

@Serializable
data class VideoStreams(
    val url: String,
    val token: String,
    val subtitles: JsonObject? = null,
    val audioLocale: String? = null,
)

@Serializable
data class HlsLinks(
    val hardsub_locale: String,
    val url: String,
)

@Serializable
data class Subtitle(
    val language: String,
    val url: String? = null,
)

@Serializable
data class AnilistResult(
    val data: AniData,
) {
    @Serializable
    data class AniData(
        @SerialName("Media")
        val media: Media? = null,
    )

    @Serializable
    data class Media(
        val status: String,
    )
}

@Serializable
data class MediaSegmentsResponse(
    val intro: MediaSegment? = null,
    val credits: MediaSegment? = null,
    val preview: MediaSegment? = null,
    val recap: MediaSegment? = null,
) {
    @Serializable
    data class MediaSegment(
        val type: String? = null,
        val start: Double? = null,
        val end: Double? = null,
    )
}

fun <T> List<T>.thirdLast(): T? {
    if (size < 3) return null
    return this[size - 3]
}
