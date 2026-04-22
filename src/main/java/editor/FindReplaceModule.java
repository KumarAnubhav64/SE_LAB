package editor;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * FindReplaceModule provides a persistent Find & Replace dialog.
 * It operates directly on the TextArea of the active editor tab.
 */
public class FindReplaceModule {

    private int    lastFindIndex = -1;
    private String lastQuery     = null;

    /** Open (or re-open) the Find & Replace dialog for the given TextArea. */
    public void showDialog(TextArea editor) {
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
    // Private helpers
    // -------------------------------------------------------------------------

    private void findNext(TextArea editor, String query, boolean caseSens) {
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
            editor.selectRange(idx, idx + query.length());
            editor.requestFocus();
        } else {
            lastFindIndex = -1;
            alert("\"" + query + "\" not found.");
        }
    }

    private void replaceCurrent(TextArea editor, String query, String replacement, boolean caseSens) {
        String sel     = editor.getSelectedText();
        String cmpSel  = caseSens ? sel  : sel.toLowerCase();
        String cmpQ    = caseSens ? query : query.toLowerCase();

        if (cmpSel.equals(cmpQ)) {
            editor.replaceSelection(replacement);
            lastFindIndex = -1;
        } else {
            // Nothing selected that matches — find first occurrence
            findNext(editor, query, caseSens);
        }
    }

    private void replaceAll(TextArea editor, String query, String replacement, boolean caseSens) {
        String text   = editor.getText();
        String result;
        if (caseSens) {
            result = text.replace(query, replacement);
        } else {
            result = text.replaceAll(
                    "(?i)" + Pattern.quote(query),
                    java.util.regex.Matcher.quoteReplacement(replacement));
        }
        editor.setText(result);
        lastFindIndex = -1;
        alert("Replace All complete.");
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
