# Manual Acceptance — mail-open-tracking-120-second-filter

## Epoch 1 — 2026-09-28T11:03:00+08:00

- Reviewed code boundary: `f98e27c7538d091bfcdecfcb6ffc10360a35ba04..e0b002076b92d3e1e543040fb5640585fc2869aa`
- Machine report epoch: 1
- Status: PENDING
- Environment: independent clean acceptance database only; do not insert fixtures into production.

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | Early/exact 120-second fixtures; ALL, OPENED and NO_SIGNAL filters; wait 120 seconds and refresh. | `TRACK120-EARLY` and `TRACK120-EXACT` stay “无120秒后请求”; raw request times remain visible; only NO_SIGNAL includes them. | PENDING | Run master-plan A-1 fixture and steps. | — | — |
| A-2 | Yes | Boundary and late-repeat request fixtures; update late row to 121 seconds and inspect detail. | Boundary and updated late rows show “疑似打开（120秒后请求）”; first=10s and latest=121s for late row. | PENDING | Run master-plan A-2 steps. | — | — |
| A-3 | Yes | Summary, keyword/status switches and untracked filter after A-2. | Tracked sent=4, late requests=2, rate=50.0%; switches do not change metrics; untracked row is visible. | PENDING | Run master-plan A-3 steps before A-4. | — | — |
| A-4 | Yes | Real pixel HEAD, GET and tracking-toggle regression. | HEAD does not write; GET creates late signal/times; disabled tracking prevents later update; both return GIF. | PENDING | Run master-plan A-4 steps. | — | — |
| A-5 | Yes | Failed/inbound exclusion and page-style regression. | Failed/inbound excluded; 3 cards/9 columns and existing badge/layout appearance remain; full required explanation appears. | PENDING | Run master-plan A-5 steps. | — | — |
| A-6 | Yes | Keyword, pagination and date regression. | 21 page fixtures yield 20+1 paging; query state persists; other-day row only appears on other date; untracked rows do not affect tracking metrics. | PENDING | Run master-plan A-6 steps. | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: `e0b002076b92d3e1e543040fb5640585fc2869aa`
- Reporter: —
- Timestamp: —
- Note: Report A-1..A-6 verdicts with evidence, then explicitly accept this boundary.
