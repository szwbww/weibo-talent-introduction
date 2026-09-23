# Child c4 Brief — LuKai / LuKai_QF 生产配置迁移与历史异常修复

- Child ID: `c4`
- Approved plan (complete contract): `docs/plans/2026-09-23/04-lukai-production-migration.md` — plan identity `commit:daabfdc900555f3c89a698cd85a0165ada20d1a9`
- Master plan (upper constraint): `docs/plans/2026-09-23/00-shared-inbox-main.md` — invariants M-1…M-6, phase gate G-4
- Base SHA: `<c3 code head — recorded in the ledger at dispatch>`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-shared-inbox-master`
- Branch: `fast/2026-09-23-shared-inbox-master`
- Execution report: `docs/plans/fast/2026-09-23-shared-inbox-master/children/c4/execution.md`

## Authority gate (blocks normal execution)

- The approved plan itself states: "本文件是执行方案，不是上线授权；当前绝不运行 UPDATE/DELETE/DDL/重启" and the master plan's phase table gives 04 the entry gate "独立核验 + 人工黑盒验收；**需单独上线授权**" (separate production authorization required), with M-4 forbidding any production write-path operation before that authorization and 01–03 verification.
- The `$fast-p` invocation that authorizes this run covers exactly one local worktree, one local branch, and local commits. It does **not** authorize stopping production schedulers, taking a production database backup, running DDL/DML against the production MySQL, or restarting the production Tomcat.
- c4's only repository artifact, `docs/runbooks/repair-lukai-shared-inbox.md`, is defined by the plan as a record of *actual* on-site evidence: "包含本次实际命令、脱敏行映射、备份位置、前后 SQL 结果和回滚步骤". Every one of those fields requires the authorized maintenance window; writing the runbook without it would fabricate evidence, and the plan forbids reusing the 2026-09-23 read-only snapshot numbers as execution thresholds ("执行前再重采，不能继承今天的数字").
- Therefore the fast-p workflow must stop at c4 with `PAUSED_FOR_HUMAN` unless the human supplies both the production write authorization and the maintenance window in which the work is to be performed. No child artifact may claim production results that were not observed.

## What a resumed c4 requires (from the approved plan)

1. Separate上线授权 recorded for this exact window, plus the maintenance window itself (stop application/schedulers, confirm no active receive/send job and no DB writes).
2. Fresh read-only re-collection: both accounts' UIDVALIDITY, sampled same-UID headers, cursors, QF `inbound_mail_processing` / `mail_record` rows, every FK and soft reference from the live `information_schema`; the 2026-09-23 numbers (`MANUAL_REVIEW 44` / `PROCESSED 2`, `[self-check]` 23/2, `id=383`, `id=366/369`, `INBOUND=21`) are snapshots only and must not be reused as thresholds.
3. A verifiable database backup, then manual equivalent DDL for V134 when the production schema lacks it (`SHOW CREATE TABLE` comparison; no Flyway in production).
4. Per-ID classification in the plan's order: internal `[self-check]` probes first (strict 03 detection, raw IMAP header evidence, zero dependencies, `MANUAL_REVIEW` only), then physical duplicates versus logic-account gaps, then INBOUND `mail_record` equivalence including body/cleaned-body, each with row snapshots and dependency re-pointing; `MANUAL_RESOLVED` rows (366/369) stay untouched; unknown classifications stop the run.
5. The single-row account UPDATE `LuKai_QF.inbound_mailbox_code='LuKai'` inside the same transaction, with SMTP/IMAP/limits/`enabled` values proven unchanged, both cursors retained, owner cursor not rewound.
6. Deploy of the full 01–03 WAR, read-only checks, then low-risk test mail per address; rollback plan executed from the saved snapshots if any assertion fails.

## Deliverable if resumed

`docs/runbooks/repair-lukai-shared-inbox.md` containing the executed commands, the desensitised row mapping, backup location, before/after SQL results and the rollback procedure — plus this child's `execution.md` recording the classification, commands, counts and verification. No product-code or test file may change.
