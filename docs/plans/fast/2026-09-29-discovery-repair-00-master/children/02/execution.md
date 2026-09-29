# Child 02 执行报告 — ORCID 多作者禁止任取一人补全

## Execution Result: READY_FOR_VERIFICATION

| 项 | 值 |
|---|---|
| Plan | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master/docs/plans/2026-09-29/discovery-repair-02-orcid-enrichment.md` |
| Plan SHA-256 | `9f13ad0454973f5ed31737d8388e3320aa0e6cf6fd551002f9bd7a038e53c82b`（与 brief 记录的批准字节一致，执行前后各核一次） |
| Brief | `docs/plans/fast/2026-09-29-discovery-repair-00-master/children/02/brief.md`，sha256 `6861c43b297c67384aacb5c580e719f93a8baddded5cfe1f72a7f68835e8c774` |
| Execution ID | `.../docs/plans/2026-09-29/discovery-repair-02-orcid-enrichment.md@9f13ad04…` |
| Execution epoch | NEW |
| Approval basis | fast-p master run `2026-09-29-discovery-repair-00-master` 的 child 02 brief（批准 child plan 同一 seed commit `70f55065`） |
| Executor | `Child02Implementer`（task 子代理） |
| Target worktree | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master` |
| Target branch | `fast/2026-09-29-discovery-repair-00-master` |
| 起始 HEAD / child_base_sha | `66f9c1c1b6e3b6fcb53d8dd7d9051aea3211032c` / `93308663e593177a7d6a7631dc4f90aea6f98c80`（后者为 01 的 Code head；两者之间只有 01 的 fast-p 证据提交） |
| Pre-execution code SHA | `93308663e593177a7d6a7631dc4f90aea6f98c80` |
| Post-execution code SHA | `bd0cb377987a800104c393af11254eb760519b68` |
| Evidence HEAD | N/A（本报告按 brief 由 controller 单独提交；报告不在实现提交内） |
| Implementation boundary | `93308663e593177a7d6a7631dc4f90aea6f98c80..bd0cb377987a800104c393af11254eb760519b68`（1 个提交，7 个授权文件） |
| Commit | `bd0cb377987a800104c393af11254eb760519b68` — `feat(fast-p): implement 02`（+body），`7 files changed, 724 insertions(+), 18 deletions(-)` |

未 push / merge / rebase / amend；未触碰其他 worktree；未修改任何 `docs/plans/**`（工作区里 `children/02/baseline.md`、`children/02/brief.md` 的改动是进入本子任务前就存在的 controller 改动，未被本人暂存或提交）。

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1（I-1、I-2）OpenAlexDataSource：单条/批量共用同一解析判定、per_page=200 + meta.count 校验、规范化入参、同 ID 重复不算两人、同 ID 冲突/无有效作者 ID → ApiError、可空包装返回 null、A ID 分支语义不变、429/503/额度原样上抛 | IMPLEMENTED | `OpenAlexDataSource.kt` | 新增 `resolveOrcid` / `OrcidLookup` / `canonicalOrcid` / `ORCID_PAGE_SIZE=200` / `ORCID_RESPONSE_INCOMPLETE`；`EnrichmentOutcome.AmbiguousIdentity`；`OpenAlexDataSourceTest` 新增 6 个用例（排列×6、重复入参、唯一详情、截断/缺 count/坏结构/坏节点、同 ID 重复与冲突、429） |
| T-2（I-3）ExpertDiscoveryService：结果贯穿、`enrichIdentityGroups` 分支、历史补全汇总、`classifyBatchOutcome` 补分支；跳过 `updateExpertAcademicFields` 与 `revalidationService` | IMPLEMENTED | `ExpertDiscoveryService.kt` | `EnrichmentOutcome.AmbiguousIdentity -> ProfileEnrichmentOutcome.AmbiguousIdentity`（整组全部文档）；人工路径 `failed++ / AUTHOR_IDENTITY_AMBIGUOUS`（不计 enriched）；批次 `BatchOutcomeBucket.UNMATCHED`；`ProfileEnrichmentOutcome.AmbiguousIdentity` |
| T-2（I-3）ExpertAcademicEnrichmentJobService：classify/resultJson 复用 UNMATCHED + 固定原因码 + 固定 outcome，租约/重开/attempts 不变 | IMPLEMENTED | `ExpertAcademicEnrichmentJobService.kt` | `unmatched(attempts, now, "AUTHOR_IDENTITY_AMBIGUOUS")`、`{"outcome":"AMBIGUOUS_IDENTITY"}`、`REASON_AUTHOR_IDENTITY_AMBIGUOUS`；`ExpertAcademicEnrichmentJobServiceTest` 新增 2 个用例（终态/原因/outcome/attempts=4 不增长；过期 token 不提交、不写任何列） |
| T-3（I-1–I-3）fixture + 三测试文件覆盖 | IMPLEMENTED | `src/test/resources/discovery/orcid-collision-20260929.json`、`OpenAlexDataSourceTest.kt`、`ExpertDiscoveryServiceTest.kt`、`ExpertAcademicEnrichmentJobServiceTest.kt` | fixture 由真实摘录构造（注明来源与构造值）；新增测试 6 + 3 + 2 = 11 个，含 Nobuhiro 两档案的三层写入 0 / 再核验 0 / 标题请求 0 与 worker 两条 UNMATCHED |

## Commands

| Command（cwd = worktree root，`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`） | Result | Evidence |
|---|---|---|
| `mvn -Dtest=OpenAlexDataSourceTest,ExpertDiscoveryServiceTest,ExpertAcademicEnrichmentJobServiceTest test`（brief 规定命令，最终实现状态后重新执行，无附加参数） | PASS | 进程退出码 `0`（`PIPESTATUS_MVN=0`），`BUILD SUCCESS`，Total time 03:32 min（含 `mvn test` 阶段由 exec 插件绑定的 1203 个前端用例：`pass 1203 / fail 0`） |
| `mvn -o -Dtest=OpenAlexDataSourceTest,ExpertDiscoveryServiceTest,ExpertAcademicEnrichmentJobServiceTest test`（第一次迭代运行，仅用于改写两处断言） | PASS | 退出码 0，`BUILD SUCCESS`；正式证据以上一行为准 |

`target/surefire-reports` 逐类计数（最终一次运行，报告 mtime 2026-09-29 14:37:05）：

| 测试类 | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|
| `com.weibo.talentintroduction.discovery.service.OpenAlexDataSourceTest` | 85 | 0 | 0 | 0 |
| `com.weibo.talentintroduction.discovery.service.ExpertDiscoveryServiceTest` | 176 | 0 | 0 | 0 |
| `com.weibo.talentintroduction.discovery.service.ExpertAcademicEnrichmentJobServiceTest` | 6 | 0 | 0 | 0 |

新增用例数（`git show HEAD:<file> | grep -c '@Test'` → working）：`OpenAlexDataSourceTest` 79 → 85（+6）、`ExpertDiscoveryServiceTest` 173 → 176（+3）、`ExpertAcademicEnrichmentJobServiceTest` 4 → 6（+2）。

未运行项目级/全包测试，未运行 `mvn clean package`（按 brief 限定）。

## Changed Files（7，全部在授权清单内）

| # | 文件 | 目的 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | 唯一性/完整性判定、`AmbiguousIdentity`、单条与批量共用 `resolveOrcid` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 新结果类型贯穿、整组跳过写入/再核验、人工原因码、批次 unmatched |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentJobService.kt` | `UNMATCHED` + `AUTHOR_IDENTITY_AMBIGUOUS` + `outcome=AMBIGUOUS_IDENTITY` |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | 传输输入回归 + 新用例；旧 ORCID fixture 补真实的 `id`/`meta.count` |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 按文档跳过（三写 0/再核验 0/标题 0）、人工原因、worker 计数 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentJobServiceTest.kt` | 终态/原因/outcome/attempts 与租约语义 |
| 7 | `src/test/resources/discovery/orcid-collision-20260929.json` | 由真实摘录构造的测试响应（3 个碰撞作者 + 唯一作者 + 空响应） |

## Invariant Evidence

### I-1 先分组判身份，再读取学术事实

- 实现：`resolveOrcid(response, orcid)` 是单条（`enrichAuthorByOrcidWithReason`）与批量（`batchEnrichByOrcids`）唯一的身份判定入口；按 `normalizeOpenAlexAuthorId` 规范化去重后 0/1/>1 三分类，唯一才 `enrichmentOutcome(node)`（单条则走既有详情接口）。
- 证据：`every ordering of the colliding authors is an identity ambiguity and requests no titles` 对 3 个真实作者 ID 的 6 种排列各断言 `AmbiguousIdentity`，并在每次排列中改写 `cited_by_count`（首个节点恒为最高引用）证明引用量不参与选择；同名（`Nobuhiro Tsuji` 出现两次）不改变结果。
- 证据：`duplicate nodes with one author id count once while conflicting facts are an ApiError` 覆盖「相同 ID 重复」。

### I-2 不完整响应不能证明唯一

- 实现：`ORCID_PAGE_SIZE = 200` 用于单条与批量；`resolveOrcid` 要求 `results` 为数组且 `meta.count` 为非负整数并等于返回数组长度，否则 `ApiError("ORCID_RESPONSE_INCOMPLETE")`。不做翻页、不新增限额框架。
- 证据：`only a complete listing may decide unique or not-found, and a missing author id is not not-found` 覆盖 ①`count=3, results=1`（恰好只见一个作者）②`count=201, results=200` ③缺 `meta.count` ④`results` 非数组 ⑤关联节点无 `id` ⑥`count=0, results=[]`（唯一合法的 NotFound）；①②③④ 均 `ORCID_RESPONSE_INCOMPLETE` 且无任何 Success/NotFound（②额外断言 `values.all { it is ApiError }`）。
- 证据：`batchEnrichByOrcids reports ... per_page=200` 断言实际 URL 逐字为 `https://api.openalex.org/authors?filter=orcid:0000-0002-2132-1327&per_page=200`（旧实现是 `per_page=<入参个数>` 且 last-wins）。

### I-3 身份歧义为未匹配，不写事实

- 实现：`EnrichmentOutcome.AmbiguousIdentity` → `ProfileEnrichmentOutcome.AmbiguousIdentity`（该 ORCID 分组的**全部**文档）→ 不进入 `updateExpertAcademicFields` / `revalidationService` / 标题请求；人工入口 `failureReasons["AUTHOR_IDENTITY_AMBIGUOUS"]` 且不计 enriched；批次 `UNMATCHED`；job 层 `status=UNMATCHED`、`last_error=AUTHOR_IDENTITY_AMBIGUOUS`、`result_json={"outcome":"AMBIGUOUS_IDENTITY"}`、`attempts` 原样。
- 证据：`two archives sharing a colliding ORCID skip every academic write, revalidation and title request` —— 两个共享 ORCID 的邮箱档案（`DOC-A`/`DOC-B`）均歧义，且 `/_update/` 0 次、`/works?`（标题）0 次、`/authors/A…`（详情）0 次、`revalidateDiscovery`/`revalidateEnrichedRaw` 0 次，只发 1 次列表查询。
- 证据：`manual backfill records AUTHOR_IDENTITY_AMBIGUOUS without touching academic fields` —— `enriched=0`、`failed=1`、`AUTHOR_IDENTITY_AMBIGUOUS=1`、无 `ORCID_NOT_IN_OPENALEX`、无 `/_update/`。
- 证据：`worker marks both colliding-ORCID archives UNMATCHED and writes no academic fact` —— `succeeded=0`、`unmatched=2`、`pending=0`、`failed=0`，`getEnrichmentStats().autoEnrichment.unmatched=2 / succeeded=0`，交给 `complete` 的两个结果都是 `AmbiguousIdentity`（即 07 落 UNMATCHED），且无写入/再核验。
- 证据：`identity ambiguity reuses UNMATCHED with the fixed reason and never burns attempts` —— 行 `attempts=4`（下一次故障必然 FAILED）时 `status=UNMATCHED`、`attempts` 仍为 4、`last_error=AUTHOR_IDENTITY_AMBIGUOUS`、`result_json={"outcome":"AMBIGUOUS_IDENTITY"}`。
- 证据：`identity ambiguity still requires the current lease token` —— 过期 token 返回 false 且 `completeWithToken` 从未被调用（不写任何列）。

### 保留行为（A-2 六类案例）

| 保留项 | 覆盖用例（既有，未弱化） |
|---|---|
| 唯一作者三层存在更新 3 层 / RAW-only 只更新 1 层 | `ExpertDiscoveryServiceTest` 既有三层与 RAW-only 用例（`stubLayerPresence` / `stubRawLayerOnly`）；本次新增 `a unique ORCID still reads the author detail endpoint` 证明唯一身份仍走既有详情接口 |
| 可信 A ID 优先、不走 ORCID 猜人 | `enrichProfiles prefers the trusted author id…`、`discovery enrichment never treats the old business key…`（既有，未改） |
| 429 / 额度延期 / 网络失败与租约 | `ORCID batch keeps the existing rate limit and budget failure semantics`、既有 `discovery defers on the reserved share…`、`额度延期只把任务记为待补而不算失败`、`complete refuses a stale token…` |
| 邮箱/机构原有规则、一人多邮箱分别对应真实 ES 文档 | 未改动（`parseAuthorBase` / `last_known_institutions` / `trustedOrcid` / `trustedOpenAlexAuthorId` 逐字保留） |

### EnrichmentOutcome / ProfileEnrichmentOutcome 全使用点收据（无隐藏 `else` 把歧义当成功）

```
$ grep -rn "EnrichmentOutcome" --include=*.kt src/main | grep -v ProfileEnrichmentOutcome | wc -l
59
$ grep -rn "EnrichmentOutcome" --include=*.kt src/main | grep -v ProfileEnrichmentOutcome | cut -d: -f1 | sort | uniq -c
      7 src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt
     52 src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt
$ grep -rn "ProfileEnrichmentOutcome" --include=*.kt src/main | cut -d: -f1 | sort | uniq -c
     18 src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentJobService.kt
     48 src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt
$ grep -rn "EnrichmentOutcome" --include=*.kt src | grep -v "src/main" | cut -d: -f1 | sort | uniq -c
     13 src/test/kotlin/com/weibo/talentintroduction/discovery/repository/ExpertAcademicEnrichmentJobRepositoryIT.kt
      3 src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentJobServiceTest.kt
     68 src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt
     38 src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt
```

`EnrichmentOutcome` 在 `src/main` 只被两个文件引用：`OpenAlexDataSource.kt`（定义与传输层内部）、`ExpertDiscoveryService.kt`（消费侧）。消费侧全部 7 处：

```
$ grep -n "EnrichmentOutcome" src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt | grep -v ProfileEnrichmentOutcome
3195:        query: (List<String>, RequestKind) -> Map<String, EnrichmentOutcome>
3206:                when (val found = lookup[identity] ?: EnrichmentOutcome.NotFound) {
3207:                    is EnrichmentOutcome.Success -> {
3229:                    is EnrichmentOutcome.NotFound ->
3231:                    is EnrichmentOutcome.AmbiguousIdentity ->
3237:                    is EnrichmentOutcome.ApiError ->
3241:                    is EnrichmentOutcome.RateLimited ->
```

`when (val found = …)`（3206）**没有** `else`：5 个分支穷尽 sealed 类型（编译期保证），歧义显式落 `ProfileEnrichmentOutcome.AmbiguousIdentity`，不进写入分支。

全部 outcome 相关 `when` 与 `else` 收据：

```
$ grep -c "else ->" src/main/…/ExpertAcademicEnrichmentJobService.kt
0                     # classify(116) 与 resultJson(183) 全穷尽，无 else
$ grep -n "when (outcome)\|when (val found\|when (val lookup\|when (classifyBatchOutcome" …/{ExpertDiscoveryService,OpenAlexDataSource,ExpertAcademicEnrichmentJobService}.kt
ExpertAcademicEnrichmentJobService.kt:116:  when (outcome) {            # classify：AmbiguousIdentity → unmatched(...)
ExpertAcademicEnrichmentJobService.kt:183:  resultJson … when (outcome) # AmbiguousIdentity → {"outcome":"AMBIGUOUS_IDENTITY"}
ExpertDiscoveryService.kt:2534:  val layers = when (outcome) # Success/Partial → layers；else → null（仅用于「是否是写成功类结果」）
ExpertDiscoveryService.kt:2554:  when (outcome)              # 有显式 is AmbiguousIdentity -> { failed++ / AUTHOR_IDENTITY_AMBIGUOUS }
ExpertDiscoveryService.kt:2890:  when (classifyBatchOutcome(…))  # 批次桶，与 outcome 生命周期无关
ExpertDiscoveryService.kt:2963:  classifyBatchOutcome … when (outcome)  # 穷尽，无 else
ExpertDiscoveryService.kt:3206:  when (val found = …)        # 穷尽，无 else
OpenAlexDataSource.kt:453:      when (val lookup = resolveOrcid(…))   # 穷尽（Unique/NotFound/Ambiguous/Invalid），无 else
OpenAlexDataSource.kt:525:      when (val lookup = lookups.getOrPut(…))  # 穷尽，无 else
OpenAlexDataSource.kt:433-436:  fun enrichAuthorByOrcid(…): AuthorEnrichment? = when (outcome) {
                                   is EnrichmentOutcome.Success -> outcome.data
                                   else -> null        # 歧义 → null（绝不伪装成某个作者）
                               }
```

唯一两处 `else` 已逐一核对：①`OpenAlexDataSource.enrichAuthorByOrcid` 的 `else -> null` 只把非成功结果映射为 null（歧义 → null）；②`ExpertDiscoveryService:2534` 的 `else -> null` 只回答「该结果是否携带 layers」，其后的 `when (outcome)`（2554）为歧义显式记账，两处都不会把歧义当成成功。

## Deviations

1. **完整性/歧义判定的适用范围**：I-2 的 `Applies to` 是「单条/批量 ORCID 列表请求」，T-1 又要求「明确 A ID 查询分支语义不变」。故 `resolveOrcid`（校验 + 分组 + 歧义）只用于 `enrichAuthorByOrcidWithReason` 与 `batchEnrichByOrcids`；`batchEnrichByAuthorIds` 仍走原 `batchEnrichIdentities`（显式 A ID 本身就是被请求身份，非规范形状节点继续不参与匹配、缺失 = NotFound，不新增 completeness/歧义判定）。`batchEnrichIdentities` 现只服务作者 ID 路径，其 KDoc 已同步说明。
2. **新增两个固定原因码**：计划只固定了 `ApiError("ORCID_RESPONSE_INCOMPLETE")`。T-1 另要求「无有效作者 ID 的关联节点 → 该身份 ApiError」与「同 ID 节点内容冲突 → ApiError」，故补入 `ORCID_NODE_WITHOUT_AUTHOR_ID`、`ORCID_NODE_CONTENT_CONFLICT`（均为 `ApiError.message`，粒度保守：按既有 ApiError→RetryableError→5 次退避→FAILED 收口，不产生无限即时重试）。
3. **既有 ORCID 测试 fixture 补真实字段**：`OpenAlexDataSourceTest` 中若干 ORCID 批量 stub 的节点缺 `id`、单个 ORCID 查询 stub 缺 `meta`，与新规则（真实 OpenAlex 作者对象必带 `id`；列表响应必带 `meta.count`）不符，已补齐；这属于 T-3 要求的「坏节点/缺 count」覆盖面，不是放宽断言。
4. **空 ORCID 入参**：`canonicalOrcid` 为空串时 `resolveOrcid` 直接返回 NotFound（保持旧「空 filter 得不到匹配 → NotFound」的行为，且保证空串不会与「无 orcid 字段的节点」误配）。无生产调用方传入空串（`trustedOrcid` 已过滤）。
5. **第一次运行带 `-o`（离线）**：仅用于迭代修正两处新增断言；brief 规定的精确命令在最终实现状态后重新完整执行（无 `-o`）并作为唯一正式证据（退出码 0，BUILD SUCCESS）。

## Freshness

- Plan identity rechecked: YES（执行前/后 `shasum -a 256` 均为 `9f13ad04…`）
- Worktree identity rechecked: YES（worktree root/branch `fast/2026-09-29-discovery-repair-00-master`，提交 `bd0cb37` 为当前 HEAD，父提交 `66f9c1c`）
- Reported commits reachable from target branch: YES（`git log -1` = `bd0cb377…`）
- Required commands run this invocation: YES（精确命令，退出码 0）
- Historical evidence used only as baseline: YES（基线 `93308663`/`66f9c1c` 仅作对照，所有计数来自本次新生成/新写入的 surefire 报告）

## Remaining Blocker

- None.

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`（独立验证），随后由 controller 提交本报告与 fast-p 证据。
