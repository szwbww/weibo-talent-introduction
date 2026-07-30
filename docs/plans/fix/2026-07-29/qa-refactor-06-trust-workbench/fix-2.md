# QA 重构 06：可信回复工作台与最终校验 — 修复计划 2

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-17/qa-fact-card-trust-reply-master-plan.md` Phase 6。
- 子计划：`docs/plans/2026-07-17/qa-refactor-06-trust-workbench.md`。
- 上轮：`docs/plans/fix/qa-refactor-06-trust-workbench/fix-1.md`。

## 约束摘录

| 约束 | 要求 |
|---|---|
| I-6 | 历史草稿或审计写入失败不能单独阻止人工富文本发送。 |
| I-7 | 正常路径仍写 canonical facts、关联表与既有 `SEND_MANUAL_COMPOSED_REPLY` 审计。 |

## 修正记录表

| ID | 优先级 | 发现 | 触发频率 |
|---|---|---|---|
| P1-1 | P1 | `sendManualRichReply` 仍在外层 `@Transactional` 内直接调用审计写入；虽然 catch 了异常，未使用独立事务或 after-commit。日志库错误可把同一事务标为 rollback-only，造成 SMTP 已投递但 mail_record/link 回滚。 | 低频：审计表连接、序列化或写入异常时；一旦发生可诱发重复发送。 |

## 修复规格

### P1-1：审计改为 after-commit best effort

- 文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`、`src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`。
- 在 mail_record 与 canonical links 成功后注册 transaction after-commit 回调；回调中调用现有 `OperatorActionLogService.record`，并捕获、记录失败。
- 若没有活跃事务同步（直接单元调用），保持当前 best-effort 调用；生产 `@Transactional` 路径不得在提交前写 audit。
- 正常路径的 action、`canonicalFactIds`、`serverSuggestedFactIds`、links 与 ordinal 不变。
- 回归测试覆盖 audit 抛错不影响 send、mail_record/link 已保存；并断言事务同步活跃时审计在 after-commit 才调用。

## 当前状态（修复前）

- 编译：PASS — `mvn -q -Dtest=PendingMailOperationServiceTest,UnmatchedInboundTrustWorkbenchTest,AiReplyHighRiskClaimValidatorTest,QaRuleAuditServiceTest test`。
- JS：PASS — Maven 触发全量 `node --test`，329 passed，0 failed。
- 定向 JS：PASS — `trustReplyWorkbench.test.js` + `aiAdoptDraftRouting.test.js`，11 passed，0 failed。

## 合规审计

| 约束 | 结论 | 证据 |
|---|---|---|
| I-1 / fix-1 P1-3 | ✅ | `QaReplyComposer.kt:10-48` 仅保留自动路径 `compose/selectPrimary`；`rg` 无 `composeInOperatorOrder` 或 `LlmStitchService`。 |
| I-2/I-3/I-5/I-7 / fix-1 P1-1 | ✅ | `app.js:165-170` 变更事实立即清空确认与草稿；`:8319-8335` 只接受同快照 evaluate；`:9149-9159`、`:9261-9269` pending/未确认时阻止生成与采用。 |
| I-4 | ✅ | `app.js:8619-8649` 字段仅为 `operatorInstruction`；`:9175-9182` 仅传生成请求；`:9463-9497` 最终发送不提交该字段。 |
| I-5 | ✅ | `PendingMailOperationService.kt:132-162` 先 canonicalize，再在 SMTP 前执行 `validatePlainText`；失败抛固定 code。 |
| I-6 / fix-1 P1-2 | ❌ P1-1 | `PendingMailOperationService.kt:102` 外层事务仍活跃；`:409-438` 直接调用 `operatorActionLogService.record`，仅 catch，未隔离或延后。 |
| I-7 | ✅ | `PendingMailOperationService.kt:209-238` 写 canonical IDs/ordinal，审计使用服务端 suggested facts。 |
| I-8 | ✅ | `UnmatchedInboundMailController.kt:196-204`、`:249-267` 三个旧 POST 均 410。 |
| S-1/S-3 | ✅ | `app.js:8619-8658` 复用三栏 class，只含生成/采用；未改 `styles.css`。 |
| S-2 / fix-1 P1-4 | ✅ | `app.js:8381-8385`、`:8601-8611` 使用 `UNNAMED_FACT_LABEL`，不展示 rule ID。 |
| 范围 | ⚠️ | 本轮修复 JS 旧契约测试以匹配已删除独立 AI 面板；为 Phase 6 引入的测试维护，非产品行为改动。 |

### 语义完整性审计

- Accumulation check：✅ N/A；无时间窗口累计。
- State machine check：✅ N/A；readiness 是评估/发送闸门，不是状态机。
- Cross-plan check：✅ Phase 4→6 的 facts 选择在 evaluate 确认前不可生成或采用；重开页面会重建并重新 evaluate，安全失败。
