package view.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.javafx.FontIcon;
import service.ThemeService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Modern VS Code-styled Status Bar in JavaFX with Codicons.
 * Includes:
 * - Git branch indicator with Codicons.SOURCE_CONTROL
 * - Error & Warning counters
 * - Live save/modified state
 * - Cursor position & document metrics
 * - Indentation, UTF-8 encoding, line endings (LF), language mode
 * - Theme selector
 */
public class FxStatusBar extends HBox {

    private final Label gitBranchLabel;
    private final Label syncIconLabel;
    private final Label problemsLabel;
    private final Label syncStatusLabel;
    private final Label positionLabel;
    private final Label statsLabel;
    private final Label spacesLabel;
    private final Label encodingLabel;
    private final Label lineEndingsLabel;
    private final Label languageLabel;
    private final Label themeButton;
    private final Label bellLabel;

    private Consumer<ThemeService.Theme> onThemeSelected;

    public FxStatusBar(ThemeService themeService) {
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);
        setPadding(new Insets(2, 10, 2, 10));

        // 1. Left items: Git Branch + Sync + Problems
        gitBranchLabel = new Label(" " + detectGitBranch());
        gitBranchLabel.setGraphic(IconFactory.getIcon(Codicons.SOURCE_CONTROL, 12, "#ffffff"));
        gitBranchLabel.getStyleClass().add("status-item");
        gitBranchLabel.setTooltip(new Tooltip("Current Git Branch"));

        syncIconLabel = new Label();
        syncIconLabel.setGraphic(IconFactory.getIcon(Codicons.SYNC, 11, "#ffffff"));
        syncIconLabel.getStyleClass().add("status-item");
        syncIconLabel.setTooltip(new Tooltip("AuraOrbit Sync Status"));

        problemsLabel = new Label(" 0  0");
        HBox problemGraphic = new HBox(4);
        problemGraphic.setAlignment(Pos.CENTER_LEFT);
        problemGraphic.getChildren().addAll(
                IconFactory.getIcon(Codicons.ERROR, 11, "#ffffff"),
                IconFactory.getIcon(Codicons.WARNING, 11, "#ffffff")
        );
        problemsLabel.setGraphic(problemGraphic);
        problemsLabel.getStyleClass().add("status-item");
        problemsLabel.setTooltip(new Tooltip("No problems detected"));

        syncStatusLabel = new Label(" Saved");
        syncStatusLabel.setGraphic(IconFactory.getIcon(Codicons.CHECK, 12, "#ffffff"));
        syncStatusLabel.getStyleClass().add("status-item");

        positionLabel = new Label("Ln 1, Col 1");
        positionLabel.getStyleClass().add("status-item");

        statsLabel = new Label("0 lines | 0 chars");
        statsLabel.getStyleClass().add("status-item");

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 2. Right items: Spaces, Encoding, LF, Language, Theme, Bell
        spacesLabel = new Label("Spaces: 4");
        spacesLabel.getStyleClass().add("status-item");

        encodingLabel = new Label("UTF-8");
        encodingLabel.getStyleClass().add("status-item");

        lineEndingsLabel = new Label("LF");
        lineEndingsLabel.getStyleClass().add("status-item");

        languageLabel = new Label("Plain Text");
        languageLabel.getStyleClass().add("status-item");

        themeButton = new Label(" " + themeService.getCurrentTheme().getDisplayName());
        themeButton.setGraphic(IconFactory.getIcon(Codicons.COLOR_MODE, 12, "#ffffff"));
        themeButton.getStyleClass().add("status-item");
        themeButton.setTooltip(new Tooltip("Switch Color Theme"));

        bellLabel = new Label();
        bellLabel.setGraphic(IconFactory.getIcon(Codicons.BELL, 11, "#ffffff"));
        bellLabel.getStyleClass().add("status-item");
        bellLabel.setTooltip(new Tooltip("Notifications"));

        // Setup Theme Switcher Context Menu
        ContextMenu themeMenu = new ContextMenu();
        for (ThemeService.Theme theme : ThemeService.Theme.values()) {
            MenuItem item = new MenuItem(theme.getDisplayName());
            item.setOnAction(e -> {
                themeButton.setText(" " + theme.getDisplayName());
                if (onThemeSelected != null) {
                    onThemeSelected.accept(theme);
                }
            });
            themeMenu.getItems().add(item);
        }

        themeButton.setOnMouseClicked(e -> {
            themeMenu.show(themeButton, e.getScreenX(), e.getScreenY() - 130);
        });

        getChildren().addAll(
                gitBranchLabel,
                syncIconLabel,
                problemsLabel,
                syncStatusLabel,
                positionLabel,
                statsLabel,
                spacer,
                spacesLabel,
                encodingLabel,
                lineEndingsLabel,
                languageLabel,
                themeButton,
                bellLabel
        );
    }

    public void updatePosition(int line, int col) {
        positionLabel.setText(String.format("Ln %d, Col %d", line, col));
    }

    public void updateStats(int lines, long chars) {
        statsLabel.setText(String.format("%d lines | %d chars", lines, chars));
    }

    public void setLineEndings(String lineEndings) {
        lineEndingsLabel.setText(lineEndings != null ? lineEndings : "LF");
    }

    public void setIndentation(String indentation) {
        spacesLabel.setText(indentation != null ? indentation : "Spaces: 4");
    }

    public void setEncoding(String encoding) {
        encodingLabel.setText(encoding != null ? encoding : "UTF-8");
    }

    public void setProblems(int errors, int warnings) {
        problemsLabel.setText(String.format(" %d  %d", errors, warnings));
        if (errors > 0) {
            problemsLabel.setStyle("-fx-text-fill: #f48771;");
        } else if (warnings > 0) {
            problemsLabel.setStyle("-fx-text-fill: #cca700;");
        } else {
            problemsLabel.setStyle("");
        }
    }

    public void setOnLineEndingsClicked(Runnable onLineEndingsClicked) {
        lineEndingsLabel.setOnMouseClicked(e -> {
            if (onLineEndingsClicked != null) onLineEndingsClicked.run();
        });
    }

    public void setOnIndentationClicked(Runnable onIndentationClicked) {
        spacesLabel.setOnMouseClicked(e -> {
            if (onIndentationClicked != null) onIndentationClicked.run();
        });
    }

    public void setOnEncodingClicked(Runnable onEncodingClicked) {
        encodingLabel.setOnMouseClicked(e -> {
            if (onEncodingClicked != null) onEncodingClicked.run();
        });
    }

    public void setOnGitBranchClicked(Runnable onGitBranchClicked) {
        gitBranchLabel.setOnMouseClicked(e -> {
            if (onGitBranchClicked != null) onGitBranchClicked.run();
        });
    }

    public void setOnProblemsClicked(Runnable onProblemsClicked) {
        problemsLabel.setOnMouseClicked(e -> {
            if (onProblemsClicked != null) onProblemsClicked.run();
        });
    }

    public void setOnBellClicked(Runnable onBellClicked) {
        bellLabel.setOnMouseClicked(e -> {
            if (onBellClicked != null) onBellClicked.run();
        });
    }

    public void setModified(boolean isModified) {
        if (isModified) {
            syncStatusLabel.setText(" Modified");
            syncStatusLabel.setGraphic(IconFactory.getIcon(Codicons.CIRCLE_FILLED, 8, "#ffd166"));
            syncStatusLabel.setStyle("-fx-text-fill: #ffd166; -fx-font-weight: bold;");
        } else {
            syncStatusLabel.setText(" Saved");
            syncStatusLabel.setGraphic(IconFactory.getIcon(Codicons.CHECK, 12, "#ffffff"));
            syncStatusLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: normal;");
        }
    }

    public void setLanguage(String language) {
        languageLabel.setText(language);
    }

    public void setOnThemeSelected(Consumer<ThemeService.Theme> onThemeSelected) {
        this.onThemeSelected = onThemeSelected;
    }

    public void updateGitBranch(Path workspaceDir) {
        if (workspaceDir != null) {
            String branch = detectGitBranch(workspaceDir.toFile());
            gitBranchLabel.setText(" " + branch);
        }
    }

    private static String detectGitBranch() {
        return detectGitBranch(new File("."));
    }

    private static String detectGitBranch(File dir) {
        try {
            File current = dir.getAbsoluteFile();
            while (current != null) {
                File gitHead = new File(current, ".git/HEAD");
                if (gitHead.exists() && gitHead.isFile()) {
                    String headContent = Files.readString(gitHead.toPath()).trim();
                    if (headContent.startsWith("ref: refs/heads/")) {
                        return headContent.substring("ref: refs/heads/".length()).trim();
                    } else if (!headContent.isEmpty()) {
                        return headContent.substring(0, Math.min(7, headContent.length()));
                    }
                }
                current = current.getParentFile();
            }
        } catch (Exception ignored) {}
        return "master";
    }
}
