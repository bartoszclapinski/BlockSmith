package com.blocksmith.network.messages;

import com.blocksmith.network.Message;
import com.blocksmith.network.MessageType;

/**
 * Request a range of blocks from a peer, starting at {@code fromIndex}
 * through the peer's chain tip. Sent by a node that has fallen behind to
 * catch up; the peer answers with a {@link BlocksMessage}.
 */
public class GetBlocksMessage extends Message {

    private int fromIndex;

    public GetBlocksMessage(String nodeId, int fromIndex) {
        super(MessageType.GET_BLOCKS, nodeId);
        this.fromIndex = fromIndex;
    }

    /** Default constructor for Gson. */
    public GetBlocksMessage() {}

    public int getFromIndex() { return fromIndex; }
}
