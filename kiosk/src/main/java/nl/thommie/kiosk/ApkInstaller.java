package nl.thommie.kiosk;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.security.MessageDigest;
import java.util.Arrays;

final class ApkInstaller {

    static final String ACTION_INSTALL_STATUS =
            "nl.thommie.kiosk.INSTALL_STATUS";

    static final class ValidatedApk {
        final String packageName;
        final String versionName;
        final String displayName;

        ValidatedApk(
                String packageName,
                String versionName,
                String displayName
        ) {
            this.packageName = packageName;
            this.versionName = versionName;
            this.displayName = displayName;
        }
    }

    private ApkInstaller() {}

    static ValidatedApk validateUpdateApk(
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
                || archive.packageName == null) {
            throw new Exception(
                    "APK kon niet worden gelezen."
            );
        }

        String packageName =
                archive.packageName;

        boolean isMaatje =
                KioskPolicy.MAATJE_PACKAGE
                        .equals(packageName);

        boolean isKiosk =
                context.getPackageName()
                        .equals(packageName);

        if (!isMaatje
                && !isKiosk) {
            throw new Exception(
                    "Alleen MAATJE of MAATJE Kiosk kan via deze updater worden geïnstalleerd."
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
                installedSignerDigest(
                        context,
                        packageName
                );

        if (trustedCert == null
                || !Arrays.equals(
                        archiveCert,
                        trustedCert
                )) {
            throw new Exception(
                    "APK-handtekening komt niet overeen met de geïnstalleerde "
                            + (isKiosk
                            ? "MAATJE Kiosk."
                            : "MAATJE.")
            );
        }

        String version =
                archive.versionName == null
                        ? "onbekende versie"
                        : archive.versionName;

        return new ValidatedApk(
                packageName,
                version,
                isKiosk
                        ? "MAATJE Kiosk"
                        : "MAATJE"
        );
    }

    static void launchManualInstall(
            Context context,
            File apk,
            ValidatedApk info
    ) throws Exception {
        ValidatedApk checked =
                validateUpdateApk(
                        context,
                        apk
                );

        if (!checked.packageName.equals(
                info.packageName
        )) {
            throw new Exception(
                    "APK-doelpakket veranderde tijdens validatie."
            );
        }

        Intent bridge =
                InstallerBridgeActivity
                        .createFileInstallIntent(
                                context,
                                apk,
                                info.versionName,
                                info.packageName,
                                info.displayName
                        );

        context.startActivity(
                bridge
        );
    }

    private static byte[] installedSignerDigest(
            Context context,
            String packageName
    ) {
        PackageManager pm =
                context.getPackageManager();

        try {
            PackageInfo installed =
                    getPackageInfo(
                            pm,
                            packageName,
                            false
                    );

            return firstSignerDigest(
                    installed
            );

        } catch (Exception ignored) {}

        /*
         * Beide apps worden met dezelfde permanente key getekend.
         * Alleen als het doelpakket niet gevonden wordt, mag Kiosk zelf
         * als trust anchor dienen.
         */
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
