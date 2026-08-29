package com.jiotvplus.app.data.model

data class EpgApiResponse(
    val code: Int = 0,
    val message: String = "",
    val data: Map<String, List<EpgItem>>? = null
)

data class EpgItem(
    val startEpoch: Long = 0L,
    val endEpoch: Long = 0L,
    val title: String = "",
    val description: String? = null,
    val thumbnail: String? = null,
    val programId: String? = null,
    val showId: String? = null,
    val catchupShowId: String? = null,
    val episodeCount: Int? = null,
    val programDate: String? = null
)
