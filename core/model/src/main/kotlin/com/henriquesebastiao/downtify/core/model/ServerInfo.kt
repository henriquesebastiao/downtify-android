package com.henriquesebastiao.downtify.core.model

/** `GET /api/server/info`: who a server is and what it can do. */
data class ServerInfo(
    val serverId: String,
    val name: String,
    val product: String,
    val version: String,
    val apiVersion: Int,
    val requireSignIn: Boolean,
    val capabilities: Capabilities,
) {
    /** Whether this app can talk to the server at all. */
    val compatibility: Compatibility
        get() = when {
            product != PRODUCT -> Compatibility.NotDowntify
            apiVersion > SUPPORTED_API_VERSION -> Compatibility.TooNew
            else -> Compatibility.Ok
        }

    enum class Compatibility { Ok, NotDowntify, TooNew }

    companion object {
        const val PRODUCT = "Downtify"

        /** The highest `api_version` this app understands. */
        const val SUPPORTED_API_VERSION = 1
    }
}

data class Capabilities(
    val transcoding: Transcoding = Transcoding(),
    val signedUrls: Boolean = false,
    val pairing: Boolean = false,
    val podcasts: Boolean = false,
    val discover: Boolean = false,
    val lyrics: Boolean = false,
    val librarySync: Boolean = false,
    /** The app may sign in with a username and password (`POST /api/auth/token`); else it pairs with a code. */
    val passwordSignIn: Boolean = false,
)

data class Transcoding(
    val available: Boolean = false,
    val formats: List<String> = emptyList(),
    val bitrates: List<Int> = emptyList(),
)
