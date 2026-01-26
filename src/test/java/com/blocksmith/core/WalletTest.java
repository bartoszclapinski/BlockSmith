package com.blocksmith.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Wallet class.
 */

public class WalletTest {

    // ===== KEY PAIR GENERATION TESTS =====

    @Test
    @DisplayName("Wallet should generate key pair on creation")
    void constructorShouldGenerateKeyPair() {
        Wallet wallet = new Wallet();
        // We can't directly test the private key, but we can test the public key.
        assertNotNull(wallet.getPublicKey(), "Public key should not be null");
    }

    @Test
    @DisplayName("Public key should be EC type")
    void publicKeyShouldBeECType() {
        Wallet wallet = new Wallet();
        String algorithm = wallet.getPublicKey().getAlgorithm();
        assertEquals("EC", algorithm, "Public key should be EC type");    
    }

    @Test
    @DisplayName("Different wallets should have different keys")
    void differentWalletsShouldHaveDifferentKeys() {
        Wallet wallet1 = new Wallet();
        Wallet wallet2 = new Wallet();
        assertNotEquals(wallet1.getPublicKey(), wallet2.getPublicKey(),
            "Different wallets should have different keys");    
    }

    // ===== ADDRESS GENERATION TESTS =====

    @Test
    @DisplayName("Address should start with 0x")
    void getAddress_newWallet_startsWithOx() {        
        Wallet wallet = new Wallet();       
        String address = wallet.getAddress();
       
        assertTrue(address.startsWith("0x"), "Address should start with '0x'");
    }

    @Test
    @DisplayName("Address should be 42 characters long (0x + 40 hex)")
    void getAddress_newWallet_correctLength() {        
        Wallet wallet = new Wallet();        
        String address = wallet.getAddress();
        
        assertEquals(42, address.length(), "Address should be 42 characters (0x + 40 hex)");
    }

    @Test
    @DisplayName("Address should only contain hex characters after 0x")
    void getAddress_newWallet_onlyHexCharacters() {        
        Wallet wallet = new Wallet();        
        String address = wallet.getAddress();
        String hexPart = address.substring(2); // Remove "0x"        
        
        assertTrue(
            hexPart.matches("[0-9a-f]+"),
            "Address should only contain hex characters (0-9, a-f)"
        );
    }

    @Test
    @DisplayName("Different wallets should have different addresses")
    void getAddress_multipleWallets_differentAddresses() {
        Wallet wallet1 = new Wallet();
        Wallet wallet2 = new Wallet();
        
        assertNotEquals(
            wallet1.getAddress(),
            wallet2.getAddress(),
            "Different wallets should have different addresses"
        );
    }

    @Test
    @DisplayName("Same wallet should always return same address")
    void getAddress_sameWallet_consistentAddress() {
        Wallet wallet = new Wallet();
        String address1 = wallet.getAddress();
        String address2 = wallet.getAddress();

        assertEquals(address1, address2, "Same wallet should always return same address");
    }

    // ===== TRANSACTION SIGNING TESTS =====

    @Test
    @DisplayName("Wallet should sign transaction when sender matches")
    void signTransaction_validSender_signsTransaction() {
        Wallet wallet = new Wallet();
        Transaction tx = new Transaction(wallet.getAddress(), "recipient123", 50.0);

        wallet.signTransaction(tx);

        assertNotNull(tx.getSignature(), "Transaction should have a signature");
        assertNotNull(tx.getSenderPublicKey(), "Transaction should have sender's public key");
    }

    @Test
    @DisplayName("Signature should be non-empty bytes")
    void signTransaction_validTransaction_signatureHasBytes() {
        Wallet wallet = new Wallet();
        Transaction tx = new Transaction(wallet.getAddress(), "recipient123", 50.0);

        wallet.signTransaction(tx);

        assertTrue(tx.getSignature().length > 0, "Signature should have bytes");
    }

    @Test
    @DisplayName("Should throw exception when signing for wrong sender")
    void signTransaction_wrongSender_throwsException() {
        Wallet wallet = new Wallet();
        Transaction tx = new Transaction("someOtherAddress", "recipient123", 50.0);

        assertThrows(
            IllegalStateException.class,
            () -> wallet.signTransaction(tx),
            "Should throw exception when wallet address doesn't match sender"
        );
    }

    @Test
    @DisplayName("Different transactions should have different signatures")
    void signTransaction_differentTransactions_differentSignatures() {
        Wallet wallet = new Wallet();
        Transaction tx1 = new Transaction(wallet.getAddress(), "recipient1", 50.0);
        Transaction tx2 = new Transaction(wallet.getAddress(), "recipient2", 100.0);

        wallet.signTransaction(tx1);
        wallet.signTransaction(tx2);

        assertFalse(
            java.util.Arrays.equals(tx1.getSignature(), tx2.getSignature()),
            "Different transactions should have different signatures"
        );
    }

    @Test
    @DisplayName("Sender public key should match wallet public key")
    void signTransaction_signed_publicKeyMatchesWallet() {
        Wallet wallet = new Wallet();
        Transaction tx = new Transaction(wallet.getAddress(), "recipient123", 50.0);

        wallet.signTransaction(tx);

        assertEquals(
            wallet.getPublicKey(),
            tx.getSenderPublicKey(),
            "Transaction's public key should match wallet's public key"
        );
    }
        
}
