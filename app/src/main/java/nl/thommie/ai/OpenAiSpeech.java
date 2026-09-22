package nl.thommie.ai;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class OpenAiSpeech {

    private OpenAiSpeech() {}

    static File synthesize(
            Context context,
            String apiKey,
            String text,
            String voice,
            String instructions,
            float speed
    ) throws Exception {

        String cleaned = text == null ? "" : text.trim();
        if (cleaned.length() > 4000) {
            cleaned = cleaned.substring(0, 4000);
        }

        URL url = new URL("https://api.openai.com/v1/audio/speech");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(90000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");

        JSONObject body = new JSONObject();
        body.put("model", "gpt-4o-mini-tts");
        body.put("voice", voice);
        body.put("input", cleaned);
        body.put("response_format", "mp3");
        body.put("speed", speed);

        if (instructions != null && !instructions.trim().isEmpty()) {
            body.put("instructions", instructions.trim());
        }

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();

        if (status < 200 || status >= 300) {
            InputStream err = conn.getErrorStream();
            throw new Exception("Voice API " + status + ": " + readAll(err));
        }

        File out = new File(
                context.getCacheDir(),
                "thommie_voice_" + System.nanoTime() + ".mp3"
        );

        try (InputStream in = conn.getInputStream();
             FileOutputStream fos = new FileOutputStream(out)) {

            byte[] buffer = new byte[8192];
            int read;

            while ((read = in.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        } finally {
            conn.disconnect();
        }

        return out;
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br =
                     new BufferedReader(
                             new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
