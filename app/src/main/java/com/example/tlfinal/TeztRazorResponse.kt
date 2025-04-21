package com.example.tlfinal

data class TextRazorResponse(
    val response: ResponseData? = null
)

data class ResponseData(
    val topics: List<Topic>? = null
)

data class Topic(
    val id: Int? = null,
    val label: String? = null,
)