package collaboration.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.BiConsumer;

/**
 * Modern VS Code Live Share-styled dialog for joining a remote collaboration session.
 */
public class JoinSessionDialog extends Stage {

    private final TextField urlField;
    private final TextField nameField;
    private final Label errorLabel;
    private final Button joinBtn;

    public JoinSessionDialog(Stage owner, BiConsumer<String, String> onJoinRequested) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.UTILITY);
        setTitle("Live Share - Join Collaboration Session");

        VBox root = new VBox(16);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #1e1e1e; -fx-font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;");
        root.setPrefWidth(440);

        // Header
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        FontIcon joinIcon = FontIcon.of(Codicons.LINK_EXTERNAL, 20);
        joinIcon.setIconColor(Color.web("#007acc"));
        Label title = new Label("Join Collaboration Session");
        title.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 15px; -fx-font-weight: bold;");
        header.getChildren().addAll(joinIcon, title);

        // Invite URL input
        VBox urlBox = new VBox(6);
        Label urlTitle = new Label("Collaboration Session URL or Token:");
        urlTitle.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");
        urlField = new TextField();
        urlField.setPromptText("wss://...trycloudflare.com/?token=...");
        urlField.setStyle("-fx-background-color: #252526; -fx-text-fill: #d4d4d4; -fx-border-color: #3c3c3c; -fx-border-radius: 3; -fx-padding: 6 8; -fx-font-size: 12px;");
        urlBox.getChildren().addAll(urlTitle, urlField);

        // User Display Name input
        VBox nameBox = new VBox(6);
        Label nameTitle = new Label("Your Display Name:");
        nameTitle.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");
        nameField = new TextField(System.getProperty("user.name", "Collaborator"));
        nameField.setStyle("-fx-background-color: #252526; -fx-text-fill: #d4d4d4; -fx-border-color: #3c3c3c; -fx-border-radius: 3; -fx-padding: 6 8; -fx-font-size: 12px;");
        nameBox.getChildren().addAll(nameTitle, nameField);

        // Error message label
        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #f14c4c; -fx-font-size: 11px;");
        errorLabel.setVisible(false);

        // Action Buttons
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; -fx-border-color: #3c3c3c; -fx-cursor: hand; -fx-padding: 6 14; -fx-font-size: 12px;");
        cancelBtn.setOnAction(e -> close());

        joinBtn = new Button("Join Session");
        joinBtn.setStyle("-fx-background-color: #007acc; -fx-text-fill: #ffffff; -fx-cursor: hand; -fx-padding: 6 16; -fx-font-size: 12px; -fx-font-weight: bold;");
        joinBtn.setOnAction(e -> {
            String url = urlField.getText().trim();
            String name = nameField.getText().trim();

            if (url.isEmpty()) {
                showError("Please enter a valid session URL.");
                return;
            }

            joinBtn.setDisable(true);
            joinBtn.setText("Connecting...");
            if (onJoinRequested != null) {
                onJoinRequested.accept(url, name);
            }
        });

        footer.getChildren().addAll(cancelBtn, joinBtn);

        root.getChildren().addAll(header, urlBox, nameBox, errorLabel, footer);
        Scene scene = new Scene(root);
        setScene(scene);
    }

    public void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        joinBtn.setDisable(false);
        joinBtn.setText("Join Session");
    }
}
