# 01：去重失败保留失败页

状态：待评审、未实施。目标main；本次仅创建计划。对应 DD-18。

前置：无；本组第一份计划。

## 需求描述

去重查询失败的论文页或 ORCID 页不再被标记已消费；执行记录明确显示仍待续跑，下一次从失败页入口重放。

必须保持：

- M1：姓名/邮箱/作者ID必须有同一来源的唯一关系；歧义不绑定、同名不同邮箱不合并，同一人多个明确邮箱可保留。
- M2：邮箱验证、人才资格、create写入/重复不覆盖、原发送配置保持；不增加隐藏发送门禁。
- M3：人工暂停、既有额度/下载大小/运行时限保持；不回填、删除或重命名线上存量，不自动部署或定时验证。06只有明确列出的末页参数是有意变化。

范围外：不新增按专家自动重试表；不重构 consumeOutcome 返回类型；不修改 RAW/候选写入和晋升失败策略。

## 关键不变量

### Invariant I-1：失败页不推进
- Rule：完整消费条件同时包含本页 dedupErrors 增量=0、RAW写入失败增量=0、补全入队失败增量=0，且页未被取消/限额/时限截断。
- Applies to：两个同步页循环→persistSourceCheckpoint
- Violation consequence：失败页被跳过
- 来源：K-search-error-must-not-clear-cursor

### Invariant I-2：停止状态可读
- Rule：新增 DiscoveryStopReason.DEDUP_INCOMPLETE 常量，只是现有 stopReason 字符串取值，无新存储字段。纯去重失败保存 entering cursor、ACTIVE、pendingWork=true；不把它记成来源搜索失败。单源纯去重失败 terminalStatus=PARTIAL_SUCCESS；人工取消优先 CANCELLED。RAW/入队/去重同时失败时主停止原因按 RAW_WRITE_INCOMPLETE > ENQUEUE_INCOMPLETE > DEDUP_INCOMPLETE，计数全部保留。
- Applies to：页退出、SourceStats、DiscoveryTerminalStatus既有读路径
- Violation consequence：错误显示 SUCCESS 或混淆故障阶段
- 来源：original

### Invariant I-3：重放幂等
- Rule：重放页面仍按邮箱去重和 create 写，已入库的姓名/作者ID不覆盖；补建任务沿用现存ES _id。队列 dedupFailed 仍按既有 DEDUP_LOOKUP_FAILED 重试。
- Applies to：consumeOutcomeInternal、ORCID重复分支、队列消费
- Violation consequence：重放产生第二份错误身份
- 来源：K-author-identity-needs-email-evidence

### Invariant I-4：既有业务边界
- Rule：M1～M3必须保持。未知身份不得调用邮箱验证/专家写入；明确身份仍过原验证和资格，重复不覆盖；不创建新发送拦截字段、黑名单或后台定时任务。
- Applies to：本计划列出的生产文件及其既有consumer/writer调用。
- Violation consequence：为提高覆盖而改变专家准入或发信配置。
- 来源：会话要求；K-author-identity-needs-email-evidence。

## 现状审计

引用审计附件 C1/C2/C3/C5。ExpertDiscoveryService.kt:725、763、773、815 为论文页边界；ORCID :964、999、1057、1067 为对称路径。round2/results.json 的 ES500 回执：dedupErrors=1、indexed=0，却持久化 v2|EXHAUSTED|、terminalStatus=SUCCESS。ORCID 相同分支只有代码证明，先写失败用例再实现。交互 X1、X4、X5、X7；队列 X2 只作回归。

[审计附件](discovery-repair-audit.md)列出的相关C表schema、写路径、读路径与交互点是本节组成部分；[原始检索回执](discovery-repair-evidence/)保留命令和逐行输出。非本计划文件只允许只读回归，不能借审计扩大改动范围。新增测试资源写于实施期，测试classloader只读；不新建线上文件存储。

## 实现方案

### T-1：先锁定 RED（I-1/I-2，I-4）

src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt：扩展现有 dedup search error counts dedupErrors and skips，加入 entering cursor 非空/首游标null、page.nextCursor 非空/末页null的交叉用例；另造 ORCID dedup500 页。断言最终 checkpoint、pendingWork、terminalStatus，而非只看计数。

### T-2：补齐两个页边界（I-1/I-2，I-4）

src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt：在 RAW/enqueue 的 before/delta 同位置增加 dedup before/delta；将零增量加入完整页判定；失败后停止本来源，退出保存进入页游标。新增常量，复用现有终态决策，不新增决策类。

### T-3：验证跨页重放（I-1/I-2/I-3，I-4）

src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt：两篇真实消费输出，第一篇写成功、第二篇去重500；再次同页请求时第一篇重复不改身份，第二篇成功入库。补 ORCID 对称、暂停、RAW写失败、补全入队失败组合。消费结果由真实消费函数生成。

### T-4：生成可核验的验收输出（I-1～I-4）

在已列出的测试文件里用现有临时目录/JSON工具输出 `target/discovery-plan-acceptance/01.json`，包含输入fixture标识/哈希、实际解析或请求输出、checkpoint/终态及必要ES请求结果。验收数字来自实际函数调用和替身记录，不硬编码“passed=true”冒充证据。测试不访问外网和生产。人工看输出即可，不需要阅读测试实现。保存这些衍生报告不增加生产数据写路径；不在此时生成-acceptance.md。

## 变更文件清单

共2个文件，1个生产子系统（发现编排）；下表为穷尽清单。无新增共享存储字段；06新增的是应用配置属性，不是DB/ES字段。未列文件不可修改。

| # | 文件 |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` |
| 2 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` |

## 验收标准

- I-1：4种 cursor 组合以及 ORCID 案例均 ACTIVE/保留原值；成功整页仍正常推进。
- I-2：单源纯dedup错误=PARTIAL_SUCCESS，pendingWork=true，DEDUP_INCOMPLETE；取消=CANCELLED；0条正常空结果=SUCCESS。
- I-3：第二次完成后RAW总数2、候选总数2、第一篇原身份逐字段一致；队列去重故障仍RETRY_WAIT，不误判SUCCEEDED。
- I-4 / M1～M3：未知邮箱验证调用0/RAW写0；明确邮箱验证拒绝RAW=0；资格拒绝RAW=1候选=0；同邮箱重复新增0且原字段不变；同名不同邮箱各1条；一人两个明确邮箱各1条。暂停后新的消费写入0；源码diff不涉及发送配置、发送服务、迁移或线上数据脚本。
- 交互覆盖：每条人工A项中标明X路径；真实案例必须完整原文/metadata，允许mock外部HTTP/验证/资格/ES，不允许mock身份解析。

定向命令（JDK11）：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,DiscoveryCheckpointCodecTest,DiscoveryResultTest,DiscoveryPipelineServiceTest,ExpertIndexWriterServiceTest
```

定向通过后按fix-v独立机器验证；最终联合`mvn clean package`。若已有断言被本计划有意改变，只修改清单内的对应断言并保留旧场景反例；不靠删除测试通过。测试或文件范围不足先修订计划，不在执行中扩项。

## 人工验收清单

### A-1：失败末页
- 前置条件：使用本计划 ExpertDiscoveryServiceTest 的 failure-page fixture：进入游标 ENTERING-PAGE，一名明确作者，邮箱与资格通过，ES去重返回500。
- 操作步骤：1. 在仓库根执行上面的定向命令；2. 运行本计划测试命令，打开 target/discovery-plan-acceptance/01.json，查看第一次调用结果与保存游标。
- 预期结果：dedupErrors=1；indexed=0；stopReason=DEDUP_INCOMPLETE；checkpoint=v2|ACTIVE|ENTERING-PAGE；terminalStatus=PARTIAL_SUCCESS。
- 覆盖：I-1/I-2，X1/X7，需求。

### A-2：故障后重放与ORCID
- 前置条件：同一fixture第二次改去重响应成功；另有第一条已存在/第二条失败的两项页，以及ORCID同形场景。
- 操作步骤：1. 在仓库根执行上面的定向命令；2. 在报告查看两次请求游标和最终ES请求/文档；打开ORCID场景。
- 预期结果：第二次从原游标取页；两项页最终RAW=2、候选=2，旧身份不变；ORCID故障末页也ACTIVE，恢复后EXHAUSTED。
- 覆盖：I-1/I-3，X1/X4/X5。

### A-3：业务边界回归
- 前置条件：沿用ExpertDiscoveryServiceTest现有明确身份/未知/无效/资格拒绝/重复/同名/多邮箱/暂停场景；只补本计划缺失的断言或报告输出，不为每份子计划复制一套测试。外部验证与ES均为现有隔离替身。
- 操作步骤：1. 执行定向命令；2. 查看同编号JSON中的boundaryCases，按输入、验证调用、RAW/CANDIDATE请求及旧文档比较；3. 查看git diff --name-only，确认未出现发送配置、发送服务、迁移和线上数据脚本。
- 预期结果：未知与无效邮箱RAW=0；资格拒绝RAW=1候选=0；明确合格RAW=1候选=1；重复新增0、旧身份不变；同名不同邮箱2条、一人两明确邮箱2条；暂停后新增消费写0；上述禁止范围改动0个文件。
- 覆盖：I-4，M1/M2/M3，X4/X5/X8（本计划触及的入口）。

人工验收开始时才从本节导出同目录同前缀-acceptance.md，包含勾选框、验收人、日期、结果/备注。此刻不生成。

## 自查结论

已按create-p检查：具备不变量/审计/逐文件任务/机器及人工验收；每任务引用I编号；文件≤10、子系统≤2、共享存储新字段=0；前端仅08且S-1覆盖新增DOM；真实/合成证据分开。状态仍待评审，未执行测试、未实施代码。
