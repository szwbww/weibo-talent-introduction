# 03 · 会议日历Tab与收发件箱双端操作

状态：待审批；依赖01、02；发布还需08统一静态缓存激活。一个前端子系统，7文件（A1 将授权由 6 扩至 7）；不引入前端框架或日历依赖包。

## 需求描述

新增“会议日历”Tab，月历/列表查看、手动新增/改期/取消；收发件箱专家卡显示“已有排期”，头部可新增、查看、改期、取消。双端写同API，全部会议日期中文、北京时间。保留会话列表筛选/排序、标签、材料、草稿和既有会议邀请正文。排期修改本期只更新系统排期；不自动发送通知或改写已发ICS。

## 关键不变量

### Invariant I-1: 服务器权威、同源操作
- Rule: app.js实现一套排期表单和API adapter；邮箱和日历都调用它。保存成功才关闭并更新；失败保留输入。变更后按contactId使邮箱摘要与当前日历失效并回读，切Tab总会回读。前端不维护另一份localStorage/IndexedDB排期真值，不从邮件文本推测。
- Applies to: 新增/改期/取消/成功发邀请回调/Tab刷新。
- Violation consequence: 两边显示不同、失败伪成功。
- 来源: original。

### Invariant I-2: 中文与北京时区
- Rule: 统一formatter使用zh-CN、Asia/Shanghai、24小时；界面标签“专家会议时间（北京时间）”，显示例“2026年9月18日 周五 10:00–10:30”。datetime-local值由UTC显式formatToParts转换，提交按北京本地文本，不使用new Date(无时区文本)。月初/月末/今天均以北京日期计算。跨午夜事件在涉及的日期格展示，显示起止完整日期。会议确认草稿附件摘要也使用preview.startUtc/endUtc中文显示；已有对外英文邮件模板与ICS内容不翻译。
- Applies to: 月历/列表/弹窗/邮箱摘要/meetingCardMetaText。
- Violation consequence: 浏览器时区改变会议时刻、专家时间仍显示英文。
- 来源: original；MeetingConfirmationService.kt:410已有中国时间文本，但新UI从UTC统一格式化。

### Invariant I-3: 草稿与异步隔离
- Rule: 摘要查询批量传当前页专家id，捕获列表epoch；弹窗捕获contactId/eventId/版本。旧请求不能覆盖新筛选/专家；改期只更新排期区域，不重建人工回复或可信工作台。发送成功刷新排期不得清其他专家草稿。新发送只在原适用的入站会议确认路径产生排期。
- Applies to: 列表加载、selectExpert、onSendSuccess、CRUD回调、unmount。
- Violation consequence: 丢草稿、串专家、旧回包覆盖新页面。
- 来源: K-mailbox-draft-cache-owner-capture。

### Invariant I-4: 取消和多排期可见
- Rule: 默认月历不含CANCELLED；“显示已取消”可查历史且只读，取消不可恢复。一个专家多场有效会议显示activeCount并可逐条选择；无有效会议显示“暂无排期”和“新增排期”。摘要失败显示“排期暂不可用”，不能冒充0场。手动新增不要求已有邮件，专家从既有联系人选择。
- Applies to: 摘要、详情列表、月历、dialog。
- Violation consequence: 只能修改第一场、记录消失、失败误报无排期。
- 来源: original。

### Invariant I-5: 注册和既有外观
- Rule: index nav/section、app viewMeta/refreshCurrentView四点注册齐全。mailbox-chat.css字节不变，所有新增样式进styles.css；所有名称/文件/链接经escapeText或textContent输出。外链仅http/https且noopener。
- Applies to: 新DOM、模板、事件委托。
- Violation consequence: 空白Tab、CSS契约失败、内容注入。
- 来源: K-view-registration-triad / K-mailbox-chat-css-byte-contract / K-dom-stub-tests-hide-dangling-refs。

## 样式契约

### S-1：导航与控件复用
- 复用：styles.css:226 .nav-tab、:253 .nav-tab.active；:802 .button及:825 hover/:832 active/:838 primary/:862 danger；:872 .icon-button。不修改这些规则及其任何既有使用点。
- index中邮箱Tab后新增同层按钮，图标使用18×18 calendar SVG，文字仅“会议日历”。DOM：

```html
<button class="nav-tab" data-view="meeting-calendar"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 11h18"/></svg><span>会议日历</span></button>
<section class="view" id="view-meeting-calendar"><div id="meetingCalendarRoot" class="calendar-root"></div></section>
```
- 禁止inline style、新增未列class、改变现有Tab排序（只插入新Tab）。

### S-2：日历、共享表单和提示
- 复用上述button；新增规则必须整块逐字追加：

```css
/* meeting-mail-03: meeting calendar */
.calendar-root{display:flex;flex-direction:column;gap:16px;color:#475569;font-size:13px;line-height:1.6}
.calendar-toolbar{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px}
.calendar-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
.calendar-scroll{overflow:auto;border:1px solid #dce4ef;border-radius:14px;background:#fff}
.calendar-grid{display:grid;grid-template-columns:repeat(7,minmax(0,1fr));min-width:700px}
.calendar-weekday{padding:10px;text-align:center;background:#f8faff;border-bottom:1px solid #dce4ef;color:#64748b}
.calendar-day{min-height:132px;padding:8px;border-right:1px solid #e2e8f0;border-bottom:1px solid #e2e8f0}
.calendar-day[data-outside=true]{background:#f8fafc;color:#94a3b8}
.calendar-day[data-today=true]{background:#eff5ff}
.calendar-event{display:flex;flex-direction:column;gap:2px;width:100%;margin-top:6px;padding:7px 9px;border:1px solid #cbdcf7;border-radius:7px;background:#eff5ff;color:#1e40af;font:inherit;text-align:left;overflow-wrap:anywhere;cursor:pointer}
.calendar-event:hover{border-color:#93b4ec;background:#eaf1ff}
.calendar-event:active{background:#dbeafe}
.calendar-event[data-cancelled=true]{border-color:#dce4ef;background:#f1f5f9;color:#64748b}
.calendar-list{display:flex;flex-direction:column;gap:8px}
.calendar-summary{display:flex;align-items:center;flex-wrap:wrap;gap:8px;margin-top:8px;color:#1e40af;font-size:12px}
.calendar-dialog{inset:0;margin:auto;width:min(640px,calc(100vw - 32px));max-height:calc(100dvh - 32px);padding:24px;border:1px solid #dce4ef;border-radius:18px;background:#fff;color:#334155;overflow:auto;box-shadow:0 24px 64px rgba(15,23,42,.2)}
.calendar-dialog::backdrop{background:rgba(15,23,42,.35)}
.calendar-form{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin:16px 0}
.calendar-field{display:flex;flex-direction:column;gap:6px;min-width:0;font-size:12px;color:#64748b}
.calendar-field input,.calendar-field select,.calendar-field textarea{width:100%;min-height:36px;padding:8px 10px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font:inherit}
.calendar-field textarea{min-height:72px;resize:vertical}
.calendar-wide{grid-column:1/-1}
.calendar-note{margin:8px 0;color:#64748b;font-size:12px}
.calendar-error{margin:8px 0;color:#be123c;font-size:12px}
.calendar-root :is(button,input,select,a):focus-visible,.calendar-dialog :is(button,input,textarea,select,a):focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
.calendar-root :is(button,input,select):disabled,.calendar-dialog :is(button,input,textarea,select):disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.calendar-root [hidden],.calendar-dialog [hidden]{display:none!important}
@media(max-width:760px){.calendar-form{grid-template-columns:1fr}.calendar-dialog{padding:16px}.calendar-day{min-height:112px}}
@media(prefers-reduced-motion:reduce){.calendar-root *,.calendar-dialog *{transition:none!important;scroll-behavior:auto!important}}
```
- DOM合同（动态文本位置由textContent填充；数据循环仅重复同一骨架）：

```html
<div class="calendar-toolbar"><h2 data-role="calendar-month"></h2><div class="calendar-actions"><button class="button" data-calendar-action="previous">上月</button><button class="button" data-calendar-action="today">今天</button><button class="button" data-calendar-action="next">下月</button><button class="button" data-calendar-action="month" aria-pressed="true">月历</button><button class="button" data-calendar-action="list" aria-pressed="false">列表</button><label><input type="checkbox" data-role="show-cancelled">显示已取消</label><button class="button primary" data-calendar-action="create">新增排期</button></div></div>
<p class="calendar-note" data-role="calendar-status" role="status"></p>
<div class="calendar-scroll"><div class="calendar-grid" data-role="calendar-grid"><div class="calendar-weekday">周一</div><div class="calendar-day" data-outside="false" data-today="false"><span data-role="day-number"></span><button class="calendar-event" data-cancelled="false" data-event-id=""><time></time><strong></strong><span data-role="event-status"></span></button></div></div></div>
<div class="calendar-list" data-role="calendar-list" hidden></div>
<dialog id="meetingCalendarDialog" class="calendar-dialog" aria-labelledby="meetingCalendarDialogTitle"><form id="meetingCalendarForm"><div class="calendar-toolbar"><h2 id="meetingCalendarDialogTitle">新增排期</h2><button class="button icon-button" type="button" data-calendar-action="close" aria-label="关闭">×</button></div><div class="calendar-form"><label class="calendar-field calendar-wide" data-role="expert-picker">选择专家<input type="search" data-role="expert-search" placeholder="搜索姓名或邮箱"><select name="contactId" required></select></label><p class="calendar-note calendar-wide" data-role="expert-name"></p><label class="calendar-field">开始时间（北京时间）<input name="startBeijing" type="datetime-local" required></label><label class="calendar-field">结束时间（北京时间）<input name="endBeijing" type="datetime-local" required></label><label class="calendar-field calendar-wide">会议链接（选填）<input name="meetingLink" type="url" maxlength="1024"></label><label class="calendar-field calendar-wide">备注（选填）<textarea name="note" maxlength="200"></textarea></label></div><p class="calendar-note" data-role="time-summary"></p><p class="calendar-note" data-role="source-summary"></p><p class="calendar-note">排期操作不会自动发送通知邮件。</p><p class="calendar-error" role="alert" data-role="calendar-error" hidden></p><div class="calendar-list" data-role="expert-events" hidden></div><div class="calendar-actions"><button class="button" type="button" data-calendar-action="close">关闭</button><button class="button danger" type="button" data-calendar-action="cancel-event">取消排期</button><button class="button primary" type="submit">保存排期</button></div></form></dialog>
```
- 月历固定周一至周日；42日期格；列表复用calendar-event按钮骨架，空/错误用calendar-note/calendar-error。一个modal portal在body，退出还焦点，Escape只关闭不提交。选择多个排期时expert-events用同一event按钮骨架；已取消行禁用编辑字段并隐藏保存/取消按钮。source-summary可含来源邮件只读id与外部会议链接（a标签，无新class）。
- 取消在同一个calendar-dialog进入确认态：隐藏日期/链接/备注编辑区，沿用calendar-field/calendar-wide新增name=cancelReason、maxlength=200的textarea，标签“取消原因（选填）”；标题“取消这场排期？”，按钮“保留排期/确认取消排期”。保留或Escape零写请求；确认携带最新expectedUpdatedAt提交。此为确定实现，不依赖通用dialog能否同时确认和输入。
- 不新增inline style或未声明class；所有控件禁用/聚焦状态由上述CSS及既有button处理。

取消确认态的替换区骨架（同一dialog内；data-role不同，无重复id）：

```html
<label class="calendar-field calendar-wide" data-role="cancel-reason-field">取消原因（选填）<textarea name="cancelReason" maxlength="200"></textarea></label>
<div class="calendar-actions" data-role="cancel-confirm-actions"><button class="button" type="button" data-calendar-action="keep-event">保留排期</button><button class="button danger" type="button" data-calendar-action="confirm-cancel">确认取消排期</button></div>
```

### S-3：邮箱摘要和操作
- 复用mailbox-chat.css:19 .mc-person-main、:34 .mc-header、:38 .mc-actions、:29 .mc-badge，不修改规则；新class仅S-2中的calendar-summary。
- 列表卡片在现有信息后追加：

```html
<span class="calendar-summary" data-role="meeting-summary">已有排期 · 1场 · 9月18日 10:00</span>
```
- 头部在身份信息下追加calendar-summary，操作区追加下面骨架；多场“变更日期”先打开expert-events选择场次，“取消排期”同理：

```html
<button class="button" type="button" data-action="mc-add-schedule">新增排期</button>
<button class="button" type="button" data-action="mc-edit-schedule">变更日期</button>
<button class="button danger" type="button" data-action="mc-cancel-schedule">取消排期</button>
```
- 0场隐藏改期/取消，保留新增。已有会议草稿卡仅改meta文本，不改DOM/CSS。

## 现状审计

### 存储、注册与当前UI
- 没有生产日历Tab；index.html:111邮箱Tab，:715邮箱view；app.js:546 viewMeta、:1766 refreshCurrentView。四点注册规则已复核。
- 当前改动区逐字HTML/CSS见[证据基线](meeting-mail-evidence.md#前端改动前基线)；mailbox-chat.js:2665工具栏仍为B/I/列表/链接/会议确认/跟进。头像头部与卡片仍原结构。
- 样式实值：styles.css:1主色#1e40af、hover#1e3a8a、亮色#3b82f6；:70 radius-sm=7px、:72 radius-lg=18px；:116字号13/行高1.5；按钮32px高/12px字/gap6；邮箱行高1.6/字12px、列表306px/间距16px；新样式使用契约固定数值。mailboxChatStyle.test.js严格比较mailbox-chat.css全文，且字面class只允许styles.css/邮箱CSS已定义；不绕过测试、不拼接隐藏class。
- 专家选择接口真实为 GET /api/expert-contacts 返回contacts,totalCount（ExpertContactManagementController.kt:56），没有keyword分页参数；打开新增时读取一次，用姓名/邮箱在已返回contacts内筛选，不编造不存在的搜索接口。
- API：app.js:1540自动拼contextPath并处理认证；新请求继续用api。
- sessionStore Map与drafts Map的全体读写命中已留存 evidence.draft_writes；新日历只新增短生命周期查询缓存、请求序号和dirty标记，不写上述草稿存储。mailbox select/render和success需捕获owner/epoch（来源K-mailbox-draft-cache-owner-capture）。
- 会议卡meetingCardMetaText:2582使用原zoneId与本地字符串；applyMeetingFill:3945已保存preview.startUtc/endUtc，可以直接使用真实UTC值显示中文，无需猜时区。
- 新读写API全部来自01；IP-1：邮箱创建→日历回读；IP-2：日历改期/取消→邮箱摘要回读；IP-3：02成功发信→两端回读；IP-4：刷新/切换→草稿隔离；IP-5：UTC→北京日期格与编辑值。

## 实现方案

1. index/app实现S-1四点注册、S-2一个共享dialog和日历函数（I-1/I-2/I-5）。不新增script资源；在app.js按现有view模式增加函数。08统一更新现有9资源键。
2. app提供mcHostGetMeetingSummaries、mcHostOpenMeetingSchedule、mcHostMeetingScheduleChanged；CRUD共用01API（I-1/I-4，S-2）。一次事件写成功后发一个包含contactId的页面内事件；订阅回调仅刷新自己当前owner。跨浏览器在Tab激活/页面重新获得焦点时回读，不承诺websocket实时推送。
3. mailbox-chat在列表加载后批量读摘要、头部加载按专家读事件；注册S-3操作并在unmount移除事件监听。02会议发送成功只通知日历失效；不会触发额外SMTP。更改meetingCardMetaText用previewUTC（I-2/I-3）。
4. styles.css只追加S-2块；两份新JS测试验证所有DOM id源文本存在、42格/跨日/分页、中文24小时、表单＋摘要双端操作、旧epoch忽略，保留原JS全量回归（I-1～I-5、S-1～S-3）。

## 变更文件清单

|序号|文件|操作|内容|
|---|---|---|---|
|1|`src/main/resources/static/index.html`|修改|注册Tab、view和一个共享排期dialog|
|2|`src/main/resources/static/app.js`|修改|日历渲染、CRUD表单、统一北京时间格式、宿主adapter|
|3|`src/main/resources/static/mailbox-chat.js`|修改|列表摘要、头部排期操作、发送后同步、草稿时间中文|
|4|`src/main/resources/static/styles.css`|修改|仅追加本计划calendar-*规则|
|5|`src/test/js/meetingCalendar.test.js`|新增|区间/月历/列表/时区/DOM注册|
|6|`src/test/js/mailboxCalendarIntegration.test.js`|新增|摘要和双端CRUD同步、陈旧回调|
|7|`src/test/js/meetingConfirmationIntegration.test.js`|修改|A1：:1738 草稿卡时间期望随 I-2 改为中文北京口径，弃用旧 IANA zone 文案|

## 验收标准

- I-1：两侧POST/PUT/cancel只各一次；取消弹窗零写；失败不改本地真值；进入Tab回读。
- I-2：浏览器UTC、America/Los_Angeles、Asia/Shanghai三时区均显示相同北京10:00且提交同样startBeijing；跨月23:30–次日00:30占两个日期格。草稿卡不出现英文周/月或原IANA zone串。
- I-3：A专家慢请求后切B，不改B摘要/草稿；日期编辑前后已输入正文逐字不变。
- I-4：同专家2场均可编辑/取消；默认取消隐藏，勾选后灰色只读；网络失败明确“排期暂不可用”。
- I-5：四点注册源码存在且唯一；旧CSS字节一致；动态文本转义。
- S-1～S-3：逐字CSS块包含、无inline/未知class；1440px和760px窗口目测。node --check app.js/mailbox-chat.js；node --test src/test/js/*.test.js。

## 人工验收清单

### A-1：双端新增与改期
- 前置条件：01/02部署至测试环境；已有一个测试专家，0场；登录后浏览器时区模拟Los Angeles。
- 操作步骤：1.邮箱“新增排期”填9月18日10:00–10:30。2.切会议日历。3.点事件改成9月19日14:00–14:30。4.返回邮箱。
- 预期结果：新增后邮箱“已有排期 · 1场”；日历显示9月18日周五10:00；改后两边9月19日14:00，刷新仍存在；已发送邮件计数不变。
- 覆盖：I-1/I-2/I-4；S-1～S-3；IP-1/IP-2/IP-5。

### A-2：取消、历史和多场
- 前置条件：给同专家再手动新增9月20日10:00–10:30，已有2场。
- 操作步骤：1.邮箱取消其中一场；先点保留，再重开确认取消，原因“时间冲突”。2.日历勾选显示已取消。3.尝试编辑已取消记录。4.取消剩余一场。
- 预期结果：保留时2场；确认后有效1场，已取消灰色可读，原因“时间冲突”，不能保存改期；最后邮箱“暂无排期”，新增仍可点击。
- 覆盖：I-1/I-4；S-2/S-3；IP-2。

### A-3：发邀请与草稿回归
- 前置条件：A/B两测试专家；A有可回复来信，B草稿正文输入“保留B草稿”；开发者工具开启Slow 3G。
- 操作步骤：1.A生成会议确认，查看草稿卡后发送到SMTP沙箱。2.请求中切B；等待完成。3.切回A和日历。4.检查标签、材料入口及会话筛选。
- 预期结果：A新增恰1场；B正文仍“保留B草稿”；A时间中文北京口径；原标签/材料数量不变，筛选排序不变，会议邮件英文正文保留。
- 覆盖：I-1/I-2/I-3/I-5；IP-3/IP-4；保留原行为。

### A-4：样式、失败与跨日
- 前置条件：创建北京9月30日23:30至10月1日00:30的排期；1440px和760px视口。
- 操作步骤：1.翻9月与10月月历。2.切列表、打开弹窗，用Tab/Escape操作。3.断网刷新邮箱摘要和日历。
- 预期结果：两个日期格均能找到该会；无英文时间；按钮32px、蓝色#1e40af、表单边框#dce4ef、圆角7px；焦点蓝框2px；小屏月历内部横滚不撑宽页面；断网显示错误，不伪造空列表。
- 覆盖：I-2/I-4/I-5；S-1～S-3；IP-5。
