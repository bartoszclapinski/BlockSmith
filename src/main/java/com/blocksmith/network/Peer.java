package com.blocksmith.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import com.blocksmith.network.messages.HelloMessage;

/**
 * THEORY: Peer - Client-Side Connection to Remote Node
 * 
 * While Node acts as a SERVER (listening for connections),
 * Peer acts as a CLIENT (initiating connections to other nodes).
 * 
 * In a P2P network, every participant is BOTH:
 * - A server (Node) - accepting incoming connections
 * - A client (Peer) - connecting to other nodes
 * 
 * CONNECTION FLOW:
 * 1. Create Peer with target host:port
 * 2. Call connect() to establish TCP connection
 * 3. Exchange messages through input/output streams
 * 4. Call disconnect() to close connection
 * 
 * SOCKET vs SERVERSOCKET:
 * - ServerSocket: Listens and accepts connections (used by Node)
 * - Socket: Actual connection endpoint (used by Peer)
 * 
 * BITCOIN: Nodes maintain ~8 outbound connections (peers they connected to)
 * and up to 125 inbound connections (peers that connected to them).
 */
public class Peer {

    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private volatile boolean connected;
    
    // Remote node info (populated after handshake)
    private String remoteNodeId;

    private Thread listenerThread;

    /**
     * Creates a new Peer targeting a remote node.
     * Does NOT connect immediately - call connect() to establish connection.
     * 
     * @param host The hostname or IP address of the remote node
     * @param port The port the remote node is listening on
     */
    public Peer(String host, int port) {
        this.host = host;
        this.port = port;
        this.connected = false;
    }

    /**
     * THEORY: Establishing a TCP Connection
     * 
     * new Socket(host, port) performs:
     * 1. DNS lookup (if hostname provided)
     * 2. TCP three-way handshake (SYN, SYN-ACK, ACK)
     * 3. Returns connected Socket ready for communication
     * 
     * STREAMS:
     * - InputStream: Read data FROM remote node
     * - OutputStream: Write data TO remote node
     * - We wrap these in BufferedReader/PrintWriter for easy text handling
     * 
     * TIMEOUT: We set a connection timeout to avoid hanging forever
     * if the remote node is unreachable.
     * 
     * @throws IOException if connection fails
     */
    public void connect() throws IOException {
        if (connected) {
            throw new IllegalStateException("Peer is already connected");
        }
        
        // Create socket with timeout
        socket = new Socket();
        socket.connect(
            new java.net.InetSocketAddress(host, port),
            NetworkConfig.CONNECTION_TIMEOUT_MS
        );
        socket.setSoTimeout(NetworkConfig.READ_TIMEOUT_MS);
        
        // Set up streams for text communication
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(socket.getOutputStream(), true); // true = auto-flush
        
        connected = true;
        System.out.println("  → Connected to " + host + ":" + port);
    }

    /**
     * THEORY: P2P Handshake Protocol
     * 
     * After TCP connection is established, nodes exchange HELLO messages
     * to identify themselves and share basic information.
     * 
     * HANDSHAKE FLOW:
     * 1. Peer sends HelloMessage with its info
     * 2. Node responds with HelloMessage containing its info
     * 3. Both sides now know each other's nodeId, version, etc.
     * 
     * WHY HANDSHAKE?
     * - Verify protocol compatibility (version check)
     * - Exchange node identifiers
     * - Share chain length for sync decisions
     * 
     * BITCOIN: Uses "version" and "verack" messages for handshake.
     * 
     * @param localNodeId Our node's ID
     * @param localPort Our node's listening port
     * @param chainLength Our blockchain length
     * @throws IOException if handshake fails
     */
    public void performHandshake(String localNodeId, int localPort, int chainLength) 
            throws IOException {
        if (!connected) {
            throw new IllegalStateException("Not connected - call connect() first");
        }
        
        // Send our HelloMessage
        HelloMessage hello = new HelloMessage(
                localNodeId,
                NetworkConfig.PROTOCOL_VERSION,
                localPort,
                chainLength
        );
        sendMessage(hello);
        System.out.println("  → Sent HELLO to " + host + ":" + port);
        
        // Wait for HelloMessage response
        String responseJson = readLine();
        if (responseJson != null) {
            HelloMessage response = Message.fromJson(responseJson, HelloMessage.class);
            this.remoteNodeId = response.getNodeId();
            System.out.println("  ← Received HELLO from " + remoteNodeId);
        } else {
            throw new IOException("No response from remote node");
        }
    }

    /**
     * THEORY: Asynchronous Message Listening
     * 
     * Starts a background daemon thread that continuously reads
     * messages from the remote peer and fires callbacks.
     * 
     * DAEMON THREAD:
     * - A daemon thread is automatically killed when the JVM exits
     * - Perfect for background I/O tasks
     * - Won't prevent the application from shutting down
     * 
     * FLOW:
     * 1. Thread starts and enters read loop
     * 2. readLine() blocks until data arrives
     * 3. MessageParser.parse() converts JSON to Message object
     * 4. Listener.onMessage() is called with the parsed message
     * 5. On null/error -> listener.onDisconnect() is called
     * 
     * @param listener The callback interface for received messages
     */
    public void startListening(MessageListener listener) {
        if (!connected) 
            throw new IllegalStateException("Not connected - call connect() first");

        listenerThread = new Thread(() -> {
            try {
                while (connected && !socket.isClosed()) {
                    String json = reader.readLine();
                    if (json == null) break; // Connection closed

                    Message message = MessageParser.parse(json);
                    if (message != null) listener.onMessage(message);
                }
            } catch (IOException e) {
                // Expected when disconnecting or timeout
            } finally {
                listener.onDisconnect();
            }
        }, "Peer-Listener-" + host + ":" + port);

        listenerThread.setDaemon(true);
        listenerThread.start();    
    }

    /**
     * THEORY: Graceful Disconnection
     * 
     * Properly closing a connection:
     * 1. Close streams first (flushes any pending data)
     * 2. Close socket (sends TCP FIN to remote)
     * 3. Update state
     * 
     * WHY ORDER MATTERS:
     * - Closing socket first may lose buffered data
     * - Always close in reverse order of opening
     */
    public void disconnect() {
        if (!connected) {
            return;
        }
        
        connected = false;

        if (listenerThread != null) {
            listenerThread.interrupt();
            try {
                listenerThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        try {
            if (writer != null) {
                writer.close();
            }
            if (reader != null) {
                reader.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("  ← Disconnected from " + host + ":" + port);
        } catch (IOException e) {
            System.err.println("Error during disconnect: " + e.getMessage());
        }
    }

    /**
     * Sends a message to the remote node.
     * 
     * @param message The message to send (will be serialized to JSON)
     * @throws IOException if send fails
     */
    public void sendMessage(Message message) throws IOException {
        if (!connected) {
            throw new IllegalStateException("Not connected");
        }
        writer.println(message.toJson());
    }

    /**
     * Reads a line from the remote node (blocking).
     * 
     * @return The received line, or null if connection closed
     * @throws IOException if read fails
     */
    public String readLine() throws IOException {
        if (!connected) {
            throw new IllegalStateException("Not connected");
        }
        return reader.readLine();
    }

    // === Getters ===

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    public String getRemoteNodeId() {
        return remoteNodeId;
    }

    /**
     * Sets the remote node ID (called after handshake).
     */
    public void setRemoteNodeId(String remoteNodeId) {
        this.remoteNodeId = remoteNodeId;
    }

    @Override
    public String toString() {
        return String.format("Peer{%s:%d, connected=%s, remoteId=%s}",
                host, port, connected, remoteNodeId);
    }
}