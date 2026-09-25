package nl.thommie.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;

final class InternetSettings {

    private static final String PREFS =
            "maatje_internet";
    private static final String KEY_ENABLED =
            "web_search_enabled";

    static final class CommandResult {
        final boolean handled;
        final String message;

        CommandResult(
                boolean handled,
                String message
        ) {
            this.handled = handled;
            this.message = message;
        }
    }

    private InternetSettings() {}

    static boolean enabled(
            Context context
    ) {
        return prefs(context)
                .getBoolean(
                        KEY_ENABLED,
                        true
                );
    }

    static void setEnabled(
            Context context,
            boolean enabled
    ) {
        prefs(context)
                .edit()
                .putBoolean(
                        KEY_ENABLED,
                        enabled
                )
                .apply();
    }

    static void show(
            Activity activity
    ) {
        String[] options = {
                "Automatisch",
                "Uit"
        };

        int checked =
                enabled(activity)
                        ? 0
                        : 1;

        new AlertDialog.Builder(activity)
                .setTitle(
                        "MAATJE v1.1.2 – Internet"
                )
                .setSingleChoiceItems(
                        options,
                        checked,
                        (dialog, which) -> {
                            boolean on =
                                    which == 0;

                            setEnabled(
                                    activity,
                                    on
                            );

                            dialog.dismiss();
                        }
                )
                .setMessage(
                        "Automatisch: MAATJE mag OpenAI web search gebruiken "
                                + "wanneer actuele of externe informatie nuttig is.\n\n"
                                + "Uit: antwoorden gebruiken geen web search."
                )
                .setNegativeButton(
                        "Sluiten",
                        null
                )
                .show();
    }

    static CommandResult handleCommand(
            Context context,
            String raw
    ) {
        if (raw == null) {
            return new CommandResult(
                    false,
                    ""
            );
        }

        String q =
                raw.toLowerCase(
                        java.util.Locale.ROOT
                )
                .replaceAll(
                        "[^\\p{L}\\p{N}\\s]",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();

        boolean internetMention =
                q.contains("internet")
                        || q.contains("web search")
                        || q.contains("web zoeken");

        if (!internetMention) {
            return new CommandResult(
                    false,
                    ""
            );
        }

        if (q.contains("zet internet uit")
                || q.equals("internet uit")
                || q.contains("web search uit")
                || q.contains("web zoeken uit")) {
            setEnabled(
                    context,
                    false
            );

            return new CommandResult(
                    true,
                    "Internet zoeken staat uit."
            );
        }

        if (q.contains("zet internet aan")
                || q.equals("internet aan")
                || q.contains("web search aan")
                || q.contains("web zoeken aan")) {
            setEnabled(
                    context,
                    true
            );

            return new CommandResult(
                    true,
                    "Internet zoeken staat op automatisch."
            );
        }

        if (q.contains("staat internet")
                || q.contains("is internet")
                || q.contains("internet status")
                || q.contains("web search status")) {
            return new CommandResult(
                    true,
                    enabled(context)
                            ? "Internet zoeken staat op automatisch."
                            : "Internet zoeken staat uit."
            );
        }

        return new CommandResult(
                false,
                ""
        );
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
