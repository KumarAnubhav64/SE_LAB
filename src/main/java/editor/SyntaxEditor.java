package editor;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A JavaFX WebView wrapping CodeMirror 5 to provide full HTML/CSS/JS
 * syntax highlighting as a drop-in replacement for the plain TextArea editor.
 *
 * <p>Key design rules:
 * <ul>
 *   <li>All JavaScript calls are guarded: they only execute after the page is SUCCEEDED.</li>
 *   <li>A Java bridge object is injected into the JS window so CodeMirror can fire
 *       callbacks back to Java (onChange, onCursorMove).</li>
 *   <li>The editor HTML + all CodeMirror assets live in
 *       {@code src/main/resources/} and are loaded as classpath resources.</li>
 * </ul>
 */
public class SyntaxEditor {

    // ── Pending-content buffer: text set before page was ready ──────────────
    private volatile String pendingContent = null;
    private final AtomicBoolean pageReady   = new AtomicBoolean(false);

    private final WebView           webView;
    private final WebEngine         engine;
    private final StringProperty    textProperty = new SimpleStringProperty("");

    // ── Callbacks ───────────────────────────────────────────────────────────
    private Runnable         onChangeCallback  = null;
    private Consumer<int[]>  onCursorCallback  = null; // [line, col]

    // ── Word-wrap state ─────────────────────────────────────────────────────
    private boolean wrapText = false;

    // ────────────────────────────────────────────────────────────────────────

    public SyntaxEditor() {
        webView = new WebView();
        engine  = webView.getEngine();

        // Disable right-click context menu
        webView.setContextMenuEnabled(false);

        // Listen for page load
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                injectBridge();
                pageReady.set(true);
                // If text was set before the page was ready, apply it now
                String pending = pendingContent;
                if (pending != null) {
                    pendingContent = null;
                    Platform.runLater(() -> execSetContent(pending, true));
                }
            }
        });

        loadPage();
    }

    // ── Page loading ─────────────────────────────────────────────────────────

    private void loadPage() {
        URL editorHtml   = getClass().getResource("/editor.html");
        if (editorHtml == null) {
            engine.loadContent("<h3 style='color:red'>ERROR: /editor.html not found in classpath</h3>");
            return;
        }

        // Build the HTML with all resource paths resolved as file:// URIs
        try {
            String html = new String(getClass().getResourceAsStream("/editor.html").readAllBytes());

            html = html.replace("CODEMIRROR_JS_PLACEHOLDER",  resourceUrl("codemirror/codemirror.min.js"))
                       .replace("XML_JS_PLACEHOLDER",          resourceUrl("codemirror/xml.min.js"))
                       .replace("JS_JS_PLACEHOLDER",           resourceUrl("codemirror/javascript.min.js"))
                       .replace("CSS_JS_PLACEHOLDER",          resourceUrl("codemirror/css.min.js"))
                       .replace("HTMLMIXED_JS_PLACEHOLDER",    resourceUrl("codemirror/htmlmixed.min.js"))
                       .replace("MATCHBRACKETS_JS_PLACEHOLDER",resourceUrl("codemirror/matchbrackets.min.js"))
                       .replace("CLOSETAG_JS_PLACEHOLDER",     resourceUrl("codemirror/closetag.min.js"))
                       .replace("ACTIVELINE_JS_PLACEHOLDER",   resourceUrl("codemirror/active-line.min.js"))
                       .replace("CODEMIRROR_CSS_PLACEHOLDER",  resourceUrl("codemirror/codemirror.min.css"))
                       .replace("DRACULA_CSS_PLACEHOLDER",     resourceUrl("codemirror/dracula.min.css"));

            // loadContent with a base URI so relative paths would work (they're already absolute above)
            engine.loadContent(html, "text/html");

        } catch (Exception e) {
            engine.loadContent("<h3 style='color:red'>ERROR loading editor: " + e.getMessage() + "</h3>");
        }
    }

    private String resourceUrl(String path) {
        URL url = getClass().getResource("/" + path);
        return url != null ? url.toExternalForm() : "";
    }

    // ── Bridge injection ─────────────────────────────────────────────────────

    // Strong reference to prevent Javascript from losing the bridge during Java GC
    private final JavaBridge bridge = new JavaBridge();

    /**
     * Inject a Java object as {@code window.javaBridge} so the JS side
     * can call back into Java.  Must be called on the FX thread.
     */
    private void injectBridge() {
        JSObject win = (JSObject) engine.executeScript("window");
        win.setMember("javaBridge", bridge);
    }

    /** Called by CodeMirror JS callbacks. Must be public. */
    public class JavaBridge {
        /** Fired by CodeMirror on every change event. */
        public void onContentChanged() {
            Platform.runLater(() -> {
                String text = (String) engine.executeScript("getContent()");
                textProperty.set(text);
                if (onChangeCallback != null) onChangeCallback.run();
            });
        }

        /** Fired by CodeMirror cursor-activity event. */
        public void onCursorMoved(int line, int col) {
            if (onCursorCallback != null) {
                onCursorCallback.accept(new int[]{line, col});
            }
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public WebView getView() { return webView; }

    public String getText() {
        if (!pageReady.get()) return pendingContent != null ? pendingContent : "";
        try {
            return (String) engine.executeScript("getContent()");
        } catch (Exception e) { return ""; }
    }

    /**
     * Execute arbitrary JavaScript in the CodeMirror page.
     * Used by EditorModule to invoke tag-wrapping, cursor manipulation, etc.
     */
    public void execJS(String script) {
        if (!pageReady.get()) return;
        Platform.runLater(() -> {
            try { engine.executeScript(script); } catch (Exception ignored) {}
        });
    }

    /**
     * Replace the editor content. Clears undo history (use for file open).
     */
    public void setText(String text) {
        if (!pageReady.get()) {
            pendingContent = text == null ? "" : text;
            return;
        }
        Platform.runLater(() -> execSetContent(text, true));
    }

    /**
     * Update the editor content, preserving undo history (use for programmatic inserts).
     */
    public void updateText(String text) {
        if (!pageReady.get()) {
            pendingContent = text == null ? "" : text;
            return;
        }
        Platform.runLater(() -> execSetContent(text, false));
    }

    public void appendText(String text) {
        if (text == null || text.isEmpty()) return;
        String current = getText();
        String prefix  = current.isBlank() ? "" : "\n";
        setText(current + prefix + text);
    }

    public boolean isWrapText() { return wrapText; }

    public void setWrapText(boolean wrap) {
        this.wrapText = wrap;
        if (!pageReady.get()) return;
        Platform.runLater(() -> engine.executeScript("setWrap(" + wrap + ")"));
    }

    public void toggleWordWrap() { setWrapText(!wrapText); }

    public void setFontSize(int px) {
        if (!pageReady.get()) return;
        Platform.runLater(() -> engine.executeScript("setFontSize(" + px + ")"));
    }

    /** Observe text changes (fired after every CodeMirror change event). */
    public void setOnChange(Runnable callback) { this.onChangeCallback = callback; }

    /** Observe cursor position changes — int[]{line, col} (1-based). */
    public void setOnCursorMoved(Consumer<int[]> callback) { this.onCursorCallback = callback; }

    /** JavaFX property for binding. */
    public StringProperty textProperty() { return textProperty; }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void execSetContent(String text, boolean clearHistory) {
        if (text == null) text = "";
        // Escape the string for safe JS injection
        String escaped = text
                .replace("\\", "\\\\")
                .replace("`",  "\\`")
                .replace("$",  "\\$");
        String fn = clearHistory ? "setContent" : "updateContent";
        engine.executeScript(fn + "(`" + escaped + "`)");
        textProperty.set(text);
    }
}
