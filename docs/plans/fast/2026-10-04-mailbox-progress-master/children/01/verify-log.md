# Fast-P Child 01 Verification

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 01（`docs/plans/2026-10-04/mailbox-progress-01-backend.md`）
Boundary: 9594d4b3b3c3022b2be2bc04b24d90ee40a58ad4..dbe79c2bfb466bedb2c70d767bdd34082307579c（head = `feat(fast-p): implement 01`）
Verifier: VerifyMailboxProgress01

### Four Gates
|Gate|Result|Evidence|
|---|---|---|
|Authorized scope|PASS|`git show --name-only dbe79c2` = 授权 10 文件逐字一致；`git diff --name-only 9594d4b..dbe79c2 -- 'src/**'` 只列出这 10 个（5 main + V149 + 4 test）；区间内其余 3 提交仅 `docs/plans/fast/**` 控制方工件。工作树 clean|
|Plan and invariants|PASS|I-1 `V149__…sql:13-15`（`VARCHAR(16) NOT NULL DEFAULT 'FOLLOWING'`）、`ExpertFollowService.kt:18` 枚举；I-2 `ExpertFollowService.kt:84-122`（存在性校验 + `:126-141` upsert 不刷新 created_at/`:104-110` NONE 单删）；I-3 `ExpertFollowService.kt:57-78`（旧 DELETE 仅 `progress_status='FOLLOWING'`）；I-4 `MailboxConversationRepository.kt:188-193,229-234` 同源投影、`:66-72` followed 派生、`:684-700` 同层谓词、`:205/785-796` mapper、`:263-266` orderByClause 归非 pending-first；I-5 `:718-724` NOT EXISTS 整行排除；I-6 `MailboxSuspensionService.kt:177-194,213-224`；测试逐条见下|
|Required commands|PASS|B1 exit 0 BUILD SUCCESS；B2 exit 1 Tests run **81**/F0/E1（同唯一既有 `dismissed_at` error，`MailboxConversationRepositoryIT.kt:226`）；B3 exit 0 **36**/0/0 BUILD SUCCESS，均与 baseline.md 一致|
|Downstream interfaces|PASS|`MailboxConversationController.kt:210,290` PUT `/api/mail/mailbox/conversations/{contactId}/progress-status` → `MailboxProgressResult`（`ExpertFollowService.kt:29-33`）含 `{contactId,progressStatus,followed}`；`:239,254-258` `providedOnly`；`:85-90,89` 列表项 `progressStatus`；`MailboxSuspensionService.kt:10,28-31,183-192` 挂起响应含 `progressStatus` 且 `followed=(==FOLLOWING)`|

### Evidence detail

Invariant → test/code receipts:
- I-1: `FlywayMigrationIntegrationTest.kt:2030-2033`（迁到 147 后断言无 `progress_status` 列 → 升级 149，行数/key/created_at 保留、全 FOLLOWING、省略列 INSERT 仍 FOLLOWING）；`MailboxConversationControllerTest.kt:357-399` 六转换 one-row/NONE 即删行。
- I-2: `MailboxConversationControllerTest.kt:422-443`（匿名/空白/幽灵 401）、`:401-419`（非法/缺失 400、未知专家 404，`followRows==0`）、`:445-469`（body username 不可改 owner；用户隔离）、`:557-585`（8 线程并发仍 1 行且完整提交态）；`ExpertFollowService.kt:111-124` 存在性校验。
- I-3: `MailboxConversationControllerTest.kt:471-499`（新 PROVIDED → 旧 DELETE 仍 PROVIDED；旧 PUT 转 FOLLOWING 且重复不重置 created_at）。
- I-4: `MailboxConversationRepositoryIT.kt:830-861`（投影三值/两筛选同传得空集/count 同谓词）、`:862-886`（21=20+1 无重复 + EXPLAIN 可执行）、`:887-923`（AND q/账号/日期/主题/标签）、`:924-940`（排序最近真实来信倒序、无来信置底、不因发送时间提升）；`MailboxConversationControllerTest.kt:500-556`（列表 progressStatus 与 followed 恒一致）。
- I-5: `MailboxConversationControllerTest.kt:586-604`（写标记前后 6 张业务表计数不变）；`MailboxConversationRepositoryIT.kt:941-971`（已回复对 FOLLOWING/PROVIDED 均 NOT EXISTS 排除，取消后按原资格恢复，不改水位）。
- I-6: `MailboxSuspensionServiceIT.kt:254-322`（GET/PUT/PATCH/DELETE 均带真实 progressStatus；PROVIDED → followed=false；切换标记不改挂起其它字段；消息处理不改标记）。

T-1: V149 内容与计划 SQL 逐字一致（注释 + 单列 `VARCHAR(16) NOT NULL DEFAULT 'FOLLOWING'` + COMMENT），未改 V121、未加索引/表。迁移测试 26 处“最新版本”`"147"`→`"149"`；仅剩 147 命中为 V147 历史行为测试名（`:1922`）与本片新用例显式迁到 147（`:2030-2033`），未改历史断言。
T-2/T-3: 新 PUT 与 `providedOnly`；`ConversationItemResponse.progressStatus` 尾部默认从 followed 派生仅供旧构造兼容，真实路径由 `MailboxConversationService.kt:206-210` 显式传入一次读取值。
T-4: `isFollowed`（行存在）→ `progressStatusOf` 一次真值读取。

Command receipts（fresh）：B2 per-file `MailboxConversationControllerTest 41/0/0`、`MailboxConversationRepositoryIT 30/0/1`、`MailboxSuspensionServiceIT 10/0/0`；唯一 error 文本 `INSERT INTO expert_replied_dismissal (username, expert_contact_id, last_inbound_id) … Field 'dismissed_at' doesn't have a default value`，与 baseline 逐字一致。B3 surefire `36/0/0`，日志确认 `Migrating … version "149 - add expert follow progress status"`。未发生 Flyway 历史冲突，未执行 DROP+CREATE。

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1（环境/协调，非本片代码缺陷）：实施报告记录 `ti-mysql-it` 共享测试库因并行分支已应用 V148 而与本 worktree 149 冲突，实施者按 brief 约束 6 重置库 `talent_introduction`。本次复验时库已处于 v149、无 V148，B2/B3 未再需要重置。风险：同一容器库的 `fast/2026-10-04-discovery-review-master` worktree 后续 mysqlIt 会看到 “V149 not resolved locally”，需双方重置或完成编号/发布顺序协调（主计划“编号协调”已预告）。改由控制方判断，不进入本片结论。

### Required Action
- COMPLETE_CHILD
