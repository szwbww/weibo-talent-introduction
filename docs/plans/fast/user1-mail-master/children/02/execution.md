# Child 02 执行记录 — PLAN_CONFLICT

- 状态：**PLAN_CONFLICT**（未提交，未推送）
- 计划：`docs/plans/2026-10-27/user1-mail-02-mail-queries.md`（identity `commit:4d1698bb7c51120e98cfe8f6a412a4589fc9defd`）
- Brief：`docs/plans/fast/user1-mail-master/children/02/brief.md`
- Base（child 01 Code head）：`b12b53625e2fb2e155f13e830c8647a453c13ddb`
- 当前 HEAD：`7203aae94edd32b5581d73d004207af8e0d5997d`（`docs(fast-p): record 01 checkpoint`，未新增提交）
- 分支：`fast/user1-mail-master`

## 1. 冲突结论

已批准的 6 个授权文件**无法同时满足** child 02 的 I-2（“Controller 从 session 传身份给 Service”）与
“不得引入新失败”的要求。实现该契约必须改动第 7 个文件
`src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt`（一个既有的、
base 上通过的测试），其 `reader is allowed on conversation list and messages` 用例对
`MailboxConversationService.listMessages` 的 Mockito stub 只有旧的 4 个具名参数。

控制器按 I-2 显式传 `username = sessionUsername(request)` 后，该用例的 mock 调用不再匹配
（多了一个 `"user1"` 实参），stub 返回 `null`，随后
`jsonPath("$.hasMore")` 对空响应体断言失败。除修改该文件（补 `username = MailReadOnlyPolicy.READER_USERNAME`
或改用 matcher）外，没有满足契约的做法：

1. Controller 不传身份 → 直接违反 I-2（“Controller 从 session 传身份给 Service”，“HTTP Controller 必须显式传 session”）。
2. Controller 仅在 reader 时传 → 该用例的 session 正是 `user1`，仍然不匹配。
3. 保留旧 4 参重载、Controller 调新方法 → mock 调用的仍是新方法，仍返回 `null`。
4. Controller 先调 `hasReadAccess` 再调旧 4 参 `listMessages` → 违反 I-2/实现方案 2（“Service 方法末尾新增
   username:String?=null；user1 先检查 grant 再做原有 findById/…”），且会把准入边界放到 Controller。
5. Service 内部从 RequestContext 取 session → 违反“Controller 从 session 传身份给 Service”，并新增隐式依赖。

计划自身在“实现方案 5”中写明“方法追加默认参数仍须修正 mock 参数匹配，不能跳过原测试”，但
`## 变更文件清单` 只列 6 个文件，未把 `AuthInterceptorTest.kt` 列入。这是计划内部矛盾，需人工裁决：
把该文件加入授权清单（仅改 stub 参数匹配，不改断言语义），或接受 1 个新失败。

Main 裁决（本轮）：**PLAN_CONFLICT，禁止编辑 `AuthInterceptorTest.kt`，禁止提交。**

## 2. 证据（精确位置）

### 2.1 冲突的 Mockito stub 站点

`src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt:377-385`
（用例 `reader is allowed on conversation list and messages`，声明在 `:354`）：

```kotlin
        Mockito.`when`(
            conversationService.listMessages(      // :378
                contactId = 1L,
                accountCode = null,
                beforeCursor = null,
                limit = 50                        // 无 username：Kotlin 默认参数编译为 $default → username=null
            )
        ).thenReturn(ConversationMessageListResponse(emptyList(), null, false))
```

### 2.2 冲突的断言（预期失败点）

`AuthInterceptorTest.kt:390-393`：

```kotlin
        mockMvc.perform(get("/api/mail/mailbox/conversations/1/messages").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hasMore").value(false))   // :393  ← 实际调用未命中 stub，body 为空 → 断言失败
```

`session` 为 `sessionOf("user1")`（`:386`），因此新签名下实际调用为
`listMessages(1L, null, null, 50, "user1")`，与 `... 50, null` 不相等。

失败形态（`@WebMvcTest` + `@MockBean MailboxConversationService`）：未命中 stub → 返回 `null`
→ 响应体为空 → `jsonPath("$.hasMore")` 抛 `AssertionError: No content`。该失败由本 child 的
授权改动直接引入，base 上该用例通过（基线 `mvn test` 的 19 个错误全部在
`ExpertContactLocationServiceTest`）。

### 2.3 触发它的控制器调用点（授权文件内）

`src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt:265-279`：

```kotlin
    @GetMapping("/{contactId}/messages")
    fun messages(
        // 02 (I-2)：身份只取 Session，绝不从 query/body 读取；Service 内部据此做准入。
        request: HttpServletRequest,          // :268
        @PathVariable contactId: Long,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) before: String?,
        @RequestParam(required = false) accountCode: String?
    ): ConversationMessageListResponse = conversationService.listMessages(
        contactId = contactId,
        accountCode = accountCode,
        beforeCursor = before,
        limit = limit,
        username = sessionUsername(request)   // :278  ← 新增身份实参
    )
```

### 2.4 Service 入口（授权文件内）

`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt:223-244`
（尾参 `username: String? = null`，准入先于 `findById`；缺 grant 与缺专家统一
`MailReadAccessDeniedException(MAIL_READ_FORBIDDEN)`）。

## 3. 工作区当前改动（保留原样，未提交）

`git status --porcelain`（2026-10-27，工作区 `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master`）：

```
 M docs/plans/fast/user1-mail-master/ledger.md
 M src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt
 M src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt
 M src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt
 M src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt
 M src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt
?? docs/plans/fast/user1-mail-master/children/02/
?? src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxReadAccessProjectionTest.kt
```

说明：

- `docs/plans/fast/user1-mail-master/ledger.md` 在开工前**已由控制器改脏**（本 child 未触碰）。
- 6 个授权文件的改动内容见 §4；`MailboxReadAccessProjectionTest.kt` 为计划要求的新增文件（列表第 6 项）。
- 无提交：`git rev-parse HEAD` 仍为 `7203aae`；`b12b536` 之后没有本 child 的提交。

## 4. 已写入工作区的实现（完整，符合计划，但被 PLAN_CONFLICT 阻断交付）

### 4.1 `MailboxConversationRepository.kt`

- `expertPredicates(...)` 末尾、所有 OR 组之外追加 reader 专用子句（`:755-767`）：

  ```sql
  EXISTS (
      SELECT 1 FROM expert_mail_read_access emra
       WHERE emra.username = :username
         AND emra.expert_contact_id = u.expert_contact_id)
  ```

  仅在 `MailReadOnlyPolicy.isReader(username)` 时追加；`:username` 为既有固定绑定参数，无字符串拼接。
  count/page/explain 共用同一 `expertPredicates` → 三处天然一致（I-1）。
- 新增 `hasReadAccess(username, contactId): Boolean`（`:473-497`），参数化
  `SELECT COUNT(*) FROM expert_mail_read_access WHERE username = :username AND expert_contact_id = :contactId`；
  无 grant 与专家不存在同为 `false`，无缓存、无写入（I-2）。

### 4.2 `MailboxConversationService.kt`

- `listMessages(contactId, accountCode, beforeCursor, limit, username: String? = null)`
  （尾参默认 `null`，保持既有内部调用/测试编译兼容）。
- reader：先 `hasReadAccess` → 不通过即 `MailReadAccessDeniedException(MAIL_READ_FORBIDDEN)`，
  先于 `expertContactRepository.findById`、正文/timeline/标签/附件查询（I-2）。
- reader：`outboundSentRows` 置空（跳过只服务附件的 `mailRecordRepository.findAllById` 快照读取），
  不调用 `expertMaterialService.resolveMessageAttachments`，四个附件字段固定
  `attachmentCount=0` / `firstAttachmentNames=[]` / `calendarAttachment=null` /
  `outboundAttachments=[]`；正文/主题/方向/时间/来源/发送状态与标签照常（I-3）。
- 非 reader 路径逐字保留原 SQL/字段/排序/附件解析（I-4）。

### 4.3 `MailboxConversationController.kt`

- `messages` 追加 `HttpServletRequest`，调用时传 `username = sessionUsername(request)`（见 §2.3）。
- 列表 `list` 原本已传 `username = sessionUsername(request)`（未改）。

### 4.4 `MailboxConversationRepositoryIT.kt`（真实 MySQL）

新增 5 个用例（`@EnabledIfSystemProperty(mysqlIt)` 门禁内）：

- `reader grant filter counts and pages only the twenty three interleaved grants`：45 名有邮件专家、
  23 名交错授权，`size=20` → 20/3/0，`total=23`，页间无重复；admin/空身份仍 45。
- `reader without any grant sees zero experts while admin stays unchanged`。
- `reader acl ANDs with q account direction date subject keyword and status filters`：q/account/direction/
  date/subject/keyword/pendingOnly/followed 与 ACL 同时成立；未授权姓名/邮箱 0 条。
- `reader explain keeps the grant exists in the plan`：EXPLAIN 计划中出现
  `expert_mail_read_access`（SQL 层 EXISTS，非应用层过滤）。
- `hasReadAccess is a parameterized existence read that honours revocation immediately`。
- 辅助：`insertReadAccess(...)`、`cleanup()` 增加 `DELETE FROM expert_mail_read_access`。

### 4.5 `MailboxConversationControllerTest.kt`（mysqlIt，真实 Service + 真实 MySQL）

新增 4 个用例：reader 列表范围与 `q` 不可绕过（无授权 `total=0` / 单授权 1 / admin 3）；
reader 详情 200 且四个附件字段固定空值、正文保留、`resolveMessageAttachments` 零调用、
未授权与不存在专家均 403 `MAIL_READ_FORBIDDEN`、撤销后新请求 403、admin 仍 2 个附件名；
reader 从不读 `mailRecordRepository.findAllById`（admin 仍投影通用附件）；
reader 游标跨专家 400。辅助：`insertReadAccess(...)`、`stubReader()`、cleanup 增加 grant 表清理。

### 4.6 `MailboxReadAccessProjectionTest.kt`（新增，单测）

6 个用例：准入先于联系人/timeline/标签/附件/快照读取；无 grant 与专家不存在统一 403；
每次请求重读 grant（撤销后下一更早页 403）；reader 游标仍绑定 contact/account；
reader 固定空附件投影 + 正文/标签保留 + 附件依赖零交互；admin 与默认 `null` 身份保留完整附件投影
与快照读取，且从不查询授权表。含 `anyFilter()` 辅助（Kotlin 非空参数 + Mockito matcher 约定：
`Mockito.any<T>() ?: ConversationFilter(...)`）。

## 5. 命令执行与结果（如实记录）

| # | 命令 | 结果 |
|---|---|---|
| 1 | `JAVA_HOME=…zulu-11… mvn -B -Dtest=MailboxReadAccessProjectionTest,OutboundAttachmentFlowTest test` | **exit 1**（唯一一次执行，见下） |
| 2 | `DB_URL=…127.0.0.1:3307/talent_introduction_user1_it… mvn -B -Pmysql-it -Dtest=MailboxConversationRepositoryIT,MailboxConversationControllerTest,CalendarAttachmentIntegrationTest,MailReplyDraftSendIntegrationTest,ExpertMailReadAccessIT test` | **未执行**（Main 裁决后停止） |
| 3 | `git diff --check` | **未执行**（同上） |

命令 1 的真实输出（`Tests run: 15, Failures: 0, Errors: 5`，`BUILD FAILURE`，总耗时 05:08）：

- `OutboundAttachmentFlowTest`：9 个用例全绿（服务/控制器新增尾参的编译与旧调用点兼容已证实）。
- `MailboxReadAccessProjectionTest`：6 个用例中 5 个 error，全部来自**新测试自身**的 Mockito 误用
  `Mockito.any(ConversationFilter::class.java)` —— 该 matcher 返回 `null`，撞上 Kotlin 非空形参抛 NPE
  （`any(...) must not be null`），并因 matcher 残留污染后续用例：
  `setUp:68 InvalidUseOfMatchersException`。
- 该误用已在本轮修正（改为仓库既有约定 `anyValue` 风格：`anyFilter()`），修正后的重跑在
  Main 裁决后立即取消（后台任务 `bg_22` 已 kill），因此**修正版没有绿过的完整命令记录**。

结论：命令 1 的 5 个 error 是测试代码缺陷（已修但未复验），不是产品代码失败；但 child 02
本身因 §1 的 7 文件冲突无法在不新增 `AuthInterceptorTest` 失败的前提下交付，故不计“命令通过”。

## 6. 后续所需的人工决定

1. 把 `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt` 加入 child 02
   授权文件清单（仅把 `listMessages` stub 补成
   `username = MailReadOnlyPolicy.READER_USERNAME`，断言语义不变），同时把计划
   `## 变更文件清单` 更新为 7 个文件；或
2. 明确接受该用例在修复前的 1 个新失败（与“不得新增失败”的整体门禁冲突，不推荐）。

一旦裁决，剩余工作仅为：修正后的命令 1 复跑、命令 2（隔离 MySQL 127.0.0.1:3307）、
`git diff --check`、以及 `feat(fast-p): implement 02` 提交（排除 `docs/plans/fast/**`）。

---

# Epoch 2 执行记录 — READY_FOR_VERIFICATION

- 状态：**READY_FOR_VERIFICATION**（本地提交，未推送；`docs/plans/fast/**` 未纳入提交）
- 计划：`docs/plans/2026-10-27/user1-mail-02-mail-queries.md`（amend 后 identity `commit:d66f54251af163f105c6244a6d06c4b28042518e`）
- Brief：`docs/plans/fast/user1-mail-master/children/02/brief.md`（amend 后 7 文件授权）
- 执行 epoch：RESUME（同一路径新内容 → 新执行 epoch，按 A1/A2 授权补第 7 个文件）
- Base（child 01 Code head）：`b12b53625e2fb2e155f13e830c8647a453c13ddb`
- 开工 HEAD：`d66f54251af163f105c6244a6d06c4b28042518e`
- 实现提交：`8f5b514871fdddf5e78f86bd26fe7e9575194a94` `feat(fast-p): implement 02`（7 文件，`git show --stat` 复核）
- 分支：`fast/user1-mail-master`（提交为该分支 HEAD，且 `git merge-base --is-ancestor` 通过）
- 上一 epoch 的 §1 PLAN_CONFLICT 记录**原样保留**于本文件前部；其冲突已由 amend A1/A2 解决，不再是开放问题。

## 1. 本 epoch 改动

1. **保留 epoch 1 的 6 文件工作区实现**（未 reset/未重建），内容与 §4 记载一致，逐点复核：
   Repository `expertPredicates` 尾部 `MailReadOnlyPolicy.isReader(username)` 分支追加 grant
   `EXISTS`（`:username` 固定绑定，所有既有子句已带括号）；新增 `hasReadAccess(username, contactId)`
   参数化 `COUNT(*)` 读取；Service `listMessages` 尾参 `username: String? = null`，reader 准入先于
   `findById`；reader 跳过附件解析与 `findAllById` 快照读取并固定 4 个附件字段；Controller
   `messages` 追加 `HttpServletRequest` 并传 `sessionUsername(request)`。
2. **A1/A2（第 7 文件）**：`AuthInterceptorTest.kt:377-386` 的 `listMessages` stub 由 4 参数
   同步为 5 参数（补 `username = MailReadOnlyPolicy.READER_USERNAME`），断言语义与白名单断言未改。
3. **修正本 child 自带的新测试**：`MailboxConversationRepositoryIT.reader explain keeps the grant
   exists in the plan` 首轮断言按基表名 `expert_mail_read_access` 匹配 EXPLAIN 的 `table` 列，
   而 MySQL 该列返回的是 SQL 别名 `emra`；改为断言别名行存在（仍证明过滤在 SQL 层 EXPLAIN 计划中，
   不是应用层 filter）。其余新增用例首轮即通过。
4. 除上述外未新增/修改任何文件；未改数据库生产者、迁移、行号锁定文件；读取路径无持久写。

## 2. 命令执行（本 epoch 全部重新执行）

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | `mvn -B -Dtest=MailboxReadAccessProjectionTest,OutboundAttachmentFlowTest,AuthInterceptorTest test`（JAVA_HOME=zulu-11） | 0 | BUILD SUCCESS（02:59）。`MailboxReadAccessProjectionTest` 6/0/0、`OutboundAttachmentFlowTest` 9/0/0、`AuthInterceptorTest` 19/0/0；同次 `mvn test` 生命周期内的 JS 门禁 `node --test` 1851 pass / 0 fail。 |
| 2 | `DB_URL='jdbc:mysql://127.0.0.1:3307/talent_introduction_user1_it?...&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' DB_USERNAME=root DB_PASSWORD=root mvn -B -Pmysql-it -Dtest=MailboxConversationRepositoryIT,MailboxConversationControllerTest,CalendarAttachmentIntegrationTest,MailReplyDraftSendIntegrationTest,ExpertMailReadAccessIT test` | 1 | `Tests run: 127, Failures: 0, Errors: 7`。0 个本 child 引起的失败；7 个 error 全部为既存缺陷（见 §3）。 |
| 3 | `git diff --check` | 0 | 无空白/冲突标记问题（提交前最终状态）。 |

命令 2 的分项计数（`target/surefire-reports`）：

- `MailboxConversationRepositoryIT`：40 tests / 0 failures / 1 error（35 base + 5 新增；唯一 error 是
  既存 `replied filter excludes followed and dismissed experts until a new inbound arrives`
  → `DataIntegrityViolation`：`expert_replied_dismissal.dismissed_at` 无默认值）。
- `MailboxConversationControllerTest`：45 tests / 0 / 0（含 4 个新增 reader 用例）。
- `MailReplyDraftSendIntegrationTest`：27 / 0 / 0。
- `ExpertMailReadAccessIT`：9 / 0 / 0。
- `CalendarAttachmentIntegrationTest`：6 tests / 0 / **6 errors**（见 §3）。

## 3. 既存失败（非本 child 引起，未修）

1. `MailboxConversationRepositoryIT.replied filter ...`（1 error）—— 计划/brief 已点名的基线缺陷，
   按要求不修不隐藏。
2. `CalendarAttachmentIntegrationTest` 6 个 error —— **brief 的基线描述在此项上不准确**。该类的
   `@WebMvcTest` context 缺 `PendingMailOperationService` 的 `@MockBean`（controller 构造参数 3），
   启动即 `NoSuchBeanDefinitionException`。本 epoch 用只读临时 worktree 在 base
   `b12b536` 上实测同一命令（`-Dtest=CalendarAttachmentIntegrationTest`），得到**完全相同**的
   6 个 error 与相同根因：

   ```
   Tests run: 6, Failures: 0, Errors: 6, Skipped: 0   (base b12b536, 隔离 MySQL 3307)
   Caused by: NoSuchBeanDefinitionException: No qualifying bean of type
     'com.weibo.talentintroduction.mail.service.PendingMailOperationService' available
   ```

   即该缺失自 base 起就存在，与 02 的改动无关（base 与当前 controller 构造函数、
   该类的 `@Import`/`@MockBean` 集合逐字相同）。修复它需要给该测试类补 mock（或改 slice 配置），
   属于本 child 未授权的行为/范围扩张，故仅记录不修。

## 4. 不变量证据映射

- I-1：`MailboxConversationRepositoryIT` 真实 MySQL 45 名有邮件专家、23 名交错授权 → size20 返回
  20/3/0、total=23、页间无重复且全部落在授权集；空授权 total=0/items=[]；q/account/direction/
  date/subject/keyword/pending/followed 与 ACL 同时成立；EXPLAIN 计划含 `emra` 半连接行。
- I-2：`MailboxReadAccessProjectionTest` 证明准入先于联系人/timeline/标签/附件/快照读取、缺 grant 与
  缺专家同为 403、每次调用重读 grant、游标仍绑 contact/account；ControllerTest 证明未授权与不存在
  contact 均 403 `MAIL_READ_FORBIDDEN`、撤销后新请求 403、跨专家游标 400、`q` 不可绕过。
- I-3：两处测试断言 reader 四个附件字段固定 `0/[]/null/[]`、`resolveMessageAttachments` 与
  `mailRecordRepository.findAllById` 零交互，正文与标签保留。
- I-4：admin/默认 null 身份保留完整附件投影与快照读取，且从不查询授权表；`OutboundAttachmentFlowTest`
  9 用例全绿证明附件外发链路未被改动；`activeAccountCodes` 逻辑未动；读取路径无写。

## 5. 提交与边界

- 提交只含 7 个授权文件（`git show --stat HEAD`：controller/repository/service 三个主文件 +
  4 个测试文件），`docs/plans/fast/**` 未纳入。
- 未推送、未合并、未 amend、未改写历史；未触碰其他 child。
