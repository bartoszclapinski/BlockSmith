package com.blocksmith.storage;

import java.util.List;

import com.blocksmith.core.Block;
import com.blocksmith.core.Transaction;

/**
 * A store that remembers nothing: the node keeps today's in-memory behaviour.
 *
 * This is the default for {@code new Blockchain()}, so every existing test and
 * call site runs exactly as before. Persistence is opt-in (the runnable node
 * turns it on).
 */
public final class NoOpChainStore implements ChainStore {

    /** Shared instance; the store has no state. */
    public static final NoOpChainStore INSTANCE = new NoOpChainStore();

    private NoOpChainStore() {}

    @Override
    public void saveBlock(Block block) {}

    @Override
    public List<Block> loadBlocks() {
        return List.of();
    }

    @Override
    public void savePending(Transaction transaction) {}

    @Override
    public void removePending(String transactionId) {}

    @Override
    public List<Transaction> loadPending() {
        return List.of();
    }

    @Override
    public void close() {}
}
