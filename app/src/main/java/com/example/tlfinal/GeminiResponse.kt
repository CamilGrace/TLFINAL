package com.example.tlfinal

data class GeminiResponse(
    val generatedContent: List<GeneratedContent>?
)

data class GeneratedContent(
    val text: String
)
