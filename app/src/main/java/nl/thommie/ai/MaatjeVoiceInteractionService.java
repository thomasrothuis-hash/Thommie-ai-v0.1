package nl.thommie.ai;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.voice.VoiceInteractionService;
import android.service.voice.VoiceInteractionSession;

public class MaatjeVoiceInteractionService
        extends VoiceInteractionService {

    static final String EXTRA_WAKE_SOURCE =
            "maatje_wake_source";

    private static volatile
    MaatjeVoiceInteractionService instance;

    private static volatile
    boolean activityVisible = false;

    private static volatile
    boolean sessionVisible = false;

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
        refreshInstance();
    }

    static void setSessionVisible(
            boolean visible
    ) {
        sessionVisible = visible;
        refreshInstance();
    }

    static void claimMicrophoneForSession() {
        sessionVisible = true;

        MaatjeVoiceInteractionService
                service = instance;

        if (service != null
                && service.backgroundWakeWord != null) {
            service.backgroundWakeWord
                    .stopAndWait(1000L);
        }
    }

    static void refreshFromActivity() {
        refreshInstance();
    }

    private static void refreshInstance() {
        MaatjeVoiceInteractionService
                service = instance;

        if (service != null) {
            service.handler.post(
                    service::refreshWake
            );
        }
    }

    private void refreshWake() {
        if (backgroundWakeWord == null) {
            return;
        }

        boolean shouldListen =
                !activityVisible
                        && !sessionVisible
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
        if (activityVisible
                || sessionVisible) {
            return;
        }

        sessionVisible = true;

        if (backgroundWakeWord != null) {
            backgroundWakeWord
                    .stopAndWait(1000L);
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
                    VoiceInteractionSession
                            .SHOW_WITH_ASSIST
                            | VoiceInteractionSession
                            .SHOW_WITH_SCREENSHOT
            );

        } catch (Exception ignored) {
            sessionVisible = false;
            refreshWake();
        }
    }
}
