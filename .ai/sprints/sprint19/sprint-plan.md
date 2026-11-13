# Sprint 19: Fee Market

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 19 |
| **Title** | Fee Market |
| **Phase** | Phase 4: Production Features (last planned sprint) |
| **Status** | 📋 Planned |
| **Depends On** | Sprint 18 Complete (fees, `feeRate()`, block limits, `getPendingInMiningOrder()`) |

> **Where we are:** since Sprint 18 a block holds at most 16 KB / 50
> transactions and miners pick the best fee per byte. But the mempool itself
> is unbounded (anyone can queue a million zero-fee transactions), a sender
> has no idea what fee is enough, and a transaction stuck with a low fee can
> never be sped up. Those three gaps are what turns "fees exist" into a fee
> *market*.

---

## Goal

The mempool has a size limit and drops the worst-paying transactions when
full, a stuck transaction can be replaced by the same payment with a higher
fee (and the network guarantees only one of them ever confirms), and a node
can suggest a fee for "next block", "within 3 blocks" and "within 6 blocks".

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **19a** | Mempool limit + eviction | `sprint19a/mempool-limit` | ⬜ Planned (#295, #296) |
| **19b** | Replace-by-fee (consensus) | `sprint19b/replace-by-fee` | ⬜ Planned (#297, #298) |
| **19c** | Fee estimation | `sprint19c/fee-estimation` | ⬜ Planned (#299, #300) |
| **19d** | API + dashboard | `sprint19d/fee-market-api` | ⬜ Planned (#301, #302) |

---

## Milestone 19a: Mempool limit + eviction

### Deliverables

- [ ] `core.MempoolPolicy` record (node **policy**, not consensus, so it is
      kept apart from `ChainParams`): `maxBytes` (default 64 KB = 4 full
      blocks), `maxTransactions` (200), `incrementalFeeRate` (the minimum
      step a fee rate must beat, in BSC per byte)
- [ ] `Blockchain(store, params, policy)`; existing constructors use `DEFAULT`
- [ ] When a new transaction does not fit:
  - its fee rate must beat the lowest pending one by `incrementalFeeRate`,
    otherwise it is rejected ("mempool full, minimum fee rate X")
  - the lowest fee-rate transactions are evicted (newest first among equal
    rates) until it fits; evicted ones are removed from the store too
- [ ] `getMempoolMinFeeRate()`: 0 while there is room, else lowest pending
      rate + increment (what a new transaction must pay to get in)
- [ ] Restore: a stored mempool larger than the policy is trimmed the same way
- [ ] Tests: full pool rejects a cheap tx, a better-paying tx evicts the
      cheapest, sender's pending outgoing is freed, equal rates evict newest,
      byte limit and count limit, restore trims, blocks are unaffected
      (policy never rejects a block)

---

## Milestone 19b: Replace-by-fee (consensus)

### The problem

In Bitcoin a replacement spends **the same coins** (inputs) as the original,
so the two conflict and at most one can ever be mined. BlockSmith is
account based: a "replacement" with a new id is just a second payment, and
both could be mined, paying the recipient twice. A conflict needs a key.

### Deliverables

- [ ] `Transaction.sequence` (optional, `Long`, absent = not replaceable).
      Like the fee in 18c, it is part of the id and signed data **only when
      set**, so every existing transaction keeps its id and signature
- [ ] **Consensus:** a `(sender, sequence)` pair confirms at most once, in a
      block and across the chain (a `confirmedSequences` set next to
      `confirmedTxIds`; restore replay rebuilds it). Same idea as Ethereum's
      account nonce, but not required to be consecutive, so any subset of
      the mempool is still a valid block
- [ ] **Mempool policy** (BIP125-style) for a tx whose `(sender, sequence)` is
      already pending:
  - same recipient and amount (only the fee may change; keeps it a "bump")
  - higher absolute fee **and** fee rate >= old rate + `incrementalFeeRate`
  - sender can afford it with the old one's cost released
  - then the old one is removed (pool and store) and the new one added
- [ ] When a block confirms `(sender, sequence)`, pending conflicts are dropped
- [ ] Tests: bump accepted and replaces, too-small bump rejected, changed
      amount/recipient rejected, txs without sequence never conflict, a block
      with both versions rejected, a replacement of a confirmed tx rejected
      (and vice versa), mempool conflict dropped when the other version is
      mined, txs without a sequence keep their old ids, restore replay

---

## Milestone 19c: Fee estimation

### Deliverables

- [ ] `Blockchain.estimateFeeRate(int targetBlocks)`:
  - **mempool view**: fill `targetBlocks` simulated blocks from
    `getPendingInMiningOrder()` under the block limits; if everything fits,
    the estimate is the floor; otherwise the rate of the last tx that made
    it into block `targetBlocks`, plus the increment (to beat it)
  - **history view**: if recent blocks were full, the median of the lowest
    fee rate included in each of the last N blocks
  - result = max(mempool view, history view, `getMempoolMinFeeRate()`)
- [ ] Targets 1, 3 and 6 blocks ("fast", "normal", "slow")
- [ ] Tests: empty mempool -> floor, mempool over one block -> must beat the
      cut-off, longer target never costs more, history raises the estimate
      after full blocks, full mempool -> at least the min fee rate

---

## Milestone 19d: API + dashboard

### Deliverables

- [ ] `GET /api/fees/estimate`: fee rate for 1 / 3 / 6 blocks and the fee for
      a typical transaction, plus `mempoolMinFeeRate`
- [ ] `POST /api/transactions`: new transactions get a `sequence` (so they are
      replaceable); clear 400 for "mempool full" with the minimum fee rate
- [ ] `POST /api/transactions/{id}/bump` with a new `fee`: builds the
      replacement (same sender, recipient, amount, sequence) and submits it
- [ ] `GET /api/mempool` adds the policy limits, bytes used and
      `mempoolMinFeeRate`
- [ ] Dashboard: suggested fees (fast / normal / slow) in the send drawer;
      a Mempool drawer listing pending txs by fee rate with a "Bump fee" action
- [ ] Tests: estimate shape, bump end to end (old gone, new pending, miner
      gets the new fee), bump too small 400, mempool-full 400

---

## Theory: why a fee market

```
                     block space is scarce (16 KB per ~10 s)
                                     |
            +------------------------+------------------------+
            |                        |                        |
   miners pick the best       the mempool is bounded    users need to know
   fee per byte (Sprint 18)   too: cheapest are evicted  what to pay: estimate
                                     |                   from the mempool and
                        a stuck tx can be bumped         recent blocks
                        (RBF): same payment, higher fee,
                        and only one version can confirm
```

Fees are the price of block space. When demand is low every transaction
fits and fees can be zero; when blocks are full, the fee per byte decides
who waits. Bitcoin's block reward halves until it reaches zero, so in the
long run fees are what pays miners to secure the chain.

---

## Dependencies

- Sprint 18c/18d: `fee`, `feeRate()`, `getPendingInMiningOrder()`, `/api/mempool`
- Sprint 18a: one rule set for every block path (the sequence rule joins it)
- Sprint 16: pending transactions are stored, so eviction and replacement
  must update the store

---

## Open decisions

- **Conflict key = optional `sequence` on the transaction** (recommended:
  makes RBF safe in an account model without forcing consecutive nonces) vs
  RBF only inside one node's mempool (unsafe: both versions could be mined
  by different nodes)
- **Bump keeps recipient and amount** (recommended for a first version) vs
  allowing any change (Bitcoin allows it, but then "replace" is really
  "cancel and send something else")
- **Mempool limits are policy, not consensus**: two nodes may hold different
  mempools; a block is never rejected because of them
- **Out of scope**: child-pays-for-parent (no parent/child links between
  transactions here), mempool expiry by age, Bitcoin Core's
  confirmation-time-bucket estimator

---

*Created: 2026-11-13 | Sprint 19 Planning*
