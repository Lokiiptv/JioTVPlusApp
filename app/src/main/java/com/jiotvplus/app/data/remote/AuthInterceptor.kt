package com.jiotvplus.app.data.remote

import android.util.Base64
import com.jiotvplus.app.data.model.TokenExchangeRequest
import com.jiotvplus.app.data.prefs.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
    private val tokenApi: JioTokenApi
) : Interceptor {

    companion object {
        private const val X_API_SIGNATURES = "37ca682625d7"
        private const val X_FEATURE_CODE = "ce1eb674jdkc"
        private const val X_PLATFORM = "androidtv"
        private const val USER_AGENT = "ktor-client"
    }

    private fun decodeJwtExp(token: String): Long {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return 0
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
            JSONObject(payload).optLong("exp", 0)
        } catch (e: Exception) { 0 }
    }

    private var lastRefreshAttempt: Long = 0

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        runBlocking {
            refreshIfExpired()
        }

        val builder = original.newBuilder()
            .header("x-apisignatures", X_API_SIGNATURES)
            .header("x-feature-code", X_FEATURE_CODE)
            .header("x-platform", X_PLATFORM)
            .header("user-agent", USER_AGENT)
            .header("accept", "application/json")
            .header("accept-charset", "UTF-8")

        runBlocking {
            val authToken = tokenStore.getAuthToken()
            val deviceId = tokenStore.getDeviceId()
            val userId = tokenStore.getUserId()
            val subscriberId = tokenStore.getSubscriberId()
            val phoneNumber = tokenStore.getPhoneNumber()
            val ssoToken = tokenStore.getSsoToken()

            if (!authToken.isNullOrBlank()) {
                builder.header("x-accesstoken", authToken)
                if (!deviceId.isNullOrBlank()) builder.header("deviceid", deviceId)
                if (!userId.isNullOrBlank()) builder.header("uniqueid", userId)
                if (!subscriberId.isNullOrBlank()) builder.header("subid", subscriberId)
                if (!ssoToken.isNullOrBlank()) builder.header("ssotoken", ssoToken)
                if (!phoneNumber.isNullOrBlank()) {
                    val digits = phoneNumber.filter { it.isDigit() }.takeLast(10)
                    if (digits.length == 10) builder.header("rmn", "+91$digits")
                }
            }
        }

        val response = chain.proceed(builder.build())

        // If 419/401, try refresh and retry once
        if ((response.code == 419 || response.code == 401) && System.currentTimeMillis() - lastRefreshAttempt > 10_000) {
            response.close()
            runBlocking { refreshIfExpired(force = true) }
            return chain.proceed(builder.build())
        }

        return response
    }

    private suspend fun refreshIfExpired(force: Boolean = false) {
        val now = System.currentTimeMillis() / 1000
        if (!force && now - lastRefreshAttempt / 1000 < 60) return

        val token = tokenStore.getAuthToken() ?: return
        val exp = decodeJwtExp(token)
        if (!force && exp != 0L && now < exp - 120) return

        lastRefreshAttempt = System.currentTimeMillis()

        val refreshToken = tokenStore.getRefreshToken() ?: return
        val ssoToken = tokenStore.getSsoToken() ?: return
        val deviceId = tokenStore.getDeviceId() ?: return
        val subscriberId = tokenStore.getSubscriberId() ?: return
        val phone = tokenStore.getPhoneNumber() ?: return

        try {
            val encodedPhone = Base64.encodeToString(
                "+91${phone.filter { it.isDigit() }.takeLast(10)}".toByteArray(),
                Base64.NO_WRAP
            )
            val headers = mapOf(
                "appname" to "RJIL_JioTVPlus",
                "os" to "android",
                "subscriberid" to subscriberId,
                "persistentRefreshToken" to "true",
                "x-platform" to "jiotvplus-androidtv",
                "deviceid" to deviceId,
                "ssotoken" to ssoToken,
                "devicetype" to "tv",
                "cache-control" to "no-cache",
                "x-apisignatures" to X_API_SIGNATURES,
                "x-feature-code" to X_FEATURE_CODE,
                "x-appname" to "JioTVPlus",
                "content-type" to "application/json; charset=UTF-8",
                "user-agent" to USER_AGENT
            )
            val response = tokenApi.exchangeToken(headers, TokenExchangeRequest(number = encodedPhone))
            val body = response.body()
            if (response.isSuccessful && body != null && body.authToken.isNotBlank()) {
                tokenStore.saveAuthSession(
                    authToken = body.authToken,
                    refreshToken = body.refreshToken.ifBlank { refreshToken },
                    ssoToken = ssoToken,
                    userId = body.userId,
                    subscriberId = body.subscriberId,
                    deviceId = deviceId,
                    phoneNumber = phone
                )
            }
        } catch (e: Exception) {
            // Refresh failed — will retry on next request
        }
    }
}
