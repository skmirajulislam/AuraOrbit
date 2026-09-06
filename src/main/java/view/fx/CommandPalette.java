package view.fx;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern VS Code Command Palette popup (Cmd+P / Ctrl+P).
 * Features:
 * - Fluid keyboard navigation (Up/Down arrows navigate list without stealing focus from search field)
 * - Rich custom cells with Codicon icons, clean command titles, and shortcut pill badges
 * - Instant fuzzy search filtering
 * - VS Code modal drop shadow and responsive sizing
 */
public class CommandPalette extends VBox {

    public static class CommandItem {
        public final String title;
        public final String shortcut;
        public final Runnable action;
        public final Codicons icon;

        public CommandItem(String title, String shortcut, Runnable action) {
            this(title, shortcut, action, resolveIcon(title));
        }

        public CommandItem(String title, String shortcut, Runnable action, Codicons icon) {
            this.title = title;
            this.shortcut = shortcut;
            this.action = action;
            this.icon = icon;
        }

        private static Codicons resolveIcon(String title) {
            String lower = title.toLowerCase();
            if (lower.contains("new file")) return Codicons.NEW_FILE;
            if (lower.contains("open file")) return Codicons.FOLDER_OPENED;
            if (lower.contains("save as")) return Codicons.SAVE_ALL;
            if (lower.contains("save")) return Codicons.SAVE;
            if (lower.contains("close all")) return Codicons.CLEAR_ALL;
            if (lower.contains("close")) return Codicons.CLOSE;
            if (lower.contains("find")) return Codicons.SEARCH;
            if (lower.contains("split")) return Codicons.SPLIT_HORIZONTAL;
            if (lower.contains("copilot") || lower.contains("ai")) return Codicons.HUBOT;
            if (lower.contains("explorer")) return Codicons.FILES;
            if (lower.contains("terminal")) return Codicons.TERMINAL;
            if (lower.contains("theme")) return Codicons.COLOR_MODE;
            return Codicons.CHEVRON_RIGHT;
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
        getStyleClass().add("command-palette-box");
        setSpacing(6);
        setPadding(new Insets(8));
        setPrefWidth(540);
        setMaxWidth(540);
        setMinWidth(0);
        setMaxHeight(Region.USE_PREF_SIZE);
        setVisible(false);
        setManaged(false);
        setPickOnBounds(false);

        searchField = new TextField();
        searchField.setPromptText("Type a command or search actions...");
        searchField.setMinWidth(0);
        searchField.getStyleClass().add("command-palette-input");

        listView = new ListView<>(filteredCommands);
        listView.setPrefHeight(280);
        listView.setMaxHeight(280);
        listView.setMinWidth(0);
        listView.getStyleClass().add("command-palette-list");

        // Custom Cell Factory for VS Code styling
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CommandItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    HBox cellBox = new HBox(10);
                    cellBox.setAlignment(Pos.CENTER_LEFT);
                    cellBox.setPadding(new Insets(5, 8, 5, 8));

                    FontIcon iconNode = IconFactory.getIcon(item.icon, 13);

                    Label titleNode = new Label(item.title);
                    titleNode.getStyleClass().add("command-palette-title");

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    cellBox.getChildren().addAll(iconNode, titleNode, spacer);

                    if (item.shortcut != null && !item.shortcut.isBlank()) {
                        Label shortcutNode = new Label(item.shortcut);
                        shortcutNode.getStyleClass().add("shortcut-badge");
                        cellBox.getChildren().add(shortcutNode);
                    }

                    setGraphic(cellBox);
                    setText(null);
                }
            }
        });

        searchField.textProperty().addListener((obs, oldVal, newVal) -> filter(newVal));

        // Smooth Keyboard Navigation: Up/Down keeps cursor and focus in searchField!
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) {
                int next = Math.min(listView.getSelectionModel().getSelectedIndex() + 1, filteredCommands.size() - 1);
                if (next >= 0) {
                    listView.getSelectionModel().select(next);
                    listView.scrollTo(next);
                }
                e.consume();
            } else if (e.getCode() == KeyCode.UP) {
                int prev = Math.max(listView.getSelectionModel().getSelectedIndex() - 1, 0);
                if (prev >= 0 && !filteredCommands.isEmpty()) {
                    listView.getSelectionModel().select(prev);
                    listView.scrollTo(prev);
                }
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                executeSelected();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                hidePalette();
                e.consume();
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
            if (e.getClickCount() == 1 || e.getClickCount() == 2) {
                executeSelected();
            }
        });

        // Click outside anywhere in scene dismisses Command Palette
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                    if (isVisible()) {
                        javafx.geometry.Bounds bounds = localToScene(getBoundsInLocal());
                        if (bounds != null && !bounds.contains(e.getSceneX(), e.getSceneY())) {
                            hidePalette();
                        }
                    }
                });
            }
        });

        getChildren().addAll(searchField, listView);
    }

    public void registerCommand(String title, String shortcut, Runnable action) {
        allCommands.add(new CommandItem(title, shortcut, action));
    }

    public void registerCommand(String title, String shortcut, Runnable action, Codicons icon) {
        allCommands.add(new CommandItem(title, shortcut, action, icon));
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
