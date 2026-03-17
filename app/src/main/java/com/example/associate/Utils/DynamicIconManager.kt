package com.example.associate.Utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Manages dynamic app icon changes using `<activity-alias>` in the AndroidManifest.
 */
object DynamicIconManager {
    
    // Names must match the aliases defined in AndroidManifest.xml
    private const val ALIAS_DEFAULT = "com.example.associate.Activities.SplashScreenActivity"
    private const val ALIAS_REMOTE_ICON_1 = "com.example.associate.Activities.SplashAliasRemote1"
    private const val ALIAS_REMOTE_ICON_2 = "com.example.associate.Activities.SplashAliasRemote2"

    private const val PREF_NAME = "DynamicIconPrefs"
    private const val KEY_ACTIVE_ALIAS = "ActiveAlias"

    /**
     * Checks if the icon needs to be updated. Since Android requires building the icon into the APK 
     * for the launcher, we can't fetch a raw URL and set it as the launcher icon on the fly.
     * 
     * What we CAN do is apply the remote icon anywhere inside the app (Toolbar, Profile, Splash) 
     * using Glide. 
     * 
     * For the ACTUAL home screen launcher icon, Android only allows switching between pre-defined 
     * `<activity-alias>` elements. If the user wants to dynamically load ANY url as a home screen icon, 
     * that is fundamentally impossible in Android due to OS security restrictions.
     */
    fun logIconStatus() {
        Log.i("DynamicIconManager", "Remote icon caching is handled by Glide for in-app use.")
        Log.w("DynamicIconManager", "Note: Android OS does not allow downloading an image from the internet and setting it as the home screen launcher icon. It must be a drawable compiled into the app. We apply it everywhere inside the app instead.")
    }
}
