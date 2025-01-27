package com.example.tlfinal

data class HuggingFaceRequest(
    val inputs: String,
    val parameters: Parameters? = Parameters()
)

data class Parameters(
    val return_full_text: Boolean? = false,
    val truncation: Boolean? = false,
    val candidate_labels: List<String>? = null
)
