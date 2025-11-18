// ProfileFragment.kt
package com.example.associate.Fragments

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.associate.R
import com.example.associate.DataClass.DialogUtils
import com.example.associate.DataClass.UserData
import com.example.associate.Repositorys.UserRepository
import com.example.associate.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

class ProfileFragment : Fragment() {

    private lateinit var binding: FragmentProfileBinding
    private val userRepository = UserRepository()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val currentUserId = auth.currentUser?.uid ?: ""
    private var currentProfileImageUrl: String = ""

    // Compression settings

     companion object {

        fun newInstance() = ProfileFragment()
        const val TAG = "ProfileFragment"

         private const val PICK_IMAGE_REQUEST = 100

        const val MAX_IMAGE_WIDTH = 1024
        const val MAX_IMAGE_HEIGHT = 1024
        const val TARGET_FILE_SIZE_KB = 300 // 300KB target
        const val INITIAL_QUALITY = 80
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "onViewCreated: Current User ID = $currentUserId")

        loadUserData()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.profileImage.setOnClickListener {
            openImagePicker()
        }
        binding.changePhotoImage.setOnClickListener {
            openImagePicker()
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "loadUserData: Starting to load user data")
                DialogUtils.showLoadingDialog(requireContext(), "Loading...")

                when (val result = userRepository.getUserById(currentUserId)) {
                    is UserRepository.Result.Success -> {
                        val userData = result.data
                        if (userData is UserData) {
                            Log.d(TAG, "loadUserData: User data loaded successfully - ${userData.name}")
                            displayUserData(userData)
                            currentProfileImageUrl = userData.profilePhotoUrl ?: ""
                        } else {
                            Log.e(TAG, "loadUserData: User data is not of type UserData - ${result.data?.javaClass}")
                            Toast.makeText(
                                requireContext(),
                                "Invalid user data format",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    is UserRepository.Result.Failure -> {
                        Log.e(TAG, "loadUserData: Failed to load - ${result.exception.message}")
                        Toast.makeText(
                            requireContext(),
                            "Failed to load profile: ${result.exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadUserData: Exception - ${e.message}", e)
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                DialogUtils.hideLoadingDialog()
            }
        }
    }

    private fun displayUserData(user: UserData) {
        with(binding) {
            // Text Data
            name.text = user.name
            email.text = user.email
            phoneNumber.text = user.phone

            // Profile Image
            if (!user.profilePhotoUrl.isNullOrEmpty()) {
                Log.d(TAG, "displayUserData: Loading profile image - ${user.profilePhotoUrl}")
                Glide.with(requireContext())
                    .load(user.profilePhotoUrl)
                    .placeholder(R.drawable.user)
                    .error(R.drawable.user)
                    .into(profileImage)
            } else {
                Log.d(TAG, "displayUserData: No profile image found, using placeholder")
                profileImage.setImageResource(R.drawable.user)
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { imageUri ->
                // 🔥 COMPRESS IMAGE BEFORE UPLOAD
                compressAndUploadImage(imageUri)
            }
        }
    }

    // 🔥 NEW: COMPRESS AND UPLOAD IMAGE
    private fun compressAndUploadImage(imageUri: Uri) {
        DialogUtils.showLoadingDialog(requireContext(), "Compressing image...")

        lifecycleScope.launch {
            try {
                val compressedImageData = compressImage(imageUri)
                val originalSize = getImageSize(imageUri)
                val compressedSizeKB = compressedImageData.size / 1024

                Log.d(TAG, "Image compressed: ${originalSize}KB -> ${compressedSizeKB}KB")

                uploadCompressedImage(compressedImageData)

            } catch (e: Exception) {
                DialogUtils.hideLoadingDialog()
                Log.e(TAG, "Image compression failed", e)
                Toast.makeText(
                    requireContext(),
                    "Image processing failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 🔥 SMART IMAGE COMPRESSION
    private suspend fun compressImage(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        try {
            // Step 1: Get image dimensions without loading full image
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            Log.d(TAG, "Original image: ${originalWidth}x${originalHeight}")

            // Step 2: Calculate sampling ratio
            val sampleSize = calculateSampleSize(originalWidth, originalHeight)
            Log.d(TAG, "Using sample size: $sampleSize")

            // Step 3: Load bitmap with sampling
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // Memory efficient
            }

            val bitmap = requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            }

            if (bitmap == null) {
                throw Exception("Failed to decode image")
            }

            Log.d(TAG, "Bitmap after sampling: ${bitmap.width}x${bitmap.height}")

            // Step 4: Smart quality compression
            val compressedData = smartQualityCompression(bitmap)
            bitmap.recycle()

            compressedData

        } catch (e: Exception) {
            Log.e(TAG, "Compression error", e)
            throw e
        }
    }

    // 🔥 CALCULATE OPTIMAL SAMPLE SIZE
    private fun calculateSampleSize(width: Int, height: Int): Int {
        var inSampleSize = 1

        if (height > MAX_IMAGE_HEIGHT || width > MAX_IMAGE_WIDTH) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // Calculate the largest inSampleSize value that is a power of 2
            while (halfHeight / inSampleSize >= MAX_IMAGE_HEIGHT &&
                halfWidth / inSampleSize >= MAX_IMAGE_WIDTH) {
                inSampleSize *= 2
            }
        }

        // For very large images, be more aggressive
        if (width > 4000 || height > 4000) {
            inSampleSize = maxOf(inSampleSize, 4)
        }

        return inSampleSize
    }

    // 🔥 SMART QUALITY COMPRESSION WITH MULTI-STAGE
    private fun smartQualityCompression(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        var quality = INITIAL_QUALITY

        // First compression attempt
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        var compressedData = outputStream.toByteArray()
        var sizeKB = compressedData.size / 1024

        Log.d(TAG, "First compression: ${sizeKB}KB with quality $quality")

        // If still too large, reduce quality in steps
        if (sizeKB > TARGET_FILE_SIZE_KB) {
            val qualitySteps = listOf(70, 60, 50, 40)
            for (stepQuality in qualitySteps) {
                if (sizeKB <= TARGET_FILE_SIZE_KB) break

                outputStream.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, stepQuality, outputStream)
                compressedData = outputStream.toByteArray()
                sizeKB = compressedData.size / 1024

                Log.d(TAG, "Reduced to: ${sizeKB}KB with quality $stepQuality")

                if (sizeKB <= TARGET_FILE_SIZE_KB) break
            }
        }

        Log.d(TAG, "Final compressed size: ${sizeKB}KB")
        return compressedData
    }

    // 🔥 GET ORIGINAL IMAGE SIZE FOR COMPARISON - FIXED
    private suspend fun getImageSize(uri: Uri): Long = withContext(Dispatchers.IO) {
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                var totalBytes = 0L
                val buffer = ByteArray(8192)
                var bytesRead: Int

                do {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead != -1) {
                        totalBytes += bytesRead
                    }
                } while (bytesRead != -1)

                totalBytes
            } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting image size", e)
            0
        }
    } / 1024 // Convert to KB



    // 🔥 UPLOAD COMPRESSED IMAGE DATA
    private fun uploadCompressedImage(compressedImageData: ByteArray) {
        DialogUtils.showLoadingDialog(requireContext(), "Uploading image...")

        val storageRef = storage.reference
        val profileImagesRef = storageRef.child("profile_images/${currentUserId}/${UUID.randomUUID()}")

        // Upload compressed bytes instead of file
        profileImagesRef.putBytes(compressedImageData)
            .addOnSuccessListener { taskSnapshot ->
                profileImagesRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    updateUserProfileImage(downloadUri.toString())
                }
            }
            .addOnFailureListener { exception ->
                DialogUtils.hideLoadingDialog()
                Toast.makeText(
                    requireContext(),
                    "Upload failed: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e(TAG, "Upload error", exception)
            }
    }

    // 🔥 QUICK COMPRESSION ALTERNATIVE (Simpler approach)
    private suspend fun quickCompress(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeStream(
                requireContext().contentResolver.openInputStream(uri)
            )

            // Simple resize if too large
            val resizedBitmap = if (bitmap.width > MAX_IMAGE_WIDTH || bitmap.height > MAX_IMAGE_HEIGHT) {
                val scale = minOf(
                    MAX_IMAGE_WIDTH.toFloat() / bitmap.width,
                    MAX_IMAGE_HEIGHT.toFloat() / bitmap.height
                )
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()

                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)

            val compressedData = outputStream.toByteArray()
            bitmap.recycle()
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }

            compressedData
        } catch (e: Exception) {
            throw e
        }
    }

    private fun updateUserProfileImage(imageUrl: String) {
        lifecycleScope.launch {
            try {
                if (currentProfileImageUrl.isNotEmpty()) {
                    deleteOldImage(currentProfileImageUrl)
                }

                when (val result = userRepository.updateUserProfileImage(currentUserId, imageUrl)) {
                    is UserRepository.Result.Success -> {
                        DialogUtils.hideLoadingDialog()
                        currentProfileImageUrl = imageUrl

                        Glide.with(requireContext())
                            .load(imageUrl)
                            .into(binding.profileImage)

                        Toast.makeText(
                            requireContext(),
                            "Profile image updated successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is UserRepository.Result.Failure -> {
                        DialogUtils.hideLoadingDialog()
                        Toast.makeText(
                            requireContext(),
                            "Failed to update profile: ${result.exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                DialogUtils.hideLoadingDialog()
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun deleteOldImage(oldImageUrl: String) {
        try {
            val oldImageRef = storage.getReferenceFromUrl(oldImageUrl)
            oldImageRef.delete().addOnFailureListener {
                Log.e(TAG, "Failed to delete old image: ${it.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting old image: ${e.message}")
        }
    }


}