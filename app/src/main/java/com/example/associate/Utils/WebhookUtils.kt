package com.example.associate.Utils

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder

object WebhookUtils {
    private val client = OkHttpClient()

    fun triggerWelcomeEmail(name: String, email: String) {
        try {
            val encodedName = URLEncoder.encode(name, "UTF-8")
            val encodedEmail = URLEncoder.encode(email, "UTF-8")
            val webhookUrl = "https://associateapp.app.n8n.cloud/webhook/8e4a094d-f89f-46cf-99e7-87f335373043?eventType=registration&name=$encodedName&email=$encodedEmail"

            android.util.Log.d("WebhookUtils", "🚀 Triggering Registration Webhook: $webhookUrl")

            val request = Request.Builder()
                .url(webhookUrl)
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    android.util.Log.e("WebhookUtils", "❌ Registration Webhook Failed: ${e.message}")
                    e.printStackTrace()
                }

                override fun onResponse(call: Call, response: Response) {
                    android.util.Log.d("WebhookUtils", "✅ Registration Webhook Response: ${response.code}")
                    response.close()
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("WebhookUtils", "🔥 Registration Exception: ${e.message}")
            e.printStackTrace()
        }
    }

    fun triggerBookingEmail(
        name: String,
        email: String,
        meetingDate: String,
        meetingTime: String,
        duration: String,
        bookingType: String,
        advisorName: String,
        bookingId: String,
        amount: String
    ) {
        try {
            val encodedName = URLEncoder.encode(name, "UTF-8")
            val encodedEmail = URLEncoder.encode(email, "UTF-8")
            val encodedDate = URLEncoder.encode(meetingDate, "UTF-8")
            val encodedTime = URLEncoder.encode(meetingTime, "UTF-8")
            val encodedDuration = URLEncoder.encode(duration, "UTF-8")
            val encodedBookingType = URLEncoder.encode(bookingType, "UTF-8")
            val encodedAdvisorName = URLEncoder.encode(advisorName, "UTF-8")
            val encodedBookingId = URLEncoder.encode(bookingId, "UTF-8")
            val encodedAmount = URLEncoder.encode(amount, "UTF-8")
            
            val webhookUrl = "https://associateapp.app.n8n.cloud/webhook/8e4a094d-f89f-46cf-99e7-87f335373043?eventType=booking" +
                    "&name=$encodedName" +
                    "&email=$encodedEmail" +
                    "&meetingDate=$encodedDate" +
                    "&meetingTime=$encodedTime" +
                    "&duration=$encodedDuration" +
                    "&bookingType=$encodedBookingType" +
                    "&advisorName=$encodedAdvisorName" +
                    "&bookingId=$encodedBookingId" +
                    "&amount=$encodedAmount"

            android.util.Log.d("WebhookUtils", "🚀 Triggering Booking Webhook: $webhookUrl")

            val request = Request.Builder()
                .url(webhookUrl)
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    android.util.Log.e("WebhookUtils", "❌ Booking Webhook Failed: ${e.message}")
                    e.printStackTrace()
                }

                override fun onResponse(call: Call, response: Response) {
                    android.util.Log.d("WebhookUtils", "✅ Booking Webhook Response: ${response.code}")
                    response.close()
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("WebhookUtils", "🔥 Booking Exception: ${e.message}")
            e.printStackTrace()
        }
    }
}