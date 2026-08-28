# Child 01 Execution Report — lastPublicationYear 补齐并重新分类

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate/docs/plans/2026-08-28/01-lastpublicationyear-recovery.md`
- Plan SHA-256: `b589ff7cef60126bd4fac0170ca02d6e1574b59fea3ca8f475909c31771f7799`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate/docs/plans/2026-08-28/01-lastpublicationyear-recovery.md@b589ff7cef60126bd4fac0170ca02d6e1574b59fea3ca8f475909c31771f7799`
- Execution epoch: NEW
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate` (branch `fast/single-gate`)
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate@fast/single-gate@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-single-gate`
- Child base SHA: `1f5a916489933fc9b2e8e469037fc912d55edd5d`
- Implementation commit: see below (`git rev-parse HEAD`)
- Executor: `Impl01YearBackfill`

## Files changed (exactly the 8 authorized; nothing else)

| # | File | Change |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | `parseAuthorEnrichmentFromNode()` 解析 `counts_by_year`（取 `works_count > 0` 的最大 year，I1-1/I1-2/I1-3）；构造传入；`AuthorEnrichment` 尾部加 `val lastPublicationYear: Int? = null`（I1-7） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | `updateExpertAcademicFields()` 写入 doc（I1-3/I1-4）；新增 `buildLastPublicationYearBackfillFilters()`（I1-5）；`EnrichmentScope` 加 `LAST_PUBLICATION_YEAR_BACKFILL`；`when` 新增一支（I1-6）；`getEnrichmentStats()`/`EnrichmentStats` 加 `lastPublicationYearPending` |
| 3 | `src/main/resources/static/index.html` | S1-1 逐字新增一行（enrichYearBackfill 下拉项） |
| 4 | `src/main/resources/static/app.js` | `handleDiscoverOption` 新增 `enrichYearBackfill` 分支 → `handleEnrichExperts("LAST_PUBLICATION_YEAR_BACKFILL")` |
| 5 | `src/test/resources/openalex/author-response-sample.json` | fixture 加 `counts_by_year`（含 `works_count: 0` 且大于所有有产出年份的 2022） |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | Task 5 第 1~4 条：fixture 断言 2010；降序数组取同一最大值（I1-1）；无键/空数组/全零 → null（I1-3）；批量路径解析（共用解析点） |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | Task 5 第 5~7 条：`lastPublicationYear=2026` 无条件写入 doc（I1-4）；null 时 doc 不含该键（I1-3）；新增 scope 过滤器断言（I1-5）；stats 断言 `lastPublicationYearPending`；既有 21 处无参调用零改动通过（I1-6） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt` | 新枚举值 `LAST_PUBLICATION_YEAR_BACKFILL` scope 透传断言；`EnrichmentStats` 构造补新字段 |

## Invariant compliance

- **I1-1/I1-2**: 取 `works_count > 0` 的最大 year；fixture 中 `works_count=0` 的 2022 不被选中（断言 2010）；降序输入测试同值 2022。
- **I1-3**: 无键/空数组/全零 → null；`updateExpertAcademicFields` 中 `?.let` 保证 null 时不写入键；单测断言 doc 不含该键。
- **I1-4**: 非 null 无条件覆盖（与 `institutionType` 同款 `?.let`）；单测断言覆盖已有 2015 → 2026。
- **I1-5**: 过滤器含 `must exists enrichedAt` + `must_not [exists lastPublicationYear, prefix orcidId "EMAIL-"]`；单测逐项断言。
- **I1-6**: `buildEnrichmentFilters(cutoff)` 与 `when` 既有两支逐字未改（git diff 确认）；21 处无参调用零改动通过。
- **I1-7**: `AuthorEnrichment.lastPublicationYear` 位于最后一个位置且带 `= null`。
- **I1-8**: `ExpertClassificationService.kt` 零改动（`VERSION` 未变）；Task 6 运维不执行（不归本计划）。
- **S1-1**: `index.html` 仅一行新增、与契约代码块逐字一致；`styles.css` 零改动。

## Commands (all ran freshly in this invocation, JDK 11 zulu)

| Command | Exit | Result |
|---|---|---|
| `JAVA_HOME=...zulu-11... mvn test -Dtest='OpenAlexDataSourceTest,ExpertDiscoveryServiceTest,ExpertDiscoveryControllerTest'` | 0 | BUILD SUCCESS；OpenAlexDataSourceTest 34/0/0，ExpertDiscoveryServiceTest 67/0/0，ExpertDiscoveryControllerTest 15/0/0（tests/failures/errors） |
| `JAVA_HOME=...zulu-11... mvn test -Dtest=ExpertClassificationServiceTest` | 0 | BUILD SUCCESS；30 tests, 0 failures, 0 errors（分类器回归，M-3 零改动） |
| `node --check src/main/resources/static/app.js` | 0 | OK |
| `git diff --check` | 0 | no output |
| `JAVA_HOME=...zulu-11... mvn test`（全量回归，跑了两遍） | 0 | **Tests run: 2960, Failures: 0, Errors: 0, Skipped: 5**（基线 2952 + 新增 8 = 2960）；JS **755 tests, 755 pass, 0 fail**（120 suites）；BUILD SUCCESS |

## Deviations

- None in implementation scope.
- 环境注记：`worktree_identity.py` 因 `git worktree list --porcelain` 中一条已失效的 locked 条目（`/sessions/…` 不存在）而报错；已用等价 git 命令手动核验 worktree 身份（root/branch/HEAD/git_dir 全部匹配），并记录了 Worktree ID。计划身份（plan_identity.py）正常返回。

## Post-execution state

- Implementation commit message: `feat(fast-p): implement child 01`
- Commit contains ONLY the 8 authorized files; fast-p docs (`docs/plans/fast/`, `docs/runbooks/institutiontype-backfill-run.md`) excluded and left untracked for controller evidence commit.
- No push/merge/rebase/amend; Task 6 (运维三步) not executed per scope.
