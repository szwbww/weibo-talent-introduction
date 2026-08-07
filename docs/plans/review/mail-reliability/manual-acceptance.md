# Manual Acceptance — mail-reliability

## Epoch 2 — 2026-08-07 CST

- Reviewed code boundary: `d911bd6..c3e6d00`
- Governing master identity: sha256 `5b8ca123301a2b9819470392bef3044cd33fbe1dcebe2ebb002dcbd628344e7d` (recorded `commit 9bbb046`; A1)
- Machine report epoch: 2
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| J-1 | yes | Send a meeting invitation; reply from Gmail after vendor prefixing; open the inbound item. | Stored Message-ID has the P3 meeting-invitation form; candidate first is `IN_REPLY_TO`, confidence 90, despite vendor prefix. | PENDING | — | — | — |
| J-2 | yes | Process a reply for `TEST-LUKAI-18014905480` (MySQL contact, no ES profile), including bind or manual reply. | Panel renders with profile-unavailable notice; `IN_REPLY_TO` candidate present; workflow reaches completion. | PENDING | — | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: `c3e6d00`
- Reporter: user
- Timestamp: —
- Note: —
