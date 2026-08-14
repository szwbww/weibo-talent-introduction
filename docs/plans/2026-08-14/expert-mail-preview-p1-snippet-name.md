# P1：回复片段增加名称，并统一所有显示位置的片段标签

> 隶属主计划 `expert-mail-preview-main.md`。执行前必须先读主计划的
> `## 共享不变量`（M-1..M-4）、`## 共享验证命令`、`## 需求描述` 的 must-NOT-change 表。
> 本计划必须在 P2 之前完成。
>
> 编号约定：本文件的 `I-n` / `S-n` 为**计划内局部编号**，与 P2 的同名编号无关；跨计划共享的不变量一律用 `M-n`。

---

## 需求描述

**Observable outcome**

1. 回复片段编辑弹窗里可以填写"片段名称"，保存后生效；留空合法。
2. 邮件模板编辑器的"回复片段"块下拉，显示片段名称；未命名的片段显示其内容首行摘要，不再显示 `尊语 #10`。
3. 邮件模板列表里的内容块 pill、以及预览里的块说明，与下拉显示同一个名字。
4. 回复片段管理表格新增"名称"列，运营能一眼看出哪些片段还没命名。

**What must NOT change**：见主计划 N-1..N-6，逐条为本计划的硬约束。特别强调：

- `resolveManualFrame` / `resolveAck` / `listSelectableFrameOptions` / `resolveSelectableFrame` / `frameVersion` 的行为与输出不得因新增 `name` 发生任何变化（N-1）。
- 工作台 frame 下拉继续显示 `option.content` 全文（N-2）。

**Out of scope**：见主计划。本计划额外推迟：

- 不写数据回填迁移（seed）。名称留空时的兜底改为**内容首行摘要**，存量片段无需回填即可摆脱 `#id` 显示；运营后续按需命名。
- `name` 不加唯一索引、不加长度以外的业务校验。

---

## 关键不变量

### Invariant I-1: 片段显示名单一算法，两处实现必须逐字等价
- Rule：任何展示"这个片段叫什么"的位置，标签必须由同一算法产出：
  1. `name` 去空白后非空 → 用 `name`
  2. 否则 → `content` 的**首个非空行**去空白后，取前 40 个字符；原始首行长度 > 40 时在末尾追加 `…`（U+2026，单字符）
  3. 否则（`content` 为空，持久化数据不可达，见下）→ `"<snippetType 原样> #<id>"`
- 第 2 级不可能落到第 3 级：`ReplySnippetService.kt:185`（create）与 `:223`（update）均有 `require(command.content.isNotBlank())`，且 `reply_snippet.content` 是 `TEXT NOT NULL`（`V47__create_reply_snippet.sql:4`）。第 3 级仅作防御性代码保留，不参与产品语义。
- Applies to（**共 2 处实现**，grep 回执见 `## 现状审计`）：
  - 前端 `app.js:8059`（`composeTemplateBlockRowHtml` 内的块下拉 option 文本，map 开始于 `:8058`）
  - 后端 `MailComposeTemplateService.kt:382-386`（`resolveRefDisplayName` 的 `REPLY_SNIPPET` 分支）
- Violation consequence：同一个片段在下拉里叫 A、在模板列表 pill 里叫 B，运营无法把两处对上，等于没修。
- 来源: original

### Invariant I-2: `name` 是纯展示字段，绝不进入任何业务判定
- Rule：`name` 不得被 frame 解析、默认片段选取、变体解析或 frame version 计算读取。具体地，下列方法的行为与返回值必须与本计划实施前**逐字一致**：
  - `resolveManualFrame()` `ReplySnippetService.kt:29-40`
  - `resolveAck()` `:42-51`
  - `listSelectableFrameOptions()` `:58-76`
  - `resolveDefaultSelectableFrame()` `:83-91`
  - `resolveSelectableFrame()` `:100-113`
  - `defaultSnippetId()` `:115-120`
  - `resolveFrameSlot()` `:122-140`
  - `frameVersion()` `:147-160` 与 `frameSlotIdentity()` `:162-174`
- 特别地，`frameSlotIdentity` 的输入是 `slot / id / snippetType / enabled / updatedAt / sha256(content)`（`:166-173`），**不含 name**，本计划不得加入。
- 附带事实（必须由测试锁定）：仅修改 `name` 不会改变 `frameVersion`。理由链——`update()` 走 `existing.copy(...)`（`:232-240`）不改 `updatedAt`；Spring Data JDBC 的 UPDATE 绑定全部可写非 ID 属性（K-spring-data-jdbc-null-default），因此 `updated_at` 被显式写回旧值，MySQL 的 `ON UPDATE CURRENT_TIMESTAMP`（`V47__create_reply_snippet.sql:8`）在列被显式赋值时不触发。
- Violation consequence：运营改个片段名字就让已锁定的回复 frame 版本失效，工作台里已组装的回复被静默作废。
- 来源: original + K-spring-data-jdbc-null-default

### Invariant I-3: `name` 必须贯通 DTO 七层
- Rule：按 K-variant-pool-dto-chain 的检查清单逐层落地，缺一层即为未完成：
  1. `ReplySnippetCreateRequest` / `ReplySnippetUpdateRequest` 接收（`ReplySnippetController.kt:60-67` / `:81-87`）
  2. 两个 `toCommand()`（`:69-78` / `:89-97`）
  3. `ReplySnippetCreateCommand` / `ReplySnippetUpdateCommand`（`ReplySnippetService.kt:381-389` / `:391-398`）
  4. `create()` 的 `ReplySnippet(...)` **构造器**传入（`ReplySnippetService.kt:195-206`）
  5. `update()` 的 `existing.copy(...)` 传入（`:232-240`）
  6. `toResponse()` 映射（`ReplySnippetController.kt:114-124`）
  7. `ReplySnippetResponse` 携带（`:100-109`）
- 第 4 层的坑：`create` 走构造器，不传即 `null`（字段丢失）。第 5 层的坑：`update` 走 `copy`，不传即**保留旧值**，用户清空名字将无法生效——因此 `update` 必须显式 `name = <归一化后的 command.name>`。
- Violation consequence：前端填了名字但保存后不显示（create 丢失），或清空名字后旧名字复活（update 未显式传）。
- 来源: K-variant-pool-dto-chain

### Invariant I-4: 空白名归一化为 NULL
- Rule：`name` 的空串与纯空白必须在服务层归一化为 `null` 后再落库，写法与既有 `variantGroup` 完全一致：`command.name?.trim()?.takeIf { it.isNotBlank() }`（参照 `ReplySnippetService.kt:200` create 与 `:236` update）。
- Violation consequence：库里存 `""` 时 I-1 第 1 级判定为"有名字"，下拉渲染出空白 option，比 `#id` 更糟。
- 来源: original

### Invariant I-5: 迁移不得引入 Flyway 占位符
- Rule：`V96` 迁移文件正文中不得出现 `${...}`。`src/main/resources/application.yml` 的 `spring.flyway.placeholder-replacement: false` 必须保持存在，本计划不得修改该段。
- 本计划的迁移只有 `ALTER TABLE ... ADD COLUMN`，天然不含 `${}`；本不变量是防止执行 agent 顺手加 COMMENT 示例文本时带入 `${}`。
- Violation consequence：生产启动即抛 `No value provided for placeholder expressions`，属部署即挂。
- 来源: K-flyway-placeholder-replacement

---

## 样式契约

本计划**不新增任何 CSS**。所有新 DOM 复用既有 class。

### S-1: 片段编辑弹窗的"片段名称"输入
- 复用：`#replySnippetForm` 是 `class="form-grid"`（`index.html:1731`）。新字段与既有 `片段 ID`（`index.html:1741`）、`显示排序`（`:1742`）同为**不带 `span-2`** 的普通 `<label>`，由 `.form-grid` 的既有栅格自动排布。
- 新增 CSS：**无**。
- DOM 结构（逐字，插入位置 = `index.html:1741` 之前，使名称成为类型之后的第一个字段）：
  ```html
            <label>片段名称<input name="name" maxlength="120" placeholder="留空则显示内容摘要"></label>
  ```
- 禁止项：inline style；新建任何 class；修改 `.form-grid` / `label` / `input` 的既有规则块。

### S-2: 片段管理表格的"名称"列
- 复用：既有表格结构 `renderReplySnippetTypePanel`（`app.js:3548-3572`）的 `<th>` / `<td>`，以及未命名占位用的 `.muted`（`styles.css:2845`）。表格首列既有 `.muted-cell`（`styles.css:2850`，用于内容列 `app.js:3532`）不变。
- 新增 CSS：**无**。
- DOM 结构（表头，插入在 `<th>内容</th>` 之前）：
  ```html
                            <th>名称</th>
  ```
  （行单元格，插入在内容 `<td class="muted-cell">` 之前）：
  ```html
            <td>${snippet.name ? escapeHtml(snippet.name) : '<span class="muted">未命名</span>'}</td>
  ```
- 连带修改：`renderReplySnippetTypePanel` 的空表 `colspan` 由 `showDefault ? 6 : 5` 改为 `showDefault ? 7 : 6`（`app.js:3553`），同函数内 `renderReplySnippetRow` 的空表分支同理（`app.js:3553` 唯一一处）。
- 禁止项：inline style；新建 class。

### S-3: 块下拉 option 文本
- 复用：`composeTemplateBlockRowHtml` 内既有 `<select data-field="refId">`（`app.js:8071`），结构不变。
- 新增 CSS：**无**。
- DOM 结构：`<option>` 标签数量、属性、`selected` 判定逻辑均不变，**只替换 `label` 变量的取值来源**（`app.js:8059` 单行）。
- 禁止项：给 option 加 class、加 `title`、加图标；改变 select 结构。

---

## 现状审计

### 存储：MySQL `reply_snippet`

- Schema（`src/main/resources/db/migration/V47__create_reply_snippet.sql:1-9`）：
  ```
  id BIGINT PK AUTO_INCREMENT
  snippet_type VARCHAR(32) NOT NULL
  content TEXT NOT NULL
  display_order INT NOT NULL DEFAULT 100
  is_default TINYINT(1) NOT NULL DEFAULT 0
  enabled TINYINT(1) NOT NULL DEFAULT 1
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  ```
  唯一后续 ALTER：`V64__add_subject_variants_and_snippet_variant_group.sql:2` 加 `variant_group VARCHAR(64) NULL`。
  grep 回执：
  ```
  $ grep -rn "ALTER TABLE reply_snippet" src/main/resources/db/migration/*.sql
  V64__add_subject_variants_and_snippet_variant_group.sql:2:ALTER TABLE reply_snippet ADD COLUMN variant_group ...
  ```
  当前最新迁移版本：`V95__add_operator_status_to_batch_send_task_config.sql`（`ls src/main/resources/db/migration | sort -V | tail -1`）→ 本计划占用 **V96**。
- 领域对象：`ReplySnippet.kt:9-19`，字段与上表一一对应，无 `name`。

- **写路径（全集）**，全部集中在 `ReplySnippetService`：
  1. `create()` `ReplySnippetService.kt:182-218` — 构造 `ReplySnippet(...)` 并 `repository.save`（`:195-206`）
  2. `update()` `:220-251` — `existing.copy(...)` 并 save（`:232-240`）
  3. `setEnabled()` `:253-256` — `copy(enabled = ...)`
  4. `setDefault()` `:258-270` — `copy(isDefault = ...)`，并把同类型旧默认 `copy(isDefault = false)`（`:265-267`）
  5. `clearOtherDefaults()` `:294-298` — `copy(isDefault = false)`
  6. `delete()` `:272-277` — `repository.deleteById`
  7. 迁移种子 `V47__create_reply_snippet.sql:12-22` — INSERT 6 行（SALUTATION×1, GREETING×1, CLOSING×1, ACK×3）
  - 3~5 走 `copy()`，`name` 自动保留，**无需改动**（这是 `copy()` 相对构造器的关键差异，见 I-3）。

- **读路径（全集）**：
  1. `listAll()` `:20-21` → `toDetail()` `:279-286` → Controller `list()` `:23-29` → `toResponse()` `:111-124` → 前端 `state.replySnippets`（`app.js:2905-2908`）
  2. `listByType()` `:23-27`（同上链路）
  3. `resolveManualFrame()` `:29-40` — 只读 `content` / `id` / `snippetType` / `enabled` / `displayOrder`
  4. `resolveAck()` `:42-51` — 只读 `enabled` / `snippetType` / `content`
  5. `listSelectableFrameOptions()` `:58-76` — 产出 `ReplyFrameOption`(`:342-348`)，字段 `id/snippetType/content/displayOrder/isDefault`
  6. `resolveSelectableFrame()` `:100-113` / `resolveFrameSlot()` `:122-140` — 只读 `enabled` / `snippetType` / `content`
  7. `frameSlotIdentity()` `:162-174` — 读 `id/snippetType/enabled/updatedAt/sha256(content)`
  8. `MailComposeTemplateService.resolveRefDisplayName()` `:382-386` — 跨模块读，只读 `snippetType` / `id`
  9. `MailComposeTemplateService.resolveBlocks()` 路径读片段正文（`:474`、`:520` 附近传 `displayName`）
  - 3~7 全部只依赖 content/type/enabled 等既有字段，加 `name` 后行为不变 → 支撑 I-2。

### 显示名的 2 处实现（I-1 的 Applies to 全集）

grep 回执：
```
$ grep -rn "snippetType} #\|snippetType.orEmpty()} #" src/main/kotlin src/main/resources/static
src/main/resources/static/app.js:8059:        const label = `${replySnippetTypeLabels[snippet.snippetType] || snippet.snippetType} #${snippet.id}`;
src/main/kotlin/.../template/service/MailComposeTemplateService.kt:384:                    "${snippet.snippetType} #${snippet.id}"
```
- 前端 `app.js:8059` — `replySnippetTypeLabels` 定义在 `app.js:2895-2901`（SALUTATION→尊语 / ACK→致谢语 / GREETING→开场白 / CLOSING→结束语 / CUSTOM→自定义内容）。用户看到的"尊语 #10"来自这里。
- 后端 `MailComposeTemplateService.kt:382-386` — 产出 `refDisplayName`，进入 `MailComposeTemplateBlockDetail:649` 与 `ComposeTemplatePreviewBlock:672`。
  前端消费点 2 处：模板列表块 pill `app.js:8004-8006`、预览块说明 `app.js:8248`。

**未受影响的第三处**：`trust-reply-workbench.js:1248` 用 `option.content` 全文渲染 frame 下拉，不走 `#id` 拼接，本计划不动（主计划 N-2）。

### 先例：`qa_rule.display_name`

同一个 `resolveRefDisplayName` 的 QA_RULE 分支已经是三级回退（`MailComposeTemplateService.kt:375-381`）：
```kotlin
rule.displayName?.takeIf { it.isNotBlank() }
    ?: rule.replySubject?.takeIf { it.isNotBlank() }
    ?: "Rule #$refId"
```
`qa_rule.display_name` 由 `V14__contact_index_level_and_reason_type_and_qa_display_name.sql:54` 加为 `VARCHAR(120) NULL`。
本计划的列定义与回退结构照此对齐，不是新设计。

### Interaction points

| # | 写路径 | 读路径 | 本计划是否处理 |
|---|---|---|---|
| X-1 | `ReplySnippetService.create/update` 写 `name` | `MailComposeTemplateService.resolveRefDisplayName`（跨模块，经 `ReplySnippetRepository` 直读实体） | 是 —— I-1 第 1 级 |
| X-2 | `ReplySnippetService.create/update` 写 `name` | Controller `list()` → 前端 `state.replySnippets` → 块下拉 `app.js:8059` + 管理表格 `app.js:3519-3546` | 是 —— I-1、S-2、S-3 |
| X-3 | `setEnabled/setDefault/clearOtherDefaults` 走 `copy()` | 同 X-2 | 是 —— 显式验证 name 不被这三条路径清空（A-6） |
| X-4 | `update()` 写 `name`（触发 `repository.save`） | `frameSlotIdentity` 读 `updatedAt` | 是 —— I-2 附带事实，须由测试锁定 |

### 前端样式盘点

- 可复用 class：
  - `.form-grid` — `index.html:1731` 使用；片段表单栅格容器
  - `.span-2` — `index.html:1732, 1743` 使用；跨两列的表单项，**本计划新字段不用**
  - `.muted` — `styles.css:2845`；未命名占位文案
  - `.muted-cell` — `styles.css:2850`；片段表格内容列既有样式，不改
- 设计基准 token（`styles.css:1-90` 的 `:root`）：`--text-main: #1e293b`、`--text-muted: #94a3b8`、`--panel-border: rgba(15, 23, 42, 0.08)`、`--radius-sm: 7px`、`--font-mono: 'SF Mono', ui-monospace, Menlo, monospace`。本计划不引用它们（无新 CSS），列出仅供交叉核对。
- DOM 结构约定：片段表单 = `<form id="replySnippetForm" class="form-grid">` 内一串 `<label>字段名<input/select/textarea></label>`（`index.html:1731-1765`）；表单读值走 `formValues(form)` + 直接 `form.<name>`（`app.js:3652-3670`）。
- 改动前基线：
  - `index.html:1741`：`<label>片段 ID<input name="id" disabled placeholder="系统自动生成"></label>`
  - `app.js:8058-8059`（map 开始行 + label 行）：
    ```js
    const snippetOptions = enabledSnippets.map((snippet) => {
        const label = `${replySnippetTypeLabels[snippet.snippetType] || snippet.snippetType} #${snippet.id}`;
    ```
  - `app.js:3530-3533`（行首两列）：
    ```js
    return `
        <tr>
            <td class="muted-cell">${escapeHtml((snippet.content || "").slice(0, 120))}</td>
            <td>${snippet.displayOrder}</td>
    ```
  - `MailComposeTemplateService.kt:382-386`：见上文 grep 回执。

---

## 实现方案

### 阶段 A：数据层（遵守 I-5）

**A-1 新增迁移 `V96__add_name_to_reply_snippet.sql`**
```sql
ALTER TABLE reply_snippet
    ADD COLUMN name VARCHAR(120) NULL COMMENT '运营维护的片段显示名，留空时按内容首行摘要显示';
```
禁止在本文件写入任何 `${`（I-5）。不写 UPDATE 回填（Out of scope）。

**A-2 `ReplySnippet.kt`** — 在 `variantGroup` 之后、`createdAt` 之前加 `val name: String? = null`。
位置选择理由：与列的语义分组一致，且默认值 `null` 保证既有测试里手写的 `ReplySnippet(...)` 具名构造不因新参数编译失败（`ReplySnippetServiceTest.kt` 内多处直接构造）。

### 阶段 B：后端 DTO 与服务（遵守 I-3、I-4）

**B-1 `ReplySnippetService.kt`**
- `ReplySnippetCreateCommand`（`:381-389`）加 `val name: String? = null`
- `ReplySnippetUpdateCommand`（`:391-398`）加 `val name: String? = null`
- `create()` 的 `ReplySnippet(...)`（`:195-206`）加 `name = command.name?.trim()?.takeIf { it.isNotBlank() }`
- `update()` 的 `existing.copy(...)`（`:232-240`）加 `name = command.name?.trim()?.takeIf { it.isNotBlank() }` —— **必须显式传**，否则清空不生效（I-3 第 5 层）
- `setEnabled` / `setDefault` / `clearOtherDefaults` **不动**（走 `copy()` 自动保留，I-2 / X-3）
- `validateSnippetType` / `requireValidPlaceholders` 调用点不动（N-6）

**B-2 `ReplySnippetController.kt`**
- `ReplySnippetCreateRequest`（`:60-67`）加 `val name: String? = null`，`toCommand()`（`:69-78`）透传
- `ReplySnippetUpdateRequest`（`:81-87`）加 `val name: String? = null`，`toCommand()`（`:89-97`）透传
- `ReplySnippetResponse`（`:100-109`）加 `val name: String?`
- `ReplySnippet.toResponse()`（`:114-124`）映射 `name = name`

**B-3 `MailComposeTemplateService.kt`** — 遵守 I-1，`REPLY_SNIPPET` 分支（`:382-386`）改为三级回退：
```kotlin
ComposeBlockType.REPLY_SNIPPET -> {
    replySnippetRepository.findById(refId).orElse(null)?.let { snippet ->
        snippet.name?.takeIf { it.isNotBlank() }
            ?: snippetContentExcerpt(snippet.content)
            ?: "${snippet.snippetType} #${snippet.id}"
    }
}
```
新增同文件私有方法（摘要算法必须与前端逐字等价，I-1）：
```kotlin
private fun snippetContentExcerpt(content: String?): String? {
    val firstLine = content?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.isNotEmpty() }
        ?: return null
    return if (firstLine.length > EXCERPT_MAX_CHARS) {
        firstLine.take(EXCERPT_MAX_CHARS) + "…"
    } else {
        firstLine
    }
}
```
`EXCERPT_MAX_CHARS = 40` 放入该类既有 `companion object`。
QA_RULE 分支（`:375-381`）与 `else` 分支（`:387`）**不动**。

### 阶段 C：前端（遵守 I-1、S-1、S-2、S-3、M-1、M-3）

**C-1 `index.html`**
- 按 S-1 在 `:1741` 之前插入片段名称输入。
- 按 M-1 bump 三处缓存键（`:11`、`:1969`、`:1970`）为同一新值，建议 `20260814-v9-snippet-name-01`。
- 按 M-3 核对：新 `input[name="name"]` 的宿主是 `index.html` 写死结构，改完 grep `name="name"` 确认存在。

**C-2 `app.js`**
- 新增共享函数 `replySnippetDisplayLabel(snippet)`，实现 I-1 的三级回退与 40 字摘要（与 B-3 逐字等价）。放在 `replySnippetTypeLabels`（`:2895-2901`）之后，使片段管理与模板编辑器两处都能调用。
- `composeTemplateBlockRowHtml`（`:8059`）：`const label = replySnippetDisplayLabel(snippet);` 其余不动（S-3）。
- `renderReplySnippetRow`（`:3519-3546`，行模板在 `:3530-3546`）：按 S-2 加"名称"列单元格。
- `renderReplySnippetTypePanel`（`:3548-3572`）：按 S-2 加表头 `<th>名称</th>`；空表 `colspan` `6/5` → `7/6`（`:3553`）。
- `fillReplySnippetForm`（`:3616-3636`）：加 `form.name.value = snippet?.name || "";`
- `saveReplySnippet`（`:3639-3676`）：`payload` 加 `name: values.name`（`values` 来自 `formValues(form)`，`:3652`）。create 分支走 `...payload` 已自动带上，无需额外处理。
- **不得**改动 `renderContentVariantRows`（`app.js:7744`）/ `collectContentVariants`（`:7843`）/ `validateContentVariantInputs`（`:7858`）/ `updateContentVariantsCountBadge`（`:7829`）的任何调用（N-5、K-content-variant-input-read-contract；注意该 K 条目正文里的行号已过期，实测值以此处为准）。

### 阶段 D：测试

**D-1 `ReplySnippetServiceTest.kt`** 新增用例（沿用文件既有 Mockito + `ArgumentCaptor` 风格，`:31-52` 已备好 service 实例）：
- `create persists trimmed name` — 传 `name = "  尊称-教授  "`，捕获 save 参数断言 `name == "尊称-教授"`
- `create normalizes blank name to null` — 传 `name = "   "`，断言落库 `name == null`（I-4）
- `update clears name when blank` — existing 有 `name = "旧名"`，update 传 `name = ""`，断言落库 `name == null`（I-3 第 5 层）
- `name does not affect frame version` — 构造两个仅 `name` 不同、其余字段（含 `updatedAt`）完全相同的 `ReplySnippet`，分别经 `resolveSelectableFrame` 断言 `version` 相等（I-2）
- `setDefault preserves name` — existing 带 `name`，调用 `setDefault`，捕获 save 参数断言 `name` 未丢（X-3）

**D-2 新建 `src/test/js/replySnippetLabel.test.js`**（沿用 `composeTemplatePreview.test.js:1-16` 的 `extractFn` + `vm` 范式）：
- `replySnippetDisplayLabel` 有 name → 返回 name
- name 为空串 / 纯空白 → 落到摘要
- 无 name、content 首行 ≤40 → 返回首行原文，**不带** `…`
- 无 name、content 首行 >40 → 返回前 40 字 + `…`，总长 41
- content 以空行开头（如 `"\n\nDear Professor,"`）→ 取首个非空行
- **等价性断言**：从 `MailComposeTemplateService.kt` 源文件文本中读出 `EXCERPT_MAX_CHARS` 的值，断言等于 JS 侧使用的 40（防两侧漂移，I-1）。范式参照 `QaSeedEncodingRepairMigrationTest` 的"对源文件做文本断言"思路。

**D-3 `src/test/js/batchSendTaskConsoleVisualFix.test.js`** — 按 M-1 同步 `:37-39` 三条硬编码断言为 C-1 的新键值。

---

## 变更文件清单

| # | 文件 | 类型 | 改动摘要 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V96__add_name_to_reply_snippet.sql` | 新建 | `ALTER TABLE reply_snippet ADD COLUMN name VARCHAR(120) NULL` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/reply/domain/ReplySnippet.kt` | 修改 | 加 `name: String? = null` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/reply/controller/ReplySnippetController.kt` | 修改 | Create/Update Request + Response + toCommand×2 + toResponse |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt` | 修改 | Create/Update Command + create 构造器 + update copy（含归一化） |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt` | 修改 | `resolveRefDisplayName` REPLY_SNIPPET 分支三级回退 + `snippetContentExcerpt` + `EXCERPT_MAX_CHARS` |
| 6 | `src/main/resources/static/index.html` | 修改 | 片段名称输入（S-1）+ 缓存键三处 bump（M-1） |
| 7 | `src/main/resources/static/app.js` | 修改 | `replySnippetDisplayLabel` + 块下拉标签 + 表格名称列 + 表单回填/保存 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetServiceTest.kt` | 修改 | 新增 5 条用例（D-1） |
| 9 | `src/test/js/replySnippetLabel.test.js` | 新建 | 标签算法 6 条断言（D-2） |
| 10 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改 | 缓存键三条断言同步（M-1） |

文件数 **10**（上限 10）。子系统数 **2**（后端 reply+template 模块 / 前端 static）。新增共享存储字段数 **1**（`reply_snippet.name`）。

**未列入即视为超范围**：`styles.css` 不改（S-1/S-2/S-3 均无新 CSS）；`trust-reply-workbench.js` 不改（N-2）；`trustReplyWorkbenchSharedMount.test.js` 不改（它用正则断言三键**相等**，不含硬编码值，bump 后自动通过）。

---

## 验证命令

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。全量命令见主计划 `## 共享验证命令`。

```bash
# 本计划的后端单测类（快速迭代）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ReplySnippetServiceTest

# 受影响的模板服务单测类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailComposeTemplateServiceTest

# 本计划新增的前端用例（前端权威门禁，M-2）
node --test src/test/js/replySnippetLabel.test.js

# 缓存键断言用例（M-1）
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js

# 空库全量迁移（可选，需本机 Docker；默认被 @EnabledIfSystemProperty 跳过）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 全量回归 + 构建 + 卫生：见主计划 ## 共享验证命令
```

通过判据：上述各条 `mvn test` 命令退出码 0 且 `Tests run: N, Failures: 0, Errors: 0`；各 `node --test` 退出码 0 且 `pass N / fail 0`；`FlywayMigrationIntegrationTest` 若未跑需在验收记录中注明"未执行（无 Docker）"，不得默认为通过。
来源：`CLAUDE.md:5-27`；`node --test` 形式来源 K-js-test-invocation-surface。

---

## 验收标准

- **I-1**：`grep -n "snippetType} #" src/main/resources/static/app.js src/main/kotlin -r` 除 `MailComposeTemplateService.kt` 的第 3 级防御分支外无其他命中；`replySnippetLabel.test.js` 的等价性断言（D-2 最后一条）通过，证明两侧 `EXCERPT_MAX_CHARS` 同值。
- **I-2**：`ReplySnippetServiceTest` 的 `name does not affect frame version` 通过；`grep -n "name" MailComposeTemplateService.kt` 与 `ReplySnippetService.kt` 中 `frameSlotIdentity`/`frameVersion`/`resolveFrameSlot`/`listSelectableFrameOptions` 函数体内无 `name` 出现。
- **I-3**：7 层逐层 grep 存在 `name`：`ReplySnippetController.kt` 的 CreateRequest/UpdateRequest/两个 toCommand/Response/toResponse 共 6 处，`ReplySnippetService.kt` 的两个 Command/create 构造器/update copy 共 4 处。计数需附 grep 输出（M-4）。
- **I-4**：`create normalizes blank name to null` 与 `update clears name when blank` 两条用例通过。
- **I-5**：`grep -c '\${' src/main/resources/db/migration/V96__add_name_to_reply_snippet.sql` 输出 `0`；`grep -n "placeholder-replacement" src/main/resources/application.yml` 仍为 `false` 且本次 diff 未触及该文件。
- **S-1**：`git diff src/main/resources/static/index.html` 中新增行与 S-1 的 DOM 代码块逐字一致；diff 内无 `style=`、无新 class。
- **S-2**：`git diff src/main/resources/static/app.js` 中表格相关新增行与 S-2 代码块逐字一致；`colspan` 由 `6 : 5` 改为 `7 : 6`。
- **S-3**：`app.js:8060` 附近的 `<option ...>` 模板串（属性、`selected` 判定）与改动前逐字一致，diff 仅涉及 `label` 赋值行。
- **M-1**：`grep -o 'v=[^"]*' src/main/resources/static/index.html | sort -u` 只输出 1 个值；该值与 `batchSendTaskConsoleVisualFix.test.js:37-39` 三条断言一致。
- **M-3**：`grep -c 'name="name"' src/main/resources/static/index.html` ≥ 1。
- 回归：执行主计划 `## 共享验证命令` 的全量测试与构建，均通过。

---

## 人工验收清单

### A-1: 新建带名称的片段并在块下拉里看到它
- 前置条件：以运营身份打开管理后台，进入「邮件模板」→「回复片段」。
- 操作步骤：
  1. 点「新建片段」。
  2. 片段类型选「尊语」，**片段名称**填 `教授尊称-正式`，片段内容填 `Dear ${expertFamilyName|Professor},`（35 字符，短于 40 字摘要阈值，供 A-4 复用），显示排序保持 100。
  3. 点「保存片段」。
  4. 切到「邮件模板」子标签，点任一模板的「编辑」。
  5. 把任一内容块的类型切成「回复片段」，展开其下拉。
- 预期结果：下拉中存在选项，文本**逐字**为 `教授尊称-正式`；不存在 `尊语 #` 开头的同一条目。
- 覆盖：I-1、I-3、S-1、S-3、需求 observable outcome 1

### A-2: 未命名的存量片段显示内容摘要而非 #id
- 前置条件：库中存在 `V47__create_reply_snippet.sql:12-22` 种子片段，且未被命名（`name` 为 NULL）。
- 操作步骤：
  1. 进入「邮件模板」→「邮件模板」子标签，编辑任一模板。
  2. 把一个内容块类型切成「回复片段」，展开下拉。
- 预期结果（逐条核对，字符数已按 `V47__create_reply_snippet.sql:14-22` 的种子文本实算）：

  | 种子片段 | 首个非空行长度 | 下拉应显示 |
  |---|---|---|
  | SALUTATION `Dear Professor,` | 15 | `Dear Professor,`（无省略号） |
  | ACK `Thank you for sharing your CV.` | 30 | 原文，无省略号 |
  | ACK `Thank you for sharing your materials.` | 37 | 原文，无省略号 |
  | ACK `Thank you for your prompt reply.` | 32 | 原文，无省略号 |
  | GREETING `Thank you for your email. Please find our answers below.` | 56 | `Thank you for your email. Please find ou…`（前 40 字 + `…`，总长 41） |
  | CLOSING（首行）`Please let us know if you have any further questions.` | 53 | `Please let us know if you have any furth…`（前 40 字 + `…`，总长 41） |

  全下拉内不出现 `#` 加数字的条目。CLOSING 那条尤其要确认：取的是**首行**，不是把 `Best regards,\nTalent Introduction Team` 一起拼进来。
- 覆盖：I-1、需求 observable outcome 2

### A-3: 名称在三处显示一致
- 前置条件：完成 A-1，且存在一个模板，其某个内容块引用了 `教授尊称-正式` 这个片段并已保存。
- 操作步骤：
  1. 在「邮件模板」子标签的模板列表里，找到该模板所在行，查看「内容块」列的 pill。
  2. 点该行「预览」，查看预览面板里的块说明。
  3. 点该行「编辑」，查看内容块下拉的当前选中项文本。
- 预期结果：三处显示的文本**逐字相同**，均为 `教授尊称-正式`。
- 覆盖：I-1、X-1、X-2

### A-4: 清空名称后回落到摘要
- 前置条件：完成 A-1。
- 操作步骤：
  1. 「回复片段」子标签，找到 `教授尊称-正式` 所在行，点「编辑」。
  2. 把「片段名称」输入框内容全部删除（留空），点「保存片段」。
  3. 回到片段表格查看该行「名称」列。
  4. 到模板编辑器展开片段块下拉。
- 预期结果：表格「名称」列显示灰色 `未命名`；下拉里该条显示为 `Dear ${expertFamilyName|Professor},`（首行原文 35 字符，短于阈值，**不加**省略号；占位符原样显示，此处不做变量渲染）。旧名 `教授尊称-正式` 在任何位置都不再出现。
- 覆盖：I-3 第 5 层、I-4、S-2

### A-5: 名称留空空格也算未命名
- 前置条件：无。
- 操作步骤：新建一个「致谢语」片段，名称栏只输入 3 个空格，内容填 `Thank you for the update.`，保存后查看片段表格该行「名称」列。
- 预期结果：显示灰色 `未命名`，不是 3 个空格造成的空白单元格。
- 覆盖：I-4

### A-6: 启用/禁用/设为默认不清空名称（回归）
- 前置条件：存在一个已命名的「开场白」片段，名称 `标准开场-v1`，且当前不是默认。
- 操作步骤：
  1. 在片段表格该行依次点「禁用」→「启用」。
  2. 再点「设为默认」。
  3. 每步之后查看该行「名称」列。
- 预期结果：三步之后名称始终为 `标准开场-v1`，未变空。
- 覆盖：X-3、must-NOT-change N-1

### A-7: 信任回复工作台 frame 下拉不受影响（回归）
- 前置条件：存在若干已命名的 SALUTATION / GREETING / CLOSING 片段。
- 操作步骤：进入收发件箱，打开任一待处理专家的信任回复工作台，展开「尊语 / 开场白 / 结束语」三个下拉。
- 预期结果：每个下拉的选项文本仍是片段**正文全文**（例如 `Dear Professor ${expertFamilyName|Professor},`），**不是**新填的名称。
- 覆盖：must-NOT-change N-2

### A-8: 组装好的回复不因改名失效（回归）
- 前置条件：在信任回复工作台里选定一组 frame 并完成一次组装（存在已锁定版本）。
- 操作步骤：
  1. 记下工作台当前组装结果。
  2. 另开一个页面，去「回复片段」里给这组 frame 中的**任意一个**片段改名（只改名称，不动内容）。
  3. 回到工作台刷新，查看组装状态。
- 预期结果：组装结果与锁定版本仍然有效，未被作废、未提示 frame 版本变化。
- 覆盖：I-2、must-NOT-change N-1

### A-9: 内容变体编辑未被破坏（回归）
- 前置条件：无。
- 操作步骤：新建一个「致谢语」片段，名称填 `致谢-多变体`，内容填 `Thank you for your reply.`，在「内容变体」里添加 2 个变体并填入不同文本，保存；重新打开该片段编辑。
- 预期结果：2 个变体都被保存并回填，变体计数徽标显示 `2 变体`。
- 覆盖：must-NOT-change N-5

### A-11: 片段内容的占位符校验仍然生效（回归）
- 前置条件：无。
- 操作步骤：
  1. 新建一个「致谢语」片段，名称填 `占位符校验测试`，片段内容填 `Thank you, ${expertFamilyName}.`（**故意不写 `|兜底值`**），点「保存片段」。
  2. 记下报错。
  3. 把内容改成 `Thank you, ${expertFamilyName|Professor}.` 再保存。
  4. 再试一次：内容改成 `Thank you, ${notARealKey|X}.` 保存。
- 预期结果：步骤 1 保存失败并提示占位符非法（nullable 变量缺兜底值）；步骤 3 保存成功；步骤 4 保存失败（未知变量名）。新增的「片段名称」字段不影响这三种判定。
- 覆盖：must-NOT-change N-6

### A-10: 页面加载无缓存残留（UI 目测）
- 前置条件：改动已部署。
- 操作步骤：硬刷新后台页面，打开浏览器开发者工具的 Network 面板，筛选 `styles.css`、`app.js`、`trust-reply-workbench.js` 三个请求。
- 预期结果：三者的 `?v=` 查询串**完全相同**，且不等于改动前的 `20260814-v8-expert-layout-default-01`。片段编辑弹窗中「片段名称」输入框与「片段 ID」「显示排序」在同一栅格行列节奏内，无错行、无溢出。
- 覆盖：M-1、S-1

---

## Phase 6 知识回写

### 已由 create-p 在写计划阶段完成（无需执行 agent 再做）

- **bump** 已使用条目的 `hit_count` / `last_used`（2026-08-14）：K-variant-pool-dto-chain(9)、K-spring-data-jdbc-null-default(1)、K-flyway-placeholder-replacement(2)、K-dead-template-field-save-ignore(2)、K-content-variant-input-read-contract(10)、K-frontend-cache-key-triad(4)、K-js-test-invocation-surface(2)、K-dom-stub-tests-hide-dangling-refs(2)、K-plan-quantified-claims-need-grep-receipts(1)。
- **提升至 `CLAUDE.md`**：`K-content-variant-input-read-contract` 的 `hit_count` 本轮达到 10，越过提升阈值，已在 `CLAUDE.md`「团队沉淀知识」新增一行摘要（带 `(K-content-variant-input-read-contract)` 回指）。

### 待执行 agent 在实施后补做

1. **更新** `docs/knowledge/mail/K-spring-data-jdbc-null-default.md` —— 补一条推论：`update()` 走 `existing.copy(...)` 时 `updated_at` 被显式写回旧值，MySQL 的 `ON UPDATE CURRENT_TIMESTAMP` 因此**不触发**。这既是本计划 I-2 的依据（改名不动 frameVersion），也意味着 `reply_snippet.updated_at` 实际停在创建时刻——**若将来有人依赖它做"最近修改"排序或缓存失效，会静默失效**。本轮不修，作为已知事实记录。实施时若测试证伪该推论（`updatedAt` 确实变了），必须**改这条知识 + 改 I-2 的论证 + 重新评估 A-8**，不得只改测试断言。
2. **更新** `docs/knowledge/template/K-variant-pool-dto-chain.md` —— 若本轮实施中发现 7 层清单有缺项（例如本仓库另有第 8 个必改点），就地补齐并 bump `created`（视为复验）。
