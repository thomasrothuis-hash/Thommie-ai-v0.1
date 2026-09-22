package nl.thommie.ai;

import android.content.Context;
import android.content.SharedPreferences;

final class MemoryStore {
    private static final String PREFS = "thommie_ai_memory";
    private static final String KEY_CONVERSATION = "conversation_id";
    private static final String KEY_PROFILE = "profile_memory";

    private MemoryStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String getConversationId(Context context) {
        return prefs(context).getString(KEY_CONVERSATION, "");
    }

    static void setConversationId(Context context, String id) {
        prefs(context).edit().putString(KEY_CONVERSATION, id == null ? "" : id).apply();
    }

    static void clearConversation(Context context) {
        prefs(context).edit().remove(KEY_CONVERSATION).apply();
    }

    static String getProfile(Context context) {
        return prefs(context).getString(KEY_PROFILE, "");
    }

    static void setProfile(Context context, String value) {
        prefs(context).edit().putString(KEY_PROFILE, value == null ? "" : value.trim()).apply();
    }

    static boolean captureExplicitMemory(Context context, String input) {
        if (input == null) return false;
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase();

        String memory = null;
        if (lower.startsWith("onthoud dat ")) {
            memory = trimmed.substring("onthoud dat ".length()).trim();
        } else if (lower.startsWith("onthoud:")) {
            memory = trimmed.substring("onthoud:".length()).trim();
        } else if (lower.startsWith("remember that ")) {
            memory = trimmed.substring("remember that ".length()).trim();
        }

        if (memory == null || memory.isEmpty()) return false;

        String current = getProfile(context);
        String line = "• " + memory;

        if (current.contains(line)) return true;

        if (current.isEmpty()) {
            setProfile(context, line);
        } else {
            setProfile(context, current + "\n" + line);
        }
        return true;
    }
}
