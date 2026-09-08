# Child 05 Execution — 检查回复进度与机器邮件隔离

- Plan: `docs/plans/2026-09-07/05-reply-check-progress-and-machine-mail.md`
- Plan SHA-256: `c06218518115a460f51dba5f94a896e67ccbce0e7d9f2761248abb41fd336895`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials/docs/plans/2026-09-07/05-reply-check-progress-and-machine-mail.md@c06218518115a460f51dba5f94a896e67ccbce0e7d9f2761248abb41fd336895`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials` @ branch `fast/mailbox-materials`
- Base SHA: `c9f80906c872b21d0f4887c7c3f4c95e961b1cef`（child 04 code head）; 起点 HEAD `64efdadebdfed40bc5a6b47e77150e89c139e1f4`（04 验证记录）
- Result: READY_FOR_VERIFICATION
- Commit: `feat(fast-p): implement 05`（10 个授权文件）

## Changed files（恰好 10 个授权文件）

| # | 路径 | 操作 | 内容 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyService.kt` | 修改 | 可选 `onAccountStarted`/`onStage` 回调（默认 null，兼容原调度/队列调用）；`AccountAutoMailReplyStage` + `AccountAutoMailReplyPhases`（五阶段常量）；进入账号前发布 CONNECTING；READING_METADATA/PROCESSING_MAIL 经 receiveAndAutoReply 透传；完成态由 onProgress 发布；accountsPolled 语义不变 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt` | 修改 | check-replies 运行期统一 publishRunning：只在既有 TaskProgress.details JSON 加单个 `accountProgress` 键（accountCode/phase/startedAt/updatedAt）；message 字面量 `当前账号：<name> · <activity>；已完成<N>/<M>个账号`；阶段活动文案映射；取消请求到达时收尾显示 `正在结束当前处理`（不把 SMTP 回滚伪装成取消）；终态汇总文案与任务终态枚举不变；execution token 校验不变 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 修改 | `receiveAndAutoReply` 增加可选 `onPhase`/`isCancelled`（默认 null）；读取前 READING_METADATA、处理前 PROCESSING_MAIL；取消只在每封邮件安全边界停止；metadata 模式 DMARC 分支改经 02 队列登记 SYSTEM 请求（register+enqueueTransferByIds，purpose=DMARC、attachmentId=null、无专家附件/文档、requested_by=SYSTEM），源索引行持久化且明确入队后才 markSeen；legacy 内联 ingest 原样保留 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt` | 修改 | fetchInboundSince/fetchByUids 走 `withAccountReceiveWindow`：每次读取独立 deadline（默认 120s 可配）+ watchdog 到点真关连接 + finally 清理；连接关闭采用「先关底层 socket 再 IMAPFolder.forceClose」（实测 JavaMail 1.6.x 的 forceClose/protocol.disconnect 会等待在途 literal 读完成，见 notes）；转换后再次 budget.check，杜绝「读到空正文冒充成功」；fetchUnseenMessages/markSeen 不改 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/DmarcAttachmentTransferConsumer.kt` | 新增 | `AttachmentTransferPurposeConsumer`（purpose=DMARC）注册进 child-02 worker seam；有界解压（总 XML 20MiB / 最多 10 归档成员，流式读 .part，不落临时文件），拒绝路径穿越与超限；解出 XML 包装为原 ReceivedMailAttachment 后先用原 DmarcReportParser 确认非 null 再交原 DmarcReportIngestService 入库；压缩内容绝不经原 parser 无界 readBytes；parse-null → `AttachmentTransferConsumeError(DMARC_PARSE_FAILED)` 可见可重试 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/config/MailAttachmentStorageProperties.kt` | 修改 | 新增可配默认值：`accountReceiveTimeoutSeconds=120`、`dmarcMaxExtractedBytes=20MiB`、`dmarcMaxArchiveMembers=10` |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyServiceTest.kt` | 修改 | stub/verify 适配 4 参 receiveAndAutoReply；新增：账号开始回调先于工作且顺序正确、取消后无开始事件（安全边界）、intra-account 阶段顺序/startedAt 一致/totalAccounts 透传、partial 失败保留完成计数 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt` | 修改 | stub/verify 适配 5/6 参；新增：慢账号运行期显示「当前账号：LuKai · 读取邮件信息；已完成1/2个账号」且 accountProgress/accountsPolled 正确、结束无「正在检查」文案、WuWei COMPLETED/LuKai FAILED 完成行、取消请求显示「正在结束当前处理」、全部更新 executionId token=1 绑定 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMetadataFetchIT.kt` | 修改扩展（child 03 已建同路径） | fixture 支持逐字节滴流正文（bodyDripMs）；新增 3 个协议测试：慢滴流超过账号预算被 watchdog 强制断开（elapsed≈1s 而非 30s/读超时）、断开后下一账号新连接正常读取、健康账号在预算内完成 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/DmarcAttachmentTransferConsumerTest.kt` | 新增 | 真实 parser+ingest（repo mock）：gz/raw/zip 多成员入库、report_id 去重、成员数/总量上限（LIMIT_EXCEEDED）、路径穿越拒绝（DMARC_ARCHIVE_INVALID）、坏报表/坏压缩流 DMARC_PARSE_FAILED 且修复后可重试成功 |

## Commands（全部在 worktree 根运行，JAVA_HOME=zulu-11）

| # | 命令 | 退出码 | 证据 |
|---|---|---|---|
| 1 | `mvn test -Dtest=BatchAutoMailReplyServiceTest,MailAutomationControllerTest,DmarcAttachmentTransferConsumerTest` | 0 | 64 run / 0 fail / 0 err（Batch 21、Controller 34、DMARC consumer 9）；BUILD SUCCESS |
| 2 | `mvn -Pmysql-it -Dtest=ImapMetadataFetchIT test` | 0 | 9 run / 0 fail / 0 err，Time elapsed 6.473s；BUILD SUCCESS（协议级滴流 force-close + 下一账号继续均在 1s 预算验证） |
| 3 | `mvn test`（全量，含 Node exec） | 0 | BUILD SUCCESS 02:27；surefire 汇总 3189 run / 0 fail / 0 err / 8 skipped（跳过=8 个受 migrationIt/mysqlIt 门禁的既有 IT 类）；node tests 671 pass / 0 fail / 0 skipped |

附（为修 drip 测试期间跑的额外回归）：`mvn test -Dtest=AutoMailReplyServiceTest,ImapMailReceiveServiceTest,DmarcReportParserTest,DmarcReportIngestServiceTest,DmarcReportDetectorTest` → 0，73 run / 0 fail（AutoMailReplyService 与 ImapMailReceiveService 改动未破坏既有单测）；编译 `mvn -o compile` / `mvn -o test-compile` → 0。

## Invariant 核对

- **I-1（开始与完成分离）**：Batch 进入账号前回调 onAccountStarted(CONNECTING)；READING_METADATA/PROCESSING_MAIL 在账号内发布；完成态只在账号完成后（onProgress）发布；accountsPolled 只随完成增长（测试：慢账号显示期间 accountsPolled=1；结束文案无「正在检查」）；execution token 校验照旧（全部更新 executionId=1 绑定，旧 token 逻辑未动）。
- **I-2（有界接收与取消）**：每次账号接收 120s 默认独立预算 + 单信 60s（既有 metadataTotalTimeoutSeconds）；watchdog 到点真关闭连接（socket close + forceClose），IT 实测 1s 预算 ~1s 断开、慢滴流不再拖满 30s/读超时；取消只在安全边界（每封邮件前 / 账号间）停止后续邮件/账号；业务处理中收到取消显示「正在结束当前处理」，无 SMTP 回滚/伪取消；fetchUnseenMessages（退信 DSN 路径）未改。
- **I-3（机器报告可靠转交）**：metadata 模式 DMARC 附件 → register + enqueue SYSTEM（purpose=DMARC、attachmentId null、inboundProcessingId null）成功后 markSeen；任一失败即不确认该 UID（游标不推进，重试收敛）；consumer 有界解压（20MiB/10 成员/路径穿越拒绝）、先原 parser 非 null 再原 ingest 入库、DMARC_PARSE_FAILED/LIMIT_EXCEEDED/DMARC_ARCHIVE_INVALID 可见可重试；原检测器文件名规则未改；bounce/DSN/self-check 分流未动。
- **I-4（阶段数据含义）**：accountProgress.phase 限定 CONNECTING/READING_METADATA/PROCESSING_MAIL/COMPLETED/FAILED（controller require isValid）；文件传输进度不计入「已检查」；partial 失败保留已完成账号计数（2 个账号 PARTIAL_SUCCESS accountsPolled=2/成功 1/失败 1）；无新增 TaskProgressLog 列/任务终态。

## Deviations & Notes

- 无授权文件之外的改动。临时探针文件 `ProbeForceCloseTest.kt`（诊断用）已删除，未提交。fast-p evidence（docs/plans/fast/**）按约定不随实现提交。
- 协议实现说明（已在代码注释中记录）：JavaMail 1.6.x 的 `IMAPFolder.forceClose()`/`protocol.disconnect()` 均会等待在途 literal 读完成（本地 fixture 实测各阻塞 ~54s，直到滴流发完），不能打断慢服务端阻塞读；因此账号窗口 watchdog 采用「反射关闭 IMAP 协议底层 socket + forceClose 收尾」，实测 4ms 内打断阻塞读。该路径全部 runCatching，失败回退公开 forceClose。
- ImapMetadataFetchIT 的 drip 失败先例（修复前 106.5s 才失败）为上述 forceClose 限制的实证；修复后 1s 预算在 ~1s 失败。
- 队列重试从头重新获取为允许的 v1 行为（consumer 失败行处于 FAILED，可显式重试；DMARC 源唯一键幂等，不产生重复行）。
