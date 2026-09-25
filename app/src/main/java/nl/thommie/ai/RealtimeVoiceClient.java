package nl.thommie.ai;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.SystemClock;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

final class RealtimeVoiceClient {

    interface Listener {
        void onConnected();
        void onDisconnected();
        void onError(String message);
        void onUserSpeechStarted();
        void onUserSpeechStopped();
        void onUserTranscript(String text, boolean complete);
        void onAssistantTranscript(String text, boolean complete);
        void onAssistantSpeaking(boolean speaking);
        void onMicPcm(short[] samples, int length);
        void onAssistantPcm(byte[] pcm);
    }

    private static final int SAMPLE_RATE = 24000;
    private static final int MIC_CHUNK_SAMPLES = 960;

    private final Context context;
    private final Listener listener;

    private final AtomicBoolean running =
            new AtomicBoolean(false);
    private final AtomicBoolean socketReady =
            new AtomicBoolean(false);
    private final AtomicBoolean responseActive =
            new AtomicBoolean(false);
    private final AtomicBoolean assistantAudioActive =
            new AtomicBoolean(false);
    private final AtomicInteger playbackGeneration =
            new AtomicInteger(0);

    private final ExecutorService microphoneExecutor =
            Executors.newSingleThreadExecutor();
    private final ExecutorService playbackExecutor =
            Executors.newSingleThreadExecutor();

    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private AudioManager audioManager;
    private int previousAudioMode = AudioManager.MODE_NORMAL;
    private AudioDeviceInfo previousCommunicationDevice;
    private boolean previousSpeakerphoneOn = false;
    private boolean communicationAudioActive = false;
    private AcousticEchoCanceler echoCanceler;
    private NoiseSuppressor noiseSuppressor;
    private volatile float recentOutputLevel = 0f;
    private volatile long lastAssistantAudioMs = 0L;
    private int bargeInCandidateChunks = 0;
    private final ArrayDeque<byte[]> bargeInBuffer =
            new ArrayDeque<>();

    private String instructions;
    private String voice;

    private StringBuilder userTranscript =
            new StringBuilder();
    private StringBuilder assistantTranscript =
            new StringBuilder();

    RealtimeVoiceClient(
            Context context,
            Listener listener
    ) {
        this.context =
                context.getApplicationContext();
        this.listener = listener;
    }

    void start(
            String apiKey,
            String instructions,
            String voice
    ) {
        if (running.getAndSet(true)) {
            return;
        }

        this.instructions =
                instructions == null
                        ? ""
                        : instructions;
        this.voice =
                normalizeVoice(voice);

        responseActive.set(false);
        assistantAudioActive.set(false);
        recentOutputLevel = 0f;
        lastAssistantAudioMs = 0L;
        clearBargeInCandidate();

        httpClient =
                new OkHttpClient.Builder()
                        .readTimeout(
                                0,
                                java.util.concurrent.TimeUnit.MILLISECONDS
                        )
                        .pingInterval(
                                20,
                                java.util.concurrent.TimeUnit.SECONDS
                        )
                        .build();

        Request request =
                new Request.Builder()
                        .url(
                                "wss://api.openai.com/v1/realtime"
                                        + "?model=gpt-realtime-2.1"
                        )
                        .header(
                                "Authorization",
                                "Bearer " + apiKey
                        )
                        .build();

        webSocket =
                httpClient.newWebSocket(
                        request,
                        new WebSocketListener() {
                            @Override
                            public void onOpen(
                                    WebSocket socket,
                                    Response response
                            ) {
                                socketReady.set(true);
                                sendSessionUpdate();
                                startOutput();
                                startMicrophone();
                            }

                            @Override
                            public void onMessage(
                                    WebSocket socket,
                                    String text
                            ) {
                                handleServerEvent(text);
                            }

                            @Override
                            public void onClosing(
                                    WebSocket socket,
                                    int code,
                                    String reason
                            ) {
                                socket.close(code, reason);
                            }

                            @Override
                            public void onClosed(
                                    WebSocket socket,
                                    int code,
                                    String reason
                            ) {
                                socketReady.set(false);
                                stopAudioOnly();

                                if (running.get()) {
                                    running.set(false);
                                    listener.onDisconnected();
                                }
                            }

                            @Override
                            public void onFailure(
                                    WebSocket socket,
                                    Throwable t,
                                    Response response
                            ) {
                                socketReady.set(false);

                                if (running.get()) {
                                    listener.onError(
                                            t == null
                                                    ? "Realtime-verbinding mislukt."
                                                    : safeMessage(t)
                                    );
                                }

                                stopAudioOnly();
                            }
                        }
                );
    }

    void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        socketReady.set(false);
        responseActive.set(false);
        assistantAudioActive.set(false);
        recentOutputLevel = 0f;
        lastAssistantAudioMs = 0L;
        clearBargeInCandidate();
        playbackGeneration.incrementAndGet();

        stopAudioOnly();

        WebSocket socket = webSocket;
        webSocket = null;

        if (socket != null) {
            try {
                socket.close(
                        1000,
                        "session closed"
                );
            } catch (Exception ignored) {}
        }

        OkHttpClient client = httpClient;
        httpClient = null;

        if (client != null) {
            try {
                client.dispatcher()
                        .executorService()
                        .shutdown();
            } catch (Exception ignored) {}

            try {
                client.connectionPool().evictAll();
            } catch (Exception ignored) {}
        }
    }

    void shutdown() {
        stop();
        microphoneExecutor.shutdownNow();
        playbackExecutor.shutdownNow();
    }

    boolean isRunning() {
        return running.get();
    }

    void cancelResponse() {
        if (responseActive.compareAndSet(
                true,
                false
        )) {
            try {
                send(
                        new JSONObject()
                                .put("type", "response.cancel")
                );
            } catch (Exception ignored) {}
        }

        flushOutput();
    }

    void sendImageQuestion(
            String imageDataUrl,
            String text
    ) {
        if (imageDataUrl == null
                || imageDataUrl.trim().isEmpty()) {
            return;
        }

        try {
            cancelResponse();

            JSONArray content =
                    new JSONArray();

            content.put(
                    new JSONObject()
                            .put(
                                    "type",
                                    "input_image"
                            )
                            .put(
                                    "image_url",
                                    imageDataUrl
                            )
            );

            content.put(
                    new JSONObject()
                            .put(
                                    "type",
                                    "input_text"
                            )
                            .put(
                                    "text",
                                    text == null
                                            ? "Beschrijf wat er op dit scherm staat."
                                            : text
                            )
            );

            JSONObject item =
                    new JSONObject()
                            .put(
                                    "type",
                                    "message"
                            )
                            .put(
                                    "role",
                                    "user"
                            )
                            .put(
                                    "content",
                                    content
                            );

            send(
                    new JSONObject()
                            .put(
                                    "type",
                                    "conversation.item.create"
                            )
                            .put(
                                    "item",
                                    item
                            )
            );

            send(
                    new JSONObject()
                            .put(
                                    "type",
                                    "response.create"
                            )
                            .put(
                                    "response",
                                    new JSONObject()
                                            .put(
                                                    "output_modalities",
                                                    new JSONArray()
                                                            .put("audio")
                                            )
                            )
            );

        } catch (Exception e) {
            listener.onError(
                    safeMessage(e)
            );
        }
    }

    private void sendSessionUpdate() {
        try {
            JSONObject turnDetection =
                    new JSONObject()
                            .put(
                                    "type",
                                    "server_vad"
                            )
                            .put(
                                    "threshold",
                                    0.48
                            )
                            .put(
                                    "prefix_padding_ms",
                                    220
                            )
                            .put(
                                    "silence_duration_ms",
                                    360
                            )
                            .put(
                                    "create_response",
                                    true
                            )
                            .put(
                                    "interrupt_response",
                                    false
                            );

            JSONObject input =
                    new JSONObject()
                            .put(
                                    "format",
                                    new JSONObject()
                                            .put(
                                                    "type",
                                                    "audio/pcm"
                                            )
                                            .put(
                                                    "rate",
                                                    SAMPLE_RATE
                                            )
                            )
                            .put(
                                    "transcription",
                                    new JSONObject()
                                            .put(
                                                    "model",
                                                    "gpt-transcribe"
                                            )
                                            .put(
                                                    "language",
                                                    "nl"
                                            )
                            )
                            .put(
                                    "turn_detection",
                                    turnDetection
                            );

            JSONObject output =
                    new JSONObject()
                            .put(
                                    "format",
                                    new JSONObject()
                                            .put(
                                                    "type",
                                                    "audio/pcm"
                                            )
                                            .put(
                                                    "rate",
                                                    SAMPLE_RATE
                                            )
                            )
                            .put(
                                    "voice",
                                    voice
                            );

            JSONObject audio =
                    new JSONObject()
                            .put("input", input)
                            .put("output", output);

            JSONObject session =
                    new JSONObject()
                            .put(
                                    "type",
                                    "realtime"
                            )
                            .put(
                                    "model",
                                    "gpt-realtime-2.1"
                            )
                            .put(
                                    "output_modalities",
                                    new JSONArray()
                                            .put("audio")
                            )
                            .put(
                                    "audio",
                                    audio
                            )
                            .put(
                                    "reasoning",
                                    new JSONObject()
                                            .put(
                                                    "effort",
                                                    "minimal"
                                            )
                            )
                            .put(
                                    "instructions",
                                    instructions
                            );

            send(
                    new JSONObject()
                            .put(
                                    "type",
                                    "session.update"
                            )
                            .put(
                                    "session",
                                    session
                            )
            );

        } catch (Exception e) {
            listener.onError(
                    safeMessage(e)
            );
        }
    }

    private void startMicrophone() {
        microphoneExecutor.submit(() -> {
            if (!running.get()) {
                return;
            }

            int minBuffer =
                    AudioRecord.getMinBufferSize(
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                    );

            int recordBuffer =
                    Math.max(
                            minBuffer,
                            SAMPLE_RATE / 5 * 2
                    );

            try {
                audioRecord =
                        new AudioRecord(
                                MediaRecorder.AudioSource
                                        .VOICE_COMMUNICATION,
                                SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                recordBuffer
                        );

                if (audioRecord.getState()
                        != AudioRecord.STATE_INITIALIZED) {
                    throw new IllegalStateException(
                            "Microfoon kon niet in realtime-modus worden geopend."
                    );
                }

                enableInputAudioEffects(
                        audioRecord.getAudioSessionId()
                );

                short[] samples =
                        new short[MIC_CHUNK_SAMPLES];

                audioRecord.startRecording();

                while (running.get()) {
                    int read =
                            audioRecord.read(
                                    samples,
                                    0,
                                    samples.length,
                                    AudioRecord.READ_BLOCKING
                            );

                    if (read <= 0) {
                        continue;
                    }

                    listener.onMicPcm(
                            samples,
                            read
                    );

                    if (!socketReady.get()) {
                        continue;
                    }

                    byte[] pcm =
                            shortsToLittleEndian(
                                    samples,
                                    read
                            );

                    handleMicrophoneChunk(
                            samples,
                            read,
                            pcm
                    );
                }

            } catch (Exception e) {
                if (running.get()) {
                    listener.onError(
                            safeMessage(e)
                    );
                }
            } finally {
                releaseInputAudioEffects();

                AudioRecord record =
                        audioRecord;
                audioRecord = null;

                if (record != null) {
                    try {
                        record.stop();
                    } catch (Exception ignored) {}

                    try {
                        record.release();
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private void enableInputAudioEffects(
            int audioSessionId
    ) {
        releaseInputAudioEffects();

        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler =
                        AcousticEchoCanceler.create(
                                audioSessionId
                        );

                if (echoCanceler != null) {
                    echoCanceler.setEnabled(true);
                }
            }
        } catch (Exception ignored) {
            echoCanceler = null;
        }

        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor =
                        NoiseSuppressor.create(
                                audioSessionId
                        );

                if (noiseSuppressor != null) {
                    noiseSuppressor.setEnabled(true);
                }
            }
        } catch (Exception ignored) {
            noiseSuppressor = null;
        }
    }

    private void releaseInputAudioEffects() {
        AcousticEchoCanceler aec =
                echoCanceler;
        echoCanceler = null;

        if (aec != null) {
            try {
                aec.release();
            } catch (Exception ignored) {}
        }

        NoiseSuppressor ns =
                noiseSuppressor;
        noiseSuppressor = null;

        if (ns != null) {
            try {
                ns.release();
            } catch (Exception ignored) {}
        }
    }

    private void handleMicrophoneChunk(
            short[] samples,
            int length,
            byte[] pcm
    ) throws Exception {
        if (pcm == null
                || pcm.length == 0) {
            return;
        }

        long now =
                SystemClock.elapsedRealtime();

        boolean assistantWindow =
                responseActive.get()
                        || assistantAudioActive.get()
                        || now - lastAssistantAudioMs < 900L;

        if (assistantWindow) {
            clearBargeInCandidate();
            return;
        }

        sendInputPcm(pcm);
    }

    private void clearBargeInCandidate() {
        bargeInCandidateChunks = 0;
        bargeInBuffer.clear();
    }

    private void sendInputPcm(
            byte[] pcm
    ) throws Exception {
        String encoded =
                Base64.encodeToString(
                        pcm,
                        Base64.NO_WRAP
                );

        send(
                new JSONObject()
                        .put(
                                "type",
                                "input_audio_buffer.append"
                        )
                        .put(
                                "audio",
                                encoded
                        )
        );
    }

    private float rmsShorts(
            short[] samples,
            int length
    ) {
        if (samples == null
                || length <= 0) {
            return 0f;
        }

        int safeLength =
                Math.min(
                        length,
                        samples.length
                );

        double sum = 0.0;

        for (int i = 0; i < safeLength; i++) {
            double value =
                    samples[i] / 32768.0;
            sum += value * value;
        }

        return safeLength == 0
                ? 0f
                : (float) Math.sqrt(
                        sum / safeLength
                );
    }

    private float rmsPcm16(
            byte[] pcm
    ) {
        if (pcm == null
                || pcm.length < 2) {
            return 0f;
        }

        int samples =
                pcm.length / 2;
        double sum = 0.0;

        for (int i = 0; i < samples; i++) {
            int lo =
                    pcm[i * 2] & 0xff;
            int hi =
                    pcm[i * 2 + 1];

            short value =
                    (short) (
                            (hi << 8)
                                    | lo
                    );

            double normalized =
                    value / 32768.0;
            sum +=
                    normalized
                            * normalized;
        }

        return samples == 0
                ? 0f
                : (float) Math.sqrt(
                        sum / samples
                );
    }

    private void startOutput() {
        beginCommunicationAudio();

        int minBuffer =
                AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                );

        int outputBuffer =
                Math.max(
                        minBuffer * 4,
                        SAMPLE_RATE / 2
                );

        try {
            audioTrack =
                    new AudioTrack.Builder()
                            .setAudioAttributes(
                                    new AudioAttributes.Builder()
                                            .setUsage(
                                                    AudioAttributes
                                                            .USAGE_ASSISTANT
                                            )
                                            .setContentType(
                                                    AudioAttributes
                                                            .CONTENT_TYPE_SPEECH
                                            )
                                            .build()
                            )
                            .setAudioFormat(
                                    new AudioFormat.Builder()
                                            .setEncoding(
                                                    AudioFormat
                                                            .ENCODING_PCM_16BIT
                                            )
                                            .setSampleRate(
                                                    SAMPLE_RATE
                                            )
                                            .setChannelMask(
                                                    AudioFormat
                                                            .CHANNEL_OUT_MONO
                                            )
                                            .build()
                            )
                            .setBufferSizeInBytes(
                                    outputBuffer
                            )
                            .setTransferMode(
                                    AudioTrack.MODE_STREAM
                            )
                            .build();

            audioTrack.play();

        } catch (Exception e) {
            listener.onError(
                    safeMessage(e)
            );
        }
    }

    private void playOutputChunk(
            byte[] pcm
    ) {
        if (pcm == null
                || pcm.length == 0) {
            return;
        }

        listener.onAssistantPcm(pcm);

        int generation =
                playbackGeneration.get();

        playbackExecutor.submit(() -> {
            if (!running.get()
                    || generation
                    != playbackGeneration.get()) {
                return;
            }

            AudioTrack track =
                    audioTrack;

            if (track == null
                    || track.getState()
                    != AudioTrack.STATE_INITIALIZED) {
                return;
            }

            try {
                if (track.getPlayState()
                        != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play();
                }

                assistantAudioActive.set(true);

                track.write(
                        pcm,
                        0,
                        pcm.length,
                        AudioTrack.WRITE_BLOCKING
                );

                lastAssistantAudioMs =
                        SystemClock.elapsedRealtime();

            } catch (Exception ignored) {}
        });
    }

    private void flushOutput() {
        playbackGeneration.incrementAndGet();

        AudioTrack track =
                audioTrack;

        if (track == null) {
            return;
        }

        try {
            track.pause();
        } catch (Exception ignored) {}

        try {
            track.flush();
        } catch (Exception ignored) {}

        try {
            if (running.get()) {
                track.play();
            }
        } catch (Exception ignored) {}
    }

    private void handleServerEvent(
            String raw
    ) {
        try {
            JSONObject event =
                    new JSONObject(raw);

            String type =
                    event.optString(
                            "type",
                            ""
                    );

            switch (type) {
                case "session.updated":
                    listener.onConnected();
                    break;

                case "input_audio_buffer.speech_started":
                    userTranscript =
                            new StringBuilder();
                    listener.onUserSpeechStarted();
                    break;

                case "input_audio_buffer.speech_stopped":
                    listener.onUserSpeechStopped();
                    break;

                case "conversation.item.input_audio_transcription.delta": {
                    String delta =
                            event.optString(
                                    "delta",
                                    ""
                            );

                    if (!delta.isEmpty()) {
                        userTranscript.append(delta);
                        listener.onUserTranscript(
                                userTranscript.toString(),
                                false
                        );
                    }
                    break;
                }

                case "conversation.item.input_audio_transcription.completed": {
                    String transcript =
                            event.optString(
                                    "transcript",
                                    userTranscript.toString()
                            );

                    userTranscript =
                            new StringBuilder(transcript);

                    listener.onUserTranscript(
                            transcript,
                            true
                    );
                    break;
                }

                case "response.created":
                    responseActive.set(true);
                    assistantAudioActive.set(true);
                    assistantTranscript =
                            new StringBuilder();
                    listener.onAssistantSpeaking(true);
                    break;

                case "response.output_audio.delta": {
                    String delta =
                            event.optString(
                                    "delta",
                                    ""
                            );

                    if (!delta.isEmpty()) {
                        playOutputChunk(
                                Base64.decode(
                                        delta,
                                        Base64.DEFAULT
                                )
                        );
                    }
                    break;
                }

                case "response.output_audio_transcript.delta": {
                    String delta =
                            event.optString(
                                    "delta",
                                    ""
                            );

                    if (!delta.isEmpty()) {
                        assistantTranscript.append(delta);
                        listener.onAssistantTranscript(
                                assistantTranscript.toString(),
                                false
                        );
                    }
                    break;
                }

                case "response.output_audio_transcript.done": {
                    String transcript =
                            event.optString(
                                    "transcript",
                                    assistantTranscript.toString()
                            );

                    assistantTranscript =
                            new StringBuilder(transcript);

                    listener.onAssistantTranscript(
                            transcript,
                            true
                    );
                    break;
                }

                case "response.done": {
                    int generation =
                            playbackGeneration.get();

                    playbackExecutor.submit(() -> {
                        if (!running.get()
                                || generation
                                != playbackGeneration.get()) {
                            return;
                        }

                        responseActive.set(false);
                        assistantAudioActive.set(false);
                        lastAssistantAudioMs =
                                SystemClock.elapsedRealtime();
                        listener.onAssistantSpeaking(false);
                    });
                    break;
                }

                case "error": {
                    JSONObject error =
                            event.optJSONObject(
                                    "error"
                            );

                    listener.onError(
                            error == null
                                    ? "Realtime API-fout."
                                    : error.optString(
                                            "message",
                                            "Realtime API-fout."
                                    )
                    );
                    break;
                }

                default:
                    break;
            }

        } catch (Exception e) {
            if (running.get()) {
                listener.onError(
                        safeMessage(e)
                );
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void beginCommunicationAudio() {
        if (communicationAudioActive) {
            return;
        }

        try {
            audioManager =
                    (AudioManager) context.getSystemService(
                            Context.AUDIO_SERVICE
                    );

            if (audioManager == null) {
                return;
            }

            previousAudioMode =
                    audioManager.getMode();

            if (android.os.Build.VERSION.SDK_INT
                    >= android.os.Build.VERSION_CODES.S) {
                previousCommunicationDevice =
                        audioManager.getCommunicationDevice();
            } else {
                previousSpeakerphoneOn =
                        audioManager.isSpeakerphoneOn();
            }

            audioManager.setMode(
                    AudioManager.MODE_IN_COMMUNICATION
            );

            if (android.os.Build.VERSION.SDK_INT
                    >= android.os.Build.VERSION_CODES.S) {
                AudioDeviceInfo speaker = null;

                for (AudioDeviceInfo device :
                        audioManager
                                .getAvailableCommunicationDevices()) {
                    if (device.getType()
                            == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        speaker = device;
                        break;
                    }
                }

                if (speaker != null) {
                    audioManager.setCommunicationDevice(
                            speaker
                    );
                }
            } else {
                audioManager.setSpeakerphoneOn(true);
            }

            communicationAudioActive = true;

        } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    private void endCommunicationAudio() {
        if (!communicationAudioActive
                || audioManager == null) {
            return;
        }

        try {
            if (android.os.Build.VERSION.SDK_INT
                    >= android.os.Build.VERSION_CODES.S) {
                if (previousCommunicationDevice != null) {
                    audioManager.setCommunicationDevice(
                            previousCommunicationDevice
                    );
                } else {
                    audioManager.clearCommunicationDevice();
                }
            } else {
                audioManager.setSpeakerphoneOn(
                        previousSpeakerphoneOn
                );
            }
        } catch (Exception ignored) {}

        try {
            audioManager.setMode(
                    previousAudioMode
            );
        } catch (Exception ignored) {}

        communicationAudioActive = false;
        previousCommunicationDevice = null;
    }

    private void stopAudioOnly() {
        releaseInputAudioEffects();

        AudioRecord record =
                audioRecord;
        audioRecord = null;

        if (record != null) {
            try {
                record.stop();
            } catch (Exception ignored) {}

            try {
                record.release();
            } catch (Exception ignored) {}
        }

        AudioTrack track =
                audioTrack;
        audioTrack = null;

        if (track != null) {
            try {
                track.pause();
            } catch (Exception ignored) {}

            try {
                track.flush();
            } catch (Exception ignored) {}

            try {
                track.release();
            } catch (Exception ignored) {}
        }

        endCommunicationAudio();
    }

    private void send(
            JSONObject event
    ) {
        WebSocket socket =
                webSocket;

        if (socket == null
                || event == null) {
            return;
        }

        socket.send(
                event.toString()
        );
    }

    private byte[] shortsToLittleEndian(
            short[] samples,
            int length
    ) {
        byte[] bytes =
                new byte[length * 2];

        for (int i = 0; i < length; i++) {
            short value = samples[i];
            bytes[i * 2] =
                    (byte) (value & 0xff);
            bytes[i * 2 + 1] =
                    (byte) (
                            (value >> 8)
                                    & 0xff
                    );
        }

        return bytes;
    }

    private String normalizeVoice(
            String requested
    ) {
        if (requested == null) {
            return "marin";
        }

        switch (requested) {
            case "alloy":
            case "ash":
            case "ballad":
            case "coral":
            case "echo":
            case "sage":
            case "shimmer":
            case "verse":
            case "marin":
            case "cedar":
                return requested;
            default:
                return "marin";
        }
    }

    private String safeMessage(
            Throwable throwable
    ) {
        if (throwable == null
                || throwable.getMessage() == null
                || throwable.getMessage().trim().isEmpty()) {
            return "Onbekende realtime-fout.";
        }

        return throwable.getMessage();
    }
}
