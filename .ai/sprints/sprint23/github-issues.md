# Sprint 23: GitHub Issues

> Created with `--body-file` (one file per issue) after a dry run.

---

## Milestone 23a: API: the node holds no member keys

---

### Anyone can claim a multisig: the node holds the member keys and signs for any claimer

**Labels:** `sprint-23`, `milestone-23a`, `bug`

**Description:**

Reproduced on master (signatures required): Alice funds a 2-of-3 multisig with
30; Mallory, with no key, calls `POST /api/multisig/claim` with claimer =
mallory, gets 201, and has 30 after one block. The node keeps the member
private keys (in memory: lost on restart) and signs claims for whoever asks.

**Fix:**
- `POST /api/multisig/create` from member public keys; the node keeps nothing
- `POST /api/multisig/claim/prepare` -> claim sighash, member keys in order, threshold
- `POST /api/multisig/claim` with {publicKey, signature} pairs; the node orders
  them by member position, builds the unlocking script, the VM decides
- clear 400s (unknown member, too few signatures, not an open multisig)

**Acceptance Criteria:**
- [ ] A claim without member signatures is refused
- [ ] Compiles with `mvn compile`

---

### Tests for client-signed multisig claims

**Labels:** `sprint-23`, `milestone-23a`, `test`

**Description:**

**Test Cases:**
1. `claimWithoutSignatures_refused` (the probe)
2. `mMemberSignatures_anyOrder_accepted`
3. `signaturesForAnotherClaimer_refused`
4. `nonMemberOrDuplicateKey_refused`
5. `nodeHoldsNoKeys_restartLosesNothing`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 23b: Dashboard: members sign in the browser

---

### Multisig member keys and claim signing in the dashboard

**Labels:** `sprint-23`, `milestone-23b`, `enhancement`

**Description:**

- Deploy a multisig from member keys made in this browser, or from pasted
  member public keys
- Claim drawer: prepare -> sign with local member keys -> copy the message /
  paste other members' signatures -> submit when M are collected
- "Sign as a multisig member" tool (paste message, pick key, copy signature)

**Acceptance Criteria:**
- [ ] A 2-of-3 deployed and claimed from the dashboard with browser keys
- [ ] Compiles with `mvn compile`

---

### Dashboard multisig flow checks

**Labels:** `sprint-23`, `milestone-23b`, `test`

**Description:**

**Test Cases:**
1. `dashboardMultisig_createFromKeys_returnsScriptOnly`
2. `dashboardMultisig_claimWithPastedSignature` (API-level, mirrors the UI)
3. Live: deploy + claim in a headless browser, no JS errors

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 23c: Two signers, two browsers + interop + docs

---

### Two-signer live check and multisig docs

**Labels:** `sprint-23`, `milestone-23c`, `enhancement`

**Description:**

- Live check with two browser profiles, one member key each: A prepares and
  signs, B signs the copied message, A pastes and submits; mined
- Docs: multisig flow in ARCHITECTURE; the "server-held member keys" item
  leaves the STATUS deferred list

**Acceptance Criteria:**
- [ ] A claim signed in two different browsers is accepted and mined
- [ ] Compiles with `mvn compile`

---

### Interop: a browser claim signature passes CHECKMULTISIG

**Labels:** `sprint-23`, `milestone-23c`, `test`

**Description:**

**Test Cases:**
1. `webCryptoClaimSignature_passesCheckMultiSig` (vector from a real browser)
2. `webCryptoClaimSignature_otherClaimer_fails`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

*Created: 2026-11-14 | Sprint 23 Planning*
