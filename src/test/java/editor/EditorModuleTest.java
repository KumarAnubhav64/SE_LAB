package editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EditorModuleTest {

    private final EditorModule editorModule = new EditorModule();

    @Test
    public void testProcessPlainTextToHtmlJustify() {
        String input = "This is a short line.\n\nAnd here is a slightly longer line that should be justified according to the character width specified.";
        String result = editorModule.processPlainTextToHtml(input, 40, 2, EditorModule.ParagraphMode.JUSTIFY);
        
        assertTrue(result.contains("<html>"));
        assertTrue(result.contains("This is a short line."));
        // First paragraph should have 2 space indent
        assertTrue(result.contains("  This is"));
        // The second paragraph should have justify spaces
        assertTrue(result.contains("  And"));
    }

    @Test
    public void testProcessPlainTextToHtmlLeftAligned() {
        String input = "This is a short line.\n\nAnd here is a slightly longer line.";
        String result = editorModule.processPlainTextToHtml(input, 40, 2, EditorModule.ParagraphMode.LEFT);
        
        assertTrue(result.contains("<html>"));
        assertTrue(result.contains("This is a short line."));
        assertTrue(result.contains("slightly longer line"));
    }

    @Test
    public void testApplyTagWithNullEditor() {
        // Just ensuring it doesn't crash
        assertDoesNotThrow(() -> {
            editorModule.applyTag(null, "<b>", "</b>");
            editorModule.applyFontSize(null, 14);
            editorModule.insertHtmlBoilerplate(null);
        });
    }
}
