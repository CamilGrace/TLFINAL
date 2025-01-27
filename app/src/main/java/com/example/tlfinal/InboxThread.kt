package com.example.tlfinal.models

data class InboxThread(
    val clientId: String = "",
    val clientName: String = "",
    val lawyerId: String = "",
    val lawyerName: String = "",
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0L
)
