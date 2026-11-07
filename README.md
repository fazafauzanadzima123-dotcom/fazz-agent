# Orbit — Android AI Personal Agent

Orbit is an Android assistant (Kotlin + Jetpack Compose + Material 3) that chats with Google Gemini
through a secure Supabase backend and can act on your phone through a fixed set of safe tools.

**One assistant. Multiple applications. One intelligent interface.**

---

## 1. What works today (MVP)

| Area | Status |
|---|---|
| Chat with Gemini (streaming replies, conversation history, retry, stop) | ✅ |
| Voice input (Android SpeechRecognizer) with live "microphone on" indicator | ✅ |
| Spoken replies (Android TextToSpeech) | ✅ |
| Agent loop with Gemini function calling (multi-step, max 8 steps) | ✅ |
| Tool registry + executor: validation, permission check, confirmation, timeout, error reporting | ✅ |
| Tools: open app, date/time, read calendar, create calendar event (via calendar app), search contacts, device info, web search (Google-grounded, real sources), ask confirmation, save memory, share text, set timer | ✅ |
| User-controlled memory (view / add / delete, stored on device, secrets refused) | ✅ |
| Gemini key only on the server; per-device daily rate limit | ✅ |
| Wake word / always-listening / background assistant | ❌ Not implemented (see §7) |
| Screen reading / controlling other apps (AccessibilityService) | ❌ Not implemented on purpose |

---

## 2. One-time setup: add your Gemini API key (2 minutes)

The app will answer *"The AI server has no Gemini API key yet"* until you do this.

1. Get a free key at <https://aistudio.google.com/apikey> → **Create API key** → copy it.
2. Open <https://supabase.com/dashboard/project/fntbkmkcsvzkozkgklgt/functions/secrets>
   (Project **ai-personal-agent** → Edge Functions → Secrets).
3. Click **Add new secret**:
   - Name: `GEMINI_API_KEY`
   - Value: *(paste your key)*
4. Click **Save**. No redeploy is needed.

Optional secrets (same page):

| Name | Default | Purpose |
|---|---|---|
| `GEMINI_MODEL` | `gemini-flash-latest` | Default model if the app doesn't pick one |
| `DAILY_REQUEST_LIMIT` | `300` | Max requests per device per day |

Then, in the app: **Settings → Test connection**. It lists the models your key can really use; tap one to select it.

> Never paste the Gemini key into the app, into `secrets.properties`, or into git.

---

## 3. Install on your phone

**Public download page:** <https://orbit-agent-ten.vercel.app> (Vercel project `orbit-agent`; page source in `web/`).
Redeploy after a new build:
```
cp app/build/outputs/apk/debug/app-debug.apk web/orbit-<version>.apk   # and update the link in web/index.html
npx vercel@latest deploy --prod --yes --scope fazafauzanadzima123-9292s-projects --cwd web
```

**Model note (verified 2026-11-07):** with the current key, `gemini-3.5-flash-lite` has free quota and
function calling works end-to-end. `gemini-flash-latest` and Google Search grounding returned
"quota exceeded" (no free quota on this key), so the app defaults to `gemini-3.5-flash-lite` and the
web_search tool reports honestly when search is unavailable.

The debug APK is built to `app/build/outputs/apk/debug/app-debug.apk`.

**Option A – copy the file**
1. Copy `app-debug.apk` to your phone (USB, Google Drive, etc.).
2. Open it on the phone. Allow *"Install unknown apps"* for the app you opened it with when asked.

**Option B – USB cable (adb)**
1. On the phone: Settings → About phone → tap **Build number** 7 times → back → Developer options → enable **USB debugging**.
2. Connect the cable, accept the prompt on the phone, then run on the PC:
   ```
   C:\Users\ADVAN\dev-tools\android-sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
   ```

---

## 4. Build it yourself

Tools installed for this project (no admin rights needed) live in `C:\Users\ADVAN\dev-tools`:
JDK 17, Android SDK (platforms 36 & 37), Gradle 9.9.

```bash
export JAVA_HOME=/c/Users/ADVAN/dev-tools/jdk-17.0.21+9
export ANDROID_HOME=/c/Users/ADVAN/dev-tools/android-sdk
./gradlew assembleDebug        # build the APK
./gradlew testDebugUnitTest    # run unit tests
```

`secrets.properties` (git-ignored) holds the **public** Supabase URL and anon key. Copy
`secrets.properties.example` if you clone the project fresh.

---

## 5. Architecture

```
app/src/main/java/com/orbit/agent/
├── presentation/      UI only (Compose): chat, settings, memory, theme, navigation, components
├── core/
│   ├── ai/            AiService interface (provider-neutral) + SupabaseAiService + stream parser
│   ├── agent/         AgentOrchestrator (the agent loop), SystemPrompt, HistoryTrimmer
│   ├── tools/         AgentTool, ToolRegistry, ToolExecutor, SchemaValidator, user interaction
│   │   └── impl/      The concrete Android tools
│   ├── voice/         SpeechInputController, TextToSpeechController
│   ├── security/      Permission checker, SensitiveDataDetector
│   └── common/        JSON helpers
├── data/local/        Settings (DataStore), memory + conversation files (app-private)
└── domain/models/     Plain data classes
backend/supabase/
├── functions/agent-chat/index.ts   Edge Function: holds Gemini key, validates, rate-limits, streams
└── migrations/0001_agent_usage.sql Per-device usage counter (RLS on, service role only)
```

A single Gradle module is used on purpose: the packages give the same separation as multiple
modules, while keeping builds fast on an 8 GB machine. Packages can be split into modules later
without code changes.

**How a request flows**

1. You type or speak → `ChatViewModel` → `AgentOrchestrator.run()`.
2. The orchestrator sends the conversation + tool list to `AiService` → Supabase `agent-chat` → Gemini (streaming).
3. If Gemini asks for a tool, `ToolExecutor` runs it: **validate args → check/request Android permission → ask you to confirm if sensitive → run with timeout → structured result**.
4. The result goes back to Gemini; repeat until it answers (max 8 steps).

**Safety rules built in**
- The model can only call the tools in the registry; there is no code or shell execution.
- Calendar and contact data are shared with Gemini only after you approve each request.
- Creating events and sharing text open the real system app; you press Save/Send yourself.
- Memory is saved only after you approve the exact text, and passwords/PINs/card numbers are refused.

---

## 6. Tests

- Unit tests (`app/src/test`): stream parser, backend client (MockWebServer: auth headers, invalid key,
  missing key, quota, network failure, HTTPS enforcement), tool executor (unknown tool, bad args,
  permission denied/granted, user decline/approve, timeout, exceptions), agent loop (tool round-trip,
  thought-signature preservation, max steps, failure recovery, safety block), history trimming,
  app-name matching, sensitive-data detection, date parsing.
- Backend checked live with curl: missing auth → 401, invalid JSON → 400, missing Gemini key → 503.

---

## 7. Roadmap / not done yet

- **Wake word & background listening.** Android only allows background microphone use from a
  foreground service with a persistent notification (`FOREGROUND_SERVICE_MICROPHONE`), and Android 14+
  restricts starting such services from the background. A real wake word also needs an on-device
  hotword engine. This is a Phase 6 investigation; nothing in the app claims it works today.
- Multiple saved conversations (currently one, with "New chat").
- Optional cloud sync of memory (Supabase tables with Row Level Security + user accounts).
- Supabase Auth per user (today: public anon key + per-install rate limit).
- Notification tool, reminders, more app integrations.
