# 05 · 检查回复进度与机器邮件隔离

状态：待审阅/未执行。前置：04子计划通过独立验证。 范围：10个文件；不超过2子系统。共同契约见[总计划](00-mailbox-materials-master.md)。

## 需求描述

进度明确显示真正正在处理的账号；慢账号/附件任务不无限拖住后续账号；DMARC附件另行有界获取解析。

不得改变：CHECK_REPLIES现有终态/取消token、账号筛选、退信与自检分流、DMARC报表入库去重。

范围外：不新建通用进度系统、不修改任务表、不重写退信分类或SMTP发送。

## 关键不变量

### Invariant I-1：开始与完成分离
- Rule：进入账号前发布currentAccount/phase/start时间；accountsPolled只在账号完成后增长；结束不能保留“正在检查”文案。execution token校验继续生效。
- Applies to：Batch循环、Controller进度
- Violation consequence：把上一账号当成当前卡住账号
- 来源：original

### Invariant I-2：有界接收与取消
- Rule：每次账号IMAP接收窗口总预算120秒（含连接/头/正文目录）；单信60秒；到期真正关闭该连接，失败账号记录后继续下一账号。只约束接收阶段，不能中断已开始的SMTP事务并宣称取消成功。
- Applies to：IMAP读取、Batch取消、异常终态
- Violation consequence：Future取消但网络线程继续/误取消发送
- 来源：K-manual-outreach-executor-shared

### Invariant I-3：机器报告可靠转交
- Rule：DMARC只在源索引持久化并明确排队后确认该UID；purpose=DMARC，不创建专家附件/文档；worker按目标part获取后调用原解析/入库，再完成任务。解析失败可见并可重试，不被日志吞掉。
- Applies to：AutoMailReplyService DMARC分支、transfer consumer
- Violation consequence：丢报表或进入专家列表
- 来源：original

### Invariant I-4：阶段数据含义
- Rule：details.accountProgress.phase限定CONNECTING/READING_METADATA/PROCESSING_MAIL/COMPLETED/FAILED；file transfer进度不计入“已检查邮件”；partial失败保留已经完成的账号数。
- Applies to：任务详情、原任务弹窗message
- Violation consequence：虚假完成或进度交叉
- 来源：original

## 现状审计

[A审计E1–E7](audit.md)与[代码检索全集](code-search-evidence.md)为本计划组成部分：包括表schema、全部生产写/读入口及跨模块交互。执行时先按符号复核，不以旧行号代替源码。 本项直接核对：E1/E3/E4（账号进度/机器邮件/取消）。

新增/改动读写关系：本项实现方案逐任务明确生产者和消费者；只允许表中列出的文件引入这些写路径。迁移/源定位/任务字段的完整类型与默认值见总计划持久化契约，禁止再自增冗余状态。

## 实现方案

1. [I-1/I-4] BatchAutoMailReplyService增加可选onAccountStarted/onStage回调（默认null，兼容原调度/队列调用）；循环进入账号前回调，完成后原onProgress只累计结果。Controller仅在现有TaskProgress.details新增accountProgress一个对象键，内部保存accountCode、phase、startedAt、updatedAt；message直接写“当前账号：LuKai · 读取邮件信息；已完成1/2个账号”。原TaskProgressStore无需改表/接口。
2. [I-2] ImapMailReceiveService在本次读取开始建立独立deadline与连接取消句柄，连接/每封/每段检查预算；阻塞期间由watchdog forceClose，finally清理。取消只在安全边界停止后续邮件/账号；若处于业务处理显示“正在结束当前处理”，不把邮件发送回滚当作可用取消方案。账号结果沿用SUCCESS/FAILED，汇总仍COMPLETED/PARTIAL_SUCCESS/FAILED/CANCELLED，不创造新的任务终态。
3. [I-2/I-4] AutoMailReplyService明确标出接收与处理阶段，不把120秒称为整个含LLM/SMTP任务时限。本期修复可证实的IMAP阻塞原因；LLM/SMTP独立耗时仍由既有客户端超时负责，不伪造全任务SLA。将一次接收失败与已完成的处理数在现有结果中准确返回。
4. [I-3] metadata模式下Auto的DMARC分支调用02队列登记SYSTEM请求，不在检查线程等待。新增DmarcAttachmentTransferConsumer，注册purpose=DMARC，consumer对临时文件有界解压（总XML上限20MiB/最多10个归档成员），拒绝路径穿越与超限压缩；将已解压XML包装为原ReceivedMailAttachment后，先用原DmarcReportParser确认非null，再调用原IngestService入库，避免原ingest的parse-null静默跳过被误判成功。不得直接把压缩内容交给原无界readBytes解压。成功后清理机器报告临时文件，仅保存原报表聚合数据；解析失败任务FAILED、errorCode=DMARC_PARSE_FAILED。队列重试从头获取是第一版允许的行为。原检测器只用文件名，不改规则。
5. [I-1..I-4] 扩展Batch/Controller测试（账号开始回调顺序、token、取消）；协议测试验证慢传输断开后下一账号；新增DMARC consumer测试验证报表写入/重复/解压限额。退信DSN与自检用现有全套测试回归。

任务中的领域文件路径全部以本计划下表为准；类内DTO/辅助函数不另拆文件。若必须增加文件，先修订计划与≤10文件边界。

## 变更文件清单

| # | 精确路径 | 操作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyService.kt` | 修改 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt` | 修改 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 修改 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt` | 修改 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/DmarcAttachmentTransferConsumer.kt` | 新增 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/config/MailAttachmentStorageProperties.kt` | 修改 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyServiceTest.kt` | 修改 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt` | 修改 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMetadataFetchIT.kt` | 新增 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/DmarcAttachmentTransferConsumerTest.kt` | 新增 |

## 验收标准

- I-1/I-4：第二账号读取未完成时显示第二账号且accountsPolled=1，结束无正在检查；旧token拒绝。
- I-2：持续滴流到预算强制断开，下一账号执行；processing/SMTP阶段不被错误取消。
- I-3：DMARC检查线程附件读取0、任务已持久化，worker报表写入；解析失败可重试；专家材料总数不增加。

仅运行后才能记录通过。先本项测试，再mvn test（Java11，含Node），涉迁移追加MigrationIntegrationTest；协议/数据库IT必须按总计划显式启用，不用默认跳过当证据。

## 人工验收清单

### A-1：真实当前账号与超时
- 前置条件：测试2账号WuWei快速、LuKai持续慢速；接收预算在测试配置缩为5秒，页面开启任务弹窗。
- 操作步骤：1. 检查全部；2. 观察WuWei完成后的文字；3. 等LuKai超时；4. 对调账号顺序再检查。
- 预期结果：当前账号显示LuKai时已完成1/2；5秒接收预算后连接断开，终态PARTIAL_SUCCESS；对调后WuWei仍能完成；不存在“正在检查WuWei”却网络停在LuKai。
- 覆盖：I-1/I-2/I-4；本项可观察需求与列明的回归/交互。

### A-2：机器信、取消与回归
- 前置条件：测试邮箱投递DMARC压缩XML、DSN退信、自检信、普通专家信各1封。
- 操作步骤：1. 开始检查后请求取消；2. 再运行完成；3. 查看任务与测试DMARC报表API；4. 使一个报告解析失败后重试。
- 预期结果：取消在安全边界生效；DSN和自检不进专家材料；DMARC后续入原报表且不重复；坏报告明确FAILED/DMARC_PARSE_FAILED，普通专家信继续处理。
- 覆盖：I-2/I-3/I-4；本项可观察需求与列明的回归/交互。
