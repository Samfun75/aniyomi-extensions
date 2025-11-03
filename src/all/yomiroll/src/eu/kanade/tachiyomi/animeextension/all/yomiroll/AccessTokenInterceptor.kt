package eu.kanade.tachiyomi.animeextension.all.yomiroll

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.text.MessageFormat
import java.text.SimpleDateFormat
import java.util.Locale

class AccessTokenInterceptor(
    private val crUrl: String,
    private val preferences: SharedPreferences,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val accessTokenN = getAccessToken()

        val request = chain.request().newRequestWithAccessToken(accessTokenN)
        val response = chain.proceed(request)

        when (response.code) {
            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                synchronized(this) {
                    response.close()
                    // Access token is refreshed in another thread. Check if it has changed.
                    val newAccessToken = getAccessToken()
                    if (accessTokenN != newAccessToken) {
                        return chain.proceed(
                            request.newRequestWithAccessToken(newAccessToken),
                        )
                    }
                    val refreshedToken = getAccessToken(true)
                    // Retry the request
                    return chain.proceed(
                        chain.request().newRequestWithAccessToken(refreshedToken),
                    )
                }
            }

            else -> return response
        }
    }

    private fun Request.newRequestWithAccessToken(tokenData: AccessToken): Request {
        return newBuilder().let {
            it.header("Authorization", "${tokenData.token_type} ${tokenData.access_token}")
            it.header("User-Agent", preferences.userAgent)
            val requestUrl = Uri.decode(url.toString())
            if (requestUrl.contains("/cms/v2")) {
                it.url(
                    MessageFormat.format(
                        requestUrl,
                        tokenData.bucket,
                        tokenData.policy,
                        tokenData.signature,
                        tokenData.key_pair_id,
                    ),
                )
            }
            it.build()
        }.also {
            Log.d("Yomiroll", "Authorization: ${tokenData.token_type} ${tokenData.access_token}")
        }
    }

    fun getAccessToken(force: Boolean = false): AccessToken {
        val token = preferences.getString(TOKEN_PREF_KEY, null)
        return if (!force && token != null) {
            token.toObject()
        } else {
            synchronized(this) {
                if (!preferences.useLocalToken) {
                    refreshAccessToken()
                } else {
                    refreshAccessToken(false)
                }
            }
        }
    }

    fun removeToken() {
        preferences.edit().putString(TOKEN_PREF_KEY, null).apply()
    }

    private fun refreshAccessToken(useProxy: Boolean = true): AccessToken {
        Log.i("Yomiroll", "Refreshing access token...")
        removeToken()
        val client = OkHttpClient().newBuilder().let {
            if (useProxy) {
                Authenticator.setDefault(
                    object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication {
                            return PasswordAuthentication(
                                "GeoBypassCommunity-US",
                                "UseWithRespect".toCharArray(),
                            )
                        }
                    },
                )
                it.proxy(
                    Proxy(
                        Proxy.Type.SOCKS,
                        InetSocketAddress("us.community-proxy.meganeko.dev", 5445),
                    ),
                )
                    .build()
            } else {
                it.build()
            }
        }
        val response = client.newCall(getRequest()).execute()
        if (response.code != 200) {
            val error = JSON.decodeFromString<TokenError>(response.body.string())
            throw Exception("${response.code} ${error.code ?: "Failed to authenticate"}")
        }

        val body = response.body.string()
        val parsedJson = JSON.decodeFromString<AccessToken>(body)

        val policy = client.newCall(
            GET("$crUrl/index/v2").newRequestWithAccessToken(parsedJson),
        ).execute()
        val policyJson = JSON.decodeFromString<Policy>(policy.body.string())
        val allTokens = AccessToken(
            parsedJson.access_token,
            parsedJson.token_type,
            policyJson.cms.policy,
            policyJson.cms.signature,
            policyJson.cms.key_pair_id,
            policyJson.cms.bucket,
            DATE_FORMATTER.parse(policyJson.cms.expires)?.time,
        )

        preferences.edit().putString(TOKEN_PREF_KEY, allTokens.toJsonString()).apply()
        return allTokens
    }

    private fun getRequest(): Request {
        val userName = preferences.username
        val password = preferences.password
        val userAgent = preferences.userAgent
        val basicAuth = preferences.basicAuth
        val deviceId = preferences.deviceId

        require(userName.isNotEmpty() && password.isNotEmpty()) {
            "Set your username and password in the extension settings."
        }

        val headers = Headers.Builder()
            .add("Content-Type", "application/x-www-form-urlencoded")
            .add("Authorization", "Basic $basicAuth")
            .add("User-Agent", userAgent)
            .build()
        val postBody =
            "grant_type=password&username=$userName&password=$password&scope=offline_access&device_type=CPH2449&device_id=$deviceId".toRequestBody(
                "application/x-www-form-urlencoded".toMediaType(),
            )
        return POST("$crUrl/auth/v1/token", headers, postBody)
    }

    companion object {
        private const val TOKEN_PREF_KEY = "access_token_data"

        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
        }
    }
}
