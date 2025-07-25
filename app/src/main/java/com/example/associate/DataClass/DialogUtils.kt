package com.example.associate.DataClass

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.widget.Button
import android.widget.TextView
import com.airbnb.lottie.LottieAnimationView
import com.example.associate.R

object DialogUtils {

    fun showStatusDialog(
        context: Context,
        isSuccess: Boolean,
        title: String,
        message: String,
        action: (() -> Unit)? = null
    ): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.loding_dialog)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val animationView = dialog.findViewById<LottieAnimationView>(R.id.lottieAnimation)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialog.findViewById<TextView>(R.id.tvDialogMessage)
        val btnAction = dialog.findViewById<Button>(R.id.btnDialogAction)

        if (isSuccess) {
            animationView.setAnimation(R.raw.success_animation)
            tvTitle.setTextColor(context.getColor(R.color.green))
        } else {
            animationView.setAnimation(R.raw.error_animation)
            tvTitle.setTextColor(context.getColor(R.color.red))
        }

        tvTitle.text = title
        tvMessage.text = message

        btnAction.setOnClickListener {
            dialog.dismiss()
            action?.invoke()
        }

        dialog.show()
        return dialog
    }
}