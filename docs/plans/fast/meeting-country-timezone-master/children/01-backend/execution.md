# Child 01-backend 执行报告

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
