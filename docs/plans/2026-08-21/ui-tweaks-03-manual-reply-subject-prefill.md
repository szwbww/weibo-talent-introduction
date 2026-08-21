# 计划 P3：人工富文本回复自动填入回复主题

- 基线：`main`，在 P2 之后
- 顺序：见 `ui-tweaks-00-execution-order.md`，本计划**第三**（与 P1、P2 仅在缓存键上串行）
- 子系统数：1（来信详情人工回复）
- 变更文件数：4
- 缓存键：`20260821-v11-reply-subject-prefill`

---

## 需求描述

### Observable outcome

1. 打开一封来信的详情、展开「人工富文本回复」时，主题输入框**不再是空的**，已按专家来信主题预填好回复主题：
   - 来信主题 `Application for the talent programme` → 预填 `Re: Application for the talent programme`
   - 来信主题 `Re: Application for the talent programme`（专家自己就是在回信）→ 预填 **`Re: Application for the talent programme`**（不叠加成 `Re: Re: …`）
   - 来信主题为空 → 预填 `Re:`
2. 预填值是**可编辑**的普通输入框内容：运营可以随意改写、清空、粘贴替换；改完后点「发送人工回复」发出去的就是框里的最终值。

### What must NOT change

- 发送校验不变：主题被清空后点发送，仍提示「请输入邮件主题」并中止（`app.js:10424-10428`）。
- 发送请求体不变：仍是 `{ senderAccountCode: null, subject, htmlBody, textBody, operatorName }`（+ 采用草稿时的 `templateTextBody` / `trustReplyAssembly`），`subject` 取输入框当前值 `.trim()`。
- 后端 `POST /api/mail/unmatched-inbound/{id}/manual-rich-reply` 与 `PendingMailOperationService.sendManualRichReply` **一行不改**：主题仍走 `require(subject.isNotBlank())` → `trim()` → `length <= 255` → `requireValidPlaceholders` → `renderForContact`，服务端**不会**再补 `Re:`。
- 主题输入框的 `id="manualReplySubject"`、`placeholder="邮件主题"`、`style="margin-bottom:8px;"` **逐字不变**（该 inline style 是既有的，本计划不清理）。
- 「人工富文本回复」折叠头那枚写死的 `未填写` 状态标签不动（它本来就从不更新）。
- 从「可信回复工作台」采用草稿到人工回复区（`adoptTrustReplyAssembly`，`app.js:9589-9615`）只写正文编辑器，**不碰主题**；采用后主题仍是预填值。

### Out of scope

见 `ui-tweaks-00-execution-order.md` 的「已明确不做」全部四条。另外：

- 不给专家详情页的其它发信入口（`ManualExpertMailService` 那条材料提醒链路）加预填 —— 它本来就在服务端算主题。
- 不改 `GroundedAutoReplyDecisionService.buildReplySubject`（Kotlin），前端只做**镜像**。
- 不做「主题被改过就不再被重渲染覆盖」的状态保持 —— 详情面板重渲染本来就会重建整个正文编辑器，主题跟随同一节奏是既有行为（见 I-3）。
- 不清理 `app.js` 里 `#manualReplySubject` / 「发送人工回复」按钮上的既有 inline style。

---

## 关键不变量

### Invariant I-1：前端预填规则逐字镜像服务端 `buildReplySubject`
- Rule：新增的 `buildManualReplySubject(inboundSubject)` 必须与 `GroundedAutoReplyDecisionService.buildReplySubject`（`src/main/kotlin/.../mail/service/GroundedAutoReplyDecisionService.kt:93-103`）**行为等价**：
  1. `trim()`；
  2. 结果为空 → 返回 `"Re:"`；
  3. 以 `"Re:"` 开头（**大小写不敏感**）→ 原样返回 trim 后的值；
  4. 否则返回 `"Re: " + trimmed`。
  唯一允许的额外行为是 I-2 的长度截断。**禁止**自造别的规则（如 `stripReplyPrefixes` 反复剥离 —— 那是 `ManualExpertMailService:281-286` 的另一套语义，两者不可混用）。
- Applies to：新增的 `buildManualReplySubject`；`renderUnmatchedDetail` 中主题输入框的 `value` 生成。
- Violation consequence：专家回信主题已带 `Re:` 时叠成 `Re: Re: …`，或大小写为 `RE:` / `re:` 时漏判，运营每封都要手工改。
- 来源：original（本轮读 Kotlin 源码实证）

### Invariant I-2：预填值必须先截断到 255 字符，再进 `value`
- Rule：`buildManualReplySubject` 的返回值必须 `.slice(0, 255)`。理由：服务端 `PendingMailOperationService.sendManualRichReply`（`:159`）有 `require(trimmedSubject.length <= 255) { "Subject exceeds 255 characters" }`，超长直接 400。同仓库的 `ManualExpertMailService.buildReplySubject`（`:278`）也用 `.take(255)`。
- Applies to：`buildManualReplySubject` 的返回路径。
- Violation consequence：转发链很长的来信（主题带多层 `Fwd:`／会议纪要标题）预填后超 255，运营一点发送就吃 400 且**不知道原因出在预填**。
- 来源：original（本轮读 `PendingMailOperationService:156-159` 与 `ManualExpertMailService:275-279` 实证）

### Invariant I-3：预填只在详情面板渲染时发生一次，且必须 HTML 转义
- Rule：预填通过 `renderUnmatchedDetail` 生成 `<input … value="${escapeHtml(buildManualReplySubject(record.subject))}">` 实现。**禁止**在渲染之后用 JS 二次赋值 `subjectEl.value = …`，**禁止**加 `input` 监听器做「已被编辑就不覆盖」的状态保持。转义必须走既有 `escapeHtml`（`app.js:1488-1495`，已转义 `"` 与 `'`）。
- Applies to：`app.js:9956` 那一行 `<input id="manualReplySubject" …>`。
- Violation consequence：① 二次赋值会与「详情面板重渲染即重建正文编辑器」的既有节奏错位，出现「正文被清空但主题保留上一封的」这种更难解释的状态；② 不转义时，主题里的 `"` 会截断 `value` 属性 → 属性注入，主题里的 `<` 会破坏后续 DOM。
- 来源：original

### Invariant I-4：预填不改变服务端占位符校验的既有语义
- Rule：预填值原样来自来信主题，**不做任何占位符清洗**。若来信主题恰好含 `${...}`（服务端 `MailPlaceholderService.PLACEHOLDER_REGEX = \$\{([^}]*)\}`，`:99`），发送时仍会被 `requireValidPlaceholders` 拒绝并返回 `Invalid placeholders: …`。这是**既有行为**（今天运营手工输入同样的主题也会被拒），本计划不引入静默改写来规避它。
- Applies to：`buildManualReplySubject`（不得内置任何 `${` 替换/剥离）。
- Violation consequence：若为了「让它能发出去」而悄悄改写主题，运营发出的主题与他看到的不一致 —— 对外发信内容与界面脱节，比报错严重得多。
- 来源：original（本轮读 `MailPlaceholderService:40-56, 99` 实证）

### Invariant I-5：缓存键三键同值、同时 bump，并同步逐字断言
- Rule：`index.html:11 / 2074 / 2075` 三处 `?v=` 统一改为 `20260821-v11-reply-subject-prefill`；同步 `src/test/js/batchSendTaskConsoleVisualFix.test.js:49-51` 三条逐字断言。
- Violation consequence：`trustReplyWorkbenchSharedMount.test.js:347-348` 三键相等断言失败 → `mvn test` 中止 → WAR 构建失败。
- 来源：K-frontend-cache-key-triad

---

## 样式契约

### S-1：主题输入框只加 `value`，其余属性逐字不动
- 改动前基线（`app.js:9956`，`renderUnmatchedDetail` 模板内，逐字）：

```js
                <input id="manualReplySubject" placeholder="邮件主题" style="margin-bottom:8px;">
```

- 改动后（逐字；只在 `placeholder` 与 `style` 之间插入 `value` 属性，其余字符、缩进、既有 inline style 全部保留）：

```js
                <input id="manualReplySubject" placeholder="邮件主题" value="${escapeHtml(buildManualReplySubject(record.subject))}" style="margin-bottom:8px;">
```

- 复用：不新增任何 class、不新增任何 CSS。输入框沿用它今天的默认外观（`.reply-workflow-content` 内的裸 `input`）。本计划 **`styles.css` 零改动**。
- 禁止项：不得给输入框加 class 或改 inline style；不得改 `placeholder` 文案；不得新增 `required` 属性（发送校验已在 JS 侧，见 What must NOT change 第 1 条）。

---

## 现状审计

### `app.js` — 主题输入框的全部读写路径
- **渲染（唯一写路径）**：`renderUnmatchedDetail(...)` 的 `panel.innerHTML = …` 模板，`app.js:9956`。`#manualReplySubject` 在 `app.js` 中**仅此 1 处渲染**（grep 2 命中：`:9956` 渲染、`:10424` 读取）。
- **读取（唯一读路径）**：`handleUnmatchedAction` 的 `action === "send-manual-rich-reply"` 分支，`app.js:10424`：

```js
        const subject = $("#manualReplySubject")?.value?.trim();
        if (!subject) {
            showStatus("请输入邮件主题", "error");
            return;
        }
```

  随后进入 `requestBody.subject`（`app.js:10437`）→ `submitManualRichReply` → `POST /api/mail/unmatched-inbound/${recordId}/manual-rich-reply`（`app.js:10356`）。
- **不写主题的相邻路径（确认过，不受影响）**：
  - `adoptTrustReplyAssembly(recordId, assembly)`（`app.js:9589-9615`）只写 `#manualRichReplyEditor` 与 `aiReplyState.adoptContext` / `manualReplyQaContext`，**不碰主题**。
  - `submitManualRichReply` 的门禁重试路径（`app.js:10376-10415`）用同一个 `requestBody` 重发，**不重读主题**。
  - `resetPreflightState()` / `schedulePreflightCheck()` 只处理正文，**不碰主题**。

### 详情面板重渲染时机（决定预填何时被重置）
`showUnmatchedDetail(id)` 共 6 个调用点（`app.js:9996 / 10106 / 10130 / 10150 / 10418 / 10991`），每次都整块 `panel.innerHTML = …`。因此**正文编辑器与主题一起**回到初始态 —— 正文回到空、主题回到预填值。这是既有节奏，本计划顺承（I-3）。

### 服务端契约（本计划不改，仅确认边界）
- `UnmatchedInboundMailController.kt:234-240` `@PostMapping("/unmatched-inbound/{id}/manual-rich-reply")` → `PendingMailOperationService.sendManualRichReply(...)`。
- `PendingMailOperationService.sendManualRichReply` 对主题的处理（`:156-186`）：
  1. `require(subject.isNotBlank()) { "Subject is required" }`
  2. `val trimmedSubject = subject.trim()`
  3. `require(trimmedSubject.length <= 255) { "Subject exceeds 255 characters" }` ← **I-2 的依据**
  4. `mailVariableService.requireValidPlaceholders(trimmedSubject)` ← **I-4 的依据**
  5. `mailVariableService.renderForContact(trimmedSubject, account, contact)`
  服务端**从不**给主题补 `Re:`。
- 仓库内两套已有的回复主题算法（本计划镜像第一套）：
  | 位置 | 规则 | 空值 | 长度 |
  |---|---|---|---|
  | `GroundedAutoReplyDecisionService.buildReplySubject`（`:93-103`） | 已以 `Re:` 开头（忽略大小写）则原样，否则 `"Re: " + s` | `"Re:"` | 不截断 |
  | `ManualExpertMailService.buildReplySubject`（`:275-279`） | 先 `stripReplyPrefixes` 反复剥前缀，再 `"Re: " + stripped` | 用 `fallback` | `.take(255)` |
  | `AutoReplyPreviewService:105` / `AutoMailReplyService:996` | `rendered.subject.ifBlank { "Re: ${…}".trim() }` | — | — |
  选第一套的理由：它的语义是「保持专家那条线程的主题不变」，最贴合人工回信；第二套会把 `Fwd:` 一并剥掉，改变线程主题。

- **Interaction point A**：预填值 → `app.js:10424` 的非空校验。预填后该校验**默认不再触发**；只有运营主动清空才触发。必须有回归验收项（A-4）。
- **Interaction point B**：预填值 → 服务端 255 长度校验。长主题是**真实高发**场景，必须由 I-2 在前端截断兜住。
- **Interaction point C**：预填值 → 服务端占位符校验。含 `${` 的主题是**极低频**场景，按 I-4 保持既有报错语义，不静默改写。

### 前端样式盘点
- 可复用 class：无（本计划不碰样式）。
- 设计基准 token：不涉及。
- DOM 结构约定：`<details class="detail-section reply-workflow-detail manual-rich-reply-section">` → `<summary class="reply-workflow-summary">` → `<div class="reply-workflow-content">` → `<input id="manualReplySubject">` + `.rich-toolbar` + `#manualRichReplyEditor.rich-editor` + 发送按钮。
- 改动前基线：见 S-1 的「改动前基线」代码块。
- 结论：`styles.css` **零改动**；本计划的样式契约只有 S-1 这一条「属性级」约束。

---

## 实现方案

### 阶段 1：新增镜像函数（I-1、I-2、I-4）

**T1-1**　`app.js`：在 `escapeHtml`（`:1488-1495`）附近的工具函数区，或紧邻 `renderUnmatchedDetail` 之前，新增下列函数（逐字复制）：

```js
// P3 (I-1): mirrors GroundedAutoReplyDecisionService.buildReplySubject —
// trim; blank -> "Re:"; already "Re:"-prefixed (case-insensitive) -> as is;
// otherwise "Re: " + subject. I-2: cap at 255 so the server's length guard
// (PendingMailOperationService: Subject exceeds 255 characters) can never be
// tripped by the prefill itself. I-4: no placeholder rewriting here.
function buildManualReplySubject(inboundSubject) {
    const trimmed = String(inboundSubject ?? "").trim();
    if (!trimmed) return "Re:";
    const prefixed = trimmed.slice(0, 3).toLowerCase() === "re:" ? trimmed : `Re: ${trimmed}`;
    return prefixed.slice(0, 255);
}
```

### 阶段 2：接到渲染模板（I-3、S-1）

**T2-1**　`app.js:9956`：按 S-1 把主题输入框改成带 `value="${escapeHtml(buildManualReplySubject(record.subject))}"` 的形式，其余属性逐字不动。
**T2-2**　`app.js:10422-10428`（发送分支的读取与非空校验）**不改**。
**T2-3**　`app.js:9589-9615`（`adoptTrustReplyAssembly`）**不改**。

### 阶段 3：缓存键与测试（I-5）

**T3-1**　`index.html:11 / 2074 / 2075` 三处 `?v=` 改为 `20260821-v11-reply-subject-prefill`。
**T3-2**　`src/test/js/batchSendTaskConsoleVisualFix.test.js:49-51` 三条字符串同步改为新键。
**T3-3**　新增 `src/test/js/manualReplySubjectPrefill.test.js`：沿用仓库既有的 `vm` + `extractFn` 套路，从 `app.js` 抽出 `buildManualReplySubject` 并断言 I-1/I-2 的全部分支；再以源文本正则断言 S-1 的 `value` 属性形态与 I-3（无二次赋值、无 `input` 监听）。必须覆盖的用例矩阵：

| 输入 | 期望输出 | 覆盖 |
|---|---|---|
| `"Application for the talent programme"` | `"Re: Application for the talent programme"` | I-1 规则 4 |
| `"Re: Application"` | `"Re: Application"` | I-1 规则 3 |
| `"RE: Application"` | `"RE: Application"` | I-1 规则 3（大写） |
| `"re: Application"` | `"re: Application"` | I-1 规则 3（小写） |
| `"  Application  "` | `"Re: Application"` | I-1 规则 1 |
| `""` / `"   "` / `null` / `undefined` | `"Re:"` | I-1 规则 2 |
| `"Reply about funding"` | `"Re: Reply about funding"` | I-1 规则 3 的**边界**：`Re` 后面不是冒号时不算前缀 |
| 长度 300 的主题 | 结果长度 == `255` | I-2 |
| `"Fwd: ${expertName} 的申请"` | `"Re: Fwd: ${expertName} 的申请"`（占位符**原样保留**） | I-4 |

**T3-4**　`src/test/js/expertProfileAbsence.test.js`（A4）：`createRendererSandbox()`（约 `:326-349`）在 `showUnmatchedDetail` 注册（`:348`）之前新增一行 `vm.runInContext(extractFunction("buildManualReplySubject"), sandbox);`；其余**逐字不动**。原因：S-1 让 `showUnmatchedDetail` 调用新增的顶层函数 `buildManualReplySubject`，沙箱固定函数清单不含它 → `ReferenceError`（K-dom-stub-tests-hide-dangling-refs 的 vm 沙箱形态）。

---

## 变更文件清单

| # | 文件 | 动作 | 覆盖 |
|---|---|---|---|
| 1 | `src/main/resources/static/app.js` | 改 | T1-1, T2-1 |
| 2 | `src/main/resources/static/index.html` | 改（3 行） | T3-1 |
| 3 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 改（3 行） | T3-2 |
| 4 | `src/test/js/manualReplySubjectPrefill.test.js` | 新增 | T3-3 |
| 5 | `src/test/js/checkRepliesRelocation.test.js` | 改 | A3：键 v10→v11 |
| 6 | `src/test/js/overlayAndDialogContrast.test.js` | 改 | A3：键 v10→v11 |
| 7 | `src/test/js/expertProfileAbsence.test.js` | 改（1 行） | T3-4（A4：沙箱注册 buildManualReplySubject） |

合计 7 个文件，1 个子系统。`styles.css`、`trust-reply-workbench.js` 与全部 Kotlin 源码/测试**不在清单内**。

---

## 验证命令

> 前提一：本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败（`CLAUDE.md` 的 Commands 章节）。
> 前提二：前端 JS 用例的权威门禁是 `node --test <file>` 单跑；`verify.sh` **只跑一个文件，不可作为本计划的回归门禁**（K-js-test-invocation-surface）。
> 环境实测：`node -v` = `v22.23.2`。

```bash
# 1) 本计划直接改动/新增的 JS 用例（快速迭代用）
node --test src/test/js/manualReplySubjectPrefill.test.js
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js
node --test src/test/js/unmatchedQaReplySource.test.js

# 2) 前端全量 JS 用例（本计划的前端回归门禁）
node --test src/test/js/*.test.js

# 3) 语法检查
node --check src/main/resources/static/app.js
node --check src/main/resources/static/task-modal-runtime.js

# 4) 全量回归
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 5) 服务端主题契约回归（确认前端镜像的那条 Kotlin 规则未被本轮改动）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=GroundedAutoReplyDecisionServiceTest

# 6) 空白/换行卫生
git diff --check
```

通过判据：
- 命令 1/2：退出码 0，输出含 `# fail 0`。
- 命令 3：退出码 0，无输出。
- 命令 4：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`，且输出中出现 `node --test` 执行记录。
- 命令 5：退出码 0；若该测试类不存在，Surefire 会报「No tests were executed」—— 此时按命令 4 的全量结果判定即可，**不视为失败**（本计划不改 Kotlin）。
- 命令 6：退出码 0，无输出。

来源：`CLAUDE.md` 项目元信息 + `pom.xml:188-231` + 实测。

---

## 验收标准

- **I-1**：`manualReplySubjectPrefill.test.js` 的 9 条输入/输出用例全部通过；`grep -c 'function buildManualReplySubject' src/main/resources/static/app.js` == `1`。
- **I-2**：测试断言长度 300 的输入返回值 `.length === 255`；`grep -n 'slice(0, 255)' src/main/resources/static/app.js` 命中且位于 `buildManualReplySubject` 内。
- **I-3**：`grep -c 'manualReplySubject' src/main/resources/static/app.js` == `2`（渲染 1 + 读取 1，与改动前相同）；`git diff src/main/resources/static/app.js` 中**不含** `manualReplySubject` 的 `.value =` 赋值，也**不含**对该 id 的 `addEventListener`；测试断言 `app.js` 源文本匹配 `/<input id="manualReplySubject" placeholder="邮件主题" value="\$\{escapeHtml\(buildManualReplySubject\(record\.subject\)\)\}" style="margin-bottom:8px;">/`。
- **I-4**：测试断言含 `${expertName}` 的输入输出**逐字保留**该占位符；`buildManualReplySubject` 函数体内不含字符串 `"${"` 的替换逻辑（`grep -c 'replace' `在该函数区间内为 `0`）。
- **I-5**：`grep -o '?v=[^"]*' src/main/resources/static/index.html | sort -u` 只输出一行 `?v=20260821-v11-reply-subject-prefill`；`batchSendTaskConsoleVisualFix.test.js` 通过。
- **S-1**：`app.js:9956` 那一行与契约「改动后」代码块 `diff` 为空；`git diff src/main/resources/static/styles.css` 为空（本计划零样式改动）。
- 回归：执行「验证命令」节的命令 2、3、4、6 全部通过。

---

## 人工验收清单

### A-1：普通来信主题自动加 `Re:`
- 前置条件：收发件箱中有一封来信，主题为不带 `Re:` 前缀的英文标题（例如 `Application for the talent programme`）。若没有，可在测试邮箱用该主题给任一已激活账号发一封信，等「检查回复」拉进来。
- 操作步骤：1）打开该来信详情；2）展开「人工富文本回复」；3）看主题输入框。
- 预期结果：输入框内已有文字 **`Re: Application for the talent programme`**（`Re:` 后一个空格），灰色 placeholder「邮件主题」不再显示。
- 覆盖：需求描述 observable outcome 1；I-1

### A-2：专家回信主题不叠加 `Re: Re:`
- 前置条件：一封主题已带 `Re:` 前缀的来信（专家回复我们首封介绍信时自然如此，例如 `Re: Invitation to the talent programme`）。
- 操作步骤：1）打开该来信详情；2）展开「人工富文本回复」；3）看主题输入框。
- 预期结果：输入框内为 **`Re: Invitation to the talent programme`**，**只有一个** `Re:`，且大小写与来信原样一致（来信是 `RE:` 就显示 `RE:`）。
- 覆盖：需求描述 observable outcome 1；I-1

### A-3：无主题来信预填 `Re:`
- 前置条件：一封主题为空的来信（详情页顶部标题显示「（无主题）」）。
- 操作步骤：1）打开该来信详情；2）展开「人工富文本回复」；3）看主题输入框。
- 预期结果：输入框内为 **`Re:`**（三个字符，冒号后无空格）。
- 覆盖：需求描述 observable outcome 1；I-1

### A-4（回归 · Interaction point A）：清空主题仍然拦截发送
- 前置条件：任一已绑定专家的来信，正文编辑器里已写入内容。
- 操作步骤：1）展开「人工富文本回复」；2）**全选删除**主题输入框里的预填内容；3）点「发送人工回复」。
- 预期结果：页面顶部出现红色提示 **「请输入邮件主题」**，邮件**不发送**，不弹出任何确认框。
- 覆盖：需求描述 What must NOT change 第 1 条；现状审计 Interaction point A

### A-5：改写主题后发出去的是改写值
- 前置条件：一封可安全试发的来信（测试专家），正文已写好。
- 操作步骤：1）展开「人工富文本回复」；2）把主题改为 `Re: 人工验收测试 A-5`；3）点「发送人工回复」并完成发送；4）到「收发件箱」找到刚发出的这封发件记录，查看其主题。
- 预期结果：发件记录的主题**逐字**为 `Re: 人工验收测试 A-5`，不是预填的原值，服务端也没有额外补 `Re:`。
- 覆盖：需求描述 observable outcome 2；What must NOT change 第 2、3 条

### A-6（跨路径 · Interaction point B）：超长主题不会导致发送报错
- 前置条件：一封主题很长的来信（≥ 260 个字符；可用测试邮箱自行发一封长主题的信构造）。
- 操作步骤：1）打开该来信详情；2）展开「人工富文本回复」；3）把光标移到主题框末尾按 End 键，确认预填值末尾是被截断的；4）写好正文后点「发送人工回复」。
- 预期结果：第 3 步主题框内容长度不超过 255 字符（可全选复制到任意计数工具核对）；第 4 步发送**成功**，**没有** `Subject exceeds 255 characters` 或「人工回复发送失败」的报错。
- 覆盖：I-2；现状审计 Interaction point B

### A-7（回归）：从可信回复工作台采用草稿后，主题保持预填
- 前置条件：一封已绑定专家、含可识别请求的来信。
- 操作步骤：1）展开「可信回复工作台」，走完一键预判 → 整合 → 采用到人工回复区；2）看「人工富文本回复」的主题输入框。
- 预期结果：正文编辑器被填入采用的草稿内容，主题输入框**仍是 A-1/A-2 规则下的预填值**（既没有被清空，也没有被草稿覆盖）。
- 覆盖：需求描述 What must NOT change 第 6 条

### A-8（回归）：详情面板重新打开后主题回到预填值
- 前置条件：同 A-1。
- 操作步骤：1）展开「人工富文本回复」，把主题改成 `临时随手写的`；2）点面板右上角「关闭」；3）重新点开同一封来信的详情，展开「人工富文本回复」。
- 预期结果：主题回到 **`Re: <来信主题>`** 的预填值（不是 `临时随手写的`），正文编辑器同样是空的 —— 与本次改动前「重开即重置」的节奏一致。
- 覆盖：I-3

### A-9（UI 目测）：输入框外观未变
- 前置条件：任一来信详情。
- 操作步骤：目测「人工富文本回复」里的主题输入框。
- 预期结果：宽度、高度、边框、圆角、字号、与下方富文本工具栏之间的 **8px** 间距，与本次改动前**完全一致**；唯一变化是框内多了预填文字。
- 覆盖：S-1
