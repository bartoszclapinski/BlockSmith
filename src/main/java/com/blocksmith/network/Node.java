package com.blocksmith.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import java.net.ServerSocket;
import java.net.Socket;

import java.security.SecureRandom;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.network.messages.PongMessage;
import com.blocksmith.network.messages.HelloMessage;
import com.blocksmith.network.messages.PingMessage;
import com.blocksmith.network.messages.PeersMessage;
import com.blocksmith.network.messages.NewBlockMessage;
import com.blocksmith.network.messages.NewTransactionMessage;
import com.blocksmith.network.messages.GetBlocksMessage;
import com.blocksmith.network.messages.BlocksMessage;
import com.blocksmith.network.messages.GetMempoolMessage;
import com.blocksmith.network.messages.MempoolMessage;
import com.blocksmith.core.Transaction;

/**
 * THEORY: Network Node - The Heart of P2P
 * 
 * A Node is a single participant in the blockchain network.
 * Each node:
 * - Has a unique identifier (nodeId)
 * - Listens for incoming connections (server role)
 * - Can connect to other nodes (client role - Milestone 8c)
 * 
 * SERVER SOCKET:
 * - ServerSocket listens on a specific port
 * - When a peer connects, accept() returns a Socket for communication
 * - Each connection is typically handled in a separate thread
 * 
 * LIFECYCLE:
 * 1. Create Node (assigns ID, creates ServerSocket)
 * 2. start() - begins accepting connections
 * 3. stop() - shuts down gracefully
 * 
 * BITCOIN: Full nodes listen on port 8333 and maintain connections
 * to ~8 outbound and up to 125 inbound peers.
 */
public class Node {

    private final String nodeId;
    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running;
    private ExecutorService connectionPool;
    private Thread acceptThread;
    private final Map<MessageType, MessageHandler> handlers;
    private final PeerManager peerManager;
    private final Blockchain blockchain;
    private final List<Peer> outboundPeers;
    private final Map<String, PrintWriter> peerWriters = new ConcurrentHashMap<>();
    private ScheduledExecutorService heartbeatScheduler;

    /**
     * Creates a new Node with default port.
     */
    public Node() {
        this(NetworkConfig.DEFAULT_PORT);
    }

    /**
     * Creates a new Node on the specified port with a fresh Blockchain.
     *
     * @param port The port to listen on
     */
    public Node(int port) {
        this(port, new Blockchain());
    }

    /**
     * Creates a new Node on the specified port backed by the given Blockchain.
     *
     * @param port The port to listen on
     * @param blockchain The blockchain this node validates and extends
     */
    public Node(int port, Blockchain blockchain) {
        this.nodeId = generateNodeId();
        this.port = port;
        this.blockchain = blockchain;
        this.running = false;
        this.handlers = new HashMap<>();
        this.peerManager = new PeerManager();
        this.outboundPeers = new ArrayList<>();
        registerDefaultHandlers();
    }

    /**
     * THEORY: Generates a unique node identifier.
     * 
     * Format: "node-" + 16 random hex characters
     * Example: "node-a3f7b2c9e1d4f8a6"
     * 
     * WHY RANDOM?
     * - No central authority to assign IDs
     * - 16 hex chars = 64 bits = collision probability negligible
     * 
     * @return Unique node identifier
     */
    private String generateNodeId() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[8]; // 8 bytes = 16 hex chars
        random.nextBytes(bytes);
        
        StringBuilder hexString = new StringBuilder(NetworkConfig.NODE_ID_PREFIX);
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    /**
     * THEORY: Starts the node's server socket and accept loop.
     * 
     * THREADING MODEL:
     * 1. Main thread: Your application code
     * 2. Accept thread: Waits for new connections (blocking)
     * 3. Connection pool: Handles each connected client
     * 
     * WHY SEPARATE THREADS?
     * - accept() is BLOCKING - it waits until someone connects
     * - If we did this on main thread, the app would freeze
     * - Each client needs its own thread for concurrent handling
     * 
     * EXECUTOR SERVICE:
     * - Thread pool that manages worker threads
     * - CachedThreadPool: Creates threads as needed, reuses idle ones
     * - Handles thread lifecycle automatically
     * 
     * @throws IOException if unable to bind to port
     */
    public void start() throws IOException {
        if (running) {
            throw new IllegalStateException("Node is already running");
        }
        
        serverSocket = new ServerSocket(port);
        running = true;
        connectionPool = Executors.newCachedThreadPool();
        
        // Start accept loop in separate thread
        acceptThread = new Thread(this::acceptLoop, "Node-Accept-" + port);
        acceptThread.start();

        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "Node-Heartbeat-" + port));
        heartbeatScheduler.scheduleAtFixedRate(
            this::heartbeatTask,
            NetworkConfig.HEARTBEAT_INTERVAL_MS,
            NetworkConfig.HEARTBEAT_INTERVAL_MS,
            TimeUnit.MILLISECONDS);        
        
        System.out.println("▶ Node " + nodeId + " started on port " + port);

        bootstrap();
    }

    /**
     * THEORY: Bootstrap - Joining the Network
     *
     * On startup a node has no peers. It connects to the configured seed
     * nodes to get its first connections; peer discovery (GET_PEERS) then
     * grows the network from there.
     *
     * Best-effort: a seed that is unreachable or is ourselves is skipped,
     * never fatal to startup.
     */
    private void bootstrap() {
        for (String address : NetworkConfig.SEED_NODES) {
            PeerInfo seed = parseAddress(address);
            if (seed == null) continue;
            if (seed.getPort() == port) continue; // skip self (local testing)
            try {
                connectToPeer(seed.getHost(), seed.getPort());
                System.out.println("  ✓ Bootstrapped to seed " + address);
            } catch (Exception e) {
                System.out.println("  ✗ Could not reach seed " + address
                        + ": " + e.getMessage());
            }
        }
    }

    /**
     * THEORY: Heartbeat Task
     * 
     * Sends PING to all connected peers every 10 seconds.
     * Expects PONG back within 30 seconds.
     * If no PONG received, evict the peer.
     */
    private void heartbeatTask() {
        PingMessage ping = new PingMessage(nodeId);
        String pingJson = ping.toJson();
        long now = System.currentTimeMillis();

        for (PeerInfo peer : peerManager.getConnectedPeers()) {
            String address = peer.getAddress();

            if (now - peer.getLastSeen() > NetworkConfig.PEER_TIMEOUT_MS) {
                peerManager.removePeer(address);
                PrintWriter deadWriter = peerWriters.remove(address);
                if (deadWriter != null) deadWriter.close();
                System.out.println("  ✗ Evicted dead peer " + address);
                continue;
            }

            PrintWriter writer = peerWriters.get(address);
            if (writer != null) writer.println(pingJson);
        }
    }

    /**
     * THEORY: The Accept Loop
     * 
     * This runs in its own thread, continuously accepting connections.
     * 
     * FLOW:
     * 1. serverSocket.accept() BLOCKS until a client connects
     * 2. Returns a Socket representing the connection
     * 3. Hand off Socket to thread pool for processing
     * 4. Repeat
     * 
     * EXCEPTION HANDLING:
     * - When stop() closes serverSocket, accept() throws IOException
     * - We check 'running' flag to distinguish shutdown from real errors
     */
    private void acceptLoop() {
        System.out.println("  Listening for connections...");
        
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("  ✓ New connection from " + 
                        clientSocket.getInetAddress().getHostAddress() + ":" + 
                        clientSocket.getPort());
                
                // Handle connection in thread pool
                connectionPool.submit(() -> handleConnection(clientSocket));
                
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
                // If !running, this is expected (socket closed during shutdown)
            }
        }
    }

    /**
     * Register a handler for a specific message type.
     * 
     * @param type The message type to handle
     * @param handler The handler to call when this type arrives     
     */
    public void registerHandler(MessageType type, MessageHandler handler) {
        handlers.put(type, handler);
    }

    /**
     * THEORY: Default Message Handlers
     * 
     * The node registers built-in handlers for standard protocol messages.
     * Custom handlers can be added via registerHandler() to extend behavior.
     * 
     * PING -> PONG is the simplest handler: just echo back "I'm alive".
     * This is how nodes detect if peers are still connected.    
     */
    private void registerDefaultHandlers() {
        // PING -> respond with PONG
        registerHandler(MessageType.PING, (message, context) -> {
            PongMessage pong = new PongMessage(nodeId);
            context.sendMessage(pong);
            System.out.println("  → Sent PONG to " + context.getRemoteNodeId());
        });

        // PONG -> log receipt (lastSeen already updated by message loop)
        registerHandler(MessageType.PONG, (message, context) -> {
            System.out.println("  ← Received PONG from " + context.getRemoteNodeId());
        });

        // GET_PEERS -> respond with the addresses of all known peers
        registerHandler(MessageType.GET_PEERS, (message, context) -> {
            List<String> addresses = peerManager.getKnownPeers().stream()
                    .map(PeerInfo::getAddress)
                    .toList();
            context.sendMessage(new PeersMessage(nodeId, addresses));
            System.out.println("  → Sent PEERS (" + addresses.size() + ") to "
                    + context.getRemoteNodeId());
        });

        // PEERS -> record received addresses as DISCOVERED peers (no auto-connect)
        registerHandler(MessageType.PEERS, (message, context) -> {
            List<String> received = ((PeersMessage) message).getPeers();
            if (received == null) return;

            int added = 0;
            for (String address : received) {
                PeerInfo info = parseAddress(address);
                if (info != null && peerManager.addPeer(info)) added++;
            }
            System.out.println("  ← Received PEERS (" + received.size() + "), added "
                    + added + " new");
        });

        // NEW_BLOCK -> validate, append, and re-gossip if newly accepted
        registerHandler(MessageType.NEW_BLOCK, (message, context) -> {
            Block block = ((NewBlockMessage) message).getBlock();
            if (block == null) return;

            // addBlock() returns false for duplicates and invalid blocks, so a
            // block is only relayed once - this naturally stops gossip storms.
            if (blockchain.addBlock(block)) {
                System.out.println("  ← Accepted NEW_BLOCK #" + block.getIndex()
                        + " from " + context.getRemoteNodeId());
                broadcastBlock(block, context.getRemoteNodeId());
            } else if (block.getIndex() > blockchain.getChainSize()) {
                // The peer is ahead of us: a block landed beyond our tip+1, so we
                // are missing the blocks in between. Ask for the gap.
                context.sendMessage(new GetBlocksMessage(nodeId, blockchain.getChainSize()));
            }
        });

        // GET_BLOCKS -> serve our blocks from the requested index to the tip
        registerHandler(MessageType.GET_BLOCKS, (message, context) -> {
            int fromIndex = ((GetBlocksMessage) message).getFromIndex();
            if (fromIndex < 0) fromIndex = 0;

            List<Block> chain = blockchain.getChain();
            if (fromIndex >= chain.size()) {
                context.sendMessage(new BlocksMessage(nodeId, new ArrayList<>()));
                return;
            }
            List<Block> range = new ArrayList<>(chain.subList(fromIndex, chain.size()));
            context.sendMessage(new BlocksMessage(nodeId, range));
        });

        // BLOCKS -> append the received range in order
        registerHandler(MessageType.BLOCKS, (message, context) -> {
            List<Block> blocks = ((BlocksMessage) message).getBlocks();
            if (blocks == null) return;

            int applied = 0;
            for (Block block : blocks) {
                if (blockchain.addBlock(block)) applied++;
            }
            if (applied > 0) {
                System.out.println("  ← Synced " + applied + " block(s) from "
                        + context.getRemoteNodeId());
            }
        });

        // NEW_TRANSACTION -> validate, add to mempool, and re-gossip if new
        registerHandler(MessageType.NEW_TRANSACTION, (message, context) -> {
            Transaction tx = ((NewTransactionMessage) message).getTransaction();
            if (tx == null) return;

            // addTransaction() returns false for duplicates and invalid txs, so
            // a transaction is only relayed once - this stops gossip storms.
            if (blockchain.addTransaction(tx)) {
                System.out.println("  ← Accepted NEW_TRANSACTION from "
                        + context.getRemoteNodeId());
                broadcastTransaction(tx, context.getRemoteNodeId());
            }
        });

        // GET_MEMPOOL -> serve our current pending transactions
        registerHandler(MessageType.GET_MEMPOOL, (message, context) -> {
            List<Transaction> pending = new ArrayList<>(blockchain.getPendingTransactions());
            context.sendMessage(new MempoolMessage(nodeId, pending));
        });

        // MEMPOOL -> add received pending transactions to our pool
        registerHandler(MessageType.MEMPOOL, (message, context) -> {
            List<Transaction> txs = ((MempoolMessage) message).getTransactions();
            if (txs == null) return;

            int added = 0;
            for (Transaction tx : txs) {
                if (blockchain.addTransaction(tx)) added++;
            }
            if (added > 0) {
                System.out.println("  ← Synced " + added + " mempool tx(s) from "
                        + context.getRemoteNodeId());
            }
        });
    }

    /**
     * Broadcasts a block to every connected peer.
     *
     * @param block the block to announce
     */
    public void broadcastBlock(Block block) {
        broadcastBlock(block, null);
    }

    /**
     * THEORY: Block Propagation
     *
     * When a node mines or accepts a block it announces it to all connected
     * peers via NEW_BLOCK. Each receiver validates, appends, and relays it
     * onward, so the block floods the network. When relaying, we exclude the
     * peer we received it from to avoid bouncing it straight back.
     *
     * @param block the block to announce
     * @param excludeNodeId nodeId of a peer to skip (the sender on relay), or null
     */
    public void broadcastBlock(Block block, String excludeNodeId) {
        if (block == null) return;

        String json = new NewBlockMessage(nodeId, block).toJson();
        for (PeerInfo peer : peerManager.getConnectedPeers()) {
            if (excludeNodeId != null && excludeNodeId.equals(peer.getNodeId())) continue;
            PrintWriter writer = peerWriters.get(peer.getAddress());
            if (writer != null) writer.println(json);
        }
    }

    /**
     * Broadcasts a transaction to every connected peer.
     *
     * @param tx the transaction to announce
     */
    public void broadcastTransaction(Transaction tx) {
        broadcastTransaction(tx, null);
    }

    /**
     * Announces a transaction to all connected peers via NEW_TRANSACTION.
     * Each receiver validates, adds it to its mempool, and relays it onward,
     * so the transaction floods the network. When relaying, we exclude the
     * peer we received it from to avoid bouncing it straight back.
     *
     * @param tx the transaction to announce
     * @param excludeNodeId nodeId of a peer to skip (the sender on relay), or null
     */
    public void broadcastTransaction(Transaction tx, String excludeNodeId) {
        if (tx == null) return;

        String json = new NewTransactionMessage(nodeId, tx).toJson();
        for (PeerInfo peer : peerManager.getConnectedPeers()) {
            if (excludeNodeId != null && excludeNodeId.equals(peer.getNodeId())) continue;
            PrintWriter writer = peerWriters.get(peer.getAddress());
            if (writer != null) writer.println(json);
        }
    }

    /**
     * Parses a "host:port" address into a DISCOVERED PeerInfo.
     * Splits on the last colon so IPv4 hosts parse cleanly.
     *
     * @return PeerInfo, or null if the address is malformed
     */
    private PeerInfo parseAddress(String address) {
        if (address == null) return null;
        int idx = address.lastIndexOf(':');
        if (idx <= 0 || idx == address.length() - 1) return null;
        try {
            String host = address.substring(0, idx);
            int port = Integer.parseInt(address.substring(idx + 1));
            return new PeerInfo(host, port);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * THEORY: Handle a single client connection with message loop
     * 
     * PROTOCOL:
     * 1. Handshake: Exchange HelloMessages (existing)
     * 2. Message Loop: Continuously read, parse and dispatch messages
     * 3. Exit: When connection closes or node stops
     * 
     * MESSAGE LOOP PATTERN:
     * - Read a line (blocking)
     * - Parse it into a Message (MessageParser)
     * - Look up handler (handlers map)
     * - Call handler with message + context
     * - Repeat
     * 
     * This is the heart of the P2P protocol - it's what turns a 
     * simple socket connection into a communication channel.
     *  
     * @param clientSocket The connected client's socket
     */
    private void handleConnection(Socket clientSocket) {
        String clientInfo = clientSocket.getInetAddress().getHostAddress() + 
                ":" + clientSocket.getPort();
        
        PeerInfo peerInfo = null;
        
        try {
            clientSocket.setSoTimeout(NetworkConfig.READ_TIMEOUT_MS);            
            
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(
                    clientSocket.getOutputStream(), true);
            
            // === PHASE 1: Handshake ===
            String helloJson = reader.readLine();
            
            if (helloJson == null) return;

            HelloMessage peerHello = Message.fromJson(helloJson, HelloMessage.class);
            System.out.println("  ← Received HELLO from " + peerHello.getNodeId());
            
            // Send our HelloMessage response
            HelloMessage response = new HelloMessage(
                nodeId,
                NetworkConfig.PROTOCOL_VERSION,
                port,
                blockchain.getChainSize()
            );
            writer.println(response.toJson());
            System.out.println("  → Sent HELLO response to " + peerHello.getNodeId());
            
            // Create context for handlers
            MessageContext context = new MessageContext(writer, peerHello.getNodeId());

            String host = clientSocket.getInetAddress().getHostAddress();
            int peerPort = peerHello.getPort();
            peerInfo = new PeerInfo(host, peerPort);
            peerInfo.markConnected(peerHello.getNodeId());
            peerManager.addPeer(peerInfo);

            peerWriters.put(peerInfo.getAddress(), writer);

            // === PHASE 2: Message Loop ===
            while (running && !clientSocket.isClosed()) {
                String json = reader.readLine();
                if (json == null) break; // Connection closed by peer

                Message message = MessageParser.parse(json);

                if (peerInfo != null) peerInfo.updateLastSeen();

                if (message == null) {
                    System.err.println("  ✗ Failed to parse message from " + clientInfo);
                    continue; // Skip bad messages, don't crash                    
                }

                // Look up and call handler
                MessageHandler handler = handlers.get(message.getType());
                if (handler != null) 
                    handler.handle(message, context);
                else
                    System.out.println(" ? No handler for " + message.getType() + 
                        " from " + clientInfo);
            }
            
        } catch (IOException e) {
            if (running) {
                System.err.println("Error handling connection: " + e.getMessage());
            }        
        } finally {
            try {
                if (peerInfo != null) {
                    peerWriters.remove(peerInfo.getAddress());
                    peerInfo.markDisconnected();
                }
                clientSocket.close();
                System.out.println("  ✗ Connection closed: " + clientInfo);
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }

    /**
     * THEORY: Outgoing Peer Connections
     * 
     * A node need to initiate connections, not just accept them.
     * This is how a node joins the network - by connecting OUT to
     * known peers (seed nodes or discovered addresses).
     * 
     * FLOW:
     * 1. Check if already connected (avoid duplicates)
     * 2. Check MAX_PEERS limit
     * 3. Create Peer, connect, perform handshake
     * 4. Register in PeerManager as CONNECTED
     * 5. Start listener thread for incoming messages
     * 
     * BITCOIN: Nodes maintain ~8 outbound connections that THEY initiated,
     * plus up to 125 inbound connections from other nodes.
     * 
     * @param host remote node's hostname or IP
     * @param port remote node's listening port
     * @return the connected Peer object
     * @throws IOException if connection or handshake fails
     */
    public Peer connectToPeer(String host, int port) throws IOException {
        String address = host + ":" + port;

        // Don't connect if already known
        if (peerManager.isKnown(address))
            throw new IllegalStateException("Already connected to " + address);

        // Don't exceed connection limit
        if (!peerManager.canAcceptMore())
            throw new IllegalStateException("MAX_PEERS limit reached");

        // Create and connect
        Peer peer = new Peer(host, port);
        peer.connect();
        peer.performHandshake(nodeId, this.port, blockchain.getChainSize());

        // Register in PeerManager
        PeerInfo peerInfo = new PeerInfo(host, port);
        peerInfo.markConnected(peer.getRemoteNodeId());
        peerManager.addPeer(peerInfo);
        peerWriters.put(peerInfo.getAddress(), new PrintWriter(peer.getOutputStream(), true));

        // Start listening for messages from this peer
        peer.startListening(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                peerInfo.updateLastSeen();
                MessageHandler handler = handlers.get(message.getType());
                if (handler != null) {
                    try {
                        MessageContext context = new MessageContext(
                                new PrintWriter(peer.getOutputStream(), true),
                                peer.getRemoteNodeId());
                        handler.handle(message, context);
                    } catch (IOException e) {
                        System.err.println("Error handling message from " + address
                                + ": " + e.getMessage());
                    }
                }
            }

            @Override
            public void onDisconnect() {
                peerInfo.markDisconnected();
                System.out.println("  ✗ Outbound peer disconnected: " + address);
            }
        });

        outboundPeers.add(peer);
        System.out.println("  ✓ Outbound connection established to " + address);

        // Catch up on the peer's pending transactions now that we're connected.
        new PrintWriter(peer.getOutputStream(), true)
                .println(new GetMempoolMessage(nodeId).toJson());

        return peer;
    }

    /**
     * THEORY: Graceful shutdown of the node.
     * 
     * SHUTDOWN SEQUENCE:
     * 1. Set running = false (signals threads to stop)
     * 2. Close ServerSocket (unblocks accept() call)
     * 3. Shutdown thread pool (waits for active connections)
     * 
     * IMPORTANT: Order matters!
     * - Must close socket BEFORE waiting for threads
     * - Otherwise accept thread never exits
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        
        // Close server socket (this will interrupt accept())
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing server socket: " + e.getMessage());
            }
        }
        
        // Shutdown connection pool
        if (connectionPool != null) {
            connectionPool.shutdown();
            try {
                if (!connectionPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    connectionPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                connectionPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (heartbeatScheduler != null) 
            heartbeatScheduler.shutdown();
        
        // Disconnect outbound peers
        for (Peer peer : outboundPeers) {
            peer.disconnect();
        }
        outboundPeers.clear();

        // Wait for accept thread to finish
        if (acceptThread != null) {
            try {
                acceptThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.println("■ Node " + nodeId + " stopped");
    }

    // === Getters ===

    public String getNodeId() {
        return nodeId;
    }

    public int getPort() {
        return port;
    }

    public PeerManager getPeerManager() {
        return peerManager;
    }

    public Blockchain getBlockchain() {
        return blockchain;
    }

    public boolean isRunning() {
        return running;
    }
}