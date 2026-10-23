package com.njxai.chessintel.data

import com.njxai.chessintel.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin client for the LibreChat REST API backing the ChessIntel agent.
 *
 * Endpoints used:
 *  - POST /api/auth/login   -> { "token": "<jwt>", "user": { ... } }
 *  - POST /api/agents/chat  -> Server-Sent Events (SSE) stream
 */
class LibreChatApi {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // SSE responses never "finish" on their own
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Authenticates and returns the JWT access token. */
    suspend fun login(email: String, password: String): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()

        val request = Request.Builder()
            .url("${Config.BASE_URL}/api/auth/login")
            .header("User-Agent", Config.USER_AGENT)
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(body).optString("message") }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Login failed (HTTP ${response.code})"
                throw IOException(message)
            }

            val data = JSONObject(body)
            if (data.optBoolean("twoFAPending")) {
                throw IOException("This account requires two-factor authentication.")
            }

            val token = data.optString("token")
            if (token.isEmpty()) throw IOException("Login response did not include a token.")
            token
        }
    }

    /**
     * Starts a streaming chat with the ChessIntel agent.
     *
     * [onToken] receives the complete accumulated reply each time a new token arrives.
     * [onDone] receives the final reply exactly once.
     * [onError] receives any failure.
     */
    fun streamChat(
        token: String,
        prompt: String,
        onToken: (String) -> Unit,
        onDone: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        // LibreChat's agent chat reads `agent_id` and `text` from the body.
        val payload = JSONObject()
            .put("endpoint", "agents")
            .put("agent_id", Config.AGENT_ID)
            .put("conversationId", JSONObject.NULL)
            .put("text", prompt)
            .toString()

        val request = Request.Builder()
            .url("${Config.BASE_URL}/api/agents/chat")
            .header("User-Agent", Config.USER_AGENT)
            .header("Authorization", "Bearer $token")
            .header("Accept", "text/event-stream")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e)

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val body = runCatching { response.body?.string() }.getOrNull().orEmpty()
                    onError(IOException("Chat failed (HTTP ${response.code}): $body"))
                    return
                }

                val source = response.body?.source()
                if (source == null) {
                    onError(IOException("Empty response body"))
                    return
                }

                response.use {
                    val text = StringBuilder()
                    try {
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            // LibreChat writes `event: message` then `data: {...}`.
                            if (!line.startsWith("data:")) continue
                            val data = line.removePrefix("data:").trim()
                            if (data.isEmpty() || data == "[DONE]") continue

                            val obj = try {
                                JSONObject(data)
                            } catch (_: Exception) {
                                continue
                            }

                            // Terminal event: `{"final": true, "responseMessage": {...}}`
                            if (obj.optBoolean("final")) {
                                val finalText = obj.optJSONObject("responseMessage")
                                    ?.let { it.optString("text").ifBlank { textFromContentParts(it) } }
                                if (!finalText.isNullOrBlank()) {
                                    text.setLength(0)
                                    text.append(finalText)
                                }
                                break
                            }

                            // Streaming events are wrapped: `{"event": "...", "data": {...}}`
                            val eventName = obj.optString("event")
                            val payload = obj.optJSONObject("data") ?: obj
                            val chunk = when (eventName) {
                                "on_message_delta" -> textDelta(payload.optJSONObject("delta"))
                                "on_chat_model_stream" -> textDelta(payload.optJSONObject("chunk") ?: payload)
                                "on_reasoning_delta" -> textDelta(payload.optJSONObject("delta"))
                                else -> ""
                            }
                            if (chunk.isNotEmpty()) {
                                text.append(chunk)
                                onToken(text.toString())
                            }
                        }
                    } catch (e: Exception) {
                        onError(e)
                        return
                    }
                    onDone(text.toString())
                }
            }
        })
    }

    /**
     * Extracts a text chunk from a stream delta.
     *
     * LibreChat sends content as either a single object
     * (`{"type":"text","text":"..."}`) or an array of those objects.
     */
    private fun textDelta(delta: JSONObject?): String {
        if (delta == null || !delta.has("content")) return ""
        val content = delta.opt("content")
        return when (content) {
            is JSONObject -> content.optString("text")
            is JSONArray -> if (content.length() > 0) {
                content.optJSONObject(0)?.optString("text").orEmpty()
            } else {
                ""
            }
            else -> ""
        }
    }

    /** Builds plain text from a message's `content` parts when `message.text` is absent. */
    private fun textFromContentParts(message: JSONObject?): String {
        if (message == null) return ""
        val content = message.optJSONArray("content") ?: return ""
        val parts = StringBuilder()
        for (i in 0 until content.length()) {
            val part = content.optJSONObject(i) ?: continue
            if (part.has("text")) parts.append(part.optString("text"))
        }
        return parts.toString()
    }
}
