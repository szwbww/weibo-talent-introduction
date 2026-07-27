# QA 重构 06：可信回复工作台与最终校验 — 修复计划 1

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-17/qa-fact-card-trust-reply-master-plan.md` Phase 6。
- 子计划：`docs/plans/2026-07-17/qa-refactor-06-trust-workbench.md`。
- 本轮无此前修复计划。

## 约束摘录

| 约束 | 要求 |
|---|---|
| I-1 | 工作台只控制事实；不得保留规则正文拼接或人工排序拼接 API。 |
| I-2/I-3 | facts 变化先经服务端 evaluate 取得 canonical 集合；草稿会话不得与当前事实集合漂移。 |
| I-5/I-7 | 携带事实的最终发送必须校验、写 canonical link 和审计；不能因 UI 竞态变成无事实发送。 |
| I-6 | 历史草稿/审计写失败不能单独阻止人工富文本发送。 |
| S-2 | 工作台不显示 rule ID。 |

## 修正记录表

| ID | 优先级 | 发现 | 触发频率 |
|---|---|---|---|
| P1-1 | P1 | 切换事实后 300ms debounce 尚未返回时可立即生成；生成使用旧 `evaluation.canonicalFactIds`，随后 evaluate 可将当前集合改为空。采用时又以空 evaluation 覆盖 locked IDs，导致含事实草稿以 `qaRuleIds=[]` 发送，跳过最终校验、link 和 QA 审计。 | 常规低频：运营快速切换事实后立即点击生成；可稳定复现。 |
| P1-2 | P1 | `sendManualRichReply` 在 SMTP 和 mail_record 后同步写 `operator_action_log`；该调用抛错会使请求失败并回滚本地事务，违背审计失败不得阻止人工发送。 | 低频：日志库/序列化/数据库瞬态失败；会形成已投递但界面报错、记录回滚的重复发送风险。 |
| P1-3 | P1 | `QaReplyComposer.composeInOperatorOrder` 及两条测试仍存在，违反 I-1 明确的生产扫描验收；原计划的文件清单遗漏该实现与测试。 | 休眠路径：当前无调用方；未来重新接入时会恢复人工排序拼接。 |
| P1-4 | P1 | 工作台两个 label fallback 直接渲染 `规则 #ID`，违反 S-2。`displayName`/`sectionTitle` 可为空时会真实显示内部 ID。 | 数据相关：任一可匹配事实缺少两个展示字段时触发。 |

## 修复规格

### P1-1：把 evaluate 响应绑定到事实选择版本

- 文件：`src/main/resources/static/app.js`、`src/test/js/trustReplyWorkbench.test.js`。
- checkbox 变化后立即标记 evaluation pending，并清空草稿/锁定集合；在对应 fact-ID 快照的 evaluate 成功前禁用生成与采用。
- 只允许已确认的 canonical 集合进入生成和采用；evaluate 返回的请求快照与当前 selected IDs 不同则丢弃。不得用旧 evaluation 或空数组覆盖 locked facts。
- 增加回归：初始 `[A]`，取消 A 后立即点击生成；不得发起含 A 的 `/ai-reply/turn`，也不得以空 `qaRuleIds` 采用该草稿。

### P1-2：审计写入降级为非阻断观测

- 文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`；如需事务隔离，可最小增加现有审计服务的安全写入口。
- 在 SMTP、mail_record、canonical link 成功后，以独立事务或 after-commit 的 best-effort 审计写入记录 action；日志失败必须被捕获并记录服务端诊断，不得使 manual-rich 请求失败或回滚 mail_record/link。
- 保持正常路径 action、`canonicalFactIds`、`serverSuggestedFactIds` 和 `mail_record_qa_rule` 语义不变。
- 增加测试：mock 审计写入抛错；发送成功，mail record/link 已写，且不返回异常。

### P1-3：删除已废弃人工排序拼接 API

- 文件：`src/main/kotlin/com/weibo/talentintroduction/qa/service/QaReplyComposer.kt`、`src/test/kotlin/com/weibo/talentintroduction/qa/service/QaReplyComposerTest.kt`。
- 删除 `composeInOperatorOrder` 与仅覆盖它的两条测试；保留自动路径仍使用的 `compose`、`selectPrimary`、frame 常量。
- 已在原子计划 `修正记录` 扩展此二文件的唯一用途；不得顺带重构自动回复 composer。

### P1-4：移除 rule-ID 展示 fallback

- 文件：`src/main/resources/static/app.js`、`src/test/js/trustReplyWorkbench.test.js`。
- 两处事实展示 fallback 改为非内部标识文案（如“未命名事实”）；不改变 checkbox `data-rule-id` 或 API canonical IDs。
- 增加空 `displayName`/`sectionTitle` fixture，断言 DOM 不含规则 ID。

## 当前状态（修复前）

- 编译：PASS — `mvn -q -Dtest=PendingMailOperationServiceTest,UnmatchedInboundTrustWorkbenchTest,AiReplyHighRiskClaimValidatorTest,QaRuleAuditServiceTest test`。
- Kotlin 测试：PASS — 89 passed，0 failed，0 skipped（38 + 6 + 36 + 9）。
- JS 测试：PASS — `node --test src/test/js/trustReplyWorkbench.test.js src/test/js/aiAdoptDraftRouting.test.js`，7 passed，0 failed，0 skipped。
- `git diff --check`：PASS。

## 合规审计

| 约束 | 结论 | 证据 |
|---|---|---|
| I-1 | ❌ P1-3 | `QaReplyComposer.kt:42-61` 仍定义 `composeInOperatorOrder`；`LlmStitchService.kt` 已删除。 |
| I-2 | ❌ P1-1 | `app.js:8418-8419` 只 debounce evaluate；`:9093-9095` 在 pending 期间读取旧 evaluation。 |
| I-3 | ❌ P1-1 | `app.js:8258-8265` 清空 session，但 `:9093-9119` 未等待新 canonical 集合即可生成。 |
| I-4 | ✅ | `app.js:8589` 仅收集 `operatorInstruction`；`:9115-9122` 仅传给生成 endpoint；`:9408-9427` 最终发送不提交 instruction/freeText。 |
| I-5 | ❌ P1-1 | `PendingMailOperationService.kt:152-157` 只在非空 qa IDs 校验；`:9201-9227` 竞态可采用空 IDs。 |
| I-6 | ❌ P1-2 | `PendingMailOperationService.kt:214-235`、`:237-252` 同步审计写入，`sendManualRichReply` 自 `:97` 开始处于事务中。 |
| I-7 | ❌ P1-1 | 正常路径 `PendingMailOperationService.kt:204-232` 正确写 link/log；竞态采用空 IDs 时该分支不执行。 |
| I-8 | ✅ | `UnmatchedInboundMailController.kt:196-204`、`:249-267` 三个旧 POST 均返回 410 和固定提示。 |
| S-1 | ✅ | `app.js:8563-8602` 复用既有三栏 class；`styles.css` 无 diff。 |
| S-2 | ❌ P1-4 | `app.js:8337`、`:8549` fallback 输出 `规则 #ID`。 |
| S-3 | ✅ | `app.js:8331-8339` selected list 无 draggable/move 控件；`:8591-8594` 仅生成与采用按钮。 |
| 删除/兼容 | ❌ P1-3 | `LlmStitchService` 已删除，但人工排序拼接 API 尚存。 |
| 范围 | ⚠️ | 工作区含 Phase 1–5 改动；本轮只审计 Phase 6 文件。P1-3 所需两文件已由子计划修正记录明确纳入。 |

### 语义完整性审计

- Accumulation check：✅ N/A；无时间窗口计数器。
- State machine check：✅ N/A；`READY/NEEDS_REVIEW/BLOCKED` 是展示/发送闸门，不是可迁移状态机。
- Cross-plan check：❌ P1-1。Phase 4 grounded 生成的事实集合在 Phase 6 checkbox→evaluate→generate→adopt 链路可漂移为空，绕过最终校验和审计。
