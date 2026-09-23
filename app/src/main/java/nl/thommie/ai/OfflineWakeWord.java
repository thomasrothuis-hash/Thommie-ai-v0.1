package nl.thommie.ai;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.StorageService;

import java.util.Locale;

final class OfflineWakeWord {

    interface Callback {
        void onReady();
        void onDetected();
        void onStopDetected();
        void onHeard(String text);
        void onError(String message);
    }

    private enum Mode {
        WAKE,
        STOP
    }

    private final Activity activity;
    private final Callback callback;

    private Model model;
    private static final int SAMPLE_RATE = 16000;

    private AudioRecord audioRecord;
    private AcousticEchoCanceler echoCanceler;
    private NoiseSuppressor noiseSuppressor;
    private Thread audioThread;

    private volatile int captureGeneration = 0;
    private volatile boolean echoCancellationActive = false;

    private boolean preparing = false;
    private boolean ready = false;
    private volatile boolean running = false;
    private boolean detected = false;

    private String lastHeard = "";
    private Mode mode = Mode.WAKE;

    OfflineWakeWord(
            Activity activity,
            Callback callback
    ) {
        this.activity = activity;
        this.callback = callback;
    }

    void prepare() {
        if (ready || preparing) return;

        preparing = true;

        StorageService.unpack(
                activity,
                "model-nl",
                "maatje-vosk-model",
                unpackedModel -> {
                    model = unpackedModel;
                    preparing = false;
                    ready = true;
                    callback.onReady();
                },
                exception -> {
                    preparing = false;
                    ready = false;

                    callback.onError(
                            "Offline wake-model laden mislukt: "
                                    + exception.getMessage()
                    );
                }
        );
    }

    boolean isReady() {
        return ready;
    }

    synchronized void start() {
        startInternal(
                Mode.WAKE,
                grammarForWake()
        );
    }

    synchronized void startStopListening() {
        startInternal(
                Mode.STOP,
                "["
                        + "\"maatje stop\","
                        + "\"maartje stop\","
                        + "\"hey maatje stop\","
                        + "\"hee maatje stop\","
                        + "\"he maatje stop\","
                        + "\"stop maatje\","
                        + "\"stop maartje\","
                        + "\"[unk]\""
                        + "]"
        );
    }

    private synchronized void startInternal(
            Mode requestedMode,
            String grammar
    ) {
        if (!ready || model == null) {
            return;
        }

        stop();

        detected = false;
        lastHeard = "";
        mode = requestedMode;

        final int generation =
                ++captureGeneration;

        Recognizer localRecognizer = null;
        AudioRecord localRecord = null;
        AcousticEchoCanceler localAec = null;
        NoiseSuppressor localNoise = null;

        try {
            localRecognizer =
                    new Recognizer(
                            model,
                            SAMPLE_RATE,
                            grammar
                    );

            int minBuffer =
                    AudioRecord.getMinBufferSize(
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                    );

            if (minBuffer <= 0) {
                throw new IllegalStateException(
                        "Geen geldige microfoonbuffer beschikbaar."
                );
            }

            int bufferSize =
                    Math.max(
                            minBuffer * 2,
                            4096
                    );

            localRecord =
                    new AudioRecord(
                            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize
                    );

            if (localRecord.getState()
                    != AudioRecord.STATE_INITIALIZED) {
                try {
                    localRecord.release();
                } catch (Exception ignored) {}

                localRecord =
                        new AudioRecord(
                                MediaRecorder.AudioSource.MIC,
                                SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                bufferSize
                        );
            }

            if (localRecord.getState()
                    != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException(
                        "Microfoon kon niet initialiseren."
                );
            }

            int sessionId =
                    localRecord.getAudioSessionId();

            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    localAec =
                            AcousticEchoCanceler.create(
                                    sessionId
                            );

                    if (localAec != null) {
                        localAec.setEnabled(true);
                        echoCancellationActive =
                                localAec.getEnabled();
                    }
                } catch (Exception ignored) {
                    localAec = null;
                    echoCancellationActive = false;
                }
            } else {
                echoCancellationActive = false;
            }

            if (NoiseSuppressor.isAvailable()) {
                try {
                    localNoise =
                            NoiseSuppressor.create(
                                    sessionId
                            );

                    if (localNoise != null) {
                        localNoise.setEnabled(true);
                    }
                } catch (Exception ignored) {
                    localNoise = null;
                }
            }

            localRecord.startRecording();

            if (localRecord.getRecordingState()
                    != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException(
                        "Microfoonopname kon niet starten."
                );
            }

            running = true;
            audioRecord = localRecord;
            echoCanceler = localAec;
            noiseSuppressor = localNoise;

            final Recognizer threadRecognizer =
                    localRecognizer;
            final AudioRecord threadRecord =
                    localRecord;
            final AcousticEchoCanceler threadAec =
                    localAec;
            final NoiseSuppressor threadNoise =
                    localNoise;

            audioThread =
                    new Thread(
                            () -> captureLoop(
                                    generation,
                                    threadRecognizer,
                                    threadRecord,
                                    threadAec,
                                    threadNoise
                            ),
                            "MAATJE-Vosk-AEC"
                    );

            audioThread.start();

        } catch (Exception e) {
            running = false;
            ++captureGeneration;
            echoCancellationActive = false;

            releaseCaptureResources(
                    localRecord,
                    localAec,
                    localNoise,
                    localRecognizer
            );

            callback.onError(
                    "Offline voice-control starten mislukt: "
                            + e.getMessage()
            );
        }
    }

    private void captureLoop(
            int generation,
            Recognizer recognizer,
            AudioRecord record,
            AcousticEchoCanceler aec,
            NoiseSuppressor noise
    ) {
        android.os.Process.setThreadPriority(
                android.os.Process.THREAD_PRIORITY_AUDIO
        );

        byte[] buffer = new byte[4096];

        try {
            while (running
                    && captureGeneration
                    == generation) {
                int count =
                        record.read(
                                buffer,
                                0,
                                buffer.length
                        );

                if (count > 0) {
                    boolean complete =
                            recognizer.acceptWaveForm(
                                    buffer,
                                    count
                            );

                    inspect(
                            complete
                                    ? recognizer.getResult()
                                    : recognizer.getPartialResult()
                    );
                } else if (count
                        == AudioRecord.ERROR_INVALID_OPERATION
                        || count
                        == AudioRecord.ERROR_BAD_VALUE) {
                    throw new IllegalStateException(
                            "AudioRecord leesfout: "
                                    + count
                    );
                }
            }
        } catch (Exception e) {
            if (running
                    && captureGeneration
                    == generation) {
                activity.runOnUiThread(
                        () -> callback.onError(
                                "Offline voice-control fout: "
                                        + e.getMessage()
                        )
                );
            }
        } finally {
            releaseCaptureResources(
                    record,
                    aec,
                    noise,
                    recognizer
            );

            synchronized (this) {
                if (captureGeneration
                        == generation) {
                    running = false;
                    audioRecord = null;
                    echoCanceler = null;
                    noiseSuppressor = null;
                    audioThread = null;
                    echoCancellationActive = false;
                }
            }
        }
    }

    private void releaseCaptureResources(
            AudioRecord record,
            AcousticEchoCanceler aec,
            NoiseSuppressor noise,
            Recognizer recognizer
    ) {
        if (record != null) {
            try {
                if (record.getRecordingState()
                        == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop();
                }
            } catch (Exception ignored) {}

            try {
                record.release();
            } catch (Exception ignored) {}
        }

        if (aec != null) {
            try {
                aec.release();
            } catch (Exception ignored) {}
        }

        if (noise != null) {
            try {
                noise.release();
            } catch (Exception ignored) {}
        }

        if (recognizer != null) {
            try {
                recognizer.close();
            } catch (Exception ignored) {}
        }
    }

    synchronized void stop() {
        running = false;
        ++captureGeneration;
        echoCancellationActive = false;

        AudioRecord record =
                audioRecord;

        audioRecord = null;
        echoCanceler = null;
        noiseSuppressor = null;
        audioThread = null;

        if (record != null) {
            try {
                if (record.getRecordingState()
                        == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop();
                }
            } catch (Exception ignored) {}
        }
    }

    boolean isEchoCancellationActive() {
        return echoCancellationActive;
    }

    void destroy() {
        stop();
    }

    private void inspect(
            String hypothesis
    ) {
        if (detected
                || hypothesis == null
                || hypothesis.isEmpty()) {
            return;
        }

        String text =
                extractText(hypothesis);

        if (text.isEmpty()) return;

        String normalized =
                normalize(text);

        if (normalized.isEmpty()
                || "[unk]".equals(normalized)) {
            return;
        }

        if (!normalized.equals(lastHeard)) {
            lastHeard = normalized;

            activity.runOnUiThread(
                    () -> callback.onHeard(
                            normalized
                    )
            );
        }

        if (mode == Mode.STOP) {
            if (containsStopPhrase(normalized)) {
                detected = true;
                stop();

                activity.runOnUiThread(
                        callback::onStopDetected
                );
            }

            return;
        }

        if (containsWakePhrase(
                normalized,
                WakeWordSettings.sensitivity(
                        activity
                )
        )) {
            detected = true;
            stop();

            activity.runOnUiThread(
                    callback::onDetected
            );
        }
    }

    private String extractText(
            String json
    ) {
        try {
            JSONObject root =
                    new JSONObject(json);

            String partial =
                    root.optString(
                            "partial",
                            ""
                    ).trim();

            if (!partial.isEmpty()) {
                return partial;
            }

            return root.optString(
                    "text",
                    ""
            ).trim();

        } catch (Exception ignored) {
            return json.trim();
        }
    }

    private String normalize(
            String value
    ) {
        return value
                .toLowerCase(Locale.ROOT)
                .replace('é', 'e')
                .replace('è', 'e')
                .replaceAll(
                        "[^\\p{L}\\p{N}\\s]",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private boolean containsStopPhrase(
            String value
    ) {
        return value.equals("maatje stop")
                || value.equals("maartje stop")
                || value.equals("hey maatje stop")
                || value.equals("hee maatje stop")
                || value.equals("he maatje stop")
                || value.equals("stop maatje")
                || value.equals("stop maartje")
                || value.contains("maatje stop")
                || value.contains("maartje stop");
    }

    private boolean containsWakePhrase(
            String value,
            int sensitivity
    ) {
        if (value.equals("hey maatje")
                || value.equals("hee maatje")
                || value.equals("he maatje")
                || value.equals("hey maartje")
                || value.equals("hee maartje")
                || value.equals("he maartje")) {
            return true;
        }

        if (sensitivity >= 45) {
            if (value.equals("hoi maatje")
                    || value.equals("hoi maartje")
                    || value.equals("hey maat")
                    || value.equals("hee maat")
                    || value.equals("he maat")) {
                return true;
            }
        }

        if (sensitivity >= 70) {
            if (value.contains("hey maatje")
                    || value.contains("hee maatje")
                    || value.contains("he maatje")
                    || value.contains("hey maartje")
                    || value.contains("hee maartje")
                    || value.contains("he maartje")
                    || value.contains("hoi maatje")
                    || value.contains("hoi maartje")) {
                return true;
            }
        }

        if (sensitivity >= 88) {
            return value.equals("maatje")
                    || value.equals("maartje")
                    || value.equals("maat");
        }

        return false;
    }

    private String grammarForWake() {
        int sensitivity =
                WakeWordSettings.sensitivity(
                        activity
                );

        if (sensitivity >= 88) {
            return "["
                    + "\"hey maatje\","
                    + "\"hee maatje\","
                    + "\"he maatje\","
                    + "\"hey maartje\","
                    + "\"hee maartje\","
                    + "\"he maartje\","
                    + "\"hoi maatje\","
                    + "\"hoi maartje\","
                    + "\"hey maat\","
                    + "\"hee maat\","
                    + "\"he maat\","
                    + "\"maatje\","
                    + "\"maartje\","
                    + "\"maat\","
                    + "\"[unk]\""
                    + "]";
        }

        if (sensitivity >= 45) {
            return "["
                    + "\"hey maatje\","
                    + "\"hee maatje\","
                    + "\"he maatje\","
                    + "\"hey maartje\","
                    + "\"hee maartje\","
                    + "\"he maartje\","
                    + "\"hoi maatje\","
                    + "\"hoi maartje\","
                    + "\"hey maat\","
                    + "\"hee maat\","
                    + "\"he maat\","
                    + "\"[unk]\""
                    + "]";
        }

        return "["
                + "\"hey maatje\","
                + "\"hee maatje\","
                + "\"he maatje\","
                + "\"hey maartje\","
                + "\"hee maartje\","
                + "\"he maartje\","
                + "\"[unk]\""
                + "]";
    }
}
