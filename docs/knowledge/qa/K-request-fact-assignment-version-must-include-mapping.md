---
id: K-request-fact-assignment-version-must-include-mapping
domain: qa
created: 2026-08-05
last_used: 2026-08-19
hit_count: 1
source: create-p:trust-reply-configurable-workbench
severity: P1
---
经验：逐摘要允许人工配置事实后，只保存/版本化事实 ID 并集不够；相同并集从摘要 A 换绑到摘要 B 时，旧 evidenceSetVersion 和 locked version 仍可能被接受。
正确做法：服务端以 canonical requestKey→ordered factRuleIds 矩阵作为 authority，跨 request 全局拒绝重复 ID，并把矩阵 canonical 加入确定性 evidence version。旧 flat 输入只能确定性归一化且每条事实恰好消费一次；新旧字段同时出现必须拒绝。
关联：[[K-request-facts-not-flat-pool]]、[[K-explicit-fact-selection-must-match-request]]、[[K-ai-reply-evidence-version-deterministic]]。
