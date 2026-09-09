# 01 收发件箱查询、邮件标签投影与主题兼容

状态：待实施。依赖：无；为02提供真实接口。不改前端、不执行生产数据库写入。9文件，两个子系统（会话读取、收信头解码）。

## 需求描述

- R1 全部专家按待处理优先、最近来信倒序分页；关注/待处理按最近来信倒序。
- R2 真实支持专家邮箱与主题/正文关键词筛选，timeline 一次返回当前窗口的来信标签。
- R4 会话列表随本页返回真实专家标签，与邮件标签字段分离，不逐个专家请求画像。
- R3 新来信和已存邮件的合法 MIME 主题在会话摘要/消息/回复预填中可读。
- 必须保留：P1 来源、账号范围、计数和游标；P2 关注与旧waitingReply参数兼容；P3 附件仅读文件信息；P4 所有现有处理/发件业务写服务。
- 不做：DB迁移、ES变更、待回复新状态、全文检索平台、历史subject回填、发件标签、其他页面主题全站重构。

## 关键不变量

### Invariant I-1: 分页前排序
- Rule：`latest_reply_at=MAX(CASE WHEN u.source='INBOUND_PROCESSING' THEN u.event_at END)`，不能用MAX事件/first_reply_at；`pending_count=SUM(u.pending_flag)`。
  全部（!followed && !pendingOnly && !waitingReply）：待处理>0在前，之后NULL来信置底、latest_reply_at DESC、contactId DESC。关注及待处理仅来信倒序和稳定id。分页先GROUP BY、排序再LIMIT，count不受排序改变。
- Applies to：pageConversations与explainConversationsPage，同一私有order片段；page响应顺序不由service/front重排。
- Violation consequence：第2页有待处理却被第1页普通专家压住。
- 来源：用户；K-group-before-pagination（排序指标以本次指令覆盖）。

### Invariant I-2: 全历史聚合与单消息筛选
- Rule：只在激活账号∩选定账号范围聚合；q匹配专家名/邮箱。recipientEmail、keyword、subject兼容字段、方向、日期、label经同一封消息EXISTS限定专家membership，不能使聚合计数/最近来信截断。OR的完整组必须括号包裹再与q/followed做AND。
- Applies to：membership/expertPredicates/count/page/params、controller/service验证。
- Violation consequence：标签/时间缩窄后专家计数错误，或OR绕过关注/搜索。
- 来源：K-mailbox-inbound-source-authority，审计X1/X2/X3。

### Invariant I-3: 标签批量读取与正确来源
- Rule：消息DTO新增 `tags: List<TagView> = emptyList()`；仅当前窗口INBOUND_PROCESSING id集合传 `InboundMailTagService.listTagsBatch` 一次。OUTBOUND tags为空，不以source_inbound_id映射。POST/DELETE沿用既有端点和服务。
- Applies to：listMessages DTO/service。
- Violation consequence：重复拉全历史、标签串信或伪发件标签。
- 来源：K-mail-record-source-inbound-id。

### Invariant I-4: 主题解码有限且不取附件
- Rule：唯一纯函数MailSubjectDecoder.decode(String?):String?；null/普通文本不变；对合法folding先unfold，再MimeUtility.decodeText单次解码；不做HTML unescape，不把解码结果作为HTML，不循环解码。未知charset/损坏输入异常回退原字符串，不阻断收信/会话读取。
- Applies to：Imap.fetchEnvelopeHeaders的Subject赋值；会话service latestMessage.subject、timeline.subject（含历史OUTBOUND）；两者复用同函数。
- Violation consequence：只修新邮件而历史仍乱码，或解码顺手触发正文/附件网络请求。
- 来源：当前Imap :336/:311代码证据。

### Invariant I-5: 零业务状态副作用
- Rule：GET会话只读DB、既有ES专家画像及已有资料元信息；不下载、不标记、不新增状态。新收信仍走原确认sink，唯一行为差异为ReceivedMail.subject解码。旧waitingReply API保留兼容，新UI不使用；不删除业务WAITING_REPLY会话状态。
- Applies to：所有修改文件。
- Violation consequence：误发/误处理或旧客户端失效。
- 来源：原始、K-inbound-seen-not-processed-marker。

### Invariant I-6: 专家标签按当前页和真实层级读取
- Rule：summary新增 `expertTags: List<String>? = null`，与timeline.tags完全分离。标签来自当前专家层级的ES画像tags；当前页contactIds一次findAllById读取层级，再按RAW/CANDIDATE/APPLICATION分组，用既有searchByOrcidIds批量查询，每个非空层最多1次（全页最多3次）。本页为空零查询。不能查全库、不能逐人findByOrcidId，不能用inbound_mail_tag或状态badge代替。
- Applies to：MailboxConversationService.listConversations及summary DTO；当前页所有读取和管理后刷新。
- Violation consequence：列表N+1、把邮件标签误当专家标签、标签与管理页不一致。
- 来源：ExpertSearchService:631批量查询/:494 tags读取/:591 sourceFields含tags；ExpertContact.currentIndexLevel:23；用户补充。

## 现状审计

共同审计 `mailbox-refinement-audit.md` D1..D5 / X1..X5 全文属于本节。核心源定位：repository :157/:169/:209/:478/:503/:516/:554；service :57/:126/:133/:183；controller :44/:67/:108；Imap :311/:336。全部写服务保持原签名与业务。专家标签读取新增审计X8，复用既有ExpertSearchService，不修改ES写服务或schema。

## 实现方案

### T1 查询排序与筛选（I-1/I-2/I-5）

文件：MailboxConversationRepository.kt、MailboxConversationService.kt、MailboxConversationController.kt及既有RepositoryIT/ControllerTest。

- page与explain新增局部SQL投影latest_reply_at，不加表字段、不新增response最近来信字段（已有latestInbound.receivedAt）。保留latest_event_at给现有row映射，排序改为以下合同：

```sql
-- 仅全部使用首项，关注/待处理省略首项
CASE WHEN SUM(u.pending_flag) > 0 THEN 0 ELSE 1 END ASC,
CASE WHEN MAX(CASE WHEN u.source = 'INBOUND_PROCESSING' THEN u.event_at END) IS NULL THEN 1 ELSE 0 END ASC,
MAX(CASE WHEN u.source = 'INBOUND_PROCESSING' THEN u.event_at END) DESC,
u.expert_contact_id DESC
```

- `followed/pendingOnly/waitingReply` 为既有白名单布尔量；不要提供任意orderBy参数。page/explain调用同一order helper，避免诊断SQL漂移。
- 新可选query `recipientEmail`、`keyword`，trim空白→null，长度≤255，超长400；原subject继续只搜主题。日期开始包含、结束次日不包含，开始晚于结束400。
- recipientEmail：出站EXISTS匹配其ec.expert_email，入站EXISTS匹配impi.from_email；keyword分别 `(subject LIKE ... OR cleaned_body LIKE ... OR body LIKE ...)`，NULL列照SQL空值处理。只在EXISTS取正文，summary SELECT不返回全量body。
- 同封邮件必须同时满足所有消息级条件。EXISTS组写成 `(<outbound EXISTS> OR <inbound EXISTS>)` 后再与q/followed AND，不能是散落OR。
- 不用ROW_NUMBER/OVER/CTE，兼容已确认MySQL5.7限制。执行EXPLAIN用同版本隔离库，记录耗时与rows；不为本轮引入索引/迁移。关键词扫描性能若不达现有预算，记录实际测量再修订，不伪称索引覆盖。

### T2 当前窗口标签（I-2/I-3/I-5）

文件：MailboxConversationService.kt、MailboxConversationController.kt、MailboxConversationControllerTest.kt。

- service注入现有InboundMailTagService；listMessages取完page后提取真实processing ids并调用一次listTagsBatch；空集合不请求。
- response新增tags默认空数组；只给INBOUND_PROCESSING消息填结果；summary不读取标签正文/thread，不新增全量资料读取。
- service/page/controller全部消费既有标签写服务。02从返回tags直显，不再每封请求`/thread`。

### T3 MIME主题（I-4/I-5）

文件：新增MailSubjectDecoder.kt；ImapMailReceiveService.kt；MailboxConversationService.kt；新增MailSubjectDecoderTest.kt；ImapMailReceiveServiceTest.kt。

- 用项目已存在的javax.mail.internet.MimeUtility；封装nullable安全的unfold+decodeText，不增加依赖。
- 接收只改读取过的Subject字符串；对话读取对DTO进行同函数解码，旧库不UPDATE。
- 测试：用户截图UTF-8 Q两段、Windows-1252 Q、UTF-8 B、折叠空白、中文/英文普通文本、null/empty、未知charset、损坏encoded-word；确认普通下划线不被非MIME正则误改、HTML字符经前端escape保留文本。
- Imap假Message在getContent/附件流访问时失败的header级用例需证明解码不新增这些访问；不改变原walkContent既有行为。

### T4 本页专家标签投影（I-2/I-5/I-6）

文件：MailboxConversationService.kt、MailboxConversationController.kt、MailboxConversationControllerTest.kt（均在原白名单，文件数不增加）。

- 注入既有ExpertSearchService；取到当前SQL页contactIds后，用既有ExpertContactRepository.findAllById批量读取currentIndexLevel/orcidId，仅本页，不调用findAll全表。
- 按有效currentIndexLevel分组并对非空ORCID去重，调用ExpertSearchService.searchByOrcidIds(orcidIds, level)；用(level,orcidId)匹配结果，再按原SQL rows顺序填expertTags。不得用ES返回顺序替代SQL排序。
- tags trim、去空、去重并保持ES原顺序，返回所有真实标签，不在服务端为了UI截断数组。正常画像无tags返回[]；无ORCID/层级无效/画像缺失/该层查询异常返回null，不能伪称[]证明无标签，不能跨层猜测回退。异常记录已有logger，其他层标签、会话列表仍返回；用户刷新可重试。无需新增业务状态表或缓存。
- 复用searchByOrcidIds现有_source投影（已含tags），本轮不扩大ExpertSearchService修改范围。该方法仅读ES，禁止发现/补全/晋级画像。
- ControllerTest给ExpertSearchService加受控stub/mock，验证一页20专家同层1次、跨三层最多3次、无结果0次、顺序仍SQL、邮件标签与expertTags隔离；某层抛错只该组null、不使整页500。
- 01仍9文件/会话读取+MIME两个子系统；专家标签是会话summary读取的附加投影，不新建独立服务。

## 变更文件清单

| # | 精确路径 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt` | 上述T1/T2/T3范围内修改 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt` | 上述T1/T2/T3范围内修改 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt` | 上述T1/T2/T3范围内修改 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt` | 上述T1/T2/T3范围内修改 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSubjectDecoder.kt` | 新增纯主题解码/测试 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt` | 上述T1/T2/T3范围内修改 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt` | 上述T1/T2/T3范围内修改 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveServiceTest.kt` | 上述T1/T2/T3范围内修改 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSubjectDecoderTest.kt` | 新增纯主题解码/测试 |

## 验收标准

- I-6：本页按层批量查询次数/来源/失败隔离/顺序测试通过；expertTags不含pending/邮件标签，管理修改后下一次summary反映ES结果。

- I-1：真实隔离MySQL种4专家A/B/C/D，A老来信待处理、B新来信已处理、C仅更新发件、D较新来信待处理；size=2，全部分页是D,A / B,C；关注全4时B,D,A,C；待处理D,A。补同时间不同contactId及空来信测试；未发生数据变化时翻页无重复遗漏。
- I-2：一个匹配方向不匹配主题、另一个匹配主题不匹配日期不能联合凑出专家；关键词只出现在正文也命中；入站from_email别名与出站专家邮箱分别测试；q/followed不能被outbound/inbound OR绕过；筛选后pendingCount/latestInbound仍全账号范围聚合。
- I-3：一窗口20封来信只调用1次listTagsBatch；同数值MAIL_RECORD与INBOUND_PROCESSING不串标签；OUTBOUND空tags；POST标签后下一次timeline返回其tagId。
- I-4：上述MIME矩阵逐字assert；旧库fixture保留encoded subject不更新，API输出可读；reply预填在02验证。
- I-5：来源权威、cursor同秒稳定、账号边界、关注权限、waitingReply旧参数原用例仍通过；Imap附件元数据测试通过。

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailSubjectDecoderTest,ImapMailReceiveServiceTest,MailboxConversationControllerTest,MailboxConversationRepositorySqlCompatTest
# 先显式配置隔离库，确认库名不为生产/开发业务库，再运行mysql-it。
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Pmysql-it -Dtest=MailboxConversationRepositoryIT
```

IT当前setUp/cleanup会写删数据；不可沿默认`talent_introduction`业务库直接运行。测试跳过/无可用隔离MySQL需标未验证，不得把H2/mock当MySQL证明。

## 人工验收清单

### A-1: 排序与处理跨路径
- 前置条件：隔离环境准备四位测试专家。A有09-01来信且标为待处理；B有09-09来信且已处理；C只有09-10发件；D有09-08来信且待处理；均同激活账号，关注全部四人。用邮件测试夹具/测试邮箱导入，勿改真实专家。
- 操作步骤：1 请求会话size=2的0/1页；2 请求followed=true；3 请求pendingOnly=true；4 在旧来信处理入口把D最后待处理信标为已处理，再请求全部。
- 预期结果：原全部D,A / B,C；关注B,D,A,C；待处理D,A；D处理后全部A,B,D,C。给C再发一封测试信，C仍最后。
- 覆盖：I-1/I-2/I-5、R1、P1/P2/P4；X1/X2/X4。

### A-2: 真实筛选
- 前置条件：A某封来信正文含唯一词`meeting-z9`，主题不含；from_email为其别名，给这封信加自定义标签“会议安排”，另封信处于不同日期。
- 操作步骤：1 keyword=meeting-z9；2 加recipientEmail别名、正确日期和label；3 改成只覆盖另一封邮件的日期；4 在关注条件加不匹配q。
- 预期结果：1/2命中A；3/4不命中；2中的receivedCount不因过滤只剩1封。
- 覆盖：I-2、R2；X3。

### A-3: 主题历史兼容
- 前置条件：隔离库有截图中的UTF-8 Q及Windows-1252 Q主题记录；另一封测试邮件通过IMAP新进入。
- 操作步骤：1 GET对应会话列表和timeline；2 比对旧记录subject；3 进入02人工回复。
- 预期结果：接口与前端显示可读的`Re: Remote advisory collaboration…`；旧数据库subject不改；新落库subject已解码；不新增附件下载任务。
- 覆盖：I-4/I-5、R3、P3/P4；X2。

### A-4: 标签与来源回归
- 前置条件：入站processing和OUTBOUND mail_record恰有相同数字id；隔离环境资料有仅元信息文件。
- 操作步骤：1 给来信加QA和自定义标签；2 GET timeline；3 删除自定义标签；4 刷新旧详情和来信汇总；5 GET资料与timeline。
- 预期结果：标签仅出现在真实processing卡片，删除在两个入口同步；发件tags=[]；仅读取资料元信息无内容下载；原关注/处理/发送服务不变。
- 覆盖：I-3/I-5、R2、P1/P3/P4；X3。

### A-5: 专家标签来源与批量读取
- 前置条件：隔离环境一页20位同层专家，其中A在专家管理添加“学术科研”“重点关注”，A来信另加邮件标签“会议安排”；B没有专家标签，C画像缺失；用测试stub模拟另一层ES读取失败。
- 操作步骤：1 请求会话列表；2 在专家管理给A删除“重点关注”后刷新；3 查看调用记录；4 模拟某层ES失败后再次请求。
- 预期结果：A.expertTags只有专家标签而不含“会议安排”；B=[]，C=null；删除后新summary不再包含“重点关注”；同层只1次批量画像查询，最多3层3次；某层失败不改变SQL排序/列表分页，也不使整页失败。
- 覆盖：R4、I-6、P1/P4；X8。
