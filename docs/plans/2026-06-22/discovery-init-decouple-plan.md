# 深度发现初始化解耦：RAW 扫描可选化 + 按钮重命名

## 需求描述

**可观测结果：** 深度发现任务不再长时间卡在"初始化中..."状态。用户点击后立即进入数据源搜索阶段。RAW 扫描作为高级选项由用户按需勾选（默认不勾选）。下拉菜单中按钮名称更清晰地反映各模式功能。

**不得改变的行为：**

- "快速扫描（本地 RAW）"独立入口功能不变
- "重新验证候选人"功能不变
- `discover()` 的核心发现流程（数据源搜索、邮箱提取、去重、晋升）不变
- 游标持久化机制不变（plan-discovery-throughput-boost 已实现）
- 定时任务 `ExpertDiscoveryScheduler` 同样遵循配置值，不硬编码
- 所有已有 Flyway 迁移不动
- `ExpertDiscoveryProperties` 中 `maxPapersPerRun` / `maxAuthorsPerRun` 的 Kotlin 默认值不变（遵循 I-6）

**不在本次范围：**

- RAW 扫描子任务内部的进度上报优化（不改 `promoteEligibleRawExperts` 内部逻辑）
- `backfillRawEmailsAndPromote` 进度上报
- 前端 task modal 的 UI 样式调整
- 新增取消响应（让 RAW 扫描感知 EXPERT_DISCOVERY 取消）

---

## 关键不变量

### Invariant I-1: includeRawScan 配置化，Kotlin 默认值为 true

- Rule: `ExpertDiscoveryProperties.includeRawScan` 的 Kotlin 数据类默认值为 `true`（保持向后兼容）。生产环境通过 `application.yml` 覆盖为 `false`。`discover()` 方法从配置读取该值，不再使用方法参数默认值。
- Applies to: `ExpertDiscoveryProperties`、`ExpertDiscoveryService.discover()`、`application.yml`
- Violation consequence: Kotlin 默认值改为 false → 没配 YAML 的环境行为静默变更；保留方法参数默认值 → 配置不生效。

### Invariant I-2: 前端 includeRawScan 参数透传

- Rule: 前端复选框勾选时，API 请求携带 `includeRawScan=true` 参数。Controller 将该参数传递给 `discover()`，覆盖配置值。不勾选时不传该参数，使用配置默认值（`false`）。
- Applies to: `app.js executeDiscover()`、`ExpertDiscoveryController.triggerDiscovery()`、`ExpertDiscoveryController.triggerDiscoveryByKeyword()`
- Violation consequence: 参数不透传 → 用户勾选无效，RAW 扫描永远不执行。

### Invariant I-3: discover() 入口进度更新

- Rule: `discover()` 进入后、开始任何耗时操作前，必须更新一次 EXPERT_DISCOVERY 进度消息（如 "正在加载数据源配置..."），替换 Controller 层设的 "初始化中..."。当 `includeRawScan=true` 时，进入 RAW 扫描前再更新一次（如 "正在扫描 RAW 索引..."）。
- Applies to: `ExpertDiscoveryService.discover()`
- Violation consequence: 用户仍然看到长时间 "初始化中..."。

### Invariant I-4: 按钮命名清晰

- Rule: 下拉菜单项重命名为："快速晋升（扫描 RAW）"和"深度发现（外部数据源）"。`taskButtonMapping`、`taskLaunchConfigs`、`openTaskModal` 调用处的 label/title 同步更新。`handleDiscoverClick` 的默认行为（直接点按钮）指向深度发现。
- Applies to: `index.html` 下拉菜单、`app.js` 中所有引用这两个名称的位置
- Violation consequence: 新旧名称混用 → 用户困惑，同一任务在不同界面显示不同名称。

---

## 现状审计

### `ExpertDiscoveryProperties` 配置类

- 文件: `src/main/kotlin/.../config/ExpertDiscoveryProperties.kt`
- 当前字段: `enabled`, `cron`, `maxPapersPerRun`, `maxAuthorsPerRun`（无 `includeRawScan`）
- Write paths: 仅 `application.yml` 通过 `@ConfigurationProperties` 绑定
- Read paths:
  1. `ExpertDiscoveryService.discover()` — 读 `maxPapersPerRun`, `maxAuthorsPerRun`
  2. `ExpertDiscoveryScheduler` — 读 `enabled`, `cron`
  3. `ExpertDiscoveryController` — 不直接读（通过 service 间接使用）

### `ExpertDiscoveryService.discover()` 的 includeRawScan 逻辑

- 当前: 方法参数 `includeRawScan: Boolean = true`，执行两个子任务：
  1. `revalidationService.promoteEligibleRawExperts()` — scroll 全量 RAW 索引，更新 `RAW_PROMOTION_SCAN` 进度（不更新 `EXPERT_DISCOVERY`）
  2. `backfillRawEmailsAndPromote(100)` — scroll RAW 索引查无邮箱记录，调 ORCID API，不更新任何进度
- 调用者:
  1. `ExpertDiscoveryController.triggerDiscovery()` — 不传 `includeRawScan`，走默认 `true`
  2. `ExpertDiscoveryController.triggerDiscoveryByKeyword()` — 不传 `includeRawScan`，走默认 `true`
  3. `ExpertDiscoveryScheduler.scheduleDiscovery()` — 不传 `includeRawScan`，走默认 `true`
- Interaction points: RAW 扫描是导致"长时间初始化"的直接原因

### 前端按钮与下拉菜单

- `index.html:409` — 主按钮 `"发现专家"` → `handleDiscoverClick()`
- `index.html:414` — 下拉 `"快速扫描（本地 RAW）"` → `handleDiscoverOption('quick')`
- `index.html:415` — 下拉 `"深度发现（含外部平台）"` → `handleDiscoverOption('deep')`
- `app.js:219` — `RAW_PROMOTION_SCAN` label: `"发现专家（快速）"`
- `app.js:220` — `EXPERT_DISCOVERY` label: `"发现专家（深度）"`
- `app.js:1975-1986` — `taskLaunchConfigs.RAW_PROMOTION_SCAN` title: `"发现专家（快速）"`
- `app.js:1988-1994` — `taskLaunchConfigs.EXPERT_DISCOVERY` title: `"发现专家（深度）"`
- `app.js:2252` — `openTaskModal` 调用: `"发现专家（快速）"`
- `app.js:2262` — `openTaskModal` 调用: `"发现专家（深度）"`
- `app.js:2276` — `openTaskModal` 调用: `"发现专家（快速）"`
- `app.js:2299` — `openTaskModal` 调用: `"发现专家（快速）"`
- `app.js:2369` — `openTaskModal` 调用: `"发现专家（深度）"`

### 深度发现启动弹窗 (taskModalConfigSection)

- `index.html:669-673` — 关键词输入行 (`taskLaunchKeywordRow`)，EXPERT_DISCOVERY 时显示
- `index.html:683-686` — 数据源选择行 (`taskLaunchSourcesRow`)，EXPERT_DISCOVERY 时显示
- `app.js:2093` — `taskLaunchKeywordRow` 的显隐由 `config.showKeyword` 控制
- `app.js:2098-2104` — `taskLaunchSourcesRow` 在 `taskType === "EXPERT_DISCOVERY"` 时显示
- 高级选项复选框需要插入到 `taskLaunchSourcesRow` 之后

### Controller API 参数

- `POST /api/expert-discovery/run` — `@RequestBody PaperSearchCriteria?`，无 `includeRawScan` 参数
- `POST /api/expert-discovery/run/by-keyword` — `@RequestParam` 方式，有 `keywords`, `yearFrom`, `yearTo`, `sources`，无 `includeRawScan`

---

## 实现方案

### 阶段一：配置层

**任务 1: ExpertDiscoveryProperties 新增字段** [I-1]

- 文件: `src/main/kotlin/.../config/ExpertDiscoveryProperties.kt`
- 新增字段 `val includeRawScan: Boolean = true`（Kotlin 默认 true，向后兼容）

**任务 2: application.yml 覆盖** [I-1]

- 文件: `src/main/resources/application.yml`
- `talent-introduction.expert-discovery` 下新增 `include-raw-scan: false`

### 阶段二：服务层

**任务 3: discover() 改用配置 + 添加进度更新** [I-1, I-2, I-3]

- 文件: `src/main/kotlin/.../discovery/service/ExpertDiscoveryService.kt`
- `discover()` 签名变更: 移除 `includeRawScan: Boolean = true` 参数，改为 `includeRawScan: Boolean = discoveryProperties.includeRawScan`（从配置读取，但允许调用方覆盖）
- 方法入口（`val stats = DiscoveryStats()` 之后、`if (includeRawScan)` 之前）插入进度更新:
  ```kotlin
  progressStore.update("EXPERT_DISCOVERY", TaskProgress(
      taskType = "EXPERT_DISCOVERY", status = "RUNNING",
      batchNumber = 0, processedCount = 0, totalCount = 0,
      message = "正在加载数据源配置..."
  ), execId)
  ```
- `if (includeRawScan)` 块内、调用 `promoteEligibleRawExperts()` 前插入进度更新:
  ```kotlin
  progressStore.update("EXPERT_DISCOVERY", TaskProgress(
      taskType = "EXPERT_DISCOVERY", status = "RUNNING",
      batchNumber = 0, processedCount = 0, totalCount = 0,
      message = "正在扫描 RAW 索引并晋升..."
  ), execId)
  ```

### 阶段三：Controller 层

**任务 4: API 接受 includeRawScan 参数** [I-2]

- 文件: `src/main/kotlin/.../discovery/controller/ExpertDiscoveryController.kt`
- `triggerDiscovery()` 新增 `@RequestParam(required = false) includeRawScan: Boolean?`，传给 `discover()`:
  ```kotlin
  discoveryService.discover(
      criteria ?: PaperSearchCriteria(...),
      "MANUAL",
      includeRawScan = includeRawScan ?: discoveryProperties.includeRawScan
  )
  ```
- `triggerDiscoveryByKeyword()` 同理，新增 `@RequestParam(required = false) includeRawScan: Boolean?`

### 阶段四：前端

**任务 5: 启动弹窗新增高级选项** [I-2]

- 文件: `src/main/resources/static/index.html`
- 在 `taskLaunchSourcesRow` (line 686) 之后新增:
  ```html
  <div id="taskLaunchAdvancedRow" hidden>
      <details class="task-launch-advanced">
          <summary style="font-size:12px;color:var(--text-muted);cursor:pointer;user-select:none;">高级选项</summary>
          <div style="margin-top:6px;">
              <label style="display:flex;align-items:center;gap:6px;font-size:12px;cursor:pointer;">
                  <input type="checkbox" id="taskLaunchIncludeRawScan">
                  启动前扫描 RAW 索引并晋升（耗时较长）
              </label>
          </div>
      </details>
  </div>
  ```

**任务 6: JS 显隐逻辑 + 参数透传** [I-2]

- 文件: `src/main/resources/static/app.js`
- `openTaskLaunchModal()` 中 (约 line 2098-2104)，在 `taskLaunchSourcesRow` 显隐逻辑后新增:
  ```javascript
  const advancedRow = $("#taskLaunchAdvancedRow");
  if (taskType === "EXPERT_DISCOVERY") {
      advancedRow.hidden = false;
      $("#taskLaunchIncludeRawScan").checked = false;
  } else {
      advancedRow.hidden = true;
  }
  ```
- `executeDiscover()` 中，读取复选框值并拼入 API 请求:
  - keyword 模式: `params.append("includeRawScan", ...)` 
  - body 模式: `body.includeRawScan = ...`
  - 仅在 `checked === true` 时传参，否则不传（走配置默认 false）

**任务 7: 按钮名称统一更新** [I-4]

- 文件: `src/main/resources/static/index.html`
  - line 414: `"快速扫描（本地 RAW）"` → `"快速晋升（扫描 RAW）"`
  - line 415: `"深度发现（含外部平台）"` → `"深度发现（外部数据源）"`
- 文件: `src/main/resources/static/app.js` — 所有引用处同步:
  - `taskButtonMapping` (line 220): `"发现专家（快速）"` → `"快速晋升（扫描 RAW）"`
  - `taskButtonMapping` (line 221): `"发现专家（深度）"` → `"深度发现（外部数据源）"`
  - `taskLaunchConfigs.RAW_PROMOTION_SCAN.title` (line 1976): → `"快速晋升（扫描 RAW）"`
  - `taskLaunchConfigs.EXPERT_DISCOVERY.title` (line 1989): → `"深度发现（外部数据源）"`
  - `openTaskModal` 调用 (line 2252): → `"快速晋升（扫描 RAW）"`
  - `openTaskModal` 调用 (line 2262): → `"深度发现（外部数据源）"`
  - `openTaskModal` 调用 (line 2276): → `"快速晋升（扫描 RAW）"`
  - `openTaskModal` 调用 (line 2299): → `"快速晋升（扫描 RAW）"`
  - `openTaskModal` 调用 (line 2369): → `"深度发现（外部数据源）"`

---

## 变更文件清单

| # | 文件路径 | 操作 | 说明 |
|---|---------|------|------|
| 1 | `src/main/kotlin/.../config/ExpertDiscoveryProperties.kt` | 修改 | 新增 `includeRawScan` 字段 |
| 2 | `src/main/resources/application.yml` | 修改 | 新增 `include-raw-scan: false` |
| 3 | `src/main/kotlin/.../discovery/service/ExpertDiscoveryService.kt` | 修改 | `discover()` 签名变更 + 入口进度更新 |
| 4 | `src/main/kotlin/.../discovery/controller/ExpertDiscoveryController.kt` | 修改 | 两个 API 新增 `includeRawScan` 参数 |
| 5 | `src/main/resources/static/index.html` | 修改 | 高级选项 HTML + 按钮重命名 |
| 6 | `src/main/resources/static/app.js` | 修改 | 高级选项显隐/透传 + 按钮名称统一 |

共 6 个文件，2 个子系统（后端配置/服务层、前端 UI）。

---

## 验收标准

### 按不变量验证

- **I-1**: `ExpertDiscoveryProperties.kt` 中 `includeRawScan` 默认值为 `true`。`application.yml` 中 `include-raw-scan` 值为 `false`。`discover()` 方法参数默认值引用 `discoveryProperties.includeRawScan`。
- **I-2**: 前端不勾选复选框 → API 请求不含 `includeRawScan` → Controller 使用配置值 `false` → `discover()` 跳过 RAW 扫描。前端勾选 → API 请求含 `includeRawScan=true` → Controller 传递 → `discover()` 执行 RAW 扫描。
- **I-3**: 启动深度发现后，前端进度弹窗在 1 秒内从 "初始化中..." 更新为 "正在加载数据源配置..."（而非持续停留在 "初始化中..."）。当 `includeRawScan=true` 时，进入 RAW 扫描前消息更新为 "正在扫描 RAW 索引并晋升..."。
- **I-4**: 下拉菜单、taskButtonMapping、taskLaunchConfigs、openTaskModal 调用处的名称完全一致，无新旧名称混用。

### 集成场景

1. **默认启动（不勾选）**: 点击深度发现 → 弹窗打开 → 高级选项折叠（默认不勾选）→ 点击开始 → 进度立即从 "初始化中..." 更新为 "正在加载数据源配置..." → 几秒内进入第一个数据源搜索
2. **勾选 RAW 扫描**: 点击深度发现 → 展开高级选项 → 勾选 → 开始 → 进度显示 "正在扫描 RAW 索引并晋升..." → RAW 扫描完成后进入数据源搜索
3. **定时任务**: `ExpertDiscoveryScheduler` 调用 `discover()` 不传 `includeRawScan` → 使用配置值 `false` → 跳过 RAW 扫描
4. **按钮名称**: 下拉菜单显示 "快速晋升（扫描 RAW）"、"深度发现（外部数据源）"。任务执行中和任务弹窗内名称一致。

### 编译 + 测试

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean test
```

全部已有测试必须通过。本次改动不新增表、不改迁移，测试适配量最小。重点关注:
- `ExpertDiscoveryServiceTest` — `discover()` 签名变更，现有测试如直接调用需适配参数
- `ExpertDiscoveryControllerTest` / `ExpertDiscoveryControllerMvcTest` — 新参数不影响现有请求（`required = false`）

---

## 自检清单

- [x] 关键不变量 section 存在，每个新字段/状态至少 1 条不变量（`includeRawScan` 配置 → I-1, 前端透传 → I-2, 进度更新 → I-3, 按钮命名 → I-4）
- [x] 现状审计列出了所有受影响的写路径和读路径
- [x] 没有任务引入未被不变量覆盖的写路径
- [x] 文件数 = 6 ≤ 10
- [x] 子系统数 = 2 ≤ 2
- [x] 每个任务引用了其对应的不变量编号
- [x] 验收标准中每个不变量至少有一条检查
- [x] 文件清单中无 "and related files" 或 "etc."
- [x] Out-of-scope 明确列出了延迟项
