---
id: K-ai-subtab-whitelist-mapping
domain: frontend
created: 2026-07-08
last_used: 2026-07-29
hit_count: 3
source: create-p:ai-training-dialogue-ui
---
经验：AI 训练视图的子 Tab（`.ai-tab` / `.ai-tab-content`）与侧栏视图注册（K-view-registration-triad）机制不同：点击绑定是通配的（app.js `querySelectorAll("#view-ai-training .ai-tab")`），但面板显隐由 `switchAiTrainingTab()` 内**显式 `||` 白名单映射链**决定。
新增子 Tab 三点同步：① index.html `.ai-tab` 按钮（data-tab）② index.html `.ai-tab-content` 面板（id=aiTab<Name>）③ `switchAiTrainingTab` 映射链追加一项。漏 ③ 的症状是按钮高亮但面板永不显示。

加载策略要按依赖强度区分：主视图必需数据才加入 `loadAiTraining()` 的 Promise.all；可选、慢或独立故障域的数据源（例如外部 ES 只读列表）应在首次切入 Tab 时 lazy load，并在 panel 内独立显示失败。否则一个可选源不可用会让整个 AI 训练视图初始化失败。
