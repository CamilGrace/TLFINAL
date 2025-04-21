package com.example.tlfinal // Or your package

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tlfinal.R
import com.example.tlfinal.TimeFormatter // Your time formatter
import com.example.tlfinal.Message
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale

class MessageAdapter(
    private var messages: List<Message>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    companion object {
        const val VIEW_TYPE_OUTGOING_TEXT = 1
        const val VIEW_TYPE_INCOMING_TEXT = 2
        const val VIEW_TYPE_OUTGOING_AUDIO = 3 // Future
        const val VIEW_TYPE_INCOMING_AUDIO = 4 // Future
        // Add other types like IMAGE etc.
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        val isOutgoing = message.senderId == currentUserId

        return when (message.messageType) {
            "TEXT" -> if (isOutgoing) VIEW_TYPE_OUTGOING_TEXT else VIEW_TYPE_INCOMING_TEXT
            "AUDIO" -> if (isOutgoing) VIEW_TYPE_OUTGOING_AUDIO else VIEW_TYPE_INCOMING_AUDIO
            // Handle other types
            else -> -1 // Indicate an unknown type
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_OUTGOING_TEXT -> {
                val view = inflater.inflate(R.layout.message_item_outgoing, parent, false)
                OutgoingTextViewHolder(view)
            }
            VIEW_TYPE_INCOMING_TEXT -> {
                val view = inflater.inflate(R.layout.message_item_incoming, parent, false)
                IncomingTextViewHolder(view)
            }
            VIEW_TYPE_OUTGOING_AUDIO -> {
                val view = inflater.inflate(R.layout.message_item_outgoing, parent, false) // Reuse layout
                OutgoingAudioViewHolder(view) // Create this ViewHolder
            }
            VIEW_TYPE_INCOMING_AUDIO -> {
                val view = inflater.inflate(R.layout.message_item_incoming, parent, false) // Reuse layout
                IncomingAudioViewHolder(view) // Create this ViewHolder
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val timestampFormatted = message.timestamp?.toDate()?.let {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(it)
        } ?: ""

        when (holder) {
            is OutgoingTextViewHolder -> holder.bind(message, timestampFormatted)
            is IncomingTextViewHolder -> holder.bind(message, timestampFormatted)
            is OutgoingAudioViewHolder -> holder.bind(message, timestampFormatted)
            is IncomingAudioViewHolder -> holder.bind(message, timestampFormatted)
            // Bind other view holders
        }
    }

    override fun getItemCount(): Int = messages.size

    fun updateMessages(newMessages: List<Message>) {
        messages = newMessages
        notifyDataSetChanged() // Use DiffUtil later for performance
    }

    // --- View Holders ---

    // Text ViewHolders
    inner class OutgoingTextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        private val audioLayout: LinearLayout = itemView.findViewById(R.id.audioPlayerLayout)

        fun bind(message: Message, timestamp: String) {
            messageText.text = message.text
            timestampText.text = timestamp
            messageText.visibility = View.VISIBLE
            audioLayout.visibility = View.GONE // Hide audio layout for text messages
        }
    }

    inner class IncomingTextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        private val audioLayout: LinearLayout = itemView.findViewById(R.id.audioPlayerLayout)

        fun bind(message: Message, timestamp: String) {
            messageText.text = message.text
            timestampText.text = timestamp
            messageText.visibility = View.VISIBLE
            audioLayout.visibility = View.GONE // Hide audio layout for text messages
        }
    }

    // Audio ViewHolders (Basic structure, needs player logic)
    inner class OutgoingAudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        private val audioLayout: LinearLayout = itemView.findViewById(R.id.audioPlayerLayout)
        private val playButton: ImageButton = itemView.findViewById(R.id.buttonPlayPause)
        private val durationText: TextView = itemView.findViewById(R.id.audioDurationText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText) // To hide it

        fun bind(message: Message, timestamp: String) {
            messageText.visibility = View.GONE // Hide text view
            audioLayout.visibility = View.VISIBLE // Show audio layout
            timestampText.text = timestamp
            durationText.text = TimeFormatter.formatDuration(message.audioDuration) // Format milliseconds

            // TODO: Add click listener to playButton to handle audio playback
            playButton.setOnClickListener { /* Play audio logic */ }
        }
    }

    inner class IncomingAudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        private val audioLayout: LinearLayout = itemView.findViewById(R.id.audioPlayerLayout)
        private val playButton: ImageButton = itemView.findViewById(R.id.buttonPlayPause)
        private val durationText: TextView = itemView.findViewById(R.id.audioDurationText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText) // To hide it

        fun bind(message: Message, timestamp: String) {
            messageText.visibility = View.GONE // Hide text view
            audioLayout.visibility = View.VISIBLE // Show audio layout
            timestampText.text = timestamp
            durationText.text = TimeFormatter.formatDuration(message.audioDuration)

            // TODO: Add click listener to playButton to handle audio playback
            playButton.setOnClickListener { /* Play audio logic */ }
        }
    }
}