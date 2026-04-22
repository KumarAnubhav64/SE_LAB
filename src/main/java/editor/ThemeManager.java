package editor;

import javafx.scene.Scene;

public class ThemeManager {

    private boolean darkMode = true;

    public void applyTheme(Scene scene, Class<?> resourceScope) {
        if (scene == null || resourceScope == null) {
            return;
        }

        scene.getStylesheets().clear();
        String css = darkMode ? "/dark.css" : "/light.css";
        var cssUrl = resourceScope.getResource(css);

        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
    }

    public void toggleTheme(Scene scene, Class<?> resourceScope) {
        darkMode = !darkMode;
        applyTheme(scene, resourceScope);
    }

    public boolean isDarkMode() {
        return darkMode;
    }
}
