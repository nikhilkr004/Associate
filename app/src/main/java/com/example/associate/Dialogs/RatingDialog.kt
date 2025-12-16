package com.example.associate.Dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.RatingBar
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.associate.R

class RatingDialog(private val listener: (Float, String) -> Unit) : DialogFragment() {

    private var currentRating = 0f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_rating, container, false)

        val emojis = listOf(
            view.findViewById<android.widget.TextView>(R.id.tvEmoji1),
            view.findViewById<android.widget.TextView>(R.id.tvEmoji2),
            view.findViewById<android.widget.TextView>(R.id.tvEmoji3),
            view.findViewById<android.widget.TextView>(R.id.tvEmoji4),
            view.findViewById<android.widget.TextView>(R.id.tvEmoji5)
        )
        
        val etReview = view.findViewById<android.widget.EditText>(R.id.etReview)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmit)

        // Setup Emoji Click Listeners
        emojis.forEachIndexed { index, textView ->
            textView.setOnClickListener {
                currentRating = (index + 1).toFloat()
                updateEmojiSelection(emojis, index)
            }
        }

        btnSubmit.setOnClickListener {
            val review = etReview.text.toString().trim()

            if (currentRating == 0f) {
                Toast.makeText(requireContext(), "Please select a rating", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            listener(currentRating, review)
            dismiss()
        }

        return view
    }

    private fun updateEmojiSelection(emojis: List<android.widget.TextView>, selectedIndex: Int) {
        emojis.forEachIndexed { index, textView ->
            if (index == selectedIndex) {
                textView.alpha = 1.0f
                textView.scaleX = 1.2f
                textView.scaleY = 1.2f
            } else {
                textView.alpha = 0.5f
                textView.scaleX = 1.0f
                textView.scaleY = 1.0f
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
