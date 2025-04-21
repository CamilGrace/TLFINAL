package com.example.tlfinal

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tlfinal.MessageAdapter
import com.example.tlfinal.databinding.ActivityChatBinding
import com.example.tlfinal.Conversation // Import Conversation model
import com.example.tlfinal.Message
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.util.UUID

class ChatActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatActivity" // Tag for logging
    }

    private lateinit var binding: ActivityChatBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var messageAdapter: MessageAdapter
    private val messagesList = mutableListOf<Message>()
    private var messagesListener: ListenerRegistration? = null

    private var receiverId: String? = null
    private var receiverName: String? = null
    private var currentUserId: String? = null
    private var conversationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid

        // Get data from Intent
        receiverId = intent.getStringExtra("receiverId")
        receiverName = intent.getStringExtra("receiverName")
        // Optional: Get existing conversation ID if passed from Inbox
        val existingConversationId = intent.getStringExtra("conversationId")


        if (currentUserId == null || receiverId == null) {
            Log.e("ChatActivity", "User ID or Receiver ID is missing.")
            finish() // Close activity if essential data is missing
            return
        }

        // Determine or create Conversation ID
        conversationId = existingConversationId ?: createConversationId(currentUserId!!, receiverId!!)

        setupToolbar()
        setupRecyclerView()
        setupInputListeners()

        fetchMessages()
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.remove() // Clean up listener
    }

    private fun setupToolbar() {
        binding.toolbarTitle.text = receiverName ?: "Chat"
        binding.buttonBack.setOnClickListener { finish() }
        // TODO: Add listener for Info button if needed
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(messagesList) // Instantiate client's message adapter
        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true // Keep scrolling to bottom
            }
            adapter = messageAdapter
        }
    }

    private fun setupInputListeners() {
        binding.messageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrBlank()) {
                    binding.buttonSend.visibility = View.GONE
                    binding.buttonAttach.visibility = View.VISIBLE // Or other logic
                    binding.buttonEmoji.visibility = View.VISIBLE
                } else {
                    binding.buttonSend.visibility = View.VISIBLE
                    binding.buttonAttach.visibility = View.GONE // Hide attach when send is visible
                    binding.buttonEmoji.visibility = View.GONE // Hide emoji when send is visible
                }
            }
        })

        binding.buttonSend.setOnClickListener {
            val messageText = binding.messageEditText.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
            }
        }

        // TODO: Add listeners for Attach and Emoji buttons
    }


    // Generates a consistent ID regardless of who starts the chat
    private fun createConversationId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
    }

    private fun fetchMessages() {
        if (conversationId == null) return

        Log.d(TAG, "Setting up message listener for conversation: $conversationId") // Add Log

        messagesListener = firestore.collection("messages")
            .whereEqualTo("conversationId", conversationId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Messages listen failed.", e)
                    return@addSnapshotListener
                }
                if (snapshots == null) {
                    Log.w(TAG, "Messages snapshot listener returned null snapshots.")
                    return@addSnapshotListener
                }

                Log.d(TAG, "Messages snapshot received with ${snapshots.documentChanges.size} changes.")
                var listUpdatedByListener = false // Track if listener made changes

                for (dc in snapshots.documentChanges) {
                    try {
                        val message = dc.document.toObject(Message::class.java)?.copy(messageId = dc.document.id)
                        if (message == null || message.timestamp == null) { // Ensure message and timestamp exist
                            Log.w(TAG, "Skipping invalid message from snapshot: ${dc.document.id}")
                            continue
                        }

                        when (dc.type) {
                            DocumentChange.Type.ADDED -> {
                                // Add only if it's not already in the list (handles local add)
                                if (!messagesList.any { it.messageId == message.messageId }) {
                                    messagesList.add(message)
                                    listUpdatedByListener = true
                                    Log.d(TAG, "Listener ADDED message: ${message.messageId}")
                                } else {
                                    Log.v(TAG, "Listener detected ADDED message already present locally: ${message.messageId}")
                                }
                            }
                            DocumentChange.Type.MODIFIED -> {
                                val index = messagesList.indexOfFirst { it.messageId == message.messageId }
                                if (index != -1) {
                                    messagesList[index] = message
                                    listUpdatedByListener = true
                                    Log.d(TAG, "Listener MODIFIED message: ${message.messageId}")
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                if (messagesList.removeAll { it.messageId == message.messageId }) {
                                    listUpdatedByListener = true
                                    Log.d(TAG, "Listener REMOVED message: ${message.messageId}")
                                }
                            }
                        }
                    } catch (mapError: Exception){
                        Log.e(TAG, "Error mapping message document ${dc.document.id} in listener", mapError)
                    }
                } // End for loop

                if (listUpdatedByListener) {
                    Log.d(TAG, "Updating adapter from listener. List size: ${messagesList.size}")
                    messagesList.sortBy { it.timestamp } // Sort by timestamp
                    messageAdapter.updateMessages(ArrayList(messagesList)) // Pass a new list copy
                    // Scroll only if the update came from the listener for a *new* message
                    if (snapshots.documentChanges.any { it.type == DocumentChange.Type.ADDED }){
                        binding.messagesRecyclerView.scrollToPosition(messagesList.size - 1)
                    }
                }
            }
    }


    private fun sendMessage(text: String) {
        if (currentUserId.isNullOrEmpty() || receiverId.isNullOrEmpty() || conversationId.isNullOrEmpty()) {
            Log.e(TAG, "Cannot send message, missing IDs (Client: $currentUserId, Lawyer: $receiverId, Convo: $conversationId)")
            Toast.makeText(this, "Error sending message.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.buttonSend.isEnabled = false // Disable send button temporarily

        val messageId = UUID.randomUUID().toString()
        val now = Timestamp.now()

        val message = Message(
            messageId = messageId,
            conversationId = conversationId!!,
            senderId = currentUserId!!,    // CLIENT is sending
            receiverId = receiverId!!,      // LAWYER is receiving
            text = text,
            messageType = "TEXT",
            timestamp = now // Use client-side timestamp for immediate display consistency
        )

        // Add locally first (as before)
        messagesList.add(message)
        messageAdapter.notifyItemInserted(messagesList.size - 1)
        binding.messagesRecyclerView.scrollToPosition(messagesList.size - 1)
        binding.messageEditText.text.clear()
        // Re-enable slightly later in Firestore callbacks or immediately if preferred
        // binding.buttonSend.isEnabled = true

        Log.d(TAG, "Attempting to save message to Firestore: $messageId")
        // Send to Firestore
        firestore.collection("messages").document(messageId)
            .set(message)
            .addOnSuccessListener {
                Log.d(TAG, "Message saved successfully to Firestore: $messageId")
                // Update conversation AFTER message is saved
                updateConversationTimestamp(text, receiverName ?: "Lawyer", now) // Pass the same timestamp
                binding.buttonSend.isEnabled = true // Re-enable after success
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error sending message to Firestore", e)
                Toast.makeText(this, "Failed to send message.", Toast.LENGTH_SHORT).show()
                // Optional: Remove local message or show error state
                messagesList.remove(message)
                messageAdapter.notifyDataSetChanged()
                binding.buttonSend.isEnabled = true
            }
    }

    // Function to update the 'conversations' collection when a message is sent
    private fun updateConversationTimestamp(lastMessageText: String, lawyerName: String, messageTimestamp: Timestamp) {
        if (currentUserId.isNullOrEmpty() || receiverId.isNullOrEmpty() || conversationId.isNullOrEmpty()) return

        val conversationRef = firestore.collection("conversations").document(conversationId!!)

        val clientName = auth.currentUser?.displayName ?: "Client" // Fetch if needed

        // Ensure participant names map has both current user and receiver
        val participantNames = mapOf(
            currentUserId!! to clientName,
            receiverId!! to lawyerName // Name passed from Intent
        )
        // TODO: Similarly update participantProfileUrls map if necessary

        // Prepare data for update/set merge
        val conversationUpdateData = mapOf(
            "lastMessageText" to lastMessageText,
            "lastMessageTimestamp" to messageTimestamp, // Use the timestamp of the message just sent
            "participantIds" to listOf(currentUserId!!, receiverId!!), // Ensure order doesn't matter, or keep consistent
            "participantNames" to participantNames
            // TODO: Add logic to increment lawyer's unread count here
            // "unreadCounts.LAWYER_UID" to FieldValue.increment(1) // Example using FieldValue
        )

        Log.d(TAG, "Updating conversation $conversationId with: $conversationUpdateData")
        // Use set with merge to create conversation if it doesn't exist, or update if it does
        conversationRef.set(conversationUpdateData, SetOptions.merge())
            .addOnSuccessListener { Log.d(TAG, "Conversation timestamp/last message updated by client.") }
            .addOnFailureListener { e -> Log.w(TAG, "Error updating conversation by client", e) }
    }
}