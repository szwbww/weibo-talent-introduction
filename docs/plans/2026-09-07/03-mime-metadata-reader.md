# 03 · MIME 元数据读取与正文白名单

状态：待审阅/未执行。前置：02子计划通过独立验证。 范围：8个文件；不超过2子系统。共同契约见[总计划](00-mailbox-materials-master.md)。

## 需求描述

具备不读取附件内容的收信模式，保留完整附件名称/定位并正确取得邮件正文；默认开关暂不启用。

不得改变：旧模式兼容现有ByteArray消费者；multipart/alternative、混合正文、DSN识别。

范围外：不变更收信业务状态、不推进新游标逻辑、不增加图片OCR。

## 关键不变量

### Invariant I-1：描述符与字节互斥
- Rule：metadataOnly模式content=null，每个附件有远端source描述；旧模式content非null，默认构造兼容既有调用；encodedSize只是估算、不可写成实际fileSize。
- Applies to：ReceivedMail/Attachment DTO、IMAP、兼容消费者
- Violation consequence：误拿null当空文件，漏下游DMARC
- 来源：original

### Invariant I-2：零附件内容读取
- Rule：先按filename/disposition排除附件，再按MIME类型遍历multipart容器及合法正文；附件禁止getInputStream/getContent；命令日志不得出现附件BODY[]/part内容FETCH。
- Applies to：fetchInboundSince/fetchByUids/fetchUnseenMessages共用解析
- Violation consequence：只改附件函数但正文继续下载附件
- 来源：K-extract-body-multipart-subtype

### Invariant I-3：完整与有界
- Rule：不因19/20/1000附件截断目录；单信正文读取上限2MiB、MIME节点上限10000、元数据总时限60秒，超限明确失败或正文截断标志，不能静默声称完整。
- Applies to：MIME递归和ReceivedMail新增bodyTruncated
- Violation consequence：假完整清单/无界内存
- 来源：K-mime-dsn-content-handler-absent

## 现状审计

[A审计E1–E7](audit.md)与[代码检索全集](code-search-evidence.md)为本计划组成部分：包括表schema、全部生产写/读入口及跨模块交互。执行时先按符号复核，不以旧行号代替源码。 本项直接核对：E1/E4（正文/附件/DSN/DMARC）。

新增/改动读写关系：本项实现方案逐任务明确生产者和消费者；只允许表中列出的文件引入这些写路径。迁移/源定位/任务字段的完整类型与默认值见总计划持久化契约，禁止再自增冗余状态。

## 实现方案

1. [I-1] MailReceiveService.kt 中保留现有构造参数顺序，将content改ByteArray?并默认null；新增ImapAttachmentSource值对象(accountCode,folder,uidValidity,uid,partPath,messageId,encodedSize,disposition)，ReceivedMail增加实际uidValidity和bodyTruncated默认值。ReceivedMailAttachment新增source可空（仅旧模式允许空）。
2. [I-2/I-3] ImapMailReceiveService 优先FetchProfile的ENVELOPE/CONTENT_INFO/UID及必要Message-ID/In-Reply-To头，不预取MESSAGE全内容。multipart类型确认后才能取容器；带文件名的text/plain/text/html不当正文。alternative只选一份正文；mixed按顺序合并；DSN机器段有界读取；附加message/rfc822作为一件附件、不递归其内容。无文件名但disposition=ATTACHMENT也登记“未命名附件-{partPath}”；无名内联签名不当专家材料。
3. [I-1] MailAttachmentService/DmarcReportParser 暂保留旧写文件/解压流程，但content=null时明确报“metadata content unavailable”，不能?:byteArrayOf()；最终消费由04/05替换。metadataOnly默认false，故此阶段仍可独立部署旧业务。
4. [I-3] 目录不能部分成功后推进游标；超MIME节点/元数据超时抛明确可重试错误。正文超2MiB保留有界正文及bodyTruncated=true，04转人工且不自动回复。JavaMail物理FETCH日志需验证限制发生在字节读取期间，不能getContent拿完整String后再截断。
5. [I-1..I-3] 扩展真实MimeMessage写出再读入的测试（带编码中文/超长名、text附件、无名附件、嵌套rfc822、DSN、alternative）。新增本地IMAP脚本式服务测试，捕获命令证明不拉附件；构造1000附件元数据，用会抛错的附件流断言零访问。

任务中的领域文件路径全部以本计划下表为准；类内DTO/辅助函数不另拆文件。若必须增加文件，先修订计划与≤10文件边界。

## 变更文件清单

| # | 精确路径 | 操作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReceiveService.kt` | 修改 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt` | 修改 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt` | 修改 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/DmarcReportParser.kt` | 修改 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/config/MailAttachmentStorageProperties.kt` | 修改 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveServiceTest.kt` | 修改 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMetadataFetchIT.kt` | 新增 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/DmarcReportParserTest.kt` | 修改 |

## 验收标准

- I-1：老构造与所有现有消费者编译通过；metadata模式content严格为null且source完整；无未知大小=0。
- I-2：真实协议记录只读指定正文段，附件流访问0；DSN/alternative回归，不能只使用内存Multipart Mock。
- I-3：1000条完整；10001节点错误可见；慢正文有界；bodyTruncated进入后续可判定字段。

仅运行后才能记录通过。先本项测试，再mvn test（Java11，含Node），涉迁移追加MigrationIntegrationTest；协议/数据库IT必须按总计划显式启用，不用默认跳过当证据。

## 人工验收清单

### A-1：大量附件与正文
- 前置条件：本地测试IMAP投递2封分别19/20附件邮件，另投递1000附件、一个text/plain附件、一个DSN；开启测试metadataOnly。
- 操作步骤：1. 执行元数据读取集成用例；2. 查看命令记录与返回JSON；3. 改用旧模式重跑原接收用例。
- 预期结果：两封39条及压力信1000条均完整；附件流读取0；text附件不出现在正文；DSN状态能解析；旧模式仍返回原附件字节。
- 覆盖：I-1/I-2/I-3；本项可观察需求与列明的回归/交互。
