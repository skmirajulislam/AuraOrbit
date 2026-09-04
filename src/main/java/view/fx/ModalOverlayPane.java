package view.fx;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.kordamp.ikonli.codicons.Codicons;

import java.util.function.Consumer;

/**
 * Modern In-App Modal Dialog System.
 * Renders all alerts, confirmations, about screens, and settings dialogs
 * directly inside the application frame as a themed backdrop overlay,
 * completely eliminating external OS popup windows.
 */
public class ModalOverlayPane extends StackPane {

    private final StackPane backdrop;
    private final VBox dialogCard;
    private final Label titleLabel;
    private final Button closeButton;
    private final VBox bodyContainer;
    private final ScrollPane bodyScroll;
    private final HBox footerContainer;

    public ModalOverlayPane() {
        setVisible(false);
        setManaged(false);
        setAlignment(Pos.CENTER);

        // 1. Scrim Backdrop
        backdrop = new StackPane();
        backdrop.setStyle("-fx-background-color: rgba(0, 0, 0, 0.68);");

        // 2. Centered Modal Dialog Card with strictly bounded fixed dimensions
        dialogCard = new VBox(0);
        dialogCard.setPrefWidth(520);
        dialogCard.setMinWidth(0);
        dialogCard.setMaxWidth(520);
        // Keep dialogs inside the app frame on small windows as well.
        dialogCard.prefWidthProperty().bind(Bindings.max(0, Bindings.min(520, widthProperty().subtract(32))));
        dialogCard.maxWidthProperty().bind(Bindings.max(0, Bindings.min(520, widthProperty().subtract(32))));
        // USE_PREF_SIZE tells StackPane to preserve the dialog's natural height
        // instead of stretching it to the available window height.
        dialogCard.setMaxHeight(Region.USE_PREF_SIZE);
        dialogCard.setAlignment(Pos.TOP_LEFT);
        dialogCard.setStyle("-fx-background-color: -bg-secondary; -fx-border-color: -border-color; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.45), 24, 0, 0, 8);");
        dialogCard.setOnMouseClicked(javafx.event.Event::consume);

        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 16, 12, 16));
        header.setStyle("-fx-border-color: transparent transparent -border-color transparent; -fx-border-width: 0 0 1 0;");

        titleLabel = new Label("Dialog");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: -text-primary;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        closeButton = new Button();
        closeButton.setGraphic(IconFactory.getIcon(Codicons.CLOSE, 13));
        closeButton.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 2 6 2 6;");
        closeButton.setOnAction(e -> close());

        header.getChildren().addAll(titleLabel, spacer, closeButton);

        // A scrollable, height-capped body keeps long dialogs within the window.
        bodyContainer = new VBox(12);
        bodyContainer.setPadding(new Insets(16));
        bodyContainer.setStyle("-fx-background-color: transparent;");

        bodyScroll = new ScrollPane(bodyContainer);
        bodyScroll.setFitToWidth(true);
        bodyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        bodyScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        bodyScroll.setMaxHeight(430);
        bodyScroll.setMinHeight(0);
        bodyScroll.getStyleClass().add("modal-dialog-body");
        VBox.setVgrow(bodyScroll, Priority.NEVER);

        // Footer
        footerContainer = new HBox(10);
        footerContainer.setAlignment(Pos.CENTER_RIGHT);
        footerContainer.setPadding(new Insets(12, 16, 14, 16));
        footerContainer.setStyle("-fx-border-color: -border-color transparent transparent transparent; -fx-border-width: 1 0 0 0;");

        dialogCard.getChildren().addAll(header, bodyScroll, footerContainer);

        getChildren().addAll(backdrop, dialogCard);

        // Clicking outside the dialog card closes the modal immediately
        backdrop.setOnMouseClicked(e -> close());

        // Escape Key closes the modal
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                close();
                e.consume();
            }
        });
    }

    public void showInformation(String title, String message) {
        setupDialog(title, Codicons.INFO, "#4ea8de");

        Label msg = new Label(message);
        msg.setWrapText(true);
        msg.setStyle("-fx-font-size: 13px; -fx-text-fill: -text-primary; -fx-line-spacing: 3;");
        bodyContainer.getChildren().add(msg);

        Button okBtn = createPrimaryButton("OK", this::close);
        footerContainer.getChildren().add(okBtn);

        open();
    }

    public void showError(String title, String message) {
        setupDialog(title, Codicons.ERROR, "#f14c4c");

        Label msg = new Label(message);
        msg.setWrapText(true);
        msg.setStyle("-fx-font-size: 13px; -fx-text-fill: -text-primary; -fx-line-spacing: 3;");
        bodyContainer.getChildren().add(msg);

        Button closeBtn = createPrimaryButton("Dismiss", this::close);
        footerContainer.getChildren().add(closeBtn);

        open();
    }

    public void showConfirmation(String title, String message, String confirmText, Runnable onConfirm) {
        setupDialog(title, Codicons.QUESTION, "#cca700");

        Label msg = new Label(message);
        msg.setWrapText(true);
        msg.setStyle("-fx-font-size: 13px; -fx-text-fill: -text-primary; -fx-line-spacing: 3;");
        bodyContainer.getChildren().add(msg);

        Button cancelBtn = createSecondaryButton("Cancel", this::close);
        Button actionBtn = createPrimaryButton(confirmText, () -> {
            close();
            if (onConfirm != null) onConfirm.run();
        });

        footerContainer.getChildren().addAll(cancelBtn, actionBtn);
        open();
    }

    public void showAbout() {
        setupDialog("AuraOrbit Studio — Modern Desktop AI IDE", Codicons.HUBOT, "#4ea8de");

        VBox content = new VBox(10);

        Label subHeader = new Label("AuraOrbit 2.0.0 — The Intelligent Next-Gen Code Studio");
        subHeader.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: -accent-color;");

        Label desc = new Label(
                "AuraOrbit is an ultra-fast, modern VS Code-grade desktop code editor and AI workspace.\n"
                + "Equipped with rich multi-language syntax highlighting, intelligent code formatting, multi-shell terminal tabs, integrated Git, and multi-LLM Copilot."
        );
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 12px; -fx-text-fill: -text-secondary; -fx-line-spacing: 2;");

        VBox shortcutsBox = new VBox(4);
        shortcutsBox.setStyle("-fx-background-color: -bg-primary; -fx-padding: 10 12 10 12; -fx-background-radius: 6; -fx-border-color: -border-color; -fx-border-radius: 6;");
        
        Label scTitle = new Label("Essential Keyboard Shortcuts:");
        scTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 11.5px; -fx-text-fill: -text-primary;");
        
        Label sc1 = new Label("• Cmd/Ctrl+P : Open Command Palette");
        Label sc2 = new Label("• Shift+Alt+F : Format Document");
        Label sc3 = new Label("• Cmd/Ctrl+Shift+A : Toggle AI Copilot Studio");
        Label sc4 = new Label("• Ctrl+` : Toggle Integrated Terminal Dock");
        Label sc5 = new Label("• Cmd/Ctrl+\\ : Toggle Split Side-by-Side Editor");
        Label sc6 = new Label("• Cmd/Ctrl+W : Close Active Tab");
        
        for (Label l : new Label[]{sc1, sc2, sc3, sc4, sc5, sc6}) {
            l.setStyle("-fx-font-size: 11px; -fx-text-fill: -text-secondary;");
        }
        shortcutsBox.getChildren().addAll(scTitle, sc1, sc2, sc3, sc4, sc5, sc6);

        content.getChildren().addAll(subHeader, desc, shortcutsBox);
        bodyContainer.getChildren().add(content);

        Button okBtn = createPrimaryButton("Got it!", this::close);
        footerContainer.getChildren().add(okBtn);

        open();
    }

    public void showApiKeyDialog(service.AiService aiService, Runnable onSaved) {
        setupDialog("Configure AI Copilot Models & API Keys", Codicons.KEY, "#cca700");

        VBox form = new VBox(12);

        Label intro = new Label("Configure your external LLM provider API keys to unlock dynamic code generation, multi-file reasoning, and refactoring directly in AuraOrbit.");
        intro.setWrapText(true);
        intro.setStyle("-fx-font-size: 12px; -fx-text-fill: -text-secondary; -fx-line-spacing: 2;");

        // 1. Google Gemini Key
        VBox geminiBox = new VBox(4);
        Label geminiLbl = new Label("Google Gemini API Key (Gemini 2.0 Flash / 1.5 Pro)");
        geminiLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11.5px; -fx-text-fill: -text-primary;");
        PasswordField geminiField = new PasswordField();
        geminiField.setPromptText("AIzaSy...");
        geminiField.setText(aiService.getGeminiKey());
        geminiField.getStyleClass().add("ai-prompt-input");
        Label geminiHint = new Label("Get free API key at: aistudio.google.com");
        geminiHint.setStyle("-fx-font-size: 10.5px; -fx-text-fill: -accent-color;");
        geminiBox.getChildren().addAll(geminiLbl, geminiField, geminiHint);

        // 2. OpenAI Key
        VBox openAiBox = new VBox(4);
        Label openAiLbl = new Label("OpenAI API Key (GPT-4o / GPT-4o-mini)");
        openAiLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11.5px; -fx-text-fill: -text-primary;");
        PasswordField openAiField = new PasswordField();
        openAiField.setPromptText("sk-proj-...");
        openAiField.setText(aiService.getOpenAiKey());
        openAiField.getStyleClass().add("ai-prompt-input");
        Label openAiHint = new Label("Get API key at: platform.openai.com/api-keys");
        openAiHint.setStyle("-fx-font-size: 10.5px; -fx-text-fill: -accent-color;");
        openAiBox.getChildren().addAll(openAiLbl, openAiField, openAiHint);

        // 3. xAI Grok Key
        VBox grokBox = new VBox(4);
        Label grokLbl = new Label("xAI Grok API Key (Grok-2)");
        grokLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11.5px; -fx-text-fill: -text-primary;");
        PasswordField grokField = new PasswordField();
        grokField.setPromptText("xai-...");
        grokField.setText(aiService.getGrokKey());
        grokField.getStyleClass().add("ai-prompt-input");
        Label grokHint = new Label("Get API key at: console.x.ai");
        grokHint.setStyle("-fx-font-size: 10.5px; -fx-text-fill: -accent-color;");
        grokBox.getChildren().addAll(grokLbl, grokField, grokHint);

        form.getChildren().addAll(intro, geminiBox, openAiBox, grokBox);
        bodyContainer.getChildren().add(form);

        Button cancelBtn = createSecondaryButton("Cancel", this::close);
        Button saveBtn = createPrimaryButton("Save API Keys", () -> {
            aiService.setGeminiKey(geminiField.getText());
            aiService.setOpenAiKey(openAiField.getText());
            aiService.setGrokKey(grokField.getText());
            close();
            if (onSaved != null) onSaved.run();
        });

        footerContainer.getChildren().addAll(cancelBtn, saveBtn);
        open();
    }

    public void showThemeSelectionDialog(service.ThemeService themeService, Consumer<service.ThemeService.Theme> onSelected) {
        setupDialog("Color Themes & Shades", Codicons.COLOR_MODE, "#cca700");

        VBox content = new VBox(12);

        Label prompt = new Label("Choose your preferred code studio aesthetic:");
        prompt.setStyle("-fx-font-size: 12px; -fx-text-fill: -text-primary;");

        ToggleGroup themeGroup = new ToggleGroup();
        VBox themeOptions = new VBox(6);
        for (String themeName : themeService.getAllThemes().keySet()) {
            RadioButton themeOption = new RadioButton(themeName);
            themeOption.setToggleGroup(themeGroup);
            themeOption.setUserData(themeName);
            themeOption.setStyle("-fx-text-fill: -text-primary; -fx-font-size: 12px;");
            if (themeName.equals(themeService.getCurrentTheme().getDisplayName())) {
                themeOption.setSelected(true);
            }
            themeOptions.getChildren().add(themeOption);
        }

        content.getChildren().addAll(prompt, themeOptions);
        bodyContainer.getChildren().add(content);

        Button cancelBtn = createSecondaryButton("Cancel", this::close);
        Button applyBtn = createPrimaryButton("Apply Theme", () -> {
            Toggle chosenToggle = themeGroup.getSelectedToggle();
            close();
            if (chosenToggle != null && onSelected != null) {
                String chosen = String.valueOf(chosenToggle.getUserData());
                service.ThemeService.Theme theme = themeService.getAllThemes().get(chosen);
                if (theme != null) {
                    onSelected.accept(theme);
                }
            }
        });

        footerContainer.getChildren().addAll(cancelBtn, applyBtn);
        open();
    }

    /**
     * In-frame alternative to ChoiceDialog. Radio buttons avoid a separate
     * ContextMenu popup and therefore remain themed and bounded by this overlay.
     */
    public void showOptionSelection(String title, String prompt, String selectedValue,
                                    java.util.List<String> options, Consumer<String> onSelected) {
        setupDialog(title, Codicons.SETTINGS_GEAR, "#4ea8de");

        Label promptLabel = new Label(prompt);
        promptLabel.setWrapText(true);
        promptLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -text-primary;");

        ToggleGroup optionGroup = new ToggleGroup();
        VBox optionBox = new VBox(6);
        for (String option : options) {
            RadioButton optionButton = new RadioButton(option);
            optionButton.setToggleGroup(optionGroup);
            optionButton.setUserData(option);
            optionButton.setStyle("-fx-text-fill: -text-primary; -fx-font-size: 12px;");
            if (option.equals(selectedValue)) {
                optionButton.setSelected(true);
            }
            optionBox.getChildren().add(optionButton);
        }
        bodyContainer.getChildren().addAll(promptLabel, optionBox);

        Button cancelBtn = createSecondaryButton("Cancel", this::close);
        Button applyBtn = createPrimaryButton("Apply", () -> {
            Toggle selected = optionGroup.getSelectedToggle();
            close();
            if (selected != null && onSelected != null) {
                onSelected.accept(String.valueOf(selected.getUserData()));
            }
        });
        footerContainer.getChildren().addAll(cancelBtn, applyBtn);
        open();
    }

    public void showCustom(String title, Codicons icon, Node customBody, Node... actionButtons) {
        setupDialog(title, icon, "#4ea8de");
        bodyContainer.getChildren().add(customBody);
        if (actionButtons != null && actionButtons.length > 0) {
            footerContainer.getChildren().addAll(actionButtons);
        } else {
            footerContainer.getChildren().add(createPrimaryButton("Done", this::close));
        }
        open();
    }

    private void setupDialog(String title, Codicons icon, String iconColor) {
        titleLabel.setText(" " + title);
        titleLabel.setGraphic(IconFactory.getIcon(icon, 16, iconColor));
        bodyContainer.getChildren().clear();
        footerContainer.getChildren().clear();
    }

    public void open() {
        setVisible(true);
        setManaged(true);
        toFront();
        requestFocus();

        // Smooth fade and scale animation
        setOpacity(0);
        dialogCard.setScaleX(0.92);
        dialogCard.setScaleY(0.92);

        FadeTransition ft = new FadeTransition(Duration.millis(150), this);
        ft.setToValue(1.0);

        ScaleTransition st = new ScaleTransition(Duration.millis(150), dialogCard);
        st.setToX(1.0);
        st.setToY(1.0);

        ft.play();
        st.play();
    }

    public void close() {
        FadeTransition ft = new FadeTransition(Duration.millis(120), this);
        ft.setToValue(0);
        ft.setOnFinished(e -> {
            setVisible(false);
            setManaged(false);
        });
        ft.play();
    }

    public Button createPrimaryButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.getStyleClass().add("btn-modern");
        btn.setStyle("-fx-background-color: -accent-color; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 6 16 6 16; -fx-background-radius: 4; -fx-cursor: hand;");
        btn.setOnAction(e -> {
            if (action != null) action.run();
        });
        return btn;
    }

    public Button createSecondaryButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: transparent; -fx-border-color: -border-color; -fx-text-fill: -text-primary; -fx-font-size: 12px; -fx-padding: 6 16 6 16; -fx-background-radius: 4; -fx-border-radius: 4; -fx-cursor: hand;");
        btn.setOnAction(e -> {
            if (action != null) action.run();
        });
        return btn;
    }
}
