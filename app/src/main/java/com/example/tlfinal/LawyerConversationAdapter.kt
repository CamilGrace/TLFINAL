package com.example.tlfinal

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.tlfinal.R // Import R
import com.example.tlfinal.TimeFormatter // Import your TimeFormatter
import com.google.firebase.Timestamp // Import Timestamp
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class LawyerConversationAdapter(
    private val context: Context,
    private var conversations: List<Conversation>
) : RecyclerView.Adapter<LawyerConversationAdapter.ViewHolder>() { // Renamed ViewHolder

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    // Define date formats statically
    companion object {
        private val TIME_FORMAT = SimpleDateFormat("h:mm a", Locale.getDefault())
        private val DATE_FORMAT = SimpleDateFormat("MMM d", Locale.getDefault())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Ensure this layout ID matches your conversation list item XML
        val view = LayoutInflater.from(context).inflate(R.layout.conversation_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (currentUserId == null) return
        val conversation = conversations[position]
        holder.bind(conversation, currentUserId)
    }

    override fun getItemCount(): Int = conversations.size

    fun updateList(newConversations: List<Conversation>) {
        conversations = newConversations
        notifyDataSetChanged() // Consider DiffUtil later
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // --- Ensure these IDs match conversation_list_item.xml ---
        private val profileImage: ImageView = itemView.findViewById(R.id.imageProfile)
        private val nameText: TextView = itemView.findViewById(R.id.textName)
        private val lastMessageText: TextView = itemView.findViewById(R.id.textLastMessage)
        private val timestampText: TextView = itemView.findViewById(R.id.textTimestamp)
        private val unreadBadge: TextView? = itemView.findViewById(R.id.badgeUnreadCount) // Make nullable safe
        // --- ---

        fun bind(conversation: Conversation, currentLawyerId: String) {
            // otherParticipantId should be correctly set in LawyerInboxActivity fetch
            val otherUserId = conversation.otherParticipantId
            val otherUserName = conversation.participantNames[otherUserId] ?: "Unknown Client"
            // Safely access profile URLs map
            val otherUserProfileUrl = conversation.participantProfileUrls?.get(otherUserId)

            nameText.text = otherUserName // Display Client Name
            lastMessageText.text = conversation.lastMessageText ?: "..." // Use nullable field
            timestampText.text = formatTimestamp(conversation.lastMessageTimestamp) // Use helper

            // Load Client Profile Pic safely
            Glide.with(context)
                .load(otherUserProfileUrl) // Handles null URL gracefully
                .placeholder(R.drawable.ic_profile) // Use a default profile icon
                .error(R.drawable.ic_profile)       // Same default on error
                .apply(RequestOptions.circleCropTransform())
                .into(profileImage)

            val unreadCountForLawyer = conversation.unreadCount ?: 0 // Default to 0 if null
            if (unreadCountForLawyer > 0 && unreadBadge != null) { // Check > 0
                unreadBadge.text = unreadCountForLawyer.toString()
                unreadBadge.visibility = View.VISIBLE
            } else {
                unreadBadge?.visibility = View.GONE // Use safe call ?.
            }

            itemView.setOnClickListener {
                val intent = Intent(context, LawyerChatActivity::class.java).apply {
                    putExtra("receiverId", otherUserId)       // Client ID
                    putExtra("receiverName", otherUserName)    // Client Name
                    putExtra("conversationId", conversation.conversationId) // Pass conversation ID
                }
                context.startActivity(intent)
            }
        }

        // Helper function moved from adapter companion object
        private fun formatTimestamp(timestamp: Timestamp?): String {
            if (timestamp == null) return ""
            val messageDate = timestamp.toDate()
            val calendar = Calendar.getInstance()
            val todayStart = calendar.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val messageTime = messageDate.time

            return when {
                messageTime >= todayStart -> TIME_FORMAT.format(messageDate)
                messageTime >= todayStart - (24 * 60 * 60 * 1000) -> "Yesterday"
                else -> DATE_FORMAT.format(messageDate)
            }
        }
    }
}