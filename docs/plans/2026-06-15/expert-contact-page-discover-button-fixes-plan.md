# 专家联系页面：发现专家按钮组三项修复 — 开发计划

> 本计划交给执行 agent 实施。行号基于 2026-06-15 代码，可能有少量漂移，请以符号名定位。
> 不涉及数据库迁移、不涉及 API 路径变更。

---

## 一、需求描述

### 需求 1：重新验证按钮合并到发现专家下拉菜单

当前工具栏有独立的"重新验证"按钮（`index.html:354`），与"发现专家"分列按钮并排。从功能归属看，"重新验证候选人"与"快速扫描"/"深度发现"属于同类操作（都是专家池维护），应合并到"发现专家"下拉菜单中作为第三个选项。

### 需求 2：发现专家执行中状态刷新页面后无法恢复

用户启动"发现专家（快速）"或"深度发现"任务后刷新页面，按钮未恢复"执行中"状态，进度弹窗也不会自动打开。期望刷新后自动恢复到执行中视图。

### 需求 3：快速扫描日志行只显示总处理数，缺少总个数

"发现专家（快速）"任务的完成消息和终态进度日志只显示实际处理的专家数量（如 `总数 1000`），但不显示 RAW 层的总文档数（如 50000），用户无法知道此次扫描覆盖了多少比例。

---

## 二、现状分析

### 2.1 按钮布局与映射关系

工具栏 HTML（`index.html:344-356`）：
- `discoverBtnGroup` — 分列按钮，主按钮 `discoverBtn`（"发现专家"），下拉有两项：快速扫描、深度发现
- `revalidateBtn` — 独立按钮，"重新验证"

JS 映射（`app.js:193-206`）：
```js
taskButtonOriginalTexts = {
    revalidateBtn: "重新验证",      // 独立 btnId
    discoverBtn: "发现专家",         // 快速 + 深度共用
    ...
};
taskButtonMapping = {
    EXPERT_REVALIDATION: { label: "重新验证", btnId: "revalidateBtn" },
    RAW_PROMOTION_SCAN:  { label: "发现专家（快速）", btnId: "discoverBtn" },
    EXPERT_DISCOVERY:    { label: "发现专家（深度）", btnId: "discoverBtn" },
    ...
};
```

`taskLaunchConfigs`（`app.js:1698-1732`）中三个任务类型的 `btnId` 也分别指向对应按钮。

`handleDiscoverClick()`（`app.js:1952`）默认只检查 `RAW_PROMOTION_SCAN` 和 `EXPERT_DISCOVERY` 的运行状态，不检查 `EXPERT_REVALIDATION`。

### 2.2 页面刷新恢复机制

`resumeProgressPollingIfNeeded()`（`app.js:236`）在 `startAuthenticatedApp()`（`app.js:4709`）和 `setView("contacts")`（`app.js:961`）时调用，遍历 `taskButtonMapping` 所有任务类型，通过 `GET /api/task-progress/{taskType}` 检查状态：

```js
if (progress.status === "RUNNING" || progress.status === "CANCELLING") {
    const mapping = taskButtonMapping[taskType];
    if (mapping) setTaskButtonRunning(mapping.btnId);
    startTaskWatcher(taskType);
}
```

**问题 1**：该函数仅恢复按钮文字为"执行中"并启动后台 watcher，但不会自动打开进度弹窗。用户刷新后只看到按钮状态变了，需手动点击才能看到进度。

**问题 2**：`setView()` 函数（`app.js:963`）中切换离开 contacts 视图时调用了 `stopProgressPollingFor`，但该函数**未定义**，会抛 `TypeError`：
```js
} else {
    ["EXPERT_REVALIDATION", "RAW_PROMOTION_SCAN", "EXPERT_DISCOVERY"].forEach(stopProgressPollingFor);
}
```

### 2.3 快速扫描进度的 totalCount 问题

后端 `ExpertRevalidationService.promoteEligibleRawExperts()`（`ExpertRevalidationService.kt:130-240`）：

- **执行中批次进度**：`totalCount = totalHits`（ES RAW 层总文档数）—— 正确，批次日志表的"累计进度"列能正常显示如 `500/50000`
- **终态（COMPLETED/CANCELLED）进度**：`totalCount = stats.total.toLong()`（实际处理数）—— **丢失了 ES 总数**
  ```kotlin
  // COMPLETED 状态（ExpertRevalidationService.kt:222-227）
  progressStore.update(taskType, TaskProgress(
      ...
      processedCount = stats.total.toLong(), totalCount = stats.total.toLong(), // ← 两者相同
      message = "完成: 晋升 ${stats.promoted}, 过滤 ${stats.filtered}",
      ...
  ))
  ```
- `PromotionScanStats`（`ExpertRevalidationDomain.kt:30-38`）没有 `totalHits` 字段，`totalHits` 仅在 `scrollExperts` 回调的局部参数中存在

前端完成通知（`app.js:1996`）：
```js
message: `发现专家（快速）完成: 总数 ${stats.total}, 晋升 ${stats.promoted}, 过滤 ${stats.filtered}, ...`
```
这里 `stats.total` 就是已处理数，RAW 层总文档数在整个 response 链路中不可见。

---

## 三、实现方案

### 修复 1：重新验证按钮合并到发现专家下拉菜单

#### 3.1.1 `index.html` — 按钮布局

**删除** 独立按钮（第 354 行）：
```html
<!-- 删除这一行 -->
<button class="button" id="revalidateBtn" onclick="handleRevalidateCandidates()">重新验证</button>
```

**在 `discoverModeMenu` 内新增第三项**（第 351 行之后）：
```html
<hr class="dropdown-divider">
<button class="dropdown-item" onclick="handleDiscoverOption('revalidate')">重新验证候选人</button>
```

用 `<hr class="dropdown-divider">` 做视觉分隔，因为"重新验证"的方向（降级不合格）与前两项（晋升合格）相反。

#### 3.1.2 `app.js` — 映射配置

`taskButtonOriginalTexts`（~L193）：**删除** `revalidateBtn` 条目。

`taskButtonMapping`（~L201）：
```js
EXPERT_REVALIDATION: { label: "重新验证", btnId: "discoverBtn" },  // revalidateBtn → discoverBtn
```

`taskLaunchConfigs.EXPERT_REVALIDATION`（~L1698）：
```js
btnId: "discoverBtn",  // revalidateBtn → discoverBtn
```

#### 3.1.3 `app.js` — 处理函数

`handleDiscoverOption(mode)`（~L1961）增加 `'revalidate'` 分支：
```js
async function handleDiscoverOption(mode) {
    if (mode === 'quick') {
        await handlePromoteRaw();
    } else if (mode === 'revalidate') {
        await handleRevalidateCandidates();
    } else {
        await handleDiscover();
    }
}
```

`handleRevalidateCandidates()`（~L1883）和 `executeRevalidate()`（~L1893）中所有 `"revalidateBtn"` 替换为 `"discoverBtn"`。

`handleDiscoverClick()`（~L1952）增加 `EXPERT_REVALIDATION` 运行检测（放在最前面，因为合并后三个任务共用 discoverBtn）：
```js
async function handleDiscoverClick() {
    const runningRevalidate = await isTaskRunning("EXPERT_REVALIDATION");
    if (runningRevalidate) {
        openTaskModal("EXPERT_REVALIDATION", "重新验证候选人", "discoverBtn", { knownActiveAtOpen: true });
        return;
    }
    const runningQuick = await isTaskRunning("RAW_PROMOTION_SCAN");
    if (runningQuick) {
        openTaskModal("RAW_PROMOTION_SCAN", "发现专家（快速）", "discoverBtn", { knownActiveAtOpen: true });
        return;
    }
    await handleDiscover();
}
```

#### 3.1.4 `styles.css` — 下拉分隔线

如果 `.dropdown-divider` 样式不存在，新增：
```css
.dropdown-divider {
    border: none;
    border-top: 1px solid var(--panel-border);
    margin: 4px 0;
}
```

检查是否有 `#revalidateBtn` 专属样式，如有则清理。

---

### 修复 2：发现专家执行中状态刷新页面后恢复

#### 3.2.1 `app.js` — `resumeProgressPollingIfNeeded` 增加自动打开弹窗

检测到运行中任务时，如果当前在 contacts 视图且没有已打开的弹窗，自动打开进度弹窗：

```js
async function resumeProgressPollingIfNeeded() {
    let firstRunningTask = null;
    for (const taskType of Object.keys(taskButtonMapping)) {
        try {
            const response = await fetch(`${contextPath}/api/task-progress/${taskType}`);
            await handleAuthResponse(response);
            if (response.status === 204 || !response.ok) continue;
            const progress = await response.json();
            if (progress.status === "RUNNING" || progress.status === "CANCELLING") {
                const mapping = taskButtonMapping[taskType];
                if (mapping) setTaskButtonRunning(mapping.btnId);
                startTaskWatcher(taskType);
                if (!firstRunningTask) {
                    firstRunningTask = { taskType, mapping };
                }
            }
        } catch (e) { /* 静默 */ }
    }
    // 自动恢复进度弹窗（仅 contacts 视图、无已打开弹窗时）
    if (firstRunningTask && state.view === "contacts" && !currentTaskModal) {
        const { taskType, mapping } = firstRunningTask;
        openTaskModal(taskType, mapping.label, mapping.btnId, { knownActiveAtOpen: true });
    }
}
```

#### 3.2.2 `app.js` — 修复 `stopProgressPollingFor` 未定义

`setView()` 函数（~L963），将未定义的 `stopProgressPollingFor` 替换为已有的 `stopTaskWatcher`：

```js
// 原来（会抛 TypeError）
["EXPERT_REVALIDATION", "RAW_PROMOTION_SCAN", "EXPERT_DISCOVERY"].forEach(stopProgressPollingFor);

// 改为
["EXPERT_REVALIDATION", "RAW_PROMOTION_SCAN", "EXPERT_DISCOVERY"].forEach(t => stopTaskWatcher(t, true));
```

---

### 修复 3：快速扫描日志行补充总个数

#### 3.3.1 `ExpertRevalidationDomain.kt` — `PromotionScanStats` 增加字段

```kotlin
data class PromotionScanStats(
    var total: Int = 0,
    var totalHits: Long = 0,  // 新增：RAW 层 ES 总文档数
    var promoted: Int = 0,
    var filtered: Int = 0,
    var emailRejected: Int = 0,
    var alreadyPromoted: Int = 0,
    var promotionFailed: Int = 0,
    var existenceCheckFailed: Int = 0,
    val filterReasons: MutableMap<String, Int> = mutableMapOf()
)
```

#### 3.3.2 `ExpertRevalidationService.kt` — 记录 totalHits 并修正终态进度

`promoteEligibleRawExperts()` 中 `scrollExperts` 回调第一行记录 totalHits：
```kotlin
expertSearchService.scrollExperts(ExpertIndexLevel.RAW) { batch, batchNumber, totalHits ->
    stats.totalHits = totalHits  // 新增：每个批次都会传入相同的 totalHits
    ...
}
```

**COMPLETED 终态**（~L222-227）：
```kotlin
progressStore.update(taskType, TaskProgress(
    taskType = taskType, status = "COMPLETED",
    batchNumber = -1,
    processedCount = stats.total.toLong(),
    totalCount = stats.totalHits,  // 原来: stats.total.toLong()
    message = "完成: 已处理 ${stats.total}/${stats.totalHits}, 晋升 ${stats.promoted}, 过滤 ${stats.filtered}",
    details = mapOf("promoted" to stats.promoted, "filtered" to stats.filtered, "emailRejected" to stats.emailRejected, "filterReasons" to stats.filterReasons)
), execId)
```

**CANCELLED 终态**（~L213-218）同理：
```kotlin
processedCount = stats.total.toLong(),
totalCount = stats.totalHits,  // 原来: stats.total.toLong()
message = "已取消: 已处理 ${stats.total}/${stats.totalHits}, 晋升 ${stats.promoted}, 过滤 ${stats.filtered}",
```

**FAILED 终态**（~L229-233）：
```kotlin
processedCount = stats.total.toLong(),
totalCount = stats.totalHits,  // 原来: 0
message = "失败: 已处理 ${stats.total}/${stats.totalHits}, ${e.message}"
```

#### 3.3.3 `app.js` — 前端完成通知补充总数

`executePromoteRaw()`（~L1996）的完成消息：
```js
// 原来
message: `发现专家（快速）完成: 总数 ${stats.total}, 晋升 ${stats.promoted}, 过滤 ${stats.filtered}, 邮箱拒收 ${stats.emailRejected}${failureMsg}`,

// 改为
const totalHits = stats.totalHits || stats.total;
const coverageMsg = totalHits > stats.total ? `已处理 ${stats.total}/${totalHits}` : `总数 ${stats.total}`;
message: `发现专家（快速）完成: ${coverageMsg}, 晋升 ${stats.promoted}, 过滤 ${stats.filtered}, 邮箱拒收 ${stats.emailRejected}${failureMsg}`,
```

---

## 四、影响范围

| 文件 | 涉及修复 | 改动类型 |
|------|----------|----------|
| `src/main/resources/static/index.html` | #1 | 删按钮、加下拉项 |
| `src/main/resources/static/app.js` | #1, #2, #3 | 映射配置、处理函数、恢复逻辑 |
| `src/main/resources/static/styles.css` | #1 | 下拉分隔线样式（如不存在） |
| `src/main/kotlin/.../expert/domain/ExpertRevalidationDomain.kt` | #3 | `PromotionScanStats` 加字段 |
| `src/main/kotlin/.../expert/service/ExpertRevalidationService.kt` | #3 | 终态进度 totalCount 修正 |

**不涉及**：数据库迁移、API 路径变更、新增控制器。

## 五、验证要点

1. **修复 1 验证**：工具栏无独立"重新验证"按钮；下拉菜单含三项（快速扫描、深度发现、重新验证候选人）且分隔线正常；三个任务执行中共用 discoverBtn 显示"执行中"；运行中点击按钮能正确打开对应任务的进度弹窗。
2. **修复 2 验证**：启动任意发现专家任务 → 刷新页面 → 按钮显示"执行中"且进度弹窗自动打开；切换到其他视图再切回 contacts 时行为一致；`setView` 切换离开 contacts 视图时不再抛 TypeError。
3. **修复 3 验证**：启动快速扫描（设 maxPromotions 较小如 10）→ 完成消息显示 `已处理 10/50000`（实际处理数/RAW 总数）；进度弹窗中批次日志"累计进度"列格式不变；终态日志进度百分比正确。

## 修正记录

| 原要求 | 修正后要求 | 原因 | 参考 |
|---|---|---|---|
| 自动恢复进度弹窗仅限 `state.view === "contacts"` | 首次认证启动恢复时，只要检测到三类发现专家任务正在运行且当前无任务弹窗，就允许打开进度弹窗；从其他视图进入 contacts 的现有恢复行为保持不变 | 整页刷新后 `state.view` 固定重置为 `accounts`，原条件导致刷新验收目标不可达 | `docs/plans/fix/2026-06-15-expert-contact-page-discover-button-fixes-plan/fix-1.md` |
