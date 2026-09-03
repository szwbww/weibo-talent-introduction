# c4 Execution Report — 03b RAG 草稿采用 → 人工发送桥接

- Result: **READY_FOR_VERIFICATION**
- Plan: `docs/plans/2026-09-02/03b-rag-send-bridge.md`
- Plan identity: `commit:f63153a1e7bc03fa0455b94453787990c6c09a23`（A3/A4 修订后；`git diff f63153a -- <plan>` 为空，已复核）
- Master plan: `docs/plans/2026-09-02/00-execution-order.md`（identity `92b0519a18a3a46989f8733259af4649f7748a72`；G-1..G-8 遵守）
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-02-execution-order`
- Branch: `fast/2026-09-02-execution-order`（HEAD `ae755c4`）
- Child base (product boundary): `10a38bb6457280f7104a333faa46fad6f7cb078f`（c3 代码头）
- Implementation commit: `ae755c4b8b851f17cbf82db081d631332cee83e4`（`feat(fast-p): implement c4`，10 个授权文件；docs/plans/fast/** 未纳入）
- Task status: COMPLETE（全部必需门禁：单项 Kotlin/JS/回归全绿、clean package 3107/0/0/7、全量 mvn test 3107/0/0/7；Flyway IT 记录环境阻塞并给替代验证；V113 在 scratch 补丁链验证）

## 变更文件（10，含 A3/A4 授权的既有测试修改；全部为计划 `## 变更文件清单` 文件）

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql` | 新增（T1 DDL：UNIQUE(mail_record_id, ordinal)、corpus_fingerprint、表注释 I-39；无 FK 到 rag_fact/mail_record） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailRecordRagFact.kt` | 新增（T2，`@Table("mail_record_rag_fact")`） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRagFactRepository.kt` | 新增（T2：CrudRepository + `findByMailRecordIdOrderByOrdinalAsc`，与 MailRecordQaRuleRepository 同形） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 修改（构造器 +2 可空默认协作件；`sendManualRichReply` 尾部 +2 可空参数；I-39 互斥 400；I-41 指纹 400/409；I-40 快照 code 422；RAG carriesQa/factResolution/serverSuggestedFactIds/primaryRuleId 分支；I-47 `ragSend` 守卫；成功后按 I-42 写存证；DTO +2 字段） |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 修改（透传 `ragFactCodes`/`ragCorpusFingerprint`，2 行） |
| 6 | `src/main/resources/static/app.js` | 修改（`adoptTrustReplyAssembly` 形态分流 + SKIP preflight（I-44）＋清遗留定时器；发送组装 RAG 分支；正文来源兼容 05 载荷 `text` 键） |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt` | 新增（T6；10 用例，mocked docker-free） |
| 8 | `src/test/js/ragAdoptSendBridge.test.js` | 新增（I-44/I-39 前端契约 5 用例） |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt` | 修改（**A3 授权**：sendManualRichReply 增 2 形参后 4 处 stub 各补 2 个 `Mockito.any()`，17→19 matcher；先例 a21784e） |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 修改（**A4 授权**：EXCLUDED_NOISE_SITES 钉死行 1116→1118，context 不变；先例 05 P-E/A5） |

未触碰：`docs/plans/fast/**`、`qa_rule`/`qa_category`、`mail_record_qa_rule` 读写、迁移 V1..V112、c1-c3 已提交文件、`index.html`/`styles.css`（diff 恒空，缓存键交 c6）、`ReplySnippetService`、`TrustReplyWorkbenchService`。

## A3/A4 登记（计划增补授权，均经人工批准）

- **A3**（plan 5a420584）：计划原清单 8 文件不含 `UnmatchedInboundTrustWorkbenchTest`，但该必需回归门禁以 17 个位置 matcher stub `sendManualRichReply`（基线 17 参）；计划强制 +2 尾参后方法 19 参 → 4 个用例 `InvalidUseOfMatchers`（19 expected/17 recorded）。授权第 9 文件做机械修正（4 个 `when()` 块各补 2 matcher），与本仓先例 `a21784e` 一致。
- **A4**（plan f63153a）：授权控制器第 5 文件 +2 转发行使 `OperatorStatusWriteSeamGuardTest` 的 EXCLUDED_NOISE_SITES 钉死行 1116→1118 的单行修正（第 10 文件）；守卫扫 `src/main/kotlin`，本计划 2 行净增使控制器文件命中集变化。

## 关键实现（对照不变量）

- **I-39**：互斥判定插在 `:181` assembly 校验之前 → `400 SEND_EVIDENCE_SOURCE_CONFLICT`；RAG 成功路径 `mail_record_qa_rule` 零交互（`verifyNoInteractions` 断言）。
- **I-40**：fact_code 只对 `RagKnowledgeBase.snapshot().facts`（存在且 enabled）校验 → 422 `RAG_FACT_CODE_UNKNOWN`；分支内不调 `canonicalizeFactRuleIds`/`qaFactSelectionService.select`/`qaRuleRepository`（三者 `verifyNoInteractions` 断言）。
- **I-41**：指纹缺失 → 400 `RAG_FINGERPRINT_REQUIRED`；不符 → 409 `RAG_CORPUS_STALE`；均在 claim/SMTP 前（无任何发送尝试断言）。
- **I-42**：成功后按请求原序 `saveAll`（mapIndexed ordinal），重复 code 不去重（落 4 行 ord 0..3 断言）。
- **I-43**：RAG 路径 `primaryRuleId = null`（SendPayload 断言），canonical 空 → matched_qa_rule_id 不写。
- **I-47**：`collectSafetyFindings` 尾参 `ragSend: Boolean = false`（两既有调用点零改动）；RAG 调用点 `carriesQa=false` + `ragSend=true`；trust-rhetoric 后 `if (ragSend) return findings` 整段短路 selection/trust-gap/intent/action-policy（纯添加）；虚构数字正文仍触发 `AI_REPLY_CLAIM_HALLUCINATED_FACT` 且不含 `QA_FACTS_ALL_INVALID`，确认后照常发出。
- **I-44**：RAG 形态不调度 preflight（schedulePreflightCheck 计数 0）并清遗留防抖；canonicalFactIds 形态行为逐字不变（快照 + preflight 恰 1 次）。
- 旧路径零改写：diff 中 `:181-196` verifyAssembly/元素相等、`:208` canonicalize、carriesQa/safety 旧分支文本逐字保留（仅新增 else-if 分支）。

## 命令与结果（JDK 11 zulu-11；最终代码态新鲜执行）

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | `mvn test -Dtest=RagSendBridgeTest` | 0 | **Tests run: 10, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS |
| 2 | `node --test src/test/js/ragAdoptSendBridge.test.js` | 0 | **# pass 5 / # fail 0** |
| 3 | `mvn test -Dtest=PendingMailOperationServiceTrustWorkbenchTest` | 0 | **Tests run: 59, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS |
| 4 | `mvn test -Dtest=UnmatchedInboundTrustWorkbenchTest` | 0 | **Tests run: 12, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS（A3 修正后） |
| 5 | `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | 1 | **环境阻塞（记录，非实现缺陷）**：Testcontainers 无法连 daemon —— docker-java client API 1.32 被拒（`Minimum supported API version is 1.40`，本机 daemon 29.4.0/OrbStack）；基线 `10a38bb` 同命令同因复现（Tests run: 1, Errors: 1）。V82 fresh 门禁另经 scratch runner 等价复现（同因同文：`V82 baseline drift: audited legacy QA rules changed`）；V113 经 scratch 补丁链验证通过（见 §迁移验证） |
| 6 | `node --test src/test/js/*.test.js` | 0 | **tests 809, pass 809, fail 0** |
| 7 | `node --check src/main/resources/static/app.js` | 0 | SYNTAX_OK |
| 8 | `git diff src/.../PendingMailOperationService.kt`（提交前） | — | 纯新增分支（+109/−8，无 :181-215 逻辑改写） |
| 9 | `git diff --stat styles.css index.html` | — | **空输出**（缓存键不 bump，交 c6） |
| 10 | `mvn clean package` | 0 | **Tests run: 3107, Failures: 0, Errors: 0, Skipped: 7**，BUILD SUCCESS，WAR 构建 |
| 11 | `mvn test`（全量回归） | 0 | **Tests run: 3107, Failures: 0, Errors: 0, Skipped: 7**，BUILD SUCCESS（c3 基线 3097 → +10 = RagSendBridgeTest；skip 7 = 6 docker/IT 门控 + 1 I-46 @Disabled） |
| 12 | `git diff --check` | 0 | 无输出 |

## 迁移验证（Flyway IT）

- **环境阻塞（基线复现）**：`FlywayMigrationIntegrationTest -DmigrationIt=true` 在基线 `10a38bb` 与最终代码态均 exit 1 —— Testcontainers 1.19.8 的 docker-java ping 固定 client API 1.32，本机 daemon 29.4.0 最低要求 1.40（OrbStack；c1 时代可启动、本会话已升级），任何 Testcontainers IT 均无法启动容器。`DOCKER_HOST`/`DOCKER_API_VERSION=1.41` 无效。
- **V82 fresh 门禁等价复现（scratch runner，非 Testcontainers）**：clean 全链 → `Migration V82__split_trust_reply_atomic_facts.sql failed — V82 baseline drift: audited legacy QA rules changed; stop deployment and merge manually — Statement: CALL v82_trust_reply_baseline_gate()`（与 c1 记录同因同文：V1..V81 成功，fresh 链 V82 门禁必失败）。
- **V113 替代验证（scratch 补丁链，通过）**：scratch mysql:8.0.36（`v113probe`，3307）→ V1..V81 → 按 c1 同法对齐 `qa_rule` 行 17/34 到门禁字面量（行 28→34、updated_at 对齐；无 FK 引用、SHA2 全匹配）→ V82..V113 全链成功（`flyway_schema_history version=113`）。断言全绿：列序 `id,mail_record_id,fact_code,ordinal,corpus_fingerprint,created_at`；`uk_mail_record_rag_fact(mail_record_id,ordinal)` UNIQUE + record/code 普通索引；**无外键**；InnoDB/utf8mb4/表注释；重复 fact_code 按原序可写（ord 0,1,2）、同 (record,ordinal) 重复插入报 1062（I-42 语义）。

## 清理与新鲜度

- 主 checkout（`/Users/lukai/IdeaProjects/weibo-talent-introduction`）：epoch-1 编辑工具相对路径误写其 `PendingMailOperationService.kt` 已 `git checkout --` 还原并复核（无残留）。
- scratch 容器 `v113probe` 已删除；`/tmp/base10a38` worktree 已移除。
- Plan identity 复算: YES（f63153a diff 空；master 92b0519 未变）；Worktree identity: YES（branch `fast/2026-09-02-execution-order`，HEAD ae755c4）。
- 提交不含 fast-p 证据: YES（仅 10 个授权文件；`docs/plans/fast/**` 未纳入；`git status` 仅余控制器 ledger.md 与 c4 文档 untracked）。
- 必需命令最终代码态新鲜执行: YES（命令 1-12 全部在提交前最终态执行/复核）。
