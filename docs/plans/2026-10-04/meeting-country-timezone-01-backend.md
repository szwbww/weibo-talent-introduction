# 01 会议国家文案、时刻目录与附件名

状态：待评审；未实施。所属：[主计划](meeting-country-timezone-master.md)。本计划为第一步，可独立部署；第二步使用本计划的新增返回字段。范围为会议生成器及其读接口，不建立新业务服务。

## 需求描述

新会议正文/ICS 描述使用 `Brazil (UTC-3)` 等“英文国家＋实际偏移”，新附件名使用当地开始日期、开始分钟、已有会议摘要短码。为下一步前端提供可验证的国家映射和本场会议起止偏移，同时保留旧目录调用方。

必须保留：
1. 相同原始 zoneId/起止输入仍对应相同 UTC 起止、北京时间；两端 DST 非唯一时间仍拒绝。
2. 通用 MEETING_INVITATION 模板、称呼、签名、链接验证、24 小时时长上限、1900–2100 年范围、ICS SUMMARY/UID 语义。
3. 发送重建/hash/正文一致性检查、claim、SMTP、成功/失败事务、日历排期与普通回复行为。
4. 旧命名快照可展示下载、旧已发送原件不重建、不改写。
5. `time-zones?date=` 原字段/顺序/中午偏移和世界时钟行为。

不做 DB/ES 迁移或回填；不改变 sender/contact/模板；不改 contact-country-timezones.json；不改共享目录生成脚本 `generate_meeting_timezone_catalog.py`；不修改 MIME/下载控制器/codec 正则；不引入运行时网络、第三方时区库、新 Spring bean 或新缓存框架。

## 关键不变量

### I-1：国家归属有明确来源
- Rule：新 `meeting-zone-countries.properties` 为离线派生数据。精确匹配现有国家目录 zone ID 优先；否则沿 IANA 2026c backward 的 `#=` 原目标（存在时）或 Link 目标递归，遇到国家目录即停止。检测循环，不用 labels/offset/规则相等推断国家。中文国名取既有目录，英文国名取同版 iso3166.tab。
- Applies to：新生成脚本、resource 加载、目录 DTO、`zoneLabel`。
- Violation consequence：别名可被错误标成另一个国家；同偏移国家可能混组。
- 来源：原创；官方链与完整审计 [country-mapping-audit.json](meeting-country-timezone-evidence/country-mapping-audit.json)。

### I-2：旧目录模式保持兼容
- Rule：无 startLocal/endLocal 的 GET 继续调用 `timeZones(date)`；保留每个原始 ID、顺序、`labelZh`、`aliases`、`offsetLabel`、`offsetSeconds`。新增国家字段只做加法；不全局去重、不改 `catalogZoneIds()`。
- Applies to：controller.timeZones、service.timeZones；world-clock.js 调用方。
- Violation consequence：世界时钟城市搜索/常用项消失，或中午元数据被悄然改成别的含义。
- 来源：K-meeting-timezone-offset-is-noon-metadata；`world-clock.js:3–10`。

### I-3：会议模式用两个当地端点
- Rule：新增可选参数必须成对；对应服务方法 `timeZonesForMeeting(date, startLocal, endLocal)`。使用原分钟输入解析及年限，date 必须等于开始日期，结束当地时间须晚于开始。每个 ZoneId 分别调用两端 `getValidOffsets`，各为 1 才是可选项；0/2 分别给既有 gap/overlap 文案，不能自动 atZone 补齐。有效项再检查实际时长 1–1440 分钟。
- Applies to：会议模式目录；preview/validateAndBuild 的原端点校验保持。
- Violation consequence：切换日显示错误或自动选到不存在/重复的时间。
- 来源：`MeetingConfirmationService.kt:104–119,339–365`；[JDK 探针](meeting-country-timezone-evidence/jdk11-timezone-probe.txt)。

### I-4：显示和时间身份分离
- Rule：国家 label 只进入 meetingTime/正文/ICS DESCRIPTION，不替换原请求 zoneId，不改变 semanticSha256 的字段、顺序或 UID 生成算法。文件名或中文选项不参与时间解析。跨偏移仍显示 `UTC-5 → UTC-4`，跨日保留两天日期。
- Applies to：meetingTimeText、zoneLabel、validateAndBuild、semanticSha256、buildIcs。
- Violation consequence：会面时刻漂移，或同一请求更换身份导致幂等变化。
- 来源：原代码 `130–146,385–408,539–566`。

### I-5：无姓名、确定的附件名
- Rule：`meeting-${startLocalDate}-${HHmm}-${semanticSha256.take(8)}.ics`；HHmm 为会议选定时区当地开始分钟，英文 ASCII、小写十六进制短码；不用系统当前日期、随机 UUID、邮箱、称呼。相同完整语义重复生成（即使 generatedAt 变化）保持文件名一致。
- Applies to：validateAndBuild → buildCalendarFilename → MeetingCalendarAttachment。
- Violation consequence：附件继续泄露可见姓名或同场每次预览文件名变化。
- 来源：`MeetingConfirmationService.kt:130–163,529–566`。
- 界限：短码只方便区分文件，不保证全局无碰撞；现有 64 位 hex 摘要、UID 和发送幂等仍各司其职。

### I-6：旧存档兼容与单快照传播
- Rule：保持 schemaVersion=1 和现有 filename codec 正则；它已能接受新格式。SMTP、持久化、时间线与下载使用同一快照中的文件名；不在任何读路径补算。老 `meeting-日期-专家名.ics` 继续被接受。
- Applies to：CalendarAttachmentCodec；PendingMailOperationService → ManualReplySendAttemptService → MailRecord；SMTP/时间线/下载。
- Violation consequence：收件人附件名与页面不一致；旧邮件的日历附件突然不可下载。
- 来源：K-calendar-not-expert-material-owner；schema/写读路径如下。

### I-7：原生成/发送边界保持
- Rule：通过既有 `renderByCode(MEETING_INVITATION)` 渲染；只调整 meeting_time 值，zoom_url 注入保持。Pending 仍在 claim 前 validateAndBuild、比较 ICS hash 和正文；旧预览若因新文案失配，明确重新预览，不绕过校验。普通非会议回复不走新逻辑。
- Applies to：validateAndBuild、meetingTemplateVariables、Pending 重建/发送。
- Violation consequence：正文与附件不一致或未经确认即发送/增加排期。
- 来源：K-meeting-confirmation-generic-template-boundary。

### I-8：无国家旧技术时区不伪装成国家
- Rule：UTC 是显式特殊项（code=`UTC`，中文“协调世界时”，英文 `Coordinated Universal Time`）。现有 13 个 SystemV ID 保留在原目录但国家字段为 null；新会议预览如使用无归属项，返回 `该旧时区没有国家归属，请重新选择国家和时区`，发生在 claim/SMTP 前。已发送附件读取不受此规则影响。
- Applies to：resource 生成/读取、timeZones、meetingTimeText；02 草稿重开提示。
- Violation consequence：随意归国或未察觉改动旧会议时刻。
- 来源：完整映射审计；不根据“东部/西部”等技术名字猜国家。

## 现状审计

路径省略公共前缀 `src/main/kotlin/com/weibo/talentintroduction/` 时，以 [code-baseline](meeting-country-timezone-evidence/code-baseline.md) 中的完整路径为准；实施清单始终写全路径。

### 离线文件及内存目录

- Schema：`meeting-timezones-zh.properties` 每行 `id=labelZh\taliases`，aliases 分隔符 U+001F；service companion `634–657` 读取。country JSON 顶层 version/sourceUrl/defaultPolicy/countries，每个国家有 code/labelZh/defaultZoneId/zones（`ExpertContactLocationModels.kt:35–67`）。
- 既有写路径：`scripts/generate_meeting_timezone_catalog.py:175–187` 写中文目录；country JSON 随仓库存储，本计划只读。精确 `rg` 回执见 evidence 的 `contact-country-timezones|meeting-timezones-zh.properties` 段，不假定存在运行时维护器。
- 既有读路径：`MeetingConfirmationService.timeZones` 读中文目录供会议与世界时钟；`ExpertContactLocationCatalog.load` 读国家目录供联系人位置。
- 新写路径：一个离线脚本写一个新 properties；新读路径放现有 service companion 内，沿当前 lazy Properties 模式，不增加 service 构造参数。手工构造 service 的测试调用点完整回执见 evidence，因构造不变不扩张这些测试修改名单。
- 不修改既有两份目录，不将新会议去重回写它们。

### mail_record.calendar_attachment_json

- Schema：`V123__add_mail_record_calendar_attachment.sql:14`，LONGTEXT NULL；NULL 表示无附件；codec 校验 schemaVersion/contentType/filename/ICS 字节 SHA/semantic SHA。
- 非空快照来源：`PendingMailOperationService.kt:619–629` 从重建结果构造一次。
- 写路径（按 `rg -n 'calendarAttachmentJson|calendar_attachment_json' src/main`）：
  - `ManualReplySendAttemptService.finalizeSuccess`：已有行 update `372`、新行 insert `393`。
  - `ManualReplySendAttemptService.finalizeFailure`：已有行 update `470`、新行 insert `492`。
  - `MailRecord.kt:35` 仅 nullable 字段声明，迁移仅新增列，均不是额外的生成器。
- 读路径：`MailboxConversationService.kt:404` 解析并回 filename/byteLength/downloadUrl；`CalendarAttachmentController.kt:59,78` 解析并回原件+Content-Disposition；`MeetingCalendarService.kt:69` 验证来源邮件有合法快照。grep 原始命中在 evidence 中，不省略失败写分支。
- 交互 P1：生成 → 发送重建 → 同一个 ComposedMail/SendPayload → 成功/失败存档 → 时间线与下载。单改 filename 不需变列或改读路径。

### SMTP 与排期（只做回归，不变实现）

- SMTP：`SmtpMailDeliveryService.kt:101–117` 设置 ICS 数据及 `fileName=calendar.filename`；同类 `74–96` 保留无附件分支。证据见 smtp 段。
- `meeting_calendar_event` schema：V125，UTC DATETIME(6) 两端、source_mail_record_id UNIQUE、contact/mail 外键、ACTIVE 默认。没有 filename 或 zoneId 列。
- 自动排期写：Pending `633–639` 从重建结果得到 Instant；ManualReplySendAttemptService `417–418` 在 finalizeSuccess 调 `createFromSentMail`；MeetingCalendarService 校验源邮件再进入 repository INSERT。该表其他新增/改期/取消路径及 repository SQL 在 grep 回执 calendar 段列明，不因附件改名扩改它们。
- 其他写路径：MeetingCalendarService.createManual `33–60` → repository.insert；update `170–197` → updateMutable；cancel `200–210` → repository.cancel。读路径：get→findById；list→repository.list；summaries→findActiveByContactIds；写前locked→findByIdForUpdate；自动建排期读findBySourceMailRecordId/ForUpdate做幂等。这些方法仍由原MeetingCalendarController或发送成功链调用，不增加读取filename的新消费方。
- 交互 P2：ICS 同一 start/end 进入自动排期；本计划不变事务/唯一约束/状态。
- 交互 P3：已发送快照直接读，不从当前模板/当前文件名算法重建；修改独立排期也不更改源邮件附件。

### 原正文/别名问题

```kotlin
// MeetingConfirmationService.kt:404–407
private fun zoneLabel(zone: ZoneId, zoneIdRaw: String): String = when (zoneIdRaw) {
    "Europe/Istanbul" -> "Türkiye Time"
    "Asia/Shanghai" -> "China Standard Time"
    else -> zone.id.substringAfterLast('/').replace('_', ' ') + " Time"
}
// 同文件 536
return "meeting-$date-${safe.ifEmpty { "expert" }}.ics"
```

中文目录 203/375 行分别给 America/Sao_Paulo 与 Brazil/East 同样的“巴西 · 圣保罗”。这证明问题是名字与 UI 枚举；没有证据要求修复这场会议的实际时间。

### 共用 API

- `MeetingConfirmationController.kt:42–47` 当前仅接 date；`MeetingTimeZoneOption` 有 id/labelZh/aliases/offsetLabel/offsetSeconds。
- `meeting-confirmation.js:837,874` 与 `world-clock.js:10` 都请求该 API，前者无独立国家控件。
- 交互 P4：只能新增可选会议起止参数，旧 mode 保持；前端新字段消费见 02。
- 交互 P5：国家 labels 进入正文也进入 ICS DESCRIPTION；因此旧预览 sha 不会被新版本无条件接受。

## 实现方案

### T-1 离线国家元信息（I-1/I-8）

文件：新增 `scripts/generate_meeting_zone_countries.py`、新增 `src/main/resources/meeting-zone-countries.properties`、新增 `scripts/test_generate_meeting_zone_countries.py`；Models/Service 内定义读取结构。

脚本参数冻结：`--country-catalog`（现有 JSON）、`--zone-labels`（现有 meeting properties）、`--backward`、`--iso3166`、`--output`。不下载、不访问 ES/DB，运行时不调用脚本。

生成规则：
1. 源 ID 从现有中文目录读取，国家与英中文标签按 I-1 合并。
2. 新文件 UTF-8 行：`zoneId=countryCode\tcountryLabelZh\tcountryLabelEn\tcanonicalZoneId`；按 ID 排序，记录输入版本/源 SHA256 注释。canonicalZoneId 是别名解析首次到达的现有 country JSON zone ID，不追过国家边界。
3. UTC 独立写固定项；SystemV 不输出国家条目。未知缺映射、循环、重复 key、缺英中文名均失败并列出 ID，禁止默认为一个国家。SystemV 豁免必须精确覆盖本轮审计列表，不把任意未知前缀都视为成功。
4. 已验证映射有 519 项（含 UTC）；完整预期见 audit JSON，而不是运行时直接读取计划证据 JSON。
5. Service companion 增加 COUNTRY_METADATA lazy map，仿现有 Properties 读取模式；DTO/正文共用该 map。新资源缺失/格式不合法显式配置错误。保留现有构造参数。

重现输入位于 evidence 的 `iana-2026c-backward`、`iana-2026c-iso3166.tab`。通过 CLI 生成代码资源时传这两个固定文件；不重新下载 rolling 最新数据。

### T-2 兼容 DTO 与会议时刻模式（I-2/I-3/I-8）

文件：MeetingConfirmationModels.kt、MeetingConfirmationController.kt、MeetingConfirmationService.kt；对应 ServiceTest/ControllerTest。

在 `MeetingTimeZoneOption` **尾部**新增带默认 null 的字段，旧 Kotlin 构造调用可编译：

```kotlin
val countryCode: String? = null,
val countryLabelZh: String? = null,
val countryLabelEn: String? = null,
val canonicalZoneId: String? = null,
val endOffsetSeconds: Int? = null,
val localTimeIssue: String? = null
```

两种模式：

| 请求 | 实现/返回契约 |
|---|---|
| `?date=2026-10-07` | 调原 `timeZones(date)`；旧字段值/排序不动，增加国家元信息；endOffsetSeconds/localTimeIssue 均 null |
| `?date=2026-10-07&startLocal=2026-10-07T09:00&endLocal=2026-10-07T09:30` | controller 调新 `timeZonesForMeeting`；同一 raw ID 列表，每项两端校验；有效项 offsetSeconds/offsetLabel 为实际起点，endOffsetSeconds 为终点，localTimeIssue=null |
| 只传其中一端、空串、非法日期、date 与 start 日期不同 | 400，可理解文案，不回半份数据 |

两端无效的某项不拖垮整个国家目录：该项 localTimeIssue 取原 gap/overlap/duration 文案，endOffsetSeconds=null；旧 offset 字段保留该项原目录辅助值，但客户端禁止使用有 issue 的项分组/预览。处理顺序固定：起点 gap/overlap → 终点 gap/overlap → 实际 duration。原 preview 的 resolveLocalTime 校验继续执行，目录不是发送授权凭证。

`startLocal/endLocal` 都省略时必须保持旧 Mockito 调用 `timeZones(date)`；不为了新分支去改世界时钟或破坏旧测试打桩。新参数在控制器提供默认 null 以保留直接调用兼容。

### T-3 英文会议时间与文件名（I-4/I-5/I-7/I-8）

文件：MeetingConfirmationService.kt、MeetingConfirmationServiceTest.kt。

- `zoneLabel` 返回 countryLabelEn（无 ` Time` 后缀）；UTC 返回明确特殊名；SystemV 等无元信息输入按 I-8 报错。日期/时间/offsetInfo 拼接保留既有规则。
- 巴西示例：`Wednesday, October 7, 2026, from 9:00 AM to 9:30 AM Brazil (UTC-3)`。
- 原生成器先计算 semanticSha256，再调用 `buildCalendarFilename(startZoned.toLocalDateTime(), semanticSha256)`。使用固定 DateTimeFormatter `HHmm`；代码不读取 salutation。原旧方法的姓名字符清洗删除；若 DASH_RUN 还有别处使用先 grep，仅在无其他用途时删除这个常量。
- 新生成格式测试：`^meeting-[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{4}-[0-9a-f]{8}\.ics$`。**不收紧** `CALENDAR_FILENAME_REGEX`，不升 schemaVersion。
- SUMMARY 中专家姓名及正文称呼仍来自既有模板变量；本需求限定文件名，不扩大内容脱敏范围。
- `semanticSha256` 字段序列、zoneIdRaw 与 UID 算法保持；不同语言显示不影响时间身份。若 input/generatedAt 完全相同，新版自身预览与发送 ICS bytes/hash 一致；旧版正文变化引起的 hash 拒绝属预期。

### T-4 贯通回归（I-5/I-6/I-7）

文件：PendingMailOperationServiceTest.kt、MailboxConversationControllerTest.kt；不改这两个生产服务。

- Pending 真实生成器用 Brazil/East 固定例，断言预览 filename 等于重建后 payload 和 ComposedMail 的 filename，UTC 为 12:00Z–12:30Z，正文含 Brazil (UTC-3)，同一 snapshot 实例贯通。
- Mailbox controller 为新格式与旧姓名格式分别存入合法快照，核对 timeline filename 和下载 Content-Disposition/原件 bytes。旧 fixture 使用明确旧 filename 和匹配 ICS hash，不从新生成器重新获得旧名。
- 沿用 SmtpMailDeliveryServiceTest 对 MIME 文件名/字节等于 snapshot 的用例；该测试类经 A1 修订加入清单，只更新 4 处旧附件名字面量（:449/:516/:548/:597）为新命名确定期望，附件名/字节仍等于同一 snapshot 实例；沿用 ManualReplySendAttemptServiceTest 的成功/失败四分支序列化及幂等；不加“模拟数据库”替代真实 schema 证明。
- 既有 MeetingCalendarSendIntegrationTest 为 `mysqlIt` 门控，只在已配置的隔离测试 MySQL 上执行；未开启就记录 skipped，不能宣称真实 MySQL 验证通过。本次不改持久化/事务逻辑，不要求为计划安装 DB。

## 变更文件清单

| # | 文件 | 修改 |
|---:|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt` | 元信息加载、兼容目录模式、国家文案、新文件名 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationModels.kt` | DTO 追加字段/注释；保留存档 codec |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationController.kt` | 可选起止参数与分派 |
| 4 | `scripts/generate_meeting_zone_countries.py` | 新增纯离线生成器 |
| 5 | `src/main/resources/meeting-zone-countries.properties` | 新增派生国家元信息 |
| 6 | `scripts/test_generate_meeting_zone_countries.py` | 映射/别名/缺失输入测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationServiceTest.kt` | 时间/文案/命名/旧 codec 回归 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt` | 旧/新目录 HTTP 契约 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | 同源发送重建回归 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt` | 新旧附件元信息及下载回归 |
| 11 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt` | 4 处旧附件名字面量改为新命名确定期望（真实生成器夹具，A1 修订加入） |

本计划没有前端文件。未列文件不得为顺手重构而改；验证可运行清单外已有测试。2026-10-04 修订 A1：因必跑命令包含且 `SmtpMailDeliveryServiceTest.kt` 把旧附件名写死为字面量、与 I-5 冲突，将该文件列入清单（10→11），仅允许更新其附件名断言；其余未列文件仍不得改。

## 验收标准

| 不变量 | 必须断言 |
|---|---|
| I-1 | Brazil/East→BR/Sao_Paulo；Asia/Calcutta→IN/Kolkata；Pacific/Ponape→FM/Pohnpei；精确 Europe/Brussels 等国家项不被跨国 link 覆盖；循环/未知/缺英文名生成失败；重复生成 bytes 相同 |
| I-2 | 同 date 无新参数，旧字段逐项与基线相同、排序相同，SystemV/UTC 仍在；旧 HTTP 验证/认证用例继续通过 |
| I-3 | 纽约 2026-03-08 01:30→03:30 返回 -18000/-14400；02:30 gap、11-01 01:30 overlap 项有 issue；其他有效地区仍可用；参数不成对/坏日期/超年份400；India/Nepal 保留分钟偏移 |
| I-4 | Brazil/East 与 Sao_Paulo 同时刻 12:00Z，北京 20:00；同一原输入国家显示变化不改 semantic SHA/UID 计算；跨日/跨偏移完整保留 |
| I-5 | 文件名日期与 HHmm 来自当地开始；尾码等于当前语义摘要前8；改 generatedAt 名称不变；改变会议信息按语义摘要变化；中文/空/标点姓名均不输出名字；不把8位码用于claim |
| I-6 | 新旧 filename 都通过现有 codec；timeline/MIME/download 名称及 bytes 与各自快照一致；NULL/损坏快照按既有逻辑处理；成功/失败存档回归通过 |
| I-7 | Pending 重建相同 bytes；旧hash失配在claim之前400；普通回复无日历；只预览无SMTP/写库；原模板禁用/链接非法/大小上限规则继续通过 |
| I-8 | UTC+0 特例；SystemV 新预览给重新选择文案；原始目录与已发旧附件可读；不将其匹配到任意国 |

执行命令（从仓库根目录；本轮规划未执行新功能测试）：

```bash
python3 -m unittest discover -s scripts -p 'test_generate_meeting_zone_countries.py'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MeetingConfirmationServiceTest,MeetingConfirmationControllerTest,PendingMailOperationServiceTest,MailboxConversationControllerTest,SmtpMailDeliveryServiceTest,ManualReplySendAttemptServiceTest,MeetingCalendarServiceTest
node --test src/test/js/worldClock.test.js
```

生成器验收重跑生成并比对资源 bytes；对文件名不能只断言正则，必须检查固定例的日期/分钟/摘要。数据库真实集成如已有隔离配置：`JAVA_HOME=... mvn test -Pmysql-it -Dtest=MeetingCalendarSendIntegrationTest`，连接只能指向测试库。

## 人工验收清单

### A-1：巴西邮件和会议时刻
- 前置条件：本地/测试环境已部署01；准备测试联系人/来信和可用 MEETING_INVITATION 模板。
- 操作步骤：1. 打开会议确认；2. 选择 America/Sao_Paulo，2026-10-07 09:00–09:30，测试 Zoom 链接；3. 预览不发送；4. 用 Brazil/East 重做。
- 预期结果：英文时间行均含 `Brazil (UTC-3)`，北京时间20:00–20:30；ICS DTSTART `20261007T120000Z`、DTEND `20261007T123000Z`；有过期提示时不影响核对固定例。
- 覆盖：I-1/I-3/I-4/I-7，原时间/模板不变；P4/P5。

### A-2：夏令时和分数偏移
- 前置条件：同一测试联系人。
- 操作步骤：1. 选择纽约2026-03-08 01:30–03:30；2. 起点改02:30；3. 改11-01 01:30–02:30；4. 选择印度09:00–09:30。
- 预期结果：第1步出现 `UTC-5 → UTC-4`；第2步报当地时间不存在；第3步报出现两次；印度显示 `India (UTC+5:30)`。
- 覆盖：I-3/I-4，DST拒绝不变。

### A-3：新文件名稳定
- 前置条件：A-1相同配置。
- 操作步骤：1. 预览并下载；2. 原配置再次预览；3. 查看filename与ICS UID。
- 预期结果：文件名匹配 `meeting-2026-10-07-0900-[8位小写hex].ics`，不含专家姓名/邮箱；相同语义两次名称相同，UID仍按既有生成器规则；不要求不同专家短码数学上绝不碰撞。
- 覆盖：I-4/I-5。

### A-4：受控发送贯通
- 前置条件：仅测试联系人指向受控测试邮箱；测试环境可发测试邮件，或现有测试桩SMTP可查看MIME；严禁用Lucio真实邮箱执行验收。
- 操作步骤：1. 预览并确认填入回复；2. 按原发送校验流程发送一封测试邮件；3. 对照收到附件、页面已发卡片、下载文件和会议日历；4. 用同请求检查既有幂等结果。
- 预期结果：名称/ICS内容贯通一致；排期是同一UTC起止；同请求不新增第二封/第二场。邮件称呼/签名/链接仍来自原配置。
- 覆盖：I-5/I-6/I-7；P1/P2。

### A-5：历史原件
- 前置条件：测试库存在合法旧名 `meeting-2026-09-11-Professor-Basdogan.ics` 的SENT存档，保留其原字节副本。
- 操作步骤：1. 打开历史会话下载；2. 与原副本比较；3. 在日历里改其排期后再次下载。
- 预期结果：旧文件名和字节保持一致；独立排期改期不重写源邮件附件；不出现404。
- 覆盖：I-6；P3。

### A-6：旧调用及生成边界
- 前置条件：保留升级前预览草稿；准备世界时钟与普通回复入口。
- 操作步骤：1. 世界时钟搜索圣保罗/纽约/IANA ID；2. 尝试发送旧草稿再重新预览；3. 发送一封无会议的受控普通回复；4. 用SystemV旧草稿打开预览。
- 预期结果：世界时钟仍是原城市目录；旧草稿若hash变化明确要求重预览；普通回复无新增日历；SystemV提示重新选择国家，不猜国家、不发信。
- 覆盖：I-2/I-7/I-8；P4/P5，不变项3/5。
