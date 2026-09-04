package collaboration.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import collaboration.core.PermissionManager;
import collaboration.core.UserPresenceTracker;

/**
 * Main collaboration panel integrated into the IDE.
 * Shows participants, connection status, and provides controls.
 */
public class CollaborationPanel extends BorderPane {
    private Label statusLabel;
    private ListView<UserItem> participantsList;
    private Button hostButton;
    private Button joinButton;
    private Button disconnectButton;
    private Label connectionStatus;
    private ProgressIndicator syncIndicator;
    private final ObservableList<UserItem> participants;

    public CollaborationPanel() {
        this.participants = FXCollections.observableArrayList();

        // Header
        VBox headerBox = createHeader();
        setTop(headerBox);

        // Main content
        VBox contentBox = createContent();
        setCenter(contentBox);

        // Footer
        HBox footerBox = createFooter();
        setBottom(footerBox);

        setPadding(new Insets(10));
        setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");
    }

    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(10, 12, 12, 12));

        // Title
        Label titleLabel = new Label("Collaboration");
        titleLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        // Connection Status
        HBox statusBox = new HBox(8);
        statusBox.setPadding(new Insets(5));
        statusBox.setStyle("-fx-background-color: #f5f5f5; -fx-border-radius: 4;");

        connectionStatus = new Label("Not Connected");
        connectionStatus.setStyle("-fx-text-fill: #666;");

        syncIndicator = new ProgressIndicator();
        syncIndicator.setMaxWidth(20);
        syncIndicator.setMaxHeight(20);
        syncIndicator.setPrefWidth(20);
        syncIndicator.setPrefHeight(20);
        syncIndicator.setVisible(false);

        statusBox.getChildren().addAll(connectionStatus, syncIndicator);

        header.getChildren().addAll(titleLabel, statusBox);
        return header;
    }

    private VBox createContent() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setFillWidth(true);

        // Action buttons
        HBox buttonBox = new HBox(8);
        hostButton = new Button("🏠 Host Workspace");
        hostButton.setPrefWidth(140);
        hostButton.setStyle("-fx-font-size: 11; -fx-padding: 8;");

        joinButton = new Button("🔗 Join Workspace");
        joinButton.setPrefWidth(140);
        joinButton.setStyle("-fx-font-size: 11; -fx-padding: 8;");

        disconnectButton = new Button("❌ Disconnect");
        disconnectButton.setPrefWidth(140);
        disconnectButton.setStyle("-fx-font-size: 11; -fx-padding: 8;");
        disconnectButton.setDisable(true);

        buttonBox.getChildren().addAll(hostButton, joinButton, disconnectButton);
        buttonBox.setPadding(new Insets(0, 0, 10, 0));

        // Participants list
        Label participantsLabel = new Label("Participants");
        participantsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");

        participantsList = new ListView<>(participants);
        participantsList.setPrefHeight(200);
        participantsList.setCellFactory(param -> new UserItemCell());
        participantsList.setStyle("-fx-control-inner-background: #fafafa; -fx-font-size: 10;");

        VBox.setVgrow(participantsList, Priority.ALWAYS);
        content.getChildren().addAll(buttonBox, participantsLabel, participantsList);

        return content;
    }

    private HBox createFooter() {
        HBox footer = new HBox(8);
        footer.setPadding(new Insets(8));
        footer.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 9;");

        Label versionLabel = new Label("Live Collab v1.0");
        versionLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 8;");
        HBox.setHgrow(versionLabel, Priority.ALWAYS);

        footer.getChildren().addAll(statusLabel, versionLabel);
        return footer;
    }

    // Public API
    public void addParticipant(String userId, String userName, String color) {
        Platform.runLater(() -> {
            participants.add(new UserItem(userId, userName, color));
        });
    }

    public void removeParticipant(String userId) {
        Platform.runLater(() -> {
            participants.removeIf(item -> item.userId.equals(userId));
        });
    }

    public void updateConnectionStatus(String status, boolean connected) {
        Platform.runLater(() -> {
            connectionStatus.setText(status);
            connectionStatus.setStyle("-fx-text-fill: " + (connected ? "#4CAF50;" : "#f44336;"));
            disconnectButton.setDisable(!connected);
            hostButton.setDisable(connected);
            joinButton.setDisable(connected);
        });
    }

    public void setStatusMessage(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    public void showSyncIndicator(boolean show) {
        Platform.runLater(() -> syncIndicator.setVisible(show));
    }

    public Button getHostButton() {
        return hostButton;
    }

    public Button getJoinButton() {
        return joinButton;
    }

    public Button getDisconnectButton() {
        return disconnectButton;
    }

    /**
     * User item for list display.
     */
    public static class UserItem {
        public final String userId;
        public final String userName;
        public final String color;

        public UserItem(String userId, String userName, String color) {
            this.userId = userId;
            this.userName = userName;
            this.color = color;
        }
    }

    /**
     * Custom cell renderer for user items.
     */
    private static class UserItemCell extends ListCell<UserItem> {
        @Override
        protected void updateItem(UserItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                HBox cell = new HBox(8);
                cell.setPadding(new Insets(5));
                cell.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                // Color indicator
                Pane colorBox = new Pane();
                colorBox.setStyle("-fx-background-color: " + item.color + "; -fx-border-radius: 50;");
                colorBox.setPrefWidth(12);
                colorBox.setPrefHeight(12);

                // User name
                Label nameLabel = new Label(item.userName);
                nameLabel.setStyle("-fx-font-size: 10;");

                cell.getChildren().addAll(colorBox, nameLabel);
                setGraphic(cell);
                setText(null);
            }
        }
    }
}
