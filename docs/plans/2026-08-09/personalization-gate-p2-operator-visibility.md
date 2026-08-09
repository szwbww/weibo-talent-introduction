# P2 · 运营可见性（列表按模板门禁筛选）

- master: `docs/plans/2026-08-09/personalization-gate-master.md`
- 子计划序号: 2 / 2
- 前置依赖: **P1 必须已合入**（消费 `MailComposeTemplateService.effectiveRequiredKeys` / `requiredEsFields` 与 `primaryResearchField` 的字段映射）
- 子系统: 查询（ES）+ 前端

## 需求描述

### Observable outcome

1. 专家列表「数据完整度」新增「有近期论文」选项。
2. 专家列表新增「按模板门禁」下拉；选中某模板后自动应用该模板必填字段的筛选。
3. 筛选区显示「符合 N / 总数 M」。

### What must NOT change

1. 既有五个 `hasField` 选项（`employment` / `degree` / `institution` / `researchFields` / `patentTitles`）的 chip 文案、`data-value` 与查询语义不变。
2. `/api/experts` 的既有查询参数（`tag` / `operatorStatus` / `emailDomain` / `region` / `hIndexMin` / `citationCountMin` / `recentYears` / `hasField` / `discipline`）语义不变。
3. `.tag-chip` 既有三条规则（base / `:hover` / `.active`）不得就地修改——它们被本视图之外的位置复用。
4. `researchFields` 的 ES 存储格式不变。
5. `countByFieldPresence` / `findRandomByFieldPresence` 供 QA 随机专家预览使用的既有行为不变。

### Out of scope

1. 新增侧栏 Tab 或 view（本计划只在既有专家列表工具栏内加控件，因此不涉及 `K-view-registration-triad` 的四处注册）。
2. 让列表计数变成精确值（`text` 类型字段无法在 ES 层排除空字符串，见 I-9）。
3. 批量发送配置界面的联动。

## 关键不变量

### I-9: 列表计数是近似值，且只能高估
- Rule: ES 层的字段存在性筛选对 `keyword` 类型字段追加 `must_not: {term: {<field>: ""}}`；对 `text` 类型字段只用 `exists`。因此计数可能高于实际可发数，**不得**在任何 UI 文案中把它表述为「可发送数量」。
- Applies to: `ExpertSearchService.buildExpertFilters`、前端计数展示文案。
- Violation consequence: 运营按列表数字预期发送量，实际被 P1 门禁拦下更多，误判为系统故障。
- 来源: master I-M2；mapping 证据见「现状审计」

### I-10: 前端不得持有必填集
- Rule: 前端只能通过 `GET /api/compose-templates/{id}/gate-fields` 获取字段列表并原样塞进 `hasField` 参数。前端不得解析 `${...}`、不得内置任何默认字段数组、不得在接口失败时回退到硬编码集合（失败时应不应用筛选并提示）。
- Applies to: `app.js` 的门禁筛选逻辑。
- Violation consequence: 前后端口径漂移。
- 来源: master I-M3

### I-11: 字段白名单必须容纳门禁字段
- Rule: `ALLOWED_HAS_FIELDS` 必须包含 `requiredEsFields()` 可能返回的全部值。新增 `recentWorkTitles`。若接口返回的字段不在白名单内，服务端必须返回明确错误而非静默忽略。
- Applies to: `ExpertSearchService.ALLOWED_HAS_FIELDS`、`buildExpertFilters` 的 `require`（`:773`）。
- Violation consequence: 现有 `require(field in ALLOWED_HAS_FIELDS)` 会抛 `IllegalArgumentException`，整个列表查询 500。
- 来源: original

### I-12: 空字符串排除只施加于 keyword 字段
- Rule: 追加 `must_not term ""` 的字段集合必须是显式白名单常量，且只含 mapping 中类型为 `keyword` 的字段：`researchFields`、`recentWorkTitles`、`patentTitles`、`degree`、`country`。
- Applies to: `ExpertSearchService`。
- Violation consequence: 对 `text` 字段用 `term ""` 匹配的是分词结果，语义不确定，可能误排除有值文档。
- 来源: original（mapping 证据见下）

## 现状审计

### `/api/experts` 查询链路
- Controller: `expert/controller/ExpertIndexController.kt:55-67`，`hasField: List<String>?` 透传。
- Service: `ExpertSearchService.searchExperts(:63-83)` → `buildExpertFilters(:730-782)`；`hasField` 在 `:772-775` 逐项 `require(field in ALLOWED_HAS_FIELDS)` 后加 `exists` 过滤。
- 白名单: `ExpertSearchService.kt:24` `ALLOWED_HAS_FIELDS = setOf("employment", "degree", "institution", "researchFields", "patentTitles")` —— **不含 `recentWorkTitles`**。
- 计数: `countByFieldPresence(:534-543)` 与 `buildFieldPresenceFilters(:594-613)` 已存在，供 QA 随机专家预览（`QaRuleManagementController.kt:59-66`）使用。
- 既有对空字符串的处理: `findRandomByFieldPresence(:575-580)` 对 `SATISFY_ALL` 额外做 Kotlin 侧 `hasNonBlankEsField` 后过滤——证实 ES `exists` 不排除空串。

### ES mapping（`src/main/resources/es/orcid_info_candidate.json`）
| 字段 | 类型 | 可用 `term ""` 排空 |
|---|---|---|
| `researchFields` | keyword | 是 |
| `recentWorkTitles` | keyword | 是 |
| `patentTitles` | keyword | 是 |
| `degree` | keyword | 是 |
| `country` | keyword | 是 |
| `institution` | text | 否 |
| `employment` | text | 否 |
| `keyword` | text | 否 |
| `familyNames` | text | 否 |

### 模板接口
- `template/controller/MailComposeTemplateController.kt:20` `@RequestMapping("/api/compose-templates")`。新增只读端点挂此处。

### 前端样式盘点

可复用 class：

| class | 位置 | 用途 |
|---|---|---|
| `.tag-select` | `styles.css:540-545` | chip 组容器 |
| `.tag-chip` | `styles.css:547-564` | chip 基态 |
| `.tag-chip:hover` | `styles.css:566-570` | chip 悬停 |
| `.tag-chip.active` | `styles.css:572-577` | chip 选中 |
| `.toolbar-label` | 用法见 `index.html:526-561` | 工具栏内「标签 + 控件」包裹 |

设计基准 token 实值（`styles.css:1-61` `:root`）：

- `--primary: #2563eb`；`--primary-rgb: 37, 99, 235`；`--primary-light: rgba(var(--primary-rgb), 0.07)`
- `--border: rgba(15, 23, 42, 0.11)`；`--text-muted: #94a3b8`；`--text-main: #1e293b`
- `--font-body: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Helvetica Neue', sans-serif`
- chip 尺寸基准：`height: 26px`；`padding: 0 10px`；`border-radius: 13px`；`font-size: 11px`；`font-weight: 500`
- body 基准字号 13px；工具栏内文字 12px（见 `.filter-toggle` `styles.css:4976-4990`）

DOM 结构约定 —— 既有「数据完整度」区块（`index.html:552-561`，改动前基线，逐字）：

```html
<span class="toolbar-label">
    数据完整度:
    <span class="tag-select" id="hasFieldTagSelect">
        <button type="button" class="tag-chip" data-value="employment">有职位</button>
        <button type="button" class="tag-chip" data-value="degree">有学历</button>
        <button type="button" class="tag-chip" data-value="institution">有机构</button>
        <button type="button" class="tag-chip" data-value="researchFields">有研究方向</button>
        <button type="button" class="tag-chip" data-value="patentTitles">有专利</button>
    </span>
</span>
```

前端已有的 chip 行为：`app.js:11118` 起的 `initHasFieldTags()` 通过 shim 暴露 `.selectedOptions`，使 `app.js:4631-4638` 的查询构造代码无需改动即可读到选中值。**本计划必须沿用该 shim，不得另起一套选中状态管理。**

### Interaction points
1. P1 的 `requiredEsFields()` → 新端点 → 前端 `hasField` 参数 → `ExpertSearchService.buildExpertFilters` 的 `ALLOWED_HAS_FIELDS` 校验。任一环字段集不一致即 500（I-11）。
2. 新增的 `must_not term ""`（I-12）同时影响 `searchExperts` 与 `countExperts`，两者必须用同一份过滤器构造函数，否则列表条数与计数不一致。

## 样式契约

### S-1: 「有近期论文」chip
- 复用：`.tag-chip`（`styles.css:547-564`）、`.tag-chip:hover`（`:566-570`）、`.tag-chip.active`（`:572-577`）。**禁止**新建任何近似 chip 样式。
- 新增：无 CSS。
- DOM 结构：在 `index.html` 既有 `#hasFieldTagSelect` 内，`researchFields` 与 `patentTitles` 两个 button 之间插入一行，缩进与相邻行一致：

```html
<button type="button" class="tag-chip" data-value="recentWorkTitles">有近期论文</button>
```

- 禁止项：inline style；修改相邻五个 button 的任何属性；改动 `.tag-chip` 三条既有规则。

### S-2: 「按模板门禁」控件
- 复用：`.toolbar-label`（用法见 `index.html:526-561`）包裹；`<select>` 沿用工具栏内既有 select 的裸标签写法（参见 `#expertDisciplineFilter`，`index.html:528-533`），不加自定义 class。
- 新增：一个 class，CSS 逐字如下，追加到 `styles.css` 中 `.tag-chip.active` 规则块（`:577`）之后、`/* Back to list button ... */` 注释（`:579`）之前：

```css
.gate-filter-summary {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 26px;
    padding: 0 10px;
    border: 1px solid var(--border);
    border-radius: 13px;
    background: transparent;
    color: var(--text-muted);
    font-size: 11px;
    font-weight: 500;
    font-family: var(--font-body);
    white-space: nowrap;
    line-height: 1;
}

.gate-filter-summary.has-value {
    border-color: var(--primary);
    color: var(--primary);
    background: var(--primary-light);
}

.gate-filter-summary .gate-filter-match {
    font-weight: 500;
    color: var(--text-main);
}

.gate-filter-summary.has-value .gate-filter-match {
    color: var(--primary);
}
```

- DOM 结构：在 `index.html` 的 `#hasFieldTagSelect` 所在 `<span class="toolbar-label">`（`:552-561`）**之后**、`</div>`（`:562`）之前插入：

```html
<span class="toolbar-label">
    按模板门禁:
    <select id="expertGateTemplateFilter">
        <option value="">不限</option>
    </select>
    <span class="gate-filter-summary" id="expertGateSummary" hidden>
        符合 <span class="gate-filter-match" id="expertGateMatchCount">0</span> / <span id="expertGateTotalCount">0</span>
    </span>
</span>
```

- 禁止项：inline style；除上述一个 class 外新增任何 class；对 `.toolbar-label` 或 `.tag-select` 规则块的任何修改。

### S-3: 计数文案
- 复用：S-2 的 `.gate-filter-summary`。
- 新增：无。
- 文案逐字为 `符合 N / M`，**不得**写成「可发送 N 封」或任何暗示精确可发量的措辞（I-9）。
- 下拉为「不限」时 `#expertGateSummary` 保持 `hidden`；选中模板且计数返回后移除 `hidden` 并加 `has-value`。

## 实现方案

### 任务 1 · `ExpertSearchService.kt`（I-11、I-12）

1. `ALLOWED_HAS_FIELDS` 增加 `"recentWorkTitles"`。
2. 新增常量：
   ```kotlin
   val BLANK_EXCLUDABLE_FIELDS = setOf(
       "researchFields", "recentWorkTitles", "patentTitles", "degree", "country"
   )
   ```
3. 新增私有函数 `fieldPresenceFilter(field: String): Map<String, Any>`：字段在 `BLANK_EXCLUDABLE_FIELDS` 中时返回 `exists` + `must_not term ""` 的 bool 组合，否则返回裸 `exists`。
4. `buildExpertFilters` 的 `hasField` 分支（`:772-775`）改用该函数。
5. `buildFieldPresenceFilters` 的 `SATISFY_ALL` 分支（`:596-598`）同样改用该函数，使列表与计数口径一致（Interaction point 2）。`MISSING_ANY` 分支不动（QA 预览语义不变，must-NOT-change 第 5 项）。

### 任务 2 · `MailComposeTemplateController.kt`（I-10）

新增只读端点：

```
GET /api/compose-templates/{id}/gate-fields
→ { "templateId": 1, "requiredKeys": ["recentWorkTitle","primaryResearchField"], "esFields": ["recentWorkTitles","researchFields"] }
```

`requiredKeys` 取 `effectiveRequiredKeys(id)`，`esFields` 取 `requiredEsFields(id)`，二者均由 P1 提供，本计划**不得**自行推导。

### 任务 3 · `index.html`（S-1、S-2）

按 S-1 与 S-2 的 DOM 片段逐字插入。

### 任务 4 · `styles.css`（S-2）

按 S-2 逐字追加 `.gate-filter-summary` 四条规则到指定位置。

### 任务 5 · `app.js`（I-9、I-10、S-3）

1. 页面加载时用既有的 compose 模板列表接口填充 `#expertGateTemplateFilter`（value = 模板 id，文案 = 模板名称）。
2. 选中模板 → 调 `/api/compose-templates/{id}/gate-fields` → 把 `esFields` 逐项应用到 `#hasFieldTagSelect` 的对应 chip（加 `.active`），复用 `initHasFieldTags` 的既有选中机制；返回的字段在 chip 中不存在时忽略该字段并在控制台记录，不阻断。
3. 切回「不限」→ 清除由门禁自动加上的 `.active`，恢复用户此前手动勾选的状态（需在应用门禁前记录快照）。
4. 计数：沿用既有 `/api/experts` 响应的 `totalHits`——应用筛选后的 `totalHits` 即「符合」，不带 `hasField` 的一次查询即「总数」。避免新增计数接口。
5. 接口失败：不应用任何筛选，`#expertGateSummary` 保持 hidden，提示一次失败信息。**禁止回退到任何硬编码字段集**（I-10）。

## 变更文件清单

生产文件 **5** 个：

| # | 文件 | 改动性质 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | 白名单 + 空串排除 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/template/controller/MailComposeTemplateController.kt` | 新增只读端点 |
| 3 | `src/main/resources/static/index.html` | S-1、S-2 的 DOM |
| 4 | `src/main/resources/static/styles.css` | S-2 的 CSS |
| 5 | `src/main/resources/static/app.js` | 门禁筛选逻辑 |

测试文件（不计入上限）：

| 文件 | 改动性质 |
|---|---|
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | 补空串排除与白名单用例 |
| `src/test/kotlin/com/weibo/talentintroduction/template/controller/ComposeTemplateGateControllerTest.kt` | 新增 |
| `src/test/js/gateTemplateFilter.test.js` | 新增 |

## 验证命令

见 master 计划 `## 验证命令` 节。P2 相关的是该节的「P2 新增/受影响测试类」与「前端 JS 测试单文件」两条。注意全量 `mvn test` 已包含前端 JS 测试（`pom.xml` 把 `node --test` 绑在 `test` 阶段），单文件命令仅供开发期迭代。本节不重复命令文本。

## 验收标准

- **I-9**: 断言 `researchFields` 的过滤器 JSON 同时含 `exists` 与 `must_not`/`term`/`""`；断言 `institution` 的过滤器只含 `exists`。grep 前端确认无「可发送」「可发」字样出现在计数文案中。
- **I-10**: grep `app.js` 确认无 `${` 占位符解析逻辑、无硬编码必填字段数组；JS 测试断言接口返回 500 时不产生任何 `hasField` 参数。
- **I-11**: 断言 `ALLOWED_HAS_FIELDS` 包含 `requiredEsFields` 可能返回的全部值；断言传入未知字段时 `buildExpertFilters` 抛 `IllegalArgumentException`（既有行为）。
- **I-12**: 断言 `BLANK_EXCLUDABLE_FIELDS` 恰好等于 `{researchFields, recentWorkTitles, patentTitles, degree, country}`，且与 `orcid_info_candidate.json` 中类型为 `keyword` 的可筛字段一致。
- **S-1**: diff 断言 `index.html` 新增行逐字等于契约中的 button 片段；`styles.css` 中 `.tag-chip` 三条规则块无改动。
- **S-2**: diff 断言 `styles.css` 新增的四条规则与契约代码块逐字一致（含属性顺序）；`index.html` 新增片段与契约骨架一致；全 diff 无 inline style，无契约外新增 class。
- **S-3**: grep 确认计数文案为 `符合`/`/` 结构。
- 回归：执行 master `## 验证命令` 节的全量测试命令与前端 JS 测试通过。

## 人工验收清单

### A2-1: 新增 chip 可用且样式一致
- 前置条件: 无。
- 操作步骤: 打开专家列表 → 观察「数据完整度」→ 点击「有近期论文」。
- 预期结果: 该 chip 与相邻五个在高度、圆角、字号上完全一致；选中后底色为 `#2563eb`、文字白色（与「有研究方向」选中态目测无差别）；列表结果数量减少。
- 覆盖: S-1、需求 observable outcome 1

### A2-2: 选模板自动应用筛选
- 前置条件: 存在一个 `required_keys` 为 `["recentWorkTitle","primaryResearchField"]` 的模板。
- 操作步骤: 「按模板门禁」下拉选中该模板。
- 预期结果: 「有近期论文」与「有研究方向」两个 chip 自动变为选中态；右侧出现「符合 N / M」，N ≤ M 且均为具体数字；列表内容随之刷新。
- 覆盖: I-10、S-2、S-3、需求 observable outcome 2 与 3

### A2-3: 切回「不限」恢复手动勾选
- 前置条件: 先手动只勾选「有机构」，再选中某模板。
- 操作步骤: 把下拉切回「不限」。
- 预期结果: 由门禁自动加上的两个 chip 取消选中，「有机构」仍保持选中；计数区隐藏。
- 覆盖: I-10

### A2-4: 接口失败不回退硬编码
- 前置条件: 用开发者工具把 `/api/compose-templates/{id}/gate-fields` 拦截为 500。
- 操作步骤: 选中一个模板。
- 预期结果: 出现一次失败提示；没有任何 chip 被自动勾选；计数区保持隐藏；列表结果与选中前一致。
- 覆盖: I-10、master AM-6

### A2-5: 既有五个 chip 行为不变（回归）
- 前置条件: 记录改动前，仅勾选「有研究方向」时的列表总数 N。
- 操作步骤: 上线后重复该筛选。
- 预期结果: 总数为 N 或**略小于** N（因新增了空字符串排除）。若明显大于 N，说明过滤器构造被破坏。
- 覆盖: must-NOT-change 第 1 项、I-12

### A2-6: 列表口径不低估
- 前置条件: 完成 A2-2。
- 操作步骤: 记下「符合」数 N，用同一模板发起批量发送，记录成功数 S 与「个性化字段缺失」跳过数 K。
- 预期结果: `N ≥ S`。允许 `N > S`（近似高估，属预期）；若 `N < S`，说明筛选比门禁更严，属缺陷。
- 覆盖: I-9、master AM-5
