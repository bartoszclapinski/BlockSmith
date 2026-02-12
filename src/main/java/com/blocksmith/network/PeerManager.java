package com.blocksmith.network;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * THEORY: Peer Registry Management
 * 
 * In a P2P network, each node needs to keep track of all peers it knows about.
 * PeerManager acts as a centralized registry for this information.
 * 
 * WHY A REGISTRY? 
 * - Need to know who we're connected to (avoid duplicate connections)
 * - Need to enforce connection limits (MAX_PEERS)
 * - Need to track peer states for heartbeat/eviction
 * - Need to share peer lists for discovery
 * 
 * THREAD SAFETY:
 * - Multiple threads handle connections simultaneously (one per peer)
 * - ConcurrentHashMap ensures safe concurrent reads/writes
 * - No explicit synchronization needed for individual operations
 * 
 * BITCOIN COMPARSION:
 * Bitcoin Core's CConnman class manages peer connections similarly -
 * tracking inbound/outbound peers, enforcing limits, and providing
 * peer lists for the addr gossip protocol.
 * 
 * KEY DESIGN:
 * - Keyed by "host:port" string (from PeerInfo.getAddress())
 * - MAX_PEERS limit applies only to CONNECTED peers
 * - DISCOVERED peers can exceed the limit (they're just addresses)
 */
public class PeerManager {
    
    private final ConcurrentHashMap<String, PeerInfo> peers;
    private final int maxPeers;

    /**
     * Creates a PeerManager with the default MAX_PEERS limit.
     */
    public PeerManager() {
        this(NetworkConfig.MAX_PEERS);
    }

    /**
     * Creates a PeerManager with custom peer limit.
     * 
     * @param maxPeers maximum number of connected peers allowed
     */
    public PeerManager(int maxPeers) {
        this.peers = new ConcurrentHashMap<>();
        this.maxPeers = maxPeers;
    }

    /**
     * Add a peer to the registry.
     * Respects MAX_PEERS limit for CONNECTED peers.
     * 
     * @param info the peer info to add
     * @return true if added, false if rejected (at limit or duplicate)
     */
    public boolean addPeer(PeerInfo info) {
        if (info == null) return false;

        String address = info.getAddress();

        if (peers.containsKey(address)) return false;

        if (info.getState() == PeerState.CONNECTED && !canAcceptMore()) return false;

        peers.put(address, info);
        return true;
    }

    /**
     * Remove peer from the registry.
     * 
     * @param address the peer address in "host:port" format
     */
    public void removePeer(String address) {
        peers.remove(address);
    }

    /**
     * Get a peer's info by address.
     * 
     * @param address the peer address in "host:port" format
     * @return PeerInfo or null if not found
     */
    public PeerInfo getPeer(String address) {
        return peers.get(address);
    }

    /**
     * Get all peers in CONNECTED state.
     *
     * @return list of connected peers
     */
    public List<PeerInfo> getConnectedPeers() {
        return peers.values().stream()
                .filter(p -> p.getState() == PeerState.CONNECTED)
                .collect(Collectors.toList());
    }

    /**
     * Get all known peers (any state).
     *
     * @return list of all peers in the registry
     */
    public List<PeerInfo> getKnownPeers() {
        return List.copyOf(peers.values());
    }

    /**
     * Get the number of currently connected peers.
     *
     * @return connected peer count
     */
    public int getConnectedCount() {
        return (int) peers.values().stream()
                .filter(p -> p.getState() == PeerState.CONNECTED)
                .count();
    }

    /**
     * Check if a peer is already in the registry.
     *
     * @param address the peer address in "host:port" format
     * @return true if the peer is known
     */
    public boolean isKnown(String address) {
        return peers.containsKey(address);
    }

    /**
     * Check if we can accept more connected peers.
     *
     * @return true if connected count is below maxPeers
     */
    public boolean canAcceptMore() {
        return getConnectedCount() < maxPeers;
    }
}
