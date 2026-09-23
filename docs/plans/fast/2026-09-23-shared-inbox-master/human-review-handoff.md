# Fast-P Human Review Handoff

- Outcome: READY_FOR_HUMAN_REVIEW
- Master base: 9237d6f573335d1624217cbc5501f68a6f52b97b
- Current/final code head: 1cff8f650cfa27ca506246380e0e56a77a20a4f9
- Branch/worktree: fast/2026-09-23-shared-inbox-master / /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-shared-inbox-master

## Child Status

| Child | Status | Code boundary | Fix rounds | Evidence commit |
|---|---|---|---|---|
| c1 | LIGHT_PASS_WITH_NOTES | daabfdc900555f3c89a698cd85a0165ada20d1a9..a15cb52b599e81753bb9fa3bcbb6e04969f44813 | 0 | 23bf02608bca49201d3eb51ab0002c1e455198ef |
| c2 | LIGHT_PASS_WITH_NOTES | a15cb52b599e81753bb9fa3bcbb6e04969f44813..e28da464bb4bf310698079340796382e32acd2d0 | 0 | 77746e27c4de3ec6c8d4a32d579d61d1bd2b774c |
| c3 | LIGHT_PASS_WITH_NOTES | e28da464bb4bf310698079340796382e32acd2d0..e77cb065ba6261317adc7060b2a7729052086407 | 0 | 71d8aeb8202c872faca323960c5c58198b932945 |
| c4 | LIGHT_PASS_WITH_NOTES | e77cb065ba6261317adc7060b2a7729052086407..1cff8f650cfa27ca506246380e0e56a77a20a4f9 | 0 | cdc9c67adbc7626501def2813f6704459e4b833e |

## RECORD_ONLY Index

| Observation | Child | Evidence | Source report |
|---|---|---|---|
| O-1 Two extra Flyway IT repairs beyond the literal latest-version pin bump (V131 delta bound to V130→V131; V124 seeded via `migrateToV23AndSeedBase()`); target-preserving, inside the authorized test file | c1 | `FlywayMigrationIntegrationTest.kt:439-449`, `:603-609` | `children/c1/verify-log.md` |
| O-2 No PNG screenshot (`page.screenshot` timeout); verifier re-rendered the shipped `index.html` and confirmed the S-1 fragment, placement, computed geometry, exact S-2 copy and candidate-list behaviour; human A-3 visual sign-off remains | c1 | `index.html:1799-1803`; cache keys `index.html:11-15,2176-2181` | `children/c1/verify-log.md` |
| O-3 `app.js:3203` hardcodes `SIMULATOR_NOOP`, consistent with `app.js:3278` | c1 | `app.js:3203`, `:3278` | `children/c1/verify-log.md` |
| O-1 The batch path still short-circuits `[self-check]` probes before `processSingle` (`AutoMailReplyService.kt:847-853`, unchanged from base) without writing business tables; c3 was required to account for it | c2 | `AutoMailReplyService.kt:847-853` | `children/c2/verify-log.md` |
| O-1 The nullable tail-defaulted `MailSenderAccountService?` parameter in `BounceCollectionService` is the only group-membership source for bounce attribution; its degraded non-Spring path is unasserted | c3 | `BounceCollectionService.kt:27-36`, `BounceBackfillServiceTest.kt:16` | `children/c3/verify-log.md` |
| O-2 The original-contact read is now OUTBOUND-only, so a bounce matching only an INBOUND Message-ID leaves `original_expert_contact_id` NULL and falls back to `failedRecipient` | c3 | `BounceCollectionService.kt:247-252` | `children/c3/verify-log.md` |
| Ordering delta: an already-recorded probe UID now returns `SELF_CHECK_IGNORED` instead of `DUPLICATE_IMAP_UID` (same `recorded=false` class) | c3 | probe filter before physical dedup | `children/c3/verify-log.md` |
| O-1 `children/c4/execution.md` was uncommitted at verification time (bookkeeping only) | c4 | worktree status | `children/c4/verify-log.md` |
| O-2 The same-physical-mailbox claim was re-verified from `mail_inbox_cursor.uid_validity` (1782107786 both logins) rather than a live IMAP login; the `_bk_c4_20260923_acc` column hash independently proves only the target column changed | c4 | cursor table + `_bk_c4_20260923_acc` | `children/c4/verify-log.md` |
| O-3 Six pre-existing orphan `inbound_mail_tag` rows (ids 19–24 → processing 78/79, created 2026-07-06) predate this window and are unchanged (6→6) | c4 | `inbound_mail_tag` | `children/c4/verify-log.md` |
| O-4 Pre-existing production hygiene items remain open: weak MySQL `root` password, plaintext credentials in `setenv.sh`, 1.05 GB `catalina.out` mixing three apps | c4 | runbook §10 | `children/c4/verify-log.md` |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-09-23/02-shared-inbox-routing.md | commit:daabfdc900555f3c89a698cd85a0165ada20d1a9 | commit:327bbbf3562bcbc1c7c7de45ce1eec1a100b1f6c | M-5 (每阶段最多改自己清单文件，超出先修计划) | 02 的 owner-only 收信列表取代计划 01 的旧断言，而该断言所在测试文件不在 02 的授权清单内 | HUMAN: option A at 2026-09-23 18:58 +0800 |
| A2 | docs/plans/2026-09-23/00-shared-inbox-main.md | commit:daabfdc900555f3c89a698cd85a0165ada20d1a9 | commit:8de18f69a63d1683515dd00c1b46fbddbd644094 | M-5 (四份子计划各自文件数 ≤10) | 记录 02 的授权文件数按 11 计的例外，保持 MAIN 验收口径自洽 | HUMAN: option A at 2026-09-23 18:58 +0800 |

## Production Window (child c4)

- Authorization: explicit human `授权` for the 04 steps (stop polling/app, DDL, per-ID delete/re-attribute, account config UPDATE, deploy/restart).
- Timeline: stop-write `21:18:39` → backup + restore verification → manual V134-equivalent DDL → per-ID classification → single fix transaction `COMMITTED` (`fail_count=0`) → WAR deploy `21:35` (health 200) → stray old-code context removed + restart `21:49` → stray-probe cleanup `21:52`.
- Post-state: `LuKai_QF.inbound_mailbox_code='LuKai'` (SMTP/IMAP/limits/`enabled` unchanged); group processing `LuKai MANUAL_REVIEW 27 / PROCESSED 109`, `LuKai_QF PROCESSED 2`; group `[self-check]` pending = 0; `id=383` gone; `366/369` preserved; 0 orphan additions; `LuKai_QF` cursor frozen at 326; one scheduler instance per cycle.
- Runbook: `docs/runbooks/repair-lukai-shared-inbox.md` (committed with `-f`; `.gitignore` ignores `docs/runbooks/*.md` since 2026-09-15 while plan 04 names it as c4's file).
- Open for humans: acceptance A-1…A-6 (test mails from external mailboxes, visual conversation/material checks), plus the hygiene items in RECORD_ONLY O-4.

No whole-system verification was performed.
