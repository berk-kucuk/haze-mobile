package com.haze.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haze.mobile.ui.ChatScreen
import com.haze.mobile.ui.ChatViewModel
import com.haze.mobile.ui.ConnectScreen
import com.haze.mobile.ui.Screen
import com.haze.mobile.ui.SettingsScreen
import com.haze.mobile.ui.VaultScreen
import com.haze.mobile.ui.theme.HazeTheme

class MainActivity : ComponentActivity() {

    private val vm: ChatViewModel by viewModels()

    // Ask for notification permission so the ongoing "connected" notification shows.
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Block screenshots, screen recording, and the recent-apps thumbnail by
        // default so no other app (or the OS/Google) can capture chat contents.
        // The user may opt in via Settings → the flag is then toggled below.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        setContent {
            HazeTheme {
                val state by vm.ui.collectAsStateWithLifecycle()
                // Apply the screenshot preference: allow → clear FLAG_SECURE.
                LaunchedEffect(state.settings.allowScreenshots) {
                    if (state.settings.allowScreenshots) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    }
                }
                // System back navigation between screens (instead of exiting).
                BackHandler(enabled = state.screen == Screen.Settings) { vm.exitSettings() }
                BackHandler(enabled = state.screen == Screen.Vault) {
                    if (state.vaultOpenMessages != null) vm.closeVaultSession() else vm.exitVault()
                }
                BackHandler(enabled = state.screen == Screen.Connect && state.addingSession) {
                    vm.cancelAdd()
                }

                when (state.screen) {
                    Screen.Connect -> ConnectScreen(
                        state = state,
                        onJoin = { onion, nick, password -> vm.connect(onion, nick, password) },
                        onHost = { nick, password -> vm.host(nick, password) },
                        onOpenVault = { vm.openVault(Screen.Connect) },
                        onOpenSettings = { vm.openSettings(Screen.Connect) },
                        onCancel = vm::cancelAdd,
                    )
                    Screen.Chat -> ChatScreen(
                        state = state,
                        onSend = vm::sendChat,
                        onTyping = vm::setTyping,
                        onLeave = vm::leaveActive,
                        onPanic = vm::panic,
                        onConfirmReceivedPanic = vm::confirmReceivedPanicWipe,
                        onDismissReceivedPanic = vm::dismissReceivedPanic,
                        onReconnect = vm::reconnect,
                        onSendFile = vm::sendFile,
                        onSendBytes = vm::sendFileBytes,
                        onSaveVault = vm::saveToVault,
                        onOpenVault = { vm.openVault(Screen.Chat) },
                        onOpenSettings = { vm.openSettings(Screen.Chat) },
                        onDeleteMessage = vm::deleteMessage,
                        onEditMessage = vm::editMessage,
                        onNewSession = vm::newSession,
                        onSwitchSession = vm::switchSession,
                        onKickUser = vm::kickUser,
                        onBlockUser = vm::toggleBlock,
                    )
                    Screen.Vault -> VaultScreen(
                        state = state,
                        onExit = vm::exitVault,
                        onUnlock = vm::unlockVault,
                        onOpenSession = vm::loadVaultSession,
                        onDeleteSession = vm::deleteVaultSession,
                        onCloseSession = vm::closeVaultSession,
                        onClearError = vm::clearVaultError,
                    )
                    Screen.Settings -> SettingsScreen(
                        settings = state.settings,
                        onChange = vm::updateSettings,
                        onExit = vm::exitSettings,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onAppForeground()
    }

    override fun onStop() {
        super.onStop()
        vm.onAppBackground()
    }
}
