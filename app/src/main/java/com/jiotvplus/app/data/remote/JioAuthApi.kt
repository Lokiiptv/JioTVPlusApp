package com.jiotvplus.app.data.remote

import com.jiotvplus.app.data.model.SendOtpResponse
import com.jiotvplus.app.data.model.VerifyOtpRequest
import com.jiotvplus.app.data.model.VerifyOtpResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface JioAuthApi {

    /**
     * sendotp: number + identifierid are HEADERS (not query params), body is empty.
     * The 6.0.8 APK sends ~25 headers on this call; the ones below marked as
     * @Header are dynamic, the rest are static @Headers.
     */
    @Headers(
        "app-name: RJIL_JioTVPlus",
        "x-api-key: l7xx61fae40fe3af4c93b02792ae12422a82",
        "appkey: NzNiMDhlYzQyNjJm",
        "devicetype: phone",
        "os: android",
        "usergroup: tvYR7NSNn7rymo3F",
        "languageId: 6",
        "userId: ",
        "crmid: ",
        "isott: false",
        "channel_id: -1",
        "langid: ",
        "camid: 1",
        "m-rating: 100",
        "ssotoken: ",
        "subscriberId: ",
        "lbcookie: 1",
        "Connection: close"
    )
    @POST("apis/v3.2/stbotplogin/sendotp")
    suspend fun sendOtp(
        @Header("number") number: String,
        @Header("identifierid") identifierId: String = "",
        @Header("x-platform") platform: String = "smartandroidtv",
        @Header("deviceId") deviceId: String,
        @Header("osVersion") osVersion: String,
        @Header("dm") deviceModel: String,
        @Header("uniqueId") uniqueId: String,
        @Header("session_id") sessionId: String,
        @Header("versionCode") versionCode: String
    ): Response<SendOtpResponse>

    @Headers(
        "app-name: RJIL_JioTVPlus",
        "x-api-key: l7xx61fae40fe3af4c93b02792ae12422a82",
        "appkey: NzNiMDhlYzQyNjJm",
        "devicetype: phone",
        "os: android",
        "usergroup: tvYR7NSNn7rymo3F",
        "languageId: 6",
        "userId: ",
        "crmid: ",
        "isott: false",
        "channel_id: -1",
        "langid: ",
        "camid: 1",
        "m-rating: 100",
        "ssotoken: ",
        "subscriberId: ",
        "lbcookie: 1",
        "Connection: close",
        "content-type: application/json; charset=utf-8"
    )
    @POST("apis/v3.2/stbotplogin/verifyotp")
    suspend fun verifyOtp(
        @Body request: VerifyOtpRequest,
        @Header("x-platform") platform: String = "smartandroidtv",
        @Header("deviceId") deviceId: String,
        @Header("osVersion") osVersion: String,
        @Header("dm") deviceModel: String,
        @Header("uniqueId") uniqueId: String,
        @Header("session_id") sessionId: String,
        @Header("versionCode") versionCode: String
    ): Response<VerifyOtpResponse>
}
