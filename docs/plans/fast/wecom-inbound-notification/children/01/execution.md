# Child 01 execution

## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/wecom-inbound-notification/docs/plans/2026-10-06/wecom-inbound-notification-01-backend.md`

Plan SHA-256: `8e98130160b78db091a2a1d8a8b94b6d7ce820e9cf2ef390899cacf0248bfac0`

Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/wecom-inbound-notification/docs/plans/2026-10-06/wecom-inbound-notification-01-backend.md@8e98130160b78db091a2a1d8a8b94b6d7ce820e9cf2ef390899cacf0248bfac0`

Execution epoch: NEW

Approval basis: HUMAN “批准该总方案及两个子方案，按 fast-p 执行” (2026-10-08), exact master and child 01 at `4826cbe111310284cf13bfe7fa395bd9e2e122ad`; current child brief read before implementation. Used `skill://execute-p`, with the brief's separate-verifier and no-mid-flight-check overrides.

Executor: WecomBackendImplementer

Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/wecom-inbound-notification`

Target branch: `fast/wecom-inbound-notification`

Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/wecom-inbound-notification@fast/wecom-inbound-notification@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/wecom-inbound-notification`

Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`

Pre-execution code SHA / child_base_sha: `4826cbe111310284cf13bfe7fa395bd9e2e122ad`

Pre-execution evidence HEAD: `c5b638826ac80f8f1ab37b0af1f40f3be12b21a5`

Post-execution code SHA: `74ead840b5c8e7ab78e57a913c882cbaed3eeeb5`

Evidence HEAD at executor handoff: `74ead840b5c8e7ab78e57a913c882cbaed3eeeb5`; this report remains uncommitted for the fast-p controller's separate evidence commit.

Implementation boundary: `4826cbe111310284cf13bfe7fa395bd9e2e122ad..74ead840b5c8e7ab78e57a913c882cbaed3eeeb5`, product commit `feat(fast-p): implement 01`. Reports, logs, ledger, plans, and unrelated files are excluded from this implementation commit. The controller's pre-existing unstaged `ledger.md` change was preserved.

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T1 / I-1, I-4, I-5, I-7 | IMPLEMENTED | V150 migration, notification repository/service/controller, application.yml | Singleton defaults false/0; same-value writes preserve generation and audit timestamp; UTC setting/outbox storage; settings and enqueue lock singleton first; disable cancels pending; physical uniqueness and token-fenced terminal updates; authenticated strict-boolean endpoint with no-store, sanitized errors and configured=false/409 behavior. Final controller and MySQL tests pass. |
| T2 / I-2, I-3, I-8 | IMPLEMENTED | AutoMailReplyService, notification service/repository | Setter injection with production lifecycle requirement; notifyGroups defaults false, polling explicitly true, UID backfill explicitly false. Skip-ack, simulator, unmatched and duplicate results excluded; qualification does not use recorded. Physical processing/contact read and enqueue run inside dedicated NESTED savepoint after core result and before receipt commit. Real MySQL rollback, uncommitted visibility, SQL-trigger failure with committed receipt/markSeen, and receipt connection unit tests pass. |
| T3 / I-4, I-5, I-6, I-7, I-8 | IMPLEMENTED | Notification repository/service | Dedicated lifecycle-owned single-thread scheduler; persisted singleton leadership and 30-second claim leases; DB-clock authorization/cooldown, one live claim, token fencing, three-attempt limit and 30/120-second backoff; expired claim recovery, bounded old terminal cleanup; safe HTTPS target allowlist and no redirects; 3s connect/5s read timeout; HTTP and integral errcode classification; bounded pure-text Unicode message. MySQL lease/rate/recovery/cleanup tests, HTTP mock tests and local HTTP smoke pass. |
| T4 / I-1–I-8 | IMPLEMENTED | Authorized service/controller/repository tests, AutoMailReplyServiceTest | Final focused JVM run: 104 tests, no failure/error/skip. Final real MySQL run: 13 tests, no failure/error/skip, including actual changed-path smoke and slow HTTP/disable scenario. Maven-bound JS suite also passes in both commands. |

### Commands

All repository commands used the target worktree as cwd and JDK 11. Final checks ran after the final implementation edits; no product/test changes followed them.

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertInboundNotificationServiceTest,ExpertInboundNotificationControllerTest,AutoMailReplyServiceTest` | PASS, exit 0 | Final run completed 2026-10-08 11:49:44 +08:00; controller 7, service 11, AutoMailReplyService 86; total 104, failures/errors/skips 0. Maven-bound Node suite 1503 pass, 0 fail; bound JS checks also completed. Raw combined log `artifact://31`, focused result receipt lines 3007/3012/3620/3624 and final BUILD SUCCESS. |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_TOOL_OPTIONS=-Dapi.version=1.44 JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertInboundNotificationRepositoryIT -DmysqlIt=true` | PASS, exit 0 | Final run completed 2026-10-08 11:47:42 +08:00; actual MySQL 8.0.36 Testcontainer with migrations through V150, 13 tests, failures/errors/skips 0. Maven-bound Node suite 1503 pass, 0 fail. Raw log `artifact://31`, MySQL result receipt lines 578/584. |
| `git diff --check` | PASS, exit 0 | Final command sequence and final staged-boundary command both exited 0. |
| `git diff --cached --check` | PASS, exit 0 | Executed after staging, covering newly added files as well as existing files. |
| `plan_identity.py <child-plan>` | PASS, exit 0 | Canonical path and SHA-256 unchanged from initial gate; final receipt `artifact://32`. |
| `worktree_identity.py <child-plan> --worktree <target> --expect-root <target> --expect-branch fast/wecom-inbound-notification --expect-git-dir <target-git-dir>` | PASS, exit 0 | Gates before staging/commit passed; receipts `artifact://32`, `artifact://33`, `artifact://35`. |
| `git commit -m 'feat(fast-p): implement 01'` and `git merge-base --is-ancestor HEAD fast/wecom-inbound-notification` | PASS, exit 0 | New target HEAD `74ead840b5c8e7ab78e57a913c882cbaed3eeeb5`, reachable from the retained target branch; receipt `artifact://35`. |

#### Actual smoke scenario

Executed as the named `SMOKE real receipt commit dedicated two workers and local HTTP stub never send to a real group` MySQL test, not merely source inspection or an HTTP mock:

1. Applied migrations to isolated real MySQL; created a test sender/campaign/contact; opened notification settings through the real service.
2. Ran the existing `AutoMailReplyService.processSingle` receipt core with real Spring Data JDBC processing/contact repositories and real transaction manager, using a matched body-truncated manual-review fixture. IMAP/SMTP and other existing business collaborators were test doubles; this scenario does not claim real mailbox polling or SMTP delivery.
3. Confirmed receipt/outbox committed before workers start, no HTTP request before commit, unchanged expert contact, and existing markSeen calls.
4. Started separate notification service instances and their dedicated executors; routed the validated fake target only in the test HTTP factory to a loopback `HttpServer`. Recorded actual POST text JSON requests and request times.
5. Observed two successful local requests at least five seconds apart and two SENT outbox rows, stable notification numbers, expert name/Emoji, no mentioned_list, and no credential in message.
6. Disabled notifications, received another fixture, re-enabled, and repeated an already processed physical UID; outbox increments remained zero.

Final stdout receipt (`artifact://31:576`):

```text
SMOKE: real receipt/outbox committed, 2 local text HTTP requests, global interval >=5s, SENT=2, disabled-period and duplicate increments=0
```

The separate slow-loopback-HTTP test additionally proves that a live HTTP request does not hold receipt/settings locks: disable and another receipt/markSeen complete on a caller thread within the test's one-second wait while HTTP is deliberately held; only the already in-flight notification completes SENT.

### Changed Files

- `src/main/resources/db/migration/V150__create_expert_inbound_notification.sql` — independent singleton settings/outbox schema, seed, physical unique key and worker scan indexes.
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/ExpertInboundNotificationRepository.kt` — setting transitions, NESTED receipt savepoint, enqueue, current-read deduplication, leadership/claim/authorization, token-fenced results, recovery and cleanup; UTC DATETIME reads avoid JDBC Timestamp/JVM-zone shifting.
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertInboundNotificationService.kt` — configuration and payload validation, best-effort registration, dedicated worker lifecycle and bounded HTTP response interpretation.
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/ExpertInboundNotificationController.kt` — session-protected GET/PUT settings, strict JSON boolean, no-store and sanitized errors.
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` — additive optional notification setter and transaction seam; existing confirm sinks, markSeen location, SMTP/core decisions unchanged.
- `src/main/resources/application.yml` — empty-default server-only `WECOM_EXPERT_INBOUND_WEBHOOK` configuration.
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertInboundNotificationServiceTest.kt` — settings/configuration, message bounds, HTTP outcomes, fail-closed worker, lifecycle and best-effort error coverage.
- `src/test/kotlin/com/weibo/talentintroduction/mail/repository/ExpertInboundNotificationRepositoryIT.kt` — actual MySQL locking/savepoint/rollback/uniqueness/lease/retry/cleanup and local HTTP smoke scenarios.
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertInboundNotificationControllerTest.kt` — downstream JSON contract, authentication/password-change boundary, boolean validation and sanitized error behavior.
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` — injection and receipt notification/exclusion/commit-order regression.

Staged exact file boundary was recorded with `git diff --cached --name-only`; no report/log/ledger was staged.

### Execution failures and corrections

- First focused invocation exited 1 at test compilation: a new fixture used nonexistent `fetchMessagesByUids`; changed that authorized fixture to the existing `fetchByUids` API. Raw failure `artifact://18`.
- Second focused invocation exited 1: new test fixture issues (non-null Mockito matcher, nested fixture stubbing, MockMvc response charset assertion). Corrected only authorized tests; existing AutoMailReplyService tests were retained. Raw failure `artifact://20`.
- Focused run then passed 104 tests, but the unmodified MySQL command exited 1 before tests because the existing Docker Java client defaulted to API 1.32 while Docker 29.4.0 requires at least 1.40. Read `docker context inspect` / `docker version`; supplied only process-local `DOCKER_HOST` and `JAVA_TOOL_OPTIONS=-Dapi.version=1.44`. No Docker daemon, dependency, pom, or user-global configuration changed. Raw failure `artifact://22`.
- Initial actual MySQL invocation exited 1: new source fixture omitted required createdAt/updatedAt; injected-failure trigger needed test-container `log_bin_trust_function_creators`; initial smoke timed out. Corrected fixture and isolated container configuration, and fixed notification repository UTC/current-clock handling rather than shifting production mail timestamps. Raw failure `artifact://25`.
- Next MySQL run exercised the smoke successfully but exited 1 on the concurrent enqueue test: repeatable-read snapshot-based dedup could miss a row committed while waiting for singleton lock. Changed the authorized notification physical-key query to `FOR UPDATE` current read. Raw failure `artifact://28`.
- Final invocation after all corrections passed both required Maven commands and diff checks; added matched simulator-source exclusion and the planned slow-HTTP isolation regression within the same authorized files.

The provided baseline “AutoMailReplyServiceTest: 79 tests, exit 0” was used only as historical baseline, not as fresh execution evidence. No baseline rerun was performed just to reconfirm it.

### External protocol evidence

- [Official 企业微信消息推送配置说明](https://developer.work.weixin.qq.com/document/path/91770) was read during implementation. It documents text JSON, UTF-8 content maximum 2048 bytes, optional mention lists, and a 20-per-minute sending limit. The implementation deliberately uses the approved stricter 1800-byte cap, no mention lists, and persisted five-second cooldown. Retrieved evidence `artifact://14`, search receipts for `2048`, `msgtype` and the sending limit.
- [Official global error code documentation](https://developer.work.weixin.qq.com/document/path/90313) was read: success must be decided by errcode rather than errmsg, 0 is success, and 45009 is frequency limiting. Retrieved evidence `artifact://15`, error-code search receipt at line 332. Other business rejection responses are terminal; arbitrary remote errmsg is never logged or stored.

### Deviations / boundaries

- No product scope expansion and no plan amendments. Child 02 was not reviewed or edited.
- Docker command environment compatibility prefix is required on this workstation and is shown explicitly above; MySQL tests were not skipped.
- No real Webhook credential, real group notification, deployment, push, merge, or history rewrite.
- No changes to existing mail data schema, mail scheduler/RabbitMQ/global auto-reply configuration, expert states, SMTP semantics, IMAP acknowledgement position, existing processing confirmation sinks, or frontend.
- `mvn test` and `mvn clean package` without focused selectors were not run: the plan places them at the pre-publication boundary, and this child assignment does not publish. The required focused commands compiled production/test code and ran their Maven-bound JS suite. A full JVM release gate remains for the controller/pre-publication workflow, not claimed as exercised here.
- Notification registration remains intentionally best effort after savepoint failure. Unknown HTTP result retry may duplicate a group message with the same stable notification number; no exactly-once or in-flight recall guarantee is claimed.

### Freshness

- Plan identity rechecked: YES, unchanged SHA-256.
- Worktree identity rechecked: YES, target branch/root/Git directory gates passed before staging and commit.
- Reported implementation commit reachable from target branch: YES.
- Required commands run this invocation after final implementation changes: YES.
- Historical evidence used only as baseline: YES.
- Product/test working tree and index are clean after implementation commit; report and controller-owned ledger remain evidence changes for the controller.

### Remaining Blocker

None for authorized child 01 execution. This is executor evidence, not an independent PASS decision.

### Next Action

Fast-p controller dispatches its separate four-gate light verifier for child 01 against code SHA `74ead840b5c8e7ab78e57a913c882cbaed3eeeb5`, then commits execution evidence under its ledger protocol. Do not treat READY_FOR_VERIFICATION as approval to execute later child work.

