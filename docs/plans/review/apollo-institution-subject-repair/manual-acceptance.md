# Manual Acceptance — apollo-institution-subject-repair

## Epoch 2 — 2026-10-20T03:34:32Z

- Reviewed code boundary: `fcd6719ecd03645284c3df0f8a6b95bf4686ba95..8e4e7d7e6cdd4670ccc372cd9ac6e67c2ba72078`
- Machine report epoch: 2
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| S-A-1 | Yes | Institution: real long-institution extraction | Gachon exact; UC campuses distinct; Renji/Leidos ambiguous; joined MIT unresolved | PENDING | — | — | — |
| S-A-2 | Yes | Institution: subject query and unclear admission | NARROW is only Gachon University; raw preserved; APOLLO_UNCLEAR/UNKNOWN admission contract holds | PENDING | — | — | — |
| S-A-3 | Yes | Institution: non-merge and identity negatives | UC/hospital/ambiguous/RAW-only/historical/conflict/incomplete cases add no admission or arbitrary NARROW | PENDING | — | — | — |
| S-A-4 | Yes | Institution: legacy/restart/damaged context | Legacy result and old search cursor stay stable; damaged context denies Gate but list returns item with missing `institutionSubject` | PENDING | — | — | — |
| S-A-5 | Yes | Institution: protection, retention, page regression | Protected samples do not admit; projection retry adds no Apollo request; samples/GET/page structure remain unchanged | PENDING | — | — | — |
| S-A-6 | Yes | Institution: same-input effect report | Same input/SHA/clock; 41,844/26,134/15,710 format counts; no unsupported accuracy claim; confirmed wrong merge blocks release | PENDING | — | — | — |
| C-A-1 | Yes | Country: OpenAlex zero-request fill | US/North America, valid OPENALEX receipt, zero extra HTTP; institution/nationality unchanged | PENDING | — | — | — |
| C-A-2 | Yes | Country: ORCID current employment and negatives | Match fills GB with one request; six negatives remain empty; no qualification facts added | PENDING | — | — | — |
| C-A-3 | Yes | Country: existing/legacy/protected records | Existing country and old snapshots unchanged; protected/old have no supplement HTTP/receipt; disabled flag leaves new case empty | PENDING | — | — | — |
| C-A-4 | Yes | Country: failure/concurrency/restart | Restart reuses saved profile; SQL winner remains consistent; failures retain original collection with precise error/skip receipts | PENDING | — | — | — |
| C-A-5 | Yes | Country: retention/statistics/effect | Every added country has evidence; incomplete real evidence is NOT_EVALUATED; no Apollo/sample/UI regression; known wrong fills are zero before enablement | PENDING | — | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: `8e4e7d7e6cdd4670ccc372cd9ac6e67c2ba72078`
- Reporter: user
- Timestamp: —
- Note: —
