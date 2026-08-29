package com.jiotvplus.app.data.remote

import com.jiotvplus.app.data.model.TokenExchangeRequest
import com.jiotvplus.app.data.model.TokenExchangeResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST

interface JioTokenApi {

    @POST("userservice/apis/v1/loginotp/exchangetoken")
    suspend fun exchangeToken(
        @HeaderMap headers: Map<String, String>,
        @Body request: TokenExchangeRequest
    ): Response<TokenExchangeResponse>
}
