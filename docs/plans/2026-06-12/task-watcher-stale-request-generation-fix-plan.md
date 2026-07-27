# Task Watcher 旧请求污染新实例竞态修复计划

> 目标：修复同一 `taskType` 的旧 watcher 请求在 watcher 被停止并重新创建后返回，错误更新或停止新 watcher 的竞态。
>
> 本计划只修 watcher 实例身份隔离及对应测试。不要修改后端接口、任务执行汇总、两层日志、`TaskProgressStore` token 机制或其他业务逻辑。

---

## 一、复验结论

上一轮“启动阶段首次 204”问题已按计划修复：

- watcher 已改为状态对象。
- `awaitingLaunch` 支持有限 204 宽限期。
- POST 成功后支持 watcher 接管。
- 409/失败分支会清理 watcher。
- 新增竞态测试已通过。

但复验发现一个新的同类型 watcher 代际竞态。

### 1.1 当前问题代码

文件：

`src/main/resources/static/app.js`

当前 `pollTaskWatcher` 在请求前保存 watcher：

```js
async function pollTaskWatcher(taskType) {
    const watcher = taskWatchers[taskType];
    if (!watcher) return;

    const response = await fetch(...);

    const current = taskWatchers[taskType];
    if (!current) return;

    // 继续更新或停止 current
}
```

这里仅验证“map 中仍存在同 taskType watcher”，没有验证：

```js
current === watcher
```

因此旧请求可操作后来创建的新 watcher。

---

## 二、复现时序

### 2.1 204 错误停止新 watcher

```text
Watcher A 启动
→ A 发出 GET /api/task-progress/{taskType}
→ 请求尚未返回
→ 用户重新打开任务弹窗
→ openTaskModal 调 stopTaskWatcher，A 被删除
→ 用户再次关闭弹窗
→ 创建同 taskType 的 Watcher B
→ A 的旧 GET 返回 204
→ pollTaskWatcher 读取 taskWatchers[taskType]
→ 读到 B
→ 把 B.noProgressCount + 1
→ 甚至 stopTaskWatcher(taskType, true)
→ B 被错误删除，按钮被错误恢复
```

### 2.2 终态错误停止新 watcher

```text
Watcher A 监控 execution 100
→ A 的 GET 尚未返回
→ A 被停止
→ 创建 Watcher B，准备监控 execution 101
→ A 的旧 GET 返回 execution 100 的 COMPLETED
→ 当前代码把 B 停止
→ 恢复按钮
→ 发出旧 execution 100 的完成通知
→ execution 101 后续无人监控
```

### 2.3 RUNNING 错误污染新 watcher

```text
Watcher A 的旧 GET 返回 RUNNING
→ 当前 map 中已是 Watcher B
→ B 被写入 observedActive=true
→ B.awaitingLaunch=false
→ B.noProgressCount=0
```

这会让新 watcher 丢失自己的启动宽限期语义。

---

## 三、根因

`taskWatchers` 以 `taskType` 为 key：

```js
const taskWatchers = {};
```

同一 key 被删除和重建后，key 相同但对象实例已不同。

异步请求返回后只按 key 重新查询：

```js
const current = taskWatchers[taskType];
if (!current) return;
```

无法区分：

- 请求所属的旧 watcher A。
- 当前 map 中的新 watcher B。

现有测试 Case 8 只覆盖：

```text
请求期间 watcher 被删除，且没有重建
```

未覆盖：

```text
请求期间 watcher 被删除，并重建同 taskType watcher
```

---

## 四、修复目标

1. 每次 `pollTaskWatcher` 只能更新发起该请求的 watcher 实例。
2. watcher 被停止后，所有旧在途请求返回时必须静默丢弃。
3. 同 taskType 新 watcher 创建后，不受旧 watcher 的 204、RUNNING、CANCELLING、COMPLETED、FAILED、CANCELLED 响应影响。
4. 旧请求不得：
   - 增加新 watcher 的 `noProgressCount`
   - 修改新 watcher 的 `awaitingLaunch`
   - 修改新 watcher 的 `observedActive`
   - 停止新 watcher
   - 恢复按钮
   - 发出完成通知
5. 保持现有首次 204 宽限期行为。
6. 保持同 taskType 单 watcher、通知去重和按钮恢复行为。
7. 不引入 AbortController 作为必要依赖；对象身份校验即可解决正确性问题。

---

## 五、实施方案

### 5.1 增加 watcher 实例身份校验函数

文件：

`src/main/resources/static/app.js`

新增顶层函数：

```js
function isCurrentTaskWatcher(taskType, watcher) {
    return watcher != null && taskWatchers[taskType] === watcher;
}
```

要求：

- 使用严格对象身份比较。
- 不只比较 `taskType`。
- 不只比较 `startedAt`，时间戳可能相同。
- 不需要新增随机 ID；对象身份已经足够。

也可增加单调递增 `generation`，但本次优先使用对象身份，改动更小。

### 5.2 在所有异步边界后检查身份

修改 `pollTaskWatcher`：

```js
async function pollTaskWatcher(taskType) {
    const watcher = taskWatchers[taskType];
    if (!watcher) return;

    try {
        const response = await fetch(...);
        if (!isCurrentTaskWatcher(taskType, watcher)) return;

        if (response.status === 204) {
            watcher.noProgressCount += 1;
            // 只操作 watcher，不再重新按 key 获取另一个 current
        }

        if (!response.ok) return;

        const progress = await response.json();
        if (!isCurrentTaskWatcher(taskType, watcher)) return;

        // 只操作 watcher
    } catch (e) {
        // 保留当前网络错误策略
    }
}
```

必须有两次身份检查：

1. `await fetch(...)` 后。
2. `await response.json()` 后。

原因：

- watcher 可能在 HTTP 响应返回前被替换。
- watcher 也可能在 JSON 异步解析期间被替换。

### 5.3 避免重新获取不相关的 `current`

当前代码：

```js
const current = taskWatchers[taskType];
if (!current) return;
```

改为：

```js
if (!isCurrentTaskWatcher(taskType, watcher)) return;
```

后续直接修改捕获的 `watcher`：

```js
watcher.noProgressCount += 1;
watcher.awaitingLaunch = false;
watcher.observedActive = true;
```

不要在请求返回后把 `taskWatchers[taskType]` 赋给新的局部变量并操作，因为该对象可能属于新实例。

### 5.4 停止 watcher 时增加 expected instance 保护

仅在 `pollTaskWatcher` 内调用停止时，建议支持 expected watcher：

```js
function stopTaskWatcher(taskType, restoreButton, expectedWatcher) {
    const watcher = taskWatchers[taskType];
    if (expectedWatcher && watcher !== expectedWatcher) {
        return false;
    }

    if (watcher) {
        if (watcher.intervalId) clearInterval(watcher.intervalId);
        delete taskWatchers[taskType];
    }

    if (restoreButton) {
        const mapping = taskButtonMapping[taskType];
        if (mapping) restoreTaskButton(mapping.btnId);
    }
    return true;
}
```

`pollTaskWatcher` 中使用：

```js
stopTaskWatcher(taskType, true, watcher);
```

其他同步调用保持兼容：

```js
stopTaskWatcher(taskType, false);
stopTaskWatcher(taskType, true);
```

这样即使身份检查和停止调用之间发生同步重入，也不会删除新 watcher。

如果执行 agent 认为浏览器单线程下身份检查后不存在同步重入，也仍建议保留 expected instance 参数，形成函数级防御。

### 5.5 `startTaskWatcher` 保持实例独立

当前创建状态对象的方式可以保留：

```js
watcher = {
    intervalId: null,
    awaitingLaunch: ...,
    startedAt: Date.now(),
    noProgressCount: 0,
    observedActive: false
};
```

要求：

- 每次真正重建必须创建新对象。
- 不得复用已停止 watcher 对象。
- 重复 start 且 watcher 仍有效时继续复用同一对象，避免重复 interval。

### 5.6 `markTaskWatcherLaunchSucceeded` 不需大改

该函数是同步操作，可继续按当前 map 中实例更新。

但应补测试确认：

- 旧 watcher 请求返回时，不能覆盖 `markTaskWatcherLaunchSucceeded` 对新 watcher 的状态。

### 5.7 可选增强：记录 generation 便于调试

非必须。若希望日志和测试更明确，可增加：

```js
let taskWatcherGenerationSequence = 0;

watcher = {
    generation: ++taskWatcherGenerationSequence,
    ...
};
```

身份判断仍以对象严格相等为准：

```js
taskWatchers[taskType] === watcher
```

不要只依赖 generation 数字，避免手工构造测试对象时遗漏。

---

## 六、测试要求

文件：

`src/test/js/taskModalStateMachine.test.js`

### Case 11：旧 204 不得停止新 watcher

完整步骤：

1. 创建 watcher A。
2. A 发起 fetch，保持 pending。
3. 停止 A。
4. 创建同 taskType watcher B。
5. 让 B 的 fetch 保持 pending，避免测试干扰。
6. 让 A 的 fetch 返回 204。
7. 断言：
   - map 中仍是 B。
   - B 的 `noProgressCount` 未变化。
   - B 的 `awaitingLaunch` 未变化。
   - 未恢复按钮。
   - B 的 interval 未被 clear。

### Case 12：旧 RUNNING 不得修改新 watcher

1. A 请求 pending。
2. 删除 A，创建 B，B 为 `awaitingLaunch=true`。
3. A 返回 RUNNING。
4. 断言 B：
   - `awaitingLaunch=true`
   - `observedActive=false`
   - `noProgressCount` 保持原值

### Case 13：旧 COMPLETED 不得停止或通知新 watcher

1. A 请求 pending。
2. 删除 A，创建 B。
3. A 返回：

```json
{
  "status": "COMPLETED",
  "executionId": 100
}
```

4. 断言：
   - B 仍存在。
   - 未恢复按钮。
   - `showStatus` 未调用。
   - notification set 未写入 execution 100。

### Case 14：JSON 解析期间替换 watcher

1. A 的 fetch 返回 HTTP 200。
2. `response.json()` 保持 pending。
3. 停止 A，创建 B。
4. resolve A 的 JSON 为 CANCELLED。
5. 断言 B 不受影响。

### Case 15：expected watcher 防止误删

直接验证：

```js
stopTaskWatcher(taskType, true, watcherA)
```

当 map 中是 watcher B 时：

- 返回 false。
- B 仍存在。
- 不恢复按钮。
- 不 clear B 的 interval。

### Case 16：当前实例终态仍正常停止

确保防护没有拦截正常路径：

1. 当前 watcher 返回 COMPLETED。
2. 断言：
   - watcher 删除。
   - interval 清理。
   - 按钮恢复一次。
   - 通知一次。

---

## 七、生命周期集成测试

文件：

`src/test/js/taskModalLifecycleIntegration.test.js`

新增真实函数组合测试：

```text
Watcher A 正在请求
→ 用户打开弹窗，A 被 stop
→ 用户关闭弹窗，创建 Watcher B
→ A 的旧响应返回终态或 204
→ B 保持存活
→ B 后续观察 RUNNING
→ B 后续观察 COMPLETED
→ B 正常停止
→ 按钮只在 B 终态时恢复
→ 只发 B 的完成通知
```

要求：

- 调用从 `app.js` 提取的真实：
  - `pollTaskWatcher`
  - `startTaskWatcher`
  - `stopTaskWatcher`
  - `closeTaskModal`
- 不允许仅用 `source.includes(...)` 断言。
- 分别控制 A、B 的 Promise resolver。
- 明确断言旧响应返回后 B 仍为 map 当前实例。

---

## 八、回归测试

现有以下用例必须继续通过：

- 启动阶段首次 204 保留 watcher。
- 多个 204 后观察 RUNNING。
- RUNNING 后 204 停止当前 watcher。
- 宽限期次数耗尽。
- 宽限期时间耗尽。
- 启动阶段直接观察终态。
- 非 awaitingLaunch watcher 收到 204。
- 重复 start 不创建多个 interval。
- 请求期间 watcher 删除且未重建。
- POST 成功时弹窗已关闭。
- POST 失败清理 watcher。
- modal generation stale response 隔离。
- completion notification 去重。
- 两层日志 UI 渲染。

---

## 九、验收命令

JS 全套：

```bash
node --test src/test/js/*.test.js
```

语法检查：

```bash
node --check src/main/resources/static/app.js
node --check src/main/resources/static/task-modal-runtime.js
```

完整 Maven：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

代码卫生：

```bash
git diff --check
```

注意：

```bash
node --test src/test/js/
```

在当前 Node 25 环境会把目录当模块处理并失败，应使用 glob。

---

## 十、完成标准

- [ ] 新增 `isCurrentTaskWatcher` 或等价严格实例身份校验。
- [ ] `await fetch` 后检查 watcher 身份。
- [ ] `await response.json` 后再次检查 watcher 身份。
- [ ] 旧 204 不修改或停止新 watcher。
- [ ] 旧 RUNNING/CANCELLING 不修改新 watcher。
- [ ] 旧终态不停止新 watcher、不恢复按钮、不通知。
- [ ] `stopTaskWatcher` 支持 expected watcher 防误删，或提供等价原子保护。
- [ ] 当前 watcher 的正常终态停止行为无回归。
- [ ] 新增 Case 11 至 Case 16。
- [ ] 新增生命周期 A/B watcher 替换集成测试。
- [ ] 原有 70 个 JS 测试全部通过。
- [ ] `mvn test` 通过。
- [ ] 两份 JS 语法检查通过。
- [ ] `git diff --check` 通过。
- [ ] 不修改后端和任务 token 语义。
- [ ] 不提交、不 push，直到下一轮复验无问题。

