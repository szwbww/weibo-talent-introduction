# 04 · 一键预判编排（有据自动生成 / 无据机器代填 / 服务端汇总 / 可调可重置）

日期：2026-08-18
基线：`52380ab`（fast/auto-reply-convergence 已合入 main）
主计划：[00-auto-reply-convergence-master.md](./00-auto-reply-convergence-master.md)
子系统数：2（frontend + llm 后端）
文件数：9

## 先说结论：编排已经存在，缺的只有一步

`assemble()`（`trust-reply-workbench.js:917-1011`）**本来就是一键编排器**，顺序是：

```
canStartAssembly 闸门
  → 自动采用可采用的 GROUNDED 版本 + persistResolvedSnapshot()      (:921-950)
  → generateMissingGrounded(missingKeys)  逐项生成缺的有据项          (:952-956)
  → serializeResolvedVersion 全项快照                                (:957-965)
  → POST /api/trust-reply/workbench/assemble  服务端汇总             (:971-979)
```

按钮文案已经写好了这套语义（`trust-reply-workbench.js:1556-1564`，**逐字**）：

```js
            const assembleLabel = state.generation.pending && state.generation.stage === "ASSEMBLING"
                ? "整合中…"
                : state.generation.pending && readiness.pendingGeneration > 0
                    ? "生成并整合中…"
                    : readiness.unresolvedManual > 0
                        ? "服务端整合"
                        : readiness.pendingGeneration > 0
                            ? "生成有据回答并整合"
                            : "服务端整合";
```

**唯一卡住它的是 `unresolvedManual === 0` 这一条**（`computeReadiness()`，`trust-reply-workbench.js:885-890`，**逐字，无添加注释**）：

```js
            const canStartAssembly = !state.generation.pending
                && !state.stateSavePending
                && !state.frameSavePending
                && total > 0
                && unresolvedManual === 0
                && !state.requests.some((request) => request.pending);
```

而 `unresolvedManual` 的定义（`:879-881`）：

```js
} else if (request.coverage === "PARTIAL" || request.coverage === "UNSUPPORTED") {
    if (!serializeResolvedVersion(request)) unresolvedManualKeys.push(request.requestKey);
}
```

→ **PARTIAL / UNSUPPORTED 只要没人处理过，按钮就是禁用的。**
本计划要做的就是：让机器先把这些项处理掉（扮演操作员），然后原样调用既有 `assemble()`。
逐项生成的接缝也现成 —— `requestItemVersion(request, seq, generationId, "full", "ANSWER_WITH_EVIDENCE")`（`:671`），第 5 个参数就是 handling 覆盖。

## 需求方已确认的两点（2026-08-18）

1. **无依据项**：AI 模拟真人，依据现有知识库合成回答说明 → 填入提示词 → 再生成回复。
   即 `ANSWER_FROM_OPERATOR_INPUT` + 机器合成 instruction → `generateOperatorDirectedAnswer()`。**与本计划设计一致，无需调整。**
   但由此引出的证据边界见 I-0。
2. **代填说明不折叠**：填进 textarea，全文可见可改（S-2 已如此设计）。

另：原先向需求方提的「PARTIAL 项默认用 ANSWER_SUPPORTED_PART 还是 ACKNOWLEDGE_PENDING」是**多余的问题** ——
代码里 `recommendedHandling()`（`TrustReplyWorkbenchService.kt:1692-1696`）早已把 PARTIAL 定为 `ANSWER_SUPPORTED_PART`，
本计划沿用，不改。

## 需求描述

### Observable outcome

1. 工作台新增一个按钮，点一次跑完：有据项自动生成 → 无据项由机器代填回答说明并生成 → 服务端汇总 → 输出结论。
2. 结论区展示：判定（可自动发 / 转人工）、未通过的硬性闸门、汇总正文。**未触发硬性闸门时直接给出正文结论，不再要求人工先动手。**
3. 机器代填的每一项仍然可被人工就地覆盖（改处理方式、改说明、重新生成、换版本）—— 用的就是现有逐项控件，一行不改。
4. 新增「重置」按钮：清空本次编排产生的所有采用与说明，回到刚 bootstrap 的默认状态。

### What must NOT change

- `assemble()` 的编排顺序、`persistResolvedSnapshot()`、`generateMissingGrounded()`、`requestItemVersion()` 的既有实现。
- 服务端 `/assemble` 的 canonical 校验（X-4：所有字段来自同一 resolved version；hash 不符返回 422）。
- `validateItemHandling()` 的 status→handling 白名单（`AiReplyDraftService.kt:770-787`）。
- 训练宿主与正式宿主继续共用同一实现、同一按钮、同一编排；只有「完成」动作不同。
- **X-3 边界**：机器代填的说明仍走 `ANSWER_FROM_OPERATOR_INPUT`，该项仍是 `UNSUPPORTED`、`claims` 为空、不转 QA evidence、不因此获得自动发送许可。
- 本计划**不发送任何邮件**，不改 `AutoMailReplyService`。

### Out of scope

- **自动发送链路复用同一编排**（用户提的「后续开启自动发送也按这个流程」）→ 独立的 05 计划。理由见下节。
- CRS 阈值的实际取值与分档放行（03 已落 `tier='SHADOW'`，本轮不动）。
- `decide()` 接受工作台依据覆盖 —— 本计划的结论来自 `/assemble`，不经过 `decide()`，因此不阻塞；但第 2 页那个「预判」若要吃依据仍需单独处理。

## 关键不变量

### I-1: 机器代填只改「谁写的说明」，不改任何服务端契约
- Rule：机器代填产生的 `operatorInstruction` 必须走与人工完全相同的请求路径（`ADJUST_ITEM` + handling + instruction），服务端不新增"机器来源"分支，也不放宽任何校验。
- Applies to：新增的 `autoRun()`；`TrustReplyWorkbenchService.adjustItem()` 不改。
- Violation consequence：开出一条绕过 `TRUST_REPLY_LOCKED_ITEM_INVALID` 校验的旁路。
- 依据：`TrustReplyWorkbenchService.kt:1186-1193` 对 `ANSWER_FROM_OPERATOR_INPUT` 的校验是
  `instruction.isBlank() || instruction.length > 500 || locked.operatorInstructionHash != sha256Hex(instruction) || locked.answerText.isBlank() || locked.claims.isNotEmpty() || locked.generationKind != AI_GENERATED` →
  **服务端只校验"说明非空且哈希自洽"，不关心是谁写的**，所以机器代填天然合法，无需改后端校验。

### I-0: 建议说明只写「怎么答」，不写「答什么事实」
- Rule：机器合成的 `suggestedInstruction` 只允许描述**应答姿态与结构**（先说明没有确认口径 → 给出邻近事实的**名称** → 交出下一步、不承诺时间、不出现数字与链接）。**禁止**把 QA 规则的正文事实抄进说明里。
- Applies to：T1 的说明合成方法。
- Violation consequence：`ANSWER_FROM_OPERATOR_INPUT` 产出的版本 `claims` 必须为空——服务端强制（`TrustReplyWorkbenchService.kt:1190` 的 `locked.claims.isNotEmpty()` 即 422），且 `generateOperatorDirectedAnswer()` 的 prompt 把说明标为 `operator-provided answer basis`（`AiReplyDraftService.kt:672`），**说明即唯一权威**。所以任何被塞进说明的库内事实，都会以「无 claim、未经证据校验」的形态出现在对外邮件里——等于绕过了整套 claim 校验。
- 正确做法：若某条事实确实在库里，应当把它**绑定为该项的 fact**（该项随之变成 PARTIAL/GROUNDED，走证据生成与 claim 校验），而不是从说明里夹带。
- 来源：original（2026-08-18 与需求方确认无据项走「AI 模拟真人 + 按说明生成」后补充）

### I-2: 建议说明由服务端给出，不在前端硬编码
- Rule：每个非 GROUNDED 项的 `suggestedHandling` / `suggestedInstruction` 必须由后端在 bootstrap 响应里给出；前端只负责填进既有控件并发起既有请求。
- Applies to：`TrustReplyWorkbenchService.bootstrap()` 的 `requestCoverage` 项。
- Violation consequence：训练与正式各自在前端拼一套话术，口径又分叉——这正是前三份计划在消除的问题。
- 来源：K-ai-generate-single-freeform-seam（口径收口在 service 内）

### I-3: 机器代填必须是可见且可覆盖的，不能静默
- Rule：被机器代填的项必须打上可见标记（"机器代填"徽标），且该项的处理方式下拉、说明文本框、重新生成按钮保持可用；人工修改后标记消失。
- Applies to：`renderRequest()` 的 item 卡。
- Violation consequence：运营以为是自己写的，或以为无法改。

### I-4: 重置是回到 bootstrap 默认态，不是清库
- Rule：重置删除该 source 的 `trust_reply_workbench_state` 行并重新 bootstrap；**不得**删除 QA 规则、`reply_snippet`、`mail_record` 或任何 ES 文档。
- Applies to：新增的 `DELETE /api/trust-reply/workbench/state`。
- Violation consequence：一个"重置"按钮删掉运营的知识库。
- 依据：`TrustReplyWorkbenchStateStore.kt:84` 已有 `fun delete(sourceType: String, sourceId: Long, expectedStateVersion: Long): Boolean`，本计划只是把它暴露成端点，不新写删除逻辑。

### I-5: 硬性闸门不因编排完成而放行
- Rule：编排跑完、`/assemble` 返回正文，**不等于**可自动发送。结论区必须分别展示「汇总已完成」与「硬性闸门未通过」两件事。
- Applies to：新增的结论区渲染。
- Violation consequence：运营把"出稿了"读成"能自动发了"。
- 来源：主计划 X-2、K-preview-runtime-gates-visible

## 现状审计

### 前端编排（`trust-reply-workbench.js`）

| 位置 | 现有能力 | 本计划如何用 |
|---|---|---|
| `:868-903` `computeReadiness()` | 统计 `missingGroundedKeys` / `adoptableGrounded` / `unresolvedManualKeys` / `canStartAssembly` | 新增 `autoFillableKeys`（= `unresolvedManualKeys`），不改既有字段 |
| `:917-1011` `assemble()` | 采用→生成缺失有据→序列化→POST /assemble | **一行不改**，由 `autoRun()` 在其之前补齐无据项后调用 |
| `:652+` `generateMissingGrounded(allowlist, seq)` | 按 canonical order 逐项生成，含取消/陈旧/失败分支 | 作为 `autoFillManualItems()` 的实现范本（同构，不复制粘贴：抽公共循环） |
| `:671` `requestItemVersion(request, seq, generationId, "full", "ANSWER_WITH_EVIDENCE")` | 单项生成接缝，第 5 参为 handling 覆盖 | 直接复用，传 `ANSWER_FROM_OPERATOR_INPUT` / `ACKNOWLEDGE_PENDING` |
| `:1532-1544` `renderRequest()` | 逐项控件：处理方式 select、版本 select、说明 textarea、逐项动作 | 加一个「机器代填」徽标；控件本身不动（I-3） |
| `:1639-1641` `onClick` 分派 | `assemble` / `complete` / `cancel-generation` | 加 `auto-run` / `auto-reset` 两个分支 |

`data-action` 现有全集（已 grep）：`add-fact`、`assemble`、`cancel-generation`、`complete`、`next-page`、`prev-page`、`remove-fact`、`set-page`、`toggle-fact-picker`、`toggle-item` —— **没有聚合按钮，也没有重置**，两者都要新增。

### 服务端 handling 白名单（决定机器能选什么）

`AiReplyDraftService.kt:770-787`（下方为**压缩改写**便于阅读，非逐字；逐字原文为每个 `setOf(...)` 跨多行）：

```kotlin
GROUNDED    -> setOf(ANSWER_WITH_EVIDENCE, OMIT)
PARTIAL     -> setOf(ANSWER_SUPPORTED_PART, ACKNOWLEDGE_PENDING, OMIT)
UNSUPPORTED -> setOf(ANSWER_FROM_OPERATOR_INPUT, ACKNOWLEDGE_PENDING, OMIT)
```

同一份白名单在 `TrustReplyWorkbenchService.kt:1675-1690` 有第二份实现 `allowedHandlings()`，两处取值一致。

→ 机器代填的默认选择只能在这个白名单内：

| coverage | 机器默认 handling | 说明来源 |
|---|---|---|
| GROUNDED | `ANSWER_WITH_EVIDENCE` | 无需说明（既有 `generateMissingGrounded` 已覆盖） |
| PARTIAL | `ANSWER_SUPPORTED_PART` | 无需说明（只答有据部分），instruction 留空 |
| UNSUPPORTED | `ANSWER_FROM_OPERATOR_INPUT` | **机器代填说明**（本计划新增） |

`OMIT` 永不由机器自动选择 —— 静默省略一条诉求是产品事故，必须人工显式选。

### `ANSWER_FROM_OPERATOR_INPUT` 的服务端校验（决定代填说明的形状）

`TrustReplyWorkbenchService.kt:1186-1201`。下面两段是**条件片段**；末行 `-> 422 …` 是本计划为便于阅读加的结论标注，**源码中没有这一行**：

```kotlin
item.status != RequestGroundingStatus.UNSUPPORTED || instruction.isBlank() ||
instruction.length > 500 || locked.operatorInstructionHash != sha256Hex(instruction) ||
locked.answerText.isBlank() || locked.claims.isNotEmpty() ||
locked.generationKind != TrustReplyItemGenerationKind.AI_GENERATED
    -> 422 TRUST_REPLY_LOCKED_ITEM_INVALID
```

其后还有动作越界校验：

```kotlin
val allowedActions = AiReplyActionPolicy.deriveAllowed(inboundText, null, emptyList())
if (AiReplyActionPolicy.detectActions(locked.answerText).isNotEmpty() ||
    AiReplyActionPolicy.findViolations(locked.answerText, allowedActions).isNotEmpty())
    -> 422 TRUST_REPLY_CLAIM_INVALID
```

**结论：代填说明只需满足「非空、≤500 字、哈希自洽」。服务端无需任何改动即可接受机器写的说明。**

### 已有的确定性兜底文案

`AiReplyHighRiskClaimValidator.kt:359-364`：

```kotlin
        fun safeAcknowledgementFor(inboundText: String): String =
            if (CHINESE_TEXT_MARKER.containsMatchIn(inboundText)) {
                SAFE_ACKNOWLEDGEMENT_CHINESE
            } else {
                SAFE_ACKNOWLEDGEMENT_ENGLISH
            }
```

→ `ACKNOWLEDGE_PENDING` 的 SAFE_TEMPLATE 路径已有确定性文案，LLM 不可用时的降级不用新写。

### 重置所需的存储能力

`TrustReplyWorkbenchStateStore.kt` 已有：

```
:32  fun load(sourceType: String, sourceId: Long): TrustReplyStoredState?
:84  fun delete(sourceType: String, sourceId: Long, expectedStateVersion: Long): Boolean
```

控制器现有端点（已 grep，`TrustReplyWorkbenchController.kt`）：

```
:41  @PostMapping("/bootstrap")
:52  @PostMapping("/generations/stream")
:85  @PostMapping("/generations/{generationId}/cancel")
:101 @PostMapping("/assemble")
:105 @PutMapping("/state")
```

**没有 DELETE。** 重置要新增一个端点，落在 `delete()` 上。

### Interaction points

| # | 写入方 | 读取方 | 影响 |
|---|---|---|---|
| IP-1 | `bootstrap` 返回的 `suggestedInstruction` | 前端 `autoRun()` 填进 textarea → `ADJUST_ITEM` 请求 | 新建。建议说明变化会改变代填结果 |
| IP-2 | `autoRun()` 写 `resolvedVersionId` → `persistResolvedSnapshot()` | `trust_reply_workbench_state` | 复用既有写路径，未新增 |
| IP-3 | 新 `DELETE /state` | `bootstrap` 的 `restoreSavedStateWithFrame()` | 新建。删后重新 bootstrap 必须落回默认态而非报错 |
| IP-4 | `/assemble` 的返回 | 新增结论区 | 新建 |

## 样式契约

### S-1: 顶部一键动作条

- **复用**：`.trust-reply-toolbar`（既有，`renderShell` 内 `data-role="toolbar"`）、`.button` / `.button.primary` / `.button.secondary`（全局）。
- **新增**（逐字追加到 `styles.css` 的 `.compose-workbench-section` 规则块之后）：

```css
.trust-reply-autorun {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    padding: 10px 12px;
    margin: 0 0 10px;
    border: 1px solid var(--panel-border);
    border-left: 2px solid var(--primary);
    border-radius: var(--radius-sm);
    background: var(--panel-bg);
}

.trust-reply-autorun-hint {
    flex: 1;
    min-width: 180px;
    color: var(--text-muted);
    font-size: 11.5px;
    line-height: 1.5;
}

.trust-reply-autofilled {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 1px 8px;
    border-radius: 999px;
    background-color: var(--info-bg);
    border: 1px solid var(--info-border);
    color: var(--info);
    font-size: 11px;
    line-height: 1.6;
    white-space: nowrap;
}
```

- **DOM 骨架**：

```html
<div class="trust-reply-autorun">
    <button type="button" class="button primary" data-action="auto-run">一键预判</button>
    <button type="button" class="button secondary" data-action="auto-reset">重置</button>
    <span class="trust-reply-autorun-hint">有据项自动生成，无据项由系统代填回答说明；汇总后仍可逐项调整。不发送、不写外发记录。</span>
</div>
```

- **禁止项**：inline style；未声明的新 class；修改 `.trust-reply-toolbar` / `.button` 既有规则块。

### S-2: 机器代填徽标

- **复用**：无（`.trust-reply-autofilled` 由 S-1 一并给出）。
- **DOM 骨架**（插入 `renderRequestHeader()` 输出的末尾）：

```html
<span class="trust-reply-autofilled">机器代填</span>
```

- **渲染条件**：`request.autoFilled === true && !request.instructionEditedByOperator`。人工一改说明或 handling，该标志置 false，徽标消失（I-3）。
- **禁止项**：把徽标做成 `disabled` 状态或遮罩 —— 代填项必须保持完全可编辑。

## 实现方案

### T1 · bootstrap 增加建议说明（I-2）

文件：`TrustReplyWorkbenchService.kt`、`TrustReplyWorkbenchController.kt`

**复核后收窄：`suggestedHandling` 不用加，已经有了。**
`TrustReplyRequestCoverage` 已带 `allowedHandlings` 与 `recommendedHandling` 两个字段
（`TrustReplyWorkbenchService.kt:132-133`），由 `toCoverage()` 填充（`:1665-1666`），
取值来自既有的两个函数（`:1675-1696`）：

```kotlin
        fun recommendedHandling(status: RequestGroundingStatus): TrustReplyItemHandling = when (status) {
            RequestGroundingStatus.GROUNDED -> TrustReplyItemHandling.ANSWER_WITH_EVIDENCE
            RequestGroundingStatus.PARTIAL -> TrustReplyItemHandling.ANSWER_SUPPORTED_PART
            RequestGroundingStatus.UNSUPPORTED -> TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT
        }
```

→ 机器代填直接用 `request.recommendedHandling`，**本计划不新增 handling 相关字段，也不改这三行取值**。

`requestCoverage` 只新增一个字段（**本计划要写的新代码，源码中尚不存在**）：

```kotlin
val suggestedInstruction: String? = null   // 仅 UNSUPPORTED 非空；≤500 字
```

`suggestedInstruction` 的生成落在 `TrustReplyWorkbenchService` 内一个新私有方法，输入是该 request 的原文与其**邻近 QA 规则**（跨条目命中的规则，02 计划的 ADJACENT 概念），产出一段中文说明，形如：

> 这一条我们库里没有确认口径。请按真人对接人的方式回答：先明说没有确认答案，再给出能确认的邻近事实（<邻近规则名>），最后交出下一步但不承诺具体时间。不要出现数字、链接或时间承诺。

**约束**：这段说明是**给 AI 的指令**，不是对外正文；它必须只描述"怎么答"，不得包含任何库外事实（X-3）。

### T2 · 前端一键编排（I-1, I-3, S-1, S-2）

文件：`trust-reply-workbench.js`

1. `computeReadiness()` 新增返回 `autoFillableKeys`（等于既有 `unresolvedManualKeys`，不改其计算）。
2. 抽出 `runItemSequence(keys, seq, handlingFor, instructionFor, labelPrefix)` —— 把 `generateMissingGrounded`（`:652-700`）的循环骨架提取为公共函数（取消/陈旧/失败三种分支原样保留），`generateMissingGrounded` 改为它的一个调用。**不复制粘贴。**
3. 新增 `autoRun()`：

```
autoRun():
  if (state.generation.pending) return
  seq = state.bootSeq
  keys = computeReadiness().autoFillableKeys
  for each key:                          // 复用 runItemSequence
     request.draftHandling = suggestedHandling
     request.instruction   = suggestedInstruction ?? ""
     request.autoFilled    = true
     await requestItemVersion(request, seq, id, "full", request.draftHandling)   // :671 同一接缝
     request.resolvedVersionId = request.activeVersionId
  await assemble()                       // 既有编排，一行不改
```

4. `onClick` 加两个分支（`:1639` 附近）：`if (action === "auto-run") void autoRun();` / `if (action === "auto-reset") void autoReset();`
5. `onChange` / `onInput` 里，当 `data-role="handling"` 或 `data-role="instruction"` 变化时置 `request.autoFilled = false`（I-3 徽标消失）。

### T3 · 结论区（I-5）

文件：`trust-reply-workbench.js`

`renderSummary()` 之上新增 `renderVerdict()`：汇总完成后展示
① 判定文案 ② 硬性闸门清单（未通过项逐条列出）③ 正文。
**汇总成功 ≠ 可自动发**：两者分两行显示，措辞不得混用。

闸门数据来源：本计划不新增判定接口，沿用第 2 页「自动回复预判」已有的闸门数据；若该页尚未执行，结论区闸门行显示「尚未预判」而非留空。

### T4 · 重置（I-4）

文件：`TrustReplyWorkbenchController.kt`、`TrustReplyWorkbenchService.kt`、`trust-reply-workbench.js`

1. 新增 `@DeleteMapping("/state")`，入参 `{sourceType, sourceId, expectedStateVersion}`，落到既有 `stateStore.delete(...)`。
   版本不符返回既有的 state conflict（复用 `throwStateConflict`，`StateStore:159`）。
2. 前端 `autoReset()`：确认对话框 → DELETE → 清空本地 `requests` 的 `resolvedVersionId` / `instruction` / `autoFilled` / `versions` → 重新 `bootstrap()`。
3. **只读模式（若 02 的 AUTO_PREVIEW 仍在）不得渲染这两个按钮** —— `requestJson` 的写闸门（`:229`）会拦下 DELETE，但按钮不该出现。

### T5 · 测试

- `src/test/js/autoRunOrchestration.test.js`（新建）：
  1. `autoRun()` 对每个 UNSUPPORTED 项发出的 `ADJUST_ITEM` 请求，`handling` 为 `ANSWER_FROM_OPERATOR_INPUT` 且 `operatorInstruction` 非空。
  2. `autoRun()` 结束后恰好调用一次 `/assemble`。
  3. 任一项生成失败 → 不调用 `/assemble`（沿用 `generateMissingGrounded` 的失败语义）。
  4. 人工改动 handling 或 instruction 后 `autoFilled` 置 false。
  5. `auto-reset` 发出 DELETE 且随后重新 bootstrap。
- `TrustReplyWorkbenchControllerTest.kt`（修改）：DELETE /state 的 200 / 版本冲突 / 未知 source 三种响应。
- `TrustReplyWorkbenchServiceTest.kt`（修改）：`suggestedHandling` 对三种 coverage 的取值落在 `validateItemHandling` 白名单内；`suggestedInstruction` 仅 UNSUPPORTED 非空且 ≤500 字。

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/resources/static/trust-reply-workbench.js` | 修改 | `autoRun` / `autoReset` / `runItemSequence` 抽取 / 徽标 / 结论区 / 两个 action 分支 |
| 2 | `src/main/resources/static/styles.css` | 修改 | 按 S-1 逐字新增 3 个规则块 |
| 3 | `src/main/kotlin/.../llm/service/TrustReplyWorkbenchService.kt` | 修改 | `requestCoverage` **+1 字段**（`suggestedInstruction`）；说明合成；delete 转发。`allowedHandlings` / `recommendedHandling` 已存在，不动 |
| 4 | `src/main/kotlin/.../llm/controller/TrustReplyWorkbenchController.kt` | 修改 | `@DeleteMapping("/state")`；响应 DTO +1 字段 |
| 5 | `src/test/js/autoRunOrchestration.test.js` | 新建 | 编排契约 5 条 |
| 6 | `src/test/js/trustReplyWorkbench.test.js` | 修改 | 新 action 与徽标的存在性断言 |
| 7 | `src/test/kotlin/.../llm/controller/TrustReplyWorkbenchControllerTest.kt` | 修改 | DELETE /state 三种响应 |
| 8 | `src/test/kotlin/.../llm/service/TrustReplyWorkbenchServiceTest.kt` | 修改 | 建议 handling / instruction 契约 |
| 9 | `src/test/kotlin/.../llm/service/TrustReplyWorkbenchItemFlowTest.kt` | 修改 | 机器代填说明能通过 `ANSWER_FROM_OPERATOR_INPUT` 的既有校验 |

9 个文件，2 个子系统。无迁移，无新表，无新枚举值。

## 验证命令

> JDK 11（zulu-11）必须；裸 `mvn` 会构建失败。前端权威门禁是 `node --test` 单跑，
> `verify.sh` 只跑一个无关文件，不可用作回归门禁（来源：K-js-test-invocation-surface）。

```bash
# 前端权威门禁
node --test src/test/js/autoRunOrchestration.test.js
node --test src/test/js/trustReplyWorkbench.test.js
node --test src/test/js/*.test.js
node --check src/main/resources/static/trust-reply-workbench.js

# 后端相关类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=TrustReplyWorkbenchControllerTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=TrustReplyWorkbenchServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=TrustReplyWorkbenchItemFlowTest

# 单方法
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=TrustReplyWorkbenchServiceTest#'suggested handling stays inside the allowed set'

# 全量 + 构建 + 卫生
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

通过判据：`node --test` 输出 `# fail 0`；`mvn` 退出码 0 且含 `Failures: 0, Errors: 0`。

## 验收标准

- **I-1**：`git diff` 显示 `TrustReplyWorkbenchService.adjustItem()` 的校验分支零改动；T5-1 通过。
- **I-0**：新增测试 —— 对一个 UNSUPPORTED 项，断言合成的 `suggestedInstruction` 不包含其邻近 QA 规则 `answerBody` 中任意长度 ≥ 12 的连续子串（防止事实夹带）；且不含数字、`http`、时间承诺词。
- **I-2**：`grep -n "希望如何回答\|请按真人\|机器代填说明" trust-reply-workbench.js` 无输出（话术不在前端）；`TrustReplyWorkbenchServiceTest` 的建议说明用例通过。
- **I-3**：T5-4 通过；`grep -n "trust-reply-autofilled" trust-reply-workbench.js` 命中处不伴随 `disabled`。
- **I-4**：`grep -n "qa_rule\|reply_snippet\|mail_record\|DELETE FROM" TrustReplyWorkbenchService.kt` 在新增代码段内无输出；DELETE 端点仅调用 `stateStore.delete`。
- **I-5**：结论区渲染函数中「汇总完成」与「可自动发送」是两个独立字符串，且不存在把 assembly 非空直接当作可发送的分支（人工 diff 核对）。
- **不变量回归**：`assemble()` / `generateMissingGrounded()` / `requestItemVersion()` 三个函数体在 `git diff` 中仅出现「抽取为 `runItemSequence`」这一种改动，无语义改写。
- **S-1/S-2**：`styles.css` 新增 3 个规则块与契约逐字一致；新 DOM 骨架无 `style="`。
- **回归**：执行「验证命令」全部通过。

## 人工验收清单

### A-1: 一键跑通（覆盖需求 1/2，I-1，I-5）

- 前置条件：一封含 ≥1 条 UNSUPPORTED 诉求、已绑定联系人的来信。
- 操作步骤：打开工作台第 1 页，直接点「一键预判」，不做任何手工操作。
- 预期结果：
  - 按钮进入进行中态，状态区依次出现「正在生成…1/N」「正在请求服务端整合…」。
  - 结束后「整合摘要」出现服务端原始正文（非空）。
  - 无据项卡片上出现蓝色「机器代填」徽标，其说明文本框内有内容。
  - 结论区**同时**显示「汇总已完成」与未通过的硬性闸门列表 —— 二者不得合并成一句「可自动发送」。

### A-2: 代填项可人工覆盖（覆盖需求 3，I-3）

- 前置条件：A-1 已跑完。
- 操作步骤：在带「机器代填」徽标的那一项，把说明改成自己的一句话 → 点该项的重新生成 → 再点「一键预判」。
- 预期结果：
  - 一改说明，徽标立即消失。
  - 重新生成后的正文体现你写的说明。
  - 再次「一键预判」**不会覆盖**你改过的那一项（该项已有 resolvedVersion，不在 autoFillableKeys 里）。

### A-3: 重置回默认态（覆盖需求 4，I-4）

- 前置条件：A-1/A-2 已产生采用与说明。记录 `SELECT COUNT(*) FROM qa_rule` 与 `SELECT COUNT(*) FROM reply_snippet`。
- 操作步骤：点「重置」→ 确认。
- 预期结果：
  - 页面回到刚打开时的样子：无采用版本、说明清空、无徽标、整合摘要为「配置预览 · 未整合」。
  - `trust_reply_workbench_state` 中该 source 的行消失。
  - `qa_rule` 与 `reply_snippet` 计数**与操作前完全相同**。

### A-4: 训练宿主同样可用（覆盖 must-NOT-change 第 4 条）

- 操作步骤：进「AI 训练」→「模拟」Tab 选一封邮件，重复 A-1。
- 预期结果：按钮、编排、徽标、重置全部存在且行为一致；最终动作仍是「完成模拟并评估」而非「采用到人工回复」。

### A-5: 失败不半途落库（回归）

- 前置条件：把 LLM 配置成不可用（如清空 `apiUrl`）。
- 操作步骤：点「一键预判」。
- 预期结果：状态区报错并可重试；**不调用 `/assemble`**；`trust_reply_workbench_state` 不出现半成品采用记录。

---

## 关于「后续开启自动发送也走这个流程」

这条**必须单列为 05 计划**，不能并进本轮，原因是硬约束而非偏好：

本计划的编排跑在**浏览器里**，一次点击对应 N 次串行 LLM 调用，用户在旁边看着进度条，慢一点没关系。
自动发送跑在 IMAP 拉取循环里：`MAIL_SCHEDULING_AUTO_REPLY_MAX_MESSAGES_PER_ACCOUNT` 默认 20（`application.yml:67`），
`BatchAutoMailReplyService` 还跨账号循环，`processSingle()` 又带 `@Transactional`。
把 N 次串行生成塞进去 = 事务长时间持有 + 拉取循环阻塞，收信链路会挂。

**05 的前置条件**是先把自动回复改成队列异步（RabbitMQ 配置现成：`talent-introduction.mail-queue.*`），
或给自动路一套独立的更紧预算。这一步不做完，05 不能开工。


---

## 计划自查记录（2026-08-18）

对本计划中每一条可验证的代码断言做了机器核对：**31 条正向断言 + 6 条反向断言**（反向 = 「这些东西现在必须不存在」）。

反向断言 6 条全部通过，确认以下都还不存在，不会与既有实现冲突：
`data-action="auto-run"`、`data-action="auto-reset"`、`@DeleteMapping`、`suggestedInstruction`、
`.trust-reply-autorun`、`.trust-reply-autofilled`。

正向断言中查出并已修正的 5 处：

| # | 原文 | 实测 | 性质 |
|---|---|---|---|
| 1 | `assembleLabel` 在 `:1552-1560` | **`:1556-1564`** | 行号错 4 行 |
| 2 | operator basis prompt 在 `AiReplyDraftService.kt:673` | **`:672`** | 行号错 1 行 |
| 3 | `locked.claims.isNotEmpty()` 在 `Service:1189` | **`:1190`** | 行号错 1 行（该文件另有两处同名判断，在 1170 / 1177，分属 OMIT 与 ACKNOWLEDGE_PENDING 分支，勿混） |
| 4 | `ANSWER_FROM_OPERATOR_INPUT` 校验 `:1186-1194` | **`:1186-1193`** | 尾行多算 1 |
| 5 | handling 白名单 `AiReplyDraftService.kt:770-788` | **`:770-787`**（788 是收尾 `}`） | 尾行多算 1 |

另修正 3 处**标注不实**（内容不假，但不该称"逐字"）：

- `assembleLabel` 原先是带 `...` 的节选却与"逐字"并列 → 已换成源码全文。
- `canStartAssembly` 代码块里混入了计划自己加的 `// ← 就是这里` 注释 → 已移除，改在块外文字里指出。
- `recommendedHandling` 代码块被重新对齐过缩进 → 已还原为源码真实缩进。
- handling 白名单块是压缩改写（源码里每个 `setOf(...)` 跨多行）→ 已显式标注"非逐字"，并补上第二份实现出处 `TrustReplyWorkbenchService.kt:1675-1690`。

**已复核无误、无需改动的关键断言**（实测行号与计划一致）：

```
assemble() 函数体                    917-1011
  前置闸门 canStartAssembly           919
  自动采用 adoptableGrounded          921
  persistResolvedSnapshot()          930
  generateMissingGrounded(...)       956
  lockedItems 序列化                  959
  POST /assemble                     972
computeReadiness()                   868
  canStartAssembly 表达式             885-890
  unresolvedManual 定义               879-880
generateMissingGrounded()            652
requestItemVersion(... "full", ...)  671
renderRequest()                      1532
onClick assemble 分派                1639
requestJson 只读写闸门                229
coverage DTO allowed/recommended     132-133
toCoverage() 填充这两个字段            1665-1666
allowedHandlings()                   1675
recommendedHandling()                1692-1696
when (locked.handling)               1168
StateStore.delete()                  84
StateStore.throwStateConflict()      159
Controller @PostMapping("/bootstrap") 41
Controller @PutMapping("/state")      105
```

自查方式：脚本按 (文件, 行区间, 断言内容) 三元组核对，并对声称逐字的代码块做归一化子串比对。
未通过项已按实测值改写，本节记录改动前后以便追溯。

### 第二轮：全文 `file:line` 引用与代码块复查

- 正文中 **16 条唯一 `file:line` 引用，0 条越界**。
- 正文中 **9 段 js/kotlin 代码块**，逐段判定是否在源码中原样存在：
  - 原样存在（5 段）：`assembleLabel`、`canStartAssembly`、`unresolvedManual` 判定、
    `recommendedHandling()`、`safeAcknowledgementFor()`。
  - 有意非原样（4 段），已在各自位置显式标注，不再声称逐字：
    ① handling 白名单——压缩改写，源码每个 `setOf(...)` 跨多行；
    ② `ANSWER_FROM_OPERATOR_INPUT` 条件片段、③ 动作越界条件片段——末行 `-> 422 …` 是本计划加的结论标注，源码无此行；
    ④ `val suggestedInstruction`——本计划要新写的代码，源码中本就不存在。
