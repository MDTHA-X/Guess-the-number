package com.iinaya.gtnonline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iinaya.gtnonline.data.ApiException
import com.iinaya.gtnonline.data.GameRepository
import com.iinaya.gtnonline.data.SessionStore
import com.iinaya.gtnonline.data.remote.GameState
import com.iinaya.gtnonline.data.remote.NetworkModule
import com.iinaya.gtnonline.data.remote.PlayerSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val SOLO_STATUS_ACTIVE = "active"
private const val SOLO_STATUS_FINISHED = "finished"

data class SoloMove(
    val attemptNo: Int,
    val guess: String,
    val matchCount: Int,
    val positionCount: Int,
    val scoreCode: String,
    val isCorrect: Boolean,
)

data class SoloGameState(
    val secret: String,
    val status: String = SOLO_STATUS_ACTIVE,
    val moves: List<SoloMove> = emptyList(),
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
)

data class GameUiState(
    val isLoading: Boolean = false,
    val createNameInput: String = "",
    val joinNameInput: String = "",
    val joinCodeInput: String = "",
    val secretInput: String = "",
    val guessInput: String = "",
    val soloGuessInput: String = "",
    val session: PlayerSession? = null,
    val gameState: GameState? = null,
    val soloGame: SoloGameState? = null,
    val soloResumeAvailable: Boolean = false,
    val mySecretCache: String? = null,
    val message: String? = null,
)

sealed interface GameUiEffect {
    data class PlaySound(val effect: SoundEffect) : GameUiEffect
}

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameRepository(NetworkModule.gameApiService)
    private val sessionStore = SessionStore(application.applicationContext)
    private val appVersion = BuildConfig.VERSION_NAME

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState

    private val _effects = MutableSharedFlow<GameUiEffect>()
    val effects: SharedFlow<GameUiEffect> = _effects

    private var pollingJob: Job? = null
    private var pendingSoloResume: SoloGameState? = null

    init {
        restoreStateIfAny()
    }

    fun onCreateNameChanged(value: String) {
        _uiState.update { it.copy(createNameInput = value) }
    }

    fun onJoinNameChanged(value: String) {
        _uiState.update { it.copy(joinNameInput = value) }
    }

    fun onJoinCodeChanged(value: String) {
        _uiState.update { it.copy(joinCodeInput = value.uppercase()) }
    }

    fun onSecretChanged(value: String) {
        _uiState.update { it.copy(secretInput = value.take(4)) }
    }

    fun onGuessChanged(value: String) {
        _uiState.update { it.copy(guessInput = value.take(4)) }
    }

    fun onSoloGuessChanged(value: String) {
        _uiState.update { it.copy(soloGuessInput = value.take(4)) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun onRoomCodeCopied() {
        setMessage("Room code copied.")
        emitSound(SoundEffect.SUCCESS)
    }

    fun createRoom() {
        val name = _uiState.value.createNameInput.trim().ifBlank { "Player 1" }

        viewModelScope.launch {
            setLoading(true)
            repository.createRoom(displayName = name, appVersion = appVersion)
                .onSuccess { session ->
                    attachSession(session)
                    setMessage("Room created: ${session.roomCode} (v${session.appVersion})")
                    emitSound(SoundEffect.SUCCESS)
                }
                .onFailure { throwable ->
                    setMessage(throwable.message ?: "Could not create room")
                    emitSound(SoundEffect.ERROR)
                }
            setLoading(false)
        }
    }

    fun joinRoom() {
        val name = _uiState.value.joinNameInput.trim()
        val roomCode = _uiState.value.joinCodeInput.trim().uppercase()

        if (name.isBlank()) {
            setMessage("Enter your name to join the room.")
            emitSound(SoundEffect.ERROR)
            return
        }

        if (!roomCode.matches(Regex("^[A-Z0-9]{6}$"))) {
            setMessage("Enter a valid 6 character room code.")
            emitSound(SoundEffect.ERROR)
            return
        }

        viewModelScope.launch {
            setLoading(true)
            repository.joinRoom(roomCode = roomCode, displayName = name, appVersion = appVersion)
                .onSuccess { session ->
                    attachSession(session)
                    setMessage("Joined room: ${session.roomCode} (v${session.appVersion})")
                    emitSound(SoundEffect.SUCCESS)
                }
                .onFailure { throwable ->
                    setMessage(throwable.message ?: "Could not join room")
                    emitSound(SoundEffect.ERROR)
                }
            setLoading(false)
        }
    }

    fun submitSecret() {
        val session = _uiState.value.session ?: return
        val secret = _uiState.value.secretInput.trim()

        if (!isValidUniqueNumber(secret)) {
            setMessage("Secret must be 4 unique digits from 1-9.")
            emitSound(SoundEffect.ERROR)
            return
        }

        viewModelScope.launch {
            setLoading(true)
            repository.submitSecret(session = session, secret = secret)
                .onSuccess { newState ->
                    sessionStore.saveMySecret(secret)
                    _uiState.update { it.copy(secretInput = "", mySecretCache = secret) }
                    applyGameState(newState)
                    setMessage("Secret submitted.")
                    emitSound(SoundEffect.SUCCESS)
                }
                .onFailure { throwable ->
                    setMessage(throwable.message ?: "Could not submit secret")
                    emitSound(SoundEffect.ERROR)
                }
            setLoading(false)
        }
    }

    fun submitGuess() {
        val snapshot = _uiState.value
        val session = snapshot.session ?: return
        val gameState = snapshot.gameState
        val guess = snapshot.guessInput.trim()

        if (gameState?.status != "active") {
            setMessage("Game is not active yet.")
            emitSound(SoundEffect.ERROR)
            return
        }

        if (!gameState.yourTurn) {
            setMessage("Wait for your turn.")
            emitSound(SoundEffect.ERROR)
            return
        }

        if (!isValidUniqueNumber(guess)) {
            setMessage("Guess must be 4 unique digits from 1-9.")
            emitSound(SoundEffect.ERROR)
            return
        }

        viewModelScope.launch {
            setLoading(true)
            repository.submitGuess(session = session, guess = guess)
                .onSuccess { response ->
                    _uiState.update { it.copy(guessInput = "") }
                    response.state?.let(::applyGameState)
                    response.score?.let { setMessage("Score: $it") }
                    emitSound(if (response.isCorrect) SoundEffect.SUCCESS else SoundEffect.OPPONENT_TURN)
                }
                .onFailure { throwable ->
                    setMessage(throwable.message ?: "Could not submit guess")
                    emitSound(SoundEffect.ERROR)
                }
            setLoading(false)
        }
    }

    fun rematch() {
        val snapshot = _uiState.value
        val session = snapshot.session ?: return
        val state = snapshot.gameState ?: return

        if (state.status != "finished") {
            setMessage("Rematch is available after game end.")
            emitSound(SoundEffect.ERROR)
            return
        }

        viewModelScope.launch {
            setLoading(true)
            repository.rematch(session)
                .onSuccess { newState ->
                    sessionStore.clearMySecret()
                    _uiState.update {
                        it.copy(
                            guessInput = "",
                            secretInput = "",
                            mySecretCache = null,
                        )
                    }
                    applyGameState(newState)
                    setMessage("Rematch started. Set your new secret.")
                    emitSound(SoundEffect.SUCCESS)
                }
                .onFailure { throwable ->
                    if (throwable is ApiException && throwable.message?.contains("(404)") == true) {
                        handleRematchMissingEndpoint(session)
                    } else {
                        setMessage(throwable.message ?: "Rematch failed")
                        emitSound(SoundEffect.ERROR)
                    }
                }
            setLoading(false)
        }
    }

    fun leaveRoom() {
        val session = _uiState.value.session

        viewModelScope.launch {
            session?.let { repository.leaveRoom(it) }
            sessionStore.clearOnlineSession()
            stopPolling()
            _uiState.update {
                it.copy(
                    session = null,
                    gameState = null,
                    secretInput = "",
                    guessInput = "",
                    mySecretCache = null,
                )
            }
            setMessage("You left the room.")
            emitSound(SoundEffect.ERROR)
        }
    }

    fun refreshState() {
        val session = _uiState.value.session ?: return

        viewModelScope.launch {
            repository.fetchState(session)
                .onSuccess { applyGameState(it) }
                .onFailure {
                    setMessage(it.message ?: "Could not refresh state")
                    emitSound(SoundEffect.ERROR)
                }
        }
    }

    fun startNewSoloGame() {
        val game = SoloGameState(secret = generateUniqueNumber())
        pendingSoloResume = game

        _uiState.update {
            it.copy(
                soloGame = game,
                soloGuessInput = "",
                soloResumeAvailable = false,
            )
        }

        viewModelScope.launch {
            sessionStore.saveSoloGame(game.toSnapshot())
        }

        setMessage("Solo game started. Guess the secret number.")
        emitSound(SoundEffect.YOUR_TURN)
    }

    fun resumeSoloGame() {
        val game = pendingSoloResume
        if (game == null || game.status != SOLO_STATUS_ACTIVE) {
            setMessage("No unfinished solo game found.")
            emitSound(SoundEffect.ERROR)
            return
        }

        _uiState.update {
            it.copy(
                soloGame = game,
                soloGuessInput = "",
                soloResumeAvailable = false,
            )
        }

        setMessage("Resumed unfinished solo game.")
        emitSound(SoundEffect.SUCCESS)
    }

    fun leaveSoloMode() {
        val activeSolo = _uiState.value.soloGame ?: return

        viewModelScope.launch {
            if (activeSolo.status == SOLO_STATUS_ACTIVE) {
                sessionStore.saveSoloGame(activeSolo.toSnapshot())
                pendingSoloResume = activeSolo
                _uiState.update {
                    it.copy(
                        soloGame = null,
                        soloGuessInput = "",
                        soloResumeAvailable = true,
                    )
                }
                setMessage("Solo game saved. Resume anytime from lobby.")
            } else {
                sessionStore.clearSoloGame()
                pendingSoloResume = null
                _uiState.update {
                    it.copy(
                        soloGame = null,
                        soloGuessInput = "",
                        soloResumeAvailable = false,
                    )
                }
                setMessage("Solo game closed.")
            }
            emitSound(SoundEffect.SUCCESS)
        }
    }

    fun submitSoloGuess() {
        val snapshot = _uiState.value
        val soloGame = snapshot.soloGame ?: return
        val guess = snapshot.soloGuessInput.trim()

        if (soloGame.status != SOLO_STATUS_ACTIVE) {
            setMessage("Solo game is finished. Start a new game.")
            emitSound(SoundEffect.ERROR)
            return
        }

        if (!isValidUniqueNumber(guess)) {
            setMessage("Guess must be 4 unique digits from 1-9.")
            emitSound(SoundEffect.ERROR)
            return
        }

        val (matchCount, positionCount) = scoreGuess(secret = soloGame.secret, guess = guess)
        val isCorrect = positionCount == 4
        val nextAttempt = soloGame.moves.size + 1
        val scoreCode = "$matchCount-$positionCount"
        val move = SoloMove(
            attemptNo = nextAttempt,
            guess = guess,
            matchCount = matchCount,
            positionCount = positionCount,
            scoreCode = scoreCode,
            isCorrect = isCorrect,
        )

        val updatedGame = if (isCorrect) {
            soloGame.copy(
                status = SOLO_STATUS_FINISHED,
                moves = soloGame.moves + move,
                finishedAt = System.currentTimeMillis(),
            )
        } else {
            soloGame.copy(moves = soloGame.moves + move)
        }

        _uiState.update {
            it.copy(
                soloGame = updatedGame,
                soloGuessInput = "",
                soloResumeAvailable = false,
            )
        }

        viewModelScope.launch {
            if (isCorrect) {
                sessionStore.clearSoloGame()
                pendingSoloResume = null
            } else {
                sessionStore.saveSoloGame(updatedGame.toSnapshot())
                pendingSoloResume = updatedGame
            }
        }

        if (isCorrect) {
            setMessage("You solved it in ${updatedGame.moves.size} attempts. Secret: ${updatedGame.secret}")
            emitSound(SoundEffect.WIN)
        } else {
            setMessage("Score: $scoreCode")
            emitSound(SoundEffect.OPPONENT_TURN)
        }
    }

    private fun restoreStateIfAny() {
        viewModelScope.launch {
            val snapshot = sessionStore.snapshotFlow.first()
            val savedSolo = snapshot.soloGame
                ?.toSoloGameState()
                ?.takeIf { it.status == SOLO_STATUS_ACTIVE }
            pendingSoloResume = savedSolo

            _uiState.update {
                it.copy(soloResumeAvailable = savedSolo != null)
            }

            val savedSession = snapshot.session
            if (savedSession != null) {
                _uiState.update {
                    it.copy(
                        session = savedSession,
                        joinCodeInput = savedSession.roomCode,
                        mySecretCache = snapshot.mySecret,
                        isLoading = true,
                    )
                }

                repository.fetchState(savedSession)
                    .onSuccess { applyGameState(it) }
                    .onFailure {
                        sessionStore.clearOnlineSession()
                        _uiState.update { state ->
                            state.copy(
                                session = null,
                                gameState = null,
                                mySecretCache = null,
                                message = "Previous session expired. Please create/join again.",
                            )
                        }
                    }

                _uiState.update { it.copy(isLoading = false) }
                if (_uiState.value.session != null) {
                    startPolling()
                }
            }
        }
    }

    private fun attachSession(session: PlayerSession) {
        stopPolling()
        _uiState.update {
            it.copy(
                session = session,
                joinCodeInput = session.roomCode,
                gameState = null,
                guessInput = "",
                secretInput = "",
                mySecretCache = null,
                soloGame = null,
                soloGuessInput = "",
            )
        }

        viewModelScope.launch {
            sessionStore.saveSession(session)
            sessionStore.clearMySecret()
            repository.fetchState(session)
                .onSuccess { applyGameState(it) }
                .onFailure {
                    setMessage(it.message ?: "Could not sync room state")
                    emitSound(SoundEffect.ERROR)
                }
        }

        startPolling()
    }

    private fun startPolling() {
        stopPolling()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                val session = _uiState.value.session ?: break
                repository.fetchState(session)
                    .onSuccess { applyGameState(it) }
                    .onFailure {
                        // Silent retry loop
                    }

                val nextDelay = if (_uiState.value.gameState?.status == "active") 1500L else 2500L
                delay(nextDelay)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun applyGameState(newState: GameState) {
        val oldState = _uiState.value.gameState
        val cached = _uiState.value.mySecretCache

        val secretFromServer = newState.mySecretValue?.takeIf { it.isNotBlank() }
        val effectiveSecret = secretFromServer ?: cached

        if (!secretFromServer.isNullOrBlank() && secretFromServer != cached) {
            viewModelScope.launch {
                sessionStore.saveMySecret(secretFromServer)
            }
        }

        _uiState.update {
            it.copy(
                gameState = newState,
                mySecretCache = effectiveSecret,
            )
        }

        val enteredFinished = oldState?.status != "finished" && newState.status == "finished"
        if (enteredFinished) {
            when (newState.winner) {
                "you" -> {
                    emitSound(SoundEffect.WIN)
                    setMessage("Victory! You won the match.")
                }

                "opponent" -> {
                    emitSound(SoundEffect.LOSE)
                    setMessage("Defeat! Opponent won this match.")
                }

                "draw" -> {
                    emitSound(SoundEffect.DRAW)
                    setMessage("Draw! Both solved in same attempt.")
                }
            }
        }

        val becameMyTurn = (oldState?.yourTurn == false) && newState.yourTurn && newState.status == "active"
        if (becameMyTurn) {
            emitSound(SoundEffect.YOUR_TURN)
        }
    }

    private fun isValidUniqueNumber(value: String): Boolean {
        if (!value.matches(Regex("^[1-9]{4}$"))) {
            return false
        }
        return value.toSet().size == 4
    }

    private fun scoreGuess(secret: String, guess: String): Pair<Int, Int> {
        var positionCount = 0
        for (index in 0 until 4) {
            if (secret[index] == guess[index]) {
                positionCount++
            }
        }
        val matchCount = guess.count { secret.contains(it) }
        return matchCount to positionCount
    }

    private fun generateUniqueNumber(): String {
        return ('1'..'9').shuffled().take(4).joinToString("")
    }

    private fun SoloGameState.toSnapshot(): SessionStore.SoloGameSnapshot {
        return SessionStore.SoloGameSnapshot(
            secret = secret,
            status = status,
            moves = moves.map {
                SessionStore.SoloMoveSnapshot(
                    attemptNo = it.attemptNo,
                    guess = it.guess,
                    matchCount = it.matchCount,
                    positionCount = it.positionCount,
                    scoreCode = it.scoreCode,
                    isCorrect = it.isCorrect,
                )
            },
            startedAt = startedAt,
            finishedAt = finishedAt,
        )
    }

    private fun SessionStore.SoloGameSnapshot.toSoloGameState(): SoloGameState {
        return SoloGameState(
            secret = secret,
            status = status,
            moves = moves.map {
                SoloMove(
                    attemptNo = it.attemptNo,
                    guess = it.guess,
                    matchCount = it.matchCount,
                    positionCount = it.positionCount,
                    scoreCode = it.scoreCode,
                    isCorrect = it.isCorrect,
                )
            },
            startedAt = startedAt,
            finishedAt = finishedAt,
        )
    }

    private suspend fun handleRematchMissingEndpoint(session: PlayerSession) {
        if (session.role == "host") {
            repository.createRoom(displayName = session.displayName, appVersion = appVersion)
                .onSuccess { newSession ->
                    attachSession(newSession)
                    setMessage("Rematch API missing on server. New room: ${newSession.roomCode}. Share it with opponent.")
                    emitSound(SoundEffect.SUCCESS)
                }
                .onFailure { throwable ->
                    setMessage(throwable.message ?: "Rematch endpoint missing. Could not auto-create a new room.")
                    emitSound(SoundEffect.ERROR)
                }
        } else {
            setMessage("Rematch API is missing on server. Ask host to create a new room and share the code.")
            emitSound(SoundEffect.ERROR)
        }
    }

    private fun setLoading(isLoading: Boolean) {
        _uiState.update { it.copy(isLoading = isLoading) }
    }

    private fun setMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    private fun emitSound(effect: SoundEffect) {
        viewModelScope.launch {
            _effects.emit(GameUiEffect.PlaySound(effect))
        }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }
}

@Suppress("UNCHECKED_CAST")
class GameViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            return GameViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
