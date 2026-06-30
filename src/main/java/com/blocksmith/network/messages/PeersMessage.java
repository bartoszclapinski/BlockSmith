package com.blocksmith.network.messages;

import java.util.List;

import com.blocksmith.network.Message;
import com.blocksmith.network.MessageType;

/**
 * Peer discovery response carrying known peer addresses in "host:port" format.
 * Sent in reply to a GetPeersMessage.
 */
public class PeersMessage extends Message {

    private List<String> peers;

    public PeersMessage(String nodeId, List<String> peers) {
        super(MessageType.PEERS, nodeId);
        this.peers = peers;
    }

    /** Default constructor for Gson. */
    public PeersMessage() {}

    public List<String> getPeers() { return peers; }
}
