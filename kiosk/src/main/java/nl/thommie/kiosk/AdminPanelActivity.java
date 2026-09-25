package nl.thommie.kiosk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

public class AdminPanelActivity extends Activity {

    private static final int BG =
            Color.rgb(4, 8, 5);
    private static final int PANEL =
            Color.rgb(9, 17, 11);
    private static final int MINT =
            Color.rgb(54, 220, 104);
    private static final int TEXT =
            Color.rgb(216, 240, 222);
    private static final int MUTED =
            Color.rgb(105, 139, 113);

    private TextView ownerStatus;
    private TextView updateStatus;
    private TextView updateAddress;
    private ImageView qrView;

    private UpdateServer updateServer;
    private boolean maintenanceReturnPending =
            false;

    private final BroadcastReceiver installReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(
                        Context context,
                        Intent intent
                ) {
                    if (!ApkInstaller
                            .ACTION_INSTALL_STATUS
                            .equals(
                                    intent.getAction()
                            )) {
                        return;
                    }

                    String message =
                            intent.getStringExtra(
                                    "message"
                            );

                    if (message != null) {
                        updateStatus.setText(
                                message
                        );
                    }

                    int status =
                            intent.getIntExtra(
                                    "status",
                                    PackageInstaller
                                            .STATUS_FAILURE
                            );

                    if (status
                            == PackageInstaller
                            .STATUS_SUCCESS) {
                        stopUpdateServer();
                    }
                }
            };

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        buildUi();
        registerInstallReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (maintenanceReturnPending) {
            maintenanceReturnPending =
                    false;
            KioskPolicy.leaveMaintenance(
                    this
            );
        }

        refreshOwnerStatus();
    }

    @Override
    protected void onDestroy() {
        stopUpdateServer();

        try {
            unregisterReceiver(
                    installReceiver
            );
        } catch (Exception ignored) {}

        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll =
                new ScrollView(this);

        scroll.setBackgroundColor(BG);

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );
        root.setPadding(
                dp(22),
                dp(28),
                dp(22),
                dp(28)
        );

        TextView title =
                new TextView(this);

        title.setText(
                "MAATJE KIOSK • BEHEER"
        );
        title.setTextColor(MINT);
        title.setTextSize(20);
        title.setTypeface(
                Typeface.create(
                        Typeface.MONOSPACE,
                        Typeface.BOLD
                )
        );

        root.addView(title);

        ownerStatus =
                new TextView(this);

        ownerStatus.setTextColor(TEXT);
        ownerStatus.setTextSize(12);
        ownerStatus.setTypeface(
                Typeface.MONOSPACE
        );
        ownerStatus.setPadding(
                0,
                dp(12),
                0,
                dp(18)
        );

        root.addView(ownerStatus);

        root.addView(
                action(
                        "ANDROID-INSTELLINGEN OPENEN",
                        v -> {
                            maintenanceReturnPending =
                                    true;
                            KioskPolicy
                                    .openAndroidSettings(
                                            this
                                    );
                        }
                ),
                buttonParams()
        );

        root.addView(
                action(
                        "MAATJE UPDATE ONTVANGEN",
                        v -> startUpdateServer()
                ),
                spacedButtonParams()
        );

        updateStatus =
                new TextView(this);

        updateStatus.setText(
                "Update-server staat uit."
        );
        updateStatus.setTextColor(MUTED);
        updateStatus.setTextSize(12);
        updateStatus.setPadding(
                0,
                dp(12),
                0,
                dp(8)
        );

        root.addView(updateStatus);

        updateAddress =
                new TextView(this);

        updateAddress.setTextColor(TEXT);
        updateAddress.setTextSize(13);
        updateAddress.setTypeface(
                Typeface.MONOSPACE
        );
        updateAddress.setTextIsSelectable(
                true
        );

        root.addView(updateAddress);

        qrView =
                new ImageView(this);

        qrView.setVisibility(
                View.GONE
        );
        qrView.setAdjustViewBounds(true);

        LinearLayout.LayoutParams qrLp =
                new LinearLayout.LayoutParams(
                        dp(260),
                        dp(260)
                );
        qrLp.gravity = Gravity.CENTER;
        qrLp.topMargin = dp(12);
        qrLp.bottomMargin = dp(12);

        root.addView(
                qrView,
                qrLp
        );

        root.addView(
                action(
                        "MAATJE HERSTARTEN",
                        v -> KioskPolicy
                                .launchMaatje(this)
                ),
                spacedButtonParams()
        );

        root.addView(
                action(
                        "KIOSK NU VERGRENDELEN",
                        v -> {
                            KioskPolicy.apply(this);
                            KioskPolicy
                                    .launchMaatje(this);
                        }
                ),
                spacedButtonParams()
        );

        root.addView(
                action(
                        "BEHEER-PIN WIJZIGEN",
                        v -> showChangePin()
                ),
                spacedButtonParams()
        );

        Button close =
                action(
                        "SLUIT BEHEER → MAATJE",
                        v -> {
                            KioskPolicy.apply(this);
                            KioskPolicy
                                    .launchMaatje(this);
                            finish();
                        }
                );

        LinearLayout.LayoutParams closeLp =
                spacedButtonParams();
        closeLp.topMargin = dp(26);

        root.addView(
                close,
                closeLp
        );

        TextView recovery =
                new TextView(this);

        recovery.setText(
                "Testfase: USB-debugging en fabrieksreset worden nog niet door MAATJE Kiosk geblokkeerd. "
                        + "Dat blijft voorlopig de herstelroute."
        );
        recovery.setTextColor(MUTED);
        recovery.setTextSize(11);
        recovery.setPadding(
                0,
                dp(24),
                0,
                0
        );

        root.addView(recovery);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void refreshOwnerStatus() {
        boolean owner =
                KioskPolicy.isDeviceOwner(
                        this
                );

        ownerStatus.setText(
                owner
                        ? "DEVICE OWNER • ACTIEF\nLOCK TASK • BESCHIKBAAR\nMAATJE • ALLOWLISTED"
                        : "DEVICE OWNER • NIET ACTIEF\nKiosk wordt pas streng na de ADB provisioning."
        );
    }

    private void startUpdateServer() {
        if (!KioskPolicy.isDeviceOwner(
                this
        )) {
            toast(
                    "Activeer eerst Device Owner."
            );
            return;
        }

        stopUpdateServer();

        updateServer =
                new UpdateServer(
                        this,
                        new UpdateServer.Callback() {
                            @Override
                            public void onReady(
                                    String url,
                                    String code
                            ) {
                                runOnUiThread(() -> {
                                    updateStatus.setText(
                                            "UPDATE ONTVANGER ACTIEF • CODE "
                                                    + code
                                    );
                                    updateAddress.setText(
                                            url
                                    );
                                    showQr(url);
                                });
                            }

                            @Override
                            public void onStatus(
                                    String text
                            ) {
                                runOnUiThread(() ->
                                        updateStatus
                                                .setText(text)
                                );
                            }
                        }
                );

        try {
            updateServer.start();
        } catch (Exception e) {
            updateStatus.setText(
                    "Update-server kon niet starten: "
                            + e.getMessage()
            );
        }
    }

    private void stopUpdateServer() {
        UpdateServer server =
                updateServer;
        updateServer = null;

        if (server != null) {
            server.stop();
        }
    }

    private void showQr(
            String value
    ) {
        try {
            int size = 700;

            BitMatrix matrix =
                    new MultiFormatWriter()
                            .encode(
                                    value,
                                    BarcodeFormat.QR_CODE,
                                    size,
                                    size
                            );

            Bitmap bitmap =
                    Bitmap.createBitmap(
                            size,
                            size,
                            Bitmap.Config.RGB_565
                    );

            for (int y = 0;
                 y < size;
                 y++) {
                for (int x = 0;
                     x < size;
                     x++) {
                    bitmap.setPixel(
                            x,
                            y,
                            matrix.get(
                                    x,
                                    y
                            )
                                    ? Color.BLACK
                                    : Color.WHITE
                    );
                }
            }

            qrView.setImageBitmap(
                    bitmap
            );
            qrView.setVisibility(
                    View.VISIBLE
            );

        } catch (Exception e) {
            qrView.setVisibility(
                    View.GONE
            );
        }
    }

    private void showChangePin() {
        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );
        content.setPadding(
                dp(18),
                dp(8),
                dp(18),
                0
        );

        EditText first =
                pinField(
                        "Nieuwe PIN"
                );

        EditText second =
                pinField(
                        "Herhaal PIN"
                );

        content.addView(first);
        content.addView(second);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Beheer-PIN wijzigen"
                        )
                        .setView(content)
                        .setPositiveButton(
                                "Opslaan",
                                null
                        )
                        .setNegativeButton(
                                "Annuleren",
                                null
                        )
                        .create();

        dialog.setOnShowListener(
                ignored ->
                        dialog.getButton(
                                AlertDialog
                                        .BUTTON_POSITIVE
                        ).setOnClickListener(v -> {
                            String a =
                                    first.getText()
                                            .toString()
                                            .trim();
                            String b =
                                    second.getText()
                                            .toString()
                                            .trim();

                            if (!PinStore
                                    .isValidFormat(a)) {
                                toast(
                                        "PIN moet 4 t/m 8 cijfers zijn."
                                );
                                return;
                            }

                            if (!a.equals(b)) {
                                toast(
                                        "PIN-codes zijn niet gelijk."
                                );
                                return;
                            }

                            try {
                                PinStore.setPin(
                                        this,
                                        a
                                );
                                toast(
                                        "PIN gewijzigd."
                                );
                                dialog.dismiss();
                            } catch (Exception e) {
                                toast(
                                        "PIN wijzigen mislukt."
                                );
                            }
                        })
        );

        dialog.show();
    }

    private EditText pinField(
            String hint
    ) {
        EditText field =
                new EditText(this);

        field.setHint(hint);
        field.setSingleLine(true);
        field.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType
                        .TYPE_NUMBER_VARIATION_PASSWORD
        );

        return field;
    }

    private Button action(
            String text,
            View.OnClickListener listener
    ) {
        Button button =
                new Button(this);

        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(MINT);
        button.setBackgroundColor(PANEL);
        button.setOnClickListener(
                listener
        );

        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
        );
    }

    private LinearLayout.LayoutParams spacedButtonParams() {
        LinearLayout.LayoutParams lp =
                buttonParams();
        lp.topMargin = dp(10);
        return lp;
    }

    private void registerInstallReceiver() {
        IntentFilter filter =
                new IntentFilter(
                        ApkInstaller
                                .ACTION_INSTALL_STATUS
                );

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                    installReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );
        } else {
            registerReceiver(
                    installReceiver,
                    filter
            );
        }
    }

    private void toast(
            String text
    ) {
        Toast.makeText(
                this,
                text,
                Toast.LENGTH_SHORT
        ).show();
    }

    private int dp(int value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
