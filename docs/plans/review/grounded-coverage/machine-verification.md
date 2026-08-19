# Aggregate Machine Verification — master: docs/plans/2026-08-19/00-grounded-coverage-master.md

## Epoch 1 — 2026-08-19T09:39:49Z

- Master plan: docs/plans/2026-08-19/00-grounded-coverage-master.md (sha256: 3112ffe1c665ebda5295e36a315a5bf65f3a09082febf11ce89bec1ba854d4e7)
- Governing master identity: worktree sha256 3112ffe1c665ebda5295e36a315a5bf65f3a09082febf11ce89bec1ba854d4e7; recorded commit af1723f37021328f8ffa61261504727e514fbb4b
- Master identity state: CONSISTENT; governing amendment A1 is recorded in docs/plans/fast/grounded-coverage/ledger.md and applies only to child 02 scope.
- Boundary: af1723f37021328f8ffa61261504727e514fbb4b..8c2ec53f4e97d06acb89b81bfb5a388a9d49a566
- Reviewer: /root/aggregate_reviewer
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: N/A — repair planning BLOCKED pending human plan-amendment/scope decision.

### Fresh Command Evidence

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0; Surefire 2589 tests, 0 failures, 0 errors, 4 skipped; Node 658 pass, 0 fail |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | FAIL | exit 1: Maven clean cannot delete `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war.original` |
| `git diff --check af1723f37021328f8ffa61261504727e514fbb4b 8c2ec53f4e97d06acb89b81bfb5a388a9d49a566` | PASS | exit 0 |

### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 P1 trigger letter reaches 5 recognised intents | PASS | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalogTest.kt:247-260` |
| M-2 P1 trigger letter has 5 supported intents / 5 bound facts | FAIL | V-1; production Funding support migration lacks `remuneration` |
| M-3 P1 catalog/coverage/migration invariants | PASS | `src/main/resources/db/migration/V105__add_programme_identity_facts.sql:12-75`; catalog tests pass |
| M-4 P2 shadow-only ask enumeration, no gate behavior change | PASS | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt:39-46,131-156,410-451`; A1 scope ratified |
| M-5 P3 fact ordering through canonical change path | PASS | child 03 evidence; fresh JS suite included in `mvn test` |
| M-6 ordered child execution and authorized scope | PASS | P1 `f5c09382744c0da8a537610af6145974ee1fcaf4`; P2 `533a02fce781ff09693d630c0d029f0d93c7d58a`; P3 `8c2ec53f4e97d06acb89b81bfb5a388a9d49a566`; A1 identity/approval consistent |
| M-7 full build gate | BLOCKED | `mvn clean package` cannot clean locked/unremovable transient WAR |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | Child-01 RECORD_ONLY O-2 confirmed against production migration |
| O-1/O-3/O-4/O-5 child-01 | RECORD_ONLY | plan inconsistency/guard detail; no new product defect |
| child-02 O-1/O-3 | RECORD_ONLY | speculative Unicode/range-attribution observations |
| child-02 O-2 | RESOLVED/RATIFIED | A1 explicitly ratifies select-path log breadth |
| child-03 | N/A | no record-only finding |

### Findings

#### V-1 — production Funding support does not support the mandatory trigger-letter outcome

P1’s mandatory observable outcome requires the verbatim orthopaedic letter’s `finance.arrangements` to bind Funding support and yield 5/5 grounded coverage. Production Funding support keywords are `salary,subsidy,funding,compensation` plus V81’s two compensated phrases (`src/main/resources/db/migration/V3__seed_qa_rules.sql:32`; `src/main/resources/db/migration/V81__ai_reply_due_diligence_keyword_parity.sql:5-17`). None is a substring of the letter’s `remuneration`.

`src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt:829-836` inserts an un-migrated `remuneration` keyword into an in-memory Funding support rule, masking production behavior. `V105__add_programme_identity_facts.sql:12-75` changes only new facts plus id=6/id=18.

#### Build observation

The `mvn clean package` failure is transient-environment evidence, not attributed to product code. A successful fresh build remains unverified because Maven clean cannot delete `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war.original`.

### Repair Planning Result

- Baseline plan: docs/plans/2026-08-19/00-grounded-coverage-master.md
- Verification result: FAIL / INITIAL
- Repair artifact: N/A
- Result: BLOCKED

V-1 is behaviorally repairable, but approved P1 fixes V105 at four statements and authorizes only V105, not a post-V105 migration. A safe repair needs a new migration (normally V106) plus a production-faithful regression test. This needs a human-approved plan amendment / scope decision. The alternative of changing V105 requires an explicit guarantee that it has not been applied.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Child 01 O-1 | P1 catalog/coverage validity | RECORD_ONLY | extra `application.timeline` exemption is a guard-detail issue; no mandatory violation shown |
| Child 01 O-2 | P1 5/5 supported outcome | V-1 | production migration and trigger-letter text prove the missing Funding support keyword |
| Child 01 O-3 | P1 keyword hygiene | RECORD_ONLY | plan wording conflicts with its own body text; operative keyword-line invariant passes |
| Child 01 O-4 | P1 fact-rule enumeration | RECORD_ONLY | plan wording says three new rules while its own enumeration has five rules |
| Child 01 O-5 | P1 reply body placeholders | RECORD_ONLY | generated V105 bodies are byte-identical to required bodies |
| Child 02 O-1 | P2 span matching | RECORD_ONLY | speculative Unicode/non-BMP canonicalization divergence; fixtures pass |
| Child 02 O-2 | P2 auto-path log scope | RESOLVED/RATIFIED | A1 item 4 explicitly ratifies `select()`-path logging breadth |
| Child 02 O-3 | P2 region attribution | RECORD_ONLY | no mandatory acceptance-letter violation proven |

No product code, tests, configuration, aggregate review evidence, staging, or commits were modified by the reviewer.

### Repair Planning Addendum — 2026-08-19T09:49:47Z

## Repair Planning Result: DRAFT_READY

- Baseline: `00-grounded-coverage-master.md`
- Verification: `FAIL / INITIAL`
- Repair artifact: `docs/plans/fix/00-grounded-coverage-master/repair.md`
- Included finding: V-1.
- Authorized execution files: `src/main/resources/db/migration/V106__add_remuneration_keyword_to_funding_support.sql`; `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt`; `docs/plans/review/grounded-coverage/repair-execution.md`.
- Excluded: transient `mvn clean package` WAR-deletion condition; every RECORD_ONLY item.
- Scope authority: HUMAN:user `批准 生成repair文件` on 2026-08-19, interpreted against the immediately preceding requested scope as authorization to draft V106 plus its production-faithful regression only. It authorizes no execution.
- Required approval: explicit `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage/docs/plans/fix/00-grounded-coverage-master/repair.md`.

The repair plan contains the resolved Review-Fast-P one-approval execution handoff: product commit subject `fix(qa): support remuneration in funding facts`; evidence commit subject `docs(review-fast-p): record repair execution`; aggregate re-review returns to this task. No implementation, tests, staging, or commits were performed by the planner.

## Epoch 2 — 2026-08-19T12:30:43Z

- Master plan: docs/plans/2026-08-19/00-grounded-coverage-master.md (sha256: 3112ffe1c665ebda5295e36a315a5bf65f3a09082febf11ce89bec1ba854d4e7)
- Governing master identity: worktree sha256 3112ffe1c665ebda5295e36a315a5bf65f3a09082febf11ce89bec1ba854d4e7; recorded commit af1723f37021328f8ffa61261504727e514fbb4b
- Master identity state: CONSISTENT; recorded amendment A1 applies to child 02.
- Boundary: af1723f37021328f8ffa61261504727e514fbb4b..a7cceb2e3fdec25cecd4e3582135edefb3a5447f
- Reviewer: /root/post_repair_aggregate_reviewer
- Result: PASS
- Convergence: PROGRESSING
- Repair artifact/result: existing executed docs/plans/fix/00-grounded-coverage-master/repair.md; NO_ACTION.
- Post-repair evidence: DURABLE_HANDOFF; approval, executor Main, and code head recorded in repair-execution.md.

### Fresh Command Evidence

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q test -Dtest=QaFactSelectionServiceTest` | PASS | exit 0; 44/0/0/0; Node 658/0 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q test -Dtest='AiReplyIntentCatalogTest,QaFactSelectionServiceTest,ProgrammeIdentityFactsMigrationTest'` | PASS | 35 + 44 + 6 tests; zero failures/errors |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q test -Dtest='InboundAskEnumeratorTest,QaFactSelectionServiceTest,AiReplyIntentCatalogTest'` | PASS | 13 + 44 + 35 tests; zero failures/errors |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q test` | PASS | Surefire 2590 tests, 0 failures, 0 errors, 4 skipped; Node 658/0 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q clean package` | PASS | Surefire 2590/0/0/4; Node 658/0; WAR assembled |
| `git diff --check af1723f37021328f8ffa61261504727e514fbb4b HEAD` and `git diff --check` | PASS | exit 0; no whitespace errors |

### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 P1 trigger letter recognises five intents | PASS | `AiReplyIntentCatalogTest.kt:247-258`; focused/full tests pass |
| M-2 five supported intents/facts, GROUNDED | PASS | `QaFactSelectionServiceTest.kt:831-896`; V106 provides production `remuneration` |
| M-3 catalog, coverage, migration invariants | PASS | V105 unchanged; V106 forward-only; migration tests 6/0/0 |
| M-4 P2 shadow-only measurement | PASS | `InboundAskEnumerator.kt:41-128`; `QaFactSelectionService.kt:39-49`; `TrustReplyWorkbenchService.kt:410-418` |
| M-5 P3 ordered drag path | PASS | `trust-reply-workbench.js:126-140,1386-1392,1405-1409`; JS suite 658/0 |
| M-6 ordering and authorized scope | PASS | P1→P2 ordering/A1 approval valid; repair product delta is exactly V106 plus `QaFactSelectionServiceTest.kt`, both authorized |
| M-7 full build gate | PASS | fresh `mvn clean package` exit 0 |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | V106 conditionally appends `remuneration` only to Funding support, preserves `updated_at`; regression proves 5/5/GROUNDED |
| Child-01 O-1/O-3/O-4/O-5 | RECORD_ONLY | no mandatory violation |
| Child-02 O-1/O-3 | RECORD_ONLY | speculative/non-proven adjacent risks |
| Child-02 O-2 | RESOLVED/RATIFIED | A1 governs broader neutral log emission |

### Fast-P RECORD_ONLY Re-evaluation

All prior RECORD_ONLY items remain non-blocking. V-1 is resolved; no regression or new mandatory finding. Optional Docker-backed Flyway integration remains outside the mandatory gate.

No product code was modified by the reviewer. Manual UI acceptance remains pending.
