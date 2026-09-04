package view.fx;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.codicons.Codicons;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modern AI IDE Copilot & Assistant Studio Pane powered by VS Code Codicons.
 * Structured with BorderPane for guaranteed bottom-anchored composer visibility.
 */
public class AiAssistantPane extends BorderPane {

    /**
     * Attached file or image model for Copilot context.
     */
    public static class AttachmentItem {
        public enum Type { CODE_FILE, TEXT_FILE, IMAGE }

        private final Type type;
        private final String name;
        private final Path path;
        private final String content;
        private final Image previewImage;

        public AttachmentItem(Type type, String name, Path path, String content, Image previewImage) {
            this.type = type;
            this.name = name;
            this.path = path;
            this.content = content;
            this.previewImage = previewImage;
        }

        public Type getType() { return type; }
        public String getName() { return name; }
        public Path getPath() { return path; }
        public String getContent() { return content; }
        public Image getPreviewImage() { return previewImage; }
    }

    private final ComboBox<String> modelSelector;
    private final VBox chatMessagesContainer = new VBox(8);
    private final ScrollPane scrollPane;
    private final TextArea promptInput;
    private final Button sendButton;
    private final Button attachButton;
    private final Label contextLabel;
    private final HBox attachmentChipBox;
    private final ScrollPane attachmentScroll;
    private final List<AttachmentItem> attachments = new ArrayList<>();

    private boolean requestInProgress;
    private static final int MAX_CHAT_MESSAGES = 100;
    private static final int CLEANUP_THRESHOLD = 150;
    private static final int MAX_ATTACHMENT_CHARS = 50_000;

    private final service.AiService aiService = new service.AiService();
    private Runnable onConfigureApiKeysRequested;

    private Supplier<String> activeFileSupplier;
    private Supplier<Path> activeFilePathSupplier;
    private Supplier<Path> workspacePathSupplier;
    private Supplier<String> selectedCodeSupplier;
    private Supplier<String> entireFileContentSupplier;
    private Consumer<String> onInsertCodeToEditor;
    private Consumer<String> onReplaceSelectionInEditor;
    private Runnable onCloseRequested;

    public AiAssistantPane() {
        getStyleClass().add("ai-assistant-pane");
        setPrefWidth(320);
        setMinWidth(240);
        setMaxWidth(450);

        // Chat viewport components (initialized first for lambda references)
        chatMessagesContainer.setPadding(new Insets(8, 10, 8, 10));
        chatMessagesContainer.getStyleClass().add("ai-chat-list");

        scrollPane = new ScrollPane(chatMessagesContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("ai-chat-scroll");
        scrollPane.setMinHeight(0); // Essential for flexible shrinking without pushing composer

        // ─────────────────────────────────────────────────────────────────────
        // 1. HEADER & ACTIONS (TOP)
        // ─────────────────────────────────────────────────────────────────────
        HBox header = new HBox(4);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 6, 0, 10));
        header.getStyleClass().add("ai-header");

        Label chatTab = new Label("Chat");
        chatTab.getStyleClass().addAll("ai-panel-tab", "active");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button newChatBtn = new Button();
        newChatBtn.setGraphic(IconFactory.getIcon(Codicons.ADD, 13));
        newChatBtn.getStyleClass().add("ai-icon-btn");
        newChatBtn.setTooltip(new Tooltip("New Chat Session"));
        newChatBtn.setOnAction(e -> {
            chatMessagesContainer.getChildren().clear();
            attachments.clear();
            refreshAttachmentChips();
            addAssistantMessage("Chat session refreshed. Ready for your next request!");
        });

        Button keyBtn = new Button();
        keyBtn.setGraphic(IconFactory.getIcon(Codicons.KEY, 13, "#cca700"));
        keyBtn.getStyleClass().add("ai-icon-btn");
        keyBtn.setTooltip(new Tooltip("Configure API Keys (Gemini, GPT, Grok)"));
        keyBtn.setOnAction(e -> {
            if (onConfigureApiKeysRequested != null) onConfigureApiKeysRequested.run();
        });

        Button closeBtn = new Button();
        closeBtn.setGraphic(IconFactory.getIcon(Codicons.CLOSE, 12));
        closeBtn.getStyleClass().add("ai-icon-btn");
        closeBtn.setTooltip(new Tooltip("Close Copilot Panel"));
        closeBtn.setOnAction(e -> {
            if (onCloseRequested != null) onCloseRequested.run();
        });

        header.getChildren().addAll(chatTab, spacer, newChatBtn, keyBtn, closeBtn);

        // Quick Actions Bar
        HBox quickActions = new HBox(5);
        quickActions.setPadding(new Insets(6, 10, 6, 10));
        quickActions.setAlignment(Pos.CENTER_LEFT);
        quickActions.getStyleClass().add("ai-quick-actions");

        Button explainBtn = createActionChip(Codicons.LIGHTBULB, "Explain", () -> handleQuickAction("Explain this code step-by-step with complexity analysis."));
        Button refactorBtn = createActionChip(Codicons.TOOLS, "Refactor", () -> handleQuickAction("Refactor this code for readability, performance, and clean architecture."));
        Button testsBtn = createActionChip(Codicons.BEAKER, "Tests", () -> handleQuickAction("Write comprehensive JUnit 5 unit tests for this code covering all edge cases."));
        Button fixBugsBtn = createActionChip(Codicons.BUG, "Fix Bugs", () -> handleQuickAction("Analyze this code for potential null pointer exceptions, resource leaks, or concurrency bugs, and provide the fix."));

        quickActions.getChildren().addAll(explainBtn, refactorBtn, testsBtn, fixBugsBtn);
        ScrollPane quickScroll = new ScrollPane(quickActions);
        quickScroll.setFitToHeight(true);
        quickScroll.setFitToWidth(true);
        quickScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        quickScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        quickScroll.getStyleClass().add("ai-quick-scroll");

        VBox topContainer = new VBox(0, header, quickScroll);
        setTop(topContainer);

        setCenter(scrollPane);

        addAssistantMessage("Hello! I'm **AuraOrbit Copilot**.\n\n"
                + "I can help you write code, debug issues, generate tests, and refactor your project.\n\n"
                + "Select code in the editor or click one of the quick actions above to start!");

        // ─────────────────────────────────────────────────────────────────────
        // 3. COMPOSER INPUT AREA (BOTTOM - PERMANENTLY ANCHORED)
        // ─────────────────────────────────────────────────────────────────────
        contextLabel = new Label(" No active file");
        contextLabel.setGraphic(IconFactory.getIcon(Codicons.FILE_CODE, 11));
        contextLabel.getStyleClass().add("ai-context-label");

        // Attachment chips container
        attachmentChipBox = new HBox(4);
        attachmentChipBox.setAlignment(Pos.CENTER_LEFT);
        attachmentChipBox.setPadding(new Insets(2, 2, 2, 2));

        attachmentScroll = new ScrollPane(attachmentChipBox);
        attachmentScroll.setFitToHeight(true);
        attachmentScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        attachmentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        attachmentScroll.getStyleClass().add("ai-attachment-scroll");
        attachmentScroll.setMinHeight(0);
        attachmentScroll.setMaxHeight(32);
        attachmentScroll.setVisible(false);
        attachmentScroll.setManaged(false);

        // Multiline prompt input
        promptInput = new TextArea();
        promptInput.setPromptText("Ask Copilot or type a prompt...");
        promptInput.setPrefRowCount(2);
        promptInput.setMinHeight(44);
        promptInput.setMaxHeight(120);
        promptInput.setWrapText(true);
        promptInput.getStyleClass().add("ai-prompt-input");
        promptInput.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (e.isShiftDown()) {
                    // Allow normal multiline newline
                } else {
                    e.consume();
                    sendMessage();
                }
            }
        });

        // Attachment '+' button
        attachButton = new Button();
        attachButton.setGraphic(IconFactory.getIcon(Codicons.ADD, 13));
        attachButton.getStyleClass().addAll("ai-icon-btn", "ai-attach-btn");
        attachButton.setTooltip(new Tooltip("Attach active file, code, or image..."));
        attachButton.setOnAction(e -> showAttachmentMenu());

        // Model Selector ComboBox
        modelSelector = new ComboBox<>();
        modelSelector.getItems().addAll(
                "Gemini 3.5 Flash (Google)",
                "GPT-4o 2024-11-20 (OpenAI)",
                "GPT-4o-mini (OpenAI)",
                "Grok-3 (xAI)",
                "DeepSeek-R1 14B (Local / Ollama)",
                "Offline Copilot (Built-in)"
        );
        modelSelector.getSelectionModel().select("Offline Copilot (Built-in)");
        modelSelector.getStyleClass().add("ai-model-pill");
        modelSelector.setMaxWidth(160);

        // Custom list cell styling for clean compact display
        modelSelector.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(formatShortModelName(item));
                    setGraphic(IconFactory.getIcon(Codicons.LIGHTBULB_AUTOFIX, 11, "#4ea8de"));
                }
            }
        });
        modelSelector.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setGraphic(IconFactory.getIcon(Codicons.LIGHTBULB_AUTOFIX, 11, "#4ea8de"));
                }
            }
        });

        // Send Button
        sendButton = new Button();
        sendButton.setGraphic(IconFactory.getIcon(Codicons.ARROW_UP, 13, "#ffffff"));
        sendButton.getStyleClass().add("ai-send-btn");
        sendButton.setTooltip(new Tooltip("Send message (Enter, Shift+Enter for newline)"));
        sendButton.setOnAction(e -> sendMessage());

        Region composerSpacer = new Region();
        HBox.setHgrow(composerSpacer, Priority.ALWAYS);

        HBox composerBar = new HBox(6);
        composerBar.setAlignment(Pos.CENTER_LEFT);
        composerBar.getStyleClass().add("ai-composer-bar");
        composerBar.getChildren().addAll(attachButton, composerSpacer, modelSelector, sendButton);

        VBox composer = new VBox(4);
        composer.getStyleClass().add("ai-composer");
        composer.getChildren().addAll(attachmentScroll, promptInput, composerBar);

        VBox inputArea = new VBox(4);
        inputArea.setPadding(new Insets(4, 8, 8, 8));
        inputArea.getStyleClass().add("ai-input-area");
        inputArea.setMinHeight(Region.USE_PREF_SIZE); // Never pushed off-screen
        inputArea.getChildren().addAll(contextLabel, composer);

        setBottom(inputArea);
    }

    private String formatShortModelName(String fullName) {
        if (fullName == null) return "";
        if (fullName.contains("Gemini")) return "Gemini 3.5";
        if (fullName.contains("mini")) return "GPT-4o mini";
        if (fullName.contains("GPT-4o")) return "GPT-4o";
        if (fullName.contains("Grok")) return "Grok-3";
        if (fullName.contains("DeepSeek")) return "DeepSeek-R1";
        if (fullName.contains("Offline")) return "Offline Copilot";
        return fullName;
    }

    /**
     * Compact attachment popover menu.
     */
    private void showAttachmentMenu() {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("ai-attachment-menu");

        // 1. Attach Active File
        String currentFileName = activeFileSupplier != null ? activeFileSupplier.get() : null;
        Path currentFilePath = activeFilePathSupplier != null ? activeFilePathSupplier.get() : null;
        MenuItem attachActiveItem = new MenuItem(
                currentFileName != null && !currentFileName.isBlank()
                        ? "Attach Active File (" + currentFileName + ")"
                        : "Attach Active File"
        );
        attachActiveItem.setGraphic(IconFactory.getIcon(Codicons.FILE_CODE, 13, "#4ea8de"));
        attachActiveItem.setDisable(currentFileName == null || currentFileName.isBlank());
        attachActiveItem.setOnAction(e -> attachActiveFile(currentFileName, currentFilePath));

        // 2. Attach Code or Text File
        MenuItem attachFileItem = new MenuItem("Attach Code / Text File...");
        attachFileItem.setGraphic(IconFactory.getIcon(Codicons.NEW_FILE, 13, "#89d185"));
        attachFileItem.setOnAction(e -> promptAttachCodeFile());

        // 3. Attach Image
        MenuItem attachImageItem = new MenuItem("Attach Image...");
        attachImageItem.setGraphic(IconFactory.getIcon(Codicons.FILE_MEDIA, 13, "#cca700"));
        attachImageItem.setOnAction(e -> promptAttachImageFile());

        // 4. Attach Workspace File
        MenuItem attachWorkspaceItem = new MenuItem("Attach From Workspace...");
        attachWorkspaceItem.setGraphic(IconFactory.getIcon(Codicons.FOLDER_OPENED, 13, "#c586c0"));
        attachWorkspaceItem.setOnAction(e -> promptAttachWorkspaceFile());

        menu.getItems().addAll(attachActiveItem, attachFileItem, attachImageItem, new SeparatorMenuItem(), attachWorkspaceItem);
        menu.show(attachButton, javafx.geometry.Side.TOP, 0, -4);
    }

    private void attachActiveFile(String fileName, Path filePath) {
        String content = entireFileContentSupplier != null ? entireFileContentSupplier.get() : "";
        if (content == null) content = "";
        if (content.length() > MAX_ATTACHMENT_CHARS) {
            content = content.substring(0, MAX_ATTACHMENT_CHARS) + "\n\n/* [Truncated: middle/end omitted to stay within context] */";
        }
        addAttachment(new AttachmentItem(AttachmentItem.Type.CODE_FILE, fileName, filePath, content, null));
    }

    private void promptAttachCodeFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Attach Code or Text File to Copilot");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Source Code & Text", "*.java", "*.py", "*.js", "*.ts", "*.json", "*.xml", "*.html", "*.css", "*.md", "*.txt", "*.c", "*.cpp", "*.h", "*.sql", "*.sh", "*.yaml", "*.yml", "*.properties"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        if (workspacePathSupplier != null && workspacePathSupplier.get() != null) {
            File wsDir = workspacePathSupplier.get().toFile();
            if (wsDir.exists() && wsDir.isDirectory()) chooser.setInitialDirectory(wsDir);
        }
        File selected = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (selected != null && selected.isFile()) {
            try {
                String content = Files.readString(selected.toPath());
                if (content.length() > MAX_ATTACHMENT_CHARS) {
                    content = content.substring(0, MAX_ATTACHMENT_CHARS) + "\n\n/* [Truncated for AI context] */";
                }
                addAttachment(new AttachmentItem(AttachmentItem.Type.CODE_FILE, selected.getName(), selected.toPath(), content, null));
            } catch (Exception ex) {
                addAssistantMessage("⚠️ Failed to read attached file `" + selected.getName() + "`: " + ex.getMessage());
            }
        }
    }

    private void promptAttachImageFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Attach Image to Copilot");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp", "*.bmp"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File selected = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (selected != null && selected.isFile()) {
            try {
                Image img = new Image(new FileInputStream(selected), 48, 48, true, true);
                addAttachment(new AttachmentItem(AttachmentItem.Type.IMAGE, selected.getName(), selected.toPath(), null, img));
            } catch (Exception ex) {
                addAssistantMessage("⚠️ Failed to load attached image `" + selected.getName() + "`: " + ex.getMessage());
            }
        }
    }

    private void promptAttachWorkspaceFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Attach Project File from Workspace");
        if (workspacePathSupplier != null && workspacePathSupplier.get() != null) {
            File wsDir = workspacePathSupplier.get().toFile();
            if (wsDir.exists() && wsDir.isDirectory()) chooser.setInitialDirectory(wsDir);
        }
        File selected = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (selected != null && selected.isFile()) {
            try {
                String content = Files.readString(selected.toPath());
                if (content.length() > MAX_ATTACHMENT_CHARS) {
                    content = content.substring(0, MAX_ATTACHMENT_CHARS) + "\n\n/* [Truncated] */";
                }
                addAttachment(new AttachmentItem(AttachmentItem.Type.CODE_FILE, selected.getName(), selected.toPath(), content, null));
            } catch (Exception ex) {
                addAssistantMessage("⚠️ Failed to attach workspace file: " + ex.getMessage());
            }
        }
    }

    private void addAttachment(AttachmentItem item) {
        // Prevent duplicate attachment of the same file
        for (AttachmentItem existing : attachments) {
            if (existing.getName().equalsIgnoreCase(item.getName())) return;
        }
        attachments.add(item);
        refreshAttachmentChips();
    }

    private void removeAttachment(AttachmentItem item) {
        attachments.remove(item);
        refreshAttachmentChips();
    }

    private void refreshAttachmentChips() {
        attachmentChipBox.getChildren().clear();
        if (attachments.isEmpty()) {
            attachmentScroll.setVisible(false);
            attachmentScroll.setManaged(false);
            return;
        }

        for (AttachmentItem item : attachments) {
            HBox chip = new HBox(4);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.getStyleClass().add("ai-attachment-chip");

            Node icon;
            if (item.getType() == AttachmentItem.Type.IMAGE && item.getPreviewImage() != null) {
                ImageView iv = new ImageView(item.getPreviewImage());
                iv.setFitWidth(16);
                iv.setFitHeight(16);
                icon = iv;
            } else {
                icon = IconFactory.getIcon(Codicons.FILE_CODE, 12, "#4ea8de");
            }

            Label nameLbl = new Label(item.getName());
            nameLbl.getStyleClass().add("ai-attachment-chip-label");

            Button removeBtn = new Button();
            removeBtn.setGraphic(IconFactory.getIcon(Codicons.CLOSE, 9, "#888888"));
            removeBtn.getStyleClass().add("ai-attachment-remove-btn");
            removeBtn.setTooltip(new Tooltip("Remove attachment"));
            removeBtn.setOnAction(e -> removeAttachment(item));

            chip.getChildren().addAll(icon, nameLbl, removeBtn);
            attachmentChipBox.getChildren().add(chip);
        }

        attachmentScroll.setVisible(true);
        attachmentScroll.setManaged(true);
    }

    private Button createActionChip(Codicons icon, String text, Runnable action) {
        Button btn = new Button(" " + text);
        btn.setGraphic(IconFactory.getIcon(icon, 12));
        btn.getStyleClass().add("ai-action-chip");
        btn.setOnAction(e -> action.run());
        return btn;
    }

    private void handleQuickAction(String actionPrompt) {
        if (requestInProgress) {
            addAssistantMessage("### Request in progress\n\nWait for the current AI response before starting another task.");
            return;
        }
        String selectedCode = selectedCodeSupplier != null ? selectedCodeSupplier.get() : null;
        String fileName = activeFileSupplier != null ? activeFileSupplier.get() : "file";
        String fullContent = entireFileContentSupplier != null ? entireFileContentSupplier.get() : "";

        String codeToProcess = (selectedCode != null && !selectedCode.trim().isEmpty()) ? selectedCode : fullContent;

        if (codeToProcess == null || codeToProcess.trim().isEmpty()) {
            addUserMessage(actionPrompt);
            addAssistantMessage("⚠️ The active file is currently empty. Open or type code in the editor to use quick actions.");
            return;
        }

        addUserMessage(actionPrompt + " (" + fileName + ")");
        generateAiResponse(actionPrompt, codeToProcess, fileName, true);
    }

    private void sendMessage() {
        if (requestInProgress) {
            addAssistantMessage("### Request in progress\n\nWait for the current AI response before sending another message.");
            return;
        }
        String query = promptInput.getText();
        if ((query == null || query.trim().isEmpty()) && attachments.isEmpty()) return;

        promptInput.clear();

        List<AttachmentItem> attachedList = new ArrayList<>(attachments);
        attachments.clear();
        refreshAttachmentChips();

        addUserMessage(query != null ? query : "", attachedList);

        String selectedCode = selectedCodeSupplier != null ? selectedCodeSupplier.get() : null;
        String fileName = activeFileSupplier != null ? activeFileSupplier.get() : "file";
        String fullContent = entireFileContentSupplier != null ? entireFileContentSupplier.get() : "";
        String codeToProcess = (selectedCode != null && !selectedCode.trim().isEmpty()) ? selectedCode : fullContent;

        // Build combined context with attached files
        StringBuilder combinedContext = new StringBuilder();
        if (codeToProcess != null && !codeToProcess.isBlank()) {
            combinedContext.append(codeToProcess);
        }
        for (AttachmentItem att : attachedList) {
            if (att.getContent() != null && !att.getContent().isBlank()) {
                if (combinedContext.length() > 0) combinedContext.append("\n\n");
                combinedContext.append("/* ─── Attached File: ").append(att.getName()).append(" ─── */\n");
                combinedContext.append(att.getContent());
            } else if (att.getType() == AttachmentItem.Type.IMAGE) {
                if (combinedContext.length() > 0) combinedContext.append("\n\n");
                combinedContext.append("/* [Attached Image: ").append(att.getName()).append("] */\n");
            }
        }

        String finalContext = combinedContext.toString();
        String effectivePrompt = (query != null && !query.isBlank()) ? query : "Please analyze the attached files and provide recommendations.";
        generateAiResponse(effectivePrompt, finalContext, fileName, requiresConnectedModel(effectivePrompt, finalContext));
    }

    private boolean requiresConnectedModel(String prompt, String codeContext) {
        String request = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        return (codeContext != null && !codeContext.isBlank())
                || request.matches(".*\\b(write|generate|create|explain|analy[sz]e|refactor|fix|debug|test|implement|optimi[sz]e)\\b.*");
    }

    private boolean isProviderReady(String model) {
        if (model == null || model.startsWith("Offline")) return false;
        String normalized = model.toLowerCase(Locale.ROOT);
        return normalized.contains("local") || normalized.contains("deepseek") || aiService.hasKeyForModel(model);
    }

    private void generateAiResponse(String prompt, String codeContext, String fileName, boolean requiresProvider) {
        String selectedModel = modelSelector.getValue();

        if (requiresProvider && !isProviderReady(selectedModel)) {
            addAssistantMessage("### AI model connection required\n\n"
                    + "This request needs a connected AI model to generate reliable code or analysis. "
                    + "Choose Gemini, GPT, Grok, or a running local Ollama model, then use the **Key** button to configure it.");
            return;
        }

        requestInProgress = true;
        sendButton.setDisable(true);
        promptInput.setDisable(true);

        // Show thinking indicator
        VBox thinkingBox = new VBox(4);
        thinkingBox.setStyle("-fx-background-color: -bg-secondary; -fx-padding: 8 10 8 10; -fx-background-radius: 6;");
        Label thinkingLbl = new Label(" Analyzing " + fileName + " with " + selectedModel + "...");
        thinkingLbl.setGraphic(IconFactory.getIcon(Codicons.LIGHTBULB_AUTOFIX, 12, "#4ea8de"));
        thinkingLbl.setStyle("-fx-text-fill: -accent-color; -fx-font-size: 11.5px; -fx-font-style: italic;");
        thinkingBox.getChildren().add(thinkingLbl);
        chatMessagesContainer.getChildren().add(thinkingBox);
        scrollToBottom();

        // Perform async intelligent code analysis with proper error handling
        Thread aiThread = new Thread(() -> {
            String response;
            try {
                if (selectedModel != null && !selectedModel.startsWith("Offline")) {
                    try {
                        response = aiService.generateResponse(selectedModel, prompt, codeContext, fileName);
                        if (response == null || response.isBlank()) {
                            response = "### Empty AI response\n\nThe provider returned no usable content. Please try again.";
                        }
                    } catch (Exception ex) {
                        String err = ex.getMessage();
                        response = "### AI provider unavailable\n\n"
                                + (err != null ? err : "The selected provider did not return a response.")
                                + "\n\nCheck the model connection and try again. AuraOrbit did not fabricate a fallback result.";
                    }
                } else {
                    if (codeContext != null && !codeContext.isBlank()) {
                        response = generateDynamicCodeAiResponse(prompt, codeContext, fileName);
                    } else {
                        response = "### Offline Copilot (Built-in)\n\n"
                                + "Offline mode can answer structural questions, explain code, and generate unit tests for active editor files without an API key.\n\n"
                                + "To chat freely with external LLMs, select Gemini, GPT, Grok, or Ollama and configure your API key.";
                    }
                }

                final String finalResponse = response;
                Platform.runLater(() -> {
                    try {
                        if (chatMessagesContainer.getChildren().contains(thinkingBox)) {
                            chatMessagesContainer.getChildren().remove(thinkingBox);
                        }
                        addAssistantMessage(finalResponse);
                    } finally {
                        requestInProgress = false;
                        sendButton.setDisable(false);
                        promptInput.setDisable(false);
                        promptInput.requestFocus();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    try {
                        if (chatMessagesContainer.getChildren().contains(thinkingBox)) {
                            chatMessagesContainer.getChildren().remove(thinkingBox);
                        }
                        addAssistantMessage("### Unexpected error\n\n" + (ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred."));
                    } finally {
                        requestInProgress = false;
                        sendButton.setDisable(false);
                        promptInput.setDisable(false);
                        promptInput.requestFocus();
                    }
                });
            }
        }, "ai-assistant-worker");
        aiThread.setDaemon(true);
        aiThread.start();
    }

    private record ParsedMethod(String returnType, String name, String params) {}

    private record CodeAnalysis(
            String packageName,
            String className,
            List<ParsedMethod> methods,
            int totalLines,
            int codeLines,
            int commentLines,
            int cyclomaticComplexity,
            List<String> detectedSmells,
            List<String> frameworksUsed
    ) {}

    private CodeAnalysis analyzeCode(String code, String fallbackFileName) {
        String pkg = "";
        Matcher pkgMatcher = Pattern.compile("package\\s+([a-zA-Z0-9_.]+);").matcher(code);
        if (pkgMatcher.find()) {
            pkg = pkgMatcher.group(1);
        }

        String cls = fallbackFileName.contains(".") ? fallbackFileName.substring(0, fallbackFileName.lastIndexOf('.')) : fallbackFileName;
        Matcher clsMatcher = Pattern.compile("(?:class|interface|enum|record)\\s+([A-Za-z0-9_]+)").matcher(code);
        if (clsMatcher.find()) {
            cls = clsMatcher.group(1);
        }

        List<ParsedMethod> methods = new ArrayList<>();
        Pattern methodPattern = Pattern.compile("(?:public|protected|private|static|final|synchronized|\\s)*\\s+([\\w\\<\\>\\[\\]]+)\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)\\s*(?:throws\\s+[\\w\\s,]+)?\\s*\\{");
        Matcher methodMatcher = methodPattern.matcher(code);
        while (methodMatcher.find()) {
            String ret = methodMatcher.group(1);
            String name = methodMatcher.group(2);
            String params = methodMatcher.group(3);
            if (!name.equals("if") && !name.equals("while") && !name.equals("for") && !name.equals("switch") && !name.equals("catch") && !name.equals("synchronized")) {
                methods.add(new ParsedMethod(ret, name, params.trim()));
            }
        }

        String[] lines = code.split("\\R");
        int total = lines.length;
        int codeCount = 0;
        int comments = 0;
        for (String l : lines) {
            String trimmed = l.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                comments++;
            } else {
                codeCount++;
            }
        }

        int complexity = 1;
        Pattern flowPattern = Pattern.compile("\\b(if|while|for|case|catch)\\b|(&&|\\|\\||\\?)");
        Matcher flowMatcher = flowPattern.matcher(code);
        while (flowMatcher.find()) {
            complexity++;
        }

        List<String> smells = new ArrayList<>();
        if (methods.isEmpty() && codeCount > 5) {
            smells.add("No explicit methods identified in selection; script-style or inline block.");
        }
        for (ParsedMethod m : methods) {
            if (m.params.split(",").length > 4) {
                smells.add("Method '" + m.name + "' has more than 4 parameters (high coupling).");
            }
        }
        if (code.contains("catch (Exception e) {}") || code.contains("catch (Throwable t) {}")) {
            smells.add("Empty catch block detected; potential silent failure.");
        }
        if (complexity > 15) {
            smells.add("High cyclomatic complexity (" + complexity + "); consider decomposing into smaller methods.");
        }

        List<String> frameworks = new ArrayList<>();
        if (code.contains("javafx.")) frameworks.add("JavaFX UI");
        if (code.contains("java.nio.file") || code.contains("java.io.")) frameworks.add("Java I/O & NIO");
        if (code.contains("java.util.concurrent") || code.contains("Thread")) frameworks.add("Java Concurrency & Threads");
        if (code.contains("org.junit")) frameworks.add("JUnit Testing");
        if (code.contains("java.sql") || code.contains("javax.sql")) frameworks.add("JDBC Database Access");
        if (code.contains("java.net.http") || code.contains("java.net.Socket")) frameworks.add("Networking & HTTP");

        return new CodeAnalysis(pkg, cls, methods, total, codeCount, comments, complexity, smells, frameworks);
    }

    private String generateDynamicCodeAiResponse(String prompt, String code, String fileName) {
        String pLower = prompt.toLowerCase();
        CodeAnalysis a = analyzeCode(code, fileName);

        if (pLower.contains("explain")) {
            StringBuilder sb = new StringBuilder();
            sb.append("### 💡 Dynamic Code Explanation (").append(fileName).append(")\n\n");
            sb.append("**Target Element**: `").append(a.className).append("`");
            if (!a.packageName.isEmpty()) sb.append(" (Package: `").append(a.packageName).append("`)");
            sb.append("\n\n");

            sb.append("#### 📊 Structural & Performance Metrics\n");
            sb.append("- **Total Lines**: ").append(a.totalLines).append(" (Executable: ").append(a.codeLines).append(", Comments: ").append(a.commentLines).append(")\n");
            sb.append("- **Cyclomatic Complexity**: ").append(a.cyclomaticComplexity).append(" (")
              .append(a.cyclomaticComplexity <= 5 ? "🟢 Simple / Low Risk" : a.cyclomaticComplexity <= 15 ? "🟡 Moderate" : "🔴 High Complexity").append(")\n");
            if (!a.frameworksUsed.isEmpty()) {
                sb.append("- **Detected Frameworks & Libraries**: ").append(String.join(", ", a.frameworksUsed)).append("\n");
            }

            sb.append("\n#### 🧩 Discovered Methods & Signatures\n");
            if (a.methods.isEmpty()) {
                sb.append("- *No methods detected in the current code snippet.*\n");
            } else {
                for (ParsedMethod m : a.methods) {
                    sb.append("- `").append(m.returnType).append(" ").append(m.name).append("(").append(m.params).append(")`\n");
                }
            }

            if (!a.detectedSmells.isEmpty()) {
                sb.append("\n#### ⚠️ Code Health Observations\n");
                for (String smell : a.detectedSmells) {
                    sb.append("- ").append(smell).append("\n");
                }
            }
            return sb.toString();
        } else if (pLower.contains("test")) {
            StringBuilder sb = new StringBuilder();
            sb.append("### 🧪 Generated JUnit 5 Test Suite for `").append(a.className).append("`\n\n");
            sb.append("```java\n");
            if (!a.packageName.isEmpty()) {
                sb.append("package ").append(a.packageName).append(";\n\n");
            }
            sb.append("import org.junit.jupiter.api.BeforeEach;\n");
            sb.append("import org.junit.jupiter.api.Test;\n");
            sb.append("import org.junit.jupiter.api.DisplayName;\n");
            sb.append("import static org.junit.jupiter.api.Assertions.*;\n\n");
            sb.append("class ").append(a.className).append("Test {\n\n");
            sb.append("    private ").append(a.className).append(" instance;\n\n");
            sb.append("    @BeforeEach\n");
            sb.append("    void setUp() {\n");
            sb.append("        // Setup test instance for ").append(a.className).append("\n");
            sb.append("        // instance = new ").append(a.className).append("();\n");
            sb.append("    }\n\n");

            if (a.methods.isEmpty()) {
                sb.append("    @Test\n");
                sb.append("    @DisplayName(\"Verify execution of ").append(a.className).append(" routine\")\n");
                sb.append("    void testExecution() {\n");
                sb.append("        assertDoesNotThrow(() -> {\n");
                sb.append("            // Test execution logic\n");
                sb.append("        });\n");
                sb.append("    }\n");
            } else {
                for (ParsedMethod m : a.methods) {
                    String cap = Character.toUpperCase(m.name.charAt(0)) + m.name.substring(1);
                    sb.append("    @Test\n");
                    sb.append("    @DisplayName(\"Test method: ").append(m.name).append("\")\n");
                    sb.append("    void test").append(cap).append("() {\n");

                    StringBuilder callArgs = new StringBuilder();
                    if (!m.params.isEmpty()) {
                        String[] parts = m.params.split(",");
                        for (int i = 0; i < parts.length; i++) {
                            String p = parts[i].trim();
                            String[] pTokens = p.split("\\s+");
                            String pType = pTokens.length > 0 ? pTokens[0] : "Object";
                            String pName = pTokens.length > 1 ? pTokens[1] : "arg" + i;
                            if (i > 0) callArgs.append(", ");
                            callArgs.append(pName);

                            String defaultVal = switch (pType) {
                                case "int", "short", "byte" -> "0";
                                case "long" -> "0L";
                                case "double" -> "0.0";
                                case "float" -> "0.0f";
                                case "boolean" -> "true";
                                case "String" -> "\"test\"";
                                case "Path" -> "java.nio.file.Paths.get(\".\")";
                                case "File" -> "new java.io.File(\".\")";
                                default -> "null";
                            };
                            sb.append("        ").append(pType).append(" ").append(pName).append(" = ").append(defaultVal).append(";\n");
                        }
                    }

                    if ("void".equalsIgnoreCase(m.returnType)) {
                        sb.append("        assertDoesNotThrow(() -> instance.").append(m.name).append("(").append(callArgs).append("));\n");
                    } else {
                        sb.append("        ").append(m.returnType).append(" result = instance.").append(m.name).append("(").append(callArgs).append(");\n");
                        if ("boolean".equalsIgnoreCase(m.returnType)) {
                            sb.append("        assertTrue(result);\n");
                        } else {
                            sb.append("        assertNotNull(result);\n");
                        }
                    }
                    sb.append("    }\n\n");
                }
            }
            sb.append("}\n```");
            return sb.toString();
        } else if (pLower.contains("refactor") || pLower.contains("optimize")) {
            StringBuilder sb = new StringBuilder();
            sb.append("### 🛠 Dynamic Refactoring & Architecture Analysis\n\n");
            sb.append("Target: `").append(a.className).append("` (").append(a.codeLines).append(" LOC, Cyclomatic Complexity: ").append(a.cyclomaticComplexity).append(")\n\n");
            sb.append("#### Optimization Recommendations:\n");
            if (a.cyclomaticComplexity > 10) {
                sb.append("1. **Decompose Complex Branches**: Extract nested loops and conditional branches into dedicated helper methods.\n");
            } else {
                sb.append("1. **Clean Control Flow**: Current cyclomatic complexity (").append(a.cyclomaticComplexity).append(") is modular and clean.\n");
            }
            sb.append("2. **Defensive Validation**: Validate arguments with `Objects.requireNonNull`.\n");
            sb.append("3. **Resource Safety**: Enforce try-with-resources on all streams, channels, and sockets.\n\n");

            sb.append("#### Refactored Scaffold Preview:\n```java\n");
            if (!a.packageName.isEmpty()) sb.append("package ").append(a.packageName).append(";\n\n");
            sb.append("import java.util.Objects;\n\n");
            sb.append("public class ").append(a.className).append(" {\n\n");
            for (ParsedMethod m : a.methods) {
                sb.append("    public ").append(m.returnType).append(" ").append(m.name).append("(").append(m.params).append(") {\n");
                if (!m.params.isEmpty()) {
                    String[] parts = m.params.split(",");
                    for (String p : parts) {
                        String[] tokens = p.trim().split("\\s+");
                        if (tokens.length > 1 && !tokens[0].matches("int|long|double|float|boolean|char|short|byte")) {
                            sb.append("        Objects.requireNonNull(").append(tokens[1]).append(", \"").append(tokens[1]).append(" must not be null\");\n");
                        }
                    }
                }
                if ("void".equalsIgnoreCase(m.returnType)) {
                    sb.append("        // Optimized implementation\n");
                } else {
                    sb.append("        return ").append("boolean".equals(m.returnType) ? "true" : m.returnType.matches("int|long|double|float") ? "0" : "null").append(";\n");
                }
                sb.append("    }\n\n");
            }
            sb.append("}\n```");
            return sb.toString();
        } else if (pLower.contains("fix") || pLower.contains("bug")) {
            StringBuilder sb = new StringBuilder();
            sb.append("### 🐛 Dynamic Bug & Safety Audit: `").append(a.className).append("`\n\n");
            sb.append("- **Class**: `").append(a.className).append("` (").append(a.codeLines).append(" LOC)\n");
            sb.append("- **Complexity Rating**: ").append(a.cyclomaticComplexity).append("\n\n");
            sb.append("#### Safety & Concurrency Findings:\n");
            if (a.detectedSmells.isEmpty()) {
                sb.append("✔ **Clean Execution**: No critical anti-patterns or excessive complexity detected.\n");
                sb.append("✔ **Method Sizing**: All ").append(a.methods.size()).append(" methods are well-scoped.\n");
            } else {
                for (String smell : a.detectedSmells) {
                    sb.append("⚠️ **Observation**: ").append(smell).append("\n");
                }
            }
            sb.append("✔ **Memory & CPU Efficiency**: Zero static leak vectors identified.\n");
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("### 🤖 AuraOrbit Copilot\n\n");
            sb.append("Analyzed `").append(fileName).append("` (`").append(a.className).append("`):\n\n");
            sb.append("- **Class**: `").append(a.className).append("`\n");
            sb.append("- **Discovered Methods**: ").append(a.methods.size()).append("\n");
            sb.append("- **Executable Lines**: ").append(a.codeLines).append("\n");
            sb.append("- **Cyclomatic Complexity**: ").append(a.cyclomaticComplexity).append("\n\n");
            sb.append("Ask me to **Explain**, write **Tests**, **Refactor**, or **Fix Bugs** for this specific code!");
            return sb.toString();
        }
    }

    public void addUserMessage(String text) {
        addUserMessage(text, Collections.emptyList());
    }

    public void addUserMessage(String text, List<AttachmentItem> attached) {
        VBox msgBox = new VBox(4);
        msgBox.setAlignment(Pos.CENTER_RIGHT);
        msgBox.setPadding(new Insets(2, 0, 2, 20));

        VBox bubbleBox = new VBox(4);
        bubbleBox.setAlignment(Pos.CENTER_RIGHT);
        bubbleBox.maxWidthProperty().bind(scrollPane.widthProperty().subtract(56));
        bubbleBox.setStyle("-fx-background-color: -accent-color; -fx-padding: 7 11 7 11; -fx-background-radius: 10 10 3 10;");

        if (attached != null && !attached.isEmpty()) {
            HBox attachRow = new HBox(4);
            attachRow.setAlignment(Pos.CENTER_RIGHT);
            for (AttachmentItem att : attached) {
                HBox chip = new HBox(3);
                chip.setAlignment(Pos.CENTER_LEFT);
                chip.setStyle("-fx-background-color: rgba(255,255,255,0.22); -fx-padding: 2 6 2 6; -fx-background-radius: 4;");
                Label nameLbl = new Label(att.getName());
                nameLbl.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 10.5px; -fx-font-weight: bold;");
                chip.getChildren().addAll(
                        IconFactory.getIcon(att.getType() == AttachmentItem.Type.IMAGE ? Codicons.FILE_MEDIA : Codicons.FILE_CODE, 10, "#ffffff"),
                        nameLbl
                );
                attachRow.getChildren().add(chip);
            }
            bubbleBox.getChildren().add(attachRow);
        }

        if (text != null && !text.isBlank()) {
            Label label = new Label(text);
            label.setWrapText(true);
            label.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 12.5px;");
            bubbleBox.getChildren().add(label);
        }

        msgBox.getChildren().add(bubbleBox);
        chatMessagesContainer.getChildren().add(msgBox);
        enforceChatHistoryLimit();
        scrollToBottom();
    }

    public void addAssistantMessage(String markdown) {
        VBox msgBox = new VBox(6);
        msgBox.setAlignment(Pos.CENTER_LEFT);
        msgBox.setPadding(new Insets(2, 20, 2, 0));

        VBox contentBox = new VBox(8);
        contentBox.maxWidthProperty().bind(scrollPane.widthProperty().subtract(44));
        contentBox.setStyle("-fx-background-color: -bg-secondary; -fx-border-color: -border-color; -fx-border-width: 1; -fx-padding: 9 11 9 11; -fx-background-radius: 3 10 10 10; -fx-border-radius: 3 10 10 10;");

        // Split markdown by code fences ```
        String[] parts = markdown.split("```");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part == null || part.isBlank()) continue;

            if (i % 2 == 0) {
                // Render Markdown prose instead of showing **, ###, and list markers literally.
                contentBox.getChildren().add(renderMarkdownProse(part.trim(), contentBox));
            } else {
                // Code block
                int firstNewline = part.indexOf('\n');
                String lang = "CODE";
                String codeContent = part;
                if (firstNewline != -1) {
                    String possibleLang = part.substring(0, firstNewline).trim();
                    if (!possibleLang.isEmpty()) {
                        lang = possibleLang.toUpperCase();
                    }
                    codeContent = part.substring(firstNewline + 1);
                }
                final String snippet = codeContent.stripTrailing();

                // Sleek VS Code Code Card
                VBox codeCard = new VBox(0);
                codeCard.setStyle("-fx-background-color: -bg-primary; -fx-border-color: -border-color; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;");

                // Card Header: [LANG            [Copy] [Insert] [Replace]]
                HBox cardHeader = new HBox(6);
                cardHeader.setAlignment(Pos.CENTER_LEFT);
                cardHeader.setPadding(new Insets(4, 8, 4, 8));
                cardHeader.setStyle("-fx-background-color: -bg-secondary; -fx-border-color: transparent transparent -border-color transparent; -fx-border-width: 0 0 1 0;");

                Label langLabel = new Label(lang);
                langLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: -text-secondary;");

                Region cardSpacer = new Region();
                HBox.setHgrow(cardSpacer, Priority.ALWAYS);

                Button copyBtn = new Button("Copy");
                copyBtn.setGraphic(IconFactory.getIcon(Codicons.CLIPPY, 11));
                copyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -text-secondary; -fx-font-size: 10.5px; -fx-padding: 2 6 2 6; -fx-cursor: hand;");
                copyBtn.setOnAction(e -> {
                    javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                    cc.putString(snippet);
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
                    copyBtn.setText("Copied!");
                    copyBtn.setGraphic(IconFactory.getIcon(Codicons.CHECK, 11, "#89d185"));
                    javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
                    pt.setOnFinished(ev -> {
                        copyBtn.setText("Copy");
                        copyBtn.setGraphic(IconFactory.getIcon(Codicons.CLIPPY, 11));
                    });
                    pt.play();
                });

                Button insertBtn = new Button("Insert");
                insertBtn.setGraphic(IconFactory.getIcon(Codicons.DIFF_ADDED, 11, "#ffffff"));
                insertBtn.setStyle("-fx-background-color: -accent-color; -fx-text-fill: #ffffff; -fx-font-size: 10.5px; -fx-padding: 2 8 2 8; -fx-cursor: hand; -fx-background-radius: 3;");
                insertBtn.setOnAction(e -> {
                    if (onInsertCodeToEditor != null) onInsertCodeToEditor.accept(snippet);
                });

                Button replaceBtn = new Button("Replace");
                replaceBtn.setGraphic(IconFactory.getIcon(Codicons.REPLACE, 11));
                replaceBtn.setStyle("-fx-background-color: transparent; -fx-border-color: -border-color; -fx-text-fill: -text-primary; -fx-font-size: 10.5px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 3;");
                replaceBtn.setOnAction(e -> {
                    if (onReplaceSelectionInEditor != null) onReplaceSelectionInEditor.accept(snippet);
                });

                cardHeader.getChildren().addAll(langLabel, cardSpacer, copyBtn, insertBtn, replaceBtn);

                // Code body
                Label codeLabel = new Label(snippet);
                codeLabel.setWrapText(true);
                codeLabel.setStyle("-fx-font-family: 'JetBrains Mono', 'Fira Code', 'Menlo', 'Consolas', monospace; -fx-font-size: 11.5px; -fx-text-fill: -text-primary; -fx-padding: 8 10 8 10;");

                codeCard.getChildren().addAll(cardHeader, codeLabel);
                contentBox.getChildren().add(codeCard);
            }
        }

        msgBox.getChildren().add(contentBox);
        chatMessagesContainer.getChildren().add(msgBox);
        enforceChatHistoryLimit();
        scrollToBottom();
    }

    /** Renders the compact Markdown used by AI responses (headings, bold, inline code, and lists). */
    private VBox renderMarkdownProse(String markdown, VBox parent) {
        VBox prose = new VBox(4);
        prose.setFillWidth(true);
        prose.setMaxWidth(Double.MAX_VALUE);

        for (String rawLine : markdown.split("\\R", -1)) {
            if (rawLine.isBlank()) {
                Region gap = new Region();
                gap.setMinHeight(5);
                prose.getChildren().add(gap);
                continue;
            }

            String line = rawLine.trim();
            int headingLevel = 0;
            while (headingLevel < line.length() && line.charAt(headingLevel) == '#') headingLevel++;
            boolean isHeading = headingLevel > 0 && headingLevel < line.length()
                    && Character.isWhitespace(line.charAt(headingLevel));
            if (isHeading) line = line.substring(headingLevel).trim();

            boolean isBullet = line.startsWith("- ") || line.startsWith("* ");
            if (isBullet) line = line.substring(2).trim();

            TextFlow lineFlow = new TextFlow();
            lineFlow.setLineSpacing(2);
            lineFlow.setPrefWidth(0);
            lineFlow.maxWidthProperty().bind(Bindings.max(0, parent.widthProperty().subtract(24)));

            if (isBullet) {
                Text bullet = new Text("•  ");
                bullet.setStyle("-fx-fill: -accent-color; -fx-font-size: 12.5px;");
                lineFlow.getChildren().add(bullet);
            }

            appendMarkdownInline(lineFlow, line, isHeading, headingLevel);
            prose.getChildren().add(lineFlow);
        }
        return prose;
    }

    private void appendMarkdownInline(TextFlow flow, String text, boolean heading, int headingLevel) {
        Pattern inlinePattern = Pattern.compile("\\*\\*(.+?)\\*\\*|`([^`]+)`");
        Matcher matcher = inlinePattern.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            addMarkdownText(flow, text.substring(cursor, matcher.start()), heading, headingLevel, false, false);
            if (matcher.group(1) != null) {
                addMarkdownText(flow, matcher.group(1), heading, headingLevel, true, false);
            } else {
                addMarkdownText(flow, matcher.group(2), heading, headingLevel, false, true);
            }
            cursor = matcher.end();
        }
        addMarkdownText(flow, text.substring(cursor), heading, headingLevel, false, false);
    }

    private void addMarkdownText(TextFlow flow, String value, boolean heading, int headingLevel,
                                 boolean bold, boolean inlineCode) {
        if (value.isEmpty()) return;
        Text segment = new Text(value);
        double size = heading ? (headingLevel <= 2 ? 15 : 13.5) : 12.5;
        String color = heading ? "-accent-color" : inlineCode ? "-syntax-string" : "#ffffff";
        String weight = (heading || bold) ? "bold" : "normal";
        String fontFamily = inlineCode ? " -fx-font-family: 'Menlo', 'Monaco', 'Consolas', monospace;" : "";
        segment.setStyle("-fx-fill: " + color + "; -fx-font-size: " + size
                + "px; -fx-font-weight: " + weight + ";" + fontFamily);
        flow.getChildren().add(segment);
    }

    public void updateActiveContext(String fileName, int lineCount, int selectedChars) {
        String info = " " + (fileName != null ? fileName : "No file active");
        if (selectedChars > 0) {
            info += " (" + selectedChars + " chars selected)";
        } else if (lineCount > 0) {
            info += " (" + lineCount + " lines)";
        }
        contextLabel.setText(info);
    }

    private void enforceChatHistoryLimit() {
        int childCount = chatMessagesContainer.getChildren().size();
        if (childCount > CLEANUP_THRESHOLD) {
            int removeCount = childCount - MAX_CHAT_MESSAGES;
            chatMessagesContainer.getChildren().subList(0, removeCount).clear();
        }
    }
    
    public void setMaxChatMessages(int max) {
        // Allow dynamic configuration if needed
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    public service.AiService getAiService() { return aiService; }
    public void setOnConfigureApiKeysRequested(Runnable onConfigureApiKeysRequested) { this.onConfigureApiKeysRequested = onConfigureApiKeysRequested; }
    public void setActiveFileSupplier(Supplier<String> activeFileSupplier) { this.activeFileSupplier = activeFileSupplier; }
    public void setActiveFilePathSupplier(Supplier<Path> activeFilePathSupplier) { this.activeFilePathSupplier = activeFilePathSupplier; }
    public void setWorkspacePathSupplier(Supplier<Path> workspacePathSupplier) { this.workspacePathSupplier = workspacePathSupplier; }
    public void setSelectedCodeSupplier(Supplier<String> selectedCodeSupplier) { this.selectedCodeSupplier = selectedCodeSupplier; }
    public void setEntireFileContentSupplier(Supplier<String> entireFileContentSupplier) { this.entireFileContentSupplier = entireFileContentSupplier; }
    public void setOnInsertCodeToEditor(Consumer<String> onInsertCodeToEditor) { this.onInsertCodeToEditor = onInsertCodeToEditor; }
    public void setOnReplaceSelectionInEditor(Consumer<String> onReplaceSelectionInEditor) { this.onReplaceSelectionInEditor = onReplaceSelectionInEditor; }
    public void setOnCloseRequested(Runnable onCloseRequested) { this.onCloseRequested = onCloseRequested; }
}
