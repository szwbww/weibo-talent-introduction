# Review-Fast-P Machine Verification — expert-reachability

## Epoch 1 — 2026-08-17

- Master plan: docs/plans/2026-08-16/expert-reachability-00-execution-order.md (sha256 55e4a37ae76772ea880a5c3478d14c0eb219ea2d9b07a6ba9c418314af94421c)
- Governing master identity: worktree sha256 55e4a37ae76772ea880a5c3478d14c0eb219ea2d9b07a6ba9c418314af94421c; recorded commit 1c7cf0e4c11c53d1f4d20f28964fce837f70442b
- Master identity state: CONSISTENT
- Boundary: edda3e4e67e8b4511f3c7ca76b09926c56e4f69a..59f33864c0cd91f6699f83eabf5fa88e7c1d7839
- Reviewer: ReachabilityAggregateReviewer (fresh reviewer, distinct from all fast-p writers/verifiers)
- Result: PASS
- Convergence: INITIAL (first aggregate-review epoch; no repair rounds; all children terminal LIGHT_PASS)
- Repair artifact/result: N/A (review-p routing for PASS stops; repair-p did not run; docs/plans/fix/expert-reachability/repair.md untouched)

### Implementation Boundary

29 product/test files (21 src/main + 8 src/test): ExpertReachabilityClassifier.kt, ExpertReachability.kt (new); ExpertIndexService.kt (checkReachabilityMapping), ExpertProfile.kt, ExpertSearchService.kt, ExpertIndexWriterService.kt, ExpertReachabilitySyncService.kt (new), ExpertIndexController.kt, EmailSuppressionService.kt, BounceCollectionService.kt, MailAutomationScheduler.kt, BatchExecutionModels.kt, BatchSendTaskConfig.kt, BatchSendTaskConfigService.kt, ManualInitialOutreachService.kt, V100 migration, orcid_info_candidate/application.json, app.js, index.html, styles.css; tests ExpertReachabilityClassifierTest/ExpertReachabilitySyncServiceTest/ReachabilityFilterSeamTest/BatchSendTaskConfigReachabilityTest (new) + ExpertIndexControllerTest/BatchSendTaskConfigServiceTest/EmailSuppressionServiceTest/OperatorStatusWriteSeamGuardTest (extended). Review evidence HEAD 21b6412 is docs-only, outside the reviewed boundary.

### Fresh Command Evidence (re-run this review)

| Command | Exit | Counts |
|---|---|---|
| git diff --check | 0 | clean |
| git diff --check edda3e4..59f3386 | 0 | clean |
| node --check src/main/resources/static/app.js | 0 | OK |
| JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test | 0 | Tests run: 2515, Failures: 0, Errors: 0, Skipped: 4, BUILD SUCCESS |

Total matches baseline arithmetic exactly: 2456 base + 13 (02) + 14 (03) + 1 (04) + 25 (05) + 6 (06) = 2515; new classes individually green in full run (13/10/25/6); OperatorStatusWriteSeamGuardTest 1/0/0 (A1-A3 pins valid); FlywayMigrationIntegrationTest counted in Skipped: 4 (Docker-gated).

### Master Contract Matrix

| ID | Requirement | Result | Evidence |
|---|---|---|---|
| C02-1 | I-2-1 single classify implementation | PASS | only ExpertReachabilityClassifier constructs reachability tiers |
| C02-2 | I-2-2 classify pure function (ProviderResolver only; zero IO/clock) | PASS | constructor dep audit |
| C02-3 | I-2-3 null=UNKNOWN; 4-member enum, no UNKNOWN member | PASS | enum + return type |
| C02-4 | I-2-4 BLOCKED short-circuit unsubscribe > hard-bounce | PASS | classify ordering + test case 1 |
| C02-5 | I-2-5 emailSource missing -> null, never LOW | PASS | tests 3/4 |
| C02-6 | I-2-6 mapping assertion CANDIDATE+APPLICATION only | PASS | checkReachabilityMapping |
| C02-7 | N-1 raw JSON 0-diff | PASS | git diff |
| C02-8 | N-3 sourceFields append-only | PASS | git diff |
| C02-9 | N-4 ExpertProfile appended nullable last | PASS | git diff |
| C02-10 | T2 keyword declarations candidate+application | PASS | JSON |
| C02-11 | T4 read pipeline (mapToProfile+sourceFields+response) | PASS | ExpertSearchService |
| C02-12 | T5 13-case test matrix | PASS | test green |
| C03-1 | I-3-1 null->remove-script; zero 'UNKNOWN' strings | PASS | writer + grep |
| C03-2 | I-3-2 level loop CANDIDATE+APPLICATION | PASS | syncReachabilityBatch |
| C03-3 | I-3-3 scrollExperts(CANDIDATE) sole driver | PASS | syncAll |
| C03-4 | I-3-4 tryStartWithToken+409+bindExecutionId+clearExecutionContext | PASS | endpoint |
| C03-5 | I-3-5 both incremental hooks try/catch fail-open | PASS | suppress/BounceCollection |
| C03-6 | I-3-6 mapping precondition fail-fast + 400 | PASS | syncAll first line + endpoint |
| C03-7 | IP-5 no updatedAt in either branch | PASS | writer diff |
| C03-8 | N-1 syncOperatorStatusBatch/resolveOrcidToDocIds untouched | PASS | git diff |
| C03-9 | N-2 suppress semantics preserved | PASS | save->hook->true |
| C03-10 | N-4 BulkSyncResult untouched | PASS | git diff |
| C03-11 | T6 cron gated by scheduling.enabled + runAndRecordWithResult | PASS | MailAutomationScheduler |
| C03-12 | A1 pins 90->94/431->483 context-identical | PASS | guard green, lines verified |
| C04-1 | I-4-1 frontend maps stored value only | PASS | renderContactListItems |
| C04-2 | I-4-2 unknown default, no LOW fallback | PASS | reachabilityMeta default |
| C04-3 | I-4-3 two BLOCKED labels distinct | PASS | 已退订/邮箱失效 counts |
| C04-4 | S-1 CSS block verbatim; zero existing-rule edits | PASS | styles.css diff |
| C04-5 | S-2 checkbox disable union | PASS | isBlockedReach |
| C04-6 | N-2 hIndex/enriched/tags unchanged | PASS | git diff |
| C04-7 | N-1 no default filter | PASS | loadContacts |
| C04-8 | A2 pin 483->484 | PASS | guard green |
| C04-9 | T1 response passthrough | PASS | ExpertIndexResponse |
| C04-10 | T2 dual-path keys (MySQL null, ES ?? null) | PASS | loadContacts |
| C05-1 | I-5-1 sole ES expression + 3 seams delegate + matchesExpert parity | PASS | reachabilityFilter + 4 seams |
| C05-2 | I-5-2 UNKNOWN must_not.exists / isNullOrBlank | PASS | filter + matchesExpert |
| C05-3 | I-5-3 20/20 parametrized combos | PASS | ReachabilityFilterSeamTest |
| C05-4 | I-5-4 null/blank->null; no extra filter | PASS | filter-size assertion |
| C05-5 | N-1 existing filter items/order untouched | PASS | git diff |
| C05-6 | N-3 ALLOWED_HAS_FIELDS 0-diff | PASS | git diff |
| C05-7 | T4 frontend 4 sync points | PASS | grep count 4 |
| C05-8 | A3 pins 94->95/484->485/431->476 | PASS | guard green |
| C06-1 | I-6-1 updateLegacyConfig preservation line | PASS | line 200 |
| C06-2 | I-6-2 toView+3x*Fields include; toLegacyConfig excludes | PASS | service mapping audit |
| C06-3 | I-6-3 V100 ${ count 0 | PASS | grep |
| C06-4 | I-6-4 validation reuses ALLOWED_REACHABILITY_MODES only | PASS | service audit |
| C06-5 | I-6-5 NULL no-DEFAULT + null defaults | PASS | migration + entities |
| C06-6 | N-3 BatchSendConfig 0-diff | PASS | git diff |
| C06-7 | N-4 gateFilterEnabled behavior untouched | PASS | git diff |
| C06-8 | T1 V100 unique (98/99/100) | PASS | migration dir |
| C06-9 | T6 wiring complete via A4 chain (toExecutionSnapshot->BatchExecutionSnapshot.reachabilityFilter->fromSnapshot->RecipientScope; manual preview+run carry end-to-end; legacy KV paths omit by design) | PASS | BatchExecutionModels |
| C06-10 | A4 JUDGMENT: 3 additive carrier lines exactly satisfy A4 master rule (T6 resolveScope wiring); mirror gateFilterEnabled precedent; no field semantics; authorized file | PASS | BatchExecutionModels diff |
| C06-11 | T7 all 12 gate roles mirrored incl. change-log formatter branch | PASS | app.js/index.html |
| C06-12 | T8 tests (6) cover I-6-1 core legacy-preservation case | PASS | test green |
| C06-13 | O-1 Flyway IT Docker failure environmental (self-detected; @EnabledIfSystemProperty-gated; recorded, not faked) | PASS | test behavior |
| M-1 | CandidateEligibilityService/MailVariableService/MailPlaceholderService 0-diff (master N-2/N-3) | PASS | git diff |
| M-2 | guard suite green (master N-4) | PASS | 1/0/0 |
| M-3 | manual-quota paths untouched (master N-5) | PASS | git diff |

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| 03 O-1 nullable ctor params | no mandatory invariant | stands (style only) | production Spring-injected non-null; fail-open consistent; documented in-code |
| 03 O-2 docs commits in boundary | informational | stands | impl commits isolated |
| 04 O-1 helpers in-function | no mandatory invariant | stands | behaviorally equivalent; vm-sandbox rationale evidence-backed; acceptance location-agnostic |
| 04 O-2 uncommitted evidence docs | evidence-side only | stands | no gate impact |
| 04 O-3 title emailSource empty | plan 04 T3 title spec | ESCALATED to finding RE-04-O3 | cosmetic tooltip only; tier decision/badge/checkbox/filter unaffected |
| 06 O-1 Flyway Docker failure | master 验证命令 | stands | environmental; self-detected; recorded not faked |

### Findings

- RE-04-O3 (P3, cosmetic): HIGH/LOW badge tooltip renders empty email-source label segment — `邮箱来源 ${emailSourceLabel(contact.emailSource)}` reads a field ExpertIndexResponse deliberately does not carry (plan 04 T3 mandates the template; I-6 forbade a new backend field), so every HIGH/LOW tooltip shows `邮箱来源  · 域名 <domain>`. Implementation is plan-faithful; inconsistency originates in the plan's title spec vs response surface. Impact: cosmetic tooltip text only; tier decision, badge class, checkbox disable, filtering unaffected. Triggered for every expert with reachability HIGH or LOW. Source: src/main/resources/static/app.js:4788-4792.

### Required Action

- Machine PASS (INITIAL) — no repair plan. Next: manual acceptance checklist (AWAITING_HUMAN_ACCEPTANCE).

No product code was modified during this review.
