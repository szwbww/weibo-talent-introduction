# Child 01 Brief — 修复 PDF 邮箱归属（标记/段落边界 + 抽取缓存版本）

Approved child plan: `docs/plans/2026-09-29/discovery-repair-01-contact-ownership.md`
Approved bytes identity: plan-seed commit `70f550658d078b228fe619b735d81f0e740c4db3`; sha256 `734c36236b55518d21a68306ae90cf73dadb7c0691fac356d4563742f26763af`.
Read the full child plan from disk before implementing; it is the complete approved contract (需求描述 / 关键不变量 / 现状审计 / 实现方案 T-1–T-3 / 变更文件清单 / 验收标准 / 人工验收清单).

## Execution context

- Retained worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master`
- Branch: `fast/2026-09-29-discovery-repair-00-master`
- `child_base_sha`: `b9ec45b008f5c4f965679b99575db0ea8fe50731` (plan-seed + ledger-init commit; product code byte-identical to master base `1cd59e31164d11e962203e31c9f61310f2bc5912`)
- Master plan: `docs/plans/2026-09-29/discovery-repair-00-master.md` (same seed commit). Master invariants M-1 (邮箱归属先于学术身份传播) and M-3 are the parent rules; for this child M-1 plus I-1/I-2/I-3 below are governing.
- Execution report to write: `docs/plans/fast/2026-09-29-discovery-repair-00-master/children/01/execution.md` (controller commits it as fast-p evidence; never include `docs/plans/fast/**` in the implementation commit).
- Baseline evidence: `docs/plans/fast/2026-09-29-discovery-repair-00-master/children/01/baseline.md` (recorded by controller at this child's base revision; product code identical to this base).
- Use the `execute-p` skill with this brief plus the exact child plan; return its report shape.

## Hard constraints

- Only the 8 Authorized Files below may change. No other product, test, resource, config, migration, or fixture file.
- No new DB migration; no ES field/schema change; no production or online-data writes; no real mail sending; no changes to scheduling or deployment.
- Do not change `DiscoveryIdentity.VERSION`; only the extraction cache version constant (plan T-2). No data migration / re-enqueue.
- Keep `SourceAuthorEmailResolver`'s public interface and its existing conflict rules. The plan forbids widening them without a plan amendment: if you believe new code in that file is required, stop with `PLAN_CONFLICT`.
- Preserve: 明确的一人多邮箱、HTML/纯文本已有明确归属、未知邮箱保留线索、原有邮箱验证/资格/去重、现有已存身份凭证有效性.
- Out of scope: 猜测所有姓名变体、修改元数据拆名公共规则、自动清理线上档案、自动重跑旧缓存、修改原文下载策略.
- Minimal diff; no unrelated reformatting or refactoring. New shared "smart name splitting" service is explicitly forbidden.
- One local commit with exact subject `feat(fast-p): implement 01`. No push/merge/rebase/amend/squash; do not touch other worktrees.

## Authorized Files (8)

| # | File | Action |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt` | 标记与段落边界 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` | 仅 `EXTRACTION_VERSION`（当前 `20261002`，取更大值） |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` | 原文与合成解析回归 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 真实消费链回归 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt` | 缓存版本回归 |
| 6 | `src/test/resources/discovery/ownership-20260929.zip` | 原文证据，逐字复制自 `docs/plans/2026-09-29/discovery-repair-evidence/original-source-evidence.zip` |
| 7 | `src/test/resources/discovery/ownership-20260929-expected.json` | 逐案预期与出处（仅真实禁止/允许关系，基线值、来源、SHA） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` | 同步 `EXTRACTION_VERSION` pin（`20261002 → 20261003`）— 由 amendment **A1** 授权（`commit:cc57128f3c2dc1f4bbadd86d2809a1ce787d7e7c`）；只允许改该一行断言 |

## Child invariants (verbatim contract)

### Invariant I-1：唯一性必须包含原文作者
- Rule：同一标记在原文作者区出现多个拥有者，或者存在不能对应元数据的拥有者时，该共享标记不得证明某一个人的邮箱；不能仅对成功匹配的元数据作者计数。
- Applies to：`PdfAuthorContactLayout.collect/markerOwners` 的标记归属。
- Violation consequence：元数据少一个中间名或连字符差异就把共享邮箱全部给首位作者。

### Invariant I-2：每个联系片段独立归属
- Rule：同一行不同标记的邮箱分段处理；新作者联系语句即使尚未匹配到元数据，也必须终止上一段。一个无冲突的作者片段可有多个邮箱。
- Applies to：marker contact、paragraph contact；`SourceAuthorEmailResolver.resolvePdf` 消费结果。
- Violation consequence：上一作者继承下一作者邮箱、ORCID/OpenAlex ID 和机构。

### Invariant I-3：歧义只保留邮箱线索
- Rule：归属无法确定时不生成 Contact 身份证明；现有 resolver 可以保留 UNKNOWN_OWNER 邮箱，但不给其补上姓名、作者 ID、机构和 identityEvidence。旧版本抽取结果必须拒绝消费；已入库凭证版本保持不变。
- Applies to：collect 输出→resolvePdf→extractQueuedItem→consumeQueuedExtraction。
- Violation consequence：旧缓存重新导入已经修掉的错误，或无关的已入库专家批量失效。

## Tasks

- **T-1（I-1、I-2）** `PdfAuthorContactLayout.kt`：作者片段拥有者计数必须包含未匹配元数据的署名；支持符号集合至少 `*`、`∗`、`†`、`‡`、`§` 并做 `∗`/`*` 归一；同一联系行按标记边界切分后各自抽邮箱（分段不明确不出证明）；`is with`/`are with` 作为段落边界（含 `A. K. Singh` 多首字母），边界判断独立于能否匹配已知作者；保留几何列限制与最多五行。
- **T-2（I-3）** `DiscoveryIdentity.kt` 仅提升 `EXTRACTION_VERSION`；`DiscoveryPipelineServiceTest.kt` 验证旧非空提取缓存失败且 ES 写入 0、新结果仍可消费。
- **T-3（I-1–I-3）** 原文贯穿回归：ZIP 逐字复制；`SourceAuthorEmailResolverTest.kt` 用真实 PDFBox 解析六份原文并覆盖合成正反例（未匹配署名的共享标记、同行 `‡`/`§`、下一段 `are with`、多首字母边界、合法一人两邮箱、纯文本/HTML 原有归属，全部明确标注 SYNTHETIC）；`ExpertDiscoveryServiceTest.kt` 让真实解析结果进入 `consumeQueuedExtraction`/create-only 捕获器，断言七条错误关系均不传递机构或 author ID；共享未知邮箱不触发作者补全。

验收标准与人工验收清单（A-1–A-3）以子计划原文为准。

## Required command (fresh, after final implementation state, from the worktree root)

```
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=SourceAuthorEmailResolverTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryIdentityTest test
```

Record exit code and per-class test/failure/error/skip counts from `target/surefire-reports`. Do not run project-wide or full-package suites; do not run `mvn clean package` (controller runs it once at the end). Evidence-only commands must be read-only.

## Downstream interface (child 02 consumes this code head)

- `ExpertDiscoveryService.consumeQueuedExtraction` stays the single extraction-consumption seam and keeps returning `IDENTITY_EXTRACTION_VERSION_UNSUPPORTED` for incompatible cached payloads; child 02 edits the same file strictly downstream of identity admission (OpenAlex enrichment path).
- Preserve public shapes: `SourceAuthorEmailResolver` result semantics incl. UNKNOWN_OWNER placeholders, `PdfAuthorContactLayout.collect(...)` signature used by `PdfEmailExtractor`, and the existing `DiscoveryIdentity` constants other than `EXTRACTION_VERSION`.
- Do not alter or delete fixture resources added by earlier runs under `src/test/resources/discovery/` (`source-email-ownership-cases.*`, `*-recall.zip`, etc.); they stay valid.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.
