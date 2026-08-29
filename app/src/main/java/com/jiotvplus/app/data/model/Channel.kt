package com.jiotvplus.app.data.model

import com.google.gson.annotations.SerializedName

data class ChannelListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: Map<String, Channel> = emptyMap()
)

data class Channel(
    val contentId: String = "",
    val contentType: String = "",
    val name: String = "",
    val subtitle: String = "",
    val thumbnail: String = "",
    val still: String? = null,
    val stillFallback: String? = null,
    val description: String? = null,
    val maturityRating: String? = null,
    val provider: String? = null,
    val vendor: String? = null,
    val language: String = "",
    val genres: List<String> = emptyList(),
    val quality: String = "SD",
    val order: Int = 999,
    val channelNumber: Int = 0,
    val restriction: Int = 0,
    val isPremium: Boolean = false,
    val onAir: Boolean = false,
    val isPromoted: Boolean = false,
    val isDisney: Boolean = false,
    val isDynamic: Boolean = false,
    val subProvider: String? = null,
    val broadcaster: String? = null,
    val logoUrl: String? = null,
    val promoteLogo: String? = null,
    val openToNonJio: Boolean = false,
    val walledGardenAllowed: Boolean = false,
    val playbackType: String? = null,
    val playViaSDK: Boolean = false,
    val multicam: Boolean = false,
    val currentProgram: CurrentProgram? = null,
    val deepLink: DeepLink? = null
)

data class CurrentProgram(
    val title: String = "",
    val startEpoch: Long = 0L,
    val endEpoch: Long = 0L,
    val thumbnail: String? = null
)

data class DeepLink(
    val url: String = "",
    @SerializedName("package") val package_: String = ""
)
