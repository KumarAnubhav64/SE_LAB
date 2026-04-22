package editor;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParserModule {

    private static final Pattern TAG_PATTERN = Pattern.compile("<(\\/?)([a-zA-Z][a-zA-Z0-9-]*)([^>]*)>");

    private static final Set<String> VOID_TAGS = new HashSet<>(Arrays.asList(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"
    ));

    public ValidationResult validateHtml(String html) {
        if (html == null || html.isBlank()) {
            return ValidationResult.valid("Document is empty.");
        }

        Deque<String> stack = new ArrayDeque<>();
        Matcher matcher = TAG_PATTERN.matcher(html);

        while (matcher.find()) {
            String slash = matcher.group(1);
            String tag = matcher.group(2).toLowerCase();
            String attrs = matcher.group(3) == null ? "" : matcher.group(3);

            if (VOID_TAGS.contains(tag) || attrs.trim().endsWith("/")) {
                continue;
            }

            if (slash.isEmpty()) {
                stack.push(tag);
                continue;
            }

            if (stack.isEmpty()) {
                return ValidationResult.invalid("Unexpected closing tag </" + tag + ">.");
            }

            String openTag = stack.pop();
            if (!openTag.equals(tag)) {
                return ValidationResult.invalid("Mismatched tags: expected </" + openTag + "> but found </" + tag + ">.");
            }
        }

        if (!stack.isEmpty()) {
            return ValidationResult.invalid("Missing closing tag for <" + stack.peek() + ">.");
        }

        return ValidationResult.valid("HTML appears valid.");
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult valid(String message) {
            return new ValidationResult(true, message);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }
}
