# 00 执行顺序（权威）· 整封式 RAG 替换可信工作台

> 本文件是本轮 7 份计划的**顺序权威**。任何与子计划冲突之处以本文件为准。
> 日期：2026-09-02

## 本轮要做的事

用 `scripts/spike_deepseek_reply.py`（1094 行，45 条事实）的整封式 RAG 流程，
整体替换现有可信工作台的生成逻辑。信任单位从「request × fact 的 claim」换成
「fact chunk + render_mode」：高危事实模型无权书写，只放 `{{FACT:KB-XXX}}` 占位符，
由代码逐字替换；低危事实才交给模型组织语言。

## 子计划与依赖顺序

| 序号 | 文件 | 内容 | 依赖 |
|---|---|---|---|
| 01 | `01-rag-knowledge-base-schema.md` | **V112** 五张 `rag_*` 表 + 45 条种子 + 领域/快照/指纹 | — |
| 02 | `02-rag-deterministic-retrieval.md` | 确定性层：归一化 / 短语组 / 覆盖键 / 强制 / 剔除 / 预筛 + 平价测试 | 01 |
| 03 | `03-rag-letter-composer.md` | LLM 客户端补 `max_tokens` + 两次调用 + 令牌逐字替换 + unaddressed 校验 + 新端点 | 02 |
| 03b | `03b-rag-send-bridge.md` | **RAG 草稿采用 → 人工发送桥接**（**V113** 存证表 + 第三条发送路径 + safety 链绕过） | 03 |
| 04 | `04-rag-knowledge-base-page.md` | RAG 知识库管理页（替换「QA 知识库」子 Tab）+ **V114** 审计表 | 01 |
| 05 | `05-workbench-frontend-replace.md` | 工作台前端替换为「正文 / 用到哪些事实 / 未识别的提问」 | **03b** |
| 06 | `06-prompt-console.md` | 「AI 提示词与约束」页改为可编辑约束清单 + **V115** | 03 |
| 07 | `07-legacy-entry-retire.md` | 旧 QA 页下线、`qa_rule` 转只读、旧工作台端点摘除 | 04, 05, 06 |

- 01 → 02 → 03 → 03b 是**串行**的，不可并行：02 的平价测试依赖 01 的种子数据，
  03 依赖 02 的预筛产物，03b 依赖 03 的 compose 返回结构。
- 04 只依赖 01，可与 02/03/03b 并行。
- **05 依赖 03b，不是 03**。没有 03b 的发送桥接，新工作台生成的草稿发不出去
  （详见 D-11）。**03b 未落地前，05 与 07 不得开工。**
- 06 依赖 03。
- 07 必须最后。
- **带迁移的四份（01 / 03b / 04 / 06）必须按 `V112 → V113 → V114 → V115` 的顺序部署**，
  见 G-9。它们可以并行**开发**，但不可乱序**上线**。

## 已定决策（不再讨论，子计划直接引用）

| 编号 | 决策 | 决定人/时间 |
|---|---|---|
| D-1 | **安全闸门全部删除**：`AiReplyActionPolicy`、`AiReplyHighRiskClaimValidator`、非英文拒绝、动作白名单等一律不迁移到新链路。理由：不存在自动发送，本质是人工发送，人是唯一的门。 | 用户 2026-09-02 |
| D-2 | **逻辑与脚本逐字一致**。任何偏离必须在子计划中显式登记。 | 用户 2026-09-02 |
| D-3 | **唯一刻意偏离**：新增 `COMPENSATION` 强制事实规则（`sort_order 15`），使「问报酬」必出 `KB-FUND-033` 原文。实测脚本在「只说 compensation、未说 more details / government funding」时会漏掉 033。 | 用户 2026-09-02 |
| D-4 | **旧库 4 条未覆盖事实刻意舍弃**：`id 14 单一申报承诺`、`id 43 合作企业类型与研发需求`、`id 45 顾问交付成果`、`id 46 项目研究保密安排`。以脚本 45 条为准，直接覆盖，不补 `confidentiality.research` 事实。 | 用户 2026-09-02 |
| D-5 | **回复框架与落款保留现状**（尊语/开场白/致谢语/结束语 snippet + 落款随发件账号）。因此生成提示词第 12 条必须改为「不要写称呼与署名」。 | 用户 2026-09-02 |
| D-6 | **未识别的提问靠约束实现**：生成调用 JSON 增加 `unaddressed` 字段（第 22 条约束），服务端校验 quote 为来信逐字子串，非子串即丢弃。不新增第三次 LLM 调用。 | 用户 2026-09-02 |
| D-7 | **ProcessContext 接 `expert_material_status`**：`PROVIDED → RECEIVED`、缺行(PENDING) → `MISSING`、`DECLINED → UNKNOWN`；`expert_reply_count` = 该联系人 `mail_record` 中 `direction='INBOUND'` 的条数。 | 用户 2026-09-02 |
| D-8 | **RAG 知识库页替换 AI 回复训练下的「QA 知识库」子 Tab**，不新增侧栏 Tab。 | 用户 2026-09-02 |
| D-9 | **`qa_rule` 表数据一行不删**。被 `mail_record_qa_rule`（已发信件存证）、`inbound_mail_tag.qa_rule_id`、`MailComposeTemplateService` 的 `QA_RULE` 块、`MailMonitoringService` 四处钉住。只停写、不删数据。 | 本轮结论 |
| D-10 | **旧后端死代码的物理删除不在本轮**。07 只摘除入口；`TrustReplyWorkbenchService` / `AiReplyDraftService` grounded 部分的删除留待 01-06 落地后、引用集确定时另开计划。 | 本轮结论 |
| D-11 | **RAG 证据不冒充 `qaRuleIds`，另开第三条发送路径**（03b）。实测：`UnmatchedInboundMailController.kt:978/1012/1027` 的 `qaRuleIds` 是 `List<Long>`，且 `PendingMailOperationService.kt:191` 要求它与 `verifyAssembly` 的 `canonicalFactIds` **逐元素相等**。字符串 `fact_code` 连反序列化都过不去。存证落到新表 `mail_record_rag_fact`，不回填 `mail_record_qa_rule`（45 条里只有 33 条有 `legacy_rule_id`）。 | 2026-09-02 计划评审 |
| D-12 | **LLM 客户端的 `max_tokens` 由 03 的 T0 补齐**，做法是给 `LlmDraftClient` 新增一个带默认实现的四参重载，只让 `HttpLlmDraftClient` 覆写。实测 `chatWithModelObserved` 有 22 处 override（几乎全是测试桩），给它加参数会批量编译失败。 | 2026-09-02 计划评审 |
| D-13 | **指纹的校验与重发布拆成两个入口**：`verifyAndPublish()` 只在启动时用、只读不写；`republish { }` 供运营编辑用、事务内重算并更新 meta、提交后发布快照。合并成一个会让第一次合法编辑被自己的门禁拦下。 | 2026-09-02 计划评审 |
| D-15 | **RAG 发送必须绕过整条 QA safety/selection 链**（03b I-47）。实测 `PendingMailOperationService.kt:845` 的 `QA_FACTS_ALL_INVALID` 分支与 `:860-864` 的 `qaFactSelectionService.select()` 都会被 RAG 命中。做法：`carriesQa` 传 false + `collectSafetyFindings` 新增 `ragSend` 守卫短路 selection 段；纯文本检查保留，以免二次确认失去触发源。 | 2026-09-02 二轮评审 |
| D-16 | **事实改动审计落 `rag_fact_audit`（04 的 V114）**，含 `fingerprint_before/after` 两列，与 03b 的 `mail_record_rag_fact.corpus_fingerprint` 闭环，才能还原「当时发的是哪一版原文」。不复用 `QaRuleAuditService`（G-4）。 | 2026-09-02 二轮评审 |
| D-17 | **真实发送入口是 `sendManualRichReply()`**（`PendingMailOperationService.kt:135`），assembly 形参名为 `trustReplyAssembly`。`sendComposedReply` 这个函数在本仓**不存在**。 | 2026-09-02 二轮评审 |
| D-14 | **旧「自由回复系统提示词 + 约束项」表单保留，不在本轮删除**。它配置的是 `AiReplyDraftService` 的 FREE_FORM 兜底路径，而该路径在 D-10 之前仍在跑（`UnmatchedInboundMailController.kt:366,378`、`AiTrainingController.kt:222` 仍调用）。删表单会让一条仍在运行的路径失去配置入口。随 X-4 一起处置。 | 2026-09-02 计划评审 |

## 跨计划全局不变量

以下不变量跨多份子计划生效，子计划按编号引用，不重复定义。

### G-1: fact_code 是唯一业务标识
- Rule: `rag_fact.fact_code`（形如 `KB-FUND-033`）是全链路唯一业务主键。自增 `id` 只作外键与分页，
  **绝不进入任何提示词、任何前端响应、任何审计记录**。
- Applies to: 01 的领域与快照、02 的强制/剔除规则、03 的两次提示词与 `unaddressed`、04 的管理页、05 的工作台。
- Violation consequence: 提示词里出现自增 id → 换库或重排后强制规则静默失效。
- 来源: original

### G-2: 语料指纹是启动门禁
- Rule: 语料指纹 = 45 行 `rag_fact` 按 `fact_code` 升序；每行取 V112 建表列序的数据列以 `|` 连接
  （`legacy_rule_id` 为 NULL 记空串，`enabled` 记 1/0，`seq` 记整数，字符串不加引号）；行间以 `\n`
  连接；整体 SHA-256 取前 16 位。当前值 `e62421a42c432cf3`（A1 修订：原 `2b29a2152f2671df` 无法按
  任何文档化序列化从当前语料复现；Kotlin `RagKnowledgeBase` 与 Python `export_rag_kb_sql.py` 双实现
  等价，由测试交叉验证。见 ledger ## Amendments A1）。
  迁移写入常量，应用启动时重算比对，不一致直接启动失败。
- Applies to: 01 的迁移与快照、03 的检索缓存键、04 的保存后刷新。
- Violation consequence: 库里的事实与脚本漂移而无人察觉，逐字出信的正文与预期不符。
- 来源: original

### G-3: answer 是对外正文的唯一来源；title 只到检索为止
- Rule: 分两层，**不可合并成一句**：
  - **对外正文**：只有 `rag_fact.answer` 可以成为发出去的文字。`title`（中文名）永远不进正文。
  - **生成调用的提示词**：不得出现 `title`。`VERBATIM` 条目连 `answer` 都不给，只给 render_token（03 I-13）。
  - **检索调用的提示词**：**允许出现 `title`**。脚本 `retrieval_record()` 明确带 `title`，
    且 `retrieval_text` 的拼接首段就是 `title`（01 I-5）。禁掉它会与 D-2 的逐字平价直接冲突。
- Applies to: 03 的两次提示词构建、04 的管理页、05 的工作台事实列表。
- Violation consequence: 写成「绝不进入任何提示词」会让 03 的检索调用无法实现，
  或被迫改掉 `retrieval_text` 的拼接顺序从而与脚本分叉。
  反过来，若放宽到生成侧，中文内部名会混进英文对外信件。
- 来源: K-answerbody-source-exclusive（2026-09-02 计划评审按检索/生成两层定界）

### G-4: rag_* 与 qa_rule 零运行时耦合
- Rule: 新链路的任何代码路径不得读写 `qa_rule` / `qa_category`。`rag_fact.legacy_rule_id` 只读，
  只用于人工对账，任何运行时判断（匹配、排序、门禁、审计）不得读它。
- Applies to: 01-07 全部。
- Violation consequence: 两套语义搅在一起，旧库停写后新链路静默降级。
- 来源: original

### G-5: 前端缓存键三联必须同值同时 bump
- Rule: `index.html` 中 `styles.css?v=`、`trust-reply-workbench.js?v=`、`app.js?v=` 三个键必须同值同时 bump。
  当前值 `20260902-monitoring-window`。固定该字符串的测试文件目前只有
  `src/test/js/batchSendTaskConsoleVisualFix.test.js:49-51`。
- Applies to: 04、05、06、07（任何触及静态资源的计划）。
- Violation consequence: 只 bump 部分键 → 构建期 node 测试失败，WAR 构建中止（2026-08-13 实测踩坑）。
- 来源: K-frontend-cache-key-triad
- **执行前必须重新 grep 复核**，不得照抄本条的文件清单：
  `grep -rn "v=$(grep -o 'styles.css?v=[^"]*' src/main/resources/static/index.html | cut -d= -f3)" src/test/js/`

### G-6: 子 Tab 三点同步
- Rule: AI 训练视图的子 Tab 增删必须三处同步：① `index.html` 的 `.ai-tab[data-tab]` 按钮（823-827 行区）
  ② `index.html` 的 `.ai-tab-content` 面板（`id=aiTab<Name>`）③ `app.js:3298-3303` `switchAiTrainingTab()`
  的显式 `||` 白名单映射链。漏 ③ 的症状是按钮高亮但面板永不显示。
- Applies to: 04、06、07。
- Violation consequence: 按钮点了没反应，且无报错。
- 来源: K-ai-subtab-whitelist-mapping

### G-7: 删 UI 必须同步退役契约测试
- Rule: 删除 DOM / 端点 / 表格列时，必须 grep 并同步删除或改写直接断言它们的测试文件，
  并把这些测试文件列入变更文件清单。
- Applies to: 04、05、06、07。
- Violation consequence: 全量测试持续失败并阻塞发布。
- 来源: K-ui-removal-retires-obsolete-contract-tests

### G-8: DOM stub 测试掩盖悬空引用
- Rule: 本仓前端测试用 `extractFn` + DOM stub，`document.getElementById` 永远返回 stub 元素，
  因此**真实 `index.html` 里 DOM 已删除时测试仍全绿**，函数在生产中因 `if (!el) return;` 静默短路。
  新增「按 id 取元素再写入」的渲染函数时，测试中必须额外断言该 id 确实出现在 `index.html` 源文本里。
- Applies to: 04、05、06。
- Violation consequence: 页面某块永远不渲染，测试却全绿。
- 来源: K-dom-stub-tests-hide-dangling-refs

### G-9: 迁移版本按部署顺序线性分配，禁止乱序上线
- Rule: 本轮四份计划各带一个 Flyway 迁移，版本号按**部署顺序**分配，不按计划编号：
  `01 = V112` → `03b = V113` → `04 = V114` → `06 = V115`。
  这四份必须严格按此顺序上线；任意两份的部署顺序颠倒即视为发布事故。
- Applies to: 01、03b、04、06 的迁移文件命名与发布流程。
- Violation consequence: `application.yml:8-13` 的 flyway 块**没有配置 `out-of-order`**，
  Spring Boot 默认为 `false`。先应用 V114 再遇到 V113，启动直接失败
  （`Detected resolved migration not applied to database`），且回滚需要人工清
  `flyway_schema_history`。
- 附带约束: 谁先合并谁先占号。若实际合并顺序与上表不同，**必须重命名迁移文件**使版本号
  与实际部署顺序一致，并同步改对应计划的变更文件清单与验证命令；
  **不得**改用 `spring.flyway.out-of-order=true` 绕过——那会让整个仓库的迁移顺序失去约束。
- 来源: original（2026-09-02 第二轮计划评审 P0-1）

## 验证命令（全轮通用）

> 本项目必须用 JDK 11（zulu-11）。裸 `mvn` 会构建失败。
> 前端 JS 用例由 `exec-maven-plugin` 绑在 `mvn test` 的 test 阶段（`pom.xml:186-232`），
> `verify.sh` 只跑单个文件，**不能**当前端回归门禁。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 单个 Kotlin 测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=<ClassName>

# 单个 Kotlin 测试方法
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=<ClassName>#<methodName>

# Flyway 迁移集成测试（需本地 Docker，默认跳过）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 前端 JS 单文件 / 全量 / 语法检查
node --test src/test/js/<x>.test.js
node --test src/test/js/*.test.js
node --check src/main/resources/static/<x>.js

# 空白/换行卫生
git diff --check
```

通过判据：`mvn test` 退出码 0 且输出 `Tests run: N, Failures: 0, Errors: 0`；
`node --test` 退出码 0 且 `# fail 0`；`git diff --check` 无输出。
来源：`CLAUDE.md:10-27` 的 Commands 章节 + `CLAUDE.md:148/150` 项目元信息 + `CLAUDE.md:66` 团队沉淀知识。

## 参照设计稿

- `docs/mockups/rag-knowledge-base.html` — 04 的界面基准
- `docs/mockups/trust-workbench-rag.html` — 05 的界面基准
- `docs/mockups/ai-prompt-console.html` — 06 的界面基准

设计稿是**界面基准**，不是样式契约。逐字 CSS 以各子计划的 `## 样式契约` 为准。
