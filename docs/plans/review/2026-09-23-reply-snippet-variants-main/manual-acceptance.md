# Manual Acceptance — reply-snippet-variants-main

## Epoch 1 — 2026-09-24T09:34:42+08:00

- Reviewed code boundary: `24e8439480581fa6b6e5a81b5579e7b8ce393206..b62f4bb63005e268ae789966257cfa400a3bb353`
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| Backend A-1 | Yes | Create a subject-snippet template, read it, switch to custom subject, read again; also issue old request without ID. | ID/subject round-trip; custom clears ID; old request stores null. | PENDING | Source: backend plan A-1. | — | — |
| Backend A-2 | Yes | Preview subject snippet and two body references repeatedly, then update the subject snippet and preview. | Subject/body stay in their own candidate sets; both body references may repeat; updated subject is read live. | PENDING | Source: backend plan A-2. | — | — |
| Backend A-3 | Yes | Disable, delete, and make referenced subject invalid; preview each; try 256-character subject. | Explicit invalid-reference/constraint errors; no stale snapshot used; body missing behavior unchanged. | PENDING | Source: backend plan A-3. | — | — |
| Backend A-4 | Yes | Exercise gate fields and strict previews with required/defaulted institution variables. | Required-key and default behavior correct; preview creates neither send record nor email. | PENDING | Source: backend plan A-4. | — | — |
| Backend A-5 | Yes | Test old no-variant template, QA/AI-RAG, captured SMTP send, old reply flow, and retried batch task. | Existing behavior retained; captured content/record agree; retry is a new sample. | PENDING | Source: backend plan A-5. | — | — |
| Backend A-6 | Yes | Migrate isolated V135 test DB, set/clear reference ID, toggle template. | Old subject retained; new ID round-trips and clears; enable/disable preserves ID. | PENDING | Source: backend plan A-6. | — | — |
| Frontend A-1 | Yes | Edit MAIN and 10 variants through the snippet editor, save/reopen, add/delete a variant. | Exactly one visible editor; values preserved; original cannot be deleted. | PENDING | Source: frontend plan A-1. | — | — |
| Frontend A-2 | Yes | Trigger hidden validation errors, insert variable at cursor, cancel/reopen, empty original. | Focuses invalid version; insertion only at active cursor; cancel discards changes; no hidden-focus error. | PENDING | Source: frontend plan A-2. | — | — |
| Frontend A-3 | Yes | Choose identically named subject snippets by ID, then use free/custom text and keyboard selection. | ID distinction/reference badge works; free text nulls ID; no accidental submit; one-line subject row. | PENDING | Source: frontend plan A-3. | — | — |
| Frontend A-4 | Yes | Disable or make referenced snippet multiline; try preview/save; return to custom input and variable insertion. | Invalid reference retained and clearly reported; variable disabled while referenced; custom restores it. | PENDING | Source: frontend plan A-4. | — | — |
| Frontend A-5 | Yes | Use both preview entry points, strict toggle, refresh sample, rapid expert/account changes, and snippet variant preview. | Both render subject/body/random-sample hint; no save/send; stale response guarded; current variant shown. | PENDING | Source: frontend plan A-5. | — | — |
| Frontend A-6 | Yes | Inspect UI at 1440/1024/768px; verify menus, QA page, existing metadata/block operations, and refresh. | Specified layout/visual states; unrelated QA/variable flows intact; new assets remain after refresh. | PENDING | Source: frontend plan A-6. | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: `b62f4bb63005e268ae789966257cfa400a3bb353`
- Reporter: —
- Timestamp: —
- Note: —
