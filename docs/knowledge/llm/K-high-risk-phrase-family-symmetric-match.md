---
id: K-high-risk-phrase-family-symmetric-match
domain: llm
created: 2026-07-16
last_used: 2026-07-17
hit_count: 7
source: fix-v:ai-reply-p0-p2-master-plan:fix-1
severity: P1
---
经验：高风险 phrase family 若答案侧只检查 canonical key、来源侧才检查全部 aliases，同义高风险声明会完全绕过 family 校验。
正确做法：答案与引用来源都用同一边界安全 matcher 遍历整个 family；答案命中任一成员时，来源必须命中同一 family 的任一成员。
反例：family key 是 `labor contract`，答案写 `employment contract`，无对应来源时旧实现不触发拒绝。
