# 03 — 统一 SMTP 注入与成功邮件关联

状态：待实施。依赖01的预留/API与02的回复位/清理helper；默认关闭可独立部署。不要先上线03再补02。

## 需求描述
- O-1：全局开启时，所有符合条件的非回复业务外发携带一个图片像素；批量与专家页共用，重复非回复发送也各自跟踪。
- O-2：成功外发与跟踪行准确关联，查询页能显示其打开信号；跟踪预留故障不阻断原本可以发送的邮件。
- 不改变：N-1 抑制名单、原发送资格/去重/额度/错误分类/重试；N-2 原正文审计、纯文本、线程头、退订头、附件字节；N-3 实际回复不带新像素，账号自检保持原样。
- 范围外：放开现有禁止重复发送的业务规则、回填历史、改 Message-ID 工厂、改回复头、增加发信重试或补偿任务。

## 关键不变量
### Invariant I-1: 全部非回复而非首封
- Rule: eligible = NOT(isReply OR 非空inReplyTo OR 非空references OR 主题开头回复前缀)。不限制mailType/触发入口/专家既往发送次数；全局开关与有效单一收件人/配置另由01预留检查。
- Applies to: SmtpMailDeliveryService唯一业务出口
- Violation consequence: 漏掉非介绍邮件、第二封新信或混入真回复。
- 来源: original
### Invariant I-2: 像素只进入投递副本
- Rule: 先清理旧的本系统img，eligible且预留成功才插一个新img；生成wireMail副本，禁止修改调用者mail和业务持久化body/text；只有HTML部件含新像素，plain保持原文本。
- Applies to: send无附件/混合附件分支、originalBodyPart
- Violation consequence: 审计/预览触发打开或指纹被随机token污染。
- 来源: K-mail-body-display-sites、K-plaintext-reply-client-reflow
### Invariant I-3: 单次发送与独立预留
- Rule: 抑制闸门在预留和SMTP资源之前；使用01已独立提交的reserve；任何跟踪准备失败恢复到清理后未注入正文继续原发送，绝不因跟踪错误重复调用sender.send。
- Applies to: SmtpMailDeliveryService.send
- Violation consequence: 绕过抑制、非业务故障阻断投递或重复发送。
- 来源: K-smtp-idempotency-reservation-before-delivery
### Invariant I-4: 只有成功结果关联
- Rule: DeliveredMail尾部openTrackingId:Long?=null；仅sender.send正常返回的SENT带预留id；FAILED/SKIPPED/异常不带。SMTP接受后不再新增跟踪DB操作。成功id经各业务原save链透传，不能用Message-ID反查。
- Applies to: DeliveredMail、ManualOutreachTxHelper/两调用者、ManualExpert、Meeting
- Violation consequence: 串信、假成功、跟踪写失败导致SMTP重发。
- 来源: K-mail-record-save-sites
### Invariant I-5: 原MIME契约不变
- Rule: 保持收件人、From、Message-ID、In-Reply-To、References、List-Unsubscribe/POST、附件顺序/类型/字节。需要跟踪的plain-only邮件才在wire copy增加HTML alternative；关闭时无旧像素的邮件结构与原来一致。
- Applies to: send两种MIME分支
- Violation consequence: 附件损坏、线程错位或关闭仍改变投递。
- 来源: original

## 现状审计
- [审计](mail-open-tracking-audit.md) §1～3、§5、§7；原始 send-paths、mail-record-writes、constructors-tests 为全集凭据。
- SmtpMailDeliveryService:17 单出口；:19抑制；:46与:64两种结构；:110实际发送；:111成功；异常沿原分类器返回。
- SMTP接收前01写新表；成功后现有mail_record写入通过新增ID读取它。写表点：ManualExpert、Meeting、ManualOutreachTxHelper成功分支；调用者InitialOutreach/ManualInitialOutreach要透传。失败和回复写方保持null。
- IP-1：reserve→SMTP→DeliveredMail→四条非回复业务路径→mail_record→列表；IP-2：图片可能早到→独立预留聚合→成功关联后统计；IP-3：wire copy与业务原文/attempt指纹分离。
- 当前 introduction 已有 html/text。之前知识条目“intro纯文本”为过时事实；不得照旧知识重改composer。账号自检独立send不接入。

## 实现方案
### T1 — 排除判断和wire copy（I-1/2/3/5）
文件：`SmtpMailDeliveryService.kt`。
- 注入必需 MailOpenTrackingService；生产依赖不做可空测试后门。现有构造点集中在该 SMTP test，统一更新。
- reply prefix在本文件私有常量，Kotlin raw string regex为 `(?i)^\s*(?:re(?:\[\d+\])?|回复|答复)\s*[:：]`。Re/re/RE:、Re[2]:、回复：算回复；Regarding、主题中间Re:不算；Fwd单独不排除，真实回复上下文仍排除。
- 原抑制判断后创建清理过的本地copy；若符合eligible，调用service预留，捕获跟踪异常但不吞原有SMTP/收件资格异常。失败日志不输出token、密码或完整正文。
- HTML在最后一个不分大小写 `</body>` 前插入，无body结束标签则追加。唯一像素：`<img data-mail-open-tracking="1" src="{escapedUrl}" width="1" height="1" alt="">`；URL属性转义；不使用display:none。
- 原html=false：wire副本html=true，body=plainTextToHtml(original.body)+img，text=original.body；原html=true：text沿原值/原回退；清理旧img后追加。本文不改变生成HTML的业务composer。
- 两种MIME分支统一用wireMail的body/html/text；原附件/calendar保持相同引用与内容。不要漏 `originalBodyPart(wireMail)`。
- reserve异常/URL构造失败/像素注入失败：不带id，不注入新像素，原业务继续；不新增“跟踪失败→重新发送”链路。

### T2 — 关联透传（I-4）
文件：`MailDeliveryService.kt`、`ManualExpertMailService.kt`、`MeetingScheduleService.kt`、`ManualOutreachTxHelper.kt`、`InitialOutreachService.kt`、`ManualInitialOutreachService.kt`。
- DTO尾部默认null，原分类器与原调用兼容。
- ManualExpert/Meeting在构造MailRecord时赋 `openTrackingId = delivered.openTrackingId`，并防御只在SENT时使用；不改原sentAt/status/body。
- helper.recordSuccess末尾加 `openTrackingId:Long?=null`，只成功save写入；删除七参数兼容重载，两个outreach调用者统一以命名参数透传（包括null）。recordFailure签名不改。taskExecutionId保持原值。迁移既有Mockito调用验证，不留下条件分支或兼容路径。
- 不向 Auto/ManualReplySendAttempt 回复保存链透传；02明确isReply+SMTP测试保证回复输出id恒null。
- SMTP成功后由已有业务事务持久化关联。既有“SMTP已接受但业务记录失败”的窗口仍存在，本期不能声称exactly-once；孤儿token不进入指标，也不为修复它新建重发任务。

### T3 — 实际发送链测试（I-1～5）
文件：`SmtpMailDeliveryServiceTest.kt`、新增 `MailOpenTrackingPersistenceTest.kt`、既有 `InitialOutreachServiceTest.kt` 和 `ManualInitialOutreachServiceTest.kt`（迁移recordSuccess的七参数Mockito验证）。
- SMTP mock只替代网络，捕获真实MimeMessage、递归解析部件验证HTML/plain/附件；预留mock为null/id/抛错，验证send调用恰一次或被抑制时零次。
- 表驱动覆盖回复位/两种非空头/主题regex正反例；plain、html、calendar、通用附件、合并附件；开关关闭、未配置、抑制、SMTP失败与普通成功。
- 新持久化test实际调用四种业务发送入口（Initial、ManualInitial、ManualExpert含批量、Meeting），mock外部依赖但捕获真实repository.save。outreach使用实际helper，不能只验证mock helper收到参数就声称已落记录。可采用既有对应service test的最小fixture，不启动真实SMTP/ES。
- 记录成功ID、失败null、回复null、连续两次非回复分别ID、body/text不带token。01真实MySQL IT负责SQL关联/早到请求，03不重复搭数据库平台。

## 变更文件清单
共11文件，两个子系统：MIME投递；业务成功记录关联。经人工批准，本阶段文件上限由10调整为11，仅新增两份既有调用测试。

| # | 文件 |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailDeliveryService.kt` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt` |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt` |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt` |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt` |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailOpenTrackingPersistenceTest.kt` |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt` |
| 11 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` |

## 验收标准
- I-1：按入口、主题、头、reply位的正反矩阵；第二封非回复仍eligible；现有业务去重仍生效，不为了测试解除去重。
- I-2：每封eligible HTML恰一img、plain无token；原mail对象/落库body保持原值；引用旧img不重复保留。
- I-3：suppressed=0次reserve/SMTP；预留失败=1次SMTP、ID null；无额外send重试；01真实代理事务测试通过。
- I-4：四条路径save关联断言；失败/回复null；SMTP成功后跟踪repository零额外调用；列表由真实关联读取，不用Message-ID。
- I-5：捕获MIME验证原头及附件字节、顺序、类型；关闭且无旧img时原结构保持；既有SMTP测试全绿。
- JDK11：`mvn test -Dtest=SmtpMailDeliveryServiceTest,MailOpenTrackingPersistenceTest,InitialOutreachServiceTest,ManualInitialOutreachServiceTest,ManualExpertMailServiceTest,MeetingScheduleServiceTest -DskipNodeTests=true`。上述既有测试类已由rg确认存在；执行前复核漂移，不把未运行算通过。

## 人工验收清单
### A-1: 两类入口与非回复范围
- 前置条件: 01～03测试实例开启；准备自己的两个测试联系人，允许按既有规则手动发送。
- 操作步骤: 1. 用批量介绍入口向测试邮箱发送。2. 专家页发送介绍。3. 再用专家页发送允许的非回复模板。4. 打开图片后读跟踪API。
- 预期结果: 三条成功记录均各有关联；加载图片的记录为OPENED；第二次非回复不因“不是首封”被排除。
- 覆盖: O-1/O-2、I-1/I-4、IP-1/IP-2
### A-2: 关闭与回复
- 前置条件: 开启下准备一封测试来信；另保留一封可非回复外发。
- 操作步骤: 1. 回信并删除Re前缀。2. 关闭开关后发非回复。3. 查看原始邮件与跟踪API。
- 预期结果: 两封均无新像素；查询均NOT_TRACKED；原有已打开记录保留。
- 覆盖: N-3、I-1/I-2/I-5、IP-1
### A-3: 故障隔离
- 前置条件: 独立测试实例使用只允许读取而不允许INSERT跟踪表的测试数据库账号；SMTP测试邮箱可用。
- 操作步骤: 1. 发送非回复。2. 查看收到邮件和API。3. 对抑制名单测试地址尝试原发送入口，不启用override。
- 预期结果: 预留失败时仍只收1封且无像素；列表该信NOT_TRACKED；抑制地址仍被原规则拒绝。
- 覆盖: O-2、N-1、I-3/I-4、IP-1
### A-4: MIME与审计回归
- 前置条件: 准备测试模板、自己的邮箱、一份小附件和日历邀约。
- 操作步骤: 1. 发送非回复HTML测试信。2. 回信附上文件/日历。3. 查看原始邮件和系统历史。
- 预期结果: 非回复仅HTML有像素，纯文本无；回复附件原件可用；线程/退订头仍存在；历史正文不含新token。
- 覆盖: N-2/N-3、I-2/I-5、IP-3
