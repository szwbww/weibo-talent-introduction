---
id: K-release-gate-evidence-not-example
domain: qa
created: 2026-07-17
last_used: 2026-07-17
hit_count: 1
source: fix-v:qa-refactor-01-template-boundary:fix-1
severity: P1
---
经验：数据库迁移发布门禁只有目标环境的实际查询输出才能关闭；脚本、示例输出或待填写模板只能证明门禁可执行，不能证明门禁已执行。
正确做法：把代码完成与发布门禁完成拆成独立状态；迁移前后实际计数、环境和执行时间进入发布记录后，才允许关闭部署期 P1。
反例：`docs/plans/2026-07-17/qa-refactor-01-template-boundary-release-gate-record.md:43` 仍标记“发布窗口填写”，`:49` 和 `:60` 仍是须替换的示例输出，却被用于宣称 fix-1 已关闭。
