# Fast-P Ledger — master: docs/plans/2026-09-28/discovered-institution-repair-00-master.md

- Status: RUNNING
- Master plan: docs/plans/2026-09-28/discovered-institution-repair-00-master.md (commit 2e9639df7947bc5f3057ca08e1445b155aba7cd7)
- Amendments: A1
- Master base: d90084841d400e75eb0f2b6c4c6726e54307260a
- Branch: fast/2026-09-28-discovered-institution-repair-00-master
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-09-28T15:05:00+08:00
- Current child: 02
- Waiting role: IMPLEMENTER
- Agent attempt: 0
- Last agent error: N/A (01b attempt-1 provider failure recorded in the 01b row)
- Pause reason: child 02 epoch 1 PLAN_CONFLICT — ExpertIndexServiceTest.kt pinned the RAW mapping property count (36→37) but was not in the child plan's authorized files; resolved in place by A1 with human approval 2026-09-28T17:19+08:00, no work discarded
- Resume from: a90f59d8d57dea33d83b571bbb62c5f389d387d7
- Baseline: children/01/baseline.md — seed 提交上 `mvn clean package` exit 0；surefire 4220 tests / 0 failures / 0 errors / 13 skipped；Node 1202 pass

## Children
| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---:|---|---|---|---:|---|---|---|---|
| 01 | docs/plans/2026-09-28/discovered-institution-repair-01-source.md | commit:2e9639df7947bc5f3057ca08e1445b155aba7cd7 | none | 1 | LIGHT_PASS_WITH_NOTES | 2e9639df7947bc5f3057ca08e1445b155aba7cd7 | b12c971be46a992b275a3e4fb3768047b0af8877 | 0 | — | b12c971be46a992b275a3e4fb3768047b0af8877 | c4b2c6a64b45ca63d16434437b17b752048511b7 | Implementer Implement01-2; verifier Verify01-2 LIGHT_PASS_WITH_NOTES / COMPLETE_CHILD; 2 RECORD_ONLY (stale "无条件覆盖" comment at ExpertDiscoveryService.kt:3008; institutionTypePending backlog); 0 fix rounds |
| 01b | docs/plans/2026-09-28/discovered-institution-repair-01-orcid.md | commit:2e9639df7947bc5f3057ca08e1445b155aba7cd7 | 01 | 1 | LIGHT_PASS_WITH_NOTES | b12c971be46a992b275a3e4fb3768047b0af8877 | 68ad011971ac4cbafdd439cfe2d981476ff1332a | 0 | — | 68ad011971ac4cbafdd439cfe2d981476ff1332a | f5bff239bd4c1c2ff939a29cff0d682d78f7a601 | Agent availability: IMPLEMENTER attempt 1 (Implement01b) provider crash, code head unchanged, partial test-only work retained, action RETRY; implementer Implement01bRetry; verifier Verify01b LIGHT_PASS_WITH_NOTES / COMPLETE_CHILD; 2 RECORD_ONLY; 0 fix rounds |
| 02 | docs/plans/2026-09-28/discovered-institution-repair-02-evidence.md | commit:a90f59d8d57dea33d83b571bbb62c5f389d387d7 | 01b | 2 | IMPLEMENTING | 68ad011971ac4cbafdd439cfe2d981476ff1332a | — | 0 | — | — | — | Epoch 1 PLAN_CONFLICT (no commit; all nine files retained in the working tree); A1 authorized ExpertIndexServiceTest.kt count expectation 36→37; implementer Implement02 resumes in epoch 2 with fix_round=0 |
| 03 | docs/plans/2026-09-28/discovered-institution-repair-03-outreach.md | commit:2e9639df7947bc5f3057ca08e1445b155aba7cd7 | 02 | 1 | PENDING | — | — | 0 | — | — | — | N/A |

## Amendments
| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-09-28/discovered-institution-repair-02-evidence.md | commit:2e9639df7947bc5f3057ca08e1445b155aba7cd7 | commit:a90f59d8d57dea33d83b571bbb62c5f389d387d7 | I-3（新字段分层一致）+ 实现方案 1 | 子计划强制新增 institutionEvidence 使 RAW 顶层 mapping 属性计数 36→37，而 ExpertIndexServiceTest.kt:170 以计数断言钉住该形状且不在授权清单内，必需命令无法在不越权的情况下完成 | HUMAN:批准最小修正（推荐）— 将 ExpertIndexServiceTest.kt 加入 02 授权文件并只更新计数期望 36→37 与紧邻注释，断言与语义不变 (recorded 2026-09-28T17:19:33+08:00) |
