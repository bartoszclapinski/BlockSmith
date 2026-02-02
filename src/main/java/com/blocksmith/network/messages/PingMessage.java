package com.blocksmith.network.messages;

import com.blocksmith.network.Message;
import com.blocksmith.network.MessageType;

/**
 * Keep-alive message to check if peer is responsive.
 */
public class PingMessage extends Message {
    
    public PingMessage(String nodeId) {
        super(MessageType.PING, nodeId);
    }
    
    public PingMessage() {}
}