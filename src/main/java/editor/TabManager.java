package editor;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.web.WebView;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TabManager {

    private final TabPane      tabPane;
    private final EditorModule editorModule;
    private final ParserModule parserModule;
    private final PreviewModule previewModule;
    private int untitledCounter = 1;

    private final Map<Tab, EditorTabState> tabState = new HashMap<>();

    public TabManager(TabPane tabPane, EditorModule editorModule,
                      ParserModule parserModule, PreviewModule previewModule) {
        this.tabPane        = tabPane;
        this.editorModule   = editorModule;
        this.parserModule   = parserModule;
        this.previewModule  = previewModule;
    }

    // -------------------------------------------------------------------------
    // Tab creation
    // -------------------------------------------------------------------------

    public Tab createNewTab() {
        return createNewTab("");
    }

    public Tab createNewTab(String initialContent) {
        TextArea editor  = new TextArea();
        WebView  preview = new WebView();
        PauseTransition previewDebounce = new PauseTransition(Duration.millis(140));

        editorModule.installAutoIndent(editor);
        editor.setPromptText("Type HTML here, or use File > Import Text / Import Image");
        editor.setText(initialContent == null ? "" : initialContent);

        String baseTitle = "Untitled-" + untitledCounter++;
        Tab    tab       = new Tab(baseTitle);

        EditorTabState state = new EditorTabState(editor, preview, previewDebounce, null);
        tabState.put(tab, state);

        // Live preview with debounce
        previewDebounce.setOnFinished(ev -> {
            ParserModule.ValidationResult r = parserModule.validateHtml(editor.getText());
            previewModule.render(preview, editor.getText(), r);
        });

        editor.textProperty().addListener((obs, oldText, newText) -> {
            previewDebounce.playFromStart();

            // Mark dirty only after the initial load (skip if text hasn't changed)
            if (!newText.equals(oldText)) {
                if (!state.dirty) {
                    state.dirty = true;
                    updateTabTitle(tab, state);
                }
            }
        });

        ParserModule.ValidationResult r = parserModule.validateHtml(editor.getText());
        previewModule.render(preview, editor.getText(), r);

        SplitPane split = new SplitPane(editor, preview);
        split.setDividerPositions(0.5);
        split.setStyle("-fx-background-color: transparent;");

        tab.setContent(split);
        tab.setClosable(true);

        // Intercept close: ask if unsaved
        tab.setOnCloseRequest(ev -> {
            if (state.dirty) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Unsaved Changes");
                alert.setHeaderText("Tab '" + cleanTitle(tab.getText()) + "' has unsaved changes.");
                alert.setContentText("Close without saving?");
                Optional<ButtonType> choice = alert.showAndWait();
                if (choice.isEmpty() || choice.get() != ButtonType.OK) {
                    ev.consume();   // cancel the close
                }
            }
        });

        tab.setOnClosed(ev -> {
            previewDebounce.stop();
            tabState.remove(tab);
        });

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        return tab;
    }

    // -------------------------------------------------------------------------
    // Dirty-state management (called by FileModule after save)
    // -------------------------------------------------------------------------

    public void markCurrentClean() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab == null) return;
        EditorTabState state = tabState.get(tab);
        if (state == null) return;
        state.dirty = false;
        updateTabTitle(tab, state);
    }

    public boolean isCurrentDirty() {
        EditorTabState state = getCurrentState();
        return state != null && state.dirty;
    }

    // -------------------------------------------------------------------------
    // Word-wrap toggle (stored per-tab)
    // -------------------------------------------------------------------------

    public void toggleWordWrap() {
        TextArea editor = getCurrentEditor();
        if (editor != null) {
            editor.setWrapText(!editor.isWrapText());
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public TextArea getCurrentEditor() {
        EditorTabState state = getCurrentState();
        return state == null ? null : state.editor;
    }

    public WebView getCurrentPreview() {
        EditorTabState state = getCurrentState();
        return state == null ? null : state.preview;
    }

    public String getCurrentHtml() {
        TextArea editor = getCurrentEditor();
        return editor == null ? "" : editor.getText();
    }

    public void setCurrentText(String text) {
        TextArea editor = getCurrentEditor();
        if (editor == null) return;
        editor.setText(text == null ? "" : text);
        refreshCurrentPreview();
    }

    public void appendToCurrentText(String text) {
        TextArea editor = getCurrentEditor();
        if (editor == null || text == null || text.isEmpty()) return;
        String prefix = editor.getText().isBlank() ? "" : "\n";
        editor.appendText(prefix + text);
        refreshCurrentPreview();
    }

    public void setCurrentFilePath(Path path) {
        Tab current = tabPane.getSelectionModel().getSelectedItem();
        if (current == null) return;
        EditorTabState state = tabState.get(current);
        if (state == null) return;
        state.filePath = path;
        updateTabTitle(current, state);
    }

    public Path getCurrentFilePath() {
        EditorTabState state = getCurrentState();
        return state == null ? null : state.filePath;
    }

    public boolean hasTabs() {
        return !tabPane.getTabs().isEmpty();
    }

    public void refreshCurrentPreview() {
        EditorTabState state = getCurrentState();
        if (state == null) return;
        state.previewDebounce.stop();
        ParserModule.ValidationResult result = parserModule.validateHtml(state.editor.getText());
        previewModule.render(state.preview, state.editor.getText(), result);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private EditorTabState getCurrentState() {
        Tab current = tabPane.getSelectionModel().getSelectedItem();
        return current == null ? null : tabState.get(current);
    }

    private void updateTabTitle(Tab tab, EditorTabState state) {
        String base = state.filePath != null
                ? state.filePath.getFileName().toString()
                : cleanTitle(tab.getText());
        tab.setText(state.dirty ? base + " *" : base);
    }

    private String cleanTitle(String title) {
        return title.endsWith(" *") ? title.substring(0, title.length() - 2) : title;
    }

    // -------------------------------------------------------------------------
    // Mutable inner class (replaces the old record — needs dirty field)
    // -------------------------------------------------------------------------

    private static class EditorTabState {
        final TextArea         editor;
        final WebView          preview;
        final PauseTransition  previewDebounce;
        Path                   filePath;
        boolean                dirty;

        EditorTabState(TextArea editor, WebView preview,
                       PauseTransition previewDebounce, Path filePath) {
            this.editor          = editor;
            this.preview         = preview;
            this.previewDebounce = previewDebounce;
            this.filePath        = filePath;
            this.dirty           = false;
        }
    }
}
