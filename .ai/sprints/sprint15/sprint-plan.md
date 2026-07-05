# Sprint 15: Multi-signature Wallets

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 15 |
| **Title** | Multi-signature Wallets |
| **Phase** | Phase 3: API & Interface |
| **Status** | ✅ Complete (15a, 15b, 15c) |
| **Depends On** | Sprint 14 Complete (Smart Contracts / script VM) |

> **Approach (recommended):** build multisig ON the Sprint 14 script VM. Add
> signature-checking opcodes (`CHECKSIG`, `CHECKMULTISIG`); an M-of-N multisig
> is then just a **contract** whose locking script is a `CHECKMULTISIG`, reusing
> all of Sprint 14's deploy/claim/registry machinery. This is exactly how
> Bitcoin does multisig (P2SH). The one genuinely new piece is a **sighash**:
> the message a spender signs to authorize a specific claim, bound to the
> claimer and amount so a signature cannot be replayed elsewhere.

---

## Goal

Let funds require **M of N** signatures to move, instead of a single key. A
2-of-3 escrow, a shared treasury, a backup-key setup - all expressed as a
multisig contract. The capstone of Phase 3: it unifies the wallet (Sprint 5),
the contract model (Sprint 14), and the script VM into one feature, and drives
end to end from the dashboard.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **15a** | Signature opcodes in the VM | `sprint15a/sig-opcodes` | ✅ Complete (PR #149) |
| **15b** | Multisig contracts (M-of-N) | `sprint15b/multisig-contracts` | ✅ Complete (PR #152) |
| **15c** | API + dashboard integration | `sprint15c/multisig-api` | ✅ Complete (PR #155) |

---

## Milestone 15a: Signature opcodes in the VM

### Deliverables

- [ ] `SignatureUtil`: ECDSA sign/verify helpers (secp256r1, SHA256withECDSA)
      plus hex encode/decode for public keys and signatures (the VM works on
      string data elements, so keys/sigs travel as hex)
- [ ] `ScriptVM.execute(...)` gains an optional **sighash** (the message the
      spender authorizes); ordinary scripts pass it as empty/ignored
- [ ] `CHECKSIG`: pop a public key and a signature, push 1 if the signature is
      valid for the sighash under that key, else 0
- [ ] `CHECKMULTISIG`: pop N, N public keys, M, and M signatures; push 1 iff at
      least M signatures are valid under distinct keys (ordered, Bitcoin-style)
- [ ] Never-throws preserved: malformed keys/sigs/counts evaluate to false
- [ ] Tests: single-sig valid/invalid, 2-of-3 valid, insufficient signatures,
      wrong-key signature, duplicate signature not double-counted, malformed
      hex, count mismatches

### Why this is first

The crypto is pure logic and belongs in the VM. Building and testing it in
isolation means 15b only wires it to contracts.

---

## Milestone 15b: Multisig contracts (M-of-N)

### Deliverables

- [ ] `MultiSigWallet`: holds N member public keys and a threshold M; builds
      the `CHECKMULTISIG` locking script; derives a stable multisig address
- [ ] A **sighash** for claims: deterministic digest binding the claim to its
      contract, claimer, and amount, so a signature authorizes exactly one
      claim and cannot be replayed to a different claimer
- [ ] Claim path: when a claim targets a multisig contract, feed the claim's
      sighash to the VM so `CHECKMULTISIG` can verify the presented signatures
- [ ] Members sign the sighash; the unlocking data carries the M signatures
- [ ] Tests: 2-of-3 deploy/claim moves funds, insufficient signatures rejected,
      wrong-key signature rejected, signature-replay-to-different-claimer
      rejected, cross-node convergence

### Note

This reuses Sprint 14's contract deploy/claim/registry unchanged - a multisig
is a contract with a signature-checking lock. Only the sighash and the claim's
signature-collection are new.

---

## Milestone 15c: API + dashboard integration

### Deliverables

- [ ] `POST /api/multisig/create` - create an M-of-N wallet, return address +
      member public keys + the locking script
- [ ] Claim assembly: an endpoint to produce/submit a multisig claim with the
      collected member signatures
- [ ] Dashboard panel: create a multisig, show its members/threshold, and
      claim from it; errors surfaced inline
- [ ] Tests (server-side): HTTP multisig lifecycle + rejection envelopes

### Scope note

Signing normally happens client-side with private keys the node never sees.
For this educational build the node offers a **signing convenience** (the
member keys are generated and used server-side), clearly flagged - consistent
with the already-unsigned transaction endpoints from Sprint 12. A production
design would sign in the wallet and submit only signatures.

---

## Theory: M-of-N is just a script

```
Single-sig lock:   <pubkey> CHECKSIG
                   unlock with one signature over the sighash

Multisig lock:     M <pub1> <pub2> <pub3> N CHECKMULTISIG      (e.g. 2 of 3)
                   unlock with any 2 valid signatures over the sighash

The sighash binds the signature to THIS claim:
   sighash = SHA256(contractId + claimer + amount)

So a 2-of-3 signature set authorizing "pay Bob" cannot be replayed to pay Carol
- the message signed is different. This is the same reason Bitcoin signs a
transaction digest, not a fixed string.
```

---

## Dependencies

- Sprint 14 script VM (`ScriptVM`, contract deploy/claim/registry)
- Sprint 5 `Wallet` (ECDSA secp256r1, SHA256withECDSA) - the same primitives
- `HashUtil` for the sighash
- No new dependency

---

## Open decisions

- **Multisig as a contract script** (recommended) vs a standalone multi-sig
  transaction type - the script route reuses Sprint 14 wholesale
- **Sighash contents**: contractId + claimer + amount (recommended) - minimal
  and replay-safe
- **Signing location**: server-side convenience for the demo (recommended,
  flagged educational) vs client-side only (correct but needs a JS crypto stack)

---

*Created: 2026-07-03 | Sprint 15 Planning*
