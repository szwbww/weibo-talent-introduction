# 可信回复锁定持久化与整合自动生成开发计划

> 使用 `create-p` 编写。依赖：先执行 `trust-reply-atomic-facts-and-duplicate-guard.md`。

## 需求描述

### 可观察结果

1. AI 训练与收发邮件的可信回复工作台中，用户点击“采用此版本/确认省略”形成的锁定决定保存到服务端；刷新页面、关闭后重新进入或换浏览器后，当前 source/evidence 未变化时自动恢复，不要求重新生成。
2. 点击“服务端整合”时，所有未生成的 `GROUNDED（依据充分）` 项自动走与逐项按钮相同的 `ADJUST_ITEM` 生成路径，按原问题顺序生成、锁定、持久化并回填；全部成功后自动调用 `/assemble`，用户无需逐项点击。
3. 若自动生成中途失败，已经成功的 GROUNDED 项继续显示并已持久化；本次不 assemble。用户再次点击时只生成仍缺失的项。

### 必须保持不变

- 打开/刷新页面只 bootstrap + 恢复锁定状态 + 现有问题翻译，绝不自动生成 AI 回答。
- 未锁定的 active version、临时 instruction、翻译展开态不保证跨刷新恢复；只有 resolved/locked 决定持久化。
- `PARTIAL` / `UNSUPPORTED` 必须继续人工逐项处理，服务端整合不得替用户自动生成或采用。
- sourceVersion/evidenceSetVersion 不匹配时旧状态不得恢复；不得把旧事实版本答案带入新邮件/新规则。
- `resolvedVersionId` 与服务端重物化后的 version 仍是采用权威；state store 不能绕过现有 locked item/claims/versionId 校验。
- compose 仍按 canonical request list 逐字整合，不本地 join、不去重、不润色。
- SIMULATION 与 LIVE 共用同一持久化/生成行为；完成回调仍分别进入训练评估或人工回复编辑器。
- 不自动发送邮件；刷新恢复、自动生成、持久化、整合均不得调用发送接口。
- SSE generationId、单次/总 TTL、真实进度、取消、COMMITTING 与 stale fail-closed 语义不变。
- `operator_action_log` 继续是审计而不是草稿重放存储；不得把完整工作台正文塞入该表。
- 前置计划新增的 `TRUST_REPLY_DUPLICATE_CLAIM` 整合防线必须保留；恢复或自动生成不能绕过它。

### 范围外

- 不恢复未采用版本、textarea 草稿、译文展开状态或已经生成但未锁定的回答。
- 不持久化 assembly preview；刷新后恢复 locked items，用户可重新点击服务端整合。
- 不提供历史版本列表、撤销历史、多人协作合并或状态管理后台。
- 不新增模糊重复过滤；重复 claim 由前置计划处理。
- 不新增 CSS 或改变布局。
- 不修改自动/人工发送服务、训练评估表、操作日志 schema。

## 关键不变量

### Invariant I-1：持久化对象只能是已验证的锁定快照

- Rule：持久化 payload 只包含 `schemaVersion`、sourceVersion、evidenceSetVersion、requestedFactIds、selectedModel 和按 canonical request 顺序排列的完整 lockedItems；不得保存 active-only version、assembly、翻译或任意 DOM 状态。每个 locked item 保存 answer/claims/model/kind/versionId/operator instruction/hash，足以逐字恢复。
- Applies to：前端 state save payload、服务端 state validation/store codec。
- Violation consequence：刷新后恢复未确认草稿，或无法重物化原版本。
- 来源：`K-trust-reply-resolved-version-single-source` + `K-ai-draft-audit-version-hash-not-replay`

### Invariant I-2：保存前必须重用 locked item 信任边界

- Rule：`saveState` 对 subset lockedItems 执行与 assemble 相同的 source/evidence、requestKey、allowed handling、claims、operator instruction/hash、versionId 重物化校验；区别只在于允许 canonical request 的有序子集。校验失败不写表。
- Applies to：`TrustReplyWorkbenchService.saveState`、共享 validation helper。
- Violation consequence：恶意/陈旧前端可把不可整合版本持久化并在刷新后伪装成已锁定。
- 来源：`K-trust-reply-resolved-version-single-source`

### Invariant I-3：恢复必须双版本一致且重新校验

- Rule：bootstrap 先按 sourceType/sourceId 读取候选状态；仅当 sourceVersion 与当前来源一致、用 payload requestedFactIds 重算的 evidenceSetVersion 一致、每个 locked item 再校验通过时返回 `savedState.status=RESTORED`。否则返回 STALE/INVALID/EXPIRED 且 lockedItems 为空，不自动生成。
- Applies to：bootstrap/state store read/frontend applyBootstrap。
- Violation consequence：邮件正文或 QA 事实变化后沿用旧回答。
- 来源：`K-ai-reply-evidence-version-deterministic`

### Invariant I-4：服务端状态使用乐观并发

- Rule：同一 `(source_type,source_id)` 只有一行；客户端每次 PUT 携带 expectedStateVersion。insert 只允许 expected=0，update/delete 只允许等于当前 version；insert/update 成功后返回递增 version，删除成功后返回 0。冲突返回 409 `TRUST_REPLY_STATE_CONFLICT`，前端不得覆盖另一标签页的新状态。
- Applies to：state store save/delete、controller、frontend save flow。
- Violation consequence：两个页面后保存者无提示覆盖先保存者的人工决定。
- 来源：original

### Invariant I-5：状态正文有大小与时效边界

- Rule：`payload_json` UTF-8 序列化后最大 256 KiB；lockedItems 数量不得超过当前 canonical request 数；`expires_at=updated_at+30 days`。bootstrap 不返回 expired 状态；每次成功 save opportunistically 删除 expired rows。不得写 `operator_action_log`。
- Applies to：V83、state store、save/load。
- Violation consequence：无界正文占用、长期保留敏感草稿或污染通用审计。
- 来源：`K-ai-draft-audit-version-hash-not-replay`

### Invariant I-6：锁定/解锁只有服务端确认后才成为 durable

- Rule：前端锁定/解锁先构造候选完整 snapshot，调用 PUT；成功后更新 `stateVersion` 并显示最终 locked 状态。失败时恢复本次操作前的 resolved 状态，保留已生成 active version并提示重试。不得只改浏览器状态后静默忽略保存失败。
- Applies to：`toggleResolve`、自动采用 GROUNDED、事实变化/状态清空。
- Violation consequence：页面看似已保存，刷新后却丢失。
- 来源：original

### Invariant I-7：整合自动生成复用逐项 ADJUST_ITEM，不依赖 FULL_DRAFT

- Rule：`missingGroundedKeys` 按 canonical order 逐个调用 `operation=ADJUST_ITEM`、`handling=ANSWER_WITH_EVIDENCE`；每项复用当前 model/TTL/SSE/cancel/identity validation。服务端整合点击流程中 `operation=FULL_DRAFT` 调用数必须为 0。
- Applies to：工作台整合生成 helper、请求 payload、测试。
- Violation consequence：复杂多问题邮件的 FULL_DRAFT 进入 FALLBACK_NO_RESPONSE 时仍迫使用户手动逐项点击。
- 来源：original + `K-assembly-fill-missing-allowlist`

### Invariant I-8：自动生成成功一项即回填并持久化一项

- Rule：每个 ADJUST_ITEM 成功后验证唯一 version，append 到对应 request、设 active/resolved、折叠、PUT 当前完整 locked snapshot；保存成功才进入下一项。失败/取消/stale/state conflict 立即停止，禁止 assemble；先前成功项保留，失败项展开并显示错误。
- Applies to：自动生成循环、state save、assemble gate。
- Violation consequence：一次后续失败让已付费生成结果丢失，或未持久化就继续整合。
- 来源：`K-assembly-fill-missing-allowlist`

### Invariant I-9：现有 active GROUNDED 的采用也必须先持久化

- Rule：点击服务端整合时，已有有效 active 但未 resolved 的 GROUNDED 可视为该次显式采用；必须与其他候选 lockedItems 一起 PUT 成功后，才生成剩余项或 assemble。
- Applies to：assemble readiness/start sequence。
- Violation consequence：本次整合使用了刷新后无法恢复的临时版本。
- 来源：`K-trust-reply-resolved-version-single-source`

### Invariant I-10：人工必处理项继续阻断自动流程

- Rule：任一 PARTIAL/UNSUPPORTED 未 resolved 时服务端整合 disabled；自动循环不得包含这些 requestKey。状态恢复不能把 coverage 已变化的旧 handling 强行锁定。
- Applies to：computeReadiness、restore、generation allowlist。
- Violation consequence：风险回答绕过人工确认。
- 来源：`K-assembly-fill-missing-allowlist`

### Invariant I-11：生成序列只显示真实进度并可取消

- Rule：显示“正在生成有据回答 i/n”及当前 SSE phase；不显示估算百分比。取消只取消当前 generationId并停止后续循环；已成功且已保存项不回滚。生成完成后才进入 ASSEMBLING。
- Applies to：generation state、toolbar cancel、status/summary。
- Violation consequence：误导时长、取消后继续调用模型或错误回滚已保存内容。
- 来源：`K-ai-reply-loading-panel`

### Invariant I-12：训练/收发邮件同源，发送边界不变

- Rule：所有逻辑只在共享 `trust-reply-workbench.js` 和共同后端 API；两个 app host 不复制保存/恢复/生成实现。LIVE complete 仍只采用到人工编辑器，SIMULATION complete 仍只评估。
- Applies to：shared runtime、controller/service、两个 mount adapter。
- Violation consequence：两入口刷新/整合行为分叉或出现自动发送。
- 来源：`K-shared-workbench-fixed-mode-host-adapter`

### Invariant I-13：静态资源版本同值更新

- Rule：index 中 styles.css、trust-reply-workbench.js、app.js query version 统一更新为 `20260804-trust-reply-durable-locks-01`，对应契约测试同步。
- Applies to：index/cache test。
- Violation consequence：浏览器继续加载依赖 FULL_DRAFT 的旧工作台脚本。
- 来源：original

## 样式契约

本计划不新增 CSS，不修改 `styles.css`。只改变现有区域的状态与文案。

### S-1：恢复状态提示

- 复用：`.ai-reply-feedback`（`styles.css:6058`）、`.ai-reply-coverage/.ai-reply-error`（`styles.css:7295-7318`）。
- DOM 结构：继续使用现有 `<div data-role="status" role="status" aria-live="polite">`；恢复成功显示 `READY：已恢复 N 项已锁定回答`，stale 显示 `STALE：来源或依据已变化，旧锁定回答未恢复`。
- 禁止项：新 class、inline style、toast、modal、动画。

### S-2：锁定保存状态

- 复用：`.trust-reply-item`（`styles.css:7339-7364`）、`.trust-reply-item-actions`（`:7543`）、`.button.primary/.secondary`（`:623-645`）、既有 disabled 状态。
- DOM 结构：卡片和按钮结构不变；PUT pending 时当前操作按钮 disabled，文字=`保存中…`；成功回到“取消采用/取消省略”，失败回到“采用此版本/确认省略”。
- 禁止项：新增 spinner、图标、颜色或卡片结构。

### S-3：自动生成与整合状态

- 复用：`.trust-reply-summary`（`styles.css:7546-7564`）、`.trust-reply-progress`（`:7566-7580`）、`.trust-reply-lock-hint`（`:7598`）、`.trust-reply-final-actions`（`:7599`）、toolbar cancel button。
- DOM 结构：现有 summary/button 不变；按钮依次显示“生成有据回答并整合”→“生成并整合中…”→“整合中…”。status 显示真实 `i/n` 和 SSE phase。
- 进度条仍只按 durable resolved 数量计算；不得显示模型进度百分比。
- 禁止项：新进度组件、新 CSS、虚假倒计时。

### S-4：恢复后的卡片

- 复用：`.trust-reply-item[data-locked="true"]`（`styles.css:7359-7364`）、`.badge.ok`、现有版本下拉与 answer 区。
- DOM 结构：saved locked item 作为唯一恢复 version 写入原卡片，activeVersionId=resolvedVersionId；卡片默认折叠并显示“已处理/已省略”。PARTIAL/UNSUPPORTED 未锁定项仍展开。
- 禁止项：新增“历史版本”DOM、修改 coverage 颜色、替换现有卡片层级。

### 设计基线

- 主色/hover：`#2563eb/#1d4ed8`；success/error/warning：`#059669/#e11d48/#d97706`。
- 圆角：`--radius-sm:7px`、`--radius-md:10px`；阴影：`--shadow-sm:0 1px 2px rgba(15,23,42,.04)`。
- 所有既有 class 就地复用，不修改其规则；因此无其他使用点影响。

## 现状审计

### 浏览器内工作台状态

- Store：`createInstance` closure，保存 requests/versions/activeVersionId/resolvedVersionId/assembly/model/TTL/controllers；刷新即销毁，没有 localStorage、IndexedDB 或服务端 draft store。
- Write paths：bootstrap 重建 requests；ADJUST_ITEM 追加 version；toggleResolve 仅改内存；assemble 自动采用 active GROUNDED；生成/事实变化清状态。
- Read paths：render cards/summary、serializeResolvedVersion、assemble payload、两个 host onComplete。
- Interaction point：服务端生成返回的 version 只写浏览器内存；因此即使用户锁定，refresh 后 bootstrap 无法恢复。

### 新 `trust_reply_workbench_state` 表

- 现状：仓库与生产均无此表；不能复用 `operator_action_log`，因为其 AI draft snapshot 只保存 hash/evidence metadata，不含可重放正文（来源：`K-ai-draft-audit-version-hash-not-replay`）。
- 计划 schema（MySQL 5.7）：
  - `id BIGINT AUTO_INCREMENT PRIMARY KEY`
  - `source_type VARCHAR(32) NOT NULL`
  - `source_id BIGINT NOT NULL`
  - `state_version BIGINT NOT NULL`
  - `payload_json LONGTEXT NOT NULL`
  - `expires_at DATETIME NOT NULL`
  - `created_at/updated_at DATETIME NOT NULL`
  - unique `(source_type,source_id)`；index `(expires_at)`。
- Planned write paths：`TrustReplyWorkbenchStateStore.save/delete`；save 前 opportunistic prune expired。
- Planned read path：`TrustReplyWorkbenchService.bootstrap` 按 exact source load candidate。
- 不设 polymorphic FK；source_type 校验只允许 TRAINING_MAIL/LIVE_INBOUND，source 存在性由 `resolveSource` 保证。

### 服务端 workbench API

- 现有：POST bootstrap、SSE generation、POST cancel、POST assemble；没有 state save/load endpoint。
- `bootstrap` 当前只返回 canonical facts/coverage/source/evidence，不读取任何草稿。
- `assemble` 当前只接受完整 lockedItems；其 `validateLockedItem + materializeVersion/versionId` 是新 state save 必须复用的信任边界。
- 新交互：bootstrap response 增加单一 `savedState` object；PUT `/state` 保存完整 resolved snapshot。

### 当前整合自动生成实现与真实失败

- `computeReadiness` 已把 unresolved GROUNDED 放入 `missingGroundedKeys`，按钮文案已为“生成有据回答并整合”。
- `generateMissingGrounded` 当前只调用一次 `operation=FULL_DRAFT`，要求响应为 allowlist 每个 key 恰好返回一个 version；任一缺失就停止。
- 线上静态文件 SHA-256 与当前仓库完全一致，故不是未部署；样本 inbound 105 最新 FULL_DRAFT 审计为 `FALLBACK_NO_RESPONSE/BLOCKED`，复杂多问未产生完整 itemVersions，解释了用户仍需逐项点击。
- 单项按钮使用 `ADJUST_ITEM`，每次只处理一个 request，正是应由整合按钮自动编排的稳定路径。

### 前端样式盘点

- 可复用 class 与行号：S-1～S-4 已列出；工作台完整样式位于 `styles.css:7142-7641`。
- 改动前 DOM：status、item、summary、final-actions 均由 `renderMarkup/renderRequest/renderSummary` 生成；本计划不新增节点。
- 当前静态版本键：`20260801-trust-reply-manual-generation-01`，三项资源同值。

### 跨模块交互点

| 交互点 | 写入 | 读取 | 约束 |
|---|---|---|---|
| IP-1 | PUT state 写 payload/version | bootstrap restore | I-1～I-5 |
| IP-2 | toggleResolve 候选状态 | PUT state acknowledgement | I-6 |
| IP-3 | ADJUST_ITEM version | request state + durable snapshot | I-7/I-8 |
| IP-4 | restored locked items | serializeResolvedVersion/assemble | I-2/I-3 |
| IP-5 | facts/source 变化 | bootstrap stale decision | I-3/I-10 |
| IP-6 | shared assembly | training/live onComplete | I-12 |
| IP-7 | index cache key | browser script selection | I-13 |

## 实现方案

### Task 1：创建持久化表与窄 Store（I-1、I-4、I-5）

文件：

- `src/main/resources/db/migration/V83__create_trust_reply_workbench_state.sql`
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStore.kt`

1. 按审计 schema 建表；LONGTEXT 兼容 MySQL 5.7，不使用 vendor JSON 操作。
2. Store 用 `NamedParameterJdbcTemplate + ObjectMapper` 实现 `load(source)`、`save(expectedVersion,payload)`、`delete(expectedVersion)`、`pruneExpired(now)`。
3. update/delete SQL 带 `state_version=:expected`；affectedRows=0 时区分不存在/冲突并抛稳定异常。
4. 序列化前检查 256 KiB；反序列化只接受 `trust-reply-workbench-state-v1`。
5. 不记录到 operator_action_log，不打印 payload/正文日志。

### Task 2：后端保存/恢复服务与 HTTP 契约（I-1～I-6、I-10）

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt`

1. 增加 domain/HTTP DTO：`TrustReplySavedState(status,stateVersion,selectedModel,requestedFactIds,lockedItems)`、save request/response。
2. 抽取 locked subset validator：复用现有 `validateLockedItem/canonicalizeClaims/materializeVersion`；save 允许 canonical 子集，assemble 继续要求全集。
3. `saveState`：resolve source → 重算 facts/evidence → validate expected versions/locked subset → canonical order serialize → store optimistic save/delete。
4. `bootstrap`：先 load candidate；request 明确给 fact IDs 时以 request 为准，否则可采用 candidate requestedFactIds；重算 evidence 后决定 RESTORED/STALE/INVALID/EXPIRED，并把单一 savedState object 放入 response。
5. 新增 `PUT /api/trust-reply/workbench/state`；沿用全局 auth 与结构化错误处理。
6. state store constructor 参数放在 service 末尾；所有直接构造 service 的测试显式/默认注入 test double，不让生产绕过持久化。

### Task 3：前端恢复与 durable 锁定（I-1、I-3、I-4、I-6、I-9、S-1、S-2、S-4）

文件：`src/main/resources/static/trust-reply-workbench.js`

1. state 增加 `savedStateVersion`、`stateSavePending`；不使用 localStorage/sessionStorage。
2. `applyBootstrap` 在 canonical requests 建好后应用 RESTORED lockedItems：按 requestKey 转回 version，设 versions/active/resolved/handling/instruction/collapsed；显示恢复数量。
3. STALE/INVALID/EXPIRED 不应用任何旧 item；保留“未恢复”状态提示，绝不触发生成。
4. 新增 `persistResolvedSnapshot(previousDecision)`：PUT 完整 resolved list；成功更新 version，失败回滚本次 resolved 改动并保留 active version。
5. `toggleResolve` 改为 async durable action；pending 时禁用当前按钮并显示“保存中…”。
6. facts 变化确认后，先用空 snapshot 删除旧状态成功，再 bootstrap；删除失败不切换事实。

### Task 4：整合按钮自动编排逐项 GROUNDED 生成（I-7～I-12、S-2、S-3）

文件：`src/main/resources/static/trust-reply-workbench.js`

1. 删除 `generateMissingGrounded` 的 FULL_DRAFT 调用和 `validateAllowlistVersions` 全量响应假设。
2. 抽取可被手动按钮与整合循环共用的 `requestItemVersion(request, handling, instruction)`；保持单项 identity/TTL/SSE/cancel 校验。
3. `assemble()` 固定顺序：
   - 阻断未处理 PARTIAL/UNSUPPORTED；
   - durable 保存 adoptable active GROUNDED；
   - 冻结仍 missing GROUNDED canonical list；
   - 逐项 ADJUST_ITEM → validate → active/resolved → durable save；
   - 重新计算 readiness；全量 resolved 后才 POST assemble。
4. 每项开始显示真实 `i/n`；取消当前 generation 后设置 sequence cancelled，不启动下一个。
5. 单项失败时展开该卡片、保留并持久化之前成功项；重试仅处理新的 missing list。
6. 两种 host 无改动；complete 行为不变。

### Task 5：缓存版本与自动测试（I-1～I-13、S-1～S-4）

文件：

- `src/main/resources/static/index.html`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt`
- `src/test/js/trustReplyWorkbenchSharedMount.test.js`
- `src/test/js/batchSendTaskConsoleVisualFix.test.js`

测试：

1. service：save subset validation、restore success、source/evidence stale、invalid version、payload size、insert/update/delete conflict、expiry。
2. controller：PUT DTO mapping、200 response、409 conflict、422 invalid locked item；bootstrap savedState JSON。
3. JS：SIMULATION/LIVE restore；锁定/解锁 PUT 成功与 rollback；刷新 bootstrap 不发 generation；facts change delete。
4. JS 整合：N 个 missing GROUNDED 产生 N 个按序 ADJUST_ITEM、0 个 FULL_DRAFT、N 次 durable save、最后一次 assemble；partial/unsupported 从不进入循环。
5. JS 失败：第 k 项失败/取消/state conflict 时无 assemble，前 k-1 项保留；重试只生成剩余项。
6. 三项静态资源 version 同步为 I-13 实值。

验证命令：

```bash
node --check src/main/resources/static/trust-reply-workbench.js
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js src/test/js/batchSendTaskConsoleVisualFix.test.js
node --test src/test/js/*.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test
git diff --check
```

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V83__create_trust_reply_workbench_state.sql` | 新增 | durable locked snapshot 表 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStore.kt` | 新增 | JSON codec、乐观锁、过期与大小边界 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 修改 | save/restore、subset validation、bootstrap savedState |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt` | 修改 | PUT state HTTP 契约 |
| 5 | `src/main/resources/static/trust-reply-workbench.js` | 修改 | 恢复、durable lock、逐项自动生成/回填/整合 |
| 6 | `src/main/resources/static/index.html` | 修改 | 静态资源缓存版本 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | 修改 | state 保存/恢复/冲突/过期测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt` | 修改 | state HTTP 契约测试 |
| 9 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 修改 | 双宿主恢复、durable lock、自动逐项生成测试 |
| 10 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改 | 缓存键契约 |

范围：10 文件；后端状态存储/API + 共享前端工作台两个子系统。无发送、评估、审计、CSS 文件变更。

## 验收标准

- I-1：数据库 payload 只含声明字段和 resolved lockedItems；未锁定 active/assembly/translation 不出现。
- I-2：伪造 requestKey/versionId/claims/handling/source/evidence 的 PUT 均 409/422，表不变化。
- I-3：相同双版本恢复 N 项；修改邮件正文或 QA rule 后 status=STALE、恢复 0 项、generation 请求 0。
- I-4：两个相同 expected version 的连续 PUT 只有一个成功，另一个 409；前端不覆盖。
- I-5：>256 KiB 返回 413/422；expired 不恢复；成功 save 执行过期清理；operator_action_log 无新增完整正文。
- I-6：PUT 成功后卡片 locked；模拟失败后回滚 resolved，active version 仍可再次采用。
- I-7：整合 missing GROUNDED 时 generation payload 全为 ADJUST_ITEM，FULL_DRAFT=0，顺序与 request index 一致。
- I-8：第 k 项失败时 assemble=0；前 k-1 项在刷新后仍恢复；重试调用数仅为剩余项数。
- I-9：已有 active GROUNDED 先 PUT 成 locked，再生成/assemble；保存失败则后续请求为 0。
- I-10：存在 unresolved PARTIAL/UNSUPPORTED 时按钮 disabled，generation/save/assemble 均不发生。
- I-11：status 含真实 `i/n`；cancel 后无后续 generation；无模拟百分比。
- I-12：SIMULATION/LIVE 使用同一 state endpoint 和循环；complete 分别评估/采用编辑器，发送请求=0。
- I-13：三项静态资源 query version 与测试期望完全等于 `20260804-trust-reply-durable-locks-01`。
- 跨计划回归：恢复/自动生成的 locked items 仍经过前置计划 duplicate claim 校验；冲突时无 assembly。
- S-1～S-4：`styles.css` 无 diff；DOM class/层级不变；只出现契约规定文案和 disabled 状态，无新 class/inline style。
- 全量：Node 定向/全量、Maven、diff check 全绿。

## 人工验收清单

### A-1：LIVE 锁定刷新恢复
- 前置条件：收发邮件中存在一封可生成 GROUNDED 的来信。
- 操作步骤：逐项生成→点击“采用此版本”→刷新浏览器→重新打开同一详情。
- 预期结果：显示“已恢复 1 项已锁定回答”；该卡片折叠、“已处理”，展开后正文/版本/处理方式与刷新前逐字相同；Network 无 generation。
- 覆盖：I-1～I-3、I-6、S-1/S-4、observable 1。

### A-2：SIMULATION 锁定刷新恢复
- 前置条件：AI 训练中选择一封训练邮件。
- 操作步骤：锁定两项→刷新→重新选择该邮件。
- 预期结果：恢复 2 项；训练评估尚未自动执行；无 generation/发送请求。
- 覆盖：I-12、observable 1、must-not-change 1/7/8。

### A-3：未锁定版本不恢复
- 前置条件：任一工作台逐项生成成功但不点击采用。
- 操作步骤：刷新页面。
- 预期结果：该项回到“待生成/待处理”，不显示旧 active version；系统不自动重生成。
- 覆盖：I-1、范围外第 1 项。

### A-4：来源或事实变化拒绝恢复
- 前置条件：先锁定一项；随后在测试环境修改原邮件 cleaned body 或该事实正文。
- 操作步骤：刷新工作台。
- 预期结果：显示 `STALE：来源或依据已变化，旧锁定回答未恢复`；恢复 0 项；无 AI 请求。
- 覆盖：I-3、I-10、IP-5。

### A-5：整合自动生成全部 GROUNDED
- 前置条件：人工完成所有 PARTIAL/UNSUPPORTED，至少 3 个 GROUNDED 仍“待生成”。
- 操作步骤：点击“生成有据回答并整合”，观察 Network 与卡片。
- 预期结果：按问题顺序出现 3 次 ADJUST_ITEM、0 次 FULL_DRAFT；卡片逐个变“已处理”并回填正文；随后恰好 1 次 `/assemble`，出现整合预览。
- 覆盖：I-7～I-11、S-2/S-3、observable 2。

### A-6：中途失败与重试
- 前置条件：3 个待生成 GROUNDED；让第 2 次模型请求失败。
- 操作步骤：点击整合；失败后刷新；再次点击整合。
- 预期结果：第一次只第 1 项锁定并可刷新恢复，第 2 项显示错误，第 3 项未请求，assemble=0；第二次只生成第 2、3 项并最终整合。
- 覆盖：I-6～I-8、observable 3。

### A-7：取消序列
- 前置条件：至少 2 个待生成 GROUNDED。
- 操作步骤：第一项生成中点击“取消生成”。
- 预期结果：当前 generation 收到 cancel；后续项不启动；assemble=0；之前已保存项不回滚。
- 覆盖：I-11、must-not-change 9。

### A-8：人工项闸门
- 前置条件：至少一个 PARTIAL 或 UNSUPPORTED 未锁定，同时存在 GROUNDED。
- 操作步骤：观察/点击服务端整合按钮。
- 预期结果：按钮 disabled，提示“待人工处理 N 项”；GROUNDED 不生成；完成人工项后按钮变“生成有据回答并整合”。
- 覆盖：I-10、must-not-change 3。

### A-9：双标签并发
- 前置条件：两个标签打开同一 source，初始 stateVersion 相同。
- 操作步骤：标签 A 锁定一项；标签 B 锁定另一项。
- 预期结果：A 成功；B 返回状态冲突并回滚本次锁定，提示刷新；刷新 B 后能看到 A 的状态。
- 覆盖：I-4、IP-1/IP-2。

### A-10：LIVE 完成不发送
- 前置条件：完成自动生成与服务端整合。
- 操作步骤：点击“采用到人工回复”，但不点击发送。
- 预期结果：只填入人工编辑器；无发送请求、无处理状态变化；刷新工作台仍能恢复 locked items。
- 覆盖：I-12、must-not-change 7/8。

### A-11：视觉回归
- 前置条件：桌面与窄屏分别打开工作台。
- 操作步骤：观察恢复、保存中、生成 i/n、整合中、失败五种状态。
- 预期结果：卡片/摘要结构不变；颜色、圆角、按钮、disabled 与 S-1～S-4 实值一致；无新 spinner、modal、动画或布局溢出。
- 覆盖：S-1～S-4。

### A-12：30 天过期边界
- 前置条件：测试库把一行 state 的 expires_at 改为过去时间。
- 操作步骤：刷新对应工作台，再保存任一其他工作台状态。
- 预期结果：过期状态不恢复；后一次 save 后过期行被删除；操作日志没有保存该 payload。
- 覆盖：I-5、IP-1。
