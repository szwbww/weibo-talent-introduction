# 03a 证据版本按请求条目切分（局部失效）

> 本计划由 create-p 生成。所有计数与全称判断均附 grep 回执（K-plan-quantified-claims-need-grep-receipts）。
> 顺序：`workbench-repair-02` → 本计划 → `workbench-repair-03b`。本计划是 03b 的前置。

## 需求描述

**Observable outcome**

1. 在「摘要与事实」页给**某一条**摘要增删或拖动事实后，**只有那一条**摘要的已生成/已锁定回答被清空；其余摘要的回答原样保留，不需重新生成。
2. 上述操作不再把整个工作台打回「正在加载工作台…」骨架。
3. 事实变化的确认框只在真正会丢内容时弹出，且文案说明影响范围是**本条**摘要。

**What must NOT change**

1. 跨条目事实唯一性：一个事实仍最多属于一条摘要，重复分配仍抛 `TRUST_REPLY_FACT_ALREADY_ASSIGNED`（TrustReplyWorkbenchService.kt:1510-1513）。
2. 同一组事实从摘要 A 换绑到摘要 B **必须**产生不同版本，旧 locked version 不得被接受（K-request-fact-assignment-version-must-include-mapping）。
3. `evidenceSetVersion` 作为**整份草稿**的聚合指纹仍然存在，并继续出现在 bootstrap 响应、assemble 响应、saveState payload、`AiReplyReviewAuditService` 审计快照、`AiTrainingEvaluationService` 评估记录。
4. 服务端仍是唯一权威：前端状态一致不构成信任边界，服务端仍重新 materialize 并计算 `versionId`（K-trust-reply-resolved-version-single-source 第 4 条）。
5. 事实**顺序**仍参与版本身份（拖动排序仍使该条目失效）。
6. `sourceVersion` 变化时**仍然全量重置**——本计划不动，归 03b。

**Out of scope**

- `sourceVersion` 成分拆分、训练知识/mailHistory 降级 → 03b。
- 段落合并与段数上限 → 已在 02 的 Out of scope 记录。
- `FULL_DRAFT` 分支的 evidence 口径分叉（现状审计 C-6）：工作台不可达且已有既有测试守住，**只记录不修**。
- `evidenceSetVersion` **改名**：现状审计 C-4 证明线上格式已支持逐条取值，改名会把变更面从 9 个文件撑到 17 个，明确否决。

## 关键不变量

### Invariant I-1: 每条请求一个证据版本，且必须绑定 requestKey
- Rule: 新增 `requestEvidenceVersion(requestKey, factRuleIds)`，输入**恰好**为：`requestKey`、该条目的**有序** `factRuleIds`、以及**只属于这些 ruleId** 的规则快照（沿用 `localEvidenceSetVersion` 现有格式 `id:available:updatedAt:sha256(answerBody)`，TrustReplyWorkbenchService.kt:1591-1603）。**禁止**混入其他条目的 ruleId；**禁止**混入观测时间。
- Applies to: `resolveCanonicalSelection`（:1486-1524）产出；`versionId()`（:1890-1908）第 7 参；`validateLockedItem`（:1205-1207）比较。
- Violation consequence: 混入其他条目 ruleId → 局部失效退化为全局失效，本计划价值归零；混入观测时间 → 违反 K-ai-reply-evidence-version-deterministic，每次生成误报「来源已变化」。
- 来源: K-ai-reply-evidence-version-deterministic、K-request-fact-assignment-version-must-include-mapping

### Invariant I-2: 换绑必须同时失效两端、且只失效两端
- Rule: 事实 F 从条目 A 移到条目 B 后，A 与 B 的 `requestEvidenceVersion` **都**必须改变（A 少了 F；B 多了 F 且 requestKey 不同），**其余条目必须保持不变**。
- Applies to: `requestEvidenceVersion` 构造（I-1）+ 唯一性校验（:1510-1513）。
- Violation consequence: K-request-fact-assignment-version-must-include-mapping 记录的原始事故——相同并集换绑后旧 locked version 被接受。
- 来源: K-request-fact-assignment-version-must-include-mapping

### Invariant I-3: 聚合版本是派生量，不是单条判定依据
- Rule: `aggregateEvidenceVersion = sha256(按 index 升序的 requestEvidenceVersion 列表)`，**只**用于整份草稿指纹。**禁止**用它判定单条目是否失效。三个 `requireCurrentEvidenceVersion` 调用点分别处置：`:464`（saveState）保留聚合比对；`:959`（adjustItem）改为比对该 requestKey 的 per-request 值；**`:1051`（assemble）整行删除**。
- Applies to: 上述三行 + 定义行 `:1615`。
- Violation consequence: 留着 `:1051` 的聚合前置检查，改任一条目事实后 assemble 必 409，局部失效形同虚设。
- 来源: original

### Invariant I-4: 部分恢复取代全量作废
- Rule: `restoreSavedStateWithFrame`（:532-608）不得再因 `payload.evidenceSetVersion != evidenceSetVersion`（:555）整体判 STALE。改为逐条：per-request 版本匹配的 locked item 保留，不匹配的丢弃并计数；全部丢弃时才整体 `STALE`。`payload.sourceVersion != resolved.sourceVersion` 的整体作废**保留**（归 03b）。
- Applies to: `restoreSavedStateWithFrame`、`validateLockedSubset`（:713 起）。
- Violation consequence: 服务端仍全量作废，前端保留了也会在下次交互被打回。
- 来源: K-workbench-lock-replay-needs-dedicated-state-store（"bootstrap 恢复时必须重算 source/evidence version 并重物化 versionId" —— 重算保留，作废粒度改）

### Invariant I-5: 前端只重置受影响条目
- Rule: `changeRequestFacts`（trust-reply-workbench.js:1338-1355）不得再无条件调 `resetVersions()`（:1355）。改为：仍走 `bootstrap()` 取权威矩阵，但按 per-request 版本逐条比对——未变的条目保留其 `versions` / `activeVersionId` / `resolvedVersionId`，变了的才清空。
- Applies to: `changeRequestFacts`（:1338-1355）、`bootstrap`（:610-628）、`applyBootstrap`（:521 附近）、`resetVersions`（:395-416，函数本身保留）。
- Violation consequence: 服务端做了局部失效但前端全清，用户看不到改善。
- 来源: original（`handleFrameStale`（:381-388）注释「must never reset versions」是同类先例）

### Invariant I-6: 存量 saved state 必须显式作废，不得静默错配
- Rule: `SCHEMA_VERSION` 从 `trust-reply-workbench-state-v3` 升至 `v4`；`PREVIOUS_SCHEMA_VERSION` 改为 v3；`LEGACY_SCHEMA_VERSION` 保持 v1；`ACCEPTED_REQUEST_SCHEMA_VERSIONS` 移除 v2。`decodePayload`（:120-130）对 v3 及更早返回可识别的旧版标记，由 `restoreSavedStateWithFrame` 判整体 `STALE`。
- Applies to: `TrustReplyWorkbenchStateStore.kt:179-190`、`:120-130`。
- Violation consequence: 存量 payload 的 `evidenceSetVersion` 是**聚合值**，与 per-request 值同为 64 位 sha256 十六进制串，若被当作 per-request 比对会出现「长度相同、语义不同」的静默错配，locked item 可能被错误接受。
- 来源: original

### Invariant I-7: 不新增数据库迁移
- Rule: 本计划**不得**新增任何 `V<n>__*.sql`。`evidenceSetVersion` 不是数据库列（现状审计 C-5）。
- Applies to: `src/main/resources/db/migration/`。
- Violation consequence: 无谓迁移链增长；且 CLAUDE.md 的 K-flyway-placeholder-replacement 警告新增迁移有生产启动风险。
- 来源: original

## 样式契约

### S-1: 仅新增一条条目级提示，不新增任何 class
- 复用：条目级提示复用本文件已有的 `class="muted"`。执行前先跑
  `grep -n 'class="muted"' src/main/resources/static/trust-reply-workbench.js`
  记录既有使用点，**只引用不修改**其 CSS 规则。
- 新增：**无新 class、无新 CSS 规则**。因此本节不含新增 CSS 代码块——这是"零新增样式"的显式声明，不是省略。
- DOM 结构：仅当该条目的 per-request 版本与其已有 version 携带值不符时，在该摘要卡片的 `.trust-reply-fact-section`（:1612 输出）之后追加**这一段，逐字**：
  ```html
  <span class="muted" data-role="item-evidence-stale">事实已变化，本条回答需重新生成</span>
  ```
  条件不成立时整段不输出（不输出空标签）。
- 禁止项：inline style；任何新 class；任何 `styles.css` 改动；对 `.trust-reply-fact-section` / `.trust-reply-fact-chip` / `.trust-reply-page-tab` / `.ai-reply-feedback` 既有规则块的改动。
- 验证依据：变更文件清单不含 `styles.css` 与 `index.html`。

## 现状审计

### 存储 A：`trust_reply_workbench_state`（MySQL）

- Schema 来源：`src/main/resources/db/migration/V83__create_trust_reply_workbench_state.sql:13`（`CREATE TABLE trust_reply_workbench_state`）。正文以 JSON 存于 `payload_json`，`stateVersion` 做乐观锁。
- 常量（`TrustReplyWorkbenchStateStore.kt:179-190`）：`SCHEMA_VERSION = "trust-reply-workbench-state-v3"`、`PREVIOUS_SCHEMA_VERSION = v2`、`LEGACY_SCHEMA_VERSION = v1`、`MAX_PAYLOAD_BYTES = 256 * 1024`、`EXPIRY_DAYS = 30`。
- 写路径：
  1. `TrustReplyWorkbenchService.saveState`（:449 起，经 `requireCurrentEvidenceVersion`(:464)）——前端 `persistResolvedSnapshot`（trust-reply-workbench.js:647-…）触发。
  2. 同一 PUT 端点被前端 `deleteSavedState`（trust-reply-workbench.js:666-685）以空 `lockedItems` 调用，用于清空。
- 读路径：`bootstrap`（:358-447）→ `stateStore.load` → `decodePayload`（:120-130）→ `restoreSavedStateWithFrame`（:532-608）。

### 存储 B：`UnsupportedAnswerIndex`（ES）—— 本计划零影响

```
$ grep -n "requestKey" src/main/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexService.kt
43:    val requestKey: String,
251:        sha256("${document.sourceType}|${document.sourceId}|${document.requestKey}|${document.versionId}")
320:        put("requestKey", document.requestKey)
391:        requestKey = version.requestKey,
416:        if (!bounded(document.sourceVersion, 512) || !bounded(document.requestKey, 512) || !bounded(document.requestText, 10_000)
```

**4 处**：`_id` 组装、写字段、映射、长度校验。查询侧只按 `sourceMode` 做 `term` / `match_all`（:285-287），**从不按 requestKey 或 evidenceSetVersion 查**。本计划不改 `requestKey` 组成（那是 03b），因此零影响。

### C-1. `evidenceSetVersion` 的合成方式是**两层**全局耦合

```
$ sed -n '1514,1523p' src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt
        val baseEvidence = if (useLocalEvidence) {
            localEvidenceSetVersion(selection.sendQaRuleIds)
        } else {
            aiReplyDraftService.buildEvidenceSnapshotForSelection(selection.sendQaRuleIds).first
        }
        return ResolvedCanonicalSelection(
            selection = selection,
            requestFactSelections = matrix,
            evidenceSetVersion = evidenceSetVersionWithMapping(baseEvidence, matrix)
        )
```

`baseEvidence` 走 `sendQaRuleIds`——**全部条目规则的并集**；`evidenceSetVersionWithMapping`（:1576-1584）再拌**全矩阵**。
→ **只把 mapping 按条目切是不够的，`baseEvidence` 也必须按子集算。** 这是本计划最容易漏的一点。

两个 base 函数的签名本就是 `List<Long>`，天然支持子集：

```
$ grep -n "private fun localEvidenceSetVersion" src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt
1591:    private fun localEvidenceSetVersion(sendQaRuleIds: List<Long>): String {
```

`buildEvidenceSnapshotForSelection` 的现有调用点（执行前复核）：

```
$ grep -rn "buildEvidenceSnapshotForSelection" --include=*.kt src/main
llm/service/AiReplyDraftService.kt:1521
llm/service/AiReplyDraftService.kt:1591
llm/service/AiReplyDraftService.kt:1828
llm/service/TrustReplyWorkbenchService.kt:1517
```

本计划**只改** `TrustReplyWorkbenchService.kt:1517` 的调用方式（改为按子集多次调用），`AiReplyDraftService.kt` 内的 3 个调用点不动。

### C-2. `ResolvedCanonicalSelection` 现状

```
$ sed -n '1473,1477p' src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt
    private data class ResolvedCanonicalSelection(
        val selection: ResolvedQaRules,
        val requestFactSelections: List<TrustReplyRequestFactSelection>,
        val evidenceSetVersion: String
    )
```

### C-3. `requireCurrentEvidenceVersion` 调用点（全集，3 个调用 + 1 个定义）

```
$ grep -n "requireCurrentEvidenceVersion" src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt
464:        requireCurrentEvidenceVersion(request.evidenceSetVersion, resolvedSelection.evidenceSetVersion)
959:        requireCurrentEvidenceVersion(request.expectedEvidenceSetVersion, evidenceSetVersion)
1051:        requireCurrentEvidenceVersion(request.expectedEvidenceSetVersion, evidenceSetVersion)
1615:    private fun requireCurrentEvidenceVersion(expected: String?, actual: String) {
```

`:464` = `saveState`；`:959` = `adjustItem`；`:1051` = `assemble`。

### C-4. 线上格式**已经**是逐条一个 evidence 版本（关键结论，决定了变更面大小）

```
$ grep -n "evidenceSetVersion" src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt
186:                evidenceSetVersion = locked.evidenceSetVersion,
199:        evidenceSetVersion = evidenceSetVersion,
217:                evidenceSetVersion = locked.evidenceSetVersion,
296:    val evidenceSetVersion: String,
322:    val evidenceSetVersion: String,
```

`TrustReplyLockedItemRequest`（controller :296）与 `TrustReplyItemVersion`（service :315）**本来就每条带一个 `evidenceSetVersion`**，只是当前所有条目填同一个全局值。前端也已逐条拷贝：

```
$ grep -n "evidenceSetVersion: item.evidenceSetVersion" src/main/resources/static/app.js
3419:                evidenceSetVersion: item.evidenceSetVersion,
9551:        evidenceSetVersion: item.evidenceSetVersion,
$ grep -n "evidenceSetVersion: locked.evidenceSetVersion\|evidenceSetVersion: version.evidenceSetVersion" src/main/resources/static/trust-reply-workbench.js
603:                evidenceSetVersion: locked.evidenceSetVersion,
1328:                evidenceSetVersion: version.evidenceSetVersion,
```

→ **线上格式不需改、字段不需改名**，`TrustReplyWorkbenchController.kt`、`UnmatchedInboundMailController.kt`、`app.js` 全部可以不进变更清单。

### C-5. `evidenceSetVersion` 不是数据库列，也不在任何 ES mapping

```
$ grep -rn "evidence_set_version" src/main/resources/db/migration/ ; echo exit=$?
exit=1
$ grep -rn "evidenceSetVersion" src/main/resources/ --include=*.json ; echo exit=$?
exit=1
```

（两次 exit=1 即两次零匹配。）→ I-7 成立。

### C-6. `FULL_DRAFT` 的 evidence 口径分叉（观察项，**本计划不修**）

`generate()` 开头有 `operation == ADJUST_ITEM` 早分支（:800-841），委托 `adjustItem()` 并返回 mapping-bound 的 `adjustment.evidenceSetVersion`（:838）。只有 FULL_DRAFT 分支才落到 `aiReplyDraftService.generate` + `buildInitialItemVersions(result.evidenceSetVersion)`（:932-937，**base，无 mapping**）。

前端 `makeGenerationPayload` 恒传 `operation: "ADJUST_ITEM"`：

```
$ grep -n 'operation: "ADJUST_ITEM"' src/main/resources/static/trust-reply-workbench.js
641:                operation: "ADJUST_ITEM",
$ grep -rn "FULL_DRAFT" src/test/js/*.js
src/test/js/trustReplyWorkbench.test.js:79:        assert.doesNotMatch(workbench, /"FULL_DRAFT"/);
src/test/js/trustReplyWorkbenchSharedMount.test.js:536
src/test/js/trustReplyWorkbenchSharedMount.test.js:1030
src/test/js/trustReplyWorkbenchSharedMount.test.js:1091
src/test/js/trustReplyWorkbenchSharedMount.test.js:1654
```

**5 行**：1 行源码字符串断言 + 4 行 payload 断言。分叉存在但工作台不可达且已被既有不变量守住。执行 agent **不得**顺手修改 `buildInitialItemVersions` 的 evidence 来源；如认为必须修，停下来报告。

### C-7. 前端 `evidenceSetVersion` 的 15 个使用点逐行归类

```
$ grep -n "evidenceSetVersion" src/main/resources/static/trust-reply-workbench.js
192, 343, 344, 352, 353, 521, 603, 634, 654, 674, 917, 966, 1034, 1040, 1328   （共 15 行）
```

| 行 | 现语义 | 改后 |
|---|---|---|
| 192 | `state.evidenceSetVersion` 初始化 `null` | 保留（聚合） |
| 343-344 | `hasGenerationIdentity`：比整份生成结果 | 保留（聚合） |
| 352-353 | `hasVersionIdentity`：比单条 version | **改 per-request** |
| 521 | `applyBootstrap` 写 `state.evidenceSetVersion` | 保留（聚合）；**新增**逐条写 `request.evidenceSetVersion` |
| 603 | `serializeResolvedVersion` 拷贝 locked item 值 | 不改（本就逐条，C-4） |
| 634 | `makeGenerationPayload` 的 `expectedEvidenceSetVersion` | **改 per-request**（对应 I-3 的 :959） |
| 654 | `persistResolvedSnapshot` payload | 保留（聚合，对应 I-3 的 :464） |
| 674 | `deleteSavedState` payload | 保留（聚合） |
| 917 | `isVersionSerializable` 非空判定 | 不改 |
| 966 | `canStartAssembly` 比 assembly 与 state 一致 | 保留（聚合） |
| 1034 | `assemble` 的 `expectedEvidenceSetVersion` | **删除**（对应 I-3 的 :1051） |
| 1040 | assemble 响应 identity 校验 | 保留（聚合） |
| 1328 | 采用快照拷贝 version 值 | 不改（本就逐条） |

### C-8. `resetVersions()` 的调用点（全集，2 个调用 + 1 个定义）

```
$ grep -n "resetVersions()" src/main/resources/static/trust-reply-workbench.js
368:            resetVersions();            ← handleStaleGeneration 内，保留
395:        function resetVersions() {      ← 定义
613:            resetVersions();            ← bootstrap 内，改为受 preserveVersions 控制
1355:            resetVersions();            ← changeRequestFacts 内，删除
```

（`grep` 命中 4 行：3 个调用 + 1 个定义。）

### Interaction points

| # | 写入侧 | 读取侧 | 计划覆盖 |
|---|---|---|---|
| IP-1 | `resolveCanonicalSelection` 产出 per-request 版本 | `versionId()`（:1890-1908）→ `TrustReplyItemVersion.versionId` | I-1；T1/T3 |
| IP-2 | `materializeVersion`（:1307-1360）写 `evidenceSetVersion` 进 version | `validateLockedItem`（:1205-1207）比对 | I-1/I-3；T3 |
| IP-3 | `saveState`（:449）写 payload 聚合值 | `restoreSavedStateWithFrame`（:555）读回比对 | I-4/I-6；T4/T5 |
| IP-4 | 服务端 bootstrap 的 `requestCoverage`（:438） | 前端 `applyBootstrap`（:521）逐条写入 | I-5；T3/T6 |
| IP-5 | 前端 `changeRequestFacts`（:1338-1355） | 服务端 `bootstrap` + 前端逐条保留 | I-5；T7 |
| IP-6 | `assemble` 收到的逐条 per-request 版本 | `validateLockedItem` + `validateMatrixKeys`（:1536-1557） | I-3；T3 |

## 实现方案

> **研究检查点（动工前必做）**：先跑
> `grep -rn "evidenceSetVersionWithMapping" --include=*.kt src/main src/test`
> 确认除 `TrustReplyWorkbenchService.kt:1522` 外无其他调用方。若有，停下来报告，不要自行扩大范围。

### 阶段 A — 服务端版本模型

#### T1 — 新增 per-request 与 aggregate 两个版本函数（I-1 / I-3）

文件：`TrustReplyWorkbenchService.kt`

新增 `requestEvidenceVersion(requestKey, factRuleIds, baseSnapshotOf)`：返回
`sha256Hex(listOf(requestKey, factRuleIds.joinToString(","), baseSnapshotOf(factRuleIds)).joinToString(" "))`。
`factRuleIds` 按原顺序参与（must-NOT-change 5）；分隔符沿用文件内既有约定（见 `requestKey()` :1875-1888、`versionId()` :1890-1908）。

新增 `aggregateEvidenceVersion(perRequestByIndex: List<Pair<Int, String>>)`：返回
`sha256Hex(perRequestByIndex.sortedBy { it.first }.joinToString("") { it.second })`。

删除 `evidenceSetVersionWithMapping`（:1576-1584），职责被上面两个函数取代。

#### T2 — `ResolvedCanonicalSelection` 携带 per-request 映射（I-1）

文件：`TrustReplyWorkbenchService.kt`

- `ResolvedCanonicalSelection`（:1473-1477）新增 `val requestEvidenceVersions: Map<String, String>`（key = requestKey）。
- `resolveCanonicalSelection`（:1486-1524）：算出 `matrix` 后对每条 `TrustReplyRequestFactSelection` 调 T1；`evidenceSetVersion` 改为 `aggregateEvidenceVersion(...)`。
- `baseSnapshotOf` 按 `useLocalEvidence` 选 `::localEvidenceSetVersion` 或 `{ aiReplyDraftService.buildEvidenceSnapshotForSelection(it).first }`——**按子集调用**，不再传 `sendQaRuleIds` 全集（见 C-1）。

#### T3 — 版本物化与校验改用 per-request（I-1 / I-2 / I-3）

文件：`TrustReplyWorkbenchService.kt`

- `materializeVersion`（:1307-1360）的 `evidenceSetVersion` 形参语义改为 per-request；三个调用方各传该 requestKey 的值：`adjustItem`（:1021-1029）、`assemble`（:1085-1097）、`buildInitialItemVersions`（:1624-1650）。
- `validateLockedItem`（:1205-1207）比对该条目的 per-request 值。
- `requireCurrentEvidenceVersion`：`:464` 不动（聚合）；`:959` 改为该 `request.requestKey` 的 per-request 值；**`:1051` 整行删除**。
- `TrustReplyRequestCoverage`（:126-139）**在末尾追加**带默认值的字段 `val evidenceSetVersion: String = ""`（默认值保证既有构造点不破），由 `toCoverage`（调用点 :438、:918）填该条目的 per-request 值。
- 说明：`TrustReplyBootstrapResponse` 由 controller **直接返回**（`TrustReplyWorkbenchController.kt:42-43` 无 DTO 转换），因此本项**不需要改 controller**。

#### T4 — 部分恢复（I-4）

文件：`TrustReplyWorkbenchService.kt`

- `restoreSavedStateWithFrame`（:532-608）：**删除** :555 条件里的 `payload.evidenceSetVersion != evidenceSetVersion`，只保留 `payload.sourceVersion != resolved.sourceVersion` 的整体作废分支。
- `validateLockedSubset`（:713 起）改为返回「保留项 + 丢弃项」而非抛异常整体作废。
- `TrustReplySavedState` 新增 `val droppedItemCount: Int = 0`；`status` 在「有保留且有丢弃」时为 `"PARTIALLY_RESTORED"`，全丢时仍 `"STALE"`，无丢弃时维持既有 `"RESTORED"` / `"FRAME_STALE"` 逻辑不变。

#### T5 — schema 版本（I-6 / I-7）

文件：`TrustReplyWorkbenchStateStore.kt`

- `:180-186`：`SCHEMA_VERSION` = `"trust-reply-workbench-state-v4"`；`PREVIOUS_SCHEMA_VERSION` = `"trust-reply-workbench-state-v3"`；`LEGACY_SCHEMA_VERSION` 保持 `"trust-reply-workbench-state-v1"`；`ACCEPTED_REQUEST_SCHEMA_VERSIONS` 由 v4、v3、v1 三个组成（v2 移除）。
- `decodePayload`（:120-130）的 `when`：v4 直通；v3 **不做字段迁移**（聚合值无法拆成 per-request），原样返回并由 `restoreSavedStateWithFrame` 判整体 STALE；v1 保持既有 legacy 处理。
- **不新增迁移文件**（I-7）。

### 阶段 B — 前端局部失效

#### T6 — 逐条持有 evidence 版本（I-5）

文件：`trust-reply-workbench.js`

- `requestFromCoverage`（:418 起）为每个 request 增加 `evidenceSetVersion` 字段，取自 T3 新增的 coverage 字段。
- `hasVersionIdentity`（:347-354）末两行改为比 `version.evidenceSetVersion === findRequest(requestKey).evidenceSetVersion`（该函数已收 `requestKey` 形参）。
- `makeGenerationPayload`（:630-645）的 `expectedEvidenceSetVersion`（:634）改取该 request 的值。
- `assemble`（:1031 起）payload **删除** `expectedEvidenceSetVersion`（:1034）。

#### T7 — `changeRequestFacts` 只重置受影响条目（I-5 / S-1）

文件：`trust-reply-workbench.js`

- `bootstrap`（:610-628）增加参数 `preserveVersions = false`；为 `true` 时**跳过** :613 的 `resetVersions()`，改为在 `applyBootstrap` 之后逐条比对：新 `request.evidenceSetVersion` 与该条目已有 version 携带的值相同则保留 `versions` / `activeVersionId` / `resolvedVersionId`；不同则只清该条并置 `request.evidenceStale = true`。
- `changeRequestFacts`（:1338-1355）：**删除** :1355 的 `resetVersions()`；改调 `bootstrap({ preserveVersions: true })`。
- 确认框（:1341-1344）：判断条件由「全局 `hasGeneratedState`」收窄为「**被改的这一条**已有 version 或 resolvedVersionId」；文案改为「该摘要的事实变化会清空**本条**已生成回答，其余摘要保留，继续？」。
- 渲染：`request.evidenceStale === true` 时按 S-1 输出提示片段。
- `resetVersions()` 函数**保留**（:395-416），仍被 `handleStaleGeneration`（:368）与 `bootstrap` 的非保留分支（:613）使用。

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | T1–T4 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStore.kt` | T5 |
| 3 | `src/main/resources/static/trust-reply-workbench.js` | T6–T7 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | 版本构造与断言 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | 局部失效、换绑、assemble 用例 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStoreTest.kt` | schema v4 与部分恢复 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt` | assemble 不再收 `expectedEvidenceSetVersion` |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | 归档时服务端重整合比对 |
| 9 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 局部保留行为 |

合计 **9** 个文件（上限 10）。子系统 **2** 个（后端版本模型 / 前端工作台，上限 2）。

**明确不在清单内，执行 agent 不得改动**：`TrustReplyWorkbenchController.kt`（DTO 不变，见 C-4；bootstrap 无 DTO 转换，见 T3）、`UnmatchedInboundMailController.kt`、`app.js`、`AiReplyDraftService.kt`、`AiReplyReviewAuditService.kt`、`AiTrainingEvaluationService.kt`、`UnsupportedAnswerIndexService.kt`、`AiReplyHighRiskClaimValidator.kt`、`AiReplyPointByPointComposer.kt`、任何 `db/migration/*.sql`、`styles.css`、`index.html`。

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。JS 测试由 `exec-maven-plugin` 的 `node-test` execution 在 `test` 阶段执行（`pom.xml:186-202`），`skipNodeTests` 未在 `<properties>` 声明（`grep -n skipNodeTests pom.xml` 仅命中 :201/:216/:231 三处 `<skip>`），默认不跳过。

```bash
# 本计划相关 Kotlin 测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='TrustReplyWorkbenchServiceTest+TrustReplyWorkbenchItemFlowTest+TrustReplyWorkbenchStateStoreTest+TrustReplyWorkbenchControllerTest+PendingMailOperationServiceTrustWorkbenchTest'

# 单个测试方法（确切过滤语法）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=TrustReplyWorkbenchItemFlowTest#methodName

# 本计划相关前端测试（实测可用；node v22.23.2）
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js

# 全部前端测试
node --test src/test/js/*.test.js

# 前端语法检查
node --check src/main/resources/static/trust-reply-workbench.js

# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：
- `mvn test`：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`，且 `node-test` execution 无报错。
- `node --test`：退出码 0，输出含 `# fail 0`。
- `mvn clean package`：退出码 0，`BUILD SUCCESS`。
- `git diff --check`：无输出。

来源：`CLAUDE.md` 项目元信息的 `test_command` / `build_command` 与「Commands」章节（`-Dtest=Class#method` 语法）；`pom.xml:186-202`；`node --test src/test/js/trustReplyWorkbench.test.js` 于 2026-08-19 实测通过（`# tests 16 / # pass 16 / # fail 0`，node v22.23.2）。

## 验收标准

- **I-1**：新增单测三条——(a) 同一 `factRuleIds` 绑到不同 `requestKey` 产生**不同** `requestEvidenceVersion`；(b) 同一 requestKey 下调换 `factRuleIds` 顺序产生**不同**值；(c) 同一输入连续两次调用产生**相同**值（确定性，K-ai-reply-evidence-version-deterministic）。
  且 `grep -n "evidenceSetVersionWithMapping" src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` **无输出**。
- **I-2**：新增单测——3 条目场景，把事实 F 从条目 1 移到条目 2，断言条目 1、2 的 per-request 版本**均变化**且**条目 3 不变**；携带旧版本的条目 2 locked item 被 `validateLockedItem` 以 `TRUST_REPLY_EVIDENCE_STALE` 拒绝。
- **I-3**：`grep -n "requireCurrentEvidenceVersion" src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` **恰 3 行**（saveState 调用、adjustItem 调用、定义行），**不含 assemble 的那一处**。新增单测：改条目 1 事实后，携带条目 2、3 未变版本的 locked items 仍能 `assemble` 成功。
- **I-4**：新增单测——payload 含 3 条 locked item、其中 1 条 per-request 版本已变，断言 `savedState.status == "PARTIALLY_RESTORED"`、`droppedItemCount == 1`、返回 lockedItems 为 2 条；再断言 3 条全变时 `status == "STALE"`。
- **I-5**：`grep -n "resetVersions()" src/main/resources/static/trust-reply-workbench.js` **恰 3 行**（handleStaleGeneration 内、定义行、bootstrap 非保留分支），`changeRequestFacts` 函数体内**不再出现**（改动前实测为 4 行：:368、:395 定义、:613、:1355）。前端测试断言：改条目 1 事实后 `state.requests[1].versions.length` 仍 > 0，且该操作触发的 `/bootstrap` 调用数恰为 1。
- **I-6**：`grep -n "trust-reply-workbench-state-v" src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStore.kt` 显示 `SCHEMA_VERSION` = v4、`PREVIOUS_SCHEMA_VERSION` = v3、`LEGACY_SCHEMA_VERSION` = v1。新增单测：v3 payload 解码后被判整体 STALE，**不得**被当作 per-request 接受。
- **I-7**：`git diff --stat -- src/main/resources/db/migration/` **无输出**。
- **S-1**：`git diff --stat` 不含 `styles.css` / `index.html`；`git diff src/main/resources/static/trust-reply-workbench.js` 中新增的 DOM 片段与 S-1 的代码块**逐字一致**；diff 中无新增 `style="`；无新增以 `trust-reply-` 开头的新 class 名。
- **must-NOT-change 1**：`TrustReplyWorkbenchItemFlowTest` 中断言 `TRUST_REPLY_FACT_ALREADY_ASSIGNED` 的既有用例（执行前用 `grep -n "TRUST_REPLY_FACT_ALREADY_ASSIGNED" src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` 定位）仍通过。
- **must-NOT-change 3**：`AiReplyReviewAuditServiceTest`、`AiTrainingEvaluationServiceTest` 通过，且这两个文件**未被修改**（不在变更清单内，`git diff --stat` 可核）。
- **回归**：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 改一条摘要的事实，其他摘要的回答保住
- 前置条件：找一封在「摘要与事实」页有 **≥3 条摘要**的来信（可在「AI 训练」页用含 3 个问句的来信构造）。
- 操作步骤：
  1. 对 3 条摘要**全部**生成并锁定回答，进度显示「已处理 3/3」。
  2. 在**第 2 条**摘要上点「+ 添加事实」，添加一个当前未被任何摘要占用的事实。
  3. 确认框点「确定」。
- 预期结果：
  - 页面**不**变成「正在加载工作台…」全屏骨架。
  - 第 1、3 条摘要的已锁定回答**原文仍在**且仍为已锁定态。
  - 只有第 2 条摘要的回答被清空，该卡片出现「事实已变化，本条回答需重新生成」。
  - 进度变为「已处理 2/3」。
- 覆盖：I-1、I-5、S-1、需求描述 observable outcome 1 与 2

### A-2: 拖动事实顺序同样只影响本条
- 前置条件：同 A-1，且第 2 条摘要绑定了 **≥2 个事实**。
- 操作步骤：把焦点移到第 2 条摘要某个事实 chip 的 `⋮⋮` 把手上，按 `→`，确认。
- 预期结果：同 A-1——只有第 2 条失效，第 1、3 条保留。
- 覆盖：I-1（顺序敏感）、must-NOT-change 第 5 条

### A-3: 确认框只在真会丢东西时弹，且说明范围
- 前置条件：一封有 ≥2 条摘要的来信，**只对第 1 条**生成并锁定回答，第 2 条留空。
- 操作步骤：(1) 在第 2 条（无回答）上添加一个事实；(2) 再在第 1 条（有回答）上添加一个事实。
- 预期结果：步骤 1 **不弹**确认框，直接完成；步骤 2 **弹**确认框，文案含「本条」二字并说明其余摘要保留。
- 覆盖：需求描述 observable outcome 3

### A-4: 事实换绑两端都失效、第三条不动
- 前置条件：3 条摘要全部已锁定；事实 F 当前绑在第 1 条。
- 操作步骤：(1) 从第 1 条移除 F，确认；(2) 把 F 添加到第 2 条，确认。
- 预期结果：第 1、2 条回答均被清空并提示需重新生成；**第 3 条自始至终保留**。
- 覆盖：I-2、must-NOT-change 第 2 条

### A-5: 部分失效后仍能整合发送
- 前置条件：完成 A-1（第 2 条已清空、第 1/3 条保留）。
- 操作步骤：(1) 只对第 2 条重新生成并锁定；(2) 切到「回复框架与整合」点「服务端整合」。
- 预期结果：整合成功，**不出现 409 或「来源或事实已变化」**；正文包含 3 条摘要的回答，且第 1、3 条的文字与 A-1 之前**逐字相同**。
- 覆盖：I-3、IP-6

### A-6: 刷新后部分恢复
- 前置条件：完成 A-1 后**不整合**，直接关闭标签页。
- 操作步骤：重新打开同一封来信的回复台。
- 预期结果：第 1、3 条的锁定回答**被恢复**；第 2 条为空；状态区提示保留了 2 项、丢弃了 1 项，而不是整体「来源或事实已变化」。
- 覆盖：I-4、IP-3

### A-7: 回归 — 部署前留下的存量草稿安全作废
- 前置条件：**部署前**在某封来信留下至少 1 条已锁定但未整合的草稿。
- 操作步骤：部署后打开该来信的回复台。
- 预期结果：页面正常加载不报错；提示为整体「来源或事实已变化」需重新生成；**不得**出现锁定项被错误恢复、随后整合失败的情况。
- 覆盖：I-6

### A-8: 回归 — 一个事实不能同时属于两条摘要
- 前置条件：任意一封有 ≥2 条摘要的来信；事实 F 已绑在第 1 条。
- 操作步骤：在第 2 条的事实选择器里查看事实 F。
- 预期结果：F 显示为**已被占用/禁用**，无法直接添加，与部署前一致。
- 覆盖：must-NOT-change 第 1 条

### A-9: 回归 — 来源变化仍然全量重置
- 前置条件：某封来信的回复台已锁定 ≥2 条回答，保持页面打开。
- 操作步骤：在另一个浏览器标签给同一位专家发一封任意邮件（或等 IMAP 轮询收到该专家新信），回到回复台点任意一条的「重新生成」。
- 预期结果：出现「来源或事实已变化，确认刷新工作台并重新生成？」，确认后**全部**回答被清空——这是 03b 之前的既有行为，本计划不得改变它。
- 覆盖：must-NOT-change 第 6 条

### A-10: 回归 — 审计仍记录整份草稿的聚合指纹
- 前置条件：完成一次完整的生成 → 整合 → 采用 → 发送。
- 操作步骤：在「任务记录」或操作日志里查看该次发送的 AI 审计条目。
- 预期结果：`evidenceSetVersion` 字段**存在且非空**，为一个 64 位十六进制串，形态与改动前一致。
- 覆盖：must-NOT-change 第 3 条
