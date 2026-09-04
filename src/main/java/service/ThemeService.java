package service;

import javafx.scene.Scene;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service managing application themes ("shades") and live theme switching on JavaFX scenes.
 */
public class ThemeService {

    public enum Theme {
        VSCODE_DARK("VS Code Dark+", "/themes/vscode-dark.css"),
        MONOKAI("Monokai Pro", "/themes/monokai.css"),
        DRACULA("Dracula", "/themes/dracula.css"),
        GITHUB_LIGHT("GitHub Light", "/themes/github-light.css"),
        CYBERPUNK("Cyberpunk Neon", "/themes/cyberpunk.css");

        private final String displayName;
        private final String cssPath;

        Theme(String displayName, String cssPath) {
            this.displayName = displayName;
            this.cssPath = cssPath;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getCssPath() {
            return cssPath;
        }
    }

    private Theme currentTheme = Theme.VSCODE_DARK;

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public void applyTheme(Scene scene, Theme theme) {
        if (scene == null || theme == null) return;
        this.currentTheme = theme;

        scene.getStylesheets().clear();
        URL cssResource = getClass().getResource(theme.getCssPath());
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        } else {
            System.err.println("Warning: Theme CSS not found: " + theme.getCssPath());
        }

        // Shared editor geometry is loaded after the theme so every color
        // scheme keeps the same clear, VS Code-like code-reading experience.
        URL editorCss = getClass().getResource("/themes/vscode-editor.css");
        if (editorCss != null) {
            scene.getStylesheets().add(editorCss.toExternalForm());
        }
    }

    public Map<String, Theme> getAllThemes() {
        Map<String, Theme> map = new LinkedHashMap<>();
        for (Theme t : Theme.values()) {
            map.put(t.getDisplayName(), t);
        }
        return map;
    }
}
