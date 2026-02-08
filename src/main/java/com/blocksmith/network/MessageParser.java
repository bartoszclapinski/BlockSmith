package com.blocksmith.network;

import java.util.HashMap;
import java.util.Map;

import com.blocksmith.network.messages.HelloMessage;
import com.blocksmith.network.messages.NewBlockMessage;
import com.blocksmith.network.messages.NewTransactionMessage;
import com.blocksmith.network.messages.PingMessage;
import com.blocksmith.network.messages.PongMessage;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * THEORY: Message Parser - Protocol Dispatch
 * 
 * In any network protocol, incoming data arrives as raw bytes (or text).
 * The receiver must determine WHAT TYPE of message it is before it can
 * process it. This is called "protocol dispatch" or "message routing".
 * 
 * COMMON PATTERNS:
 * - TLV (Type-Length-Value): First bytes indicate type, then Length, then data
 * - JSON with type field: Our approach - read "type" field first, then deserialize
 * - Protobuf: Binary format with field numbers
 * 
 * OUR APPROACH:
 * 1. Parse JSON to read the "type" field
 * 2. Look up to concrete Message class for that type
 * 3. Deserialize the full JSON into the correct class
 * 
 * WHY NOT JUST USE Message.fromJson()?
 * - fromJson() requires knowing the class: Message.fromJson(json, PingMessage.class)
 * - When reading from socket, we don't know the type yet
 * - MessageParser solves this by inspecting the JSON first
 * 
 * BITCOIN: Uses a 12-byte command string in the message header
 * (e.g., "version", "verack", "tx", "block") for dispatch.
 */
public class MessageParser {

    /**
     * Registry mapping MessageType to its concrete class.
     * This allows us to deserialize JSON into the correct Message subclass.
     */
    private static final Map<MessageType, Class<? extends Message>> TYPE_REGISTRY = new HashMap<>();

    static {
        TYPE_REGISTRY.put(MessageType.HELLO, HelloMessage.class);
        TYPE_REGISTRY.put(MessageType.PING, PingMessage.class);
        TYPE_REGISTRY.put(MessageType.PONG, PongMessage.class);
        TYPE_REGISTRY.put(MessageType.NEW_BLOCK, NewBlockMessage.class);
        TYPE_REGISTRY.put(MessageType.NEW_TRANSACTION, NewTransactionMessage.class);        
    }

    /**
     * THEORY: parsing an Unknown Message
     * 
     * STEPS:
     * 1. Parse JSON string into a JsonObject (lightweight, no class needed)
     * 2. Read the "type" field as a string
     * 3. Convert string to MessageType enum
     * 4. Look up the concrete class in our registry
     * 5. Deserialize the full JSON into that class
     * 
     * WHY RETURN NULL INSTEAD OF THROW?
     * - Network input is untrusted - we expect bad data sometimes
     * - Caller can decide how to handle (log, ignore, disconnect)
     * - Avoids crashing the message loop over one bad message
     * 
     * @param json Raw JSON string from the network
     * @return Parsed Message object, or null if parsing fails
     */
    public static Message parse(String json) {
        if (json == null || json.isBlank()) return null;

        try {
            // Step 1: Parse JSON to read the type field
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();

            // Step 2: Extract the "type" field
            if (!jsonObject.has("type")) {
                System.err.println("MessageParser: Missing 'type' field in message");
                return null;
            }

            String typeString = jsonObject.get("type").getAsString();

            // Step 3: Convert to MessageType enum
            MessageType type;
            try {
                type = MessageType.valueOf(typeString);
            } catch (IllegalArgumentException e) {
                System.err.println("MessageParser: Invalid message type: " + typeString);
                return null;
            }

            // Step 4: Look up concrete class
            Class<? extends Message> messageClass = TYPE_REGISTRY.get(type);
            if (messageClass == null) {
                System.err.println("MessageParser: Unknown message type: " + typeString);
                return null;
            }

            // Step 5: Deserialize to concrete class
            return Message.fromJson(json, messageClass);

        } catch (JsonSyntaxException e) {
            System.err.println("MessageParser: Malformed JSON: " + e.getMessage());
            return null;
        }
    }

    // private constructor - utility class, no instances needed
    private MessageParser() {}
}