# c4 execution.md

## Execution Result: PAUSED_FOR_HUMAN (not started)

- Plan: `docs/plans/2026-09-23/04-lukai-production-migration.md` (identity `commit:daabfdc900555f3c89a698cd85a0165ada20d1a9`)
- Child base (product boundary): `e77cb065ba6261317adc7060b2a7729052086407` (c3's terminal code head)
- Execution epoch: 1 (never dispatched)
- Product changes: none. No file was created, modified or deleted for this child; `git log --oneline` shows no commit after c3's evidence commit for c4.
- Production objects touched: none — no DDL, no DML, no IMAP login, no scheduler stop, no WAR deploy, no restart.

## Why it was not started

The approved plan is explicitly an execution scheme rather than a deployment authorization ("本文件是执行方案，不是上线授权；当前绝不运行 UPDATE/DELETE/DDL/重启"), and the master plan gives phase 04 the entry gate "独立核验 + 人工黑盒验收；需单独上线授权" with invariant M-4 forbidding any production write-path operation before that authorization. The `$fast-p` invocation authorizes exactly one local worktree, one local branch and local commits.

c4's only repository artifact is `docs/runbooks/repair-lukai-shared-inbox.md`, defined by the plan as a record of actual on-site evidence — executed commands, desensitised per-ID classification, backup location, before/after SQL results and rollback steps. None of those fields can be produced without the maintenance window, and the plan forbids reusing the 2026-09-23 read-only snapshot counts as execution thresholds ("执行前再重采，不能继承今天的数字"). Writing the runbook from the snapshot would fabricate evidence, so the workflow stopped instead.

## What a resumed c4 requires

See `children/c4/brief.md` for the full gate analysis. In short:

1. The separate production authorization for a specific maintenance window.
2. Stop application and schedulers, confirm no active receive/send job and no database writes.
3. Fresh read-only re-collection: both accounts' UIDVALIDITY, sampled same-UID headers, cursors, QF `inbound_mail_processing` / `mail_record` rows, and every FK / soft reference from live `information_schema`; treat all previously recorded numbers as snapshots only.
4. A verifiable database backup, then manual equivalent V134 DDL when `SHOW CREATE TABLE` shows it missing (production runs without Flyway).
5. Three ordered classification passes: internal `[self-check]` probes first (strict 03 detection, raw IMAP header evidence, zero dependencies, `MANUAL_REVIEW` only), then physical duplicates versus logic-account gaps, then INBOUND `mail_record` equivalence including body / cleaned body; `MANUAL_RESOLVED` rows (366/369) stay untouched, unknown classifications stop the run.
6. In one transaction: dependency re-pointing, the confirmed merges/re-attributions/deletions, and the single-row `LuKai_QF.inbound_mailbox_code='LuKai'` update with SMTP/IMAP/limits/`enabled` proven unchanged and both cursors retained.
7. Deploy the full 01–03 WAR, verify read-only first, then low-risk test mail per address, then restore scheduling; roll back from the saved snapshots if any assertion fails.

## Carried forward from c1–c3 (deployment gate facts)

- V134 is the Flyway head added by c1 and is additive-only (`mail_sender_account.inbound_mailbox_code`, `inbound_mail_processing.mailbox_owner_code`, unique key on `(mailbox_owner_code, uid_validity, imap_uid)`); production must apply it manually with a backup.
- c2 verified LIGHT_PASS_WITH_NOTES (`77746e27c4de3ec6c8d4a32d579d61d1bd2b774c`): owner-only polling, To/Cc + unique In-Reply-To routing, physical dedup, `RECIPIENT_UNRESOLVED` manual row.
- c3 verified LIGHT_PASS_WITH_NOTES (`71d8aeb8202c872faca323960c5c58198b932945`): OUTBOUND-only bounce attribution, group-wide hard-bounce checks, single-entry `[self-check]` filtering; its RECORD_ONLY notes (O-1 constructor default, O-2 OUTBOUND-only expert association) are listed in the handoff and are relevant to how historical QF bounces are interpreted during c4.
- Nothing in c1–c3 activates the shared inbox in production: all `inbound_mailbox_code` values remain NULL until c4 performs the authorized update.
