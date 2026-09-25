package nl.thommie.ai;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.StorageService;

import java.util.Locale;

final class BackgroundWakeWord {

    interface Callback {
        void onReady();
        void onDetected();
        void onError(String message);
    }

    private static final int SAMPLE_RATE = 16000;

    private final Context context;
    private final Callback callback;
    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private Model model;
    private AudioRecord audioRecord;
    private Thread audioThread;

    private volatile boolean preparing = false;
    private volatile boolean ready = false;
    private volatile boolean running = false;
    private volatile int generation = 0;

    BackgroundWakeWord(
            Context context,
            Callback callback
    ) {
        this.context =
                context.getApplicationContext();
        this.callback = callback;
    }

    synchronized void prepare() {
        if (ready || preparing) {
            return;
        }

        preparing = true;

        StorageService.unpack(
                context,
                "model-nl",
                "maatje-vosk-model",
                loaded -> {
                    model = loaded;
                    preparing = false;
                    ready = true;
                    callback.onReady();
                },
                error -> {
                    preparing = false;
                    ready = false;
                    callback.onError(
                            "Achtergrond wake-model laden mislukt: "
                                    + error.getMessage()
                    );
                }
        );
    }

    boolean isReady() {
        return ready;
    }

    synchronized void start() {
        if (running) {
            return;
        }

        if (context.checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {
            callback.onError(
                    "Microfoontoestemming ontbreekt voor achtergrond wake word."
            );
            return;
        }

        if (!ready || model == null) {
            prepare();
            return;
        }

        final int localGeneration =
                ++generation;

        Recognizer recognizer = null;
        AudioRecord record = null;

        try {
            recognizer =
                    new Recognizer(
                            model,
                            SAMPLE_RATE,
                            grammar()
                    );

            int minBuffer =
                    AudioRecord.getMinBufferSize(
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                    );

            if (minBuffer <= 0) {
                throw new IllegalStateException(
                        "Geen geldige microfoonbuffer."
                );
            }

            int bufferSize =
                    Math.max(
                            minBuffer * 2,
                            4096
                    );

            record =
                    new AudioRecord(
                            MediaRecorder.AudioSource.VOICE_RECOGNITION,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize
                    );

            if (record.getState()
                    != AudioRecord.STATE_INITIALIZED) {
                try {
                    record.release();
                } catch (Exception ignored) {}

                record =
                        new AudioRecord(
                                MediaRecorder.AudioSource.MIC,
                                SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                bufferSize
                        );
            }

            if (record.getState()
                    != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException(
                        "Microfoon kon niet initialiseren."
                );
            }

            record.startRecording();

            if (record.getRecordingState()
                    != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException(
                        "Microfoonopname kon niet starten."
                );
            }

            running = true;
            audioRecord = record;

            final Recognizer threadRecognizer =
                    recognizer;
            final AudioRecord threadRecord =
                    record;

            audioThread =
                    new Thread(
                            () -> captureLoop(
                                    localGeneration,
                                    threadRecognizer,
                                    threadRecord
                            ),
                            "MAATJE-Assistant-Wake"
                    );

            audioThread.start();

        } catch (Exception e) {
            running = false;
            ++generation;
            release(record, recognizer);

            callback.onError(
                    "Achtergrond wake word starten mislukt: "
                            + safeMessage(e)
            );
        }
    }

    private void captureLoop(
            int localGeneration,
            Recognizer recognizer,
            AudioRecord record
    ) {
        android.os.Process.setThreadPriority(
                android.os.Process.THREAD_PRIORITY_AUDIO
        );

        byte[] buffer =
                new byte[4096];

        try {
            while (running
                    && generation
                    == localGeneration) {

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
                    && generation
                    == localGeneration) {
                mainHandler.post(
                        () -> callback.onError(
                                "Achtergrond wake word fout: "
                                        + safeMessage(e)
                        )
                );
            }
        } finally {
            release(
                    record,
                    recognizer
            );

            synchronized (this) {
                if (audioRecord == record) {
                    audioRecord = null;
                }

                if (audioThread
                        == Thread.currentThread()) {
                    audioThread = null;
                }

                if (generation
                        == localGeneration) {
                    running = false;
                }
            }
        }
    }

    private void inspect(
            String hypothesis
    ) {
        if (hypothesis == null
                || hypothesis.isEmpty()) {
            return;
        }

        String text =
                extractText(
                        hypothesis
                );

        if (text.isEmpty()) {
            return;
        }

        String normalized =
                normalize(text);

        if (normalized.isEmpty()
                || "unk".equals(normalized)
                || "[unk]".equals(normalized)) {
            return;
        }

        if (containsWakePhrase(
                normalized,
                WakeWordSettings.sensitivity(
                        context
                )
        )) {
            stop();

            mainHandler.post(
                    callback::onDetected
            );
        }
    }

    void stop() {
        AudioRecord record;

        synchronized (this) {
            running = false;
            ++generation;
            record = audioRecord;
        }

        if (record != null) {
            try {
                if (record.getRecordingState()
                        == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop();
                }
            } catch (Exception ignored) {}
        }
    }

    void stopAndWait(
            long timeoutMs
    ) {
        Thread thread;

        stop();

        synchronized (this) {
            thread = audioThread;
        }

        if (thread != null
                && thread != Thread.currentThread()) {
            try {
                thread.join(
                        Math.max(
                                0L,
                                timeoutMs
                        )
                );
            } catch (InterruptedException e) {
                Thread.currentThread()
                        .interrupt();
            }
        }
    }

    synchronized void destroy() {
        stopAndWait(900L);

        if (model != null) {
            try {
                model.close();
            } catch (Exception ignored) {}
            model = null;
        }

        ready = false;
        preparing = false;
    }

    private void release(
            AudioRecord record,
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

        if (recognizer != null) {
            try {
                recognizer.close();
            } catch (Exception ignored) {}
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

    private String grammar() {
        int sensitivity =
                WakeWordSettings.sensitivity(
                        context
                );

        StringBuilder g =
                new StringBuilder(
                        "["
                );

        g.append("\"hey maatje\",")
                .append("\"hee maatje\",")
                .append("\"he maatje\",")
                .append("\"hey maartje\",")
                .append("\"hee maartje\",")
                .append("\"he maartje\",");

        if (sensitivity >= 45) {
            g.append("\"hoi maatje\",")
                    .append("\"hoi maartje\",")
                    .append("\"hey maat\",")
                    .append("\"hee maat\",")
                    .append("\"he maat\",");
        }

        if (sensitivity >= 88) {
            g.append("\"maatje\",")
                    .append("\"maartje\",")
                    .append("\"maat\",");
        }

        g.append("\"[unk]\"]");

        return g.toString();
    }

    private String safeMessage(
            Exception e
    ) {
        String message =
                e.getMessage();

        return message == null
                || message.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : message.trim();
    }
}
