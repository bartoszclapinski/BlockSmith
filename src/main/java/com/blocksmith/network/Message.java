package com.blocksmith.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * THEORY: Network Message Base Class
 * 
 * All network messages share common fields:
 * - type: What kind of message (from MessageType enum)
 * - timestamp: When the message was created
 * - nodeId: Who sent it (unique node identifier)
 * 
 * Messages are serialized to JSON for transmission over TCP.
 * 
 * EXAMPLE JSON:
 * {
 *   "type": "HELLO",
 *   "timestamp": 1706886000000,
 *   "nodeId": "node-abc123"
 * }
 */
public abstract class Message {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();
            
    protected MessageType type;
    protected long timestamp;
    protected String nodeId;
    
    /**
     * Constructor for creating new messages
     */
    protected Message(MessageType type, String nodeId) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.nodeId = nodeId;
    }

    /**
     * Default constructor for deserialization     * 
     */
    protected Message() {}
    
    // === Getters ===
    public MessageType getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getNodeId() {
        return nodeId;
    }
    
    // === Serialization ===

    /**
     * Serialize this message to JSON string
     */
    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * Deserialize JSON string to Message object
     */
    public static <T extends Message> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    @Override
    public String toString() {
        return String.format("%s{nodeId='%s', timestamp=%d}", 
                type, nodeId, timestamp);
    }
}
