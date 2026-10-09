# Manual Acceptance — docs/plans/2026-10-08/mailbox-server-drafts-master.md

## Epoch 2 — 2026-10-09

- Reviewed code boundary: `7c86599f85f6462e00a3fcd2c5a74f1ca57f013d..65eb16774c64e6c741a0339a481dda35739d9c8c`
- Machine report epoch: 2
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | In two browsers logged in as the same test user, write `服务器草稿验收` in A, wait for `已保存到服务器`, restore it in B, simulate send failure and refresh, then restore test SMTP and send. | B shows identical subject/body; after failed send one draft remains; after successful send of that version the draft count decreases by one and the mail timeline has one matching successful record. | PENDING | N/A | N/A | N/A |
| A-2 | Yes | Record mailbox count and follow/suspension state under the same filter; prepare normal, meeting, and attachment messages. Save only a draft, compare original list/state, then run the plan's 02 A-5 and 03 A-8 send/layout regressions. | Saving only a draft leaves counts and business state unchanged; old send validation, attachment, and thread behavior follow existing rules; the entry remains in the existing mailbox. | PENDING | N/A | N/A | N/A |

## Human Sign-off

- Decision: PENDING
- Boundary: `65eb16774c64e6c741a0339a481dda35739d9c8c`
- Governing master identity: SHA-256 `fca914b11731d582381dde9254b687b86fe37c1198ef09317d2353e6d48ac676`, recorded commit `02af6d42cdf3617c48335a9ad3aeddb025d1302f`
- Reporter: N/A
- Timestamp: N/A
- Note: Awaiting human A-1/A-2 results and explicit acceptance of this boundary and governing identity.
