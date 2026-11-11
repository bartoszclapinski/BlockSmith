# Sprint 18: Block Rules, Size & Fees

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 18 |
| **Title** | Block Rules, Size & Fees (roadmap: "Block Size & Limits") |
| **Phase** | Phase 4: Production Features |
| **Status** | 📋 Planned |
| **Depends On** | Sprint 17 Complete (numeric target, retargeting) |

> **Why the scope grew:** while planning, a test showed that a block from a
> peer is accepted **without checking its transactions**. A block whose
> coinbase pays 1,000,000 (instead of 50) and in which Alice sends 500 coins
> she never had was accepted: Mallory's balance became 1,000,500, Alice's
> -500, and `isChainValid()` still returned true. Contract claims inside peer
> blocks are not run through the script VM either. Validation so far checks
> that a block is *well built* (hash, PoW, Merkle, timestamps), not that its
> *contents are allowed*. Block limits and fees are content rules too
> ("coinbase <= reward + fees"), so this sprint starts by adding them.

---

## Goal

A node accepts a block only if every transaction in it is allowed, blocks
have a maximum size, and transactions can pay fees that decide which ones get
into a full block. After this sprint a dishonest peer cannot mint coins,
overspend or bypass a contract, and miners pick the best-paying transactions.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **18a** | Block content validation (consensus) | `sprint18a/block-content-rules` | ⬜ Planned (#281, #282) |
| **18b** | Block size & transaction limits | `sprint18b/block-limits` | ⬜ Planned (#283, #284) |
| **18c** | Transaction fees + fee-priority mining | `sprint18c/fees` | ⬜ Planned (#285, #286) |
| **18d** | API + dashboard | `sprint18d/fees-api` | ⬜ Planned (#287, #288) |

---

## Milestone 18a: Block content validation (consensus)

### Deliverables

- [ ] Every block appended from outside (peer, orphan attach, restore replay)
      has its transactions checked **in order**, against the chain state at
      that point plus the transactions before it in the same block:
  - exactly one coinbase, and it is the first transaction
  - coinbase amount <= block reward (fees join in 18c)
  - no other transaction from `COINBASE`
  - every other transaction passes `isValid()`; a signed one must verify
  - sender's balance covers the amount (no overspending, no negative balances)
  - no transaction id appears twice (in the block, or already on the chain)
  - contract deploys carry a locking script; contract claims pass the script
    VM against the contract state at that point (OPEN, right amount, unlock
    script valid at that height)
- [ ] `isChainValid()` runs the same rules over the whole chain
- [ ] Locally mined blocks pass the same check (one code path for all blocks)
- [ ] Tests: inflated coinbase, two coinbases, coinbase not first, overspend,
      overspend split across two txs in one block, duplicate tx (in block /
      already on chain), bad claim inside a peer block, honest blocks still
      accepted, restore rejects a stored forged block

### Why this is first

It closes a hole that lets any peer create money. Limits and fees (18b, 18c)
are additional content rules that plug into the same check.

---

## Milestone 18b: Block size & transaction limits

### Deliverables

- [ ] Block size = bytes of the block's JSON (the same form peers exchange)
- [ ] `ChainParams`: `maxBlockBytes` and `maxBlockTransactions` (defaults sized
      so the dashboard can fill a block, e.g. 32 KB / 100 transactions)
- [ ] Consensus: a block over either limit is rejected (peer, restore,
      `isChainValid`)
- [ ] Mining fills a block up to the limits; the rest stays in the mempool
      for the next block (today the whole mempool goes into one block)
- [ ] Tests: oversize peer block rejected, too many transactions rejected,
      a full mempool is spread over several blocks, nothing is lost

---

## Milestone 18c: Transaction fees + fee-priority mining

### Deliverables

- [ ] `Transaction.fee` (default 0)
  - part of the transaction id and of the signed data **only when > 0**, so
    every existing transaction keeps its id and signature
  - sender pays `amount + fee` (balance check, pending outgoing)
- [ ] Coinbase may claim `reward + sum(fees in the block)`; the miner collects
      the fees
- [ ] Block assembly picks transactions by **fee rate** (fee per byte),
      highest first, until the block is full
- [ ] Tests: fee in id and signature (and zero-fee compatibility), sender
      pays amount + fee, coinbase over reward + fees rejected, miner earns the
      fees, a full block keeps the best-paying transactions

### Note

In this account-based model every pending transaction is checked against
confirmed balances only, so any subset of the mempool forms a valid block and
a simple greedy pick by fee rate is correct.

---

## Milestone 18d: API + dashboard

### Deliverables

- [ ] `POST /api/transactions` accepts an optional `fee`
- [ ] `GET /api/mempool`: pending transactions with fee, size and fee rate,
      in the order a miner would pick them
- [ ] Block JSON / explorer shows size in bytes and total fees
- [ ] `GET /api/network/status`: `maxBlockBytes`, `maxBlockTransactions`
- [ ] Dashboard: fee field in the send drawer; block cards show size and fees
- [ ] Tests: fee accepted and charged, mempool order, status fields

---

## Theory: well built vs allowed

```
Well built (checked since Sprints 3-17)      Allowed (missing until 18a)
- hash matches the content                   - only one coinbase, first, <= reward + fees
- meets the expected PoW target              - nobody spends money they don't have
- links to our tip                           - no transaction included twice
- Merkle root matches the transactions       - contract claims satisfy their scripts
- timestamps follow the rules                - block within size limits (18b)
```

Proof-of-Work only proves that work was done, not that the work was spent on
a valid block. Every full node checks every rule itself; this is what lets
Bitcoin nodes reject a miner who pays themselves too much, no matter how much
hash power is behind the block.

---

## Dependencies

- Sprint 17 `ChainParams` (limits join it)
- Sprint 16 restore-by-replay: stored chains are re-validated under the new
  rules automatically
- Sprint 19 (Fee Market) builds on 18c: fee estimation, replace-by-fee,
  mempool eviction

---

## Open decisions

- **Unsigned transactions stay allowed** (the API submits unsigned ones,
  educational); a signed one must verify. Requiring signatures everywhere
  would need client-side signing first
- **Size = JSON bytes** (recommended: what peers actually send) vs a compact
  binary encoding
- **Fee rate = fee / bytes** (Bitcoin's sat/vB idea) vs a flat fee priority
- **Fee in the id only when > 0** (recommended: no migration for old data)

---

*Created: 2026-11-11 | Sprint 18 Planning*
