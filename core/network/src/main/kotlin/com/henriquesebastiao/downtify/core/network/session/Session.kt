package com.henriquesebastiao.downtify.core.network.session

/** The server this phone is paired with, and its device token. */
data class Session(
    /** No trailing slash; may have a path (a reverse proxy). */
    val baseUrl: String,
    val serverId: String,
    val serverName: String,
    val deviceId: String,
    val token: String,
    /** The account the device belongs to; blank on servers without accounts. */
    val username: String = "",
) {
    override fun toString(): String = "Session(baseUrl=$baseUrl, serverId=$serverId, deviceId=$deviceId, token=***)"
}

/** Whose cached audio this is: one server and one pairing (the read-ahead cache and its receipts). */
val Session.cacheOwner: String get() = "${serverId.ifBlank { baseUrl }}\u0000$deviceId"

/** Why the app is back at the connect screen. */
enum class SignOutReason {
    /** The user unpaired or changed server. */
    User,

    /** The server answered 401 to our token, or closed the socket with 4401. */
    Revoked,
}
