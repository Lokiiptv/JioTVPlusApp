package com.jiotvplus.app.data.remote

import com.jiotvplus.app.data.model.PlaybackApiResponse
import com.jiotvplus.app.data.model.PlaybackRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

interface JioPlaybackApi {

    @Headers("content-type: application/json; charset=UTF-8")
    @POST("playback/v2/{contentId}")
    suspend fun getPlayback(
        @Path("contentId") contentId: String,
        @Body request: PlaybackRequest,
        @Header("x-page") page: String = "Player"
    ): Response<PlaybackApiResponse>
}
