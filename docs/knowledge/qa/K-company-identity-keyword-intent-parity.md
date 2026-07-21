---
id: K-company-identity-keyword-intent-parity
domain: qa
created: 2026-07-16
last_used: 2026-07-21
hit_count: 3
source: fix-v:ai-reply-p0-p2-master-plan:fix-1
severity: P1
---
经验：intent catalog 能识别公司法定名称，不代表 QA matcher 一定能提供该事实；迁移 keywords 与 intent aliases 漂移时，已审核事实会被误判缺失。
正确做法：公司身份规则的独立完整问法必须与 catalog 做 parity 回归；新补词用后续迁移追加，不能修改已应用迁移或覆盖运营 keywords。
反例：catalog 支持 `legal name`，V75 只支持 `full name and registered`，单问法定名称时 candidateRuleIds 为空。
