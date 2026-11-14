# Sprint 23: Client-side Multisig

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 23 |
| **Title** | Client-side Multisig |
| **Phase** | Hardening (after the roadmap) |
| **Status** | ✅ Complete (2026-11-14) |
| **Depends On** | Sprint 15 (multisig contracts, CHECKMULTISIG), Sprint 22 (signatures, browser keys) |

> **Why:** Sprint 15 made multisig claims convenient: `POST /api/multisig/create`
> generates the N member key pairs ON THE NODE and keeps the private keys,
> and `POST /api/multisig/claim` signs the claim with M of them. A probe on
> master (not committed), on a node that requires signatures:
>
> 1. Alice creates a 2-of-3 multisig and funds it with 30 (signed deploy)
> 2. Mallory, who has no key at all, calls `POST /api/multisig/claim` with
>    `claimer` = mallory
> 3. **201**, and after one block Mallory has 30
>
> The node signs for whoever asks, so the "2 of 3 members must agree" lock
> protects nothing against anyone who can reach the API. The member keys
> also live only in memory: after a restart nobody can claim at all. It is
> the last exception to Sprint 22's rule "the node never holds a user's
> private key".

---

## Goal

Multisig member keys are created and kept by the members (the dashboard:
in the browser). The node builds the claim and the message to sign, the
members sign it wherever their keys are, and the node only assembles the
signatures into the unlocking script. Without M real member signatures for
THIS claimer, nothing moves.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **23a** | API: the node holds no member keys | `sprint23a/multisig-api` | ✅ PR #349 (#342, #343) |
| **23b** | Dashboard: members sign in the browser | `sprint23b/multisig-dashboard` | ✅ PR #350 (#344, #345) |
| **23c** | Two signers, two browsers + interop + docs | `sprint23c/multisig-two-signers` | ✅ PR #351 (#346, #347) |

---

## Milestone 23a: API: the node holds no member keys

### Deliverables

- [ ] `POST /api/multisig/create` {memberPublicKeys: [hex X.509...], threshold}:
      returns the locking script and address; the node keeps nothing
      (`multisigSessions` and the server-side signing are removed)
- [ ] `POST /api/multisig/claim/prepare` {contractId, claimer}: returns the
      exact message to sign (the claim sighash, which binds the signatures to
      this contract, this claimer and this amount), the member keys in script
      order and the threshold
- [ ] `POST /api/multisig/claim` {contractId, claimer, signatures: [{publicKey,
      signature}]}: the node orders the signatures by member position (the VM
      matches them left to right), builds `PUSH sig1 PUSH sig2 ...` and submits
      the claim; the script VM decides
- [ ] Clear 400s: unknown member key, too few signatures, contract not a
      multisig / not open
- [ ] Tests: the probe (a claim without signatures) is refused; M member
      signatures in any order are accepted; signatures made for another
      claimer, by a non-member, or the same key twice are refused; a restart
      loses nothing (nothing was on the node)

---

## Milestone 23b: Dashboard: members sign in the browser

### Deliverables

- [ ] Deploy a multisig from member keys made in the browser (the
      convenience path: three new member keys here, labelled; or members'
      public keys pasted, for a wallet shared with other people)
- [ ] Claim drawer: prepare -> sign with every member key held in this
      browser -> for members whose key is elsewhere: copy the message, paste
      their signature -> submit when M are collected
- [ ] "Sign as a multisig member" tool: paste a message, pick a local key,
      copy the signature (what the other member does on their device)
- [ ] The hashlock flow is unchanged

---

## Milestone 23c: Two signers, two browsers + interop + docs

### Deliverables

- [ ] Live check with **two separate browser profiles**, each holding one
      member key of a 2-of-3: profile A prepares the claim and signs, profile
      B signs the copied message, A pastes B's signature and submits; mined
- [ ] Interop vector: a claim signature made by Web Crypto in a real browser
      verifies in the Java script VM (`CHECKMULTISIG`)
- [ ] Docs: ARCHITECTURE (multisig flow), STATUS (the deferred item about
      server-held member keys is removed)

---

## Theory: who signs a multisig

```
 lock (on chain):   PUSH 2  PUSH <key1> PUSH <key2> PUSH <key3>  PUSH 3 CHECKMULTISIG
 claim message:     SHA-256(contractId + claimer + amount)     (the "sighash")
 unlock (claim):    PUSH <sig by key1>  PUSH <sig by key3>     (in key order)

 member 1 (laptop)  ---- signs the message ----+
                                               +--> node assembles -> VM checks 2 of 3
 member 3 (phone)   ---- signs the message ----+
```

The node is a coordinator, not a signer: it knows the message, never a key.
Because the message includes the claimer, a signature collected for "pay
Bob" cannot be reused to "pay Mallory". Bitcoin's PSBT (Partially Signed
Bitcoin Transaction, BIP174) formalises the same round trip between
devices.

---

## Open decisions

- **Signatures sent as {publicKey, signature} pairs, ordered by the node**
  (recommended: members do not need to know their position) vs a raw
  ordered list
- **Convenience path keeps "three keys in this browser"** (a 2-of-3 where one
  person holds all keys teaches the mechanics, not the security), next to
  "paste other members' public keys" for a real shared wallet
- **Existing multisig contracts** created with node-held keys cannot be
  claimed after this change (they already could not after a restart): fresh
  `data/` directory, as after Sprint 22

---

*Created: 2026-11-14 | Sprint 23 Planning | Completed: 2026-11-14*
