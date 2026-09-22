package nl.thommie.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class OpenAiClient {
    static final class Reply {
        final String responseId;
        final String text;
        Reply(String responseId, String text) {
            this.responseId = responseId;
            this.text = text;
        }
    }

    private OpenAiClient() {}

    static Reply ask(String apiKey, String model, String previousResponseId, String input) throws Exception {
        URL url = new URL("https://api.openai.com/v1/responses");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(90000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("input", input);
        body.put("store", true);
        body.put("instructions", "Je bent THOMMIE AI, een snelle persoonlijke assistent op een dedicated Android-toestel. Antwoord standaard in het Nederlands, compact maar technisch correct. Als de gebruiker technisch doorvraagt, mag je diep gaan.");
        if (previousResponseId != null && !previousResponseId.isEmpty()) {
            body.put("previous_response_id", previousResponseId);
        }

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
        String raw = readAll(stream);
        if (status < 200 || status >= 300) {
            throw new Exception(extractError(raw, status));
        }

        JSONObject root = new JSONObject(raw);
        String id = root.optString("id", "");
        String text = extractOutputText(root);
        if (text.isEmpty()) text = "Ik kreeg een leeg antwoord terug van de API.";
        return new Reply(id, text);
    }

    private static String extractOutputText(JSONObject root) {
        StringBuilder sb = new StringBuilder();
        JSONArray output = root.optJSONArray("output");
        if (output == null) return "";
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) continue;
            JSONArray content = item.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part == null) continue;
                if ("output_text".equals(part.optString("type"))) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(part.optString("text", ""));
                }
            }
        }
        return sb.toString().trim();
    }

    private static String extractError(String raw, int status) {
        try {
            JSONObject root = new JSONObject(raw);
            JSONObject err = root.optJSONObject("error");
            if (err != null) return "API " + status + ": " + err.optString("message", raw);
        } catch (Exception ignored) {}
        return "API-fout " + status + (raw == null || raw.isEmpty() ? "" : ": " + raw);
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
