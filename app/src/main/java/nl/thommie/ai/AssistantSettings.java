package nl.thommie.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.voice.VoiceInteractionService;
import android.widget.Toast;

final class AssistantSettings {

    static final int REQ_ASSISTANT_ROLE = 2602;

    private AssistantSettings() {}

    static boolean isAvailable(
            Context context
    ) {
        if (Build.VERSION.SDK_INT < 29) {
            return true;
        }

        RoleManager roleManager =
                (RoleManager) context.getSystemService(
                        Context.ROLE_SERVICE
                );

        return roleManager == null
                || roleManager.isRoleAvailable(
                        RoleManager.ROLE_ASSISTANT
                );
    }

    static boolean isSelected(
            Context context
    ) {
        ComponentName service =
                new ComponentName(
                        context,
                        MaatjeVoiceInteractionService.class
                );

        if (VoiceInteractionService.isActiveService(
                context,
                service
        )) {
            return true;
        }

        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager roleManager =
                    (RoleManager) context
                            .getSystemService(
                                    Context.ROLE_SERVICE
                            );

            return roleManager != null
                    && roleManager.isRoleHeld(
                            RoleManager.ROLE_ASSISTANT
                    );
        }

        return false;
    }

    static void show(
            Activity activity
    ) {
        boolean selected =
                isSelected(activity);

        String message =
                selected
                        ? "MAATJE is momenteel de standaard Android-assistent.\n\n"
                        + "De VoiceInteractionService kan daardoor beschikbaar blijven "
                        + "voor assistent-aanroepen en 'Hey Maatje' buiten de gewone app."
                        : "Kies in het volgende Android-scherm bij 'Digitale assistent-app' "
                        + "of 'Standaard digitale assistent' voor MAATJE.\n\n"
                        + "Dit moet via Android Instellingen; Android 16 laat de Assistant-role "
                        + "niet rechtstreeks door een app aanvragen.";

        new AlertDialog.Builder(activity)
                .setTitle(
                        "MAATJE v1.1.3 – Standaard assistent"
                )
                .setMessage(message)
                .setPositiveButton(
                        selected
                                ? "Open instellingen"
                                : "Naar assistent-keuze",
                        (dialog, which) ->
                                openAssistantSettings(
                                        activity
                                )
                )
                .setNegativeButton(
                        "Sluiten",
                        null
                )
                .show();
    }

    static void requestRole(
            Activity activity
    ) {
        // ROLE_ASSISTANT is not requestable on current Android.
        // Always send the user to the system's Assist & voice input screen.
        openAssistantSettings(
                activity
        );
    }

    static void openAssistantSettings(
            Activity activity
    ) {
        Intent[] candidates =
                new Intent[]{
                        new Intent(
                                Settings.ACTION_VOICE_INPUT_SETTINGS
                        ),
                        new Intent(
                                Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS
                        ),
                        new Intent(
                                Settings.ACTION_SETTINGS
                        )
                };

        for (Intent intent : candidates) {
            try {
                if (intent.resolveActivity(
                        activity.getPackageManager()
                ) != null) {
                    activity.startActivity(
                            intent
                    );

                    Toast.makeText(
                            activity,
                            "Kies bij Digitale assistent-app voor MAATJE.",
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }
            } catch (Exception ignored) {}
        }

        Toast.makeText(
                activity,
                "Assistent-instellingen konden niet worden geopend.",
                Toast.LENGTH_LONG
        ).show();
    }
}
