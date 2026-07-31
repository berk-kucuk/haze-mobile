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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.haze.mobile.R
import com.haze.mobile.ui.theme.HazeColors

@Composable
fun ConnectScreen(
    state: ChatUiState,
    onJoin: (onion: String, nick: String, password: String) -> Unit,
    onHost: (nick: String, password: String) -> Unit,
    onOpenVault: () -> Unit,
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit,
) {
    var hostMode by rememberSaveable { mutableStateOf(true) }
    var onion by rememberSaveable { mutableStateOf("") }
    var nick by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HazeColors.Bg)
            // safeDrawing = system bars + display cutout + IME, so the form
            // stays clear of a notch in landscape and is pushed up by the
            // keyboard instead of sitting behind it.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HazeColors.Surface, RoundedCornerShape(20.dp))
                .border(1.dp, HazeColors.Border2, RoundedCornerShape(20.dp))
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Back to existing chats (only when adding another session)
            if (state.addingSession) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .clickable(onClick = onCancel)
                        .padding(vertical = 2.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "back",
                        tint = HazeColors.Text2,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Back to chats", color = HazeColors.Text2, fontSize = 12.sp)
                }
            }

            // Wordmark — same PNG asset as the desktop app
            Image(
                painter = painterResource(id = R.drawable.haze_wordmark),
                contentDescription = "Haze",
                modifier = Modifier.height(47.dp).fillMaxWidth(0.63f),
                contentScale = ContentScale.Fit,
            )

            // Tagline — matches desktop "ANONYMOUS · ENCRYPTED · NO TRACE"
            Text(
                "ANONYMOUS  ·  ENCRYPTED  ·  NO TRACE",
                color = HazeColors.Text3,
                fontSize = 8.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(8.dp))

            // Mode selector — Host (start a room) vs Join (connect to one)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ModeButton("HOST", selected = hostMode, modifier = Modifier.weight(1f)) { hostMode = true }
                ModeButton("JOIN", selected = !hostMode, modifier = Modifier.weight(1f)) { hostMode = false }
            }

            HazeField(value = nick, onValueChange = { nick = it }, placeholder = "Nickname")
            if (!hostMode) {
                HazeField(value = onion, onValueChange = { onion = it }, placeholder = "Session address")
            }
            HazeField(
                value = password,
                onValueChange = { password = it },
                placeholder = if (hostMode) "Set a session password (optional)" else "Session password (if required)",
                isPassword = true,
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { if (hostMode) onHost(nick, password) else onJoin(onion, nick, password) },
                enabled = !state.connecting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HazeColors.Accent,
                    contentColor = HazeColors.Bg,
                    disabledContainerColor = HazeColors.Surface3,
                    disabledContentColor = HazeColors.Text3,
                ),
            ) {
                if (state.connecting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = HazeColors.Text2)
                    Spacer(Modifier.width(10.dp))
                    Text(state.status.ifEmpty { "Connecting…" }, fontSize = 13.sp)
                } else {
                    Text(if (hostMode) "Start Hosting" else "Join Chat", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            state.error?.let {
                Text(it, color = HazeColors.Red, fontSize = 11.sp, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(2.dp))

            Text(
                if (hostMode) "You'll get a session address to share. Built-in Tor — no setup."
                else "All communication is encrypted and anonymous.",
                color = HazeColors.Text3,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onOpenVault)
                    .padding(6.dp),
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = HazeColors.Text3,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Secret Vault",
                    color = HazeColors.Text2,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                )
            }
        }

        // Settings gear — top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "settings",
                tint = HazeColors.Text3,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .background(
                if (selected) HazeColors.Surface3 else HazeColors.Surface2,
                RoundedCornerShape(10.dp),
            )
            .border(
                1.dp,
                if (selected) HazeColors.Text3 else HazeColors.Border,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) HazeColors.Accent else HazeColors.Text3,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun HazeField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = HazeColors.Text3, fontSize = 14.sp) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = HazeColors.Surface2,
            unfocusedContainerColor = HazeColors.Surface2,
            focusedBorderColor = HazeColors.Border2,
            unfocusedBorderColor = HazeColors.Border,
            focusedTextColor = HazeColors.Accent,
            unfocusedTextColor = HazeColors.Text,
            cursorColor = HazeColors.Accent,
        ),
    )
}
