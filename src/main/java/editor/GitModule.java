package editor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GitModule wraps the system git CLI via ProcessBuilder.
 * All methods accept a working directory and return stdout/stderr as a String.
 */
public class GitModule {

    public String init(Path dir) {
        return run(dir, "git", "init");
    }

    public String status(Path dir) {
        return run(dir, "git", "status");
    }

    public String log(Path dir) {
        return run(dir, "git", "log", "--oneline", "--graph", "--decorate", "-20");
    }

    public String diff(Path dir) {
        return run(dir, "git", "diff");
    }

    public String diffStaged(Path dir) {
        return run(dir, "git", "diff", "--staged");
    }

    public String addAll(Path dir) {
        return run(dir, "git", "add", "-A");
    }

    public String commit(Path dir, String message) {
        return run(dir, "git", "commit", "-m", message);
    }

    /** Stage all changes and commit in one step. Returns combined output. */
    public String stageAndCommit(Path dir, String message) {
        String addOut    = addAll(dir);
        String commitOut = commit(dir, message);
        return "--- git add -A ---\n" + addOut + "\n\n--- git commit ---\n" + commitOut;
    }

    public String branch(Path dir) {
        return run(dir, "git", "branch", "-v");
    }

    public String stashList(Path dir) {
        return run(dir, "git", "stash", "list");
    }

    // -------------------------------------------------------------------------

    private String run(Path dir, String... command) {
        if (dir == null || !Files.isDirectory(dir)) {
            return "Error: '" + dir + "' is not a valid directory.";
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);   // merge stderr → stdout

            Process process = pb.start();
            StringBuilder sb = new StringBuilder();

            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }

            process.waitFor();
            String out = sb.toString().trim();
            return out.isEmpty() ? "(no output)" : out;

        } catch (IOException e) {
            return "IO Error: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Interrupted: " + e.getMessage();
        }
    }
}
