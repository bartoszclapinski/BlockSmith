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
import java.util.Comparator;
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
 *
 * THREADING (Sprint 20):
 * One node uses one Blockchain from several threads at once: a listener
 * thread per peer, the API's request threads and the heartbeat. The
 * Blockchain is a MONITOR: every public method that touches its state is
 * synchronized on the instance, so only one thread is inside at a time and
 * each call is atomic (a balance check and the add that follows it cannot
 * be split by another thread). The lock is reentrant, so public methods may
 * call each other. Lists handed out are copies taken under the lock, never
 * live views. The only things read without the lock are the immutable
 * ChainParams and MempoolPolicy. Lock order is always Blockchain, then
 * ChainStore (the store never calls back), so the two cannot deadlock.
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

    /** This node's mempool limits (Sprint 19a): policy, not consensus. */
    private final MempoolPolicy mempoolPolicy;

    /** How often mining starts over when a peer's block makes its work stale (Sprint 20b). */
    private static final int MAX_MINING_ATTEMPTS = 100;

    /**
     * Hash of the tip, readable WITHOUT the lock (volatile), so the nonce
     * search, which runs outside it, can notice a new block (Sprint 20b).
     * Written only under the lock, together with the chain.
     */
    private volatile String tipHash;

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

    /** Every (sender, sequence) pair already inside a block (Sprint 19b). */
    private final Set<SequenceKey> confirmedSequences = new HashSet<>();

    /** A replace-by-fee key: at most one transaction per pair ever confirms. */
    private record SequenceKey(String sender, long sequence) {
        /** The key of {@code tx}, or null if it has no sequence. */
        static SequenceKey of(Transaction tx) {
            return tx.getSequence() == null ? null : new SequenceKey(tx.getSender(), tx.getSequence());
        }
    }

    /**
     * Largest number of orphan blocks held at once. Caps memory if a peer
     * floods us with forward blocks whose parents never arrive.
     */
    static final int MAX_ORPHANS = 50;

    /** Deepest fork we consider: a branch must leave our chain at most this far below the tip (Sprint 21). */
    public static final int MAX_REORG_DEPTH = 100;

    /** Largest number of side-branch blocks held at once (Sprint 21a). */
    static final int MAX_SIDE_BLOCKS = 200;

    /**
     * THEORY: SIDE BRANCHES (Sprint 21a)
     *
     * Two miners can find a block at the same height at the same time. Each
     * node first sees one of them; the other is a COMPETING block: valid,
     * with a parent we know, but not on our chain. It is not junk: if more
     * blocks are built on it, its branch may become the one with the most
     * work, and every node must then switch to it (Sprint 21b). So instead
     * of dropping it, we keep it here, keyed by its own hash, so a child
     * can find it as a parent. Bounded like the orphans; oldest dropped first.
     */
    private final Map<String, Block> sideBlocks =
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Block> eldest) {
                    return size() > MAX_SIDE_BLOCKS;
                }
            };

    /** Height of every block on our chain, by hash (Sprint 21a): "is this parent on our chain, and where?" */
    private final Map<String, Integer> heightByHash = new HashMap<>();

    /**
     * Cumulative work up to and including each height (Sprint 21a), next to
     * {@code targets}: derived from them, never stored, so every node and
     * every restore computes the same numbers.
     */
    private final List<BigInteger> cumulativeWork = new ArrayList<>();

    /** A chain switch, for the API and the log (Sprint 21b). */
    public record ReorgEvent(int depth, int forkHeight, String oldTip, String newTip,
                             int newHeight, int transactionsBack, long timestamp) {}

    private int reorgCount = 0;
    private ReorgEvent lastReorg = null;

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
        this(store, params, clock, MempoolPolicy.DEFAULT);
    }

    /**
     * Creates a blockchain with explicit mempool limits (Sprint 19a).
     * Unlike the params, the policy is this node's own choice.
     */
    public Blockchain(ChainStore store, ChainParams params, MempoolPolicy mempoolPolicy) {
        this(store, params, Clock.systemUTC(), mempoolPolicy);
    }

    /** Creates a blockchain with every dependency explicit. */
    public Blockchain(ChainStore store, ChainParams params, Clock clock, MempoolPolicy mempoolPolicy) {
        this.chain = new ArrayList<>();
        this.pendingTransactions = new ArrayList<>();
        this.store = store;
        this.params = params;
        this.clock = clock;
        this.mempoolPolicy = mempoolPolicy;

        // Create and mine the Genesis block (deterministic: fixed timestamp)
        Block genesis = Block.createGenesisBlock();
        genesis.mineBlock(GENESIS_TARGET);
        chain.add(genesis);
        tipHash = genesis.getHash();
        targets.add(params.initialTarget());
        heightByHash.put(genesis.getHash(), 0);
        cumulativeWork.add(Target.work(params.initialTarget()));
        store.saveBlock(genesis);
    }

    // ===== DIFFICULTY (Sprint 17b) =====

    /** The consensus params this chain runs with. */
    public ChainParams getChainParams() {
        return params;
    }

    /** The target the next block (tip + 1) must meet. */
    public synchronized BigInteger getNextTarget() {
        int height = chain.size();
        return targetAt(height, chain, targets.get(height - 1));
    }

    /** The target that was in effect for the block at {@code height}. */
    public synchronized BigInteger getTargetAt(int height) {
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
    public synchronized long medianTimePast(int height) {
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
    public synchronized List<DifficultyEpoch> getDifficultyHistory() {
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
    public synchronized int getBlocksUntilRetarget() {
        int next = chain.size();                       // height of the next block
        int interval = params.retargetInterval();
        int boundary = (next / interval + 1) * interval;
        return boundary - next;
    }

    /**
     * Average time between the recent blocks (up to one retarget window,
     * genesis excluded), or null if there are fewer than two such blocks.
     */
    public synchronized Long getAverageBlockTimeMs() {
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
    public synchronized RestoreReport restoreFromStore() {
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

        for (Transaction tx : store.loadPending()) {
            // Already in a block (the node stopped between saving the block
            // and clearing pending): re-adding it would let it be mined twice.
            if (isConfirmed(tx.getTransactionId()) || !addTransaction(tx)) {
                store.removePending(tx.getTransactionId());
            }
        }
        // Counted at the end: a smaller mempool policy than before the
        // restart (Sprint 19a) may evict some of the ones re-added first.
        return new RestoreReport(restored, candidates - restored, pendingTransactions.size());
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
        tipHash = block.getHash();
        targets.add(target);
        heightByHash.put(block.getHash(), block.getIndex());
        cumulativeWork.add(cumulativeWork.get(cumulativeWork.size() - 1).add(Target.work(target)));
        sideBlocks.remove(block.getHash());
        for (Transaction tx : block.getTransactions()) {
            confirmedTxIds.add(tx.getTransactionId());
            SequenceKey key = SequenceKey.of(tx);
            if (key != null) confirmedSequences.add(key);
        }
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
        final Set<SequenceKey> sequences = new HashSet<>();

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
            SequenceKey key = SequenceKey.of(tx);
            if (key != null) sequences.add(key);
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
        if (coinbase.getSequence() != null) return "the coinbase carries a sequence";
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
        // Replace-by-fee (Sprint 19b): two versions of one payment share a
        // (sender, sequence) pair, and only one of them may ever confirm.
        SequenceKey key = SequenceKey.of(tx);
        if (key != null && (check.sequences.contains(key) || confirmedSequences.contains(key))) {
            return "sequence " + key.sequence() + " of this sender is already used";
        }

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
    public synchronized Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }

    /**
     * Adds a new block to the blockchain (Legacy method for string data).    
     * 
     * @param data The data to store in the new block
     * @return The newly created and mined block
     */ 
    public synchronized Block addBlock(String data) {
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
    public synchronized boolean addBlock(Block block) {
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

        // Already ours (on the chain or kept on a side branch): nothing to do.
        if (heightByHash.containsKey(block.getHash()) || sideBlocks.containsKey(block.getHash())) {
            return false;
        }

        // Its parent is known but it does not extend our tip: a COMPETING
        // block (Sprint 21a). Keep it on a side branch; it may later turn
        // out to be on the chain with the most work (Sprint 21b).
        // If its branch now has more work than our chain, we switch to it
        // (Sprint 21b); then the block IS on our chain and we return true,
        // so the node relays it like any other block it accepted.
        String parent = block.getPreviousHash();
        if (heightByHash.containsKey(parent) || sideBlocks.containsKey(parent)) {
            Block branchTip = addSideBlock(block);
            return branchTip != null && tryReorg(branchTip) && heightByHash.containsKey(block.getHash());
        }

        // Parent unknown: park as an orphan until the parent shows up (it may
        // extend our chain or a side branch). Without its history we cannot
        // know its exact target yet, so for now it only has to meet the
        // easiest one allowed (cheap spam filter); the real target is checked
        // once it connects. A malformed block is dropped outright.
        if (block.getIndex() > 0 && cheapChecksPass(block)) {
            orphans.put(block.getPreviousHash(), block);
        }

        return false;
    }

    /** The checks a block gets before we know its branch's history: well formed vs powLimit, time, size. */
    private boolean cheapChecksPass(Block block) {
        return isWellFormed(block, params.powLimit()) && isNotTooFarInFuture(block) && limitViolation(block) == null;
    }

    /**
     * Keeps a competing block (its parent is known, on our chain or on a
     * side branch) if it passes the cheap checks, sits right above its
     * parent, and its branch leaves our chain at most {@link #MAX_REORG_DEPTH}
     * below the tip. Orphans waiting on it follow it onto the side branch.
     *
     * @return the highest block kept (the new tip of that branch), or null
     */
    private Block addSideBlock(Block block) {
        Block kept = null;
        while (block != null) {
            Integer parentHeight = heightOf(block.getPreviousHash());
            int forkHeight = forkHeightOf(block);
            if (parentHeight == null || block.getIndex() != parentHeight + 1 || !cheapChecksPass(block)
                    || getLatestBlock().getIndex() - forkHeight > MAX_REORG_DEPTH) {
                return kept;
            }
            sideBlocks.put(block.getHash(), block);
            System.out.println("Kept competing block #" + block.getIndex() + " on a side branch (fork at #"
                    + forkHeight + ")");
            kept = block;
            block = orphans.remove(block.getHash());          // a child that arrived first
        }
        return kept;
    }

    // ===== REORGANISATION (Sprint 21b) =====

    /**
     * Switches to the branch ending at {@code branchTip} if it is valid and
     * has strictly more work than our chain.
     *
     * THEORY: Fork choice by replay
     *
     * Bitcoin keeps "undo data" for every block so it can roll its state
     * back to the fork point. We do something simpler and harder to get
     * wrong: build the CANDIDATE chain (ours up to the fork point, then the
     * branch) on a fresh Blockchain, through addBlock, the same validation
     * every block, every restore and isChainValid() go through: targets,
     * timestamps, content rules, sequences, contracts. If any branch block
     * fails, the branch is invalid and dropped from that block on. If all
     * pass, the candidate knows its exact total work; only if it is
     * STRICTLY more than ours do we adopt it (a tie keeps the chain we saw
     * first, as Bitcoin does, so two equal branches cannot flip-flop).
     *
     * Cost: one replay of the chain per attempt, fine at this scale. A cheap
     * pre-filter skips branches lower than our tip: within a retarget window
     * both sides share the target, so a lower branch cannot have more work.
     *
     * @return true if we switched
     */
    private boolean tryReorg(Block branchTip) {
        if (branchTip.getIndex() < getLatestBlock().getIndex()) return false;

        // The branch, oldest first, back to its last ancestor on our chain.
        java.util.LinkedList<Block> branch = new java.util.LinkedList<>();
        Block b = branchTip;
        while (true) {
            branch.addFirst(b);
            if (heightByHash.containsKey(b.getPreviousHash())) break;
            b = sideBlocks.get(b.getPreviousHash());
            if (b == null) return false;                      // detached (an ancestor was evicted)
        }
        int forkHeight = heightByHash.get(branch.getFirst().getPreviousHash());
        if (getLatestBlock().getIndex() - forkHeight > MAX_REORG_DEPTH) return false;

        Blockchain candidate = new Blockchain(NoOpChainStore.INSTANCE, params, clock, mempoolPolicy);
        for (int h = 1; h <= forkHeight; h++) {
            if (!candidate.addBlock(chain.get(h))) {
                throw new IllegalStateException("Our own chain failed its replay at #" + h);
            }
        }
        for (int i = 0; i < branch.size(); i++) {
            if (!candidate.addBlock(branch.get(i))) {
                // Invalid from here on: forget this block and everything built on it.
                for (Block bad : branch.subList(i, branch.size())) sideBlocks.remove(bad.getHash());
                System.out.println("Side branch rejected at #" + branch.get(i).getIndex()
                        + ": it does not pass full validation");
                return false;
            }
        }
        if (candidate.getTotalWork().compareTo(getTotalWork()) <= 0) return false;

        adoptChain(candidate, forkHeight);
        return true;
    }

    /**
     * Makes {@code candidate}'s chain ours (Sprint 21b). All under the lock,
     * so no other thread ever sees half of the old chain and half of the new.
     *
     *   1. undo:  our blocks above the fork point become side blocks (if
     *             their branch wins again later, we can switch back)
     *   2. apply: chain, targets, work, confirmed ids and sequences, the
     *             contract registry and the tip all come from the candidate
     *   3. store: rewrite from the fork point, drop anything higher
     *   4. mempool: the undone blocks' transactions, then the old pending
     *             ones, are re-submitted against the new chain, in order;
     *             whatever is now confirmed, conflicting or unaffordable
     *             falls out
     */
    private void adoptChain(Blockchain candidate, int forkHeight) {
        List<Block> undone = new ArrayList<>(chain.subList(forkHeight + 1, chain.size()));
        List<Transaction> oldPending = new ArrayList<>(pendingTransactions);
        String oldTip = tipHash;

        chain.clear();
        chain.addAll(candidate.chain);
        targets.clear();
        targets.addAll(candidate.targets);
        cumulativeWork.clear();
        cumulativeWork.addAll(candidate.cumulativeWork);
        heightByHash.clear();
        heightByHash.putAll(candidate.heightByHash);
        confirmedTxIds.clear();
        confirmedTxIds.addAll(candidate.confirmedTxIds);
        confirmedSequences.clear();
        confirmedSequences.addAll(candidate.confirmedSequences);
        contracts.clear();
        contracts.putAll(candidate.contracts);
        tipHash = candidate.tipHash;                          // a mine in progress sees this and starts over

        for (Block applied : chain.subList(forkHeight + 1, chain.size())) sideBlocks.remove(applied.getHash());
        for (Block old : undone) sideBlocks.put(old.getHash(), old);

        for (int h = forkHeight + 1; h < chain.size(); h++) store.saveBlock(chain.get(h));
        store.truncateAbove(chain.size() - 1);

        for (Transaction tx : oldPending) store.removePending(tx.getTransactionId());
        pendingTransactions.clear();
        List<Transaction> resubmit = new ArrayList<>();
        for (Block old : undone) {
            List<Transaction> txs = old.getTransactions();
            if (txs.size() > 1) resubmit.addAll(txs.subList(1, txs.size()));   // not the coinbase
        }
        resubmit.addAll(oldPending);
        int back = 0;
        for (Transaction tx : resubmit) if (submitTransaction(tx) == null) back++;

        reorgCount++;
        lastReorg = new ReorgEvent(undone.size(), forkHeight, oldTip, tipHash, chain.size() - 1, back,
                clock.millis());
        System.out.println("🔀 Reorganised: undid " + undone.size() + " block(s) back to #" + forkHeight
                + ", now at #" + (chain.size() - 1) + "; " + back + " transaction(s) pending");
        attachOrphans();
    }

    /** How many times this node switched to a branch with more work (Sprint 21b). */
    public synchronized int getReorgCount() {
        return reorgCount;
    }

    /** The most recent chain switch, or null if there was none (Sprint 21b). */
    public synchronized ReorgEvent getLastReorg() {
        return lastReorg;
    }

    /** Height of a block on our chain or on a side branch, or null if unknown. */
    private Integer heightOf(String hash) {
        Integer h = heightByHash.get(hash);
        if (h != null) return h;
        Block side = sideBlocks.get(hash);
        return side != null ? side.getIndex() : null;
    }

    /** Height where a side-branch block's branch leaves our chain (its last ancestor on our chain). */
    private int forkHeightOf(Block block) {
        String hash = block.getPreviousHash();
        while (!heightByHash.containsKey(hash)) {
            Block side = sideBlocks.get(hash);
            if (side == null) return -1;                     // detached: treat as too deep
            hash = side.getPreviousHash();
        }
        return heightByHash.get(hash);
    }

    /** How many competing blocks we hold on side branches (Sprint 21a). */
    public synchronized int getSideBlockCount() {
        return sideBlocks.size();
    }

    /** True if a block with this hash is held on a side branch (Sprint 21a). */
    public synchronized boolean isSideBlock(String hash) {
        return sideBlocks.containsKey(hash);
    }

    /** Total work of our chain, genesis included (Sprint 21a): what fork choice compares. */
    public synchronized BigInteger getTotalWork() {
        return cumulativeWork.get(cumulativeWork.size() - 1);
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
            // Also drop a pending version of a payment whose OTHER version
            // was just confirmed (Sprint 19b): it can never be mined now.
            SequenceKey key = SequenceKey.of(tx);
            boolean conflict = key != null && confirmedSequences.contains(key);
            if (!confirmedIds.contains(tx.getTransactionId()) && !conflict) return false;
            store.removePending(tx.getTransactionId());
            return true;
        });
    }

    /**
     * @return number of blocks currently held in the orphan buffer
     */
    public synchronized int getOrphanCount() {
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
    public synchronized boolean addTransaction(Transaction transaction) {
        String why = submitTransaction(transaction);
        // Duplicates are routine (gossip echoes), so they are not logged.
        if (why != null && !why.equals(ALREADY_PENDING) && !why.equals(ALREADY_CONFIRMED)) {
            System.out.println("Transaction rejected: " + why);
        }
        return why == null;
    }

    /** {@link #submitTransaction} reason: the same id is already waiting. */
    public static final String ALREADY_PENDING = "already pending";

    /** {@link #submitTransaction} reason: the same id is already in a block. */
    public static final String ALREADY_CONFIRMED = "already confirmed";

    /**
     * Same as {@link #addTransaction}, but says WHY a transaction was
     * refused (Sprint 19d), so the API can tell the user, e.g. "mempool
     * full, fee rate ... is below the minimum ...".
     *
     * @return null if the transaction was added, else the reason it was not
     */
    public synchronized String submitTransaction(Transaction transaction) {
        // Validate the transaction
        if (transaction == null || !transaction.isValid()) {
            return "invalid transaction (amount, fee, sender or recipient)";
        }

        // Reject COINBASE transactions (only mining creates these)
        if (transaction.getSender().equals(BlockchainConfig.COINBASE_ADDRESS)) {
            return "cannot manually create COINBASE transactions";
        }

        // Contract rules (Sprint 14). A transfer TO a contract address must
        // carry a locking script (otherwise the funds would be burned); a
        // transfer FROM one is a claim and must satisfy the contract's script.
        if (transaction.getRecipient().startsWith(BlockchainConfig.CONTRACT_ADDRESS_PREFIX)
                && (transaction.getLockingScript() == null
                    || transaction.getLockingScript().isBlank())) {
            return "a contract deploy requires a locking script";
        }
        if (transaction.getSender().startsWith(BlockchainConfig.CONTRACT_ADDRESS_PREFIX)
                && !isValidClaim(transaction)) {
            return "the contract claim is not valid";
        }

        // A signed transaction must verify (Sprint 18a: blocks enforce the
        // same rule, so a bad one would make our own mined block invalid).
        if ((transaction.getSignature() != null || transaction.getSenderPublicKey() != null)
                && !transaction.verifySignature()) {
            return "the signature does not verify";
        }

        // Reject duplicates already waiting in the pool. This also stops a
        // gossiped transaction from being relayed twice (returns false).
        String txId = transaction.getTransactionId();
        if (confirmedTxIds.contains(txId)) {
            return ALREADY_CONFIRMED;                  // already in a block: replaying it is not allowed
        }
        for (Transaction pending : pendingTransactions) {
            if (pending.getTransactionId().equals(txId)) {
                return ALREADY_PENDING;
            }
        }

        // Replace-by-fee (Sprint 19b): a pending tx with the same (sender,
        // sequence) is an older version of this payment.
        SequenceKey key = SequenceKey.of(transaction);
        Transaction replaced = null;
        if (key != null) {
            if (confirmedSequences.contains(key)) {
                return "sequence " + key.sequence() + " of this sender is already confirmed";
            }
            replaced = pendingWithSequence(key);
            if (replaced != null) {
                String why = replacementViolation(replaced, transaction);
                if (why != null) return "replacement refused: " + why;
            }
        }

        // Check sender has sufficient balance. A replacement releases what
        // the version it replaces had promised.
        double senderBalance = getBalance(transaction.getSender());
        double pendingOutgoing = getPendingOutgoing(transaction.getSender());
        double availableBalance = senderBalance - pendingOutgoing
                + (replaced != null ? replaced.getTotalCost() : 0);

        if (availableBalance < transaction.getTotalCost()) {
            return "insufficient funds: available " + availableBalance + " " + BlockchainConfig.CURRENCY_SYMBOL
                    + ", required " + transaction.getTotalCost() + " " + BlockchainConfig.CURRENCY_SYMBOL
                    + " (amount + fee)";
        }

        // Mempool limit (Sprint 19a): when full, the cheapest transactions
        // make room, but only for one that pays clearly more per byte.
        // A replacement first gives up its old version's place; if it then
        // still does not fit, the old version is put back where it was.
        int replacedAt = replaced != null ? pendingTransactions.indexOf(replaced) : -1;
        if (replaced != null) pendingTransactions.remove(replacedAt);
        String noRoom = makeRoomFor(transaction);
        if (noRoom != null) {
            if (replaced != null) pendingTransactions.add(replacedAt, replaced);
            return noRoom;
        }
        if (replaced != null) {
            store.removePending(replaced.getTransactionId());
            System.out.println("Replaced pending transaction " + shortId(replaced) + " (fee "
                    + replaced.getFee() + ") with " + shortId(transaction) + " (fee " + transaction.getFee() + ")");
        }

        // Add to pending pool (and remember it across restarts)
        pendingTransactions.add(transaction);
        store.savePending(transaction);
        System.out.println("Transaction added to pending pool: " + transaction);
        return null;
    }

    /**
     * Runs {@code work} while holding this chain's lock (Sprint 20c), so
     * several reads see ONE state: no block or transaction can land between
     * them. Each public method is atomic on its own; this makes a GROUP of
     * calls atomic, e.g. an API response that reports the height and the
     * target together. Keep {@code work} short and local: no I/O, no
     * waiting, and no calls into other components that take their own locks.
     *
     * @return what {@code work} returns
     */
    public synchronized <T> T atomically(java.util.function.Supplier<T> work) {
        return work.get();
    }

    // ===== REPLACE-BY-FEE (Sprint 19b) =====

    private Transaction pendingWithSequence(SequenceKey key) {
        for (Transaction tx : pendingTransactions) {
            if (key.equals(SequenceKey.of(tx))) return tx;
        }
        return null;
    }

    /**
     * Why {@code tx} may not replace the pending {@code old}, or null.
     *
     * THEORY: Replacement rules (after Bitcoin's BIP125)
     *
     * 1. Only the fee may change. Changing the recipient or amount would be
     *    "cancel and send something else", which is a different feature.
     * 2. The fee must go up, and the fee per byte by at least the mempool's
     *    increment. Otherwise anyone could relay endless near-identical
     *    versions, each costing every node bandwidth, for free.
     * 3. A signed payment can only be replaced by one signed with the same
     *    key, so nobody else can bump (and so spend) someone's fee for them.
     *
     * These are mempool POLICY. Consensus only says that one version, never
     * two, may be confirmed; which one a miner picks is up to the miner.
     */
    private String replacementViolation(Transaction old, Transaction tx) {
        if (!old.getRecipient().equals(tx.getRecipient()) || old.getAmount() != tx.getAmount()
                || !java.util.Objects.equals(old.getLockingScript(), tx.getLockingScript())
                || !java.util.Objects.equals(old.getUnlockingScript(), tx.getUnlockingScript())) {
            return "a replacement may only change the fee";
        }
        if (old.getSenderPublicKey() != null && (tx.getSenderPublicKey() == null
                || !java.util.Arrays.equals(old.getSenderPublicKey().getEncoded(),
                        tx.getSenderPublicKey().getEncoded()))) {
            return "the replacement must be signed by the same key";
        }
        if (!(tx.getFee() > old.getFee())) {
            return "the fee " + tx.getFee() + " is not higher than " + old.getFee();
        }
        double needed = old.feeRate() + mempoolPolicy.incrementalFeeRate();
        if (!(tx.feeRate() > old.feeRate() && tx.feeRate() >= needed)) {
            return "fee rate " + tx.feeRate() + " BSC/byte is below the minimum " + needed;
        }
        return null;
    }

    /**
     * The smallest fee (rounded up to 0.0001 BSC) at which an unsigned
     * replacement of {@code pending} passes the replacement rules (Sprint
     * 19d): a higher fee and a fee rate at least the increment above the old
     * one. The replacement's size changes a little with the fee's digits, so
     * the candidate is checked on a real {@link Transaction#withFee} copy.
     *
     * @throws IllegalStateException if {@code pending} has no sequence
     */
    public synchronized double minimumBumpFee(Transaction pending) {
        double needed = pending.feeRate() + mempoolPolicy.incrementalFeeRate();
        double fee = Math.max(roundUpFee(needed * pending.sizeBytes()), roundUpFee(pending.getFee() + 0.0001));
        for (int i = 0; i < 10_000; i++) {
            Transaction probe = pending.withFee(fee);
            if (probe.feeRate() > pending.feeRate() && probe.feeRate() >= needed) return fee;
            fee = roundUpFee(fee + 0.0001);
        }
        return fee;
    }

    /** Rounds a fee UP to 4 decimals (0.0001 BSC), so it never pays too little. */
    public static double roundUpFee(double fee) {
        double rounded = Math.ceil(fee * 10_000 - 1e-9) / 10_000;
        return rounded > 0 ? rounded : 0;              // never -0.0 (ceil of a tiny negative)
    }

    /** The pending transaction with this id, or null (Sprint 19b: what a bump starts from). */
    public synchronized Transaction getPendingTransaction(String transactionId) {
        for (Transaction tx : pendingTransactions) {
            if (tx.getTransactionId().equals(transactionId)) return tx;
        }
        return null;
    }

    // ===== MEMPOOL LIMIT (Sprint 19a) =====

    /**
     * Makes room for {@code tx} by evicting the cheapest pending transactions.
     *
     * Victims are taken lowest fee rate first and, among equal rates, newest
     * first (the ones that have waited longest keep their place). The new
     * transaction must beat every victim's rate by the policy's increment;
     * if it cannot, nothing is evicted and the transaction is refused.
     *
     * Only the decision is all-or-nothing: victims are chosen first, then
     * removed, so a refused transaction never costs anyone their place.
     *
     * @return null if {@code tx} now fits, else why it was refused
     */
    private String makeRoomFor(Transaction tx) {
        int size = tx.sizeBytes();
        if (size > mempoolPolicy.maxBytes()) {
            return "larger than the whole mempool (" + size + " bytes)";
        }
        int count = pendingTransactions.size();
        int bytes = getPendingBytes();
        if (fits(count, bytes, size)) return null;

        List<Transaction> candidates = new ArrayList<>(pendingTransactions);
        Collections.reverse(candidates);                                    // newest first
        candidates.sort(Comparator.comparingDouble(Transaction::feeRate));  // stable: ties stay newest first

        double rate = tx.feeRate();
        List<Transaction> victims = new ArrayList<>();
        for (Transaction victim : candidates) {
            if (fits(count, bytes, size)) break;
            double needed = victim.feeRate() + mempoolPolicy.incrementalFeeRate();
            if (!(rate > victim.feeRate() && rate >= needed)) {
                return "mempool full, fee rate " + rate + " BSC/byte is below the minimum " + needed;
            }
            victims.add(victim);
            count--;
            bytes -= victim.sizeBytes();
        }

        for (Transaction victim : victims) {
            pendingTransactions.remove(victim);
            store.removePending(victim.getTransactionId());
            System.out.println("Evicted pending transaction " + shortId(victim)
                    + " (fee rate " + victim.feeRate() + ") to make room");
        }
        return null;
    }

    private boolean fits(int count, int bytes, int size) {
        return count + 1 <= mempoolPolicy.maxTransactions() && bytes + size <= mempoolPolicy.maxBytes();
    }

    /** This node's mempool limits. */
    public MempoolPolicy getMempoolPolicy() {
        return mempoolPolicy;
    }

    /** Total size of the pending transactions, in bytes of their JSON. */
    public synchronized int getPendingBytes() {
        int bytes = 0;
        for (Transaction tx : pendingTransactions) bytes += tx.sizeBytes();
        return bytes;
    }

    /**
     * The fee rate (BSC per byte) a new transaction must pay to get into the
     * mempool: 0 while a typical transaction still fits, else the lowest
     * pending rate plus the increment (Bitcoin calls it {@code mempoolminfee}).
     */
    public synchronized double getMempoolMinFeeRate() {
        if (fits(pendingTransactions.size(), getPendingBytes(), MempoolPolicy.TYPICAL_TX_BYTES)) {
            return 0;
        }
        double lowest = Double.MAX_VALUE;
        for (Transaction tx : pendingTransactions) lowest = Math.min(lowest, tx.feeRate());
        return lowest + mempoolPolicy.incrementalFeeRate();
    }

    // ===== FEE ESTIMATION (Sprint 19c) =====

    /** How many recent blocks the history view of the fee estimate looks at. */
    public static final int FEE_HISTORY_BLOCKS = 6;

    /**
     * The fee rate (BSC per byte) that should get a new transaction mined
     * within {@code targetBlocks} blocks.
     *
     * THEORY: Two views, take the stricter
     *
     * 1. MEMPOOL VIEW (what is waiting now): fill {@code targetBlocks}
     *    blocks the way a miner would (best fee per byte first, same
     *    limits). If everything fits, any fee will do. If not, a new
     *    transaction must outbid the cheapest one that still made it in.
     * 2. HISTORY VIEW (what recent blocks accepted): the cheapest rate in
     *    each of the last {@value #FEE_HISTORY_BLOCKS} blocks, counting a
     *    block that had room as 0 (anything would have got in). For "next
     *    block" we want a rate that would have made it into every one of
     *    them (the highest of those minimums); for 6 blocks, into at least
     *    one (the lowest). So a longer target never costs more.
     * 3. And never below what the mempool itself demands to let a
     *    transaction in ({@link #getMempoolMinFeeRate()}).
     *
     * The mempool view reacts at once; the history view remembers that blocks
     * were full a moment ago even if the mempool has just been emptied.
     * Bitcoin Core's estimator is fancier (it tracks how long transactions
     * at each fee rate actually took to confirm) but answers the same
     * question: "what rate gets me in within N blocks?"
     *
     * @param targetBlocks within how many blocks, at least 1
     */
    public synchronized double estimateFeeRate(int targetBlocks) {
        if (targetBlocks < 1) throw new IllegalArgumentException("targetBlocks must be at least 1");
        double estimate = Math.max(mempoolFeeRate(targetBlocks), historyFeeRate(targetBlocks));
        return Math.max(estimate, getMempoolMinFeeRate());
    }

    /** The mempool view: simulate the next {@code targetBlocks} blocks. */
    private double mempoolFeeRate(int targetBlocks) {
        List<Transaction> waiting = byFeeRate(pendingTransactions);
        Block latest = getLatestBlock();
        Transaction placeholder = new Transaction(
                BlockchainConfig.COINBASE_ADDRESS, "fee-estimate", BlockchainConfig.MINING_REWARD);
        int emptyBlockBytes = new Block(latest.getIndex() + 1, List.of(placeholder), latest.getHash(), 0L)
                .sizeBytes() + NONCE_DIGITS_MARGIN + COINBASE_AMOUNT_MARGIN;

        double cheapestIn = Double.MAX_VALUE;
        for (int b = 0; b < targetBlocks && !waiting.isEmpty(); b++) {
            // Same selection as minePendingTransactions (without the stale check).
            int bytes = emptyBlockBytes;
            int count = 0;
            java.util.Iterator<Transaction> it = waiting.iterator();
            while (it.hasNext()) {
                if (count + 1 >= params.maxBlockTransactions()) break;
                Transaction tx = it.next();
                int txBytes = tx.sizeBytes() + 1;
                if (bytes + txBytes > params.maxBlockBytes()) continue;
                bytes += txBytes;
                count++;
                cheapestIn = Math.min(cheapestIn, tx.feeRate());
                it.remove();
            }
            if (count == 0) break;                    // blocks hold no transactions at all
        }
        if (waiting.isEmpty() || cheapestIn == Double.MAX_VALUE) return 0;
        // Some would still wait: beat the cheapest that got in (ties lose, they keep arrival order).
        return cheapestIn + mempoolPolicy.incrementalFeeRate();
    }

    /** The history view: what recent blocks accepted, see {@link #estimateFeeRate}. */
    private double historyFeeRate(int targetBlocks) {
        List<Double> cheapest = new ArrayList<>();
        for (int i = chain.size() - 1; i >= 1 && cheapest.size() < FEE_HISTORY_BLOCKS; i--) {
            cheapest.add(cheapestRateIfFull(chain.get(i)));
        }
        if (cheapest.isEmpty()) return 0;
        cheapest.sort(Comparator.reverseOrder());
        return cheapest.get(Math.min(targetBlocks, cheapest.size()) - 1);
    }

    /**
     * The lowest fee rate a full block included, or 0 if the block had room
     * for another typical transaction (then any fee would have got in).
     */
    private double cheapestRateIfFull(Block block) {
        List<Transaction> txs = block.getTransactions();
        if (txs.size() <= 1) return 0;
        boolean full = txs.size() >= params.maxBlockTransactions()
                || block.sizeBytes() + MempoolPolicy.TYPICAL_TX_BYTES + 1 > params.maxBlockBytes();
        if (!full) return 0;
        double cheapest = Double.MAX_VALUE;
        for (int i = 1; i < txs.size(); i++) cheapest = Math.min(cheapest, txs.get(i).feeRate());
        return cheapest;
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
     * Counts amount + fee (Sprint 18c): both leave the sender's balance.
     *
     * @param address Ther address to check
     * @return Total amount (plus fees) being sent in pending transactions
     */
    public synchronized double getPendingOutgoing(String address) {
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
        return minePendingTransactions(minerAddress, () -> false);
    }

    /**
     * Mines like {@link #minePendingTransactions(String)}, but gives up and
     * returns null as soon as {@code cancelled} says so (Sprint 20b), e.g.
     * when the node shuts down.
     *
     * THEORY: Template, work, submit (Sprint 20b)
     *
     * The nonce search can take seconds and needs none of the chain's state:
     * only the block being mined. So it runs WITHOUT the lock, and the API
     * and the network keep working meanwhile. A real miner works the same
     * way: it gets a block template from its node, hashes, and submits.
     *
     *   1. template (locked): tip, transactions, coinbase, target, timestamp
     *   2. work (no lock):     the nonce search; it stops early if the tip
     *                          moves (a peer's block arrived), because a
     *                          block on the old tip could never be appended
     *   3. submit (locked):    appended only if the tip is still the one the
     *                          template was built on
     *
     * Stale work is thrown away and a new template is built, at most
     * {@value #MAX_MINING_ATTEMPTS} times. Nothing is lost: the template
     * never removed anything from the mempool except stale transactions.
     *
     * @return the appended block, or null if cancelled
     */
    public Block minePendingTransactions(String minerAddress, java.util.function.BooleanSupplier cancelled) {
        for (int attempt = 1; attempt <= MAX_MINING_ATTEMPTS; attempt++) {
            MiningTemplate template = buildMiningTemplate(minerAddress);
            Block block = template.block();
            String parent = template.parentHash();
            System.out.println("\n⛏️  Mining block #" + block.getIndex() + " with "
                    + block.getTransactions().size() + " transactions...");

            boolean found = block.mineBlock(template.target(),
                    () -> cancelled.getAsBoolean() || !parent.equals(tipHash));
            if (found && submitMinedBlock(template)) {
                System.out.println("✅ Block mined and added to chain!");
                System.out.println("   Miner " + minerAddress + " received " + block.getTransactions().get(0).getAmount()
                        + " " + BlockchainConfig.CURRENCY_SYMBOL + " (reward + fees)");
                return block;
            }
            if (cancelled.getAsBoolean()) {
                System.out.println("Mining of block #" + block.getIndex() + " cancelled");
                return null;
            }
            System.out.println("Stale work: the chain moved on while mining block #" + block.getIndex()
                    + ", building a new template (attempt " + attempt + ")");
        }
        throw new IllegalStateException("Could not mine: the tip moved " + MAX_MINING_ATTEMPTS + " times in a row");
    }

    /** What step 1 hands to step 2: the block to mine and what it was built on. */
    private record MiningTemplate(Block block, BigInteger target, String parentHash) {}

    /** Step 1 (locked): pick transactions and build the block to mine on the current tip. */
    private synchronized MiningTemplate buildMiningTemplate(String minerAddress) {
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

        Block newBlock = new Block(newIndex, blockTransactions, latestBlock.getHash(),
                nextBlockTimestamp(newIndex));
        return new MiningTemplate(newBlock, getNextTarget(), latestBlock.getHash());
    }

    /**
     * Step 3 (locked): append the mined block if it still extends the tip.
     *
     * @return false if the work is stale (the tip moved meanwhile)
     */
    private synchronized boolean submitMinedBlock(MiningTemplate template) {
        if (!getLatestBlock().getHash().equals(template.parentHash())) return false;

        // Same tip, so the same balances, targets and median time as when the
        // template was built: our block must pass the rules anyone's must.
        String why = blockRuleViolation(template.block());
        if (why != null) throw new IllegalStateException("Mined an invalid block: " + why);

        // appendBlock also removes the block's transactions from the pending
        // pool (they're now confirmed), and any other version of them (19b).
        appendBlock(template.block());
        return true;
    }

    /**
     * The mempool in the order a miner considers it (Sprint 18d): best fee
     * per byte first, ties in arrival order. Limits and stale checks still
     * decide what actually makes it into the next block.
     */
    public synchronized List<Transaction> getPendingInMiningOrder() {
        return Collections.unmodifiableList(byFeeRate(pendingTransactions));
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
    public synchronized double getBalance(String address) {
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
    public synchronized Transaction deployContract(String funder, String lockingScript, double amount) {
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
    public synchronized Transaction claimContract(String contractId, String claimer, String unlockingScript) {
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
    public synchronized Contract getContract(String contractId) {
        return contracts.get(contractId);
    }

    /**
     * @return Unmodifiable view of all contracts derived from the chain
     */
    public synchronized List<Contract> getContracts() {
        return Collections.unmodifiableList(new ArrayList<>(contracts.values()));
    }

    /**
     * Return the pending transaction pool.
     *
     * @return Unmodifiable list of pending transactions
     */
    public synchronized List<Transaction> getPendingTransactions() {
        return List.copyOf(pendingTransactions);       // a snapshot: safe to iterate on any thread
    }

    /**
     * Returns the number of pending transactions.
     * 
     * @return Number of transactions waiting to be mined
     */
    public synchronized int getPendingCount() {
        return pendingTransactions.size();
    }

    /**
     * Writes an online backup of this chain's store to {@code zipFile}
     * (Sprint 16d). The node keeps running while it is taken.
     *
     * @throws UnsupportedOperationException if the store cannot be backed up
     *         (for example the default in-memory chain)
     */
    public synchronized void backupTo(java.nio.file.Path zipFile) {
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
    public synchronized Block getBlockByHash(String hash) {
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
    public synchronized TransactionLookup findTransaction(String transactionId) {
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
    public synchronized List<TransactionLookup> getTransactionHistory(String address) {
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
    public synchronized boolean isChainValid() {
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
    public synchronized List<Block> getChain() {
        return List.copyOf(chain);                     // a snapshot: safe to iterate on any thread
    }

    /**
     * Returns the number of blocks in the chain.
     * 
     * @return Chain length (including Genesis block)
     */
    public synchronized int getChainSize() {
        return chain.size();
    }

    /**
     * Gets a block by its index.
     * 
     * @param index Block index (0 = Genesis)
     * @return The block at the specified index
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public synchronized Block getBlock(int index) {
        return chain.get(index);
    }

    /**
     * Prints a summary of the entire blockchain.
     */
    public synchronized void printChain() {
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
    public synchronized String toString() {
        return "Blockchain{" +
            "chainSize= " + chain.size() +
            ", pendingTx= " + pendingTransactions.size() +
            ", isValid= " + isChainValid() +
            ", latestBlockHash= " + getLatestBlock().getHash().substring(0, 16) + "..." +
            "}";
    }
}