# 🗺️ BlockSmith - Extended Roadmap

## Project Vision

Transform BlockSmith from an educational blockchain into a **fully functional distributed cryptocurrency system** with networking, REST API, web dashboard, and basic smart contracts.

---

## 📊 Project Phases Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        BLOCKSMITH ROADMAP                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  PHASE 1: Core Blockchain (Sprint 0-6)          ███████████████ 100% │
│  ├── Project Setup                               ✅ Complete        │
│  ├── Fundamentals (Hash, Block)                  ✅ Complete        │
│  ├── Proof-of-Work                               ✅ Complete        │
│  ├── Blockchain Management                       ✅ Complete        │
│  ├── Transactions                                ✅ Complete        │
│  ├── Wallets & Signatures                        ✅ Complete        │
│  └── Economic System                             ✅ Complete        │
│                                                                     │
│  PHASE 2: Network Layer (Sprint 8-11)           ███░░░░░░░░░ 15%    │
│  ├── P2P Networking                              🔄 Sprint 8 (8a ✅) │
│  ├── Node Discovery                              ⬜ Sprint 9        │
│  ├── Block Broadcasting                          ⬜ Sprint 10       │
│  └── Mempool Synchronization                     ⬜ Sprint 11       │
│                                                                     │
│  PHASE 3: API & Interface (Sprint 12-15)        ░░░░░░░░░░░░ 0%     │
│  ├── REST API                                    ⬜ Sprint 12       │
│  ├── Web Dashboard                               ⬜ Sprint 13       │
│  ├── Basic Smart Contracts                       ⬜ Sprint 14       │
│  └── Multi-signature Wallets                     ⬜ Sprint 15       │
│                                                                     │
│  PHASE 4: Production Features (Sprint 16-19)    ░░░░░░░░░░░░ 0%     │
│  ├── Database Persistence                        ⬜ Sprint 16       │
│  ├── Difficulty Adjustment                       ⬜ Sprint 17       │
│  ├── Block Size & Limits                         ⬜ Sprint 18       │
│  └── Fee Market                                  ⬜ Sprint 19       │
│                                                                     │
│  BONUS: Advanced Features                       ░░░░░░░░░░░░ 0%     │
│  ├── Light Clients (SPV)                         ⬜ Optional        │
│  ├── Blockchain Explorer Web App                 ⬜ Optional        │
│  └── Mobile Wallet                               ⬜ Optional        │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 📦 Phase 1: Core Blockchain (Current)

**Goal:** Build a functional single-node blockchain with all core features.

| Sprint | Title | Key Deliverables | Status |
|--------|-------|------------------|--------|
| 0 | Project Setup | Maven, structure, placeholders | ✅ Complete |
| 1 | Fundamentals | HashUtil, Block, BlockchainConfig | ✅ Complete |
| 2 | Proof-of-Work | Mining, nonce, difficulty | ✅ Complete |
| 3 | Blockchain | Chain management, validation | ✅ Complete |
| 4 | Transactions | Transaction model, Merkle tree | ✅ Complete |
| 5 | Wallets | ECDSA keys, signatures | ✅ Complete |
| 6 | Economics | Balance validation | ✅ Complete |

---

## 🌐 Phase 2: Network Layer

**Goal:** Transform single-node blockchain into a distributed P2P network.

| Sprint | Title | Key Deliverables |
|--------|-------|------------------|
| 8 | P2P Networking | TCP socket communication, message protocol, node connections |
| 9 | Node Discovery | Peer list management, connection bootstrapping, heartbeat |
| 10 | Block Broadcasting | Block propagation, orphan handling, chain sync |
| 11 | Mempool Sync | Transaction broadcasting, mempool management, double-spend prevention |

### New Classes (Phase 2)
```
com.blocksmith.network/
├── Node.java              # Main network node
├── Peer.java              # Peer connection handler
├── PeerManager.java       # Manages peer connections
├── Message.java           # Network message base
├── MessageType.java       # Message types enum
├── BlockMessage.java      # Block broadcast message
├── TransactionMessage.java # Transaction broadcast
├── SyncMessage.java       # Chain synchronization
└── NetworkConfig.java     # Network configuration
```

### Technologies
- Java Sockets (TCP)
- JSON message serialization (Gson)
- Multithreading for connections

---

## 🖥️ Phase 3: API & Interface

**Goal:** Add REST API and web dashboard for easy interaction.

| Sprint | Title | Key Deliverables |
|--------|-------|------------------|
| 12 | REST API | HTTP endpoints, JSON responses, Swagger docs |
| 13 | Web Dashboard | HTML/JS frontend, real-time updates, wallet UI |
| 14 | Smart Contracts | Script interpreter, basic conditions, contract storage |
| 15 | Multi-sig Wallets | M-of-N signatures, threshold signing |

### API Endpoints (Sprint 12)
```
GET  /api/blocks              # List all blocks
GET  /api/blocks/{index}      # Get block by index
GET  /api/blocks/latest       # Get latest block
POST /api/transactions        # Submit transaction
GET  /api/transactions/{id}   # Get transaction
GET  /api/wallet/{address}    # Get wallet balance
POST /api/wallet/create       # Create new wallet
POST /api/mine                # Mine pending transactions
GET  /api/network/peers       # List connected peers
GET  /api/network/status      # Network status
```

### Technologies
- Javalin or Spark Java (lightweight HTTP server)
- HTML5 + Vanilla JS (or htmx for simplicity)
- WebSocket for real-time updates

---

## 🏭 Phase 4: Production Features

**Goal:** Add features needed for real-world usage.

| Sprint | Title | Key Deliverables |
|--------|-------|------------------|
| 16 | Database | SQLite/H2 persistence, indexed queries, backup/restore |
| 17 | Difficulty Adjustment | Dynamic difficulty, target block time, adjustment algorithm |
| 18 | Block Limits | Max block size, transaction limits, fee priority |
| 19 | Fee Market | Fee estimation, replace-by-fee, mempool eviction |

### Technologies
- SQLite or H2 Database
- JDBC for database access

---

## 🌟 Bonus Features (Optional)

| Feature | Description | Complexity |
|---------|-------------|------------|
| Light Clients (SPV) | Verify transactions without full chain | High |
| Blockchain Explorer | Full web app to browse chain | Medium |
| Mobile Wallet | Android app for wallet management | High |
| CLI Tool | Command-line interface for node management | Low |
| Docker Support | Containerized deployment | Low |
| Testnet Mode | Separate test network with faucet | Medium |

---

## 📅 Estimated Timeline

| Phase | Sprints | Estimated Duration |
|-------|---------|-------------------|
| Phase 1: Core | 0-7 | 8 weeks |
| Phase 2: Network | 8-11 | 6 weeks |
| Phase 3: API | 12-15 | 6 weeks |
| Phase 4: Production | 16-19 | 6 weeks |
| **Total** | **20 sprints** | **~26 weeks (6 months)** |

---

## 🎯 Skills You'll Learn

### Phase 1 (Current)
- Cryptographic hashing (SHA-256)
- Digital signatures (ECDSA)
- Data structures (linked lists, Merkle trees)
- Object-oriented design

### Phase 2
- Socket programming
- Network protocols
- Concurrent programming
- Distributed systems

### Phase 3
- REST API design
- Web development
- Simple language interpreters
- Cryptographic schemes (multi-sig)

### Phase 4
- Database design
- Algorithm design (difficulty adjustment)
- Economic incentive systems
- Performance optimization

---

## 🏆 Portfolio Value

When complete, BlockSmith will demonstrate:

1. **Backend Development** - Java, REST APIs, databases
2. **Distributed Systems** - P2P networking, consensus
3. **Cryptography** - Hashing, signatures, key management
4. **Full-Stack** - API + Web dashboard
5. **Software Architecture** - Clean code, testing, documentation

This is a **senior-level project** that shows deep understanding of both theory and implementation.

---

## 📚 References

- [Bitcoin Whitepaper](https://bitcoin.org/bitcoin.pdf)
- [Ethereum Yellow Paper](https://ethereum.github.io/yellowpaper/paper.pdf)
- [Mastering Bitcoin](https://github.com/bitcoinbook/bitcoinbook)
- [Build Your Own Blockchain](https://andersbrownworth.com/blockchain/)
