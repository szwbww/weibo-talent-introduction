## Light Verification: LIGHT_PASS_WITH_NOTES

- Child: 03 — 无机构证据的新发现首发拦截；plan `docs/plans/2026-09-28/discovered-institution-repair-03-outreach.md`，identity `commit:c01c86cdce3cb5747457d364241f83c535c3961d`（本验证器实测 HEAD 版本 sha256 `985969e0f44b7ea9cf324de5ebb145bf03df6c96c2ceadb61aae775ecd4c67fd`，与 A2 版本零差异）
- Boundary: `c30c954761b199467c7d904a50b177808f54ddce..70e6144065335beee72dbd22a84e4bb975a68928`
  - 实现提交 `70e6144`（`feat(fast-p): implement 03`，11 文件，+656/-99，无 `docs/plans/**` 内容，无新增文件）；边界内另有 A2 计划修订 `c01c86c`（docs-only，计划变更清单 +2 行）、`f34b4b3`（pause + A2 记录）、`89acd6f`（02 验证记录），后两者均 docs-only，非本 child 产品改动
- Verifier: Verify03（light / 四门）；工作区 HEAD = `70e6144065335beee72dbd22a84e4bb975a68928`，分支 `fast/2026-09-28-discovered-institution-repair-00-master`；`git status --porcelain src/` 为空（工作区源码与待验提交一致）；三条必需命令均为本次 fresh 运行，未复用实现者数字

| Gate | 结果 | 结论要点 |
|---|---|---|
| 1 Authorized scope | PASS | 变更产品/测试文件恰 11 个，全部命中 brief 授权表（10 计划行 + 2 A2 行）中的 11 行；第 10 行 `OperatorStatusWriteSeamGuardTest.kt` 为条件授权且未触发（合法未触碰）；无新建文件、无白名单外文件、无 `docs/plans/**` 改动 |
| 2 Plan and invariants | PASS | I-1 / I-2 / I-3 均有直接代码 + 测试证据；唯一最终谓词被 预估 / ES 取页 / NEW 重试 / 旧首发 四条路径共用；证据校验复用 02 唯一验签函数（`DiscoveryIdentity.kt` 零改动）；非新发现行为不变 |
| 3 Required commands | PASS | 三条命令 fresh 全绿：262/0/0/0（定向）、4262/0/0/13（全量）+ Node 1202 pass、`git diff --check` 无输出；与基线 4253/0/0/13 差 +9 = 新增用例数 |
| 4 Downstream interfaces | PASS | 本 child 为末位、无下游；上游契约满足：复用 02 的 `validInstitutionEvidence`（未复刻第二份验签）、消费 `ExpertProfile.institutionEvidence/filterResult` 可空投影、不改非新发现行为 |

### Gate 1 — Authorized scope

- `git diff --name-status c30c954..70e6144 -- src/` 恰 11 个 `M`（无 `A`/`D`）：
  `BatchExecutionModels.kt`、`InitialOutreachService.kt`、`ManualInitialOutreachService.kt`、`CandidateEligibilityService.kt`、`ExpertSearchService.kt`、`ManualInitialOutreachServiceTest.kt`、`InitialOutreachServiceTest.kt`、`ExpertSearchServiceTest.kt`、`CandidateEligibilityServiceTest.kt`、`BatchSendTaskRuntimeIntegrationTest.kt`（A2 行 12）、`MailOpenTrackingPersistenceTest.kt`（A2 行 11）。
- 授权表行 10 `OperatorStatusWriteSeamGuardTest.kt` 未改动且仍绿（定向 1/0/0/0、全量 1/0/0/0）：钉死行 `ExpertSearchService.kt:499` 位于本次编辑点（`:1174+`）之前，行号未平移 —— 条件授权「若移行仅改行号」未触发，无多余改动。
- A2 两份文件仅做测试侧 stale-seam 适配：`MailOpenTrackingPersistenceTest.kt` 新增 `scrollExpertsFiltered` stub（沿用同一 `expert` fixture），`:231-233` 三条断言逐字未改；`BatchSendTaskRuntimeIntegrationTest.kt` 反射目标改为 `countEsTargets(RecipientScope, LocalDateTime, Function0)`（`:751+`，取 `Pair.first`），`assertEquals(5, …)`（`:285`）逐字未改，另按层补 scroll stub（见 RECORD_ONLY 3）。
- `git show --name-only 70e6144 | grep -c docs/` = 0：实现提交未混入 `docs/plans/**` 报告。

### Gate 2 — Plan and invariants

- **I-1（唯一最终谓词 + 触发条件）**：`RecipientScope.isDiscoveryOutreach`（`BatchExecutionModels.kt:198`）= `DiscoveryIdentity.isDiscovery` ∪ `tags ∋ "待确认"`，未改 `DiscoveryIdentity.isDiscovery` 既有语义（该文件在本边界零改动）；`matchesDiscoveryOutreach`（`:210-217`）依次要求 `DiscoveryIdentity.allowed` → `institution` 非空 → `country` 非空且 `toRegion != REGION_OTHER` → `filterResult == "PASSED"` → `DiscoveryIdentity.validInstitutionEvidence`。四类路径均收口到它：`matchesExpert`（`:151`，服务 NEW 重试与 `buildRetryableTargets:1400`）、`matchesEsTarget`（`:157`，服务 ES 取页 `ManualInitialOutreachService.kt:600`/预估 `:1675`/发前兜底 `:699`）、`InitialOutreachService` 建联系人前门禁（`:62`）与取页过滤（`:181`）。阻断者不建联系人、不选号、不发信：`ManualInitialOutreachService.kt:697` 门禁先于 `selectAccount`（`:1322/1324`）与 `expertContactRepository.save`（`:867`）；`InitialOutreachService.kt:62` 先于 `:82` 的 save 与选号。
- **证据校验复用 02**：`grep -rn validInstitutionEvidence src/main` 仅 `BatchExecutionModels.kt:216` 一个调用方，实现为 02 的 `DiscoveryIdentity.validInstitutionEvidence`（只读已存字段重算 + 来源 ID 一致性；冲突拒签/失效由 02 的 `ExpertDiscoveryServiceTest.kt:6755+` 直接断言）。未新增第二份验签或 token 格式解析。
- **I-2（地区不把缺值当 Other）**：`matchesEsTarget` 对新发现要求 `regions.isEmpty() || CountryContinentMapping.toRegion(country) in regions`；`toRegion(null/blank|未映射) = REGION_OTHER`（`CountryContinentMapping.kt:254-260`），且 `matchesDiscoveryOutreach` 已先行拒绝空/未映射国家 —— 故无地区限制与仅选 `Other` 两种任务下都不放行；非新发现不收紧（`isDiscoveryOutreach` 为假即 `return true`）。`CandidateEligibilityService.nationalityOf`（`:75`）仅对新发现只读明确 `nationality`，旧非发现保留 `nationality ?: country`。
- **I-3（预估—执行同一谓词 / 分页）**：旧 1 参粗筛计数 `countEsTargets(scope)` 已删除；新 `countEsTargets(scope, now, shouldStop)`（`:1658-1684`）走 `scrollExpertsFiltered` + `batch.filter { matchesEsTarget }` + `filterKnownProfiles`，不读粗筛命中数；执行侧 `filterPage`（`:600`）与发前门禁（`:699`）用同一谓词；`buildFilteredRetryableTargets`（`:1338`）→ `buildRetryableTargets` → `matchesExpert` 同时服务预估（`:503`/`:565`）与执行（`:115`）。ES 页持续推进：`OutreachTargetIterator`（本边界零改动）整页被过滤时 `esOffset += page.size` 继续取页；旧首发 `fetchSendableCandidates`（`InitialOutreachService.kt:169-184`）显式 `from` 循环、短页/越过 `totalHits` 才耗尽、`take(size)`，`ExpertSearchService.searchExpertsByTypesWithEmail(from = 0)` 默认行为逐字不变。
- **测试证据（直接、非声明式）**：`ManualInitialOutreachServiceTest` 新增 5 例（`retry scope keeps legacy profiles and blocks discovery without institution evidence`、`countBySnapshot counts only discovery profiles with verified institution evidence`、`countBySnapshot excludes NEW retry contacts whose discovery profile lacks evidence`、`discovery country rules keep blank and unmapped countries out of every region including Other`、`run skips discovery profiles without evidence and keeps paging for eligible candidates`）+ 3 条与新不变量冲突的旧期望改写（`preview … same final predicate in both modes`、`runBulkOutreach blocks discovery …`、上条 retry）；`InitialOutreachServiceTest` +2（`blocks discovery … before contact creation`、`pages past discovery without evidence and sends the next page`）；`CandidateEligibilityServiceTest` +1、`ExpertSearchServiceTest` +1（`sends explicit from offset and defaults to zero`）。fixture 的 token 由 02 唯一签发函数产出（`signedDiscoveryExpert`），故测试走的是同一份验签口径。
- **非新发现不变**：`matchesEsTarget`/`matchesDiscoveryOutreach` 对非新发现短路放行（谓词对非新发现是 no-op），材料催办 `buildMaterialReminderSnapshot` 与邮件模板正文在本边界零改动；`non-discovery profiles keep the pre-change ES sieve without institution evidence (I-1 regression)` 直接断言。

### Gate 3 — Required commands（本次 fresh）

| # | Command | exit | 本次实测计数 |
|---|---|---:|---|
| a | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest,InitialOutreachServiceTest,ExpertSearchServiceTest,CandidateEligibilityServiceTest,OperatorStatusWriteSeamGuardTest` | 0 | `Tests run: 262, Failures: 0, Errors: 0, Skipped: 0`（ManualInitialOutreachServiceTest 158、InitialOutreachServiceTest 20、ExpertSearchServiceTest 75、CandidateEligibilityServiceTest 8、OperatorStatusWriteSeamGuardTest 1），`BUILD SUCCESS` |
| b | `JAVA_HOME=… mvn test` | 0 | surefire `Tests run: 4262, Failures: 0, Errors: 0, Skipped: 13`，`[ERROR]` 行 0 条；Node 套件 `tests 1202 / pass 1202 / fail 0`；`BUILD SUCCESS`（04:43 min） |
| c | `git diff --check` | 0 | 无输出（另附 `git diff --check c30c954..70e6144` 亦 exit 0） |

- 与基线对比：seed `children/01/baseline.md` = 4220/0/0/13；child 02 后态 = 4253/0/0/13；本次 = **4262/0/0/13 = 02 后态 + 9**，与新增用例数完全一致（ManualInitialOutreachServiceTest +5、InitialOutreachServiceTest +2、ExpertSearchServiceTest +1、CandidateEligibilityServiceTest +1），无新增失败、无 skipped 漂移；Node 1202 不变。全量运行逐类：`ManualInitialOutreachServiceTest 187`、`InitialOutreachServiceTest 20`、`ExpertSearchServiceTest 75`、`CandidateEligibilityServiceTest 8`、`OperatorStatusWriteSeamGuardTest 1`。基线上无预置红，故本次无「基线已红」需归类项。
- 口径说明（解释 158 vs brief 的 182）：该测试类含 5 个 `@org.junit.jupiter.api.Nested` 内嵌类；`-Dtest=<类名>` 只跑外层类（158 = 基线外层 153 + 5），全量运行把内嵌用例计入同类（187 = 基线 182 + 5）。两条口径自洽，非计数异常（见 RECORD_ONLY 4）。

### Gate 4 — Downstream interfaces

- 本 child 为末位、无下游消费者。其上游契约逐条核对：
  - **复用 02 唯一验签**：是（`BatchExecutionModels.kt:216` 调用 `DiscoveryIdentity.validInstitutionEvidence`；`DiscoveryIdentity.kt` 本边界零改动；无第二份 token 解析/重算）。
  - **消费可空投影**：是（`ExpertProfile.institutionEvidence: String?`/`filterResult: String?`，`ExpertProfile.kt:48/53`；`validInstitutionEvidence(null) = false`，`filterResult != "PASSED"` 亦阻断，旧文档缺键绝不默认合格）。
  - **不改非新发现行为**：是（谓词对非新发现短路；材料催办、模板正文、类型白名单与发送语义零改动；全量 4262/0/0/13 无回归）。

### AUTO_FIX

- none（四门均无被证实违规项；无「计划唯一确定修正 + 全部变更文件已授权」的必要条件成立的反例）

### RECORD_ONLY

1. **新增兜底原因码 `DISCOVERY_EVIDENCE_MISSING`**（`BatchExecutionModels.kt:261` 常量 + `:286` label，发前门禁 `ManualInitialOutreachService.kt:700-702`）：计划变更清单该文件项仅写「唯一最终谓词、重试地区」。该码为纯附加常量，是计划「用固定测试快照断言阻断人数与原因」所需的原因码（复用 `EXPERT_NOT_SENDABLE` 会误标语义）；无既有断言依赖 `LABELS` 集合（全量绿可证）。实现者已在 `children/03/execution.md` 偏差 1 披露。
2. **取消预扫描的 `result.total` 口径变化**：预估统一走最终筛选后 `shouldStop` 生效，「发送前即取消」时 `total` 由基线的粗筛数（2）变为 0（`ManualInitialOutreachService.kt:1649/1656`）；`finalStatus=CANCELLED`、`wasCancelled=true` 不变，且与该分支既有注释「A cancelled prescan has no complete target count; do not report its partial estimate.」及验证开启路径的既有行为一致。对应 1 条旧期望改写（`ManualInitialOutreachServiceTest.kt:879`，另加 `finalStatus` 断言）；实现者偏差 3 已披露。
3. **A2 行 12 的字面范围**：计划仅写「反射目标改为新签名 + 断言不变」，实现另在同文件按层补 2 个 `scrollExpertsFiltered` stub（`BatchSendTaskRuntimeIntegrationTest.kt:267-283`）——新 seam 下每层必须交回 3/2 条记录，断言 `assertEquals(5, …)` 才能保持逐字不变；属授权文件内的 stale-seam 适配，非断言放宽，已在 `execution.md` 的 A2 补充说明中披露。
4. **测试计数口径（防后续误判）**：brief/`children/01/baseline.md` 的 `ManualInitialOutreachServiceTest 182` 是全量运行口径（外层 + `@Nested`），定向 `-Dtest` 只跑外层类故为 158；两者均为正数差 +5，与「实现回归」无关。ledger 记录 03 计数建议注明所用口径。
5. **`CandidateEligibilityService`（expert 模块）新增 `campaign.domain.RecipientScope` 依赖**：为复用唯一「新发现」判定而引入的跨模块 import（单 Maven 模块，无构建影响，无重复实现）。

### Required Action
- COMPLETE_CHILD
