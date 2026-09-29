# Child 01 — Execution Report

## Execution Result: PLAN_CONFLICT

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master/docs/plans/2026-09-29/discovery-repair-01-contact-ownership.md
Plan SHA-256: 734c36236b55518d21a68306ae90cf73dadb7c0691fac356d4563742f26763af
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master/docs/plans/2026-09-29/discovery-repair-01-contact-ownership.md@734c36236b55518d21a68306ae90cf73dadb7c0691fac356d4563742f26763af
Execution epoch: NEW
Approval basis: child brief `docs/plans/fast/2026-09-29-discovery-repair-00-master/children/01/brief.md`（逐字给出子计划、授权文件、命令与不变量）
Executor: Child01Implementer
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master
Target branch: fast/2026-09-29-discovery-repair-00-master
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master@fast/2026-09-29-discovery-repair-00-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master
Pre-execution code SHA: b9ec45b008f5c4f965679b99575db0ea8fe50731
Post-execution code SHA: b944ccf0f4c1b395706a6add6d869ff59eed1a75
Evidence HEAD: N/A（fast-p 证据由 controller 单独提交；本报告与其余 `docs/plans/fast/**` 未进入实现提交）
Implementation boundary: b9ec45b008f5c4f965679b99575db0ea8fe50731..b944ccf0f4c1b395706a6add6d869ff59eed1a75（仅 7 个授权文件）

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 标记与段落边界（I-1、I-2） | IMPLEMENTED | `PdfAuthorContactLayout.kt` | ①每页先按几何作者标题行 + 署名区（`authorArea`：作者行起、遇机构/联系/摘要行即止）取拥有者，未匹配元数据的署名计为 `null` 拥有者；②符号集合 `*∗†‡§` 且 `∗`→`*` 归一；③同一联系行按标记边界切分（成组标记视为分段不明确）；④`paragraphBoundary` 用 `(?:is|are) with` 终止上一段且与元数据匹配无关；几何列限制与最多五行未动 |
| T-2 仅提升 `EXTRACTION_VERSION`（I-3） | IMPLEMENTED | `DiscoveryIdentity.kt`、`DiscoveryPipelineServiceTest.kt` | `EXTRACTION_VERSION` 20261002 → 20261003；`VERSION` 仍为 20260925（同一 run 内 `DiscoveryIdentityTest` 的 `20260925` 断言未受影响） |
| T-3 原文贯穿回归（I-1–I-3） | IMPLEMENTED | `SourceAuthorEmailResolverTest.kt`、`ExpertDiscoveryServiceTest.kt`、两个新 fixture | 六份原文逐字 ZIP + manifest 双口径 SHA 校验；真实 PDFBox 解析 + 生产作者映射（`OpenAlexDataSource`）；七条禁止关系、六条 allowed 关系、20 条必须保持线索的邮箱逐条断言；消费侧断言落在 create-only 捕获器上；四条 SYNTHETIC 正反例（未匹配署名共享标记、同行 `‡`/`§` 与 `∗`/`*` 归一、下一段 `are with`、多首字母 `A. K. Singh`）——测试文件仅新增，`git diff -U0 src/test | grep '^-'` 行数为 0，未弱化任何既有断言 |
| 授权范围外：`DiscoveryIdentityTest.kt` 的版本 pin | CONFLICT | — | 见下 |

### 逐案回归结果（`target/discovery-plan-acceptance/01.json`，写入 target、不提交）

| 原文 | 基线可复现性 | 修复后仍具名的邮箱 | 必须保持线索 |
|---|---|---|---|
| crea | reproduced | —（共享 `*` 判为无法唯一归属） | simona.crea / ristic / jan.veneman.cost |
| debie | reproduced | e.debie→Essam Debie；helge.janicke→Helge Janicke | nour.moustafa / marwa.hassan |
| kumar | reproduced | jitendra→Jitendra Kumar；stefan.schmid→Stefan Schmid | ashutosh / 13deepikasaxena / d.saxena |
| nguyen | not-reproduced-in-rerun | — | 5 条（含 damien.bouchabou） |
| everett | not-reproduced-in-rerun | — | 5 条（含 aderuiter） |
| lumma | not-reproduced-in-rerun | j.lumma→Johannes Lumma；m.pauly→Martin Pauly | mark.hindmarsh / mlueben |

消费侧（`target/discovery-plan-acceptance/01-consumer.json`）：RAW 6 / CANDIDATE 6（即 6 条 allowed 关系），仅这 6 个地址进入邮箱校验；20 条线索邮箱既不校验也不落档，因此不触发作者补全；七条禁止关系在 create-only 捕获器里既无错误姓名，也无错误作者 ID。

## Conflict (阻止 READY_FOR_VERIFICATION)

`DiscoveryIdentityTest.kt` **不在**子计划的 7 个授权文件内，却把被取代的抽取版本逐字钉死：

```
[ERROR] Tests run: 7, Failures: 1, Errors: 0, Skipped: 0  in com.weibo.talentintroduction.expert.domain.DiscoveryIdentityTest
[ERROR] DiscoveryIdentityTest.new extraction cache version is accepted and prior version remains rejected:54
        expected: <20261002> but was: <20261003>
```

- T-2 要求 `DiscoveryIdentity.EXTRACTION_VERSION` 变成**大于 20261002** 的值，而 `DiscoveryIdentity.kt` 是 T-2 唯一授权可改的产品文件。
- 任何不等于 20261002 的取值都会让 `assertEquals(20261002, DiscoveryIdentity.EXTRACTION_VERSION)`（第 54 行）失败，而该测试类是 brief 指定必跑命令的一部分。
- 要修它必须改一个授权清单之外的测试文件，需要人工批准的 plan amendment（brief 明确禁止擅改）。
- 最小修复：把 `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` 加入 child 01 授权文件，并把该 pin 改为 `20261003`（同一测试里 `DiscoveryIdentity.VERSION == 20260925` 的断言不动）。本仓库此前每次提升 `EXTRACTION_VERSION` 的计划都恰好授权了这个文件（例：`docs/plans/2026-09-26/...` 各子计划「`src/test/kotlin/.../DiscoveryIdentityTest.kt` — 版本边界」）。
- 按 brief 要求，这里选择**不上手改未授权文件**并返回 PLAN_CONFLICT；除此之外全部授权工作已完成，required command 里其余三个类全绿。

## Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=SourceAuthorEmailResolverTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryIdentityTest test` | FAIL（唯一失败＝未授权 pin） | exit 1；日志 `/tmp/child01-required.log`；total 251 tests / 1 failure / 0 errors / 0 skipped。`target/surefire-reports` 逐类：SourceAuthorEmailResolverTest 24/0/0/0、ExpertDiscoveryServiceTest 173/0/0/0、DiscoveryPipelineServiceTest 47/0/0/0、DiscoveryIdentityTest 7/1/0/0（`expected: <20261002> but was: <20261003>`，:54）。基线（brief 记录）为 19/172/46/7 共 244 例全绿 → 本次 +7 例、仅 1 例失败＝上述 pin |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=SourceAuthorEmailResolverTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest test` | PASS | exit 0 / BUILD SUCCESS；日志 `/tmp/child01-scoped.log`；24+173+47 = 244 tests / 0 failures / 0 errors / 0 skipped（与提交后的实现状态同一份代码） |
| `git diff -U0 -- src/test/kotlin \| grep -cE '^-'` | PASS | `0`（测试侧零删除行，未弱化既有断言） |
| `git diff --check -- src/main/kotlin src/test/kotlin` | PASS | exit 0（无空白错误） |
| `cmp docs/plans/2026-09-29/discovery-repair-evidence/original-source-evidence.zip src/test/resources/discovery/ownership-20260929.zip` | PASS | 逐字节相同，sha256 `d250220a9bd94519e3b15e36cfd48305b976dedeb29f1a57d6fc807ee9aa19e7`（与 `ownership-20260929-expected.json` 的 `archiveSha256` 一致，测试内也有断言） |

未运行 `mvn clean package`、未跑全量/全包套件、无真实发信、无线上写入。

## Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt` — 作者署名区拥有者计数（含匹配不上元数据的署名，最长候选逐级回退到元数据全名）、`*∗†‡§` 标记集合与 `∗`/`*` 归一、同一联系行按标记边界分段、`is with`/`are with` 段落边界
- `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` — 仅 `EXTRACTION_VERSION` 20261002 → 20261003（`VERSION` 未变）
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` — 六份原文真实解析回归 + 四条 SYNTHETIC 正反例 + 共享 fixture/作者映射辅助
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` — 真实解析结果进入 create-only 消费链的回归（含去重与资格拒绝）
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt` — 旧缓存拒绝（0 写入、不重新下载）与新缓存可消费的版本回归
- `src/test/resources/discovery/ownership-20260929.zip` — 逐字复制的六份原文与 OpenAlex 作品 JSON（含 manifest）
- `src/test/resources/discovery/ownership-20260929-expected.json` — 逐案禁止/允许/线索关系、来源 URL、SHA 与基线可复现性

## Deviations

- None（除上文必须由人工授权的冲突项）。补充说明两处实现选择（均在子计划文字范围内，非偏差）：
  1. `are with` 按 T-1 第 3 条当作**段落边界**（终止上一段）而不是段落**起点**；`paragraphStart` 仍只认 `is with`，因此下一作者联系段只保留“不得继承上一段邮箱”的语义，不为无法唯一匹配的署名生成归属。
  2. 用几何作者署名区（`authorArea`）取代原先「几何标题 + pre-abstract 全文」两处计数：全文口径会把机构脚注里的标记（Debie 的 `Australia ∗†‡`、`…Australia§`）误计为作者拥有者，与 T-1 第 1 条相冲突。
- 未改 `SourceAuthorEmailResolver`（接口与冲突规则逐字未动）、未改 `DiscoveryIdentity.VERSION`、未新增迁移/ES 字段、未改任何 `docs/plans/**`、未触碰其他 worktree、未推送/合并/rebase/amend。

## Freshness

- Plan identity rechecked: YES（执行前后均为 `734c36236b55518d21a68306ae90cf73dadb7c0691fac356d4563742f26763af`，与 brief 的批准字节一致）
- Worktree identity rechecked: YES（root/branch/git-dir 与执行前一致；`git add`/`git commit` 均在该 worktree 内）
- Reported commits reachable from target branch: YES（`b944ccf` 是 `fast/2026-09-29-discovery-repair-00-master` 的 HEAD 且 `merge-base --is-ancestor` 通过；提交仅含 7 个授权文件，`docs/plans/**` 未进入）
- Required commands run this invocation: YES（最终实现状态之后；逐类计数取自 `target/surefire-reports`）
- Historical evidence used only as baseline: YES（brief 的 baseline.md 只用于对比 244 例全绿与 4 条可复现关系）

## Remaining Blocker

- 需要人工批准的 plan amendment：把 `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` 加入 child 01 授权文件，并把 `EXTRACTION_VERSION` 断言从 `20261002` 改为 `20261003`（仅此一行）。

## Next Action

- PLAN_CONFLICT → 取得上述 amendment 后在同一 child 的新 epoch 里改这一行 pin，重跑 brief 的 required command 即可 READY_FOR_VERIFICATION；本 epoch 的实现提交 `b944ccf` 保留为 epoch-1 佐证。
