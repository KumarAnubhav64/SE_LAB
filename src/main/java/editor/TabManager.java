package editor;

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

public class TabManager {

    private final TabPane tabPane;
    private final EditorModule editorModule;
    private final ParserModule parserModule;
    private final PreviewModule previewModule;
    private int untitledCounter = 1;

    private final Map<Tab, EditorTabState> tabState = new HashMap<>();

    public TabManager(TabPane tabPane, EditorModule editorModule, ParserModule parserModule, PreviewModule previewModule) {
        this.tabPane = tabPane;
        this.editorModule = editorModule;
        this.parserModule = parserModule;
        this.previewModule = previewModule;
    }

    public Tab createNewTab() {
        return createNewTab("");
    }

    public Tab createNewTab(String initialContent) {
        TextArea editor = new TextArea();
        WebView preview = new WebView();
        PauseTransition previewDebounce = new PauseTransition(Duration.millis(140));

        editorModule.installAutoIndent(editor);
        editor.setPromptText("Type HTML here, or use File > Import Text / Import Image");
        editor.setText(initialContent == null ? "" : initialContent);

        previewDebounce.setOnFinished(event -> {
            ParserModule.ValidationResult result = parserModule.validateHtml(editor.getText());
            previewModule.render(preview, editor.getText(), result);
        });

        editor.textProperty().addListener((obs, oldText, newText) -> {
            previewDebounce.playFromStart();
        });

        ParserModule.ValidationResult result = parserModule.validateHtml(editor.getText());
        previewModule.render(preview, editor.getText(), result);

        SplitPane split = new SplitPane(editor, preview);
        split.setDividerPositions(0.5);
        split.setStyle("-fx-background-color: transparent;");

        String title = "Untitled-" + untitledCounter++;
        Tab tab = new Tab(title, split);
        tab.setClosable(true);

        tab.setOnClosed(event -> {
            previewDebounce.stop();
            tabState.remove(tab);
        });

        tabState.put(tab, new EditorTabState(editor, preview, previewDebounce, null));
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        return tab;
    }

    public TextArea getCurrentEditor() {
        EditorTabState state = getCurrentState();
        return state == null ? null : state.editor();
    }

    public WebView getCurrentPreview() {
        EditorTabState state = getCurrentState();
        return state == null ? null : state.preview();
    }

    public String getCurrentHtml() {
        TextArea editor = getCurrentEditor();
        return editor == null ? "" : editor.getText();
    }

    public void setCurrentText(String text) {
        TextArea editor = getCurrentEditor();
        if (editor == null) {
            return;
        }
        editor.setText(text == null ? "" : text);
        refreshCurrentPreview();
    }

    public void appendToCurrentText(String text) {
        TextArea editor = getCurrentEditor();
        if (editor == null || text == null || text.isEmpty()) {
            return;
        }

        String prefix = editor.getText().isBlank() ? "" : "\n";
        editor.appendText(prefix + text);
        refreshCurrentPreview();
    }

    public void setCurrentFilePath(Path path) {
        Tab current = tabPane.getSelectionModel().getSelectedItem();
        if (current == null) {
            return;
        }

        EditorTabState state = tabState.get(current);
        if (state == null) {
            return;
        }

        tabState.put(current, new EditorTabState(state.editor(), state.preview(), state.previewDebounce(), path));
        current.setText(path == null ? current.getText() : path.getFileName().toString());
    }

    public Path getCurrentFilePath() {
        EditorTabState state = getCurrentState();
        return state == null ? null : state.filePath();
    }

    public boolean hasTabs() {
        return !tabPane.getTabs().isEmpty();
    }

    public void refreshCurrentPreview() {
        EditorTabState state = getCurrentState();
        if (state == null) {
            return;
        }

        state.previewDebounce().stop();
        ParserModule.ValidationResult result = parserModule.validateHtml(state.editor().getText());
        previewModule.render(state.preview(), state.editor().getText(), result);
    }

    private EditorTabState getCurrentState() {
        Tab current = tabPane.getSelectionModel().getSelectedItem();
        if (current == null) {
            return null;
        }

        return tabState.get(current);
    }

    private record EditorTabState(TextArea editor, WebView preview, PauseTransition previewDebounce, Path filePath) {
    }
}
