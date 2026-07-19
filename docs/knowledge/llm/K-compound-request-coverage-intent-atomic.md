---
id: K-compound-request-coverage-intent-atomic
domain: llm
created: 2026-07-15
last_used: 2026-07-19
hit_count: 16
source: create-p:ai-reply-p0-p2
severity: P0
---
经验：request 级“有一条 QA 规则”不能证明复合问题已完整回答；`selection + matching`、`responsibilities + deliverables`、`contract + finance + IP` 任一子意图有据都会掩盖其他缺口。
正确做法：后端先把 request 拆成稳定原子 intent，再以 QA coverage key 为每个 intent 绑定本 request 的证据；group 状态只能由全部 intent 聚合，模型不得自行定义 intent 或跨 request 借事实。
关联：[[K-request-facts-not-flat-pool]]、[[K-grounding-status-ui-only]]。
