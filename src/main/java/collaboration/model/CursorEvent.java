package collaboration.model;

/**
 * Lightweight cursor and selection tracking event for remote peers.
 */
public record CursorEvent(
        String clientId,
        String clientName,
        String colorHex,
        String fileUri,
        int line,
        int column,
        int selectionStart,
        int selectionEnd
) {}
