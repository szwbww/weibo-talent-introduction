---
id: K-batch-console-regression-contract
domain: frontend
created: 2026-07-14
last_used: 2026-08-14
hit_count: 7
source: fix-v:batch-send-task-console-frontend:fix-1
severity: P1
---
经验：移除旧 DOM 或入口而不更新直接断言它们的前端测试，会使 CI 在功能实现正确时仍必然失败。
正确做法：删除/替换 UI 契约时，同一计划必须列出并同步相关 JS 测试；新断言应验证替代入口的可观察行为。
反例：`src/test/js/expertTagBatchFix.test.js:198` 仍要求已删除的 `#batchSendType`。
