# AI 回复补强第 8 步：LLM 失败契约与工作台禁用采用

## 需求描述

- 可观察结果：DeepSeek 超时、限流、网络异常、服务异常、空响应或结构/可信校验失败时，页面显示明确红色 `LLM 生成失败：<原因>`；fallback 明确标为“QA 规则参考内容”，而不是“可信草稿”。
- 可观察结果：LLM 未成功时，“采用到人工回复”和历史草稿采用按钮直接禁用；失败结果不锁定事实、不推进 `firstTurnDone`、不写入成功 operator turn，生成按钮显示“重试生成”。
- 可观察结果：“问题与依据”在生成前后按 `evidence snapshot → suggested rule displayName → sectionTitle → replySubject → 事实名称缺失` 解析名称，不再显示“未命名事实”或 rule ID。

必须保持不变：

1. 成功条件仍为 `usedLlm=true && generationState=LLM_USED`；成功草稿的 raw/rendered、继续修改、采用、变量预览和最终人工发送链保持现状。
2. 人工编辑器中的纯人工内容仍可走最终发送复验；本计划只禁止采用失败 fallback，不用历史 readiness、audit 或 draft identity 阻断人工发送。（来源：K-ai-generation-observability-not-send-gate、K-ai-preview-raw-adoption-boundary）
3. 自动发送继续由 `GroundedAutoReplyDecisionService` 的既有 `usedLlm + generationState + readiness` 门禁控制，任何 fallback 均不自动外发。
4. 浏览器继续提交稳定业务模型枚举；provider model id、`chat()`、`stitchDraft()` 兼容签名不变。（来源：K-reply-model-stable-enum-mapping）
5. 现有 30 秒 connect/read timeout 配置不改；日志不记录 API key、邮件正文、prompt 或模型原文。（来源：K-llm-timeout-fallback）

不在本计划范围：

- 不改 fallback 正文组织方式、QA 关键词/事实正文、意图目录或数据库。
- 不调整 30 秒超时值，不引入异步任务、流式响应、熔断器或第三方监控。
- 不改历史邮件截取策略、收件人变量或最终发送安全复验。
- 不新增 modal、人工确认勾选或发送审批状态。

## 关键不变量

### Invariant I-1：LLM 调用结果必须可分类且不泄密
- Rule：回复专用窄 seam 返回 `SUCCESS / TIMEOUT / RATE_LIMITED / NETWORK_ERROR / PROVIDER_ERROR / EMPTY_RESPONSE / CLIENT_UNAVAILABLE`；异常文本只进服务端有界日志，响应只携带稳定 warning code。日志仅允许 model、attempt、messageCount、总字符数、耗时、分类，不得记录消息内容、URL query、Authorization 或 API key。
- Applies to：`LlmDraftClient` 默认兼容实现、`HttpLlmDraftClient.executeChat`、`AiReplyDraftService` 首次生成与结构修复调用。
- Violation consequence：所有异常继续坍缩成“无有效响应”，运营无法区分超时；或敏感邮件/prompt 进入日志。
- 来源：original；K-llm-timeout-fallback、K-reply-model-stable-enum-mapping。

### Invariant I-2：重试预算有上限
- Rule：首次语义生成遇到 `TIMEOUT / RATE_LIMITED / NETWORK_ERROR / PROVIDER_ERROR / EMPTY_RESPONSE` 时立即重试一次；首次重试成功后不得保留失败 warning。若成功响应未通过结构/可信校验，仍只允许现有一次 correction call；单次用户操作最多 3 次 provider call。correction call 失败不得再做 transport retry。
- Applies to：QA_GROUNDED/QA_MATCHED 首轮；FREE_FORM 首次调用；现有 correction repair。
- Violation consequence：短暂抖动直接降级，或重试与结构修复叠加成无界调用，放大超时和费用。
- 来源：original；K-validation-exhaustion-must-block-readiness。

### Invariant I-3：稳定 warning code 是失败原因单源
- Rule：最终失败只追加一种 transport 主因：`AI_REPLY_LLM_TIMEOUT / AI_REPLY_LLM_RATE_LIMITED / AI_REPLY_LLM_NETWORK_ERROR / AI_REPLY_LLM_PROVIDER_ERROR / AI_REPLY_LLM_EMPTY_RESPONSE`；结构/可信失败继续使用既有校验 codes 和 `AI_REPLY_TRUST_REPAIR_EXHAUSTED`。`generationState` 保持现有四值，不增加 provider-specific 枚举。
- Applies to：`AiReplyDraftResult.contextWarnings`、两个 controller 既有映射、AI draft audit、前端 label map。
- Violation consequence：DTO/审计/前端出现互相漂移的新状态，或同一最终失败展示多个冲突原因。
- 来源：K-reply-model-stable-enum-mapping；original。

### Invariant I-4：失败结果永远不可采用
- Rule：任一 `usedLlm!=true` 或 `generationState!=LLM_USED` 的结果只能显示为内部参考；trust workbench 与旧 AI draft bubble 的采用按钮必须 disabled，采用 handler 还必须二次拒绝。失败不得更新 `lastDraftTemplate / lastRenderedDraft / lastQaRuleIds / mode / firstTurnDone / turns / lockedFactIds` 等成功会话状态。
- Applies to：`trust-generate-draft`、`trust-adopt-draft`、`ai-reply-turn`、`ai-adopt-draft`、`appendAiChatDraftBubble()`、`updateTrustWorkbenchButtons()`。
- Violation consequence：LLM 超时后的规则拼接正文被当作 AI 成功稿继续改写或直接采用，复现当前问题。
- 来源：original；K-ai-draft-review-state-per-draft（每个草稿必须自带采用边界，不能借最后一次 response）。

### Invariant I-5：失败不阻断人工编辑器的独立发送权
- Rule：I-4 只控制“从 AI 结果采用到编辑器”；运营仍可在人工编辑器自行输入内容并调用现有最终发送链。发送 endpoint 不新增 `generationState/usedLlm` 参数或历史草稿 gate。
- Applies to：前端 adopt handler 与现有 `send-manual-rich-reply` handler 的边界。
- Violation consequence：故障期间运营无法人工回复，或 AI 可用性变成发送审批权。
- 来源：K-ai-generation-observability-not-send-gate。

### Invariant I-6：失败 UI 必须先于覆盖/readiness 展示
- Rule：失败 banner 是 feedback 区第一项，固定两行：`LLM 生成失败：<中文原因>`；`当前显示的是 QA 规则参考内容，未经过 LLM 自然化；不可直接采用或发送。`。失败 warning code 不得在下方再次以 raw code 或重复中文出现；标题改为“QA 规则参考内容”，成功时恢复“可信草稿”。
- Applies to：训练模拟与收件箱共用的 `renderAiReplyFeedback()`、trust workbench 标题、toast。
- Violation consequence：运营先看到“依据完整/READY”而误判生成成功，或把 fallback 当可发送成稿。
- 来源：original。

失败原因映射固定如下，按“最终 transport warning → trust repair warning → generationState”优先取第一项，不允许自由改文案：

| 条件 | `<中文原因>` |
|---|---|
| `AI_REPLY_LLM_TIMEOUT` | `DeepSeek 请求超时` |
| `AI_REPLY_LLM_RATE_LIMITED` | `DeepSeek 请求过于频繁` |
| `AI_REPLY_LLM_NETWORK_ERROR` | `无法连接 DeepSeek` |
| `AI_REPLY_LLM_PROVIDER_ERROR` | `DeepSeek 服务异常` |
| `AI_REPLY_LLM_EMPTY_RESPONSE` | `DeepSeek 返回空内容` |
| `AI_REPLY_TRUST_REPAIR_EXHAUSTED` | `DeepSeek 返回内容未通过结构与可信边界校验` |
| `FALLBACK_LLM_DISABLED` | `LLM 功能未启用` |
| `FALLBACK_CLIENT_UNAVAILABLE` | `LLM 客户端不可用` |
| 其他 `FALLBACK_NO_RESPONSE` | `DeepSeek 未返回有效内容` |

### Invariant I-7：事实名称不依赖草稿存在
- Rule：问题依据标签解析顺序固定为：有效 evidence snapshot displayName；suggested/rulesByCategory 的 displayName；sectionTitle；replySubject；`事实名称缺失`。`未命名事实` 视为无效占位并继续回查；任何分支不得显示 rule ID、intent key、coverage key 或 warning code。
- Applies to：`renderComposedGapList()`、`renderComposedSelectedList()`、生成前 suggest 状态、生成后 evidence 状态。
- Violation consequence：生成前 evidenceSources 为空时所有依据都显示“未命名事实”，掩盖后台已有名称并降低可信度。
- 来源：K-ai-evidence-ui-no-rule-id-fallback（本次审计证明旧条目“只能使用 snapshot”已过时，Phase 6 修订）。

### Invariant I-8：既有审计只记录最终事实
- Rule：首轮失败仍允许现有 audit 记录 `generationState/usedLlm/warningCodes`；只记录最终失败主因，不记录第一次失败后成功重试的瞬态错误，不因 audit 写失败改变页面结果。
- Applies to：`AiReplyReviewAuditService` 既有 `contextWarnings` 消费路径和 `operator_action_log.after_value`。
- Violation consequence：质量统计把已恢复请求算成失败，或审计异常吞掉页面结果。
- 来源：original。

## 样式契约

### S-1：LLM 失败 banner
- 复用：`styles.css:5965` 的 `.ai-reply-feedback` 作为纵向容器；不修改其规则。全部使用点：`index.html:898` 训练模拟、`app.js:8760` trust workbench、`app.js:9183` 人工发送 preflight。新 banner 仅由 `renderAiReplyFeedback()` 写入前两个 AI feedback 容器，preflight 不生成该 class。
- 新增：以下规则逐字添加到 `styles.css`，不得改值：

```css
.ai-reply-failure-banner {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 10px 12px;
    border: 1px solid var(--error-border);
    border-left: 3px solid var(--error);
    border-radius: var(--radius-sm);
    background: var(--error-bg);
    color: var(--error);
    font-size: 12px;
    line-height: 1.5;
}

.ai-reply-failure-banner strong {
    font-size: 13px;
    font-weight: 700;
}

.ai-reply-failure-banner span {
    color: var(--text-main);
}
```

- DOM 结构：`renderAiReplyFeedback()` 只能生成以下层级；动态文本必须 `escapeHtml()`：

```html
<div class="ai-reply-failure-banner" role="alert">
    <strong>LLM 生成失败：DeepSeek 请求超时</strong>
    <span>当前显示的是 QA 规则参考内容，未经过 LLM 自然化；不可直接采用或发送。</span>
</div>
```

- 禁止项：inline style；渐变/图标/动画；新增未声明 class；修改 `.ai-reply-error` 或 `.ai-reply-warning` 近似替代。

### S-2：采用按钮 disabled 状态
- 复用：`styles.css:587` `.button`、`:610` hover、`:617` active、`:623` `.button.primary`、`:637` `.button.secondary`；DOM 继续使用 `button secondary`，不新造按钮 class。
- 新增：以下规则逐字添加；作用域只覆盖 `.compose-draft-actions` 中现有生成、采用两个按钮：

```css
.compose-draft-actions .button:disabled,
.compose-draft-actions .button:disabled:hover,
.compose-draft-actions .button:disabled:active {
    cursor: not-allowed;
    opacity: 0.45;
    transform: none;
    box-shadow: none;
}
```

- DOM 结构：保持 `app.js:8764-8767` 的 `.compose-draft-actions > button.button.primary + button.button.secondary`；失败后采用按钮必须同时具有原生 `disabled`、`aria-disabled="true"`、`title="LLM 生成失败，当前 QA 规则参考内容不可采用"`，生成按钮文案为“重试生成”。成功后采用按钮移除上述失败 title/aria 值并启用。
- 禁止项：用 opacity 的视觉假禁用替代原生 `disabled`；inline style；改动全局 `.button:disabled`；新增 retry 按钮。

### S-3：改动前 DOM 基线与标题切换
- 复用：`app.js:8750` `.compose-panel.compose-draft.ai-chat-panel`、`styles.css:5785` `.compose-rendered-preview`、`styles.css:5801` `.compose-draft-actions`，现有规则均就地不改。
- 改动前基线：

```html
<div class="compose-panel compose-draft ai-chat-panel">
    <h4>可信草稿</h4>
    <div class="ai-reply-model-row">
        <label>生成模型
            <select id="trustReplyModel" class="ai-reply-model-select"${llmDisabled ? " disabled" : ""}>
                <option value="DEEPSEEK_V4_FLASH">DeepSeek V4 Flash</option>
                <option value="DEEPSEEK_V4_PRO">DeepSeek V4 Pro</option>
            </select>
        </label>
    </div>
    <div id="trustReplyFeedback" class="ai-reply-feedback" role="status" aria-live="polite" hidden></div>
    <ul id="composedSelectedList" class="compose-selected-list"></ul>
    <textarea id="composedOperatorInstruction" class="compose-free-text" placeholder="可选：语气、长度、结构要求（不会作为事实写入正文）"${llmDisabled ? " disabled" : ""}></textarea>
    <div id="composedRenderedPreview" class="compose-rendered-preview pre"></div>
    <div class="compose-draft-actions">
        <button type="button" class="button primary" id="trustGenerateDraftBtn" data-action="trust-generate-draft" data-record-id="${recordId}"${llmDisabled ? " disabled" : ""}>生成可信草稿</button>
        <button type="button" class="button secondary" id="trustAdoptDraftBtn" data-action="trust-adopt-draft" data-record-id="${recordId}" disabled>采用到人工回复</button>
    </div>
</div>
```

- 目标结构：只给 `h4` 增加 `id="trustDraftHeading"`；成功文本“可信草稿”，失败文本“QA 规则参考内容”，空态文本“可信草稿”。其他层级不变。
- 禁止项：新增 wrapper、调整三栏布局、修改 textarea/模型选择器尺寸。

设计基准实值：主色 `#2563eb`；错误色 `#e11d48`；错误背景 `rgba(225, 29, 72, 0.07)`；错误边框 `rgba(225, 29, 72, 0.16)`；正文 `#1e293b`；圆角 `7px`；body `13px/1.5`；feedback gap `6px`；按钮高 `32px`。

## 现状审计

### LLM HTTP 调用与内存结果契约
- Schema/mapping：`LlmDraftClient.chat/chatWithModel` 返回 nullable String；`HttpLlmDraftClient.executeChat()` 在 API URL 空、body 空或任意异常时均返回 null。`AiReplyDraftResult` 已有 `usedLlm`、四值 `generationState`、`contextWarnings`，无需新增 DTO 字段。
- Write paths：
  1. `HttpLlmDraftClient.executeChat()` 写 nullable 调用结果；当前异常仅一条 message 日志。
  2. `AiReplyDraftService.generate()` 写 LLM disabled/client unavailable/free-form no-response 结果。
  3. `AiReplyDraftService.generateGrounded()` 写首次成功、首次空响应、一次结构修复成功/失败结果。
  4. `AiReplyDraftService.groundedFallbackResult()` 写 `usedLlm=false`、generationState、warnings、readiness、evidence snapshot。
- Read paths：
  1. `UnmatchedInboundMailController.aiReplyTurn()` 原样映射 `usedLlm/generationState/contextWarnings` 到页面响应。
  2. `AiTrainingController.simulate()` 原样映射相同字段到训练页。
  3. `GroundedAutoReplyDecisionService` 读取 `usedLlm/generationState/readiness`，fallback fail closed。
  4. `AiReplyReviewAuditService` 读取结果并将 generationState/usedLlm/warningCodes 写审计。
  5. `app.js` 的 feedback、trust workbench、旧 chat bubble 读取结果；当前采用逻辑不读失败状态。
- Interaction points：client 分类必须经 DraftService 转成最终 warning，再由两个 controller 既有字段传给 UI/audit；不得在 controller 重新猜异常。

### `operator_action_log`
- Schema/mapping：V19；`after_value` 为 TEXT JSON，无 JSON schema constraint；AI audit 截取 warning 最多 30 个、单项 200 字符。
- Write paths（全部）：唯一 repository save 为 `OperatorActionLogService.record()`；caller 为 `ExpertOperatorStatusService`、`ExpertIndexLevelOperationService`、`ExpertContactManagementService`、`BounceController`、`ManualReplySendAttemptService`、`UnmatchedInboundMailService`、`PendingMailOperationService`、`AiReplyReviewAuditService`。本计划只改变最后一条 caller 已有 `warningCodes` 的值集合，不改 store writer。
- Read paths（全部）：`OperatorActionLogService.search()`/后台 controller；`UnmatchedInboundMailController.getUnmatchedDetail()`；`QaRuleAuditService`；`OperatorActionLogRepository.findLatestAiDraftByInboundProcessingId()`。
- Interaction points：最终 failure warning 经 `AiReplyReviewAuditService.recordInitialDraft()` 写入；第一次失败后重试成功时 audit 不得看到瞬态 warning。

### 前端样式盘点
- `renderAiReplyFeedback()` 当前先渲染 readiness，再用通用 `.ai-reply-warning` 显示 `FALLBACK_NO_RESPONSE`，无法区分 timeout/限流/空响应。
- `trust-generate-draft` 当前任何 HTTP 200 都写 `lockedFactIds`、`lastDraft*`、`firstTurnDone=true`，因此 fallback 被当成功轮次。
- `updateTrustWorkbenchButtons()` 只检查 rendered/事实，不检查 `usedLlm/generationState`；`trust-adopt-draft` 无二次 guard。
- 旧 `appendAiChatDraftBubble()` 每个结果都创建可采用按钮，`ai-adopt-draft` 同样无失败 guard。（来源：K-ai-draft-review-state-per-draft）
- `renderComposedGapList()` 当前只查 `composedReplyState.draft.result.evidenceSources`；生成前 `draft=null`，即使 suggest 已返回 displayName，也写固定“未命名事实”。`renderComposedSelectedList()` 已会回查 suggest，两个区域逻辑漂移。
- 可复用 class：`.button/.primary/.secondary`（styles.css:587-645）；`.compose-rendered-preview`（5785-5793）；`.compose-draft-actions`（5801-5806）；`.ai-reply-feedback`（5965-5970）；`.ai-reply-warning`（5981-5989）；`.ai-reply-error`（5991-5999）。全部就地复用且不修改；只新增 S-1/S-2 的派生规则。
- `.ai-reply-feedback` 全部使用点：训练模拟 `index.html:898`、trust workbench `app.js:8760`、人工发送 preflight `app.js:9183`。新 banner 只进入前两个 AI 生成 feedback；preflight DOM/CSS 不变。
- 设计基准 token：主色 `#2563eb`；错误色 `#e11d48`；错误背景 `rgba(225, 29, 72, 0.07)`；错误边框 `rgba(225, 29, 72, 0.16)`；正文 `#1e293b`；圆角 `7px`；body `13px/1.5`；feedback gap `6px`；按钮高 `32px`。
- DOM 结构约定：目标区保持 `.compose-panel.compose-draft.ai-chat-panel`，内部顺序固定为 heading → model row → feedback → selected list → instruction textarea → preview → actions；按钮仍使用 `data-action` 事件委派。
- 改动前 HTML 基线为 S-3 中逐字骨架；相关既有 CSS 基线为：

```css
.compose-rendered-preview {
    min-height: 48px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    padding: 8px 10px;
    margin-bottom: 6px;
    white-space: pre-wrap;
}

.compose-draft-actions {
    display: flex;
    gap: 8px;
    justify-content: flex-end;
    margin-top: auto;
}

.ai-reply-feedback {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-bottom: 10px;
}

.ai-reply-warning {
    padding: 8px 10px;
    border: 1px solid var(--warning-border);
    border-radius: var(--radius-sm);
    background: var(--warning-bg);
    color: var(--warning);
    font-size: 12px;
    line-height: 1.5;
}

.ai-reply-error {
    padding: 8px 10px;
    border: 1px solid var(--error-border);
    border-radius: var(--radius-sm);
    background: var(--error-bg);
    color: var(--error);
    font-size: 12px;
    line-height: 1.5;
}
```

## 实现方案

### T1：增加回复专用 observed seam 与精确分类
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClient.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClientTest.kt`
- 在同文件定义稳定内部 outcome/failure enum，并在 `LlmDraftClient` 增加有默认实现的 `chatWithModelObserved()`；默认实现调用现有 `chatWithModel()`，保证文档分析、QA 提炼和全部 fake client 无需改签名。（I-1、I-3）
- `HttpLlmDraftClient` override observed seam：按 `ResourceAccessException` 的 timeout cause、HTTP 429、HTTP 5xx、其他网络/HTTP、空 body/content 分类；`chat/chatWithModel` 继续投影 `.content`，兼容旧 caller。（I-1）
- 增加结构化但无正文的日志参数：provider model、attempt 由 caller log、messageCount、contentChars、elapsedMs、failureType；禁止 request/response body 与凭据。（I-1）
- 测试覆盖成功、blank、read timeout、429、5xx、network exception、空 URL及旧三次 model 映射回归。

### T2：在 DraftService 收口重试预算与 warning
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- 增加单一 helper 执行首次 call + 一次 transient retry，返回最终 outcome 和总调用次数；grounded/free-form 首次生成共用，correction call 显式不重试。（I-1、I-2）
- 最终失败映射 I-3 warning；重试恢复不写 warning。结构首次 invalid + correction transport 失败同时保留首次 validation codes、最终 transport code 和 `AI_REPLY_TRUST_REPAIR_EXHAUSTED`。（I-2、I-3）
- 不增加 generationState；所有失败仍 `FALLBACK_NO_RESPONSE/usedLlm=false`，disabled/client unavailable 保持各自 state。（I-3）
- 测试覆盖调用数 1/2/3 边界、超时恢复、二次超时、429、empty、validation correction、FREE_FORM，并回归 auto gate 不因 retry 放宽。

### T3：传输与审计合同回归
- 文件：
  - `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`
- 用 DraftService stub/fake 返回带 failure warning 的结果，断言 `/ai-reply/turn` HTTP 200 保留 `usedLlm=false`、`generationState=FALLBACK_NO_RESPONSE`、稳定 warning；不出现异常 message/正文。（I-3、I-8）
- 捕获 `recordInitialDraft()` 入参，断言最终 warning 同源；重试成功场景不含瞬态 failure warning。（I-8）

### T4：失败 banner、禁用采用与会话状态隔离
- 文件：
  - `src/main/resources/static/app.js`
  - `src/main/resources/static/styles.css`
  - `src/test/js/aiReplyLoadingFeedback.test.js`
  - `src/test/js/trustReplyWorkbench.test.js`
  - `src/test/js/aiReplyReviewConfirmation.test.js`
- 增加 `isAiReplyGenerationSuccess()`、failure reason helper；`renderAiReplyFeedback()` 按 I-6/S-1 首项渲染 banner，并从普通 warnings 列表排除已消费 failure codes。（I-3、I-6；S-1）
- trust workbench 失败时仍显示参考正文，但标题、按钮和 title 按 S-2/S-3；handler 只在成功后提交会话状态，失败保留 instruction 供重试。（I-4；S-2、S-3）
- 旧 chat bubble 的 draft entry 保存自己的 `usedLlm/generationState`；失败 bubble 采用按钮 disabled，handler 二次 guard；失败不 append operator turn、不推进 firstTurnDone。（I-4）
- 采用 guard 不触碰 `send-manual-rich-reply`；回归纯人工编辑器可独立发送。（I-5）
- 抽出统一事实名称 resolver，删除固定 `UNNAMED_FACT_LABEL`；按 I-7 回查 suggest，非法/缺失最终显示“事实名称缺失”，永不显示 ID。（I-7）
- JS 测试更新旧的“generationState 不参与采用”和“无需 CSS”过时断言，新增 success/failure/前后草稿/名称解析/DOM/CSS 逐字合同。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClient.kt` | observed outcome、异常分类、兼容旧 seam |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 有界重试、稳定 warning、最终失败收口 |
| 3 | `src/main/resources/static/app.js` | failure banner、采用门禁、会话隔离、事实名称解析 |
| 4 | `src/main/resources/static/styles.css` | S-1/S-2 逐字样式 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClientTest.kt` | transport 分类与兼容测试 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | retry/warning/state 全矩阵 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt` | controller/audit warning 透传 |
| 8 | `src/test/js/aiReplyLoadingFeedback.test.js` | banner、错误文案、共享 feedback 合同 |
| 9 | `src/test/js/trustReplyWorkbench.test.js` | trust 按钮/状态/事实名称合同 |
| 10 | `src/test/js/aiReplyReviewConfirmation.test.js` | 成功采用与纯人工发送回归 |

边界：10 个文件，2 个子系统（LLM 失败契约、前端工作台），0 个 schema/共享 store 新字段。执行中若需 controller DTO、新表、新按钮或第 11 个文件，必须停下修订计划。

## 验收标准

- I-1：client test 对 timeout/429/5xx/network/empty 返回唯一分类；日志捕获测试/代码审查断言无 body、Authorization、apiKey；旧 `chat/chatWithModel` 行为与 model mapping 通过。
- I-2：首次 timeout 后成功总调用 2 且 `LLM_USED`；两次 timeout 总调用 2；首次 transport retry 成功但 JSON invalid 时 correction 后总调用不超过 3；correction timeout 不再重试。
- I-3：每种最终 transport failure 恰有一个主 warning；generationState 仍仅四值；HTTP/audit 看到稳定 code，不含 exception message。
- I-4：JS 测试断言两个采用入口失败均原生 disabled 且 handler 二次拒绝；失败前后 `firstTurnDone/turns/lockedFactIds/lastDraft*` 不变；成功路径仍更新。
- I-5：现有人工发送 handler request 不增加 `generationState/usedLlm`；手工输入的安全内容仍调用 `submitManualRichReply()`。
- I-6：失败 HTML 第一个 child 是 `.ai-reply-failure-banner`，两行文案逐字匹配；失败 code 不重复；标题为“QA 规则参考内容”；成功标题恢复。
- I-7：无 draft、有 draft 但 snapshot 缺名、snapshot 有名、suggest 仅 sectionTitle/replySubject、完全缺名五组断言；输出分别走指定优先级，源码/DOM 均无“未命名事实”、`规则 #`、rule ID。
- I-8：首次失败 audit warning 等于 response 最终 warning；重试成功 audit 不含失败 code；audit exception 仍返回页面结果。
- S-1：diff 断言新增三段 CSS 与契约逐字一致；DOM 层级/role/text 一致；无 inline style/未声明 class。
- S-2：CSS 与契约逐字一致；失败按钮含 disabled/aria/title，成功移除；无全局 disabled 规则改动。
- S-3：只新增 `trustDraftHeading` id，三栏层级、preview、textarea、模型 select 不变。
- 交互集成：`HttpLlmDraftClient outcome → AiReplyDraftService warning → controller response/audit → app banner/adopt gate` 全链有测试。
- 运行：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=HttpLlmDraftClientTest,AiReplyDraftServiceTest,UnmatchedInboundAiReplyTurnKnowledgeTest
node --test src/test/js/aiReplyLoadingFeedback.test.js src/test/js/trustReplyWorkbench.test.js src/test/js/aiReplyReviewConfirmation.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean test
npm test
git diff --check
```

## 人工验收清单

### A-1：DeepSeek 读取超时
- 前置条件：测试环境把回复模型 endpoint 配为连接成功但超过 `LLM_TIMEOUT_MS=30000` 不返回；准备一封已绑定专家、至少一条事实完整的入站邮件。
- 操作步骤：1. 打开收件箱详情；2. 点击“生成可信草稿”；3. 等待请求结束；4. 查看标题、feedback、按钮和 Network response。
- 预期结果：feedback 第一项红色显示 `LLM 生成失败：DeepSeek 请求超时`，第二行固定提示“当前显示的是 QA 规则参考内容，未经过 LLM 自然化；不可直接采用或发送。”；标题为“QA 规则参考内容”；正文可见；“采用到人工回复”disabled；生成按钮为“重试生成”；response 为 `usedLlm=false / FALLBACK_NO_RESPONSE / AI_REPLY_LLM_TIMEOUT`。
- 覆盖：I-1、I-3、I-4、I-6、S-1、S-2、S-3；需求第 1、2 条。

### A-2：短暂超时后重试成功
- 前置条件：LLM stub 第一次 timeout、第二次返回合法 Grounded JSON。
- 操作步骤：1. 点击生成一次；2. 查看 stub 调用数、页面 banner、标题和采用按钮；3. 点击采用。
- 预期结果：provider 调用恰好 2 次；页面无失败 banner/timeout warning，显示“模型已生成”；标题“可信草稿”；采用按钮启用；采用后人工编辑器得到 rendered 文本。
- 覆盖：I-2、I-3、I-4；必须保持不变第 1 条。

### A-3：结构/可信修复耗尽
- 前置条件：LLM stub 连续返回两次不合法 Grounded JSON 或未授权高风险 claim。
- 操作步骤：1. 点击生成；2. 查看调用数、feedback 和采用按钮；3. 再点生成重试。
- 预期结果：第一次操作 provider 恰好 2 次；红色标题为 `LLM 生成失败：DeepSeek 返回内容未通过结构与可信边界校验`；采用 disabled；第二次点击按首轮重新生成，未把失败正文作为 assistantDraft 发回。
- 覆盖：I-2、I-3、I-4、I-6。

### A-4：旧草稿入口不可绕过
- 前置条件：打开仍渲染旧 AI chat bubble 的页面入口；先生成成功稿，再制造一次 timeout fallback。
- 操作步骤：1. 查看两个 bubble；2. 点击成功稿采用；3. 尝试点击失败稿采用或用控制台触发其 action。
- 预期结果：成功稿可采用；失败稿按钮原生 disabled；强制触发 handler 显示错误且不改人工编辑器。
- 覆盖：I-4、K-ai-draft-review-state-per-draft interaction point。

### A-5：人工编辑发送不受 LLM 故障影响
- 前置条件：LLM 保持 timeout；人工编辑器准备一段通过最终发送复验的安全正文和主题。
- 操作步骤：1. 不采用 fallback；2. 直接在人工编辑器输入正文；3. 点击发送。
- 预期结果：发送请求不携带 generationState/usedLlm；按现有最终复验与 SMTP 流程处理，不出现“必须先有成功 AI 草稿”的阻断。
- 覆盖：I-5；必须保持不变第 2 条。

### A-6：生成前问题依据名称
- 前置条件：QA 建议接口返回一条 `displayName=项目总览` 的规则；尚未点击生成。
- 操作步骤：1. 打开详情；2. 查看“问题与依据”；3. 再生成一次成功草稿并复看。
- 预期结果：生成前后均显示 `依据：项目总览`；页面全文无“未命名事实”、`规则 #` 或数字 rule ID。若人为清空 displayName，依次显示 sectionTitle/replySubject；都空时仅显示“事实名称缺失”。
- 覆盖：I-7；需求第 3 条。

### A-7：样式目测
- 前置条件：桌面宽度与小于 960px 各打开一次 timeout 场景。
- 操作步骤：对照 S-1/S-2/S-3 查看 banner、按钮和三栏/单栏布局。
- 预期结果：banner 错误色 `#e11d48`、背景 `rgba(225, 29, 72, 0.07)`、左边框 3px、内边距 10px/12px、圆角 7px；disabled opacity 0.45、无 hover 位移；布局和模型选择器尺寸不变。
- 覆盖：S-1、S-2、S-3。

### A-8：自动发送回归
- 前置条件：自动回复测试入口分别返回 LLM_USED/READY 与 FALLBACK_NO_RESPONSE/READY。
- 操作步骤：分别执行自动回复 decision/preview。
- 预期结果：前者按既有门禁可发送；后者即使事实 readiness 为 READY 也不自动发送，reason 保持 AI generation unavailable/validation failed 的既有分类。
- 覆盖：必须保持不变第 3 条；LLM 结果与 auto gate interaction point。
