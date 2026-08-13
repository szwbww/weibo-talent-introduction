# ES 存量文档重建倒排索引运行手册（新增 mapping 后必做）

> 对应计划：`docs/plans/2026-08-13/03-es-mapping-contract-convergence.md`（P-B，commit:c703137）
> 域：三层专家索引（RAW `orcid_info` / CANDIDATE `orcid_info_candidate` / APPLICATION `orcid_info_application`）

## 1. 为什么需要这份手册（I-4）

给既有索引 `PUT _mapping` 新增字段后，存量文档 `_source` 中该字段的值**不会**自动进入倒排索引：
「新写入的文档能查、老文档查不到」比全查不到更难排查。必须 `POST /{index}/_update_by_query`
触发重建（无 script 的 no-op update 即可）。

本次 03 交付在 **APPLICATION** 层新增/变化了以下字段，部署后必须执行本手册：

- `operatorStatus`（新增，keyword —— 晋升路径 `_source` 已透传存量值）
- `enrichedAt` / `enrichmentSource` / `patentTitles` / `recentWorkTitles`
  （此前被 Kotlin 白名单 `phase5NewFields` 挡住从未到达索引，现随 I-1 恢复推送）

> **enrichedAt 类型债**：线上三层 `enrichedAt` 均为 `keyword`（动态映射产物），本地 JSON 已迁就线上
> 声明为 `keyword`（见计划「未决决策」，方案甲）。将来若改回 `date`，必须拆独立计划（全量 reindex + 回滚预案）。

## 2. 前置条件

1. 新版本已部署并完成启动（启动日志出现 `Prepared N mapping fields from es/orcid_info_application.json`
   且 `Updated N mapping fields for index ...`，或出现 `index=... 推送 N 字段：成功 M，冲突 K（字段列表）` 汇总行）。
2. 环境变量 `$ES_USER` / `$ES_PASS` / `$ES_BASE` 可用（`$ES_BASE` 形如 `https://es.example.com:9200`，无结尾斜杠）。

## 3. 操作步骤（可复制命令）

### 3.1 确认新 mapping 已落地

```bash
# 预期返回 operatorStatus 的 mapping 而非空对象
curl -s -u "$ES_USER:$ES_PASS" "$ES_BASE/orcid_info_application/_mapping/field/operatorStatus"
```

若返回空对象：先看启动日志的 mapping 推送汇总行；存在冲突字段时逐字段降级日志
（`index=... 推送 N 字段：成功 M，冲突 K（字段列表）`）会标明失败字段与原因。

### 3.2 触发存量文档重建倒排（no-op update_by_query）

```bash
# conflicts=proceed：跳过单个文档失败；wait_for_completion=false：异步执行，返回任务 id
curl -s -XPOST -u "$ES_USER:$ES_PASS" \
  "$ES_BASE/orcid_info_application/_update_by_query?conflicts=proceed&wait_for_completion=false"
```

> 只需重建 **APPLICATION**（本次新增字段仅 APPLICATION 受影响；CANDIDATE/RAW 本次无新增字段，
> 类型变更在 mapping 层已对齐线上，无需重建）。若后续为 CANDIDATE/RAW 增加新字段，同样执行本命令换索引名即可。

### 3.3 轮询任务

```bash
# 用 3.2 返回的 task id 替换 <taskId>；completed=true 即完成
curl -s -u "$ES_USER:$ES_PASS" "$ES_BASE/_tasks/<taskId>"
```

### 3.4 验证可查询

```bash
# 预期 count > 0（改动前 exists(enrichedAt) 实测为 0）
curl -s -u "$ES_USER:$ES_PASS" "$ES_BASE/orcid_info_application/_count" \
  -H 'Content-Type: application/json' -d '{"query":{"exists":{"field":"operatorStatus"}}}'

curl -s -u "$ES_USER:$ES_PASS" "$ES_BASE/orcid_info_application/_count" \
  -H 'Content-Type: application/json' -d '{"query":{"exists":{"field":"enrichedAt"}}}'
```

## 4. 回滚

- 代码回滚（改回白名单/旧 JSON）后重启：`updateMappingIfNeeded` 不会删除已建字段，
  `operatorStatus`/enrichment 字段在索引中依然存在（ES mapping 不可删）。旧版本行为恢复
  （不推送新字段），存量倒排数据保留但不再更新，无数据损坏。
- 无需数据回滚；`_update_by_query` 只是重建倒排，不改变 `_source`。

## 5. 修订记录

| 日期 | 修订 | 作者 |
|---|---|---|
| 2026-08-13 | 初版：03 P-B 交付后存量重建流程 | Impl03 |
