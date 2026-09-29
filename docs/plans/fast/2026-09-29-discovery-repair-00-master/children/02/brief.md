# Child 02 Brief — ORCID 多作者禁止任取一人补全

Approved child plan: `docs/plans/2026-09-29/discovery-repair-02-orcid-enrichment.md`
Approved bytes identity: plan-seed commit `70f550658d078b228fe619b735d81f0e740c4db3`; sha256 `9f13ad0454973f5ed31737d8388e3320aa0e6cf6fd551002f9bd7a038e53c82b`.
Read the full child plan from disk before implementing; it is the complete approved contract (需求描述 / 关键不变量 / 现状审计 / 实现方案 T-1–T-3 / 变更文件清单 / 验收标准 / 人工验收清单).

## Execution context

- Retained worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master`
- Branch: `fast/2026-09-29-discovery-repair-00-master`
- `child_base_sha`: `93308663e593177a7d6a7631dc4f90aea6f98c80` — child 01's terminal `Code head` (recorded in `docs/plans/fast/2026-09-29-discovery-repair-00-master/ledger.md`). The intervening commits (`ccd99da`, `66f9c1c`) are child-01 fast-p evidence only. Do not start from any earlier revision.
- Master plan: `docs/plans/2026-09-29/discovery-repair-00-master.md` (same seed commit). Master invariant **M-2（歧义不产生学术事实）** is the parent rule; child invariants I-1/I-2/I-3 below are governing.
- Execution report to write: `docs/plans/fast/2026-09-29-discovery-repair-00-master/children/02/execution.md` (controller commits it as fast-p evidence; never include `docs/plans/fast/**` in the implementation commit).
- Use the `execute-p` skill with this brief plus the exact child plan; return its report shape.

## Hard constraints

- Only the 7 Authorized Files below may change.
- No new DB/ES field or migration (`expert_academic_enrichment_job` V131 already has `result_json`/`last_error`; no new status value, no new state column, no new review system).
- Reuse the existing `UNMATCHED` status with `last_error=AUTHOR_IDENTITY_AMBIGUOUS`; do **not** map ambiguity to NotFound and do **not** add a permanent ban — existing re-enqueue rules stay.
- Preserve: 可信 OpenAlex 作者 ID 优先、唯一作者正常补全、三层局部更新与身份 CAS、邮箱/机构原有规则、429/额度延期/网络失败口径与租约、一人多邮箱分别对应真实 ES 文档。
- Out of scope: 凭名字选人、自动合并 OpenAlex 作者、删除现有学术数据、重跑全库、无界翻页或第二套限额框架.
- Ambiguity must skip `updateExpertAcademicFields` **and** `revalidationService` calls and any recent-paper/title request; existing facts must not be cleared.
- Minimal diff; no unrelated refactoring. One local commit with exact subject `feat(fast-p): implement 02`. No push/merge/rebase/amend/squash; do not touch other worktrees.

## Authorized Files (7)

| # | File | Action |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | 唯一性、完整性、查询结果 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 结果贯穿与汇总（child 01 已改此文件，只在其之上增量修改） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentJobService.kt` | UNMATCHED 原因/审计 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | 传输输入回归 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 按文档跳过与保留写路径 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentJobServiceTest.kt` | 终态/CAS/次数 |
| 7 | `src/test/resources/discovery/orcid-collision-20260929.json` | 真实摘录测试材料（注明"由真实摘录构造的测试响应"，补充的 meta.count/summary_stats 不得冒充在线抓取值） |

## Child invariants (verbatim contract)

### Invariant I-1：先分组判身份，再读取学术事实
- Rule：ORCID 完整响应中不同有效作者 ID 数量为0→NotFound，1→Success 候选，超过1→AmbiguousIdentity。按规范化作者 ID 去重；顺序、相同姓名、引用量均不改变歧义结果。
- Applies to：`enrichAuthorByOrcidWithReason`、`batchEnrichByOrcids`。
- Violation consequence：Nobuhiro 再次收到天体物理/法律学主题。

### Invariant I-2：不完整响应不能证明唯一
- Rule：ORCID 查询按每页200条请求；只有 results 为数组、meta.count 为非负整数且等于返回数组长度时才能作唯一/未找到判定。截断、缺少 count、结构损坏返回 ApiError，不返回部分 Success/NotFound。明确查到多个有效 ID 也可统一等完整性检查后再分类，避免不同入口口径漂移。
- Applies to：单条/批量 ORCID 列表请求。
- Violation consequence：按输入人数截断，恰好只见同 ORCID 的一个作者后误判。

### Invariant I-3：身份歧义为未匹配，不写事实
- Rule：新增 `EnrichmentOutcome.AmbiguousIdentity` 与 `ProfileEnrichmentOutcome.AmbiguousIdentity`；语义只表示本次无法唯一选择作者。worker 复用 `UNMATCHED`，`last_error=AUTHOR_IDENTITY_AMBIGUOUS`，`result_json.outcome=AMBIGUOUS_IDENTITY`；故障 attempts 不增加。该结果不调用学术写入、再资格核验或标题请求，也不把现有事实清空。
- Applies to：查询结果→`enrichIdentityGroups`→人工结果汇总/自动批次计数→job classify/resultJson。
- Violation consequence：歧义仍被当成功、无限即时重试或空值覆盖已有资料。

## Tasks

- **T-1（I-1、I-2）** `OpenAlexDataSource.kt`：单条/批量复用同一文件内解析函数；规范化入参 ORCID/作者 ID；`per_page=200` 并核对 `meta.count` 与数组长度；超过一页的批次整体 `ApiError("ORCID_RESPONSE_INCOMPLETE")`（不加无界翻页/第二套限额重试）；组内两个不同规范 ID 即 `AmbiguousIdentity`（相同节点重复不算两个人）；同 ID 节点学术内容冲突时 ApiError；无有效作者 ID 的关联节点返回该身份 ApiError；结构损坏不能当 NotFound；单条复用同一分组判定，唯一后仍可调用既有详情；兼容可空包装对歧义返回 `null`；明确 A ID 查询分支语义不变；429/503/额度异常原样上抛。
- **T-2（I-3）** `ExpertDiscoveryService.kt`：结果类型、`enrichIdentityGroups`、历史补全汇总、`classifyBatchOutcome` 补 exhaustive when；歧义映射 `ProfileEnrichmentOutcome.AmbiguousIdentity`；手动任务 `failureReasons` 记录 `AUTHOR_IDENTITY_AMBIGUOUS` 且不计 enriched；自动汇总计入 unmatched；跳过 `updateExpertAcademicFields` 与 `revalidationService`。`ExpertAcademicEnrichmentJobService.kt`：classify/resultJson 复用 UNMATCHED + 固定原因码 + 固定 outcome，保留租约/重开/attempts 语义。
- **T-3（I-1–I-3）** 新 fixture `orcid-collision-20260929.json`（真实三作者 ID/ORCID/姓名/主题及出处）；三个测试文件覆盖 单条与批量重复 ORCID、三个节点所有排列、同名不同 ID、相同 ID 重复、唯一/未找到、200条截断/缺少 count/坏节点、429/预算延期与详情失败；Nobuhiro 两档案同时断言三层写入 0、再核验 0、标题请求 0；唯一作者正常补全与真实 docId 三层 CAS 沿用旧测试。

验收标准与人工验收清单（A-1–A-3）以子计划原文为准。

## Required command (fresh, after final implementation state, from the worktree root)

```
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=OpenAlexDataSourceTest,ExpertDiscoveryServiceTest,ExpertAcademicEnrichmentJobServiceTest test
```

Record exit code and per-class test/failure/error/skip counts from `target/surefire-reports`. Additionally enumerate every `EnrichmentOutcome` / `ProfileEnrichmentOutcome` usage site (grep receipts) and show none treats ambiguity as success via a hidden `else`. Do not run project-wide or full-package suites; do not run `mvn clean package`.

## Downstream interface (child 03/04 do not consume this child; child 02 closes the discovery-repair chain)

- Preserve `ExpertAcademicEnrichmentJob` status values, lease/CAS semantics, and repository contracts unchanged; only `completeWithToken` input semantics gain the new outcome.
- Keep `ExpertDiscoveryService.updateExpertAcademicFields` layer write behaviour and its identity CAS untouched; the change is admission, not writing.
- Child 01's identity-admission behaviour in `ExpertDiscoveryService.consumeQueuedExtraction` must remain intact.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.
