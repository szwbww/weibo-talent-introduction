# Child c1 Brief — 共享收件箱配置与兼容表结构

- Child ID: `c1`
- Approved plan (complete contract): `docs/plans/2026-09-23/01-shared-inbox-configuration.md` — plan identity `commit:daabfdc900555f3c89a698cd85a0165ada20d1a9`
- Master plan (upper constraint): `docs/plans/2026-09-23/00-shared-inbox-main.md` (invariants M-1…M-6)
- Base SHA: `daabfdc900555f3c89a698cd85a0165ada20d1a9`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-shared-inbox-master`
- Branch: `fast/2026-09-23-shared-inbox-master`
- Execution report: `docs/plans/fast/2026-09-23-shared-inbox-master/children/c1/execution.md`

## Deliverable

Additive schema + explicit single-level inbox-ownership configuration (DDL, domain mapping, service validation, REST request/response, admin form). No production data is touched; `inbound_mailbox_code` values stay NULL everywhere in this run.

## Authorized files (exactly these 10, no others)

1. `src/main/resources/db/migration/V134__shared_inbox_owner.sql`
2. `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailSenderAccount.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/mail/domain/InboundMailProcessing.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt`
5. `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailSenderAccountController.kt`
6. `src/main/resources/static/index.html`
7. `src/main/resources/static/app.js`
8. `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountServiceTest.kt`
10. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailSenderAccountControllerMvcTest.kt`

Touching any other file (including `MailSenderAccountRepository.kt`, `styles.css`, `mailbox-chat.css`) requires stopping and returning `PLAN_CONFLICT`/`BLOCKED`.

## Frozen downstream interface (children c2 and c3 consume these; do not rename)

- Domain: `MailSenderAccount.inboundMailboxCode: String?` (nullable, default `null`), column `mail_sender_account.inbound_mailbox_code`.
- Domain: `InboundMailProcessing.mailboxOwnerCode: String?` (nullable), column `inbound_mail_processing.mailbox_owner_code`.
- REST JSON property name on create/update request and on response: exactly `inboundMailboxCode` (matches the form control name `inboundMailboxCode` required by style contract S-1). `null`/empty means "独立收件箱（本账号）".
- Migration file name and version: exactly `V134__shared_inbox_owner.sql` (highest existing migration at base is V133; V134 is free — re-verify before writing).
- DDL: `mail_sender_account.inbound_mailbox_code VARCHAR(64) NULL` + FK to `mail_sender_account(account_code)`; `inbound_mail_processing.mailbox_owner_code VARCHAR(64) NULL` + unique key on `(mailbox_owner_code, uid_validity, imap_uid)`. No backfill of existing rows.
- Service validation entry points stay `MailSenderAccountService.createAccount` / `updateAccount` / `deleteAccount`, and the existing read methods `listAccounts` / `getAccount` return the new field unchanged in shape.

## Invariants (from the approved plan; violations are light-gate failures)

- `I-1` single-level relation: NULL = independent mailbox; non-NULL must reference an existing, non-simulator principal whose own `inbound_mailbox_code` is NULL, and must not be self. No chains or cycles. Relation changes must not modify `sender_email`, SMTP/IMAP credentials, `enabled`, or send counters. A principal that has children may still be edited in other fields but must not be deleted or turned into a child. Non-cascading.
- `I-2` legacy rows never get a fabricated physical generation: new column defaults NULL, existing rows stay NULL (especially `uid_validity=0`), unique key only constrains non-NULL `(mailbox_owner_code, uid_validity, imap_uid)`.
- `I-3` `enabled` keeps its existing send-side semantics; the configuration must not gate polling. Page copy must not imply `enabled=false` stops polling.
- `S-1`/`S-2` exact DOM and copy: reuse existing classes only, no new CSS, no inline styles; selector block exactly as quoted in the plan's S-1, placed after the SMTP/IMAP `form-section-pair` and before `<!-- 发送策略 -->`; the `enabled` label text becomes exactly `启用此账号发信`.
- Plan-required extra service check: because `sender_email` is not globally unique, the service must perform a case-insensitive e-mail conflict check inside the same ownership group.

## Re-checked base facts (verify again before editing; line numbers are anchors, not truth)

- `src/main/resources/static/index.html`: the SMTP/IMAP pair starts at line 1763 (`<div class="form-section-pair">`), `<!-- 发送策略 -->` is at line 1798, the `enabled` checkbox input is at line 1807 with text `开启此发件账号的轮询调度`.
- Cache keys: 11 occurrences of `?v=20260922-discovery-continuous` (lines 11–15 and 2168–2173). All resource keys share one value and must be bumped together; re-grep `rg -n '20260922-discovery-continuous' src/main/resources/static/index.html` and also find every test that pins the literal key (do not assume the count).
- `MailSenderAccountService` already has `SIMULATOR_ACCOUNT_CODE`; `listAutoReceiveAccounts()` currently returns `findAllByAccountCodeNot(SIMULATOR_ACCOUNT_CODE)` and must keep its current behavior in this child.
- `MailSenderAccountController` uses `*Request` objects with `toCommand()` and a private `toResponse(...)`; extend those, do not restructure.

## Recorded baseline (master base, before any child)

- `node --check src/main/resources/static/app.js` → exit 0.
- `node --test src/test/js/*.test.js` → exit 0, 1121 tests / 1121 pass / 0 fail.
- `mvn -B -Dtest=<the 8 classes above> test` → exit 0, Tests run: 161, Failures: 0.
- `mvn -B -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40 test` → **exit 1 with 17 pre-existing failures**, all of the identical form `expected: <131> but was: <133>` at `FlywayMigrationIntegrationTest.kt` lines 62, 136, 178, 275, 442, 571, 606, 717, 824, 840, 875, 921, 997, 1113, 1258, 1348, 1525. These are stale "latest version" pins (repo max is already V133 before your change). The approved plan directs updating exactly these latest-version assertions to the actual new maximum, keeping each test's behavior-specific version targets unchanged. After your V134 migration the pins must expect the new maximum, and this command must exit 0.

Raw transcripts: `docs/plans/fast/2026-09-23-shared-inbox-master/baseline/`.

## Required commands (run in the worktree; JDK 11 is mandatory)

```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
mvn -B -Dtest=FlywayMigrationIntegrationTest,MailSenderAccountServiceTest,MailSenderAccountControllerMvcTest test
node --check src/main/resources/static/app.js
node --test src/test/js/*.test.js
```

The Flyway IT is gated: add `-DmigrationIt=true` and `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock -Dapi.version=1.40` when running it (Docker/OrbStack verified available). Do not run the whole project suite, and do not run formatters or linters.

## Acceptance (from the approved plan)

- `I-1`: service/MVC tests cover independent, legal child, self-reference, pointing at a child, missing/simulator principal, principal deletion and principal-becoming-child; SMTP fields, limits and `enabled` unchanged across save.
- `I-2`: Flyway IT asserts both columns exist, legacy rows stay NULL, two different owners may hold the same UID, a second row with the same owner + `(uid_validity, imap_uid)` violates the unique key, and `uid_validity=0` rows are not backfilled. Update only the latest-version assertions that this change makes stale; leave historical pinned assertions at their original targets.
- `I-3`: `MailSenderAccountServiceTest` proves `enabled=false` behavior of `listAutoReceiveAccounts` is unchanged by this child; page copy is exactly `启用此账号发信`.
- `S-1`/`S-2`: DOM matches the contract fragments, no new class or inline style, no CSS edit, all resource keys identical; frontend JS syntax check and the JS test suite pass.
- Release gate (recorded, not executed): production receives only nullable columns and all values stay NULL.

## Commit and reporting

- Commit locally as `feat(fast-p): implement c1` (implementation + tests only; never include `docs/plans/fast/**`).
- Write the full result to the execution report path above: files changed, decisions, exact commands with exit codes and counts, deviations from the plan, and any blocker.
- Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.
- Do not push, merge, rebase, amend, or rewrite history; do not modify files outside this worktree.
