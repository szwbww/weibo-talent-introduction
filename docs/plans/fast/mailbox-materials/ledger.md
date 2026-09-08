# Fast-P Ledger — master: docs/plans/2026-09-07/00-mailbox-materials-master.md

- Status: READY_FOR_HUMAN_REVIEW
- Master plan: docs/plans/2026-09-07/00-mailbox-materials-master.md (commit a61ecb543668532317bebd7864286352dc3359c7)
- Amendments: A1, A2
- Master base: 8a0c5360e25e875e52800d17797a7b1ea4bd452c
- Branch: fast/mailbox-materials
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-09-07T16:49:36Z
- Current child: N/A
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline

- Master plan and all 11 child plans (docs/plans/2026-09-07/01..11), the audit/evidence/style/baseline docs, and the referenced preview assets (artifacts/mailbox-chat-preview/) were untracked on main at run start; seeded on the branch as docs-only commit `a61ecb543668532317bebd7864286352dc3359c7`, which is not an amendment. Master and child plan identities at run start = that seed commit; child 07 and child 11 identities were later amended (rows below).
- MASTER_BASE_SHA `8a0c5360e25e875e52800d17797a7b1ea4bd452c` (main HEAD `fix: preserve manual expert types and filter layout`) is an ancestor of branch HEAD; branch `fast/mailbox-materials` created at that commit in a dedicated worktree. Production code tree at master base is identical to the planning session's audited tree.
- Child order and dependencies per master plan 实现方案 table (strictly serial; master: "前一子计划未验证不执行后一项"): 01 none; 02 01; 03 02; 04 03; 05 04; 06 05; 07 06; 08 07; 09 08; 10 09; 11 10.
- Baseline commands run at seed commit `a61ecb543668532317bebd7864286352dc3359c7` on 2026-09-07T16:49Z: `node --test src/test/js/*.test.js` exit 0; `node --check app.js` OK (expert-materials.js/mailbox-chat.js absent until children 08/10); `mvn test` exit 0; FlywayMigrationIntegrationTest bare run env-blocked (docker-java client API 1.32 vs OrbStack daemon min API 1.40 -> "Docker is required"; reproduced identically by children 01/02/04; known-good invocation in this environment: DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock + -Dapi.version=1.40). MySQL at 127.0.0.1:3306 was closed at baseline; controller provisioned mysql:8.0.36 (root/root, database talent_introduction) for the master-mandated -Pmysql-it gates (MailboxConversationRepositoryIT 07, ImapMetadataFetchIT 03/05, AttachmentTransferWorkerIT 02) — provisioned before child 02, still running for human acceptance.
- FINALIZATION REBUILD (2026-09-08, controller-executed, HUMAN-APPROVED 2026-09-08, documented for human review): the finalization validator found evidence-format defects embedded in historical docs commits (03/04 fix-log recorded short SHA / trailing annotation; 07 evidence commit lacked a fix-log.md change because the placeholder had entered via the pause commit). Per HUMAN approval the controller rebuilt the docs-only evidence chain via filter-branch in scratch clones (two passes: pass 1 rewrote from the 03-evidence commit forward normalizing the 03/04 fix-log SHA lines and adding the 07 annotated placeholder at its evidence commit; pass 2 rewrote from the 04-evidence commit forward rebinding the 04 fix-log line to the pass-1 fix-commit SHA). Product trees are byte-identical (verified old-vs-new tree diffs restricted to docs/plans/fast/**); every rewritten commit received a new SHA and the ledger/handoff were remapped old->new accordingly. An earlier `git rebase -i` sequence-editor attempt was a no-op (editor never paused) and was superseded by the filter-branch rebuild.
- Interleaved docs commits precede later implementations in ancestry without advancing product bases.
- Human acceptance A-1..A-8 and browser/viewport/IMAP-protocol checks are NOT performed by this workflow; see human-review-handoff.md.
- NOTE: `mvn test` output contains NO reliable `node --test` exec record precedent; standalone `node --test` is the JS authority gate (2026-09-02-execution-order precedent). Full-suite fresh counts per child recorded in each child's execution.md.

## Children

| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 01 | docs/plans/2026-09-07/01-attachment-storage-compat.md | commit:a61ecb543668532317bebd7864286352dc3359c7 | none | 1 | LIGHT_PASS_WITH_NOTES | 8a0c5360e25e875e52800d17797a7b1ea4bd452c | 8779d71f00567625ebebe206801994aefcc5725b | 0 | — | 8779d71f00567625ebebe206801994aefcc5725b | b8b6ae56db91c5c1f93c12148bf9fc99ea9c664d | V118 nullable compat; O-1..O-4 (verify-log) |
| 02 | docs/plans/2026-09-07/02-attachment-transfer-core.md | commit:a61ecb543668532317bebd7864286352dc3359c7 | 01 | 1 | LIGHT_PASS | 8779d71f00567625ebebe206801994aefcc5725b | 7c2420e80f98775c1fcf4970598617faca378c0b | 0 | — | 7c2420e80f98775c1fcf4970598617faca378c0b | dd236c587848ea45701ab1fea10debb1d1aeeb60 | V119 transfer table+worker+fetcher; implementer BLOCKED round resolved in-tree pre-commit; O-1..O-2 (verify-log) |
| 03 | docs/plans/2026-09-07/03-mime-metadata-reader.md | commit:a61ecb543668532317bebd7864286352dc3359c7 | 02 | 1 | LIGHT_PASS_WITH_NOTES | 7c2420e80f98775c1fcf4970598617faca378c0b | 2b8c3d65a3f72e4ddfb3e3be6e7bd0744064fb09 | 1 | 95d8661ed3d9e0557fe69cce6f6dde24dbea5e79 | 95d8661ed3d9e0557fe69cce6f6dde24dbea5e79 | c10f3b4914a01e2e66a3e0054f9fa0335132090e | metadata fetch mode + zero-content-FETCH IT; fix round 1 = IT fixture protocol repair; O-1..O-4 (verify-log) |
| 04 | docs/plans/2026-09-07/04-inbound-metadata-persistence.md | commit:a61ecb543668532317bebd7864286352dc3359c7 | 03 | 1 | LIGHT_PASS | 95d8661ed3d9e0557fe69cce6f6dde24dbea5e79 | 640ec8c5c52810c6357630e68d2a5eb9177bd599 | 1 | 8ba24989302ed4a4e6d0f58a70cb5e19a2ed7cd2 | 8ba24989302ed4a4e6d0f58a70cb5e19a2ed7cd2 | 7b051f433648ff1a63266fa5caa304643113073f | V120 uid_validity + metadata registration/bridge; A-1 (bridge broke legacy content-mode confirm) fixed round 1, CLOSED by re-verification |
| 05 | docs/plans/2026-09-07/05-reply-check-progress-and-machine-mail.md | commit:a61ecb543668532317bebd7864286352dc3359c7 | 04 | 1 | LIGHT_PASS_WITH_NOTES | 8ba24989302ed4a4e6d0f58a70cb5e19a2ed7cd2 | 8766c1f5dda3ffe5fd3b0228e635f69ea20ee7b2 | 0 | — | 8766c1f5dda3ffe5fd3b0228e635f69ea20ee7b2 | 00da92e38b3a7df3cb20cf7f24335cd14b2fb526 | account progress + DMARC SYSTEM queue/consumer; O-1..O-4 (verify-log) |
| 06 | docs/plans/2026-09-07/06-shared-material-api.md | commit:a61ecb543668532317bebd7864286352dc3359c7 | 05 | 1 | LIGHT_PASS_WITH_NOTES | 8766c1f5dda3ffe5fd3b0228e635f69ea20ee7b2 | b36bcb49d04f4651c4b01fc524f40fc322678806 | 0 | — | b36bcb49d04f4651c4b01fc524f40fc322678806 | ec0951cb9d77b708c7d68c6529823d902fd3229d | shared materials API + readiness unification; bean-name + bounded-projection deviations verified in-scope; O-1..O-4 (verify-log) |
| 07 | docs/plans/2026-09-07/07-expert-conversations-follow.md | commit:6be7a01d76969b6a56536121ae02dcb65f0082ee | 06 | 2 | LIGHT_PASS_WITH_NOTES | b36bcb49d04f4651c4b01fc524f40fc322678806 | ca0611cf04e499a5d0e08ffaea1e20abb635bb66 | 0 | — | ca0611cf04e499a5d0e08ffaea1e20abb635bb66 | bead9c99e6ffbb9d43608433e6405ec0a8c2a948 | conversations+follow + A1 route-retirement (epoch 2); epoch-1 impl 5ae293bdbefe20abbf2edb009ccedc0c8268b801 in ancestry; notes in verify-log |
| 08 | docs/plans/2026-09-07/08-shared-materials-frontend.md | commit:a61ecb543668532317bebd7864286352dc3359c7 | 07 | 1 | LIGHT_PASS_WITH_NOTES | ca0611cf04e499a5d0e08ffaea1e20abb635bb66 | b08d6e4a3f3c86bab6103a40fe1bd9168ba7fabe | 0 | — | b08d6e4a3f3c86bab6103a40fe1bd9168ba7fabe | 15af0e90f63423895167d32766221400554017a6 | ExpertMaterials component + S-1 CSS verbatim; O-1..O-5 (verify-log) |
| 09 | docs/plans/2026-09-07/09-material-analysis-selection.md | commit:a61ecb543668532317bebd7864286352dc3359c7 | 08 | 1 | LIGHT_PASS_WITH_NOTES | b08d6e4a3f3c86bab6103a40fe1bd9168ba7fabe | 782a1ee144615eca1b0531443318c94386187213 | 0 | — | 782a1ee144615eca1b0531443318c94386187213 | 2c95a6f13086ff89f99aa1b4ed0b3e1809f45d28 | AI picker acquire-then-analyze + no-partial-success backend gate; O-1..O-4 (verify-log) |
| 10 | docs/plans/2026-09-07/10-mailbox-chat-frontend.md | commit:a61ecb543668532317bebd7864286352dc3359c7 | 09 | 1 | LIGHT_PASS_WITH_NOTES | 782a1ee144615eca1b0531443318c94386187213 | 8a3e2faaa774ed1ee7cc3e27f5ccfb4fab4526ac | 0 | — | 8a3e2faaa774ed1ee7cc3e27f5ccfb4fab4526ac | f5d758579860a58768ceaa4155438f48daa39c5d | mailbox chat layout + S-3 CSS verbatim; 5/10 listed test files intentionally unchanged (compat-branch justification verified); O-1..O-4 (verify-log; O-1 latent chat-settings selects note for human) |
| 11 | docs/plans/2026-09-07/11-release-and-cache-gate.md | commit:6595cfb166dbab5dc0c865592678154706f81f2c | 10 | 2 | LIGHT_PASS_WITH_NOTES | 8a3e2faaa774ed1ee7cc3e27f5ccfb4fab4526ac | 009d9bab431c75184b00659905f80dcde91e3166 | 0 | — | 009d9bab431c75184b00659905f80dcde91e3166 | 08b01758488934b7b515ed9726143aa09c051de5 | 7-key registration + metadataOnly=true default + rollout runbook; A2 (epoch 2) +1 test file; R1..R3 (verify-log) |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-09-07/07-expert-conversations-follow.md | commit:a61ecb543668532317bebd7864286352dc3359c7 | commit:6be7a01d76969b6a56536121ae02dcb65f0082ee | 00 实现方案尾注（编译证明须改未列文件→先修订子计划）+ audit.md E 表（统一组件改用新材料 API） | child-06 ExpertMaterialController 与既有 campaign GET /api/expert-contacts/{id}/materials 同路径歧义映射使生产无法启动；授权退役旧 feed 映射（07 +1 授权文件，映射回归并入 MailboxConversationControllerTest.kt） | HUMAN:"A: 修订 child 07 计划并授权退役旧路由 (Recommended)" 2026-09-08 |
| A2 | docs/plans/2026-09-07/11-release-and-cache-gate.md | commit:a61ecb543668532317bebd7864286352dc3359c7 | commit:6595cfb166dbab5dc0c865592678154706f81f2c | 总计划 I-2 启用顺序（metadataOnly 默认 true）+ 变更文件清单尾注（编译证明须改未列文件→先修订子计划） | metadataOnly 默认翻转使 child-03 docker-free ImapMailReceiveServiceTest 无参构造的 legacy 断言失败；授权 +1 测试文件一处显式 metadataOnly=false 构造 | HUMAN:"批准 A2 拓宽 (+1 测试文件) (Recommended)" 2026-09-08 |
