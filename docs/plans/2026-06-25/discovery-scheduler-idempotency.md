# 深度发现：调度当日幂等 + 定时线程泄漏加固

> 计划日期：2026-06-25
> 目标：消除"同一天被触发多次"的异常调度（线上 4722 14:15 / 6-24 双跑）。

## 需求描述

**可观测产出**
1. `SCHEDULED` 触发的深度发现每个自然日至多启动一次；重复触发被拦截并记日志。
2. 应用定时任务由可优雅停机的受管 scheduler 执行，Tomcat 热部署/重启时旧线程随上下文关闭，不再泄漏（`pool-2-thread-1` 残留）。

**不可改变**
- 正常 02:00 cron 行为；`MANUAL`（页面/接口 `/run`、`/run/by-keyword`）触发不受当日限制。
- `tryStartWithToken` 的"并发互斥"语义保留（本计划是叠加"当日幂等"，不替换并发锁）。
- 其它 `@Scheduled`（邮件自动回复、初始外联、日计数重置、退信收集、批量发送）行为不变。

**不在本次范围**
- 批次号乱序、失败原因可观测性、抓取可靠性/效率（各有独立计划）。
- 分布式多实例锁（当前单实例部署；多实例下需 DB/Redis 锁，另做）。

---

## 关键不变量

### Invariant I-1: SCHEDULED 深度发现当日至多一次
- 规则：`ExpertDiscoveryScheduler.scheduleDiscovery` 启动前，若当日（`startedAt >= 当天00:00`）已存在 `taskType=EXPERT_DISCOVERY` 且 `triggerType=SCHEDULED` 且 `status ∈ {RUNNING, SUCCESS, PARTIAL_SUCCESS}` 的 `task_execution`，则跳过本次（记 info 日志），不启动 progress、不建 execution。
- 适用写路径：仅 `ExpertDiscoveryScheduler.scheduleDiscovery`。**不**作用于 `ExpertDiscoveryController` 的 MANUAL 入口。
- 违反后果：旧上下文泄漏线程或多次 cron 触发会重复跑全量发现，浪费配额、污染批次记录。

### Invariant I-2: 幂等判定基于持久化记录，而非内存
- 规则：当日判定必须查 `task_execution` 表（跨上下文可见），不能依赖 `TaskProgressStore` 内存态（泄漏线程在另一个 ApplicationContext，内存互不可见）。
- 适用写路径：`scheduleDiscovery` 的前置检查。
- 违反后果：泄漏在旧上下文的 scheduler 与新上下文内存不共享，内存判定无效。

### Invariant I-3: 定时执行器受 Spring 生命周期管理且优雅停机
- 规则：提供显式 `taskScheduler` Bean（`ThreadPoolTaskScheduler`，命名线程、`waitForTasksToCompleteOnShutdown=true`、`awaitTerminationSeconds>0`），供全应用 `@Scheduled` 使用；上下文关闭时线程池必须 shutdown。
- 适用写路径：新增调度配置。
- 违反后果：WAR 重新部署时默认调度线程可能不随旧上下文关闭，形成 `pool-2-thread-1` 泄漏并继续触发 cron。

---

## 现状审计

### 调度入口：`ExpertDiscoveryScheduler.kt`
- `@Scheduled(cron = "\${...expert-discovery.cron:-}")`（:20），yml 默认 `EXPERT_DISCOVERY_CRON:0 0 2 * * ?`（application.yml:92）。
- `scheduleDiscovery`（:21-57）：仅 `progressStore.tryStartWithToken("EXPERT_DISCOVERY", ...)`（:22）防"当前正在跑"，**无当日幂等**。随后 `taskExecutionService.runAndRecord("EXPERT_DISCOVERY","SCHEDULED",...)`（:35）。

### task_execution 读路径
- `TaskExecutionRepository`（repository）：有 `findAllByTaskTypeAndStatusOrderByStartedAtDesc`、`findRecentByTaskType(@Query ... LIMIT)`，**无按 startedAt 时间过滤的查询** → 需新增。
- `TaskExecution`（domain）：含 `taskType, triggerType, status, startedAt`。
- 写入：`TaskExecutionService.runAndRecord`（service:128-199）建 RUNNING 行（:136），结束置 SUCCESS/PARTIAL_SUCCESS/FAILED。

### 调度线程模型
- `@EnableScheduling` 在 `TalentIntroductionApplication`（:9），**未定义** `taskScheduler` Bean → Spring 用默认单线程调度执行器（线程名 `pool-N-thread-M`，与告警 `pool-2-thread-1` 吻合）。
- grep 全仓：发现路径无 `Executors.newXxx` / `new Thread(` 裸线程；`ManualOutreachConfig` 有自管 `ThreadPoolTaskExecutor`（@Async 用，非调度）；`BatchSendScheduler` 自建 `ThreadPoolTaskScheduler` 仅供其动态 cron 用。→ **泄漏源就是默认调度执行器**，需用受管 Bean 替换。
- 多个 `@Scheduled`（`MailAutomationScheduler` 3 个、`ExpertDiscoveryScheduler` 1 个）共享该默认单线程执行器。

### 交互点
- IP-1：`scheduleDiscovery`（写当日幂等判定）↔ `TaskExecutionRepository`（新增时间过滤查询）↔ `task_execution`（MANUAL 写入也进同表，但幂等仅按 `triggerType=SCHEDULED` 过滤，不误伤手动）。
- IP-2：新增 `taskScheduler` Bean 影响**所有** `@Scheduled`（含邮件类）。需设池大小 ≥ 并发 cron 数，避免把现默认单线程行为改成串行阻塞或反之造成意外并发。

---

## 实现方案

### 阶段 1：当日幂等（I-1、I-2）

**Task 1.1** `TaskExecutionRepository.kt` 新增查询：
```kotlin
@Query("""SELECT COUNT(*) FROM task_execution
         WHERE task_type = :taskType AND trigger_type = :triggerType
           AND status IN ('RUNNING','SUCCESS','PARTIAL_SUCCESS')
           AND started_at >= :since""")
fun countActiveSince(taskType: String, triggerType: String, since: java.time.LocalDateTime): Long
```

**Task 1.2** `ExpertDiscoveryScheduler.kt`：在 `tryStartWithToken` 之前加当日判定：
```kotlin
val todayStart = java.time.LocalDate.now().atStartOfDay()
if (taskExecutionService.countScheduledSince("EXPERT_DISCOVERY", todayStart) > 0) {
    log.info("今日已存在 SCHEDULED 深度发现执行，跳过本次触发 (since={})", todayStart)
    return
}
```
经由 `TaskExecutionService` 暴露一个薄方法 `countScheduledSince(taskType, since)` 包装 repository（避免 scheduler 直依赖 repository，与现有 scheduler→service 依赖风格一致）。

> 注意：先判幂等、再 `tryStartWithToken`。两者叠加：幂等防"今日已跑"，token 防"此刻正在跑"。

### 阶段 2：受管调度执行器（I-3）

**Task 2.1** 新建 `config/SchedulingConfig.kt`，定义受管 `taskScheduler`：
```kotlin
@Configuration
class SchedulingConfig {
    @Bean
    fun taskScheduler(): ThreadPoolTaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 4                 // ≥ 并发 @Scheduled 数（当前 4 个 cron）
            setThreadNamePrefix("app-sched-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(30)
            initialize()
        }
}
```
Spring 检测到名为 `taskScheduler` 的 Bean 后，`@EnableScheduling` 会用它替代默认执行器；命名前缀让后续若再泄漏可被准确定位（不再是匿名 `pool-N`）。

> 影响评估：邮件类 `@Scheduled` 之前共享默认单线程（串行）；改为 `poolSize=4` 后它们可并发触发。各任务内部已各自有互斥/幂等（如本计划的发现、`tryStart*`、批量发送的 `autoEnabled` 判定），并发触发安全。在验收里回归确认。

---

## 变更文件清单

| # | 文件 | 改动 | 不变量 |
|---|------|------|--------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt` | 加 `countActiveSince` 查询 | I-1/I-2 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionService.kt` | 加 `countScheduledSince` 包装方法 | I-1 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt` | 启动前当日幂等判定 | I-1/I-2 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/config/SchedulingConfig.kt` | 新增受管 taskScheduler | I-3 |

文件数 4（≤10 ✅）；子系统 2（幂等 / 调度执行器，≤2 ✅）；新增共享存储字段 0。

---

## 验收标准

- **I-1**：单测/集成——预置一条当日 `SCHEDULED EXPERT_DISCOVERY` SUCCESS 记录，调用 `scheduleDiscovery()`，断言未新建 execution、未启动 progress、日志含"跳过"。
- **I-1 不误伤**：当日已有 SCHEDULED 记录时，调用 Controller `/run`（MANUAL）仍能正常启动（不被幂等拦截）。
- **I-2**：断言 `countScheduledSince` 走 DB 查询（`triggerType='SCHEDULED'`、`started_at>=当天0点`、状态集合正确）；FAILED 当日记录不阻止重试（状态集合不含 FAILED）。
- **I-3**：启动应用断言存在名为 `taskScheduler` 的 `ThreadPoolTaskScheduler` Bean 且被调度采用（线程名前缀 `app-sched-`）；关闭上下文断言线程池 shutdown。
- **回归**：邮件类 `@Scheduled` 在 `poolSize=4` 下仍按各自 cron 正常触发；`mvn clean package` + `mvn test` 通过（JDK 11）。

## 自检清单
- [x] 每个新行为有不变量（幂等 I-1/I-2、执行器 I-3）
- [x] 现状审计 grep 确认泄漏源 + 列全 task_execution 读写路径
- [x] 文件数 4 ≤ 10；子系统 2 ≤ 2；新增共享字段 0
- [x] 任务标注不变量编号
- [x] 验收每条不变量有检查（含"不误伤 MANUAL"）
- [x] 文件逐一具名
- [x] 明确推迟分布式锁/其它问题域
- [x] 保存至 docs/plans/2026-06-25/
