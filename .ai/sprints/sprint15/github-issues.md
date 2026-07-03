# Sprint 15: GitHub Issues

> Copy each issue to GitHub. Actual issue numbers are assigned on creation
> (PRs consume the same number space, so they will not be contiguous).

---

## Milestone 15a: Signature opcodes in the VM

---

### Add CHECKSIG and CHECKMULTISIG to the script VM

**Labels:** `sprint-15`, `milestone-15a`, `enhancement`

**Description:**

- `SignatureUtil`: ECDSA sign/verify (secp256r1, SHA256withECDSA) + hex
  encode/decode for public keys and signatures
- `ScriptVM.execute(...)` gains an optional sighash (the message a spender
  authorizes); ordinary scripts ignore it
- `CHECKSIG`: pop pubkey + signature, push 1 if valid for the sighash else 0
- `CHECKMULTISIG`: pop N, N pubkeys, M, M signatures; push 1 iff at least M
  signatures are valid under distinct keys (ordered, Bitcoin-style)
- Never-throws preserved: malformed keys/sigs/counts evaluate to false

**Acceptance Criteria:**
- [ ] CHECKSIG true only for a valid signature over the sighash
- [ ] CHECKMULTISIG true for M-of-N, false below threshold
- [ ] Malformed input never throws
- [ ] Compiles with `mvn compile`

---

### Tests for the signature opcodes

**Labels:** `sprint-15`, `milestone-15a`, `test`

**Description:**

**Test Cases:**
1. `checkSig_trueForValidSignature_falseOtherwise`
2. `checkMultiSig_twoOfThree_valid`
3. `checkMultiSig_insufficientSignatures_false`
4. `checkMultiSig_wrongKeySignature_false`
5. `checkMultiSig_duplicateSignatureNotDoubleCounted`
6. Malformed hex / count mismatch -> false, no exception

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Milestone 15b: Multisig contracts (M-of-N)

---

### Multisig wallet + signature-locked contracts

**Labels:** `sprint-15`, `milestone-15b`, `enhancement`

**Description:**

- `MultiSigWallet`: N member public keys + threshold M; builds the
  `CHECKMULTISIG` locking script; derives a stable multisig address
- Claim sighash: `SHA256(contractId + claimer + amount)` - binds a signature
  to one claim so it cannot be replayed to a different claimer
- Claim path feeds the claim's sighash to the VM so `CHECKMULTISIG` can verify
  the presented signatures; unlocking data carries the M signatures
- Reuses Sprint 14 contract deploy/claim/registry unchanged

**Acceptance Criteria:**
- [ ] 2-of-3 deploy then claim with 2 valid signatures moves the funds
- [ ] Claim with fewer than M valid signatures is rejected
- [ ] A signature for one claimer cannot be replayed for another
- [ ] Compiles with `mvn compile`

---

### Tests for multisig contracts

**Labels:** `sprint-15`, `milestone-15b`, `test`

**Description:**

**Test Cases:**
1. `twoOfThree_claimWithTwoSignatures_creditsClaimer`
2. `claimWithOneSignature_rejected`
3. `claimWithWrongKeySignature_rejected`
4. `signatureReplayToDifferentClaimer_rejected`
5. `externalBlocks_buildSameMultisigState`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Milestone 15c: API + dashboard integration

---

### Multisig endpoints + dashboard panel

**Labels:** `sprint-15`, `milestone-15c`, `enhancement`

**Description:**

- `POST /api/multisig/create` - create an M-of-N wallet; return address,
  member public keys, and the locking script
- Claim assembly endpoint: produce/submit a multisig claim with the collected
  member signatures (server-side signing convenience, flagged educational)
- Dashboard panel: create a multisig, show members/threshold, claim from it;
  errors surfaced inline
- JSON error envelope for rejected creates/claims (Sprint 12 style)

**Acceptance Criteria:**
- [ ] A user can create a multisig and claim from it in the UI
- [ ] Errors are shown to the user
- [ ] Compiles with `mvn compile`

---

### Tests for multisig endpoints

**Labels:** `sprint-15`, `milestone-15c`, `test`

**Description:**

**Test Cases:**
1. `multisigLifecycle_overHttp` (create -> deploy -> claim -> balances move)
2. `rejectedMultisigClaim_returnsErrorEnvelope`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Summary

| Milestone | Issues | Tests |
|-----------|--------|-------|
| 15a: Signature opcodes | 2 | ~10 tests |
| 15b: Multisig contracts | 2 | ~5 tests |
| 15c: API + dashboard | 2 | ~2 tests |
| **Total** | **6 issues** | **~17 tests** |

> 15a is the test-heavy milestone: signature verification is pure crypto logic,
> so it gets exhaustive unit coverage. 15c follows the Sprint 13-14 pattern -
> the browser UI is verified by running it; automated tests cover the server.

---

*Created: 2026-07-03 | Sprint 15 Planning*
