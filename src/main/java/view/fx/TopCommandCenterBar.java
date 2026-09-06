package view.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.kordamp.ikonli.codicons.Codicons;

/**
 * Modern VS Code Command Center Top Bar.
 * Features:
 * - Tab History Navigation (Back / Forward)
 * - Centered Search / Quick Open Pill (displays active file and workspace)
 * - Window Layout Toggles (Primary Sidebar, Bottom Terminal Panel, Secondary AI Assistant)
 */
public class TopCommandCenterBar extends HBox {

    private final Button backBtn;
    private final Button forwardBtn;
    private final HBox searchPill;
    private final Label searchPillLabel;
    private final Button toggleSidebarBtn;
    private final Button togglePanelBtn;
    private final Button toggleAiBtn;

    private Runnable onBackAction;
    private Runnable onForwardAction;
    private Runnable onSearchAction;
    private Runnable onToggleSidebarAction;
    private Runnable onTogglePanelAction;
    private Runnable onToggleAiAction;

    private String currentWorkspaceName = "AuraOrbit";
    private String currentFileName = "";

    public TopCommandCenterBar() {
        getStyleClass().add("top-command-center");
        setAlignment(Pos.CENTER_LEFT);
        setMinHeight(35);
        setPrefHeight(35);
        setMaxHeight(35);
        setPadding(new Insets(2, 8, 2, 8));

        // 1. Left Section: Navigation (Back / Forward)
        HBox leftBox = new HBox(4);
        leftBox.setAlignment(Pos.CENTER_LEFT);

        backBtn = createNavButton(Codicons.ARROW_LEFT, "Go Back (Ctrl+-)");
        forwardBtn = createNavButton(Codicons.ARROW_RIGHT, "Go Forward (Ctrl+Shift+-)");
        backBtn.setDisable(true);
        forwardBtn.setDisable(true);

        backBtn.setOnAction(e -> {
            if (onBackAction != null) onBackAction.run();
        });
        forwardBtn.setOnAction(e -> {
            if (onForwardAction != null) onForwardAction.run();
        });

        leftBox.getChildren().addAll(backBtn, forwardBtn);

        // 2. Center Section: Floating Command Center Search Pill
        HBox centerContainer = new HBox();
        centerContainer.setAlignment(Pos.CENTER);
        HBox.setHgrow(centerContainer, Priority.ALWAYS);

        searchPill = new HBox(6);
        searchPill.getStyleClass().add("command-center-pill");
        searchPill.setAlignment(Pos.CENTER);
        searchPill.setPadding(new Insets(3, 14, 3, 12));
        searchPill.setMinWidth(260);
        searchPill.setPrefWidth(420);
        searchPill.setMaxWidth(600);

        Label searchIcon = new Label();
        searchIcon.setGraphic(IconFactory.getIcon(Codicons.SEARCH, 12, "#969696"));

        searchPillLabel = new Label("AuraOrbit");
        searchPillLabel.getStyleClass().add("command-center-pill-text");

        searchPill.getChildren().addAll(searchIcon, searchPillLabel);
        Tooltip.install(searchPill, new Tooltip("Quick Open / Command Palette (Cmd+P)"));
        searchPill.setOnMouseClicked(e -> {
            if (onSearchAction != null) onSearchAction.run();
        });

        centerContainer.getChildren().add(searchPill);

        // 3. Right Section: Layout Toggles
        HBox rightBox = new HBox(4);
        rightBox.setAlignment(Pos.CENTER_RIGHT);

        toggleSidebarBtn = createNavButton(Codicons.SPLIT_HORIZONTAL, "Toggle Primary Side Bar (Cmd+B)");
        togglePanelBtn = createNavButton(Codicons.TERMINAL, "Toggle Terminal Panel (Ctrl+`)");
        toggleAiBtn = createNavButton(Codicons.HUBOT, "Toggle AuraOrbit Copilot (Cmd+Shift+A)");

        toggleSidebarBtn.setOnAction(e -> {
            if (onToggleSidebarAction != null) onToggleSidebarAction.run();
        });
        togglePanelBtn.setOnAction(e -> {
            if (onTogglePanelAction != null) onTogglePanelAction.run();
        });
        toggleAiBtn.setOnAction(e -> {
            if (onToggleAiAction != null) onToggleAiAction.run();
        });

        rightBox.getChildren().addAll(toggleSidebarBtn, togglePanelBtn, toggleAiBtn);

        getChildren().addAll(leftBox, centerContainer, rightBox);
    }

    private Button createNavButton(Codicons codicon, String tooltipText) {
        Button btn = new Button();
        btn.setGraphic(IconFactory.getIcon(codicon, 13));
        btn.getStyleClass().add("command-center-nav-btn");
        btn.setTooltip(new Tooltip(tooltipText));
        btn.setMinSize(26, 26);
        btn.setPrefSize(26, 26);
        btn.setMaxSize(26, 26);
        return btn;
    }

    public void setWorkspaceName(String workspaceName) {
        this.currentWorkspaceName = (workspaceName != null && !workspaceName.isBlank()) ? workspaceName : "AuraOrbit";
        updatePillText();
    }

    public void setActiveFileName(String fileName) {
        this.currentFileName = (fileName != null && !fileName.isBlank()) ? fileName : "";
        updatePillText();
    }

    private void updatePillText() {
        if (currentFileName != null && !currentFileName.isBlank()) {
            searchPillLabel.setText(currentFileName + " \u2014 " + currentWorkspaceName);
        } else {
            searchPillLabel.setText(currentWorkspaceName);
        }
    }

    public void setNavigationState(boolean canGoBack, boolean canGoForward) {
        backBtn.setDisable(!canGoBack);
        forwardBtn.setDisable(!canGoForward);
    }

    public void setOnBackAction(Runnable onBackAction) {
        this.onBackAction = onBackAction;
    }

    public void setOnForwardAction(Runnable onForwardAction) {
        this.onForwardAction = onForwardAction;
    }

    public void setOnSearchAction(Runnable onSearchAction) {
        this.onSearchAction = onSearchAction;
    }

    public void setOnToggleSidebarAction(Runnable onToggleSidebarAction) {
        this.onToggleSidebarAction = onToggleSidebarAction;
    }

    public void setOnTogglePanelAction(Runnable onTogglePanelAction) {
        this.onTogglePanelAction = onTogglePanelAction;
    }

    public void setOnToggleAiAction(Runnable onToggleAiAction) {
        this.onToggleAiAction = onToggleAiAction;
    }
}
