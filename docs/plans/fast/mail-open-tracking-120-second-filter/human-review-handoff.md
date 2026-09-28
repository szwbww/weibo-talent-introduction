# Fast-P Human Review Handoff

- Outcome: READY_FOR_HUMAN_REVIEW
- Master base: f98e27c7538d091bfcdecfcb6ffc10360a35ba04
- Current/final code head: e0b002076b92d3e1e543040fb5640585fc2869aa
- Branch/worktree: fast/mail-open-tracking-120-second-filter / /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mail-open-tracking-120-second-filter

Plan：`docs/plans/2026-09-28/mail-open-tracking-120-second-filter.md`（commit `3237f07e694565bda5e0e2d6a453fc654d695014`，sha256 `8b2f24faaa1a11e741c8b3ca9407ee6cfb89cf52ae9c0a1f5467cd62db4693c1`）。执行期未修改计划，无 amendment。单文件 master 按计划「阶段」拆为两个 child：`01-backend`（此部分供人工核对，非产品改动）与 `02-ui`。

## Child Status
| Child | Status | Code boundary | Fix rounds | Evidence commit |
|---|---|---|---:|---|
| 01-backend | LIGHT_PASS | 3237f07e694565bda5e0e2d6a453fc654d695014..84560652c3227cf95f50ddd12bd285c54ece96cb | 0 | 5df5d0e07921ae15f94e28ac0aa5629bda33b911 |
| 02-ui | LIGHT_PASS_WITH_NOTES | 84560652c3227cf95f50ddd12bd285c54ece96cb..e0b002076b92d3e1e543040fb5640585fc2869aa | 0 | 08d0496ed4d566b92e88f8d357ffed5a743ba170 |

产物边界（产品代码 = 计划变更文件清单的 5 文件）：

- `01-backend`（`8456065`）：`MailOpenTrackingRepository.kt` 单一 `CUTOFF = DATE_ADD(m.sent_at, INTERVAL 120 SECOND)` / `QUALIFIED = t.last_open_at > CUTOFF`，`SELECT` 输出 `qualified_signal`，列表过滤、`totalCount`、汇总分子、详情共用同一谓词；`MailOpenTrackingRepositoryIT.kt` 新增边界与跨路径用例（NULL / 119.999999s / 恰 120s / 120.000001s / 10s→121s / 早于 sent_at / 未跟踪 / 失败 / 入站 / 孤儿；filter↔list↔detail↔summary 一致；I-2 只经再次 `recordSignal` 翻状态）。
- `02-ui`（`e0b0020`）：`index.html` 与 `app.js` 按 S-1/S-2 换文案与 badge 色类（`OPENED → badge info「疑似打开（120秒后请求）」`，`NO_SIGNAL → badge warn「无120秒后请求」`，指标「120秒后请求」「120秒后请求率」，原始时间列/详情标为「首次图片请求」「最近图片请求」），11 处资源缓存键统一为 `20260928-mail-open-120s`；`src/test/js/mailOpenTracking.test.js` 新增逐字断言并保留既有竞态/分页/转义/配置用例。`styles.css` 零差异。

## RECORD_ONLY Index
| Observation | Child | Evidence | Source report |
|---|---|---|---|
| O-1 `NO_SIGNAL` 过滤为 `QUALIFIED` 的手写互补式（同源单一 `CUTOFF`，即 brief 要求形式），未来改比较方向需同时改两处 | 01-backend | `MailOpenTrackingRepository.kt:124`（`t.last_open_at IS NULL OR t.last_open_at <= $CUTOFF`） | children/01-backend/verify-log.md |
| O-2 两条必需 `mvn test` 命令经 exec 插件连带运行仓库级 JS 套件（1199 tests），耗时与基线相同 | 01-backend | baseline/*.txt 与实施日志中的 `exec:3.1.0:exec (node-test)` | children/01-backend/verify-log.md |
| O-1 审查区间 diff 还列出 11 个控制方 fast-p 证据文件（`5df5d0e`/`15cef3d`）；实现提交 `e0b0020` 仅含 3 个授权文件 | 02-ui | `git show --stat e0b0020` = 3 files | children/02-ui/verify-log.md |

## Pause/Resume
- Reason: N/A
- Resume from: N/A

未执行计划的「人工验收清单」A-1..A-6（需独立干净的验收库 + 真实像素 GET/HEAD），也未执行全系统验证；每个 child 只过了四道轻量门禁。合并或发布前请人工执行 A-1..A-6。
