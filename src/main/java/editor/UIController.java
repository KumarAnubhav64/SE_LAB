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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import javafx.application.Platform;

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
    private final GeminiModule      geminiModule      = new GeminiModule();

    private final TabManager tabManager =
            new TabManager(tabPane, editorModule, parserModule, previewModule);

    // ── Git working directory (remembered across operations) ──────────────────
    private Path    gitWorkDir      = null;
    private VBox    sidebarPane     = null;
    private SplitPane centerSplit   = null;
    // ── AI panel ─────────────────────────────────────────────────────────────
    private VBox    aiPanel         = null;
    private boolean aiPanelVisible  = false;
    private SplitPane mainSplit     = null;   // centerSplit | aiPanel

    // =========================================================================
    public void init(Stage stage) {
        if (!loadSession()) {
            tabManager.createNewTab();
        }

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

        // ── AI panel (hidden until toggled) ─────────────────────────────────
        aiPanel = buildAiPanel(stage);
        aiPanel.setVisible(false);
        aiPanel.setManaged(false);

        // ── Center split: sidebar | editor+preview ───────────────────────────
        centerSplit = new SplitPane(sidebarPane, tabPane);
        centerSplit.setDividerPositions(0.17);
        SplitPane.setResizableWithParent(sidebarPane, false);

        // ── Main split: center | AI panel ────────────────────────────────────
        mainSplit = new SplitPane(centerSplit, aiPanel);
        mainSplit.setDividerPositions(0.72);
        SplitPane.setResizableWithParent(aiPanel, false);

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
        root.setCenter(mainSplit);
        root.setBottom(statusLabel);
        statusLabel.getStyleClass().add("status-bar");
        BorderPane.setMargin(statusLabel, new Insets(2, 8, 4, 8));

        Scene scene = new Scene(root, 1440, 900);
        themeManager.applyTheme(scene, HtmlEditorFX.class);

        stage.setScene(scene);
        stage.setTitle("HTML Document Editor");
        stage.setMaximized(true);

        stage.setOnCloseRequest(ev -> {
            if (tabManager.hasUnsavedTabs()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Unsaved Changes");
                alert.setHeaderText("You have unsaved changes.");
                alert.setContentText("Are you sure you want to exit without saving?");
                Optional<ButtonType> choice = alert.showAndWait();
                if (choice.isEmpty() || choice.get() != ButtonType.OK) {
                    ev.consume();
                    return;
                }
            }
            saveSession();
        });

        stage.show();
    }

    private Path getSessionFile() {
        Path dir = Path.of(System.getProperty("user.home"), ".config", "htmleditor");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        return dir.resolve("session.txt");
    }

    private boolean loadSession() {
        Path sessionFile = getSessionFile();
        if (!Files.exists(sessionFile)) return false;
        
        try {
            List<String> lines = Files.readAllLines(sessionFile);
            if (lines.isEmpty()) return false;
            
            int activeIndex = 0;
            boolean loadedAny = false;
            
            for (String line : lines) {
                if (line.startsWith("ACTIVE:")) {
                    try { activeIndex = Integer.parseInt(line.substring(7)); } catch (Exception ignored) {}
                } else if (line.startsWith("GITDIR:") && line.length() > 7) {
                    Path dir = Path.of(line.substring(7));
                    if (Files.isDirectory(dir)) {
                        gitWorkDir = dir;
                    }
                } else if (!line.isBlank()) {
                    Path file = Path.of(line);
                    if (Files.exists(file)) {
                        fileModule.openPathInTab(file, tabManager, statusLabel);
                        loadedAny = true;
                    }
                }
            }
            
            if (loadedAny) {
                tabManager.setActiveTabIndex(activeIndex);
                return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    private void saveSession() {
        try {
            List<String> lines = new ArrayList<>();
            for (Path p : tabManager.getOpenFilePaths()) {
                lines.add(p.toAbsolutePath().toString());
            }
            lines.add("ACTIVE:" + tabManager.getActiveTabIndex());
            if (gitWorkDir != null) {
                lines.add("GITDIR:" + gitWorkDir.toAbsolutePath().toString());
            }
            Files.write(getSessionFile(), lines);
        } catch (IOException ignored) {}
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

        // ── AI ────────────────────────────────────────────────────────────────
        Menu aiMenu = new Menu("✨ AI");

        MenuItem aiGenerate    = new MenuItem("✨ Generate HTML from prompt…");
        MenuItem aiFix         = new MenuItem("🔧 Fix HTML errors");
        MenuItem aiImprove     = new MenuItem("⬆ Improve & modernise");
        MenuItem aiSeo         = new MenuItem("🔍 Add SEO meta tags");
        MenuItem aiA11y        = new MenuItem("♿ Improve accessibility");
        MenuItem aiExplain     = new MenuItem("📖 Explain this HTML");
        MenuItem aiPanel_toggle = new MenuItem("🤖 Toggle AI Panel");
        MenuItem aiSetKey      = new MenuItem("🔑 Set API Key…");

        aiGenerate.setAccelerator(acc(KeyCode.G, true));
        aiFix.setAccelerator(acc(KeyCode.F, true));

        aiGenerate.setOnAction(e -> runAi(GeminiModule.SYS_HTML_GENERATE,
                promptUser("Generate HTML", "Describe the page you want:"), false));
        aiFix.setOnAction(e -> runAi(GeminiModule.SYS_HTML_FIX,
                "Fix this HTML:\n\n" + tabManager.getCurrentHtml(), true));
        aiImprove.setOnAction(e -> runAi(GeminiModule.SYS_HTML_IMPROVE,
                tabManager.getCurrentHtml(), true));
        aiSeo.setOnAction(e -> runAi(GeminiModule.SYS_SEO,
                tabManager.getCurrentHtml(), true));
        aiA11y.setOnAction(e -> runAi(GeminiModule.SYS_ACCESSIBILITY,
                tabManager.getCurrentHtml(), true));
        aiExplain.setOnAction(e -> runAiExplain(tabManager.getCurrentHtml()));
        aiPanel_toggle.setOnAction(e -> toggleAiPanel());
        aiSetKey.setOnAction(e -> showSetKeyDialog());

        aiMenu.getItems().addAll(
                aiPanel_toggle, aiSetKey,
                new SeparatorMenuItem(),
                aiGenerate, aiFix, aiImprove,
                new SeparatorMenuItem(),
                aiSeo, aiA11y, aiExplain
        );

        menuBar.getMenus().addAll(fileMenu, editMenu, toolsMenu, viewMenu, gitMenu, aiMenu);
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
    // AI helpers
    // =========================================================================

    /**
     * Build the AI side panel — a VBox with a prompt area, action buttons,
     * and an output area.  Hidden by default; toggled via toggleAiPanel().
     */
    private VBox buildAiPanel(Stage stage) {
        // ── Header ────────────────────────────────────────────────────────────
        Label title = new Label("✨ AI Assistant");
        title.getStyleClass().add("ai-panel-title");
        title.setStyle("-fx-font-size:16px; -fx-font-weight:bold; -fx-padding:8 0 4 0;");

        // ── Prompt field ──────────────────────────────────────────────────────
        Label promptLbl = new Label("Your prompt:");
        TextArea promptArea = new TextArea();
        promptArea.setId("aiPromptArea");
        promptArea.setPromptText("Describe the HTML page you want, ask a question, or type any instruction…");
        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(5);
        promptArea.setStyle("-fx-font-size:13px;");

        // ── Quick-action buttons ──────────────────────────────────────────────
        Button btnGenerate = new Button("✨ Generate");
        Button btnFix      = new Button("🔧 Fix Errors");
        Button btnImprove  = new Button("⬆ Improve");
        Button btnSeo      = new Button("🔍 SEO");
        Button btnA11y     = new Button("♿ A11y");
        Button btnExplain  = new Button("📖 Explain");

        for (Button b : new Button[]{btnGenerate, btnFix, btnImprove, btnSeo, btnA11y, btnExplain}) {
            b.setMaxWidth(Double.MAX_VALUE);
            b.getStyleClass().add("ai-action-btn");
        }

        // ── Output area ───────────────────────────────────────────────────────
        Label outputLbl = new Label("AI Response:");
        TextArea outputArea = new TextArea();
        outputArea.setId("aiOutputArea");
        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.setStyle("-fx-font-size:13px; -fx-font-family: monospace;");
        VBox.setVgrow(outputArea, javafx.scene.layout.Priority.ALWAYS);

        // ── Apply / Discard ───────────────────────────────────────────────────
        Button btnApply   = new Button("✅ Apply to Editor");
        Button btnDiscard = new Button("✖ Discard");
        btnApply.setDisable(true);
        btnDiscard.setDisable(true);
        btnApply.setMaxWidth(Double.MAX_VALUE);
        btnDiscard.setMaxWidth(Double.MAX_VALUE);
        btnApply.getStyleClass().add("ai-apply-btn");
        btnDiscard.getStyleClass().add("ai-discard-btn");

        // ── Status label ──────────────────────────────────────────────────────
        Label aiStatus = new Label("Ready.");
        aiStatus.setId("aiStatusLabel");
        aiStatus.setStyle("-fx-font-size:12px; -fx-text-fill: #aaa;");

        // Holder for the last AI response (to apply to editor)
        final String[] pendingAiHtml = {null};
        final boolean[] isHtmlResponse = {false};

        // ── Wire buttons ──────────────────────────────────────────────────────
        Runnable doSend = () -> {
            String prompt = promptArea.getText().strip();
            if (prompt.isEmpty()) return;
            aiStatus.setText("⏳ Thinking…");
            outputArea.setText("");
            btnApply.setDisable(true);
            btnDiscard.setDisable(true);
            String key = geminiModule.resolveKey();
            if (key == null) { aiStatus.setText("⚠ No API key. Use ✨ AI → Set API Key…"); return; }
            geminiModule.generate(
                GeminiModule.SYS_HTML_GENERATE, prompt, key,
                text -> javafx.application.Platform.runLater(() -> {
                    outputArea.setText(text);
                    pendingAiHtml[0] = text;
                    isHtmlResponse[0] = true;
                    btnApply.setDisable(false);
                    btnDiscard.setDisable(false);
                    aiStatus.setText("✅ Done.");
                }),
                err -> javafx.application.Platform.runLater(() -> {
                    outputArea.setText("Error: " + err);
                    aiStatus.setText("❌ Error.");
                })
            );
        };

        // Ctrl+Enter in the prompt textarea sends
        promptArea.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ENTER && ev.isControlDown()) {
                doSend.run();
            }
        });

        btnGenerate.setOnAction(e -> doSend.run());

        btnFix.setOnAction(e -> {
            String html = tabManager.getCurrentHtml();
            aiStatus.setText("⏳ Fixing…");
            outputArea.setText("");
            btnApply.setDisable(true); btnDiscard.setDisable(true);
            String key = geminiModule.resolveKey();
            if (key == null) { aiStatus.setText("⚠ No API key."); return; }
            geminiModule.generate(GeminiModule.SYS_HTML_FIX, html, key,
                text -> javafx.application.Platform.runLater(() -> {
                    outputArea.setText(text); pendingAiHtml[0] = text; isHtmlResponse[0] = true;
                    btnApply.setDisable(false); btnDiscard.setDisable(false); aiStatus.setText("✅ Done.");
                }),
                err -> javafx.application.Platform.runLater(() -> { outputArea.setText("Error: " + err); aiStatus.setText("❌ Error."); }));
        });

        btnImprove.setOnAction(e -> {
            String html = tabManager.getCurrentHtml();
            aiStatus.setText("⏳ Improving…");
            outputArea.setText(""); btnApply.setDisable(true); btnDiscard.setDisable(true);
            String key = geminiModule.resolveKey();
            if (key == null) { aiStatus.setText("⚠ No API key."); return; }
            geminiModule.generate(GeminiModule.SYS_HTML_IMPROVE, html, key,
                text -> javafx.application.Platform.runLater(() -> {
                    outputArea.setText(text); pendingAiHtml[0] = text; isHtmlResponse[0] = true;
                    btnApply.setDisable(false); btnDiscard.setDisable(false); aiStatus.setText("✅ Done.");
                }),
                err -> javafx.application.Platform.runLater(() -> { outputArea.setText("Error: " + err); aiStatus.setText("❌ Error."); }));
        });

        btnSeo.setOnAction(e -> {
            String html = tabManager.getCurrentHtml();
            aiStatus.setText("⏳ Adding SEO…");
            outputArea.setText(""); btnApply.setDisable(true); btnDiscard.setDisable(true);
            String key = geminiModule.resolveKey();
            if (key == null) { aiStatus.setText("⚠ No API key."); return; }
            geminiModule.generate(GeminiModule.SYS_SEO, html, key,
                text -> javafx.application.Platform.runLater(() -> {
                    outputArea.setText(text); pendingAiHtml[0] = text; isHtmlResponse[0] = true;
                    btnApply.setDisable(false); btnDiscard.setDisable(false); aiStatus.setText("✅ Done.");
                }),
                err -> javafx.application.Platform.runLater(() -> { outputArea.setText("Error: " + err); aiStatus.setText("❌ Error."); }));
        });

        btnA11y.setOnAction(e -> {
            String html = tabManager.getCurrentHtml();
            aiStatus.setText("⏳ Improving accessibility…");
            outputArea.setText(""); btnApply.setDisable(true); btnDiscard.setDisable(true);
            String key = geminiModule.resolveKey();
            if (key == null) { aiStatus.setText("⚠ No API key."); return; }
            geminiModule.generate(GeminiModule.SYS_ACCESSIBILITY, html, key,
                text -> javafx.application.Platform.runLater(() -> {
                    outputArea.setText(text); pendingAiHtml[0] = text; isHtmlResponse[0] = true;
                    btnApply.setDisable(false); btnDiscard.setDisable(false); aiStatus.setText("✅ Done.");
                }),
                err -> javafx.application.Platform.runLater(() -> { outputArea.setText("Error: " + err); aiStatus.setText("❌ Error."); }));
        });

        btnExplain.setOnAction(e -> {
            String html = tabManager.getCurrentHtml();
            aiStatus.setText("⏳ Explaining…");
            outputArea.setText(""); btnApply.setDisable(true); btnDiscard.setDisable(true);
            String key = geminiModule.resolveKey();
            if (key == null) { aiStatus.setText("⚠ No API key."); return; }
            geminiModule.generate(GeminiModule.SYS_HTML_EXPLAIN, html, key,
                text -> javafx.application.Platform.runLater(() -> {
                    outputArea.setText(text); pendingAiHtml[0] = text; isHtmlResponse[0] = false;
                    btnApply.setDisable(true);  // explanation can't be applied
                    btnDiscard.setDisable(false); aiStatus.setText("✅ Done.");
                }),
                err -> javafx.application.Platform.runLater(() -> { outputArea.setText("Error: " + err); aiStatus.setText("❌ Error."); }));
        });

        btnApply.setOnAction(e -> {
            if (pendingAiHtml[0] != null && isHtmlResponse[0]) {
                tabManager.setCurrentText(pendingAiHtml[0]);
                statusLabel.setText("AI result applied to editor.");
                btnApply.setDisable(true); btnDiscard.setDisable(true);
                aiStatus.setText("Applied ✅");
            }
        });

        btnDiscard.setOnAction(e -> {
            outputArea.setText(""); pendingAiHtml[0] = null;
            btnApply.setDisable(true); btnDiscard.setDisable(true);
            aiStatus.setText("Discarded.");
        });

        // ── Quick actions row ─────────────────────────────────────────────────
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(6); grid.setVgap(6);
        grid.addRow(0, btnGenerate, btnFix);
        grid.addRow(1, btnImprove, btnSeo);
        grid.addRow(2, btnA11y, btnExplain);
        javafx.scene.layout.ColumnConstraints cc = new javafx.scene.layout.ColumnConstraints();
        cc.setPercentWidth(50);
        grid.getColumnConstraints().addAll(cc, cc);

        // ── Apply / Discard row ───────────────────────────────────────────────
        HBox applyRow = new HBox(6, btnApply, btnDiscard);
        HBox.setHgrow(btnApply,   javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(btnDiscard, javafx.scene.layout.Priority.ALWAYS);

        // ── Separator ─────────────────────────────────────────────────────────
        Separator sep1 = new Separator();
        Separator sep2 = new Separator();

        // ── Assemble ──────────────────────────────────────────────────────────
        VBox panel = new VBox(8,
                title, sep1,
                promptLbl, promptArea,
                grid,
                sep2,
                outputLbl, outputArea,
                applyRow,
                aiStatus
        );
        panel.setPadding(new Insets(10));
        panel.getStyleClass().add("ai-panel");
        VBox.setVgrow(outputArea, javafx.scene.layout.Priority.ALWAYS);
        return panel;
    }

    /** Toggle the AI side panel visibility. */
    private void toggleAiPanel() {
        if (aiPanel == null || mainSplit == null) return;
        aiPanelVisible = !aiPanelVisible;
        aiPanel.setVisible(aiPanelVisible);
        aiPanel.setManaged(aiPanelVisible);
        if (aiPanelVisible) {
            if (mainSplit.getItems().size() == 1)
                mainSplit.getItems().add(aiPanel);
            mainSplit.setDividerPositions(0.72);
        } else {
            mainSplit.setDividerPositions(1.0);
        }
        statusLabel.setText(aiPanelVisible ? "AI Panel opened." : "AI Panel closed.");
    }

    /**
     * Run an AI action from the menu bar (no panel — shows result in a dialog
     * and optionally replaces editor content).
     */
    private void runAi(String systemPrompt, String userPrompt, boolean replaceEditor) {
        if (userPrompt == null || userPrompt.isBlank()) return;
        String key = geminiModule.resolveKey();
        if (key == null) { showSetKeyDialog(); return; }
        statusLabel.setText("⏳ Asking AI…");
        geminiModule.generate(systemPrompt, userPrompt, key,
            text -> javafx.application.Platform.runLater(() -> {
                statusLabel.setText("AI done.");
                if (replaceEditor) {
                    Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
                        "Apply AI result to editor?", ButtonType.YES, ButtonType.NO);
                    conf.setHeaderText("AI response ready");
                    conf.showAndWait().ifPresent(bt -> {
                        if (bt == ButtonType.YES) tabManager.setCurrentText(text);
                    });
                } else {
                    // Show in a dialog
                    TextArea ta = new TextArea(text);
                    ta.setEditable(false); ta.setWrapText(true);
                    ta.setPrefRowCount(25); ta.setPrefColumnCount(80);
                    Dialog<Void> dlg = new Dialog<>();
                    dlg.setTitle("AI Result");
                    dlg.getDialogPane().setContent(ta);
                    dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                    dlg.showAndWait();
                }
            }),
            err -> javafx.application.Platform.runLater(() -> statusLabel.setText("AI error: " + err))
        );
    }

    /** Show explanation in a dialog (not applied to editor). */
    private void runAiExplain(String html) {
        if (html == null || html.isBlank()) return;
        String key = geminiModule.resolveKey();
        if (key == null) { showSetKeyDialog(); return; }
        statusLabel.setText("⏳ Explaining…");
        geminiModule.generate(GeminiModule.SYS_HTML_EXPLAIN, html, key,
            text -> javafx.application.Platform.runLater(() -> {
                statusLabel.setText("Done.");
                TextArea ta = new TextArea(text);
                ta.setEditable(false); ta.setWrapText(true);
                ta.setPrefRowCount(25); ta.setPrefColumnCount(80);
                Dialog<Void> dlg = new Dialog<>();
                dlg.setTitle("HTML Explanation");
                dlg.getDialogPane().setContent(ta);
                dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                dlg.showAndWait();
            }),
            err -> javafx.application.Platform.runLater(() -> statusLabel.setText("AI error: " + err))
        );
    }

    /** Prompt user for a string with a text input dialog. Returns null if cancelled or blank. */
    private String promptUser(String title, String question) {
        TextInputDialog td = new TextInputDialog();
        td.setTitle(title);
        td.setHeaderText(question);
        td.setContentText("Prompt:");
        Optional<String> r = td.showAndWait();
        return r.map(String::strip).filter(s -> !s.isEmpty()).orElse(null);
    }

    /** Let the user enter or update their Gemini API key. */
    private void showSetKeyDialog() {
        String existing = geminiModule.resolveKey();
        TextInputDialog td = new TextInputDialog(existing != null ? existing : "");
        td.setTitle("Gemini API Key");
        td.setHeaderText("Enter your Google Gemini API key.\n" +
            "It will be saved to ~/.config/htmleditor/gemini.key");
        td.setContentText("API Key:");
        td.showAndWait().map(String::strip).filter(s -> !s.isEmpty()).ifPresent(key -> {
            try {
                geminiModule.saveKey(key);
                statusLabel.setText("API key saved ✅");
            } catch (java.io.IOException ex) {
                statusLabel.setText("Failed to save key: " + ex.getMessage());
            }
        });
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

