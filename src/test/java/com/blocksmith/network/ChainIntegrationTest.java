package com.blocksmith.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.core.Blockchain;
import com.blocksmith.network.messages.HelloMessage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that Node is backed by a Blockchain and reports its real chain
 * length during the HELLO handshake (Milestone 10a).
 */
@DisplayName("Chain Integration Tests")
class ChainIntegrationTest {

    private Node node;

    private static final int TEST_PORT_BASE = 19700;
    private static int portCounter = 0;

    private int getNextPort() {
        return TEST_PORT_BASE + (portCounter++);
    }

    @AfterEach
    void tearDown() {
        if (node != null) {
            node.stop();
        }
    }

    @Test
    @DisplayName("Node exposes the injected blockchain")
    void node_exposesBlockchain() {
        Blockchain chain = new Blockchain();
        node = new Node(getNextPort(), chain);

        assertSame(chain, node.getBlockchain(), "getBlockchain() returns the injected chain");
    }

    @Test
    @DisplayName("HELLO response carries the node's real chain length")
    void node_helloCarriesChainLength() throws Exception {
        Blockchain chain = new Blockchain();
        chain.addBlock("block-1");
        chain.addBlock("block-2");
        int expected = chain.getChainSize(); // genesis + 2 = 3

        int port = getNextPort();
        node = new Node(port, chain);
        node.start();

        try (Socket socket = new Socket("localhost", port)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(new HelloMessage("client", NetworkConfig.PROTOCOL_VERSION, 12345, 1).toJson());

            String responseJson = in.readLine();
            HelloMessage response = Message.fromJson(responseJson, HelloMessage.class);

            assertEquals(expected, response.getChainLength(),
                    "HELLO response should report the real chain length");
        }
    }
}
