---
id: K-ai-subtab-whitelist-mapping
domain: frontend
created: 2026-07-08
last_used: 2026-07-08
hit_count: 1
source: create-p:ai-training-dialogue-ui
---
经验：AI 训练视图的子 Tab（`.ai-tab` / `.ai-tab-content`）与侧栏视图注册（K-view-registration-triad）机制不同：点击绑定是通配的（app.js `querySelectorAll("#view-ai-training .ai-tab")`），但面板显隐由 `switchAiTrainingTab()` 内**显式 `||` 白名单映射链**（app.js:2217-2219 `tab === "qa" && panelId === "aiTabQa" || ...`）决定。
新增子 Tab 三点同步：① index.html `.ai-tab` 按钮（data-tab）② index.html `.ai-tab-content` 面板（id=aiTab<Name>）③ `switchAiTrainingTab` 映射链追加一项。漏 ③ 的症状是按钮高亮但面板永不显示。若需进视图即加载数据，还要在 `loadAiTraining()` 的 Promise.all 追加加载函数。
