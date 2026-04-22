package editor;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;

import java.util.ArrayList;
import java.util.List;

public class EditorModule {

    public enum ParagraphMode {
        LEFT,
        JUSTIFY
    }

    public void applyTag(TextArea editor, String startTag, String endTag) {
        if (editor == null) {
            return;
        }

        String selected = editor.getSelectedText();
        if (selected != null && !selected.isEmpty()) {
            editor.replaceSelection(startTag + selected + endTag);
            return;
        }

        int caret = editor.getCaretPosition();
        editor.insertText(caret, startTag + endTag);
        editor.positionCaret(caret + startTag.length());
    }

    public void applyFontSize(TextArea editor, int px) {
        int safeSize = Math.max(8, Math.min(72, px));
        applyTag(editor, "<span style=\"font-size:" + safeSize + "px;\">", "</span>");
    }

    public void insertHtmlBoilerplate(TextArea editor) {
        if (editor == null) {
            return;
        }

        String boilerplate = """
                <!doctype html>
                <html lang=\"en\">
                <head>
                  <meta charset=\"UTF-8\" />
                  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
                  <title>Document</title>
                </head>
                <body>

                </body>
                </html>
                """;

        editor.setText(boilerplate);
        editor.positionCaret(boilerplate.indexOf("\n\n") + 2);
    }

    public void installAutoIndent(TextArea editor) {
        if (editor == null) {
            return;
        }

        editor.setOnKeyPressed(event -> {
            if (event.getCode() != KeyCode.ENTER) {
                return;
            }

            int caret = editor.getCaretPosition();
            String text = editor.getText();
            int lineStart = text.lastIndexOf("\n", Math.max(0, caret - 1)) + 1;
            String previousLine = text.substring(lineStart, Math.min(caret, text.length()));
            String indent = previousLine.replaceAll("^(\\s*).*$", "$1");

            Platform.runLater(() -> editor.insertText(caret, indent));
        });
    }

    public String processPlainTextToHtml(String input, int lineWidth, int paragraphIndent, ParagraphMode mode) {
        int width = Math.max(1, Math.min(132, lineWidth));
        int indent = Math.max(0, Math.min(width - 1, paragraphIndent));

        String source = input == null ? "" : input.replace("\r\n", "\n").replace('\r', '\n');
        String trimmed = source.trim();
        if (trimmed.isEmpty()) {
            return "<h3>Start typing HTML...</h3>";
        }

        String[] paragraphs = trimmed.split("\\n\\s*\\n+");
        StringBuilder preContent = new StringBuilder();

        for (int i = 0; i < paragraphs.length; i++) {
            List<String> lines = justifyParagraph(paragraphs[i], width, indent, mode == ParagraphMode.JUSTIFY);
            for (String line : lines) {
                preContent.append(escapeHtml(line)).append("\n");
            }
            if (i < paragraphs.length - 1) {
                preContent.append("\n");
            }
        }

        return """
                <html>
                  <body>
                    <pre style='font-family:monospace; white-space:pre; margin:0;'>%s</pre>
                  </body>
                </html>
                """.formatted(preContent);
    }

    private List<String> justifyParagraph(String paragraph, int width, int indent, boolean justify) {
        String normalized = paragraph.trim().replaceAll("\\s+", " ");
        List<String> output = new ArrayList<>();
        if (normalized.isEmpty()) {
            return output;
        }

        String[] words = normalized.split(" ");
        int index = 0;
        boolean firstLine = true;

        while (index < words.length) {
            int available = Math.max(1, width - (firstLine ? indent : 0));
            int currentLen = words[index].length();
            int last = index + 1;

            while (last < words.length) {
                int nextLen = words[last].length();
                if (currentLen + 1 + nextLen > available) {
                    break;
                }
                currentLen += 1 + nextLen;
                last++;
            }

            int wordsInLine = last - index;
            boolean isLastLine = last >= words.length;
            String line;

            if (justify && !isLastLine && wordsInLine > 1) {
                int totalWordLen = 0;
                for (int i = index; i < last; i++) {
                    totalWordLen += words[i].length();
                }
                int totalSpaces = Math.max(wordsInLine - 1, available - totalWordLen);
                int baseSpaces = totalSpaces / (wordsInLine - 1);
                int extra = totalSpaces % (wordsInLine - 1);

                StringBuilder sb = new StringBuilder();
                for (int i = index; i < last; i++) {
                    sb.append(words[i]);
                    if (i < last - 1) {
                        int gap = baseSpaces + (extra > 0 ? 1 : 0);
                        sb.append(" ".repeat(Math.max(1, gap)));
                        if (extra > 0) {
                            extra--;
                        }
                    }
                }
                line = sb.toString();
            } else {
                line = String.join(" ", java.util.Arrays.copyOfRange(words, index, last));
            }

            if (firstLine && indent > 0) {
                line = " ".repeat(indent) + line;
            }

            output.add(line);
            index = last;
            firstLine = false;
        }

        return output;
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
