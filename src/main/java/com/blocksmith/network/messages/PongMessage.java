package com.blocksmith.network.messages;

import com.blocksmith.network.Message;
import com.blocksmith.network.MessageType;

/**
 * Response to Ping message.
 */
public class PongMessage extends Message {
    
    public PongMessage(String nodeId) {
        super(MessageType.PONG, nodeId);
    }
    
    public PongMessage() {}
}