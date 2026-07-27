---
id: K-grounded-proposed-action-body-parity
domain: llm
created: 2026-07-27
last_used: 2026-07-27
hit_count: 6
source: create-p:ai-reply-grounded-server-owned-envelope-plan
last_source: create-p:ai-reply-grounded-server-owned-envelope-plan
severity: P1
---
经验：让模型同时声明 action type 和 action text，或只检查独立 action 字段，都会允许 claim 正文藏入 CTA，且声明与最终正文可能不一致。
正确做法：动作只允许出现在独立 actionText 通道；服务端用同一 detector 推导 exact singleton action 并校验 allowedActions，claims 必须无动作。frame 组装后再次扫描整封正文，动作集合必须与 actionText 一致，否则进入统一修复或 fallback/BLOCKED。
