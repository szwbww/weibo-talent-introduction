---
id: K-public-page-not-admin-css
domain: frontend
created: 2026-08-12
last_used: 2026-08-13
hit_count: 1
source: create-p:unsubscribe-08-branded-page
severity: P2
---

经验：本仓库有**两套互不兼容的视觉体系**，面向公网收件人的页面不要复用后台管理 UI 的样式表。

- 后台管理 UI：`src/main/resources/static/styles.css`（9122 行），`:root` 设计基准是**浅色商务蓝**（`styles.css:1-40`）：`--primary: #1e40af`(2026-08-13 V6 改版后)、`--bg-main: #f5f7fb`、`--panel-bg: #ffffff`、`--text-main: #1e293b`。
- 公网页面（退订页等）要对齐的是官网 `https://www.qingfeitalent.com` 的**深色**体系（`#0B1B2E` 底 / `#F2F6FA` 主文字 / `#1E6FB8` 主按钮）。

两套基准同名变量语义冲突，复用只会打架；且把 9122 行后台 CSS 投给公网收件人属无谓暴露与体积浪费。

**技术上是可复用的**（`AuthWebConfig.kt:25-27` 的拦截器只 `addPathPatterns("/api/**")`，`/u/**` 与 `/static/**` 都不鉴权），所以这是一条**刻意的设计约束**，不是技术限制 —— 执行 agent 很容易"顺手引一下 styles.css"，计划里必须显式禁止。

**正确做法**：公网页面用自包含内联 `<style>`，class 统一加独立前缀（退订页用 `qf-`），零外部 CSS/JS/字体/CDN 依赖。可选的品牌 logo 是唯一允许的外链，且未配置时必须降级为纯文字 wordmark —— 渲染 `<img src="">` 会让部分浏览器重新请求当前页 URL。

**create-p Step 1b-fe 的触发判定要人工兜一下**：这类页面的 HTML 内联在 `.kt` 源码里，变更清单里没有 `.html`/`.css` 文件，按字面条件 Step 1b-fe **不触发**。但样式失真风险与前端改动同级，应主动执行并产出 `## 样式契约`。

关联：[[K-view-registration-triad]]（后台侧栏 view 的注册套路，只适用于管理 UI）、[[K-mail-body-display-sites]]。
