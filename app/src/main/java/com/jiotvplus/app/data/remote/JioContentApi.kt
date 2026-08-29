package com.jiotvplus.app.data.remote

import com.jiotvplus.app.data.model.ChannelListResponse
import com.jiotvplus.app.data.model.EpgApiResponse
import com.jiotvplus.app.data.model.GenreFilterResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface JioContentApi {

    @Headers("x-page: Metadata")
    @GET("metadata/v2/livechannels")
    suspend fun getChannels(): Response<ChannelListResponse>

    @Headers("x-page: Metadata")
    @GET("metadata/v2/livechannels/filters/genre")
    suspend fun getGenreFilters(): Response<GenreFilterResponse>

    @Headers("x-page: Player")
    @GET("metadata/v2/livechannels/epg")
    suspend fun getEpg(
        @Query("contentIds") contentIds: String,
        @Query("offsets") offsets: String = "[0,1,2]"
    ): Response<EpgApiResponse>
}
