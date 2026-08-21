# 2026-08-21 UI 小改动：执行顺序（权威）

来源：运营提出的 5 项小改动，汇总后按「可独立部署 + 变更文件 ≤10 + 子系统 ≤2」拆成 3 份计划。

| 序 | 计划 | 覆盖的运营诉求 | 文件数 | 子系统 |
|---|---|---|---|---|
| 1 | `ui-tweaks-01-check-replies-move-and-auto-preview-removal.md` | ①「检查回复」移到收发件箱、贴着「批量发送」<br>②「处理与回复」里的「自动回复预览」删除 | 8 | 1（静态前端入口） |
| 2 | `ui-tweaks-02-overlay-and-dialog-contrast.md` | ③ 可信回复工作台操作遮罩补全<br>⑤ 确认框样式（透明+灰字）修复 | 5 | 1（浮层视觉） |
| 3 | `ui-tweaks-03-manual-reply-subject-prefill.md` | ④ 人工富文本回复自动填入回复主题 | 4 | 1（来信详情人工回复） |
| 4 | `qa-gate-visibility.md` | ⑥ QA 事实编辑框看不到门禁绑定、报错为英文内部术语；规则 24 不可保存 | 10 | 2（后端 QA 服务+迁移 / 前端静态资源） |

> 第 4 份于 2026-08-21 晚些时候追加，与前三份同样只在缓存键三键上串行；
> 它另改后端 `QaCoverageKeyCatalog` / `QaRuleManagementController` / `QaRuleManagementService`
> 与新迁移 `V107`，与前三份零交集。

## 为什么必须按序

三份计划都要 bump `index.html` 的**缓存键三键**（`styles.css?v=` / `trust-reply-workbench.js?v=` / `app.js?v=`），
并同步 `src/test/js/batchSendTaskConsoleVisualFix.test.js:49-51` 的三条逐字断言（K-frontend-cache-key-triad）。
三键值互相覆盖，**并行执行必冲突**。约定值：

- P1 → `20260821-v9-check-replies-move`
- P2 → `20260821-v10-overlay-contrast`
- P3 → `20260821-v11-reply-subject-prefill`
- P4 → `20260821-v12-qa-coverage-gate`（`qa-gate-visibility.md`，2026-08-21 追加，见下）

除缓存键外四份计划**无代码耦合**，任一份可单独部署与验收；若只做其中一份，把该份的缓存键改成 `20260821-v9-<本份 slug>` 即可。

## 三键断言测试的跨计划同步规则（A3，2026-08-21 追加）

本 run 中每个子计划 bump 三键时，除同步 `src/test/js/batchSendTaskConsoleVisualFix.test.js:49-51` 外，还必须把**本 run 此前子计划新增的三键断言测试**中的硬编码键值同步为本子计划的键值：

- 可用文件（按届时已存在者计）：`src/test/js/checkRepliesRelocation.test.js`（P1 新增，I-3）、`src/test/js/overlayAndDialogContrast.test.js`（P2 新增，I-8）、`src/test/js/manualReplySubjectPrefill.test.js`（P3 新增，I-5）。
- 这些文件计入该子计划的授权文件清单与文件上限（≤10）。
- 同步内容仅限其中的缓存键字面量与本 run 键值一致；其余断言逐字不动。

来源：K-frontend-cache-key-triad（三键断言点随本 run 扩展；P1 新增 `checkRepliesRelocation.test.js` 起，键值断言点不再只有 `batchSendTaskConsoleVisualFix.test.js` 一处）。

## 基线

- 分支 `main`，工作区仅有 `docs/releases.json` 的既有改动与未跟踪文件，无未提交的源码改动。

## 已明确不做（三份计划共同的 Out of scope）

1. **不删** `trust-reply-workbench.js` 的 `AUTO_PREVIEW` 模式与 `readOnly` 分支（`MODES`/`MODE_SOURCE`/`renderReadOnlyZone`/`requestJson` 写闸门/`autoRunBar` 抑制，共 11 处）。P1 只删宿主入口，模式代码保留为无调用方的死模式。理由：删模式要连带重写 `trustReplyWorkbenchSharedMount.test.js:420`「rejects invalid mode and source combinations」，与本轮 5 项运营诉求无关，且会把 P1 顶破文件上限。→ 建议另立清理计划。
2. **不删**后端 `AutoReplyPreviewService` 与 `GET /api/mail/unmatched-inbound/{id}/auto-reply-preview`（`UnmatchedInboundMailController.kt:259`）。它是 K-preview-mirrors-pipeline 记录的 dry-run 同源链路，前端入口删掉不等于链路作废。
3. **不给** `mailbox` 视图补 `resumeProgressPollingIfNeeded()`。P1 的现状审计已确认：`.view` section 常驻 DOM，`$("#checkRepliesBtn")` 跨视图仍可取到，运行态恢复不受按钮位置影响（详见 P1 的 I-2）。
4. **不改**「人工富文本回复」折叠头那枚写死的 `未填写` 状态标签（`app.js:9952`），它本来就从不更新。
