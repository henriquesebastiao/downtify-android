package com.henriquesebastiao.downtify.core.network.session

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The paired server and its token, kept across launches. The token is
 * encrypted with a Keystore-backed key ([TokenCipher]).
 *
 * Reads block on first use (Keystore); call [ensureLoaded] off the main thread
 * before relying on [session].
 */
@Singleton
class SessionStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cipher = TokenCipher(context)
    private val state = MutableStateFlow<Session?>(null)
    private val clientCert = MutableStateFlow<String?>(null)
    private val reason = MutableStateFlow<SignOutReason?>(null)
    private val lock = Any()

    @Volatile
    private var loaded = false

    internal var onClientCertificateChanged: (() -> Unit)? = null

    /** The paired session, or null. */
    val session: StateFlow<Session?>
        get() {
            ensureLoaded()
            return state.asStateFlow()
        }

    /** Alias of the client certificate in Android's KeyChain for mTLS, or null. */
    val clientCertificate: StateFlow<String?>
        get() {
            ensureLoaded()
            return clientCert.asStateFlow()
        }

    /** Why the last session ended, for the connect screen; null after a fresh start. */
    val signOutReason: StateFlow<SignOutReason?> = reason.asStateFlow()

    val current: Session? get() = session.value
    val currentClientCertificate: String? get() = clientCertificate.value

    fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (!loaded) {
                clientCert.value = prefs.getString(KEY_CLIENT_CERT, null)?.takeIf { it.isNotBlank() }
                state.value = read()
                loaded = true
            }
        }
    }

    /** Sets or clears the KeyChain alias presented when a server asks for a client certificate (mTLS). */
    fun setClientCertificate(alias: String?) {
        val shouldEvict = synchronized(lock) {
            ensureLoaded()
            val normalized = alias?.trim()?.takeIf { it.isNotEmpty() }
            if (clientCert.value != normalized) {
                prefs.edit {
                    if (normalized == null) remove(KEY_CLIENT_CERT) else putString(KEY_CLIENT_CERT, normalized)
                }
                clientCert.value = normalized
                true
            } else {
                normalized != null
            }
        }
        if (shouldEvict) onClientCertificateChanged?.invoke()
    }

    fun save(session: Session) = synchronized(lock) {
        prefs.edit {
            putString(KEY_BASE_URL, session.baseUrl)
            putString(KEY_SERVER_ID, session.serverId)
            putString(KEY_SERVER_NAME, session.serverName)
            putString(KEY_DEVICE_ID, session.deviceId)
            putString(KEY_USERNAME, session.username)
            putString(KEY_TOKEN, cipher.encrypt(session.token))
        }
        loaded = true
        reason.value = null
        state.value = session
    }

    /** Updates what the server calls itself or where it is, keeping the token. */
    fun update(transform: (Session) -> Session) = synchronized(lock) {
        val current = state.value ?: return@synchronized
        save(transform(current))
    }

    /** Forgets the token. The server still lists the device until it's unpaired there. */
    fun clear(why: SignOutReason) = synchronized(lock) {
        ensureLoaded()
        clearSessionPrefs()
        loaded = true
        reason.value = why
        state.value = null
    }

    /**
     * The server refused [token] (401 "Invalid or revoked token", or WebSocket
     * 4401). Drops it — unless the session changed meanwhile (a new pairing).
     */
    fun onRevoked(token: String) = synchronized(lock) {
        if (state.value?.token == token) {
            Log.w(TAG, "The server refused this phone's token: unpaired")
            clear(SignOutReason.Revoked)
        }
    }

    private fun clearSessionPrefs() {
        val cert = clientCert.value
        prefs.edit {
            clear()
            if (cert != null) putString(KEY_CLIENT_CERT, cert)
        }
    }

    private fun read(): Session? {
        val baseUrl = prefs.getString(KEY_BASE_URL, null) ?: return null
        val encrypted = prefs.getString(KEY_TOKEN, null) ?: return null
        val token = try {
            cipher.decrypt(encrypted)
        } catch (e: GeneralSecurityException) {
            // A lost or rotated Keystore key (e.g. restored onto another phone): pair again.
            Log.w(TAG, "Could not decrypt the device token; pairing again", e)
            clearSessionPrefs()
            return null
        }
        return Session(
            baseUrl = baseUrl,
            serverId = prefs.getString(KEY_SERVER_ID, "").orEmpty(),
            serverName = prefs.getString(KEY_SERVER_NAME, "").orEmpty(),
            deviceId = prefs.getString(KEY_DEVICE_ID, "").orEmpty(),
            token = token,
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
        )
    }

    private companion object {
        const val TAG = "SessionStore"
        const val PREFS = "downtify_session"
        const val KEY_BASE_URL = "base_url"
        const val KEY_SERVER_ID = "server_id"
        const val KEY_SERVER_NAME = "server_name"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_TOKEN = "token"
        const val KEY_USERNAME = "username"
        const val KEY_CLIENT_CERT = "client_cert_alias"
    }
}
