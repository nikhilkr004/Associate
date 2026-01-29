package com.example.associate.Dialogs

import android.app.Dialog
import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.example.associate.R
import com.example.associate.Activities.VideoCallActivity
import android.content.Intent
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log

class IncomingCallDialog(
    context: Context,
    private val callId: String,
    private val advisorName: String,
    private val channelName: String,
    private val profileImage: String? = null,
    advisorId: String,
    urgencyLevel: String,
    bookingId: String
) : Dialog(context, R.style.FullScreenDialog) {

    private var mediaPlayer: MediaPlayer? = null
    private var ringtoneHandler: Handler? = null
    private var ringtoneRunnable: Runnable? = null
    private var isDialogActive = true
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.incoming_call_item)

        setupWindow()
        initializeViews()
        startRingtone()
    }

    private fun setupWindow() {
        window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawableResource(android.R.color.transparent)

            // Full screen with system UI flags
            setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )

            // Show over lock screen
            addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Position at top
            setGravity(Gravity.TOP)
        }

        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }

    private fun initializeViews() {
        // Set caller name
        findViewById<TextView>(R.id.tvCallerName).text = advisorName

        // Set profile image
        profileImage?.let { imageUrl ->
            if (imageUrl.isNotEmpty()) {
                Glide.with(context)
                    .load(imageUrl)
                    .placeholder(R.drawable.user)
                    .into(findViewById(R.id.ivCallerProfile))
            }
        }

        // Accept button
        findViewById<ImageView>(R.id.btnAcceptCall).setOnClickListener {
            acceptCall()
        }

        // Reject button
        findViewById<ImageView>(R.id.btnRejectCall).setOnClickListener {
            rejectCall()
        }

        // Swipe gestures (optional) - agar root view hai layout mein
        val rootView = findViewById<View>(android.R.id.content)
        rootView?.setOnTouchListener(object : OnSwipeTouchListener(context) {
            override fun onSwipeUp() {
                acceptCall()
            }

            override fun onSwipeDown() {
                rejectCall()
            }
        })
    }

    private fun startRingtone() {
        try {
            // Stop any existing ringtone
            stopRingtone()

            // Play custom ringtone if available, else default
            val rawId = context.resources.getIdentifier("incoming_call_tone", "raw", context.packageName)
            if (rawId != 0) {
                 mediaPlayer = MediaPlayer.create(context, rawId)
            } else {
                 val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                 mediaPlayer = MediaPlayer.create(context, ringtoneUri)
            }
            
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()

            // Stop automatically after 45 seconds if not answered
            ringtoneHandler = Handler(Looper.getMainLooper())
            ringtoneRunnable = Runnable {
                if (isDialogActive) {
                    rejectCall()
                }
            }
            ringtoneHandler?.postDelayed(ringtoneRunnable!!, 45000) // 45 seconds

        } catch (e: Exception) {
            e.printStackTrace()
            // Agar ringtone play nahi ho pa raha, toh bhi call show karein
        }
    }

    private fun stopRingtone() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            ringtoneHandler?.removeCallbacks(ringtoneRunnable ?: return)
            ringtoneHandler = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun acceptCall() {
        if (!isDialogActive) return
        isDialogActive = false

        stopRingtone()

        // Update call status to accepted
        updateCallStatus("accepted")

        // Start video call activity
        val intent = Intent(context, VideoCallActivity::class.java).apply {
            putExtra("CALL_ID", callId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("IS_INCOMING_CALL", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)

        dismiss()
    }

    private fun rejectCall() {
        if (!isDialogActive) return
        isDialogActive = false

        stopRingtone()

        // Update call status to rejected in Firestore
        updateCallStatus("rejected")
        dismiss()
    }

    private fun updateCallStatus(status: String) {
        // Update videoCalls collection
        db.collection("videoCalls")
            .document(callId)
            .update("status", status)
            .addOnSuccessListener {
                Log.d("IncomingCallDialog", "VideoCall status updated to: $status")
            }
            .addOnFailureListener { e ->
                Log.e("IncomingCallDialog", "Failed to update videoCall status: ${e.message}")
            }

        // Update instant_bookings collection (Redundancy for Advisor Listener)
        db.collection("instant_bookings")
            .document(callId)
            // Combined update for both fields to avoid chaining error (update() returns Task, not Ref)
            .update(mapOf(
                "status" to status,
                "bookingStatus" to status
            ))
            .addOnSuccessListener {
                 Log.d("IncomingCallDialog", "InstantBooking status updated to: $status")
            }
            .addOnFailureListener { e ->
                 // Start silent, might not exist if it's just a call.
                 Log.w("IncomingCallDialog", "Failed/Skipped instant_booking update: ${e.message}")
            }
    }

    override fun dismiss() {
        stopRingtone()
        isDialogActive = false
        super.dismiss()
    }

    override fun onBackPressed() {
        // Back button press par call reject karein
        rejectCall()
    }
}

// Separate class for swipe gestures
open class OnSwipeTouchListener(ctx: Context) : View.OnTouchListener {

    private val gestureDetector: android.view.GestureDetector

    init {
        gestureDetector = android.view.GestureDetector(ctx, GestureListener())
    }

    override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event)
    }

    private inner class GestureListener : android.view.GestureDetector.SimpleOnGestureListener() {

        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onDown(e: android.view.MotionEvent): Boolean {
            return true
        }

        override fun onFling(
            e1: android.view.MotionEvent?,
            e2: android.view.MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false

            val diffY = e2.y - e1.y
            val diffX = e2.x - e1.x
            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        onSwipeRight()
                    } else {
                        onSwipeLeft()
                    }
                }
            } else {
                if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        onSwipeDown()
                    } else {
                        onSwipeUp()
                    }
                }
            }
            return false
        }
    }

    open fun onSwipeRight() {}
    open fun onSwipeLeft() {}
    open fun onSwipeUp() {}
    open fun onSwipeDown() {}
}
// Updated for repository activity
