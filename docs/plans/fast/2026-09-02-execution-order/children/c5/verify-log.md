# c5 Light Verification — 04 RAG 知识库管理页（替换「QA 知识库」子 Tab）

## Light Verification: LIGHT_PASS_WITH_NOTES

- Child: c5 — `docs/plans/2026-09-02/04-rag-knowledge-base-page.md`
- Plan identity: `commit:92b0519a18a3a46989f8733259af4649f7748a72`（A1 修订后）— `git diff 92b0519a18a3a46989f8733259af4649f7748a72 -- docs/plans/2026-09-02/04-rag-knowledge-base-page.md` EMPTY（已复核，0 输出）。Master plan `00-execution-order.md` identity 同 commit（G-1..G-9 全文读取，G-5「执行前必须重新 grep 复核」为第 3 个钉值文件的权威授权）。
- Boundary: `ae755c417d2be4cecda52c5adf20a7f52227a072` (c4 code head) .. `db89054f32a51f79f4cc86f5b21a9871a8dac729`（`feat(fast-p): implement c5`；中间仅 docs-only `f72dc18` c4 evidence，非产品）。HEAD 复核 = db89054 ✓；branch = `fast/2026-09-02-execution-order` ✓。
- Verifier: VerifierC5
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-02-execution-order`
- 只读约束遵守：产品/测试/index/提交/分支/计划零写入；唯一写入 = 本报告。运行后 `git status --short` 仅余 `?? docs/plans/fast/**/children/c5/`（本报告 + implementer 的 brief/execution.md）。

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| 1. Authorized scope | PASS | `git show db89054 --name-only` = **恰好 12 文件**：计划 `## 变更文件清单` 的 9 个（V114 sql、RagFactAdminService.kt、RagFactAdminController.kt、index.html、app.js、styles.css、batchSendTaskConsoleVisualFix.test.js、ragKnowledgeBasePage.test.js、RagFactAdminServiceTest.kt）+ G-5 复核追加 3 个（checkRepliesRelocation.test.js、manualReplySubjectPrefill.test.js、overlayAndDialogContrast.test.js）。3 个追加文件的 diff **各只有单行**：`CACHE_KEY = "20260902-undelivered"` → `"20260902-rag-knowledge-base"`（checkRepliesRelocation:11 / manualReplySubjectPrefill:13 / overlayAndDialogContrast:15 区），无任何其他改动 —— 满足 master G-5 授权（child 清单自认过时；预 grep 复核发现 3 个额外钉值文件，同步为单值即授权语义）。无其他未列文件（无 docs/plans/fast、无 V1..V113、无 c1-c4 rag/mail 已提交文件、无 TrustReplyWorkbenchService/AiReplyDraftService）。parent = f72dc18（docs-only）✓。 |
| 2. Plan requirements & invariants | PASS | 直接代码/测试证据见下方（I-20..I-23、G-1/G-4/G-5/G-6/G-7/G-8/G-9、V114 T0 形状、S-1..S-6 逐字节、A-1/A-2 语义、T0-T6 机制）。 |
| 3. Required commands | PASS | 全部必需命令在最终提交态新鲜执行（JDK 11 zulu-11，worktree 根），见命令表。3108/0/0/8 与 implementer 声称完全一致；JS 818/818；plain RagFactAdminServiceTest 1/1 Skipped（docker-free）。FlywayMigrationIntegrationTest `-DmigrationIt=true` **环境阻塞现跑复现**（docker-java client API 1.32 < OrbStack daemon 29.4.0 min 1.40；Tests run 1, Errors 1, BUILD FAILURE）——记录，不判 gate 3 失败；V114 语义经 execution.md 的 scratch 补丁链 genuine 运行证据（10/10）验证（O-2）。 |
| 4. Downstream interfaces | PASS | `rag_fact_audit`（V114）与 c4 `mail_record_rag_fact` 指纹闭环形状一致（两表 `fingerprint* VARCHAR(64)`、两表均无 FK 到 rag_fact）；`/api/rag/facts` GET/PUT/{factCode}/POST enable/disable 端点就位；前端 `aiTabRagKb` + `loadRagKb()`（替换 QA loader）+ save/toggle 交互；缓存键单值 `20260902-rag-knowledge-base`（c6/c7 自行再 bump，未预占）。 |

#### Gate-1 补充（commit 内容 vs 边界 diff）
- 边界 `ae755c4..db89054` 的 16 文件 = db89054 的 12 产品文件 + f72dc18 的 4 docs（c4 evidence/ledger）——中间提交全部文档化；产品改动恰为 c5 commit 本体。
- 3 个额外文件逐行核对（上方 Evidence）；`git show --check db89054` 无空白警告。

#### Gate-2 直接证据（代码 + 已执行测试 + grep）
- **V114 (T0 形状逐项)**：SQL 文件独立复核 —— 列 `id BIGINT PK AUTO_INCREMENT / fact_code VARCHAR(32) NOT NULL / field VARCHAR(32) NOT NULL / old_value MEDIUMTEXT NULL / new_value MEDIUMTEXT NULL / fingerprint_before VARCHAR(64) NOT NULL / fingerprint_after VARCHAR(64) NOT NULL / operator VARCHAR(64) NULL / created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP`；`KEY idx_rag_fact_audit_code (fact_code, id)` + `KEY idx_rag_fact_audit_fp (fingerprint_after)`；**FK 计数 0**（grep `foreign key` = 0，与 V113 基线一致）；表注释 + 列注释写明 I-21/D-16 闭环还原路径（comment 记录）。
- **I-20（行为语义，非 grep-gaming）**：`RagFactAdminService.update()/toggleEnabled()` 均 `@Transactional`；`commitEdit()` 内唯一写路径 = `ragKnowledgeBase.republish { factRepository.save(updated) }`。复核 c1 的 `RagKnowledgeBase.republish()` 本体：REQUIRED 传播加入外层事务 → `writeInTx()` → 重读全表 → UPDATE `rag_kb_meta`（同事务）→ `afterCommit` 才 `publish(loaded)` 替换快照 → 返回新指纹；无旧指纹比对（不做启动门禁）。服务/控制器源码 **verifyAndPublish 与 QaRuleAuditService 出现次数 = 0**（含注释与 KDoc，叙述性规避确认；全 rag 包内仅 c1 既有文件 RagKnowledgeBase.kt/RagRetrievalRule.kt 引用）。acceptance grep：`grep verifyAndPublish RagFactAdminService.kt` exit 1、`grep QaRuleAuditService` exit 1 —— 均无输出。无变更 edit 为 no-op（不写库不写审计，返回当前指纹）——防误写。连续两次编辑用例（第二次同样成功）在真实 MySQL 通过（I-20 双编辑 + `verifyAndPublish()` 放行断言）。
- **I-21**：审计行在 `republish` 返回后、本方法提交前经 `NamedParameterJdbcTemplate` 直写 `rag_fact_audit` —— 同事务（回滚用例：强制回滚后 fact 原值/audit 0 行/meta 指纹不变/快照同实例）；`fingerprint_before` = republish 前 `ragKnowledgeBase.fingerprint()`（已提交快照指纹）、`fingerprint_after` = republish 返回新指纹；`answer` old/new 存全文（DB 测试断言逐字符相等）；按字段逐行（DB 测试断言多字段 6 行、field 为 DB 列名、enabled 记 1/0 与 G-2 一致）；**不复用 QaRuleAuditService**（源码 0 出现 + `qa_rule` 0 出现测试）。
- **I-22**：DTO 显式携带 `factCode/area/seq/legacyRuleId` 四只读字段名（JSON 绑定后服务端永不读取 → 忽略非报错）；DB 测试断言 A-5 场景（KB-XXX-999/ZZZ/999/999 + title）四列不变、title 生效、审计仅 title 一行。
- **I-23**：控制器方法面 = `@GetMapping`(class base `/api/rag/facts`) + `@PutMapping("/{factCode}")` + `@PostMapping("/{factCode}/enable")` + `@PostMapping("/{factCode}/disable")`；`DeleteMapping` 与 `@PostMapping("")`/`("/")` 出现 0（源码 grep + 文件级测试双断言）。
- **G-1/G-4**：list DTO 无自增 id（ObjectMapper JSON 无 `"id"` 测试在真库通过）；legacy_rule_id 仅展示。
- **G-6 三点同步**：index.html 按钮 `data-tab="ragKb"`「RAG 知识库」（就地改，`data-tab="qa"`/`id="aiTabQa"` 从 html 消失）；面板 `id="aiTabRagKb"`（S-2 骨架：ragKb-layout 三栏、ragKbFilters/ragKbSearch/ragKbList/ragKbDetail，panel-head 含 `ragKbListCount` + 额外 `ragKbFingerprint` 新 id span —— execution.md §偏离 6 登记，id 非 class 不违禁）；app.js `switchAiTrainingTab` 白名单链 `(tab === "ragKb" && panelId === "aiTabRagKb")` 替换 qa 项、`(tab === "qa" && panelId === "aiTabQa")` 消失（新 JS 测试 9 用例第 1 条断言三点 + 旧链消失）。CSS 规则块未动（S-6）。
- **G-5**：index.html 三处 `?v=` 全部 = `20260902-rag-knowledge-base`（styles.css:11 / trust-reply-workbench.js:2093 / app.js:2094，grep 确认无其他 `?v=` 值）；钉值测试文件 grep = 4 个（batchSendTaskConsoleVisualFix + 3 额外）+ 新 ragKnowledgeBasePage.test.js 断言同值（新文件属授权清单）；`20260902-undelivered`/`20260902-monitoring-window` 残留 = 0。
- **G-7**：grep 全部 JS 测试 —— 旧 QA DOM 断言仅剩 aiTrainingUnsupportedAnswers.test.js:53 的沙箱面板桩（非 index.html 源断言，不受影响）与 ragKnowledgeBasePage.test.js 的否定断言（必须消失）；无测试钉旧 QA 按钮/面板/渲染 id。
- **G-8**：ragKnowledgeBasePage.test.js 断言 `ragKbFilters/ragKbSearch/ragKbList/ragKbDetail/ragKbListCount/ragKbFingerprint` 六 id 真实出现在 index.html 源文本（真实 HTML，非 stub）。
- **G-9**：V114 单文件、版本号符合部署序（V112→V113→V114→V115）；未改 V1..V113。
- **S-1..S-5 逐字节**：Python 从计划文件抽取 5 个 CSS 代码栅（S-1..S-5）逐一 `in css` 检查 —— **全部 verbatim=True**（styles.css 内含完整契约块）。styles.css diff 恰 2 hunk：`:root` 末尾 3 个 verbatim token（`--verbatim: #7c3aed` 等，值/数 grep：`--verbatim:` 命中 #7c3aed、`--verbatim` 计数 11 ≥ 3）+ EOF 追加 241 行（S-2..S-5）。index.html diff 恰 3 hunk：缓存键 ×2 区 + 823/830 按钮/面板区（.ai-tab 改动仅按钮行 data-tab 与文案）。
- **T3/T6/A-1/A-2 语义**：loadAiTraining 的 Promise.all 内 `loadAiTrainingQa()` → `loadRagKb()`（唯一 diff 触达点；其余 `loadAiTrainingQa` 调用点 3427/3434/12451-12525 为**基态既有**、c5 未改，其 DOM 目标随面板删除不可达 —— 07 清理域，见 O-1）；旧 QA 三函数保留（3325/3340/3438 仍定义，新测试断言保留）；新函数 loadRagKb/renderRagKbFilters/renderRagKbList/renderRagKbDetail/saveRagFact + toggle 辅助 + bindEvents 4 组绑定就位。页头 `ragKbFingerprint` 显示服务端返回的库指纹（A-1/A-2「指纹显示与变化」由服务端数据驱动，非 JS 硬编码 —— 与 plan 一致，seed 值 e62421a42c432cf3 由 Kotlin 测试在真库断言）。
- **A-5/I-22 的 A 面**、**I-23 grep 验收**、**P0-3 钉死点** 均有 DB/源码级测试覆盖（10 用例清单见 execution.md §命令 4）。

#### Gate-3 命令表（最终提交态，全部由 verifier 新鲜执行）

| # | 命令 | Exit | 结果 |
|---|---|---|---|
| 1 | `node --test src/test/js/ragKnowledgeBasePage.test.js` | 0 | **tests 9, pass 9, fail 0** |
| 2 | `mvn test -Dtest=RagFactAdminServiceTest`（plain，无 migrationIt） | 0 | **Tests run: 1, Failures: 0, Errors: 0, Skipped: 1** — BUILD SUCCESS（docker-free，类被门控跳过） |
| 2b | `mvn test -Dtest=RagFactAdminServiceTest -DmigrationIt=true` + `RAG_KB_TEST_DB_URL`（scratch 补丁链） | 0 | **genuine 运行证据核验**（见 O-2；execution.md §命令 4/§迁移验证：MySQL 8.0.36 V1..V81+对齐+V82..V114，flyway_schema_history version=114，Tests run: 10, Failures: 0, Errors: 0, Skipped: 0；表列/无 FK/KEY/审计写行为断言全绿）。未由 verifier 重跑（链按设计已清理）——O-2 记录 |
| 3 | `node --test src/test/js/*.test.js` | 0 | **tests 818, pass 818, fail 0**（与声称完全一致） |
| 4 | `node --check src/main/resources/static/app.js` | 0 | SYNTAX_OK（无输出） |
| 5 | `mvn test`（全量回归） | 0 | **Tests run: 3108, Failures: 0, Errors: 0, Skipped: 8** — BUILD SUCCESS（c4 基线 3107/0/0/7 → +1 = RagFactAdminServiceTest gated 类；skip 8 = 7 docker/IT 门控 + 1 既有 @Disabled） |
| 6 | `mvn clean package` | 0 | **Tests run: 3108, Failures: 0, Errors: 0, Skipped: 8** — BUILD SUCCESS（WAR 构建） |
| 7 | `git diff --check` + `git show --check db89054` | 0 | 均无输出 |
| 8 | 缓存键 grep | — | index.html 3 键全 = `20260902-rag-knowledge-base`；测试钉值 = 4 文件；旧值残留 0 |
| 9 | `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | 1 | **环境阻塞（记录，不判失败）**：`client version 1.32 is too old. Minimum supported API version is 1.40`（docker-java 1.19.8 vs OrbStack daemon 29.4.0）；Tests run: 1, Errors: 1 — 与 implementer/c1/c4 记录同因同文 |

#### Gate-4 细节
- `rag_fact_audit.fingerprint_before/after VARCHAR(64) NOT NULL` ↔ V113 `mail_record_rag_fact.corpus_fingerprint VARCHAR(64) NOT NULL`：类型/长度/语义闭环（A-3b 还原路径成立）；两表均无 FK（审计与存证保留历史、不随 rag_fact 失效）。
- 端点契约：`GET /api/rag/facts` → `RagFactAdminListResult{facts[], fingerprint, factCount}`（无自增 id，G-1）；`PUT /api/rag/facts/{factCode}` → `RagFactSaveResult{fingerprint}`；`POST /{factCode}/enable|disable` 同形。前端载荷键（title/answer/questionVariants/coverageKeys/renderMode/riskLevel/status/replyPolicy/operator）与 DTO 逐键匹配；save 后 re-load + 重新选中（A-2 刷新语义）。
- 前端 `aiTabRagKb` + `loadRagKb()`（GET facts → state → render ×3 + 指纹头）；筛选/搜索/列表选择/保存/启停事件经 bindEvents 4 组委托绑定。
- 缓存键单值 = c6/c7 再 bump 的现役值（无预占）。

### AUTO_FIX
N/A — 无 proven four-gate 违例：scope 精确（12 = 9 + G-5 授权 3，追加文件仅单行键值同步）；plan identity 绑定验证（diff 为空）；全部验收判据机器核验；无未授权修改；无下游形状漂移。

### RECORD_ONLY
- O-1（一行，非缺陷）：基态既有 app.js 代码（3427/3434 包装函数与 bindEvents 12451-12525 的 `loadAiTrainingQa()` 调用、`#reloadAiTrainingQaBtn`/旧 QA 弹窗按钮 wiring）仍引用本次随面板删除的 QA DOM —— c5 diff 未触碰（逐行复核：c5 对 loadAiTrainingQa 的唯二改动 = loadAiTraining 调用移除 + 注释行）；运行期不可达（按钮已不在 HTML，`?.` 短路），属 07（G-7）统一清理域，非本 child 违例。
- O-2（透明性记录）：RagFactAdminServiceTest 的 genuine 外部库运行（`-DmigrationIt=true` + `RAG_KB_TEST_DB_URL`）未由 verifier 重跑 —— scratch 补丁链（V1..V81+对齐+V82..V114）按实现期环境清理约定已删除、其 V1..V81 预迁移驱动不在仓内文档化；execution.md §命令 4/§迁移验证 提供了完整 genuine 证据（真 MySQL 8.0.36、10/10、含回滚/全文/闭环断言、version=114），测试类外部库机制在仓内复核成立（`RAG_KB_TEST_DB_URL` env → DynamicPropertySource）。与 c1/c4 验证先例一致（V113/V112 同理，记录为已验证证据而非重跑）。plain 门禁已重跑确认 docker-free（命令 2/5/6）。
- O-3（一行）：styles.css `:root` 追加 3 变量前含 1 空行分隔（execution.md §偏离 5）；S-1..S-5 逐字节契约不受影响（Python 全栅 verbatim 复核通过），hunk 结构 = 计划 S-6 声明（:root 末尾 + EOF 追加）。
- O-4（一行）：S-2 骨架之外新增 `span#ragKbFingerprint`（execution.md §偏离 6，A-1/A-2 页头指纹可观测所需）；新 id 非新 class，不违反样式契约禁止项；ragKnowledgeBasePage.test.js 将其纳入 G-8 存在性断言。

### Required Action
- COMPLETE_CHILD
