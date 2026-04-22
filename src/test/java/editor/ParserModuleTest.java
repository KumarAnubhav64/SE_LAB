package editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ParserModuleTest {

    private final ParserModule parser = new ParserModule();

    @Test
    public void testValidHtml() {
        ParserModule.ValidationResult result = parser.validateHtml("<html><body><h1>Test</h1></body></html>");
        assertTrue(result.isValid(), "HTML should be valid");
    }

    @Test
    public void testEmptyHtml() {
        ParserModule.ValidationResult result = parser.validateHtml("");
        assertTrue(result.isValid());
        assertEquals("Document is empty.", result.getMessage());
    }

    @Test
    public void testVoidTags() {
        ParserModule.ValidationResult result = parser.validateHtml("<div><img src='test.jpg'><br><hr></div>");
        assertTrue(result.isValid(), "Void tags do not need closing");
    }

    @Test
    public void testMissingClosingTag() {
        ParserModule.ValidationResult result = parser.validateHtml("<div><p>Test</div>");
        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("Mismatched tags"));
    }

    @Test
    public void testUnexpectedClosingTag() {
        ParserModule.ValidationResult result = parser.validateHtml("</div>");
        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("Unexpected closing tag"));
    }
}
