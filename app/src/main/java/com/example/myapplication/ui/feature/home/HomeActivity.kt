package com.example.myapplication.ui.feature.home

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.databinding.ActivityHomeBinding
import com.example.myapplication.data.datastore.DataStoreManager
import com.example.myapplication.data.local.RelatedDocument
import com.example.myapplication.data.remote.DocumentRequest
import com.example.myapplication.data.remote.NetworkModule.apiService
import com.example.myapplication.ui.adapter.ChatAdapter
import com.example.myapplication.ui.adapter.ChatViewModel
import com.example.myapplication.ui.adapter.DocumentAdapter
import com.example.myapplication.ui.feature.history.HistoryActivity
import com.example.myapplication.ui.feature.user.LoginActivity
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.UUID

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var documentAdapter: DocumentAdapter
    private lateinit var dataStoreManager: DataStoreManager
    private val viewModel by lazy { ViewModelProvider(this)[ChatViewModel::class.java] }
    private val selectedDocuments = mutableSetOf<RelatedDocument>()

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        dataStoreManager = DataStoreManager(this)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerViews()
        setupSearchFeature()
        observeViewModel()

        supportActionBar?.title = ""

        binding.messageInputLayout.setEndIconOnClickListener { sendMessage() }
        binding.sendButton.setOnClickListener { sendMessage() }

        val retryButton: Button = binding.retryButton

        retryButton.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE
            retryButton.visibility = View.GONE
            loadDataFromApi()
        }
        val messageInput = binding.messageInput
        val clearMessage = binding.clearMessage

        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                clearMessage.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        clearMessage.setOnClickListener {
            messageInput.text?.clear()
        }
    }

    private fun updateEmptyState() {
        val isChatEmpty = chatAdapter.itemCount == 0
        binding.emptyStateContainer.visibility = if (isChatEmpty) View.VISIBLE else View.GONE
        binding.chatRecyclerView.visibility = if (isChatEmpty) View.GONE else View.VISIBLE
    }

    private fun animateMessageAddition() {
        val anim = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom)
        binding.chatRecyclerView.startAnimation(anim)
    }

    private fun sendMessage() {
        val message = binding.messageInput.text.toString()
        val currentUser = auth.currentUser
        if (message.isNotEmpty() && selectedDocuments.isNotEmpty()) {

            val messageId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()

            // Prepare the related document data
            val documentDetails = selectedDocuments.map {
                mapOf(
                    "judul" to it.judul,
                    "url" to it.url
                )
            }


            chatAdapter.addMessage(SpannableString(message), true)
            binding.messageInput.text?.clear()
            viewModel.sendMessage(message, context = selectedDocuments.joinToString("\n\n") { it.abstrak })

            viewModel.chatResponse.observe(this) { botResponse ->
                if (!botResponse.isNullOrEmpty()) {
                    // Save the message and response to Firestore
                    val chatData = hashMapOf(
                        "id" to messageId,
                        "userId" to currentUser?.uid,
                        "message" to message,
                        "response" to botResponse,
                        "timestamp" to timestamp,
                        "relatedDocuments" to documentDetails
                    )

                    firestore.collection("messages").document(messageId)
                        .set(chatData)
                        .addOnSuccessListener {
                            Log.d("Firestore", "Message and response saved successfully: $message")

                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "Error saving message and response", e)
                        }

                    // Clear the response to avoid duplicate handling
                    viewModel.clearChatResponse()
                }
            }
            if (chatAdapter.itemCount == 1) {
                animateMessageAddition()
            }
            updateEmptyState()
        } else {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.sidebarButton.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.historyButton.setOnClickListener {
            navigateToHistory()
        }

        binding.signOutButton.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun showLogoutConfirmationDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Logout Confirmation")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch {
                    dataStoreManager.setLoggedIn(false)
                    Toast.makeText(this@HomeActivity, "Signed out successfully", Toast.LENGTH_SHORT).show()
                    navigateToLogin()
                }
            }
            .setNegativeButton("No", null)
            .create()

        dialog.show()
    }

    private fun navigateToHistory() {
        val intent = Intent(this, HistoryActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun setupRecyclerViews() {
        chatAdapter = ChatAdapter()
        binding.chatRecyclerView.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@HomeActivity)
        }

        documentAdapter = DocumentAdapter { document, isChecked ->
            if (isChecked) {
                selectedDocuments.add(document)
                addDocumentChip(document)
                Toast.makeText(this@HomeActivity, "Document selected: ${document.judul}", Toast.LENGTH_SHORT).show()
            } else {
                selectedDocuments.remove(document)
                removeDocumentChip(document)
                Toast.makeText(this@HomeActivity, "Document deselected: ${document.judul}", Toast.LENGTH_SHORT).show()
            }
        }
        binding.documentsRecyclerView.apply {
            adapter = documentAdapter
            layoutManager = LinearLayoutManager(this@HomeActivity)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun addDocumentChip(document: RelatedDocument) {
        val chip = Chip(this).apply {
            text = document.judul
            isCloseIconVisible = true
            tag = document
            setOnCloseIconClickListener {
                selectedDocuments.remove(document)
                binding.selectedDocumentsGroup.removeView(this)
                documentAdapter.deselectDocument(document)
                Toast.makeText(this@HomeActivity, "Document removed: ${document.judul}", Toast.LENGTH_SHORT).show()
            }
        }
        binding.selectedDocumentsGroup.addView(chip)
    }

    private fun removeDocumentChip(document: RelatedDocument) {
        val chipCount = binding.selectedDocumentsGroup.childCount
        for (i in 0 until chipCount) {
            val chip = binding.selectedDocumentsGroup.getChildAt(i) as? Chip
            if (chip?.tag == document) {
                binding.selectedDocumentsGroup.removeView(chip)
                documentAdapter.deselectDocument(document)
                break
            }
        }
    }

    private fun setupSearchFeature() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.clearIcon.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.clearIcon.setOnClickListener {
            binding.searchInput.text?.clear()
        }

        binding.searchButton.setOnClickListener {
            val query = binding.searchInput.text.toString()
            if (query.isNotEmpty()) {
                viewModel.searchRelatedDocuments(query)
            } else {
                Toast.makeText(this, "Please enter a search query", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.relatedDocuments.observe(this) { documents ->
            documentAdapter.submitList(documents)
        }

        viewModel.chatResponse.observe(this) { responseText ->
            responseText?.let {
                val spannableMessage = SpannableString(it)
                chatAdapter.addMessage(spannableMessage, false)
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadDataFromApi() {
        lifecycleScope.launch {
            try {
                val response = apiService.getRelatedDocuments(DocumentRequest(title = "Sample", number = 3))
                if (response.isSuccessful) {
                    binding.progressBar.visibility = View.GONE
                    binding.retryButton.visibility = View.GONE
                } else {
                    handleError("Failed to load data, try again")
                }
            } catch (e: Exception) {
                handleError("No internet connection")
            }
        }
    }

    private fun handleError(errorMessage: String) {
        binding.progressBar.visibility = View.GONE
        binding.retryButton.visibility = View.VISIBLE
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }
}