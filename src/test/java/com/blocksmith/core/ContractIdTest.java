package com.blocksmith.core;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.contract.Contract;
import com.blocksmith.contract.ContractStatus;
import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.HashUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A contract's id is bound to the deploy that creates it (Milestone 25a):
 * SHA-256(funder | lockingScript | amount | sequence), recomputed by every
 * node. The planning probe: Mallory copied Alice's pending deploy address and
 * deployed to it first, leaving Alice's 30 stuck behind Mallory's contract.
 * Real network parameters (signatures required).
 */
@DisplayName("Contract Id Tests")
class ContractIdTest {

    private static final String SECRET = "alice-secret";
    private static final String HASHLOCK = "SHA256 PUSH " + HashUtil.applySha256(SECRET) + " EQUAL";

    private final Wallet alice = new Wallet();
    private final Wallet mallory = new Wallet();

    private Blockchain fundedChain() {
        Blockchain c = new Blockchain(NoOpChainStore.INSTANCE, ChainParams.DEFAULT);
        c.minePendingTransactions(alice.getAddress());
        c.minePendingTransactions(mallory.getAddress());
        return c;
    }

    private static Block blockOn(Blockchain c, List<Transaction> txs) {
        Block tip = c.getLatestBlock();
        Block b = new Block(tip.getIndex() + 1, txs, tip.getHash(), Math.max(System.currentTimeMillis(), tip.getTimestamp() + 1));
        b.mineBlock(c.getNextTarget());
        return b;
    }

    private static Transaction coinbase(String to) {
        return new Transaction(BlockchainConfig.COINBASE_ADDRESS, to, BlockchainConfig.MINING_REWARD);
    }

    private Transaction aliceDeploy() {
        Transaction d = Blockchain.buildDeploy(alice.getAddress(), HASHLOCK, 30);
        alice.signTransaction(d);
        return d;
    }

    /** Mallory's deploy to {@code address}: 0.1, an always-true lock, a high fee, her own signature. */
    private Transaction malloryDeployTo(String address, Long sequence) {
        Transaction d = new Transaction(mallory.getAddress(), address, 0.1, 1.0, sequence);
        d.setLockingScript("PUSH 1");
        mallory.signTransaction(d);
        return d;
    }

    private static String idOf(Transaction deploy) {
        return deploy.getRecipient().substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length());
    }

    @Test
    @DisplayName("The probe: Mallory's deploy to Alice's pending contract address is refused; Alice claims her 30")
    void frontRunDeploy_refused() {
        Blockchain c = fundedChain();
        Transaction deploy = aliceDeploy();
        assertNull(c.submitTransaction(deploy));

        String target = c.getPendingTransactions().get(0).getRecipient();         // copied from the mempool
        String why = c.submitTransaction(malloryDeployTo(target, Transaction.randomSequence()));
        assertNotNull(why);
        assertTrue(why.contains("contract id is not this deploy's own"), why);
        assertEquals(1, c.getPendingCount());

        c.minePendingTransactions("miner");
        Contract contract = c.getContract(idOf(deploy));
        assertEquals(alice.getAddress(), contract.getFunder());
        assertEquals(30.0, contract.getAmount());
        assertNotNull(c.claimContract(idOf(deploy), "bob", "PUSH " + SECRET));
        c.minePendingTransactions("miner");
        assertEquals(30.0, c.getBalance("bob"), 1e-9);
        assertEquals(0.0, c.getBalance(deploy.getRecipient()), 1e-9, "Nothing stuck at the contract address");
    }

    @Test
    @DisplayName("A miner cannot slip the front-run into a block either (before or after Alice's deploy)")
    void frontRunDeploy_inBlock_refused() {
        Blockchain c = fundedChain();
        Transaction deploy = aliceDeploy();
        Transaction front = malloryDeployTo(deploy.getRecipient(), Transaction.randomSequence());
        assertFalse(c.addBlock(blockOn(c, List.of(coinbase("miner"), front, deploy))));
        assertFalse(c.addBlock(blockOn(c, List.of(coinbase("miner"), deploy, front))));
        assertTrue(c.addBlock(blockOn(c, List.of(coinbase("miner"), deploy))), "Alice's deploy alone is fine");
        assertEquals(alice.getAddress(), c.getContract(idOf(deploy)).getFunder());
    }

    @Test
    @DisplayName("A deploy without a sequence, or with an id that is not its own, is refused")
    void deployWithoutSequenceOrWrongId_refused() {
        Blockchain c = fundedChain();

        Transaction noSequence = new Transaction(alice.getAddress(), BlockchainConfig.CONTRACT_ADDRESS_PREFIX + "c0ffee", 5);
        noSequence.setLockingScript(HASHLOCK);
        alice.signTransaction(noSequence);
        assertTrue(c.submitTransaction(noSequence).contains("needs a sequence"));

        long seq = 42L;
        String idForAnotherAmount = Blockchain.contractIdFor(alice.getAddress(), HASHLOCK, 6, seq);
        Transaction wrongId = new Transaction(alice.getAddress(), BlockchainConfig.CONTRACT_ADDRESS_PREFIX + idForAnotherAmount, 5, 0, seq);
        wrongId.setLockingScript(HASHLOCK);
        alice.signTransaction(wrongId);
        assertTrue(c.submitTransaction(wrongId).contains("not this deploy's own"));

        String own = Blockchain.contractIdFor(alice.getAddress(), HASHLOCK, 5, seq);
        Transaction right = new Transaction(alice.getAddress(), BlockchainConfig.CONTRACT_ADDRESS_PREFIX + own, 5, 0, seq);
        right.setLockingScript(HASHLOCK);
        alice.signTransaction(right);
        assertNull(c.submitTransaction(right), "Built by hand, but with its own id: fine");
        assertFalse(c.addBlock(blockOn(c, List.of(coinbase("miner"), noSequence))), "Blocks apply the same rule");
    }

    @Test
    @DisplayName("A fee bump keeps the sequence, so the bumped deploy keeps its contract id")
    void bumpedDeploy_keepsContractId() {
        Blockchain c = fundedChain();
        Transaction deploy = aliceDeploy();
        assertNull(c.submitTransaction(deploy));
        Transaction bump = deploy.withFee(0.5);
        alice.signTransaction(bump);
        assertNull(c.submitTransaction(bump));
        assertEquals(deploy.getRecipient(), bump.getRecipient());
        assertEquals(List.of(bump.getTransactionId()), c.getPendingTransactions().stream().map(Transaction::getTransactionId).toList());

        c.minePendingTransactions("miner");
        assertEquals(ContractStatus.OPEN, c.getContract(idOf(deploy)).getStatus());
        assertEquals(50 - 30 - 0.5, c.getBalance(alice.getAddress()), 1e-9, "Paid the bumped fee once");
    }

    @Test
    @DisplayName("The id is reproducible from the deploy; two deploys get two ids")
    void contractId_reproducibleAndUnique() {
        Transaction a = Blockchain.buildDeploy(alice.getAddress(), HASHLOCK, 30);
        assertEquals(idOf(a), Blockchain.contractIdFor(alice.getAddress(), HASHLOCK, 30, a.getSequence()));
        assertNull(Blockchain.deployIdViolation(a));
        assertNotEquals(idOf(a), idOf(Blockchain.buildDeploy(alice.getAddress(), HASHLOCK, 30)), "Random sequence");
        assertNotEquals(idOf(a), Blockchain.contractIdFor(mallory.getAddress(), HASHLOCK, 30, a.getSequence()),
                "Another funder, another id");
    }
}
