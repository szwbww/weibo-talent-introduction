# 02：会话查询授权过滤与只读投影

状态：待审阅。依赖：01机器验证通过。后续：03。子系统：邮件查询（1）；实施文件6个。仍不创建账号或开放user1登录。

## 需求描述

user1会话列表只包含获授权专家，详情及更早页均重新校验权限，返回正文而无附件入口。保留admin的列表筛选、排序、游标、历史邮箱范围及完整附件投影；不改收发信写入、ES mapping或草稿流程。

## 关键不变量

### Invariant I-1: 过滤先于分页
- Rule: 对user1，count/page/explain共同的expertPredicates在所有OR条件之外追加grant EXISTS AND。不得先取全量页再filter；total只计有邮件且授权的专家。grant空集即total=0/items=[]。
- Applies to: Repository.countConversations/pageConversations/explainConversationsPage、Service派生元数据读取。
- Violation consequence: 总数泄露、稀疏分页、搜索/筛选绕过。
- 来源: original；K-plan-quantified-claims-need-grep-receipts。

### Invariant I-2: 每次详情都准入
- Rule: Controller从session传身份给Service；user1在联系人存在检查、正文/标签/附件查询前执行hasReadAccess；无grant或专家不存在统一403/MAIL_READ_FORBIDDEN，不泄露存在性。每次before/accountCode变化都检查；游标继续绑定专家和邮箱。
- Applies to: messages Controller、listMessages、Repository准入查询。
- Violation consequence: 直接换contactId/游标绕过；撤销后仍有后续读取。
- 来源: original。

### Invariant I-3: 附件能力从响应与入口同时关闭
- Rule: reader响应固定attachmentCount=0、firstAttachmentNames=[]、calendarAttachment=null、outboundAttachments=[]；跳过resolveMessageAttachments及只用于附件的发件快照读取。正文作为邮件文本允许；不读取附件文件内容或生成附件URL。01拦截器仍禁止真实附件接口。
- Applies to: listMessages DTO投影；01对应入口策略。
- Violation consequence: 前端藏入口但响应仍带附件信息/下载能力。
- 来源: original。

### Invariant I-4: 管理员与现有内部调用不改变
- Rule: 非user1保留原SQL/字段/排序/附件解析；新方法身份参数追加且默认null，保持既有内部调用/测试兼容，但HTTP Controller必须显式传session。activeAccountCodes仍排除SIMULATOR_NOOP并保留disabled历史邮箱。只读不得新增任何持久写。
- Applies to: Controller/Service/Repository与现有调用点。
- Violation consequence: 改坏附件发送测试、管理员时间线或历史计数。
- 来源: K-sender-account-enabled-scope；K-line-number-guard-breaks-on-any-insertion。

## 现状审计

共同审计的邮件读取、附件、测试章节及证据02/08/10/11均属本节。具体切点：Repository:150/171/213共享expertPredicates:678；timeline:482参数目前无身份；Service:149–166只按当前页取派生资料，:227先查联系人，:273调用附件解析；Controller:265 messages未接收session。

- **grant表**：01 migration及GrantService PUT/DELETE是全部新写路径；本步仅新增Repository EXISTS和hasReadAccess读路径，无缓存。
- **邮件/来信/联系人主表**：既有schema和调用清单见10/11；现有发信/来信处理/联系人服务写入不改。ACL只改变user1读取范围；不会把授权状态写进expert_contact、follow、mail_record或ES。
- **附件关系/文件**：现有ExpertMaterialService.resolveMessageAttachments:707读取附件/桥接关系；本步user1不调用，admin保留。磁盘文件不改、不搬迁。
- **交叉路径**：admin授予→count/page/详情；撤销→新详情403；收件/发件既有写入→授权专家出现新邮件；账号停用→历史邮件仍可读。
- **构造调用**：08中OutboundAttachmentFlowTest、MailReplyDraftSendIntegrationTest手工new现有对象。保留Controller/Service构造参数，避免跨子系统扩大修改。

## 实现方案

1. **SQL成员过滤（I-1/I-4）**：仅改MailboxConversationRepository，在expertPredicates内按固定policy追加下列条件；共用现有`:username`绑定，严禁字符串插入用户输入：

```sql
EXISTS (
    SELECT 1 FROM expert_mail_read_access emra
    WHERE emra.username = :username
      AND emra.expert_contact_id = u.expert_contact_id
)
```

count/page/explain已有共同入口，本步不复制三份SQL。q、pending/replied等OR必须完整括号后再AND ACL。新增同Repository的`hasReadAccess(username,contactId):Boolean`参数化EXISTS读取，不注入新构造依赖。

2. **详情准入（I-2/I-4）**：Controller.messages追加HttpServletRequest，并调用`listMessages(..., username=sessionUsername(request))`。Service方法末尾新增`username:String?=null`；user1先检查grant再做原有findById/activeCodes/decodeKeyset/timeline。空grant一律抛01权限异常，不走404。Repository.timelineMessages保持既有签名和内部语义，本次唯一HTTP调用必须经Service准入；用检索02验明未新增绕行HTTP入口。
3. **只读投影（I-3/I-4）**：Service按server-side isReader分支：跳过附件解析；只有admin继续获取用于calendar/outbound附件的mailRecordRowsById；reader DTO按I-3固定空值。保留正文、主题、方向、时间、消息来源及发送状态，既有标签只读信息不扩大到未授权专家。列表材料计数可保留为已有授权专家元数据，reader前端不展示材料入口；不能据此开放材料API。
4. **撤销并发语义（I-1/I-2）**：以每次请求的grant读取准入为界，撤销前已准入的请求可能完成；撤销提交后开始的新请求必须拒绝。count/page非原子快照下授权同时变化，允许一次total与items瞬时不一致，但items绝不能包含其SQL查询时未授权专家；刷新修正。不添加缓存/推送/锁表。
5. **测试（I-1–I-4）**：扩展RepositoryIT真实MySQL、多专家交错授权≥两页；扩展ControllerTest session传参/403；新MailboxReadAccessProjectionTest覆盖Service先准入、附件依赖零交互及admin完整投影。方法追加默认参数仍须修正mock参数匹配，不能跳过原测试。

## 变更文件清单

|#|路径|动作|
|---|---|---|
|1|src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt|SQL过滤及grant读取|
|2|src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt|详情准入/只读投影|
|3|src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt|传session身份|
|4|src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt|真实SQL ACL场景|
|5|src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt|MVC/参数传递回归|
|6|src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxReadAccessProjectionTest.kt|新增Service投影/准入测试|

## 验收标准

- I-1：真实MySQL至少45名有邮件专家、其中23名交错授权；size20返回20+3，total23，页间无重复；空授权total0；q/subject/keyword/account/direction/date及现有状态筛选与ACL同时成立；explain保留主键EXISTS路径，无应用层filter替代。普通admin基线结果不变。
- I-2：直接请求未授权/不存在contact均403；篡改username参数无效；对已授权A使用B游标被原游标校验拒绝；撤销后首次/更早页/刷新均403。mock验证未授权时联系人、timeline、标签、附件查询未发生。
- I-3：带来件附件/已发通用附件/ICS的fixture，user1响应四个附件字段满足固定空值；附件解析与快照仓库零交互；admin仍返回原附件对象，且01直接附件接口拒绝继续通过。
- I-4：旧排序同秒source_rank/id、cursor、历史邮箱、时间线正文不重复等原IT全部运行；Auth之外的定时业务无新增policy调用；未改数据库生产者或行号锁定文件。
- 命令：Java11环境 `mvn -Dtest=MailboxReadAccessProjectionTest,OutboundAttachmentFlowTest test`；隔离MySQL `mvn -Pmysql-it -Dtest=MailboxConversationRepositoryIT,MailboxConversationControllerTest,CalendarAttachmentIntegrationTest,MailReplyDraftSendIntegrationTest,ExpertMailReadAccessIT test`。禁止把默认跳过IT当SQL验证通过。

## 人工验收清单

### A-1: SQL分页与新邮件
- 前置条件: 隔离库45名有邮件专家，其中23名由admin调用01接口授权；测试fixture给user1会话。
- 操作步骤: 1. GET列表size20/page0、page1。2. 用已授权和未授权姓名各搜索。3. 向已授权A追加受控来信并刷新。
- 预期结果: 20/3，total23，无重复；未授权姓名0条；A新增正文出现，未授权新邮件不出现。
- 覆盖: I-1/I-4；grant写、邮件写→列表读。

### A-2: 详情与附件
- 前置条件: A授权、B未授权；A含三类附件；admin可正常下载。
- 操作步骤: 1. user1读A、B和不存在专家。2. 用admin读A。3. admin撤销A，再user1请求A及更早页。
- 预期结果: A200但附件字段0/[]/null/[]；B/不存在403；admin完整附件字段保留；撤销后A新请求403。
- 覆盖: I-2/I-3/I-4。

### A-3: 历史邮箱与后台不变
- 前置条件: A有enabled=false真实邮箱历史邮件和受控发送任务。
- 操作步骤: 1. admin和获授权user1读取A。2. 运行既有受控发送/收信任务。3. 再读时间线。
- 预期结果: 双方可见历史邮件；任务产生原有记录，授权专家新消息可见；邮件状态未因读取而改写。
- 覆盖: I-4。
