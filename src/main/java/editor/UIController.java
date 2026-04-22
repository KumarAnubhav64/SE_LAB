package editor;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

public class UIController {

    // ── Core UI ──────────────────────────────────────────────────────────────
    private final TabPane tabPane    = new TabPane();
    private final Label   statusLabel = new Label("Ready");

    // ── Spinners / combo ─────────────────────────────────────────────────────
    private final Spinner<Integer> widthSpinner    = new Spinner<>(1, 132, 80);
    private final Spinner<Integer> indentSpinner   = new Spinner<>(0, 32, 5);
    private final Spinner<Integer> fontSizeSpinner = new Spinner<>(8, 72, 14);
    private final ComboBox<EditorModule.ParagraphMode> paragraphMode =
            new ComboBox<>(FXCollections.observableArrayList(EditorModule.ParagraphMode.values()));

    // ── Modules ───────────────────────────────────────────────────────────────
    private final EditorModule      editorModule      = new EditorModule();
    private final ParserModule      parserModule      = new ParserModule();
    private final PreviewModule     previewModule     = new PreviewModule();
    private final FileModule        fileModule        = new FileModule();
    private final StatisticsModule  statisticsModule  = new StatisticsModule();
    private final ThemeManager      themeManager      = new ThemeManager();
    private final GitModule         gitModule         = new GitModule();
    private final FindReplaceModule findReplaceModule = new FindReplaceModule();
    private final FileTreePanel     fileTreePanel     = new FileTreePanel();

    private final TabManager tabManager =
            new TabManager(tabPane, editorModule, parserModule, previewModule);

    // ── Git working directory (remembered across operations) ──────────────────
    private Path    gitWorkDir   = null;
    private VBox    sidebarPane  = null;
    private SplitPane centerSplit = null;

    // =========================================================================
    public void init(Stage stage) {
        tabManager.createNewTab();

        paragraphMode.setValue(EditorModule.ParagraphMode.JUSTIFY);
        widthSpinner.setEditable(true);
        indentSpinner.setEditable(true);
        fontSizeSpinner.setEditable(true);
        widthSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 132, 80));
        indentSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 32, 5));
        fontSizeSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(8, 72, 14));

        Tooling tooling = buildTooling(stage);

        // ── Sidebar (file tree) ──────────────────────────────────────────────
        sidebarPane = fileTreePanel.build(stage);
        fileTreePanel.setOnFileOpen(path ->
                fileModule.openPathInTab(path, tabManager, statusLabel));

        // Auto-sync git dir with sidebar root
        // (when user opens a folder in the sidebar, update git working dir)
        // Handled via requireGitDir() which checks tabManager's current file path.

        // ── Center split: sidebar | editor+preview ───────────────────────────
        centerSplit = new SplitPane(sidebarPane, tabPane);
        centerSplit.setDividerPositions(0.17);
        SplitPane.setResizableWithParent(sidebarPane, false);

        BorderPane root   = new BorderPane();
        Label      header = new Label("Write HTML on the left, check output on the right.");
        header.getStyleClass().add("header-label");

        VBox topSection = new VBox(
                tooling.menuBar(),
                tooling.toolBar(),
                tooling.optionStrip()
        );
        topSection.getStyleClass().add("top-section");

        root.setTop(topSection);
        root.setCenter(centerSplit);
        root.setBottom(statusLabel);
        statusLabel.getStyleClass().add("status-bar");
        BorderPane.setMargin(statusLabel, new Insets(2, 8, 4, 8));

        Scene scene = new Scene(root, 1440, 900);
        themeManager.applyTheme(scene, HtmlEditorFX.class);

        stage.setScene(scene);
        stage.setTitle("HTML Document Editor");
        stage.setMaximized(true);
        stage.show();
    }

    // =========================================================================
    private Tooling buildTooling(Stage stage) {

        // ── Spinner config (used inline in toolbar) ───────────────────────────
        widthSpinner.setPrefWidth(72);
        indentSpinner.setPrefWidth(64);
        fontSizeSpinner.setPrefWidth(64);
        paragraphMode.setPrefWidth(110);
        fontSizeSpinner.setTooltip(new Tooltip("Font size (8–72 pt)"));
        widthSpinner.setTooltip(new Tooltip("Line width (chars)"));
        indentSpinner.setTooltip(new Tooltip("Paragraph indent"));
        paragraphMode.setTooltip(new Tooltip("Text justification mode"));

        // ── Group 1: File actions ─────────────────────────────────────────────
        Button newTabBtn  = iconBtn("＋ New",    "New tab  (Ctrl+N)",      "tb-file");
        Button openBtn    = iconBtn("📂 Open",   "Open file  (Ctrl+O)",    "tb-file");
        Button saveBtn    = iconBtn("💾 Save",   "Save  (Ctrl+S)",          "tb-file tb-accent");
        Button saveAsBtn  = iconBtn("📤 Save As","Save As  (Ctrl+Shift+S)", "tb-file");

        newTabBtn.setOnAction(e -> tabManager.createNewTab());
        openBtn.setOnAction(e -> fileModule.openFile(stage, tabManager, statusLabel));
        saveBtn.setOnAction(e -> fileModule.saveFile(stage, tabManager, statusLabel));
        saveAsBtn.setOnAction(e -> fileModule.saveAsFile(stage, tabManager, statusLabel));

        // ── Group 2: History ──────────────────────────────────────────────────
        Button undoBtn = createUndoButton();  undoBtn.setText("↩ Undo");
        Button redoBtn = createRedoButton();  redoBtn.setText("↪ Redo");
        undoBtn.setTooltip(new Tooltip("Undo  (Ctrl+Z)"));
        redoBtn.setTooltip(new Tooltip("Redo  (Ctrl+Y)"));
        undoBtn.getStyleClass().add("tb-edit");
        redoBtn.getStyleClass().add("tb-edit");
        Button findBtn = createFindReplaceButton();
        findBtn.setText("🔍 Find");
        findBtn.setTooltip(new Tooltip("Find & Replace  (Ctrl+H)"));
        findBtn.getStyleClass().add("tb-edit");

        // ── Group 3: Inline text formatting ───────────────────────────────────
        Button boldBtn  = tagBtn("𝐁",   "<b>",    "</b>");
        Button italBtn  = tagBtn("𝑰",   "<i>",    "</i>");
        Button ulineBtn = tagBtn("U̲",   "<u>",    "</u>");
        Button supBtn   = tagBtn("Xⁿ",  "<sup>",  "</sup>");
        Button subBtn   = tagBtn("Xₙ",  "<sub>",  "</sub>");
        boldBtn.setTooltip(new Tooltip("Bold  <b>"));
        italBtn.setTooltip(new Tooltip("Italic  <i>"));
        ulineBtn.setTooltip(new Tooltip("Underline  <u>"));
        supBtn.setTooltip(new Tooltip("Superscript  <sup>"));
        subBtn.setTooltip(new Tooltip("Subscript  <sub>"));
        for (Button b : new Button[]{boldBtn, italBtn, ulineBtn, supBtn, subBtn})
            b.getStyleClass().add("tb-fmt");

        // ── Group 4: Block / structural tags ──────────────────────────────────
        Button h1Btn    = tagBtn("H1",  "<h1>",          "</h1>");
        Button h2Btn    = tagBtn("H2",  "<h2>",          "</h2>");
        Button h3Btn    = tagBtn("H3",  "<h3>",          "</h3>");
        Button paraBtn  = tagBtn("¶ P", "<p>",           "</p>");
        Button ulBtn    = tagBtn("• UL","<ul>\n  ",      "\n</ul>");
        Button olBtn    = tagBtn("1. OL","<ol>\n  ",     "\n</ol>");
        Button liBtn    = tagBtn("  LI","<li>",          "</li>");
        Button linkBtn  = createLinkButton();  linkBtn.setText("🔗 Link");
        Button quoteBtn = tagBtn("❝ Quote","<blockquote>","</blockquote>");
        Button codeBtn  = tagBtn("`Code","<code>",        "</code>");
        Button preBtn   = tagBtn("⎵ Pre","<pre>",         "</pre>");
        h1Btn.setTooltip(new Tooltip("Heading 1"));
        h2Btn.setTooltip(new Tooltip("Heading 2"));
        h3Btn.setTooltip(new Tooltip("Heading 3"));
        paraBtn.setTooltip(new Tooltip("Paragraph  <p>"));
        ulBtn.setTooltip(new Tooltip("Unordered list"));
        olBtn.setTooltip(new Tooltip("Ordered list"));
        liBtn.setTooltip(new Tooltip("List item"));
        linkBtn.setTooltip(new Tooltip("Hyperlink — prompts for URL"));
        quoteBtn.setTooltip(new Tooltip("Block quote"));
        codeBtn.setTooltip(new Tooltip("Inline code"));
        preBtn.setTooltip(new Tooltip("Preformatted block"));
        for (Button b : new Button[]{h1Btn,h2Btn,h3Btn,paraBtn,ulBtn,olBtn,liBtn,
                                     linkBtn,quoteBtn,codeBtn,preBtn})
            b.getStyleClass().add("tb-struct");

        // ── Group 5: Tools ────────────────────────────────────────────────────
        Button processBtn    = createProcessButton();
        Button validateBtn   = createValidateButton();
        Button statsBtn      = createStatsButton();
        Button boilerBtn     = createBoilerplateButton();
        Button applySizeBtn  = createFontSizeButton();
        Button wrapBtn       = createWordWrapButton();
        processBtn.setText("⚙ Process");
        validateBtn.setText("✔ Validate");
        statsBtn.setText("📊 Stats");
        boilerBtn.setText("📋 Boilerplate");
        applySizeBtn.setText("Aₐ Apply Size");
        wrapBtn.setText("↵ Wrap");
        processBtn.setTooltip(new Tooltip("Format plain text → HTML"));
        validateBtn.setTooltip(new Tooltip("Validate HTML tag structure"));
        statsBtn.setTooltip(new Tooltip("Word / char / tag statistics"));
        boilerBtn.setTooltip(new Tooltip("Insert HTML5 boilerplate"));
        applySizeBtn.setTooltip(new Tooltip("Apply the font-size spinner value"));
        wrapBtn.setTooltip(new Tooltip("Toggle word-wrap"));
        for (Button b : new Button[]{processBtn,validateBtn,statsBtn,boilerBtn,
                                     applySizeBtn,wrapBtn})
            b.getStyleClass().add("tb-tool");

        // ── Group 6: Settings inline (font size only) ──────────────────────
        Button themeBtn = createThemeButton(); themeBtn.setText("◑ Theme");
        themeBtn.setTooltip(new Tooltip("Toggle dark / light theme"));
        themeBtn.getStyleClass().add("tb-view");

        Label fsLabel = new Label("Sz:");
        fsLabel.getStyleClass().add("tb-spinner-label");
        // applySizeBtn already declared above in Group 5; update its label for toolbar position
        applySizeBtn.setText("✓ Apply");
        applySizeBtn.setTooltip(new Tooltip("Apply font size to selection"));

        // ── Build single unified toolbar ──────────────────────────────────────
        ToolBar toolBar = new ToolBar(
                // File
                newTabBtn, openBtn, saveBtn, saveAsBtn,
                new Separator(),
                // History + search
                undoBtn, redoBtn, findBtn,
                new Separator(),
                // Formatting
                boldBtn, italBtn, ulineBtn, supBtn, subBtn,
                new Separator(),
                // Structure
                h1Btn, h2Btn, h3Btn, paraBtn,
                linkBtn, codeBtn, preBtn, quoteBtn,
                ulBtn, olBtn, liBtn,
                new Separator(),
                // Tools
                processBtn, validateBtn, statsBtn, boilerBtn, wrapBtn,
                new Separator(),
                // Font size (compact, right of tools)
                fsLabel, fontSizeSpinner, applySizeBtn,
                new Separator(),
                themeBtn
        );
        toolBar.getStyleClass().add("editor-toolbar");

        // ── Option strip (compact row for line/indent/justify) ──────────────────
        HBox optionStrip = new HBox(6,
                new Label("W:"),    widthSpinner,
                new Label("In:"),   indentSpinner,
                new Label("Just:"), paragraphMode
        );
        optionStrip.setPadding(new Insets(3, 8, 3, 8));
        optionStrip.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        optionStrip.getStyleClass().add("option-strip");
        for (var lbl : optionStrip.getChildren()) {
            if (lbl instanceof Label l) l.getStyleClass().add("tb-spinner-label");
        }

        // ── Menu bar ──────────────────────────────────────────────────────────
        MenuBar menuBar = buildMenuBar(stage);

        return new Tooling(menuBar, toolBar, optionStrip);
    }

    /** Create a toolbar button with emoji/text label, tooltip, and style class(es). */
    private Button iconBtn(String label, String tip, String styleClasses) {
        Button b = new Button(label);
        b.setTooltip(new Tooltip(tip));
        for (String cls : styleClasses.split(" ")) {
            if (!cls.isBlank()) b.getStyleClass().add(cls);
        }
        return b;
    }

    // =========================================================================
    // Menu bar
    // =========================================================================

    private MenuBar buildMenuBar(Stage stage) {
        MenuBar menuBar = new MenuBar();

        // ── File ──────────────────────────────────────────────────────────────
        Menu fileMenu = new Menu("File");

        MenuItem newFile         = new MenuItem("New Tab");
        MenuItem openFile        = new MenuItem("Open…");
        MenuItem openFolder      = new MenuItem("Open Folder…");
        MenuItem saveFile        = new MenuItem("Save");
        MenuItem saveAsFile      = new MenuItem("Save As…");
        MenuItem importTextFile  = new MenuItem("Import Text");
        MenuItem importImageFile = new MenuItem("Import Image");
        MenuItem boilerplate     = new MenuItem("Insert Boilerplate");

        newFile.setAccelerator(acc(KeyCode.N, false));
        openFile.setAccelerator(acc(KeyCode.O, false));
        openFolder.setAccelerator(shiftAcc(KeyCode.O));
        saveFile.setAccelerator(acc(KeyCode.S, false));
        saveAsFile.setAccelerator(acc(KeyCode.S, true));
        importTextFile.setAccelerator(acc(KeyCode.I, false));
        importImageFile.setAccelerator(shiftAcc(KeyCode.I));
        boilerplate.setAccelerator(shiftAcc(KeyCode.B));

        newFile.setOnAction(e -> tabManager.createNewTab());
        openFile.setOnAction(e -> fileModule.openFile(stage, tabManager, statusLabel));
        openFolder.setOnAction(e -> {
            fileTreePanel.openFolder(stage);
            // Sync git dir to the new root
            Path root = fileTreePanel.getRootPath();
            if (root != null) { gitWorkDir = root; statusLabel.setText("Folder: " + root); }
        });
        saveFile.setOnAction(e -> fileModule.saveFile(stage, tabManager, statusLabel));
        saveAsFile.setOnAction(e -> fileModule.saveAsFile(stage, tabManager, statusLabel));
        importTextFile.setOnAction(e -> fileModule.importFile(stage, tabManager, statusLabel));
        importImageFile.setOnAction(e -> fileModule.importImage(stage, tabManager, statusLabel));
        boilerplate.setOnAction(e -> {
            editorModule.insertHtmlBoilerplate(tabManager.getCurrentEditor());
            statusLabel.setText("Inserted HTML boilerplate.");
        });

        fileMenu.getItems().addAll(
                newFile, openFile, openFolder, saveFile, saveAsFile,
                new SeparatorMenuItem(),
                importTextFile, importImageFile,
                new SeparatorMenuItem(),
                boilerplate
        );

        // ── Edit ──────────────────────────────────────────────────────────────
        Menu editMenu = new Menu("Edit");

        MenuItem findReplace = new MenuItem("Find & Replace…");
        MenuItem undoItem    = new MenuItem("Undo");
        MenuItem redoItem    = new MenuItem("Redo");

        findReplace.setAccelerator(acc(KeyCode.H, false));
        undoItem.setAccelerator(acc(KeyCode.Z, false));
        redoItem.setAccelerator(acc(KeyCode.Y, false));

        findReplace.setOnAction(e ->
                findReplaceModule.showDialog(tabManager.getCurrentEditor()));
        undoItem.setOnAction(e -> {
            SyntaxEditor ed = tabManager.getCurrentEditor();
            if (ed != null) ed.execJS("editor.undo()");
        });
        redoItem.setOnAction(e -> {
            SyntaxEditor ed = tabManager.getCurrentEditor();
            if (ed != null) ed.execJS("editor.redo()");
        });

        editMenu.getItems().addAll(undoItem, redoItem, new SeparatorMenuItem(), findReplace);

        // ── Tools ─────────────────────────────────────────────────────────────
        Menu toolsMenu = new Menu("Tools");

        MenuItem processFile  = new MenuItem("Process Text");
        MenuItem validateFile = new MenuItem("Validate");
        MenuItem statsFile    = new MenuItem("Statistics");

        processFile.setAccelerator(acc(KeyCode.P, false));
        validateFile.setAccelerator(acc(KeyCode.R, false));
        statsFile.setAccelerator(acc(KeyCode.G, false));

        processFile.setOnAction(e -> runProcess());
        validateFile.setOnAction(e -> runValidate());
        statsFile.setOnAction(e -> runStats());

        toolsMenu.getItems().addAll(processFile, validateFile, statsFile);

        // ── View ──────────────────────────────────────────────────────────────
        Menu viewMenu = new Menu("View");

        MenuItem toggleTheme   = new MenuItem("Toggle Theme");
        MenuItem wordWrap      = new MenuItem("Toggle Word Wrap");
        MenuItem toggleSidebar = new MenuItem("Toggle Sidebar");
        MenuItem refreshTree   = new MenuItem("Refresh File Tree");

        toggleTheme.setAccelerator(acc(KeyCode.T, false));
        toggleSidebar.setAccelerator(acc(KeyCode.B, false));
        toggleTheme.setOnAction(e ->
                themeManager.toggleTheme(tabPane.getScene(), HtmlEditorFX.class));
        wordWrap.setOnAction(e -> tabManager.toggleWordWrap());
        toggleSidebar.setOnAction(e -> {
            if (centerSplit != null && sidebarPane != null) {
                boolean visible = sidebarPane.isVisible();
                sidebarPane.setVisible(!visible);
                sidebarPane.setManaged(!visible);
                if (!visible) centerSplit.setDividerPositions(0.17);
            }
        });
        refreshTree.setOnAction(e -> fileTreePanel.refresh());

        viewMenu.getItems().addAll(toggleTheme, wordWrap,
                new SeparatorMenuItem(), toggleSidebar, refreshTree);

        // ── Git ───────────────────────────────────────────────────────────────
        Menu gitMenu = new Menu("Git");

        MenuItem gitInit    = new MenuItem("Init Repository…");
        MenuItem gitStatus  = new MenuItem("Status");
        MenuItem gitCommit  = new MenuItem("Stage & Commit…");
        MenuItem gitLog     = new MenuItem("Log");
        MenuItem gitDiff    = new MenuItem("Diff (unstaged)");
        MenuItem gitBranch  = new MenuItem("Branches");
        MenuItem gitSetDir  = new MenuItem("Set Working Directory…");

        gitInit.setOnAction(e -> {
            Path dir = requireGitDir(stage);
            if (dir != null) showGitResult("git init", gitModule.init(dir));
        });
        gitStatus.setOnAction(e -> {
            Path dir = requireGitDir(stage);
            if (dir != null) showGitResult("git status", gitModule.status(dir));
        });
        gitCommit.setOnAction(e -> {
            Path dir = requireGitDir(stage);
            if (dir == null) return;
            TextInputDialog td = new TextInputDialog();
            td.setTitle("Commit");
            td.setHeaderText("Commit message");
            td.setContentText("Message:");
            td.showAndWait().filter(s -> !s.isBlank()).ifPresent(msg ->
                    showGitResult("git add -A && git commit", gitModule.stageAndCommit(dir, msg)));
        });
        gitLog.setOnAction(e -> {
            Path dir = requireGitDir(stage);
            if (dir != null) showGitResult("git log", gitModule.log(dir));
        });
        gitDiff.setOnAction(e -> {
            Path dir = requireGitDir(stage);
            if (dir != null) showGitResult("git diff", gitModule.diff(dir));
        });
        gitBranch.setOnAction(e -> {
            Path dir = requireGitDir(stage);
            if (dir != null) showGitResult("git branch -v", gitModule.branch(dir));
        });
        gitSetDir.setOnAction(e -> pickGitDir(stage));

        gitMenu.getItems().addAll(
                gitSetDir,
                new SeparatorMenuItem(),
                gitInit, gitStatus,
                new SeparatorMenuItem(),
                gitCommit, gitLog, gitDiff, gitBranch
        );

        menuBar.getMenus().addAll(fileMenu, editMenu, toolsMenu, viewMenu, gitMenu);
        return menuBar;
    }

    // =========================================================================
    // Button factories
    // =========================================================================

    /** Generic "wrap selected text in tag pair" button. */
    private Button tagBtn(String label, String open, String close) {
        Button btn = new Button(label);
        btn.setOnAction(e -> editorModule.applyTag(tabManager.getCurrentEditor(), open, close));
        return btn;
    }

    private Button createLinkButton() {
        Button btn = new Button("Link");
        btn.setOnAction(e -> {
            TextInputDialog td = new TextInputDialog("https://");
            td.setTitle("Insert Link");
            td.setHeaderText("Hyperlink URL");
            td.setContentText("URL:");
            td.showAndWait().ifPresent(url -> {
                String safe = url.replace("\"", "&quot;");
                editorModule.applyTag(tabManager.getCurrentEditor(),
                        "<a href=\"" + safe + "\">", "</a>");
            });
        });
        return btn;
    }

    private Button createFontSizeButton() {
        Button btn = new Button("Apply Size");
        btn.setOnAction(e -> {
            int size = fontSizeSpinner.getValue() == null ? 14 : fontSizeSpinner.getValue();
            editorModule.applyFontSize(tabManager.getCurrentEditor(), size);
            statusLabel.setText("Applied font size to selection.");
        });
        return btn;
    }

    private Button createBoilerplateButton() {
        Button btn = new Button("Boilerplate");
        btn.setOnAction(e -> {
            editorModule.insertHtmlBoilerplate(tabManager.getCurrentEditor());
            statusLabel.setText("Inserted HTML boilerplate.");
        });
        return btn;
    }

    private Button createProcessButton() {
        Button btn = new Button("Process Text");
        btn.setOnAction(e -> runProcess());
        return btn;
    }

    private Button createValidateButton() {
        Button btn = new Button("Validate");
        btn.setOnAction(e -> runValidate());
        return btn;
    }

    private Button createStatsButton() {
        Button btn = new Button("Stats");
        btn.setOnAction(e -> runStats());
        return btn;
    }

    private Button createNewTabButton() {
        Button btn = new Button("+ Tab");
        btn.setOnAction(e -> tabManager.createNewTab());
        return btn;
    }

    private Button createThemeButton() {
        Button btn = new Button("Theme");
        btn.setOnAction(e -> themeManager.toggleTheme(tabPane.getScene(), HtmlEditorFX.class));
        return btn;
    }

    private Button createUndoButton() {
        Button btn = new Button("Undo");
        btn.setOnAction(e -> {
            SyntaxEditor ed = tabManager.getCurrentEditor();
            if (ed != null) ed.execJS("editor.undo()");
        });
        return btn;
    }

    private Button createRedoButton() {
        Button btn = new Button("Redo");
        btn.setOnAction(e -> {
            SyntaxEditor ed = tabManager.getCurrentEditor();
            if (ed != null) ed.execJS("editor.redo()");
        });
        return btn;
    }

    private Button createFindReplaceButton() {
        Button btn = new Button("Find/Replace");
        btn.setOnAction(e -> findReplaceModule.showDialog(tabManager.getCurrentEditor()));
        return btn;
    }

    private Button createWordWrapButton() {
        Button btn = new Button("Word Wrap");
        btn.setOnAction(e -> tabManager.toggleWordWrap());
        return btn;
    }

    private Button createSaveAsButton(Stage stage) {
        Button btn = new Button("Save As…");
        btn.setOnAction(e -> fileModule.saveAsFile(stage, tabManager, statusLabel));
        return btn;
    }

    // =========================================================================
    // Action helpers
    // =========================================================================

    private void runProcess() {
        int width  = widthSpinner.getValue()     == null ? 80   : widthSpinner.getValue();
        int indent = indentSpinner.getValue()     == null ? 5    : indentSpinner.getValue();
        EditorModule.ParagraphMode mode = paragraphMode.getValue() == null
                ? EditorModule.ParagraphMode.JUSTIFY : paragraphMode.getValue();

        String processed = editorModule.processPlainTextToHtml(
                tabManager.getCurrentHtml(), width, indent, mode);
        tabManager.setCurrentText(processed);

        ParserModule.ValidationResult result = parserModule.validateHtml(processed);
        previewModule.render(tabManager.getCurrentPreview(), processed, result);
        statusLabel.setText("Processed — width=" + width + ", indent=" + indent + ", mode=" + mode);
    }

    private void runValidate() {
        String html = tabManager.getCurrentHtml();
        ParserModule.ValidationResult result = parserModule.validateHtml(html);
        previewModule.render(tabManager.getCurrentPreview(), html, result);
        statusLabel.setText(result.getMessage());
    }

    private void runStats() {
        StatisticsModule.Stats stats = statisticsModule.compute(tabManager.getCurrentHtml());
        statusLabel.setText(statisticsModule.format(stats));
    }

    // =========================================================================
    // Git helpers
    // =========================================================================

    /**
     * Returns the active git working directory.
     * Priority: (1) explicitly set, (2) parent of current file, (3) user picks.
     */
    private Path requireGitDir(Stage stage) {
        if (gitWorkDir != null) return gitWorkDir;

        Path filePath = tabManager.getCurrentFilePath();
        if (filePath != null) {
            gitWorkDir = filePath.getParent();
            statusLabel.setText("Git dir: " + gitWorkDir);
            return gitWorkDir;
        }

        return pickGitDir(stage);
    }

    private Path pickGitDir(Stage stage) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Git Working Directory");
        File chosen = dc.showDialog(stage);
        if (chosen != null) {
            gitWorkDir = chosen.toPath();
            statusLabel.setText("Git dir set: " + gitWorkDir);
        }
        return gitWorkDir;
    }

    /** Show git command output in a scrollable dialog. */
    private void showGitResult(String title, String output) {
        TextArea area = new TextArea(output);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(20);
        area.setPrefColumnCount(70);
        area.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(title);
        dlg.setHeaderText(null);
        dlg.getDialogPane().setContent(area);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.showAndWait();
    }

    // =========================================================================
    // Keyboard shortcut helpers
    // =========================================================================

    /** Ctrl + key */
    private KeyCombination acc(KeyCode key, boolean withShift) {
        return withShift
                ? new KeyCodeCombination(key, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN)
                : new KeyCodeCombination(key, KeyCombination.CONTROL_DOWN);
    }

    /** Ctrl + Shift + key */
    private KeyCombination shiftAcc(KeyCode key) {
        return new KeyCodeCombination(key, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);
    }

    // =========================================================================
    // Tooling record
    // =========================================================================

    private record Tooling(MenuBar menuBar, ToolBar toolBar, HBox optionStrip) {}
}
