package nl.thommie.ai;

import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

public class MaatjeVoiceInteractionSessionService
        extends VoiceInteractionSessionService {

    @Override
    public VoiceInteractionSession onNewSession(
            Bundle args
    ) {
        return new MaatjeVoiceInteractionSession(
                this
        );
    }
}
