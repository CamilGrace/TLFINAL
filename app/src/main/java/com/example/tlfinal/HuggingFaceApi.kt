package com.example.tlfinal

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface HuggingFaceApi {
    @POST
    fun classifyText(
        @Url url: String, // Full URL, including model ID
        @Header("Authorization") authorization: String,
        @Body request: HuggingFaceRequest
    ): Call<HuggingFaceResponse> // Changed back to single object
}

// Data classes remain the same
data class HuggingFaceRequest(
    val inputs: String,
    val parameters: Parameters = Parameters()
)
data class Parameters(
    val candidate_labels: List<String> = listOf(),
    val multi_label: Boolean? = null
)

data class HuggingFaceResponse(
    val sequence: String,
    val labels: List<String>,
    val scores: List<Double>
)