package service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Universal Multi-LLM AI Service.
 * Manages persistent API keys (OpenAI GPT, Google Gemini, xAI Grok)
 * and dispatches context-aware coding queries over high-performance HTTP/2.
 */
public class AiService {

    /** Keeps requests predictable and prevents an entire huge file from being sent accidentally. */
    private static final int MAX_CONTEXT_CHARS = 24_000;

    private static final String PREF_NODE = "com.auraorbit.ai";
    private static final String KEY_OPENAI = "openai_api_key";
    private static final String KEY_GEMINI = "gemini_api_key";
    private static final String KEY_GROK = "grok_api_key";

    private static final Pattern GEMINI_PATTERN = Pattern.compile("\"text\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern OPENAI_PATTERN = Pattern.compile("\"content\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern OLLAMA_RESPONSE_PATTERN = Pattern.compile("\"response\"\\s*:\\s*\"(.*?)\"(?:,|\\})", Pattern.DOTALL);

    private final Preferences prefs;
    private final HttpClient httpClient;

    public AiService() {
        this.prefs = Preferences.userRoot().node(PREF_NODE);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String getOpenAiKey() {
        return prefs.get(KEY_OPENAI, "");
    }

    public void setOpenAiKey(String key) {
        prefs.put(KEY_OPENAI, key != null ? key.trim() : "");
    }

    public String getGeminiKey() {
        return prefs.get(KEY_GEMINI, "");
    }

    public void setGeminiKey(String key) {
        prefs.put(KEY_GEMINI, key != null ? key.trim() : "");
    }

    public String getGrokKey() {
        return prefs.get(KEY_GROK, "");
    }

    public void setGrokKey(String key) {
        prefs.put(KEY_GROK, key != null ? key.trim() : "");
    }

    public boolean hasAnyKey() {
        return !getOpenAiKey().isEmpty() || !getGeminiKey().isEmpty() || !getGrokKey().isEmpty();
    }

    public boolean hasKeyForModel(String model) {
        String m = model.toLowerCase();
        if (m.contains("gpt") || m.contains("openai")) return !getOpenAiKey().isEmpty();
        if (m.contains("gemini")) return !getGeminiKey().isEmpty();
        if (m.contains("grok") || m.contains("xai")) return !getGrokKey().isEmpty();
        if (m.contains("local") || m.contains("deepseek")) return true;
        return false;
    }

    /**
     * Sends context-aware prompt to selected model and returns markdown response.
     */
    public String generateResponse(String model, String userPrompt, String codeContext, String fileName) throws Exception {
        codeContext = limitContext(codeContext);
        String m = model.toLowerCase();

        // 1. Google Gemini. Gemini 2.0 Flash was shut down in 2026.
        if (m.contains("gemini")) {
            String key = getGeminiKey();
            if (key.isEmpty()) {
                throw new IllegalStateException("Google Gemini API Key is not configured. Click the Key icon in the AI panel to set it.");
            }
            return queryGemini("gemini-3.5-flash", key, userPrompt, codeContext, fileName);
        }

        // 2. OpenAI GPT (latest models)
        if (m.contains("gpt") || m.contains("openai")) {
            String key = getOpenAiKey();
            if (key.isEmpty()) {
                throw new IllegalStateException("OpenAI API Key is not configured. Click the Key icon in the AI panel to set it.");
            }
            String gptModel = m.contains("mini") ? "gpt-4o-mini" : "gpt-4o-2024-11-20";
            return queryOpenAi(gptModel, key, userPrompt, codeContext, fileName);
        }

        // 3. xAI Grok (latest models)
        if (m.contains("grok") || m.contains("xai")) {
            String key = getGrokKey();
            if (key.isEmpty()) {
                throw new IllegalStateException("xAI Grok API Key is not configured. Click the Key icon in the AI panel to set it.");
            }
            return queryGrok("grok-3", key, userPrompt, codeContext, fileName);
        }

        // 4. Local Ollama / DeepSeek (latest models)
        if (m.contains("local") || m.contains("deepseek")) {
            return queryLocalOllama("deepseek-r1:14b", userPrompt, codeContext, fileName);
        }

        throw new IllegalArgumentException("Unsupported AI model: " + model);
    }

    private String limitContext(String codeContext) {
        if (codeContext == null || codeContext.length() <= MAX_CONTEXT_CHARS) return codeContext;
        int firstPartLength = MAX_CONTEXT_CHARS * 3 / 4;
        int lastPartLength = MAX_CONTEXT_CHARS - firstPartLength;
        return codeContext.substring(0, firstPartLength)
                + "\n\n/* AuraOrbit: middle of the file omitted to stay within the AI context limit. */\n\n"
                + codeContext.substring(codeContext.length() - lastPartLength);
    }

    private String queryGemini(String model, String apiKey, String userPrompt, String codeContext, String fileName) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        String systemInstruction = "You are AuraOrbit Copilot, an expert AI programming assistant in a modern IDE. "
                + "Provide concise, accurate, production-ready code with clean explanations. "
                + "When providing code snippets or replacements, enclose them in markdown code fences with the language identifier (e.g. ```java).";

        String contextHeader = "";
        if (codeContext != null && !codeContext.isBlank()) {
            contextHeader = "Context from file `" + (fileName != null ? fileName : "editor") + "`:\n```\n" + codeContext + "\n```\n\n";
        }

        String fullPrompt = contextHeader + userPrompt;

        String payload = "{"
                + "\"contents\": [{\"parts\": [{\"text\": \"" + escapeJson(systemInstruction + "\n\nUser request: " + fullPrompt) + "\"}]}]"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return parseGeminiResponse(response.body());
        } else {
            throw new RuntimeException("Gemini API error (HTTP " + response.statusCode() + "): " + response.body());
        }
    }

    private String queryOpenAi(String model, String apiKey, String userPrompt, String codeContext, String fileName) throws Exception {
        String url = "https://api.openai.com/v1/chat/completions";

        String systemMsg = "You are AuraOrbit Copilot, an expert programming assistant in a modern IDE. "
                + "Provide direct, elegant, production-grade solutions. Always format code using markdown code fences.";

        String contextHeader = "";
        if (codeContext != null && !codeContext.isBlank()) {
            contextHeader = "Active file (" + (fileName != null ? fileName : "code") + "):\n```\n" + codeContext + "\n```\n\n";
        }

        String payload = "{"
                + "\"model\": \"" + model + "\","
                + "\"messages\": ["
                + "  {\"role\": \"system\", \"content\": \"" + escapeJson(systemMsg) + "\"},"
                + "  {\"role\": \"user\", \"content\": \"" + escapeJson(contextHeader + userPrompt) + "\"}"
                + "]"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return parseOpenAiResponse(response.body());
        } else {
            throw new RuntimeException("OpenAI API error (HTTP " + response.statusCode() + "): " + response.body());
        }
    }

    private String queryGrok(String model, String apiKey, String userPrompt, String codeContext, String fileName) throws Exception {
        String url = "https://api.x.ai/v1/chat/completions";

        String systemMsg = "You are AuraOrbit Copilot powered by xAI Grok. "
                + "Provide high-performance, clear, production-grade code. Always format code using markdown code blocks.";

        String contextHeader = "";
        if (codeContext != null && !codeContext.isBlank()) {
            contextHeader = "Active file (" + (fileName != null ? fileName : "code") + "):\n```\n" + codeContext + "\n```\n\n";
        }

        String payload = "{"
                + "\"model\": \"" + model + "\","
                + "\"messages\": ["
                + "  {\"role\": \"system\", \"content\": \"" + escapeJson(systemMsg) + "\"},"
                + "  {\"role\": \"user\", \"content\": \"" + escapeJson(contextHeader + userPrompt) + "\"}"
                + "]"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return parseOpenAiResponse(response.body());
        } else {
            throw new RuntimeException("xAI Grok API error (HTTP " + response.statusCode() + "): " + response.body());
        }
    }

    private String queryLocalOllama(String model, String userPrompt, String codeContext, String fileName) throws Exception {
        String url = "http://localhost:11434/api/generate";

        String prompt = "Active file: " + (fileName != null ? fileName : "code") + "\n"
                + (codeContext != null ? "```\n" + codeContext + "\n```\n" : "")
                + "\nUser request: " + userPrompt;

        String payload = "{\"model\": \"" + model + "\", \"prompt\": \"" + escapeJson(prompt) + "\", \"stream\": false}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Matcher m = OLLAMA_RESPONSE_PATTERN.matcher(response.body());
            if (m.find()) {
                return unescapeJson(m.group(1));
            }
            return response.body();
        } else {
            throw new RuntimeException("Ollama returned status " + response.statusCode() + ". Make sure Ollama is running on localhost:11434.");
        }
    }

    private String parseGeminiResponse(String json) {
        Matcher matcher = GEMINI_PATTERN.matcher(json);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            sb.append(unescapeJson(matcher.group(1)));
        }
        return sb.length() > 0 ? sb.toString() : "No response generated.";
    }

    private String parseOpenAiResponse(String json) {
        Matcher matcher = OPENAI_PATTERN.matcher(json);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return "No response received.";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
