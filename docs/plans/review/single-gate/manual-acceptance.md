# Manual Acceptance — 00-single-gate-master

Generated from the master plan's manual items (child-plan 人工验收清单 and deployment checklists; master plan CP-2/回滚点). Machine PASS (epoch 3) is not final acceptance; every mandatory item below requires human-originated results.

## Epoch 3 — 2026-08-29T00:14:15+0800

- Reviewed code boundary: de228e17cc0134a7c11dea7cbf82054e8d249f99..b3b7a9b78ed3f85abc0e9fc6a51a0a5a43f5695f
- Machine report epoch: 3
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| M-A1 | yes | 子计划 01 Task 6-1：跑「补采发表年份（一次性）」直至收敛 | `lastPublicationYearPending` 收敛（0 或稳定不再下降） | PENDING | — | — | — |
| M-A2 | yes | 子计划 01 Task 6-2：`onlyPending:false` 全量分类回填（先 DRY_RUN 看分布，再 EXECUTE + `confirmation:"EXECUTE_CANDIDATE:rnd-v2-2026"`） | UNKNOWN 显著下降（预期 1.5 万~2 万转出），差异全部可归因于 `lastPublicationYear` 补齐 | PENDING | — | — | — |
| M-A3 | yes | 子计划 01 Task 6-3：重跑分布脚本对比前后类型分布 | 与 DRY_RUN 一致 | PENDING | — | — | — |
| M-A4 | yes | 子计划 02 部署检查清单：确认三条旧首发链路状态（`MAIL_QUEUE_ENABLED`、`MAIL_SCHEDULING_INITIAL_OUTREACH_CRON`、HTTP 调用方） | 关闭；或已加 `MAIL_SCHEDULING_INITIAL_OUTREACH_EXPERT_TYPES`（与 SENDABLE_TYPES 等价） | PENDING | — | — | — |
| M-A5 | yes | 子计划 03：V109 迁移已应用；存量 `batch_send_task_config.expert_types_json` 非空 | 存量空行 = `["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]` | PENDING | — | — | — |
| M-A6 | yes | 子计划 05 A5-1：回填进度按研发类型分项 | 形如「已扫描 N，ACADEMIC_RND …」；无「可发信/不可发信」 | PENDING | — | — | — |
| M-A7 | yes | 子计划 05 A5-2：专家列表页零影响（回归） | 上线前后同一页显示一致 | PENDING | — | — | — |
| M-A8 | yes | 子计划 05 A5-3：新回填文档 `expertClassification` 无 `sendable` 键 | 抽查通过（存量残留属预期） | PENDING | — | — | — |
| M-A9 | yes | 子计划 05 A5-4：分类结果零变化（上线后不跑回填再聚合） | 各类型条数逐字相同 | PENDING | — | — | — |
| M-A10 | yes | 子计划 05 A5-5：发信人群零变化（预估命中数对比） | 数字逐字相同 | PENDING | — | — | — |

## Human Sign-off

- Decision: PENDING | ACCEPT | REJECT
- Boundary: b3b7a9b78ed3f85abc0e9fc6a51a0a5a43f5695f
- Reporter: —
- Timestamp: —
- Note: —
