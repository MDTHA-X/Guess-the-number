package com.iinaya.gtnonline.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface GameApiService {
    @POST("api/game/create_room.php")
    suspend fun createRoom(@Body request: CreateRoomRequest): Response<SessionResponse>

    @POST("api/game/join_room.php")
    suspend fun joinRoom(@Body request: JoinRoomRequest): Response<SessionResponse>

    @POST("api/game/submit_secret.php")
    suspend fun submitSecret(@Body request: SubmitSecretRequest): Response<SubmitSecretResponse>

    @POST("api/game/submit_guess.php")
    suspend fun submitGuess(@Body request: SubmitGuessRequest): Response<SubmitGuessResponse>

    @GET("api/game/state.php")
    suspend fun getState(
        @Query("roomCode") roomCode: String,
        @Query("playerToken") playerToken: String,
        @Query("appVersion") appVersion: String,
    ): Response<StateResponse>

    @POST("api/game/leave_room.php")
    suspend fun leaveRoom(@Body request: LeaveRoomRequest): Response<SimpleResponse>

    @POST("api/game/rematch.php")
    suspend fun rematch(@Body request: RematchRequest): Response<RematchResponse>
}
