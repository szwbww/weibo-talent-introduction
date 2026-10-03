# 01 导航与专家手机布局

状态：待审阅。依赖：[master](mobile-core-master.md)。子系统：全局外壳、专家浏览；5 个变更文件。

## 需求描述

手机可登录、选择全部页面；专家查询后由列表进入单屏详情，返回时保留查询、页码和列表位置。包括 ES 专家及已有联系人两个详情入口。

保持：桌面/平板现有布局；默认首页、权限、刷新和退出行为；专家筛选参数、分页、业务操作、个人桌面分栏宽度。

不做：新的路由/history、专家业务字段修改、批量配置操作重设计、原有全部表单翻新。

## 关键不变量

### I-1：唯一导航来源
- Rule：手机选择框选项从 `.nav-tabs .nav-tab[data-view]` 生成，只允许 `viewMeta` 中存在的值；用户菜单切页统一调用 navigateFromCoreMenu：进入mailbox先执行既有clearMailboxExpertFocus，再调用一次setView。程序跳转仍直调setView并同步选择框，不清除显式focus。旧点击绑定也限定到该选择器，退出按钮只走原bindAuthEvents。
- Applies to：初始化绑定、选择框 change、setView、业务跳转。
- Violation consequence：入口遗漏、重复刷新、未知 view 报错。
- 来源：K-topnav-global-control-is-not-nav-tab（当前原绑定仍涵盖所有.nav-tab，包含无data-view的退出按钮；本段缩窄绑定范围）。

### I-2：单屏状态不是业务状态
- Rule：仅 `#view-contacts` 的 `data-mobile-pane=list|detail` 控制手机呈现。首次为 list；显式选择专家/跨页打开专家才进入 detail；返回不调用 loadContacts、不重置筛选页码；宽屏忽略属性。
- Applies to：handleContactAction 的 select-expert/select-contact、openContactInList、两个详情入口、返回按钮、断点监听。
- Violation consequence：返回丢上下文、旋转重发请求或详情不可达。
- 来源：original。

### I-3：个人宽度只由桌面操作写入
- Rule：≤1024px 不通过 initLayoutResizer 初始化/重置/拖动写 `contacts-list-width`，不将手机宽度裁剪值存成桌面偏好；恢复桌面时按当前容器重用保存值。默认与两个重置入口仍为500px。
- Applies to：initLayoutResizer 的所有 setListWidth 调用、pointer handler、断点恢复。
- Violation consequence：手机打开一次就破坏桌面布局。
- 来源：K-contacts-layout-width-preference。

### I-4：详情请求不能抢回已返回的屏幕
- Rule：两条详情路径使用同一递增请求代次，校验发生在 await 后、任何详情 DOM 写入之前；新选择、返回、离开专家页使旧代次失效。请求失败可见失败提示和返回按钮，不留下上一专家的可操作内容。滚动/焦点回调也须检查代次与当前页面。
- Applies to：showExpertDetail、loadContactDetail、setView、返回、requestAnimationFrame。
- Violation consequence：A 的迟到响应覆盖 B，或用户返回后被跳回详情。
- 来源：K-expert-detail-two-panel-render-sites + original。

### I-5：缓存与资源契约
- Rule：依 master G-5 更新全部版本化资源；不增加资源。
- Applies to：index.html；测试读取资源键。
- Violation consequence：新 JS 搭配旧 CSS。
- 来源：K-frontend-cache-key-triad。

## 样式契约

### S-1：导航与外壳
复用 `.button`（styles.css:802–870）、`.topnav`（140）、`.main`（261）、`.auth-card`（5161）。新增 DOM 紧接现有 nav 之后；旧 nav 节点原位保留，所有既有 ID 不变：

```html
<label class="mobile-core-nav" for="mobileCoreView">切换页面
    <select id="mobileCoreView" aria-label="切换页面"></select>
</label>
```

选项只通过 `createElement('option')` 和 `textContent` 构造，不复制业务导航 DOM。新增 CSS 在本节给定合并块中。

### S-2：专家列表/详情
在现有 `#view-contacts` 增加 `data-mobile-pane="list"`。在 `.contact-detail-panel` 的原 panel-head 之前插入：

```html
<div class="mobile-contact-bar">
    <button type="button" class="button" id="mobileContactsBack">返回专家列表</button>
    <span id="mobileContactStatus" role="status" aria-live="polite" hidden></span>
</div>
```

原有两处详情模板与 `.back-to-list` 在桌面/平板继续复用；只在手机隐藏旧返回按钮。`data-mobile-loading=true` 隐藏旧详情与旧业务操作栏，新的静态返回栏始终保留。失败状态 `data-mobile-loading` 保持 true，status 显示“专家详情加载失败，请返回列表重试”；返回或下一次成功结束 loading。

以下完整 CSS 原样追加；触及现有 class 的全部使用位置已列于 [审计回执](mobile-core-audit.md) 的“相关选择器全部使用位置”。原规则不就地修改，全部派生到手机作用域。

```css
/* mobile-core-01:start */
.mobile-core-nav,
.mobile-contact-bar {
    display: none;
}
@media (max-width: 760px) {
    .app-shell { height: 100dvh; min-width: 0; }
    .topnav.task-center-nav { padding: 8px 12px; gap: 8px; }
    .topnav.task-center-nav > .nav-tabs { display: none; }
    .topnav .brand-subtitle,
    .topnav-side .user-info { display: none; }
    .mobile-core-nav {
        display: flex; align-items: center; gap: 8px;
        order: 4; flex: 1 0 100%; min-width: 0;
        font-size: 12px; color: var(--text-secondary);
    }
    .mobile-core-nav select {
        flex: 1; min-width: 0; min-height: 44px; font-size: 16px;
        padding: 8px 12px; border: 1px solid var(--border);
        border-radius: 7px; background: var(--bg-main); color: var(--text-main);
    }
    .mobile-core-nav select:focus-visible,
    .mobile-contact-bar .button:focus-visible { outline: 2px solid #3b82f6; outline-offset: 2px; }
    .main { padding: 12px; gap: 12px; }
    .main > .topbar { flex-wrap: wrap; gap: 8px; }
    .main > .topbar > div { min-width: 0; }
    .topbar-actions { flex-wrap: wrap; }
    .topnav .logout-btn,
    .topbar-actions .button,
    .topbar-actions .task-center-global { min-height: 44px; height: auto; }
    .auth-card { width: calc(100% - 24px); padding: 24px; }
    .auth-card input { min-height: 44px; font-size: 16px; }
    .auth-card .button { min-height: 44px; height: auto; }
    #view-contacts .contacts-layout { min-width: 0; grid-template-columns: minmax(0, 1fr) !important; }
    #view-contacts[data-mobile-pane="list"] .contact-detail-panel { display: none; }
    #view-contacts[data-mobile-pane="detail"] .contacts-list-panel,
    #view-contacts[data-mobile-pane="detail"] > .contacts-toolbar { display: none !important; }
    #view-contacts .contacts-list-panel,
    #view-contacts .contact-detail-panel { min-width: 0; height: auto; }
    #view-contacts .contacts-layout .list { max-height: none; overflow: visible; }
    #view-contacts .back-to-list { display: none; }
    #view-contacts .mobile-contact-bar { display: flex; flex-wrap: wrap; gap: 8px; padding: 12px; }
    #view-contacts[data-mobile-loading="true"] #contactDetail,
    #view-contacts[data-mobile-loading="true"] .contact-detail-head { display: none; }
    #view-contacts .button { min-height: 44px; height: auto; white-space: normal; }
    #view-contacts .contacts-toolbar,
    #view-contacts .toolbar-actions,
    #view-contacts .list-pager,
    #view-contacts .contact-head-main-row { flex-wrap: wrap; min-width: 0; }
    #view-contacts .toolbar-label { min-width: 0; max-width: 100%; }
    #view-contacts :is(input:not([type="checkbox"]):not([type="radio"]), select, textarea) {
        min-width: 0; max-width: 100%; min-height: 44px; font-size: 16px;
    }
    #view-contacts .detail-sub-tabs { overflow-x: auto; max-width: 100%; }
    #view-contacts .detail-sub-tab { min-height: 44px; flex-shrink: 0; }
    #view-contacts #contactDetail { min-width: 0; overflow-wrap: anywhere; }
    #view-contacts #contactDetail .table-wrap { max-width: 100%; overflow-x: auto; }
}
/* mobile-core-01:end */
```

既有颜色、hover/active/disabled 原样继承；本段新增控件只有原生 select 和已有 `.button`，不另造状态。禁止新增 inline style、未声明 class、全局 overflow 裁剪；原有 inline style 不做无关清理。

## 现状审计

### 浏览器与内存存储
- `contacts-list-width` 唯一持久化键：initLayoutResizer（app.js:16339）读取一次；内部 setListWidth 写入，调用来自默认初始化、两种500px preset、等分、双击及 pointermove。现有手机初始化可将500px裁成视口60%，这是需要保护的交互点。
- 专家选择来自 state.selectedExpertOrcid、state.contacts；详情路径为 showExpertDetail（9357）和 loadContactDetail（9786）。读取来自列表 active 标记、联系详情、材料子组件。筛选/分页仍由 loadContacts 与现有控件管理，不改数据 schema。
- 新增 UI 状态为私有内存记录（pane、请求代次、列表滚动和触发元素），写入只在导航/返回/详情请求生命周期；读取只供呈现、恢复焦点和丢弃过期回调。不写 localStorage/sessionStorage。
- `.main` 是既有主要滚动容器；须记录 `.main.scrollTop`，并兼顾旧 `.list.scrollTop`，不能只用 window.scrollY。
- scrollBackToContactsList（9219、9223）当前重复定义，修改时合并为一个函数；保留761–1024px原有滚动行为。
- setView（3214）是统一导航入口；原 .nav-tab click 绑定在14864，对所有.nav-tab调用setView，只有mailbox清focus的特例，没有data-view合法性过滤；logout也带.nav-tab但不在.nav-tabs内。手机/桌面用户菜单使用同一navigateFromCoreMenu，非导航动作不注册该handler。
- 新增 bindMobileCoreNavigation 在bootstrap中单次调用；isMobileCoreViewport读取 `(max-width: 760px)`；唯一全局onMobileCoreViewportChange处理呈现恢复。03只在此handler补入日历渲染通知，不另建全局监听器。不建立第二个refresh定时器。

### 前端样式盘点
- 完整规则与逐字基线见 audit 中 index:62–182、690–738，styles:1–96、133–174、261–285、802–870、4254–4323。
- token：主色#1e40af；背景#f5f7fb；主文字#1e293b；原按钮字号12px/高32px/圆角7px；面板半透明rgba(255,255,255,.55)；焦点#3b82f6；手机新增尺寸见S-1/S-2。
- 本段不新建浮层，避免半透明 panel 及 backdrop-filter 的定位问题。（来源：K-panel-bg-token-is-translucent）

## 实现方案

1. **T-1 基线**：对比 audit 哈希与当前文件，保存手机/桌面截图；重新查缓存键。只读，无产品改动。遵循 I-1..I-5/S-1..S-2。
2. **T-2 导航**：修改 index.html、app.js、styles.css，插入S-1节点与整个CSS块；单次绑定初始化，setView同步值，桌面点击/手机change共同调用navigateFromCoreMenu，保留mailbox清focus动作。缩窄旧.nav-tab绑定排除logout。保留所有非核心页入口和默认首页。遵循 I-1/I-5/S-1。
3. **T-3 专家切屏**：同三文件，插入S-2节点，添加 begin/finish/fail/return 呈现 helper；两个详情函数在 await 前取代次、返回后校验；所有入口覆盖。返回保存并恢复滚动/焦点，不刷新列表。断点切换仅显隐，不重挂详情。遵循 I-2/I-4/S-2。
4. **T-4 宽度保护**：app.js 的 initLayoutResizer 早期手机分支禁止持久化；监听1024px边界时恢复桌面偏好但不把裁剪值回写。桌面明确拖动/重置才写。修改 contactsLayoutDefault.test.js 验证两种首次宽度与已有偏好。遵循 I-3/S-2。
5. **T-5 验证**：新增 mobileCoreNavigation.test.js，真实执行导航/呈现helper覆盖双入口、异步A/B、返回后迟到、无localStorage新增写。依master统一缓存键，执行验收。遵循全部I/S。

## 变更文件清单

| 文件（仓库相对路径） | 用途 |
|---|---|
| src/main/resources/static/index.html | 手机选择框、专家返回栏、11资源缓存键 |
| src/main/resources/static/styles.css | 追加S-1/S-2合并CSS |
| src/main/resources/static/app.js | 唯一导航接入、专家呈现/竞态、桌面宽度保护 |
| src/test/js/mobileCoreNavigation.test.js | 新增导航与详情状态验证 |
| src/test/js/contactsLayoutDefault.test.js | 扩展宽度存储回归 |

## 验收标准

- I-1：手机选择框恰好覆盖当前10个data-view；排除logout；用户change仅调用一次setView，进入mailbox会清旧focus；程序定向跳转仍保留focus且value同步；退出不调用setView(undefined)。
- I-2：返回前后筛选、页码、选中ID一致，API查询次数不因返回增加；旋转后两个pane在桌面可见。
- I-3：390px首次打开不创建偏好键；已有620px键保持620；桌面首次默认500px与reset回归通过。
- I-4：用受控Promise分别模拟两个详情入口的乱序、返回、切页及失败；旧响应不得写head/detail、跳屏或滚动；新失败显示指定文案。
- I-5：11资源同键，精确反查及现有资源契约测试通过。
- S-1/S-2：CSS块与本文逐字一致，DOM唯一、无新增inline style；390px截屏无整页横滑，原强制display规则不能盖过手机隐藏规则。1440px基线不变。
- 命令：`node --check src/main/resources/static/app.js`；`node --test src/test/js/mobileCoreNavigation.test.js src/test/js/contactsLayoutDefault.test.js src/test/js/loadContactsFilter.test.js src/test/js/expertFilterLayout.test.js src/test/js/contactHeadLayout.test.js`；最后全量JS测试。

## 人工验收清单

### A-1：导航、登录和退出
- 前置条件：测试账号、390px与1440px窗口。
- 操作：登录；选择专家、邮箱、会议、任务；检查其他6入口；使用刷新；退出再登录。
- 预期：手机选择框含10业务入口，当前值与标题一致；退出独立可点；只有当前页显示；桌面保持原导航与默认首页。
- 覆盖：I-1/I-5/S-1、认证与入口不变。

### A-2：两类专家与返回
- 前置条件：通过后台准备一名尚无contactId的ES专家与一名已有联系人的专家；查询结果至少两页。
- 操作：查询→第2页→滚动→打开详情→返回；两类专家各一次；再从邮箱进入专家详情。
- 预期：手机同屏只见列表或详情；返回保持第2页、原查询词和原专家位置（滚动误差≤8px）；两条详情都能查看；跨页打开直接显示目标详情。
- 覆盖：I-2/I-4/S-2、筛选/分页/两条读路径。

### A-3：慢网和失败
- 前置条件：浏览器限速；测试服务可令一次专家详情请求失败。
- 操作：选择A后立即返回并选择B；等待A慢响应；再触发一次失败。
- 预期：B不被A覆盖；返回后不自动跳回；失败显示“专家详情加载失败，请返回列表重试”，返回按钮可操作，旧专家业务按钮不露出。
- 覆盖：I-4/S-2。

### A-4：桌面偏好与视觉
- 前置条件：1440px拖动列表到620px；同一会话与浏览器。
- 操作：切390px刷新→返回1440px；再检查760/761与1024/1025；查看输入、按钮、长邮箱和详情标签。
- 预期：保存偏好仍620px，桌面宽度恢复；手机按钮44px、输入16px、边距12px；姓名与邮箱可读，无整页横向拖动；平板仍使用原有单栏与返回方式。
- 覆盖：I-3/S-1/S-2、桌面/平板不变。
