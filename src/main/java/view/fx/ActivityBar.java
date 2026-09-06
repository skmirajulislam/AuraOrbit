package view.fx;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.codicons.Codicons;

import java.util.function.Consumer;

/**
 * Modern VS Code Activity Bar (Left icon strip) with official Codicons.
 * Includes:
 * - Explorer, Search, Source Control (with modified badge), AI Copilot, Templates, Terminal
 * - Live Share indicator
 * - Account Profile
 * - Settings Gear (Preferences menu)
 */
public class ActivityBar extends VBox {

    public enum Panel {
        EXPLORER,
        SEARCH,
        SOURCE_CONTROL,
        AI_COPILOT,
        TEMPLATES,
        TERMINAL
    }

    private final Button explorerBtn;
    private final Button searchBtn;
    private final Button sourceControlBtn;
    private final Label sourceControlBadgeLabel;
    private final Button aiBtn;
    private final Button templatesBtn;
    private final Button terminalBtn;

    private final Button liveShareBtn;
    private final Button accountBtn;
    private final Button settingsBtn;

    private Panel activePanel = Panel.EXPLORER;
    private Consumer<Panel> onPanelToggled;
    private Runnable onLiveShareAction;
    private Runnable onAccountAction;
    private Runnable onSettingsAction;

    public ActivityBar() {
        getStyleClass().add("activity-bar");
        setAlignment(Pos.TOP_CENTER);
        setPrefWidth(48);
        setSpacing(2);

        // 1. Primary Feature Icons (Top)
        explorerBtn = createIconButton(Codicons.FILES, "Explorer (Cmd/Ctrl+Shift+E)", Panel.EXPLORER);
        searchBtn = createIconButton(Codicons.SEARCH, "Search in Document (Cmd/Ctrl+F)", Panel.SEARCH);

        // Source Control with badge
        sourceControlBtn = createIconButton(Codicons.SOURCE_CONTROL, "Source Control (Cmd/Ctrl+Shift+G)", Panel.SOURCE_CONTROL);
        sourceControlBadgeLabel = new Label();
        sourceControlBadgeLabel.setStyle("-fx-background-color: #007acc; -fx-text-fill: white; -fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 0 4 0 4; -fx-background-radius: 8;");
        sourceControlBadgeLabel.setVisible(false);
        sourceControlBadgeLabel.setMouseTransparent(true);
        StackPane sourceControlWrapper = new StackPane(sourceControlBtn, sourceControlBadgeLabel);
        StackPane.setAlignment(sourceControlBadgeLabel, Pos.BOTTOM_RIGHT);

        aiBtn = createIconButton(Codicons.HUBOT, "AI Copilot & Assistant Studio (Cmd/Ctrl+Shift+A)", Panel.AI_COPILOT);
        templatesBtn = createIconButton(Codicons.PACKAGE, "Templates & Scaffolds", Panel.TEMPLATES);
        terminalBtn = createIconButton(Codicons.TERMINAL, "Terminal (Ctrl+`)", Panel.TERMINAL);

        // 2. Spacer
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // 3. System & Preferences Actions (Bottom)
        liveShareBtn = createActionButton(Codicons.RADIO_TOWER, "Live Share Collaboration (WAN / Cloudflare)");
        accountBtn = createActionButton(Codicons.ACCOUNT, "Accounts");
        settingsBtn = createActionButton(Codicons.SETTINGS_GEAR, "Manage (Preferences, Settings, Themes)");

        liveShareBtn.setOnAction(e -> {
            if (onLiveShareAction != null) onLiveShareAction.run();
        });

        accountBtn.setOnAction(e -> {
            if (onAccountAction != null) onAccountAction.run();
        });

        settingsBtn.setOnAction(e -> {
            if (onSettingsAction != null) onSettingsAction.run();
        });

        getChildren().addAll(
                explorerBtn,
                searchBtn,
                sourceControlWrapper,
                aiBtn,
                templatesBtn,
                terminalBtn,
                spacer,
                liveShareBtn,
                accountBtn,
                settingsBtn
        );

        setActivePanel(Panel.EXPLORER);
    }

    private Button createIconButton(Codicons codicon, String tooltip, Panel panel) {
        Button btn = new Button();
        btn.setGraphic(IconFactory.getIcon(codicon, 20));
        btn.getStyleClass().add("activity-button");
        btn.setTooltip(new Tooltip(tooltip));
        btn.setOnAction(e -> {
            if (activePanel == panel) {
                // Toggle off
                setActivePanel(null, true);
            } else {
                setActivePanel(panel, true);
            }
        });
        return btn;
    }

    private Button createActionButton(Codicons codicon, String tooltip) {
        Button btn = new Button();
        btn.setGraphic(IconFactory.getIcon(codicon, 18));
        btn.getStyleClass().add("activity-button");
        btn.setTooltip(new Tooltip(tooltip));
        return btn;
    }

    public void setSourceControlBadge(int count) {
        if (count > 0) {
            sourceControlBadgeLabel.setText(String.valueOf(count));
            sourceControlBadgeLabel.setVisible(true);
        } else {
            sourceControlBadgeLabel.setVisible(false);
        }
    }

    public void setActivePanel(Panel panel) {
        setActivePanel(panel, false);
    }

    public void setActivePanel(Panel panel, boolean notifyListener) {
        this.activePanel = panel;
        explorerBtn.getStyleClass().remove("active");
        searchBtn.getStyleClass().remove("active");
        sourceControlBtn.getStyleClass().remove("active");
        aiBtn.getStyleClass().remove("active");
        templatesBtn.getStyleClass().remove("active");
        terminalBtn.getStyleClass().remove("active");

        if (panel == Panel.EXPLORER) explorerBtn.getStyleClass().add("active");
        else if (panel == Panel.SEARCH) searchBtn.getStyleClass().add("active");
        else if (panel == Panel.SOURCE_CONTROL) sourceControlBtn.getStyleClass().add("active");
        else if (panel == Panel.AI_COPILOT) aiBtn.getStyleClass().add("active");
        else if (panel == Panel.TEMPLATES) templatesBtn.getStyleClass().add("active");
        else if (panel == Panel.TERMINAL) terminalBtn.getStyleClass().add("active");

        if (notifyListener && onPanelToggled != null) {
            onPanelToggled.accept(panel);
        }
    }

    public Panel getActivePanel() {
        return activePanel;
    }

    public void setOnPanelToggled(Consumer<Panel> onPanelToggled) {
        this.onPanelToggled = onPanelToggled;
    }

    public void setOnLiveShareAction(Runnable onLiveShareAction) {
        this.onLiveShareAction = onLiveShareAction;
    }

    public void setOnAccountAction(Runnable onAccountAction) {
        this.onAccountAction = onAccountAction;
    }

    public void setOnSettingsAction(Runnable onSettingsAction) {
        this.onSettingsAction = onSettingsAction;
    }

    // Retain legacy method for backward compatibility
    public void setOnThemeAction(Runnable onThemeAction) {
        // Handled via onSettingsAction menu
    }

    public void setOnInfoAction(Runnable onInfoAction) {
        // Handled via onSettingsAction menu
    }
}
