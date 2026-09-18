# 会议日历线上样式对齐

授权：2026-09-17 用户明确要求对照既有线上预览直接修改生产前端。
此授权替代 `fast/meeting-mail-master/children/03-calendar-ui/brief.md` S-2 的旧工具栏逐字布局要求；原数据、CRUD、时间、草稿隔离约束继续保留。

## 本次范围与证据

- `app.js` 的 `MEETING_CALENDAR_CHROME_HTML` 原来只有单行工具栏、状态与全宽网格，缺少预览的统计栏、月历卡片和右侧会议安排。
- `styles.css` 全局 `input, select, textarea` 指定 `width:100%; height:34px; min-height:34px`；旧日历复选框没有专用约束，造成截图中的大方框。使用已有 `checkbox-row` 并限为 14×14。
- 预览依据：`https://qingfei.szwbww.com/previews/meeting-calendar-20260916/#calendar`；复用其浅色统计栏、左右卡片、月份左置、分段切换、圆形今日日期与紧凑格子。
- 原接口返回 `createdAt/updatedAt`，修改备注也更新 `updatedAt`，没有独立改期标识（`MeetingCalendarController.kt` 的 `MeetingCalendarEventResponse`，`MeetingCalendarService.kt` 的更新路径）。因此第三项显示真实的“今日会议”，不凭更新时间推算“已改期”。其余为本月有效会议、其中待召开会议。
- 月统计/列表/侧栏从已加载网格范围过滤与当前北京月份相交的事件，半开时间区间；统计排除取消项。数据失败显示“—”和错误，不冒充零场。
- 侧栏按钮使用既有 `data-event-id` 点击委托，共享原修改/只读弹窗。无新增 API、数据库、存储、通知、后端代码。

## 样式与变更边界

- `app.js`：日历页面骨架、月范围过滤、统计和侧栏文本渲染，动态值仅 `textContent`。
- `styles.css`：保留原样式块，新增 `.calendar-root` 范围样式；仅日历激活时约束 shell 网格列，修复窄屏横向溢出。保留 42 格和日历内部横滚。900px 以下隐藏辅助侧栏，主列表/月历仍可操作。
- `index.html` 与按旧缓存键反查的九份 JS 契约测试：统一更换静态缓存键为 `20260917-calendar-layout-align`。资源本身不新增。
- `meetingCalendar.test.js`：旧工具栏逐字断言改为新版角色和原操作入口合同，新增真实月份边界、取消过滤、北京午夜统计测试；原日期格/事件骨架、CSS、CRUD 回归保留。
- 不改邮箱样式、附件、世界时钟、业务写路径。

## 验证与发布

- `node --check src/main/resources/static/app.js`。
- `node --test src/test/js/*.test.js`。
- 使用生产 CSS 与真实日历函数的本地隔离夹具验证有数据、取消、跨日、列表及 760px 布局；生产页面只读核验，避免写入测试排期。
- 部署前核对线上三个资产与下载基线哈希；备份三个原文件与原 WAR；仅更新三个静态文件。WAR 同步更新相同条目并保留原修改时间，避免热部署触发应用重启。
- 发布后核对公开资产哈希、缓存键，并用已登录生产页面检查月历/列表、月份切换、取消筛选、新增弹窗。

人工验收：用户在已更新的会议日历页面确认视觉效果；本次不重跑会发送真实邮件或写真实排期的人工用例。
