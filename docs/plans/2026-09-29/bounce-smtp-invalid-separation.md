# 03 SMTP即时失败：地址判据与状态写入口收口

状态：待批准；严格依赖02已验证。02创建的helper和对账文件只能在其完成后继续编辑。
基线/完整调用点见[总计划](bounce-repair-master.md)、[代码回执](bounce-repair-code-audit.md)。

## 需求描述

- O-1：首封SMTP永久失败继续记录FAILED，但只有明确收件地址错误才写EMAIL_INVALID；实时与对账使用相同持久化摘要作判断。
- O-2：保留原本“永久失败不自动再发”的效果，不能因去掉EMAIL_INVALID而让历史未绑定NEW联系人反复发送。
- 必须不变：失败记录/发送尝试/失败计数、额度、限流和基础设施异常处理、绑定/已发送去重、已回信保护、人工覆盖、对账只读；不恢复旧状态。
- 不做：一般重试状态机、新抑制表、新终态、新错误枚举、SMTP异常链重写、实际历史恢复、其它手动邮件/回复发送器改造。

## 关键不变量

### Invariant I-1: 使用相同、可重放的证据
- Rule: 在线先取得buildSmtpErrorSummary已有结果；判定与recordFailure传入同一个errorSummary。对账读取已存mail_record.error_summary，必须同时满足OUTBOUND+INTRODUCTION+FAILED，再用同一helper。不可在线读500字符errorDetail而对账只读200字符摘要。
- Applies to: ManualInitialOutreachService永久失败分支、RecipientAddressFailureClassifier、OperatorStatusReconcileService。
- Violation consequence: 在线与历史重算相互矛盾。
- 来源: original；buildSmtpErrorSummary:1261-1266、ManualOutreachTxHelper:99-143。

### Invariant I-2: SMTP解析保守且有界
- Rule: helper新增`isInvalidPermanentSummary(summary: String?): Boolean`。必须匹配`PERMANENT:<500..599>:`前缀，详情中只接受行首可带空白的完整增强码，或行首三位SMTP码+空白/连字符+完整增强码。若带SMTP码必须与摘要三位码一致；不能从任意正文/URL/异常说明中搜索嵌入码。
- Applies to: helper及在线/对账两调用点。
- Violation consequence: 从无关文字得出地址无效结论。
- 来源: original；复用02 I-2白名单及完整码边界。
- 多行契约: 提取上述可信协议行；不同增强码冲突、4xx增强码、无可信码均false；重复同一码允许。额外不含协议码的说明行不作为证据。
- 截断契约: 详情达到现有200字符截断上限时保守返回false，避免尾部5.1.10被截成5.1.1。不改变摘要存储格式或放大字段。这是明确保守策略，允许漏掉自动无效标记，不允许凭截断推断。
- 典型值: `PERMANENT:550:550 5.1.1 User unknown` true；`PERMANENT:550:550 5.7.1 Blocked` false；`PERMANENT:550:user unknown` false。只有550不能证明收件地址错误。

### Invariant I-3: 公共状态入口与失败事实独立
- Rule: PERMANENT分支始终先recordFailure，再按I-1/I-2决定是否调用ExpertOperatorStatusService.markEmailInvalid；删除该分支直接save(operatorStatus=EMAIL_INVALID)和直接ES sync。其余失败计数、验证记录FAILED、stat.failed、roundRejected不变。公共入口REPLIED及以上保护、无人工审计、ES同步语义不变。
- Applies to: ManualInitialOutreachService及构造测试。
- Violation consequence: 失败数变少、状态回退或再出现第二套ES写逻辑。
- 来源: K-operator-status-single-writer / K-operator-status-write-seam-guard。

### Invariant I-4: 永久首封不自动重发
- Rule: 对引擎当前处理的contact，只要已有OUTBOUND+INTRODUCTION+FAILED且errorSummary以PERMANENT:开头，无论是否能证明地址无效，都不重新尝试首封。NEW重试构造与最终SMTP前检查共用同一事实谓词；UNKNOWN/TRANSIENT不冒充PERMANENT。保留已绑定/已发首封门禁。仅约束本引擎的首封，不替用户发起新的显式人工重发，也不重构其它邮件类型。
- Applies to: buildRetryableTargets与run中的最终contact检查，包含ES页重新出现的相同contact。
- Violation consequence: 政策拒信取消误标后被反复重试。
- 来源: K-batch-send-filter-retry-parity；本次代码新发现（见审计）。
- 存储规则: 复用现有findAllByExpertContactIdOrderByCreatedAtAsc，不新增查询/表/状态；尽量一次读取列表供SENT和PERMANENT检查，不制造每记录N次查询。
- 结果规则: retry集合排除；最终门禁用既有SEND_EXCEPTION原因码和“历史首封永久失败，需人工处理”说明记录skipped，不计本次failed/sent；若已经PASS验证，则记录NOT_SENT；不更新发送尝试、不调用SMTP、不增加额度。原有进度计数分支成对维护。

### Invariant I-5: 只读对账与历史边界
- Rule: 只改permanentFailureContactIds集合条件，保留02里的DSN白名单和里程碑优先级；人工覆盖和COMPLETED原豁免不变；不写DB/ES、不执行历史恢复。既有EMAIL_INVALID若无合格证据可以出现差异，不自动变状态。
- Applies to: OperatorStatusReconcileService、上线。
- Violation consequence: 修一条路径却重引另一条误判，或隐式恢复真实无效地址。
- 来源: K-operator-status-reconcile。

## 现状审计

### SMTP写路径及存储
- ManualInitialOutreachService:999构建errorSummary，:1001永久分支，:1002先recordFailure，:1008直接contact.copy(EMAIL_INVALID)，:1011直接ES同步。此前没有增强码检查，且绕过公共已回信保护。
- SmtpErrorClassifier将500..599分类PERMANENT，errorDetail取顶层e.message最多500字符；buildSmtpErrorSummary再取200。不能假设所有错误都有完整增强码。此处保持协议类别不变。
- ManualOutreachTxHelper.recordFailure:112写mail_record(OUTBOUND/INTRODUCTION/FAILED、sent_at=null、原errorSummary、mailSendAttemptId)，:138更新attempt FAILED。V23/V24约束及全部writer/readers见总计划。IP-1：在线失败记录 → 对账读取，使用同一个摘要，不更改格式。
- expert_contact / ES schema及所有写读点同02；03消除永久失败旁路，其它初始化/完整保存/同步不动。IP-2：永久失败 → 公共状态入口 → DB/ES、人工列表、后续筛选。

### 重试门禁证据
- :727先检查绑定；:862的`existingContact ?: run { ... }`只有新建contact才填boundSenderAccountCode(:873)。复用的历史NEW contact可能仍未绑定。
- :879仅检查hasSentIntroduction；:1380筛选仅“无SENT且不是EMAIL_INVALID”，:1396才排除已绑定ORCID；因此单纯删除EMAIL_INVALID副作用，会放行无绑定且只有永久失败的NEW联系人。此结论由分支条件证明，不声称线上已发生重复发送。
- IP-3：recordFailure持久化PERMANENT → 下一轮NEW构造/ES页最终检查；需I-4保留终止效果，不靠错误的EMAIL_INVALID继续挡发送。
- ES估算countEsTargets当前本来是候选估算，不保证精确扣除每个contact门禁；本计划不重构估算，但最终SMTP前必须阻止重发，NEW计数与NEW执行共用构造函数。
- 旧InitialOutreachService先检查campaign+orcid存在就跳过，失败不在该服务写EMAIL_INVALID；不改此路径。

### 构造与守卫
- 生产由Spring注入；显式构造只有ManualInitialOutreachServiceTest:122、BatchSendTaskRuntimeIntegrationTest:701、MailOpenTrackingPersistenceTest:180（回执已全文搜索）。
- helper采用object，不给BounceCollectionService/OperatorStatusReconcileService增加构造参数。
- OperatorStatusWriteSeamGuardTest白名单按文件而非写语句计数。ManualInitialOutreachService仍有NOT_CONTACTED初始化，删除永久失败旁路不要求修改守卫白名单。
- 不能删expertIndexWriterService整个依赖：服务还有其它既有调用，修改仅限永久失败分支。

## 实现方案

1. I-1/I-2：扩展02的helper及其test，追加SMTP摘要入口；纯DSN方法行为不变。补前缀、跨行、冲突、无增强码、发件地址错误码、200字符截断正反测试。
2. I-1/I-3：ManualInitialOutreachService新增必需的ExpertOperatorStatusService构造依赖，永久失败分支按helper调用公共入口；保持recordFailure顺序与所有失败/验证计数。
3. I-4：同文件新增窄的永久首封事实判据；在NEW重试筛选和contact确定后的原SENT去重检查旁接入（写PREPARED/发SMTP之前）。复用同批mail records；不碰账号绑定、不新增错误原因枚举。send前保护即使目标来自ES页也要经过。
4. I-1/I-5：OperatorStatusReconcileService的首封永久失败集合增加helper；其Test用真实格式的PERMANENT:550:550 5.1.1替换旧“user unknown即可”的正例，再新增5.7.1/null/截断反例；保留02全部回归。
5. I-3/I-4：三个显式构造测试点补依赖。ManualInitialOutreachServiceTest建议使用真实ExpertOperatorStatusService+mock repo/audit/ES，以原DB/ES断言覆盖公共保护；另两文件只做构造适配及原集成回归。新增两次run测试必须让repo第二次返回第一次实际捕获的失败行和contact，不能手工伪造EMAIL_INVALID来“证明”不会重发。
6. I-1～I-5：针对性测试、全量test、守卫；通过后才整体人工验收。

## 变更文件清单

| 文件 | 用途 |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/mail/service/RecipientAddressFailureClassifier.kt | 追加SMTP摘要判据 |
| src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt | 公共状态写入口/窄重发门禁 |
| src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt | SMTP同源判据 |
| src/test/kotlin/com/weibo/talentintroduction/mail/service/RecipientAddressFailureClassifierTest.kt | SMTP矩阵/截断 |
| src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt | 失败/状态/双次执行 |
| src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt | 同源重算 |
| src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt | 构造适配/运行回归 |
| src/test/kotlin/com/weibo/talentintroduction/mail/service/MailOpenTrackingPersistenceTest.kt | 构造适配/跟踪回归 |

共8文件，mail/campaign两个子系统。与02共享helper、helper test、对账、对账test四文件；必须串行。没有更改MailRecordRepository、TxHelper、枚举或mapping。

## 验收标准

- I-1：capture recordFailure传入的errorSummary，作为对账mail_record fixture输入；在线判断与对账同值同结论。仅首封OUTBOUND FAILED有效，回复失败/INBOUND/SENT同文本不能标无效。
- I-2：白名单四码+匹配SMTP回复头true；5.7.1/5.4.1/5.2.2/5.0.0/5.1.7/5.1.8/纯550/无码false；200字符详情false（包括恰好截断在5.1.1处），199字符完整协议码按白名单判断；相互矛盾码false。
- I-3：5.7.1和5.1.1两场景failed各+1、sent=0，均recordFailure并保留attempt FAILED；只有后者对低阶段状态调用公共无效写入；REPLIED/MATERIALS_RECEIVED/INVITED/COMPLETED不降级；自动写入不新增人工审计。421/452限流、其他TRANSIENT、INFRASTRUCTURE现有测试不退化。
- I-4：新建绑定contact和历史未绑定NEW contact分别以5.7.1失败两次run；第二次实际SMTP调用累计仍1、失败记录仍1；ES页再次出现该contact也不能绕过。TRANSIENT失败未被新谓词当永久阻断；SENT去重/绑定保持；不新增额度或PREPARED更新；进度守恒，已PASS但跳过为NOT_SENT。
- I-5：对账5.7.1首封失败且无通信里程碑期望NOT_CONTACTED，不是EMAIL_INVALID；5.1.1为EMAIL_INVALID；已有回复仍REPLIED；humanOverride/COMPLETED/零写入通过。
- 命令：`mvn -DskipNodeTests=true -Dtest=RecipientAddressFailureClassifierTest,ManualInitialOutreachServiceTest,OperatorStatusReconcileServiceTest,BatchSendTaskRuntimeIntegrationTest,MailOpenTrackingPersistenceTest,ExpertOperatorStatusServiceTest,OperatorStatusWriteSeamGuardTest test`；随后JDK11环境`mvn test`。外部依赖测试只能使用隔离资源，缺环境报未验证，不用生产补跑。

## 人工验收清单

共同准备：隔离环境使用可指定SMTP回复的本地测试邮件服务，关闭真实外发及测试账号以外的自动任务；由UI创建专用账号/批次、导入只含测试地址的专家；关闭该测试批次的外部邮箱验证或让测试地址明确通过。配置仅用于测试，不上线真实专家。

### A-1: 政策拒绝
- 前置条件: 测试专家初始NOT_CONTACTED；SMTP对其返回550 5.7.1。
- 操作步骤: 1. 运行单人首封批次；2. 查看任务结果、邮件记录、专家详情；3. POST /api/experts/reconcile-operator-status。
- 预期结果: failed=1、sent=0；邮件FAILED、摘要PERMANENT:550:开头、attempt FAILED；专家仍NOT_CONTACTED；expected=NOT_CONTACTED；不新增人工状态审计。
- 覆盖: O-1、I-1/I-2/I-3/I-5、IP-1/2。

### A-2: 地址错误与无证据
- 前置条件: 两位测试专家分别返回550 5.1.1 User unknown、550 User unknown（无增强码）。
- 操作步骤: 1. 运行两人批次；2. 查看记录、DB/ES详情；3. 运行对账。
- 预期结果: failed=2、sent=0；前者EMAIL_INVALID，后者NOT_CONTACTED；两者都保留永久失败；对账分别同值，不能仅凭550/英文描述判无效。
- 覆盖: O-1、I-1/I-2/I-3/I-5、IP-1/2。

### A-3: 未绑定NEW不得反复发
- 前置条件: 测试库创建current_status=NEW、operator_status=NOT_CONTACTED、bound_sender_account_code=NULL且无历史邮件的测试contact；其profile满足当前批次筛选；SMTP返回550 5.7.1。另有新建、自动绑定的同类测试联系人。
- 操作步骤: 1. 执行首封批次一次；2. 查看失败记录；3. 同条件再执行；4. 即使该联系人仍在ES候选页，检查SMTP测试服务接收日志和attempt。
- 预期结果: 两人首次各失败1次；第二次每人新增SMTP调用=0、新FAILED记录=0、无新PREPARED、无额度增长；未绑定联系人仍不被伪标EMAIL_INVALID，绑定联系人绑定不变。
- 覆盖: O-2、I-4、IP-3、原有绑定/发送去重。

### A-4: 回归与历史无写入
- 前置条件: 测试SMTP分别配置421 4.7.0、认证失败；另有已REPLIED测试联系人、一个旧EMAIL_INVALID且只有5.7.1记录、一个人工状态覆盖对象。
- 操作步骤: 1. 执行隔离异常批次；2. 查限流/暂停结果；3. 对已REPLIED者运行包含合格地址失败事实的只读对账；4. 检查历史对象状态。
- 预期结果: 421仍走限流、认证故障仍走原基础设施处理；REPLIED不被新无效写入口降级，对账有入站事实时expected=REPLIED；历史EMAIL_INVALID实际值不变；人工对象归HUMAN_OVERRIDE；对账不更新DB/ES。
- 覆盖: I-3/I-5、原有异常处理/保护/人工覆盖/历史边界。

人工验收开始时导出bounce-smtp-invalid-separation-acceptance.md；清单错误先修本节再导出。
