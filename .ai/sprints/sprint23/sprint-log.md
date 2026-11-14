# Sprint 23: Client-side Multisig - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-14 |
| **Sprint End** | 2026-11-14 |

---

## Planning probe (not committed)

On master, node with signatures required: Alice funded a 2-of-3 multisig with
30 (signed deploy). Mallory, with no key, called `POST /api/multisig/claim`
with claimer = mallory: 201, and after one block Mallory had 30.

---

## Milestone 23a: API: the node holds no member keys - Complete (PR #349)

- `POST /api/multisig/create` from member public keys (the old `{members: N}`
  is refused with an explanation; bad or duplicate keys 400); nothing kept.
- `POST /api/multisig/claim/prepare` -> the claim sighash, member keys in
  script order, threshold, amount.
- `POST /api/multisig/claim` with {publicKey, signature} pairs: pre-checked
  one by one (member? twice? verifies for this claimer?), ordered by member
  position, exactly M into the unlocking script; the VM decides.
- `MultiSigWallet.fromLockingScript` (canonical form only). Server-side
  sessions and key generation removed.
- `ApiMultiSigTest` rewritten (6) on real network params; the probe is
  refused. Mutation: without the pre-check, Bob's signatures still cannot
  pay Mallory (the VM refuses): consensus is the gate, the API only words
  the error. Suite: 419 -> 423. Issues #342, #343.

---

## Milestone 23b: Dashboard: members sign in the browser - Complete (PR #350)

- Deploy from three new member keys here (2-of-3) or from pasted public keys
  (+ a new member key in this browser) with M; keys labelled
  `member N of M-of-N · contract`; keys made for a failed deploy are removed.
- Claim drawer: prepare & sign with the local member keys, member states,
  copyable claim request, paste other members' signatures (another contract,
  another claimer, non-member, already signed: refused), submit at M.
- "Multisig member" drawer: keys with copyable public keys, new member key,
  check a request against our own node (a mismatch is refused: never sign a
  hash blind), sign as member N.
- **Found in the live check:** pasting a signature from a member who had
  signed already replaced theirs silently; now "Member N has signed already".
- Tests (+3): create returns the script only, pasted-signature claim, the
  served script uses the new API. Live in headless Edge: both deploy modes,
  tampered request refused, hashlock unchanged. Suite: 423 -> 426.
  Issues #344, #345.

---

## Milestone 23c: Two signers, two browsers + interop + docs - Complete (PR #351)

- Live with two separate Edge profiles: B made a member key, A deployed a
  2-of-3 (A's key, B's key, an offline key), A prepared and signed, B checked
  and signed as member 2, A pasted and submitted; the claimer got 12. Each
  profile held only its own key.
- `WebCryptoInteropTest` (+2): that claim's signatures pass `CHECKMULTISIG`
  in Java; reversed order, one signature, the same member twice, or another
  claimer fail. Two mutations caught.
- ARCHITECTURE: multisig flow; STATUS: the server-held keys issue is gone.
- Suite: 426 -> 428. Issues #346, #347.

---

## Retrospective

- The probe was blunt (Mallory claimed with no key at all); the fix made
  the node a coordinator: Sprint 22's prepare / sign / submit with M
  signers instead of one.
- Mutation testing showed where the real protection is: the script VM. The
  API checks exist to say WHICH signature is wrong.
- The dashboard's "check the request" step matters as much as the
  signature: a member who signs a hash they cannot read signs anything.
- Multisig contracts created before Sprint 23 (node-held keys) cannot be
  claimed: start a fresh `data/` directory.

---

*Sprint 23 Log*
