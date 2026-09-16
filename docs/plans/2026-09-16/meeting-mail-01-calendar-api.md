# 01 · 会议日历数据与接口

状态：待审批；仅计划。依赖：无。一个子系统，8 个文件；现有共享表新增字段 0。

## 需求描述

提供可持久保存的手动新增、变更日期、取消排期 API，日历与邮箱使用同一份排期。保持原邮件发送、专家流程状态和旧专家详情排期行为。范围不含提醒、重复会议、拖拽、自动通知、历史自由文本解析或旧排期迁移。

## 关键不变量

### Invariant I-1: 新排期只有一个数据源
- Rule: 新表 meeting_calendar_event 是本功能权威；expert_contact_id 必填且真实存在。source_mail_record_id 为空表示手工新增，非空表示已成功发出的会议确认邮件；非空来源全局唯一。专家姓名/邮箱读 expert_contact，不复制进排期表。
- Applies to: 手工创建、createFromSentMail、日历查询、邮箱摘要。
- Violation consequence: 双端不一致或重复排期。
- 来源: original；旧表边界经代码审计。

### Invariant I-2: 统一时间语义
- Rule: starts_at_utc / ends_at_utc 存 UTC DATETIME(6)，所有映射显式按 UTC，禁止用服务器默认时区；接口写入 startBeijing/endBeijing 为 yyyy-MM-dd'T'HH:mm，按 Asia/Shanghai 转换；end > start。读接口输出带 Z 的 startUtc/endUtc。created_at/updated_at 同样 UTC，updatedAt 作为不透明完整版本字符串回传。
- Applies to: 创建、改期、邮件来源创建、区间查询。
- Violation consequence: 时区偏移、跨日漏会、并发覆盖。
- 来源: original；复用 MeetingConfirmationDomain.DEFAULT_ZONE_ID 的 Asia/Shanghai 语义。

### Invariant I-3: 取消保留且不复活
- Rule: status 仅 ACTIVE / CANCELLED，默认 ACTIVE；取消保存 cancel_reason（选填，最长200字符），不删行；已取消不可改期，无恢复入口。改期/取消用事务锁行并比对 expectedUpdatedAt；版本失配409，成功 updated_at 严格递增至少1微秒。未变化保存返回原记录且不更新时间。重复取消已取消记录返回现状200，不覆盖原取消原因。
- Applies to: 更新、取消、创建默认值。
- Violation consequence: 旧弹窗覆盖新排期、取消记录消失。
- 来源: original。

### Invariant I-4: 排期操作不发送邮件、不推进专家流程
- Rule: 手动新增/改期/取消不调用 SMTP、ConversationStateService 或专家 operator_status 写入；source_mail_record_id / expert_contact_id 创建后不可修改。meeting_link 可空，非空仅 http/https 且≤1024字符；note 可空且≤200字符；取消原因与备注分开保存。
- Applies to: 所有新写接口；旧 MeetingScheduleService 保持原状。
- Violation consequence: 未授权通知或错误更改专家状态。
- 来源: original；K-calendar-not-expert-material-owner 的职责分离原则。

### Invariant I-5: 查询完整且有界
- Rule: 默认只查 ACTIVE；showCancelled=true 同时读两状态；区间交集条件 starts_at_utc < to AND ends_at_utc > from。日历范围≤62天，按 starts_at_utc,id 排序，分页100条（最大200），游标绑定过滤条件；前端读完所有页。专家详情可按contactId分页不传区间。摘要单请求≤100个contactId，只返回 ACTIVE 数量和最近一场（优先最近未来开始，否则最近过去开始）；零场明确 activeCount=0,next=null。
- Applies to: list/get/summaries。
- Violation consequence: 静默漏会、摘要残留或跨页错序。
- 来源: original。

## 现状审计

### 旧 meeting_schedule
- Schema：V9__create_meeting_schedule_and_template.sql:1-17；china_time 是 VARCHAR(255)，expert_available_text 为文本；状态 PENDING/CONFIRMED/COMPLETED/CANCELLED，缺少结构化起止时间。MeetingSchedule.kt:7 同构。
- 全部写路径：MeetingScheduleService.kt:40 extractAndCreate、55 createManual、74 updateSchedule、101 confirmMeetingAndEmail、196 completeMeeting、222 cancelMeeting。
- 全部业务读入口：同服务 findOwnedSchedule:267；ExpertContactManagementService.kt:90 专家详情；ExpertContactManagementController.kt:208-242 CRUD/确认/完成/取消；app.js:7819 renderMeetingSchedule、7915 confirmMeetingSchedule、8207 详情、13309 表单。
- 自动写入口：AutoMailReplyService.kt:405 调 extractAndCreate。取消会在 MeetingScheduleService.kt:229 调专家状态迁移；确认保存 CONFIRMED 后发送邮件（:101、:141）。不能直接当本次“仅修改排期”的写接口。
- 方案选择：新增一张窄表和专用服务，保留旧自动提取与流程。避免给旧文本字段强行赋予 UTC 日期语义，亦不改旧状态机。

### 新表、专家与来源邮件
- 本次全仓检索新表名0命中，属于明确拟新增设计，非现存事实。新表全部拟写路径仅本服务 createManual/update/cancel/createFromSentMail；全部拟读路径仅本服务 list/get/summaries 和来源去重查询。
- expert_contact 只读现有仓储；AuthWebConfig.kt:25 拦 /api/**，沿用登录会话边界，不自建角色权限系统。
- mail_record 仅在 createFromSentMail 校验同专家、OUTBOUND、MANUAL_RICH_REPLY、SENT、有 calendarAttachmentJson；详细既有全量读写回执见 [代码证据](meeting-mail-evidence.md#完整检索回执)。不修改该表。
- IP-1：本接口写→日历与邮箱读（03）。IP-2：发件事务→来源唯一排期（02）。IP-3：原专家状态/旧排期读写与新表隔离。
- Flyway 当前最高V124；集成测试有13处最新版本124断言，另有历史版本断言；精确回执见 evidence.flyway_pins（来源 K-flyway-latest-version-test-pin）。

## 实现方案

### T1：表与仓储（I-1～I-5）
修改文件清单1～3和8。迁移建立以下完整列：

|列|SQL类型/约束|
|---|---|
|id|BIGINT PRIMARY KEY AUTO_INCREMENT|
|expert_contact_id|BIGINT NOT NULL，FK expert_contact(id)，RESTRICT|
|source_mail_record_id|BIGINT NULL，UNIQUE，FK mail_record(id)，RESTRICT|
|starts_at_utc / ends_at_utc|DATETIME(6) NOT NULL|
|meeting_link|VARCHAR(1024) NULL|
|note / cancel_reason|VARCHAR(200) NULL|
|status|VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'|
|created_at / updated_at|DATETIME(6) NOT NULL，由服务显式提供UTC值|

索引：(status,starts_at_utc,id)、(expert_contact_id,status,starts_at_utc,id)。服务强校验状态、起止关系、长度，不依赖数据库是否执行 CHECK。仓储使用项目已有参数化 JDBC 方式显式 DTO 映射，不假定 Spring Data 自动支持新投影。锁行、版本比较、更新在同一事务。来源唯一冲突只读取原记录并返回，不把已取消/已改期记录覆盖为原邮件日期。

### T2：服务与端点（I-1～I-5）
修改清单4～5。路径以 /api/meeting-calendar 为前缀：

|方法/路径|输入|输出/语义|
|---|---|---|
|GET /events|from/to：ISO瞬时；contactId可选；showCancelled=false；limit/cursor|items,nextCursor；区间与专家规则见I-5|
|GET /events/{id}|id|单条事件，含当前专家姓名/邮箱；不存在404|
|GET /summaries|contactIds逗号分隔|每个请求id一项 activeCount,next；空列表直接[]|
|POST /events|contactId,startBeijing,endBeijing,meetingLink?,note?|201，ACTIVE；来源mail id由服务置null|
|PUT /events/{id}|startBeijing,endBeijing,meetingLink?,note?,expectedUpdatedAt|200；body不接受换专家/来源/状态|
|POST /events/{id}/cancel|expectedUpdatedAt,reason?|200 CANCELLED；冲突409|

统一事件 DTO：id,contactId,expertName,expertEmail,sourceMailRecordId,startUtc,endUtc,meetingLink,note,status,cancelReason,createdAt,updatedAt。无额外持久化 source、hasSchedule、isCancelled、changed 布尔值。来源中文由 sourceMailRecordId 是否为空显示“手动新增/会议邀请”。

在MeetingCalendarEvent.kt同时定义内部MeetingCalendarInput(startUtc: Instant, endUtc: Instant, meetingLink: String?)值对象。预留且实现内部 createFromSentMail(record, input: MeetingCalendarInput)，调用方必须已处在事务内（MANDATORY）；不提供公开“标记已发送”接口。本阶段通过事务测试调用，02接入真实发送。没有历史自动回填；上线前发出的邀请由操作员手动补排期。

### T3：校验（I-1～I-5）
清单6～8。MySQL测试验证交集跨日、排序分页、唯一来源、同源并发、版本冲突、锁行修改与取消；源邮件不合格拒绝。13个“最新版本”断言改125，历史target V23/V24/V116原样保留。参数错误用IllegalArgumentException→400，不存在用NoSuchElementException→404；版本/取消冲突定义同文件MeetingCalendarConflictException，由新MeetingCalendarController的专用ExceptionHandler返回409 ApiErrorResponse。不能直接抛ResponseStatusException：GlobalExceptionHandler.kt:65当前会将其落入500，MeetingConfirmationController.kt:23也明确记录此限制。新行含FK，测试清理先删新表再删关联专家/邮件，不改无关既有测试夹具。

## 变更文件清单

|序号|文件|操作|内容|
|---|---|---|---|
|1|`src/main/resources/db/migration/V125__create_meeting_calendar_event.sql`|新增|创建结构化排期表；不回填旧排期|
|2|`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/MeetingCalendarEvent.kt`|新增|排期实体、状态常量|
|3|`src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MeetingCalendarEventRepository.kt`|新增|参数化区间/专家/摘要查询，锁行更新|
|4|`src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt`|新增|新增、改期、取消、发送成功关联入口|
|5|`src/main/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarController.kt`|新增|接口和窄 DTO|
|6|`src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarServiceTest.kt`|新增|时间、状态、幂等与并发|
|7|`src/test/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarControllerTest.kt`|新增|真实认证、接口与 MySQL 查询|
|8|`src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`|修改|最新版本 124→125；新表约束|

## 验收标准

- I-1：同一source_mail_record_id创建两次只有1行，创建关联错误专家的邮件返回错误。
- I-2：北京2026-09-18 10:00–10:30读回2026-09-18T02:00:00Z–02:30:00Z；应用JVM时区UTC/上海结果相同；相等/反向结束时间400。
- I-3：两客户端同时拿同版；先修改成功，后修改409；取消后更新409；重复取消不改变原原因。
- I-4：verifyNoInteractions SMTP/专家状态服务；改期前后来源邮件正文和ICS字节一致。
- I-5：跨午夜事件出现在两日的交集范围；201条结果分两页完整读取；摘要不存在残留。
- 执行：JDK11 mvn test -Dtest=MeetingCalendarServiceTest,MeetingCalendarControllerTest；MySQL分组启用 -DmysqlIt=true；FlywayMigrationIntegrationTest 用 -DmigrationIt=true（需要Docker）。不能将跳过数据库用例写成通过。

## 人工验收清单

### A-1：手工排期与北京时间
- 前置条件：测试环境已有专家，通过 GET /api/expert-contacts 取真实id；已登录。使用浏览器开发者工具发起同源请求。
- 操作步骤：1.POST /api/meeting-calendar/events，时间填2026-09-18T10:00和T10:30。2.GET返回事件id。3.查询该专家summaries。
- 预期结果：201；startUtc=2026-09-18T02:00:00Z；状态ACTIVE；摘要activeCount=1；来源为空；该账号已发送数量不变。
- 覆盖：I-1/I-2/I-4；IP-1/IP-3；手动新增需求。

### A-2：改期、冲突与取消
- 前置条件：A-1事件，保存其updatedAt；复制为两个请求。
- 操作步骤：1.第一份PUT改到2026-09-19T14:00–14:30。2.第二份用旧updatedAt再PUT。3.用最新版本取消，reason=专家时间调整。4.默认查询和showCancelled=true查询。5.重复取消。
- 预期结果：第一次200、第二次409；默认查询0场，含取消查询1场CANCELLED，原因“专家时间调整”；重复取消仍1条，专家原流程状态不变、无新邮件。
- 覆盖：I-2/I-3/I-4/I-5；IP-1/IP-3；双端修改与取消的API合同。

### A-3：来源排期联动与回归
- 前置条件：02完成后，用测试邮箱在收发件箱会议确认生成邀请。
- 操作步骤：1.只预览后GET日历。2.发送成功后GET日历。3.同请求重提，再取消该排期并重提已成功请求。
- 预期结果：预览0新增；成功新增1场；重提均不重复且取消记录不复活；旧专家详情排期表不新增行。
- 覆盖：I-1/I-3/I-4；IP-2/IP-3。
