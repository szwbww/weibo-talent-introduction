# 14 工作台并发与手动分析（局部遮罩 + 条目级持久化 + autoBootstrap）

顺序权威：`10-reply-orchestration-order.md`。
**本计划与 12 / 13 无依赖，可并行执行。** 它是纯缺陷修复，不引入任何新功能，不消费 `paragraphPlan`。

## 需求描述

**Observable outcome**
1. 对某一条摘要做「生成 / 改事实 / 改处理方式」时，只有该条摘要出现遮罩，其余摘要仍可操作；可以同时对多条摘要发起操作。
2. 打开一封来信（AI 训练的模拟回复、或收发件箱的处理面板）时**不再自动分析**。工具栏出现「开始分析」按钮，点了才跑 bootstrap；未分析时「一键预判」「重置」置灰。

**What must NOT change**
- 全局遮罩在三种情况下**仍然出现**：整合中（`completePending`）、回复框架保存中（`frameSavePending`）、全量重跑中（`generation.pending`）。这三种确实影响整封回复。
- `PUT /api/trust-reply/workbench/state` 的整封快照语义与 `expectedStateVersion` 乐观锁不变——它仍是「整合」时的权威落库路径。
- `state.readOnly` 时 `requestJson` 只放行 `/api/trust-reply/workbench/bootstrap` 的白名单不变（`trust-reply-workbench.js:333`）。
- 生成本身的 SSE 流、取消、TTL 语义不变。
- `requestKey` 哈希构成不变（G-7）。

**Out of scope**
- 三步界面、事实集表格、段落 pinned、运营事实 → 15。
- 把逐条生成改成整封编排 → 12 / 13。

**待需求方确认的一个取舍（阻塞 T-4）**
`state.readOnly` 模式下 `requestJson` 只放行 `/bootstrap`（`:332-334`）。若只读模式也改成手动分析，只读用户点开一封信将**什么都看不到**，必须点一次按钮。二选一：
- **方案 A（默认，本计划按此实现）**：只读模式保持自动分析——它不写工作台状态、不消耗生成成本，自动跑无副作用。
- **方案 B**：只读模式也手动，「开始分析」按钮在只读下可点。
需求方未表态时按 A 实现，并在 T-4 的代码注释中写明这是默认取舍而非疏漏。

## 关键不变量

### Invariant I-1: 单条操作只锁单条
- Rule: 「单条摘要生成」「单条改事实」「单条保存」三类操作期间，`busyOverlayState()` 必须返回 `null`（不出全局遮罩），只由 `itemBusyState(request)` 出该条的局部遮罩。
- 现状证据：渲染层**已经做对了**——`busyOverlayState()` 的注释（`trust-reply-workbench.js:2035-2037`）写着「Workbench-level busy reasons are limited to operations that affect multiple summaries or the final assembled reply. Single-summary operations are rendered by itemBusyState() below.」，`itemBusyState()`（`:2055-2069`）与 `renderItemBusyOverlay()`（`:2072-2076`）均已实现，文案含「其他摘要仍可继续操作」。**坏在状态层**：单条生成成功后走 `persistResolvedSnapshot()` 并置 `state.stateSavePending = true`（`:954`），命中 `busyOverlayState()` 的第一个分支（`:2039-2041`）。
- Applies to: `runItemSequence` 的 `persistEach` 分支（`:953-981`）。
- Violation consequence: 改一条锁全封，运营无法并行处理多问来信。
- 来源: original（`trust-reply-workbench.js:2035-2069, 954` 实读）

### Invariant I-2: 单条持久化走条目级接口，整封快照只在整合时用
- Rule: 单条摘要解析完成后的落库改为调用新接口 `PATCH /api/trust-reply/workbench/state/item`，请求体只含该条的 `requestKey` + 该条的 locked item + 乐观锁 `expectedStateVersion`。服务端在既有 `trust_reply_workbench_state`（V83）行内**合并**该条，不重写其余条目。
- `persistResolvedSnapshot()`（`:821-838`，`PUT /state`，`lockedItems` 为 `state.requests.map(serializeResolvedVersion)` 全量）保留原样，只在「整合」路径继续使用。
- Applies to: `trust-reply-workbench.js` 的 `runItemSequence`；`TrustReplyWorkbenchController`（现有端点 `POST /bootstrap`、`POST /generations/stream`、`POST /generations/{id}/cancel`、`POST /assemble`、`PUT /state`、`DELETE /state`、`POST /state/reset`，无条目级端点）；`TrustReplyWorkbenchService`。
- Violation consequence: 保存粒度不变则 I-1 无法成立——全局 `stateSavePending` 是遮罩的直接来源。
- 来源: original

### Invariant I-3: 并发守卫按作用域拆开
- Rule: `hasRequestMutationPending()`（`:1202-1204`，定义为「任一 request pending 即 true」）只允许出现在**真正需要全封一致**的入口守卫上：`regenerateContextStale`（`:1117`）、`autoRun`（`:1364`）、`reset`（`:1447`）、`canStartAssembly`（`:1187`）。**单条生成的入口不得使用它**，只查该条自身的 `request.pending`。
- 同理，`renderItemActions` 的 `disabled` 表达式（`:2208`）中的全局项 `state.stateSavePending` 必须移除，只保留 `request.factChangePending || request.stateSavePending`。
- Violation consequence: 一条在跑，其他条的按钮全灰，等于没有并发。
- 来源: original

### Invariant I-4: 乐观锁在并发下必须收敛，不得静默丢写
- Rule: 多条条目级 PATCH 并发时，`expectedStateVersion` 冲突必须以**该条重试**收场，不得整封失败、也不得跳过校验直接覆盖。服务端每次 PATCH 成功后返回新的 `stateVersion`，前端以最后一次返回值为准。
- Applies to: 新的条目级端点；`trust-reply-workbench.js` 的 `state.savedStateVersion` 更新逻辑（`:834-836` 同款）。
- Violation consequence: 并发操作下后写覆盖先写，运营看到的状态与库里不一致。
- 来源: original

### Invariant I-5: 分析是显式动作，未分析态是合法的稳定态
- Rule: `mount(host, options)` 新增可选参数 `autoBootstrap`（默认 `true`，保持既有宿主行为不变）。传 `false` 时 `mount` 不调 `instance.bootstrap()`（`:216`），渲染「未分析」态：来信正文 + 工具栏 + 「开始分析」按钮，其余操作区不渲染或置灰。点击后走**与现在完全相同**的 `bootstrap()`，不新造分析路径。
- 两个宿主传 `false`：`app.js:3786`（AI 训练 `mountAiTrainingTrustReply`）与 `app.js:9960`（处理收件）。
- Applies to: `validateMount`（`:220-240`，需接受新可选参数且不因其缺失而 reject）。
- Violation consequence: 点开一封信就在服务端建一份工作台状态（bootstrap 会写 `trust_reply_workbench_state`），给无人处理的邮件堆状态行。
- 来源: original（`trust-reply-workbench.js:213-217, 220-240` 实读）

## 样式契约

### S-1: 「开始分析」按钮（复用，零新增 CSS）
- 复用：`.trust-reply-autorun`（`styles.css:5384-5395`）作为容器，`.button` + `.button.primary`（`styles.css:655-677`，`.button` 的 `min-height/height: 32px`、`padding: 0 12px`、`border-radius: var(--radius-sm)`、`font-size: 12px`）作为按钮，`.button.secondary` 作为「重置」。
- DOM 结构：改 `renderToolbar()` 中 `autoRunBar` 的模板字符串（`trust-reply-workbench.js:2098`），把「开始分析」插在「一键预判」**之前**：
```html
<div class="trust-reply-autorun">
  <button type="button" class="button primary" data-action="start-analysis">开始分析</button>
  <button type="button" class="button primary" data-action="auto-run" disabled>一键预判</button>
  <button type="button" class="button secondary" data-action="auto-reset" disabled>重置</button>
  <span class="trust-reply-autorun-hint">先点「开始分析」拆分来信并匹配事实；分析完成后再用一键预判生成回答。不发送、不写外发记录。</span>
</div>
```
  分析完成后，`data-action="start-analysis"` 的按钮文案改为「重新分析」且 class 由 `button primary` 改为 `button secondary`；`auto-run` / `auto-reset` 去掉 `disabled`。
- 禁止项：inline style；新增 class；修改 `.trust-reply-autorun` 或 `.button` 的既有规则块。

### S-2: 未分析态的占位区（唯一新增 class，逐字给出）
- 新增：以下规则块**原样复制**到 `styles.css`，插在 `.trust-reply-autorun-hint` 规则块（`styles.css:5397-5403`）之后：
```css
.trust-reply-preanalysis {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 18px 16px;
    margin: 0;
    border: 1px dashed var(--panel-border);
    border-radius: var(--radius-sm);
    background: var(--panel-bg);
    color: var(--text-muted);
    font-size: 12px;
    line-height: 1.6;
    text-align: center;
}

.trust-reply-preanalysis strong {
    color: var(--text-main);
    font-size: 13px;
    font-weight: 600;
}
```
- 说明：`--panel-border` / `--panel-bg` / `--text-muted` / `--text-main` / `--radius-sm` 均为既有 token（`styles.css:15,16,21,22,70`），暗色主题在 `:9607-9608` 已重定义，故本块无需另写暗色规则。
- DOM 结构：
```html
<div class="trust-reply-preanalysis" role="status">
  <strong>尚未分析这封来信</strong>
  <span>点「开始分析」拆分来信、匹配已审核事实并生成覆盖矩阵。分析会在服务端建立本信的工作台状态。</span>
</div>
```
- 禁止项：inline style；未在本契约声明的新 class；对既有 class 规则块的任何修改。

### S-3: 局部遮罩（纯复用，零 CSS 改动）
- 复用：`.trust-reply-item-busy-overlay`（`styles.css:7410-7421`）与 `.trust-reply-item-busy-card`（`:7423-`），暗色覆盖在 `:9691`。本计划**不改这两个规则块**——它们已经存在且正确，问题在状态层（I-1）。
- 全信遮罩 `.trust-reply-busy-overlay`（`:7255`，暗色 `:9687`）同样不改，只收窄其触发条件。
- 禁止项：为「局部遮罩」另造新 class。

## 现状审计

### `trust_reply_workbench_state`（MySQL，V83 建表）
- **Write paths**：
  1. `PUT /api/trust-reply/workbench/state`（`TrustReplyWorkbenchController.kt:106`）——整封快照，前端 `persistResolvedSnapshot()`（`trust-reply-workbench.js:821-838`）。
  2. `DELETE /api/trust-reply/workbench/state`（`:110`）——`deleteSavedState()`（`:840-`）。
  3. `POST /api/trust-reply/workbench/state/reset`（`:114`）。
  4. `POST /api/trust-reply/workbench/bootstrap`（`:42`）——**本计划关注点**：mount 即调用，会建立本信的工作台状态。
- **Read paths**：`POST /bootstrap` 的响应装配；`POST /assemble`（`:102`）。
- **Interaction point IP-1**：write 1（整封快照）× I-1（局部遮罩）——保存粒度是遮罩粒度的上游，必须一起改。
- **Interaction point IP-2**：write 4（bootstrap 写状态）× I-5（手动分析）——改成手动后，未点按钮的来信在库里不再有状态行；`POST /assemble` 与 `PUT /state` 在无状态行时的既有行为必须复核（bootstrap 是它们的前置，界面上也够不到，但接口层不得因此抛未捕获异常）。

### 前端状态标量（`trust-reply-workbench.js`）
- 全局：`state.stateSavePending`（设置点 `:954`、`:1229`）、`state.generation.pending`、`state.frameSavePending`、`state.completePending`。
- 条目级：`request.pending`、`request.factChangePending`、`request.stateSavePending`。
- `busyOverlayState()`（`:2038-2053`）四个分支，第一个就是 `state.stateSavePending`。
- `itemBusyState(request)`（`:2055-2069`）开头 `:2058` 有一句「A global operation already owns the workbench mask」的短路——**四个全局标量任一为真即返回 null**。本计划收窄 `state.stateSavePending` 的产生源之后，该短路保留（它对剩下三个真·全局操作仍然正确）。
- `renderItemActions` 的 `disabled`（`:2208`）：`action.disabled || request.factChangePending || request.stateSavePending || state.stateSavePending`。
- `hasRequestMutationPending()`（`:1202-1204`）被 4 处引用：`:1117`、`:1187`、`:1364`、`:1447`。

### mount 链路
`runtime.mount(host, options)` → `validateMount`（`:220`）→ `createInstance` → **`instance.bootstrap()`（`:216`，无条件）**。
宿主两处：`app.js:3779 mountAiTrainingTrustReply`（由 `:3810 selectSimulateMail` 调用，即**点一封训练邮件就分析**）与 `app.js:9960`（处理收件）。

### 前端样式盘点
- 可复用 class：`.trust-reply-autorun`（styles.css:5384）、`.trust-reply-autorun-hint`（:5397）、`.button` / `.button.primary` / `.button.secondary`（:655 起）、`.trust-reply-item-busy-overlay`（:7410）、`.trust-reply-item-busy-card`（:7423）、`.trust-reply-busy-overlay`（:7255）。
- 设计基准 token 实值：`--primary: #1e40af`（:3）、`--panel-bg: rgba(255,255,255,0.55)`（:15）、`--panel-border: rgba(15,23,42,0.08)`（:16）、`--text-main: #1e293b`（:21）、`--text-muted: #94a3b8`（:22）、`--radius-sm: 7px`（:70）、`--radius-md: 10px`（:71）；暗色覆盖在 `:9602-9608`。按钮基准：`min-height/height 32px`、`padding 0 12px`、`font-size 12px`、`font-weight 500`。
- DOM 结构约定：工具栏由 `renderToolbar()` 返回模板字符串，`autoRunBar` 在 `:2098` 拼装；按钮统一走 `data-action="<name>"` 的委托点击。
- 改动前基线：`:2098` 的 `autoRunBar` 当前逐字为
  `<div class="trust-reply-autorun"><button type="button" class="button primary" data-action="auto-run">一键预判</button><button type="button" class="button secondary" data-action="auto-reset">重置</button><span class="trust-reply-autorun-hint">有据项自动生成，无据项由系统代填回答说明；汇总后仍可逐项调整。不发送、不写外发记录。</span></div>`
  且整个 `autoRunBar` 在 `state.readOnly` 时为空串。

## 实现方案

### T-1：新增条目级持久化端点（I-2 / I-4）
`TrustReplyWorkbenchController` 新增 `@PatchMapping("/state/item")`，请求体 `{source, expectedStateVersion, schemaVersion, sourceVersion, evidenceSetVersion, requestKey, lockedItem}`。`TrustReplyWorkbenchService` 读现有状态行 → 只替换 `requestKey` 匹配的那一项 → 写回并返回新 `stateVersion`。乐观锁失败返回既有的 stale 错误码，前端按 I-4 重试该条。

### T-2：前端单条落库改走条目级接口（I-1 / I-2）
`runItemSequence` 的 `persistEach` 分支（`:953-981`）：`state.stateSavePending = true` 改为 `request.stateSavePending = true`；`persistResolvedSnapshot()` 改为新增的 `persistResolvedItem(request)`；错误分支里的 `state.stateSavePending = false` 同步改为 `request.stateSavePending = false`。**`state.generation.pending` 在单条路径上也必须不置位**——否则 `itemBusyState` 的短路（`:2058`）仍会吞掉局部遮罩。

### T-3：并发守卫按作用域拆开（I-3）
- 单条生成入口只查 `request.pending`；`hasRequestMutationPending()` 的 4 个引用点（`:1117`/`:1187`/`:1364`/`:1447`）保留不动。
- `renderItemActions` 的 `disabled`（`:2208`）去掉末项 `state.stateSavePending`。
- `renderRequestHeader` 与 handling/version `<select>` 的 `disabled` 条件（`:2263`）已是条目级（`request.pending || request.factChangePending || request.stateSavePending`），**不动**。

### T-4：手动分析开关（I-5 / S-1 / S-2）
- `mount()`（`:213-217`）：`if (options.autoBootstrap !== false) instance.bootstrap();`
- `validateMount`（`:220-240`）：新增 `autoBootstrap` 为可选布尔，非布尔时 reject；缺省不 reject。
- 新增 `state.analyzed` 布尔；未分析时 `renderMarkup()` 渲染 S-2 的占位区替代摘要列表与框架页，工具栏渲染 S-1 的三按钮形态。
- 新增 `data-action="start-analysis"` 的委托处理：调用现有 `bootstrap()`，成功后置 `state.analyzed = true` 并重渲染。**不新造分析路径。**
- `app.js:3786` 与 `app.js:9960` 的 `runtime.mount(...)` 调用各加 `autoBootstrap: false`。
- 只读模式按方案 A：`state.readOnly` 为真时不传 `autoBootstrap: false` 的效果——即在 `mount()` 中判断 `options.autoBootstrap !== false || <只读>`。**该判断处必须写注释说明这是默认取舍**（见需求描述的待确认项）。

### T-5：测试
1. `trust-reply-workbench` 的既有 JS 测试套件（见 `K-js-test-invocation-surface`）新增：单条 `stateSavePending` 为真时 `busyOverlayState()` 返回 null 且 `itemBusyState(该条)` 非 null、`itemBusyState(其他条)` 为 null。
2. 新增：`mount(host, {autoBootstrap: false, ...})` 后未调用 bootstrap（以 fetch 调用计数或注入桩断言）；`validateMount` 对非布尔 `autoBootstrap` reject。
3. 后端新增 `TrustReplyWorkbenchServiceTest` 用例：条目级 PATCH 只改目标条目，其余 lockedItems 逐字不变；`expectedStateVersion` 冲突返回既有 stale 码。
4. **DOM stub 测试的已知盲区**：`K-dom-stub-tests-hide-dangling-refs` 记录过 stub 测试不会抛真实 DOM 异常。本计划新增的 `data-action="start-analysis"` 委托必须有一条断言「点击后 bootstrap 被调用恰好一次」，而不是只断言 DOM 里有这个按钮。

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/resources/static/trust-reply-workbench.js` | 修改（T-2、T-3、T-4） |
| 2 | `src/main/resources/static/app.js` | 修改（T-4，两处 mount 调用） |
| 3 | `src/main/resources/static/styles.css` | 修改（S-2 的一个新规则块） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt` | 修改（T-1 新端点） |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 修改（T-1 条目级合并） |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | 修改（T-5.3） |
| 7 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 修改（T-5.2：`autoBootstrap` 与 `validateMount`） |
| 8 | `src/test/js/autoRunOrchestration.test.js` | 修改（T-5.1、T-5.4：遮罩作用域与 start-analysis 委托） |

合计 8 个文件，2 个子系统（静态前端 / 工作台后端）。

测试落点的依据（`ls src/test/js/` + 符号计数实测）：`trustReplyWorkbenchSharedMount.test.js`（2967 行，64 处 `mount(`、141 处 `bootstrap`、1 处 `stateSavePending`）是 mount 契约的现有归属地；`autoRunOrchestration.test.js`（872 行，16 处 `auto-run`、33 处 `bootstrap`）是一键预判序列与并发守卫的现有归属地。`autoPreviewWorkbenchHost.test.js` 是「AUTO_PREVIEW 宿主已下线」的标识符守卫（60 行，无 `mount(`），与本计划无关，不改。

两个宿主的精确位置：`mountAiTrainingTrustReply`（`app.js:3779`，mode `SIMULATION`）与 `mountLiveTrustReply`（`app.js:9956`，mode `LIVE`，host `[data-trust-reply-live-host]`）。

## 验证命令

> 本项目是 Kotlin + Spring Boot 2.7 (Java 11) Maven 工程，**必须用 JDK 11 (zulu-11)**，裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关后端测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchServiceTest

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 前端 JS 用例（node:test；本计划的权威门禁）
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js
node --test src/test/js/autoRunOrchestration.test.js

# 前端 JS 全量回归
node --test src/test/js/*.test.js

# 空白/换行卫生
git diff --check
```



> **JS 门禁与 `mvn test` 的关系**：`pom.xml:190-201` 的 exec-maven-plugin 把
> `bash -lc 'node --test src/test/js/*.test.js'` 绑在 `test` phase，因此 `mvn test`
> 名义上覆盖 JS 用例；但该 execution 带 `<skip>${skipNodeTests}</skip>` 而
> `skipNodeTests` 在 `pom.xml:19-25` 的 `<properties>` 中**未定义**（K-js-test-invocation-surface
> 记为推断）。**本计划的权威门禁是上面的 `node --test <file>` 单跑命令**，`mvn test`
> 作为全量回归另列。首次执行须确认 `mvn test` 输出里出现 `node --test` 记录。
> `verify.sh` 只跑 `normalizeDiscoveryResultSummary.test.js` 一个文件，**不可用作前端门禁**。

通过判据：`mvn test` 退出码 0，输出 `Tests run: N, Failures: 0, Errors: 0`。
来源：Maven 命令取自项目根 `CLAUDE.md` 的 `## Commands`（逐字照抄）；JS 命令取自 `pom.xml:190-201` 的 exec 绑定与 `docs/knowledge/frontend/K-js-test-invocation-surface.md`，目标文件名经 `ls src/test/js/` 实测确认。

## 验收标准

- **I-1**：T-5.1 通过。
- **I-2**：T-5.3 通过；且 `grep -n "persistResolvedSnapshot" src/main/resources/static/trust-reply-workbench.js` 的结果只出现在整合路径，不出现在 `runItemSequence` 的 `persistEach` 分支。
- **I-3**：`grep -n "state.stateSavePending" src/main/resources/static/trust-reply-workbench.js` 在 `renderItemActions` 的 disabled 表达式中不再出现；`hasRequestMutationPending` 的引用点仍恰为 4 处。
- **I-4**：T-5.3 的冲突用例通过。
- **I-5**：T-5.2 通过——`node --test src/test/js/trustReplyWorkbenchSharedMount.test.js` 中「`mount(host,{autoBootstrap:false})` 后 bootstrap 未被调用」与「非布尔 `autoBootstrap` 被 reject」两条断言通过。
- **S-1**：`git diff src/main/resources/static/styles.css` 中**不含**对 `.trust-reply-autorun` / `.button` 既有规则块的任何修改；`trust-reply-workbench.js` 的 `autoRunBar` 模板与契约中的 HTML 骨架结构一致（三个按钮 + hint，class 名逐一对应）。
- **S-2**：`styles.css` 新增的两个规则块与契约中的代码块**逐字一致**（可用 diff 比对）；全文 grep 无 `style="` 内联样式新增。
- **S-3**：`git diff src/main/resources/static/styles.css` 中不含 `.trust-reply-item-busy-overlay` / `.trust-reply-busy-overlay` 规则块的改动。
- **回归**：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 两条摘要可以同时操作
- 前置条件：一封被切成 ≥3 问的来信，已点「开始分析」。
- 操作步骤：
  1. 对第 1 条摘要选「按回答说明生成」，填入任意说明，点生成
  2. **不等它结束**，立刻对第 2 条摘要打开处理方式下拉并切换选项
- 预期结果：第 1 条上出现局部遮罩，文案含「其他摘要仍可继续操作」；**第 2 条的下拉可正常操作**，整个页面没有覆盖全屏的遮罩。
- 覆盖：observable outcome 1；I-1；I-3

### A-2: 单条保存不锁全页
- 前置条件：同 A-1。
- 操作步骤：对第 1 条摘要增删一条事实，观察保存过程中的页面状态。
- 预期结果：只有第 1 条卡片出现遮罩（文案「正在更新本条事实…」或「正在保存本条摘要…」）；**不出现**全局的「正在保存工作台状态…」遮罩。
- 覆盖：observable outcome 1；I-2；IP-1

### A-3: 打开来信不自动分析
- 前置条件：一封从未处理过的训练邮件。
- 操作步骤：
  1. 打开「AI 训练 → 历史邮件模拟回复」，在左侧列表点中该邮件
  2. 观察右侧面板；同时在 devtools Network 里过滤 `workbench/bootstrap`
- 预期结果：右侧显示「尚未分析这封来信」占位区与「开始分析」按钮；**Network 中没有 `POST /api/trust-reply/workbench/bootstrap` 请求**；「一键预判」与「重置」为灰色不可点。
- 覆盖：observable outcome 2；I-5；IP-2

### A-4: 点「开始分析」后行为与改造前一致
- 前置条件：承接 A-3。
- 操作步骤：点「开始分析」，等待完成。
- 预期结果：发出**恰好一次** `POST /api/trust-reply/workbench/bootstrap`；摘要列表、覆盖矩阵、事实卡片全部正常渲染，内容与本计划上线前对同一封信的结果一致；按钮文案变为「重新分析」，「一键预判」「重置」变为可点。
- 覆盖：observable outcome 2；I-5

### A-5（回归）: 三种全局操作仍出全局遮罩
- 前置条件：已分析的来信，全部摘要已解析。
- 操作步骤：分别执行 ① 切到「回复框架与整合」页保存框架 ② 点整合 ③ 点「一键预判」。
- 预期结果：三种情况都出现覆盖整个工作台的遮罩，文案分别为「正在保存回复框架…」「正在整合整封回复…」「正在生成回复…」（第三种带「取消生成」按钮）。
- 覆盖：What must NOT change 第 1 项

### A-6（回归）: 整合仍走整封快照且乐观锁生效
- 前置条件：同 A-5。
- 操作步骤：点整合，在 devtools 里看请求。
- 预期结果：仍发出 `PUT /api/trust-reply/workbench/state` 且请求体的 `lockedItems` 是**全部**条目；带 `expectedStateVersion`。
- 覆盖：What must NOT change 第 2 项；I-2

### A-7（回归）: 只读模式仍能看到内容
- 前置条件：以只读身份（或触发只读态的场景）打开一封已处理的来信。
- 操作步骤：观察面板。
- 预期结果：按方案 A，只读模式**自动完成分析**并展示只读横幅与全部内容；不出现「尚未分析」占位区。
- 覆盖：What must NOT change 第 3 项；T-4 的取舍

### A-8（回归）: 样式未失真
- 前置条件：已分析的来信。
- 操作步骤：对照「前端样式盘点 · 改动前基线」逐项目测：工具栏容器的左侧 2px 主色竖条是否还在、按钮高度是否仍为 32px、hint 文字是否仍为 11.5px 灰色；再切到暗色主题重看一遍。
- 预期结果：除新增的三按钮布局与「尚未分析」占位区外，工具栏与遮罩的视觉与改动前**无差异**；暗色主题下占位区的边框与背景随 token 正常反色，无白底黑字或黑底黑字。
- 覆盖：S-1；S-2；S-3
