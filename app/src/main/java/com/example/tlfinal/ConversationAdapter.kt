package com.example.tlfinal

import android.content.Context
import android.content.Intent
import android.util.Log // <<< MAKE SURE Log IS IMPORTED
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.tlfinal.R
// Removed TimeFormatter import if formatTimestamp is internal now
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class ConversationAdapter( // This is the CLIENT's adapter
    private val context: Context,
    private var conversationList: List<Conversation>
) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    // Correctly declared at the class level, accessible by member functions/classes
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    // --- ADD companion object for TAG ---
    companion object {
        private const val TAG = "ClientConvoAdapter" // Define TAG here
        private val TIME_FORMAT = SimpleDateFormat("h:mm a", Locale.getDefault())
        private val DATE_FORMAT = SimpleDateFormat("MMM d", Locale.getDefault())
    }
    // --- END companion object ---

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.conversation_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Check if currentUserId is valid before binding
        val conversation = conversationList[position]
        if (currentUserId != null) {
            // *** REMOVED second argument from bind call ***
            holder.bind(conversation)
        } else {
            // Log error if user ID is somehow null during binding
            Log.e(TAG, "Current User ID is null in onBindViewHolder for position $position")
            // Optionally clear views in the holder if needed
        }
    }

    override fun getItemCount(): Int = conversationList.size

    fun updateList(newList: List<Conversation>) {
        conversationList = newList
        notifyDataSetChanged()
        Log.d(TAG, "Adapter list updated. New size: ${conversationList.size}") // Use TAG
    }

    // ViewHolder remains an inner class
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profileImage: ImageView = itemView.findViewById(R.id.imageProfile)
        private val nameText: TextView = itemView.findViewById(R.id.textName)
        private val lastMessageText: TextView = itemView.findViewById(R.id.textLastMessage)
        private val timestampText: TextView = itemView.findViewById(R.id.textTimestamp)
        private val unreadBadge: TextView? = itemView.findViewById(R.id.badgeUnreadCount)

        init { // Keep init block for click listener
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val conversation = conversationList[position]
                    val otherUserId = conversation.otherParticipantId // Lawyer's ID
                    val otherNameRaw = conversation.participantNames[otherUserId] ?: "Lawyer"

                    val intent = Intent(context, ChatActivity::class.java).apply {
                        putExtra("conversationId", conversation.conversationId)
                        putExtra("receiverId", otherUserId)
                        putExtra("receiverName", otherNameRaw) // Pass raw name
                    }
                    context.startActivity(intent)
                }
            }
        }

        // *** REMOVED currentClientId parameter from bind ***
        fun bind(conversation: Conversation) {
            val otherUserId = conversation.otherParticipantId // Lawyer's ID
            val otherNameRaw = conversation.participantNames[otherUserId] ?: "Unknown Lawyer"
            val otherUserProfileUrl = conversation.participantProfileUrls?.get(otherUserId)

            // Add "Atty." prefix for display
            nameText.text = "Atty. $otherNameRaw"

            lastMessageText.text = conversation.lastMessageText ?: "..."
            timestampText.text = formatTimestamp(conversation.lastMessageTimestamp)

            Glide.with(context)
                .load(otherUserProfileUrl)
                .placeholder(R.drawable.ic_profile) // Placeholder
                .error(R.drawable.ic_profile)       // Error placeholder
                .apply(RequestOptions.circleCropTransform())
                .into(profileImage)

            // Unread count logic (ensure conversation.unreadCount matches your data class)
            val unreadCountForClient = conversation.unreadCount // Directly use field if it's for the current user
            // or: conversation.unreadCounts[currentClientId] ?: 0L
            if (unreadCountForClient != null) {
                if (unreadCountForClient > 0 && unreadBadge != null) {
                    unreadBadge.text = unreadCountForClient.toString()
                    unreadBadge.visibility = View.VISIBLE
                } else {
                    unreadBadge?.visibility = View.GONE
                }
            }
        }

        // Keep formatTimestamp helper
        private fun formatTimestamp(timestamp: Timestamp?): String {
            if (timestamp == null) return ""
            val messageDate = timestamp.toDate()
            val calendar = Calendar.getInstance()
            val todayStart = calendar.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val yesterdayStart = todayStart - (24 * 60 * 60 * 1000)
            val messageTime = messageDate.time
            return when {
                messageTime >= todayStart -> TIME_FORMAT.format(messageDate)
                messageTime >= yesterdayStart -> "Yesterday"
                else -> DATE_FORMAT.format(messageDate)
            }
        }
    }
}