package editor;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class UIController {

    private final TabPane tabPane = new TabPane();
    private final Label statusLabel = new Label("Ready");

    private final Spinner<Integer> widthSpinner = new Spinner<>(1, 132, 80);
    private final Spinner<Integer> indentSpinner = new Spinner<>(0, 32, 5);
    private final Spinner<Integer> fontSizeSpinner = new Spinner<>(8, 72, 14);
    private final ComboBox<EditorModule.ParagraphMode> paragraphMode =
            new ComboBox<>(FXCollections.observableArrayList(EditorModule.ParagraphMode.values()));

    private final EditorModule editorModule = new EditorModule();
    private final ParserModule parserModule = new ParserModule();
    private final PreviewModule previewModule = new PreviewModule();
    private final FileModule fileModule = new FileModule();
    private final StatisticsModule statisticsModule = new StatisticsModule();
    private final ThemeManager themeManager = new ThemeManager();

    private final TabManager tabManager = new TabManager(tabPane, editorModule, parserModule, previewModule);

    public void init(Stage stage) {
        tabManager.createNewTab();

        paragraphMode.setValue(EditorModule.ParagraphMode.JUSTIFY);
        widthSpinner.setEditable(true);
        indentSpinner.setEditable(true);
        fontSizeSpinner.setEditable(true);
        widthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 132, 80));
        indentSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 32, 5));
        fontSizeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(8, 72, 14));

        Tooling tooling = buildTooling(stage);

        BorderPane root = new BorderPane();
        Label header = new Label("Write HTML on the left, check output on the right.");
        header.getStyleClass().add("header-label");

        VBox topSection = new VBox(tooling.menuBar(), header, tooling.optionBar(), tooling.toolBar());
        topSection.getStyleClass().add("top-section");

        root.setTop(topSection);
        root.setCenter(tabPane);
        root.setBottom(statusLabel);
        statusLabel.getStyleClass().add("status-bar");
        BorderPane.setMargin(statusLabel, new Insets(4, 8, 6, 8));

        Scene scene = new Scene(root, 1400, 900);
        themeManager.applyTheme(scene, HtmlEditorFX.class);

        stage.setScene(scene);
        stage.setTitle("HTML Document Editor");
        stage.setMaximized(true);
        stage.show();
    }

    private Tooling buildTooling(Stage stage) {
        Button boldButton = createTagButton("Bold", "<b>", "</b>");
        Button italicButton = createTagButton("Italic", "<i>", "</i>");
        Button underlineButton = createTagButton("Underline", "<u>", "</u>");
        Button supButton = createTagButton("Sup", "<sup>", "</sup>");
        Button subButton = createTagButton("Sub", "<sub>", "</sub>");
        Button applySizeButton = createFontSizeButton();
        Button boilerplateButton = createBoilerplateButton();
        Button processButton = createProcessButton();
        Button validateButton = createValidateButton();
        Button statsButton = createStatsButton();
        Button newTabButton = createNewTabButton();
        Button themeButton = createThemeButton();

        boldButton.setTooltip(new Tooltip("Wrap selection in <b>...</b>"));
        italicButton.setTooltip(new Tooltip("Wrap selection in <i>...</i>"));
        underlineButton.setTooltip(new Tooltip("Wrap selection in <u>...</u>"));
        supButton.setTooltip(new Tooltip("Apply superscript"));
        subButton.setTooltip(new Tooltip("Apply subscript"));
        applySizeButton.setTooltip(new Tooltip("Apply selected font size"));
        boilerplateButton.setTooltip(new Tooltip("Insert a full HTML boilerplate"));
        processButton.setTooltip(new Tooltip("Format plain text to HTML using width/indent/mode"));
        validateButton.setTooltip(new Tooltip("Run HTML validation"));
        statsButton.setTooltip(new Tooltip("Show words, lines, chars, paragraphs and tags"));
        newTabButton.setTooltip(new Tooltip("Create a new editor tab"));
        themeButton.setTooltip(new Tooltip("Toggle light/dark theme"));

        javafx.scene.control.ToolBar toolBar = new javafx.scene.control.ToolBar(
            boldButton,
            italicButton,
            underlineButton,
            supButton,
            subButton,
            applySizeButton,
            boilerplateButton,
            new Separator(),
            processButton,
            validateButton,
            statsButton,
            new Separator(),
            newTabButton,
            themeButton
        );
        toolBar.getStyleClass().add("editor-toolbar");

        HBox optionBar = new HBox(8,
                new Label("Line Width (1-132):"), widthSpinner,
                new Label("Paragraph Indent:"), indentSpinner,
                new Label("Justification:"), paragraphMode,
                new Label("Font Size:"), fontSizeSpinner
        );
        optionBar.setPadding(new Insets(6, 8, 6, 8));
        optionBar.getStyleClass().add("option-bar");
        widthSpinner.setPrefWidth(96);
        indentSpinner.setPrefWidth(96);
        fontSizeSpinner.setPrefWidth(96);
        paragraphMode.setPrefWidth(120);

        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        Menu viewMenu = new Menu("View");
        Menu toolsMenu = new Menu("Tools");

        MenuItem newFile = new MenuItem("New Tab");
        MenuItem openFile = new MenuItem("Open");
        MenuItem saveFile = new MenuItem("Save");
        MenuItem importTextFile = new MenuItem("Import Text");
        MenuItem importImageFile = new MenuItem("Import Image");
        MenuItem boilerplateFile = new MenuItem("Insert Boilerplate");
        MenuItem validateFile = new MenuItem("Validate");
        MenuItem statsFile = new MenuItem("Statistics");
        MenuItem processFile = new MenuItem("Process Text");
        MenuItem toggleTheme = new MenuItem("Toggle Theme");

        newFile.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        openFile.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        saveFile.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        importTextFile.setAccelerator(new KeyCodeCombination(KeyCode.I, KeyCombination.CONTROL_DOWN));
        importImageFile.setAccelerator(new KeyCodeCombination(KeyCode.I, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        boilerplateFile.setAccelerator(new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        validateFile.setAccelerator(new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN));
        statsFile.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN));
        processFile.setAccelerator(new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN));
        toggleTheme.setAccelerator(new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN));

        newFile.setOnAction(e -> tabManager.createNewTab());
        openFile.setOnAction(e -> fileModule.openFile(stage, tabManager, statusLabel));
        saveFile.setOnAction(e -> fileModule.saveFile(stage, tabManager, statusLabel));
        importTextFile.setOnAction(e -> fileModule.importFile(stage, tabManager, statusLabel));
        importImageFile.setOnAction(e -> fileModule.importImage(stage, tabManager, statusLabel));
        boilerplateFile.setOnAction(e -> boilerplateButton.fire());
        validateFile.setOnAction(e -> validateButton.fire());
        statsFile.setOnAction(e -> statsButton.fire());
        processFile.setOnAction(e -> processButton.fire());
        toggleTheme.setOnAction(e -> themeButton.fire());

        fileMenu.getItems().addAll(newFile, openFile, saveFile, importTextFile, importImageFile, boilerplateFile);
        toolsMenu.getItems().addAll(processFile, validateFile, statsFile);
        viewMenu.getItems().add(toggleTheme);
        menuBar.getMenus().addAll(fileMenu, toolsMenu, viewMenu);

        return new Tooling(menuBar, optionBar, toolBar);
    }

    private Button createTagButton(String name, String start, String end) {
        Button button = new Button(name);
        button.setOnAction(e -> editorModule.applyTag(tabManager.getCurrentEditor(), start, end));
        return button;
    }

    private Button createFontSizeButton() {
        Button button = new Button("Apply Size");
        button.setOnAction(e -> {
            Integer size = fontSizeSpinner.getValue();
            editorModule.applyFontSize(tabManager.getCurrentEditor(), size == null ? 14 : size);
            statusLabel.setText("Applied font size to selection.");
        });
        return button;
    }

    private Button createBoilerplateButton() {
        Button button = new Button("Boilerplate");
        button.setOnAction(e -> {
            editorModule.insertHtmlBoilerplate(tabManager.getCurrentEditor());
            statusLabel.setText("Inserted HTML boilerplate.");
        });
        return button;
    }

    private Button createProcessButton() {
        Button button = new Button("Process Text");
        button.setOnAction(e -> {
            int width = widthSpinner.getValue() == null ? 80 : widthSpinner.getValue();
            int indent = indentSpinner.getValue() == null ? 5 : indentSpinner.getValue();
            EditorModule.ParagraphMode mode = paragraphMode.getValue() == null
                    ? EditorModule.ParagraphMode.JUSTIFY
                    : paragraphMode.getValue();

            String processed = editorModule.processPlainTextToHtml(tabManager.getCurrentHtml(), width, indent, mode);
            tabManager.setCurrentText(processed);

            ParserModule.ValidationResult result = parserModule.validateHtml(processed);
            previewModule.render(tabManager.getCurrentPreview(), processed, result);
            statusLabel.setText("Processed with width=" + width + ", indent=" + indent + ", mode=" + mode + ".");
        });
        return button;
    }

    private Button createValidateButton() {
        Button button = new Button("Validate");
        button.setOnAction(e -> {
            String html = tabManager.getCurrentHtml();
            ParserModule.ValidationResult result = parserModule.validateHtml(html);
            previewModule.render(tabManager.getCurrentPreview(), html, result);
            statusLabel.setText(result.getMessage());
        });
        return button;
    }

    private Button createStatsButton() {
        Button button = new Button("Stats");
        button.setOnAction(e -> {
            StatisticsModule.Stats stats = statisticsModule.compute(tabManager.getCurrentHtml());
            statusLabel.setText(statisticsModule.format(stats));
        });
        return button;
    }

    private Button createNewTabButton() {
        Button button = new Button("+ Tab");
        button.setOnAction(e -> tabManager.createNewTab());
        return button;
    }

    private Button createThemeButton() {
        Button button = new Button("Theme");
        button.setOnAction(e -> themeManager.toggleTheme(tabPane.getScene(), HtmlEditorFX.class));
        return button;
    }

    private record Tooling(MenuBar menuBar, HBox optionBar, javafx.scene.control.ToolBar toolBar) {
    }
}
