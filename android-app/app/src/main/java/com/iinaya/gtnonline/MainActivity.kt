package com.iinaya.gtnonline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iinaya.gtnonline.data.remote.GameState
import com.iinaya.gtnonline.data.remote.MoveItem
import com.iinaya.gtnonline.ui.theme.DeepBlue
import com.iinaya.gtnonline.ui.theme.GuessTheNumberOnlineTheme
import com.iinaya.gtnonline.ui.theme.LightSurface
import com.iinaya.gtnonline.ui.theme.Mint
import com.iinaya.gtnonline.ui.theme.Ocean
import com.iinaya.gtnonline.ui.theme.PanelDark
import com.iinaya.gtnonline.ui.theme.SkyBlue
import com.iinaya.gtnonline.ui.theme.SoftGray
import com.iinaya.gtnonline.ui.theme.Tangerine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GuessTheNumberOnlineTheme {
                val vm: GameViewModel = viewModel(factory = GameViewModelFactory(application))
                val uiState by vm.uiState.collectAsState()
                val snackbarHost = remember { SnackbarHostState() }
                val clipboardManager: ClipboardManager = LocalClipboardManager.current
                val sounds = remember { GameSoundPlayer() }
                var guideVisible by rememberSaveable { mutableStateOf(false) }

                DisposableEffect(Unit) {
                    onDispose { sounds.release() }
                }

                LaunchedEffect(Unit) {
                    vm.effects.collect { effect ->
                        when (effect) {
                            is GameUiEffect.PlaySound -> sounds.play(effect.effect)
                        }
                    }
                }

                LaunchedEffect(uiState.message) {
                    val message = uiState.message ?: return@LaunchedEffect
                    snackbarHost.showSnackbar(message = message, duration = SnackbarDuration.Short)
                    vm.clearMessage()
                }

                GameRoot(
                    uiState = uiState,
                    guideVisible = guideVisible,
                    snackbarHost = snackbarHost,
                    onCreateNameChanged = vm::onCreateNameChanged,
                    onJoinNameChanged = vm::onJoinNameChanged,
                    onJoinCodeChanged = vm::onJoinCodeChanged,
                    onSecretChanged = vm::onSecretChanged,
                    onGuessChanged = vm::onGuessChanged,
                    onSoloGuessChanged = vm::onSoloGuessChanged,
                    onCreateRoom = vm::createRoom,
                    onJoinRoom = vm::joinRoom,
                    onSubmitSecret = vm::submitSecret,
                    onSubmitGuess = vm::submitGuess,
                    onStartNewSoloGame = vm::startNewSoloGame,
                    onResumeSoloGame = vm::resumeSoloGame,
                    onLeaveSoloMode = vm::leaveSoloMode,
                    onSubmitSoloGuess = vm::submitSoloGuess,
                    onRefresh = vm::refreshState,
                    onLeaveRoom = vm::leaveRoom,
                    onRematch = vm::rematch,
                    onCopyRoomCode = { code ->
                        clipboardManager.setText(AnnotatedString(code))
                        vm.onRoomCodeCopied()
                    },
                    onOpenGuide = { guideVisible = true },
                    onCloseGuide = { guideVisible = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameRoot(
    uiState: GameUiState,
    guideVisible: Boolean,
    snackbarHost: SnackbarHostState,
    onCreateNameChanged: (String) -> Unit,
    onJoinNameChanged: (String) -> Unit,
    onJoinCodeChanged: (String) -> Unit,
    onSecretChanged: (String) -> Unit,
    onGuessChanged: (String) -> Unit,
    onSoloGuessChanged: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onSubmitSecret: () -> Unit,
    onSubmitGuess: () -> Unit,
    onStartNewSoloGame: () -> Unit,
    onResumeSoloGame: () -> Unit,
    onLeaveSoloMode: () -> Unit,
    onSubmitSoloGuess: () -> Unit,
    onRefresh: () -> Unit,
    onLeaveRoom: () -> Unit,
    onRematch: () -> Unit,
    onCopyRoomCode: (String) -> Unit,
    onOpenGuide: () -> Unit,
    onCloseGuide: () -> Unit,
) {
    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            SkyBlue.copy(alpha = 0.40f),
            LightSurface,
            Mint.copy(alpha = 0.22f),
        )
    )
    val showResultPopup = !guideVisible && uiState.session != null && uiState.gameState?.status == "finished"

    BackHandler(enabled = guideVisible || uiState.session != null || uiState.soloGame != null) {
        if (uiState.session != null) {
            onCloseGuide()
            onLeaveRoom()
        } else if (uiState.soloGame != null) {
            onCloseGuide()
            onLeaveSoloMode()
        } else {
            onCloseGuide()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .padding(paddingValues)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Header(
                    onOpenGuide = onOpenGuide,
                    appVersion = BuildConfig.VERSION_NAME,
                )

                if (guideVisible) {
                    GuideAndAboutScreen(onCloseGuide = onCloseGuide)
                } else if (uiState.soloGame != null) {
                    SoloScreen(
                        uiState = uiState,
                        onSoloGuessChanged = onSoloGuessChanged,
                        onSubmitSoloGuess = onSubmitSoloGuess,
                        onStartNewSoloGame = onStartNewSoloGame,
                        onLeaveSoloMode = onLeaveSoloMode,
                    )
                } else if (uiState.session == null) {
                    LobbyScreen(
                        uiState = uiState,
                        onCreateNameChanged = onCreateNameChanged,
                        onJoinNameChanged = onJoinNameChanged,
                        onJoinCodeChanged = onJoinCodeChanged,
                        onCreateRoom = onCreateRoom,
                        onJoinRoom = onJoinRoom,
                        onStartNewSoloGame = onStartNewSoloGame,
                        onResumeSoloGame = onResumeSoloGame,
                    )
                } else {
                    RoomScreen(
                        uiState = uiState,
                        playerName = uiState.session.displayName,
                        onSecretChanged = onSecretChanged,
                        onGuessChanged = onGuessChanged,
                        onSubmitSecret = onSubmitSecret,
                        onSubmitGuess = onSubmitGuess,
                        onRefresh = onRefresh,
                        onLeaveRoom = onLeaveRoom,
                        onCopyRoomCode = onCopyRoomCode,
                    )
                }
            }

            if (showResultPopup) {
                ResultPopupOverlay(
                    winner = uiState.gameState?.winner,
                    finishReason = uiState.gameState?.finishReason,
                    opponentSecret = uiState.gameState?.opponentSecretValue,
                    onRematch = onRematch,
                    onExit = onLeaveRoom,
                    isLoading = uiState.isLoading,
                )
            }

            AnimatedVisibility(
                visible = uiState.isLoading,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Header(onOpenGuide: () -> Unit, appVersion: String) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = DeepBlue.copy(alpha = 0.95f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                GameLogoGlyph(modifier = Modifier.size(30.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Guess The Number",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Solo + online duel • Match version v$appVersion",
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            IconButton(onClick = onOpenGuide) {
                Icon(Icons.Default.Info, contentDescription = "Guide", tint = Color.White)
            }
        }
    }
}

@Composable
private fun GameLogoGlyph(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DeepBlue),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Dot(Tangerine)
            Dot(Color(0xFF7C3AED))
            Dot(Mint)
            Dot(SkyBlue)
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            CodeSlot(Color.White.copy(alpha = 0.9f))
            CodeSlot(Color.White.copy(alpha = 0.9f))
            CodeSlot(Tangerine.copy(alpha = 0.9f))
            CodeSlot(Color.White.copy(alpha = 0.9f))
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(9.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(DeepBlue),
            )
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}

@Composable
private fun CodeSlot(color: Color) {
    Box(
        modifier = Modifier
            .size(width = 5.dp, height = 5.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(color),
    )
}

@Composable
private fun GuideAndAboutScreen(onCloseGuide: () -> Unit) {
    var banglaMode by rememberSaveable { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (banglaMode) "খেলার পূর্ণ গাইড" else "Complete Game Guide",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (banglaMode) {
                            "নিচ থেকে ভাষা বেছে নিয়ে নিয়মগুলো দেখো।"
                        } else {
                            "Switch language below and follow these rules step by step."
                        },
                        color = DeepBlue.copy(alpha = 0.72f),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        if (!banglaMode) {
                            Button(onClick = { banglaMode = false }, modifier = Modifier.weight(1f)) { Text("English") }
                            OutlinedButton(onClick = { banglaMode = true }, modifier = Modifier.weight(1f)) { Text("বাংলা") }
                        } else {
                            OutlinedButton(onClick = { banglaMode = false }, modifier = Modifier.weight(1f)) { Text("English") }
                            Button(onClick = { banglaMode = true }, modifier = Modifier.weight(1f)) { Text("বাংলা") }
                        }
                    }

                    HorizontalDivider()

                    if (banglaMode) {
                        GuideStepCard(
                            title = "১) রুম তৈরি ও জয়েন",
                            body = "হোস্ট রুম বানিয়ে ৬ অক্ষরের রুম কোড শেয়ার করবে। অন্য খেলোয়াড় নিজের নাম + কোড দিয়ে জয়েন করবে।",
                        )
                        GuideStepCard(
                            title = "২) সিক্রেট নাম্বার সেট",
                            body = "দুজনই ৪ অংকের গোপন সংখ্যা দেবে। নিয়ম: শুধু ১-৯, শূন্য নয়, এবং কোনো ডিজিট রিপিট নয়।",
                        )
                        GuideStepCard(
                            title = "৩) পালা করে গেস",
                            body = "একজন গেস দিলে প্রতিপক্ষ শুধু স্কোর দেবে। স্কোর ফরম্যাট x-y: x = মোট মিল, y = সঠিক পজিশনে মিল।",
                        )
                        GuideStepCard(
                            title = "৪) স্কোর উদাহরণ",
                            body = "সিক্রেট 4567, গেস 1234 হলে স্কোর 1-0। সিক্রেট 9874, গেস 1234 হলে স্কোর 1-1।",
                        )
                        GuideStepCard(
                            title = "৫) জয়/ড্র নিয়ম",
                            body = "কেউ n-তম টার্নে সঠিক বললে অন্যজনও একই n-তম একটি শেষ সুযোগ পাবে। দুজনই n-তম টার্নে মিলালে ড্র।",
                        )
                        GuideStepCard(
                            title = "৬) গেম শেষে",
                            body = "ফলাফল স্ক্রিনে Winner/Draw দেখাবে। নিচে Rematch দিয়ে আবার খেলতে পারবে, Exit দিয়ে বের হতে পারবে।",
                        )
                    } else {
                        GuideStepCard(
                            title = "1) Create + Join Room",
                            body = "Host creates a room and shares the 6-character code. Opponent joins with their player name + room code.",
                        )
                        GuideStepCard(
                            title = "2) Set Secret Number",
                            body = "Each player submits a 4-digit secret. Rules: digits 1-9 only, no zero, no repeated digits.",
                        )
                        GuideStepCard(
                            title = "3) Take Turns Guessing",
                            body = "Players guess alternately. Response format is x-y where x = matched digits and y = correct positions.",
                        )
                        GuideStepCard(
                            title = "4) Scoring Examples",
                            body = "Secret 4567 vs guess 1234 gives 1-0. Secret 9874 vs guess 1234 gives 1-1.",
                        )
                        GuideStepCard(
                            title = "5) Fair Win Rule",
                            body = "If one player solves on move n, the other player still gets one final nth move. Same nth solve by both = draw.",
                        )
                        GuideStepCard(
                            title = "6) After Match",
                            body = "Result panel shows Winner/Draw, with Rematch and Exit options at the bottom of the game room.",
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("About Creators", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "The game is crafted by these creators.",
                        color = DeepBlue.copy(alpha = 0.72f),
                    )
                    CreatorProfileCard(
                        name = "MD Tanjim Hossen Ahad",
                        githubUrl = "https://github.com/MDTHA-X",
                        accent = SkyBlue,
                    )
                    CreatorProfileCard(
                        name = "Khadiza Akter",
                        githubUrl = "https://github.com/khadiza-x",
                        accent = Mint,
                    )
                    HorizontalDivider()
                    Button(onClick = onCloseGuide, modifier = Modifier.fillMaxWidth()) {
                        Text("Back To Game")
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideStepCard(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SkyBlue.copy(alpha = 0.12f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = DeepBlue,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = DeepBlue.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun CreatorProfileCard(
    name: String,
    githubUrl: String,
    accent: Color,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(accent),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = DeepBlue,
                )
            }

            Text(
                text = "GitHub",
                style = MaterialTheme.typography.labelLarge,
                color = DeepBlue.copy(alpha = 0.68f),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = githubUrl,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = DeepBlue.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
private fun LobbyScreen(
    uiState: GameUiState,
    onCreateNameChanged: (String) -> Unit,
    onJoinNameChanged: (String) -> Unit,
    onJoinCodeChanged: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onStartNewSoloGame: () -> Unit,
    onResumeSoloGame: () -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Create Room", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = uiState.createNameInput,
                        onValueChange = onCreateNameChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Your name (host)") },
                        singleLine = true,
                        colors = readableTextFieldColors(),
                    )
                    ElevatedButton(onClick = onCreateRoom, modifier = Modifier.fillMaxWidth(), enabled = !uiState.isLoading) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Room")
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Join Room", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = uiState.joinNameInput,
                        onValueChange = onJoinNameChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Your name (join)") },
                        singleLine = true,
                        colors = readableTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = uiState.joinCodeInput,
                        onValueChange = onJoinCodeChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Room code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            capitalization = KeyboardCapitalization.Characters,
                        ),
                        colors = readableTextFieldColors(),
                    )
                    OutlinedButton(onClick = onJoinRoom, modifier = Modifier.fillMaxWidth(), enabled = !uiState.isLoading) {
                        Icon(Icons.Default.Groups, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Join By Code")
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Mint.copy(alpha = 0.12f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Solo Mode (Offline)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Play against a random secret number on this device. Unfinished games are saved and can be resumed.",
                        color = DeepBlue.copy(alpha = 0.75f),
                    )
                    if (uiState.soloResumeAvailable) {
                        Button(
                            onClick = onResumeSoloGame,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                        ) {
                            Text("Resume Solo Game")
                        }
                    }
                    OutlinedButton(onClick = onStartNewSoloGame, modifier = Modifier.fillMaxWidth()) {
                        Text("Start New Solo Game")
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PanelDark),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quick Rules", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("• 4 digits only", color = Color.White)
                    Text("• Digits must be 1-9", color = Color.White)
                    Text("• No repeated digits", color = Color.White)
                    Text("• Same attempt solve by both = draw", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SoloScreen(
    uiState: GameUiState,
    onSoloGuessChanged: (String) -> Unit,
    onSubmitSoloGuess: () -> Unit,
    onStartNewSoloGame: () -> Unit,
    onLeaveSoloMode: () -> Unit,
) {
    val soloGame = uiState.soloGame ?: return

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                Box(modifier = Modifier.background(Brush.horizontalGradient(listOf(DeepBlue, Ocean, Tangerine)))) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Solo Mode", color = Color.White.copy(alpha = 0.85f))
                                Text(
                                    "Offline Practice",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                            }
                            IconActionButton(icon = Icons.Default.Refresh, onClick = onStartNewSoloGame, tint = Color.White)
                            IconActionButton(icon = Icons.AutoMirrored.Filled.ExitToApp, onClick = onLeaveSoloMode, tint = Color.White)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip(label = "Status: ${soloGame.status}")
                            StatusChip(label = "Version: v${BuildConfig.VERSION_NAME}")
                            StatusChip(label = "Attempts: ${soloGame.moves.size}")
                        }
                    }
                }
            }
        }

        item {
            ActionCard(
                title = "Your Guess",
                subtitle = if (soloGame.status == "active") {
                    "Guess the 4-digit random number."
                } else {
                    "Finished. Secret number revealed below."
                },
                accent = if (soloGame.status == "active") Mint else Tangerine,
            ) {
                OutlinedTextField(
                    value = uiState.soloGuessInput,
                    onValueChange = onSoloGuessChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Enter guess") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = soloGame.status == "active",
                    colors = readableTextFieldColors(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onSubmitSoloGuess,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = soloGame.status == "active",
                ) {
                    Text(if (soloGame.status == "active") "Submit Guess" else "Game Finished")
                }
            }
        }

        if (soloGame.status == "finished") {
            item {
                ActionCard(
                    title = "Secret Number",
                    subtitle = "You solved it in ${soloGame.moves.size} moves.",
                    accent = Tangerine,
                ) {
                    SecretCodeChip(secret = soloGame.secret)
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Solo Timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        if (soloGame.moves.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))) {
                    Text(
                        "No guesses yet. Start with your first guess.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(soloGame.moves.reversed()) { move ->
                SoloMoveCard(move = move)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onStartNewSoloGame,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                ) {
                    Text("New Game")
                }
                OutlinedButton(
                    onClick = onLeaveSoloMode,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Back To Lobby")
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun SoloMoveCard(move: SoloMove) {
    val container = if (move.isCorrect) Tangerine.copy(alpha = 0.22f) else SkyBlue.copy(alpha = 0.14f)
    val badge = if (move.isCorrect) Tangerine else Ocean

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Attempt ${move.attemptNo}",
                    fontWeight = FontWeight.SemiBold,
                    color = DeepBlue,
                )
                Text(
                    text = "Guess: ${move.guess}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DeepBlue,
                )
            }

            Card(colors = CardDefaults.cardColors(containerColor = badge)) {
                Text(
                    text = move.scoreCode,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun RoomScreen(
    uiState: GameUiState,
    playerName: String,
    onSecretChanged: (String) -> Unit,
    onGuessChanged: (String) -> Unit,
    onSubmitSecret: () -> Unit,
    onSubmitGuess: () -> Unit,
    onRefresh: () -> Unit,
    onLeaveRoom: () -> Unit,
    onCopyRoomCode: (String) -> Unit,
) {
    val session = uiState.session ?: return
    val state = uiState.gameState

    if (state == null) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.93f),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(10.dp))
                Text("Syncing room state...")
            }
        }
        return
    }

    val mySecretToShow = state.mySecretValue ?: uiState.mySecretCache
    val canShowSecret = !mySecretToShow.isNullOrBlank() || state.mySecretSubmitted
    val opponentJoined = state.status != "waiting"
    var moveFilter by rememberSaveable { mutableStateOf(MoveQueryFilter.MIXED_QUERY) }

    val filteredMoves = remember(state.moves, state.role, moveFilter) {
        when (moveFilter) {
            MoveQueryFilter.MY_QUERY -> state.moves.filter { it.role == state.role }
            MoveQueryFilter.OPPONENT_QUERY -> state.moves.filter { it.role != null && it.role != state.role }
            MoveQueryFilter.MIXED_QUERY -> state.moves
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            HeroGameCard(
                roomCode = session.roomCode,
                playerName = playerName,
                state = state,
                onCopyRoomCode = onCopyRoomCode,
                onRefresh = onRefresh,
                onLeaveRoom = onLeaveRoom,
            )
        }

        item {
            ActionCard(
                title = "Your Secret",
                subtitle = if (canShowSecret) "Always visible to you" else "Set your 4-digit secret",
                accent = Tangerine,
            ) {
                if (canShowSecret) {
                    SecretCodeChip(secret = mySecretToShow ?: "----")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = uiState.secretInput,
                            onValueChange = onSecretChanged,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Secret number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = readableTextFieldColors(),
                        )
                        Button(onClick = onSubmitSecret, modifier = Modifier.fillMaxWidth(), enabled = !uiState.isLoading) {
                            Text("Submit Secret")
                        }
                    }
                }
            }
        }

        if (opponentJoined) {
            item {
                ActionCard(
                    title = "Your Guess",
                    subtitle = if (state.yourTurn) "Your move now" else "Opponent is thinking",
                    accent = if (state.yourTurn) Mint else SoftGray,
                ) {
                    OutlinedTextField(
                        value = uiState.guessInput,
                        onValueChange = onGuessChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Enter guess") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = state.status == "active",
                        readOnly = !state.yourTurn,
                        colors = readableTextFieldColors(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onSubmitGuess,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.status == "active" && !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.yourTurn) SkyBlue else SoftGray,
                            contentColor = if (state.yourTurn) Color.White else DeepBlue,
                        ),
                    ) {
                        Text(if (state.yourTurn) "Fire Guess" else "Wait For Turn")
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Move Timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                MoveQueryToggleRow(
                    selected = moveFilter,
                    onSelected = { moveFilter = it },
                )
            }
        }

        if (filteredMoves.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))) {
                    Text(
                        "No moves in this filter yet.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(filteredMoves) { move ->
                MoveCard(move = move, myRole = state.role)
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun HeroGameCard(
    roomCode: String,
    playerName: String,
    state: GameState,
    onCopyRoomCode: (String) -> Unit,
    onRefresh: () -> Unit,
    onLeaveRoom: () -> Unit,
) {
    val heroBrush = Brush.horizontalGradient(listOf(DeepBlue, Ocean, Mint))

    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(modifier = Modifier.background(heroBrush)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Room", color = Color.White.copy(alpha = 0.85f))
                        Text(roomCode, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "Player: $playerName",
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    IconActionButton(icon = Icons.Default.ContentCopy, onClick = { onCopyRoomCode(roomCode) }, tint = Color.White)
                    IconActionButton(icon = Icons.Default.Refresh, onClick = onRefresh, tint = Color.White)
                    IconActionButton(icon = Icons.AutoMirrored.Filled.ExitToApp, onClick = onLeaveRoom, tint = Color.White)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(label = "Role: ${state.role?.replaceFirstChar { it.uppercase() } ?: "-"}")
                    StatusChip(label = "Status: ${state.status ?: "-"}")
                    StatusChip(label = "v${state.appVersion ?: BuildConfig.VERSION_NAME}")
                    StatusChip(
                        label = if (state.yourTurn) "Your Turn" else "Opponent Turn",
                        containerColor = if (state.yourTurn) Tangerine.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.2f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AttemptBadge(label = "You", value = state.myAttempts)
                    AttemptBadge(label = "Opponent", value = state.opponentAttempts)
                }
            }
        }
    }
}

@Composable
private fun IconActionButton(icon: ImageVector, onClick: () -> Unit, tint: Color) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}

@Composable
private fun AttemptBadge(label: String, value: Int? = null, valueText: String? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.18f))) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
            Text(valueText ?: (value ?: 0).toString(), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(accent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(subtitle, color = DeepBlue.copy(alpha = 0.68f))
            content()
        }
    }
}

@Composable
private fun SecretCodeChip(secret: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Tangerine.copy(alpha = 0.18f))) {
        Text(
            text = secret,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Tangerine,
        )
    }
}

@Composable
private fun StatusChip(label: String, containerColor: Color = Color.White.copy(alpha = 0.2f)) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = Color.White,
        )
    )
}

private enum class MoveQueryFilter {
    MY_QUERY,
    OPPONENT_QUERY,
    MIXED_QUERY,
}

@Composable
private fun MoveQueryToggleRow(
    selected: MoveQueryFilter,
    onSelected: (MoveQueryFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        QueryFilterButton(
            label = "My Query",
            active = selected == MoveQueryFilter.MY_QUERY,
            onClick = { onSelected(MoveQueryFilter.MY_QUERY) },
            modifier = Modifier.weight(1f),
        )
        QueryFilterButton(
            label = "Opponent Query",
            active = selected == MoveQueryFilter.OPPONENT_QUERY,
            onClick = { onSelected(MoveQueryFilter.OPPONENT_QUERY) },
            modifier = Modifier.weight(1f),
        )
        QueryFilterButton(
            label = "Mixed Query",
            active = selected == MoveQueryFilter.MIXED_QUERY,
            onClick = { onSelected(MoveQueryFilter.MIXED_QUERY) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QueryFilterButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (active) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White.copy(alpha = 0.9f),
                contentColor = DeepBlue,
            ),
        ) {
            Text(label)
        }
    }
}

@Composable
private fun MoveCard(move: MoveItem, myRole: String?) {
    val isMine = move.role == myRole
    val isCorrect = move.isCorrect == "1"

    val container = when {
        isCorrect -> Tangerine.copy(alpha = 0.24f)
        isMine -> SkyBlue.copy(alpha = 0.16f)
        else -> Mint.copy(alpha = 0.14f)
    }

    val badgeColor = when {
        isCorrect -> Tangerine
        isMine -> SkyBlue
        else -> Ocean
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Turn ${move.turnNo ?: "-"} • ${if (isMine) "You" else "Opponent"}",
                    fontWeight = FontWeight.SemiBold,
                    color = DeepBlue,
                )
                Text(
                    text = "Guess: ${move.guessValue ?: "----"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DeepBlue,
                )
            }

            Card(colors = CardDefaults.cardColors(containerColor = badgeColor)) {
                Text(
                    text = move.scoreCode ?: "-",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun ResultPopupOverlay(
    winner: String?,
    finishReason: String?,
    opponentSecret: String?,
    onRematch: () -> Unit,
    onExit: () -> Unit,
    isLoading: Boolean,
) {
    val (title, subtitle, accent) = when (winner) {
        "you" -> {
            when (finishReason) {
                "opponent_timeout" -> Triple("Victory", "Opponent disconnected for 40 seconds.", Mint)
                "opponent_left" -> Triple("Victory", "Opponent left the room.", Mint)
                else -> Triple("Victory", "You cracked it. Ready for another duel?", Mint)
            }
        }
        "opponent" -> {
            when (finishReason) {
                "opponent_timeout" -> Triple("Defeat", "You were inactive for 40 seconds.", Tangerine)
                else -> Triple("Defeat", "Opponent won this round. Come back stronger.", Tangerine)
            }
        }
        else -> Triple("Draw", "Both solved on same attempt.", Ocean)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(accent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Text(subtitle, color = DeepBlue.copy(alpha = 0.72f))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F7FF)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Opponent Secret",
                            style = MaterialTheme.typography.labelLarge,
                            color = DeepBlue.copy(alpha = 0.72f),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = opponentSecret ?: "Not available",
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = FontFamily.Monospace,
                            color = DeepBlue,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onRematch,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                    ) {
                        Text("Rematch")
                    }
                    OutlinedButton(
                        onClick = onExit,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Exit")
                    }
                }
            }
        }
    }
}

@Composable
private fun readableTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DeepBlue,
    unfocusedTextColor = DeepBlue,
    disabledTextColor = DeepBlue.copy(alpha = 0.62f),
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Color(0xFFF7FAFF),
    focusedBorderColor = DeepBlue.copy(alpha = 0.55f),
    unfocusedBorderColor = DeepBlue.copy(alpha = 0.35f),
    disabledBorderColor = DeepBlue.copy(alpha = 0.22f),
    cursorColor = SkyBlue,
    focusedLabelColor = DeepBlue.copy(alpha = 0.72f),
    unfocusedLabelColor = DeepBlue.copy(alpha = 0.58f),
    disabledLabelColor = DeepBlue.copy(alpha = 0.42f),
    focusedPlaceholderColor = DeepBlue.copy(alpha = 0.5f),
    unfocusedPlaceholderColor = DeepBlue.copy(alpha = 0.42f),
    disabledPlaceholderColor = DeepBlue.copy(alpha = 0.3f),
)
