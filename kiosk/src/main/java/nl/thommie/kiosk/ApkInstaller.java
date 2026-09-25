package nl.thommie.kiosk;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Arrays;

final class ApkInstaller {

    static final String ACTION_INSTALL_STATUS =
            "nl.thommie.kiosk.INSTALL_STATUS";

    private ApkInstaller() {}

    static String validateMaatjeApk(
            Context context,
            File apk
    ) throws Exception {
        if (apk == null
                || !apk.isFile()
                || apk.length() < 1024L) {
            throw new Exception(
                    "Ongeldig APK-bestand."
            );
        }

        PackageManager pm =
                context.getPackageManager();

        PackageInfo archive =
                getPackageInfo(
                        pm,
                        apk.getAbsolutePath(),
                        true
                );

        if (archive == null
                || !KioskPolicy.MAATJE_PACKAGE
                .equals(archive.packageName)) {
            throw new Exception(
                    "Dit is geen MAATJE APK."
            );
        }

        byte[] archiveCert =
                firstSignerDigest(
                        archive
                );

        if (archiveCert == null) {
            throw new Exception(
                    "APK-handtekening ontbreekt."
            );
        }

        byte[] trustedCert =
                trustedSignerDigest(
                        context
                );

        if (trustedCert == null
                || !Arrays.equals(
                        archiveCert,
                        trustedCert
                )) {
            throw new Exception(
                    "APK-handtekening komt niet overeen met MAATJE/Kiosk."
            );
        }

        return archive.versionName == null
                ? "onbekende versie"
                : archive.versionName;
    }

    static void installMaatje(
            Context context,
            File apk
    ) throws Exception {
        validateMaatjeApk(
                context,
                apk
        );

        PackageInstaller installer =
                context.getPackageManager()
                        .getPackageInstaller();

        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(
                        PackageInstaller
                                .SessionParams
                                .MODE_FULL_INSTALL
                );

        params.setAppPackageName(
                KioskPolicy.MAATJE_PACKAGE
        );

        if (Build.VERSION.SDK_INT >= 26) {
            params.setInstallReason(
                    PackageManager
                            .INSTALL_REASON_POLICY
            );
        }

        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(
                    PackageInstaller
                            .SessionParams
                            .USER_ACTION_NOT_REQUIRED
            );
        }

        int sessionId =
                installer.createSession(
                        params
                );

        PackageInstaller.Session session =
                installer.openSession(
                        sessionId
                );

        try {
            try (
                    FileInputStream input =
                            new FileInputStream(apk);
                    OutputStream output =
                            session.openWrite(
                                    "base.apk",
                                    0,
                                    apk.length()
                            )
            ) {
                byte[] buffer =
                        new byte[64 * 1024];

                int read;

                while ((read = input.read(buffer)) > 0) {
                    output.write(
                            buffer,
                            0,
                            read
                    );
                }

                session.fsync(output);
            }

            Intent resultIntent =
                    new Intent(
                            context,
                            InstallResultReceiver.class
                    );

            resultIntent.setAction(
                    ACTION_INSTALL_STATUS
            );

            int flags =
                    PendingIntent.FLAG_UPDATE_CURRENT;

            if (Build.VERSION.SDK_INT >= 31) {
                flags |=
                        PendingIntent.FLAG_MUTABLE;
            }

            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            context,
                            sessionId,
                            resultIntent,
                            flags
                    );

            IntentSender sender =
                    pendingIntent.getIntentSender();

            session.commit(sender);

        } finally {
            session.close();
        }
    }

    private static byte[] trustedSignerDigest(
            Context context
    ) {
        PackageManager pm =
                context.getPackageManager();

        try {
            PackageInfo maatje =
                    getPackageInfo(
                            pm,
                            KioskPolicy.MAATJE_PACKAGE,
                            false
                    );

            byte[] digest =
                    firstSignerDigest(
                            maatje
                    );

            if (digest != null) {
                return digest;
            }

        } catch (Exception ignored) {}

        try {
            PackageInfo kiosk =
                    getPackageInfo(
                            pm,
                            context.getPackageName(),
                            false
                    );

            return firstSignerDigest(
                    kiosk
            );

        } catch (Exception ignored) {
            return null;
        }
    }

    private static PackageInfo getPackageInfo(
            PackageManager pm,
            String source,
            boolean archive
    ) throws Exception {
        if (archive) {
            if (Build.VERSION.SDK_INT >= 33) {
                return pm.getPackageArchiveInfo(
                        source,
                        PackageManager
                                .PackageInfoFlags
                                .of(
                                        PackageManager
                                                .GET_SIGNING_CERTIFICATES
                                )
                );
            }

            return pm.getPackageArchiveInfo(
                    source,
                    Build.VERSION.SDK_INT >= 28
                            ? PackageManager
                            .GET_SIGNING_CERTIFICATES
                            : PackageManager
                            .GET_SIGNATURES
            );
        }

        if (Build.VERSION.SDK_INT >= 33) {
            return pm.getPackageInfo(
                    source,
                    PackageManager
                            .PackageInfoFlags
                            .of(
                                    PackageManager
                                            .GET_SIGNING_CERTIFICATES
                            )
            );
        }

        return pm.getPackageInfo(
                source,
                Build.VERSION.SDK_INT >= 28
                        ? PackageManager
                        .GET_SIGNING_CERTIFICATES
                        : PackageManager
                        .GET_SIGNATURES
        );
    }

    private static byte[] firstSignerDigest(
            PackageInfo info
    ) throws Exception {
        if (info == null) {
            return null;
        }

        Signature signature = null;

        if (Build.VERSION.SDK_INT >= 28
                && info.signingInfo != null) {
            Signature[] signers =
                    info.signingInfo
                            .hasMultipleSigners()
                            ? info.signingInfo
                            .getApkContentsSigners()
                            : info.signingInfo
                            .getSigningCertificateHistory();

            if (signers != null
                    && signers.length > 0) {
                signature = signers[0];
            }
        } else if (info.signatures != null
                && info.signatures.length > 0) {
            signature =
                    info.signatures[0];
        }

        if (signature == null) {
            return null;
        }

        return MessageDigest
                .getInstance("SHA-256")
                .digest(
                        signature.toByteArray()
                );
    }
}
