# 03 · CRS 置信分与阈值样本日志

日期：2026-08-18
基线 commit：`4583525`（main）
主计划：[00-auto-reply-convergence-master.md](./00-auto-reply-convergence-master.md)
前置依赖：**必须在 [01](./01-decide-context-closure.md) 通过人工验收后执行**；建议在 [02](./02-preview-into-workbench.md) 之后
子系统数：2（mail 服务与持久化 / 配置与迁移）
文件数：11

## 需求描述

### Observable outcome

1. 每一封走到 QA 分支的来信，产出一个 0–100 的置信分 CRS 及其四个分量，并落库到新表 `auto_reply_confidence_log`。
2. 引入**影子打分模式**：`LLM_AUTO_REPLY_ENABLED=false` 时也能跑完整生成与打分并攒样本，但**绝不发送**。这是阈值反解的唯一数据来源。
3. 日志表可用于导出「人工是否改动 vs CRS」的样本对，用于后续反解 A 线 / B 线。

### What must NOT change

- `readyToSend` 的判定逻辑（`resolveReason()` + `passesSendGate()`）——CRS 是**旁路观测量**，本轮不参与任何发送决策。
- `LLM_AUTO_REPLY_ENABLED=false` 时不发出任何自动回复邮件。
- 01 建立的上下文收口（I-1~I-5）。
- 预览的只读性质：预览侧**只展示分数，不写日志**（01 的 I-4）。
- 收信主流程不因日志写失败而中断。

### Out of scope

- 分档动作（A/B/C 三档）与自动投递放行——本轮只打分不分档，`tier` 列先落库为 `SHADOW` 常量。
- 阈值 A 线 / B 线的具体取值——必须在样本攒够后从数据反解，不在计划里写死。
- 熔断规则实现（依赖退信分类缺陷修复，见主计划「已知前置缺陷」）。
- 「人工是否改动」的自动回填——本轮只预留列，回填留待 02 的宿主接入后单独立项。
- 前端展示 CRS（→ 后续；02 的 `AUTO_PREVIEW` 宿主已预留位置）。

## 关键不变量

### Invariant I-1: CRS 不参与发送决策
- Rule：`GroundedAutoReplyDecision.readyToSend` 的值必须完全由既有 `resolveReason()` 与 `passesSendGate()` 决定。CRS 及其分量、`tier` 均不得出现在这两个方法内，也不得在 `decide()` 返回前修改 `readyToSend`。
- Applies to：`GroundedAutoReplyDecisionService.decide()`。
- Violation consequence：一个未经校准的分数直接决定对外发信，且校准所需的样本恰恰还没攒到——因果倒置。
- 来源：original

### Invariant I-2: 影子模式绝不发送
- Rule：`shadowScoringEnabled = true` 且 `autoReplyEnabled = false` 时，`decide()` 必须执行完整生成与打分，但返回的 `readyToSend` **恒为 `false`**，`reason` 恒为 `AI_AUTO_REPLY_DISABLED`。
- Applies to：`GroundedAutoReplyDecisionService.decide()` 的 kill-switch 分支。
- Violation consequence：把"攒样本"的开关误变成"开自动发"的开关——这是本计划风险最高的一处。
- 来源：original

### Invariant I-3: 日志写入是 best-effort，永不阻断收信
- Rule：`auto_reply_confidence_log` 的写入必须包在 `runCatching { }` 内并记 `log.warn`；写失败不得抛出、不得回滚 `processSingle()` 的事务、不得改变 `SinglePipelineResult`。
- Applies to：`AutoMailReplyService` 内新增的唯一写入点。
- Violation consequence：一张观测表把整条收信链路拖挂。
- 来源：K-inbound-processing-write-paths（"副作用须 best-effort（runCatching）避免阻断收信主流程"）

### Invariant I-4: `createdAt` 必须显式非空
- Rule：`AutoReplyConfidenceLog` 实体的 `createdAt` 在唯一写路径上必须传入非空 `LocalDateTime`。
- Applies to：`AutoMailReplyService` 内的日志构造点。
- Violation consequence：Spring Data JDBC 会把 Kotlin nullable 属性的 `null` 绑定为 SQL NULL；MySQL 的 `NOT NULL DEFAULT CURRENT_TIMESTAMP` 只在**省略列**时生效，不会覆盖显式 NULL，导致 INSERT 失败或写入 NULL。
- 来源：K-spring-data-jdbc-null-default

### Invariant I-5: 分量取数不新增计算口径
- Rule：CRS 的四个分量必须只从 `AiReplyDraftResult` 与 `verifyAutoEvidenceRuleIds()` 的现有产物派生，禁止在 scorer 内重新做 QA 匹配、重新判 grounding、或重新调用 LLM。
- Applies to：`AutoReplyConfidenceScorer`。
- Violation consequence：分数与判定基于两套口径，01 建立的收口白做。
- 来源：K-ai-generate-single-freeform-seam（口径收口在 service 内）

### Invariant I-6: 预览侧不写日志
- Rule：`AutoReplyPreviewService` 不得注入 `AutoReplyConfidenceLogRepository`，不得触发任何日志写入。
- Applies to：`AutoReplyPreviewService`。
- Violation consequence：破坏 01 的 I-4（预览只读），且用"运营点了几次预览"污染样本分布。
- 来源：K-preview-mirrors-pipeline、01 的 I-4

## 现状审计

### 新表 `auto_reply_confidence_log`（本计划新建）

- **当前最大迁移号**：`V103`。

```
$ ls src/main/resources/db/migration | sed -n 's/^V\([0-9]*\)__.*/\1/p' | sort -n | tail -3
101
102
103
```

→ 新迁移编号 `V104`。

- **Flyway 占位符风险**：生产 `application.yml` 未关 `placeholder-replacement`（默认 true）。本迁移**不得包含任何 `${...}` 字面量**，否则生产启动即抛 `No value provided for placeholder expressions`。本计划的 DDL 全部为纯 SQL，无模板变量。（来源：K-flyway-placeholder-replacement）
- **Write paths（本计划建立，唯一 1 处）**：`AutoMailReplyService`，在 `decide()` 返回后、`markManualReview` / 发送分支之前。
- **Read paths（本计划不建立）**：本轮无程序读取方，仅供 SQL 直查导出样本。`tier` 列先恒为 `'SHADOW'`。

### `LlmProperties`（要扩展的配置类）

当前（`src/main/kotlin/com/weibo/talentintroduction/config/LlmProperties.kt`，逐字）：

```kotlin
@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.llm")
data class LlmProperties(
    val enabled: Boolean = false,
    val autoReplyEnabled: Boolean = false,
    val apiUrl: String = "",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val replyFlashModel: String = "deepseek-v4-flash",
    val replyProModel: String = "deepseek-v4-pro",
    val timeoutMs: Int = 30_000,
    val temperature: Double = 0.3,
    val freeFormTemperature: Double = 0.6
)
```

对应 `application.yml:110`：`auto-reply-enabled: ${LLM_AUTO_REPLY_ENABLED:false}`。

**构造点规模**：

```
$ grep -rn "LlmProperties(" src/test --include=*.kt | wc -l
184
```

184 处测试构造，抽样均为具名参数形式（`GroundedAutoReplyDecisionServiceTest.kt:56-57` 的 `LlmProperties(enabled = true, autoReplyEnabled = autoReplyEnabled)`；`AiReplyDraftServiceTest.kt:924` 的 `LlmProperties(enabled = true, apiUrl = "http://llm", autoReplyEnabled = true)`；`TrustReplyWorkbenchServiceTest.kt:90` 的 `LlmProperties(enabled = true)`）。

**硬约束**：新字段必须**追加在参数列表末尾**且**带默认值**。满足这两条时，具名构造与位置构造均不受影响，184 处一处不用改。若把新字段插在中间，位置构造会静默错位赋值——那是编译能过、行为错乱的最坏情形。执行时以 `mvn test -Dtest=AiReplyDraftServiceTest` 的编译结果为准核销。

### `GroundedAutoReplyDecisionService`（01 改造后的形态）

- kill-switch 分支（01 后仍在 `decide()` 开头）：`if (!llmProperties.autoReplyEnabled) return disabledDecision(subject)`。
  本计划要把它改成「关闭时若 shadow 打开，仍走完整流程但强制 `readyToSend = false`」（I-2）。
- 打分所需的现成产物（全部来自 `AiReplyDraftResult`，I-5）：
  - `requestFacts: List<RequestFactItem>`，每项含 `status: RequestGroundingStatus`（GROUNDED / PARTIAL / UNSUPPORTED）与 `intents: List<RequestIntentCoverage>`（`status` 为 `"SUPPORTED"` / `"PARTIAL"` / `"MISSING"` 字符串）。
  - `qaRuleIds: List<Long>`。
  - `contextWarnings: List<String>`。
  - `draftReadiness: AiReplyDraftReadiness`（READY / NEEDS_REVIEW / BLOCKED）。
  - `generationState: AiReplyGenerationState`、`usedLlm: Boolean`。
  - `verifyAutoEvidenceRuleIds(draft.qaRuleIds)` 的返回（已按 AUTO/enabled/非空 过滤）。

### `AutoMailReplyService`（唯一日志写入方）

- `decide()` 调用点：`:505`（01 后签名已含 contact）。
- 该点作用域内可用于日志的标识：`contactId`（`:99`）、`inboundMailRecordId`、`received.messageId`、`account.accountCode`。
- **`inboundProcessingId` 在 `decide()` 时点尚不存在**：`inbound_mail_processing` 行由 `confirmProcessed()`（~`:1045`）/ `confirmManualReviewWithBody()`（~`:1010`）在**之后**创建（来源：K-inbound-processing-write-paths）。
  → 因此日志表以 `expert_contact_id` + `inbound_mail_record_id` 作为关联键，**不使用 `inbound_processing_id`**。这与设计文档初稿的措辞不同，以本审计为准。
- `processSingle()` 带 `@Transactional`（`:66`），日志 INSERT 会进同一事务；I-3 的 `runCatching` 只吞异常，**不阻止事务因其他原因回滚时日志一并回滚**——这是可接受的（信没处理成功，样本也就无意义）。

### Interaction points

| # | 写入方 | 读取方 | 本计划影响 |
|---|---|---|---|
| IP-1 | `AutoMailReplyService` 写 `auto_reply_confidence_log` | 无程序读取方；人工 SQL 导出 | 新建。本轮只写不读 |
| IP-2 | `LlmProperties.shadowScoringEnabled` | `decide()` 的 kill-switch 分支 | 新建。**这是本计划唯一能造成对外发信风险的开关**，I-2 覆盖 |
| IP-3 | 01 的上下文收口 | CRS 覆盖分与证据分的输入 | 依赖。01 未落地则分数基于被污染的 `requestFacts` |

## 实现方案

### T1 · 迁移 `V104__create_auto_reply_confidence_log.sql`（I-4）

文件：`src/main/resources/db/migration/V104__create_auto_reply_confidence_log.sql`

```sql
-- Shadow-mode confidence samples for auto-reply threshold calibration.
-- Write-only in this phase: no application read path; samples are exported by SQL.
CREATE TABLE auto_reply_confidence_log (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    expert_contact_id     BIGINT       NOT NULL,
    inbound_mail_record_id BIGINT      NULL,
    sender_account_code   VARCHAR(64)  NOT NULL,
    inbound_message_id    VARCHAR(255) NULL,
    crs                   DECIMAL(5,2) NOT NULL,
    coverage_score        DECIMAL(5,2) NOT NULL,
    evidence_score        DECIMAL(5,2) NOT NULL,
    consistency_score     DECIMAL(5,2) NOT NULL,
    history_score         DECIMAL(5,2) NOT NULL,
    request_count         INT          NOT NULL,
    unsupported_count     INT          NOT NULL,
    partial_count         INT          NOT NULL,
    verified_rule_count   INT          NOT NULL,
    warning_count         INT          NOT NULL,
    draft_readiness       VARCHAR(32)  NOT NULL,
    generation_state      VARCHAR(64)  NOT NULL,
    decision_reason       VARCHAR(64)  NOT NULL,
    ready_to_send         TINYINT(1)   NOT NULL,
    tier                  VARCHAR(32)  NOT NULL,
    operator_edited       TINYINT(1)   NULL,
    operator_edit_distance INT         NULL,
    created_at            DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_arcl_contact (expert_contact_id),
    KEY idx_arcl_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**约束**：本文件不得出现 `${` 序列（K-flyway-placeholder-replacement）。`operator_edited` / `operator_edit_distance` 本轮不写，留待后续回填。

### T2 · 实体与仓库（I-4）

文件：
- `src/main/kotlin/com/weibo/talentintroduction/mail/domain/AutoReplyConfidenceLog.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/AutoReplyConfidenceLogRepository.kt`

实体按仓库既有 Spring Data JDBC 惯例（对照 `InboundMailProcessing.kt`）：不可变 `data class` + `@Table` + `@Id`，字段名 camelCase 对应下划线列名。`createdAt` 声明为 **非空** `LocalDateTime`（不给 `null` 默认值），从类型层面强制 I-4。

仓库 `interface AutoReplyConfidenceLogRepository : CrudRepository<AutoReplyConfidenceLog, Long>`，本轮不加自定义查询方法。

### T3 · `AutoReplyConfidenceScorer`（I-5）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyConfidenceScorer.kt`

无 Spring 依赖注入（纯函数式 `@Service`，不注入 repository），输入 `AiReplyDraftResult` + `verifiedRuleIds: List<Long>`，输出 `AutoReplyConfidenceScore` 数据类。

四个分量（权重为本轮初值，必须以常量形式集中定义，便于阶段 1 校准）：

| 分量 | 满分 | 计算 |
|---|---:|---|
| coverage | 40 | 每条 request 记权重：GROUNDED=1.0、PARTIAL=0.6、UNSUPPORTED=0.35、`requestFacts` 为空=0。`40 × Σw / n` |
| evidence | 25 | `25 × (有非空 factRuleIds 的 request 数 / n)`；`verifiedRuleIds` 为空时整项记 0 |
| consistency | 20 | 基线 20，每条 `contextWarnings` 扣 5（下限 0）；再乘 readiness 系数：READY=1.0、NEEDS_REVIEW=0.75、BLOCKED=0 |
| history | 15 | **本轮恒为冷启动常量 7.0**。真实历史统计留待日志攒够后单独立项 |

CRS = 四项之和，保留一位小数，钳制到 `[0, 100]`。

**禁止**：在 scorer 内调用 `QaRuleRepository`、`AiReplyDraftService` 或任何 LLM 客户端（I-5）。

### T4 · 影子模式与打分接入（I-1, I-2）

文件：
- `src/main/kotlin/com/weibo/talentintroduction/config/LlmProperties.kt`：新增 `val shadowScoringEnabled: Boolean = false`（带默认值，不打断既有具名构造）。
- `src/main/resources/application.yml`：在 `auto-reply-enabled` 同级新增 `shadow-scoring-enabled: ${LLM_SHADOW_SCORING_ENABLED:false}`。
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt`：

1. kill-switch 分支改为：

```kotlin
if (!llmProperties.autoReplyEnabled && !llmProperties.shadowScoringEnabled) {
    return disabledDecision(subject)
}
```

2. 在 `decide()` 末尾构造返回值处，**先**按既有逻辑算出 `reason` 与 `ready`，**再**做影子降级（I-2）：

```kotlin
val shadowOnly = !llmProperties.autoReplyEnabled
return GroundedAutoReplyDecision(
    readyToSend = if (shadowOnly) false else ready,
    reason = if (shadowOnly) GroundedAutoReplyReason.AI_AUTO_REPLY_DISABLED else reason,
    // …其余字段不变
    confidence = autoReplyConfidenceScorer.score(draft, verifiedRuleIds)  // 新增字段
)
```

3. `GroundedAutoReplyDecision` 新增 `val confidence: AutoReplyConfidenceScore?`（可空，`disabledDecision()` 传 `null`）。

**约束**：`resolveReason()` 与 `passesSendGate()` 两个方法体一行不改（I-1）。影子降级只在返回构造处发生，不侵入判定函数。

### T5 · 日志写入（I-3, I-4, I-6）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`

1. 构造器注入 `autoReplyConfidenceLogRepository: AutoReplyConfidenceLogRepository`。
2. `:505` 的 `decide()` 之后立即写日志：

```kotlin
decision.confidence?.let { score ->
    runCatching {
        autoReplyConfidenceLogRepository.save(
            AutoReplyConfidenceLog(
                expertContactId = contactId,
                inboundMailRecordId = inboundMailRecordId,
                senderAccountCode = account.accountCode,
                inboundMessageId = received.messageId,
                // …分量与统计字段
                tier = "SHADOW",
                readyToSend = decision.readyToSend,
                decisionReason = decision.reason,
                createdAt = LocalDateTime.now()   // I-4：显式非空
            )
        )
    }.onFailure { log.warn("Failed to persist auto-reply confidence log: {}", it.message) }
}
```

3. `AutoReplyPreviewService` **不做任何改动**（I-6）。

### T6 · 测试

文件：
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyConfidenceScorerTest.kt`（新建）
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt`（修改）

`AutoReplyConfidenceScorerTest` 覆盖：
1. 全 GROUNDED + READY + 零 warning + 证据齐全 → coverage 40.0、evidence 25.0、consistency 20.0、history 7.0、CRS 92.0。
2. 设计文档第二节的四诉求样例（GROUNDED / PARTIAL / UNSUPPORTED / PARTIAL）→ coverage 25.5。
3. `requestFacts` 为空 → coverage 0.0，CRS 不为 NaN 且不抛异常。
4. `draftReadiness = BLOCKED` → consistency 0.0。
5. `verifiedRuleIds` 为空 → evidence 0.0。
6. CRS 钳制：人为构造超界输入，断言结果落在 `[0, 100]`。

`GroundedAutoReplyDecisionServiceTest` 新增：
7. `shadow mode scores but never sends`：`autoReplyEnabled = false, shadowScoringEnabled = true`，且底层 draft 完全满足 `passesSendGate` 的全部条件 → 断言 `readyToSend == false`、`reason == AI_AUTO_REPLY_DISABLED`、且 `confidence != null`（证明确实跑了生成与打分）。
8. `both flags off skips generation entirely`：两个开关都 false → 断言 `aiReplyDraftService.generate()` **零调用**、`confidence == null`。

## 变更文件清单

| # | 文件 | 改动类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V104__create_auto_reply_confidence_log.sql` | 新建 | 建表；无 `${}` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/AutoReplyConfidenceLog.kt` | 新建 | 实体，`createdAt` 非空 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/AutoReplyConfidenceLogRepository.kt` | 新建 | `CrudRepository` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyConfidenceScorer.kt` | 新建 | 四分量计算 + `AutoReplyConfidenceScore` |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/config/LlmProperties.kt` | 修改 | +`shadowScoringEnabled`（**追加在参数列表末尾**，带默认值 `false`） |
| 6 | `src/main/resources/application.yml` | 修改 | +`shadow-scoring-enabled` |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt` | 修改 | kill-switch 分支；返回值加 `confidence`；影子降级 |
| 8 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 修改 | 注入 repository；`:505` 后 best-effort 写日志 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyConfidenceScorerTest.kt` | 新建 | 分量与钳制 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt` | 修改 | 影子模式两条 |
| 11 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` | 修改 | I-3 回归：stub `autoReplyConfidenceLogRepository.save()` 抛异常，断言 `processSingle` 仍返回正常 `SinglePipelineResult`（A2 授权） |

合计 11 个文件（上限），2 个子系统。新增 1 张表，`LlmProperties` 新增 1 个字段。

## 验证命令

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。以下命令可原样复制执行。
> 来源：项目根 `CLAUDE.md`「Commands」章节与「项目元信息」。

```bash
# 本计划相关测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=AutoReplyConfidenceScorerTest

JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=GroundedAutoReplyDecisionServiceTest

JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=AutoMailReplyServiceTest

# 单个测试方法（定位失败用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=GroundedAutoReplyDecisionServiceTest#'shadow mode scores but never sends'

# 迁移集成测试（需本地 Docker；默认被 @EnabledIfSystemProperty 跳过）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 占位符体检（预期输出为空；有输出则必须同提交关闭 placeholder-replacement）
grep -n '\${' src/main/resources/db/migration/V104__create_auto_reply_confidence_log.sql

# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`；`FlywayMigrationIntegrationTest` 在 `-DmigrationIt=true` 下必须真实执行（输出中出现该类名且 `Tests run` 非 0），不得因跳过而误判通过。

## 验收标准

- **I-1**：`git diff` 显示 `resolveReason()` 与 `passesSendGate()` 两个方法体零改动；`grep -n "crs\|confidence" GroundedAutoReplyDecisionService.kt` 的命中行均不在这两个方法内。
- **I-2**：T6 测试 7 通过——底层 draft 满足全部发送门禁条件时，影子模式仍返回 `readyToSend == false` 且 `reason == AI_AUTO_REPLY_DISABLED`，同时 `confidence != null`。
- **I-3**：`grep -n "runCatching" AutoMailReplyService.kt` 命中包含日志写入点；补一条测试：stub `autoReplyConfidenceLogRepository.save()` 抛异常，断言 `processSingle` 仍返回正常 `SinglePipelineResult`。
- **I-4**：`AutoReplyConfidenceLog.createdAt` 类型为 `LocalDateTime`（非 `LocalDateTime?`）；`FlywayMigrationIntegrationTest` 在 `-DmigrationIt=true` 下通过。
- **I-5**：`grep -n "Repository\|DraftService\|LlmClient" AutoReplyConfidenceScorer.kt` 无输出。
- **I-6**：`git diff --stat` 中 `AutoReplyPreviewService.kt` 未出现；`grep -n "ConfidenceLog" AutoReplyPreviewService.kt` 无输出。
- **T6 测试 8**：两开关全 false 时 `generate()` 零调用（`Mockito.verify(aiReplyDraftService, never()).generate(...)`）。
- **占位符**：`grep -n '\${' V104__*.sql` 无输出。
- **回归**：执行「验证命令」节的全量测试与构建命令通过；`git diff --check` 无输出。

## 人工验收清单

### A-1: 影子模式攒到样本且不发信（覆盖：需求 1/2，I-1，I-2，I-3，IP-2）

- 前置条件：
  1. 环境变量 `LLM_AUTO_REPLY_ENABLED=false`、`LLM_SHADOW_SCORING_ENABLED=true`，重启应用。
  2. 确认 `SELECT COUNT(*) FROM auto_reply_confidence_log` 为 0。
  3. 记录当前 `SELECT COUNT(*) FROM mail_record WHERE direction='OUTBOUND'` 的值，记为 N0。
  4. 准备一封会走 QA 分支的测试来信（发件人是已绑定且已发过介绍信的专家）。
- 操作步骤：
  1. 触发一次「检查回复」（或等定时任务跑）。
  2. 查 `SELECT * FROM auto_reply_confidence_log ORDER BY id DESC LIMIT 1`。
  3. 再查 `SELECT COUNT(*) FROM mail_record WHERE direction='OUTBOUND'`。
- 预期结果：
  - 日志表新增 1 行，`crs` 在 0–100 之间，四个分量之和等于 `crs`（允许 ±0.05 舍入误差）。
  - 该行 `tier = 'SHADOW'`、`ready_to_send = 0`、`decision_reason = 'AI_AUTO_REPLY_DISABLED'`、`created_at` 非 NULL 且为当前时刻。
  - **`mail_record` 的 OUTBOUND 计数仍为 N0**（一封都没发）。

### A-2: 两开关全关时不跑生成（覆盖：must-NOT-change 第 2 条，I-2）

- 前置条件：`LLM_AUTO_REPLY_ENABLED=false`、`LLM_SHADOW_SCORING_ENABLED=false`，重启应用。记录日志表当前行数 M0。
- 操作步骤：触发一次「检查回复」，处理至少一封走 QA 分支的来信。
- 预期结果：
  - 日志表行数仍为 M0（无新增）。
  - 应用日志中**没有** LLM 调用记录（不消耗配额）。
  - 来信仍按既有逻辑落到 `MANUAL_HANDOFF`，`process_reason` 为 `AI_AUTO_REPLY_DISABLED`。

### A-3: 日志写失败不阻断收信（覆盖：I-3）

- 前置条件：`LLM_SHADOW_SCORING_ENABLED=true`。手工把表改坏以制造写失败，例如：
  `ALTER TABLE auto_reply_confidence_log MODIFY sender_account_code VARCHAR(1) NOT NULL;`
- 操作步骤：
  1. 触发一次「检查回复」，处理一封 QA 分支来信。
  2. 观察应用日志与 `inbound_mail_processing` 表。
  3. 验收后执行 `ALTER TABLE auto_reply_confidence_log MODIFY sender_account_code VARCHAR(64) NOT NULL;` 复原。
- 预期结果：
  - 应用日志出现一条 `WARN` 含 `Failed to persist auto-reply confidence log`。
  - 该来信仍在 `inbound_mail_processing` 中正常落库，`process_status` 为 `MANUAL_REVIEW`。
  - 接口/任务**未抛 500**，「检查回复」正常返回。

### A-4: 分数可解释（覆盖：需求 1，I-5）

- 前置条件：A-1 已产出至少 3 行样本，其中至少 1 行来自含 UNSUPPORTED 诉求的来信。
- 操作步骤：
  1. 执行
     `SELECT id, crs, coverage_score, evidence_score, consistency_score, history_score, request_count, unsupported_count, partial_count, warning_count, draft_readiness FROM auto_reply_confidence_log ORDER BY id DESC LIMIT 5;`
  2. 对含 UNSUPPORTED 的那一行，手工按 T3 表格重算 coverage。
- 预期结果：
  - `history_score` 每行恒为 `7.00`（冷启动常量）。
  - 手算 coverage 与 `coverage_score` 一致（允许 ±0.05）。
  - `unsupported_count + partial_count <= request_count`。
  - `draft_readiness` 为 `BLOCKED` 的行，`consistency_score` 为 `0.00`。

### A-5: 回归 —— 预览未被写入污染（覆盖：must-NOT-change 第 4 条，I-6）

- 前置条件：`LLM_SHADOW_SCORING_ENABLED=true`。记录日志表行数 K0。
- 操作步骤：在某来信详情页，展开自动回复预览（02 已合并后）或点击预览 5 次（02 未执行时）。
- 预期结果：日志表行数仍为 K0，一行不增。

### A-6: 回归 —— 打开自动回复时行为不变（覆盖：must-NOT-change 第 1 条，I-1）

- 前置条件：**在隔离环境**设 `LLM_AUTO_REPLY_ENABLED=true`、`LLM_SHADOW_SCORING_ENABLED=false`。
- 操作步骤：用一封改动前会被自动回复的来信触发一次处理。
- 预期结果：
  - 该来信仍被自动回复，`mail_record` 新增一条 OUTBOUND，`decision_reason` 落库为 `QA_AUTO_REPLIED`。
  - 日志表新增一行，`ready_to_send = 1`。
  - 该行的 `crs` **不影响**是否发送——即使 `crs` 很低，信仍照发（本轮 CRS 不参与决策）。
