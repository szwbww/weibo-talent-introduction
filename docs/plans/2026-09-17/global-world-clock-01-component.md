# 全局北京时间与时区对照：01 独立组件

日期：2026-09-17。状态：待用户批准执行。本文件只规划，不表示已实现。

主计划：[global-world-clock-master.md](global-world-clock-master.md)。后继：[02 注册与发布](global-world-clock-02-registration.md)。
代码基线：`24f5c8205a304d3682e09e02458960bc2caa0463`；行号为此工作区审计快照，执行前核对上下文，不按行号盲改。

## 需求描述

交付一个全局时钟组件：最终注册后显示在顶栏「退出登录」左侧，宽度不足时只显示时钟图标；点击显示当前北京时间、可编辑的北京日期时间、常用欧美日韩对照和全球时区搜索。顶栏保持一行，组件状态不依附某个业务 view。

本子计划新增独立资源与测试，不修改注册页；完成后可以随包部署，但页面尚不激活该功能。02 负责激活、删除轮询日志入口和统一版本键。

必须保持：

- 现有九个导航按钮、`data-view`、未读徽标、刷新、登录、修改初始密码、退出登录的业务处理不变。
- 会议确认的表单、预览、正文、ICS、发送接口及其 DOM/CSS 不变。
- 不改变轮询任务、日志存储、任务记录页面；本次移除的是顶栏入口。

不做：新增后端接口／数据库字段、时区管理后台、时间同步服务、地图、第三方时间库、导航菜单重构、会议弹窗新增入口、跨设备保存、localStorage 偏好、改造既有缓存测试基础设施。前序建议中的“会议表单下再增加入口”没有纳入最终用户批准的全局入口设计。

## 关键不变量

### I-1：全局归属与原导航隔离
- Rule：一次页面生命周期至多一个 trigger、panel 和实例；trigger 插入 `.topnav-side` 的 `#logoutBtn` 前，panel 是 `.topnav` 的直接子节点，均不属于 `.view`。新按钮不得带 `.nav-tab` 或 `data-view`。业务 view 切换不销毁组件、不重置输入、不重新请求目录。
- Applies to：`mount`、自动启动、打开／关闭、销毁、紧凑布局切换。
- Violation consequence：访问专家页前功能不可用、重复计时器，或时钟点击进入 `setView(undefined)`。
- 来源：K-view-registration-triad（本次不新增 view）、K-relocated-control-refresh-owner；审计 R-1/R-2。

### I-2：顶栏一行与宽度退化
- Rule：`.topnav.world-clock-header`、导航按钮和 `.topnav-side` 不换行。优先尝试完整时间按钮；完整内容放不下时置 `.world-clock-icon-only`，只留 16px 时钟 SVG、完整 accessible name。图标态仍不足时仅导航区域横向滚动，退出按钮和时钟不能挤出屏幕。不增“更多”菜单，不移动／复制原导航节点。
- Applies to：初次可见、窗口大小、字体加载、用户名／徽标变化触发的 `fitHeader`。
- Violation consequence：重现当前 1100px 换行、导航不可达或宽度振荡。
- 来源：original；`styles.css:4123-4139`。

### I-3：当前时间与换算时间分离
- Rule：`now` 每次取设备当前时钟；顶栏显示北京 `MM月DD日 HH:mm`，面板显示北京 `YYYY-MM-DD HH:mm:ss`。首次打开时把当前北京日期与分钟填入输入框，冻结为 `selectedDate/selectedTime`；后续 tick 不覆盖输入或换算结果。关闭、重新打开及切 view 保留选择；「使用当前时间」才重新取当前值。页面刷新／认证被隐藏后重置。
- Applies to：tick、首次 open、输入事件、use-now、close、认证隐藏。
- Violation consequence：用户在编辑未来会议时间时被实时计时覆盖。
- 来源：original。不是服务器校时产品，不声称修正设备时钟误差。

### I-4：同一瞬间换算
- Rule：输入年份范围 2000–2100（包括端点），按 `Asia/Shanghai` 的现代北京时间解释为 UTC+08:00，转换成一个分钟精度 epoch。所有目标区都从同一个 epoch 通过 `Intl.DateTimeFormat` 的 IANA `timeZone` 换算。禁止取浏览器本地时区、固定纽约时差、后端目录 offset 做时间加减。当地日期、UTC 偏移、与北京时差、前一天／后一天均从该 epoch 推导。
- Applies to：`parseBeijingInput`、`projectZones`、日期／时间变更、UTC 偏移搜索。
- Violation consequence：DST 变更日、跨日或非整小时时区错误。
- 来源：original；审计 R-3。范围明确避开历史北京时间的夏令时转换问题，不扩展历史时间查询。

### I-5：目录来源与查询范围
- Rule：目录只来自 `api('/api/mail/meeting-confirmation/time-zones?date=YYYY-MM-DD', {signal})`。原始 `id/labelZh/aliases` 不修改，不把本次会话预览内嵌的目录复制到生产。首个成功响应在当前已登录页面内复用，切日期只重算 Intl 结果，不再请求目录。接口 offset 不进入结果／偏移筛选。常用固定为 London、Berlin、New_York、Los_Angeles、Tokyo、Seoul，并且只能从响应中找对应条目。任何非空搜索或区域选择切换为 global；清空搜索保留当前 scope；点击 common/global 按钮清空搜索、区域和页码。
- Applies to：load、retry、query、scope、region、分页。
- Violation consequence：搜索被常用范围限制，或使用假数据、旧偏移。
- 来源：original；复用 `MailboxMeeting.filterZones`，见审计 R-3。

### I-6：生命周期、认证与异步结果
- Rule：`open`、`selectedInitialized`、`catalogStatus=idle/loading/ready/error`、`scope=common/global`、`query`、`region`、`page`、`requestSeq` 仅为当前组件内存态，不写业务 state／任何持久化存储。一次至多一个目录请求；失败显式重试，不无限重试。shell 隐藏时关闭、停止 timer、abort 请求、递增 seq、清空目录与选择；晚到回包不能更新 DOM。shell 再显示时重新启动一个 timer，目录仍延迟到点击。`destroy` 解绑组件监听、observer、timer、请求和新增 DOM，不能解绑宿主事件。
- Applies to：mount、open/close、成功／失败响应、shell MutationObserver、visibilitychange、destroy。
- Violation consequence：退出后残留浮层、在途回包越过认证状态、后台重复请求或重复计时器。
- 来源：original；`app.js:14201-14250`、`task-modal-runtime.js:311-345`。

### I-7：只读边界
- Rule：组件通过既有 api 访问目录，不直接 fetch、不拼 contextPath、不调用会议 options/preview/send，不修改 meeting state、原目录对象、旧 `.nav-tab` 节点或任何业务 DOM。返回文本使用 textContent，固定模板之外不插入外部 HTML。
- Applies to：boot、网络、渲染、所有交互。
- Violation consequence：前缀重复、认证绕过、业务副作用、内容注入。
- 来源：K-download-context-path-host-injection、K-dom-stub-tests-hide-dangling-refs。

### I-8：资源发布边界（由 02 完成）
- Rule：01 不注册资源、不修改 index；02 的 11 个版本化资源同键 `20260917-global-world-clock`，新 CSS 在 meeting CSS 后、新 JS 在 app.js 后；`task-modal-runtime.js` 仍不带版本键。
- Applies to：01 文件范围、02 注册／缓存测试。
- Violation consequence：独立资源未激活、脚本先于 api 加载或旧浏览器缓存混用。
- 来源：K-frontend-cache-key-triad；审计 R-5。

### I-9：样式和可访问性边界
- Rule：下列 S-1～S-4 的 CSS 是完整最终文件，逐字复制；新 DOM 使用列出的模板和 class。禁止新增 inline style、`element.style`、预览 Tweak/Lucide 依赖、外部字体依赖或全局 button/p/input 规则。hidden 状态不会保留焦点，Escape／关闭按钮回焦 trigger；外部点击关闭时不抢用户点中的焦点。panel 是非模态 region，不加焦点锁或 body 滚动锁。
- Applies to：CSS、模板、事件、测试。
- Violation consequence：样式偏离、遮挡既有对话框、键盘无法退出。
- 来源：K-panel-bg-token-is-translucent、K-global-p-is-muted-in-dialogs、K-mailbox-popover-scope-and-fixed-containing-block。

## 样式契约

此节是实现依据。批准的会话预览仅证明交互方向，不得把其模拟页面、示例专家、模拟账号、导航菜单系统及多轮覆盖 CSS 搬入产品。

### S-1：全局顶栏与图标退化

- 复用：`.topnav`、`.brand`、`.brand-mark`、`.brand-title`、`.brand-subtitle`、`.nav-tabs`、`.nav-tab`、`.topnav-side`、`.user-info`、`.logout-btn`，现状见 `styles.css:140-258,4123-4151,5076-5083`。原规则不编辑；新文件用 `.topnav.world-clock-header` 派生覆盖。
- 使用位置：审计回执 E-3，相关 HTML class 位于 `index.html:61,72,73,76,143,144`；`.nav-tab` 的九个 data-view 保持原节点。`.logout-btn` 中删除日志入口由 02 限定，退出节点不改。
- 新 class：`world-clock-header`（mount 加到原 topnav）、`world-clock-trigger`、`world-clock-label`、`world-clock-chevron`、`world-clock-icon-only`。下方 CSS 已包含完整规则。
- trigger 宽度：完整态随文字，34px 高；紧凑态 34×34px、SVG 16×16px；coarse pointer 为 44×44px。header 间距 12px、横向 padding 20px，小屏 8px/10px。
- 1100px 下明确覆盖旧 `flex-wrap:wrap`、nav `order:3/flex-basis:100%`；640px 下隐藏品牌文字，保留 logo，利用既有隐藏用户名规则腾出空间。导航保持原顺序和原样式，允许自身横向滚动。

### S-2：浮层框架与当前时间

- panel 是顶栏直接子节点，`position:absolute; right:12px; top:calc(100% + 8px)`，宽度 `min(490px, calc(100vw - 24px))`，最大高度 `calc(100dvh - 96px)`，内容超高时面板内部滚动。
- 继承 header z-index 50，不抬到 modal 的 1000 之上，不创建透明遮罩。背景必须是实色白／深蓝，不能使用半透明 `--panel-bg`。
- 面板标题、当前时间、关闭按钮、表单、结果与分页的逐字 DOM 在本节给出。

### S-3：输入、搜索、范围、结果

- 日期／时间和搜索／区域各一排两列，440px 以下改为一列。样式覆盖均限定在 `.world-clock-panel` 内，不改全局 `input`、`p`。
- 结果列：国家城市 43%、当地日期时间 35%、时差 22%；当地时间 18px、IANA/UTC/日期 11px。跨日徽标文字为前一天／后一天。
- 常用默认六行；全球分页每页六行。输入、搜索、范围、区域改变均把 page 归零；搜索无结果显式空态。
- `aria-live=polite` 只放结果数量／异常，不放每秒变化的钟面；UTC、日期、时差同时有文字，不依赖颜色。

### S-4：完整最终 CSS 与 DOM

文件 `src/main/resources/static/world-clock.css` 必须与下方 fenced block 完全一致（UTF-8/LF，末尾一个换行）。子计划 01 的测试直接提取这段比对，不另造第二份 CSS 来源。hover/active/focus-visible/disabled、暗色、窄屏与 reduced-motion 已在同一块中。

<!-- WORLD_CLOCK_CSS_BEGIN -->
```css
.topnav.world-clock-header {
    --wc-bg: #ffffff;
    --wc-soft: #f8faff;
    --wc-border: #dce4ef;
    --wc-ink: #334155;
    --wc-muted: #64748b;
    --wc-blue: #1e40af;
    --wc-tint: #eef4ff;
    --wc-warning-bg: #fff5e8;
    --wc-warning: #9a5715;
    --wc-error: #be123c;
    display: flex;
    flex-wrap: nowrap;
    align-items: center;
    gap: 12px;
    padding: 10px 20px;
    min-width: 0;
}
.topnav.world-clock-header > .nav-tabs {
    order: 0;
    flex: 1 1 auto;
    flex-basis: auto;
    flex-wrap: nowrap;
    min-width: 0;
    overflow-x: auto;
    overflow-y: hidden;
    scrollbar-width: thin;
    padding-bottom: 0;
}
.topnav.world-clock-header > .nav-tabs > .nav-tab { flex: 0 0 auto; }
.topnav.world-clock-header > .topnav-side { flex: 0 0 auto; flex-wrap: nowrap; gap: 8px; }
.world-clock-trigger {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 7px;
    flex: 0 0 auto;
    min-height: 34px;
    padding: 6px 11px;
    border: 1px solid var(--wc-border);
    border-radius: 999px;
    background: var(--wc-bg);
    color: var(--wc-blue);
    font-family: var(--font-body);
    font-size: 12px;
    line-height: 20px;
    font-weight: 500;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
    cursor: pointer;
    transition: background-color .15s ease, border-color .15s ease;
}
.world-clock-trigger > svg { display: block; width: 16px; height: 16px; flex: none; }
.world-clock-trigger:hover, .world-clock-trigger[aria-expanded="true"] { background: var(--wc-tint); border-color: var(--wc-blue); }
.world-clock-trigger:active { background: var(--wc-tint); }
.world-clock-trigger.world-clock-icon-only { width: 34px; height: 34px; padding: 8px; gap: 0; }
.world-clock-icon-only > .world-clock-label, .world-clock-icon-only > .world-clock-chevron { display: none; }
.world-clock-panel {
    position: absolute;
    top: calc(100% + 8px);
    right: 12px;
    z-index: 1;
    width: min(490px, calc(100vw - 24px));
    max-height: calc(100vh - 96px);
    max-height: calc(100dvh - 96px);
    overflow: auto;
    overscroll-behavior: contain;
    border: 1px solid var(--wc-border);
    border-radius: 12px;
    background: var(--wc-bg);
    color: var(--wc-ink);
    box-shadow: 0 16px 48px rgba(35, 58, 96, .18);
    font-family: var(--font-body);
    font-size: 12px;
    line-height: 1.6;
    text-align: left;
}
.world-clock-panel *, .world-clock-trigger { box-sizing: border-box; }
.world-clock-panel[hidden], .world-clock-panel [hidden] { display: none !important; }
.world-clock-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 15px 18px; border-bottom: 1px solid var(--wc-border); }
.world-clock-head h2 { margin: 0; font: 500 14px/1.5 var(--font-body); color: var(--wc-ink); }
.world-clock-caption { display: block; color: var(--wc-muted); font-size: 11px; line-height: 1.6; }
.world-clock-now-label { margin-top: 5px; }
.world-clock-now { display: block; color: var(--wc-blue); font-size: 16px; line-height: 1.6; font-variant-numeric: tabular-nums; }
.world-clock-close { flex: none; display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; border: 0; border-radius: 5px; background: transparent; color: var(--wc-muted); font: 400 21px/1 var(--font-body); cursor: pointer; }
.world-clock-close:hover, .world-clock-close:active { background: var(--wc-tint); color: var(--wc-blue); }
.world-clock-link { padding: 0; border: 0; border-radius: 3px; background: transparent; color: var(--wc-blue); font: 400 11px/1.6 var(--font-body); cursor: pointer; }
.world-clock-link:hover { text-decoration: underline; }
.world-clock-link:active { background: var(--wc-tint); }
.world-clock-body { padding: 15px 18px; }
.world-clock-fields { display: grid; grid-template-columns: 1.4fr 1fr; gap: 10px; margin-bottom: 14px; }
.world-clock-panel label { display: flex; flex-direction: column; gap: 6px; margin: 0; min-width: 0; color: var(--wc-muted); font-size: 12px; font-weight: 400; letter-spacing: 0; text-transform: none; }
.world-clock-panel input, .world-clock-panel select { display: block; width: 100%; min-width: 0; height: 36px; margin: 0; padding: 7px 10px; border: 1px solid var(--wc-border); border-radius: 7px; background: var(--wc-bg); color: var(--wc-ink); box-shadow: none; font: 400 12px/20px var(--font-body); }
.world-clock-panel input::placeholder { color: var(--wc-muted); opacity: 1; }
.world-clock-panel :is(input, select):hover:not(:disabled) { border-color: var(--wc-blue); }
.world-clock-panel :is(input, select)[aria-invalid="true"] { border-color: var(--wc-error); }
.world-clock-filters { display: grid; grid-template-columns: minmax(0, 1fr) 110px; gap: 9px; margin-bottom: 12px; }
.world-clock-tabs { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
.world-clock-tab { padding: 4px 9px; border: 1px solid transparent; border-radius: 6px; background: transparent; color: var(--wc-muted); font: 400 12px/20px var(--font-body); cursor: pointer; }
.world-clock-tab:hover { background: var(--wc-soft); }
.world-clock-tab:active, .world-clock-tab[aria-pressed="true"] { background: var(--wc-tint); border-color: var(--wc-border); color: var(--wc-blue); }
.world-clock-results-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 9px; }
.world-clock-results-head strong { font-weight: 500; }
.world-clock-table { width: 100%; margin: 0; border-collapse: collapse; table-layout: fixed; background: var(--wc-bg); color: var(--wc-ink); }
.world-clock-table thead { background: var(--wc-soft); }
.world-clock-table th { padding: 7px 8px; border: 0; background: var(--wc-soft); color: var(--wc-muted); text-align: left; font-size: 11px; line-height: 1.6; font-weight: 400; white-space: normal; }
.world-clock-table th:first-child { width: 43%; }
.world-clock-table th:nth-child(2) { width: 35%; }
.world-clock-table th:last-child { width: 22%; text-align: right; }
.world-clock-table td { padding: 8px; border: 0; border-bottom: 1px solid var(--wc-border); background: var(--wc-bg); color: var(--wc-ink); text-align: left; vertical-align: middle; white-space: normal; overflow-wrap: anywhere; font-size: 12px; line-height: 1.6; }
.world-clock-table tr:hover td { background: var(--wc-soft); }
.world-clock-table td:last-child { text-align: right; }
.world-clock-name { display: block; font-size: 12px; font-weight: 500; }
.world-clock-zone { display: block; color: var(--wc-muted); font-size: 11px; line-height: 1.5; overflow-wrap: anywhere; }
.world-clock-local { display: block; font-size: 18px; line-height: 1.4; font-weight: 500; font-variant-numeric: tabular-nums; }
.world-clock-date { display: block; color: var(--wc-muted); font-size: 11px; line-height: 1.5; font-variant-numeric: tabular-nums; }
.world-clock-day { display: inline-block; margin-left: 5px; padding: 1px 4px; border-radius: 4px; background: var(--wc-warning-bg); color: var(--wc-warning); font-size: 11px; line-height: 1.5; vertical-align: middle; font-weight: 400; }
.world-clock-status { margin: 0 0 12px; color: var(--wc-muted); font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.world-clock-error { color: var(--wc-error); }
.world-clock-empty { padding: 22px 8px; color: var(--wc-muted); text-align: center; font-size: 12px; line-height: 1.6; }
.world-clock-footer { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.world-clock-pager { display: flex; align-items: center; gap: 8px; color: var(--wc-muted); font-size: 11px; }
.world-clock-page { border: 1px solid var(--wc-border); border-radius: 5px; padding: 3px 7px; background: var(--wc-bg); color: var(--wc-ink); font: 400 11px/18px var(--font-body); cursor: pointer; }
.world-clock-page:hover:not(:disabled) { background: var(--wc-tint); border-color: var(--wc-blue); }
.world-clock-page:active:not(:disabled) { background: var(--wc-tint); }
.world-clock-trigger:focus-visible, .world-clock-panel :is(button, input, select):focus-visible { outline: 2px solid #82a8e8; outline-offset: 2px; }
.world-clock-trigger:disabled, .world-clock-panel :is(button, input, select):disabled { opacity: .5; cursor: not-allowed; }
@media (max-width: 640px) {
    .topnav.world-clock-header { gap: 8px; padding: 10px; }
    .topnav.world-clock-header > .brand > div:last-child { display: none; }
    .topnav.world-clock-header > .topnav-side { gap: 5px; }
    .topnav.world-clock-header #logoutBtn { padding: 6px 8px; }
}
@media (max-width: 440px) {
    .world-clock-body, .world-clock-head { padding: 13px; }
    .world-clock-fields, .world-clock-filters { grid-template-columns: minmax(0, 1fr); }
    .world-clock-table th, .world-clock-table td { padding-left: 5px; padding-right: 5px; }
    .world-clock-day { display: block; width: max-content; margin: 2px 0 0; }
}
@media (pointer: coarse) {
    .world-clock-trigger, .world-clock-trigger.world-clock-icon-only { min-width: 44px; min-height: 44px; }
    .world-clock-panel :is(button, input, select) { min-height: 44px; }
    .world-clock-close { width: 44px; }
    .world-clock-panel input, .world-clock-panel select { font-size: 16px; }
}
@media (prefers-color-scheme: dark) {
    .topnav.world-clock-header { --wc-bg: #182232; --wc-soft: #1e293b; --wc-border: #364359; --wc-ink: #e2e8f0; --wc-muted: #a8b6ca; --wc-blue: #a5c4ff; --wc-tint: #243855; --wc-warning-bg: #443222; --wc-warning: #f5c38f; --wc-error: #ff9db5; }
    .world-clock-panel { box-shadow: 0 16px 48px rgba(0, 0, 0, .4); }
}
@media (prefers-reduced-motion: reduce) {
    .world-clock-trigger { transition: none; }
}
```
<!-- WORLD_CLOCK_CSS_END -->

固定模板（动态文本用空节点，不把示例时间写入生产）。trigger 与 panel 分别插入指定宿主；字符串内容按下面 HTML 使用，允许仅为 JS 字符串转义，不允许改标签、class、id、属性或层级。

<!-- WORLD_CLOCK_TRIGGER_BEGIN -->
```html
<button type="button" id="worldClockTrigger" class="world-clock-trigger" aria-label="查看当前北京时间与全球时区" aria-expanded="false" aria-controls="worldClockPanel">
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"></circle><path d="M12 7v5l3 2"></path></svg>
    <span id="worldClockHeaderTime" class="world-clock-label"></span>
    <span class="world-clock-label">北京时间</span>
    <span class="world-clock-chevron" aria-hidden="true">⌄</span>
</button>
```
<!-- WORLD_CLOCK_TRIGGER_END -->

<!-- WORLD_CLOCK_PANEL_BEGIN -->
```html
<section id="worldClockPanel" class="world-clock-panel" role="region" aria-labelledby="worldClockTitle" hidden>
    <header class="world-clock-head">
        <div>
            <h2 id="worldClockTitle">全球时间</h2>
            <span class="world-clock-caption world-clock-now-label">当前北京时间</span>
            <time id="worldClockNow" class="world-clock-now"></time>
            <button type="button" id="worldClockUseNow" class="world-clock-link">使用当前时间换算</button>
        </div>
        <button type="button" id="worldClockClose" class="world-clock-close" aria-label="关闭全球时间">×</button>
    </header>
    <div class="world-clock-body">
        <div class="world-clock-fields">
            <label for="worldClockDate">日期（北京时间）<input id="worldClockDate" type="date" min="2000-01-01" max="2100-12-31" required></label>
            <label for="worldClockTime">时间（北京时间）<input id="worldClockTime" type="time" step="60" required></label>
        </div>
        <p id="worldClockInputError" class="world-clock-status world-clock-error" role="alert" hidden>请填写 2000–2100 年内有效的北京日期和时间。</p>
        <div class="world-clock-filters">
            <label for="worldClockSearch">搜索全球时区<input id="worldClockSearch" type="search" placeholder="国家、城市、IANA 或 UTC+5:30" autocomplete="off"></label>
            <label for="worldClockRegion">时区分组<select id="worldClockRegion"><option value="">全部</option><option value="Europe">欧洲</option><option value="America">美洲</option><option value="Asia">亚洲</option><option value="Africa">非洲</option><option value="PacificAustralia">太平洋/澳洲</option><option value="Other">其他/别名</option></select></label>
        </div>
        <div class="world-clock-tabs" role="group" aria-label="时区范围">
            <button type="button" id="worldClockCommon" class="world-clock-tab" aria-pressed="true">常用 · 欧美日韩</button>
            <button type="button" id="worldClockGlobal" class="world-clock-tab" aria-pressed="false">全球时区</button>
        </div>
        <p id="worldClockLoadStatus" class="world-clock-status" role="status" hidden></p>
        <button type="button" id="worldClockRetry" class="world-clock-link" hidden>重新加载时区</button>
        <div class="world-clock-results-head"><strong>同一时刻 · 全球对照</strong><span id="worldClockCount" class="world-clock-caption" aria-live="polite"></span></div>
        <table id="worldClockTable" class="world-clock-table" aria-label="所选北京时间对应的全球时间"><thead><tr><th scope="col">国家 / 城市</th><th scope="col">当地日期与时间</th><th scope="col">与北京时差</th></tr></thead><tbody id="worldClockRows"></tbody></table>
        <div id="worldClockEmpty" class="world-clock-empty" hidden>没有匹配的时区，试试“伦敦”或“UTC+5:30”。</div>
        <div id="worldClockUnsupported" class="world-clock-caption" role="status" hidden></div>
        <footer class="world-clock-footer">
            <span class="world-clock-caption">按所选时刻换算 · 自动适配夏令时</span>
            <div id="worldClockPager" class="world-clock-pager" hidden><button type="button" id="worldClockPrev" class="world-clock-page">上一页</button><span id="worldClockPageLabel"></span><button type="button" id="worldClockNext" class="world-clock-page">下一页</button></div>
        </footer>
    </div>
</section>
```
<!-- WORLD_CLOCK_PANEL_END -->

动态结果行（S-3）：每行按下面层级建立；`world-clock-day` 无跨日时 hidden；其余节点均使用 textContent。不得给条目添加点击选择／写回会议的行为。

<!-- WORLD_CLOCK_ROW_BEGIN -->
```html
<tr>
    <td><span class="world-clock-name"></span><span class="world-clock-zone"></span></td>
    <td><span class="world-clock-local"><span data-field="time"></span><span class="world-clock-day" hidden></span></span><span class="world-clock-date"></span></td>
    <td><span data-field="difference"></span><span class="world-clock-zone" data-field="offset"></span></td>
</tr>
```
<!-- WORLD_CLOCK_ROW_END -->

状态文案也属 S-3：loading「正在加载时区…」；error「时区加载失败，请重试。」；浏览器不支持时区提示「当前浏览器有 N 个目录时区暂不可用。」；无合法目录时视为 error，不显示常用假数据。错误状态 `.world-clock-error` 仅附加到 load status，成功／重试前移除。

## 现状审计

### R-1：页面宿主与生命周期

- `index.html:60-166` 是 `.app-shell > .topnav`；`.main` 从 169 开始，业务 `.view` 在其内。`setView`（`app.js:1738-1763`）只切换 `.nav-tab/.view` 并刷新，未重建顶栏。
- `bindEvents` 的精确代码（`app.js:12657-12660`）：

```js
    $$(".nav-tab").forEach((tab) => tab.addEventListener("click", () => {
        if (tab.dataset.view === "mailbox") clearMailboxExpertFocus();
        setView(tab.dataset.view);
    }));
```

这证明新 trigger 不能套 `.nav-tab`，不是凭风格判断。现有 logout 业务的 class／handler 不在本次修改范围。

- `startAuthenticatedApp`（14201-14228）设置用户名并将 `shell.style.display="grid"`；`stopAuthenticatedApp`（14231-14250）设为 `"none"`。新组件据此观察 shell 的 `style/hidden` 属性，不替换 auth 函数。
- `bootstrap`（15620-15627，调用在 17997）绑定业务事件后异步 `checkAuth`。新 script 必须放在 app.js 后，boot 验证 `typeof window.api === 'function'`。这是普通 script 的顶层函数声明（1540），不是 ES module；不读取 `window.contextPath`。
- `task-modal-runtime.js:311-345` 在 401 和要求改密的 403 中调用 `window.stopAuthenticatedApp()` 后抛错，目录请求沿此路径退出，不重复写认证 UI。
- 应用事件读取者：原 `bindEvents`、`setView`、auth 回调。新 DOM 写入点：mount 插 trigger/panel，加 header class；fit 改 trigger class；渲染改自有节点；close/认证隐藏/destroy 清理。没有新 data-view 注册、业务 state 写入或跨页面 refresh 分支。

### R-2：前端样式盘点与改动前基线

- 原主色 `#1e40af`、正文 `#334155/#1e293b`、辅助 `#64748b/#94a3b8`；圆角 token 7/10/18px，`--z-overlay=50`、`--z-modal=1000`（`styles.css:1-94`）。暗色自动随系统，实值见 9693-9766。
- `--panel-bg` 浅色为 `rgba(255,255,255,.55)`，深色为 `rgba(21,31,48,.55)`，因此本浮层单独采用完全不透明色。（来源：K-panel-bg-token-is-translucent）
- 全局 `p` 在 306-310 降为灰色12px；新 status 明确声明文字颜色，不改全局规则。（来源：K-global-p-is-muted-in-dialogs）
- 目标区域原样（`index.html:143-166` 中关键结构；完整 SVG/原文见 `global-world-clock-audit.txt`）：

```html
        <div class="topnav-side">
            <div class="user-info">
                当前登录: <span id="currentUserDisplay">admin</span>
            </div>
            <button class="nav-tab logout-btn" id="showPollLogBtn" onclick="showPollLog()">
```

- 顶栏及换行规则原文：

```css
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
```

```css
@media (max-width: 1100px) {
    .topnav {
        flex-wrap: wrap;
        row-gap: 8px;
    }

    .nav-tabs {
        overflow-x: auto;
        order: 3;
        flex-basis: 100%;
        padding-bottom: 4px;
    }

    .nav-tab {
        white-space: nowrap;
        padding: 6px 12px;
    }
}
```

- E-2/E-3 给出选择器定义与引用回执；完整的 `.brand/.nav-tab/.topnav-side` 规则原文按行保存在审计附件，避免只记录抽象颜色意图。
- 不把面板放进 `.panel` 或 body portal：topnav 已有 `position:relative` 与 z-index；其 blur 会改变 fixed 包含块，所以使用直接子节点 absolute。（来源：K-mailbox-popover-scope-and-fixed-containing-block）

### R-3：时区目录与换算数据

- API：`MeetingConfirmationController.kt:42-47`，GET `/api/mail/meeting-confirmation/time-zones?date=`，只要求 ISO 日期，不要求 contactId/processingId。
- DTO：`MeetingConfirmationModels.kt:77-86`：`id,labelZh,aliases,offsetLabel,offsetSeconds`；注释明示 offset 只是该日期12:00 UTC的辅助展示。
- 服务方法原文（`MeetingConfirmationService.kt:63-80`）：

```kotlin
    fun timeZones(date: LocalDate): List<MeetingTimeZoneOption> {
        val noonUtc = date.atTime(12, 0).toInstant(ZoneOffset.UTC)
        val zoneIds = catalogZoneIds()
        val orderedIds = COMMON_ZONE_IDS.filter { it in zoneIds } +
            (zoneIds - COMMON_ZONE_IDS.toSet()).sorted()
        return orderedIds.map { id ->
            val offset = ZoneId.of(id).rules.getOffset(noonUtc)
            val metadata = TIME_ZONE_CATALOG[id]
                ?: throw IllegalStateException("时区中文目录缺少条目：$id")
            MeetingTimeZoneOption(
                id = id,
                labelZh = metadata.labelZh,
                aliases = metadata.aliases,
                offsetLabel = formatUtcOffset(offset.totalSeconds),
                offsetSeconds = offset.totalSeconds
            )
        }
    }
```

- 这段链路读取 JDK ZoneId 与内存目录；不调用方法所在 service 的其他 repository 成员。只读结论限于这个 GET，不推广到整个 meeting service。
- 文件 schema：`meeting-timezones-zh.properties` 每行 `id=labelZh<TAB>alias<US>alias`；生成器 `scripts/generate_meeting_timezone_catalog.py:145-175` 通过 `--output` 写文件。生产读取 `MeetingConfirmationService.kt:643-657`，测试读取 `MeetingConfirmationServiceTest.kt:568-580`。文件名引用回执 E-8；本计划不写此文件、不重新生成目录。
- 常用六区在资源文件的明确行：London413、Berlin392、New_York172、Los_Angeles150、Tokyo324、Seoul313；首尔不在 service 的 COMMON_ZONE_IDS 中，因此新组件自己定义“显示顺序”，不是声称后端已提供该六项分组。
- 当前前端目录读取在 `meeting-confirmation.js:837,874-877`；搜索实现 104-122，导出 `filterZones` 在 1454，`global.MailboxMeeting` 在1462。
- 特别注意：旧搜索在子串命中后直接返回 true（115-118），所以 `UTC+05` 可能匹配 `UTC+05:30`。新组件对完整 UTC 偏移串先做数值精确筛选，非偏移文本再用原 `filterZones`；不改旧函数。
- 新读取方只拿响应的元信息，给它构造独立的按 epoch 投影。保留后端条目顺序和 IANA alias，不做跨条目合并；用户能从显示的 ID 区分别名。Intl 不支持某 ID 时跳过该条并显示数量，不用后端 noon offset 兜底。

### R-4：网络／认证前缀

- `app.js:541-544` 的 contextPath 是顶层 const；`api:1540-1555` 加此前缀、传透 signal、先走 handleAuthResponse 再读 JSON。新模块在 app.js 后加载，通过 `window.api` 调用，不复制路径计算。（来源：K-download-context-path-host-injection）
- `AuthWebConfig.kt:23-25` 对 `/api/**` 拦截，仅 auth/login、auth/me 豁免；目录不在豁免中。`AuthInterceptor` 检查 session 与 mustChangePassword。因此不应通过“公开静态 JSON”另起时区来源。

### R-5：测试、缓存、日志按钮

- 9 个版本资源／9 份固定键测试的逐行回执在 E-5/E-6；02 列出名单与目标。新增 CSS+JS 后目标是11项。
- `showPollLogBtn` 查询命中 `index.html:147`；`showPollLog` 在 app.js7332，原日志 panel632及 close binding14114 仍可独立保留。匹配命令覆盖 `src/test/js`，本次该目录没有命中旧按钮名称；不把“无按钮测试”推断成日志功能可整段删除。（来源：K-ui-removal-retires-obsolete-contract-tests）
- 基线实际运行：`node --test src/test/js/meetingConfirmation.test.js src/test/js/meetingConfirmationAssets.test.js`，33 tests、0 fail，完整日志在审计附件。没有运行 Maven、生产接口或真实浏览器；不据此宣称 UI 已验证。
- `pom.xml:183-233` 的 exec-maven-plugin 在 test 阶段运行 Node 测试；独立运行命令写在验收节。（来源：K-js-tests-run-via-exec-plugin）

### R-6：交互点清单

| 编号 | 写入方 → 读取方 | 本次约束 |
|---|---|---|
| IP-1 | auth 写 shell display → 时钟观察可见性 | show 启动、hide 清空/终止；不能抢登录处理 |
| IP-2 | API DTO → Intl 投影 → 搜索/结果/分页 | 元信息保留、offset 按选中 epoch 重算 |
| IP-3 | 输入框或 use-now → selected epoch → 六区/全球结果 | tick 只更新 current，不写 selected |
| IP-4 | 原 nav/用户名/未读徽标/字体布局 → fitHeader | 原节点不移动、只改变新时钟显示密度 |
| IP-5 | 02 资源顺序 → boot → api/filterZones | 新 JS 后置加载，单例；共同版本键 |
| IP-6 | panel open/close 与异步响应 → DOM | 关闭不重新打开，认证隐藏/销毁拒绝晚到响应 |

知识采纳：本次读入的11条均已在上述约束/审计引用；K-view-registration-triad 作为“不新增 view”的边界，未执行其新增 view 步骤。没有新业务存储，因此不审计与本功能无关的 ES/MySQL 全库写路径。

## 实现方案

### T1：构造最小组件和纯换算函数（I-3/I-4/I-5/I-7；S-3/S-4）

文件：`src/main/resources/static/world-clock.js`。

1. 普通 IIFE，浏览器导出 `window.WorldClock`，Node 导出 `module.exports`；API 保留 `mount, parseBeijingInput, projectZones` 即可，不建立通用插件／全站状态框架。`mount({header, api, filterZones})` 返回 `{open,close,destroy}`，可在测试以 fake Date/timer/DOM 控制时钟，不增测试专用产品分支。
2. `parseBeijingInput(date,time)`：完整匹配 YYYY-MM-DD 与 HH:mm；校验年份2000–2100、月日有效性和闰年、小时0–23/分钟0–59；先数值构造 UTC 日历并反查年月日，不依赖 Date 自动归一化；epoch=`Date.UTC(y,m-1,d,h,minute)-8*3600000`。失败返回明确 invalid，不拿最近一次合法值继续展示旧结果。清空／无效时隐藏 table/empty/pager，显示输入错误；当前时间与输入仍可用。
3. `projectZones(rawZones,epoch)`：缓存每个 id 的 formatter，options 固定 `year/month/day/hour/minute`、`hourCycle:'h23'`、显式 timeZone；从 formatToParts 拼当地 YYYY-MM-DD 和 HH:mm，不做本地 getHours。`offsetSeconds=(Date.UTC(localParts)-epoch)/1000`，`differenceSeconds=offsetSeconds-28800`。相对日以当地 YYYY-MM-DD 与所选北京 YYYY-MM-DD 的 UTC 日历日差求得。新建投影对象，不原地覆盖 rawZones；显示 UTC±HH:mm。
4. 间隔时差格式精确到分钟：慢12小时、慢2小时30分、快45分、无时差；不要四舍五入成整数小时。前一天/后一天徽标同时显示完整当地日期。
5. 不支持的 zone 捕获 formatter 的 RangeError 并统计；拒绝空 id/非字符串 label/非数组 aliases 等不合契约条目，不让一个坏条目崩全表。整个响应不是数组或可用条目为0时 error。

### T2：目录、筛选和局部渲染（I-3～I-7/I-9；S-2～S-4）

文件：`src/main/resources/static/world-clock.js`。

- 初次打开有效 shell：冻结当前选择，启动一次目录 GET。成功后 cache 元信息；重开／改日期不重复 GET。close 不强制丢弃一次正常在途请求，响应可入当前实例内存，但不能改 open=true；认证隐藏/destroy 才 abort+invalidate。
- 时间输入可在 loading 中修改；响应到达后使用最新合法 selected epoch投影。目录 date 参数是发起时有效北京日期（若输入未完成用当天北京日期）；元信息复用不受该日期绑定，绝不使用响应 offset。
- 搜索/分组在 loading/error 中禁用；日期、时间、关闭、use-now 可用；error显示 retry，retry运行中不可重复触发。
- 常用 ID 顺序：`Europe/London, Europe/Berlin, America/New_York, America/Los_Angeles, Asia/Tokyo, Asia/Seoul`。缺失某条目不造元信息，支持数量按真实结果显示。
- global 使用目录原顺序，先按 id 首段分组（Europe/America/Asia/Africa；Pacific和Australia→PacificAustralia；其他→Other），这是目录分组，不推断国家大洲。别名仍展示原ID。
- UTC完整串 `/^utc([+-])(\d{1,2})(?::(\d{2}))?$/i` 先归一全角／减号；分钟0–59、绝对偏移≤14小时，按投影 offsetSeconds 精确匹配。其他文本交给注入的 `MailboxMeeting.filterZones(projected,query)`。
- 过滤后计数、每页6条，page clamp；0条隐藏 pager，1页隐藏 pager，边界按钮 disabled。每次render只替换 tbody 和状态文本；输入节点保持，避免逐键丢焦点。（搜索测试必须真实触发 input，而非只测 helper。）

### T3：宿主集成与单行布局（I-1/I-2/I-6/I-7/I-9；S-1～S-4）

文件：`src/main/resources/static/world-clock.js`、`src/main/resources/static/world-clock.css`。

- 直接采用 S-4 CSS、trigger/panel/row 模板。marker、新增 DOM 和公开实例都带重复挂载防护；destroy 后可重新挂载。boot 若没有预期 header/side/logout/api/filterZones 则不挂载、不加 header marker，不导致 app.js 的其余功能失败。
- 脚本位于 app.js 后；自动启动在 DOMContentLoaded（若已就绪立即启动）。读取 `.app-shell` 可见性，不依赖先访问任何 view。初始 hidden 不开始计时／发请求。
- shell observer 只观察 shell 的 style/hidden 属性；隐藏时按 I-6 清理，会话恢复后只开始读钟，等待点击目录请求。visibilitychange 暂停后台 tick、回来立即刷新 current；保持 selected 和目录不变。一个1000ms timer取真实 now，不做“上次+1秒”。destroy清除observer、timer、document事件及新DOM。
- 外部 pointerdown 关闭（panel和trigger内部不关）；trigger 切换、close与Escape关闭。打开焦点落日期；关闭按钮/Escape返回trigger；点击原导航属于外部点击，可关闭浮层，但所选时间/筛选仍保留。
- 若Tab把焦点移出panel与trigger，关闭浮层但不回抢焦点；认证隐藏时若焦点仍在浮层内则blur，不把焦点送往已隐藏的trigger。开关同步aria-expanded与箭头⌃/⌄；`worldClockNow`的datetime同步当前UTC ISO值。
- `fitHeader` 每次同步去掉 icon-only 进行一次完整态测量，再根据实际可用宽度设置最终状态；同一回调内完成，避免跨帧闪烁。宽度不取已收缩的 nav.clientWidth；取原导航各按钮 border-box宽度+横向margin+gap总和，再加brand和side所需宽度、header两侧padding和两个主gap。以所需宽度>header.clientWidth为紧凑判据（容差1px）；**不能以“当前已经压缩后的总宽度”作为恢复完整态的条件**。
- ResizeObserver 观察 header；字体 `document.fonts.ready` 后重算；另一个 MutationObserver 只观察原 nav 的 childList/characterData/hidden属性（徽标），以及currentUserDisplay的characterData/childList。所有触发经单个 requestAnimationFrame合并。不要观察整个header全部DOM，否则每秒钟面变化会触发布局回环。destroy取消待处理rAF。
- 原导航节点不复制不挪动，原 click listener保留；导航区域继续使用水平滚动。不引入预览的9份模拟view或示例数据。

### T4：测试与阶段交付（I-1～I-9；S-1～S-4）

文件：`src/test/js/worldClock.test.js`。

- 测试中加载生产 `meeting-confirmation.js` 导出的真实 filterZones；检查正文测试 fixture只在测试中、生产模块没有静态目录。
- 测试真实组件事件路径、资源未注册时的独立导出、重复mount/destroy、输入无效、失败/重试、晚到回包和认证隐藏。DOM stub须缺失节点返回null，不能用“任意ID自动创建”的假对象。（来源：K-dom-stub-tests-hide-dangling-refs）
- CSS字节测试从本计划 WORLD_CLOCK_CSS标记提取；模板测试从三个HTML标记提取并与模块常量/生成DOM对比，允许HTML序列化的属性次序差异，不允许缺节点、inline style或新class。取模板时使用初始状态对比；运行中的hidden/aria-expanded/aria-pressed/disabled/aria-invalid、datetime以及已声明的状态class按相应状态断言，不拿动态状态误判静态模板不一致。
- 01阶段不断言 index 已注册新资源、不改现有cache测试。02在现有asset测试中添加注册/删除入口断言。因此01可独立绿测并且没有对现有页面的可见影响。

## 变更文件清单

| 文件 | 操作 | 责任 |
|---|---|---|
| `src/main/resources/static/world-clock.js` | 新增 | 纯换算、目录/搜索、全局宿主与响应式 |
| `src/main/resources/static/world-clock.css` | 新增 | S-4逐字样式 |
| `src/test/js/worldClock.test.js` | 新增 | 计算、行为、生命周期、模板与样式契约 |

本子计划实现文件3个，子系统1个（全局前端组件），持久化字段0个。计划文档、审计附件、知识元数据属于本轮规划产物，不授权执行agent改其他业务文件。

## 验收标准

| 不变量 | 验证和明确断言 |
|---|---|
| I-1 | mount两次仍一个trigger/panel；trigger在logout前，不带nav-tab/data-view；切九个原view不增timer/目录请求 |
| I-2 | 模拟测量required=1200/available=1199→icon；available=1201→full；同宽重算不抖动。浏览器另外检查单行与滚动，DOM stub不能证明布局 |
| I-3 | fake now前进60秒，只current/header变化；已选2026-01-17 15:00和各区结果不变；use-now更新输入 |
| I-4 | 下表全部精确值；TZ=UTC和TZ=America/Los_Angeles运行结果一致；2026-02-30、24:00、1999/2101、空串均invalid |
| I-5 | 获取使用GET目录路径、单次成功cache；中文/英文/IANA搜索；UTC+5:30不得包含+5:45；非空搜索自动global；页码重置、clamp |
| I-6 | loading时重复open/retry不重复请求；hide→abort/seq失效且零timer；旧响应不写DOM；restore一个timer；destroy后无事件生效 |
| I-7 | transport spy只收到目录GET（无POST）；路径不带重复前缀；冻结rawZones后仍可投影；恶意label作为纯文本 |
| I-8 | git diff仅列出的三个实现文件；index旧注册保持不变；最终注册断言在02执行 |
| I-9 | CSS与模板精确比对；未知class/inline style/element.style零新增；键盘Escape/外部点击行为明确 |

确定性换算样例（规划阶段已用本机Node Intl实际计算，执行测试应把期望硬编码，不能用被测函数生成expected）：

| 北京输入 | 目标 | 当地结果 | UTC偏移 |
|---|---|---|---|
| 2026-09-17 15:00 | London | 2026-09-17 08:00 | +01:00 |
| 同上 | Berlin | 2026-09-17 09:00 | +02:00 |
| 同上 | New_York | 2026-09-17 03:00 | -04:00 |
| 同上 | Los_Angeles | 2026-09-17 00:00 | -07:00 |
| 同上 | Tokyo / Seoul | 2026-09-17 16:00 | +09:00 |
| 2026-01-17 15:00 | Los_Angeles | 2026-01-16 23:00（前一天） | -08:00 |
| 2026-09-17 23:30 | Tokyo | 2026-09-18 00:30（后一天） | +09:00 |
| 2026-09-17 15:00 | Kolkata | 2026-09-17 12:30 | +05:30 |
| 同上 | Kathmandu | 2026-09-17 12:45 | +05:45 |
| 2026-03-08 14:30 | New_York | 2026-03-08 01:30 | -05:00 |
| 2026-03-08 15:30 | New_York | 2026-03-08 03:30 | -04:00 |
| 2026-11-01 13:30 | New_York | 2026-11-01 01:30 | -04:00 |
| 2026-11-01 14:30 | New_York | 2026-11-01 01:30 | -05:00 |

S-1：目标CSS和DOM字节契约、1100px覆盖精确存在；1920/1440/1100/1024/640/375/320px宽度无页面级横向溢出，导航可以自己横向滚动；S-2：面板实色、宽度490px上限、右侧12px、modal之下；S-3：6行分页、输入焦点不丢、错误/空态互斥；S-4：暗色/disabled/键盘焦点/coarse pointer/reduced-motion全包含。

命令：

```bash
node --check src/main/resources/static/world-clock.js
node --test src/test/js/worldClock.test.js
TZ=UTC node --test src/test/js/worldClock.test.js
TZ=America/Los_Angeles node --test src/test/js/worldClock.test.js
node --test src/test/js/meetingConfirmation.test.js src/test/js/meetingConfirmationAssets.test.js
```

两个不同TZ是时区不变量的必要覆盖，不代替真实浏览器验收。不运行无关全库集成作为本阶段通过依据；最终全量JS由02执行。

## 人工验收清单

01尚未注册，以下UI验收在02完成后执行；01独立验收使用本地临时页面加载真实资源、显式mount，禁止为预览修改index或注入业务假数据到生产。正式验收开始时从本节导出同名前缀`-acceptance.md`，目前不生成勾选文件。

### A-1：任意页面全局可用
- 前置条件：02已完成，本地已登录；刷新后直接停在默认邮箱账号页，尚未进入专家列表。
- 操作步骤：1.查看退出登录左侧。2.点时钟。3.改为2026-01-17 15:00。4.依次切换邮件监控、邮件模板、退订名单、专家列表、收发件箱、来信汇总、AI训练、任务记录，再次点时钟。
- 预期结果：入口始终一个；各页标题按原功能切换；重新打开输入仍为2026-01-17 15:00；洛杉矶为2026-01-16 23:00、前一天；不会跳到空白view。
- 覆盖：I-1/I-3/I-6，S-1，IP-3/IP-4。

### A-2：一行、图标与全导航可达
- 前置条件：浏览器100%缩放，登录用户名可见，当前页面有未读徽标时保留徽标。
- 操作步骤：1.以1920、1440、1100、1024、640、375、320px逐档调整宽度。2.点紧凑时钟。3.在导航区域横向滚到末尾并点任务记录。4.放大窗口。
- 预期结果：顶栏不分第二行；不足时按钮仅一个时钟图标，退出登录保持可见；图标打开后显示完整当前北京时间；窄屏原导航可横向滚动访问全部九项；宽度足够后恢复日期时间文字，无持续闪烁；页面body不横向滚动。
- 覆盖：I-2，S-1/S-4，IP-4。

### A-3：实时钟不覆盖换算
- 前置条件：打开浮层。
- 操作步骤：1.输入2026-09-17 15:00。2.等待约10秒。3.点使用当前时间换算。4.关闭再打开。
- 预期结果：第二步当前时间秒数前进，而输入仍为15:00、纽约03:00、东京16:00；第三步输入变为设备当前北京日期与分钟；第四步保留第三步选择。
- 覆盖：I-3/I-4，S-2/S-3，IP-3。

### A-4：跨日、DST与非整小时
- 前置条件：目录加载成功。
- 操作步骤：1.逐行输入验收标准换算表中的北京值。2.常用中查看纽约/东京/洛杉矶。3.搜索Kathmandu、Kolkata。
- 预期结果：与换算表逐格一致，回拨前后纽约同为01:30但UTC分别-04:00/-05:00；加德满都12:45且慢2小时15分；空日期及1999年输入显示错误，旧换算结果隐藏。
- 覆盖：I-4，S-3，IP-2/IP-3。

### A-5：全球搜索、分组、分页
- 前置条件：打开常用六区结果。
- 操作步骤：1.搜索新西兰。2.清空搜索并选择欧洲。3.点击下一页。4.输入UTC+5:30。5.清空分组再搜索UTC+5:30。6.搜索不存在的qwertyasdf。
- 预期结果：搜索自动切全球；可找到奥克兰；欧洲分组显示Europe/开头的ID并可翻页；筛选变化回第1页；数值偏移+05:30结果不包含+05:45；无结果显示明确文案，不残留上次行。分组与搜索是交集，第四步可能为空，第五步恢复+05:30结果。
- 覆盖：I-5，S-3，IP-2。

### A-6：失败重试与认证清理
- 前置条件：浏览器开发工具能按URL阻断目录GET；目录尚未首次成功加载。
- 操作步骤：1.阻断`/meeting-confirmation/time-zones`并打开。2.解除阻断，点重试。3.通过开发工具延迟目录响应，在响应返回前使认证失效，再发起任一受保护请求。4.重新登录。
- 预期结果：失败显示「时区加载失败，请重试。」且当前钟面仍显示；重试成功出现真实目录；认证失效按原产品显示登录框，时区panel关闭且晚到回包不打开它；登录后新打开可加载目录，未复用旧认证会话状态。
- 覆盖：I-6/I-7，S-3，IP-1/IP-6。

### A-7：样式与键盘
- 前置条件：浅色和深色各测一次，存在底层列表内容。
- 操作步骤：1.打开面板。2.Tab遍历输入与按钮。3.Escape关闭。4.重开后点击页面其他控件。5.窗口缩到375px。
- 预期结果：浮层完全遮住其覆盖区域的底层文字，浅色背景#fff/暗色#182232；宽度≤490px，右边12px；focus外框2px #82a8e8；Escape回到时钟，外部点击不抢原控件焦点；375px输入和搜索纵排；关闭时隐藏控件不可Tab进入。
- 覆盖：I-9，S-1～S-4，IP-6。

### A-8：业务回归与只读
- 前置条件：已有一个可打开会议确认的来信/专家；使用根路径和既有带部署前缀的测试环境各测一次（没有前缀环境时记未验收，不臆造成功）。
- 操作步骤：1.时钟搜索并改日期，Network观察请求。2.原会议确认选择Asia/Shanghai，输入2026-09-17 15:00至16:00、Zoom链接https://zoom.us/j/123456789，生成正文并下载日历但不发送。3.查看任务记录。4.退出后重新登录。
- 预期结果：时钟只有目录GET，请求前缀恰一次，无会议POST、发信或写库请求；原会议正文显示该北京时段，ICS包含DTSTART:20260917T070000Z与DTEND:20260917T080000Z（既有生成器证据MeetingConfirmationService.kt:473-474）；任务记录可读；退出和登录维持原行为，时钟不能挡住登录或改密浮层。
- 覆盖：I-1/I-7/I-9，IP-1/IP-2/IP-5；全部must-NOT-change边界。
