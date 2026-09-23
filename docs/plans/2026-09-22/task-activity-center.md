# 后台任务非打扰展示与任务记录整合

状态：DRAFT / 待评审；本文件仅为开发计划，不授权上线，不表示功能已实现。
日期：2026-09-22。审计基线：`e2247680592603b091af791ef3629d70739a015b` + 本次工作树。
用户确认的方向：后台任务不主动弹框，全站看得到任务运行，详情集中进入任务记录。
视觉参考：`docs/mockups/task-center-preview/overview.png`；它是本地演示数据截图，不是生产运行证据。最终实现以本计划 CSS / DOM 为准，禁止复制 preview.js 的硬编码任务数据。

## 需求描述

O-1：登录、刷新、切换页面和定时任务开始时不自动打开任务弹框；导航及页面标题右侧展示任务执行数量，用户点击进入任务记录。
O-2：任务记录顶部展示分页的进行中执行记录，含真实类型、触发方式、开始时间、耗时及可获得的实时进度；点击卡片在页内展开该执行详情，下方保留现有查询、分页及钻取。

必须保持：

- R-1：手动启动任务、任务互斥、取消/暂停、批量发送配置与日志控制台保持现有业务行为。用户主动点击打开的控制弹框仍允许存在。
- R-2：现有任务记录的七列表格、筛选、50 条分页、邮件/专家钻取、原始结果展示保持。
- R-3：调度频率、执行器、数据库写入、ES、发信/收信、任务终态和保留清理规则不变。
- R-4：鉴权、退出清理、现有手动任务完成提示及去重保持；其他导航与页面不被任务刷新打断。

本轮边界（防止把预览扩成任务平台重构）：

- 只实现预览中可见的常驻入口、运行卡片、页内详情；不迁移手动任务的整套启动/控制弹框状态机。此前讨论中“手动提交后自动收起”属于可选后续项，本轮不做。
- 不新增 WebSocket/SSE、事件总线、通用任务框架、缓存层、数据库字段、迁移、心跳、跨实例调度协调。
- 不新增跨会话“失败未读”通知中心。自动任务完成不强行弹通知；已有手动 watcher 提示保留。失败结果在详情和记录中显示。
- 不推断短于轮询周期的任务曾经运行；它们仍由任务记录查询可见。
- 不修复既有进程崩溃后 `task_execution.RUNNING` 遗留问题。数量的明确口径是“数据库记录状态”，不是进程存活探针。UI 必须标注此口径；无内存进度不能伪造为已中断或正常进行。
- 不把当前任务记录表重做成预览中的八列表格；截图的“耗时”在运行卡片和详情提供，历史七列保持，以减少无关改动。

规模：9 个实现/测试文件，2 个子系统（任务只读接口、前端展示），新增共享持久字段 0。

## 关键不变量

### I-1：自动观察无弹框副作用
- Rule：自动入口只能启动/刷新只读观察器和更新任务中心 DOM，不得调用 `openTaskModal`、`openTaskLaunchModal`、`openBatchSendTaskModal`，不得修改 `currentTaskModal`、`body.modal-open` 或跳转视图。
- Applies to：`resumeProgressPollingIfNeeded`、`startAuthenticatedApp`、`setView` 的自动观察分支、新轮询回调。
- Violation consequence：重新抢占用户界面，或把已打开的业务弹框换成任务弹框。
- 来源：原始需求；代码 E-1。

### I-2：执行记录为集合、执行 ID 为身份
- Rule：集合取 `task_execution.status IN ('RUNNING','CANCELLING')`；按 `started_at DESC,id DESC` 排序，独立分页。未知 catalog 类型仍返回，label 回落 code。同类型不同 id 必须保留为不同卡片，不能用 taskType 去重。
- Rule：总数取独立 COUNT；卡片数不充当总数。分页导致执行离开本页不表示完成。数量只是记录状态，文案固定标注“按任务记录统计”。
- Applies to：新 repository 两条 SELECT、DTO、全局计数、卡片 key、翻页。
- Violation consequence：漏任务、计数不一致、把翻页当完成。
- 来源：K-task-type-semantics-three-lists、K-task-execution-list-full-scan；E-2。

### I-3：实时进度必须匹配同一次执行
- Rule：只读内存 `peek(taskType)`；仅 `p.executionId == row.id && row.id > 0 && p.taskType == row.taskType && p.status in {RUNNING,CANCELLING}` 时返回 progress。null、负 pendingToken、旧 id、已清理 id、终态一律 progress=null。不得用 `get(taskType)` 的日志恢复结果充当内存实时状态。
- Rule：row.status 原样保留；progress.status 仅补充展示“取消中”。内存终态与 DB 终态的短时错位不修改数据库、不推断完成。
- Rule：`totalCount <= 0` → percentage=null；不显示 0% 或无意义进度条。progress=null → 固定“记录为执行中，暂无实时进度”，不得猜耗时阈值来标失败。total>0 使用既有 `TaskProgress.percentage`；指标只写“已处理 X / Y”，不编造账号数、邮件数或轮次。
- Applies to：`TaskProgressStore.peek`、新 controller、卡片 renderer。
- Violation consequence：新任务进度串到旧任务，服务重启后误报真实运行，显示虚假统计。
- 来源：K-clearExecutionContext-status-leak、K-progress-log-pending-token-orphan、K-metric-label-not-reflection；E-3。

### I-4：观察链无业务写入且查询有界
- Rule：active 查询只取小字段（复用投影的 errorMessage 固定为 null）；不得 SELECT *、读取 request_payload/result_summary，不调用 summary extractor，不对每条执行查日志。默认 size=6，clamp 1..50，page>=0，offset 使用 Long；每次最多 1 次 COUNT + 1 次分页 SELECT + 内存读取。
- Rule：`peek` 仅返回 `store[taskType]`，不调用恢复、不写日志、不改取消标记。新 controller 只 GET；不改任何执行/调度写路径。
- Applies to：新增接口和 Store 方法。
- Violation consequence：高频扫描大 TEXT、写放大或改变任务业务状态。
- 来源：K-task-execution-list-full-scan、K-progress-log-per-mail-write-amplification；E-2/E-3/E-4。

### I-5：全局轮询单实例、串行、有代际隔离
- Rule：登录后立即查询，此后每次请求结束 5000ms 后单个 setTimeout；重复启动幂等。隐藏浏览器页暂停新请求，恢复可见立即查询一次。切业务 tab 不停止计数观察，退出/鉴权失效停止 timer 并清理状态。
- Rule：sessionGeneration、listRequestSequence、捕获的 view/page 必须阻止旧响应覆盖新页面；检查点在 await 解析完成之后。请求失败保留上次值并明确标“更新失败”，首次失败显示“任务状态不可用”；不置零、不每轮 toast、不发起并行重试。
- Rule：卡片局部更新，不替换用户聚焦的详情按钮/日志区；详情与列表分离，不随列表重建丢失。既有手动 watcher 是控制流程的一部分，不在本轮重写。
- Applies to：新 observer 状态、鉴权启动/停止、visibilitychange、翻页、卡片 DOM。
- Violation consequence：重复请求、登出后旧响应复活界面、页面切换串数据、详情/焦点闪烁。
- 来源：既有 watcher 的对象身份防旧响应机制；E-1/E-5。

### I-6：详情绑定明确执行、控制入口先核对身份
- Rule：卡片只按所选正整数 executionId 查询既有 `/{id}/detail`；一次只打开一个页内详情。打开 A 后改选 B/收起/切页/退出，A 的迟到响应不得写回。正在显示的执行不在卡片页中也不能切成同类新执行。
- Rule：详情中不新增按 taskType 直接取消的按钮。需要控制时，用户主动点击“打开任务控制”，先重新 GET `/api/task-progress/{taskType}` 验证正 id 与所选 id 相等且状态为运行/取消中，再调用现有 `openTaskModal`；不一致则只提示“该执行已结束或已被新执行替代，请刷新记录”，不打开控制。
- Rule：只有 `taskButtonMapping` 已支持的类型显示控制入口；catalog.hasProgressUi 并不意味着已有启动/控制按钮。控制后的既有取消确认与 backend 语义不改。该前置核对不是原子的 compare-and-cancel；旧控制面板面向当前任务，本页不承诺在核对之后任务切换的原子隔离，更不能直接替用户发送取消请求。
- Applies to：新详情面板、详情轮询、控制入口。
- Violation consequence：查看旧记录却看到新任务；历史卡片误导用户去取消新执行。
- 来源：K-task-type-semantics-three-lists（按钮与 catalog 刻意分离）；E-3/E-6。

### I-7：保留现有查询与提示契约
- Rule：现有 `loadTasks`、`toggleTaskDetail`、筛选/分页/钻取主体不改；全局 tick 不调用 `loadTasks()` 整表重绘。运行列表成员变化时显示独立“任务状态有变化，刷新执行记录”按钮，用户点击后按当前筛选和页码调用原 loadTasks 并清除提示。进入 tasks 页仍由 refreshCurrentView 加载记录。
- Rule：自动结束只移出 active 集合；若该执行的页内详情正在打开，继续按 id 读取到 DB 终态并保留终态内容（失败原因仍由原记录表提供），随后停止该详情轮询。新 observer 不调用 notifyTaskCompletionOnce，避免与旧 watcher 产生重复通知。
- Applies to：观察器与原任务记录页的交互。
- Violation consequence：历史查询结果被刷新重置、展开详情丢失、用户被成功通知刷屏。
- 来源：K-list-pager-skeleton-reuse；E-5/E-6。

### I-8：新增视图安全、样式与缓存可复现
- Rule：全部服务端文本 textContent 或 escapeHtml；id/百分比经过数值校验；禁止来自服务端的 HTML、inline style、内联事件函数。使用原生 progress.value 设置进度，不生成 style.width。
- Rule：新增 CSS 逐字复制 S-0 完整块，新增 DOM 遵守 S-1..S-4；旧规则不就地改，旧 renderer 内既有 inline style 本轮不扩散、不整治。
- Rule：11 个带版本资源统一换 `20260922-task-activity-center`；不增加脚本/CSS 文件数量；`task-modal-runtime.js` 保持原引用方式。执行前重新反查旧键，若基线变化先核实。
- Applies to：app.js、index.html、styles.css、测试。
- Violation consequence：样式漂移、XSS、部署后新旧 JS/CSS 混用。
- 来源：K-frontend-cache-key-triad、K-dom-stub-tests-hide-dangling-refs；E-7/E-8。

## 样式契约

视觉决策：保留现有蓝灰玻璃背景与页面骨架。三列卡片、14px 间距、14px 圆角；1100px 及以下两列，760px 及以下一列。进度未知时不渲染进度条。预览中“成功124 / 待重试2”“第3/5轮”等演示指标没有通用读契约，不写进实现。

### S-0：新增 CSS 的唯一权威正文

以下块原样追加到 `styles.css` 末尾；开始/结束标记也保留，便于字节比对。不得调整声明值、加入动画或再追加覆盖规则。所用 `--font-body/--font-mono` 来自既有 token。没有 hover 的展示元素保持静态；按钮 focus/hover/active/disabled 已明确。

```css
/* task-center-contract:start */
.topnav.task-center-nav {
    flex-wrap: wrap;
    row-gap: 8px;
}
.task-center-nav .nav-tabs {
    flex: 1 1 auto;
    flex-wrap: wrap;
}
.task-center-actions {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;
}
.task-center-pill {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 4px 9px;
    border-radius: 20px;
    background: #eaf0ff;
    color: #1e40af;
    font-size: 11px;
    font-weight: 600;
    line-height: 16px;
    white-space: nowrap;
}
.task-center-dot {
    display: inline-block;
    flex: 0 0 6px;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #3b82f6;
    box-shadow: 0 0 0 3px #dbeafe;
}
.task-center-nav-count {
    gap: 4px;
    margin-left: 2px;
    padding: 2px 6px;
    font-size: 10px;
}
.task-center-nav-count .task-center-dot {
    flex-basis: 5px;
    width: 5px;
    height: 5px;
    box-shadow: none;
}
.task-center-global {
    border: 1px solid #cbdaf8;
    padding: 8px 12px;
    background: #f3f6ff;
    font-family: var(--font-body);
    cursor: pointer;
}
.task-center-global:hover {
    border-color: #91afe8;
    background: #eaf0ff;
}
.task-center-global:active {
    background: #dce7fc;
}
.task-center-stale {
    background: #fff5e5;
    color: #ae7214;
}
.task-center-stale .task-center-dot {
    background: #d97706;
    box-shadow: none;
}
.task-center-section-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 8px;
    margin: 7px 0 13px;
}
.task-center-section-head h2 {
    display: flex;
    align-items: center;
    gap: 9px;
    font-size: 14px;
}
.task-center-count {
    padding: 2px 7px;
    border-radius: 6px;
    background: #e8eef9;
    color: #34548b;
    font-size: 11px;
}
.task-center-note {
    color: #64748b;
    font-size: 11px;
    line-height: 18px;
    overflow-wrap: anywhere;
}
.task-center-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 14px;
    margin-bottom: 18px;
}
.task-center-card {
    min-width: 0;
    padding: 18px;
    border: 1px solid #dce5f3;
    border-radius: 14px;
    background: rgba(255, 255, 255, 0.85);
    box-shadow: 0 3px 12px rgba(30, 64, 175, 0.024);
}
.task-center-card-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 14px;
}
.task-center-card h3 {
    margin: 0 0 5px;
    color: #1e293b;
    font-size: 14px;
    font-weight: 600;
    line-height: 20px;
    overflow-wrap: anywhere;
}
.task-center-source {
    color: #7b8ba3;
    font-size: 11px;
    line-height: 18px;
    overflow-wrap: anywhere;
}
.task-center-id {
    font-family: var(--font-mono);
    color: #8796ab;
    font-size: 11px;
}
.task-center-message {
    margin: 17px 0 11px;
    color: #53657e;
    font-size: 12px;
    line-height: 18px;
    overflow-wrap: anywhere;
}
.task-center-progress {
    display: block;
    width: 100%;
    height: 5px;
    appearance: none;
    -webkit-appearance: none;
    border: 0;
    border-radius: 5px;
    overflow: hidden;
    background: #e9eef7;
    color: #3b69d6;
}
.task-center-progress::-webkit-progress-bar {
    border-radius: 5px;
    background: #e9eef7;
}
.task-center-progress::-webkit-progress-value {
    border-radius: 5px;
    background: #3b69d6;
}
.task-center-progress::-moz-progress-bar {
    border-radius: 5px;
    background: #3b69d6;
}
.task-center-progress-text {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin: 8px 0 18px;
    color: #718198;
    font-size: 11px;
    line-height: 18px;
}
.task-center-progress-text strong {
    color: #405b87;
    font-weight: 600;
}
.task-center-card-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 8px;
    padding-top: 13px;
    border-top: 1px solid #edf0f6;
}
.task-center-link {
    padding: 0;
    border: 0;
    background: transparent;
    color: #1e40af;
    font-family: var(--font-body);
    font-size: 12px;
    line-height: 20px;
    cursor: pointer;
}
.task-center-link:hover {
    color: #1e3a8a;
    text-decoration: underline;
}
.task-center-link:active {
    color: #172554;
}
.task-center-global:focus-visible,
.task-center-link:focus-visible,
.task-center-detail .button:focus-visible,
#taskActivePager .button:focus-visible {
    outline: 2px solid #3b82f6;
    outline-offset: 3px;
}
.task-center-link:disabled,
.task-center-detail .button:disabled,
#taskActivePager .button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
}
.task-center-empty {
    padding: 24px 16px;
    margin-bottom: 18px;
    border: 1px dashed #dce5f3;
    border-radius: 14px;
    color: #64748b;
    background: rgba(255, 255, 255, 0.55);
    font-size: 12px;
    text-align: center;
}
.task-center-detail {
    min-width: 0;
    padding: 17px 20px;
    margin-bottom: 18px;
    border: 1px solid #d9e4f6;
    border-radius: 12px;
    background: #f8faff;
}
.task-center-detail-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 12px;
}
.task-center-detail-body {
    min-width: 0;
    overflow-wrap: anywhere;
}
.task-center-log {
    max-height: 240px;
    margin-top: 12px;
    overflow: auto;
    color: #586b87;
    font-family: var(--font-mono);
    font-size: 12px;
    line-height: 24px;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
}
.task-center-detail-actions {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
    margin-top: 12px;
}
@media (max-width: 1100px) {
    .task-center-nav .nav-tabs {
        flex-basis: 100%;
        flex-wrap: nowrap;
    }
    .task-center-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }
}
@media (max-width: 760px) {
    .task-center-grid {
        grid-template-columns: minmax(0, 1fr);
    }
    .task-center-card {
        padding: 13px;
    }
}
@media (prefers-color-scheme: dark) {
    .task-center-card {
        background: rgba(21, 31, 48, 0.85);
        border-color: #334155;
        box-shadow: none;
    }
    .task-center-card h3 {
        color: #e2e8f0;
    }
    .task-center-note,
    .task-center-source,
    .task-center-id,
    .task-center-message,
    .task-center-progress-text,
    .task-center-log {
        color: #a8b6c8;
    }
    .task-center-pill,
    .task-center-count {
        background: #172d50;
        color: #93c5fd;
    }
    .task-center-global {
        background: #172d50;
        border-color: #345580;
    }
    .task-center-global:hover {
        background: #1e3a5f;
        border-color: #60a5fa;
    }
    .task-center-global:active {
        background: #23456f;
    }
    .task-center-dot {
        box-shadow: 0 0 0 3px #1e3a5f;
    }
    .task-center-nav-count .task-center-dot {
        box-shadow: none;
    }
    .task-center-stale {
        background: #3e301a;
        color: #fbbf24;
    }
    .task-center-stale .task-center-dot {
        background: #fbbf24;
        box-shadow: none;
    }
    .task-center-progress,
    .task-center-progress::-webkit-progress-bar {
        background: #334155;
    }
    .task-center-progress-text strong,
    .task-center-link {
        color: #93c5fd;
    }
    .task-center-link:hover,
    .task-center-link:active {
        color: #bfdbfe;
    }
    .task-center-card-footer {
        border-color: #334155;
    }
    .task-center-empty,
    .task-center-detail {
        background: #1a2436;
        border-color: #334155;
        color: #a8b6c8;
    }
}
/* task-center-contract:end */
```

### S-1：全站入口

- 复用：`.topnav` styles.css:140、`.nav-tabs`:218、`.nav-tab`:226、`.topbar`:282；只给既有元素附加类，不修改旧 CSS 声明。
- 改 `index.html:62` 为 `<header class="topnav task-center-nav">`；改 `:167` 为 `<div class="topbar-actions task-center-actions">`。
- 此派生样式影响位置全集：同一个 header 内，index.html:78/84/90/99/105/112/121/122/129/137 的十个导航，以及 :149 的退出按钮；不能隐藏用户名或退出按钮来换空间。
- `.task-center-nav .nav-tabs` 在桌面允许换行；<=1100px 恢复既有横向可滚动单行导航，不能隐藏任务 tab。此处响应式规则优先于演示预览的压缩导航方案。
- 在任务 tab 原 `<span>任务记录</span>` 后追加以下 span；不要在 button 内嵌套 button。

```html
<span id="taskActiveNavBadge" class="task-center-pill task-center-nav-count" hidden>
    <i class="task-center-dot" aria-hidden="true"></i>
    <span id="taskActiveNavCount"></span>
</span>
```

- 在 `#refreshBtn` 前添加：

```html
<button type="button" id="taskActiveGlobalBtn" class="task-center-pill task-center-global" hidden
        title="按任务记录统计；实时进度仅显示与执行 ID 匹配的数据">
    <i class="task-center-dot" aria-hidden="true"></i>
    <span id="taskActiveGlobalText"></span>
    <span aria-hidden="true">↗</span>
</button>
```

- 首次成功 total=0：两入口隐藏；total>0：nav 数字=total（>99显示99+，aria-label 含准确数量），global=`N 个任务执行中`。错误时 global 保持显示、追加 `.task-center-stale`；首次失败=“任务状态不可用”，已有值=“N 个任务 · 更新失败”。nav 旧数追加 stale 状态和完整 aria-label，不可伪装实时。
- 点击任一任务入口只 `setView('tasks')`，不修改已有历史筛选；任务数更新不得抢焦点，nav aria-label 可读。

### S-2：运行区、卡片与独立分页

在 `#view-tasks` 的原 toolbar 之前追加以下固定结构。既有 view 保持 class，不清空 innerHTML。所有动态卡片插入 `#taskActiveCards`。

```html
<section id="taskActiveSection" aria-labelledby="taskActiveHeading">
    <div class="task-center-section-head">
        <h2 id="taskActiveHeading">正在执行 <span id="taskActiveCount" class="task-center-count">—</span></h2>
        <span id="taskActiveUpdated" class="task-center-note" role="status" aria-live="polite">正在读取任务状态…</span>
    </div>
    <div id="taskActiveEmpty" class="task-center-empty" hidden>暂无执行中的任务</div>
    <div id="taskActiveCards" class="task-center-grid"></div>
    <div id="taskActivePager" class="list-pager" hidden>
        <button type="button" class="button small" id="taskActivePrevPage">上一页</button>
        <span id="taskActivePageInfo" class="list-pager-info"></span>
        <button type="button" class="button small" id="taskActiveNextPage">下一页</button>
    </div>
</section>
```

- 成功提示固定“后台运行，不影响当前操作 · 约每 5 秒更新 · 按任务记录统计”。不能随每次刷新在 aria-live 播报所有卡片。
- 卡片模板（`{{…}}` 是替换位置，文本全部转义）：

```html
<article class="task-center-card" data-execution-id="{{id}}">
    <div class="task-center-card-top">
        <span class="task-center-source">{{triggerLabel}} · <span class="task-center-id">#{{id}}</span></span>
        <span class="task-center-pill"><i class="task-center-dot" aria-hidden="true"></i><span>{{statusLabel}}</span></span>
    </div>
    <h3>{{taskTypeLabel}}</h3>
    <div class="task-center-source">开始于 {{startedAt}} · 已运行 {{elapsedText}}</div>
    <p class="task-center-message">{{message}}</p>
    <progress class="task-center-progress" max="100" value="{{percentage}}" aria-label="{{taskTypeLabel}}执行进度"></progress>
    <div class="task-center-progress-text"><span>{{processedText}}</span><strong>{{percentageText}}</strong></div>
    <div class="task-center-card-footer">
        <span class="task-center-note">{{metricText}}</span>
        <button type="button" class="task-center-link" data-task-active-detail="{{id}}" aria-expanded="false" aria-controls="taskActiveDetail">查看详情 →</button>
    </div>
</article>
```

- 进度未知时隐藏 progress，percentageText=“进度未知”；有 live processedCount 但 total=0 时 processedText=“已处理 X”；无 live 数据为“暂无实时计数”。metricLabel=null 时 metricText=“— 无统计”；否则严格复用 catalog label + 持久 successCount/failureCount，不用任务中文名猜单位。
- 活动区分页复用 `.list-pager` styles.css:1259、`.list-pager-info`:1269、`.button.small`:2482；它们在其他页面多处使用，禁止就地修改。
- 只允许对新插入的卡片生成 HTML，之后按 id 复用节点更新；删除与添加均按 id，不能每 5 秒重建已存在的按钮。

### S-3：页内详情

在 `#taskActiveSection` 后、旧任务 toolbar 前插入，任意时刻只有一份。不使用 overlay、drawer 或 `body.modal-open`。

```html
<section id="taskActiveDetail" class="task-center-detail" aria-labelledby="taskActiveDetailTitle" hidden>
    <div class="task-center-detail-head">
        <h2 id="taskActiveDetailTitle"></h2>
        <button type="button" id="taskActiveDetailClose" class="task-center-link">收起详情 ↑</button>
    </div>
    <div id="taskActiveDetailStatus" class="task-center-note" role="status" aria-live="polite"></div>
    <div id="taskActiveDetailBody" class="task-center-detail-body"></div>
    <div class="task-center-detail-actions">
        <button type="button" id="taskActiveLoadLogs" class="task-center-link" hidden>加载批次日志</button>
        <button type="button" id="taskActiveOpenControl" class="button small secondary" hidden>打开任务控制</button>
    </div>
    <pre id="taskActiveLogs" class="task-center-log" hidden></pre>
</section>
```

- title=`任务名 · #执行ID`；加载中=“正在加载执行详情…”；失败=“详情加载失败，请重新选择该任务”；404=“执行记录不存在或已被清理”。详情终态沿用 labelStatus 与 badge 的既有颜色，body 复用 `renderTaskDetailRawBlocks(detail)`，其中 `.pre`（styles.css:1875）、`.text-muted`（:2489）保持原规则，状态 `.badge/.ok/.warn/.error` 复用 :1054-1084；保留原始请求/结果与截断提示。不复制钻取 renderer，钻取继续在原记录表。
- 日志用 pre.textContent 写文本，每行“时间  批次 N  状态  message”，不输出 detailsJson/errorsJson 的全量账号快照。无日志=“该执行暂无批次日志”。
- 复用 `.button` styles.css:802、`.secondary`:852、`.small`:2482；完整新增局部 focus/disabled 在 S-0。关闭后将焦点归还原详情按钮（仍存在时）；切页/退出时不抢焦点。

### S-4：原记录区域与状态变化提示

在原任务 toolbar 前、S-3 后添加：

```html
<button type="button" id="taskHistoryRefreshHint" class="task-center-link" hidden>任务状态有变化，刷新执行记录</button>
```

- 原 `#view-tasks .panel-head h2` 从“定时任务审计与消费日志 (Spring Job Audit)”改为“执行记录”。视图副标题改“统一查看后台任务进度、执行结果和日志。”。
- 原七列 `<table>`、`taskTypeFilter`、`taskStatusFilter`、`loadTasksBtn`、`taskPager`、动态 `.task-row/.task-detail-row` 不换模板，不新增列；复用 styles.css:948 的 `.panel`、:962 `.panel-head`、:1259 分页。
- 本轮新增/修改 DOM 映射全集：header/nav badge/topbar → S-1；active section/卡片/分页 → S-2；详情及日志 → S-3；标题/副标题/刷新提示 → S-4。除此以外不新增样式类、wrapper 或 inline style。

## 现状审计

### E-1：自动弹框与轮询所有权

- `src/main/resources/static/app.js:756-778` 的 `resumeProgressPollingIfNeeded()` 遍历 `taskButtonMapping`，给第一个 RUNNING/CANCELLING 调用 openTaskModal，是真实自动弹框根因。
- `app.js:1835-1840`：切回 contacts 调用 resume；切出 contacts 停部分旧 watcher。`app.js:14370`：登录启动也调用 resume。
- `app.js:718-725`：taskButtonMapping 六个键，与 `TaskTypeCatalog.hasProgressUi=true` 当前七项也不完全相同；新增专家类型回填未注册启动按钮。
- `app.js:788-904`：旧 watcher 三秒间隔、launch grace、204 处理与身份隔离；`openTaskModal:930` 接管 watcher；`closeTaskModal:1048` 按状态恢复 watcher。这些是手动控制状态机，不能为了全局展示全部删除。
- `app.js:14376-14402`：stopAuthenticatedApp 关 shell、关各类轮询、清 modal。新观察器必须加入同一退出路径。
- 原自动弹框代码逐字基线：

```javascript
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
    if (firstRunningTask && !currentTaskModal) {
        const { taskType, mapping } = firstRunningTask;
        openTaskModal(taskType, mapping.label, mapping.btnId, { knownActiveAtOpen: true });
    }
}
```

### E-2：task_execution 表（新增读取，不新增写入）

Schema：`task/domain/TaskExecution.kt:9-30`；`V4__create_task_execution.sql:1-15` 定义 id/type/trigger/status、两个 TEXT 请求/结果、success/failure、error_message TEXT、开始/结束/创建/更新时间；`V73` 加 batch_config_id 及外键；`V100` 有 `(status,started_at)`、`(task_type,started_at)`、`started_at` 索引。

写入路径全集（生产代码直接接触 repository 者）：

1. `TaskExecutionService.runAndRecordWithResult:103`：:112 插入 RUNNING；:154 写结果及终态；:168 异常写 FAILED。:126 onStarted 在 try 前，这是既有行为，本轮不修。
2. `TaskExecutionService.runAndRecord:181`：:190 插入；:232 结果终态；:244 异常 FAILED。
3. `ManualInitialOutreachService:330/767` → `TaskExecutionService.updateProgressCounts:80` → `TaskExecutionRepository.updateProgressCounts:118`：执行中更新成功/失败计数和 updated_at。
4. `TaskAuditRetentionService.purge:49` → `TaskExecutionRepository.deleteOlderThan`：按 started_at 分批删除，无运行状态排除；展示必须处理详情 404，不改变清理。
5. 建表/变更 DDL：V4/V73/V100。已应用迁移只读。

封装调用者全集见下方“代码搜索回执”；scheduler、controller、queue consumer 的所有 runAndRecord 调用均落入 1/2，无新业务入口。

读取路径全集：

- `TaskExecutionService:19-41`：四种分页 SELECT+COUNT，供 TaskExecutionController.listExecutions；投影 `TaskExecutionListItem` 不含 request/result TEXT。
- `TaskExecutionService:44-55`：按 id、按配置、按类型最近执行；由 TaskExecutionController 和 BatchSendConfigController 的详情/列表消费。
- `TaskExecutionService:63-90`：配置最近时间、当日成功数、AUTO_REPLY_ALL 最近轮询、定时已执行计数。配置最近时间实际调用来自 `BatchSendTaskConfigService:48/55/124/148`；countScheduledSince 来自 `ExpertDiscoveryScheduler:28`。sumSuccessCountTodayByBatchConfigId 本次生产搜索无外部调用，不能把它当活动展示数据源。
- `TaskExecutionController:59` 查询实际类型计数；:77 读取单条详情，summary extractor、drilldown 使用其结果；:181 recent polls；数字 id 路由调用 service.getExecution。
- `TaskProgressController:93`：按类型读取最近执行（旧方法 SELECT *，本轮不改其历史契约，不复用作高频活动列表）。
- Repository 还声明四个旧 `findAll...OrderByStartedAtDesc` 方法，本次生产调用搜索未找到使用；本轮不清理。

交互点 X-1：所有任务生产者 → DB RUNNING/终态 → 新活动集合/计数。X-2：分页/COUNT 同时有任务开始或结束，二者不是事务快照，允许一轮暂态不一致，下一轮自愈；不得由差集推断终态。

### E-3：TaskProgressStore 内存与 task_progress_log

Schema：Store `ConcurrentHashMap<String,TaskProgress>` 键为类型；取消标记键为 `type:executionId`。TaskProgress immutable data class 含 status、processed/total、message、details/errors、batch、executionId、percentage。`TaskProgressLog` 的 DB id 与 task_execution_id 不同；V22 建表，V35 加 reject reasons，V102 加 created_at 索引。

内存全部写方法：`update:22`、`clear:50`、`clearExecutionContext:57`、`requestCancel:81`、`setCurrentExecutionId:112`、`tryStart:134`、`tryStartWithToken:145`、`bindExecutionId:158`。清理 context 只将 id 置 null，不清 status；pendingToken=-System.nanoTime() 为负。

内存全部读方法：`get:44`、`isRunning:76`、`isCancelled:103/108`、`getCurrentExecutionId:132`；get 缓存为空调用 `restoreFromLog:209`，把历史 RUNNING/CANCELLING 转为 INTERRUPTED（不重新插入内存）。不能把 get 当纯内存活动快照。

日志写路径：

1. `TaskProgressStore.persistProgressLog:186-205` 在 update/requestCancel/tryStart/tryStartWithToken 成功后 repository.save。
2. `bindExecutionId:178` 调用 `rebindPendingExecutionId` 将负 token 日志绑定真实 id；失败 catch WARN。**已实现，不是待修项**（K-progress-log-pending-token-orphan 的旧描述需更新）。
3. `TaskAuditRetentionService:42` 按 created_at 清理日志；已有 V102 索引。**保留机制已存在**（K-progress-log-per-mail-write-amplification 的历史“无清理”不得作为当前事实）。

日志读取全集：Store.restoreFromLog:211；TaskExecutionSummaryExtractor:158 取该执行最后日志补充 running totals；TaskProgressController:70/74 获取日志；BatchSendConfigController:165 获取执行日志、:272 获取最新结果。

进度生产者/绑定清理者：ExpertIndexController、ExpertDiscoveryController、ExpertDiscoveryScheduler、ExpertAcademicEnrichmentWorker、ExpertClassificationAdminController、ExpertClassificationScheduler、MailAutomationController、BatchSendControlService；进度/取消读取者另有 ExpertRevalidationService、ExpertDiscoveryService、ExpertClassificationBackfillService、ManualInitialOutreachService 和 TaskProgressController。具体方法、行号见搜索回执；新 peek 不改任一调用者。

交互点 X-3：负 token → 正 id 绑定 → 任务卡片；清理 context/同类型下一执行不能串读。X-4：SQL 已进入 RUNNING 但 progress 未绑定、或内存已终态但 DB 尚未终态，这两窗口显示“暂无实时进度”，不虚构状态。

### E-4：真实生产者证明，不用演示值做接口假设

- `MailAutomationScheduler:31` 的定时收信类型是 **AUTO_REPLY_ALL**，未设置 CHECK_REPLIES 进度；`MailAutomationController:147-156` 的手动收信才是 CHECK_REPLIES 并 bindExecutionId。
- `ExpertAcademicEnrichmentWorker:95-100` 的自动补全 triggerType 是 **SCHEDULED**，不是预览写的“后台队列”。生产中必须展示“定时触发”，不能凭任务名改 triggerType。
- `MailQueueConsumer:23/41/55` 使用 QUEUE 触发；TaskTypeCatalog.group 只是类型分类，不能代替每次执行的 triggerType。
- `TaskExecutionService:129/154-160` 表明 resultSummary 在 block 返回后写入，不能用它计算运行中的百分比；现有 `TaskExecutionSummaryExtractor:158` 有日志回退，但属于单条详情，不搬到高频列表。
- `TaskTypeCatalog:byCode` 是名称/指标语义源；未知类型回落 code，不能用前端自造名称表填补。

### E-5：前端读取、通知、鉴权

- `loadTasks:10686-10718` 用 page/size 查询记录并整体写 tasksTable；`renderTaskPager:10720` 控制页码；`toggleTaskDetail:10781-10852` 插入 task-detail-row。自动全表刷新会销毁原展开行。
- `handleTaskDrilldownMail:10762` 附近的现有钻取依赖记录行祖先关系，故新卡片详情不搬用其 DOM，只保留原表入口。
- `task-modal-runtime.js:266-282` 的 notifyTaskCompletionOnce 以 type:id 去重并调用 showStatus；旧 watcher、旧 modal 同用。新观察器不另造去重库、不发第二套通知。
- `app.js:1581-1596` api 处理 contextPath、鉴权、HTTP status；全局新 GET 必须复用。`auth/config/AuthWebConfig.kt:23-25` 保护 /api/**；新端点不加入豁免。

交互点 X-5：登录/退出/再登录与迟到响应；X-6：切 tab、活动分页与旧历史筛选/钻取；X-7：用户打开别的弹框期间后台 tick，不得更改 modal。

### E-6：详情与控制

- `TaskExecutionController:75-103` 的 `/{id}/detail` 可用于任何任务类型，响应 totals、rawRequestPayload/rawResultSummary、drilldown，但 `TaskExecutionDetailResponse:366` **不含errorMessage**（数字id旧端点:255才有）；原始内容有截断，不应请求完整历史列表找详情。
- `TaskProgressController:62-82` 的 `/api/task-progress/{type}/logs?executionId=...&batchOnly=true` 已支持指定执行批次去重；不变更 batchOnly 默认值、不把 false 偷换 true。
- 此旧 logs API 返回完整批次列表，无服务端分页。本轮只允许用户手动加载一次（再次点击可刷新），不能挂到每5秒全局轮询；前端最多显示最后50条并说明“仅展示最近50条批次日志”。这限制渲染，不声称已限制网络大小；日志分页后续另立任务。
- `TaskProgressController:53-58` 的 cancel 仅接受 taskType，`TaskProgressStore:81-100` 取消的是此刻该类型当前执行。新详情不能自建按旧 id 取消的假按钮。
- `openTaskModal:962-965` 对 MANUAL_INITIAL_OUTREACH 转到既有批量任务控制台；原控制台仍由用户主动打开。

交互点 X-8：旧执行卡片 → 新同类型执行 → 用户主动控制；X-9：日志/详情请求期间改选、取消选择与保留清理404。

### E-7：前端样式盘点与改动前基线

设计 token 实值：主色 #1e40af、hover #1e3a8a、active #172554；主背景 #f5f7fb；主文字 #1e293b，次文字 #475569，muted #94a3b8；成功 #059669、错误 #e11d48、警告 #d97706；圆角 7/10/18px；主体字体 Inter/系统 sans-serif。styles.css:1-85 是实际来源，不能另造新主题。另有 styles.css:9705 的系统暗色主题：背景#0d1420、文字#e2e8f0、次文字#a8b6c8、primary#3b82f6；新增组件须使用S-0给定暗色覆盖，不能在暗色页面留下白底卡片。

可复用规则与完整现状摘录（路径均为 src/main/resources/static/styles.css）：

`styles.css:133-172`

```css
.app-shell {
    display: grid;
    grid-template-rows: auto minmax(0, 1fr);
    height: 100vh;
}

/* Topnav */
.topnav {
    display: flex;
    align-items: center;
    gap: 20px;
    padding: 10px 24px;
    background: var(--panel-bg);
    backdrop-filter: blur(20px) saturate(1.3);
    -webkit-backdrop-filter: blur(20px) saturate(1.3);
    border-bottom: 1px solid var(--glass-border);
    box-shadow: 0 1px 12px rgba(var(--primary-rgb), 0.06);
    position: relative;
    z-index: var(--z-overlay);
}

.topnav .brand {
    margin-bottom: 0;
    padding: 0;
    flex-shrink: 0;
}

.topnav-side {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 4px;
    flex-shrink: 0;
}

.topnav-side .user-info {
    padding-left: 0;
    margin-right: 8px;
    white-space: nowrap;
}
```

`styles.css:218-259`

```css
.nav-tabs {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 4px;
    min-width: 0;
}

.nav-tab {
    position: relative;
    display: flex;
    align-items: center;
    gap: 8px;
    border: none;
    background: transparent;
    color: var(--text-sidebar);
    padding: 6px 14px;
    border-radius: 999px;
    cursor: pointer;
    font-weight: 500;
    font-size: 13px;
    text-align: left;
    white-space: nowrap;
    transition: background-color 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.nav-tab > svg {
    display: none;
}

.nav-tab:hover {
    background-color: rgba(255, 255, 255, 0.5);
    color: var(--text-main);
}

.nav-tab.active {
    background-color: #ffffff;
    color: var(--primary);
    font-weight: 600;
    box-shadow: 0 2px 8px rgba(var(--primary-rgb), 0.14);
}

```

`styles.css:802-860`

```css
.button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    min-height: 32px;
    height: 32px;
    padding: 0 12px;
    border-radius: var(--radius-sm);
    font-weight: 500;
    font-size: 12px;
    cursor: pointer;
    border: 1px solid var(--border);
    background-color: transparent;
    color: var(--text-main);
    transition: transform 0.12s ease, box-shadow 0.15s ease, background-color 0.15s ease, border-color 0.15s ease, opacity 0.1s ease;
    outline: none;
    user-select: none;
    font-family: var(--font-body);
    position: relative;
    overflow: hidden;
}

.button:hover {
    border-color: rgba(15, 23, 42, 0.2);
    background-color: var(--surface);
    transform: translateY(-1px);
    box-shadow: 0 2px 6px rgba(15, 23, 42, 0.08);
}

.button:active {
    transform: translateY(0) scale(0.97);
    box-shadow: none;
    opacity: 0.85;
}

.button.primary {
    background-image: linear-gradient(180deg, var(--primary-bright), var(--primary));
    background-color: var(--primary);
    border-color: transparent;
    color: #ffffff;
    font-weight: 600;
    box-shadow: 0 1px 2px rgba(var(--primary-rgb), 0.4), inset 0 1px 0 rgba(255,255,255,0.18);
}

.button.primary:hover {
    background-image: linear-gradient(180deg, #2f7bff, var(--primary-hover));
    box-shadow: 0 4px 14px rgba(var(--primary-rgb), 0.35), inset 0 1px 0 rgba(255,255,255,0.18);
}

.button.secondary {
    background-color: var(--primary-light);
    border-color: rgba(var(--primary-rgb), 0.12);
    color: var(--primary);
}

.button.secondary:hover {
    background-color: rgba(var(--primary-rgb), 0.1);
}
```

`styles.css:948-991`

```css
.panel {
    background: var(--panel-bg);
    backdrop-filter: var(--glass-blur);
    -webkit-backdrop-filter: var(--glass-blur);
    border: 1px solid var(--glass-border);
    border-radius: var(--radius-lg);
    box-shadow: var(--glass-shadow);
}

.panel:hover {
    box-shadow: var(--shadow-lg);
    border-color: rgba(15, 23, 42, 0.12);
}

.panel-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 12px 16px;
    border-bottom: 1px solid var(--line);
}

.panel-head h2 {
    padding-left: 0;
}

.panel-head h2::before {
    display: none;
}

.panel-head-actions {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
}

/* Tables */
.table-wrap {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    width: 100%;
}
```

`styles.css:1259-1273`

```css
.list-pager {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    padding: 8px 12px;
    border-top: 1px solid var(--line);
}

.list-pager-info {
    font-size: 12px;
    color: var(--text-muted);
    font-family: var(--font-mono);
}
```

`styles.css:2482-2487`

```css
.button.small {
    height: 26px;
    min-height: 26px;
    padding: 0 8px;
    font-size: 11px;
}
```

任务导航与 toolbar/title 当前 DOM（index.html:137-177）：

```html
            <button class="nav-tab" data-view="tasks">
                <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/>
                    <line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/>
                </svg>
                <span>任务记录</span>
            </button>
        </nav>
        <div class="topnav-side">
            <div class="user-info">
                当前登录: <span id="currentUserDisplay">admin</span>
            </div>
            <button class="nav-tab logout-btn" id="logoutBtn">
                <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
                    <polyline points="16 17 21 12 16 7"/>
                    <line x1="21" y1="12" x2="9" y2="12"/>
                </svg>
                <span>退出登录</span>
            </button>
        </div>
    </header>

    <!-- Main Content Panel -->
    <main class="main">
        <header class="topbar">
            <div>
                <h1 id="viewTitle">邮箱账号</h1>
                <p id="viewSubtitle">维护发送账号、权重、限额和连通性。</p>
            </div>
            <div class="topbar-actions">
                <button class="button secondary" id="refreshBtn">
                    <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 4px;">
                        <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/>
                    </svg>
                    刷新
                </button>
            </div>
        </header>

        <!-- Dynamic Status Bar notifications -->
```

任务记录原 DOM（index.html:988-1032）：

```html

        <!-- View 6: Async Task execution log audit -->
        <section class="view" id="view-tasks">
            <div class="toolbar">
                <select id="taskTypeFilter">
                    <option value="">全部自动化任务</option>
                </select>
                <select id="taskStatusFilter">
                    <option value="">全部执行状态</option>
                    <option value="RUNNING">执行中</option>
                    <option value="SUCCESS">执行成功</option>
                    <option value="PARTIAL_SUCCESS">部分成功</option>
                    <option value="FAILED">执行失败</option>
                    <option value="CANCELLED">已取消</option>
                </select>
                <button class="button primary" id="loadTasksBtn">查询任务执行记录</button>
            </div>

            <section class="panel">
                <div class="panel-head"><h2>定时任务审计与消费日志 (Spring Job Audit)</h2></div>
                <div class="table-wrap">
                    <table>
                        <thead>
                        <tr>
                            <th>审计 ID</th>
                            <th>任务类型</th>
                            <th>触发方式</th>
                            <th>当前状态</th>
                            <th>发信统计/成功数</th>
                            <th>开始时间</th>
                            <th>异常堆栈/错误原因</th>
                        </tr>
                        </thead>
                        <tbody id="tasksTable"></tbody>
                    </table>
                </div>
                <div id="taskPager" class="list-pager" hidden>
                    <button class="button small" id="taskPrevPage">上一页</button>
                    <span id="taskPageInfo" class="list-pager-info"></span>
                    <button class="button small" id="taskNextPage">下一页</button>
                </div>
            </section>
        </section>
    </main>
</div>
```

共享 class 不修改旧规则；仅 S-0 中 `.task-center-nav` 派生选择器覆盖现有单个 header，影响位置已在 S-1 穷举。所有其他新选择器以 task-center / taskActive 命名，只命中新 DOM。

### E-8：测试与资源版本基线

- 当前静态版本键为 `20260920-manual-material-upload`；index.html:11-15 和 :2128-2133 共11项（5 CSS、6 JS）。`task-modal-runtime.js` 没有版本键。
- 实际命令 `rg -n '20260920-manual-material-upload' src/test` 本次 **0 命中**；不能照知识条目旧清单猜必须改9个测试。目前不需要为缓存键扩文件。
- 本次已运行六组现有前端测试，共 **108 tests，108 pass，0 fail**：taskRecordsPaging、taskRecordsSemantics、taskDrilldown、taskModalStateMachine、taskModalLifecycleIntegration、taskModalTwoLevelUi。另已运行authFlow.test.js：19 tests，19 pass，0 fail；累计127项已通过。authFlow:227-228/324-325实际抽取启动/停止函数，新增停止观察器调用必须同步两个sandbox，已明确纳入第9文件。原始输出在本次机器 `/tmp/task-center-baseline-tests.txt` 与 `/tmp/task-center-auth-baseline.txt`，它不是长期验收证据。
- `pom.xml:186-232` 将 node 全套与两个语法检查接入 test phase；verify.sh 不能替代全量 node 测试（K-js-test-invocation-surface）。本次未运行 Kotlin 全套，未声称后端基线通过。
- 当前工作树存在用户的其他代码/知识/版本改动和本次预览。执行者只改本计划九个文件，不 checkout/reset/revert 其他修改。baseline fingerprints 见本节末尾。

```text
3b9449ed2d2034671e7dd68bcfbb788275106990fd747dd9cf82a587b00edb54  src/main/resources/static/app.js
027588b76ac049839c44ec996a98f2932050c65838073ede755b5b17185299fb  src/main/resources/static/index.html
c034256168422a3a9b8f5879c72962851d050929ddab55c6e757447ba7a219c0  src/main/resources/static/styles.css
c56855b01c7329bf5ca185543762d9fc21cac8aa2d68f2e3f70fa0dbfb6d4784  src/main/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStore.kt
6986c4702f60a1b4186c8cc4dbbca8d813d5d6530e5934751a3bd62d0d6c07bb  src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt
```

### 代码搜索回执（本次工作树，覆盖调用者）

说明：以下是检索定位证据，表结构与核心方法已逐段读过；注释命中不充当写入。相对路径均以仓库根为起点。实现前如果相关函数改变，重新审计该交互点。

```text
src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt
  179:getLiveExecutionView→getCurrentExecutionId; 180:getLiveExecutionView→get; 200:cancelExecution→getCurrentExecutionId; 204:cancelExecution→requestCancel; 218:pause→requestCancel; 281:getStatus→get; 337:launchFromSnapshot→tryStartWithToken; 354:launchFromSnapshot→runAndRecordWithResult; 359:launchFromSnapshot→bindExecutionId; 379:launchFromSnapshot→update; 387:launchFromSnapshot→clearExecutionContext; 389:launchFromSnapshot→clearExecutionContext; 398:launchFromSnapshot→update; 403:launchFromSnapshot→clearExecutionContext
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt
  208:runMaterialFromSnapshot→isCancelled; 550:runIntroductionFromSnapshot→isCancelled; 1133:updateProgress→update; 1449:updateProgressWithAccumulator→update
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt
  74:triggerDiscovery→tryStartWithToken; 89:triggerDiscovery→runAndRecord; 93:triggerDiscovery→bindExecutionId; 103:triggerDiscovery→get; 104:triggerDiscovery→update; 117:triggerDiscovery→get; 118:triggerDiscovery→update; 130:triggerDiscovery→clearExecutionContext; 132:triggerDiscovery→clearExecutionContext; 145:triggerDiscoveryByKeyword→tryStartWithToken; 165:triggerDiscoveryByKeyword→runAndRecord; 169:triggerDiscoveryByKeyword→bindExecutionId; 179:triggerDiscoveryByKeyword→get; 180:triggerDiscoveryByKeyword→update; 193:triggerDiscoveryByKeyword→get; 194:triggerDiscoveryByKeyword→update; 206:triggerDiscoveryByKeyword→clearExecutionContext; 208:triggerDiscoveryByKeyword→clearExecutionContext; 223:enrichExperts→tryStartWithToken; 237:enrichExperts→runAndRecordWithResult; 241:enrichExperts→bindExecutionId; 247:enrichExperts→update; 256:enrichExperts→clearExecutionContext; 258:enrichExperts→clearExecutionContext; 260:enrichExperts→get; 262:enrichExperts→clear; 267:enrichExperts→clear
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentWorker.kt
  58:processDueEnrichmentJobs→tryStartWithToken; 72:processDueEnrichmentJobs→clear; 85:runBatch→isCancelled; 95:runBatch→runAndRecordWithResult; 99:runBatch→bindExecutionId; 107:runBatch→update; 116:runBatch→clearExecutionContext; 118:runBatch→clearExecutionContext; 120:runBatch→get; 121:runBatch→clear
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt
  32:scheduleDiscovery→tryStartWithToken; 46:scheduleDiscovery→runAndRecord; 49:scheduleDiscovery→bindExecutionId; 55:scheduleDiscovery→update; 63:scheduleDiscovery→clearExecutionContext; 65:scheduleDiscovery→clearExecutionContext
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt
  333:discover→getCurrentExecutionId; 350:discover→update; 357:discover→update; 376:discover→isCancelled; 394:discover→isCancelled; 408:discover→update; 426:discover→update; 439:discover→update; 565:discoverFromSource→getCurrentExecutionId; 614:persistCheckpoint→isCancelled; 725:persistCheckpoint→isCancelled; 787:persistCheckpoint→update; 861:discoverFromOrcid→getCurrentExecutionId; 900:persistCheckpoint→isCancelled; 1033:persistCheckpoint→update; 1413:sleepInterruptible→isCancelled; 1416:sleepInterruptible→isCancelled; 1421:sleepInterruptible→isCancelled; 1434:enrichExistingExperts→getCurrentExecutionId; 1445:enrichExistingExperts→update; 1452:enrichExistingExperts→update; 1473:enrichExistingExperts→isCancelled; 1484:enrichExistingExperts→isCancelled; 1490:enrichExistingExperts→isCancelled; 1565:enrichExistingExperts→update; 1590:enrichExistingExperts→update; 1609:enrichExistingExperts→isCancelled; 1612:enrichExistingExperts→isCancelled; 1615:enrichExistingExperts→update; 1638:enrichExistingExperts→update; 1661:enrichExistingExperts→update; 1680:enrichExistingExperts→update; 1694:enrichExistingExperts→update; 1727:enrichDiscoveryPendingJobs→update; 1730:enrichDiscoveryPendingJobs→getCurrentExecutionId; 1823:processClaimedEnrichmentJobBatch→isCancelled; 1896:recordEnrichmentBatch→update; 1904:recordEnrichmentBatch→getCurrentExecutionId; 2329:backfillRawEmailsAndPromote→isCancelled; 2334:backfillRawEmailsAndPromote→isCancelled; 2343:backfillRawEmailsAndPromote→isCancelled; 2346:backfillRawEmailsAndPromote→isCancelled; 2348:backfillRawEmailsAndPromote→isCancelled
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminController.kt
  68:startBackfill→tryStartWithToken; 84:startBackfill→runAndRecordWithResult; 90:startBackfill→bindExecutionId; 97:startBackfill→update; 112:startBackfill→clearExecutionContext; 114:startBackfill→clearExecutionContext; 120:startBackfill→update; 132:startBackfill→clearExecutionContext
src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt
  127:revalidateCandidates→tryStartWithToken; 137:revalidateCandidates→runAndRecordWithResult; 141:revalidateCandidates→bindExecutionId; 148:revalidateCandidates→update; 157:revalidateCandidates→clearExecutionContext; 159:revalidateCandidates→clearExecutionContext; 166:promoteEligibleRaw→tryStartWithToken; 176:promoteEligibleRaw→runAndRecordWithResult; 180:promoteEligibleRaw→bindExecutionId; 187:promoteEligibleRaw→update; 196:promoteEligibleRaw→clearExecutionContext; 198:promoteEligibleRaw→clearExecutionContext; 206:backfillOperatorStatus→runAndRecordWithResult; 227:reconcileOperatorStatus→runAndRecordWithResult
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationBackfillService.kt
  198:isCancelled→isCancelled; 223:publishProgress→update
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationScheduler.kt
  77:scheduleIncremental→tryStartWithToken; 90:scheduleIncremental→runAndRecordWithResult; 96:scheduleIncremental→bindExecutionId; 103:scheduleIncremental→update; 118:scheduleIncremental→clearExecutionContext; 120:scheduleIncremental→clearExecutionContext; 126:scheduleIncremental→update; 138:scheduleIncremental→clearExecutionContext
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt
  33:revalidateCandidates→getCurrentExecutionId; 37:revalidateCandidates→isCancelled; 93:revalidateCandidates→update; 106:revalidateCandidates→isCancelled; 109:revalidateCandidates→update; 121:revalidateCandidates→update; 128:revalidateCandidates→update; 142:promoteEligibleRawExperts→getCurrentExecutionId; 147:promoteEligibleRawExperts→isCancelled; 196:promoteEligibleRawExperts→update; 208:promoteEligibleRawExperts→isCancelled; 211:promoteEligibleRawExperts→update; 220:promoteEligibleRawExperts→update; 227:promoteEligibleRawExperts→update
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiQaExtractionScheduler.kt
  19:scheduleExtraction→runAndRecord
src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt
  137:checkReplies→tryStartWithToken; 147:checkReplies→runAndRecordWithResult; 153:checkReplies→bindExecutionId; 167:checkReplies→isCancelled; 169:checkReplies→isCancelled; 188:publishRunning→update; 325:publishRunning→update; 347:publishRunning→update; 357:publishRunning→clearExecutionContext; 359:publishRunning→clearExecutionContext; 364:publishRunning→update; 369:publishRunning→clearExecutionContext
src/main/kotlin/com/weibo/talentintroduction/mail/queue/MailQueueConsumer.kt
  23:handleInitialOutreach→runAndRecord; 41:handleAutoReplyAccount→runAndRecord; 55:handleAutoReplyAllAccounts→runAndRecord
src/main/kotlin/com/weibo/talentintroduction/postmaster/service/PostmasterScheduler.kt
  17:runDaily→runAndRecord
src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressController.kt
  48:getProgress→get; 54:cancelTask→requestCancel; 68:getProgressLogs→getCurrentExecutionId
src/main/kotlin/com/weibo/talentintroduction/task/service/BounceCollectionScheduler.kt
  32:runCollection→runAndRecord
src/main/kotlin/com/weibo/talentintroduction/task/service/DailyCountResetScheduler.kt
  28:runScheduledReset→runAndRecord
src/main/kotlin/com/weibo/talentintroduction/task/service/MailAutomationScheduler.kt
  31:scheduleAutoReplyAll→runAndRecord; 57:scheduleInitialOutreach→runAndRecord; 79:scheduleOperatorStatusSync→runAndRecord; 91:scheduleOperatorStatusReconcile→runAndRecordWithResult
src/main/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionScheduler.kt
  31:scheduleRetention→runAndRecordWithResult
```

知识消费结论：已使用条目见 I-2..I-8 来源；K-circuit-breaker-terminal-status 用于禁止展示层覆写终态；K-execution-detail-running-needs-progress-log 用于禁止运行卡片依赖 resultSummary；K-allowedTaskTypes-whitelist 用于日志按钮的 hasProgressUi 门控；K-batch-console-log-timeline 用于显式批次日志空态。K-manual-outreach-executor-shared、K-task-launch-config-registration 仅作为“不改执行器/不加启动任务”的边界，本轮不引入其改造建议。17个读取条目的 hit_count/last_used 已更新，无条目超过90天阈值；没有达到同主题5条需要合并的集合。

## 实现方案

### T-1：增加活动记录投影与纯内存读取（I-2/I-3/I-4）

精确文件：`task/repository/TaskExecutionRepository.kt`、`task/service/TaskProgressStore.kt`（前缀均 src/main/kotlin/com/weibo/talentintroduction）。

Repository 复用已有 TaskExecutionListItem DTO，增加且仅增加以下两个方法。error_message 是 TEXT 且本视图不消费，因此 SQL 返回 `NULL AS error_message` 以复用已有 nullable 投影属性；三个 TEXT 列都不读取。

```kotlin
@Query("""
    SELECT id AS id, task_type AS task_type, trigger_type AS trigger_type,
           status AS status, success_count AS success_count, failure_count AS failure_count,
           NULL AS error_message,
           started_at AS started_at, finished_at AS finished_at
    FROM task_execution
    WHERE status IN ('RUNNING', 'CANCELLING')
    ORDER BY started_at DESC, id DESC
    LIMIT :size OFFSET :offset
""")
fun findActivePage(size: Int, offset: Long): List<TaskExecutionListItem>

@Query("SELECT COUNT(*) FROM task_execution WHERE status IN ('RUNNING', 'CANCELLING')")
fun countActive(): Long
```

Store 增加纯读方法，不修改 get 或其他读写方法：

```kotlin
/** 当前进程的内存快照；不触发历史日志恢复。 */
fun peek(taskType: String): TaskProgress? = store[taskType]
```

新读取由既有两个 runAndRecord 写路径、updateProgressCounts 与 Store.update/bind 提供；不调整任何生产者。既有 `(status,started_at)` 索引可供筛选，实际执行计划在隔离库验证；不声称无需 filesort，也不在本轮新增索引。

### T-2：只读聚合端点（I-2/I-3/I-4/I-8）

精确新文件：`src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskActivityController.kt`。

采用一个小 controller，注入 TaskExecutionRepository、TaskProgressStore；DTO 放同文件。不要为两个 SELECT 再造一层 service。独立 controller 避免修改现有 TaskExecutionController 构造器和全部直构测试。

`GET /api/task-executions/active?page=0&size=6`：page clamp>=0，size clamp1..50；一次 capturedNow=LocalDateTime.now()，elapsedSeconds=Duration.between(startedAt,now).seconds.coerceAtLeast(0)。日期用与旧 Controller 一致的 `yyyy-MM-dd HH:mm:ss`。total 可因并发与 items 短时不一致，不做事务或假填充。

响应契约：

```json
{
  "items": [{
    "id": 12845,
    "taskType": "EXPERT_ENRICHMENT",
    "taskTypeLabel": "学术数据补全",
    "triggerType": "SCHEDULED",
    "status": "RUNNING",
    "startedAt": "2026-09-22 14:28:32",
    "elapsedSeconds": 226,
    "metricLabel": null,
    "successCount": 0,
    "failureCount": 0,
    "hasProgressUi": true,
    "progress": {
      "status": "RUNNING",
      "processedCount": 126,
      "totalCount": 300,
      "percentage": 42,
      "message": "正在补全专家学术信息"
    }
  }],
  "total": 1,
  "page": 0,
  "size": 6
}
```

- progress 为 nullable DTO；不携带 details/errors/batchRejectReasons 大 Map。message 最多500字符（take），空文本前端回落“正在执行”。未知 total 使用 null percentage；无匹配进度 progress=null。
- metricLabel/taskTypeLabel/hasProgressUi 由 TaskTypeCatalog.byCode 提供；未知 label=code、metricLabel=null、hasProgressUi=false。triggerType 原样，不用 group 替代。
- status 永远是 DB 行状态；前端只在匹配 progress.status=CANCELLING 时把显示标签改“取消中”。progress 进入终态到 DB 保存期间按无进度处理，下一轮记录消失；没有副作用补写。
- 不返回 requestPayload、resultSummary、errorMessage 大字段、完整 TaskProgress；Repository 的 errorMessage 是 null 占位，active DTO不使用它。失败状态与结果来自现有单条detail；错误原因仍在原记录表的错误原因列展示，不能假设detail已经带有errorMessage。
- 此端点遵循既有 auth interceptor，禁止新增匿名路径。

### T-3：常驻观察器，移除自动弹框（I-1/I-5/I-7，S-1/S-2/S-4）

精确文件：`src/main/resources/static/app.js`。

局部状态放 `const taskActivityState = {...}`，至少包含：timer、started、inFlight、sessionGeneration、listRequestSequence、activePage、lastTotal、lastItems、lastSuccessAt、stale、detailId、detailType、detailGeneration。都是内存 UI 状态，不写 localStorage，不建通用 store。

明确函数职责（可加小型纯格式化函数，不能扩成框架）：

- `startTaskActivityPolling()`：幂等启动；以同一 sessionGeneration 首次请求。
- `stopTaskActivityPolling()`：清 timer，generation++，清选中详情、计数、错误提示；旧 finally 不得重新挂 timer。
- `refreshTaskActivity()`：串行 GET；不在 tasks 页时 page=0,size=1，仅需全局总数；tasks 页时 activePage,size=6。本轮采集 view/page/requestSequence，返回后再检查；本次active与必要的已选详情请求都完成后才挂5秒timer。inFlight期间的显式刷新/可见恢复/翻页只设置一个pendingRefresh；finally先校验started与sessionGeneration，若有效且可见则pendingRefresh执行一次立即刷新，否则挂5000ms；不得每个入口各挂timer。
- `renderTaskActivitySnapshot(data)`：计数+卡片按 id 更新。未在 tasks 页仅更新全局入口；不能用 size=1 的后台响应替换任务页六张卡片。
- `renderTaskActivityError()`：按 S-1 保留旧数并标 stale；空态只由成功且 total=0 产生。
- `resumeProgressPollingIfNeeded()`：整体替换为观察器的幂等启动/刷新适配，不遍历 taskButtonMapping，不创建恢复型旧 watcher，不 openTaskModal。其登录/contacts 现有调用点可保留。

接入：

1. `startAuthenticatedApp` 既有 resume 路径启动观察器；`stopAuthenticatedApp` 最前停止观察器；不要让 catch/finally 在 auth 失效后创建新轮询。
2. 页面 visibilitychange 只绑定一次：hidden 清 timer并使listRequestSequence失效，保留隐藏前最后有效快照，不提交隐藏后的迟到响应；visible 在 started=true 时启动一轮（若 inFlight 则设置一次待刷新标记，不能并发）。
3. setView 在 tasks 进入/离开时使列表请求版本失效并立即安排新轮；离开 tasks 收起 active 详情、detailGeneration++。保留原手动 watcher 的其余语义，不篡改 currentTaskModal。
4. 任务全局按钮 click→setView('tasks')，不自动展开第一项、不 reset 原任务记录筛选。翻页使用独立 activePage；“刷新”可手动刷新当前任务记录与 active，当前查询参数仍保留。
5. 如果当前 activePage 超过 maxPage，归到 maxPage 并安排一次新查询；不递归无限 retry。count/items竞争导致暂空，显示“正在刷新任务列表…”并下轮重试，不误标无任务。
6. 卡片集合/状态在已成功快照之后变化时显示 taskHistoryRefreshHint；首次快照不提示。全局后台只拿1条，所以不能据其差集发完成/失败提示；切回任务页由原 refreshCurrentView 获取历史。

不改：executeCheckReplies/executeRevalidation/executeEnrichment/executeRawPromotionScan/executeDeepDiscovery/批量手动入口的 POST、等待与绑定，不改 `task-modal-runtime.js`。这保证已有108项前端状态机回归仍可直接运行。

### T-4：详情与展示落地（I-3/I-6/I-7/I-8，S-1..S-4）

精确文件：`src/main/resources/static/app.js`、`index.html`、`styles.css`。

- 按 S-1..S-4 改静态 DOM；所有新监听注册到现有初始化区，使用 addEventListener/事件委托，不增 inline onclick。CSS 按 S-0逐字追加。只改 tasks 的 viewMeta 副标题。
- 展开 `openTaskActivityDetail(id)`：校验 id 来自当前 active 卡片；保存 detailType 与 id；detailGeneration++；立即请求既有 `/api/task-executions/{id}/detail`。回包三重检查 sessionGeneration/detailGeneration/detailId。
- 原始结果复用 renderTaskDetailRawBlocks。详情状态/耗时以单条详情为准；FAILED时状态说明追加“错误原因可在下方执行记录查看”，不杜撰errorMessage字段；运行中耗时可从该卡片 elapsedSeconds 展示，详情 durationSeconds=null 不能显示0秒。若卡片已离开本页，仅显示 beganAt 和“执行中”，不猜进度。
- 详情轮询不新增第二个 interval：每次 active tick 完成后若 tasks 可见、detailId存在、上次详情状态仍 RUNNING/CANCELLING，则串行刷新一次该 id 的 detail；首次失败保留选择并显示错误，下轮允许恢复。读取到终态停止该详情周期请求，保留选中结果、显示终态色。404保留明确空态并停止详情重试，用户可重新选卡片。
- 批次日志只在 hasProgressUi=true 时显示按钮；点击才调用既有 logs?executionId=id&batchOnly=true，响应 guard 同详情。写最后50条纯文本，无日志/失败分别给明确文案；不定时取全量日志。其他类型详情仍可展开原始数据，日志按钮隐藏。
- “打开任务控制”需 taskButtonMapping 有此类型且最新详情仍运行；点击先按 I-6 查当前进度并核对 id，再调用原 openTaskModal。发生 mismatch 不打开。随后旧控制系统自行负责状态变化与取消确认，本轮不加新的取消 API。
- 收起/离开后 detailGeneration++；卡片按钮 aria-expanded同步。卡片节点被删除时详情仍保留直至用户收起或终态展示，不跳新执行。
- UI时间：开始时间使用后端完整字符串；elapsedSeconds 格式化为 `mm:ss` 或 `hh:mm:ss`，不用 Date.parse 无时区字符串；每轮刷新更新一次，不单独建立计时器。
- triggerType 显示固定：SCHEDULED=定时触发；QUEUE=队列触发；MANUAL=手动触发；MANUAL_ALL=手动全量；MANUAL_SELECTIVE=手动选中；未知原样文本。不能按 taskType 猜触发来源。
- 同时将 index.html 中11个资源缓存键改为 `20260922-task-activity-center`；当前反查无测试写死旧键，若执行时出现新增命中，先记录基线变化并修订文件清单，不私自扩大实现。

### T-5：有针对性的接口与浏览器状态验证（全部 I/S）

精确测试文件：

1. `src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskActivityControllerTest.kt`：同时覆盖 MVC endpoint contract（与旧TaskExecutionController一同注册到测试MockMvc，确认/active没有进入/{id}）、分页边界、未知类型、同类型多执行、SQL注解投影与真实Store.peek纯读行为；使用 real Store + mock log repo 验证空 peek 不查库、不写库。不要只 mock progress 然后断言自己的映射。
2. `src/test/js/taskActivityCenter.test.js`：沿用 node:test/vm 函数装载，但真实 HTML/CSS 的结构/ID/样式合同必须直接读文件验证；fake timer+deferred promise 构造乱序、退出、hidden/visible，不只验证字符串。

3. `src/test/js/authFlow.test.js`（修改）：现有 createSandbox:109 / createBootstrapSandbox:241 都抽取真实 stopAuthenticatedApp，但未提供新stopTaskActivityPolling。因此在两个sandbox增加有调用计数的stub，并断言401、退出、强制改密路径都清理观察器；首次登录和重复start仍保持一次初始化。保留原19项断言，不用生产代码的typeof防御来掩盖测试缺依赖。

除上述已证明需要同步的鉴权测试外，不改既有测试迁就行为。若其他精确抽取测试因新依赖失败，先核对接口与已列文件；需要增加文件则修订计划，不跳过旧套件。

## 变更文件清单

以下是**后续实现唯一清单**。计划文件、既有演示预览、知识条目元数据/纠错是本次规划产物，不算实现文件；实现者不得借此授权修改它们。总数9，未列出文件不属于本轮实现。

| # | 精确路径 | 操作 | 范围 |
|---|---|---|---|
| 1 | src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt | 修改 | 活动分页/COUNT 两个只读查询 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStore.kt | 修改 | 增加纯内存 peek |
| 3 | src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskActivityController.kt | 新增 | GET active 与小 DTO |
| 4 | src/main/resources/static/app.js | 修改 | 观察器、卡片/详情、自动恢复入口、事件与退出接入 |
| 5 | src/main/resources/static/index.html | 修改 | S-1..S-4 DOM、11项同键 |
| 6 | src/main/resources/static/styles.css | 修改 | 仅追加 S-0 合同块 |
| 7 | src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskActivityControllerTest.kt | 新增 | 后端查询/DTO/peek/MVC验证 |
| 8 | src/test/js/taskActivityCenter.test.js | 新增 | 状态隔离、DOM/CSS、非打扰验证 |
| 9 | src/test/js/authFlow.test.js | 修改 | 两个sandbox接入观察器停止依赖，鉴权清理回归 |

不改：TaskExecutionService、TaskExecutionController、TaskProgressController、TaskTypeCatalog、task-modal-runtime.js、所有 scheduler/worker/业务 controller、数据库迁移、pom.xml、docs/releases.json。不重置用户现有未提交修改。

## 验收标准

### 机器验收矩阵

| 编号 | 必须断言的结果 |
|---|---|
| I-1 | 登录恢复、contacts往返、定时tick、收到新active行，所有 modal-open/openTaskModal/openBatchSendTaskModal spy 调用数均0；已打开业务弹框上下文不变 |
| I-2 | 两条同类型id都返回；未知类型不丢；page/size/Long offset准确；total=13,size=6时全局13、当前最多6；翻页离页不发完成通知 |
| I-3 | 匹配正id显示42%；负token/null/另一次id/终态progress返回null；total=0百分比null；不同任务绝不共用消息；clearExecutionContext后不拿旧状态作实时值 |
| I-4 | peek无logRepo交互；active只有2次repo只读调用，无save/update/delete/extractor；SQL有LIMIT/OFFSET、不含SELECT *及两个大TEXT；消息长度<=500；auth未豁免 |
| I-5 | fake timer重复start只1条链；未resolve不并发；旧响应在翻页/退出/新登录后不能写UI；隐藏不继续请求、可见只补1次；500响应保留旧数而非0 |
| I-6 | 展开A后B先返回、A后返回仍显示B；收起/退出不复活；同类型替换后control不得打开；当前匹配用户点击只打开一次；日志指定准确executionId |
| I-7 | tick不调用loadTasks，不重置筛选/page、不删除原展开行；终态详情保留并停止轮询；自动集合变化只显示刷新入口；既有通知去重108项测试全绿 |
| I-8 | `<img onerror=...>`/引号/换行作为纯文本；没有新style属性/onclick；11项版本键相同；生产脚本不含12846演示fixture |
| S-0 | 从本计划CSS fenced block与styles.css标记间取全文，统一换行后逐字相同；追加块之外旧styles.css字节不变 |
| S-1 | 固定ID真实存在且唯一；badge不嵌套button；nav保留十入口和退出；stale文案/aria状态覆盖 |
| S-2 | 3/2/1列断点规则精确；原生progress的value，不写style.width；未知进度不显示条；独立活动分页不影响tasksPage |
| S-3 | 单个页内section，无overlay；日志textContent；详情generation和close焦点行为；所有按ID访问的节点存在于真实index.html |
| S-4 | 七列表格/既有筛选和分页ID保留；历史loadTasks与toggleTaskDetail行为和钻取不变 |

跨路径测试场景：X-1/2 DB活动列表与并发分页；X-3/4 token绑定/清理/同类多执行；X-5鉴权迟到；X-6历史与活动独立分页；X-7业务弹框保持；X-8控制前id重核；X-9日志乱序与404。

测试命令（实现后执行；本次实测108项任务测试及19项鉴权测试，共127项基线）：

```bash
node --check src/main/resources/static/app.js
node --test src/test/js/taskActivityCenter.test.js src/test/js/authFlow.test.js src/test/js/taskRecordsPaging.test.js src/test/js/taskRecordsSemantics.test.js src/test/js/taskDrilldown.test.js src/test/js/taskModalStateMachine.test.js src/test/js/taskModalLifecycleIntegration.test.js src/test/js/taskModalTwoLevelUi.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TaskActivityControllerTest,TaskExecutionListPagingTest,TaskProgressStoreTest,TaskProgressStoreRebindTest,TaskProgressControllerTest,TaskProgressControllerExecutionsTest -DskipNodeTests=true
node --test src/test/js/*.test.js
```

- 完成上述有针对性检查后做正常项目构建门禁；若完整构建受其他WIP影响，保留具体失败证据，不改白名单外文件，不宣称全部通过。
- 只在隔离测试库对 T-1 SELECT 跑 EXPLAIN 验证实际扫描/索引；不以 mocked repository 测试替代性能证据。无生产访问时标明尚未验证，不凭历史索引定义声称耗时。
- 浏览器验证使用真实 index/app/styles，不使用预览的 preview.js 替代产品代码；截图保存作执行报告证据。1440、1024、760、390四档宽度，真实DOM空态/长文本/多任务/详情均核对。

## 人工验收清单

当前不生成 acceptance.md。人工验收开始时从本节导出同目录 `task-activity-center-acceptance.md`，逐项加勾选框、验收人、日期与备注；改验收要求先改本节再重新导出。

测试数据构造约定：只在隔离测试实例，禁止在生产插RUNNING记录或试发邮件。A-1/2/3/5/6用下面 SQL 创建确定数量的记录（无需真实调度），用于验证“按记录状态展示”和无进度降级；A-4/8用真实非发信任务验证内存进度。记录身份由查询 id 获取，不写死本地截图中的编号。

```sql
-- 只在一次性测试库执行；先确认该测试实例没有其他 RUNNING/CANCELLING 记录。
INSERT INTO task_execution(task_type,trigger_type,status,request_payload,result_summary,started_at)
VALUES ('AUTO_REPLY_ALL','SCHEDULED','RUNNING','{"fixture":"task-center-A"}',NULL,NOW()),
       ('EXPERT_ENRICHMENT','SCHEDULED','RUNNING','{"fixture":"task-center-B"}',NULL,NOW()),
       ('PREVIEW_UNKNOWN_TASK','QUEUE','RUNNING','{"fixture":"task-center-C"}',NULL,NOW());
SELECT id,task_type FROM task_execution WHERE request_payload LIKE '%task-center-%' ORDER BY id;
-- 终态场景：用上一步得到的该条测试记录id替换 <fixture_id>，不能按全表类型批量更新。
UPDATE task_execution SET status='FAILED',error_message='测试数据源超时',finished_at=NOW()
WHERE id=<fixture_id>;
```

### A-1：登录/刷新不自动弹框
- 前置条件：隔离库用上面的SQL得到3条RUNNING，关闭真实调度；当前没有其他执行记录状态为运行中。
- 操作步骤：1. 登录；2. 浏览器刷新；3. 专家列表→邮箱账号→专家列表往返两次；4. 等待一次5秒刷新。
- 预期结果：始终无主动任务弹框、无遮罩；nav显示3，右上显示“3 个任务执行中”；当前页面保持专家列表。
- 覆盖：O-1，I-1/I-2/I-5，S-1，X-1/X-7。

### A-2：运行卡片和未知类型不漏
- 前置条件：A-1的三条记录。
- 操作步骤：1. 点击常驻任务入口；2. 查看三张卡片；3. 展开未知类型卡片。
- 预期结果：页面为任务记录；三个执行id分别可见，未知任务显示原code；自动补全显示“定时触发”，未知记录显示“队列触发”；因无匹配内存进度，三张卡片均为“记录为执行中，暂无实时进度”，不显示0%进度条；未知详情可打开，不显示日志/控制按钮。
- 覆盖：O-2，I-2/I-3，S-2/S-3，X-1/X-4。

### A-3：集合减少与历史不被重置
- 前置条件：A-2；历史筛选选全部类型/执行中，并展开一行；另打开该活动执行的页内详情。
- 操作步骤：1. 使用给定UPDATE将选中的fixture行改FAILED；2. 等待下一轮；3. 点击“任务状态有变化，刷新执行记录”。
- 预期结果：计数3→2；该卡片移除；页内详情保留原id并显示失败；全局不会弹通知/弹框；步骤2原历史行展开不被自动删除；步骤3才按当前筛选刷新，失败行不再属于“执行中”筛选。改选“执行失败”可见“测试数据源超时”。
- 覆盖：O-2，R-2/R-4，I-2/I-6/I-7，S-4，X-1/X-2/X-6。

### A-4：真实进度与未知总数
- 前置条件：隔离测试实例具备可运行的非发信学术补全任务和足够处理数据；任务服务可访问测试数据源。开启浏览器Network。
- 操作步骤：1. 手动启动补全并主动关闭控制弹框；2. 切入任务记录；3. 检查active响应中此id的progress；4. 等待处理推进。
- 预期结果：只有id相等的卡片显示该message/processedCount；百分比严格等于响应percentage（若totalCount=0则显示“进度未知”且无条）；不得出现另一次执行的计数。关闭弹框后任务仍推进。
- 覆盖：O-2，R-1/R-3，I-3，S-2，X-3/X-4。pendingToken与精确乱序窗口由机器矩阵I-3补足，不要求人抢毫秒窗口。

### A-5：翻页、同类型多执行
- 前置条件：在隔离库按前述INSERT方式再插入10条带不同fixture标记的RUNNING（允许重复taskType），总计13条；没有其他运行记录。
- 操作步骤：1. 进入任务页；2. 活动区下一页；3. 再下一页；4. 返回第一页。
- 预期结果：全局计数始终13；活动三页分别6/6/1条，每个id独立；历史区页码与筛选不变；翻页没有任何完成/失败通知。相同started_at时id降序稳定。
- 覆盖：I-2/I-4/I-7，S-2/S-4，X-2/X-6。

### A-6：断网、隐藏页、退出
- 前置条件：A-1运行数量3且已经显示；浏览器Network可切Offline。
- 操作步骤：1. Offline后等待一轮；2. 恢复网络；3. 将浏览器页隐藏10秒后返回；4. 请求进行中退出登录，再登录。
- 预期结果：失败时保留3并显示“3 个任务 · 更新失败”，不显示无任务；恢复后去掉stale；隐藏后不新增周期请求；恢复只立即补一轮，未完成时无并发轮询；退出后无新active请求且旧响应不复活计数，重新登录只有一条观察链。
- 覆盖：R-4，I-5，S-1，X-5。

### A-7：详情、日志与迟到响应
- 前置条件：至少两条活动fixture；Network设置慢速，另有一条带真实批次日志的测试执行。
- 操作步骤：1. 展开A立刻展开B；2. 收起；3. 对带日志执行点击“加载批次日志”；4. 切离任务页；5. 在测试库删除某fixture之前先记住id，再请求其详情（仅一次性库）。
- 预期结果：A不会覆盖B，收起后不自动出现；logs请求携带所选executionId和batchOnly=true，仅点击触发、不随每5秒刷新；超过50条时只展示最后50并标注；切离停止详情更新；不存在记录显示“执行记录不存在或已被清理”，不跳其他执行、不每轮重试。
- 覆盖：I-4/I-6/I-8，S-3，X-9。

### A-8：手动控制回归与身份核对
- 前置条件：隔离实例开启一个可取消的非发信补全任务A；另准备同类型下一次任务B。
- 操作步骤：1. 在任务详情点击“打开任务控制”；2. 检查原控制弹框并关闭；3. A结束、B启动后在保留的A详情尝试控制（若按钮已隐藏则记录此结果）；4. 使用原业务入口对B操作暂停/取消并确认。
- 预期结果：步骤1在id匹配时由用户点击打开原控制；步骤3不打开B控制，显示替代/结束提示或按钮已隐藏；步骤4沿用原确认和最终状态，关闭弹框不取消任务。没有新POST取消接口。
- 覆盖：R-1/R-3，I-1/I-6，X-8。

### A-9：历史钻取与批量控制台保持
- 前置条件：隔离实例有一条已关联邮件的批量执行、一条关联专家的检查回复执行；使用已有测试邮件或禁用真实外发的测试账号，不为验收向真实专家发送邮件。
- 操作步骤：1. 在原任务表展开对应历史行；2. 分别点击邮件/专家钻取；3. 从原“批量发送”入口进入配置和日志控制台；4. 切页后返回任务记录。
- 预期结果：钻取仍限定所选executionId或专家id；原批量配置、手动执行和日志入口存在；后台计数仍可见且不抢弹框；七列表格/50条分页保持。
- 覆盖：R-1/R-2/R-3/R-4，I-7，S-4，X-6/X-7。

### A-10：样式与缓存
- 前置条件：实现后的真实页面，运行卡片至少3条；浏览器可调整1440/1024/760/390宽度。
- 操作步骤：1. 逐档查看；2. 键盘Tab聚焦常驻入口、详情、日志、分页；3. 检查Network资源版本；4. 比对计划S-0与页面Computed样式；5. 在浏览器中模拟系统暗色主题。
- 预期结果：对应3/2/1/1列；卡片边框#dce5f3、圆角14px、桌面内边距18px、间距14px，<=760内边距13px；主文字14px、说明11/12px；focus蓝色2px；新内容无页面水平溢出，原历史table在其table-wrap内部横向滚动。导航在窄屏沿用横向滚动，任务入口可达；用户名和退出不被删除；11个资源键同为20260922-task-activity-center，无新增资源数量漂移；暗色卡片背景rgba(21,31,48,0.85)、主文字#e2e8f0，原任务记录表仍沿用原暗色主题。
- 覆盖：O-1/O-2，I-8，S-0..S-4。

### A-11：空态、长文本与记录状态边界
- 前置条件：隔离实例全部fixture改为终态；之后再插入一条message缺失、未知类型且名称含HTML样文本的测试记录；不修改真实数据。
- 操作步骤：1. 查看无运行记录；2. 新增fixture后刷新；3. 在没有内存进度的情况下重启测试服务再查看。
- 预期结果：成功total=0时计数入口隐藏、显示“暂无执行中的任务”；新行的文本原样显示、不执行脚本；重启后若DB仍RUNNING则仍按记录显示，明确无实时进度，不标“任务已恢复”或猜测失败；不把DTO无法获取误当成0。
- 覆盖：I-2/I-3/I-5/I-8，S-1/S-2，X-4。

### 规划自检

- [x] 核心写/读路径、schema、旧恢复与自动弹框均有代码证明。
- [x] 每个新增展示状态、DTO语义有不变量；不新建持久化状态。
- [x] 9文件/2子系统/0持久字段；无“相关文件”等不封闭范围。
- [x] 每个新DOM映射S-1..S-4，完整CSS逐字给出，复用与响应式边界明确。
- [x] X-1..X-9均有对应机器/人工场景；R-1..R-4均有回归场景。
- [x] 127项既有前端基线通过；后端与生产性能没有未经运行的通过声明。
- [x] 知识已消费、5条陈旧事实已纠正、新增1条执行身份知识；未把演示截图当业务事实。未发现agents/或templates/角色入口，通用约束以CLAUDE.md一行引用级联；已超过推广阈值的缓存/测试条目原本已有引用，不重复推广。
- [x] 只创建计划，不实施产品代码、不部署、不创建验收衍生文件。
