package collaboration.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Dialog for joining an existing collaborative workspace.
 * Accepts connection string (host:port) and username.
 */
public class JoinWorkspaceDialog extends Stage {
    private TextField connectionField;
    private TextField sessionNameField;
    private TextField userNameField;
    private Button joinButton;
    private Button cancelButton;
    private Label statusLabel;

    private boolean confirmed = false;
    private String resultHost;
    private int resultPort;
    private String resultUserName;
    private String resultSessionName;

    public JoinWorkspaceDialog() {
        setTitle("Join Collaborative Workspace");
        setWidth(400);
        setHeight(330);
        setResizable(false);

        VBox mainBox = createMainContent();
        Scene scene = new Scene(mainBox);
        setScene(scene);

        setOnCloseRequest(event -> {
            confirmed = false;
        });
    }

    private VBox createMainContent() {
        VBox main = new VBox(12);
        main.setPadding(new Insets(20));

        // Title
        Label titleLabel = new Label("Join Collaborative Workspace");
        titleLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        // Connection Details
        VBox connBox = new VBox(5);
        Label connLabel = new Label("Host Connection:");
        connLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold;");
        connectionField = new TextField();
        connectionField.setPromptText("e.g., localhost:8080 or 192.168.1.100:8080");
        connBox.getChildren().addAll(connLabel, connectionField);

        VBox sessionBox = new VBox(5);
        Label sessionLabel = new Label("Workspace Name:");
        sessionLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold;");
        sessionNameField = new TextField("Untitled Workspace");
        sessionNameField.setPromptText("Ask the host for this name");
        sessionBox.getChildren().addAll(sessionLabel, sessionNameField);

        // User Name
        VBox userBox = new VBox(5);
        Label userLabel = new Label("Your Name:");
        userLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold;");
        userNameField = new TextField();
        userNameField.setPromptText("Enter your name");
        userNameField.setText("Guest");
        userBox.getChildren().addAll(userLabel, userNameField);

        // Info
        Label infoLabel = new Label("💡 Ask the host for their connection link");
        infoLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 9; -fx-font-style: italic;");

        // Status
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 9;");

        // Buttons
        HBox buttonBox = new HBox(8);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        joinButton = new Button("Join Workspace");
        joinButton.setPrefWidth(120);
        joinButton.setStyle("-fx-padding: 8; -fx-font-size: 11; -fx-background-color: #2196F3; -fx-text-fill: white;");

        cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(120);
        cancelButton.setStyle("-fx-padding: 8; -fx-font-size: 11;");

        cancelButton.setOnAction(event -> {
            confirmed = false;
            close();
        });

        joinButton.setOnAction(event -> {
            if (validateInputs()) {
                confirmed = true;
                close();
            }
        });

        buttonBox.getChildren().addAll(joinButton, cancelButton);
        HBox.setHgrow(joinButton, javafx.scene.layout.Priority.ALWAYS);

        main.getChildren().addAll(
                titleLabel,
                new Separator(),
                connBox,
                sessionBox,
                userBox,
                infoLabel,
                statusLabel,
                buttonBox
        );

        VBox.setVgrow(buttonBox, javafx.scene.layout.Priority.ALWAYS);
        return main;
    }

    private boolean validateInputs() {
        String connection = connectionField.getText().trim();
        if (connection.isEmpty()) {
            showError("Connection string is required");
            return false;
        }

        // Parse host:port
        String[] parts = connection.split(":");
        if (parts.length != 2) {
            showError("Format must be host:port (e.g., localhost:8080)");
            return false;
        }

        try {
            resultHost = parts[0].trim();
            resultPort = Integer.parseInt(parts[1].trim());

            if (resultPort < 1024 || resultPort > 65535) {
                showError("Port must be between 1024 and 65535");
                return false;
            }
        } catch (NumberFormatException ex) {
            showError("Port must be a valid number");
            return false;
        }

        resultUserName = userNameField.getText().trim();
        if (resultUserName.isEmpty()) {
            showError("Your name is required");
            return false;
        }

        resultSessionName = sessionNameField.getText().trim();
        if (resultSessionName.isEmpty()) {
            showError("Workspace name is required");
            return false;
        }
        if (!resultSessionName.matches("[A-Za-z0-9 _.-]{1,64}")) {
            showError("Workspace name contains unsupported characters");
            return false;
        }

        return true;
    }

    private void showError(String message) {
        statusLabel.setText("❌ " + message);
        statusLabel.setStyle("-fx-text-fill: #f44336; -fx-font-size: 9;");
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getHost() {
        return resultHost;
    }

    public int getPort() {
        return resultPort;
    }

    public String getUserName() {
        return resultUserName;
    }

    public String getSessionName() {
        return resultSessionName;
    }
}
