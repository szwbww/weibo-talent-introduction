# 批量邮件：历史不可用邮箱过滤与验证故障分流开发计划

状态：DRAFT，仅创建开发计划，未实施、未运行测试、未部署。
目标工作区：`/Users/lukai/IdeaProjects/weibo-talent-introduction`，审计分支 `main`，HEAD `d6f54c25b228ee2e9e0317d053957ae3f56984b5`。当前已有其他未提交改动，见证据 E-00；执行前重查，禁止覆盖。
证据：[源码快照](batch-email-reliability-evidence.md)。E-n 均包含读取命令/原始输出或带原文件行号的摘录；下文“拟改”是设计决策，不是现状事实。

## 需求与范围

本轮只产出计划。用户已明确两件事：过滤条件增加手动开关，跳过已验证不可用邮箱；单邮箱验证问题继续后续候选，仅全局依赖失败停止整批。实施前按这份文档与子计划审阅，不将本次创建计划视为已修改/已验证代码。

界面可见结果：

1. “排除已验证不可用邮箱”位于两个过滤区，独立于发送前实时验证；新建默认开，存量保持关闭，手动覆盖仅本次。
2. 候选先按最新有效原始验证记录排除 undeliverable，不刷成本次验证失败；人数预估展示排除数。
3. INCOMPLETE/TIMEOUT/BAD_RESPONSE 只暂缓当前地址，仍不向未经可信验证的当前地址发信；AUTH/NO_CREDITS/RATE_LIMITED/SERVICE_ERROR 和审计故障停止本批。分类表是设计决策，详见03。
4. 日志区分“邮箱确认不可投递”“邮箱验证暂缓”“验证服务故障”；全局停止保留真实 sent/remaining 和错误原因。

“暂停整批”在本计划定义为停止当前 execution 的后续验证/发送；旧 runtime 入口同步 PAUSED。现代配置任务下一次 cron 是否运行仍由既有 autoEnabled 管理，本次不自动停用其他任务/未来调度，不新增恢复按钮或断点续发。

## 代码事实与结论

| 代码事实（现状） | 证明 | 对方案的约束 |
|---|---|---|
| HTTP249重试一次，500ms，第二次仍未完成→INCOMPLETE；所有ServiceFailure都break | E-05:209–248；E-06:737 | 只需调整编排层分流，保留HTTP协议/重试实现 |
| 原始结果按邮箱、一年、最新 checked_at/id复用；复用行不延长有效期 | E-04:79/191；E-05:96–114 | 过滤必须共享其判据，不另建黑名单或凭标签过滤 |
| PASS risky/unknown可复用，但清理保护仅PASS deliverable | E-04:191；E-12:179–190 | 对齐保留谓词，防较新放行记录被删后旧坏记录重新胜出 |
| ES email是无normalizer的keyword，dynamic=false | E-15；三份mapping | 禁止直接拿lowercase历史地址做terms然后假定等价；使用现成规范化+批量SQL |
| 候选有ES与DB NEW重试两源；材料提醒用contact邮箱 | E-06/E-07 | 预估与三个候选路径共用一条过滤判据 |
| 迭代器看原page.size判末页，发送集合收缩后从offset0重扫 | E-08 | 过滤只能在原始页判定之后；不能在fetch返回前删行 |
| 现成scrollExpertsFiltered可按500批处理并finally清理 | E-09:715 | 过滤on的预估采用已有scroll，避免新缓存或整表内存快照 |
| controller直收Command/Snapshot，存储/历史JSON已由框架贯通 | E-14、E-13 | controller/scheduler无需新DTO或新接口 |
| 旧runtime的PARTIAL_SUCCESS落到IDLE；现代入口manageRuntimeStatus=false | E-11:312/516 | 精准修正旧runtime全局故障映射，不误把现代执行当可恢复暂停 |
| 现有开关、人数、badge及diff样式可直接复用 | E-16/E-17/E-26 | 不改styles.css，不新增样式体系 |

源码快照含全部检索命令与未截断原始输出；未做生产数据查询，未确认供应商当时为何返回249，不以推测写生产事实。

## 分解与执行次序

按 create-p 的每计划≤10文件约束拆分；合计 **21 个不同实施文件：生产代码/迁移 12 个，测试 9 个**。此为审计后明确的授权清单，替代此前12～18文件的粗估；增加来自实际验证链、分页与保留规则的必要覆盖。

| 顺序 | 子计划 | 文件数 | 可独立验证/部署的边界 |
|---|---|---:|---|
| 01 | [历史查询与保留一致性](batch-email-01-history-query.md) | 6 | 只读能力和保留修正，不开放新配置 |
| 02 | [配置与过滤后端](batch-email-02-filter-backend.md) | 10 | REST配置、预估、执行同时生效，无UI依赖 |
| 03 | [验证失败分流](batch-email-03-failure-policy.md) | 9 | 现有开关下局部继续/全局停止，含日志文案 |
| 04 | [过滤开关UI](batch-email-04-filter-ui.md) | 3 | 接入已验证的完整后端能力 |

子计划共享文件按01→02→03→04顺序修改，不并行执行；每个子计划有自己的 I-n/S-n/A-n 与测试命令。只新增一列 `batch_send_task_config.exclude_verified_unavailable_emails`；派生统计字段是API返回值，不新增持久化列。新迁移当前拟用V142，实施前再次查版本是否占用。

## 总体改动清单

| 文件 | 子计划 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt` | 01, 03 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt` | 01 |
| `src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt` | 01 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt` | 01 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt` | 01 |
| `src/test/kotlin/com/weibo/talentintroduction/task/service/TaskRetentionMigrationTest.kt` | 01 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` | 02 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | 02, 03 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` | 02 |
| `src/main/resources/db/migration/V142__add_exclude_verified_unavailable_emails.sql` | 02 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 02, 03 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIterator.kt` | 02 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt` | 02 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 02, 03 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIteratorTest.kt` | 02 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 02 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` | 03 |
| `src/main/resources/static/app.js` | 03, 04 |
| `src/main/resources/static/index.html` | 03, 04 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt` | 03 |
| `src/test/js/batchEmailVerification.test.js` | 03, 04 |

以上不含本轮计划/证据/知识文档。未列文件只读；若现有硬编码测试或迁移版本冲突要求扩范围，先附代码证据修订具体子计划，不能默默增加文件。

## 不做的设计

- 不建邮箱黑名单表/Redis/ES邮箱验证字段；不加同步、回填或缓存失效机制。
- 不新增付费验证请求，不扩大249重试次数，不自动重验/补发。
- 不增加失败阈值、熔断器或全局开关；用现有错误码表达当前需求。
- 不把异常地址当可投递；局部暂缓只允许继续其他目标。
- 不自动清除既有邮箱异常标签，也不以标签作为过滤依据。
- 不重构SMTP、发件账号、发送节奏或旧调度框架。

## 验证及实际限制

- 每个子计划的精确命令和可黑盒执行的验收 A-n 已列出；普通 mvn test 会执行JS测试（pom.xml，E-20），MySQL/migration IT必须显式开启，跳过不能记为通过。
- 组合完成后运行 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`、真实MySQL验证与 `git diff --check`；若失败来自本工作区其他已存在改动，按具体报错归因，禁止顺手修无关功能。
- 本轮仅检查源码和文档，不运行发信、不运行付费验证、不执行数据库迁移。测试报告只能在实施后产生；不得据本计划标记PASS。
- 过滤on的预估较原ES count增加分批历史读取；这是不新增ES字段时保证规范化判据一致的明确取舍。复用500ms debounce和请求seq，但不承诺无实测依据的延迟/吞吐指标。
- 原估算跨层/双来源可能重复，原from/size执行分页也有深分页限制，见02；本次不扩大为全引擎重写，保留原口径并专项测过滤不截断页。若真实范围测试出现既有深分页错误，记录为独立问题，不伪报完整发送。
- 同时编辑/验证会使不同请求结果变化；本计划保证同判据/每请求固定时间边界，不引入跨ES/DB一致性事务。
- 创建计划期间工作区已有其他未提交更改，E-00记录基线；这些文件不在本计划授权范围内，不得还原或提交。

## 人工验收入口

权威清单分别为四份子计划的“人工验收清单”。机器验证通过后才从对应计划导出 `*-acceptance.md` 供人勾选，本轮不生成重复副本。整体必走：新增/旧配置→预估→手动覆盖→快照历史→过滤后实际发送→局部249继续→全局402停止→部分成功的旧runtime暂停；全部使用本地fixture与SMTP sink。

## 自检记录

- 已加载并重新定位4条相关知识；counter已更新，均在90天窗口内，不做无关归档/知识合并。
- 业务/SQL/HTML/CSS/JS现状证据已保存；新策略明确标为拟改，无供应商故障推断。
- 子计划文件数6/10/9/3，均≤10；每计划最多两个子系统；持久化只增一个配置字段。
- 每个子计划具备需求、关键不变量、现状审计、实现方案、准确文件表、机器验收、人工验收；03/04另有逐字DOM或复用样式契约。
- 每个任务引用I/S；各读写交互点映射A；未列文件不得改；没有请求权限或实施授权的悬置步骤。
- 本轮没有业务代码变更；也没有执行独立机器验收或上线验收。

## 文档校验记录

2026-09-26：本地脚本核对四份子计划章节顺序、文件数≤10、现有文件实际存在（唯一待新增文件为V142迁移）、本地链接、I-n引用；全部通过。另运行 `git diff --check`，退出0。此处只表示文档结构和diff格式检查完成，不代表任何业务测试/机器验收通过。

补充审计发现：FlywayMigrationIntegrationTest 将“迁移至最新”的版本硬编码为141（E-30），已列入02清单，要求新增142迁移断言并更新这些最新版本期望；不改历史明确target的测试边界。计划使用实际前端函数 deepCloneConfig / normalizeManualSnapshot，未假设存在新的历史参数面板。
