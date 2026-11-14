# Sprint 22: Required Signatures

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 22 |
| **Title** | Required Signatures |
| **Phase** | Hardening (after the roadmap) |
| **Status** | ✅ Complete (2026-11-14) |
| **Depends On** | Sprint 5 (wallets, ECDSA), Sprint 18a (block content rules), Sprint 19 (sequences, bumps) |

> **Why:** a probe on master (not committed), with a funded Victim wallet:
>
> | Attack | Result |
> |--------|--------|
> | Unsigned transfer, `sender` = Victim's address | **accepted** |
> | Transfer signed with **Mallory's own key**, `sender` = Victim's address | **accepted** |
>
> After one block Mallory had 80 (50 reward + 30 stolen), Victim 20, and
> `isChainValid()` was true. Two holes:
> 1. unsigned transfers are allowed for any sender (an educational shortcut
>    from Sprint 12, when the API had no way to sign)
> 2. `verifySignature()` checks the signature against the public key the
>    transaction CARRIES, but never that this key belongs to the sender:
>    the address is `0x` + the last 40 hex of SHA-256(public key), and
>    nobody compares the two
>
> Also found: the dashboard cannot sign at all. `POST /api/wallet/create`
> generates a key pair on the server, returns the address and throws the
> private key away.

---

## Goal

Only the owner of an address can spend from it. A transfer is valid only if
it is signed by a key whose address is the sender (coinbase and contract
claims are authorised by their own rules). Keys are created and kept in the
browser; the node never sees a private key.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **22a** | Consensus: the sender must sign | `sprint22a/sender-must-sign` | ✅ PR #338 (#331, #332) |
| **22b** | API: prepare, sign elsewhere, submit | `sprint22b/prepare-submit` | ✅ PR #339 (#333, #334) |
| **22c** | Dashboard: keys and signing in the browser | `sprint22c/browser-signing` | ✅ PR #340 (#335, #336) |

---

## Milestone 22a: Consensus: the sender must sign

### Deliverables

- [ ] `Wallet.addressOf(PublicKey)` (the one derivation, shared with
      `Wallet`), and `Transaction.isSignedBySender()`: a signature that
      verifies AND a public key whose address is the sender
- [ ] **Always** (whatever the params): a transaction that carries a key
      must carry the sender's key. Closes hole 2 everywhere
- [ ] `ChainParams.requireSignatures` (consensus): when true, every transfer
      must be signed by its sender. Exempt: the coinbase, and contract
      claims (the sender is the contract; its script authorises the spend)
- [ ] `ChainParams.DEFAULT` requires signatures (the real node); a named
      copy `withoutSignatureRule()` for tests about OTHER rules (the role of
      Bitcoin's regtest)
- [ ] Checked in `addTransaction` (mempool) and the block rules (peers,
      restore, reorg replay, `isChainValid`)
- [ ] Existing tests that use named senders ("alice") switch to explicit
      unsigned params through one test helper; the demo uses wallets
- [ ] Tests: both probe attacks rejected (mempool and peer block), the owner's
      signed transfer accepted, a wrong key rejected even without the rule,
      contract deploys need the funder's signature, claims still work, fee
      bumps must be re-signed, a restore of an old unsigned chain under the
      rule stops at the first unsigned transfer

---

## Milestone 22b: API: prepare, sign elsewhere, submit

### The problem

The node signs nothing (it holds no keys), and the browser cannot rebuild
the exact text the node verifies: Java writes the amount 10 as `10.0`,
JavaScript as `10`, and the timestamp, fee and sequence must match to the
character. So the node builds the transaction and hands out the text to sign.

### Deliverables

- [ ] `POST /api/transactions/prepare` {sender, recipient, amount, fee}:
      the node builds the transaction (timestamp, sequence, id) and returns
      a template id + the exact `signatureData`; templates are kept for a few
      minutes, bounded
- [ ] `POST /api/transactions` {templateId, signature, publicKey} (hex): the
      node attaches them, and the normal rules decide. An unsigned submission
      is a 400 "signature required" when the rule is on
- [ ] Same two steps for a contract deploy and for a fee bump (a bump is a
      new version of the payment, so it is signed again)
- [ ] `POST /api/wallet/create` is retired (a key made on the server is not
      the user's key)
- [ ] Tests: prepare -> sign with a Java wallet -> submit -> mined; tampered
      template, wrong key, unknown/expired template rejected; deploy and bump
      the same way

---

## Milestone 22c: Dashboard: keys and signing in the browser

### Deliverables

- [ ] Web Crypto ECDSA P-256 key pair in the browser; the address computed in
      JavaScript the same way as Java (SHA-256 of the hex SPKI public key,
      last 40 hex digits)
- [ ] Keys kept in `localStorage` (educational; the UI says so), with export
      and import
- [ ] Send, deploy and bump: prepare -> sign `signatureData` -> convert Web
      Crypto's r||s signature to DER (what Java verifies) -> submit
- [ ] **Cross-implementation test:** a Java test verifies a signature and an
      address produced by Web Crypto in a real browser (a fixed vector), so
      the two implementations cannot drift apart silently
- [ ] Live check in a browser; docs (ARCHITECTURE, STATUS: the item
      "API transactions are unsigned" leaves the deferred list)

---

## Theory: who may spend

```
 address = "0x" + last 40 hex of SHA-256(hex(public key))

 a transfer from A is valid only if
   signature verifies with the attached public key K   (the data was not changed)
   AND address(K) == A                                 (K is A's key)

 knowing A's address is not enough: you need the private key behind it
```

A signature alone proves "someone with key K signed this". It only proves
"the owner of A signed this" once the node checks that K hashes to A.
Bitcoin's P2PKH script does exactly that: `OP_DUP OP_HASH160 <A>
OP_EQUALVERIFY OP_CHECKSIG` first hashes the supplied key and compares it to
the address, then checks the signature.

---

## Open decisions

- **Secure default, tests opt out explicitly** (recommended):
  `ChainParams.DEFAULT` requires signatures; tests about other rules use
  `withoutSignatureRule()` via one helper (about 80 call sites, mechanical)
  vs rewriting all 178 test transactions to be signed (much more churn,
  no extra safety: the signature rule gets its own tests)
- **Key-to-address binding always on**, even without the rule (recommended:
  a signed transaction with someone else's key is never legitimate)
- **Prepare/submit** (recommended) vs rebuilding the signed text in
  JavaScript (fragile: number formatting must match Java exactly)
- **Old local chains** (`data/`) contain unsigned transfers: under the rule a
  restore stops at the first one. Start a fresh data directory; no
  activation height (this is a learning network, not a live one)

---

*Created: 2026-11-14 | Sprint 22 Planning | Completed: 2026-11-14*
