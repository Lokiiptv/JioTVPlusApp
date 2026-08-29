package com.jiotvplus.app.data.model

data class GenreFilterResponse(
    val code: Int = 0,
    val message: String = "",
    val data: GenreFilterData? = null
)

data class GenreFilterData(
    val filters: List<GenreItem> = emptyList()
)

data class GenreItem(
    val genre: String = "",
    val languages: List<String> = emptyList(),
    val thumbnail: String? = null
)
