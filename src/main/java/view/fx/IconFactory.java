package view.fx;

import javafx.scene.paint.Color;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Official VS Code Codicons Factory for JavaFX.
 * Provides high-DPI vector icons matching Visual Studio Code aesthetics.
 */
public class IconFactory {

    public static FontIcon getIcon(Codicons codicon, int size) {
        FontIcon icon = new FontIcon(codicon);
        icon.setIconSize(size);
        icon.getStyleClass().add("codicon");
        return icon;
    }

    public static FontIcon getIcon(Codicons codicon, int size, String colorHex) {
        FontIcon icon = getIcon(codicon, size);
        if (colorHex != null) {
            icon.setIconColor(Color.web(colorHex));
        }
        return icon;
    }

    public static FontIcon getFileIcon(String fileName, int size) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".java")) {
            return getIcon(Codicons.FILE_CODE, size, "#e76f51");
        } else if (lower.endsWith(".class")) {
            return getIcon(Codicons.FILE_BINARY, size, "#e76f51");
        } else if (lower.endsWith(".json")) {
            return getIcon(Codicons.JSON, size, "#f4a261");
        } else if (lower.endsWith(".md")) {
            return getIcon(Codicons.MARKDOWN, size, "#4ea8de");
        } else if (lower.endsWith(".xml") || lower.endsWith(".html")) {
            return getIcon(Codicons.FILE_CODE, size, "#e9c46a");
        } else if (lower.endsWith(".css")) {
            return getIcon(Codicons.PAINTCAN, size, "#2a9d8f");
        } else if (lower.endsWith(".sh") || lower.endsWith(".zsh") || lower.endsWith(".bash")) {
            return getIcon(Codicons.TERMINAL, size, "#52b788");
        } else {
            return getIcon(Codicons.FILE, size, "#90a4ae");
        }
    }

    public static FontIcon getFolderIcon(boolean expanded, int size) {
        return expanded ? getIcon(Codicons.FOLDER_OPENED, size, "#d4af37") : getIcon(Codicons.FOLDER, size, "#d4af37");
    }
}
