# 📐 BlockSmith - Code Conventions

> Style guide and conventions for consistent code across the project.

---

## 📝 General Principles

1. **Educational Focus**: Code should be clear and well-documented
2. **Simplicity Over Optimization**: Prefer readable code over clever code
3. **Consistency**: Follow existing patterns in the codebase
4. **Test Everything**: All features need unit tests

---

## 🏷️ Naming Conventions

### Classes
- **PascalCase**: `Block`, `Blockchain`, `HashUtil`
- **Descriptive nouns**: `Transaction`, `Wallet`, `BlockExplorer`

### Methods
- **camelCase**: `calculateHash()`, `mineBlock()`, `isValid()`
- **Verb prefixes**:
  - `get*` - getters
  - `set*` - setters
  - `is*`/`has*` - boolean queries
  - `calculate*` - computation methods
  - `create*` - factory methods
  - `add*`/`remove*` - collection modifiers
  - `print*` - console output

### Variables
- **camelCase**: `previousHash`, `transactionId`, `merkleRoot`
- **Meaningful names**: Avoid single letters (except loop counters)

### Constants
- **UPPER_SNAKE_CASE**: `MINING_DIFFICULTY`, `GENESIS_PREV_HASH`
- **In config class**: Centralize in `BlockchainConfig`

### Packages
- **lowercase**: `com.blocksmith.core`, `com.blocksmith.util`

---

## 📚 Documentation Style

### THEORY Comments (Required for key methods)

Every important method should have a THEORY block explaining the concept:

```java
/**
 * THEORY: [Main concept explanation]
 * 
 * [Detailed explanation with examples if needed]
 * 
 * EXAMPLE:
 * [Visual diagram or code example]
 * 
 * SECURITY: [Security implications if any]
 * 
 * BLOCKCHAIN USE: [How this applies to blockchain]
 * 
 * @param paramName Description
 * @return Description
 */
public ReturnType methodName(ParamType paramName) {
    // Implementation
}
```

### Example THEORY Comment

```java
/**
 * THEORY: Proof-of-Work (PoW) requires finding a nonce value that,
 * when combined with block data and hashed, produces a hash starting 
 * with a required number of zeros (determined by difficulty).
 * 
 * THE PUZZLE: 
 * - difficulty = 4 means hash must start with "0000"
 * - Only way to find valid nonce is brute-force (try many values)
 * - Higher difficulty = more zeros required = exponentially more attempts
 * 
 * WHY THIS WORKS:
 * - SHA-256 is unpredictable - can't calculate nonce mathematically
 * - Must try random nonces until valid hash is found
 * - On average, difficulty N requires 16^N attempts
 * 
 * BITCOIN: Adjusts difficulty every 2016 blocks to maintain ~10 min/block
 * 
 * @param difficulty Number of leading zeros required in hash
 * @return Time taken to mine in milliseconds
 */
public long mineBlock(int difficulty) { ... }
```

### Class-Level Documentation

```java
/**
 * [One-line description]
 * 
 * THEORY: [Concept explanation]
 * 
 * STRUCTURE:
 * [ASCII diagram if helpful]
 * 
 * USAGE:
 * [Code example]
 */
public class ClassName { ... }
```

---

## 🧪 Test Conventions

### Test Class Structure

```java
package com.blocksmith.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassNameTest {
    
    private ClassName instance;
    
    @BeforeEach
    void setUp() {
        instance = new ClassName();
    }
    
    // ===== SECTION HEADER =====
    
    @Test
    @DisplayName("Human readable test description")
    void methodName_scenario_expectedResult() {
        // Arrange
        // Act
        // Assert
    }
}
```

### Test Naming Pattern

```java
// Pattern: method_scenario_expectedResult
void calculateHash_sameInput_returnsSameHash()
void mineBlock_difficultyFour_hashStartsWithFourZeros()
void isValid_negativeAmount_returnsFalse()
void addTransaction_nullTransaction_returnsFalse()
```

### Test Section Comments

```java
// ===== BASIC CREATION TESTS =====

// ===== VALIDATION TESTS =====

// ===== MINING TESTS =====

// ===== TRANSACTION TESTS =====
```

### Assertions

Prefer specific assertions with messages:
```java
// Good
assertEquals(64, hash.length(), "Hash should be 64 characters");
assertTrue(isValid, "Valid transaction should pass validation");
assertNotNull(block.getHash(), "Hash should not be null");

// Avoid
assertEquals(64, hash.length());  // No message
assertTrue(isValid);              // No message
```

---

## 🏗️ Code Structure

### Class Organization

```java
public class ClassName {
    
    // 1. Static constants
    private static final int CONSTANT = 42;
    
    // 2. Instance fields
    private final String field1;
    private int field2;
    
    // 3. Constructors
    public ClassName() { }
    public ClassName(String param) { }
    
    // 4. Static factory methods
    public static ClassName create() { }
    
    // 5. Public methods
    public void publicMethod() { }
    
    // 6. Package-private methods
    void packageMethod() { }
    
    // 7. Private methods
    private void privateMethod() { }
    
    // 8. Getters/Setters
    public String getField1() { }
    
    // 9. toString, equals, hashCode
    @Override
    public String toString() { }
}
```

### Method Length

- Keep methods under 30 lines when possible
- Extract complex logic into helper methods
- One method = one responsibility

### Constructor Pattern

```java
public ClassName(Type1 param1, Type2 param2) {
    // Validate inputs if needed
    if (param1 == null) throw new IllegalArgumentException("...");
    
    // Assign fields
    this.field1 = param1;
    this.field2 = param2;
    
    // Compute derived fields
    this.derivedField = computeSomething();
}
```

---

## 🔒 Immutability Patterns

### Defensive Copies for Collections

```java
// In constructor
this.transactions = new ArrayList<>(transactions);

// In getter
public List<Transaction> getTransactions() {
    return new ArrayList<>(transactions);  // Return copy
}

// Or use unmodifiable
public List<Block> getChain() {
    return Collections.unmodifiableList(chain);
}
```

### Final Fields

```java
private final String transactionId;  // Can't be changed after construction
private final long timestamp;
```

---

## 🖨️ Console Output Style

### Section Headers

```java
System.out.println("═══════════════════════════════════════════════════════════");
System.out.println("                      SECTION TITLE                         ");
System.out.println("═══════════════════════════════════════════════════════════");
```

### Progress Indicators

```java
System.out.println("▶ Creating blockchain...");
System.out.println("⛏️  Mining block #" + index + "...");
System.out.println("✅ Block mined and added to chain!");
```

### Info Display

```java
System.out.println("  Field name: " + value);
System.out.println("  Balance:    " + balance + " " + BlockchainConfig.CURRENCY_SYMBOL);
```

### Block Display Format

```java
System.out.println("┌─ Block #" + block.getIndex());
System.out.println("│  Data: " + block.getData());
System.out.println("│  Nonce: " + block.getNonce());
System.out.println("│  Hash: " + block.getHash().substring(0, 16) + "...");
System.out.println("└─ PrevHash: " + block.getPreviousHash().substring(0, 16) + "...");
```

---

## 📦 Import Order

```java
// 1. Project imports
import com.blocksmith.core.Block;
import com.blocksmith.util.HashUtil;

// 2. Java standard library
import java.util.ArrayList;
import java.util.List;

// 3. Third-party libraries
import com.google.gson.Gson;

// 4. Test imports (in test files)
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
```

---

## 🚫 Avoid

1. **Magic numbers** - Use constants in `BlockchainConfig`
2. **Null returns** - Return empty collections or Optional
3. **Mutable getters** - Return copies or unmodifiable views
4. **Long methods** - Extract into smaller methods
5. **Cryptic names** - Be descriptive
6. **Missing THEORY comments** - Document the why, not just the what

---

## ✅ Checklist Before Commit

- [ ] Code compiles (`mvn compile`)
- [ ] All tests pass (`mvn test`)
- [ ] THEORY comments added for new methods
- [ ] Consistent naming conventions
- [ ] No magic numbers
- [ ] Collections returned defensively
- [ ] Sprint documentation updated

---

*Last updated: 2026-01-21*
