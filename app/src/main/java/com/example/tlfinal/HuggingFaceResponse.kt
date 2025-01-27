package com.example.tlfinal

data class HuggingFaceResponse(
    val sequence: String,
    val labels: List<String>,
    val scores: List<Double> // Added to handle score values returned from the API
)