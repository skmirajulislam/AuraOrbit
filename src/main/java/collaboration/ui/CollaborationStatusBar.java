package collaboration.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * Status bar for displaying collaboration status and connection metrics.
 * Shows at the bottom of the IDE window.
 */
public class CollaborationStatusBar extends HBox {
    private final Label statusLabel;
    private final Label userCountLabel;
    private final Label latencyLabel;
    private final Label syncStatusLabel;
    private final ProgressIndicator syncIndicator;

    public CollaborationStatusBar() {
        setPadding(new Insets(4, 10, 4, 10));
        setSpacing(20);
        setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");

        // Status
        statusLabel = new Label("🔴 Offline");
        statusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #f44336;");

        // User count
        userCountLabel = new Label("👥 1 user");
        userCountLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #666;");

        // Latency
        latencyLabel = new Label("📡 --ms");
        latencyLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #666;");

        // Sync status
        syncIndicator = new ProgressIndicator();
        syncIndicator.setMaxWidth(16);
        syncIndicator.setMaxHeight(16);
        syncIndicator.setPrefWidth(16);
        syncIndicator.setPrefHeight(16);
        syncIndicator.setVisible(false);

        syncStatusLabel = new Label("✓ Synced");
        syncStatusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #4CAF50;");

        // Spacer
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(
                statusLabel,
                userCountLabel,
                latencyLabel,
                syncIndicator,
                syncStatusLabel,
                spacer
        );
    }

    /**
     * Update connection status.
     */
    public void setConnectionStatus(boolean connected) {
        Platform.runLater(() -> {
            if (connected) {
                statusLabel.setText("🟢 Connected");
                statusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #4CAF50;");
            } else {
                statusLabel.setText("🔴 Offline");
                statusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #f44336;");
            }
        });
    }

    /**
     * Update user count.
     */
    public void setUserCount(int count) {
        Platform.runLater(() -> {
            userCountLabel.setText("👥 " + count + " user" + (count != 1 ? "s" : ""));
        });
    }

    /**
     * Update latency indicator (in milliseconds).
     */
    public void setLatency(long latencyMs) {
        Platform.runLater(() -> {
            String style = "-fx-font-size: 10; -fx-text-fill: #4CAF50;";
            if (latencyMs > 100) {
                style = "-fx-font-size: 10; -fx-text-fill: #FFC107;"; // Warning
            } else if (latencyMs > 500) {
                style = "-fx-font-size: 10; -fx-text-fill: #f44336;"; // Error
            }

            latencyLabel.setText("📡 " + latencyMs + "ms");
            latencyLabel.setStyle(style);
        });
    }

    /**
     * Show/hide sync indicator (when syncing in progress).
     */
    public void setSyncing(boolean syncing) {
        Platform.runLater(() -> {
            syncIndicator.setVisible(syncing);
            if (syncing) {
                syncStatusLabel.setText("⟳ Syncing...");
                syncStatusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #2196F3;");
            } else {
                syncStatusLabel.setText("✓ Synced");
                syncStatusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #4CAF50;");
            }
        });
    }

    /**
     * Show sync error.
     */
    public void setSyncError(String errorMessage) {
        Platform.runLater(() -> {
            syncStatusLabel.setText("⚠ " + errorMessage);
            syncStatusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #f44336;");
            syncIndicator.setVisible(false);
        });
    }

    /**
     * Reset to default state.
     */
    public void reset() {
        Platform.runLater(() -> {
            statusLabel.setText("🔴 Offline");
            statusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #f44336;");
            userCountLabel.setText("👥 1 user");
            latencyLabel.setText("📡 --ms");
            syncStatusLabel.setText("✓ Synced");
            syncStatusLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #4CAF50;");
            syncIndicator.setVisible(false);
        });
    }
}
