# P0：SSE 生成错误码透出 + 工作台状态强制重置入口

> 顺序权威：本目录 `00-execution-order.md`。基线与"符号名优先、行号仅交叉验证"的约定见该文件。
> 本刀不修任何业务缺陷，只把两件事从"不可诊断/不可自救"变成"可诊断/可自救"。P1 与 P2a 的人工验收依赖本刀。

---

## 需求描述

### Observable outcome

1. 回复台逐条生成失败时，条目错误区显示**具体原因**（例如「来源或事实已变化，请刷新工作台」），不再一律是 `AI generation failed`；服务端日志留下该次失败的异常与错误码。
2. 当 `POST /api/trust-reply/workbench/bootstrap` 失败导致工作台整个打不开时，失败界面上出现一个「重置本封信的工作台状态」按钮；点击并二次确认后，该封信的锁定快照被删除，工作台可重新打开。

### What must NOT change

1. **成功路径零变化**：`ready` / `progress` / `heartbeat` / `result` / `cancelled` 五种事件的载荷与时序一字不改。
2. **取消语义不变**：`catch (_: AiReplyGenerationCancelledException)` 分支（`AiReplyGenerationCoordinator.kt:78-79`）保持原样，仍发 `cancelled`，仍不记错误日志。
3. **客户端断开分支不变**：`GenerationControl.sendLocked()` 内的 `catch (_: Exception)`（`AiReplyGenerationCoordinator.kt:300-309`）是"emitter 已断开"的正常路径，不属于本刀，一行不改。
4. **不泄露内部异常信息**：非 `TrustReplyWorkbenchException` 的异常，对外 `code` 固定、`message` 固定，**不得**把 `ex.message` / 栈信息放进 SSE 载荷。
5. **乐观并发不被削弱**：既有 `DELETE /api/trust-reply/workbench/state`（`TrustReplyWorkbenchController:110-112` → `deleteState(source, expectedStateVersion)`）继续要求版本匹配，继续在不匹配时抛 `TRUST_REPLY_STATE_CONFLICT`。强制重置走**独立入口**，不改这条。
6. **重置只删一行**：只删 `trust_reply_workbench_state` 中本 source 的那一行；QA 规则、片段、邮件记录、ES 文档一律不动。
7. **只读页零变化**：`state.readOnly` 为真时不得出现重置按钮。

### Out of scope（显式推迟）

1. **给 `GlobalExceptionHandler` 加 `TrustReplyWorkbenchException` 的全局 handler**。`TrustReplyWorkbenchController` 已有局部 `@ExceptionHandler`（`:114-118`）覆盖本目录全部普通 HTTP 端点；加全局 handler 会影响所有 controller，属独立决策。
2. **`app.js` 里其它 SSE 消费点的对齐**。本刀只改 `UnmatchedInboundMailController` 与 `trust-reply-workbench.js` 这一对；`app.js` 的 AI 草稿 SSE 是另一条链路。
3. **修 `saveState` 在 `expectedStateVersion == 0` 时返回假 `DELETED`**（见现状审计证据 E-5）。本刀新增独立重置入口绕开了它，就地修它会动 `saveState` 的既有语义。
4. **前端错误码文案的国际化**。本刀只加中文映射表。

---

## 关键不变量

### Invariant I-1: SSE error 事件必须携带 code，且只对已知业务异常透出真实码
- Rule: `error` 事件载荷固定为 `{generationId, code, message}` 三个键。
  - `ex is TrustReplyWorkbenchException` → `code = ex.code`，`message` 为固定通用文案（前端负责按 code 出中文，见 I-3）。
  - 其余任何异常 → `code = "AI_REPLY_GENERATION_FAILED"`，`message` 与现状一致。**不得**把 `ex.message`、`ex::class.simpleName`、栈或任何异常原文放进载荷。
- Applies to: `AiReplyGenerationCoordinator.start()` 的 `catch` 分支（当前 `:80-84`）；`UnmatchedInboundMailController` 内同形状的 `catch` 分支（当前 `:532-536`）。两处必须同时改，且载荷形状一致。
- Violation consequence: 只改一处 → 两个入口对同类失败给出不同信息；透出异常原文 → 内部实现细节（表名、SQL、类名）出现在浏览器里。
- 来源: original

### Invariant I-2: 异常必须落日志，且日志里有可关联的标识
- Rule: 两处 `catch` 都必须记一条 WARN 级日志，至少包含 `generationId`、判定出的 `code`、以及异常对象本身（作为 `Throwable` 参数传给 logger，保留栈）。`catch (_: Exception)` 这种**不绑定异常变量**的写法一律不允许留存。
- Applies to: 同 I-1 的两处。
- Violation consequence: 现状就是"响应和日志同时没有原因"，本刀的全部意义即在此。
- 来源: original（实证见证据 E-1）

### Invariant I-3: 前端展示优先级是 中文文案 > code > 兜底，且不改既有解析函数
- Rule: `errorFromStream()`（`trust-reply-workbench.js:89-95`）**一行不改**——它已经读 `data.code || data.errorCode` 并写入 `error.code`（证据 E-2）。新增一张 `code → 中文` 映射表，在渲染条目错误时按 `error.code` 查表；查不到时退回 `error.message`；再查不到用既有兜底文案。
- Applies to: `trust-reply-workbench.js` 的条目错误渲染路径。
- Violation consequence: 改 `errorFromStream` 会同时影响 `isStaleError()` / `isFrameStaleError()`（`:395-403`）的判据，那两处依赖 `error.code || error.message` 的现有形状。
- 来源: original

### Invariant I-4: 强制重置是破坏性操作，三重约束缺一不可
- Rule:
  (a) **入口位置**：只在 bootstrap 失败渲染出的失败界面里出现；正常加载成功的工作台里**不得**出现该按钮。
  (b) **二次确认**：点击后必须弹确认，文案明确写出"会清空本封信已锁定的全部回答"。
  (c) **独立服务端入口**：新增 `resetState(source)`，**不复用**带 `expectedStateVersion` 校验的 `deleteState`。`resetState` 不接受、不读取任何版本参数。
- Applies to: 新增的服务端方法与端点；`trust-reply-workbench.js` 的失败界面。
- Violation consequence: 缺 (a) 或 (b) → 运营在正常界面误点，丢掉全部已锁定回答；缺 (c) → 要么绕过乐观并发保护，要么根本解不开死锁（`stateStore.delete` 对 `expectedStateVersion == 0L` 直接 `return false`，见证据 E-4）。
- 来源: original

### Invariant I-5: 重置的作用域是一行
- Rule: `resetState` 只能按 `(source_type, source_id)` 删 `trust_reply_workbench_state` 的行，删除行数 0 或 1 都是成功（幂等），**不得**抛 `TRUST_REPLY_STATE_CONFLICT`。不得触碰 `qa_rule`、`reply_snippet`、`mail_record`、`inbound_mail_processing` 及任何 ES 索引。
- Applies to: 新增的 store 方法与 service 方法。
- Violation consequence: 越界删除会毁掉与本封信无关的运营资产；抛冲突则死锁解不开（死锁的成因恰恰是拿不到当前版本号）。
- 来源: original

### Invariant I-6: 重置后必须重新 bootstrap，且不得复用失败前的内存状态
- Rule: 重置成功后前端必须走一次完整的 `bootstrap()`（**不带** `preserveVersions`），并把 `state.savedStateVersion` 归零。不得直接把失败前的 `state.requests` 拿来渲染。
- Applies to: `trust-reply-workbench.js` 的重置处理函数。
- Violation consequence: 失败前的内存矩阵正是把 bootstrap 打挂的输入（P1 现状审计），复用它会立刻再次失败。
- 来源: original

---

## 样式契约

> 触发条件：本刀改动 `src/main/resources/static/trust-reply-workbench.js`（前端文件），Step 1b-fe 已执行。
> 总原则：**本刀不新增任何 CSS class，不改 `styles.css`。** 全部复用既有 class。

### S-1: 失败界面的重置按钮
- **复用**：`class="button secondary"`。该组合是本工作台既有按钮的标准写法，现成用例见 `trust-reply-workbench.js:1996` 的「重置」按钮（`<button type="button" class="button secondary" data-action="auto-reset">重置</button>`）。
  **禁止**执行 agent 自造 `.trust-reply-reset` 之类的新 class 或写 inline style。
- **新增 CSS**：无。本契约不新增任何规则块，`styles.css` 保持零改动。
- **DOM 结构**：`renderShell(message)`（`:1954-1961`）的 `<div class="ai-reply-feedback" data-role="status" …>${escapeText(message || "")}</div>` **保持原样**，在其**紧后面**追加：

```html
<div class="trust-reply-item-actions" data-role="shell-recovery"><button type="button" class="button secondary" data-action="reset-workbench-state">重置本封信的工作台状态</button></div>
```

  `.trust-reply-item-actions` 是既有 class（条目操作栏用，`:2156` 已在用），此处复用其横向布局，不新增规则。
- **渲染条件**：`renderShell` 增加一个参数控制是否输出上面这段；**只有** bootstrap 的 `catch` 分支（`:700-704`）调用时传真，且 `state.readOnly` 为真时**恒不输出**（must-NOT-change 第 7 条）。其它调用 `renderShell` 的地方一律不输出。
- **禁止项**：inline style；未在本契约声明的新 class；对 `.button`、`.button.secondary`、`.ai-reply-feedback`、`.trust-reply-item-actions` 既有规则块的任何修改。

### S-2: 条目错误文案（纯文本替换，无 DOM/CSS 变化）
- **复用**：条目错误的现有渲染节点与 class 完全不变，本契约只改**填进去的字符串**。
- **新增 CSS**：无。
- **DOM 结构**：不变。
- **禁止项**：不得为不同错误码引入不同样式（不加颜色、不加图标、不加新 class）。

---

## 现状审计

### Phase 0 知识加载（采用与驳回）

**采用**：
- `K-manual-send-safety-gate-first-hit-only` —— 其"预检静默丢弃 `code == null` 的违规"记录了同一类病（错误码被吞）。该条目已于 2026-08-20 修订，标注其"现状"部分已被 `a21784e` 修复；本刀处理的是**另一条链路**（SSE），不重叠。
- `K-js-tests-run-via-exec-plugin` —— 前端测试命令来源。
- `K-workbench-lock-replay-needs-dedicated-state-store` —— 状态存储是专用表，重置作用域据此界定（I-5）。

**读取后确认不适用**：
- `K-workbench-state-lazy-expiry`（本人 2026-08-20 新增）—— 讲的是**读**路径必须自判过期。本刀的 `resetState` 是**删**路径，删一行过期与否都对，无需判过期，也不得顺手调 `pruneExpired`（那会扩大影响面）。

### 数据存储：`trust_reply_workbench_state`

- 表结构与访问全部封装在 `TrustReplyWorkbenchStateStore`（`TrustReplyWorkbenchStateStore.kt:22`）。**本刀不新增表、字段、索引、迁移**，只新增一个按 source 删除的方法。
- **写路径（全集，`grep -rn "stateStore\.save\|stateStore\.delete\|stateStore\.pruneExpired" --include=*.kt src/main`）**：
  1. `TrustReplyWorkbenchService.saveState()`（`:515`）内的 `stateStore.delete(...)`（`:533`，锁定项清空时）
  2. 同上 `stateStore.pruneExpired(now)`（`:534`）
  3. 同上 `stateStore.save(...)`（`:565`）与 `stateStore.pruneExpired(now)`（`:572`）
  4. `TrustReplyWorkbenchService.deleteState()`（`:587`）内的 `stateStore.delete(...)`（`:589`）
  5. `restoreSavedStateWithFrame()`（`:617`）过期分支内的 `stateStore.pruneExpired(now)`
  本刀**新增第 6 条写路径**（`resetState` → 新的 `deleteBySource`），其余五条一行不改。
- **读路径（全集）**：`bootstrap()`（`:420`）的 `stateStore.load` / `decodePayload`，及其私有助手 `restoreSavedStateWithFrame()`（`:617`）内的 `decodePayload`。本刀不改读路径。

**证据 E-4 — 现有删除接口解不开死锁。** `TrustReplyWorkbenchStateStore.delete()`（`:84-99`）首行：

```kotlin
fun delete(sourceType: String, sourceId: Long, expectedStateVersion: Long): Boolean {
    if (expectedStateVersion == 0L) return false
    val deleted = jdbc.update(""" DELETE ... AND state_version = :expected """, ...)
    if (deleted == 1) return true
    throwStateConflict(sourceType, sourceId, expectedStateVersion)
}
```
传 0 直接 no-op，传错版本抛冲突。而死锁场景下前端**根本拿不到当前版本号**（见证据 E-3）。因此必须新增一个按 source 删除的方法，而不是给 `delete` 传 0。

**证据 E-5 — `saveState` 在版本为 0 时返回假 `DELETED`（本刀不修，仅记录）。** `TrustReplyWorkbenchService.saveState()`（`:532-536`）：
```kotlin
if (request.lockedItems.isEmpty()) {
    stateStore.delete(resolved.source.sourceType.name, resolved.source.sourceId, request.expectedStateVersion)
    stateStore.pruneExpired(now)
    return TrustReplySavedState(status = "DELETED", stateVersion = 0)
}
```
`stateStore.delete` 的返回值被丢弃，`expectedStateVersion == 0` 时它 `return false`（什么都没删），调用方仍返回 `status = "DELETED"`。**这条路径永远不会报错，也永远不会删掉东西。** 属 Out of scope 第 3 条。

### 关键路径：SSE 生成协调器

**证据 E-1 — 异常在响应与日志里同时消失。**
`AiReplyGenerationCoordinator.start()`（`:30`）的 worker 体，当前 `:73-90`：

```kotlin
control.workerFuture = executor.submit {
    control.markRunning()
    try {
        val response = operation(token, tracker, control::tryBeginCommit)
        control.sendTerminal("result", response)
    } catch (_: AiReplyGenerationCancelledException) {
        control.sendTerminal("cancelled", mapOf("generationId" to generationId))
    } catch (_: Exception) {
        control.sendTerminal(
            "error",
            mapOf("generationId" to generationId, "message" to "AI generation failed")
        )
    } finally {
        control.cleanup()
    }
}
```

- `catch (_: Exception)` 用下划线，**异常对象没有被绑定**，语法上无法记录。
- 实测 `grep -n "logger\|Logger\|log\." src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGenerationCoordinator.kt` → **零命中**，整个文件没有 logger。
- 异常被 `catch` 掉后不再向外传播，Spring 的全局 handler 也拿不到它。

**这就是"响应体只有 `AI generation failed`、服务端日志一片空白"的完整成因。**

**证据 E-1b — 同形状的代码被复制了一份。** `UnmatchedInboundMailController.kt`（当前 `:529-537`）有逐字相同的 `catch (_: AiReplyGenerationCancelledException)` / `catch (_: Exception)` 结构与相同的 `"AI generation failed"` 字面量。两处必须同时改（I-1）。

**证据 E-1c — 另一处 `catch (_: Exception)` 不属于本刀。** `GenerationControl.sendLocked()`（`:295-309`）的 `catch (_: Exception)` 捕的是 `emitter.send()` 失败，即**客户端已断开**，其处理是取消 token、清理资源，属正常路径。must-NOT-change 第 3 条。

**证据 E-2 — 前端已经准备好接收 code，服务端从未发过。**
`trust-reply-workbench.js:89-95`：
```javascript
function errorFromStream(data, fallback) {
    const code = data && typeof data === "object" ? (data.code || data.errorCode) : null;
    const message = data && typeof data === "object" ? data.message : data;
    const error = new Error(message || code || fallback);
    if (code) error.code = code;
    return error;
}
```
它已经读 `data.code` 并写进 `error.code`。消费方 `isStaleError()`（`:395-398`）与 `isFrameStaleError()`（`:400-403`）读 `error.code || error.message`。
**注意展示优先级**：`new Error(message || code || fallback)` 里 `message` 优先，所以服务端即使补上 `code`，界面文字仍是 `message`。因此 I-3 要求在渲染层按 `error.code` 查中文表，而不是改这个函数。

`error` 事件的消费点：`trust-reply-workbench.js` 逐条生成回调内 `else if (event === "error") { throw errorFromStream(data, "单项生成失败"); }`（当前 `:975-976`）。

### 关键路径：bootstrap 失败与自救

**证据 E-3 — 失败后 UI 没有任何自救路径。**
1. bootstrap 的 `catch`（`trust-reply-workbench.js:700-704`）只做 `setStatus(...)` + `renderShell(...)`，`renderShell`（`:1954-1961`）渲染的失败界面里**没有任何按钮**。
2. `deleteSavedState()`（`:814-833`）首行 `if (state.savedStateVersion <= 0) return true;`，而 `state.savedStateVersion` 只在 bootstrap 成功后由 `applySavedState(...)` 填入。
3. `deleteSavedState()` 全仓**只有一个调用点**：`changeRequestFacts()` 内（当前 `:1552`）——而 `changeRequestFacts` 本身要求工作台已经加载出来。
4. 即便手工调 `PUT /state` 传 `lockedItems: []`，`saveState` 也会先跑 `requireCurrentSourceVersion`（`:517`）与 `requireCurrentEvidenceVersion`（`:530`），这两个值同样只有 bootstrap 成功才有。

**四条合起来：bootstrap 一旦失败，运营在界面上无路可走。** 唯一的出路是等 30 天 TTL（`TrustReplyWorkbenchStateStore.kt:190` 的 `EXPIRY_DAYS = 30L`）或直接改库。

**证据 E-6 — 服务端已有 DELETE 端点，形状可直接照抄。**
`TrustReplyWorkbenchController.kt:110-112`：
```kotlin
@DeleteMapping("/state")
fun deleteState(@RequestBody request: TrustReplyDeleteStateHttpRequest): TrustReplySavedState =
    workbenchService.deleteState(request.source.toDomain(), request.expectedStateVersion)
```
请求体 `TrustReplyDeleteStateHttpRequest(source, expectedStateVersion)`（`:312-315`）。
新增的重置端点照抄这个形状，但请求体**只含 `source`**（I-4c）。

### Interaction points

| # | 写/产生 | 读/消费 | 影响 | 验收 |
|---|---|---|---|---|
| IP-1 | 协调器 `error` 事件载荷（`:80-84`） | `errorFromStream`（`:89-95`）→ `isStaleError` / `isFrameStaleError`（`:395-403`） | 载荷加 `code` 后这两个判据会**开始命中**（以前只能靠 message 里的子串）——是改善，但必须验证不误伤 | I-1 / I-3 / A-2 |
| IP-2 | `UnmatchedInboundMailController` 的 `error` 事件 | 同上（同一个前端解析函数） | 两处载荷必须同形状，否则同类失败两种表现 | I-1 / A-3 |
| IP-3 | 新增 `resetState` 删除的那一行 | `bootstrap()` 的 `stateStore.load` | 重置后再 bootstrap 必须走"无快照"分支（`restoreSavedStateWithFrame` 的 `if (stored == null)`，`:618-620`） | I-5 / I-6 / A-4 |
| IP-4 | `renderShell` 的新参数 | `renderShell` 的**全部 3 个**调用点（实测 `grep -n "renderShell("`：`:690` 加载中、`:704` bootstrap 失败、`:2450` 初始挂载） | 只有 `:704` 允许输出恢复区；`:690` 与 `:2450` 必须继续渲染出无按钮的界面 | I-4a / S-1 / A-6 |

---

## 实现方案

### 阶段 A：SSE 错误码与日志（I-1 / I-2）

**A-1. `AiReplyGenerationCoordinator.kt` —— 加 logger，改 `catch`。**

在类里加 `private val logger = LoggerFactory.getLogger(AiReplyGenerationCoordinator::class.java)`（`org.slf4j.LoggerFactory`，本仓库通用写法，现成范例见 `TrustReplyWorkbenchService.kt:376` 的 `private val logger = LoggerFactory.getLogger(TrustReplyWorkbenchService::class.java)`）。

worker 体的第三个 `catch` 改为：

```kotlin
} catch (ex: Exception) {
    // I-1: 只对已知业务异常透出真实 code；其余固定码，不泄露异常原文。
    val code = (ex as? TrustReplyWorkbenchException)?.code ?: CODE_GENERATION_FAILED
    // I-2: 响应与日志必须同时有原因；这里是本链路唯一的诊断点。
    logger.warn("AI reply generation failed: generationId={}, code={}", generationId, code, ex)
    control.sendTerminal(
        "error",
        mapOf(
            "generationId" to generationId,
            "code" to code,
            "message" to "AI generation failed"
        )
    )
}
```

`CODE_GENERATION_FAILED` 定义在该类 `companion object`（当前 `:131`）内：
```kotlin
const val CODE_GENERATION_FAILED = "AI_REPLY_GENERATION_FAILED"
```

约束：
- `catch (_: AiReplyGenerationCancelledException)`（`:78-79`）与 `catch (_: RejectedExecutionException)`（`:89`）**一字不改**。
- `sendLocked` 内的 `catch (_: Exception)`（`:295-309`）**一字不改**（must-NOT-change 第 3 条）。
- `message` 保持 `"AI generation failed"` 不变——中文由前端按 code 出（I-3），避免后端夹带文案。

**A-2. `UnmatchedInboundMailController.kt` —— 同形状改造。**

对当前 `:532-536` 的 `catch (_: Exception)` 施加与 A-1 完全相同的改造：绑定异常、取 code、记 WARN 日志、载荷加 `code`。该 controller 是否已有 logger，执行时先 grep；没有就照 A-1 的写法加。

**两处的载荷键名与取值逻辑必须逐字一致**（I-1）。

### 阶段 B：前端错误文案（I-3 / S-2）

**B-1. `trust-reply-workbench.js` —— 新增 code→中文映射表。**

在既有 `COVERAGE_LABELS`（`:31`，`const COVERAGE_LABELS = Object.freeze({...})`）**同一作用域、紧随其后**新增（注意该常量在模块 IIFE 内部，不是文件顶层）：

```javascript
// P0 (I-3): SSE / HTTP 错误码 → 运营可读中文。查不到时退回 error.message。
const WORKBENCH_ERROR_TEXT = {
    TRUST_REPLY_SOURCE_STALE: "来信内容已变化，请刷新工作台后重试。",
    TRUST_REPLY_EVIDENCE_STALE: "本条的事实已变化，请刷新工作台后重试。",
    TRUST_REPLY_EVIDENCE_VERSION_REQUIRED: "缺少事实版本，请刷新工作台后重试。",
    TRUST_REPLY_REQUEST_KEY_INVALID: "摘要标识与来信对不上，请刷新工作台后重试。",
    TRUST_REPLY_FACT_SELECTION_INVALID: "事实选择与来信摘要对不上，请刷新工作台后重试。",
    TRUST_REPLY_FACT_SELECTION_AMBIGUOUS: "事实选择参数冲突，请刷新工作台后重试。",
    TRUST_REPLY_FACT_ALREADY_ASSIGNED: "同一条事实被多个摘要绑定，请先解除其中一处。",
    TRUST_REPLY_HANDLING_INVALID: "该处理方式不适用于本条摘要的当前状态。",
    TRUST_REPLY_OPERATOR_INSTRUCTION_INVALID: "回答说明为空或超过 500 字。",
    TRUST_REPLY_ITEM_GENERATION_FAILED: "AI 未能产出可用的回答，请重试或换一种处理方式。",
    TRUST_REPLY_CLAIM_INVALID: "生成的内容未通过内容安全校验。",
    TRUST_REPLY_ACKNOWLEDGEMENT_INVALID: "致意内容未通过内容安全校验。",
    TRUST_REPLY_LOCKED_ITEM_INVALID: "已锁定的回答与当前状态不一致，请重新生成本条。",
    TRUST_REPLY_ITEM_VERSION_INVALID: "版本身份校验未通过，请重新生成本条。",
    TRUST_REPLY_STATE_CONFLICT: "该封信的工作台状态已被其他页面修改，请刷新后重试。",
    AI_REPLY_GENERATION_FAILED: "AI 生成失败，请重试。"
};
```

> 上表 16 个码全部来自实测：
> `grep -rn "TrustReplyWorkbenchException(HttpStatus" --include=*.kt src/main` 与 `AiReplyGenerationCoordinator` 的新常量。
> 执行时如发现有码未覆盖，**补进表里**即可（查不到会退回 message，不会崩）。

**B-2. 条目错误渲染改为按 code 查表。**

在设置 `request.error` 的地方（逐条生成失败路径）改为：先看 `error.code` 是否在 `WORKBENCH_ERROR_TEXT` 里，命中则用表里的中文，否则用 `error.message`，再否则用原有兜底串。

约束（S-2）：只改填进去的**字符串**，渲染节点、class、结构一律不动。

**B-3. 不改 `errorFromStream`（`:89-95`）、不改 `errorFromResponse`（`:81-87`）、不改 `isStaleError` / `isFrameStaleError`（`:395-403`）。**

### 阶段 C：强制重置（I-4 / I-5 / I-6 / S-1）

**C-1. `TrustReplyWorkbenchStateStore.kt` —— 新增按 source 删除。**

```kotlin
    /**
     * P0 (I-5): 强制重置专用——按 (source_type, source_id) 删行，不比对 state_version。
     * 删除 0 行或 1 行都算成功（幂等），绝不抛 TRUST_REPLY_STATE_CONFLICT：死锁的成因
     * 恰恰是调用方拿不到当前版本号。作用域严格限定本表本行，不做 pruneExpired。
     */
    fun deleteBySource(sourceType: String, sourceId: Long): Int =
        jdbc.update(
            """
            DELETE FROM trust_reply_workbench_state
             WHERE source_type = :sourceType AND source_id = :sourceId
            """.trimIndent(),
            MapSqlParameterSource("sourceType", sourceType).addValue("sourceId", sourceId)
        )
```

既有 `delete(...)`（`:84-99`）、`save(...)`、`pruneExpired(...)`、`load(...)`、`decodePayload(...)` 一字不改。

**C-2. `TrustReplyWorkbenchService.kt` —— 新增 `resetState`。**

放在既有 `deleteState`（`:587-591`）**之后**，两者并列且互不调用：

```kotlin
    /**
     * P0 (I-4c/I-5/I-6): 死锁自救专用。bootstrap 失败时前端拿不到 stateVersion，
     * 因而无法走带乐观并发校验的 deleteState；本方法按 source 无条件删行。
     * 破坏性操作，前端必须二次确认（I-4b），且只在失败界面暴露（I-4a）。
     * 只删 trust_reply_workbench_state 一行，不动 QA 规则/片段/邮件记录/ES（I-5）。
     * 不调 resolveSource：解析来信需要联系人与画像，而死锁场景下这些恰恰可能不可用。
     */
    fun resetState(source: TrustReplySourceRef): TrustReplySavedState {
        require(source.sourceId > 0) { "sourceId must be positive" }
        stateStore.deleteBySource(source.sourceType.name, source.sourceId)
        return TrustReplySavedState(status = "DELETED", stateVersion = 0)
    }
```

**特别注意**：故意**不调 `resolveSource(source)`**。`deleteState`（`:588`）调了它，而 `resolveSource` → `resolveLiveInbound` 会因联系人缺失抛 `TRUST_REPLY_SOURCE_CONTACT_REQUIRED`——在死锁场景下这会让自救入口本身也失败。source 的合法性由 `sourceId > 0` 与"只删自己这行"共同兜住。

**C-3. `TrustReplyWorkbenchController.kt` —— 新增端点。**

```kotlin
    @PostMapping("/state/reset")
    fun resetState(@RequestBody request: TrustReplyResetStateHttpRequest): TrustReplySavedState =
        workbenchService.resetState(request.source.toDomain())
```

请求体（放在 `TrustReplyDeleteStateHttpRequest`，当前 `:312-315` 之后）：
```kotlin
data class TrustReplyResetStateHttpRequest(
    val source: TrustReplySourceHttpRequest
)
```
**只有 `source`，没有版本字段**（I-4c）。既有 `@DeleteMapping("/state")` 一字不改（must-NOT-change 第 5 条）。

**C-4. `trust-reply-workbench.js` —— 失败界面的按钮与处理。**

1. `renderShell(message)` 增加第二个参数（如 `allowRecovery`），按 S-1 的逐字片段在状态区之后输出恢复区；`state.readOnly` 为真时恒不输出。
2. **只有** `:704`（bootstrap 的 `catch` 内）那一次 `renderShell` 传真。实测 `renderShell(` 全仓 3 个调用点：`:690`（"正在加载工作台…"）、`:704`（失败）、`:2450`（初始挂载）——前者与后者**保持现有单参数调用**（IP-4）。
3. 新增点击处理，挂在既有的 `data-action` 分发处（与 `:2269-2270` 的 `add-fact` / `remove-fact` 同一处）：

```javascript
if (action === "reset-workbench-state") void resetWorkbenchState();
```

4. 处理函数：

```javascript
// P0 (I-4b/I-6): 破坏性操作，必须二次确认；重置后走一次干净的 bootstrap，
// 绝不复用失败前的 state.requests——那份内存矩阵正是把 bootstrap 打挂的输入。
async function resetWorkbenchState() {
    if (state.readOnly) return;
    if (typeof global.confirm === "function" &&
        !global.confirm("重置会清空本封信已锁定的全部回答，且不可撤销。确认继续？")) {
        return;
    }
    try {
        await requestJson("/api/trust-reply/workbench/state/reset", { source });
        state.savedStateVersion = 0;
        state.requests = [];
        await bootstrap();
    } catch (error) {
        setStatus(errorText(error) || "重置失败，请刷新页面后重试", "ERROR");
        renderShell(errorText(error) || "重置失败，请刷新页面后重试", true);
    }
}
```

`state.requests = []` 是关键：它让下一次 `bootstrap()` 的 `requestFactSelections` 取值为 `null`（`:695` 的 `state.requests.length ? … : null`），从而走服务端的自动匹配回退路径。

`errorText(error)` 是 B-2 引入的按 code 查表的小助手，复用即可。

### 阶段 D：测试

**D-1. `AiReplyGenerationCoordinatorTest`（文件已存在，只加用例）**
- `business exception surfaces its code in the error event`：`operation` 抛 `TrustReplyWorkbenchException(422, "TRUST_REPLY_EVIDENCE_STALE")`，断言 error 事件载荷 `code == "TRUST_REPLY_EVIDENCE_STALE"`。
- `unknown exception surfaces the fixed code and no exception text`：`operation` 抛 `IllegalStateException("db password is xyz")`，断言 `code == "AI_REPLY_GENERATION_FAILED"`，且序列化后的载荷**不含** `"xyz"`（I-1 的不泄露约束）。
- `cancellation still emits cancelled and never error`：抛 `AiReplyGenerationCancelledException`，断言事件名为 `cancelled`（must-NOT-change 第 2 条）。

**D-2. `TrustReplyWorkbenchServiceTest` —— 新增 3 个用例**
- `resetState deletes the row by source without a version`：mock store，断言调用了 `deleteBySource(sourceType, sourceId)` 且**没有**调用 `delete(...)`。
- `resetState never resolves the source`：mock 一个会抛异常的 `inboundMailProcessingRepository`，断言 `resetState` 仍成功（C-2 的"不调 resolveSource"）。
- `deleteState still enforces the expected version`：回归，断言既有 `deleteState` 仍走 `stateStore.delete(..., expectedStateVersion)`。

**D-3. `src/test/js/trustReplyWorkbench.test.js` —— 新增 3 个用例**
- `error event code renders the mapped chinese text`：驱动 SSE `error` 事件带 `{code: "TRUST_REPLY_EVIDENCE_STALE", message: "AI generation failed"}`，断言渲染出的是中文表里的文案，**不是** `AI generation failed`。
- `bootstrap failure shell offers the reset button`：让 bootstrap 抛错，断言 `host.innerHTML` 含 `data-action="reset-workbench-state"`，且不含 inline `style=`。
- `successful bootstrap never renders the reset button`：正常加载，断言 `host.innerHTML` **不含** `data-action="reset-workbench-state"`（I-4a）。

---

## 变更文件清单

| # | 文件 | 改动性质 | 任务 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGenerationCoordinator.kt` | 加 logger、改第三个 `catch`、加一个 `const val` | A-1 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 同形状改一个 `catch` | A-2 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStore.kt` | 新增 `deleteBySource` | C-1 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 新增 `resetState` | C-2 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt` | 新增端点 + 请求体 data class | C-3 |
| 6 | `src/main/resources/static/trust-reply-workbench.js` | 错误码文案表、按 code 渲染、失败界面重置按钮与处理 | B-1, B-2, C-4 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGenerationCoordinatorTest.kt` | 新增用例 3 条（文件已存在，实测 `ls src/test/kotlin/.../llm/service/`） | D-1 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | 新增用例 3 条 | D-2 |
| 9 | `src/test/js/trustReplyWorkbench.test.js` | 新增用例 3 条 | D-3 |

**文件数：9（≤10 ✓）**
**子系统数：2 ✓** —— ① SSE 错误传递（1、2、6 的 B 段、7、9 的第 1 条）；② 工作台状态重置（3、4、5、6 的 C 段、8、9 的第 2/3 条）。两者可各自独立发布与验收。
**新增存储字段：0 ✓　新增表/索引/迁移：0 ✓　`styles.css` 改动：0 ✓**

---

## 验证命令

> 全量测试、构建、前端全量、语法检查、空白卫生一律见 `00-execution-order.md` 的「验证命令」节，此处不重复。

```bash
# 本刀新增/修改的后端测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=AiReplyGenerationCoordinatorTest,TrustReplyWorkbenchServiceTest,TrustReplyWorkbenchControllerTest

# D-1 三条单独运行
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='AiReplyGenerationCoordinatorTest#business exception surfaces its code in the error event'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='AiReplyGenerationCoordinatorTest#unknown exception surfaces the fixed code and no exception text'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='AiReplyGenerationCoordinatorTest#cancellation still emits cancelled and never error'

# D-2 三条单独运行
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='TrustReplyWorkbenchServiceTest#resetState deletes the row by source without a version'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='TrustReplyWorkbenchServiceTest#resetState never resolves the source'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='TrustReplyWorkbenchServiceTest#deleteState still enforces the expected version'

# D-3 前端用例
node --test src/test/js/trustReplyWorkbench.test.js
```

**通过判据**：同 `00-execution-order.md`。

---

## 验收标准

- **I-1**：`grep -n "AI generation failed" src/main -r` 命中的每一处，其所在 `mapOf` 都含 `"code" to`；两处载荷的键集合完全一致（`generationId` / `code` / `message`）。D-1 第二条用例断言载荷不含异常原文。
- **I-2**：`grep -n "catch (_: Exception)" src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGenerationCoordinator.kt` 只剩 **1 处**（`sendLocked` 内）；`grep -n "logger" .../AiReplyGenerationCoordinator.kt` 由 0 处变为 ≥2 处（声明 + 使用）。`UnmatchedInboundMailController.kt` 同理只剩 0 处该写法。
- **I-3**：`git diff src/main/resources/static/trust-reply-workbench.js` 中 `errorFromStream` / `errorFromResponse` / `isStaleError` / `isFrameStaleError` 四个函数体 **零改动**（diff 不含这些行）。D-3 第一条用例通过。
- **I-4**：`grep -n "expectedStateVersion" src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt` 的命中不包含新增的 reset 端点与其请求体；D-3 第二、三条用例通过（失败界面有按钮、成功界面无按钮）；重置处理函数中含 `confirm(`。
- **I-5**：`deleteBySource` 的 SQL 里只有 `trust_reply_workbench_state` 一张表，且 `WHERE` 只含 `source_type` 与 `source_id`；函数体内**不含** `pruneExpired` 与 `throwStateConflict`。D-2 第一条用例通过。
- **I-6**：重置处理函数内含 `state.requests = []` 且随后调用的是无参 `bootstrap()`（不含 `preserveVersions`）。
- **S-1**：`git diff src/main/resources/static/styles.css` **为空**；新增 DOM 片段与契约中的 HTML 逐字一致；`grep -n 'style="' src/main/resources/static/trust-reply-workbench.js` 的命中数不增加。
- **S-2**：条目错误渲染的 diff 中不出现新 class 名与新 `data-role`。
- **IP-1 / IP-2**：D-1 三条用例绿；两个 `catch` 的改造 diff 逐字同形。
- **IP-3**：D-2 第一、二条用例绿。
- **IP-4**：`grep -n "renderShell(" src/main/resources/static/trust-reply-workbench.js` 应命中 4 行（1 处定义 + 3 处调用，改动后 `resetWorkbenchState` 内再加 1 处调用共 4 处调用）；其中传了恢复参数的**只有** bootstrap `catch`（`:704`）与 `resetWorkbenchState` 的失败分支两处，`:690` 与 `:2450` 必须仍是单参数。
- **回归**：执行 `00-execution-order.md` 的全量测试与构建通过；前端全量与两个 `node --check` 通过；`git diff --check` 无输出。

---

## 人工验收清单

### A-1: 逐条生成失败显示具体原因（本刀主目标之一）
- 前置条件：一封已加载的来信；打开浏览器 F12 → Network
- 操作步骤：
  1. 在一个条目上正常生成一次，成功
  2. **在另一个浏览器标签页**打开同一封信，给某条摘要增删一个事实（制造 evidence 版本漂移）
  3. 回到第一个标签页，对该条目点「重试 AI 调整」
- 预期结果：条目错误区显示**「本条的事实已变化，请刷新工作台后重试。」**，不再是 `AI generation failed`。
- 覆盖：observable outcome 1；I-1、I-3、IP-1

### A-2: 服务端日志留下原因
- 前置条件：能看到应用日志
- 操作步骤：复现 A-1 后，查应用日志
- 预期结果：出现一条 WARN，含 `AI reply generation failed`、该次的 `generationId`、`code=TRUST_REPLY_EVIDENCE_STALE`，并带异常栈。（改动前此处**完全没有日志**。）
- 覆盖：I-2

### A-3: 未知异常不泄露内部信息（回归/安全）
- 前置条件：同 A-1
- 操作步骤：制造一次非业务异常（例如临时停掉 LLM 依赖使其抛运行时异常），观察浏览器 Network 里 SSE 的 `error` 事件载荷
- 预期结果：载荷为 `{generationId, code: "AI_REPLY_GENERATION_FAILED", message: "AI generation failed"}`；**不含**任何类名、SQL、表名、堆栈或异常原文。
- 覆盖：must-NOT-change 第 4 条；I-1

### A-4: bootstrap 失败后能自救（本刀主目标之二）
- 前置条件：一封处于"bootstrap 会 422"状态的来信（可按 `P1-fact-binding-drop-not-fatal.md` 的 A-1 制造：给 `UNSUPPORTED` 条目手动加事实）
- 操作步骤：
  1. 打开该来信的回复台，确认工作台加载失败
  2. 点击失败界面上的「重置本封信的工作台状态」
  3. 在确认框点确认
- 预期结果：确认框文案含「会清空本封信已锁定的全部回答，且不可撤销」；确认后工作台**重新加载成功**，条目回到自动匹配的事实状态。（改动前此处无任何按钮，只能等 30 天或改库。）
- 覆盖：observable outcome 2；I-4、I-5、I-6、IP-3

### A-5: 取消操作不受影响（回归）
- 前置条件：一封已加载的来信
- 操作步骤：发起一次逐条生成，在进行中点取消
- 预期结果：显示取消，**不**出现任何错误文案；日志里**没有** `AI reply generation failed`。
- 覆盖：must-NOT-change 第 2 条

### A-6: 正常界面不出现重置按钮（防误操作）
- 前置条件：任意一封能正常加载的来信
- 操作步骤：打开回复台，正常加载完成后通篇查看
- 预期结果：界面上**没有**「重置本封信的工作台状态」按钮。只读模式（模拟/只读页）同样没有。
- 覆盖：I-4a；must-NOT-change 第 7 条；IP-4

### A-7: 既有 DELETE /state 仍受版本保护（回归）
- 前置条件：同一封信在两个标签页打开，各自锁定过条目
- 操作步骤：在标签页 A 改事实触发状态删除，再在标签页 B 做同样操作
- 预期结果：标签页 B 出现状态冲突提示（「该封信的工作台状态已被其他页面修改，请刷新后重试。」），**不是**静默成功。
- 覆盖：must-NOT-change 第 5 条

### A-8: UI 目测（对照样式契约）
- 前置条件：A-4 的失败界面
- 操作步骤：对照 S-1 逐项核对
- 预期结果：按钮外观与工作台其它 `button secondary` 完全一致（同字号、同圆角、同配色、同 hover）；按钮位于状态提示文字下方；页面无任何 inline style。
- 覆盖：S-1

### A-9: 改动范围核对（防越界）
- 前置条件：本刀实现完成，且线 A 已提交（见 `00-execution-order.md`）
- 操作步骤：`git diff --name-only`
- 预期结果：输出恰好为变更文件清单的 9 个路径。特别确认 `styles.css`、`GlobalExceptionHandler.kt`、`app.js` **不在**其中。
- 覆盖：I-1 的范围约束、S-1、Out of scope 第 1、2 条
