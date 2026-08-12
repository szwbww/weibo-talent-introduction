# Aggregate Machine Verification — unsubscribe-link-and-page-master

## Epoch 1 — 2026-08-12 15:22:47 +0800

- Master plan: docs/plans/2026-08-12/unsubscribe-link-and-page-master.md; sha256 29f401c80efaba9649fb720d8b2856d8dedc1b45956c36d5cd76eb7628108594.
- Governing master identity: worktree and recorded sha256 29f401c80efaba9649fb720d8b2856d8dedc1b45956c36d5cd76eb7628108594; invoked identity SAME; state CONSISTENT.
- Governing amendments: A1, A2, A3 as recorded and human-approved in the fast-p ledger.
- Boundary: 0482bcd497eefba9ce4f44f61a5624ae25d0efe1..44d6aa23ebdb43581af427708f8348423e2c33a7.
- Reviewer: /root/aggregate_reviewer.
- Result: FAIL.
- Convergence: INITIAL.
- Repair artifact/result: docs/plans/fix/unsubscribe-link-and-page-master/repair.md — DRAFT_READY.

## Verification Result: FAIL

Plan: docs/plans/2026-08-12/unsubscribe-link-and-page-master.md

Implementation boundary: 0482bcd497eefba9ce4f44f61a5624ae25d0efe1..44d6aa23ebdb43581af427708f8348423e2c33a7

Convergence: INITIAL

Manual acceptance: PENDING

The governing/invoked master identities are 29f401c80efaba9649fb720d8b2856d8dedc1b45956c36d5cd76eb7628108594 and SAME. A1/A2/A3 identities match the supplied records. Current evidence HEAD is b3c2d3cf36046ec4b825397631a41ee9dd4e125b; docs-only evidence commits were excluded from implementation review.

### Commands

| Command | Result | Evidence |
|---|---|---|
| JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailContentServiceTest,IntroductionMailComposerTest,UnsubscribeWordingMigrationTest,UnsubscribeBodyLinkMigrationTest,SmtpMailDeliveryServiceTest,ManualExpertMailServiceGateTest | PASS | exit 0; 13+12+5+6+24+5 = 65 tests; 0 failures/errors. |
| JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsubscribeTokenServiceTest,UnsubscribeTokenMigrationTest,UnsubscribeControllerTest,UnsubscribeControllerIllegalTokenTest,MailVariableServiceTest,SmtpMailDeliveryServiceTest,ManualExpertMailServiceGateTest | PASS | exit 0; 18+3+5+3+40+24+5 = 98 tests; 0 failures/errors. |
| JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsubscribePageRendererTest,UnsubscribeControllerTest,UnsubscribeControllerIllegalTokenTest | PASS | exit 0; 11+5+3 tests; 0 failures/errors. |
| JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsubscribeControllerTest#GET valid token returns confirm html with context-path-safe action | PASS | exit 0; 1 test; 0 failures/errors. |
| JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test | PASS | exit 0; Surefire XML: 2,333 tests, 0 failures, 0 errors, 4 skipped. |
| JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package | PASS | exit 0; WAR emitted at target/weibo-talent-introduction-1.0.0-SNAPSHOT.war; 2,333 tests, 0 failures/errors, 4 skipped. |
| mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true | N/A | Default-skip rule applies. Fresh docker info exited 1; Docker-required IT unavailable. |
| git diff --check | PASS | exit 0; no output for working tree and implementation boundary. |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 identity, ancestry, scope | PASS | SHA-256 records match; 0482bcd is ancestor of 44d6aa2; cumulative product/test diff has exactly 23 files matching Plan 06 (10), Plan 07 (6), Plan 08 (7). |
| 06-I-1 one plain-text source/multipart | PASS | IntroductionMailComposer.kt:37-46 derives HTML and text from plain; ManualExpertMailService.kt:243-255 does the same; SmtpMailDeliveryService.kt:49-59 emits multipart alternative. |
| 06-I-2 plain-text mail_record.body | PASS | Seven changed sites: InitialOutreachService.kt:95,105; ManualInitialOutreachService.kt:695,709,723,747,763. |
| 06-I-3 exact post-escape anchoring | PASS | MailContentService.kt:7-18 escapes before exact replace(url,...); focused converter tests pass. |
| 06-I-4 blank URL/backcompat | PASS | MailContentService.kt:9,21 filters blanks and delegates the single-argument overload to an empty collection. |
| 06-I-5 gate input remains plain text | PASS | IntroductionMailComposer.kt:29,46 keeps raw texts and plain as gate inputs. |
| 06-I-6 V88 guarded point migration | PASS | V88__rewrite_unsubscribe_line_wording.sql:8-18 uses REPLACE and exact LIKE for two template codes; no mail_template write. |
| 06 must-not-change headers/threading | PASS | SmtpMailDeliveryService.kt:47-69 unchanged; focused SMTP suite 24/0/0 and gate suite 5/0/0 pass. |
| 07-I-1 opaque token | PASS | UnsubscribeTokenService.kt:26-35,64-68 uses a SecureRandom 32-byte base64url token; repo-backed tests 18/0/0. |
| 07-I-2 idempotence/concurrent duplicate | PASS | UnsubscribeTokenService.kt:29-35; V89:13-14 unique keys; tests cover reuse and duplicate-key reread. |
| 07-I-3 table-first plus legacy compatibility | PASS | UnsubscribeTokenService.kt:39-62 does table lookup before constant-time legacy HMAC fallback. |
| 07-I-4 required random source/encoding | PASS | UnsubscribeTokenService.kt:9,20,64-68; no UUID or java.util.Random use. |
| 07-I-5 enabled/blank-secret split | PASS | UnsubscribeTokenService.kt:22-24,50-51; legacy fallback rejects blank secret. |
| 07-I-6 non-null repository coverage | PASS | UnsubscribeTokenServiceTest.kt:103-184 has ten repo-backed cases. |
| 07-I-7 V89 create-only migration | PASS | V89__create_unsubscribe_token.sql:8-15; migration test passes and has no executable INSERT. |
| 07 external compatibility | PASS | Controller, MailVariableService, SMTP contracts retain signatures/behavior; focused dependent suites pass. |
| 08-I-1 GET no suppression/keep link | PASS | UnsubscribeController.kt:31-35 only verifies/renders; renderer has one POST form and separate keep anchor at UnsubscribePageRenderer.kt:18-22. |
| 08-I-2 token attribute escaping | PASS | UnsubscribePageRenderer.kt:19,79-84; injection test passes. |
| 08-I-3 relative action | PASS | UnsubscribePageRenderer.kt:18 exact action equals unsubscribe/confirm; controller method test passes. |
| 08-I-4 self-contained page/logo fallback | PASS | UnsubscribePageRenderer.kt:38-76,106-134; one inline style block, wordmark fallback, optional logo branch; renderer tests pass. |
| 08-I-5 masking behavior | PASS | UnsubscribePageRenderer.kt:86-94 handles no-at, multi-at, empty local, and empty domain. |
| 08-I-5 mandatory boundary-test coverage | FAIL | Plan 08 I-5 requires independent coverage for no-at, multiple-at, empty local, and empty domain. UnsubscribePageRendererTest.kt:87-93 omits @b.com and a@. |
| 08-I-6 shared shell/style contract | PASS | Both pages use renderShell() at UnsubscribePageRenderer.kt:25,35,38; normalized Plan S-1 CSS comparison exited 0; one doctype and one allowed inline style. |
| 08 endpoint/config must-not-change | PASS | UnsubscribeController.kt:23-42 preserves one-click responses, invalid responses, suppress arguments; UnsubscribeProperties.kt:8-15 and application.yml defaults are present. |
| Manual acceptance | PENDING | Gmail/client, deployed context-path, visual, and production migration checks require human execution. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | No prior aggregate report. Plan 08 I-5 requires the two direct boundary tests; the test file omits them. |

### Findings

#### P1

- N/A.

#### P2

- V-1: Plan 08 I-5 explicitly requires independent empty-local and empty-domain masking coverage. UnsubscribePageRenderer.kt:86-94 implements both bounds, but UnsubscribePageRendererTest.kt:87-93 tests neither @b.com nor a@. This is mandatory test-acceptance debt, not a production behavior defect.

#### Observations

- Plan 06 RECORD_ONLY lead: AutoMailReplyService.kt:977 is unchanged from base and belongs to out-of-scope MEETING_INVITATION. No aggregate violation.
- A2 windowed implementation is semantically equivalent to the approved contiguous-byte-subsequence assertion and remains covered by fresh tests.

### Evidence Boundaries

- Flyway migration IT is Docker-required and explicitly default-skipped; fresh Docker availability check failed.
- Manual acceptance remains pending.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Plan 06: AutoMailReplyService.kt:977 body = mail.body | Plan 06 I-2 applies only seven listed INTRODUCTION persistence sites; MEETING_INVITATION is explicitly out of scope. | OBSERVATION — no violation | git diff --quiet 0482bcd..44d6aa2 -- AutoMailReplyService.kt exited 0; AutoMailReplyService.kt:958-963 constructs non-HTML mail; :977 persists its plain-text body. |
| Plan 08: missing empty-local/empty-domain maskEmail assertions | Plan 08 I-5 explicitly requires independent tests for no-at, multiple-at, empty local, and empty domain. | V-1 / FAIL — mandatory P2 test-coverage gap | UnsubscribePageRenderer.kt:86-94 handles both; UnsubscribePageRendererTest.kt:87-93 lacks confirmPage(t, @b.com) and confirmPage(t, a@) assertions. |

### Next Action

- FAIL + INITIAL: repair-p produced the bounded repair artifact named above. Human approval is required before execution.

No product code was modified.

## Epoch 2 — 2026-08-12 16:04:56 +0800

- Master/governing identity: `docs/plans/2026-08-12/unsubscribe-link-and-page-master.md`, sha256 `29f401c80efaba9649fb720d8b2856d8dedc1b45956c36d5cd76eb7628108594`; invoked SAME; `CONSISTENT`; A1/A2/A3 unchanged.
- Boundary: `0482bcd497eefba9ce4f44f61a5624ae25d0efe1..0a8723a14f6e1035f9e56e9cfb75427b4c0774b8`; repair delta `44d6aa23ebdb43581af427708f8348423e2c33a7..0a8723a14f6e1035f9e56e9cfb75427b4c0774b8`.
- Reviewer: `/root/post_repair_reviewer`; result: PASS; convergence: PROGRESSING; repair planning: N/A.

### Commands

| Command | Result | Evidence |
|---|---|---|
| Plan 06 focused suite | PASS | 65 tests, 0 failures/errors |
| Plan 07 focused suite | PASS | 98 tests, 0 failures/errors |
| Plan 08 focused suite + exact controller method | PASS | renderer 11; controller 5; illegal-token 3; method pass |
| `mvn test` with JDK 11 | PASS | 2,333 tests, 0 failures, 0 errors, 4 skipped |
| `mvn clean package` with JDK 11 | PASS | BUILD SUCCESS; WAR produced; 03:17 |
| `git diff --check` | PASS | exit 0 |
| Docker/Flyway IT | N/A | Docker unavailable; plan-default skipped |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| 06-I-1..I-6 and headers/threading | PASS | composer/content/migration paths inspected; focused tests pass |
| 07-I-1..I-7 and compatibility | PASS | token/storage paths inspected; focused/dependent tests pass |
| 08-I-1..I-4 and controller/config contract | PASS | controller/renderer/config paths inspected; focused tests pass |
| 08-I-5 masking and required bounds | PASS | renderer `:86-94`; test `:90-95` directly covers `@b.com` and `a@` |
| 08-I-6 shared shell/style | PASS | `renderShell()` at renderer `:25,35,38`; style equality test pass |
| Manual acceptance | PENDING | human-only deployment/client/visual/context-path/migration checks |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | authorized repair changed only `UnsubscribePageRendererTest.kt`; direct empty-local/empty-domain assertions pass |

### Findings

- P1: N/A.
- P2: N/A.
- Observations: unchanged `AutoMailReplyService.kt:977` remains out-of-scope plain-text MEETING_INVITATION; unrelated compiler warnings non-blocking.

### Evidence Boundaries

- Docker-gated Flyway execution unavailable under plan-default skip.
- Gmail/client rendering, deployed context-path, visual parity, and production migration remain manual acceptance.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Plan 06 AutoMailReply record | I-2 covers seven INTRODUCTION writes only. | OBSERVATION — no violation | unchanged from base; non-HTML MEETING_INVITATION path |
| Plan 08 test-coverage lead | I-5 requires four masking bounds. | RESOLVED | authorized repair added `@b.com`/`a@` assertions; fresh suites pass |

### Next Action

- Obtain all manual acceptance results and explicit sign-off for `0a8723a14f6e1035f9e56e9cfb75427b4c0774b8`.

No product code was modified by aggregate re-review.
