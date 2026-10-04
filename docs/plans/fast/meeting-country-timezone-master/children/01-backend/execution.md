# Child 01-backend 执行报告

## Epoch 2 — READY_FOR_VERIFICATION（2026-10-04）

### 身份

- Executor: `MCT01Impl2`（fast-p child 01-backend，epoch 2）
- 批准子计划：`docs/plans/2026-10-04/meeting-country-timezone-01-backend.md`
  - 当前磁盘 SHA-256：`45ded221a832e4c73358f29ea49778a23835189bdbad65f5f7d472ebef40a12c`（含 A1 修订）
  - 原始批准 identity：`commit:fee3a7ca2f3b3b619ac46ee90dc88ddcb459f35f`；A1 修订 commit：`3a896ce28a533cc68fe9107638c3c74b141afc38`（人工批准，10→11 文件）
- 主计划：`docs/plans/2026-10-04/meeting-country-timezone-master.md`
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-country-timezone-master`
- Branch：`fast/meeting-country-timezone-master`
- 执行前 code SHA / HEAD：`1911c044692914b2ebc5a5ada35d2659f73f2051`（child_base `e6e1bf10dc5be548db9c5034ae13f0080ceb4654` 的祖先）
- 执行后 code SHA / Evidence HEAD：`4edfffd`（本地提交 `feat(fast-p): implement 01-backend`，仅 11 个授权文件；evidence 报告由控制器单独提交）

### 改动文件（恰好 11 个授权文件，无清单外改动）

| # | 文件 | 修改 |
|---:|---|---|
| 1 | `src/main/kotlin/.../mail/service/MeetingConfirmationService.kt` | 国家元信息 lazy 目录、`timeZonesForMeeting`、英文国家文案、新附件名、`MSG_DATE_MISMATCH`/`MSG_ZONE_NO_COUNTRY`；删除 `DASH_RUN` 与按姓名生成文件名 |
| 2 | `src/main/kotlin/.../mail/service/MeetingConfirmationModels.kt` | `MeetingTimeZoneOption` 尾部新增 6 个默认 null 字段 |
| 3 | `src/main/kotlin/.../mail/controller/MeetingConfirmationController.kt` | 可选 `startLocal`/`endLocal` 成对分派；旧模式调用不变 |
| 4 | `scripts/generate_meeting_zone_countries.py` | 新增纯离线确定性生成器（精确匹配→`#=` 原目标→Link，循环/未知/缺名失败；SystemV 精确豁免） |
| 5 | `src/main/resources/meeting-zone-countries.properties` | 新增 519 条派生国家元信息（含 UTC；无 SystemV） |
| 6 | `scripts/test_generate_meeting_zone_countries.py` | 12 个映射/别名/`#=`/循环/未知/缺名/幂等/格式单测 |
| 7 | `src/test/kotlin/.../mail/service/MeetingConfirmationServiceTest.kt` | 英文文案/文件名/会议模式/UTC/SystemV/codec 新旧名回归 |
| 8 | `src/test/kotlin/.../mail/controller/MeetingConfirmationControllerTest.kt` | 新旧目录 HTTP 契约、成对校验 400、服务错误 400 |
| 9 | `src/test/kotlin/.../mail/service/PendingMailOperationServiceTest.kt` | Brazil 固定例同源发送重建回归 |
| 10 | `src/test/kotlin/.../mail/controller/MailboxConversationControllerTest.kt` | 新/旧文件名快照 timeline + 下载回归 |
| 11 | `src/test/kotlin/.../mail/service/SmtpMailDeliveryServiceTest.kt` | 仅 4 处附件名字面量改为真实生成器派生的新命名期望 |

### 关键决策

- **国家元信息形态**：`zoneId=countryCode\tcountryLabelZh\tcountryLabelEn\tcanonicalZoneId`，按 ID 排序 + 头部注释记录 `sourceVersion`/四个输入 SHA256；`Properties.load` 读取，缺失/格式不合法抛 `IllegalStateException`。运行时零联网、零脚本调用。
- **会议模式校验顺序**：请求级 `parseLocalDateTime`→年限→`date==start 日期`；每项起点 gap/overlap→终点 gap/overlap→duration(1..1440)。无效项 `localTimeIssue` 取既有文案、`endOffsetSeconds=null`、旧 offset 字段保留 12:00 UTC 目录辅助值。
- **旧模式零变化**：`timeZones(date)` 仍读中文目录、同一排序与字段；仅尾部加法国家字段。
- **文件名**：`meeting-${startLocalDate}-${HHmm}-${semanticSha256.take(8)}.ics`；`HHmm` 用固定 `DateTimeFormatter`；不收紧 `CALENDAR_FILENAME_REGEX`、不升 schemaVersion。`buildCalendarFilename` 的 salutation 参数与 `DASH_RUN` 一并删除（grep 证明唯一用途）。
- **SystemV**：不输出国家条目；会议预览命中 `COUNTRY_METADATA` 缺失即 400「该旧时区没有国家归属，请重新选择国家和时区」，发生在任何 claim/SMTP 之前（生成器只在 preview 出现）。
- **Smtp 夹具**：4 处断言改为 `meeting-2026-09-11-1000-${semanticSha256.take(8)}.ics`（从真实生成器产物取摘要前 8 位，不猜），filename/bytes-equals-snapshot 语义保留。

### 命令与证据（本 epoch 全新执行）

| 命令 | 结果 | 证据 |
|---|---|---|
| `python3 -m unittest discover -s scripts -p 'test_generate_meeting_zone_countries.py'` | PASS | exit 0，Ran 12 tests，OK |
| 生成器重跑并 `diff -q` 比对资源 | PASS | exit 0，bytes identical；`sha256=2ec901c2eccf08fb84253bba3496224d69779b48d441f548450288042d5813f4`；519 条与 `country-mapping-audit.json` 逐项相等（set 相等、0 差异） |
| `mvn test -Dtest=MeetingConfirmationServiceTest,MeetingConfirmationControllerTest,PendingMailOperationServiceTest,MailboxConversationControllerTest,SmtpMailDeliveryServiceTest,ManualReplySendAttemptServiceTest,MeetingCalendarServiceTest`（JDK zulu-11） | PASS | exit 0 / `BUILD SUCCESS`；Kotlin 合计 Tests run 217, Failures 0, Errors 0, Skipped 1；JS 套件（exec 绑定）1434 pass / 0 fail |
| `node --test src/test/js/worldClock.test.js` | PASS | exit 0；tests 41 / pass 41 / fail 0 |

Kotlin 各类计数：MeetingConfirmationControllerTest 16/0/0；PendingMailOperationServiceTest 38/0/0；ManualReplySendAttemptServiceTest 56/0/0；SmtpMailDeliveryServiceTest 40/0/0；MeetingConfirmationServiceTest 48/0/0；MeetingCalendarServiceTest 18/0/0；MailboxConversationControllerTest 1/0/0（**Skipped 1**，`@EnabledIfSystemProperty(mysqlIt=true)` 门控，未配置隔离 MySQL）。

### 基线对照

- 控制器记录基线：7 个 Kotlin 测试类 → exit 0 / BUILD SUCCESS（JS 1434 pass）；`worldClock.test.js` 41 pass / 0 fail。本次一致。
- `MailboxConversationControllerTest` 在基线与本次均为 mysqlIt 门控跳过（非新增偏差）。

### 残余风险 / 不确定项

- `MailboxConversationControllerTest`（含新增新旧文件名 timeline/download 回归）在未加 `-DmysqlIt=true` 时整类跳过，本 epoch 未获得真实 MySQL 证据；如需执行须指向隔离测试库，且不得宣称真实库验证通过。
- `MeetingCalendarSendIntegrationTest` 同样 mysqlIt 门控，本 epoch 未运行。
- `zoneLabel` 英文名取自同版 `iso3166.tab`，个别标签为官方原文（如 `GB` → `Britain (UK)`），文案观感可能与自然语言习惯不同；这是 I-1 冻结来源的直接结果，不在本 child 调优范围。

### 自检

- 计划身份：读取当前磁盘字节（含 A1），未变更。
- 工作树身份：`fast/meeting-country-timezone-master` @ worktree gitdir 未变。
- 变更文件：11/11 均在授权清单内，`git status` 无清单外文件。
- 提交：仅一次本地提交（见文末），不含 `docs/plans/**`；未 push/merge/rebase/amend/squash/reset。

---

## Epoch 1（历史记录，PLAN_CONFLICT）

以下为 epoch 1 的冲突结论，保留作为历史证据；A1 修订已解除该冲突（`SmtpMailDeliveryServiceTest.kt` 列入授权文件 #11）。

## Execution Result: PLAN_CONFLICT

- Executor: `MCT01Impl`（fast-p child 01-backend）
- 批准子计划：`docs/plans/2026-10-04/meeting-country-timezone-01-backend.md`
  （identity `commit:fee3a7ca2f3b3b619ac46ee90dc88ddcb459f35f`）
- 主计划：`docs/plans/2026-10-04/meeting-country-timezone-master.md`
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-country-timezone-master`
- Branch：`fast/meeting-country-timezone-master`
- child_base_sha：`e6e1bf10dc5be548db9c5034ae13f0080ceb4654`（worktree HEAD，执行前未变）
- Commit：**无**（未改动任何文件，未提交）
- 结果：`PLAN_CONFLICT`（对应 brief「需要计划外/未授权文件改动 → BLOCKED（计划冲突）」）

## 冲突结论

在授权范围内**无法**完成本子计划的必跑命令。子计划 I-5/T-3 要求把日历附件名从
`meeting-<date>-<姓名>.ics` 改为 `meeting-<当地开始日期>-<HHmm>-<semanticSha256 前8位>.ics`，
但必跑命令中的 7 个 Kotlin 测试类之一 `SmtpMailDeliveryServiceTest` **未被列入 10 个授权文件**，
且其断言把旧文件名**硬编码为字面量**。改完授权产品代码后该测试必然失败，而修复它需要修改
清单外文件。按子计划「未列文件不得改；若发现确需改其他文件，先修订清单/拆分」与本次派发
「unauthorized file change → BLOCKED，不要自行扩权」，故以计划冲突停止，未做任何改动。

## 精确证据

### 1. 必跑命令包含该测试类

```
mvn test -Dtest=MeetingConfirmationServiceTest,MeetingConfirmationControllerTest,\
PendingMailOperationServiceTest,MailboxConversationControllerTest,\
SmtpMailDeliveryServiceTest,ManualReplySendAttemptServiceTest,MeetingCalendarServiceTest
```

`SmtpMailDeliveryServiceTest` 是 7 个必跑类之一（brief「必跑命令」与子计划「执行命令」逐字一致）。

### 2. 该测试硬编码旧文件名

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt`
（**不在** 10 个授权文件清单内）。

- `:449`（`realMeetingSnapshot()` 内，真实生成器 `preview()` 产物断言）
  `assertEquals("meeting-2026-09-11-Professor-Basdogan.ics", preview.attachment.filename)`
- `:516`
  `assertEquals("meeting-2026-09-11-Professor-Basdogan.ics", calendarPart.fileName)`
- `:548`
  `assertEquals("meeting-2026-09-11-Professor-Basdogan.ics", calendarPart.fileName)`
- `:597`
  `assertEquals("meeting-2026-09-11-Professor-Basdogan.ics", calendarPart.fileName)`

其中 `:449` 在 `realMeetingSnapshot()` 中直接断言真实生成器输出，`.516/.548/.597` 断言
由该快照 `filename` 派生出的 MIME 附件名，均将于授权改动落地后立即失败。

### 3. 新格式与旧字面量必然不等

子计划 T-3 与 brief「下游接口」冻结：文件名 `meeting-2026-10-07-0900-<8位小写hex>.ics`，
即调用 `buildCalendarFilename(startZoned.toLocalDateTime(), semanticSha256)`，
格式 `meeting-<YYYY-MM-DD>-<HHmm>-<8hex>.ics`。

`SmtpMailDeliveryServiceTest` 该夹具输入为 `Europe/Istanbul`、`2026-09-11T10:00`，
新输出前缀必为 `meeting-2026-09-11-1000-`，与旧字面量 `meeting-2026-09-11-Professor-Basdogan`
在断言全等比较下必然不等（无需计算 hash 即可判定失败）。

### 4. 其余必跑类不受授权改动影响（已核对，用于界定冲突范围）

- `ManualReplySendAttemptServiceTest`：文件名取自 `preview.attachment.filename`，动态引用，不受影响。
- `MeetingCalendarServiceTest`（`campaign/service`，非清单内）：`:532` 手工快照名
  `meeting-2026-09-18-Alice.ics`，不经过生成器，仍匹配既有 codec 正则，不受影响。
- `PendingMailOperationServiceTest`、`MailboxConversationControllerTest`、`MeetingConfirmationServiceTest`、
  `MeetingConfirmationControllerTest`：均在授权清单内，本应随计划一并更新，不构成本冲突。
- 唯一清单外受牵连文件即 `SmtpMailDeliveryServiceTest.kt`（4 处字面量）。

## 需要的计划修订（供控制器决定）

任选其一：

1. 将 `src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt`
   加入 01-backend 授权文件清单（10 → 11），授权把上述 4 处旧文件名断言改为
   `preview.attachment.filename` / `snapshot.filename` 等动态引用（或与新帧例一致的期望值）；
2. 或将该测试类从必跑命令中移除并另行安排（不推荐：它是 I-6 真实 MIME 往返回归）。

按子计划文件计数上限（create-p 每计划最多 10 文件），若采纳方案 1，需把该测试归入
本子计划或做一次显式清单修订。

## 已执行 / 未执行

- 未修改任何文件；未运行任何必跑命令（预检即发现授权范围不足，按 execute-p Phase 2
  「resolution requires new files/unauthorized scope → stop」停止）。
- 未提交；worktree 除控制器预置的 `docs/plans/fast/.../ledger.md` 既有改动外保持基线。

## 残余风险 / 不确定项

- 本冲突仅由 `SmtpMailDeliveryServiceTest` 的 4 处字面量引起；授权清单内 4 个测试类与
  其余 2 个必跑类的改动是明确的、无歧义的。
- 一旦清单获修订，剩余实现路径清晰（T-1..T-4），无其他已知冲突。
