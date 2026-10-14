# Manual Acceptance — batch-filter-main

## Epoch 3 — 2026-10-14T14:10:54+0800

- Reviewed code boundary: 9d353934b82ed3e36a193c70748e5efccb3d9e82..57727abf8aac2cd01f687cdb4e622abe99cbba9a
- Machine report epoch: 3
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | In the isolated G-2 D1–D10 fixture, open task-editor and manual-execution previews from one snapshot, expand reasons, request D1 diagnostics, and inspect business rows/call logs. | Both views show 10/4/6/3; region=2, status=2, template=1, email=1, excluded=4; D1 remains MANUAL_APPROVED; 0 new business records, provider calls, and SMTP calls. | PENDING | — | — | — |
| A-2 | Yes | Prepare 01 A-1/A-2 unknown-country, Japan/Morocco, and non-discovered cases with both verification switches off. Select Other, then Japan/Korea; compare API and both pages. | Other admits four unknown-country cases and rejects Japan/Morocco; Japan/Korea admits explicit Japan and rejects unknowns; no profile/admission change; non-discovered baseline unchanged. | PENDING | — | — | — |
| A-3 | Yes | Prepare sent, permanent-failure, binding, suppression, manual-rejection, and sendable cases; preview/diagnose one snapshot, add suppression, alter current region config, run the prior snapshot, and compare old initial/material flows. | Five protections each have a reason; sendable target counts once; original snapshot applies and new suppression takes effect; local capture sends 0; binding/admission/history unchanged; old flows retain baseline results. | PENDING | — | — | — |
| A-4 | Yes | Use risky/no-cache/undeliverable and template-missing cases with provider-rejecting stub; toggle live/history verification, allowed states, and template gate; inspect both UI hints and verification rows. | Disallowed risky excluded with live on; no-cache awaits verification; history-only excludes only undeliverable; both switches off means no email-history exclusion; gate-on excludes exactly one template-missing target; gate-off shows `不因个性化缺项排除`; no `计入失败`; no new verification rows. | PENDING | — | — | — |
| A-5 | Yes | Prepare 1,201 targets/30 template-missing records; expand samples, request 201 docIds, induce stale response, simulate 500/partial/legacy response, and inspect light/dark page style/resource keys. | Samples cap at 20 with truncation; 201 IDs report parameter error; only newest selection renders; messages are `预估不可用`, `统计未完成`, `详细解释不可用` without fabricated zeros; binding hint/selection retain; S-M1 styling/resource key holds; no new send/verification request. | PENDING | — | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: 57727abf8aac2cd01f687cdb4e622abe99cbba9a
- Reporter: —
- Timestamp: —
- Note: —
