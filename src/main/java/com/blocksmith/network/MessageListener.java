package com.blocksmith.network;

/**
 * THEORY: Listener Pattern for Async Message Receiving
 * 
 * In a P2P network, messages can arrive at any time. Instead of
 * blocking the main thread with readLine(), we use a listener
 * pattern (also called Observer pattern):
 * 
 * 1. Register a listener with the Peer
 * 2. A background thread reads messages continuously
 * 3. When message arrives, the listener is notified
 * 4. When the connection drops, the listener is notified
 * 
 * WHY ASYNC? 
 * - Main thread can continue doing other work (mining, UI, etc.)
 * - Multiple peers can be listened to simultaneously
 * - Clean separation between I/O and business logic
 * 
 * JAVA NOTE: This is similar to event listeners in GUI frameworks
 * (e.g., ActionListener in Swing, EventHandler in JavaFX).
 * 
 * BITCOIN: Uses a similar async model where each peer connection
 * has its own thread reading messages independently.
 */
public interface MessageListener {

    /**
     * Called when a message is received from the remote peer.
     * 
     * @param message The received and parsed message
     */
    void onMessage(Message message);

    /**
     * Called when the connection to the remote peer is lost.
     * This could be due to the peer disconnecting, network error,
     * or the local node shutting down.
     */
    void onDisconnect();
    
}
