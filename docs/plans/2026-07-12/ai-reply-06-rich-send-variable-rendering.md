# AI 采用草稿最终变量渲染

## 需求描述

Observable outcome：AI 草稿采用到人工富文本后，发送前按最终选择的 sender account/contact 再次渲染文本和 HTML；已知 `${...}` 不会字面外发。  
What must NOT change：人工富文本样式、QA 审计关联、mail type/action type、普通无占位符邮件、人工 composed/QA/自动回复现有渲染链。  
Out of scope：改变编辑器、保存 draft、调整 sender 选择 UI、修改其他三个 QA 外发 seam。

## 关键不变量

### Invariant I-1: 最终 sender/contact 权威
- Rule: 发送时使用 `resolvePendingReplyAccount` 最终账号和当前 ExpertContact；预览值不作为最终外发依据。
- Applies to: `sendManualRichReply`。
- Violation consequence: 切换账号后签名仍是旧账号。
- 来源: original

### Invariant I-2: 文本与 HTML 同值不同转义
- Rule: textBody 用普通变量值渲染；htmlBody 用 HTML-escape 后变量值渲染，保留编辑器标签；两者语义一致，禁止直接把未转义专家名插入 HTML。
- Applies to: MailVariableService 新 HTML 方法、sendManualRichReply。
- Violation consequence: multipart 内容漂移或 HTML 注入。
- 来源: K-plaintext-reply-client-reflow

### Invariant I-3: 未知 token 禁止外发
- Rule: 发送前 `requireValidPlaceholders` 校验 raw text/html；未知 key 直接 400/IllegalArgumentException，不得原样发送。已知无值无 fallback 按现有变量规则为空。
- Applies to: manual rich send。
- Violation consequence: `${typo}` 字面到达专家。
- 来源: K-qa-outbound-render-seams

### Invariant I-4: 审计/正文写已渲染值
- Rule: ComposedMail html/text、mail_record.body、bodyPreviewText 全部基于 rendered 值；qaRuleIds/matchedQaRuleId/ordinal/action type 不变。
- Applies to: delivery、mail record、operator log。
- Violation consequence: 实发与审计预览不一致。
- 来源: K-rich-reply-qa-audit-reuse

### Invariant I-5: 其他外发 seam 零改动
- Rule: sendQaReply/sendManualComposedReply/AutoMailReplyService 已有渲染不改；本计划仅补 manual rich seam。
- Applies to: QA 外发全集。
- Violation consequence: 扩散回归。
- 来源: K-qa-outbound-render-seams

## 现状审计

### 人工富文本外发
- Schema: mail_record.body 存正文；mail_record_qa_rule 存 QA 审计关联。
- Write paths: `PendingMailOperationService.sendManualRichReply` 直接发送 raw htmlBody，mail_record 写 textBody/raw html；携带 QA 时写关联和 COMPOSED action。
- Read paths: SMTP delivery、邮箱历史、QA audit/operator log。
- Interaction points: sendQaReply/manualComposed 已 renderForContact；manualRich 是缺失 seam，但审计分支有意复用现状，不能改 action type。（来源: K-rich-reply-qa-audit-reuse / K-qa-outbound-render-seams）

## 实现方案

### T1：增加 HTML 安全渲染（I-1/I-2/I-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt`

- 新增 `renderHtmlForContact`：复用同一变量构建，但对每个 value `HtmlUtils.htmlEscape` 后交给既有 renderWithVariables。
- 不复制 placeholder regex；fallback/unknown 语义仍由模板服务和 validator 控制。

### T2：补齐 manual rich 最终 seam（I-1 至 I-5）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`

- resolve 最终 account 后校验 raw html/text token。
- renderedText 来源为 textBody；为空时先 clean raw html 再渲染。
- renderedHtml=renderHtmlForContact(raw html)。
- delivery、record、preview log 改用 rendered；QA 审计代码完全不动。

### T3：测试（I-1 至 I-5）
文件：
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailVariableServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`

- HTML escape、fallback、纯文本一致；最终 sender 切换；unknown token 拒绝；mail/delivery/record/log rendered；QA audit 关联不变；无 token 回归。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt` | HTML 安全变量渲染 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | manual rich 最终渲染 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailVariableServiceTest.kt` | HTML/text 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | 发送/审计测试 |

## 验收标准

- I-1：测试切换 sender 后实发签名取最终账号。
- I-2：`A&B <X>` 在 text 原样、HTML 为 escaped；编辑器标签保留。
- I-3：未知 token 不调用 delivery/save。
- I-4：SMTP text、record.body、log preview 相同 rendered 文本；QA ordinal/action 不变。
- I-5：其他三个 seam 无 diff且现有测试通过。
- 命令：`mvn -Dtest=MailVariableServiceTest,PendingMailOperationServiceTest test`。

## 人工验收清单

### A-1: AI 草稿采用并发送
- 前置条件: AI 草稿 raw 含 expertName/senderName/teamName；最终选择账号 B。
- 操作步骤: 采用草稿，选择账号 B，发送到测试邮箱。
- 预期结果: 收件邮件显示实际专家名与账号 B 签名；正文无 `${`；段落和编号保留。
- 覆盖: I-1/I-2/I-4

### A-2: HTML 特殊字符安全
- 前置条件: senderName=`A&B <Team>`。
- 操作步骤: 发送含 senderName 的富文本草稿，查看原始 MIME。
- 预期结果: HTML 中为 escaped 文本且页面显示 `A&B <Team>`；text part 为普通文本；无标签注入。
- 覆盖: I-2

### A-3: 未知 token 阻止发送
- 前置条件: 编辑器加入 `${unknownKey}`。
- 操作步骤: 点击发送。
- 预期结果: 页面提示非法占位符；SMTP 未发送；mail_record/operator log 未新增。
- 覆盖: I-3/I-4

### A-4: 无变量富文本回归
- 前置条件: 普通人工富文本，无 `${...}`。
- 操作步骤: 发送并检查审计。
- 预期结果: 格式与改动前一致；mailType=`MANUAL_RICH_REPLY`；携带 QA 时原 action/ordinal 保持。
- 覆盖: I-4/I-5 / must-NOT-change

## 修正记录

| 日期 | 修正项 | 原约束 | 修正后约束 | 原因 | 来源 |
|---|---|---|---|---|---|
| 2026-07-13 | 服务端统一最终渲染门禁 | 仅 hasTemplate/template* 时校验渲染 | 所有 manual rich 请求始终 requireValidPlaceholders + 最终 account/contact 渲染；template* 仅作 raw 优先来源；无占位符编辑内容渲染为 no-op 保格式 | hasTemplate 门禁让无 adoption 标记请求可字面外发 ${typo} | docs/plans/fix/ai-reply-06-rich-send-variable-rendering/fix-1.md |
