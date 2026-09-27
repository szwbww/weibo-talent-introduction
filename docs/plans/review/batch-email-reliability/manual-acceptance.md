# Manual Acceptance — batch-email-reliability

## Epoch 2 — 2026-09-27T10:06:57Z

- Reviewed code boundary: `64c0394a940bd79c2ecc04e5c497650f045faa75..9b381affc1a1654628bae1f71394ea37c1366c18`
- Governing master identity: `d0226d4fd73fd6e542d77a85ceab3d9285e0aacef4047668c0e7983735890a77` (approved A1–A5 target-worktree amendments)
- Machine report: `docs/plans/review/batch-email-reliability/machine-verification.md` Epoch 2; `PASS`
- Status: `PENDING`. These checks require human-originated outcomes; automated tests and Chromium smoke do not fill them.
- Use the isolated database, local fixture/test runner and SMTP sink prescribed by the source plans. No production send or paid verification is part of this checklist.

| ID | Mandatory | Check and source | Expected human observation | Human result | Evidence/note | Reporter | Timestamp |
|---|---|---|---|---|---|---|---|
| 01-A1 | Yes | With the four historical email cases in child 01 §人工验收 A-1, query, append a newer original undeliverable row, query again. | First exclusion set empty; second contains only the updated email; HTTP count 0 and verification rows unchanged. | PENDING | — | — | — |
| 01-A2 | Yes | Prepare 110/100-day executions and an unrelated expired execution; clean with a 90-day cutoff, inspect executions and re-query (child 01 A-2). | Both executions holding effective records survive; unrelated expired one is deleted; no obsolete bad result revives. | PENDING | — | — | — |
| 02-A1 | Yes | V141 old config migrated to V142; GET old, POST omitted field, PUT explicit false, legacy typed update, enable/disable then GET (child 02 A-1). | Old=false, new=true, explicit false persists, legacy update and enable/disable preserve the configured flag. | PENDING | — | — | — |
| 02-A2 | Yes | Five distinct targets with two historic bad emails; preview and execute the same filter-on/live-verification-off snapshot using SMTP sink; preview filter-off (child 02 A-2). | On counts 2/1/3/2, only three deliveries, no verification/binding writes for excluded two; off counts 3/2/5/0. | PENDING | — | — | — |
| 02-A3 | Yes | Material contact A bad/profile B good; introductory first full page bad/next page good; unsubscribed and sent cases; cancel during scan (child 02 A-3). | Material A excluded, later good page delivered, unsubscribed/sent not delivered, no page after cancellation and final CANCELLED; account/quota semantics intact. | PENDING | — | — | — |
| 02-A4 | Yes | Saved scheduled config true with auto send off; run manual false override; inspect historic requestSnapshot and source config (child 02 A-4). | Snapshot=false, source remains true, old absent field reads false, future schedule still derives true from source. | PENDING | — | — | — |
| 03-A1 | Yes | Fake verification client returns 249 twice for first target, deliverable for next two; one round in SMTP sink (child 03 A-1). | First deferred with two requests and no send/bad tag; later two sent; success 2/skip 1/fail 0; retries spaced 500ms. | PENDING | — | — | — |
| 03-A2 | Yes | First-target 402 and pass-then-402 sequences on legacy runtime and modern API (child 03 A-2). | FAILED and PARTIAL_SUCCESS respectively; no later verification/send, remaining preserved; legacy PAUSED, modern execution stops without clearing autoEnabled. | PENDING | — | — | — |
| 03-A3 | Yes | Force deferred recordSend audit failure and inspect historical ERROR+NOT_SENT, undeliverable, risky/unknown logs and tags (child 03 A-3). | Audit failure stops batch; historic ERROR not mislabelled deferred; undeliverable invalid, risky/unknown policy permitted, deferred not marked invalid; existing style only. | PENDING | — | — | — |
| 04-A1 | Yes | Real local UI new, old and independent manual contexts; material reminder, live verification and template gate toggles; keyboard and narrow viewport (child 04 A-1). | Switch sits by template gate in filters, independent and operable; new/manual on, old off; prescribed dimensions/spacing and no visual obstruction. | PENDING | — | — | — |
| 04-A2 | Yes | Saved filter-on source; preview 5 before/3 after with 2 excluded under available/unavailable gate fixtures, toggle quickly and inspect request/response/confirmation (child 04 A-2). | Correct 5 vs 3 and exclusion 2; draft diff shows original on/current off; confirmation current; source on; stale response never overwrites current count. | PENDING | — | — | — |
| 04-A3 | Yes | Submit manual execution from on-source with off-draft, inspect snapshot/config and historic request without field (child 04 A-3). | New execution false, source true, old absent field false, real-time verification independently follows its own switch. | PENDING | — | — | — |

## Human Sign-off

- Decision: `PENDING`
- Boundary: `9b381affc1a1654628bae1f71394ea37c1366c18`
- Accepted governing master identity: `PENDING` (sign-off must name `d0226d4fd73fd6e542d77a85ceab3d9285e0aacef4047668c0e7983735890a77` and reviewed boundary)
- Reporter: —
- Timestamp: —
- Note: Awaiting all 12 item verdicts with evidence or notes and explicit human sign-off; no integration, push or deployment authorized.
