package editor;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * VS Code-style sidebar file explorer panel.
 * Shows a TreeView of the open folder; double-click opens files.
 */
public class FileTreePanel {

    private final TreeView<Path>  treeView   = new TreeView<>();
    private final Label           rootLabel  = new Label("NO FOLDER OPEN");
    private       Path            rootPath   = null;
    private       Consumer<Path>  onFileOpen = p -> {};

    // =========================================================================
    // Build
    // =========================================================================

    public VBox build(Stage stage) {
        // ── Header ──────────────────────────────────────────────────────────
        Label explorerTitle = new Label("EXPLORER");
        explorerTitle.getStyleClass().add("sidebar-section-title");
        explorerTitle.setMaxWidth(Double.MAX_VALUE);

        Button openBtn = new Button("⊕  Open Folder…");
        openBtn.setMaxWidth(Double.MAX_VALUE);
        openBtn.getStyleClass().add("sidebar-open-btn");
        openBtn.setOnAction(e -> openFolder(stage));

        // ── Tree ────────────────────────────────────────────────────────────
        treeView.setShowRoot(true);
        treeView.setCellFactory(tv -> new FileTreeCell(this));
        treeView.getStyleClass().add("file-tree");
        VBox.setVgrow(treeView, Priority.ALWAYS);

        // Double-click → open file
        treeView.setOnMouseClicked(ev -> {
            if (ev.getClickCount() < 2) return;
            TreeItem<Path> sel = treeView.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getValue() != null && !Files.isDirectory(sel.getValue())) {
                onFileOpen.accept(sel.getValue());
            }
        });

        treeView.setContextMenu(buildContextMenu(stage));

        // ── Root folder name bar ─────────────────────────────────────────────
        Label folderNameLabel = new Label();
        folderNameLabel.getStyleClass().add("sidebar-folder-name");
        folderNameLabel.setMaxWidth(Double.MAX_VALUE);
        folderNameLabel.textProperty().addListener((o, ov, nv) ->
                folderNameLabel.setVisible(nv != null && !nv.isEmpty()));

        // Update folder label whenever root changes
        treeView.rootProperty().addListener((obs, oldRoot, newRoot) -> {
            if (newRoot != null && newRoot.getValue() != null) {
                folderNameLabel.setText(
                        "  " + newRoot.getValue().getFileName().toString().toUpperCase());
            } else {
                folderNameLabel.setText("");
            }
        });

        VBox panel = new VBox(explorerTitle, openBtn, folderNameLabel, treeView);
        panel.getStyleClass().add("sidebar-panel");
        panel.setPrefWidth(230);
        panel.setMinWidth(150);
        panel.setMaxWidth(400);
        return panel;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public void setOnFileOpen(Consumer<Path> handler) {
        this.onFileOpen = handler;
    }

    public void openFolder(Stage stage) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Open Folder");
        java.io.File chosen = dc.showDialog(stage);
        if (chosen != null) setRoot(chosen.toPath());
    }

    /** Replace the tree root with the given directory. */
    public void setRoot(Path path) {
        rootPath = path;
        treeView.setRoot(buildTree(path));
        treeView.getRoot().setExpanded(true);
    }

    /** Refresh (reloads from disc). */
    public void refresh() {
        if (rootPath != null) setRoot(rootPath);
    }

    public Path getRootPath() { return rootPath; }

    // =========================================================================
    // Tree building
    // =========================================================================

    TreeItem<Path> buildTree(Path path) {
        TreeItem<Path> item = new TreeItem<>(path);
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                List<Path> children = new ArrayList<>();
                for (Path child : stream) children.add(child);

                // Sort: dirs first, then files; alphabetical within each group
                children.sort(Comparator
                        .<Path, Boolean>comparing(p -> !Files.isDirectory(p))
                        .thenComparing(p -> p.getFileName().toString().toLowerCase()));

                for (Path child : children) {
                    String name = child.getFileName().toString();
                    if (name.startsWith(".")) continue;   // hide dot-files
                    if (name.equals("build") || name.equals("out") || name.equals("target")) {
                        // still show them but collapsed
                    }
                    item.getChildren().add(buildTree(child));
                }
            } catch (IOException ignored) {}
        }
        return item;
    }

    // =========================================================================
    // Context menu
    // =========================================================================

    private ContextMenu buildContextMenu(Stage stage) {
        MenuItem newFile   = new MenuItem("📄  New File");
        MenuItem newFolder = new MenuItem("📁  New Folder");
        MenuItem rename    = new MenuItem("✏️  Rename");
        MenuItem delete    = new MenuItem("🗑️  Delete");
        MenuItem refresh   = new MenuItem("🔄  Refresh");

        refresh.setOnAction(e -> refresh());

        newFile.setOnAction(e -> {
            Path dir = targetDir();
            if (dir == null) return;
            promptName("New File", "newfile.html", name -> {
                try {
                    Files.createFile(dir.resolve(name));
                    refresh();
                } catch (IOException ex) {
                    err("Could not create file: " + ex.getMessage());
                }
            });
        });

        newFolder.setOnAction(e -> {
            Path dir = targetDir();
            if (dir == null) return;
            promptName("New Folder", "new-folder", name -> {
                try {
                    Files.createDirectory(dir.resolve(name));
                    refresh();
                } catch (IOException ex) {
                    err("Could not create folder: " + ex.getMessage());
                }
            });
        });

        rename.setOnAction(e -> {
            TreeItem<Path> sel = treeView.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            Path target = sel.getValue();
            String current = target.getFileName().toString();
            promptName("Rename", current, name -> {
                try {
                    Files.move(target, target.resolveSibling(name));
                    refresh();
                } catch (IOException ex) {
                    err("Rename failed: " + ex.getMessage());
                }
            });
        });

        delete.setOnAction(e -> {
            TreeItem<Path> sel = treeView.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            Path target = sel.getValue();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete \"" + target.getFileName() + "\"?");
            confirm.setHeaderText("Confirm Delete");
            confirm.showAndWait()
                   .filter(bt -> bt == ButtonType.OK)
                   .ifPresent(bt -> {
                       try {
                           deleteRecursive(target);
                           refresh();
                       } catch (IOException ex) {
                           err("Delete failed: " + ex.getMessage());
                       }
                   });
        });

        return new ContextMenu(newFile, newFolder,
                new SeparatorMenuItem(), rename, delete,
                new SeparatorMenuItem(), refresh);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Returns the directory for the currently selected item (or root). */
    private Path targetDir() {
        TreeItem<Path> sel = treeView.getSelectionModel().getSelectedItem();
        if (sel == null) return rootPath;
        Path p = sel.getValue();
        return Files.isDirectory(p) ? p : p.getParent();
    }

    private void promptName(String title, String defaultVal, Consumer<String> onOk) {
        TextInputDialog td = new TextInputDialog(defaultVal);
        td.setTitle(title);
        td.setHeaderText(null);
        td.setContentText("Name:");
        td.showAndWait().filter(s -> !s.isBlank()).ifPresent(onOk);
    }

    private void err(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    private void deleteRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) deleteRecursive(child);
            }
        }
        Files.delete(path);
    }

    // =========================================================================
    // Custom TreeCell
    // =========================================================================

    static class FileTreeCell extends TreeCell<Path> {

        private final FileTreePanel panel;

        FileTreeCell(FileTreePanel panel) {
            this.panel = panel;
        }

        @Override
        protected void updateItem(Path item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                getStyleClass().removeAll("tree-file", "tree-dir");
                return;
            }

            boolean isDir = Files.isDirectory(item);
            String name = item.getParent() == null
                    ? item.toString()
                    : item.getFileName().toString();

            TreeItem<Path> treeItem = getTreeItem();
            boolean expanded = treeItem != null && treeItem.isExpanded();
            String icon = isDir ? (expanded ? "▾ 📂 " : "▸ 📁 ") : fileIcon(name) + "  ";
            setText(icon + name);

            getStyleClass().removeAll("tree-file", "tree-dir");
            getStyleClass().add(isDir ? "tree-dir" : "tree-file");
        }

        private String fileIcon(String name) {
            String lower = name.toLowerCase();
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "🌐";
            if (lower.endsWith(".css"))  return "🎨";
            if (lower.endsWith(".js"))   return "⚡";
            if (lower.endsWith(".java")) return "☕";
            if (lower.endsWith(".json")) return "{}";
            if (lower.endsWith(".md"))   return "📝";
            if (lower.endsWith(".txt") || lower.endsWith(".asc")) return "📄";
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".gif") || lower.endsWith(".webp")
                    || lower.endsWith(".svg")) return "🖼";
            if (lower.endsWith(".xml"))  return "🏷";
            if (lower.endsWith(".sh"))   return "⚙";
            if (lower.endsWith(".git") || lower.equals(".gitignore")) return "⎇";
            return "◻";
        }
    }
}
