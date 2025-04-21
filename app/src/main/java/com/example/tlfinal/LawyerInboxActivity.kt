package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tlfinal.databinding.ActivityLawyerInboxBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp // Import Timestamp

class LawyerInboxActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLawyerInboxBinding // Correct binding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var conversationAdapter: LawyerConversationAdapter // Correct adapter type
    private var conversationsListener: ListenerRegistration? = null
    private val conversationsList = mutableListOf<Conversation>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLawyerInboxBinding.inflate(layoutInflater) // Correct binding
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) { /* ... handle not logged in ... */ finish(); return }

        setupRecyclerView()
        setupBottomNavigation()
        fetchConversations()
    }

    override fun onDestroy() {
        super.onDestroy()
        conversationsListener?.remove()
    }

    private fun setupRecyclerView() {
        conversationAdapter = LawyerConversationAdapter(this, conversationsList) // Correct adapter
        binding.conversationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@LawyerInboxActivity)
            adapter = conversationAdapter
        }
    }

    private fun setupBottomNavigation() { /* ... Keep or adjust lawyer's nav logic ... */
        try {
            binding.bottomNavigation.selectedItemId = R.id.bottom_message // Select message icon
            binding.bottomNavigation.setOnNavigationItemSelectedListener { item ->
                if (item.itemId == binding.bottomNavigation.selectedItemId) return@setOnNavigationItemSelectedListener false
                when (item.itemId) {
                    R.id.bottom_message -> true // Already here
                    R.id.bottom_home -> { startActivity(Intent(this, LawyerDashboardActivity::class.java)); overridePendingTransition(0,0); finish(); true }
                    R.id.bottom_settings -> { /* To Lawyer Settings */ true }
                    else -> false
                }
            }
        } catch (e: Exception) { Log.e("LawyerInboxActivity", "Error setup bottom nav", e) }
    }

    private fun fetchConversations() {
        val currentUserId = auth.currentUser?.uid ?: return

        binding.textNoConversations.text = "Loading conversations..."
        binding.textNoConversations.visibility = View.VISIBLE
        binding.conversationsRecyclerView.visibility = View.GONE

        conversationsListener = firestore.collection("conversations")
            .whereArrayContains("participantIds", currentUserId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (!isDestroyed && !isFinishing) { // Check activity state
                    if (e != null) {
                        Log.w("LawyerInboxActivity", "Listen failed.", e)
                        binding.textNoConversations.text = "Error loading conversations."
                        binding.textNoConversations.visibility = View.VISIBLE
                        binding.conversationsRecyclerView.visibility = View.GONE
                        return@addSnapshotListener
                    }
                    if (snapshots == null) { /* Handle null snapshot */ return@addSnapshotListener }

                    Log.d("LawyerInboxActivity", "Snapshot received: ${snapshots.size()} docs.")
                    val fetchedConversations = mutableListOf<Conversation>()
                    for (doc in snapshots.documents) {
                        Log.v("LawyerInboxActivity", "[Listener] --- Processing Doc ID: ${doc.id} ---")
                        try {
                            val convoData = doc.data
                            if (convoData != null) {
                                // Check required fields FIRST
                                val participantIds = convoData["participantIds"] as? List<String>
                                val participantNamesMap = convoData["participantNames"] as? Map<String, String>
                                val timestamp = convoData["lastMessageTimestamp"] as? Timestamp // Check timestamp existence

                                if (participantIds == null || participantIds.size != 2 || participantNamesMap == null || timestamp == null) {
                                    Log.w("LawyerInboxActivity", "[Listener]   Skipping ${doc.id}: Missing required fields (participants, names, or timestamp).")
                                    continue // Skip if essential data is missing
                                }

                                // Fetch optional fields safely
                                val participantProfileUrlsMap = convoData["participantProfileUrls"] as? Map<String, String?> ?: emptyMap()
                                val lastMessage = convoData["lastMessageText"] as? String ?: ""
                                val unreadCounts = convoData["unreadCounts"] as? Map<String, Long> ?: emptyMap()

                                val otherId = participantIds.firstOrNull { it != currentUserId } ?: ""
                                if (otherId.isEmpty()) {
                                    Log.w("LawyerInboxActivity", "[Listener]   Skipping ${doc.id}: Could not determine other participant ID.")
                                    continue
                                }

                                val otherName = participantNamesMap[otherId] ?: "Unknown Client"
                                val currentUnread = unreadCounts[currentUserId] ?: 0

                                // Create object only if all checks pass
                                val mappedConvo = Conversation(
                                    conversationId = doc.id, participantIds = participantIds,
                                    participantNames = participantNamesMap, participantProfileUrls = participantProfileUrlsMap,
                                    lastMessageText = lastMessage, lastMessageTimestamp = timestamp, // Timestamp is non-null here
                                    otherParticipantId = otherId, unreadCount = currentUnread
                                )
                                fetchedConversations.add(mappedConvo)
                                Log.v("LawyerInboxActivity", "[Listener]   Successfully mapped & added: ${mappedConvo.conversationId}")

                            } else { Log.w("LawyerInboxActivity", "[Listener]   Document data is null for ${doc.id}") }
                        } catch (ex: Exception) { Log.e("LawyerInboxActivity", "[Listener]   EXCEPTION parsing ${doc.id}", ex) }
                    }

                    conversationAdapter.updateList(fetchedConversations) // Update adapter

                    if (conversationsList.isEmpty()) {
                        binding.textNoConversations.text = "No client conversations yet."
                        binding.textNoConversations.visibility = View.VISIBLE
                        binding.conversationsRecyclerView.visibility = View.GONE
                    } else {
                        binding.textNoConversations.visibility = View.GONE
                        binding.conversationsRecyclerView.visibility = View.VISIBLE
                    }
                    Log.d("LawyerInboxActivity", "UI Updated: ${conversationsList.size} convos.")
                } else { Log.w("LawyerInboxActivity", "Activity destroyed before UI update.") }
            }
    }
}