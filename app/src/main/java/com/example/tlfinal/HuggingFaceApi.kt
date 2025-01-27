package com.example.tlfinal

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface HuggingFaceApi {
    @POST
    fun classifyText(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body request: HuggingFaceRequest
    ): Call<HuggingFaceResponse>
}