package com.example.associate.Utils

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.associate.R
import kotlinx.coroutines.launch

/**
 * Utility to load the dynamic app icon from the admin panel URL.
 * Automatically handles caching so it loads instantly throughout the app.
 */
object AppIconLoader {

    /**
     * Loads the remote app icon into the provided [ImageView] from local storage.
     * Starts with a default placeholder if the local file is missing.
     */
    fun loadIcon(context: Context, imageView: ImageView) {
        val file = java.io.File(context.filesDir, "custom_app_logo.png")
        if (file.exists()) {
            Glide.with(context)
                .load(file)
                .diskCacheStrategy(DiskCacheStrategy.NONE) // Local file, no need to cache network
                .skipMemoryCache(true) // Ensure it picks up new files if overwritten
                .dontAnimate() // Explicitly prevent cross-fade and size jumps on splash screen
                .placeholder(R.drawable.app_logo)
                .error(R.drawable.app_logo)
                .into(imageView)
        } else {
            // Fallback to local drawable
            imageView.setImageResource(R.drawable.app_logo)
        }
    }

    /**
     * Updates the "Recent Apps" (Overview) screen icon to match the local icon file.
     * Should be called from the main entry point activity of the app.
     */
    fun applyTaskDescriptionIcon(activity: android.app.Activity) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val file = java.io.File(activity.filesDir, "custom_app_logo.png")
            if (!file.exists()) return

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val bitmap = Glide.with(activity.applicationContext)
                           .asBitmap()
                        .load(file)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .submit()
                        .get()
                    
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        val description = android.app.ActivityManager.TaskDescription(
                            activity.getString(R.string.app_name),
                            bitmap,
                            activity.resources.getColor(R.color.white)
                        )
                        activity.setTaskDescription(description)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AppIconLoader", "Failed to load TaskDescription icon", e)
                }
            }
        }
    }
}
