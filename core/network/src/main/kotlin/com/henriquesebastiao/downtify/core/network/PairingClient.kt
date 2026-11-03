package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.network.dto.PairRequest
import com.henriquesebastiao.downtify.core.network.dto.PairResponse
import com.henriquesebastiao.downtify.core.network.dto.SignInRequest
import com.henriquesebastiao.downtify.core.network.session.Session
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Gets this phone its device token: with the account's username and password
 * (`POST /api/auth/token`), or with a pairing code from the web page
 * (`POST /api/auth/pair`). Both answer the same way.
 */
class PairingClient @Inject constructor(private val apis: ApiFactory) {
    sealed interface Result {
        data class Paired(val session: Session) : Result

        /** 401 to a pairing code: it's wrong, expired or already used. */
        data object WrongCode : Result

        /** 401 to a sign-in: the username or the password is wrong (the server doesn't say which). */
        data object WrongPassword : Result

        /** 409 to a sign-in: accounts are turned off on the server, so only a pairing code works. */
        data object SignInOff : Result

        /** 429: too many wrong codes or passwords from this address. */
        data class TooManyAttempts(val retryAfterSeconds: Long) : Result
        data object Unreachable : Result
        data class Failed(val status: Int) : Result
    }

    /** Pairs with [baseUrl]; the code is sent as typed (the server normalises it). */
    suspend fun pair(baseUrl: String, code: String, deviceName: String): Result =
        obtain(baseUrl, unauthorized = Result.WrongCode) {
            pair(PairRequest(code = code.trim(), deviceName = deviceName))
        }

    /** Signs in to [baseUrl] as [username]; the password is sent as typed and kept nowhere. */
    suspend fun signIn(baseUrl: String, username: String, password: String, deviceName: String): Result =
        obtain(baseUrl, unauthorized = Result.WrongPassword) {
            signIn(SignInRequest(username = username.trim(), password = password, deviceName = deviceName))
        }

    @Suppress("TooGenericExceptionCaught") // Network boundary: every failure maps to a Result.
    private suspend fun obtain(
        baseUrl: String,
        unauthorized: Result,
        call: suspend DowntifyApi.() -> PairResponse,
    ): Result = try {
        val response = apis.create(baseUrl).call()
        Result.Paired(
            Session(
                baseUrl = baseUrl,
                serverId = response.server.serverId,
                serverName = response.server.name,
                deviceId = response.device.id,
                token = response.token,
                username = response.user?.username.orEmpty(),
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        when (e.httpStatus) {
            null -> Result.Unreachable
            HTTP_UNAUTHORIZED -> unauthorized
            HTTP_CONFLICT -> Result.SignInOff
            HTTP_TOO_MANY -> Result.TooManyAttempts(e.retryAfterSeconds ?: DEFAULT_RETRY_AFTER)
            else -> Result.Failed(e.httpStatus ?: 0)
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_CONFLICT = 409
        const val HTTP_TOO_MANY = 429
        const val DEFAULT_RETRY_AFTER = 60L
    }
}
