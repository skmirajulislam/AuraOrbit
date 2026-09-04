package view.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Modern floating Find and Replace bar (VS Code style).
 */
public class FindReplaceBar extends VBox {

    private final TextField findField;
    private final TextField replaceField;
    private final CheckBox matchCaseCheck;
    private final Label resultLabel;
    private final HBox replaceRow;

    private Consumer<FindRequest> onFindNext;
    private Consumer<FindRequest> onFindPrev;
    private Consumer<ReplaceRequest> onReplace;
    private Consumer<ReplaceRequest> onReplaceAll;

    public static class FindRequest {
        public final String query;
        public final boolean matchCase;
        public FindRequest(String query, boolean matchCase) {
            this.query = query;
            this.matchCase = matchCase;
        }
    }

    public static class ReplaceRequest {
        public final String query;
        public final String replacement;
        public final boolean matchCase;
        public ReplaceRequest(String query, String replacement, boolean matchCase) {
            this.query = query;
            this.replacement = replacement;
            this.matchCase = matchCase;
        }
    }

    public FindReplaceBar() {
        getStyleClass().add("search-bar");
        setSpacing(6);
        setPadding(new Insets(8, 12, 8, 12));
        setMaxWidth(420);
        setVisible(false);
        setManaged(false);

        // Find Row
        HBox findRow = new HBox(8);
        findRow.setAlignment(Pos.CENTER_LEFT);

        findField = new TextField();
        findField.setPromptText("Find");
        findField.setPrefWidth(160);

        Button prevBtn = new Button("▲");
        prevBtn.setTooltip(new Tooltip("Previous Match (Shift+Enter)"));
        Button nextBtn = new Button("▼");
        nextBtn.setTooltip(new Tooltip("Next Match (Enter)"));

        matchCaseCheck = new CheckBox("Aa");
        matchCaseCheck.setTooltip(new Tooltip("Match Case"));

        resultLabel = new Label("No results");
        resultLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -text-secondary;");

        Button toggleReplaceBtn = new Button("▾");
        toggleReplaceBtn.setTooltip(new Tooltip("Toggle Replace Mode"));

        Button closeBtn = new Button("✕");
        closeBtn.setTooltip(new Tooltip("Close (Escape)"));
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -text-secondary; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> hideBar());

        findRow.getChildren().addAll(toggleReplaceBtn, findField, prevBtn, nextBtn, matchCaseCheck, resultLabel, closeBtn);

        // Replace Row
        replaceRow = new HBox(8);
        replaceRow.setAlignment(Pos.CENTER_LEFT);
        replaceRow.setVisible(false);
        replaceRow.setManaged(false);

        replaceField = new TextField();
        replaceField.setPromptText("Replace");
        replaceField.setPrefWidth(160);

        Button replaceBtn = new Button("Replace");
        Button replaceAllBtn = new Button("Replace All");

        replaceRow.getChildren().addAll(new Label("   "), replaceField, replaceBtn, replaceAllBtn);

        getChildren().addAll(findRow, replaceRow);

        // Event Handlers
        toggleReplaceBtn.setOnAction(e -> {
            boolean show = !replaceRow.isVisible();
            replaceRow.setVisible(show);
            replaceRow.setManaged(show);
            toggleReplaceBtn.setText(show ? "▴" : "▾");
        });

        findField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (e.isShiftDown()) triggerFindPrev();
                else triggerFindNext();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                hideBar();
            }
        });

        replaceField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                triggerReplace();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                hideBar();
            }
        });

        prevBtn.setOnAction(e -> triggerFindPrev());
        nextBtn.setOnAction(e -> triggerFindNext());
        replaceBtn.setOnAction(e -> triggerReplace());
        replaceAllBtn.setOnAction(e -> triggerReplaceAll());
    }

    public void showBar(boolean withReplace, String prefillQuery) {
        setVisible(true);
        setManaged(true);
        if (prefillQuery != null && !prefillQuery.isBlank()) {
            findField.setText(prefillQuery);
        }
        replaceRow.setVisible(withReplace);
        replaceRow.setManaged(withReplace);
        findField.requestFocus();
        findField.selectAll();
    }

    public void hideBar() {
        setVisible(false);
        setManaged(false);
    }

    public void setResultText(String text) {
        resultLabel.setText(text);
    }

    private void triggerFindNext() {
        if (onFindNext != null) {
            onFindNext.accept(new FindRequest(findField.getText(), matchCaseCheck.isSelected()));
        }
    }

    private void triggerFindPrev() {
        if (onFindPrev != null) {
            onFindPrev.accept(new FindRequest(findField.getText(), matchCaseCheck.isSelected()));
        }
    }

    private void triggerReplace() {
        if (onReplace != null) {
            onReplace.accept(new ReplaceRequest(findField.getText(), replaceField.getText(), matchCaseCheck.isSelected()));
        }
    }

    private void triggerReplaceAll() {
        if (onReplaceAll != null) {
            onReplaceAll.accept(new ReplaceRequest(findField.getText(), replaceField.getText(), matchCaseCheck.isSelected()));
        }
    }

    public void setOnFindNext(Consumer<FindRequest> onFindNext) { this.onFindNext = onFindNext; }
    public void setOnFindPrev(Consumer<FindRequest> onFindPrev) { this.onFindPrev = onFindPrev; }
    public void setOnReplace(Consumer<ReplaceRequest> onReplace) { this.onReplace = onReplace; }
    public void setOnReplaceAll(Consumer<ReplaceRequest> onReplaceAll) { this.onReplaceAll = onReplaceAll; }
}
