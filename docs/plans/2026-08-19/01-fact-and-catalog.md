# P1：新增项目身份事实 + 目录对齐（事实与 intent 目录）

> 主计划：`00-grounded-coverage-master.md`。本计划必须先于 P2 执行。
> 触发来信（逐字，作为 fixture 权威文本，见 I-7）：
> `Thank you for contacting me and for your explanation of how the programme works. I would be open to exploring whether there could be a suitable match. My current clinical and research interests are mainly focused on orthopaedic trauma, particularly femoral fractures, peri-implant femoral fractures, fracture fixation strategies, implant-related complications, and clinical research and registry development in these areas. Before going further, I would appreciate some additional information about the programme, particularly its official name, the government body or institution supporting it, the usual form of collaboration with the Chinese partner companies, and the general arrangements regarding remuneration and intellectual property. At this stage, I would be happy to continue the conversation by email.`

---

## 需求描述

**Observable outcome**

1. 上述来信在可信回复工作台上，`requestCoverage[].intents` 从当前的 2 条（`finance.arrangements`、`ip.arrangements`）变为 5 条，新增 `programme.name`、`governance.sponsor`、`collaboration.form`，且三条新 intent 各自 `status = "SUPPORTED"`、`evidenceRuleIds` 非空。
2. QA 规则 `Agency credentials and government cooperation` 从"结构性不可达"变为可被 `governance.sponsor` 取证。
3. 操作员在工作台看到该摘要绑定 5 条事实（当前 2 条）。

**What must NOT change**

- N1. 现有 20 条 intent 定义（`AiReplyIntentCatalog.definitions`）的 alias 列表、`requiredCoverageKeys`、`alternativeCoverageKeys`、`requiresProfile` 一律不改。
- N2. `AiReplyContextService.kt:26` 的 `matchIntents(text).any { it.requiresProfile }` 结果不变（三条新 intent 的 `requiresProfile` 必须为默认 `false`）。
- N3. `finance.arrangements` 与 `ip.arrangements` 在本封信上绑定的事实不变（`Funding support` / `Pre-contract IP boundary`）。
- N4. 既有 QA 规则的 `reply_body` / `answer_body` / `enabled` / `priority` / `display_name` / `coverage_keys` 一律不改。本计划对既有规则**只追加 `keywords`**（id=6 与 id=18 两条）。
- N5. `QaCoverageKeyCatalog` 既有 29 个键的 `key` / `group` 不改（`label` / `description` 也不改）。
- N6. 已应用的迁移文件（V3–V104）一个字节都不改。

**Out of scope（明确延后）**

- O1. 「未识别诉求静默消失」的判定层修复 → P2。识别不了的问题在本计划后**仍然**不会变成 MISSING。
- O2. 事实排序的人工控制 → P3（见主计划附录 A）。
- O3. 资助金额口径冲突（事实库 `3-12 million RMB` vs 官网 1M/5–8M/100M）。本计划不碰任何金额文案。
- O4. 另外两个孤儿覆盖键 `application.required_materials`、`work.relocation` 的处置。
- O5. 反向失配键 `work.time_commitment` / `work.advisory_duration`（intent 要、目录没有）。
- O6. 官网 `/en/talent.html`、`/en/cases.html` 的内容更新。
- O7. `QaRuleManagementService` 的 coverage_keys 写路径改造（见 I-4 只做约束不做改造）。

---

## 关键不变量

### I-1: 新 coverage key 必须被至少一个 intent 引用（禁止再造死键）
- Rule: 任何写入 `QaCoverageKeyCatalog.catalog` 的新键，必须同时出现在 `AiReplyIntentCatalog.definitions` 某条的 `requiredCoverageKeys` 或 `alternativeCoverageKeys` 中。反之，任何 intent 的 required/alternative 键必须是 `QaCoverageKeyCatalog` 的合法键。
- Applies to: `QaCoverageKeyCatalog.kt`（新增 `programme.name`、`governance.sponsor_level`）、`AiReplyIntentCatalog.kt`（新增三条 intent）。
- Violation consequence: `isCoverageEligible`（`AiReplyIntentCatalog.kt:475-481`）对非空 coverage 要求与 intent 的 required+alternative 有交集；无 intent 引用该键 → 对所有 intent 恒 `false` → `selectIntentKeyForRule`（:483-500）返回 `null` → 携带该键的规则在 grounded 链路中**永久不可达**。这正是 `company.verification_evidence` 今天的状态。
- 证据：
  ```
  $ grep -c "verification_evidence" src/main/kotlin/.../llm/service/AiReplyIntentCatalog.kt
  0
  $ grep -rn "verification_evidence" src/main/kotlin src/main/resources/db/migration
  QaCoverageKeyCatalog.kt:58   Entry("company.verification_evidence", "可验证的公司证明", ...)
  V76__add_qa_rule_coverage_keys.sql:19   SET coverage_keys = 'company.verification_evidence'
                                          WHERE reply_subject = 'Agency credentials and government cooperation'
  ```
- 来源: original（本轮实测）

### I-2: keyword 必须同时满足「来信子串」与「alias 规范化后子串」两个条件
- Rule: 一条 QA 规则要成为某 intent 的证据，它的**同一个** keyword 必须同时满足：
  (a) 是来信文本经 `QaFactKeywordMatcher.normalize` 后的子串 —— 否则不进 `candidateRules`；
  (b) 是该 intent 的 `title` 或某条 `requestAliases` 经 `AiReplyIntentCatalog.canonicalize` 后的子串 —— 否则 `scoreRuleIntentAlignment` 得 0 分，`selectIntentKeyForRule` 不分配。
- 两侧规范化**不对称**，这是本不变量的要害：
  - `QaFactKeywordMatcher.normalize`（`QaFactSelectionService.kt:381-386`）：lowercase + 空白折叠 + `details`→`information`。**不做 programme→program**。
  - `AiReplyIntentCatalog.canonicalize`（`AiReplyIntentCatalog.kt:305-312`）：URL 屏蔽 + lowercase + 连字符/空白归一 + **`\bprogramme\b` → `program`**。
  - 后果：任何含 `programme` 的 rule keyword 永远无法满足 (b)（alias 侧已被改写成 `program`）；而写成 `program name` 又无法满足 (a)（来信原文是 `the programme, particularly its official name`）。
- Applies to: V105 迁移里三处 keywords（两条新规则 + id=6 追加）、`AiReplyIntentCatalog` 三条新 intent 的 aliases。
- Violation consequence: intent 识别成功但 `evidenceRuleIds` 为空 → `resolveIntentEvidence`（:398-414）判 `MISSING` → 整项掉到 PARTIAL/UNSUPPORTED，事实明明存在却被判缺失。
- 来源: K-company-identity-keyword-intent-parity（本轮把"parity"落到上述两条可判定条件）

### I-3: 三条新 intent 的 `requiresProfile` 必须为 false
- Rule: `programme.name` / `governance.sponsor` / `collaboration.form` 不得设置 `requiresProfile = true`。
- Applies to: `AiReplyIntentCatalog.definitions` 新增项。
- Violation consequence: `AiReplyContextService.kt:26` 是 `matchIntents(text).any { it.requiresProfile }`，任一新 intent 置 true 会让所有含这些问法的来信被判为需要研究画像，`resolveIntentEvidence` 的 `requiresProfile && !profileSufficient` 分支（:399-408）直接返回 MISSING，与 N2 冲突。
- 来源: original

### I-4: 新规则的 `coverage_keys` 只能由迁移落库
- Rule: 两条新事实的 `coverage_keys` 必须写在 V105 的 `INSERT` 列里；不得依赖后台 UI 补录。
- Applies to: `V105__add_programme_identity_facts.sql`。
- Violation consequence: `coverage_keys` 有**两条写路径**，迁移不写就只剩后台 UI，而后台是否填由运营决定；未填则落进 `isCoverageEligible`（`AiReplyIntentCatalog.kt:475-481`）的 `keys.isEmpty()` 分支，行为退化为"非高风险 intent 一律可取证"，与 I-1 的意图相反。
- **⚠️ 知识条目 `K-qa-coverage-keys-management-write-boundary` 已过时，本轮实测更正**：该条写的是「`createRule()` 强制写空、`updateRule()` 不更新该字段」，实际代码已经支持：
  ```
  $ grep -n "coverageKeys" src/main/kotlin/.../qa/service/QaRuleManagementService.kt
  74:  val normalizedCoverage = QaCoverageKeyCatalog.normalizeAndValidate(command.coverageKeys)
  76:  val coverageKeys = QaCoverageKeyCatalog.serialize(normalizedCoverage)
  83:  coverageKeys = coverageKeys                       # createRule 会写
  101: val effectiveCoverage = when (command.coverageKeys) {
  102:     null -> QaCoverageKeyCatalog.parseStored(existing.coverageKeys)
  103:     else -> QaCoverageKeyCatalog.normalizeAndValidate(command.coverageKeys)
  106: val coverageKeys = when (command.coverageKeys) {
  107:     null -> existing.coverageKeys                  # updateRule 传 null 时保留
  120: coverageKeys = coverageKeys,                       # 传值时会更新
  ```
  即 `createRule` **会**写 coverageKeys，`updateRule` 在 `command.coverageKeys != null` 时**会**更新、为 null 时保留原值。
  **对本计划的影响**：I-4 的约束从「只能靠迁移」放宽为「迁移必须写，且后台后续可覆盖」——因此 A-6 人工验收要同时确认后台改这两条规则不会把 coverage_keys 清空。
  知识条目已就地更正（见 `docs/knowledge/qa/K-qa-coverage-keys-management-write-boundary.md`）。
- 来源: K-qa-coverage-keys-management-write-boundary（本轮实测证明其陈旧，已更正）

### I-5: 对既有规则只做防重追加，且保持 updated_at
- Rule: V105 对 id=6 的 keywords 修改必须是 `CONCAT` + `NOT LIKE` 防重形式，且显式 `updated_at = updated_at`；两条新规则的 `INSERT` 必须带 `WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = '...')`。
- Applies to: `V105__add_programme_identity_facts.sql` 全部三条语句。
- Violation consequence: `qa_rule` 有两类写路径（Flyway 迁移 与 `QaRuleManagementService` 运行时）；直接 `SET keywords = '...'` 会无条件覆盖运营在 UI 上的改动；不写 `updated_at = updated_at` 会把记录误标为运营更新（`V79__add_qa_answer_body.sql:4-6` 是既有反例）。
- 来源: K-qa-rule-runtime-vs-migration-writes + K-qa-migration-preserve-auto-updated-timestamp

### I-6: 不得用宽泛事实覆盖相邻问法制造假完整
- Rule: id=6 `Full-time and part-time options` 只追加"合作形式"族关键词（`form of collaboration` / `forms of collaboration` / `how the collaboration works`），**禁止**追加 `partner companies` / `partner company` / `collaboration with` 这类会把"企业本身是谁"的问法也吸进来的词——那是 `enterprise.matching` 的语义域。
- Applies to: V105 对 id=6 的 keywords 追加。
- Violation consequence: 专家问"合作企业是哪家"会被 id=6 的"可兼职做技术顾问、远程指导"正文当作已答，判 SUPPORTED，制造假完整。
- 来源: K-due-diligence-intent-fact-parity

### I-7: intent 回归必须用逐字原始来信
- Rule: `AiReplyIntentCatalogTest` 与 `QaFactSelectionServiceTest` 的新增用例必须使用本文件顶部逐字保留的来信全文，不得改写、截断或"语义等价"重述。
- Applies to: 两个测试文件的新增 fixture。
- Violation consequence: 英美拼写、连字符、词序差异会静默改变匹配结果；改写过的 fixture 通过不代表真实邮件通过。
- 来源: K-ai-reply-intent-alias-fixture-fidelity（hit_count 17）

### I-8: intent 目录变更会使存量工作台状态降级为 STALE（已验证非硬失败）
- Rule: 本计划新增 intent 会改变 `matchIntents` 结果 → 改变 `intentKeys` → 改变 `requestKey` 哈希。必须确认降级路径是 STALE 回退而非 500/422 泄漏到操作员。
- Applies to: `TrustReplyWorkbenchService.bootstrap`。
- 已验证的降级链（本轮读码，非推测）：
  - `requestKey = sha256(sourceVersion \0 index \0 requestText \0 intentKeys)`（`TrustReplyWorkbenchService.requestKey`，:1831-1844）
  - `validateMatrixKeys`（:1506-1527）对存量 key 不在当前 canonical 集合中时抛 `TRUST_REPLY_REQUEST_KEY_INVALID`
  - `bootstrap`（:343 起；`implicitSelectionUnusable = true` 在 :370）捕获该异常，在调用方未显式传选择时置 `implicitSelectionUnusable = true` 并以 `null` 重新解析（回退自动选择）
  - 返回 `TrustReplySavedState(status = "STALE", ...)`（:381）
  - 前端 `trust-reply-workbench.js:560` 显示「STALE：来源或依据已变化，旧锁定回答未恢复」
- Violation consequence: 若上述任一环被改动，intent 目录升级会变成在办邮件的硬故障。
- 来源: original（本轮实测）

---

## 现状审计

### `qa_rule` 表

**Schema 相关列**（按迁移追溯）
- `coverage_keys VARCHAR(2000) NOT NULL DEFAULT ''` — `V76__add_qa_rule_coverage_keys.sql:7-8`
- `answer_body TEXT NOT NULL` — `V79__add_qa_answer_body.sql`，由 `reply_body` 全量回填
- `supersedes_children TINYINT(1) NOT NULL DEFAULT 0` — `V41__qa_overview_supersede.sql:4`
- `reply_policy` — `V80__add_qa_reply_policy.sql`

**写路径（grep 回执）**
```
$ grep -rn "qa_rule" src/main/resources/db/migration/*.sql | wc -l   # 迁移侧写路径众多，逐条见下
$ grep -rln "qaRuleRepository.save\|QaRuleManagementService" src/main/kotlin
src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt
```
1. Flyway 迁移 — V3 种子、V38 分类重构、V52 信任规则、V57/V65 正文修订、V68/V70 关键词与边界、V75 公司身份拆分、V76 coverage 回填、V79 answer_body、V81 关键词 parity、V82 原子拆分。
2. `QaRuleManagementService.createRule()` — 运行时新建，**强制写空 coverage_keys**。
3. `QaRuleManagementService.updateRule()` — 运行时改 keywords / reply_body / answer_body，**不更新 coverage_keys**。
4. `QaRuleManagementService.setRuleEnabled()` — 直接持久化 enabled，**不经正文/coverage 校验**（K-qa-rule-enable-must-revalidate-facts）。

**读路径**
1. `QaFactSelectionService.select()` :20-23 / `selectForWorkbench()` :89-101 — `findAllEnabledOrdered().filter { it.isMatchable() && it.answerBody.trim().isNotBlank() }`。**此路径不调用 `applySupersede`**，因此 `supersedes_children=1` 的 `Program overview` 在事实选择链路上不会吞掉子规则命中（与 `QaMatchService` 不同）。
2. `QaMatchService.suggestComposition()` :18-58 — 用 `applySupersede`，gapItems 用未压缩前的 `enabledRules` 逐 request 算 `candidateRuleIds`。
3. `AiReplyDraftService.resolveQaRules()` → mode 判定（:842-850）。

**本计划涉及的三条既有规则现状（逐条回执）**

| 规则 | id | 当前 keywords | coverage_keys | 来源 |
|---|---|---|---|---|
| Agency credentials and government cooperation | 18 | `accredited,official agency,prove government,cooperation with government,authorized,how can i trust,trust you,can i trust,commercial,not academic,is this legitimate,legitimate,is this a scam,scam,verify,company website,company site,who are you,real company,are you real`（**无 `linkedin`** —— V52:11 曾加入，V65:99 已删除，V75:11 沿用删除后的版本） | `company.verification_evidence` | `V75:11`（**当前权威**）、`V76:17-20` |
| Full-time and part-time options | 6 | `full time,part time,remote,technical consultant` | `work.remote_arrangement,work.travel_arrangement` | `V3:30`、`V76:83-85` |
| Program overview | 24 | `learn more,more information,name and background,objectives and scope,before sharing,understand the program` + V81 追加 `typical duration,duration of advisory projects,advisory project duration` | 11 个键（见 V76:22-25） | `V41:14`、`V70:12-24`、`V81:22-33` |

**实测：这三条对本封来信的命中情况**
- id=18：来信含 `government body or institution supporting it`，与 `official agency` / `prove government` / `cooperation with government` / `authorized` 均无子串关系 → **不进 candidateRules**（V105 语句四补词后成立，见 IP-6）。
- id=6：来信不含 `full time` / `part time` / `remote` / `technical consultant` → **不进 candidateRules**。
- id=24：来信是 `some additional information about the programme`，不含 `more information` / `learn more` / `name and background` / `understand the program` → **不进 candidateRules**。（此前预览稿误列 id=24 会命中，本轮实测推翻。）

**Interaction points**
- IP-1：`V105` 写 `coverage_keys` × `QaFactSelectionService.buildRequestFact` 读 `isCoverageEligible` —— 新键必须被 intent 引用（I-1）。
- IP-2：`V105` 写 id=6 `keywords` × `QaFactKeywordMatcher.matchesRule` 读（进 candidateRules）× `scoreRuleIntentAlignment` 读（分配 intent）—— 两侧规范化不对称（I-2）。
- IP-3：`AiReplyIntentCatalog.definitions` 写 × `TrustReplyWorkbenchService.canonicalRequests`（:1496-1503）读 → `requestKey` 哈希 → `trust_reply_workbench_state`（V83）存量行 —— 降级为 STALE（I-8）。
- IP-4：`AiReplyIntentCatalog.definitions` 写 × `AiReplyContextService.kt:26` 读 `requiresProfile` —— I-3。
- IP-5：`governance.sponsor` 把 `company.verification_evidence` 列为 alternative 后，id=18 首次进入 grounded 取证池。
  **已用代码推演结论（无需运行时确认）**：`isCoverageEligible`（:475-481）对非空 coverage 要求交集，
  而 `company.verification_evidence` 在 P1 之后**只**被 `governance.sponsor` 引用 → 其余 intent 一律 false →
  `selectIntentKeyForRule` 的 `scored` 至多含一项，`catalogOrder` tie-break 永不触发。**不会被其他 intent 取走。**
- IP-6（**自查时发现的缺陷，已修**）：仅让 coverage key 不再是孤儿**还不够**。`selectIntentKeyForRule`（:483-500）
  要求 `scoreRuleIntentAlignment > 0`，而该函数比较的是「rule 的每个 keyword」是否为「intent 的 title/alias 规范化后」的子串。
  id=18 的 keywords（`accredited` / `official agency` / `prove government` / `cooperation with government` / `authorized` / `how can i trust` / …）
  与初稿提出的 `governance.sponsor` alias 集（`government body` / `institution supporting` / …）**没有任何子串交集** → score = 0 → 仍然不分配。
  同时 id=18 的 keywords 也不是骨科来信的子串 → 连 `candidateRules` 都进不去。
  **修法：V105 追加一条语句，给 id=18 补 `government body` / `institution supporting` 两个关键词** ——
  这一个动作同时满足 I-2 的两侧（既是来信子串，又是 alias 子串）。见实现方案 B-1 语句四。

### 前端样式盘点

不适用：本计划变更文件清单中无任何 `.html` / `.css` / 前端 `.js` 文件。工作台事实 chip 的渲染（`trust-reply-workbench.js:1425-1440`）由服务端返回的 `factRuleIds` 驱动，事实条数变化无需前端改动。

---

## 实现方案

### 阶段 A：目录对齐（先于迁移，保证 I-1 双向成立）

**A-1. `QaCoverageKeyCatalog.kt` 新增两个键**（遵守 I-1、N5）

在 `catalog` 列表中，`programme.scope` 之后追加：
```kotlin
Entry("programme.name", "项目名称与可见性", "对外可用的计划名称与项目是否公开", "项目概况"),
```
在 `company.verification_evidence` 之后追加：
```kotlin
Entry("governance.sponsor_level", "背书层级与组织方", "项目的政府背书层级与具体组织申报的机构层级", "公司信息"),
```
> 注意 `normalizeAndValidate`（:106-120）末行 `return all().map { it.key }.filter { it in trimmed }` —— 返回顺序由 `catalog` 声明顺序决定，插入位置会影响既有规则 coverage_keys 的**序列化顺序**。因此两个新键必须插在**列表末尾以外的位置时**同步确认 A-4 的迁移文本断言用的是集合比较而非字符串比较。**为规避该风险，本计划要求两个新键一律追加到 `catalog` 列表末尾**（在 `confidentiality.research` 之后），不插入到语义相邻位置。

**A-2. `AiReplyIntentCatalog.kt` 新增三条 intent**（遵守 I-1、I-2、I-3）

追加到 `definitions` 列表末尾：
```kotlin
RequestIntentDefinition(
    key = "programme.name",
    title = "Programme name",
    requestAliases = listOf(
        "official name", "its official name", "the official name",
        "what is it called", "name of the scheme"
    ),
    requiredCoverageKeys = listOf("programme.name"),
    alternativeCoverageKeys = listOf("programme.tracks")
),
RequestIntentDefinition(
    key = "governance.sponsor",
    title = "Sponsoring body and organising level",
    requestAliases = listOf(
        "government body", "government institution", "government agency",
        "institution supporting", "body or institution", "which government",
        "who supports the", "supporting body"
    ),
    requiredCoverageKeys = listOf("governance.sponsor_level"),
    alternativeCoverageKeys = listOf("company.verification_evidence")
),
RequestIntentDefinition(
    key = "collaboration.form",
    title = "Form of collaboration",
    requestAliases = listOf(
        "form of collaboration", "forms of collaboration",
        "how the collaboration works", "collaboration arrangement"
    ),
    requiredCoverageKeys = listOf("work.remote_arrangement"),
    alternativeCoverageKeys = listOf("work.travel_arrangement", "role.responsibilities")
)
```
> `collaboration.form` 的 required 不含 `work.relocation`（O4 已声明延后），alternative 不含 `enterprise.matching`——后者属 `enterprise.matching` intent 自己的语义域（I-6）。

**A-3. `groupTitles` 追加一组**（可选但推荐，避免多 intent 合并时标题回落到原文截断）

在 `groupTitles` 列表追加：
```kotlin
IntentGroupTitle(
    intentKeys = setOf("programme.name", "governance.sponsor"),
    title = "Programme identity and sponsorship"
)
```

**A-4. I-1 双向一致性守卫测试**（新增用例，落在 `AiReplyIntentCatalogTest.kt`）

断言两个方向：
- `AiReplyIntentCatalog.definitions.flatMap { it.requiredCoverageKeys + it.alternativeCoverageKeys }.toSet()` 中每个键都满足 `QaCoverageKeyCatalog.isValid(key)`；
- `QaCoverageKeyCatalog.all().map { it.key }` 中，除**当前已知豁免集** `setOf("general.answer", "application.required_materials", "work.relocation")`（O4/O5 已声明延后）外，每个键都被至少一条 intent 的 required/alternative 引用。
> 豁免集必须写成显式常量并附注释指向 O4/O5，使后续计划删豁免项时测试立刻变红。`work.time_commitment` / `work.advisory_duration` 属反向失配（O5），第一条断言会立即失败——因此本计划**必须**同步把这两个键加入 `QaCoverageKeyCatalog`，或把第一条断言同样加豁免集。**选定做法：加入豁免集**，理由是加键会改变 `parseStored` 的合法键集合，属于 O5 范围。

### 阶段 B：事实迁移

**B-1. 新建 `V105__add_programme_identity_facts.sql`**（遵守 I-2、I-4、I-5、I-6）

> 版本号回执：
> ```
> $ ls src/main/resources/db/migration | sed 's/__.*//;s/V//' | sort -n | tail -3
> 102
> 103
> 104
> ```
> 最大为 V104，故新迁移为 V105。（注意 `ls | tail` 按字典序会误显示 V99 为末位。）

语句一 — 新事实①（coverage `programme.name`）：
```sql
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    answer_body, display_name, section_title, reply_policy,
    auto_reply_enabled, handoff_required, supersedes_children, enabled, coverage_keys
)
SELECT
    (SELECT id FROM qa_category WHERE category_code = 'OVERVIEW'),
    'official name,name of the scheme,what is it called',
    'ANY', 120, 'Programme name and public visibility',
    '<正文见下>', '<同 reply_body>',
    'Programme name and public visibility', 'Programme identity', 'AUTO', 1, 0, 0, 1,
    'programme.name'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Programme name and public visibility');
```
正文（`reply_body` = `answer_body`，逐字）：
```
The programme runs as three schemes: the Innovative Talent Project, the Entrepreneurial Talent Project and the Young Talent Project. The programme itself is not publicly listed and has no public project website; in formal documents it is identified by the scheme name together with the applying agency and the application year.
```

语句二 — 新事实②（coverage `governance.sponsor_level`）：
```sql
INSERT INTO qa_rule (...同上列...)
SELECT
    (SELECT id FROM qa_category WHERE category_code = 'TRUST_AND_COMPLIANCE'),
    'government body,government institution,government agency,institution supporting,supporting body',
    'ANY', 120, 'Programme sponsorship and organising level',
    '<正文见下>', '<同 reply_body>',
    'Programme sponsorship and organising level', 'Trust and compliance', 'AUTO', 1, 0, 0, 1,
    'governance.sponsor_level'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Programme sponsorship and organising level');
```
正文（逐字）：
```
It is a national-level talent scheme, and applications are organised locally through municipal governments and their talent offices. Jiangsu Qingfei Talent Technology Co., Ltd. maintains standing cooperation with the local governments of Shanghai, Hangzhou, Suzhou, Wuxi, Nantong and Ningbo. Which talent office handles an application depends on the location of the matched enterprise and is therefore not determined before matching.
```

语句三 — id=6 追加关键词（防重 + 保时间戳）：
```sql
UPDATE qa_rule
SET keywords = CONCAT(keywords,
    CASE WHEN LOWER(keywords) NOT LIKE '%form of collaboration%'
         THEN ',form of collaboration' ELSE '' END,
    CASE WHEN LOWER(keywords) NOT LIKE '%forms of collaboration%'
         THEN ',forms of collaboration' ELSE '' END,
    CASE WHEN LOWER(keywords) NOT LIKE '%how the collaboration works%'
         THEN ',how the collaboration works' ELSE '' END
),
updated_at = updated_at
WHERE reply_subject = 'Full-time and part-time options'
  AND (
    LOWER(keywords) NOT LIKE '%form of collaboration%'
    OR LOWER(keywords) NOT LIKE '%forms of collaboration%'
    OR LOWER(keywords) NOT LIKE '%how the collaboration works%'
  );
```

语句四 — id=18 追加关键词（IP-6 的修法；防重 + 保时间戳）：
```sql
UPDATE qa_rule
SET keywords = CONCAT(keywords,
    CASE WHEN LOWER(keywords) NOT LIKE '%government body%'
         THEN ',government body' ELSE '' END,
    CASE WHEN LOWER(keywords) NOT LIKE '%institution supporting%'
         THEN ',institution supporting' ELSE '' END
),
updated_at = updated_at
WHERE reply_subject = 'Agency credentials and government cooperation'
  AND (
    LOWER(keywords) NOT LIKE '%government body%'
    OR LOWER(keywords) NOT LIKE '%institution supporting%'
  );
```
> 这两个词同时是骨科来信的子串**和** `governance.sponsor` 的 alias，因此一次满足 I-2 的两个条件。
> 不追加 `prove government` / `authorized` 之类的既有词到 alias 侧——那会让「你们是正规机构吗」这类纯信任问法
> 也被 `governance.sponsor` 吸走，属 I-6 禁止的假完整。

> **I-2 两侧核对表（三条新/改规则逐条验算，执行前必须复核）**
>
> | 规则 | keyword | 是来信子串？ | 是 alias 规范化后子串？ |
> |---|---|---|---|
> | 新事实① | `official name` | ✅ `particularly its official name` | ✅ alias `official name` |
> | 新事实② | `government body` | ✅ `the government body or institution supporting it` | ✅ alias `government body` |
> | 新事实② | `institution supporting` | ✅ 同上 | ✅ alias `institution supporting` |
> | id=6 追加 | `form of collaboration` | ✅ `the usual form of collaboration with…` | ✅ alias `form of collaboration` |
> | id=18 追加 | `government body` | ✅ 同上 | ✅ alias `government body` |
>
> 三条新 intent 的 coverage 归属也已验算为**唯一**：`programme.name` 只被 intent `programme.name` 引用、
> `governance.sponsor_level` 与 `company.verification_evidence` 只被 `governance.sponsor` 引用、
> id=6 的 `work.remote_arrangement,work.travel_arrangement` 只与 `collaboration.form` 有交集
> （`finance.arrangements` / `ip.arrangements` / `programme.name` / `governance.sponsor` 的 required+alternative 均不含这两个键）。
> 因此 `selectIntentKeyForRule` 的 `catalogOrder` tie-break 在本封来信上永不触发。

> **占位符核对（K-flyway-placeholder-replacement）**：本迁移正文与关键词均**不含 `${`**。同时 `src/main/resources/application.yml:8-13` 已显式 `placeholder-replacement: false`，回归断言在 `UnsubscribeBodyLinkMigrationTest.kt:46`。本计划不改该配置。

> **上线前基线核对（I-5 / K-qa-rule-runtime-vs-migration-writes）**：部署前必须导出线上 `qa_rule` 中 `reply_subject = 'Full-time and part-time options'` 的 `keywords` 实值，与本计划记录的基线 `full time,part time,remote,technical consultant` 比对。若运营已在 UI 改过，先把改动并入迁移的 `NOT LIKE` 守卫再上线。

### 阶段 C：回归

**C-1. `AiReplyIntentCatalogTest.kt`**（I-7）
- 用顶部逐字来信断言 `matchIntents(mail).map { it.key }.toSet()` 恰好等于
  `setOf("programme.name", "governance.sponsor", "collaboration.form", "finance.arrangements", "ip.arrangements")`。
- 断言不含 `general.answer`（说明 `disambiguated` 非空）。
- 回归既有：随机抽取 3 条既有 fixture 断言其 intent 列表**逐字未变**（守 N1）。
- A-4 的双向 coverage key 守卫用例。
- I-2 机械 parity 用例：对三条新 intent，断言存在至少一个 keyword 字符串 `k`，使得 `QaFactKeywordMatcher.normalize(mail).contains(k)` 且 `(title + aliases).any { canonicalize(it).contains(k) }` 同时成立。

**C-2. `QaFactSelectionServiceTest.kt`**（I-2、I-6、N3）
- 以三条新规则 + id=6（追加后 keywords）+ `Funding support` + `Pre-contract IP boundary` 构造 `promptPool`，对逐字来信调用 `buildRequestFact`，断言：
  - `intents` 5 条且全部 `status == "SUPPORTED"`；
  - `factRuleIds` 包含新事实①②、id=6、`Funding support`、`Pre-contract IP boundary`；
  - `status == RequestGroundingStatus.GROUNDED`。
- I-6 负向用例：输入 `Which partner company would I be matched with?`，断言 id=6 **不**出现在 `factRuleIds`（防止"合作形式"关键词把企业身份问法吸走）。
- N3 用例：断言 `finance.arrangements` 的 `evidenceRuleIds` 仍只含 `Funding support`，`ip.arrangements` 仍只含 `Pre-contract IP boundary`。

**C-3. 新建 `ProgrammeIdentityFactsMigrationTest.kt`**（迁移文本断言，范式同 `QaSeedEncodingRepairMigrationTest.kt`）
- `Files.readString(Path.of("src/main/resources/db/migration/V105__add_programme_identity_facts.sql"))` 后断言：
  - 含两处 `WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject =`（I-5）；
  - 含 `updated_at = updated_at`（I-5）；
  - id=6 的三个新词各有配套 `NOT LIKE` 守卫（I-5）；
  - 全文不含 `${`（占位符）；
  - 两条新规则的 `coverage_keys` 字面量分别为 `programme.name` 与 `governance.sponsor_level`（I-4）。
> 选择文本断言而非 `FlywayMigrationIntegrationTest` 的理由：后者需本机 Docker 且默认跳过（`@EnabledIfSystemProperty(named = "migrationIt", matches = "true")`），无法进全量 `mvn test` 门禁。

---

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V105__add_programme_identity_facts.sql` | 新增 | 两条新事实 INSERT + id=6 与 id=18 各追加 keywords（四条语句） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyCatalog.kt` | 修改 | 追加 `programme.name`、`governance.sponsor_level` 两个 Entry（列表末尾） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt` | 修改 | 追加三条 `RequestIntentDefinition` + 一组 `IntentGroupTitle` |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalogTest.kt` | 修改 | C-1 全部用例 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | 修改 | C-2 全部用例 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/ProgrammeIdentityFactsMigrationTest.kt` | 新增 | C-3 迁移文本断言 |

文件数 **6** ≤ 10。子系统 **2**（QA 事实/迁移、LLM intent 目录）≤ 2。新增共享存储字段 **0**（`coverage_keys` 列已存在，本计划只写值）。

---

## 验证命令

> 本项目是 Kotlin + Spring Boot 2.7（Java 11）Maven 工程，**必须用 JDK 11（zulu-11）**，裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyIntentCatalogTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSelectionServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ProgrammeIdentityFactsMigrationTest

# 三个类一起跑
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='AiReplyIntentCatalogTest,QaFactSelectionServiceTest,ProgrammeIdentityFactsMigrationTest'

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空库全量迁移（可选；需本机 Docker，默认跳过）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0，且测试输出含 `Tests run: N, Failures: 0, Errors: 0`。
来源：`CLAUDE.md` 第 5-27 行「Commands」章节 与第 140/142 行 `test_command:` / `build_command:` 项目元信息；Flyway 命令来源同处第 22-24 行。

---

## 验收标准

- **I-1**：`AiReplyIntentCatalogTest` 的双向守卫用例通过；且 `grep -c "verification_evidence" src/main/kotlin/.../AiReplyIntentCatalog.kt` 由 0 变为 ≥1。
- **I-2**：`AiReplyIntentCatalogTest` 的机械 parity 用例通过；且人工核对 V105 中三处 keywords **无一含 `programme`** 字样（`grep -c 'programme' V105__*.sql` 结果为 0）。
- **I-3**：`grep -n "requiresProfile" src/main/kotlin/.../AiReplyIntentCatalog.kt` 的命中行数与改动前一致（只有 `expertise.programme_fit` 一处显式 `true`）；`AiReplyContextServiceTest` 全绿。
- **I-4**：`ProgrammeIdentityFactsMigrationTest` 断言两条 `coverage_keys` 字面量通过。
- **I-5**：`ProgrammeIdentityFactsMigrationTest` 断言 `NOT EXISTS` / `NOT LIKE` / `updated_at = updated_at` 通过。
- **I-6**：`QaFactSelectionServiceTest` 的负向用例（`Which partner company…` 不绑 id=6）通过。
- **I-7**：`AiReplyIntentCatalogTest`、`QaFactSelectionServiceTest` 中的来信常量与本文件顶部逐字一致（diff 比对）。
- **I-8**：见 A-3 人工验收项；代码侧断言 `bootstrap` 的 catch 分支（:370 附近）与 `:381` 的 STALE 返回未被本计划改动（`git diff --stat` 中不出现该文件）。
- **N1/N3/N5**：执行「验证命令」节的全量测试命令通过。
- **N6**：`git status --porcelain src/main/resources/db/migration` 只出现 `V105__add_programme_identity_facts.sql` 一行且为新增（`??` 或 `A`）。

---

## 人工验收清单

### A-1: 三个新问题在工作台被识别并绑上事实
- 前置条件：V105 已执行；库中存在一封 inbound 邮件，正文为本文件顶部逐字来信（可用 AI 训练页的模拟来信，或直接插一条 `mail_record` INBOUND 记录）。
- 操作步骤：
  1. 打开该邮件的可信回复工作台。
  2. 进入「摘要与事实」页。
  3. 展开该摘要卡片。
- 预期结果：「对应事实」计数为 **5**，chip 依次包含 `Programme name and public visibility`、`Programme sponsorship and organising level`、`全职 / 兼职安排`、`薪资与资金支持`、`知识产权边界`；状态标签为 `GROUNDED · 依据充分`。
- 覆盖：需求描述 observable outcome 1、3

### A-2: 资质事实从不可达变为可取证
- 前置条件：同 A-1。另准备一封正文为 `Could you tell me which government body or institution supports this programme?` 的来信。
- 操作步骤：打开该邮件的工作台，展开摘要，查看绑定事实。
- 预期结果：绑定事实中出现 `Agency credentials and government cooperation`（显示名以库中 display_name 为准）与 `Programme sponsorship and organising level` 两条；改动前该问法绑定事实为 0 条。
- 覆盖：需求描述 observable outcome 2、I-1

### A-3: 存量工作台状态降级为 STALE 而非报错（回归）
- 前置条件：**在部署 V105 与目录改动之前**，先在测试环境对任意一封来信打开工作台，绑定至少 1 条事实并保存（产生 `trust_reply_workbench_state` 行）。随后部署本计划改动。
- 操作步骤：
  1. 部署后重新打开**同一封**来信的工作台。
  2. 观察顶部状态条。
- 预期结果：页面正常加载，不出现 500/422 错误弹窗；状态条显示「STALE：来源或依据已变化，旧锁定回答未恢复」；事实绑定回落为系统自动选择的结果。
- 覆盖：I-8、interaction point IP-3

### A-4: 合作形式关键词不吸走企业身份问法（回归）
- 前置条件：V105 已执行。
- 操作步骤：用正文 `Which partner company would I be matched with, and where is it based?` 的来信打开工作台，展开摘要。
- 预期结果：绑定事实中**不出现** `全职 / 兼职安排`（id=6）；出现的是 `Partner company information` 一类企业匹配事实。
- 覆盖：I-6、must-NOT-change N4 的语义边界

### A-5: 既有高频问法回归
- 前置条件：V105 已执行。
- 操作步骤：分别用 `Is the advisory role compensated?` 与 `Who owns the intellectual property?` 两封来信打开工作台。
- 预期结果：第一封绑定 `薪资与资金支持`，第二封绑定 `知识产权边界`；两封的事实条数与改动前一致（各 1 条）。
- 覆盖：must-NOT-change N3

### A-6: 运营 keywords 未被覆盖
- 前置条件：部署前已在后台 QA 规则页手动给 `Full-time and part-time options` 加过一个自定义关键词（如 `advisory setup`）。
- 操作步骤：部署 V105 后，在后台打开该规则查看 keywords。
- 预期结果：`advisory setup` 仍在，且新增了 `form of collaboration` / `forms of collaboration` / `how the collaboration works` 三个词；`updated_at` 未因本次迁移刷新为部署时间。
- 覆盖：I-5、must-NOT-change N4
