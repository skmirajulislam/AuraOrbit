package collaboration.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Detailed participants panel showing all users with their status and permissions.
 */
public class ParticipantsPanel extends VBox {
    private ListView<ParticipantView> participantsList;
    private final ObservableList<ParticipantView> participants;
    private Label countLabel;

    public ParticipantsPanel() {
        this.participants = FXCollections.observableArrayList();

        // Header
        VBox headerBox = createHeader();
        getChildren().add(headerBox);

        // List
        participantsList = new ListView<>(participants);
        participantsList.setCellFactory(param -> new ParticipantCell());
        participantsList.setStyle("-fx-control-inner-background: #fafafa; -fx-font-size: 10;");
        participantsList.setPrefHeight(250);

        VBox.setVgrow(participantsList, javafx.scene.layout.Priority.ALWAYS);
        getChildren().add(participantsList);

        // Footer
        HBox footerBox = createFooter();
        getChildren().add(footerBox);

        setStyle("-fx-border-color: #e0e0e0; -fx-padding: 10;");
        setSpacing(8);
    }

    private VBox createHeader() {
        VBox header = new VBox(5);
        Label title = new Label("Active Participants");
        title.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");
        header.getChildren().add(title);
        return header;
    }

    private HBox createFooter() {
        HBox footer = new HBox(10);
        footer.setPadding(new Insets(8, 0, 0, 0));
        footer.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");

        countLabel = new Label("0 participants");
        countLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 9;");

        footer.getChildren().add(countLabel);
        return footer;
    }

    public void addParticipant(String userId, String userName, String color,
                               String permission, int cursorLine) {
        Platform.runLater(() -> {
            participants.add(new ParticipantView(userId, userName, color, permission, cursorLine));
            updateCount();
        });
    }

    public void removeParticipant(String userId) {
        Platform.runLater(() -> {
            participants.removeIf(p -> p.userId.equals(userId));
            updateCount();
        });
    }

    public void updateParticipantCursor(String userId, int line, int column) {
        Platform.runLater(() -> {
            for (ParticipantView p : participants) {
                if (p.userId.equals(userId)) {
                    p.cursorLine = line;
                    p.cursorColumn = column;
                    participantsList.refresh();
                    break;
                }
            }
        });
    }

    public void updateParticipantPermission(String userId, String permission) {
        Platform.runLater(() -> {
            for (ParticipantView p : participants) {
                if (p.userId.equals(userId)) {
                    p.permission = permission;
                    participantsList.refresh();
                    break;
                }
            }
        });
    }

    private void updateCount() {
        countLabel.setText(participants.size() + " participant" + (participants.size() != 1 ? "s" : ""));
    }

    /**
     * Participant view data.
     */
    public static class ParticipantView {
        public final String userId;
        public final String userName;
        public final String color;
        public volatile String permission;
        public volatile int cursorLine;
        public volatile int cursorColumn;
        public volatile long lastUpdate;

        public ParticipantView(String userId, String userName, String color,
                              String permission, int cursorLine) {
            this.userId = userId;
            this.userName = userName;
            this.color = color;
            this.permission = permission;
            this.cursorLine = cursorLine;
            this.cursorColumn = 0;
            this.lastUpdate = System.currentTimeMillis();
        }
    }

    /**
     * Custom cell for participant display.
     */
    private static class ParticipantCell extends ListCell<ParticipantView> {
        @Override
        protected void updateItem(ParticipantView item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                HBox cell = new HBox(10);
                cell.setPadding(new Insets(8, 10, 8, 10));
                cell.setStyle("-fx-border-color: #f0f0f0; -fx-border-width: 0 0 1 0;");

                // Color indicator
                javafx.scene.shape.Circle circle = new javafx.scene.shape.Circle(8);
                circle.setStyle("-fx-fill: " + item.color + ";");

                // User info
                VBox infoBox = new VBox(2);
                Label nameLabel = new Label(item.userName);
                nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");

                HBox statusBox = new HBox(8);
                Label permLabel = new Label("📋 " + item.permission);
                permLabel.setStyle("-fx-font-size: 9; -fx-text-fill: #666;");

                Label cursorLabel = new Label("Line " + (item.cursorLine + 1));
                cursorLabel.setStyle("-fx-font-size: 9; -fx-text-fill: #999;");

                statusBox.getChildren().addAll(permLabel, cursorLabel);
                infoBox.getChildren().addAll(nameLabel, statusBox);

                cell.getChildren().addAll(circle, infoBox);
                HBox.setHgrow(infoBox, javafx.scene.layout.Priority.ALWAYS);

                setGraphic(cell);
                setText(null);
            }
        }
    }
}
