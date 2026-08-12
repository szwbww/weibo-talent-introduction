---
id: K-qingfei-site-design-tokens-source
domain: frontend
created: 2026-08-12
last_used: 2026-08-12
hit_count: 0
source: create-p:unsubscribe-08-branded-page
severity: P2
---

经验：任何要与官网 `https://www.qingfeitalent.com` 对齐视觉的公网页面（退订页、着陆页、公开报告页…），设计基准**有权威源码可查，不要靠截图目测或效果预览推断**。

**权威来源**：官网源码本地仓库 `/Users/lukai/IdeaProjects/qingfeitalent-local`，样式表 `assets/css/site.css`（2026-08-12 时 2419 行）。

**最容易踩的坑：必须取 `.visual-parity` 变体的覆盖值，不是基础规则。**
`index.html:12` 是 `<body class="visual-parity" data-page="home">`，`site.css:600-620` 是该变体的整段覆盖。只读基础规则会取到已被覆盖的值，例如：

| 项 | 基础规则 | visual-parity 覆盖（线上实际） |
|---|---|---|
| 按钮圆角 | `--radius-pill: 999px`（`:89` 用它） | `7px`（`:614`） |
| 按钮高度/内距/字号 | `46px` / `12px 22px` / `15px`（`:89`） | `38px` / `9px 16px` / `13px`（`:614`） |
| 按钮 hover 位移 | `translateY(-2px)`（`:90`） | `translateY(-1px)`（`:615`） |
| logo | `36px` + `filter:brightness(0) invert(1)`（`:71`） | `34px` + `filter:none`（`:609`） |
| eyebrow 字号/字距 | `12px` / `0.16em`（`:62`） | `11px` / `0.14em`（`:603`） |
| 区块标题 | `clamp(32px,4vw,44px)`（`:63`） | `clamp(28px,3vw,38px)`（`:604`） |
| header 高度 | `--header-height: 74px`（`:26`） | `60px`（`:607`） |

**核心 token（`site.css:3-30` 的 `:root`）**：底色 `#05070f`；面板渐变 `linear-gradient(180deg,#101a2e,#0c1322)`；主文字 `#eaf0ff`；弱/强次文字 `#93a3c4` / `#c3cee2`；强调 `#3b82f6`；按钮内文字 `#f7fbff`；弱/强分隔线 `rgba(255,255,255,0.08)` / `rgba(255,255,255,0.14)`；品牌渐变 `linear-gradient(100deg,#60a5fa,#3b82f6 55%,#6366f1)`；辉光 `0 0 34px rgba(59,130,246,0.26)`；sans 字体栈以 `ui-sans-serif` 打头。

**另一个易漏点**：`body`（`:33-47`）的背景不是纯色，是 `#05070f` 叠 8 层 `radial-gradient`（两层大辉光 + 六层星点）加 `background-attachment: fixed`。只抄 `background-color` 会明显不像。

**取样通路限制**：`web_fetch` 对该站只能返回 markdown 化的正文，取不到 CSS（非 HTML 内容类型一律返回空，用 `robots.txt` 可对照验证）；Chrome 扩展未连接时也读不到 computed style。**结论：直接读本地仓库，别在网络取样上浪费轮次。**

关联：[[K-public-page-not-admin-css]]（公网页面不要复用本仓库的后台 `styles.css`，两套基准冲突）。
