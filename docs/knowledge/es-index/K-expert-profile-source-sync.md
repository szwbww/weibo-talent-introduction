---
id: K-expert-profile-source-sync
domain: es-index
created: 2026-07-07
last_used: 2026-07-07
hit_count: 3
source: create-p:expert-enrichment-backend
---

ES 字段新增时必须同步四处：① mapping JSON（三文件）② `ExpertProfile.kt` 属性 ③ `ExpertSearchService.sourceFields()` 列表 ④ `ExpertSearchService.toExpertProfile()` 解析逻辑。缺一则字段存在但不可用。若需要暴露到前端 API，还需同步 `ExpertIndexResponse` 及其 `from()` 工厂方法。
