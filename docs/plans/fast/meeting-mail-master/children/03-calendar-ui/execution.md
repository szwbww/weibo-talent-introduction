# Fast-P Execution — 03-calendar-ui

## Epoch 1 — IMPLEMENTATION

- Executor: isolated implementer `Implementer03`.
- Product base: `f83e29c397dd01ceafb98025e0d649a69c6eafae` (child 02 terminal code head); child 02 evidence commit `64fb83b100a3527e7638ea9224ada4c33b167afe` precedes this implementation in Git ancestry.
- Plan: `docs/plans/fast/meeting-mail-master/children/03-calendar-ui/brief.md` (byte-identical copy of the approved `docs/plans/2026-09-16/meeting-mail-03-calendar-ui.md`).
- Upstream HTTP surface inherited from 01/02: `/api/meeting-calendar` (`GET /events`, `GET /events/{id}`, `GET /summaries`, `POST /events`, `PUT /events/{id}`, `POST /events/{id}/cancel`) plus the existing mailbox endpoints. Cache-key activation for the static resources belongs to child 08, not this child.
- Required commands: `node --check src/main/resources/static/app.js`, `node --check src/main/resources/static/mailbox-chat.js`, `node --test src/test/js/*.test.js`.
- Dispatch: fresh implementer acquired through the Required Agent Gate.

### 执行结果：PLAN_CONFLICT

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master/docs/plans/fast/meeting-mail-master/children/03-calendar-ui/brief.md`
- Plan SHA-256: `af25844d8ee103d3923229b25938a6a68f42f28061925920a359e29ac74c58e8`（19933 bytes）
- Execution ID: `…/children/03-calendar-ui/brief.md@af25844d…c58e8`；Epoch: `NEW`
- Executor: `Implementer03`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master`；branch `fast/meeting-mail-master`
- Worktree ID: `<root>@fast/meeting-mail-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-meeting-mail-master`
- Pre-execution code SHA: `f83e29c397dd01ceafb98025e0d649a69c6eafae`；执行前 HEAD：`64fb83b100a3527e7638ea9224ada4c33b167afe`
- Post-execution code SHA: `25b47e541cddc72a4d5acf33ee71c075fe8e392c`（`feat(fast-p): implement 03-calendar-ui`，仅含 6 个授权文件，可达目标分支）
- Evidence HEAD: N/A（证据提交由控制器单独执行）

### 任务状态

| 要求 | 状态 | 文件 | 证据 |
|---|---|---|---|
| 变更清单 1 index.html（Tab/view/共享 dialog） | IMPLEMENTED | `src/main/resources/static/index.html` | S-1 nav+section 逐字；S-2 dialog 逐字（含取消态替换区）；`data-view`/`id` 各唯一 |
| 变更清单 2 app.js（日历渲染/CRUD/统一时间/host adapter） | IMPLEMENTED | `src/main/resources/static/app.js` | 四点注册 + 唯一 formatter + 月历/列表/弹窗/CRUD + `mcHost*` 三函数 |
| 变更清单 3 mailbox-chat.js（摘要/头部操作/发送后同步/草稿中文） | IMPLEMENTED | `src/main/resources/static/mailbox-chat.js` | S-3 骨架逐字；批量摘要按列表 epoch；广播订阅/解绑；`meetingCardMetaTextFor` 走宿主 formatter |
| 变更清单 4 styles.css（仅追加 calendar-*） | IMPLEMENTED | `src/main/resources/static/styles.css` | 计划 S-2 CSS 块逐字追加（30 行 + 标题注释）；`mailbox-chat.css` 与 base 字节一致 |
| 变更清单 5 meetingCalendar.test.js | IMPLEMENTED | `src/test/js/meetingCalendar.test.js` | 22 tests / 22 pass |
| 变更清单 6 mailboxCalendarIntegration.test.js | IMPLEMENTED | `src/test/js/mailboxCalendarIntegration.test.js` | 15 tests / 15 pass |

### 命令（本调用内全新执行）

| 命令 | 结果 | 证据 |
|---|---|---|
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `node --check src/main/resources/static/mailbox-chat.js` | PASS | exit 0 |
| `node --test src/test/js/*.test.js` | **FAIL** | exit 1；tests 923 / suites 177 / pass 922 / **fail 1**（唯一失败为未授权文件，见下） |
| （附加）`node --test src/test/js/meetingCalendar.test.js` | PASS | exit 0；22/22 |
| （附加）`node --test src/test/js/mailboxCalendarIntegration.test.js` | PASS | exit 0；15/15 |

唯一失败（**授权范围外文件**，未修改）：

```
src/test/js/meetingConfirmationIntegration.test.js:1726  suite "fast-p 04: 确认填入草稿（I-1/I-3/T3/S-3 块与卡）"
  ✖ 空正文首填：1 块 1 卡 ready；只写草稿不调用发送/旧确认接口
    AssertionError [ERR_ASSERTION]: The input did not match the regular expression /Europe\/Istanbul · 30 分钟/. Input:
    "2026年9月11日 周五 15:00–15:30 · 30 分钟"
    断言位置：src/test/js/meetingConfirmationIntegration.test.js:1738
      assert.match(container.querySelector('[data-role="file-meta"]').textContent, /Europe\/Istanbul · 30 分钟/);
```

### 不变量覆盖

- **I-1 服务器权威、同源操作**：`app.js` 只有一套表单（`#meetingCalendarDialog`/`#meetingCalendarForm`）与一套 adapter（`meetingCalendarCreateEvent/UpdateEvent/CancelEvent/FetchEvents/FetchEvent/FetchSummaries`）；收发件箱经 `mcHostOpenMeetingSchedule`/`mcHostGetMeetingSummaries` 复用同一实现（两新测试用例断言组件源码不含 `/api/meeting-calendar`、不写 localStorage）。保存/取消仅在 `.then` 成功分支 `closeMeetingCalendarDialog`，失败路径保留全部输入并只置 `data-role="calendar-error"`。写成功后按 contactId 广播 + 两端回读；切 Tab 由 `refreshCurrentView → loadMeetingCalendar()` 无条件回读。浏览器实测：新增提交恰 1 个 `POST /api/meeting-calendar/events` 且弹窗关闭、状态「已新增排期」。
- **I-2 中文北京时区**：唯一 `MEETING_CALENDAR_FORMATTER`（zh-CN/Asia/Shanghai/hourCycle h23），全部显示与转换经 `formatToParts`；`meetingCalendarBeijingTextToInstant` 显式按 +08:00 计算并拒绝被 Date 归一化的非法值，全程不出现 `new Date(无时区文本)`。测试在 UTC / America/Los_Angeles / Asia/Shanghai 三个真实浏览器时区下断言同串「2026年9月18日 周五 10:00–10:30」与同一 `startBeijing`；跨午夜事件（23:30–00:30）在北京 9/30 与 10/1 两个日期格同时出现且显示起止完整日期（node 断言 + 浏览器实测）。草稿卡 `meetingCardMetaText` 改用 `preview.startUtc/endUtc` 经宿主同一 formatter，测试断言输出「2026年9月11日 周五 15:00–15:30 · 30 分钟」且无英文周/月与 IANA 串。
- **I-3 草稿与异步隔离**：摘要请求捕获 `instance.listSeq`（列表）与 `instance.convEpoch`（当前专家），旧回包整批丢弃——集成测试用两个延迟 Promise 验证「先回新、后回旧」时卡片文案保持新值。头部摘要只改 `.calendar-summary` 的 textContent 与按钮 hidden，不重建 `.mc-scroll`（测试断言同一节点引用）；排期刷新不动草稿 map。`unmount` 解绑 `meeting-calendar-changed`（测试断言解绑后广播不再回读）。
- **I-4 取消与多排期可见**：默认请求 `showCancelled=false`（服务端排除 CANCELLED）；「显示已取消」走只读 `view` 模式：字段 `disabled`、保存/取消按钮 `hidden`、时间摘要附「已取消 · 原因：时间冲突」（浏览器实测）。头部按专家 `activeCount` 决定显示，多场走 `pick` 态复用同一事件骨架逐条选择；0 场显示「暂无排期」+ 保留「新增排期」，隐藏改期/取消；摘要失败置 `state:"error"` → 「排期暂不可用」，绝不显示 0 场。取消确认态在同一个 dialog 内替换编辑区（`data-role="cancel-reason-field"`/`cancel-confirm-actions`），进入/保留/Escape 零写请求、确认时先 `GET /events/{id}` 取最新版本再带 `expectedUpdatedAt` 提交一次——浏览器实测写序列：进入 0、保留 0、Escape 0、确认 1 次 `POST /events/11/cancel {"expectedUpdatedAt":"2026-09-10T00:00:00Z","reason":"时间冲突"}`。
- **I-5 注册与既有外观**：四点注册齐全且唯一（`index.html` `data-view="meeting-calendar"` ×1、`id="view-meeting-calendar"` ×1；`app.js` `viewMeta["meeting-calendar"]`、`refreshCurrentView` 分支）；静态资源键仍为 9 个（未 bump，属 08）；`mailbox-chat.css` 与 base `f83e29c` 字节一致（`git diff --quiet` 零差异）；动态文本一律 `escapeText`/`textContent`，外链仅 `http/https` 且 `target="_blank" rel="noopener"`（浏览器实测 `source-summary` 的 anchor HTML）。

### 样式契约符合性

- **S-1**：index nav 按钮与 `<section class="view" id="view-meeting-calendar"><div id="meetingCalendarRoot" class="calendar-root"></div></section>` 均为计划块逐字（测试从 brief 提取并比对）；新 Tab 紧跟邮箱 Tab、在来信汇总之前；新片段无 inline style；未改既有 `.nav-tab`/`.button`/`.icon-button` 规则。
- **S-2**：计划 CSS 块以脚本从 brief 的 ```css 围栏提取后整块追加（测试断言 `stylesSource.includes(briefCss)` 为真，且含 `/* meeting-mail-03: meeting calendar */`）；DOM 骨架逐字落在 index.html（共享 dialog、取消态替换区）与 app.js（toolbar/status/滚动容器/列表容器常量、`.calendar-weekday` 行、`.calendar-day` 格、事件按钮骨架），动态文本全部由 textContent/setAttribute 填充，无 inline style、无未声明 class。浏览器实测样式实值：`.button` 32px/12px、`.button.primary` rgb(30,64,175)/圆角 7px、`.calendar-field input` 边框 rgb(220,228,239)/圆角 7px、`.calendar-grid` min-width 700px、`.calendar-scroll` overflow-x auto、`.calendar-day` min-height 132px（≤760px 时 112px、表单单列）、1440px 页面零横向溢出。
- **S-3**：列表卡片与头部追加 `calendar-summary` 骨架（计划示例文案为动态值，落码为空槽 + textContent）；头部三按钮骨架逐字（`mc-add-schedule`/`mc-edit-schedule`/`mc-cancel-schedule`）；复用类 `.mc-person-main`/`.mc-header`/`.mc-actions` 未改；新 class 仅 `calendar-summary` 且由 styles.css 声明（沿用 `mailboxChatStyle.test.js` 的白名单判据）；会议草稿卡只改 meta 文本，DOM/CSS 未动。

### 真实浏览器冒烟（静态目录 + CDP 请求拦截桩 API）

以 `python3 -m http.server` 提供 static 目录、CDP 拦截 `/api/**` 返回夹具：会议日历页渲染 42 日期格 + 7 列周一起始表头（grid 子节点 49）、今天格 1 个、状态「2026年9月共 3 场排期」（默认不含已取消）、事件落位正确（9/18、9/20、跨午夜 9/30+10/1）；列表/月历切换 `aria-pressed` 与 `hidden` 正确、列表项为完整中文区间；「显示已取消」出现灰行「已取消」；dialog 各态（新增/改期/只读详情/取消确认/多场选择）按钮可见性与禁用态符合 S-2。**该冒烟发现并修复了一个真实缺陷**：日期格骨架原为闭合 `</div>`，事件按钮被追加到格外的 `.calendar-grid` 上（渲染出空按钮且栅格错位）；改为 `DAY_OPEN + EVENT×n + DAY_CLOSE` 组合后事件进入格内。

### 未验证 / 阻塞项

1. **【阻塞，授权范围外】`src/test/js/meetingConfirmationIntegration.test.js:1738` 钉住被本计划 I-2 取代的旧口径。** 计划验收标准要求「草稿卡不出现英文周/月或原 IANA zone 串」，实现方案第 3 条明确「更改 meetingCardMetaText 用 previewUTC」，因此该断言与计划要求直接冲突；但 6 个授权文件不含该测试文件，批量约束要求「Any other file is out of scope: STOP and return BLOCKED/PLAN_CONFLICT instead of editing it」且「do not weaken or delete existing assertions」，故未修改。最小修复是把该行期望改为 `assert.match(..., /2026年9月11日 周五 15:00–15:30 · 30 分钟/)`（夹具 `preview.startUtc=2026-09-11T07:00:00Z`、`durationMinutes=30`；该文件其余 12 处 `Europe/Istanbul` 断言属于组件时区选择器，与本变更无关、保持通过）。需要人工决定：把该测试文件加入变更清单，或由控制器在证据提交阶段授权一行修正。
2. **【观察，非本 child 可修】≤760px 视口下第 12 个 Tab 使页面横向溢出 74px。** 实测（CDP，760×900）：含会议日历 Tab 时 `documentElement.scrollWidth - clientWidth = 74`，且最宽元素为 `header.topnav`/`nav.nav-tabs`/`button.nav-tab`（非 `.calendar-*`，`.calendar-scroll` 为 auto 内部横滚，未撑宽页面）；把该 Tab 从 DOM 移除后溢出回到 0。1440px 下为零。修复需要改 `.nav-tabs`（flex 收缩/横向滚动）或媒体查询，属 S-1「不修改既有规则及其使用点」与变更清单「styles.css 仅追加 calendar-* 规则」之外，故未改。A-4 人工清单的 760px 目测项需连同此结论一起复核。
3. 未运行 Maven/JUnit 全量（03 不涉及 Kotlin 文件，且批量约束禁止项目级验证）；未跑 `mvn test` 的 exec-plugin 前端阶段。

### 偏差

- 除上文第 1 项（无法在不改未授权文件的前提下满足全部验收）外无偏差；未新增静态资源、未 bump 缓存键、未推送/合并/改写历史、未改动 `docs/plans/**`。

### Freshness

- Plan identity rechecked: YES（执行前后 hash 一致 `af25844d…`）
- Worktree identity rechecked: YES（`--expect-root/--expect-branch/--expect-git-dir` 通过）
- Reported commit reachable from target branch: YES（`git merge-base --is-ancestor HEAD fast/meeting-mail-master`）
- Required commands run this invocation: YES
- Historical evidence used only as baseline: YES

### Epoch 1 — PAUSED_FOR_HUMAN (PLAN_CONFLICT)

- Implementer `Implementer03` committed `25b47e541cddc72a4d5acf33ee71c075fe8e392c` covering all 6 authorized files, then returned `PLAN_CONFLICT` instead of editing an unauthorized file.
- Cause: child plan 03 I-2 and 实现方案 3 mandate that the meeting-confirmation draft card meta text be rendered through the shared Beijing formatter, which supersedes the assertion at `src/test/js/meetingConfirmationIntegration.test.js:1738` that pins the old draft-card text `Europe/Istanbul · 30 分钟`. That test file is outside child 03's 6-file 变更文件清单, so the implementer stopped.
- Command evidence at `25b47e5`: `node --check app.js` exit 0; `node --check mailbox-chat.js` exit 0; `node --test src/test/js/*.test.js` exit 1 with tests 923 / pass 922 / fail 1 (sole failure = the pinned assertion above); `node --test src/test/js/meetingCalendar.test.js` 22/22; `node --test src/test/js/mailboxCalendarIntegration.test.js` 15/15.
- Minimal repair: add `src/test/js/meetingConfirmationIntegration.test.js` to child 03's authorized files and update that one expectation to the new formatter output (fixture `preview.startUtc = 2026-09-11T07:00:00Z`, `durationMinutes = 30`); the file's other 12 `Europe/Istanbul` assertions belong to the independent timezone picker and stay green.
- Recorded observation (not repaired, needs a `.nav-tabs` rule that S-1 forbids): at 760px viewport the 12th nav tab overflows the page by 74px; measured elements are `header.topnav`/`nav.nav-tabs`/`button.nav-tab`, not `.calendar-*`.
- Controller action: paused for a HUMAN-approved plan amendment; no verifier was dispatched for this child.
- Resume from: `25b47e541cddc72a4d5acf33ee71c075fe8e392c`.
