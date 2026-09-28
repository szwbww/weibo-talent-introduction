# Child 03 — Execution Report

## 结论：PLAN_CONFLICT

授权范围内的工作已全部实现，且必需命令 1（5 个授权测试类）**全绿**（262/0/0/0）。但全量 `mvn test`
因**两份不在 Authorized Files 清单内的测试**仍钉住被本计划强制替换的旧「粗筛计数」预估 seam 而失败：

- `MailOpenTrackingPersistenceTest`（`src/test/kotlin/com/weibo/talentintroduction/mail/service/`）
- `BatchSendTaskRuntimeIntegrationTest`（`src/test/kotlin/com/weibo/talentintroduction/campaign/service/`）

这两处失败**不是**实现缺陷，而是计划授权清单的遗漏（计划要求 `countBySnapshot` 与执行前估算
「**改用现有 scroll 批量最终筛选**」，必然删除 1 参 `countEsTargets(RecipientScope): Int` 粗筛计数路径）。
按 brief「需要白名单外文件 → PLAN_CONFLICT」，本 child 停在 PLAN_CONFLICT，**未创建实现提交**，
全部改动保留在工作区（同 child 02 epoch 1 的处置方式）。

需要的最小修订（A2）：把上述两个测试文件加入 child 03 授权清单，只做测试侧 seam 适配（见「最小修订请求」）。
修订后复跑必需命令 2 与 3、再提交 `feat(fast-p): implement 03` 即可，无需改动任何生产语义。

---

## 身份

| 项 | 值 |
|---|---|
| 批准计划 | `docs/plans/2026-09-28/discovered-institution-repair-03-outreach.md` |
| Plan SHA-256 | `a51f4b89ebd565d634087499138eba514ee6eb2f5a3b125f1f8cebfd1e4e71f8` |
| Execution ID | `…/docs/plans/2026-09-28/discovered-institution-repair-03-outreach.md@a51f4b89…e71f8` |
| Execution epoch | NEW（此前无同身份执行记录；`children/03/fix-log.md` 的 “epoch 1 …” 为旧模板文本，本 epoch 未消费 fix round） |
| Executor | Implement03 |
| Target worktree | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master` |
| Target branch | `fast/2026-09-28-discovered-institution-repair-00-master` |
| Worktree ID | `<root>@fast/2026-09-28-discovered-institution-repair-00-master@<common>/.git/worktrees/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master` |
| child_base_sha (= 执行前 HEAD) | `c30c954761b199467c7d904a50b177808f54ddce` |
| 实现提交 | **N/A（PLAN_CONFLICT，未提交）** |
| 实现边界 | working tree（10 个授权文件） |

## 任务状态

| 需求 | 状态 | 文件 | 证据 |
|---|---|---|---|
| I-1 新发现首发判定（唯一最终谓词） | IMPLEMENTED | `BatchExecutionModels.kt` | `matchesDiscoveryOutreach` :209、`isDiscoveryOutreach` :198、`DISCOVERY_PENDING_TAG="待确认"` :189；`matchesExpert` 末尾追加 :155；ES 侧谓词 `matchesEsTarget` :166（新发现按已证实机构所在地判地区） |
| I-1 三证齐备判定复用 02 唯一验签函数 | IMPLEMENTED | 同上 | 只调用 `DiscoveryIdentity.allowed` / `DiscoveryIdentity.validInstitutionEvidence`，未复刻第二份验签；`institution` 非空、`country` 可映射（`toRegion != REGION_OTHER`）、`filterResult == "PASSED"` 四项独立校验 |
| I-1 ES 新目标拦截 | IMPLEMENTED | `ManualInitialOutreachService.kt` | `filterPage` :600（取页后先过 `matchesEsTarget` 再叠加历史不可达过滤）；发送前兜底 :697-711（`DISCOVERY_EVIDENCE_MISSING`） |
| I-1 MySQL NEW 重试拦截 | IMPLEMENTED | 同上 | 重试构造走 `scope.matchesExpert`（已含新门禁），与 ES 页同谓词 |
| I-1 旧首发拦截 | IMPLEMENTED | `InitialOutreachService.kt` | 取目标 :177 后逐条过谓词；建联系人前再判 :62 |
| I-2 新发现按机构所在地判地区 | IMPLEMENTED | `BatchExecutionModels.kt` / `ManualInitialOutreachService.kt` | `matchesEsTarget` :166（空/未映射国家被门禁拦截；已证国家按 `regions` 判，禁止用 nationality 推断）；不用 `nationality` 推断本人国籍 |
| I-2 CandidateEligibilityService 国籍语义 | IMPLEMENTED | `CandidateEligibilityService.kt` | `nationalityOf` :75（新发现只读明确 `nationality`，旧非发现保留 `nationality ?: country`），调用点 :41 |
| I-3 预估—执行共用最终筛选 | IMPLEMENTED | `ManualInitialOutreachService.kt` | `countEsTargets(scope, now, shouldStop)` :1658 走 `scrollExpertsFiltered` + `matchesEsTarget` :1675；删除 1 参粗筛计数（同一 seam 由 `countBySnapshot` 与执行前估算共用） |
| I-3 页 offset 针对粗筛持续推进 | IMPLEMENTED | 同上 / `ExpertSearchService.kt` | 整页被过滤时 `OutreachTargetIterator` 继续 `esOffset += page.size`（既有行为，现由新谓词触发）；`fetchEsPage` 仍按粗筛 offset 取页 |
| I-3 旧首发继续分页找足数量 | IMPLEMENTED | `InitialOutreachService.kt` / `ExpertSearchService.kt` | `fetchSendableCandidates` :169（按 `size` 页循环、被过滤页继续推进 offset、短页/越过 totalHits 才算耗尽）；`searchExpertsByTypesWithEmail` 新增 `from: Int = 0`（`ExpertSearchService.kt` :1183 签名、:1191 请求体 `"from"`），默认调用行为逐字不变 |
| 非新发现行为不变 | IMPLEMENTED | `BatchExecutionModels.kt` | `matchesDiscoveryOutreach` 对非新发现立即放行；`matchesEsTarget` 对非新发现不收紧地区（保留 ES country OR nationality 粗筛）；材料催办路径未改（不消费 `matchesExpert`） |
| 未改变 `DiscoveryIdentity` 既有语义 | IMPLEMENTED | — | `DiscoveryIdentity.kt` 未在授权清单内且未被修改 |

## 改前红证据

### 阶段 1：行为断言（实现前，仅使用既有 API）

```sh
JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn -q test \
  -Dtest=ManualInitialOutreachServiceTest,InitialOutreachServiceTest,CandidateEligibilityServiceTest
```

exit 1；合计 `Tests run: 185, Failures: 18, Errors: 2`：

- `ManualInitialOutreachServiceTest`：158 run / 17 failures / 1 error —— 含新增断言
  `countBySnapshot counts only discovery profiles with verified institution evidence (I-1 I-3)`、
  `countBySnapshot excludes NEW retry contacts whose discovery profile lacks evidence (I-1 I-3)`、
  `discovery country rules keep blank and unmapped countries out of every region including Other (I-2)`、
  `run skips discovery profiles without evidence and keeps paging for eligible candidates (I-1 I-3)`、
  `retry scope keeps legacy profiles and blocks discovery without institution evidence (I-1 I-3)`，
  以及 11 处「预估 filter 同源」断言（预估仍打 `countExperts`、不调 `scrollExpertsFiltered`）。
  典型报文：`Wanted but not invoked: expertSearchService.scrollExpertsFiltered(…)`
  `-> at ManualInitialOutreachService.countEsTargets(ManualInitialOutreachService.kt:1633)`。
- `InitialOutreachServiceTest`：19 run / 1 error ——
  `sendInitialBatch blocks discovery without institution evidence before contact creation (I-1 I-3)` 红（旧首发把无证据新发现发出去，
  经未 stub 的选号路径抛 NullPointer）。
- `CandidateEligibilityServiceTest`：8 run / 1 failure ——
  `discovery profiles judge nationality only from the explicit nationality field (I-2):99 expected: <false> but was: <true>`
  （旧口径把 `country=China` 当本人国籍）。

### 阶段 2：分页断言（受控实验：临时回退两处分页 seam 后运行，随即完整还原）

临时回退内容仅两处：`fetchSendableCandidates` 的页循环 → 单页取回；`searchExpertsByTypesWithEmail`
请求体的 `"from" to from,` 删除。运行：

```sh
JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=InitialOutreachServiceTest,ExpertSearchServiceTest
```

exit 1；红证据：

- `sendInitialBatch pages past discovery without evidence and sends the next page (I-1 I-3)` →
  `expected: <1> but was: <0>`（单页取回：唯一命中者是被过滤的无证据新发现 → 一封未发）
- `sendInitialBatch blocks discovery without institution evidence before contact creation (I-1 I-3)` →
  `expected: <0> but was: <2>`（无页级过滤时 `candidates` 计成 2；`sent` 仍为 0，证明建联系人前的兜底门禁独立生效）
- `searchExpertsByTypesWithEmail sends explicit from offset and defaults to zero (I-3)` → error（请求体缺 `from`）

还原证据：`grep -n '"from" to from,' ExpertSearchService.kt` 命中 340/681/1191；
`grep -n "while (eligible.size < size)" InitialOutreachService.kt` → 176。随后必需命令 1 全绿。

## 命令结果（本 epoch 新鲜运行）

| # | 命令 | exit | 计数 |
|---|---|---:|---|
| 1 | `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest,InitialOutreachServiceTest,ExpertSearchServiceTest,CandidateEligibilityServiceTest,OperatorStatusWriteSeamGuardTest` | 0 | `Tests run: 262, Failures: 0, Errors: 0, Skipped: 0`（ManualInitialOutreachServiceTest 158、InitialOutreachServiceTest 20、ExpertSearchServiceTest 75、CandidateEligibilityServiceTest 8、OperatorStatusWriteSeamGuardTest 1） |
| 2 | `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test` | 1 | `Tests run: 4262, Failures: 1, Errors: 1, Skipped: 13` |
| 3 | `git diff --check` | 0 | 无输出（无空白错误） |
| 附 | `node --test src/test/js/*.test.js` | 0 | `tests 1202 / pass 1202 / fail 0`（与基线一致） |

### 命令 2 的两处失败（均在本 child 授权清单之外）

```
[ERROR] Failures:
[ERROR]   MailOpenTrackingPersistenceTest.manual outreach batch entry persists tracking id through real success helper:231 expected: <1> but was: <0>
[ERROR] Errors:
[ERROR]   BatchSendTaskRuntimeIntegrationTest.ES count path queries every funnel level in scope:268->invokeCountEsTargets:737 » NoSuchMethod
```

- `BatchSendTaskRuntimeIntegrationTest`（`:737`）用反射取 1 参
  `ManualInitialOutreachService.countEsTargets(RecipientScope): Int` —— 该粗筛计数路径被计划
  「预估改用 scroll 批量最终筛选」强制删除，故 `NoSuchMethod`。
- `MailOpenTrackingPersistenceTest`（`:231`）只 stub 了旧预估 seam（`countExperts` +
  `searchExpertsFiltered`），未 stub 新的 `scrollExpertsFiltered` handler，于是本次预估为 0 →
  执行前估算为 0 → 早退（`sent=0`）。该用例主体是 open tracking id 落库，不是预估口径。

两处基线（seed 4220/0/0/13、child 02 post-state 4253/0/0/13）均绿，故为本次改动的直接后果。

### 与基线对比

| 项 | 基线（child 02 post-state） | 本 epoch |
|---|---|---|
| surefire 总数 | 4253 / 0 / 0 / 13 | 4262 / 1 / 1 / 13 |
| 新增用例 | — | +9（ManualInitialOutreachServiceTest +5、InitialOutreachServiceTest +2、ExpertSearchServiceTest +1、CandidateEligibilityServiceTest +1） |
| Node | 1202 pass | 1202 pass |

注：brief 的「ManualInitialOutreachServiceTest 182」是**外层类 + `@Nested` 内嵌类**的合计口径；
本仓库 surefire 在 `-Dtest=…` 与全量运行中都只执行并统计外层类（158；`@Nested` 用例不在
surefire XML 中）。故按 surefire 口径比较：外类 153（基线）→ 158（本 epoch，+5）。
InitialOutreachServiceTest 18→20、ExpertSearchServiceTest 74→75、CandidateEligibilityServiceTest 7→8
与 brief 基线逐类对齐。

## 变更文件（10/10，均在授权清单内）

| 文件 | 改动 |
|---|---|
| `src/main/kotlin/…/campaign/domain/BatchExecutionModels.kt` | 唯一新发现最终谓词（`matchesDiscoveryOutreach`/`isDiscoveryOutreach`/`DISCOVERY_PENDING_TAG`）、ES 侧谓词 `matchesEsTarget`、`matchesExpert` 收口、兜底原因码 `DISCOVERY_EVIDENCE_MISSING` |
| `src/main/kotlin/…/campaign/service/ManualInitialOutreachService.kt` | ES 取页最终谓词、预估改 scroll+最终筛选（删除旧 1 参粗筛计数）、发送前兜底门禁 |
| `src/main/kotlin/…/campaign/service/InitialOutreachService.kt` | 旧首发分页取目标 `fetchSendableCandidates` + 建联系人前门禁 |
| `src/main/kotlin/…/expert/service/ExpertSearchService.kt` | `searchExpertsByTypesWithEmail(from)` 分页（默认 0，请求体 `from`） |
| `src/main/kotlin/…/expert/service/CandidateEligibilityService.kt` | 新发现国籍只读明确 `nationality` |
| `src/test/kotlin/…/campaign/service/ManualInitialOutreachServiceTest.kt` | 新增 5 例（预估/重试/地区 Other/取页持续/回归）、改写 2 例旧期望、11 处预估 filter 断言改打 scroll、helper 同时铺 scroll 与取页 |
| `src/test/kotlin/…/campaign/service/InitialOutreachServiceTest.kt` | 新增 3 例（无证据拦截、三证齐备放行、分页跨页）、旧期望改写（原「discovery without proof 照发」）、matcher 补 `from` 位 |
| `src/test/kotlin/…/expert/service/ExpertSearchServiceTest.kt` | 新增 `from` 分页断言 |
| `src/test/kotlin/…/expert/service/CandidateEligibilityServiceTest.kt` | 新增国籍语义用例 |
| `src/test/kotlin/…/campaign/OperatorStatusWriteSeamGuardTest.kt` | **未改动** —— `ExpertSearchService.kt` 的行号钉（:499）位于本次编辑点（:1174+）之前，行号未平移，守卫用例原样通过 |

## 偏差

1. **新增兜底原因码** `BatchOutcomeReasonCodes.DISCOVERY_EVIDENCE_MISSING`（label「新发现机构证据不足」）。
   计划的变更清单只写「唯一最终谓词、重试地区」，未点名原因码；但 I-1/I-3 要求"发送前再检查"，
   被拦截者需要一个面向运营的原因（复用 `EXPERT_NOT_SENDABLE` 会把机构证据问题标成研发类型问题）。
   纯附加、仅在该兜底路径出现。
2. **发送前兜底门禁在当前实现下不可由公开 API 触发**（取页/重试已按同一谓词过滤），属与既有
   `matchesExpertType` 同型的防御性复核（"查询/缓存/未来重构错误可能绕过 ES 侧"）；其判定语义由
   `matchesEsTarget` 层面的用例覆盖。
3. **取消时的 total 口径变化**：预估改为扫描后，取消会中止扫描，故取消发生在发送前时
   `result.total` 由基线的粗筛数（2）变为 0，与既有代码注释「A cancelled prescan has no complete
   target count; do not report its partial estimate.」一致；`finalStatus=CANCELLED`、`wasCancelled=true`
   不变。对应改写 `runBulkOutreach terminates when cancelled` 的期望值（1 行）+ 说明注释。
4. **改写 3 条与新不变量直接冲突的旧期望**（不改写就只能红）：
   `retry scope ignores identity proof…`、`runBulkOutreach sends discovery without proof…`、
   `sendInitialBatch uses configured eligibility for discovery without proof`。三者都在断言
   「无机构证据的新发现照发」，正是 I-1 要禁止的行为；改写后分别覆盖 I-1 门禁、拦截不发送、三证齐备放行。
   `preview … disabled filter uses count fast path` 同理改写为「两种模式都走同一最终筛选」。
5. 未修改 `docs/plans/**` 计划正文、未改 `DiscoveryIdentity.kt`、未改任何迁移、未跑 formatter/linter。

## 最小修订请求（A2）

把下列两个**测试文件**加入 child 03 授权清单，只做测试侧 seam 适配（不改任何生产语义、不放宽断言）：

1. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailOpenTrackingPersistenceTest.kt`
   （`:195-199` 附近）：为该用例的 `ExpertSearchService` mock 补一个预估用的
   `scrollExpertsFiltered(eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList()), eqValue(500), anyValue({ _: List<ExpertProfile> -> true }))`
   handler，把同一 `expert` fixture 交给 handler（用例主体仍是 open tracking id 落库）。
2. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt`
   （`invokeCountEsTargets`，`:735-740`；用例 `:261`）：反射目标由已删除的
   `countEsTargets(RecipientScope): Int` 改为新 seam
   `countEsTargets(RecipientScope, LocalDateTime, () -> Boolean)`（以 scroll handler 覆盖每层），
   断言主体「scope 内每个 funnel level 都被查询」保持不变。

预计修订后：命令 2 → `4262 / 0 / 0 / 13`，命令 3 仍为 exit 0，随后即可提交 `feat(fast-p): implement 03`。

### 备选（不推荐，供人类选择）

保留「预估走粗筛计数」会违反 I-3，已排除。另一条不用修订授权清单的路线是把**预估改成与执行取页同一条
`countExperts` + `searchExpertsFiltered` 分页扫描**（`MailOpenTrackingPersistenceTest` 与
`BatchSendTaskRuntimeIntegrationTest` 的既有 stub 恰好覆盖这条 seam，两测试可保持不动）。代价是：
① 偏离计划明文的「改用现有 scroll 批量最终筛选」；② 必须重写 4 条钉住既有「历史不可达预扫描（scroll）」
语气的授权用例（含 `cancellation during historical prescan stops later pages and layers even when all are excluded`），
改动面明显大于上面的测试侧 seam 适配。故本 agent 未擅自采用。

## 当前工作区状态（供复现/续跑）

- 10 个授权文件已改（生产 5 + 测试 4 改 + 1 未改），**未提交**；`docs/plans/**` 除本报告与既有未跟踪
  的 `children/03/` 外无改动；`ledger.md` 的改动为本次派发前既有（非本 agent 所为）。
- 复现命令 1：见上表；复现命令 2 的两处失败：见「命令 2 的两处失败」。
- 临时回退实验所用的备份在 `/tmp/03-ios.kt.bak`、`/tmp/03-ess.kt.bak`（工作区外，未入库）。

## Freshness

- Plan identity rechecked：YES（`a51f4b89…e71f8`，执行前后一致）
- Worktree identity rechecked：YES（分支/root/git-dir 与派发一致，无跨 worktree 提交）
- Reported commits reachable from target branch：N/A（本次无实现提交）
- Required commands run this invocation：YES（命令 1/2/3 均为本 epoch 新鲜运行）
- Historical evidence used only as baseline：YES（基线只用于逐类计数对比）
