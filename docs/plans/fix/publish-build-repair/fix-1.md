# FIX-1：隔离历史补录 CLI 的 Spring 配置

日期：2026-10-26。状态：待独立修复执行；本文件不表示已修复或已发布。

复验对象：`docs/plans/2026-10-26/publish-build-repair.md`。
基线提交：`6b2846932e1d`（发布任务固定的提交前缀；未将前缀冒充完整SHA）。
授权模式：MECHANICAL_REPAIR。机械修复尝试1已通过；新发现属于生产运行时P1，按fix-v要求生成最小修复计划并停止，不直接实施。
关联原合同：`docs/plans/2026-10-24/discovery-recheck-10-historical-replay.md` I-3及实现方案1（CLI隔离、默认零业务写）；发布计划I-3（真实启动/健康检查通过）。

## P1-1：显式 apply-only 配置被生产扫描，启动冲突

- Lineage：NEW_IN_SCOPE；原先测试时钟/重复版本已解决，本项不是其持续失败，也不是改迁移引入。
- 回归边界：INTRODUCED于重审历史CLI实现；本轮实际部署暴露。没有将旧版归档启动耗时归入同一缺陷。
- 触发：包含该配置的WAR正常生产启动，即可扫描并注册CLI别名。
- 影响：应用Spring上下文启动失败，/talent无法正常提供服务，发布健康检查失败并自动回滚。
- 代码证据：`DiscoveryHistoricalReplayTool.kt:216-263` 声明“Explicit apply-only bean graph”，却以`@Configuration`成为扫描候选；`:255`将restTemplate与europePmcRestTemplate声明为同一bean别名。正式`RestTemplateConfig.kt:61-71`分别定义这两个客户端。`:287`显式register表明CLI本来不依赖组件扫描。
- 运行证据：`job_20261026-110010_publish-production_7e1e6eab`终态failed/exit1；生产`catalina.2026-10-26.log:106`：`Cannot register alias 'europePmcRestTemplate' for name 'restTemplate': Alias would override bean definition 'europePmcRestTemplate'`。同日志`:101`为StandardContext[/talent]启动失败。
- 发布日志已确认V161与V162均applied、WAR上传、健康检查失败、`status=rolled_back`。不能再次修改这两份已应用迁移，也不能宣称数据库跟随WAR回滚。

## 最小修复范围（待执行）

| 文件 | 允许变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryHistoricalReplayTool.kt` | 将HistoricalBindingsConfiguration改为仅显式register可用、不会成为默认组件扫描候选的配置；优先移除扫描stereotype，保留显式注册的lite配置及现有@Bean/@Import语义。禁止启用bean覆盖掩盖冲突。 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryHistoricalReplayToolTest.kt` | 添加可捕获真实启动污染的行为回归：正常生产扫描不发现CLI配置，而显式CLI注册仍可解析其bean图；不以源码字符串、注解存在性或仅bean数量断言代替运行证据。 |
| `docs/plans/2026-10-26/publish-build-repair.md` | 执行批准后记录本项范围修订和真实验证收据；不改旧验收或既有功能repair计划。 |

修复不得改变HTTP timeout/retry、正式RestTemplate、执行器、mapper、准入/应用/取消/原文恢复语义；不得新增类、接口、状态、fallback、配置开关或迁移。若移除stereotype不能保留显式CLI正常装配，停止并报告实际证据，不扩大设计。

## 验收

1. 真实Spring组件扫描不注册HistoricalBindingsConfiguration；生产两个正式HTTP客户端保持独立且使用RestTemplateConfig，无CLI的同步执行器/analyzer污染。
2. 显式register CLI配置仍可解析现有@Bean及@Import；已有compare默认不访问DB/ES/provider，apply仍要求固定manifestHash及连接配置。
3. JDK11.0.32执行`mvn test -Dtest=DiscoveryHistoricalReplayToolTest,DiscoveryPaperBackfillServiceTest,RestTemplateConfigTest,DiscoveryRecheckFlowIT -DmysqlIt=true`，真实MySQL用例不得跳过。
4. 除测试外做真实Spring装配烟测，验证默认扫描与显式CLI两个入口；不得对生产运行apply-bindings或付费调用。
5. 完整`mvn clean package`后，明确获准继续发布时仅启动一次新publish job，每15秒查询至终态。新部署应先评估下述旧版启动恢复耗时，但本修复禁止以跳过恢复/调大健康超时掩盖新缺陷。

## 修复前验证与合规审计

- 原阻塞定向测试：PASS，31 tests / 0 failures / 0 errors / 0 skipped。
- 隔离MySQL全量迁移及真实Store烟测：PASS；schema162、重复迁移0，SAVED/SAVED/QUOTA、重启QUOTA、第8天过期。
- 发布构建：WAR已生成并上传；最终日志窗口未保留全量测试计数，count unavailable，不借用上一失败轮计数。
- 发布/启动：FAIL，bean alias冲突已证；自动回滚后HTTP200已实测。
- I-1配额/时间：✅ 原断言保留、定向测试和真实Store烟测。
- I-2迁移：✅ 新库与真实发布应用记录；已应用迁移不可再改。
- I-3部署：❌ P1-1。
- Accumulation check：✅ quota重启烟测。
- State-machine check：N/A，本修复不改状态机。
- Cross-plan check：❌ CLI装配泄露到生产。
- Deleted code：✅ 旧重复版本名称已移除，未加兼容别名。
- No extras / Scope compliance：✅ 已提交机械修复限本轮清单；新增生产装配修复尚未执行。
- 人工验收：PENDING。

## 观察（不授权本轮顺手修复）

旧WAR回滚后最初HTTP超时。生产PID23073的`jcmd Thread.print`显示主线程RUNNABLE，停在DiscoveryPaperArchiveService.hashFile→validBlob→recover→startupRecovery（ApplicationReadyEvent同步监听），随后最终HTTP200。属于既有归档恢复耗时，本轮未证明永久故障，不创建恢复策略或超时调整任务。

## 下一步

独立修复执行以上P1-1范围，然后重新运行fix-v；在修复前不要重发同一WAR。该计划不修改此前discovery-recheck-master/repair.md中已关闭的功能审查决定。
