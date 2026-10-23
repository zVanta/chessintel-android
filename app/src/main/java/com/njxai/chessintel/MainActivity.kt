package com.njxai.chessintel

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.njxai.chessintel.ui.ChatScreen
import com.njxai.chessintel.ui.ChatViewModel
import com.njxai.chessintel.ui.LoginScreen
import com.njxai.chessintel.ui.theme.ChessIntelTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChessIntelTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                if (state.isLoggedIn) {
                    ChatScreen(
                        messages = state.messages,
                        isBusy = state.isBusy,
                        onSend = viewModel::send,
                        onLogout = viewModel::logout,
                        onOpenUrl = { url ->
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                    )
                } else {
                    LoginScreen(
                        isLoggingIn = state.isLoggingIn,
                        error = state.loginError,
                        onLogin = viewModel::login,
                    )
                }
            }
        }
    }
}
