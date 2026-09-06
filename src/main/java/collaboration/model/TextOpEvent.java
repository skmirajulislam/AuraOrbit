package collaboration.model;

/**
 * Immutable granular delta operation representing a text insert or delete
 * for server-authoritative Operational Transformation (OT).
 */
public record TextOpEvent(
        String fileUri,
        long revision,
        int offset,
        int length,
        String insertedText,
        String deletedText,
        String originClientId
) {}
