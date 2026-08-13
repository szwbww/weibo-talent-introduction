# V6 顶栏 × 玻璃拟态 × 藏青墨蓝 — 控制台视觉改版

日期: 2026-08-13
来源预览: /tmp/style-preview/v3.html?g=g6(用户已确认"藏青墨蓝"配色)

## 需求描述

将控制台从"左侧栏 + 白色面板 + #2563eb 商务蓝"改为"顶部胶囊导航 + 半透明玻璃面板 + 藏青墨蓝(#1e40af)":

1. 侧栏消失,品牌区 + 9 个视图导航 + 用户区收进一条 sticky 顶栏;导航项为胶囊(pill)样式,激活项白底蓝字带投影。
2. body 背景换成低饱和灰蓝渐变(`#e6ecf5 → #dde5f0 → #edf1f7`),`background-attachment: fixed`,滚动不跟随。
3. 内容面板(`.panel`)、顶栏为半透明玻璃(白 55% + backdrop blur 16px/20px),圆角 18px,主色调投影。
4. 主色由 `#2563eb` 改为 `#1e40af`(全站经 `--primary` 令牌联动,按钮渐变/聚焦环/激活态自动跟随)。
5. 徽章由方角改为胶囊(`border-radius: 999px`)。
6. 深色模式同步适配:深靛渐变底 + 深色玻璃面板 + 暗色激活胶囊。

What must NOT change:
- app.js 全部视图切换/徽标/登录逻辑(`.nav-tab[data-view]`、`#unmatchedBadgeHigh/Normal`、`#showPollLogBtn`、`#logoutBtn`、`#currentUserDisplay`、`.app-shell`、`.main`、`.view` 的 id/class/挂载关系原样保留)。
- 各业务视图内部结构、组件样式(表格、表单、模态框、抽屉)除经令牌联动的颜色外不变。
- 深色模式仍只经 `prefers-color-scheme` 自动切换,不加手动开关。
- 公网页面(退订页 `/u/**` 等)不得引用 `styles.css`(来源: K-public-page-not-admin-css)。
- 三个静态资源缓存键保持一致并同步 bump。

Out of scope:
- 预览页中的"指标卡统计行"(`.stats`)——真实视图无对应数据源,不做。
- 各视图内部重排/卡片化;模态框玻璃化(保持白底保证可读性);字阶/密度调整;手动主题切换器。
- 后端任何改动。

## 关键不变量

### Invariant I-1: JS DOM 钩子零变更
- Rule: 改版后 index.html 必须继续包含且仅经这些钩子与 app.js 交互:`.nav-tab`(带 `data-view`)、`.nav-tabs`、`#unmatchedBadgeHigh`、`#unmatchedBadgeNormal`、`#showPollLogBtn`(`onclick="showPollLog()"`)、`#logoutBtn`、`#currentUserDisplay`、`.app-shell`、`.main`、9 个 `.view`(id 不变)。app.js 不得出现任何改动;`git diff src/main/resources/static/app.js` 必须为空。
- Applies to: index.html 顶栏结构改造。
- Violation consequence: 视图切换/未匹配邮件徽标/登出/轮询日志全部失效。
- 来源: original

### Invariant I-2: 缓存键三联同步
- Rule: `styles.css?v=`、`trust-reply-workbench.js?v=`、`app.js?v=` 三个查询串必须同时改为 `20260813-v6-topnav-glass-01`;`batchSendTaskConsoleVisualFix.test.js` 中断言串同步改为同值。
- Applies to: index.html、src/test/js/batchSendTaskConsoleVisualFix.test.js。
- Violation consequence: 构建期 node 测试失败(上次发布已踩过:`trustReplyWorkbenchSharedMount.test.js` 断言三键相等)。
- 来源: original

### Invariant I-3: 双视觉体系隔离
- Rule: 本次只改 `src/main/resources/static/` 的后台三件套;禁止任何公网页面(`/u/**` 退订页等)新增对 `styles.css` 的引用(现状已为零引用,保持)。
- Applies to: 全部改动。
- Violation consequence: 公网深色品牌页被后台浅色玻璃体系污染。
- 来源: K-public-page-not-admin-css

### Invariant I-4: 滚动语义不变
- Rule: `.main` 仍是唯一的主内容滚动容器(`overflow-y: auto`),由 `.app-shell` 的 `minmax(0, 1fr)` 行约束高度;body 不产生纵向滚动条;`#view-contacts.active` 的 `flex: 1 1 auto; min-height: 0` 内部分栏滚动行为保持现状。顶栏位于 grid 第一行,不使用 `position: sticky`(shell 恒 100vh,顶栏天然常显)。
- Applies to: .app-shell / .main / .topnav 的 CSS。
- Violation consequence: contacts/mailbox 等满高分栏视图出现双滚动条或高度塌陷。
- 来源: original

### Invariant I-5: 窄屏导航可滚动
- Rule: 视口 < 1100px 时 `.nav-tabs` 横向滚动(`overflow-x: auto`),导航项 `white-space: nowrap`,不换行不截断;9 个导航项 + 品牌 + 用户区在 1280px 必须完整显示不溢出。
- Applies to: 顶栏媒体查询。
- Violation consequence: 小屏用户无法到达靠后的视图。
- 来源: original

## 样式契约

原则:既有样式引用行号;新增/重写样式逐字给出;执行 agent 只许复制。行号基于当前 HEAD(56dabfd)的 `src/main/resources/static/styles.css`。

### S-1: 设计令牌(:root 就地修改,styles.css:1-90)
就地修改以下 6 个既有令牌的值,其余令牌不动:

```css
    --primary: #1e40af;
    --primary-hover: #1e3a8a;
    --primary-active: #172554;
    --primary-rgb: 30, 64, 175;
    --panel-bg: rgba(255, 255, 255, 0.55);
    --radius-lg: 18px;
```

在 `--z-toast: 9999;` 之后新增 3 个玻璃令牌(逐字):

```css
    --glass-border: rgba(255, 255, 255, 0.5);
    --glass-shadow: 0 8px 32px rgba(var(--primary-rgb), 0.1);
    --glass-blur: blur(16px);
```

### S-2: body 渐变底(styles.css:82-91 的 body 块就地修改)
`background-color: var(--bg-main);` 一行替换为(逐字):

```css
    background: linear-gradient(135deg, #e6ecf5 0%, #dde5f0 50%, #edf1f7 100%);
    background-attachment: fixed;
```

同时**删除**以下两处环境光残留(它们与新底色重复):
- styles.css:3327-3343 的 `.main::before { ... }` 整个规则块。
- styles.css:4056-4058 媒体查询内的 `.main::before { left: 0; }`。

### S-3: 应用壳与顶栏(styles.css:123-131 就地重写)
`.app-shell` 块(styles.css:124-128)整体替换为(逐字):

```css
.app-shell {
    display: grid;
    grid-template-rows: auto minmax(0, 1fr);
    height: 100vh;
}
```

`.sidebar` 块(styles.css:131-141)及 styles.css:3376-3378 的 `.sidebar` 渐变块**整体删除**,替换为(逐字):

```css
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

### S-4: 胶囊导航(styles.css:188-247 就地重写)
`.nav-tabs`、`.nav-tab` 系列块(styles.css:188-247)整体替换为(逐字)。`svg` 图标在顶栏隐藏;`nav-badge` 保留显示:

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

同时**删除**:
- styles.css:238-247 的 `.nav-tab.active::before`(左侧指示条,胶囊形态下无意义)。
- styles.css:3453-3473 的 `.nav-tab::after` / `.nav-tab:hover::after` / `.nav-tab.active::after` 三个块。

`.nav-badge`(styles.css:1926-1954)规则不动。`.nav-tab.active .nav-badge.*`(styles.css:1956-1964)规则不动。

### S-5: 主内容区(styles.css:250-260 就地修改)
`.main` 块替换为(逐字):

```css
.main {
    padding: 20px 28px 28px;
    display: flex;
    flex-direction: column;
    gap: 12px;
    overflow-y: auto;
    width: 100%;
    max-width: 1400px;
    margin: 0 auto;
    min-width: 0;
    position: relative;
    z-index: 0;
}
```

(删除原 `height: 100vh; max-height: 100vh;`——高度由 S-3 的 grid 行约束,满足 I-4。)

### S-6: 玻璃面板(styles.css 中 `.panel` 基础块就地修改)
先 grep 定位 `.panel {` 基础块(约 styles.css:780 附近,含 `background: var(--panel-bg); border: 1px solid var(--panel-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-md);`)。该块替换为(逐字):

```css
.panel {
    background: var(--panel-bg);
    backdrop-filter: var(--glass-blur);
    -webkit-backdrop-filter: var(--glass-blur);
    border: 1px solid var(--glass-border);
    border-radius: var(--radius-lg);
    box-shadow: var(--glass-shadow);
}
```

`.panel` 使用点极多(各视图内容面板),属**就地修改**——全部使用点自动获得玻璃效果,这正是本计划的目标,无需派生 class。

### S-7: 徽章胶囊(styles.css `.badge` 块就地修改,约 :889)
`.badge` 块中 `border-radius: var(--radius-sm);` 一行替换为:

```css
    border-radius: 999px;
```

### S-8: 顶栏 HTML 结构(index.html:58-167 就地重写)
现有 `<aside class="sidebar">…</aside>`(index.html:58-167)整体替换为以下骨架(逐字;`…` 处为**原样保留**的现有 9 个 `<button class="nav-tab" data-view=…>` 按钮,含 svg 与 mailbox 的两个 `#unmatchedBadge*` 徽标,顺序不变):

```html
    <header class="topnav">
        <div class="brand">
            <div class="brand-mark">
                …原 brand-mark svg 保留…
            </div>
            <div>
                <div class="brand-title">Talent Console</div>
                <div class="brand-subtitle">专家引进自动化</div>
            </div>
        </div>
        <nav class="nav-tabs" aria-label="Main">
            …原 9 个 nav-tab 按钮逐字保留…
        </nav>
        <div class="topnav-side">
            <div class="user-info">
                当前登录: <span id="currentUserDisplay">admin</span>
            </div>
            <button class="nav-tab logout-btn" id="showPollLogBtn" onclick="showPollLog()">
                <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <polyline points="14 2 14 8 20 8"/>
                    <line x1="16" y1="13" x2="8" y2="13"/>
                    <line x1="16" y1="17" x2="8" y2="17"/>
                    <polyline points="10 9 9 9 8 9"/>
                </svg>
                <span>轮询日志</span>
            </button>
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
```

禁止项:
- `showPollLogBtn` 上的 inline `style="border-top: none; padding-top: 8px; padding-bottom: 8px;"` 必须删除(顶栏中无边框,该内联样式失效且违反契约)。
- 不得新增任何其他 inline style。
- `.sidebar-footer` 相关 CSS(styles.css:4982-4989 `.sidebar-footer` 块、:4990-4994 `.user-info` 块中 `padding-left: 12px;` 一行)——`.sidebar-footer` 块整体删除;`.user-info` 块保留但删除 `padding-left: 12px;`(S-3 已在 `.topnav-side` 下声明新间距)。
- `.logout-btn` 块(styles.css:4995-4998)中 `width: 100%;` 改为 `width: auto;`。

### S-9: 媒体查询(styles.css:4020-4058 就地重写)
现有 `@media (max-width: 1024px)` 块中针对侧栏布局的规则(`.app-shell { grid-template-columns: 1fr; }`、`.sidebar` 相关、`.nav-tabs/.nav-tab` 调整、`.main::before`)整体替换为(逐字):

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

@media (max-width: 640px) {
    .brand-subtitle,
    .topnav-side .user-info {
        display: none;
    }

    .main {
        padding: 16px;
    }
}
```

原块中对 `.main { padding: 16px; height: auto; max-height: none; }` 的声明一并被覆盖移除(高度语义由 I-4 接管)。

### S-10: 深色模式适配(styles.css 末尾深色媒体查询块内追加)
在 `@media (prefers-color-scheme: dark) { :root { … } }` 令牌覆盖之后、该 @media 块的组件规则区追加(逐字):

```css
    body {
        background: linear-gradient(135deg, #0a0f1a 0%, #0f172a 50%, #111827 100%);
        background-attachment: fixed;
    }

    .topnav {
        background: rgba(13, 20, 32, 0.6);
        border-bottom-color: rgba(148, 163, 184, 0.14);
    }

    .nav-tab:hover {
        background-color: rgba(148, 163, 184, 0.12);
    }

    .nav-tab.active {
        background-color: rgba(148, 163, 184, 0.16);
        color: var(--primary-bright);
        box-shadow: none;
    }

    .panel {
        border-color: rgba(148, 163, 184, 0.14);
    }
```

注意:深色 `:root` 覆盖块中既有 `--panel-bg: #151f30;` 一行改为 `--panel-bg: rgba(21, 31, 48, 0.55);`(玻璃透明度与浅色对称)。

### S-11: 缓存键(index.html:11、1934、1935 + 测试)
三处资源引用改为:

```html
    <link rel="stylesheet" href="styles.css?v=20260813-v6-topnav-glass-01">
```

```html
<script src="trust-reply-workbench.js?v=20260813-v6-topnav-glass-01"></script>
<script src="app.js?v=20260813-v6-topnav-glass-01"></script>
```

`src/test/js/batchSendTaskConsoleVisualFix.test.js` 中 "bumps the stylesheet cache key" 用例的三个断言串改为同值 `20260813-v6-topnav-glass-01`。

## 现状审计

### Store: 无数据存储改动
纯前端改版,无 DB/ES/缓存写入。

### JS 对 shell 的依赖(grep 实证)
- app.js:1630 — `$$(".nav-tab").forEach(tab => tab.classList.toggle("active", tab.dataset.view === view))`(激活态切换)。
- app.js:10621 — `$$(".nav-tab").forEach(tab => tab.addEventListener("click", () => setView(tab.dataset.view)))`(点击绑定)。
- app.js 对 `.sidebar` / `.sidebar-footer` **零引用**(grep 无结果)。
- `#unmatchedBadgeHigh/Normal`(index.html:115-116)由徽标轮询逻辑按 id 更新,挂在 mailbox 导航按钮内。
- `#logoutBtn`、`#showPollLogBtn`(inline onclick)、`#currentUserDisplay` 为登录态依赖。

### 测试对 shell 的依赖
- authFlow.test.js:133/157/268/279 — 仅 stub `querySelector(".app-shell")`,class 保留即可。
- trustReplyWorkbenchSharedMount.test.js:288-297 — 断言 `trust-reply-workbench.js?v=`、`app.js?v=`、`styles.css?v=` 三键**相等**(不比值)。
- batchSendTaskConsoleVisualFix.test.js:36-39 — 断言三键**等于具体字符串**,bump 时必须同步。

### 前端样式盘点
- 可复用 class:`.brand`/`.brand-mark`/`.brand-title`/`.brand-subtitle`(styles.css:143-185,顶栏继续用);`.nav-badge`(styles.css:1926-1954);`.user-info`(styles.css:4990-4994);`.logout-btn`(styles.css:4995-4998);`.view`(styles.css:326-335,display none/active flex)。
- 设计基准 token 实值(当前 → 目标):`--primary #2563eb → #1e40af`;`--primary-hover #1d4ed8 → #1e3a8a`;`--primary-active #1e40af → #172554`;`--primary-rgb 37,99,235 → 30,64,175`;`--panel-bg #ffffff → rgba(255,255,255,0.55)`;`--radius-lg 14px → 18px`。字号/间距刻度不动。
- 渐变底实值:浅色 `linear-gradient(135deg,#e6ecf5 0%,#dde5f0 50%,#edf1f7 100%)`;深色 `linear-gradient(135deg,#0a0f1a 0%,#0f172a 50%,#111827 100%)`。
- DOM 结构约定:9 个 `.view` 由 `setView()` 切换 `.active`;导航按钮 = `<button class="nav-tab" data-view="…">svg+span`;mailbox 按钮多两个 `#unmatchedBadge*`。
- 改动前基线:`.app-shell` = `grid-template-columns: 240px minmax(0,1fr)`(styles.css:124-128);`.sidebar` 白底 sticky 100vh(:131-141 + :3376 渐变);`.nav-tab` 竖排圆角 7px 带左侧激活指示条(:194-247, :3453-3473);`.main` 100vh 自滚动(:250-260);`.main::before` 环境光渐变(:3327-3343)。

### Interaction points
1. index.html 顶栏 DOM ↔ app.js 视图切换绑定(I-1)。
2. index.html 缓存键 ↔ 两个测试文件的断言(I-2)。
3. `:root` 令牌值 ↔ 全站所有经 `var(--primary*)` 联动的组件(按钮渐变、聚焦环、激活态、链接)——值变更即全局换色,这是预期行为,但验证时必须抽查非顶栏区域(如 `.button.primary`、表单 focus)确认无撞色。

## 实现方案

### Task 1: 令牌与基底(I-3, S-1, S-2, S-10 前半)
文件: src/main/resources/static/styles.css
1. 按 S-1 改 6 个令牌值 + 新增 3 个玻璃令牌。
2. 按 S-2 改 body 渐变、删 `.main::before` 两处。
3. 深色 `:root` 块中 `--panel-bg` 改 `rgba(21, 31, 48, 0.55)`。

### Task 2: 壳结构 CSS(I-4, I-5, S-3, S-4, S-5, S-9)
文件: src/main/resources/static/styles.css
1. `.app-shell` 按 S-3 重写;删 `.sidebar` 两块;新增 `.topnav`/`.topnav-side` 块。
2. `.nav-tabs`/`.nav-tab` 系列按 S-4 重写;删 `::before`/`::after` 指示条块。
3. `.main` 按 S-5 重写。
4. `.sidebar-footer` 删除;`.user-info` 删 padding-left;`.logout-btn` width 改 auto(S-8 禁止项)。
5. 媒体查询按 S-9 重写。

### Task 3: 面板与徽章皮肤(S-6, S-7)
文件: src/main/resources/static/styles.css
1. `.panel` 基础块就地替换为玻璃规则(S-6)。
2. `.badge` 圆角改 999px(S-7)。

### Task 4: 深色适配(S-10)
文件: src/main/resources/static/styles.css
在深色 @media 块追加 S-10 五条规则。

### Task 5: HTML 结构(I-1, S-8)
文件: src/main/resources/static/index.html
`<aside class="sidebar">` 整体替换为 S-8 的 `<header class="topnav">` 骨架;9 个 nav-tab 按钮逐字搬移;两个 footer 按钮移入 `.topnav-side` 并删 inline style。

### Task 6: 缓存键(I-2, S-11)
文件: src/main/resources/static/index.html、src/test/js/batchSendTaskConsoleVisualFix.test.js
三处引用 + 测试断言串同步 bump。

### Task 7: 验证
1. `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js src/test/js/authFlow.test.js` 全绿。
2. 本地静态服务 + 无头浏览器截图:浅色顶栏/深色顶栏/1280px 完整导航/900px 横向滚动,确认无控制台错误。

## 变更文件清单

| 文件 | 改动 |
|---|---|
| src/main/resources/static/styles.css | 令牌/壳/导航/面板/徽章/媒体查询/深色 全部 CSS |
| src/main/resources/static/index.html | 顶栏 DOM 重写 + 缓存键 |
| src/test/js/batchSendTaskConsoleVisualFix.test.js | 缓存键断言串 |

共 3 个文件,1 个子系统(前端)。app.js 不允许出现在 diff 中(I-1)。

## 验收标准

- I-1: `git diff --name-only` 不含 app.js;`grep -c 'class="nav-tab"' index.html` ≥ 11(9 视图 + 2 侧区按钮);`grep 'id="logoutBtn"\|id="showPollLogBtn"\|id="currentUserDisplay"\|id="unmatchedBadgeHigh"\|id="unmatchedBadgeNormal"' index.html` 五项齐全。
- I-2: `grep -o 'v=[0-9a-z-]*' index.html` 去重后仅 `v=20260813-v6-topnav-glass-01`;两个测试文件相关用例通过。
- I-3: `grep -rn 'styles.css' src/main --include='*.html' --include='*.kt' | grep -v static/index.html` 无结果。
- I-4: styles.css 中 `.main` 块不含 `height: 100vh`;`.app-shell` 含 `grid-template-rows: auto minmax(0, 1fr)`;`#view-contacts.active` 规则未被触碰。
- I-5: styles.css 含 `@media (max-width: 1100px)` 且其内 `.nav-tabs` 含 `overflow-x: auto`。
- S-1: `:root` 含 `--primary: #1e40af;` 与三个 `--glass-*` 令牌。
- S-2: `.main::before` 在 styles.css 中零匹配;body 块含 `background-attachment: fixed`。
- S-3/S-4: `.sidebar`、`.sidebar-footer`、`.nav-tab.active::before`、`.nav-tab::after` 在 styles.css 与 index.html 中均零匹配;存在 `.topnav` 块且与契约逐字一致。
- S-6: `.panel` 块含 `backdrop-filter: var(--glass-blur);`。
- S-7: `.badge` 块含 `border-radius: 999px;`。
- S-10: 深色 @media 块含五条追加规则;`--panel-bg: rgba(21, 31, 48, 0.55);` 存在。
- S-8: index.html 含 `<header class="topnav">` 与 `<div class="topnav-side">`;`showPollLogBtn` 行无 inline style。
- 集成: 浏览器加载无 console error;点击 9 个导航项视图切换正常(`.nav-tab.active` 白底胶囊跟随);mailbox 徽标元素仍在 DOM。

## 人工验收清单

### A-1: 浅色模式整体观感
- 前置条件: 系统外观为浅色;已登录控制台。
- 操作步骤: 1) 硬刷新(Cmd+Shift+R); 2) 观察背景、面板、导航。
- 预期结果: 背景为灰蓝渐变(顶部略亮),滚动时背景不跟随;内容面板呈半透明磨砂(可隐约透出背景渐变),圆角明显大于改前;主按钮为深藏青(`#1e40af`)渐变。
- 覆盖: 需求 2/3/4,S-1/S-2/S-6。

### A-2: 顶栏导航功能
- 前置条件: 已登录,视口宽度 ≥1280px。
- 操作步骤: 1) 依次点击顶栏 9 个导航项(邮件监控→任务记录); 2) 观察激活样式; 3) 若有未匹配邮件,看"收发件箱"上的数字徽标。
- 预期结果: 每次点击内容区切换到对应视图;激活项为白底胶囊、藏青字、浅投影;徽标数字正常显示;无导航图标(纯文字胶囊)。
- 覆盖: I-1、需求 1,S-4/S-8。

### A-3: 窄屏导航
- 前置条件: 已登录。
- 操作步骤: 1) 把浏览器窗口拖窄到约 900px; 2) 在导航条上左右滑动。
- 预期结果: 导航换到第二行并可横向滚动,9 项全部可达,文字不截断不换行。
- 覆盖: I-5,S-9。

### A-4: 深色模式
- 前置条件: 系统外观切换为深色;已登录。
- 操作步骤: 1) 刷新页面; 2) 观察背景/面板/激活导航; 3) 点开任意含表格的视图。
- 预期结果: 背景为深靛渐变;面板深色半透明;激活导航为深灰胶囊亮蓝字;表格文字对比清晰无白块刺眼。
- 覆盖: 需求 6,S-10。

### A-5: 回归 — contacts 分栏高度
- 前置条件: 专家联系视图有数据。
- 操作步骤: 1) 进入"专家联系"; 2) 拖动列表/详情分栏; 3) 分别滚动左右两栏。
- 预期结果: 两栏各自内部滚动,页面整体不出现第二根纵向滚动条;分栏拖拽正常。
- 覆盖: I-4(must-NOT-change)。

### A-6: 回归 — 登录/登出/轮询日志
- 前置条件: 无。
- 操作步骤: 1) 点顶栏右侧"退出登录"; 2) 重新登录; 3) 点"轮询日志"。
- 预期结果: 退出回到登录玻璃卡;登录后进主界面;轮询日志弹窗正常打开。
- 覆盖: I-1(must-NOT-change)。

### A-7: 回归 — 表格与表单联动色
- 前置条件: 已登录。
- 操作步骤: 1) 进入"邮箱账号",hover 表格行; 2) 点开任意编辑模态框,focus 一个输入框。
- 预期结果: 行 hover 为藏青色调浅蓝;focus 描边为藏青色系;模态框保持不透明白底、可读性正常。
- 覆盖: interaction point 3(must-NOT-change)。
