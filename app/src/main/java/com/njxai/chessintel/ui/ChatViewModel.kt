package com.njxai.chessintel.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.njxai.chessintel.data.AuthStore
import com.njxai.chessintel.data.LibreChatApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val role: Role,
    val text: String,
    val isStreaming: Boolean = false,
) {
    enum class Role { USER, ASSISTANT }
}

data class ChatUiState(
    val isLoggedIn: Boolean = false,
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isBusy: Boolean = false,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val api = LibreChatApi()
    private val authStore = AuthStore(application)

    private val _state = MutableStateFlow(
        ChatUiState(isLoggedIn = !authStore.token.isNullOrBlank())
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoggingIn = true, loginError = null) }
            try {
                val token = api.login(email.trim(), password)
                authStore.token = token
                authStore.email = email.trim()
                _state.update { it.copy(isLoggingIn = false, isLoggedIn = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoggingIn = false, loginError = e.message ?: "Login failed")
                }
            }
        }
    }

    fun logout() {
        authStore.clear()
        _state.value = ChatUiState(isLoggedIn = false)
    }

    fun send(prompt: String) {
        val text = prompt.trim()
        if (text.isEmpty()) return
        val token = authStore.token ?: run {
            _state.value = ChatUiState(isLoggedIn = false)
            return
        }

        _state.update {
            it.copy(
                messages = it.messages + ChatMessage(ChatMessage.Role.USER, text),
                isBusy = true,
            )
        }

        api.streamChat(
            token = token,
            prompt = text,
            onToken = { partial ->
                viewModelScope.launch { updateAssistant(partial, isStreaming = true) }
            },
            onDone = { final ->
                viewModelScope.launch {
                    updateAssistant(final, isStreaming = false)
                    _state.update { it.copy(isBusy = false) }
                }
            },
            onError = { e ->
                viewModelScope.launch {
                    updateAssistant(
                        "Sorry, something went wrong: ${e.message}",
                        isStreaming = false,
                    )
                    _state.update { it.copy(isBusy = false) }
                }
            },
        )
    }

    private fun updateAssistant(text: String, isStreaming: Boolean) {
        _state.update { state ->
            val messages = state.messages.toMutableList()
            val last = messages.lastOrNull()
            if (last != null && last.role == ChatMessage.Role.ASSISTANT) {
                messages[messages.size - 1] = last.copy(text = text, isStreaming = isStreaming)
            } else {
                messages.add(ChatMessage(ChatMessage.Role.ASSISTANT, text, isStreaming))
            }
            state.copy(messages = messages)
        }
    }
}
