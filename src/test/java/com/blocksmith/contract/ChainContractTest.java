package com.blocksmith.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.core.Blockchain;
import com.blocksmith.core.Transaction;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.HashUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for contracts on the chain (Milestone 14b): deploy locks funds,
 * claims run through the script VM, state converges from mined blocks, and
 * every invalid path is rejected without side effects.
 */
@DisplayName("Chain Contract Tests")
class ChainContractTest {

    private static final String FUNDER = "funder-address";
    private static final String CLAIMER = "claimer-address";
    private static final String MINER = "miner-address";

    private static final String SECRET = "open-sesame";
    private static final String HASHLOCK =
            "SHA256 PUSH " + HashUtil.applySha256(SECRET) + " EQUAL";

    private Blockchain blockchain;

    @BeforeEach
    void setUp() {
        blockchain = new Blockchain();
        // Fund the funder with a mining reward (50 BSC).
        blockchain.minePendingTransactions(FUNDER);
    }

    /** Extracts the contract id from a deploy transaction's recipient. */
    private static String contractIdOf(Transaction deploy) {
        return deploy.getRecipient()
                .substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length());
    }

    @Test
    @DisplayName("Deploy locks the funder's balance once mined")
    void deploy_locksFunderBalance() {
        Transaction deploy = blockchain.deployContract(FUNDER, HASHLOCK, 20);
        assertNotNull(deploy, "Deploy within balance should be accepted");

        // Not yet mined: no contract, funds still pending.
        assertNull(blockchain.getContract(contractIdOf(deploy)));

        blockchain.minePendingTransactions(MINER);

        Contract contract = blockchain.getContract(contractIdOf(deploy));
        assertNotNull(contract, "Mined deploy should open the contract");
        assertEquals(ContractStatus.OPEN, contract.getStatus());
        assertEquals(20, contract.getAmount(), 0.0001);
        assertEquals(FUNDER, contract.getFunder());
        assertEquals(30, blockchain.getBalance(FUNDER), 0.0001,
                "Funder should be debited the locked amount");
    }

    @Test
    @DisplayName("Deploy beyond the funder's balance is rejected")
    void deploy_beyondBalance_rejected() {
        assertNull(blockchain.deployContract(FUNDER, HASHLOCK, 60),
                "Funder only has 50");
        assertEquals(0, blockchain.getPendingCount());
    }

    @Test
    @DisplayName("Plain transfer to a contract address is rejected (no script = burned funds)")
    void plainTransferToContractAddress_rejected() {
        Transaction burn = new Transaction(
                FUNDER, BlockchainConfig.CONTRACT_ADDRESS_PREFIX + "deadbeef", 5);
        assertFalse(blockchain.addTransaction(burn));
    }

    @Test
    @DisplayName("Claim with the correct preimage credits the claimer")
    void claim_withCorrectPreimage_creditsClaimer() {
        Transaction deploy = blockchain.deployContract(FUNDER, HASHLOCK, 20);
        blockchain.minePendingTransactions(MINER);
        String id = contractIdOf(deploy);

        Transaction claim = blockchain.claimContract(id, CLAIMER, "PUSH " + SECRET);
        assertNotNull(claim, "Correct preimage should unlock the contract");

        blockchain.minePendingTransactions(MINER);

        assertEquals(20, blockchain.getBalance(CLAIMER), 0.0001);
        assertEquals(ContractStatus.CLAIMED, blockchain.getContract(id).getStatus());
        assertEquals(CLAIMER, blockchain.getContract(id).getClaimer());
    }

    @Test
    @DisplayName("Claim with a wrong preimage is rejected without side effects")
    void claim_withWrongPreimage_rejected() {
        Transaction deploy = blockchain.deployContract(FUNDER, HASHLOCK, 20);
        blockchain.minePendingTransactions(MINER);
        String id = contractIdOf(deploy);

        assertNull(blockchain.claimContract(id, CLAIMER, "PUSH wrong-word"));

        assertEquals(0, blockchain.getBalance(CLAIMER), 0.0001);
        assertEquals(ContractStatus.OPEN, blockchain.getContract(id).getStatus());
        assertEquals(0, blockchain.getPendingCount(), "No claim should be pending");
    }

    @Test
    @DisplayName("Timelock claim: rejected before height N, accepted after")
    void timelockClaim_beforeHeight_rejected_afterHeight_accepted() {
        // Chain: genesis(0) + funding(1). Deploy will be mined into block 2.
        Transaction deploy = blockchain.deployContract(FUNDER, "PUSH 5 CHECKLOCKTIME", 15);
        blockchain.minePendingTransactions(MINER);
        String id = contractIdOf(deploy);

        // Next block would be #3 - too early for height 5.
        assertNull(blockchain.claimContract(id, CLAIMER, ""),
                "Claim should be rejected before the lock height");

        // Mine empty-pool blocks (coinbase only) until the next block is #5.
        blockchain.minePendingTransactions(MINER); // 3
        blockchain.minePendingTransactions(MINER); // 4

        Transaction claim = blockchain.claimContract(id, CLAIMER, "");
        assertNotNull(claim, "Claim should be accepted once the chain is tall enough");

        blockchain.minePendingTransactions(MINER);
        assertEquals(15, blockchain.getBalance(CLAIMER), 0.0001);
    }

    @Test
    @DisplayName("Double claim is rejected - both pending and after mining")
    void doubleClaim_rejected() {
        Transaction deploy = blockchain.deployContract(FUNDER, HASHLOCK, 20);
        blockchain.minePendingTransactions(MINER);
        String id = contractIdOf(deploy);

        // First claim enters the pool; a second pending claim finds no
        // available funds (pending-outgoing check).
        assertNotNull(blockchain.claimContract(id, CLAIMER, "PUSH " + SECRET));
        assertNull(blockchain.claimContract(id, "someone-else", "PUSH " + SECRET),
                "Second pending claim should be rejected");

        blockchain.minePendingTransactions(MINER);

        // After mining the contract is CLAIMED - further claims are rejected.
        assertNull(blockchain.claimContract(id, "someone-else", "PUSH " + SECRET),
                "Claim on a CLAIMED contract should be rejected");
        assertEquals(20, blockchain.getBalance(CLAIMER), 0.0001);
        assertEquals(0, blockchain.getBalance("someone-else"), 0.0001);
    }

    @Test
    @DisplayName("Claim before the deploy is mined is rejected")
    void claim_beforeDeployMined_rejected() {
        Transaction deploy = blockchain.deployContract(FUNDER, HASHLOCK, 20);
        assertNull(blockchain.claimContract(contractIdOf(deploy), CLAIMER, "PUSH " + SECRET),
                "Contract does not exist until the deploy is mined");
    }

    @Test
    @DisplayName("External blocks build the same contract state (network convergence)")
    void externalBlocks_buildSameContractState() {
        // Node A mines the full lifecycle; node B replays A's blocks.
        Blockchain nodeB = new Blockchain();

        Transaction deploy = blockchain.deployContract(FUNDER, HASHLOCK, 20);
        blockchain.minePendingTransactions(MINER);
        String id = contractIdOf(deploy);
        blockchain.claimContract(id, CLAIMER, "PUSH " + SECRET);
        blockchain.minePendingTransactions(MINER);

        // Replay A's mined blocks (skip the shared genesis) onto B.
        for (int i = 1; i < blockchain.getChainSize(); i++) {
            assertTrue(nodeB.addBlock(blockchain.getBlock(i)),
                    "Block " + i + " should append cleanly on node B");
        }

        Contract onB = nodeB.getContract(id);
        assertNotNull(onB, "Node B should derive the contract from the blocks");
        assertEquals(ContractStatus.CLAIMED, onB.getStatus());
        assertEquals(CLAIMER, onB.getClaimer());
        assertEquals(20, nodeB.getBalance(CLAIMER), 0.0001);
    }
}
