# QA 重构 06：可信回复工作台与最终校验

## 需求描述

把“组装台回复 + AI 生成回复”合并为一个“可信回复工作台”：服务端拆分专家请求，运营选择原子事实，LLM 生成自然草稿，系统展示 readiness/缺口，采用后仍走人工富文本发送。取消规则正文拼接、人工段落排序、QA 内容变体、致谢片段手选和事实型自由文本。

必须不变：

- 最终仍由运营点击人工富文本发送；账号选择、主题/正文必填、变量最终渲染、SMTP、mail_record 与操作日志保持现有 seam。
- 纯人工富文本回复（无 qaRuleIds）不受 QA 最终事实校验约束。
- 历史 SEND_MANUAL_COMPOSED_REPLY 审计与 mail_record_qa_rule 仍可统计。
- 回复片段 frame 与其变体功能不删除；只是工作台不再提供 QA 变体开关和 ACK 手工拼接。

Out of scope：项目介绍信、TrustProfile UI、自动发送（已由子计划 05完成）、删除旧数据库列。

## 关键不变量

### Invariant I-1：工作台控制事实，不拼邮件
- Rule：前端选择的是 fact IDs；任何预览/草稿不得由 `answerBody.join`、`QaReplyComposer` 或 `LlmStitchService` 产生。生成必须调用 `AiReplyDraftService` grounded seam。
- Applies to：suggest/evaluate/generate、app.js。
- Violation consequence：事实卡仍被当邮件片段，机械回复问题未解决。
- 来源：original。

### Invariant I-2：服务端权威评估
- Rule：每次 facts 变化由 `POST /api/mail/unmatched-inbound/{id}/composed-reply/evaluate` 重新加载规则并返回 canonical fact IDs、requestCoverage、draftReadiness；前端不得用“candidate ID 有交集”自行宣布已覆盖。
- Applies to：controller、PendingMailOperationService、app.js。
- Violation consequence：复合问题再次假覆盖。
- 来源：original；K-gap-items-compose-only、K-request-facts-not-flat-pool。

### Invariant I-3：生成锁定事实集合
- Rule：首次生成显式提交当前 canonical fact IDs；续轮“重新表达”沿用同一集合，模型/前端不能自动增删事实。改变事实集合必须清空当前草稿会话并重新 evaluate/generate。
- Applies to：ai-reply/turn request、aiReplyState、fact checkbox handler。
- Violation consequence：运营审核过的事实与最终草稿来源漂移。
- 来源：original。

### Invariant I-4：操作员输入只作表达指令
- Rule：工作台 textarea 字段命名/文案为 `operatorInstruction`，只允许语气、长度、结构要求；不进入 facts，不直接 append 到正文。最终人工编辑可改正文，但实时高风险校验必须重新执行。
- Applies to：UI、ai-reply/turn、audit。
- Violation consequence：自由文本绕过 grounded 约束。
- 来源：original；K-audit-free-text-topic（旧自由文本能力在本计划终止）。

### Invariant I-5：最终正文实时校验
- Rule：携带 qaRuleIds 的 `sendManualRichReply` 在最终变量渲染前，必须：服务端 canonicalize IDs、拒绝 unknown/disabled/NEVER/unassignable fact；用最终 raw text 对 answerBody 做数字/URL/modality/高风险声明校验。校验失败不调用 SMTP、不写 outbound/link。
- Applies to：PendingMailOperationService、AiReplyHighRiskClaimValidator。
- Violation consequence：采用后人工加入未证实费用、政府、合同、IP、金额等承诺。
- 来源：original；K-manual-rich-render-before-send。

### Invariant I-6：人工发送不信历史 readiness
- Rule：最终事实校验通过后，历史 draft READY/NEEDS_REVIEW/BLOCKED 或 audit 写失败不能单独阻止人工发送；纯措辞编辑允许。BLOCKED 的事实缺口必须通过改变事实集合或删除相关断言解决，不接受浏览器布尔 override。
- Applies to：sendManualRichReply、前端 send。
- Violation consequence：与现有“采用后直接人工发送”产品约定冲突，或客户端伪造确认绕过。
- 来源：K-ai-generation-observability-not-send-gate、K-ai-adopt-direct-send-no-residual-gates。

### Invariant I-7：审计使用最终 canonical facts
- Rule：成功发送后 `mail_record_qa_rule` 写 canonical fact IDs；suggested 集由服务端对 inbound 重新计算，不信客户端；ordinal 为 request/evidence canonical 顺序。action 继续 `SEND_MANUAL_COMPOSED_REPLY` 以复用报表。
- Applies to：PendingMailOperationService、QaRuleAuditService 既有 reader。
- Violation consequence：审计选用集与实际证据不一致。
- 来源：K-rich-reply-qa-audit-reuse、K-audit-selected-source。

### Invariant I-8：旧直发接口关闭
- Rule：`POST /qa-reply`、`POST /composed-reply`、`POST /composed-reply/polish` 返回 HTTP 410 和固定迁移提示；不得保留可直接发送 answerBody/replyBody 的 API backdoor。
- Applies to：UnmatchedInboundMailController、PendingMailOperationService。
- Violation consequence：新 UI 安全但旧 API 仍能机械/未经校验外发。
- 来源：original；K-qa-outbound-render-seams（本计划有意收敛 seam）。

## 样式契约

### S-1：可信回复工作台布局
- 复用：`.compose-workbench-section .compose-workbench` (`styles.css:5282-5294`)、`.compose-panel` (`5296-5320`)、`.compose-fragments`/category/rule chips (`5323-5438`)、`.compose-draft` (`5440` 起)、`.compose-gaps/.compose-gap-list` (`5719-5778`)、960px 响应式 (`5786-5794`)。
- 新增：无新 class、无 CSS。
- DOM 结构：

```html
<details class="detail-section reply-workflow-detail compose-workbench-section">
  <summary class="reply-workflow-summary">可信回复工作台</summary>
  <div class="reply-workflow-content">
    <div class="compose-workbench">
      <div class="compose-panel compose-fragments">
        <h4>可用事实</h4>
        <details class="compose-category-panel"><summary>身份与项目</summary><div class="compose-rule-list">
          <label class="compose-rule-item"><input class="compose-rule-checkbox" type="checkbox"><span>事实标题</span><span class="badge ok">建议</span></label>
        </div></details>
      </div>
      <div class="compose-panel compose-draft">
        <h4>可信草稿</h4>
        <ul class="compose-selected-list"></ul>
        <textarea class="compose-free-text" id="composedOperatorInstruction"></textarea>
        <div class="compose-rendered-preview pre"></div>
        <div class="compose-draft-actions"><button class="button primary">生成可信草稿</button></div>
      </div>
      <div class="compose-panel compose-gaps">
        <h4>问题与依据<span class="compose-count"></span></h4>
        <ul class="compose-gap-list"></ul>
      </div>
    </div>
  </div>
</details>
```

- 禁止项：新增 inline style；新 CSS/class；恢复 `manualReplyUseVariants`、ACK chips、拖拽排序、合并分段预览或独立 AI panel。

### S-2：状态表达
- 复用：READY=`.badge.ok`、NEEDS_REVIEW=`.badge.warn`、BLOCKED=`.badge.error`，定义 `styles.css:750-800`；建议事实 `.badge.ok`。
- 新增：无 CSS。
- DOM 结构：每个请求一行，左侧符号 + 请求文本 + 单一状态 badge；无依据时复用 `.gap-no-rules-hint` 文案“暂无可核验事实”。
- 禁止项：显示 raw intent key、coverage key、rule ID；颜色自行扩展。

### S-3：已选事实与按钮
- 复用：`.compose-selected-list li` (`styles.css:5445-5474`)、`.compose-draft-actions` (`5712-5717`)、通用 `.button.primary/.button.secondary`。
- 新增：无 CSS。
- DOM 结构：selected list 不加 `draggable`，不含 `.compose-drag-handle/.compose-selected-actions`；按钮最多“生成可信草稿/重新生成表达”“采用到人工回复”。
- 禁止项：move up/down、拖拽、直接发送按钮。

## 现状审计

### 人工组装 API/服务
- `GET /composed-reply/suggest`：PendingMailOperationService 调 QaMatchService，支持 useVariants，返回 rulesByCategory/gapItems/frame。
- `POST /composed-reply/polish`：LlmStitchService 先 deterministic compose，再让 LLM 润色。
- `POST /composed-reply`：sendManualComposedReply 解析规则/变体/frame/freeText，直接发送并写审计。
- `POST /qa-reply`：sendQaReply 单规则直接发送。
- `POST /ai-reply/turn`：已有 grounded 生成、initial draft audit、raw/rendered preview。
- Interaction points：suggest/evaluate 输出 → UI selection；selected IDs → ai generate；adopt context → manual rich send；deprecated endpoints → 旧客户端。

### `mail_record_qa_rule`
- Write paths：自动成功、manual composed、manual rich carriesQa。
- Read paths：QaRuleAuditService 从关联表按 ordinal 取 selected，日志只作 fallback。（来源：K-audit-selected-source）
- 本计划删除 direct composed/qa 写路径，保留 auto + manual rich 两条。
- Interaction points：manual rich 成功 save mail_record → link writes → audit reader。

### `operator_action_log`
- manual rich carriesQa 当前记 `SEND_MANUAL_COMPOSED_REPLY`，after 含 qaRuleIds/suggestedRuleIds/ack/freeText/edited。
- QaRuleAuditService 以此 action 查询，并以 mail_record_qa_rule 为 selected 权威。
- 本计划 after 改为 canonicalFactIds/serverSuggestedFactIds/draftGenerationState/edited；为兼容报表同时保留旧 key `qaRuleIds/suggestedRuleIds`，值与新 key一致；ack/freeText 固定 null/删除。
- Interaction points：send log JSON → audit report。

### 最终人工 rich 发送
- 始终进行 placeholder validation、最终 account/contact text+HTML render，再 SMTP/mail_record/log。（来源：K-manual-rich-render-before-send）
- carriesQa 当前只验证规则 exists/enabled，再用 QaReplyComposer.selectPrimary；不验证最终正文事实。
- Interaction point：raw editor/template → final validator → render → SMTP。

### 前端工作台
- `app.js:8190-8782` 当前 buildComposedSegments/merge/deterministic preview、gap intersection、拖拽、ACK、variants、free text、copy。
- `app.js:8784` 后另有独立 AI panel；adopt 后进入 manual rich editor。
- 改动前 DOM 基线见 `app.js:8695-8782`；样式事实见 S-1..S-3。

## 实现方案

### T1：权威 suggest/evaluate API
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
- suggest 调 QaFactSelectionService，返回 request items、factsByCategory、suggestedFactIds、readiness、frame；移除 useVariants/完整 fact body 直接拼接语义。
- 新增 `POST /composed-reply/evaluate`，request 仅 factRuleIds；response 使用同一 requestCoverage/readiness DTO。
- ai-reply/turn 首轮允许显式 fact IDs；续轮由 I-3 锁定。
- 三个旧直发/润色 endpoint 按 I-8 返回 410。
- 遵守 I-1、I-2、I-3、I-4、I-8。

### T2：收敛 PendingMailOperationService
- 文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
- 删除 `sendQaReply/sendManualComposedReply/suggest variants/resolve QA body/append free text` 与 QA variant 依赖。
- `sendManualRichReply` canonicalize fact IDs，使用 canonical first ID 作为 matchedQaRuleId；服务端重新计算 suggested；成功按 I-7 写 links/log。
- 保留纯人工分支、账号解析、变量最终渲染、reply snippet/frame 其他调用。
- 遵守 I-1、I-5、I-6、I-7、I-8。

### T3：最终高风险事实校验
- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt`
- 新增 `validatePlainText(finalRawText, factRuleIds)`，复用现有数字/URL/modality/high-risk family；source 合并 answerBody。
- PendingMailOperationService 在任何 SMTP/mail_record save 前调用；失败返回固定 400 code `AI_REPLY_FACT_VALIDATION_FAILED` 和不泄露内部全文的 warnings。
- 纯人工 qaRuleIds 为空时跳过该 validator，但仍走变量/邮件校验。遵守 I-5/I-6。

### T4：删除旧 stitch service
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/LlmStitchService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/LlmStitchServiceTest.kt`
- 删除文件；确认 `rg LlmStitchService` 只剩历史 docs。
- `llmEnabled` 由 AiReplyDraftService/client 配置的现有可用性 response 提供，不为保留 flag 而留下拼接 service。
- 遵守 I-1、I-8。

### T5：工作台前端重写
- 文件：`src/main/resources/static/app.js`
- 删除 build/merge segments、manual order/drag、ACK chips、useVariants、freeText append、copy direct composition、独立 AI panel。
- 按 S-1..S-3 渲染事实/问题/草稿；facts change 时 debounce evaluate，并按 I-3 清空旧生成状态。
- Generate 调 ai-reply/turn with canonical IDs + operatorInstruction；response 显示 raw/rendered、generationState/readiness；adopt 填 manual rich并保留 qa context。
- manual send 不提交 suggestedRuleIds/useVariants/ack/freeText/edited 权威值；只提交 canonical qaRuleIds 与现有 raw template boundary。
- 遵守 I-1、I-2、I-3、I-4、I-7、S-1、S-2、S-3。

### T6：测试
- 文件：
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt`
  - `src/test/js/trustReplyWorkbench.test.js`
  - `src/test/js/composedReplyOrder.test.js`
- 新增后端 controller/evaluate/410/final-validator/audit tests；新增前端 trust workbench tests。
- 删除 `composedReplyOrder.test.js` 文件或改为负向契约测试；本计划文件清单按“修改/删除”计 1。
- 测试必须逐项断言 I-1 至 I-8、S-1 至 S-3。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 新 suggest/evaluate，旧接口 410 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 收敛 manual rich 唯一发送 seam |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt` | 最终正文校验 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/LlmStitchService.kt` | 删除 |
| 5 | `src/main/resources/static/app.js` | 可信工作台 UI/状态 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | 发送/审计/校验测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt` | 新 API 测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/LlmStitchServiceTest.kt` | 删除 |
| 9 | `src/test/js/trustReplyWorkbench.test.js` | 新 UI 契约测试 |
| 10 | `src/test/js/composedReplyOrder.test.js` | 删除或改负向回归 |

共 10 个文件、2 个子系统（mail workbench backend + frontend），符合限制。

## 验收标准

- I-1：生产 JS/Kotlin 无 `buildComposedSegments/mergeSegmentsToText/composeInOperatorOrder/LlmStitchService`；草稿 API 只走 AiReplyDraftService。
- I-2：任意 facts selection 的 UI 状态等于 evaluate response；JS 无 `candidates.some(selected.includes)` 作为覆盖权威。
- I-3：改变 checkbox 后 draft/turns 清空；续轮 payload fact IDs 与首轮 canonical 集合逐项一致。
- I-4：operatorInstruction 不出现在 facts/source/audit freeText；没有直接 append 代码。
- I-5：携 QA 的高风险无来源正文返回 400，SMTP/mail_record/link/log save 均为 0；合法正文成功。
- I-6：历史 BLOCKED 但最终正文删掉无依据断言且实时校验通过时可人工发送；未知浏览器 override 不影响结果。
- I-7：link IDs/ordinal 与 server canonical 一致；审计 reader 返回同集合；client suggested 被篡改不影响。
- I-8：三个旧 endpoint 均 410；响应包含固定迁移提示“Use trust workbench and manual-rich-reply”。
- S-1：`styles.css` diff 为空；三栏 DOM 与契约一致；960px 下按 facts→draft→gaps 单列；无新增 inline style/class。
- S-2：READY/NEEDS_REVIEW/BLOCKED 只使用 `.badge.ok/.badge.warn/.badge.error`；无 raw intent key、coverage key、rule ID。
- S-3：已选事实无 draggable/drag handle/move action；按钮最多两个固定动作；无直接发送按钮。
- 集成：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=PendingMailOperationServiceTest,UnmatchedInboundTrustWorkbenchTest,AiReplyHighRiskClaimValidatorTest,QaRuleAuditServiceTest test
node --test src/test/js/trustReplyWorkbench.test.js src/test/js/aiAdoptDraftRouting.test.js
```

## 修正记录

| 日期 | 来源 | 修正 |
|---|---|---|
| 2026-07-17 | fix-v Phase 6 fix-1 | I-1 验收要求删除 `QaReplyComposer.composeInOperatorOrder`，原变更清单遗漏其实现与测试。修复范围仅增加 `QaReplyComposer.kt` 和 `QaReplyComposerTest.kt`，用于删除这条已废弃的人工排序拼接 API 及其测试。 |

## 人工验收清单

### A-1：可信工作台完整流程
- 前置条件：一封有 3 个请求、其中 2 个有事实的待处理邮件。
- 操作步骤：1. 打开可信回复工作台；2. 勾选建议事实；3. 输入“更简短、语气自然”；4. 生成；5. 采用到人工回复。
- 预期结果：右侧显示 2 个有依据、1 个无依据，整体 BLOCKED/NEEDS_REVIEW；指令不被原样追加；草稿自然成段；采用后编辑器有草稿和 QA context。
- 覆盖：I-1..I-4、S-1..S-3。

### A-2：切换事实使草稿失效
- 前置条件：已生成一版草稿。
- 操作步骤：取消一条事实或新增一条事实。
- 预期结果：旧草稿/续轮历史清空；状态重新 evaluate；必须重新生成后才能采用。
- 覆盖：I-2、I-3。

### A-3：QA 变体与拼接功能消失
- 前置条件：打开工作台。
- 操作步骤：检查全部控件和网络请求。
- 预期结果：没有“使用内容变体”、致谢 chips、自由文本、上下移动、拖拽、分段合并预览、独立 AI panel；没有 useVariants 参数。
- 覆盖：I-1、S-1、must-NOT-change（回复片段后台仍存在）。

### A-4：最终高风险校验
- 前置条件：采用一个有 QA facts 的草稿。
- 操作步骤：在编辑器加入 facts 中不存在的 `10 million RMB`、新 URL、`no fees`、`will definitely receive` 后发送。
- 预期结果：发送失败并显示事实校验提示；SMTP、outbound、QA link 均无新增。逐项删除无依据断言后发送成功。
- 覆盖：I-5、I-6。

### A-5：纯人工回复回归
- 前置条件：不采用工作台草稿，直接写人工邮件。
- 操作步骤：填写主题/正文并发送。
- 预期结果：不要求 qaRuleIds，不执行 QA 事实校验；仍完成变量校验、SMTP 和 SEND_MANUAL_RICH_REPLY 审计。
- 覆盖：must-NOT-change、I-5。

### A-6：审计一致性
- 前置条件：工作台建议 3 条，运营最终选择 2 条并成功发送。
- 操作步骤：打开 QA 使用审计报表和邮件详情。
- 预期结果：mail_record_qa_rule 恰好 2 条且顺序按 request/evidence；报表 selected=2、removed=1；客户端篡改 suggestedRuleIds 不改变结果。
- 覆盖：I-7、interaction point。

### A-7：旧接口关闭
- 前置条件：准备有效 inbound ID。
- 操作步骤：分别调用 qa-reply、composed-reply、composed-reply/polish。
- 预期结果：均 HTTP 410；无邮件、mail_record、link、operator log 新增。
- 覆盖：I-8。

### A-8：响应式与目测
- 前置条件：桌面宽度 >960px 和移动宽度 <960px。
- 操作步骤：分别打开工作台，对照 S-1/S-2。
- 预期结果：桌面 facts 横跨顶部、draft/gaps 双列；移动依次 facts→draft→gaps；边框/圆角/颜色沿用现有实值，无横向溢出。
- 覆盖：S-1、S-2、S-3。
