package com.henriquesebastiao.downtify.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Whether the app runs on a TV (Android TV, Google TV, Fire TV): there it shows the TV interface
 * ([TvShell]) instead of the phone and tablet one. Fire TV reports both signs; either is enough.
 */
fun Context.isTelevision(): Boolean {
    val uiMode = getSystemService(UiModeManager::class.java)?.currentModeType
    return uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}
