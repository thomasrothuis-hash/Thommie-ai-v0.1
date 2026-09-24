package nl.thommie.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PersonalitySettings {

    static final class CommandResult {
        final boolean handled;
        final String message;

        CommandResult(boolean handled, String message) {
            this.handled = handled;
            this.message = message;
        }
    }

    private static final String PREFS = "maatje_ai_personality";
    private static final String KEY_HUMOR = "humor";
    private static final String KEY_SARCASM = "sarcasm";
    private static final String KEY_DRY = "dry";
    private static final String KEY_ENTHUSIASM = "enthusiasm";
    private static final String KEY_PROFANITY = "profanity";
    private static final String KEY_MOOD = "mood";
    private static final String KEY_LEARN = "learn_humor";
    private static final String KEY_HUMOR_MEMORY = "humor_memory";

    private static final int DEF_HUMOR = 50;
    private static final int DEF_SARCASM = 40;
    private static final int DEF_DRY = 70;
    private static final int DEF_ENTHUSIASM = 25;
    private static final int DEF_PROFANITY = 45;

    private PersonalitySettings() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int humor(Context c) {
        return prefs(c).getInt(KEY_HUMOR, DEF_HUMOR);
    }

    static int sarcasm(Context c) {
        return prefs(c).getInt(KEY_SARCASM, DEF_SARCASM);
    }

    static int dry(Context c) {
        return prefs(c).getInt(KEY_DRY, DEF_DRY);
    }

    static int enthusiasm(Context c) {
        return prefs(c).getInt(KEY_ENTHUSIASM, DEF_ENTHUSIASM);
    }

    static int profanity(Context c) {
        return prefs(c).getInt(KEY_PROFANITY, DEF_PROFANITY);
    }

    static String mood(Context c) {
        return prefs(c).getString(KEY_MOOD, "NORMAAL");
    }

    static boolean learnHumor(Context c) {
        return prefs(c).getBoolean(KEY_LEARN, true);
    }

    static String humorMemory(Context c) {
        return prefs(c).getString(KEY_HUMOR_MEMORY, "");
    }

    static String prompt(Context c) {
        String mood = mood(c);
        int humor = humor(c);
        int sarcasm = sarcasm(c);
        int dry = dry(c);
        int enthusiasm = enthusiasm(c);
        int profanity = profanity(c);

        StringBuilder sb = new StringBuilder();

        sb.append("Actuele persoonlijkheidsinstellingen van MAATJE:\n");
        sb.append("BELANGRIJK: deze waarden zijn de actuele bron van waarheid. ");
        sb.append("Ze overschrijven elke oudere of afwijkende waarde die eerder in het gesprek is genoemd. ");
        sb.append("Als de gebruiker naar een huidige instelling vraagt, gebruik uitsluitend deze actuele waarden.\n");
        sb.append("- Mood: ").append(mood).append("\n");
        sb.append("- Humor: ").append(humor).append("/100\n");
        sb.append("- Sarcasme: ").append(sarcasm).append("/100\n");
        sb.append("- Droge/deadpan humor: ").append(dry).append("/100\n");
        sb.append("- Enthousiasme: ").append(enthusiasm).append("/100\n");
        sb.append("- Natuurlijk vloeken/schelden: ").append(profanity).append("/100\n\n");

        sb.append("Deze percentages moeten MERKBAAR verschil maken in gedrag. ");
        sb.append("Volg de onderstaande intensiteitsregels als actuele runtime-instellingen. ");
        sb.append("Forceer geen grap of scheldwoord als het onderwerp dat duidelijk niet toelaat. ");
        sb.append("Bij serieuze, gevoelige of feitelijke onderwerpen mag de toon vanzelf rustiger worden.\n");

        appendHumorRule(sb, humor);
        appendSarcasmRule(sb, sarcasm);
        appendDryRule(sb, dry);
        appendEnthusiasmRule(sb, enthusiasm);
        appendProfanityRule(sb, profanity);

        if ("CHILL".equals(mood)) {
            sb.append("De huidige mood is CHILL: praat losser, warmer en iets speelser. ");
        } else if ("SERIEUS".equals(mood)) {
            sb.append("De huidige mood is SERIEUS: onderdruk grappen en sarcasme sterk en wees vooral helder en zakelijk. ");
        } else {
            sb.append("De huidige mood is NORMAAL: gebruik de ingestelde persoonlijkheid zonder extra versterking. ");
        }

        if (learnHumor(c)) {
            sb.append("Let op expliciete feedback van de gebruiker over wat hij wel of niet grappig vindt en pas je binnen het gesprek daarop aan. ");
        }

        String memory = humorMemory(c);
        if (!memory.isEmpty()) {
            sb.append("\n\nOpgeslagen humorvoorbeelden/feedback:\n");
            sb.append(memory);
        }

        return sb.toString();
    }

    static String voiceStyle(Context c) {
        String mood = mood(c);
        int humor = humor(c);
        int sarcasm = sarcasm(c);
        int dry = dry(c);
        int enthusiasm = enthusiasm(c);

        StringBuilder sb = new StringBuilder();
        sb.append("Reflect the current MAATJE personality in the delivery. ");

        if ("SERIEUS".equals(mood)) {
            sb.append("Sound calm, focused and precise, with almost no playful amusement. ");
        } else if ("CHILL".equals(mood)) {
            sb.append("Sound relaxed, warm, casually confident and subtly amused when appropriate. ");
        } else {
            sb.append("Sound composed, natural and quietly confident. ");
        }

        if (humor >= 85) {
            sb.append("Sound noticeably playful and quick-witted when appropriate. ");
        } else if (humor >= 60) {
            sb.append("Allow a natural playful undertone when a joke lands. ");
        } else if (humor <= 20) {
            sb.append("Keep delivery mostly straight and non-playful. ");
        }

        if (sarcasm >= 75) {
            sb.append("Allow a subtle sly or teasing edge when context supports sarcasm. ");
        }

        if (dry >= 70) {
            sb.append("Use strong dry deadpan delivery; never over-sell a joke or laugh at your own line. ");
        }

        if (enthusiasm >= 80) {
            sb.append("Sound energetic and animated. ");
        } else if (enthusiasm <= 20) {
            sb.append("Keep delivery restrained and calm. ");
        }

        return sb.toString();
    }

    private static void appendHumorRule(
            StringBuilder sb,
            int value
    ) {
        sb.append("- HUMOR-regel: ");
        if (value <= 15) {
            sb.append("vrijwel geen grappen; antwoord hoofdzakelijk rechttoe-rechtaan. ");
        } else if (value <= 35) {
            sb.append("weinig humor; alleen een incidentele natuurlijke grap. ");
        } else if (value <= 60) {
            sb.append("gematigde natuurlijke humor; af en toe een gevatte opmerking. ");
        } else if (value <= 80) {
            sb.append("duidelijk speelser; regelmatig gevat of grappig waar passend. ");
        } else {
            sb.append("zeer humoristisch en gevat; zoek vaak naar een natuurlijke grappige invalshoek, zonder elk antwoord in een grap te veranderen. ");
        }
        sb.append("\n");
    }

    private static void appendSarcasmRule(
            StringBuilder sb,
            int value
    ) {
        sb.append("- SARCASME-regel: ");
        if (value <= 15) {
            sb.append("vermijd sarcasme vrijwel volledig. ");
        } else if (value <= 40) {
            sb.append("licht sarcasme, alleen als het vanzelf past. ");
        } else if (value <= 70) {
            sb.append("regelmatig speels sarcasme en plagerigheid. ");
        } else {
            sb.append("duidelijk scherp en sarcastisch wanneer de context dat toelaat, zonder vijandig te worden. ");
        }
        sb.append("\n");
    }

    private static void appendDryRule(
            StringBuilder sb,
            int value
    ) {
        sb.append("- DROOGHEID-regel: ");
        if (value <= 20) {
            sb.append("weinig deadpan; maak humor duidelijker en warmer. ");
        } else if (value <= 60) {
            sb.append("gematigd droog; subtiele deadpan is prima. ");
        } else {
            sb.append("sterke droge/deadpan stijl; leg grappen niet uit en lach niet om je eigen grap. ");
        }
        sb.append("\n");
    }

    private static void appendEnthusiasmRule(
            StringBuilder sb,
            int value
    ) {
        sb.append("- ENTHOUSIASME-regel: ");
        if (value <= 20) {
            sb.append("rustig, beheerst en weinig uitbundig. ");
        } else if (value <= 60) {
            sb.append("normaal energieniveau. ");
        } else if (value <= 80) {
            sb.append("duidelijk enthousiast en levendig. ");
        } else {
            sb.append("zeer enthousiast en energiek, zonder irritant overdreven te worden. ");
        }
        sb.append("\n");
    }

    private static void appendProfanityRule(
            StringBuilder sb,
            int value
    ) {
        sb.append("- SCHELDEN-regel: ");
        if (value == 0) {
            sb.append("gebruik geen krachttermen of gevloek. ");
        } else if (value <= 25) {
            sb.append("zeer sporadisch een milde krachtterm als het natuurlijk past. ");
        } else if (value <= 55) {
            sb.append("af en toe gewone Nederlandse krachttermen bij frustratie of humor. ");
        } else if (value <= 80) {
            sb.append("vrij los taalgebruik en regelmatig passend gevloek. ");
        } else {
            sb.append("zeer los en ongefilterd spreektaalgebruik met geregeld passende krachttermen, maar niet gratuit en geen haatdragende slurs. ");
        }
        sb.append("\n");
    }

    static CommandResult handleCommand(Context c, String input) {
        if (input == null) return new CommandResult(false, "");

        String lower = input.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) return new CommandResult(false, "");

        if (asksForAllSettings(lower)) {
            return new CommandResult(
                    true,
                    currentSettingsSummary(c)
            );
        }

        if (asksForSettingValue(lower)) {
            if (containsAny(
                    lower,
                    "humor",
                    "grappig",
                    "grappigheid"
            )) {
                return currentValue(
                        "Humor",
                        humor(c)
                );
            }

            if (containsAny(
                    lower,
                    "sarcasme",
                    "sarcastisch"
            )) {
                return currentValue(
                        "Sarcasme",
                        sarcasm(c)
                );
            }

            if (containsAny(
                    lower,
                    "droogheid",
                    "droge humor",
                    "deadpan"
            )) {
                return currentValue(
                        "Droogheid",
                        dry(c)
                );
            }

            if (containsAny(
                    lower,
                    "enthousiasme",
                    "enthousiast"
            )) {
                return currentValue(
                        "Enthousiasme",
                        enthusiasm(c)
                );
            }

            if (containsAny(
                    lower,
                    "schelden",
                    "vloeken",
                    "gevloek"
            )) {
                return currentValue(
                        "Schelden",
                        profanity(c)
                );
            }

            if (containsAny(
                    lower,
                    "mood",
                    "modus",
                    "stemming"
            )) {
                return new CommandResult(
                        true,
                        "Mood staat op "
                                + mood(c)
                                + "."
                );
            }
        }

        Integer numeric;

        numeric = extractPercent(
                lower,
                "(?:(?:zet|maak)\\s+)?(?:(?:je|jouw|de)\\s+)?humor(?:\\s+level)?\\s*(?:op|naar|=)?\\s*(\\d{1,3})\\s*%?"
        );
        if (numeric != null) {
            setInt(c, KEY_HUMOR, numeric);
            return changed("Humor", numeric);
        }

        numeric = extractPercent(
                lower,
                "(?:(?:zet|maak)\\s+)?(?:(?:je|jouw|de)\\s+)?sarcasme\\s*(?:op|naar|=)?\\s*(\\d{1,3})\\s*%?"
        );
        if (numeric != null) {
            setInt(c, KEY_SARCASM, numeric);
            return changed("Sarcasme", numeric);
        }

        numeric = extractPercent(
                lower,
                "(?:(?:zet|maak)\\s+)?(?:(?:je|jouw|de)\\s+)?(?:droogheid|droge humor|deadpan)\\s*(?:op|naar|=)?\\s*(\\d{1,3})\\s*%?"
        );
        if (numeric != null) {
            setInt(c, KEY_DRY, numeric);
            return changed("Droogheid", numeric);
        }

        numeric = extractPercent(
                lower,
                "(?:(?:zet|maak)\\s+)?(?:(?:je|jouw|de)\\s+)?enthousiasme\\s*(?:op|naar|=)?\\s*(\\d{1,3})\\s*%?"
        );
        if (numeric != null) {
            setInt(c, KEY_ENTHUSIASM, numeric);
            return changed("Enthousiasme", numeric);
        }

        numeric = extractPercent(
                lower,
                "(?:(?:zet|maak)\\s+)?(?:(?:je|jouw|de)\\s+)?(?:schelden|vloeken|gevloek)\\s*(?:op|naar|=)?\\s*(\\d{1,3})\\s*%?"
        );
        if (numeric != null) {
            setInt(c, KEY_PROFANITY, numeric);
            return changed("Schelden", numeric);
        }

        if (containsAny(lower,
                "doe wat grappiger",
                "doe eens wat grappiger",
                "wees grappiger",
                "humor omhoog",
                "meer humor")) {
            int value = adjust(c, KEY_HUMOR, humor(c), 15);
            return changed("Humor", value);
        }

        if (containsAny(lower,
                "minder grappig",
                "humor omlaag",
                "minder humor")) {
            int value = adjust(c, KEY_HUMOR, humor(c), -15);
            return changed("Humor", value);
        }

        if (containsAny(lower,
                "sarcastischer",
                "sarcasme omhoog",
                "meer sarcasme")) {
            int value = adjust(c, KEY_SARCASM, sarcasm(c), 15);
            return changed("Sarcasme", value);
        }

        if (containsAny(lower,
                "minder sarcastisch",
                "sarcasme omlaag",
                "minder sarcasme")) {
            int value = adjust(c, KEY_SARCASM, sarcasm(c), -15);
            return changed("Sarcasme", value);
        }

        if (containsAny(lower,
                "doe wat droger",
                "droger zijn",
                "droge humor omhoog",
                "meer droge humor")) {
            int value = adjust(c, KEY_DRY, dry(c), 15);
            return changed("Droogheid", value);
        }

        if (containsAny(lower,
                "minder droog",
                "droge humor omlaag")) {
            int value = adjust(c, KEY_DRY, dry(c), -15);
            return changed("Droogheid", value);
        }

        if (containsAny(lower,
                "scheld wat meer",
                "meer schelden",
                "vloek wat meer",
                "meer vloeken",
                "je mag meer schelden")) {
            int value = adjust(c, KEY_PROFANITY, profanity(c), 15);
            return changed("Schelden", value);
        }

        if (containsAny(lower,
                "scheld minder",
                "minder schelden",
                "vloek minder",
                "minder vloeken",
                "niet zo schelden")) {
            int value = adjust(c, KEY_PROFANITY, profanity(c), -20);
            return changed("Schelden", value);
        }

        if (containsAny(lower,
                "niet schelden",
                "niet meer schelden",
                "geen gevloek",
                "niet vloeken")) {
            setInt(c, KEY_PROFANITY, 0);
            return changed("Schelden", 0);
        }

        if (containsAny(lower,
                "doe even serieus",
                "wees even serieus",
                "even serieus nu",
                "serieuze modus",
                "serious mode")) {
            setMood(c, "SERIEUS");
            return new CommandResult(true, "Begrepen. Mood staat op SERIEUS.");
        }

        if (containsAny(lower,
                "doe weer normaal",
                "normale modus",
                "mood normaal")) {
            setMood(c, "NORMAAL");
            return new CommandResult(true, "Mood staat weer op NORMAAL.");
        }

        if (containsAny(lower,
                "doe wat chiller",
                "chill modus",
                "mood chill",
                "doe wat losser",
                "wees wat losser")) {
            setMood(c, "CHILL");
            return new CommandResult(true, "Prima. Mood staat op CHILL.");
        }

        if (containsAny(lower,
                "humor uit",
                "geen humor")) {
            setInt(c, KEY_HUMOR, 0);
            return changed("Humor", 0);
        }

        if (containsAny(lower,
                "humor maximaal",
                "humor voluit",
                "maximale humor")) {
            setInt(c, KEY_HUMOR, 100);
            return changed("Humor", 100);
        }

        return new CommandResult(false, "");
    }

    private static boolean asksForAllSettings(
            String input
    ) {
        return containsAny(
                input,
                "wat zijn je instellingen",
                "wat zijn jouw instellingen",
                "wat zijn de instellingen",
                "persoonlijkheidsinstellingen",
                "hoe sta je ingesteld",
                "hoe ben je ingesteld"
        );
    }

    private static boolean asksForSettingValue(
            String input
    ) {
        return containsAny(
                input,
                "op hoeveel",
                "hoeveel procent",
                "welk percentage",
                "welke stand",
                "wat is je",
                "wat is jouw",
                "waar staat je",
                "waar staat jouw",
                "hoe hoog staat",
                "hoe staat je",
                "hoe staat jouw"
        );
    }

    private static CommandResult currentValue(
            String name,
            int value
    ) {
        return new CommandResult(
                true,
                name
                        + " staat momenteel op "
                        + value
                        + "%."
        );
    }

    private static String currentSettingsSummary(
            Context c
    ) {
        return "Mijn actuele instellingen zijn: "
                + "humor " + humor(c) + "%, "
                + "sarcasme " + sarcasm(c) + "%, "
                + "droogheid " + dry(c) + "%, "
                + "enthousiasme " + enthusiasm(c) + "%, "
                + "schelden " + profanity(c) + "% "
                + "en mood " + mood(c) + ".";
    }

    static boolean captureHumorFeedback(
            Context c,
            String userInput,
            String previousAssistantReply
    ) {
        if (!learnHumor(c)) return false;
        if (userInput == null || previousAssistantReply == null) return false;
        if (previousAssistantReply.trim().isEmpty()) return false;

        String lower = userInput.toLowerCase(Locale.ROOT);

        boolean positive =
                containsAny(lower,
                        "goeie grap",
                        "goede grap",
                        "dit soort humor is goed",
                        "dit soort humor vind ik leuk",
                        "dit soort humor hou ik van",
                        "zo is de humor goed",
                        "haha dit is goed",
                        "haha precies",
                        "dit soort grapjes mag je onthouden");

        boolean negative =
                containsAny(lower,
                        "kutgrap",
                        "slechte grap",
                        "niet grappig",
                        "dit soort humor niet",
                        "deze humor is niks",
                        "wat flauw",
                        "cringe");

        if (!positive && !negative) return false;

        String example = previousAssistantReply
                .replace("\n", " ")
                .replace("\r", " ")
                .trim();

        if (example.length() > 320) {
            example = example.substring(0, 320) + "…";
        }

        String note = (positive ? "• POSITIEF voorbeeld: " : "• NEGATIEF voorbeeld: ")
                + "\"" + example + "\"";

        appendHumorMemory(c, note);
        return true;
    }

    private static void appendHumorMemory(Context c, String note) {
        String current = humorMemory(c);
        String next = current.isEmpty() ? note : current + "\n" + note;

        while (next.length() > 4000) {
            int cut = next.indexOf('\n');
            if (cut < 0) {
                next = next.substring(Math.max(0, next.length() - 4000));
                break;
            }
            next = next.substring(cut + 1);
        }

        prefs(c).edit().putString(KEY_HUMOR_MEMORY, next).apply();
    }

    private static Integer extractPercent(String input, String regex) {
        Matcher m = Pattern.compile(regex).matcher(input);
        if (!m.find()) return null;

        try {
            return clamp(Integer.parseInt(m.group(1)));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean containsAny(String input, String... values) {
        for (String value : values) {
            if (input.contains(value)) return true;
        }
        return false;
    }

    private static int adjust(Context c, String key, int current, int delta) {
        int value = clamp(current + delta);
        setInt(c, key, value);
        return value;
    }

    private static void setInt(Context c, String key, int value) {
        prefs(c).edit().putInt(key, clamp(value)).apply();
    }

    private static void setMood(Context c, String mood) {
        prefs(c).edit().putString(KEY_MOOD, mood).apply();
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static CommandResult changed(String name, int value) {
        return new CommandResult(
                true,
                name + " staat nu op " + value + "%."
        );
    }

    static void show(Activity activity) {
        SharedPreferences p = prefs(activity);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(45, 20, 45, 30);
        scroll.addView(box);

        SeekBar humor = addSlider(
                box,
                "Humor",
                humor(activity)
        );

        SeekBar sarcasm = addSlider(
                box,
                "Sarcasme",
                sarcasm(activity)
        );

        SeekBar dry = addSlider(
                box,
                "Droogheid / deadpan",
                dry(activity)
        );

        SeekBar enthusiasm = addSlider(
                box,
                "Enthousiasme",
                enthusiasm(activity)
        );

        SeekBar profanity = addSlider(
                box,
                "Natuurlijk schelden / vloeken",
                profanity(activity)
        );

        TextView moodLabel = new TextView(activity);
        moodLabel.setText("\nMood");
        moodLabel.setTextSize(16);
        box.addView(moodLabel);

        Spinner mood = new Spinner(activity);
        String[] moods = {"CHILL", "NORMAAL", "SERIEUS"};
        ArrayAdapter<String> moodAdapter = new ArrayAdapter<>(
                activity,
                android.R.layout.simple_spinner_item,
                moods
        );
        moodAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );
        mood.setAdapter(moodAdapter);

        String currentMood = mood(activity);
        mood.setSelection(
                "CHILL".equals(currentMood) ? 0
                        : "SERIEUS".equals(currentMood) ? 2
                        : 1
        );
        box.addView(mood);

        Switch learn = new Switch(activity);
        learn.setText("\nLeer van expliciete humorfeedback");
        learn.setChecked(learnHumor(activity));
        box.addView(learn);

        Button viewMemory = new Button(activity);
        viewMemory.setText("HUMORPROFIEL BEKIJKEN");
        box.addView(viewMemory);

        viewMemory.setOnClickListener(v -> {
            String memory = humorMemory(activity);
            if (memory.isEmpty()) {
                memory = "Nog geen expliciete humorvoorbeelden opgeslagen.";
            }

            new AlertDialog.Builder(activity)
                    .setTitle("MAATJE – Humorprofiel")
                    .setMessage(memory)
                    .setPositiveButton("Sluiten", null)
                    .setNeutralButton("Wissen", (d, which) -> {
                        p.edit().remove(KEY_HUMOR_MEMORY).apply();
                        Toast.makeText(
                                activity,
                                "Humorprofiel gewist.",
                                Toast.LENGTH_SHORT
                        ).show();
                    })
                    .show();
        });

        Button reset = new Button(activity);
        reset.setText("RESET PERSOONLIJKHEID");
        box.addView(reset);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("MAATJE v0.9.1 – Persoonlijkheid")
                .setView(scroll)
                .setPositiveButton("Opslaan", null)
                .setNegativeButton("Annuleren", null)
                .create();

        reset.setOnClickListener(v -> {
            humor.setProgress(DEF_HUMOR);
            sarcasm.setProgress(DEF_SARCASM);
            dry.setProgress(DEF_DRY);
            enthusiasm.setProgress(DEF_ENTHUSIASM);
            profanity.setProgress(DEF_PROFANITY);
            mood.setSelection(1);
            learn.setChecked(true);
        });

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        p.edit()
                                .putInt(KEY_HUMOR, humor.getProgress())
                                .putInt(KEY_SARCASM, sarcasm.getProgress())
                                .putInt(KEY_DRY, dry.getProgress())
                                .putInt(KEY_ENTHUSIASM, enthusiasm.getProgress())
                                .putInt(KEY_PROFANITY, profanity.getProgress())
                                .putString(
                                        KEY_MOOD,
                                        moods[mood.getSelectedItemPosition()]
                                )
                                .putBoolean(KEY_LEARN, learn.isChecked())
                                .apply();

                        Toast.makeText(
                                activity,
                                "Persoonlijkheid opgeslagen.",
                                Toast.LENGTH_SHORT
                        ).show();

                        dialog.dismiss();
                    });
        });

        dialog.show();
    }

    private static SeekBar addSlider(
            LinearLayout box,
            String title,
            int value
    ) {
        TextView label = new TextView(box.getContext());
        label.setText("\n" + title + ": " + value + "%");
        label.setTextSize(16);
        box.addView(label);

        SeekBar seek = new SeekBar(box.getContext());
        seek.setMax(100);
        seek.setProgress(value);
        box.addView(seek);

        seek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {
                        label.setText(
                                "\n" + title + ": " + progress + "%"
                        );
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                }
        );

        return seek;
    }
}
