package com.blocksmith.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.core.Blockchain;
import com.blocksmith.core.Transaction;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.SignatureUtil;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for multisig contracts (Milestone 15b): an M-of-N multisig is a
 * contract whose lock is a CHECKMULTISIG, spent by presenting M signatures over
 * the claim sighash. Reuses the Sprint 14 deploy/claim/registry unchanged, so
 * these tests focus on the signature semantics and replay safety.
 */
@DisplayName("Multisig Contract Tests")
class MultiSigContractTest {

    private static final String FUNDER = "funder-address";
    private static final String CLAIMER = "claimer-address";
    private static final String MINER = "miner-address";

    private Blockchain blockchain;
    private KeyPair k1, k2, k3;

    @BeforeEach
    void setUp() {
        blockchain = new Blockchain();
        // Fund the funder with a mining reward (50 BSC).
        blockchain.minePendingTransactions(FUNDER);

        k1 = newKeyPair();
        k2 = newKeyPair();
        k3 = newKeyPair();
    }

    @Test
    @DisplayName("2-of-3: a claim with two valid signatures credits the claimer")
    void twoOfThree_claimWithTwoSignatures_creditsClaimer() {
        String id = deployMultisig(2, 20);

        String unlocking = signatures(id, CLAIMER, k1, k2);
        Transaction claim = blockchain.claimContract(id, CLAIMER, unlocking);
        assertNotNull(claim, "Two valid member signatures should unlock the contract");

        blockchain.minePendingTransactions(MINER);

        assertEquals(20, blockchain.getBalance(CLAIMER), 0.0001);
        assertEquals(ContractStatus.CLAIMED, blockchain.getContract(id).getStatus());
        assertEquals(CLAIMER, blockchain.getContract(id).getClaimer());
    }

    @Test
    @DisplayName("2-of-3: a single signature is below the threshold and rejected")
    void claimWithOneSignature_rejected() {
        String id = deployMultisig(2, 20);

        String unlocking = signatures(id, CLAIMER, k1);
        assertNull(blockchain.claimContract(id, CLAIMER, unlocking),
                "One signature cannot satisfy a 2-of-3 lock");

        assertEquals(0, blockchain.getBalance(CLAIMER), 0.0001);
        assertEquals(ContractStatus.OPEN, blockchain.getContract(id).getStatus());
        assertEquals(0, blockchain.getPendingCount());
    }

    @Test
    @DisplayName("2-of-3: a signature from a non-member key is rejected")
    void claimWithWrongKeySignature_rejected() {
        String id = deployMultisig(2, 20);

        // One valid member signature plus one from a key not in the set.
        KeyPair outsider = newKeyPair();
        String unlocking = signatures(id, CLAIMER, k1, outsider);
        assertNull(blockchain.claimContract(id, CLAIMER, unlocking),
                "A non-member signature must not count toward the threshold");

        assertEquals(0, blockchain.getBalance(CLAIMER), 0.0001);
        assertEquals(ContractStatus.OPEN, blockchain.getContract(id).getStatus());
    }

    @Test
    @DisplayName("Signatures authorizing one claimer cannot be replayed for another")
    void signatureReplayToDifferentClaimer_rejected() {
        String id = deployMultisig(2, 20);

        // Two valid signatures, but signed over CLAIMER's sighash.
        String unlocking = signatures(id, CLAIMER, k1, k2);

        // Presenting them to pay a DIFFERENT claimer changes the sighash, so
        // the signatures no longer verify.
        assertNull(blockchain.claimContract(id, "0xcarol", unlocking),
                "A signature for one claimer must not authorize another");

        assertEquals(0, blockchain.getBalance("0xcarol"), 0.0001);
        assertEquals(ContractStatus.OPEN, blockchain.getContract(id).getStatus());

        // The intended claimer can still spend with the same signatures.
        assertNotNull(blockchain.claimContract(id, CLAIMER, unlocking));
    }

    @Test
    @DisplayName("External blocks build the same multisig state (network convergence)")
    void externalBlocks_buildSameMultisigState() {
        Blockchain nodeB = new Blockchain();

        String id = deployMultisig(2, 20);
        blockchain.claimContract(id, CLAIMER, signatures(id, CLAIMER, k1, k2));
        blockchain.minePendingTransactions(MINER);

        // Replay A's mined blocks (skip the shared genesis) onto B.
        for (int i = 1; i < blockchain.getChainSize(); i++) {
            assertTrue(nodeB.addBlock(blockchain.getBlock(i)),
                    "Block " + i + " should append cleanly on node B");
        }

        Contract onB = nodeB.getContract(id);
        assertNotNull(onB, "Node B should derive the multisig contract from the blocks");
        assertEquals(ContractStatus.CLAIMED, onB.getStatus());
        assertEquals(CLAIMER, onB.getClaimer());
        assertEquals(20, nodeB.getBalance(CLAIMER), 0.0001);
    }

    // ===== HELPERS =====

    /**
     * Deploys an M-of-3 multisig over {@code k1,k2,k3} and mines it OPEN.
     *
     * @return the contract id
     */
    private String deployMultisig(int threshold, double amount) {
        MultiSigWallet wallet = MultiSigWallet.ofPublicKeys(
                List.of(k1.getPublic(), k2.getPublic(), k3.getPublic()), threshold);
        Transaction deploy = blockchain.deployContract(FUNDER, wallet.getLockingScript(), amount);
        assertNotNull(deploy, "Deploy within balance should be accepted");
        blockchain.minePendingTransactions(MINER);
        return deploy.getRecipient()
                .substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length());
    }

    /**
     * Builds an unlocking script of signatures over the claim's sighash, in the
     * given key order: {@code PUSH sig1 PUSH sig2 ...}.
     */
    private String signatures(String contractId, String claimer, KeyPair... signers) {
        String sighash = blockchain.getContract(contractId).claimSighash(claimer);
        StringBuilder unlocking = new StringBuilder();
        for (KeyPair signer : signers) {
            if (unlocking.length() > 0) unlocking.append(' ');
            unlocking.append("PUSH ").append(sign(signer.getPrivate(), sighash));
        }
        return unlocking.toString();
    }

    private String sign(PrivateKey privateKey, String message) {
        return SignatureUtil.sign(privateKey, message);
    }

    private KeyPair newKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(new ECGenParameterSpec("secp256r1"));
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate key pair", e);
        }
    }
}
