package com.example.associate.DataClass

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
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


    private var loadingDialog: Dialog? = null

    fun showLoadingDialog(context: Context, message: String = "") {
        if (loadingDialog?.isShowing == true) return

        loadingDialog = Dialog(context)
        loadingDialog?.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.progress_bar_dialog)
            setCancelable(false)

            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }

            findViewById<TextView>(R.id.loadingText)?.text = message

            show()
        }
    }


    fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }





    fun getBookingPurposes(): List<String> = listOf(
        "Select Purpose",
        "Career Guidance",
        "Interview Preparation",
        "Subject Help",
        "Project Guidance",
        "Resume Review",
        "Study Plan",
        "Other"
    )

    fun getPreferredLanguages(): List<String> = listOf(
        "Select Language",
        "Hindi",
        "English",
        "Hindi & English",
        "Regional Language"
    )

    fun getUrgencyLevels(): List<String> = listOf("Medium", "Low", "High")

    fun isValidPurpose(purpose: String): Boolean {
        return purpose != "Select Purpose" && getBookingPurposes().drop(1).contains(purpose)
    }

    fun isValidLanguage(language: String): Boolean {
        return language != "Select Language" && getPreferredLanguages().drop(1).contains(language)
    }

}