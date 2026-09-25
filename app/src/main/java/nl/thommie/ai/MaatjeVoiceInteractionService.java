package nl.thommie.ai;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.voice.VoiceInteractionService;

public class MaatjeVoiceInteractionService
        extends VoiceInteractionService {

    static final String EXTRA_WAKE_SOURCE =
            "maatje_wake_source";

    private static volatile
    MaatjeVoiceInteractionService instance;

    private static volatile
    boolean activityVisible = false;

    private final Handler handler =
            new Handler(
                    Looper.getMainLooper()
            );

    private BackgroundWakeWord
            backgroundWakeWord;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        backgroundWakeWord =
                new BackgroundWakeWord(
                        this,
                        new BackgroundWakeWord.Callback() {
                            @Override
                            public void onReady() {
                                refreshWake();
                            }

                            @Override
                            public void onDetected() {
                                onWakeDetected();
                            }

                            @Override
                            public void onError(
                                    String message
                            ) {
                                handler.postDelayed(
                                        MaatjeVoiceInteractionService
                                                .this
                                                ::refreshWake,
                                        1500L
                                );
                            }
                        }
                );
    }

    @Override
    public void onReady() {
        super.onReady();

        if (backgroundWakeWord != null) {
            backgroundWakeWord.prepare();
        }

        refreshWake();
    }

    @Override
    public void onShutdown() {
        stopWake();
        super.onShutdown();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(
                null
        );

        if (backgroundWakeWord != null) {
            backgroundWakeWord.destroy();
            backgroundWakeWord = null;
        }

        if (instance == this) {
            instance = null;
        }

        super.onDestroy();
    }

    static void setActivityVisible(
            boolean visible
    ) {
        activityVisible = visible;

        MaatjeVoiceInteractionService
                service = instance;

        if (service != null) {
            service.handler.post(
                    service::refreshWake
            );
        }
    }

    static void refreshFromActivity() {
        MaatjeVoiceInteractionService
                service = instance;

        if (service != null) {
            service.handler.post(
                    service::refreshWake
            );
        }
    }

    private void refreshWake() {
        handler.removeCallbacks(
                this::refreshWake
        );

        if (backgroundWakeWord == null) {
            return;
        }

        boolean shouldListen =
                !activityVisible
                        && WakeWordSettings.enabled(
                                this
                        )
                        && checkSelfPermission(
                                Manifest.permission.RECORD_AUDIO
                        )
                        == PackageManager.PERMISSION_GRANTED;

        if (!shouldListen) {
            backgroundWakeWord.stop();
            return;
        }

        if (!backgroundWakeWord.isReady()) {
            backgroundWakeWord.prepare();
            return;
        }

        backgroundWakeWord.start();
    }

    private void stopWake() {
        if (backgroundWakeWord != null) {
            backgroundWakeWord.stop();
        }
    }

    private void onWakeDetected() {
        if (activityVisible) {
            return;
        }

        Bundle args =
                new Bundle();

        args.putString(
                EXTRA_WAKE_SOURCE,
                "hotword"
        );

        try {
            showSession(
                    args,
                    0
            );
        } catch (Exception ignored) {
            launchMainFallback();
        }

        handler.postDelayed(
                this::refreshWake,
                2500L
        );
    }

    private void launchMainFallback() {
        try {
            android.content.Intent intent =
                    new android.content.Intent(
                            this,
                            MainActivity.class
                    );

            intent.addFlags(
                    android.content.Intent
                            .FLAG_ACTIVITY_NEW_TASK
                            | android.content.Intent
                            .FLAG_ACTIVITY_CLEAR_TOP
                            | android.content.Intent
                            .FLAG_ACTIVITY_SINGLE_TOP
            );

            intent.putExtra(
                    MainActivity
                            .EXTRA_ASSISTANT_INVOCATION,
                    true
            );

            startActivity(intent);
        } catch (Exception ignored) {}
    }
}
