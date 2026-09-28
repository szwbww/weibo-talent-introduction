# 02-ui 执行报告（120 秒阈值：监控页文案与 badge）

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mail-open-tracking-120-second-filter/docs/plans/2026-09-28/mail-open-tracking-120-second-filter.md`
- Plan SHA-256: `8b2f24faaa1a11e741c8b3ca9407ee6cfb89cf52ae9c0a1f5467cd62db4693c1`（`scripts/plan_identity.py`，size 32978 bytes）
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mail-open-tracking-120-second-filter/docs/plans/2026-09-28/mail-open-tracking-120-second-filter.md@8b2f24faaa1a11e741c8b3ca9407ee6cfb89cf52ae9c0a1f5467cd62db4693c1`
- Execution epoch: NEW（brief 已存在，但本 child 此前无同 identity 的执行证据）
- Approval basis: 派发消息 + `children/02-ui/brief.md`（master plan identity `commit:3237f07e694565bda5e0e2d6a453fc654d695014`；该提交内容与工作区计划文件同 blob `47923d42d398067e25e0610eec14287f068b863c`，计划字节未变）
- Executor: `Impl02Ui`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mail-open-tracking-120-second-filter`
- Target branch: `fast/mail-open-tracking-120-second-filter`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mail-open-tracking-120-second-filter@fast/mail-open-tracking-120-second-filter@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-mail-open-tracking-120-second-filter`
- Pre-execution code SHA: `15cef3dc07687ae916412f7ff1a8b912077a5889`（HEAD，也是 `01-backend` 证据提交后的 HEAD；`child_base_sha` = `84560652c3227cf95f50ddd12bd285c54ece96cb` 为其祖先）
- Post-execution code SHA: `e0b002076b92d3e1e543040fb5640585fc2869aa`
- Evidence HEAD: N/A（fast-p 证据提交由控制方执行，本 child 不提交报告）
- Implementation boundary: `15cef3dc07687ae916412f7ff1a8b912077a5889..e0b002076b92d3e1e543040fb5640585fc2869aa`

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| 计划 task 4：`index.html` 静态指标/说明/筛选项/表头文案（S-1、I-5） | IMPLEMENTED | `src/main/resources/static/index.html` | `:274`、`:275`、`:277`、`:283` 逐字替换；新断言 test 9 |
| 计划 task 4：11 项资源缓存键统一 bump（K-frontend-cache-key-triad） | IMPLEMENTED | `src/main/resources/static/index.html` | `:11-15`、`:2310-2315` 共 11 项 → `?v=20260928-mail-open-120s`；旧键残留 0；新断言 test 11 |
| 计划 task 5：`app.js` badge 色类与动态指标/详情文案（S-2、I-5） | IMPLEMENTED | `src/main/resources/static/app.js` | `:14266-14267`（`info`/`warn`）、`:14337-14338`、`:14378`；新断言 test 10 |
| 计划 task 6：`src/test/js/mailOpenTracking.test.js` 沙箱固定值同步 + 新增逐字断言 | IMPLEMENTED | `src/test/js/mailOpenTracking.test.js` | `:56` 同步；新增 test 9/10/11；既有 8 用例全部保留 |
| I-3：API 字段名/参数值、竞态、转义、分页不变 | IMPLEMENTED | 同上 | `git diff` 无字段名/参数/逻辑改动；既有用例 1-8 全绿 |
| S-1/S-2：无新增 DOM 节点/class/inline style，`styles.css` 零差异 | IMPLEMENTED | 同上 | `git diff 15cef3dc HEAD -- src/main/resources/static/styles.css` = 0 行；diff 仅文本与 `?v=` |

### Commands

| Command | Result | Evidence |
|---|---|---|
| `node --test src/test/js/mailOpenTracking.test.js`（改动后、提交后 fresh） | PASS | exit 0；`tests 11 / pass 11 / fail 0`（变更前基线：exit 0；`tests 8 / pass 8 / fail 0`） |
| `node --test src/test/js/*.test.js` | PASS | exit 0；`tests 1202 / suites 236 / pass 1202 / fail 0` |
| `node --check src/main/resources/static/app.js` | PASS | exit 0（无输出） |
| 反证（falsification）：把 `app.js`/`index.html` 换成 `child_base_sha` 版本，跑同一份新测试 | 预期失败 | exit 1；`tests 11 / pass 9 / fail 2`——`static markup pins the 120-second copy…`、`badges, dynamic metrics and detail labels…` 失败，即新断言确实能抓住旧文案 |
| `git diff 15cef3dc HEAD -- src/main/resources/static/styles.css \| wc -l` | PASS | `0` |
| `git show --name-only HEAD` | PASS | 恰为 3 个授权文件 |
| `grep -c '20260928-mail-open-120s' src/main/resources/static/index.html` | PASS | `11` |
| 旧键/旧文案检索（`20260926-discovery-repair\|已收到打开信号\|打开信号率\|首次信号\|最近信号\|暂无打开信号` 于 `index.html`+`app.js`） | PASS | 无匹配 |
| `git check-ignore`/`git ls-files` 复核测试侧固定键 | PASS | `src/test` 内含 `20260926-discovery-repair` / `20260928-mail-open-120s` 字面量均为 0（既有缓存键测试均从 `index.html` 现键动态取值，故无需扩文件、未触发 BLOCKED） |

### Changed Files

- `src/main/resources/static/index.html` — 静态文案（指标卡标签、说明段、状态 option、表头）与 11 项缓存键；除文本外无结构性改动。
- `src/main/resources/static/app.js` — `openTrackingStatusLabels` 文案/色类、动态指标标签、详情两个时间字段标题。
- `src/test/js/mailOpenTracking.test.js` — 沙箱固定值同步 + 3 个新用例（共 +51/-1 行）。

## 逐文件改动

### 1. `src/main/resources/static/index.html`

| 行 | 变更前 | 变更后 |
|---|---|---|
| `:274` | `<div class="metric-label">收到打开信号</div>`、`<div class="metric-label">打开信号率</div>` | `<div class="metric-label">120秒后请求</div>`、`<div class="metric-label">120秒后请求率</div>`（`跟踪发出` 不变） |
| `:275` | `<p class="muted">打开信号表示图片被加载，不等于本人已读。指标按发送日期和发件账号统计；下方状态与搜索只影响列表。</p>` | `<p class="muted">发送后120秒内的图片请求按预加载处理；之后的新请求仅表示疑似打开，不等于本人已读。图片可能被缓存，重复打开不一定产生新请求。指标按发送日期和发件账号统计；状态与搜索只影响列表。</p>` |
| `:277` | `OPENED` option `已收到打开信号`、`NO_SIGNAL` option `暂无打开信号` | `OPENED` option `疑似打开（120秒后请求）`、`NO_SIGNAL` option `无120秒后请求`；`value` 仍为 `ALL/OPENED/NO_SIGNAL/NOT_TRACKED` |
| `:283` | `<th>首次信号</th><th>最近信号</th>` | `<th>首次图片请求</th><th>最近图片请求</th>`（仍 9 列，顺序不变） |
| `:11-15`、`:2310-2315` | 见下节缓存键 | 见下节缓存键 |

未改动：跟踪开关行（`#motEnabled`/`#motSettingStatus`/`#motSettingsRetry`）、`<p class="muted">适用于所有非回复外发邮件…新信号记录，历史保留。</p>`（`agents` 约定外文本，brief 明确“其它既有文案不动”）、`#motConfigStatus`、`#motError`、`#motPagination`、`#motDetail` 骨架、`task-modal-runtime.js`（无版本键）。未新增/删除元素、class、inline style。

### 2. `src/main/resources/static/app.js`

| 行 | 变更前 | 变更后 | 理由 |
|---|---|---|---|
| `:14266` | `OPENED: ["已收到打开信号", "ok"]` | `OPENED: ["疑似打开（120秒后请求）", "info"]` | I-5/S-2：文案逐字替换；tone 必须是既有 `.badge.info`（不得用代表确认成功的 `ok`） |
| `:14267` | `NO_SIGNAL: ["暂无打开信号", "warn"]` | `NO_SIGNAL: ["无120秒后请求", "warn"]` | I-5/S-2：仅文案，色类保持 `warn` |
| `:14337` | `["收到打开信号", snapshot.summary.opened]` | `["120秒后请求", snapshot.summary.opened]` | S-1/S-2 与静态首屏指标卡逐字一致；字段 `summary.opened` 未动 |
| `:14338` | `["打开信号率", …openSignalRate…]` | `["120秒后请求率", …openSignalRate…]` | 同上；`openSignalRate` 字段名与 `—` 兜底逻辑未动 |
| `:14378` | `["首次信号", row.firstOpenAt…], ["最近信号", row.lastOpenAt…]` | `["首次图片请求", …], ["最近图片请求", …]` | I-3/S-2：原始时间与推断状态分离，字段名 `firstOpenAt`/`lastOpenAt` 未动 |

未改动：`renderOpenTrackingRecords` 转义与 9 列结构、`loadOpenTrackingRecords` 的 `requestSeq` 竞态与 `pageSize/pageOffset/status/keyword/senderAccountCode`、`loadOpenTrackingDetail` 的正整数校验与 `detailSeq`、`renderOpenTrackingDetail` 其余字段（邮件记录/主题/状态/收件邮箱/发送时间/Message-ID）。

### 3. `src/test/js/mailOpenTracking.test.js`

变更前 8 用例（`settings`、快照隔离/编码、标签页与详情竞态、转义与 ID 校验、末页重查、设置读/写竞态、查询控件重置、详情丢弃晚到响应）全部保留、未削弱。

同步（`:56`）：沙箱内 `openTrackingStatusLabels` 固定值 → `OPENED: ["疑似打开（120秒后请求）", "info"]`、`NO_SIGNAL: ["无120秒后请求", "warn"]`、`NOT_TRACKED: ["未跟踪", ""]`。

新增三个用例：

1. `static markup pins the 120-second copy and drops every stale signal label`（同步断言，无 DOM 依赖）
   - `markup` 逐字含 `motMetrics` 三卡整块（`跟踪发出` / `120秒后请求` / `120秒后请求率`）——证明静态首屏与 S-1 骨架逐字一致，含 `id`/`class`/`aria-live` 未被改动。
   - `markup` 逐字含整个说明段 `<p class="muted">发送后120秒内的图片请求按预加载处理；…状态与搜索只影响列表。</p>`——I-5 要求说明写出“疑似”与图片缓存限制。
   - `markup` 逐字含 `#motStatus` 四个 option 与 `value` 组合——防止只改文案不改回退映射（`ALL/OPENED/NO_SIGNAL/NOT_TRACKED` 必须保持英文原串）。
   - `markup` 逐字含 `<thead>…</thead>` 9 个表头（含 `首次图片请求`/`最近图片请求`）——证明仍是 9 列且顺序不变。
   - `source` 正则断言 `OPENED→"疑似打开（120秒后请求）"/"info"`、`NO_SIGNAL→"无120秒后请求"/"warn"`、`NOT_TRACKED→"未跟踪"/""`；并断言 `OPENED` 不再带 `"ok"`。理由：该常量位于 `app.js` 顶层、不被 `extractFn` 提取，沙箱值是测试自己注入的，若不校验源文本，`app.js` 回退成旧文案/旧 tone 时运行时用例仍会绿（`K-dom-stub-tests-hide-dangling-refs` 同类陷阱）。
   - 逐条断言 `已收到打开信号`/`打开信号率`/`首次信号`/`最近信号` 在 `markup` 与 `source` 中均不存在——I-5 的“旧文案不得残留”。
   - 该用例在 `child_base_sha` 源码上失败（`AssertionError: three metric cards`），即非空断言。

2. `badges, dynamic metrics and detail labels render the 120-second copy`（跑真实渲染函数，DOM stub）
   - 一次 `loadOpenTrackingRecords` 解析含 `OPENED`/`NO_SIGNAL`/`NOT_TRACKED` 三行的快照，断言 `#motTabletbody` 输出 `<span class="badge info">疑似打开（120秒后请求）</span>`、`<span class="badge warn">无120秒后请求</span>`、`<span class="badge">未跟踪</span>`，且不含 `badge ok`——S-2 的 badge 类与文字，兼顾“`info` 不得被升级成 `ok`”。
   - 断言 `#motMetrics` 动态渲染出三个 `<div class="metric-label">…</div>`（`跟踪发出`/`120秒后请求`/`120秒后请求率`）且分子分母 4/2 时比率为 `50.0%`——证明动态指标与静态首屏同文案、比例仍走 `formatPercent`。
   - 解析详情后断言 `<dt>首次图片请求</dt><dd>…</dd>`、`<dt>最近图片请求</dt><dd>…</dd>`、`<dt>状态</dt><dd>疑似打开（120秒后请求）</dd>`——I-3 原始时间与状态分离在详情同样成立。
   - 该用例在 `child_base_sha` 源码上失败（`AssertionError: metric label 120秒后请求`）。

3. `all eleven versioned assets in index.html carry one cache key`（项目级不变量）
   - 用 `/\.(?:css|js)\?v=([A-Za-z0-9._-]+)/g` 收集 `index.html` 内全部带版本键的静态资源，断言恰为 11 条（5 css + 6 js）且版本串唯一——落实 S-1“11 项版本键一致”与 `K-frontend-cache-key-triad`（同值同时 bump；漏 bump 任一项即红）。
   - 该用例不钉死键字面量，因此在 `child_base_sha` 上也是绿的（旧键同样一致）；它防的是“只 bump 一部分”的回归，而非证明本次 bump 发生过（后者由上面的 `grep -c` 与新键检索记录证明）。

## 缓存键（计划 task 4）

- 变更前键：`20260926-discovery-repair`，在 `index.html` 出现 **11** 次 —— `:11` `styles.css`、`:12` `expert-materials.css`、`:13` `mailbox-chat.css`、`:14` `meeting-confirmation.css`、`:15` `world-clock.css`、`:2310` `trust-reply-workbench.js`、`:2311` `expert-materials.js`、`:2312` `meeting-confirmation.js`、`:2313` `mailbox-chat.js`、`:2314` `app.js`、`:2315` `world-clock.js`。
- 变更后键：`20260928-mail-open-120s`，同样 **11** 次、同一批引用（`grep -c '20260928-mail-open-120s' src/main/resources/static/index.html` = 11）。
- 旧键残留：`index.html` + `app.js` 检索 `20260926-discovery-repair` 无匹配；`src/test` 检索该键无匹配（既有缓存键用例均从 `index.html` 现键动态取值，故本次无固定键测试需要同步，未扩文件）。
- `styles.css` 零差异：`git diff 15cef3dc HEAD -- src/main/resources/static/styles.css` 输出 0 行；diff 中所有 CSS/JS 链接除 `?v=` 外逐字不变。
- `task-modal-runtime.js`（`index.html:2309`）原本就无版本键，未纳入本次 bump（不在 11 项内，也未新增键）。

## 逐字文案对照（brief / I-5 / S-1 / S-2）

| 位置 | 落地值 |
|---|---|
| `OPENED` badge | 文本 `疑似打开（120秒后请求）`，类 `badge info` |
| `NO_SIGNAL` badge | 文本 `无120秒后请求`，类 `badge warn` |
| `NOT_TRACKED` badge | 文本 `未跟踪`，基础 `badge`（tone 空串） |
| 指标卡 | `跟踪发出`、`120秒后请求`、`120秒后请求率`（静态 `:274` 与动态 `app.js:14336-14338` 同串） |
| 表格/详情时间列 | `首次图片请求`、`最近图片请求` |
| 说明段 | `发送后120秒内的图片请求按预加载处理；之后的新请求仅表示疑似打开，不等于本人已读。图片可能被缓存，重复打开不一定产生新请求。指标按发送日期和发件账号统计；状态与搜索只影响列表。` |
| `#motStatus` option | `全部` / `疑似打开（120秒后请求）` / `无120秒后请求` / `未跟踪`（value 保持 `ALL/OPENED/NO_SIGNAL/NOT_TRACKED`） |

## Deviations

- 除 brief 逐条列出的断言外，额外增加 1 个用例 `all eleven versioned assets in index.html carry one cache key`。依据为批准计划「验收标准 S-1：11 项版本键一致且执行时按实际键反查测试」，非新增需求范围；该用例不钉死键字面量，改键时无需同步。
- 未发现 brief 未覆盖、需要计划解释的冲突；未触及授权文件之外的任何文件。
- 未执行 Maven/JDK 相关命令：后端行为属 `01-backend` 范围，本 child 授权命令仅为三条 Node 命令。

## Freshness

- Plan identity rechecked: YES（提交后再跑 `plan_identity.py`，SHA-256 仍 `8b2f24fa…`；计划 blob 与 `3237f07` 一致）
- Worktree identity rechecked: YES（`git add` 前按 `--expect-root/--expect-branch/--expect-git-dir` 复核，exit 0）
- Reported commits reachable from target branch: YES（`e0b00207…` 为 `fast/mail-open-tracking-120-second-filter` HEAD，父提交 `15cef3dc…`）
- Required commands run this invocation: YES（提交后 fresh 运行三条命令）
- Historical evidence used only as baseline: YES（brief 给的 8/8 基线已在本 worktree 复现，未用于代替本次运行）

## Remaining Blocker

- None

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`
