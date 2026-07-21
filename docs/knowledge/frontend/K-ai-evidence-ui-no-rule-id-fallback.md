---
id: K-ai-evidence-ui-no-rule-id-fallback
domain: frontend
created: 2026-07-21
last_used: 2026-07-21
hit_count: 5
source: fix-v:ai-reply-06-draft-audit-evidence-preflight-plan:fix-1
last_source: fix-v:ai-reply-failure-trust-closure-master-plan:blocked-after-fix-1
severity: P1
---
经验：来源元数据缺失时回退输出 rule ID，会把内部实现标识暴露给运营，并造成“该事实支持该问题”的假象。
正确做法：生成后优先使用 evidence snapshot 的有效 displayName；生成前 snapshot 不存在时，回查页面已加载 suggested/rulesByCategory 的 displayName，再依次回退 sectionTitle、replySubject、`事实名称缺失`。`未命名事实` 视为无效占位并继续回查；绝不显示 ID、intent、coverage key 或 warning code。
反例：只读 `draft.result.evidenceSources`，导致生成前后台明明已有 displayName，页面仍批量显示“未命名事实”。
