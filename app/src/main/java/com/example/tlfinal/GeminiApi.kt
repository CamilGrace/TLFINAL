package com.example.tlfinal

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface GeminiApi {
    @POST
    fun generateContent(
        @Url url: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): Call<GeminiResponse>
}