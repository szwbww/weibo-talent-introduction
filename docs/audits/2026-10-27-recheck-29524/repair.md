# #29524 准备阶段内存溢出修复

授权：用户 2026-10-27「好的 你直接修复 并上线吧」。本次为已确认生产故障的直接修复；不是扩大准入规则或新建重审任务。

证据：线上 preparation 线程在 recheckScopeHash -> ObjectMapper.writeValueAsString 发生 Java heap space；堆上限 2GB。8594 条 RESERVED，snapshot_json 合计 315715363 字节，最大 652666 字节，8594 条输入全部留存，Apollo 请求 0。run revision=8629，PREPARING/EVIDENCE，scopeHash=null；owner 级心跳仍不断续期。

## 修复边界与验收

- H1：SHA256 输入仍是原排序下六列组成的 JSON 二维数组，含 null/数字/Unicode 转义语义，已有 READY 哈希不失效。
- H2：哈希结果集和 JSON 输出均流式处理，内存不随全名单快照总量增长；真实 MySQL + 独立 128MB JVM 验证 8594 人、至少 300MB 快照。旧实现必须复现失败。
- H3：startRecheck 同样不能全量装载快照；保持原事务、run -> item 锁序、逐人顺序、批次编号和幂等判定。
- L1：准备工作异常（含 Error）终止后不能继续续心跳；可持久化时记 FAILED，仍重抛 Error；持久化不可用时原失联回收能够接管。
- L2：正常工作、READY、取消、重复确认、重启失联语义保持；不扩大外部调用、准入或人工保护规则。
- D1：部署只包含本次受测类文件，不夹带已有 application.yml/文档工作区改动；备份旧 WAR/展开目录，核对运行任务，验证发布文件散列、服务健康。
- D2：#29524 仅在输入与已留存名单逐项对应、全 RESERVED、零请求/零准入、身份与快照完整时补做哈希冻结，恢复 READY 待用户确认；恢复步骤不激活任何人，不改名单或证据。用户随后明确授权「上线之后运行线上的重审」，服务健康验收后通过正常应用入口启动此原任务。

代码边界：DiscoveryVerificationRepository.kt（哈希、启动的读取）、DiscoveryRecheckService.kt（工作线程生命周期/异常）、TaskExecutionRepository.kt（精确任务心跳）；直接对应回归测试、部署/恢复审计。无迁移、无 CSS、无新状态。

机器验证与发布结果将在实际完成后追加。不存在未经执行的 PASS 声明。

## 已执行验证

- RED：10 项，3 项预期失败：8594 人大快照在128MiB堆下旧 JDBC 全量读取 OOM；致命准备异常未终结任务；idle reconcile 继续 owner 心跳。其余7项通过。日志 `/tmp/recheck-29524-red.log`。
- GREEN：DiscoveryVerificationRepositoryIT 31、DiscoveryRecheckServiceTest 9，40/40，零失败/跳过。日志 `/tmp/recheck-29524-green.log`。大名单合成快照大于315715363字节，独立JVM maxHeap=134217728，实际完成hash→READY→RUNNING，8594人QUEUED、68752步骤、批次17*500+94；不含外部服务。
- 线上只读核对：128MiB独立进程使用实际修复类；8594输入与原prepare类型反序列化后快照逐项一致，全RESERVED、无步骤/请求/准入/激活；revision=8629；hash=`fab8adb6db794da34fd36a875b22a86527cb4bead3e7d02a9bd3a84b252328b9`。
- 旧版相邻问题：4319份输入profile的`hindex`在反序列化ExpertProfile（构造参数hIndex）后变null。由实际逐人对照+相同Jackson/DTO转换证明；其余字段按原类型转换后全相同。本次保留已冻结快照，不修此资格数据问题，不将其纳入内存修复效果声明。后续需独立确定历史快照/事实指纹/资格重算兼容路径。
- 源码审计：hash所有调用点（prepare/READY CAS/start）均走新流式实现；六列顺序、DB排序、Writer JSON输出保持旧哈希；流式结果期间无嵌套查询；run→item锁序和start事务未变化。
- 线程生命周期：真正执行时登记线程，finally移除；仅活线程对应id+owner可续心跳；Error尽力标FAILED并重抛；落库失败则finally仍移除，既有5分钟失联回收接管。无新状态、无通用恢复入口。
- 修复验收来源为本次明确用户授权及本文件H1..D2；未宣称此前大计划全部复验通过。

## 发布前补充验证（2026-10-28）

- 精确心跳真实MySQL测试另1项通过：不同id、owner、终态均不误续期。合计41项独立测试通过。
- 补充DiscoveryRecheckFlowIT遭遇本地休眠后Docker/数据库连接失效；复跑阻塞于Docker探测，未计为通过。核心40项和补充心跳SQL项已经分别完成，线上真实8594人只读校验也已完成。
- 发布前再次核对：唯一RUNNING任务#29524；8594 RESERVED、请求0、输入8594、revision8629；3份生产源码与受测隔离构建SHA一致。

- 发布恢复已执行：原hash与线上再算一致，run revision8630、READY、input_closed=1；准备任务SUCCESS，8594项全RESERVED。新PID19778，39/39类校验一致，配置文件SHA保持。
- 启动耗时已定位：既有DiscoveryPaperArchiveService定时recover先取得锁、遍历原文计算SHA；ApplicationReadyEvent主线程等待同一锁，再执行startupRecovery。线程栈证明非本次哈希OOM。当前未改该组件。

## 上线验收

- 2026-10-28 00:30:43（服务器时间）：新PID19778；39/39修复文件SHA匹配；配置SHA未变；内网/公网首页HTTP200，未登录业务接口401；启动无OOM/链接错误/Bean创建失败。完整回执见deploy-receipt.json。
- 本地main提交90bee3b。生产为原WAR上的限定类文件补丁，不包含其他并行改动。
- #29524已恢复READY，revision8630，8594 RESERVED，0外部请求、0准入；用户已授权启动，但当前可用管理页面未登录，等待用户完成登录后通过正常启动入口执行。不宣称已运行。
- 服务启动约7分钟：既有论文原文recover的定时与启动校验先后全量执行，线程栈确认持续读文件/计算SHA。未为提速跳过原文校验。
