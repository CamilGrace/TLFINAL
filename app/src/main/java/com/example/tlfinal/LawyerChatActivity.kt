package com.example.tlfinal

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tlfinal.databinding.ActivityLawyerChatBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.util.UUID

class LawyerChatActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "LawyerChatActivity"
    }

    private lateinit var binding: ActivityLawyerChatBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    // CHANGE: Use appropriate Adapter
    private lateinit var messageAdapter: LawyerMessageAdapter // Or MessageAdapter
    private val messagesList = mutableListOf<Message>()
    private var messagesListener: ListenerRegistration? = null

    // Variable names kept for clarity, but context changes:
    private var receiverId: String? = null // This will be the CLIENT's ID
    private var receiverName: String? = null // CLIENT's Name
    private var currentUserId: String? = null // This will be the LAWYER's ID
    private var conversationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // CHANGE: Use correct binding inflation
        binding = ActivityLawyerChatBinding.inflate(layoutInflater)
        setContentView(binding.root) // Ensure layout name matches binding if copied

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid // LAWYER's ID

        // Get data from Intent (sent from LawyerInboxActivity)
        receiverId = intent.getStringExtra("receiverId")       // CLIENT's ID
        receiverName = intent.getStringExtra("receiverName")    // CLIENT's Name
        val existingConversationId = intent.getStringExtra("conversationId")

        if (currentUserId == null) {
            Log.e("LawyerChatActivity", "Lawyer not logged in.")
            finish()
            return
        }
        if (receiverId == null) {
            Log.e("LawyerChatActivity", "Client ID (Receiver ID) is missing.")
            finish()
            return
        }

        // Determine or create Conversation ID (logic remains the same)
        conversationId = existingConversationId ?: createConversationId(currentUserId!!, receiverId!!)

        setupToolbar() // Displays Client's name
        setupRecyclerView()
        setupInputListeners()
        fetchMessages()
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.remove()
    }

    private fun setupToolbar() {
        binding.toolbarTitle.text = receiverName ?: "Client Chat" // Show Client Name
        binding.buttonBack.setOnClickListener { finish() }
        // TODO: Add listener for Info button if needed (e.g., show client details)
    }

    private fun setupRecyclerView() {
        // CHANGE: Instantiate correct adapter
        messageAdapter = LawyerMessageAdapter(messagesList) // Or MessageAdapter(messagesList)
        binding.messagesRecyclerView.apply { // Assumes binding names match
            layoutManager = LinearLayoutManager(this@LawyerChatActivity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }
    }

    private fun setupInputListeners() {
        // This logic remains exactly the same
        binding.messageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.buttonSend.visibility = if (s.isNullOrBlank()) View.GONE else View.VISIBLE
                // Toggle attach/emoji visibility based on send button
                val isTextEmpty = s.isNullOrBlank()
                binding.buttonAttach.visibility = if(isTextEmpty) View.VISIBLE else View.GONE
                binding.buttonEmoji.visibility = if(isTextEmpty) View.VISIBLE else View.GONE
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

    // This helper function remains the same
    private fun createConversationId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
    }

    private fun fetchMessages() {
        if (conversationId == null) {
            Log.e(TAG, "Conversation ID is null, cannot fetch messages.")
            return
        }
        Log.d(TAG, "Fetching messages for conversation ID: $conversationId")


        messagesListener = firestore.collection("messages")
            .whereEqualTo("conversationId", conversationId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Messages listen failed.", e)
                    // Optionally show an error message to the user
                    return@addSnapshotListener
                }
                if (snapshots == null) {
                    Log.w(TAG, "Messages snapshot listener returned null snapshots.")
                    return@addSnapshotListener
                }

                Log.d(TAG, "Messages snapshot received with ${snapshots.documentChanges.size} changes.")
                var listChanged = false
                for (dc in snapshots.documentChanges) {
                    try { // Add try-catch around mapping
                        val message = dc.document.toObject(Message::class.java)?.copy(messageId = dc.document.id) // <<< CHECK Message data class mapping

                        if (message == null) {
                            Log.w(TAG, "Failed to map document ${dc.document.id} to Message object.")
                            continue // Skip this document change
                        }
                        // Log the fetched message content for debugging
                        Log.v(TAG, "Fetched/Modified Message: ID=${message.messageId}, Text='${message.text}', Sender=${message.senderId}, Timestamp=${message.timestamp}")

                        when (dc.type) {
                            DocumentChange.Type.ADDED -> {
                                if (!messagesList.any { it.messageId == message.messageId }) {
                                    messagesList.add(message)
                                    listChanged = true
                                    Log.d(TAG, "Message ADDED: ${message.messageId}")
                                } else {
                                    Log.d(TAG, "Message ADDED but already exists: ${message.messageId}")
                                }
                            }
                            DocumentChange.Type.MODIFIED -> {
                                val index = messagesList.indexOfFirst { it.messageId == message.messageId }
                                if (index != -1) {
                                    messagesList[index] = message
                                    listChanged = true
                                    Log.d(TAG, "Message MODIFIED: ${message.messageId}")
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                if (messagesList.removeAll { it.messageId == message.messageId }) {
                                    listChanged = true
                                    Log.d(TAG, "Message REMOVED: ${message.messageId}")
                                }
                            }
                        }
                    } catch (mapError: Exception) {
                        Log.e(TAG, "Error mapping message document ${dc.document.id}", mapError)
                    }
                } // End for loop

                if(listChanged) {
                    Log.d(TAG, "Updating adapter. Message list size: ${messagesList.size}")
                    messagesList.sortBy { it.timestamp } // Ensure order just in case
                    // Ensure adapter's update method and internal logic work correctly
                    messageAdapter.updateMessages(messagesList) // <<< CHECK ADAPTER'S updateMessages
                    if (messagesList.isNotEmpty()) {
                        binding.messagesRecyclerView.scrollToPosition(messagesList.size - 1)
                    }
                } else {
                    Log.d(TAG, "No effective changes to message list.")
                }
            }
    }


    private fun sendMessage(text: String) { /* ... Keep existing logic ... */
        if (currentUserId == null || receiverId == null || conversationId == null) { Log.e(TAG, "Cannot send message, missing IDs"); return }
        val messageId = UUID.randomUUID().toString()
        // Timestamp set by server usually, but can set client-side if needed immediately
        val message = Message(messageId = messageId, conversationId = conversationId!!, senderId = currentUserId!!, receiverId = receiverId!!, text = text, messageType = "TEXT", timestamp = Timestamp.now()) // Added client-side timestamp
        firestore.collection("messages").document(messageId).set(message)
            .addOnSuccessListener { Log.d(TAG, "Message sent: $messageId"); binding.messageEditText.text.clear(); updateConversationTimestamp(text, receiverName ?: "Client") }
            .addOnFailureListener { e -> Log.e(TAG, "Error sending message", e) }
    }

    // Modified slightly to potentially use known receiver name
    private fun updateConversationTimestamp(lastMessageText: String, clientName: String) { /* ... Keep existing logic ... */
        if (currentUserId == null || receiverId == null || conversationId == null) return
        val conversationRef = firestore.collection("conversations").document(conversationId!!)
        val lawyerName = auth.currentUser?.displayName ?: "Lawyer" // Or fetch real name
        val conversationData = mapOf("lastMessageText" to lastMessageText, "lastMessageTimestamp" to Timestamp.now(), "participantIds" to listOf(currentUserId!!, receiverId!!), "participantNames" to mapOf(currentUserId!! to lawyerName, receiverId!! to clientName))
        conversationRef.set(conversationData, SetOptions.merge()).addOnSuccessListener { Log.d(TAG, "Convo updated.") }.addOnFailureListener { e -> Log.w(TAG, "Error updating convo", e) }
    }

}