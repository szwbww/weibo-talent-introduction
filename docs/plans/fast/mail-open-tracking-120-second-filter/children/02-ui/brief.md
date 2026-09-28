# Fast-P Child Brief — 02-ui（120 秒阈值：监控页文案与 badge）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-09-28/mail-open-tracking-120-second-filter.md`，identity `commit:3237f07e694565bda5e0e2d6a453fc654d695014`。
- 本 child 的批准计划 = 同一份计划文件的「实现方案 / 阶段 2」task 4-6 + 「样式契约」S-1/S-2 + 「关键不变量」I-3/I-5 + 验收标准 I-5/S-1/S-2（完整合同，必须先通读全文）。
- Worktree / branch / `child_base_sha`：见派发消息。
- 依赖：`01-backend`（已在其 Code head 上；后端仅改判定口径，JSON 字段与端点不变）。`child_base_sha` 由派发消息给出，等于 `01-backend` 的 Code head。
- 上游已完成事实：`01-backend` 落地单一 120 秒谓词（`DATE_ADD(m.sent_at, INTERVAL 120 SECOND)` / `t.last_open_at > CUTOFF`），列表/筛选/总数/汇总/详情共用；四门轻量验证 LIGHT_PASS（MySQL IT 6/0、定向 47/0）。本 child 只改文案与缓存键，不改后端。

## 全局约束

1. 只允许修改「Authorized Files」三个文件；不得新建文件，不得改 `styles.css`（计划 S-1/S-2：CSS 零差异），不得改任何 Kotlin、迁移或其它 JS。
2. 不得修改 `docs/plans/**`、不得写 `docs/plans/fast/**`（fast-p 证据由控制方提交）。
3. 不新增/删除 DOM 节点、class 或 inline style；只改既有元素文字与既有 badge 色类；`OPENED` 用 `info`（不得改成代表确认成功的 `ok`）。
4. 不改 API 字段名/参数值（`OPENED`/`NO_SIGNAL`/`NOT_TRACKED`、`firstOpenAt`/`lastOpenAt`、`pageSize`/`pageOffset`/`status`/`keyword`/`senderAccountCode`），不改详情请求竞态控制、转义、分页逻辑。
5. 提交格式：`feat(fast-p): implement 02-ui`；fast-p 报告/日志排除在该提交之外。
6. 本 worktree 基线：`index.html` 11 处缓存键 `?v=20260926-discovery-repair`（`:11-15`、`:2310-2315`）；`src/test` 内当前无该字面量引用；`node --test src/test/js/mailOpenTracking.test.js` 基线 8 tests / 8 pass。

## Authorized Files（3）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/resources/static/index.html` | `:274-283` 静态指标/说明/筛选项/表头文字；11 处资源缓存键统一 bump |
| 2 | `src/main/resources/static/app.js` | `openTrackingStatusLabels` 与动态指标、详情字段标题文案/色类 |
| 3 | `src/test/js/mailOpenTracking.test.js` | 沙箱 `openTrackingStatusLabels` 固定值同步；新增文案/结构断言 |

## 逐字文案合同（I-5 / S-1 / S-2，禁止意译）

- 状态：`OPENED → 疑似打开（120秒后请求）`（badge class `badge info`）；`NO_SIGNAL → 无120秒后请求`（`badge warn`）；`NOT_TRACKED → 未跟踪`（基础 badge，tone 为空串）。
- 指标：`跟踪发出`（不变）、`120秒后请求`、`120秒后请求率`；静态初始 DOM 与 `app.js` 动态渲染两处必须逐字一致。
- 原始时间标签：表头与详情 `dt` 均为 `首次图片请求`、`最近图片请求`。
- 说明段（`.muted`，逐字，含标点）：「发送后120秒内的图片请求按预加载处理；之后的新请求仅表示疑似打开，不等于本人已读。图片可能被缓存，重复打开不一定产生新请求。指标按发送日期和发件账号统计；状态与搜索只影响列表。」
- `#motStatus` 选项文案：`全部` / `疑似打开（120秒后请求）` / `无120秒后请求` / `未跟踪`；`option value` 保持 `ALL/OPENED/NO_SIGNAL/NOT_TRACKED`。
- 表格仍是 9 列，顺序：发送时间、专家、收件邮箱、发件账号、主题、跟踪状态、首次图片请求、最近图片请求、操作。
- 不得残留旧文案：`已收到打开信号`、`打开信号率`、`首次信号`、`最近信号` 在 `index.html` 与 `app.js` 中均不得再出现（`app.js` 内 `OPENED` 的旧 tone `ok` 同理）。
- 其它既有文案/结构不动：跟踪开关行、`#motConfigStatus`、`#motError`、分页控件、详情头部与其它字段（邮件记录/主题/状态/收件邮箱/发送时间/Message-ID）。

## 缓存键（计划 task 4，K-frontend-cache-key-triad）

- 把 `index.html` 全部 11 处 `?v=20260926-discovery-repair` 统一改为 `?v=20260928-mail-open-120s`（`styles.css`、`expert-materials.css`、`mailbox-chat.css`、`meeting-confirmation.css`、`world-clock.css`、`trust-reply-workbench.js`、`expert-materials.js`、`meeting-confirmation.js`、`mailbox-chat.js`、`app.js`、`world-clock.js`）。
- 执行前用当前键对 `src/test` 复核固定键测试（计划回执：当前无命中）。若出现新的字面量测试，**停在本计划范围内**、不要扩文件，返回 `BLOCKED`；不得顺手改无关测试。

## 必需测试（计划 task 6）

`src/test/js/mailOpenTracking.test.js`：

- 同步沙箱内 `openTrackingStatusLabels`（`OPENED: ["疑似打开（120秒后请求）", "info"]`、`NO_SIGNAL: ["无120秒后请求", "warn"]`、`NOT_TRACKED: ["未跟踪", ""]`）。
- 新增逐字断言：`index.html` 源文本含 S-1 的三张指标卡标签、说明段原句、四个 `option` 文案、9 个表头；`app.js` 渲染的动态指标标签、列表 badge（`badge info` + 新文案、`badge warn` + 新文案）、详情 `首次图片请求`/`最近图片请求`。
- 断言旧文案不再出现在 `markup`/`source`（`已收到打开信号`、`打开信号率`、`首次信号`、`最近信号`）。
- 保留既有全部用例（竞态、分页、转义、设置、DOM id 存在性断言）不得削弱。

## 必需命令（fresh 运行，逐条记录 exit code 与计数）

```sh
node --test src/test/js/mailOpenTracking.test.js
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
```

## 下游接口

- 无下游 child；API 契约与后端（child `01-backend`）保持一致：仅 `trackingStatus`/`summary.opened` 的口径由 120 秒候选决定，前端只负责显示 120 秒语义，不改字段。

## 交付

- 执行报告写入 `docs/plans/fast/mail-open-tracking-120-second-filter/children/02-ui/execution.md`（报告不进实现提交）。
- 只返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不得修复授权文件外问题、不得重构相邻代码、不得 push/merge/amend/squash，不得改计划。
