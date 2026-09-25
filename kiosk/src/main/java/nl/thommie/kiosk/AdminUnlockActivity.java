package nl.thommie.kiosk;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class AdminUnlockActivity extends Activity {

    private static final int BG =
            Color.rgb(4, 8, 5);
    private static final int PANEL =
            Color.rgb(9, 17, 11);
    private static final int MINT =
            Color.rgb(54, 220, 104);
    private static final int TEXT =
            Color.rgb(216, 240, 222);
    private static final int MUTED =
            Color.rgb(105, 139, 113);

    private EditText pinOne;
    private EditText pinTwo;
    private TextView title;
    private TextView subtitle;
    private boolean creating;
    private boolean openUpdateAfterUnlock;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        creating =
                !PinStore.hasPin(this);

        openUpdateAfterUnlock =
                getIntent().getBooleanExtra(
                        "open_update",
                        false
                );

        buildUi();
    }

    private void buildUi() {
        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );
        root.setGravity(
                Gravity.CENTER
        );
        root.setBackgroundColor(BG);
        root.setPadding(
                dp(28),
                dp(28),
                dp(28),
                dp(28)
        );

        title =
                new TextView(this);

        title.setText(
                creating
                        ? "MAATJE KIOSK • PIN INSTELLEN"
                        : "MAATJE KIOSK • BEHEER"
        );
        title.setGravity(
                Gravity.CENTER
        );
        title.setTextColor(MINT);
        title.setTextSize(18);
        title.setTypeface(
                Typeface.create(
                        Typeface.MONOSPACE,
                        Typeface.BOLD
                )
        );

        root.addView(title);

        subtitle =
                new TextView(this);

        subtitle.setText(
                creating
                        ? "Kies een PIN van 4 t/m 8 cijfers."
                        : "Voer je beheer-PIN in."
        );
        subtitle.setGravity(
                Gravity.CENTER
        );
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(12);
        subtitle.setPadding(
                0,
                dp(14),
                0,
                dp(20)
        );

        root.addView(subtitle);

        pinOne =
                pinField(
                        creating
                                ? "Nieuwe PIN"
                                : "PIN"
                );

        root.addView(
                pinOne,
                fieldParams()
        );

        if (creating) {
            pinTwo =
                    pinField(
                            "Herhaal PIN"
                    );

            LinearLayout.LayoutParams confirmLp =
                    fieldParams();
            confirmLp.topMargin = dp(12);

            root.addView(
                    pinTwo,
                    confirmLp
            );
        }

        Button unlock =
                new Button(this);

        unlock.setAllCaps(false);
        unlock.setText(
                creating
                        ? "PIN OPSLAAN"
                        : "ONTGRENDEL BEHEER"
        );
        unlock.setTextColor(BG);
        unlock.setBackgroundColor(MINT);
        unlock.setOnClickListener(
                v -> submit()
        );

        LinearLayout.LayoutParams buttonLp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(58)
                );
        buttonLp.topMargin = dp(20);

        root.addView(
                unlock,
                buttonLp
        );

        Button cancel =
                new Button(this);

        cancel.setAllCaps(false);
        cancel.setText("Annuleren");
        cancel.setTextColor(TEXT);
        cancel.setBackgroundColor(PANEL);
        cancel.setOnClickListener(
                v -> finish()
        );

        LinearLayout.LayoutParams cancelLp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                );
        cancelLp.topMargin = dp(10);

        root.addView(
                cancel,
                cancelLp
        );

        setContentView(root);

        pinOne.requestFocus();
    }

    private EditText pinField(
            String hint
    ) {
        EditText field =
                new EditText(this);

        field.setHint(hint);
        field.setHintTextColor(MUTED);
        field.setTextColor(TEXT);
        field.setTextSize(22);
        field.setGravity(
                Gravity.CENTER
        );
        field.setSingleLine(true);
        field.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType
                        .TYPE_NUMBER_VARIATION_PASSWORD
        );
        field.setBackgroundColor(PANEL);

        return field;
    }

    private LinearLayout.LayoutParams fieldParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(62)
        );
    }

    private void submit() {
        String first =
                pinOne.getText()
                        .toString()
                        .trim();

        if (creating) {
            String second =
                    pinTwo == null
                            ? ""
                            : pinTwo.getText()
                                    .toString()
                                    .trim();

            if (!PinStore.isValidFormat(first)) {
                toast(
                        "PIN moet 4 t/m 8 cijfers zijn."
                );
                return;
            }

            if (!first.equals(second)) {
                toast(
                        "De twee PIN-codes zijn niet gelijk."
                );
                return;
            }

            try {
                PinStore.setPin(
                        this,
                        first
                );
            } catch (Exception e) {
                toast(
                        "PIN opslaan is mislukt."
                );
                return;
            }

            openAdmin();
            return;
        }

        if (!PinStore.verify(
                this,
                first
        )) {
            pinOne.setText("");
            toast("Onjuiste PIN.");
            return;
        }

        openAdmin();
    }

    private void openAdmin() {
        Intent intent =
                new Intent(
                        this,
                        AdminPanelActivity.class
                );

        if (openUpdateAfterUnlock) {
            intent.putExtra(
                    "start_update",
                    true
            );
        }

        startActivity(intent);
        finish();
    }

    private void toast(String text) {
        Toast.makeText(
                this,
                text,
                Toast.LENGTH_SHORT
        ).show();
    }

    private int dp(int value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
