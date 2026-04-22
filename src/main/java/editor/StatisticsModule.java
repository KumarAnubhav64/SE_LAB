package editor;

public class StatisticsModule {

    public Stats compute(String text) {
        String value = text == null ? "" : text;

        int words = value.trim().isEmpty() ? 0 : value.trim().split("\\s+").length;
        int lines = value.isEmpty() ? 0 : value.split("\\R", -1).length;
        int chars = value.length();

        int paragraphs = value.trim().isEmpty() ? 0 : value.trim().split("\\R\\s*\\R+").length;

        int tags = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '<') {
                tags++;
            }
        }

        return new Stats(words, lines, chars, paragraphs, tags);
    }

    public String format(Stats stats) {
        if (stats == null) {
            return "Words: 0 | Lines: 0 | Chars: 0 | Paragraphs: 0 | Tags: 0";
        }

        return "Words: " + stats.words()
                + " | Lines: " + stats.lines()
                + " | Chars: " + stats.chars()
                + " | Paragraphs: " + stats.paragraphs()
                + " | Tags: " + stats.tags();
    }

    public record Stats(int words, int lines, int chars, int paragraphs, int tags) {
    }
}
