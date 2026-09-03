# c5 Execution Report — 04 RAG 知识库管理页（替换「QA 知识库」子 Tab）

- Result: **READY_FOR_VERIFICATION**
- Plan: `docs/plans/2026-09-02/04-rag-knowledge-base-page.md`
- Plan identity: `commit:92b0519a18a3a46989f8733259af4649f7748a72`（A1 修订后；`git diff 92b0519 -- <plan>` 为空，已复核）
- Plan sha: `92b0519a18a3a46989f8733259af4649f7748a72`
- Master plan: `docs/plans/2026-09-02/00-execution-order.md`（identity `92b0519...`；G-1..G-9 遵守；D-8/D-13/D-16）
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-02-execution-order`
- Branch: `fast/2026-09-02-execution-order`
- Child base (product boundary): `ae755c417d2be4cecda52c5adf20a7f52227a072`（c4 代码头）；实施前 HEAD `f72dc18ee05246da82fcbc1b9bef4cd9d1940072`（c4 证据头）
- Implementation commit: `db89054f32a51f79f4cc86f5b21a9871a8dac729`（`feat(fast-p): implement c5`，12 个文件；docs/plans/fast/** 未纳入，git status 仅余 c5 证据目录 untracked）
- Task status: COMPLETE（全部门禁：新 JS 9/9、单项 Kotlin genuine 10/10、JS 全量 818/0、mvn test 3108/0/0/8、clean package 3108/0/0/8、node --check、git diff --check；Flyway IT 记录环境阻塞并给 scratch 替代验证；V114 在 scratch 补丁链验证）

## 变更文件（12 = 计划清单 9 + G-5 复核追加 3）

计划 `## 变更文件清单` 的 9 个授权文件：

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V114__create_rag_fact_audit.sql` | 新增（T0 审计表：无 FK、KEY(fact_code,id) + KEY(fingerprint_after)、表注释写明 I-21 闭环路径；注释风格对齐 V113） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagFactAdminService.kt` | 新增（list/update/toggleEnabled；republish 原子入口 + NamedParameterJdbcTemplate 审计直写） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagFactAdminController.kt` | 新增（GET "" / PUT /{factCode} / POST enable、disable；无 create/delete） |
| 4 | `src/main/resources/static/index.html` | 修改（823 按钮 data-tab=ragKb + 「RAG 知识库」；830-865 aiTabQa 面板整块换 S-2 骨架 aiTabRagKb；三处 ?v= → `20260902-rag-knowledge-base`） |
| 5 | `src/main/resources/static/app.js` | 修改（白名单链 qa→ragKb；state 8 字段；新增 loadRagKb/renderRagKbFilters/renderRagKbList/renderRagKbDetail/saveRagFact(+toggle 辅助)；loadAiTraining 换 loadRagKb()；bindEvents 4 组交互绑定；旧 QA 三函数保留不删） |
| 6 | `src/main/resources/static/styles.css` | 修改（S-1 三个 verbatim token 追加到 :root 末尾；S-2..S-5 规则块逐字追加到文件末尾 —— 与计划代码栅格逐字节一致，测试逐条断言） |
| 7 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改（缓存键 49-51 → 20260902-rag-knowledge-base） |
| 8 | `src/test/js/ragKnowledgeBasePage.test.js` | 新增（G-6/G-8/S-1..S-5 逐字 CSS/G-5/白名单/渲染 DOM-stub 9 用例） |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/rag/RagFactAdminServiceTest.kt` | 新增（I-20/I-21/I-22/I-23/G-1/G-4 共 10 用例，migrationIt 门控 + 外部库模式） |

G-5 复核追加（见 §偏离 1 —— 计划清单以 `20260902-monitoring-window` 时代为准，HEAD 实值 `20260902-undelivered` 被 4 个测试文件钉住；预 grep 已复核）：

| # | 文件 | 动作 |
|---|---|---|
| 10 | `src/test/js/checkRepliesRelocation.test.js` | 修改（CACHE_KEY → 20260902-rag-knowledge-base） |
| 11 | `src/test/js/manualReplySubjectPrefill.test.js` | 修改（同上） |
| 12 | `src/test/js/overlayAndDialogContrast.test.js` | 修改（同上） |

未触碰：`docs/plans/fast/**`、`qa_rule`/`qa_category`、`/api/qa/*`、迁移 V1..V113、c1-c4 已提交 rag/mail/llm 文件（RagKnowledgeBase 只读消费）、`TrustReplyWorkbenchService`/`AiReplyDraftService`、主 checkout（还原后复核 `git status --porcelain -- src/` 为空）。

## 关键实现（对照不变量）

- **I-20**：`update()`/`toggleEnabled()` 均 `@Transactional`，fact 写入包在 `ragKnowledgeBase.republish { repository.save(...) }` 内；republish REQUIRED 加入同一事务，同一事务内 UPDATE rag_kb_meta，提交后（afterCommit）才发布快照；返回新指纹。服务源码**零出现** `verifyAndPublish` 与 `QaRuleAuditService`（含注释，接受 grep 为空；注释改为叙述性表述）。「连续两次编辑」用例第二次同样成功（防只修好第一次）。无变更的部分更新为 no-op：不写库不写审计，返回当前指纹。
- **I-21**：审计行在 republish 返回后、本方法提交前写入 —— 与 fact 写入/meta 更新同事务，回滚时一起回滚（TransactionTemplate 强制回滚用例断言：fact 未改、audit 0 行、meta/快照原样、verify 放行）。按字段逐行：`answer` old/new 为全文（MEDIUMTEXT），其余为字符串（enabled 记 1/0，与 G-2 规范化一致）；`fingerprint_before` = republish 前快照指纹、`fingerprint_after` = republish 返回的新指纹；operator 入库。字段名为 DB 列名（answer/title/question_variants/coverage_keys/render_mode/risk_level/status/reply_policy/enabled）。
- **I-22**：DTO 显式带 `factCode/area/seq/legacyRuleId` 四个只读名（JSON 绑定后忽略，不报错），服务端永不读取；A-5 场景（body 带 KB-XXX-999/ZZZ/999/999 + title）库中四列不变、title 生效。
- **I-23**：控制器仅 GET/PUT/{code}/enable/disable；无 `@PostMapping("")`、无 `@DeleteMapping`（文件级断言 + 源码 grep）。
- **G-1/G-4**：list 响应 DTO 无自增 id（测试用 ObjectMapper 断言 JSON 无 `"id"`）；审计不进 QA 表/服务；legacy_rule_id 仅展示。
- **G-6**：三点同步 —— index.html 按钮 `data-tab="ragKb"`、面板 `id="aiTabRagKb"`、app.js 白名单链 `(tab === "ragKb" && panelId === "aiTabRagKb")`（测试断言三点 + 旧链消失）。
- **G-8**：`ragKbFilters/ragKbSearch/ragKbList/ragKbDetail/ragKbListCount/ragKbFingerprint` 全部断言真实存在于 index.html 源文本。
- **S-1..S-5**：styles.css diff 仅两个 hunk —— `:root` 末尾 3 个 token + 文件末尾 S-2..S-5 追加块（S-6 复核）；追加块与计划代码栅格**逐字节一致**（python 直接从计划文件抽取拼接，测试内嵌同源栅格逐条 `includes` 断言，含全部状态选择器）。
- **G-7 复核**：grep 全部 JS 测试 —— 无测试断言旧 QA 子 Tab 按钮/面板/渲染 id（唯一 `aiTabQa` 引用是 aiTrainingUnsupportedAnswers.test.js 沙箱面板桩，不受影响）；无需改动清单外测试。旧 QA 三函数保留不删（07 清理）。
- 页头：`ragKbFingerprint` 显示 `库指纹 <fp>`，`ragKbListCount` 显示 `匹配/总数 条`；A-1/A-2 流程可用（编辑 → 指纹变 → 撤销 → 恢复种子指纹）。

## 命令与结果（JDK 11 zulu-11；最终代码态新鲜执行）

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | G-5 预 grep（改 index.html 前对 HEAD 态复核）`git grep '20260902-undelivered' HEAD -- src/test/js src/main/resources/static/index.html` | 0 | HEAD 态：index.html 3 处键 + **4 个**测试文件钉住旧值（batchSendTaskConsoleVisualFix:49-51 + checkRepliesRelocation:11 / manualReplySubjectPrefill:13 / overlayAndDialogContrast:15 的 CACHE_KEY）—— 计划清单以 `20260902-monitoring-window` 时代为准，实值已两次漂移；G-5 终态复核（同命令取 styles.css 键）输出全部为 `20260902-rag-knowledge-base` 单值 |
| 2 | `mvn -o test-compile` | 0 | BUILD SUCCESS（含新服务/控制器/测试编译） |
| 3 | `node --test src/test/js/ragKnowledgeBasePage.test.js` | 0 | **tests 9, pass 9, fail 0** |
| 4 | `mvn test -Dtest=RagFactAdminServiceTest -DmigrationIt=true`（env `RAG_KB_TEST_DB_URL` 指向 scratch 补丁链，见 §偏离 3） | 0 | **Tests run: 10, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS —— I-20 连续双编辑、I-21 审计/回滚/多字段、I-22 只读忽略、I-23 无 create/delete、G-1 无 id、G-4 grep 全部在真实 MySQL 8.0.36（V1..V81+对齐+V82..V114）上通过 |
| 5 | `mvn test -Dtest=RagFactAdminServiceTest`（plain，无 migrationIt） | 0 | **Tests run: 1, Failures: 0, Errors: 0, Skipped: 1**，BUILD SUCCESS —— docker-free 类被跳过 |
| 6 | `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | 1 | **环境阻塞**（记录）：`client version 1.32 is too old. Minimum supported API version is 1.40`（docker-java 1.19.8 vs daemon 29.4.0/OrbStack；Tests run 1, Errors 1）—— c1/c4 同因同文；另 fresh 链经 scratch runner 等价复现：`Migration V82__split_trust_reply_atomic_facts.sql failed`（V82 baseline gate，fresh DB 上 V1..V81 成功后必失败） |
| 7 | `node --test src/test/js/*.test.js` | 0 | **tests 818, pass 818, fail 0** |
| 8 | `node --check src/main/resources/static/app.js` | 0 | SYNTAX_OK |
| 9 | `mvn test`（全量回归） | 0 | **Tests run: 3108, Failures: 0, Errors: 0, Skipped: 8**，BUILD SUCCESS（c4 基线 3107/0/0/7 → +1 gated 类 = RagFactAdminServiceTest；skip 8 = 7 docker/IT 门控 + 1 既有 @Disabled） |
| 10 | `mvn clean package` | 0 | **Tests run: 3108, Failures: 0, Errors: 0, Skipped: 8**，BUILD SUCCESS，WAR 构建 |
| 11 | `git diff --check` | 0 | 无输出 |
| 12 | 源码接受 grep：`verifyAndPublish`/`QaRuleAuditService` in RagFactAdminService.kt、`DeleteMapping`/`@PostMapping("")` in RagFactAdminController.kt | — | 全部无输出 |
| 13 | S-6 hunk 复核 | — | styles.css 恰 2 hunk（`:root` 尾 3 token 行 + EOF 追加 241 行）；index.html 3 hunk（缓存键 ×2 区 + 823/830 面板区，`.ai-tab` 仅按钮行改动） |

补充：scratch 补丁链全程 —— `c5probe` mysql:8.0.36 容器（端口 3307，已删除）：V1..V81 → 按 c1 同法对齐 qa_rule 行 17/34 到 V82 门禁字面量（gate17=1/gate34=1，实测无 FK 引用冲突）→ 测试上下文 Flyway 续跑 V82..V114（`flyway_schema_history version=114`）。命令 4 为 genuine 语义运行。

## 迁移验证（V114，scratch 补丁链）

- Flyway IT fresh 链**环境阻塞（基线复现）**：命令 6 同因同文（docker client API 1.32 vs 1.40，与 c1/c4 记录一致）；fresh 全链经 scratch runner（`fresh_probe` 空库）复现 V82 baseline gate 失败 —— 任何 fresh 链都无法到达 V114，IT 的 fresh 断言环境不可执行（c1/c4 先例）。
- **V114 替代验证（通过）**：scratch 补丁链 V1..V81+对齐+V82..V114 全链成功（version=114）。断言全绿：表列 `id,fact_code,field,old_value,new_value,fingerprint_before,fingerprint_after,operator,created_at`；old/new_value MEDIUMTEXT NULL；fingerprint_before/after VARCHAR(64) NOT NULL；`KEY idx_rag_fact_audit_code(fact_code,id)`、`KEY idx_rag_fact_audit_fp(fingerprint_after)`；**无外键**（information_schema fk_count=0，仅 PRIMARY）；InnoDB/utf8mb4；表注释与列注释记录 I-21/D-16 闭环路径。审计写行为由命令 4 的 10 用例在真库验证（含全文 old/new、指纹闭环、同事务回滚）。

## 偏离（登记；无计划修订）

1. **G-5 追加 3 个钉值测试文件（超出计划清单 9 文件）**：计划的「固定该字符串的测试文件目前只有 batchSendTaskConsoleVisualFix.test.js:49-51」以 `20260902-monitoring-window` 时代为准；HEAD 实值 `20260902-undelivered`，预 grep 复核发现另有 3 个测试文件以 `CACHE_KEY` 常量钉住该值（checkRepliesRelocation / manualReplySubjectPrefill / overlayAndDialogContrast）。三者与 batchSend 同为缓存键三联守卫，不同步会直接炸构建期 node 回归门禁（G-5 违例后果原文）。已把 4 处全部同步为 `20260902-rag-knowledge-base` 单值；文件 10-12 属 G-5 复核语义内，非静默改清单外测试（本报告与 commit 明示，请 verifier/controller 知悉）。未发现任何钉旧 QA 子 Tab UI/ids 的测试（G-7 复核为空）。
2. **RagFactAdminServiceTest 门控/外部库**（控制器先例，实现细节非计划修订）：与 RagKnowledgeBaseTest 同款 `@EnabledIfSystemProperty(migrationIt)` + `RAG_KB_TEST_DB_URL` 外部库模式；plain `mvn test`/`clean package` docker-free 全绿（命令 5/9/10）。
3. **V114 验证通道**：同 c1/c4 —— fresh 链被既有 V82 门禁挡住（环境阻塞，命令 6 基线复现），V114 与全部语义经 scratch 补丁链验证（命令 4 + §迁移验证）。
4. **服务 KDoc 措辞**：接受标准对 `RagFactAdminService.kt` 的 grep 要求「无 verifyAndPublish / QaRuleAuditService 字样」，故注释不写方法/类全名（叙述性表述保留 P0-3/G-4 语义）。
5. **styles.css :root 追加含 1 空行分隔**（3 token 行前空行，与 :root 内变量块间空行风格一致）；S-6 复核 hunk 结构不变。
6. **页面指纹展示位**：S-2 骨架未定义页头元素，按 A-1/A-2 可观测要求在「事实列表」panel-head 增加 `span#ragKbFingerprint`（新 id 非新 class，不违反禁止项），`ragKbListCount` 显示匹配/总数。

## 新鲜度

- Plan identity 复算: YES（92b0519 未变，plan diff 为空）
- Worktree identity 复算: YES（branch `fast/2026-09-02-execution-order`；实施前 HEAD f72dc18 → 实施后 db89054，ae755c4 为 ancestor）
- 实现提交不含 fast-p 证据: YES（commit db89054 恰 12 文件；`docs/plans/fast/**` 未纳入；git status 仅余 c5 证据目录 untracked）
- 必需命令最终代码态新鲜执行: YES（命令 1-13 全部在最终态执行/复核）
- 环境副作用清理: c5probe 容器与 fresh_probe 库已删除；/tmp 脚本已清；主 checkout 无残留（`git status --porcelain -- src/` 为空）
- 历史输出仅作基线: YES（V82 fresh 失败与 docker API 不匹配在 scratch/fresh 现跑复现）
