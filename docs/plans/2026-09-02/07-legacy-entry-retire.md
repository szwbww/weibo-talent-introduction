# 07 旧链路入口下线：旧 QA 页摘除、`qa_rule` 转只读、旧工作台端点摘除

> 顺序权威：`00-execution-order.md`。**依赖 04、05、06 全部落地并验收通过**。
> 全局不变量 G-1 ~ G-8 适用，本文不重复定义。

## 需求描述

**Observable outcome**

1. 后台不再有任何入口能编辑 `qa_rule`：旧「QA 知识库」子 Tab 的残余 DOM 与 JS 全部移除，
   `/api/qa` 的四个写端点（新增 / 修改 / 启用 / 停用）返回 `403 QA_RULE_READ_ONLY`。
2. `/api/trust-reply/workbench/*` 的九个端点全部移除；前端已无调用方。
3. `qa_rule` 表的数据一行不删，读路径（已发信件存证、来信标签、模板 QA_RULE 块、监控页显示名）
   全部照常工作。

**What must NOT change**

1. `qa_rule` / `qa_category` 的表结构与数据。
2. `mail_record_qa_rule` 的写入与读取（已发信件的 QA 使用审计）。
3. `inbound_mail_tag` 的自动打标签与手动打标签（`InboundMailTagService`）。
4. `MailComposeTemplateService` 的 `QA_RULE` 块渲染。
5. `MailMonitoringService` 中 `matchedQaRuleDisplayName` 的展示。
6. 新链路（01-06）的任何行为。

**Out of scope**

- **旧「自由回复系统提示词 + 约束项」表单的移除**（D-14）。它配置的是
  `AiReplyDraftService` 的 FREE_FORM 兜底路径，而该路径在本计划之后**仍在运行**——
  `mail/controller/UnmatchedInboundMailController.kt:366,378` 与
  `llm/controller/AiTrainingController.kt:222` 仍调用 `aiReplyDraftService.generate`，
  本计划不动这两处。删表单会让一条仍在跑的路径失去配置入口。随 X-4 一起处置。
- **旧后端 Kotlin 死代码的物理删除**（`TrustReplyWorkbenchService` 3135 行、
  `AiReplyDraftService` 的 grounded 部分 2724 行、`AiReplyGroundedContentPlanner`、
  `AiReplyGroundedDraftMaterializer`、`AiReplyPointByPointComposer`、`AiReplyLetterOrchestrator`
  等）——见 D-10，另开计划。本计划只摘入口，代码留在原地。
- `inbound_mail_tag` 与 `MailComposeTemplateService` 迁移到 `rag_fact`（登记为 X-2、X-3）。
- 自动回复链路（`AutoMailReplyService` / `GroundedAutoReplyDecisionService` /
  `AutoReplyConfidenceScorer`）的处置——它们仍读 `qa_rule` 的**读**路径，不受本计划影响。

## 关键不变量

### I-35: 只停写不删数据
- Rule: 本计划**不得**包含任何 `DELETE FROM qa_rule` / `DROP TABLE qa_rule` /
  `TRUNCATE` 语句，也不得把 `qa_rule.enabled` 批量置 0。
  停写只通过应用层端点返回 403 实现。
- Applies to: 本计划的全部迁移与代码改动。
- Violation consequence: `mail_record_qa_rule` 与 `inbound_mail_tag` 的外键指向消失，
  历史发信记录变成「用了 #37 号规则（已不存在）」，退信排查与纠纷复盘断链。
- 来源: D-9

### I-36: 读路径必须逐条验证仍然工作
- Rule: 停写后，`qa_rule` 的 11 条读路径（见现状审计）必须逐条验证仍可读到数据。
  停写只作用于 `QaRuleManagementService` 的写方法，不得误伤 `QaRuleRepository` 的读方法。
- Applies to: `QaRuleManagementController` / `QaRuleManagementService`。
- Violation consequence: 误把读也挡掉 → 来信打标签、模板渲染、监控页同时失效。
- 来源: original

### I-37: 前端删 DOM 必须同步退役契约测试
- Rule: 删除 `aiTabQa` 残余 DOM 与 `renderAiTrainingQaPager/Table/loadAiTrainingQa` 三个函数时，
  必须先 grep 全部断言它们的测试文件，同步删除或改写，并列入变更文件清单。
- Applies to: `src/test/js/` 下全部涉及 QA 表格的测试。
- Violation consequence: 全量测试持续失败并阻塞发布。
- 来源: G-7 / K-ui-removal-retires-obsolete-contract-tests

### I-38: 删端点必须同步退役其 Kotlin 测试
- Rule: 移除 `/api/trust-reply/workbench/*` 九个端点时，
  `TrustReplyWorkbenchControllerTest.kt` 必须同步删除或改写。
  **本计划只删 Controller 的端点方法与该测试**，不动 `TrustReplyWorkbenchService`
  及其四个服务层测试（D-10）。
- Applies to: `llm/controller/TrustReplyWorkbenchController.kt`、
  `src/test/kotlin/.../llm/controller/TrustReplyWorkbenchControllerTest.kt`。
- Violation consequence: 删了端点不删测试 → 编译失败或全量测试失败。
- 来源: G-7

## 现状审计

### `qa_rule` 的写路径（本计划要停的）
- 唯一应用层写入点：`qa/service/QaRuleManagementService.kt`。
- 对外端点：`qa/controller/QaRuleManagementController.kt:30` `@RequestMapping("/api/qa")`
  - `:112` `@PostMapping("/rules")` — 新增
  - `:116` `@PutMapping("/rules/{ruleId}")` — 修改
  - `:123` `@PostMapping("/rules/{ruleId}/enable")` — 启用
  - `:127` `@PostMapping("/rules/{ruleId}/disable")` — 停用
  - 另有分类写端点 `:96 POST /categories`、`:100 enable`、`:104 disable`——
    **一并停写**（分类只服务于旧 QA 库）。
- 迁移写入：`V1`, `V3`, `V38`, `V41`, `V52`, `V57`, `V68`, `V75`, `V105`, `V109` —— 已应用，不动。

### `qa_rule` 的读路径（本计划必须保住的 11 条）
| # | 位置 | 用途 |
|---|---|---|
| 1 | `llm/service/QaFactSelectionService.kt:33,214` | `findAllEnabledOrdered()` |
| 2 | `llm/service/QaFactSelectionService.kt:149,469` | `findById` |
| 3 | `llm/service/AiReplyDraftService.kt:743,901,1066,2210,2407,2461,2527,2553,2671` | `findById` |
| 4 | `llm/service/TrustReplyWorkbenchService.kt:2597,2684,2783` | 工作台事实元数据 |
| 5 | `llm/service/AiReplyHighRiskClaimValidator.kt:227` | 高危 claim 校验 |
| 6 | `llm/service/AiReplyPointByPointComposer.kt:136` | 逐点组装 |
| 7 | `mail/service/PendingMailOperationService.kt:558,1147` | 待办面板 |
| 8 | `mail/service/GroundedAutoReplyDecisionService.kt:107,227` | 自动回复判定 |
| 9 | `mail/service/InboundMailTagService.kt:57,76,132` | **来信打标签**（必须保住） |
| 10 | `template/service/MailComposeTemplateService.kt:383,467` | **模板 QA_RULE 块**（必须保住） |
| 11 | `monitoring/service/MailMonitoringService.kt:133` | **监控页显示名**（必须保住） |
| — | `qa/controller/QaRuleManagementController.kt:108` `GET /rules` | 只读端点，**保留** |

- **Interaction point 1**：`QaRuleManagementService`（停写）× 读路径 9/10/11。
  停写实现若下沉到 Repository 或加了全局拦截器，会连读一起挡掉。
  必须只改 Controller 层的四个写端点 + 三个分类写端点（I-36）。A-3 / A-4 / A-5 验收。

### 旧工作台端点（本计划要删的）
`llm/controller/TrustReplyWorkbenchController.kt:44` `@RequestMapping("/api/trust-reply/workbench")`
- `:50` `POST /bootstrap`
- `:62` `POST /generations/stream`（SSE）
- `:94` `POST /generations/{generationId}/cancel`
- `:106` `POST /assemble`
- `:114` `POST /rearrange`
- `:118` `PUT /state`
- `:127` `PATCH /state/item`
- `:131` `DELETE /state`
- `:135` `POST /state/reset`

调用方核查（执行前必须重跑）：
```
grep -rn "trust-reply/workbench" src/main/resources/static/ src/test/
```
05 落地后前端应已无调用；若仍有输出，说明 05 未完成，本计划不得开工。

### 旧 QA 子 Tab 的前端残余（04 已改按钮与面板 id，本计划删函数）
- `app.js:3316` `renderAiTrainingQaPager()`
- `app.js:3331` `renderAiTrainingQaTable()`
- `app.js:3429` `loadAiTrainingQa()`
- 04 已把 `index.html:830` 的面板 id 从 `aiTabQa` 改为 `aiTabRagKb` 并替换内容；
  本计划核查是否还有 `aiTabQa` / `data-tab="qa"` 残留：
  `grep -n "aiTabQa\|data-tab=\"qa\"" src/main/resources/static/index.html`。

### 契约测试盘点（I-37 / I-38）
执行前必须跑，不得照抄本清单：
```
grep -rln "loadAiTrainingQa\|renderAiTrainingQaTable\|aiTabQa" src/test/js/
grep -rln "trust-reply/workbench" src/test/
grep -rln "/api/qa/rules" src/test/
```
已知必然命中：`src/test/kotlin/.../llm/controller/TrustReplyWorkbenchControllerTest.kt`。

### 缓存键（G-5）
本计划触及 `index.html` 与 `app.js`，必须 bump 三联。当前值取决于 04/05/06 的落地顺序，
执行前用 G-5 的复核命令读取实际值，bump 为 `20260902-legacy-retire`。

## 样式契约

本计划**只删 DOM 与 JS，不新增任何 DOM，不新增任何 class**。

### S-1: 无新增样式
- 复用：无。
- 新增：无。
- DOM 结构：只有删除，无新增骨架。
- 禁止项：本计划不得新增或修改任何 CSS 规则块。
  `git diff src/main/resources/static/styles.css` 必须为**空**
  （除非 grep 证明某个 class 在 04/05/06 之后已无任何使用点，
  此时可删除该规则块，并在 PR 中逐条列出 grep 证据）。

## 实现方案

### T1 — 前置核查（不通过则不开工）
按现状审计的三条 grep 逐条执行，确认：
- `src/main/resources/static/` 下无 `trust-reply/workbench` 命中；
- `index.html` 下无 `aiTabQa` / `data-tab="qa"` 命中；
- 记录契约测试盘点的实际命中清单，据此补全变更文件清单。

### T2 — `/api/qa` 写端点停写
修改 `qa/controller/QaRuleManagementController.kt`：
在 `:96 / :100 / :104 / :112 / :116 / :123 / :127` 七个写端点方法体开头统一抛
`ResponseStatusException(HttpStatus.FORBIDDEN, "QA_RULE_READ_ONLY")`，
方法签名与路由**保留**（保留路由是为了给调用方一个明确的 403，而不是 404 的歧义）。
`:108 GET /rules` 与其余只读端点**一行不动**（I-36）。
`QaRuleManagementService` 的写方法**保留不删**（D-10）。

### T3 — 旧工作台端点删除
修改 `llm/controller/TrustReplyWorkbenchController.kt`：删除九个端点方法与随之无用的
请求/响应 DTO 与 `toDomain()` 转换函数；`TrustReplyWorkbenchService` 与
`AiReplyGenerationCoordinator` 的注入随之移除。
若删空后整个类无剩余端点，则整文件删除。
同步删除 `src/test/kotlin/.../llm/controller/TrustReplyWorkbenchControllerTest.kt`（I-38）。

### T4 — 前端残余清理
- `app.js`：删除 `renderAiTrainingQaPager` / `renderAiTrainingQaTable` / `loadAiTrainingQa`
  三个函数及其内部只被它们使用的辅助函数；删除 `state.aiTraining` 中只服务于旧 QA 表格的字段。
- `index.html`：核查并删除任何 `aiTabQa` 残留（04 之后应已无）。
- 删除前对每个待删函数跑 `grep -rn "<函数名>" src/main/resources/static/ src/test/js/`，
  确认无剩余调用方（G-8：DOM stub 测试不会替你发现悬空引用）。

### T5 — 契约测试退役（I-37 / I-38）
按 T1 记录的实际命中清单，逐个删除或改写。改写的判据：
该测试若还在断言别的活功能则改写，若整份都在断言已删功能则删除。

### T6 — 缓存键（G-5）
先跑复核命令读取当前值，三处 + 固定值测试同步 bump。

### T7 — 新增回归测试
新建 `src/test/kotlin/.../qa/QaRuleReadOnlyTest.kt`：
- 断言七个写端点返回 403 且 body code 为 `QA_RULE_READ_ONLY`
- 断言 `GET /api/qa/rules` 仍返回 200 且数据非空（I-36）

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt` | 修改（七个写端点抛 403） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt` | 删除（或删空九个端点） |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt` | 删除 |
| 4 | `src/main/resources/static/app.js` | 修改（删三个函数 + 残余状态字段） |
| 5 | `src/main/resources/static/index.html` | 修改（三处缓存键；如有 `aiTabQa` 残留则删） |
| 6 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改（缓存键 49-51） |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/qa/QaRuleReadOnlyTest.kt` | 新增 |
| 8-10 | **由 T1 的 grep 结果补全**（旧 QA 表格契约测试，预留 3 个名额） | 删除或改写 |

文件数 ≤10，子系统 2（后端 controller 层 + 前端静态页）。
若 T1 的 grep 命中超过 3 个测试文件导致超限，**必须拆分**：先做 T2+T7（后端停写），
再做 T3~T6（前端与端点清理）。执行时在 PR 描述中注明是否拆分。

## 验证命令

> 本项目必须用 JDK 11（zulu-11）。前端 JS 用例由 `exec-maven-plugin` 绑在 `mvn test` 的 test 阶段
> （`pom.xml:186-232`）。

```bash
# T1 前置核查（不通过不开工）
grep -rn "trust-reply/workbench" src/main/resources/static/ src/test/
grep -n "aiTabQa\|data-tab=\"qa\"" src/main/resources/static/index.html
grep -rln "loadAiTrainingQa\|renderAiTrainingQaTable\|aiTabQa" src/test/js/
grep -rln "/api/qa/rules" src/test/

# 缓存键复核
grep -rn "v=$(grep -o 'styles.css?v=[^"]*' src/main/resources/static/index.html | cut -d= -f3)" src/test/js/

# 全量测试（回归门禁，含前端 JS 用例）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增的测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaRuleReadOnlyTest

# 读路径回归（必须全绿）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=InboundMailTagServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailComposeTemplateServiceTest

# 前端 JS 全量 + 语法检查
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# styles.css 必须无改动
git diff --stat src/main/resources/static/styles.css

# 确认没有任何删数据的 SQL
git diff | grep -iE "DELETE FROM qa_rule|DROP TABLE qa_rule|TRUNCATE"

# 空白/换行卫生
git diff --check
```

通过判据：`mvn test` 退出码 0 且 `Tests run: N, Failures: 0, Errors: 0`；
`node --test` 退出码 0 且 `# fail 0`；
`git diff --stat src/main/resources/static/styles.css` 无输出；
删数据 SQL 的 grep 无输出；`git diff --check` 无输出。
来源：`CLAUDE.md:10-27` Commands + `CLAUDE.md:66` 团队沉淀知识。
（`InboundMailTagServiceTest` / `MailComposeTemplateServiceTest` 的确切类名以
`find src/test/kotlin -iname "*InboundMailTag*" -o -iname "*MailComposeTemplate*"` 的结果为准，
执行时若类名不同以实际为准并在 PR 中注明。）

## 验收标准

- **I-35**：`git diff | grep -iE "DELETE FROM qa_rule|DROP TABLE qa_rule|TRUNCATE"` 无输出；
  本计划**不含任何新迁移文件**。
- **I-36**：`QaRuleReadOnlyTest` 断言七个写端点 403、`GET /api/qa/rules` 200 且列表非空；
  `InboundMailTagServiceTest` 与 `MailComposeTemplateServiceTest` 全绿。
- **I-37**：`grep -rn "loadAiTrainingQa\|renderAiTrainingQaTable\|aiTabQa" src/main/resources/static/ src/test/js/`
  无输出。
- **I-38**：`grep -rn "trust-reply/workbench" src/main src/test` 无输出；
  `TrustReplyWorkbenchControllerTest.kt` 已删除。
- **S-1**：`git diff --stat src/main/resources/static/styles.css` 无输出
  （若确有 class 删除，PR 中必须逐条附 grep 证据）。
- **G-5**：三处 `?v=` 同值且等于 `20260902-legacy-retire`；固定值测试同步更新。
- 回归：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 旧 QA 编辑入口彻底消失
- 前置条件: 04-06 已落地并验收通过；应用已启动。
- 操作步骤:
  1. 进「AI 回复训练」，查看子 Tab 列表。
  2. 用 curl 执行 `POST /api/qa/rules`（body 随意）。
  3. 用 curl 执行 `PUT /api/qa/rules/1`。
  4. 用 curl 执行 `POST /api/qa/rules/1/disable`。
- 预期结果: 第 1 步子 Tab 为「RAG 知识库 / 对话范例 / AI 提示词与约束 / 历史邮件模拟回复 /
  无依据回答索引」，**没有**「QA 知识库」；
  第 2-4 步均返回 HTTP **403**，body 中 code 为 `QA_RULE_READ_ONLY`（不是 404、不是 500）。
- 覆盖: 需求 observable outcome 1；I-35

### A-2: 旧工作台端点已移除
- 前置条件: A-1 已通过。
- 操作步骤: 用 curl 执行 `POST /api/trust-reply/workbench/bootstrap`。
- 预期结果: 返回 HTTP 404。
- 覆盖: 需求 observable outcome 2；I-38

### A-3: 来信打标签仍然可用（跨路径回归）
- 前置条件: 库中有一封未打标签的来信。
- 操作步骤:
  1. 打开「收发件箱」，选中该来信。
  2. 查看是否自动带出 QA 标签。
  3. 手动添加一个 QA 标签，再删除它。
- 预期结果: 第 2 步标签正常显示（含规则名）；第 3 步添加与删除都成功。
- 覆盖: 现状审计 Interaction point 1；读路径 9；What must NOT change 第 3 条

### A-4: 模板 QA_RULE 块仍然渲染（跨路径回归）
- 前置条件: 存在一个含 `QA_RULE` 块的邮件组装模板。
- 操作步骤: 打开该模板的预览。
- 预期结果: `QA_RULE` 块正常渲染出规则正文与显示名；不出现「QA 规则不存在」。
- 覆盖: 现状审计 Interaction point 1；读路径 10；What must NOT change 第 4 条

### A-5: 监控页显示名仍然可读（跨路径回归）
- 前置条件: 库中有带 `matched_qa_rule_id` 的邮件记录。
- 操作步骤: 打开邮件监控页，查看命中规则那一列。
- 预期结果: 显示规则名而非空白或 id。
- 覆盖: 现状审计 Interaction point 1；读路径 11；What must NOT change 第 5 条

### A-6: 已发信件的 QA 审计仍然可查（回归）
- 前置条件: 库中有已发出的、带 `mail_record_qa_rule` 关联的信件。
- 操作步骤:
  1. 执行 `SELECT COUNT(*) FROM mail_record_qa_rule;` 并与实施前记录比对。
  2. 在后台打开一封这样的信件，查看其 QA 使用审计。
- 预期结果: 行数不变；审计正常展示所用规则。
- 覆盖: What must NOT change 第 2 条；I-35

### A-7: 数据一行未删（回归）
- 前置条件: 实施前已记录 `SELECT COUNT(*) FROM qa_rule;` 与 `qa_category` 的行数。
- 操作步骤: 重新执行这两条 count。
- 预期结果: 与实施前**完全相同**。
- 覆盖: What must NOT change 第 1 条；I-35

### A-8: 新链路不受影响（回归）
- 前置条件: A-1 已通过。
- 操作步骤:
  1. 打开一封来信的可信工作台，生成一次草稿。
  2. 进「RAG 知识库」改一条事实再改回。
  3. 进「AI 提示词与约束」查看两张清单。
- 预期结果: 三处行为与 07 实施前完全一致。
- 覆盖: What must NOT change 第 6 条

## 已登记的后续项

- **X-2**：`inbound_mail_tag` 的打标签链路迁移到 `rag_fact`。存量标签的
  `qa_rule_id` 外键仍指向旧表，迁移方案需单独设计。
- **X-3**：`MailComposeTemplateService` 的 `QA_RULE` 块改为引用 `rag_fact`，或废弃该块类型。
- **X-4**（= D-10）：旧后端 Kotlin 死代码物理删除。执行前需重跑全仓引用核查，
  因为自动回复链路（`GroundedAutoReplyDecisionService` 等）仍在引用其中一部分。
  **旧「自由回复系统提示词 + 约束项」表单（DOM + `app.js` 的
  `loadAiTrainingPromptConfig` / `restoreAiTrainingPromptDefault` / 保存函数 +
  `/api/ai-training/prompt-config` 端点 + `AiPromptConfigService`）与 X-4 同批处置**——
  它们的存在理由就是 FREE_FORM 路径还活着，路径删了它们才该删（D-14）。
- **X-5**（03b）：监控页「命中规则」列对 RAG 回信显示为空，需要时再加读
  `mail_record_rag_fact` 的分支。
