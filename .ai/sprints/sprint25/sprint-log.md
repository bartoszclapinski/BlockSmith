# Sprint 25: Loose Ends: Contract IDs, Coinbase Height, Key Rotation - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-14 |
| **Sprint End** | 2026-11-15 |

---

## Planning probe (not committed)

1. Contract front-run (signatures required): Alice's signed hashlock deploy
   of 30 waited in the mempool; Mallory signed a deploy of 0.1 with
   `PUSH 1` and fee 1 to the same `CONTRACT:<id>`. After one block the
   registry held Mallory's contract (0.1, `PUSH 1`), the address 30.1; a
   claim took 0.1, the contract became CLAIMED and 30 stayed at the address.
2. Two blocks with coinbases for one miner made in the same millisecond
   (same id): both accepted, balance 100, history 1 entry, lookup of the
   first reward returned block 2.

---

## Milestone 25a: Contract ids bound to their deploy - Complete (PR #371)

- `contractIdFor = SHA-256(funder | lockingScript | amount | sequence)`;
  `buildDeploy` gives every deploy a random sequence; mempool and block
  rules refuse a deploy without a sequence or with an id that is not its own.
- The sequence, not the timestamp: `withFee` builds a new transaction (new
  timestamp) but keeps the sequence, so a bumped deploy keeps its id.
- `ContractIdTest` (5): the probe refused in the mempool and in a block,
  Alice claims her 30 and nothing stays at the address. 4 mutations caught.
  Two tests that deployed to hand-written ids now use `buildDeploy`.
- **Breaking:** chains with deploys made before this version stop restoring
  at the first one. Suite: 435 -> 440. Issues #364, #365.

---

## Milestone 25b: Unique coinbases + index by position - Complete (PR #372)

- The block height in the coinbase sequence (BIP34); a stated height must be
  the block's own; old blocks without one stay valid (no break).
- `tx_index` keyed by (block_idx, pos); lookup by id returns the earliest;
  an old index keyed by tx_id is dropped on open and refilled by the restore
  (which re-saves every block).
- `CoinbaseHeightTest` (4); 5 mutations caught. Suite: 440 -> 444.
  Issues #366, #367.

---

## Milestone 25c: Move funds to a new key + docs - Complete (PR #373)

- "Move funds to a new key": new key, one transfer of
  `floor((balance - pending - fee) * 1e8) / 1e8` signed by the old key, old
  key kept as `retiredTo`; a failed move removes the new key.
- `plaintextBefore` on migrated keys and plaintext imports: warnings until
  the funds are moved.
- **Found while building it:** a passphrase change rewrote keystore entries
  with four fields only (extra fields lost); entries are now copied whole.
- Live: 14/14 checks in two profiles; a non-zero fee with a fractional
  balance (49.6566, fee 0.0137) moved 49.6429 and left 0. One FAIL in the
  first run was the script's own: "Mine block" paid the reward to the new
  active key (150 instead of 100); the check now mines to another address.
- `DashboardKeystoreTest` +1; 5 mutations caught. Docs: ARCHITECTURE,
  STATUS. Suite: 444 -> 445. Issues #368, #369.

---

## Retrospective

- The "cosmetic" deferred item (a contract id from the clock) hid a way to
  destroy someone else's funds: probing the item, not reading its title,
  found it.
- Binding the id to the deploy's own fields is the general fix (Ethereum
  does the same with creator + nonce); the sequence rule from Sprint 19
  then gives uniqueness for free.
- The coinbase fix needed no break: an optional field that new blocks always
  carry, and an index that no longer depends on the id being unique.
- Key rotation is the honest answer to "the old plaintext cannot be erased".

---

*Sprint 25 Log*
