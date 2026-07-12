---
id: K-gap-items-compose-only
domain: qa
created: 2026-06-27
last_used: 2026-07-12
hit_count: 7
source: fix-v:gap-clickable-rule-highlight:verification
severity: P2
---
经验：`CompositionSuggestResult.gapItems` 是人工组装台的建议接口数据，不参与入站自动回复缺口判定；修改其结构时应只追踪 `toResponse` 和前端 `renderComposedGapList` 消费链路。
正确做法：缺口展示结构可携带 UI 所需的派生字段，但 `detectGap` 仍应独立读取原始问题文本与规则命中集，避免展示层结构变化影响自动管线。
反例：把 `gapItems` 结构改造误接入 `detectGap` 或自动回复 `match` 路径，导致人工组装台 UI 需求改变自动转人工语义。
