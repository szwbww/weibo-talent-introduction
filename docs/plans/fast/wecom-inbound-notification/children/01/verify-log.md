# Child 01 verify-log


# epoch1 attempt1 — reviewed range 4826cbe111310284cf13bfe7fa395bd9e2e122ad..74ead840b5c8e7ab78e57a913c882cbaed3eeeb5 — 2026-10-08T12:00:12+08:00

## Light Verification: LIGHT_PASS
Child: 01 — docs/plans/2026-10-06/wecom-inbound-notification-01-backend.md
Boundary: 4826cbe111310284cf13bfe7fa395bd9e2e122ad..74ead840b5c8e7ab78e57a913c882cbaed3eeeb5
Verifier: WecomBackendVerifier (fresh independent verifier; distinct from writer WecomBackendImplementer)

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact boundary inspected with git diff; git diff --name-only <base>..<head> -- src/main src/test lists exactly the authorized ten product/test paths. Additional boundary files are controller-owned fast-p preflight evidence, not product expansion. git log -3 shows reviewed HEAD 74ead840, preceding preflight c5b63882, approved plan base 4826cbe1. git diff 74ead840 -- src/main src/test is empty, so freshly tested product/test sources match the reviewed head. No product, test, plan, index or commit edits by verifier. |
| Plan and invariants | PASS | Direct evidence for I-1 through I-8 mapped below; fresh focused 104 tests and real MySQL 13 tests pass with zero skips. Existing receipt processing core and acknowledgement placement remain unchanged; notification registration is inside its transaction, with independently created NESTED savepoint and catch outside the savepoint. Fresh local HTTP smoke exercises the actual receipt/outbox/worker path without real group calls. |
| Required commands | PASS | Fresh JDK11 focused Maven command exit 0, 104 tests (controller 7/service 11/receipt 86), failures/errors/skips 0; completed 2026-10-08T11:56:43+08:00, artifact://44. Fresh real MySQL command with supplied Docker/API environment exit 0, 13 tests, failures/errors/skips 0; completed 2026-10-08T11:59:23+08:00, artifact://46. Maven-bound JS suite in both runs: 1503 passed/0 failed; bound syntax checks completed. git diff --check exit 0, no output. Baseline receipt tests were 79/exit0; fresh receipt tests are 86/exit0, plus new service/controller tests absent at baseline. No baseline command rerun. |
| Downstream interfaces | PASS | ExpertInboundNotificationController.kt:20-34 exposes exact GET/PUT /api/expert-inbound-notifications/settings, strict JSON boolean enabled, session identity, successful no-store; Repository.kt:19-24 response fields enabled/configured/generation/updatedAt. ControllerTest.kt:39-90 asserts exact JSON, boolean rejection, authentication/password-change boundary, 409 unconfigured enable, available disable and sanitized errors. Child02 plan I-1 consumes the same endpoint and enabled/configured semantics; AuthWebConfig.kt:23-26 routes it through existing /api/** interceptor, AuthInterceptor.kt:18-59 enforces login/user/password-change rules. No downstream interface mismatch. |

Direct invariant evidence (paths below are under src/main/kotlin/com/weibo/talentintroduction/mail unless explicitly test/resource):
- I-1: resources/db/migration/V150__create_expert_inbound_notification.sql:1-13 defaults false/0; repository/ExpertInboundNotificationRepository.kt:45-62 missing-row default, locked setting and true-change-only generation/audit updates; RepositoryIT.kt:106-121 validates idempotence/reconstruction/missing-row; ServiceTest.kt:59-80 validates configuration and DB fail-closed.
- I-2: service/AutoMailReplyService.kt:106-110,153-166,927,1002 uses default false, ordinary polling true, UID backfill false; excludes skip acknowledgement, simulator and duplicate outcomes without using recorded. service/ExpertInboundNotificationService.kt:74-90 reads the exact physical processing row and existing expert inside savepoint. AutoMailReplyServiceTest.kt:932-1010 covers global auto-reply disabled, recorded=false, UID backfill, simulator, unmatched, body truncation and duplicate exclusions. No historical work-order writers changed.
- I-3: repository/ExpertInboundNotificationRepository.kt:39-43,64-68 creates dedicated NESTED template and requires real outer transaction; service enqueue catches outside it. RepositoryIT.kt:123-157 proves rollback removes receipt/outbox, another connection cannot see uncommitted outbox, injected SQL failure still commits the real receipt and marks seen. Existing commit-before-markSeen is preserved; receipt test verifies ordering.
- I-4/I-5: repository/ExpertInboundNotificationRepository.kt:47-61,72-95,99-171 locks singleton before queue changes; generation cancellation, persisted leader/rate/claim leases, one live claim, token-fenced completion, three attempts and 30/120-second retry delays. RepositoryIT.kt:176-282 exercises concurrent physical dedup/enqueue-disable, disable/re-enable, multiple owners/rate limit, expired claims, old-token rejection, final-attempt failure and permanent errors. Bounded terminal cleanup at Repository.kt:175-179 and RepositoryIT.kt:284-303. Status and unique physical key migration lines 24-38 correspond to all claim/finish/recovery consumer branches.
- I-6: service/ExpertInboundNotificationService.kt:48-58,94-137 uses dedicated single-thread executor with lifecycle shutdown, 3s connect/5s read timeouts and top-level worker catch; repository persists 30s leases and 5s cooldown. ServiceTest.kt:154-170 tests lifecycle. RepositoryIT.kt:305-367 exercises two dedicated service instances with real MySQL and local HTTP, and :369-411 proves slow HTTP does not hold settings/receipt locks. No existing scheduler, RabbitMQ or automatic-reply settings modified.
- I-7: application.yml:117-118 adds only empty-default server-side webhook; service/ExpertInboundNotificationService.kt:61-70,204-215 validates allowed HTTPS host/path/no userinfo/key; HTTP disables redirects and sanitizes all diagnostics. Controller.kt:25-54 preserves session identity, exact JSON contract and sanitized statuses. Controller and service focused tests cover authentication, forced password change, malformed booleans, configuration, errors and redirect rejection.
- I-8: service/ExpertInboundNotificationService.kt:140-171 sends text JSON, checks integral errcode and classifies HTTP/business outcomes; :173-202 formats stable notification id and bounded cleaned pure-text fields, 160 Unicode code points and 1800 UTF-8-byte safe prefix. ServiceTest.kt:82-152 covers fallbacks, quotation removal, Emoji/Chinese bounds, no mentions/credentials and HTTP failures. Fresh smoke receipt artifact://46:564 confirms 2 local text requests, global interval >=5s, SENT=2, disabled-period/duplicate increments=0.

Fresh commands (cwd: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/wecom-inbound-notification):
1. JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertInboundNotificationServiceTest,ExpertInboundNotificationControllerTest,AutoMailReplyServiceTest — exit 0, BUILD SUCCESS, 104 JVM tests.
2. DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_TOOL_OPTIONS=-Dapi.version=1.44 JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertInboundNotificationRepositoryIT -DmysqlIt=true — exit 0, BUILD SUCCESS, 13 real MySQL tests; migrations applied through V150 (artifact://46:561). No skipped MySQL substitution.
3. git diff --check — exit 0, no output.

Scope of this result: four-gate child verification only, not aggregate release verification. No full-review/verify/repair/fix-v invocation, aggregate Maven suite/package, real mailbox/SMTP integration, deployment or real group Webhook calls. The plan reserves full JVM suite/package and authorized real-group acceptance for the later pre-publication/human boundary; not claimed here. Implementer execution.md was read as context, not substituted for fresh required commands.

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD
