package com.blocksmith.network;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.blocksmith.BlockSmithNode;
import com.blocksmith.BlockSmithNode.Running;
import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.network.messages.PeersMessage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sprint 26b: a node keeps one id across restarts, recognises itself and a
 * second link to a peer by that id, and finds more peers through GET_PEERS,
 * dialling them up to {@link NetworkConfig#TARGET_OUTBOUND}.
 */
@DisplayName("Identity and discovery (Sprint 26b)")
class IdentityDiscoveryTest {

    @TempDir
    Path tmp;

    private final List<Node> nodes = new ArrayList<>();
    private final List<Running> running = new ArrayList<>();

    @AfterEach
    void tearDown() {
        nodes.forEach(Node::stop);
        for (Running r : running) {
            try {
                r.close();
            } catch (RuntimeException e) {
                // already closed
            }
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** An in-memory node, started; dials {@code peers} (persistent) in the background. */
    private Node node(Node... peers) throws IOException {
        Node n = new Node(freePort(), new Blockchain());
        for (Node p : peers) n.addPersistentPeer("localhost:" + p.getPort());
        n.start();
        nodes.add(n);
        return n;
    }

    private Node node(String id) throws IOException {
        Node n = new Node(freePort(), new Blockchain(), id);
        n.start();
        nodes.add(n);
        return n;
    }

    private Running launch(String dir, int p2p, int api, String... extra) throws IOException {
        String[] args = new String[extra.length + 2];
        args[0] = String.valueOf(p2p);
        args[1] = String.valueOf(api);
        System.arraycopy(extra, 0, args, 2, extra.length);
        Running r = BlockSmithNode.launch(BlockSmithNode.parseArgs(args, null).withDataDir(tmp.resolve(dir)));
        running.add(r);
        return r;
    }

    /** Live links from {@code from} to the node with id {@code toId}. */
    private static long links(Node from, String toId) {
        return from.getPeerManager().getConnectedPeers().stream()
                .filter(p -> toId.equals(p.getNodeId())).count();
    }

    private static void waitFor(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting for: " + what);
            Thread.sleep(50);
        }
    }

    /** Waits, then checks that a condition still holds (nothing changes it back). */
    private static void stays(BooleanSupplier condition, String what) throws InterruptedException {
        long until = System.currentTimeMillis() + 500;
        while (System.currentTimeMillis() < until) {
            assertTrue(condition.getAsBoolean(), what);
            Thread.sleep(50);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> retries(Node target) throws Exception {
        Field f = Node.class.getDeclaredField("dialRetries");
        f.setAccessible(true);
        return (Map<String, ?>) f.get(target);
    }

    @SuppressWarnings("unchecked")
    private static MessageHandler handler(Node target, MessageType type) throws Exception {
        Field f = Node.class.getDeclaredField("handlers");
        f.setAccessible(true);
        return ((Map<MessageType, MessageHandler>) f.get(target)).get(type);
    }

    @Test
    @DisplayName("the node id is kept in <dataDir>/node-id and survives a restart")
    void nodeId_survivesRestart() throws Exception {
        int p = freePort(), api = freePort();
        Running a = launch("a", p, api);
        String id = a.node().getNodeId();
        assertTrue(id.matches("node-[0-9a-f]{16}"), id);
        assertEquals(id, Files.readString(tmp.resolve("a").resolve(NetworkConfig.NODE_ID_FILE)).trim());

        // A peer sees the same id before and after the restart.
        int pB = freePort(), apiB = freePort();
        Running b = launch("b", pB, apiB, "--peer", "localhost:" + p);
        waitFor(() -> links(b.node(), id) == 1, "B to connect to A");
        assertNotEquals(id, b.node().getNodeId(), "another data directory, another id");

        a.close();
        Running a2 = launch("a", p, api);
        assertEquals(id, a2.node().getNodeId(), "same data directory, same id");
        waitFor(() -> {
            b.node().maintainConnections();
            return links(b.node(), id) == 1;
        }, "B to reconnect to A under the same id");

        // A file that does not hold an id is replaced by a fresh one.
        Path c = tmp.resolve("c");
        Files.createDirectories(c);
        Files.writeString(c.resolve(NetworkConfig.NODE_ID_FILE), "not an id");
        String fresh = Node.loadOrCreateNodeId(c);
        assertTrue(fresh.matches("node-[0-9a-f]{16}"), fresh);
        assertEquals(fresh, Node.loadOrCreateNodeId(c));
    }

    @Test
    @DisplayName("dialling ourselves: refused when the HELLO comes back with our id, and remembered")
    void dialingSelf_refusedAndRemembered() throws Exception {
        Node a = node();
        String self = "127.0.0.1:" + a.getPort();

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> a.connectToPeer("127.0.0.1", a.getPort()));
        assertTrue(e.getMessage().contains("this node"), e.getMessage());
        assertTrue(a.getSelfAddresses().contains(self));
        stays(() -> a.getPeerManager().getConnectedCount() == 0, "no link to ourselves on either end");
        assertFalse(a.getPeerManager().isKnown(self), "not kept as a peer");

        // Remembered: the next attempt is refused without dialling.
        assertThrows(IllegalStateException.class, () -> a.connectToPeer("127.0.0.1", a.getPort()));

        // Our own addresses in a PEERS list are not recorded.
        handler(a, MessageType.PEERS).handle(new PeersMessage("node-x",
                List.of(self, "localhost:" + a.getPort(), "10.0.0.9:1234")), new MessageContext(null, "node-x"));
        assertEquals(List.of("10.0.0.9:1234"),
                a.getPeerManager().getKnownPeers().stream().map(PeerInfo::getAddress).toList());

        // Under a name the inbound side does not use (it sees 127.0.0.1): the
        // inbound side itself must refuse to register its own HELLO.
        assertThrows(IllegalStateException.class, () -> a.connectToPeer("localhost", a.getPort()));
        stays(() -> a.getPeerManager().getKnownPeers().stream().noneMatch(p -> p.getPort() == a.getPort()),
                "our own address is never a peer");

        // A persistent peer that is us is skipped too.
        a.addPersistentPeer(self);
        a.maintainConnections();
        assertEquals(0, a.getPeerManager().getConnectedCount());
    }

    @Test
    @DisplayName("a second link to a node we are linked to is closed; both ends keep the same one, and it works")
    void secondLinkToSamePeer_closed() throws Exception {
        Node small = node("node-0000000000000001");
        Node big = node("node-ffffffffffffffff");

        // L1: big dials small.
        big.connectToPeer("localhost", small.getPort());
        waitFor(() -> links(small, big.getNodeId()) == 1 && links(big, small.getNodeId()) == 1, "the first link");

        // small dials big too. Rule: of their link and ours, the one opened
        // by the smaller id stays, so small keeps its new link and closes L1.
        small.connectToPeer("localhost", big.getPort());
        assertEquals(1, links(small, big.getNodeId()), "the losing link is down at once, not when its thread ends");
        waitFor(() -> links(small, big.getNodeId()) == 1 && links(big, small.getNodeId()) == 1, "one link again");
        assertTrue(direction(small, big.getNodeId()), "small keeps the link it opened");
        assertFalse(direction(big, small.getNodeId()), "big sees it as inbound");

        // big already has small's link as 127.0.0.1:port, and knows that
        // localhost:port answered with small's id: both refused before dialling.
        assertThrows(IllegalStateException.class, () -> big.connectToPeer("127.0.0.1", small.getPort()));
        IllegalStateException known = assertThrows(IllegalStateException.class,
                () -> big.connectToPeer("localhost", small.getPort()));
        assertTrue(known.getMessage().contains(small.getNodeId()), known.getMessage());
        // small dials big under another name: it already has its own link, the new one goes.
        IllegalStateException own = assertThrows(IllegalStateException.class,
                () -> small.connectToPeer("127.0.0.1", big.getPort()));
        assertTrue(own.getMessage().contains(big.getNodeId()), own.getMessage());
        waitFor(() -> links(small, big.getNodeId()) == 1 && links(big, small.getNodeId()) == 1, "the extra links to close");
        stays(() -> links(small, big.getNodeId()) == 1 && links(big, small.getNodeId()) == 1, "one link stays");
        assertTrue(direction(small, big.getNodeId()), "still small's link");

        // Names are remembered: refused without dialling.
        assertThrows(IllegalStateException.class, () -> small.connectToPeer("127.0.0.1", big.getPort()));

        // The other way round: small2 dials big2 first; big2 dials back under a
        // name it has not seen. Its id is larger, so its new link goes.
        Node small2 = node("node-0000000000000002");
        Node big2 = node("node-fffffffffffffffe");
        small2.connectToPeer("localhost", big2.getPort());
        waitFor(() -> links(big2, small2.getNodeId()) == 1, "small2's link");
        IllegalStateException lost = assertThrows(IllegalStateException.class,
                () -> big2.connectToPeer("localhost", small2.getPort()));
        assertTrue(lost.getMessage().contains(small2.getNodeId()), lost.getMessage());
        waitFor(() -> links(small2, big2.getNodeId()) == 1 && links(big2, small2.getNodeId()) == 1, "one link 2");
        stays(() -> links(small2, big2.getNodeId()) == 1 && direction(small2, big2.getNodeId()), "small2's link stays");

        // ... and the surviving link carries blocks both ways.
        Block block = small.getBlockchain().minePendingTransactions("alice");
        small.broadcastBlock(block);
        waitFor(() -> big.getBlockchain().getChainSize() == 2, "small's block to reach big");
        Block next = big.getBlockchain().minePendingTransactions("bob");
        big.broadcastBlock(next);
        waitFor(() -> small.getBlockchain().getChainSize() == 3, "big's block to reach small");
    }

    /** True if {@code from}'s link to {@code toId} is one it opened. */
    private static boolean direction(Node from, String toId) {
        return from.getPeerManager().getConnectedPeers().stream()
                .filter(p -> toId.equals(p.getNodeId())).findFirst().orElseThrow().isOutbound();
    }

    @Test
    @DisplayName("A and C know only B: A finds C through GET_PEERS and connects; the link outlives B")
    @SuppressWarnings("unchecked")
    void discovery_findsAndConnectsThirdNode() throws Exception {
        Node b = node();
        Node a = node(b);
        waitFor(() -> links(a, b.getNodeId()) == 1, "A to join B");
        Node c = node(b);

        // C asks B for peers as soon as it connects (Bitcoin: getaddr), hears
        // about A and dials it: no heartbeat needed (that one comes every ~30 s).
        waitFor(() -> links(a, c.getNodeId()) == 1 && links(c, a.getNodeId()) == 1, "A and C to find each other");
        stays(() -> links(a, c.getNodeId()) == 1 && links(c, a.getNodeId()) == 1, "exactly one link A-C");

        // Nobody stored its own address as a peer.
        for (Node n : List.of(a, b, c)) {
            assertTrue(n.getPeerManager().getKnownPeers().stream().noneMatch(p -> p.getPort() == n.getPort()),
                    n.getNodeId() + " lists itself: " + n.getPeerManager().getKnownPeers());
        }

        // B's answer to A: C's address, once, not A's own and not B's.
        java.util.concurrent.atomic.AtomicReference<Message> reply = new java.util.concurrent.atomic.AtomicReference<>();
        handler(b, MessageType.GET_PEERS).handle(new com.blocksmith.network.messages.GetPeersMessage(a.getNodeId()),
                new MessageContext(null, a.getNodeId()) {
                    @Override
                    public void sendMessage(Message m) {
                        reply.set(m);
                    }
                });
        List<String> advertised = ((PeersMessage) reply.get()).getPeers();
        assertEquals(1, advertised.size(), advertised.toString());
        assertTrue(advertised.get(0).endsWith(":" + c.getPort()), advertised.toString());

        // The heartbeat asks for peers every DISCOVERY_EVERY_HEARTBEATS-th beat.
        java.io.StringWriter sent = new java.io.StringWriter();
        Node lone = new Node(freePort(), new Blockchain());
        PeerInfo fake = new PeerInfo("10.0.0.1", 8335);
        fake.markConnected("node-fake");
        lone.getPeerManager().addPeer(fake);
        Field writers = Node.class.getDeclaredField("peerWriters");
        writers.setAccessible(true);
        ((Map<String, java.io.PrintWriter>) writers.get(lone)).put(fake.getAddress(), new java.io.PrintWriter(sent, true));
        java.lang.reflect.Method beat = Node.class.getDeclaredMethod("heartbeatTask");
        beat.setAccessible(true);
        for (int i = 0; i < 2 * NetworkConfig.DISCOVERY_EVERY_HEARTBEATS; i++) beat.invoke(lone);
        String out = sent.toString();
        assertEquals(2 * NetworkConfig.DISCOVERY_EVERY_HEARTBEATS, out.split("\"PING\"", -1).length - 1, out);
        assertEquals(2, out.split("\"GET_PEERS\"", -1).length - 1, out);

        // B goes away; A's block still reaches C.
        b.stop();
        waitFor(() -> links(a, b.getNodeId()) == 0 && links(c, b.getNodeId()) == 0, "A and C to see B go");
        Block block = a.getBlockchain().minePendingTransactions("alice");
        a.broadcastBlock(block);
        waitFor(() -> c.getBlockchain().getLatestBlock().getHash().equals(block.getHash()), "A's block to reach C");
    }

    @Test
    @DisplayName("auto-connect stops at TARGET_OUTBOUND links of our own, and nobody goes above MAX_PEERS")
    void autoConnect_respectsLimit() throws Exception {
        Node hub = node();
        List<Node> others = new ArrayList<>();
        for (int i = 0; i < 6; i++) others.add(node(hub));
        waitFor(() -> others.stream().allMatch(n -> links(n, hub.getNodeId()) == 1), "six nodes to join the hub");

        Node x = node(hub);
        waitFor(() -> {
            x.askForPeers();
            x.maintainConnections();
            return x.getOutboundCount() == NetworkConfig.TARGET_OUTBOUND;
        }, "X to fill its outbound slots");

        // More rounds of discovery do not open more links of our own.
        for (int i = 0; i < 5; i++) {
            x.askForPeers();
            Thread.sleep(100);
            x.maintainConnections();
        }
        assertEquals(NetworkConfig.TARGET_OUTBOUND, x.getOutboundCount());
        for (Node n : nodes) {
            assertTrue(n.getOutboundCount() <= NetworkConfig.TARGET_OUTBOUND, n.getNodeId());
            assertTrue(n.getPeerManager().getConnectedCount() <= NetworkConfig.MAX_PEERS, n.getNodeId());
        }

        // An unreachable discovered address waits before the next dial.
        Node y = node();
        String dead = "localhost:" + freePort();
        handler(y, MessageType.PEERS).handle(new PeersMessage("node-x", List.of(dead)),
                new MessageContext(null, "node-x"));
        Map<String, ?> retries = retries(y);
        waitFor(() -> {
            y.maintainConnections();
            return retries.containsKey(dead);
        }, "the failed dial to be recorded");
        Object first = retries.get(dead);
        y.maintainConnections();
        y.maintainConnections();
        assertSame(first, retries.get(dead), "backed off: not dialled again yet");
        assertEquals(PeerState.DISCOVERED, y.getPeerManager().getPeer(dead).getState());
    }
}
