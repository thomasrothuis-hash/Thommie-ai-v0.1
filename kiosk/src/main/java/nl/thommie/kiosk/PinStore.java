package nl.thommie.kiosk;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class PinStore {

    private static final String PREFS = "maatje_kiosk_secure";
    private static final String KEY_SALT = "pin_salt";
    private static final String KEY_HASH = "pin_hash";
    private static final int ITERATIONS = 180000;
    private static final int KEY_BITS = 256;

    private PinStore() {}

    static boolean hasPin(Context context) {
        SharedPreferences p =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );

        return !p.getString(
                KEY_HASH,
                ""
        ).isEmpty();
    }

    static boolean isValidFormat(String pin) {
        return pin != null
                && pin.matches("\\d{4,8}");
    }

    static void setPin(
            Context context,
            String pin
    ) throws Exception {
        if (!isValidFormat(pin)) {
            throw new IllegalArgumentException(
                    "PIN moet 4 t/m 8 cijfers bevatten."
            );
        }

        byte[] salt =
                new byte[24];
        new SecureRandom().nextBytes(salt);

        byte[] hash =
                derive(pin, salt);

        context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                )
                .edit()
                .putString(
                        KEY_SALT,
                        Base64.encodeToString(
                                salt,
                                Base64.NO_WRAP
                        )
                )
                .putString(
                        KEY_HASH,
                        Base64.encodeToString(
                                hash,
                                Base64.NO_WRAP
                        )
                )
                .apply();
    }

    static boolean verify(
            Context context,
            String pin
    ) {
        try {
            SharedPreferences p =
                    context.getSharedPreferences(
                            PREFS,
                            Context.MODE_PRIVATE
                    );

            String saltText =
                    p.getString(
                            KEY_SALT,
                            ""
                    );

            String hashText =
                    p.getString(
                            KEY_HASH,
                            ""
                    );

            if (saltText.isEmpty()
                    || hashText.isEmpty()) {
                return false;
            }

            byte[] salt =
                    Base64.decode(
                            saltText,
                            Base64.DEFAULT
                    );

            byte[] expected =
                    Base64.decode(
                            hashText,
                            Base64.DEFAULT
                    );

            byte[] actual =
                    derive(
                            pin == null ? "" : pin,
                            salt
                    );

            if (actual.length != expected.length) {
                return false;
            }

            int diff = 0;

            for (int i = 0; i < actual.length; i++) {
                diff |=
                        actual[i]
                                ^ expected[i];
            }

            return diff == 0;

        } catch (Exception ignored) {
            return false;
        }
    }

    private static byte[] derive(
            String pin,
            byte[] salt
    ) throws Exception {
        PBEKeySpec spec =
                new PBEKeySpec(
                        pin.toCharArray(),
                        salt,
                        ITERATIONS,
                        KEY_BITS
                );

        try {
            return SecretKeyFactory
                    .getInstance(
                            "PBKDF2WithHmacSHA256"
                    )
                    .generateSecret(spec)
                    .getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
