# HTML Document Editor - Project Documentation

## 1. Overview
This project is a modern JavaFX-based HTML document editor that supports:
- Multi-tab editing with robust CodeMirror 5 syntax highlighting
- AI-Powered Assistant (powered by Google Gemini) for HTML generation and fixing
- Integrated Git version control
- Live side-by-side rendering preview
- Structural HTML validation
- Find & Replace functionality
- Text formatting tools and plain-text to processed HTML transformation
- File operations (open, save, import text, import image)
- Document statistics and dynamic UI theme switching (dark/light)
- Build and release packaging to zero-dependency AppImages

## 2. Structured Folder Layout

```text
src/
  main/
    java/
      editor/
        HtmlEditorFX.java
        UIController.java
        EditorModule.java
        SyntaxEditor.java
        FindReplaceModule.java
        GeminiModule.java
        GitModule.java
        ParserModule.java
        PreviewModule.java
        FileModule.java
        StatisticsModule.java
        TabManager.java
        ThemeManager.java
    resources/
      dark.css
      light.css
      icon.png
      codemirror/
        lib/codemirror.js
        lib/codemirror.css
        ... (and language modes)
build.sh
appimage.sh
release.sh
DOCUMENTATION.md
.gitignore
```

## 3. Module Responsibilities

### HtmlEditorFX.java (Main Application)
- JavaFX entry point.
- Starts application and delegates initialization to UIController.

### UIController.java (UI Module)
- Builds and wires the modern JavaFX UI layout.
- Connects toolbar, menu actions, and the collapsible AI side panel.
- Hosts controls for line-width, paragraph indent, justification, and font size.

### EditorModule.java & SyntaxEditor.java (Editor Layer)
- `SyntaxEditor` wraps a JavaFX WebView to run CodeMirror 5, providing professional HTML syntax highlighting, theming, and line numbers.
- `EditorModule` interfaces with the syntax editor to apply HTML tags to selected text, insert formatting tags (bold, italic, underline, superscript, subscript), and apply fonts.
- Implements plain-text processing to HTML with fixed line width and justification.

### FindReplaceModule.java (Search Module)
- Provides an integrated dialog/bar for searching and replacing text throughout the active CodeMirror editor bounds.

### GeminiModule.java (AI Assistant)
- Lightweight async HTTP client interfacing with Google's Gemini REST API.
- Implements prompt libraries for structured HTML generation, bug fixing, and semantic improvement.

### GitModule.java (Version Control)
- Provides rudimentary integration with the local `.git` repository, allowing users to stage, review changes, and commit directly from the editor.

### ParserModule.java (Parser Module)
- Performs structural HTML tag validation.
- Detects mismatched and unexpected closing tags.
- Handles standard void tags.

### PreviewModule.java (Preview Module)
- Renders current HTML into a seamless split-pane WebView.
- Shows validation warning banner when parser reports issues.

### FileModule.java (File Module)
- Open file into a new tab.
- Save current tab.
- Import text file into current tab.
- Import image file into current tab by inserting an HTML img tag.

### StatisticsModule.java (Statistics Module)
- Computes and formats metrics: Words, Lines, Characters, Paragraphs, Tag count.

### TabManager.java (Tab Management)
- Creates and tracks editor tabs.
- Stores per-tab editor, preview, and file path state.
- Keeps live preview synchronized with editor text updates.

### ThemeManager.java (Theme Management)
- Applies dark/light stylesheet resources across both the JavaFX and CodeMirror environments.
- Toggles theme at runtime.

## 4. Feature Coverage

- Editing, formatting, interaction: implemented in EditorModule, SyntaxEditor, FindReplaceModule, + UIController.
- Parsing + rendering: implemented in ParserModule + PreviewModule.
- File and versioning: implemented in FileModule + GitModule.
- AI Assistant: implemented via GeminiModule.
- Stats + display: implemented in StatisticsModule + UIController status bar.
- Tab management: implemented in TabManager.
- Theme management: implemented in ThemeManager.

## 5. How to Use

1. Start app with local build output:
   - Run `./build.sh`
   - Run `./build/output/HtmlEditorFX/bin/HtmlEditorFX`
2. Open or create a new tab (`Ctrl+N`).
3. Type HTML or interact with the AI side panel (click "AI Assistant" on the right sidebar).
4. Use formatting tools in the top toolbar to structure documents.
5. Search with `Ctrl+F` using Find & Replace.
6. Commit changes via the Git button in the top menu.

## 6. Build and Packaging Scripts

### build.sh
- Compiles sources from `src/main/java`.
- Copies resources and fully vendors CodeMirror from `src/main/resources`.
- Creates `app.jar`.
- Builds a jpackage app-image, requiring JDK crypto and HTTP modules for the Gemini API SSL connections.

### appimage.sh
- Uses jpackage output to create AppDir.
- Injects AppRun and desktop entry.
- Uses appimagetool to generate an AppImage.

### release.sh
- Generates timestamped release folder.
- Runs build.sh & appimage.sh. Produces final deployable AppImage.

## 7. Configuration
- To use the AI Assistant, export `GEMINI_API_KEY` into your shell prior to launch or place it in `~/.config/htmleditor/gemini.key`.
