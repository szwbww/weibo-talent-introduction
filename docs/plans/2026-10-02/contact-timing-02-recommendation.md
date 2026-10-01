# 02：根据回复时间计算北京时间推荐窗口

依赖：01 已通过验证、总计划 G-0 满足。范围：8 个文件，推荐计算后台 1 个子系统。

## 需求描述

O-1：有所在地但回复不足时返回当地 08:00–17:00 对应的北京时间。
O-2：回复分布积累后返回常回复的连续两小时窗口；新回复、绑定旧回复和时间流逝在下次读取时影响结果。
O-3：同时返回所在地、实际日期、当地时间、样本数与回复日数，支持解释推荐依据。

必须保持 N-1：读取推荐不收取新邮件、不发信、不修改处理状态/草稿/专家画像；01 配置接口与国家默认语义保持。
范围外：按发送时间训练因果模型、预测回复概率、机器回复分类器、持久化模型、定时重算、缓存失效体系、工作日/节假日模型。

## 关键不变量

### Invariant I-1: 样本来源、身份和范围
- Rule: 只读指定 contactId 的 inbound_mail_processing；账号集合复用 findAllByAccountCodeNot(SIMULATOR_ACCOUNT_CODE) 的现有口径。不读 INBOUND mail_record；不读正文。按真实 message_id/物理身份去重；已处理/待处理不作为学习资格门槛。
- Applies to: service 查询与去重；calculator 输入构造。
- Violation consequence: 两张表重复计数、其他专家或模拟器污染结果、人工处理一次被算作新增回复。
- 来源: K-mailbox-inbound-source-authority、K-inbound-processing-write-paths。

### Invariant I-2: 时间必须先变成 Instant
- Rule: imp.received_at 按 Asia/Shanghai 解释，再转目标 ZoneId 的当地日期/半小时桶。按实际 Instant 计算年龄；禁止把 DATETIME 当 UTC 或用固定国家 UTC 偏移代替时区规则。
- Applies to: JDBC 映射、去重后样本转换、工作时间与峰值窗口转换、API DTO。
- Violation consequence: 北京/当地时刻错位，夏令时或半小时区错误。
- 来源: original；TimeZoneConfig:11、ImapMailReceiveService:339–343。

### Invariant I-3: 未配置、冷启动、习惯模式三种结果
- Rule: 无配置：location.configured=false、recommendation=null，不能猜。去重后的当地回复日期数 D<3：mode=WORK_HOURS，完整 08:00–17:00。D>=3：mode=REPLY_PATTERN，按下文唯一公式选连续四桶，允许跨午夜、允许工作时间外。
- Applies to: calculator、service DTO 装配、前端消费者。
- Violation consequence: 一天连回十封被当作稳定习惯，或冷启动被压成任意两小时。
- 来源: original。

### Invariant I-4: 上限与衰减可复现
- Rule: 最近 180×24 小时，排除未来时间；最多最近 1000 行参与后续去重/计算，多取第 1001 行仅判断截断。sampleCount/replyDayCount 为实际去重条数/当地日期数，recentSamples 最多 8 条，historyDays 固定180，historyTruncated 如实表示上限。日权重、衰减、平滑、先验、平局规则固定如下。相同 now/配置/记录输入返回相同输出。
- Applies to: 查询、纯计算函数、解释 DTO。
- Violation consequence: 无界查询、排序不稳定或验收无法复现。
- 来源: original；K-mailbox-groupwise-latest-mysql-compat。

### Invariant I-5: 推荐是下一段完整可用窗口
- Rule: 使用专家当地今天的候选起点；若其 Instant 已早于 now，则候选日期加一天。北京时间和当地端点均携带完整日期与 offset；跨日不丢日期；DST 缺口顺延、重叠选较早 offset，遵循 Java LocalDateTime.atZone 语义。
- Applies to: calculator 生成区间、DTO 输出。
- Violation consequence: 推荐已过去的时间、用户把跨日区间理解反了。
- 来源: original。

### Invariant I-6: 读取没有业务副作用
- Rule: timing GET 只读配置、账号和小投影；不写模型/邮件/状态/缓存表、不调用 IMAP/SMTP/ES/LLM。保存配置仍只走 01 的 PUT。
- Applies to: 新控制器 GET、新服务 timing 方法。
- Violation consequence: 打开详情触发邮件动作或改变现有业务。
- 来源: original。

## 现状审计

### inbound_mail_processing：本计划只新增 reader

- Schema：V5 的 received_at DATETIME NOT NULL、expert_contact_id 可空 FK、message_id 可空；V10 增加正文/人工解决字段；V14/15 增加并回填 reason_type；V120 的 uid_validity 默认 0 只表示历史未知；V134 增加 nullable mailbox_owner_code 及物理唯一键。对应片段与迁移命中保存在证据。
- 新建写入：AutoMailReplyService.confirmManualReviewWithBody:1308、confirmProcessed:1364；confirmManualReview 委派后者。receivedAt 均原样保存收到邮件的 receivedAt。
- 现有行改写：UnmatchedInboundMailService.bindToContact:193 改绑定/状态；markResolved:238 改处理状态；PendingMailOperationService.markResolved:1524 改状态；reopen:1595 调 InboundMailProcessingRepository.reopenManualResolved:50–62 条件 UPDATE。这些 copy/UPDATE 不产生新的接收时刻。
- 迁移写入：V10/V14/V15/V120/V134 的结构、状态/原因历史回填；没有为本推荐新增任何历史回填。完整命中见 grep-receipts 的 inbound-store/inbound-writers。
- 读路径闭包：InboundMailProcessingRepository 的派生查询与 SQL 被 AutoMailReplyService、UnmatchedInboundMailService、PendingMailOperationService、MailboxService、MailMonitoringService、InboundMailTagService、AutoReplyPreviewService、MeetingConfirmationService、BounceBackfillService、ExpertContactManagementService、TrustReplyWorkbenchService、RagReplyController、InboundMailSummaryController、AiTrainingController 消费；额外 SQL reader 为 MailboxConversationRepository、MailRecordRepository、InboundMailTagRepository、ExpertMaterialService、ExpertRepliedDismissalService。字段涉及接收时间/关联 contact/处理状态/正文/标签附件身份，完整调用逐行列于 inbound-readers 与 inbound-store 证据。本计划不改变这些 reader。
- IP-1：两个新建 sink→timing GET 纳入新回复。
- IP-2：人工绑定未匹配记录→timing GET 纳入原收到时间；人工解决/撤销解决→不能改变样本身份和时间。
- IP-3：同一来信可能另有 INBOUND mail_record→只计 processing；历史共享邮箱重复→按下面的身份去重。

### 时区与账号配置

TimeZoneConfig:11 强制 JVM 默认上海；ImapMailReceiveService:339–343 将 receivedDate 转系统当地时间再存 LocalDateTime。它在 receivedDate 缺失时回退接收处理当下；持久层没有“邮件头时间是否可信”的标志，因此不能承诺全部时间都是专家点击发送的时刻。

MailboxConversationService:529–531 的 activeAccountCodes 实际只排除模拟器，没有 enabled=true 条件。本功能沿相同业务账号集合，不擅自再过滤停用账号的历史回复。01 的新配置表没有其他 writer；IP-4：修改 country/zone → 下一 GET 以新时区重投影同一批 Instant。

当前系统在自动收信循环 AutoMailReplyService:873–905 将识别到的 bounce/DMARC 分流；这不能证明每一条 linked processing 都是人工回复。没有审计到可直接使用的永久“人工回复”字段。首版依据文案必须写“已关联来信”，不新增猜测式正文关键词过滤；已关联的自动回复仍可能影响样本，这是明确的数据局限。

### 性能与框架

01 的窄 JDBC 服务可直接承载新投影查询。按 contact+时间筛选、ORDER BY received_at DESC,id DESC LIMIT 1001；不使用窗口函数、不遍历正文、不为每个会话列表项请求。首先在隔离 MySQL 记录 EXPLAIN 与样本查询耗时；本轮不预加索引。若实际数据证明必须新增索引，先做有证据的范围修订，不能把旧迁移改写。（来源: K-mailbox-groupwise-latest-mysql-compat）

## 实现方案

### T-1：读取与投影（I-1/I-2/I-4/I-6）

文件：ExpertContactLocationService.kt、ExpertContactLocationModels.kt、ExpertContactLocationServiceIT.kt。

读取 01 配置；无配置立即返回 recommendation=null。账号集合通过 MailSenderAccountRepository.findAllByAccountCodeNot 获取。集合为空则零样本，仍可返回默认工作时间。查询只投影 `id,message_id,mailbox_owner_code,uid_validity,imap_uid,received_at`：

```sql
SELECT id, message_id, mailbox_owner_code, uid_validity, imap_uid, received_at
FROM inbound_mail_processing
WHERE expert_contact_id = :contactId
  AND sender_account_code IN (:accountCodes)
  AND received_at >= :fromBeijing
  AND received_at <= :nowBeijing
ORDER BY received_at DESC, id DESC
LIMIT 1001
```

- fromBeijing = now.minus(180, DAYS) 转上海 LocalDateTime；nowBeijing 同理。
- 多于 1000 行时先留排序前 1000 条，historyTruncated=true；解释“仅使用最近 1000 条范围内的去重来信”。
- 去重先按非空 trim(message_id) 精确匹配（不改大小写）；保留排序中第一条。再按有效物理键 `(owner非空,uidValidity>0,imapUid)` 去重；身份不完整且 messageId 空的只能用 row id，不能把未知 owner/代际猜成当前值。
- 不按 process_status/process_reason 过滤：这些字段会被人工解决改写；也不按相同正文猜重复。重复识别有可用身份时才合并。
- sampleCount 是去重后的实际条数；replyDayCount 是目标时区日期去重数。recentSamples 只返回最新 8 个时间（receivedAtBeijing、receivedAtLocal，完整 ISO offset date-time），不返回正文、主题、邮箱或内部 message-id。

### T-2：纯计算器（I-2/I-3/I-4/I-5）

文件：ReplyTimeRecommender.kt、ReplyTimeRecommenderTest.kt、ExpertContactLocationModels.kt。

函数输入 `samples: List<Instant>, zone: ZoneId, now: Instant`，不访问数据库/系统时钟/网络。服务在每个请求开始获取一次 Instant.now() 并传入，测试直接传固定 now；不引入 Clock Bean。

1. 一天 48 桶，`bin=hour*2+minute/30`，依据每个样本发生时目标 ZoneId 的真实当地时间。
2. 同一当地日 d 有 N_d 条样本，每条权重 `w_i=min(1,2/N_d) * 2^(-ageDays_i/30)`，ageDays 为两 Instant 相差秒数 / 86400。日上限 2 在衰减前施加，避免一日密集对话压倒长期规律。
3. H[b] 为桶权重和。循环平滑 `S[b]=0.2*H[(b+47)%48]+0.6*H[b]+0.2*H[(b+1)%48]`，再归一化为 P。
4. 工作时间先验 U：桶 16..33 各为 1/18，其余 0。日期有效量 `E=sum_d(max_{i in d}(2^(-ageDays_i/30)))`；混合系数 `alpha=min(0.9,E/(E+2))`。最终 Q=`alpha*P+(1-alpha)*U`。E 随时间下降，使陈旧样本影响逐渐减弱；回复频次在日上限内影响 H。
5. D<3 直接输出完整 08:00–17:00，mode=WORK_HOURS；D>=3 对 48 个起点求连续四桶 Q 之和。比较容差 1e-12，平局选择较早的当地起始分钟，mode=REPLY_PATTERN。不把结果裁回 08:00–17:00。
6. 窗口端点先作为目标 ZoneId 的当地日期/时间构造；四桶跨 48 时结束日期+1。冷启动起止 08:00/17:00。以当地今天试算，起点早于 now 就加一天；DST 缺口按 atZone 顺延、重叠用较早 offset。端点 Instant 必须递增；极端日期转换不能形成正区间时移到下一当地日期再试（最多 3 天，仍失败返回明确计算错误，不伪造区间）。
7. 同一对 Instant 分别转专家时区与 Asia/Shanghai 输出。日期和 DST 标准化后的当地端点必须使用转换结果，不用原字符串伪装。

这是首版衰减直方图与工作时间先验，不是已验证的最优发送策略；参数常量集中在此文件，不提供用户配置面板。

### T-3：只读接口与响应（I-1–I-6）

文件：ExpertContactLocationController.kt、ExpertContactLocationService.kt、ExpertContactLocationModels.kt、对应 controller/service 测试。

新增 `GET /api/mail/contact-locations/{contactId}/timing`；沿 01 的 404 和既有登录边界。返回：

```text
{
  location: ContactLocationView,
  recommendation: null | {
    mode: "WORK_HOURS" | "REPLY_PATTERN",
    localStart: ISO_OFFSET_DATE_TIME,
    localEnd: ISO_OFFSET_DATE_TIME,
    beijingStart: ISO_OFFSET_DATE_TIME,
    beijingEnd: ISO_OFFSET_DATE_TIME,
    sampleCount: integer,
    replyDayCount: integer,
    historyDays: 180,
    historyTruncated: boolean,
    recentSamples: [{receivedAtBeijing, receivedAtLocal}],
    calculatedAt: ISO_INSTANT
  }
}
```

mode/source 只保留上述一套语义，不再增加 confidence、preferredHour、bestHour 等同义字段。当地时区 id 在 location.effectiveZoneId 中；初始工作时间也是“下一完整窗口”。01 的 PUT 响应不改，前端 PUT 成功后再 GET timing。

### T-4：跨路径测试（I-1–I-6）

扩展 01 IT 的 fixture 和验证，不新建一套数据库框架。覆盖：processing + 对应 INBOUND mail_record 仅计一次；两个业务账号同 message-id 仅一次；模拟器不进入；历史 owner=null/uidValidity=0 不凭空合并；人工绑定增加样本、resolve/reopen 不增加；换时区重新归日；第 1001 行截断；180 天外/未来排除。

## 变更文件清单

| # | 路径 | 操作 |
|---:|---|---|
| 1 | src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationModels.kt | 扩展 01 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationService.kt | 扩展 01 |
| 3 | src/main/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactLocationController.kt | 扩展 01 |
| 4 | src/main/kotlin/com/weibo/talentintroduction/mail/service/ReplyTimeRecommender.kt | 新增 |
| 5 | src/test/kotlin/com/weibo/talentintroduction/mail/service/ReplyTimeRecommenderTest.kt | 新增 |
| 6 | src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationServiceTest.kt | 扩展 01 |
| 7 | src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactLocationControllerTest.kt | 扩展 01 |
| 8 | src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationServiceIT.kt | 扩展 01 |

## 验收标准

- I-1：真实 SQL fixture 验证来源与去重；SELECT 没有 body/subject，状态变化前后 sampleCount 相同，别的 contact 不进入。
- I-2：固定 now=2026-10-02T00:00:00Z：印度零样本 local 2026-10-02 08:00–17:00，对应北京 10:30–19:30；纽约零样本当天北京 20:00–次日05:00。另测 2026-12-01 纽约为21:00–次日06:00，Nepal :45、Lord_Howe 半小时 DST 与欧洲切换日。
- I-3：0/1/2 个当地回复日完整工作时间；同一天 20 封仍 WORK_HOURS；3 天各 14:15 的 BR 样本稳定选13:00–15:00（平局取早），北京次日00:00–02:00。持续多日23点附近的样本可越出工作时间且跨午夜。
- I-4：同输入逐字段一致；同日100封的总未衰减权重=2；30天衰减为1/2；较新的多日夜间样本能逐渐覆盖旧白天样本；全部样本老化时 E 下降。1001 行标记截断；边界恰好180天包含，早1秒排除，未来排除。
- I-5：起点不早于 now，端点有日期/offset；跨北京午夜和跨专家午夜分别覆盖；DST gap/overlap 不产生倒置或零长度区间；样本依据按样本当时规则转换。
- I-6：mock 验证 timing 无业务 writer 调用；IT 读取前后配置、邮件和状态行不变；01 API 全部回归通过。
- IP-1..4 在 IT 中形成数据写入/改绑定→GET 的跨路径断言，不只测试 calculator 自己的公式。
- JVM 单测：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ReplyTimeRecommenderTest,ExpertContactLocationServiceTest,ExpertContactLocationControllerTest`。
- MySQL IT：使用 01 的独立数据库命令执行 ExpertContactLocationServiceIT；记录 EXPLAIN、1000 行结果耗时。不能以 mock 或 MySQL8窗口函数测试替代 5.7 兼容查询。

人工 fixture（仅隔离测试库，C 是无历史来信的测试联系人，A 是真实测试业务账号，二者通过测试会话列表/账号配置取得）：

```sql
-- 用实际测试值替换两处占位；不在生产库执行。
SET @contact_id = <C>;
SET @account_code = '<A>';
INSERT INTO inbound_mail_processing
(sender_account_code, uid_validity, imap_uid, message_id, from_email,
 received_at, process_status, process_reason, expert_contact_id)
SELECT @account_code, 0, 920000000+n,
 CONCAT('contact-timing-fixture-', @contact_id, '-', n), 'timing-fixture@example.org',
 TIMESTAMP(DATE_SUB(UTC_DATE(), INTERVAL n DAY), '01:15:00'),
 'MANUAL_REVIEW', 'TIMING_TEST_FIXTURE', @contact_id
FROM (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) d;
```

这是历史未知物理身份测试行（owner=null、uidValidity=0），不是给生产收件写入0的许可。该批北京01:15对应巴西前一天14:15。测试前保证 UID/message-id 未占用，测试后只删除该测试 contact 的这个 fixture 前缀，不清空整表。

## 人工验收清单

### A-1: 未配置与初始工作时间
- 前置条件: 隔离环境登录；无来信测试联系人 C，无人工所在地记录。
- 操作步骤: 1. GET `/api/mail/contact-locations/C/timing`。2. PUT C 配置 IN/null。3. 再 GET timing。
- 预期结果: 第1步 configured=false、recommendation=null；第3步 mode=WORK_HOURS、sampleCount=0、replyDayCount=0，当地08:00–17:00、北京10:30–19:30；端点有完整日期，起点尚未过去。
- 覆盖: O-1/O-3；I-2/I-3/I-5；IP-4。

### A-2: 学习、处理状态与绑定旧回复
- 前置条件: 独立库中的 C 无原来信；配置 BR/null；按上方 SQL 先插入 n=1、2 两行，第3行先令 expert_contact_id=NULL。
- 操作步骤: 1. GET timing。2. 用现有“待匹配”界面把第3封绑定到 C。3. GET timing。4. 在现有待处理界面标记其中一封已处理，再撤销处理，分别 GET timing。
- 预期结果: 第1步 sampleCount=2、replyDayCount=2、WORK_HOURS；第3步3/3、REPLY_PATTERN，当地13:00–15:00、北京次日00:00–02:00；第4步两次仍3/3且同一 now 对应的峰值不变。
- 覆盖: O-2/O-3、N-1；I-1/I-3/I-4/I-6；IP-1/IP-2。

### A-3: 来源隔离与重新投影
- 前置条件: C 使用 A-2 的3封；IT fixture 另创建同 message-id 的另一业务账号 processing，以及一条对应的 INBOUND mail_record；只在隔离库准备。
- 操作步骤: 1. GET timing。2. PUT C 为 BR/America/Manaus。3. 再 GET timing，查看 recentSamples。4. 刷新01的配置GET。
- 预期结果: 去重后仍3条；配置改为Manaus且usingDefaultZone=false；每封当地时间由14:15变为13:15，北京时间仍01:15；配置GET仍返回已选时区；读取不增加发件或修改邮件状态。
- 覆盖: O-2/O-3、N-1；I-1/I-2/I-6；IP-3/IP-4。
