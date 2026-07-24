package com.haze.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haze.mobile.R
import com.haze.mobile.ui.theme.HazeColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ── Colours matching Qt's DARK_QSS exactly ───────────────────────────
private val BubbleMeBg     = Color(0xD71E1E20)   // rgba(30,30,32,215)
private val BubbleMeBorder = Color(0x78414144)   // rgba(65,65,68,120)
private val BubbleOtherBg     = Color(0xD20E0E10) // rgba(14,14,16,210)
private val BubbleOtherBorder = Color(0x642A2A2E) // rgba(42,42,46,100)
private val InputBarBg    = Color(0xF0030305)     // rgba(3,3,5,240)
private val InputFieldBg  = Color(0xDC0E0E10)    // rgba(14,14,16,220)
private val InputFieldBorder = Color(0xB4262628) // rgba(38,38,40,180)
private val BadgeBg       = Color(0xFF020905)    // #020905
private val BadgeBorder   = Color(0xFF0A2E14)    // #0a2e14

// ── Ambient-light animation — port of Qt's _AmbientLight ─────────────
@Composable
private fun AmbientLight(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ambient")
    // t cycles 0→2π in 90 s, matching Qt's 0.007 increment at 25 fps
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 90_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "t",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawRect(color = Color.Black)

        // Glow 1 — upper, drifting slowly
        val cx1 = w * (0.5f + 0.38f * sin(t * 0.32f))
        val cy1 = h * (0.38f + 0.22f * cos(t * 0.25f))
        drawRect(
            brush = Brush.radialGradient(
                0.00f to Color(0x12FFFFFF),
                0.35f to Color(0x07D2D7E1),
                1.00f to Color.Transparent,
                center = Offset(cx1, cy1),
                radius = w * 0.62f,
            ),
        )

        // Glow 2 — lower, counter-drifting
        val cx2 = w * (0.5f - 0.32f * cos(t * 0.21f))
        val cy2 = h * (0.65f + 0.20f * sin(t * 0.28f))
        drawRect(
            brush = Brush.radialGradient(
                0.0f to Color(0x0ABEC3D2),
                0.5f to Color(0x04969BAA),
                1.0f to Color.Transparent,
                center = Offset(cx2, cy2),
                radius = w * 0.48f,
            ),
        )

        // Centre pulse
        val pulse = 0.5f + 0.5f * sin(t * 0.15f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 6f / 255f * pulse),
                    Color.Transparent,
                ),
                center = Offset(w * 0.5f, h * 0.5f),
                radius = w * 0.5f,
            ),
        )
    }
}

// ── Root screen ───────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onTyping: (Boolean) -> Unit,
    onLeave: () -> Unit,
    onPanic: () -> Unit,
    onSendFile: (android.net.Uri) -> Unit,
    onSendBytes: (ByteArray, String, String) -> Unit,
    onSaveVault: (String) -> Unit,
    onOpenVault: () -> Unit,
    onNewSession: () -> Unit,
    onSwitchSession: (String) -> Unit,
    onKickUser: (String) -> Unit,
    onBlockUser: (String) -> Unit,
) {
    var showParticipants by remember { mutableStateOf(false) }
    var showLeaveDialog  by remember { mutableStateOf(false) }
    var showProtocol     by remember { mutableStateOf(false) }
    var showPanicDialog  by remember { mutableStateOf(false) }
    var showVaultDialog  by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // Animated ambient-light background fills the entire screen area
        AmbientLight(modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                status           = state.status,
                connected        = state.connected,
                participantCount = state.participants.size,
                onParticipants   = { showParticipants = !showParticipants },
                onProtocol       = { showProtocol = true },
                onVault          = { showVaultDialog = true },
                onOpenVault      = onOpenVault,
                onPanic          = { showPanicDialog = true },
                onLeave          = { showLeaveDialog = true },
            )

            if (state.isHost && state.hostOnion.isNotBlank()) {
                HostOnionBar(state.hostOnion)
            }

            SessionBar(
                sessions = state.sessions,
                onSwitch = onSwitchSession,
                onNew = onNewSession,
            )

            AnimatedVisibility(visible = showParticipants) {
                ParticipantsPanel(
                    participants = state.participants,
                    myNick = state.myNick,
                    isHost = state.isHost,
                    blocked = state.blockedUsers,
                    onKick = onKickUser,
                    onBlock = onBlockUser,
                )
            }

            MessageList(
                messages = state.messages,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            TypingLine(state.typingUsers)

            InputBar(
                enabled     = state.connected,
                onSend      = onSend,
                onTyping    = onTyping,
                onSendFile  = onSendFile,
                onSendBytes = onSendBytes,
            )
        }
    }

    if (showProtocol) {
        ProtocolPopup(
            onionAddress = state.hostOnion,
            onDismiss = { showProtocol = false },
        )
    }

    if (showLeaveDialog) {
        BasicAlertDialog(onDismissRequest = { showLeaveDialog = false }) {
            Column(
                modifier = Modifier
                    .background(HazeColors.Surface, RoundedCornerShape(16.dp))
                    .border(1.dp, HazeColors.Border2, RoundedCornerShape(16.dp))
                    .padding(20.dp),
            ) {
                Text("Leave session?", color = HazeColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("You will disconnect from this Haze chat.", color = HazeColors.Text2, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showLeaveDialog = false }) {
                        Text("Cancel", color = HazeColors.Text2)
                    }
                    TextButton(onClick = { showLeaveDialog = false; onLeave() }) {
                        Text("Leave", color = HazeColors.Red)
                    }
                }
            }
        }
    }

    if (showVaultDialog) {
        VaultSaveDialog(
            onDismiss = { showVaultDialog = false },
            onSave = { pw -> showVaultDialog = false; onSaveVault(pw) },
            onOpenVault = { showVaultDialog = false; onOpenVault() },
        )
    }

    if (showPanicDialog) {
        BasicAlertDialog(onDismissRequest = { showPanicDialog = false }) {
            Column(
                modifier = Modifier
                    .background(HazeColors.Surface, RoundedCornerShape(16.dp))
                    .border(1.dp, HazeColors.Red.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
            ) {
                Text("PANIC", color = HazeColors.Red, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Signals peers, wipes all messages from memory, and force-closes the app instantly. This cannot be undone.",
                    color = HazeColors.Text2, fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showPanicDialog = false }) {
                        Text("Cancel", color = HazeColors.Text2)
                    }
                    TextButton(onClick = { showPanicDialog = false; onPanic() }) {
                        Text("WIPE & EXIT", color = HazeColors.Red, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Title bar ─────────────────────────────────────────────────────────
@Composable
private fun TopBar(
    status: String,
    connected: Boolean,
    participantCount: Int,
    onParticipants: () -> Unit,
    onProtocol: () -> Unit,
    onVault: () -> Unit,
    onOpenVault: () -> Unit,
    onPanic: () -> Unit,
    onLeave: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(HazeColors.Surface)              // rgba(4,4,6,255)
            .border(
                width = 0.5.dp,
                color = HazeColors.Border,
                shape = RoundedCornerShape(0.dp),
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // HAZE wordmark — same transparent PNG as the desktop title bar
        Image(
            painter = painterResource(id = R.drawable.haze_wordmark),
            contentDescription = "Haze",
            modifier = Modifier.height(26.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(12.dp))

        // "● HAZE PROTOCOL" badge — click opens the protocol/circuit popup
        Row(
            modifier = Modifier
                .background(BadgeBg, RoundedCornerShape(6.dp))
                .border(1.dp, BadgeBorder, RoundedCornerShape(6.dp))
                .clickable(onClick = onProtocol)
                .padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .background(
                        if (connected) HazeColors.Green else HazeColors.Yellow,
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                "HAZE PROTOCOL",
                color = HazeColors.Green,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        // Participant count pill
        Text(
            "$participantCount",
            color = HazeColors.Text2,
            fontSize = 12.sp,
            modifier = Modifier
                .background(HazeColors.Surface2, RoundedCornerShape(20.dp))
                .clickable(onClick = onParticipants)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
        Spacer(Modifier.width(6.dp))
        // PANIC — wipe & kill (matches desktop panicBtn)
        Text(
            "PANIC",
            color = HazeColors.Red,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier
                .background(Color(0xF00E0000), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF280000), RoundedCornerShape(6.dp))
                .clickable(onClick = onPanic)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Spacer(Modifier.width(4.dp))
        // Hamburger menu — Save to Vault / Secret Vault / Leave
        Box {
            Box(
                modifier = Modifier.size(32.dp).clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Menu, contentDescription = "menu", tint = HazeColors.Text2, modifier = Modifier.size(20.dp))
            }
            androidx.compose.material3.DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.background(HazeColors.Surface),
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Save to Vault", color = HazeColors.Text, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Lock, null, tint = HazeColors.Green, modifier = Modifier.size(16.dp)) },
                    onClick = { menuOpen = false; onVault() },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Secret Vault", color = HazeColors.Text, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Lock, null, tint = HazeColors.Text3, modifier = Modifier.size(16.dp)) },
                    onClick = { menuOpen = false; onOpenVault() },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Leave session", color = HazeColors.Red, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = HazeColors.Red, modifier = Modifier.size(16.dp)) },
                    onClick = { menuOpen = false; onLeave() },
                )
            }
        }
    }
}

// ── Participants panel ────────────────────────────────────────────────
@Composable
private fun SessionBar(sessions: List<SessionSummary>, onSwitch: (String) -> Unit, onNew: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HazeColors.Bg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sessions.forEach { s ->
            Row(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .background(if (s.active) HazeColors.Surface3 else HazeColors.Surface2, RoundedCornerShape(8.dp))
                    .border(1.dp, if (s.active) HazeColors.Border2 else HazeColors.Border, RoundedCornerShape(8.dp))
                    .clickable { onSwitch(s.id) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(5.dp).background(if (s.connected) HazeColors.Green else HazeColors.Yellow, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    s.label,
                    color = if (s.active) HazeColors.Text else HazeColors.Text3,
                    fontSize = 10.sp,
                    fontWeight = if (s.active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, softWrap = false,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(HazeColors.Surface2, RoundedCornerShape(8.dp))
                .border(1.dp, HazeColors.Border, RoundedCornerShape(8.dp))
                .clickable(onClick = onNew),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "new session", tint = HazeColors.Text2, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun HostOnionBar(onion: String) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HazeColors.Surface)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("YOUR ONION", color = HazeColors.Green, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            onion,
            color = HazeColors.Text2,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (copied) "Copied" else "Copy",
            color = if (copied) HazeColors.Green else HazeColors.Text3,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(HazeColors.Surface3, RoundedCornerShape(6.dp))
                .clickable {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(onion))
                    copied = true
                }
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ParticipantsPanel(
    participants: List<String>,
    myNick: String,
    isHost: Boolean,
    blocked: Set<String>,
    onKick: (String) -> Unit,
    onBlock: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HazeColors.Surface)
            .border(
                width = 0.5.dp,
                color = HazeColors.Border,
                shape = RoundedCornerShape(0.dp),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            "ONLINE",
            color = HazeColors.Text3,
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        participants.forEach { p ->
            val isMe = p == myNick
            val isBlocked = p in blocked
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(if (isMe) HazeColors.Green else HazeColors.Text4, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isMe) "$p (you)" else p,
                    color = if (isMe) HazeColors.Text2 else HazeColors.Text3,
                    fontSize = 12.sp,
                )
                if (isBlocked) {
                    Spacer(Modifier.width(6.dp))
                    Text("muted", color = HazeColors.Text4, fontSize = 9.sp, fontStyle = FontStyle.Italic)
                }
                if (!isMe) {
                    Spacer(Modifier.weight(1f))
                    // Client-side mute/unmute
                    Text(
                        if (isBlocked) "Unmute" else "Mute",
                        color = HazeColors.Text3, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { onBlock(p) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    // Host-only kick
                    if (isHost) {
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "Kick",
                            color = HazeColors.Red, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .background(Color(0xF00E0000), RoundedCornerShape(5.dp))
                                .border(1.dp, Color(0xFF280000), RoundedCornerShape(5.dp))
                                .clickable { onKick(p) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Message list + empty (slogan) state ───────────────────────────────
@Composable
private fun MessageList(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    if (messages.isEmpty()) {
        // Slogan — matches Qt's SloganWidget (shown when chat is empty)
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.haze_wordmark),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(0.52f)
                        .alpha(0.18f),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your words dissolve into the haze.",
                    color = HazeColors.Text3,
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.3.sp,
                )
                Text(
                    "HAZE PROTOCOL  ·  END-TO-END ENCRYPTED  ·  NO LOGS",
                    color = HazeColors.Text4,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(messages) { msg ->
                when {
                    msg.isSystem -> SystemMessage(msg.content)
                    msg.isFile   -> FileBubble(msg)
                    else         -> MessageBubble(msg)
                }
            }
        }
    }
}

// ── System message ────────────────────────────────────────────────────
@Composable
private fun SystemMessage(text: String) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = HazeColors.Text3,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            fontStyle = FontStyle.Italic,
            letterSpacing = 0.3.sp,
        )
    }
}

// ── Chat bubble — Qt bubble shape: asymmetric radius on the "tail" corner ──
@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isMe = msg.isMe
    val bubbleShape = if (isMe)
        // Qt #bubbleMe: border-radius 18 18 4 18 (TL TR BR BL)
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
    else
        // Qt #bubbleOther: border-radius 18 18 18 4
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
    ) {
        if (!isMe) {
            Text(
                msg.nick.uppercase(),
                color = HazeColors.Text4,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (isMe) BubbleMeBg else BubbleOtherBg,
                    bubbleShape,
                )
                .border(
                    1.dp,
                    if (isMe) BubbleMeBorder else BubbleOtherBorder,
                    bubbleShape,
                )
                .padding(horizontal = 13.dp, vertical = 9.dp),
        ) {
            Column {
                Text(
                    msg.content,
                    color = HazeColors.Text,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                // Timestamp (tsLabel)
                Text(
                    msg.timestamp,
                    color = HazeColors.Text3,
                    fontSize = 10.sp,
                    modifier = Modifier.align(if (isMe) Alignment.End else Alignment.Start),
                )
            }
        }
    }
}

// ── File / image / voice message ──────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FileBubble(msg: ChatMessage) {
    val isMe = msg.isMe
    val shape = if (isMe)
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
    else
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
    val isImage = msg.mime?.startsWith("image/") == true
    val isAudio = msg.mime?.startsWith("audio/") == true

    val context = androidx.compose.ui.platform.LocalContext.current
    // Long-press the bubble to save the file to storage.
    val saver = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument(
            msg.mime ?: "application/octet-stream"
        )
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(msg.fileData ?: ByteArray(0)) }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
    ) {
        if (!isMe) {
            Text(
                msg.nick.uppercase(),
                color = HazeColors.Text4, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(if (isMe) BubbleMeBg else BubbleOtherBg, shape)
                .border(1.dp, if (isMe) BubbleMeBorder else BubbleOtherBorder, shape)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { if (msg.fileReady) saver.launch(msg.filename ?: "haze_file") },
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                when {
                    isImage && msg.fileReady -> ImagePreview(msg.fileData!!)
                    isAudio -> AudioPlayer(msg)
                    else -> FileRow(msg)
                }

                if (!msg.fileReady) {
                    Spacer(Modifier.height(6.dp))
                    val pct = if (msg.totalSize > 0) (msg.received * 100 / msg.totalSize) else 0
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = HazeColors.Green,
                        trackColor = HazeColors.Surface3,
                    )
                    Text("Receiving $pct%", color = HazeColors.Text3, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                }

                Text(
                    msg.timestamp,
                    color = HazeColors.Text3, fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp).align(if (isMe) Alignment.End else Alignment.Start),
                )
            }
        }
    }
}

@Composable
private fun ImagePreview(data: ByteArray) {
    val bitmap = remember(data) {
        runCatching { android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "image",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Text("🖼  image", color = HazeColors.Text, fontSize = 13.sp)
    }
}

@Composable
private fun FileRow(msg: ChatMessage) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (msg.mime?.startsWith("image/") == true) Icons.Filled.Image else Icons.Filled.InsertDriveFile,
            contentDescription = null,
            tint = HazeColors.Text2,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                msg.filename ?: "file",
                color = HazeColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
            Text(formatSize(msg.totalSize), color = HazeColors.Text3, fontSize = 10.sp)
        }
    }
}

@Composable
private fun AudioPlayer(msg: ChatMessage) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var playing by remember { mutableStateOf(false) }
    val player = remember { android.media.MediaPlayer() }
    DisposableEffect(Unit) { onDispose { runCatching { player.release() } } }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(HazeColors.Accent, CircleShape)
                .clickable(enabled = msg.fileReady) {
                    if (playing) {
                        runCatching { player.pause() }; playing = false
                    } else {
                        runCatching {
                            val f = java.io.File(context.cacheDir, "play_${msg.fileId}.m4a")
                            if (!f.exists()) f.writeBytes(msg.fileData ?: ByteArray(0))
                            player.reset()
                            player.setDataSource(f.absolutePath)
                            player.prepare()
                            player.setOnCompletionListener { playing = false }
                            player.start(); playing = true
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "pause" else "play",
                tint = HazeColors.Bg,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("Voice note", color = HazeColors.Text, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text(formatSize(msg.totalSize), color = HazeColors.Text3, fontSize = 10.sp)
    }
}

private fun formatSize(bytes: Int): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "%.1f MB".format(bytes / (1024f * 1024f))
}

@Composable
private fun TypingLine(typingUsers: List<String>) {
    val text = if (typingUsers.isEmpty()) ""
    else typingUsers.joinToString(", ") + " is typing…"
    Box(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text.isNotEmpty()) {
            Text(
                text,
                color = HazeColors.Text3,
                fontSize = 10.sp,
                fontStyle = FontStyle.Italic,
                letterSpacing = 0.3.sp,
            )
        }
    }
}

// ── Input bar — Qt #inputBar + attach/camera/voice + #sendBtn ─────────
@Composable
private fun InputBar(
    enabled: Boolean,
    onSend: (String) -> Unit,
    onTyping: (Boolean) -> Unit,
    onSendFile: (android.net.Uri) -> Unit,
    onSendBytes: (ByteArray, String, String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var text by remember { mutableStateOf("") }
    var typingSent by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    val recorderRef = remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    val recordFileRef = remember { mutableStateOf<java.io.File?>(null) }

    // File / photo picker
    val pickFile = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(onSendFile) }

    // Camera capture (writes to a FileProvider uri, then sends it)
    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) pendingCameraUri?.let(onSendFile) }
    val cameraPerm = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) { val u = createImageUri(context); pendingCameraUri = u; takePhoto.launch(u) }
    }
    fun launchCamera() {
        val has = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (has) { val u = createImageUri(context); pendingCameraUri = u; takePhoto.launch(u) }
        else cameraPerm.launch(android.Manifest.permission.CAMERA)
    }

    // Voice note recording
    fun startRecording() {
        runCatching {
            val f = java.io.File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            @Suppress("DEPRECATION")
            val r = android.media.MediaRecorder()
            r.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start()
            recorderRef.value = r; recordFileRef.value = f; recording = true
        }
    }
    fun stopRecording(send: Boolean) {
        recorderRef.value?.let { r -> runCatching { r.stop() }; runCatching { r.release() } }
        recorderRef.value = null
        recording = false
        val f = recordFileRef.value
        if (send && f != null && f.exists()) {
            val data = f.readBytes()
            if (data.isNotEmpty()) onSendBytes(data, "voice_note.m4a", "audio/mp4")
        }
        recordFileRef.value = null
    }
    val micPerm = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startRecording() }
    fun toggleVoice() {
        if (recording) { stopRecording(true); return }
        val has = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (has) startRecording() else micPerm.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(InputBarBg)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InputIcon(Icons.Filled.AttachFile, enabled && !recording) { pickFile.launch("*/*") }
        InputIcon(Icons.Filled.PhotoCamera, enabled && !recording) { launchCamera() }
        InputIcon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, enabled, tintRed = recording) { toggleVoice() }

        Spacer(Modifier.width(6.dp))

        // Message input (or recording indicator)
        Box(
            modifier = Modifier
                .weight(1f)
                .background(InputFieldBg, RoundedCornerShape(22.dp))
                .border(1.dp, if (recording) HazeColors.Red.copy(alpha = 0.4f) else InputFieldBorder, RoundedCornerShape(22.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            if (recording) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(HazeColors.Red, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text("Recording…  tap", color = HazeColors.Red, fontSize = 12.sp)
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Filled.Stop, contentDescription = null, tint = HazeColors.Red, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("to send", color = HazeColors.Red, fontSize = 12.sp)
                }
            } else {
                // Placeholder lives inside decorationBox and shares an identical
                // line box (centered line-height, no font padding) so the cursor
                // and the placeholder sit on exactly the same line when empty.
                val textStyle = TextStyle(
                    color = Color(0xFFF0F0F0),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
                    ),
                )
                BasicTextField(
                    value = text,
                    onValueChange = {
                        text = it.take(4000)
                        val nowTyping = text.isNotEmpty()
                        if (nowTyping != typingSent) { typingSent = nowTyping; onTyping(nowTyping) }
                    },
                    enabled = enabled,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(HazeColors.Accent),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        submit(text, onSend); text = ""; typingSent = false; onTyping(false)
                    }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) {
                                Text(
                                    "Type a message…",
                                    style = textStyle.copy(color = HazeColors.Text3),
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        IconButton(
            onClick = { submit(text, onSend); text = ""; typingSent = false; onTyping(false) },
            enabled = enabled && text.isNotBlank() && !recording,
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (enabled && text.isNotBlank() && !recording) HazeColors.Accent else HazeColors.Surface3,
                    CircleShape,
                ),
        ) {
            Icon(
                Icons.Filled.ArrowUpward,
                contentDescription = "send",
                tint = if (enabled && text.isNotBlank() && !recording) HazeColors.Bg else HazeColors.Text3,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun InputIcon(icon: ImageVector, enabled: Boolean, tintRed: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(
                if (tintRed) HazeColors.Red.copy(alpha = 0.15f) else HazeColors.Surface3,
                CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (tintRed) HazeColors.Red else HazeColors.Text2,
            modifier = Modifier.size(19.dp),
        )
    }
}

private fun createImageUri(context: android.content.Context): android.net.Uri {
    val f = java.io.File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
    return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultSaveDialog(onDismiss: () -> Unit, onSave: (String) -> Unit, onOpenVault: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(HazeColors.Surface, RoundedCornerShape(16.dp))
                .border(1.dp, HazeColors.Border2, RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = HazeColors.Green, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save to Vault", color = HazeColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "This chat is encrypted with a password only you know. Enter one to seal it — you'll need it to reopen.",
                color = HazeColors.Text3, fontSize = 12.sp,
            )
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.OutlinedTextField(
                value = pw,
                onValueChange = { pw = it },
                placeholder = { Text("Password", color = HazeColors.Text3) },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = HazeColors.Surface2,
                    unfocusedContainerColor = HazeColors.Surface2,
                    focusedBorderColor = HazeColors.Border2,
                    unfocusedBorderColor = HazeColors.Border,
                    focusedTextColor = HazeColors.Text,
                    unfocusedTextColor = HazeColors.Text,
                    cursorColor = HazeColors.Accent,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenVault) { Text("Open vault", color = HazeColors.Text2, fontSize = 12.sp) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel", color = HazeColors.Text2) }
                TextButton(onClick = { if (pw.isNotBlank()) onSave(pw) }) {
                    Text("Save", color = HazeColors.Green, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun submit(text: String, onSend: (String) -> Unit) {
    if (text.isNotBlank()) onSend(text)
}
