# P2a：拆开「运营绑了什么」与「系统认可什么是证据」（不改 status）

> 顺序权威：本目录 `00-execution-order.md`。基线与"符号名优先、行号仅交叉验证"的约定见该文件。
> **前置：P0、P1 必须先合并。** 本刀改的是 P1 降级点的语义（从"丢弃"变成"保留但不作为证据"），P1 的提示文案也由本刀重写。
> **需求方已拍板：绑定不改变条目 status。** 绑了事实的 `UNSUPPORTED` 条目**仍然是** `UNSUPPORTED`。

---

## 需求描述

### Observable outcome

1. 运营给任意一条摘要（含 `UNSUPPORTED`）手动「+ 添加事实」后，**chips 里保留该事实**，刷新页面后仍在，不再消失。
2. 条目下方的提示从「未被采纳」改为说明**已绑定但不作为依据**，措辞不再像报错。
3. 该条目的 status、可选处理方式、生成行为与绑定前**完全一致**——特别是 `UNSUPPORTED` 条目仍可选「按回答说明生成」。
4. 已有的锁定版本不会因为本次升级而批量失效。

### What must NOT change

1. **status 判定一字不改**：`buildRequestFact` 的 `status` 计算（`QaFactSelectionService.kt:426-433`）、`allowedHandlings` / `recommendedHandling`（`TrustReplyWorkbenchService.kt:2032-2053` 区段）全部保持原样。绑定**不会**把 `UNSUPPORTED` 抬成 `PARTIAL` / `GROUNDED`。
2. **`factRuleIds` 的取值逻辑一字不改**：`candidateRules` 的关键词过滤（`:394-398`）、`evidenceSet` 的 SUPPORTED 过滤（`:435-438`）、`factRuleIds` 的合成（`:440-442`）保持原样。它继续表示"系统认可的证据"。
3. **证据下游零变化**：`workbenchResult` 的 `sendIds`（`:301`）、`generateItem` 的 `ResolvedQaRules`（`AiReplyDraftService.kt:474-480`）、`canonicalizeClaims` 的 `general.answer` 兜底（`TrustReplyWorkbenchService.kt:1424-1425`）、`AiReplyGroundedContentPlanner`（`:73-80`）、`AutoReplyConfidenceScorer`（`:54`）、`AiReplyReviewAuditService`（`:68`）、`PendingMailOperationService`（`:534`、`:654`）—— **本刀全部不改**（其中 `promptRuleIds` 由 P2b 处理）。
4. **`suggestedInstruction` 的邻近事实名单仍取证据**：`toCoverage` 内 `adjacentIds`（`:1882`）与 `adjacentRules.filterKeys { it !in item.factRuleIds }`（`:1900`）继续读 `factRuleIds`。运营绑了但没成为证据的事实**不得**出现在机器代填的回答说明里。
5. **无存储变更**：不新增表、字段、索引、迁移。`TrustReplySavedStatePayload`（`:197-206`）结构不变。
6. **既有锁定项不得批量失效**（见 I-5）。

### Out of scope（显式推迟）

1. **让绑定的事实进入 AI 上下文** → `P2b`。本刀之后，绑定的事实是"看得见、留得住、进版本身份"，但**还不会**被 AI 引用。
2. **让绑定影响 status**。需求方已明确否决（会连带改 `allowedHandlings`，使「按回答说明生成」从条目上消失，与线 A 冲突）。
3. **legacy 扁平并集路径**（`resolveLegacySelection`，`QaFactSelectionService.kt:212` 起）的同款改造。触发面极小（只在解码到 v1 历史快照时走到），一并改会让验证面翻倍。
4. **`AiTrainingController.kt:259` / `UnmatchedInboundMailController.kt:422` 两处展示用的 `factRuleIds`**。它们是只读投影，语义仍是证据，不改。

---

## 关键不变量

### Invariant I-1: `boundRuleIds` 恒被显式赋值，三条选择路径各自负责
- Rule: `RequestFactItem` 新增 `boundRuleIds: List<Long> = emptyList()`，但**默认值只为源码兼容存在，生产路径一律显式赋值**：
  - `resolveMatrixSelection`（显式矩阵，`:162`）→ `boundRuleIds = explicitIds`（**运营绑的原样，含顺序**）
  - `resolveAutoSelection`（自动匹配）→ `boundRuleIds = item.factRuleIds`
  - `resolveLegacySelection`（v1 扁平并集）→ `boundRuleIds = item.factRuleIds`
  赋值一律在**调用点**做（`item.copy(...)`），**不在 `buildRequestFact` 内部**——该函数收到的 `promptPool` / `promptSet` 在自动路径下是"候选池"而非"绑定"，无法通用推导。
- Applies to: `QaFactSelectionService` 的三条 `resolve*Selection`
- Violation consequence: 漏赋值的路径会让 `boundRuleIds` 为空，而 `canonicalMatrix` / `toCoverage` / 版本哈希都改读它（I-2），结果是该路径下所有事实凭空消失。
- 来源: original

### Invariant I-2: 「运营视角」的四个投影切到 `boundRuleIds`，一个不多一个不少
- Rule: 本刀**只**把下列四处从 `item.factRuleIds` 改为 `item.boundRuleIds`：
  1. `canonicalMatrix`（`TrustReplyWorkbenchService.kt:1743-1752`）的 `factRuleIds = item.factRuleIds`
  2. `resolveCanonicalSelection` 内 `requestEvidenceVersion(key, item.factRuleIds, …)`（`:1698`）
  3. `buildInitialItemVersions` 内 `requestEvidenceVersion(key, item.factRuleIds, …)`（`:1828`）
  4. `toCoverage` 内 `factRuleIds = item.factRuleIds`（`:1913`）
  **其余 26 处 `.factRuleIds` 读点一律不动**（实测 `grep -rn "\.factRuleIds" --include=*.kt src/main | wc -l` → 30，减去本刀切的 4 处）。其中 `TrustReplyWorkbenchService.kt` 内的逐行清单见现状审计证据 E-1b。
- Applies to: `TrustReplyWorkbenchService.kt`
- Violation consequence: 多切一处（例如 `workbenchResult` 的 `sendIds`）→ 运营乱绑的事实进外发审计与 prompt；少切一处 → chips 与矩阵/版本不同源，触发 I-3 的守卫。
- 来源: original

### Invariant I-3: `canonicalMatrix` 与 `toCoverage` 必须同时切换，且继续逐字相等
- Rule: 这两个投影是前端 `applyBootstrap` 相等性守卫（`trust-reply-workbench.js:585-595`）的两个比较对象，必须**同一次提交内同时**从 `factRuleIds` 切到 `boundRuleIds`，切换后仍逐字相等。
- Applies to: I-2 的第 1 与第 4 项
- Violation consequence: 只切一个 → 前端 `throw new Error("TRUST_REPLY_FACT_SELECTION_INVALID")`，工作台打不开，**且这次不是 422 而是前端异常，P0 的重置按钮仍能救，但症状会被误判成 P1 回归**。
- 来源: original（承接 P1 的 I-4）

### Invariant I-4: 相等性校验回到「比运营输入」，且必须成为恒真断言
- Rule: P1 降级掉的那处判据（`QaFactSelectionService.kt:199-204` 区段）在本刀改为比对 `item.boundRuleIds != explicitIds`。因为 I-1 规定该路径下 `boundRuleIds` 就是 `explicitIds`，**它恒真**——保留它作为防御性断言（抛 `TRUST_REPLY_FACT_SELECTION_INVALID`），用于挡住未来有人在 `buildRequestFact` 里动了 `copy` 顺序。
  **不得**改回比 `item.factRuleIds`（那就是原缺陷）。
- Applies to: `QaFactSelectionService.resolveMatrixSelection`
- Violation consequence: 比 `factRuleIds` → 缺陷原样复活；完全删掉 → 未来的顺序错误无人拦截。
- 来源: original

### Invariant I-5: 既有锁定项不得批量失效
- Rule: 对**没有发生过丢弃**的条目（即 `boundRuleIds == factRuleIds`），切换后 `requestEvidenceVersion` 的输入逐字不变，因此 `evidenceSetVersion` 与 `versionId` **必须完全不变**，既有锁定项继续有效。
  只有"绑定过但被丢弃"的条目版本会变——那些条目在 P1 之前根本无法存在（会 422），在 P1 之后其绑定也没进过版本，因此不存在需要保住的历史锁定。
- Applies to: `requestEvidenceVersion` 的两个调用点（I-2 的第 2、3 项）
- Violation consequence: 若顺序或去重方式在切换中被改动，全站所有已锁定条目会一次性判为陈旧，运营的既有工作全部作废。
- 来源: K-request-fact-assignment-version-must-include-mapping

### Invariant I-6: `droppedBindingRuleIds` 语义由「未被采纳」改为「已绑定但不作为依据」
- Rule: P1 引入的 `RequestFactItem.droppedBindingRuleIds` / `TrustReplyRequestCoverage.droppedFactRuleIds` **字段名与传递链保持不变**，但语义与前端文案由本刀重写：
  - 取值仍为 `explicitIds - item.factRuleIds`（即"绑了但没成为证据的"）
  - 前端文案不再说"未被采纳"，改为说明**已绑定、会保留、但本条回答不会把它当作依据**
  - chips **同时**显示这些事实（因为 chips 现在读 `boundRuleIds`）
- Applies to: `QaFactSelectionService.resolveMatrixSelection` 的赋值；`trust-reply-workbench.js` 的提示文案
- Violation consequence: 文案不改 → 界面自相矛盾（chips 里明明有，下面写着"未被采纳"）。
- 来源: original

---

## 样式契约

> 触发条件：本刀改动 `src/main/resources/static/trust-reply-workbench.js`。
> 总原则：**不新增任何 CSS class，不改 `styles.css`。** 本刀只改 P1 已落地的那段提示的**文字**。

### S-1: 条目级提示改文案（无 DOM/CSS 变化）
- **复用**：完全沿用 P1 落地的 `<span class="muted" data-role="item-facts-dropped">…</span>`，class 与 `data-role` **一字不改**。
- **新增 CSS**：无。
- **DOM 结构**：不变，只替换模板里的中文串。新文案（**逐字**）：

```javascript
const droppedMarkup = (Array.isArray(request.droppedFactRuleIds) && request.droppedFactRuleIds.length > 0)
    ? `<span class="muted" data-role="item-facts-dropped">以下事实已绑定但不会作为本条回答的依据：${escapeText(droppedFactLabels(request))}。该问题未识别出可支持的意图，绑定会保留，但 AI 不会引用它们的正文。</span>`
    : "";
```
- **禁止项**：改 class 或 `data-role`；加颜色、图标、边框；inline style；把该提示挪出事实区。

### S-2: chips 区（无改动，仅确认）
- chips 渲染（`:1805-1819`）读 `request.factRuleIds`，而 `request.factRuleIds` 来自 coverage 的 `factRuleIds`（`:463`）——本刀让服务端该字段改产 `boundRuleIds`，**前端一行不用改**，chips 自动开始显示绑定的事实。
- **禁止项**：不得在前端另加一个 `boundRuleIds` 字段或第二套 chips 逻辑。

---

## 现状审计

### Phase 0 知识加载（采用与驳回）

**采用**：
- `K-request-fact-assignment-version-must-include-mapping` → I-5（版本身份必须精确覆盖映射，且切换不得扰动既有取值）。
- `K-ai-reply-prompt-vs-send-rule-ids` → must-NOT-change 第 3 条（`sendQaRuleIds` 只能是真实匹配/显式勾选的证据）。
- `K-js-tests-run-via-exec-plugin` → 验证命令。
- `K-workbench-evidence-two-layer-global-coupling` → I-5 的风险面（证据版本是两层结构，改一层会牵动全局）。

**读取后确认不适用**：
- `K-locked-answer-paragraphs-at-version-time` —— 讲锁定答案的段落切分，本刀不碰 `answerText`。
- `K-operator-directed-authorization-seam` —— 动作授权，与事实矩阵无交集。

### 数据存储

**本刀不触及任何数据存储写路径。** 不新增表、字段、索引、迁移。
`TrustReplySavedStatePayload.requestFactSelections`（`TrustReplyWorkbenchService.kt:203`）的**类型与字段名不变**，但其内容语义由"证据集合"变为"运营绑定集合"（因为它由 `canonicalMatrix` 产出，见 I-2 第 1 项）。
**这是一次语义迁移而非结构迁移**：既有 v4 快照里的值在自动匹配路径下 `bound == evidence`，读回来仍然自洽（I-5）。

### 关键路径：`.factRuleIds` 的 30 处读点分类

实测 `grep -rn "\.factRuleIds" --include=*.kt src/main | wc -l` → **30**（其中一部分读的是 DTO 的同名字段而非 `RequestFactItem`）。按语义分类如下——**本刀只切其中 4 行**：

**A 类 · 运营视角（本刀切到 `boundRuleIds`，共 4 处）**

| 位置 | 用途 |
|---|---|
| `TrustReplyWorkbenchService.kt:1750`（`canonicalMatrix`） | 回传给前端 / 存进快照的矩阵 |
| `TrustReplyWorkbenchService.kt:1698`（`resolveCanonicalSelection`） | 逐条目证据版本 |
| `TrustReplyWorkbenchService.kt:1828`（`buildInitialItemVersions`） | 同上，整封生成路径 |
| `TrustReplyWorkbenchService.kt:1913`（`toCoverage`） | 前端 chips |

**B 类 · 证据视角（本刀一律不动，共 22 处）**

| 位置 | 用途 | 归属 |
|---|---|---|
| `QaFactSelectionService.kt:301`（`workbenchResult`） | `sendQaRuleIds` / `promptRuleIds` | P2b 只改 prompt 侧 |
| `QaFactSelectionService.kt:256`、`:288` | auto/legacy 的 `consumedIds` | 不动 |
| `QaFactSelectionService.kt:199` | 相等性断言 | 改比 `boundRuleIds`（I-4） |
| `AiReplyDraftService.kt:475`、`:476` | 逐条 grounded 的 send/prompt | P2b 只改 `:476` |
| `AiReplyDraftService.kt:861` | 判断整封有无事实 | 不动 |
| `AiReplyDraftService.kt:1905` | `evidenceRuleIds` 默认值 | 不动 |
| `AiReplyReviewAuditService.kt:68` | 审计 `evidenceIds` | 不动 |
| `AiReplyGroundedContentPlanner.kt:73`、`:80` | `general.answer` 兜底的 `sourceIds` | 不动 |
| `AutoReplyConfidenceScorer.kt:54` | CRS 打分的证据项计数 | 不动（绑定不得拉高置信分） |
| `PendingMailOperationService.kt:534`、`:654` | 候选规则 / 展示 | 不动 |
| `TrustReplyWorkbenchService.kt:1041` | 整封生成的日志/统计 | 不动 |
| `TrustReplyWorkbenchService.kt:1424`、`:1425` | `canonicalizeClaims` 的 `general.answer` 兜底 | 不动（claims 必须有据） |
| `TrustReplyWorkbenchService.kt:1882`、`:1900` | `suggestedInstruction` 的邻近事实名单 | 不动（must-NOT-change 第 4 条） |
| `TrustReplyWorkbenchService.kt:1665`、`:1675`、`:1737` | 读的是 `TrustReplyRequestFactSelection.factRuleIds`（**DTO**，不是 `RequestFactItem`）：分别是矩阵取值、跨摘要重复检查、factId ≤ 0 校验 | 不动（其取值随 `canonicalMatrix` 的语义迁移而自然变为绑定集合，重复检查与正数校验仍成立） |
| `AiTrainingController.kt:259`、`UnmatchedInboundMailController.kt:422` | 只读展示投影 | 不动（Out of scope 第 4 条） |

**证据 E-1 — 分类依据是"谁需要知道"。** A 类四处的共同点是：它们的消费者是**运营与前端**（chips、矩阵回传、版本身份）；B 类的消费者是 **AI 与外发审计**。这正是缺陷的根源——一个字段同时服务两类消费者。

**证据 E-1b — `TrustReplyWorkbenchService.kt` 内 `.factRuleIds` 共 12 行，本刀切 4 行。** 实测该文件命中：
`:1041`、`:1424`、`:1425`、`:1665`、`:1675`、`:1698`✅、`:1737`、`:1750`✅、`:1828`✅、`:1882`、`:1900`、`:1913`✅。
打 ✅ 的四行即 A 类；**其余八行一律不动**。执行时以这份逐行清单核对，勿凭印象。

**证据 E-2 — `RequestFactItem` 只有一个构造点。** 实测 `grep -rn "RequestFactItem(" --include=*.kt src/main` 只有 2 行：`AiReplyDraftService.kt:349`（声明）与 `QaFactSelectionService.kt:444`（唯一 `return`）。加一个带默认值的字段对全部现有构造点（含测试）源码兼容；**赋值在三条 `resolve*Selection` 的调用点做**（I-1）。

**证据 E-3 — 已有两个同性质影子字段可照抄。** `RequestFactItem`（`:349-359`）现有 `unrecognizedAsks`（注释逐字：「shadow-period measurement only — never feeds status, groundedRequestCount, unsupportedRequests or any hash」）与 P1 新增的 `droppedBindingRuleIds`。区别在于：**`boundRuleIds` 不是影子字段**——它会进版本哈希（I-2 第 2、3 项），必须在注释里写清楚这个区别，避免后人照搬影子字段的约束。

### 关键路径：版本身份不受扰动的证明

**证据 E-4 — 未发生丢弃时，切换是恒等变换。**
显式矩阵路径下 `explicitIds` → `validateExplicitSelection(explicitIds)` 按 `explicitIds` 顺序返回规则（`QaFactSelectionService.kt:336-347` 的 `ruleIds.map { … }`）→ `promptPool` 保持该顺序 → `candidateRules = promptPool.filter { … }`（`:394-398`，`filter` 保序）→ `factRuleIds = candidateRules.mapNotNull{}.filter{}`（`:440-442`，保序）。
因此**当全部绑定都被采纳时，`item.factRuleIds` 与 `explicitIds` 逐元素、逐顺序相等**（这正是改动前那条等式恒成立的场景）。此时 `boundRuleIds == factRuleIds`，`requestEvidenceVersion` 的输入不变，哈希不变。

自动匹配路径下 I-1 规定 `boundRuleIds = item.factRuleIds`，同样是恒等。

**结论：只有"绑定过且被丢弃"的条目版本会变，而这类条目在 P1 之前无法存在（会 422），P1 之后其绑定也从未进入过版本身份。因此本刀不产生任何历史锁定项失效。**（I-5）

### Interaction points

| # | 写/产生 | 读/消费 | 影响 | 验收 |
|---|---|---|---|---|
| IP-1 | `canonicalMatrix` 切到 `boundRuleIds` | 前端 `applyBootstrap` 守卫（`:585-595`）比对它与 coverage | 必须与 `toCoverage` 同时切换 | I-3 / A-6 |
| IP-2 | `canonicalMatrix` 切到 `boundRuleIds` | `TrustReplySavedStatePayload.requestFactSelections` → `restoreSavedStateWithFrame` 的 `payload.requestFactSelections != matrix` 比对（`:658` 区段） | 语义迁移；自动路径下取值不变，故历史快照仍匹配 | I-5 / A-7 |
| IP-3 | `requestEvidenceVersion` 切到 `boundRuleIds` | `validateLockedSubset` 的丢弃判据（`:849-852` 区段）与 `versionId` | 未丢弃条目必须哈希不变 | I-5 / A-4 |
| IP-4 | `toCoverage` 切到 `boundRuleIds` | `requestFromCoverage`（`:463`）→ chips（`:1805-1819`）→ `serializeRequestFactSelections`（`:500-506`） | chips 自动显示绑定；回传的矩阵也变成绑定，与服务端一致 | I-3 / A-1 / A-3 |
| IP-5 | `boundRuleIds` 与 `factRuleIds` 分叉 | B 类 22 处消费点 | 任一处被误切，绑定就会漏进 prompt/审计/打分 | I-2 / A-8 |

---

## 实现方案

### 阶段 A：字段与三条赋值路径（I-1）

**A-1. `AiReplyDraftService.kt` —— `RequestFactItem` 新增 `boundRuleIds`。**

放在 `factRuleIds` 之后、`status` 之前**不可行**（会破坏既有位置参数调用），因此放在**末尾**、`droppedBindingRuleIds`（P1 新增）之后：

```kotlin
    // P2a (I-1/I-2): 运营绑定的事实 id，按运营给出的顺序。与 factRuleIds 的区别：
    //   factRuleIds  = 系统认可、可用作回答依据的证据（关键词命中 + 落在 SUPPORTED 意图证据集）
    //   boundRuleIds = 运营主张"这条事实属于这个问题"，不代表系统认可它是依据
    // 自动匹配 / legacy 路径下两者相等；只有显式矩阵路径可能分叉。
    // 注意：本字段与 unrecognizedAsks / droppedBindingRuleIds 这类影子字段【不同】——
    // 它【会】进入 canonicalMatrix 与 requestEvidenceVersion 的身份哈希（I-2），
    // 默认值仅为源码兼容存在，生产路径一律在调用点显式赋值（I-1）。
    val boundRuleIds: List<Long> = emptyList()
```

**A-2. `QaFactSelectionService.kt` —— 三条路径显式赋值。**

- `resolveMatrixSelection`（`:162`）内，把 P1 落地的那段改为：

```kotlin
// P2a (I-1/I-4/I-6): 运营绑的原样进 boundRuleIds；factRuleIds 保持"系统认可的证据"语义不变。
// 相等性校验回到比对运营输入——由本行的赋值保证它恒真，作为防御性断言保留（I-4）。
val accepted = item.factRuleIds.toSet()
val bound = item.copy(
    boundRuleIds = explicitIds,
    droppedBindingRuleIds = explicitIds.filter { it !in accepted }
)
if (bound.boundRuleIds != explicitIds) {
    throw TrustReplyWorkbenchException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "TRUST_REPLY_FACT_SELECTION_INVALID"
    )
}
bound
```

- `resolveAutoSelection` 与 `resolveLegacySelection`：在各自产出 `RequestFactItem` 的位置加 `.copy(boundRuleIds = item.factRuleIds)`（或在构造后统一 `map`）。**这两条路径的其余逻辑一字不改**，`droppedBindingRuleIds` 保持默认空。

`workbenchResult(...)`（`:295-320`）**一字不改**：`sendIds` 仍由 `it.factRuleIds` 合成（must-NOT-change 第 3 条）。

### 阶段 B：四个运营视角投影（I-2 / I-3 / I-5）

**B-1. `canonicalMatrix`（`TrustReplyWorkbenchService.kt:1743-1752`）**
```kotlin
// 改前： factRuleIds = item.factRuleIds
// 改后（I-2/I-3）：
                factRuleIds = item.boundRuleIds
```

**B-2. `resolveCanonicalSelection`（`:1698`）**
```kotlin
// 改前： item.index to (key to requestEvidenceVersion(key, item.factRuleIds, baseSnapshotOf, researchEvidence))
// 改后（I-2/I-5）：
            item.index to (key to requestEvidenceVersion(key, item.boundRuleIds, baseSnapshotOf, researchEvidence))
```

**B-3. `buildInitialItemVersions`（`:1828`）**
```kotlin
// 改前： evidenceSetVersion = requestEvidenceVersion(key, item.factRuleIds, baseSnapshotOf, researchEvidence),
// 改后（I-2/I-5）：
                    evidenceSetVersion = requestEvidenceVersion(key, item.boundRuleIds, baseSnapshotOf, researchEvidence),
```

**B-4. `toCoverage`（`:1913`）**
```kotlin
// 改前： factRuleIds = item.factRuleIds,
// 改后（I-2/I-3）：
                factRuleIds = item.boundRuleIds,
```

**B-5. 同一函数内 `:1882` 与 `:1900` 的 `adjacentIds` / `filterKeys` 一字不改**（must-NOT-change 第 4 条）。这两处与 B-4 在同一个 `toCoverage` 里，**执行时极易顺手一起改，务必逐行核对**。

**B-6. 除 B-1〜B-4 外，`TrustReplyWorkbenchService.kt` 内其余 `.factRuleIds` 一律不动。**

### 阶段 C：前端文案（I-6 / S-1）

**C-1. 只改 P1 落地的 `droppedMarkup` 中文串**，按 S-1 的逐字片段替换。class、`data-role`、条件判断、`droppedFactLabels` 助手全部沿用。

**C-2. chips、`requestFromCoverage`、`serializeRequestFactSelections` 一行不改**（S-2）。

### 阶段 D：测试

**D-1. `QaFactSelectionServiceTest` —— 新增 4 个用例**
- `matrix selection keeps operator bindings verbatim in boundRuleIds`：UNSUPPORTED 摘要绑 2 条 → 断言 `boundRuleIds == explicitIds`（含顺序）、`factRuleIds` 为空、`status == UNSUPPORTED`（must-NOT-change 第 1 条）。
- `auto selection sets boundRuleIds equal to factRuleIds`：自动匹配路径 → 断言两者逐字相等（I-1）。
- `send and prompt rule ids still come from factRuleIds`：绑定被丢弃的情况下，断言 `sendQaRuleIds` 与 `promptRuleIds` **不含**被丢弃的 id（must-NOT-change 第 3 条）。
- `dropped bindings are reported while still bound`：断言 `droppedBindingRuleIds` 非空**且** `boundRuleIds` 含这些 id（I-6）。

**D-2. `TrustReplyWorkbenchServiceTest` —— 新增 3 个用例**
- `canonical matrix and coverage both project boundRuleIds`：断言 `requestFactSelections[i].factRuleIds` 与 `requestCoverage[i].factRuleIds` 逐字相等且等于绑定集合（I-3）。
- `evidence version is unchanged when every binding is accepted`：同一输入下，绑定全被采纳时的 `evidenceSetVersion` 与切换前的期望值（用自动匹配路径的同集合作为对照）**完全相同**（I-5 / IP-3）。
- `suggested instruction never names a bound-but-unsupported fact`：一条 UNSUPPORTED 摘要绑了 1 条事实 → 断言另一条摘要的 `suggestedInstruction` **不含**该事实的显示名（must-NOT-change 第 4 条 / B-5）。

**D-3. `TrustReplyWorkbenchItemFlowTest` —— 新增 1 个用例**
- `locked items survive the bound-vs-evidence split`：构造一个自动匹配路径下已锁定的条目，断言切换后 `assemble` 仍通过、`versionId` 未变（I-5）。

**D-4. `src/test/js/trustReplyWorkbench.test.js` —— 新增 2 个用例**
- `bound facts render as chips`：coverage 的 `factRuleIds` 含绑定 id → 断言 chips 里出现对应显示名。
- `hint wording says bound-but-not-evidence`：断言 `data-role="item-facts-dropped"` 的文本含「已绑定但不会作为本条回答的依据」，**不含**「未被采纳」。

---

## 变更文件清单

| # | 文件 | 改动性质 | 任务 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | `RequestFactItem` 新增 1 个字段 | A-1 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | 三条 `resolve*Selection` 赋值 + 断言改比 `boundRuleIds` | A-2 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 四处投影切换（B-1〜B-4），其余不动 | B |
| 4 | `src/main/resources/static/trust-reply-workbench.js` | 只改一处中文串 | C-1 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | 新增 4 个用例 | D-1 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | 新增 3 个用例 | D-2 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | 新增 1 个用例 | D-3 |
| 8 | `src/test/js/trustReplyWorkbench.test.js` | 新增 2 个用例 | D-4 |

**文件数：8（≤10 ✓）**
**子系统数：1 ✓** —— 事实选择与其运营视角投影。
**新增存储字段：0 ✓　新增表/索引/迁移：0 ✓　`styles.css` 改动：0 ✓　HTTP 契约变更：0 ✓**（`TrustReplyRequestCoverage.factRuleIds` 字段名与类型不变，只是取值语义迁移）

---

## 验证命令

> 全量测试、构建、前端全量、语法检查、空白卫生一律见 `00-execution-order.md`。

```bash
# 本刀相关的后端测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=QaFactSelectionServiceTest,TrustReplyWorkbenchServiceTest,TrustReplyWorkbenchItemFlowTest,AiReplyDraftServiceTest

# D-1 四条
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='QaFactSelectionServiceTest#matrix selection keeps operator bindings verbatim in boundRuleIds'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='QaFactSelectionServiceTest#auto selection sets boundRuleIds equal to factRuleIds'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='QaFactSelectionServiceTest#send and prompt rule ids still come from factRuleIds'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='QaFactSelectionServiceTest#dropped bindings are reported while still bound'

# D-2 三条
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='TrustReplyWorkbenchServiceTest#canonical matrix and coverage both project boundRuleIds'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='TrustReplyWorkbenchServiceTest#evidence version is unchanged when every binding is accepted'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='TrustReplyWorkbenchServiceTest#suggested instruction never names a bound-but-unsupported fact'

# D-3 一条
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='TrustReplyWorkbenchItemFlowTest#locked items survive the bound-vs-evidence split'

# D-4 前端
node --test src/test/js/trustReplyWorkbench.test.js
```

**通过判据**：同 `00-execution-order.md`。

---

## 验收标准

- **I-1**：`grep -rn "boundRuleIds = " --include=*.kt src/main` 恰好命中 **4 行**（`RequestFactItem` 默认值 1 行 + 三条 `resolve*Selection` 各 1 行）。
- **I-2**：`grep -rn "item.boundRuleIds" src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` 恰好命中 **4 行**，且行号分别落在 `canonicalMatrix` / `resolveCanonicalSelection` / `buildInitialItemVersions` / `toCoverage` 四个函数体内；同文件 `grep -c "item.factRuleIds"` 的数值比改动前**恰好少 4**。
- **I-3**：`canonicalMatrix` 与 `toCoverage` 赋给 `factRuleIds` 的表达式**都是** `item.boundRuleIds`；`git diff src/main/resources/static/trust-reply-workbench.js` 中 `applyBootstrap` 的守卫（`:585-595`）**零改动**。D-2 第一条用例绿。
- **I-4**：`QaFactSelectionService.kt` 内相等性断言比的是 `boundRuleIds`（`grep -n "boundRuleIds != explicitIds"` 命中 1 行），且 `grep -n "factRuleIds != explicitIds"` **零命中**。
- **I-5**：D-2 第二条与 D-3 用例绿。
- **I-6**：`grep -n "未被采纳" src/main/resources/static/trust-reply-workbench.js` **零命中**；D-4 第二条用例绿。
- **must-NOT-change 第 3 条**：`git diff` 中 `workbenchResult`、`AiReplyGroundedContentPlanner.kt`、`AutoReplyConfidenceScorer.kt`、`AiReplyReviewAuditService.kt`、`PendingMailOperationService.kt` **零改动**（后四个文件不在变更清单里）。D-1 第三条用例绿。
- **must-NOT-change 第 4 条**：`toCoverage` 内 `:1882` 与 `:1900` 两处仍是 `item.factRuleIds`。D-2 第三条用例绿。
- **S-1 / S-2**：`git diff src/main/resources/static/styles.css` 为空；`trust-reply-workbench.js` 的 diff **只有一处中文串替换**，无新 class、无新 `data-role`、无 `style=` 增量。
- **IP-1〜IP-5**：分别由 D-2 第一条、A-7、D-2 第二条 + D-3、D-4 第一条、D-1 第三条覆盖。
- **回归**：执行 `00-execution-order.md` 的全量测试与构建通过；前端全量与 `node --check` 通过；`git diff --check` 无输出。

---

## 人工验收清单

### A-1: 手动绑定的事实留得住（本刀主目标）
- 前置条件：一封来信，某条摘要显示 `UNSUPPORTED · 无依据`、「未绑定事实」
- 操作步骤：
  1. 点「+ 添加事实」加入 2 条事实
  2. 观察 chips 区
  3. 刷新页面重新进入该来信的回复台
- 预期结果：两步都能看到这 2 条事实的 chips；刷新后**仍在**。（P1 之后此处 chips 会消失，本刀修好。）
- 覆盖：observable outcome 1；I-2、I-3、IP-4

### A-2: 提示文案不再像报错
- 前置条件：A-1 已完成
- 操作步骤：查看该条摘要事实区下方的灰色小字
- 预期结果：逐字含「以下事实已绑定但不会作为本条回答的依据」，并说明「绑定会保留，但 AI 不会引用它们的正文」。**不含**「未被采纳」四字。
- 覆盖：observable outcome 2；I-6、S-1

### A-3: status 与可选处理方式不变（需求方决策的直接验收）
- 前置条件：A-1 已完成
- 操作步骤：查看该条摘要的状态标签与「处理方式」下拉
- 预期结果：状态**仍是** `UNSUPPORTED · 无依据`；下拉里**仍有**「按回答说明生成」「确认待补充」「省略此项」三项，一项不多一项不少。
- 覆盖：observable outcome 3；must-NOT-change 第 1 条

### A-4: 已有锁定项不失效（回归，风险最高的一条）
- 前置条件：升级**之前**，在一封来信里把所有条目都生成并锁定，然后保存状态
- 操作步骤：部署本刀后，重新打开这封来信
- 预期结果：全部锁定项**照常恢复**，没有条目出现「事实已变化，本条回答需重新生成」；汇总可直接进行。
- 覆盖：I-5、IP-3

### A-5: 绑定不进 AI 上下文、不进外发审计、不拉高置信分（回归）
- 前置条件：A-1 完成，该条目按「按回答说明生成」出稿并随整封信发出
- 操作步骤：① 查看生成 prompt 日志；② 查看该封外发邮件的 QA 事实使用审计；③ 若该来信走过自动回复评估，查看 CRS 分数构成
- 预期结果：三处**都不含**这 2 条绑定的事实。（本刀阶段绑定只影响"看得见"，不影响"AI 用得上"——那是 P2b。）
- 覆盖：must-NOT-change 第 3 条；IP-5

### A-6: 前端一致性守卫不被触发（回归）
- 前置条件：任意一封来信
- 操作步骤：反复做 加事实 / 删事实 / 调整顺序 各两次，每次观察工作台
- 预期结果：每次都正常加载，**从不**出现 `TRUST_REPLY_FACT_SELECTION_INVALID`（无论是 422 还是前端异常）。
- 覆盖：I-3、IP-1

### A-7: 历史快照仍能恢复（回归）
- 前置条件：升级前保存过状态、且**未**手动绑定过事实的一封来信
- 操作步骤：部署后打开该来信
- 预期结果：状态正常恢复（`RESTORED` 或 `PARTIALLY_RESTORED`），**不是** `STALE`。
- 覆盖：I-5、IP-2

### A-8: 改动范围核对（防越界）
- 前置条件：本刀实现完成，P0、P1 与线 A 均已提交
- 操作步骤：`git diff --name-only`
- 预期结果：输出恰好为变更文件清单的 8 个路径。特别确认 `AiReplyGroundedContentPlanner.kt`、`AutoReplyConfidenceScorer.kt`、`AiReplyReviewAuditService.kt`、`PendingMailOperationService.kt`、`AiTrainingController.kt`、`UnmatchedInboundMailController.kt`、`styles.css` **不在**其中。
- 覆盖：I-2、must-NOT-change 第 3 条、Out of scope 第 4 条
