# 02 · 有界、持久化附件传输服务

状态：待审阅/未执行。前置：01子计划通过独立验证。 范围：10个文件；不超过2子系统。共同契约见[总计划](00-mailbox-materials-master.md)。

## 需求描述

提供独立于检查回复的可恢复文件下载基础服务；重复请求同一附件仅一个活动任务。

不得改变：现有收信仍走旧模式；不占用 manualOutreachExecutor；文件与审核语义不变。

范围外：不新建消息中间件、对象存储、分块续传、OCR或通用任务平台；本阶段不开放UI。

## 关键不变量

### Invariant I-1：来源唯一身份
- Rule：远端身份为account/folder/UIDVALIDITY/UID/partPath；Message-ID仅复核，文件名不作唯一键；MATERIAL必须attachmentId，DMARC必须无attachmentId/专家。
- Applies to：new transfer表、登记和任务服务
- Violation consequence：同名覆盖、串件或机器报告进入专家材料
- 来源：original

### Invariant I-2：状态与请求
- Rule：METADATA_ONLY→QUEUED→DOWNLOADING→STORED；可失败到FAILED/SOURCE_UNAVAILABLE；重复提交QUEUED/DOWNLOADING/STORED不新建；失败重试必须显式请求；重启仅恢复已请求任务。
- Applies to：全部状态写路径、重启恢复
- Violation consequence：重复下载、重启自动拉全邮箱
- 来源：original

### Invariant I-3：资源与租约
- Rule：独立全局2并发、每账号1；单文件100MiB、总时长10分钟、connect/read各10秒；64KiB流式缓冲。使用DB条件更新领取+workerToken；租约过期可回QUEUED，但旧worker必须停止且不能提交。
- Applies to：worker、超时/异常/finally、恢复
- Violation consequence：持续慢传输耗尽线程或旧任务覆盖新结果
- 来源：K-manual-outreach-executor-shared

### Invariant I-4：可见文件与归属
- Rule：所有验证、生成路径、临时.part、实际字节限额、原子转正完成后才写storage_path/file_size并标STORED；每次领取及最终提交重新核对owner；完成不写审核/专家状态。
- Applies to：worker文件写、DB提交、下载读者
- Violation consequence：半文件被分析或跨专家泄露
- 来源：K-document-file-read-via-storage-path

## 现状审计

[A审计E1–E7](audit.md)与[代码检索全集](code-search-evidence.md)为本计划组成部分：包括表schema、全部生产写/读入口及跨模块交互。执行时先按符号复核，不以旧行号代替源码。 本项直接核对：E1/E2/E4（文件/队列/独立执行器）。

新增/改动读写关系：本项实现方案逐任务明确生产者和消费者；只允许表中列出的文件引入这些写路径。迁移/源定位/任务字段的完整类型与默认值见总计划持久化契约，禁止再自增冗余状态。

## 实现方案

1. [I-1/I-2] 新建 V119 的 mail_attachment_transfer（完整字段契约见 master“持久化契约”），领域类型与Repository各一文件；支持按源唯一幂等登记、CAS领取、CAS提交、过期租约恢复、账号活动计数。新表完整实体不是向既有共享表同时加多个字段；旧 mail_attachment 不加列。
2. [I-2/I-3] AttachmentTransferService 接收已验证的attachmentIds（最多500/请求），事务内全部校验归属后入队；全局排队上限5000，超额返回429，不影响元数据登记。定时扫描DB领取，不使用无界内存队列，不每个文件创建线程。worker实现、purpose consumer接口写在 AttachmentTransferWorker.kt；消费者按purpose注册，未知purpose保持未执行并报配置错误。
3. [I-1/I-3/I-4] ImapAttachmentContentFetcher 打开独立READ_ONLY连接，校验folder UIDVALIDITY→UID存在→Message-ID（有值则比较）→part路径/类型；只流式取目标part。不得按subject/from搜索替换来源，不下载整个message。强制关闭连接执行总时限，不能仅Future.cancel。客户端文件名不参与路径拼接。
4. [I-4] 路径为basePath/transfer/{id}/{workerToken}.part及固定最终文件；FileChannel/Files.move原子转正后事务更新，故障注入覆盖“文件已转正DB未提交”，重启通过该任务确定性路径核验与收敛，不重复创建专家资料。失败删除.part；历史存储根路径不搬迁。
5. [I-2/I-3] MailAttachmentStorageProperties 新增上述可配置默认值及 metadataOnly=false；用现有ConfigurationProperties扫描注册，不改application配置。queuedAt、startedAt、leaseUntil、bytesDownloaded、errorCode/errorMessage与attempt更新按master契约；错误脱敏，不记密码/邮件正文。
6. [I-1..I-4] 单元+本地IMAP传输集成测试；MigrationTest目标119。本阶段通过测试服务直接调用队列，不开放未鉴权HTTP。

任务中的领域文件路径全部以本计划下表为准；类内DTO/辅助函数不另拆文件。若必须增加文件，先修订计划与≤10文件边界。

## 变更文件清单

| # | 精确路径 | 操作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V119__create_mail_attachment_transfer.sql` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailAttachmentTransfer.kt` | 新增 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentTransferRepository.kt` | 新增 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferService.kt` | 新增 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferWorker.kt` | 新增 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapAttachmentContentFetcher.kt` | 新增 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/config/MailAttachmentStorageProperties.kt` | 修改 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferServiceTest.kt` | 新增 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferWorkerIT.kt` | 新增 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 修改 |

## 验收标准

- I-1：同名跨UID/part得到不同任务；相同源并发登记仅一行；错UIDVALIDITY/Message-ID拒绝。
- I-2：重复提交10次仅1下载；重启METADATA_ONLY保持不动、过期DOWNLOADING重新领取；失败非自动无限重试。
- I-3：慢速每秒1字节仍在总时限内失败；连接已关闭；两账号最大2并发，同账号最大1；超过100MiB立即失败。
- I-4：.part不能被GET/分析；转正与DB提交间崩溃恢复；旧lease不能提交，审核不变。

仅运行后才能记录通过。先本项测试，再mvn test（Java11，含Node），涉迁移追加MigrationIntegrationTest；协议/数据库IT必须按总计划显式启用，不用默认跳过当证据。

## 人工验收清单

### A-1：重复、断网与恢复
- 前置条件：测试IMAP放2个同名不同内容附件和1个慢速文件；测试配置总时限5秒、单文件1MiB；通过集成测试fixture建立源索引。
- 操作步骤：1. 服务测试入口连续提交同一附件10次；2. 同账号与另一账号各提交2件；3. 下载中断网/重启；4. 再提交失败件。
- 预期结果：同一附件只有1个活动任务；同账号并发1/全局2；5秒总时限到后连接被关闭；其余文件继续；重启不下载从未请求的文件；重试成功STORED且审核仍PENDING_REVIEW。
- 覆盖：I-1/I-2/I-3/I-4；本项可观察需求与列明的回归/交互。

### A-2：来源丢失与旧文件回归
- 前置条件：fixture有一个已下载文件和一个未下载远端源；删除未下载源或修改测试UIDVALIDITY。
- 操作步骤：1. 请求缺失源；2. 下载已落地文件；3. 用越界路径fixture请求下载。
- 预期结果：缺失源SOURCE_UNAVAILABLE；已落地文件仍可读；越界拒绝；无自动改找另一封邮件。
- 覆盖：I-1/I-4；本项可观察需求与列明的回归/交互。
