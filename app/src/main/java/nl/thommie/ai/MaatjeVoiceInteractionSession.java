package nl.thommie.ai;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;

final class MaatjeVoiceInteractionSession
        extends VoiceInteractionSession {

    MaatjeVoiceInteractionSession(
            Context context
    ) {
        super(context);
    }

    @Override
    public void onShow(
            Bundle args,
            int showFlags
    ) {
        super.onShow(
                args,
                showFlags
        );

        Intent intent =
                new Intent(
                        getContext(),
                        MainActivity.class
                );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        intent.putExtra(
                MainActivity
                        .EXTRA_ASSISTANT_INVOCATION,
                true
        );

        String source =
                args == null
                        ? ""
                        : args.getString(
                                MaatjeVoiceInteractionService
                                        .EXTRA_WAKE_SOURCE,
                                ""
                        );

        intent.putExtra(
                MainActivity
                        .EXTRA_ASSISTANT_SOURCE,
                source
        );

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startAssistantActivity(
                        intent,
                        new Bundle()
                );
            } else {
                startVoiceActivity(
                        intent
                );
            }
        } catch (Exception first) {
            try {
                intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                );

                getContext()
                        .startActivity(intent);
            } catch (Exception ignored) {}
        }

        finish();
    }
}
