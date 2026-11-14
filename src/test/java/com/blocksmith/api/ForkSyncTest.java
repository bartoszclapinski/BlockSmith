package com.blocksmith.api;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
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
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.Target;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fork-aware sync over real TCP connections (Milestone 21c): block locators,
 * a node on another branch catching up through a reorg, and two nodes that
 * both mine and still end on one chain.
 */
@DisplayName("Fork Sync Tests")
class ForkSyncTest {

    private static final int API_PORT_BASE = 18300;
    private static final int P2P_PORT_BASE = 20700;
    private static int counter = 0;

    /** Easy PoW, no retarget: forks are cheap to make and every block weighs the same. */
    private static final ChainParams EASY = new ChainParams(Target.fromLeadingZeros(1), 10_000, 1_000_000, 4,
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

    private static Blockchain chain() {
        return new Blockchain(NoOpChainStore.INSTANCE, EASY);
    }

    /** Starts a node (P2P listening) with an API; returns the API port. */
    private int startNode(Blockchain chain) throws Exception {
        int offset = counter++;
        Node node = new Node(P2P_PORT_BASE + offset, chain);
        node.start();
        ApiServer api = new ApiServer(node, API_PORT_BASE + offset);
        api.start();
        nodes.add(node);
        apis.add(api);
        return API_PORT_BASE + offset;
    }

    private void connect(int from, int to) throws Exception {
        nodes.get(from).connectToPeer("localhost", nodes.get(to).getPort());
        waitFor(() -> nodes.get(from).getPeerManager().getConnectedPeers().size() == 1
                && nodes.get(to).getPeerManager().getConnectedPeers().size() == 1, "the nodes to connect");
    }

    private JsonObject get(int port, String path) throws Exception {
        HttpResponse<String> res = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private HttpResponse<String> post(int port, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static void waitFor(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting for: " + what);
            Thread.sleep(50);
        }
    }

    @Test
    @DisplayName("Block locator: tip first, 10 one by one, then doubling gaps, genesis last; finds the common block")
    void locator_findsCommonAncestor() {
        Blockchain ours = chain();
        Blockchain theirs = chain();
        for (int i = 0; i < 12; i++) {
            Block shared = ours.minePendingTransactions("shared");
            assertTrue(theirs.addBlock(shared));
        }
        for (int i = 0; i < 18; i++) ours.minePendingTransactions("us");      // ours: #30
        for (int i = 0; i < 5; i++) theirs.minePendingTransactions("them");   // theirs: #17

        List<String> locator = ours.getBlockLocator();
        List<Block> chain = ours.getChain();
        List<Integer> heights = new ArrayList<>();
        for (String hash : locator) {
            for (Block b : chain) if (b.getHash().equals(hash)) heights.add(b.getIndex());
        }
        assertEquals(List.of(30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 19, 15, 7, 0), heights);

        List<Block> reply = theirs.getBlocksAfter(locator, Blockchain.MAX_BLOCKS_PER_REPLY);
        assertEquals(8, reply.get(0).getIndex(), "#7 is the newest locator block they have");
        assertEquals(theirs.getLatestBlock().getHash(), reply.get(reply.size() - 1).getHash());
        assertEquals(3, theirs.getBlocksAfter(locator, 3).size(), "At most max blocks");
        assertEquals(1, theirs.getBlocksAfter(List.of("unknown"), 1).get(0).getIndex(), "Nothing in common: after genesis");
    }

    @Test
    @DisplayName("A node on another branch fetches the missing blocks over TCP and reorganises; status reports it")
    void nodeOnOtherBranch_syncsAndReorgs() throws Exception {
        Blockchain chainA = chain();
        Blockchain chainB = chain();
        int apiA = startNode(chainA);
        int apiB = startNode(chainB);
        for (int i = 0; i < 3; i++) chainA.minePendingTransactions("A");      // A: #3
        Block b1 = chainB.minePendingTransactions("B");                        // B: #1, a fork
        connect(1, 0);

        // A's next block reaches B, whose parent B has never seen: B asks with
        // its locator, A answers with a1..a4, and B switches branches.
        assertEquals(201, post(apiA, "/api/mine", "{\"minerAddress\":\"A\"}").statusCode());
        waitFor(() -> chainB.getLatestBlock().getHash().equals(chainA.getLatestBlock().getHash()), "B to reach A's tip");

        JsonObject status = get(apiB, "/api/network/status");
        assertEquals(1, status.get("reorgs").getAsInt());
        JsonObject last = status.getAsJsonObject("lastReorg");
        assertEquals(1, last.get("depth").getAsInt());
        assertEquals(0, last.get("forkHeight").getAsInt());
        assertEquals(b1.getHash(), last.get("oldTip").getAsString());
        assertEquals(2, last.get("newHeight").getAsInt(), "The switch came with a2 (more work than b1); a3, a4 then extended");
        assertEquals(4, status.get("chainLength").getAsInt() - 1);
        assertEquals(chainB.getTotalWork(), new BigInteger(status.get("totalWork").getAsString()));
        assertEquals(1, status.get("sideBlocks").getAsInt(), "B's own block, kept");
        assertEquals(0.0, chainB.getBalance("B"), 1e-9);
        assertTrue(chainB.isChainValid());
    }

    @Test
    @DisplayName("Two nodes both mining under parallel traffic end on the same valid chain; nothing lost or doubled")
    void twoMiningNodes_convergeToSameValidChain() throws Exception {
        Blockchain chainA = chain();
        Blockchain chainB = chain();
        int apiA = startNode(chainA);
        int apiB = startNode(chainB);
        connect(1, 0);
        for (int i = 0; i < 4; i++) assertEquals(201, post(apiA, "/api/mine", "{\"minerAddress\":\"alice\"}").statusCode());
        waitFor(() -> chainB.getChainSize() == 5, "B to receive Alice's blocks");         // Alice has 200

        Set<String> admitted = Collections.synchronizedSet(new HashSet<>());
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
                            // One miner per node: competing blocks, forks and reorgs.
                            int api = id == 0 ? apiA : apiB;
                            assertEquals(201, post(api, "/api/mine", "{\"minerAddress\":\"miner" + id + "\"}").statusCode());
                        } else {
                            int api = id % 2 == 0 ? apiA : apiB;
                            HttpResponse<String> res = post(api, "/api/transactions", "{\"sender\":\"alice\",\"recipient\":\"bob"
                                    + id + "\",\"amount\":" + (1 + i * 0.01) + ",\"fee\":0.01}");
                            if (res.statusCode() == 201) {
                                admitted.add(JsonParser.parseString(res.body()).getAsJsonObject()
                                        .get("transactionId").getAsString());
                            }
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

        // Settle: A mines until its mempool is empty; one more block from A
        // breaks any tie, and B must follow A onto one chain.
        do {
            assertEquals(201, post(apiA, "/api/mine", "{\"minerAddress\":\"sweeper\"}").statusCode());
            Thread.sleep(200);
        } while (chainA.getPendingCount() > 0);
        waitFor(() -> chainB.getLatestBlock().getHash().equals(chainA.getLatestBlock().getHash()), "B to reach A's tip");
        waitFor(() -> chainB.getPendingCount() == 0, "B's mempool to empty");

        List<Block> a = chainA.getChain();
        List<Block> b = chainB.getChain();
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) assertEquals(a.get(i).getHash(), b.get(i).getHash(), "height " + i);
        assertTrue(chainA.isChainValid());
        assertTrue(chainB.isChainValid());

        Map<String, Integer> confirmed = new HashMap<>();
        Set<String> addresses = new HashSet<>();
        for (Block block : a) {
            for (int i = 0; i < block.getTransactions().size(); i++) {
                Transaction tx = block.getTransactions().get(i);
                addresses.add(tx.getRecipient());
                if (i > 0) {
                    addresses.add(tx.getSender());
                    confirmed.merge(tx.getTransactionId(), 1, Integer::sum);
                }
            }
        }
        for (String id : admitted) assertEquals(1, confirmed.get(id), "Admitted tx confirmed exactly once: " + id);
        double supply = 0;
        for (String address : addresses) {
            if (!address.equals(BlockchainConfig.COINBASE_ADDRESS)) supply += chainA.getBalance(address);
        }
        assertEquals((a.size() - 1) * BlockchainConfig.MINING_REWARD, supply, 1e-6, "Only block rewards created coins");
        System.out.println("Converged at #" + (a.size() - 1) + " after " + chainA.getReorgCount() + " reorg(s) on A and "
                + chainB.getReorgCount() + " on B; " + admitted.size() + " transfers");
    }
}
