package nl.thommie.ai;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

import java.util.List;

public class MaatjeRecognitionService
        extends RecognitionService {

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private SpeechRecognizer recognizer;

    @Override
    protected void onStartListening(
            Intent recognizerIntent,
            Callback callback
    ) {
        mainHandler.post(() -> {
            destroyRecognizer();

            ComponentName backend =
                    findExternalRecognizer();

            if (backend == null) {
                callback.error(
                        SpeechRecognizer
                                .ERROR_CLIENT
                );
                return;
            }

            try {
                recognizer =
                        SpeechRecognizer
                                .createSpeechRecognizer(
                                        this,
                                        backend
                                );

                recognizer
                        .setRecognitionListener(
                                new RecognitionListener() {
                                    @Override
                                    public void onReadyForSpeech(
                                            Bundle params
                                    ) {
                                        callback.readyForSpeech(
                                                params
                                        );
                                    }

                                    @Override
                                    public void onBeginningOfSpeech() {
                                        callback.beginningOfSpeech();
                                    }

                                    @Override
                                    public void onRmsChanged(
                                            float rmsdB
                                    ) {
                                        callback.rmsChanged(
                                                rmsdB
                                        );
                                    }

                                    @Override
                                    public void onBufferReceived(
                                            byte[] buffer
                                    ) {
                                        callback.bufferReceived(
                                                buffer
                                        );
                                    }

                                    @Override
                                    public void onEndOfSpeech() {
                                        callback.endOfSpeech();
                                    }

                                    @Override
                                    public void onError(
                                            int error
                                    ) {
                                        callback.error(error);
                                        destroyRecognizer();
                                    }

                                    @Override
                                    public void onResults(
                                            Bundle results
                                    ) {
                                        callback.results(results);
                                        destroyRecognizer();
                                    }

                                    @Override
                                    public void onPartialResults(
                                            Bundle partialResults
                                    ) {
                                        callback.partialResults(
                                                partialResults
                                        );
                                    }

                                    @Override
                                    public void onEvent(
                                            int eventType,
                                            Bundle params
                                    ) {}
                                }
                        );

                recognizer.startListening(
                        recognizerIntent
                );

            } catch (Exception e) {
                callback.error(
                        SpeechRecognizer
                                .ERROR_CLIENT
                );
                destroyRecognizer();
            }
        });
    }

    @Override
    protected void onStopListening(
            Callback callback
    ) {
        mainHandler.post(() -> {
            if (recognizer != null) {
                try {
                    recognizer.stopListening();
                } catch (Exception ignored) {}
            }
        });
    }

    @Override
    protected void onCancel(
            Callback callback
    ) {
        mainHandler.post(() -> {
            if (recognizer != null) {
                try {
                    recognizer.cancel();
                } catch (Exception ignored) {}
            }

            destroyRecognizer();
        });
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacksAndMessages(
                null
        );

        destroyRecognizer();
        super.onDestroy();
    }

    private ComponentName findExternalRecognizer() {
        PackageManager pm =
                getPackageManager();

        Intent probe =
                new Intent(
                        RecognitionService
                                .SERVICE_INTERFACE
                );

        List<ResolveInfo> services =
                pm.queryIntentServices(
                        probe,
                        PackageManager
                                .MATCH_DEFAULT_ONLY
                );

        if (services == null
                || services.isEmpty()) {
            services =
                    pm.queryIntentServices(
                            probe,
                            0
                    );
        }

        if (services == null) {
            return null;
        }

        ResolveInfo fallback = null;

        for (ResolveInfo info : services) {
            if (info == null
                    || info.serviceInfo == null) {
                continue;
            }

            String packageName =
                    info.serviceInfo.packageName;

            if (getPackageName()
                    .equals(packageName)) {
                continue;
            }

            if (fallback == null) {
                fallback = info;
            }

            String lower =
                    packageName == null
                            ? ""
                            : packageName
                            .toLowerCase();

            if (lower.contains("google")
                    || lower.contains("lineage")
                    || lower.contains("speech")) {
                return new ComponentName(
                        packageName,
                        info.serviceInfo.name
                );
            }
        }

        if (fallback == null) {
            return null;
        }

        return new ComponentName(
                fallback.serviceInfo.packageName,
                fallback.serviceInfo.name
        );
    }

    private void destroyRecognizer() {
        if (recognizer != null) {
            try {
                recognizer.destroy();
            } catch (Exception ignored) {}

            recognizer = null;
        }
    }
}
