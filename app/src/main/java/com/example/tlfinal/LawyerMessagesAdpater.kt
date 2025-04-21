package com.example.tlfinal // Or your package

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton // Keep for audio later
import android.widget.LinearLayout  // Keep for audio later
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tlfinal.R // Import R
import com.example.tlfinal.TimeFormatter // Assuming this exists
import com.example.tlfinal.Message // Your Message data class
import com.google.firebase.Timestamp // Import Timestamp
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.* // Import Locale and Date

class LawyerMessageAdapter( // Renamed to reflect lawyer context
    private var messages: MutableList<Message> // Use MutableList if adding locally before listener updates
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    companion object {
        private const val VIEW_TYPE_SENT_TEXT = 1
        private const val VIEW_TYPE_RECEIVED_TEXT = 2
        private const val VIEW_TYPE_SENT_AUDIO = 3
        private const val VIEW_TYPE_RECEIVED_AUDIO = 4
        // Add other types (IMAGE, etc.)
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        val isOutgoing = message.senderId == currentUserId // Lawyer sent this

        return when (message.messageType.uppercase()) { // Use uppercase for safety
            "TEXT" -> if (isOutgoing) VIEW_TYPE_SENT_TEXT else VIEW_TYPE_RECEIVED_TEXT
            "AUDIO" -> if (isOutgoing) VIEW_TYPE_SENT_AUDIO else VIEW_TYPE_RECEIVED_AUDIO
            else -> -1 // Or a default view type
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        // Ensure these layout names match your chat bubble XML files
        return when (viewType) {
            VIEW_TYPE_SENT_TEXT -> {
                val view = inflater.inflate(R.layout.message_item_outgoing, parent, false)
                SentTextViewHolder(view)
            }
            VIEW_TYPE_RECEIVED_TEXT -> {
                val view = inflater.inflate(R.layout.message_item_incoming, parent, false)
                ReceivedTextViewHolder(view)
            }
            VIEW_TYPE_SENT_AUDIO -> {
                val view = inflater.inflate(R.layout.message_item_outgoing, parent, false) // Use specific audio layout
                SentAudioViewHolder(view)
            }
            VIEW_TYPE_RECEIVED_AUDIO -> {
                val view = inflater.inflate(R.layout.message_item_incoming, parent, false) // Use specific audio layout
                ReceivedAudioViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        // Format timestamp once
        val timestampFormatted = formatTimestampForChat(message.timestamp)

        when (holder) {
            is SentTextViewHolder -> holder.bind(message, timestampFormatted)
            is ReceivedTextViewHolder -> holder.bind(message, timestampFormatted)
            is SentAudioViewHolder -> holder.bind(message, timestampFormatted)
            is ReceivedAudioViewHolder -> holder.bind(message, timestampFormatted)
        }
    }

    override fun getItemCount(): Int = messages.size

    fun updateMessages(newMessages: List<Message>) {
        // Basic update, consider DiffUtil for performance
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    // --- View Holders ---

    // Text ViewHolders
    inner class SentTextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // --- Ensure these IDs match message_item_outgoing.xml ---
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        // --- ---

        fun bind(message: Message, timestamp: String) {
            messageText.text = message.text // <<< SET TEXT
            timestampText.text = timestamp
            // Make sure only text view is visible if using combined layouts
            // itemView.findViewById<LinearLayout>(R.id.audioPlayerLayout)?.visibility = View.GONE
            messageText.visibility = View.VISIBLE
        }
    }

    inner class ReceivedTextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // --- Ensure these IDs match message_item_incoming.xml ---
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        // --- ---

        fun bind(message: Message, timestamp: String) {
            messageText.text = message.text // <<< SET TEXT
            timestampText.text = timestamp
            // Make sure only text view is visible if using combined layouts
            // itemView.findViewById<LinearLayout>(R.id.audioPlayerLayout)?.visibility = View.GONE
            messageText.visibility = View.VISIBLE
        }
    }

    // Audio ViewHolders (Example structure)
    inner class SentAudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // --- Ensure these IDs match message_item_outgoing_audio.xml ---
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText) // Reuse timestamp ID?
        private val playButton: ImageButton = itemView.findViewById(R.id.buttonPlayPause)
        private val durationText: TextView = itemView.findViewById(R.id.audioDurationText)
        // --- ---

        fun bind(message: Message, timestamp: String) {
            timestampText.text = timestamp
            durationText.text = TimeFormatter.formatDuration(message.audioDuration)
            playButton.setOnClickListener { /* TODO: Play audio logic */ }
        }
    }

    inner class ReceivedAudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // --- Ensure these IDs match message_item_incoming_audio.xml ---
        private val timestampText: TextView = itemView.findViewById(R.id.messageText) // Reuse timestamp ID?
        private val playButton: ImageButton = itemView.findViewById(R.id.buttonPlayPause)
        private val durationText: TextView = itemView.findViewById(R.id.audioDurationText)
        // --- ---

        fun bind(message: Message, timestamp: String) {
            timestampText.text = timestamp
            durationText.text = TimeFormatter.formatDuration(message.audioDuration)
            playButton.setOnClickListener { /* TODO: Play audio logic */ }
        }
    }

    // Helper function for chat timestamp formatting
    private fun formatTimestampForChat(timestamp: Timestamp?): String {
        return timestamp?.toDate()?.let {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
        } ?: "" // Just show time for chat messages
    }
}