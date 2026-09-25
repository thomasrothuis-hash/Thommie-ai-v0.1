package nl.thommie.ai;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MaatjeScheduleActivity
        extends Activity {

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

    private LinearLayout listBox;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void buildUi() {
        ScrollView scroll =
                new ScrollView(this);

        scroll.setBackgroundColor(BG);

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );
        root.setPadding(
                dp(20),
                dp(28),
                dp(20),
                dp(28)
        );

        TextView title =
                new TextView(this);

        title.setText(
                "MAATJE • TIMERS & WEKKERS"
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

        listBox =
                new LinearLayout(this);

        listBox.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams listLp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams
                                .MATCH_PARENT,
                        LinearLayout.LayoutParams
                                .WRAP_CONTENT
                );
        listLp.topMargin = dp(18);

        root.addView(
                listBox,
                listLp
        );

        Button cancelAll =
                new Button(this);

        cancelAll.setAllCaps(false);
        cancelAll.setText(
                "ALLE TIMERS & WEKKERS ANNULEREN"
        );
        cancelAll.setTextColor(MINT);
        cancelAll.setBackgroundColor(PANEL);
        cancelAll.setOnClickListener(
                v -> {
                    MaatjeScheduler.cancelAll(
                            this
                    );
                    refresh();
                }
        );

        LinearLayout.LayoutParams buttonLp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams
                                .MATCH_PARENT,
                        dp(56)
                );
        buttonLp.topMargin = dp(20);

        root.addView(
                cancelAll,
                buttonLp
        );

        scroll.addView(root);
        setContentView(scroll);
    }

    private void refresh() {
        if (listBox == null) {
            return;
        }

        listBox.removeAllViews();

        List<MaatjeScheduler.Entry> entries =
                MaatjeScheduler.list(this);

        if (entries.isEmpty()) {
            TextView empty =
                    new TextView(this);

            empty.setText(
                    "Geen actieve timers of wekkers."
            );
            empty.setTextColor(MUTED);
            empty.setTextSize(13);

            listBox.addView(empty);
            return;
        }

        SimpleDateFormat format =
                new SimpleDateFormat(
                        "EEE dd-MM HH:mm:ss",
                        Locale.getDefault()
                );

        for (MaatjeScheduler.Entry entry :
                entries) {
            LinearLayout row =
                    new LinearLayout(this);

            row.setOrientation(
                    LinearLayout.VERTICAL
            );
            row.setPadding(
                    dp(14),
                    dp(14),
                    dp(14),
                    dp(14)
            );
            row.setBackgroundColor(PANEL);

            TextView name =
                    new TextView(this);

            name.setText(
                    (
                            MaatjeScheduler.TYPE_ALARM
                                    .equals(entry.type)
                                    ? "WEKKER • "
                                    : "TIMER • "
                    )
                            + entry.label
            );
            name.setTextColor(TEXT);
            name.setTextSize(14);
            name.setTypeface(
                    Typeface.DEFAULT_BOLD
            );

            row.addView(name);

            TextView when =
                    new TextView(this);

            when.setText(
                    format.format(
                            new Date(
                                    entry.triggerAt
                            )
                    )
            );
            when.setTextColor(MUTED);
            when.setTextSize(12);
            when.setPadding(
                    0,
                    dp(4),
                    0,
                    dp(8)
            );

            row.addView(when);

            Button cancel =
                    new Button(this);

            cancel.setAllCaps(false);
            cancel.setText("Annuleren");
            cancel.setTextColor(MINT);
            cancel.setOnClickListener(
                    v -> {
                        MaatjeScheduler.cancel(
                                this,
                                entry.id
                        );
                        refresh();
                    }
            );

            row.addView(cancel);

            LinearLayout.LayoutParams rowLp =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams
                                    .MATCH_PARENT,
                            LinearLayout.LayoutParams
                                    .WRAP_CONTENT
                    );
            rowLp.bottomMargin = dp(10);

            listBox.addView(
                    row,
                    rowLp
            );
        }
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
