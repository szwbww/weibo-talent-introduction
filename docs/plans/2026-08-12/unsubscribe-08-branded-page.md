# Plan 08 — 退订页品牌化（确认页 + 成功页两态）

> 主索引：[unsubscribe-link-and-page-master.md](unsubscribe-link-and-page-master.md)（共享证据 E-8 不在本文重复）
> 生成日期：2026-08-12 · create-p
> 子系统：① HTTP 端点渲染（Kotlin） ② 页面样式（内联 CSS）—— 共 2 个，符合上限

## 需求描述

**Observable outcome**

1. 浏览器打开退订链接后看到一张与 `https://www.qingfeitalent.com` 同调性的**确认页**：深色导航底、品牌标识、eyebrow 小标题、说明文案、脱敏邮箱 pill、主按钮「Confirm unsubscribe」+ 次按钮「Keep me subscribed」、页脚公司信息。
2. 点击「Confirm unsubscribe」后看到同风格的**成功页**：对勾图标、「You've been unsubscribed」、说明文案、返回官网链接、页脚。
3. 点击「Keep me subscribed」跳转到官网首页，**不产生任何退订副作用**。

**What must NOT change**

1. `GET /u/unsubscribe` **不执行退订**（防邮件客户端预取误触发）—— 现有语义，`UnsubscribeController.kt:28-33`。
2. `POST /u/unsubscribe`（RFC 8058 一键退订）的返回体 `"unsubscribed"` / `"invalid"` 与状态码逐字不变（`:21-26`）—— 该端点由邮件客户端机器消费，**不得**改成 HTML。
3. 表单 `action` 保持相对路径 `unsubscribe/confirm`（`UnsubscribeControllerTest.kt:70` 现有断言，防 context-path 部署下 404）。
4. token 无效时 GET 返回 400 且响应体逐字为 `invalid link`（`UnsubscribeController.kt:31`，断言在 `UnsubscribeControllerTest.kt:82`）。
5. `/u/**` 不经过登录拦截（`AuthWebConfig.kt:24-26` 只拦 `/api/**`）。
6. `EmailSuppressionService.suppress(email, ONE_CLICK, "web confirm unsubscribe")` 的三个实参逐字不变（`UnsubscribeControllerTest.kt:94-98` 现有断言，其中 `:97` 是 `"web confirm unsubscribe"` 实参）。

**Out of scope**

- token 形态 → Plan 07；正文链接形态 → Plan 06。本计划对 token 的长度与字符集**不做任何假设**。
- 多语言。页面固定英文，与现有邮件正文一致。
- 退订原因收集。
- 「重新订阅」入口（需新端点 + 反滥用设计）。
- 复用 `src/main/resources/static/styles.css`。理由见下方现状审计「前端样式盘点」。
- 前序 Plan 05 的其余两项（`enabled()` 为 false 的启动期告警、人工发信 override 勾选框）。

## 关键不变量

### Invariant I-1: GET 无副作用
- Rule：`GET /u/unsubscribe` 只做 `verify` 与渲染，禁止调用 `suppressionService` 的任何方法。「Keep me subscribed」必须是纯 `<a href>` 跳转，不得是表单提交或带副作用的请求。
- Applies to：`UnsubscribeController.page()`（`:29-33`）；`UnsubscribePageRenderer` 产出的 HTML。
- Violation consequence：Gmail/Outlook 的链接预取会把未点击的专家静默退订，外联池不可逆损失。
- 来源：original（该语义已在现有代码注释 `:28` 显式声明，本计划继承）

### Invariant I-2: token 进 HTML 前必须转义
- Rule：`token` 写入 `<input value="...">` 前必须做 HTML 属性转义（至少 `& < > " '` 五字符）。禁止字符串插值直出。
- Applies to：`UnsubscribePageRenderer` 的确认页渲染。
- Violation consequence：当前 `UnsubscribeController.kt:50` 是 `value="$token"` 直出。虽然只有 `verify` 通过才渲染、攻击者无从伪造，但这是零成本加固；Plan 07 落地后 token 字符集虽受控，渲染层不应依赖上游字符集假设。
- 来源：前序 `unsubscribe-closure-master.md:203`（Plan 05 锚点，本计划承接）

### Invariant I-3: 表单 action 保持相对路径
- Rule：确认表单的 `action` 必须逐字为 `unsubscribe/confirm`（相对），不得改为 `/u/unsubscribe/confirm`。method 保持 `post`。
- Applies to：`UnsubscribePageRenderer` 确认页。
- Violation consequence：部署到带 `server.servlet.context-path` 的 Tomcat 时 POST 404。本项目 packaging 为 war，该风险真实存在。
- 来源：前序 `unsubscribe-closure-master.md:202`；现有断言 `UnsubscribeControllerTest.kt:70`

### Invariant I-4: 页面自包含，零外部依赖（唯一例外是可选 logo）
- Rule：产出的 HTML 不得引用任何外部 CSS 文件、JS 文件、Web 字体或 CDN。所有样式内联在单个 `<style>` 块里。唯一允许的外链是可选的品牌 logo `<img src>`，且**未配置时必须降级为纯文字 wordmark**，不得渲染 `<img src="">`。
- Applies to：`UnsubscribePageRenderer`；`UnsubscribeProperties` 新增的 `brandLogoUrl`。
- Violation consequence：退订页面向公网收件人，外部依赖会带来加载失败、隐私追踪、以及 CSP 问题；`<img src="">` 在部分浏览器会重新请求当前页 URL。
- 来源：original

### Invariant I-5: 页面上的邮箱必须脱敏
- Rule：确认页展示的收件邮箱必须脱敏为 `<local 首字符>•••@<domain>` 形式（如 `l•••@tsinghua.edu.cn`）；local 部分长度 ≤1 时整体显示为 `•••@<domain>`。禁止输出完整邮箱。
- Applies to：`UnsubscribePageRenderer` 确认页；脱敏函数须有独立单测覆盖边界（无 `@`、多个 `@`、空 local、空 domain）。
- Violation consequence：链接一旦被转发或出现在浏览器历史/代理日志里，完整邮箱随页面暴露。脱敏后仍足以让本人确认"是我的邮箱"。
- 来源：original

### Invariant I-6: 两态共用同一 shell
- Rule：确认页与成功页必须由同一个 `renderShell(bodyHtml)` 函数产出外壳（`<!DOCTYPE>`、`<style>`、header、footer），两态只提供各自的主体片段。禁止两处各写一份 HTML 骨架与 CSS。
- Applies to：`UnsubscribePageRenderer`。
- Violation consequence：两份骨架必然漂移，改品牌色要改两处，样式失真从这里开始。
- 来源：original

## 样式契约

> **本节是执行 agent 的唯一样式依据。既有样式引用行号，新增样式逐字给出，执行 agent 只许复制、不许改写。**
> 说明：本页不复用任何既有 class（理由见现状审计「前端样式盘点」），因此本契约全部是「新增 · 逐字」形态。

### S-1: 页面外壳与设计基准（`renderShell`）

- 复用：**无**（本仓库内）。本页与后台管理 UI 分属两套视觉体系，见现状审计。
- **色值与排版来源：官网源码逐字采样。** 官网仓库位于 `/Users/lukai/IdeaProjects/qingfeitalent-local`，样式表 `assets/css/site.css`（2419 行）。`index.html:12` 为 `<body class="visual-parity" data-page="home">`，故线上生效的是 `.visual-parity` 变体的覆盖值。逐条对照见现状审计的「官网设计基准采样表」。
- 新增：以下 `<style>` 块必须**原样复制**进 `renderShell` 产出的 HTML，不得增删属性或改值：

```html
<style>
*{box-sizing:border-box}
:root{--color-bg:#05070f;--color-panel:#0c1322;--color-surface:#101a2e;--color-text:#eaf0ff;--color-muted:#93a3c4;--color-muted-strong:#c3cee2;--color-cyan:#3b82f6;--color-ink:#f7fbff;--border-subtle:rgba(255,255,255,0.08);--border-strong:rgba(255,255,255,0.14);--gradient-brand:linear-gradient(100deg,#60a5fa,#3b82f6 55%,#6366f1);--gradient-panel:linear-gradient(180deg,#101a2e,#0c1322);--shadow-card:0 24px 80px rgba(0,0,0,0.42);--shadow-glow:0 0 34px rgba(59,130,246,0.26);--radius-pill:999px;--font-sans:ui-sans-serif,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;--font-mono:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace}
body{margin:0;min-width:320px;color:var(--color-text);font-family:var(--font-sans);line-height:1.6;-webkit-font-smoothing:antialiased;background-color:#05070f;background-image:radial-gradient(1100px 720px at 82% -8%,rgba(59,130,246,0.20),transparent 58%),radial-gradient(900px 620px at 12% 4%,rgba(99,102,241,0.12),transparent 60%),radial-gradient(1.4px 1.4px at 24% 18%,rgba(255,255,255,0.85),transparent 60%),radial-gradient(1px 1px at 68% 32%,rgba(255,255,255,0.60),transparent 60%),radial-gradient(1.5px 1.5px at 44% 62%,rgba(191,219,254,0.55),transparent 60%),radial-gradient(1px 1px at 86% 74%,rgba(255,255,255,0.55),transparent 60%),radial-gradient(1px 1px at 14% 82%,rgba(255,255,255,0.45),transparent 60%),radial-gradient(1.2px 1.2px at 58% 88%,rgba(255,255,255,0.50),transparent 60%);background-repeat:no-repeat,no-repeat,repeat,repeat,repeat,repeat,repeat,repeat;background-size:auto,auto,320px 320px,260px 260px,300px 300px,240px 240px,280px 280px,220px 220px;background-attachment:fixed}
h1,p{margin:0}
a{color:inherit;text-decoration:none}
.qf-wrap{width:min(560px,calc(100% - 48px));margin-inline:auto}
.qf-head{min-height:60px;display:flex;align-items:center;justify-content:space-between;gap:24px;border-bottom:1px solid var(--border-subtle)}
.qf-logo{width:auto;height:34px;display:block}
.qf-wordmark{font-size:16px;font-weight:700;color:var(--color-text)}
.qf-headnote{font-size:12px;font-weight:600;color:var(--color-muted)}
.qf-main{padding:72px 0;text-align:center}
.qf-eyebrow{color:var(--color-cyan);font:700 11px/1 var(--font-mono);letter-spacing:0.14em;text-transform:uppercase}
.qf-title{margin-top:12px;font-size:clamp(28px,3vw,38px);line-height:1.14;letter-spacing:-0.025em}
.qf-text{margin:9px auto 0;max-width:460px;font-size:14px;color:var(--color-muted-strong)}
.qf-pill{display:inline-flex;align-items:center;gap:8px;margin-top:24px;padding:9px 18px;border:1px solid var(--border-subtle);border-radius:var(--radius-pill);background:var(--gradient-panel);box-shadow:var(--shadow-card);font-family:var(--font-mono);font-size:13px;color:var(--color-muted-strong)}
.qf-actions{display:flex;gap:12px;justify-content:center;flex-wrap:wrap;margin-top:32px}
.qf-btn{display:inline-flex;min-height:38px;align-items:center;justify-content:center;gap:8px;padding:9px 16px;border:1px solid transparent;border-radius:7px;font-family:inherit;font-size:13px;font-weight:700;line-height:1;cursor:pointer;transition:transform 180ms ease,border-color 180ms ease,box-shadow 180ms ease,background-color 180ms ease}
.qf-btn:hover{transform:translateY(-1px)}
.qf-btn:focus-visible{outline:3px solid rgba(59,130,246,0.52);outline-offset:3px}
.qf-btn-primary{background:var(--gradient-brand);color:var(--color-ink);box-shadow:var(--shadow-glow)}
.qf-btn-ghost{border-color:var(--border-strong);background:rgba(255,255,255,0.03);color:var(--color-text)}
.qf-btn-ghost:hover{border-color:rgba(59,130,246,0.45);background:rgba(59,130,246,0.06)}
.qf-check{width:46px;height:46px;margin:0 0 4px;border-radius:50%;border:1px solid var(--border-subtle);background:rgba(59,130,246,0.14);color:var(--color-cyan);display:inline-flex;align-items:center;justify-content:center;font-size:22px;line-height:1}
.qf-link{margin-top:24px;display:inline-block;font-size:14px;color:var(--color-muted)}
.qf-link:hover{color:var(--color-cyan)}
.qf-foot{padding:42px 0 24px;border-top:1px solid var(--border-subtle);text-align:center;color:var(--color-muted);font-size:12px}
@media (max-width:480px){.qf-wrap{width:min(560px,calc(100% - 36px))}.qf-main{padding:52px 0}.qf-actions{flex-direction:column}.qf-btn{width:100%}}
</style>
```

> **为什么把官网的 `:root` 变量整段搬过来**：本页是自包含单文件，不能 `@import` 官网样式表（I-4）。把变量层原样带上，未来官网改品牌色时，比对与同步是"改 8 个变量值"而不是"在 30 条规则里逐个找 hex"。变量名与官网**逐字同名**，便于 diff。

- DOM 结构（`renderShell(bodyHtml)` 的骨架，逐字）：

```html
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>Unsubscribe</title>
<!-- S-1 的 <style> 块插入此处 -->
</head>
<body>
<div class="qf-wrap">
  <header class="qf-head">
    <!-- S-2 品牌块 -->
    <span class="qf-headnote">Email preferences</span>
  </header>
  <main class="qf-main">
    <!-- bodyHtml：S-3 或 S-4 -->
  </main>
  <footer class="qf-foot">
    <!-- S-5 页脚 -->
  </footer>
</div>
</body>
</html>
```

- 禁止项：inline `style="..."` 属性；未在本契约中声明的新 class；引用 `styles.css`；引用任何外部字体或 JS。

### S-2: 品牌块（header 左侧）

- 复用：无。
- 新增：无新 CSS（用 S-1 的 `.qf-logo` / `.qf-wordmark`）。
- DOM 结构 —— 二选一，由 `brandLogoUrl` 是否为空决定（I-4）：

```html
<img class="qf-logo" src="{{brandLogoUrl}}" alt="{{brandName}}">
```

```html
<span class="qf-wordmark">{{brandName}}</span>
```

- 禁止项：`brandLogoUrl` 为空时渲染 `<img src="">`；`alt` 缺失。

### S-3: 确认页主体

- 复用：S-1 的 `.qf-eyebrow` `.qf-title` `.qf-text` `.qf-pill` `.qf-actions` `.qf-btn` `.qf-btn-primary` `.qf-btn-ghost`。
- 新增：无新 CSS。
- DOM 结构（逐字，`{{...}}` 为服务端插值点）：

```html
<p class="qf-eyebrow">Unsubscribe</p>
<h1 class="qf-title">Stop receiving emails from {{brandShortName}}?</h1>
<p class="qf-text">We&#39;ll remove this address from all future outreach. You can still reach us any time by replying to a previous message.</p>
<p class="qf-pill">{{maskedEmail}}</p>
<div class="qf-actions">
  <form method="post" action="unsubscribe/confirm" style="margin:0">
    <input type="hidden" name="token" value="{{escapedToken}}">
    <button type="submit" class="qf-btn qf-btn-primary">Confirm unsubscribe</button>
  </form>
  <a class="qf-btn qf-btn-ghost" href="{{siteUrl}}">Keep me subscribed</a>
</div>
```

> `style="margin:0"` 是本契约**唯一允许**的 inline style —— `<form>` 是 flex 子项，需清零默认外边距；不为它单开 class 以免执行 agent 在别处复用。执行 agent 不得添加任何其他 inline style。

- 禁止项：把「Keep me subscribed」做成 `<form>` 或 `<button>`（违反 I-1）；改 `action` 值（违反 I-3）；`value` 未转义（违反 I-2）；输出完整邮箱（违反 I-5）。

### S-4: 成功页主体

- 复用：S-1 的 `.qf-check` `.qf-title` `.qf-text` `.qf-link`。
- 新增：无新 CSS。
- DOM 结构（逐字）：

```html
<p class="qf-check">&#10003;</p>
<h1 class="qf-title">You&#39;ve been unsubscribed</h1>
<p class="qf-text">This address won&#39;t receive further outreach from us. Changes take effect immediately.</p>
<a class="qf-link" href="{{siteUrl}}">Visit {{siteHost}} &#8594;</a>
```

- 禁止项：使用图片或 SVG 图标（用 `&#10003;` 字符对勾，保证 I-4 零外部依赖）；重复 `<style>` 块（违反 I-6）。

### S-5: 页脚

- 复用：S-1 的 `.qf-foot`。
- 新增：无新 CSS。
- DOM 结构（逐字）：

```html
{{footerLine1}}<br>{{footerLine2}}
```

其中两行由 `UnsubscribeProperties` 的 `footerLine1` / `footerLine2` 提供，均经 HTML 转义后插入；任一为空时该行与其 `<br>` 一并省略。

- 禁止项：把公司地址硬编码进 Kotlin 源码。

## 现状审计

### `UnsubscribeController`（唯一被改的端点类）

- Schema/结构：`@RestController @RequestMapping("/u")`，三个方法。
- Write paths（副作用）：
  1. `oneClick()` `:22-26` — POST `/u/unsubscribe`，`suppress(..., ONE_CLICK, "one-click unsubscribe")`。**本计划不改**。
  2. `confirm()` `:36-41` — POST `/u/unsubscribe/confirm`，`suppress(..., ONE_CLICK, "web confirm unsubscribe")`。本计划只改其**返回体**（`:40` 的 `"<p>You have been unsubscribed.</p>"` → 成功页），三个 suppress 实参逐字不变。
- Read paths（无副作用）：
  1. `page()` `:29-33` — GET `/u/unsubscribe`，`verify` + `confirmHtml(token)`。本计划改渲染，保留 `verify` 与 400 分支。
- Interaction points：
  - `page()` × `confirm()`：确认页表单的 `action` 与 `confirm()` 的映射路径必须匹配。相对 `action="unsubscribe/confirm"` + `@RequestMapping("/u")` + `@PostMapping("/unsubscribe/confirm")` 在无 context-path 与有 context-path 两种部署下都成立（浏览器以当前 URL `<ctx>/u/unsubscribe` 的目录 `<ctx>/u/` 为基准解析）。**这是本计划唯一的跨方法交互点。**
  - `verify()` × Plan 07：本计划对 token 值不做任何长度/字符集假设，只做 HTML 转义（I-2），因此与 Plan 07 无 diff 冲突（Plan 07 的变更文件清单不含本文件）。

### `UnsubscribeProperties` / `application.yml`

- 现状：`UnsubscribeProperties.kt:8-11` 只有 `baseUrl` / `secret`；`application.yml:86-88` 对应两项，默认均为空串。
- Write paths：Spring `@ConstructorBinding` 绑定，无运行时写入。
- Read paths：`UnsubscribeTokenService.kt:15`、`:38`。新增字段只被 `UnsubscribePageRenderer` 读取，不影响这两处。
- Interaction point：新增字段必须全部带默认值，否则空配置环境启动失败。

### 鉴权与静态资源

- `AuthWebConfig.kt:24-26`：`addPathPatterns("/api/**")`。`/u/**` 与 `/static/**` 均不拦截。
- 因此技术上**可以**从退订页引用 `/styles.css`，但本计划明确不这么做，理由见下。

### 前端样式盘点（Step 1b-fe）

> 触发判定：变更文件清单中无 `src/main/resources/static` 下文件，也无独立 `.html`/`.css` 文件 —— 按 create-p 的字面触发条件，Step 1b-fe **不强制**。但本计划产出的是**面向公网收件人的完整页面**，样式失真风险与前端改动同级，故**主动执行**本步并产出上方 `## 样式契约`。

- **可复用 class：无。** `src/main/resources/static/styles.css`（9122 行）是后台管理 UI 的样式表，其 `:root` 设计基准是浅色商务蓝体系（`styles.css:1-40`）：`--primary: #2563eb`、`--bg-main: #f5f7fb`、`--panel-bg: #ffffff`、`--text-main: #1e293b`。退订页要对齐的是 `https://www.qingfeitalent.com` 的**深色**官网体系。两套基准冲突，复用只会导致同名变量语义打架。
- **不复用的第二个理由**：退订页是公网页面，引入 9122 行管理后台 CSS 属无谓暴露与体积浪费。
### 官网设计基准采样表（证据：`/Users/lukai/IdeaProjects/qingfeitalent-local`）

官网源码已连接到本会话。样式表 `assets/css/site.css`（2419 行）；`index.html:12` 是 `<body class="visual-parity" data-page="home">`，故**线上生效值取 `.visual-parity` 变体的覆盖**（`site.css:600-620` 段）。下表每行都标注了采样行号，执行 agent 与验证方均可复核。

| 用途 | 官网实测值 | 采样位置 |
|---|---|---|
| 页面底色 | `#05070f`（`--color-bg`），并叠加 8 层 radial-gradient 星空 + `background-attachment: fixed` | `site.css:5`；`body` 规则 `:33-47` |
| 面板底 | `--gradient-panel: linear-gradient(180deg,#101a2e,#0c1322)` | `site.css:17` |
| 主文字 | `--color-text: #eaf0ff` | `site.css:8` |
| 次文字（弱） | `--color-muted: #93a3c4` | `site.css:9` |
| 次文字（强，正文用） | `--color-muted-strong: #c3cee2` | `site.css:10` |
| 强调色 | `--color-cyan: #3b82f6` | `site.css:11` |
| 按钮内文字 | `--color-ink: #f7fbff` | `site.css:14` |
| 分隔线（弱 / 强） | `rgba(255,255,255,0.08)` / `rgba(255,255,255,0.14)` | `site.css:15-16` |
| 品牌渐变（主按钮底） | `linear-gradient(100deg,#60a5fa,#3b82f6 55%,#6366f1)` | `site.css:17` |
| 阴影 卡片 / 辉光 | `0 24px 80px rgba(0,0,0,0.42)` / `0 0 34px rgba(59,130,246,0.26)` | `site.css:19-20` |
| 字体栈 sans / mono | `ui-sans-serif,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif` / `ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace` | `site.css:28-29` |
| body 行高 | `1.6` | `site.css:33` |
| header 高度 / 底边 | `min-height:60px`；`border-bottom:1px solid var(--border-subtle)` | `site.css:607`（visual-parity 覆盖）、`:68` |
| logo 高度 | `34px`，`filter:none` | `site.css:609`（visual-parity 覆盖了 `:71` 的 `36px` + 反色滤镜） |
| eyebrow | `color:var(--color-cyan)`；`font:700 11px/1 var(--font-mono)`；`letter-spacing:0.14em`；`text-transform:uppercase` | `site.css:62` + visual-parity 覆盖 `:603` |
| 区块标题 | `margin-top:12px`；`font-size:clamp(28px,3vw,38px)`；`line-height:1.14`；`letter-spacing:-0.025em` | `site.css:63` + visual-parity 覆盖 `:604` |
| 区块正文 | `margin-top:9px`；`font-size:14px` | visual-parity `site.css:605` |
| 按钮基础 | `min-height:38px`；`padding:9px 16px`；`border-radius:7px`；`font-size:13px`；`font-weight:700`；`line-height:1`；`border:1px solid transparent`；`transition:transform 180ms ease,border-color 180ms ease,box-shadow 180ms ease,background-color 180ms ease` | `site.css:89` + visual-parity 覆盖 `:614` |
| 按钮 hover | `transform:translateY(-1px)` | visual-parity `site.css:615`（覆盖 `:90` 的 `-2px`） |
| 按钮 focus | `outline:3px solid rgba(59,130,246,0.52)`；`outline-offset:3px` | visual-parity `site.css:616` |
| 主按钮 | `background:var(--gradient-brand)`；`color:var(--color-ink)`；`box-shadow:var(--shadow-glow)` | `site.css:92` |
| 次按钮 / hover | `border-color:var(--border-strong)`；`background:rgba(255,255,255,0.03)`；`color:var(--color-text)` ／ hover `border-color:rgba(59,130,246,0.45)`；`background:rgba(59,130,246,0.06)` | `site.css:93-94` |
| 页脚 | `padding:42px 0 24px`；`border-top:1px solid var(--border-subtle)`；`color:var(--color-muted)`；正文 `font-size:12px` | `site.css:195` + visual-parity `:618`、`:621` |
| 链接 hover | `color:var(--color-cyan)` | `site.css:199` |
| 全局链接 | `color:inherit; text-decoration:none` | `site.css:50` |
| pill 圆角 | `--radius-pill: 999px` | `site.css:24` |
| 容器宽度 | `width:min(1180px,calc(100% - 48px))` | `site.css:26`（`--container`）+ visual-parity `:600` |
| 移动端断点 | `480px`（另有更大断点，本页只用这一档） | `site.css` 媒体查询段 |

**本页对官网的 3 处刻意偏离**（其余全部逐字采样，无第四处）：

| 偏离项 | 官网 | 本页 | 理由 |
|---|---|---|---|
| 容器宽度 | `min(1180px, 100% - 48px)` | `min(560px, 100% - 48px)` | 官网是多栏落地页，本页是单列确认卡片。1180px 会让一行文案横跨全屏、可读性差。**只改宽度，不改任何颜色/字号 token。** |
| 成功页对勾圈 | 官网无对应组件 | `border:1px solid var(--border-subtle)`；`background:rgba(59,130,246,0.14)`；`color:var(--color-cyan)` | 官网无「成功态」视觉。**刻意不引入绿色**——绿色在官网基准里没有任何依据，用品牌蓝是唯一有据的选择。 |
| 文字 wordmark / header 右侧说明 | 官网 header 只有 logo 图 + 导航，无这两个元素 | wordmark `16px/700 var(--color-text)`；说明 `12px/600 var(--color-muted)` | logo 未配置时的降级形态（I-4）与「Email preferences」标注。字号字重派生自官网导航项（`site.css:611`：`font-size:12px;font-weight:600`）与页脚小标题（`:620`：`14px`）。 |

> 除上表 3 项外，S-1 的 `<style>` 块中每一个色值、字号、圆角、阴影、过渡曲线都能在 `site.css` 里找到逐字来源。这一节取代了初稿里"来自效果预览"的说法 —— 初稿色值与官网实测差异显著（如底色 `#0B1B2E` vs 实测 `#05070f`、主按钮纯色 `#1E6FB8` vs 实测三段渐变 + 辉光、eyebrow 是 sans 字体 vs 实测 mono 700），已全部作废。

- **DOM 结构约定**：本页不沿用后台的 `.nav-tab` / `.view` / `.pre` 等约定（那套属管理 UI）。全部新 class 统一 `qf-` 前缀，避免未来若真被引入 `styles.css` 时冲突。
- **改动前基线**（`UnsubscribeController.kt:43-55` 与 `:40`，逐字）：

```kotlin
private fun confirmHtml(token: String): String = """
    <!DOCTYPE html>
    <html lang="en">
    <head><meta charset="UTF-8"><title>Unsubscribe</title></head>
    <body>
    <p>Confirm that you want to unsubscribe from future emails.</p>
    <form method="post" action="unsubscribe/confirm">
      <input type="hidden" name="token" value="$token">
      <button type="submit">Unsubscribe</button>
    </form>
    </body>
    </html>
""".trimIndent()
```

```kotlin
.body("<p>You have been unsubscribed.</p>")
```

对应的现有断言（本轮 grep 实测）：`UnsubscribeControllerTest.kt:70`（`action="unsubscribe/confirm"`）、`:71`（含 token）、`:92`（`You have been unsubscribed`）。**注意 `:92` 的断言字符串会被本计划改掉**（成功页文案改为 `You&#39;ve been unsubscribed`），必须同步更新，见 T-5。

## 实现方案

### 阶段 1 — 配置（I-4、S-2、S-5）

**T-1** `UnsubscribeProperties.kt` 追加 5 个带默认值的字段：

```kotlin
data class UnsubscribeProperties(
    val baseUrl: String = "",
    val secret: String = "",
    val brandName: String = "Qingfei Talent",
    val brandLogoUrl: String = "",
    val siteUrl: String = "https://www.qingfeitalent.com",
    val footerLine1: String = "Jiangsu Qingfei Talent Technology Co., Ltd · Nanjing",
    val footerLine2: String = "QFtechtalent@qftechtalent.com"
)
```

**T-2** `application.yml` 的 `talent-introduction.unsubscribe` 段追加对应 5 项，全部可由环境变量覆盖：

```yaml
  unsubscribe:
    base-url: ${UNSUBSCRIBE_BASE_URL:}
    secret: ${UNSUBSCRIBE_SECRET:}
    brand-name: ${UNSUBSCRIBE_BRAND_NAME:Qingfei Talent}
    brand-logo-url: ${UNSUBSCRIBE_BRAND_LOGO_URL:}
    site-url: ${UNSUBSCRIBE_SITE_URL:https://www.qingfeitalent.com}
    footer-line1: ${UNSUBSCRIBE_FOOTER_LINE1:Jiangsu Qingfei Talent Technology Co., Ltd · Nanjing}
    footer-line2: ${UNSUBSCRIBE_FOOTER_LINE2:QFtechtalent@qftechtalent.com}
```

### 阶段 2 — 渲染器（I-2、I-4、I-5、I-6，S-1 ~ S-5）

**T-3** 新建 `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribePageRenderer.kt`，`@Service`，构造注入 `UnsubscribeProperties`。公开三个方法：

- `fun confirmPage(token: String, email: String): String` — 用 S-3 主体调用 `renderShell`。
- `fun successPage(): String` — 用 S-4 主体调用 `renderShell`。
- `private fun renderShell(bodyHtml: String): String` — S-1 骨架 + S-2 品牌块 + S-5 页脚（I-6：两态唯一外壳来源）。

内部私有工具：

- `private fun escapeHtml(text: String): String` — 替换 `& < > " '` 五字符，顺序上 `&` 必须最先（与 `MailContentService.kt:27-32` 同一写法，但**不复用**该类：`MailContentService` 的 `escapeHtml` 是 private，且该类属邮件内容域，跨域复用会制造无谓耦合）。
- `private fun maskEmail(email: String): String` — I-5。规则：按**最后一个** `@` 切分；domain 为空则整体返回 `"•••"`；local 长度 ≥2 → `local[0] + "•••@" + domain`；local 长度 ≤1 → `"•••@" + domain`。
- `private fun siteHost(): String` — 从 `siteUrl` 取主机名用于 S-4 的链接文案；解析失败时回退为 `siteUrl` 原串。

**T-4** 改 `UnsubscribeController.kt`：

- 构造注入 `UnsubscribePageRenderer`。
- `page()` `:29-33`：`val email = tokenService.verify(token) ?: return ResponseEntity.badRequest().body("invalid link")`，然后 `body(renderer.confirmPage(token, email))`。**保留** 400 分支的逐字响应体（must-NOT-change 4）与 `MediaType.TEXT_HTML`。
- `confirm()` `:36-41`：`.body(renderer.successPage())`。`suppress(...)` 三个实参逐字不动。
- `oneClick()` `:21-26`：**零改动**。
- 删除私有 `confirmHtml()` `:43-55`。

### 阶段 3 — 测试

**T-5** 改 `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnsubscribeControllerTest.kt`：

- 新增 `@MockBean`/构造 `UnsubscribePageRenderer`（`@WebMvcTest` 装配，需能构造 `UnsubscribeProperties`）。
- `POST valid token unsubscribes without auth`、`POST invalid token returns 400 and does not suppress`、`GET invalid token returns 400` 三个用例**逐字保留**（must-NOT-change 2、4）。
- `GET valid token returns confirm html with context-path-safe action`：保留 `action="unsubscribe/confirm"` 与含 token 两条断言（I-3），新增断言页面含 `class="qf-btn qf-btn-primary"` 与 `Confirm unsubscribe`。
- `POST confirm valid token unsubscribes`：断言字符串由 `You have been unsubscribed` 改为 `You&#39;ve been unsubscribed`；`verify(suppressionService).suppress(...)` 三实参断言逐字保留（must-NOT-change 6）。

**T-6** 新建 `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribePageRendererTest.kt`：
- **I-1**：`confirmPage()` 输出中「Keep me subscribed」所在标签是 `<a`，且整页只有 **1 个** `<form`、**1 个** `<button`。
- **I-2**：`confirmPage(token = "a\"><script>x</script>", ...)` 的输出**不含** `<script>`，且含 `&quot;` 与 `&lt;script&gt;`。
- **I-3**：输出含逐字 `action="unsubscribe/confirm"`，不含 `action="/u/`。
- **I-4**：输出不含 `styles.css`、不含 `<script`、不含 `https://cdn`、不含 `fonts.googleapis`；`brandLogoUrl` 为空（默认）时不含 `<img`，含 `class="qf-wordmark"`；配置了 logo 时含 `<img class="qf-logo" src="https://…"` 且含 `alt=`。
- **I-5**：`maskEmail` 经 `confirmPage` 观察 —— 输入 `liu@tsinghua.edu.cn` 输出含 `l•••@tsinghua.edu.cn` 且**不含** `liu@`；边界：`a@b.com` → `•••@b.com`；`noatsign` → `•••`；`a@b@c.com` 按最后一个 `@` 切 → `•••@c.com`。
- **I-6**：`confirmPage()` 与 `successPage()` 的输出各自**恰好含 1 个** `<style`，且两者的 `<style>…</style>` 内容**逐字相等**；两者都含 `class="qf-foot"`。
- **S-1 契约一致性**：断言输出含 `--color-bg:#05070f`、`background:var(--gradient-brand)`、`linear-gradient(100deg,#60a5fa,#3b82f6 55%,#6366f1)`、`font:700 11px/1 var(--font-mono)`、`@media (max-width:480px)` 五个逐字片段；并反向断言输出**不含** `#0B1B2E`、`#1E6FB8`、`#5DCAA5`（已作废的初稿预览色）。
- **S-5**：`footerLine2` 配空串时输出的页脚不含多余 `<br>`。

**T-7**（fast-p 修正 A3，2026-08-12 人工批准）改 `UnsubscribeControllerIllegalTokenTest.kt`：
- 背景：T-4 给 `UnsubscribeController` 构造注入 `@Service UnsubscribePageRenderer` 后，既有的 `@WebMvcTest` 切片类 `UnsubscribeControllerIllegalTokenTest.kt`（`@WebMvcTest(UnsubscribeController::class)` + `@Import(TokenTestConfig)`）没有 renderer bean；`@WebMvcTest` 按类型排除 `@Service` bean，Spring 5.3.31 的 Kotlin 构造无默认参数回退，上下文加载报 `NoSuchBeanDefinitionException: UnsubscribePageRenderer`，其 3 个用例全部 error —— 而本计划「验证命令」要求该类保持通过（base 提交 eaf308b 引入，不在原清单内）。
- 修正：该类内追加 `@MockBean private lateinit var renderer: UnsubscribePageRenderer`（与该文件既有的 `suppressionService` mock 同一范式；非法 token 用例在 `verify` 返回 null 后即返回 400，从不触达 renderer）。仅此一处，其余用例与 `TokenTestConfig` 逐字保留。

## 变更文件清单

| # | 文件 | 类型 | 改动 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/config/UnsubscribeProperties.kt` | 主代码 | 追加 5 个带默认值字段 |
| 2 | `src/main/resources/application.yml` | 配置 | `unsubscribe` 段追加 5 项 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribePageRenderer.kt` | 新建主代码 | 页面渲染 + 逐字 CSS（S-1 ~ S-5） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnsubscribeController.kt` | 主代码 | 注入 renderer；`page()`/`confirm()` 改返回体；删 `confirmHtml()` |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnsubscribeControllerTest.kt` | 测试 | T-5 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribePageRendererTest.kt` | 新建测试 | T-6 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnsubscribeControllerIllegalTokenTest.kt` | 测试 | T-7（fast-p 修正 A3）：追加 `@MockBean UnsubscribePageRenderer` |

合计 7 个文件 ≤ 10 ✅；子系统 2 个 ✅；无新增共享存储字段 ✅。

## 验证命令

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。以下命令可原样复制到终端执行。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsubscribePageRendererTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsubscribeControllerTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsubscribeControllerIllegalTokenTest

# 单个方法（定位失败用；#后为方法名，Kotlin 反引号方法名用引号包住整个 -Dtest 值）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test "-Dtest=UnsubscribeControllerTest#GET valid token returns confirm html with context-path-safe action"

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0，且输出含 `Tests run: N, Failures: 0, Errors: 0`（`mvn test`）／`BUILD SUCCESS`（`mvn clean package`）；`git diff --check` 无输出。
来源：`CLAUDE.md` 的「Commands」章节与项目元信息 `test_command` / `build_command`。

## 验收标准

- **I-1**：T-6 的「只有 1 个 `<form>`、1 个 `<button>`、Keep 是 `<a>`」用例通过；`grep -n "suppressionService" src/main/kotlin/.../UnsubscribeController.kt` 的结果中，`page()` 方法体内 **0 命中**。
- **I-2**：T-6 的注入用例通过；`grep -n 'value="\$token"' src/main/kotlin/` 为 0 行。
- **I-3**：T-6 与 T-5 的 action 断言通过；`grep -n 'action="/u/' src/main/kotlin/` 为 0 行。
- **I-4**：T-6 的零外部依赖用例通过；`grep -n "styles.css\|cdn\.\|fonts.googleapis\|<script" src/main/kotlin/.../UnsubscribePageRenderer.kt` 为 0 行。
- **I-5**：T-6 的四条 `maskEmail` 边界用例通过；渲染输出中不含完整邮箱（用例以 `assertFalse(html.contains("liu@"))` 形式断言）。
- **I-6**：T-6 的「两态 `<style>` 逐字相等且各只 1 个」用例通过；`grep -c "<!DOCTYPE" src/main/kotlin/.../UnsubscribePageRenderer.kt` 结果为 **1**。
- **S-1**：契约中的 `<style>` 块与落地文件中的对应片段逐字一致（可用 `grep -F -f` 逐行比对）。T-6 的逐字片段断言通过。无 inline `style="` 出现，唯一例外是 S-3 声明的 `style="margin:0"`（`grep -c 'style="' UnsubscribePageRenderer.kt` 结果为 **1**）。
- **S-1 官网基准一致性**（新增，防止执行 agent 私改采样值）：在 `UnsubscribePageRenderer.kt` 上执行以下 grep，每条必须 ≥1 命中 ——
  `--color-bg:#05070f` / `--color-text:#eaf0ff` / `--color-muted:#93a3c4` / `--color-muted-strong:#c3cee2` / `--color-cyan:#3b82f6` / `--color-ink:#f7fbff` / `linear-gradient(100deg,#60a5fa,#3b82f6 55%,#6366f1)` / `0 0 34px rgba(59,130,246,0.26)` / `font:700 11px/1 var(--font-mono)` / `clamp(28px,3vw,38px)` / `border-radius:7px` / `outline:3px solid rgba(59,130,246,0.52)` / `background-attachment:fixed`。
  反向断言（必须 **0** 命中）：`#0B1B2E`、`#1E6FB8`、`#4FA3D9`、`#F2F6FA`、`#5DCAA5` —— 这些是**已作废的初稿预览色**，出现即说明改错了来源。
- **S-2**：T-6 的 logo 有/无两个分支用例通过。
- **S-3 / S-4**：T-6 断言两态主体分别含 S-3、S-4 契约里的逐字标签串（`class="qf-eyebrow"`、`class="qf-check"` 等）。
- **S-5**：T-6 的空 footerLine2 用例通过。
- **回归**：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 确认页外观与官网同调性
- 前置条件：应用已部署，`UNSUBSCRIBE_BASE_URL` 已配置；手上有一条有效退订链接。
- 操作步骤：桌面浏览器打开退订链接；另开一个标签打开 `https://www.qingfeitalent.com`。
- 预期结果（逐项对照官网实测值）：
  1. 页面底色取色器测得 `#05070f`，且能看到与官网首页同款的**淡蓝/靛紫辉光 + 细密星点**背景，滚动时背景不跟随（`background-attachment: fixed`）。
  2. 顶部有品牌标识（高 34px）与右侧灰色小字 `Email preferences`，下方一条极淡分隔线。
  3. 中部依次为：**等宽字体、全大写、字距放大的蓝色** `UNSUBSCRIBE`（取色 `#3b82f6`）→ 近白标题 `Stop receiving emails from Qingfei Talent?`（取色 `#eaf0ff`）→ 浅灰蓝正文（`#c3cee2`）→ 深色渐变胶囊里的脱敏邮箱。
  4. 主按钮 `Confirm unsubscribe` 是**从左到右由浅蓝到靛紫的渐变**（不是纯色），周围有一圈淡蓝辉光；次按钮 `Keep me subscribed` 是近透明底 + 细描边。两个按钮圆角都很小（7px），不是胶囊形。
  5. 与官网首页并排截图：底色、强调蓝、按钮渐变与圆角、eyebrow 的字体形态**一致**。
- 覆盖：需求描述 observable 1；样式契约 S-1、S-2、S-3

### A-2: 邮箱脱敏
- 前置条件：已知该链接对应的收件邮箱（如 `liu@tsinghua.edu.cn`）。
- 操作步骤：在确认页上查看胶囊内容；再用浏览器「查看网页源代码」搜索完整邮箱。
- 预期结果：胶囊显示 `l•••@tsinghua.edu.cn`；网页源代码里**搜不到** `liu@tsinghua.edu.cn` 完整串。
- 覆盖：I-5

### A-3: 「Keep me subscribed」无副作用
- 前置条件：该邮箱当前不在抑制名单。
- 操作步骤：在确认页点「Keep me subscribed」→ 跳转后回到后台「退订名单」搜索该邮箱。
- 预期结果：浏览器跳转到 `https://www.qingfeitalent.com`；后台退订名单**没有**该邮箱。
- 覆盖：需求描述 observable 3；I-1

### A-4: 成功页外观与内容
- 前置条件：同 A-1。
- 操作步骤：回到确认页，点「Confirm unsubscribe」。
- 预期结果：页面变为成功页 —— 圆形**淡蓝底对勾**（描边 `rgba(255,255,255,0.08)`，底 `rgba(59,130,246,0.14)`，对勾取色 `#3b82f6`；**不是绿色**，官网基准里没有绿色）、标题 `You've been unsubscribed`、说明文案、链接 `Visit www.qingfeitalent.com →`（默认灰、hover 变蓝）；页面底色、星空背景、header、页脚与确认页**完全一致**（并排截图对比无差异）。
- 覆盖：需求描述 observable 2；I-6；S-4

### A-5: 手机端可用
- 前置条件：同 A-1。
- 操作步骤：用手机浏览器（或桌面 DevTools 切到 375px 宽）打开退订链接。
- 预期结果：内容不横向溢出；两个按钮**上下堆叠**且各自撑满容器宽度；标题字号明显小于桌面端；点按钮有可见按下反馈。
- 覆盖：S-1 的 `@media (max-width:480px)` 规则

### A-6: 无效链接仍返回原有响应（回归）
- 前置条件：无。
- 操作步骤：浏览器打开 `<base-url>/u/unsubscribe?token=garbage`。
- 预期结果：HTTP 400；页面内容逐字为 `invalid link`（纯文本，非品牌页）。
- 覆盖：must-NOT-change 4

### A-7: 一键退订机器通道不受影响（回归）
- 前置条件：能发起命令行请求；准备一条有效 token。
- 操作步骤：执行 `curl -i -X POST '<base-url>/u/unsubscribe' -d 'token=<有效token>'`。
- 预期结果：HTTP 200；响应体逐字为 `unsubscribed`（**不是** HTML 页面）；后台退订名单出现该邮箱，来源 `ONE_CLICK`。
- 覆盖：must-NOT-change 2；现状审计中 `oneClick()` 零改动的声明

### A-8: 带 context-path 部署下表单仍可提交（回归）
- 前置条件：一套配置了 `server.servlet.context-path`（如 `/talent`）的部署环境；有效退订链接。
- 操作步骤：打开 `<host>/talent/u/unsubscribe?token=<有效token>` → 点「Confirm unsubscribe」。
- 预期结果：POST 落到 `<host>/talent/u/unsubscribe/confirm`，返回成功页而非 404。
- 覆盖：I-3；现状审计中 `page()` × `confirm()` 的交互点

### A-9: 未配置 logo 时降级为文字（回归）
- 前置条件：`UNSUBSCRIBE_BRAND_LOGO_URL` 未设置（默认空）。
- 操作步骤：打开确认页 → 查看网页源代码搜索 `<img`。
- 预期结果：header 左侧显示文字 `Qingfei Talent`；源代码中**无** `<img` 标签，更无 `src=""`。
- 覆盖：I-4；S-2
