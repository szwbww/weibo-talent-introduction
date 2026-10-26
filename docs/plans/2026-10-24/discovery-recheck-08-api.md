# 08 · 批量重审的名单冻结、启动与取消接口

日期：2026-10-24。状态：待执行；本文件不证明修复已上线。前置及发布门禁见 [总计划](discovery-recheck-master.md)。基线 HEAD `b161e87464d42b9fa2cd076ef9255b0e3b2b29ab`，须保留工作区已有修改。

## 需求描述

结果：从专家审核固定所选/当前筛选全部页名单，确认后执行；能取消、查看整批及逐人历史。默认仅对照+复用证据，不自动开始付费请求；用户选择应用模式后仍由统一commit决定，不改人工审核入口。

## 关键不变量

### I-1：固定名单且幂等
- 规则：范围仅RAW/discovered；IDS由服务端重新读真实docId；ALL_MATCHING复用ScanService.filter+scroll，保存去重docId及输入、前态revision和证据清单；全部成功才READY和inputClosed。requestKey+spec相同返回原run，不同409。READY确认必须带rosterHash+runRevision。
- 适用：prepare/confirm
- 违反后果：全页漂移、浏览器伪造准入或并发重复批次。
- 来源：需求及下列源码审计

### I-2：只读准备
- 规则：prepare不能initializeAdmission，不调providers、不激活item；新item epoch=runId且RESERVED。只写核验run/item及task审计；步骤表在真正激活后初始化。默认AUDIT+REUSE，ENFORCE只能从用户所选模式及服务器验证生成。
- 适用：controller/service/repository
- 违反后果：仅打开确认框就改业务。
- 来源：需求及下列源码审计

### I-3：取消/失败/重启
- 规则：PREPARING/READY可直接取消；RUNNING→CANCELLING阻止新外调及提交，活动项收敛后CANCELLED。名单准备失败只能FAILED，不允许部分名单启动；重启准备中标INTERRUPTED需新建，运行中冻结名单/lease保持可恢复，不重置HTTP/证据。
- 适用：控制CAS、recovery、worker
- 违反后果：漏执行、重复执行或取消还在晋升。
- 来源：需求及下列源码审计

### I-4：真实状态及权限
- 规则：actor仅会话，所有接口要求登录；名单/详情分页20/50/100；run/taskId来自DB。整批总数等于固定人数，已处理/排队/取消/错误互斥；AUDIT用wouldAdmit而非actualAdmitted，候选同步独立列。
- 适用：API输出/QueryService
- 违反后果：把建议当已入库或泄露名单。
- 来源：需求及下列源码审计

## 现状审计

ReviewController:61现有prepare支持IDS/ALL_MATCHING，但ReviewService:450–500会initializeAdmission且强制NEEDS_REVIEW；不能复用这个写方法作只读重审准备。应仅复用ScanService.scanAll的筛选语义和writer只读profile。TaskExecutionService:138通过onStarted可绑定真实id；task_type是字符串。
05 run.recheck_json为控制权威、item.review_epoch为名单；07 trace为过程，V148仅在应用提交时使用；原文/外部缓存在准备阶段只读取并固定refs。旧prepare/retry仍保持原手工动作，不能改名充当重审。

完整检索回执、共享存储全集及交互矩阵：[证据目录](discovery-recheck-evidence/README.md)。以下列出的写入边界为准，不因引用共享审计而扩大文件范围。

## 实现方案

1. 新DiscoveryRecheckService及Controller（`/api/discovery/rechecks`）：POST `/prepare` body `{requestKey,scope:IDS|ALL_MATCHING,docIds?,filter:{q,issue,decision},mode:AUDIT|ENFORCE,evidenceMode:REUSE|REFRESH}`；服务端固定RAW/discovered，unknown enum/不合规参数400；登录actor来自session。scope IDS最大每次请求100条，超过需ALL_MATCHING或分次，不把100当全任务人数上限。ALL_MATCHING无静默截断。（I-1/2/4）
2. 专用单线程executor，task_type DISCOVERY_RECHECK_PREPARE；异步建立真实task_execution与verification_run，每批500冻结快照；snapshot_json保留现有identityHash/factFingerprint/profile，并只增加reviewInput对象保存expectedAdmissionRevision（未初始化为null，不能写0）、beforeDecision、source refs、原文SHA和基线证据选择/哈希。Worker已有FAIL_ON_UNKNOWN_PROPERTIES=false可继续解析原VerificationSnapshot，新增reviewInput由服务端JsonNode读取，不改ORCID客户端模型；不能把这些控制数据塞入profile。item不激活、不写运行步骤。run.spec/specHash冻结，config由现有ApolloSettings.freeze与当前rule版本写run.settings；以规范docId顺序和identity/fact/revision/evidenceHash计算rosterHash。扫描完整后closeInput/READY，否则FAILED。（I-1/2）
3. POST `/{runId}/start` body `{rosterHash,revision}`：事务中CAS READY→RUNNING并激活已冻结items，幂等重复start返回当前run；过期hash/revision409。先核查worker enabled/API配置：不满足返回409明确码且保持READY。真正外调由原Worker，不创建第二套worker。（I-1/3）
4. POST `/{runId}/cancel` 设置控制状态并取消未提交项；POST `/{runId}/sync-retry` body `{itemIds}`只允许已准入且projection ERROR/PENDING；错误重审用新prepare、沿用所选失败名单及新requestKey，不覆盖旧item。已完成同步操作幂等。（I-3）
5. GET `/runs?page&size&docId?`、GET `/latest?docIds=...`（≤100个真实docId批量最新轮）、`/{id}`、`/{id}/items?page&size&state`、`/{id}/items/{itemId}/steps`；校验item所属run；每项返回before/proposed/current/applied、8步、blockingStep、allReasons、versions、source refs。QueryService接07的TraceRepository只读DTO；GET不创建任务、不重算外部证据。GET `/{id}/manifest`分页返回固定名单；不在一个result_summary里塞全量姓名和论文文本。（I-4）
6. Repository新增独立prepare/control CAS。requestKey幂等登记采用短事务、同一JDBC连接GET_LOCK("discovery_recheck_prepare",1)，取锁后当前读按JSON_EXTRACT(recheck_json,'$.spec.requestKey')精确绑定参数查重；创建run/spec完成COMMIT后在同连接RELEASE_LOCK，finally释放。仿照现有Apollo预算锁的事务同步模式（Repository:488–520），不得只靠进程mutex，不在持锁期间扫描ES或调用provider；未拿锁返回409/PREPARE_BUSY。task创建与run绑定在该登记阶段完成，若task已创建而run失败将task标FAILED，不留可执行孤儿。新查询只接受UUID参数，不能LIKE拼用户文本。重启恢复在Service定时入口将未关闭输入且prepare task已INTERRUPTED者标INTERRUPTED；RUNNING由原lease续跑；run完成条件从item终态+投影独立状态计算。（I-1/3/4）

## 变更文件清单

仅下列 9 个文件。新增文件明确标注；其余文件不得顺带修改。

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryRecheckController.kt` | 新增6类操作/分页接口 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt` | 新增准备/启动/取消协调 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` | prepare/control/manifest查询CAS |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` | 新run列表/指标/步骤DTO |
| `src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt` | 名单固定专用executor |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryRecheckControllerTest.kt` | 新增会话/参数/状态码 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckServiceTest.kt` | 新增默认、幂等、范围 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepositoryIT.kt` | 控制/并发/恢复真实库测试 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckFlowIT.kt` | 新增跨保存→worker→gate→投影合同 |

## 验收标准

- I-1：筛选1001人跨3批冻结1001，不受前端当前20条影响；同requestKey/spec唯一run，不同spec409；部分失败不可READY；固定后新增RAW不加入旧run。
- I-2：prepare和AUDIT全程业务表/ES写0（仅审计表可变）；默认mode=AUDIT/evidenceMode=REUSE；在READY前HTTP0。
- I-3：取消与lease/commit的真实事务竞态；重启不将PREPARING当完成；RUNNING恢复仍同rosterHash/requestCount；sync-retry不走providers。
- I-4：未登录401；跨run/item404；未知枚举400；计数互斥/建议与实际分离；任务prepare SUCCESS不当核验COMPLETED。

## 人工验收清单

### A-1：所有页固定
- 前置条件：测试筛选匹配1001人，当前页20。
- 操作步骤：prepare ALL_MATCHING；READY后插入第1002人；start带返回hash/revision。
- 预期结果：固定名单1001、执行1001；后插入者不混入；prepare未发外部请求。
- 覆盖：I-1/2

### A-2：默认仅对照
- 前置条件：取两名待审核者，保留准入和三层资料摘要。
- 操作步骤：不改默认选项prepare/start，再取manifest和steps。
- 预期结果：AUDIT/REUSE、固定2人、每人8步；业务摘要不变；wouldAdmit与actualAdmitted分别显示。
- 覆盖：I-2/4

### A-3：重启与取消
- 前置条件：1001人批次，分别在准备中、执行中重启测试服务。
- 操作步骤：读取原run并尝试启动/取消。
- 预期结果：准备中断者INTERRUPTED且不能start；运行者按原名单恢复；取消后未提交项不再准入，已准入不回滚。
- 覆盖：I-3

### A-4：权限和旧人工审核
- 前置条件：未登录浏览器与已登录浏览器各一；人工待审核样本一人。
- 操作步骤：未登录请求全部新接口；登录后用原人工通过，再对同人重审。
- 预期结果：未登录401；人工通过仍可用；重审不覆盖人工结论、步骤显示REVIEW_PROTECTED。
- 覆盖：I-4/2

人工验收开始后，才从本节导出同名前缀 `-acceptance.md`；机器通过不代表人工验收完成。
