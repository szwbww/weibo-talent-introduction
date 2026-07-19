---
id: K-ai-evidence-ui-no-rule-id-fallback
domain: frontend
created: 2026-07-19
last_used: 2026-07-20
hit_count: 2
source: fix-v:ai-reply-06-draft-audit-evidence-preflight-plan:fix-1
severity: P1
---
经验：来源元数据缺失时回退输出 rule ID，会把内部实现标识暴露给运营，并造成“该事实支持该问题”的假象。
正确做法：问题来源只显示已返回 evidence snapshot 的 displayName；缺失条目统一显示“暂无可核验事实”或“未命名事实”，绝不显示 ID、intent、coverage key 或 warning code。
反例：app.js:8462-8471。
