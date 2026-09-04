package collaboration.sync;

/**
 * Operational Transform (OT) algorithm for concurrent text editing.
 * Ensures consistency across all clients without conflicts.
 */
public class OperationalTransform {

    /**
     * Represents a single edit operation (insert or delete).
     */
    public static class Operation {
        public final OperationType type; // INSERT or DELETE
        public final int position;       // Cursor position
        public final String content;     // Text to insert (for INSERT)
        public final int length;         // Length to delete (for DELETE)
        public final String userId;      // Who made the edit
        public final long timestamp;     // When the edit was made
        public final int revision;       // Document revision at time of edit

        public Operation(OperationType type, int position, String content, int length,
                        String userId, long timestamp, int revision) {
            this.type = type;
            this.position = position;
            this.content = content;
            this.length = length;
            this.userId = userId;
            this.timestamp = timestamp;
            this.revision = revision;
        }

        public static Operation insert(int position, String content, String userId, int revision) {
            return new Operation(OperationType.INSERT, position, content, 0, userId, System.currentTimeMillis(), revision);
        }

        public static Operation delete(int position, int length, String userId, int revision) {
            return new Operation(OperationType.DELETE, position, "", length, userId, System.currentTimeMillis(), revision);
        }

        @Override
        public String toString() {
            if (type == OperationType.INSERT) {
                return String.format("INSERT(%d, \"%s\") by %s", position, content, userId);
            } else {
                return String.format("DELETE(%d, len=%d) by %s", position, length, userId);
            }
        }
    }

    public enum OperationType {
        INSERT, DELETE
    }

    /**
     * Transform operation A against operation B to resolve conflicts.
     * Returns the transformed version of A that can be applied after B.
     */
    public static Operation transform(Operation a, Operation b) {
        if (a.type == OperationType.INSERT && b.type == OperationType.INSERT) {
            return transformInsertInsert(a, b);
        } else if (a.type == OperationType.INSERT && b.type == OperationType.DELETE) {
            return transformInsertDelete(a, b);
        } else if (a.type == OperationType.DELETE && b.type == OperationType.INSERT) {
            return transformDeleteInsert(a, b);
        } else {
            return transformDeleteDelete(a, b);
        }
    }

    /**
     * Transform: INSERT vs INSERT
     */
    private static Operation transformInsertInsert(Operation a, Operation b) {
        if (a.position < b.position) {
            return a; // No change needed
        } else if (a.position > b.position) {
            // Shift a's position to the right
            return new Operation(a.type, a.position + b.content.length(), a.content,
                    a.length, a.userId, a.timestamp, a.revision);
        } else {
            // Same position: use user ID or timestamp to break tie
            if (a.userId.compareTo(b.userId) < 0) {
                return a;
            } else {
                return new Operation(a.type, a.position + b.content.length(), a.content,
                        a.length, a.userId, a.timestamp, a.revision);
            }
        }
    }

    /**
     * Transform: INSERT vs DELETE
     */
    private static Operation transformInsertDelete(Operation a, Operation b) {
        if (a.position <= b.position) {
            return a; // Insertion is before deletion
        } else if (a.position >= b.position + b.length) {
            // Insertion is after deletion
            return new Operation(a.type, a.position - b.length, a.content,
                    a.length, a.userId, a.timestamp, a.revision);
        } else {
            // Insertion is inside deletion range
            return new Operation(a.type, b.position, a.content,
                    a.length, a.userId, a.timestamp, a.revision);
        }
    }

    /**
     * Transform: DELETE vs INSERT
     */
    private static Operation transformDeleteInsert(Operation a, Operation b) {
        if (a.position + a.length <= b.position) {
            return a; // Deletion is before insertion
        } else if (a.position >= b.position) {
            // Deletion is after insertion
            return new Operation(a.type, a.position + b.content.length(), "",
                    a.length, a.userId, a.timestamp, a.revision);
        } else {
            // Insertion is inside deletion range
            return new Operation(a.type, a.position, "",
                    a.length + b.content.length(), a.userId, a.timestamp, a.revision);
        }
    }

    /**
     * Transform: DELETE vs DELETE
     */
    private static Operation transformDeleteDelete(Operation a, Operation b) {
        if (a.position + a.length <= b.position) {
            return a; // a is completely before b
        } else if (a.position >= b.position + b.length) {
            // a is after b
            return new Operation(a.type, a.position - b.length, "",
                    a.length, a.userId, a.timestamp, a.revision);
        } else if (a.position <= b.position && a.position + a.length >= b.position + b.length) {
            // a completely contains b
            return new Operation(a.type, a.position, "",
                    a.length - b.length, a.userId, a.timestamp, a.revision);
        } else if (b.position <= a.position && b.position + b.length >= a.position + a.length) {
            // b completely contains a
            return new Operation(a.type, b.position, "",
                    0, a.userId, a.timestamp, a.revision); // No-op
        } else if (a.position < b.position) {
            // Overlap: a starts before b
            int overlap = a.position + a.length - b.position;
            return new Operation(a.type, a.position, "",
                    a.length - overlap, a.userId, a.timestamp, a.revision);
        } else {
            // Overlap: a starts after b
            int overlap = b.position + b.length - a.position;
            return new Operation(a.type, b.position, "",
                    a.length - overlap, a.userId, a.timestamp, a.revision);
        }
    }

    /**
     * Apply an operation to a text string.
     */
    public static String applyOperation(String text, Operation op) {
        if (op.type == OperationType.INSERT) {
            if (op.position < 0 || op.position > text.length()) {
                throw new IllegalArgumentException("Invalid insert position: " + op.position);
            }
            return text.substring(0, op.position) + op.content + text.substring(op.position);
        } else {
            if (op.position < 0 || op.position + op.length > text.length()) {
                throw new IllegalArgumentException("Invalid delete range: " + op.position + " - " + (op.position + op.length));
            }
            return text.substring(0, op.position) + text.substring(op.position + op.length);
        }
    }
}
