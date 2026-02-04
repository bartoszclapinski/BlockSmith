package com.blocksmith.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
     * THEORY: Handle a single client connection.
     * 
     * This runs in a thread from the connection pool.
     * For now, we just log and close - actual message handling
     * comes in Milestone 8d.
     * 
     * @param clientSocket The connected client's socket
     */
    private void handleConnection(Socket clientSocket) {
        String clientInfo = clientSocket.getInetAddress().getHostAddress() + 
                ":" + clientSocket.getPort();
        
        try {
            // TODO: Message handling will go here (Milestone 8d)
            // For now, just keep connection open briefly
            Thread.sleep(100);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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