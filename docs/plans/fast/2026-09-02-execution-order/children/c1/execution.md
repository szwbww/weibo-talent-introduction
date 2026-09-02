# c1 Execution Report — 01 RAG 知识库数据层：V112 五张表 + 45 条种子 + 快照与指纹

- Result: **READY_FOR_VERIFICATION**（含控制器裁定的环境阻塞记录，见 §偏离 4）
- Plan: `docs/plans/2026-09-02/01-rag-knowledge-base-schema.md`
- Plan identity: `commit:92b0519a18a3a46989f8733259af4649f7748a72`（A1/A2 修订后；`git diff 92b0519 -- <plan>` 为空）
- Plan sha: `92b0519a18a3a46989f8733259af4649f7748a72`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-02-execution-order`
- Branch: `fast/2026-09-02-execution-order`（`git rev-parse --abbrev-ref HEAD` 确认）
- Master plan: `docs/plans/2026-09-02/00-execution-order.md`（同 identity；G-1..G-4、G-9 逐一遵守）
- Child base (product boundary) SHA: `bbf08287d91bd7a540401bfe71c8dc8baecd34f3`（`git merge-base --is-ancestor bbf0828 HEAD` 通过）
- Implementation commit: `acb88c1e77d172a7f252690b1da1203f08c01817`（`feat(fast-p): implement c1`，11 个授权文件，docs/plans/fast/** 未纳入）
- Task status: COMPLETE（10 项验收用例在真实 MySQL 上全绿；两项 docker 门禁按控制器裁定记录为环境阻塞并给替代验证）

## 变更文件（11，含 A2 授权第 11 项）

| # | 文件 | 动作 |
|---|---|---|
| 1 | `scripts/export_rag_kb_sql.py` | 新增（机器生成 V112 种子段 + G-2 指纹；禁止手抄） |
| 2 | `src/main/resources/db/migration/V112__create_rag_knowledge_base.sql` | 新增（DDL 手写 + export 输出的种子段） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/rag/domain/RagFact.kt` | 新增（T3；I-1/I-2/I-4/I-5 解析与归一） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/rag/domain/RagRetrievalRule.kt` | 新增（T2/T3 四张规则表模型 + RagKbMeta + 聚合；D-3 行 init 校验） |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/rag/repository/RagFactRepository.kt` | 新增（T3；仅两个查询） |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/rag/repository/RagRetrievalRuleRepository.kt` | 新增（T3；NamedParameterJdbcTemplate 整表一次读出） |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagKnowledgeBase.kt` | 新增（T4；verifyAndPublish @PostConstruct 门禁 + republish + @Volatile 快照 + G-2 指纹唯一实现） |
| 8 | `src/main/kotlin/com/weibo/talentintroduction/rag/config/RagProperties.kt` | 新增（T5；默认值 + 注册类同构 FactRetrieverProperties） |
| 9 | `src/main/resources/application.yml` | 修改（`talent-introduction.rag:` 块，10 项默认值） |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/rag/RagKnowledgeBaseTest.kt` | 新增（T6 验收；10 用例） |
| 11 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 修改（A2 授权：5 处 targetSchemaVersion 钉值 V111→V112 + 新增 V112 rag_* 表/种子断言 + queryString helper） |

未触碰：`qa_rule`/`qa_category`、既有迁移 V1..V111、`docs/plans/fast/**`（控制器单独提交证据）。

## 关键实现

- **A1 指纹**：常量 `e62421a42c432cf3`。规范化：45 行按 `fact_code` 升序；每行 V112 数据列（不含 id/审计列）以 `|` 连接（`legacy_rule_id` NULL 记空串、`enabled` 记 1/0、`seq`/`sort_order` 整数、字符串不加引号）；行间 `\n`；SHA-256 hex 前 16 位。Kotlin `RagKnowledgeBase.fingerprintOf` 与 Python `export_rag_kb_sql._corpus_fingerprint` 逐字节等价；种子 `sort_order` = fact_code 升序序号 1..45，保证 Kotlin（读库列）与 Python（同序枚举）一致。
- **V112**：`rag_fact`(45) / `rag_phrase_group`(87) / `rag_intent_coverage`(21) / `rag_mandatory_rule`(6，含 D-3 sort_order 15) / `rag_prefilter_exclusion`(4，T2 标题「3 行」为笔误) / `rag_kb_meta`(单行，CHECK id=1)。表注释写明 D-1；`legacy_rule_id` 只读（G-4）；无任何调用方（G-4 交互点为零）。
- **I-3 / P0-3**：`verifyAndPublish()`（@PostConstruct，只读，不写 meta，失配抛 IllegalStateException 且消息同时含期望值与实际值）；`@Transactional republish(writeInTx)`（事务内写→重读→UPDATE rag_kb_meta→afterCommit 才发布快照；无旧指纹比对）。
- **配置**：`RagProperties` prefix `talent-introduction.rag`，yml `rag:` 块默认值与 T5 一致（prefilter-limit 18 … generation-max-tokens 2600）。

## 命令与结果（最终状态后本轮新鲜执行）

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | `mvn test -Dtest=RagKnowledgeBaseTest -DmigrationIt=true`（env `RAG_KB_TEST_DB_URL` 指向 scratch 补丁链，见 §偏离 4） | 0 | **Tests run: 10, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS — T6 全部语义在真实 MySQL 8.0.36（V1..V112 已应用）上通过：G-2/I-3 指纹门禁、I-3 漂移异常（消息含期望+实际指纹）、P0-3 republish 后 verifyAndPublish 通过、I-3b 回滚保持旧快照与 meta、I-6 实例语义、I-1/I-2/I-4/I-5/D-3 |
| 2 | `mvn test -Dtest=RagKnowledgeBaseTest -DmigrationIt=true`（默认 fresh Testcontainers 链） | 1 | **环境阻塞**（控制器裁定，见 §偏离 4）：fresh 链在 V82 门禁失败，10 Errors，与基线同因 |
| 3 | `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | 1 | **环境阻塞**：Tests run: 11, Failures: 0, Errors: 10 — 全部为 `V82 baseline drift: audited legacy QA rules changed`（fresh mysql:8.0.36）；基线 92b0519（无 V112）同命令复现 10 Errors 同因；第 11 项（V23 checksum，无 DB）通过 |
| 4 | `mvn clean package` | 0 | **Tests run: 3063, Failures: 0, Errors: 0, Skipped: 6**，BUILD SUCCESS |
| 5 | `mvn test`（全量回归） | 0 | **Tests run: 3063, Failures: 0, Errors: 0, Skipped: 6**，BUILD SUCCESS |
| 6 | `python3 scripts/spike_deepseek_reply.py --dump-kb \| python3 -c "…len(d)"` | 0 | `45` |
| 7 | `python3 scripts/export_rag_kb_sql.py \| grep -o "fingerprint[^;]*"` | 0 | `fingerprint e62421a42c432cf3` |
| 8 | `git diff --check` | 0 | 无输出 |

补充：`mvn test-compile`（离线）exit 0；V112 种子段与 `export_rag_kb_sql.py` 输出逐字节一致（205 行）。

## 偏离（全部经控制器裁定或已登记）

1. **RagKnowledgeBaseTest 门控**（控制器裁定，实现细节非计划修订）：类级加 `@EnabledIfSystemProperty(named = "migrationIt", matches = "true")`，与仓内全部 Testcontainers 测试同约定（AuthFlowIntegrationTest / FlywayMigrationIntegrationTest / *IT），保证普通 `mvn test`/`clean package` 不依赖 Docker 保持全绿。计划 T6 原表述未加门控，但 plain 回归门禁与仓内约定优先。另为 scratch 补丁链验证在 companion 增加外部库模式：设 `RAG_KB_TEST_DB_URL`（+ 可选 USERNAME/PASSWORD，默认 root/root）时不启动容器直接连已就绪库。
2. **V82 fresh 链环境阻塞**（控制器裁定，同仓先例 batch-send-rhythm-and-filter 02b/03「环境阻塞（非实现缺陷）」）：本环境 fresh mysql:8.0.36 全链迁移在 V82 `v82_trust_reply_baseline_gate` 失败 —— 门禁把 dev 库历史状态（`updated_at` 字面量 2026-06-26 / 2026-07-16、qa_rule id 34 位置）硬编码进确定性 fresh 链，fresh 链永远无法复现（实测：V1..V81 迁移成功；id 17 的 subject/keywords/SHA2 全匹配但 updated_at = 迁移运行时刻；'Contract and IP arrangements' 行在 fresh 链落在 id 28 而非 34）。基线复现：在树 `92b0519`（无 V112）用同容器镜像 fresh 全链迁移 → 同一 V82 错误。c1 未引入任何问题。
3. **替代验证（控制器裁定，同仓先例）**：scratch mysql:8.0.36 容器（`v82probe`，端口 3307）：migrate V1..V81 → 将 qa_rule 行 17/34 对齐到 V82 门禁字面量（两行 SHA2 本就匹配，仅 id/updated_at 需对齐，实测 gate17=1/gate34=1）→ migrate V82..V112 → 全链成功（version=112），断言全部通过：`rag_fact=45`、`DISTINCT fact_code=45`、`rag_kb_meta.fingerprint=e62421a42c432cf3`、`fact_count=45`、`rag_phrase_group=87`、`rag_intent_coverage=21`、`rag_mandatory_rule=6`（含 sort_order 15 COMPENSATION→KB-FUND-033）、`rag_prefilter_exclusion=4`、`enabled=0 恰 1`、`VERBATIM=7`。随后把 `RagKnowledgeBaseTest` 指向该库运行（命令 1）10/10 绿。容器与临时 worktree 已清理。
4. **V112 `source_refs` 无 `DEFAULT ''`**：MySQL 8 禁止 TEXT 列字面量默认值（实测 `BLOB, TEXT, GEOMETRY or JSON column 'source_refs' can't have a default value`），T2 的 `TEXT NOT NULL DEFAULT ''` 相应省略并在列注释登记；种子永远显式提供值，无行为影响。
5. **`rag_phrase_group` 实际 87 行**（计划措辞「约 120」）：按 A1/Brief 的构成（脚本 `_XXX_PHRASES` 常量 + 内联命名组 + COMPENSATION 组）推导为 12 个意图组 68 行 + POSITIVE_INTENT/NEXT_STEP/COMPENSATION_MENTION/GOVERNMENT_FUNDING_MENTION 4 个匹配组 19 行 = 87 行；export/V112/IT/测试四处以 87 一致落地并断言（下游 02 的 I-8③ 剔除与 I-12 CV 判定需要后 4 组）。
6. **A2 授权改动**：FlywayMigrationIntegrationTest 5 处无目标 migrate 的 `targetSchemaVersion` 钉值 V111→V112（方法名同步），并新增 V112 rag_* 表/种子断言测试（K-flyway-latest-version-test-pin 约定）。
7. **A1 常量修订**：G-2 常量 `2b29a2152f2671df` → `e62421a42c432cf3`（计划/04 计划/mockup 由控制器在 92b0519 一并修订）；本实现全部引用新常量。

## 新鲜度

- Plan identity 复算: YES（SHA 92b0519 未变，plan diff 为空）
- Worktree identity 复算: YES（branch `fast/2026-09-02-execution-order`，HEAD 自 seed 演进）
- 实现提交可达目标分支: YES（`acb88c1` 在分支上，bbf0828 ancestor）
- 必需命令最终状态后本轮新鲜执行: YES（命令 1–8）
- 历史输出仅作基线: YES（V82 失败基线在 92b0519 现跑复现）
- 环境副作用清理: ~/.testcontainers.properties 已恢复原状；scratch 容器 `v82probe` 与临时 worktree `/tmp/base92` 已移除
