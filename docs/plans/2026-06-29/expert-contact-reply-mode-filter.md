# 专家列表「回复模式」筛选（自动模式 / 人工模式）

> 计划产出：create-p ｜ 日期：2026-06-29 ｜ 关联讨论：318 误自动回复排查

## 需求描述

**可观察结果**：专家列表筛选区新增一个独立下拉「回复模式」，可选「全部 / 自动模式 / 人工模式」。选「人工模式」只显示系统不会自动回复的专家，选「自动模式」只显示下一封来信系统会自动回复的专家。

**必须不变**：
- 现有「人工干预」下拉（`needs_manual_attention`，含「仅看需要干预 / 仅看自动流转中」）的语义和行为完全不动。
- 现有 ES 查询路径 `/api/experts`、标签/地区/服务商筛选、聚合计数不变。
- 不新增/修改任何数据库字段，不写迁移（`auto_reply_enabled`、`current_status` 均已存在）。
- 不改任何自动回复闸门、状态机、写路径（纯读侧筛选）。
- `findFilteredContacts` / `listContacts` 现有调用在不传新参数时行为与现在完全一致。

**不做（明确推迟）**：
- 单专家「自动/人工」开关按钮（pause/resume 接入前端）——另起计划。
- 让 ES 路径 `/api/experts` 也支持按回复模式筛选——本计划只走 MySQL 路径。
- 列表行内展示「回复模式」列/徽标——本计划只做筛选，不做展示。
- 修复 `auto_reply_enabled` 与 `current_status` 两标志可能不一致的历史数据——筛选口径用「实际会不会自动回」吸收该不一致，不在此清洗数据。

## 关键不变量

### Invariant I-1: 回复模式口径对齐自动回复闸门
- Rule: 「人工模式」≝ `auto_reply_enabled = false OR current_status = 'MANUAL_HANDOFF'`；「自动模式」≝ 其严格否定 `auto_reply_enabled = true AND current_status <> 'MANUAL_HANDOFF'`。两侧互补且无交集、无遗漏。
- 依据：与 `AutoMailReplyService.kt:106` 的跳过条件（`!autoReplyEnabled || currentStatus==MANUAL_HANDOFF`）逐字对齐——筛选结果即「实际会/不会自动回」。
- Applies to: `ExpertContactRepository.findFilteredContacts` 的 SQL；前端不得在别处用单字段近似（如只看 `autoReplyEnabled`）替代。
- Violation consequence: 若口径只取 `auto_reply_enabled`，则 318 那类「开关 true 但已 MANUAL_HANDOFF」的记录会被误标为自动模式，筛选无法暴露真实风险；两侧若不互补会漏记录。
- 来源: original

### Invariant I-2: 回复模式筛选只认 MySQL 真值，必须走 DB 查询路径
- Rule: `auto_reply_enabled` 与 `current_status` 是 MySQL `expert_contact` 的事实，ES 索引不持有。选中回复模式筛选时，列表必须走 `/api/expert-contacts`（MySQL）路径，禁止走 `/api/experts`（ES）路径。
- Applies to: 前端 `loadContacts()` 的路径分支（现仅 `needsAttention` 触发 DB 路径，`app.js:2030`）。
- Violation consequence: 走 ES 路径时该筛选无字段可依据，会静默失效（返回未过滤结果），运营误判。
- 来源: original（审计 `app.js:2030/2060` 双路径得出）

### Invariant I-3: 新参数 null 安全、向后兼容
- Rule: 新增的 `replyMode` 参数可空；为 `null` 时 SQL 不施加任何回复模式过滤，等价于现状。Controller `@RequestParam(required = false)`，Service/Repository 形参 `String?` 默认 `null`。
- Applies to: `findFilteredContacts`、`ExpertContactManagementService.listContacts`、`ExpertContactManagementController.listContacts`。
- Violation consequence: 任何现有不带该参数的调用（含未来）若被迫传值会编译失败或改变行为。
- 来源: original

### Invariant I-4: 与「人工干预」筛选正交可叠加
- Rule: 回复模式筛选与 `needsAttention` 筛选作用于不同列，二者可同时生效（AND 关系），互不覆盖、互不禁用对方。
- Applies to: SQL（两段独立 `AND`）、前端路径选择（任一选中即走 DB 路径并合并参数）。
- Violation consequence: 若塞进同一下拉或互斥，则「人工模式 且 有待办」这类组合无法查询（正是本次设计要避免的）。
- 来源: original

## 现状审计

### MySQL `expert_contact` 表
- Schema：`ExpertContact.kt` data class，关键列 `auto_reply_enabled`（Boolean，默认 true，`:20`）、`current_status`（String，默认 "NEW"，`:15`）、`needs_manual_attention`（Boolean，默认 false，`:25`）、`operator_status`（默认 "NOT_CONTACTED"，`:24`）。Spring Data JDBC，无 JPA。
- 读路径（与本计划相关）：
  1. `ExpertContactRepository.findFilteredContacts`（`:41-54`）—— 当前 4 条件 `@Query`：`campaignId / status / operatorStatus / needsAttention`，模式均为 `:p IS NULL OR col = :p`，`ORDER BY updated_at DESC`。**唯一**待改读路径。
  2. `ExpertContactManagementService.listContacts`（`:40-41`）—— 透传到上面。**唯一**调用者（grep 确认）。
  3. `ExpertContactManagementController.listContacts`（`:45-57`）—— `@GetMapping` `/api/expert-contacts`，4 个 `@RequestParam(required=false)`。**唯一**调用 service 者（grep 确认）。
- 写路径：本计划**不涉及任何写路径**（纯读侧筛选）。`auto_reply_enabled` 的写路径（pause/resume/switch-to-manual/switch-to-auto/handoff、`AutoMailReplyService` 自动停）不在本计划范围，无需改动。
- 测试：grep `src/test` 无任何用例引用 `findFilteredContacts` / `listContacts`，签名扩展（带默认值）无破坏面。

### 前端 `app.js` 列表加载 `loadContacts()`（`:1965-2121`）
- **双查询路径**（关键交互点）：
  1. `if (needsAttention)` → 走 MySQL `/api/expert-contacts`（`:2030-2059`），前端切片分页。
  2. `else` → 走 ES `/api/experts`（`:2060-2090`）。
- ES 路径不持有 `auto_reply_enabled`；故回复模式筛选必须并入路径 1（见 I-2）。
- 选 `needsAttention` 时已会禁用标签/地区筛选（`:1977-1990`，"仅在 ES 查询模式下可用"）——回复模式选中时需同样处理。
- 排序兜底 `:2104` 已对 `operatorStatus || needsAttention` 触发前端按 updatedAt 排序；新增条件应并入。
- 前端 contact 映射（`:2044-2058` / `:2074-2088`）当前未携带 `autoReplyEnabled`，但本计划不展示该字段，无需补。

### 前端 `index.html` 筛选区
- 现有「人工干预」下拉 `#contactNeedsAttentionFilter`（`:410-414`，三项：`"" / true / false`）。新下拉紧邻其后新增，独立 `<select>`，不动此元素。

### 交互点汇总
- IP-1：写路径（自动回复闸门 `AutoMailReplyService.kt:106`）定义了「会不会自动回」，读路径（新 SQL 条件）必须用同一口径 → I-1 约束。
- IP-2：MySQL 写真值 vs 前端两条查询路径 → 必须强制走 DB 路径 → I-2 约束。
- IP-3：回复模式筛选 × `needsAttention` 筛选共存 → I-4 约束。

## 实现方案

### Stage 1：后端筛选（SQL → Service → Controller）

**Task 1.1 —— Repository 增加 `replyMode` 条件**（遵守 I-1, I-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt`
- 在 `findFilteredContacts` 的 `@Query` `ORDER BY` 之前追加：
  ```sql
  AND (:replyMode IS NULL
       OR (:replyMode = 'MANUAL' AND (auto_reply_enabled = false OR current_status = 'MANUAL_HANDOFF'))
       OR (:replyMode = 'AUTO'   AND auto_reply_enabled = true AND current_status <> 'MANUAL_HANDOFF'))
  ```
- 形参追加 `replyMode: String? = null`（放末位，保持既有顺序）。
- 口径必须与 I-1 逐字一致；`auto_reply_enabled = false` 在 MySQL 对 tinyint(1) 等价 `= 0`。

**Task 1.2 —— Service 透传**（遵守 I-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt`
- `listContacts(...)`（`:40`）签名追加 `replyMode: String? = null`，透传给 `findFilteredContacts(..., replyMode)`。

**Task 1.3 —— Controller 暴露查询参数**（遵守 I-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt`
- `listContacts(...)`（`:45-57`）追加 `@RequestParam(required = false) replyMode: String?`，传入 `service.listContacts(..., replyMode)`。
- 可选健壮性：非 `AUTO`/`MANUAL`/null 的值视为 null（不抛错），避免坏参数返回空集误导。

### Stage 2：前端筛选下拉与路径路由

**Task 2.1 —— 新增下拉**（遵守 I-4）
文件：`src/main/resources/static/index.html`
- 在 `#contactNeedsAttentionFilter` 的 `</label>`（`:415`）之后，新增并列 `<label class="toolbar-label">回复模式: <select id="contactReplyModeFilter"><option value="">全部回复模式</option><option value="AUTO">自动模式</option><option value="MANUAL">人工模式</option></select></label>`。

**Task 2.2 —— 读取并路由到 DB 路径**（遵守 I-2, I-4）
文件：`src/main/resources/static/app.js`，`loadContacts()`
- 顶部读取 `const replyMode = $("#contactReplyModeFilter")?.value || "";`（紧邻 `:1969`）。
- 路径判定改为：`if (needsAttention || replyMode) { 走 /api/expert-contacts }`（替换 `:2030` 的 `if (needsAttention)` 与 `:1977` 标签/地区禁用判定的触发条件，二者统一用 `needsAttention || replyMode`）。
- DB 分支内（`:2031-2034`）：`if (needsAttention) params.set("needsAttention", needsAttention);` 保持；新增 `if (replyMode) params.set("replyMode", replyMode);`。注意 `needsAttention` 改为按需 set（当前是无条件 set，需调整为仅在有值时 set，否则只选回复模式时会误带空 needsAttention——验证 `URLSearchParams` 空值行为，空字符串会被后端当非 null，需避免）。
- 标签/地区禁用块（`:1977-2002`）的 `if (needsAttention)` 改为 `if (needsAttention || replyMode)`。
- 排序兜底（`:2104`）`(operatorStatus || needsAttention)` 改为 `(operatorStatus || needsAttention || replyMode)`。
- 绑定 change 事件触发 `loadContacts()`（与现有筛选下拉同一处注册，按现有模式补一行）。

## 变更文件清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt` | `findFilteredContacts` 加 `replyMode` 形参 + SQL 条件 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt` | `listContacts` 加形参并透传 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt` | `listContacts` 加 `@RequestParam replyMode` |
| 4 | `src/main/resources/static/index.html` | 新增 `#contactReplyModeFilter` 下拉 |
| 5 | `src/main/resources/static/app.js` | `loadContacts()` 读参、路由 DB 路径、禁用 ES-only 筛选、排序兜底、change 绑定 |

文件数：5（≤10）。子系统：后端筛选链 + 前端列表（2，≤2）。新增共享存储字段：0。

## 验收标准

- **I-1**：构造 4 组 MySQL 数据：(a) `auto_reply_enabled=1, status=QA_AUTO_REPLIED`、(b) `auto_reply_enabled=0, status=QA_AUTO_REPLIED`、(c) `auto_reply_enabled=1, status=MANUAL_HANDOFF`、(d) `auto_reply_enabled=0, status=MANUAL_HANDOFF`。`replyMode=AUTO` 只返回 (a)；`replyMode=MANUAL` 返回 (b)(c)(d)；两集合并集 = 全集、交集为空。其中 (c) 正是 318 类记录，必须落在 MANUAL 集合。
- **I-2**：前端选「自动模式」时，网络请求命中 `/api/expert-contacts`（非 `/api/experts`），且带 `replyMode=AUTO`。
- **I-3**：`replyMode` 不传时 `GET /api/expert-contacts` 返回结果与改动前一致（同一过滤集合）；现有 4 参数调用编译通过、行为不变。
- **I-4**：同时选「人工模式」+「仅看需要干预」，结果 = `(auto_reply_enabled=false OR MANUAL_HANDOFF) AND needs_manual_attention=true`；两下拉互不禁用、互不清空。
- **集成（IP-1）**：选「自动模式」筛出的每条记录，喂入自动回复管线模拟（或对照 `AutoMailReplyService.kt:106` 条件）均判定为「会自动回」；选「人工模式」的均判定为「跳过」。
- **回归**：仅选标签/地区（不选回复模式、不选人工干预）时仍走 ES 路径 `/api/experts`，标签/地区下拉可用；选回复模式时标签/地区被禁用并置灰。
- **构建/测试**：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` 通过。
