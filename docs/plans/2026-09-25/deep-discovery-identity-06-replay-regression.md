# 身份修复：重复发现的可靠重试

当前修复授权内的回归补充。基于测试发现ES写入成功而MySQL入队失败会需要重放，因此细化04-admission中的“重复不补建任务”：只在存量档案自身有当前有效凭证、姓名/邮箱/真实外部ID与输入一致时允许按存量真实_id幂等补建任务；输入证据不能替代存量凭证。工作树同前。

## 需求描述

保护已确认身份，同时保留跨ES/MySQL崩溃窗口重试。原业务键及历史邮件不变，未知旧档案不因新论文而获得补全资格。

## 关键不变量

### I-1：存量独立证明
重复记录必须自身通过DiscoveryIdentity.allowedSource，且与当前输入邮箱、姓名和已有真实外部ID一致，才能补建任务；否则仅记录重复/冲突，不写专家。
### I-2：恢复失败可重试
幂等补建失败仍保留页或队列重试，不能假报成功；成功不增加专家数、不覆盖专家。

## 现状审计

ExpertDiscoveryService同步ORCID与consumeOutcomeInternal各有去重分支；两个分支统计enqueue失败决定推进。DiscoveryPipelineServiceTest的3条真实consumer测试、ExpertDiscoveryServiceTest的重放测试覆盖该跨存储窗口。旧stub没有当前凭证，需明确构造已确认存量档案；同时增加无凭证拒绝断言。

## 实现方案

ExpertDiscoveryService.recordIdentityDuplicate读取源对象和当前验证字段（I-1），仅独立验证一致时调既有enqueueEnrichmentJob（I-2）；保留原失败统计。两个测试文件替换已确认样本stub并保留重试断言；新版本抽取fixture显式附版本与证据，禁止修改生产默认值来迁就旧测试。

## 变更文件清单

| 文件 | 动作 |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt | 存量验证及安全重试 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt | 重试和拒绝回归 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt | 当前版本队列与存量验证回归 |

## 验收标准

I-1：未知/不一致重复档案补建0；已验证且一致可补建，不重写档案。I-2：第一次RAW成功入队失败→重放只补建，新增总数1；旧缓存仍拒绝。
命令：JDK11 `mvn test -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest -Dexec.skip=true`。

## 人工验收清单

### A-1：跨存储重试
前置：隔离测试环境关闭发信，用当前明确XML样本，第一次让学术任务写入失败。操作：恢复任务库后重放同一条论文。预期：专家累计1条、任务1条、专家姓名和业务键不变；I-1/I-2。
### A-2：旧身份无凭证
前置：隔离环境预置相同邮箱但没有身份凭证的档案。操作：消费新论文。预期：不改姓名、不补建任务，保留拒绝原因；历史信件不变；I-1。
