package com.haze.mobile

import android.os.Bundle
import android.view.WindowManager
import com.haze.mobile.BuildConfig
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haze.mobile.ui.ChatScreen
import com.haze.mobile.ui.ChatViewModel
import com.haze.mobile.ui.ConnectScreen
import com.haze.mobile.ui.Screen
import com.haze.mobile.ui.VaultScreen
import com.haze.mobile.ui.theme.HazeTheme

class MainActivity : ComponentActivity() {

    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Block screenshots, screen recording, and the recent-apps thumbnail so
        // no other app (or the OS/Google) can capture chat contents.
        // Disabled in debug builds so the UI can be inspected during development.
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        setContent {
            HazeTheme {
                val state by vm.ui.collectAsStateWithLifecycle()
                when (state.screen) {
                    Screen.Connect -> ConnectScreen(
                        state = state,
                        onJoin = { onion, nick, password -> vm.connect(onion, nick, password) },
                        onHost = { nick, password -> vm.host(nick, password) },
                        onOpenVault = { vm.openVault(Screen.Connect) },
                        onCancel = vm::cancelAdd,
                    )
                    Screen.Chat -> ChatScreen(
                        state = state,
                        onSend = vm::sendChat,
                        onTyping = vm::setTyping,
                        onLeave = vm::leaveActive,
                        onPanic = vm::panic,
                        onSendFile = vm::sendFile,
                        onSendBytes = vm::sendFileBytes,
                        onSaveVault = vm::saveToVault,
                        onOpenVault = { vm.openVault(Screen.Chat) },
                        onNewSession = vm::newSession,
                        onSwitchSession = vm::switchSession,
                        onKickUser = vm::kickUser,
                        onBlockUser = vm::toggleBlock,
                    )
                    Screen.Vault -> VaultScreen(
                        state = state,
                        onExit = vm::exitVault,
                        onOpenSession = vm::loadVaultSession,
                        onDeleteSession = vm::deleteVaultSession,
                        onCloseSession = vm::closeVaultSession,
                        onClearError = vm::clearVaultError,
                    )
                }
            }
        }
    }
}
