package nl.thommie.ai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQ_AUDIO = 1001;
    private static final int BG = Color.rgb(5, 7, 9);
    private static final int PANEL = Color.rgb(13, 18, 20);
    private static final int MINT = Color.rgb(117, 243, 208);
    private static final int TEXT = Color.rgb(235, 245, 242);
    private static final int MUTED = Color.rgb(136, 154, 150);

    private final ExecutorService chatExecutor =
            Executors.newSingleThreadExecutor();
    private final ExecutorService voiceExecutor =
            Executors.newSingleThreadExecutor();

    private TextView stateText;
    private TextView wakeDebugText;
    private TextView transcript;
    private EditText input;
    private Button micButton;
    private Button sendButton;
    private SpeechRecognizer speechRecognizer;
    private MediaPlayer mediaPlayer;
    private OfflineWakeWord offlineWakeWord;
    private String lastAssistantReply = "";
    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private boolean wakeWordEnabled = true;
    private boolean wakeWordListening = false;
    private boolean commandListening = false;
    private boolean ignoreNextRecognitionError = false;
    private boolean appVisible = false;
    private boolean chatBusy = false;
    private boolean pendingManualPermission = false;
    private boolean conversationModeActive = false;
    private long conversationExpiresAt = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );

        buildUi();
        initSpeechRecognizer();
        wakeWordEnabled = WakeWordSettings.enabled(this);

        offlineWakeWord =
                new OfflineWakeWord(
                        this,
                        new OfflineWakeWord.Callback() {
                            @Override
                            public void onReady() {
                                stateText.setText(
                                        "WAKE MODEL READY"
                                );

                                scheduleWakeListening(
                                        300
                                );
                            }

                            @Override
                            public void onDetected() {
                                wakeWordListening = false;

                                beginConversationSession();

                                stateText.setText("YES?");

                                if (wakeDebugText != null) {
                                    wakeDebugText.setText(
                                            "HEARD • WAKE WORD"
                                    );
                                }

                                mainHandler.postDelayed(
                                        MainActivity.this
                                                ::startCommandListeningInternal,
                                        350
                                );
                            }

                            @Override
                            public void onStopDetected() {
                                handleSpokenStop();
                            }

                            @Override
                            public void onHeard(
                                    String text
                            ) {
                                updateWakeDebug(text);
                            }

                            @Override
                            public void onError(
                                    String message
                            ) {
                                stateText.setText(
                                        "WAKE ERROR"
                                );

                                Toast.makeText(
                                        MainActivity.this,
                                        message,
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                );

        offlineWakeWord.prepare();

        if (SecurePrefs.loadApiKey(this).isEmpty()) {
            showApiKeyDialog(true);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(22), dp(30), dp(22), dp(22));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("MAATJE");
        title.setTextColor(TEXT);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(
                title,
                new LinearLayout.LayoutParams(
                        0,
                        dp(50),
                        1f
                )
        );

        Button settings = button("⚙", PANEL, TEXT);
        settings.setOnClickListener(
                v -> showSettingsMenu()
        );
        header.addView(
                settings,
                new LinearLayout.LayoutParams(
                        dp(54),
                        dp(46)
                )
        );
        root.addView(header);

        TextView version = new TextView(this);
        version.setText(
                "v0.8  •  PERSONAL AI TERMINAL"
        );
        version.setTextColor(MUTED);
        version.setTextSize(11);
        version.setLetterSpacing(.16f);
        root.addView(version);

        TextView orb = new TextView(this);
        orb.setText("●");
        orb.setTextColor(MINT);
        orb.setTextSize(90);
        orb.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams orbLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(125)
                );
        orbLp.topMargin = dp(8);
        root.addView(orb, orbLp);

        stateText = new TextView(this);
        stateText.setText("READY");
        stateText.setGravity(Gravity.CENTER);
        stateText.setTextColor(MINT);
        stateText.setTextSize(13);
        stateText.setTypeface(Typeface.DEFAULT_BOLD);
        stateText.setLetterSpacing(.20f);
        root.addView(stateText);

        wakeDebugText = new TextView(this);
        wakeDebugText.setGravity(Gravity.CENTER);
        wakeDebugText.setTextColor(MUTED);
        wakeDebugText.setTextSize(11);
        wakeDebugText.setPadding(
                0,
                dp(6),
                0,
                0
        );

        wakeDebugText.setVisibility(
                WakeWordSettings.debugEnabled(this)
                        ? View.VISIBLE
                        : View.GONE
        );

        root.addView(wakeDebugText);

        GradientDrawable panelBg =
                roundRect(
                        PANEL,
                        22,
                        Color.rgb(31, 43, 43)
                );

        transcript = new TextView(this);
        transcript.setText(
                "Welkom.\n\n"
                        + "MAATJE v0.8 gebruikt OpenAI cloud voice, "
                        + "blijvend gespreksgeheugen, lokaal profielgeheugen en lokale \"Hey Maatje\" activatie."
        );
        transcript.setTextColor(TEXT);
        transcript.setTextSize(16);
        transcript.setLineSpacing(0, 1.25f);
        transcript.setPadding(
                dp(18),
                dp(18),
                dp(18),
                dp(18)
        );
        transcript.setBackground(panelBg);
        transcript.setMovementMethod(
                new ScrollingMovementMethod()
        );
        transcript.setVerticalScrollBarEnabled(true);
        transcript.setScrollBarStyle(
                View.SCROLLBARS_INSIDE_OVERLAY
        );

        LinearLayout.LayoutParams transcriptLp =
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1f
                );
        transcriptLp.topMargin = dp(20);
        transcriptLp.bottomMargin = dp(16);
        root.addView(transcript, transcriptLp);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(
                LinearLayout.HORIZONTAL
        );
        composer.setGravity(
                Gravity.CENTER_VERTICAL
        );

        micButton = button("🎙", PANEL, TEXT);
        micButton.setOnClickListener(
                v -> startListening()
        );
        composer.addView(
                micButton,
                new LinearLayout.LayoutParams(
                        dp(58),
                        dp(58)
                )
        );

        input = new EditText(this);
        input.setHint("Vraag iets…");
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setTextSize(16);
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setPadding(
                dp(16),
                dp(8),
                dp(16),
                dp(8)
        );
        input.setBackground(
                roundRect(
                        PANEL,
                        18,
                        Color.rgb(31, 43, 43)
                )
        );

        LinearLayout.LayoutParams inputLp =
                new LinearLayout.LayoutParams(
                        0,
                        dp(58),
                        1f
                );
        inputLp.leftMargin = dp(10);
        inputLp.rightMargin = dp(10);
        composer.addView(input, inputLp);

        sendButton = button("➜", MINT, BG);
        sendButton.setTextSize(21);
        sendButton.setOnClickListener(
                v -> sendCurrentInput()
        );
        composer.addView(
                sendButton,
                new LinearLayout.LayoutParams(
                        dp(58),
                        dp(58)
                )
        );

        root.addView(composer);
        setContentView(root);
    }

    private void sendCurrentInput() {
        String q =
                input.getText()
                        .toString()
                        .trim();

        if (q.isEmpty()) return;

        input.setText("");
        ask(q);
    }

    private void ask(String q) {
        PersonalitySettings.CommandResult personalityCommand =
                PersonalitySettings.handleCommand(this, q);

        if (personalityCommand.handled) {
            append("\n\nJIJ\n" + q);
            append("\n\nMAATJE\n" + personalityCommand.message);
            lastAssistantReply = personalityCommand.message;
            speak(personalityCommand.message);
            return;
        }

        if (PersonalitySettings.captureHumorFeedback(
                this,
                q,
                lastAssistantReply
        )) {
            Toast.makeText(
                    this,
                    "Humorprofiel bijgewerkt.",
                    Toast.LENGTH_SHORT
            ).show();
        }

        String key = SecurePrefs.loadApiKey(this);

        if (key.isEmpty()) {
            showApiKeyDialog(true);
            return;
        }

        boolean remembered =
                MemoryStore.captureExplicitMemory(
                        this,
                        q
                );

        append("\n\nJIJ\n" + q);

        if (remembered) {
            append(
                    "\n\nGEHEUGEN\n"
                            + "Opgeslagen in lokaal profielgeheugen."
            );
        }

        setBusy(true, "THINKING");

        chatExecutor.submit(() -> {
            try {
                String conversationId =
                        MemoryStore.getConversationId(
                                MainActivity.this
                        );

                if (conversationId.isEmpty()) {
                    conversationId =
                            OpenAiClient.createConversation(
                                    key
                            );

                    MemoryStore.setConversationId(
                            MainActivity.this,
                            conversationId
                    );
                }

                String profileMemory =
                        MemoryStore.getProfile(
                                MainActivity.this
                        );

                OpenAiClient.Reply reply =
                        OpenAiClient.ask(
                                key,
                                "gpt-5.6-luna",
                                conversationId,
                                q,
                                profileMemory,
                                PersonalitySettings.prompt(MainActivity.this)
                        );

                runOnUiThread(() -> {
                    append(
                            "\n\nMAATJE\n"
                                    + reply.text
                    );
                    lastAssistantReply = reply.text;
                    setBusy(false, "READY");
                    speak(reply.text);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    append(
                            "\n\nFOUT\n"
                                    + e.getMessage()
                    );
                    setBusy(false, "ERROR");
                    resumeAfterAssistant(700);
                });
            }
        });
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer
                .isRecognitionAvailable(this)) {
            return;
        }

        speechRecognizer =
                SpeechRecognizer
                        .createSpeechRecognizer(this);

        speechRecognizer.setRecognitionListener(
                new RecognitionListener() {
                    public void onReadyForSpeech(
                            Bundle params
                    ) {
                        if (wakeWordListening) {
                            stateText.setText(
                                    "STANDBY • HEY MAATJE"
                            );
                        } else {
                            stateText.setText(
                                    "LISTENING"
                            );
                        }
                    }

                    public void onBeginningOfSpeech() {
                        if (!wakeWordListening) {
                            stateText.setText(
                                    "LISTENING"
                            );
                        }
                    }

                    public void onRmsChanged(
                            float rmsdB
                    ) {}

                    public void onBufferReceived(
                            byte[] buffer
                    ) {}

                    public void onEndOfSpeech() {
                        if (wakeWordListening) {
                            stateText.setText(
                                    "CHECKING"
                            );
                        } else if (commandListening) {
                            stateText.setText(
                                    "PROCESSING SPEECH"
                            );
                        }
                    }

                    public void onError(int error) {
                        if (ignoreNextRecognitionError) {
                            ignoreNextRecognitionError = false;
                            return;
                        }

                        boolean wasWake =
                                wakeWordListening;

                        boolean wasCommand =
                                commandListening;

                        wakeWordListening = false;
                        commandListening = false;

                        if (wasWake) {
                            stateText.setText(
                                    "STANDBY • HEY MAATJE"
                            );

                            scheduleWakeListening(
                                    error
                                            == SpeechRecognizer.ERROR_NO_MATCH
                                            ? 200
                                            : 700
                            );
                            return;
                        }

                        if (wasCommand) {
                            setBusy(false, "READY");

                            if (conversationModeActive
                                    && isConversationWindowOpen()
                                    && (error
                                    == SpeechRecognizer.ERROR_NO_MATCH
                                    || error
                                    == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {

                                scheduleFollowUpListening(
                                        350,
                                        false
                                );
                                return;
                            }

                            if (conversationModeActive) {
                                endConversationSession();
                            }

                            Toast.makeText(
                                    MainActivity.this,
                                    "Spraakherkenning fout: "
                                            + error,
                                    Toast.LENGTH_SHORT
                            ).show();

                            scheduleWakeListening(700);
                        }
                    }

                    public void onResults(
                            Bundle results
                    ) {
                        boolean wasWake =
                                wakeWordListening;

                        boolean wasCommand =
                                commandListening;

                        wakeWordListening = false;
                        commandListening = false;

                        ArrayList<String> list =
                                results.getStringArrayList(
                                        SpeechRecognizer
                                                .RESULTS_RECOGNITION
                                );

                        if (wasWake) {
                            handleWakeResults(list);
                            return;
                        }

                        if (wasCommand) {
                            setBusy(false, "READY");

                            if (list != null
                                    && !list.isEmpty()) {
                                String value =
                                        list.get(0);

                                if (isConversationEndCommand(value)) {
                                    endConversationSession();
                                    stateText.setText(
                                            "STANDBY • HEY MAATJE"
                                    );
                                    scheduleWakeListening(300);
                                    return;
                                }

                                pauseConversationTimer();

                                input.setText(value);
                                input.setSelection(
                                        input.length()
                                );

                                ask(value);
                                input.setText("");
                            } else if (conversationModeActive
                                    && isConversationWindowOpen()) {
                                scheduleFollowUpListening(
                                        350,
                                        false
                                );
                            } else {
                                endConversationSession();
                                scheduleWakeListening(
                                        400
                                );
                            }
                        }
                    }

                    public void onPartialResults(
                            Bundle partialResults
                    ) {
                        if (!wakeWordListening) {
                            return;
                        }

                        ArrayList<String> list =
                                partialResults.getStringArrayList(
                                        SpeechRecognizer
                                                .RESULTS_RECOGNITION
                                );

                        String command =
                                extractWakeCommand(list);

                        if (command == null) {
                            return;
                        }

                        wakeWordListening = false;
                        commandListening = false;
                        ignoreNextRecognitionError = true;

                        stateText.setText("YES?");

                        try {
                            speechRecognizer.cancel();
                        } catch (Exception ignored) {}

                        final String wakeCommand =
                                command;

                        mainHandler.postDelayed(
                                () -> {
                                    ignoreNextRecognitionError = false;

                                    if (!wakeCommand.isEmpty()) {
                                        ask(wakeCommand);
                                    } else {
                                        startCommandListeningInternal();
                                    }
                                },
                                650
                        );
                    }

                    public void onEvent(
                            int eventType,
                            Bundle params
                    ) {}
                }
        );
    }

    private void startListening() {
        if (checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {
            pendingManualPermission = true;

            requestPermissions(
                    new String[]{
                            Manifest.permission.RECORD_AUDIO
                    },
                    REQ_AUDIO
            );
            return;
        }

        pendingManualPermission = false;

        if (speechRecognizer == null) {
            Toast.makeText(
                    this,
                    "Geen Android spraakherkenner beschikbaar.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        stopPlayer();
        stopRecognitionSession();

        beginConversationSession();

        mainHandler.postDelayed(
                this::startCommandListeningInternal,
                250
        );
    }

    private void startCommandListeningInternal() {
        if (offlineWakeWord != null) {
            offlineWakeWord.stop();
        }

        wakeWordListening = false;

        if (!appVisible
                || speechRecognizer == null
                || chatBusy) {
            return;
        }

        wakeWordListening = false;
        commandListening = true;

        stateText.setText(
                conversationModeActive
                        ? "CONVERSATION • LISTENING"
                        : "LISTENING"
        );

        try {
            speechRecognizer.startListening(
                    createRecognizerIntent(false)
            );
        } catch (Exception e) {
            commandListening = false;

            Toast.makeText(
                    this,
                    "Luisteren starten mislukt: "
                            + e.getMessage(),
                    Toast.LENGTH_SHORT
            ).show();

            scheduleWakeListening(700);
        }
    }

    private void startWakeListening() {
        wakeWordEnabled =
                WakeWordSettings.enabled(this);

        if (!wakeWordEnabled
                || !appVisible
                || chatBusy
                || mediaPlayer != null
                || offlineWakeWord == null
                || wakeWordListening
                || commandListening) {
            return;
        }

        if (checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {
            stateText.setText("MIC PERMISSION");
            return;
        }

        if (!offlineWakeWord.isReady()) {
            stateText.setText(
                    "PREPARING WAKE MODEL"
            );

            offlineWakeWord.prepare();
            return;
        }

        wakeWordListening = true;
        commandListening = false;

        stateText.setText(
                "STANDBY • HEY MAATJE"
        );

        offlineWakeWord.start();
    }

    private Intent createRecognizerIntent(
            boolean partialResults
    ) {
        Intent intent =
                new Intent(
                        RecognizerIntent
                                .ACTION_RECOGNIZE_SPEECH
                );

        intent.putExtra(
                RecognizerIntent
                        .EXTRA_LANGUAGE_MODEL,
                RecognizerIntent
                        .LANGUAGE_MODEL_FREE_FORM
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "nl-NL"
        );

        intent.putExtra(
                RecognizerIntent
                        .EXTRA_PARTIAL_RESULTS,
                partialResults
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                5
        );

        return intent;
    }

    private void handleWakeResults(
            ArrayList<String> results
    ) {
        String command =
                extractWakeCommand(results);

        if (command == null) {
            stateText.setText(
                    "STANDBY • HEY MAATJE"
            );

            scheduleWakeListening(250);
            return;
        }

        stateText.setText("YES?");

        if (!command.isEmpty()) {
            ask(command);
            return;
        }

        mainHandler.postDelayed(
                this::startCommandListeningInternal,
                300
        );
    }

    private String extractWakeCommand(
            ArrayList<String> results
    ) {
        if (results == null) return null;

        String[] triggers = {
                "hey maatje",
                "hee maatje",
                "hé maatje",
                "hey maartje",
                "hee maartje",
                "hé maartje",
                "hey maatie",
                "hee maatie"
        };

        for (String result : results) {
            if (result == null) continue;

            String normalized =
                    result.toLowerCase(java.util.Locale.ROOT)
                            .replaceAll(
                                    "[^\\\\p{L}\\\\p{N}\\\\s]",
                                    " "
                            )
                            .replaceAll(
                                    "\\\\s+",
                                    " "
                            )
                            .trim();

            for (String trigger : triggers) {
                int index =
                        normalized.indexOf(trigger);

                if (index >= 0) {
                    return normalized
                            .substring(
                                    index
                                            + trigger.length()
                            )
                            .trim();
                }
            }
        }

        return null;
    }

    private void scheduleWakeListening(
            long delayMs
    ) {
        mainHandler.removeCallbacks(
                wakeRestartRunnable
        );

        if (!wakeWordEnabled
                || !appVisible
                || chatBusy
                || conversationModeActive) {
            return;
        }

        mainHandler.postDelayed(
                wakeRestartRunnable,
                delayMs
        );
    }

    private final Runnable wakeRestartRunnable =
            new Runnable() {
                @Override
                public void run() {
                    startWakeListening();
                }
            };

    private void stopRecognitionSession() {
        mainHandler.removeCallbacks(
                wakeRestartRunnable
        );

        if (offlineWakeWord != null) {
            offlineWakeWord.stop();
        }

        wakeWordListening = false;

        if (speechRecognizer == null) {
            commandListening = false;
            return;
        }

        if (commandListening) {
            ignoreNextRecognitionError = true;
        }

        commandListening = false;

        try {
            speechRecognizer.cancel();
        } catch (Exception ignored) {}
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == REQ_AUDIO
                && grantResults.length > 0
                && grantResults[0]
                == PackageManager
                .PERMISSION_GRANTED) {

            if (pendingManualPermission) {
                pendingManualPermission = false;
                startListening();
            } else {
                scheduleWakeListening(300);
            }
        }
    }

    private void showSettingsMenu() {
        String[] options = {
                "API-key",
                "Stem & audio",
                "Geheugen",
                "Persoonlijkheid",
                "Gespreksmodus",
                "Wake word"
        };

        new AlertDialog.Builder(this)
                .setTitle(
                        "MAATJE v0.8 – Instellingen"
                )
                .setItems(
                        options,
                        (dialog, which) -> {
                            if (which == 0) {
                                showApiKeyDialog(false);
                            } else if (which == 1) {
                                VoiceSettings.show(
                                        this,
                                        this::testCloudVoice
                                );
                            } else if (which == 2) {
                                showMemoryDialog();
                            } else if (which == 3) {
                                PersonalitySettings.show(this);
                            } else if (which == 4) {
                                ConversationSettings.show(
                                        this,
                                        enabled -> {
                                            if (!enabled) {
                                                endConversationSession();
                                                scheduleWakeListening(300);
                                            }
                                        }
                                );
                            } else {
                                WakeWordSettings.show(
                                        this,
                                        enabled -> {
                                            wakeWordEnabled = enabled;

                                            updateWakeDebugVisibility();
                                            stopRecognitionSession();

                                            if (enabled) {
                                                if (checkSelfPermission(
                                                        Manifest.permission.RECORD_AUDIO
                                                ) != PackageManager.PERMISSION_GRANTED) {
                                                    pendingManualPermission = false;

                                                    requestPermissions(
                                                            new String[]{
                                                                    Manifest.permission.RECORD_AUDIO
                                                            },
                                                            REQ_AUDIO
                                                    );
                                                } else {
                                                    scheduleWakeListening(350);
                                                }
                                            } else {
                                                stateText.setText("READY");
                                            }
                                        }
                                );
                            }
                        }
                )
                .setNegativeButton(
                        "Sluiten",
                        null
                )
                .show();
    }

    private void showMemoryDialog() {
        LinearLayout box =
                new LinearLayout(this);
        box.setOrientation(
                LinearLayout.VERTICAL
        );
        box.setPadding(
                dp(20),
                dp(4),
                dp(20),
                0
        );

        String conversationId =
                MemoryStore.getConversationId(this);

        TextView info = new TextView(this);
        info.setText(
                conversationId.isEmpty()
                        ? "Gespreksgeheugen: nog niet gestart."
                        : "Gespreksgeheugen: actief.\n"
                        + "Nieuwe chats blijven gekoppeld aan dezelfde conversation."
        );
        box.addView(info);

        TextView label = new TextView(this);
        label.setText(
                "\nLangetermijnprofiel\n"
                        + "Je kunt dit zelf aanpassen. "
                        + "Je kunt ook zeggen: \"onthoud dat ...\""
        );
        box.addView(label);

        EditText memory = new EditText(this);
        memory.setMinLines(6);
        memory.setMaxLines(12);
        memory.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        memory.setText(
                MemoryStore.getProfile(this)
        );
        box.addView(memory);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "MAATJE v0.8 – Geheugen"
                        )
                        .setView(box)
                        .setPositiveButton(
                                "Opslaan",
                                (d, which) -> {
                                    MemoryStore.setProfile(
                                            this,
                                            memory.getText()
                                                    .toString()
                                    );

                                    Toast.makeText(
                                            this,
                                            "Profielgeheugen opgeslagen.",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }
                        )
                        .setNeutralButton(
                                "Nieuw gesprek",
                                (d, which) -> {
                                    MemoryStore
                                            .clearConversation(
                                                    this
                                            );

                                    Toast.makeText(
                                            this,
                                            "Nieuw gesprek gestart. "
                                                    + "Profielgeheugen blijft behouden.",
                                            Toast.LENGTH_LONG
                                    ).show();
                                }
                        )
                        .setNegativeButton(
                                "Sluiten",
                                null
                        )
                        .create();

        dialog.show();
    }

    private void showApiKeyDialog(
            boolean mandatory
    ) {
        LinearLayout box =
                new LinearLayout(this);
        box.setOrientation(
                LinearLayout.VERTICAL
        );
        box.setPadding(
                dp(20),
                dp(4),
                dp(20),
                0
        );

        EditText key =
                new EditText(this);
        key.setHint("sk-…");
        key.setSingleLine(true);
        key.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType
                        .TYPE_TEXT_VARIATION_PASSWORD
        );
        key.setText(
                SecurePrefs.loadApiKey(this)
        );
        box.addView(key);

        TextView note =
                new TextView(this);
        note.setText(
                "De API-key wordt lokaal versleuteld "
                        + "met Android Keystore."
        );
        note.setPadding(
                0,
                dp(12),
                0,
                0
        );
        box.addView(note);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "MAATJE v0.8 – API"
                        )
                        .setView(box)
                        .setPositiveButton(
                                "Opslaan",
                                null
                        )
                        .setNegativeButton(
                                mandatory
                                        ? "Later"
                                        : "Annuleren",
                                null
                        )
                        .setNeutralButton(
                                "Wissen",
                                null
                        )
                        .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(v -> {
                String value =
                        key.getText()
                                .toString()
                                .trim();

                if (value.isEmpty()) {
                    Toast.makeText(
                            this,
                            "Vul een API-key in.",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                try {
                    SecurePrefs.saveApiKey(
                            this,
                            value
                    );

                    Toast.makeText(
                            this,
                            "API-key opgeslagen.",
                            Toast.LENGTH_SHORT
                    ).show();

                    dialog.dismiss();

                } catch (Exception e) {
                    Toast.makeText(
                            this,
                            "Opslaan mislukt: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                }
            });

            dialog.getButton(
                    AlertDialog.BUTTON_NEUTRAL
            ).setOnClickListener(v -> {
                SecurePrefs.clear(this);
                MemoryStore.clearConversation(this);
                key.setText("");

                Toast.makeText(
                        this,
                        "API-key gewist.",
                        Toast.LENGTH_SHORT
                ).show();
            });
        });

        dialog.show();
    }

    private void beginConversationSession() {
        if (!ConversationSettings.enabled(this)) {
            conversationModeActive = false;
            conversationExpiresAt = 0L;
            return;
        }

        conversationModeActive = true;
        conversationExpiresAt = 0L;

        mainHandler.removeCallbacks(
                conversationTimeoutRunnable
        );
    }

    private void pauseConversationTimer() {
        mainHandler.removeCallbacks(
                conversationTimeoutRunnable
        );

        conversationExpiresAt = 0L;
    }

    private boolean isConversationWindowOpen() {
        return conversationModeActive
                && conversationExpiresAt > 0L
                && System.currentTimeMillis()
                < conversationExpiresAt;
    }

    private void scheduleFollowUpListening(
            long delayMs,
            boolean resetWindow
    ) {
        if (!conversationModeActive
                || !ConversationSettings.enabled(this)
                || !appVisible
                || chatBusy) {
            return;
        }

        if (resetWindow
                || conversationExpiresAt <= 0L) {
            conversationExpiresAt =
                    System.currentTimeMillis()
                            + ConversationSettings
                            .timeoutSeconds(this)
                            * 1000L;
        }

        long remaining =
                conversationExpiresAt
                        - System.currentTimeMillis();

        if (remaining <= 0L) {
            endConversationSession();
            scheduleWakeListening(250);
            return;
        }

        mainHandler.removeCallbacks(
                conversationTimeoutRunnable
        );

        mainHandler.postDelayed(
                conversationTimeoutRunnable,
                remaining
        );

        stateText.setText(
                "CONVERSATION • WAITING"
        );

        mainHandler.postDelayed(
                () -> {
                    if (conversationModeActive
                            && isConversationWindowOpen()
                            && !chatBusy
                            && mediaPlayer == null) {
                        startCommandListeningInternal();
                    }
                },
                delayMs
        );
    }

    private final Runnable conversationTimeoutRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (!conversationModeActive) {
                        return;
                    }

                    if (isConversationWindowOpen()) {
                        long remaining =
                                conversationExpiresAt
                                        - System.currentTimeMillis();

                        mainHandler.postDelayed(
                                this,
                                Math.max(
                                        100L,
                                        remaining
                                )
                        );
                        return;
                    }

                    endConversationSession();
                    stateText.setText(
                            "STANDBY • HEY MAATJE"
                    );
                    scheduleWakeListening(250);
                }
            };

    private void endConversationSession() {
        mainHandler.removeCallbacks(
                conversationTimeoutRunnable
        );

        conversationModeActive = false;
        conversationExpiresAt = 0L;

        if (commandListening
                && speechRecognizer != null) {
            ignoreNextRecognitionError = true;
            commandListening = false;

            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {}
        }
    }

    private void resumeAfterAssistant(
            long delayMs
    ) {
        if (conversationModeActive
                && ConversationSettings.enabled(this)) {
            scheduleFollowUpListening(
                    delayMs,
                    true
            );
        } else {
            scheduleWakeListening(
                    delayMs
            );
        }
    }

    private void startStopWordListening() {
        if (mediaPlayer == null
                || offlineWakeWord == null
                || !offlineWakeWord.isReady()) {
            return;
        }

        offlineWakeWord.startStopListening();
    }

    private void handleSpokenStop() {
        if (mediaPlayer == null) {
            return;
        }

        stopPlayer();

        stateText.setText("GESTOPT");

        if (conversationModeActive) {
            scheduleFollowUpListening(
                    250,
                    true
            );
        } else {
            scheduleWakeListening(300);
        }
    }

    private boolean isConversationEndCommand(
            String value
    ) {
        if (value == null) return false;

        String normalized =
                value.toLowerCase(java.util.Locale.ROOT)
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

        return normalized.equals("maatje klaar")
                || normalized.equals("maartje klaar")
                || normalized.equals("stop gesprek")
                || normalized.equals("stop het gesprek")
                || normalized.equals("slaap maar")
                || normalized.equals("ga maar slapen")
                || normalized.equals("maatje stop")
                || normalized.equals("maartje stop");
    }

    private void updateWakeDebug(
            String text
    ) {
        if (wakeDebugText == null) return;

        if (!WakeWordSettings.debugEnabled(this)) {
            wakeDebugText.setVisibility(
                    View.GONE
            );
            return;
        }

        wakeDebugText.setVisibility(
                View.VISIBLE
        );

        wakeDebugText.setText(
                "HEARD • " + text
        );
    }

    private void updateWakeDebugVisibility() {
        if (wakeDebugText == null) return;

        boolean visible =
                WakeWordSettings.debugEnabled(this);

        wakeDebugText.setVisibility(
                visible
                        ? View.VISIBLE
                        : View.GONE
        );

        if (!visible) {
            wakeDebugText.setText("");
        }
    }

    private void setBusy(
            boolean busy,
            String label
    ) {
        chatBusy = busy;
        stateText.setText(label);
        micButton.setEnabled(!busy);
        sendButton.setEnabled(!busy);
        input.setEnabled(!busy);
    }

    private void append(String text) {
        transcript.append(text);

        transcript.post(() -> {
            if (transcript.getLayout() == null
                    || transcript.getLineCount()
                    == 0) {
                return;
            }

            int contentHeight =
                    transcript.getLayout()
                            .getLineBottom(
                                    transcript
                                            .getLineCount()
                                            - 1
                            );

            int visibleHeight =
                    transcript.getHeight()
                            - transcript
                            .getPaddingTop()
                            - transcript
                            .getPaddingBottom();

            transcript.scrollTo(
                    0,
                    Math.max(
                            0,
                            contentHeight
                                    - visibleHeight
                    )
            );
        });
    }

    private void speak(String text) {
        if (!VoiceSettings.autoSpeak(this)) {
            resumeAfterAssistant(350);
            return;
        }

        String key =
                SecurePrefs.loadApiKey(this);

        if (key.isEmpty()) {
            resumeAfterAssistant(350);
            return;
        }

        String cleaned =
                text.replace("```", "")
                        .replace("**", "")
                        .trim();

        String voice =
                VoiceSettings.voice(this);
        float speed =
                VoiceSettings.speed(this);
        String style =
                VoiceSettings.style(this)
                        + "\n"
                        + PersonalitySettings.voiceStyle(this);

        voiceExecutor.submit(() -> {
            try {
                File audio =
                        OpenAiSpeech.synthesize(
                                MainActivity.this,
                                key,
                                cleaned,
                                voice,
                                style,
                                speed
                        );

                runOnUiThread(
                        () -> playAudio(audio)
                );

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(
                            MainActivity.this,
                            "Stemfout: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                    scheduleWakeListening(700);
                });
            }
        });
    }

    private void testCloudVoice(
            String voice,
            float speed,
            String instructions
    ) {
        String key =
                SecurePrefs.loadApiKey(this);

        if (key.isEmpty()) {
            Toast.makeText(
                    this,
                    "Vul eerst een API-key in.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        stopPlayer();

        voiceExecutor.submit(() -> {
            try {
                File audio =
                        OpenAiSpeech.synthesize(
                                MainActivity.this,
                                key,
                                "Goedemiddag. MAATJE is online. "
                                        + "Alle systemen functioneren normaal.",
                                voice,
                                instructions,
                                speed
                        );

                runOnUiThread(
                        () -> playAudio(audio)
                );

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "Stemtest mislukt: "
                                        + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        });
    }

    private void playAudio(File file) {
        stopPlayer();

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(
                    file.getAbsolutePath()
            );

            mediaPlayer.setOnPreparedListener(
                    mp -> {
                        mp.start();

                        mainHandler.postDelayed(
                                this::startStopWordListening,
                                250
                        );
                    }
            );

            mediaPlayer.setOnCompletionListener(
                    mp -> {
                        mp.release();

                        if (mediaPlayer == mp) {
                            mediaPlayer = null;
                        }

                        if (offlineWakeWord != null) {
                            offlineWakeWord.stop();
                        }

                        file.delete();
                        resumeAfterAssistant(450);
                    }
            );

            mediaPlayer.setOnErrorListener(
                    (mp, what, extra) -> {
                        mp.release();

                        if (mediaPlayer == mp) {
                            mediaPlayer = null;
                        }

                        if (offlineWakeWord != null) {
                            offlineWakeWord.stop();
                        }

                        file.delete();
                        resumeAfterAssistant(700);
                        return true;
                    }
            );

            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            file.delete();

            Toast.makeText(
                    this,
                    "Audio afspelen mislukt: "
                            + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void stopPlayer() {
        if (offlineWakeWord != null) {
            offlineWakeWord.stop();
        }

        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ignored) {}

            try {
                mediaPlayer.release();
            } catch (Exception ignored) {}

            mediaPlayer = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        appVisible = true;
        wakeWordEnabled =
                WakeWordSettings.enabled(this);

        scheduleWakeListening(600);
    }

    @Override
    protected void onPause() {
        appVisible = false;
        endConversationSession();
        stopRecognitionSession();

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mainHandler.removeCallbacksAndMessages(null);

        chatExecutor.shutdownNow();
        voiceExecutor.shutdownNow();

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }

        if (offlineWakeWord != null) {
            offlineWakeWord.destroy();
        }

        stopPlayer();
    }

    private Button button(
            String text,
            int bg,
            int fg
    ) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(fg);
        b.setTextSize(18);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(
                roundRect(bg, 18, bg)
        );
        return b;
    }

    private GradientDrawable roundRect(
            int color,
            int radiusDp,
            int strokeColor
    ) {
        GradientDrawable d =
                new GradientDrawable();

        d.setColor(color);
        d.setCornerRadius(
                dp(radiusDp)
        );
        d.setStroke(
                dp(1),
                strokeColor
        );

        return d;
    }

    private int dp(int value) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
