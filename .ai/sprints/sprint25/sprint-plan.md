# Sprint 25: Loose Ends: Contract IDs, Coinbase Height, Key Rotation

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 25 |
| **Title** | Loose Ends: Contract IDs, Coinbase Height, Key Rotation |
| **Phase** | Hardening (after the roadmap) |
| **Status** | ✅ Complete (2026-11-15) |
| **Depends On** | Sprint 14 (contracts), Sprint 16 (tx_index), Sprint 19 (sequence rule), Sprint 24 (keystore) |

> **Why:** two items on the deferred list looked cosmetic. A probe on master
> (not committed) shows one of them loses money.
>
> **1. A contract deploy can be front-run, and the victim's funds are lost.**
> On a node that requires signatures:
> 1. Alice signs a hashlock deploy of 30; it waits in the mempool, where its
>    address `CONTRACT:<id>` is visible to everyone
> 2. Mallory signs her own deploy TO THE SAME ADDRESS: 0.1, locking script
>    `PUSH 1` (always true), fee 1, so it is mined first
> 3. The registry keeps the first deploy (`putIfAbsent`): the contract is
>    Mallory's (0.1, `PUSH 1`), while the address holds 30.1
> 4. A claim can only take 0.1; after it the contract is CLAIMED and
>    **Alice's 30 is stuck for good**
>
> The id is just a hash of the parameters and the clock, written into the
> recipient by whoever builds the deploy; nothing checks it.
>
> **2. Two rewards in one millisecond share an id, and the index loses one.**
> Two blocks rewarding one miner with coinbases made in the same millisecond:
> both accepted, balance 100 (right), but the address history shows **one**
> reward, and a lookup of the first one points at block 2: `tx_index` is
> keyed by the transaction id and `MERGE` overwrote the row.
>
> **3. Plaintext keys from before Sprint 24 stay in the browser's files**
> (measured in 24c). The fix there is to stop using them: move the funds
> to a new key.

---

## Goal

A contract's id is bound to the deploy that creates it, so nobody else can
deploy to it; every new coinbase is unique (it carries the block height),
and the index survives the old duplicates; the dashboard moves a wallet's
funds to a new key in one step.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **25a** | Contract ids bound to their deploy | `sprint25a/contract-ids` | ✅ PR #371 (#364, #365) |
| **25b** | Unique coinbases + index by position | `sprint25b/coinbase-height` | ✅ PR #372 (#366, #367) |
| **25c** | Move funds to a new key + docs | `sprint25c/move-funds` | ✅ PR #373 (#368, #369) |

---

## Milestone 25a: Contract ids bound to their deploy

### Deliverables

- [ ] `contractId = SHA-256(funder | lockingScript | amount | sequence)`
      (see Theory); `buildDeploy` gives every deploy a random sequence
- [ ] Consensus (mempool and block rules): a deploy must carry a sequence and
      its address must be `CONTRACT:` + that id; anything else is refused
- [ ] A bump keeps the sequence, so a bumped deploy keeps its contract id
- [ ] Tests: the probe (Mallory's deploy to Alice's address) is refused in
      the mempool and inside a block; an id that does not match is refused;
      a bumped deploy keeps its id; the API deploy flow still works

---

## Milestone 25b: Unique coinbases + index by position

### Deliverables

- [ ] Mining puts the block height in the coinbase's `sequence` (Bitcoin's
      BIP34), so new coinbase ids are unique; a coinbase sequence, if
      present, must equal the block index (old blocks without one stay valid)
- [ ] `tx_index` keyed by (block, position); a lookup by id returns the
      earliest; history lists every occurrence; an old index is rebuilt from
      the stored blocks on open
- [ ] Tests: the probe (two same-id rewards: both in the history, the
      lookup finds block 1); two rewards in one millisecond now differ; a
      wrong height is refused; an old-schema database is migrated

---

## Milestone 25c: Move funds to a new key + docs

### Deliverables

- [ ] Wallet drawer: "Move funds to a new key": a new key, then a transfer
      of the whole spendable balance minus the suggested fee, signed by the
      old key; the old key is labelled retired (-> new address), the new one
      becomes active
- [ ] Keys migrated from plaintext (Sprint 24 migration, from now on) are
      flagged and the dashboard suggests moving their funds
- [ ] Tests: served-script checks; live: funds move, the old key is retired,
      nothing is left but dust
- [ ] Docs: ARCHITECTURE (contract ids, coinbase height, index), STATUS
      (three deferred items closed)

---

## Theory

### Who may create a contract id

```
 before:  id = SHA-256(funder + script + amount + clock)   computed by the client, never checked
 now:     id = SHA-256(funder | script | amount | sequence) checked by every node

 Mallory's deploy to Alice's id:   funder = Mallory  ->  a different hash  ->  refused
 Alice's two deploys, same id:     same (sender, sequence)  ->  only one may confirm (Sprint 19 rule)
```

Ethereum derives a contract's address from its creator and the creator's
nonce for the same reason: an address nobody else can claim, known before
the deploy is mined. The sequence (not the timestamp) is used because a fee
bump keeps the sequence and changes the timestamp.

### Why a coinbase carries the height

Two blocks can pay the same miner the same reward in the same millisecond,
and then their coinbases are identical. Bitcoin had real duplicate
coinbases (blocks 91,812 and 91,842); BIP34 made every coinbase include its
block height. An index keyed by position (block, slot) instead of by id
also stops one row from overwriting another.

---

## Open decisions

- **Bind the id with the sequence, as a consensus rule** (recommended: it
  stops the front-run and makes ids reproducible) vs only refusing a second
  deploy to a known contract (non-breaking, but Mallory can still block
  Alice's deploy). **Breaking:** chains with contract deploys made before
  this version do not restore past the first one: fresh `data/` directory
  (release notes say so)
- **Coinbase height optional for old blocks, required from now on by
  miners** (recommended: no break) vs mandatory
- **Retired keys are kept** (recommended: money sent to the old address
  later can still be moved) vs deleted

---

*Created: 2026-11-14 | Sprint 25 Planning | Completed: 2026-11-15*
