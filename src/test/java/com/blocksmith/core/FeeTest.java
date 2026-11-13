package com.blocksmith.core;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.HashUtil;
import com.blocksmith.util.JsonUtil;
import com.blocksmith.util.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for transaction fees (Milestone 18c): who pays, who collects, what
 * the coinbase may claim, and which transactions a full block keeps.
 */
@DisplayName("Fee Tests")
class FeeTest {

    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String MINER = "miner";
    private static final double REWARD = BlockchainConfig.MINING_REWARD;

    private static ChainParams params(int maxTxs) {
        return new ChainParams(Target.fromLeadingZeros(1), 10_000, 10, 4, Target.MAX,
                ChainParams.DEFAULT_MAX_FUTURE_DRIFT_MS, ChainParams.DEFAULT_MAX_BLOCK_BYTES, maxTxs);
    }

    private static Blockchain chain(int maxTxs) {
        Blockchain c = new Blockchain(NoOpChainStore.INSTANCE, params(maxTxs));
        c.minePendingTransactions(ALICE);                     // Alice has 50
        return c;
    }

    private static Block blockOn(Blockchain c, List<Transaction> txs) {
        Block tip = c.getLatestBlock();
        Block b = new Block(tip.getIndex() + 1, txs, tip.getHash(),
                Math.max(System.currentTimeMillis(), tip.getTimestamp() + 1));
        b.mineBlock(c.getNextTarget());
        return b;
    }

    // ===== ID AND SIGNATURE =====

    @Test
    @DisplayName("A zero fee keeps the old id and signature; a fee changes both and is protected")
    void fee_inIdAndSignature_zeroFeeCompatible() {
        Transaction noFee = new Transaction(ALICE, BOB, 10);
        assertEquals(HashUtil.applySha256(ALICE + BOB + 10.0 + noFee.getTimestamp()), noFee.getTransactionId(),
                "Zero fee: the pre-18c id formula");
        assertEquals(ALICE + BOB + 10.0 + noFee.getTimestamp(), noFee.getSignatureData());

        Wallet wallet = new Wallet();
        Transaction withFee = new Transaction(wallet.getAddress(), BOB, 10, 0.5);
        wallet.signTransaction(withFee);
        assertTrue(withFee.hasValidId());
        assertTrue(withFee.verifySignature());

        // A relay raising the fee (to drain the sender) breaks the id and the signature.
        String json = JsonUtil.GSON.toJson(withFee).replace("\"fee\":0.5", "\"fee\":40.0");
        Transaction raised = JsonUtil.GSON.fromJson(json, Transaction.class);
        assertEquals(40.0, raised.getFee());
        assertFalse(raised.hasValidId());
        assertFalse(raised.verifySignature());
    }

    @Test
    @DisplayName("Old JSON without a fee field reads as fee 0 with a valid id")
    void legacyJson_withoutFee_isFeeZero() {
        Transaction tx = new Transaction(ALICE, BOB, 10);
        String legacy = JsonUtil.GSON.toJson(tx).replace(",\"fee\":0.0", "");
        assertFalse(legacy.contains("\"fee\""), "No fee key (the hex id itself may contain \"fee\")");
        Transaction back = JsonUtil.GSON.fromJson(legacy, Transaction.class);
        assertEquals(0.0, back.getFee());
        assertTrue(back.hasValidId());
    }

    @Test
    @DisplayName("Negative or non-numeric fees are invalid")
    void badFees_invalid() {
        assertFalse(new Transaction(ALICE, BOB, 10, -1).isValid());
        assertFalse(new Transaction(ALICE, BOB, 10, Double.NaN).isValid());
        assertFalse(new Transaction(ALICE, BOB, 10, Double.POSITIVE_INFINITY).isValid());
        assertTrue(new Transaction(ALICE, BOB, 10, 0).isValid());
    }

    // ===== WHO PAYS, WHO COLLECTS =====

    @Test
    @DisplayName("The sender pays amount + fee, the recipient gets the amount, the miner the fee")
    void sender_paysAmountPlusFee_minerCollects() {
        Blockchain c = chain(50);
        assertFalse(c.addTransaction(new Transaction(ALICE, BOB, 49, 2)), "49 + 2 > 50");
        assertTrue(c.addTransaction(new Transaction(ALICE, BOB, 48, 2)), "48 + 2 = 50");
        assertFalse(c.addTransaction(new Transaction(ALICE, BOB, 0.01, 0)), "Nothing left: the fee counts as pending");

        Block mined = c.minePendingTransactions(MINER);

        assertEquals(REWARD + 2, mined.getTransactions().get(0).getAmount(), 1e-9);
        assertEquals(0.0, c.getBalance(ALICE), 1e-9);
        assertEquals(48.0, c.getBalance(BOB), 1e-9);
        assertEquals(REWARD + 2, c.getBalance(MINER), 1e-9);
        assertTrue(c.isChainValid());
    }

    // ===== COINBASE =====

    @Test
    @DisplayName("A coinbase may claim reward + fees, not a cent more")
    void coinbase_overRewardPlusFees_rejected() {
        Blockchain c = chain(50);
        Transaction t1 = new Transaction(ALICE, BOB, 10, 0.3);
        Transaction t2 = new Transaction(ALICE, BOB, 10, 0.2);
        double fees = 0.3 + 0.2;

        Transaction greedy = new Transaction(BlockchainConfig.COINBASE_ADDRESS, MINER, REWARD + fees + 0.01);
        assertFalse(c.addBlock(blockOn(c, List.of(greedy, t1, t2))));

        Transaction fair = new Transaction(BlockchainConfig.COINBASE_ADDRESS, MINER, REWARD + fees);
        assertTrue(c.addBlock(blockOn(c, List.of(fair, t1, t2))));
        assertEquals(REWARD + fees, c.getBalance(MINER), 1e-9);
    }

    @Test
    @DisplayName("A peer block where fees overdraw the sender is rejected")
    void feesCountTowardOverspend() {
        Blockchain c = chain(50);
        Transaction cb = new Transaction(BlockchainConfig.COINBASE_ADDRESS, MINER, REWARD + 1);
        assertFalse(c.addBlock(blockOn(c, List.of(cb, new Transaction(ALICE, BOB, 50, 1)))), "50 + 1 > 50");
    }

    // ===== FEE PRIORITY =====

    @Test
    @DisplayName("A full block keeps the best fee per byte; the rest waits, in fee order")
    void fullBlock_keepsHighestFeeRate() {
        Blockchain c = chain(3);                              // coinbase + 2 per block
        Transaction f0 = new Transaction(ALICE, BOB, 1, 0);
        Transaction f5 = new Transaction(ALICE, BOB, 1.1, 0.5);
        Transaction f1 = new Transaction(ALICE, BOB, 1.2, 0.1);
        Transaction f3 = new Transaction(ALICE, BOB, 1.3, 0.3);
        for (Transaction tx : List.of(f0, f5, f1, f3)) assertTrue(c.addTransaction(tx));

        Block first = c.minePendingTransactions(MINER);
        assertEquals(List.of(f5.getTransactionId(), f3.getTransactionId()), ids(first));
        assertEquals(REWARD + 0.8, first.getTransactions().get(0).getAmount(), 1e-9);

        Block second = c.minePendingTransactions(MINER);
        assertEquals(List.of(f1.getTransactionId(), f0.getTransactionId()), ids(second));
        assertEquals(0, c.getPendingCount());
        assertTrue(c.isChainValid());
    }

    @Test
    @DisplayName("Fee rate is per byte: a bigger signed tx needs a bigger fee to win the last slot")
    void feeRate_perByte() {
        Wallet wallet = new Wallet();
        Blockchain c = new Blockchain(NoOpChainStore.INSTANCE, params(2));   // coinbase + 1
        c.minePendingTransactions(wallet.getAddress());
        c.minePendingTransactions(ALICE);

        Transaction plain = new Transaction(ALICE, BOB, 1, 0.4);            // ~245 bytes
        Transaction signed = new Transaction(wallet.getAddress(), BOB, 1, 0.5);
        wallet.signTransaction(signed);                                      // ~700 bytes
        assertTrue(signed.sizeBytes() > 2 * plain.sizeBytes());
        assertTrue(plain.feeRate() > signed.feeRate(), "Smaller fee, but more per byte");

        assertTrue(c.addTransaction(signed));
        assertTrue(c.addTransaction(plain));
        Block mined = c.minePendingTransactions(MINER);
        assertEquals(List.of(plain.getTransactionId()), ids(mined));
    }

    @Test
    @DisplayName("Equal fee rates keep arrival order")
    void equalFees_keepArrivalOrder() {
        Blockchain c = chain(50);
        List<Transaction> sent = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            // distinct amounts: identical txs in the same millisecond share an id
            Transaction tx = new Transaction(ALICE, BOB, 1 + i * 0.01);
            sent.add(tx);
            assertTrue(c.addTransaction(tx));
        }
        Block mined = c.minePendingTransactions(MINER);
        assertEquals(sent.stream().map(Transaction::getTransactionId).toList(), ids(mined));
    }

    private static List<String> ids(Block block) {
        return block.getTransactions().subList(1, block.getTransactions().size()).stream()
                .map(Transaction::getTransactionId).collect(Collectors.toList());
    }
}
