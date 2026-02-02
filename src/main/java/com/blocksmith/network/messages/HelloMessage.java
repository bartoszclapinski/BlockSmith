package com.blocksmith.network.messages;

import com.blocksmith.network.Message;
import com.blocksmith.network.MessageType;

/**
 * Handshake message sent when connecting to a peer
 * Contains node information for identification.
 */
public class HelloMessage extends Message {

    private String version;
    private int port;
    private int chainLength;

    public HelloMessage(String nodeId, String version, int port, int chainLength) {
        super(MessageType.HELLO, nodeId);
        this.version = version;
        this.port = port;
        this.chainLength = chainLength;
    }

    /**
     * Default constructor for Gson
     */
    public HelloMessage() {}

    public String getVersion() { return version; }
    public int getPort() { return port; }
    public int getChainLength() { return chainLength; }
    
}
