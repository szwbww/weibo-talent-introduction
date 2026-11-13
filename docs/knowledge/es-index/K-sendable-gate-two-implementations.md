---
id: K-sendable-gate-two-implementations
domain: es-index
created: 2026-10-18
last_used: 2026-10-18
hit_count: 2
source: create-p:05a-institution-type-collection
severity: P1
---

发送资格的读者与写者数量不能永久钉死。旧“两处/四处逐字实现”是历史观察，不是当前事实。

2026-10-18复核：BatchRecipientSelectionService.select/resolveAdmissions读取持久准入；DiscoveryPersonRecipientService在人物维度复用；InitialOutreachService.fetchSendableCandidates通过人物selector.selectAdmission筛选；ManualInitialOutreachService也使用统一选择链。BatchExecutionModels.matchesDiscoveryOutreach仍有历史纯谓词，不能仅见定义就认定所有发送入口仍调用它。未来改规则必须从调用点反查，不只改这个旧函数。

准入也不能只审计发信读者：ExpertRevalidationService的hardBlockingReasons/reconcile可能删除候选，DiscoveryReviewScanService重新算理由。是否可发还叠加显式筛选/人物/联系保护；准入事件不代表已发送。

证据：docs/plans/2026-10-18/apollo-evidence/protect-readers.txt、knowledge-revalidation.txt；基线e7c3422。
