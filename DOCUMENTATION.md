# HTML Document Editor - Project Documentation

## 1. Overview
This project is a JavaFX-based HTML document editor that supports:
- Multi-tab editing
- Live preview
- HTML validation
- Text formatting tools
- Plain-text to processed HTML transformation with fixed line width and optional justification
- File operations (open, save, import text, import image)
- Statistics (characters, words, lines, paragraphs, tags)
- Theme switching
- Build and release packaging to AppImage

## 2. Structured Folder Layout

```text
src/
  main/
    java/
      editor/
        HtmlEditorFX.java
        UIController.java
        EditorModule.java
        ParserModule.java
        PreviewModule.java
        FileModule.java
        StatisticsModule.java
        TabManager.java
        ThemeManager.java
    resources/
      dark.css
      light.css
build.sh
appimage.sh
release.sh
DOCUMENTATION.md
```

## 3. Module Responsibilities

### HtmlEditorFX.java (Main Application)
- JavaFX entry point.
- Starts application and delegates initialization to UIController.

### UIController.java (UI Module)
- Builds and wires UI components.
- Connects toolbar and menu actions to modules.
- Hosts controls for:
  - Line width (1-132)
  - Paragraph indent
  - Justification mode (LEFT/JUSTIFY)
  - Font size application

### EditorModule.java (Editor Module)
- Applies HTML tags to selected text or inserts tags at cursor.
- Supports formatting tags: bold, italic, underline, superscript, subscript.
- Applies font size using span style.
- Implements auto-indentation on Enter key.
- Implements plain-text processing to HTML with:
  - Fixed line width
  - Optional full justification
  - First-line paragraph indentation

### ParserModule.java (Parser Module)
- Performs structural HTML tag validation.
- Detects mismatched and unexpected closing tags.
- Handles standard void tags.

### PreviewModule.java (Preview Module)
- Renders current HTML into WebView.
- Shows validation warning banner when parser reports issues.

### FileModule.java (File Module)
- Open file into a new tab.
- Save current tab.
- Import text file into current tab.
- Import image file into current tab by inserting an HTML img tag.

### StatisticsModule.java (Statistics Module)
- Computes and formats metrics:
  - Words
  - Lines
  - Characters
  - Paragraphs
  - Tag count

### TabManager.java (Tab Management)
- Creates and tracks editor tabs.
- Stores per-tab editor, preview, and file path state.
- Keeps live preview synchronized with editor text updates.

### ThemeManager.java (Theme Management)
- Applies dark/light stylesheet resources.
- Toggles theme at runtime.

## 4. Feature Coverage (SRS Mapping)

- FR1/FR2/FR7 (Editing, formatting, interaction): implemented in EditorModule + UIController.
- FR3 (Parsing + rendering): implemented in ParserModule + PreviewModule.
- FR4/FR5 (Open/save/import): implemented in FileModule.
- FR6/FR10 (Stats + display): implemented in StatisticsModule + UIController status bar.
- FR8 (Tab management): implemented in TabManager.
- FR9 (Theme management): implemented in ThemeManager.

Additional SRS points now covered:
- User-configurable line width between 1 and 132.
- Paragraph processing with optional full justification.
- Configurable first-line indentation.
- Superscript/subscript formatting.
- Font size formatting.
- Paragraph count in statistics.
- Image import support (predefined image formats).

## 5. How to Use

1. Start app with local build output:
   - Run ./build.sh
   - Run build/output/HtmlEditorFX/bin/HtmlEditorFX
2. Open or create a tab.
3. Type HTML or import text.
4. Use toolbar formatting buttons.
5. Use Process Text for plain text transformation.
6. Use Validate and Stats buttons.
7. Use File menu for:
   - New Tab
   - Open
   - Save
   - Import Text
   - Import Image

## 5.1 UI Experience

The interface is arranged to reduce friction for first-time use:
- The top area groups file actions, editing tools, and transformation controls.
- The center workspace is a split editor/preview layout.
- The bottom status bar shows the latest action or validation result.
- Tooltips explain the purpose of each formatting button.
- Keyboard shortcuts are available for faster navigation:
  - Ctrl+N new tab
  - Ctrl+O open
  - Ctrl+S save
  - Ctrl+I import text
  - Ctrl+Shift+I import image
  - Ctrl+R validate
  - Ctrl+G statistics
  - Ctrl+P process text
  - Ctrl+T toggle theme

The editor also shows a placeholder hint when a tab is empty.

## 6. Build and Packaging Scripts

### build.sh
- Compiles sources from src/main/java.
- Copies resources from src/main/resources.
- Creates app.jar.
- Builds a jpackage app-image in build/output/HtmlEditorFX.

### appimage.sh
- Uses jpackage output to create AppDir.
- Injects AppRun and desktop entry.
- Uses appimagetool to generate AppImage.
- Optional argument: destination directory for output AppImage.

Usage:

```bash
./appimage.sh
./appimage.sh release/my-release-folder
```

### release.sh
- Generates timestamped release folder.
- Runs build.sh.
- Runs appimage.sh with release destination.
- Produces final AppImage under release/HtmlEditorFX-<timestamp>/.

## 7. Image Import Details
- Supported import filter formats:
  - png
  - jpg
  - jpeg
  - gif
  - webp
  - bmp
- Inserted syntax example:

```html
<img src="file:///absolute/path/to/image.png" alt="image.png" />
```

## 8. Runtime Notes
- If src/main/resources/icon.png is missing, packaging uses a placeholder icon.
- AppImage build may show AppStream metadata warning; this does not block output generation.

## 9. Verification Performed
- Compilation succeeds via build.sh.
- AppImage packaging succeeds via release.sh.
- Versioned AppImage artifacts generated under release/.

## 10. Future Improvements
- Add dedicated app metadata file for AppStream compliance.
- Add unit tests for line-justification and parser edge cases.
- Add optional relative-path image embedding strategy for portability.
