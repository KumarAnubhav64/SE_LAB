package editor;

import java.util.ArrayList;
import java.util.List;

public class EditorModule {

    public enum ParagraphMode {
        LEFT,
        JUSTIFY
    }

    /**
     * Wrap selection (or insert at cursor) with HTML tags.
     * Since SyntaxEditor does not expose a selection API directly, we insert
     * at the end if no selection concept is available; the CodeMirror JS side
     * handles tag-wrapping natively via the editor bridge when called by UI.
     *
     * <p>This method is kept for modules that have a text string already
     * and want to wrap selected text — the caller (UIController) queries
     * the current text, wraps, and pushes back.</p>
     */
    public void applyTag(SyntaxEditor editor, String startTag, String endTag) {
        if (editor == null) return;
        // Delegate to JS — CodeMirror's replaceSelection wraps highlighted text
        // or inserts at cursor if nothing is selected.
        editor.execJS(
            "var sel = editor.getSelection();" +
            "if (sel) {" +
            "  editor.replaceSelection(" + jsStr(startTag) + " + sel + " + jsStr(endTag) + ");" +
            "} else {" +
            "  var cur = editor.getCursor();" +
            "  editor.replaceRange(" + jsStr(startTag + endTag) + ", cur);" +
            "  editor.setCursor({line: cur.line, ch: cur.ch + " + startTag.length() + "});" +
            "}"
        );
    }

    public void applyFontSize(SyntaxEditor editor, int px) {
        int safeSize = Math.max(8, Math.min(72, px));
        applyTag(editor, "<span style=\"font-size:" + safeSize + "px;\">", "</span>");
    }

    public void insertHtmlBoilerplate(SyntaxEditor editor) {
        if (editor == null) return;
        String bp = """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>Document</title>
                </head>
                <body>

                </body>
                </html>
                """;
        editor.setText(bp);
    }

    /** No-op: auto-indent is built into CodeMirror. Kept for API compat. */
    public void installAutoIndent(SyntaxEditor editor) { /* handled by CM */ }

    public String processPlainTextToHtml(String input, int lineWidth, int paragraphIndent, ParagraphMode mode) {
        int width  = Math.max(1, Math.min(132, lineWidth));
        int indent = Math.max(0, Math.min(width - 1, paragraphIndent));

        String source  = input == null ? "" : input.replace("\r\n", "\n").replace('\r', '\n');
        String trimmed = source.trim();
        if (trimmed.isEmpty()) return "<h3>Start typing HTML...</h3>";

        String[] paragraphs = trimmed.split("\\n\\s*\\n+");
        StringBuilder preContent = new StringBuilder();

        for (int i = 0; i < paragraphs.length; i++) {
            List<String> lines = justifyParagraph(paragraphs[i], width, indent, mode == ParagraphMode.JUSTIFY);
            for (String line : lines) preContent.append(escapeHtml(line)).append("\n");
            if (i < paragraphs.length - 1) preContent.append("\n");
        }

        return """
                <html>
                  <body>
                    <pre style='font-family:monospace; white-space:pre; margin:0;'>%s</pre>
                  </body>
                </html>
                """.formatted(preContent);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Escape a Java string into a JS quoted string literal (single-quoted). */
    private String jsStr(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private List<String> justifyParagraph(String paragraph, int width, int indent, boolean justify) {
        String normalized = paragraph.trim().replaceAll("\\s+", " ");
        List<String> output = new ArrayList<>();
        if (normalized.isEmpty()) return output;

        String[] words = normalized.split(" ");
        int index = 0;
        boolean firstLine = true;

        while (index < words.length) {
            int available  = Math.max(1, width - (firstLine ? indent : 0));
            int currentLen = words[index].length();
            int last       = index + 1;

            while (last < words.length) {
                int nextLen = words[last].length();
                if (currentLen + 1 + nextLen > available) break;
                currentLen += 1 + nextLen;
                last++;
            }

            int wordsInLine = last - index;
            boolean isLastLine = last >= words.length;
            String line;

            if (justify && !isLastLine && wordsInLine > 1) {
                int totalWordLen = 0;
                for (int i = index; i < last; i++) totalWordLen += words[i].length();
                int totalSpaces = Math.max(wordsInLine - 1, available - totalWordLen);
                int baseSpaces  = totalSpaces / (wordsInLine - 1);
                int extra       = totalSpaces % (wordsInLine - 1);

                StringBuilder sb = new StringBuilder();
                for (int i = index; i < last; i++) {
                    sb.append(words[i]);
                    if (i < last - 1) {
                        int gap = baseSpaces + (extra > 0 ? 1 : 0);
                        sb.append(" ".repeat(Math.max(1, gap)));
                        if (extra > 0) extra--;
                    }
                }
                line = sb.toString();
            } else {
                line = String.join(" ", java.util.Arrays.copyOfRange(words, index, last));
            }

            if (firstLine && indent > 0) line = " ".repeat(indent) + line;
            output.add(line);
            index = last;
            firstLine = false;
        }

        return output;
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
