# fix-1：manual rich 外发渲染门禁

## 原计划 / 子计划引用

- 原计划包：`docs/plans/2026-07-12/ai-reply-point-by-point-plan-index.md`
- 子计划：`docs/plans/2026-07-12/ai-reply-06-rich-send-variable-rendering.md`

## 约束摘录

- I-1：发送时以 `resolvePendingReplyAccount` 和当前 `ExpertContact` 为权威。
- I-2：text 用普通变量值，HTML 用转义变量值；保留编辑器标签。
- I-3：发送前校验 raw text/html，未知 token 必须阻止 delivery、record 和 log。
- I-4：delivery、mail_record、preview log 全写 rendered 值。
- I-5：仅改 manual rich seam，QA 审计 action、ordinal、关联不变。

## 修正记录表

| P1 | 触发频率 | 根因 |
|---|---|---|
| 无 `templateTextBody/templateHtmlBody` 的 manual rich 请求绕过最终校验与渲染，`${typo}` 可字面外发 | 高：普通人工富文本、API 调用、已编辑 AI 草稿都会缺少前端 adoption 标记 | 安全边界错误地由 `hasTemplate` 决定 |

## 修复规格

### P1：所有 manual rich 请求统一进入最终渲染门禁

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`

1. 移除以 `hasTemplate` 决定是否验证/渲染的分支。
2. raw text 取 `templateTextBody`（有值）否则 `textBody`，再否则清洗 raw html；raw html 取 `templateHtmlBody`（有值）否则 `htmlBody`。
3. 在 delivery 前对 raw text、raw html 都调用 `requireValidPlaceholders`；任何非法 token 抛 `IllegalArgumentException`，不得写 delivery/record/log。
4. 始终以最终 account/contact 生成 `renderForContact(rawText)` 与 `renderHtmlForContact(rawHtml)`；以这两个 rendered 值构造 `ComposedMail`、`MailRecord.body` 与 `bodyPreviewText`。
5. 保持携带 `qaRuleIds` 的 `SEND_MANUAL_COMPOSED_REPLY`、关联表写入和 ordinal 完全不变。

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`

1. 增加无 `template*` 时 `${unknownKey}` 被拒绝且 delivery/record/log 均未调用的测试。
2. 增加无 `template*` 时 `${senderName}` 在最终账号切换后 text/HTML/record/log 均为 rendered 值的测试。
3. 保留既有 QA 审计回归断言。

## 当前状态

- 编译：PASS（固定 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` 后 Kotlin compile 通过）。
- 测试：未完成。默认 JDK 25.0.1 与 Kotlin 1.9.25 不兼容；Java 11 重跑在 test-compile 后未产出本轮 Surefire 报告。现存报告为 12:30 历史结果，不能作为本轮证据。

## 合规审计

- I-1：❌ `PendingMailOperationService.kt:217-239` 仅 `hasTemplate` 时用最终 account/contact 渲染；无标记请求直接发 editor 值。
- I-2：❌ `PendingMailOperationService.kt:236-238` 无标记请求直接使用 raw HTML/text，绕过 `renderHtmlForContact`。
- I-3：❌ `PendingMailOperationService.kt:220-239` validator 只在 `hasTemplate` 分支调用；无标记 `${unknownKey}` 可外发。
- I-4：❌ `PendingMailOperationService.kt:241-272` 记录和日志基于未渲染 `final*` 分支值。
- I-5：✅ `PendingMailOperationService.kt:275-309` QA action、关联与 ordinal 代码未改。
- Deleted code：✅ N/A。
- No extras：❌ `UnmatchedInboundMailController.kt`、`app.js` 的传递改动不在本子计划变更清单内；作为跨计划 glue 观察，不构成本轮修复任务。

## 语义完整性检查

- Accumulation check：✅ 无 time-window counter。
- State machine check：✅ 无状态机改动。
- Cross-plan check：❌ AI 草稿 adoption（计划 5）只在编辑器完全未改时传 `templateTextBody`（`app.js:9386-9395`），与计划 6 的“最终发送安全边界”契约不一致；P1 已由服务端统一渲染门禁覆盖。
