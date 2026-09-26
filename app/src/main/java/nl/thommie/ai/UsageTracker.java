package nl.thommie.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;

import java.text.NumberFormat;
import java.util.Locale;

final class UsageTracker {

    static final long CONTEXT_WINDOW = 1_050_000L;

    private static final String PREFS =
            "maatje_usage";
    private static final String KEY_LIFETIME_INPUT =
            "lifetime_input";
    private static final String KEY_LIFETIME_OUTPUT =
            "lifetime_output";
    private static final String KEY_LIFETIME_TOTAL =
            "lifetime_total";
    private static final String KEY_LAST_INPUT =
            "last_input";
    private static final String KEY_LAST_OUTPUT =
            "last_output";
    private static final String KEY_LAST_TOTAL =
            "last_total";

    private static long sessionInput = 0L;
    private static long sessionOutput = 0L;
    private static long sessionTotal = 0L;

    // Current GPT-5.6 Luna standard token prices.
    private static final double INPUT_USD_PER_MILLION = 0.20d;
    private static final double OUTPUT_USD_PER_MILLION = 1.20d;

    private UsageTracker() {}

    static synchronized void record(
            Context context,
            long inputTokens,
            long outputTokens,
            long totalTokens
    ) {
        long safeInput =
                Math.max(
                        0L,
                        inputTokens
                );

        long safeOutput =
                Math.max(
                        0L,
                        outputTokens
                );

        long safeTotal =
                Math.max(
                        safeInput + safeOutput,
                        totalTokens
                );

        sessionInput += safeInput;
        sessionOutput += safeOutput;
        sessionTotal += safeTotal;

        SharedPreferences p =
                prefs(context);

        p.edit()
                .putLong(
                        KEY_LAST_INPUT,
                        safeInput
                )
                .putLong(
                        KEY_LAST_OUTPUT,
                        safeOutput
                )
                .putLong(
                        KEY_LAST_TOTAL,
                        safeTotal
                )
                .putLong(
                        KEY_LIFETIME_INPUT,
                        p.getLong(
                                KEY_LIFETIME_INPUT,
                                0L
                        )
                                + safeInput
                )
                .putLong(
                        KEY_LIFETIME_OUTPUT,
                        p.getLong(
                                KEY_LIFETIME_OUTPUT,
                                0L
                        )
                                + safeOutput
                )
                .putLong(
                        KEY_LIFETIME_TOTAL,
                        p.getLong(
                                KEY_LIFETIME_TOTAL,
                                0L
                        )
                                + safeTotal
                )
                .apply();
    }

    static String compactLine(
            Context context
    ) {
        SharedPreferences p =
                prefs(context);

        long input =
                p.getLong(
                        KEY_LAST_INPUT,
                        0L
                );

        long output =
                p.getLong(
                        KEY_LAST_OUTPUT,
                        0L
                );

        if (input <= 0L
                && output <= 0L) {
            return "TOKENS • NOG GEEN API-USAGE";
        }

        long headroom =
                Math.max(
                        0L,
                        CONTEXT_WINDOW - input
                );

        return "TOKENS • IN "
                + shortNumber(input)
                + " • OUT "
                + shortNumber(output)
                + " • CTX ~"
                + shortNumber(headroom)
                + " VRIJ";
    }

    static void show(
            Activity activity
    ) {
        SharedPreferences p =
                prefs(activity);

        long lastInput =
                p.getLong(
                        KEY_LAST_INPUT,
                        0L
                );

        long lastOutput =
                p.getLong(
                        KEY_LAST_OUTPUT,
                        0L
                );

        long lastTotal =
                p.getLong(
                        KEY_LAST_TOTAL,
                        0L
                );

        long lifetimeInput =
                p.getLong(
                        KEY_LIFETIME_INPUT,
                        0L
                );

        long lifetimeOutput =
                p.getLong(
                        KEY_LIFETIME_OUTPUT,
                        0L
                );

        long lifetimeTotal =
                p.getLong(
                        KEY_LIFETIME_TOTAL,
                        0L
                );

        long contextHeadroom =
                Math.max(
                        0L,
                        CONTEXT_WINDOW - lastInput
                );

        String message =
                "LAATSTE RESPONSE\n"
                        + "Input: "
                        + format(lastInput)
                        + "\nOutput: "
                        + format(lastOutput)
                        + "\nTotaal: "
                        + format(lastTotal)
                        + "\nContext-input: "
                        + format(lastInput)
                        + " / "
                        + format(CONTEXT_WINDOW)
                        + "\nVrije context (indicatie): ~"
                        + format(contextHeadroom)
                        + "\n\nDEZE APP-SESSIE\n"
                        + "Input: "
                        + format(sessionInput)
                        + "\nOutput: "
                        + format(sessionOutput)
                        + "\nTotaal: "
                        + format(sessionTotal)
                        + "\nGeschatte modelkosten: "
                        + money(
                                estimateCost(
                                        sessionInput,
                                        sessionOutput
                                )
                        )
                        + "\n\nSINDS v1.1.1 TRACKING / INSTALLATIE\n"
                        + "Input: "
                        + format(lifetimeInput)
                        + "\nOutput: "
                        + format(lifetimeOutput)
                        + "\nTotaal: "
                        + format(lifetimeTotal)
                        + "\nGeschatte modelkosten: "
                        + money(
                                estimateCost(
                                        lifetimeInput,
                                        lifetimeOutput
                                )
                        )
                        + "\n\nDe contextmeter is geen API-tegoed of saldo. "
                        + "De kostenschatting telt alleen Luna input/output-tokens; "
                        + "TTS en eventuele web-search-toolkosten staan hier niet in.";

        new AlertDialog.Builder(activity)
                .setTitle(
                        "MAATJE v1.3.3 ONEPLUS – Gebruik & tokens"
                )
                .setMessage(message)
                .setPositiveButton(
                        "Sluiten",
                        null
                )
                .show();
    }

    private static double estimateCost(
            long input,
            long output
    ) {
        return (input
                / 1_000_000d
                * INPUT_USD_PER_MILLION)
                + (output
                / 1_000_000d
                * OUTPUT_USD_PER_MILLION);
    }

    private static String money(
            double usd
    ) {
        if (usd < 0.01d) {
            return String.format(
                    Locale.US,
                    "$%.4f",
                    usd
            );
        }

        return String.format(
                Locale.US,
                "$%.3f",
                usd
        );
    }

    private static String format(
            long value
    ) {
        return NumberFormat
                .getIntegerInstance(
                        new Locale(
                                "nl",
                                "NL"
                        )
                )
                .format(value);
    }

    private static String shortNumber(
            long value
    ) {
        if (value >= 1_000_000L) {
            return String.format(
                    Locale.US,
                    "%.2fM",
                    value / 1_000_000d
            );
        }

        if (value >= 1_000L) {
            return String.format(
                    Locale.US,
                    "%.1fK",
                    value / 1_000d
            );
        }

        return Long.toString(value);
    }

    private static SharedPreferences prefs(
            Context context
    ) {
        return context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
    }
}
