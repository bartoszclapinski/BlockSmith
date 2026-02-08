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
import java.util.HashMap;
import java.util.Map;

import com.blocksmith.network.messages.PongMessage;
import com.blocksmith.network.messages.HelloMessage;

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

    /**
     * Creates a new Node with default port.
     */
    public Node() {
        this(NetworkConfig.DEFAULT_PORT);
    }

    /**
     * Creates a new Node on specified port.
     * 
     * @param port The port to listen on
     */
    public Node(int port) {
        this.nodeId = generateNodeId();
        this.port = port;
        this.running = false;
        this.handlers = new HashMap<>();
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
        
        System.out.println("▶ Node " + nodeId + " started on port " + port);
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
                0  // chainLength - will be set when blockchain is integrated
            );
            writer.println(response.toJson());
            System.out.println("  → Sent HELLO response to " + peerHello.getNodeId());
            
            // Create context for handlers
            MessageContext context = new MessageContext(writer, peerHello.getNodeId());

            // === PHASE 2: Message Loop ===
            while (running && !clientSocket.isClosed()) {
                String json = reader.readLine();
                if (json == null) break; // Connection closed by peer

                Message message = MessageParser.parse(json);
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
                clientSocket.close();
                System.out.println("  ✗ Connection closed: " + clientInfo);
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
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

    public boolean isRunning() {
        return running;
    }
}