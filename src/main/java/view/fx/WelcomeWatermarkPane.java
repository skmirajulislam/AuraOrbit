package view.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * VS Code-style Welcome Watermark displayed when no editor tabs are open.
 * Shows AuraOrbit branding, quick-action keyboard shortcut cards, and Antigravity-style empty space context menu.
 */
public class WelcomeWatermarkPane extends VBox {

    private final Runnable onNewFile;
    private final Runnable onOpenFile;
    private final Runnable onCommandPalette;
    private final Runnable onToggleTerminal;
    private final Runnable onToggleAi;

    private Runnable onNewTerminal;
    private Runnable onSplitUp;
    private Runnable onSplitDown;
    private Runnable onSplitLeft;
    private Runnable onSplitRight;
    private Runnable onNewWindow;
    private Runnable onLockGroup;
    private boolean isGroupLocked = false;
    private ContextMenu emptySpaceContextMenu;

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
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        getStyleClass().add("welcome-watermark-pane");

        setupEmptySpaceContextMenu();

        // Right-click / context menu requested anywhere on the empty space
        setOnContextMenuRequested(e -> {
            emptySpaceContextMenu.show(this, e.getScreenX(), e.getScreenY());
            e.consume();
        });

        setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                emptySpaceContextMenu.show(this, e.getScreenX(), e.getScreenY());
                e.consume();
            } else if (e.getButton() == MouseButton.PRIMARY) {
                if (emptySpaceContextMenu.isShowing()) {
                    emptySpaceContextMenu.hide();
                }
                if (e.getClickCount() == 2) {
                    // Double clicking empty space creates new file (matches VS Code / Antigravity)
                    if (onNewFile != null) onNewFile.run();
                    e.consume();
                }
            }
        });

        // 1. Branding Header: Icon + Name + Subtitle
        VBox brandBox = new VBox(8);
        brandBox.setAlignment(Pos.CENTER);

        FontIcon logoIcon = IconFactory.getIcon(Codicons.ROCKET, 48);
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
                createActionRow(Codicons.TERMINAL, "Show All Commands", cmdKey + "P", this.onCommandPalette),
                createActionRow(Codicons.NEW_FILE, "New File", cmdKey + "N", this.onNewFile),
                createActionRow(Codicons.FOLDER_OPENED, "Open File...", cmdKey + "O", this.onOpenFile),
                createActionRow(Codicons.TERMINAL, "Toggle Terminal", "Ctrl+`", this.onToggleTerminal),
                createActionRow(Codicons.HUBOT, "AuraOrbit Copilot", shiftKey + cmdKey + "A", this.onToggleAi)
        );

        getChildren().addAll(brandBox, actionsBox);
    }

    public Runnable getOnNewFile() { return onNewFile; }
    public Runnable getOnOpenFile() { return onOpenFile; }
    public Runnable getOnCommandPalette() { return onCommandPalette; }
    public Runnable getOnToggleTerminal() { return onToggleTerminal; }
    public Runnable getOnToggleAi() { return onToggleAi; }

    private HBox createActionRow(Codicons iconCode, String labelText, String shortcutText, Runnable action) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("welcome-action-row");
        row.setPadding(new Insets(6, 14, 6, 14));

        FontIcon icon = IconFactory.getIcon(iconCode, 14);
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

    private void setupEmptySpaceContextMenu() {
        emptySpaceContextMenu = new ContextMenu();
        emptySpaceContextMenu.getStyleClass().add("editor-context-menu");

        // 1. New Text File (Cmd+N / Ctrl+N)
        MenuItem newFileItem = new MenuItem("New Text File");
        newFileItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
        newFileItem.setOnAction(e -> {
            if (onNewFile != null) onNewFile.run();
        });

        // 2. Open File... (Cmd+O / Ctrl+O)
        MenuItem openFileItem = new MenuItem("Open File...");
        openFileItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
        openFileItem.setOnAction(e -> {
            if (onOpenFile != null) onOpenFile.run();
        });

        // 3. New Terminal
        MenuItem newTerminalItem = new MenuItem("New Terminal");
        newTerminalItem.setOnAction(e -> {
            if (onNewTerminal != null) {
                onNewTerminal.run();
            } else if (onToggleTerminal != null) {
                onToggleTerminal.run();
            }
        });

        // 4. Split Up (Cmd+K Cmd+\)
        MenuItem splitUpItem = new MenuItem("Split Up");
        splitUpItem.setOnAction(e -> {
            if (onSplitUp != null) onSplitUp.run();
        });

        // 5. Split Down
        MenuItem splitDownItem = new MenuItem("Split Down");
        splitDownItem.setOnAction(e -> {
            if (onSplitDown != null) onSplitDown.run();
        });

        // 6. Split Left
        MenuItem splitLeftItem = new MenuItem("Split Left");
        splitLeftItem.setOnAction(e -> {
            if (onSplitLeft != null) onSplitLeft.run();
        });

        // 7. Split Right
        MenuItem splitRightItem = new MenuItem("Split Right");
        splitRightItem.setOnAction(e -> {
            if (onSplitRight != null) onSplitRight.run();
        });

        // 8. New Window
        MenuItem newWindowItem = new MenuItem("New Window");
        newWindowItem.setOnAction(e -> {
            if (onNewWindow != null) onNewWindow.run();
        });

        // 9. Lock Group
        MenuItem lockGroupItem = new MenuItem("Lock Group");
        lockGroupItem.setOnAction(e -> {
            isGroupLocked = !isGroupLocked;
            lockGroupItem.setText(isGroupLocked ? "Unlock Group" : "Lock Group");
            if (onLockGroup != null) onLockGroup.run();
        });

        emptySpaceContextMenu.getItems().addAll(
                newFileItem,
                openFileItem,
                newTerminalItem,
                new SeparatorMenuItem(),
                splitUpItem,
                splitDownItem,
                splitLeftItem,
                splitRightItem,
                new SeparatorMenuItem(),
                newWindowItem,
                new SeparatorMenuItem(),
                lockGroupItem
        );
    }

    public void setAdditionalActions(
            Runnable onNewTerminal,
            Runnable onSplitUp,
            Runnable onSplitDown,
            Runnable onSplitLeft,
            Runnable onSplitRight,
            Runnable onNewWindow,
            Runnable onLockGroup
    ) {
        this.onNewTerminal = onNewTerminal;
        this.onSplitUp = onSplitUp;
        this.onSplitDown = onSplitDown;
        this.onSplitLeft = onSplitLeft;
        this.onSplitRight = onSplitRight;
        this.onNewWindow = onNewWindow;
        this.onLockGroup = onLockGroup;
    }

    public ContextMenu getEmptySpaceContextMenu() {
        return emptySpaceContextMenu;
    }

    public boolean isGroupLocked() {
        return isGroupLocked;
    }
}
