# P1：定时任务 cron 回显改为白名单反解

主计划：`batch-task-filters-main.md`
子系统数：1（前端）  文件数：2
依赖：无。可最先执行。

---

## 需求描述

### Observable outcome

打开一个 cron 为 `0 0 9-17 * * ?` 的定时任务，「执行频率」显示「自定义 cron」、cron 输入框原样填回 `0 0 9-17 * * ?`；不做任何修改直接保存，落库 cron 仍是 `0 0 9-17 * * ?`。

### What must NOT change

- **N1-1** 三种预设频率的回显不变：`0 0 * * * ?` → 每小时；`0 15 3 * * ?` → 每天 03:15；`0 30 9 ? * MON` → 每周一 09:30。
- **N1-2** 保存逻辑一行不改。自定义模式仍直接提交输入框原串（`app.js:14043-14046`），已有回归测试 `batchSendTaskConsoleInteraction.test.js:466` 必须继续绿。
- **N1-3** `syncBatchConfigEditorScheduleFields()` 的调用位置与时机不变。
- **N1-4** 后端、数据库、调度器零改动。`BatchSendTaskConfigService.normalizeAndValidate` 用 `CronExpression.parse` 校验（`BatchSendTaskConfigService.kt:248-252`），本计划不触碰。

### Out of scope

- cron 输入框的「测试」按钮与 `POST /api/mail/batch-send/cron/preview`（`BatchSendConfigController.kt:89`）。
- 增加更多预设频率（每周多天、每月等）。
- 保存路径的任何改动。
- 手动执行面板 —— 它没有 cron 字段。

---

## 关键不变量

### Invariant I1-1: 反解是白名单，不是黑名单
- Rule: 只有 **完全** 匹配下列三个正则之一的 cron 才映射为预设频率；其余一律 `custom`，并把 `config.cron` 原串填进 `#batchConfigEditorCron`。
  ```
  hourly : /^0 0 (\*|\*\/1) \* \* \?$/
  daily  : /^0 (\d{1,2}) (\d{1,2}) \* \* \?$/      且 min∈[0,59] 且 hour∈[0,23]
  weekly : /^0 (\d{1,2}) (\d{1,2}) \? \* MON$/     且 min∈[0,59] 且 hour∈[0,23]
  ```
- Applies to: `showBatchConfigEditor` 的 cron 反解块（`app.js:13525-13537`）。
- Violation consequence: 见「现状审计」的静默数据损坏链。
- 来源: original

### Invariant I1-2: 非 custom 分支必须清空 cron 输入框
- Rule: 反解结果为 hourly/daily/weekly 时，必须 `setVal("batchConfigEditorCron", "")`；为 custom 时必须 `setVal("batchConfigEditorCron", config.cron)`。两条路径都要显式写值，不允许"不动它"。
- Applies to: 同上。
- Violation consequence: `showBatchConfigEditor` 复用同一份 DOM，不是每次重建。先编辑任务 A（custom，cron 框留下 `0 0 9-17 * * ?`）→ 取消 → 编辑任务 B（daily）→ 用户把频率手动切到「自定义 cron」→ 框里是 **A 的表达式**，保存即把 A 的调度写到 B 上。
- 来源: original（`showBatchConfigEditor` 现有代码只在 custom 分支写这个框，`app.js:13534`）

### Invariant I1-3: 新建任务（config 为 null）的默认不变
- Rule: `showBatchConfigEditor(null)` 时 freq = `daily`、time = `09:00`、cron 框为空。
- Applies to: 同上。
- Violation consequence: 「新增任务」表单默认值变化，运营误建任务。
- 来源: original（现有代码 `var freq = "daily", time = "09:00";` + `if (config && config.cron)` 保护）

---

## 样式契约

**本计划不新增、不修改任何 CSS，也不改任何 DOM 结构。** 改动全部在 `app.js` 的一个函数体内，只写既有元素的 `.value`。

- **S1-1: 无样式改动**
  - 复用：不适用。
  - 新增：无。
  - DOM 结构：`index.html` 零改动。`#batchConfigEditorFrequency`、`#batchConfigEditorTime`、`#batchConfigEditorCronField`、`#batchConfigEditorCron`、`#batchConfigEditorCronPreview` 全部沿用（`index.html:1258-1283`）。
  - 禁止项：任何 `styles.css` 改动；任何 `index.html` 改动；任何 inline style。
  - 验收：`git diff --stat` 中不得出现 `styles.css` 或 `index.html`。

---

## 现状审计

### 前端：`showBatchConfigEditor` 的 cron 反解块

`src/main/resources/static/app.js:13525-13540`（逐字，改动前基线）：

```js
    // Parse cron to frequency + time; anything not matching the three known modes is "custom"
    var freq = "daily", time = "09:00";
    if (config && config.cron) {
        var cronParts = config.cron.trim().split(/\s+/);
        if (cronParts.length >= 5) {
            var hour = cronParts[2], min = cronParts[1], dow = cronParts[5];
            if (hour === "*" || hour === "*/1") { freq = "hourly"; time = ""; }
            else if (dow === "MON" || dow === "TUE" || dow === "WED" || dow === "THU" || dow === "FRI" || dow === "SAT" || dow === "SUN") { freq = "weekly"; time = (hour || "0").padStart(2, "0") + ":" + (min || "0").padStart(2, "0"); }
            else if (!dow || dow === "?" || dow === "*") { freq = "daily"; time = (hour || "0").padStart(2, "0") + ":" + (min || "0").padStart(2, "0"); }
            else { freq = "custom"; setVal("batchConfigEditorCron", config.cron); }
        }
    }
    setVal("batchConfigEditorFrequency", freq);
    setVal("batchConfigEditorTime", time);
    syncBatchConfigEditorScheduleFields();
```

`setVal` 的定义（`app.js:13512`）：

```js
    var setVal = function(id, val) { var el = document.getElementById(id); if (el) el.value = val || ""; };
```

### 静默数据损坏链（逐步推导，输入 `0 0 9-17 * * ?`）

1. `cronParts` = `["0","0","9-17","*","*","?"]`，长度 6 ≥ 5 → 进入判断。
2. `hour = cronParts[2]` = `"9-17"`，`min = cronParts[1]` = `"0"`，`dow = cronParts[5]` = `"?"`。
3. `hour === "*"` 假、`hour === "*/1"` 假 → 不是 hourly。**此处是根因：分支只比较 `hour` 是否为通配，从不校验它是不是"单个小时"。**
4. `dow` 不在周名集合 → 不是 weekly。
5. `dow === "?"` 真 → **freq = "daily"**，`time = "9-17".padStart(2,"0") + ":" + "0".padStart(2,"0")` = `"9-17:00"`（`padStart` 对长度 ≥ 2 的串不补位，原样返回）。
6. `setVal("batchConfigEditorTime", "9-17:00")` → `<input type="time">`（`index.html:1264`）拒绝非法值，`.value` 落为 `""`。
7. 用户看到：频率「每天」，时间空白。**页面上已无任何 `9-17` 的痕迹** —— cron 输入框此路径没被写过，且被 `syncBatchConfigEditorScheduleFields()` 隐藏。
8. 用户点「保存任务」→ `saveBatchConfigEditor`（`app.js:14035-14047`）：
   ```js
   var freq = val("batchConfigEditorFrequency") || "daily";           // "daily"
   var timeParts = (val("batchConfigEditorTime") || "09:00").split(":"); // "" || "09:00" → ["09","00"]
   var hour = parseInt(timeParts[0] || "0", 10);                       // 9
   var min = parseInt(timeParts[1] || "0", 10);                        // 0
   ...
   else cron = "0 " + min + " " + hour + " * * ?";                     // "0 0 9 * * ?"
   ```
9. 落库 cron 从 `0 0 9-17 * * ?`（每天 9-17 点整点，9 次/天）变成 `0 0 9 * * ?`（每天 9 点，1 次/天）。**发送频次静默降为 1/9，无任何提示。**

### 同类被误判为 daily 的表达式（`dow` 为 `?` 或 `*` 且时/分非单值）

| cron | 现在回显 | 应回显 |
|---|---|---|
| `0 0 9-17 * * ?` | 每天（时间空） | 自定义 |
| `0 0 9,12,15 * * ?` | 每天（时间空） | 自定义 |
| `0 */30 9 * * ?` | 每天（时间空） | 自定义 |
| `0 0 9 1 * ?`（每月 1 号） | 每天 09:00 ← **日字段被丢弃** | 自定义 |
| `0 0 9 L * ?` | 每天 09:00 ← **日字段被丢弃** | 自定义 |
| `0 0 9 ? * MON#2` | 自定义（已正确，走 else） | 自定义 |
| `0 0 9 ? * MON-FRI` | 自定义（已正确） | 自定义 |

⚠️ 第 4、5 行比 `9-17` 更隐蔽：`hour="9"` 是单值，`time` 能算出合法的 `09:00`，回显完全"正常"，但 **`日` 字段 `1` / `L` 被静默丢弃**，保存后变成每天执行。

### 读/写路径

- **写**（唯一）：`saveBatchConfigEditor`（`app.js:14035-14047`）→ `POST/PUT /api/mail/batch-send/configs`。本计划**不改**。
- **读**（唯一）：`showBatchConfigEditor(config)`（`app.js:13525`）。本计划只改这里。
- **交互点**：读路径产出的 `#batchConfigEditorFrequency` / `#batchConfigEditorTime` / `#batchConfigEditorCron` 三个 DOM 值，正是写路径的输入。读路径的任何有损反解都会在保存时被写回，且**用户看不到差异**。这是本计划唯一的交互点，A1-1 覆盖它。

### 既有测试

`src/test/js/batchSendTaskConsoleInteraction.test.js`：
- `:452-475` —— custom 模式保存原串（对应 N1-2，必须继续绿）
- `:477-505` —— `0 15 3 * * ?` 回显 daily 03:15（对应 N1-1，必须继续绿）
- 该测试用 `vm.runInContext` + `extractFn` 抽单个函数体执行，sandbox 只 stub 了 `setBatchTagPickerValue` / `setBatchRegionPickerValue` / `syncBatchConfigEditorScheduleFields` / `fillBatchConfigEditorTemplateSelector` / `fillBatchConfigEditorProviderSelect` / `updateBatchConfigVolumeHint`。`scheduleRecipientPreview` 未 stub，靠 `app.js:13541` 的 `typeof ... === "function"` 守卫。**新增用例照抄这套 sandbox，不要改 stub 集合。**

---

## 实现方案

### T1-1 替换 `showBatchConfigEditor` 的 cron 反解块（I1-1 / I1-2 / I1-3 / S1-1）

文件：`src/main/resources/static/app.js`

把 `:13525-13537` 的整块（从 `// Parse cron to frequency + time` 注释到闭合的 `}`）替换为：

```js
    // Cron 回显走白名单反解（I1-1）：只有完全匹配预设格式的表达式才映射到
    // 每小时 / 每天 / 每周一；其余（范围、列表、步长、工作日、月/日限制…）
    // 一律 custom 并原样回填，避免有损反解在保存时把表达式改写掉。
    var freq = "daily", time = "09:00", customCron = "";
    if (config && config.cron) {
        var raw = String(config.cron).trim();
        var m;
        if (/^0 0 (\*|\*\/1) \* \* \?$/.test(raw)) {
            freq = "hourly"; time = "";
        } else if ((m = /^0 (\d{1,2}) (\d{1,2}) \* \* \?$/.exec(raw)) && isCronClock(m[1], m[2])) {
            freq = "daily"; time = padClock(m[2], m[1]);
        } else if ((m = /^0 (\d{1,2}) (\d{1,2}) \? \* MON$/.exec(raw)) && isCronClock(m[1], m[2])) {
            freq = "weekly"; time = padClock(m[2], m[1]);
        } else {
            freq = "custom"; customCron = raw;
        }
    }
    // I1-2：两条路径都显式写值。编辑器 DOM 复用，不清空会把上一条任务的
    // 表达式留在框里，用户切到 custom 时会把别的任务的调度保存进来。
    setVal("batchConfigEditorCron", customCron);
```

并在 `showBatchConfigEditor` **之外**（同文件顶层，紧邻 `showBatchConfigEditor` 定义之前）新增两个纯函数：

```js
/* cron 白名单反解的两个纯 helper（I1-1）。分钟 0-59、小时 0-23 才算合法时钟；
   正则已保证是 1-2 位数字，此处只做范围判定。 */
function isCronClock(minText, hourText) {
    var min = Number(minText), hour = Number(hourText);
    return min >= 0 && min <= 59 && hour >= 0 && hour <= 23;
}

function padClock(hourText, minText) {
    return String(hourText).padStart(2, "0") + ":" + String(minText).padStart(2, "0");
}
```

⚠️ 执行约束：
- `setVal("batchConfigEditorFrequency", freq)` / `setVal("batchConfigEditorTime", time)` / `syncBatchConfigEditorScheduleFields()` 三行**保持原位、原样**（N1-3）。
- `saveBatchConfigEditor` **一个字符都不改**（N1-2）。
- 不要把 helper 定义在 `showBatchConfigEditor` 函数体内 —— 测试用 `extractFn("showBatchConfigEditor")` 只抽该函数体，helper 必须能被单独 `extractFn` 注入 sandbox。

### T1-2 补回归测试（I1-1 / I1-2 / I1-3）

文件：`src/test/js/batchSendTaskConsoleInteraction.test.js`

在 `:505`（现有 daily 回显用例的 `});` 之后）追加用例。sandbox 构造照抄 `:481-497`，并把 `isCronClock` / `padClock` 一并 `vm.runInContext` 注入。

必须包含的断言：

| 用例 | 输入 cron | 断言 |
|---|---|---|
| U1 | `0 0 9-17 * * ?` | `frequency === "custom"` **且** `batchConfigEditorCron.value === "0 0 9-17 * * ?"` |
| U2 | `0 0 9,12,15 * * ?` | 同上形态，cron 原串不变 |
| U3 | `0 0 9 1 * ?` | `frequency === "custom"`，cron 原串不变（防「日字段被丢弃」回归） |
| U4 | `0 0 9 ? * MON-FRI` | `frequency === "custom"`，cron 原串不变 |
| U5 | `0 0 * * * ?` | `frequency === "hourly"`，`time === ""`，`cron.value === ""` |
| U6 | `0 15 3 * * ?` | `frequency === "daily"`，`time === "03:15"`，`cron.value === ""` |
| U7 | `0 30 9 ? * MON` | `frequency === "weekly"`，`time === "09:30"`，`cron.value === ""` |
| U8 | `0 70 9 * * ?`（分钟越界） | `frequency === "custom"`（`isCronClock` 拦下） |
| U9 | 连续两次调用：先 `0 0 9-17 * * ?` 再 `0 15 3 * * ?`（复用同一 elements 对象） | 第二次后 `cron.value === ""`（I1-2 的 DOM 复用清空） |
| U10 | `showBatchConfigEditor(null)` | `frequency === "daily"`，`time === "09:00"`，`cron.value === ""`（I1-3） |
| U11 | 保存链路：`frequency = "custom"` + `cron = "0 0 9-17 * * ?"` → `saveBatchConfigEditor()` | `apiBodies[0].cron === "0 0 9-17 * * ?"`（沿用 `:452-475` 的 saveConfig sandbox 写法） |

---

## 变更文件清单

| # | 文件 | 类型 | 改动 |
|---|---|---|---|
| 1 | `src/main/resources/static/app.js` | 修改 | 替换 `:13525-13537` 反解块；顶层新增 `isCronClock` / `padClock` 两个纯函数 |
| 2 | `src/test/js/batchSendTaskConsoleInteraction.test.js` | 修改 | 在 `:505` 后追加 U1-U11 共 11 条用例 |

文件数：**2**（≤10 ✅）  子系统数：**1**（≤2 ✅）

**不改**：`index.html`、`styles.css`、任何 `.kt`、任何 `.sql`。

---

## 验证命令

> 本项目**必须**用 JDK 11（zulu-11）。裸 `mvn` 会构建失败。
> 本计划只动前端，日常迭代用 `node --test` 即可；合并前仍须跑一次全量。

```bash
# 本计划相关测试（迭代用，秒级）
node --test src/test/js/batchSendTaskConsoleInteraction.test.js

# 全部 JS 测试
node --test src/test/js/*.test.js

# 全量测试（回归门禁，含 Kotlin + JS）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：
- `node --test`：退出码 0，输出末尾 `fail 0`。
- Maven：退出码 0，`Tests run: N, Failures: 0, Errors: 0` 且 `BUILD SUCCESS`。
- `git diff --check`：无输出。

来源：`CLAUDE.md`「Commands」章节；JS 命令取自 `pom.xml:199` 的 `exec-maven-plugin` argument 原串。

---

## 验收标准

- **I1-1**：`node --test src/test/js/batchSendTaskConsoleInteraction.test.js` 中 U1-U8 全绿。另 `grep -n 'dow === "?"' src/main/resources/static/app.js` 无输出（旧黑名单判断已删净）。
- **I1-2**：U9 绿。另 `grep -n 'setVal("batchConfigEditorCron"' src/main/resources/static/app.js` 恰好 **1** 处命中（合并到单一无条件写入点，不再是 custom 分支专属）。
- **I1-3**：U10 绿。
- **S1-1**：`git diff --stat` 输出中不含 `styles.css`、不含 `index.html`；`git diff src/main/resources/static/app.js | grep -c 'style='` 为 0。
- **N1-2**：`git diff src/main/resources/static/app.js` 的 diff hunk 不覆盖 `saveBatchConfigEditor` 函数体；既有用例 `:452-475`、`:477-505` 继续绿。
- **N1-4**：`git diff --stat` 不含任何 `.kt` / `.sql` / `.yml`。
- 回归：执行「验证命令」节的全量测试命令通过。

---

## 人工验收清单

### A1-1: 范围型 cron 打开后保存不被改写（核心场景 + 交互点）
- 前置条件：库中存在一条定时任务，cron = `0 0 9-17 * * ?`。构造方式二选一：
  - 界面：新增任务 → 频率选「自定义 cron」→ 填 `0 0 9-17 * * ?` → 保存；
  - SQL：`UPDATE batch_send_task_config SET cron='0 0 9-17 * * ?' WHERE config_name='<任务名>';`
- 操作步骤：
  1. 打开「批量邮件任务控制台」→「定时任务」→ 点该任务的「编辑」。
  2. 观察「执行频率」下拉与其下方字段。
  3. 不做任何修改，点「保存任务」。
  4. 再次点「编辑」打开同一任务。
- 预期结果：
  - 第 2 步：「执行频率」= **自定义 cron**；下方出现 cron 输入框，内容为 `0 0 9-17 * * ?`；**没有**「执行时间」时间选择器。
  - 第 3 步：保存成功提示。
  - 第 4 步：cron 输入框仍为 `0 0 9-17 * * ?`，频率仍为「自定义 cron」。
- 覆盖：O-4、I1-1、现状审计的交互点

### A1-2: 月/日限制型 cron 不再丢字段
- 前置条件：一条 cron = `0 0 9 1 * ?`（每月 1 号 09:00）的任务。
- 操作步骤：编辑该任务 → 观察频率 → 直接保存 → 重新打开。
- 预期结果：频率为「自定义 cron」，输入框为 `0 0 9 1 * ?`；保存后重新打开仍是 `0 0 9 1 * ?`（**不是** `0 0 9 * * ?`）。
- 覆盖：I1-1

### A1-3: 回归 —— 三种预设频率回显不变
- 前置条件：三条任务，cron 分别为 `0 0 * * * ?`、`0 15 3 * * ?`、`0 30 9 ? * MON`。
- 操作步骤：依次编辑这三条，观察「执行频率」与「执行时间」。
- 预期结果：分别为「每小时」（无时间选择器）、「每天 + 03:15」、「每周一 + 09:30」。三者均**不**显示 cron 输入框。
- 覆盖：N1-1

### A1-4: 回归 —— cron 输入框在任务间不串值
- 前置条件：任务 A 的 cron = `0 0 9-17 * * ?`；任务 B 的 cron = `0 15 3 * * ?`。
- 操作步骤：
  1. 编辑任务 A，确认 cron 框为 `0 0 9-17 * * ?`。
  2. 点「取消」。
  3. 编辑任务 B（频率应显示「每天 03:15」）。
  4. 把「执行频率」手动切到「自定义 cron」。
- 预期结果：第 4 步出现的 cron 输入框为**空**，不是 `0 0 9-17 * * ?`。
- 覆盖：I1-2

### A1-5: 回归 —— 新增任务默认值
- 前置条件：无。
- 操作步骤：「定时任务」→「新增任务」。
- 预期结果：「执行频率」= 每天，「执行时间」= 09:00，无 cron 输入框。
- 覆盖：I1-3

### A1-6: 回归 —— 自定义模式保存原串
- 前置条件：无。
- 操作步骤：新增任务 → 填名称 → 频率选「自定义 cron」→ 输入 `0 0 9 ? * MON#2` → 保存 → 重新打开。
- 预期结果：保存成功；重新打开后频率为「自定义 cron」，输入框为 `0 0 9 ? * MON#2`（**未**被重新拼装成 `0 0 9 ? * MON`）。
- 覆盖：N1-2

### A1-7: UI 目测 —— 无样式变化
- 前置条件：无。
- 操作步骤：并排对照改动前后的定时任务编辑器「定时调度」区域。
- 预期结果：字段位置、间距、下拉宽度、cron 输入框与「测试」按钮的排布完全一致；无新增视觉元素。
- 覆盖：S1-1、N1-3
