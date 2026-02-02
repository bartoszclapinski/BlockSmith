package com.blocksmith.network.messages;

import com.blocksmith.core.Block;
import com.blocksmith.network.Message;
import com.blocksmith.network.MessageType;

/**
 * Broadcast a new block to peers.
 */
public class NewBlockMessage extends Message {
    
    private Block block;
    
    public NewBlockMessage(String nodeId, Block block) {
        super(MessageType.NEW_BLOCK, nodeId);
        this.block = block;
    }
    
    public NewBlockMessage() {}
    
    public Block getBlock() { return block; }
}