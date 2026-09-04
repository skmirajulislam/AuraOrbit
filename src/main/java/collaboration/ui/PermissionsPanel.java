package collaboration.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Permissions panel for hosts to manage guest access.
 * Allows granting/revoking READ and WRITE permissions.
 */
public class PermissionsPanel extends VBox {
    private final ListView<PermissionItem> permissionsList;
    private final ObservableList<PermissionItem> permissions;
    private final Button readOnlyButton;
    private final Button readWriteButton;
    private final Button revokeButton;

    public PermissionsPanel() {
        this.permissions = FXCollections.observableArrayList();

        // Header
        Label titleLabel = new Label("Guest Permissions");
        titleLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");
        titleLabel.setPadding(new Insets(10, 0, 5, 0));
        getChildren().add(titleLabel);

        // List
        permissionsList = new ListView<>(permissions);
        permissionsList.setCellFactory(param -> new PermissionCell());
        permissionsList.setStyle("-fx-control-inner-background: #fafafa; -fx-font-size: 10;");
        permissionsList.setPrefHeight(200);

        VBox.setVgrow(permissionsList, javafx.scene.layout.Priority.ALWAYS);
        getChildren().add(permissionsList);

        // Control buttons
        HBox buttonBox = new HBox(8);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        readOnlyButton = new Button("🔒 Read Only");
        readOnlyButton.setStyle("-fx-font-size: 10; -fx-padding: 6;");
        readOnlyButton.setDisable(true);

        readWriteButton = new Button("✏️ Read & Write");
        readWriteButton.setStyle("-fx-font-size: 10; -fx-padding: 6;");
        readWriteButton.setDisable(true);

        revokeButton = new Button("❌ Revoke");
        revokeButton.setStyle("-fx-font-size: 10; -fx-padding: 6; -fx-text-fill: #f44336;");
        revokeButton.setDisable(true);

        buttonBox.getChildren().addAll(readOnlyButton, readWriteButton, revokeButton);
        getChildren().add(buttonBox);

        // Info
        Label infoLabel = new Label("Select a user to change permissions. Host always has full access.");
        infoLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 8; -fx-font-style: italic;");
        infoLabel.setPadding(new Insets(8, 0, 0, 0));
        getChildren().add(infoLabel);

        setPadding(new Insets(10));
        setStyle("-fx-border-color: #e0e0e0; -fx-padding: 10;");
        setSpacing(8);
    }

    public void addPermissionItem(String userId, String userName, String currentPermission) {
        Platform.runLater(() -> {
            permissions.add(new PermissionItem(userId, userName, currentPermission));
        });
    }

    public void removePermissionItem(String userId) {
        Platform.runLater(() -> {
            permissions.removeIf(item -> item.userId.equals(userId));
        });
    }

    public void updatePermission(String userId, String newPermission) {
        Platform.runLater(() -> {
            for (PermissionItem item : permissions) {
                if (item.userId.equals(userId)) {
                    item.permission = newPermission;
                    permissionsList.refresh();
                    break;
                }
            }
        });
    }

    public PermissionItem getSelectedPermissionItem() {
        return permissionsList.getSelectionModel().getSelectedItem();
    }

    public Button getReadOnlyButton() {
        return readOnlyButton;
    }

    public Button getReadWriteButton() {
        return readWriteButton;
    }

    public Button getRevokeButton() {
        return revokeButton;
    }

    /**
     * Permission item data.
     */
    public static class PermissionItem {
        public final String userId;
        public final String userName;
        public volatile String permission; // READ or WRITE

        public PermissionItem(String userId, String userName, String permission) {
            this.userId = userId;
            this.userName = userName;
            this.permission = permission;
        }
    }

    /**
     * Custom cell renderer for permissions.
     */
    private static class PermissionCell extends ListCell<PermissionItem> {
        @Override
        protected void updateItem(PermissionItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                HBox cell = new HBox(12);
                cell.setPadding(new Insets(8, 10, 8, 10));
                cell.setStyle("-fx-border-color: #f0f0f0; -fx-border-width: 0 0 1 0;");

                // User name
                Label nameLabel = new Label(item.userName);
                nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");
                nameLabel.setPrefWidth(120);

                // Permission badge
                String badgeStyle;
                String badgeText;

                if (item.permission.equals("READ")) {
                    badgeStyle = "-fx-background-color: #FFC107; -fx-text-fill: white; -fx-padding: 4 8 4 8; -fx-border-radius: 4;";
                    badgeText = "🔒 Read Only";
                } else if (item.permission.equals("WRITE")) {
                    badgeStyle = "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 4 8 4 8; -fx-border-radius: 4;";
                    badgeText = "✏️ Read & Write";
                } else {
                    badgeStyle = "-fx-background-color: #999; -fx-text-fill: white; -fx-padding: 4 8 4 8; -fx-border-radius: 4;";
                    badgeText = "❌ Revoked";
                }

                Label permLabel = new Label(badgeText);
                permLabel.setStyle("-fx-font-size: 9; " + badgeStyle);

                cell.getChildren().addAll(nameLabel, permLabel);
                HBox.setHgrow(permLabel, javafx.scene.layout.Priority.ALWAYS);

                setGraphic(cell);
                setText(null);
            }
        }
    }
}
