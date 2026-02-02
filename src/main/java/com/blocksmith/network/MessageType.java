package com.blocksmith.network;

/**
 * THEORY: Network Message Types
 * 
 * In a P2P blockchain network, nodes communicate using typed messages.
 * Each message type has a specific purpose in the protocol.
 * 
 * HANDSHAKE:
 * - HELLO: "Hi, I'm a node, here's my info"
 * - HELLO_ACK: "Got it, here's my info back"
 * 
 * SYNCHRONIZATION:
 * - GET_BLOCKS: "Send me blocks starting from X"
 * - BLOCKS: "Here are the blocks you requested"
 * - GET_CHAIN_LENGTH: "How long is your chain?"
 * - CHAIN_LENGTH: "My chain has N blocks"
 * 
 * BROADCASTING:
 * - NEW_BLOCK: "I just mined/received a new block"
 * - NEW_TRANSACTION: "Here's a new transaction for the mempool"
 * 
 * PEER DISCOVERY:
 * - GET_PEERS: "Who else do you know?"
 * - PEERS: "Here's my list of known peers"
 * 
 * KEEP-ALIVE:
 * - PING: "Are you still there?"
 * - PONG: "Yes, I'm alive"
 */
public enum MessageType {

    // === Handshake ===
    /** Initial connection message with node info */
    HELLO,

    /** Response to HELLO with reciever's node info */
    HELLO_ACK,

    // === Chain synchronization ===
    /** Request blocks starting from a specific index */
    GET_BLOCKS,

    /** Response with the requested blocks */
    BLOCKS,

    /** Request the length of the chain */
    GET_CHAIN_LENGTH,
    
    // === Broadcasting ===
    /** Broadcast a newly mined or received block */
    NEW_BLOCK,

    /** Broadcast a new transaction for the mempool */
    NEW_TRANSACTION,

    // === Peer discovery ===
    /** Request the list of known peers */
    GET_PEERS,

    /** Response with the list of known peers */
    PEERS,

    // === Keep-Alive ===
    /** Ping the receiver to check if they are still alive */
    PING,

    /** Response to PING with PONG */
    PONG
}
