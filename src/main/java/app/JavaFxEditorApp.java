package app;

import controller.FxEditorController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import service.ThemeService;
import view.fx.*;

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
        tabPaneLeft.getStyleClass().add("tab-header-area");
        tabPaneLeft.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        TabPane tabPaneRight = new TabPane();
        tabPaneRight.getStyleClass().add("tab-header-area");
        tabPaneRight.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabPaneRight.setVisible(false);
        tabPaneRight.setManaged(false);

        // Editor Split Pane (Allows Side-by-Side Dual Editor)
        SplitPane editorSplitPane = new SplitPane(tabPaneLeft);
        editorSplitPane.getStyleClass().add("editor-split-pane");

        // Command Palette Floating Overlay
        CommandPalette commandPalette = new CommandPalette();
        StackPane editorCenterStack = new StackPane(editorSplitPane, commandPalette);
        StackPane.setAlignment(commandPalette, Pos.TOP_CENTER);

        // Sidebars & Studio Panes
        ActivityBar activityBar = new ActivityBar();
        SidebarExplorer sidebarExplorer = new SidebarExplorer();
        AiAssistantPane aiAssistantPane = new AiAssistantPane();
        TerminalPane terminalPane = new TerminalPane();
        FxStatusBar statusBar = new FxStatusBar(themeService);

        // Vertical SplitPane: [Editor Area (top) | Terminal (bottom)]
        SplitPane editorTerminalSplitPane = new SplitPane(editorCenterStack);
        editorTerminalSplitPane.setOrientation(Orientation.VERTICAL);
        editorTerminalSplitPane.getStyleClass().add("editor-split-pane");

        // Master 3-way Split Pane: [Sidebar | Editor+Terminal Area | AI Copilot]
        SplitPane masterSplitPane = new SplitPane(sidebarExplorer, editorTerminalSplitPane);
        masterSplitPane.getStyleClass().add("editor-split-pane");
        masterSplitPane.setDividerPositions(0.22);
        SplitPane.setResizableWithParent(sidebarExplorer, false);
        SplitPane.setResizableWithParent(aiAssistantPane, false);

        // Main Application Border Container
        BorderPane root = new BorderPane();
        root.getStyleClass().add("main-container");
        root.setLeft(activityBar);
        root.setCenter(masterSplitPane);
        root.setBottom(statusBar);

        // Initialize Controller
        controller.initializeComponents(
                tabPaneLeft,
                tabPaneRight,
                editorSplitPane,
                masterSplitPane,
                activityBar,
                sidebarExplorer,
                aiAssistantPane,
                statusBar,
                commandPalette,
                terminalPane,
                editorTerminalSplitPane
        );

        // Scene Setup
        Scene scene = new Scene(root, 1200, 780);
        themeService.applyTheme(scene, ThemeService.Theme.VSCODE_DARK);

        // Global Keyboard Accelerators
        setupKeyboardShortcuts(scene);

        // Handle Clean Window Closing (Kills background threads / prevents zombie JVM process)
        primaryStage.setOnCloseRequest(e -> {
            controller.shutdown();
            Platform.exit();
            System.exit(0);
        });

        primaryStage.setTitle("Minimal Code Studio & AI IDE (JavaFX 21+)");
        primaryStage.setScene(scene);
        primaryStage.show();

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

        scene.getAccelerators().put(cmdS, () -> controller.handleSave(false));
        scene.getAccelerators().put(cmdShiftS, () -> controller.handleSave(true));
        scene.getAccelerators().put(cmdO, controller::handleOpenFile);
        scene.getAccelerators().put(cmdN, () -> controller.createNewTab("untitled.txt"));
        scene.getAccelerators().put(cmdW, controller::closeActiveTab);
        scene.getAccelerators().put(cmdF, () -> controller.handleFind(true));
        scene.getAccelerators().put(cmdP, controller::showCommandPalette);
        scene.getAccelerators().put(cmdSplit, controller::toggleSplitEditor);
        scene.getAccelerators().put(cmdAi, controller::toggleAiPanel);
        scene.getAccelerators().put(ctrlBacktick, controller::toggleTerminal);
        scene.getAccelerators().put(ctrlShiftBacktick, controller::createNewTerminalTab);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
