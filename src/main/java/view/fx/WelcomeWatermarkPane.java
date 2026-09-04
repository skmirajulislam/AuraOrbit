package view.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * VS Code-style Welcome Watermark displayed when no editor tabs are open.
 * Shows AuraOrbit branding, quick-action keyboard shortcut cards, and getting started cues.
 */
public class WelcomeWatermarkPane extends VBox {

    private final Runnable onNewFile;
    private final Runnable onOpenFile;
    private final Runnable onCommandPalette;
    private final Runnable onToggleTerminal;
    private final Runnable onToggleAi;

    public WelcomeWatermarkPane(
            Runnable onNewFile,
            Runnable onOpenFile,
            Runnable onCommandPalette,
            Runnable onToggleTerminal,
            Runnable onToggleAi
    ) {
        this.onNewFile = onNewFile;
        this.onOpenFile = onOpenFile;
        this.onCommandPalette = onCommandPalette;
        this.onToggleTerminal = onToggleTerminal;
        this.onToggleAi = onToggleAi;

        setAlignment(Pos.CENTER);
        setSpacing(24);
        setPadding(new Insets(40));
        getStyleClass().add("welcome-watermark-pane");

        // 1. Branding Header: Icon + Name + Subtitle
        VBox brandBox = new VBox(8);
        brandBox.setAlignment(Pos.CENTER);

        FontIcon logoIcon = IconFactory.getIcon(Codicons.ROCKET, 48, "#007acc");
        logoIcon.getStyleClass().add("welcome-watermark-icon");

        Label titleLabel = new Label("AuraOrbit");
        titleLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: -text-primary; -fx-letter-spacing: 0.5px;");

        Label subtitleLabel = new Label("Lightweight High-Performance Code Studio & AI Workspace");
        subtitleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -text-secondary;");

        brandBox.getChildren().addAll(logoIcon, titleLabel, subtitleLabel);

        // 2. Quick Action Shortcuts List
        boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        String cmdKey = isMac ? "⌘" : "Ctrl+";
        String shiftKey = isMac ? "⇧" : "Shift+";

        VBox actionsBox = new VBox(6);
        actionsBox.setAlignment(Pos.CENTER);
        actionsBox.setMaxWidth(380);

        actionsBox.getChildren().addAll(
                createActionRow(Codicons.TERMINAL, "Show All Commands", cmdKey + "P", onCommandPalette),
                createActionRow(Codicons.NEW_FILE, "New File", cmdKey + "N", onNewFile),
                createActionRow(Codicons.FOLDER_OPENED, "Open File...", cmdKey + "O", onOpenFile),
                createActionRow(Codicons.TERMINAL, "Toggle Terminal", "Ctrl+`", onToggleTerminal),
                createActionRow(Codicons.HUBOT, "AuraOrbit Copilot", shiftKey + cmdKey + "A", onToggleAi)
        );

        getChildren().addAll(brandBox, actionsBox);
    }

    private HBox createActionRow(Codicons iconCode, String labelText, String shortcutText, Runnable action) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("welcome-action-row");
        row.setPadding(new Insets(6, 14, 6, 14));

        FontIcon icon = IconFactory.getIcon(iconCode, 14, "#969696");
        icon.setMouseTransparent(true);

        Label text = new Label(labelText);
        text.setStyle("-fx-font-size: 13px; -fx-text-fill: -text-primary;");
        text.setMouseTransparent(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        spacer.setMouseTransparent(true);

        Label shortcutBadge = new Label(shortcutText);
        shortcutBadge.getStyleClass().add("shortcut-badge");
        shortcutBadge.setMouseTransparent(true);

        row.getChildren().addAll(icon, text, spacer, shortcutBadge);
        row.setPickOnBounds(true);

        if (action != null) {
            row.setOnMouseClicked(e -> action.run());
        }

        return row;
    }
}
