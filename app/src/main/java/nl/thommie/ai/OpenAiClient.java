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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class OpenAiClient {

    interface StreamListener {
        void onText(String text);
    }

    static final class Source {
        final String title;
        final String url;

        Source(
                String title,
                String url
        ) {
            this.title = title;
            this.url = url;
        }
    }

    static final class Reply {
        final String responseId;
        final String text;
        final boolean webUsed;
        final List<Source> sources;
        final long inputTokens;
        final long outputTokens;
        final long totalTokens;
        final long cachedTokens;

        Reply(
                String responseId,
                String text,
                boolean webUsed,
                List<Source> sources,
                long inputTokens,
                long outputTokens,
                long totalTokens,
                long cachedTokens
        ) {
            this.responseId = responseId;
            this.text = text;
            this.webUsed = webUsed;
            this.sources = sources;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.totalTokens = totalTokens;
            this.cachedTokens = cachedTokens;
        }
    }

    private OpenAiClient() {}

    static String createConversation(String apiKey) throws Exception {
        URL url = new URL("https://api.openai.com/v1/conversations");
        HttpURLConnection conn = open(url, apiKey);

        JSONObject body = new JSONObject();
        JSONObject metadata = new JSONObject();
        metadata.put("app", "MAATJE");
        metadata.put("version", "1.2.0");
        body.put("metadata", metadata);

        writeJson(conn, body);

        int status = conn.getResponseCode();
        String raw = readAll(
                status >= 200 && status < 300
                        ? conn.getInputStream()
                        : conn.getErrorStream()
        );
        conn.disconnect();

        if (status < 200 || status >= 300) {
            throw new Exception(extractError(raw, status));
        }

        JSONObject root = new JSONObject(raw);
        String id = root.optString("id", "");

        if (id.isEmpty()) {
            throw new Exception(
                    "Conversation API gaf geen conversation-id terug."
            );
        }

        return id;
    }

    static Reply ask(
            String apiKey,
            String model,
            String conversationId,
            String input,
            String profileMemory,
            String personalityPrompt,
            boolean webSearchEnabled
    ) throws Exception {
        return askInternal(
                apiKey,
                model,
                conversationId,
                input,
                null,
                "high",
                profileMemory,
                personalityPrompt,
                webSearchEnabled,
                null
        );
    }

    static Reply askWithImage(
            String apiKey,
            String model,
            String conversationId,
            String input,
            String imageDataUrl,
            String profileMemory,
            String personalityPrompt,
            boolean webSearchEnabled
    ) throws Exception {
        return askInternal(
                apiKey,
                model,
                conversationId,
                input,
                imageDataUrl,
                "high",
                profileMemory,
                personalityPrompt,
                webSearchEnabled,
                null
        );
    }

    static Reply askStreaming(
            String apiKey,
            String model,
            String conversationId,
            String input,
            String profileMemory,
            String personalityPrompt,
            boolean webSearchEnabled,
            StreamListener listener
    ) throws Exception {
        return askInternal(
                apiKey,
                model,
                conversationId,
                input,
                null,
                "high",
                profileMemory,
                personalityPrompt,
                webSearchEnabled,
                listener
        );
    }

    static Reply askWithImageStreaming(
            String apiKey,
            String model,
            String conversationId,
            String input,
            String imageDataUrl,
            String imageDetail,
            String profileMemory,
            String personalityPrompt,
            boolean webSearchEnabled,
            StreamListener listener
    ) throws Exception {
        return askInternal(
                apiKey,
                model,
                conversationId,
                input,
                imageDataUrl,
                imageDetail,
                profileMemory,
                personalityPrompt,
                webSearchEnabled,
                listener
        );
    }

    private static Reply askInternal(
            String apiKey,
            String model,
            String conversationId,
            String input,
            String imageDataUrl,
            String imageDetail,
            String profileMemory,
            String personalityPrompt,
            boolean webSearchEnabled,
            StreamListener streamListener
    ) throws Exception {

        URL url = new URL("https://api.openai.com/v1/responses");
        HttpURLConnection conn = open(url, apiKey);

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put(
                "reasoning",
                new JSONObject().put("effort", "none")
        );
        body.put(
                "text",
                new JSONObject().put("verbosity", "low")
        );
        body.put("max_output_tokens", 700);
        body.put("prompt_cache_key", "maatje-v1");
        body.put("prompt_cache_retention", "24h");

        if (imageDataUrl == null
                || imageDataUrl.trim().isEmpty()) {
            body.put("input", input);
        } else {
            JSONArray requestInput = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");

            JSONArray content = new JSONArray();
            content.put(
                    new JSONObject()
                            .put("type", "input_text")
                            .put("text", input)
            );
            content.put(
                    new JSONObject()
                            .put("type", "input_image")
                            .put("image_url", imageDataUrl)
                            .put(
                                    "detail",
                                    imageDetail == null
                                            ? "low"
                                            : imageDetail
                            )
            );

            message.put("content", content);
            requestInput.put(message);
            body.put("input", requestInput);
        }

        if (conversationId != null && !conversationId.isEmpty()) {
            body.put("conversation", conversationId);
        }

        if (webSearchEnabled) {
            JSONArray tools = new JSONArray();
            JSONObject web = new JSONObject();
            web.put("type", "web_search");
            web.put("search_context_size", "low");
            web.put("external_web_access", true);
            tools.put(web);
            body.put("tools", tools);
            body.put(
                    "tool_choice",
                    InternetSettings.shouldForceWeb(input)
                            ? "required"
                            : "auto"
            );
            body.put(
                    "include",
                    new JSONArray()
                            .put(
                                    "web_search_call.action.sources"
                            )
            );
        }

        String instructions =
                "Je bent MAATJE, een persoonlijke assistent op een dedicated Android-toestel. "
                + "Antwoord standaard in het Nederlands. "
                + "Wees slim, relaxed, direct, technisch competent en menselijk. "
                + "Reageer als een snelle spraakassistent: geef bij gewone vragen standaard een kort antwoord van ongeveer 1 tot 3 zinnen. "
                + "Ga alleen uitgebreid in detail wanneer de gebruiker daarom vraagt of wanneer extra uitleg echt nodig is. "
                + "Geen overdreven klantenservice-toon, geen onnodige emoji's en geen lange beleefdheidsintroducties. "
                + "Gebruik korte natuurlijke bevestigingen waar passend. "
                + "Als de gebruiker technisch doorvraagt, mag je diep gaan. "
                + "Je hoeft niet overdreven netjes te praten; normale spreektaal en passend gevloek zijn toegestaan. "
                + "BELANGRIJK: je kunt lokale appinstellingen niet zelf wijzigen. "
                + "Zeg nooit dat je humor, sarcasme, droogheid, enthousiasme, mood of scheldniveau hebt aangepast "
                + "tenzij de Android-app het commando lokaal heeft afgehandeld voordat deze request werd verstuurd. "
                + "Als een wijzigingsverzoek toch bij jou terechtkomt, zeg kort dat het lokale commando niet herkend is "
                + "in plaats van te doen alsof de instelling gewijzigd is. "
                + "Gebruik gewone platte tekst zonder Markdown-opmaak. Gebruik geen dubbele sterretjes, enkele sterretjes, underscores, headings of backticks voor opmaak. "
                + "Formuleer antwoorden in duidelijke, natuurlijke Nederlandse zinnen. Gebruik alleen een opsomming als de gebruiker daar expliciet om vraagt of als dat echt veel duidelijker is.";

        if (imageDataUrl != null
                && !imageDataUrl.trim().isEmpty()) {
            instructions +=
                    " Je ontvangt bij deze vraag één screenshot van het scherm dat zichtbaar was toen MAATJE werd geopend. "
                    + "Gebruik daadwerkelijk de zichtbare inhoud van die screenshot om de vraag te beantwoorden. "
                    + "Beschrijf wat je ziet bij voorkeur als een paar korte, natuurlijke lopende zinnen in plaats van een lijst. "
                    + "Noem onzekerheid als tekst of details niet goed leesbaar zijn en verzin geen elementen die niet zichtbaar zijn.";
        }

        if (webSearchEnabled) {
            instructions +=
                    " Je hebt web search beschikbaar. "
                    + "Gebruik dit wanneer actuele, veranderlijke of externe informatie nodig of duidelijk nuttig is, "
                    + "en wanneer de gebruiker expliciet vraagt iets op internet op te zoeken. "
                    + "Gebruik web search niet onnodig voor stabiele algemene kennis.";
        } else {
            instructions +=
                    " Web search staat lokaal uit. "
                    + "Beweer niet dat je iets live op internet hebt opgezocht.";
        }

        if (personalityPrompt != null
                && !personalityPrompt.trim().isEmpty()) {
            instructions += "\n\n" + personalityPrompt.trim();
        }

        if (profileMemory != null
                && !profileMemory.trim().isEmpty()) {
            instructions +=
                    "\n\nLangetermijngeheugen over de gebruiker. "
                    + "Gebruik dit alleen als het relevant is en doe geen aannames buiten deze notities:\n"
                    + profileMemory.trim();
        }

        body.put("instructions", instructions);

        if (streamListener != null) {
            body.put("stream", true);
            conn.setRequestProperty(
                    "Accept",
                    "text/event-stream"
            );
        }

        writeJson(conn, body);

        int status = conn.getResponseCode();

        if (status < 200 || status >= 300) {
            String raw = readAll(
                    conn.getErrorStream()
            );
            conn.disconnect();
            throw new Exception(
                    extractError(raw, status)
            );
        }

        if (streamListener != null) {
            return readStreamingReply(
                    conn,
                    streamListener
            );
        }

        String raw = readAll(
                conn.getInputStream()
        );
        conn.disconnect();

        JSONObject root = new JSONObject(raw);
        String id = root.optString("id", "");
        String text = extractOutputText(root);
        boolean webUsed = containsWebSearchCall(root);
        List<Source> sources = extractWebSources(root);

        JSONObject usage = root.optJSONObject("usage");
        long inputTokens = usage == null
                ? 0L : usage.optLong("input_tokens", 0L);
        long outputTokens = usage == null
                ? 0L : usage.optLong("output_tokens", 0L);
        long totalTokens = usage == null
                ? inputTokens + outputTokens
                : usage.optLong("total_tokens", inputTokens + outputTokens);

        long cachedTokens = 0L;
        if (usage != null) {
            JSONObject details =
                    usage.optJSONObject("input_tokens_details");
            if (details != null) {
                cachedTokens =
                        details.optLong("cached_tokens", 0L);
            }
        }

        if (!sources.isEmpty()) {
            webUsed = true;
        }

        if (text.isEmpty()) {
            text = "Ik kreeg een leeg antwoord terug van de API.";
        }

        return new Reply(
                id,
                text,
                webUsed,
                sources,
                inputTokens,
                outputTokens,
                totalTokens,
                cachedTokens
        );
    }

    private static Reply readStreamingReply(
            HttpURLConnection conn,
            StreamListener listener
    ) throws Exception {
        JSONObject completedResponse = null;
        StringBuilder generated =
                new StringBuilder();

        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(
                                     conn.getInputStream(),
                                     StandardCharsets.UTF_8
                             )
                     )) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }

                String payload =
                        line.substring(5).trim();

                if (payload.isEmpty()) {
                    continue;
                }

                if ("[DONE]".equals(payload)) {
                    break;
                }

                JSONObject event =
                        new JSONObject(payload);

                String type =
                        event.optString("type", "");

                if ("response.output_text.delta"
                        .equals(type)) {
                    String delta =
                            event.optString(
                                    "delta",
                                    ""
                            );

                    if (!delta.isEmpty()) {
                        generated.append(delta);
                        listener.onText(
                                generated.toString()
                        );
                    }
                } else if ("response.completed"
                        .equals(type)) {
                    completedResponse =
                            event.optJSONObject(
                                    "response"
                            );
                } else if ("response.failed"
                        .equals(type)
                        || "error".equals(type)) {
                    JSONObject error =
                            event.optJSONObject(
                                    "error"
                            );

                    throw new Exception(
                            error == null
                                    ? "Streaming response mislukt."
                                    : error.optString(
                                            "message",
                                            "Streaming response mislukt."
                                    )
                    );
                }
            }
        } finally {
            conn.disconnect();
        }

        if (completedResponse == null) {
            String text =
                    generated.toString().trim();

            if (text.isEmpty()) {
                text = "Ik kreeg een leeg antwoord terug van de API.";
            }

            return new Reply(
                    "",
                    text,
                    false,
                    new ArrayList<>(),
                    0L,
                    0L,
                    0L,
                    0L
            );
        }

        String id =
                completedResponse.optString(
                        "id",
                        ""
                );
        String text =
                extractOutputText(
                        completedResponse
                );
        boolean webUsed =
                containsWebSearchCall(
                        completedResponse
                );
        List<Source> sources =
                extractWebSources(
                        completedResponse
                );

        JSONObject usage =
                completedResponse
                        .optJSONObject("usage");

        long inputTokens = usage == null
                ? 0L
                : usage.optLong(
                        "input_tokens",
                        0L
                );
        long outputTokens = usage == null
                ? 0L
                : usage.optLong(
                        "output_tokens",
                        0L
                );
        long totalTokens = usage == null
                ? inputTokens + outputTokens
                : usage.optLong(
                        "total_tokens",
                        inputTokens + outputTokens
                );

        long cachedTokens = 0L;
        if (usage != null) {
            JSONObject details =
                    usage.optJSONObject(
                            "input_tokens_details"
                    );

            if (details != null) {
                cachedTokens =
                        details.optLong(
                                "cached_tokens",
                                0L
                        );
            }
        }

        if (!sources.isEmpty()) {
            webUsed = true;
        }

        if (text.isEmpty()) {
            text = generated.toString().trim();
        }

        if (text.isEmpty()) {
            text = "Ik kreeg een leeg antwoord terug van de API.";
        }

        return new Reply(
                id,
                text,
                webUsed,
                sources,
                inputTokens,
                outputTokens,
                totalTokens,
                cachedTokens
        );
    }

    static String searchInternet(
            String apiKey,
            String query
    ) throws Exception {
        String clean =
                query == null
                        ? ""
                        : query.trim();

        if (clean.isEmpty()) {
            throw new Exception(
                    "Geen zoekopdracht ontvangen."
            );
        }

        Reply reply = ask(
                apiKey,
                "gpt-5.6-luna",
                "",
                "Zoek dit live op internet. Gebruik actuele informatie en geef een kort, feitelijk antwoord: "
                        + clean,
                "",
                "",
                true
        );

        StringBuilder result =
                new StringBuilder(
                        reply.text
                );

        if (reply.sources != null
                && !reply.sources.isEmpty()) {
            result.append("\n\nBronnen:");

            int max = Math.min(
                    5,
                    reply.sources.size()
            );

            for (int i = 0; i < max; i++) {
                Source source =
                        reply.sources.get(i);

                result.append("\n- ");

                if (source.title != null
                        && !source.title.isEmpty()) {
                    result.append(source.title)
                            .append(": ");
                }

                result.append(source.url);
            }
        }

        return result.toString().trim();
    }

    private static HttpURLConnection open(URL url, String apiKey)
            throws Exception {
        HttpURLConnection conn =
                (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(90000);
        conn.setDoOutput(true);
        conn.setRequestProperty(
                "Authorization",
                "Bearer " + apiKey
        );
        conn.setRequestProperty(
                "Content-Type",
                "application/json"
        );

        return conn;
    }

    private static void writeJson(
            HttpURLConnection conn,
            JSONObject body
    ) throws Exception {
        try (OutputStream os = conn.getOutputStream()) {
            os.write(
                    body.toString()
                            .getBytes(StandardCharsets.UTF_8)
            );
        }
    }

    private static boolean containsWebSearchCall(
            JSONObject root
    ) {
        JSONArray output = root.optJSONArray("output");
        if (output == null) return false;

        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item != null
                    && "web_search_call".equals(item.optString("type"))) {
                return true;
            }
        }
        return false;
    }

    private static List<Source> extractWebSources(
            JSONObject root
    ) {
        ArrayList<Source> sources = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JSONArray output = root.optJSONArray("output");
        if (output == null) return sources;

        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) continue;

            JSONArray content = item.optJSONArray("content");
            if (content != null) {
                for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.optJSONObject(j);
                    if (part == null) continue;

                    JSONArray annotations = part.optJSONArray("annotations");
                    if (annotations == null) continue;

                    for (int k = 0; k < annotations.length(); k++) {
                        JSONObject annotation = annotations.optJSONObject(k);
                        if (annotation == null
                                || !"url_citation".equals(annotation.optString("type"))) {
                            continue;
                        }

                        addSource(
                                sources,
                                seen,
                                annotation.optString("title", ""),
                                annotation.optString("url", "")
                        );
                    }
                }
            }

            if ("web_search_call".equals(item.optString("type"))) {
                JSONObject action = item.optJSONObject("action");
                if (action != null) {
                    JSONArray actionSources = action.optJSONArray("sources");
                    if (actionSources != null) {
                        for (int j = 0; j < actionSources.length(); j++) {
                            JSONObject source = actionSources.optJSONObject(j);
                            if (source != null) {
                                addSource(
                                        sources,
                                        seen,
                                        "",
                                        source.optString("url", "")
                                );
                            }
                        }
                    }
                }
            }
        }
        return sources;
    }

    private static void addSource(
            List<Source> sources,
            Set<String> seen,
            String title,
            String url
    ) {
        if (url == null) return;

        String cleanUrl = url.trim();
        if (cleanUrl.isEmpty() || seen.contains(cleanUrl)) return;

        seen.add(cleanUrl);
        sources.add(
                new Source(
                        title == null ? "" : title.trim(),
                        cleanUrl
                )
        );
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

                if ("output_text".equals(
                        part.optString("type")
                )) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(
                            part.optString("text", "")
                    );
                }
            }
        }

        return sb.toString().trim();
    }

    private static String extractError(
            String raw,
            int status
    ) {
        try {
            JSONObject root = new JSONObject(raw);
            JSONObject err = root.optJSONObject("error");

            if (err != null) {
                return "API "
                        + status
                        + ": "
                        + err.optString("message", raw);
            }
        } catch (Exception ignored) {}

        return "API-fout "
                + status
                + (raw == null || raw.isEmpty()
                ? ""
                : ": " + raw);
    }

    private static String readAll(InputStream in)
            throws Exception {
        if (in == null) return "";

        StringBuilder sb = new StringBuilder();

        try (BufferedReader br =
                     new BufferedReader(
                             new InputStreamReader(
                                     in,
                                     StandardCharsets.UTF_8
                             )
                     )) {
            String line;

            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }

        return sb.toString();
    }
}
