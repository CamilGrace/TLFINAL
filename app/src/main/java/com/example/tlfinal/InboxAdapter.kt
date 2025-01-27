package com.example.tlfinal.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tlfinal.R
import com.example.tlfinal.models.InboxThread

class InboxAdapter(
    private val threads: MutableList<InboxThread>,
    private val onItemClick: (InboxThread) -> Unit
) : RecyclerView.Adapter<InboxAdapter.InboxViewHolder>() {

    class InboxViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.threadNameTextView)
        val lastMessageTextView: TextView = itemView.findViewById(R.id.lastMessageTextView)
        val timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InboxViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_inbox_thread, parent, false)
        return InboxViewHolder(view)
    }

    override fun onBindViewHolder(holder: InboxViewHolder, position: Int) {
        val thread = threads[position]
        holder.nameTextView.text = if (thread.clientName.isNotEmpty()) thread.clientName else thread.lawyerName
        holder.lastMessageTextView.text = thread.lastMessage
        holder.timestampTextView.text = formatTimestamp(thread.lastMessageTimestamp)

        holder.itemView.setOnClickListener { onItemClick(thread) }
    }

    override fun getItemCount(): Int = threads.size

    fun updateThreads(newThreads: List<InboxThread>) {
        threads.clear()
        threads.addAll(newThreads)
        notifyDataSetChanged()
    }

    private fun formatTimestamp(timestamp: Long): String {
        // Format timestamp to a human-readable time (e.g., "14h" or "2d")
        // You can use any time formatting logic here
        return "14h" // Replace with actual logic
    }
}
