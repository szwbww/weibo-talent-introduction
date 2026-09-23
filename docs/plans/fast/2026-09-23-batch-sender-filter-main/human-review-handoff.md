# Fast-P Human Review Handoff

- Outcome: READY_FOR_HUMAN_REVIEW
- Master base: 9237d6f573335d1624217cbc5501f68a6f52b97b
- Current/final code head: 75cc1714611ac085341cf372d28c057bb332796d
- Branch/worktree: fast/2026-09-23-batch-sender-filter-main / /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-batch-sender-filter-main

## Child Status
| Child | Status | Code boundary | Fix rounds | Evidence commit |
|---|---|---|---|---|
| c1 | LIGHT_PASS_WITH_NOTES | 377a38b91ffd8a0a78815f5ad3041dbc55b2db80..248c30a | 1 | 47505d3f38d91570d639222d652f3cf1a145e5ba |
| c2 | LIGHT_PASS_WITH_NOTES | 248c30a..75cc1714611ac085341cf372d28c057bb332796d | 0 | 504b5b9f9711a5bda701d2e0d90ed2b1fe5c164c |

## RECORD_ONLY Index
| Observation | Child | Evidence | Source report |
|---|---|---|---|
| O-1 两个历史 Flyway 期望在 A5 授权范围外：V124 `clean()` 后插入 `expert_application_promotion(expert_contact_id=1)` 但无迁移 seed 该行（FK `fk_eap_contact`）；V131 `historyBefore + 1` 对 V130→latest 升级已因 V132/V133 失真（现为 4）。基线时被版本断言掩盖 | c1 | `children/c1/verify-log.md` O-1；`baseline/mvn.txt` | children/c1/verify-log.md |
| O-2 I-3 构造侧用 `!isNullOrBlank()`、发送前守卫用 `!= null`：空白字符串绑定值会被预估计入而在发送时跳过（V85 禁止空白绑定，脏数据在计划范围外） | c1 | `ManualInitialOutreachService` 构造/发送守卫 | children/c1/verify-log.md |
| O-3 legacy KV 启动路径（`launchLegacyKv`→`toLegacySnapshot`、`ManualInitialOutreachService.toSnapshot` 走 `batch_send_setting`）不携带白名单，旧 typed API 写入的白名单不约束 KV 驱动启动 | c1 | `BatchSendControlService`/`BatchSendSettingService` | children/c1/verify-log.md |
| O-4 `BatchSendTaskConfigService` 新增可选 `mailSenderAccountService = null` 在 null 时跳过 code 存在性校验（生产由 Spring 注入，形状对未来非 Spring 装配 fail-open） | c1 | `BatchSendTaskConfigService` 构造与校验分支 | children/c1/verify-log.md |
| O-1 `notifyBatchMultiPickerChanged` 历史上对所有 `previewKind:"manual"` picker 写死 `manualDraft.emailDomains`；c2 改为 `meta.draftKey \|\| "emailDomains"`，6 个既有 picker 行为逐字不变，历史误键保持原样 | c2 | `app.js` 该函数 diff | children/c2/verify-log.md |
| O-2 `readBatchMultiPickerValue` 自身不 `distinct()`：有序去重靠 toggle UI 构造 + 后端 `.trim().filter{}.distinct()`，未发现可达重复路径 | c2 | `app.js` + c1 后端校验 | children/c2/verify-log.md |
| O-3 字面边界 `248c30a..75cc1714` 含 8 个 docs-only 证据文件（由编排提交 `47505d3` 引入，非 c2 实现提交） | c2 | `git diff --name-status` | children/c2/verify-log.md |

## Pause/Resume
- Reason: N/A
- Resume from: N/A

## Notes (recorded facts only)

- 计划改写 A1–A6 均在 `## Amendments` 有行：A1–A3 = 放弃「共享收件箱先实施」门槛并让本组改用当时下一个空号；A4–A6 = 发现并行 run 已占用 V134 后本组让号到 V135、不 rebase 到并行分支。
- 遗留合入事项（人工）：并行分支 `fast/2026-09-23-shared-inbox-master`（V134 + owner UI）与本分支共享 `index.html`、`app.js`、`FlywayMigrationIntegrationTest.kt`，合入时需按「方法区分开改、不整文件覆盖」解冲突；合入顺序应为并行分支在前（V134→V135）。
- 基线复现用的辅助 detached worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-baseline-batch-sender-filter`（detached at `377a38b`），仅用于跑基线命令，审阅后可删除。

No whole-system verification was performed.
