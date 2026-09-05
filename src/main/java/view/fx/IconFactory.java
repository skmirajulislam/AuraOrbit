package view.fx;

import javafx.scene.paint.Color;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.devicons.Devicons;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Official VS Code & Devicons Vector Icon Factory for JavaFX.
 * Provides high-DPI vector icons matching Visual Studio Code aesthetics.
 * Renders in native theme style without hardcoded color overrides.
 */
public class IconFactory {

    public static FontIcon getIcon(Ikon ikon, int size) {
        FontIcon icon = new FontIcon(ikon);
        icon.setIconSize(size);
        icon.getStyleClass().add("codicon");
        return icon;
    }

    public static FontIcon getIcon(Ikon ikon, int size, String colorHex) {
        FontIcon icon = getIcon(ikon, size);
        if (colorHex != null && !colorHex.isBlank()) {
            icon.setIconColor(Color.web(colorHex));
        }
        return icon;
    }

    public static FontIcon getIcon(Codicons codicon, int size) {
        return getIcon((Ikon) codicon, size);
    }

    public static FontIcon getIcon(Codicons codicon, int size, String colorHex) {
        return getIcon((Ikon) codicon, size, colorHex);
    }

    /**
     * Resolves file-specific vector icons matching VS Code file-icons glyphs.
     * Rendered in native theme style ("as it is") without forced color overrides.
     */
    public static FontIcon getFileIcon(String fileName, int size) {
        if (fileName == null || fileName.isBlank()) {
            return getIcon(Codicons.FILE, size);
        }

        String name = fileName.toLowerCase().trim();

        // 1. Exact Filename Matching
        if (name.equals(".gitignore") || name.equals(".gitattributes") || name.equals(".gitmodules")) {
            return getIcon(Devicons.GIT, size);
        }
        if (name.equals("code_of_conduct.md") || name.equals("code_of_conduct")) {
            return getIcon(Codicons.VERIFIED, size);
        }
        if (name.startsWith("license") || name.equals("copying")) {
            return getIcon(Codicons.LAW, size);
        }
        if (name.equals("security.md") || name.equals("security")) {
            return getIcon(Codicons.SHIELD, size);
        }
        if (name.equals("pom.xml")) {
            return getIcon(Codicons.TOOLS, size);
        }
        if (name.startsWith("readme")) {
            return getIcon(Codicons.BOOK, size);
        }
        if (name.equals("package.json") || name.equals("package-lock.json")) {
            return getIcon(Devicons.NPM, size);
        }
        if (name.equals("dockerfile") || name.startsWith("docker-compose")) {
            return getIcon(Devicons.DOCKER, size);
        }
        if (name.equals("requirements.txt") || name.equals("pipfile")) {
            return getIcon(Devicons.PYTHON, size);
        }
        if (name.equals("makefile") || name.equals("cmakelists.txt")) {
            return getIcon(Codicons.TOOLS, size);
        }
        if (name.equals("tsconfig.json") || name.equals("jsconfig.json")) {
            return getIcon(Codicons.FILE_CODE, size);
        }
        if (name.equals("favicon.ico")) {
            return getIcon(Codicons.FILE_MEDIA, size);
        }
        if (name.startsWith(".env")) {
            return getIcon(Codicons.SYMBOL_KEY, size);
        }

        // 2. Extension-based matching with genuine Devicons/Codicons
        if (name.endsWith(".py") || name.endsWith(".pyw") || name.endsWith(".ipynb")) {
            return getIcon(Devicons.PYTHON, size);
        }
        if (name.endsWith(".java")) {
            return getIcon(Devicons.JAVA, size);
        }
        if (name.endsWith(".class") || name.endsWith(".jar")) {
            return getIcon(Codicons.FILE_BINARY, size);
        }
        if (name.endsWith(".js") || name.endsWith(".mjs") || name.endsWith(".cjs")) {
            return getIcon(Devicons.JAVASCRIPT_BADGE, size);
        }
        if (name.endsWith(".ts")) {
            return getIcon(Codicons.FILE_CODE, size);
        }
        if (name.endsWith(".jsx") || name.endsWith(".tsx")) {
            return getIcon(Devicons.REACT, size);
        }
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            return getIcon(Devicons.HTML5, size);
        }
        if (name.endsWith(".css")) {
            return getIcon(Devicons.CSS3, size);
        }
        if (name.endsWith(".scss") || name.endsWith(".sass")) {
            return getIcon(Devicons.SASS, size);
        }
        if (name.endsWith(".less")) {
            return getIcon(Devicons.LESS, size);
        }
        if (name.endsWith(".json")) {
            return getIcon(Codicons.JSON, size);
        }
        if (name.endsWith(".xml")) {
            return getIcon(Codicons.FILE_CODE, size);
        }
        if (name.endsWith(".yml") || name.endsWith(".yaml")) {
            return getIcon(Codicons.SYMBOL_KEY, size);
        }
        if (name.endsWith(".md") || name.endsWith(".markdown")) {
            return getIcon(Codicons.MARKDOWN, size);
        }
        if (name.endsWith(".sh") || name.endsWith(".bash") || name.endsWith(".zsh")
                || name.endsWith(".fish") || name.endsWith(".ps1") || name.endsWith(".bat") || name.endsWith(".cmd")) {
            return getIcon(Codicons.TERMINAL, size);
        }
        if (name.endsWith(".cpp") || name.endsWith(".cxx") || name.endsWith(".cc") || name.endsWith(".hpp")) {
            return getIcon(Codicons.FILE_CODE, size);
        }
        if (name.endsWith(".c") || name.endsWith(".h")) {
            return getIcon(Codicons.FILE_CODE, size);
        }
        if (name.endsWith(".cs")) {
            return getIcon(Codicons.FILE_CODE, size);
        }
        if (name.endsWith(".rs")) {
            return getIcon(Devicons.RUST, size);
        }
        if (name.endsWith(".go")) {
            return getIcon(Devicons.GO, size);
        }
        if (name.endsWith(".php")) {
            return getIcon(Devicons.PHP, size);
        }
        if (name.endsWith(".rb")) {
            return getIcon(Devicons.RUBY, size);
        }
        if (name.endsWith(".swift")) {
            return getIcon(Devicons.SWIFT, size);
        }
        if (name.endsWith(".kt") || name.endsWith(".kts")) {
            return getIcon(Devicons.JAVA, size);
        }
        if (name.endsWith(".scala")) {
            return getIcon(Devicons.SCALA, size);
        }
        if (name.endsWith(".groovy") || name.endsWith(".gradle")) {
            return getIcon(Devicons.GROOVY, size);
        }
        if (name.endsWith(".sql") || name.endsWith(".db") || name.endsWith(".sqlite")) {
            return getIcon(Devicons.MYSQL, size);
        }
        if (name.endsWith(".dockerfile")) {
            return getIcon(Devicons.DOCKER, size);
        }
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".svg") || name.endsWith(".ico")
                || name.endsWith(".webp") || name.endsWith(".bmp")) {
            return getIcon(Codicons.FILE_MEDIA, size);
        }
        if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg")
                || name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".avi") || name.endsWith(".mkv")) {
            return getIcon(Codicons.FILE_MEDIA, size);
        }
        if (name.endsWith(".zip") || name.endsWith(".tar") || name.endsWith(".gz")
                || name.endsWith(".tgz") || name.endsWith(".7z") || name.endsWith(".rar")) {
            return getIcon(Codicons.FILE_ZIP, size);
        }
        if (name.endsWith(".pdf")) {
            return getIcon(Codicons.FILE_PDF, size);
        }
        if (name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".csv") || name.endsWith(".tsv")) {
            return getIcon(Codicons.FILE, size);
        }

        return getIcon(Codicons.FILE, size);
    }

    public static FontIcon getFolderIcon(boolean expanded, int size) {
        return expanded ? getIcon(Codicons.FOLDER_OPENED, size) : getIcon(Codicons.FOLDER, size);
    }

    public static FontIcon getFolderIcon(String folderName, boolean expanded, int size) {
        return getFolderIcon(expanded, size);
    }
}
