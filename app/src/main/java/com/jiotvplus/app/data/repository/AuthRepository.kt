package com.jiotvplus.app.data.repository

import android.util.Base64
import com.jiotvplus.app.data.model.*
import com.jiotvplus.app.data.prefs.TokenStore
import com.jiotvplus.app.data.remote.JioAuthApi
import com.jiotvplus.app.data.remote.JioTokenApi
import com.jiotvplus.app.util.DeviceUtils
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: JioAuthApi,
    private val tokenApi: JioTokenApi,
    private val tokenStore: TokenStore
) {

    val isLoggedIn: Flow<Boolean> = tokenStore.isLoggedIn

    companion object {
        private const val SESSION_ID = "fa06b053-5b38-4c5b-b9f0-6459827b"
        private const val VERSION_CODE = "67584484"
        private const val DEVICE_MODEL = "sdk_google_atv_x86"
        private const val OS_VERSION = "9"
    }

    private fun decodeJwtExp(token: String): Long {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return 0
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
            val json = JSONObject(payload)
            json.optLong("exp", 0)
        } catch (e: Exception) {
            0
        }
    }

    suspend fun isTokenExpired(): Boolean {
        val token = tokenStore.getAuthToken() ?: return true
        val exp = decodeJwtExp(token)
        if (exp == 0L) return false
        val now = System.currentTimeMillis() / 1000
        return now >= exp - 120
    }

    suspend fun refreshTokenIfNeeded(): Boolean {
        if (!isTokenExpired()) return true
        return refreshToken()
    }

    suspend fun refreshToken(): Boolean {
        val refreshToken = tokenStore.getRefreshToken() ?: return false
        val ssoToken = tokenStore.getSsoToken() ?: return false
        val deviceId = tokenStore.getDeviceId() ?: return false
        val subscriberId = tokenStore.getSubscriberId() ?: return false
        val phone = tokenStore.getPhoneNumber() ?: return false

        return try {
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
                "x-apisignatures" to "37ca682625d7",
                "x-feature-code" to "ce1eb674jdkc",
                "x-appname" to "JioTVPlus",
                "content-type" to "application/json; charset=UTF-8",
                "user-agent" to "ktor-client"
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
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendOtp(phone: String, deviceId: String): Result<String> = runCatching {
        val response = authApi.sendOtp(
            number = phone,
            identifierId = "",
            deviceId = deviceId,
            osVersion = OS_VERSION,
            deviceModel = DEVICE_MODEL,
            uniqueId = deviceId,
            sessionId = SESSION_ID,
            versionCode = VERSION_CODE
        )
        val body = response.body()
        if (response.isSuccessful && body != null && body.code == 200) {
            tokenStore.saveIdentifier(body.identifier)
            body.identifier
        } else {
            throw Exception(body?.message ?: "Failed to send OTP (HTTP ${response.code()})")
        }
    }

    suspend fun verifyOtp(
        identifier: String,
        otp: String,
        deviceId: String
    ): Result<VerifyOtpResponse> = runCatching {
        val deviceName = "unknown $DEVICE_MODEL"
        val request = VerifyOtpRequest(
            identifier = identifier,
            otp = otp,
            deviceInfo = DeviceInfo(
                consumptionDeviceName = deviceName,
                info = DeviceInfoDetails(
                    platform = Platform(name = deviceName),
                    androidId = deviceId
                )
            )
        )
        val response = authApi.verifyOtp(
            request = request,
            deviceId = deviceId,
            osVersion = OS_VERSION,
            deviceModel = DEVICE_MODEL,
            uniqueId = deviceId,
            sessionId = SESSION_ID,
            versionCode = VERSION_CODE
        )
        val body = response.body()
        if (response.isSuccessful && body != null && body.code == 200) {
            body
        } else {
            throw Exception("OTP verification failed (HTTP ${response.code()})")
        }
    }

    suspend fun exchangeToken(
        phone: String,
        subscriberId: String,
        userId: String,
        ssoToken: String,
        deviceId: String
    ): Result<TokenExchangeResponse> = runCatching {
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
            "x-apisignatures" to "37ca682625d7",
            "x-feature-code" to "ce1eb674jdkc",
            "x-appname" to "JioTVPlus",
            "content-type" to "application/json; charset=UTF-8",
            "user-agent" to "ktor-client"
        )
        val response = tokenApi.exchangeToken(headers, TokenExchangeRequest(number = encodedPhone))
        val body = response.body()
        if (response.isSuccessful && body != null && body.authToken.isNotBlank()) {
            tokenStore.saveAuthSession(
                authToken = body.authToken,
                refreshToken = body.refreshToken,
                ssoToken = ssoToken,
                userId = body.userId,
                subscriberId = body.subscriberId,
                deviceId = deviceId,
                phoneNumber = phone
            )
            body
        } else {
            throw Exception("Token exchange failed (HTTP ${response.code()})")
        }
    }

    suspend fun logout() {
        tokenStore.clearSession()
    }
}
