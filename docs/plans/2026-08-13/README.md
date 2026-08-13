# 批量发送状态一致性 — 主计划索引（路线乙）

创建日期：2026-08-13 ｜ 生成方式：create-p ｜ 所有结论均带 grep 回执

## 起因

运营反馈三个问题：① 批量任务配置加过滤条件后看不到待发送数量；② 缺少专家状态过滤；
③ 手动发过介绍邮件的专家仍显示"未联系"。

诊断过程中发现问题远不止这三个，且 ①②③ 之间存在硬依赖。

## 根因判定

"状态不一致"不是一个 bug，是三条性质同时不成立的必然结果：

| | 性质 | 现状 | 证据 |
|---|---|---|---|
| **P1** | 唯一写入口 | ✗ 两套独立实现 | `ManualOutreachTxHelper:46` 硬编码字符串 vs `ExpertOperatorStatusService:61` 走枚举 |
| **P2** | 期望值可判定 | ✗ 无任何对账 | 全仓无反推校验；「回刷 ES」是单向推不是 reconciler |
| **P3** | 机械强制 | ✗ 只靠约定 | pom.xml 无 ArchUnit/Konsist；无守卫测试 |

### 为什么不能"消除冗余"（这条路已验证走不通）

- `grep -rn "OperatorStatus.COMPLETED" src/main/kotlin` → 仅 `ExpertOperatorStatusService:53`
  一处，且是读取判断。**COMPLETED 无任何自动来源，只能人工设置** → operator_status 无法完全派生。
- `buildExpertFilters` 供给 `searchExperts:157` + 三个聚合（`aggregateTags:714` /
  `aggregateRegions:759` / `aggregateEmailDomains:866`）。全索引 terms 聚合搬不到应用层
  → **ES 那份副本无法移除**。

**所以根源方案是"让冗余可证明地收敛"，不是"消除冗余"。**

## 子计划（7 个）

| 序 | 文件 | 目标 | 性质 | 文件数 | 前置 |
|---|---|---|---|---|---|
| P-0 | [00-manual-panel-missing-fields.md](00-manual-panel-missing-fields.md) | 手动执行面板补 regions/roundsPerRun | 止血 | 2 | 无 |
| P-A | [01-operator-status-single-writer.md](01-operator-status-single-writer.md) | operator_status 收敛为唯一写入口 | **根源 P1** | 9 | 无 |
| P-D | [02-single-writer-guard-test.md](02-single-writer-guard-test.md) | 唯一写入口的守卫测试 | **根源 P3** | 2 | P-A（同发布） |
| P-B | [03-es-mapping-contract-convergence.md](03-es-mapping-contract-convergence.md) | ES mapping 契约收敛 | 地基 | 7 | 无 |
| P-C | [04-operator-status-reconciler.md](04-operator-status-reconciler.md) | 状态对账作业 | **根源 P2** | 7 | P-A |
| P-E | [05-recipient-scope-status-filter.md](05-recipient-scope-status-filter.md) | RecipientScope 接入状态过滤 | 功能 | 10 | P-A + P-B |
| P-F | [06-recipient-count-preview.md](06-recipient-count-preview.md) | 批量任务收件人预估 | 功能 | 6 | P-E |

## 依赖图与建议顺序

```
P-0 ────────────────────────────────(独立，最高风险/成本比，先做)

P-A ──┬─→ P-D (同一发布列车，不可分开发布)
      └─→ P-C

P-B ──┐
      ├─→ P-E ──→ P-F
P-A ──┘
```

**为什么 P-0 排第一**：生产实测 P-A 的存量损害仅 1 行（`expert_contact.id=2089`，
用户测试数据）；而 P-0 的缺陷是静默错发——配了地区限制的任务点一次"手动执行"就发给全球，
无报错无日志痕迹，爆炸半径无上界。

**为什么 P-A 必须在 P-E/P-F 之前**：后两者消费 `operator_status` 这列数据，
数据本身不可信时在其上做过滤和统计等于盖空中楼阁。

**为什么 P-A 与 P-D 不可分开发布**：P-A 的 T-2/T-3 才把手动路径接通到
`updateAutomatically`，从而**创造出** I-1/I-2 的触发条件。先发 P-A 后补 P-D 的窗口期里，
任何人加一条旁路都不会被发现。

## 已证伪 / 需修正的知识条目

1. **`docs/knowledge/es-index/K-es-dynamic-false.md`** —— 声称三层索引均 `dynamic: false`。
   实测（`_mapping?filter_path=**.dynamic`）：CANDIDATE / RAW 返回 `{}`（键不存在 → ES 默认
   true），仅 APPLICATION 返回 `"false"`。**只对 APPLICATION 成立**，P-B 负责修正。

2. **`docs/knowledge/es-index/K-es-bootstrap-create-only-on-404.md`** —— 该条目本身描述的是
   创建路径的正确姿势，无误；但它容易被误读成"改本地 JSON 对既有索引无效"。
   实际 `bootstrapMappings():66-70` 每次启动都对三层 `PUT _mapping`，
   真正的阻塞是 `loadMappingProperties` 的 `phase5NewFields` 白名单（`:111-117`）。
   P-B 负责补充说明。

## 未纳入本轮的观察（有意 out-of-scope）

- ES 同步在 `@Transactional` 内、未挂 `afterCommit`：DB 回滚会留 ES 脏数据且不自愈。
  批量路径同病，属既有模式，单独立项。
- 前端 `operatorStatusLabels`（`app.js:609-616`）缺 `EMAIL_INVALID`，界面直出裸英文串
  （`app.js:4714` 的 `|| contact.operatorStatus` 兜底）。既有显示缺口。
- `enrichedAt` 在 CANDIDATE/RAW 是 `keyword` 而非 `date`，`ExpertDiscoveryService:795/806`
  的 `range` 是字典序比较——因 `ofPattern("yyyy-MM-dd HH:mm:ss")`（`:74`）定宽零填充而侥幸正确。
  P-B 记录但不修（改类型需 reindex，成本另议）。
