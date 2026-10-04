# Child Brief — 01-backend

本文件是 fast-p 控制器的派发契约。**完整契约 = 已批准子计划**，必须逐字读完再动手；本 brief 只补充身份、授权范围、命令与下游接口。

## 身份

- Child ID：`01-backend`
- 批准子计划（权威，逐字读）：`docs/plans/2026-10-04/meeting-country-timezone-01-backend.md`
- 子计划身份：`commit:fee3a7ca2f3b3b619ac46ee90dc88ddcb459f35f`
- 主计划：`docs/plans/2026-10-04/meeting-country-timezone-master.md`
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-country-timezone-master`
- Branch：`fast/meeting-country-timezone-master`
- `child_base_sha`：由控制器在派发消息中给出（同时记录于 ledger 的 Base 列）
- 执行报告（写这里，提交由控制器负责）：`docs/plans/fast/meeting-country-timezone-master/children/01-backend/execution.md`
- 只读证据目录（计划阶段产物）：`docs/plans/2026-10-04/meeting-country-timezone-evidence/`，含 `code-baseline.md`、`grep-receipts.md`、`country-mapping-audit.json`、`iana-2026c-backward`、`iana-2026c-iso3166.tab`、`iana-2026c-zone.tab`、`iana-2026c-version`、`jdk11-timezone-probe.txt`、`plan-self-review.md`。

## 授权文件（只准改这 10 个，其余一律不动；也不得新建清单外文件）

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

明确禁止改：`contact-country-timezones.json`、`meeting-timezones-zh.properties`、`scripts/generate_meeting_timezone_catalog.py`、`MeetingConfirmationModels.kt` 的 `CalendarAttachmentCodec` 正则/schemaVersion、MIME/下载控制器、任何 Flyway 迁移、`world-clock.js`、`mailbox-chat.js`、`index.html`。

## 硬约束（主计划 + 子计划）

- 不新增 DB/ES 迁移或回填；不引入运行时网络、第三方时区库、新 Spring bean 或缓存框架；`MeetingConfirmationService` 构造参数保持不变（不得新增构造依赖）。
- 全部入口保持只读：不得触发 SMTP / 写库 / 状态变更。
- `?date=` 旧目录模式：每个原始 ID、顺序、`labelZh`、`aliases`、`offsetLabel`、`offsetSeconds` 与基线逐项相同；只允许尾部新增字段（默认 null）。世界时钟调用方不得受影响。
- 真实时间身份（原 zoneId/起止 → UTC 起止/北京时间、DST gap/overlap 拒绝、semanticSha256 字段序列与 UID 算法、24h 时长上限、1900–2100 年限、模板/称呼/签名/Zoom 链接校验）保持不变。
- 新文件名：`meeting-${startLocalDate}-${HHmm}-${semanticSha256.take(8)}.ics`（当地开始日期分钟、小写 hex）；不使用系统当前日期/随机 UUID/邮箱/称呼；相同完整语义（即使 generatedAt 变化）文件名一致；不收紧 `CALENDAR_FILENAME_REGEX`、不升 schemaVersion。
- 旧 `meeting-日期-专家名.ics` 快照继续可解析/下载；已发送原件不重建、不改写。
- SystemV 等无国家归属项：国家字段为 null，会议预览使用时返回 `该旧时区没有国家归属，请重新选择国家和时区`，在 claim/SMTP 之前；UTC 是显式特殊项（code=`UTC`）。
- `timeZonesForMeeting` 参数成对；date 必须等于开始日期；结束晚于开始；每个 ZoneId 两端各调 `getValidOffsets`，1 个才有效；0/2 分别给既有 gap/overlap 文案，禁止 `atZone` 自动补齐；有效项再校验实际时长 1–1440 分钟。

## 必跑命令（在 worktree 根目录；JDK 必须用 zulu-11）

```bash
python3 -m unittest discover -s scripts -p 'test_generate_meeting_zone_countries.py'
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MeetingConfirmationServiceTest,MeetingConfirmationControllerTest,PendingMailOperationServiceTest,MailboxConversationControllerTest,SmtpMailDeliveryServiceTest,ManualReplySendAttemptServiceTest,MeetingCalendarServiceTest
node --test src/test/js/worldClock.test.js
```

- 生成器验收：重跑脚本生成资源，比对/确认 `src/main/resources/meeting-zone-countries.properties` bytes（含 519 项已映射 + UTC；13 个 SystemV 不输出国家条目）。
- 文件名验收：不只断言正则，必须检查固定例（Brazil/East 2026-10-07 09:00–09:30）的日期、分钟、摘要前 8 位。
- `MeetingCalendarSendIntegrationTest` 是 `mysqlIt` 门控：未配置隔离测试库时如实记录 skipped，禁止宣称真实 MySQL 验证通过。

基线（控制器已在 master base 记录）：`worldClock.test.js` 41 pass / 0 fail；7 个 child-02 JS 文件 123 pass / 0 fail；Kotlin 7 个测试类基线结果见执行报告要求的 Baseline 段（若控制器提供数值则抄录，否则在本地先跑一次记录）。

## 下游接口（02 冻结消费，必须逐字成立）

- `GET /api/mail/meeting-confirmation/time-zones?date=YYYY-MM-DD`：旧行为不变，尾部新增国家字段（默认 null）。
- `GET /api/mail/meeting-confirmation/time-zones?date=...&startLocal=YYYY-MM-DDTHH:mm&endLocal=YYYY-MM-DDTHH:mm`：新增会议模式；只传一端/空串/非法日期/date≠start 日期 → 400。
- `MeetingTimeZoneOption` 尾部新增（顺序固定，默认 null，旧构造调用可编译）：
  `countryCode`、`countryLabelZh`、`countryLabelEn`、`canonicalZoneId`、`endOffsetSeconds`、`localTimeIssue`。
- 会议模式下：有效项 `offsetSeconds/offsetLabel` 为实际起点，`endOffsetSeconds` 为终点，`localTimeIssue=null`；无效项 `localTimeIssue` 取既有 gap/overlap/duration 文案、`endOffsetSeconds=null`，旧 offset 字段保留目录辅助值。
- 邮件/ICS 文案：`Wednesday, October 7, 2026, from 9:00 AM to 9:30 AM Brazil (UTC-3)`（英文国家名 + 实际偏移；无 ` Time` 后缀；跨偏移 `UTC-5 → UTC-4`）。
- 附件名：`meeting-2026-10-07-0900-<8位小写hex>.ics`。

## 提交与返回

- 用 `execute-p` 流程执行；只改授权文件；用提交 `feat(fast-p): implement 01-backend` 落地**一次**本地提交；提交内**不得**包含 `docs/plans/**`（fast-p 报告/账本由控制器单独提交）。
- 禁止 push / merge / rebase / amend / squash / reset / 改历史；禁止动主工作树与其他 worktree。
- 执行报告写全：改动文件、关键决策、每条命令 + exit code + 计数、基线对照、残余风险。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径。
- 需要计划外的行为决策或新文件 → 立即 `BLOCKED`（计划冲突），不要自行扩权。

## 本子计划的验收断言（自检清单，来自子计划验收标准）

I-1 国名/别名链（Brazil/East→BR、Asia/Calcutta→IN、Pacific/Ponape→FM、Europe/Brussels 精确项不被跨国 link 覆盖；循环/未知/缺名失败；重复生成 bytes 相同）；I-2 旧字段逐项相同；I-3 纽约 2026-03-08 01:30→03:30 为 -18000/-14400、02:30 gap、11-01 01:30 overlap、参数不成对/坏日期 400、India/Nepal 分钟偏移；I-4 巴西两 ID 同时刻 12:00Z/北京 20:00、国名变化不改 semantic SHA/UID；I-5 文件名三项来源与 generatedAt 不敏感、姓名不出现在文件名；I-6 新旧 filename 都过 codec、timeline/MIME/download 与快照一致；I-7 Pending 重建 bytes 相同、旧 hash 失配在 claim 前 400、普通回复无日历；I-8 UTC 特例、SystemV 重新选择文案。
