# 01 · 附件元数据存储兼容

状态：待审阅/未执行。前置：无；先核对audit快照。 范围：10个文件；不超过2子系统。共同契约见[总计划](00-mailbox-materials-master.md)。

## 需求描述

允许附件仅登记名称、尚无实际大小和本地路径；历史文件仍可下载、预览和分析。

不得改变：既有附件 owner XOR、专家归属与 realpath 校验；资料审核状态；已落地文件 Content-Length。

范围外：不启用元数据收信、不新增队列、不改页面。

## 关键不变量

### Invariant I-1：空值语义
- Rule：file_size NULL=尚未取得真实字节数；storage_path NULL=没有可用本地文件；不得用0/空字符串代表未下载。真实零字节文件为size=0且文件实际存在。
- Applies to：实体、迁移、所有附件DTO与文件读取
- Violation consequence：未下载被误显示为0B或可下载
- 来源：K-document-file-read-via-storage-path

### Invariant I-2：旧文件兼容与路径
- Rule：原有非空值不批量重写；下载响应长度取已验证文件实际大小，跨专家/越界/符号链接逃逸仍拒绝。
- Applies to：两个下载服务、提取器
- Violation consequence：越权或内容长度错误
- 来源：K-expert-document-ownership-chain

### Invariant I-3：文件名与审核
- Rule：原始展示文件名保留完整；file_name改TEXT以免登记长名称失败；落盘仍用生成名；不改document_status。
- Applies to：迁移、DTO
- Violation consequence：长附件名丢失或误审核
- 来源：original

## 现状审计

[A审计E1–E7](audit.md)与[代码检索全集](code-search-evidence.md)为本计划组成部分：包括表schema、全部生产写/读入口及跨模块交互。执行时先按符号复核，不以旧行号代替源码。 本项直接核对：E2（nullable链）/E7（迁移）。

新增/改动读写关系：本项实现方案逐任务明确生产者和消费者；只允许表中列出的文件引入这些写路径。迁移/源定位/任务字段的完整类型与默认值见总计划持久化契约，禁止再自增冗余状态。

## 实现方案

1. [I-1/I-3] 在 V118 将 mail_attachment.file_size 改 BIGINT NULL DEFAULT NULL、storage_path 改 VARCHAR(1024) NULL、file_name 改 TEXT NOT NULL。保留 V36 CHECK/FK/全部原数据，不新增状态列。修改 MailAttachment 的两个 nullable 类型。
2. [I-1/I-2] MailboxAttachmentService 的列表 DTO 接受 null；下载 DTO 仍 Long，先 require 路径且校验文件再 Files.size。ExpertDocumentBrowseService 列表 DTO 允许 fileSize=null，未落地 downloadUrl/previewUrl=null、previewable=false；resolveForDownload 只返回真实文件。DocumentTextExtractor 遇路径null在读文件前明确拒绝，不静默跳过。
3. [I-1] ExpertContactManagementController.MailAttachmentResponse 的 size/path 同步 nullable，保持旧 mailRecordId 约束和旧 attachments 数组结构，不在此插入 processing-owner。
4. [I-1/I-2/I-3] 扩展三项文件服务测试并更新 FlywayMigrationIntegrationTest 的目标版本为118；包含V117升级后的数据值、XOR约束、长中文名与0B合法文件。

任务中的领域文件路径全部以本计划下表为准；类内DTO/辅助函数不另拆文件。若必须增加文件，先修订计划与≤10文件边界。

## 变更文件清单

| # | 精确路径 | 操作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailAttachment.kt` | 修改 |
| 2 | `src/main/resources/db/migration/V118__allow_attachment_metadata_only.sql` | 新增 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentService.kt` | 修改 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt` | 修改 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractor.kt` | 修改 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt` | 修改 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentServiceTest.kt` | 修改 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt` | 修改 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt` | 修改 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 修改 |

## 验收标准

- I-1：迁移与DTO测试断言null不转0；不存在路径时不得打开流。
- I-2：旧PDF下载字节完全一致、Content-Length=Files.size；跨专家和越界路径拒绝。
- I-3：300字中文文件名完整往返；document_status迁移前后不变。

仅运行后才能记录通过。先本项测试，再mvn test（Java11，含Node），涉迁移追加MigrationIntegrationTest；协议/数据库IT必须按总计划显式启用，不用默认跳过当证据。

## 人工验收清单

### A-1：空值与历史回归
- 前置条件：独立测试库迁移到V117，用测试fixture建立专家A的旧PDF文件、零字节文件及专家B；升级V118后再插入A的size/path均NULL附件。
- 操作步骤：1. 调A资料GET；2. 下载旧PDF和零字节文件；3. 尝试读取NULL路径及以B访问A附件。
- 预期结果：旧PDF内容不变、零文件长度0；NULL大小返回null且无下载链接；未落地与越权均非200文件响应；审核仍PENDING_REVIEW。
- 覆盖：I-1/I-2/I-3；本项可观察需求与列明的回归/交互。
