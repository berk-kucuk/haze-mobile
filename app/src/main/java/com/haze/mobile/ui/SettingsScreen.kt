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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haze.mobile.net.TorBridges
import com.haze.mobile.storage.SettingsStore
import com.haze.mobile.ui.theme.HazeColors

@Composable
fun SettingsScreen(
    settings: SettingsStore.Settings,
    onChange: (SettingsStore.Settings) -> Unit,
    onExit: () -> Unit,
) {
    var showLockDialog by remember { mutableStateOf(false) }
    var showDecoyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HazeColors.Bg)
            // safeDrawing rather than systemBars so a display cutout (incl. the
            // landscape notch) is kept clear too.
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // ── Top bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(HazeColors.Surface)
                .border(0.5.dp, HazeColors.Border, RoundedCornerShape(0.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).clickable(onClick = onExit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "back",
                    tint = HazeColors.Text2,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text("Settings", color = HazeColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("PRIVACY")

            SettingSwitch(
                icon = Icons.Filled.Visibility,
                title = "Allow screenshots",
                subtitle = "When off, screenshots, screen recording and the recent-apps preview are blocked.",
                checked = settings.allowScreenshots,
                onCheckedChange = { onChange(settings.copy(allowScreenshots = it)) },
            )

            SettingSwitch(
                icon = Icons.Filled.BlurOn,
                title = "Blur messages",
                subtitle = "Blur message contents a few seconds after they appear so people nearby can't read them. Tap a message to reveal it.",
                checked = settings.blurMessages,
                onCheckedChange = { onChange(settings.copy(blurMessages = it)) },
            )

            SettingSwitch(
                icon = Icons.Filled.Public,
                title = "Allow Tor Browser access",
                subtitle = "When you host, also serve the chat as a web page on the same .onion address, so guests can join from Tor Browser without installing Haze. Browser guests are shown as \"[web]…\" and, unlike app users, are protected only by the Tor connection — not by Haze's group encryption.",
                checked = settings.allowWebAccess,
                onCheckedChange = { onChange(settings.copy(allowWebAccess = it)) },
            )

            Spacer(Modifier.height(10.dp))
            SectionLabel("TOR CONNECTION")
            TorConnectionSection(settings = settings, onChange = onChange)

            Spacer(Modifier.height(6.dp))
            SectionLabel("VAULT LOCK")

            SettingButton(
                icon = Icons.Filled.Lock,
                title = "Set lock password",
                subtitle = if (settings.vaultLockHash.isEmpty())
                    "Vault opens directly — set a password to require it before viewing saved chats."
                else "Vault is locked — a password is required before the saved-chats list appears.",
                onClick = { showLockDialog = true },
            )

            SettingButton(
                icon = Icons.Filled.Lock,
                title = "Set decoy password",
                subtitle = "Enter this one instead, under coercion — shows an empty vault and deletes the real saved chats.",
                onClick = { showDecoyDialog = true },
            )

            Spacer(Modifier.height(6.dp))
            SectionLabel("CHAT")

            SettingSwitch(
                icon = Icons.AutoMirrored.Filled.Send,
                title = "Send typing indicators",
                subtitle = "Let others in the room see when you are typing.",
                checked = settings.sendTypingIndicators,
                onCheckedChange = { onChange(settings.copy(sendTypingIndicators = it)) },
            )

            SettingSwitch(
                icon = Icons.Filled.Schedule,
                title = "Show timestamps",
                subtitle = "Display the time under each message.",
                checked = settings.showTimestamps,
                onCheckedChange = { onChange(settings.copy(showTimestamps = it)) },
            )

            SettingSwitch(
                icon = Icons.AutoMirrored.Filled.Send,
                title = "Enter key sends",
                subtitle = "Press the keyboard send action to send instead of adding a new line.",
                checked = settings.enterToSend,
                onCheckedChange = { onChange(settings.copy(enterToSend = it)) },
            )

            Spacer(Modifier.height(10.dp))
            SectionLabel("DISAPPEARING MESSAGES")

            val disappearOptions = listOf(0 to "Off", 30 to "30s", 300 to "5m", 3600 to "1h")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                disappearOptions.forEach { (secs, label) ->
                    val selected = settings.disappearingSeconds == secs
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(
                                if (selected) HazeColors.Green.copy(alpha = 0.15f) else HazeColors.Surface3,
                                RoundedCornerShape(8.dp),
                            )
                            .border(
                                1.dp,
                                if (selected) HazeColors.Green else HazeColors.Border2,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { onChange(settings.copy(disappearingSeconds = secs)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (selected) HazeColors.Green else HazeColors.Text3,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("NOTIFICATIONS")

            SettingSwitch(
                icon = Icons.Filled.Shield,
                title = "Keep connection alive",
                subtitle = "Shows an ongoing notification so Haze stays connected in the background. Turning it off may drop sessions when the app is not open.",
                checked = settings.persistentNotification,
                onCheckedChange = { onChange(settings.copy(persistentNotification = it)) },
            )

            SettingSwitch(
                icon = Icons.Filled.Notifications,
                title = "Message notifications",
                subtitle = "Notify me when a new message arrives while the app is in the background.",
                checked = settings.messageNotifications,
                onCheckedChange = { onChange(settings.copy(messageNotifications = it)) },
            )

            SettingSwitch(
                icon = Icons.Filled.VisibilityOff,
                title = "Show content in notifications",
                subtitle = "When off, notifications say \"New message\" instead of showing the sender and text.",
                checked = settings.notificationsShowContent,
                onCheckedChange = { onChange(settings.copy(notificationsShowContent = it)) },
            )

            Spacer(Modifier.height(14.dp))
            Text(
                "HAZE PROTOCOL  ·  END-TO-END ENCRYPTED  ·  NO LOGS",
                color = HazeColors.Text4,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.Bold,
            )
        }
    }

    if (showLockDialog) {
        SetVaultPasswordDialog(
            title = "Vault lock password",
            subtitle = "Required before the saved-chats list appears. Leave blank and save to remove the lock.",
            onDismiss = { showLockDialog = false },
            onSave = { pw ->
                showLockDialog = false
                onChange(settings.copy(vaultLockHash = if (pw.isEmpty()) "" else com.haze.mobile.storage.VaultLock.makeLockHash(pw)))
            },
        )
    }

    if (showDecoyDialog) {
        SetVaultPasswordDialog(
            title = "Decoy password",
            subtitle = "Entering this password instead of the real one shows an empty vault and permanently deletes the actual saved chats. Leave blank and save to remove it.",
            onDismiss = { showDecoyDialog = false },
            onSave = { pw ->
                showDecoyDialog = false
                onChange(settings.copy(vaultDecoyHash = if (pw.isEmpty()) "" else com.haze.mobile.storage.VaultLock.makeDecoyHash(pw)))
            },
        )
    }
}

/**
 * Connection-method picker for networks that block Tor outright.
 *
 * The chosen method is read when the Tor daemon starts, and the daemon is a
 * process-wide singleton — so a change only lands after the app is restarted,
 * which the note at the bottom says.
 */
@Composable
private fun TorConnectionSection(
    settings: SettingsStore.Settings,
    onChange: (SettingsStore.Settings) -> Unit,
) {
    val mode = TorBridges.normalizeMode(settings.torBridgeMode)

    val labels = listOf(
        TorBridges.MODE_DIRECT to "Direct",
        TorBridges.MODE_VANILLA to "Bridge",
        TorBridges.MODE_OBFS4 to "obfs4",
        TorBridges.MODE_SNOWFLAKE to "Snowflake",
    )

    val description = when (mode) {
        TorBridges.MODE_VANILLA ->
            "Connect through an unlisted relay. Gets past blocked relay addresses, " +
                "but the traffic still looks like Tor to a censor inspecting it. " +
                "Your own bridge lines are required."
        TorBridges.MODE_OBFS4 ->
            "Bridges that disguise the traffic so it looks like nothing in particular. " +
                "The best choice where Tor is blocked."
        TorBridges.MODE_SNOWFLAKE ->
            "Routes through volunteers' browsers over WebRTC, hidden behind a CDN. " +
                "Works where obfs4 is already blocked; often slower."
        else ->
            "Connect straight to the Tor network. Fastest, but blocked in countries " +
                "that filter Tor."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HazeColors.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, HazeColors.Border2, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        labels.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (value, label) ->
                    val selected = mode == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(
                                if (selected) HazeColors.Green.copy(alpha = 0.15f) else HazeColors.Surface3,
                                RoundedCornerShape(8.dp),
                            )
                            .border(
                                1.dp,
                                if (selected) HazeColors.Green else HazeColors.Border2,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { onChange(settings.copy(torBridgeMode = value)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (selected) HazeColors.Green else HazeColors.Text3,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        Text(description, color = HazeColors.Text3, fontSize = 11.sp, lineHeight = 15.sp)

        if (TorBridges.usesBridges(mode)) {
            val builtin = TorBridges.BUILTIN[mode].orEmpty()
            Text(
                if (builtin.isEmpty()) "YOUR OWN BRIDGE LINES  ·  required"
                else "YOUR OWN BRIDGE LINES  ·  optional",
                color = HazeColors.Text3,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = settings.torBridgesCustom,
                onValueChange = { onChange(settings.copy(torBridgesCustom = it)) },
                placeholder = {
                    Text(
                        "obfs4 192.0.2.1:443 FINGERPRINT cert=… iat-mode=0",
                        color = HazeColors.Text4,
                        fontSize = 10.sp,
                    )
                },
                minLines = 2,
                maxLines = 5,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = HazeColors.Text,
                ),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
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
            Text(
                "One per line. Get bridges from bridges.torproject.org, from " +
                    "@GetBridgesBot on Telegram, or by mailing bridges@torproject.org.",
                color = HazeColors.Text4,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )

            // Live feedback: the same validation the connect path runs, so a bad
            // paste is caught here instead of at the next bootstrap attempt.
            val problem = remember(mode, settings.torBridgesCustom) {
                runCatching { TorBridges.validate(mode, settings.torBridgesCustom) }
                    .exceptionOrNull()?.message
            }
            if (problem != null) {
                Text(problem, color = HazeColors.Red, fontSize = 11.sp, lineHeight = 15.sp)
            } else if (TorBridges.parseLines(settings.torBridgesCustom).isEmpty() && builtin.isNotEmpty()) {
                Text(
                    "Using ${builtin.size} built-in bridges. Censors know these too — " +
                        "paste fresh ones above if they stop working.",
                    color = HazeColors.Green,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }

        Text(
            "Takes effect the next time Haze starts.",
            color = HazeColors.Text4,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun SettingButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HazeColors.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, HazeColors.Border2, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = HazeColors.Text2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = HazeColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = HazeColors.Text3, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SetVaultPasswordDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(HazeColors.Surface, RoundedCornerShape(16.dp))
                .border(1.dp, HazeColors.Border2, RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Text(title, color = HazeColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = HazeColors.Text3, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = pw,
                onValueChange = { pw = it },
                placeholder = { Text("New password", color = HazeColors.Text3) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = HazeColors.Text2) }
                TextButton(onClick = { onSave(pw) }) {
                    Text("Save", color = HazeColors.Green, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = HazeColors.Text3,
        fontSize = 10.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HazeColors.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, HazeColors.Border2, RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = HazeColors.Text2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = HazeColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = HazeColors.Text3, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = HazeColors.Bg,
                checkedTrackColor = HazeColors.Green,
                checkedBorderColor = HazeColors.Green,
                uncheckedThumbColor = HazeColors.Text3,
                uncheckedTrackColor = HazeColors.Surface3,
                uncheckedBorderColor = HazeColors.Border2,
            ),
        )
    }
}
