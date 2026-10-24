# 07 · 统一核验执行与八步留痕

日期：2026-10-24。状态：待执行；本文件不证明修复已上线。前置及发布门禁见 [总计划](discovery-recheck-master.md)。基线 HEAD `b161e87464d42b9fa2cd076ef9255b0e3b2b29ab`，须保留工作区已有修改。

## 需求描述

结果：普通深度发现和重新审核都调用现有Worker/Rules/Gate/Commit，写真实8步记录；对照只留审计，应用通过原保护/CAS提交。不是另写一套资格判断，不替换既有独立学术补全任务，不改免费/付费配置或门槛。

## 关键不变量

### I-1：共用执行与建议/写入分离
- 规则：普通发现及新重审复用同一Worker.process、Rules和Commit；AUDIT仅保存建议/trace，admission及三层ES零写入，业务Gate不因AUDIT变化。ENFORCE必须经过原prepare/commit/project保护与全量hard blockers。
- 适用：Worker、Commit、Gate
- 违反后果：对照与生产判断两套或产生隐性晋升。
- 来源：需求及下列源码审计

### I-2：固定八步且真实
- 规则：每项保存1名单保护、2来源原文、3身份、4学术资格补证、5直接准入、6Apollo、7综合结论、8记录同步；早退出也填剩余SKIPPED及原因。主卡点为最早真正阻止后续的步骤；缺原文但身份已可靠不是卡点。错误不是明确不合格。
- 适用：TraceRepository、Worker、Query
- 违反后果：用户不知道实际卡哪一步。
- 来源：需求及下列源码审计

### I-3：固定输入及计时
- 规则：新轮保留inputHash/原文SHA/快照/证据版本/冻结evaluatedAt；REUSE原始ORCID响应投影重建派生事实，但不得把新身份hash强贴旧证据。所有rule3 Rules/Gate复算用同一evaluatedAt；输入变化记录STALE_INPUT，需另开轮。
- 适用：WorkerEvidence、AdmissionEvidence、Gate
- 违反后果：年龄跨日或证据漂移导致无法复验。
- 来源：需求及下列源码审计

### I-4：配置与续跑
- 规则：Apollo沿run冻结search/detail配置；REUSE绝不外调；REFRESH沿原预算/429/credits/retry游标。普通在途旧run不重排queries，已有旧evidence不强制换版本。重试append步骤attempt但不清历史HTTP计数。
- 适用：Worker/provider callbacks
- 违反后果：恢复后查错游标、付费越权、重复伪造调用。
- 来源：需求及下列源码审计

### I-5：取消与同步
- 规则：每次外调预留及commit前检查run控制CAS；取消后迟到回包只可存该请求诊断，不能新准入。已提交准入保留；projection失败单独显示已准入/同步失败，重试只投影，不能重查Apollo。
- 适用：Worker、Commit、Trace
- 违反后果：取消回滚成果或重试烧请求。
- 来源：需求及下列源码审计

## 现状审计

Worker:55 tick→claim、:73 process，finish:178左右prepare/commit/project；它已有SQL请求许可/HTTP完成/心跳/原文解析重建/游标。Commit:49–100对比租约/身份/事实/准入revision及人工/联系/SBIR/APPLICATION保护，再政策Gate；:103投影独立重试。AdmissionGate:60–83重新评估payload，不能仅把Rules结果写成AUTO。
存储：V158由Repository写，05新trace由新增TraceRepository写；read为QueryService及08API。新普通发现记录从已有item首次激活产生8槽，不向旧已结束项补造耗时。run settings、原文及准入全部读写路径见共享审计；无新ES写者。

完整检索回执、共享存储全集及交互矩阵：[证据目录](discovery-recheck-evidence/README.md)。以下列出的写入边界为准，不因引用共享审计而扩大文件范围。

## 实现方案

1. 新DiscoveryVerificationTraceRepository专门读写05表：initSteps幂等创建8个NOT_STARTED；startStep获取下一attempt，finishStep仅允许RUNNING→终态；写入前校验item lease/generation，普通激活初始化可无运行租约但必须已activated。input/output各≤64KiB，证据只存短摘录与引用，不存整篇全文、API key或完整HTTP头。超限明确truncated/count，完整结构在item evidence_json/attempt有引用，不能假装全集。（I-2/3）
2. Worker在原执行节点调用trace，不新建通用workflow引擎：1读取保护/基线；2查询并锁定来源，3验证已有身份并补原文身份佐证；4解析论文资格与ORCID，REFRESH按需只读OpenAlex供方向补证；5本地Rules；6按原逻辑Apollo；7原policy+Gate最终判定；8AUDIT记“仅对照，未应用”，ENFORCE commit与project分别记录。已可靠身份时原文缺失只记INSUFFICIENT继续；没有可靠身份则后续个人外调SKIPPED。（I-1/2）
3. 对普通新发现同样经过共享原文证据桥接；OpenAlex三层补全仍由既有任务负责，这里只读已有研究方向，避免重复外查；重审REFRESH才按spec执行学术只读刷新。Registration.activate幂等初始化trace；旧项继续运行可从首次可观察节点记“之前步骤历史未记录”，不得倒推时间。（I-1/2）
4. REUSE在新item的snapshot_json.reviewInput中读取准备阶段冻结的缓存响应/派生证据/完整search对象，禁止HTTP客户端调用；缓存复用必须标CACHE_REUSED与真实原fetchedAt，新轮HTTP计数保持0；缺原始响应时保留已有派生事实并明确not-reparsed，不伪称parser对照；不得复制旧progress游标到新轮。REFRESH沿原Worker逻辑查询。新payload只增加一个executionContext协议成员，内含evaluationAt与paperSourceRefs；Gate AdmissionEvidence兼容读取并传Rules；旧payload无时刻维持旧语义。旧版本与新版本比较必须同一时刻、同facts，parser重建差异另列。（I-3/4）
5. Commit在读取新控制状态后再次锁run→item并检查允许应用；对epoch>0额外比较snapshot_json.reviewInput中的expectedAdmissionRevision与当前准入revision（null代表当时无记录），不能用结束时prepare刚读取的revision替代启动时基线；不符则STALE_INPUT/ADMISSION_CHANGED，只记审计；仅建议为准入者写当前admission。未通过/错误重审不自动撤销已有已批准状态，显示“本轮建议未通过；当前准入未变”；显式撤销仍走原人工动作。第7步保留before/proposed，步骤8独立after/persistedRevision/projection状态。刷新时事实hash变了记STALE_INPUT，不局部重新签名。（I-1/3/5）
6. TraceRepository提供step详情读取DTO（08再接入QueryService/API），按step1..8及attempt顺序；旧记录无表行返回recorded=false，不创建行。projection重试追加step8 attempt；auth/credits/429保留等待原因与下次时间。VerificationRepository增加仅完成已派发attempt诊断的方法：校验attempt.itemId/requestKey与原请求归属、completed_at为空，允许取消后迟到响应一次性补录，不改item终态/准入/租约，不取得新请求许可；无回包保持UNKNOWN_AFTER_CANCEL，不伪造HTTP状态。（I-2/4/5）

## 变更文件清单

仅下列 10 个文件。新增文件明确标注；其余文件不得顺带修改。

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationTraceRepository.kt` | 新增8步持久读写 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` | 共享原文/证据模式/轨迹 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt` | 新项初始化/普通兼容 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt` | 重审控制/对照与投影轨迹 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGate.kt` | 冻结评估时间/证据兼容 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` | 取消后已派发请求的有界诊断补录 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` | 各分支8步与零HTTP |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitServiceTest.kt` | 取消/保护/无写入/同步 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGateTest.kt` | 时钟及历史复算一致 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationTraceRepositoryIT.kt` | 新增租约/步骤CAS真实库测试 |

## 验收标准

- I-1：同快照REUSE规则建议与真实Gate一致；AUDIT业务写0；ENFORCE走原commit；所有原保护测试不删除。
- I-2：无原文但可靠身份能继续；无身份不查Apollo；直接通过step6=SKIPPED/DIRECT_SUFFICIENT；错误有HTTP码/原始code；旧史recorded=false。
- I-3：固定时刻跨生日复跑相同；Gate读取新evidence与Worker同outcome/qualification/route/personId；不同hash拒绝，不重签旧证据。
- I-4：REUSE网络调用0；免费设置detail调用0；429等待不增失败；普通旧APOLLO_SEARCH queries顺序不变。
- I-5：真实库并发取消/commit只有一个胜出；取消胜出不提交，commit胜出保留；同步重试provider调用0；trace不能被过期token写。

## 人工验收清单

### A-1：8步可解释
- 前置条件：准备测试专家：直接通过、Apollo通过、缺身份、缺原文但身份可靠各1人。
- 操作步骤：在测试执行入口跑AUDIT并读取逐步详情。
- 预期结果：每人8步；直接通过第6步跳过；缺身份卡第3步；仅缺原文不被判不合格。业务准入不变。
- 覆盖：I-1/2

### A-2：对照与刷新区分
- 前置条件：冻结同一人数据并关闭测试网络；另配置免费Apollo。
- 操作步骤：先REUSE，再打开网络REFRESH。
- 预期结果：REUSE请求0并列缓存缺口；REFRESH明确新数据来源/时刻；免费detail请求0。
- 覆盖：I-3/4

### A-3：取消与候选同步
- 前置条件：两人拟准入，其中一人在HTTP阻塞，另一人已提交但候选写入故障。
- 操作步骤：取消run，解除阻塞；恢复ES仅重试同步。
- 预期结果：阻塞者不新准入；另一人仍已准入，重试后同步成功；Apollo请求数不因同步重试增加。
- 覆盖：I-5

### A-4：人工保护回归
- 前置条件：运行前后分别用人工审核通过/暂缓一人，另有已联系/APPLICATION/SBIR样本。
- 操作步骤：执行重审并应用；查看步骤1、7、8与当前列表。
- 预期结果：保护原因明确；人工、联系、申报、SBIR记录不被覆盖；未通过建议也不会暗撤旧批准。
- 覆盖：I-1/3/5

人工验收开始后，才从本节导出同名前缀 `-acceptance.md`；机器通过不代表人工验收完成。
