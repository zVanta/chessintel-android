# ChessIntel Coach — Android

Native Android chat client for your **ChessIntel** agent running on LibreChat
(`njxai.com`). The app logs into your LibreChat account and chats with the
ChessIntel agent (LLM + MCP tools: game finder, NJCC lessons, homework PDFs,
engine analysis) with a mobile-friendly interface.

## Architecture

The ChessIntel brain (LLM + tools) already lives on your LibreChat server. This
app is a thin native client for LibreChat's existing **public HTTP API**:

| Purpose        | Endpoint                  | Notes                                    |
| -------------- | ------------------------- | ---------------------------------------- |
| Sign in        | `POST /api/auth/login`    | body `{ email, password }` → `{ token }` |
| Chat (stream)  | `POST /api/agents/chat`   | Bearer token, Server-Sent Events         |

> There is no separate server to run. The "public API" is LibreChat's own API,
> already exposed at `https://njxai.com`.

Two LibreChat quirks this app handles for you:
1. **User-Agent check** — LibreChat rejects non-browser requests with
   `{"message":"Illegal request"}`, so every request sends a Chrome UA.
2. **SSE format** — replies stream as named `event: message` frames carrying
   `on_message_delta` tokens and a final `{"final":true, ...}` frame.

## ⚠️ Required one-time setup: Cloudflare

`njxai.com` is behind Cloudflare, and the API endpoints are currently protected
by a Cloudflare **Managed Challenge** ("Just a moment…", HTTP 403). Browsers can
solve it, but a native Android app cannot — so the app will get `403` until you
allow the API paths through.

The API is already protected by authentication (`/api/auth/login` is
rate-limited and requires credentials; `/api/agents/chat` requires the JWT), so
skipping the challenge there is safe.

**Cloudflare dashboard → your `njxai.com` zone → Security → WAF → Custom rules:**

1. Click **Create rule**, name it e.g. `Allow LibreChat API`.
2. **When incoming requests match…**
   - Field: `URI Path` — Operator: `starts with` — Value: `/api/`
   - *(optionally add)* `AND` → `Request method` `equals` `POST`
3. **Then…** choose **Skip** → select **Managed Challenge** (and optionally
   **Bot Fight Mode** / **Security Level**).
4. Deploy.

After deploying, re-test from a command line:

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST "https://njxai.com/api/auth/login" \
  -H "Content-Type: application/json" \
  -H "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36" \
  --data '{"email":"you@example.com","password":"wrong-password"}'
```

Expected result: `400` (invalid credentials) instead of `403`.

## Requirements

- Android Studio (Koala or newer) or a Gradle 8.7 + JDK 17 toolchain
- An Android device/emulator with Android 7.0 (API 24) or later

## Download the APK

The easiest way to get an installable APK is from the
[Releases](https://github.com/zVanta/chessintel-android/releases) page — every
`v*` tag builds a signed-in-debug APK and attaches it to the release. Download
`ChessIntel-debug.apk` to your phone and install it (allow "unknown sources").

## Build & run

Open this folder in Android Studio and press **Run**. (Android Studio generates
the Gradle wrapper on first sync; after that the command line works too.)

```bash
# macOS/Linux (also works in Git Bash on Windows)
./gradlew assembleDebug

# Windows PowerShell
.\gradlew.bat assembleDebug
```

Then install `app/build/outputs/apk/debug/app-debug.apk` on your device, or run
`.\gradlew.bat installDebug` with a connected device/emulator.

Sign in with the same email/password you use on `njxai.com`. Messages that
contain links to generated lesson/homework PDFs are tappable and open in the
browser.

## Configuration

Everything you may need to change is in
[`app/src/main/java/com/njxai/chessintel/Config.kt`](app/src/main/java/com/njxai/chessintel/Config.kt):

```kotlin
object Config {
    const val BASE_URL = "https://njxai.com"
    const val AGENT_ID = "agent_oyvghVijlzhuGXazjSQqv"  // LibreChat agents.id field
}
```

`AGENT_ID` is the agent's `id` field in MongoDB (`LibreChat.agents.findOne({name:
"ChessIntel"}).id`), **not** its Mongo `_id`.

## Project layout

```
app/src/main/java/com/njxai/chessintel/
├── Config.kt                 # base URL + agent id + User-Agent
├── MainActivity.kt           # top-level navigation (login vs chat)
├── data/
│   ├── LibreChatApi.kt       # login + SSE streaming chat client
│   └── AuthStore.kt          # JWT persistence
└── ui/
    ├── ChatViewModel.kt      # state + streaming orchestration
    ├── LoginScreen.kt
    ├── ChatScreen.kt
    ├── MarkdownText.kt       # tappable links in replies
    └── theme/Theme.kt
```
