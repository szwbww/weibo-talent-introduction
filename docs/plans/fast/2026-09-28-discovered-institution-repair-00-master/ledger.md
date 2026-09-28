# Fast-P Ledger — master: docs/plans/2026-09-28/discovered-institution-repair-00-master.md

- Status: READY_FOR_HUMAN_REVIEW
- Master plan: docs/plans/2026-09-28/discovered-institution-repair-00-master.md (commit 2e9639df7947bc5f3057ca08e1445b155aba7cd7)
- Amendments: A1,A2
- Master base: d90084841d400e75eb0f2b6c4c6726e54307260a
- Branch: fast/2026-09-28-discovered-institution-repair-00-master
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-09-28T15:05:00+08:00
- Current child: N/A
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A (A1 and A2 resolved the two child plan-conflict pauses; see Amendments and each child's fix-log)
- Resume from: N/A
- Baseline: children/01/baseline.md — seed 提交上 `mvn clean package` exit 0；surefire 4220 tests / 0 failures / 0 errors / 13 skipped；Node 1202 pass

## Children
| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---:|---|---|---|---:|---|---|---|---|
| 01 | docs/plans/2026-09-28/discovered-institution-repair-01-source.md | commit:2e9639df7947bc5f3057ca08e1445b155aba7cd7 | none | 1 | LIGHT_PASS_WITH_NOTES | 2e9639df7947bc5f3057ca08e1445b155aba7cd7 | b12c971be46a992b275a3e4fb3768047b0af8877 | 0 | — | b12c971be46a992b275a3e4fb3768047b0af8877 | c4b2c6a64b45ca63d16434437b17b752048511b7 | Implementer Implement01-2; verifier Verify01-2 LIGHT_PASS_WITH_NOTES / COMPLETE_CHILD; RECORD_ONLY (stale "无条件覆盖" comment at ExpertDiscoveryService.kt:3008; institutionTypePending backlog); 0 fix rounds |
| 01b | docs/plans/2026-09-28/discovered-institution-repair-01-orcid.md | commit:2e9639df7947bc5f3057ca08e1445b155aba7cd7 | 01 | 1 | LIGHT_PASS_WITH_NOTES | b12c971be46a992b275a3e4fb3768047b0af8877 | 68ad011971ac4cbafdd439cfe2d981476ff1332a | 0 | — | 68ad011971ac4cbafdd439cfe2d981476ff1332a | f5bff239bd4c1c2ff939a29cff0d682d78f7a601 | Agent availability: IMPLEMENTER attempt 1 (Implement01b) provider crash, code head unchanged, partial test-only work retained, action RETRY; implementer Implement01bRetry; verifier Verify01b LIGHT_PASS_WITH_NOTES / COMPLETE_CHILD; RECORD_ONLY (dead OrcidRecord.country; trailing whitespace in children/01/execution.md:39); 0 fix rounds |
| 02 | docs/plans/2026-09-28/discovered-institution-repair-02-evidence.md | commit:a90f59d8d57dea33d83b571bbb62c5f389d387d7 | 01b | 2 | LIGHT_PASS_WITH_NOTES | 68ad011971ac4cbafdd439cfe2d981476ff1332a | c30c954761b199467c7d904a50b177808f54ddce | 0 | — | c30c954761b199467c7d904a50b177808f54ddce | 89acd6ffdd25f150d709fa122227763c5fc06707 | Epoch 1 PLAN_CONFLICT → A1 (human 2026-09-28T17:19:33+08:00); epoch 2 completed by Implement02; verifier Verify02 LIGHT_PASS_WITH_NOTES / COMPLETE_CHILD; RECORD_ONLY (no positive OPENALEX issuance test; stale fix-log placeholder); 0 fix rounds |
| 03 | docs/plans/2026-09-28/discovered-institution-repair-03-outreach.md | commit:c01c86cdce3cb5747457d364241f83c535c3961d | 02 | 2 | LIGHT_PASS_WITH_NOTES | c30c954761b199467c7d904a50b177808f54ddce | 70e6144065335beee72dbd22a84e4bb975a68928 | 0 | — | 70e6144065335beee72dbd22a84e4bb975a68928 | ddde808143ca00fe84d7761b4adfa880da670eef | Epoch 1 PLAN_CONFLICT → A2 (human 2026-09-28T18:40:47+08:00); epoch 2 completed by Implement03 (11 files; OperatorStatusWriteSeamGuardTest authorized-untouched); verifier Verify03 LIGHT_PASS_WITH_NOTES / COMPLETE_CHILD; 5 RECORD_ONLY; 0 fix rounds |

## Amendments
| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-09-28/discovered-institution-repair-02-evidence.md | commit:2e9639df7947bc5f3057ca08e1445b155aba7cd7 | commit:a90f59d8d57dea33d83b571bbb62c5f389d387d7 | I-3（新字段分层一致）+ 实现方案 1 | 子计划强制新增 institutionEvidence 使 RAW 顶层 mapping 属性计数 36→37，而 ExpertIndexServiceTest.kt:170 以计数断言钉住该形状且不在授权清单内，必需命令无法在不越权的情况下完成 | HUMAN:批准最小修正（推荐）— 将 ExpertIndexServiceTest.kt 加入 02 授权文件并只更新计数期望 36→37 与紧邻注释，断言与语义不变 (recorded 2026-09-28T17:19:33+08:00) |
| A2 | docs/plans/2026-09-28/discovered-institution-repair-03-outreach.md | commit:2e9639df7947bc5f3057ca08e1445b155aba7cd7 | commit:c01c86cdce3cb5747457d364241f83c535c3961d | I-2（身份与机构证据同时成立）+ 03 I-3 预估—执行共用最终筛选 | 子计划强制预估改走 scroll 与执行同源、必然删除旧粗筛计数 seam countEsTargets(RecipientScope)，而 MailOpenTrackingPersistenceTest.kt 与 BatchSendTaskRuntimeIntegrationTest.kt 钉住该旧 seam 且不在授权清单内，必需命令无法在不越权的情况下完成 | HUMAN:批准 A2（推荐）— 将 MailOpenTrackingPersistenceTest.kt 与 BatchSendTaskRuntimeIntegrationTest.kt 加入 03 授权文件并只做测试侧 seam 适配，断言不变 (recorded 2026-09-28T18:40:47+08:00) |
