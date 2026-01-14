package com.example.associate.Fragments

import android.app.Dialog
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.associate.Adapters.ChatAdapter
import com.example.associate.R
import com.example.associate.Repositories.StorageRepository
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import android.widget.PopupMenu

class ChatBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var rvChat: RecyclerView
    private lateinit var btnUploadDoc: ImageView
    private lateinit var btnUploadImage: ImageView
    private lateinit var btnSendMessage: ImageView
    private lateinit var etMessage: EditText
    private lateinit var emptyStateView: LinearLayout
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var progressDialog: ProgressDialog

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storageRepository = StorageRepository()
    private var listenerRegistration: ListenerRegistration? = null
    
    // Arguments
    private var callId: String = ""
    private var currentUserId: String = ""

    companion object {
        fun newInstance(callId: String): ChatBottomSheetFragment {
            val fragment = ChatBottomSheetFragment()
            val args = Bundle()
            args.putString("CALL_ID", callId)
            fragment.arguments = args
            return fragment
        }
    }

    // File Pickers
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) uploadSelectedFile(uri, true)
    }

    private val pickDocument = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadSelectedFile(uri, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        callId = arguments?.getString("CALL_ID") ?: ""
        currentUserId = auth.currentUser?.uid ?: ""
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.8).toInt()
                
                // Set max height to 80%
                val layoutParams = bottomSheet.layoutParams
                layoutParams.height = (resources.displayMetrics.heightPixels * 0.8).toInt()
                bottomSheet.layoutParams = layoutParams
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupRecyclerView()
        setupClickListeners()
        listenForMessages()
    }

    private fun initViews(view: View) {
        rvChat = view.findViewById(R.id.rvChat)
        btnUploadDoc = view.findViewById(R.id.btnUploadDoc)
        btnUploadImage = view.findViewById(R.id.btnUploadImage)
        btnSendMessage = view.findViewById(R.id.btnSendMessage)
        etMessage = view.findViewById(R.id.etMessage)
        emptyStateView = view.findViewById(R.id.emptyStateView)
        
        progressDialog = ProgressDialog(requireContext())
        progressDialog.setMessage("Uploading...")
        progressDialog.setCancelable(false)
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(
            currentUserId, 
            ArrayList(),
            onItemClick = { content, type ->
                if (type == "document" || type == "image") {
                    downloadAndOpenFile(content, type)
                }
            },
            onItemLongClick = { messageId, senderId ->
                if (senderId == currentUserId) {
                    showDeleteMenu(messageId)
                }
            }
        )
        val layoutManager = LinearLayoutManager(context)
        layoutManager.stackFromEnd = true // Start from bottom like standard chat
        rvChat.layoutManager = layoutManager
        rvChat.adapter = chatAdapter
    }

    private fun downloadAndOpenFile(url: String, type: String) {
        val progressDialog = ProgressDialog(context)
        progressDialog.setMessage("Opening...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        // Create a temporary file
        val fileName = "temp_file_" + System.currentTimeMillis() + if (type == "image") ".jpg" else ".pdf"
        val file = java.io.File(requireContext().cacheDir, fileName)

        // Download using a background thread (simplest way without heavy libs)
        Thread {
            try {
                val u = java.net.URL(url)
                val conn = u.openConnection()
                val inputStream = conn.getInputStream()
                val outputStream = java.io.FileOutputStream(file)
                val buffer = ByteArray(1024)
                var len: Int
                while (inputStream.read(buffer).also { len = it } > 0) {
                    outputStream.write(buffer, 0, len)
                }
                outputStream.close()
                inputStream.close()

                // Open File on UI Thread
                if (isAdded) {
                    activity?.runOnUiThread {
                        progressDialog.dismiss()
                        openFileInternal(file, type)
                    }
                }
            } catch (e: Exception) {
                if (isAdded) {
                    activity?.runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(context, "Failed to download file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    private fun openFileInternal(file: java.io.File, type: String) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )
            val mimeType = if (type == "image") "image/*" else "application/pdf"
            
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteMenu(messageId: String) {
        // Since we are in a BottomSheet, standard PopupMenu might be overlayed or weird.
        // Let's use a simple AlertDialog or try PopupMenu anchored to center/view?
        // Actually, just a simple Dialog is safer.
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Message")
            .setMessage("Are you sure you want to delete this message?")
            .setPositiveButton("Delete") { _, _ ->
                 deleteMessage(messageId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMessage(messageId: String) {
        db.collection("videoCalls")
            .document(callId)
            .collection("messages")
            .document(messageId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupClickListeners() {
        btnUploadImage.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnUploadDoc.setOnClickListener {
            pickDocument.launch("application/pdf")
        }

        btnSendMessage.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text, "text", "", "text")
                etMessage.setText("")
            }
        }
    }

    private fun listenForMessages() {
        if (callId.isEmpty()) return

        listenerRegistration = db.collection("videoCalls")
            .document(callId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("ChatSheet", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val messages = ArrayList<HashMap<String, Any>>()
                    for (doc in snapshot.documents) {
                        val data = doc.data as HashMap<String, Any>
                        data["id"] = doc.id
                        messages.add(data)
                    }
                    
                    if (messages.isEmpty()) {
                        emptyStateView.visibility = View.VISIBLE
                        rvChat.visibility = View.GONE
                    } else {
                        emptyStateView.visibility = View.GONE
                        rvChat.visibility = View.VISIBLE
                        chatAdapter.updateList(messages)
                        rvChat.scrollToPosition(messages.size - 1)
                    }
                }
            }
    }

    private fun uploadSelectedFile(uri: Uri, isImage: Boolean) {
        progressDialog.show()
        
        storageRepository.uploadFile(
            context = requireContext(),
            fileUri = uri,
            isImage = isImage,
            callback = object : StorageRepository.UploadCallback {
                override fun onSuccess(downloadUrl: String, fileName: String, fileType: String) {
                    if (isAdded) {
                        activity?.runOnUiThread {
                            progressDialog.dismiss()
                            val type = if (isImage) "image" else "document"
                            sendMessage(downloadUrl, type, fileName, type)
                        }
                    }
                }

                override fun onFailure(error: String) {
                    if (isAdded) {
                        activity?.runOnUiThread {
                            progressDialog.dismiss()
                            Toast.makeText(context, "Upload Failed: $error", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onProgress(progress: Int) {
                    if (isAdded) {
                        activity?.runOnUiThread {
                            progressDialog.setMessage("Uploading... $progress%")
                        }
                    }
                }
            }
        )
    }

    private fun sendMessage(content: String, type: String, name: String, fileType: String) {
        val message = hashMapOf(
            "content" to content, // URL or Text
            "name" to name,
            "type" to type, // text, image, document
            "timestamp" to Timestamp.now(),
            "senderId" to currentUserId
        )

        db.collection("videoCalls")
            .document(callId)
            .collection("messages")
            .add(message)
            .addOnFailureListener { e ->
                Log.e("ChatSheet", "Failed to send message: ${e.message}")
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerRegistration?.remove()
    }
}

// Updated for repository activity
