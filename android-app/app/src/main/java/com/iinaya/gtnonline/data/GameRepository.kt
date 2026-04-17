package com.iinaya.gtnonline.data

import com.iinaya.gtnonline.data.remote.CreateRoomRequest
import com.iinaya.gtnonline.data.remote.GameApiService
import com.iinaya.gtnonline.data.remote.GameState
import com.iinaya.gtnonline.data.remote.JoinRoomRequest
import com.iinaya.gtnonline.data.remote.LeaveRoomRequest
import com.iinaya.gtnonline.data.remote.PlayerSession
import com.iinaya.gtnonline.data.remote.RematchRequest
import com.iinaya.gtnonline.data.remote.SubmitGuessRequest
import com.iinaya.gtnonline.data.remote.SubmitGuessResponse
import com.iinaya.gtnonline.data.remote.SubmitSecretRequest
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class GameRepository(private val api: GameApiService) {

    suspend fun createRoom(displayName: String, appVersion: String): Result<PlayerSession> =
        safeApiCall {
            val response = api.createRoom(
                CreateRoomRequest(
                    displayName = displayName,
                    appVersion = appVersion,
                )
            )
            val body = response.body()

            when {
                !response.isSuccessful -> Result.failure(ApiException("Create room failed (${response.code()})"))
                body == null -> Result.failure(ApiException("Empty server response"))
                !body.ok -> Result.failure(ApiException(body.error ?: "Create room failed"))
                body.roomCode.isNullOrBlank() ||
                    body.playerToken.isNullOrBlank() ||
                    body.role.isNullOrBlank() -> {
                    Result.failure(ApiException("Incomplete session response"))
                }
                else -> Result.success(
                    PlayerSession(
                        roomCode = body.roomCode,
                        playerToken = body.playerToken,
                        role = body.role,
                        displayName = body.displayName?.takeIf { it.isNotBlank() } ?: displayName,
                        appVersion = body.appVersion?.takeIf { it.isNotBlank() } ?: appVersion,
                    )
                )
            }
        }
 
    suspend fun joinRoom(roomCode: String, displayName: String, appVersion: String): Result<PlayerSession> =
        safeApiCall {
            val response = api.joinRoom(
                JoinRoomRequest(
                    roomCode = roomCode,
                    displayName = displayName,
                    appVersion = appVersion,
                )
            )
            val body = response.body()

            when {
                !response.isSuccessful -> Result.failure(ApiException("Join room failed (${response.code()})"))
                body == null -> Result.failure(ApiException("Empty server response"))
                !body.ok -> Result.failure(ApiException(body.error ?: "Join room failed"))
                body.roomCode.isNullOrBlank() ||
                    body.playerToken.isNullOrBlank() ||
                    body.role.isNullOrBlank() -> {
                    Result.failure(ApiException("Incomplete session response"))
                }
                else -> Result.success(
                    PlayerSession(
                        roomCode = body.roomCode,
                        playerToken = body.playerToken,
                        role = body.role,
                        displayName = body.displayName?.takeIf { it.isNotBlank() } ?: displayName,
                        appVersion = body.appVersion?.takeIf { it.isNotBlank() } ?: appVersion,
                    )
                )
            }
        }
 
    suspend fun submitSecret(session: PlayerSession, secret: String): Result<GameState> =
        safeApiCall {
            val response = api.submitSecret(
                SubmitSecretRequest(
                    roomCode = session.roomCode,
                    playerToken = session.playerToken,
                    secret = secret,
                    appVersion = session.appVersion,
                )
            )
            val body = response.body()

            when {
                !response.isSuccessful -> Result.failure(ApiException("Submit secret failed (${response.code()})"))
                body == null -> Result.failure(ApiException("Empty server response"))
                !body.ok -> Result.failure(ApiException(body.error ?: "Submit secret failed"))
                body.state == null -> Result.failure(ApiException("No state returned"))
                else -> Result.success(body.state)
            }
        }
 
    suspend fun submitGuess(session: PlayerSession, guess: String): Result<SubmitGuessResponse> =
        safeApiCall {
            val response = api.submitGuess(
                SubmitGuessRequest(
                    roomCode = session.roomCode,
                    playerToken = session.playerToken,
                    guess = guess,
                    appVersion = session.appVersion,
                )
            )
            handleGuessResponse(response)
        }
 
    suspend fun fetchState(session: PlayerSession): Result<GameState> =
        safeApiCall {
            val response = api.getState(
                roomCode = session.roomCode,
                playerToken = session.playerToken,
                appVersion = session.appVersion,
            )
            val body = response.body()

            when {
                !response.isSuccessful -> Result.failure(ApiException("State sync failed (${response.code()})"))
                body == null -> Result.failure(ApiException("Empty server response"))
                !body.ok -> Result.failure(ApiException(body.error ?: "State sync failed"))
                body.state == null -> Result.failure(ApiException("No state returned"))
                else -> Result.success(body.state)
            }
        }
 
    suspend fun leaveRoom(session: PlayerSession): Result<Unit> =
        safeApiCall {
            val response = api.leaveRoom(
                LeaveRoomRequest(
                    roomCode = session.roomCode,
                    playerToken = session.playerToken,
                    appVersion = session.appVersion,
                )
            )
            val body = response.body()

            when {
                !response.isSuccessful -> Result.failure(ApiException("Leave room failed (${response.code()})"))
                body == null -> Result.failure(ApiException("Empty server response"))
                !body.ok -> Result.failure(ApiException(body.error ?: "Leave room failed"))
                else -> Result.success(Unit)
            }
        }
 
    suspend fun rematch(session: PlayerSession): Result<GameState> =
        safeApiCall {
            val response = api.rematch(
                RematchRequest(
                    roomCode = session.roomCode,
                    playerToken = session.playerToken,
                    appVersion = session.appVersion,
                )
            )
            val body = response.body()

            when {
                !response.isSuccessful -> Result.failure(ApiException("Rematch failed (${response.code()})"))
                body == null -> Result.failure(ApiException("Empty server response"))
                !body.ok -> Result.failure(ApiException(body.error ?: "Rematch failed"))
                body.state == null -> Result.failure(ApiException("No state returned"))
                else -> Result.success(body.state)
            }
        }

    private fun handleGuessResponse(response: Response<SubmitGuessResponse>): Result<SubmitGuessResponse> {
        val body = response.body()

        return when {
            !response.isSuccessful -> Result.failure(ApiException("Submit guess failed (${response.code()})"))
            body == null -> Result.failure(ApiException("Empty server response"))
            !body.ok -> Result.failure(ApiException(body.error ?: "Submit guess failed"))
            body.state == null -> Result.failure(ApiException("No state returned"))
            else -> Result.success(body)
        }
    }

    private suspend fun <T> safeApiCall(block: suspend () -> Result<T>): Result<T> {
        return try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            Result.failure(normalizeApiThrowable(throwable))
        }
    }

    private fun normalizeApiThrowable(throwable: Throwable): ApiException {
        if (throwable is ApiException) {
            return throwable
        }

        val message = when (throwable) {
            is UnknownHostException -> "No internet connection."
            is SocketTimeoutException -> "Server timeout. Try again."
            is IOException -> "Network error. Please try again."
            else -> throwable.message ?: "Unexpected error. Please try again."
        }

        return ApiException(message)
    }
}

class ApiException(message: String) : Exception(message)
