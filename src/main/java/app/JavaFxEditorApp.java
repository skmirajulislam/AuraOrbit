package app;

import controller.FxEditorController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import service.ThemeService;
import view.fx.*;
import org.kordamp.ikonli.codicons.Codicons;

import java.nio.file.Paths;
import java.util.List;

/**
 * Main JavaFX Desktop Application for the Minimal VS Code-style AI IDE.
 */
public class JavaFxEditorApp extends Application {

    private ThemeService themeService;
    private FxEditorController controller;

    @Override
    public void start(Stage primaryStage) {
        Platform.setImplicitExit(true);

        themeService = new ThemeService();
        controller = new FxEditorController(primaryStage, themeService);

        // Tab Panes (Primary Left + Split Right)
        TabPane tabPaneLeft = new TabPane();
        tabPaneLeft.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        TabPane tabPaneRight = new TabPane();
        tabPaneRight.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabPaneRight.setVisible(false);
        tabPaneRight.setManaged(false);

        // Editor Split Pane (Allows Side-by-Side Dual Editor)
        SplitPane editorSplitPane = new SplitPane(tabPaneLeft);
        editorSplitPane.getStyleClass().add("editor-split-pane");

        // Welcome Watermark Pane (Displayed when no editor tabs are open)
        WelcomeWatermarkPane welcomeWatermarkPane = new WelcomeWatermarkPane(
        () -> controller.createNewTab("untitled.txt"),
        controller::handleOpenFile,
        controller::showCommandPalette,
        controller::toggleTerminal,
        controller::toggleAiPanel
    );
        welcomeWatermarkPane.setVisible(false);
        welcomeWatermarkPane.setManaged(false);

        // Command Palette Floating Overlay
        CommandPalette commandPalette = new CommandPalette();
        StackPane editorCenterStack = new StackPane(welcomeWatermarkPane, editorSplitPane, commandPalette);
        editorCenterStack.setPickOnBounds(false);
        StackPane.setAlignment(commandPalette, Pos.TOP_CENTER);
        StackPane.setAlignment(welcomeWatermarkPane, Pos.CENTER);

        Button splitButton = new Button();
        splitButton.setGraphic(IconFactory.getIcon(Codicons.SPLIT_HORIZONTAL, 13));
        splitButton.setTooltip(new Tooltip("Split Editor Right (Cmd+\\)"));
        splitButton.getStyleClass().add("editor-action-icon-button");
        splitButton.setOnAction(e -> controller.toggleSplitEditor());

        Button formatButton = new Button();
        formatButton.setGraphic(IconFactory.getIcon(Codicons.CHECK_ALL, 13));
        formatButton.setTooltip(new Tooltip("Format Document (Shift+Alt+F)"));
        formatButton.getStyleClass().add("editor-action-icon-button");
        formatButton.setOnAction(e -> controller.formatActiveDocument());

        Button wordWrapButton = new Button();
        wordWrapButton.setGraphic(IconFactory.getIcon(Codicons.WORD_WRAP, 13));
        wordWrapButton.setTooltip(new Tooltip("Toggle Word Wrap (Alt+Z)"));
        wordWrapButton.getStyleClass().add("editor-action-icon-button");
        wordWrapButton.setOnAction(e -> controller.toggleWordWrap());

        Button runButton = new Button("Run");
        runButton.setGraphic(IconFactory.getIcon(Codicons.PLAY, 14, "#89d185"));
        runButton.setTooltip(new Tooltip("Run Active File (F5). Type runtime input in the terminal."));
        runButton.getStyleClass().add("editor-run-button");
        runButton.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        runButton.setOnAction(e -> controller.runActiveFile());

        HBox runOverlay = new HBox(3, formatButton, wordWrapButton, splitButton, runButton);
        runOverlay.setAlignment(Pos.CENTER_RIGHT);
        runOverlay.setPadding(new Insets(0, 10, 0, 0));
        runOverlay.setMinHeight(35);
        runOverlay.setPrefHeight(35);
        runOverlay.setMaxHeight(35);
        runOverlay.setPickOnBounds(false);
        editorCenterStack.getChildren().add(runOverlay);
        StackPane.setAlignment(runOverlay, Pos.TOP_RIGHT);

        // Sidebars & Studio Panes
        ActivityBar activityBar = new ActivityBar();
        SidebarExplorer sidebarExplorer = new SidebarExplorer();
        AiAssistantPane aiAssistantPane = new AiAssistantPane();
        TerminalPane terminalPane = new TerminalPane();
        terminalPane.setMinHeight(100);
        terminalPane.setPrefHeight(220);
        FxStatusBar statusBar = new FxStatusBar(themeService);

        // Vertical SplitPane: [Editor Area (top) | Terminal (bottom)]
        editorCenterStack.setMinHeight(120);
        SplitPane editorTerminalSplitPane = new SplitPane(editorCenterStack);
        editorTerminalSplitPane.setOrientation(Orientation.VERTICAL);
        editorTerminalSplitPane.getStyleClass().add("editor-split-pane");
        SplitPane.setResizableWithParent(editorCenterStack, true);
        SplitPane.setResizableWithParent(terminalPane, false);

        // Master 3-way Split Pane: [Sidebar | Editor+Terminal Area | AI Copilot]
        SplitPane masterSplitPane = new SplitPane(sidebarExplorer, editorTerminalSplitPane);
        masterSplitPane.getStyleClass().add("editor-split-pane");
        masterSplitPane.setDividerPositions(0.22);
        SplitPane.setResizableWithParent(sidebarExplorer, false);
        SplitPane.setResizableWithParent(aiAssistantPane, false);
        SplitPane.setResizableWithParent(editorTerminalSplitPane, true);

        // Main Application Border Container
        BorderPane root = new BorderPane();
        root.getStyleClass().add("main-container");
        root.setLeft(activityBar);
        root.setCenter(masterSplitPane);
        root.setBottom(statusBar);

        // Universal In-App Modal Overlay (Replaces OS popups)
        ModalOverlayPane modalOverlayPane = new ModalOverlayPane();
        sidebarExplorer.setModalOverlayPane(modalOverlayPane);
        terminalPane.setModalOverlayPane(modalOverlayPane);
        StackPane rootStackPane = new StackPane(root, modalOverlayPane);

        // Initialize Controller
        controller.initializeComponents(
        tabPaneLeft,
        tabPaneRight,
        editorSplitPane,
        welcomeWatermarkPane,
        masterSplitPane,
        activityBar,
        sidebarExplorer,
        aiAssistantPane,
        statusBar,
        commandPalette,
        terminalPane,
        editorTerminalSplitPane,
        modalOverlayPane
    );

        controller.setRunButton(runButton);
        controller.setRunOverlay(runOverlay);

        // Scene Setup
        Scene scene = new Scene(rootStackPane, 1200, 780);
        themeService.applyTheme(scene, ThemeService.Theme.VSCODE_DARK);

        // Global Keyboard Accelerators
        setupKeyboardShortcuts(scene);

        // Handle Clean Window Closing (Kills background threads / prevents zombie JVM process)
        primaryStage.setOnCloseRequest(e -> {
            controller.shutdown();
            Platform.exit();
            System.exit(0);
        });

        primaryStage.setTitle("AuraOrbit");
        try (var stream = getClass().getResourceAsStream("/icons/app-icon.png")) {
            if (stream != null) {
                primaryStage.getIcons().add(new javafx.scene.image.Image(stream));
            }
        } catch (Exception ignored) {}

        primaryStage.setScene(scene);
        primaryStage.show();

        // First-Run EULA & Software Resource Usage Policy
        if (!service.PolicyAgreementService.isPolicyAccepted()) {
            modalOverlayPane.showAgreementModal(
                    service.PolicyAgreementService.getPolicyTitle(),
                    service.PolicyAgreementService.getPolicySummary(),
                    service.PolicyAgreementService::recordPolicyAcceptance,
                    () -> {
                        Platform.exit();
                        System.exit(0);
                    }
            );
        }

        // Auto-synchronize file explorer whenever the AuraOrbit window regains focus
        primaryStage.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused && sidebarExplorer != null) {
                sidebarExplorer.refreshWorkspace();
            }
        });

        // Check if a file argument was passed
        List<String> rawArgs = getParameters().getRaw();
        if (!rawArgs.isEmpty() && !rawArgs.get(0).startsWith("-")) {
            controller.openFile(Paths.get(rawArgs.get(0)));
        }
    }

    private void setupKeyboardShortcuts(Scene scene) {
        KeyCombination cmdS = new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN);
        KeyCombination cmdShiftS = new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
        KeyCombination cmdO = new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN);
        KeyCombination cmdN = new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN);
        KeyCombination cmdW = new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN);
        KeyCombination cmdF = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);
        KeyCombination cmdP = new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN);
        KeyCombination cmdSplit = new KeyCodeCombination(KeyCode.BACK_SLASH, KeyCombination.SHORTCUT_DOWN);
        KeyCombination cmdAi = new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);

        // Terminal shortcuts use CONTROL_DOWN (not SHORTCUT) to match VS Code behavior on all platforms
        KeyCombination ctrlBacktick = new KeyCodeCombination(KeyCode.BACK_QUOTE, KeyCombination.CONTROL_DOWN);
        KeyCombination ctrlShiftBacktick = new KeyCodeCombination(KeyCode.BACK_QUOTE, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);

        KeyCombination shiftAltF = new KeyCodeCombination(KeyCode.F, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN);
        KeyCombination altZ = new KeyCodeCombination(KeyCode.Z, KeyCombination.ALT_DOWN);
        KeyCombination runActiveFile = new KeyCodeCombination(KeyCode.F5);

        scene.getAccelerators().put(cmdS, () -> controller.handleSave(false));
        scene.getAccelerators().put(cmdShiftS, () -> controller.handleSave(true));
        scene.getAccelerators().put(cmdO, controller::handleOpenFile);
        scene.getAccelerators().put(cmdN, () -> controller.createNewTab("untitled.txt"));
        scene.getAccelerators().put(cmdW, controller::closeActiveTab);
        scene.getAccelerators().put(cmdF, () -> controller.handleFind(true));
        scene.getAccelerators().put(cmdP, controller::showCommandPalette);
        scene.getAccelerators().put(shiftAltF, controller::formatActiveDocument);
        scene.getAccelerators().put(altZ, controller::toggleWordWrap);
        scene.getAccelerators().put(runActiveFile, controller::runActiveFile);
        scene.getAccelerators().put(cmdSplit, controller::toggleSplitEditor);
        scene.getAccelerators().put(cmdAi, controller::toggleAiPanel);
        scene.getAccelerators().put(ctrlBacktick, controller::toggleTerminal);
        scene.getAccelerators().put(ctrlShiftBacktick, controller::createNewTerminalTab);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
