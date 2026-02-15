package com.blocksmith.network;

/**
 * THEORY: Network Configuration Constants
 * 
 * Centralized configuration for P2P networking, similar to how
 * BlockchainConfig holds blockchain-related constants.
 * 
 * WHY CENTRALIZE?
 * - Easy to change values in one place
 * - Clear documentation of "magic numbers"
 * - Consistent values across all network classes
 * 
 * BITCOIN REFERENCE:
 * - Default port: 8333 (mainnet), 18333 (testnet)
 * - Max connections: typically 8-125 peers
 * - Connection timeout: varies by implementation
 */
public class NetworkConfig {
    
    /**
     * Default port for P2P connections.
     * Bitcoin uses 8333, we use 8335 to avoid conflicts.
     */
    public static final int DEFAULT_PORT = 8335;
    
    /**
     * Maximum number of peer connections.
     * Keeps resource usage manageable for educational purposes.
     */
    public static final int MAX_PEERS = 10;
    
    /**
     * Connection timeout in milliseconds.
     * How long to wait when connecting to a peer.
     */
    public static final int CONNECTION_TIMEOUT_MS = 5000;
    
    /**
     * Socket read timeout in milliseconds.
     * How long to wait for data from a connected peer.
     */
    public static final int READ_TIMEOUT_MS = 30000;
    
    /**
     * Prefix for generated node IDs.
     * Format: "node-" + random hex string
     */
    public static final String NODE_ID_PREFIX = "node-";
    
    /**
     * Protocol version string.
     * Used in HELLO messages to check compatibility.
     */
    public static final String PROTOCOL_VERSION = "1.0.0";

    /**
     * THEORY: Heartbeat - Keeping the Network Alive
     * 
     * In a P2P network, connections can silently die (network issues,
     * crashed nodes, firewalls, etc.). Without heartbeats, a node would keep
     * "ghost peers" int its registry forever.
     * 
     * PING/PONG heartbeat solves this:
     * - Periodically send PING to all connected peers
     * - Expect PONG back within a timeout
     * - No PONG = peer is dead -> evict it
     * 
     * BITCOIN: Uses a similar mechanism with ping/pong messages.
     * Default timeout is ~20 minutes (we use shorter for educational purposes).
     */

    /**
     * How often to send PING to all connected peers (milliseconds).
     * Every 10 seconds, the node checks if its peers are still alive.
     */
    public static final int HEARTBEAT_INTERVAL_MS = 10000;

    /**
     * How long before a silent peer is considered dead (milliseconds).
     * If no message received from a peer within this window, evict it. 
     */
    public static final int PEER_TIMEOUT_MS = 30000;

    
    // Private constructor - utility class, no instances needed
    private NetworkConfig() {}
}