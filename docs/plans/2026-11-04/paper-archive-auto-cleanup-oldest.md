# 深度发现原文空间回收：已确认实施版

2026-11-04；目标 /Users/lukai/IdeaProjects/weibo-talent-introduction，main。
审批依据：用户明确修正为“最早一批”，确认从旧到新清理、够用即停、当前任务不删、元数据与提取结果保留，并授权直接在 main 修改。此前 paper-archive-auto-cleanup.md 中“最近一批”、新增配置开关及七文件范围已被本轮对话替代。本次不提交、不推送、不部署。

## 需求描述
深度发现写入前容量不足，按已结束任务从旧到新回收原文，满足原写入条件即停并继续；没有可删原文则沿用阻断/重试。复用现有保护、共享和读取锁，不新增历史重审保留机制、前端、设置开关或迁移。

## 关键不变量
- I-1：候选是早于当前 execution、已结束的 EXPERT_DISCOVERY，按 started_at/id 升序；未知归属、当前及运行中任务不删。清理最早批后仍不足才进入下一批。
- I-2：只删除原文；复用 DELETING → unlink → finishDelete；保留 record/outcome/analysis/历史统计。受保护文件、读取租约、活动 capture 和本次复用 hash 不删。跨历史批次共享文件只在其全部引用所属批次均已进入清理范围时删除；引用当前/运行中任务的文件不删。
- I-3：只在容量检查失败时清理；实际空闲和现有归档配额满足当前写入即停；权限/路径/数据库错误不触发删除；大于整个配额的单个文件不删除历史数据。无可删内容仍 ARCHIVE_STORAGE_BLOCKED，保留既有消费边界。
- I-4：settings/stats/list/detail/preview 不自动删除，手动删除 API 和原有崩溃恢复语义不变。归档禁用时既有 scope 完成语义不变。

## 现状审计
沿用 paper-archive-auto-cleanup-evidence/paths.txt 的路径审计。V159/V163 定义归档库；文件按 sha256 共享。Service 的 preflight 与无 scope saveBodyInternal 是容量入口，delete/recover 是删除入口；Repository 提供全部写入。withContent/analysisLease 是原文读取入口，重审/分析消费固定引用；删除后可报原文缺失。task_execution 本次只读，TaskExecutionService 写入任务状态。ArchiveMysqlFixture 当前仅加载 V159/V163，需要补最小 task_execution fixture。

## 实现方案
- T-1（I-1/I-2）：Repository 查询旧任务及按 hash 分页的批次 blob 候选。
- T-2（I-1/I-2/I-3/I-4）：Service 的容量入口传 execution；容量不足按批清理、复测即停；复用单 blob 删除实现和日志，保留手动 API。
- T-3（I-1 至 I-4）：补任务 fixture、查询顺序/排除、跨批递进/提前停止/状态保留、capture 自动继续与失败测试；回归现有删除及恢复测试。

## 变更文件清单
实现文件限定如下（5 个）：
1. src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepository.kt
2. src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt
3. src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepositoryIT.kt
4. src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveServiceTest.kt
5. src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCaptureTest.kt
执行记录写入 docs/plans/2026-11-04/paper-archive-auto-cleanup-oldest-execution.md。

## 验收标准
- I-1：顺序不能取决于论文 record 插入顺序；老批空间够用不得删新批；老批不足递进；当前/运行中/非发现/未知任务保留。
- I-2：保护、共享、读取/活动 scope 保留；已删原文 attempt=DELETED、blob=MISSING，提取 JSON 及历史成功字节不变；既有恢复测试通过。
- I-3：fake diskProbe 模拟容量变化，覆盖配额与真实空闲两个入口；实际清理后 capture 继续保存；全部历史清理仍不足则继续阻断；空间足够不清理。
- I-4：原有 settings、手动删除与 capture 禁用切换测试通过。
- 必跑命令：JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPaperArchiveRepositoryIT,DiscoveryPaperArchiveServiceTest,DiscoveryPaperArchiveCaptureTest,DiscoveryPaperArchiveRecoveryConcurrencyTest,DiscoveryPaperArchiveFlowIT -Dexec.skip=true test
- 格式检查：git diff --check -- 上述五个文件。

## 人工验收清单
- A-1：隔离环境准备两个已结束的发现任务原文，第二批晚于第一批。制造低于 minFreeBytes 的空间条件后启动新任务；预期从第一批原文开始回收，够用即停，新任务继续保存。覆盖 I-1/I-3。
- A-2：让第一批可释放空间不足；预期继续回收第二批，当前任务原文保留。全部可删原文不足时预期 ARCHIVE_STORAGE_BLOCKED，输入仍待重试。覆盖 I-1/I-3。
- A-3：标记历史论文 protected，并准备与当前任务共享的原文；触发清理后两者仍可下载。被清理论文的标题/提取结果保留，原文下载返回 410。覆盖 I-2。
- A-4：容量充足时打开列表/统计/预览并启动新任务；预期旧原文均保留，手动删除仍要求有效快照。覆盖 I-3/I-4。
