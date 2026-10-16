# Child 07-ui — Verify Log

## Light Verification: LIGHT_PASS
Child: 07-ui (docs/plans/2026-10-13/discovery-person-07-ui.md)
Boundary: 0755833e3e5e0480d9ad40443ae5ebba0e91ac6a..2fae99a551ea656604c46a19f1f046eb3ad6593e (+ epoch-1 product 4fbc76109d8948f410e20234f2225323bc025bdd)
Verifier: Verify07

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git show --stat 4fbc761` = exactly app.js + discoveryPersonEmails.test.js + index.html (3 files). `git show --stat 2fae99a` = exactly app.js + discoveryPersonEmails.test.js (2 files). Delta `0755833..2fae99a` non-product files are docs only (06-query execution/fix-log/verify-log, 07-ui brief, ledger) = controller bookkeeping. No CSS/other source touched (`git diff --stat 0755833..2fae99a -- '*.css'` empty; `git show --stat 4fbc761 -- '*.css'` empty). |
| Plan and invariants | PASS | S-1: list both branches feed `discoveryEmailCount` (DB grouped `app.js:9081`, ES `:9139`); render `:9241` shows `${email} · 共 N 个邮箱` only when `>1`, else original text; escaped, reuses `.list-item-meta`/`.expert-meta span`, no inline style. S-2: shared `renderDiscoveryPersonEmailsCard`/`loadDiscoveryPersonEmails` (`:13507-13592`) consumed by both hosts (`showExpertDetail` host `:13701`, call `:13725`; `loadContactDetail` host `:14347`, call `:14362`); member rows carry `data-member-doc-id` + `open-person-member-history` with original `data-contact-id`; `状态未取得` fallback `:13540`; conflicts row `:13533`; classes `.metadata-card/.metadata-card-header/.metadata-card-value/span-all/.link-btn` all existing, no inline style, no `id=`. I-1: DB branch sets `groupDiscoveredPersons=true` + server-side `emailDomain` (`:9024-9025`), person/contacts preserved, actions use original contactId/docId, `orcidId` never sourced from `personId`. I-2: generation guard `isCurrentContactDetailPresentation(generation)` (`:13560`) + `typeof`/`docId` early return; all fields via `escapeHtml`/`textContent` (`:13572`); non-discovered (no `personId`) clears host → original UI. index.html: exactly 16 `?v=` all `20261014-person-emails` (`grep -oE '\?v=[^"&]+' | sort | uniq -c` → `16 ?v=20261014-person-emails`). |
| Required commands | PASS | C1 `node --check src/main/resources/static/app.js` exit 0. C2 `node --test src/test/js/discoveryPersonEmails.test.js` exit 0, tests 20 / suites 5 / pass 20 / fail 0. C3 `node --test src/test/js/*.test.js` exit 0, tests 1677 / suites 321 / pass 1677 / fail 0 (≥1677, 0 fail). |
| Downstream interfaces | PASS | `loadDiscoveryPersonEmails` consumes 06 `GET /api/experts/person?docId=&level=` and reads `personId/revision/members[]/conflicts[]`; ES list branch consumes 06 `memberCount` — completes 06↔07 API/DOM joint contract. Later child 10-history is a backend-only maintenance service/CLI (readonly preview, 06/08/09 readers) and does not consume this UI's DOM/JS. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD
