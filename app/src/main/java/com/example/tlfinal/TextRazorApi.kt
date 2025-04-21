package com.example.tlfinal

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface TextRazorApi {
    @Headers("Content-Type: application/json")
    @POST("analyze")
    fun analyze(
        @Header("X-TextRazor-Key") apiKey: String,
        @Body request: TextRazorRequest
    ): Call<TextRazorResponse>
}
