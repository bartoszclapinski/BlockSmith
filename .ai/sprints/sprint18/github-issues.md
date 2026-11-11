# Sprint 18: GitHub Issues

> Created with `--body-file` (one file per issue) after a dry run.

---

## Milestone 18a: Block content validation (consensus)

---

### Blocks from peers are accepted without checking their transactions

**Labels:** `sprint-18`, `milestone-18a`, `bug`

**Description:**

A peer block is checked for hash, PoW, Merkle root, link and timestamps, but
not for what its transactions do. Reproduced: a block with a 1,000,000
coinbase and an overspend by Alice was accepted (Mallory 1,000,500, Alice
-500) and `isChainValid()` returned true. Claims in peer blocks are not run
through the script VM.

**Fix:** validate every block's transactions in order against the chain state
plus earlier transactions in the same block:
- exactly one coinbase, first, amount <= reward
- no other `COINBASE` sender; every other tx `isValid()`, signature verifies if present
- sender balance covers the amount
- no duplicate tx id (in block or on chain)
- deploys carry a locking script; claims pass the VM against the contract state
- same rules in `isChainValid()` and for locally mined blocks

**Acceptance Criteria:**
- [ ] The forged block above is rejected
- [ ] Honest blocks (incl. contracts and multisig) are still accepted
- [ ] Restore rejects a stored forged block
- [ ] Compiles with `mvn compile`

---

### Tests for block content validation

**Labels:** `sprint-18`, `milestone-18a`, `test`

**Description:**

**Test Cases:**
1. `inflatedCoinbase_rejected`
2. `twoCoinbases_or_coinbaseNotFirst_rejected`
3. `overspend_rejected` (single tx and split across two txs)
4. `duplicateTransaction_rejected` (in block, already on chain)
5. `invalidClaimInPeerBlock_rejected`
6. `honestBlocks_stillAccepted` (transfers, contracts, multisig)
7. `restore_rejectsForgedStoredBlock`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 18b: Block size & transaction limits

---

### Maximum block size and transaction count

**Labels:** `sprint-18`, `milestone-18b`, `enhancement`

**Description:**

- Block size = bytes of its JSON (as exchanged by peers)
- `ChainParams.maxBlockBytes` and `maxBlockTransactions`
- Blocks over either limit are rejected (peer, restore, `isChainValid`)
- Mining fills a block up to the limits; the rest stays pending

**Acceptance Criteria:**
- [ ] Oversize / too-many-tx blocks rejected
- [ ] A large mempool is mined over several blocks without losing transactions
- [ ] Compiles with `mvn compile`

---

### Tests for block limits

**Labels:** `sprint-18`, `milestone-18b`, `test`

**Description:**

**Test Cases:**
1. `oversizePeerBlock_rejected`
2. `tooManyTransactions_rejected`
3. `fullMempool_spreadOverBlocks_nothingLost`
4. `limitsChecked_onRestore`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 18c: Transaction fees + fee-priority mining

---

### Transaction fees and fee-rate block assembly

**Labels:** `sprint-18`, `milestone-18c`, `enhancement`

**Description:**

- `Transaction.fee` (default 0), in the id and signed data only when > 0
- Sender pays `amount + fee` (balance + pending outgoing)
- Coinbase may claim `reward + sum(fees)`; miner collects fees
- Block assembly by fee rate (fee per byte), highest first, within limits

**Acceptance Criteria:**
- [ ] Zero-fee transactions keep their old id and signature
- [ ] Coinbase over reward + fees is rejected
- [ ] A full block keeps the best-paying transactions
- [ ] Compiles with `mvn compile`

---

### Tests for fees

**Labels:** `sprint-18`, `milestone-18c`, `test`

**Description:**

**Test Cases:**
1. `fee_inIdAndSignature_zeroFeeCompatible`
2. `sender_paysAmountPlusFee`
3. `coinbase_overRewardPlusFees_rejected`
4. `miner_collectsFees`
5. `fullBlock_keepsHighestFeeRate`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 18d: API + dashboard

---

### Fees and block size in the API and dashboard

**Labels:** `sprint-18`, `milestone-18d`, `enhancement`

**Description:**

- `POST /api/transactions` accepts an optional `fee`
- `GET /api/mempool`: pending txs with fee, size, fee rate, in mining order
- Block JSON / explorer: size in bytes and total fees
- Status: `maxBlockBytes`, `maxBlockTransactions`
- Dashboard: fee field when sending; block cards show size and fees

**Acceptance Criteria:**
- [ ] Fee charged end to end via the API
- [ ] Mempool endpoint ordered by fee rate
- [ ] Compiles with `mvn compile`

---

### Tests for the fee API

**Labels:** `sprint-18`, `milestone-18d`, `test`

**Description:**

**Test Cases:**
1. `postTransaction_withFee_charged`
2. `mempool_orderedByFeeRate`
3. `status_hasBlockLimits`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

*Created: 2026-11-11 | Sprint 18 Planning*
