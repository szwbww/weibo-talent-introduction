# 子计划 04：新增候选人增量分类调度

## 需求描述

可观察结果：初始候选回填完成并由运维显式启用后，系统每天自动分类 CANDIDATE 中缺失当前版本或版本不匹配的新增文档；未分类新专家在任务完成前继续被子计划 03 安全拒绝。

必须保持不变：功能默认关闭；应用发布/启动不触发任务；不自动扫描 428 万 RAW；不并发于人工回填；单次处理有上限；MATERIAL_REMINDER 不受影响。

范围外：RAW 自动全量、跨字段 stale 比较、enrichment 后同版本强制重算、前端调度开关、分类规则升级。

前置：子计划 01～03 均完成；子计划 02 的 CANDIDATE 正式回填和人工抽样已通过。

## 关键不变量

### Invariant I4-1: 默认关闭且发布零副作用
- Rule: `talent-introduction.expert-classification.incremental-enabled` 默认 false；false 时 scheduler bean 不创建，启动不产生任务记录或 ES 写入。
- Applies to: properties、application.yml、scheduler 条件、启动测试。
- Violation consequence: 发布意外触发生产写入。
- 来源: original

### Invariant I4-2: 只处理候选 pending
- Rule: 自动任务固定 level=CANDIDATE、mode=EXECUTE、version=rnd-v1-2026、onlyPending=true；不得自动 force、不得扫描 RAW/APPLICATION。
- Applies to: scheduler 构造的 backfill request。
- Violation consequence: 每天重写全库或对 428 万 RAW 制造压力。
- 来源: original

### Invariant I4-3: 与人工回填共享互斥和语义
- Rule: 自动任务复用 `EXPERT_CLASSIFICATION_BACKFILL` taskType、同一 `ExpertClassificationBackfillService`、同一 `expertClassificationExecutor`；抢锁失败只记录 skip，不排队第二个任务。
- Applies to: scheduler、progressStore、task execution。
- Violation consequence: 手工 RAW 回填和日常候选增量并发争抢 ES。
- 来源: K-progress-log-pending-token-orphan

### Invariant I4-4: 有界增量
- Rule: batchSize 默认500、delayMs默认250、maxDocsPerRun默认50000，配置范围分别100..1000、0..5000、1..200000；达到上限以 SUCCESS/有 remaining 记录结束，次日继续，不视为失败。
- Applies to: properties validation、scheduler request、progress result。
- Violation consequence: 单次任务无限运行或错误显示失败。
- 来源: original

### Invariant I4-5: v1 对 enrichment 更新采取保守欠召回
- Rule: 同版本已分类文档不因 updatedAt/enrichedAt 变化自动重算；新增正向证据可能延后到人工 force 或新 policy version，但现有 false/null 永不被自动放行。禁止为比较两个 ES 字段引入 script query。
- Applies to: pending filter、runbook 说明、测试。
- Violation consequence: 高成本脚本扫描 428 万文档，或未经版本审查的结果漂移。
- 来源: original

## 现状审计

### Expert classification backfill task
- Schema/mapping: 子计划 02 已提供单线程 executor、互斥 taskType、onlyPending filter、限速、取消和分类 bulk 写入。
- Write paths: `ExpertClassificationBackfillService` 是唯一分类写入口。
- Read paths: pending CANDIDATE search_after 扫描。
- Interaction points: scheduler 不得复制扫描/写入逻辑，只负责构造固定请求和任务生命周期。

### 配置绑定
- Schema/mapping: `RestTemplateConfig` 的 `@EnableConfigurationProperties` 显式枚举 properties class；`application.yml` 已使用 `talent-introduction.*` + env fallback 风格。
- Write paths: 无持久化写；环境变量在启动时绑定。
- Read paths: 新 scheduler 读取 enabled/cron/batch/delay/maxDocs。
- Interaction points: 新 properties 必须加入 EnableConfigurationProperties，否则生产无法绑定。

### Scheduler 与 task executor
- Schema/mapping: 现有 scheduler 用 `tryStartWithToken` + TaskExecutionService；长任务控制器用单线程 executor 避免 HTTP/调度线程阻塞。
- Write paths: task_execution、task_progress_log、CANDIDATE classification。
- Read paths: task progress/status/cancel/history。
- Interaction points: 自动和手动共用 taskType/Executor 才能互斥；executor rejected 必须清理 pending token。

## 实现方案

### Task 1：新增显式配置（I4-1、I4-4）

修改文件：

- 新增 `src/main/kotlin/com/weibo/talentintroduction/config/ExpertClassificationProperties.kt`
- `src/main/kotlin/com/weibo/talentintroduction/config/RestTemplateConfig.kt`
- `src/main/resources/application.yml`

配置逐字定义：

```yaml
talent-introduction:
  expert-classification:
    incremental-enabled: ${EXPERT_CLASSIFICATION_INCREMENTAL_ENABLED:false}
    incremental-cron: ${EXPERT_CLASSIFICATION_INCREMENTAL_CRON:0 0 4 * * ?}
    batch-size: ${EXPERT_CLASSIFICATION_BATCH_SIZE:500}
    delay-ms: ${EXPERT_CLASSIFICATION_DELAY_MS:250}
    max-docs-per-run: ${EXPERT_CLASSIFICATION_MAX_DOCS_PER_RUN:50000}
```

Properties 构造/启动校验范围按 I4-4；加入 `@EnableConfigurationProperties`。

### Task 2：候选 pending 调度（I4-1～I4-5）

修改文件：

- 新增 `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationScheduler.kt`
- 新增 `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationSchedulerTest.kt`

要求：

- bean 加 `@ConditionalOnProperty(prefix="talent-introduction.expert-classification", name=["incremental-enabled"], havingValue="true")`。
- `@Scheduled(cron="\${talent-introduction.expert-classification.incremental-cron:0 0 4 * * ?}")`。
- 固定 request：CANDIDATE/EXECUTE/rnd-v1-2026/onlyPending=true/confirmation=`EXECUTE_CANDIDATE:rnd-v1-2026`，其余取 properties。
- 与子计划 02 controller 逐字复用 tryStartWithToken→executor→runAndRecordWithResult→bind→finally clear 模式；公共样板若需要抽取，只能在子计划 02 已列文件内完成，不得新增未列文件。
- 抢锁失败 log `incremental classification skipped: task already running` 并返回；executor rejected 清理 token并写 warn。
- 测试使用固定 properties 和同步 fake executor，断言请求所有字段、互斥跳过、token 绑定、异常终态、默认 disabled context 无 bean。

### Task 3：更新线上 runbook（I4-1～I4-5）

修改文件：

- `docs/runbooks/expert-classification-backfill.md`

在子计划 02 手册末尾增加：只有 CANDIDATE 回填/抽样/发送门禁验收通过后，才在下一次人工发布配置 `EXPERT_CLASSIFICATION_INCREMENTAL_ENABLED=true`；启用前后查询任务历史；明确自动任务不处理 RAW、同版本 enrichment 更新不重算、分类未知仍不可发送。此次修改与子计划 02 顺序执行，不并行编辑 runbook。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/config/ExpertClassificationProperties.kt` | 增量配置 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/config/RestTemplateConfig.kt` | properties 注册 |
| 3 | `src/main/resources/application.yml` | 默认关闭配置 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationScheduler.kt` | 自动候选 pending 任务 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationSchedulerTest.kt` | 调度/互斥/禁用测试 |
| 6 | `docs/runbooks/expert-classification-backfill.md` | 增量启用章节 |

共 6 个文件、2 个子系统（配置、专家维护任务）、不新增 store 字段。

## 验收标准

- I4-1: 默认配置 context 中 scheduler bean 不存在；启动 2 分钟无 task_execution/ES 写。
- I4-2: scheduler test 捕获 request，逐字段断言仅 CANDIDATE + onlyPending=true，无 RAW/APPLICATION/force。
- I4-3: 人工任务占锁时 schedule 调用不提交 executor；空闲时 token 绑定、execution 历史和 progress 正常。
- I4-4: 边界配置测试覆盖最小/最大/越界；maxDocs 达到后结果记录 remaining/pending 而非 failure。
- I4-5: 已有同版本分类 fixture 即使 enrichedAt 更新仍不在 pending；缺失/旧版本仍在 pending；grep 无 script query。
- 回归：`mvn test` 全绿；子计划 02 管理 API 行为不变。

## 人工验收清单

### A4-1: 默认发布不自动执行
- 前置条件: 测试环境有 10 条未分类 CANDIDATE，未设置增量 enabled env。
- 操作步骤: 1. 发布重启；2. 等待超过 cron 时间或将测试 cron 临时设为每分钟但 enabled 保持 false；3. 查任务历史和 ES。
- 预期结果: 无新任务；10 条仍未分类。
- 覆盖: I4-1、必须保持不变第 1 条

### A4-2: 显式启用后仅处理候选
- 前置条件: CANDIDATE 与 RAW 各有 10 条未分类文档；设置 enabled=true、maxDocs=5、测试 cron 每分钟。
- 操作步骤: 1. 重启；2. 等待一次任务完成；3. 查询两层。
- 预期结果: CANDIDATE 恰有 5 条新增 v1；RAW 仍为 0；任务 SUCCESS 且 remaining>0。
- 覆盖: I4-2、I4-4

### A4-3: 次轮续跑
- 前置条件: 承接 A4-2。
- 操作步骤: 等待第二次 cron 完成，再查 CANDIDATE。
- 预期结果: 10 条全部 v1；第一轮已分类的 5 条 classifiedAt 不变。
- 覆盖: I4-2、I4-4

### A4-4: 与人工 RAW 回填互斥
- 前置条件: 人工 RAW backfill 正在运行，占用 EXPERT_CLASSIFICATION_BACKFILL。
- 操作步骤: 等待增量 cron 触发；查询 executor、任务日志和 RAW executionId。
- 预期结果: 增量记录 skip warn，不创建第二 execution，不改变人工任务 executionId/进度。
- 覆盖: I4-3

### A4-5: 同版本 enrichment 保守不重算
- 前置条件: 一名 UNKNOWN v1 候选，随后更新 recentWorkTitles/enrichedAt。
- 操作步骤: 等待增量任务；读取该分类。
- 预期结果: type、classifiedAt、version 不变，仍 sendable=false；runbook 明确需人工 force 或新版本升级。
- 覆盖: I4-5

人工验收开始时导出 `04-expert-rnd-incremental-classification-acceptance.md`。
