# 收齐回复邮件

## 需求描述
垃圾箱中的专家回复也必须进入系统收信箱。检查回复覆盖所有可读取的收件文件夹（含 Junk、归档、自定义目录、已删除邮件），排除已发送、草稿、发件箱和自发邮件；共享转发组中已配置独立 IMAP 凭据的原邮箱也补查。未配置凭据的原邮箱无法直接读取，不能声称已覆盖。

用户已授权系统修改。分三步连续实现，不部署生产、不发送外部邮件。无前端改动。

## 关键不变量
1. 远端身份为实际邮箱 + 文件夹完整路径 + UIDVALIDITY + UID；原 INBOX 数据默认保留 INBOX，不猜历史代际。（来源：K-imap-source-vs-business-account、K-inbound-seen-not-processed-marker）
2. 每文件夹独立游标，失败邮件前停住；已读标记不影响抓取；单个文件夹失败仍尝试其他文件夹，最终明确报错。
3. 同组 Message-ID + 发件人相同的邮件只入库一次；事务内锁组账号避免并发跨文件夹重复；无 Message-ID 不武断合并。
4. 补收文件夹及原邮箱的邮件一律人工处理，不触发 SMTP。原主邮箱 INBOX 的首轮跳过历史规则保持。（来源：K-new-inbox-baseline-and-bounce-scan）
5. 逻辑收件人归属与物理来源分开；附件、已读标记、通知均携带实际来源。旧调用默认 INBOX。

## 现状审计
- `mail_inbox_cursor`：V49 唯一账号；MailInboxCursorService 是运行时写入/查询入口，初始化 insert-only，advance 只推进连续成功前缀。
- `inbound_mail_processing`：V120/V134 唯一键未含文件夹。AutoMailReplyService 的 confirmProcessed / confirmManualReviewWithBody 两个新行落点；PendingMailOperationService、UnmatchedInboundMailService 等通过已有行 copy 更新，保留新增字段。历史脚本/SQL 不带新字段时默认 INBOX。（来源：K-inbound-processing-write-paths）
- 判重/通知通过 owner+validity+uid 查询，必须添加 folder 条件；人工列表/详情按 id、专家、状态查询，无须修改。
- ImapMailReceiveService 所有公共读信/已读操作固定 INBOX；附件 source 已支持 folder，但使用 folder.name 丢失嵌套路径。
- receiveAndAutoReply 是自动/手动检查共用入口；processByUids 保留原 INBOX 运维语义；bounce 补扫保留 INBOX，但各目录常规读取仍识别退信/DMARC。
- listAutoReceiveAccounts 返回物理 owner；原邮箱补查在同组检查内进行，不修改转发映射。

## 实现方案及变更文件清单
### 步骤一：来源契约与持久化（10 个文件、mail/DB 两个子系统）
MailReceiveService.kt、ImapMailReceiveService.kt、MailInboxCursor.kt、InboundMailProcessing.kt、MailInboxCursorRepository.kt、InboundMailProcessingRepository.kt、MailInboxCursorService.kt、V167__scope_inbound_by_folder.sql、MailInboxCursorServiceTest.kt、ImapMailReceiveServiceTest.kt。

两个 store 各新增一个 folder 字段；唯一键纳入 folder，保留 INBOX 兼容查询；接收模型传真实 sourceAccountCode/folder；新增目录列表和指定目录操作。目录发现使用 LIST 而非 LSUB，完整路径按二进制比较。

### 步骤二：轮询与人工补收（6 个文件、mail/DB 两个子系统）
AutoMailReplyService.kt、ExpertInboundNotificationService.kt、AutoMailReplyServiceTest.kt、ExpertInboundNotificationServiceTest.kt、ImapMetadataFetchIT.kt、InboundMailProcessingRepositoryTest.kt。

每组检查主邮箱各目录及有完整凭据、不同连接身份的原邮箱。补收从 UID 0 分批推进；同组内容去重早于任何业务写入。恢复信件进入人工收信列表，按源目录确认已读。通知查询按完整来源。补充跨目录、跨邮箱、失败游标和数据库索引测试。

### 步骤三：完整来源的下游读写（5 个文件、mail/DB 两个子系统）
ExpertInboundNotificationRepository.kt、ExpertInboundNotificationRepositoryIT.kt、ExpertContactLocationService.kt、ExpertContactLocationServiceTest.kt、V168__scope_inbound_notification_by_folder.sql。

补充审计：通知 outbox 的唯一键与 enqueue 查重也只包含 owner/validity/uid，需新增一个 folder 字段并改键，默认 INBOX 保留旧数据；worker 按 outbox id 领取，不需改动。联系时间建议会按物理身份合并样本，读 projection、mapper、去重键需同时带 folder，避免不同文件夹相同 UID 被误合并。该步骤完成原“完整来源贯穿通知和读路径”的范围，不增加产品功能。

## 验收标准
- INBOX/Junk 相同 UID 不互相吞信；复制/移动同一 Message-ID 不重复入库或回复。
- 已读 Junk 回复能进入人工列表，附带真实原邮箱及嵌套目录；未匹配邮箱的回复仍可人工处理。
- 单目录异常不阻止其他目录；失败 UID 不被游标跨过。
- 发件目录、草稿、自发邮件不当作专家回复。
- 原有 INBOX 路由、自动回复与首次初始化回归通过。
- JDK11 相关单元测试；本地 socket IMAP 测试；Docker 可用时验证 MySQL 迁移及约束，不连接生产写入。

## 人工验收清单
- [ ] 发布后点击检查回复，Junk 已读回复出现。
- [ ] 同一邮件移到 INBOX 后再检查不重复。
- [ ] 检查补收附件可下载、原邮箱归属正确。
- [ ] 确认原邮箱缺少 IMAP 配置的账号已补齐；否则说明覆盖限制。

## 验证记录
实现完成，编译通过；9 个测试类合计 236 项通过，0 失败、0 错误、0 跳过。

- 完整测试：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=AutoMailReplyServiceTest,BatchAutoMailReplyServiceTest,MailInboxCursorServiceTest,ImapMailReceiveServiceTest,ExpertInboundNotificationServiceTest,ImapMetadataFetchIT,InboundMailProcessingRepositoryTest,ExpertInboundNotificationRepositoryIT,ExpertContactLocationServiceTest -DmysqlIt=true -DmigrationIt=true test`。其中 216 项通过；旧 JDK 11.0.15 的时区库缺少项目原有的 America/Coyhaique，阻止位置服务测试初始化。
- 位置服务 20 项使用本机已有 JDK 11.0.32 单独复验：`JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -q surefire:test -Dtest=ExpertContactLocationServiceTest`，全部通过。未修改系统 JDK 或时区配置。
- MySQL 在 Testcontainers 隔离容器运行，迁移到 V168；验证不同目录/邮箱相同 UID 可并存、同一物理来源唯一、目录大小写区分、消息副本查重及通知队列去重。
- IMAP 使用本地 socket fixture，验证 LIST、嵌套目录 READ_ONLY、附件来源、已读确认及不执行 EXPUNGE。
- 业务回归覆盖原 INBOX 初始化、共享账号路由、跨目录重复、原邮箱补收、目录异常后继续其他目录、失败游标、自动回复禁止和自发邮件过滤。
- 写入/读取核对：新 processing 两个 sink 带 folder/source；copy 更新保留来源；游标 get/advance 按 folder；通知 lookup/outbox 唯一键带 folder；联系时间样本物理去重带 folder；附件 worker 按真实账号和完整目录读取。
- `git diff --check` 通过。原有其他工作区改动保留。

线上状态：未部署、未迁移生产数据库、未发送真实邮件。发布须同时包含 V167/V168 与代码，随后分批补收；新邮箱原 INBOX 仍按原基线规则跳过历史。无法识别专家的补收进入“未匹配邮件”。原邮箱缺少独立 IMAP 凭据时仍只能检查其转发目标，需补齐配置后才能直接检查原邮箱垃圾箱。
