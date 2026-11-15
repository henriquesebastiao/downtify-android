package com.henriquesebastiao.downtify.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.security.KeyChain
import com.henriquesebastiao.downtify.core.model.ServerAddress
import java.net.URI

/**
 * If [KeyChain.choosePrivateKeyAlias] returns `null` faster than this threshold, Android's
 * `KeyChainActivity` closed itself without showing a dialog because no user certificate is
 * installed on the device yet.
 */
private const val EMPTY_KEYCHAIN_MS = 450L

/**
 * Opens Android's system credential picker so the user can choose (or grant access to) a client
 * certificate for mutual TLS (mTLS). When no certificate is installed on the device yet, opens
 * Android's certificate installer ([KeyChain.createInstallIntent]) so the user can pick a `.p12`
 * file directly.
 */
fun chooseClientCertificate(
    context: Context,
    currentAlias: String?,
    addressOrUrl: String? = null,
    onSelected: (String) -> Unit,
) {
    val activity = context.findActivity() ?: return
    val uri = addressOrUrl?.let(ServerAddress::normalize)?.let { runCatching { URI(it) }.getOrNull() }
    val startedAt = SystemClock.elapsedRealtime()
    KeyChain.choosePrivateKeyAlias(
        activity,
        { alias ->
            activity.runOnUiThread {
                if (!alias.isNullOrBlank()) {
                    onSelected(alias)
                } else if (SystemClock.elapsedRealtime() - startedAt < EMPTY_KEYCHAIN_MS) {
                    runCatching { activity.startActivity(KeyChain.createInstallIntent()) }
                }
            }
        },
        /* keyTypes = */
        null,
        /* issuers = */
        null,
        /* host = */
        uri?.host,
        /* port = */
        uri?.port ?: -1,
        /* alias = */
        currentAlias,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
