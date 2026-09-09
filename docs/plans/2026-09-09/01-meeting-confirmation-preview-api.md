# 01 专用模板、时区目录与日历预览

依赖：无。子系统：邮件模板、会议内容生成，共2个；9个实施文件。新增业务表字段0。接口可独立使用；本阶段不出现生产按钮。

## 需求描述

R1：用真实目标来信和账号获取专用模板/签名，配置姓名、时区、起止日期、Zoom链接，得到英文邮件与可下载的 ICS 内容。R2：时区目录可供搜索，拒绝无效/歧义时间。

必须保留 M1：普通模板渲染与普通单发流程；M2：旧会议确认业务、专家状态、账号绑定均无新增写入。范围外：Zoom建会、RSVP、重复事件、提醒闹钟、服务端草稿、通用模板语法改造。

## 关键不变量

### Invariant I-1: 预览只读且目标真实
- Rule：任何 options/zones/preview 请求不触发 SMTP、send_attempt、mail_record、meeting_schedule、processing状态、绑定、计数写入；processing 必须存在且其 expertContactId 等于请求contactId；账号取 requested非空否则来信账号。
- Applies to：MeetingConfirmationService/Controller全部入口
- Violation consequence：违反本规则会造成误发、时间/附件不一致或旧路径回归；不得以 UI 提示替代服务端规则。
- 来源：K-smtp-idempotency-reservation-before-delivery；D2

### Invariant I-2: 会议模板独立域
- Rule：新模板 code=MANUAL_MEETING_CONFIRMATION，mailType=MANUAL_MEETING_CONFIRMATION；只选 enabled=true 且该类型的模板；仅 CUSTOM_TEXT、按blockOrder,id排序后空行拼接；新服务只解析四个 {{...}}，不修改通用 ${...}。普通单发列表过滤和直接ID服务端拒绝此type。
- Applies to：V122 seed、options/preview、ManualExpertMailService.listSendOptions/composeComposeTemplate
- Violation consequence：违反本规则会造成误发、时间/附件不一致或旧路径回归；不得以 UI 提示替代服务端规则。
- 来源：K-mail-template-table-dead；K-renderText-all-callers；K-manual-compose-template-option-type-gate

### Invariant I-3: 同源时间
- Rule：zoneId 为服务端目录中的IANA区域或UTC；start/end各按ZoneRules.getValidOffsets校验，0拒绝跳时、2拒绝回拨，不静默取某偏移；两端独立换算。0<duration≤1440分钟，精度分钟。文本/中国时间/ICS均由同一Instant对生成。
- Applies to：目录、validateAndBuild、preview
- Violation consequence：违反本规则会造成误发、时间/附件不一致或旧路径回归；不得以 UI 提示替代服务端规则。
- 来源：original；RFC5545；Java11 ZoneRules

### Invariant I-4: 无默认猜测
- Rule：新建表单日期/起止/Zoom为空，zoneId=Asia/Shanghai（明确应用默认，不推断专家所在地）；专家expertName原文可改；签名按D2拼接；不存在字段不编造。
- Applies to：options、preview、后续04初值
- Violation consequence：违反本规则会造成误发、时间/附件不一致或旧路径回归；不得以 UI 提示替代服务端规则。
- 来源：original

### Invariant I-5: 受限内容与稳定快照
- Rule：结构化配置是唯一输入；拒绝客户端任意ICS/filename/MIME；raw模板四个必填变量各至少1次，未知/不闭合{{或}}及所有${残留拒绝。输出HTML先逐段escape，再仅将完整Zoom URL转换为同文字安全a标签；不能用plainTextToHtml(plain,urls)，后者固定文字为Unsubscribe。
- Applies to：validateAndBuild、preview
- Violation consequence：违反本规则会造成误发、时间/附件不一致或旧路径回归；不得以 UI 提示替代服务端规则。
- 来源：K-compose-template-html-after-render；MailContentService.kt:9..16

### Invariant I-6: 可重算日历
- Rule：ICS内容≤64KiB UTF-8；CRLF结尾、每物理行≤75字节且不截断码点；只1个VEVENT；UTC DTSTART/DTEND；无METHOD/ORGANIZER/ATTENDEE。UID按规范语义与目标确定；generatedAt在打开/编辑草稿时冻结，DTSTAMP取整秒；同一配置每次重算字节相同。
- Applies to：DTO、serializer、preview、后续send/snapshot
- Violation consequence：违反本规则会造成误发、时间/附件不一致或旧路径回归；不得以 UI 提示替代服务端规则。
- 来源：original；RFC5545

## 现状审计
审计正文与原始检索是本节不可分割的组成部分：[完整审计](meeting-confirmation-audit.md)、[代码证据目录](meeting-confirmation-evidence/)、[源文件 SHA-256](meeting-confirmation-evidence/source-sha256.json)。当前工作区代码为证据；拟新增的接口/类型/样式均明确属于本计划决策，不宣称已经存在。已有 dirty 文件不清理、不覆盖。

适用审计 D1（模板头/块全部读写）、D2（只读身份来源及排除旧写入）、D6（标准/迁移/测试）。新写路径仅V122插入模板→已有模板页和新options读取（IP-1）；IP-2是processing/account读→默认表单→规范化输出。当前render不懂{{}}；新parser是本期显式设计，不当作现有能力。

## 实现方案

### T1 类型与公开协议（I-1..I-6）

新建 `mail/service/MeetingConfirmationModels.kt`，同文件定义请求/响应/内存快照，不新增数据表：

```text
MeetingInput {
  templateId: Long,
  templateBody: String,  // 从当前模板取出的快照；允许本次修改，不回写模板
  expertSalutation: String,
  zoneId: String,
  startLocal: String,    // YYYY-MM-DDTHH:mm
  endLocal: String,
  zoomUrl: String,
  senderSignature: String,
  generatedAt: String   // UTC ISO Instant，精度秒；options发出，已有草稿复用
}
MeetingPreviewRequest { contactId:Long, senderAccountCode:String?=null, meeting:MeetingInput }
MeetingPreviewResponse {
  targetKey:String, resolvedAccountCode:String, meeting:MeetingInput,
  textBody:String, htmlBody:String, meetingTime:String,
  startUtc:String, endUtc:String, chinaTime:String, durationMinutes:Int,
  attachment: { filename:String, contentType:String, icsText:String,
                byteLength:Int, sha256:String, semanticSha256:String }
}
CalendarAttachmentSnapshot {
  schemaVersion:1, filename:String, contentType:"text/calendar; charset=UTF-8",
  icsText:String, sha256:String, semanticSha256:String
}
```

在该文件同时定义 `object CalendarAttachmentCodec`，用项目Jackson Kotlin mapper序列化固定字段；公开 `serialize(snapshot):String` 与 `parseOrNull(json:String?):CalendarAttachmentSnapshot?`。parse拒绝空串、schemaVersion!=1、非固定contentType、非法filename（必须匹配`meeting-[0-9]{4}-[0-9]{2}-[0-9]{2}-[A-Za-z0-9-]{1,60}\.ics`）、UTF8>64KiB、sha256不等于icsText字节摘要、semanticSha256非64位hex；损坏返回null，不抛到时间线500。serialize只接受同样有效快照，失败立即抛IllegalArgumentException。02存储/03读取复用此对象，不在controller里放service依赖的共享函数。

`CalendarAttachmentSnapshot` 是server-only值，不能作为发送请求附件直传。由同一生成器创建，JSON只在02持久化。sha256是ICS字节摘要；semanticSha256是下述固定规范语义摘要。客户端不传任意downloadUrl。

### T2 只读服务、接口（I-1..I-6）

新建 `mail/service/MeetingConfirmationService.kt` 与 `mail/controller/MeetingConfirmationController.kt`。路径固定：

| 方法/路径 | 请求 | 返回/行为 |
|---|---|---|
| GET `/api/mail/unmatched-inbound/{processingId}/meeting-confirmation/options?contactId=...&senderAccountCode=...` | account可省 | targetKey、resolvedAccountCode、expertSalutation、senderSignature、generatedAt(UTC Instant)、defaultZoneId=Asia/Shanghai、templates[{id,name,body}]；没有模板返回空数组，非404 |
| GET `/api/mail/meeting-confirmation/time-zones?date=2026-09-11` | ISO日期必填 | [{id,labelZh,aliases,offsetLabel,offsetSeconds}]；offset按该日12:00 UTC计算，仅列表辅助显示；不作为会议时间换算结果 |
| POST `/api/mail/unmatched-inbound/{processingId}/meeting-confirmation/preview` | MeetingPreviewRequest | MeetingPreviewResponse；不新增POST保存/发送动作 |

服务签名固定：`options(processingId:Long, contactId:Long, senderAccountCode:String?):MeetingOptionsResponse`、`timeZones(date:LocalDate):List<MeetingTimeZoneOption>`、`preview(processingId:Long, request:MeetingPreviewRequest):MeetingPreviewResponse`、`validateAndBuild(processingId:Long, contact:ExpertContact, account:MailSenderAccount, input:MeetingInput):MeetingPreviewResponse`。后三者公用同一纯时间/模板生成逻辑；preview先读目标/账号再调用validateAndBuild，03传已解析的真实实例并再校验processing归属。normalizedRecipient=`contact.expertEmail.lowercase().trim()`，与Pending:354一致。响应options类型和zone类型按表中字段在Models文件显式定义，controller仅做路由绑定。options的generatedAt用Instant.now().truncatedTo(SECONDS)，不要新增需要Spring注册的Clock构造依赖。

均沿已有 `/api/**` AuthInterceptor；不设置登录豁免。不暴露 SMTP/IMAP密码。NoSuchElementException→404；IllegalArgumentException→400 BAD_REQUEST，message使用下面固定文案。不要抛会落入通用500的ResponseStatusException。不调用需要写库的旧meeting服务。

模板为空：UI后续显示“暂无可用会议模板，请在邮件模板中启用「专家会议确认 · 英文」”；options正常200。非法模板、禁用/已删除、非CUSTOM_TEXT、空体、缺变量→400“会议模板不可用，请重新选择或检查模板变量”。本次body快照允许比库模板新/旧，不做模板版本强制同步；发送时仍查模板存在、enabled/type，但正文用用户已预览的快照，模板后台修改不暗改已确认草稿。

时区目录来源 `ZoneId.getAvailableZoneIds()`，排除不包含`/`的短名及`Etc/*`后并入`UTC`；排序：固定常用表先行，余者id字典序。中文别名表逐字从已确认preview meeting.js的zoneAliases/zoneChinese映射移入服务；`Asia/Calcutta`若存在也保留；至少：土耳其/伊斯坦布尔/Turkey/Türkiye/Istanbul、北京/上海/China、英国/伦敦/UK/London、德国/柏林、美国东部/纽约/Eastern、美国西部/洛杉矶/Pacific、日本/东京、印度/加尔各答、澳大利亚/悉尼、中国香港、新加坡、法国巴黎、俄罗斯莫斯科、加拿大多伦多、新西兰奥克兰、阿联酋迪拜。完整表冻结于04的合同附件。未映射id仍可用英文搜索，不宣称全球所有城市都有中文翻译。offset用ASCII正负号，UTC+3、UTC+5:30、UTC-4、UTC+0。

校验：姓名trim后1..100字符、无控制字符；签名trim后1..2000字符，允许LF不允许其它控制符，CRLF统一LF；模板trim后1..10000字符；URL≤2048，trim后必须HTTPS，无userinfo、fragment、控制/空格，host必须等于或以点为边界隶属于zoom.us/zoom.com/zoom.com.cn，path匹配 `/j/[^/]+` 或 `/my/[^/]+`，保留查询顺序/大小写/密码参数，不访问链接。上述域只是沿已批准preview的范围设计，不声称覆盖全部Zoom商业部署。姓名/签名/Zoom还须拒绝`${`、`{{`、`}}`，防止后续人工回复变量渲染二次解释配置值。最终邮件正文≤20000字符，与现有验证一致。日期年1900..2100，合法日期，分钟精度；不禁止过去日期（下载复核/历史复现），但UI提示“会议时间已过去，请核对”。超长会议→“会议时长须大于 0 且不超过 24 小时”；缺值→“请填写日期和起止时间”；非法zone→“请选择有效会议时区”；DST gap→“该当地时间不存在，请避开夏令时跳时区间”；DST overlap→“该当地时间出现两次，请选择不处于夏令时回拨区间的时间”。

### T3 邮件/日历唯一渲染（I-3/I-5/I-6）

默认模板逐字（库里CUSTOM_TEXT一块，不存HTML）：

```text
Dear {{expert_salutation}},

Thank you for confirming.

We have noted the meeting time as {{meeting_time}}.

Please join the meeting using the following link:

{{zoom_url}}

We look forward to speaking with you.

Best regards,
{{sender_signature}}
```

四变量单次正则替换，不二次展开用户值。expert_salutation=人工称呼；meeting_time 以英文Locale.US格式：同日 `Friday, September 11, 2026, from 10:00 AM to 10:30 AM Türkiye Time (UTC+3)`；跨日两端都写完整日期；开始/结束偏移不同写`(UTC+2 → UTC+1)`。Europe/Istanbul固定标签Türkiye Time，Asia/Shanghai China Standard Time，其它取id最后段下划线替空格+` Time`；不给会变化的夏令时缩写做常量。中国时间输出`2026/09/11 周五 15:00 – 2026/09/11 周五 15:30`（固定zh-CN映射，24小时）。换行和星期不交给浏览器猜测。

HTML使用MailContentService.plainTextToHtml(plain)安全转义，然后只把已escape的完整zoomUrl文本替换为`<a href="ESCAPED_URL" target="_blank" rel="noopener noreferrer">ESCAPED_URL</a>`；不得让URL匹配吞掉邻接标点；无CSS/内联style进入邮件正文。

语义摘要的长度前缀编码复用attempt同样的UTF-8长度思想，新域标记`meeting-calendar-v1`，字段顺序固定：processingId,contactId,accountCode,normalizedRecipient,expertSalutation,zoneId,startInstant,endInstant,zoomUrl,senderSignature。长度用4字节大端字节数，值用UTF-8；不含generatedAt/template文字/随机数/filename。UID=`semanticSha256前32位@qingfei-calendar`。generatedAt严格parse Instant→truncate秒；保持preview回传原值。此UID设计用于单次事件附件，不承诺修改会议作为iTIP更新（范围外）。

ICS属性顺序固定：BEGIN:VCALENDAR、VERSION:2.0、PRODID:-//Qingfei Tech Talent Team//Meeting Confirmation//EN、CALSCALE:GREGORIAN、BEGIN:VEVENT、UID、DTSTAMP、DTSTART、DTEND、SUMMARY、DESCRIPTION、LOCATION、URL、STATUS:CONFIRMED、TRANSP:OPAQUE、END:VEVENT、END:VCALENDAR。SUMMARY=`Meeting with {称呼} | Qingfei Tech Talent Team`；DESCRIPTION=`meetingTime + 两换行 + Join Zoom meeting: + 换行 + URL + 两换行 + 签名`；LOCATION按TEXT转义URL，URL按URI输出。TEXT先反斜线、再换行/分号/逗号转义，折行按码点累计UTF8字节，续行空格算1字节。文件名`meeting-{开始当地YYYY-MM-DD}-{称呼ASCII字母数字-，其余连续字符替-，去首尾-，限60字符；为空用expert}.ics`，不使用客户端路径。示例文件名`meeting-2026-09-11-Professor-Basdogan.ics`。

### T4 模板初始化和普通发送门禁（I-2）

V122新模板名“专家会议确认 · 英文”，code/type上述固定，subject=`Meeting confirmation`（仅库必填；人工回复主题绝不覆盖）、description=`仅供收发件箱会议确认。{{expert_salutation}} / {{meeting_time}} / {{zoom_url}} / {{sender_signature}} 在会议弹窗生成；通用预览显示原文。`、enabled1、required_keys NULL、subject_variants NULL。按code不存在才插入，不覆盖同code已有人工配置；插入的模板才创建单块，禁止为原有模板删块。V122重放由Flyway管理，不伪造幂等更新。修改 ManualExpertMailService 两处type过滤/直接拒绝；已知批量门禁不改。更新 Flyway最新版本断言为122并新增旧模板未改/新模板仅1头1块检查。

## 变更文件清单

| # | 文件（仓库根目录相对路径） | 操作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationModels.kt` | 新增DTO/内存快照 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt` | 新增只读生成器 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationController.kt` | 新增3接口 |
| 4 | `src/main/resources/db/migration/V122__seed_manual_meeting_confirmation_template.sql` | 新增seed |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt` | 修改两处类型门禁 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt` | 增加专用模板隔离回归 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationServiceTest.kt` | 新增时间/ICS/模板测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt` | 新增只读/身份/错误响应测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 更新最新版本及seed断言 |

## 验收标准

- I-1：mockverify所有预览无SMTP/写库；contact不匹配400、不存在404、账号默认与Pending相同；Auth开启未登录401。
- I-2：专用模板选项仅启用type；普通单发不列出、直传id拒绝且SMTP=0；全局${...}旧测试继续通过；禁用/删除/缺变量400。
- I-3：Istanbul样例=07:00/07:30Z、中国15:00/15:30；New_York 2026-03-08T02:30拒绝、2026-11-01T01:30拒绝；Asia/Kolkata半小时偏移、Australia/Sydney跨日、Europe/London季节偏移测试。
- I-4：无硬编码示例姓名/9月11日/Zoom；空账号title/team不生成“Customer Care Officer”。
- I-5：姓名`<img src=x onerror=alert(1)>`只为文本；Zoom `zoom.us.evil.example`拒绝、`https://zoom.us@evil.example/j/1`拒绝；链接query含&完整；模板未知/缺失变量拒绝。
- I-6：独立测试读取ICS并unfold校验每行≤75字节，中文/emoji跨边界不断码；字段值能还原反斜线/逗号/换行；同config字节相等，改generatedAt只改变sha不改变semanticSha；无METHOD和ATTENDEE；Codec roundtrip及坏JSON/hash/schema/超长文件名均有断言。
- IP-1/IP-2：实际Controller JSON交互与隔离MySQL迁移验证；运行`mvn test`和D6迁移测试，NOT_RUN须单列。

## 人工验收清单

### A-1: 模板读取与普通模板隔离
- 前置条件：隔离测试环境迁移至122；后台已有真实来信的测试专家，其邮箱为验收者控制的测试邮箱；记下后台显示contactId/processingId和账号。
- 操作步骤：1. 在邮件模板找到“专家会议确认 · 英文”。2. 调用GET /api/mail/unmatched-inbound/{processingId}/meeting-confirmation/options?contactId={contactId}，花括号替换为步骤记录的真实数字。3. 到普通模板单发列表。4. 禁用该模板，再调options。
- 预期结果：初次options有且仅1个新模板；普通单发列表没有该项；禁用后options templates=[]。旧MEETING_CONFIRMATION正文不变。
- 覆盖：R1/M1；I-1/I-2；IP-1

### A-2: 真实下载数据
- 前置条件：沿用A-1，将模板启用；向POST /api/mail/unmatched-inbound/{processingId}/meeting-confirmation/preview提交JSON：顶层contactId为A-1数字、senderAccountCode=null；meeting.templateId和templateBody分别取options.templates[0].id/body、meeting.generatedAt取options.generatedAt；其余meeting字段填写Professor Basdogan、Europe/Istanbul、2026-09-11T10:00到10:30、用户给定Zoom链接、签名LuKai, Customer Care Officer换行Qingfei Tech Talent Team China；generatedAt取options。
- 操作步骤：1. 保存响应attachment.icsText为UTF8文件（不把JSON转义符当文件字符）。2. 在Apple Calendar/Outlook中导入测试日历。3. 界面设Asia/Shanghai。
- 预期结果：事件显示2026-09-11 15:00–15:30，链接含完整pwd参数，响应meetingTime含Friday与UTC+3；仅预览没有新发件记录、会议状态未改变。
- 覆盖：R1/M2；I-1/I-3/I-5/I-6；IP-2

### A-3: 时区及无效配置
- 前置条件：沿用A-2请求。
- 操作步骤：1. GET /api/mail/meeting-confirmation/time-zones?date=2026-09-11查Istanbul及Kolkata。2. POST将zone改America/New_York、startLocal=2026-03-08T02:30。3. 改2026-11-01T01:30。4. 查看options默认签名。
- 预期结果：目录含UTC+3、UTC+5:30；两次POST400分别出现“不存在”“出现两次”固定文案；签名来自实际账号字段，日期/Zoom没有服务器示例默认值。
- 覆盖：R2；I-3/I-4；IP-2
