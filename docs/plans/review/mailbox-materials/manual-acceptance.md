# Manual Acceptance — docs/plans/2026-09-07/00-mailbox-materials-master.md

## Epoch 3 — 2026-09-08

- Reviewed code boundary: `8a0c5360e25e875e52800d17797a7b1ea4bd452c..9625769f12b48293470ff91f75e6e0bc09f0a162`
- Machine report epoch: 3
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | Real page and complete operations: 1440×900 plus the other three specified viewports; execute S-4 buttons. | Expert-left/conversation-right, no right tab; workbench collapsed/manual reply expanded; settings/tags/workbench/send controls work; unchanged areas do not overflow. | PENDING | Precondition and steps: master A-1. | — | — |
| A-2 | Yes | No-reply and follow: run waiting/follow/recent-date/follow-up flow for A/B/C fixtures. | A is 0 received/2 sent and waiting with persisted follow; B/C are not misclassified; A cannot generate workbench content but can use existing template send. | PENDING | Precondition and steps: master A-2. | — | — |
| A-3 | Yes | 19+20 and 1000-attachment reply check, material views, and IMAP protocol log. | 39/1000 catalog entries are complete; attachment-content FETCH=0; materials page at 10; current account is accurate; opening does not download. | PENDING | Precondition and steps: master A-3. | — | — |
| A-4 | Yes | Explicit fetch and shared state for 1 stored plus 39 metadata-only materials. | Only 2 selected files queue; both hosts show same state; closing does not cancel; selected files complete and unselected remain metadata-only; review status unchanged. | PENDING | Precondition and steps: master A-4. | — | — |
| A-5 | Yes | Slow transfer/network/file/UIDVALIDITY/metadata-DB-failure restart and retry cases. | Timeout truly closes; later accounts continue; invalid source is `SOURCE_UNAVAILABLE`; no repeat auto-reply; failed DB registration does not advance UID; restart does not auto-fetch unselected items. | PENDING | Precondition and steps: master A-5. | — | — |
| A-6 | Yes | AI and manual-processing independence with mixed ready/unready PDF, JPEG, scanned PDF, and historical result. | Opening does not download; only missing selected item fetches; failure does not impersonate success; image unsupported state clear; analysis editing works; marking processed does not download/change review. | PENDING | Precondition and steps: master A-6. | — | — |
| A-7 | Yes | DMARC/DSN/self-check/unmatched/task drill-through/SMTP and trusted-adopt-to-manual-send flows. | DMARC remains separate; DSN/self-check do not alter materials; bound materials keep IDs; task batches accurate; sending safety/QA stay effective and rejected validation retains draft. | PENDING | Precondition and steps: master A-7. | — | — |
| A-8 | Yes | Record staging HEAD/Flyway121/assets; restart browser; verify seven cache keys; execute runbook compatibility rollback and restore. | No mixed-cache/missing functions; no table/file deletion; old attachments remain readable; restore retains tasks/follows; no production claim. | PENDING | Precondition and steps: master A-8. | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: `9625769f12b48293470ff91f75e6e0bc09f0a162`
- Reporter: —
- Timestamp: —
- Note: —
