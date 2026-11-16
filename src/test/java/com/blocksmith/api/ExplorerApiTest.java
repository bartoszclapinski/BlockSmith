package com.blocksmith.api;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.core.ChainParams;
import com.blocksmith.core.Transaction;
import com.blocksmith.network.Node;
import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.BlockExplorer;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.HashUtil;
import com.blocksmith.util.Target;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sprint 27a: the explorer's questions over HTTP. Blocks a page at a time,
 * a block and a transaction with their place on the chain, an address with
 * its totals, one search for all of them, and the chain in numbers.
 */
@DisplayName("Explorer API: paged blocks, block / transaction / address views, search, totals (Sprint 27a)")
class ExplorerApiTest {

    /** Easy PoW, no retarget: 25 blocks take milliseconds. */
    private static final ChainParams EASY = new ChainParams(Target.fromLeadingZeros(1), 10_000, 1_000_000, 4,
            Target.MAX, ChainParams.DEFAULT_MAX_FUTURE_DRIFT_MS,
            ChainParams.DEFAULT_MAX_BLOCK_BYTES, ChainParams.DEFAULT_MAX_BLOCK_TRANSACTIONS).withoutSignatureRule();

    private static final String MINER = "Miner1";
    private static final double REWARD = BlockchainConfig.MINING_REWARD;

    private final HttpClient client = HttpClient.newHttpClient();
    private Blockchain chain;
    private Node node;
    private ApiServer api;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        chain = new Blockchain(NoOpChainStore.INSTANCE, EASY);
        node = new Node(freePort(), chain);
        port = freePort();
        api = new ApiServer(node, port);
        api.start();
    }

    @AfterEach
    void tearDown() {
        api.stop();
        node.stop();
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonObject object(String path) throws Exception {
        HttpResponse<String> res = get(path);
        assertEquals(200, res.statusCode(), path + " -> " + res.body());
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private void mine(int blocks) {
        for (int i = 0; i < blocks; i++) chain.minePendingTransactions(MINER);
    }

    private Transaction send(String from, String to, double amount, double fee) {
        Transaction tx = new Transaction(from, to, amount, fee);
        assertTrue(chain.addTransaction(tx), "transfer accepted");
        return tx;
    }

    /**
     * Height 3: block 1 a coinbase only; block 2 also Miner1 -> Alice 10
     * (fee 0.5); block 3 a coinbase only. Returns the transfer.
     */
    private Transaction threeBlocksOneTransfer() {
        mine(1);
        Transaction transfer = send(MINER, "Alice", 10.0, 0.5);
        mine(2);
        return transfer;
    }

    private static List<Integer> indexes(JsonObject page) {
        List<Integer> out = new ArrayList<>();
        for (JsonElement b : page.getAsJsonArray("blocks")) out.add(b.getAsJsonObject().get("index").getAsInt());
        return out;
    }

    @Test
    @DisplayName("pages of blocks: newest first, each block once, the cursor does not move when a block is mined")
    void blockPages_walkEveryBlockOnce() throws Exception {
        mine(24);

        JsonObject first = object("/api/explorer/blocks?limit=10");
        assertEquals(24, first.get("height").getAsInt());
        assertEquals(List.of(24, 23, 22, 21, 20, 19, 18, 17, 16, 15), indexes(first));
        assertEquals(15, first.get("nextBefore").getAsInt());
        JsonObject row = first.getAsJsonArray("blocks").get(0).getAsJsonObject();
        assertFalse(row.has("transactions"), "a summary does not carry the transactions");
        assertEquals(chain.getBlock(24).getHash(), row.get("hash").getAsString());
        assertEquals(1, row.get("transactionCount").getAsInt());
        assertEquals(MINER, row.get("miner").getAsString());
        assertEquals(chain.getBlock(24).sizeBytes(), row.get("sizeBytes").getAsInt());

        // A block mined between two requests: "below 15" is still 14..5.
        mine(1);
        JsonObject second = object("/api/explorer/blocks?limit=10&before=15");
        assertEquals(25, second.get("height").getAsInt());
        assertEquals(List.of(14, 13, 12, 11, 10, 9, 8, 7, 6, 5), indexes(second));
        assertEquals(5, second.get("nextBefore").getAsInt());

        JsonObject last = object("/api/explorer/blocks?limit=10&before=5");
        assertEquals(List.of(4, 3, 2, 1, 0), indexes(last));
        assertFalse(last.has("nextBefore"), "genesis was sent: no further page");
        assertFalse(last.getAsJsonArray("blocks").get(4).getAsJsonObject().has("miner"), "genesis has no miner");

        Set<Integer> seen = new HashSet<>(indexes(first));
        seen.addAll(indexes(second));
        seen.addAll(indexes(last));
        assertEquals(25, seen.size(), "blocks 0..24, each exactly once");

        assertEquals(BlockExplorer.DEFAULT_BLOCK_PAGE, indexes(object("/api/explorer/blocks")).size(), "default page");
        assertEquals(26, indexes(object("/api/explorer/blocks?limit=100")).size(), "the whole chain fits");
        assertEquals(List.of(25, 24), indexes(object("/api/explorer/blocks?limit=2&before=9999")), "a cursor above the tip");
        JsonObject none = object("/api/explorer/blocks?before=0");
        assertTrue(indexes(none).isEmpty());
        assertFalse(none.has("nextBefore"));

        // The old endpoint is untouched: every block, oldest first, with transactions.
        JsonArray all = JsonParser.parseString(get("/api/blocks").body()).getAsJsonArray();
        assertEquals(26, all.size());
        assertEquals(0, all.get(0).getAsJsonObject().get("index").getAsInt());
        assertTrue(all.get(25).getAsJsonObject().get("transactions").isJsonArray());
    }

    @Test
    @DisplayName("a block says where it sits: confirmations, next hash, miner, reward")
    void blockView_confirmationsNextHashMinerReward() throws Exception {
        threeBlocksOneTransfer();

        JsonObject one = object("/api/blocks/1");
        assertEquals(3, one.get("confirmations").getAsInt(), "blocks 1, 2 and 3 are on top of it, itself included");
        assertEquals(chain.getBlock(2).getHash(), one.get("nextHash").getAsString());
        assertEquals(MINER, one.get("miner").getAsString());
        assertEquals(REWARD, one.get("reward").getAsDouble());
        assertEquals(1, one.get("transactionCount").getAsInt());
        assertTrue(one.get("transactions").isJsonArray(), "the existing fields stay");

        JsonObject two = object("/api/blocks/hash/" + chain.getBlock(2).getHash());
        assertEquals(2, two.get("index").getAsInt());
        assertEquals(2, two.get("confirmations").getAsInt());
        assertEquals(chain.getBlock(3).getHash(), two.get("nextHash").getAsString());
        assertEquals(2, two.get("transactionCount").getAsInt());
        assertEquals(0.5, two.get("totalFees").getAsDouble());
        assertEquals(REWARD + 0.5, two.get("reward").getAsDouble(), "the coinbase pays the reward plus the fees");

        JsonObject tip = object("/api/blocks/latest");
        assertEquals(3, tip.get("index").getAsInt());
        assertEquals(1, tip.get("confirmations").getAsInt());
        assertFalse(tip.has("nextHash"), "nothing follows the tip");

        JsonObject genesis = object("/api/blocks/0");
        assertEquals(4, genesis.get("confirmations").getAsInt());
        assertFalse(genesis.has("miner"), "nobody mined genesis");
        assertEquals(0.0, genesis.get("reward").getAsDouble());
        assertEquals(chain.getBlock(1).getHash(), genesis.get("nextHash").getAsString());

        // Mining one more block changes what the same block reports.
        mine(1);
        assertEquals(4, object("/api/blocks/1").get("confirmations").getAsInt());
        assertEquals(chain.getBlock(4).getHash(), object("/api/blocks/3").get("nextHash").getAsString());
        assertEquals(404, get("/api/blocks/5").statusCode());
        assertEquals(404, get("/api/blocks/hash/" + "0".repeat(64)).statusCode());
    }

    @Test
    @DisplayName("a transaction says where it sits: block hash and time, position, size, fee rate")
    void transactionView_blockContextSizeAndFeeRate() throws Exception {
        Transaction transfer = threeBlocksOneTransfer();
        Block block = chain.getBlock(2);

        JsonObject tx = object("/api/transactions/" + transfer.getTransactionId());
        assertEquals("confirmed", tx.get("status").getAsString());
        assertEquals(2, tx.get("blockIndex").getAsInt());
        assertEquals(block.getHash(), tx.get("blockHash").getAsString());
        assertEquals(block.getTimestamp(), tx.get("blockTimestamp").getAsLong());
        assertEquals(1, tx.get("position").getAsInt(), "right after the coinbase");
        assertEquals(2, tx.get("confirmations").getAsInt());
        assertEquals(transfer.sizeBytes(), tx.get("sizeBytes").getAsInt());
        assertEquals(transfer.feeRate(), tx.get("feeRate").getAsDouble(), 1e-12);

        JsonObject coinbase = object("/api/transactions/" + block.getTransactions().get(0).getTransactionId());
        assertEquals(0, coinbase.get("position").getAsInt());
        assertEquals(BlockchainConfig.COINBASE_ADDRESS, coinbase.get("sender").getAsString());

        Transaction waiting = send(MINER, "Bob", 5.0, 0.2);
        JsonObject pending = object("/api/transactions/" + waiting.getTransactionId());
        assertEquals("pending", pending.get("status").getAsString());
        assertEquals(0, pending.get("confirmations").getAsInt());
        assertFalse(pending.has("blockIndex") || pending.has("blockHash") || pending.has("position"), "not in a block yet");
        assertEquals(waiting.sizeBytes(), pending.get("sizeBytes").getAsInt());

        // The history rows of an address carry the same context.
        JsonArray history = JsonParser.parseString(get("/api/wallet/Alice/transactions").body()).getAsJsonArray();
        assertEquals(1, history.size());
        assertEquals(block.getHash(), history.get(0).getAsJsonObject().get("blockHash").getAsString());
        assertEquals(1, history.get(0).getAsJsonObject().get("position").getAsInt());
    }

    @Test
    @DisplayName("an address: received - sent = balance; the history comes in pages")
    void addressView_totalsMatchBalance_andHistoryPages() throws Exception {
        threeBlocksOneTransfer();
        Transaction waiting = send(MINER, "Bob", 5.0, 0.2);

        JsonObject miner = object("/api/explorer/address/" + MINER);
        double balance = object("/api/wallet/" + MINER).get("balance").getAsDouble();
        assertEquals(balance, miner.get("balance").getAsDouble());
        assertEquals(3 * REWARD + 0.5, miner.get("totalReceived").getAsDouble(), 1e-9, "three coinbases, one with the fee");
        assertEquals(10.5, miner.get("totalSent").getAsDouble(), 1e-9, "the amount and its fee");
        assertEquals(balance, miner.get("totalReceived").getAsDouble() - miner.get("totalSent").getAsDouble(), 1e-9);
        assertEquals(4, miner.get("transactionCount").getAsInt(), "3 coinbases + 1 transfer, confirmed");
        assertEquals(1, miner.get("pendingCount").getAsInt());
        assertEquals(5.2, miner.get("pendingOutgoing").getAsDouble(), 1e-9);

        JsonArray rows = miner.getAsJsonArray("transactions");
        assertEquals(5, rows.size());
        assertEquals(waiting.getTransactionId(), rows.get(0).getAsJsonObject().get("transactionId").getAsString(), "pending first");
        assertEquals("pending", rows.get(0).getAsJsonObject().get("status").getAsString());
        assertEquals(3, rows.get(1).getAsJsonObject().get("blockIndex").getAsInt(), "then the newest block");
        assertEquals(1, rows.get(4).getAsJsonObject().get("blockIndex").getAsInt());

        // Pages of 2: 2 + 2 + 1, the same five rows in the same order.
        List<String> paged = new ArrayList<>();
        for (int offset = 0; offset < 6; offset += 2) {
            JsonObject page = object("/api/explorer/address/" + MINER + "?limit=2&offset=" + offset);
            assertEquals(4, page.get("transactionCount").getAsInt(), "totals do not depend on the page");
            assertEquals(offset, page.get("offset").getAsInt());
            for (JsonElement e : page.getAsJsonArray("transactions")) paged.add(e.getAsJsonObject().get("transactionId").getAsString());
            assertEquals(offset < 4 ? 2 : 1, page.getAsJsonArray("transactions").size());
        }
        List<String> whole = new ArrayList<>();
        for (JsonElement e : rows) whole.add(e.getAsJsonObject().get("transactionId").getAsString());
        assertEquals(whole, paged);
        assertTrue(object("/api/explorer/address/" + MINER + "?offset=50").getAsJsonArray("transactions").isEmpty());

        JsonObject alice = object("/api/explorer/address/Alice");
        assertEquals(10.0, alice.get("balance").getAsDouble());
        assertEquals(10.0, alice.get("totalReceived").getAsDouble());
        assertEquals(0.0, alice.get("totalSent").getAsDouble());
        assertEquals(1, alice.get("transactionCount").getAsInt());

        JsonObject nobody = object("/api/explorer/address/nobody");
        assertEquals(0.0, nobody.get("balance").getAsDouble(), "an unused address is empty, not missing");
        assertEquals(0, nobody.get("transactionCount").getAsInt());
        assertTrue(nobody.getAsJsonArray("transactions").isEmpty());
    }

    @Test
    @DisplayName("search: a height, a block hash, a transaction id, a contract, an address; 404 for nothing")
    void search_findsEachKind_and404ForNothing() throws Exception {
        Transaction transfer = threeBlocksOneTransfer();
        Transaction deploy = chain.deployContract(MINER, "SHA256 PUSH " + HashUtil.applySha256("secret") + " EQUAL", 20);
        assertNotNull(deploy);
        mine(1);
        Transaction waiting = send(MINER, "Bob", 5.0, 0.2);
        String contractAddress = deploy.getRecipient();
        String contractId = contractAddress.substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length());
        String hash2 = chain.getBlock(2).getHash();

        for (String q : new String[]{"2", "%232", "%20%202%20", hash2, hash2.toUpperCase()}) {
            JsonObject found = object("/api/explorer/search?q=" + q);
            assertEquals("block", found.get("type").getAsString(), q);
            assertEquals(2, found.get("blockIndex").getAsInt(), q);
            assertEquals(hash2, found.get("blockHash").getAsString(), q);
        }

        JsonObject tx = object("/api/explorer/search?q=" + transfer.getTransactionId());
        assertEquals("transaction", tx.get("type").getAsString());
        assertEquals(transfer.getTransactionId(), tx.get("transactionId").getAsString());
        assertEquals("confirmed", tx.get("status").getAsString());
        assertEquals(2, tx.get("blockIndex").getAsInt());

        JsonObject pending = object("/api/explorer/search?q=" + waiting.getTransactionId());
        assertEquals("transaction", pending.get("type").getAsString());
        assertEquals("pending", pending.get("status").getAsString());
        assertFalse(pending.has("blockIndex"));

        for (String q : new String[]{contractId, enc(contractAddress)}) {
            JsonObject contract = object("/api/explorer/search?q=" + q);
            assertEquals("contract", contract.get("type").getAsString(), q);
            assertEquals(contractId, contract.get("contractId").getAsString());
            assertEquals(contractAddress, contract.get("address").getAsString());
        }

        for (String name : new String[]{"Alice", MINER, "Bob"}) {
            JsonObject address = object("/api/explorer/search?q=" + name);
            assertEquals("address", address.get("type").getAsString(), name + " (Bob only has a pending payment)");
            assertEquals(name, address.get("address").getAsString());
        }

        for (String q : new String[]{"999", "nobody", "0".repeat(64)}) {
            HttpResponse<String> res = get("/api/explorer/search?q=" + q);
            assertEquals(404, res.statusCode(), q);
            assertTrue(res.body().contains("Nothing found for " + q), res.body());
        }
        assertEquals(400, get("/api/explorer/search").statusCode(), "no q");
        assertEquals(400, get("/api/explorer/search?q=%20%20").statusCode(), "blank q");
    }

    @Test
    @DisplayName("totals: transactions, coins issued = sum of all balances, chain bytes; kept up as the chain grows")
    void stats_countTransactionsAndCoins() throws Exception {
        threeBlocksOneTransfer();
        Transaction deploy = chain.deployContract(MINER, "SHA256 PUSH " + HashUtil.applySha256("secret") + " EQUAL", 20);
        mine(1);
        send(MINER, "Bob", 5.0, 0.2);

        JsonObject stats = object("/api/explorer/stats");
        assertEquals(4, stats.get("height").getAsInt());
        assertEquals(5, stats.get("blocks").getAsInt());
        assertEquals(6, stats.get("transactions").getAsLong(), "4 coinbases, the transfer, the deploy");
        assertEquals(1, stats.get("pendingTransactions").getAsInt());
        assertEquals(1, stats.get("contracts").getAsInt());
        assertEquals(1, stats.get("openContracts").getAsInt());
        assertEquals(chain.getBlock(4).getTimestamp(), stats.get("tipTimestamp").getAsLong());

        long bytes = 0;
        for (Block b : chain.getChain()) bytes += b.sizeBytes();
        assertEquals(bytes, stats.get("chainBytes").getAsLong());

        // Fees are passed on, not created: 4 rewards exist, wherever they are now.
        double balances = chain.getBalance(MINER) + chain.getBalance("Alice") + chain.getBalance("Bob")
                + chain.getBalance(deploy.getRecipient());
        assertEquals(4 * REWARD, stats.get("coinsIssued").getAsDouble());
        assertEquals(balances, stats.get("coinsIssued").getAsDouble(), 1e-9);
        assertEquals(0.5 + deploy.getFee(), stats.get("totalFees").getAsDouble(), 1e-9);

        // The sums are kept between calls; adding two blocks must give what a fresh count gives.
        mine(2);
        JsonObject later = object("/api/explorer/stats");
        assertEquals(6, later.get("height").getAsInt());
        assertEquals(9, later.get("transactions").getAsLong(), "+ the pending transfer and two coinbases");
        assertEquals(6 * REWARD, later.get("coinsIssued").getAsDouble());
        assertEquals(0, later.get("pendingTransactions").getAsInt());
        long bytesLater = 0;
        for (Block b : chain.getChain()) bytesLater += b.sizeBytes();
        assertEquals(bytesLater, later.get("chainBytes").getAsLong());
    }

    @Test
    @DisplayName("totals after a reorg are counted again from genesis")
    void stats_afterReorg_recount() {
        BlockExplorer explorer = new BlockExplorer(chain);
        mine(1);
        send(MINER, "Alice", 10.0, 0.5);
        send(MINER, "Bob", 3.0, 0.5);
        mine(1);
        BlockExplorer.ChainStats before = explorer.stats();
        assertEquals(4, before.transactions());

        // Another miner's longer chain from the same genesis replaces ours.
        Blockchain other = new Blockchain(NoOpChainStore.INSTANCE, EASY);
        for (int i = 0; i < 3; i++) other.minePendingTransactions("a-miner-with-a-longer-name");
        for (int i = 1; i <= 3; i++) chain.addBlock(other.getBlock(i));
        assertEquals(other.getLatestBlock().getHash(), chain.getLatestBlock().getHash(), "reorganised onto the longer chain");

        BlockExplorer.ChainStats after = explorer.stats();
        assertEquals(new BlockExplorer(chain).stats(), after, "the same as a fresh count");
        assertEquals(3, after.height());
        assertEquals(3, after.transactions(), "three coinbases; our blocks and their transfers are gone");
        assertEquals(3 * REWARD, after.coinsIssued());
        assertEquals(0.0, after.totalFees());
        assertEquals(0, after.pendingTransactions(), "Miner1's coins no longer exist, so its transfers cannot wait either");
    }

    @Test
    @DisplayName("bad paging parameters are 400 with a message")
    void badParameters_are400() throws Exception {
        mine(2);
        String[] bad = {
                "/api/explorer/blocks?limit=0",
                "/api/explorer/blocks?limit=101",
                "/api/explorer/blocks?limit=abc",
                "/api/explorer/blocks?before=-1",
                "/api/explorer/blocks?before=1.5",
                "/api/explorer/address/" + MINER + "?limit=0",
                "/api/explorer/address/" + MINER + "?limit=101",
                "/api/explorer/address/" + MINER + "?offset=-1",
                "/api/explorer/address/" + MINER + "?offset=x",
        };
        for (String path : bad) {
            HttpResponse<String> res = get(path);
            assertEquals(400, res.statusCode(), path);
            assertTrue(JsonParser.parseString(res.body()).getAsJsonObject().has("error"), path);
        }
        assertTrue(get("/api/explorer/blocks?limit=0").body().contains("limit must be between 1 and 100"));
        assertTrue(get("/api/explorer/blocks?before=1.5").body().contains("before must be an integer"));
        assertEquals(200, get("/api/explorer/blocks?limit=1").statusCode());
        assertEquals(200, get("/api/explorer/blocks?limit=100").statusCode());
    }
}
