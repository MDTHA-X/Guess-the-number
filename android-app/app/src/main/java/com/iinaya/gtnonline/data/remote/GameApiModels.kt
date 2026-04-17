package com.iinaya.gtnonline.data.remote

import com.google.gson.annotations.SerializedName

data class CreateRoomRequest(
    @SerializedName("displayName") val displayName: String,
    @SerializedName("appVersion") val appVersion: String,
)

data class JoinRoomRequest(
    @SerializedName("roomCode") val roomCode: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("appVersion") val appVersion: String,
)

data class SubmitSecretRequest(
    @SerializedName("roomCode") val roomCode: String,
    @SerializedName("playerToken") val playerToken: String,
    @SerializedName("secret") val secret: String,
    @SerializedName("appVersion") val appVersion: String,
)

data class SubmitGuessRequest(
    @SerializedName("roomCode") val roomCode: String,
    @SerializedName("playerToken") val playerToken: String,
    @SerializedName("guess") val guess: String,
    @SerializedName("appVersion") val appVersion: String,
)

data class LeaveRoomRequest(
    @SerializedName("roomCode") val roomCode: String,
    @SerializedName("playerToken") val playerToken: String,
    @SerializedName("appVersion") val appVersion: String,
)

data class RematchRequest(
    @SerializedName("roomCode") val roomCode: String,
    @SerializedName("playerToken") val playerToken: String,
    @SerializedName("appVersion") val appVersion: String,
)

data class SessionResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("error") val error: String? = null,
    @SerializedName("roomCode") val roomCode: String? = null,
    @SerializedName("playerToken") val playerToken: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("appVersion") val appVersion: String? = null,
)

data class MoveItem(
    @SerializedName("turn_no") val turnNo: String? = null,
    @SerializedName("player_attempt_no") val playerAttemptNo: String? = null,
    @SerializedName("guess_value") val guessValue: String? = null,
    @SerializedName("match_count") val matchCount: String? = null,
    @SerializedName("position_count") val positionCount: String? = null,
    @SerializedName("score_code") val scoreCode: String? = null,
    @SerializedName("is_correct") val isCorrect: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("role") val role: String? = null,
)

data class GameState(
    @SerializedName("roomCode") val roomCode: String? = null,
    @SerializedName("appVersion") val appVersion: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("finishReason") val finishReason: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("yourTurn") val yourTurn: Boolean = false,
    @SerializedName("turnPlayerId") val turnPlayerId: Int? = null,
    @SerializedName("myAttempts") val myAttempts: Int = 0,
    @SerializedName("opponentAttempts") val opponentAttempts: Int = 0,
    @SerializedName("mySecretSubmitted") val mySecretSubmitted: Boolean = false,
    @SerializedName("mySecretValue") val mySecretValue: String? = null,
    @SerializedName("opponentSecretSubmitted") val opponentSecretSubmitted: Boolean = false,
    @SerializedName("opponentSecretValue") val opponentSecretValue: String? = null,
    @SerializedName("winner") val winner: String? = null,
    @SerializedName("isDraw") val isDraw: Boolean = false,
    @SerializedName("moves") val moves: List<MoveItem> = emptyList(),
    @SerializedName("updatedAt") val updatedAt: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
)

data class StateResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("error") val error: String? = null,
    @SerializedName("state") val state: GameState? = null,
)

data class SubmitSecretResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("error") val error: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("gameStarted") val gameStarted: Boolean = false,
    @SerializedName("state") val state: GameState? = null,
)

data class SubmitGuessResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("error") val error: String? = null,
    @SerializedName("score") val score: String? = null,
    @SerializedName("matchCount") val matchCount: Int? = null,
    @SerializedName("positionCount") val positionCount: Int? = null,
    @SerializedName("isCorrect") val isCorrect: Boolean = false,
    @SerializedName("state") val state: GameState? = null,
)

data class SimpleResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("error") val error: String? = null,
)

data class RematchResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("error") val error: String? = null,
    @SerializedName("state") val state: GameState? = null,
)


data class PlayerSession(
    val roomCode: String,
    val playerToken: String,
    val role: String,
    val displayName: String,
    val appVersion: String,
)
