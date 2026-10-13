# Fast-P Ledger — master: docs/plans/2026-10-13/discovery-identity-recovery-master.md

- Status: PAUSED_FOR_HUMAN
- Master plan: docs/plans/2026-10-13/discovery-identity-recovery-master.md (commit 36075ec06c5e4382f13606c995e24a13ec4470ce)
- Amendments: A1, A2, A3, A4, A5, A6, A7, A8, A9
- Master base: 9d353934b82ed3e36a193c70748e5efccb3d9e82
- Branch: fast/2026-10-13-discovery-identity-recovery-master
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-10-13T21:55:30+0800
- Current child: 02-replay
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: 02-replay epoch1（zero-commit）PLAN_CONFLICT：计划 §I-4 要求精确 DOI/PMID 读取声明 RequestKind.DISCOVERY + Operation.SINGLETON，但 OpenAlexRequestPolicy.deriveOperation 对 /works/* 一律派生 LIST(1 credit)、SINGLETON(0) 仅覆盖 /authors/{id}，validateOperationTarget 会因此抛异常；按字面实现需改第 11 个文件且与「不改计价/额度策略」矛盾；待人工批准 A10（A=授权 policy 文件加 /works/{id}→SINGLETON；B=声明既有派生 LIST 并记录偏差）
- Resume from: bc84bac009177bc3911f00a8041ca2eb48f347b1（02-replay epoch 2 起点；批准 A10 后重派实施者）

## Children
| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---:|---|---|---|---:|---|---|---|---|
| admission-relaxation | docs/plans/2026-10-13/discovery-admission-relaxation.md | commit:f7c7604c268aad69df6afd15efe0eed91866a237 | none | 2 | LIGHT_PASS_WITH_NOTES | b06a71898bc7850d2caeb244c9f8478f18a3b2ae | d8ca3ca28fc94c26ec796a04c6ee332c1e760533 | 0 | — | d8ca3ca28fc94c26ec796a04c6ee332c1e760533 | 2855a757a879016f10625162384e6d614981b7b1 | epoch1 10 文件（cf08a695）→ PLAN_CONFLICT 暂停 → A9 修订 → epoch2 补改（d8ca3ca，1 文件）；C1 149、C2 211、JS 1657×2 全绿；核实者 VerifyAdmission 四门禁 PASS，RECORD_ONLY O-1（用例名沿旧）、O-2（apply 冲突计入 taskFailure 系 KDoc 明示口径）；写者 ImplAdmission/ImplAdmission2 |
| 01-duplicate | docs/plans/2026-10-13/discovery-identity-recovery-01-duplicate.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | admission-relaxation | 1 | LIGHT_PASS_WITH_NOTES | d8ca3ca28fc94c26ec796a04c6ee332c1e760533 | bc294d2e991e58346ce456f4e5eeb7525a2b5771 | 0 | — | bc294d2e991e58346ce456f4e5eeb7525a2b5771 | 41f3b94430958866662b8556a1fc293b3f7fa998 | 8 授权文件（1039+/12-）；C1 294、C2 48、JS 1657×2 全绿；核实者 Verify01-2 四门禁 PASS，RECORD_ONLY O-1（C2 计数差异系前序子计划，非本边界）；写者 Impl01-2 |
| 02-replay | docs/plans/2026-10-13/discovery-identity-recovery-02-replay.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | admission-relaxation,01-duplicate | 1 | PAUSED_FOR_HUMAN | bc294d2e991e58346ce456f4e5eeb7525a2b5771 | — | 0 | — | — | — | epoch1 zero-commit PLAN_CONFLICT：I-4 SINGLETON 与 OpenAlexRequestPolicy /works→LIST 派生冲突（改第 11 文件或声明 LIST 偏差），待 A10 人工裁决；工作树原样保留 |
| 04-registry | docs/plans/2026-10-13/discovery-person-04-registry.md | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | none | — | PENDING | — | — | — | — | — | — | A2 新增；人物主档 V156；6 授权文件；开发顺序在 01/02 之后 |
| 05-ingestion | docs/plans/2026-10-13/discovery-person-05-ingestion.md | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 01-duplicate,02-replay,04-registry | — | PENDING | — | — | — | — | — | — | A3 新增；补证接口基线=01/02 已验证产物；8 授权文件 |
| 06-query | docs/plans/2026-10-13/discovery-person-06-query.md | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 04-registry,05-ingestion | — | PENDING | — | — | — | — | — | — | A4 新增；与 07 API/DOM 联合验收；8 授权文件 |
| 07-ui | docs/plans/2026-10-13/discovery-person-07-ui.md | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 06-query | — | PENDING | — | — | — | — | — | — | A5 新增；2 授权文件；样式契约 S-1/S-2 |
| 08-selection | docs/plans/2026-10-13/discovery-person-08-selection.md | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 04-registry,05-ingestion,06-query | — | PENDING | — | — | — | — | — | — | A6 新增；09 完成前不得开启人物首发；10 授权文件 |
| 09-send-guard | docs/plans/2026-10-13/discovery-person-09-send-guard.md | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 04-registry,08-selection | — | PENDING | — | — | — | — | — | — | A7 新增；V157 人物级首发保护；10 授权文件 |
| 10-history | docs/plans/2026-10-13/discovery-person-10-history.md | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 04-registry,05-ingestion,06-query,07-ui,08-selection,09-send-guard | — | PENDING | — | — | — | — | — | — | A8 新增；仅预览与提案，数据应用属后续实施授权；6 授权文件 |

## Amendments
| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-10-13/discovery-identity-recovery-master.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 需求描述（用户追加同人多邮箱合并）＋实现方案「当前追加：新发现人物合并（04—10）」 | 人工更新加入同人多邮箱合并范围：追加 04—10 子计划、10,686 名单降级为参考并停用原 03 存量执行，开发顺序改为先验收准入修正及 01/02 补证接口代码再 04—10 | HUMAN:用户消息「计划已经更新 你再看一下」（master 于 2026-10-13T22:40:55+0800 落盘，本 run 22:43+0800 快照提交） |
| A2 | docs/plans/2026-10-13/discovery-person-04-registry.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「04 人物主档」＋实现方案 1 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A3 | docs/plans/2026-10-13/discovery-person-05-ingestion.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「05 发现入口」＋实现方案 1—4 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A4 | docs/plans/2026-10-13/discovery-person-06-query.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「06 人物查询」＋实现方案（研究门禁：流式完整匹配集） | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A5 | docs/plans/2026-10-13/discovery-person-07-ui.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「07 多邮箱展示」＋样式契约 S-1/S-2 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A6 | docs/plans/2026-10-13/discovery-person-08-selection.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「08 发送目标」＋实现方案 1—3 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A7 | docs/plans/2026-10-13/discovery-person-09-send-guard.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「09 首发保护」＋实现方案 1—4 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A8 | docs/plans/2026-10-13/discovery-person-10-history.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「10 历史合并」＋实现方案 1—6 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A9 | docs/plans/2026-10-13/discovery-admission-relaxation.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:f7c7604c268aad69df6afd15efe0eed91866a237 | 实现方案步骤1（先完成既有准入修正：规则/CAS/重验）＋子计划变更文件清单第 11 文件条款 | epoch1 PLAN_CONFLICT：I-1 把六类软原因移入 hints 后，ExpertRevalidationServiceBehaviorTest 2 例仍断言旧阻断语义；按子计划「改第 11 个文件须先修订方案」，经人工批准增列该测试类并把 2 例改为新语义 | HUMAN:2026-10-13T23:06+0800 ask 回复「批准：更新 2 例为新语义」 |

## Baseline
- 授权：用户显式 `/fast-p docs/plans/2026-10-13/discovery-identity-recovery-master.md`（2026-10-13），并经 22:40 人工更新该 master（A1—A8）；执行合同 = 更新后 master 与其十份在册子计划（admission-relaxation、01-duplicate、02-replay、04-registry、05-ingestion、06-query、07-ui、08-selection、09-send-guard、10-history）。
- 原 03-fallback 未列入更新后开发顺序，且其收益门槛依赖未获授权的旧 10,686 名单执行，故不纳入本次子计划清单（设计文件保留在仓库）。
- 存量边界：10,686 旧名单仅为参考背景；01/02 仅实现补证接口代码，不执行旧名单处理；回源能力仅可服务本次 discovered 范围；无生产迁移、无 SMTP、无发送配置变更。
- 主工作树含用户其他未提交改动，均未纳入本 run；本 run 自 9d353934 检出，计划种子 b06a71898b，人工更新快照 36075ec06c（docs/plans-only 提交）。
- 基线命令：16 个既有测试类 766 tests 0F/0E；JS 门禁 1657/1657（详见 baseline.md）。
