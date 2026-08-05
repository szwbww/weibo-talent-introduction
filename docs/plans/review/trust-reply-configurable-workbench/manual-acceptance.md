# Manual Acceptance — Trust Reply Configurable Workbench

## Epoch 1 — 2026-08-06 00:22:33 +0800

- Reviewed code boundary: `931e724042d9ceee9f75d4cacb45fd3ba29462a5..82a23b4b08bcc6469fb3bf0402ebeb69c4093db4`
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-M1 | Yes | Three requests, four facts: assign IP+confidentiality, contract, and fee; inspect other pickers. | Every ID appears once globally; occupied facts show owner and are disabled elsewhere. | PENDING | — | — | — |
| A-M2 | Yes | Remove a fact after completed locks/assembly; cancel once, then confirm and reassign. | Cancel changes nothing; confirm clears all old versions/locks/assembly, changes evidence version, and rejects old snapshot as `TRUST_REPLY_EVIDENCE_STALE`. | PENDING | — | — | — |
| A-M3 | Yes | With locked items, choose non-default greeting and ACK and disable salutation; reassemble. | Locks/answers/claims/evidence identity unchanged; raw order is greeting → ACK → answers → closing; frame version and draft hash change. | PENDING | — | — | — |
| A-M4 | Yes | Change a frame without server assembly. | Configuration-changed state; adopt/complete disabled; editor/evaluation receive no local preview. | PENDING | — | — | — |
| A-M5 | Yes | In SIMULATION and LIVE use the same non-default frame/matrix; save evaluation, adopt, and send unedited. | Both requests carry same canonical matrix/frame/locks; server raw matches CURRENT assembly; no flat/default fallback. | PENDING | — | — | — |
| A-M6 | Yes | Disable selected fact before evaluation, then selected frame before send. | Evidence/frame stale blocks operation; evaluation and outbound-mail record counts do not increase. | PENDING | — | — | — |
| A-M7 | Yes | Restore v1/v2/v3 states; keyboard tab switch and Chinese IME; inspect at 390px; exercise FREE_FORM/matched/auto-reply/template paths. | v1/v2 normalize and v3 restores; tabs/IME/state stable, no horizontal scroll; legacy paths retain default frames and baseline body. | PENDING | — | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: `82a23b4b08bcc6469fb3bf0402ebeb69c4093db4`
- Reporter: —
- Timestamp: —
- Note: —
