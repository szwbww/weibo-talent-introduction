# c4 执行报告 — CORE offset 分页、ORCID 原始记录翻页、统一研发范围

## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/2026-09-21/04-core-orcid-scope.md
Plan SHA-256: 59b8b131e5a4f091256f56daede6ba45c50713291cdb6adb1fd905a7dbf2e271
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/2026-09-21/04-core-orcid-scope.md@59b8b131e5a4f091256f56daede6ba45c50713291cdb6adb1fd905a7dbf2e271
Execution epoch: NEW
Approval basis: fast-p run `docs/plans/2026-09-21/00-discovery-enrichment-master.md`（计划身份 `commit:831e6604cf97e7acba005d8f00827659b49ce010`）授权的本地提交；本子计划契约 = 上述 plan 文件 + `children/c4/brief.md`。执行技能 `skill://execute-p`。
Executor: C4Implementer（fast-p child c4，单一写入者）
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master
Target branch: fast/2026-09-21-discovery-enrichment-master
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master@fast/2026-09-21-discovery-enrichment-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master
Pre-execution code SHA: 915e542b8841c7496844cced0722e9453293eeeb（c3 证据提交，含 c3 code head fba6173efd061aef73ebbce1854f83decb52a84f）
Post-execution code SHA: 985f1ddf5891bdf534fbfeb2e5140ce6fd3f254b
Evidence HEAD: N/A（本子计划只要求一个实现提交；本报告位于 `docs/plans/fast/**`，按 brief 不纳入提交）
Implementation boundary: 915e542b8841c7496844cced0722e9453293eeeb..985f1ddf5891bdf534fbfeb2e5140ce6fd3f254b

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| Task 1 / I-1 CORE 改用 offset/limit，游标保存分片+offset，不存 searchId | IMPLEMENTED | CoreDataSource.kt, ExpertDiscoveryService.kt, CoreDataSourceTest.kt, ExpertDiscoveryServiceTest.kt | `searchCorePage` 以 `{"q","limit","offset"}` 请求 `/search/works`，删掉 scroll/scrollId 分支；`CoreCursor(topic,year,offset)` 编码进 `PaperSearchResult.nextCursor`；非法/历史（含 scrollId）游标按「无游标」处理。请求边界断言见 CoreDataSourceTest「pages by offset…/keeps a legacy scroll cursor…」与服务级「CORE resumes from the persisted shard offset…」 |
| Task 1 / I-4 每分片 offset 上限 9000，到界记录未覆盖尾部并切下一分片 | IMPLEMENTED | CoreDataSource.kt, ExpertDiscoveryService.kt, CoreDataSourceTest.kt, ExpertDiscoveryServiceTest.kt | `CoreDataSource.MAX_OFFSET=9000`；`nextOffset > MAX_OFFSET` 时 `windowLimit=true`、`uncoveredTail=totalHits-nextOffset`、游标切下一分片；服务层把 `DiscoveryStopReason.WINDOW_LIMIT` 记入 `bySource.CORE.failureReasons`。断言：CoreDataSourceTest「stops the shard at the vendor window…」（尾部 190900、下一分片 0\|2021\|0；到界后不再发请求）、ExpertDiscoveryServiceTest「CORE records WINDOW_LIMIT and rotates the shard…」（落盘游标 0\|2021\|0、failureReasons=1、pendingWork=true、stopReason=SOURCE_LIMIT） |
| Task 1 / V-1 跨运行续 offset、年份/主题变更独立检查点 | IMPLEMENTED | ExpertDiscoveryServiceTest.kt | 「CORE resumes from the persisted shard offset…」：预置 v2 检查点 `0\|2020\|100` 后首个请求 offset=100，落盘推进到 `0\|2020\|200`；「CORE query shards per year use independent checkpoints」：两年份各自 key、各自游标、请求 q 分别为 yearPublished=2020/2021 |
| Task 2 / I-2 OrcidSearchPage(records,nextCursor,rawCount)，按原始条数推进 | IMPLEMENTED | OrcidDataSource.kt, ExpertDiscoveryService.kt, OrcidDataSourceTest.kt, ExpertDiscoveryServiceTest.kt | 新增 `OrcidSearchPage`/`OrcidCursor(topic,offset)`；`rawCount` 取原始 `expanded-result` 条数（含无邮箱者），偏移只由它推进；发现循环改用 `searchOrcidPage` 并只搬运返回游标。断言：OrcidDataSourceTest「advances by the raw count when a whole page has no public email」（100 条无邮箱 → nextCursor `0\|100`）、服务级「ORCID pages past a whole page without public emails…」（首页 100 无邮箱、次页 1 有邮箱 → 2 次请求 start=0/100、收录 1 人、随后切主题分片） |
| Task 2 / V-2 空关键词用 scope 主题种子、无 scope 无关键词明确跳过 | IMPLEMENTED | SubjectScopeCatalog.kt, OrcidDataSource.kt, SubjectScopeCatalogTest.kt, OrcidDataSourceTest.kt, ExpertDiscoveryServiceTest.kt | 目录新增 `orcidSeedKeywords(RND_TARGET)`（六类）；空关键词时按主题种子分片检索 `keyword:"…"` 字段；无关键词且无 scope 时不发请求并记日志。断言：OrcidDataSourceTest「skips without a request…」「rotates to the next topic shard…」；服务级「ORCID skips without a request when no keyword and no scope seed exist」（`never()` 校验 0 请求、0 失败） |
| Task 2 / V-3 CORE 括号 OR 主题、Crossref 主题种子、人工关键词不被覆盖、EuropePMC/PMC OA 排除不变 | IMPLEMENTED | CoreDataSource.kt, CrossrefDataSource.kt, SubjectScopeCatalog.kt, 对应 4 个测试 | CORE：`(engineering OR materials OR computer science OR chemical OR energy OR physics) AND yearPublished=<year>`；Crossref：目录种子走 `query.bibliographic`，人工关键词保持 `query` 且不叠加种子；EuropePMC/PMC_OA 排除断言（c3 既有用例）未改动，本子计划 5 个测试类全绿 |
| Task 2 / I-3 主题目录保持唯一 SSOT，API 保持可加性 | IMPLEMENTED | SubjectScopeCatalog.kt, SubjectScopeCatalogTest.kt | 仅新增 `orcidSeedKeywords`，既有函数签名/取值逐字不变；`ALLOWED covers every branch` 增加新函数分支覆盖断言 |

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=CoreDataSourceTest,OrcidDataSourceTest,CrossrefDataSourceTest,SubjectScopeCatalogTest,ExpertDiscoveryServiceTest` | PASS | 退出码 0；`Tests run: 126, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`（ExpertDiscoveryServiceTest 85 / CoreDataSourceTest 13 / CrossrefDataSourceTest 12 / OrcidDataSourceTest 9 / SubjectScopeCatalogTest 7） |
| `JAVA_HOME=… mvn -DskipTests test-compile` | PASS | 退出码 0（主源码 + 测试源码编译，含未授权的 Java 测试助手 `DiscoveryMockHelper`） |
| 开发中同一命令的首次运行（修复前状态，仅作过程记录） | FAIL | `Tests run: 85…Errors: 1` — `CORE sends the catalogue topics…` 用 `single()` 断言请求数，实际一年一分片产生多次请求；改为 `first()` 后于最终状态重跑全绿。非交付状态。 |

### Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSource.kt` — CORE 改 offset/limit 分片协议：`CoreCursor(topic,year,offset)`、`CoreSearchPage(result,windowLimit,uncoveredTail)`、`MAX_OFFSET=9000`、`searchCorePage`、括号 OR 主题查询、去掉 scroll/scrollId。
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSource.kt` — 新增 `OrcidSearchPage`/`OrcidCursor`/`searchOrcidPage`（按原始条数推进 + 主题分片 + 公开检索窗口 9999）；`searchOrcidRecords` 保留为记录视图（供按 orcid 反查邮箱与既有测试助手）。
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CrossrefDataSource.kt` — 目录主题种子改走 `query.bibliographic`，人工关键词仍走 `query`。
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` — CORE 页封装接缝 + `WINDOW_LIMIT` 记录与常量；ORCID 循环改用 `searchOrcidPage` 并按返回游标推进（整页无邮箱不再终止/停住）。
- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalog.kt` — 新增 ORCID 主题种子与 `orcidSeedKeywords`，更新 CORE 主题词注释（接线事实）。
- `src/test/kotlin/.../CoreDataSourceTest.kt` — 重写为请求边界断言（offset 推进、续跑、窗口边界、末页、legacy scrollId 不采信、括号 OR/人工关键词/通配）。
- `src/test/kotlin/.../OrcidDataSourceTest.kt` — 重写为分页边界断言（rawCount 推进、主题分片轮换、窗口、跳过、人工关键词优先、记录视图）。
- `src/test/kotlin/.../CrossrefDataSourceTest.kt` — 主题种子断言改 `query.bibliographic`，并断言人工关键词不叠加种子。
- `src/test/kotlin/.../SubjectScopeCatalogTest.kt` — 新增 CORE/ORCID 主题词逐字锚定与分支覆盖断言。
- `src/test/kotlin/.../ExpertDiscoveryServiceTest.kt` — ORCID stub 迁移到分页入口（新增本地 `stubOrcid` 双入口助手），新增 CORE/ORCID 服务级持久化与请求边界用例。

### Deviations

- **brief 与仓库现状的差异（非冲突，已按计划落地）**：brief 称 `nonPersistableCursorSources` 仍排除 CORE，但 c2 的实现已把它改为 `emptySet()`（空集合并把 key 重构为 `SOURCE:v2:<hash>`）。因此本子计划无需再改该集合：CORE 的持久化由 c2 的通用检查点路径承载，c4 只把 CORE 的游标换成稳定的 offset 分片编码。
- **ORCID 保留记录视图入口**：`searchOrcidRecords` 未删除，因为生产路径 `ExpertDiscoveryService.tryGetEmailFromOrcid`（按 orcid 反查邮箱）与既有未授权 Java 测试助手 `DiscoveryMockHelper` 依赖它；发现循环改用 `searchOrcidPage`。可在 authorized 文件内删除该入口会破坏未授权文件编译，故按「分页入口 + 记录视图」双入口落地（计划只要求新增封装，未要求删除旧入口）。
- **CORE 人工关键词保留 AND 语义**：计划片段 `"(" + terms.joinToString(" OR ") + ")…"` 用于目录主题种子（落实为六个研发类别 OR）；人工多关键词沿用改动前的 AND 拼接（V-3 只要求「不被覆盖」），避免悄悄放宽人工检索。
- **ORCID 也加了窗口上限**：计划只对 CORE 明确 9000。为满足 I-4「各源分页不得无限递增 offset」，ORCID 采用其公开文档给出的检索窗口（`start` 0..9999，共 10000 条；Member API 不受此限，注释内标注出处），到界停该分片并切下一主题分片。
- **CORE 分片身份含 topic 但当前恒为 0**：计划片段的数据契约是 `CoreCursor(topic, year, offset)`，而 I-3 要求六个研发类别在同一查询里以显式括号 OR 合并，故 CORE 目前只有一个「主题组」（topic=0），分片实际按年轮换；topic 字段与轮换分支保留为契约的一部分（注释已说明）。
- **c3 既有 Crossref 用例改写**：`searchPapers uses the catalogue topic queries when no keyword is given` 按 c4 Task 2（种子改走 `query.bibliographic`）改写为 `…query bibliographic…`，并新增「人工关键词不叠加种子」断言；无其他 c3 行为改动。
- 未触碰：`docs/plans/fast/**`（含 ledger.md，仍保持工作区未提交修改）、未授权文件、已应用迁移、`discovery_source_cursor` 表结构（CORE/ORCID 游标复用 c2 的 `cursor_value` envelope）。

### Freshness

- Plan identity rechecked: YES（执行结束时重算，仍为 `59b8b131e5a4f091256f56daede6ba45c50713291cdb6adb1fd905a7dbf2e271`，与开始时一致）
- Worktree identity rechecked: YES（提交前以 `--expect-root/--expect-branch/--expect-git-dir` 校验通过；提交后 HEAD=985f1dd 且为 `fast/2026-09-21-discovery-enrichment-master` 祖先）
- Reported commits reachable from target branch: YES
- Required commands run this invocation: YES（最终状态后重跑；此后无源码改动）
- Historical evidence used only as baseline: YES

### Remaining Blocker

- None

### Next Action

- READY_FOR_VERIFICATION → run `verify-p`
