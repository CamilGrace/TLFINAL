package com.example.tlfinal // Or your preferred package

import com.google.firebase.Timestamp

// In Conversation.kt or similar file
data class Conversation(
    val conversationId: String = "",
    val participantIds: List<String> = listOf(),
    val participantNames: Map<String, String> = mapOf(), // UID -> Name
    val lastMessageText: String? = null, // Make nullable if it might be absent
    val lastMessageTimestamp: Timestamp? = null, // Use Firestore Timestamp
    val otherParticipantId: String = "", // Helper field
    val participantProfileUrls: Map<String, String?>? = null,
    val unreadCount: Long? = null // Assuming unread count is stored per user (e.g., Map<String, Long>)
    // If unreadCount is stored differently, adjust fetching/binding
)