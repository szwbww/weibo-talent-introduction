# 子计划 01：补齐 lastPublicationYear 并重新分类

> 主计划：[`00-single-gate-master.md`](./00-single-gate-master.md)
> 依赖：无（与门禁改造完全解耦，可最先发布）
> 前置研究：主计划 CP-1 已完成（`counts_by_year` 实测存在）

---

## 需求描述

**Observable outcome**

1. enrichment 从 OpenAlex author 对象已返回的 `counts_by_year` 派生出**最后发表年份**并写入 ES，
   不新增任何 OpenAlex 请求。
2. 页面「发现专家」下拉新增一项「补采发表年份（一次性）」，跑完后约 4.1 万名缺该字段的专家补上真实年份。
3. 补采 + 一次全量重新分类后，`UNKNOWN` 显著下降（预期释放 1.5 万~2 万人到 `ACADEMIC_RND`），
   且该下降**可完全归因于 `lastPublicationYear` 的补齐**。

**What must NOT change**

1. `ExpertClassificationService` 的判定链、打分项、阈值、词表——一行不改（主计划 M-3）。
2. `ExpertClassificationService.VERSION` 不变，仍为 `rnd-v2-2026`。
3. 不传 `scope` 时 `enrichExistingExperts()` 的过滤器逐字不变（仍是 `buildEnrichmentFilters(cutoff)`）。
4. `institution`、`institutionType` 两个字段的值不被本计划改写。
5. `ExpertDiscoveryService.kt:800/871` 的 `minusDays(30)` 不动（主计划 M-4）。

**Out of scope**

- 打开 `OPENALEX_FETCH_WORKS_ENABLED`（`recentWorkTitles` 的 +25 仍恒为 0）。
- 改 `RECENT_PAPER_CUTOFF_YEAR = 2021`。
- 给 `citationCount` / `hIndex` 等其他打分输入做同类补齐。
- 任何门禁/筛选改动（属子计划 02/03/04）。

---

## 关键不变量

### Invariant I1-1: 取 `works_count > 0` 的**最大** year，不是数组首项
- Rule: `lastPublicationYear` = `counts_by_year` 中所有 `works_count > 0` 的元素的 `year` 最大值。
  数组顺序不可依赖——CP-1 实测样本为**升序**（2008 在前）。
- Applies to: `OpenAlexDataSource.parseAuthorEnrichmentFromNode()`。
- Violation consequence: 照抄 05A 的 I5a-2「取第一项」会写入该作者**最早**的发表年份，
  几乎必然 < 2021，`researchScore` 拿不到 +35，本计划收益归零且数据被污染。
- 来源: original（CP-1 实测）

### Invariant I1-2: `works_count == 0` 的年份不算发表年
- Rule: `counts_by_year` 元素可能出现 `works_count = 0`（只有被引、当年无产出）。
  这类元素必须排除后再取最大值。
- Applies to: 同上。
- Violation consequence: 把「当年只被引用」误当成「当年发表」，虚高年份，分类结果不可信。
- 来源: original

### Invariant I1-3: 空值即缺失，不写入该键
- Rule: `counts_by_year` 不存在、为空数组、或全部元素 `works_count = 0` 时，
  派生值为 `null`；`updateExpertAcademicFields` 的 `doc` 中**不写入** `lastPublicationYear` 键。
- Applies to: `AuthorEnrichment.lastPublicationYear`、`ExpertDiscoveryService.updateExpertAcademicFields`。
- Violation consequence: 写入 `null` 或 `0` 会覆盖发现时已有的真实年份，属数据破坏。
- 来源: K-（沿用 05A 的 I5a-3，同一写入函数同款语义）

### Invariant I1-4: enrichment 的值覆盖发现时的值
- Rule: 派生值非 null 时**无条件覆盖**已有的 `lastPublicationYear`（发现时由
  `toIndexMap` 从 `paper.pubYear` 写入）。OpenAlex 的作者级最后发表年份比
  「被发现的那一篇论文的年份」更准确。
- Applies to: `updateExpertAcademicFields`（`doc["lastPublicationYear"] = it`，与
  `disciplineCategory` / `institutionType` 同款 `?.let` 无条件写）。
- Violation consequence: 若加「仅当原值为空才写」的守卫，则发现时年份偏旧的存量专家永远修不好。
- 来源: original

### Invariant I1-5: 补采过滤器只选「有 enrichedAt 且缺年份」的人
- Rule: 新增的 `LAST_PUBLICATION_YEAR_BACKFILL` 口径为
  `must: exists enrichedAt` + `must_not: [exists lastPublicationYear, prefix orcidId "EMAIL-"]`。
- Applies to: `ExpertDiscoveryService.buildLastPublicationYearBackfillFilters()`。
- Violation consequence:
  漏掉 `exists enrichedAt` → 会把 9,643 个永久失败的 ORCID 每轮重扫一遍（实测失败率 93%）；
  漏掉 `EMAIL-` 排除 → 对无法查询的伪 ORCID 发起请求，白烧配额（[[K-enrichment-excludes-email-id-experts]]）。
- 来源: K-（沿用 05A-2 Part A 已验证的口径）

### Invariant I1-6: 默认口径逐字不变
- Rule: 不传 `scope` 或传 `DEFAULT` 时，过滤器必须仍是 `buildEnrichmentFilters(cutoff)` 的原返回值。
  新增枚举值只增不改，`when` 分支不得调整既有两支的表达式。
- Applies to: `enrichExistingExperts(scope)`（`:869-876`）。
- Violation consequence: 常规「补充学术数据」的行为漂移，且 `ExpertDiscoveryServiceTest` 中
  21 处无参调用的断言含义被静默改变。
- 来源: K-（05A-2 的 I5a2-2）

### Invariant I1-7: 尾部可空默认参数
- Rule: `AuthorEnrichment` 新增字段必须加在**最后一个位置**且带 `= null` 默认值。
- Applies to: `AuthorEnrichment`（`OpenAlexDataSource.kt:331-340`）。
- Violation consequence: 插入中间位置会让 `baseEnrichment` 等既有具名/位置构造点全线编译失败
  （[[K-openalex-author-full-object]] 已记录该范式）。
- 来源: K-openalex-author-full-object

### Invariant I1-8: 重新分类不改 VERSION，靠 `onlyPending:false` 触发
- Rule: 本计划补齐输入后需要重新分类，但**不得**变更 `VERSION`。重算通过一次
  `onlyPending:false` 的全量回填完成。
- Applies to: 第 3 步运维动作。
- Violation consequence: `ExpertClassificationBackfillService.pendingOnlyFilter():179-197`
  只按 version 选人、不看 `sourceFingerprint`（`grep -rn sourceFingerprint src/main` 显示
  它只有写入/读出/字段声明三处引用，无任何选择逻辑消费它），
  因此输入变了也不会被自动重算；而改 VERSION 会触发全池停发（主计划 M-3 禁止）。
- 来源: original

---

## 样式契约

### S1-1: 「补采发表年份（一次性）」下拉项
- **复用**：`class="dropdown-item"`（`styles.css:500-514`；hover `:516-519`；disabled `:521-524`）。
  禁止自造近似样式，禁止 inline style，禁止新增 class。
- **DOM 结构**：插在既有「补采机构类型（一次性）」之后、`<hr class="dropdown-divider">` 之前，
  逐字如下（缩进 28 空格，与相邻兄弟节点一致）：

```html
                            <button class="dropdown-item" id="enrichYearBackfillBtn" onclick="handleDiscoverOption('enrichYearBackfill')">补采发表年份（一次性）</button>
```

- **改动前基线**（`index.html:596-602`，本次只在第 599 行之后插入一行，其余逐字不动）：

```html
                            <button class="dropdown-item" id="promoteRawBtn" onclick="handleDiscoverOption('quick')">快速晋升（扫描 RAW）</button>
                            <button class="dropdown-item" id="discoverDeepBtn" onclick="handleDiscoverOption('deep')">深度发现（外部数据源）</button>
                            <button class="dropdown-item" onclick="handleDiscoverOption('enrich')">补充学术数据（OpenAlex）</button>
                            <button class="dropdown-item" id="enrichBackfillBtn" onclick="handleDiscoverOption('enrichBackfill')">补采机构类型（一次性）</button>
                            <hr class="dropdown-divider">
                            <button class="dropdown-item" onclick="handleDiscoverOption('revalidate')">重新验证候选人</button>
```

- **禁止项**：修改 `.dropdown-item` / `.dropdown-divider` 规则块；调整既有四个按钮的任何属性；
  新增任何 CSS（本条不需要新样式）。

---

## 现状审计

### CANDIDATE ES 索引（`orcid_info_candidate`）

- Mapping：`dynamic:false`；`lastPublicationYear` 声明为 `{"type":"integer"}`，
  三份 mapping（raw/candidate/application）逐字相同（2026-08-28 实测）。
- **写路径（全量 grep：`grep -rn "lastPublicationYear" src/main/kotlin`）**
  1. `ExpertDiscoveryService.toIndexMap()` — 发现入库时从 `paper.pubYear` 写入（RAW）。
  2. `ExpertDiscoveryService.buildProfile():746-757` — 构造 profile 时带上。
  3. **本计划新增**：`updateExpertAcademicFields():1092-1123` — enrichment 时覆盖写入。
     该函数当前写 11 个键（`hIndex`/`citationCount`/`updatedAt`/`enrichedAt`/`enrichmentSource`
     /`worksCount`/`researchFields`/`recentWorkTitles`/`patentTitles`/`disciplineCategory`
     /`institutionType`），**不含** `lastPublicationYear`。
- **读路径**
  1. `ExpertSearchService.sourceFields()` 已含该字段；`toExpertProfile()` 已解析。
  2. `ExpertClassificationService.normalizeInputs()` → `researchScore():140` 用它判 `>= 2021`（+35）。
  3. `ExpertIndexResponse.from()` 已透出到前端列表 API。
- **Interaction point**：写路径 3（新增，enrichment）→ 读路径 2（分类打分）。
  这是本计划的核心交互点：enrichment 写入后，**必须显式触发重新分类**才会体现到 `expertClassification`
  （见 I1-8）。

### OpenAlex 数据源

- 两条解析路径共用 `parseAuthorEnrichmentFromNode(node, fetchWorksAndPatents)`（`:283`）：
  单专家 `enrichAuthor():114-120`（`true`）、批量 `batchEnrichByOrcids():206-250`（`false`）。
- 两条路径都**不带 `select=`**，返回完整 author 对象（[[K-openalex-author-full-object]]），
  `counts_by_year` 已在响应中 ⇒ **两条路径均为零额外请求**
  （按 [[K-openalex-fetch-works-gated]] 的要求逐路径说明；与 `recentWorkTitles` 的情况不同，
  后者需要额外的 works 请求且受 `fetchWorksEnabled` 门控）。
- `AuthorEnrichment`（`:331-340`）当前 8 个字段，后 5 个为尾部可空默认参数。

### enrichment 口径与入口

- `EnrichmentScope`（`:1319`）当前两值：`DEFAULT`、`INSTITUTION_TYPE_BACKFILL`。
- `enrichExistingExperts(scope)`（`:869`）用 `when` 选过滤器（`:873-876`）。
- `getEnrichmentStats()`（`:799-811`）返回 4 字段，含 `institutionTypePending`。
- 控制器 `ExpertDiscoveryController.enrichExperts()`（`:216-219`）已有
  `@RequestParam(required = false) scope: EnrichmentScope?`，**新增枚举值无需改控制器**。
- 前端 `handleDiscoverOption()`（`app.js:5800-5812`）按 mode 分支；
  `handleEnrichExperts(scope)`（`:5816`）把 scope 存进模块级 `enrichScopeQuery`，
  由 `executeEnrichExperts()`（`:5836`）拼进 URL。

### 前端样式盘点

- 可复用 class：`.dropdown-item` — `styles.css:500-514`（+ hover `:516-519`、disabled `:521-524`）；
  `.dropdown-divider` — `styles.css:526-530`。
- 设计基准 token（`.dropdown-item` 实值）：`padding: 8px 10px`、`font-size: 12px`、
  `font-weight: 500`、`color: var(--text-main)`、`border-radius: var(--radius-sm)`、
  `background: transparent`、hover 背景 `var(--bg-sidebar-hover)`。
- DOM 约定：下拉项一律 `<button class="dropdown-item" onclick="handleDiscoverOption('<mode>')">`，
  一次性任务带 `id`（如 `enrichBackfillBtn`）。
- 改动前基线：见 S1-1。

---

## 实现方案

### Task 1：解析 `counts_by_year`（I1-1、I1-2、I1-3、I1-7）

`OpenAlexDataSource.parseAuthorEnrichmentFromNode()`（`:283`）内新增，与既有
`institutionType` 的解析并列：

```kotlin
// I1-1/I1-2：取 works_count > 0 的最大 year；数组顺序不可依赖（CP-1 实测为升序）。
// I1-3：无该键、空数组、或全部 works_count = 0 时为 null。
val lastPublicationYear = node.path("counts_by_year")
    .filter { it.path("works_count").asInt(0) > 0 }
    .mapNotNull { it.path("year").let { y -> if (y.isInt) y.asInt() else null } }
    .maxOrNull()
```

`AuthorEnrichment`（`:331-340`）**尾部**新增 `val lastPublicationYear: Int? = null`（I1-7），
并在 `parseAuthorEnrichmentFromNode` 的返回构造中传入。

### Task 2：enrichment 写入（I1-3、I1-4）

`ExpertDiscoveryService.updateExpertAcademicFields()`（`:1092-1123`），
在 `institutionType` 那一行之后新增一行，形态与之逐字同款：

```kotlin
// I1-3: null 时不写入该键，避免覆盖发现时的真实值；I1-4: 非 null 时无条件覆盖。
enrichment.lastPublicationYear?.let { doc["lastPublicationYear"] = it }
```

### Task 3：补采口径（I1-5、I1-6）

新增私有方法，与 `buildInstitutionTypeBackfillFilters()`（`:842`）并列、互不调用：

```kotlin
/** I1-5：只针对 OpenAlex 认得（有 enrichedAt）且尚无发表年份的人。 */
private fun buildLastPublicationYearBackfillFilters(): List<Map<String, Any>> = listOf(
    mapOf("bool" to mapOf(
        "must" to listOf(mapOf("exists" to mapOf("field" to "enrichedAt"))),
        "must_not" to listOf(
            mapOf("exists" to mapOf("field" to "lastPublicationYear")),
            mapOf("prefix" to mapOf("orcidId" to "EMAIL-"))
        )
    ))
)
```

`EnrichmentScope`（`:1319`）新增第三个值 `LAST_PUBLICATION_YEAR_BACKFILL`；
`enrichExistingExperts` 的 `when`（`:873-876`）新增一支，**既有两支逐字不动**（I1-6）。

`getEnrichmentStats()`（`:799-811`）新增 `lastPublicationYearPending` 字段
（`EnrichmentStats` 尾部追加，与 `institutionTypePending` 同款），用于判断何时跑完。

### Task 4：前端入口（S1-1）

- `index.html`：按 S1-1 逐字插入一行。
- `app.js` `handleDiscoverOption()`（`:5800`）新增分支，与既有 `enrichBackfill` 逐字同款：

```javascript
    } else if (mode === 'enrichYearBackfill') {
        await handleEnrichExperts("LAST_PUBLICATION_YEAR_BACKFILL");
```

### Task 5：测试

`OpenAlexDataSourceTest`：
1. 修改 `author-response-sample.json`，加入 `counts_by_year`（含一个 `works_count: 0` 的年份
   且该年份**大于**所有有产出的年份，用于同时覆盖 I1-1 与 I1-2），断言
   `enrichAuthor(...).lastPublicationYear` 等于有产出的最大年份。
2. 新增：数组**降序**输入也取到同一个最大值（证明不依赖顺序，I1-1）。
3. 新增：无 `counts_by_year` 键 → null；空数组 → null；全部 `works_count: 0` → null（I1-3）。
4. 在既有 `batchEnrichByOrcids parses multiple authors from search response` 的内联 JSON 里
   给第一个作者加 `counts_by_year`，断言批量路径同样解析到（证明共用解析点）。

`ExpertDiscoveryServiceTest`：
5. 新增：`enrichment.lastPublicationYear = 2026` 时，`_update` 的 doc 含 `"lastPublicationYear" to 2026`
   （仿既有 `writes institutionType unconditionally overwriting prior value (I5a-8)`）。
6. 新增：`lastPublicationYear = null` 时 doc **不含**该键（仿既有 `omits institutionType key when null`）。
7. 回归：既有 21 处无参 `svc.enrichExistingExperts()` 调用零改动通过（I1-6）。

### Task 6（运维，无代码）：三步执行

1. 跑「补采发表年份（一次性）」直到 `lastPublicationYearPending` 收敛。
2. 跑一次 `onlyPending:false` 的全量分类回填（I1-8）：

```bash
curl -sS -b "$COOKIE_JAR" -X POST "$BASE_URL/expert-classification/backfill" \
  -H 'Content-Type: application/json' -d '{
   "level":"CANDIDATE","mode":"DRY_RUN","version":"rnd-v2-2026",
   "batchSize":500,"delayMs":250,"onlyPending":false}'
```
   DRY_RUN 看类型分布变化，确认符合预期后把 `mode` 改成 `EXECUTE` 并加
   `"confirmation":"EXECUTE_CANDIDATE:rnd-v2-2026"`。
3. 重跑 `docs/runbooks/institutiontype-backfill-run.md` 第 6 步的分布脚本，对比前后类型分布。

---

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/.../discovery/service/OpenAlexDataSource.kt` | 解析 `counts_by_year`；`AuthorEnrichment` 尾部加字段 |
| 2 | `src/main/kotlin/.../discovery/service/ExpertDiscoveryService.kt` | 写入 doc；新增补采过滤器与枚举值；`EnrichmentStats` 加字段 |
| 3 | `src/main/resources/static/index.html` | 按 S1-1 插入一行 |
| 4 | `src/main/resources/static/app.js` | `handleDiscoverOption` 新增一支 |
| 5 | `src/test/resources/openalex/author-response-sample.json` | fixture 加 `counts_by_year` |
| 6 | `src/test/kotlin/.../discovery/service/OpenAlexDataSourceTest.kt` | Task 5 第 1~4 条 |
| 7 | `src/test/kotlin/.../discovery/service/ExpertDiscoveryServiceTest.kt` | Task 5 第 5~7 条 |
| 8 | `src/test/kotlin/.../discovery/controller/ExpertDiscoveryControllerTest.kt` | 新枚举值的 scope 透传断言 |

合计 8 个文件；子系统 2 个（discovery 后端 / 前端静态资源）。

---

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。
> JS 测试由 `pom.xml:186-232` 的 `exec-maven-plugin` 在 `test` 阶段执行，
> 也可脱离 Maven 单跑（[[K-js-tests-run-via-exec-plugin]]）。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='OpenAlexDataSourceTest,ExpertDiscoveryServiceTest,ExpertDiscoveryControllerTest'

# 分类器回归（本计划不得改变任何分类行为，主计划 M-3）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=ExpertClassificationServiceTest

# 前端语法与用例
node --check src/main/resources/static/app.js
node --test src/test/js/*.test.js

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：Maven 输出 `Tests run: N, Failures: 0, Errors: 0` 且 `BUILD SUCCESS`；
`node --test` 退出码 0 且输出含 `# fail 0`；`git diff --check` 无输出。
来源：`CLAUDE.md:5-20`「Commands」章节 + [[K-js-tests-run-via-exec-plugin]]（实测 node v22）。

---

## 验收标准

- **I1-1**：单测断言升序与降序两种 `counts_by_year` 输入取到同一个最大 year。
- **I1-2**：单测断言含 `works_count: 0` 的更大年份不被选中。
- **I1-3**：单测断言无键 / 空数组 / 全零三种输入均产出 `null`，且 doc 不含该键。
- **I1-4**：单测断言 `doc["lastPublicationYear"]` 无条件写入（无「原值为空才写」的守卫）。
- **I1-5**：grep `buildLastPublicationYearBackfillFilters` 的实现，确认同时含
  `exists enrichedAt`、`must_not exists lastPublicationYear`、`must_not prefix orcidId "EMAIL-"` 三项。
- **I1-6**：`git diff` 显示 `buildEnrichmentFilters` 与 `when` 既有两支逐字未改；
  `ExpertDiscoveryServiceTest` 的 21 处无参调用零改动通过。
- **I1-7**：`AuthorEnrichment` 新字段位于最后一行且带 `= null`。
- **I1-8**：`git diff` 显示 `ExpertClassificationService.kt` 零改动（含 `VERSION`）。
- **S1-1**：`git diff src/main/resources/static/index.html` 只有**一行新增**，
  内容与契约代码块逐字一致；`git diff src/main/resources/static/styles.css` 为空。
- 回归：执行「验证命令」节的全量测试命令与构建命令通过。

---

## 人工验收清单

### A1-1: 补采能跑起来且目标集正确
- 前置条件: 本计划已发布到线上。
- 操作步骤: 1. `GET /talent/api/expert-discovery/enrich/stats`，记下 `lastPublicationYearPending`；
  2. 页面「发现专家 → 补采发表年份（一次性）」；3. 观察任务弹窗进度。
- 预期结果: `lastPublicationYearPending` 初值在 **35,000 ~ 45,000** 之间（实测缺失 41,409，
  扣除无 `enrichedAt` 的部分）；任务启动后 `totalCount` 等于该值。
  若初值 < 10,000 或 > 60,000，说明过滤器口径错误，停止并回报。
- 覆盖: I1-5、需求描述第 2 条

### A1-2: 补采期间失败率显著低于常规轮
- 前置条件: A1-1 已启动，已处理 ≥ 2,000 条。
- 操作步骤: `GET /talent/api/task-progress/EXPERT_ENRICHMENT`，看 `details.failureReasons`。
- 预期结果: `ORCID_NOT_IN_OPENALEX` 占已扫描数的比例 **< 20%**（对照：常规 `DEFAULT` 口径实测为 93%）。
  若 > 50%，说明 `exists enrichedAt` 未生效，立即暂停并回报。
- 覆盖: I1-5

### A1-3: 年份写入正确且覆盖了旧值
- 前置条件: A1-1 跑完。
- 操作步骤: 1. 任取一名补采后有 `lastPublicationYear` 的专家，记下其 `orcidId` 与该值；
  2. `curl 'https://api.openalex.org/authors/https://orcid.org/<该 orcid>?mailto=wuwei@qftechtalent.com'`
  查其 `counts_by_year`。
- 预期结果: ES 中的 `lastPublicationYear` **等于** `counts_by_year` 里 `works_count > 0` 的最大 year。
  不等于首元素的 year（除非首元素恰为最大值）。
- 覆盖: I1-1、I1-2

### A1-4: 机构名与机构类型未被改写（回归）
- 前置条件: 补采前记录 5 名专家的 `institution` 与 `institutionType`。
- 操作步骤: 补采后重新读取同样 5 条。
- 预期结果: 两个字段**逐字未变**。
- 覆盖: must-NOT-change 第 4 条

### A1-5: 常规「补充学术数据」行为未变（回归）
- 前置条件: 补采任务已结束（并发锁释放）。
- 操作步骤: 1. 记录 `enrich/stats` 的 `pending`；2. 点「补充学术数据（OpenAlex）」；
  3. 看任务弹窗的 `totalCount`。
- 预期结果: `totalCount` 等于 `pending`（不是 `lastPublicationYearPending`），
  说明默认口径未被新枚举值污染。
- 覆盖: must-NOT-change 第 3 条、I1-6

### A1-6: 重新分类后 UNKNOWN 下降，且变化可归因
- 前置条件: A1-1 跑完，全量回填（Task 6 第 2 步）已 EXECUTE 完成。
- 操作步骤: 1. 回填前后各跑一次分类分布聚合
  （`terms` on `expertClassification.type`，全 CANDIDATE 层）；2. 对比。
- 预期结果: `UNKNOWN` 从 **47,835** 下降至少 **10,000**；下降量主要转入 `ACADEMIC_RND`；
  `OUT_OF_SCOPE` 与 `SERVICE_ONLY` 的变化量应 **< 500**（这两类由文本规则判定，
  不受 `lastPublicationYear` 影响——若它们大幅变动，说明有非预期改动，需排查）。
- 覆盖: 需求描述第 3 条、I1-8、must-NOT-change 第 1 条

### A1-7: 下拉项样式与相邻项一致（UI 目测）
- 前置条件: 前端已发布。
- 操作步骤: 打开「发现专家」下拉，肉眼对比新增项与「补采机构类型（一次性）」。
- 预期结果: 字号 12px、内边距 8px/10px、字重 500、颜色与相邻项一致；
  hover 时背景变为与相邻项相同的浅色；位置在「补采机构类型」之下、分隔线之上。
- 覆盖: S1-1
