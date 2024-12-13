package com.example.myapplication.ui.feature.history

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.data.local.RelatedDocument
import com.example.myapplication.databinding.ActivityHistoryBinding
import com.example.myapplication.ui.adapter.HistoryAdapter
import com.example.myapplication.ui.adapter.HistoryItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var historyAdapter: HistoryAdapter
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupToolbar()
        setupRecyclerView()
        loadChatHistoryFromFirestore()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_history, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_all -> {
                deleteAllChatHistory()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun deleteAllChatHistory() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("messages")
            .whereEqualTo("userId", currentUser.uid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val batch = firestore.batch()
                for (document in querySnapshot.documents) {
                    batch.delete(document.reference)
                }
                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Chat history deleted", Toast.LENGTH_SHORT).show()
                        historyAdapter.submitList(emptyList()) // Clear RecyclerView
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Error deleting history", e)
                        Toast.makeText(this, "Failed to delete history", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error loading history for deletion", e)
                Toast.makeText(this, "Failed to load history", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter()
        binding.historyRecyclerView.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(this@HistoryActivity).apply {
                reverseLayout = true
                stackFromEnd = true
            }
        }
    }

    private fun loadChatHistoryFromFirestore() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("messages")
            .whereEqualTo("userId", currentUser.uid)
            .orderBy("timestamp") // Sort by time
            .get()
            .addOnSuccessListener { querySnapshot ->
                val historyList = querySnapshot.documents.map { document ->
                    val message = document.getString("message") ?: ""
                    val response = document.getString("response") ?: ""
                    val timestamp = document.getLong("timestamp") ?: System.currentTimeMillis()
                    val relatedDocs = document.get("relatedDocuments") as? List<Map<String, String>> ?: emptyList()

                    HistoryItem(
                        message = message,
                        response = response,
                        timestamp = timestamp,
                        relatedDocuments = relatedDocs.map {
                            RelatedDocument(
                                judul = it["judul"] ?: "",
                                abstrak = it["abstrak"] ?: "",
                                url = it["url"] ?: ""
                            )
                        }
                    )
                }

                historyAdapter.submitList(historyList)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error loading chat history", e)
                Toast.makeText(this, "Failed to load history", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Chat History"
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }
}
