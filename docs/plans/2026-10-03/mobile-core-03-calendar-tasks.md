# 03 会议与任务查看

状态：待审阅。依赖：02已通过；子系统：会议查看、任务查看；5个变更文件。

## 需求描述

手机首次进入会议页默认显示按时间排列的排期列表，可查看详情与切月；可主动切回月历（仅月历容器横滑）。任务页显示单列运行卡片，历史记录及长日志可读可滚动。

保持：会议北京时间与UTC转换、月份边界、取消状态、分页和共享编辑入口；任务身份、真实状态、时间和计数、权限、轮询、原任务控制入口；桌面日历默认月视图与任务卡片布局。

不做：新会议接口、另一份会议数据、移动周历、任务历史卡片化、后台调度/中断规则修改；复杂任务控制弹窗不纳入本次手机完整适配承诺。

## 关键不变量

### I-1：视图偏好不改变会议查询
- Rule：新增唯一内存字段 `viewPreference=null|month|list`；effective view = 显式偏好，否则≤760px list、宽屏month。点击模式设置偏好；切月/刷新不清偏好；首次进入基于实际宽度。不持久化，不新增后端字段。
- Applies to：meetingCalendarState、renderMeetingCalendar、onMeetingCalendarClick、已存在的全局断点监听接入。
- Violation consequence：用户主动选月历却反复被切回、旋转多发API。
- 来源：original。

### I-2：原会议数据与时区唯一
- Rule：复用现有events、meetingCalendarMonthEvents、renderMeetingCalendarList和formatBeijingMeetingRange；列表/模式切换不重新fetch，不改变from/to、cursor、showCancelled、seq或写入流程。设备时区不影响结果。
- Applies to：日历渲染与模式选择；共享排期查看/编辑。
- Violation consequence：漏会、重复请求、显示为手机所在地时间。
- 来源：original（未采用时区目录offset作具体会议时差）。

### I-3：任务样式不改变任务状态
- Rule：不修改taskActivityState、任务详情/轮询/中断的业务JS；主状态仍按executionId匹配。历史七列全部保留在局部横滑容器，日志不裁掉内容。
- Applies to：#view-tasks CSS与历史表格容器。
- Violation consequence：手机藏掉失败原因、另建轮询、状态与桌面不一致。
- 来源：original。

### I-4：追加规则与缓存
- Rule：继承01/02的手机断点与资源合同；不重写旧日历DOM常量及历史样式块，不增加资源数量；全部11资源统一换键。
- Applies to：styles.css、index.html、现有契约测试。
- Violation consequence：旧逐字骨架/样式测试失败，手机和桌面混用资源。
- 来源：K-frontend-cache-key-triad。

## 样式契约

### S-1：会议

复用 app.js:21254 的 MEETING_CALENDAR_CHROME_HTML 原样骨架；改变的是现有grid/list的hidden和两个模式按钮的aria-pressed，不新增日历节点。`renderMeetingCalendarList`沿用现有`.calendar-event`结构。样式复用styles.css:11463–11555：背景#fff、会议卡#eef3ff、边框#dce4ef、圆角7px/18px、focus#3b82f6；共享calendar-dialog保留原提交/取消按钮。

### S-2：任务

保留 index.html:1024–1108 的taskActiveCards/taskActiveDetail/tasksTable及原7列。仅在包裹tasksTable的现有`.table-wrap`增加 `tabindex="0" role="region" aria-label="任务历史记录，可横向滚动"`，方便键盘/辅助技术定位局部滚动区。保留分页和打开任务控制入口，布局适配不授权改写这些动作。

CSS完整块原样追加styles.css。所有旧选择器使用点见audit；均为手机派生规则，不修改旧块。没有新交互组件，hover/active/disabled及焦点规则复用既有calendar/button/task样式。

```css
/* mobile-core-03:start */
@media (max-width: 760px) {
    #view-meeting-calendar .calendar-toolbar,
    #view-meeting-calendar .calendar-month-controls,
    #view-meeting-calendar .calendar-actions,
    #view-meeting-calendar .calendar-filter-bar { flex-wrap: wrap; gap: 8px; min-width: 0; }
    #view-meeting-calendar .calendar-toolbar { padding: 12px; }
    #view-meeting-calendar .calendar-actions { width: 100%; }
    #view-meeting-calendar .calendar-segmented { max-width: 100%; }
    #view-meeting-calendar .calendar-scroll { max-width: 100%; overflow-x: auto; }
    #view-meeting-calendar .calendar-list { padding: 12px; }
    #view-meeting-calendar .calendar-list .calendar-event { min-height: 44px; font-size: 14px; }
    #view-meeting-calendar .button { min-height: 44px; height: auto; white-space: normal; }
    body .calendar-dialog { width: calc(100vw - 24px); max-height: calc(100dvh - 24px); padding: 16px; }
    body .calendar-dialog .calendar-form { grid-template-columns: minmax(0, 1fr); }
    body .calendar-dialog :is(input:not([type="checkbox"]):not([type="radio"]), select, textarea) {
        min-width: 0; max-width: 100%; min-height: 44px; font-size: 16px;
    }
    body .calendar-dialog .calendar-actions { flex-wrap: wrap; gap: 8px; }
    body .calendar-dialog .button { min-height: 44px; height: auto; white-space: normal; }
    #view-tasks .task-center-grid { grid-template-columns: minmax(0, 1fr); }
    #view-tasks .task-center-detail { padding: 12px; }
    #view-tasks .toolbar,
    #view-tasks .list-pager,
    #view-tasks .task-center-interrupt { flex-wrap: wrap; gap: 8px; min-width: 0; }
    #view-tasks .toolbar :is(input, select),
    #view-tasks .task-center-interrupt :is(select, textarea) {
        min-width: 0; max-width: 100%; min-height: 44px; font-size: 16px;
    }
    #view-tasks :is(.button, .task-center-link) { min-height: 44px; height: auto; white-space: normal; }
    #view-tasks .table-wrap { width: 100%; max-width: 100%; overflow-x: auto; }
    #view-tasks .table-wrap > table { min-width: 720px; }
    #view-tasks .table-wrap:focus-visible { outline: 2px solid #3b82f6; outline-offset: 2px; }
    #view-tasks .task-center-log { max-width: 100%; max-height: 50dvh; white-space: pre-wrap; overflow-wrap: anywhere; overflow: auto; }
    #view-tasks .pre { max-width: 100%; overflow: auto; }
}
/* mobile-core-03:end */
```

## 现状审计

### 日历内存状态
- meetingCalendarState（app.js:21006）当前viewMode=month；onMeetingCalendarClick（21888）用户点month/list写viewMode；renderMeetingCalendar（21409）读取其值控制hidden、aria-pressed和选择grid/list渲染。
- 新viewPreference只由用户mode点击写，渲染/断点读取；旧viewMode作为effective mode继续存在，避免无关消费者失效。不写localStorage。
- events写路径：loadMeetingCalendar（21439）从唯一meetingCalendarFetchEvents取得、失败置空，seq拦截旧响应。共享排期写入成功按原逻辑回读；本段不改API adapter与服务端schema。
- events读路径：月格、列表、统计、侧栏及查找事件详情。月列表用meetingCalendarMonthEvents筛选相交月份并排序，不能直接把grid加载的相邻月份记录全倒入列表。
- renderMeetingCalendarList（21334）已有空/失败文本，完整渲染events；首次mobile选list后必须同步按钮aria-pressed，不能只用CSS把grid藏掉。
- 与01断点观察器接入时只在当前meeting-calendar页、没有显式偏好时重算并render；不得重新load。其余页面或编辑中的共享dialog不重建。

### 任务只读呈现
- 活动列表、详情和日志由既有taskActivityState/refreshTaskActivity及详情API提供；源码关键点见app.js:798起的任务中心、renderTaskActivityCards/Detail/Logs。
- 本段不新增读写路径；taskActiveGlobalBtn仍走已有导航，服务端状态不从CSS或手机时间推断；历史表格7列及完整错误/日志数据原样保留。
- `.task-center-grid`（styles.css:12315）已手机单列，`.task-center-log`（12288）已pre-wrap；本次只约束工具栏/横滑容器和触控尺寸，避免为“适配”重建任务卡片渲染器。

### 前端样式盘点
- 原日历与任务DOM、CSS全文片段见audit；`.calendar-grid`560px最小宽由calendar-scroll承载。默认列表消除日常横滑，用户切回月历时容器横滑保留。
- 既有meetingCalendar.test.js从历史brief提取CSS/HTML契约；本段不改原骨架和原CSS，只追加派生块。taskActivityCenter及taskRecords测试继续验证现有语义。

## 实现方案

1. **T-1 视图选择**：app.js新增viewPreference，提取effective视图helper；renderMeetingCalendar应用；用户点击更新偏好；复用01唯一断点监听通知，避免新resize handler重复注册。遵循 I-1/I-2/S-1。
2. **T-2 会议样式**：styles.css追加本段完整CSS中的会议部分（与任务部分作为同一块落地），不动原HTML常量/时区格式化/事件CRUD。遵循 I-2/I-4/S-1。
3. **T-3 任务样式**：同CSS块，index.html对原历史table-wrap增加S-2属性，统一资源键。不改任务业务JS。遵循 I-3/I-4/S-2。
4. **T-4 验证**：meetingCalendar.test.js增加首次手机/桌面默认、显式选择保留、切月/切宽无额外fetch；taskRecordsSemantics.test.js增加容器可达性/完整七列合同。真实浏览器验证溢出与日志可读。遵循全部I/S。

## 变更文件清单

| 文件 | 用途 |
|---|---|
| src/main/resources/static/app.js | 日历effective视图与内存偏好；接入01断点通知 |
| src/main/resources/static/styles.css | 追加会议/任务手机CSS |
| src/main/resources/static/index.html | 历史滚动容器语义、11资源缓存键 |
| src/test/js/meetingCalendar.test.js | 模式与API/时区回归 |
| src/test/js/taskRecordsSemantics.test.js | 原7列/滚动容器验证 |

## 验收标准

- I-1：390px首次list、1440px首次month；用户选month后切宽/换月/刷新数据仍month；硬刷新回到按视口默认；无localStorage新键。
- I-2：模式切换与纯resize新增API请求数=0；相同UTC事件在Asia/Shanghai和America/Los_Angeles设备时区显示相同北京时间；相邻月份过滤、跨午夜、取消状态原测试通过。
- I-3：任务业务JS差异为0；RUNNING/SUCCESS/FAILED及错误文本与桌面相同，原7列和分页保留，已有轮询数不增。
- I-4/S-1/S-2：追加CSS与计划逐字一致，旧日历brief骨架与CSS测试不删；11资源同键；mobile只局部表格/月历横滑，body无溢出；1440px样式不变。
- 命令：`node --check src/main/resources/static/app.js`；`node --test src/test/js/meetingCalendar.test.js src/test/js/mailboxCalendarIntegration.test.js src/test/js/taskRecordsSemantics.test.js src/test/js/taskRecordsPaging.test.js src/test/js/taskActivityCenter.test.js`；最后全量JS测试。

## 人工验收清单

### A-1：手机会议默认与偏好
- 前置条件：测试环境已有当前月份两条排期，另有一条上月排期；390px新加载页面。
- 操作：进入会议→查看列表→切下月再返回→主动选月视图→旋转→回竖屏。
- 预期：初始为排期列表，只列与当前北京月份相交的排期；用户选月后一直保持月视图；月历可在自身容器横滑，页面本身不横滑。
- 覆盖：I-1/I-2/S-1。

### A-2：时间和共享详情
- 前置条件：测试事件开始UTC `2026-10-03T02:00:00Z`、结束`03:00:00Z`，专家A；另一条已取消排期。
- 操作：设备时区分别设上海/洛杉矶查看；打开A详情；在测试环境改到北京11:00并保存；从邮箱查看A排期；切换显示已取消。
- 预期：原事件始终显示北京时间10:00–11:00；保存后日历/邮箱一致变为新时间；取消状态随已有筛选显隐；手机单列字段、保存关闭按钮可达。
- 覆盖：I-2/S-1、既有会议写→两宿主读取交互。

### A-3：任务、分页与日志
- 前置条件：测试环境有RUNNING、SUCCESS、FAILED任务及长日志，历史至少两页。
- 操作：手机查看运行卡片→详情→日志→历史查询/翻页→横滑查看错误原因→点击全局任务入口；同时桌面对照。
- 预期：卡片单列，状态/计数/时间与桌面一致；7列均可到达；长日志可完整滚动，错误文字不截断；翻页不回第1页；任务控制入口仍在。
- 覆盖：I-3/S-2、任务读路径与控制入口不变。

### A-4：桌面与视觉
- 前置条件：新的1440px页面；对照实施前截图。
- 操作：查看会议和任务；再切390px查看按钮、输入、卡片、长会议名称和日志。
- 预期：桌面默认月视图、原卡片列数不变；手机主按钮44px、输入16px，长内容不撑宽页面；切换视图不出现新的加载请求。
- 覆盖：I-1/I-4/S-1/S-2。
