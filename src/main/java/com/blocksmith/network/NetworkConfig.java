package com.blocksmith.network;

import java.util.List;

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
     * Default port for the REST API (HTTP).
     * Kept separate from the P2P port so a node can serve peers and users at
     * the same time.
     */
    public static final int API_PORT = 7070;

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

    /**
     * THEORY: Seed Nodes - Bootstrapping the Network
     *
     * A fresh node knows no peers. It connects to a hard-coded list of
     * "seed nodes" to get its first connections, then peer discovery
     * (GET_PEERS / PEERS) grows the network from there. Bitcoin uses DNS
     * seeds for the same purpose.
     *
     * Format: "host:port". Empty by default - populate for a real network
     * or for local multi-node testing, e.g. List.of("localhost:8335").
     */
    public static final List<String> SEED_NODES = List.of();

    /**
     * THEORY: Discovery and auto-connect (Sprint 26b)
     *
     * Every DISCOVERY_EVERY_HEARTBEATS-th heartbeat (~30 s) a node asks its
     * peers for GET_PEERS, and dials discovered addresses while it has fewer
     * than TARGET_OUTBOUND links it opened itself (Bitcoin: 8 outbound).
     * Inbound links fill the rest, up to MAX_PEERS. A failed dial waits
     * DIAL_BACKOFF_BASE_MS, then twice as long after every further failure,
     * up to DIAL_BACKOFF_MAX_MS.
     */
    public static final int DISCOVERY_EVERY_HEARTBEATS = 3;
    public static final int TARGET_OUTBOUND = 4;
    public static final long DIAL_BACKOFF_BASE_MS = 10_000;
    public static final long DIAL_BACKOFF_MAX_MS = 300_000;

    /** File in the data directory that keeps the node's id across restarts (Sprint 26b). */
    public static final String NODE_ID_FILE = "node-id";


    // Private constructor - utility class, no instances needed
    private NetworkConfig() {}
}