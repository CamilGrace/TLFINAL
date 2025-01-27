package com.example.tlfinal

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ClientMessagingActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messagesAdapter: MessagesAdapter
    private lateinit var currentUserId: String
    private var lawyerId: String? = null // Replace with the lawyer's ID you want to chat with

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_messaging)

        // Initialize Firestore and FirebaseAuth
        firestore = FirebaseFirestore.getInstance()
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // Initialize Views
        messageInput = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)

        // Initialize RecyclerView
        messagesAdapter = MessagesAdapter(mutableListOf())
        messagesRecyclerView.layoutManager = LinearLayoutManager(this)
        messagesRecyclerView.adapter = messagesAdapter

        // Get Lawyer ID from Intent
        lawyerId = intent.getStringExtra("lawyerId") // Passed from ClientInboxActivity

        // Send message when the button is clicked
        sendButton.setOnClickListener { sendMessage() }

        // Load chat messages in real-time
        loadMessages()
    }

    // Function to send a message to Firestore
    private fun sendMessage() {
        val messageText = messageInput.text.toString()
        if (messageText.isNotBlank() && lawyerId != null) {
            val newMessageInbox = MessageInbox(
                text = messageText,
                timestamp = System.currentTimeMillis(),
                senderId = currentUserId,
                receiverId = lawyerId ?: ""
            )

            // Save message to Firestore (Firestore will handle the real-time sync)
            firestore.collection("messages")
                .add(newMessageInbox)
                .addOnSuccessListener {
                    messageInput.text.clear() // Clear the message input after sending
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Function to load messages in real-time from Firestore
    private fun loadMessages() {
        val messagesQuery: Query = firestore.collection("messages")
            .whereEqualTo("senderId", currentUserId)
            .whereEqualTo("receiverId", lawyerId)
            .orderBy("timestamp") // Order by timestamp to show messages in sequence

        // Real-time listener for new messages
        messagesQuery.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Toast.makeText(this, "Error loading messages", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                val messageInboxes = snapshot.documents.mapNotNull { it.toObject(MessageInbox::class.java) }
                messagesAdapter.updateMessages(messageInboxes)
            }
        }
    }
}
