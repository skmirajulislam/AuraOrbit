package view.fx;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import org.fxmisc.richtext.CodeArea;
import org.kordamp.ikonli.javafx.FontIcon;
import service.AutoCompleteService.CompletionItem;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Floating VS Code-identical IntelliSense autocompletion popup.
 * Dynamically positions itself at the active caret coordinates and provides
 * keyboard-driven navigation (Up/Down, Tab, Enter, Esc).
 */
public class AutoCompletePopup extends Popup {

    private final ListView<CompletionItem> listView;
    private Consumer<CompletionItem> onItemSelected;

    public AutoCompletePopup() {
        setAutoHide(true);
        setHideOnEscape(true);

        listView = new ListView<>();
        listView.getStyleClass().add("autocomplete-list");
        listView.setPrefWidth(280);
        listView.setPrefHeight(180);
        listView.setStyle("-fx-background-color: #252526; -fx-border-color: #3c3c3c; -fx-border-radius: 4; -fx-background-radius: 4;");

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CompletionItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    HBox row = new HBox(8);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(2, 6, 2, 6));

                    String iconColor = switch (item.kind()) {
                        case SNIPPET -> "#dcdcaa";
                        case KEYWORD -> "#c586c0";
                        case CLASS -> "#4ec9b0";
                        case METHOD -> "#dcdcaa";
                        case VARIABLE -> "#9cdcfe";
                        default -> "#858585";
                    };

                    FontIcon icon = IconFactory.getIcon(item.kind().getCodicon(), 13, iconColor);
                    Label label = new Label(item.label());
                    label.setStyle("-fx-text-fill: #d4d4d4; -fx-font-family: 'JetBrains Mono', Consolas, monospace; -fx-font-size: 12px;");

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    Label detail = new Label(item.detail());
                    detail.setStyle("-fx-text-fill: #858585; -fx-font-size: 10px;");

                    row.getChildren().addAll(icon, label, spacer, detail);
                    setGraphic(row);
                    updateSelectionStyle(isSelected());
                }
            }

            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(selected);
                updateSelectionStyle(selected);
            }

            private void updateSelectionStyle(boolean selected) {
                if (selected && !isEmpty()) {
                    setStyle("-fx-background-color: #04395e; -fx-text-fill: #ffffff;");
                } else {
                    setStyle("-fx-background-color: transparent;");
                }
            }
        });

        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1 || e.getClickCount() == 2) {
                commitSelection();
            }
        });

        getContent().add(listView);
    }

    /**
     * Anchors the popup right below the CodeArea's current caret screen coordinates.
     */
    public void showAtCaret(CodeArea codeArea, List<CompletionItem> items, Consumer<CompletionItem> onSelect) {
        if (items == null || items.isEmpty()) {
            hide();
            return;
        }

        this.onItemSelected = onSelect;
        listView.getItems().setAll(items);
        listView.getSelectionModel().select(0);

        // Calculate height dynamically up to 8 items
        int visibleCount = Math.min(items.size(), 7);
        listView.setPrefHeight(visibleCount * 26 + 8);

        Optional<Bounds> caretBoundsOpt = codeArea.getCaretBounds();
        if (caretBoundsOpt.isPresent() && codeArea.getScene() != null && codeArea.getScene().getWindow() != null) {
            Bounds b = caretBoundsOpt.get();
            show(codeArea.getScene().getWindow(), b.getMinX(), b.getMaxY() + 4);
        } else if (codeArea.getScene() != null && codeArea.getScene().getWindow() != null) {
            // Fallback position
            Bounds screenBounds = codeArea.localToScreen(codeArea.getBoundsInLocal());
            if (screenBounds != null) {
                show(codeArea.getScene().getWindow(), screenBounds.getMinX() + 60, screenBounds.getMinY() + 80);
            }
        }
    }

    public void selectNext() {
        int idx = listView.getSelectionModel().getSelectedIndex();
        if (idx < listView.getItems().size() - 1) {
            listView.getSelectionModel().select(idx + 1);
            listView.scrollTo(idx + 1);
        } else {
            listView.getSelectionModel().select(0);
            listView.scrollTo(0);
        }
    }

    public void selectPrevious() {
        int idx = listView.getSelectionModel().getSelectedIndex();
        if (idx > 0) {
            listView.getSelectionModel().select(idx - 1);
            listView.scrollTo(idx - 1);
        } else {
            int last = listView.getItems().size() - 1;
            listView.getSelectionModel().select(last);
            listView.scrollTo(last);
        }
    }

    public void commitSelection() {
        CompletionItem selected = listView.getSelectionModel().getSelectedItem();
        if (selected != null && onItemSelected != null) {
            onItemSelected.accept(selected);
        }
        hide();
    }
}
