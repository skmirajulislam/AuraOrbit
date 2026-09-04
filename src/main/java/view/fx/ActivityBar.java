package view.fx;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.codicons.Codicons;

import java.util.function.Consumer;

/**
 * Modern VS Code Activity Bar (Left icon strip) with official Codicons.
 */
public class ActivityBar extends VBox {

    public enum Panel {
        EXPLORER,
        TEMPLATES,
        SEARCH,
        AI_COPILOT,
        COLLABORATION,
        TERMINAL
    }

    private final Button explorerBtn;
    private final Button templatesBtn;
    private final Button searchBtn;
    private final Button aiBtn;
    private final Button collaborationBtn;
    private final Button terminalBtn;
    private final Button themeBtn;
    private final Button infoBtn;

    private Panel activePanel = Panel.EXPLORER;
    private Consumer<Panel> onPanelToggled;
    private Runnable onThemeAction;
    private Runnable onInfoAction;

    public ActivityBar() {
        getStyleClass().add("activity-bar");
        setAlignment(Pos.TOP_CENTER);
        setPrefWidth(48);
        setSpacing(4);

        explorerBtn = createIconButton(Codicons.FILES, "Explorer (Cmd/Ctrl+Shift+E)", Panel.EXPLORER);
        templatesBtn = createIconButton(Codicons.PACKAGE, "Templates & Scaffolds", Panel.TEMPLATES);
        searchBtn = createIconButton(Codicons.SEARCH, "Search in Document (Cmd/Ctrl+F)", Panel.SEARCH);
        aiBtn = createIconButton(Codicons.HUBOT, "AI Copilot & Assistant Studio (Cmd/Ctrl+Shift+A)", Panel.AI_COPILOT);
        collaborationBtn = createIconButton(Codicons.LIVE_SHARE, "Collaboration: host or join a workspace", Panel.COLLABORATION);
        terminalBtn = createIconButton(Codicons.TERMINAL, "Terminal (Ctrl+`)", Panel.TERMINAL);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        themeBtn = createActionButton(Codicons.COLOR_MODE, "Change Theme / Shade");
        infoBtn = createActionButton(Codicons.INFO, "About & Shortcuts");

        themeBtn.setOnAction(e -> {
            if (onThemeAction != null) onThemeAction.run();
        });

        infoBtn.setOnAction(e -> {
            if (onInfoAction != null) onInfoAction.run();
        });

        getChildren().addAll(explorerBtn, templatesBtn, searchBtn, aiBtn, collaborationBtn, terminalBtn, spacer, themeBtn, infoBtn);
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

    public void setActivePanel(Panel panel) {
        setActivePanel(panel, false);
    }

    public void setActivePanel(Panel panel, boolean notifyListener) {
        this.activePanel = panel;
        explorerBtn.getStyleClass().remove("active");
        templatesBtn.getStyleClass().remove("active");
        searchBtn.getStyleClass().remove("active");
        aiBtn.getStyleClass().remove("active");
        collaborationBtn.getStyleClass().remove("active");
        terminalBtn.getStyleClass().remove("active");

        if (panel == Panel.EXPLORER) explorerBtn.getStyleClass().add("active");
        else if (panel == Panel.TEMPLATES) templatesBtn.getStyleClass().add("active");
        else if (panel == Panel.SEARCH) searchBtn.getStyleClass().add("active");
        else if (panel == Panel.AI_COPILOT) aiBtn.getStyleClass().add("active");
        else if (panel == Panel.COLLABORATION) collaborationBtn.getStyleClass().add("active");
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

    public void setOnThemeAction(Runnable onThemeAction) {
        this.onThemeAction = onThemeAction;
    }

    public void setOnInfoAction(Runnable onInfoAction) {
        this.onInfoAction = onInfoAction;
    }
}
