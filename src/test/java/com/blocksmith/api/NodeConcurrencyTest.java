package com.blocksmith.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.core.ChainParams;
import com.blocksmith.core.Transaction;
import com.blocksmith.network.Node;
import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.Target;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency through the real entry points (Milestone 20c): consistent API
 * snapshots, and two nodes over TCP under parallel API traffic.
 */
@DisplayName("Node Concurrency Tests")
class NodeConcurrencyTest {

    private static final int API_PORT_BASE = 18250;
    private static final int P2P_PORT_BASE = 20600;
    private static int counter = 0;

    /**
     * Trivial PoW that only gets easier: a 1 ms target block time makes every
     * window "too slow", so retargets move toward powLimit. Retargets still
     * happen every 5 blocks, so the countdown keeps changing.
     */
    private static final ChainParams FAST = new ChainParams(Target.fromLeadingZeros(1), 1, 5, 4,
            Target.MAX, ChainParams.DEFAULT_MAX_FUTURE_DRIFT_MS,
            ChainParams.DEFAULT_MAX_BLOCK_BYTES, ChainParams.DEFAULT_MAX_BLOCK_TRANSACTIONS);

    private final List<Node> nodes = new ArrayList<>();
    private final List<ApiServer> apis = new ArrayList<>();
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void tearDown() {
        apis.forEach(ApiServer::stop);
        nodes.forEach(Node::stop);
    }

    /** A node with an API; returns its API port. */
    private int startNode(Blockchain chain, boolean p2p) throws Exception {
        int offset = counter++;
        Node node = new Node(P2P_PORT_BASE + offset, chain);
        if (p2p) node.start();
        ApiServer api = new ApiServer(node, API_PORT_BASE + offset);
        api.start();
        nodes.add(node);
        apis.add(api);
        return API_PORT_BASE + offset;
    }

    private JsonObject get(int port, String path) throws Exception {
        HttpResponse<String> res = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private int post(int port, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private static void waitFor(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting for: " + what);
            Thread.sleep(50);
        }
    }

    @Test
    @DisplayName("atomically() keeps writers out until the group of reads is done")
    void atomically_blocksWritersUntilDone() throws Exception {
        Blockchain c = new Blockchain(NoOpChainStore.INSTANCE, FAST);
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Integer> reader = CompletableFuture.supplyAsync(() -> c.atomically(() -> {
            int before = c.getChainSize();
            inside.countDown();
            try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { }
            return c.getChainSize() - before;                   // must not see the mine below
        }));
        assertTrue(inside.await(10, TimeUnit.SECONDS));
        CompletableFuture<Block> miner = CompletableFuture.supplyAsync(() -> c.minePendingTransactions("m"));
        Thread.sleep(300);
        assertFalse(miner.isDone(), "The miner's submit waits for the reader's snapshot");
        release.countDown();
        assertEquals(0, reader.get(10, TimeUnit.SECONDS), "No block landed inside the snapshot");
        assertNotNull(miner.get(10, TimeUnit.SECONDS));
        assertEquals(2, c.getChainSize());
    }

    @Test
    @DisplayName("Status and wallet responses come from one snapshot while blocks keep landing")
    void statusSnapshot_isConsistent() throws Exception {
        Blockchain chain = new Blockchain(NoOpChainStore.INSTANCE, FAST);
        chain.minePendingTransactions("alice");                                     // Alice has 50
        int api = startNode(chain, false);

        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch polling = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                polling.await();
                for (int i = 0; i < 300; i++) {
                    chain.addTransaction(new Transaction("alice", "bob", 0.1 + i * 0.0001));
                    chain.minePendingTransactions("m");
                }
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.set(true);
            }
        });
        worker.start();

        double lastAvailable = Double.MAX_VALUE;
        int polls = 0;
        polling.countDown();
        while (!done.get()) {
            JsonObject s = get(api, "/api/network/status");
            int length = s.get("chainLength").getAsInt();
            int interval = s.get("retargetInterval").getAsInt();
            assertEquals(interval - length % interval, s.get("blocksUntilRetarget").getAsInt(),
                    "countdown matches the height in the same response: " + s);

            JsonObject w = get(api, "/api/wallet/alice");
            double available = w.get("balance").getAsDouble() - w.get("pendingOutgoing").getAsDouble();
            assertTrue(available <= lastAvailable + 1e-9,
                    "Alice's spendable balance only goes down: " + lastAvailable + " -> " + available);
            lastAvailable = available;
            polls++;
        }
        worker.join();
        assertNull(failure.get());
        assertTrue(polls > 5, "The poller ran during the traffic (" + polls + " polls)");
    }

    @Test
    @DisplayName("Two nodes over TCP under parallel API traffic converge to the same valid chain")
    void twoNodes_concurrentApiTraffic_convergeToSameValidChain() throws Exception {
        Blockchain chainA = new Blockchain(NoOpChainStore.INSTANCE, FAST);
        Blockchain chainB = new Blockchain(NoOpChainStore.INSTANCE, FAST);
        int apiA = startNode(chainA, true);
        int apiB = startNode(chainB, true);
        nodes.get(1).connectToPeer("localhost", nodes.get(0).getPort());
        waitFor(() -> nodes.get(0).getPeerManager().getConnectedPeers().size() == 1
                && nodes.get(1).getPeerManager().getConnectedPeers().size() == 1, "the two nodes to connect");

        for (int i = 0; i < 4; i++) assertEquals(201, post(apiA, "/api/mine", "{\"minerAddress\":\"alice\"}"));
        waitFor(() -> chainB.getChainSize() == 5, "B to receive Alice's blocks");          // Alice has 200

        // Only A mines: this node has no fork choice, so two miners would split the chain.
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 6; t++) {
            final int id = t;
            Thread th = new Thread(() -> {
                try {
                    go.await();
                    for (int i = 0; i < 10; i++) {
                        if (id < 2) {
                            assertEquals(201, post(apiA, "/api/mine", "{\"minerAddress\":\"miner" + id + "\"}"));
                        } else {
                            int api = id % 2 == 0 ? apiA : apiB;
                            post(api, "/api/transactions", "{\"sender\":\"alice\",\"recipient\":\"bob" + id
                                    + "\",\"amount\":" + (1 + i * 0.01) + ",\"fee\":0.01}");
                        }
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            });
            threads.add(th);
            th.start();
        }
        go.countDown();
        for (Thread th : threads) th.join(60_000);
        assertNull(failure.get(), () -> "Failed: " + failure.get());

        // Let gossip settle, then sweep A's mempool and wait for B to follow.
        waitFor(() -> chainA.getPendingCount() == chainB.getPendingCount(), "the mempools to sync");
        while (chainA.getPendingCount() > 0) assertEquals(201, post(apiA, "/api/mine", "{\"minerAddress\":\"sweeper\"}"));
        waitFor(() -> chainB.getLatestBlock().getHash().equals(chainA.getLatestBlock().getHash()), "B to reach A's tip");
        waitFor(() -> chainB.getPendingCount() == 0, "B's mempool to empty");

        List<Block> a = chainA.getChain();
        List<Block> b = chainB.getChain();
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(i, a.get(i).getIndex());
            assertEquals(a.get(i).getHash(), b.get(i).getHash(), "same block at height " + i);
        }
        assertTrue(chainA.isChainValid());
        assertTrue(chainB.isChainValid());

        // Every transaction confirmed once; total supply = 50 per mined block
        // (a coinbase also carries the fees, but those only change hands).
        Set<String> ids = new HashSet<>();
        Set<String> addresses = new HashSet<>();
        for (Block block : a) {
            for (int i = 0; i < block.getTransactions().size(); i++) {
                Transaction tx = block.getTransactions().get(i);
                addresses.add(tx.getRecipient());
                if (i == 0) continue;
                addresses.add(tx.getSender());
                assertTrue(ids.add(tx.getTransactionId()), "confirmed twice: " + tx.getTransactionId());
            }
        }
        assertEquals(40, ids.size(), "All 40 transfers (20 via A, 20 via B) were confirmed");
        double supply = 0;
        for (String address : addresses) {
            if (!address.equals("COINBASE")) supply += chainA.getBalance(address);
        }
        assertEquals((a.size() - 1) * com.blocksmith.util.BlockchainConfig.MINING_REWARD, supply, 1e-6,
                "No coins created or destroyed beyond the block rewards");
    }
}
