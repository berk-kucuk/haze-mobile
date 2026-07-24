package com.haze.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haze.mobile.storage.VaultStore
import com.haze.mobile.ui.theme.HazeColors

@Composable
fun VaultScreen(
    state: ChatUiState,
    onExit: () -> Unit,
    onOpenSession: (VaultStore.Entry, String) -> Unit,
    onDeleteSession: (VaultStore.Entry) -> Unit,
    onCloseSession: () -> Unit,
    onClearError: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HazeColors.Bg)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        val opened = state.vaultOpenMessages

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(HazeColors.Surface)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).clickable { if (opened != null) onCloseSession() else onExit() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back", tint = HazeColors.Text2, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.Lock, contentDescription = null, tint = HazeColors.Green, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (opened != null) state.vaultOpenName.uppercase() else "SECRET VAULT",
                color = HazeColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
            )
        }

        if (opened != null) {
            VaultChatView(opened)
        } else {
            VaultList(
                sessions = state.vaultSessions,
                error = state.vaultError,
                onOpen = onOpenSession,
                onDelete = onDeleteSession,
                onClearError = onClearError,
            )
        }
    }
}

@Composable
private fun VaultList(
    sessions: List<VaultStore.Entry>,
    error: String?,
    onOpen: (VaultStore.Entry, String) -> Unit,
    onDelete: (VaultStore.Entry) -> Unit,
    onClearError: () -> Unit,
) {
    var pwFor by remember { mutableStateOf<VaultStore.Entry?>(null) }

    if (sessions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No saved chats.\nSave a conversation from the chat screen.",
                color = HazeColors.Text3, fontSize = 13.sp, textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(sessions) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HazeColors.Surface2, RoundedCornerShape(12.dp))
                    .border(1.dp, HazeColors.Border, RoundedCornerShape(12.dp))
                    .clickable { pwFor = entry }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = HazeColors.Text3, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.displayName, color = HazeColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(entry.timestamp, color = HazeColors.Text3, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier.size(32.dp).clickable { onDelete(entry) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "delete", tint = HazeColors.Text3, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    pwFor?.let { entry ->
        PasswordDialog(
            title = entry.displayName,
            error = error,
            onDismiss = { pwFor = null; onClearError() },
            onSubmit = { pw -> onOpen(entry, pw) },
        )
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HazeColors.Surface, RoundedCornerShape(16.dp))
                .border(1.dp, HazeColors.Border2, RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Text("Unlock “$title”", color = HazeColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Enter this chat's password.", color = HazeColors.Text3, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = pw,
                onValueChange = { pw = it },
                placeholder = { Text("Password", color = HazeColors.Text3) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = HazeColors.Surface2,
                    unfocusedContainerColor = HazeColors.Surface2,
                    focusedBorderColor = HazeColors.Border2,
                    unfocusedBorderColor = HazeColors.Border,
                    focusedTextColor = HazeColors.Text,
                    unfocusedTextColor = HazeColors.Text,
                    cursorColor = HazeColors.Accent,
                ),
            )
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(error, color = HazeColors.Red, fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = HazeColors.Text2) }
                TextButton(onClick = { if (pw.isNotBlank()) onSubmit(pw) }) {
                    Text("Unlock", color = HazeColors.Green, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun VaultFileContent(msg: ChatMessage) {
    val context = androidx.compose.ui.platform.LocalContext.current
    when {
        msg.mime?.startsWith("image/") == true && msg.fileData != null -> {
            val bmp = remember(msg.fileData) {
                runCatching { android.graphics.BitmapFactory.decodeByteArray(msg.fileData, 0, msg.fileData.size)?.asImageBitmap() }.getOrNull()
            }
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp, contentDescription = "image",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            } else Text("🖼  image", color = HazeColors.Text, fontSize = 13.sp)
        }
        msg.mime?.startsWith("audio/") == true && msg.fileData != null -> {
            var playing by remember { mutableStateOf(false) }
            val player = remember { android.media.MediaPlayer() }
            androidx.compose.runtime.DisposableEffect(Unit) { onDispose { runCatching { player.release() } } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).background(HazeColors.Accent, androidx.compose.foundation.shape.CircleShape)
                        .clickable {
                            if (playing) { runCatching { player.pause() }; playing = false }
                            else runCatching {
                                val f = java.io.File(context.cacheDir, "vault_play_${msg.hashCode()}.m4a")
                                if (!f.exists()) f.writeBytes(msg.fileData)
                                player.reset(); player.setDataSource(f.absolutePath); player.prepare()
                                player.setOnCompletionListener { playing = false }; player.start(); playing = true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null, tint = HazeColors.Bg, modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("Voice note", color = HazeColors.Text, fontSize = 13.sp)
            }
        }
        else -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = HazeColors.Text2, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(msg.filename ?: "file", color = HazeColors.Text, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun VaultChatView(messages: List<ChatMessage>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        items(messages) { msg ->
            if (msg.isSystem) {
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    Text(msg.content, color = HazeColors.Text4, fontSize = 10.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                }
            } else {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start) {
                    if (!msg.isMe) {
                        Text(msg.nick.uppercase(), color = HazeColors.Text4, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .background(
                                if (msg.isMe) HazeColors.Accent.copy(alpha = 0.09f) else HazeColors.Accent.copy(alpha = 0.05f),
                                RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 13.dp, vertical = 9.dp),
                    ) {
                        Column {
                            if (msg.isFile) VaultFileContent(msg)
                            else Text(msg.content, color = HazeColors.Text, fontSize = 13.sp)
                            Text(msg.timestamp, color = HazeColors.Text3, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp).align(if (msg.isMe) Alignment.End else Alignment.Start))
                        }
                    }
                }
            }
        }
    }
}
