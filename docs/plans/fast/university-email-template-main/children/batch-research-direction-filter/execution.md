# Execution Report — batch-research-direction-filter

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-university-email-template-main/docs/plans/2026-09-18/batch-research-direction-filter.md`
- Plan SHA-256: `6c4a8c1a99e57ce9af7c329dfd9c37645dab8968ef62026e3fe2616eb58d0aca` (13686 bytes)
- Execution ID: `docs/plans/2026-09-18/batch-research-direction-filter.md@6c4a8c1a99e57ce9af7c329dfd9c37645dab8968ef62026e3fe2616eb58d0aca`
- Execution epoch: `NEW` (no prior execution evidence names this identity)
- Approval basis: this invocation (fast-p child 2 dispatch, brief `docs/plans/fast/university-email-template-main/children/batch-research-direction-filter/brief.md`)
- Executor: `ResearchDirectionImplementer`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-university-email-template-main`
- Target branch: `fast/university-email-template-main`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-university-email-template-main@fast/university-email-template-main@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-university-email-template-main`
- Pre-execution code SHA (`child_base_sha`, product base): `971a21d35a0d5bdffefa4aff9dc6faf93819207d`
- Pre-execution HEAD (dispatch): `24bec04a54e7ac20598185daf81dcf5e1d23302d` (evidence commits `2bf2cd4`, `24bec04` touch only `docs/plans/fast/**`, untouched)
- Post-execution code SHA: `971a21d…` + this implementation commit (recorded below in §Commit)
- Implementation boundary: `971a21d..<commit>` — exactly the 10 authorized files
- Master plan cross-invariants: M-2 (类型与方向独立), M-3 (配置与执行一致), M-4 (不导入数据/不建任务/不发信)

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| I-1 三态持久化字段（写/读/快照/手动覆盖/旧 typed API 保值/非法值拒绝） | IMPLEMENTED | V128, `BatchSendTaskConfig.kt`, `BatchSendTaskConfigService.kt`, `BatchExecutionModels.kt`, `BatchSendControlService.kt`, `app.js`, `index.html` | §I-1 |
| I-2 ES / 内存重试 / 预估同口径 | IMPLEMENTED | `ManualInitialOutreachService.kt`, `BatchExecutionModels.kt` | §I-2 |
| I-3 方向 × 类型 × 模板门禁独立取交集 | IMPLEMENTED | `BatchExecutionModels.kt`, `ManualInitialOutreachService.kt`, `index.html`(文案) | §I-3 |
| S-1 复用既有 class、无 CSS 变更、无 inline style | IMPLEMENTED | `index.html` | §S-1 |
| 测试（授权测试文件内新增） | IMPLEMENTED | `ManualInitialOutreachServiceTest.kt` (+10), `batchSendTaskConsoleInteraction.test.js` (+7) | §Commands |
| 本地实现提交（仅 10 个授权文件） | IMPLEMENTED | — | §Commit |

## 逐文件改动

| # | 文件 | 改动 | 关键行 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V128__add_research_direction_filter_to_batch_send_task_config.sql`（新增） | `ALTER TABLE batch_send_task_config ADD COLUMN research_direction_filter VARCHAR(16) NOT NULL DEFAULT 'ANY' AFTER gate_filter_enabled`（既有行由 MySQL 回填 `'ANY'`；不建 CHECK，白名单权威在服务层） | `:6-7` |
| 2 | `campaign/domain/BatchSendTaskConfig.kt` | entity/view/create-command/update-command 各加 `researchDirectionFilter: String = ANY`；新增唯一权威 `object ResearchDirectionFilters`（`ANY/PRESENT/ABSENT` + `ALLOWED` + `ES_FIELD="researchFields"` + `normalize`/`requireAllowed`） | entity `:29-30`；view `:57-58`；create `:85-86`；update `:107-108`；object `:119-140` |
| 3 | `campaign/service/BatchSendTaskConfigService.kt` | `normalizeAndValidate` 三态白名单校验；create/update 落库；`toView` 回显；`updateLegacyConfig` 显式保留存量值；`ConfigFields`/`NormalizedConfig`/三个 `toFields()` 全链路透传（无默认值 → 编译器强制每个映射点传值） | `:82`, `:118`, `:205`, `:322`, `:349`, `:496`, `:590`, `:612`, `:633`, `:654`, `:675` |
| 4 | `campaign/domain/BatchExecutionModels.kt` | 快照字段（默认 ANY）；`RecipientScope` 字段 + `matchesExpert` 方向判定；`fromSnapshot` 归一透传；`toExecutionSnapshot` 从实体透传 | `:30`, `:75`, `:107-110`, `:173`, `:342` |
| 5 | `campaign/service/BatchSendControlService.kt` | 手动启动 `validateSnapshotFields` 独立校验三态（快照来自请求体，不经配置服务） | `:432-434` |
| 6 | `campaign/service/ManualInitialOutreachService.kt` | `buildEsFiltersForLevel`（预估/扫描/执行/材料提醒共用）追加 PRESENT / ABSENT 分支 | `:1315-1330` |
| 7 | `static/app.js` | 编辑器回显、编辑器预览快照、保存 payload、手动快照、克隆草稿、独立手动默认值、手动页回显、`readManualFormValues`、差异归一/文案/字段表/节点映射/清标记、编辑器 change 监听（→ 预估刷新） | `:15993`, `:16763`, `:16785`, `:16905`, `:16997`, `:17020`, `:17044`, `:17129`, `:17149`, `:17171-17175`, `:17209`, `:17258`, `:17291`, `:17943` |
| 8 | `static/index.html` | 配置页与手动页各一个与“学科”同骨架的三态 select（手动页保留 diff badge/original） | `:1286-1293`, `:1501-1510` |
| 9 | `test/kotlin/.../ManualInitialOutreachServiceTest.kt` | +10 用例（ES 三态、内存重试三态、预估=执行同 filter、内存判定逐 profile、I-3 交集、配置→快照→ES 全链路、create/update 落库、非法值保存拒绝、legacy 保值、手动启动 422）；`runScheduledSnapshot(researchDirectionFilter = "ANY")` 加默认参数（既有调用点行为不变） | `:4290-4667`, helper `:1639` |
| 10 | `test/js/batchSendTaskConsoleInteraction.test.js` | +7 用例 H1–H7（回显/默认、保存 payload、克隆与独立默认、手动读取、差异文案、index.html 源文本 + S-1 class 白名单 + 无 inline style、diff 节点映射）；既有 “uses one complete manual snapshot” 用例的 `values` 补 `researchDirectionFilter: "ABSENT"`（否则 deepStrictEqual 会因新增键失败） | `:2208-2443`, `:1124-1125` |

无其他文件改动：`git status --porcelain` 中仅上述 9 个已跟踪文件 + 1 个新迁移（`docs/plans/fast/**` 与 `target/` 为未提交的工作区/计划目录）。

## 逐不变量证据

### I-1 三态持久化字段

- 迁移：`V128…sql:6-7` — `VARCHAR(16) NOT NULL DEFAULT 'ANY'`；存量行由列默认值回填 `'ANY'`，无 `UPDATE` 双写。
- 唯一权威：`BatchSendTaskConfig.kt:119-140`，`ALLOWED = {ANY, PRESENT, ABSENT}`，`normalize(null/空白) = ANY`，非白名单 `require` 抛 `IllegalArgumentException`。
- 写：`BatchSendTaskConfigService.kt:82`(create)/`:118`(update) 落库；读：`:496`(toView，list/get 共用)；快照：`BatchExecutionModels.kt:342`；内存对象：`:173`。
- 旧 typed API 保值：`BatchSendTaskConfigService.kt:205` `researchDirectionFilter = existing.researchDirectionFilter`（照 `gateFilterEnabled` 的既有范式，漏写即静默重置为默认值）。
- 非法值拒绝（保存）：`BatchSendTaskConfigService.kt:322` —— 用例 `create rejects an illegal direction state and saves nothing (I-1)`（断言消息含字段名与允许集、`save` 从未调用）。
- 非法值拒绝（手动启动）：`BatchSendControlService.kt:432-434` —— 用例 `startManual rejects an illegal direction state with 422 before launching (I-1)`（422 + 消息含 `researchDirectionFilter` + `verifyNoInteractions(progressStore)`，即未占执行 token）。
- 取值链用例：`create and update persist the direction state and never reset it (I-1)`（未传值 → `ANY`；显式 `ABSENT` 落库并回显；编辑改 `PRESENT` 落库为 `PRESENT`）、`persisted config carries the direction state into the snapshot and ES filters (I-1)`（旧任务默认 `ANY` 且不加任何方向 filter；`ABSENT` 全链路保留）、`updateLegacyConfig preserves the existing direction state (I-1)`（旧 typed API 只改 cron，落库实体仍为 `ABSENT`）。
- 前端往返：JS `H1`（回显 `ABSENT`；旧任务视图无字段 → `ANY`；新建 → `ANY`）、`H2`（保存 payload = `ABSENT`）、`H3`（`deepCloneConfig` 保留 `ABSENT`、旧配置 → `ANY`、独立手动草稿默认 `ANY`）、`H4`（手动选择读出 `PRESENT`/缺省 `ANY`）、`H5`（差异检测）。

### I-2 ES、重试对象与预估同口径

- `ANY` 不加查询：`ManualInitialOutreachService.kt:1317` 的 `when` 无 `ANY` 分支；用例断言 `anyFilters` 既不含存在性也不含否定 filter，且 `size + 1` 关系成立（旧任务人群不变）。
- `PRESENT` 精确等于既有接口：`:1319` `ExpertSearchService.fieldPresenceFilter(ResearchDirectionFilters.ES_FIELD)`；用例与 `ExpertSearchService.fieldPresenceFilter("researchFields")` 逐字 `assertEquals`（未改 ES 服务、未新增字段、未重构该路径）。
- `ABSENT` 为其 `bool.must_not`：`:1320-1329`；用例 `assertEquals(mapOf("bool" to mapOf("must_not" to listOf(presence))), absentFilters[2])`。
- 内存重试同口径：`BatchExecutionModels.kt:107-110`，`null`/空串＝无、其他＝有（`isNullOrEmpty`，与 keyword 字段 `exists AND NOT term ""` 一致；纯空格串在 ES 里算“有”，内存侧同判）。用例 `matchesExpert applies the direction three-state per profile…` 逐 profile 断言 PRESENT/ABSENT/ANY 与 `[null, "", 值, " "]` 的期望矩阵。
- 预估 = 执行：两条路径共用 `resolveScope` → `RecipientScope` → `buildEsFiltersForLevel`。用例 `preview and execution use identical direction filters for the same snapshot (I-2)` 断言 `countBySnapshot` 的 `totalSendable` 与 `run(...)` 的 `total` 相等，且 `countExperts(CANDIDATE, <同一 filter 列表>)` 被调用 3 次（预估 1 + 执行计数 1 + 首页预取 1）；用例 `countBySnapshot applies direction to retryable profiles exactly like the ES presence rule (I-2)` 断言同一缺方向 retryable 在 `ABSENT` 下保留、`PRESENT` 下排除，且 ES 侧 filter 列表两种状态各自精确匹配。

### I-3 方向 × 类型 × 模板门禁独立取交集

- 三个维度都是独立 filter 项、平铺 AND：`ManualInitialOutreachService.kt:1315-1336`（方向 → 门禁字段 → `expertTypesFilter`），无任何 `should` 合并。
- `ABSENT` + 无默认值 `${primaryResearchField}` 门禁：用例 `ABSENT combined with a required researchFields template gate selects nobody (I-3)` —— `scope.gateEsFields == ["researchFields"]` 且存在性 filter 与否定 filter 同时存活（丢弃任一项都会放进缺值者），内存判定对 `researchFields=null` 与 `="AI"` 均返回 `false`；发送端门禁（子计划 1 的 `PersonalizationGateService`）未被改动、未被绕过。
- `ANY` 不额外加方向过滤：同 `I-2` 的 `ANY` 断言 + `persisted config…(I-1)` 用例中的旧任务分支。
- 类型维度独立性不变：`expertTypes`/`UNCLASSIFIED` 语义未改（`RecipientScope.matchesExpertType`、`ExpertSearchService.expertTypesFilter` 未触碰）；方向判定与类型判定是 `matchesExpert` 中两段独立 `if`，fixture 统一为 `PRODUCTION_RND` 以隔离被测维度。既有 `MATERIAL_REMINDER` 不判类型的用例（`MATERIAL_REMINDER applies no classification gate in memory (I-3-5)`）仍绿。
- 文案：`index.html:1287,1502` 字段标签为“研究方向（“无”≠研发类型“未知”）”（S-1 只允许既有 class，故说明文案落在既有 `.batch-config-field-label` 内，未新增元素/class）。

### S-1 样式契约

- 新增 DOM 只用契约所列既有 class：JS `H6` 用源文本正则提取两个 `<label class="batch-config-field">…</label>` 块，展开全部 `class="…"` 并断言差集为空（允许集 = `bsc-input/bsc-select/batch-config-field/batch-config-field-label/batch-config-diff-badge/batch-config-diff-original`），且块内 `style="` 零命中、`class="bsc-input bsc-select"` 与学科字段逐字相同、三 option 取值顺序 `ANY/PRESENT/ABSENT`。
- `CSS diff 为空`：`git diff --numstat -- src/main/resources/static/styles.css` → 0 行（见 §Commands）。
- 缓存键未动：`git diff src/main/resources/static/index.html` 的新增行中 `?v=` 零命中。
- 下拉视觉与“学科”一致：完全复用同一骨架与同一 class 组合（`.bsc-input.bsc-select` + `.batch-config-field` + `.batch-config-field-label`），未就地修改任何既有 class；普通/暗色主题均走既有 token（无新色值）。
- 手动页差异提示节点保留：JS `H6` 断言手动块含 `.batch-config-diff-badge` 与 `.batch-config-diff-original`；`H7` 断言 `computeAndRenderDiffs` 的节点映射与 `clearAllDiffMarkers` 都指向 `manualFieldResearchDirectionFilter`（K-dom-stub-tests-hide-dangling-refs：DOM stub 下 `getElementById` 恒返回元素，故 `H6` 另做 index.html 源文本存在性断言，三 id 各出现 1 次）。

## Commands（全部在最终实现状态上于本 invocation 内新鲜运行）

| # | 命令 | 结果 | 退出码 / 计数 |
|---|---|---|---|
| 1 | `node --test src/test/js/batchSendTaskConsoleInteraction.test.js` | PASS | exit 0；`tests 81 / pass 81 / fail 0`（基线 74 → +7） |
| 2 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='ManualInitialOutreachServiceTest,BatchSendTaskConfigServiceTest,BatchSendTaskRuntimeIntegrationTest' -DfailIfNoTests=false` | PASS | exit 0；`Tests run: 196, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS`（明细：ManualInitialOutreachServiceTest 110、BatchSendTaskConfigServiceTest 64、BatchSendTaskRuntimeIntegrationTest 22） |
| 3 | `git diff --check` | PASS | exit 0，无输出 |
| 4 | （同一次 Maven 运行内的 exec 插件全量 JS 套件） | PASS | `tests 1003 / pass 1003 / fail 0`（基线 996 → +7，零其他 JS 回归） |
| 5 | 作用域内补证：`mvn test -Dtest='BatchSendControlServiceTest'` | PASS | exit 0；`Tests run: 34, Failures: 0, Errors: 0`（证明 `validateSnapshotFields` 新增校验未破坏手动/自动启动路径） |
| 6 | `git diff --numstat -- src/main/resources/static/styles.css` | PASS | 0 行（S-1 CSS diff 为空） |

### 基线对照

| 指标 | 基线（`971a21d`，见 ledger 记录） | 本次 | 差 |
|---|---|---|---|
| 定向 Kotlin（3 类） | 186 tests / 0F / 0E（100+64+22） | 196 / 0F / 0E（110+64+22） | +10（全部为新增方向用例） |
| 单文件 JS | 74 pass / 0 fail | 81 pass / 0 fail | +7（H1–H7） |
| exec 插件全量 JS | 996 pass | 1003 pass / 0 fail | +7 |

基线两侧均在本会话内实测复核（`971a21d` 的 186/74 与 ledger 记录一致），非引用历史文本。

## Deviations

1. **I-3 说明文案的载体**：S-1 只允许 6 个既有 class 且要求“复制既有骨架、仅换 id 与 option”，而实现方案第 3 步要求“文案说明「研发类型未知」和「无研究方向」互不等价”。二者只能同时满足于既有 `.batch-config-field-label` 文本，故标签写作“研究方向（“无”≠研发类型“未知”）”，未新增元素、class 或 inline style。
2. **方向筛选对两种 mailType 均生效**：`buildEsFiltersForLevel` 是 `INTRODUCTION` 与 `MATERIAL_REMINDER` 的共用路径，方向三态与 `expertTypes`（INTRODUCTION 专属）不同，计划/不变量未按 mailType 收窄，故按共用路径统一生效；`MATERIAL_REMINDER` 的 legacy `BatchSendConfig` 构造路径不带该值 → 默认 `ANY`（零行为变化）。
3. **测试落点**：授权测试文件仅 2 个，故配置服务（保存白名单、legacy 保值）与手动启动校验的用例放在 `ManualInitialOutreachServiceTest.kt`（该文件已有 `updateLegacyConfig …` 先例），未新增/修改任何未授权测试文件（含 `BatchSendTaskConfigServiceTest.kt`、`BatchSendControlServiceTest.kt`，两者仅作为回归证据运行）。
4. **`worktree_identity.py` 不可用**：脚本在解析 `git worktree list` 时遇到仓库中一个既有、`prunable` 的失效登记（`/private/tmp/talent-deploy-a7d2a63`，与本工作树无关）而 `FileNotFoundError` 退出；未执行任何 worktree 清理（禁止改动无关仓库状态），改为用 `git rev-parse --show-toplevel/--abbrev-ref HEAD/--absolute-git-dir/--git-common-dir` 记录等价身份（见头部 Worktree ID），并在提交前后各核对一次分支与 HEAD。
5. **一次自伤性测试失败（已修）**：首轮运行中我新增的 `startManual…` 用例误用 `Mockito.any(TaskProgress::class.java)`（Kotlin 平台类型 NPE），其异常留下 Mockito 匹配器栈，连带污染同 JVM 的 `BatchSendTaskRuntimeIntegrationTest` 两个用例（`InvalidUseOfMatchers` / `UnfinishedVerification`）。改为 `Mockito.verifyNoInteractions(progressStore)` 后三类全绿（196/0/0），非产品缺陷。

## Residual Concerns

1. **纯空格 `researchFields`**：按 I-2 明文（“其他＝有”）与 keyword/`term ""` 语义，`" "` 记为“有”，内存侧用 `isNullOrEmpty` 与 ES 对齐；同一文件内既有模板门禁字段判定用的是 `isNullOrBlank`（子计划 1 冻结行为，未改）。两者对纯空格串口径不同，属计划明文范围内的既有差异，若未来要统一须另立计划（不属本子计划授权）。
2. **灰度数据不可验证**：验收标准里的 ES 命中人数（500 人名单、`UNKNOWN` 与 `UNCLASSIFIED` 的实际分布）在有真实候选前后无法机器验证；本报告按 M-4 不声称任何线上命中人数。人工验收 A-1/A-2/A-3 仍需在测试环境执行。
3. **旧 typed API 的读回**：`BatchSendConfig`（legacy typed 模型）不携带方向三态（计划只要求“更新不得重置”）；legacy typed API 的调用方看不到该配置项，属计划范围外（未扩改 legacy 响应模型）。

## Freshness

- Plan identity rechecked: YES（`6c4a8c1a…`，执行前后一致）
- Worktree identity rechecked: YES（分支与 git-dir 在本 invocation 内两次核对；脚本因既有失效 worktree 登记不可用，见 Deviations 4）
- Reported commits reachable from target branch: YES（见 §Commit，提交后核对 `HEAD` 与分支）
- Required commands run this invocation: YES（命令 1–3；另 4–6 为同状态下的补证）
- Historical evidence used only as baseline: YES

## Commit

- 提交信息：`feat(fast-p): implement batch-research-direction-filter`
- Commit SHA：`decdb28dfe6431c9f238b76fa64fc9a1aad7939e`（提交后即本工作树 `HEAD`，分支 `fast/university-email-template-main`，`git merge-base --is-ancestor HEAD <branch>` 通过）
- 内容：仅上述 10 个授权文件（9 个已跟踪 + `V128` 新迁移）；`git show --stat` 计数 10 files changed；排除 `docs/plans/fast/**`、`target/`、报告文件（提交后 `git status --porcelain` 仅余 `docs/plans/fast/**` 下的计划/报告条目）。
- 提交后身份复算：plan SHA-256 仍为 `6c4a8c1a99e57ce9af7c329dfd9c37645dab8968ef62026e3fe2616eb58d0aca`；分支与 git-dir 不变；工作树内无源文件残留改动。
