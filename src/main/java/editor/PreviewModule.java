package editor;

import javafx.scene.web.WebView;

public class PreviewModule {

    public void render(WebView preview, String html, ParserModule.ValidationResult validation) {
        if (preview == null) {
            return;
        }

        String safeHtml = html == null ? "" : html;
        if (validation == null || validation.isValid()) {
            String content = safeHtml.isBlank() ? "<h3>Start typing HTML...</h3>" : safeHtml;
            preview.getEngine().loadContent(content, "text/html");
            return;
        }

        String warning = """
                <div style='font-family:sans-serif;padding:8px;background:#fff3cd;color:#664d03;border:1px solid #ffecb5;'>
                    Validation warning: %s
                </div>
                %s
                """.formatted(escapeHtml(validation.getMessage()), safeHtml);

        preview.getEngine().loadContent(warning, "text/html");
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
