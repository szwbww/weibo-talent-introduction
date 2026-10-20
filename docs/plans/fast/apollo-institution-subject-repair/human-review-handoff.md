# Fast-P Human Review Handoff

- Outcome: READY_FOR_HUMAN_REVIEW
- Master base: fcd6719ecd03645284c3df0f8a6b95bf4686ba95
- Current/final code head: 0953b2f3570725bf1de5cc138422220c1731a48d
- Branch/worktree: fast/apollo-institution-subject-repair / /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair
- Master plan: docs/plans/2026-10-19/apollo-institution-subject-repair.md
- Amendments: N/A

## Child Status
| Child | Status | Code boundary | Fix rounds | Evidence commit |
|---|---|---|---:|---|
| 01-institution-subject | LIGHT_PASS_WITH_NOTES | b3b3a71a865337c15c556c21225c0eb9a2300b66..1e2ae74981ccb8b6866b135222b2a24540ea09b8 | 0 | 274ab5cac6b9588f18483e3a0916e137adf1cd70 |
| 02-country-supplement | LIGHT_PASS | 1e2ae74981ccb8b6866b135222b2a24540ea09b8..0953b2f3570725bf1de5cc138422220c1731a48d | 1 | 36f48baa62ed27b61a9d531de91ac22275f5d592 |

## Recorded Command Evidence

| Evidence | Recorded result | Source |
|---|---|---|
| 01 focused suite | 69 JVM tests, 0 failures/errors/skips; 1,724 JS tests passed | children/01-institution-subject/execution.md, verify-log.md |
| 01 Docker/MySQL suite | 49 tests (FlowIT 27 + RepositoryIT 12 + ReportingIT 10), 0 skipped | children/01-institution-subject/execution.md, verify-log.md |
| 01 offline inventory audit | 41,844 records; 26,134 with value; 15,710 missing; input SHA d2019b7f…1150; identityImpact=NOT_EVALUATED | children/01-institution-subject/execution.md |
| 02 focused suite | 83 JVM tests, 0 failures/errors/skips; 1,724 JS tests passed | children/02-country-supplement/fix-log.md, verify-log.md |
| 02 Docker/MySQL suite | 47 tests (CountrySupplement 8 + VerificationFlow 27 + RepositoryIT 12), 0 skipped | children/02-country-supplement/fix-log.md, verify-log.md |
| 02 runtime smoke | snapshot==RAW country/token for OPENALEX/US (0 supplement HTTP) and ORCID/GB (1 supplement HTTP); validInstitutionEvidence=true | children/02-country-supplement/execution.md, fix-log.md |
| Child checkpoints | Full two-child manifest validated before/after each checkpoint; result VALID / CHILD_CHECKPOINT | Checkpoint commits afbc8064139db25d58d00b493a3ff08386b8f7b4 and 6feeceaf2539b5599457d53cf1686742f987a965 |

## RECORD_ONLY Index
| Observation | Child | Evidence | Source report |
|---|---|---|---|
| O-1: Worker funnels persisted-context rebuild `IllegalArgumentException` into PENDING/EVIDENCE_CONFLICT instead of VERIFICATION_FAILED; still fail-closed through Gate, and T-3 mandates the same catch for parser drift. | 01-institution-subject | `DiscoveryVerificationWorker.kt:86-94`; Gate returns VERIFICATION_EVIDENCE_INVALID; no admission | children/01-institution-subject/verify-log.md |
| O-1 (carried): ORCID selection rejects two current employments at the same institution with an identical country as INVALID_COUNTRY, while T-1 allows identical duplicates to dedupe; conservative, never mis-fills, no fixture covers it. | 02-country-supplement | `DiscoveryCountrySupplementService.kt:348`; skips a fill only | children/02-country-supplement/verify-log.md |

## Pause/Resume
- Reason: N/A
- Resume from: N/A

No whole-system verification was performed.
