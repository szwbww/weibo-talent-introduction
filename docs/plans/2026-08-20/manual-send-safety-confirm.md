# 人工回复内容安全门禁：由硬拦截改为「全量提示 + 分级二次确认」

- 计划日期：2026-08-20
- 计划类型：行为改造（后端门禁语义 + 前端确认流）
- 子系统数：2（后端 Kotlin / 前端 app.js）
- 变更文件数：10

---

## 需求描述

### Observable outcome

1. 运营在「未匹配来信详情 → 人工富文本回复」点击发送时，若正文命中任何内容安全门禁，**不再直接失败**：弹出确认框，**逐条列出本次命中的全部门禁**（中文说明 + 命中原句），运营点「确认执行」后可继续发送。
2. 命中**敏感材料类门禁**（正文索取护照 / 身份证 / 在职证明 / 银行流水）时，在第一道确认之后**再弹一次强确认框**，明确告知风险，运营必须在输入框中**逐字输入「确认发送」**才能放行；输入不匹配时弹窗不关闭并给出行内错误提示。
3. 门禁提示是**全集**：一次点击列出所有命中项，不再"改一条冒一条"。
4. 放行行为进入审计：`operator_action_log` 的 note 记录本次被覆盖的门禁码集合。

### What must NOT change

1. **自动回复链路**（`GroundedAutoReplyDecisionService` / `AutoMailReplyService`）的 fail-closed 行为完全不变，不得获得任何"已确认"旁路。
2. **退订抑制**（`PendingMailOperationService.kt:258-263`）保持硬拦截，与本次改造无关，且不得被任何确认参数绕过。
3. **幂等与投递态**分支（409 `DELIVERY_UNKNOWN` / 503 安全重试 / 422 永久失败，`:250-256`、`:360-378`、`:430-444`）行为不变。
4. **技术性校验**（主题非空 / 主题 ≤255 / 渲染后主题非空 / 最终校验文本 ≤20000 / `requireValidPlaceholders`）保持硬拦截，不进入确认流。
5. `qaRuleIds` 全部失效时的 422「所选的QA事实已全部失效，请重新选择」（`:173-178`）保持硬拦截。
6. 无门禁命中时的发送路径**零变化**：不弹任何框，一次请求发出。
7. `AiReplyActionPolicy.findViolations()` / `sanitize()` 的**检出逻辑**不变（只补 code 字段，不改判定）。

### Out of scope（显式推迟）

1. **让纯人工撰写（未采用 AI 草稿）的邮件也跑预检**。`schedulePreflightCheck()`（`app.js:9451-9454`）以 `aiReplyState.adoptContext` 为前置，且 `doPreflightCheck` 的陈旧性守卫（`app.js:9505-9512`）依赖 `adopt.draftId`；放开需要重做这套身份守卫（见 [[K-ai-preflight-stale-response-draft-identity]]），是独立一刀。本计划只保证"预检跑起来时结果与发送同源"。
2. **修 `.action-dialog` 的 `background: var(--panel-bg)` 半透明问题**（`styles.css:2618-2627`，见 [[K-panel-bg-token-is-translucent]]）。这是既有状况，本计划不新增浮层、不改 `styles.css`。执行 agent 不得顺手改。
3. `AiReplyActionPolicy.findViolations()` 里 `:145-151` 那段三分支等价的冗余 `if/else` 简化。
4. 敏感材料以外的门禁分级细化（例如给 `TRUST_RHETORIC` 单独等级）。
5. 预检响应体新增 severity 字段。

---

## 关键不变量

### Invariant I-1: 门禁结果是全集，不是首命中
- Rule: 单次发送请求的安全取证必须返回**本次命中的全部门禁**，按检测顺序去重后完整返回给客户端；任何 `return` 首个命中即中断的写法都违反本不变量。
- Applies to: `PendingMailOperationService.collectSafetyFindings()`（由现 `performFinalBlockingCheck()` `:691-775` 改造而来）、`sendManualRichReply()` 的抛出点（现 `:229-234`）。
- Violation consequence: 运营确认第一条后重试，第二条才冒出来，但前端只允许一轮确认（I-6），第二轮直接 alert 失败——退化为现状。
- 来源: original

### Invariant I-2: 动作违规必须携带非空稳定 code
- Rule: `AiReplyActionPolicy.findViolations()` 返回的每个 `ActionViolation` 的 `code` 必须非空。`code == null` 当前的唯一语义是"该动作不在 allowed 集合里"（证据见 `## 现状审计` → 动作门禁 → 证据 E-2），必须按 `violation.action` 映射为稳定码：`REQUEST_MATERIALS → AI_REPLY_ACTION_MATERIALS_NOT_ALLOWED`，`PROPOSE_MEETING → AI_REPLY_ACTION_MEETING_NOT_ALLOWED`。
- Applies to: `AiReplyActionPolicy.findViolations()`（`AiReplyActionPolicy.kt:123-152`）；消费端 `PendingMailOperationService.kt:768-771`、`:1022-1029`。
- Violation consequence: (a) 运营看到无意义的 `ACTION_VIOLATION`，无法判断改哪句；(b) 预检 `:1024` 的 `if (code != null && ...)` 会**静默丢弃**该违规，预检显示"无风险"而发送被拦（当前线上真实缺陷）。
- 来源: original

### Invariant I-3: 服务端是放行权威；两级确认互不替代
- Rule: 是否放行只由服务端依据**本次请求重新计算**的 findings 与请求中的确认参数决定：
  - `findings` 非空 且 `safetyWarningConfirmed != true` → 拦截；
  - `findings` 中存在 `severity == STRONG` 且 `strongConfirmationText?.trim() != "确认发送"` → 拦截。
  两条**同时**成立才放行。`strongConfirmationText` 单独正确不能替代 `safetyWarningConfirmed`，反之亦然。
- Applies to: `PendingMailOperationService.sendManualRichReply()`；`UnmatchedInboundMailController.sendManualRichReply()`（仅透传，不做判定）；`PendingManualRichReplyRequest`。
- Violation consequence: 前端成为唯一闸门，直接调 API 即可绕过强确认；违反 [[K-ai-review-server-authoritative-snapshot]]。
- 来源: K-ai-review-server-authoritative-snapshot / K-ai-adopt-direct-send-no-residual-gates

### Invariant I-4: STRONG 等级的成员是封闭且唯一的
- Rule: `SafetySeverity.STRONG` 当前**有且仅有** `AiReplyActionPolicy.CODE_ACTION_SENSITIVE_MATERIAL` 一个成员；其余所有码一律 `NORMAL`。等级由服务端在构造 `SafetyFinding` 时判定，**不接受客户端传入的 severity**。
- Applies to: `collectSafetyFindings()` 的每一处 `findings +=`。
- Violation consequence: 客户端可把 STRONG 降级为 NORMAL，跳过强确认。
- 来源: original

### Invariant I-5: 预检与发送共用同一取证函数
- Rule: `preflightEditedAiReply()` 的安全取证部分与 `sendManualRichReply()` 必须调用**同一个** `collectSafetyFindings()`，输入文本不同（发送用 `finalValidationText` = 渲染后主题+正文+HTML；预检用 `textBody`），检测项与顺序不得各写一份。预检特有的证据源检查（`AI_REPLY_PREFLIGHT_SOURCE_CHANGED` / `AI_REPLY_PREFLIGHT_NO_EVIDENCE`）不属于安全取证，保留在预检本地。
- Applies to: `PendingMailOperationService.kt:691-775`（发送侧）与 `:976-1029`（预检侧）。
- Violation consequence: 预检与实发漂移，运营据错误预检调正文（[[K-preview-mirrors-pipeline]]）。当前已漂移两处，见 `## 现状审计` 证据 E-4。
- 来源: K-preview-mirrors-pipeline

### Invariant I-6: 最多一轮确认后重试
- Rule: 前端对同一次发送最多重试**一次**（携带确认参数）。若携带确认参数的请求仍被服务端拦截，直接展示失败，不得再次弹窗、不得递归。
- Applies to: `app.js` `submitManualRichReply()`（`:10351-10401`）。
- Violation consequence: 服务端事实在两次请求之间变化时（QA 规则被停用等）出现弹窗死循环。
- 来源: original（沿用现有 `safetyWarningConfirmed` 递归一次的语义）

### Invariant I-7: 响应与审计的有界性
- Rule:
  - HTTP 响应中 `findings` 最多 20 条，每条 `sentence` 最多 200 字符（超出截断并置 `truncated = true`）；响应**不得**包含 SMTP `errorDetail` 或任何服务端诊断信息。
  - 审计 note **只记录码**（最多 10 个，超出以 `+N` 标记总数），**不得**写入命中原句或任何可替代正文的字段。
- Applies to: `GlobalExceptionHandler` 的新 handler；`PendingMailOperationService.kt:307-312` 的 note 构造。
- Violation consequence: 违反 [[K-review-event-audit-payload-bounds]] 与 [[K-manual-send-error-response-opaque]]。
- 来源: K-review-event-audit-payload-bounds / K-manual-send-error-response-opaque

### Invariant I-8: 阻断异常必须由专用 handler 映射为 422 且携带 findings
- Rule: `ManualSendSafetyBlockedException` 必须由 `GlobalExceptionHandler` 的**专用** `@ExceptionHandler` 处理，返回 HTTP 422 与形如 `{code, message, detail, requiresStrongConfirmation, truncated, findings[]}` 的响应体；`code` 固定为 `MANUAL_SEND_SAFETY_BLOCKED`。响应体必须保留 `code`/`message`/`detail` 三字段以兼容 `app.js:1464` 的 `data?.message` 读取。
- Applies to: `GlobalExceptionHandler.kt`；`PendingMailOperationService` 的异常定义。
- Violation consequence: 落到 `@ExceptionHandler(Exception::class)`（`GlobalExceptionHandler.kt:38-40`）后 findings 丢失，运营永远拿不到确认机会（[[K-custom-exception-http-status-mapping]]）。
- 框架行为说明：本仓库已有先例 `AnalysisFailedException : RuntimeException`（`document/service/AnalysisFailedException.kt:3`）+ 专用 handler（`GlobalExceptionHandler.kt:26-28`），新异常照抄该形态。**但仓库内无任何测试断言过"专用 handler 确实被 Spring 选中"**，因此本计划用 A-8 做一次真实 HTTP 实测兜底，前端也不以 HTTP 状态码作为判据（见 I-9）。
- 来源: K-custom-exception-http-status-mapping

### Invariant I-9: 前端识别依据是响应体，不是 HTTP 状态码
- Rule: 前端判定"这是一次可确认的安全拦截"的唯一依据是 `e.data?.code === "MANUAL_SEND_SAFETY_BLOCKED"` 且 `Array.isArray(e.data.findings)`；**不得**用 `e.status === 422 || e.status === 500` 作为判据，也不得再从 `e.message` 正则抠码。
- Applies to: `app.js` `api()`（`:1455-1470`，需新增 `error.data = data`）与 `submitManualRichReply()`。
- Violation consequence: 状态码由框架决定（现状是 500，见证据 E-5），把状态码写进判据会让前端行为随框架配置漂移。
- 来源: original

### Invariant I-10: 自动路零暴露
- Rule: `safetyWarningConfirmed` / `strongConfirmationText` 只出现在人工发送的请求 DTO、controller 透传与 `sendManualRichReply()` 签名上。`collectSafetyFindings()` **不得**接收任何"是否已确认"参数；确认判定必须发生在调用方（`sendManualRichReply`）。
- Applies to: `collectSafetyFindings()` 签名；所有自动回复调用方。
- Violation consequence: 自动路径将来复用该函数时可能传入确认旁路，破坏 fail-closed。
- 来源: K-auto-reply-decide-context-parity

---

## 样式契约

前端仅改 `app.js`。**本计划不修改 `styles.css`，不新增任何 CSS class，不使用 inline style**（既有 `openActionDialog` 渲染分支里的 inline style 属既有代码，不动）。所有新增 DOM 复用既有 class。

### S-1: 门禁列表（第一道确认框正文）
- 复用（不得自造近似样式）：
  - `.ai-reply-feedback` — `styles.css:6138-6143`（`display:flex; flex-direction:column; gap:6px; margin-bottom:10px`），作为列表容器。
  - `.ai-reply-warning` — `styles.css:6154-6162`，每条 NORMAL 门禁一个盒子。
  - `.ai-reply-error` — `styles.css:6164-6172`，STRONG 门禁用它以示区分。
  - `.ai-reply-coverage` — `styles.css:6145-6152`，用于"命中原句"的次级灰底行。
- 新增 CSS：**无**。
- DOM 结构（`options.message` 的最终 HTML，逐字骨架）：

```html
<p>本次发送命中 N 项内容安全门禁，请逐条核对后确认：</p>
<div class="ai-reply-feedback">
  <div class="ai-reply-warning">中文门禁说明</div>
  <div class="ai-reply-coverage">命中原句：……</div>
  <div class="ai-reply-error">敏感材料类门禁的中文说明</div>
  <div class="ai-reply-coverage">命中原句：……</div>
</div>
<p>确认已人工核对，仍要发送吗？</p>
```

- 渲染规则：`severity === "STRONG"` 用 `.ai-reply-error`，否则 `.ai-reply-warning`；`sentence` 为空则不输出对应的 `.ai-reply-coverage` 行；全部文本经 `escapeHtml()`（`app.js:1487`）。
- 禁止项：inline style；新 class；改 `.action-dialog` 及其子规则（`styles.css:2618-2653`）。

### S-2: 强确认框（第二道）
- 复用：
  - `openActionDialog` 既有 `type: "html"` 字段渲染分支（`app.js:12000-12001`）承载风险说明。
  - 既有 `else` 分支的文本输入渲染（`app.js:12029-12036`），即 `type: "text"` 字段，自带 `class="input"` 与既有 inline style，**逐字沿用该分支，不另写 HTML**。
  - `.ai-reply-error` — `styles.css:6164-6172`，承载"输入不匹配"的行内校验提示。
- 新增 CSS：**无**。
- DOM 结构（message 部分）：

```html
<div class="ai-reply-error">高风险：本封邮件正文向专家索取护照 / 身份证 / 在职证明 / 银行流水一类敏感证件材料。此类索取存在合规与信任风险，一经发出不可撤回。</div>
<div class="ai-reply-feedback">
  <div class="ai-reply-error">敏感材料类门禁的中文说明</div>
  <div class="ai-reply-coverage">命中原句：……</div>
</div>
<p>确认要发送，请在下方输入框中逐字输入「确认发送」四个字。</p>
```

- 校验提示节点：由 `openActionDialog` 在 `schema.validate` 存在时渲染，逐字骨架：

```html
<div class="ai-reply-error" id="dialog_validationError" hidden></div>
```

- 交互规则：**不禁用 submit 按钮**（避免共用弹窗的 `disabled` 残留，见 [[K-shared-action-dialog-cleanup]]）。提交时若 `validate()` 返回非空字符串，则把该字符串写入 `#dialog_validationError` 并 `hidden = false`，**直接 return，不调用 `cleanup()`、不 `resolve()`**，弹窗保持打开。
- 禁止项：给共用 submit 按钮设 `disabled`；给共用 form / select / input 追加未在 `cleanup()` 中成对移除的监听器；inline style；新 class。

### S-3: 共用弹窗清理契约
- `openActionDialog` 的 `cleanup()`（`app.js:12061-12066`）现移除 submit 与 cancel 两个监听器并 `dialog.close()`。本次改造**不新增任何监听器**（校验在既有 `handleSubmit` 内同步完成），因此 `cleanup()` 主体不变；仅需在 `handleSubmit` 的校验失败分支跳过 `cleanup()`。
- 禁止项：为实现校验而新增 `input` / `keyup` 监听器。

---

## 现状审计

### 存储：无新增存储；本计划不新增表、字段、索引、迁移

唯一被写入的是既有审计表 `operator_action_log`（`V19__add_operator_status_and_action_log.sql:43` — `note TEXT NULL`），且只改写 note 字符串内容，不改结构。

### 门禁 1：人工富文本发送的内容安全取证

**唯一取证函数**：`PendingMailOperationService.performFinalBlockingCheck()`，`src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt:691-775`。

**证据 E-1 — 调用点唯一：**
```
$ grep -rn "performFinalBlockingCheck\|isOverridableManualSafetyWarning" src/main --include=*.kt
src/.../PendingMailOperationService.kt:218:        val blockingCode = performFinalBlockingCheck(
src/.../PendingMailOperationService.kt:227:            safetyWarningConfirmed && isOverridableManualSafetyWarning(it)
src/.../PendingMailOperationService.kt:691:    private fun performFinalBlockingCheck(
src/.../PendingMailOperationService.kt:776:    private fun isOverridableManualSafetyWarning(code: String): Boolean =
```
即：**生产调用点恰 1 处**（`:218`），本函数为 private，无其他消费者。

**该函数可产出的码全集**（逐分支枚举，行号为现状）：

| 行 | 码 | 触发条件 | 可达性 |
|---|---|---|---|
| `:703` | `validatePlainText` 首码：`AI_REPLY_CLAIM_SOURCE_UNAVAILABLE` / `..._HALLUCINATED_FACT` / `..._MODALITY_STRENGTHENED` / `..._HIGH_RISK_UNBACKED` | `carriesQa && canonicalFactIds` 非空 | 可达 |
| `:703` | `CLAIM_VALIDATION_FAILED`（`?:` 兜底） | — | **不可达**：`validatePlainText`（`AiReplyHighRiskClaimValidator.kt:56-78`）两条 `valid=false` 出口都带非空 `warningCodes`（`:62-66` 单码；`:77` 由 `warnings.isEmpty()` 取反）。保留为防御性分支。 |
| `:706` | `QA_FACTS_ALL_INVALID` | `carriesQa && canonicalFactIds.isEmpty()` | **不可达**：`:173-178` 已先行抛出 422「所选的QA事实已全部失效，请重新选择」。保留为防御性分支，**不得**被当作"缺失门禁"或"死代码"报为缺陷。 |
| `:709-714` | `AI_REPLY_CLAIM_HALLUCINATED_FACT` / `AI_REPLY_CLAIM_HIGH_RISK_UNBACKED` | 纯人工（无 QA）分支 | 可达 |
| `:717-719` | `AI_REPLY_CLAIM_TRUST_RHETORIC` | 任意 | 可达 |
| `:727-732` | `AI_REPLY_CLAIM_CONFIDENTIALITY_SUBSTITUTE` | `hasBlockingTrust` 且命中 | 可达 |
| `:744-748` | `AI_REPLY_CLAIM_ROLE_DISCLOSURE_OMITTED` | agency./company. 意图且来源要求角色披露 | 可达 |
| `:751-757` | `AI_REPLY_CLAIM_ENTERPRISE_UNGROUNDED` | enterprise. 意图 | 可达 |
| `:768-771` | `AI_REPLY_ACTION_SENSITIVE_MATERIAL` / `AI_REPLY_ACTION_CV_PURPOSE_MISSING` / `AI_REPLY_ACTION_CV_OPTIONALITY_MISSING` / **裸 `ACTION_VIOLATION`** | 动作违规 | 可达 |

**结构缺陷**：`:691-775` 全部为 `return` 首命中，**天然只能产出 1 个码**（违反 I-1 的改造目标）。

**当前可覆盖白名单**（`:776-786`）恰 7 项，全部为 `AI_REPLY_CLAIM_*`：`HALLUCINATED_FACT` / `MODALITY_STRENGTHENED` / `HIGH_RISK_UNBACKED` / `TRUST_RHETORIC` / `CONFIDENTIALITY_SUBSTITUTE` / `ROLE_DISCLOSURE_OMITTED` / `ENTERPRISE_UNGROUNDED`。**动作类三码与 `SOURCE_UNAVAILABLE` 均不在其中**，即当前硬拦。

### 门禁 2：动作策略（`AiReplyActionPolicy`）

**证据 E-2 — 裸 `ACTION_VIOLATION` 的成因**（`AiReplyActionPolicy.kt:123-152`）：`findViolations()` 中，`violationCode` 仅由 `detectCvConditionViolation()`（`:328-341`）产出，而该函数首行 `if (AiReplyAction.REQUEST_MATERIALS !in allowed) return null`（`:329-331`）。因此：

- 动作**在** allowed 中 → 可能得到 `CV_PURPOSE_MISSING` / `CV_OPTIONALITY_MISSING`；
- 动作**不在** allowed 中 → `code` 恒为 `null`；
- `PROPOSE_MEETING` 的 `violationCode` 恒为 `null`（`:140-143` 的 `when` 只处理 `REQUEST_MATERIALS`）。

于是 `PendingMailOperationService.kt:770` 的 `violations.first().code ?: "ACTION_VIOLATION"` 只在"动作未被授权"时输出裸码。**这正是本次线上报错的成因。**

**证据 E-3 — 补 code 不会影响其他消费者。** `findViolations` 的全部调用点：
```
$ grep -rn "findViolations" src/main --include=*.kt
llm/service/AiReplyHighRiskClaimValidator.kt:47:  ...findViolations(normalized, emptySet()).isNotEmpty()
llm/service/AiReplyDraftService.kt:708:          ...findViolations(candidate, allowedActions).isNotEmpty()
llm/service/AiReplyDraftService.kt:1414:         val claimViolations = ...findViolations(answer.answer, allowedActions)
llm/service/AiReplyDraftService.kt:1452:         val actionViolations = ...findViolations(materialized.text, allowedActions)
llm/service/AiReplyDraftService.kt:1770:         var violations = ...findViolations(text, allowedActions)
llm/service/AiReplyDraftService.kt:1793:         violations = ...findViolations(text, allowedActions)
llm/service/TrustReplyWorkbenchService.kt:1353: ...findViolations(locked.answerText, allowedActions).isNotEmpty()
llm/service/TrustReplyWorkbenchService.kt:1367: ...findViolations(claim.text, allowedActions).isNotEmpty()
mail/service/PendingMailOperationService.kt:768:  val violations = ...findViolations(verificationText, restrictedActions)
mail/service/PendingMailOperationService.kt:1022: val violations = ...findViolations(textBody, restrictedActions)
```
共 10 处。除 `PendingMailOperationService` 两处外的 8 处，全部只消费 `isNotEmpty()` 或违规**条目本身**（`:1414`、`:1452`、`:1770/1793` 用于重试与 sanitize），**没有一处读取 `.code`**。给 `code` 填非空值不改变这 8 处行为。

`sanitize()` 与 `restrictForTrustState()` **不在本计划改动范围**（I-2 只改 `findViolations` 的 code 赋值）。

### 门禁 3：预检（`preflightEditedAiReply`）

位置 `PendingMailOperationService.kt:886-1042`。安全取证段落 `:976-1029`。

**证据 E-4 — 预检与发送的两处已存在漂移：**

1. **动作违规被静默丢弃。** `:1024-1029`：
```kotlin
violations.forEach { violation ->
    val code = violation.code
    if (code != null && code !in warningCodes) { warningCodes += code }
}
```
结合 E-2，`code == null`（动作未授权）的违规在预检里**完全不显示**。即本次线上这类正文，预检面板显示"未发现新增风险"，点发送被拦。

2. **纯人工（无 QA 事实）时预检更弱。** 预检 `:976` 无条件调 `validatePlainText(textBody, canonicalFactIds)`，而 `AiReplyHighRiskClaimValidator.kt:57-59` 在 `factRuleIds.isEmpty()` 时立即 `return ClaimValidationResult(valid = true)`；预检**没有**发送侧 `:709-714` 的 `containsHallucinatedNumberOrUrl(text, "")` / `containsUnbackedHighRiskDeclarations(text, "")` 兜底分支。

统一到 `collectSafetyFindings()` 后，(2) 会使**预检变严**：无事实的人工正文将新增显示"未经审核的数字或链接""高风险声明无依据"两类提示。这是本计划**有意的行为变化**，由 A-11 验收。

### 门禁 4：错误响应形态

**证据 E-5 — 现状 422 实际以 HTTP 500 抵达前端。** `GlobalExceptionHandler`（`common/controller/GlobalExceptionHandler.kt`）注册的 handler 为：`IllegalArgumentException`→400（`:14-16`）、`IllegalStateException`→400（`:18-20`）、`NoSuchElementException`→404（`:22-24`）、`AnalysisFailedException`→500 `ANALYSIS_FAILED`（`:26-28`）、绑定类异常→400（`:30-36`）、`Exception`→500 `INTERNAL_ERROR`（`:38-40`）。`ResponseStatusException` 无专用 handler，落入 `:38-40`，`message` 被透传为 `ex.message`，其值形如 `422 UNPROCESSABLE_ENTITY "发送内容安全校验未通过: ACTION_VIOLATION"`。

这与前端现状一致：`app.js:10375` 判 `(e?.status === 422 || e?.status === 500)`，`:10373` 用正则从 message 抠码。

**证据 E-6 — `api()` 丢弃响应体。** `app.js:1455-1470`：
```javascript
const message = data?.message || `${response.status} ${response.statusText}`;
const error = new Error(message);
error.status = response.status;
throw error;
```
`data` 的其余字段全部丢失。全仓 `async function api(` **恰 1 处定义**（`grep -c "^async function api(" src/main/resources/static/app.js` → `1`）。新增 `error.data = data` 为纯增量，既有 catch 只读 `message` / `status`，不受影响。

**先例**：`AnalysisFailedException`（`document/service/AnalysisFailedException.kt:3`）为 `: RuntimeException`，配 `GlobalExceptionHandler.kt:26-28` 的专用 handler。新异常照抄此形态。仓库内**无**任何测试断言过 Spring 的 handler 选择结果（`grep -rln "GlobalExceptionHandler" src/test/kotlin` → 仅 `ComposeTemplateGateControllerTest.kt`、`QaRuleManagementControllerTest.kt`，两者都是 `@WebMvcTest` + `@Import`，断言的是 404/422 业务码而非 handler 选择本身）。故 I-8 由 A-8 实测兜底。

### 门禁 5：审计写入

`PendingMailOperationService.kt:285-312` 调 `ManualReplySendAttemptService.recordSendAudit(...)`，其中 `note` 由 `:307-312` 构造，已包含单个覆盖码：
```kotlin
note = buildString {
    append("Manual rich reply sent for inbound processing $inboundProcessingId")
    if (overriddenSafetyWarning != null) {
        append("; safety warning manually confirmed: ")
        append(overriddenSafetyWarning)
    }
}
```
`recordSendAudit` 签名见 `ManualReplySendAttemptService.kt:340-353`；`note` 最终落 `operator_action_log.note`（`OperatorActionLogService.record` 第 28 参数 → `:41`）。**本计划只改 note 字符串内容，不改 `recordSendAudit` 签名，故 `ManualReplySendAttemptService.kt` 不进变更清单。**

### 交互点（Interaction points）

| # | 写入方 | 读取方 | 本计划影响 |
|---|---|---|---|
| IP-1 | `AiReplyActionPolicy.findViolations()` 产出的 `code` | `PendingMailOperationService:768-771`（发送）与 `:1022-1029`（预检） | 补 code 后，预检不再丢弃动作违规；发送侧不再输出裸 `ACTION_VIOLATION` |
| IP-2 | `collectSafetyFindings()` 的 findings 全集 | 发送侧的拦截判定 + 预检侧的 `warningCodes` | 两侧同源（I-5）；预检对纯人工正文变严 |
| IP-3 | 服务端 422 响应体 `findings` | `app.js` `api()` → `submitManualRichReply()` 弹窗 | `api()` 必须透出 `error.data`（I-9），否则前端拿不到 findings |
| IP-4 | 前端 `strongConfirmationText` | 服务端 STRONG 判定 | 服务端必须自行比对「确认发送」四字（I-3），不接受布尔 |
| IP-5 | 覆盖码集合 | `operator_action_log.note` | 有界写入（I-7） |
| IP-6 | 新增服务参数 `strongConfirmationText` | `UnmatchedInboundTrustWorkbenchTest.kt:172-191` 的 Mockito stub | stub 未列该参数时走 Kotlin `$default` 合成方法，默认 `null` 与 controller 实传一致；本计划仍显式更新该 stub 以断言透传 |

### 前端样式盘点

- **可复用 class**（本计划全部复用，零新增）：
  - `.ai-reply-feedback` — `styles.css:6138-6143` — flex 纵向容器，`gap: 6px`
  - `.ai-reply-warning` — `styles.css:6154-6162` — 琥珀色警告盒
  - `.ai-reply-error` — `styles.css:6164-6172` — 红色错误盒
  - `.ai-reply-coverage` — `styles.css:6145-6152` — 灰底次级信息行
  - `.action-dialog` / `.action-dialog-body` / `.action-dialog-footer` — `styles.css:2618-2653`
  - `.input`（由 `openActionDialog` text 分支 `app.js:12033` 输出）
- **设计基准 token 实值**（`styles.css:1-90`，仅供核对，不得改动）：
  - `--warning: #d97706`；`--warning-bg: rgba(217,119,6,0.08)`；`--warning-border: rgba(217,119,6,0.2)`
  - `--error: #e11d48`；`--error-bg: rgba(225,29,72,0.07)`；`--error-border: rgba(225,29,72,0.16)`
  - `--radius-sm: 7px`；`--radius-lg: 18px`；`--shadow-xl: 0 20px 48px -12px rgba(15,23,42,0.2), 0 4px 12px rgba(15,23,42,0.06)`
  - `--font-body: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Helvetica Neue', sans-serif`
  - 警告/错误盒字号 `12px`、行高 `1.5`、内边距 `8px 10px`
- **DOM 结构约定**：共用弹窗 `#actionDialog` 定义于 `index.html:1958-1967`，含 `#actionDialogForm`（`method="dialog"`）、`#actionDialogTitle`、`#actionDialogBody`、footer 两按钮（`[data-action='action-dialog-cancel']` 与无 id 的 `button[type="submit"]`，文案「确认执行」）。字段由 `ACTION_DIALOG_SCHEMAS`（`app.js:11926-11978`）驱动，`openActionDialog()`（`app.js:11980-12074`）按 `field.type` 渲染，`html` 类型直接注入 `options.message`（`:12000-12001`）。**本计划不改 `index.html`。**
- **改动前基线**（`app.js:10388-10391`，现状确认框正文，逐字）：
```javascript
const confirmed = await openActionDialog("confirm", {
    message: `<p>内容安全校验提示：</p><p>${escapeHtml(AI_REPLY_WARNING_LABELS[warningCode] || "正文包含需人工核对的风险声明")}</p><p>确认已人工核对，仍要发送吗？</p>`
});
```
- **既有状况，不得顺手改**：`.action-dialog` 的 `background: var(--panel-bg)` = `rgba(255,255,255,0.55)`（`styles.css:2624` + `:14`），半透明；但其 `::backdrop` 有 `blur(6px)`（`:2629-2633`），属既有设计，见 Out of scope 第 2 条。
- **无全局 `.button:disabled` 规则**，证据：
```
$ grep -n "button:disabled\|button\[disabled\]" src/main/resources/static/styles.css
6069:.ai-reply-stop-button:disabled {
6197:.compose-draft-actions .button:disabled,
6198:.compose-draft-actions .button:disabled:hover,
6199:.compose-draft-actions .button:disabled:active {
7816:.trust-reply-workbench .trust-reply-fact-chip > button:disabled {
9699:.contact-head-actions .button[disabled] {
```
5 处全部为局部作用域，`#actionDialogForm button[type="submit"]` **不在任何一条的作用域内**。这是 S-2 选择"不禁用按钮、改用行内校验"的直接依据（否则必须新增 CSS，且触碰 [[K-shared-action-dialog-cleanup]] 的 `disabled` 残留风险）。

---

## 实现方案

### 阶段 A：后端取证与放行语义（I-1 / I-2 / I-3 / I-4 / I-5 / I-10）

**任务 A-1｜给动作违规补稳定码**（遵守 I-2）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt`
- 在 object 尾部（`:409-411` 常量区）新增两个常量：
  - `const val CODE_ACTION_MATERIALS_NOT_ALLOWED = "AI_REPLY_ACTION_MATERIALS_NOT_ALLOWED"`
  - `const val CODE_ACTION_MEETING_NOT_ALLOWED = "AI_REPLY_ACTION_MEETING_NOT_ALLOWED"`
- 在 `findViolations()`（`:123-152`）构造 `ActionViolation` 时，凡 `violationCode == null` 一律按 `action` 兜底：`REQUEST_MATERIALS → CODE_ACTION_MATERIALS_NOT_ALLOWED`，`PROPOSE_MEETING → CODE_ACTION_MEETING_NOT_ALLOWED`。产出的 `code` 必须非空。
- **不得**改动 `MATERIAL_REQUEST` / `MEETING_REQUEST` / `SENSITIVE_MATERIAL` / `CV_PURPOSE` / `CV_OPTIONALITY` 任何正则，不得改 `detectDirectRequest` / `detectCvConditionViolation` / `sanitize` / `restrictForTrustState` 的判定逻辑（must-NOT-change 第 7 条）。
- 依据证据 E-3：其余 8 个调用点不读 `.code`，无需同步改动。

**任务 A-2｜取证函数改为返回全集**（遵守 I-1 / I-4 / I-5 / I-10）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
- 在文件尾部（`PendingMailSendResult` 等 data class 区，`:1050` 之后）新增：
  - `enum class SafetySeverity { NORMAL, STRONG }`
  - `data class SafetyFinding(val code: String, val severity: SafetySeverity, val sentence: String?)`
  - `class ManualSendSafetyBlockedException(val findings: List<SafetyFinding>) : RuntimeException("发送内容安全校验未通过")`（形态照抄 `AnalysisFailedException`，见 E-6）
- 将 `performFinalBlockingCheck()`（`:691-775`）改造为
  `private fun collectSafetyFindings(verificationText, carriesQa, canonicalFactIds, contact, inboundText, researchProfileSufficient): List<SafetyFinding>`：
  - 所有 `return <code>` 改为 `findings += SafetyFinding(...)`，**函数体内不得再有提前 return**；
  - 保留 `:706` 的 `QA_FACTS_ALL_INVALID` 与 `:703` 的 `CLAIM_VALIDATION_FAILED` 两个防御性分支（现状审计已论证不可达，**不是缺陷**）；
  - claim 校验分支由"取首码"改为收集 `claimValidation.warningCodes` **全部**；
  - severity 按 I-4 判定：仅 `AiReplyActionPolicy.CODE_ACTION_SENSITIVE_MATERIAL` 为 `STRONG`；
  - 动作违规的 `sentence` 取 `ActionViolation.sentence`，其余 finding 的 `sentence` 为 `null`；
  - 按 `code` 去重（同码保留首次出现，含其 sentence）；
  - **签名中不得出现任何确认参数**（I-10）。

**任务 A-3｜发送侧放行判定与抛出**（遵守 I-3 / I-6 / I-7）
文件：同上
- `sendManualRichReply()` 签名（`:129-145`）新增末位参数 `strongConfirmationText: String? = null`。
- `:218-234` 改为：
  ```
  val findings = collectSafetyFindings(...)
  val requiresStrong = findings.any { it.severity == SafetySeverity.STRONG }
  if (findings.isNotEmpty() && !safetyWarningConfirmed) throw ManualSendSafetyBlockedException(findings)
  if (requiresStrong && strongConfirmationText?.trim() != "确认发送") throw ManualSendSafetyBlockedException(findings)
  ```
  两条判定**均需保留**（I-3：互不替代）。
- 删除 `isOverridableManualSafetyWarning()`（`:776-786`）及 `overriddenSafetyWarning` 局部变量（`:226-228`）。
- `:307-312` 的审计 note 改为记录**被覆盖的码集合**，有界（I-7）：最多 10 个码，逗号连接；总数超过 10 时追加 `+N`。格式示例：
  `Manual rich reply sent for inbound processing 5; safety findings confirmed: AI_REPLY_ACTION_MATERIALS_NOT_ALLOWED,AI_REPLY_CLAIM_TRUST_RHETORIC`
  强确认发生时追加 `; strong confirmation typed`。**note 中不得出现命中原句**（I-7）。
- 保留 `:258-263` 退订抑制在 `prepareAndClaim` 之前的位置不变（must-NOT-change 第 2 条 / [[K-suppression-check-call-sites]]）。

**任务 A-4｜预检复用同一取证函数**（遵守 I-5）
文件：同上
- `preflightEditedAiReply()` 的 `:976-1029` 段整体替换为：调用 `collectSafetyFindings(textBody, factRuleIds.isNotEmpty(), canonicalFactIds, contact, inboundText, researchProfileSufficient)`，把返回的 `code` 依次并入 `warningCodes`（保持既有去重语义）。
- **保留**预检本地的 `AI_REPLY_PREFLIGHT_SOURCE_CHANGED`（`:916/:1013/:1021` 等处）与 `AI_REPLY_PREFLIGHT_NO_EVIDENCE`（`:973`）逻辑不变。
- `AiReplyPreflightResult` 结构不变（Out of scope 第 5 条）。
- 注意：`collectSafetyFindings` 内部需要 `selection`（`qaFactSelectionService.select(...)`）与 `hasBlockingTrust`，现状发送侧在 `:721-728` 自行计算、预检侧在 `:915-989` 已算过一次。改造后由 `collectSafetyFindings` 内部统一计算，预检侧原有的 `selection` 变量仍供 readiness 使用，**允许两次 select 调用**（`qaFactSelectionService.select` 为只读，见 `QaFactSelectionService.kt`），不要为省一次调用而把 selection 作为参数穿透——那会让发送/预检的输入面再次分叉。

**任务 A-5｜controller 透传**（遵守 I-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
- `PendingManualRichReplyRequest`（定义在 `PendingMailOperationService.kt:1065-1081`）新增字段 `val strongConfirmationText: String? = null`。
- `sendManualRichReply`（`:234-256`）新增一行透传 `strongConfirmationText = request.strongConfirmationText`。
- controller **不做任何门禁判定**（I-3）。

**任务 A-6｜异常映射**（遵守 I-7 / I-8）
文件：`src/main/kotlin/com/weibo/talentintroduction/common/controller/GlobalExceptionHandler.kt`
- 新增 handler（放在 `AnalysisFailedException` handler 之后、`handleRequestBinding` 之前）：
  ```
  @ExceptionHandler(ManualSendSafetyBlockedException::class)
  fun handleManualSendSafetyBlocked(ex: ManualSendSafetyBlockedException): ResponseEntity<ManualSendSafetyBlockedResponse>
  ```
  返回 `HttpStatus.UNPROCESSABLE_ENTITY`。
- 新增响应 DTO（与 `ApiErrorResponse` 同文件）：
  ```
  data class ManualSendSafetyBlockedResponse(
      val code: String = "MANUAL_SEND_SAFETY_BLOCKED",
      val message: String,
      val detail: String?,
      val requiresStrongConfirmation: Boolean,
      val truncated: Boolean,
      val findings: List<SafetyFindingResponse>
  )
  data class SafetyFindingResponse(val code: String, val severity: String, val sentence: String?)
  ```
- 有界化（I-7）：`findings` 最多 20 条，超出置 `truncated = true`；每条 `sentence` 截断至 200 字符（截断时以 `…` 结尾）。
- `message` 固定为中文可读串（例：`发送内容安全校验未通过，共 3 项，请在界面上逐条确认`），`detail` 用 `HttpStatus.UNPROCESSABLE_ENTITY.reasonPhrase`，与既有 `error()` helper 的字段语义一致。
- **不得**回显任何 SMTP 诊断（[[K-manual-send-error-response-opaque]]）。

### 阶段 B：前端确认流（I-6 / I-9 / S-1 / S-2 / S-3）

**任务 B-1｜`api()` 透出响应体**（遵守 I-9）
文件：`src/main/resources/static/app.js`
- `:1463-1468` 新增一行 `error.data = data;`（置于 `error.status = response.status;` 之后）。纯增量，证据 E-6。

**任务 B-2｜共用弹窗支持提交期校验**（遵守 S-2 / S-3）
文件：同上
- `ACTION_DIALOG_SCHEMAS`（`:11926-11978`）新增类型：
  ```
  "confirm-typed": {
      title: "高风险发送二次确认",
      fields: [
          { name: "message", label: "", type: "html", required: false },
          { name: "confirmText", label: "请输入「确认发送」", type: "text", required: true, placeholder: "确认发送" }
      ],
      validate: (result) => (String(result.confirmText || "").trim() === "确认发送" ? null : "输入不匹配，请逐字输入「确认发送」四个字。")
  }
  ```
- `openActionDialog()`（`:11980-12074`）：
  - 渲染阶段（`:12037` 之后、`bodyEl.innerHTML = html` 之前），若 `schema.validate` 存在，追加 S-2 契约中的 `#dialog_validationError` 节点；
  - `handleSubmit`（`:12045-12059`）在收集 `result` 之后、`cleanup()` 之前插入：若 `schema.validate` 存在且返回非空字符串，则写入 `#dialog_validationError`（`textContent` + `hidden = false`）并 `return`，**不 cleanup、不 resolve**；
  - `cleanup()`（`:12061-12066`）**保持原样**，不新增需要清理的监听器或 `disabled` 状态（S-3 / [[K-shared-action-dialog-cleanup]]）。

**任务 B-3｜发送失败改走 findings 确认流**（遵守 I-6 / I-9 / S-1 / S-2）
文件：同上
- 重写 `submitManualRichReply()`（`:10351-10401`）的 catch 分支：
  - 删除正则 `/\bAI_REPLY_CLAIM_[A-Z0-9_]+\b/`（`:10373`）与 7 码硬编码数组（`:10376-10386`）与 `e?.status === 422 || e?.status === 500` 判据（`:10375`）；
  - 新判据：`!safetyWarningConfirmed && e?.data?.code === "MANUAL_SEND_SAFETY_BLOCKED" && Array.isArray(e.data.findings) && e.data.findings.length > 0`；
  - 第一道：按 S-1 骨架渲染全部 findings，`openActionDialog("confirm", { message })`；取消（`null`）→ 直接 `throw e`，不 alert 成"发送失败"以外的内容；
  - 第二道：若 `e.data.requiresStrongConfirmation === true`，紧接着 `openActionDialog("confirm-typed", { message })`（S-2 骨架）；取消 → 终止；
  - 重试请求体：`{ ...requestBody, safetyWarningConfirmed: true }`，若走过第二道则再加 `strongConfirmationText: "确认发送"`；
  - 递归调用仍传入 `safetyWarningConfirmed = true` 作为第 3 个实参，保证**最多一轮**（I-6）；
  - 第二次仍失败 → 走既有 `alert("人工回复发送失败: " + e.message)` 并 `throw`。
- `e.data.findings[i].severity` 为 `"STRONG"` 时用 `.ai-reply-error`，否则 `.ai-reply-warning`（S-1）。

**任务 B-4｜补齐文案表**
文件：同上
- `AI_REPLY_WARNING_LABELS`（`:4148-4169`）新增两条：
  - `AI_REPLY_ACTION_MATERIALS_NOT_ALLOWED: "对方来信未提出提供材料，正文却主动索要简历/材料。请改为不索取，或先确认对方已表达提供意愿。"`
  - `AI_REPLY_ACTION_MEETING_NOT_ALLOWED: "对方来信未提出会面意向，正文却主动提议安排会议/Zoom。请改为不提议，或先确认对方已表达会面意愿。"`
- 该表已被 `renderPreflightResult`（`:4492`）复用，新增后预检面板同步受益（IP-1）。

### 阶段 C：测试

**任务 C-1｜动作码单测**
文件：`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicyTest.kt`
- 新增：`allowed = emptySet()` 时，含「could you please send me your CV」的文本产出 `code == CODE_ACTION_MATERIALS_NOT_ALLOWED`；含「schedule a Zoom meeting」的文本产出 `code == CODE_ACTION_MEETING_NOT_ALLOWED`。
- 回归：`:307/320/342/354/408` 现有的 `CODE_ACTION_SENSITIVE_MATERIAL` 断言必须仍然通过（补 code 不得改变敏感材料优先级）。

**任务 C-2｜门禁语义单测**
文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`
- 修改 `:229` `blocks on high risk content in final validation text`：断言抛出的是 `ManualSendSafetyBlockedException` 且 `findings` 非空。
- 修改 `:256` `sends after operator confirms claim warning`：保持"确认后发送成功"。
- 新增用例（至少 5 条）：
  1. 未授权索要简历的正文 → findings 含 `AI_REPLY_ACTION_MATERIALS_NOT_ALLOWED`，`safetyWarningConfirmed = true` 后发送成功（I-1 / I-2 / I-3）；
  2. 同时命中多类门禁的正文 → `findings.size >= 2`（I-1）；
  3. 含护照索取的正文 → `safetyWarningConfirmed = true` 但 `strongConfirmationText = null` 仍抛异常；`strongConfirmationText = "确认发送"` 且 `safetyWarningConfirmed = true` 才发送成功（I-3 / I-4）；
  4. `strongConfirmationText = "确认发送"` 但 `safetyWarningConfirmed = false` → 仍抛异常（I-3 互不替代）；
  5. 发送成功后审计 note 含被覆盖的码、**不含**命中原句（I-7）。
- 回归：`:296` / `:317` 退订用例、`:599` / `:618` 长度用例必须原样通过（must-NOT-change 第 2、4 条）。

**任务 C-3｜controller 透传测试**
文件：`src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt`
- `:172-219` 的 `manual rich reply still delegates to service`：stub 与断言显式带上 `strongConfirmationText`（IP-6）。

**任务 C-4｜异常映射单测（新文件）**
文件：`src/test/kotlin/com/weibo/talentintroduction/common/controller/ManualSendSafetyBlockedHandlerTest.kt`
- 直接实例化 `GlobalExceptionHandler()`，调用新 handler，断言：`statusCode == 422`；`body.code == "MANUAL_SEND_SAFETY_BLOCKED"`；findings 顺序与入参一致；21 条输入时只返回 20 条且 `truncated == true`；超 200 字符的 sentence 被截断（I-7 / I-8）。

**任务 C-5｜前端契约测试**
文件：`src/test/js/aiReplyReviewConfirmation.test.js`
- 改写 `:122-131` 的 `manual safety warnings require confirmation before a single retry`：
  - 删除对 `AI_REPLY_CLAIM_HALLUCINATED_FACT` 字面量与 `e?.status === 500` 的断言（[[K-ui-removal-retires-obsolete-contract-tests]]）；
  - 新断言：`submitManualRichReply` 源文本含 `MANUAL_SEND_SAFETY_BLOCKED`、`e.data`、`requiresStrongConfirmation`、`confirm-typed`、`strongConfirmationText`；**不含** `e?.status === 422` / `e?.status === 500`；
  - 新增：`api()` 源文本含 `error.data = data`；
  - 新增：`ACTION_DIALOG_SCHEMAS` 含 `"confirm-typed"` 且其 validate 中含 `"确认发送"`；
  - 新增：`AI_REPLY_WARNING_LABELS` 含两个新码；
  - 新增（S-1/S-2 断言）：渲染串含 `ai-reply-feedback` / `ai-reply-warning` / `ai-reply-error` / `ai-reply-coverage`，且 `submitManualRichReply` 与 `openActionDialog` 的源文本中**不含** `style="` 新增片段、不含 `disabled = true`。

---

## 变更文件清单

| # | 文件 | 子系统 | 变更类型 | 说明 |
|---|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt` | 后端 | 修改 | 两个新常量 + `findViolations` code 兜底（A-1） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 后端 | 修改 | 取证全集化、放行判定、异常/DTO 定义、预检复用、审计 note（A-2/A-3/A-4，DTO 字段 A-5） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 后端 | 修改 | 新参数透传（A-5） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/common/controller/GlobalExceptionHandler.kt` | 后端 | 修改 | 新 handler + 响应 DTO（A-6） |
| 5 | `src/main/resources/static/app.js` | 前端 | 修改 | `api()` 透出 data、弹窗校验钩子、确认流重写、文案表（B-1~B-4） |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicyTest.kt` | 后端 | 修改 | C-1 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | 后端 | 修改 | C-2 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt` | 后端 | 修改 | C-3 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/common/controller/ManualSendSafetyBlockedHandlerTest.kt` | 后端 | **新增** | C-4 |
| 10 | `src/test/js/aiReplyReviewConfirmation.test.js` | 前端 | 修改 | C-5 |

**明确不改**：`index.html`、`styles.css`、`ManualReplySendAttemptService.kt`、`AiReplyHighRiskClaimValidator.kt`、`AiReplyDraftService.kt`、`TrustReplyWorkbenchService.kt`、`GroundedAutoReplyDecisionService.kt`、`AutoMailReplyService.kt`、任何 `db/migration/*.sql`。

---

## 验证命令

> 本项目**必须**用 JDK 11（zulu-11），裸 `mvn` 会构建失败。JS 用例用系统 `node`（实测 v22.23.2）。

```bash
# 1) 本计划相关的后端测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=PendingMailOperationServiceTrustWorkbenchTest,AiReplyActionPolicyTest,UnmatchedInboundTrustWorkbenchTest,ManualSendSafetyBlockedHandlerTest

# 2) 本计划新增/修改的前端用例（前端权威门禁，实测可执行）
node --test src/test/js/aiReplyReviewConfirmation.test.js

# 3) app.js 语法检查
node --check src/main/resources/static/app.js

# 4) 全量回归
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 5) 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 6) 空白/换行卫生
git diff --check
```

通过判据：
- 命令 1、4：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`，且不得出现 `UnnecessaryStubbingException`。
- 命令 2：退出码 0，输出末尾 `# fail 0`（改造前基线实测为 `# tests 17 / # pass 17 / # fail 0`，改造后 tests 数应增加）。
- 命令 3：退出码 0，无输出。
- 命令 5：退出码 0，产出 `target/*.war`。
- 命令 6：退出码 0，无输出。

来源：
- 命令 1、4、5 — `CLAUDE.md`「Commands」章节与项目元信息 `test_command` / `build_command`。
- 命令 2、3 — 依据 [[K-js-test-invocation-surface]]（`verify.sh` 只跑一个文件，不可作前端门禁；`mvn test` 经 `pom.xml:185-234` 的 `exec-maven-plugin` 带 `node --test src/test/js/*.test.js`，但 `skipNodeTests` 在 `pom.xml:19-25` 未定义，覆盖情况属推断）。命令 2 已在本次计划撰写时**实测执行**，输出 `# tests 17 # pass 17 # fail 0`。

---

## 验收标准

### 不变量

- **I-1**：`grep -n "return " PendingMailOperationService.kt` 在 `collectSafetyFindings` 函数体范围内**只应命中末尾一处 `return findings`**（或 `findings.distinctBy{}`）。C-2 用例 2 断言 `findings.size >= 2`。
- **I-2**：C-1 两条新用例通过。另 `grep -rn "\"ACTION_VIOLATION\"" src/main` **应为空**（裸兜底码已消失）。
- **I-3**：C-2 用例 3、4 通过（两个确认参数互不替代）。`grep -n "strongConfirmationText" UnmatchedInboundMailController.kt` 只应出现在透传行，controller 内**无**任何 `if`/比较逻辑。
- **I-4**：`grep -n "SafetySeverity.STRONG" src/main/kotlin` 命中的赋值处**恰 1 个来源**，且其条件为 `CODE_ACTION_SENSITIVE_MATERIAL`。`SafetyFindingResponse.severity` 为出参，不在任何请求 DTO 中：`grep -n "severity" PendingMailOperationService.kt` 不得出现在 `PendingManualRichReplyRequest` 内。
- **I-5**：`grep -n "collectSafetyFindings" PendingMailOperationService.kt` **恰 3 处**（1 处定义 + 发送侧 1 处 + 预检侧 1 处）。预检段 `:976-1029` 原有的逐项检查代码已删除，不得与发送侧并存两份。
- **I-6**：`submitManualRichReply` 源文本中 `submitManualRichReply(` 的递归调用**恰 1 处**，且实参第 3 位为 `true`。
- **I-7**：C-4 的截断用例通过（21→20 且 `truncated == true`；sentence 超长被截）。C-2 用例 5 断言 note 不含原句。`grep -n "errorSummary\|errorDetail" GlobalExceptionHandler.kt` 应为空。
- **I-8**：C-4 断言 handler 返回 422 + `MANUAL_SEND_SAFETY_BLOCKED`。**运行期路由由 A-8 实测**。
- **I-9**：C-5 断言 `submitManualRichReply` 源文本不含 `e?.status === 422`、`e?.status === 500`、`AI_REPLY_CLAIM_` 正则；含 `e.data?.code` 判据。
- **I-10**：`collectSafetyFindings` 的形参列表中**不得**出现 `safetyWarningConfirmed` / `strongConfirmationText` / `confirmed` 任一标识符。

### 样式契约

- **S-1**：diff 中 `app.js` 新增的门禁列表渲染串逐字包含 `class="ai-reply-feedback"` / `class="ai-reply-warning"` / `class="ai-reply-error"` / `class="ai-reply-coverage"`，且 `git diff -- src/main/resources/static/styles.css` **为空**。
- **S-2**：`confirm-typed` schema 的 text 字段走既有渲染分支（diff 中不得出现新写的 `<input` 字符串）；`#dialog_validationError` 节点 class 为 `ai-reply-error`。
- **S-3**：`git diff -- src/main/resources/static/index.html` 与 `-- src/main/resources/static/styles.css` 均为空；`openActionDialog` 的 diff 中不得出现 `addEventListener` 净增、不得出现 `disabled = true`。
- 全局：`git diff -- src/main/resources/static/app.js | grep 'style="'` 的新增行（`+` 开头）应为空。

### 集成场景（跨交互点）

- **IP-1**：C-1 + C-2 用例 1 共同证明动作违规带码并被发送侧拦为可确认项。
- **IP-2**：I-5 的 grep 断言 + A-11 人工验收。
- **IP-3 / IP-4**：A-1 / A-2 人工验收；C-5 源文本断言。
- **IP-6**：C-3 通过，且全量测试（验证命令 4）中 `UnmatchedInboundTrustWorkbenchTest` 全绿、无 `UnnecessaryStubbingException`。

### 回归

- 执行「验证命令」节的命令 4（全量测试）通过。
- 执行「验证命令」节的命令 2、3 通过。
- 执行「验证命令」节的命令 5（构建）通过。
- 执行「验证命令」节的命令 6，无输出。

---

## 人工验收清单

> 验收环境：管理后台 → 收发件箱 / 未匹配来信详情 → 人工富文本回复区。
> 通用前置：存在一条已绑定专家联系人、状态非退订的未匹配来信记录（下称"来信 X"），且该专家邮箱可正常投递（或使用测试邮箱）。

### A-1: 未授权索要简历 → 提示后可确认发送
- 前置条件：来信 X 的正文中**不含**任何 CV / resume / 材料 / 简历字样（可用一封只写"Thanks for reaching out."的测试来信）。
- 操作步骤：
  1. 打开来信 X 详情，在人工富文本回复区填写主题（任意，如 `Re: Test`）。
  2. 正文逐字粘贴：
     `Dear Professor Soro,` / `Thank you for your reply.` / `Before arranging a Zoom meeting, could you please send me your CV so that I can learn more about your academic background and experience? After reviewing it, I would be happy to schedule a Zoom meeting for further discussion.` / `Best regards,`
  3. 点击发送。
- 预期结果：
  - 弹出标题为「确认操作」的弹窗，正文首行为 `本次发送命中 N 项内容安全门禁，请逐条核对后确认：`（N ≥ 1）。
  - 列表中出现琥珀色警告框，文案为：`对方来信未提出提供材料，正文却主动索要简历/材料。请改为不索取，或先确认对方已表达提供意愿。`
  - 该警告框下方出现灰底行，内容以 `命中原句：` 开头，其后为 `Before arranging a Zoom meeting, could you please send me your CV…` 这一句。
  - **不再**出现 `422 UNPROCESSABLE_ENTITY "发送内容安全校验未通过: ACTION_VIOLATION"` 这类原始错误串。
  - 点「确认执行」后，**不再弹第二个框**，alert 显示 `人工回复邮件发送成功`。
- 覆盖：需求 1、需求 3、I-1、I-2、I-3、IP-1、IP-3

### A-2: 敏感材料 → 强确认，必须逐字输入「确认发送」
- 前置条件：同 A-1。
- 操作步骤：
  1. 正文中加入一句：`Please also send your passport copy for verification.`
  2. 点击发送 → 在第一道确认框点「确认执行」。
  3. 在第二个弹窗的输入框中输入 `确认` 后点「确认执行」。
  4. 清空输入框，输入 `确认发送` 后点「确认执行」。
- 预期结果：
  - 第一道确认框中，敏感材料那条以**红色**框展示（其余为琥珀色）。
  - 第二个弹窗标题为 `高风险发送二次确认`，正文首个红色框文案为：`高风险：本封邮件正文向专家索取护照 / 身份证 / 在职证明 / 银行流水一类敏感证件材料。此类索取存在合规与信任风险，一经发出不可撤回。`，其下有 `请输入「确认发送」` 标签的输入框。
  - 步骤 3：弹窗**不关闭**，输入框下方出现红色提示 `输入不匹配，请逐字输入「确认发送」四个字。`
  - 步骤 4：弹窗关闭，alert 显示 `人工回复邮件发送成功`。
- 覆盖：需求 2、I-3、I-4、S-2、IP-4

### A-3: 多门禁同时命中 → 列表完整
- 前置条件：同 A-1。
- 操作步骤：正文同时包含（a）未授权索要简历句、（b）`Please rest assured that we guarantee everything.`（信任话术）、（c）敏感材料句，点击发送。
- 预期结果：第一道确认框列出 **≥3 条**门禁，各自带中文说明；其中敏感材料那条为红色框。逐条确认后一次发送成功（不出现"确认一条冒一条"）。
- 覆盖：需求 3、I-1

### A-4: 取消 → 不发送
- 前置条件：同 A-1。
- 操作步骤：正文用 A-1 的内容，点击发送 → 在第一道确认框点「取消」。再重复一次，在第二道（用 A-2 正文）点「取消」。
- 预期结果：两次都 alert `人工回复发送失败: …`，且**收件箱/发件箱中不出现该封外发记录**，专家未收到邮件。
- 覆盖：I-3、I-6

### A-5: 无门禁 → 直接发送（回归）
- 前置条件：同 A-1。
- 操作步骤：正文写 `Dear Professor Soro,` / `Thank you for your reply. We will keep you posted.` / `Best regards,`，点击发送。
- 预期结果：**不弹任何确认框**，直接 alert `人工回复邮件发送成功`。
- 覆盖：must-NOT-change 第 6 条

### A-6: 退订收件人仍硬拦（回归）
- 前置条件：把测试专家邮箱加入退订抑制名单（后台「退订管理」或直接插 `email_suppression` 记录）。
- 操作步骤：用 A-1 的正文（会命中门禁）点击发送，在确认框点「确认执行」。
- 预期结果：发送失败，错误文案含 `收件人已退订，禁止外发`；**确认动作不能让它发出去**；发件箱无该记录。
- 覆盖：must-NOT-change 第 2 条、I-3

### A-7: 技术性校验仍硬拦（回归）
- 前置条件：同 A-1。
- 操作步骤：主题填入一个 260 字符的长串，正文任意，点击发送。
- 预期结果：直接失败（提示主题超长类错误），**不弹确认框**。
- 覆盖：must-NOT-change 第 4 条

### A-8: HTTP 响应形态实测（框架路由证实）
- 前置条件：浏览器打开 DevTools → Network。
- 操作步骤：重复 A-1 步骤 1-3，在 Network 中查看 `POST /api/mail/unmatched-inbound/{id}/manual-rich-reply` 的第一次请求。
- 预期结果：
  - HTTP 状态码为 **422**（不是 500、不是 400）。
  - 响应 JSON 含 `"code": "MANUAL_SEND_SAFETY_BLOCKED"`、`"requiresStrongConfirmation": false`、`"truncated": false`、`"findings": [...]`，每条 finding 含 `code` / `severity` / `sentence` 三字段。
  - 响应 JSON **不含** 任何 SMTP 诊断字段（无 `errorDetail` / `errorSummary` / `smtpResponseCode`）。
  - 第二次（确认后）请求的 body 含 `"safetyWarningConfirmed": true`；A-2 场景下还含 `"strongConfirmationText": "确认发送"`。
- 覆盖：I-7、I-8、I-9

### A-9: 审计留痕
- 前置条件：完成一次 A-2（强确认发送成功）。
- 操作步骤：进入「操作日志 / 任务记录」页，筛选该来信对应的 `SEND_MANUAL_RICH_REPLY` 或 `SEND_MANUAL_COMPOSED_REPLY` 记录，查看 note 字段。
- 预期结果：
  - note 含 `safety findings confirmed:` 及被覆盖的门禁码（如 `AI_REPLY_ACTION_SENSITIVE_MATERIAL`），并含 `strong confirmation typed`。
  - note **不含**邮件正文原句、不含收件人邮箱以外的任何正文片段。
- 覆盖：需求 4、I-7、IP-5

### A-10: UI 目测（对照样式契约）
- 前置条件：完成 A-3 的第一道确认框展示。
- 操作步骤：目测确认框，并用 DevTools 查看元素与计算样式。
- 预期结果：
  - 列表容器 class 为 `ai-reply-feedback`，条目间距 `gap: 6px`。
  - 普通门禁框：`class="ai-reply-warning"`，文字色 `rgb(217, 119, 6)`，字号 `12px`，圆角 `7px`，内边距 `8px 10px`。
  - 敏感材料框：`class="ai-reply-error"`，文字色 `rgb(225, 29, 72)`。
  - 命中原句行：`class="ai-reply-coverage"`，灰色小字。
  - 新增元素上**无** `style=` 属性。
  - 弹窗整体外观（宽度、圆角、阴影、按钮）与改造前**无肉眼差异**；「确认执行」按钮**始终可点**（不出现置灰）。
- 覆盖：S-1、S-2、S-3

### A-11: 预检与发送同源（有意的变严）
- 前置条件：来信 X 已能走 AI 草稿流程（有可用 QA 事实），且已在 AI 回复工作台生成并「采用到人工回复」一份草稿。
- 操作步骤：
  1. 在编辑器中把正文改成含 A-1 的未授权索要简历句，等待约 1 秒让预检自动触发。
  2. 观察编辑器上方的预检提示区。
  3. 再点击发送。
- 预期结果：
  - 预检区**出现**与 A-1 相同的中文提示（`对方来信未提出提供材料…`）。改造前此处显示"当前未发现新增风险，发送前仍请人工核对"。
  - 点发送后弹出的确认框，列出的门禁与预检区显示的**一致**（不多不少）。
- 覆盖：I-5、IP-2、Out-of-scope 第 1 条的边界说明

---

## 自查（Phase 4 checklist）

- [x] `## 关键不变量`：10 条，新枚举 `SafetySeverity`、新字段 `strongConfirmationText`、新码各有不变量覆盖
- [x] `## 现状审计`：门禁取证/动作策略/预检/错误响应/审计 5 个面，全部 grep 回执（E-1~E-6）
- [x] 每个新写入路径（findings→响应、码集合→note）均被 I-7 覆盖
- [x] 含前端改动 → `## 样式契约` 存在，S-1/S-2/S-3 覆盖全部新增 DOM
- [x] 样式契约无"与现有一致/参考 XX"类表述，全为 `file:line` 引用或逐字骨架
- [x] 新增 class：**0 个**（全部复用），故无需逐字 CSS 块；已给出"不改 styles.css"的可验证断言
- [x] 被修改的既有 class：**0 个**
- [x] `## 验证命令` 存在且排在 `## 验收标准` 之前
- [x] 每条命令带 `JAVA_HOME=` 前缀或为实测可执行的 `node` 命令，注明来源与判据
- [x] 新增测试类 `ManualSendSafetyBlockedHandlerTest` 已含在命令 1 的 `-Dtest=` 列表中
- [x] 验收标准/人工验收清单中"跑测试、构建通过"均引用 `## 验证命令`，全文无裸 `mvn test`
- [x] `## 人工验收清单` 11 条；需求 4 条 observable outcome 各有覆盖（A-1/A-2/A-3/A-9）
- [x] must-NOT-change 7 项：自动路(A-11 边界+I-10 grep)、退订(A-6)、幂等(全量回归)、技术性校验(A-7)、QA 全失效(全量回归 C-2 回归项)、无门禁零变化(A-5)、检出逻辑不变(C-1 回归)
- [x] 6 个 interaction point 均有跨路径验收项
- [x] 含 UI 目测项 A-10，对照实值
- [x] 每条 A-n 可黑盒执行，预期结果为实值
- [x] 变更文件数 10 ≤ 10
- [x] 子系统数 2 ≤ 2
- [x] 每个任务按号引用不变量/样式契约
- [x] 每条不变量与每条样式契约在验收标准中至少一条检查
- [x] 文件清单无"及相关文件""等"
- [x] Out of scope 显式列出 5 项
- [x] Phase 0 载入的知识：K-plan-quantified-claims-need-grep-receipts（贯穿现状审计）、K-custom-exception-http-status-mapping（I-8）、K-js-test-invocation-surface（验证命令）、K-shared-action-dialog-cleanup（S-2/S-3）、K-review-event-audit-payload-bounds（I-7）、K-manual-send-error-response-opaque（I-7）、K-preview-mirrors-pipeline（I-5）、K-ui-removal-retires-obsolete-contract-tests（C-5）、K-panel-bg-token-is-translucent（Out of scope 2，显式拒绝）、K-ai-review-server-authoritative-snapshot（I-3）、K-auto-reply-decide-context-parity（I-10）、K-sensitive-material-cta-not-mention / K-sensitive-action-span-granularity（must-NOT-change 7，显式拒绝改动检出逻辑）、K-ai-preflight-stale-response-draft-identity（Out of scope 1）、K-dom-stub-tests-hide-dangling-refs（不新增 id，C-5 源文本断言）——全部已用或已显式拒绝，无静默忽略
