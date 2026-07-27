# Task Watcher 启动阶段 204 竞态修复计划

> 目标：修复任务启动请求尚未建立 `TaskProgress` 时关闭进度弹窗，后台 watcher 首次轮询收到 HTTP 204 后过早停止，导致外部按钮错误恢复、任务后续运行无人监控的问题。
>
> 本计划只处理该竞态。不要顺带重构两层日志、任务执行汇总接口、`TaskProgressStore` token 机制或其他业务模块。

---

## 一、问题描述

### 1.1 复现步骤

1. 点击以下任一任务的“开始执行”：
   - `EXPERT_REVALIDATION`
   - `RAW_PROMOTION_SCAN`
   - `EXPERT_DISCOVERY`
2. 前端执行 `openTaskModal(..., { launchRequested: true })`。
3. POST 启动请求尚未完成，后端尚未通过 `tryStartWithToken` 建立可查询进度。
4. 用户立即关闭进度弹窗。
5. `closeTaskModal()` 判断此次启动仍可能处于活动状态，调用 `startTaskWatcher(taskType)`。
6. watcher 立即调用 `GET /api/task-progress/{taskType}`。
7. 接口暂时返回 HTTP 204。
8. 当前 `pollTaskWatcher()` 将 204 当作“任务已经结束”，执行：
   - `stopTaskWatcher(taskType, true)`
   - 清除 watcher
   - 恢复外部按钮
9. POST 随后成功，任务进入 RUNNING，但已经没有 watcher：
   - 外部按钮不再保持“执行中”
   - 任务结束后也没有后台恢复/通知路径
   - 用户可能误以为任务未启动，并再次点击

### 1.2 根因

文件：`src/main/resources/static/app.js`

当前逻辑：

```js
async function pollTaskWatcher(taskType) {
    const response = await fetch(`${contextPath}/api/task-progress/${taskType}`);
    if (response.status === 204) {
        stopTaskWatcher(taskType, true);
        return;
    }
    // ...
}
```

204 有两种语义，当前代码无法区分：

1. 任务确实不存在或已被清理，可以停止 watcher。
2. 启动请求仍在飞行，进度尚未建立，只是暂时查不到，必须继续等待。

`closeTaskModal()` 已通过 `launchRequested` 表达“启动请求已发出但可能尚未绑定 executionId”，但该上下文在创建 watcher 时丢失。

---

## 二、修复目标

1. 从启动流程关闭弹窗后，watcher 在有限宽限期内遇到 204 不得停止。
2. 宽限期内一旦观察到 RUNNING/CANCELLING，切换为正常活动任务监控。
3. 宽限期内一旦观察到终态，恢复按钮、停止 watcher、只通知一次。
4. 宽限期耗尽仍持续 204，停止 watcher并恢复按钮，避免永久定时器。
5. 对“刷新页面恢复 watcher”和“查看已有运行任务后关闭弹窗”的现有行为无回归。
6. 同一 taskType 始终最多一个 watcher。
7. 网络错误和非 2xx 响应继续保留 watcher，等待下轮重试。
8. 不修改后端接口，不修改 `TaskProgressStore`。

---

## 三、设计方案

### 3.1 将 watcher 从 intervalId 升级为状态对象

文件：`src/main/resources/static/app.js`

当前：

```js
const taskWatchers = {}; // taskType -> intervalId
```

修改为：

```js
const taskWatchers = {}; // taskType -> watcher state
```

建议状态结构：

```js
{
    intervalId: null,
    awaitingLaunch: false,
    startedAt: Date.now(),
    noProgressCount: 0,
    observedActive: false
}
```

字段语义：

- `awaitingLaunch`：watcher 是否由“启动 POST 尚未完成时关闭弹窗”创建。
- `startedAt`：宽限期起点。
- `noProgressCount`：连续收到 204 的次数。
- `observedActive`：是否已经观察到 RUNNING/CANCELLING。
- `intervalId`：3 秒轮询定时器。

不要仅保存 intervalId，否则无法正确解释 204。

### 3.2 扩展 `startTaskWatcher`

建议签名：

```js
function startTaskWatcher(taskType, options = {})
```

支持：

```js
startTaskWatcher(taskType, {
    awaitingLaunch: true
});
```

单例规则：

1. watcher 不存在：创建状态并开始轮询。
2. watcher 已存在：
   - 不创建第二个 interval。
   - 若新调用传入 `awaitingLaunch: true`，可把已有状态升级为 awaitingLaunch。
3. 创建后立即执行一次 `pollTaskWatcher(taskType)`，并启动 3 秒 interval。

建议常量：

```js
const TASK_WATCHER_INTERVAL_MS = 3000;
const TASK_WATCHER_LAUNCH_GRACE_MS = 30000;
const TASK_WATCHER_MAX_INITIAL_204 = 10;
```

宽限期使用“时间 + 次数”双限制：

- 时间达到 30 秒，或
- 连续 204 达到 10 次

任一满足即可结束等待。这样测试可控制次数，生产环境也有明确上限。

### 3.3 修改 `closeTaskModal` 接线

关闭前先从 modal 上保存判断结果：

```js
const awaitingLaunch = currentTaskModal.mode === "PROGRESS"
    && currentTaskModal.launchRequested
    && currentTaskModal.lastProgressStatus == null
    && currentTaskModal.executionId == null;
```

调用 watcher：

```js
if (shouldWatch) {
    startTaskWatcher(taskType, { awaitingLaunch });
}
```

区分以下场景：

| 场景 | awaitingLaunch |
|---|---:|
| 点击开始后 POST 未返回，立即关闭 | true |
| 已观察到 RUNNING 后关闭 | false |
| 通过 `isTaskRunning` 打开已有任务后立即关闭 | false |
| CONFIG 弹窗关闭 | 不启动 watcher |
| 已观察到终态后关闭 | 不启动 watcher |

不要把所有 watcher 都设置为 awaitingLaunch，否则普通 204 会被无意义延迟 30 秒。

### 3.4 修改 `pollTaskWatcher` 的 204 处理

建议伪代码：

```js
async function pollTaskWatcher(taskType) {
    const watcher = taskWatchers[taskType];
    if (!watcher) return;

    try {
        const response = await fetch(`${contextPath}/api/task-progress/${taskType}`);

        if (response.status === 204) {
            const current = taskWatchers[taskType];
            if (!current) return;

            current.noProgressCount += 1;

            const graceExpired =
                Date.now() - current.startedAt >= TASK_WATCHER_LAUNCH_GRACE_MS
                || current.noProgressCount >= TASK_WATCHER_MAX_INITIAL_204;

            if (current.awaitingLaunch && !current.observedActive && !graceExpired) {
                return;
            }

            stopTaskWatcher(taskType, true);
            return;
        }

        if (!response.ok) return;

        const progress = await response.json();
        const current = taskWatchers[taskType];
        if (!current) return;

        current.noProgressCount = 0;

        if (progress.status === "RUNNING" || progress.status === "CANCELLING") {
            current.awaitingLaunch = false;
            current.observedActive = true;
            return;
        }

        if (isProgressTerminal(progress.status)) {
            stopTaskWatcher(taskType, true);
            // 保留现有 notifyTaskCompletionOnce 调用
        }
    } catch (e) {
        // 网络抖动：保留 watcher，下轮重试
    }
}
```

关键要求：

1. 每次 `await fetch` 后重新读取 `taskWatchers[taskType]`，防止请求期间 watcher 被弹窗重新接管或删除。
2. RUNNING/CANCELLING 必须把 `awaitingLaunch=false`，后续若接口返回 204，应按异常消失处理并停止 watcher。
3. 非 204 的 4xx/5xx 不增加 `noProgressCount`，避免后端短暂故障消耗启动宽限期。
4. JSON 解析失败按网络抖动处理，保留 watcher。
5. 终态通知继续走 `notifyTaskCompletionOnce`，不要新增第二套去重逻辑。

### 3.5 修改 `stopTaskWatcher`

当前清理逻辑假定 map 值是 intervalId。改为读取状态对象：

```js
function stopTaskWatcher(taskType, restoreButton) {
    const watcher = taskWatchers[taskType];
    if (watcher) {
        if (watcher.intervalId) {
            clearInterval(watcher.intervalId);
        }
        delete taskWatchers[taskType];
    }
    if (restoreButton) {
        const mapping = taskButtonMapping[taskType];
        if (mapping) restoreTaskButton(mapping.btnId);
    }
}
```

确保以下调用仍兼容：

- `openTaskModal()`：`stopTaskWatcher(taskType, false)`
- `updateTaskModalFromProgress()`：终态时停止 watcher但避免重复恢复
- 三个 execute catch 分支：`stopTaskWatcher(taskType, true)`
- `resumeProgressPollingIfNeeded()`：创建普通 watcher

### 3.6 POST 完成后的补强

三个启动函数已有：

```js
await bindTaskModalExecution(taskType, capturedGeneration, response.executionId);
```

当弹窗已关闭时，绑定会返回 false。建议在 POST 成功后补充：

```js
if (!isCurrentTaskModal(taskType, capturedGeneration)) {
    const watcher = taskWatchers[taskType];
    if (watcher) {
        watcher.awaitingLaunch = false;
        watcher.observedActive = true;
        watcher.noProgressCount = 0;
    } else {
        startTaskWatcher(taskType);
    }
}
```

应抽成顶层函数，避免三处复制：

```js
function markTaskWatcherLaunchSucceeded(taskType)
```

三个接线点：

- `executeRevalidate`
- `executePromoteRaw`
- `executeDiscover`

作用：

1. POST 成功即确认任务确实创建，不再把后续 204 当“尚未启动”。
2. 如果 watcher 因其他时序不存在，重新启动普通 watcher。
3. 弹窗仍开着时不额外创建 watcher，由弹窗轮询接管。

注意：不要在 POST 返回后立即停止 watcher。用户已关闭弹窗时 watcher 必须继续监控终态。

### 3.7 POST 失败处理

POST 失败时现有 catch 会调用：

```js
stopTaskWatcher(taskType, true);
```

保留该行为，确保 awaitingLaunch watcher 被清理并恢复按钮。

`e.message.includes("正在执行中")` 当前提前 return，必须额外检查：

```js
stopTaskWatcher(taskType, true);
```

否则启动冲突场景可能遗留 awaitingLaunch watcher。三个 execute 函数均需核对。

---

## 四、测试计划

### 4.1 扩展 JS 状态机测试

文件：

`src/test/js/taskModalStateMachine.test.js`

至少新增以下测试：

#### Case 1：启动阶段首次 204 不停止 watcher

- 创建 `awaitingLaunch=true` watcher。
- 第一次 fetch 返回 204。
- 断言 watcher 仍存在。
- 断言未调用 `restoreTaskButton`。

#### Case 2：多个 204 后观察到 RUNNING

- 前两次返回 204。
- 第三次返回 RUNNING。
- 断言 watcher 仍存在。
- 断言：
  - `awaitingLaunch=false`
  - `observedActive=true`
  - `noProgressCount=0`

#### Case 3：RUNNING 后再收到 204

- 先观察 RUNNING。
- 下一次返回 204。
- 断言 watcher 被停止。
- 断言按钮恢复一次。

#### Case 4：启动宽限期耗尽

- 固定 `Date.now()` 或将计数推进到最大值。
- 始终返回 204。
- 断言 watcher 最终停止。
- 断言按钮恢复一次。
- 断言没有完成通知。

#### Case 5：启动阶段直接观察到终态

- 第一次 204。
- 第二次返回 COMPLETED/CANCELLED。
- 断言 watcher 停止。
- 断言按钮恢复。
- 断言通知仅一次。

#### Case 6：非 awaitingLaunch watcher 收到 204

- `startTaskWatcher(taskType)`，不传 awaitingLaunch。
- fetch 返回 204。
- 断言立即停止并恢复按钮。

#### Case 7：重复 start 不创建多个 interval

- 同一 taskType 调用两次。
- 断言 `setInterval` 只调用一次。
- 第二次传 awaitingLaunch=true 时，已有状态被正确升级。

#### Case 8：fetch 期间 watcher 被删除

- 发起未完成 fetch。
- 调用 `stopTaskWatcher`。
- fetch 后返回 RUNNING 或终态。
- 断言不会重新创建 watcher、不会重复恢复按钮、不会通知。

#### Case 9：POST 成功时弹窗已关闭

- 创建 awaitingLaunch watcher。
- 模拟 `markTaskWatcherLaunchSucceeded(taskType)`。
- 断言 watcher 转为 observedActive。
- 断言后续正常监控终态。

#### Case 10：POST 失败清理 awaitingLaunch watcher

- watcher 已创建。
- 执行失败分支。
- 断言 watcher 删除、按钮恢复。

### 4.2 扩展生命周期集成测试

文件：

`src/test/js/taskModalLifecycleIntegration.test.js`

新增完整时序测试：

```text
handle/execute 启动
→ openTaskModal(launchRequested=true)
→ 立即 closeTaskModal
→ watcher 第一次 GET 返回 204
→ POST 成功
→ watcher GET 返回 RUNNING
→ watcher GET 返回 CANCELLED/COMPLETED
→ 按钮只恢复一次
→ watcher 被清理
→ 完成通知只出现一次
```

该测试必须调用真实提取出的函数，不要只断言源码包含某段字符串。

### 4.3 保留现有回归测试

必须继续通过：

- 关闭已知 RUNNING 弹窗后 watcher 接管。
- CONFIG 弹窗关闭不启动 watcher。
- 终态弹窗关闭不启动 watcher。
- stale generation 响应不污染新弹窗。
- completion notification 去重。
- 两层执行日志渲染测试。

---

## 五、验收命令

使用 JDK 11：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
mvn -Dtest=TaskProgressControllerExecutionsTest,TaskProgressControllerTest,ExpertIndexControllerTest,ExpertIndexControllerMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoveryControllerMvcTest test
```

JS 全套：

```bash
node --test src/test/js/*.test.js
```

语法检查：

```bash
node --check src/main/resources/static/app.js
node --check src/main/resources/static/task-modal-runtime.js
```

完整 Maven 验证：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

代码卫生：

```bash
git diff --check
```

注意：Node 25 下 `node --test src/test/js/` 会把目录当模块解析并失败。使用 `node --test src/test/js/*.test.js`。

---

## 六、人工验收

### 场景 A：立即关闭

1. 点击“开始执行”。
2. 弹窗出现后立即关闭。
3. 确认外部按钮保持“执行中”且可点击查看。
4. 任务结束后确认按钮恢复原文案。
5. 确认完成提示只出现一次。

### 场景 B：取消后立即关闭

1. 启动任务。
2. 点击“取消任务”。
3. 在状态变为 CANCELLED 前立即关闭弹窗。
4. 确认按钮仍显示“执行中”。
5. 后端进入 CANCELLED 后确认按钮自动恢复。
6. 再次点击按钮可正常打开启动配置。

### 场景 C：启动失败

1. 模拟启动接口返回 409/500。
2. 确认 watcher 被清理。
3. 确认按钮恢复。
4. 确认没有残留 3 秒轮询。

### 场景 D：刷新恢复

1. 任务运行中刷新页面。
2. `resumeProgressPollingIfNeeded()` 识别 RUNNING。
3. 确认按钮显示“执行中”。
4. 任务结束后确认按钮自动恢复。

---

## 七、完成标准

- [ ] awaitingLaunch watcher 收到首次 204 后继续轮询。
- [ ] watcher 有明确的 30 秒/10 次 204 上限，不会永久运行。
- [ ] 观察到 RUNNING/CANCELLING 后退出启动等待态。
- [ ] 终态恢复按钮并只通知一次。
- [ ] POST 成功且弹窗已关闭时 watcher 继续接管。
- [ ] POST 失败和 409 分支清理 watcher。
- [ ] 同一 taskType 无重复 interval。
- [ ] 新增竞态测试全部通过。
- [ ] 原 59 个 JS 测试无回归。
- [ ] 相关 Kotlin 测试无回归。
- [ ] `mvn test`、JS 语法检查、`git diff --check` 通过。
- [ ] 未修改后端任务并发/token 语义。
- [ ] 未修改本计划范围外文件。

