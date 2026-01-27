package com.blocksmith.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Transaction class.
 * 
 * TEST CATEGORIES:
 * 1. Creation - proper initialization of fields
 * 2. Hash/ID - uniqueness and consistency
 * 3. Validation - isValid() method rules
 * 4. Edge cases - boundary conditions
 */

public class TransactionTest {

    // ===== CREATION TESTS =====

    @Test
    @DisplayName("Transaction should initialize all fields correctly")
    void transactionShouldInitializeAllFieldsCorrectly() {
        Transaction tx = new Transaction("Alice", "Bob", 50.0);

        assertEquals("Alice", tx.getSender(), "Sender should be Alice");
        assertEquals("Bob", tx.getRecipient(), "Recipient should be Bob");
        assertEquals(50.0, tx.getAmount(), "Amount should be 50.0");
        assertNotNull(tx.getTransactionId(), "Transaction ID should not be null");
        assertTrue(tx.getTimestamp() > 0, "Timestamp should be greater than 0");
    }

    @Test
    @DisplayName("Transaction ID should be 64 characters (SHA-256 hex)")
    void transactionIdShouldBe64Characters() {
        Transaction tx = new Transaction("Alice", "Bob", 100.0);
        
        assertEquals(64, tx.getTransactionId().length(), 
            "Transaction ID should be 64 characters");
    }
    
    @Test
    @DisplayName("Transaction ID should only contain hex characters")
    void transactionIdShouldBeHexadecimal() {
        Transaction tx = new Transaction("Alice", "Bob", 100.0);
        
        assertTrue(tx.getTransactionId().matches("[0-9a-f]+"), 
            "Transaction ID should only contain hex characters");
    }

    // ===== UNIQUENESS TESTS =====

    @Test
    @DisplayName("Different transactions should have different IDs")
    void differentTransactionsShouldHaveDifferentIds() {
        Transaction tx1 = new Transaction("Alice", "Bob", 50.0);
        Transaction tx2 = new Transaction("Alice", "Charlie", 50.0);
        Transaction tx3 = new Transaction("Dave", "Bob", 50.0);
        Transaction tx4 = new Transaction("Alice", "Bob", 75.0);
        
        assertNotEquals(tx1.getTransactionId(), tx2.getTransactionId(), 
            "Transaction IDs should be different");
        assertNotEquals(tx1.getTransactionId(), tx3.getTransactionId(), 
            "Transaction IDs should be different");
        assertNotEquals(tx1.getTransactionId(), tx4.getTransactionId(), 
            "Transaction IDs should be different");
        
    }
    
    @Test
    @DisplayName("Same parameters at different times should have different IDs")
    void sameParametersAtDifferentTimesShouldHaveDifferentIds() throws InterruptedException {
        Transaction tx1 = new Transaction("Alice", "Bob", 50.0);
        Thread.sleep(10); // Ensure different timestamp
        Transaction tx2 = new Transaction("Alice", "Bob", 50.0);
        
        assertNotEquals(tx1.getTransactionId(), tx2.getTransactionId(), 
            "Transaction IDs should be different");
    }
    
    // ===== VALIDATION TESTS =====

    @Test
    @DisplayName("Valid transaction should pass validation")
    void validTransactionShouldPassValidation() {
        Transaction tx = new Transaction("Alice", "Bob", 50.0);
        
        assertTrue(tx.isValid());
    }
    
    @Test
    @DisplayName("Transaction with zero amount should be invalid")
    void zeroAmountShouldBeInvalid() {
        Transaction tx = new Transaction("Alice", "Bob", 0.0);
        
        assertFalse(tx.isValid());
    }
    
    @Test
    @DisplayName("Transaction with negative amount should be invalid")
    void negativeAmountShouldBeInvalid() {
        Transaction tx = new Transaction("Alice", "Bob", -50.0);
        
        assertFalse(tx.isValid());
    }
    
    @Test
    @DisplayName("Transaction with null sender should be invalid")
    void nullSenderShouldBeInvalid() {
        Transaction tx = new Transaction(null, "Bob", 50.0);
        
        assertFalse(tx.isValid());
    }
    
    @Test
    @DisplayName("Transaction with empty sender should be invalid")
    void emptySenderShouldBeInvalid() {
        Transaction tx = new Transaction("", "Bob", 50.0);
        
        assertFalse(tx.isValid());
    }
    
    @Test
    @DisplayName("Transaction with whitespace sender should be invalid")
    void whitespaceSenderShouldBeInvalid() {
        Transaction tx = new Transaction("   ", "Bob", 50.0);
        
        assertFalse(tx.isValid());
    }
    
    @Test
    @DisplayName("Transaction with null recipient should be invalid")
    void nullRecipientShouldBeInvalid() {
        Transaction tx = new Transaction("Alice", null, 50.0);
        
        assertFalse(tx.isValid());
    }
    
    @Test
    @DisplayName("Transaction with empty recipient should be invalid")
    void emptyRecipientShouldBeInvalid() {
        Transaction tx = new Transaction("Alice", "", 50.0);
        
        assertFalse(tx.isValid());
    }
    
    @Test
    @DisplayName("Transaction with whitespace recipient should be invalid")
    void whitespaceRecipientShouldBeInvalid() {
        Transaction tx = new Transaction("Alice", "   ", 50.0);
        
        assertFalse(tx.isValid());
    }
    
    // ===== EDGE CASES =====
    
    @Test
    @DisplayName("Transaction with very small amount should be valid")
    void verySmallAmountShouldBeValid() {
        Transaction tx = new Transaction("Alice", "Bob", 0.00001);
        
        assertTrue(tx.isValid());
    }
    
    @Test
    @DisplayName("Transaction with very large amount should be valid")
    void veryLargeAmountShouldBeValid() {
        Transaction tx = new Transaction("Alice", "Bob", 1_000_000_000.0);
        
        assertTrue(tx.isValid());
    }
    
    // ===== TO STRING TEST =====
    
    @Test
    @DisplayName("toString should contain transaction info")
    void toStringShouldContainTransactionInfo() {
        Transaction tx = new Transaction("Alice", "Bob", 50.0);
        String str = tx.toString();
        
        assertTrue(str.contains("Alice"));
        assertTrue(str.contains("Bob"));
        assertTrue(str.contains("50.00"));
    }

    // ===== SIGNATURE VERIFICATION TESTS =====

    @Test
    @DisplayName("Signed transaction should verify successfully")
    void signedTransactionShouldVerifySuccessfully() {
        Wallet wallet = new Wallet();
        Transaction tx = new Transaction(wallet.getAddress(), "recipient123", 50.0);
        wallet.signTransaction(tx);
        boolean isValid = tx.verifySignature();

        assertTrue(isValid, "Signed transaction should verify successfully");
    }

    @Test
    @DisplayName("Unsigned transaction should fail verification")
    void unsignedTransactionShouldFailVerification() {        
        Transaction tx = new Transaction("Alice", "recipient123", 50.0);
        boolean isValid = tx.verifySignature();

        assertFalse(isValid, "Unsigned transaction should fail verification");
    }

    @Test
    @DisplayName("COINBASE transaction should verify without signature")
    void verifySignature_coinbaseTransaction_returnsTrue() {
        // Arrange
        Transaction tx = new Transaction("COINBASE", "miner123", 50.0);
        // NOT signed - COINBASE doesn't need signature

        // Act
        boolean isValid = tx.verifySignature();

        // Assert
        assertTrue(isValid, "COINBASE transaction should verify without signature");
    }

    @Test
    @DisplayName("Transaction signed by wrong wallet should fail verification")
    void verifySignature_wrongPublicKey_returnsFalse() {
        // Arrange
        Wallet realSender = new Wallet();
        Wallet attacker = new Wallet();
        
        Transaction tx = new Transaction(realSender.getAddress(), "recipient123", 50.0);
        
        // Attacker tries to sign a transaction from realSender's address
        // This should throw because address doesn't match
        assertThrows(
            IllegalStateException.class,
            () -> attacker.signTransaction(tx),
            "Should not allow signing for different address"
        );
    }

    @Test
    @DisplayName("Tampered transaction should fail verification")
    void verifySignature_tamperedData_returnsFalse() {
        // Arrange
        Wallet wallet = new Wallet();
        Transaction tx = new Transaction(wallet.getAddress(), "recipient123", 50.0);
        wallet.signTransaction(tx);
        
        // Simulate tampering by creating a new transaction with different data
        // but copying the signature (in real scenario, attacker might try this)
        Transaction tamperedTx = new Transaction(wallet.getAddress(), "recipient123", 999.0);
        tamperedTx.setSignature(tx.getSignature());
        tamperedTx.setSenderPublicKey(tx.getSenderPublicKey());

        // Act
        boolean isValid = tamperedTx.verifySignature();

        // Assert
        assertFalse(isValid, "Tampered transaction should fail verification");
    }
}