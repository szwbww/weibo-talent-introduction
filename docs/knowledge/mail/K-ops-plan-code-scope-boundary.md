---
id: K-ops-plan-code-scope-boundary
domain: mail
created: 2026-07-03
last_used: 2026-08-11
hit_count: 1
source: fix-v:google-spam-mitigation:fix-1
severity: P1
---
经验：标明“纯运维配置变更”的投递/退订计划，复验时必须同时检查本地 diff，防止代码修复混入运维计划形成不可追踪发布。
正确做法：若需要改代码（例如配置绑定、端点行为、邮件头生成），先新建立代码计划并单独验证；原运维计划只包含 DNS、环境变量、DB 配置、重启和只读验收。
反例：`docs/plans/2026-07-03/google-spam-mitigation.md:169` 明确“不动代码”，但复验发现 `src/main/kotlin/com/weibo/talentintroduction/config/UnsubscribeProperties.kt:4-6` 有代码 diff。
