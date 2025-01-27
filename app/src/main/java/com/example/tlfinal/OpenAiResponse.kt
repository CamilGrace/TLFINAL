package com.example.tlfinal

data class OpenAiResponse (
    val id: String,
    val `object`: String,
    val created: Int,
    val model: String,
    val choices: List<Choice>
)

data class Choice(
    val message: Message,
    val finish_reason:String,
    val index:Int
)