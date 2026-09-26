# 新邮箱默认跳过历史邮件

## 需求描述

用户要求取消新配置账号默认拉取历史邮件。首次轮询无游标的物理邮箱时，只读取 UIDVALIDITY/UIDNEXT 并保存当前位置；仅后续到达的新邮件进入处理。首次轮询前的邮件均属于本次跳过范围。

保持：已有游标的账号继续断点收信；已读新信仍处理；共享别名使用物理 owner；显式 UID 回补保留；不删除已导入记录、不改模板或发件账号。
范围外：历史数据清理、前端开关、自动回复规则、OAuth、邮件投递优化。当前 Gmail 已有历史游标，需上线时针对该账号单独记录邮箱当前位置，不能重置其它账号。

## 关键不变量

### I-1：缺失游标只建基线
- Rule：uidValidity=null 才是未初始化；合法 uidValidity 且 lastUid=0 是已初始化空邮箱。使用正数 UIDVALIDITY 和 UIDNEXT-1 建基线，不读取正文或标记已读；初始化失败不回退至历史扫描。
- Applies to：收信接口、IMAP 实现、自动收信入口、游标服务。
- Violation consequence：导入历史或跳过空邮箱后第一封新信。
- 来源：original。

### I-2：首次写入不覆盖既有进度
- Rule：数据库按唯一 account key 插入基线，冲突不更新；保留既有连续成功推进及代际变更重扫语义。
- Applies to：mail_inbox_cursor 初始化和现有推进路径。
- Violation consequence：并发初始化覆盖游标或跳过待处理新信。
- 来源：K-inbound-seen-not-processed-marker。

### I-3：物理邮箱与回补边界
- Rule：先解析 owner；基线属于 owner。显式 processByUids 不受首次轮询分支影响。
- Applies to：receiveAndAutoReply、processByUids、共享邮箱解析。
- Violation consequence：误用别名游标或失去人工补抓能力。
- 来源：K-imap-source-vs-business-account、K-process-single-all-callers。

### I-4：退信补扫同样限制范围
- Rule：自动轮询末尾的 collectBounces 必须使用本轮起始 UID 和真实 UIDVALIDITY；不再通过 unseen 全量通道读取基线前历史正文。连接代际不一致时失败，不使用旧 UID 范围。
- Applies to：AutoMailReplyService、BounceCollectionService、ImapMailReceiveService。
- Violation consequence：主收信跳过历史，退信通道仍读取历史。
- 来源：K-inbound-seen-not-processed-marker。

## 现状审计

### mail_inbox_cursor
- Schema：V49，sender_account_code 唯一，uid_validity 必填，last_uid 默认 0；无外键。
- 写路径：MailInboxCursorService.advance，按批次连续成功 UID 保存；迁移仅建表。本计划新增幂等初始化插入。
- 读路径：MailInboxCursorService.get/advance；AutoMailReplyService.receiveAndAutoReply 使用 get 的 lastUid 和 resolveStart；缺行当前返回 null/0，直接从 UID 1 抓信。
- 交互：轮询入口经账号服务解析 owner；单账号、批量、队列、定时和检查回复最终共用 receiveAndAutoReply；独立 BounceCollectionScheduler.runCollection 同样调用该入口。

### IMAP 与业务记录
- ImapMailReceiveService.fetchInboundSince 读取 UID 区间后解析正文。markSeen 是处理成功后的副作用，不作为已处理判据。
- processByUids 通过 fetchByUids 显式回补，共用 processSingle 的业务事务与确认逻辑，不改。
- BounceCollectionService.collectBounces 是 receiveAndAutoReply 末尾的额外读取；fetchUnseenMessages 当前遍历全部未读并复制 MIME，必须同步约束。
- mail_record/inbound_mail_processing 不新增写路径；首次初始化分支提前返回零处理结果，因此不进入这些现有写入路径。
- 线上 LuKai_Gmail 已有 uid_validity=1、last_uid=1 和 5 条 processing 记录；不删除这些记录。

## 实现方案

1. 增加只读邮箱位置接口和值对象；IMAP 使用只读 EXAMINE 获取 UIDNEXT/UIDVALIDITY，沿用连接超时和窗口清理（I-1/I-3）。
2. repository 增加 INSERT ... ON DUPLICATE KEY UPDATE 的 no-op 写法；service 校验基线并初始化。已有行不覆盖（I-2）。
3. receiveAndAutoReply 无游标先建基线并返回 fetched/recorded/replied/manualReview=0；正常轮询保持既有路径（I-1/I-3）。
4. 退信补扫传递本轮起始 UID/UIDVALIDITY，过滤 UID 后才读取 flags/MIME；保留无范围参数调用的兼容默认值（I-4）。
5. 单元和真实本地 IMAP fixture 验证基线、空箱、异常、后续新信、共享 owner、显式回补与退信边界。仅修改下列文件。

## 变更文件清单

路径均相对 src；共 10 个实现/测试文件，无 schema 或前端变更。

| 文件 | 作用 |
|---|---|
| main/kotlin/com/weibo/talentintroduction/mail/service/MailReceiveService.kt | 位置接口 |
| main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt | 位置与退信范围 |
| main/kotlin/com/weibo/talentintroduction/mail/repository/MailInboxCursorRepository.kt | 原子初始化 |
| main/kotlin/com/weibo/talentintroduction/mail/service/MailInboxCursorService.kt | 初始化语义 |
| main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt | 首次轮询分支 |
| main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt | 传递范围 |
| test/kotlin/com/weibo/talentintroduction/mail/service/MailInboxCursorServiceTest.kt | 游标回归 |
| test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt | 收信流程回归 |
| test/kotlin/com/weibo/talentintroduction/mail/service/ImapMetadataFetchIT.kt | 真实协议回归 |
| test/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionServiceTest.kt | 退信范围回归 |

## 验收标准

- I-1：无游标首次只读位置，0 封业务处理；空邮箱保存有效代际/UID 0；连接失败不保存、不抓历史。
- I-2：初始化 SQL 不更新已有行；已有游标及失败重试测试保持通过。
- I-3：alias 初始化调用 owner；后续已读新信仍接收；显式 UID 回补照常处理。
- I-4：真实 IMAP fixture 不请求基线前消息正文；代际不匹配拒绝退信扫描。
- JDK11：mvn test -Dtest=MailInboxCursorServiceTest,AutoMailReplyServiceTest,ImapMetadataFetchIT,BounceCollectionServiceTest -DmysqlIt=true；git diff --check。
- 线上仅针对本轮类做差异发布，不部署工作区中其他未完成改动；无真实发信验收。

## 人工验收清单

### A-1：新邮箱
- 前置条件：测试邮箱 INBOX 有两封旧信，无 mail_inbox_cursor 行。
- 操作步骤：首次检查回复；再向该箱发送一封新信；再次检查回复。
- 预期结果：首次获取 0；第二次获取 1；两封旧信不出现在业务收件列表。
- 覆盖：I-1/I-2。

### A-2：空箱和失败
- 前置条件：空测试邮箱；另一个账号 IMAP 密码暂时不可用。
- 操作步骤：分别检查回复；为空箱投递一封新信后再检查；修正失败账号密码后再检查。
- 预期结果：空箱首次 0、后续 1；失败账号不建立基线，修正后仅初始化 0，不补历史。
- 覆盖：I-1。

### A-3：旧账号与共享邮箱
- 前置条件：已有游标账号有一封新信并在网页标为已读；共享 owner 与别名账号共用另一邮箱。
- 操作步骤：分别检查已有账号和共享别名。
- 预期结果：已有账号仍收到 1 封已读新信；共享组仅推进 owner 游标；既有记录未被删除。
- 覆盖：I-2/I-3。

### A-4：显式回补与退信
- 前置条件：新邮箱已初始化，基线前存在一封旧信和一封未读 DSN。
- 操作步骤：普通检查回复；再通过 backfill-uids 指定旧信 UID。
- 预期结果：普通检查不导入两封历史；显式回补只处理指定旧信。
- 覆盖：I-3/I-4。

## 执行记录

- 2026-09-25：完成上述 10 个实现/测试文件；未修改数据库 schema、UI、发件配置或线上服务。
- 首次 `mvn test`：编译成功，103 项单元测试通过；13 项 IMAP 集成测试因 sandbox 不允许绑定本地端口失败，未出现业务断言失败。
- 使用本次编译产物在允许回环端口的环境运行 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn surefire:test -Dtest=MailInboxCursorServiceTest,AutoMailReplyServiceTest,ImapMetadataFetchIT,BounceCollectionServiceTest -DmysqlIt=true`：116 项通过，0 failures / 0 errors / 0 skipped，22:04:48 完成。
- 日志：`/private/tmp/new-mailbox-skip-history-tests.log`、`/private/tmp/new-mailbox-skip-history-tests-network.log`；`git diff --check` 通过。
- 本轮没有部署。线上执行 20046/AUTO_REPLY_ALL、20047/BOUNCE_COLLECTION 在最后一次检查时仍为 RUNNING；不强制中断在途业务处理。LuKai_Gmail 已存在旧游标，正式切换时还需在收信任务结束后，将该账号基线移至当前邮箱位置；本轮未跳过或删除任何已落库记录。

## 用户授权上线与后续执行

2026-09-25 用户明确授权上线，并追加「当前异常邮件全部标记已处理」。已于 22:37–22:38 发布，仅替换本方案相关编译类；Gmail 游标从 11 移至只读快照位置 441，10 封现存异常已处理并留存审计。116 项测试及线上健康、哈希、数据状态验收通过。详见 `docs/deploy/2026-09-25-new-mailbox-skip-history.md`。以上覆盖前述“本轮没有部署”的阶段性记录。
