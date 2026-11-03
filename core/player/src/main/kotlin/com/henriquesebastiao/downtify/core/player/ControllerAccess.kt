package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession

/** A trusted car/system controller can play; only this application gets internal queue commands. */
@UnstableApi
internal object ControllerAccess {
    fun isApp(controller: MediaSession.ControllerInfo, packageName: String, uid: Int): Boolean =
        controller.uid == uid && controller.packageName == packageName

    fun canConnect(controller: MediaSession.ControllerInfo, packageName: String, uid: Int): Boolean =
        isApp(controller, packageName, uid) || controller.isTrusted
}
