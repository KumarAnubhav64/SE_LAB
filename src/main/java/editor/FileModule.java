package editor;

import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileModule {

    public void openFile(Stage stage, TabManager tabManager, Label status) {
        File file = createOpenChooser().showOpenDialog(stage);
        if (file == null) {
            setStatus(status, "Open cancelled");
            return;
        }

        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            tabManager.createNewTab(content);
            tabManager.setCurrentFilePath(file.toPath());
            tabManager.markCurrentClean();          // freshly loaded → not dirty
            setStatus(status, "Opened: " + file.getName());
        } catch (IOException ex) {
            setStatus(status, "Error opening file: " + file.getName());
        }
    }

    /** Open a known Path directly (used by the file tree sidebar). */
    public void openPathInTab(Path path, TabManager tabManager, Label status) {
        if (path == null) return;
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            tabManager.createNewTab(content);
            tabManager.setCurrentFilePath(path);
            tabManager.markCurrentClean();
            setStatus(status, "Opened: " + path.getFileName());
        } catch (IOException ex) {
            setStatus(status, "Error opening: " + path.getFileName());
        }
    }

    /** Save to existing path, or prompt if none. */
    public void saveFile(Stage stage, TabManager tabManager, Label status) {
        if (!tabManager.hasTabs()) {
            setStatus(status, "No tab to save");
            return;
        }

        Path path = tabManager.getCurrentFilePath();
        File file = path == null ? createSaveChooser().showSaveDialog(stage) : path.toFile();
        if (file == null) {
            setStatus(status, "Save cancelled");
            return;
        }

        doWrite(file, tabManager, status);
    }

    /** Always prompt for location (Save As). */
    public void saveAsFile(Stage stage, TabManager tabManager, Label status) {
        if (!tabManager.hasTabs()) {
            setStatus(status, "No tab to save");
            return;
        }

        File file = createSaveChooser().showSaveDialog(stage);
        if (file == null) {
            setStatus(status, "Save As cancelled");
            return;
        }

        doWrite(file, tabManager, status);
    }

    public void importFile(Stage stage, TabManager tabManager, Label status) {
        File file = createTextImportChooser().showOpenDialog(stage);
        if (file == null) {
            setStatus(status, "Import cancelled");
            return;
        }

        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            tabManager.appendToCurrentText(content);
            setStatus(status, "Imported: " + file.getName());
        } catch (IOException ex) {
            setStatus(status, "Import failed: " + file.getName());
        }
    }

    public void importImage(Stage stage, TabManager tabManager, Label status) {
        File file = createImageImportChooser().showOpenDialog(stage);
        if (file == null) {
            setStatus(status, "Image import cancelled");
            return;
        }

        String alt      = escapeHtml(file.getName());
        String uri      = file.toURI().toString();
        String imageTag = "<img src=\"" + uri + "\" alt=\"" + alt + "\" />";

        tabManager.appendToCurrentText(imageTag);
        setStatus(status, "Image imported: " + file.getName());
    }

    // -------------------------------------------------------------------------

    private void doWrite(File file, TabManager tabManager, Label status) {
        try {
            Files.writeString(file.toPath(), tabManager.getCurrentHtml(), StandardCharsets.UTF_8);
            tabManager.setCurrentFilePath(file.toPath());
            tabManager.markCurrentClean();          // saved → clean
            setStatus(status, "Saved: " + file.getName());
        } catch (IOException ex) {
            setStatus(status, "Error saving file: " + file.getName());
        }
    }

    private FileChooser createOpenChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Document");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("HTML Files", "*.html", "*.htm"),
                new FileChooser.ExtensionFilter("Text Files", "*.txt", "*.asc"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        return chooser;
    }

    private FileChooser createTextImportChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Text Document");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("HTML Files", "*.html", "*.htm"),
                new FileChooser.ExtensionFilter("Text Files", "*.txt", "*.asc"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        return chooser;
    }

    private FileChooser createImageImportChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Image");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files",
                        "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp", "*.bmp"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        return chooser;
    }

    private FileChooser createSaveChooser() {
        FileChooser chooser = createOpenChooser();
        chooser.setTitle("Save Document");
        return chooser;
    }

    private void setStatus(Label status, String message) {
        if (status != null) status.setText(message);
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
