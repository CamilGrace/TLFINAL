package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tlfinal.ConversationAdapter
import com.example.tlfinal.databinding.ActivityInboxBinding
import com.example.tlfinal.Conversation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp

class InboxActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInboxBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var conversationAdapter: ConversationAdapter
    private var conversationsListener: ListenerRegistration? = null
    private val conversationsList = mutableListOf<Conversation>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInboxBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) { // Check if client is logged in
            Log.e("InboxActivity", "Client not logged in.")
            // Handle not logged in state (e.g., redirect to login)
            finish()
            return
        }

        setupRecyclerView()
        setupBottomNavigation() // Implement if needed
        // TODO: Implement Search and More Options listeners

        fetchConversations()
    }

    override fun onDestroy() {
        super.onDestroy()
        conversationsListener?.remove() // Stop listening when activity is destroyed
    }

    private fun setupRecyclerView() {
        conversationAdapter = ConversationAdapter(this, conversationsList)
        binding.conversationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@InboxActivity)
            adapter = conversationAdapter
        }
    }

    private fun setupBottomNavigation() {
        // Make sure this uses the correct IDs and navigation logic for the CLIENT
        try {
            binding.bottomNavigation.selectedItemId = R.id.bottom_message // Select message icon
            binding.bottomNavigation.setOnNavigationItemSelectedListener { item ->
                if (item.itemId == binding.bottomNavigation.selectedItemId) return@setOnNavigationItemSelectedListener false
                when (item.itemId) {
                    R.id.bottom_message -> true // Already here
                    R.id.bottom_home -> { startActivity(Intent(this, ClientDashboardActivity::class.java)); overridePendingTransition(0,0); finish(); true }
                    R.id.bottom_settings -> { startActivity(Intent(this, ClientSettingsActivity::class.java)); overridePendingTransition(0,0); finish(); true }
                    else -> false
                }
            }
        } catch (e: Exception) {
            Log.e("InboxActivity", "Error setting up bottom navigation.", e)
        }
    }



    private fun fetchConversations() {
        val currentUserId = auth.currentUser?.uid // CLIENT's ID
        if (currentUserId == null) {
            Log.e("InboxActivity", "[Fetch] Current Client User ID is NULL. Aborting fetch.")
            binding.textNoConversations.text = "Please log in."
            binding.textNoConversations.visibility = View.VISIBLE
            binding.conversationsRecyclerView.visibility = View.GONE
            return
        }

        Log.d("InboxActivity", "[Fetch] Starting fetch for Client UID: $currentUserId")
        binding.textNoConversations.text = "Loading conversations..."
        binding.textNoConversations.visibility = View.VISIBLE
        binding.conversationsRecyclerView.visibility = View.GONE

        val query = firestore.collection("conversations")
            .whereArrayContains("participantIds", currentUserId) // Find conversations involving THIS client
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING) // Order by most recent message
        Log.d("InboxActivity", "[Fetch] Query built: Targeting conversations with participant $currentUserId")

        conversationsListener?.remove() // Remove previous listener

        conversationsListener = query.addSnapshotListener { snapshots, e ->
            Log.d("InboxActivity", "[Listener] Snapshot received.")

            if (isDestroyed || isFinishing) {
                Log.w("InboxActivity", "[Listener] Activity destroyed/finishing. Ignoring snapshot.")
                return@addSnapshotListener
            }

            if (e != null) {
                Log.e("InboxActivity", "[Listener] Listen failed.", e)
                binding.textNoConversations.text = "Error loading conversations."
                binding.textNoConversations.visibility = View.VISIBLE
                binding.conversationsRecyclerView.visibility = View.GONE
                return@addSnapshotListener
            }

            if (snapshots == null) {
                Log.w("InboxActivity", "[Listener] Snapshots object is NULL.")
                binding.textNoConversations.text = "Could not retrieve data."
                binding.textNoConversations.visibility = View.VISIBLE
                binding.conversationsRecyclerView.visibility = View.GONE
                return@addSnapshotListener
            }

            Log.d("InboxActivity", "[Listener] Processing ${snapshots.size()} documents.")
            val fetchedConversations = mutableListOf<Conversation>()

            for (doc in snapshots.documents) {
                Log.v("InboxActivity", "[Listener] --- Processing Doc ID: ${doc.id} ---")
                try {
                    val convoData = doc.data
                    if (convoData != null) {
                        // Check required fields
                        val participantIds = convoData["participantIds"] as? List<String>
                        val participantNamesMap = convoData["participantNames"] as? Map<String, String>
                        val timestamp = convoData["lastMessageTimestamp"] as? Timestamp

                        if (participantIds == null || participantIds.size != 2 || participantNamesMap == null || timestamp == null) {
                            Log.w("InboxActivity", "[Listener]   Skipping ${doc.id}: Missing required fields (participants, names, or timestamp).")
                            continue
                        }

                        // Fetch optional fields safely
                        val participantProfileUrlsMap = convoData["participantProfileUrls"] as? Map<String, String?> ?: emptyMap()
                        val lastMessage = convoData["lastMessageText"] as? String ?: "" // Use correct field name
                        val unreadCounts = convoData["unreadCounts"] as? Map<String, Long> ?: emptyMap() // Example

                        // Find the OTHER participant (the Lawyer)
                        val otherId = participantIds.firstOrNull { it != currentUserId } ?: ""
                        Log.v("InboxActivity", "[Listener]   otherId derived (Lawyer): '$otherId'")

                        if (otherId.isEmpty()) {
                            Log.w("InboxActivity", "[Listener]   Skipping ${doc.id}: Could not determine other participant ID.")
                            continue
                        }

                        val otherName = participantNamesMap[otherId] ?: "Unknown Lawyer" // Get lawyer's name
                        // Get unread count for THIS client
                        val currentUnread = unreadCounts[currentUserId] ?: 0

                        val mappedConvo = Conversation(
                            conversationId = doc.id, participantIds = participantIds,
                            participantNames = participantNamesMap, participantProfileUrls = participantProfileUrlsMap,
                            lastMessageText = lastMessage, lastMessageTimestamp = timestamp,
                            otherParticipantId = otherId, // Store Lawyer's ID as other participant
                            unreadCount = currentUnread
                        )
                        fetchedConversations.add(mappedConvo)
                        Log.v("InboxActivity", "[Listener]   Successfully mapped & added: ${mappedConvo.conversationId}")

                    } else { Log.w("InboxActivity", "[Listener]   Document data is null for ${doc.id}") }
                } catch (ex: Exception) { Log.e("InboxActivity", "[Listener]   EXCEPTION parsing ${doc.id}", ex) }
            } // End for loop

            Log.d("InboxActivity", "[Listener] Finished processing documents. Updating UI...")
            conversationsList.clear()
            conversationsList.addAll(fetchedConversations)
            conversationAdapter.updateList(conversationsList) // Use adapter's update method

            if (conversationsList.isEmpty()) {
                Log.d("InboxActivity", "[UI Update] List is empty.")
                binding.textNoConversations.text = "No conversations yet."
                binding.textNoConversations.visibility = View.VISIBLE
                binding.conversationsRecyclerView.visibility = View.GONE
            } else {
                Log.d("InboxActivity", "[UI Update] List has ${conversationsList.size} items.")
                binding.textNoConversations.visibility = View.GONE
                binding.conversationsRecyclerView.visibility = View.VISIBLE
            }
            Log.d("InboxActivity", "[Listener] UI Update complete.")
        }
    }
}