---
id: K-ui-removal-retires-obsolete-contract-tests
domain: frontend
created: 2026-07-17
last_used: 2026-07-17
hit_count: 5
source: fix-v:qa-refactor-02-fact-card-foundation:fix-1
severity: P1
---
经验：删除 UI 功能但保留其源码契约测试，会让全量测试持续失败并阻塞发布。
正确做法：功能移除计划必须检索并同步删除或改写直接断言旧 DOM、endpoint、表格列的测试，同时把该测试文件列入范围。
反例：`qaCoverageKeyEditor.test.js:107-176` 仍要求已删除的 QA coverage 编辑器。
