package nl.thommie.kiosk;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class UpdateServer {

    interface Callback {
        void onReady(
                String url,
                String code
        );

        void onStatus(
                String text
        );
    }

    private static final long MAX_APK_BYTES =
            160L * 1024L * 1024L;

    private final Context context;
    private final Callback callback;

    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private String code;
    private int port;

    UpdateServer(
            Context context,
            Callback callback
    ) {
        this.context =
                context.getApplicationContext();
        this.callback = callback;
    }

    void start() throws Exception {
        stop();

        code =
                String.format(
                        java.util.Locale.ROOT,
                        "%06d",
                        new SecureRandom()
                                .nextInt(1000000)
                );

        serverSocket =
                openServerSocket();

        port =
                serverSocket.getLocalPort();

        String ip =
                findLocalIpv4();

        String url =
                "http://"
                        + ip
                        + ":"
                        + port
                        + "/?code="
                        + code;

        running = true;

        callback.onReady(
                url,
                code
        );

        serverThread =
                new Thread(
                        this::runLoop,
                        "MaatjeKioskUpdateServer"
                );

        serverThread.start();
    }

    void stop() {
        running = false;

        ServerSocket socket =
                serverSocket;
        serverSocket = null;

        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }

        Thread thread =
                serverThread;
        serverThread = null;

        if (thread != null
                && thread != Thread.currentThread()) {
            try {
                thread.join(300);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void runLoop() {
        while (running) {
            try {
                Socket socket =
                        serverSocket.accept();

                handle(socket);

            } catch (Exception e) {
                if (running) {
                    callback.onStatus(
                            "Update-server fout: "
                                    + safeMessage(e)
                    );
                }
            }
        }
    }

    private void handle(
            Socket socket
    ) {
        try (
                Socket client = socket;
                BufferedInputStream input =
                        new BufferedInputStream(
                                client.getInputStream()
                        );
                BufferedOutputStream output =
                        new BufferedOutputStream(
                                client.getOutputStream()
                        )
        ) {
            client.setSoTimeout(30000);

            String requestLine =
                    readLine(input);

            if (requestLine == null
                    || requestLine.isEmpty()) {
                return;
            }

            String[] requestParts =
                    requestLine.split(" ");

            if (requestParts.length < 2) {
                sendText(
                        output,
                        400,
                        "Bad Request"
                );
                return;
            }

            String method =
                    requestParts[0];

            String path =
                    requestParts[1];

            Map<String, String> headers =
                    readHeaders(input);

            String suppliedCode =
                    queryParam(
                            path,
                            "code"
                    );

            if (!code.equals(
                    suppliedCode
            )) {
                sendText(
                        output,
                        403,
                        "Ongeldige of verlopen updatecode."
                );
                return;
            }

            if ("GET".equals(method)) {
                sendHtml(
                        output,
                        uploadPage()
                );
                return;
            }

            if ("POST".equals(method)
                    && path.startsWith(
                    "/upload"
            )) {
                receiveUpload(
                        input,
                        output,
                        headers
                );
                return;
            }

            sendText(
                    output,
                    404,
                    "Not Found"
            );

        } catch (Exception e) {
            callback.onStatus(
                    "Uploadverbinding mislukt: "
                            + safeMessage(e)
            );
        }
    }

    private void receiveUpload(
            InputStream input,
            OutputStream output,
            Map<String, String> headers
    ) throws Exception {
        long contentLength =
                parseLong(
                        headers.get(
                                "content-length"
                        )
                );

        if (contentLength <= 0L
                || contentLength
                > MAX_APK_BYTES) {
            sendText(
                    output,
                    413,
                    "Ongeldige APK-grootte."
            );
            return;
        }

        callback.onStatus(
                "APK wordt ontvangen..."
        );

        File target =
                new File(
                        context.getCacheDir(),
                        "maatje-update-upload.apk"
                );

        try (
                FileOutputStream file =
                        new FileOutputStream(
                                target,
                                false
                        )
        ) {
            byte[] buffer =
                    new byte[64 * 1024];

            long remaining =
                    contentLength;

            while (remaining > 0L) {
                int read =
                        input.read(
                                buffer,
                                0,
                                (int) Math.min(
                                        buffer.length,
                                        remaining
                                )
                        );

                if (read < 0) {
                    throw new Exception(
                            "Upload voortijdig afgebroken."
                    );
                }

                file.write(
                        buffer,
                        0,
                        read
                );

                remaining -= read;
            }

            file.flush();
        }

        ApkInstaller.ValidatedApk info =
                ApkInstaller
                        .validateUpdateApk(
                                context,
                                target
                        );

        callback.onStatus(
                info.displayName
                        + " "
                        + info.versionName
                        + " gevalideerd. Normale Android-installer openen..."
        );

        ApkInstaller.launchManualInstall(
                context,
                target,
                info
        );

        sendText(
                output,
                200,
                info.displayName
                        + " "
                        + info.versionName
                        + " ontvangen en gecontroleerd. Rond de installatie op de telefoon af."
        );

        running = false;

        ServerSocket socket =
                serverSocket;

        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    private ServerSocket openServerSocket()
            throws Exception {
        Exception last = null;

        for (int candidate = 8787;
             candidate <= 8797;
             candidate++) {
            try {
                ServerSocket socket =
                        new ServerSocket(
                                candidate
                        );

                socket.setReuseAddress(
                        true
                );

                return socket;

            } catch (Exception e) {
                last = e;
            }
        }

        throw last == null
                ? new Exception(
                "Geen vrije updatepoort."
        )
                : last;
    }

    private String findLocalIpv4()
            throws Exception {
        for (NetworkInterface network :
                Collections.list(
                        NetworkInterface
                                .getNetworkInterfaces()
                )) {
            if (!network.isUp()
                    || network.isLoopback()) {
                continue;
            }

            for (InetAddress address :
                    Collections.list(
                            network
                                    .getInetAddresses()
                    )) {
                if (address
                        instanceof Inet4Address
                        && !address.isLoopbackAddress()
                        && address.isSiteLocalAddress()) {
                    return address
                            .getHostAddress();
                }
            }
        }

        throw new Exception(
                "Geen lokaal Wi-Fi/LAN IP-adres gevonden."
        );
    }

    private Map<String, String> readHeaders(
            InputStream input
    ) throws Exception {
        Map<String, String> headers =
                new HashMap<>();

        while (true) {
            String line =
                    readLine(input);

            if (line == null
                    || line.isEmpty()) {
                return headers;
            }

            int colon =
                    line.indexOf(':');

            if (colon <= 0) {
                continue;
            }

            headers.put(
                    line.substring(
                                    0,
                                    colon
                            )
                            .trim()
                            .toLowerCase(
                                    java.util.Locale.ROOT
                            ),
                    line.substring(
                                    colon + 1
                            )
                            .trim()
            );
        }
    }

    private String readLine(
            InputStream input
    ) throws Exception {
        StringBuilder builder =
                new StringBuilder();

        int previous = -1;

        while (true) {
            int value =
                    input.read();

            if (value < 0) {
                return builder.length() == 0
                        ? null
                        : builder.toString();
            }

            if (previous == '\r'
                    && value == '\n') {
                builder.setLength(
                        Math.max(
                                0,
                                builder.length() - 1
                        )
                );
                return builder.toString();
            }

            builder.append(
                    (char) value
            );
            previous = value;

            if (builder.length() > 8192) {
                throw new Exception(
                        "HTTP-header te groot."
                );
            }
        }
    }

    private String queryParam(
            String path,
            String name
    ) {
        int question =
                path.indexOf('?');

        if (question < 0
                || question
                == path.length() - 1) {
            return "";
        }

        String query =
                path.substring(
                        question + 1
                );

        for (String pair :
                query.split("&")) {
            String[] parts =
                    pair.split(
                            "=",
                            2
                    );

            if (parts.length == 2
                    && name.equals(
                    decode(parts[0])
            )) {
                return decode(
                        parts[1]
                );
            }
        }

        return "";
    }

    private String decode(
            String value
    ) {
        try {
            return URLDecoder.decode(
                    value,
                    "UTF-8"
            );
        } catch (Exception ignored) {
            return value;
        }
    }

    private long parseLong(
            String value
    ) {
        try {
            return Long.parseLong(
                    value == null
                            ? ""
                            : value.trim()
            );
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private String uploadPage() {
        return "<!doctype html>"
                + "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>MAATJE Update</title>"
                + "<style>body{font-family:sans-serif;background:#040805;color:#d8f0de;padding:24px}"
                + "button{padding:14px;background:#36dc68;border:0;border-radius:12px;font-weight:bold}"
                + "input{margin:20px 0;width:100%}</style></head><body>"
                + "<h2>MAATJE update</h2>"
                + "<p>Kies een nieuwe gesigneerde MAATJE of MAATJE Kiosk APK.</p>"
                + "<input id='f' type='file' accept='.apk,application/vnd.android.package-archive'>"
                + "<button onclick='u()'>Upload naar MAATJE-terminal</button>"
                + "<p id='s'></p>"
                + "<script>async function u(){const f=document.getElementById('f').files[0];"
                + "if(!f){alert('Kies eerst een APK');return;}document.getElementById('s').innerText='Uploaden...';"
                + "const r=await fetch('/upload?code="
                + code
                + "',{method:'POST',headers:{'Content-Type':'application/vnd.android.package-archive','X-Filename':f.name},body:f});"
                + "document.getElementById('s').innerText=await r.text();}</script>"
                + "</body></html>";
    }

    private void sendHtml(
            OutputStream output,
            String html
    ) throws Exception {
        byte[] body =
                html.getBytes(
                        StandardCharsets.UTF_8
                );

        writeResponse(
                output,
                200,
                "text/html; charset=utf-8",
                body
        );
    }

    private void sendText(
            OutputStream output,
            int status,
            String text
    ) throws Exception {
        byte[] body =
                text.getBytes(
                        StandardCharsets.UTF_8
                );

        writeResponse(
                output,
                status,
                "text/plain; charset=utf-8",
                body
        );
    }

    private void writeResponse(
            OutputStream output,
            int status,
            String type,
            byte[] body
    ) throws Exception {
        String reason =
                status == 200
                        ? "OK"
                        : status == 403
                        ? "Forbidden"
                        : status == 404
                        ? "Not Found"
                        : status == 413
                        ? "Payload Too Large"
                        : "Bad Request";

        String headers =
                "HTTP/1.1 "
                        + status
                        + " "
                        + reason
                        + "\r\n"
                        + "Content-Type: "
                        + type
                        + "\r\n"
                        + "Content-Length: "
                        + body.length
                        + "\r\n"
                        + "Connection: close\r\n\r\n";

        output.write(
                headers.getBytes(
                        StandardCharsets.US_ASCII
                )
        );

        output.write(body);
        output.flush();
    }

    private String safeMessage(
            Throwable t
    ) {
        if (t == null
                || t.getMessage() == null
                || t.getMessage()
                .trim()
                .isEmpty()) {
            return "onbekende fout";
        }

        return t.getMessage();
    }
}
