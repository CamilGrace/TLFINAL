package com.example.tlfinal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth

class MessagesAdapter(private var messageInboxes: MutableList<MessageInbox>) : RecyclerView.Adapter<MessagesAdapter.MessageViewHolder>() {

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_SENT) {
            R.layout.item_message_sent
        } else {
            R.layout.item_message_received
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messageInboxes[position]
        holder.messageTextView.text = message.text
    }

    override fun getItemCount(): Int = messageInboxes.size

    override fun getItemViewType(position: Int): Int {
        val message = messageInboxes[position]
        return if (message.senderId == FirebaseAuth.getInstance().currentUser?.uid) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    fun addMessage(messageInbox: MessageInbox, recyclerView: RecyclerView) {
        messageInboxes.add(messageInbox)
        notifyItemInserted(messageInboxes.size - 1)
        recyclerView.scrollToPosition(messageInboxes.size - 1)
    }

    fun updateMessages(newMessageInboxes: List<MessageInbox>) {
        messageInboxes.clear()
        messageInboxes.addAll(newMessageInboxes)
        notifyDataSetChanged()
    }

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }
}

