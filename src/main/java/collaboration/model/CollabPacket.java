package collaboration.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Low-overhead wire protocol packet wrapper for live collaboration.
 */
public class CollabPacket {

    public enum Type {
        HELLO,               // Guest -> Host: credentials & client info
        AUTH_OK,             // Host -> Guest: assigned client ID & color
        AUTH_FAIL,           // Host -> Guest: invalid token or rejected
        TREE_SYNC,           // Host -> Guest: full virtual directory hierarchy
        FILE_REQUEST,        // Guest -> Host: requests contents for fileUri
        FILE_RESPONSE,       // Host -> Guest: loaded file contents & initial revision
        OP_DELTA,            // Peer <-> Peer (via Host): text insert/delete operation
        CURSOR_MOVE,         // Peer <-> Peer (via Host): cursor & selection update
        DISCONNECT,          // Peer notifies intent to leave session
        PEER_JOINED,         // Host -> Guests: announcement of new collaborator
        PEER_LEFT            // Host -> Guests: announcement of departed collaborator
    }

    private static final Gson GSON = new GsonBuilder().create();

    private Type type;
    private String senderId;
    private String senderName;
    private String payload;
    private long timestamp;

    public CollabPacket() {
        this.timestamp = System.currentTimeMillis();
    }

    public CollabPacket(Type type, String senderId, String senderName, String payload) {
        this.type = type;
        this.senderId = senderId;
        this.senderName = senderName;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static CollabPacket fromJson(String json) {
        return GSON.fromJson(json, CollabPacket.class);
    }

    public static Gson getGson() {
        return GSON;
    }
}
