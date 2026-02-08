package com.blocksmith.network;

import java.io.PrintWriter;

/**
 * THEORY: Message Context - Connection Wrapper
 * 
 * When a MessageHandler processes a message, it often needs to
 * send a response back. But we don't want handlers dealing with
 * raw PrintWriter/Socket objects directly.
 * 
 * MessageContext wraps the connection details and provides a
 * clean API for handlers:
 * - sendMessage(Message) - send a response
 * - getRemoteNodeId() - who sent the message
 * 
 * WHY A WRAPPER?
 * - Decouples handlers from I/O implementation
 * - Handlers don't need to know about PrintWriter, JSON serialization
 * - Easy to mock in tests
 * - Could be extended later (e.g., add connection metadata)
 */
public class MessageContext {

    private final PrintWriter writer;
    private final String remoteNodeId;

    /**
     * Creates a new MessageContext for a connection.
     * 
     * @param writer The output stream to the remote peer
     * @param remoteNodeId The remote peer's node ID
     */
    public MessageContext(PrintWriter writer, String remoteNodeId) {
        this.writer = writer;
        this.remoteNodeId = remoteNodeId;
    }

    /**
     * Send a message to the remote peer.
     * Serializes to JSON and writes to the output stream.
     * 
     * @param message The message to send
     */
    public void sendMessage(Message message) {
        writer.println(message.toJson());
    }

    /**
     * Get the remote peer's node ID.
     * 
     * @return The node ID of the connected peer
     */
    public String getRemoteNodeId() {
        return remoteNodeId;
    }
}
