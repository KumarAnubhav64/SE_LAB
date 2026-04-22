package editor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Thin wrapper around the Gemini 2.0 Flash REST API.
 *
 * <p>API key resolution order:
 * <ol>
 *   <li>Environment variable {@code GEMINI_API_KEY}</li>
 *   <li>File {@code ~/.config/htmleditor/gemini.key} (plain text, one line)</li>
 * </ol>
 *
 * <p>All network I/O happens on a background thread via {@link CompletableFuture}.
 */
public class GeminiModule {

    // ── Gemini API endpoint ───────────────────────────────────────────────────
    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" +
            "gemini-2.0-flash:generateContent?key=";

    private static final int TIMEOUT_SECONDS = 60;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the resolved API key, or {@code null} if not configured.
     */
    public String resolveKey() {
        // 1. Environment variable
        String env = System.getenv("GEMINI_API_KEY");
        if (env != null && !env.isBlank()) return env.strip();

        // 2. Key file
        Path keyFile = Path.of(System.getProperty("user.home"), ".config", "htmleditor", "gemini.key");
        if (Files.exists(keyFile)) {
            try {
                String content = Files.readString(keyFile).strip();
                if (!content.isEmpty()) return content;
            } catch (IOException ignored) {}
        }
        return null;
    }

    /** Save key to the config file so it persists across sessions. */
    public void saveKey(String key) throws IOException {
        Path dir = Path.of(System.getProperty("user.home"), ".config", "htmleditor");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("gemini.key"), key.strip());
    }

    /**
     * Fire an async Gemini request.
     *
     * @param systemPrompt Instructions for Gemini (role / style)
     * @param userPrompt   The user's actual question / content
     * @param apiKey       Gemini API key
     * @param onSuccess    Receives the text response on the calling thread pool
     * @param onError      Receives an error message string
     */
    public CompletableFuture<Void> generate(
            String systemPrompt,
            String userPrompt,
            String apiKey,
            Consumer<String> onSuccess,
            Consumer<String> onError) {

        String url  = BASE_URL + apiKey;
        String body = buildBody(systemPrompt, userPrompt);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                        String text = extractText(resp.body());
                        onSuccess.accept(text);
                    } else {
                        onError.accept("HTTP " + resp.statusCode() + ": " + extractErrorMessage(resp.body()));
                    }
                })
                .exceptionally(ex -> {
                    onError.accept("Network error: " + ex.getMessage());
                    return null;
                });
    }

    // ── Canned prompt library ─────────────────────────────────────────────────

    public static final String SYS_HTML_GENERATE =
        "You are an expert HTML/CSS developer. " +
        "Return ONLY valid, well-formed HTML5 code. No markdown fences. No explanation. " +
        "Use semantic tags, BEM-style classes, and inline <style> for any CSS needed.";

    public static final String SYS_HTML_FIX =
        "You are an expert HTML debugger. " +
        "Fix all syntax errors, missing closing tags, and accessibility issues in the HTML provided. " +
        "Return ONLY the corrected HTML. No markdown. No explanation.";

    public static final String SYS_HTML_IMPROVE =
        "You are an expert front-end engineer. " +
        "Improve the given HTML: add semantic structure, ARIA attributes, clean up style, " +
        "add a CSS reset inside a <style> tag, and make the layout responsive. " +
        "Return ONLY html. No markdown fences. No explanation.";

    public static final String SYS_HTML_EXPLAIN =
        "You are an expert web developer and teacher. " +
        "Given the following HTML, explain what it does in clear, concise plain English. " +
        "Use bullet points for structure. Do NOT output any HTML code; output only your explanation.";

    public static final String SYS_SEO =
        "You are an SEO specialist. " +
        "Add or improve meta tags (title, description, og:*, twitter:*), " +
        "canonical links, and semantic heading hierarchy in the provided HTML. " +
        "Return ONLY the full improved HTML. No markdown fences.";

    public static final String SYS_ACCESSIBILITY =
        "You are a web accessibility expert (WCAG 2.1 AA). " +
        "Add alt texts, ARIA roles, labels, skip-link, and fix color-contrast issues in the HTML. " +
        "Return ONLY the corrected HTML. No markdown fences.";

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildBody(String systemPrompt, String userPrompt) {
        // Minimal Gemini REST payload
        String safeSys  = jsonEscape(systemPrompt);
        String safeUser = jsonEscape(userPrompt);
        return """
            {
              "system_instruction": {
                "parts": [{"text": "%s"}]
              },
              "contents": [{
                "parts": [{"text": "%s"}]
              }],
              "generationConfig": {
                "temperature": 0.3,
                "maxOutputTokens": 8192
              }
            }
            """.formatted(safeSys, safeUser);
    }

    /**
     * Extract the text field from Gemini's JSON response without pulling in
     * a JSON library.
     */
    private String extractText(String json) {
        // Gemini response: ...candidates[0].content.parts[0].text: "..."
        String marker = "\"text\":";
        int idx = json.indexOf(marker);
        if (idx < 0) return "(no text in response)";

        int start = json.indexOf('"', idx + marker.length());
        if (start < 0) return "(parse error)";

        // Walk forward handling escape sequences
        StringBuilder sb = new StringBuilder();
        int i = start + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case 'n'  -> sb.append('\n');
                    case 't'  -> sb.append('\t');
                    case 'r'  -> sb.append('\r');
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'u'  -> {
                        if (i + 5 < json.length()) {
                            String hex = json.substring(i + 2, i + 6);
                            try { sb.append((char) Integer.parseInt(hex, 16)); } catch (NumberFormatException e) { sb.append('?'); }
                            i += 4;
                        }
                    }
                    default   -> sb.append(next);
                }
                i += 2;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString().strip();
    }

    private String jsonEscape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"",  "\\\"")
                .replace("\n",  "\\n")
                .replace("\r",  "\\r")
                .replace("\t",  "\\t");
    }

    private String extractErrorMessage(String json) {
        if (json == null) return "Unknown error (empty format)";
        String marker = "\"message\":";
        int idx = json.indexOf(marker);
        if (idx < 0) {
            return json.length() > 200 ? json.substring(0, 200) + "..." : json;
        }

        int start = json.indexOf('"', idx + marker.length());
        if (start < 0) return "Unknown error format";
        
        int end = json.indexOf('"', start + 1);
        while (end > 0 && json.charAt(end - 1) == '\\') {
            end = json.indexOf('"', end + 1); // skip escaped quotes
        }
        
        if (end > start) {
            return json.substring(start + 1, end).replace("\\\"", "\"").replace("\\n", "\n");
        }
        return json.length() > 200 ? json.substring(0, 200) + "..." : json;
    }
}
