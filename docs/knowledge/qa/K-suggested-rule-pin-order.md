---
id: K-suggested-rule-pin-order
domain: qa
created: 2026-06-26
last_used: 2026-06-27
hit_count: 3
source: fix-v:qa-rules-phase3:fix-1
severity: P1
---
经验：规则推荐 UI 中“建议”既是标签也是排序契约；只打 badge、不置顶，会让运营在长列表里继续查找系统建议项。
正确做法：渲染候选规则时先按 `suggestedRuleIds` 置顶，再保持原有稳定顺序；默认勾选和“建议”标识必须与置顶排序同源。
反例：`app.js:4714-4729` 只用 `suggestedSet` 设置 checked 和 badge，`category.rules` 原顺序未调整。
