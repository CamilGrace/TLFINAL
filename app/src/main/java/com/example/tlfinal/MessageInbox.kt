package com.example.tlfinal

data class MessageInbox(
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)
