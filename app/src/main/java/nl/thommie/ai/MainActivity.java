package nl.thommie.ai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQ_AUDIO = 1001;
    private static final int BG = Color.rgb(5, 7, 9);
    private static final int PANEL = Color.rgb(13, 18, 20);
    private static final int MINT = Color.rgb(117, 243, 208);
    private static final int TEXT = Color.rgb(235, 245, 242);
    private static final int MUTED = Color.rgb(136, 154, 150);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView stateText;
    private TextView transcript;
    private EditText input;
    private Button micButton;
    private Button sendButton;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String previousResponseId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        tts = new TextToSpeech(this, this);
        buildUi();
        initSpeechRecognizer();

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
        title.setText("THOMMIE AI");
        title.setTextColor(TEXT);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1f));

        Button settings = button("⚙", PANEL, TEXT);
        settings.setOnClickListener(v -> showApiKeyDialog(false));
        header.addView(settings, new LinearLayout.LayoutParams(dp(54), dp(46)));
        root.addView(header);

        TextView version = new TextView(this);
        version.setText("v0.1  •  PERSONAL AI TERMINAL");
        version.setTextColor(MUTED);
        version.setTextSize(11);
        version.setLetterSpacing(.16f);
        root.addView(version);

        TextView orb = new TextView(this);
        orb.setText("●");
        orb.setTextColor(MINT);
        orb.setTextSize(90);
        orb.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams orbLp = new LinearLayout.LayoutParams(-1, dp(125));
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

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        GradientDrawable panelBg = roundRect(PANEL, 22, Color.rgb(31, 43, 43));
        scroll.setBackground(panelBg);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollLp.topMargin = dp(20);
        scrollLp.bottomMargin = dp(16);
        root.addView(scroll, scrollLp);

        transcript = new TextView(this);
        transcript.setText("Welkom.\n\nTik op de microfoon of typ een bericht. In v0.1 gebruikt de app Android voor spraakherkenning en voorlezen; je tekstvraag gaat via de OpenAI Responses API.");
        transcript.setTextColor(TEXT);
        transcript.setTextSize(16);
        transcript.setLineSpacing(0, 1.25f);
        transcript.setPadding(dp(18), dp(18), dp(18), dp(18));
        scroll.addView(transcript);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);

        micButton = button("🎙", PANEL, TEXT);
        micButton.setOnClickListener(v -> startListening());
        composer.addView(micButton, new LinearLayout.LayoutParams(dp(58), dp(58)));

        input = new EditText(this);
        input.setHint("Vraag iets…");
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setTextSize(16);
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setPadding(dp(16), dp(8), dp(16), dp(8));
        input.setBackground(roundRect(PANEL, 18, Color.rgb(31, 43, 43)));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, dp(58), 1f);
        inputLp.leftMargin = dp(10);
        inputLp.rightMargin = dp(10);
        composer.addView(input, inputLp);

        sendButton = button("➜", MINT, BG);
        sendButton.setTextSize(21);
        sendButton.setOnClickListener(v -> sendCurrentInput());
        composer.addView(sendButton, new LinearLayout.LayoutParams(dp(58), dp(58)));
        root.addView(composer);

        setContentView(root);
    }

    private void sendCurrentInput() {
        String q = input.getText().toString().trim();
        if (q.isEmpty()) return;
        input.setText("");
        ask(q);
    }

    private void ask(String q) {
        String key = SecurePrefs.loadApiKey(this);
        if (key.isEmpty()) {
            showApiKeyDialog(true);
            return;
        }
        append("\n\nJIJ\n" + q);
        setBusy(true, "THINKING");
        final String prev = previousResponseId;
        executor.submit(() -> {
            try {
                OpenAiClient.Reply reply = OpenAiClient.ask(key, "gpt-5.6-luna", prev, q);
                previousResponseId = reply.responseId;
                runOnUiThread(() -> {
                    append("\n\nTHOMMIE AI\n" + reply.text);
                    setBusy(false, "READY");
                    speak(reply.text);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    append("\n\nFOUT\n" + e.getMessage());
                    setBusy(false, "ERROR");
                });
            }
        });
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle params) { setBusy(false, "LISTENING"); }
            public void onBeginningOfSpeech() { setBusy(false, "LISTENING"); }
            public void onRmsChanged(float rmsdB) {}
            public void onBufferReceived(byte[] buffer) {}
            public void onEndOfSpeech() { stateText.setText("PROCESSING SPEECH"); }
            public void onError(int error) {
                setBusy(false, "READY");
                Toast.makeText(MainActivity.this, "Spraakherkenning fout: " + error, Toast.LENGTH_SHORT).show();
            }
            public void onResults(Bundle results) {
                setBusy(false, "READY");
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    input.setText(list.get(0));
                    input.setSelection(input.length());
                    ask(list.get(0));
                    input.setText("");
                }
            }
            public void onPartialResults(Bundle partialResults) {}
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        if (speechRecognizer == null) {
            Toast.makeText(this, "Geen Android spraakherkenner beschikbaar.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (tts != null) tts.stop();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "nl-NL");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        speechRecognizer.startListening(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        }
    }

    private void showApiKeyDialog(boolean mandatory) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), 0);

        EditText key = new EditText(this);
        key.setHint("sk-…");
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(SecurePrefs.loadApiKey(this));
        box.addView(key);

        TextView note = new TextView(this);
        note.setText("De sleutel wordt versleuteld met Android Keystore en blijft op dit toestel. Voor een latere versie bouwen we liever een kleine backend met tijdelijke tokens.");
        note.setPadding(0, dp(12), 0, 0);
        box.addView(note);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("THOMMIE AI v0.1 – API")
                .setView(box)
                .setPositiveButton("Opslaan", null)
                .setNegativeButton(mandatory ? "Later" : "Annuleren", null)
                .setNeutralButton("Wissen", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = key.getText().toString().trim();
                if (value.isEmpty()) {
                    Toast.makeText(this, "Vul een API-key in.", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    SecurePrefs.saveApiKey(this, value);
                    Toast.makeText(this, "API-key opgeslagen.", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } catch (Exception e) {
                    Toast.makeText(this, "Opslaan mislukt: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                SecurePrefs.clear(this);
                key.setText("");
                previousResponseId = "";
                Toast.makeText(this, "API-key gewist.", Toast.LENGTH_SHORT).show();
            });
        });
        dialog.show();
    }

    private void setBusy(boolean busy, String label) {
        stateText.setText(label);
        micButton.setEnabled(!busy);
        sendButton.setEnabled(!busy);
        input.setEnabled(!busy);
    }

    private void append(String text) {
        transcript.append(text);
    }

    private void speak(String text) {
        if (!ttsReady || tts == null) return;
        String cleaned = text.replace("```", "").replace("**", "");
        tts.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, "thommie_ai_reply");
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(new Locale("nl", "NL"));
            tts.setSpeechRate(1.03f);
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    private Button button(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(fg);
        b.setTextSize(18);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0,0,0,0);
        b.setBackground(roundRect(bg, 18, bg));
        return b;
    }

    private GradientDrawable roundRect(int color, int radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), strokeColor);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
