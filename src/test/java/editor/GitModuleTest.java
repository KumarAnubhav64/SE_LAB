package editor;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class GitModuleTest {

    private final GitModule gitModule = new GitModule();

    @Test
    public void testGitInitAndStatus() throws IOException {
        Path tempDir = Files.createTempDirectory("git_test");
        try {
            String initOutput = gitModule.init(tempDir);
            assertTrue(initOutput.contains("Initialized empty Git repository") || initOutput.contains("Reinitialized existing Git repository"), "Output: " + initOutput);

            String statusOutput = gitModule.status(tempDir);
            assertTrue(statusOutput.contains("On branch") || statusOutput.contains("No commits yet"), "Output: " + statusOutput);
        } finally {
            // Clean up basic temp dir recursively...
            deleteDirectory(tempDir.toFile());
        }
    }

    private void deleteDirectory(java.io.File directoryToBeDeleted) {
        java.io.File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (java.io.File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }
}
