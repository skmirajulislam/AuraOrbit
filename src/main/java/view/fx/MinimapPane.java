package view.fx;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.fxmisc.richtext.CodeArea;

import java.util.ArrayList;
import java.util.List;

/**
 * High-performance interactive Code Minimap & Overview Ruler.
 * Renders a scaled micro-canvas preview of code structure,
 * viewport highlight with click-to-scroll, and error/warning overview ticks.
 */
public class MinimapPane extends StackPane {

    private static final double MINIMAP_WIDTH = 90.0;
    private static final double LINE_HEIGHT = 2.0;

    private final CodeArea codeArea;
    private final Canvas canvas;
    private final Region viewportSlider;
    private final Pane tickOverlay;

    private final List<Integer> errorLines = new ArrayList<>();
    private final List<Integer> warningLines = new ArrayList<>();

    public MinimapPane(CodeArea codeArea) {
        this.codeArea = codeArea;
        setPrefWidth(MINIMAP_WIDTH);
        setMaxWidth(MINIMAP_WIDTH);
        setMinWidth(MINIMAP_WIDTH);
        getStyleClass().add("minimap-pane");
        setVisible(true);
        setManaged(true);

        this.canvas = new Canvas(MINIMAP_WIDTH, 600);
        this.canvas.widthProperty().bind(widthProperty());

        // Viewport highlight slider
        this.viewportSlider = new Region();
        this.viewportSlider.getStyleClass().add("minimap-slider");
        this.viewportSlider.setManaged(false);
        this.viewportSlider.setMouseTransparent(true);

        // Overlay for overview ruler ticks
        this.tickOverlay = new Pane();
        this.tickOverlay.setMouseTransparent(true);

        getChildren().addAll(canvas, tickOverlay, viewportSlider);
        setAlignment(Pos.TOP_LEFT);

        // Listen for canvas resizing
        heightProperty().addListener((obs, oldH, newH) -> {
            if (newH.doubleValue() > 0) {
                canvas.setHeight(newH.doubleValue());
                renderMinimap();
                updateSlider();
            }
        });

        // Mouse interaction for instant scrolling
        setOnMousePressed(this::handleMouseScroll);
        setOnMouseDragged(this::handleMouseScroll);

        // Hook into CodeArea scroll and text changes
        codeArea.estimatedScrollYProperty().addListener((obs, o, n) -> updateSlider());
        codeArea.totalHeightEstimateProperty().addListener((obs, o, n) -> updateSlider());
        codeArea.plainTextChanges().subscribe(ch -> Platform.runLater(this::renderMinimap));

        visibleProperty().addListener((obs, oldV, newV) -> {
            if (Boolean.TRUE.equals(newV)) {
                Platform.runLater(() -> {
                    renderMinimap();
                    updateSlider();
                });
            }
        });
    }

    private void handleMouseScroll(MouseEvent e) {
        double y = Math.max(0, Math.min(e.getY(), getHeight()));
        int totalParagraphs = codeArea.getParagraphs().size();
        if (totalParagraphs <= 0 || getHeight() <= 0) return;

        double fraction = y / getHeight();
        int targetParagraph = (int) (fraction * totalParagraphs);
        codeArea.showParagraphAtCenter(Math.min(totalParagraphs - 1, Math.max(0, targetParagraph)));
    }

    /**
     * Debounced / background rendering of code lines.
     */
    public void renderMinimap() {
        if (getWidth() <= 0 || getHeight() <= 0) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        int lineCount = codeArea.getParagraphs().size();
        if (lineCount == 0) return;

        double availableHeight = getHeight();
        double scale = Math.min(1.0, availableHeight / (lineCount * LINE_HEIGHT));
        double actualLineHeight = Math.max(1.0, LINE_HEIGHT * scale);

        gc.setFill(Color.web("#808080", 0.35));

        for (int i = 0; i < lineCount; i++) {
            CharSequence line = codeArea.getParagraph(i).getText();
            int len = line.length();
            if (len == 0) continue;

            // Indentation
            int leadingSpaces = 0;
            while (leadingSpaces < len && Character.isWhitespace(line.charAt(leadingSpaces))) {
                leadingSpaces++;
            }

            double x = Math.min(30, leadingSpaces * 2.0);
            double w = Math.min(MINIMAP_WIDTH - x - 8, (len - leadingSpaces) * 1.5);
            double y = i * actualLineHeight;

            if (y > availableHeight) break;

            gc.fillRect(x + 4, y, Math.max(4, w), actualLineHeight);
        }

        renderTicks();
    }

    private void updateSlider() {
        Platform.runLater(() -> {
            int lineCount = codeArea.getParagraphs().size();
            if (lineCount <= 0 || getHeight() <= 0) return;

            double scrollY = codeArea.estimatedScrollYProperty().getValue();
            double totalHeight = codeArea.totalHeightEstimateProperty().getValue();
            double viewportH = codeArea.getHeight();

            if (totalHeight <= 0) {
                viewportSlider.setVisible(false);
                return;
            }

            viewportSlider.setVisible(true);
            double ratio = Math.min(1.0, viewportH / totalHeight);
            double sliderHeight = Math.max(20.0, getHeight() * ratio);
            double scrollRatio = scrollY / (totalHeight - viewportH > 0 ? totalHeight - viewportH : 1.0);
            double sliderY = (getHeight() - sliderHeight) * Math.max(0.0, Math.min(1.0, scrollRatio));

            viewportSlider.resizeRelocate(0, sliderY, MINIMAP_WIDTH, sliderHeight);
        });
    }

    public void updateDiagnostics(List<Integer> errors, List<Integer> warnings) {
        this.errorLines.clear();
        if (errors != null) this.errorLines.addAll(errors);

        this.warningLines.clear();
        if (warnings != null) this.warningLines.addAll(warnings);

        Platform.runLater(this::renderTicks);
    }

    private void renderTicks() {
        tickOverlay.getChildren().clear();
        int totalLines = codeArea.getParagraphs().size();
        if (totalLines <= 0 || getHeight() <= 0) return;

        double h = getHeight();
        for (int line : errorLines) {
            double y = ((double) line / totalLines) * h;
            Region tick = new Region();
            tick.setStyle("-fx-background-color: #f85149;");
            tick.resizeRelocate(MINIMAP_WIDTH - 6, y, 6, 3);
            tickOverlay.getChildren().add(tick);
        }

        for (int line : warningLines) {
            double y = ((double) line / totalLines) * h;
            Region tick = new Region();
            tick.setStyle("-fx-background-color: #cca700;");
            tick.resizeRelocate(MINIMAP_WIDTH - 6, y, 6, 3);
            tickOverlay.getChildren().add(tick);
        }
    }
}
