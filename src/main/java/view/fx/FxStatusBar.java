package view.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.codicons.Codicons;
import service.ThemeService;

import java.util.function.Consumer;

/**
 * Modern VS Code-styled Status Bar in JavaFX with Codicons.
 */
public class FxStatusBar extends HBox {

    private final Label syncStatusLabel;
    private final Label positionLabel;
    private final Label statsLabel;
    private final Label spacesLabel;
    private final Label encodingLabel;
    private final Label languageLabel;
    private final Label themeButton;

    private Consumer<ThemeService.Theme> onThemeSelected;

    public FxStatusBar(ThemeService themeService) {
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);
        setPadding(new Insets(3, 10, 3, 10));

        // Left items
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

        // Right items
        spacesLabel = new Label("Spaces: 4");
        spacesLabel.getStyleClass().add("status-item");

        encodingLabel = new Label("UTF-8");
        encodingLabel.getStyleClass().add("status-item");

        languageLabel = new Label("Plain Text");
        languageLabel.getStyleClass().add("status-item");

        themeButton = new Label(" " + themeService.getCurrentTheme().getDisplayName());
        themeButton.setGraphic(IconFactory.getIcon(Codicons.COLOR_MODE, 12));
        themeButton.getStyleClass().add("status-item");

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
            themeMenu.show(themeButton, e.getScreenX(), e.getScreenY() - 120);
        });

        getChildren().addAll(
                syncStatusLabel,
                positionLabel,
                statsLabel,
                spacer,
                spacesLabel,
                encodingLabel,
                languageLabel,
                themeButton
        );
    }

    public void updatePosition(int line, int col) {
        positionLabel.setText(String.format("Ln %d, Col %d", line, col));
    }

    public void updateStats(int lines, long chars) {
        statsLabel.setText(String.format("%d lines | %d chars", lines, chars));
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
}
