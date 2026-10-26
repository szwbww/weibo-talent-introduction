# Discovery Recheck Repair — Execution Handoff

## Execution Result: READY_FOR_VERIFICATION

This is implementation/execution evidence, not independent PASS, human acceptance, publication approval, or a green full JVM suite.

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/fix/discovery-recheck-master/repair.md`
- Plan SHA-256: `1e62fa911bb0130b8c7ab31f76cc175608da02af84454d3edb2729abb14932ae`
- Execution ID: the canonical plan path above followed by `@1e62fa911bb0130b8c7ab31f76cc175608da02af84454d3edb2729abb14932ae`.
- Execution epoch: RESUME; same approved bytes retained across conversation restoration.
- Approval basis: human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/fix/discovery-recheck-master/repair.md` in this conversation. It authorizes the exact five-file repair, required checks, product commit and separate execution-handoff commit under §§111–126, despite the plan's unchanged DRAFT label.
- Executor: resumed top-level coding assistant; no separate executor task identity exposed.
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master`
- Target branch: `fast/discovery-recheck-master`
- Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-recheck-master`
- Worktree ID: canonical worktree root `@fast/discovery-recheck-master@` the Git directory above.
- Original master base: `b161e87464d42b9fa2cd076ef9255b0e3b2b29ab`.
- Pre-execution code SHA: `0d60b43e586584319b78c4f3f9ca86b4820dd24f`.
- Pre-execution evidence HEAD / product parent: `b6959cd47f1ff9ff9288401b0325362c14b9b54e`.
- Post-execution code SHA: `50f8484ed2c30d51adf781c8b07a16920feab75e`.
- Product subject: `fix(discovery-recheck): restore prepare and evidence contracts`.
- Evidence HEAD: the docs-only commit containing this file, with direct parent `50f8484ed2c30d51adf781c8b07a16920feab75e` and subject `docs(review-fast-p): record discovery recheck repair execution`. Its exact SHA is returned in the final tool/chat receipt; this self-reference avoids requiring a second evidence commit.
- Repair product boundary: `b6959cd47f1ff9ff9288401b0325362c14b9b54e..50f8484ed2c30d51adf781c8b07a16920feab75e`.

## Task Status and Changed Files

| Requirement | Status | Evidence |
|---|---|---|
| R-1 / V-1 | IMPLEMENTED | Query projects identity/fingerprint-bound saved `evidence_json`, at most 100 facts with total/truncation and explicit MISSING/MALFORMED/EMPTY states. Preserves excerpt, URL, SHA, original fetchedAt/sourceUpdatedAt and temporal semantics. Frozen archive record/attempt association requires saved locator/SHA agreement; unknown XML page remains null/未知. UI uses textContent and the existing frozen-source reader, not `step.output.quote`. |
| R-2 / V-2 | IMPLEMENTED | IDS omits filter; ALL_MATCHING sends empty docIds and removes empty q/issue/decision while retaining effective filters. Backend validation remains unchanged. Production component bodies reach real servlet/SQL READY; invalid IDS/filter/docId and decision bodies remain 400. |

Product commit contains exactly the following authorized paths (scope receipt: final `git diff --name-only`, `git diff --stat`, and commit output; 262 insertions / 11 deletions):

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` — saved evidence projection and additive recheck DTO field; read-only only.
2. `src/main/resources/static/discovery-recheck.js` — prepare wire adaptation and persisted evidence display/source opening.
3. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt` — actual saved facts, binding/hash mismatch, unknown page, bounded projection and absent/damaged/empty payload.
4. `src/test/js/discoveryRecheck.test.js` — wire/default/effective filters, invalidation, API-shaped literal evidence, frozen source and absent/damaged/truncated states.
5. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckFlowIT.kt` — production parser/consumer/worker persistence → query → actual HTTP equality, plus production component POST bodies accepted by the existing real servlet/MySQL.

No DiscoveryRecheckService, Worker, Rules, Gate, Commit, migration, app.js, CSS, resource key or other product/test file was modified. Existing AUDIT/ENFORCE, manual/contact/SBIR/APPLICATION protection and provider contracts remain governed by the approved plan. No production database/ES, mail/contact, paid provider or historical application was used.

## Commands and Actual Results

All repository commands ran in the target worktree. JAVA_HOME is `/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home` (11.0.32.1); binaries are `/opt/homebrew/bin/mvn` and `/opt/homebrew/bin/node`.

| Command | Actual result | Receipt |
|---|---|---|
| `/opt/homebrew/bin/node --check src/main/resources/static/discovery-recheck.js` | exit0 | `artifact://1534`, chained command continued |
| `/opt/homebrew/bin/node --test src/test/js/discoveryRecheck.test.js src/test/js/discoveryReview.test.js src/test/js/meetingConfirmationAssets.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js` | exit0; 59 tests, 0 failure/skip | `artifact://1534:78-85` |
| `/opt/homebrew/bin/node --test src/test/js/discoveryPapers.test.js` | exit0; 13 tests, 0 failure/skip | `artifact://1534:99-106` |
| `/opt/homebrew/bin/node --test src/test/js/*.test.js` | exit0; 1843 tests, 0 failure/skip | `artifact://1534:2627-2634` |
| `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=DiscoveryRecheckControllerTest,DiscoveryRecheckServiceTest,DiscoveryVerificationQueryServiceTest,DiscoveryRecheckFlowIT -DmysqlIt=true -DtrimStackTrace=false` | exit0; JVM 33 tests, 0 failure/error/skip; bound JS 1843 passed, syntax checks passed | `artifact://1573`; trimStackTrace is output-only diagnostics, not a test-selection change |
| `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn test` | **exit1**; JVM **5624 tests / 1 failure / 0 errors / 24 skipped**; bound JS not reached after Surefire failure | `artifact://1574`, independently aggregated fresh XML matches totals |
| `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home /opt/homebrew/bin/mvn package -DskipTests` | exit0; WAR built/repackaged; **JVM skipped by explicit flag**, bound JS 1843 passed and syntax checks passed | `artifact://1578:464-467,2988-3013` |
| Actual Chromium, 1280×720 and 640×900 | Production HTML/JS/CSS; actual prepare servlet/MySQL READY, persisted quotes and exact-source reader observed; no page-wide horizontal overflow | Private browser evidence below |
| `git diff --check` | exit0 before product commit | scope/whitespace receipt `artifact://1584` follows precommit checks; no formatter or unrelated cleanup |

Targeted JVM breakdown: Controller 5, Service 5, QueryService 21, FlowIT 2. Runtime receipts in `artifact://1573` include:

- `paperFacts=2 savedFacts=4 parserPersistedQueryHttpExact=true xmlPageUnknown=true frozenReaderRefs=true queryWrites=0`.
- IDS with nonempty host filters, default ALL_MATCHING and effective nonempty ALL_MATCHING each `http=202 phase=READY`.
- Fixed roster 1001, batches 500/500/1, processed 1001; AUDIT business/provider writes0; cancellation/protection/history/recovery checks passed.
- Synthetic archived pipeline used production parser/consumer/registration/worker/gate/commit; first explicit RAW creation1, repeat0; providerHttp0, old history/checkpoint/mail unchanged.
- Intentional candidate projection HTTP500 in the race fixture was followed by the expected projection-only retry. Tomcat acceptor shutdown/Java11 reflective-access warnings were observed; no new product fix for those warnings is claimed.

### Non-green full-suite baseline, not hidden

The sole failure is the plan's allowed O-10-1: `DiscoverySampleStoreTest.store acceptance cases:135`, expected QUOTA, actual SAVED. No clock/store/test workaround was made. Current files still match original master-base blobs:

| File | Base and current Git blob |
|---|---|
| `src/test/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleStoreTest.kt` | `6e83b72fd3c07cabbc4ad1da299f5e0635719045` |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleStore.kt` | `0ebbdbee67a3bb91f54c6fb916ec26583e08bffa` |
| `src/main/kotlin/com/weibo/talentintroduction/config/DiscoverySampleProperties.kt` | `edc2c100fe1fbec1476dc4231e295637a0f42a5e` |

Receipt command: `git ls-tree b161e87464d42b9fa2cd076ef9255b0e3b2b29ab -- <three paths above>` plus `git hash-object <same three paths>` returned those matching blobs. The approved plan records the fixed capturedAt/retention-clock cause. Full-suite exit1 is **not PASS**. The old-JDK timezone-error family did not recur (full suite errors0).

Fresh XML extraction was restricted to reports written after this full-suite invocation began. Private `full-suite-summary.json` SHA256 `f60529ba1044963a1a042f23e50a7d7ddfbd25d3945e463e4996c9d84b048ea4`; totals 5624/1/0/24. These skips are not passes:

| Skipped class/case | Actual reason |
|---|---|
| `discovery.service.DiscoveryPaperLayoutRegressionTest` / `private original corpus replay explicitly skips when unavailable` | Assumption failed: SKIPPED: exact original294 frozen raw corpus unavailable; no real-effect claim |
| `document.service.ExpertMeetingRecordIntegrationTest` / `document.service.ExpertMeetingRecordIntegrationTest` | System property [mysqlIt] with value [false] does not match regular expression [true] |
| `rag.RagFactAdminServiceTest` / `rag.RagFactAdminServiceTest` | System property [migrationIt] with value [false] does not match regular expression [true] |
| `auth.AuthFlowIntegrationTest` / `auth.AuthFlowIntegrationTest` | System property [migrationIt] with value [false] does not match regular expression [true] |
| `rag.RagKnowledgeBaseTest` / `rag.RagKnowledgeBaseTest` | System property [migrationIt] with value [false] does not match regular expression [true] |
| `campaign.service.DiscoveryPersonOutreachGuardServiceTest` / `campaign.service.DiscoveryPersonOutreachGuardServiceTest` | System property [mysqlIt] with value [false] does not match regular expression [true] |
| `mail.service.MeetingCalendarSendIntegrationTest` / `mail.service.MeetingCalendarSendIntegrationTest` | System property [mysqlIt] with value [false] does not match regular expression [true] |
| `reply.service.ReplyIdentityDefaultsIntegrationTest` / `reply.service.ReplyIdentityDefaultsIntegrationTest` | System property [mysqlIt] with value [false] does not match regular expression [true] |
| `campaign.service.MeetingCalendarServiceMysqlTest` / `campaign.service.MeetingCalendarServiceMysqlTest` | System property [mysqlIt] with value [false] does not match regular expression [true] |
| `mail.controller.MailboxConversationControllerTest` / `mail.controller.MailboxConversationControllerTest` | System property [mysqlIt] with value [false] does not match regular expression [true] |
| `discovery.repository.DiscoveryPersonRepositoryTest` / `discovery.repository.DiscoveryPersonRepositoryTest` | System property [mysqlIt] with value [false] does not match regular expression [true] |
| `campaign.controller.MeetingCalendarControllerMysqlTest` / `campaign.controller.MeetingCalendarControllerMysqlTest` | System property [mysqlIt] with value [false] does not match regular expression [true] |
| `rag.RagLetterComposerTest` / `registeredDeviationsThinkingAndStreamAreNotImplemented` | I-46 已登记偏离（不实现）：thinking:{type:disabled} 与 stream:false 与脚本不一致，本轮只登记。见类注释与上方注释。 |
| `discovery.service.EuropePmcDataSourceTest` / `smoke test real Europe PMC API returns papers` | Requires network access to Europe PMC API - run manually to verify |
| `mail.service.MailReplyDraftSendIntegrationTest` / `mail.service.MailReplyDraftSendIntegrationTest` | System property [mysqlIt] with value [false] does not match regular expression [true] |
| `expert.service.ExpertSearchCursorTest` / `real ES 7_14 cursor traverses 11001 documents and early stop frees independent contexts` | System property [esIt] does not exist |
| `campaign.repository.FlywayMigrationIntegrationTest` / `campaign.repository.FlywayMigrationIntegrationTest` | System property [migrationIt] with value [false] does not match regular expression [true] |
| `audit.repository.OperatorActionLogRepositoryTest` / `audit.repository.OperatorActionLogRepositoryTest` | System property [migrationIt] with value [false] does not match regular expression [true] |
| `mail.repository.InboundMailProcessingRepositoryTest` / `mail.repository.InboundMailProcessingRepositoryTest` | System property [migrationIt] with value [false] does not match regular expression [true] |
| `mail.controller.CalendarAttachmentIntegrationTest` / `mail.controller.CalendarAttachmentIntegrationTest` | System property [mysqlIt] with value [false] does not match regular expression [true] |
| `discovery.service.DiscoveryVerificationIntegrationTest` / `discovery.service.DiscoveryVerificationIntegrationTest` | System property [mysqlIt] with value [false] does not match regular expression [true] |
| `document.controller.ExpertMeetingVideoStreamingIntegrationTest` / `hundred mebibytes plus one byte uploads over real http` | Condition provided in @EnabledIf evaluates to false |
| `document.controller.ExpertMeetingVideoStreamingIntegrationTest` / `one gibibyte plus one byte is rejected without leaving files or rows` | Condition provided in @EnabledIf evaluates to false |
| `document.controller.ExpertMeetingVideoStreamingIntegrationTest` / `one gibibyte uploads over real http without buffering and serves ranges` | Condition provided in @EnabledIf evaluates to false |

## Browser / Smoke Evidence

Private evidence directory: `/private/tmp/discovery-recheck-repair-n84wns__`. No private historical corpus or credentials were copied into the repository. Browser summary SHA256 `526ffcd30d5e8a7b7da3c2abfc1591064b0ff2e344aea2e7d4d8ea6420f74e93`.

- Actual isolated MySQL8.0.36, migrations through V161; actual production recheck Controller/Service/Query and local authenticated servlet. ES scan responses were strict loopback fixtures. Other host APIs were read fixtures, not production services.
- Archive responses were exported through production `DiscoveryPaperArchiveService` from the same synthetic body already processed by production parser/consumer/Worker. Source record1 / attempt1, SHA `17bca0cc75612afd04cdeab38ea3103bd4537d55be9918617d8613095f76a269`, source execution28671 / CROSSREF / fixture-900001.
- Desktop IDS run6: POST202, READY, total1, no filter. Desktop ALL_MATCHING run8: POST202, READY, total2, empty filter object and empty docIds. Narrow IDS run11 and ALL_MATCHING run10 likewise POST202/READY with totals1 and2. Additional fixture prepares7/9 also reached READY; no prepare result was applied or sent by the browser.
- Selected committed run4/item3 through real UI history, step4. Both current paper facts and previously persisted same-source observations are visible: four saved facts total, not deduplicated or retimed. Unknown XML page explicitly shown as 未知, paragraph2, locator retained. Original fetchedAt values `2026-10-24T12:00:00Z` and `2026-10-18T00:00:00Z` remain distinguishable.
- Reader requested `/api/expert-discovery/papers/1/attempts/1/content`, HTTP200. Both viewport screenshots show the actual XML and the reader's SHA-checked / no-invented-page notice.
- Document widths1280 and640 matched viewports. Narrow review table client576 / scroll1000 / overflowX=auto; table scroll is local. Prepare Esc restored focus to 批量重新审核 and did not cancel the fixed task. White opaque dialogs and visible quotes inspected in actual screenshots.
- Retained screenshots: `wide-ids-ready.png`, `wide-all-ready.png`, `wide-evidence.png`, `wide-reader.png`, `narrow-ids-ready.png`, `narrow-all-ready.png`, `narrow-evidence.png`, `narrow-reader.png`; per-file SHA256 in browser-summary.json.
- Browser/helpers had setup observations, not product PASS evidence: first all-page wait matched stale hidden text and was repeated using the open dialog's enabled confirmation; a page-world/document access mistake and an unsupported text selector were corrected before proof capture. First standalone launches used stale compiled classes; one manual compiler launch lacked Spring all-open. Final standalone backend used freshly compiled current test source with the project's Spring all-open semantics and completed the entire archived runtime before announcing ready. None of those failed attempts counted as successful smoke proof.
- Browser diagnostics ended with one expected ERR_ABORTED for a history GET during navigation/unmount, no script exception. An unrelated account-panel fixture shape displayed `state.accounts.map is not a function`; account services were not exercised and are not claimed verified.
- Browser tab closed; temporary proxy stopped; isolated backend/MySQL closed with exit0. Temporary harness sources, launcher, Java args and compiled probe classes removed. Aggregate logs/JSON/screenshots remain private for evidence.

## Failed Attempts and Corrections

Earlier targeted attempts are retained as failures, not substituted for the final passing gate: nullable cross-module smart-cast compilation was corrected with stable local values; Node vm wire capture needed browser URL/Abort globals; the evidence assertion initially assumed only two total saved facts instead of two current plus retained observations. Real output established four saved facts with distinct original fetchedAt values. HTTP/direct-tree comparison now compares canonical serialized JSON rather than LongNode/IntNode implementation identity. Additional successful prepare regressions legitimately add history rows; the history test now verifies a +1 transition against its captured prior count, rather than assuming an otherwise empty fixture history. These are authorized test/direct-dependency corrections, not new business behavior. Failed command receipts include `artifact://1524` and `artifact://1532`; final gate `artifact://1573` passed freshly after the corrections.

## Freshness, Scope and Handoff

- Plan hash rechecked unchanged before product staging/commit; `plan_identity.py` and `worktree_identity.py` returned the exact identity above.
- `worktree_identity.py --expect-root ... --expect-branch fast/discovery-recheck-master --expect-git-dir ...` ran before both product git add and commit. Product SHA is HEAD of the target worktree and reachable from its recorded branch (`merge-base --is-ancestor` exit0).
- Product commit output confirms five files. Immediately after the product commit, `git status --short` was empty; no unrelated tracked or untracked work was introduced in this worktree. Original worktree changes were not staged or touched.
- Required commands were run on the final source state; historical results used as baseline only. No applied migration was changed, no plan was amended, no new scope authority inferred.
- WAR: `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war`, SHA256 `d6317df21eab97545cfe4b2039ed616ab7c3b5ed277e2a8ccc18b75462ab6090`. Built only; not deployed.
- This file is the sole separately authorized evidence change. Evidence staging/commit and final plan/worktree/reachability/clean-state receipts are returned by the controller tools; no earlier ledger, machine report or child log is rewritten.
- Remaining in-scope execution blocker: none under the plan's command-specific criteria. Full-suite non-green O-10-1, 24 skips, original294 absence, historical PARTIAL and prior private-session evidence limitation are not waived.
- Next action: independent verification/re-review against the approved master plus this repair and committed execution handoff, preserving finding IDs V-1/V-2. The executor does not issue a new machine verdict. Human A-M1–A-M5 and governing-master sign-off remain PENDING; no push, merge, production application, provider call or release authorization is inferred.
