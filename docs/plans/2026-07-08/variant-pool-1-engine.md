# 计划一：变体池渲染引擎正确性（variant-pool-1-engine）

> 系列：变体池完善 1/3。后续计划 variant-pool-2-seed-rollout、variant-pool-3-frontend 依赖本计划先合入。
> 决策记录：主主题始终入池（无新 DB 字段/开关）；全路径启用轮换（方案 A，落在计划二）；变体发送审计本期不做。

## 需求描述

可观测结果：
1. 保存模板时非法 `subjectVariants`（坏 JSON、空元素、重复、非法占位符）被拒绝并返回错误，不再静默落库。
2. 有变体时主主题参与轮换：主题池 = 主主题 + 全部变体。
3. snippet 变体组只在**同 snippetType** 内轮换，跨类型同组名不再串组。
4. `previewDraft` 支持 `variantIndex`，可逐个预览主题池成员（并以其作为 snippet 组预览种子）。
5. 暴露统一的 seed 派生函数 `variantSeedFor(orcidId, email)`，供计划二各调用路径使用。

不得改变：
- 无变体模板（`subjectVariants` 为 null/空）的渲染输出，subject/body 逐字节不变。
- 渲染路径对历史坏数据的容错：`parseSubjectVariants` 解析失败仍静默回退主主题，不抛错（校验只挡新写入）。
- `renderText` 占位符替换行为——5 个 variables 注入入口全部不变。(来源: K-renderText-all-callers)
- `qaRuleIds` 顺序契约。(来源: CLAUDE.md K-composed-reply-order-contract)
- `Math.floorMod` 负数防护，禁止改回 `abs`。(来源: K-positive-hash-index)

超出范围（明确不做）：
- 各调用路径传 seed → 计划二。
- 前端一切改动 → 计划三。
- 主主题 `subject` 字段自身的占位符校验（现状即未校验，保持，避免锁死历史模板编辑）。观察项：后续可单独评估。
- mail record 记录实际选中变体（已决策本期不做）。
- `GET /api/compose-templates/{id}/preview` 旧端点的变体支持（前端已改用 preview-draft，该端点维持现状）。
- `IntroductionMailComposer` 改造（其 seed 已正确）。

## 关键不变量

### Invariant I-1: 主题池语义（主主题入池）
- Rule: 主题池 pool = `[subject] + parseSubjectVariants(subjectVariants)`。variants 为空/解析失败 → pool = `[subject]`。选中项 = `pool[Math.floorMod(seed, pool.size)]`。
- Applies to: `selectSubjectVariant`（render/renderByCode 路径）、`selectDraftSubject`（previewDraft 路径，用 `variantIndex` 在**同一 pool** 上选择）。
- Violation consequence: 主主题永不发出，或预览与实发主题漂移。
- 来源: original（用户决策：始终入池）

### Invariant I-2: 保存时校验 subjectVariants
- Rule: `create`/`update` 前校验 `command.subjectVariants`（非 null 时）：(a) 必须可被 ObjectMapper 解析为 JSON 字符串数组；(b) 每个元素 trim 后非空；(c) 元素间 trim 后互不重复，且不等于 `subject.trim()`；(d) 每个元素通过 `mailVariableService.requireValidPlaceholders`。任一失败 → `IllegalArgumentException`（带明确中文错误信息），不落库。渲染路径的容错回退不变（见"不得改变"）。
- Applies to: `MailComposeTemplateService.validateCommand`（create 与 update 共用）。
- Violation consequence: 非法占位符原样发给专家；坏 JSON 静默吞掉运营配置。
- 来源: original（对齐 ReplySnippetService.create/update 已有的 requireValidPlaceholders 惯例）

### Invariant I-3: snippet 变体组限同类型
- Rule: `resolveSnippetVariant` 只在 `variantGroup` 相同**且** `snippetType` 与被引用 snippet 相同、且 enabled 的集合内选择；集合为空 → 返回被引用 snippet 本身（现行为）。
- Applies to: `resolveSnippetVariant` + 新增仓库方法 `findByVariantGroupAndSnippetTypeAndEnabledTrueOrderByDisplayOrderAsc`。
- Violation consequence: 跨类型误填同组名时开场白被替换成结束语。
- 来源: original

### Invariant I-4: 池间 seed 解耦
- Rule: subject 池 index = `floorMod(seed, pool.size)`（不变）；snippet 组 index = `floorMod(seed + variantGroup.hashCode(), group.size)`。一律 `Math.floorMod`，禁止 `abs`。
- Applies to: `selectSubjectVariant`、`resolveSnippetVariant`。
- Violation consequence: 主题第 i 个恒配片段第 i 个，变体组合完全相关，分散效果打折；`abs(Int.MIN_VALUE)` 负下标崩溃。
- 来源: K-positive-hash-index（floorMod 部分）+ original（解耦部分）

### Invariant I-5: 统一 seed 派生函数
- Rule: `MailComposeTemplateService` companion object 中新增公开函数 `fun variantSeedFor(orcidId: String?, email: String?): Int`：取第一个非空白值（优先 orcidId trim，其次 email trim 后 lowercase），返回其 `hashCode()`；两者皆空白 → 0。同一专家在所有路径得到相同 seed。
- Applies to: 本计划仅定义 + 单测；计划二全部 5 个调用点必须使用它。
- Violation consequence: 各路径自造派生逻辑，同一专家跨路径变体不一致、无法审计。
- 来源: original

### Invariant I-6: 渲染确定性（回归保护）
- Rule: 相同 `(templateId, variables, variantSeed)` 的 render/renderByCode 结果恒等；现有确定性测试（MailComposeTemplateServiceTest 209-390 行区间）必须继续通过（断言值按 I-1/I-4 新语义更新，确定性本身不得放宽）。
- Applies to: 渲染全链路。
- Violation consequence: 同一专家重发内容抖动，被反垃圾判定为可疑。
- 来源: original

## 现状审计

### mail_compose_template（MySQL，Spring Data JDBC）
- Schema: V61 建表，V62 统一模板，V64 加 `subject_variants TEXT NULL`（JSON 数组，为空用 subject）。无新迁移需求（主主题入池不需要 schema 变更）。
- Write paths:
  1. `MailComposeTemplateService.create` (:47) — 构造器传 `subjectVariants = command.subjectVariants`，**无任何校验**。
  2. `MailComposeTemplateService.update` (:69) — copy 传 `subjectVariants = command.subjectVariants`（可清空），无校验。
  3. `MailComposeTemplateService.setEnabled` / `delete` — 不触 variants。
  4. 迁移 V61/V62 种子数据 — 不含 subject_variants（列后加），无冲突。
- Read paths:
  1. `render` (:109) / `renderByCode` (:115) → `renderTemplate` (:127) → `selectSubjectVariant` (:487)：**variants 非空时主主题永不入选**。
  2. `preview(id)` (:144)：`subject = template.subject`、seed 0，完全忽略变体；前端已不调用（改用 preview-draft），维持现状。
  3. `previewDraft` (:156) → `selectDraftSubject` (:252)：**恒取 `variants[0]`**，无切换能力。
  4. `parseSubjectVariants` (:493)：坏 JSON 静默返回 null → 回退主主题。
  5. `listAll`/`getById` → `toDetail`：subjectVariants 已贯通 DTO 链（Request→Command→Detail 均有字段）。(来源: K-variant-pool-dto-chain — 注意：该条目所述"链路完全未贯通"已过期，实际代码已贯通，Phase 6 修正)
- Interaction points: I-1 改 `selectSubjectVariant` 语义 → 影响全部 6 个渲染调用点（计划二逐点接 seed）；`previewDraft` 增加 `variantIndex` → 前端 payload（计划三）。

### reply_snippet（MySQL）
- Schema: V47 建表，V64 加 `variant_group VARCHAR(64) NULL`。
- Write paths:
  1. `ReplySnippetService.create` (:47) / `update` (:76) — variantGroup trim 落库，content 已有 `requireValidPlaceholders`。
  2. `setEnabled` / `setDefault` / `delete` — 不触 variantGroup。
- Read paths:
  1. `MailComposeTemplateService.resolveSnippetVariant` (:480)：`findByVariantGroupAndEnabledTrueOrderByDisplayOrderAsc`（ReplySnippetRepository:16 区域）——**不过滤 snippetType**。
  2. `resolveBlocks` REPLY_SNIPPET 分支 (:431) — 调用 resolveSnippetVariant 后校验 enabled。
  3. `ReplySnippetService.resolveManualFrame`/`resolveAck` — 不走变体组，不受影响。
- Interaction points: 新仓库方法 + resolveSnippetVariant 换用 → 唯一调用点在 resolveBlocks，无其他消费方。

### 渲染调用点全集（seed 现状）
1. `IntroductionMailComposer.compose` (:18-22) — **已传** `expert.orcidId.hashCode()`。本计划不动。(来源: K-introduction-compose-hardcode / K-dual-outreach-paths：两条外发路径共用此入口)
2. `ManualExpertMailService.composeComposeTemplate` (:162) — seed 缺省 0。计划二。
3. `AutoMailReplyService.sendMeetingInvitation` (:972/:978，调用方 :470 有 `effectiveContact` 在作用域) — seed 缺省 0。计划二。
4. `MeetingInvitationMailComposer.compose` (:14) — seed 缺省 0。计划二。
5. `MeetingScheduleService`（确认信 :109）— seed 缺省 0。计划二。
6. `AutoReplyPreviewService` (:86) — seed 缺省 0；**预览镜像约束**：必须与 #3 同源同值。(来源: K-preview-mirrors-pipeline，P1) 计划二。

### 现有测试基线
- `MailComposeTemplateServiceTest` :209-390 — 已覆盖：variants null 回退、`["A","B","C"]` 确定性选择、variantGroup null 直用、同组确定性轮换。断言将随 I-1/I-4 新语义更新。
- `ManualExpertMailServiceTest` / `MeetingInvitationMailComposerTest` / `MeetingScheduleServiceTest` / `AutoReplyPreviewServiceTest` / `AutoMailReplyServiceTest` — mock renderByCode/render，计划二更新。

## 实现方案

### T1 — 保存校验（I-2）
文件: `MailComposeTemplateService.kt`
- `validateCommand` 内新增对 `command.subjectVariants` 的校验（新私有函数 `validateSubjectVariants(subject: String, subjectVariantsJson: String?)`）：解析（复用注入的 `objectMapper`，解析异常 → IllegalArgumentException "主题变体不是合法的 JSON 数组"）、逐项 trim 非空、去重（含与 subject 比对）、逐项 `mailVariableService.requireValidPlaceholders`。
- 读路径 `parseSubjectVariants` 不改（容错保留）。

### T2 — 主主题入池（I-1, I-6）
文件: `MailComposeTemplateService.kt`
- `selectSubjectVariant` (:487)：pool = listOf(template.subject) + variants；`pool[Math.floorMod(seed, pool.size)]`。

### T3 — snippet 组同类型过滤 + seed 解耦（I-3, I-4）
文件: `ReplySnippetRepository.kt`、`MailComposeTemplateService.kt`
- 仓库新增 `findByVariantGroupAndSnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(variantGroup: String, snippetType: String): List<ReplySnippet>`；旧方法 `findByVariantGroupAndEnabledTrueOrderByDisplayOrderAsc` 若无其他调用点（审计确认仅 resolveSnippetVariant 使用）则删除。
- `resolveSnippetVariant` (:480)：换新方法；index 改为 `Math.floorMod(variantSeed + variantGroup.hashCode(), groupSnippets.size)`。

### T4 — previewDraft variantIndex（I-1）
文件: `MailComposeTemplateService.kt`
- `ComposeTemplatePreviewDraftRequest` 增加 `val variantIndex: Int? = null`。
- `selectDraftSubject(subject, variants, variantIndex)`：按 I-1 pool 语义，`pool[Math.floorMod(variantIndex ?: 0, pool.size)]`（index 0 = 主主题）。
- `previewDraft` 中 `resolveBlocks(draftBlocks, variantSeed = request.variantIndex ?: 0)`，使 snippet 组预览随切换联动。
- 结果 DTO 不加字段（前端本地已知 pool 大小），控制器透传无需改动（DTO 定义在 service 文件内）。

### T5 — 测试（I-1..I-6）
文件: `MailComposeTemplateServiceTest.kt`
- I-2: 坏 JSON / 空元素 / 变体互重 / 与主主题重复 / 非法占位符 → create 与 update 均抛 IllegalArgumentException；合法输入通过。
- I-1: `subject="S", variants=["A","B"]` → seed 0/1/2 分别命中 S/A/B（floorMod 3）；variants null 恒 S。
- I-3: 同组名不同 snippetType 的 snippet 不入池。
- I-4: 相同 seed 下 subject index 与 snippet index 不再恒同（构造 pool 大小相同的用例断言解耦）。
- I-5: `variantSeedFor("0000-0001", "a@b.c")` 用 orcid；`variantSeedFor(null, " A@B.C ")` 用 trim+lowercase email；`variantSeedFor(null, null)` = 0；同输入恒同输出。
- I-6: 既有确定性用例按新语义更新断言并保持通过。
- previewDraft: variantIndex null→主主题；1→variants[0]；越界 floorMod 回绕。

## 变更文件清单

| # | 文件 | 变更 |
|---|------|------|
| 1 | src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt | T1 校验、T2 入池、T3 换查询+解耦、T4 variantIndex、I-5 helper |
| 2 | src/main/kotlin/com/weibo/talentintroduction/reply/repository/ReplySnippetRepository.kt | T3 新查询方法（旧方法删除） |
| 3 | src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt | T5 全部用例 |

文件数 3 ≤ 10；子系统 1（template 渲染引擎）≤ 2；新增共享存储字段 0 ≤ 1。

## 验收标准

- I-1: 单测断言 pool=[S,A,B] 时 seed 0/1/2 → S/A/B；无变体模板输出与改动前逐字节一致（回归用例）。
- I-2: 五类非法输入 create/update 均被拒且不落库（mock repository 无 save 调用）；渲染坏 JSON 历史数据仍回退主主题不抛错。
- I-3: 跨类型同组名用例断言不串组；组空回退原 snippet。
- I-4: 代码 grep 无 `kotlin.math.abs` 用于下标；解耦用例通过。
- I-5: helper 三分支单测通过；grep 确认函数为 public 且位于 companion object。
- I-6: `mvn test -Dtest=MailComposeTemplateServiceTest` 全绿；不得只以 `mvn test` 通过为完成依据，须逐条打开 selectSubjectVariant/resolveSnippetVariant/validateCommand/selectDraftSubject 对照不变量核对。(来源: K-template-feature-coverage)
- 集成场景: renderByCode("INTRODUCTION", seed=variantSeedFor(...)) 端到端断言 subject 来自 pool 且 body snippet 来自同类型组。
