package collaboration.ui;

import collaboration.network.CollaborationHostServer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/**
 * Modern VS Code Live Share-styled dialog for managing hosted sessions.
 */
public class HostSessionDialog extends Stage {

    private final TextField linkField;
    private final Label statusLabel;
    private final VBox guestsListContainer;
    private final Button copyBtn;
    private final Button endSessionBtn;

    public HostSessionDialog(Stage owner, String shareUrl, Runnable onEndSession) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.UTILITY);
        setTitle("Live Share - Host Collaboration Session");

        VBox root = new VBox(16);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #1e1e1e; -fx-font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;");
        root.setPrefWidth(480);

        // 1. Header
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        FontIcon liveIcon = FontIcon.of(Codicons.RADIO_TOWER, 20);
        liveIcon.setIconColor(Color.web("#007acc"));
        Label title = new Label("Hosting Collaboration Session");
        title.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 15px; -fx-font-weight: bold;");
        header.getChildren().addAll(liveIcon, title);

        // 2. Status Row
        HBox statusRow = new HBox(8);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        Circle statusDot = new Circle(4, Color.web("#4ec9b0"));
        statusLabel = new Label("Tunnel Active (Zero-Config WAN Ingress)");
        statusLabel.setStyle("-fx-text-fill: #858585; -fx-font-size: 11px;");
        statusRow.getChildren().addAll(statusDot, statusLabel);

        // 3. Share URL Box
        VBox linkBox = new VBox(6);
        Label linkTitle = new Label("Share this secure link with collaborators:");
        linkTitle.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

        HBox inputRow = new HBox(8);
        linkField = new TextField(shareUrl != null ? shareUrl : "Generating tunnel link...");
        linkField.setEditable(false);
        linkField.setStyle("-fx-background-color: #252526; -fx-text-fill: #d4d4d4; -fx-border-color: #3c3c3c; -fx-border-radius: 3; -fx-padding: 6 8; -fx-font-size: 12px;");
        HBox.setHgrow(linkField, Priority.ALWAYS);

        copyBtn = new Button("Copy Link");
        copyBtn.setGraphic(FontIcon.of(Codicons.CLIPPY, 14));
        copyBtn.setStyle("-fx-background-color: #007acc; -fx-text-fill: #ffffff; -fx-cursor: hand; -fx-padding: 6 12; -fx-font-size: 12px;");
        copyBtn.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(linkField.getText());
            clipboard.setContent(content);
            copyBtn.setText("Copied!");
            Platform.runLater(() -> copyBtn.setStyle("-fx-background-color: #16825d; -fx-text-fill: white;"));
        });
        inputRow.getChildren().addAll(linkField, copyBtn);
        linkBox.getChildren().addAll(linkTitle, inputRow);

        // 4. Connected Guests Section
        VBox guestSection = new VBox(8);
        Label guestsTitle = new Label("Connected Collaborators:");
        guestsTitle.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px; -fx-font-weight: bold;");

        guestsListContainer = new VBox(6);
        guestsListContainer.setStyle("-fx-background-color: #252526; -fx-border-color: #3c3c3c; -fx-border-radius: 4; -fx-padding: 10; -fx-min-height: 80;");
        updateGuestList(List.of());

        guestSection.getChildren().addAll(guestsTitle, guestsListContainer);

        // 5. Footer Actions
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);

        endSessionBtn = new Button("End Session");
        endSessionBtn.setStyle("-fx-background-color: #c72e2e; -fx-text-fill: #ffffff; -fx-cursor: hand; -fx-padding: 6 14; -fx-font-size: 12px;");
        endSessionBtn.setOnAction(e -> {
            if (onEndSession != null) {
                onEndSession.run();
            }
            close();
        });

        Button closeBtn = new Button("Keep Running in Background");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; -fx-border-color: #3c3c3c; -fx-cursor: hand; -fx-padding: 6 12; -fx-font-size: 12px;");
        closeBtn.setOnAction(e -> close());

        footer.getChildren().addAll(closeBtn, endSessionBtn);

        root.getChildren().addAll(header, statusRow, linkBox, guestSection, footer);
        Scene scene = new Scene(root);
        setScene(scene);
    }

    public void setShareUrl(String url) {
        Platform.runLater(() -> {
            linkField.setText(url);
            statusLabel.setText("Tunnel Active (Ready for connections)");
        });
    }

    public void updateGuestList(List<CollaborationHostServer.ClientInfo> guests) {
        Platform.runLater(() -> {
            guestsListContainer.getChildren().clear();
            if (guests.isEmpty()) {
                Label emptyLabel = new Label("No collaborators have joined yet.");
                emptyLabel.setStyle("-fx-text-fill: #707070; -fx-font-style: italic; -fx-font-size: 11px;");
                guestsListContainer.getChildren().add(emptyLabel);
            } else {
                for (CollaborationHostServer.ClientInfo g : guests) {
                    HBox row = new HBox(8);
                    row.setAlignment(Pos.CENTER_LEFT);
                    Circle dot = new Circle(4, Color.web(g.colorHex()));
                    Label name = new Label(g.clientName());
                    name.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 12px;");
                    Label id = new Label("(" + g.clientId() + ")");
                    id.setStyle("-fx-text-fill: #858585; -fx-font-size: 10px;");
                    row.getChildren().addAll(dot, name, id);
                    guestsListContainer.getChildren().add(row);
                }
            }
        });
    }
}
