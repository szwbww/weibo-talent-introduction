# QA 重构 05：自动回复切换到 Grounded 引擎

## 需求描述

把自动 QA 回复与自动预览从 `QaMatchService.match()->QaReplyComposer` 切换到同一个 grounded 决策服务。可观察结果：只有完整、AUTO、LLM 成功且事实校验通过的自然草稿才会自动发送；其他情况统一转人工并显示精确原因。

必须不变：

- 全局/账号/联系人自动回复开关、退订、人工接管、介绍信前置条件、附件意图等现有上游门禁优先级不变。
- 最终邮件仍由 `MailVariableService` 使用实际 sender/contact 渲染，并走现有 SMTP、mail_record、mail_record_qa_rule、IMAP ack 与联系状态更新事务。
- 自动预览不写数据库、不发邮件。

Out of scope：人工工作台；修改 QA 管理；自动联网取证；自动发送 deterministic fallback。

## 关键不变量

### Invariant I-1：唯一自动决策
- Rule：`GroundedAutoReplyDecisionService` 是自动实发与预览的唯一 QA decision seam；两方不得分别复制 readiness/policy/usedLlm 校验。
- Applies to：AutoMailReplyService、AutoReplyPreviewService。
- Violation consequence：预览显示可发送，实际却转人工，或反之。
- 来源：original；K-preview-mirrors-pipeline（既有知识关联）。

### Invariant I-2：自动发送五重门
- Rule：`readyToSend=true` 必须同时满足：qaRuleIds 非空、所有事实 policy=AUTO、requestFacts 无 PARTIAL/UNSUPPORTED、draftReadiness=READY、usedLlm=true 且 generationState=LLM_USED。
- Applies to：GroundedAutoReplyDecisionService。
- Violation consequence：REVIEW 事实、缺口或 fallback 被自动外发。
- 来源：original；K-llm-timeout-fallback。

### Invariant I-3：失败原因稳定且可观测
- Rule：按固定优先级返回单一 reason：`AI_AUTO_REPLY_DISABLED` → `QA_NO_MATCH` → `QA_POLICY_REVIEW` → `QA_GROUNDING_GAP` → `AI_GENERATION_UNAVAILABLE` → `AI_REPLY_VALIDATION_FAILED`；不得全部折叠为 QA_NO_MATCH。
- Applies to：decision、manual review processReason、preview reason。
- Violation consequence：运营无法定位是知识缺失、策略还是模型故障。
- 来源：original。

### Invariant I-4：自动正文没有变体/直拼
- Rule：自动路径不得调用 `QaReplyComposer.compose`、`ContentVariantService.resolveBody(QA_RULE,...)` 或读取 `replyBody`；body 必须来自 grounded `draftText`，事实源为 answerBody。
- Applies to：AutoMailReplyService、AutoReplyPreviewService、decision。
- Violation consequence：同一产品同时存在可信生成与旧随机拼接。
- 来源：K-qa-replybody-outbound-sites（本计划有意替换其 auto seam）。

### Invariant I-5：主题与线程连续
- Rule：自动 grounded 主题固定为原 inbound subject 的 `Re:` 形式；若原主题已以 `Re:` 开头则不重复。不得再从单条 QA `replySubject` 选主题。
- Applies to：decision、实发、preview。
- Violation consequence：复合问题被任意 primary rule 改写主题，线程感和可信度下降。
- 来源：original。

### Invariant I-6：审计只关联实际证据
- Rule：成功外发后 `mail_record_qa_rule` 只写 decision.qaRuleIds，顺序保持 grounded request/evidence 顺序；失败转人工不得写 outbound mail 或 QA 关联。
- Applies to：AutoMailReplyService 成功/失败分支。
- Violation consequence：审计关联无关事实或记录未发送邮件。
- 来源：K-ai-reply-prompt-vs-send-rule-ids、K-audit-selected-source。

### Invariant I-7：自动 Grounded 有 kill switch
- Rule：现有 `LlmProperties.autoReplyEnabled`（环境变量 `LLM_AUTO_REPLY_ENABLED`）为自动 grounded 总开关；`false` 时不得调用生成器或发送 QA 自动回复，固定转人工原因 `AI_AUTO_REPLY_DISABLED`。preview 必须显示同一阻断原因。
- Applies to：AutoMailReplyService、AutoReplyPreviewService、GroundedAutoReplyDecisionService 调用边界。
- Violation consequence：无法先影子验证或在模型异常时快速止损。
- 来源：original。

## 现状审计

### 自动 QA 路径
- `AutoMailReplyService` 上游先判全局/账号/contact/退订/intent 等；QA 分支在约 506 行调用 `qaMatchService.match(cleanedBody,variantSeed)`。
- 当前门禁：match null、旧 auto flag、handoff、gap；成功直接 `renderForContact(match.replyBody)` 并发送。
- 成功写 `mail_record` 与每个 matchedRuleId 的 `mail_record_qa_rule`；失败调用 `markManualReview/confirmProcessed`。
- Interaction points：decision 输出 → mail render/send；send result → mail_record/link/contact/status；failure reason → inbound processing。

### 自动预览路径
- `AutoReplyPreviewService` QA 分支独立调用同一 `qaMatchService.match`，复制旧门禁并返回 replyBody/matchedRuleIds。
- 它还计算 wouldBeBlockedBy（账号/contact/介绍信等）；这些外围门禁必须保留。
- Interaction points：decision 输出 → previewKind/reason/replyBody；外围 blocked list 不应改变 decision 本身。

### `mail_record_qa_rule`
- Schema：V42；unique(mail_record_id,qa_rule_id)，ordinal 必填，两个外键 RESTRICT。
- Write paths：AutoMailReplyService；PendingMailOperationService manual composed/rich。
- Read paths：QaRuleAuditService、邮件/监控关联。
- 本计划只改自动写入 ID 来源，不改表结构。

### `qa_rule`（只读）
- 子计划 03 后 answerBody/replyPolicy 已可用；Grounded engine 已输出证据并集。
- Interaction point：运营 policy/answerBody 更新必须在下一封自动回复即时生效。

### LLM 自动开关（只读配置）
- Schema/mapping：`application.yml` 已把 `talent-introduction.llm.auto-reply-enabled` 映射到 `LLM_AUTO_REPLY_ENABLED`，默认 `false`；`LlmProperties.autoReplyEnabled` 已存在但当前未被自动服务读取。
- Write paths：部署环境变量；本计划不新增配置文件或数据库写入。
- Read paths：本计划由自动实发/预览在调用 shared decision 前读取。
- Interaction point：部署配置变更 → preview/实发同时停止或放行 grounded 自动回复。

## 实现方案

### T1：新增共享自动决策服务
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/config/LlmProperties.kt`
- 删除 `autoReplyEnabled` 的“未使用”注释，不改字段名、前缀、默认值或 `application.yml`。
- 调 `AiReplyDraftService.generate(inboundText, emptyList())`，不传 operator instruction/turns；研究画像不足时保持 fail closed。
- 服务端重新加载 result.qaRuleIds 并验证 enabled/policy=AUTO/answerBody 非空，不能只信 result。
- 按 I-2/I-3 产生 immutable decision：readyToSend、reason、subject、rawDraftText、qaRuleIds、generationState/readiness。
- subject helper 遵守 I-5；开关调用边界遵守 I-7。

### T2：自动实发接入
- 文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`
- 保留所有 decision 前的现有门禁；QA 分支用共享 service。
- `autoReplyEnabled=false` 时在生成前固定转人工 `AI_AUTO_REPLY_DISABLED`，生成器/SMTP 调用均为 0。
- 非 ready 走现有 manual review/confirmProcessed，processReason 使用 I-3 code；不保存 outbound。
- ready：用最终 account/contact render rawDraftText，发送并写 mail_record/link；不调用 variant/composer。遵守 I-2、I-4、I-6、I-7。

### T3：自动预览镜像
- 文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt`
- 使用同一 decision；ready 时返回 QA_AUTO_REPLIED、同 subject、同渲染正文、同 qaRuleIds。
- 非 ready 时 previewKind=QA_NO_MATCH 或 QA_GAP/人工类型，reason 精确为 I-3 code；wouldBeBlockedBy 仍独立展示外围门禁。
- 预览只调用不落审计的生成/决策 seam；`recordInitialDraft()` 仍只属于真实草稿审计写路径，preview/controller 不新增模拟审计记录。
- 遵守 I-1、I-2、I-3、I-5、I-6、I-7。

### T4：测试
- 文件：
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt`
- 覆盖五重门、reason 优先级、Re subject、preview/send 镜像、变量渲染、成功关联与失败零写入。
- 测试必须逐项断言 I-1 至 I-7。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt` | 新增共享 decision |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/config/LlmProperties.kt` | 启用既有自动 LLM 开关语义 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 自动实发切换 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt` | 预览镜像切换 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt` | decision 测试 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` | 实发测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt` | 预览测试 |

共 7 个文件、2 个子系统（mail auto reply + existing LLM config），符合限制。

## 验收标准

- I-1：auto 与 preview 均只调用 shared decision；源码无重复五重门条件。
- I-2：五个条件逐个失败时 ready=false；只有全满足为 true。
- I-3：每类 fixture 返回固定 reason，优先级测试覆盖同时失败情况。
- I-4：三份生产文件无 QA `resolveBody/compose/replyBody` 调用。
- I-5：`Question`→`Re: Question`；`Re: Question` 保持；空主题→`Re:` 或项目既有明确 fallback，实发/预览一致。
- I-6：成功 mail_record_qa_rule 与 result evidence 精确一致；失败时 outbound/link save 调用为 0。
- I-7：开关 false 时 auto/preview 固定 `AI_AUTO_REPLY_DISABLED`，generate/send/save 调用均为 0；true 时才进入五重门。
- 预览 read-only：mail_record、operator_action_log、inbound status、contact 均无写调用。
- 集成：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=GroundedAutoReplyDecisionServiceTest,AutoMailReplyServiceTest,AutoReplyPreviewServiceTest,BatchAutoMailReplyServiceTest test
```

## 人工验收清单

### A-1：完整 AUTO 自动发送
- 前置条件：测试账号/contact 均允许自动回复；来信所有问题有 AUTO 事实；LLM 正常。
- 操作步骤：1. 查看自动预览；2. 投递同内容测试邮件；3. 查看 outbound 和 QA 关联。
- 预期结果：预览为 QA_AUTO_REPLIED；实际发送一次；主题/正文与预览一致；关联规则 ID 与预览一致。
- 覆盖：I-1、I-2、I-5、I-6。

### A-2：REVIEW 事实转人工
- 前置条件：命中事实中至少一条 policy=REVIEW。
- 操作步骤：查看预览并投递测试邮件。
- 预期结果：reason=`QA_POLICY_REVIEW`；不产生 outbound/mail_record_qa_rule；inbound 进入人工处理。
- 覆盖：I-2、I-3。

### A-3：缺失事实转人工
- 前置条件：复合问题有一个 UNSUPPORTED intent，其余 AUTO。
- 操作步骤：预览并处理。
- 预期结果：reason=`QA_GROUNDING_GAP`；不得发送只回答一部分的邮件。
- 覆盖：I-2、I-3。

### A-4：LLM 超时/非法 JSON
- 前置条件：分别模拟超时与结构化响应非法。
- 操作步骤：预览并投递。
- 预期结果：分别归入 `AI_GENERATION_UNAVAILABLE` 或 `AI_REPLY_VALIDATION_FAILED`；均转人工；deterministic fallback 不外发。
- 覆盖：I-2、I-3、I-4。

### A-5：外围门禁回归
- 前置条件：分别关闭全局、账号、联系人自动开关，并测试退订/人工接管/介绍信未发。
- 操作步骤：对同一可 READY 的 QA 来信预览/投递。
- 预期结果：外围 blocked reason 与改造前一致，且优先于 grounded 自动发送。
- 覆盖：must-NOT-change。

### A-6：QA 更新跨路径
- 前置条件：一条 AUTO 事实；先生成 READY 预览。
- 操作步骤：将其 policy 改 REVIEW，再立即预览。
- 预期结果：不需重启，预览立即变 `QA_POLICY_REVIEW`；证明 QA 管理写→auto decision 读一致。
- 覆盖：qa_rule interaction point。

### A-7：kill switch 止损
- 前置条件：一封原本满足 AUTO/READY 的测试来信；部署环境可修改 `LLM_AUTO_REPLY_ENABLED`。
- 操作步骤：1. 将开关设为 `false` 并按现有配置发布方式使其生效；2. 查看预览；3. 投递同内容测试邮件。
- 预期结果：预览 reason=`AI_AUTO_REPLY_DISABLED`；实际不发送并进入人工处理；恢复 `true` 后预览重新进入 grounded 五重门。
- 覆盖：I-7、配置→preview/实发 interaction point。
