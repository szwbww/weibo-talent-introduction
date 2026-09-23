## Light Verification: LIGHT_PASS
Epoch: 1 | Attempt: 1 | Timestamp: 2026-09-24 01:58:06 +0800
Child: backend — `docs/plans/2026-09-23/01-reply-snippet-variants-backend.md`
Boundary: `24e8439480581fa6b6e5a81b5579e7b8ce393206..c5cb4cc600d265aae935100aad7ac551ed00eb32`
Verifier: `ReplySnippetBackendVerifier`

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Boundary diff contains exactly the nine product/test paths in the child brief allowlist (brief.md:10-20); no other product/test changes. Current branch is `fast/2026-09-23-reply-snippet-variants-main`; `HEAD` is `c5cb4cc600d265aae935100aad7ac551ed00eb32`, whose parent is the exact base SHA. Untracked fast-p evidence tree is outside the reviewed code diff. |
| Plan and invariants | PASS | Approved plan `01-reply-snippet-variants-backend.md` T-1..T-4/I-1..I-6 and brief.md:26-32: `ContentVariantService.kt:25-49,111-126` exposes ordered candidate listing and shared random/explicit-preview selection; reply-snippet resolve uses it while QA's legacy branch remains separate. `MailComposeTemplateService.kt:445-462,526-588,607-740` maps nullable ID to Detail, treats null as custom, reads current enabled snippet content, validates candidate/final subjects, selects body references via the shared seam and retains per-position raw selections. `MailComposeTemplateServiceTest.kt:243-315,806-856` covers reference snapshot/ID return, latest source rendering, draft sampling, explicit legacy index; focused test summaries also cover selector and gate behavior. `V136__add_compose_subject_snippet_id.sql` adds only nullable BIGINT column; `FlywayMigrationIntegrationTest.xml` reports the V136 old-row assertion and fresh migration through V136; block repository IT test covers JDBC persist/clear. SMTP/retry/write files are absent from the boundary diff. |
| Required commands | PASS | Fresh-after-final commands are recorded in execution.md:28-32. Unit: `JAVA_HOME=/Library/Java/VirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipNodeTests=true -Dtest=ContentVariantServiceTest,MailComposeTemplateServiceTest,TemplateVariantContextTest,ComposeTemplateGateControllerTest test` — reported exit 0; target Surefire summaries total 77 tests (14+59+1+3), 0 failures/errors/skips; Surefire XML records the exact selected classes and target worktree. Docker/MySQL: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/VirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -DskipNodeTests=true -Dapi.version=1.40 -Dtest=MailComposeTemplateBlockRepositoryIT,FlywayMigrationIntegrationTest test` — reported exit 0; target Surefire summaries total 30 tests (2+28), 0 failures/errors/skips; XML records `migrationIt=true`, `api.version=1.40`, exact selected classes, `V136 preserves existing template subject and initializes reference as null`, and `fresh database migrates through V136`. Baseline comparison: unit command was exit 0 / 66 tests; initial Docker/API 1.32 could not start, and corrected API 1.40 baseline exposed one pre-existing V2 placeholder error (the brief also documents the V135 fixture FK cleanup issue). Final required Docker run passes after the authorized IT fixture changes, with real V136 migration evidence; no substitution. No tests were rerun by verifier. |
| Downstream interfaces | PASS | `MailComposeTemplateController.kt:27-36,63-65,74-97` accepts ID on create/update and exposes preview-draft; `MailComposeTemplateService.kt:73,100,457,798-829,873-884` carries nullable `subjectSnippetId` through command/detail and preview-draft request. `MailComposeTemplateService.kt:552-568` preserves custom null semantics and selects referenced content via `ContentVariantService.resolveReplySnippetBody`; absent preview index passes null to public random selector. Tests at `MailComposeTemplateServiceTest.kt:298-315` verify draft selection through that public selector without stale snapshot. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD