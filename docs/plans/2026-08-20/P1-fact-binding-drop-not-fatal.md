# P1：手动绑定的事实未被采纳时，丢弃并提示，而不是让整个工作台打不开

> 顺序权威：本目录 `00-execution-order.md`。基线与"符号名优先、行号仅交叉验证"的约定见该文件。
> **前置：P0 必须先合并**（本刀的 A-1 需要读到 422 响应体里的 code，A-5 需要失败界面的重置按钮来清场）。
> **本刀是止血，不是修复。** 手动绑定仍然不会生效——那是 P2a 的事。本刀只保证运营不再把工作台搞崩，且能看懂为什么。

---

## 需求描述

### Observable outcome

1. 运营给一条摘要手动「+ 添加事实」后，**工作台仍然能正常打开**，不再出现 `POST /api/trust-reply/workbench/bootstrap` 返回 `422 {"code":"TRUST_REPLY_FACT_SELECTION_INVALID"}`。
2. 未被采纳的绑定在**该条摘要下方**出现一条可见提示，写明是哪几条事实、以及未被采纳的原因；被采纳的绑定照常显示为 chips。
3. 这个状态是自洽的：再次操作（改事实、生成、汇总、保存）都不会因为这次未被采纳的绑定而报错。

### What must NOT change

1. **真正非法的输入仍然硬拦**，不得一并降级：
   - `resolveMatrixSelection` 的矩阵条数与摘要条数不等（`QaFactSelectionService.kt:168-172`）→ 仍抛 `TRUST_REPLY_FACT_SELECTION_INVALID`
   - `validateExplicitSelection`（`:334-348`）抛 `IllegalArgumentException`（规则不存在 / 已停用 / `replyPolicy=NEVER` / 事实正文为空）→ 仍在 `:181-186` 转成 `TRUST_REPLY_FACT_SELECTION_INVALID`
   - `checkWorkbenchUniqueness`（`:322-332`）的 `TRUST_REPLY_FACT_ALREADY_ASSIGNED`
   - `validateMatrixKeys`（`TrustReplyWorkbenchService.kt:1720-1741`）的四个抛点
2. **`buildRequestFact` 的判定逻辑一字不改**：`candidateRules` 的关键词过滤（`:394-398`）、`evidenceSet` 的 SUPPORTED 过滤（`:435-438`）、`factRuleIds` 的合成（`:440-442`）、`status` 的判定（`:426-433`）全部保持原样。本刀**不改变任何条目的 status，也不改变任何条目的 `factRuleIds` 取值**。
3. **自动匹配路径与 legacy 路径零变化**：`resolveAutoSelection`、`resolveLegacySelection` 及其产出的 `ResolvedQaRules` 逐字不变。
4. **`sendQaRuleIds` / `promptRuleIds` 零变化**：`workbenchResult()`（`:295-320`）的 `sendIds` 仍由 `item.factRuleIds` 合成。未被采纳的绑定**不进** prompt、**不进**外发审计。
5. **无存储变更**：不新增表、字段、索引、迁移；`TrustReplySavedStatePayload` 结构不变。
6. **HTTP 契约只增不改**：请求体零变化；响应体只在 `TrustReplyRequestCoverage` 上新增一个带默认值的字段。

### Out of scope（显式推迟）

1. **让手动绑定真正生效**（保留 chips、进入 AI 上下文）→ `P2a` / `P2b`。
2. **在 UI 上禁用 UNSUPPORTED 条目的「+ 添加事实」**。那等于承认功能作废；P2a 会让它可用，禁用了还要再打开。
3. **`validateMatrixKeys` 的 `:1734`（矩阵未覆盖全部 canonical 摘要）也降级**。那是前端矩阵与服务端摘要集合不一致，属客户端 bug，不该静默；P0 的重置入口已经让它可自救。
4. **给未被采纳的绑定做"为什么"的细分归因**（是关键词没命中，还是意图没被支持）。本刀只给一句统一原因，细分需要把 `buildRequestFact` 的中间量暴露出来，属独立一刀。

---

## 关键不变量

### Invariant I-1: 降级只发生在一处，且只对"服务端过滤掉了运营的绑定"这一种情况
- Rule: 唯一被改成"不抛"的判据是 `QaFactSelectionService.resolveMatrixSelection` 内的
  `if (item.factRuleIds != explicitIds) throw ...`（当前 `:199-204`）。
  改造后该处产出 `droppedBindingRuleIds = explicitIds - item.factRuleIds.toSet()`（保持 `explicitIds` 的原始顺序），并**继续使用 `item.factRuleIds` 作为该条目的事实集合**。
  must-NOT-change 第 1 条列出的其余全部抛点一字不动。
- Applies to: `QaFactSelectionService.resolveMatrixSelection`
- Violation consequence: 把 `:168` 的条数不等或 `validateExplicitSelection` 的失败一并降级，等于让"规则已停用/不存在"这类真正的脏输入静默通过，事实库的失效状态就再也传不到运营眼前。
- 来源: original

### Invariant I-2: 丢弃信息是逐条目的，不是整封的
- Rule: 未被采纳的绑定必须能定位到**具体哪一条摘要**。承载方式：
  `RequestFactItem` 新增 `droppedBindingRuleIds: List<Long> = emptyList()`；
  `TrustReplyRequestCoverage` 新增 `droppedFactRuleIds: List<Long> = emptyList()`；
  前端在该条目的事实区下方渲染提示。
  **不得**改用整封级的 `TrustReplyBootstrapResponse.contextWarnings`（`:185`）——实测前端 `trust-reply-workbench.js` 对该字段**零消费**（`grep -n "contextWarnings" src/main/resources/static/trust-reply-workbench.js` 无命中），且整封级信息说不出是哪一条。
- Applies to: `AiReplyDraftService.RequestFactItem`；`TrustReplyWorkbenchService.TrustReplyRequestCoverage` 与 `List<RequestFactItem>.toCoverage`；`trust-reply-workbench.js` 的事实区渲染。
- Violation consequence: 运营看到"有事实没被采纳"却不知道是哪一条摘要，等于没提示。
- 来源: original

### Invariant I-3: 新字段是影子字段，绝不进入任何身份哈希或对外文本
- Rule: `droppedBindingRuleIds` / `droppedFactRuleIds` **不得**出现在：
  `requestEvidenceVersion(...)` 的入参（`TrustReplyWorkbenchService.kt:1698`、`:1828`）、
  `canonicalMatrix(...)` 的产出（`:1743-1752`）、
  `versionId(...)` 的入参、
  `TrustReplySavedStatePayload` 的任何字段、
  以及任何外发正文 / prompt / 审计载荷。
- Applies to: 上述全部
- Violation consequence: 进哈希 → 同一份绑定在"被采纳"与"未被采纳"两种情况下产生不同版本身份，锁定项会被整批判为陈旧；进外发文本 → 内部判定细节泄露给专家。
- 来源: K-request-fact-assignment-version-must-include-mapping（该条目要求版本身份精确覆盖映射；反过来也要求**不相干的影子字段不得混入**）

### Invariant I-4: 服务端两个投影必须继续一致，否则前端自带的守卫会立刻再次打挂
- Rule: `TrustReplyBootstrapResponse.requestFactSelections`（由 `canonicalMatrix` 产出）与
  `requestCoverage[].factRuleIds`（由 `toCoverage` 产出）**必须逐字相等**，两者都只能取 `item.factRuleIds`。
  新字段只能作为**并列的第三个**投影出现，不得替换或混入这两者中的任何一个。
- Applies to: `canonicalMatrix`（`:1743-1752`）与 `toCoverage`（`:1869`）
- Violation consequence: 前端 `applyBootstrap` 有一份**自带的**同款相等性校验（`trust-reply-workbench.js:585-595`，不一致即 `throw new Error("TRUST_REPLY_FACT_SELECTION_INVALID")`）。服务端降级了、前端这份没降级，工作台照样打不开——**只是把 422 换成了前端异常**。
- 来源: original（实证见证据 E-3）

### Invariant I-5: 自愈闭环必须成立
- Rule: 降级后，前端从 coverage 重建的 `state.requests[].factRuleIds` 即为过滤后的集合；下一次 `serializeRequestFactSelections()`（`:500-506`）发出的矩阵与服务端过滤结果一致，**同一操作不会第二次触发降级**。
- Applies to: `toCoverage` → `requestFromCoverage`（`:457-497`）→ `serializeRequestFactSelections`
- Violation consequence: 若前端把"运营原本绑的"而不是"服务端采纳的"写回 `factRuleIds`，则每次请求都重复丢弃、提示反复出现、且与 I-4 的守卫冲突。
- 来源: original

---

## 样式契约

> 触发条件：本刀改动 `src/main/resources/static/trust-reply-workbench.js`。
> 总原则：**不新增任何 CSS class，不改 `styles.css`。** 照抄同文件既有的 stale 提示写法。

### S-1: 条目级「未采纳的绑定」提示
- **复用**：`class="muted"`。这是本文件既有 hint 的标准写法，现成范例见 `trust-reply-workbench.js:1851-1855` 的 `staleMarkup`：

```javascript
const staleMarkup = (request.evidenceStale === true
    ? `<span class="muted" data-role="item-evidence-stale">事实已变化，本条回答需重新生成</span>`
    : "") + (request.contextStale === true
    ? `<span class="muted" data-role="item-context-stale">本条在旧训练知识/对话历史下生成</span>`
    : "");
```
  该处代码注释逐字写着「no new class, no inline style, no output when the condition is false」——本契约完全沿用这条约定。
  **禁止**执行 agent 自造 `.trust-reply-fact-dropped` 之类的新 class，或写 inline style，或加颜色/图标。
- **新增 CSS**：无。`styles.css` 零改动。
- **DOM 结构**：在 `renderFactSection` 返回值末尾、`${staleMarkup}` 之后追加同形状的一段（**逐字**）：

```javascript
const droppedMarkup = (Array.isArray(request.droppedFactRuleIds) && request.droppedFactRuleIds.length > 0)
    ? `<span class="muted" data-role="item-facts-dropped">以下事实未被采纳：${escapeText(droppedFactLabels(request))}。该问题未识别出可支持的意图，事实无处挂载，本条回答不会引用它们。</span>`
    : "";
```
  并把返回模板末尾的 `${staleMarkup}` 改为 `${staleMarkup}${droppedMarkup}`。
  `droppedFactLabels(request)` 是新增的小助手，按 `state.rules` 把 id 映射成显示名（找不到时回退 `事实 <id>`），复用 chips 渲染（`:1805-1819`）已有的同款查名逻辑。
- **禁止项**：inline style；未在本契约声明的新 class；对 `.muted`、`.trust-reply-fact-section`、`.trust-reply-fact-chip-list` 既有规则块的任何修改；条件为假时输出空标签（必须输出空字符串）。

---

## 现状审计

### Phase 0 知识加载（采用与驳回）

**采用**：
- `K-request-fact-assignment-version-must-include-mapping` → I-3（影子字段不得混入身份哈希）。
- `K-ai-reply-prompt-vs-send-rule-ids` → must-NOT-change 第 4 条（未采纳的绑定既不进 prompt 也不进审计）。
- `K-js-tests-run-via-exec-plugin` → 验证命令。
- **项目记忆**条目「覆盖率的分母是"认出的意图"」（`grounded-denominator-is-matched-intents.md`，不在 `docs/knowledge/` 而在项目记忆里）→ 本缺陷的上位原因：没被 `matchIntents` 认出的问题静默消失，导致"人工补事实"失效。本刀是它的一个具体落点。

**读取后确认不适用**：
- `K-operator-directed-authorization-seam`（2026-08-20 新增）—— 讲动作授权（G1/G2），与事实矩阵无交集；本刀不碰 `AiReplyActionPolicy`。
- `K-workbench-state-lazy-expiry` —— 本刀不新增状态存储的读写路径。

### 数据存储

**本刀不触及任何数据存储。** 不新增表、字段、索引、迁移。
`trust_reply_workbench_state` 的 payload（`TrustReplySavedStatePayload`，`TrustReplyWorkbenchService.kt:197-206`）**结构不变**——新字段只存在于运行时对象与 HTTP 响应，不落库（I-3）。

### 关键路径：`QaFactSelectionService.resolveMatrixSelection`（`:162-207`）

**证据 E-1 — 等式在 UNSUPPORTED 条目上数学不可满足。** 三段代码合起来构成闭合推导：

```kotlin
// :199-204  ← 本刀唯一改动点
if (item.factRuleIds != explicitIds) {
    throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_FACT_SELECTION_INVALID")
}
```
```kotlin
// buildRequestFact，:394-398 —— 第一层：关键词必须命中本条摘要文本
val candidateRules = promptPool.filter { rule ->
    rule.id != null && rule.id in promptSet && QaFactKeywordMatcher.matchesRule(rule, normalizedRequest)
}
// :435-442 —— 第二层：只留落在 SUPPORTED 意图证据集里的
val evidenceSet = intentCoverages.filter { it.status == "SUPPORTED" }.flatMap { it.evidenceRuleIds }.toSet()
val factRuleIds = candidateRules.mapNotNull { it.id }.filter { it in evidenceSet }
```
```kotlin
// :426-433 —— status 判定
val status = when {
    researchWarned && !anySupported -> RequestGroundingStatus.UNSUPPORTED
    intentCoverages.isEmpty()       -> RequestGroundingStatus.UNSUPPORTED
    allSupported                    -> RequestGroundingStatus.GROUNDED
    allMissing                      -> RequestGroundingStatus.UNSUPPORTED
    anySupported || anyPartial      -> RequestGroundingStatus.PARTIAL
    else                            -> RequestGroundingStatus.UNSUPPORTED
}
```

推导：`UNSUPPORTED` ⟹ 没有 `status == "SUPPORTED"` 的意图 ⟹ `evidenceSet` 为空 ⟹ `factRuleIds` 为 `[]` ⟹ 只要 `explicitIds` 非空，`[] != explicitIds` 恒成立 ⟹ **必抛**。

**「+ 添加事实」在 `UNSUPPORTED` 条目上，无论绑什么都必然把 bootstrap 打挂。** 而 `UNSUPPORTED` 恰恰是运营唯一需要手动绑事实的场景。

**证据 E-1b — 次要过滤：无关键词的规则永远绑不上。** `QaFactKeywordMatcher.matchesRule`（`:513-523`）：
```kotlin
val keywords = parseKeywords(rule)
if (keywords.isEmpty()) { return false }
```
关键词为空的事实在**任何**摘要上都进不了 `candidateRules`，因此在 `GROUNDED` 条目上手动绑它同样会触发本缺陷。

**证据 E-2 — 该 code 在 `src/main` 共 7 处，本刀只动 1 处。** 实测
`grep -rn "TRUST_REPLY_FACT_SELECTION_INVALID" --include=*.kt src/main | wc -l` → **7**：

| 位置 | 语义 | 本刀 |
|---|---|---|
| `QaFactSelectionService.kt:168-172` | 矩阵条数 ≠ 摘要条数 | ❌ 保持抛 |
| `QaFactSelectionService.kt:181-186` | `validateExplicitSelection` 抛（规则不存在/停用/`NEVER`/正文空） | ❌ 保持抛 |
| `QaFactSelectionService.kt:199-204` | **服务端过滤掉了运营的绑定** | ✅ **降级** |
| `TrustReplyWorkbenchService.kt:1734` | 矩阵未覆盖全部 canonical 摘要 | ❌ 保持抛 |
| `TrustReplyWorkbenchService.kt:1738` | 某个 factId ≤ 0 | ❌ 保持抛 |
| `QaFactSelectionService.kt:236-242` | **legacy 路径**：`validateExplicitSelection` 抛 | ❌ 保持抛 |
| `QaFactSelectionService.kt:260-265` | **legacy 路径**：`remaining` 非空（有事实没被任何摘要消费） | ❌ 保持抛 |

最后两处在 `resolveLegacySelection`（v1 扁平并集路径）内。其中 `:260-265` 与本刀要降级的 `:199-204` **是同一个缺陷形状**（"运营给的事实没被采纳就报错"），但本刀**不动它**：must-NOT-change 第 3 条。理由是前端 `serializeRequestFactSelections()`（`:500-506`）恒发矩阵，legacy 路径只在解码到 v1 历史快照时才被走到（`TrustReplyWorkbenchService.kt:437-439` 的 `candidateFactIds` 分支），触发面极小；一并改会把本刀的验证面扩大一倍。

（前端另有一处同名 `throw new Error("TRUST_REPLY_FACT_SELECTION_INVALID")`，见证据 E-3。）

**证据 E-2b — `validateExplicitSelection` 的四种拒绝理由（保持硬拦的依据）**，`:334-348`：
规则不存在 → `IllegalArgumentException("QA rule not found")`；`rule.enabled` 为假；`replyPolicyEnum() == QaReplyPolicy.NEVER`；`answerBody.trim().isBlank()`。
这四种都是"这条事实客观上不能用"，不是"这条事实不匹配这个问题"，必须让运营知道，故不降级（must-NOT-change 第 1 条）。

### 关键路径：服务端到前端的两个投影

**证据 E-3 — 前端自带一份同款相等性校验，服务端单方面降级会被它接着打挂。**
`trust-reply-workbench.js` `applyBootstrap`（`:585-595`）：

```javascript
// Fail closed when the server canonical matrix disagrees with the
// per-request coverage instead of silently re-deriving a flat pool.
if (Array.isArray(data.requestFactSelections) && data.requestFactSelections.length > 0) {
    const selectionsByKey = new Map(data.requestFactSelections.map((s) => [s.requestKey, s.factRuleIds || []]));
    const inconsistent = state.requests.find((request) => {
        const canonical = selectionsByKey.get(request.requestKey);
        if (!canonical) return false;
        return JSON.stringify([...canonical]) !== JSON.stringify([...(request.factRuleIds || [])]);
    });
    if (inconsistent) { throw new Error("TRUST_REPLY_FACT_SELECTION_INVALID"); }
}
```

它比的是服务端返回的两个投影：
- `data.requestFactSelections` ← `canonicalMatrix(sourceVersion, selection)`（`TrustReplyWorkbenchService.kt:1743-1752`，取 `factRuleIds = item.factRuleIds`）
- `state.requests[].factRuleIds` ← `requestFromCoverage(data.requestCoverage, …)`（`:463`，取 `item.factRuleIds`）← `toCoverage`（`:1869`，`factRuleIds = item.factRuleIds`）

**两者同源于 `item.factRuleIds`，所以只要新字段是并列的第三个投影、不去动这两个，这份守卫就恒不命中。** 这正是 I-4 的内容，也是本刀能只改服务端一处的前提。

**证据 E-4 — 自愈链路（I-5 的依据）。**
`requestFromCoverage`（`:457-497`）用 `item.factRuleIds` 初始化 `request.factRuleIds`（`:463`）；
`serializeRequestFactSelections`（`:500-506`）直接回传 `request.factRuleIds`。
所以降级后的下一次请求，前端发的就是服务端采纳的那份，**同一次操作不会第二次触发丢弃**。

**证据 E-5 — 前端不消费 `contextWarnings`（I-2 排除整封级方案的依据）。**
`grep -n "contextWarnings" src/main/resources/static/trust-reply-workbench.js` → **零命中**。
服务端 `bootstrap` 填的是 `contextWarnings = resolved.contextWarnings`（`TrustReplyWorkbenchService.kt:505`），来自 `ResolvedTrustReplySource`，与事实选择无关。走这条通道既要新建前端消费点，又说不出是哪一条摘要。

### 关键路径：`RequestFactItem` 加字段的兼容性

**证据 E-6 — 带默认值的新字段有现成先例，且构造点可枚举。**
`RequestFactItem`（`AiReplyDraftService.kt:349-359`）已有两个带默认值的后置字段：`requiresResearchContext = false`、`intents = emptyList()`、`unrecognizedAsks = emptyList()`。其中 `unrecognizedAsks` 的注释逐字写着「shadow-period measurement only — never feeds status, groundedRequestCount, unsupportedRequests or any hash」——**本刀的新字段与它同性质**，照抄该模式即可（I-3）。

构造点（`grep -rn "RequestFactItem(" --include=*.kt src/main`）：`QaFactSelectionService.buildRequestFact` 的唯一 `return`（`:444-452`）。加带默认值的字段对其余构造点（含测试）**源码兼容**。

`TrustReplyRequestCoverage`（`TrustReplyWorkbenchService.kt:133-149`）同样已有带默认值的后置字段（`unrecognizedAsks = emptyList()`、`evidenceSetVersion = ""`，后者注释写明「the default keeps every existing constructor site source-compatible」），照抄。

### Interaction points

| # | 写/产生 | 读/消费 | 影响 | 验收 |
|---|---|---|---|---|
| IP-1 | `resolveMatrixSelection` 降级后的 `item.factRuleIds` | `canonicalMatrix`（`:1750`）与 `toCoverage`（`:1913`）两个投影 | 两者必须继续同源，否则前端 `:585-595` 的守卫接管报错 | I-4 / A-1 / A-6 |
| IP-2 | `toCoverage` 新增的 `droppedFactRuleIds` | `requestFromCoverage`（`:457-497`）→ `renderFactSection`（`:1841-1859`） | 前端需把新字段带进 `state.requests`，否则提示永远不显示 | I-2 / S-1 / A-2 |
| IP-3 | 降级后前端的 `request.factRuleIds` | `serializeRequestFactSelections`（`:500-506`）→ 下一次 bootstrap / adjustItem / saveState | 自愈闭环；若不成立则提示反复出现 | I-5 / A-3 |
| IP-4 | 新增的影子字段 | `requestEvidenceVersion`（`:1698`、`:1828`）、`canonicalMatrix`、`versionId`、`TrustReplySavedStatePayload` | 混入任一处都会让锁定项批量判陈旧 | I-3 / A-4 |
| IP-5 | `resolveMatrixSelection` 的其余 4 个抛点 | 同一个 code 的前端文案 | 降级一处后，另外四处仍应硬拦并显示同一句中文（P0 的文案表） | must-NOT-change 1 / A-5 |

---

## 实现方案

### 阶段 A：服务端影子字段（I-1 / I-2 / I-3）

**A-1. `AiReplyDraftService.kt` —— `RequestFactItem` 加字段。**

在 `unrecognizedAsks` 之后追加：

```kotlin
    // P1 (I-3): 影子字段——运营显式绑定但被 buildRequestFact 过滤掉的事实 id，
    // 按运营原始顺序。仅供 UI 提示，绝不进入 status、factRuleIds、sendQaRuleIds、
    // promptRuleIds、任何 evidence/version 哈希或任何对外文本。
    val droppedBindingRuleIds: List<Long> = emptyList()
```

**A-2. `QaFactSelectionService.kt:199-204` —— 唯一的降级点。**

```kotlin
// 改前
if (item.factRuleIds != explicitIds) {
    throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_FACT_SELECTION_INVALID")
}
item

// 改后（I-1）
// P1 (I-1): 服务端两层过滤（:394-398 关键词、:440-442 SUPPORTED 证据集）可能
// 丢弃运营显式绑定的事实。对 UNSUPPORTED 条目这是必然发生的（evidenceSet 恒空），
// 因此这里不能当作非法输入抛错——那会让整个 bootstrap 422、工作台打不开。
// 改为：采纳过滤结果，把被丢弃的 id 记进影子字段供 UI 提示（I-2/I-3）。
// 注意：:168 的条数校验、:181-186 的 validateExplicitSelection 仍然硬拦（真脏输入）。
val accepted = item.factRuleIds.toSet()
val dropped = explicitIds.filter { it !in accepted }
if (dropped.isEmpty()) item else item.copy(droppedBindingRuleIds = dropped)
```

约束：
- `dropped` 用 `explicitIds.filter { … }` 而非集合差，以**保持运营的原始顺序**（提示里按运营绑的顺序列出）。
- **不得**改用 `explicitIds` 覆盖 `item.factRuleIds`（那是 P2a 的事，且会立刻违反 I-4 / must-NOT-change 第 4 条）。
- `:168-172`、`:175` 的 `checkWorkbenchUniqueness`、`:181-186` 一字不改。
- `workbenchResult(...)`（`:295-320`）一字不改：`sendIds` 仍由 `item.factRuleIds` 合成（must-NOT-change 第 4 条）。

**A-3. `TrustReplyWorkbenchService.kt` —— coverage 加字段并透传。**

`TrustReplyRequestCoverage`（`:133-149`）末尾追加：
```kotlin
    // P1 (I-2/I-3): 本条摘要中运营绑定但未被采纳的事实 id。影子字段，
    // 默认值保证既有构造点源码兼容；不参与任何身份哈希（I-3）。
    val droppedFactRuleIds: List<Long> = emptyList()
```

`List<RequestFactItem>.toCoverage`（`:1869`）内构造 `TrustReplyRequestCoverage` 处追加一行：
```kotlin
    droppedFactRuleIds = item.droppedBindingRuleIds,
```

**严禁**在 `canonicalMatrix`（`:1743-1752`）、`requestEvidenceVersion`（`:1698`、`:1828`）、`versionId(...)`、`TrustReplySavedStatePayload` 中出现该字段（I-3 / I-4）。

### 阶段 B：前端提示（I-2 / I-5 / S-1）

**B-1. `requestFromCoverage`（`:457-497`）带上新字段。**
在 `factRuleIds: [...(item.factRuleIds || [])]`（`:463`）之后追加：
```javascript
                // P1 (I-2): 服务端未采纳的绑定，仅用于提示，不参与任何请求载荷。
                droppedFactRuleIds: [...(item.droppedFactRuleIds || [])],
```

**B-2. `serializeRequestFactSelections`（`:500-506`）一字不改。**
它必须继续只回传 `request.factRuleIds`——这正是自愈闭环（I-5 / 证据 E-4）。**新字段绝不进任何请求载荷。**

**B-3. 事实区渲染追加提示。** 按 `## 样式契约` S-1 的逐字片段实现 `droppedMarkup` 与 `droppedFactLabels(request)`，把 `renderFactSection` 返回模板末尾的 `${staleMarkup}` 改为 `${staleMarkup}${droppedMarkup}`。

`droppedFactLabels` 的查名逻辑复用 chips 渲染（`:1805-1819`）中已有的按 `state.rules` 查显示名的写法，找不到时回退 `事实 <id>`。**不新增任何按 id 查规则的第二套逻辑。**

**B-4. `changeRequestFacts`（`:1540-1566`）一字不改。**
它现有的「删旧状态 → 改 ids → 重新 bootstrap」流程在降级后自然收敛：bootstrap 成功返回，chips 显示采纳的、提示显示未采纳的。

### 阶段 C：测试

**C-1. `QaFactSelectionServiceTest`（文件已存在，只加用例）—— 新增 4 个用例**
- `unsupported request keeps the filtered facts and reports the dropped bindings`：
  构造一条不匹配任何意图的摘要 + 显式绑定 2 条事实 → 断言**不抛**、`item.factRuleIds` 为空、`item.droppedBindingRuleIds` 等于那 2 个 id 且**顺序与输入一致**、`item.status == UNSUPPORTED`（must-NOT-change 第 2 条）。
- `accepted bindings report no dropped ids`：绑定能被采纳的事实 → 断言 `droppedBindingRuleIds` 为空。
- `matrix size mismatch still throws`：`selectionsByRequest.size != requests.size` → 断言仍抛 `TRUST_REPLY_FACT_SELECTION_INVALID`（must-NOT-change 第 1 条）。
- `disabled rule still throws`：显式绑定一条 `enabled = false` 的规则 → 断言仍抛 `TRUST_REPLY_FACT_SELECTION_INVALID`（证据 E-2b）。

**C-2. `TrustReplyWorkbenchServiceTest` —— 新增 2 个用例**
- `bootstrap surfaces dropped bindings per request without failing`：断言 bootstrap 正常返回、对应 coverage 项的 `droppedFactRuleIds` 非空。
- `dropped bindings never change the per-request evidence version`：同一条摘要，"绑定被丢弃"与"完全没绑定"两种输入下，断言 `requestCoverage[].evidenceSetVersion` **完全相同**（I-3 / IP-4）。

**C-3. `src/test/js/trustReplyWorkbench.test.js` —— 新增 3 个用例**
- `dropped bindings render a muted hint under the fact section`：断言 `host.innerHTML` 含 `data-role="item-facts-dropped"`、含事实显示名、且**不含** `style=`、不含新 class 名。
- `no dropped bindings renders no hint`：断言不含 `data-role="item-facts-dropped"`（S-1 的"条件为假输出空字符串"）。
- `dropped bindings are never sent back to the server`：驱动一次生成请求，断言 payload 的 `requestFactSelections` 里**只有** `requestKey` 与 `factRuleIds` 两个键，不含 `droppedFactRuleIds`（I-5 / B-2）。

---

## 变更文件清单

| # | 文件 | 改动性质 | 任务 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | `RequestFactItem` 新增 1 个带默认值字段 | A-1 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | 改 `:199-204` 一处（唯一降级点） | A-2 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | `TrustReplyRequestCoverage` 新增 1 字段；`toCoverage` 加 1 行 | A-3 |
| 4 | `src/main/resources/static/trust-reply-workbench.js` | 带入新字段、渲染提示、新增查名助手 | B-1, B-3 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | 新增 4 个用例（文件已存在，实测 `ls src/test/kotlin/.../llm/service/`） | C-1 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | 新增 2 个用例 | C-2 |
| 7 | `src/test/js/trustReplyWorkbench.test.js` | 新增 3 个用例 | C-3 |

**文件数：7（≤10 ✓）**
**子系统数：1 ✓** —— 事实选择矩阵与其 UI 呈现。
**新增存储字段：0 ✓　新增表/索引/迁移：0 ✓　`styles.css` 改动：0 ✓　请求体契约变更：0 ✓**

---

## 验证命令

> 全量测试、构建、前端全量、语法检查、空白卫生一律见 `00-execution-order.md` 的「验证命令」节。

```bash
# 本刀相关的后端测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=QaFactSelectionServiceTest,TrustReplyWorkbenchServiceTest,TrustReplyWorkbenchItemFlowTest

# C-1 四条单独运行
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='QaFactSelectionServiceTest#unsupported request keeps the filtered facts and reports the dropped bindings'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='QaFactSelectionServiceTest#accepted bindings report no dropped ids'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='QaFactSelectionServiceTest#matrix size mismatch still throws'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='QaFactSelectionServiceTest#disabled rule still throws'

# C-2 两条单独运行
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='TrustReplyWorkbenchServiceTest#bootstrap surfaces dropped bindings per request without failing'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='TrustReplyWorkbenchServiceTest#dropped bindings never change the per-request evidence version'

# C-3 前端用例
node --test src/test/js/trustReplyWorkbench.test.js
```

**通过判据**：同 `00-execution-order.md`。

---

## 验收标准

- **I-1**：`grep -rn "TRUST_REPLY_FACT_SELECTION_INVALID" --include=*.kt src/main | wc -l` 由 **7** 降为 **6**，且消失的那处正是 `QaFactSelectionService.kt` 原 `:199-204` 区段；该文件内仍命中 4 处（`:168`、`:181`、legacy 的两处）。C-1 第三、四条用例绿。
- **I-2**：`grep -rn "droppedBindingRuleIds\|droppedFactRuleIds" --include=*.kt src/main` 恰好命中 4 行（`RequestFactItem` 声明、`resolveMatrixSelection` 赋值、`TrustReplyRequestCoverage` 声明、`toCoverage` 透传）。
- **I-3**：`git diff` 中 `requestEvidenceVersion(`、`canonicalMatrix(`、`versionId(`、`TrustReplySavedStatePayload` 四处的入参与字段列表 **零改动**；`grep -n "dropped" src/main/kotlin/.../TrustReplyWorkbenchService.kt` 的命中行不落在这四者的函数体内。C-2 第二条用例绿。
- **I-4**：`canonicalMatrix`（`:1743-1752`）与 `toCoverage`（`:1869`）中赋给 `factRuleIds` 的表达式**都仍是** `item.factRuleIds`；`git diff src/main/resources/static/trust-reply-workbench.js` 中 `applyBootstrap` 的相等性守卫（`:585-595`）**零改动**。
- **I-5**：`serializeRequestFactSelections`（`:500-506`）**零改动**；C-3 第三条用例绿。
- **S-1**：`git diff src/main/resources/static/styles.css` **为空**；新增片段与契约代码块逐字一致；`grep -c 'style="' src/main/resources/static/trust-reply-workbench.js` 数值不增加；新增的 `data-role` 只有 `item-facts-dropped` 一个。
- **IP-1**：C-2 第一条用例绿 + C-3 前两条用例绿。
- **IP-2 / IP-3 / IP-4 / IP-5**：分别由 C-3 第一条、C-3 第三条、C-2 第二条、C-1 第三/四条覆盖。
- **回归**：执行 `00-execution-order.md` 的全量测试与构建通过；前端全量与 `node --check` 通过；`git diff --check` 无输出。

---

## 人工验收清单

### A-1: 手动加事实后工作台仍能打开（本刀主目标）
- 前置条件：一封来信，其中至少一条摘要显示 `UNSUPPORTED · 无依据` 且「对应事实 0 / 未绑定事实」。浏览器开 F12 → Network。
- 操作步骤：
  1. 打开该来信的回复台
  2. 在该 `UNSUPPORTED` 条目上点「+ 添加事实」，任选 2 条事实加进去
  3. 观察 Network 里的 `POST .../workbench/bootstrap`
- 预期结果：bootstrap 返回 **200**，工作台**正常显示**。（改动前此处必然 422 `{"code":"TRUST_REPLY_FACT_SELECTION_INVALID"}` 且界面变成"工作台加载失败"。）
- 覆盖：observable outcome 1；I-1、I-4、IP-1

### A-2: 未采纳的绑定有可见且说得清的提示
- 前置条件：A-1 已完成
- 操作步骤：查看该条摘要的事实区
- 预期结果：事实区下方出现一行灰色小字，逐字含「以下事实未被采纳：」，后面列出**你刚绑的那两条事实的显示名**，并说明「该问题未识别出可支持的意图，事实无处挂载，本条回答不会引用它们。」；chips 区显示「未绑定事实」。
- 覆盖：observable outcome 2；I-2、S-1、IP-2

### A-3: 状态自洽，后续操作不再报错
- 前置条件：A-1、A-2 已完成
- 操作步骤：在该条目上依次做：① 选一种处理方式生成一次；② 锁定；③ 其余条目也锁定后点汇总；④ 刷新页面重新进入
- 预期结果：四步**都不报错**；刷新后提示消失（因为前端已回传服务端采纳的空集合），条目回到「未绑定事实」。
- 覆盖：observable outcome 3；I-5、IP-3

### A-4: 未采纳的绑定不影响版本身份（回归）
- 前置条件：一封来信，某条 `UNSUPPORTED` 摘要**已生成并锁定**了一个版本
- 操作步骤：给**另一条**摘要手动加 2 条会被丢弃的事实，观察第一条摘要
- 预期结果：第一条摘要的锁定版本**仍在**，不出现「事实已变化，本条回答需重新生成」的陈旧提示。
- 覆盖：I-3、IP-4

### A-5: 真正非法的输入仍然硬拦（回归）
- 前置条件：找一条 `enabled = false`（已停用）的 QA 事实，且知道它的 id
- 操作步骤：用接口直接给某条摘要绑定这条停用的事实（UI 的事实选择器不会列出它）
- 预期结果：请求返回 422，响应体 `{"code":"TRUST_REPLY_FACT_SELECTION_INVALID"}`；界面显示 P0 文案表里的「事实选择与来信摘要对不上，请刷新工作台后重试。」**不得**静默通过。
- 覆盖：must-NOT-change 第 1 条；I-1、IP-5

### A-6: 有据条目的正常绑定不受影响（回归）
- 前置条件：一条 `GROUNDED` 或 `PARTIAL` 摘要，且事实库中有一条关键词能命中它的事实
- 操作步骤：给它手动加上那条事实
- 预期结果：chips 里出现该事实，**没有**「未被采纳」提示；条目状态与生成行为与改动前一致。
- 覆盖：must-NOT-change 第 2 条；I-4

### A-7: 未采纳的绑定不进 AI 上下文、不进外发审计（回归）
- 前置条件：A-1 完成，且该条目已生成并随整封信发出
- 操作步骤：查看该封外发邮件的 QA 事实使用审计（`mail_record_qa_rule` 关联）与生成日志
- 预期结果：未被采纳的那 2 条事实**不在**审计记录里；生成 prompt 里也没有它们的正文。
- 覆盖：must-NOT-change 第 4 条

### A-8: UI 目测（对照样式契约）
- 前置条件：A-2 的界面
- 操作步骤：对照 S-1 逐项核对
- 预期结果：提示文字的字号、颜色与同一条目上的「事实已变化，本条回答需重新生成」**完全一致**（同为 `.muted`）；没有边框、背景色、图标；页面无 inline style。
- 覆盖：S-1

### A-9: 改动范围核对（防越界）
- 前置条件：本刀实现完成，且 P0 与线 A 均已提交
- 操作步骤：`git diff --name-only`
- 预期结果：输出恰好为变更文件清单的 7 个路径。特别确认 `styles.css`、`AiReplyGroundedContentPlanner.kt`、`PendingMailOperationService.kt`、`AutoReplyConfidenceScorer.kt` **不在**其中。
- 覆盖：must-NOT-change 第 3、4 条；I-3
