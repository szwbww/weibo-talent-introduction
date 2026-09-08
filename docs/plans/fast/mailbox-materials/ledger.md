# Fast-P Ledger — master: docs/plans/2026-09-07/00-mailbox-materials-master.md

- Status: PAUSED_FOR_HUMAN
- Master plan: docs/plans/2026-09-07/00-mailbox-materials-master.md (commit a61ecb5aae0a3d1a588be80e4ec141b6d3a10bb2)
- Amendments: N/A
- Master base: 8a0c5360e25e875e52800d17797a7b1ea4bd452c
- Branch: fast/mailbox-materials
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-09-07T16:49:36Z
- Current child: 07
- Waiting role: IMPLEMENTER
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: Ambiguous mapping GET /api/expert-contacts/{id}/materials: campaign ExpertContactManagementController.listMaterials (pre-existing at master base 8a0c536:244) collides with child-06 ExpertMaterialController class-level mapping (ExpertMaterialController.kt:27) -> Spring boot failure; repair needs authorized-file widening (contract change) -> amendment approval
- Resume from: N/A

## Baseline

- Master plan and all 11 child plans (docs/plans/2026-09-07/01..11), the audit/evidence/style/baseline docs (audit.md, code-search-evidence.md, ui-style-contract.md, frontend-baseline.md), and the referenced preview assets (artifacts/mailbox-chat-preview/) were untracked on main at run start; seeded on the branch as docs-only commit `a61ecb5aae0a3d1a588be80e4ec141b6d3a10bb2`, which is not an amendment. Master and all child plan identities = `commit:a61ecb5aae0a3d1a588be80e4ec141b6d3a10bb2` at run start.
- MASTER_BASE_SHA `8a0c5360e25e875e52800d17797a7b1ea4bd452c` (main HEAD `fix: preserve manual expert types and filter layout`) is an ancestor of branch HEAD; branch `fast/mailbox-materials` created at that commit in a dedicated worktree. Production code tree at master base is identical to the planning session's audited tree (no src/ working-tree changes on main).
- Child order and dependencies per master plan 实现方案 table (strictly serial; master: "前一子计划未验证不执行后一项"): 01 none; 02 01; 03 02; 04 03; 05 04; 06 05; 07 06; 08 07; 09 08; 10 09; 11 10. Serial chain is a conservative record of the enforced order; rationale lives here, not in dependency fields.
- Baseline commands run at seed commit `a61ecb5` (tree = MASTER_BASE_SHA + seeded docs/assets) on 2026-09-07T16:49Z:
  - `node --test src/test/js/*.test.js` exit 0 (fail 0, skipped 0).
  - `node --check src/main/resources/static/app.js` OK. `expert-materials.js` / `mailbox-chat.js` ABSENT at baseline (created by children 08/09/10; `node --check` on them runs from their children onward).
  - `mvn test` (JAVA_HOME zulu-11) and Flyway IT `mvn -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true test`: running at baseline; results appended when the background job settles.
  - Docker available (server API 1.54), testcontainers mysql:8.0.36 image family used by existing migrationIt-gated ITs.
  - MySQL at 127.0.0.1:3306 CLOSED at baseline: no existing mysqlIt-gated tests in the tree; children that create them (master commands `-Pmysql-it -Dtest=MailboxConversationRepositoryIT`, `ImapMetadataFetchIT,AttachmentTransferWorkerIT`) define provisioning per their plans.
- NOTE: `mvn test` output contains NO reliable `node --test` exec record precedent; standalone `node --test` is the JS authority gate for the run (2026-09-02-execution-order precedent).
- NOTE: master plan 持久化契约 declares migration order V118→V121 and "现有最高V117已核对"; if a child finds new migrations occupied the numbers, the child pauses per plan text (plan revision first, then amendment row).

## Children

| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 01 | docs/plans/2026-09-07/01-attachment-storage-compat.md | commit:a61ecb5aae0a3d1a588be80e4ec141b6d3a10bb2 | none | 1 | LIGHT_PASS_WITH_NOTES | 8a0c5360e25e875e52800d17797a7b1ea4bd452c | 8779d71f00567625ebebe206801994aefcc5725b | 0 | — | 8779d71f00567625ebebe206801994aefcc5725b | b8b6ae56db91c5c1f93c12148bf9fc99ea9c664d | O-1..O-4 (verify-log) |
| 02 | docs/plans/2026-09-07/02-attachment-transfer-core.md | commit:a61ecb5aae0a3d1a588be80e4ec141b6d3a10bb2 | 01 | 1 | LIGHT_PASS | 8779d71f00567625ebebe206801994aefcc5725b | 7c2420e80f98775c1fcf4970598617faca378c0b | 0 | — | 7c2420e80f98775c1fcf4970598617faca378c0b | dd236c587848ea45701ab1fea10debb1d1aeeb60 | O-1..O-2 (verify-log); implementer BLOCKED round resolved in-tree before any commit (no fix_round) |
| 03 | docs/plans/2026-09-07/03-mime-metadata-reader.md | commit:a61ecb5aae0a3d1a588be80e4ec141b6d3a10bb2 | 02 | 1 | LIGHT_PASS_WITH_NOTES | 7c2420e80f98775c1fcf4970598617faca378c0b | 2b8c3d65a3f72e4ddfb3e3be6e7bd0744064fb09 | 1 | 95d8661ed3d9e0557fe69cce6f6dde24dbea5e79 | 95d8661ed3d9e0557fe69cce6f6dde24dbea5e79 | e0db1ea5e67d285a6688430ec670a5a34ab22aba | O-1..O-4 (verify-log); fix round 1 fixture protocol repair |
| 04 | docs/plans/2026-09-07/04-inbound-metadata-persistence.md | commit:a61ecb5aae0a3d1a588be80e4ec141b6d3a10bb2 | 03 | 1 | LIGHT_PASS | 95d8661ed3d9e0557fe69cce6f6dde24dbea5e79 | 5f5623dc4df04b701aa37e0904ec07579d6ba8cb | 1 | c9f80906c872b21d0f4887c7c3f4c95e961b1cef | c9f80906c872b21d0f4887c7c3f4c95e961b1cef | 64efdadebdfed40bc5a6b47e77150e89c139e1f4 | A-1 CLOSED round 1 (verify-log r3) |
| 05 | docs/plans/2026-09-07/05-reply-check-progress-and-machine-mail.md | commit:a61ecb5aae0a3d1a588be80e4ec141b6d3a10bb2 | 04 | 1 | LIGHT_PASS_WITH_NOTES | c9f80906c872b21d0f4887c7c3f4c95e961b1cef | 21dad8bf0573c21eec52cd9783967ae97222ce3d | 0 | — | 21dad8bf0573c21eec52cd9783967ae97222ce3d | 8713ed11f948d5372eb6517834656e20aaf16843 | O-1..O-4 (verify-log) |
| 06 | docs/plans/2026-09-07/06-shared-material-api.md | commit:a61ecb5aae0a3d1a588be80e4ec141b6d3a10bb2 | 05 | 1 | LIGHT_PASS_WITH_NOTES | 21dad8bf0573c21eec52cd9783967ae97222ce3d | fb913c58f375f51eb6284d8c3b449134258811f4 | 0 | — | fb913c58f375f51eb6284d8c3b449134258811f4 | a2521a7bce7325e05a2950c2ce7cb8a0f096127b | O-1..O-4 (verify-log); bean-name/in-memory deviations verified in-scope |
| 07 | docs/plans/2026-09-07/07-expert-conversations-follow.md | commit:a61ecb5aae0a3d1a588be80e4ec141b6d3a10bb2 | 06 | 1 | PAUSED_FOR_HUMAN | fb913c58f375f51eb6284d8c3b449134258811f4 | e0fa706bc9ef3055a42fa4cd13e77ab19858db36 | 0 | — | e0fa706bc9ef3055a42fa4cd13e77ab19858db36 | — | impl READY_FOR_VERIFICATION; verification blocked by cross-child boot blocker (see Pause reason) |
| 08 | docs/plans/2026-09-07/08-shared-materials-frontend.md | commit:a61ecb5aae0a3d1a588be80e4ec141b6d3a10bb2 | 07 | 1 | PENDING | — | — | 0 | — | — | — | |
| 09 | docs/plans/2026-09-07/09-material-analysis-selection.md | commit:a61ecb5aae0a3d1a588be80e4ec141b6d3a10bb2 | 08 | 1 | PENDING | — | — | 0 | — | — | — | |
| 10 | docs/plans/2026-09-07/10-mailbox-chat-frontend.md | commit:a61ecb5aae0a3d1a588be80e4ec141b6d3a10bb2 | 09 | 1 | PENDING | — | — | 0 | — | — | — | |
| 11 | docs/plans/2026-09-07/11-release-and-cache-gate.md | commit:a61ecb5aae0a3d1a588be80e4ec141b6d3a10bb2 | 10 | 1 | PENDING | — | — | 0 | — | — | — | |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
