# Manual Acceptance — docs/plans/2026-09-16/meeting-mail-master.md

## Epoch 2 — 2026-09-17T17:19:38+08:00

- Reviewed code boundary: `24f5c8205a304d3682e09e02458960bc2caa0463..06dfb878f68e909540e9ef7c4ea63e519beded6e`
- Machine report epoch: 2
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | Complete mail/calendar/attachment chain: upload a Chinese zip, send meeting confirmation at Beijing 2026-09-18 10:00–10:30, reschedule, cancel, refresh, and download zip/ICS. | Icon has no visible text; sandbox receives exactly one mail with zip/ICS; calendar/mail state, Beijing display, cancellation history, ZIP SHA, ICS instant, and unchanged material count match the master plan. | PENDING | — | — | — |
| A-2 | Yes | In America/Los_Angeles, create two manual Beijing schedules from mailbox/calendar, reschedule/cancel each, then refresh. | Both are independently manageable in Chinese Beijing time; no mail is sent; cancellation history remains and the original workflow status is unchanged. | PENDING | — | — | — |
| A-3 | Yes | Exercise failed/unknown sends, cross-expert draft switch, valid and forged attachment downloads, then ordinary and ICS-only mails. | Draft isolation, retry/no-redelivery, 200/404 download authorization, and legacy mail paths match the master plan. | PENDING | — | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: `06dfb878f68e909540e9ef7c4ea63e519beded6e`
- Reporter: —
- Timestamp: —
- Note: —
