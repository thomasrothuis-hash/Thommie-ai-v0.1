# MAATJE v1.0.0

Dedicated personal AI terminal for Android / OnePlus 9 Pro.

## v1.0.0 speed update

- Streams Responses API text into the assistant overlay while the model is still generating.
- Uses GPT-5.6 Luna with reasoning effort set to none and low output verbosity for faster everyday replies.
- Caps normal assistant replies and prompts MAATJE to answer ordinary questions in roughly 1–3 sentences unless more detail is needed.
- Uses a stable prompt-cache key with 24-hour cache retention to improve reuse of repeated conversation prefixes.
- Lowers web-search context size for faster tool-enabled answers.
- Uses low-detail vision and smaller JPEG uploads for ordinary screen descriptions, while keeping high detail for OCR-like requests such as reading text, numbers or codes.
- Changes Android speech formatting from quality-optimized to latency-optimized and requests only the single best recognition result.
- Starts overlay speech recognition slightly sooner after microphone handoff.

## v0.9.5.1 fixes

- Removes visible Markdown markers such as **, *, backticks and heading markers from overlay answers.
- Instructs MAATJE to answer in plain text by default.
- Screen descriptions now prefer short, natural Dutch sentences instead of Markdown-style lists.
- Keeps a local text-cleanup fallback in the assistant overlay if the model still returns formatting markers.

## v0.9.5 features

- Adds screen-aware vision to the native assistant overlay.
- Saying phrases such as “wat zie je op mijn scherm?” sends one Android assistant screenshot with the spoken question.
- Normal questions remain text-only; screenshots are not streamed continuously.
- Screen images are resized to at most 2400 px on the longest edge and sent as JPEG vision input.
- The overlay shows LOOKING AT SCREEN / VISION while a screenshot is being analyzed.
- Secure or policy-blocked screens fail clearly instead of silently hallucinating screen content.

## v0.9.4.1 fixes

- Hard microphone handoff: the Vosk wake-word AudioRecord is fully stopped and its capture thread gets time to release before overlay speech recognition starts.
- Recreates the Android SpeechRecognizer for every overlay listen attempt and uses the same system-selected provider as the working full MAATJE app.
- Adds one automatic retry for AUDIO, CLIENT and RECOGNIZER BUSY speech errors.
- Shows useful MIC diagnostics in the overlay instead of hiding all speech errors behind "Ik verstond je niet".
- Centers the close icon using a padding-free TextView.
- Adds a subtle three-layer green glow around the full screen while the assistant overlay is active.
- Makes the VoiceInteractionSession full-screen transparent while keeping the MAATJE card at the bottom, so the foreground app stays visible.

## v0.9.4 features

- Replaces fullscreen assistant launching with a native VoiceInteractionSession overlay.
- Keeps the foreground app visible behind a compact bottom assistant panel.
- Overlay states: LISTENING, PROCESSING SPEECH, THINKING, PREPARING VOICE, SPEAKING and READY.
- Live partial speech transcript plus final user query and assistant answer.
- Reuses the encrypted API key, OpenAI conversation, profile memory, personality, web-search setting and token tracking.
- Cloud TTS plays directly from the assistant overlay and the overlay auto-closes after the response.
- Local overlay actions preserve flashlight, volume, brightness, battery, timers, alarms, camera, Wi-Fi/Bluetooth settings and opening apps.
- Background Vosk wake listening hands microphone ownership to the overlay while the session is visible.
- SessionService now shares the main app process so wake/session microphone coordination is deterministic.
- Requests Android assist context as a foundation for future screen-aware commands such as asking what is currently shown.

## v0.9.3.1 hotfix

- Fixes the Default Assistant setup flow on Android 16/LineageOS by opening the system Assist & voice input screen instead of trying to request the non-requestable ASSISTANT role directly.
- Adds a valid RecognitionService component required by Android's VoiceInteractionService metadata.
- RecognitionService proxies to an external installed recognizer so MAATJE does not replace working speech recognition with a stub.
- Default-assistant state now also checks VoiceInteractionService.isActiveService().
- Shows READY • DEFAULT ASSISTANT after returning to MAATJE when Android has activated it.

## v0.9.3 features

- Registers MAATJE as an Android VoiceInteractionService and Assistant-role candidate.
- New Settings > Standaard assistent flow using Android RoleManager.
- Separate VoiceInteractionSessionService process for system assistant invocations.
- Assistant invocation opens MAATJE directly in command-listening mode.
- Local Vosk background wake-word engine while MAATJE is the selected assistant and the Activity is closed.
- Existing Wake word switch and sensitivity also control background "Hey Maatje".
- Foreground/background microphone ownership handoff prevents both wake engines from intentionally listening at the same time.
- Supports Android assist gesture and launch-from-keyguard metadata.

### Existing v0.9.2 features

- Local timers via Android AlarmClock with hours, minutes and seconds.
- Local alarms/wekkers including 24-hour times, half zeven, kwart over and kwart voor.
- Exact Responses API input/output/total token usage after every assistant response.
- Compact live token/context meter in the main terminal.
- Usage & tokens panel with last request, app-session totals, persistent totals and estimated Luna model-token cost.
- Context headroom indicator based on GPT-5.6 Luna's 1.05M context window; this is not an API-credit balance.

- Local Device Control layer; common phone actions bypass the cloud for speed.
- Flashlight on/off/toggle using CameraManager.
- Media volume status, up/down, mute, max and percentage control.
- System brightness status and percentage/up/down control with Android WRITE_SETTINGS consent.
- Battery percentage, charging state and temperature.
- Open camera, Wi-Fi settings and Bluetooth settings.
- Open installed launcher apps by spoken/display name.
- Basic device and Android version information.
- New Toestelbediening settings panel for permissions.

- OpenAI Responses API web_search tool with automatic tool choice.
- Internet setting: Automatic or Off.
- Detects real web-search calls and shows up to five cited sources.
- Source URLs are displayed but are not read aloud by TTS.
- Voice commands for internet on/off/status.
- Existing conversation memory, personality and local profile remain intact.

- Requests Android 13+ high-quality speech formatting for punctuation and capitalization.
- Adds zero-network local fallback formatting for recognized Dutch speech.
- Supports spoken punctuation commands: komma, punt, vraagteken and uitroepteken.
- Automatically adds a question mark to common Dutch question forms and a period otherwise.

- Temporarily raises Android's voice-call stream to maximum while MAATJE speaks.
- Restores the user's previous call volume immediately after TTS.
- Keeps communication-mode AEC and explicit loudspeaker routing.

- Keeps communication-mode AEC but explicitly routes TTS to the built-in loudspeaker on Android 12+.
- Restores the previous communication audio device after speech.
- Forces MediaPlayer output gain to full scale.

- Routes MAATJE TTS through Android's voice-communication audio path for stronger AEC.
- Accepts spoken stop only from final Vosk results with strong per-word confidence.
- Filters Vosk [unk]/unk noise from the live debug display.
- Keeps the existing post-TTS echo-tail cooldown.

- Acoustic echo cancellation (AEC) for local Vosk listening while MAATJE speaks.
- VOICE_COMMUNICATION microphone capture with NoiseSuppressor and MIC fallback.
- Keeps spoken "Maatje stop" interruption while reducing self-hearing.
- Full-screen dark OLED-style interface
- Text chat through OpenAI Responses API
- Android speech recognition (Dutch) via microphone button
- Android TextToSpeech reads replies aloud
- Multi-turn conversation using `previous_response_id`
- API key encrypted with Android Keystore (AES-GCM), never hardcoded in source/APK
- Screen kept awake while the app is open

## API
Default model: `gpt-5.6-luna`.
Endpoint: `POST https://api.openai.com/v1/responses`.

Your ChatGPT Plus subscription does not automatically include API usage; add API billing/credits separately.

## Build
Open this folder in Android Studio and build `app` as a debug APK, or use the included GitHub Actions workflow.

Local build from a machine with Android SDK + Gradle:

```bash
gradle assembleDebug
```

Expected output:
`app/build/outputs/apk/debug/app-debug.apk`

## Planned next
- Auto-start on boot
- Optional launcher/kiosk mode
- Realtime speech-to-speech
- Better secure ephemeral-token architecture via tiny backend
- Conversation screen / standby animations


## GitHub Actions
`.github/workflows/build-apk.yml` builds with Java 17, Android SDK 35 and Gradle 8.9, then uploads `MAATJE_v1.0.0.apk` as a workflow artifact.
