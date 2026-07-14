---
id: K-batch-console-diff-tag-normalization
domain: frontend
created: 2026-07-14
last_used: 2026-07-14
hit_count: 4
source: fix-v:batch-send-task-console-frontend:fix-2
severity: P1
---
经验：配置 diff 中的标签是集合而非字符串列表；重复输入同一标签不应被视为参数变化，否则会产生错误红框和伪确认差异。
正确做法：比较前统一 trim、过滤空项、去重并排序，且确认弹窗必须复用同一规范化结果。
反例：`src/main/resources/static/app.js:12576` 只 trim/filter/sort，`AI` 与 `AI, AI` 被误判为差异。
