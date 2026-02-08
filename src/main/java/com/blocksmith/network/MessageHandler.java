package com.blocksmith.network;

/**
 * THEORY: Handler Pattern for Message Processing
 * 
 * Instead of giant switch/case in Node for every message type,
 * we use the Handler pattern (similar to Strategy Pattern):
 * 
 * - Each message type gets its own handler
 * - Handlers are registered in a map: MessageType -> MessageHandler
 * - When a message arrives, we look up and call the right handler
 * 
 * WHY THIS PATTERN?
 * - Open/Closed Principle: Add new handlers without modifing Node
 * - Single Responsibility Principle: Each handler does one thing
 * - Testable: Handlers can be tested independently
 * 
 * JAVA NOTE: @FunctionalInterface means this can be used as a lambda:
 * registerHandler(PING, (msg, ctx) -> ctx.sendMessage(new PongMessage(nodeId)));
 * 
 * BITCOIN: Uses a similar dispatch table mapping command strings
 * to handler functions (e.g., "tx" -> ProcessTransaction). 
 */
@FunctionalInterface
public interface MessageHandler {

    /**
     * Handle an incoming message.
     * 
     * @param message The received message (already parsed)
     * @param context The connection context (for sending responses)
     */
    void handle(Message message, MessageContext context);
    
}
