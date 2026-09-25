package nl.thommie.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

final class AssistantSettings {

    static final int REQ_ASSISTANT_ROLE = 2602;

    private AssistantSettings() {}

    static boolean isAvailable(
            Context context
    ) {
        if (Build.VERSION.SDK_INT < 29) {
            return false;
        }

        RoleManager roleManager =
                (RoleManager) context.getSystemService(
                        Context.ROLE_SERVICE
                );

        return roleManager != null
                && roleManager.isRoleAvailable(
                        RoleManager.ROLE_ASSISTANT
                );
    }

    static boolean isSelected(
            Context context
    ) {
        if (!isAvailable(context)) {
            return false;
        }

        RoleManager roleManager =
                (RoleManager) context.getSystemService(
                        Context.ROLE_SERVICE
                );

        return roleManager != null
                && roleManager.isRoleHeld(
                        RoleManager.ROLE_ASSISTANT
                );
    }

    static void show(
            Activity activity
    ) {
        boolean available =
                isAvailable(activity);

        boolean selected =
                isSelected(activity);

        String message;

        if (!available) {
            message =
                    "Android biedt op dit toestel geen Assistant-role via RoleManager aan. "
                            + "Ik kan wel het systeemscherm voor assistent/spraak openen.";
        } else if (selected) {
            message =
                    "MAATJE is momenteel de standaard assistent.\n\n"
                            + "Wanneer Android de assistent aanroept, opent MAATJE direct in luistermodus. "
                            + "Als 'Hey Maatje' aan staat, luistert de geselecteerde VoiceInteractionService "
                            + "ook buiten de gewone app via de lokale Vosk wake-word engine.";
        } else {
            message =
                    "Maak MAATJE de standaard Android-assistent.\n\n"
                            + "Android houdt de geselecteerde VoiceInteractionService beschikbaar voor "
                            + "assistent-aanroepen en achtergrond-hotwording. "
                            + "Je moet dit éénmalig zelf bevestigen in het systeemvenster.";
        }

        AlertDialog dialog =
                new AlertDialog.Builder(activity)
                        .setTitle(
                                "MAATJE v0.9.3 – Standaard assistent"
                        )
                        .setMessage(message)
                        .setPositiveButton(
                                selected
                                        ? "Systeeminstellingen"
                                        : "Kies MAATJE",
                                null
                        )
                        .setNegativeButton(
                                "Sluiten",
                                null
                        )
                        .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(v -> {
                if (available
                        && !isSelected(activity)) {
                    requestRole(activity);
                } else {
                    openAssistantSettings(
                            activity
                    );
                }
            });
        });

        dialog.show();
    }

    static void requestRole(
            Activity activity
    ) {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager roleManager =
                    (RoleManager) activity
                            .getSystemService(
                                    Context.ROLE_SERVICE
                            );

            if (roleManager != null
                    && roleManager.isRoleAvailable(
                            RoleManager.ROLE_ASSISTANT
                    )) {
                Intent intent =
                        roleManager
                                .createRequestRoleIntent(
                                        RoleManager.ROLE_ASSISTANT
                                );

                activity.startActivityForResult(
                        intent,
                        REQ_ASSISTANT_ROLE
                );

                return;
            }
        }

        openAssistantSettings(
                activity
        );
    }

    static void openAssistantSettings(
            Activity activity
    ) {
        try {
            Intent intent =
                    new Intent(
                            Settings.ACTION_VOICE_INPUT_SETTINGS
                    );

            activity.startActivity(intent);

        } catch (Exception e) {
            try {
                activity.startActivity(
                        new Intent(
                                Settings.ACTION_SETTINGS
                        )
                );
            } catch (Exception ignored) {
                Toast.makeText(
                        activity,
                        "Assistent-instellingen konden niet worden geopend.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }
}
