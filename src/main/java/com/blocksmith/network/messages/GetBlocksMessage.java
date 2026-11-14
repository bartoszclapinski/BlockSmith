package com.blocksmith.network.messages;

import java.util.List;

import com.blocksmith.network.Message;
import com.blocksmith.network.MessageType;

/**
 * Request a range of blocks from a peer, starting at {@code fromIndex}
 * through the peer's chain tip. Sent by a node that has fallen behind to
 * catch up; the peer answers with a {@link BlocksMessage}.
 */
public class GetBlocksMessage extends Message {

    private int fromIndex;

    /**
     * Optional block locator (Sprint 21c): our hashes from the tip down. If
     * present, the peer answers with its blocks after the first hash it also
     * has, instead of from {@code fromIndex}: that works across a fork.
     */
    private List<String> locator;

    public GetBlocksMessage(String nodeId, int fromIndex) {
        super(MessageType.GET_BLOCKS, nodeId);
        this.fromIndex = fromIndex;
    }

    /** Default constructor for Gson. */
    /** A request by block locator (Sprint 21c). */
    public GetBlocksMessage(String nodeId, List<String> locator) {
        super(MessageType.GET_BLOCKS, nodeId);
        this.locator = locator;
    }

    public GetBlocksMessage() {}

    /** The block locator, or null for an index-only request. */
    public List<String> getLocator() { return locator; }

    public int getFromIndex() { return fromIndex; }
}
