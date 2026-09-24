package eu.kanade.tachiyomi.animeextension.all.yomiroll

import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import java.text.DecimalFormat
import java.util.UUID
import kotlin.text.trim

// Jellyfin Utils

val JSON =
    Json {
        ignoreUnknownKeys = true
    }

val DF by lazy { DecimalFormat("0.#") }

// Add new locales to the bottom so it doesn't mess with pref indexes
val LOCALE =
    arrayOf(
        Pair("ar-ME", "Arabic"),
        Pair("ar-SA", "Arabic (Saudi Arabia)"),
        Pair("de-DE", "German"),
        Pair("en-US", "English"),
        Pair("en-IN", "English (India)"),
        Pair("es-419", "Spanish (América Latina)"),
        Pair("es-ES", "Spanish (España)"),
        Pair("fr-FR", "French"),
        Pair("ja-JP", "Japanese"),
        Pair("hi-IN", "Hindi"),
        Pair("it-IT", "Italian"),
        Pair("ko-KR", "Korean"),
        Pair("pt-BR", "Português (Brasil)"),
        Pair("pt-PT", "Português (Portugal)"),
        Pair("pl-PL", "Polish"),
        Pair("ru-RU", "Russian"),
        Pair("tr-TR", "Turkish"),
        Pair("uk-UK", "Ukrainian"),
        Pair("he-IL", "Hebrew"),
        Pair("ro-RO", "Romanian"),
        Pair("sv-SE", "Swedish"),
        Pair("zh-CN", "Chinese (PRC)"),
        Pair("zh-HK", "Chinese (Hong Kong)"),
        Pair("zh-TW", "Chinese (Taiwan)"),
        Pair("ca-ES", "Català"),
        Pair("id-ID", "Bahasa Indonesia"),
        Pair("ms-MY", "Bahasa Melayu"),
        Pair("ta-IN", "Tamil"),
        Pair("te-IN", "Telugu"),
        Pair("th-TH", "Thai"),
        Pair("vi-VN", "Vietnamese"),
    )

fun String.getLocale(): String = LOCALE.firstOrNull { it.first == this }?.second ?: ""

fun String?.isNumeric() = this?.toDoubleOrNull() != null

val SharedPreferences.username
    get() = getString(Yomiroll.USERNAME_KEY, Yomiroll.USERNAME_DEFAULT)!!.trim()

val SharedPreferences.password
    get() = getString(Yomiroll.PASSWORD_KEY, Yomiroll.PASSWORD_DEFAULT)!!.trim()

val SharedPreferences.refreshToken
    get() = getString(Yomiroll.REFRESH_TOKEN_KEY, Yomiroll.REFRESH_TOKEN_DEFAULT)!!.trim()

val SharedPreferences.wvdDevice
    get() = getString(Yomiroll.WVD_DEVICE_KEY, Yomiroll.WVD_DEVICE_DEFAULT)!!.trim()

val SharedPreferences.userAgent
    get() = getString(Yomiroll.USER_AGENT_KEY, Yomiroll.USER_AGENT_DEFAULT)!!.trim()

val SharedPreferences.basicAuth
    get() = getString(Yomiroll.BASIC_AUTH_KEY, Yomiroll.BASIC_AUTH_DEFAULT)!!.trim()

val SharedPreferences.deviceId
    get() = getString(Yomiroll.DEVICE_ID_KEY, UUID.randomUUID().toString())!!.trim()

val SharedPreferences.useLocalToken
    get() = getBoolean(Yomiroll.PREF_USE_LOCAL_TOKEN_KEY, false)

fun String.maskOr(fallback: String) = if (isBlank()) fallback else "•".repeat(length)
