package com.blocksmith.util;

import java.util.ArrayList;
import java.util.List;

import com.blocksmith.contract.Contract;
import com.blocksmith.contract.ContractStatus;
import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.core.Transaction;

/**
 * Read-only questions about the chain, the way a person asks them (Sprint 27).
 *
 * THEORY: WHY AN EXPLORER IS A SEPARATE THING
 *
 * A node keeps blocks in the order they were mined, because that is what
 * validation needs. A person asks something else: "what is in block 12?",
 * "did my payment arrive?", "what did this address do?". An explorer is the
 * read side of the chain: the same data, looked up by height, hash, id or
 * address, a page at a time.
 *
 * Three things it shows are NOT stored in any block and are computed from
 * the chain around it:
 *
 *   confirmations   grow with every later block: tip height - height + 1
 *   next block hash a block is hashed before its successor exists
 *   balance         the sum of an address's transactions; nothing stores it
 *
 * Every answer is built from ONE snapshot of the chain
 * ({@link Blockchain#atomically}), so a block landing in the middle cannot
 * pair one block's hash with another's confirmations. The explorer shows the
 * active chain only: a block on a side branch is not found.
 *
 * It never changes the chain.
 */
public final class BlockExplorer {

    /** The most rows one request may ask for. */
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_BLOCK_PAGE = 20;
    public static final int DEFAULT_HISTORY_PAGE = 25;

    private final Blockchain blockchain;

    /** Chain totals up to {@link #totalsTip}; read and written under the chain's lock. */
    private Totals totals;
    private String totalsTip;

    public BlockExplorer(Blockchain blockchain) {
        this.blockchain = blockchain;
    }

    // ===== BLOCKS =====

    /**
     * A block without its transactions: what a list of blocks shows.
     *
     * @param miner  recipient of the coinbase, or null for a block without one (genesis)
     * @param reward what the coinbase paid (the block reward plus the fees)
     */
    public record BlockSummary(int index, String hash, long timestamp, int transactionCount,
                               int sizeBytes, double totalFees, String miner, double reward) {}

    /**
     * One page of blocks, newest first.
     *
     * @param height     the tip's height when the page was read
     * @param nextBefore pass as {@code before} for the next (older) page; null after genesis
     */
    public record BlockPage(int height, List<BlockSummary> blocks, Integer nextBefore) {}

    /** A block and where it sits on the chain. */
    public record BlockView(Block block, BlockSummary summary, int confirmations, String nextHash) {}

    /**
     * THEORY: PAGING BY CURSOR, NOT BY PAGE NUMBER
     *
     * "Page 2" changes meaning every time a block is mined: every block moves
     * down by one, so a reader going from page 1 to page 2 sees one block
     * twice. "The 20 blocks below height 41" means the same blocks for ever,
     * because a height does not move. So the cursor is a height.
     *
     * @param before only blocks below this height; null starts at the tip
     * @param limit  1 to {@link #MAX_PAGE_SIZE}
     * @throws IllegalArgumentException for a limit out of range or a negative cursor
     */
    public BlockPage blockPage(Integer before, int limit) {
        checkLimit(limit);
        if (before != null && before < 0) throw new IllegalArgumentException("before must not be negative");
        return blockchain.atomically(() -> {
            int height = blockchain.getChainSize() - 1;
            int top = before == null ? height : Math.min(before - 1, height);
            int bottom = Math.max(top - limit + 1, 0);
            List<BlockSummary> blocks = new ArrayList<>();
            for (int i = top; i >= bottom; i--) blocks.add(summarize(blockchain.getBlock(i)));
            return new BlockPage(height, blocks, top >= 0 && bottom > 0 ? bottom : null);
        });
    }

    /** The block at {@code index}, or null if the chain is not that tall. */
    public BlockView blockView(int index) {
        return blockchain.atomically(() -> {
            int size = blockchain.getChainSize();
            if (index < 0 || index >= size) return null;
            Block block = blockchain.getBlock(index);
            String next = index + 1 < size ? blockchain.getBlock(index + 1).getHash() : null;
            return new BlockView(block, summarize(block), size - index, next);
        });
    }

    /** The block with this hash, or null if no block on the active chain has it. */
    public BlockView blockView(String hash) {
        return blockchain.atomically(() -> {
            Block block = blockchain.getBlockByHash(hash);
            return block == null ? null : blockView(block.getIndex());
        });
    }

    /** The list row of a block: counts, size, fees, and who mined it. */
    public static BlockSummary summarize(Block block) {
        List<Transaction> txs = block.getTransactions();
        String miner = null;
        double reward = 0;
        double fees = 0;
        for (int i = 0; i < txs.size(); i++) {
            Transaction tx = txs.get(i);
            if (i == 0 && isCoinbase(tx)) {
                miner = tx.getRecipient();
                reward = tx.getAmount();
            } else {
                fees += tx.getFee();
            }
        }
        return new BlockSummary(block.getIndex(), block.getHash(), block.getTimestamp(), txs.size(),
                block.sizeBytes(), fees, miner, reward);
    }

    private static boolean isCoinbase(Transaction tx) {
        return BlockchainConfig.COINBASE_ADDRESS.equals(tx.getSender());
    }

    // ===== TRANSACTIONS =====

    /**
     * A transaction and where it sits. For a pending one the block fields
     * are null and confirmations is 0.
     *
     * @param position index inside its block (0 is the coinbase)
     */
    public record TransactionView(Transaction transaction, Integer blockIndex, String blockHash,
                                  Long blockTimestamp, Integer position, int confirmations) {
        public boolean isConfirmed() {
            return blockIndex != null;
        }
    }

    /** The transaction with this id (pending or confirmed), or null. */
    public TransactionView transactionView(String transactionId) {
        return blockchain.atomically(() -> {
            Blockchain.TransactionLookup found = blockchain.findTransaction(transactionId);
            return found == null ? null : describe(found);
        });
    }

    /**
     * Adds the block's hash, time and the position to a lookup. Call it in
     * the same snapshot the lookup came from.
     */
    public TransactionView describe(Blockchain.TransactionLookup lookup) {
        return blockchain.atomically(() -> {
            Transaction tx = lookup.transaction();
            if (!lookup.isConfirmed()) return new TransactionView(tx, null, null, null, null, 0);
            Block block = blockchain.getBlock(lookup.blockIndex());
            List<Transaction> txs = block.getTransactions();
            // The same object first: two old coinbases can share an id (before Sprint 25b).
            int position = txs.indexOf(tx);
            for (int i = 0; i < txs.size() && position < 0; i++) {
                if (txs.get(i).getTransactionId().equals(tx.getTransactionId())) position = i;
            }
            return new TransactionView(tx, lookup.blockIndex(), block.getHash(), block.getTimestamp(),
                    position < 0 ? null : position, lookup.confirmations());
        });
    }

    // ===== ADDRESSES =====

    /**
     * What an address did, with one page of its history (pending first, then
     * confirmed from the newest block back).
     *
     * @param transactionCount confirmed transactions the address sent or received
     * @param pendingCount     its transactions still in the mempool
     * @param totalReceived    confirmed amounts paid to it
     * @param totalSent        confirmed amounts plus fees it paid
     */
    public record AddressView(String address, double balance, double pendingOutgoing,
                              int transactionCount, int pendingCount,
                              double totalReceived, double totalSent,
                              int offset, int limit, List<TransactionView> transactions) {}

    /**
     * THEORY: AN ADDRESS HAS NO RECORD OF ITS OWN
     *
     * The chain stores transactions, not accounts. "Received", "sent" and
     * "balance" are sums over every transaction that names the address:
     *
     *   received = amounts where it is the recipient
     *   sent     = amount + fee where it is the sender (the fee leaves too)
     *   balance  = received - sent
     *
     * An address that never appeared is not an error: it has zero of
     * everything, like an empty wallet.
     *
     * @param offset rows to skip, 0 or more
     * @param limit  1 to {@link #MAX_PAGE_SIZE}
     * @throws IllegalArgumentException for a limit out of range or a negative offset
     */
    public AddressView addressView(String address, int offset, int limit) {
        checkLimit(limit);
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
        return blockchain.atomically(() -> {
            List<Blockchain.TransactionLookup> history = blockchain.getTransactionHistory(address);
            int confirmed = 0;
            int pending = 0;
            double received = 0;
            double sent = 0;
            for (Blockchain.TransactionLookup l : history) {
                if (!l.isConfirmed()) {
                    pending++;
                    continue;
                }
                confirmed++;
                Transaction tx = l.transaction();
                if (address.equals(tx.getRecipient())) received += tx.getAmount();
                if (address.equals(tx.getSender())) sent += tx.getTotalCost();
            }
            List<TransactionView> page = new ArrayList<>();
            for (int i = offset; i < history.size() && page.size() < limit; i++) {
                page.add(describe(history.get(i)));
            }
            return new AddressView(address, blockchain.getBalance(address), blockchain.getPendingOutgoing(address),
                    confirmed, pending, received, sent, offset, limit, page);
        });
    }

    // ===== SEARCH =====

    /** What a search found. Only the fields of its {@code type} are set. */
    public record SearchResult(String type, Integer blockIndex, String blockHash,
                               String transactionId, String address, String contractId) {
        public static final String BLOCK = "block";
        public static final String TRANSACTION = "transaction";
        public static final String CONTRACT = "contract";
        public static final String ADDRESS = "address";
    }

    /**
     * THEORY: ONE BOX, FOUR KINDS OF ANSWER
     *
     * A reader pastes whatever they have. The string itself says little: a
     * block hash and a transaction id are both 64 hex characters. So the
     * search asks the chain, from the most specific question to the least:
     *
     *   1. a number (or #number)  -> the block at that height
     *   2. a block hash
     *   3. a transaction id (pending or confirmed)
     *   4. a contract id, or its CONTRACT:... address
     *   5. an address that sent or received anything
     *
     * It answers with WHAT to open, not with the thing itself: the page for
     * a block and the page reached by search then show the same data.
     *
     * @return the match, or null if nothing on the active chain matches
     * @throws IllegalArgumentException for an empty query
     */
    public SearchResult search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) throw new IllegalArgumentException("Nothing to search for");
        return blockchain.atomically(() -> {
            String number = q.startsWith("#") ? q.substring(1) : q;
            if (number.matches("\\d{1,9}")) {
                int index = Integer.parseInt(number);
                if (index < blockchain.getChainSize()) {
                    return new SearchResult(SearchResult.BLOCK, index, blockchain.getBlock(index).getHash(), null, null, null);
                }
            }
            // Hashes and ids are lower-case hex; accept a pasted upper-case one.
            String hex = q.matches("[0-9a-fA-F]{64}") ? q.toLowerCase() : q;
            Block block = blockchain.getBlockByHash(hex);
            if (block != null) {
                return new SearchResult(SearchResult.BLOCK, block.getIndex(), block.getHash(), null, null, null);
            }
            Blockchain.TransactionLookup tx = blockchain.findTransaction(hex);
            if (tx != null) {
                return new SearchResult(SearchResult.TRANSACTION, tx.blockIndex(), null,
                        tx.transaction().getTransactionId(), null, null);
            }
            String prefix = BlockchainConfig.CONTRACT_ADDRESS_PREFIX;
            Contract contract = blockchain.getContract(q.startsWith(prefix) ? q.substring(prefix.length()) : q);
            if (contract != null) {
                return new SearchResult(SearchResult.CONTRACT, null, null, null,
                        contract.getAddress(), contract.getContractId());
            }
            if (!blockchain.getTransactionHistory(q).isEmpty()) {
                return new SearchResult(SearchResult.ADDRESS, null, null, null, q, null);
            }
            return null;
        });
    }

    // ===== TOTALS =====

    /**
     * The chain in numbers.
     *
     * @param coinsIssued every coin in existence: what the coinbases created
     *                    minus the fees they only passed on
     * @param chainBytes  size of all blocks as peers exchange them
     */
    public record ChainStats(int height, int blocks, long transactions, double coinsIssued,
                             double totalFees, long chainBytes, long tipTimestamp,
                             int pendingTransactions, int contracts, int openContracts) {}

    /** Sums over blocks 0..count-1. */
    private record Totals(int count, long transactions, double coinbase, double fees, long bytes) {}

    /**
     * THEORY: HOW MANY COINS EXIST
     *
     * Coins are created in one place only: the coinbase. But a coinbase pays
     * reward + fees, and the fees are not new: their senders already had
     * them. So
     *
     *   coins issued = sum of coinbase amounts - sum of fees
     *
     * which is also the sum of every balance (contracts included: a locked
     * coin still exists).
     *
     * Counting means reading every block, so the sums are kept and only the
     * blocks added since the last call are read. After a reorg the remembered
     * tip is no longer on the chain, and the count starts again from genesis.
     */
    public ChainStats stats() {
        return blockchain.atomically(() -> {
            int size = blockchain.getChainSize();
            Block tip = blockchain.getLatestBlock();
            if (!tip.getHash().equals(totalsTip)) {
                boolean grown = totals != null && totals.count() <= size
                        && blockchain.getBlock(totals.count() - 1).getHash().equals(totalsTip);
                Totals t = grown ? totals : new Totals(0, 0, 0, 0, 0);
                long transactions = t.transactions();
                double coinbase = t.coinbase();
                double fees = t.fees();
                long bytes = t.bytes();
                for (int i = t.count(); i < size; i++) {
                    BlockSummary s = summarize(blockchain.getBlock(i));
                    transactions += s.transactionCount();
                    coinbase += s.reward();
                    fees += s.totalFees();
                    bytes += s.sizeBytes();
                }
                totals = new Totals(size, transactions, coinbase, fees, bytes);
                totalsTip = tip.getHash();
            }
            List<Contract> contracts = blockchain.getContracts();
            int open = 0;
            for (Contract c : contracts) {
                if (c.getStatus() == ContractStatus.OPEN) open++;
            }
            return new ChainStats(size - 1, size, totals.transactions(), round8(totals.coinbase() - totals.fees()),
                    round8(totals.fees()), totals.bytes(), tip.getTimestamp(),
                    blockchain.getPendingCount(), contracts.size(), open);
        });
    }

    /** A long sum of doubles collects noise in its last digits (0.1 + 0.2 = 0.30000000000000004). */
    private static double round8(double x) {
        return Math.round(x * 1e8) / 1e8;
    }

    private static void checkLimit(int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
