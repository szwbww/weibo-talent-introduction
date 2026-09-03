# c7 Execution Report — 06 「AI 提示词与约束」页改为可编辑约束清单

- Result: **READY_FOR_VERIFICATION**
- Plan: `docs/plans/2026-09-02/06-prompt-console.md`
- Plan identity: `commit:46cc5c46395814b1ef03e52ab8b8bfb5197f372c`（`git diff 46cc5c46 -- <plan>` 为空，已复核）
- Master plan: `docs/plans/2026-09-02/00-execution-order.md`（identity `92b0519a18a3a46989f8733259af4649f7748a72`，diff 为空；G-1..G-9 遵守）
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-02-execution-order`
- Branch: `fast/2026-09-02-execution-order`
- Child base (product boundary): `60efcbaaa14e919ff8b4cfa9539cca41fd6a6d62`（c6 代码头）；实施前 HEAD `e7ddb20`（c6 证据头）
- Implementation commit: `e76b5a9f49c866afffbccdffcae78443cb16cab3`（`feat(fast-p): implement c7`，16 个文件；docs/plans/fast/** 未纳入，`git status` 仅余 c7 证据目录 untracked）
- Task status: COMPLETE（全部门禁：新 JS 7/7、JS 全量 648/0、node --check、RagPromptConfigServiceTest 6/6 docker-free、RagLetterComposerTest 14/0/0/1 回归、mvn test 3114/0/0/8、clean package 3114/0/0/8、I-34 grep 空、G-5 残留 grep 空、git diff --check 无输出；Flyway IT 记录环境阻塞并给 scratch 替代验证；V115 在 scratch 补丁链 V1..V81+对齐+V82..V115 验证通过）

## 变更文件（16 = 计划清单 10 + G-5 复核追加 6）

计划 `## 变更文件清单` 的 10 个授权文件：

| # | 文件 | 动作 | 状态 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V115__create_rag_prompt_config.sql` | 新增 | **完成**：单行表 id=1（CHECK 强制）+ 两列 TEXT NULL + updated_at ON UPDATE + updated_by；表/列注释写明 I-30（NULL 回落常量、恢复默认置 NULL）；仅 V115 无额外审计表 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagPromptConfigService.kt` | 新增 | **完成**：effective()/save()/resetToDefault() + defaultEffective()（builder 降级路径共用）；NamedParameterJdbcTemplate 直读直写（先例 TrustReplyWorkbenchStateStore/RagFactAdminService）；审计 NamedParameterJdbcTemplate 直写 `operator_action_log` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagPromptConfigController.kt` | 新增 | **完成**：`GET ""` / `PUT ""` / `POST "/reset"`，映射与 RagFactAdminController 同款 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagPromptBuilder.kt` | 修改（取值来源 T3） | **完成**：注入 `RagPromptConfigService?`（默认 null 保持 03 既有 `RagPromptBuilder(objectMapper)` 直构兼容——RagLetterComposerTest 未授权未改动且全绿）；四个系统提示词入口全部改从 `effective()` / `defaultEffective()` 取值；`renderDerivedRules` 及 tokenList/normalize 私有辅助移入服务端（本文件 `RagPromptConstraints\.` grep 恒空，I-34） |
| 5 | `src/main/resources/static/index.html` | 修改 | **完成**：`#aiTabPrompts` 内既有 `<section>` 前插入两张 `.panel.ai-training-panel.rag-prompt-card`（id `ragPromptRetrieval`/`ragPromptGeneration`，含 count/body/add/foot）+ `.rag-prompt-savebar`（id `ragPromptSaveBar`，含 status/save/reset）；旧表单 panel-head 标题改「自由回复提示词（旧链路 · 兜底路径）」+ 追加一行 `.muted`；三处 `?v=` → `20260902-rag-prompt-console`（G-5） |
| 6 | `src/main/resources/static/app.js` | 修改 | **完成**：state 5 字段；loadRagPromptConfig/renderRagPromptRules/markRagPromptDirty/saveRagPromptConfig/resetRagPromptConfig 五个函数 + 渲染/合并/行操作辅助（ragPromptRows/ragPromptRowList/ragPromptRowHtml/ragPromptDirtyCount/ragPromptUndoRow/ragPromptDeleteRow/ragPromptAddRow/ragPromptOperator）；`loadAiTraining()` Promise.all 追加 `loadRagPromptConfig()`；bindEvents 追加清单交互（input/focusout/click 委托 + add/save/reset） |
| 7 | `src/main/resources/static/styles.css` | 修改 | **完成**：EOF 追加 S-1..S-4 逐字（python 直接从计划 css 栅格抽取拼接，与契约字节一致）；`.rag-badge` 基类零重定义（全文件 `.rag-badge {` 恰 1 次） |
| 8 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改（缓存键 49-51） | **完成** → `20260902-rag-prompt-console` |
| 9 | `src/test/js/ragPromptConsole.test.js` | 新增 | **完成**：7 用例（G-8 三 id + 渲染用全部 id、S-5/D-14 旧表单 textarea 各 1 命中且结构原样、T4 Promise.all 追加、I-31 22 行渲染 18/19/21 readonly 无 contenteditable、dirty 亮起/撤销复原、I-32 删除后重编号、S-1..S-4 与计划栅格逐字一致、S-4 禁 var(--panel-bg)） |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/rag/RagPromptConfigServiceTest.kt` | 新增 | **完成**：6 用例全 Mockito（jdbc + RagKnowledgeBase 快照），零 DB/docker，普通 mvn test 即跑（I-30×2 / I-31×2 / I-33×2） |

G-5 复核追加（brief 授权：预 grep 实测 7 个钉值文件，超出计划 T6 只列的 batchSend；master-G-5 权威，单字符串同步，与 c5 deviation #1 / c6 控制器裁决 #2 同构）：

| # | 文件 | 动作 |
|---|---|---|
| 11 | `src/test/js/checkRepliesRelocation.test.js` | 修改（`CACHE_KEY` :11 → 新值） |
| 12 | `src/test/js/manualReplySubjectPrefill.test.js` | 修改（`CACHE_KEY` :13 → 新值） |
| 13 | `src/test/js/overlayAndDialogContrast.test.js` | 修改（`CACHE_KEY` :22 → 新值） |
| 14 | `src/test/js/ragKnowledgeBasePage.test.js` | 修改（G-5 用例 332-338 → 新值） |
| 15 | `src/test/js/ragWorkbenchRender.test.js` | 修改（`CACHE_KEY` :20 → 新值；390 动态断言） |
| 16 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 修改（`CACHE_KEY` :22 + 注释/用例标题 5/175 → 新值） |

未触碰：`docs/plans/fast/**`、`qa_rule`/`qa_category`、`/api/ai-training/prompt-config` 行为、`AiPromptConfigService`/`AiTrainingController`、迁移 V1..V114、c1-c6 已提交文件（除 #4 RagPromptBuilder.kt）、两个旧 textarea（grep 各 1 命中）、app.js 白名单链、主 checkout（`git status --porcelain -- src/` 空）。

## 关键实现（对照不变量）

- **I-30**：`effective()` 读单行（queryForList，列 NULL 即回落）；检索段 NULL → `RETRIEVAL_RULES` 逐字、生成段 NULL → `GENERATION_RULES` 去掉派生三槽后的 19 条 + 派生槽回落常量占位文本 → 全 22 条与常量**逐字相同**、`isCustom=false`（测试断言整表相等）；`resetToDefault()` = `SET retrieval_constraints = NULL, generation_constraints = NULL`（SQL 字面量，绝不写 JSON 快照）。
- **I-31**：派生三条（0-based 17/18/20）每次 `effective()` 从 `RagKnowledgeBase.snapshot().mandatoryRules` 现算（`derivedTextsOf`，逻辑自 03 `renderDerivedRules` 平移：DETAIL_INQUIRY / PROGRAMME_NAME+GOVERNMENT_ORGANIZATION+证据行 / IP；行缺失回落常量占位文本保持 22 条形态）；`save()` 对入参 `derived=true` 条目一律忽略（文本弃用）→ 库中恒 19 条（22−3）；响应 `derived=true` 标记。合并槽位规则前后端共用：总长 ≥ 21 时派生恒占第 18/19/21 位，可编辑条按序填充其余位（删除/追加后编号仍渲染自下标，I-32 语义自洽：派生编号恒定 18/19/21，其余自动前移/后延——A-2 追加显示 23、A-6 删除后第 4 条显示 3. 均满足）。
- **I-32**：存储 JSON = 纯字符串数组（无 no/index 字段，服务测试断言条目不含 `no`/`index`/`derived` 键）；编号只在渲染/拼接时按下标生成。编辑正文不引用编号的约束只作文档说明（brief 明示非硬校验，计划原文无校验要求）。
- **I-33**：`save()`/`resetToDefault()` 在**同一事务**（@Transactional）内写 `operator_action_log`（NamedParameterJdbcTemplate 直写，先例 04 审计写法）：`target_type='RAG_PROMPT_CONFIG'`、`target_id=1`、action_type `SAVE_RAG_PROMPT_CONFIG` / `RESET_RAG_PROMPT_CONFIG`、before/after = 整份配置 JSON、note = 按段的 diff 明细（公共前后缀裁剪 + 逐位对齐：changed[afterIndex+before+after]、added[新下标+text]、deleted[原下标+text]）、operator、created_at 默认。测试断言改动下标与新旧值、新增、删除、操作人。
- **I-34**：`RagPromptBuilder` 注入 `RagPromptConfigService`；`retrievalSystemPrompt()`/`generationSystemPrompt()`/`generationSystemPrompt(mandatoryRules)` 全部经 `effective()`（每次构建取当前值）；无服务实例的 03 既有测试路径（`RagLetterComposerTest` 直构 `RagPromptBuilder(objectMapper)`）回落 `defaultEffective(mandatoryRules)` 产生与旧实现逐字相同的默认提示词（compose 测试 14/0/0/1 全绿，含「12. Do not write a salutation…」与 `{{FACT:KB-PROG-002}} and {{FACT:KB-FUND-033}}` 派生令牌断言）。服务端派生/合并/常量访问全部收敛在服务文件内；builder 内 `RagPromptConstraints\.` grep 恒空。
- **前端卡片数据流**：GET → base/derived 拆分（state）→ 渲染合并（与服务端同款槽位）→ 行操作（编辑/撤销/删除/追加）→ `markRagPromptDirty()`（已修改 N 处 · 未保存 / 保存按钮 disabled 翻转）→ PUT 全量回传（含 derived 标记，服务端过滤）→ 重新 load 复位。徽章：脏行「已改」/新行「已添加」（蓝）、服务端 provenance「本次改动」/「新增」（第 12/22 条且文本与常量逐字相同才下发，A-1）、派生行「派生 · 只读」（灰）。

## 命令与结果（JDK 11 zulu-11；最终代码态新鲜执行，含提交后复核）

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | G-5 预 grep（HEAD 态、改 index.html 前）：`grep -rn "v=$(grep -o 'styles.css?v=[^"]*' index.html \| cut -d= -f3)" src/test/js/` | 0 | HEAD 钉值 `20260902-rag-workbench` 共 7 文件（batchSend 49-51 字面 + checkRepliesRelocation:11 / manualReplySubjectPrefill:13 / overlayAndDialogContrast:22 / ragWorkbenchRender:20 / trustReplyWorkbenchSharedMount:22+5+175 / ragKnowledgeBasePage:332-338）—— 计划 T6 清单（只有 batchSend）过期；全部按 master-G-5 同步（§变更文件 11-16） |
| 2 | `node --test src/test/js/ragPromptConsole.test.js` | 0 | **tests 7, pass 7, fail 0** |
| 3 | `JAVA_HOME=…/zulu-11.jdk mvn test -Dtest=RagPromptConfigServiceTest` | 0 | **Tests run: 6, Failures: 0, Errors: 0, Skipped: 0**（docker-free Mockito；另 exec 插件顺带跑 JS 全量 648/0） |
| 4 | `JAVA_HOME=…/zulu-11.jdk mvn test -Dtest=RagLetterComposerTest`（回归：builder 直构兼容 + 默认提示词逐字等价） | 0 | **Tests run: 14, Failures: 0, Errors: 0, Skipped: 1**（skip = c3 I-46 登记占位） |
| 5 | `JAVA_HOME=…/zulu-11.jdk mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | 1 | **环境阻塞（记录，非实现缺陷）**：`client version 1.32 is too old. Minimum supported API version is 1.40`（docker-java 1.19.8 vs daemon；Tests run: 1, Errors: 1）—— c1/c4/c5 同因同文 |
| 6 | `node --test src/test/js/*.test.js` | 0 | **tests 648, pass 648, fail 0** |
| 7 | `node --check src/main/resources/static/app.js` | 0 | SYNTAX_OK |
| 8 | `JAVA_HOME=…/zulu-11.jdk mvn test`（全量回归） | 0 | **Tests run: 3114, Failures: 0, Errors: 0, Skipped: 8**，BUILD SUCCESS（c6 基线 3108 → +6 = RagPromptConfigServiceTest；skip 8 = 7 docker/IT 门控 + 1 既有 @Disabled） |
| 9 | `JAVA_HOME=…/zulu-11.jdk mvn clean package` | 0 | **Tests run: 3114, Failures: 0, Errors: 0, Skipped: 8**，BUILD SUCCESS，WAR 构建 target/weibo-talent-introduction-1.0.0-SNAPSHOT.war |
| 10 | `git diff --check` | 0 | 无输出 |
| 11 | `grep -n "RagPromptConstraints\." src/main/kotlin/.../RagPromptBuilder.kt`（I-34） | 1 | 无输出 |
| 12 | `grep -c 'id="aiTrainingFreeFormPrompt"' / `id="aiTrainingConstraints"` index.html（S-5/D-14） | — | 各 1 |
| 13 | `grep -rn "20260902-rag-workbench" src/`（G-5 收口） | 1 | 无输出；index.html 三处 = `20260902-rag-prompt-console` 单值 |
| 14 | 提交后复核：`git status --porcelain` | — | 仅 `?? docs/plans/fast/2026-09-02-execution-order/children/c7/`；commit e76b5a9 含 16 文件，`git show --stat HEAD \| grep -c docs/plans/fast` = 0 |

## 迁移验证（V115，scratch 补丁链）

- **Flyway IT fresh 链环境阻塞（基线复现）**：命令 5 同因同文（docker client API 1.32 vs 1.40，c1/c4/c5 先例）；fresh 全链经 scratch runner（`talent_fresh` 空库）复现 `Migration V82__split_trust_reply_atomic_facts.sql failed` —— SQLSTATE 45000 `V82 baseline drift: audited legacy QA rules changed`（V1..V81 成功，fresh 链 V82 门禁必失败，任何 fresh 链到不了 V115）。
- **V115 替代验证（通过）**：scratch mysql:8.0.36（`v115probe`，端口 3307，已删除）→ V1..V81（target 81，placeholder-replacement=false 与 application.yml 一致）→ 按 c1 同法对齐 qa_rule（'Contract and IP arrangements' 行 28→34、两行 updated_at 对齐门禁字面量，gate17=1/gate34=1）→ V82..V115 全链成功（`flyway_schema_history version=115`，112/113/114 依序在链上）。断言全绿：表列 `id,retrieval_constraints,generation_constraints,updated_at,updated_by`；两列 TEXT NULL；updated_at DEFAULT CURRENT_TIMESTAMP ON UPDATE；PRIMARY KEY(id) + `chk_rag_prompt_config_singleton CHECK (id=1)`；**无外键**（information_schema fk_count=0）；InnoDB/utf8mb4；表/列注释记录 I-30 语义；种子单行 `id=1` 两列 NULL、updated_by NULL。

## 偏离（登记；无计划修订）

1. **G-5 追加 6 个钉值测试文件（超出计划清单 #8）**：HEAD 实测 7 个文件钉 `20260902-rag-workbench`（计划 T6 只列 batchSend）；按 master-G-5 权威与 brief 授权把 6 个额外文件同步为 `20260902-rag-prompt-console` 单值（checkRepliesRelocation / manualReplySubjectPrefill / overlayAndDialogContrast / ragKnowledgeBasePage / ragWorkbenchRender / trustReplyWorkbenchSharedMount；含注释/用例标题里的字面量，保持残留 grep 干净）。
2. **派生行「规则行缺失」的降级语义微调**：03 的 compose 路径对缺失强制规则行的派生槽位是「留空剔除」；c7 服务端 effective() 对空派生文本回落常量占位（保持页面恒 22 条且编号稳定）。种子里 6 行恒在、指纹门禁之外无删除路径，该差异仅在人工改库删除规则行的退化场景可见；已登记。
3. **审计落 `operator_action_log`（typed 枚举外直写）**：I-33 未指定审计表且 V115 仅建 rag_prompt_config（brief 禁止额外建表）；operator_action_log 是本仓唯一通用操作审计表（target_type/target_id 泛化、before/after/note/operator 列齐备、无联系人 FK 约束）。枚举 OperatorActionType 不在授权文件内，故按 04 审计同款 NamedParameterJdbcTemplate 直写，action_type 用表列注释同风格的自由串 `SAVE_RAG_PROMPT_CONFIG`/`RESET_RAG_PROMPT_CONFIG`（表定义 action_type 为 VARCHAR 注释示例非穷举）。新行 target 无 expert/inbound 上下文，不在既有操作日志 UI 的联系人/邮件过滤中出现，仅作 SQL 审计轨迹。若后续要接 UI/枚举，需另开计划加枚举常量。
4. **provenance 徽章（本次改动/新增）由服务端按「槽位 + 文本与 03 常量逐字相同」判定**：计划 A-1 需要第 12/22 条徽章但未指定数据来源（mockup 仅演示）；实现为默认态下发 `changed`/`added` 标记、运营改过即自然消失（测试覆盖默认态 12/22 徽章 + 编辑后变「已改」）。
5. **无服务实例的降级路径**：builder 构造器第二参默认 null 保留 `RagPromptBuilder(objectMapper)` 兼容（RagLetterComposerTest 未授权不改），回落 `defaultEffective(mandatoryRules)` 与旧行为逐字等价（命令 4 验证）。
6. **审计 diff 算法**（公共前缀/后缀 + 中间逐位对齐）：对运营单次保存的编辑/追加/删除给出确定可读的 changed(下标+新旧)/added/deleted 明细；不追求任意混合序列的最小编辑序列（已文档化于服务 KDoc）。
7. **前端卡片 head 采用 h2 + 少量 inline style（margin: 0）**：styles.css 契约仅允许 EOF 追加 S-1..S-4（brief #7），卡片标题 margin/间距用静态 HTML inline style（index.html 既有 67 处 inline style 先例，mockup 同款）；savebar 右对齐按钮容器同款。

## 新鲜度

- Plan identity 复算: YES（46cc5c46 未变，plan diff 为空；master 92b0519 未变）
- Worktree identity 复算: YES（branch `fast/2026-09-02-execution-order`；实施前 HEAD e7ddb20 → 实施后 e76b5a9，60efcba 为 ancestor）
- 实现提交不含 fast-p 证据: YES（commit e76b5a9 恰 16 授权文件；docs/plans/fast/** 未纳入；git status 仅余 c7 证据目录 untracked）
- 必需命令最终代码态新鲜执行: YES（命令 1-14 全部在最终态执行/复核，提交后复核无 src 差异）
- 环境副作用清理: scratch 容器 v115probe（含 talent_fresh 复现库）已删除；/tmp/c7_chain 与 cp/输出文件已清；主 checkout 无残留（`git status --porcelain -- src/` 为空；其 docs/knowledge 等改动为控制器会话自身产物，非本任务写入）
- 历史输出仅作基线: YES（V82 fresh 失败与 docker API 不匹配在 scratch/fresh 现跑复现）
