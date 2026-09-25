# MAATJE v0.9.3

Dedicated personal AI terminal for Android / OnePlus 9 Pro.

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
`.github/workflows/build-apk.yml` builds with Java 17, Android SDK 35 and Gradle 8.9, then uploads `MAATJE_v0.9.3.apk` as a workflow artifact.
