package com.jiotvplus.app.data.model

import com.google.gson.annotations.SerializedName

data class PlaybackRequest(
    val bitrateProfile: String = "xhdpi",
    val model: String = "sdk_google_atv_x86",
    val manufacturer: String = "unknown",
    val osVersion: String = "9",
    val serialNo: String,
    val is4kSupport: Boolean = true,
    val hevcSupport: Boolean = true
)

data class PlaybackApiResponse(
    val code: Int = 0,
    val playbackCode: Int = 0,
    val message: String = "",
    val data: PlaybackData? = null
)

data class PlaybackData(
    val m3u8: M3u8Urls? = null,
    val mpd: MpdUrls? = null,
    val fps: FpsUrls? = null,
    val keyURL: String? = null,
    val fairplayCertificate: String? = null,
    val playbackToken: String? = null,
    val nvAuthorizations: String? = null,
    val name: String? = null,
    val contentId: String? = null,
    val contentType: String? = null,
    val jioTVID: String? = null,
    val provider: String? = null,
    val vendor: String? = null,
    val description: String? = null,
    val defaultLanguage: String? = null,
    val maturityRating: String? = null,
    val images: String? = null,
    val scrubImage: String? = null,
    val duration: Long? = null,
    val totalDuration: Long? = null,
    val currentTime: Long? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val videoId: Long? = null,
    val algo: Int? = null,
    val algoName: String? = null,
    val enableLR: Boolean? = null,
    val customCatchupPosition: Int? = null,
    val extID: String? = null,
    @SerializedName("extSubID") val extSubID: String? = null,
    val nl: String? = null,
    val vodStitch: Boolean? = null,
    @SerializedName("VodStitchAds") val vodStitchAds: VodStitchAds? = null,
    val deeplinkDetails: DeeplinkDetails? = null,
    val credits: Credits? = null,
    val ads: PlaybackAds? = null
)

data class M3u8Urls(
    val low: String? = null,
    val medium: String? = null,
    val high: String? = null,
    val auto: String? = null
)

data class MpdUrls(
    val low: String? = null,
    val medium: String? = null,
    val high: String? = null,
    val auto: String? = null
)

data class FpsUrls(
    val low: String? = null,
    val medium: String? = null,
    val high: String? = null,
    val auto: String? = null
)

data class VodStitchAds(
    val VmapURL: String? = null
)

data class DeeplinkDetails(
    val token: String? = null
)

class Credits

data class PlaybackAds(
    val visible: Boolean? = null,
    val adsVendors: List<String>? = null,
    val pre: AdSlot? = null,
    val mid: AdSlot? = null,
    val end: AdSlot? = null
)

data class AdSlot(
    val visible: Boolean? = null,
    val adspotid: String? = null,
    val interval: String? = null
)
