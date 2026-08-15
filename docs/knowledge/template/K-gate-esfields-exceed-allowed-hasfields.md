---
id: K-gate-esfields-exceed-allowed-hasfields
domain: template
created: 2026-08-15
last_used: 2026-08-15
hit_count: 0
source: create-p:p4a-template-gate-filter-backend
severity: P1
---

`MailComposeTemplateService.requiredEsFields(templateId)` 的返回值**不是** `ExpertSearchService`
可做存在性筛选的字段集合的子集。任何"按模板门禁预筛收件人"的功能都必须先做交集裁剪。

## 两个集合的实际差异（2026-08-15 逐字核对）

- `ExpertSearchService.ALLOWED_HAS_FIELDS` =
  `{employment, degree, institution, researchFields, patentTitles, recentWorkTitles}`
- `MailPlaceholderService.ES_FIELD_BY_KEY` 的非 null 值 =
  `{familyNames, researchFields, institution, keyword, country, employment, hIndex,
    worksCount, lastPublicationYear, degree, recentWorkTitles, patentTitles}`
- **差集**（可被 `requiredEsFields` 返回但无法预筛）=
  `{familyNames, keyword, country, hIndex, worksCount, lastPublicationYear}`

## 后果

把差集字段直接传给 `ExpertSearchService` 的 `hasField` 路径，会命中
`require(field in ALLOWED_HAS_FIELDS)` 抛 `IllegalArgumentException`：
收件预估 500、定时任务执行崩溃。

## 正确做法

裁剪到交集，丢弃的字段**打日志**，并在 UI 上明确标注「另有 N 个必填字段无法预筛」。
预筛因此是**子集近似**：开了门禁过滤，仍可能有专家在发送时被 `PersonalizationGateService` 拦下。
不标注就等于骗运营。

前端专家列表的门禁筛选器已是同款降级（`applyGateFields` 对无对应 chip 的 esField 打日志后忽略），
新功能保持一致即可，不要试图靠扩 `ALLOWED_HAS_FIELDS` 来"修"它 —— `fieldPresenceFilter` 对
`text` 类型字段的空值判定（`BLANK_EXCLUDABLE_FIELDS`）只覆盖 5 个字段，扩集合会引入
"`term ""` 匹配分词内容导致误排除"的新问题（该风险已记在 `ExpertSearchService.kt:26-31` 的注释里）。

## 内存侧同口径

`RecipientScope.matchesExpert` 做同样判定时，注意两类字段的差异：

- `BLANK_EXCLUDABLE_FIELDS`（`researchFields / recentWorkTitles / patentTitles / degree / country`）
  ES 侧是 `exists AND NOT term ""` → 内存侧空串**不算**有值。
- 其余（`employment / institution`）ES 侧只有 `exists` → 内存侧空串**算**有值，判定应是 `!= null`
  而不是 `isNotBlank()`。

写成统一的 `isNullOrBlank()` 会让两条目标来源口径不一致。

## 另一个前提：`required_keys` 当前全库为 NULL

`V84__add_required_keys_to_compose_template.sql` 明写 "No backfill"，仓库无任何 seed 给它赋值。
NULL / 空数组 = 门禁关闭。所以"按模板门禁预筛"这类功能在默认状态下**对所有模板都无效果**，
UI 必须把这种状态呈现为**置灰不可用 + 说明原因**，而不是"开关开着但什么也没发生"。

关联：[[K-openalex-enrichment-existing]]（ES 字段来源）、`intro-mail-fallback-renders-as-title`（项目记忆）
