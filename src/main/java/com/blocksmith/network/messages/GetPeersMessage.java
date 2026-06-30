package com.blocksmith.network.messages;

import com.blocksmith.network.Message;
import com.blocksmith.network.MessageType;

/**
 * Peer discovery request: "Who else do you know?"
 * The receiver replies with a PeersMessage listing its known peers.
 */
public class GetPeersMessage extends Message {

    public GetPeersMessage(String nodeId) {
        super(MessageType.GET_PEERS, nodeId);
    }

    /** Default constructor for Gson. */
    public GetPeersMessage() {}
}
