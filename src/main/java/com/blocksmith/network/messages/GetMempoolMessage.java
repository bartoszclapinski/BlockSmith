package com.blocksmith.network.messages;

import com.blocksmith.network.Message;
import com.blocksmith.network.MessageType;

/**
 * Request a peer's pending transactions (mempool). Sent by a freshly connected
 * node so it can catch up on transactions that are waiting to be mined; the
 * peer answers with a {@link MempoolMessage}.
 */
public class GetMempoolMessage extends Message {

    public GetMempoolMessage(String nodeId) {
        super(MessageType.GET_MEMPOOL, nodeId);
    }

    /** Default constructor for Gson. */
    public GetMempoolMessage() {}
}
