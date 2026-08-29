package com.jiotvplus.app.data.repository

import com.jiotvplus.app.data.model.*
import com.jiotvplus.app.data.remote.JioContentApi
import com.jiotvplus.app.data.remote.JioPlaybackApi
import com.jiotvplus.app.data.prefs.TokenStore
import com.jiotvplus.app.ui.player.StreamConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    private val contentApi: JioContentApi,
    private val playbackApi: JioPlaybackApi,
    private val tokenStore: TokenStore
) {

    companion object {
        private const val VERSION_CODE = "67584484"
        private const val CDN_USER_AGENT = "JioTV.Plus/6.0.8 (Linux;Android 9) AndroidXMedia3/1.1.1"
    }

    suspend fun getChannels(): Result<List<Channel>> = runCatching {
        val response = contentApi.getChannels()
        val body = response.body()
        if (response.isSuccessful && body != null && body.code == 200) {
            body.data.values.sortedBy { it.order }
        } else {
            throw Exception("Failed to load channels (HTTP ${response.code()})")
        }
    }

    suspend fun getGenreFilters(): Result<List<GenreItem>> = runCatching {
        val response = contentApi.getGenreFilters()
        val body = response.body()
        if (response.isSuccessful && body != null && body.code == 200) {
            body.data?.filters ?: emptyList()
        } else {
            throw Exception("Failed to load genre filters (HTTP ${response.code()})")
        }
    }

    /**
     * Returns stream config (URL + CDN headers required by algo==8 streams).
     * Picks DASH (mpd) for HD quality when available, falls back to HLS (m3u8).
     */
    suspend fun getPlaybackStream(contentId: String): Result<StreamConfig> = runCatching {
        val deviceId = tokenStore.getDeviceId() ?: "3a8556f08a8128fe"
        val subscriberId = tokenStore.getSubscriberId() ?: ""
        val userId = tokenStore.getUserId() ?: ""
        val ssoToken = tokenStore.getSsoToken() ?: ""
        val authToken = tokenStore.getAuthToken() ?: ""

        val response = playbackApi.getPlayback(
            contentId = contentId,
            request = PlaybackRequest(serialNo = deviceId)
        )
        val body = response.body()
        if (!response.isSuccessful || body == null || body.code != 200) {
            throw Exception("Playback request failed (HTTP ${response.code()})")
        }

        val data = body.data ?: throw Exception("No playback data")

        val (url, isDash) = pickStreamUrl(data)
            ?: throw Exception("No playback URL available for channel $contentId")

        // CDN needs User-Agent. HLS key server (tv.media.jio.com) needs auth headers.
        val headers = buildMap {
            put("User-Agent", CDN_USER_AGENT)
            if (authToken.isNotBlank()) put("x-accesstoken", authToken)
            if (deviceId.isNotBlank()) put("deviceid", deviceId)
            if (userId.isNotBlank()) put("uniqueid", userId)
            if (subscriberId.isNotBlank()) put("subid", subscriberId)
            if (ssoToken.isNotBlank()) put("ssotoken", ssoToken)
        }

        StreamConfig(url = url, headers = headers, isDash = isDash, keyUrl = data.keyURL)
    }

    private fun pickStreamUrl(data: PlaybackData): Pair<String, Boolean>? {
        // Prefer HLS (m3u8) first — works without DRM on emulator
        val m3u8 = data.m3u8
        m3u8?.auto?.takeIf { it.isNotBlank() }?.let { return it to false }
        m3u8?.high?.takeIf { it.isNotBlank() }?.let { return it to false }
        m3u8?.medium?.takeIf { it.isNotBlank() }?.let { return it to false }
        m3u8?.low?.takeIf { it.isNotBlank() }?.let { return it to false }

        // Fall back to DASH (mpd) — primary HD stream, needs Widevine DRM
        val mpd = data.mpd
        mpd?.auto?.takeIf { it.isNotBlank() }?.let { return it to true }
        mpd?.high?.takeIf { it.isNotBlank() }?.let { return it to true }
        mpd?.medium?.takeIf { it.isNotBlank() }?.let { return it to true }
        mpd?.low?.takeIf { it.isNotBlank() }?.let { return it to true }

        return null
    }

    suspend fun getPlaybackUrl(contentId: String): Result<String> =
        getPlaybackStream(contentId).map { it.url }

    suspend fun getEpg(contentIds: List<String>): Result<Map<String, List<EpgItem>>> = runCatching {
        val idsJson = "[${contentIds.joinToString(",") { "\"$it\"" }}]"
        val response = contentApi.getEpg(contentIds = idsJson, offsets = "[0,1,2]")
        val body = response.body()
        if (response.isSuccessful && body != null && body.code == 200) {
            body.data ?: emptyMap()
        } else {
            throw Exception("EPG request failed (HTTP ${response.code()})")
        }
    }
}
