# 子计划 01：历史邮箱结论批量读取与保留一致性

状态：DRAFT，仅创建开发计划，未实施、未运行测试、未部署。
目标工作区：`/Users/lukai/IdeaProjects/weibo-talent-introduction`，审计分支 `main`，HEAD `d6f54c25b228ee2e9e0317d053957ae3f56984b5`。当前已有其他未提交改动，见证据 E-00；执行前重查，禁止覆盖。
证据：[源码快照](batch-email-reliability-evidence.md)。E-n 均包含读取命令/原始输出或带原文件行号的摘录；下文“拟改”是设计决策，不是现状事实。

## 需求描述

为后续过滤提供无副作用的批量查询：按当前邮箱取得一年内最新有效原始验证结果。修正清理规则，避免较新放行结论被先删除后旧拒绝结论重新生效。

必须保持：单邮箱验证复用行为、原检查时间与一年边界；ERROR 不缓存；验证审计字段、HTTP 行为、原有 249 两次/500ms 策略；执行清理原有分批与索引条件。

范围外：本子计划不开放过滤开关、不改变发送循环、不新增表/索引/缓存/ES 字段、不回填历史数据。可独立部署；后续子计划依赖本 helper。

## 关键不变量

### Invariant I-1：同一历史有效性判据
- Rule：原始行 `request_count > 0 AND reused_from_id IS NULL AND error_code IS NULL`；时间 `checked_at > now.minusYears(1) AND checked_at <= now`；状态组合逐字沿用 E-04：PASS + deliverable/risky/unknown，或 SKIP + undeliverable/risky/unknown。
- Applies to：现有 findReusable、新批量查询、TaskExecutionRepository.deleteOlderThan 的历史保护子查询。
- Violation consequence：复用行续期、过期排除，或较新可用结论被删后错误排除。
- 来源：original；实际 SQL 见 E-04/E-12。保留 SQL 当前漏 PASS risky/unknown，属于明确的跨读写不一致。

### Invariant I-2：先选最新，再判断不可投递
- Rule：每个规范化邮箱按 `checked_at DESC, id DESC` 选择一条有效原始记录；仅其 provider_state 为 undeliverable 才排除。禁止先 WHERE undeliverable 再取最新；旧策略 SKIP risky/unknown 仍不排除。
- Applies to：批量查询、服务 helper；较新 ERROR 不覆盖仍有效原始结论。
- Violation consequence：恢复可投递邮箱被永久排除。
- 来源：original；E-05 verify 复用 providerState 而不是旧 decision。

### Invariant I-3：批量只读、规范化一致
- Rule：复用 normalizeVerificationEmail（trim + lowercase(Locale.ROOT)，不去 +tag、不合并点号）；去空去重；每 SQL 批最多 500 个邮箱，空批零 SQL；全部时间用当前服务北京时区。helper 接受固定 now，预估/执行一次调用链固定时间边界；缺省也必须使用既有 verificationNow。
- Applies to：新服务 helper 与仓储读方法。
- Violation consequence：ES 原邮箱大小写/空格导致漏排，或预估产生付费/写入副作用。
- 来源：original，E-05:382/566。

## 现状审计

### batch_email_verification
- Schema：V138，email VARCHAR(320) utf8mb4_bin；decision/send_status/tag_status 分开；唯一键 (task_execution_id, orcid_id, email)；FK 到 task_execution，ON DELETE CASCADE。V140 增 reused_from_id 和 (email,checked_at,id) 索引；见 E-15。
- 业务写路径：BatchEmailVerificationService.verify → insertPending / recordDecision / recordReusedDecision；conclude → recordTag / recordSend；markSending / recordSend 用于发送收尾。SQL 都在 BatchEmailVerificationRepository；读写调用全文搜索 E-01/E-02、方法 E-04/E-05。
- 其他删除路径：TaskAuditRetentionService → TaskExecutionRepository.deleteOlderThan → FK 级联；无新增删除入口。E-12/E-13。
- 读路径：验证服务 findReusable；控制台 controller.readPage（listAfter + aggregate）；清理保护 NOT EXISTS 子查询。controller 直接映射结果，新增只读 helper 无需改 controller。E-14。
- 交互 IP-1：实时验证写原始结果 → 批量查询按最新有效结论排除；IP-2：清理 task_execution → 外键删除记录 → 历史判断可能改变。

### task_execution（仅保留条件）
- V4 request_payload/result_summary 为 TEXT；V137 是 owner/heartbeat/interruption 列，本文不改 schema。
- 写入与终态：TaskExecutionService.runAndRecord / runAndRecordWithResult 保存请求；finishOwned 写终态；heartbeat/interruption/progress 的方法索引 E-13，均不改。
- 本次改动只涉及 deleteOlderThan 内保护有效验证结果的状态组合，仍按 started_at/cutoff、ORDER BY started_at LIMIT batchSize 删除。E-12 当前仅保护 PASS deliverable，遗漏目前能被 findReusable 采用的 PASS risky/unknown。
- 这是源码可证的潜在清理缺陷；未声称生产已发生误删，禁止据此做生产回填。

## 实现方案

### T1：统一单个与批量的最新有效查询（I-1/I-2）
文件：BatchEmailVerificationRepository.kt。

新增 `findReusableByEmails(emails: List<String>, now: LocalDateTime): List<BatchEmailVerificationRow>`；每批最多500个；空列表直接返回空列表、不发送SQL。用 JdbcTemplate 参数化 IN 占位符 + NOT EXISTS newer 行比较，复用现有 ROW_MAPPER，无新依赖。SQL 轮廓：

```sql
SELECT v.* FROM batch_email_verification v
WHERE v.email IN (/* bound placeholders */)
  AND /* v 满足 I-1，绑定 cutoff/now */
  AND NOT EXISTS (
    SELECT 1 FROM batch_email_verification n
    WHERE n.email = v.email
      AND /* n 满足同一 I-1，绑定相同 cutoff/now */
      AND (n.checked_at > v.checked_at
        OR (n.checked_at = v.checked_at AND n.id > v.id))
  )
```

此处注释条件必须展开为 I-1 的完整谓词；不得仅筛 undeliverable。单邮箱 findReusable 委托该批量读方法，消除两个实现漂移。禁止循环每邮箱 SELECT，禁止把整个历史表搬入 JVM。查询只接收规范化邮箱，用户输入不得拼接 SQL。

### T2：暴露只读过滤依据（I-2/I-3）
文件：BatchEmailVerificationService.kt。

新增 `findKnownUndeliverableEmails(emails: Collection<String?>, now: LocalDateTime = verificationNow()): Set<String>`，按 I-3 分块调用 T1，最终只返回最新 providerState=undeliverable 的规范化邮箱集合。不经过 verify/beginExecution/requireConfiguredApiKey；不插 PENDING、不打标签、不调 HTTP。数据库失败向上报告，不把失败当空集合静默放行。

### T3：保留条件与读条件对齐（I-1）
文件：TaskExecutionRepository.kt。

只将保护条件中的 PASS 状态扩为 `IN ('deliverable', 'risky', 'unknown')`；SKIP、时区、原始行条件、时间边界、排序和分批不变。不增加保留时长，不修改清理调度。

### T4：回归（I-1/I-2/I-3）
文件：本清单的三个测试文件。真实 MySQL 测试在现有 BatchEmailVerificationRepositoryIT 中注入 TaskExecutionRepository，调用其真实 deleteOlderThan；不另抄 SQL 伪装验证。按北京 now 构造 110 天前 undeliverable、100 天前 PASS risky/unknown 的不同 execution，清理 cutoff=90 天；两条有效原始记录均保留，最新放行仍胜出。数据库时间边界测试留足时间差，精确一年边界用传入固定 now 测查询方法。

## 变更文件清单

| 文件 | 改动 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt` | 批量取每邮箱最新有效原始记录；单邮箱查询复用同一判据 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt` | 只读规范化/分块查询 helper |
| `src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt` | 保留策略纳入 PASS risky/unknown |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt` | 真实 MySQL 最新结果、边界、清理后查询测试 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt` | 规范化、500 分块、零 HTTP/零写测试 |
| `src/test/kotlin/com/weibo/talentintroduction/task/service/TaskRetentionMigrationTest.kt` | 保留判据回归 |

共 6 个文件，campaign 查询与 task 清理两个子系统。其他文件只读。

## 验收标准

- I-1：SQL 查询/保留均覆盖 PASS risky/unknown；ERROR、reused 行不能延长有效期；一年整边界排除、未来日期排除。执行真实清理后查询仍得到较新放行结论；到期记录可正常清理。
- I-2：同邮箱旧坏→新好、新好→新坏、相同 checked_at 以 id 决胜、旧 SKIP risky/unknown、较新 ERROR 不覆盖有效原结果；不同邮箱互不串扰。
- I-3：空集合无交互；501 个不同规范化邮箱触发 500+1 两次仓储调用；大小写/空格折叠；+tag 保留；HTTP client 与所有 repository 写方法零交互；原 findReusable 测试继续通过。
- 命令（目标工作区）：
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchEmailVerificationServiceTest,TaskRetentionMigrationTest test`
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmysqlIt=true -Dtest=BatchEmailVerificationRepositoryIT test`
  - `git diff --check`
- MySQL IT 需 Docker/MySQL 8.0.36（E-21）；未启用 mysqlIt 的跳过结果不算通过。当前仅计划，未运行这些命令。

## 人工验收清单

### A-1：最新结果覆盖
- 前置条件：独立测试库，通过 IT fixture 准备同邮箱旧 undeliverable、新 deliverable，以及另一邮箱旧 SKIP risky、第三邮箱一年以前的 undeliverable、第四邮箱仅有复用行；使用本地测试 runner 调用 helper，禁止真实发送。
- 操作步骤：1. 查询两邮箱。2. 为第一个邮箱追加更新的 undeliverable 原始行。3. 再查询。
- 预期结果：第一次排除集合为空（过期行和孤立复用行不生效）；第二次仅包含第一个邮箱；HTTP 请求数为 0，验证表行数不因查询增加。
- 覆盖：I-1/I-2/I-3，IP-1；原验证和付费边界不变。

### A-2：清理不复活旧坏结果
- 前置条件：独立测试库准备 T4 的 110/100 天数据，固定两个 execution id；另一过期 execution 不含有效验证。
- 操作步骤：1. 调用既有清理 repository，cutoff 为 90 天前。2. 按 execution id 查行，再调用 helper。
- 预期结果：含有效记录的两个 execution 保留；过期且无有效记录的 execution 删除；该邮箱不在排除集合。
- 覆盖：I-1/I-2、IP-2；清理分批语义不变。
