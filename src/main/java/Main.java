import app.JavaFxEditorApp;
import controller.EditorController;
import javafx.application.Application;

import java.awt.GraphicsEnvironment;

/**
 * Universal Entry Point for the Modern Code Editor & AI IDE.
 * Launches the Modern JavaFX Desktop App by default,
 * or the Console REPL if --cli is passed or in a headless environment.
 */
public class Main {
    public static void main(String[] args) {
        // Suppress legacy rasterizer off-heap warnings on modern JDK 21-25+
        System.setProperty("prism.marlin.offheap", "false");
        System.setProperty("prism.verbose", "false");

        boolean forceCli = false;
        String targetFile = null;

        for (String arg : args) {
            if ("--cli".equalsIgnoreCase(arg) || "-c".equalsIgnoreCase(arg)) {
                forceCli = true;
            } else if (!arg.startsWith("-") && targetFile == null) {
                targetFile = arg;
            }
        }

        if (forceCli || GraphicsEnvironment.isHeadless()) {
            // Launch Console REPL Mode
            EditorController cliController = new EditorController();
            if (targetFile != null) {
                cliController.openOrInitFile(targetFile);
            }
            cliController.start();
        } else {
            // Launch Modern JavaFX Desktop Application
            Application.launch(JavaFxEditorApp.class, args);
        }
    }
}
