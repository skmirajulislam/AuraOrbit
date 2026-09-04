package view.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;

/**
 * Modern floating Find and Replace bar (VS Code style).
 * Features:
 * - Genuine VS Code Codicons for navigation, toggle, and close
 * - Match Case toggle chip
 * - Result count chip
 * - In-place replace and replace-all
 */
public class FindReplaceBar extends VBox {

    private final TextField findField;
    private final TextField replaceField;
    private final CheckBox matchCaseCheck;
    private final Label resultLabel;
    private final HBox replaceRow;
    private final Button toggleReplaceBtn;
    private final FontIcon toggleChevronIcon;

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
        setPadding(new Insets(6, 10, 6, 10));
        setMaxWidth(460);
        setVisible(false);
        setManaged(false);

        // Find Row
        HBox findRow = new HBox(6);
        findRow.setAlignment(Pos.CENTER_LEFT);

        toggleChevronIcon = IconFactory.getIcon(Codicons.CHEVRON_RIGHT, 12);
        toggleReplaceBtn = new Button();
        toggleReplaceBtn.setGraphic(toggleChevronIcon);
        toggleReplaceBtn.getStyleClass().add("find-icon-btn");
        toggleReplaceBtn.setTooltip(new Tooltip("Toggle Replace Mode"));

        findField = new TextField();
        findField.setPromptText("Find");
        findField.setPrefWidth(160);
        findField.getStyleClass().add("find-text-input");

        Button prevBtn = new Button();
        prevBtn.setGraphic(IconFactory.getIcon(Codicons.ARROW_UP, 12));
        prevBtn.getStyleClass().add("find-icon-btn");
        prevBtn.setTooltip(new Tooltip("Previous Match (Shift+Enter)"));

        Button nextBtn = new Button();
        nextBtn.setGraphic(IconFactory.getIcon(Codicons.ARROW_DOWN, 12));
        nextBtn.getStyleClass().add("find-icon-btn");
        nextBtn.setTooltip(new Tooltip("Next Match (Enter)"));

        matchCaseCheck = new CheckBox("Aa");
        matchCaseCheck.getStyleClass().add("find-case-toggle");
        matchCaseCheck.setTooltip(new Tooltip("Match Case (Alt+C)"));

        resultLabel = new Label("No results");
        resultLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -text-secondary;");

        Button closeBtn = new Button();
        closeBtn.setGraphic(IconFactory.getIcon(Codicons.CLOSE, 11));
        closeBtn.getStyleClass().add("find-icon-btn");
        closeBtn.setTooltip(new Tooltip("Close (Escape)"));
        closeBtn.setOnAction(e -> hideBar());

        findRow.getChildren().addAll(toggleReplaceBtn, findField, prevBtn, nextBtn, matchCaseCheck, resultLabel, closeBtn);

        // Replace Row
        replaceRow = new HBox(6);
        replaceRow.setAlignment(Pos.CENTER_LEFT);
        replaceRow.setVisible(false);
        replaceRow.setManaged(false);

        Label indentSpacer = new Label("    ");
        indentSpacer.setPrefWidth(22);

        replaceField = new TextField();
        replaceField.setPromptText("Replace");
        replaceField.setPrefWidth(160);
        replaceField.getStyleClass().add("find-text-input");

        Button replaceBtn = new Button();
        replaceBtn.setGraphic(IconFactory.getIcon(Codicons.REPLACE, 12));
        replaceBtn.setText(" Replace");
        replaceBtn.getStyleClass().add("find-action-btn");

        Button replaceAllBtn = new Button();
        replaceAllBtn.setGraphic(IconFactory.getIcon(Codicons.REPLACE_ALL, 12));
        replaceAllBtn.setText(" All");
        replaceAllBtn.getStyleClass().add("find-action-btn");

        replaceRow.getChildren().addAll(indentSpacer, replaceField, replaceBtn, replaceAllBtn);

        getChildren().addAll(findRow, replaceRow);

        // Event Handlers
        toggleReplaceBtn.setOnAction(e -> {
            boolean show = !replaceRow.isVisible();
            replaceRow.setVisible(show);
            replaceRow.setManaged(show);
            toggleChevronIcon.setIconCode(show ? Codicons.CHEVRON_DOWN : Codicons.CHEVRON_RIGHT);
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
        toggleChevronIcon.setIconCode(withReplace ? Codicons.CHEVRON_DOWN : Codicons.CHEVRON_RIGHT);
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
