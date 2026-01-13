# Sprint BONUS: Persistence

## 📋 Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | BONUS |
| **Title** | Persistence |
| **Duration** | 1 week |
| **Phase** | BONUS |
| **Status** | Not Started |
| **Depends On** | Sprint 7 |
| **Priority** | Optional |

---

## 🎯 Goal

Implement blockchain persistence - save the entire chain to a JSON file and load it back. This allows the blockchain to survive application restarts.

---

## 📦 Deliverables

### 1. Blockchain Serialization
- [ ] `saveToFile(String filePath)` method:
  - Serialize entire chain to JSON
  - Include all blocks and transactions
  - Save to specified file path
  - Handle IO errors gracefully
- [ ] `loadFromFile(String filePath)` static method:
  - Read JSON from file
  - Deserialize to Blockchain object
  - Validate loaded chain
  - Return valid blockchain or throw exception

### 2. JSON Structure Design
- [ ] Define JSON schema for blockchain
- [ ] Handle special types:
  - byte[] signature → Base64 string
  - PublicKey → Base64 encoded
  - long timestamp → ISO 8601 or Unix timestamp

### 3. Data Directory
- [ ] Use `data/` folder for persistence
- [ ] Default file: `data/blockchain.json`
- [ ] Create directory if not exists

### 4. Validation on Load
- [ ] Verify loaded chain is valid
- [ ] Check all hashes are correct
- [ ] Verify all signatures (if applicable)
- [ ] Reject corrupted/tampered files

### 5. Wallet Persistence (Optional)
- [ ] Save wallet private key (encrypted?)
- [ ] Load wallet from file
- [ ] Warning: Private key storage is security-sensitive!

### 6. Unit Tests
- [ ] `PersistenceTest.java`:
  - Test save empty blockchain
  - Test save blockchain with blocks
  - Test load and validate
  - Test tampered file detection
  - Test file not found handling
  - Test corrupted JSON handling

---

## ✅ Acceptance Criteria

| # | Criteria | Status |
|---|----------|--------|
| 1 | Blockchain can be saved to JSON file | ⬜ |
| 2 | Blockchain can be loaded from JSON file | ⬜ |
| 3 | Loaded blockchain passes validation | ⬜ |
| 4 | Tampered JSON file is rejected | ⬜ |
| 5 | Missing file is handled gracefully | ⬜ |
| 6 | All unit tests pass | ⬜ |

---

## 📝 JSON Schema

### Blockchain JSON Structure
```json
{
  "version": "1.0",
  "savedAt": "2026-01-20T14:30:00Z",
  "difficulty": 4,
  "chain": [
    {
      "index": 0,
      "timestamp": 1705753800000,
      "hash": "0000abc123...",
      "previousHash": "0",
      "nonce": 12847,
      "merkleRoot": "def456...",
      "transactions": [
        {
          "transactionId": "tx123...",
          "sender": "COINBASE",
          "recipient": "0x71C7...",
          "amount": 50.0,
          "timestamp": 1705753800000,
          "signature": "BASE64_ENCODED_SIGNATURE",
          "senderPublicKey": "BASE64_ENCODED_PUBLIC_KEY"
        }
      ]
    }
  ],
  "pendingTransactions": []
}
```

---

## 🧪 Test Scenarios

### Save/Load Tests

| Test | Setup | Action | Expected |
|------|-------|--------|----------|
| Save empty | New blockchain (Genesis only) | saveToFile() | JSON created with 1 block |
| Save full | Chain with 5 blocks | saveToFile() | JSON with all data |
| Load valid | Valid JSON file | loadFromFile() | Blockchain recreated |
| Load tampered | Modified hash in JSON | loadFromFile() | Exception thrown |
| Load missing | Non-existent file | loadFromFile() | FileNotFoundException |
| Load corrupted | Invalid JSON syntax | loadFromFile() | JsonParseException |

### Round-trip Tests

| Test | Action | Expected |
|------|--------|----------|
| Identity | Save, load, compare | Identical blockchains |
| Validation | Save, load, isChainValid() | Returns true |
| Mining | Load, mine new block, save | Works correctly |

---

## 📝 Tasks Breakdown

| # | Task | Estimated Time | Status |
|---|------|----------------|--------|
| 1 | Design JSON schema | 20 min | ⬜ |
| 2 | Implement saveToFile() | 60 min | ⬜ |
| 3 | Implement loadFromFile() | 60 min | ⬜ |
| 4 | Handle byte[] / PublicKey serialization | 45 min | ⬜ |
| 5 | Add validation on load | 30 min | ⬜ |
| 6 | Write PersistenceTest | 60 min | ⬜ |
| 7 | Update demo to show persistence | 30 min | ⬜ |

**Total Estimated Time:** ~5-6 hours

---

## 🔗 Dependencies

- Sprint 7 must be completed
- Gson library (already in pom.xml)
- All previous functionality working

---

## 💡 Implementation Hints

### Gson Configuration
```java
Gson gson = new GsonBuilder()
    .setPrettyPrinting()
    .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    .registerTypeAdapter(byte[].class, new ByteArrayAdapter())
    .registerTypeAdapter(PublicKey.class, new PublicKeyAdapter())
    .create();
```

### Custom Type Adapter for byte[]
```java
public class ByteArrayAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {
    @Override
    public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(Base64.getEncoder().encodeToString(src));
    }
    
    @Override
    public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
        return Base64.getDecoder().decode(json.getAsString());
    }
}
```

### SaveToFile Implementation
```java
public void saveToFile(String filePath) throws IOException {
    // Create directory if needed
    Path path = Paths.get(filePath);
    Files.createDirectories(path.getParent());
    
    // Create wrapper object
    BlockchainData data = new BlockchainData();
    data.version = "1.0";
    data.savedAt = Instant.now().toString();
    data.difficulty = BlockchainConfig.MINING_DIFFICULTY;
    data.chain = this.chain;
    data.pendingTransactions = this.pendingTransactions;
    
    // Serialize and write
    String json = gson.toJson(data);
    Files.writeString(path, json, StandardCharsets.UTF_8);
}
```

### LoadFromFile Implementation
```java
public static Blockchain loadFromFile(String filePath) throws IOException {
    String json = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
    BlockchainData data = gson.fromJson(json, BlockchainData.class);
    
    Blockchain blockchain = new Blockchain(data.chain, data.pendingTransactions);
    
    // Validate loaded chain
    if (!blockchain.isChainValid()) {
        throw new IllegalStateException("Loaded blockchain is invalid or tampered");
    }
    
    return blockchain;
}
```

---

## ⚠️ Security Considerations

### Private Key Storage
If implementing wallet persistence:
- **NEVER** store private keys in plain text
- Consider encryption with user password
- Or use Java KeyStore (JKS)
- Warn users about risks

### File Integrity
- Consider adding checksum/hash of entire file
- Detect if file was modified externally
- Optional: sign the file with a master key

---

## 🚫 Out of Scope

- Database storage (SQLite, H2, etc.)
- Cloud storage
- Encrypted blockchain storage
- Automatic backups
- Multiple save slots
