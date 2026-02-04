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
    
    // Private constructor - utility class, no instances needed
    private NetworkConfig() {}
}