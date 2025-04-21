package com.example.tlfinal


data class TextRazorRequest(
    val text: String,
    val extractors: String,
    val apiKey: String
)