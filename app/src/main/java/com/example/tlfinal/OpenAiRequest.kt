package com.example.tlfinal

data class OpenAiRequest (
    val model:String,
    val messages:List<Message>,
    val store:Boolean? = true,

    )

data class Message(
    val role:String,
    val content:String
)