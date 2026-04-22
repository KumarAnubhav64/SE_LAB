package editor;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * FindReplaceModule — works with the SyntaxEditor (CodeMirror WebView).
 *
 * <p>Find / replace is performed on the raw text retrieved via
 * {@link SyntaxEditor#getText()}, then pushed back via
 * {@link SyntaxEditor#updateText(String)}.  The selection is highlighted
 * by injecting a CodeMirror JS call.</p>
 */
public class FindReplaceModule {

    private int    lastFindIndex = -1;
    private String lastQuery     = null;

    /** Open (or re-open) the Find & Replace dialog for the given SyntaxEditor. */
    public void showDialog(SyntaxEditor editor) {
        if (editor == null) return;

        Dialog<ButtonType> dialog = buildDialog();

        TextField  findField    = (TextField)  dialog.getDialogPane().lookup("#findField");
        TextField  replaceField = (TextField)  dialog.getDialogPane().lookup("#replaceField");
        CheckBox   caseCB       = (CheckBox)   dialog.getDialogPane().lookup("#caseCB");

        ButtonType findNextBT   = dialog.getDialogPane().getButtonTypes().get(0);
        ButtonType replaceBT    = dialog.getDialogPane().getButtonTypes().get(1);
        ButtonType replaceAllBT = dialog.getDialogPane().getButtonTypes().get(2);
        ButtonType closeBT      = dialog.getDialogPane().getButtonTypes().get(3);

        // Prevent the dialog from auto-closing when Find / Replace are clicked
        dialog.getDialogPane().lookupButton(findNextBT).addEventFilter(
                javafx.event.ActionEvent.ACTION, e -> e.consume());
        dialog.getDialogPane().lookupButton(replaceBT).addEventFilter(
                javafx.event.ActionEvent.ACTION, e -> e.consume());

        dialog.setResultConverter(bt -> bt);

        while (true) {
            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isEmpty() || result.get() == closeBT) break;

            String query       = findField.getText();
            String replacement = replaceField.getText();
            boolean caseSens   = caseCB.isSelected();
            if (query.isEmpty()) continue;

            ButtonType action = result.get();

            if (action == findNextBT) {
                findNext(editor, query, caseSens);
            } else if (action == replaceBT) {
                replaceCurrent(editor, query, replacement, caseSens);
            } else if (action == replaceAllBT) {
                replaceAll(editor, query, replacement, caseSens);
                break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers (operate on plain text, push back via SyntaxEditor API)
    // -------------------------------------------------------------------------

    private void findNext(SyntaxEditor editor, String query, boolean caseSens) {
        String text        = editor.getText();
        String searchText  = caseSens ? text  : text.toLowerCase();
        String searchQuery = caseSens ? query : query.toLowerCase();

        int startFrom = (!query.equals(lastQuery) || lastFindIndex < 0)
                ? 0 : lastFindIndex + 1;
        lastQuery = query;

        int idx = searchText.indexOf(searchQuery, startFrom);
        if (idx < 0) idx = searchText.indexOf(searchQuery, 0); // wrap

        if (idx >= 0) {
            lastFindIndex = idx;
            highlightInEditor(editor, text, idx, idx + query.length());
        } else {
            lastFindIndex = -1;
            alert("\"" + query + "\" not found.");
        }
    }

    private void replaceCurrent(SyntaxEditor editor, String query, String replacement, boolean caseSens) {
        String text  = editor.getText();
        // Figure out what's currently highlighted
        int startFrom = lastFindIndex >= 0 ? lastFindIndex : 0;
        String searchText  = caseSens ? text  : text.toLowerCase();
        String searchQuery = caseSens ? query : query.toLowerCase();

        int idx = searchText.indexOf(searchQuery, startFrom);
        if (idx < 0) {
            findNext(editor, query, caseSens);
            return;
        }
        // Replace the hit
        String newText = text.substring(0, idx) + replacement + text.substring(idx + query.length());
        editor.updateText(newText);
        lastFindIndex = -1;
    }

    private void replaceAll(SyntaxEditor editor, String query, String replacement, boolean caseSens) {
        String text = editor.getText();
        String result;
        if (caseSens) {
            result = text.replace(query, replacement);
        } else {
            result = text.replaceAll(
                    "(?i)" + Pattern.quote(query),
                    java.util.regex.Matcher.quoteReplacement(replacement));
        }
        editor.updateText(result);
        lastFindIndex = -1;
        alert("Replace All complete.");
    }

    /**
     * Highlight the matched range in CodeMirror.
     * Converts a flat char offset to {line, ch} via JS.
     */
    private void highlightInEditor(SyntaxEditor editor, String text, int start, int end) {
        editor.execJS(
            "(function() {" +
            "  var doc = editor.getDoc();" +
            "  var from = doc.posFromIndex(" + start + ");" +
            "  var to   = doc.posFromIndex(" + end   + ");" +
            "  editor.setSelection(from, to, {scroll: true});" +
            "  editor.focus();" +
            "})()"
        );
    }

    private void alert(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }

    // -------------------------------------------------------------------------
    // Dialog factory
    // -------------------------------------------------------------------------

    private Dialog<ButtonType> buildDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Find & Replace");
        dialog.setHeaderText(null);

        TextField findField = new TextField();
        findField.setId("findField");
        findField.setPromptText("Find…");
        findField.setPrefWidth(280);

        TextField replaceField = new TextField();
        replaceField.setId("replaceField");
        replaceField.setPromptText("Replace with…");
        replaceField.setPrefWidth(280);

        CheckBox caseCB = new CheckBox("Case sensitive");
        caseCB.setId("caseCB");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16, 20, 16, 20));
        grid.addRow(0, new Label("Find:"),    findField);
        grid.addRow(1, new Label("Replace:"), replaceField);
        grid.addRow(2, new Label(""),         caseCB);

        dialog.getDialogPane().setContent(grid);

        ButtonType findNextBT   = new ButtonType("Find Next",   ButtonBar.ButtonData.LEFT);
        ButtonType replaceBT    = new ButtonType("Replace",      ButtonBar.ButtonData.LEFT);
        ButtonType replaceAllBT = new ButtonType("Replace All",  ButtonBar.ButtonData.OK_DONE);
        ButtonType closeBT      = new ButtonType("Close",        ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getDialogPane().getButtonTypes()
              .addAll(findNextBT, replaceBT, replaceAllBT, closeBT);

        return dialog;
    }
}
