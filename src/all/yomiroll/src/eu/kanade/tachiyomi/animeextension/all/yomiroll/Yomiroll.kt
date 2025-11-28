package eu.kanade.tachiyomi.animeextension.all.yomiroll

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.text.InputType
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.parallelCatchingFlatMap
import eu.kanade.tachiyomi.util.parallelCatchingFlatMapBlocking
import eu.kanade.tachiyomi.util.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.samfun.ktvine.cdm.Cdm
import org.samfun.ktvine.core.Device
import org.samfun.ktvine.core.PSSH
import org.samfun.ktvine.proto.License.KeyContainer.KeyType
import org.samfun.ktvine.proto.LicenseType
import org.samfun.ktvine.utils.toHexString
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.io.encoding.Base64

class Yomiroll :
    AnimeHttpSource(),
    ConfigurableAnimeSource {
    // No more renaming, no matter what 3rd party service is used :)
    override val name = "Yomiroll"

    override val baseUrl = "https://www.crunchyroll.com"

    private val crUrl = "https://beta-api.crunchyroll.com"
    private val crApiUrl = "$crUrl/content/v2"

    override val lang = "all"

    override val supportsLatest = true

    private val mainScope by lazy { MainScope() }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val tokenInterceptor by lazy {
        AccessTokenInterceptor(crUrl, preferences)
    }

    override val client by lazy {
        super.client
            .newBuilder()
            .addInterceptor(tokenInterceptor)
            .cookieJar(
                cookieJar = CookieJar.NO_COOKIES,
            ).build()
    }

    private val noTokenClient = super.client

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        val start = if (page != 1) "start=${(page - 1) * 36}&" else ""
        return GET("$crApiUrl/discover/browse?${start}n=36&sort_by=popularity&locale=en-US")
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val parsed = JSON.decodeFromString<AnimeResult>(response.body.string())
        val animeList = parsed.data.mapNotNull { it.toSAnimeOrNull() }
        val position =
            response.request.url
                .queryParameter("start")
                ?.toIntOrNull() ?: 0
        return AnimesPage(animeList, position + 36 < parsed.total)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val start = if (page != 1) "start=${(page - 1) * 36}&" else ""
        return GET("$crApiUrl/discover/browse?${start}n=36&sort_by=newly_added&locale=en-US")
    }

    override fun latestUpdatesParse(response: Response): AnimesPage = popularAnimeParse(response)

    // =============================== Search ===============================

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Request {
        val params = YomirollFilters.getSearchParameters(filters)
        val start = if (page != 1) "start=${(page - 1) * 36}&" else ""
        val url =
            if (query.isNotBlank()) {
                val cleanQuery = query.replace(" ", "+").lowercase()
                "$crApiUrl/discover/search?${start}n=36&q=$cleanQuery&type=${params.type}"
            } else {
                "$crApiUrl/discover/browse?${start}n=36${params.media}${params.language}&sort_by=${params.sort}${params.category}"
            }
        return GET(url)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val bod = response.body.string()
        val total: Int
        val items =
            if (response.request.url.encodedPath
                    .contains("search")
            ) {
                val parsed = JSON.decodeFromString<SearchAnimeResult>(bod).data.first()
                total = parsed.count
                parsed.items
            } else {
                val parsed = JSON.decodeFromString<AnimeResult>(bod)
                total = parsed.total
                parsed.data
            }

        val animeList = items.mapNotNull { it.toSAnimeOrNull() }
        val position =
            response.request.url
                .queryParameter("start")
                ?.toIntOrNull() ?: 0
        return AnimesPage(animeList, position + 36 < total)
    }

    override fun getFilterList(): AnimeFilterList = YomirollFilters.FILTER_LIST

    // =========================== Anime Details ============================

    // Function to fetch anime status using AniList GraphQL API ispired by OppaiStream.kt
    private fun fetchStatusByTitle(title: String): Int {
        val query =
            """
            query {
            	Media(
                  search: "$title",
                  sort: STATUS_DESC,
                  status_not_in: [NOT_YET_RELEASED],
                  format_not_in: [SPECIAL, MOVIE],
                  isAdult: false,
                  type: ANIME
                ) {
                  id
                  idMal
                  title {
                    romaji
                    native
                    english
                  }
                  status
                }
            }
            """.trimIndent()

        val requestBody = FormBody.Builder().add("query", query).build()

        val response =
            noTokenClient
                .newCall(
                    POST("https://graphql.anilist.co", body = requestBody),
                ).execute()
                .body
                .string()

        val responseParsed = JSON.decodeFromString<AnilistResult>(response)

        return when (responseParsed.data.media?.status) {
            "FINISHED" -> SAnime.COMPLETED
            "RELEASING" -> SAnime.ONGOING
            "CANCELLED" -> SAnime.CANCELLED
            "HIATUS" -> SAnime.ON_HIATUS
            else -> SAnime.UNKNOWN
        }
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val mediaId = JSON.decodeFromString<LinkData>(anime.url)
        val resp =
            client
                .newCall(
                    if (mediaId.media_type == "series") {
                        GET("$crApiUrl/cms/series/${mediaId.id}?locale=en-US")
                    } else {
                        GET("$crApiUrl/cms/movie_listings/${mediaId.id}?locale=en-US")
                    },
                ).execute()
                .body
                .string()
        val info = JSON.decodeFromString<AnimeResult>(resp)
        return info.data.first().toSAnimeOrNull(anime) ?: anime
    }

    override fun animeDetailsParse(response: Response): SAnime = throw UnsupportedOperationException()

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request {
        val mediaId = JSON.decodeFromString<LinkData>(anime.url)
        return if (mediaId.media_type == "series") {
            GET("$crApiUrl/cms/series/${mediaId.id}/seasons")
        } else {
            GET("$crApiUrl/cms/movie_listings/${mediaId.id}/movies")
        }
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val seasons = JSON.decodeFromString<SeasonResult>(response.body.string())
        val series =
            response.request.url.encodedPath
                .contains("series/")
        val chunkSize = Runtime.getRuntime().availableProcessors()
        return if (series) {
            seasons.data
                .sortedBy { it.season_number }
                .chunked(chunkSize)
                .flatMap { chunk ->
                    chunk.parallelCatchingFlatMapBlocking(::getEpisodes)
                }.reversed()
        } else {
            seasons.data.mapIndexed { index, movie ->
                SEpisode.create().apply {
                    url = EpisodeData(listOf(Triple(movie.id, "", movie.id))).toJsonString()
                    name = "Movie ${index + 1}"
                    episode_number = (index + 1).toFloat()
                    date_upload = movie.date?.let(::parseDate) ?: 0L
                }
            }
        }
    }

    private fun getEpisodes(seasonData: SeasonResult.Season): List<SEpisode> {
        val body =
            client
                .newCall(GET("$crApiUrl/cms/seasons/${seasonData.id}/episodes"))
                .execute()
                .body
                .string()
        val episodes = JSON.decodeFromString<EpisodeResult>(body)

        return episodes.data.sortedBy { it.episodeNumber }.mapNotNull EpisodeMap@{ ep ->
            SEpisode.create().apply {
                url =
                    EpisodeData(
                        ep.versions?.map {
                            Triple(
                                it.mediaId,
                                it.audioLocale,
                                it.isPremiumOnly.toString(),
                            )
                        } ?: listOf(
                            Triple(
                                ep.streamsLink?.substringAfter("videos/")?.substringBefore("/streams")
                                    ?: return@EpisodeMap null,
                                ep.audioLocale,
                                ep.episodeId,
                            ),
                        ),
                    ).toJsonString()
                name =
                    if (ep.episodeNumber > 0 && ep.episode.isNumeric()) {
                        "Season ${seasonData.season_number} Ep ${DF.format(ep.episodeNumber)}: " + ep.title
                    } else {
                        ep.title
                    }
                episode_number = ep.episodeNumber
                date_upload = ep.airDate?.let(::parseDate) ?: 0L
                scanlator = ep.versions?.sortedBy { it.audioLocale }?.joinToString {
                    buildString {
                        append(it.audioLocale.substringBefore("-"))
                        if (it.isPremiumOnly) append("💰")
                    }
                } ?: ep.audioLocale.substringBefore("-")
            }
        }
    }

    override fun seasonListParse(response: Response): List<SAnime> = throw UnsupportedOperationException()

    // ============================ Video Links =============================

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val urlJson = JSON.decodeFromString<EpisodeData>(episode.url)
        Log.i("Yomiroll", "Fetching hosters for episode with IDs: ${urlJson.ids}")
        val dubLocale = preferences.getString(PREF_AUD_KEY, PREF_AUD_DEFAULT)!!

        if (urlJson.ids.isEmpty()) throw Exception("No IDs found for episode")

        return urlJson.ids.parallelCatchingFlatMap { idData ->
            listOf(
                Hoster(
                    hosterUrl = HosterData(idData).toJsonString(),
                    hosterName =
                        buildString {
                            append(idData.second.getLocale())
                            if (idData.third == "true") append(" 💰")
                        },
                    lazy = idData.second != dubLocale,
                ),
            )
        }
    }

    override suspend fun getVideoList(hoster: Hoster): List<Video> {
        Log.i("Yomiroll", "Getting video list for hoster: ${hoster.hosterName}")
        val urlJson = JSON.decodeFromString<HosterData>(hoster.hosterUrl)
        Log.i("Yomiroll", "Fetching videos for id: ${urlJson.id}")
        return extractVideo(urlJson.id).sort()
    }

    override fun hosterListParse(response: Response): List<Hoster> = throw UnsupportedOperationException()

    // ============================= Utilities ==============================

    private suspend fun extractVideo(media: Triple<String, String, String>): List<Video> {
        runCatching {
            preferences.wvdDevice.ifBlank { throw Exception("No Widevine device") }
            Device.loads(preferences.wvdDevice)
        }.getOrElse { err ->
            Log.e(
                "Yomiroll",
                "Failed to load Widevine device from preferences: ${err.localizedMessage}",
            )
            throw Exception("WVD: ${err.localizedMessage}")
        }

        val (mediaId, aud, isPremiumOnly) = media
        Log.i(
            "Yomiroll",
            "Extracting video for mediaId: $mediaId, aud: $aud, isPremiumOnly: $isPremiumOnly",
        )

        val response =
            client
                .newCall(getVideoRequest(mediaId))
                .execute()
                .body
                .string()
        val streams = JSON.decodeFromString<VideoStreams>(response)
        Log.i("Yomiroll", "Streams fetched: ${streams.url}")

        val subLocale = preferences.getString(PREF_SUB_KEY, PREF_SUB_DEFAULT)!!.getLocale()
        val subsList =
            runCatching {
                streams.subtitles
                    ?.entries
                    ?.mapNotNull { (_, value) ->
                        val sub = JSON.decodeFromString<Subtitle>(value.jsonObject.toString())
                        sub.url?.let { Track(it, sub.language.getLocale()) }
                    }?.sortedWith(
                        compareByDescending<Track> { it.lang.contains(subLocale) }.thenBy { it.lang },
                    )
            }.getOrNull() ?: emptyList()

        return getStreams(streams, subsList, mediaId)
    }

    private suspend fun getStreams(
        streams: VideoStreams,
        subsList: List<Track>,
        mediaId: String,
    ): List<Video> {
        Log.i("Yomiroll", "Getting streams for mediaId: $mediaId")
        val playlist = client.newCall(GET(streams.url)).execute()
        if (playlist.code != 200) throw Exception("Failed to fetch playlist")

        val playlistResp = playlist.body.use { it.string() }
        val doc = Jsoup.parse(playlistResp, Parser.xmlParser())

        doc.select("adaptationset[mimetype*=text]").remove()
        val psshB64 =
            doc
                .selectFirst("contentprotection[schemeiduri*=edef8ba9-79d6-4ace-a3c8-27dcd51d21ed]")
                ?.text() ?: ""

        val argsMpv = mutableListOf<Pair<String, String>>()
        val argsFFM = mutableListOf<Pair<String, String>>()

        getWidevineKeyExtractor(
            mediaId,
            streams.token,
            psshB64,
        )?.map { (keyType, key) ->
            argsMpv.addAll(
                listOf(
                    Pair("demuxer-lavf-o", "${keyType.type}=$key"),
                    Pair("tls-verify", "no"),
                ),
            )
            argsFFM.addAll(
                listOf(
                    Pair(keyType.type, key),
                    Pair("tls_verify", "0"),
                ),
            )
        }

        val skipId = streams.versions.find { v -> v.original }?.mediaId ?: mediaId

        val skipTimes =
            runCatching {
                client
                    .newCall(GET("https://static.crunchyroll.com/skip-events/production/$skipId.json".toHttpUrlOrNull()!!))
                    .execute()
                    .parseAs<MediaSegmentsResponse>()
                    .let { resp ->
                        listOf(
                            resp.intro,
                            resp.credits,
                            resp.recap,
                            resp.preview,
                        ).mapNotNull { timestamp ->
                            // CR sometimes send empty object for timestamp
                            if (timestamp == null || timestamp.start == null || timestamp.end == null || timestamp.type == null) {
                                return@mapNotNull null
                            }

                            TimeStamp(
                                start = timestamp.start,
                                end = timestamp.end,
                                name = timestamp.type.replaceFirstChar { it.uppercaseChar() },
                                type =
                                    when (timestamp.type) {
                                        "intro" -> ChapterType.Opening
                                        "credits" -> ChapterType.Ending
                                        "recap" -> ChapterType.Recap
                                        else -> ChapterType.Other
                                    },
                            )
                        }
                    }
            }.getOrElse { err ->
                Log.e("Yomiroll", "Failed to fetch skip times: ${err.localizedMessage}", err)
                emptyList()
            }

        mainScope.launch {
            async {
                withContext(Dispatchers.IO) {
                    delay(2_000)
                    runCatching {
                        client
                            .newCall(
                                Request(
                                    url = "$baseUrl/playback/v1/token/$mediaId/${streams.token}".toHttpUrlOrNull()!!,
                                    method = "DELETE",
                                ),
                            ).execute()
                            .use {
                                Log.i(
                                    "Yomiroll",
                                    "Delete active stream status $mediaId: ${it.code}",
                                )
                            }
                    }.onFailure { e ->
                        Log.e("Yomiroll", "Failed to delete active stream token", e)
                    }
                }
            }
        }

        val videoSelector = "representation[mimeType*=video],representation[id*=video]"

        return doc.select(videoSelector).map { element ->
            val quality = element.attr("height")
            val docCopy = doc.clone()

            docCopy
                .select(videoSelector)
                .filter { it.attr("height") != quality }
                .forEach { it.remove() }

            val modifiedDoc = docCopy.toString()
            val file =
                File.createTempFile("manifest-$mediaId-$quality-", ".mpd").also(File::deleteOnExit)

            file.writeText(modifiedDoc)
            val uri = Uri.fromFile(file)

            Video(
                videoUrl = uri.toString(),
                resolution = quality.toIntOrNull(),
                videoTitle = "${quality}p ",
                subtitleTracks = subsList,
                mpvArgs = argsMpv,
                ffmpegVideoArgs = argsFFM,
                timestamps = skipTimes,
            )
        }
    }

    private suspend fun getWidevineKeyExtractor(
        mediaId: String,
        videoToken: String,
        psshBase64: String,
    ): List<Pair<DecryptionType, String>>? {
        Log.i("Yomiroll", "Getting Widevine key for mediaId: $mediaId")

        val device = Device.loads(preferences.wvdDevice)
        val cdm = Cdm.fromDevice(device)
        val sessionId = cdm.open()
        val pssh = PSSH(psshBase64)

        val challenge =
            cdm.getLicenseChallenge(
                sessionId = sessionId,
                pssh = pssh,
                licenseType = LicenseType.STREAMING,
                privacyMode = true,
            )

        val reqBody = challenge.toRequestBody("application/octet-stream".toMediaType())

        val headers =
            headersBuilder()
                .add("x-cr-content-id", mediaId)
                .add("x-cr-video-token", videoToken)
                .add("Referer", "https://static.crunchyroll.com/")
                .add("Accept", "*/*")
                .add("Accept-Encoding", "*")
                .build()

        val resp =
            client
                .newCall(
                    POST(
                        "$baseUrl/license/v1/license/widevine",
                        body = reqBody,
                        headers = headers,
                    ),
                ).execute()
                .body
                .use { it.string() }

        val licenseB64 = JSON.decodeFromString<LicenseResponse>(resp).license
        val license = Base64.decode(licenseB64)

        cdm.parseLicense(sessionId, license)

        return cdm.getKeys(sessionId, KeyType.CONTENT).map {
            Pair(DecryptionType.CENC_DECRYPTION_KEY, it.key.toHexString())
        }
    }

    private fun getVideoRequest(mediaId: String): Request = GET("$baseUrl/playback/v3/$mediaId/tv/android_tv/play?queue=0")

    private fun Anime.toSAnimeOrNull(anime: SAnime? = null) = runCatching { toSAnime(anime) }.getOrNull()

    private fun Anime.toSAnime(anime: SAnime? = null): SAnime =
        SAnime.create().apply {
            title = this@toSAnime.title
            thumbnail_url = images.poster_tall
                ?.getOrNull(0)
                ?.thirdLast()
                ?.source
                ?: images.poster_tall
                    ?.getOrNull(0)
                    ?.last()
                    ?.source
            url = anime?.url ?: LinkData(id, type!!).toJsonString()
            fetch_type = FetchType.Episodes
            genre = anime?.genre ?: (
                series_metadata?.genres ?: movie_metadata?.genres
                    ?: genres
            )?.joinToString { gen -> gen.replaceFirstChar { it.uppercase() } }
            status = anime?.let {
                val media = JSON.decodeFromString<LinkData>(anime.url)
                if (media.media_type == "series") {
                    fetchStatusByTitle(this@toSAnime.title)
                } else {
                    SAnime.COMPLETED
                }
            } ?: SAnime.UNKNOWN
            author = content_provider
            description =
                StringBuilder()
                    .apply {
                        appendLine(this@toSAnime.description)
                        appendLine()

                        append("Language:")
                        if ((
                                subtitle_locales ?: (
                                    series_metadata
                                        ?: movie_metadata
                                )?.subtitle_locales
                            )?.any() == true ||
                            (
                                series_metadata
                                    ?: movie_metadata
                            )?.is_subbed == true ||
                            is_subbed == true
                        ) {
                            append(" Sub")
                        }
                        if ((
                                (series_metadata?.audio_locales ?: audio_locales)?.size
                                    ?: 0
                            ) > 1 ||
                            (
                                series_metadata
                                    ?: movie_metadata
                            )?.is_dubbed == true ||
                            is_dubbed == true
                        ) {
                            append(" Dub")
                        }
                        appendLine()

                        append("Maturity Ratings: ")
                        appendLine(
                            (
                                (series_metadata ?: movie_metadata)?.maturity_ratings
                                    ?: maturity_ratings
                            )?.joinToString() ?: "-",
                        )
                        if (series_metadata?.is_simulcast == true) appendLine("Simulcast")
                        appendLine()

                        append("Audio: ")
                        appendLine(
                            (
                                series_metadata?.audio_locales ?: audio_locales ?: listOf(
                                    audio_locale ?: "-",
                                )
                            ).sortedBy { it.getLocale() }.joinToString { it.getLocale() },
                        )
                        appendLine()

                        append("Subs: ")
                        append(
                            (
                                subtitle_locales ?: series_metadata?.subtitle_locales
                                    ?: movie_metadata?.subtitle_locales
                            )?.sortedBy { it.getLocale() }
                                ?.joinToString { it.getLocale() },
                        )
                    }.toString()
        }

    fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString(PREF_QLT_KEY, PREF_QLT_DEFAULT)!!
        val dubLocale = preferences.getString(PREF_AUD_KEY, PREF_AUD_DEFAULT)!!
        val subLocale = preferences.getString(PREF_SUB_KEY, PREF_SUB_DEFAULT)!!
        val subType = preferences.getString(PREF_SUB_TYPE_KEY, PREF_SUB_TYPE_DEFAULT)!!
        val shouldContainHard = subType == "hard"

        return sortedWith(
            compareBy(
                { "${it.resolution}p".contains(quality) },
                { it.videoTitle.contains("Aud: ${dubLocale.getLocale()}") },
                { it.videoTitle.contains("HardSub") == shouldContainHard },
                { it.videoTitle.contains(subLocale) },
            ),
        ).reversed()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context)
            .apply {
                key = PREF_QLT_KEY
                title = PREF_QLT_TITLE
                entries = PREF_QLT_ENTRIES
                entryValues = PREF_QLT_VALUES
                setDefaultValue(PREF_QLT_DEFAULT)
                summary = "%s"

                setOnPreferenceChangeListener { _, newValue ->
                    val selected = newValue as String
                    val index = findIndexOfValue(selected)
                    val entry = entryValues[index] as String
                    preferences.edit().putString(key, entry).commit()
                }
            }.also(screen::addPreference)

        ListPreference(screen.context)
            .apply {
                key = PREF_AUD_KEY
                title = PREF_AUD_TITLE
                entries = LOCALE.map { it.second }.toTypedArray()
                entryValues = LOCALE.map { it.first }.toTypedArray()
                setDefaultValue(PREF_AUD_DEFAULT)
                summary = "%s"

                setOnPreferenceChangeListener { _, newValue ->
                    val selected = newValue as String
                    val index = findIndexOfValue(selected)
                    val entry = entryValues[index] as String
                    preferences.edit().putString(key, entry).commit()
                }
            }.also(screen::addPreference)

        ListPreference(screen.context)
            .apply {
                key = PREF_SUB_KEY
                title = PREF_SUB_TITLE
                entries = LOCALE.map { it.second }.toTypedArray()
                entryValues = LOCALE.map { it.first }.toTypedArray()
                setDefaultValue(PREF_SUB_DEFAULT)
                summary = "%s"

                setOnPreferenceChangeListener { _, newValue ->
                    val selected = newValue as String
                    val index = findIndexOfValue(selected)
                    val entry = entryValues[index] as String
                    preferences.edit().putString(key, entry).commit()
                }
            }.also(screen::addPreference)

        ListPreference(screen.context)
            .apply {
                key = PREF_SUB_TYPE_KEY
                title = PREF_SUB_TYPE_TITLE
                entries = PREF_SUB_TYPE_ENTRIES
                entryValues = PREF_SUB_TYPE_VALUES
                setDefaultValue(PREF_SUB_TYPE_DEFAULT)
                summary = "%s"

                setOnPreferenceChangeListener { _, newValue ->
                    val selected = newValue as String
                    val index = findIndexOfValue(selected)
                    val entry = entryValues[index] as String
                    preferences.edit().putString(key, entry).commit()
                }
            }.also(screen::addPreference)

        screen.addEditTextPreference(
            title = "Username",
            default = USERNAME_DEFAULT,
            summary = preferences.username.ifBlank { "Username of your CR account" },
            key = USERNAME_KEY,
            updateSummary = { newValue: String ->
                newValue.ifBlank { "Username of your CR account" }
            },
        )

        screen.addEditTextPreference(
            title = "Password",
            default = PASSWORD_DEFAULT,
            summary =
                preferences.password.let {
                    if (it.isBlank()) "Password of your CR account" else "•".repeat(it.length)
                },
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            key = PASSWORD_KEY,
            updateSummary = { newValue: String ->
                if (newValue.isBlank()) "Password of your CR account" else "•".repeat(newValue.length)
            },
        )

        screen.addEditTextPreference(
            title = "WVD Device",
            default = WVD_DEVICE_DEFAULT,
            summary =
                preferences.wvdDevice.let {
                    if (it.isBlank()) "Widevine Device (.wvd) in Base64" else "Device Set"
                },
            key = WVD_DEVICE_KEY,
            updateSummary = { newValue: String ->
                if (newValue.isBlank()) "Widevine Device (.wvd) in Base64" else "Device Set"
            },
        )

        screen.addEditTextPreference(
            title = "User-Agent",
            default = USER_AGENT_DEFAULT,
            summary = "${PREF_HEADER_WARNING_PREFIX}\n${preferences.userAgent.ifBlank { "User-Agent to use for CR" }}",
            key = USER_AGENT_KEY,
        )

        screen.addEditTextPreference(
            title = "Basic Auth",
            default = BASIC_AUTH_DEFAULT,
            summary = PREF_HEADER_WARNING_PREFIX,
            key = BASIC_AUTH_KEY,
        )

        screen.addPreference(localSubsPreference(screen))
    }

    // From Jellyfin
    private abstract class LocalSubsPreference(
        context: Context,
    ) : SwitchPreferenceCompat(context) {
        abstract fun reload()
    }

    private fun localSubsPreference(screen: PreferenceScreen) =
        object : LocalSubsPreference(screen.context) {
            override fun reload() {
                this.apply {
                    key = PREF_USE_LOCAL_TOKEN_KEY
                    title = "Use Local Token"
                    summary = "${PREF_LOCAL_TOKEN_SUMMARY_PREFIX}Loading..."
                    mainScope.launch(Dispatchers.IO) {
                        getTokenDetail().let {
                            withContext(Dispatchers.Main) {
                                summary = "${PREF_LOCAL_TOKEN_SUMMARY_PREFIX}$it"
                            }
                        }
                    }
                    setDefaultValue(false)
                    setOnPreferenceChangeListener { _, newValue ->
                        val new = newValue as Boolean
                        preferences.edit().putBoolean(key, new).commit().also {
                            mainScope.launch(Dispatchers.IO) {
                                getTokenDetail(true).let {
                                    withContext(Dispatchers.Main) {
                                        summary = "${PREF_LOCAL_TOKEN_SUMMARY_PREFIX}$it"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.apply { reload() }

    private fun getTokenDetail(force: Boolean = false): String =
        runCatching {
            val storedToken = tokenInterceptor.getAccessToken(force)
            "Token location: " + storedToken.bucket?.substringAfter("/")?.substringBefore("/")
        }.getOrElse {
            tokenInterceptor.removeToken()
            "Error: ${it.localizedMessage ?: "Something Went Wrong"}"
        }

    companion object {
        val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
        }

        private const val PREF_QLT_KEY = "preferred_quality"
        private const val PREF_QLT_TITLE = "Preferred quality"
        private const val PREF_QLT_DEFAULT = "1080p"
        private val PREF_QLT_ENTRIES = arrayOf("1080p", "720p", "480p", "360p", "240p", "80p")
        private val PREF_QLT_VALUES = PREF_QLT_ENTRIES

        private const val PREF_AUD_KEY = "preferred_audio"
        private const val PREF_AUD_TITLE = "Preferred Audio Language"
        private const val PREF_AUD_DEFAULT = "en-US"

        private const val PREF_SUB_KEY = "preferred_sub"
        private const val PREF_SUB_TITLE = "Preferred Sub Language"
        private const val PREF_SUB_DEFAULT = "en-US"

        private const val PREF_SUB_TYPE_KEY = "preferred_sub_type"
        private const val PREF_SUB_TYPE_TITLE = "Preferred Sub Type"
        private const val PREF_SUB_TYPE_DEFAULT = "soft"
        private val PREF_SUB_TYPE_ENTRIES = arrayOf("Softsub", "Hardsub")
        private val PREF_SUB_TYPE_VALUES = arrayOf("soft", "hard")

        const val USERNAME_KEY = "username"
        const val USERNAME_DEFAULT = ""

        const val PASSWORD_KEY = "password"
        const val PASSWORD_DEFAULT = ""

        const val WVD_DEVICE_KEY = "wvd_device"
        const val WVD_DEVICE_DEFAULT = ""

        const val USER_AGENT_KEY = "user_agent"
        const val USER_AGENT_DEFAULT = "ANDROIDTV/3.42.1_22273 Android/16"

        const val BASIC_AUTH_KEY = "basic_auth"
        const val BASIC_AUTH_DEFAULT =
            "Y2I5bnpybWh0MzJ2Z3RleHlna286S1V3bU1qSlh4eHVyc0hJVGQxenZsMkMyeVFhUW84TjQ="

        const val DEVICE_ID_KEY = "device_id"

        const val PREF_USE_LOCAL_TOKEN_KEY = "preferred_local_Token"
        const val PREF_LOCAL_TOKEN_SUMMARY_PREFIX =
            "Don't Spam this please! Proxy by:\ngithub.com/MeGaNeKoS/CR-Unblocker\n\n"
        const val PREF_HEADER_WARNING_PREFIX = "Don't change unless you know what you're doing"
    }
}
