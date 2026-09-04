package collaboration.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Dialog for hosting a new collaborative workspace.
 * Allows user to configure server settings and permissions.
 */
public class HostWorkspaceDialog extends Stage {
    private TextField portField;
    private TextField sessionNameField;
    private ComboBox<String> permissionCombo;
    private Button startButton;
    private Button cancelButton;
    private Label statusLabel;

    private boolean confirmed = false;
    private String resultSessionName;
    private int resultPort;
    private String resultPermission;

    public HostWorkspaceDialog() {
        setTitle("Host Collaborative Workspace");
        setWidth(400);
        setHeight(300);
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
        Label titleLabel = new Label("Create Collaborative Workspace");
        titleLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        // Session Name
        VBox sessionBox = new VBox(5);
        Label sessionLabel = new Label("Workspace Name:");
        sessionLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold;");
        sessionNameField = new TextField();
        sessionNameField.setPromptText("e.g., My Project");
        sessionNameField.setText("Untitled Workspace");
        sessionBox.getChildren().addAll(sessionLabel, sessionNameField);

        // Server Port
        VBox portBox = new VBox(5);
        Label portLabel = new Label("Server Port:");
        portLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold;");
        portField = new TextField();
        portField.setPromptText("Leave empty for auto-assign (8080)");
        portField.setText("8080");
        portBox.getChildren().addAll(portLabel, portField);

        // Default Permissions
        VBox permBox = new VBox(5);
        Label permLabel = new Label("Default Guest Permissions:");
        permLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold;");
        permissionCombo = new ComboBox<>();
        permissionCombo.getItems().addAll("READ (View Only)", "WRITE (Edit Code)");
        permissionCombo.setValue("WRITE (Edit Code)");
        permBox.getChildren().addAll(permLabel, permissionCombo);

        // Status
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 9;");

        // Buttons
        HBox buttonBox = new HBox(8);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        startButton = new Button("Start Hosting");
        startButton.setPrefWidth(120);
        startButton.setStyle("-fx-font-size: 11; -fx-padding: 8;");
        startButton.setStyle("-fx-padding: 8; -fx-font-size: 11; -fx-background-color: #4CAF50; -fx-text-fill: white;");

        cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(120);
        cancelButton.setStyle("-fx-padding: 8; -fx-font-size: 11;");

        cancelButton.setOnAction(event -> {
            confirmed = false;
            close();
        });

        startButton.setOnAction(event -> {
            if (validateInputs()) {
                confirmed = true;
                resultSessionName = sessionNameField.getText();
                resultPort = Integer.parseInt(portField.getText());
                resultPermission = permissionCombo.getValue().split(" ")[0];
                close();
            }
        });

        buttonBox.getChildren().addAll(startButton, cancelButton);
        HBox.setHgrow(startButton, javafx.scene.layout.Priority.ALWAYS);

        main.getChildren().addAll(
                titleLabel,
                new Separator(),
                sessionBox,
                portBox,
                permBox,
                statusLabel,
                buttonBox
        );

        VBox.setVgrow(buttonBox, javafx.scene.layout.Priority.ALWAYS);
        return main;
    }

    private boolean validateInputs() {
        String sessionName = sessionNameField.getText().trim();
        if (sessionName.isEmpty()) {
            showError("Session name is required");
            return false;
        }
        if (!sessionName.matches("[A-Za-z0-9 _.-]{1,64}")) {
            showError("Use up to 64 letters, numbers, spaces, dots, dashes, or underscores");
            return false;
        }

        String portStr = portField.getText().trim();
        if (!portStr.isEmpty()) {
            try {
                int port = Integer.parseInt(portStr);
                if (port < 1024 || port > 65535) {
                    showError("Port must be between 1024 and 65535");
                    return false;
                }
            } catch (NumberFormatException ex) {
                showError("Port must be a valid number");
                return false;
            }
        } else {
            portField.setText("8080");
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

    public String getSessionName() {
        return resultSessionName;
    }

    public int getServerPort() {
        return resultPort;
    }

    public String getDefaultPermission() {
        return resultPermission;
    }
}
