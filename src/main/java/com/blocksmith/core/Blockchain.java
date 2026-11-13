package com.blocksmith.core;

import com.blocksmith.contract.Contract;
import com.blocksmith.contract.ContractStatus;
import com.blocksmith.contract.ScriptVM;
import com.blocksmith.storage.ChainStore;
import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.HashUtil;
import com.blocksmith.util.JsonUtil;
import com.blocksmith.util.Target;

import java.math.BigInteger;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Manages the blockchain - a linked list of blocks.
 * 
 * THEORY: A blockchain is a linked list of blocks where each block
 * contains the hash of the previous block, creating a chain.
 * 
 * STRUCTURE:
 * [Genesis] -> [Block 1] -> [Block 2] -> ... -> [Block N]
 *    ↑            ↑            ↑                    ↑
 * prevHash=0   prevHash=    prevHash=           prevHash=
 *              genesis.hash  block1.hash        blockN-1.hash
 * 
 * TRANSACTION FLOW:
 * 1. User creates a transaction
 * 2. Transaction is added to the PENDING POOL
 * 3. Miner collects pending transactions
 * 4. Miner creates a new block with those transactions
 * 5. Miner mines the block (Proof-of-Work)
 * 6. Block is added to chain, transactions are confirmed
 * 
 * MINING REWARD:
 * When a miner successfully mines a block, they receive a reward.
 * This is implemented as a special "coinbase" transaction from
 * the system to the miner's address. 
 */
public class Blockchain {

    private final List<Block> chain;
    private final List<Transaction> pendingTransactions;

    /**
     * Where accepted blocks and pending transactions are written (Sprint 16).
     * {@link NoOpChainStore} by default, so a plain {@code new Blockchain()}
     * stays purely in memory.
     */
    private final ChainStore store;

    /**
     * The genesis block is fixed (same timestamp, nonce and hash on every
     * node), mined once at 4 leading zeros whatever the network's params.
     */
    private static final BigInteger GENESIS_TARGET =
            Target.fromLeadingZeros(BlockchainConfig.MINING_DIFFICULTY);

    /** The network's consensus rules (block time, retarget window...). */
    private final ChainParams params;

    /** This node's clock: for the future-time rule and for mining (Sprint 17c). */
    private final Clock clock;

    /** Room left for the nonce to grow from "0" to 10 digits while mining (Sprint 18b). */
    private static final int NONCE_DIGITS_MARGIN = 9;

    /** Room for the coinbase amount to grow from "50.0" to reward + fees with decimals (18c). */
    private static final int COINBASE_AMOUNT_MARGIN = 24;

    /** How many previous blocks the median-time-past looks at (as in Bitcoin). */
    static final int MEDIAN_TIME_SPAN = 11;

    /**
     * The Proof-of-Work target in effect for each height (Sprint 17b),
     * parallel to {@link #chain}. Never stored: always recomputed from the
     * blocks' timestamps, so a restore or a peer derives the same values.
     */
    private final List<BigInteger> targets = new ArrayList<>();

    /**
     * THEORY: CONTRACT REGISTRY (Sprint 14)
     *
     * Contract state is DERIVED from the chain, never stored independently:
     * every appended block is scanned for deploy transactions (recipient =
     * CONTRACT:<id>, carrying a locking script) and claim transactions
     * (sender = CONTRACT:<id>). Since all nodes replay the same blocks, all
     * nodes derive the same registry - like balances, contract state is a
     * view over the chain.
     */
    private final Map<String, Contract> contracts = new LinkedHashMap<>();

    /** Evaluates contract scripts; never throws (consensus safety). */
    private final ScriptVM scriptVM = new ScriptVM();

    /** Ids of every transaction already inside a block (Sprint 18a). */
    private final Set<String> confirmedTxIds = new HashSet<>();

    /**
     * Largest number of orphan blocks held at once. Caps memory if a peer
     * floods us with forward blocks whose parents never arrive.
     */
    static final int MAX_ORPHANS = 50;

    /**
     * THEORY: ORPHAN BLOCKS
     *
     * Over a gossip network a block can arrive before its parent. Such a
     * block is an "orphan" - valid in isolation but not yet connectable to
     * our chain. We park it here, keyed by the previousHash it is waiting
     * for, and attach it once that parent block is appended.
     *
     * A LinkedHashMap in access-order eviction mode drops the oldest orphan
     * when the buffer is full, bounding memory.
     */
    private final Map<String, Block> orphans =
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Block> eldest) {
                    return size() > MAX_ORPHANS;
                }
            };

    /**
     * Creates a new blockchain with the Genesis block.
     * 
     * THEORY: Genesis Block is the first block in any blockchain.
     * 
     * SPECIAL PROPERTIES:
     * - index = 0 (first block)
     * - previousHash = "0" (no previous block exists)
     * - Usually hardcoded or has special data
     * 
     * BITCOIN'S GENESIS: Contains the message:
     * "The Times 03/Jan/2009 Chancellor on brink of second bailout for banks"
     * This proves the block couldn't have been created before that date.
     */
    public Blockchain() {
        this(NoOpChainStore.INSTANCE);
    }

    /**
     * Creates a blockchain that writes every accepted block and pending
     * transaction to {@code store}. The chain starts at genesis; call
     * {@link #restoreFromStore()} to load what the store already holds.
     *
     * @param store durable storage for the chain and mempool
     */
    public Blockchain(ChainStore store) {
        this(store, ChainParams.DEFAULT);
    }

    /**
     * Creates a blockchain with explicit consensus parameters (Sprint 17b).
     * Every node of one network must use the same params.
     *
     * @param store durable storage for the chain and mempool
     * @param params consensus rules (target block time, retarget window...)
     */
    public Blockchain(ChainStore store, ChainParams params) {
        this(store, params, Clock.systemUTC());
    }

    /**
     * Creates a blockchain with an explicit clock (Sprint 17c), so tests can
     * pin "now" when checking timestamp rules.
     */
    public Blockchain(ChainStore store, ChainParams params, Clock clock) {
        this.chain = new ArrayList<>();
        this.pendingTransactions = new ArrayList<>();
        this.store = store;
        this.params = params;
        this.clock = clock;

        // Create and mine the Genesis block (deterministic: fixed timestamp)
        Block genesis = Block.createGenesisBlock();
        genesis.mineBlock(GENESIS_TARGET);
        chain.add(genesis);
        targets.add(params.initialTarget());
        store.saveBlock(genesis);
    }

    // ===== DIFFICULTY (Sprint 17b) =====

    /** The consensus params this chain runs with. */
    public ChainParams getChainParams() {
        return params;
    }

    /** The target the next block (tip + 1) must meet. */
    public BigInteger getNextTarget() {
        int height = chain.size();
        return targetAt(height, chain, targets.get(height - 1));
    }

    /** The target that was in effect for the block at {@code height}. */
    public BigInteger getTargetAt(int height) {
        return targets.get(height);
    }

    // ===== TIMESTAMPS (Sprint 17c) =====

    /**
     * Median timestamp of the (up to) 11 blocks before {@code height}.
     *
     * THEORY: Why the median, not the previous block
     *
     * Clocks differ, so block timestamps are allowed to wobble: block N+1
     * may even carry a slightly earlier time than block N. What must not
     * happen is time drifting backwards for real. The median of the last 11
     * blocks is a time the network has already moved past, and one dishonest
     * timestamp cannot drag it around (it would need 6 of the 11).
     *
     *   rule 1: timestamp > median of the previous 11 blocks
     *   rule 2: timestamp <= this node's clock + maxFutureDrift
     *
     * Together they bound what a miner can fake: no stamping far into the
     * future (makes a window look slow), and no backdating beyond the
     * median. The retarget formula is only as honest as these timestamps.
     */
    public long medianTimePast(int height) {
        int from = Math.max(0, height - MEDIAN_TIME_SPAN);
        List<Long> times = new ArrayList<>();
        for (int i = from; i < height; i++) times.add(chain.get(i).getTimestamp());
        Collections.sort(times);
        return times.get(times.size() / 2);
    }

    /** Rule 1: strictly after the median time of the blocks before it. */
    private boolean isAfterMedianTimePast(Block block, int height) {
        return block.getTimestamp() > medianTimePast(height);
    }

    /** Rule 2: not further ahead of our clock than the network allows. */
    private boolean isNotTooFarInFuture(Block block) {
        return block.getTimestamp() <= clock.millis() + params.maxFutureDriftMs();
    }

    /**
     * Timestamp for a block we mine at {@code height}: now, but never at or
     * before the median-time-past (peers' clocks may run a little ahead of
     * ours, which can push the median past our "now").
     */
    private long nextBlockTimestamp(int height) {
        return Math.max(clock.millis(), medianTimePast(height) + 1);
    }

    /**
     * The target for the block at {@code height}, given the blocks before it
     * and the target in effect at {@code height - 1}.
     *
     * THEORY: Retargeting (how Bitcoin keeps blocks ~10 minutes apart)
     *
     * Every {@code retargetInterval} blocks the network looks back and asks:
     * "did that window take as long as it should have?"
     *
     *   expected = intervals * targetBlockTime
     *   actual   = timestamp(last block) - timestamp(first block)
     *   new target = old target * actual / expected
     *
     *   2x too fast -> target halves (2x harder)
     *   2x too slow -> target doubles (2x easier)
     *
     * Guard rails:
     * - actual is clamped to [expected/4, expected*4], so one window moves
     *   difficulty at most 4x (a burst of odd timestamps can't swing it wildly)
     * - the target never gets easier than powLimit
     * - the window never starts at genesis: its timestamp is fixed (2024),
     *   so it would always look "years too slow"
     * - the window spans the FULL interval, including the gap between the
     *   previous window's last block and this one's first. Bitcoin measures
     *   2015 gaps for a 2016-block window, a famous off-by-one; we don't.
     *
     * Pure function of earlier blocks: every node, and every restore replay,
     * computes the same target. Nothing is stored, nothing can be forged.
     */
    private BigInteger targetAt(int height, List<Block> blocks, BigInteger previousTarget) {
        Window w = retargetWindow(height, blocks);
        if (w == null) return previousTarget;

        long factor = params.maxAdjustmentFactor();
        long actual = Math.max(w.expectedMs() / factor, Math.min(w.expectedMs() * factor, w.actualMs()));

        BigInteger next = previousTarget.multiply(BigInteger.valueOf(actual))
                .divide(BigInteger.valueOf(w.expectedMs()));
        if (next.compareTo(params.powLimit()) > 0) next = params.powLimit();
        if (next.signum() <= 0) next = BigInteger.ONE;
        return next;
    }

    /** The blocks a retarget at {@code height} measures, and how long they took. */
    private record Window(int first, int last, long actualMs, long expectedMs) {}

    /** The retarget window for {@code height}, or null if no retarget happens there. */
    private Window retargetWindow(int height, List<Block> blocks) {
        int interval = params.retargetInterval();
        if (height < interval || height % interval != 0) return null;

        int first = Math.max(height - interval - 1, 1);
        int last = height - 1;
        int gaps = last - first;
        if (gaps <= 0) return null;

        long expected = gaps * params.targetBlockTimeMs();
        long actual = blocks.get(last).getTimestamp() - blocks.get(first).getTimestamp();
        return new Window(first, last, actual, expected);
    }

    // ===== DIFFICULTY REPORTING (Sprint 17d) =====

    /**
     * One difficulty "epoch": the blocks from {@code startHeight} until the
     * next retarget all share {@code target}. For every epoch after the
     * first, the window that produced it is included (raw, before the 4x clamp).
     *
     * @param startHeight        first height using this target
     * @param target             the target for the epoch
     * @param windowFirst        first block measured (null for the initial epoch)
     * @param windowLast         last block measured (null for the initial epoch)
     * @param actualTimespanMs   how long the window really took (null for the initial epoch)
     * @param expectedTimespanMs how long it should have taken (null for the initial epoch)
     */
    public record DifficultyEpoch(int startHeight, BigInteger target, Integer windowFirst,
                                  Integer windowLast, Long actualTimespanMs, Long expectedTimespanMs) {}

    /**
     * Every difficulty epoch so far, oldest first, including the one the
     * next block (tip + 1) belongs to.
     */
    public List<DifficultyEpoch> getDifficultyHistory() {
        List<DifficultyEpoch> history = new ArrayList<>();
        history.add(new DifficultyEpoch(0, targets.get(0), null, null, null, null));
        int interval = params.retargetInterval();
        for (int h = interval; h <= chain.size(); h += interval) {
            Window w = retargetWindow(h, chain);
            BigInteger target = h < chain.size() ? targets.get(h) : getNextTarget();
            history.add(w == null
                    ? new DifficultyEpoch(h, target, null, null, null, null)
                    : new DifficultyEpoch(h, target, w.first(), w.last(), w.actualMs(), w.expectedMs()));
        }
        return history;
    }

    /**
     * How many more blocks will be mined at the current next-block target
     * before it changes. Counts the next block itself.
     */
    public int getBlocksUntilRetarget() {
        int next = chain.size();                       // height of the next block
        int interval = params.retargetInterval();
        int boundary = (next / interval + 1) * interval;
        return boundary - next;
    }

    /**
     * Average time between the recent blocks (up to one retarget window,
     * genesis excluded), or null if there are fewer than two such blocks.
     */
    public Long getAverageBlockTimeMs() {
        int last = chain.size() - 1;
        int first = Math.max(1, last - params.retargetInterval());
        if (last - first < 1) return null;
        return (chain.get(last).getTimestamp() - chain.get(first).getTimestamp()) / (last - first);
    }

    /**
     * Outcome of {@link #restoreFromStore()}.
     *
     * @param blocksRestored stored blocks (after genesis) accepted onto the chain
     * @param blocksRejected stored blocks not restored (first invalid one and all after it)
     * @param pendingRestored stored pending transactions accepted back into the mempool
     */
    public record RestoreReport(int blocksRestored, int blocksRejected, int pendingRestored) {}

    /**
     * Rebuilds this chain and mempool from the store.
     *
     * THEORY: Restore by replay, never by trust
     *
     * The database is just data. Each stored block goes through the same
     * addBlock(Block) checks as a block from an untrusted peer: correct
     * height, links to our tip, hash matches its content, meets the
     * Proof-of-Work target. Balances and the contract registry are rebuilt
     * as a side effect, exactly as on any other node.
     *
     *   stored:   #0  #1  #2  #3*  #4      (* = edited by hand on disk)
     *   replay:   ok  ok  ok  FAIL stop -> chain = #0..#2
     *
     * We stop at the first failure rather than skipping it: every later
     * block links to the bad one, so none of them can be valid either.
     * Pending transactions re-enter through addTransaction, so the balance
     * rules still apply; ones that no longer fit are dropped from the store.
     *
     * Must be called on a fresh chain (genesis only), before the node starts.
     *
     * @return how many blocks and pending transactions were restored
     * @throws IllegalStateException if the chain already has blocks past genesis
     */
    public RestoreReport restoreFromStore() {
        if (chain.size() > 1) {
            throw new IllegalStateException("restoreFromStore() needs a fresh chain");
        }

        List<Block> stored = store.loadBlocks();
        int restored = 0;
        int candidates = 0;
        boolean stopped = false;
        for (Block block : stored) {
            if (block.getIndex() == 0) {
                // A different genesis means a different network: restore nothing.
                if (!block.getHash().equals(chain.get(0).getHash())) {
                    System.out.println("Restore: stored genesis does not match ours, ignoring store");
                    return new RestoreReport(0, stored.size() - 1, 0);
                }
                continue;
            }
            candidates++;
            if (stopped) continue;
            // Only accept the next height; anything else (a gap in the rows)
            // would be parked as an orphan by addBlock, which we do not want.
            if (block.getIndex() != getLatestBlock().getIndex() + 1 || !addBlock(block)) {
                System.out.println("Restore: stored block #" + block.getIndex()
                        + " failed validation, keeping chain up to #" + getLatestBlock().getIndex());
                stopped = true;
                continue;
            }
            restored++;
        }

        int pendingRestored = 0;
        for (Transaction tx : store.loadPending()) {
            // Already in a block (the node stopped between saving the block
            // and clearing pending): re-adding it would let it be mined twice.
            if (!isConfirmed(tx.getTransactionId()) && addTransaction(tx)) {
                pendingRestored++;
            } else {
                store.removePending(tx.getTransactionId());
            }
        }
        return new RestoreReport(restored, candidates - restored, pendingRestored);
    }

    /**
     * The single place a block joins the chain.
     *
     * Every path (local mining, legacy data blocks, blocks from peers,
     * attached orphans) goes through here, so none of them can forget a step:
     * confirm its transactions, update the contract registry, persist it.
     *
     * The block is written to the store FIRST. If the node dies right after,
     * the block is safe and its transactions may still be listed as pending;
     * restoreFromStore() skips those. The opposite order could lose a
     * transaction (removed from pending, block never written).
     */
    private void appendBlock(Block block) {
        BigInteger target = getNextTarget();
        store.saveBlock(block);
        chain.add(block);
        targets.add(target);
        for (Transaction tx : block.getTransactions()) confirmedTxIds.add(tx.getTransactionId());
        removeConfirmed(block);
        registerContracts(block);
    }

    /** True if a transaction with this id is already inside a block. */
    private boolean isConfirmed(String transactionId) {
        return confirmedTxIds.contains(transactionId);
    }

    // ===== BLOCK CONTENT RULES (Sprint 18a) =====

    /**
     * Running state while the transactions of one block are checked in order:
     * balance changes so far, ids seen, contracts already claimed.
     */
    private final class BlockCheck {
        final int height;
        final Map<String, Double> delta = new HashMap<>();
        final Set<String> ids = new HashSet<>();
        final Set<String> claimedContracts = new HashSet<>();

        BlockCheck(int height) {
            this.height = height;
        }

        /** Balance on the chain plus the effect of this block's earlier transactions. */
        double balance(String address) {
            return getBalance(address) + delta.getOrDefault(address, 0.0);
        }

        void apply(Transaction tx) {
            delta.merge(tx.getSender(), -tx.getTotalCost(), Double::sum);
            delta.merge(tx.getRecipient(), tx.getAmount(), Double::sum);
            ids.add(tx.getTransactionId());
            if (tx.getSender().startsWith(BlockchainConfig.CONTRACT_ADDRESS_PREFIX)) {
                claimedContracts.add(contractIdOf(tx.getSender()));
            }
        }
    }

    private static String contractIdOf(String contractAddress) {
        return contractAddress.substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length());
    }

    /**
     * Why this block's CONTENTS are not allowed, or null if they are.
     *
     * THEORY: Well built is not the same as allowed
     *
     * Proof-of-Work proves that work was spent on a block, not that the
     * block is honest. A miner with plenty of hash power could pay itself
     * a million coins or move someone else's money, and the hash, the PoW,
     * the Merkle root and the timestamps would all still check out. So
     * every node also replays what the block DOES, transaction by
     * transaction, against the chain as it stands:
     *
     *   1. if the block has transactions, the first is the coinbase, and it
     *      is the only one; it pays at most the block reward
     *   2. every other transaction is well formed, a signed one verifies,
     *      its id is new (not earlier in this block, not already on chain)
     *   3. its sender can afford it, counting what earlier transactions in
     *      the same block already moved
     *   4. contract deploys carry a locking script; contract claims satisfy
     *      the contract's script, and a contract is claimed at most once
     *
     * This is what makes Bitcoin nodes reject a miner who overpays itself,
     * however much hash power stands behind the block.
     *
     * The coinbase is exempt from "id already on chain": two rewards for the
     * same miner in the same millisecond have the same id (Bitcoin hit the
     * same quirk, BIP30/BIP34). In an account model that is harmless.
     */
    private String blockRuleViolation(Block block) {
        String tooBig = limitViolation(block);
        if (tooBig != null) return tooBig;

        List<Transaction> txs = block.getTransactions();
        if (txs.isEmpty()) return null;               // data-only block (legacy addBlock(String))

        Transaction coinbase = txs.get(0);
        if (!BlockchainConfig.COINBASE_ADDRESS.equals(coinbase.getSender())) {
            return "the first transaction is not the coinbase";
        }
        if (coinbase.getRecipient() == null || coinbase.getRecipient().isBlank()) {
            return "the coinbase has no recipient";
        }
        // The miner may claim the reward plus the fees of this block (18c),
        // summed in block order (the same order mining uses).
        double fees = 0;
        for (int i = 1; i < txs.size(); i++) fees += txs.get(i).getFee();
        if (coinbase.getAmount() <= 0 || coinbase.getAmount() > BlockchainConfig.MINING_REWARD + fees) {
            return "the coinbase pays " + coinbase.getAmount() + ", more than the reward "
                    + BlockchainConfig.MINING_REWARD + " + fees " + fees;
        }
        if (coinbase.getFee() != 0) return "the coinbase carries a fee";
        if (coinbase.getLockingScript() != null || coinbase.getUnlockingScript() != null) {
            return "the coinbase carries a contract script";
        }

        BlockCheck check = new BlockCheck(block.getIndex());
        check.apply(coinbase);
        for (int i = 1; i < txs.size(); i++) {
            Transaction tx = txs.get(i);
            String why = transactionRuleViolation(tx, check);
            if (why != null) return "transaction " + shortId(tx) + ": " + why;
            check.apply(tx);
        }
        return null;
    }

    /**
     * Why this block is over the network's size limits, or null (Sprint 18b).
     *
     * THEORY: Why blocks have a maximum size
     *
     * Every full node downloads, checks and stores every block. Without a
     * cap, one miner could publish a gigantic block that slower nodes cannot
     * keep up with, pushing them off the network (fewer independent nodes =
     * less decentralisation), or simply flood everyone's disk and bandwidth.
     * A cap also makes block space scarce, which is what gives fees meaning:
     * when more transactions wait than fit, the miner picks the best-paying
     * ones (Sprint 18c). Bitcoin's cap is ~4 MB of "weight" per block.
     */
    private String limitViolation(Block block) {
        int count = block.getTransactions().size();
        if (count > params.maxBlockTransactions()) {
            return count + " transactions, more than the limit of " + params.maxBlockTransactions();
        }
        int bytes = block.sizeBytes();
        if (bytes > params.maxBlockBytes()) {
            return bytes + " bytes, more than the limit of " + params.maxBlockBytes();
        }
        return null;
    }

    /** Why a non-coinbase transaction may not go into the block being checked, or null. */
    private String transactionRuleViolation(Transaction tx, BlockCheck check) {
        if (BlockchainConfig.COINBASE_ADDRESS.equals(tx.getSender())) return "a second coinbase";
        if (!tx.isValid()) return "malformed (amount, sender or recipient)";
        if ((tx.getSignature() != null || tx.getSenderPublicKey() != null) && !tx.verifySignature()) {
            return "the signature does not verify";
        }
        String id = tx.getTransactionId();
        if (check.ids.contains(id) || confirmedTxIds.contains(id)) return "already included";

        if (tx.getRecipient().startsWith(BlockchainConfig.CONTRACT_ADDRESS_PREFIX)
                && (tx.getLockingScript() == null || tx.getLockingScript().isBlank())) {
            return "a contract deploy without a locking script";
        }
        if (tx.getSender().startsWith(BlockchainConfig.CONTRACT_ADDRESS_PREFIX)) {
            if (check.claimedContracts.contains(contractIdOf(tx.getSender()))) {
                return "the contract is already claimed in this block";
            }
            if (!isValidClaim(tx, check.height)) return "the contract claim is not valid";
        }
        if (check.balance(tx.getSender()) < tx.getTotalCost()) {
            return "the sender cannot afford " + tx.getAmount() + " + fee " + tx.getFee();
        }
        return null;
    }

    private static String shortId(Transaction tx) {
        String id = tx.getTransactionId();
        return id == null ? "?" : id.substring(0, Math.min(8, id.length()));
    }
        
    /**
     * Returns the latest (most recent) block in the chain
     *   
     * @return The latest block added to the chain
     */
    public Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }

    /**
     * Adds a new block to the blockchain (Legacy method for string data).    
     * 
     * @param data The data to store in the new block
     * @return The newly created and mined block
     */ 
    public Block addBlock(String data) {
        Block latestBlock = getLatestBlock();
        int newIndex = latestBlock.getIndex() + 1;

        Block newBlock = new Block(newIndex, data, latestBlock.getHash(), nextBlockTimestamp(newIndex));
        newBlock.mineBlock(getNextTarget());

        appendBlock(newBlock);

        return newBlock;
    }

    /**
     * Appends a block mined elsewhere (e.g. received from a peer) after
     * validating it against the current chain tip.
     *
     * THEORY: ACCEPTING EXTERNAL BLOCKS
     *
     * Unlike addBlock(String), which mines a block locally, this method
     * takes an already-mined block and accepts it only if it extends our
     * chain cleanly. The block arrives from the untrusted network, so we
     * re-verify everything rather than trust the sender:
     *   1. Index is exactly tip + 1 (no gaps, no duplicates)
     *   2. previousHash links to our current tip
     *   3. The stored hash matches a fresh recomputation (integrity)
     *   4. The hash satisfies the Proof-of-Work target (it was really mined)
     *
     * Returns false instead of throwing: a bad block is a normal network
     * event, not a programming error.
     *
     * @param block A block mined elsewhere
     * @return true if the block was valid and appended, false otherwise
     */
    public boolean addBlock(Block block) {
        if (block == null) return false;

        Block tip = getLatestBlock();

        // Extends our current tip cleanly: it must meet the target the chain
        // expects at this height (Sprint 17b), then append and pull in any
        // orphans that were waiting on it.
        if (block.getIndex() == tip.getIndex() + 1
                && block.getPreviousHash().equals(tip.getHash())) {
            if (!isWellFormed(block, getNextTarget())) return false;
            if (!isAfterMedianTimePast(block, block.getIndex()) || !isNotTooFarInFuture(block)) return false;
            String why = blockRuleViolation(block);
            if (why != null) {
                System.out.println("Block #" + block.getIndex() + " rejected: " + why);
                return false;
            }
            appendBlock(block);
            attachOrphans();
            return true;
        }

        // Arrived ahead of its parent (index beyond tip+1): park as an orphan
        // until the missing parent shows up. Without its history we cannot
        // know its exact target yet, so for now it only has to meet the
        // easiest one allowed (cheap spam filter); attachOrphans checks the
        // real target. A malformed block is dropped outright.
        if (block.getIndex() > tip.getIndex() + 1 && isWellFormed(block, params.powLimit())
                && isNotTooFarInFuture(block) && limitViolation(block) == null) {
            orphans.put(block.getPreviousHash(), block);
        }

        return false;
    }

    /**
     * Checks a block is internally consistent: its transactions match its
     * Merkle root, its stored hash matches a fresh recomputation, and that
     * hash meets {@code target}. Says nothing about how it links to our chain.
     */
    private boolean isWellFormed(Block block, BigInteger target) {
        // The transactions must match the Merkle root the hash commits to;
        // otherwise an edited block could keep a valid-looking hash (#260).
        if (!block.hasValidMerkleRoot()) return false;
        if (!block.getHash().equals(block.calculateHash())) return false;
        return Target.meets(block.getHash(), target);
    }

    /**
     * Drains the orphan buffer onto the chain: while an orphan is waiting on
     * the current tip's hash, append it and advance. A run of consecutive
     * out-of-order blocks resolves in a single pass.
     */
    private void attachOrphans() {
        while (true) {
            Block tip = getLatestBlock();
            Block child = orphans.get(tip.getHash());
            if (child == null) break;

            orphans.remove(tip.getHash());
            if (child.getIndex() == tip.getIndex() + 1 && isWellFormed(child, getNextTarget())
                    && isAfterMedianTimePast(child, child.getIndex()) && isNotTooFarInFuture(child)
                    && blockRuleViolation(child) == null) {
                appendBlock(child);
            } else {
                break; // stale/ill-fitting orphan: drop and stop
            }
        }
    }

    /**
     * Removes a newly-appended block's transactions from the pending pool.
     *
     * A transaction gossiped to us earlier may still be sitting in our mempool
     * when the block that confirms it arrives from a peer. Without this it
     * would linger and be re-gossiped, or even mined again into a later block.
     * Runs for every appended block (see appendBlock): for a locally mined
     * block it clears exactly the transactions that went into it, and the
     * store forgets them too.
     */
    private void removeConfirmed(Block block) {
        if (block.getTransactions().isEmpty()) return;

        Set<String> confirmedIds = new HashSet<>();
        for (Transaction tx : block.getTransactions()) {
            confirmedIds.add(tx.getTransactionId());
        }
        pendingTransactions.removeIf(tx -> {
            if (!confirmedIds.contains(tx.getTransactionId())) return false;
            store.removePending(tx.getTransactionId());
            return true;
        });
    }

    /**
     * @return number of blocks currently held in the orphan buffer
     */
    public int getOrphanCount() {
        return orphans.size();
    }

    /**
     * Adds a transaction to the pending pool.
     * 
     * THEORY: MEMPOOL (Memory Pool)
     * 
     * In real blockchains, pending transactions wait in a "mempool"
     * until a miner includes them in a block. Miners typically
     * prioritize transactions wiht higher fees.
     * 
     * VALIDATION: 
     * 1. Transaction must be valid (amount > 0, non-empty addresses)
     * 2. COINBASE transactions are rejected (only created by mining)
     * 3. Sender must have sufficient balance
     * 
     * BALANCE CHECK:
     * We check both confirmed balance (in blockchain) and pending
     * outgoind transactions to prevent double-spending.
     * 
     * @param transaction The transaction to add
     * @return true if transaction was added, false if invalid
     */
    public boolean addTransaction(Transaction transaction) {
        // Validate the transaction
        if (transaction == null || !transaction.isValid()) {
            System.out.println("Transaction rejected: Invalid transaction");
            return false;
        }

        // Reject COINBASE transactions (only mining creates these)
        if (transaction.getSender().equals(BlockchainConfig.COINBASE_ADDRESS)) {
            System.out.println("Transaction rejected: Cannot manually create COINBASE transactions");
            return false;
        }

        // Contract rules (Sprint 14). A transfer TO a contract address must
        // carry a locking script (otherwise the funds would be burned); a
        // transfer FROM one is a claim and must satisfy the contract's script.
        if (transaction.getRecipient().startsWith(BlockchainConfig.CONTRACT_ADDRESS_PREFIX)
                && (transaction.getLockingScript() == null
                    || transaction.getLockingScript().isBlank())) {
            System.out.println("Transaction rejected: Contract deploy requires a locking script");
            return false;
        }
        if (transaction.getSender().startsWith(BlockchainConfig.CONTRACT_ADDRESS_PREFIX)
                && !isValidClaim(transaction)) {
            return false;
        }

        // A signed transaction must verify (Sprint 18a: blocks enforce the
        // same rule, so a bad one would make our own mined block invalid).
        if ((transaction.getSignature() != null || transaction.getSenderPublicKey() != null)
                && !transaction.verifySignature()) {
            System.out.println("Transaction rejected: Signature does not verify");
            return false;
        }

        // Reject duplicates already waiting in the pool. This also stops a
        // gossiped transaction from being relayed twice (returns false).
        String txId = transaction.getTransactionId();
        if (confirmedTxIds.contains(txId)) {
            return false;                              // already in a block: replaying it is not allowed
        }
        for (Transaction pending : pendingTransactions) {
            if (pending.getTransactionId().equals(txId)) {
                return false;
            }
        }

        // Check sender has sufficient balance
        double senderBalance = getBalance(transaction.getSender());
        double pendingOutgoing = getPendingOutgoing(transaction.getSender());
        double availableBalance = senderBalance - pendingOutgoing;

        if (availableBalance < transaction.getTotalCost()) {
            System.out.println("Transacion rejected: Insufficient funds. " + 
                "Available: " + availableBalance + " " + BlockchainConfig.CURRENCY_SYMBOL + 
                ", Required: " + transaction.getAmount() + " " + BlockchainConfig.CURRENCY_SYMBOL);
                return false;
        }

        // Add to pending pool (and remember it across restarts)
        pendingTransactions.add(transaction);
        store.savePending(transaction);
        System.out.println("Transaction added to pending pool: " + transaction);
        return true;
    }

    /**
     * Calculates total outgoing amount in pending transactions for an address
     * 
     * THEORY: PENDING BALANCE
     * 
     * When checking if someone can afford a transaction, we must also
     * consider funds they've already "promised" in pending transactions.
     * Otherwise, they could submit multiple transactions thath together
     * exceed their balance.
     * 
     * @param address Ther address to check
     * @return Total amount being sent in pending transactions
     */
    private double getPendingOutgoing(String address) {
        double pending = 0;
         for (Transaction tx : pendingTransactions) {
            if (tx.getSender().equals(address)) pending += tx.getTotalCost();
         }
         return pending;
    }

    /**
     * Mines all pending transactions into a new block.
     * 
     * THEORY: MINING PROCESS
     * 
     * 1. Collect pending transactions
     * 2. Add a COINBASE transaction (mining reward)
     * 3. Create a new block with all transactions
     * 4. Mine the block (find valid nonce)
     * 5. Add block to chain
     * 6. Clear pending transactions
     * 
     * COINBASE TRANSACTION:
     * A special transaction with no sender (from "COINBASE")
     * that rewards the miner. This is how new coins are created.
     * 
     * @param minerAddress Address to receive mining reward
     * @return The newly mined block, or null if no transactions
     */
    public Block minePendingTransactions(String minerAddress) {
        Block latestBlock = getLatestBlock();
        int newIndex = latestBlock.getIndex() + 1;

        // The coinbase is the first transaction, but its amount (reward +
        // fees, Sprint 18c) depends on what gets selected. So selection runs
        // against a reward-only placeholder, and the real coinbase is built
        // afterwards. The placeholder pays less, so anything valid with it is
        // still valid with the real one.
        Transaction placeholder = new Transaction(
                BlockchainConfig.COINBASE_ADDRESS, minerAddress, BlockchainConfig.MINING_REWARD);
        BlockCheck check = new BlockCheck(newIndex);
        check.apply(placeholder);
        List<Transaction> selected = new ArrayList<>();

        // Block limits (Sprint 18b): start from the size of a block holding
        // only the coinbase and add each transaction's JSON plus a comma.
        // Keep room for the nonce to grow to 10 digits and for the coinbase
        // amount to gain the fees' decimals. What does not fit stays pending.
        int bytes = new Block(newIndex, List.of(placeholder), latestBlock.getHash(), 0L).sizeBytes()
                + NONCE_DIGITS_MARGIN + COINBASE_AMOUNT_MARGIN;

        // Fee priority (Sprint 18c): best fee per byte first. List.sort is
        // stable, so equal rates (e.g. all zero) keep arrival order. Any
        // order is safe: every pending tx was admitted against confirmed
        // balances only, so it never depends on another pending tx.
        // A pending tx can also go stale (Sprint 18a), e.g. a block from a
        // peer already spent the same funds: it is dropped, not mined.
        for (Transaction tx : byFeeRate(pendingTransactions)) {
            if (selected.size() + 1 >= params.maxBlockTransactions()) break;
            int txBytes = tx.sizeBytes() + 1;
            if (bytes + txBytes > params.maxBlockBytes()) continue;   // a smaller one may still fit
            String why = transactionRuleViolation(tx, check);
            if (why == null) {
                selected.add(tx);
                check.apply(tx);
                bytes += txBytes;
            } else {
                pendingTransactions.remove(tx);
                store.removePending(tx.getTransactionId());
                System.out.println("Dropped stale pending transaction " + shortId(tx) + ": " + why);
            }
        }

        // The miner collects the block reward plus every fee in the block.
        double fees = 0;
        for (Transaction tx : selected) fees += tx.getFee();
        Transaction rewardTx = new Transaction(
                BlockchainConfig.COINBASE_ADDRESS, minerAddress, BlockchainConfig.MINING_REWARD + fees);
        List<Transaction> blockTransactions = new ArrayList<>();
        blockTransactions.add(rewardTx);
        blockTransactions.addAll(selected);

        // Create and mine the new block
        Block newBlock = new Block(newIndex, blockTransactions, latestBlock.getHash(),
                nextBlockTimestamp(newIndex));

        System.out.println("\n⛏️  Mining block #" + newIndex + " with " + blockTransactions.size() + " transactions...");
        newBlock.mineBlock(getNextTarget());

        // Our own block must pass the same rules as anyone else's.
        String why = blockRuleViolation(newBlock);
        if (why != null) throw new IllegalStateException("Mined an invalid block: " + why);

        // Add block to chain. appendBlock also removes the block's
        // transactions from the pending pool (they're now confirmed).
        appendBlock(newBlock);

        System.out.println("✅ Block mined and added to chain!");
        System.out.println("   Miner " + minerAddress + " received " +
            rewardTx.getAmount() + " " + BlockchainConfig.CURRENCY_SYMBOL + " (reward + fees)");

        return newBlock;
    }

    /** Pending transactions, best fee per byte first; ties keep arrival order. */
    private static List<Transaction> byFeeRate(List<Transaction> pool) {
        List<Transaction> sorted = new ArrayList<>(pool);
        sorted.sort(java.util.Comparator.comparingDouble(Transaction::feeRate).reversed());
        return sorted;
    }

    /**
     * Calculates the balance of an address.
     * 
     * THEORY: ACCOUNT BALANCE
     * 
     * In our simple model, balance = incoming - outgoing.
     * We scan ALL transactions in ALL blocks to calculate this.
     * 
     * BITCOIN uses UTXO (Unspent Transaction Outputs) instead,
     * which is more efficient for large chains but more complex.
     * 
     * @param address The addres to check
     * @return The current balance
     */
    public double getBalance(String address) {
        double balance = 0;

        // Scan all blocks
        for (Block block : chain) {
            // scan all transactions in block
            for (Transaction tx : block.getTransactions()) {
                // if address is sender, substract amount
                // the sender pays the amount plus the fee (Sprint 18c)
                if (tx.getSender().equals(address)) balance -= tx.getTotalCost();

                // if address is recipient, add amount
                if (tx.getRecipient().equals(address)) balance += tx.getAmount();                       
            }
        }
        return balance;
    }

    // ===== CONTRACTS (Sprint 14) =====

    /**
     * Locks {@code amount} of the funder's balance behind a script.
     *
     * THEORY: DEPLOY = A TRANSFER TO A CONDITION
     *
     * The deploy is an ordinary transaction whose recipient is the contract's
     * address (CONTRACT:<id>) and which carries the locking script. It goes
     * through the pending pool and into a block like any other transfer, so
     * the funder's balance check, gossip, and mining need no special cases.
     * The contract becomes OPEN (claimable) once the deploy is mined.
     *
     * @param funder Address funding the contract
     * @param lockingScript Script that guards the funds (e.g. a hashlock)
     * @param amount Amount to lock
     * @return The pending deploy transaction, or null if rejected
     */
    public Transaction deployContract(String funder, String lockingScript, double amount) {
        if (funder == null || funder.isBlank()
                || lockingScript == null || lockingScript.isBlank()) {
            System.out.println("Contract rejected: funder and locking script are required");
            return null;
        }

        // The contract id seeds the contract's address; hashing the deploy
        // parameters keeps it unique and lets every node derive the same id
        // from the transaction once it arrives in a block.
        String contractId = HashUtil.applySha256(
                funder + lockingScript + amount + System.currentTimeMillis());

        Transaction deploy = new Transaction(
                funder,
                BlockchainConfig.CONTRACT_ADDRESS_PREFIX + contractId,
                amount);
        deploy.setLockingScript(lockingScript);

        return addTransaction(deploy) ? deploy : null;
    }

    /**
     * Claims an OPEN contract by presenting unlocking data.
     *
     * The claim is a transaction FROM the contract's address TO the claimer.
     * addTransaction() runs the unlocking data and the locking script through
     * the VM; the funds move once the claim is mined. The pending-outgoing
     * balance check doubles as double-claim protection: a second claim on the
     * same contract finds no available funds.
     *
     * @param contractId Id of the contract to claim
     * @param claimer Address to credit
     * @param unlockingScript Data satisfying the locking script (may be empty
     *                        for pure timelocks)
     * @return The pending claim transaction, or null if rejected
     */
    public Transaction claimContract(String contractId, String claimer, String unlockingScript) {
        Contract contract = contracts.get(contractId);
        if (contract == null || claimer == null || claimer.isBlank()) {
            System.out.println("Claim rejected: Unknown contract or missing claimer");
            return null;
        }

        Transaction claim = new Transaction(contract.getAddress(), claimer, contract.getAmount());
        claim.setUnlockingScript(unlockingScript);

        return addTransaction(claim) ? claim : null;
    }

    /**
     * Validates a claim transaction against the contract registry and the
     * script VM. Used by addTransaction for locally-created claims and for
     * claims gossiped by peers alike - every node re-verifies every claim.
     */
    private boolean isValidClaim(Transaction claim) {
        return isValidClaim(claim, chain.size());
    }

    /** Validates a claim as if mined at {@code height} (the VM's CHECKLOCKTIME height). */
    private boolean isValidClaim(Transaction claim, int height) {
        String contractId = claim.getSender()
                .substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length());

        Contract contract = contracts.get(contractId);
        if (contract == null) {
            System.out.println("Claim rejected: Unknown contract " + contractId);
            return false;
        }
        if (contract.getStatus() != ContractStatus.OPEN) {
            System.out.println("Claim rejected: Contract already claimed");
            return false;
        }
        if (claim.getAmount() != contract.getAmount()) {
            System.out.println("Claim rejected: Amount must equal the locked amount");
            return false;
        }

        // The claim height is the index the NEXT block will have - the
        // earliest height the claim could be mined at (CHECKLOCKTIME). The
        // sighash binds any signature in the unlocking data to THIS claim
        // (contract + claimer + amount), so a multisig CHECKMULTISIG can verify
        // it; hashlock/timelock scripts ignore the sighash.
        String sighash = contract.claimSighash(claim.getRecipient());
        if (!scriptVM.execute(claim.getUnlockingScript(), contract.getLockingScript(),
                height, sighash)) {
            System.out.println("Claim rejected: Script evaluated to false");
            return false;
        }
        return true;
    }

    /**
     * Scans an appended block for contract deploys and claims and updates the
     * registry. Called on every block-append path (local mine, external
     * append, orphan attach), so contract state always mirrors the chain.
     */
    private void registerContracts(Block block) {
        for (Transaction tx : block.getTransactions()) {
            String recipient = tx.getRecipient();
            if (recipient.startsWith(BlockchainConfig.CONTRACT_ADDRESS_PREFIX)
                    && tx.getLockingScript() != null && !tx.getLockingScript().isBlank()) {
                String id = recipient.substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length());
                contracts.putIfAbsent(id, new Contract(
                        id, tx.getSender(), tx.getLockingScript(),
                        tx.getAmount(), block.getIndex()));
            }

            String sender = tx.getSender();
            if (sender.startsWith(BlockchainConfig.CONTRACT_ADDRESS_PREFIX)) {
                Contract contract = contracts.get(
                        sender.substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length()));
                if (contract != null && contract.getStatus() == ContractStatus.OPEN) {
                    contract.markClaimed(tx.getRecipient());
                }
            }
        }
    }

    /**
     * Looks up a contract by id.
     *
     * @return The contract, or null if no deploy for this id has been mined
     */
    public Contract getContract(String contractId) {
        return contracts.get(contractId);
    }

    /**
     * @return Unmodifiable view of all contracts derived from the chain
     */
    public List<Contract> getContracts() {
        return Collections.unmodifiableList(new ArrayList<>(contracts.values()));
    }

    /**
     * Return the pending transaction pool.
     *
     * @return Unmodifiable list of pending transactions
     */
    public List<Transaction> getPendingTransactions() {
        return Collections.unmodifiableList(pendingTransactions);
    }

    /**
     * Returns the number of pending transactions.
     * 
     * @return Number of transactions waiting to be mined
     */
    public int getPendingCount() {
        return pendingTransactions.size();
    }

    /**
     * Writes an online backup of this chain's store to {@code zipFile}
     * (Sprint 16d). The node keeps running while it is taken.
     *
     * @throws UnsupportedOperationException if the store cannot be backed up
     *         (for example the default in-memory chain)
     */
    public void backupTo(java.nio.file.Path zipFile) {
        store.backup(zipFile);
    }

    // ===== LOOKUPS (Sprint 16c) =====

    /**
     * A transaction plus where it sits.
     *
     * @param transaction the transaction
     * @param blockIndex height of the block holding it, or null while pending
     * @param confirmations blocks on top of it, counting its own (0 = pending)
     */
    public record TransactionLookup(Transaction transaction, Integer blockIndex, int confirmations) {
        public boolean isConfirmed() {
            return blockIndex != null;
        }
    }

    /**
     * Finds a block by its hash.
     *
     * With an indexed store this is one database lookup; otherwise it scans
     * the chain. Either way the result is checked against the chain itself.
     *
     * @return the block, or null if no block on our chain has this hash
     */
    public Block getBlockByHash(String hash) {
        if (store.isIndexed()) {
            OptionalInt idx = store.findBlockIndexByHash(hash);
            if (idx.isEmpty() || idx.getAsInt() >= chain.size()) return null;
            Block block = chain.get(idx.getAsInt());
            return block.getHash().equals(hash) ? block : null;
        }
        for (Block block : chain) {
            if (block.getHash().equals(hash)) return block;
        }
        return null;
    }

    /**
     * Finds a transaction by id, pending or confirmed.
     *
     * @return the lookup, or null if the id is unknown
     */
    public TransactionLookup findTransaction(String transactionId) {
        for (Transaction tx : pendingTransactions) {
            if (tx.getTransactionId().equals(transactionId)) {
                return new TransactionLookup(tx, null, 0);
            }
        }
        if (store.isIndexed()) {
            OptionalInt idx = store.findTransactionBlockIndex(transactionId);
            return idx.isEmpty() ? null : lookupInBlock(transactionId, idx.getAsInt());
        }
        for (Block block : chain) {
            TransactionLookup found = lookupInBlock(transactionId, block.getIndex());
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Every transaction sent or received by {@code address}: pending ones
     * first (newest first), then confirmed ones from the newest block back.
     *
     * THEORY: Account history in an account-free chain
     *
     * The chain stores transactions, not accounts, so "my history" is a
     * search: every transaction where I am the sender or the recipient.
     * A scan reads the whole chain; the store's address index jumps straight
     * to the matching rows (see H2ChainStore). The balance shown by
     * getBalance() is just the sum over this same set.
     *
     * @return the address's transactions, newest first
     */
    public List<TransactionLookup> getTransactionHistory(String address) {
        List<TransactionLookup> history = new ArrayList<>();
        for (int i = pendingTransactions.size() - 1; i >= 0; i--) {
            Transaction tx = pendingTransactions.get(i);
            if (involves(tx, address)) history.add(new TransactionLookup(tx, null, 0));
        }

        if (store.isIndexed()) {
            for (ChainStore.TxRef ref : store.findTransactionsByAddress(address)) {
                TransactionLookup found = lookupInBlock(ref.transactionId(), ref.blockIndex());
                if (found != null) history.add(found); // stale index rows are skipped
            }
            return history;
        }

        for (int b = chain.size() - 1; b >= 0; b--) {
            List<Transaction> txs = chain.get(b).getTransactions();
            for (int t = txs.size() - 1; t >= 0; t--) {
                if (involves(txs.get(t), address)) {
                    history.add(new TransactionLookup(txs.get(t), b, confirmationsOf(b)));
                }
            }
        }
        return history;
    }

    private static boolean involves(Transaction tx, String address) {
        return address.equals(tx.getSender()) || address.equals(tx.getRecipient());
    }

    /** Number of blocks on top of height {@code blockIndex}, counting itself. */
    private int confirmationsOf(int blockIndex) {
        return getLatestBlock().getIndex() - blockIndex + 1;
    }

    /** Resolves a (tx id, height) hint against the chain; null if it is not really there. */
    private TransactionLookup lookupInBlock(String transactionId, int blockIndex) {
        if (blockIndex < 0 || blockIndex >= chain.size()) return null;
        for (Transaction tx : chain.get(blockIndex).getTransactions()) {
            if (tx.getTransactionId().equals(transactionId)) {
                return new TransactionLookup(tx, blockIndex, confirmationsOf(blockIndex));
            }
        }
        return null;
    }

    /**
     * Validates the entire blockchain integrity.
     * 
     * THEORY: Chain validation checks:
     * 1. Genesis block is valid (index=0, previousHash="0")
     * 2. Each block's hash matches its calculated hash
     * 3. Each block's previousHash matches previous block's hash
     * 4. Each block was properly mined (hash meets difficulty)
     * 
     * WHY THIS MATTERS: 
     * - Detects any tampering with block data
     * - Detects broken links between blocks
     * - Ensures Proof-of-Work was performed correctly
     * 
     * @return true if chain is valid, false if tampered
     */
    public boolean isChainValid() {
        // Check Genesis block
        Block genesis = chain.get(0);
        if (genesis.getIndex() != 0) return false;
        if (!genesis.getPreviousHash().equals(BlockchainConfig.GENESIS_PREV_HASH)) return false;
        if (!genesis.getHash().equals(genesis.calculateHash())) return false;
        if (!Target.meets(genesis.getHash(), GENESIS_TARGET)) return false;

        // THEORY (Sprint 18a): validating a chain = replaying it. A fresh,
        // empty chain with the same rules receives every block in order, as a
        // brand-new node syncing from a peer would. Every rule (links, hash,
        // Merkle root, expected target, timestamps, block contents) is checked
        // by the very same code that guards blocks arriving from the network,
        // so the two can never disagree.
        Blockchain replay = new Blockchain(NoOpChainStore.INSTANCE, params, clock);
        if (!replay.getLatestBlock().getHash().equals(genesis.getHash())) return false;
        for (int i = 1; i < chain.size(); i++) {
            if (!replay.addBlock(chain.get(i))) return false;
        }
        return true;
    }

    /**
     * Returns an unmodifiable copy of the chain.
     * 
     * THEORY: We return an unmodifiable list to preserve immutability.
     * External code should not be able to directly modify the chain.
     * 
     * @return Unmodifiable view of the blockchain
     */
    public List<Block> getChain() {
        return Collections.unmodifiableList(chain);
    }

    /**
     * Returns the number of blocks in the chain.
     * 
     * @return Chain length (including Genesis block)
     */
    public int getChainSize() {
        return chain.size();
    }

    /**
     * Gets a block by its index.
     * 
     * @param index Block index (0 = Genesis)
     * @return The block at the specified index
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public Block getBlock(int index) {
        return chain.get(index);
    }

    /**
     * Prints a summary of the entire blockchain.
     */
    public void printChain() {
        System.out.println("\n═══════════════════════════════════════════════════════════");
        System.out.println("                     BLOCKCHAIN STATE                        ");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("Chain length: " + chain.size() + " blocks");
        System.out.println("Pending transactions: " + pendingTransactions.size());
        System.out.println("Chain valid: " + (isChainValid() ? "✓ YES" : "✗ NO"));
        System.out.println("───────────────────────────────────────────────────────────");

        for (Block block : chain) {
            System.out.println("Block #" + block.getIndex());
            if (block.getTransactions().size() > 0) {
                System.out.println("  Transactions: " + block.getTransactionCount());
                for (Transaction tx : block.getTransactions()) {
                    System.out.println("    " + tx);
                }
            } else {
                System.out.println("  Data: " + block.getData());
            }            
            System.out.println("  Nonce: " + block.getNonce());
            System.out.println("  Hash: " + block.getHash().substring(0, 16) + "...");
            System.out.println("  Prev: " + block.getPreviousHash().substring(0, Math.min(16, block.getPreviousHash().length())) + "...");
            System.out.println();
        }
    }

    @Override
    public String toString() {
        return "Blockchain{" +
            "chainSize= " + chain.size() +
            ", pendingTx= " + pendingTransactions.size() +
            ", isValid= " + isChainValid() +
            ", latestBlockHash= " + getLatestBlock().getHash().substring(0, 16) + "..." +
            "}";
    }
}