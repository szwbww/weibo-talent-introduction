# c1 execution.md

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-shared-inbox-master/docs/plans/2026-09-23/01-shared-inbox-configuration.md`
- Plan SHA-256: `44ea2571319c4069b6d5320187e5780008219c163438409cc9c364fae49e8542`
- Execution ID: `<worktree>/docs/plans/2026-09-23/01-shared-inbox-configuration.md@44ea2571319c4069b6d5320187e5780008219c163438409cc9c364fae49e8542`
- Execution epoch: NEW (no prior execution evidence for this identity; the previous `execution.md` was the empty template)
- Approval basis: current invocation (child brief `children/c1/brief.md`, approved plan above, plan identity `commit:daabfdc9`)
- Executor: `C1Implementer`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-shared-inbox-master`
- Target branch: `fast/2026-09-23-shared-inbox-master`
- Worktree ID: `<worktree root>@fast/2026-09-23-shared-inbox-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-23-shared-inbox-master`
- Child base SHA: `daabfdc900555f3c89a698cd85a0165ada20d1a9` (worktree HEAD before execution was `aa47dde` = base + controller-seeded briefs/baseline ledger; no product code difference)
- Pre-execution code SHA: `aa47dde3657a0fbca2b80f624dfd96dd1ee4cff0`
- Post-execution code SHA: `a15cb52b599e81753bb9fa3bcbb6e04969f44813`
- Evidence HEAD: `a15cb52b599e81753bb9fa3bcbb6e04969f44813` (single product/test commit; no separate evidence commit was requested)
- Implementation boundary: `aa47dde..a15cb52`

## Files changed (exactly the 10 authorized files)

| # | File | Change | Reason |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V134__shared_inbox_owner.sql` | NEW (33 lines) | Additive DDL: `mail_sender_account.inbound_mailbox_code VARCHAR(64) NULL` + self-FK `fk_mail_sender_account_inbound_mailbox → mail_sender_account(account_code)`; `inbound_mail_processing.mailbox_owner_code VARCHAR(64) NULL` + UNIQUE `uk_inbound_mail_processing_owner_uid (mailbox_owner_code, uid_validity, imap_uid)`. No backfill, no data rewrite. V134 verified free (repo max = V133) before writing. |
| 2 | `src/main/kotlin/.../mail/domain/MailSenderAccount.kt` | +`val inboundMailboxCode: String? = null` (after `countryName`) | Frozen downstream mapping (c2/c3); nullable default `null` keeps every existing construction valid. |
| 3 | `src/main/kotlin/.../mail/domain/InboundMailProcessing.kt` | +`val mailboxOwnerCode: String? = null` (after `senderAccountCode`) | Frozen downstream mapping; legacy rows stay NULL (I-2). |
| 4 | `src/main/kotlin/.../mail/service/MailSenderAccountService.kt` | +78 lines | `inboundMailboxCode` on create/update commands + `toDomain()`/`copy()`; new `requireValidInboundMailbox` (existence, non-simulator, single level, not-self, principal-with-children cannot become a child, case-insensitive e-mail conflict per ownership group), `childrenOf`, `normalizeInboundMailboxCode` (blank ⇒ `null`); `deleteAccount` rejects a principal that still has children; `createAccount` normalizes before save. `enabled`, SMTP/IMAP fields and counters untouched. |
| 5 | `src/main/kotlin/.../mail/controller/MailSenderAccountController.kt` | +9 lines | `inboundMailboxCode` on create/update request, `toCommand()`, response and `toResponse()` — JSON property name exactly `inboundMailboxCode`. Existing `*Request.toCommand()` / private `toResponse` structure preserved. |
| 6 | `src/main/resources/static/index.html` | +32/−? | S-1 fieldset placed after the SMTP/IMAP `form-section-pair` and before `<!-- 发送策略 -->` (verbatim per plan), S-2 copy `启用此账号发信`, all 11 cache keys bumped `20260922-discovery-continuous → 20260923-shared-inbox-owner`. |
| 7 | `src/main/resources/static/app.js` | +40/−1 | `fillInboundMailboxOptions()` builds options from `state.accounts` via DOM `option`/`textContent` (no `innerHTML`), excludes current account, `SIMULATOR_NOOP` and accounts that are already children; `fillAccountForm` populates/echoes the select; `saveAccount` sends `inboundMailboxCode` (`"" → null`). |
| 8 | `src/test/kotlin/.../campaign/repository/FlywayMigrationIntegrationTest.kt` | +162/−? | 17 latest-version pins `"131" → "134"`; test rename `fresh database migrates through V131 → V134`; new `V134 adds shared inbox owner columns without backfilling history` (columns/FK/unique key, legacy rows NULL, `uid_validity=0` not backfilled, FK rejects dangling owner, two owners may share the same UID, same owner + same `(uid_validity, imap_uid)` rejected); two masked pre-existing failures repaired (see Deviations). |
| 9 | `src/test/kotlin/.../mail/service/MailSenderAccountServiceTest.kt` | +288 | I-1/I-3 behavior tests: independent, legal child, self-reference, child-as-principal, missing principal, simulator principal, principal-with-children cannot become child, principal-with-children cannot be deleted, group e-mail conflict (case-insensitive), SMTP/limits/enabled/today count unchanged across a relation save, blank ⇒ independent, read shape via `getAccount`/`listAccounts`, `listAutoReceiveAccounts` still returns `enabled=false` + shared-inbox accounts. |
| 10 | `src/test/kotlin/.../mail/controller/MailSenderAccountControllerMvcTest.kt` | +201 | JSON round trip of `inboundMailboxCode` on POST and PUT, legacy payload without the field stays independent, illegal relation ⇒ 400 `BAD_REQUEST` with the service message. |

Nothing outside these 10 files changed. `docs/plans/fast/**` was not touched (the controller-created `children/c2/brief.md` remains untracked and was NOT committed).

## Decisions taken

1. **Ownership group definition** for the extra plan-required e-mail uniqueness check: `group(account) = inboundMailboxCode ?: accountCode`. A save is rejected if any *other* account in the same group has the same `sender_email` (case-insensitive, trimmed). This covers alias↔alias and owner↔child collisions while leaving accounts that have not opted into sharing untouched.
2. **Single-level relation**: rejected before any repository write when the target is the account itself, does not exist, is `SIMULATOR_NOOP`, or already has a non-NULL `inboundMailboxCode`. An account that already has children is rejected only when it would *become* a child; its other fields stay editable and it is still selectable as an owner.
3. **Blank ⇒ independent**: `null`/empty/whitespace `inboundMailboxCode` is normalized to `null` on both create and update, so old payloads and the "独立收件箱（本账号）" option mean the same thing.
4. **Error mapping**: all relation violations use `require`/`IllegalArgumentException`, which `GlobalExceptionHandler` maps to `400 BAD_REQUEST` (plan requires a 400 for illegal relations); the delete-guard uses `IllegalStateException`.
5. **UI candidate list**: owners are `state.accounts` entries with an empty `inboundMailboxCode`, excluding the account being edited and `SIMULATOR_NOOP` — i.e. already-linked children can never be picked. A currently-stored owner that is missing from the candidate set is still echoed as a single option so the form never silently drops a stored relation.
6. **Cache key value**: `20260923-shared-inbox-owner`; all 11 `?v=` occurrences share it. No test pins a literal key (all read `styles.css?v=` from `index.html`), and the new value collides with none of the retired-key assertions.
7. **FK is non-cascading** (MySQL default `RESTRICT`), which is the DB-level backstop for "a principal that has children cannot be deleted".
8. **`uid_validity=0` rows**: no `UPDATE` of any kind exists in V134; the unique key tolerates multiple NULL `mailbox_owner_code` rows, so all legacy generations keep their current uniqueness semantics.

## Commands (all run in the worktree, JDK 11, final implementation state)

| Command | Exit | Result |
|---|---|---|
| `mvn -B -Dtest=FlywayMigrationIntegrationTest,MailSenderAccountServiceTest,MailSenderAccountControllerMvcTest test` | 0 (`BUILD SUCCESS`) | `Tests run: 71, Failures: 0, Errors: 0, Skipped: 1` — MailSenderAccountControllerMvcTest 8/0/0, MailSenderAccountServiceTest 62/0/0, FlywayMigrationIntegrationTest 1 skipped (gated by `migrationIt`) |
| `mvn -B -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40 test` with `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock` | 0 (`BUILD SUCCESS`, `PIPE_EXIT=0`) | `Tests run: 27, Failures: 0, Errors: 0, Skipped: 0` (baseline: 26 run / 17 failures) |
| `node --check src/main/resources/static/app.js` | 0 | no output (matches baseline) |
| `node --test src/test/js/*.test.js` | 0 | `tests 1121 / pass 1121 / fail 0 / suites 222` (identical to baseline) |

Intermediate runs during repair (not evidence of the final state): the first IT run after the pin bump still failed 2 tests (details below); the two affected tests were then verified in isolation (`mvn -B -Dtest='FlywayMigrationIntegrationTest#V124*+V131*' -DmigrationIt=true` → `Tests run: 2, Failures: 0, Errors: 0`), and the full commands above were re-run afterwards on the final state.

### Plan-required manual acceptance (not executed here, human checklist A-1…A-3)
Browser-side checks were performed against the real `index.html` in managed Chromium (`file://…/src/main/resources/static/index.html`), exercising the shipped `fillInboundMailboxOptions`/`fillAccountForm` code:

- selector exists exactly once; isolated + legal-owner candidates only, `Alias`/`Other` (already children) and `SIMULATOR_NOOP` excluded, current account excluded;
- edit mode echoes `LuKai` for an account whose stored owner is `LuKai`; view mode disables the control and still shows the stored owner; new-account mode defaults to `独立收件箱（本账号）`;
- rendered geometry of the new block: fieldset `1px solid rgba(15,23,42,.055)` / radius `10px` / padding `12px 14px 14px`; `form-grid-1` `display:grid; gap:10px`; select `height 34px`, `13px`, padding `6px 10px`, radius `7px`; `checkbox-row` `min-height 32px`; enabled row text exactly `启用此账号发信`. No inline style, no new class, no CSS file touched.
- `page.screenshot`/CDP `Page.captureScreenshot` timed out (`protocolTimeout`) in this environment on every attempt (also with clips and after all Maven/Docker load stopped), so no PNG artefact could be captured; the DOM/computed-style inspection above is the visual evidence, and A-3 remains for the human.

## Deviations from the plan

1. **Two masked pre-existing Flyway IT failures had to be repaired** to satisfy "all three commands must exit 0". Both were hidden at master base behind the failing version pins (JUnit stops at the first failure), so they appear in neither the brief's 17-failure list nor the baseline transcript, and neither is caused by the new V134 DDL:
   - `V131 creates the enrichment job table on a V130 database…` asserted `historyBefore + 1` *after* migrating V130 → latest. With base max V133 this is +4 with V134 present, so the delta check is now bound to the V130 → V131 step (`flyway(MigrationVersion.fromVersion("131")).migrate()`) where exactly one migration record is added; the latest-version pin in the same test remains `134` and all other assertions are unchanged. This preserves the behavior target instead of hard-coding a second brittle count.
   - `V124 allows material attached promotion audit trigger` inserted `expert_application_promotion.expert_contact_id = 1` on a **fresh** database, but `fk_eap_contact` requires the contact to exist → `SQLIntegrityConstraintViolationException` once the pin above it stopped failing. The test now starts from the file's existing `migrateToV23AndSeedBase()` helper (campaign + contacts id 1/2) and still migrates to the latest version; the `triggered_by` width and `MATERIAL_ATTACHED` assertions are unchanged.
   No acceptance criterion was weakened and no behavior-specific version target was removed.
2. **MVC test stubbing** uses this file's existing nullability workaround (`Mockito.any(...) ?: <real instance>`, `Mockito.eq("x") ?: "x"`) because Mockito matchers return `null` and Kotlin's non-null parameter checks reject them at the call site.
3. **Extra `_tags`/`_refresh` audit for the S-1 selector**: the plan's fragment is rendered verbatim; an added `<!-- 收件箱归属 -->` comment (matching the file's existing `<!-- SMTP + IMAP 并排 -->` / `<!-- 发送策略 -->` convention) precedes it. Comments are not DOM nodes and no class/style/CSS rule was added.
4. Screenshot capture was impossible (see above); no other verification step was skipped.

## Invariants / frozen interface check

- `I-1`: single level enforced in the service (self, missing, simulator, principal-that-is-itself-a-child, principal-with-children-becoming-a-child, principal deletion) and backed by a non-cascading self-FK; relation saves leave `sender_email`, SMTP/IMAP credentials, `enabled`, and send counters untouched (asserted in `MailSenderAccountServiceTest`).
- `I-2`: V134 adds both columns as NULL with no `UPDATE`/`INSERT`; the unique key only constrains non-NULL `(mailbox_owner_code, uid_validity, imap_uid)`; legacy rows and `uid_validity=0` rows stay NULL (asserted in the V134 IT).
- `I-3`: `enabled` semantics unchanged; `listAutoReceiveAccounts()` still returns disabled and shared-inbox accounts (asserted); page copy is exactly `启用此账号发信`.
- `S-1`/`S-2`: DOM matches the contract fragment, existing classes only, no new CSS/inline style, all 11 resource keys identical.
- Frozen downstream contract intact: `MailSenderAccount.inboundMailboxCode`, `InboundMailProcessing.mailboxOwnerCode`, JSON property `inboundMailboxCode`, migration name `V134__shared_inbox_owner.sql`, DDL shape as specified, service entry points unchanged.
- Release gate (recorded, not executed): only nullable columns ship, and `inbound_mailbox_code` stays NULL for every account in this run.

## Freshness

- Plan identity rechecked after execution: YES (`44ea2571…`, unchanged)
- Worktree identity rechecked before staging/commit (`--expect-root/--expect-branch/--expect-git-dir`): YES
- Reported commit reachable from the target branch as `HEAD`: YES (`git merge-base --is-ancestor` OK)
- Required commands run this invocation on the final state: YES
- Historical evidence used only as baseline: YES

## Remaining blocker

- None. (`page.screenshot` timing out in the managed browser is an environment limitation, reported above; it does not block implementation or the required commands.)

## Next action

- READY_FOR_VERIFICATION → run `verify-p`
