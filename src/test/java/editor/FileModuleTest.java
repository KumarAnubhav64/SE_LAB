package editor;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class FileModuleTest {

    @Test
    public void testFileExistsHandling() throws IOException {
        Path tempFile = Files.createTempFile("test_html_editor", ".txt");
        try {
            assertTrue(Files.exists(tempFile));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
