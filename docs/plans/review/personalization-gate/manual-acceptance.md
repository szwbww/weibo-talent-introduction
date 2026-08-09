# Manual Acceptance — personalization-gate

## Epoch 2 — 2026-08-09T18:30:00+08:00

- Reviewed code boundary: `ab5dcbb7fbb58f5e8a9b13b7e54022effd270b77..d7fbe460ee8b169e687c41d433f631e34b13c025`
- Machine report epoch: 2
- Status: PENDING
- Repair approval provenance: `APPROVAL_NOT_RECORDED`; final acceptance must explicitly accept candidate SHA `d7fbe460ee8b169e687c41d433f631e34b13c025`.

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| AM-1 | Yes | Manual mail placeholder replacement | text/plain and text/html contain no `${unsubscribeUrl}`; signed unsubscribe URL present | PENDING |  |  |  |
| AM-2 | Yes | Template gate bulk skip | 1 success; one “个性化字段缺失” skip; missing recipient receives none | PENDING |  |  |  |
| AM-3 | Yes | NULL `required_keys` regression | manual mail sends with template default and no skip | PENDING |  |  |  |
| AM-4 | Yes | Existing research-field chip regression | baseline count unchanged except explainable enrichment changes | PENDING |  |  |  |
| AM-5 | Yes | List/send gate consistency | matching count ≥ sends; list-excluded expert never sends | PENDING |  |  |  |
| AM-6 | Yes | No frontend fallback field set | intercept empty gate response; client applies no filter/default fields | PENDING |  |  |  |
| AM-7 | Yes | Human sign-off | explicit acceptance of master identity and final code SHA | PENDING |  |  |  |
| A1-1 | Yes | Manual full-variable mail | both MIME parts lack `${`; unsubscribe URL and single research phrase present | PENDING |  |  |  |
| A1-2 | Yes | Gate skip statistics | one success, one personalization skip, no failure, missing expert gets no mail | PENDING |  |  |  |
| A1-3 | Yes | Unconfigured template regression | manual send succeeds with default copy and no skip | PENDING |  |  |  |
| A1-4 | Yes | INTRODUCTION path gate | missing expert is skipped and receives no mail | PENDING |  |  |  |
| A1-5 | Yes | Initial outreach regression | unconfigured INTRODUCTION sends and contact reaches `INTRO_SENT` | PENDING |  |  |  |
| A1-6 | Yes | Rewritten-copy inspection | required salutation, quoted title, single research phrase, one external link | PENDING |  |  |  |
| A2-1 | Yes | Recent-paper chip | matches adjacent chip style; selects and narrows results | PENDING |  |  |  |
| A2-2 | Yes | Template auto-filter | two chips selected; “符合 N / M” visible with N ≤ M; list refreshes | PENDING |  |  |  |
| A2-3 | Yes | Unlimited restores manual fields | gate chips clear, manual institution chip persists, summary hides | PENDING |  |  |  |
| A2-4 | Yes | Failed gate request | one error, no automatic chip, hidden summary, unchanged list | PENDING |  |  |  |
| A2-5 | Yes | Existing chip regression | research-field count baseline or slightly lower only due blank exclusion | PENDING |  |  |  |
| A2-6 | Yes | Preview lower bound | template match count N ≥ actual sent S | PENDING |  |  |  |

## Human Sign-off

- Decision: PENDING
- Boundary: `d7fbe460ee8b169e687c41d433f631e34b13c025`
- Required statement: explicitly accept this candidate code SHA and master identity `cbae234bc59e9ae9fe67315bd86e4a86ee1d4ddd4ef54b94dbd14ebde13b8324`.
- Reporter: PENDING
- Timestamp: PENDING
- Note: PENDING
