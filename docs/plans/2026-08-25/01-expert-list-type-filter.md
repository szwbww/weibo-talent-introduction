# 子计划 01：专家列表研发类型筛选与展示

## 需求描述

可观察结果：专家漏斗列表页新增「研发类型」多选筛选（六个枚举值 + 未分类），选中后列表只返回命中类型的专家；每行显示该专家的类型标记与生产/科研两个分数。

必须保持不变：

- 既有 `discipline`（学科分类）筛选行为逐字不变。
- 未选任何类型时，列表接口的 ES 查询与改动前逐字相同。
- 现有排序、分页、标签/地区/邮箱服务商下拉的取值范围不变。
- `ExpertContactView` 既有字段不改名、不删除。

范围外：

- 批量发送端筛选（子计划 02）。
- 修改 `#expertDisciplineFilter` 的 option 取值或 `resolveDisciplineCategory` 的归类逻辑（仅改显示文案，见 S-3）。
- 让 tag / region / emailDomain 三个聚合下拉的计数随类型筛选变化（见「现状审计」的有意识取舍）。
- 透出 `positiveEvidence` / `negativeEvidence` 证据 code。

## 关键不变量

### Invariant I1-1: 类型白名单单一权威
- Rule: 允许的筛选取值集合只在 `ExpertSearchService` 声明一处，取值为 `ExpertType` 六个枚举名 + 字面量 `"UNCLASSIFIED"`；`UNCLASSIFIED` 的语义是 `expertClassification.type` 字段**不存在**，不是某个字符串值。禁止在控制器或前端另建名单。
- Applies to: `ExpertSearchService.ALLOWED_EXPERT_TYPES`、`expertTypesFilter`、`ExpertIndexController.listExperts`。
- Violation consequence: 与 `discipline` 维度同款复发——界面能选、一条也查不到且无报错。
- 来源: K-discipline-unclassified-filter-bypasses

### Invariant I1-2: 空集合不追加 filter
- Rule: `expertTypesFilter(emptyList())` 必须返回 `null`；`buildExpertFilters` 在空集合时不得向 filter 数组追加任何元素。禁止产出 `should: [] + minimum_should_match: 1`。
- Applies to: `ExpertSearchService.expertTypesFilter`、`ExpertSearchService.buildExpertFilters`。
- Violation consequence: 未选类型时列表恒空。
- 来源: K-batch-multi-value-filter-seams

### Invariant I1-3: 多值必须包成单个 should
- Rule: 多个类型之间是 OR。ES 表达必须是**一个** `bool.should` + `minimum_should_match: 1` 的 Map，不得把多个 term 平铺进 `bool.filter`（那是 AND，恒零命中）。
- Applies to: `ExpertSearchService.expertTypesFilter`。
- Violation consequence: 勾选两个及以上类型时结果恒为 0。
- 来源: K-batch-multi-value-filter-seams

### Invariant I1-4: 尾部可空默认参数
- Rule: `buildExpertFilters` 与 `searchExperts` 的新参数必须是**尾部**且带默认值 `emptyList()`。`buildExpertFilters` 现有 4 个调用点（`:285`、`:949`、`:994`、`:1101`）中的后三个不传该参数，行为不得改变。
- Applies to: `ExpertSearchService.kt`。
- Violation consequence: 打断三个聚合查询的既有调用，编译失败或聚合口径漂移。
- 来源: original

### Invariant I1-5: 前端注册点全集
- Rule: 新增筛选控件必须在 `app.js` 完成全部注册；注册点全集以 `grep -n "expertDisciplineFilter" src/main/resources/static/app.js` 的结果为准，改前必须重跑该 grep 复核，不得按本计划行号直接定位。
- Applies to: `app.js`。
- Violation consequence: 漏注册表现为「选了没反应」或「筛选徽章不计数」，无报错。
- 来源: K-expert-filter-registration-sites（本轮复核发现该条目行号已大幅漂移，见现状审计）

## 样式契约

### S-1: 研发类型多选控件
- 复用：`.tag-select`（`styles.css:576-581`）+ `.tag-chip`（`styles.css:583-600`）+ `.tag-chip:hover`（`:602-606`）+ `.tag-chip.active`（`:608-613`）。外层复用 `.toolbar-label`（`styles.css:467-477`）。
  该组合在同一工具栏内已有活体先例：`#hasFieldTagSelect`（`index.html:564-572`）。
  **禁止**执行 agent 自造近似样式，或改用 `<select multiple>`。
- 新增：无新增 CSS。
- DOM 结构：插入位置为 `index.html` 中 `#expertDisciplineFilter` 所在 `<label class="toolbar-label">` 结束标签之后、`H-Index ≥` 那一段之前。骨架逐字如下：

```html
<span class="toolbar-label">
    研发类型:
    <span class="tag-select" id="expertTypeTagSelect">
        <button type="button" class="tag-chip" data-value="PRODUCTION_RND">生产研发</button>
        <button type="button" class="tag-chip" data-value="ACADEMIC_RND">学术科研</button>
        <button type="button" class="tag-chip" data-value="HYBRID_RND">混合研发</button>
        <button type="button" class="tag-chip" data-value="SERVICE_ONLY">纯服务</button>
        <button type="button" class="tag-chip" data-value="OUT_OF_SCOPE">医学越界</button>
        <button type="button" class="tag-chip" data-value="UNKNOWN">未知</button>
        <button type="button" class="tag-chip" data-value="UNCLASSIFIED">未分类</button>
    </span>
</span>
```

  注意外层是 `<span class="toolbar-label">` 不是 `<label>`——`#hasFieldTagSelect`（`index.html:564`）与 `#expertGateTemplateFilter`（`:573`）两处含按钮的控件都用 `<span>`，用 `<label>` 会触发隐式聚焦转移。
- 禁止项：inline style；新增任何 class；修改 `.tag-chip` 既有规则块。

### S-2: 列表行类型标记
- 复用：`.tag-chip`（`styles.css:583-600`），不加 `.active`，即灰底描边态。
- 新增：无新增 CSS。类型的语义色差异**本轮不做**——加语义色需要新增 3 个 class 与暗色适配，超出「无新增 CSS」的成本约束；先用统一灰底 chip + 中文类型名，文本本身已可区分。
- DOM 结构：在列表行既有标签区域内追加：

```html
<span class="tag-chip" title="生产分 {productionScore} / 科研分 {researchScore}">{中文类型名}</span>
```

  `title` 内两个分数用 `escapeHtml` 转义后插值；类型缺失时**不渲染该元素**，不渲染占位文本。
- 禁止项：inline style；新增 class；用 emoji 或色块代替文本。

### S-3: 学科分类下拉文案纠正
- 复用：不改任何 CSS，只改 `index.html:529-532` 三个 `<option>` 的文本内容。
- 新增：无。
- DOM 结构：`value` 属性**逐字不变**，仅改可见文本：

```html
<option value="">全部学科</option>
<option value="STEM">理工科（含医学）</option>
<option value="HUMANITIES">文社科</option>
<option value="UNCLASSIFIED">未分类</option>
```

  理由见现状审计：`OpenAlexDataSource.resolveDisciplineCategory:303-305` 把 `Health Sciences` 计入 STEM，现有文案「理工科」会让运营误以为选它即可排除医学。
- 禁止项：改动 `value`；改动 `#expertDisciplineFilter` 的 id；顺手修改 `resolveDisciplineCategory`。

## 现状审计

### CANDIDATE / RAW / APPLICATION ES —— `expertClassification` 读路径
- Schema/mapping: 三份 `src/main/resources/es/orcid_info_*.json` 均 `dynamic:false`；`expertClassification` 顶层对象已由 2026-08-24 计划 01 声明，子字段含 `type(keyword)`、`sendable(boolean)`、`productionScore(integer)`、`researchScore(integer)`、`version(keyword)`。
- Write paths（本子计划**全部不碰**，仅列出以证明读到的数据从何而来）:
  1. `ExpertIndexWriterService.bulkUpdateExpertClassifications`（序列化在 `classificationNode:352-363`）—— 回填任务唯一写入点。
  2. `ExpertIndexWriterService.promoteToCandidate` / `promoteToApplication` —— `_source` 全量复制，自动透传（来源: K-promotion-source-passthrough，本轮复核成立）。
  3. `ExpertRevalidationService.promoteRawToCandidate:241-261` —— 快速晋升整文档复制（子计划 03 将在此叠加写入）。
- Read paths:
  1. `ExpertSearchService.sourceFields()`（`:588` 一带）已含 `expertClassification`，无需改动。
  2. `ExpertSearchService.toExpertProfile()` 已解析该对象为 `ExpertProfile.expertClassification`（`ExpertProfile.kt:32`）。
  3. `ExpertIndexController.listExperts:53-72` → `ExpertContactView`（`:388-400` 字段声明、`:438-448` 装配）—— **当前不透出分类字段，是本子计划要补的缺口**。
- Interaction points:
  - 回填/晋升写入 `expertClassification` → 列表接口读出并展示。若 `ExpertContactView` 漏加字段，ES 有数据而页面永远空白。
  - 实测证据：`grep -c "expertClassification" src/main/resources/static/app.js src/main/resources/static/index.html` 结果均为 **0**，即后端已落地、前端零消费。

### ES 查询构造 seam
- `ExpertSearchService.buildExpertFilters`（`:1041`，private）是列表与三个聚合查询的**唯一** filter 组装点，4 个调用点：`:285`（`searchExperts`）、`:949`、`:994`、`:1101`。
- 只有 `:285` 传 `discipline`；`:949/:994/:1101` 三个聚合调用不传，依赖默认值 `null`。
- 既有多值范式：`ExpertSearchService.operatorStatusesFilter`（`:255-264`）——`map/trim/filter/distinct` → 空集合返回 `null` → 否则单个 `bool.should` + `minimum_should_match: 1`。`expertTypesFilter` 逐字照抄此结构。
- 既有「字段不存在」范式：`disciplineFilter`（`:96-106`）的 `UNCLASSIFIED` 分支 → `bool.must_not.exists`。
- Interaction points: 新参数进 `buildExpertFilters` 后，三个聚合调用点必须因默认值而行为不变（I1-4）。

**有意识取舍（对 K-filter-option-scope-parity 的显式拒绝）**：该知识条目要求「选项加载与筛选执行共享同一 scope」。本子计划**不**把 `expertTypes` 传给 `:949/:994/:1101` 三个聚合，因此勾选类型后，标签/地区/邮箱服务商下拉的计数不会随之收窄。理由：既有 `discipline` 维度同样没有传（`:285` 之外的三个调用点均不传 discipline），本轮保持一致可把改动限制在 1 个调用点；若一并处理，需同时改 `app.js:4250-4262` 的 region 下拉加载与 `:4423-4428` 的 `loadExpertTagOptions` 入参，文件与回归面翻倍。该缺口在改动前后同等存在，不是本轮引入的回归。

### 前端注册点（2026-08-25 实测）
`grep -n "expertRegionFilter" src/main/resources/static/app.js` 命中 7 处，去掉两处 tag-option 传参后，新增筛选必须同步的注册点为 **4 处**：

| # | 位置 | 内容 |
|---|---|---|
| 1 | `app.js:4855-4860` + `:5009` 一带 | `loadContacts` 取值与 `params.set` |
| 2 | `app.js:12128-12136`（`initExpertGateFilter`，函数起始 `:12070`） | 门禁匹配计数的参数构造 |
| 3 | `app.js:11854-11869`（`updateFilterBadge`） | 活跃筛选计数数组 |
| 4 | `app.js:11878-11884` | change 监听 id 数组 |

**对 K-expert-filter-registration-sites 的复核更正（两处已失效，Phase 6 须回写）**：
- 该条目所列第 ② 处 `collectBatchMailContactIds` 在当前代码中**不存在**（`grep -n "collectBatchMailContactIds" app.js` 零命中）；等价的参数构造点已迁入 `initExpertGateFilter`（`:12070`）。
- 该条目所列第 ③ 处「筛选摘要文案 `parts.push` 系列」在当前代码中**不存在**（`grep -n "parts.push" app.js` 的 19 处命中全部属于 AI 回复面板与回复拼装，无一属于专家筛选摘要）。
- 行号漂移再次确认：条目记 `.toolbar-label` 在 `styles.css:431`，实测在 **`:467`**（更早一版记 `:353`）。

因 `.tag-select` 是按钮组而非 `<select>`，注册点 4（change 监听）不适用于本控件；必须改为在 chip 点击处理函数内主动调用 `reloadContactsFromStart()`——`#hasFieldTagSelect` 的既有事件绑定即此范式，照抄。

### 前端样式盘点
- 可复用 class：
  - `.toolbar-label` — `styles.css:467-477` — 工具栏筛选项外壳
  - `.tag-select` — `styles.css:576-581` — chip 组容器（`inline-flex; gap:4px; flex-wrap:wrap`）
  - `.tag-chip` — `styles.css:583-600` — 单个 chip
  - `.tag-chip:hover` — `styles.css:602-606`
  - `.tag-chip.active` — `styles.css:608-613` — 选中态
- 设计基准 token 实值（取自上述规则块）：chip 高 `26px`、内边距 `0 10px`、字号 `11px`、字重 `500`、圆角 `var(--radius-lg)`、边框 `1px solid var(--border)`、未选文字色 `var(--text-muted)`、hover 边框与文字 `var(--primary)`、hover 背景 `var(--primary-light)`、选中背景与边框 `var(--primary)`、选中文字 `#fff`、选中阴影 `0 1px 3px rgba(var(--primary-rgb), 0.3)`、过渡 `all 0.15s ease`；`.toolbar-label` 字号 `11px`、`text-transform: uppercase`、`letter-spacing: 0.3px`、`gap: 6px`、色 `var(--text-muted)`。
- DOM 结构约定：筛选控件位于 `index.html` 的 `.toolbar-group` 内；单选用 `<label class="toolbar-label"><select id="...">`，含按钮的多选用 `<span class="toolbar-label"><span class="tag-select" id="...">`（`index.html:564-572` 与 `:573-580` 两处先例）。
- 改动前基线（`index.html:525-532`，学科分类下拉，S-3 将只改其可见文本）：

```html
<label class="toolbar-label">
    学科分类:
    <select id="expertDisciplineFilter">
        <option value="">全部学科</option>
        <option value="STEM">理工科</option>
        <option value="HUMANITIES">文社科</option>
        <option value="UNCLASSIFIED">未分类</option>
    </select>
</label>
```

## 实现方案

### Task 1：ES 筛选表达（I1-1、I1-2、I1-3）

修改文件：`src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt`

在 companion object 内、`disciplineFilter`（`:96`）之后新增：

- `val ALLOWED_EXPERT_TYPES: Set<String>` = `ExpertType.values().map { it.name }.toSet() + "UNCLASSIFIED"`。**必须从枚举派生**，不得手写六个字符串（I1-1、M-2）。
- `fun expertTypesFilter(types: List<String>): Map<String, Any>?`：
  - `map { it.trim() }.filter { it.isNotEmpty() }.distinct()`，空则 `return null`（I1-2）。
  - 逐项 `require(it in ALLOWED_EXPERT_TYPES)`，越界 fail-fast（照 `disciplineFilter:97` 的 require 范式）。
  - 返回单个 `mapOf("bool" to mapOf("should" to values.map { expertTypePredicate(it) }, "minimum_should_match" to 1))`（I1-3）。
- `private fun expertTypePredicate(type: String): Map<String, Any>`：
  - `"UNCLASSIFIED"` → `mapOf("bool" to mapOf("must_not" to listOf(mapOf("exists" to mapOf("field" to "expertClassification.type")))))`
  - 其余 → `mapOf("term" to mapOf("expertClassification.type" to type))`
  - **必须是纯谓词**：不得在分支内混入 `exists email` 之类的 AND 语义条件，否则其余 should 分支会绕过它（来源: K-batch-multi-value-filter-seams）。

### Task 2：接入查询构造（I1-4）

同文件：

- `buildExpertFilters`（`:1041`）签名末尾追加 `expertTypes: List<String> = emptyList()`；函数体内在既有 `discipline` 处理之后追加 `expertTypesFilter(expertTypes)?.let { filters.add(it) }`。
- `searchExperts`（`:267-280`）签名末尾追加 `expertTypes: List<String> = emptyList()`，并在 `:285` 的调用中透传。
- `:949`、`:994`、`:1101` 三个调用点**不改**（I1-4 与现状审计的有意识取舍）。

### Task 3：接口透出（I1-1）

修改文件：`src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt`

- `listExperts`（`:53`）参数末尾追加 `@RequestParam(required = false) expertType: List<String>? = null`，在 `:69-72` 的 `searchExpertsCall` 中以 `expertType.orEmpty()` 透传。
- `ExpertContactView`（`:388-400`）新增三个尾部可空字段：`expertType: String? = null`、`expertSendable: Boolean? = null`、`expertProductionScore: Int? = null`、`expertResearchScore: Int? = null`。
- 装配处（`:438-448`）赋值：`expert.expertClassification?.type?.name` 等；`expertClassification` 为 null 时四个字段均为 null。
- **不透出** `positiveEvidence` / `negativeEvidence`（范围外）。

### Task 4：前端控件与注册（I1-5、S-1、S-3）

修改文件：`src/main/resources/static/index.html`

- 按 S-1 插入 `#expertTypeTagSelect` 骨架。
- 按 S-3 改三个 option 的可见文本。

修改文件：`src/main/resources/static/app.js`

- 新增 `expertTypeActiveValues()`：读 `#expertTypeTagSelect` 内 `.tag-chip.active` 的 `data-value` 数组（照 `initExpertGateFilter:12079-12090` 的 `gateChips` / `gateActiveValues` 范式）。
- 绑定 chip 点击：toggle `.active` 后主动调 `reloadContactsFromStart()`（`.tag-select` 无 `change` 事件，来源: K-batch-picker-comma-delimited-contract 的同类结论）。
- 注册点 1（`:4855-4860` 取值 + `:5009` 一带 `params.set`）：多值用 **重复 `params.append("expertType", v)`**，不用逗号串——后端形参是 `List<String>`，Spring 按重复参数名绑定。
- 注册点 2（`:12128-12136`）：同样 append。
- 注册点 3（`:11854-11869`）：数组中追加 `expertTypeActiveValues().length > 0`。
- 注册点 4（`:11878-11884`）：**不加**本控件 id（非 `<select>`，无 change 事件）。
- 列表行渲染：按 S-2 追加类型 chip。

### Task 5：测试

修改文件：`src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt`

必须覆盖：空集合返回 null；单值产出 term；多值产出单个 `bool.should` 且 `minimum_should_match=1`；`UNCLASSIFIED` 产出 `must_not.exists`；越界值抛异常；`ALLOWED_EXPERT_TYPES` 与 `ExpertType.values()` 数量一致（防手写名单漂移）；`buildExpertFilters` 在 `expertTypes = emptyList()` 时 filter 数组与改动前逐字相同。

新增文件：`src/test/js/expertTypeFilter.test.js`

必须覆盖：chip toggle 后 `expertTypeActiveValues()` 返回值；未选中时 `params` 不含 `expertType` 键；选中两个时 `params.getAll("expertType")` 长度为 2；筛选徽章计数增减。照 `src/test/js/gateTemplateFilter.test.js` 的 DOM stub 范式。

> 注意 DOM stub 测试不会抛出真实浏览器才会抛的错（来源: K-dom-stub-tests-hide-dangling-refs），因此 `node --check` 与人工验收 A-4 不可省略。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | 新增白名单/filter/谓词三个成员；`buildExpertFilters`、`searchExperts` 各加一个尾部参数 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` | 列表端点加 `expertType` 参数；`ExpertContactView` 加 4 个尾部可空字段并装配 |
| 3 | `src/main/resources/static/index.html` | 插入 `#expertTypeTagSelect`；改学科下拉三个 option 可见文本 |
| 4 | `src/main/resources/static/app.js` | 取值函数 + chip 事件 + 3 处注册点 + 列表行 chip |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | 新增 filter 构造测试 |
| 6 | `src/test/js/expertTypeFilter.test.js` | 新增前端行为测试 |

合计 6 个文件，2 个子系统（后端查询/接口、前端列表），符合范围上限。

## 验证命令

> 本项目是 Kotlin + Spring Boot 2.7 / Java 11 Maven 工程，**必须用 JDK 11（zulu-11）**，裸 `mvn` 会构建失败（`CLAUDE.md:7`）。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关后端测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest

# 本计划新增的前端测试（单文件）
node --test src/test/js/expertTypeFilter.test.js

# 全部前端测试
node --test src/test/js/*.test.js

# 前端语法检查
node --check src/main/resources/static/app.js

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：Maven 退出码 0 且输出含 `Tests run: N, Failures: 0, Errors: 0`；node 退出码 0 且输出含 `# fail 0`；`git diff --check` 无输出。

来源：`CLAUDE.md:5-27`；K-js-tests-run-via-exec-plugin（`pom.xml:186-232`，2026-08-19 实测）。

## 验收标准

- I1-1: 单测断言 `ALLOWED_EXPERT_TYPES.size == ExpertType.values().size + 1`；grep 证明仓库中除 `ExpertClassification.kt` 与本文件外无第二处六值字符串名单。
- I1-2: 单测断言 `expertTypesFilter(emptyList()) == null`，且 `buildExpertFilters(expertTypes = emptyList())` 的返回与不传该参数时 `assertEquals` 相等。
- I1-3: 单测断言两值输入的返回结构为 `{bool:{should:[..2 项..], minimum_should_match:1}}`，且顶层不含 `filter` 键。
- I1-4: 编译通过即证明三个聚合调用点未被打断；额外断言 `:949/:994/:1101` 路径产出的 filter 与改动前一致。
- I1-5: `node --check` 通过；`expertTypeFilter.test.js` 中「未选中时 params 不含 expertType」与「选中两个时 getAll 长度为 2」两条断言通过。
- S-1: `git diff src/main/resources/static/styles.css` 为空（本计划不改样式文件）；grep 证明 `#expertTypeTagSelect` 骨架中出现的 class 仅有 `toolbar-label` / `tag-select` / `tag-chip`，无新 class、无 `style=` 属性。
- S-2: grep 证明列表行 chip 使用 `class="tag-chip"` 且无 `.active`；无 inline style。
- S-3: `git diff src/main/resources/static/index.html` 中学科下拉段落只有可见文本变化，`value=""` / `value="STEM"` / `value="HUMANITIES"` / `value="UNCLASSIFIED"` 四个属性逐字未变。
- 回归：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 类型筛选生效
- 前置条件: CANDIDATE 层至少各有 1 名 `expertClassification.type` 为 `ACADEMIC_RND`、`OUT_OF_SCOPE` 的专家（可用 `GET /api/experts?level=CANDIDATE` 确认存在）。
  **注意（2026-08-25 CP-2 实测）**：不要把「存在 `PRODUCTION_RND` 专家」写进前置条件——
  该类型在当前数据下**极可能一条都没有**。`PROD_PATENTS` +45 恒不可得
  （OpenAlex 无专利数据，`/types` 中无 `patent` 类型），生产分只能靠
  `PROD_ROLE` +35 与 `PROD_THEME` +20 凑满 50，两者都要求 `employment` / `researchFields` 恰好含特定词。
  若验收时确实查不到任何 `PRODUCTION_RND`，**这不是本子计划的缺陷**，用 `ACADEMIC_RND` 与
  `OUT_OF_SCOPE` 两类完成本条验收即可。
- 操作步骤: 1. 打开专家漏斗页；2. 点击「研发类型」中的「生产研发」chip；3. 观察列表与总数；4. 再点「学术科研」chip（两个同时选中）；5. 观察总数。
- 预期结果: 步骤 3 列表仅出现类型 chip 为「生产研发」的行，总数等于该类型条数；步骤 5 总数等于两类之和（严格大于步骤 3 的数字）。
- 覆盖: I1-1、I1-3、需求描述第 1 条

### A-2: 未选类型时行为不变
- 前置条件: 记录改动部署前专家列表在默认筛选下的总条数 N。
- 操作步骤: 1. 部署后打开专家漏斗页，不点任何类型 chip；2. 读取列表总条数。
- 预期结果: 总条数等于 N；筛选徽章计数与部署前相同。
- 覆盖: I1-2、必须保持不变第 2 条

### A-3: 未分类语义正确
- 前置条件: 存在至少 1 名 `expertClassification` 字段完全缺失的 CANDIDATE（新发现且未回填的专家即是）。
- 操作步骤: 1. 只点击「未分类」chip；2. 查看列表行。
- 预期结果: 列表只出现无类型 chip 的行；该专家出现在结果中。若结果为 0 条而 ES 中确有此类文档，即为 I1-1 违规。
- 覆盖: I1-1

### A-4: 列表行展示
- 前置条件: 同 A-1。
- 操作步骤: 1. 不筛选，滚动列表；2. 找到一名有分类的专家，鼠标悬停其类型 chip；3. 找到一名无分类的专家。
- 预期结果: 有分类者显示灰底描边 chip，中文类型名正确，悬停 tooltip 显示「生产分 X / 科研分 Y」两个具体数字；无分类者该位置**无任何元素**（不是空白占位或「-」）。
- 覆盖: S-2、需求描述第 1 条

### A-5: 样式目测
- 前置条件: 同一屏内可同时看到「数据完整度」的既有 chip 组与新增的「研发类型」chip 组。
- 操作步骤: 1. 对比两组 chip 的高度、圆角、字号、间距；2. 鼠标悬停任一新 chip；3. 点击选中后观察。
- 预期结果: 两组视觉完全一致（高 26px、字号 11px、圆角与边框相同）；hover 时边框与文字变主色、背景变浅主色；选中后主色实底白字带轻阴影。任一状态与既有 chip 不一致即为 S-1 违规。
- 覆盖: S-1

### A-6: 学科下拉文案
- 操作步骤: 1. 展开「学科分类」下拉；2. 选择「理工科（含医学）」并刷新列表。
- 预期结果: 文案为「理工科（含医学）」；筛选结果条数与改动前选「理工科」时**完全相同**（证明只改了文案未改语义）。
- 覆盖: S-3、必须保持不变第 1 条

### A-7: 跨路径——回填写入到页面读出
- 前置条件: 选定一名 `expertClassification` 缺失的 CANDIDATE，记录其 orcidId。
- 操作步骤: 1. 对该文档执行一次分类回填（沿用 2026-08-24 计划 02 的管理 API）；2. 刷新专家列表并搜索该专家。
- 预期结果: 该行出现类型 chip，tooltip 中的两个分数与回填任务返回的分数一致。
- 覆盖: 现状审计的 interaction point

人工验收开始时，从本节导出 `01-expert-list-type-filter-acceptance.md`；不得提前生成。
