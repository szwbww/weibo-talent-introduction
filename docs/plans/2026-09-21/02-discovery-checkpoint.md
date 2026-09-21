# 发现进度保护与真实任务状态实施计划

> 执行：superpowers:executing-plans 按任务顺序实施；独立验证使用 fix-v。当前为待审阅方案，未开始代码修改。
**目标**：故障、取消、重启后从完整检查点继续，失败不再清空游标或被记录为成功。
**依赖**：01。
**设计基线**：[主方案](00-discovery-enrichment-master.md)。
**技术栈与约束**：Java 11、Kotlin、Spring Boot 2.7；不新增外部依赖；只改清单文件；不触碰工作区已有无关改动。新增类中辅助DTO/枚举置于所属清单文件内，不暗增文件。
**审查重点**：身份歧义、半页/重启、外部限流、三层并发、旧接口回归；下方用例覆盖本子计划相关项。

## 需求描述

故障、取消、重启后从完整检查点继续，失败不再清空游标或被记录为成功。

必须保持：主方案M-1至M-5全部适用，本子计划只改变下列实现项；不外发邮件、不扩大医学范围、不更改专家主键。
范围外：其他子计划的实现、前端样式改动、付费采购与全量无条件重跑。

## 关键不变量

### Invariant I-1：检查点只在安全边界推进
- Rule：每一完整消费页后保存 next cursor；首请求失败、部分页、取消和预算停止保留进入该页的 cursor。异常与空结果/穷尽分开；有 nextCursor 的过滤后空页继续翻页。未完成 RAW 持久化或后续必需入队的页不得推进。
- Applies to：论文源与 ORCID 分页、saveSourceCursor。
- Violation consequence：丢进度、重复全扫或漏专家。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-2：查询隔离
- Rule：source_name 采用 SOURCE:v2:<24位SHA256>，不超过 VARCHAR(50)；hash 覆盖规范化实际条件、scope、年份、页大小和查询版本，不含临时 cursor。cursor_value 保存版本化 envelope，区分 ACTIVE/EXHAUSTED；EXHAUSTED 下次定时新扫描周期可重开，但 FAILED 绝不重置。
- Applies to：手动/关键词/定时共用游标表。
- Violation consequence：不同筛选条件复用游标。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-3：状态一致
- Rule：搜索层失败计入任务失败；部分来源成功=PARTIAL_SUCCESS，全源失败=FAILED；真实空结果=SUCCESS；预算/时长提前结束有 pending 时=PARTIAL_SUCCESS；用户取消=CANCELLED。进度 COMPLETED 对应记录 SUCCESS，其余语义一致。
- Applies to：TaskProgress、DiscoveryResult、task_execution。
- Violation consequence：0 产出故障继续显示成功。
- 来源：本次审计及本页现状审计列出的K条目。

### Invariant I-4：不混合失败单位
- Rule：保留 indexed 作为 success_count；failure_count 增加终止性源错误数量且在 summary 单独说明，不把每次重试当一位失败专家。TLS/IO 单页最多重试 2 次，400 不重试，取消立即退出。
- Applies to：source failureReasons、结果汇总。
- Violation consequence：重试把失败量放大或卡住任务。
- 来源：本次审计及本页现状审计列出的K条目。

## 现状审计

discovery_source_cursor schema 见 V32：source_name 唯一、50 字符；cursor_value TEXT、papers_processed_total 累计处理次数。全部生产读写在 ExpertDiscoveryService.loadSourceCursor/saveSourceCursor；repository 只提供 findBySourceName/CrudRepository.save。定时、手动、关键词入口最后都调用 discover；CORE 当前不持久化，04 改为持久化稳定 offset envelope。task_execution 的写入只有 TaskExecutionService 及进度计数更新；task_progress_log 由 TaskProgressStore.persistProgressLog 写、restoreFromLog 和任务接口/UI 读；不改两表 schema。来源失败未进入 DiscoveryResult.taskFailureCount。(来源: K-circuit-breaker-terminal-status)

所有跨模块存储读写总清单见主方案“存储与全部相关读写路径”；本页对应新增/改动点是其子集。新共享字段仅05的existing externalIds子键；07新建独立任务存储，其他共享表/索引不增列。前端不改动，无新增样式契约。

## 实现方案

### Task 1：补回归再修边界
- 约束：I-1、I-4。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] 复现首请求 TLS 错误把原 cursor 覆盖 null；拆分 SourceRunOutcome 为 next/resumable cursor、exhausted、stopReason；处理完成页立即持久化。

```kotlin
data class SourceRunOutcome(val resumeCursor: String?, val exhausted: Boolean, val stopReason: String)
// catch -> failed outcome; never turn an exception into exhausted=true
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

### Task 2：隔离与结果
- 约束：I-2、I-3。
- 文件：下方清单中对应生产文件及测试；不允许改清单外文件。
- [ ] 先编写“验收标准”中对应失败场景，运行定向测试确认该缺陷可以复现。
- [ ] 实现 checkpoint codec，默认不自动挪用未知条件的旧 source_name 行；旧行只备份保留。当前线上历史游标恢复须依据完整检查点核实，不能用累计论文数计算。结果和进度共享同一终态决策函数。

```kotlin
val key = sourceName + ":v2:" + sha256(canonicalCriteria).take(24)
val status = when { cancelled -> "CANCELLED"; allFailed -> "FAILED"; partial -> "PARTIAL_SUCCESS"; else -> "SUCCESS" }
```

- [ ] 运行定向测试，检查既有入口回归，再记录验证证据。

新写路径与读者：本页现状审计明确的消费者继续读取相同字段；新增job由08消费、身份子键由06消费、请求policy由全部OpenAlex调用消费。依赖尚未上线时，新入口默认关闭，既有业务仍运行。

## 变更文件清单

| 文件 | 作用 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 对应生产实现 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryStats.kt` | 数据契约 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SourceStats.kt` | 数据契约 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryResult.kt` | 数据契约 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCheckpointCodec.kt` | 对应生产实现 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCheckpointCodecTest.kt` | 新增或更新本计划回归验证 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryResultTest.kt` | 新增或更新本计划回归验证 |

共 8 个文件；不存在的文件标记为新增，已存在者原位修改；辅助类型放对应文件内。迁移号碰撞是唯一允许在实施前同步改名的路径变化，须同步本清单。

## 验收标准

- V-1：原 cursor=C7，第一请求 TLS 失败后仍保存 C7；第二页失败只回到第二页；部分批、作者上限、取消相同。
- V-2：已过滤空页且 nextCursor!=null 继续翻页；无记录且无 cursor 才判穷尽。不同关键词/scope/year 不共用 key；key 长度 <=50。
- V-3：全源 SEARCH_FAILED 时任务 FAILED；一源成功另一源失败 PARTIAL_SUCCESS；完整空结果 SUCCESS；故障 failure_count 非 0。
- I-1：逐条检查规则“检查点只在安全边界推进”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-2：逐条检查规则“查询隔离”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-3：逐条检查规则“状态一致”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。
- I-4：逐条检查规则“不混合失败单位”，以以上场景及下方A场景的持久化/请求边界断言验收；不能只断言内部函数被调用。

定向验证：
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,DiscoveryCheckpointCodecTest,DiscoveryResultTest
```

各子计划定向用例通过后由集成阶段运行全量测试；无业务实现时不运行测试冒充修改已验证。

## 人工验收清单

### A-1：故障续跑
- 前置条件：测试源准备两页，各 2 人，第二页第一次返回 503；保留真实 DB。
- 操作步骤：执行任务、重启应用、再次执行相同条件。
- 预期结果：最终唯一新增 4 人；第一次保存的是第二页入口；重启后不从第一页重扫。
- 覆盖：I-1、I-2。

### A-2：状态与取消
- 前置条件：测试源全部返回 TLS 错误。
- 操作步骤：运行任务并打开任务详情；随后恢复源，重新运行并中途取消。
- 预期结果：首次显示失败且失败数>0；取消任务显示 CANCELLED，下一次可从未完成页继续。
- 覆盖：I-3、I-4。

### A-3：条件隔离
- 前置条件：准备关键词 A、B 各 3 页。
- 操作步骤：A 跑一页后取消，运行 B，再续跑 A。
- 预期结果：B 从自己的第一页开始；A 从自己的检查点继续。
- 覆盖：I-2。

### A-4：保持原业务
- 前置条件：测试环境保留原人工补全三种scope、原资格规则及一位已申请专家；邮件调度关闭。
- 操作步骤：执行本子计划新行为，再执行原人工入口；对比前后专家详情、候选/申请记录、请求范围和邮件记录。
- 预期结果：原入口仍可执行；专家主键、邮箱、署名机构及运营状态不变；已申请专家不会新建候选；默认排除的医学来源仍不参与；邮件新增0；没有付费调用或已应用迁移被改写。
- 覆盖：主方案M-1/M-2/M-3/M-4/M-5及本页跨路径回归。

