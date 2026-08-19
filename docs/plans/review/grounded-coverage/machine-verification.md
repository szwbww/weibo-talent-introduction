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
