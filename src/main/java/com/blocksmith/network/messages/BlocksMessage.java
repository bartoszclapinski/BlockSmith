package com.blocksmith.network.messages;

import java.util.List;

import com.blocksmith.core.Block;
import com.blocksmith.network.Message;
import com.blocksmith.network.MessageType;

/**
 * Carries a contiguous range of blocks in reply to a {@link GetBlocksMessage}.
 * Blocks are ordered by index so the receiver can append them sequentially.
 */
public class BlocksMessage extends Message {

    private List<Block> blocks;

    public BlocksMessage(String nodeId, List<Block> blocks) {
        super(MessageType.BLOCKS, nodeId);
        this.blocks = blocks;
    }

    /** Default constructor for Gson. */
    public BlocksMessage() {}

    public List<Block> getBlocks() { return blocks; }
}
