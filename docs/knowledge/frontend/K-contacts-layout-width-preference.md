---
id: K-contacts-layout-width-preference
domain: frontend
created: 2026-08-14
last_used: 2026-08-14
hit_count: 1
source: create-p:03-expert-layout-wide-default
---

专家联系页左栏宽度由 `localStorage["contacts-list-width"]` 保存。默认值只在键缺失时使用；双击中缝和“默认分栏”按钮也必须使用同一默认值。CSS 的 `.contacts-layout` 基础和 `max-width:1280px` 规则必须与 JS 默认宽度相同，避免 JS 初始化前闪现旧宽度；`max-width:1024px` 的单栏规则不应改动。
