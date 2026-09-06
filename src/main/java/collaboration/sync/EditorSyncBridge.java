package collaboration.sync;

import collaboration.model.CursorEvent;
import collaboration.model.TextOpEvent;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.PlainTextChange;
import org.reactfx.Subscription;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * High-performance bidirectional bridge between RichTextFX CodeArea and
 * the collaboration synchronization engine. Handles debouncing, character
 * splice extraction, and echo-suppression loops.
 */
public class EditorSyncBridge {

    private final CodeArea codeArea;
    private final String fileUri;
    private final String localClientId;
    private final String localClientName;
    private final String localColorHex;

    // Echo-suppression guard: prevents remote incoming edits from re-triggering outbound network packets
    private final AtomicBoolean isApplyingRemoteEdit = new AtomicBoolean(false);

    // Debouncing executor for cursor and text edits (prevents flooding network)
    private final ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Editor-Sync-Debouncer");
        t.setDaemon(true);
        return t;
    });

    private Consumer<TextOpEvent> onLocalTextOp;
    private Consumer<CursorEvent> onLocalCursorMove;

    private CursorEvent pendingCursorEvent;
    private Subscription textChangeSub;
    private ChangeListener<Number> caretListener;

    public EditorSyncBridge(CodeArea codeArea, String fileUri, String localClientId, String localClientName, String localColorHex) {
        this.codeArea = codeArea;
        this.fileUri = fileUri;
        this.localClientId = localClientId;
        this.localClientName = localClientName;
        this.localColorHex = localColorHex;

        setupLocalListeners();
    }

    private void setupLocalListeners() {
        // 1. Text edit listener via RichTextFX plainTextChanges
        textChangeSub = codeArea.plainTextChanges().subscribe(this::handleLocalTextChange);

        // 2. Cursor movement listener (debounced at 35ms)
        caretListener = (obs, oldPos, newPos) -> {
            if (isApplyingRemoteEdit.get()) return;

            int paragraph = codeArea.getCurrentParagraph();
            int column = codeArea.getCaretColumn();
            int selStart = codeArea.getSelection().getStart();
            int selEnd = codeArea.getSelection().getEnd();

            synchronized (this) {
                pendingCursorEvent = new CursorEvent(
                        localClientId, localClientName, localColorHex,
                        fileUri, paragraph, column, selStart, selEnd
                );
            }

            debounceExecutor.schedule(() -> {
                CursorEvent toSend;
                synchronized (EditorSyncBridge.this) {
                    toSend = pendingCursorEvent;
                    pendingCursorEvent = null;
                }
                if (toSend != null && onLocalCursorMove != null) {
                    onLocalCursorMove.accept(toSend);
                }
            }, 35, TimeUnit.MILLISECONDS);
        };
        codeArea.caretPositionProperty().addListener(caretListener);
    }

    private void handleLocalTextChange(PlainTextChange change) {
        // Echo suppression: If this change was caused by a remote peer's packet, ignore it!
        if (isApplyingRemoteEdit.get()) {
            return;
        }

        int offset = change.getPosition();
        String inserted = change.getInserted();
        String removed = change.getRemoved();
        int length = removed != null ? removed.length() : 0;

        TextOpEvent op = new TextOpEvent(
                fileUri,
                0L, // revision filled by Host/Client session
                offset,
                length,
                inserted,
                removed,
                localClientId
        );

        if (onLocalTextOp != null) {
            onLocalTextOp.accept(op);
        }
    }

    /**
     * Applies a remote peer's transformed delta into the local RichTextFX CodeArea.
     * Enforces echo-suppression so no outbound packet is broadcast.
     */
    public void applyRemoteOp(TextOpEvent op) {
        if (!fileUri.equals(op.fileUri())) {
            return;
        }

        Platform.runLater(() -> {
            isApplyingRemoteEdit.set(true);
            try {
                int docLen = codeArea.getLength();
                int start = Math.min(Math.max(0, op.offset()), docLen);
                int end = Math.min(start + op.length(), docLen);

                String inserted = op.insertedText() != null ? op.insertedText() : "";
                codeArea.replaceText(start, end, inserted);
            } finally {
                isApplyingRemoteEdit.set(false);
            }
        });
    }

    public boolean isApplyingRemoteEdit() {
        return isApplyingRemoteEdit.get();
    }

    public void setOnLocalTextOp(Consumer<TextOpEvent> onLocalTextOp) {
        this.onLocalTextOp = onLocalTextOp;
    }

    public void setOnLocalCursorMove(Consumer<CursorEvent> onLocalCursorMove) {
        this.onLocalCursorMove = onLocalCursorMove;
    }

    public void dispose() {
        if (textChangeSub != null) {
            textChangeSub.unsubscribe();
            textChangeSub = null;
        }
        if (caretListener != null) {
            codeArea.caretPositionProperty().removeListener(caretListener);
            caretListener = null;
        }
        debounceExecutor.shutdownNow();
    }
}
