package info.jtrac.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight HTTP client for interacting with Ollama API (/api/chat).
 * Supports local Ollama instances and official cloud endpoints with Bearer token authentication.
 */
public class OllamaClient {

    private static final Logger logger = LoggerFactory.getLogger(OllamaClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final int timeoutSeconds;

    public OllamaClient(String baseUrl, String model, String apiKey, int timeoutSeconds) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            this.baseUrl = "http://localhost:11434";
        } else {
            String trimmed = baseUrl.trim();
            this.baseUrl = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        }
        this.model = (model == null || model.trim().isEmpty()) ? "llama3.2" : model.trim();
        this.apiKey = (apiKey == null || apiKey.trim().isEmpty()) ? null : apiKey.trim();
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 60;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public String buildChatPayload(String systemPrompt, String userPrompt) {
        return buildChatPayloadWithImage(systemPrompt, userPrompt, null, null);
    }

    public String buildChatPayloadWithImage(String systemPrompt, String userPrompt, byte[] imageBytes, String customModel) {
        ObjectNode root = objectMapper.createObjectNode();
        String selectedModel = (customModel != null && !customModel.trim().isEmpty()) ? customModel.trim() : model;
        root.put("model", selectedModel);
        root.put("stream", false);

        ArrayNode messages = root.putArray("messages");
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt.trim());
        }

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", (userPrompt != null && !userPrompt.trim().isEmpty()) ? userPrompt : "Please transcribe all readable text from this image.");
        if (imageBytes != null && imageBytes.length > 0) {
            ArrayNode images = userMsg.putArray("images");
            images.add(java.util.Base64.getEncoder().encodeToString(imageBytes));
        }

        return root.toString();
    }

    public String parseResponse(String jsonResponse) throws IOException {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            throw new IOException("Empty response from Ollama");
        }
        JsonNode root = objectMapper.readTree(jsonResponse);
        if (root.has("error")) {
            throw new IOException("Ollama error: " + root.get("error").asText());
        }
        JsonNode messageNode = root.path("message");
        if (messageNode.has("content")) {
            return messageNode.get("content").asText();
        }
        if (root.has("response")) {
            return root.get("response").asText();
        }
        throw new IOException("Unable to parse message content from Ollama response: " + jsonResponse);
    }

    public String chat(String systemPrompt, String userPrompt) throws IOException {
        String payload = buildChatPayload(systemPrompt, userPrompt);
        return postPayload(payload);
    }

    public String chatWithImage(String systemPrompt, String userPrompt, byte[] imageBytes, String customModel) throws IOException {
        String payload = buildChatPayloadWithImage(systemPrompt, userPrompt, imageBytes, customModel);
        return postPayload(payload);
    }

    private String postPayload(String payload) throws IOException {
        String endpoint = baseUrl + "/api/chat";
        logger.debug("Connecting to Ollama at {}", endpoint);

        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(timeoutSeconds * 1000);
            conn.setReadTimeout(timeoutSeconds * 1000);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            if (apiKey != null) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }

            byte[] inputBytes = payload.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(inputBytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(inputBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            InputStream is = (responseCode >= 200 && responseCode < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (is == null) {
                throw new IOException("HTTP " + responseCode + " with no response body from " + endpoint);
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }

            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("Ollama returned HTTP " + responseCode + ": " + sb.toString().trim());
            }

            return parseResponse(sb.toString());
        } finally {
            conn.disconnect();
        }
    }
}
