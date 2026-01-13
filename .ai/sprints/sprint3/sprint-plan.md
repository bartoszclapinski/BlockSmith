# Sprint 3: Blockchain

## 📋 Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 3 |
| **Title** | Blockchain |
| **Duration** | 1 week |
| **Phase** | Phase 3 |
| **Status** | Not Started |
| **Depends On** | Sprint 2 |

---

## 🎯 Goal

Create the Blockchain class that manages the chain of blocks, handles validation, and detects tampering. This is where individual blocks become a secure, linked chain.

---

## 📦 Deliverables

### 1. Blockchain Class
- [ ] Private field: `List<Block> chain`
- [ ] Constructor that creates Genesis block automatically
- [ ] `getLatestBlock()` - returns the last block in chain
- [ ] `addBlock(Block block)` - adds new block with correct previousHash and mines it
- [ ] `isChainValid()` - validates entire chain integrity
- [ ] `getChain()` - returns copy of chain (immutability)
- [ ] `getChainSize()` - returns number of blocks

### 2. Chain Validation Logic
- [ ] Check each block's hash is correctly calculated
- [ ] Check each block's previousHash matches previous block's hash
- [ ] Check Genesis block is valid (index 0, previousHash = "0")
- [ ] Check all blocks are mined (hash meets difficulty)

### 3. Tamper Detection Demo
- [ ] Create method to demonstrate chain tampering detection
- [ ] Show how changing one block invalidates the entire chain

### 4. Unit Tests
- [ ] `BlockchainTest.java`:
  - Test chain creation with Genesis block
  - Test adding blocks maintains chain integrity
  - Test `isChainValid()` returns true for valid chain
  - Test tampered block is detected
  - Test tampered previousHash is detected
  - Test Genesis block validation

---

## ✅ Acceptance Criteria

| # | Criteria | Status |
|---|----------|--------|
| 1 | New Blockchain starts with Genesis block | ⬜ |
| 2 | Adding block correctly links to previous | ⬜ |
| 3 | `isChainValid()` returns true for untampered chain | ⬜ |
| 4 | `isChainValid()` returns false if block data changed | ⬜ |
| 5 | `isChainValid()` returns false if hash changed | ⬜ |
| 6 | `isChainValid()` returns false if previousHash changed | ⬜ |
| 7 | All unit tests pass | ⬜ |

---

## 📝 Theory Comments Required

### Blockchain Explanation
```java
/**
 * THEORY: A blockchain is a linked list of blocks where each block
 * contains the hash of the previous block, creating a chain.
 * 
 * STRUCTURE:
 * [Genesis] -> [Block 1] -> [Block 2] -> ... -> [Block N]
 *    ↑            ↑            ↑                    ↑
 * prevHash=0   prevHash=    prevHash=           prevHash=
 *              genesis.hash  block1.hash        blockN-1.hash
 * 
 * IMMUTABILITY: If any block is modified, its hash changes,
 * which breaks the link from the next block (prevHash mismatch).
 * This invalidates that block AND all subsequent blocks.
 * 
 * SECURITY: To tamper with block N, attacker must:
 * 1. Modify block N
 * 2. Re-mine block N (find new valid nonce)
 * 3. Re-mine ALL blocks after N
 * 4. Do this faster than honest network adds new blocks
 */
```

### Genesis Block Explanation
```java
/**
 * THEORY: Genesis Block is the first block in any blockchain.
 * 
 * SPECIAL PROPERTIES:
 * - index = 0 (first block)
 * - previousHash = "0" (no previous block exists)
 * - Usually hardcoded or has special data
 * 
 * BITCOIN'S GENESIS: Contains the message:
 * "The Times 03/Jan/2009 Chancellor on brink of second bailout for banks"
 * This proves the block couldn't have been created before that date.
 */
```

---

## 🧪 Test Scenarios

### Chain Validation Tests

| Test | Setup | Action | Expected |
|------|-------|--------|----------|
| Valid chain | Create blockchain, add 3 blocks | isChainValid() | true |
| Tampered data | Change block[1].data | isChainValid() | false |
| Tampered hash | Manually change block[1].hash | isChainValid() | false |
| Broken link | Change block[2].previousHash | isChainValid() | false |
| Empty chain | Just Genesis | isChainValid() | true |

### Chain Operations Tests

| Test | Action | Expected |
|------|--------|----------|
| Genesis | new Blockchain() | chain.size() = 1, first block is Genesis |
| Add block | addBlock(new Block(...)) | chain.size() = 2, linked correctly |
| Get latest | getLatestBlock() | Returns last added block |
| Chain copy | getChain() | Returns copy, not reference |

---

## 📝 Tasks Breakdown

| # | Task | Estimated Time | Status |
|---|------|----------------|--------|
| 1 | Create Blockchain class structure | 20 min | ⬜ |
| 2 | Implement Genesis block creation | 20 min | ⬜ |
| 3 | Implement addBlock() | 30 min | ⬜ |
| 4 | Implement isChainValid() | 45 min | ⬜ |
| 5 | Add helper methods (getLatestBlock, etc.) | 20 min | ⬜ |
| 6 | Write BlockchainTest | 60 min | ⬜ |
| 7 | Create tamper detection demo | 30 min | ⬜ |
| 8 | Add theory comments | 20 min | ⬜ |

**Total Estimated Time:** ~4-5 hours

---

## 🔗 Dependencies

- Sprint 2 must be completed (Block with mining)
- BlockchainConfig for difficulty setting

---

## 💡 Implementation Hints

### isChainValid Algorithm
```java
public boolean isChainValid() {
    // Check Genesis block
    Block genesis = chain.get(0);
    if (genesis.getIndex() != 0) return false;
    if (!genesis.getPreviousHash().equals("0")) return false;
    
    // Check rest of chain
    for (int i = 1; i < chain.size(); i++) {
        Block current = chain.get(i);
        Block previous = chain.get(i - 1);
        
        // Verify current block's hash
        if (!current.getHash().equals(current.calculateHash())) {
            return false;
        }
        
        // Verify link to previous block
        if (!current.getPreviousHash().equals(previous.getHash())) {
            return false;
        }
        
        // Verify block was mined (optional check)
        String target = "0".repeat(BlockchainConfig.MINING_DIFFICULTY);
        if (!current.getHash().startsWith(target)) {
            return false;
        }
    }
    return true;
}
```

---

## 🚫 Out of Scope

- Transactions (data field still holds string) - Sprint 4
- Mining rewards - Sprint 6
- Persistence (save/load) - Sprint BONUS
- Network synchronization - out of project scope
