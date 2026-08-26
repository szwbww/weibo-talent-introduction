---
id: K-global-p-is-muted-in-dialogs
domain: frontend
created: 2026-08-21
last_used: 2026-08-21
hit_count: 0
source: create-p:ui-tweaks-02-overlay-and-dialog-contrast
severity: P2
---

经验：本仓库有一条**全局** `p` 规则（`styles.css:302-306`）：

```css
p {
    font-size: 12px;
    color: var(--text-muted);   /* #94a3b8 浅 / #7d8ca3 暗 */
    margin-top: 2px;
}
```

它把裸 `<p>` 一律降级为「说明性小灰字」。凡是用 JS 拼 `<p>正文</p>` 塞进弹窗、抽屉、
卡片的地方，正文就会渲染成浅灰小字 —— 看起来像被禁用，运营反馈「看不清 / 样式有问题」。

实例：`#actionDialog` 的确认框正文由 `openActionDialog()` 的 `field.type === "html"` 分支写入
（`app.js:12060` 附近），调用方（如 `app.js:10391` 的内容安全门禁二次确认）传的是
`<p>本次发送命中 N 项…</p>…<p>确认已人工核对，仍要发送吗？</p>`。叠加当时
`.action-dialog { background: var(--panel-bg) }` 的 55% 透明（[[K-panel-bg-token-is-translucent]]），
两段关键说明几乎不可读。

正确做法：**不要动全局 `p`**（全站数百处依赖它做说明文字）。在浮层容器内做**作用域覆盖**：

```css
.action-dialog-body p {
    color: var(--text-main);
    font-size: 13px;
    line-height: 1.6;
}
```

排查手法：弹窗里文字发灰时，先查是不是裸 `<p>` 命中了全局规则，再查背景 alpha
（`getComputedStyle(el).backgroundColor`）—— 这两个成因经常同时出现，只修一个仍然难读。

关联：[[K-panel-bg-token-is-translucent]]、[[K-shared-action-dialog-cleanup]]
