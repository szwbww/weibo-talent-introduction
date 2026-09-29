# 03：深度发现按整数小时动态调度

状态：待审阅，未实施。9个实施文件，2个子系统（设置持久化/API、发现调度）；新表仅1个业务字段 interval_hours，id/updated_at 为标识与生效锚点。

## 需求描述

页面可保存每隔 N 小时执行一次，第一版整数1～168小时。保存后无需重启即调整未来触发。API先独立交付，页面由04接入。

保持：未保存时既有 cron；手动启动/取消、统一运行互斥、查询条件/检查点/限额；连续流水线30秒恢复 tick和暂停状态；管理员 enabled=false/cron=- 的原有禁用语义。范围外：cron编辑器、分钟/日历调度、开关/删除设置、设置多份发现查询、启用连续模式、跨实例调度平台、历史数据处理。

## 关键不变量

### Invariant I-1：一个设置、一条事实来源
- Rule：单例表 id=1，interval_hours 为1～168整数。无行代表“沿用部署 cron”，不是停用也不是已保存2小时。updated_at 作为该小时周期的锚点，使用UTC精度毫秒；首次保存/值变化更新锚点，相同值重存不重置。GET不建行。
- Applies to：migration、repository、service.save/get、scheduler启动读取。
- Violation consequence：重启/重复保存改变节奏，或把默认 cron 擅自迁移成不同语义。
- 来源：原始需求；K-batch-send-setting-kv（独立领域设置不混入发信KV）。

### Invariant I-2：按持续小时计算，不按日内 cron 取模
- Rule：锚点 T、间隔 N 的候选点为 T+k×N小时（k≥1）。只选择严格晚于当前时间及上次完成时间的点；不立即执行、不补排漏掉的点、不因跨午夜改周期。任务运行中跳过，不排队追加一次。
- Applies to：小时 Trigger、重启、重排、长任务完成后的后续触发。
- Violation consequence：每5小时被错误写成 cron */5，跨午夜出现4小时间隔或发生补跑。
- 来源：原始需求及现有“跳过不补排”规则。

### Invariant I-3：已保存与已应用必须一致才报成功
- Rule：DB提交后同步重排；重排成功才返回200和applied=true。重排失败返回503，明确“已保存但定时应用失败，请重试保存”；DB失败不取消原调度。重存相同值可重新应用。新旧同步定时最多一个有效代次，cancel(false)不打断运行任务。
- Applies to：service.save、scheduler.reload、GET/PUT response。
- Violation consequence：页面假成功、重复启动、保存失败却停掉原计划。
- 来源：K-batch-send-scheduler-reschedule-on-enable。

### Invariant I-4：小时设置只控制当前同步定时
- Rule：enabled=true、cron非“-”、pipelineEnabled=false才允许保存。其他模式GET返回editable=false，PUT409且不写库。连续模式保持现有 cron tick + pipelineTick，不应用小时设置；暂停不会被保存或页面读取解除。同步模式移除原@Scheduled注册，统一交给一个动态future。
- Applies to：controller/service、scheduler.configureTasks、启动注册。
- Violation consequence：两个启动器同时运行、页面看似限速却被30秒tick绕过，或恢复已暂停流水线。
- 来源：ExpertDiscoveryScheduler现有两模式代码。

## 现状审计

### 当前配置与调度
- ExpertDiscoveryProperties：enabled、cron、pipelineEnabled、pipelineTick；application.yml 默认 cron=`0 0 */2 * * ?`，pipelineEnabled=false。生产只读核对未覆盖此三项，当前同步模式。
- 写：部署环境/YAML提供不可变配置；TaskProgressStore.tryStartWithToken占用统一运行槽；TaskExecutionService.runAndRecord以SCHEDULED记录发现；DiscoveryPipelineService在连续模式维护持久状态。
- 读：ExpertDiscoveryScheduler的@Scheduled读取cron、configureTasks读取pipelineEnabled/tick、scheduleDiscovery分派同步或pipeline.tick；Controller人工入口使用同一运行槽/流水线协调者。
- 当前同步条件固定：excludeCountries=[CN]、openAccessOnly=true、subjectScope=RND_TARGET、includeRawScan沿用配置；不读取页面临时关键词。小时配置只改触发频率，不能暗中把手动参数变成定时参数。
- 交互 P-1：定时回调→统一运行槽→SCHEDULED任务记录；手动入口与取消仍由原有任务模型控制。
- 现有任务表/流水线表仅通过上述原服务读写，新增设置不直接操作任务状态；完整检索 [schedule-paths.txt](discovery-repair-evidence/schedule-paths.txt)、[store-paths.txt](discovery-repair-evidence/store-paths.txt)。

### 新 discovery_schedule_setting 表
- 现状无表，无迁移、回填或其他读写路径。V144暂未占用；已有其他任务V143，实施前必须重查。
- 新schema：id TINYINT NOT NULL PRIMARY KEY（固定1）；interval_hours SMALLINT NOT NULL；updated_at DATETIME(3) NOT NULL（应用以UTC写读）。不设自动ON UPDATE，避免同值保存重置锚点；无初始化行、无外键、无JSON设置包。
- 新全部写：DiscoveryScheduleSettingRepository仅供service.save插入/更新id=1；迁移只建表。SQL与service双重范围保护，不能依赖MySQL5.7忽略的CHECK约束。
- 新全部读：service GET/PUT回读、scheduler启动/重排读取。不存在ES同步、回填、晋升、批量写入或发信设置复用。
- 交互 P-2：PUT→DB提交→调度读取；P-3：进程重启→DB原锚点→首个未来候选点。不能保存配置后仍读取旧构造cron。

### 内存调度句柄
- 复用SchedulingConfig.taskScheduler（ThreadPoolTaskScheduler，4线程），不创建新线程池。BatchSendScheduler已有future/cancel(false)/CronTrigger先例，本计划不修改发信调度。
- 新全部写：启动初始化、PUT重排、销毁cancel(false)；本地递增代次使已取消但已排出的回调失效。
- 新全部读：回调启动前校验代次；GET读取当前应用快照；nextTriggerAt仅是下一候选点，不承诺必定启动（可能互斥/限额跳过）。
- 交互 P-4：并发保存/旧回调竞争；小锁仅覆盖代次检查和运行槽占用，不能包住长时discover导致保存等待数小时。

## 实现方案

### T-1：单例设置与严格校验（I-1）

新增V144 migration、DiscoveryScheduleSettingRepository.kt、DiscoveryScheduleSettingService.kt。

repository复用JdbcTemplate，文件内小data class；只暴露find和按id=1保存。service对保存串行化，使用TransactionTemplate完成DB事务后再调用scheduler重新应用，避免@Transactional方法内部尚未提交便重排。first-save与不同值保存使用一次Clock读数；同值保留updated_at。现有Spring默认单服务实例范围内串行保存即可，不引入分布式锁。

JSON严格只接受整数数值，拒绝缺失/null/字符串/小数/布尔/越界；不能让Jackson将1.5截成1。用controller本地JsonNode校验即可，不改变全局ObjectMapper。

### T-2：单一动态注册（I-2、I-3、I-4）

修改ExpertDiscoveryScheduler.kt：去掉原@Scheduled；ApplicationReadyEvent时读取设置并注册一条future。通过@Qualifier("taskScheduler")使用既有池；pipeline模式仍注册原cron并保留configureTasks的fixedDelay，cron=-仍仅停cron、不影响原pipeline恢复tick。同步模式无设置用CronTrigger(cron, Asia/Shanghai)，有设置用小时Trigger。

小时Trigger以Instant做Duration运算，计算：base=max(clock.now,lastCompletion)；next=T+(floor((base-T)/N小时)+1)×N小时，且k至少1。同步执行结束后再算下一未来槽，绝不使用会追补过期周期的scheduleAtFixedRate。

保留scheduleDiscovery入口，抽取必要的“短时占槽/长时执行”步骤供动态回调复用：同一短锁内检查当前代次并占用原TaskProgressStore运行槽；释放锁再执行discover。保存时增加代次、cancel(false)旧future、注册新future；旧回调晚到不能取得新任务。已经取得槽的任务算运行中，继续完成。重排异常记录applied=false，不谎报旧调度仍有效。

新service经ObjectProvider<ExpertDiscoveryScheduler>读取/应用快照；scheduler只依赖repository，不依赖service，避免循环。GET根据DB版本与已应用快照判定applied；DB不可读返回503，不静默当“无行”回退cron。应用启动读取失败时不启动同步发现并显式告警，重试保存可恢复；不得默默继续按旧默认频率执行。

保存采用直接同步调用，无需新增事件总线、轮询表或专用执行器。Spring [TaskScheduler](https://docs.spring.io/spring-framework/docs/5.3.x/javadoc-api/org/springframework/scheduling/TaskScheduler.html) 已支持Trigger注册。

### T-3：GET/PUT契约（I-1～I-4）

新增DiscoveryScheduleController.kt，复用现有 `/api/expert-discovery` 身份认证与错误响应约定，不新增授权角色。

- GET `/api/expert-discovery/schedule`，无副作用。
- PUT 同路径，body=`{"intervalHours":3}`，成功返回相同响应形状。

字段固定：`mode`（LEGACY/CONTINUOUS）、`editable`、`source`（CONFIG/OVERRIDE）、`intervalHours`（已保存值；未保存默认已知cron显示2，其他cron为null）、`anchorAt`（无行null）、`nextTriggerAt`（当前已应用同步计划的下一候选ISO-8601时间，无法计算/不应用则null）、`applied`、`reason`（不可编辑/应用失败的原因码）。所有时间含偏移或Z，前端显示北京时间。

默认cron识别只对准确的 `0 0 */2 * * ?` 返回2，不写一个通用cron→小时推导器；source=CONFIG必须显示“当前沿用系统定时”。服务未启动/禁用/连续模式 nextTriggerAt=null，reason分别明确；连续模式下不返回一个仿佛已应用的小时周期。

HTTP：400非法输入，409模式不允许，503DB失败或调度应用失败（区分saved=false/true；后者返回已保存值并提示重试）。保存不同值后下一候选=保存时间+N小时；保存相同值下一候选沿原锚点。GET只读，不通过访问来启动或恢复任务。

### T-4：固定时钟与持久化验证（I-1～I-4）

修改scheduler测试，新增service/controller/repository测试。用可控Clock与捕获TaskScheduler的fake future验证真实Trigger；不用sleep等待小时。repo测试使用现有MySQL集成测试方式，实际执行migration/SQL，覆盖首写、同值不改时间、变化更新与事务失败。

测试同值重存恢复应用、两次相反顺序保存、DB失败不取消、注册失败503、旧回调不能启动、运行中保存及时返回、重启不补跑、手动/定时互斥、连续暂停保持、cron=-与enabled=false。DB断连与坏存量值均不得被解释为“无行”。不修改现有数据库配置。

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryScheduleSettingRepository.kt | 新表SQL与小模型 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryScheduleSettingService.kt | 事务提交后同步应用、DTO |
| 3 | src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryScheduleController.kt | GET/PUT、校验与错误 |
| 4 | src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt | 单一动态触发与生命周期 |
| 5 | src/main/resources/db/migration/V144__create_discovery_schedule_setting.sql | 仅建表；冲突时顺延版本 |
| 6 | src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt | 周期、互斥、模式回归 |
| 7 | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryScheduleSettingServiceTest.kt | 提交/应用/失败 |
| 8 | src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryScheduleControllerTest.kt | 严格输入、响应 |
| 9 | src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryScheduleSettingRepositoryIT.kt | 实际MySQL持久化 |

## 验收标准

- I-1：GET不插入；空表沿用cron；PUT3持久化唯一行；PUT3第二次updated_at不变；PUT5才变锚点；所有非法JSON均400且写入0。
- I-2：固定时钟2026-09-29T10:00:00+08:00保存3，候选13/16/19点；保存5跨午夜连续5小时；停止7小时再启动只注册下一未来点；保存即刻discover调用0；运行跨过多个槽不追补。
- I-3：DB失败旧future未取消；应用失败503/applied=false；同值重存可恢复；旧代次回调启动0；已运行任务不中断；并发两次保存最终DB值与有效future一致。
- I-4：同步模式仅一个注册源；enabled=false/cron=-/continuous的PUT409且DB不变；连续tick/PAUSED原行为不变；手动启动与定时互斥；原查询条件、限额、检查点不变。
- Java11定向：`mvn -DmigrationIt=true -Dtest=ExpertDiscoverySchedulerTest,DiscoveryScheduleSettingServiceTest,DiscoveryScheduleControllerTest,DiscoveryScheduleSettingRepositoryIT test`，IT需明确被执行且连接隔离MySQL。不可把跳过IT标为通过；不在生产真实跑发现。

## 人工验收清单

### A-1：保存、同值与重启
- 前置条件：隔离服务空设置表，默认cron；固定测试时钟2026-09-29 10:00北京时间。
- 操作步骤：1. GET schedule；2. PUT3；3. 在11:00再PUT3；4. 模拟停机至17:00并重启后GET。
- 预期结果：首次CONFIG/2且表0行；首存OVERRIDE/3、anchor10:00、next13:00；同值anchor仍10:00、next仍13:00；重启next19:00，不补跑13/16点；保存动作启动数0。
- 覆盖：I-1、I-2；P-2、P-3；需求结果。

### A-2：运行中修改与失败
- 前置条件：隔离测试fake发现保持运行；测试可切换DB保存失败、调度器注册失败并查看调用报告。
- 操作步骤：1. 运行中PUT5；2. 检查现有任务；3. 模拟DB失败再PUT3；4. 恢复DB但令调度注册失败再PUT3；5. 恢复调度再PUT3。
- 预期结果：保存不等待当前任务结束且不取消它；DB失败旧future不取消；注册失败503、明确“已保存但定时应用失败，请重试保存”；重存3返回applied=true且不重置失败那次已保存锚点；旧future回调启动0。
- 覆盖：I-3；P-1、P-2、P-4。

### A-3：保留控制语义
- 前置条件：隔离环境分别配置enabled=false、cron=-、pipelineEnabled=true/PAUSED；另有正常同步模式，调用原手动启动/取消接口可用。
- 操作步骤：1. 对前三种配置GET/PUT；2. 查看continuous tick报告；3. 同步模式手动启动后到达定时点；4. 取消本轮再到下一定时点。
- 预期结果：GET editable=false、PUT409、表不变；PAUSED仍PAUSED，tick不恢复；手动运行期间定时新增0；取消仅影响本轮、后续仍可按期尝试；新任务仍使用CN排除/OA/RND_TARGET与原限额/检查点。
- 覆盖：I-4；P-1；所有保留行为。
