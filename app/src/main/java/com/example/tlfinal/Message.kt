package com.example.tlfinal // Or your preferred package

import com.google.firebase.Timestamp


data class Message(
    val messageId: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "", // <<< MUST match Firestore field name
    val messageType: String = "TEXT",
    val timestamp: Timestamp? = null, // Use Firestore Timestamp
    val imageUrl: String? = null,
    val audioUrl: String? = null, // Add if you have audio
    val audioDuration: Long = 0 // Add if you have audio
) {
    // Add a no-argument constructor if using toObject()
    constructor() : this("", "", "", "", "", "TEXT", null, null, null, 0)
}