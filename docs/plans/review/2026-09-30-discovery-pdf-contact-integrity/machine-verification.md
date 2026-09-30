# Aggregate Machine Verification — discovery-pdf-contact-integrity

## Epoch 1 — 2026-09-30T04:23:57Z

- Master plan: `docs/plans/2026-09-30/discovery-pdf-contact-integrity.md` (sha256 `3502bf077f26cce7b2c368abe05c845b46b3caf43e00dc7945ef82be83792ea0`)
- Governing master identity: sha256 `3502bf077f26cce7b2c368abe05c845b46b3caf43e00dc7945ef82be83792ea0`; recorded commit `5c6e5da2c86fa392446b76bdd6b164eedd14c45e`
- Master identity state: CONSISTENT; amendments N/A
- Boundary: `a37efe970e4446242b121c5628db02daa631fc92..827b8b0f7df5c51e06db6d06be528d06e6ee2620`
- Code boundary: `8aa82c87848273313bd9239249eb77cff6c05c14..827b8b0f7df5c51e06db6d06be528d06e6ee2620`
- Evidence HEAD excluded: `5ca8492f68bd5bddb6b01da35c63d7c429d4eda9`
- Reviewer: `/root/aggregate_reviewer` (fresh after code commit; distinct from `ImplPdfContact01` and `VerifyPdfContact01`)
- Result: PASS
- Convergence: INITIAL
- Repair artifact/result: N/A

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=SourceAuthorEmailResolverTest,PlainTextEmailExtractorTest,PdfEmailExtractorTest,DiscoveryIdentityTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,CoreDataSourceTest,JatsXmlEmailParserTest test` | PASS | exit 0; BUILD SUCCESS; 03:34; 362/0/0/0. Per class: Resolver 25, PlainText 7, Pdf 40, Identity 7, Expert 177, Pipeline 47, Core 19, JATS 40; lifecycle Node checks 1233 pass, 0 fail. |
| `git diff --check a37efe970e4446242b121c5628db02daa631fc92..827b8b0f7df5c51e06db6d06be528d06e6ee2620` | PASS | exit 0 |
| U+FFFD direct runtime probe | PASS | `Jane Doe*` → `{*=[0]}`; `Jane Doe*\\uFFFD` → `{}` |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| O-1 / I-1 | PASS | `PdfAuthorContactLayout.kt:170-188`; real Chee contacts 0 and unowned retained emails. |
| I-2 | PASS | `PdfAuthorContactLayout.kt:170-172,192,221-223`; Lydéric contacts 0; direct U+FFFD probe. |
| O-2 / I-3 | PASS | `PlainTextEmailExtractor.kt:27,42-47`; marker context in `PdfAuthorContactLayout.kt:78-84`; `yue@msn.com` absent. |
| I-4 / N-1 / N-2 | PASS | Resolver `:35-45,158-170`; real negative cases unowned; positive one/two-mailbox controls retain identity. |
| O-3 / I-5 / N-5 | PASS | `DiscoveryIdentity.kt:22-24`; producer `ExpertDiscoveryService.kt:2005`; consumer reject `:2041-2048`; pipeline FAILED `DiscoveryPipelineService.kt:1074-1082`. |
| N-3 / N-4 / X-3 | PASS | 11-case text table; Core/JATS/HTML/mailto regression classes green. |
| I-6 | PASS | ZIP SHA256 `9f5802b0d0862126c570c81f15a0e6053d26fa3abc99ab25ce69aaa81fec6a27`; seven archive members; fresh compile and reports. |
| X-1 / X-2 | PASS | Fresh `pdf-contact-integrity.json`: contacts and final resolution asserted. |
| X-4 / X-5 | PASS | Fresh consumer/cache reports: old/null versions fail with zero download/write; valid controls write expected documents. |
| Scope | PASS | Exactly 3 production files, 6 tests, and ZIP changed; no unauthorized product files; diff check clean. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| O-1 RECORD_ONLY | RESOLVED | Independent runtime branch proof. |

### Findings

#### P1

N/A

#### P2

N/A

#### Observations

N/A

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| O-1 | I-2 | PASS | The U+FFFD PDF fixture remains unavailable, but the compiled branch was independently executed with a uniquely matchable author: normal marker returned `{*=[0]}` and added U+FFFD returned `{}`. `uniqueOwner` cannot create a contact. |

### Evidence Boundaries

- Manual A-1 through A-6 remain PENDING.
- No machine-verification blocker.

### Next Action

- Perform the master plan's pending human acceptance.

No product code was modified.
