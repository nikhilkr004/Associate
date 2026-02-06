package com.example.associate.Activities

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.associate.Adapters.AiChatAdapter
import com.example.associate.DataClass.AiChatMessage
import com.example.associate.Repositories.AiChatRepository
import com.example.associate.Repositories.UserRepository
import com.example.associate.databinding.ActivityAiChatBinding
import com.example.associate.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class AiChatActivity : AppCompatActivity() {

    private val binding by lazy { ActivityAiChatBinding.inflate(layoutInflater) }
    private val repository = AiChatRepository()
    private val messages = ArrayList<AiChatMessage>()
    private lateinit var adapter: AiChatAdapter

    private var selectedImageBitmap: Bitmap? = null
    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupUI()
        setupClickListeners()
        fetchUserName()
    }

    private fun setupUI() {
        adapter = AiChatAdapter(messages)
        binding.rvAiChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvAiChat.adapter = adapter
    }

    private fun fetchUserName() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lifecycleScope.launch {
            val result = userRepository.getUserById(uid)
            if (result is UserRepository.Result.Success) {
                val userData = result.data
                binding.tvGreeting.text = "Good Afternoon,\n${userData.name}"
            } else {
                Log.e("AiChatActivity", "Failed to fetch user")
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        // Chip Suggestions
        binding.chipCompare.setOnClickListener { sendMessage("Compare 'Secure Health Plus' and 'Family Floater Gold'") }
        binding.chipCoverage.setOnClickListener { sendMessage("What are the coverage limits for 'Term Life Shield'?") }
        binding.chipExclusions.setOnClickListener { sendMessage("What is usually excluded in Health Insurance?") }

        binding.btnAiSend.setOnClickListener {
            val text = binding.etAiInput.text.toString().trim()
            if (text.isNotEmpty() || selectedImageBitmap != null) {
                sendMessage(text)
            }
        }

        binding.btnAttachment.setOnClickListener {
            openContentPicker()
        }
        
        binding.btnClosePreview.setOnClickListener {
            hideAttachmentPreview()
        }
    }

    // Updated for PDF Support
    private var selectedPdfUri: Uri? = null

    private val pickContentLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val mimeType = contentResolver.getType(it)
                if (mimeType == "application/pdf") {
                    selectedPdfUri = it
                    selectedImageBitmap = null
                    showAttachmentPreview(getFileName(it), isPdf = true)
                } else {
                    // Assume Image
                    try {
                        selectedImageBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, it))
                        } else {
                            MediaStore.Images.Media.getBitmap(contentResolver, it)
                        }
                        selectedPdfUri = null
                        showAttachmentPreview("Image Selected", isPdf = false)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Failed to load file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    private fun showAttachmentPreview(name: String, isPdf: Boolean) {
        binding.layoutAttachmentPreview.visibility = View.VISIBLE
        binding.tvPreviewName.text = name
        binding.imgPreviewIcon.setImageResource(if (isPdf) R.drawable.attach_file else R.drawable.ic_launcher_background) // Just a placeholder for image icon if distinct
        // Reset Attachment Button color to indicate active
        binding.btnAttachment.setColorFilter(resources.getColor(R.color.green))
    }

    private fun hideAttachmentPreview() {
        selectedImageBitmap = null
        selectedPdfUri = null
        binding.layoutAttachmentPreview.visibility = View.GONE
        binding.btnAttachment.setColorFilter(resources.getColor(android.R.color.darker_gray))
    }
    
    // Helper to get filename
    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if(index >= 0) result = cursor.getString(index)
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "Document"
    }

    private fun openContentPicker() {
        pickContentLauncher.launch("*/*")
    }

    private fun sendMessage(text: String) {
        if (text.isEmpty() && selectedImageBitmap == null && selectedPdfUri == null) return
        
        // UI Updates
        binding.layoutWelcome.visibility = View.GONE
        binding.rvAiChat.visibility = View.VISIBLE
        binding.etAiInput.setText("")
        
        // Hide Preview since we are sending it
        val currentImage = selectedImageBitmap
        val currentPdfUri = selectedPdfUri
        hideAttachmentPreview()
        
        // Add User Message
        messages.add(AiChatMessage(message = text, isUser = true))
        adapter.notifyItemInserted(messages.size - 1)
        
        // Add Loading
        messages.add(AiChatMessage(isLoading = true))
        adapter.notifyItemInserted(messages.size - 1)
        binding.rvAiChat.smoothScrollToPosition(messages.size - 1)

        val context = repository.getInitialContext()

        // Detect Language
        com.example.associate.Utils.LanguageUtils.detectLanguage(text) { languageCode ->
            lifecycleScope.launch {
                // Prepare PDF String if needed
                val pdfBase64 = if (currentPdfUri != null) {
                    com.example.associate.Utils.PdfUtils.readPdfAsBase64(this@AiChatActivity, currentPdfUri)
                } else null
                
                val aiResponse = repository.sendMessage(
                    userMessage = text,
                    context = context,
                    languageCode = languageCode,
                    image = currentImage,
                    pdfBase64 = pdfBase64
                )
                
                processAiResponse(aiResponse)
            }
        }
    }
    
    private fun processAiResponse(response: String) {
        // Remove Loading
        if (messages.isNotEmpty() && messages.last().isLoading) {
            messages.removeAt(messages.size - 1)
            adapter.notifyItemRemoved(messages.size)
        }

        // SAVE TO HISTORY (Persistence)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            // We need the last user query. It's at [size-1] usually if we just removed loading.
            // But 'messages' has everything. 
            // We can track the last query in a variable or find it.
            // Simple approach: Pass 'text' from sendMessage to here? No, processAiResponse is separated.
            // Let's just launch a save coroutine here with the context of "Last User Message".
            // Finding last user message:
            val lastUserMsg = messages.findLast { it.isUser }?.message
            if (!lastUserMsg.isNullOrEmpty()) {
                lifecycleScope.launch {
                    repository.saveChatToHistory(uid, lastUserMsg, response)
                }
            }
        }

        // Check for Advisor Tag
        if (response.contains("[SHOW_ADVISORS]")) {
            val cleanResponse = response.replace("[SHOW_ADVISORS]", "").trim()
            
            // Add Text Response First
            if (cleanResponse.isNotEmpty()) {
                messages.add(AiChatMessage(message = cleanResponse, isUser = false))
                adapter.notifyItemInserted(messages.size - 1)
            }

            // Add Advisor List Card
            lifecycleScope.launch {
                val topAdvisors = repository.getTopAdvisors()
                if (topAdvisors.isNotEmpty()) {
                    messages.add(AiChatMessage(advisors = topAdvisors, isUser = false))
                } else {
                    messages.add(
                        AiChatMessage(
                            message = "(No active advisors found to recommend right now)",
                            isUser = false
                        )
                    )
                }
                adapter.notifyItemInserted(messages.size - 1)
                binding.rvAiChat.smoothScrollToPosition(messages.size - 1)
            }
            
        } else {
            // Normal Response
            messages.add(AiChatMessage(message = response, isUser = false))
            adapter.notifyItemInserted(messages.size - 1)
            binding.rvAiChat.smoothScrollToPosition(messages.size - 1)
        }
    }

    private fun showHistoryBottomSheet() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_chat_history, null)
        dialog.setContentView(view)
        
        val rvHistory = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvChatHistory)
        rvHistory.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        lifecycleScope.launch {
            val history = repository.fetchChatHistory(uid)
            val historyAdapter = com.example.associate.Adapters.ChatHistoryAdapter(history) { item ->
                // On Item Click: Load into chat?
                dialog.dismiss()
                // Restore headers? Or just append?
                // For now, let's just append as if it's a new context or simple view
                messages.add(AiChatMessage(message = item.query, isUser = true))
                messages.add(AiChatMessage(message = item.response, isUser = false))
                adapter.notifyDataSetChanged()
                
                binding.layoutWelcome.visibility = View.GONE
                binding.rvAiChat.visibility = View.VISIBLE
                binding.rvAiChat.smoothScrollToPosition(messages.size - 1)
            }
            rvHistory.adapter = historyAdapter
        }
        
        dialog.show()
    }
}

