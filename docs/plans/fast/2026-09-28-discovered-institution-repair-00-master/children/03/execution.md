# Child 03 — Execution Report

## 结论：READY_FOR_VERIFICATION（epoch 2）

无机构证据的新发现首发拦截已在授权范围内完整实现：唯一最终谓词覆盖 预估 / ES 新目标 / MySQL NEW 重试 /
旧首发四条路径，I-1/I-2/I-3 全部有测试证据，三条必需命令全绿（`mvn test` → 4262/0/0/13）。

- Epoch 1：PLAN_CONFLICT（两份白名单外测试钉住被替换的旧预估 seam）→ 记录见「Epoch 1 记录」。
- Epoch 2：A2（`c01c86cdce3cb5747457d364241f83c535c3961d`）把该两份测试加入授权清单后完成，
  epoch 1 的全部未提交工作原样保留，只新增测试侧 seam 适配。

---

## 身份

| 项 | 值 |
|---|---|
| 批准计划 | `docs/plans/2026-09-28/discovered-institution-repair-03-outreach.md` |
| Plan SHA-256（epoch 2） | `985969e0f44b7ea9cf324de5ebb145bf03df6c96c2ceadb61aae775ecd4c67fd` |
| Plan SHA-256（epoch 1） | `a51f4b89ebd565d634087499138eba514ee6eb2f5a3b125f1f8cebfd1e4e71f8` |
| Execution ID | `…/docs/plans/2026-09-28/discovered-institution-repair-03-outreach.md@985969e0…c67fd` |
| Execution epoch | 2（RESUME：epoch 1 为 NEW，同一计划路径、A2 修订后内容变更；授权由人类经 Main 转达，A2 提交 `c01c86c`） |
| Executor | Implement03 |
| Target worktree | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master` |
| Target branch | `fast/2026-09-28-discovered-institution-repair-00-master` |
| child_base_sha | `c30c954761b199467c7d904a50b177808f54ddce`（child 02 Code head） |
| Epoch 2 起始 HEAD | `f34b4b3b2d6e3c51889ba80dd26f12ee489cb4e8`（pause 03 + A2 记录；含 epoch 1 报告） |
| 实现提交 | 见文末「提交」；报告本身不入实现提交 |
| 实现边界 | `c30c954..<实现提交>`（11 个源码/测试文件） |

## 任务状态

| 需求 | 状态 | 文件 | 证据 |
|---|---|---|---|
| I-1 新发现首发判定（唯一最终谓词） | IMPLEMENTED | `BatchExecutionModels.kt` | `matchesDiscoveryOutreach` :209：`DiscoveryIdentity.allowed` → `institution` 非空 → `country` 可映射（`toRegion != REGION_OTHER`）→ `filterResult == "PASSED"` → `DiscoveryIdentity.validInstitutionEvidence`（复用 02 唯一验签函数，未复刻第二份）；触发条件 `isDiscoveryOutreach` :198 = `DiscoveryIdentity.isDiscovery` ∪ `tags ∋ "待确认"`（:189），未改 `DiscoveryIdentity` 既有语义；`matchesExpert` 收口 :155 |
| I-1 ES 新目标拦截 | IMPLEMENTED | `ManualInitialOutreachService.kt` | 取页后过滤 :600（`filterPage` 先过 `scope.matchesEsTarget` 再叠加历史不可达过滤）；发送前兜底 :697-711 |
| I-1 MySQL NEW 重试拦截 | IMPLEMENTED | 同上 | 重试目标构造走 `scope.matchesExpert`（已含新门禁），与 ES 页同一 I-1/I-2 谓词 |
| I-1 旧首发拦截 | IMPLEMENTED | `InitialOutreachService.kt` | 目标构造 :169-184 逐条过谓词；建联系人前再判 :62-64 |
| I-2 新发现/待确认按机构所在地判地区 | IMPLEMENTED | `BatchExecutionModels.kt` | `matchesEsTarget` :166：新发现必须 `country` 非空且可映射（空/未映射 ≠ Other），并按 `toRegion(country)` 落在 `regions` 内；非新发现不收紧（保留 ES country OR nationality 粗筛） |
| I-2 国籍不冒充 | IMPLEMENTED | `CandidateEligibilityService.kt` | `nationalityOf` :75（新发现只读明确 `nationality`，旧非发现保留 `nationality ?: country`），调用点 :41 |
| I-3 预估—执行共用最终筛选 | IMPLEMENTED | `ManualInitialOutreachService.kt` | `countEsTargets(scope, now, shouldStop)` :1658-1668：每层 `scrollExpertsFiltered` + `matchesEsTarget` :1675，绝不把粗筛命中数当可发送数；删除 1 参粗筛计数（`countBySnapshot` :503 与执行前估算 :566 共用同一 seam） |
| I-3 页 offset 持续推进 | IMPLEMENTED | `ManualInitialOutreachService.kt` / `OutreachTargetIterator.kt` | 整页被过滤时迭代器 `esOffset += page.size` 继续取页（既有行为，由新谓词触发；测试 `run skips discovery profiles … keeps paging` 固定） |
| I-3 旧首发继续分页找足数量 | IMPLEMENTED | `InitialOutreachService.kt` / `ExpertSearchService.kt` | `fetchSendableCandidates` :169-184（页循环、offset 推进、短页/越过 totalHits 才耗尽、`take(size)`）；`searchExpertsByTypesWithEmail(from)` :1183/:1191，默认 0 行为逐字不变（测试 `searchExpertsByTypesWithEmail sends explicit from offset…`） |
| 非新发现行为不变 | IMPLEMENTED | `BatchExecutionModels.kt` | `matchesDiscoveryOutreach` 对非新发现立即放行；材料催办不消费 `matchesExpert`（未改）；测试 `non-discovery profiles keep the pre-change ES sieve…`、`discovery country rules…`（含旧档案 Other 粗筛语义保留） |
| 未改计划外文件 / 未改 `DiscoveryIdentity` | IMPLEMENTED | — | 变更文件共 11 个（见下），全部在 A2 后的授权清单内；`DiscoveryIdentity.kt` 未改 |

## 改前红证据（epoch 1，实现前）

### 阶段 1：行为断言

```sh
JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn -q test \
  -Dtest=ManualInitialOutreachServiceTest,InitialOutreachServiceTest,CandidateEligibilityServiceTest
```

exit 1；`Tests run: 185, Failures: 18, Errors: 2`：

- `ManualInitialOutreachServiceTest` 158 run / 17 F / 1 E：新增 5 例（预估只计三证齐备、NEW 重试同判、
  地区 Other/未映射、取页持续、retry scope 门禁）+ 11 处「预估 filter 同源」断言全红，典型：
  `Wanted but not invoked: expertSearchService.scrollExpertsFiltered(…)`
  `-> at ManualInitialOutreachService.countEsTargets(ManualInitialOutreachService.kt:1633)`。
- `InitialOutreachServiceTest` 19 run / 1 E：`sendInitialBatch blocks discovery without institution evidence before contact creation (I-1 I-3)` 红
  （旧首发把无证据新发现发出去，经未 stub 的选号路径抛 NullPointer）。
- `CandidateEligibilityServiceTest` 8 run / 1 F：
  `discovery profiles judge nationality only from the explicit nationality field (I-2):99 expected: <false> but was: <true>`
  （旧口径把 `country=China` 当本人国籍）。

### 阶段 2：分页断言（受控实验，临时回退两处分页 seam 后运行，随即完整还原）

```sh
JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test -Dtest=InitialOutreachServiceTest,ExpertSearchServiceTest
```

exit 1；红证据：`sendInitialBatch pages past discovery without evidence…` → `expected: <1> but was: <0>`；
`sendInitialBatch blocks discovery…` → `expected: <0> but was: <2>`（无页级过滤时 candidates=2，sent 仍 0，
证明建联系人前兜底门禁独立生效）；`searchExpertsByTypesWithEmail sends explicit from offset…` → 报错（请求体缺 `from`）。
还原校验：`"from" to from,` 命中 340/681/1191，`while (eligible.size < size)` → 176；随后必需命令 (a) 全绿。

## Epoch 1 记录：PLAN_CONFLICT（保留）

授权范围内实现完成、必需命令 1 全绿，但全量 `mvn test` 出现 2 处回归，全部来自**当时白名单外**的测试：

```
[ERROR] Failures:
[ERROR]   MailOpenTrackingPersistenceTest.manual outreach batch entry persists tracking id through real success helper:231 expected: <1> but was: <0>
[ERROR] Errors:
[ERROR]   BatchSendTaskRuntimeIntegrationTest.ES count path queries every funnel level in scope:268->invokeCountEsTargets:737 » NoSuchMethod
[ERROR] Tests run: 4262, Failures: 1, Errors: 1, Skipped: 13
```

- 前者只 stub 旧预估 seam（`countExperts` + `searchExpertsFiltered`），未 stub 新的 `scrollExpertsFiltered`
  handler → 预估为 0 → 早退（用例主体是 open tracking id 落库）。
- 后者反射已删除的 1 参 `countEsTargets(RecipientScope): Int` 粗筛计数路径。
- 两处基线均绿（seed 4220/0/0/13、child 02 post-state 4253/0/0/13），系本次改动的直接后果；
  按 brief「需要白名单外文件 → PLAN_CONFLICT」停在 PLAN_CONFLICT、未提交，全部工作保留在工作区。
- 处置：A2（人类批准，`c01c86c`）把两份测试加入 child 03 授权清单，仅做测试侧 seam 适配。

## Epoch 2：A2 适配（仅测试侧，断言与生产语义均未变）

| 文件 | 改动 | 证据 |
|---|---|---|
| `src/test/kotlin/…/mail/service/MailOpenTrackingPersistenceTest.kt` | 新增 `scrollExpertsFiltered` stub，把同一 `expert` fixture 交给 handler；`countExperts`/`searchExpertsFiltered` stub 与三条断言原样保留 | 新增 :201-207；断言 :231-233 未改；`mvn test -Dtest=MailOpenTrackingPersistenceTest,BatchSendTaskRuntimeIntegrationTest` → 6/0/0/0 |
| `src/test/kotlin/…/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` | 反射目标改为新签名 `countEsTargets(RecipientScope, LocalDateTime, () -> Boolean)`，返回值取 `Pair.first`；用例内按层补 `scrollExpertsFiltered` stub（CANDIDATE 3 条 + APPLICATION 2 条），断言 `assertEquals(5, …)` 逐字未改 | 反射 :736-749、`Function0` 导入 :59、stub :267-283、断言 :285 未改；同一次运行 22/0/0/0 |

对 A2 行的补充说明：计划行只点名「反射目标改为新签名 + 断言不变」，但新 seam 下该断言要仍为 5，
必须让每层的 scroll handler 交回 3/2 条记录（否则扫描结果恒为 0）；故同文件内补了 2 个按层 stub，
未删除、未改写任何既有 stub 或断言。

## 最终命令结果（本 epoch 新鲜运行）

| # | 命令 | exit | 计数 |
|---|---|---:|---|
| a | `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest,InitialOutreachServiceTest,ExpertSearchServiceTest,CandidateEligibilityServiceTest,OperatorStatusWriteSeamGuardTest` | 0 | `Tests run: 262, Failures: 0, Errors: 0, Skipped: 0`（ManualInitialOutreachServiceTest 158、InitialOutreachServiceTest 20、ExpertSearchServiceTest 75、CandidateEligibilityServiceTest 8、OperatorStatusWriteSeamGuardTest 1） |
| b | `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test` | 0 | `Tests run: 4262, Failures: 0, Errors: 0, Skipped: 13`，BUILD SUCCESS |
| c | `git diff --check` | 0 | 无输出 |
| 附 | A2 两份文件定向运行 | 0 | MailOpenTrackingPersistenceTest 6/0/0/0、BatchSendTaskRuntimeIntegrationTest 22/0/0/0 |
| 附 | `node --test src/test/js/*.test.js` | 0 | `tests 1202 / pass 1202 / fail 0`（`mvn test` 亦包含该 exec-plugin 步骤且通过） |

### 与基线对比

| 项 | 基线（child 02 post-state） | 本 epoch |
|---|---|---|
| surefire | 4253 / 0 / 0 / 13 | 4262 / 0 / 0 / 13（+9 = 新增用例） |
| Node | 1202 pass | 1202 pass |

新增用例 9 例：ManualInitialOutreachServiceTest +5、InitialOutreachServiceTest +2、ExpertSearchServiceTest +1、
CandidateEligibilityServiceTest +1。注：brief 的「ManualInitialOutreachServiceTest 182」是外层类 + `@Nested`
内嵌类的合计口径；本仓库 surefire 只执行并统计外层类（`-Dtest=…` 与全量运行皆 158，`@Nested` 用例不出现在
surefire XML 中），基线同口径为 153 → 158（+5）。其余三类与 brief 基线逐类对齐（18→20、74→75、7→8）。

## 变更文件（11 个，全部在 A2 后授权清单内）

| 文件 | 改动 |
|---|---|
| `src/main/kotlin/…/campaign/domain/BatchExecutionModels.kt` | 唯一新发现最终谓词 + ES 侧谓词 + `matchesExpert` 收口 + 兜底原因码（+58） |
| `src/main/kotlin/…/campaign/service/ManualInitialOutreachService.kt` | ES 取页最终谓词、预估改 scroll+最终筛选（删旧粗筛计数）、发送前兜底（+56/-32） |
| `src/main/kotlin/…/campaign/service/InitialOutreachService.kt` | 旧首发分页取目标 + 建联系人前门禁（+34/-1） |
| `src/main/kotlin/…/expert/service/ExpertSearchService.kt` | `searchExpertsByTypesWithEmail(from)` 分页（+5/-1） |
| `src/main/kotlin/…/expert/service/CandidateEligibilityService.kt` | 新发现国籍只读明确 `nationality`（+10/-1） |
| `src/test/kotlin/…/campaign/service/ManualInitialOutreachServiceTest.kt` | 新增 5 例、改写 4 条旧期望、11 处预估同源断言改打 scroll、helper 同时铺 scroll 与取页 |
| `src/test/kotlin/…/campaign/service/InitialOutreachServiceTest.kt` | 新增 3 例（无证据拦截、三证齐备放行、跨页取目标）、旧期望改写、matcher 补 `from` 位 |
| `src/test/kotlin/…/expert/service/CandidateEligibilityServiceTest.kt` | 新增国籍语义用例 |
| `src/test/kotlin/…/expert/service/ExpertSearchServiceTest.kt` | 新增 `from` 分页断言 |
| `src/test/kotlin/…/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` | A2：反射改新签名 + 按层 scroll stub |
| `src/test/kotlin/…/mail/service/MailOpenTrackingPersistenceTest.kt` | A2：补 `scrollExpertsFiltered` stub |

**未改动**：`src/test/kotlin/…/campaign/OperatorStatusWriteSeamGuardTest.kt` —— `ExpertSearchService.kt` 的行号钉
（:499 `operatorStatus = source.nullableText`）位于本次编辑点（:1174+）之前，行号未平移，用例原样通过（262 与 4262 两次运行均绿）；
故本次变更为 11 个文件而非 12（该测试无需任何编辑，不制造无意义改动）。

## 偏差（每条附 file:line 证据）

1. **新增兜底原因码**：`BatchOutcomeReasonCodes.DISCOVERY_EVIDENCE_MISSING`（常量 `BatchExecutionModels.kt:261`、
   label「新发现机构证据不足」`BatchExecutionModels.kt:286`），用于发送前兜底门禁 `ManualInitialOutreachService.kt:700-702`。
   计划的变更清单只写「唯一最终谓词、重试地区」；复用 `EXPERT_NOT_SENDABLE` 会把机构证据问题误标为研发类型问题，
   故新增纯附加码（无既有断言依赖该 label 集合）。
2. **发送前兜底门禁在现有实现下不可由公开 API 触发**：取页 `ManualInitialOutreachService.kt:600` 与重试
   `:1376` 已按同一谓词过滤，该门禁（`:697-711`）与既有 `matchesExpertType`（`:683`）同型的防御性复核；
   其判定语义由谓词层用例覆盖（`ManualInitialOutreachServiceTest.kt:242`、`:310`）。
3. **取消时的 total 口径变化**：预估改为扫描后 `shouldStop` 会中止扫描（`ManualInitialOutreachService.kt:1649`,
   `:1656`），故"发送前即取消"的 `result.total` 由基线的粗筛数（2）变为 0，与既有代码注释
   「A cancelled prescan has no complete target count; do not report its partial estimate.」一致；
   `finalStatus=CANCELLED`、`wasCancelled=true` 不变。对应期望改写 1 行 + 说明注释：
   `ManualInitialOutreachServiceTest.kt:879`（断言 :893-896）。
4. **改写 4 条与新不变量直接冲突的旧期望**（不改写即持续红）：
   `ManualInitialOutreachServiceTest.kt:199`（原「retry scope ignores identity proof」）、
   `:416`（原「disabled filter uses count fast path」）、`:774`（原「runBulkOutreach sends discovery without proof」）、
   `InitialOutreachServiceTest.kt:123`（原「uses configured eligibility for discovery without proof」）。
   四者都在断言「无机构证据的新发现照发 / 预估读粗筛计数」，正是 I-1/I-3 禁止的行为；改写后分别覆盖
   门禁判定、两模式同筛选、拦截不发送与不建联系人、三证齐备放行（`:145`）。
5. 未修改 `docs/plans/**` 下的计划正文（A2 由人类提交）、未改 `DiscoveryIdentity.kt`、未改迁移、
   未跑 formatter/linter、未 push/merge/rebase/squash/amend/reset。

## 复现步骤

```sh
cd /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest,InitialOutreachServiceTest,ExpertSearchServiceTest,CandidateEligibilityServiceTest,OperatorStatusWriteSeamGuardTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
git diff --check
```

## 提交

- `70e6144` — `feat(fast-p): implement 03`（11 files changed, 656 insertions(+), 99 deletions(-)；无 `docs/plans/fast/**` 内容；
  为 `fast/2026-09-28-discovered-institution-repair-00-master` 的 HEAD，`git merge-base --is-ancestor HEAD <branch>` = YES）
- 报告 `children/03/execution.md` 与 `children/03/{brief,fix-log}.md` 保持未提交/未跟踪，由控制方单独记录。

## Freshness

- Plan identity rechecked：YES（epoch 2 = `985969e0…c67fd`；执行前后一致，A2 后未再变更）
- Worktree identity rechecked：YES（`--expect-root/--expect-branch` 匹配，无跨 worktree 提交）
- Reported commits reachable from target branch：YES（实现提交为 target branch HEAD）
- Required commands run this invocation：YES（a/b/c 均为 epoch 2 新鲜运行）
- Historical evidence used only as baseline：YES（epoch 1 红证据与基线计数仅作历史记录/对比）
