package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession

/**
 * External media controllers (Android Auto, Assistant, Bluetooth) can browse and play;
 * only this application gets internal queue commands.
 */
@UnstableApi
internal object ControllerAccess {
    fun isApp(controller: MediaSession.ControllerInfo, packageName: String, uid: Int): Boolean =
        controller.uid == uid && controller.packageName == packageName

    @Suppress("FunctionOnlyReturningConstant", "UNUSED_PARAMETER")
    fun canConnect(controller: MediaSession.ControllerInfo, packageName: String, uid: Int): Boolean = true
}
