package com.example.myapplication.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.local.RelatedDocument
import com.example.myapplication.databinding.ItemChatHistoryBinding
import java.text.SimpleDateFormat
import java.util.Locale

data class HistoryItem(
    val message: String,
    val response: String,
    val timestamp: Long,
    val relatedDocuments: List<RelatedDocument>
)

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val historyList = mutableListOf<HistoryItem>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newList: List<HistoryItem>) {
        historyList.clear()
        historyList.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemChatHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(historyList[position])
    }

    override fun getItemCount(): Int = historyList.size

    class HistoryViewHolder(private val binding: ItemChatHistoryBinding) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(historyItem: HistoryItem) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            // Set user message
            binding.messageTextView.text = "User Message: ${historyItem.message}"

            // Set bot response
            binding.responseTextView.text = "Response Bot: ${historyItem.response}"

            // Format the related documents text
            binding.relatedDocumentsTextView.text = if (historyItem.relatedDocuments.isNotEmpty()) {
                "Context: ${historyItem.relatedDocuments.joinToString("\n") { "- ${it.judul}: ${it.abstrak}" }}"
            } else {
                "No related documents available"
            }

            // Set timestamp
            binding.timestampTextView.text = dateFormat.format(historyItem.timestamp)
        }
    }

}

