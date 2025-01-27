package com.example.tlfinal

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface OpenAIAPI {
    @POST
    fun generateContent(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType:String = "application/json",
        @Body request: OpenAiRequest
    ): Call<OpenAiResponse>
}