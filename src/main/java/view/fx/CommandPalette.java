package view.fx;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern VS Code Command Palette popup (Cmd+P / Ctrl+P).
 */
public class CommandPalette extends VBox {

    public static class CommandItem {
        public final String title;
        public final String shortcut;
        public final Runnable action;

        public CommandItem(String title, String shortcut, Runnable action) {
            this.title = title;
            this.shortcut = shortcut;
            this.action = action;
        }

        @Override
        public String toString() {
            return shortcut != null && !shortcut.isEmpty() ? title + " (" + shortcut + ")" : title;
        }
    }

    private final TextField searchField;
    private final ListView<CommandItem> listView;
    private final List<CommandItem> allCommands = new ArrayList<>();
    private final ObservableList<CommandItem> filteredCommands = FXCollections.observableArrayList();

    public CommandPalette() {
        getStyleClass().add("search-bar");
        setSpacing(6);
        setPadding(new Insets(10));
        setPrefWidth(500);
        setMaxWidth(550);
        setVisible(false);
        setManaged(false);

        searchField = new TextField();
        searchField.setPromptText("Type a command or search actions...");
        searchField.setStyle("-fx-font-size: 14px; -fx-padding: 8;");

        listView = new ListView<>(filteredCommands);
        listView.setPrefHeight(220);
        listView.setStyle("-fx-background-color: transparent; -fx-border-color: -border-color; -fx-border-width: 1 0 0 0;");

        searchField.textProperty().addListener((obs, oldVal, newVal) -> filter(newVal));

        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) {
                listView.requestFocus();
                listView.getSelectionModel().select(0);
            } else if (e.getCode() == KeyCode.ENTER) {
                executeSelected();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                hidePalette();
            }
        });

        listView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                executeSelected();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                hidePalette();
            }
        });

        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                executeSelected();
            }
        });

        getChildren().addAll(searchField, listView);
    }

    public void registerCommand(String title, String shortcut, Runnable action) {
        allCommands.add(new CommandItem(title, shortcut, action));
    }

    public void showPalette() {
        filter("");
        searchField.setText("");
        setVisible(true);
        setManaged(true);
        searchField.requestFocus();
        if (!filteredCommands.isEmpty()) {
            listView.getSelectionModel().select(0);
        }
    }

    public void hidePalette() {
        setVisible(false);
        setManaged(false);
    }

    private void filter(String query) {
        filteredCommands.clear();
        String q = query != null ? query.trim().toLowerCase() : "";
        for (CommandItem item : allCommands) {
            if (q.isEmpty() || item.title.toLowerCase().contains(q) || (item.shortcut != null && item.shortcut.toLowerCase().contains(q))) {
                filteredCommands.add(item);
            }
        }
        if (!filteredCommands.isEmpty()) {
            listView.getSelectionModel().select(0);
        }
    }

    private void executeSelected() {
        CommandItem selected = listView.getSelectionModel().getSelectedItem();
        if (selected != null && selected.action != null) {
            hidePalette();
            selected.action.run();
        }
    }
}
