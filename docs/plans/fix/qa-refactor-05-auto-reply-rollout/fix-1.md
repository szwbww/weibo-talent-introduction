# QA 重构 05：自动回复切换到 Grounded 引擎 — 修复计划 1

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-17/qa-fact-card-trust-reply-master-plan.md` Phase 5。
- 子计划：`docs/plans/2026-07-17/qa-refactor-05-auto-reply-rollout.md`。
- 本轮无此前修复计划。

## 约束摘录

| 约束 | 要求 |
|---|---|
| I-1 | 自动实发与预览共用 `GroundedAutoReplyDecisionService`，不得复制 decision gate。 |
| I-2/I-3 | 仅完整 AUTO、READY、真实 LLM 结果可自动发送；失败原因稳定。 |
| I-4 | 自动路径只使用 grounded `draftText`，不读旧 QA 正文、变体或 composer。 |
| I-5 | 实发与预览使用相同 `Re:` 主题和最终正文。 |
| I-6 | 仅成功外发写实际 evidence ID，失败不写 outbound/关联。 |
| I-7 | kill switch 不生成、不发送；预览给出同一原因。 |
| T3 | QA READY 的预览返回同 subject、**同渲染正文**、同 `qaRuleIds`，且保持只读。 |

## 修正记录表

| ID | 优先级 | 发现 | 触发频率 |
|---|---|---|---|
| P1-1 | P1 | `AutoReplyPreviewService` 将 `decision.rawDraftText` 直接返回；实发在发送前调用 `MailVariableService.renderForContact`。草稿含 `${senderName}`、联系人变量或 fallback 时，预览正文与实际邮件不一致。 | 常见：任何 grounded 草稿使用发件人/联系人变量时。 |

## 修复规格

### P1-1：预览复用最终变量渲染

- 修改范围：`AutoReplyPreviewService.kt`、`AutoReplyPreviewServiceTest.kt`；不得改 decision gate、SMTP、审计或数据表。
- QA READY 分支使用与实发一致的 sender account/contact 变量，通过 `MailVariableService.renderForContact(rawDraftText, account, contact)` 生成 `replyBody`；`replySubject` 与 `matchedRuleIds` 仍直接取 shared decision。
- 预览不得写 `mail_record`、`mail_record_qa_rule`、operator audit、inbound 状态或 contact。
- 增加回归：grounded raw draft 含 sender/contact 占位符时，preview 返回已渲染文本；同 fixture 的实发 `ComposedMail.text` 相同。保留现有外围 `wouldBeBlockedBy` 的只读展示语义。

## 当前状态（修复前）

- 编译：PASS。
- 测试：PASS — 85 passed, 0 failed, 0 skipped。
- 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=GroundedAutoReplyDecisionServiceTest,AutoMailReplyServiceTest,AutoReplyPreviewServiceTest,BatchAutoMailReplyServiceTest test`。

## 合规审计

| 约束 | 结论 | 证据 |
|---|---|---|
| I-1 | ✅ | `AutoMailReplyService.kt:505` 与 `AutoReplyPreviewService.kt:96` 均调用 shared decision。 |
| I-2 | ✅ | `GroundedAutoReplyDecisionService.kt:81-152` 重新加载 AUTO/enabled/non-empty answerBody，检查 request gap、READY、LLM_USED。 |
| I-3 | ✅ | `GroundedAutoReplyDecisionService.kt:94-126` 固定 reason 分支；`GroundedAutoReplyDecisionServiceTest.kt:114-189` 覆盖。 |
| I-4 | ✅ | `AutoMailReplyService.kt:562-574` 仅用 `rawDraftText`；`AutoReplyPreviewService.kt:95-106` 无 composer/variant/`replyBody` 读取。 |
| I-5 / T3 | ❌ P1-1 | 实发 `AutoMailReplyService.kt:562-565` 渲染变量；预览 `AutoReplyPreviewService.kt:105` 原样返回 raw draft。 |
| I-6 | ✅ | `AutoMailReplyService.kt:505-535` 非 ready 返回前无 outbound；`:577-607` 仅成功写 decision evidence 且保留 ordinal。 |
| I-7 | ✅ | `GroundedAutoReplyDecisionService.kt:42-51` 开关 false 在 generate 前返回；实发/预览均只经该 seam。 |
| Preview 只读 | ✅ | `AutoReplyPreviewService.kt:37-117` 无 save/send/audit 写调用。 |
| 范围 | ✅ | P1 修复限制在子计划列出的 preview 生产/测试文件。 |

### 语义完整性审计

- Accumulation check：✅ N/A；无时间窗口计数器。
- State machine check：✅ N/A；本子计划不引入状态机。
- Cross-plan check：❌ P1-1。Phase 4 grounded draft → Phase 5 preview/send 边界在变量渲染前后不一致；happy path 预览确认后会发送不同正文。
