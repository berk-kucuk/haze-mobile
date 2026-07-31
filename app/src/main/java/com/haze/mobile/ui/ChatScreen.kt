package com.haze.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
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

// How long a message stays readable before the privacy blur kicks in.
private const val BLUR_DELAY_MS = 4_000L
// How long a tapped message stays revealed before it blurs again.
private const val BLUR_REVEAL_MS = 6_000L

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
    onSend: (text: String, replyToNick: String?, replyToContent: String?) -> Unit,
    onTyping: (Boolean) -> Unit,
    onLeave: () -> Unit,
    onPanic: () -> Unit,
    onConfirmReceivedPanic: () -> Unit,
    onDismissReceivedPanic: () -> Unit,
    onReconnect: () -> Unit,
    onSendFile: (android.net.Uri) -> Unit,
    onSendBytes: (ByteArray, String, String) -> Unit,
    onSaveVault: (String) -> Unit,
    onOpenVault: () -> Unit,
    onOpenSettings: () -> Unit,
    onDeleteMessage: (ChatMessage) -> Unit,
    onEditMessage: (ChatMessage, String) -> Unit,
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
    // Message the user is currently replying to (null = not replying).
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Top + horizontal only: the bottom inset is deliberately left to
            // InputBar (which also needs imePadding), so the message list can
            // still scroll under the navigation bar. safeDrawing rather than
            // statusBars so a display cutout — including the left/right notch
            // in landscape — is also kept clear.
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            ),
    ) {
        // Animated ambient-light background fills the entire screen area
        AmbientLight(modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                status           = state.status,
                connected        = state.connected,
                isHost           = state.isHost,
                latencyMs        = state.latencyMs,
                participantCount = state.participants.size,
                onParticipants   = { showParticipants = !showParticipants },
                onProtocol       = { showProtocol = true },
                onVault          = { showVaultDialog = true },
                onOpenVault      = onOpenVault,
                onOpenSettings   = onOpenSettings,
                onPanic          = { showPanicDialog = true },
                onLeave          = { showLeaveDialog = true },
            )

            if (state.isHost && state.hostOnion.isNotBlank()) {
                HostOnionBar(state.hostOnion, allowWebAccess = state.settings.allowWebAccess)
            }

            // Reconnect banner — shown when a join session dropped mid-chat.
            if (!state.connected && !state.connecting && !state.isHost && state.hostOnion.isNotBlank()) {
                ReconnectBanner(
                    onReconnect = onReconnect,
                    onLeave = { showLeaveDialog = true },
                )
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
                myNick = state.myNick,
                showTimestamps = state.settings.showTimestamps,
                blurMessages = state.settings.blurMessages,
                onReply = { replyTo = it },
                onDelete = onDeleteMessage,
                onEdit = onEditMessage,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            TypingLine(state.typingUsers)

            InputBar(
                enabled     = state.connected,
                replyTo     = replyTo,
                myNick      = state.myNick,
                enterToSend = state.settings.enterToSend,
                onClearReply = { replyTo = null },
                onSend      = { text ->
                    val target = replyTo
                    onSend(
                        text,
                        target?.nick,
                        target?.content?.take(200),
                    )
                    replyTo = null
                },
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

    // A peer/host triggered panic — ask before wiping rather than silently
    // force-disconnecting (matches desktop's _show_panic_dialog: the user
    // gets the same choice whether they or someone else pulled the trigger).
    val pendingPanicNick = state.pendingPanicNick
    if (pendingPanicNick != null) {
        BasicAlertDialog(onDismissRequest = onDismissReceivedPanic) {
            Column(
                modifier = Modifier
                    .background(HazeColors.Surface, RoundedCornerShape(16.dp))
                    .border(1.dp, HazeColors.Red.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
            ) {
                Text("PANIC RECEIVED", color = HazeColors.Red, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${pendingPanicNick.uppercase()} triggered panic. Wipe all messages from memory and exit now?",
                    color = HazeColors.Text2, fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissReceivedPanic) {
                        Text("Not now", color = HazeColors.Text2)
                    }
                    TextButton(onClick = onConfirmReceivedPanic) {
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
    isHost: Boolean,
    latencyMs: Int?,
    participantCount: Int,
    onParticipants: () -> Unit,
    onProtocol: () -> Unit,
    onVault: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenSettings: () -> Unit,
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

        // Round-trip latency from the heartbeat ping — join-mode only, mirrors
        // desktop's title-bar latency dot/label (host has no peer to ping).
        if (!isHost && latencyMs != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                "$latencyMs ms",
                color = HazeColors.Text3,
                fontSize = 9.sp,
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
                    text = { Text("Settings", color = HazeColors.Text, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Settings, null, tint = HazeColors.Text2, modifier = Modifier.size(16.dp)) },
                    onClick = { menuOpen = false; onOpenSettings() },
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
private fun HostOnionBar(onion: String, allowWebAccess: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var torCopied by remember { mutableStateOf(false) }
    // Displayed AND copied without the suffix — this is the address other
    // Haze users paste into "Join", and its own screen re-appends ".onion"
    // automatically, so hiding it here is intentional, not a bug.
    val display = onion.removeSuffix(".onion")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HazeColors.Surface)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("SESSION", color = HazeColors.Green, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            display,
            color = HazeColors.Text2,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        // Only meaningful when the host also published the web bridge on
        // virtual port 80 (Settings -> Allow Tor Browser access) — without
        // it, this address has nothing listening on port 80 to browse to.
        // Copies the FULL .onion URL (unlike the plain Copy button below) —
        // that's what Tor Browser's address bar actually needs. Deliberately
        // just a copy, not an auto-launch: handing the user a URL to paste is
        // more predictable than guessing which browser/profile should open it.
        if (allowWebAccess) {
            Text(
                if (torCopied) "Copied" else "Tor Browser",
                color = if (torCopied) HazeColors.Green else HazeColors.Text3,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(HazeColors.Surface3, RoundedCornerShape(6.dp))
                    .clickable {
                        copySensitive(context, "Haze Tor Browser link", "http://$onion")
                        torCopied = true
                    }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            if (copied) "Copied" else "Copy",
            color = if (copied) HazeColors.Green else HazeColors.Text3,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(HazeColors.Surface3, RoundedCornerShape(6.dp))
                .clickable {
                    copySensitive(context, "Haze session", display)
                    copied = true
                }
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ReconnectBanner(onReconnect: () -> Unit, onLeave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HazeColors.Red.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Connection lost",
            color = HazeColors.Red,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .background(HazeColors.Green, RoundedCornerShape(6.dp))
                .clickable(onClick = onReconnect)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text("Reconnect", color = HazeColors.Bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(HazeColors.Surface3, RoundedCornerShape(6.dp))
                .clickable(onClick = onLeave)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text("Leave", color = HazeColors.Text3, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
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
                // Everyone on this list is connected — a nick only appears here
                // while its owner is in the room — so the presence dot is green
                // for all of them, matching desktop's _AvatarDelegate. Green for
                // "me" only made every peer, the host included, look offline.
                // Muted peers get a dimmed dot rather than a grey one, so the
                // dot keeps meaning presence and nothing else.
                Box(
                    Modifier
                        .size(6.dp)
                        .background(
                            if (isBlocked) HazeColors.Green.copy(alpha = 0.35f) else HazeColors.Green,
                            CircleShape,
                        ),
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
private fun MessageList(
    messages: List<ChatMessage>,
    myNick: String,
    showTimestamps: Boolean,
    blurMessages: Boolean,
    onReply: (ChatMessage) -> Unit,
    onDelete: (ChatMessage) -> Unit,
    onEdit: (ChatMessage, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // ── Privacy-blur bookkeeping, hoisted so it survives LazyColumn
    // recycling (scrolling a bubble off-screen must not reset its blur). ──
    // firstSeen: when a message first appeared (blur starts DELAY after this).
    // revealUntil: timestamp until which a tapped message stays clear.
    val firstSeen = remember { mutableStateMapOf<String, Long>() }
    val revealUntil = remember { mutableStateMapOf<String, Long>() }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    if (blurMessages) {
        LaunchedEffect(Unit) {
            while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(300) }
        }
    }

    // The Haze wordmark shows until a real (non-system) message exists, then
    // fades away. System notices ("joined", "Connected") don't count.
    val hasRealMessages = messages.any { !it.isSystem }

    Box(modifier = modifier) {
        androidx.compose.animation.AnimatedVisibility(
            visible = !hasRealMessages,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(700)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            SloganContent()
        }

        if (messages.isNotEmpty()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                itemsIndexed(messages, key = { i, m -> m.msgId ?: m.fileId ?: "i$i" }) { index, msg ->
                    // Group consecutive messages from the same author: only the first
                    // in a run shows the author label. A system message breaks the run.
                    val prev = messages.getOrNull(index - 1)
                    val showAuthor = prev == null || prev.isSystem ||
                        prev.isMe != msg.isMe || prev.nick != msg.nick

                    val key = msg.msgId ?: msg.fileId ?: "i$index"
                    // Record first-seen once (guarded so recycling can't restart it).
                    LaunchedEffect(key) { if (key !in firstSeen) firstSeen[key] = System.currentTimeMillis() }
                    val start = firstSeen[key]
                    val blurred = blurMessages && start != null &&
                        now >= start + BLUR_DELAY_MS && now >= (revealUntil[key] ?: 0L)
                    val onReveal = { revealUntil[key] = System.currentTimeMillis() + BLUR_REVEAL_MS }

                    when {
                        msg.isSystem -> SystemMessage(msg.content)
                        msg.isFile   -> FileBubble(msg, myNick, showTimestamps, showAuthor, blurred, onReveal)
                        !msg.deleted && looksLikeSticker(msg.content) ->
                            StickerBubble(msg, showAuthor, showTimestamps, onReply, onDelete)
                        else         -> MessageBubble(msg, myNick, showTimestamps, showAuthor, blurred, onReveal, onReply, onDelete, onEdit)
                    }
                }
            }
        }
    }
}

// ── Empty-state slogan (Haze wordmark) ────────────────────────────────
@Composable
private fun SloganContent() {
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: ChatMessage,
    myNick: String,
    showTimestamps: Boolean,
    showAuthor: Boolean,
    blurred: Boolean,
    onReveal: () -> Unit,
    onReply: (ChatMessage) -> Unit,
    onDelete: (ChatMessage) -> Unit,
    onEdit: (ChatMessage, String) -> Unit,
) {
    val isMe = msg.isMe
    if (msg.deleted) { DeletedBubble(isMe, msg, showAuthor, showTimestamps); return }
    val bubbleShape = if (isMe)
        // Qt #bubbleMe: border-radius 18 18 4 18 (TL TR BR BL)
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
    else
        // Qt #bubbleOther: border-radius 18 18 18 4
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)

    val context = androidx.compose.ui.platform.LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    // Bubbles cap at ~68% of screen width so short lines don't stretch.
    val maxBubbleWidth = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * 0.68f).dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
    ) {
        // Author label — only on the first message of a run (grouped otherwise).
        if (showAuthor) {
            Text(
                if (isMe) "YOU" else msg.nick.uppercase(),
                color = HazeColors.Text4,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Box {
            Box(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    // Blur the whole bubble (background + text) so nothing shows
                    // through; clipped to the bubble shape to avoid a hard square.
                    .then(if (blurred) Modifier.blur(20.dp, BlurredEdgeTreatment(bubbleShape)) else Modifier)
                    .background(
                        if (isMe) BubbleMeBg else BubbleOtherBg,
                        bubbleShape,
                    )
                    .border(
                        1.dp,
                        if (isMe) BubbleMeBorder else BubbleOtherBorder,
                        bubbleShape,
                    )
                    .combinedClickable(
                        onClick = { if (blurred) onReveal() },
                        onLongClick = { menuOpen = true },
                    )
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            ) {
                Column {
                    // Quoted message being replied to
                    if (msg.replyToContent != null) {
                        ReplyQuote(
                            nick = msg.replyToNick?.let { if (it == myNick) "You" else it } ?: "",
                            content = msg.replyToContent,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        msg.content,
                        color = HazeColors.Text,
                        fontSize = 13.sp,
                    )
                    if (msg.edited) {
                        Text(
                            "(edited)",
                            color = HazeColors.Text3,
                            fontSize = 10.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        )
                    }
                    if (showTimestamps || msg.disappearSecs > 0) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.align(if (isMe) Alignment.End else Alignment.Start),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (msg.disappearSecs > 0) {
                                val label = if (msg.disappearSecs >= 60) "${msg.disappearSecs / 60}m" else "${msg.disappearSecs}s"
                                Text(
                                    "⏱ $label",
                                    color = HazeColors.Green,
                                    fontSize = 10.sp,
                                )
                            }
                            if (showTimestamps) {
                                Text(
                                    msg.timestamp,
                                    color = HazeColors.Text3,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            }

            // Long-press context menu — Reply / Copy
            androidx.compose.material3.DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.background(HazeColors.Surface),
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Reply", color = HazeColors.Text, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null, tint = HazeColors.Text2, modifier = Modifier.size(16.dp)) },
                    onClick = { menuOpen = false; onReply(msg) },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Copy", color = HazeColors.Text, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null, tint = HazeColors.Text2, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        menuOpen = false
                        copySensitive(context, "Haze message", msg.content)
                    },
                )
                if (isMe && msg.msgId != null) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Edit", color = HazeColors.Text, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, null, tint = HazeColors.Text2, modifier = Modifier.size(16.dp)) },
                        onClick = { menuOpen = false; editText = msg.content; showEditDialog = true },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Delete", color = HazeColors.Red, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = HazeColors.Red, modifier = Modifier.size(16.dp)) },
                        onClick = { menuOpen = false; onDelete(msg) },
                    )
                }
            }
        }
    }

    // Edit dialog
    if (showEditDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit message", color = HazeColors.Text) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = HazeColors.Text),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HazeColors.Green,
                        unfocusedBorderColor = HazeColors.Border2,
                    ),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (editText.isNotBlank()) {
                        onEdit(msg, editText)
                        showEditDialog = false
                    }
                }) { Text("Save", color = HazeColors.Green) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = HazeColors.Text3)
                }
            },
            containerColor = HazeColors.Surface,
        )
    }
}

// ── Deleted-message placeholder ───────────────────────────────────────
@Composable
private fun DeletedBubble(isMe: Boolean, msg: ChatMessage, showAuthor: Boolean, showTimestamps: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
    ) {
        if (showAuthor) {
            Text(
                if (isMe) "YOU" else msg.nick.uppercase(),
                color = HazeColors.Text4, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Row(
            modifier = Modifier
                .background(Color(0x14FFFFFF), shape)
                .border(1.dp, HazeColors.Border, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Delete, null, tint = HazeColors.Text4, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "This message was deleted",
                color = HazeColors.Text3, fontSize = 12.sp, fontStyle = FontStyle.Italic,
            )
            if (showTimestamps) {
                Spacer(Modifier.width(8.dp))
                Text(msg.timestamp, color = HazeColors.Text4, fontSize = 10.sp)
            }
        }
    }
}

// ── Sticker / emoji-only message (rendered large, no bubble) ───────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun StickerBubble(
    msg: ChatMessage,
    showAuthor: Boolean,
    showTimestamps: Boolean,
    onReply: (ChatMessage) -> Unit,
    onDelete: (ChatMessage) -> Unit,
) {
    val isMe = msg.isMe
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
    ) {
        if (showAuthor) {
            Text(
                if (isMe) "YOU" else msg.nick.uppercase(),
                color = HazeColors.Text4, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Box {
            Text(
                msg.content,
                fontSize = 46.sp,
                modifier = Modifier
                    .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
            androidx.compose.material3.DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.background(HazeColors.Surface),
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Reply", color = HazeColors.Text, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null, tint = HazeColors.Text2, modifier = Modifier.size(16.dp)) },
                    onClick = { menuOpen = false; onReply(msg) },
                )
                if (isMe && msg.msgId != null) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Delete", color = HazeColors.Red, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = HazeColors.Red, modifier = Modifier.size(16.dp)) },
                        onClick = { menuOpen = false; onDelete(msg) },
                    )
                }
            }
        }
        if (showTimestamps) {
            Text(
                msg.timestamp,
                color = HazeColors.Text3, fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** True when [text] is only emoji (a "sticker") so it can render large. */
private fun looksLikeSticker(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty()) return false
    val codePoints = t.codePointCount(0, t.length)
    if (codePoints > 5) return false
    // No letters, digits, or basic ASCII punctuation → treat as emoji sticker.
    var hasEmoji = false
    var i = 0
    while (i < t.length) {
        val cp = t.codePointAt(i)
        i += Character.charCount(cp)
        if (Character.isWhitespace(cp)) continue
        if (Character.isLetterOrDigit(cp)) return false
        if (cp < 0x2010) return false          // basic ASCII punctuation/symbols
        hasEmoji = true
    }
    return hasEmoji
}

// ── Small quoted-message block shown inside a reply bubble ─────────────
@Composable
private fun ReplyQuote(nick: String, content: String) {
    Row(
        // IntrinsicSize.Min → the accent bar stretches to exactly the quote's
        // height instead of overshooting past the top of the block.
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x22FFFFFF))
            .height(IntrinsicSize.Min),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(HazeColors.Green),
        )
        Column(modifier = Modifier.padding(start = 8.dp, top = 5.dp, bottom = 5.dp, end = 8.dp)) {
            Text(
                nick,
                color = HazeColors.Green,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                content,
                color = HazeColors.Text2,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

// ── File / image / voice message ──────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FileBubble(
    msg: ChatMessage,
    myNick: String,
    showTimestamps: Boolean,
    showAuthor: Boolean,
    blurred: Boolean,
    onReveal: () -> Unit,
) {
    val isMe = msg.isMe
    val shape = if (isMe)
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
    else
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
    val isImage = msg.mime?.startsWith("image/") == true
    val isAudio = msg.mime?.startsWith("audio/") == true
    val isVideo = msg.mime?.startsWith("video/") == true

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
        if (showAuthor) {
            Text(
                if (isMe) "YOU" else msg.nick.uppercase(),
                color = HazeColors.Text4, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                // Blur the whole bubble, clipped to its shape (no hard square).
                .then(if (blurred) Modifier.blur(20.dp, BlurredEdgeTreatment(shape)) else Modifier)
                .background(if (isMe) BubbleMeBg else BubbleOtherBg, shape)
                .border(1.dp, if (isMe) BubbleMeBorder else BubbleOtherBorder, shape)
                .combinedClickable(
                    onClick = { if (blurred) onReveal() },
                    onLongClick = {
                        if (blurred) onReveal()
                        else if (msg.fileReady) saver.launch(msg.filename ?: "haze_file")
                    },
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
            ) {
                when {
                    isImage && msg.fileReady -> ImagePreview(msg.fileData!!, msg.mime)
                    isAudio -> AudioPlayer(msg)
                    isVideo && msg.fileReady -> VideoPlayer(msg, blurred)
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

                if (showTimestamps) {
                    Text(
                        msg.timestamp,
                        color = HazeColors.Text3, fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp).align(if (isMe) Alignment.End else Alignment.Start),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePreview(data: ByteArray, mime: String?) {
    // Animate GIF / animated-WebP stickers via ImageDecoder (API 28+).
    val animated = (mime == "image/gif" || mime == "image/webp") &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
    if (animated) {
        AnimatedImage(data)
        return
    }
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

@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.P)
@Composable
private fun AnimatedImage(data: ByteArray) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .clip(RoundedCornerShape(10.dp)),
        factory = { ctx ->
            val iv = android.widget.ImageView(ctx).apply {
                adjustViewBounds = true
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }
            runCatching {
                val src = android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(data))
                val drawable = android.graphics.ImageDecoder.decodeDrawable(src)
                iv.setImageDrawable(drawable)
                (drawable as? android.graphics.drawable.AnimatedImageDrawable)?.apply {
                    repeatCount = android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE
                    start()
                }
            }
            iv
        },
    )
}

@Composable
private fun FileRow(msg: ChatMessage) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when {
                msg.mime?.startsWith("image/") == true -> Icons.Filled.Image
                msg.mime?.startsWith("video/") == true -> Icons.Filled.PlayArrow
                else -> Icons.Filled.InsertDriveFile
            },
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

/**
 * MediaPlayer picks its decoder by sniffing the file's actual bytes, but on
 * some devices/OS versions it still leans on the extension as a hint — and
 * with a wrong extension can fail (or throw) instead of falling back to
 * content sniffing. Voice notes aren't always our own AAC/mp4 recordings:
 * the desktop client sends raw WAV. Pick the extension from the real mime
 * type instead of hardcoding ".m4a" for every audio attachment.
 */
internal fun audioExtensionForMime(mime: String?): String = when (mime) {
    "audio/wav", "audio/x-wav" -> ".wav"
    "audio/mp4", "audio/m4a" -> ".m4a"
    "audio/aac" -> ".aac"
    "audio/mpeg" -> ".mp3"
    "audio/ogg" -> ".ogg"
    "audio/webm" -> ".weba"
    else -> ".m4a"
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
                            val f = java.io.File(context.cacheDir, "play_${msg.fileId}${audioExtensionForMime(msg.mime)}")
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

/** Same extension-hint reasoning as [audioExtensionForMime], for containers. */
internal fun videoExtensionForMime(mime: String?): String = when (mime) {
    "video/mp4" -> ".mp4"
    "video/quicktime" -> ".mov"
    "video/webm" -> ".webm"
    "video/x-matroska" -> ".mkv"
    "video/3gpp" -> ".3gp"
    "video/mpeg" -> ".mpeg"
    "video/x-msvideo" -> ".avi"
    else -> ".mp4"
}

/**
 * Inline video playback, in the bubble.
 *
 * Uses the framework's VideoView rather than pulling in a player library: this
 * only ever plays a local file the app already holds in memory, and the APK is
 * carrying enough native payload as it is.
 *
 * The file never leaves the app: it is written to the private cache and handed
 * to the player by path, never through an Intent to whatever video app the
 * phone has installed. [wipeAndExit] clears those cache files on panic.
 *
 * While the bubble is blurred the surface is not composed at all — a SurfaceView
 * punches through the Compose hierarchy, so Modifier.blur would leave the video
 * playing in the clear behind a blurred bubble.
 */
@Composable
private fun VideoPlayer(msg: ChatMessage, blurred: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var playing by remember { mutableStateOf(false) }
    val view = remember { mutableStateOf<android.widget.VideoView?>(null) }

    val cacheFile = remember(msg.fileId) {
        runCatching {
            java.io.File(context.cacheDir, "play_${msg.fileId}${videoExtensionForMime(msg.mime)}")
                .also { if (!it.exists()) it.writeBytes(msg.fileData ?: ByteArray(0)) }
        }.getOrNull()
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { view.value?.stopPlayback() } }
    }

    Box(
        modifier = Modifier
            .width(240.dp)
            .height(140.dp)
            .background(HazeColors.Bg, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (!blurred && cacheFile != null) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx: android.content.Context ->
                    android.widget.VideoView(ctx).apply {
                        setVideoPath(cacheFile.absolutePath)
                        setOnPreparedListener { it.isLooping = false }
                        setOnCompletionListener { playing = false }
                        view.value = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Play/pause overlay — shown while paused, and always while blurred so
        // the bubble still reads as a video.
        if (!playing || blurred) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(HazeColors.Accent.copy(alpha = 0.9f), CircleShape)
                    .clickable(enabled = !blurred) {
                        val v = view.value ?: return@clickable
                        if (v.isPlaying) { v.pause(); playing = false } else { v.start(); playing = true }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "play",
                    tint = HazeColors.Bg,
                    modifier = Modifier.size(26.dp),
                )
            }
        } else {
            // Tap the frame to pause once it is running.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        val v = view.value ?: return@clickable
                        v.pause(); playing = false
                    },
            )
        }
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
    replyTo: ChatMessage?,
    myNick: String,
    enterToSend: Boolean,
    onClearReply: () -> Unit,
    onSend: (String) -> Unit,
    onTyping: (Boolean) -> Unit,
    onSendFile: (android.net.Uri) -> Unit,
    onSendBytes: (ByteArray, String, String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var text by remember { mutableStateOf("") }
    var typingSent by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    // Holds the native EditText so we can read/clear it (keyboard GIF/sticker support).
    val editHolder = remember { EditTextHolder() }

    // A GIF / sticker the keyboard committed → forward it through file transfer.
    val onCommitContent: (android.net.Uri, String) -> Unit = { uri, mime ->
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null && bytes.isNotEmpty()) {
                val ext = when {
                    mime.contains("gif") -> "gif"
                    mime.contains("webp") -> "webp"
                    mime.contains("png") -> "png"
                    else -> "jpg"
                }
                onSendBytes(bytes, "sticker.$ext", mime)
            }
        }
    }

    fun doSubmit() {
        val t = (editHolder.view?.text?.toString() ?: text).trim()
        if (t.isNotBlank()) onSend(t)
        editHolder.view?.setText("")
        text = ""; typingSent = false; onTyping(false)
    }
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(InputBarBg)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding(),
    ) {
        // Reply preview — shown above the input while composing a reply.
        if (replyTo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(32.dp)
                        .background(HazeColors.Green, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Replying to ${if (replyTo.nick == myNick) "yourself" else replyTo.nick}",
                        color = HazeColors.Green, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        replyTo.content.ifEmpty {
                            when {
                                replyTo.mime?.startsWith("image/") == true -> "[Image]"
                                replyTo.mime?.startsWith("audio/") == true -> "[Voice note]"
                                replyTo.mime?.startsWith("video/") == true -> "[Video]"
                                replyTo.isFile -> "[File]"
                                else -> ""
                            }
                        },
                        color = HazeColors.Text3, fontSize = 12.sp,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier.size(32.dp).clickable(onClick = onClearReply),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "cancel reply", tint = HazeColors.Text3, modifier = Modifier.size(18.dp))
                }
            }
        }

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                // Native EditText (via AndroidView) so the keyboard can commit
                // GIFs / stickers (Commit Content API) — Compose text fields can't.
                RichMessageField(
                    enabled = enabled,
                    enterToSend = enterToSend,
                    holder = editHolder,
                    onTextChanged = { s ->
                        text = s
                        val nowTyping = s.isNotEmpty()
                        if (nowTyping != typingSent) { typingSent = nowTyping; onTyping(nowTyping) }
                    },
                    onSubmit = { doSubmit() },
                    onCommit = onCommitContent,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        IconButton(
            onClick = { doSubmit() },
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

/** Keeps a reference to the native EditText so callers can read/clear its text. */
private class EditTextHolder {
    var view: android.widget.EditText? = null
}

/**
 * A native [android.widget.EditText] hosted in Compose. Unlike Compose text
 * fields it declares accepted content MIME types and handles the keyboard's
 * Commit Content API, so GIFs and stickers (Gboard / Giphy, etc.) can be sent.
 */
@Composable
private fun RichMessageField(
    enabled: Boolean,
    enterToSend: Boolean,
    holder: EditTextHolder,
    onTextChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onCommit: (android.net.Uri, String) -> Unit,
) {
    val mimeTypes = arrayOf("image/gif", "image/png", "image/webp", "image/jpeg")
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            val et = object : android.widget.EditText(ctx) {
                override fun onCreateInputConnection(
                    outAttrs: android.view.inputmethod.EditorInfo,
                ): android.view.inputmethod.InputConnection? {
                    val ic = super.onCreateInputConnection(outAttrs) ?: return null
                    androidx.core.view.inputmethod.EditorInfoCompat.setContentMimeTypes(outAttrs, mimeTypes)
                    val listener = androidx.core.view.inputmethod.InputConnectionCompat.OnCommitContentListener { info, flags, _ ->
                        val granted =
                            if ((flags and androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                                runCatching { info.requestPermission() }.isSuccess
                            } else true
                        if (granted) {
                            runCatching { onCommit(info.contentUri, info.description.getMimeType(0)) }
                        }
                        true
                    }
                    return androidx.core.view.inputmethod.InputConnectionCompat.createWrapper(ic, outAttrs, listener)
                }
            }
            et.background = null
            et.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            et.setTextColor(0xFFF0F0F0.toInt())
            et.setHintTextColor(0xFF505050.toInt())
            et.hint = "Type a message…"
            et.textSize = 15f
            et.setPadding(0, 0, 0, 0)
            et.maxLines = 5
            et.filters = arrayOf(android.text.InputFilter.LengthFilter(4000))
            applyInputConfig(et, enterToSend)
            et.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) { onSubmit(); true } else false
            }
            et.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    onTextChanged(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            holder.view = et
            et
        },
        update = { et ->
            et.isEnabled = enabled
            applyInputConfig(et, enterToSend)
        },
    )
}

private fun applyInputConfig(et: android.widget.EditText, enterToSend: Boolean) {
    val base = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    if (enterToSend) {
        et.inputType = base
        et.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
    } else {
        et.inputType = base or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        et.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NONE
    }
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

/**
 * Copy [text] to the clipboard, flagging it sensitive so Android 13+ hides the
 * content in its clipboard preview overlay (no giant popup showing the message).
 */
private fun copySensitive(context: android.content.Context, label: String, text: String) {
    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as? android.content.ClipboardManager ?: return
    val clip = android.content.ClipData.newPlainText(label, text)
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = android.os.PersistableBundle().apply {
            // ClipDescription.EXTRA_IS_SENSITIVE
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    cm.setPrimaryClip(clip)
}
