package com.iinaya.gtnonline.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.iinaya.gtnonline.data.remote.PlayerSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "gtn_session")

class SessionStore(private val context: Context) {
    data class SoloMoveSnapshot(
        val attemptNo: Int,
        val guess: String,
        val matchCount: Int,
        val positionCount: Int,
        val scoreCode: String,
        val isCorrect: Boolean,
    )

    data class SoloGameSnapshot(
        val secret: String,
        val status: String,
        val moves: List<SoloMoveSnapshot>,
        val startedAt: Long,
        val finishedAt: Long?,
    )

    data class SessionSnapshot(
        val session: PlayerSession?,
        val mySecret: String?,
        val soloGame: SoloGameSnapshot?,
    )

    private object Keys {
        val roomCode: Preferences.Key<String> = stringPreferencesKey("room_code")
        val playerToken: Preferences.Key<String> = stringPreferencesKey("player_token")
        val role: Preferences.Key<String> = stringPreferencesKey("role")
        val displayName: Preferences.Key<String> = stringPreferencesKey("display_name")
        val appVersion: Preferences.Key<String> = stringPreferencesKey("app_version")
        val mySecret: Preferences.Key<String> = stringPreferencesKey("my_secret")
        val soloGame: Preferences.Key<String> = stringPreferencesKey("solo_game")
    }

    private val gson = Gson()

    val sessionFlow: Flow<PlayerSession?> = context.sessionDataStore.data.map { pref ->
        val roomCode = pref[Keys.roomCode]
        val token = pref[Keys.playerToken]
        val role = pref[Keys.role]
        val displayName = pref[Keys.displayName]
        val appVersion = pref[Keys.appVersion]

        if (roomCode.isNullOrBlank()
            || token.isNullOrBlank()
            || role.isNullOrBlank()
            || displayName.isNullOrBlank()
            || appVersion.isNullOrBlank()
        ) {
            null
        } else {
            PlayerSession(
                roomCode = roomCode,
                playerToken = token,
                role = role,
                displayName = displayName,
                appVersion = appVersion,
            )
        }
    }

    val snapshotFlow: Flow<SessionSnapshot> = context.sessionDataStore.data.map { pref ->
        val roomCode = pref[Keys.roomCode]
        val token = pref[Keys.playerToken]
        val role = pref[Keys.role]
        val displayName = pref[Keys.displayName]
        val appVersion = pref[Keys.appVersion]
        val secret = pref[Keys.mySecret]
        val soloGame = decodeSoloGame(pref[Keys.soloGame])

        val session = if (roomCode.isNullOrBlank()
            || token.isNullOrBlank()
            || role.isNullOrBlank()
            || displayName.isNullOrBlank()
            || appVersion.isNullOrBlank()
        ) {
            null
        } else {
            PlayerSession(
                roomCode = roomCode,
                playerToken = token,
                role = role,
                displayName = displayName,
                appVersion = appVersion,
            )
        }

        SessionSnapshot(
            session = session,
            mySecret = secret,
            soloGame = soloGame,
        )
    }

    suspend fun saveSession(session: PlayerSession) {
        context.sessionDataStore.edit { pref ->
            pref[Keys.roomCode] = session.roomCode
            pref[Keys.playerToken] = session.playerToken
            pref[Keys.role] = session.role
            pref[Keys.displayName] = session.displayName
            pref[Keys.appVersion] = session.appVersion
        }
    }

    suspend fun saveSoloGame(snapshot: SoloGameSnapshot) {
        context.sessionDataStore.edit { pref ->
            pref[Keys.soloGame] = gson.toJson(snapshot)
        }
    }

    suspend fun clearSoloGame() {
        context.sessionDataStore.edit { pref ->
            pref.remove(Keys.soloGame)
        }
    }

    suspend fun saveMySecret(secret: String) {
        context.sessionDataStore.edit { pref ->
            pref[Keys.mySecret] = secret
        }
    }

    suspend fun clearMySecret() {
        context.sessionDataStore.edit { pref ->
            pref.remove(Keys.mySecret)
        }
    }

    suspend fun clear() {
        clearOnlineSession()
        clearSoloGame()
    }

    suspend fun clearOnlineSession() {
        context.sessionDataStore.edit { pref ->
            pref.remove(Keys.roomCode)
            pref.remove(Keys.playerToken)
            pref.remove(Keys.role)
            pref.remove(Keys.displayName)
            pref.remove(Keys.appVersion)
            pref.remove(Keys.mySecret)
        }
    }

    private fun decodeSoloGame(raw: String?): SoloGameSnapshot? {
        if (raw.isNullOrBlank()) {
            return null
        }

        return try {
            gson.fromJson(raw, SoloGameSnapshot::class.java)
        } catch (_: Throwable) {
            null
        }
    }
}
