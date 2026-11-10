package com.blocksmith.storage;

import java.util.List;

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
}
