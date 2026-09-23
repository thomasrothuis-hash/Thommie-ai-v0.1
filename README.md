# MAATJE v0.8.9

Dedicated personal AI terminal for Android / OnePlus 9 Pro.

## v0.8.9 features

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

## Planned v0.8.9
- Local wake word ("Hey ChatGPT")
- Auto-start on boot
- Optional launcher/kiosk mode
- Realtime speech-to-speech
- Better secure ephemeral-token architecture via tiny backend
- Conversation screen / standby animations


## GitHub Actions
`.github/workflows/build-apk.yml` builds with Java 17, Android SDK 35 and Gradle 8.9, then uploads `MAATJE_AI_v0.8.9.apk` as a workflow artifact.
