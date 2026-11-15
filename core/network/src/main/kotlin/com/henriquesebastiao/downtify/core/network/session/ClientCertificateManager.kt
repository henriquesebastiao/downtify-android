package com.henriquesebastiao.downtify.core.network.session

import android.content.Context
import android.security.KeyChain
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSessionContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

/** A private key and its certificate chain from Android's [KeyChain]. */
internal class ClientCredentials(val privateKey: PrivateKey, val chain: Array<X509Certificate>)

internal fun interface ClientKeyLoader {
    fun load(alias: String): ClientCredentials?
}

/**
 * Presents the user-chosen client certificate from Android's [KeyChain] when a reverse proxy
 * (such as Cloudflare Access mTLS, Caddy or nginx) requests one during the TLS handshake.
 *
 * Once paired, the certificate is only offered to the paired server's host so third-party
 * CDNs (cover images, preview clips) never see it.
 */
@Singleton
class ClientCertificateManager internal constructor(
    private val aliasProvider: () -> String?,
    private val baseUrlProvider: () -> String?,
    private val loader: ClientKeyLoader,
) : X509ExtendedKeyManager() {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        store: SessionStore,
    ) : this(
        aliasProvider = { store.currentClientCertificate },
        baseUrlProvider = { store.current?.baseUrl },
        loader = ClientKeyLoader { alias ->
            val key = KeyChain.getPrivateKey(context, alias) ?: return@ClientKeyLoader null
            val chain = KeyChain.getCertificateChain(context, alias)
                ?.takeIf { it.isNotEmpty() }
                ?: return@ClientKeyLoader null
            ClientCredentials(key, chain)
        },
    )

    private val lock = Any()

    @Volatile
    private var cached: Pair<String, ClientCredentials>? = null

    @Volatile
    private var sessionContext: SSLSessionContext? = null

    override fun chooseClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        socket: Socket?,
    ): String? {
        val host = runCatching { (socket as? SSLSocket)?.handshakeSession?.peerHost }.getOrNull()
            ?: runCatching { (socket?.remoteSocketAddress as? InetSocketAddress)?.hostString }.getOrNull()
            ?: runCatching { socket?.inetAddress?.hostAddress }.getOrNull()
        return selectAlias(host)
    }

    override fun chooseEngineClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        engine: SSLEngine?,
    ): String? {
        val host = runCatching { engine?.peerHost }.getOrNull()
        return selectAlias(host)
    }

    override fun getPrivateKey(alias: String?): PrivateKey? =
        alias?.takeIf { it.isNotBlank() }?.let(::credentialsFor)?.privateKey

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
        alias?.takeIf { it.isNotBlank() }?.let(::credentialsFor)?.chain

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null

    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null

    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null

    /** Drops any cached key, certificate chain and TLS sessions so the next handshake reloads from [KeyChain]. */
    fun clearCache() = synchronized(lock) {
        cached = null
        runCatching {
            val ctx = sessionContext ?: return@runCatching
            val ids = ctx.ids ?: return@runCatching
            while (ids.hasMoreElements()) {
                ctx.getSession(ids.nextElement())?.invalidate()
            }
        }
    }

    /** Wires this key manager into [builder] while keeping Android's network-security-config trust manager. */
    fun configureTls(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        val trustManager = systemTrustManager() ?: return builder
        val sslContext = runCatching {
            SSLContext.getInstance("TLS").apply {
                init(arrayOf<KeyManager>(this@ClientCertificateManager), arrayOf<TrustManager>(trustManager), null)
                runCatching {
                    sessionContext = clientSessionContext
                }
            }
        }.getOrNull() ?: return builder
        return builder.sslSocketFactory(sslContext.socketFactory, trustManager)
    }

    private fun selectAlias(peerHost: String?): String? {
        val alias = aliasProvider()?.takeIf { it.isNotBlank() } ?: return null
        if (!allowsHost(peerHost, baseUrlProvider())) return null
        return alias
    }

    // KeyChain may throw KeyChainException, InterruptedException or SecurityException.
    @Suppress("TooGenericExceptionCaught")
    private fun credentialsFor(alias: String): ClientCredentials? {
        cached?.let { (cachedAlias, creds) -> if (cachedAlias == alias) return creds }
        return synchronized(lock) {
            cached?.let { (cachedAlias, creds) -> if (cachedAlias == alias) return@synchronized creds }
            val loaded = try {
                loader.load(alias)
            } catch (e: Exception) {
                Log.w(TAG, "Could not load client certificate '$alias' from KeyChain", e)
                null
            }
            if (loaded != null) cached = alias to loaded
            loaded
        }
    }

    private fun systemTrustManager(): X509TrustManager? = runCatching {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        factory.trustManagers?.filterIsInstance<X509TrustManager>()?.firstOrNull()
    }.getOrNull()

    companion object {
        private const val TAG = "ClientCertManager"

        /**
         * Whether [peerHost] may receive the client certificate when paired with [pairedBaseUrl].
         * Before pairing ([pairedBaseUrl] is null or blank), any valid host being probed or paired with may receive
         * it; once paired, only the paired server's host may.
         */
        fun allowsHost(peerHost: String?, pairedBaseUrl: String?): Boolean {
            if (peerHost.isNullOrBlank()) return false
            val peerClean = cleanHost(peerHost) ?: return false
            if (pairedBaseUrl.isNullOrBlank()) return true

            val pairedHost = pairedBaseUrl.toHttpUrlOrNull()?.host
                ?: pairedBaseUrl.trim()
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .substringBefore('/')
                    .takeIf { it.isNotBlank() }
                ?: return false

            val pairedClean = cleanHost(pairedHost) ?: return false
            return peerClean.equals(pairedClean, ignoreCase = true)
        }

        private fun cleanHost(host: String): String? {
            val trimmed = host.trim().trim('[', ']')
            if (trimmed.isEmpty()) return null
            return if (trimmed.contains(':') && !trimmed.contains('.')) {
                trimmed
            } else {
                trimmed.substringBefore(':')
            }
        }
    }
}
