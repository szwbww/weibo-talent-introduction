# 02 DSN永久退信与收件地址无效分离

状态：待批准。01可先独立上线；本阶段完成后SMTP即时失败仍待03处理，不能宣称全链路修复。
基线、全量存储调用点见[总计划](bounce-repair-master.md)、[代码回执](bounce-repair-code-audit.md)。本文件替代先前10文件混合草案。

## 需求描述

- O-1：5.7.1/5.4.1/5.2.2/5.0.0等永久退信保留HARD，但不再仅据HARD标专家为EMAIL_INVALID。
- O-2：完整识别5.1.10，不截断；明确收件地址错误才进入现有状态写入口；只读对账采用同一DSN判据，已有回复/材料/邀请事实不被退信遮盖。
- 必须不变：MIME优先、原消息/账号/专家归因与去重、HARD统计、公共已回信保护、ES同步表示、人工覆盖和COMPLETED保护、历史实际状态不自动改写。
- 不做：重建归因算法、多收件人DSN模型、历史码回填、SMTP即时失败分支（03负责）、报告分桶重构、ES mapping迁移、新枚举/新表。

## 关键不变量

### Invariant I-1: 永久失败不是地址判据
- Rule: 仍按现有协议分类保留HARD/SOFT；不能为减少告警把政策/路由/邮箱容量的5xx改成SOFT。保存BounceRecord之后，仅HARD且原有归因取得contact且I-2成立时，才调用markEmailInvalid。未知码只记录退信。
- Applies to: BounceDetector、BounceCollectionService.ingest及其全部入口。
- Violation consequence: 退信统计失真或继续错误标状态。
- 来源: K-permanent-failure-not-invalid-address。

### Invariant I-2: 精确、保守的地址证据
- Rule: 唯一允许码集合为5.1.1、5.1.2、5.1.3、5.1.10，依据IANA的目的邮箱/目的系统/目的地址语法/Null MX语义；不是所有5.1.x。5.1.7/5.1.8是发件地址问题，必须拒绝。5.1.6虽有地址停用语义，本轮不扩白名单；未知/无增强码/冲突码均不自动封禁。
- Applies to: 新RecipientAddressFailureClassifier、ingest、对账DSN集合。
- Violation consequence: 把发件方配置问题标到专家，或按前缀误认未知码。
- 来源: original；[IANA注册表](https://www.iana.org/assignments/smtp-enhanced-status-codes/)。
- 输入契约: `isInvalidDsnStatus(value: String?): Boolean`只消费dsn_status字段。允许trim后的完整增强码，或既有格式“5xx + 空白/连字符 + 增强码”；如5.1.1、550-5.1.1、550换行空白5.1.1。必须匹配整个字段，不能扫描任意退信正文、reason或URL。多个状态/尾随解释/残缺字段返回false。
- 新helper为无依赖Kotlin object；不新增Spring构造参数或供应商规则。

### Invariant I-3: 完整增强码与解析次序
- Rule: class一位，subject/detail各1～3位；边界禁止把5.1.100、5.1.1000、5.1.10.1、15.1.1截为5.1.1/5.1.10。BounceDetector的STATUS_PATTERN、DSN_STATUS_PATTERN、HARD_SMTP_CODE_PATTERN一并修边界。MIME Status优先于启发式；inputStream fallback保持；原5.1.1/4.2.2行为保持。
- Applies to: BounceDetector.parseBounceDetails/detect及对应测试。
- Violation consequence: helper即使精确匹配，也会被上游截断后的错误数据误导。
- 来源: K-mime-dsn-before-heuristic / K-mime-dsn-content-handler-absent；实际单字符regex证据。
- 历史限制: 已经存成5.1.1的数据无法由新parser辨别原来是否5.1.10；不臆造历史原文。

### Invariant I-4: 状态保护与只读推导
- Rule: 在线继续调用现有markEmailInvalid，REPLIED/MATERIALS_RECEIVED/INVITED/COMPLETED不回退，已有EMAIL_INVALID不重复写；不产生CHANGE_OPERATOR_STATUS审计。对账先计算现有最高通信里程碑；若达REPLIED及以上，优先该里程碑；否则才用合格地址退信（或暂存的旧SMTP条件）推导EMAIL_INVALID，再回退CONTACTED/NOT_CONTACTED。不从DB既有EMAIL_INVALID反推其证据。
- Applies to: BounceCollectionService、OperatorStatusReconcileService.deriveExpectedStatus。
- Violation consequence: 在线保护了专家，对账却持续建议EMAIL_INVALID。
- 来源: K-operator-status-single-writer；ExpertOperatorStatusService:78-91，已复核。
- 精确边界: 对账仍是事实重算；若DB写着REPLIED但没有入站/材料/邀请事实，不能许诺对账一定一致，保留差异供人工查证。人工覆盖单列及COMPLETED既有豁免不变；对账不写DB/ES。

### Invariant I-5: 原有数据路径不扩张
- Rule: 保存字段、dedupeKey、账号归因、originalContact选择算法保持不变；无contact仍保存HARD并计告警，不写专家。重复bounce不触发第二次状态写入。本次没有历史更新/重新摄取/批量ES同步。
- Applies to: ingest、backfill调用、上线。
- Violation consequence: 新归因错误或历史被隐式重写。
- 来源: K-bounce-attribution-prefer-contact-id / K-bounce-record-has-no-foreign-key / K-bounce-collection-ingest-entrypoints。
- 已知边界: resolveOriginalContact:292实际为outboundCandidates.firstOrNull后备邮箱alias；本计划不把它描述为唯一关联保证。

## 现状审计

### bounce_record → expert_contact → ES
- schema：V29/V43；dsn_status VARCHAR(20)，消息ID唯一，无外键。BounceCollectionService:135去重、:140查原始外发、:149保存、:164只按HARD更新状态。写入口ingest接收collectBounces、AutoMailReplyService UID分支、BounceBackfillService；不分别复制规则。
- 完整读路径：BounceController、BounceRateMonitorService、MailMonitoringService、MailRecordRepository联合监控、OperatorStatusReconcileService。只有对账的DSN期望值读取改变；计数/列表仍读取原HARD。
- expert_contact.operator_status：V19 VARCHAR(32)默认NOT_CONTACTED；公共三个写方法及ManualInitialOutreachService旁路、其它完整保存/局部更新见总计划与回执。本阶段只改ingest调用条件，不改字段表示、其它writer或旧SMTP写者。
- ES三层dynamic=false；candidate/application声明keyword，RAW本地模板没有operatorStatus；已有syncOperatorStatus向三层同步，NOT_CONTACTED删除字段。保留这条调用链，不新增writer、不更改mapping。mapping现场预检见总计划。
- IP-1：Detector输出 → 保存dsn_status → 地址判据 → 公共DB/ES写入；计数和列表继续消费HARD。
- IP-2：各在线/回填入口共享ingest，重复记录必须保持幂等。

### parser与对账
- BounceDetector:174调用STATUS_PATTERN.find；:245目前regex为单字符三段。parseBounceDetails:46-79优先mimeDsn；fallback在findDeliveryStatusBody中，现有测试有序列化往返覆盖。
- OperatorStatusReconcileService:73将所有HARD放入集合；:185抢先返回EMAIL_INVALID，:189-209才计算已发/回复/附件/邀请。与markEmailInvalid的已回信保护不同。
- 对账读取mail_record、bounce_record、mail_attachment、operator_action_log、expert_contact及ES；schema/所有调用点见总计划审计。资料附件写入和人工审计不变；本阶段仅修改DSN集合和derive优先级。
- IP-3：同一存储DSN → 在线状态与只读expected一致；入站/附件/邀请 → expected不再被DSN掩盖；人工记录 → HUMAN_OVERRIDE保持。
- 原有SMTP PERMANENT集合仍在本阶段保留，03再收紧。对账计数重叠问题明确延期，不据consistent总和宣称一致。
- 旧知识中的“只读不报告”“四桶互斥”不符源码，已按实际修正文档，不扩大产品改动。（来源: K-operator-status-reconcile）

## 实现方案

1. I-2/I-3：先在BounceDetectorTest新增5.1.10 MIME序列化往返、文本Status、SMTP文本、多位未知码和4.2.2回归；只修三个regex的长度/边界，不重写MIME或归因。
2. I-2：新增RecipientAddressFailureClassifier.kt和对应测试，提供上述纯函数。DSN字段整串匹配；不调用网络、不读专家信息、不记录邮箱敏感内容。
3. I-1/I-2/I-4/I-5：BounceCollectionService.kt在保存之后的原HARD条件加helper；reason使用准确的地址证据表述即可，不修改BounceRecord或账号绑定。BounceCollectionServiceTest覆盖有/无证据、无contact、去重、收信/已知逻辑账号入口；保留原sender归因测试。
4. I-2/I-4：OperatorStatusReconcileService.kt只收集HARD且helper=true的DSN contact id；derive先算里程碑，再按I-4顺序返回，避免早退。测试新增正反矩阵和REPLIED/MATERIALS_RECEIVED/INVITED+合格HARD组合；人工覆盖/COMPLETED/零写入保持。
5. I-1～I-5：执行单测和既有回填、状态守卫、材料流程回归。本阶段不改三个服务的构造签名，因此不扩大构造调用点修改。

## 变更文件清单

| 文件 | 用途 |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/mail/service/RecipientAddressFailureClassifier.kt | 新增DSN纯判据 |
| src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceDetector.kt | 完整增强码 |
| src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt | 地址证据门槛 |
| src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt | DSN判据/里程碑优先 |
| src/test/kotlin/com/weibo/talentintroduction/mail/service/RecipientAddressFailureClassifierTest.kt | 新增矩阵 |
| src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceDetectorTest.kt | MIME/文本边界 |
| src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionServiceTest.kt | 持久化/状态副作用 |
| src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt | 对账与保护 |

共8文件，mail与campaign两个子系统，无持久化新字段。

## 验收标准

- I-1：5.7.1/5.4.1/5.2.2/5.0.0均save(HARD)，不调用markEmailInvalid；5.1.1有contact则调用一次；SOFT即使携带异常5.1.1也不得标记。
- I-2：白名单四码纯格式/550前缀格式为true；null、空、550、5.1.5/5.1.6/5.1.7/5.1.8/5.1.100、多个码、正文/URL嵌码为false；不以reason补证据。
- I-3：原始MIME经writeTo再MimeMessage读取，dsnStatus精确为5.1.10，HARD；4.2.2仍SOFT；5.1.100完整保留但helper=false；非法长度/追加段不匹配成白名单；MIME5.7.1与正文5.1.1冲突时用MIME5.7.1。
- I-4：在线实际公共服务对REPLIED及更高状态不写；对账有INBOUND+5.1.1预期REPLIED、有材料预期MATERIALS_RECEIVED、有已发邀请预期INVITED。CONTACTED+5.1.1预期EMAIL_INVALID；CONTACTED+5.7.1且有已发首封仍CONTACTED。核对人工覆盖/COMPLETED与零DB/ES写入。
- I-5：dedupe不重复写，孤儿保留，原senderAccountCode/originalExpertContactId/failedRecipient不变；零DDL、零历史UPDATE、无新sync调用。
- 跨IP-1/2/3：Detector→ingest写入的同一DSN供统计、列表和reconcile读取，测试不能手工把所有fixture都预写成新正确值。
- 命令：`mvn -DskipNodeTests=true -Dtest=RecipientAddressFailureClassifierTest,BounceDetectorTest,BounceCollectionServiceTest,BounceBackfillServiceTest,OperatorStatusReconcileServiceTest,ExpertOperatorStatusServiceTest,OperatorStatusWriteSeamGuardTest,ManualExpertMaterialUploadFlowTest test`。已有测试文件不在白名单时只运行不编辑；若发现必须改设计，先修计划。

## 人工验收清单

共用准备：隔离测试IMAP账号与应用绑定；UI创建专用联系人，测试库为其插入OUTBOUND/INTRODUCTION/SENT记录，message_id为唯一`test-bounce-<编号>@example.test`、时间为当前。用邮件客户端导入multipart/report、report-type=delivery-status的测试.eml到该测试收件箱；每封使用不同Message-ID，delivery-status部分填写Final-Recipient为测试联系人邮箱、Original-Message-ID为上述记录、Action: failed、Status为各场景指定码。运行现有收信任务；不触发真实专家发送。对账通过POST /api/experts/reconcile-operator-status，等待任务完成并查看逐人样本。

### A-1: 政策拒收
- 前置条件: 按共用准备构造CONTACTED联系人，Status:5.7.1；记录账号HARD计数。
- 操作步骤: 1. 导入回执；2. 运行收信；3. 查退信列表/专家详情；4. 运行对账。
- 预期结果: HARD计数+1；dsnStatus=5.7.1；DB/ES仍CONTACTED；expected=CONTACTED；无EMAIL_INVALID写入。
- 覆盖: O-1、I-1/I-2/I-4、IP-1/3。

### A-2: 完整地址错误码
- 前置条件: 两位CONTACTED测试联系人，分别Status:5.1.1和Status:5.1.10。
- 操作步骤: 1. 分别导入/收信；2. 查退信列表/专家详情和ES_source；3. 运行对账。
- 预期结果: 分别保存完整5.1.1与5.1.10，都是HARD；DB与已有可同步的ES文档operatorStatus=EMAIL_INVALID；expected=EMAIL_INVALID；没有人工CHANGE_OPERATOR_STATUS记录。
- 覆盖: O-2、I-2/I-3/I-4、IP-1/3。

### A-3: 已推进/人工覆盖
- 前置条件: 测试联系人已有INBOUND且DB/ES为REPLIED；另一个通过UI人工改为CONTACTED并保留审计；为前者导入5.1.1回执。
- 操作步骤: 1. 收信；2. 查前者详情；3. 对账查看两人；4. 再以COMPLETED联系人重复只读对账。
- 预期结果: 前者保持REPLIED，expected=REPLIED；人工对象归HUMAN_OVERRIDE；COMPLETED不产生DB_VS_EXPECTED异常；对账本身不改任何状态。
- 覆盖: I-4、IP-3、状态保护回归。

### A-4: 去重、无归因与历史不回填
- 前置条件: 一个已入库回执；另一个新5.1.1回执无可匹配原信/邮箱；一位旧EMAIL_INVALID只有5.7.1记录。记录原账号归因及状态。
- 操作步骤: 1. 重放同Message-ID回执；2. 收取无归因回执；3. 运行测试环境退信回填POST /api/mail/bounces/backfill；4. 再查记录/旧联系人。
- 预期结果: 重复回执不多一条；无归因回执保存HARD且计数+1但不修改任意专家；已存记录不改原账号/专家归因；旧EMAIL_INVALID未被恢复。
- 覆盖: I-1/I-5、IP-2、原归因/去重/历史回归。

人工验收开始时导出bounce-address-invalid-separation-acceptance.md；本节为权威来源。
