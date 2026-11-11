package com.blocksmith.storage;

import java.util.List;
import java.util.OptionalInt;

import com.blocksmith.core.Block;
import com.blocksmith.core.Transaction;

/**
 * Durable storage for a node's chain and mempool.
 *
 * THEORY: Storage is data, validation is the authority
 *
 * A store only remembers what the node accepted. It never decides what is
 * valid: on startup the node replays the stored blocks through the same
 * checks it applies to blocks from untrusted peers (Sprint 16b). That keeps
 * the interface small and means a corrupted or hand-edited database cannot
 * smuggle an invalid block into the chain.
 *
 *   accept block  -> saveBlock()        (one row per height)
 *   new pending   -> savePending()
 *   tx confirmed  -> removePending()
 *   startup       -> loadBlocks() / loadPending()  -> replay with validation
 *
 * Derived state (balances, the contract registry) is never stored: it is
 * rebuilt from the blocks, exactly as every other node computes it.
 *
 * Implementations report storage failures as {@link StorageException}.
 */
public interface ChainStore extends AutoCloseable {

    /**
     * Stores a block at its height. Saving the same block again is a no-op,
     * so a replay or a re-gossiped block never creates duplicates.
     */
    void saveBlock(Block block);

    /** Returns every stored block, ordered by index (genesis first). */
    List<Block> loadBlocks();

    /** Stores a pending transaction. Saving the same one twice is a no-op. */
    void savePending(Transaction transaction);

    /** Forgets a pending transaction (for example once it is confirmed). */
    void removePending(String transactionId);

    /** Returns the stored pending transactions in the order they were saved. */
    List<Transaction> loadPending();

    /** Releases the underlying resources. Does not throw checked exceptions. */
    @Override
    void close();

    // ===== INDEXED LOOKUPS (Sprint 16c) =====
    //
    // THEORY: An index is a hint, the chain is the truth
    //
    // A store may keep lookup tables (block by hash, transactions by
    // address) so the node does not have to scan every block. The answers
    // are block heights only; the caller resolves them against the chain in
    // memory and checks the result is really there. A stale row (for example
    // from a stored block that failed validation on restore) is then simply
    // ignored instead of producing a wrong answer.

    /** Where a transaction sits: its id and the height of its block. */
    record TxRef(String transactionId, int blockIndex) {}

    /** True if this store answers the lookups below; false means "scan the chain". */
    default boolean isIndexed() {
        return false;
    }

    /** Height of the block with this hash, if the store knows it. */
    default OptionalInt findBlockIndexByHash(String hash) {
        return OptionalInt.empty();
    }

    /** Height of the block containing this transaction, if the store knows it. */
    default OptionalInt findTransactionBlockIndex(String transactionId) {
        return OptionalInt.empty();
    }

    /**
     * Confirmed transactions sent or received by {@code address}, newest
     * first (by block height, then position in the block).
     */
    default List<TxRef> findTransactionsByAddress(String address) {
        return List.of();
    }
}
