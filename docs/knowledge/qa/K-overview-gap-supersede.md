---
id: K-overview-gap-supersede
domain: qa
created: 2026-06-26
last_used: 2026-07-12
hit_count: 19
source: fix-v:qa-rules-phase2b:fix-1
severity: P1
---
经验：复合覆盖规则与缺口检测共存时，不能先把命中集压缩成单个复合规则再用压缩后的分类数判断缺口，否则概览型多问来信会被误转人工。
正确做法：缺口检测应使用覆盖前命中集计算覆盖度，或明确把 `supersedesChildren=true` 的复合规则视为覆盖总览型多主题意图。
反例：`QaMatchService.kt:24` 先 `applySupersede(rawMatches)`，`QaMatchService.kt:30` 再用压缩后的 `matches` 做 `detectGap`，`QaMatchService.kt:52-55` 因分类数变成 1 导致多问 OVERVIEW 邮件触发 `QA_GAP`。
