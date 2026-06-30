# ⚒️ BlockSmith

**A complete blockchain and cryptocurrency implementation built from scratch in Java**

BlockSmith is a comprehensive blockchain project that goes beyond tutorials - implementing a fully functional distributed cryptocurrency system with P2P networking, REST API, web dashboard, and basic smart contracts. Built to deeply understand how Bitcoin and Ethereum work under the hood.

[![Java](https://img.shields.io/badge/Java-20+-orange.svg)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![Tests](https://img.shields.io/badge/Tests-137%20passing-brightgreen.svg)](#)
[![Phase](https://img.shields.io/badge/Phase%202-In%20Progress-yellow.svg)](#)
[![Version](https://img.shields.io/badge/Version-1.0.0-blue.svg)](#)

---

## 🚀 Features

### Phase 1: Core Blockchain ✅ Complete (100%)
- ✅ SHA-256 cryptographic hashing
- ✅ Proof-of-Work mining with adjustable difficulty
- ✅ Merkle tree for transaction verification
- ✅ Transaction model with validation
- ✅ Pending transaction pool (mempool)
- ✅ Mining rewards (50 BSC from COINBASE)
- ✅ Balance tracking for addresses
- ✅ Chain validation and tamper detection
- ✅ ECDSA digital signatures
- ✅ Wallet with key pair generation
- ✅ Ethereum-style addresses (0x format)
- ✅ Balance validation before transfers
- ✅ Double-spend prevention (pending tracking)

### Phase 2: Network Layer 🔄 In Progress (50%)
- ✅ Message protocol with JSON serialization (Sprint 8a)
- ✅ Server-side TCP socket node (Sprint 8b)
- ✅ Client-side peer connections with handshake (Sprint 8c)
- ✅ Bidirectional message exchange with handler pattern (Sprint 8d)
- ✅ Node discovery and peer management (Sprint 9)
  - ✅ PeerInfo / PeerState metadata tracking (Sprint 9a)
  - ✅ PeerManager registry with MAX_PEERS enforcement (Sprint 9b)
  - ✅ Heartbeat PING/PONG with dead-peer eviction (Sprint 9c)
  - ✅ Peer discovery (GET_PEERS/PEERS) + seed-node bootstrap (Sprint 9d)
- 🔜 Block and transaction broadcasting (Sprint 10)
- 🔜 Mempool synchronization (Sprint 11)

### Phase 3: API & Interface 🔜 Planned
- REST API for blockchain interaction
- Web dashboard for monitoring
- BlockExplorer UI
- Basic smart contract support
- Multi-signature wallets

### Phase 4: Production Features 🔜 Planned
- Database persistence (SQLite)
- Dynamic difficulty adjustment
- Block size limits and fee market

---

## 🎮 Demo Output

```
═══════════════════════════════════════════════════════════
                    BLOCKSMITH v1.0.0                       
              Proof-of-Work Mining Demo                     
═══════════════════════════════════════════════════════════

▶ Creating Genesis Block...
    Mining with difficulty 4...
Block mined! Nonce: 8208 | Time: 103ms
Hash: 0000aeaf2928201f80df08494337a342bf04c5f72a33442db24f58ee7e76ee75

═══════════════════════════════════════════════════════════
                   TRANSACTION DEMO                         
═══════════════════════════════════════════════════════════

▶ Miner1 mines the first block (receives 50 BSC reward)...
⛏️  Mining block #1 with 1 transactions...
Block mined! Nonce: 45049 | Time: 43ms
✅ Block mined and added to chain!
   Miner Miner1 received 50.0 BSC

▶ Creating transactions...
  Transaction{id=964904f5..., Miner1 -> Alice: 30.00}
  Transaction{id=69df15dc..., Miner1 -> Bob: 15.00}

▶ Final balances:
  Miner1: 55.0 BSC (mined 2 blocks)
  Alice:  20.0 BSC
  Bob:    25.0 BSC
```

---

## 🏛️ Architecture

### Core Classes

```
┌─────────────────────────────────────────────────────────────────┐
│                         BLOCKSMITH                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌─────────────┐     ┌─────────────┐         │
│  │   Wallet    │ ──▶│ Transaction │───▶│   Block     │         │
│  │  (Keys)     │    │ (Signed)    │     │   (Mined)   │         │
│  └─────────────┘    └─────────────┘     └─────────────┘         │
│        │                   │                  │                 │
│        │ signs             │ contains         │ links           │
│        ▼                   ▼                  ▼                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    BLOCKCHAIN                           │    │
│  │  ┌───────┐   ┌───────┐    ┌───────┐    ┌───────┐        │    │
│  │  │Block 0│──▶│Block 1│──▶│Block 2│──▶│Block n│        │    │
│  │  │Genesis│   │       │    │       │    │       │        │    │
│  │  └───────┘   └───────┘    └───────┘    └───────┘        │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─────────────┐  ┌──────────────────┐  ┌─────────────────┐     │
│  │  HashUtil   │  │ BlockchainConfig │  │  BlockExplorer  │     │
│  │  (SHA-256)  │  │   (Constants)    │  │   (Viewer)      │     │
│  └─────────────┘  └──────────────────┘  └─────────────────┘     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Blockchain Structure

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│  BLOCK #0   │      │  BLOCK #1   │      │  BLOCK #2   │
│  (Genesis)  │      │             │      │             │
├─────────────┤      ├─────────────┤      ├─────────────┤
│ prevHash: 0 │◄─────│ prevHash ───│◄─────│ prevHash ───│◄── ...
│ timestamp   │      │ timestamp   │      │ timestamp   │
│ merkleRoot  │      │ merkleRoot  │      │ merkleRoot  │
│ nonce       │      │ nonce       │      │ nonce       │
│ hash ───────│─────►│ hash ───────│─────►│ hash        │
└─────────────┘      └─────────────┘      └─────────────┘
```

### Merkle Tree

```
                    ┌──────────────────┐
                    │   Merkle Root    │
                    │  (in block hash) │
                    └────────┬─────────┘
                             │
              ┌──────────────┴──────────────┐
              │                             │
        ┌─────┴─────┐                 ┌─────┴─────┐
        │ Hash(AB)  │                 │ Hash(CD)  │
        └─────┬─────┘                 └─────┬─────┘
              │                             │
       ┌──────┴──────┐               ┌──────┴──────┐
       │             │               │             │
   ┌───┴───┐     ┌───┴───┐       ┌───┴───┐     ┌───┴───┐
   │ Tx A  │     │ Tx B  │       │ Tx C  │     │ Tx D  │
   └───────┘     └───────┘       └───────┘     └───────┘
```

### Proof-of-Work Mining

```
Target: Hash must start with "0000" (difficulty = 4)

nonce = 0     → hash = "8a3f2b..."      ✗ Invalid
nonce = 1     → hash = "c7e9f1..."      ✗ Invalid
nonce = 2     → hash = "1d4a8c..."      ✗ Invalid
    ...
nonce = 52847 → hash = "0000a8b2..."    ✓ VALID!

Average attempts: ~16^difficulty (~65,536 for difficulty 4)
```

---

## 📋 Prerequisites

- **Java JDK 20** or higher
- **Maven 3.8** or higher

---

## 🔧 Build & Run

### Compile the project
```bash
mvn clean compile
```

### Run all tests (137 tests)
```bash
mvn test
```

### Run the demo
```bash
mvn exec:java
```

### Create JAR package
```bash
mvn package
java -jar target/blocksmith-1.0.0.jar
```

---

## 📁 Project Structure

```
BlockSmith/
├── .ai/                    # Project documentation for AI/LLMs
│   ├── ONBOARDING.md       # Quick start guide
│   ├── ARCHITECTURE.md     # Class descriptions
│   ├── CONVENTIONS.md      # Code style guide
│   ├── STATUS.md           # Current sprint status
│   ├── prd.md              # Product requirements
│   ├── tech-stack.md       # Technologies used
│   ├── roadmap.md          # Full project roadmap
│   └── sprints/            # Sprint plans and logs
├── src/main/java/com/blocksmith/
│   ├── core/
│   │   ├── Block.java          # Block with transactions & Merkle root
│   │   ├── Blockchain.java     # Chain management & mining
│   │   ├── Transaction.java    # Value transfers with signatures
│   │   └── Wallet.java         # ECDSA keys & signing
│   ├── util/
│   │   ├── HashUtil.java       # SHA-256 hashing
│   │   ├── BlockchainConfig.java # Configuration constants
│   │   └── BlockExplorer.java  # Chain viewer (TODO)
│   ├── network/                # P2P networking (Sprint 8)
│   │   ├── MessageType.java    # Network message types
│   │   ├── Message.java        # Base message class
│   │   ├── MessageParser.java  # JSON-to-Message routing
│   │   ├── MessageHandler.java # Handler interface
│   │   ├── MessageContext.java # Connection wrapper
│   │   ├── MessageListener.java # Async listener interface
│   │   ├── NetworkConfig.java  # Network constants
│   │   ├── Node.java           # Server node + message loop + heartbeat + discovery
│   │   ├── Peer.java           # Client peer with async listener
│   │   ├── PeerState.java     # Peer connection lifecycle
│   │   ├── PeerInfo.java      # Peer metadata tracking
│   │   ├── PeerManager.java   # Peer registry with MAX_PEERS enforcement
│   │   └── messages/           # Concrete message classes (incl. GetPeers/Peers)
│   └── BlockSmithDemo.java     # Main demo application
├── src/test/java/              # 137 unit tests
├── data/                       # Blockchain persistence (JSON)
├── pom.xml                     # Maven configuration
└── README.md
```

---

## 🧪 Test Coverage

| Test Class | Tests | Description |
|------------|-------|-------------|
| HashUtilTest | 6 | SHA-256 hashing |
| BlockTest | 12 | Block creation, mining, transactions |
| BlockchainTest | 25 | Chain management, validation, balance checks |
| MiningTest | 9 | Proof-of-Work mechanics |
| TransactionTest | 22 | Transaction validation, signatures |
| WalletTest | 13 | Key generation, addresses, signing |
| MessageTest | 6 | Network message serialization |
| NodeTest | 8 | Node start/stop, connections |
| PeerTest | 7 | Peer connections, handshake |
| CommunicationTest | 6 | Bidirectional message exchange |
| PeerInfoTest | 6 | Peer metadata and state transitions |
| PeerManagerTest | 8 | Peer registry, MAX_PEERS enforcement |
| HeartbeatTest | 4 | Heartbeat ping, dead-peer eviction |
| PeerDiscoveryTest | 5 | Peer discovery messages and handlers |
| **Total** | **137** | All passing ✅ |

---

## 📚 Skills & Technologies

### Core Blockchain
- Cryptographic hashing (SHA-256)
- Digital signatures (ECDSA)
- Proof-of-Work consensus
- Merkle trees & data structures
- Transaction pools (mempool)

### Java
- Java Cryptography Architecture (JCA)
- Collections framework
- Object-oriented design
- Reflection (for testing)

### Software Engineering
- Clean architecture
- Unit testing (JUnit 5)
- Maven build system
- Git workflow with branches
- Comprehensive documentation

---

## 🏗️ Development Status

### Phase 1: Core Blockchain ✅ Complete (100%)
| Sprint | Title | Status |
|--------|-------|--------|
| Sprint 0 | Project Setup | ✅ Complete |
| Sprint 1 | Fundamentals (Hash, Block) | ✅ Complete |
| Sprint 2 | Proof-of-Work Mining | ✅ Complete |
| Sprint 3 | Blockchain Management | ✅ Complete |
| Sprint 4 | Transactions & Merkle Trees | ✅ Complete |
| Sprint 5 | Wallets & Digital Signatures | ✅ Complete |
| Sprint 6 | Economic System | ✅ Complete |

### Phase 2: Network Layer (50% Complete)
| Sprint | Title | Status |
|--------|-------|--------|
| Sprint 8 | P2P Networking | ✅ Complete (8a, 8b, 8c, 8d) |
| Sprint 9 | Node Discovery | ✅ Complete (9a, 9b, 9c, 9d) |
| Sprint 10 | Block Broadcasting | ⬜ Planned |
| Sprint 11 | Mempool Sync | ⬜ Planned |

### Phase 3: API & Interface
| Sprint | Title | Status |
|--------|-------|--------|
| Sprint 12-15 | REST API, Dashboard, Contracts | ⬜ Planned |

### Phase 4: Production
| Sprint | Title | Status |
|--------|-------|--------|
| Sprint 16-19 | Database, Difficulty, Fees | ⬜ Planned |

---

## 🎓 Learning Resources

- [Bitcoin Whitepaper](https://bitcoin.org/bitcoin.pdf) - Original Satoshi paper
- [Blockchain Demo](https://andersbrownworth.com/blockchain/) - Visual demonstration
- [Mastering Bitcoin](https://github.com/bitcoinbook/bitcoinbook) - Comprehensive book

---

## 📝 License

This project is for educational purposes.

---

## 👤 Author

**Bartek** - [GitHub](https://github.com/bartoszclapinski)

---

*Last updated: 2026-06-30 | Phase 2 Sprint 9 Complete (Node Discovery)*
