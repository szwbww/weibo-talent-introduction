# 04 · 全部收信分支的元数据登记与幂等

状态：待审阅/未执行。前置：03子计划通过独立验证。 范围：10个文件；不超过2子系统。共同契约见[总计划](00-mailbox-materials-master.md)。

## 需求描述

新收信完整登记附件索引；未匹配绑定后两入口可引用同一附件；UID重复或下载失败不导致重复登记。

不得改变：邮件业务分类、自动回复开关、推广/审核规则、连续游标原则；不更改SMTP投递协议。

范围外：不全量重扫生产邮箱、不凭Message-ID猜新定位、不重做自动回复状态机或承诺跨SMTP exactly-once。

## 关键不变量

### Invariant I-1：UID代际
- Rule：inbound_mail_processing新增uid_validity BIGINT NOT NULL DEFAULT0；0仅历史未知；新接收保存实际正值；唯一键改account/uid_validity/uid。旧行仅同account/uid且非空Message-ID、from与秒级receivedAt均匹配才认领代际。
- Applies to：两个processing创建入口、dedup、copy更新
- Violation consequence：UID重用吞信或把旧附件定位到新信
- 来源：K-inbound-seen-not-processed-marker

### Invariant I-2：索引提交与确认
- Rule：metadataOnly时处理记录、完整attachment/transfer索引、已匹配document关联在实际数据库事务完成；成功后才markSeen/把UID纳入游标成功集合。缺一个附件登记失败则本信不能被确认。
- Applies to：processSingle所有返回分支、confirm两入口、指定UID回补
- Violation consequence：处理中途丢材料但游标已跳过
- 来源：K-process-single-all-callers

### Invariant I-3：两种owner与桥接
- Rule：保留V36 XOR。已有mail_record的材料沿用record owner；无record分支用processing owner；transfer.inbound_processing_id准确指向本次processing。未匹配绑定只创建相同attachmentId的ExpertDocument，owner不搬迁，不能重下文件。
- Applies to：MailAttachmentService、confirm、bindToContact
- Violation consequence：双owner、重复document或跨专家串件
- 来源：K-expert-document-ownership-chain

### Invariant I-4：分支与审核独立
- Rule：附件名仍参与CV/DOCS意图；仅正文截断转MANUAL_REVIEW/BODY_TRUNCATED并禁自动回复；下载状态不改变document_status/已处理状态。metadataOnly=false保持旧路径，绑定新增关联随开关启用。
- Applies to：未匹配/无首信/全局关闭/专家关闭/人工/重复正文/正常路径
- Violation consequence：漏分支或把已下载等同审核通过
- 来源：original

## 现状审计

[A审计E1–E7](audit.md)与[代码检索全集](code-search-evidence.md)为本计划组成部分：包括表schema、全部生产写/读入口及跨模块交互。执行时先按符号复核，不以旧行号代替源码。 本项直接核对：E2/E3（所有收信分支/绑定/UID游标）。

新增/改动读写关系：本项实现方案逐任务明确生产者和消费者；只允许表中列出的文件引入这些写路径。迁移/源定位/任务字段的完整类型与默认值见总计划持久化契约，禁止再自增冗余状态。

## 实现方案

1. [I-1] V120新增uid_validity，替换旧唯一索引；不把所有历史0回填为当前邮箱值。实体、Repository、AutoMailReplyService dedup同步；旧finder保留兼容测试/读取，但新写不能用仅account/uid判重。历史0且同UID信息不够核验时本信转人工LEGACY_UID_UNVERIFIABLE，不盲目吞信/自动回复；不得反写未知远端source。
2. [I-2] 在AutoMailReplyService对processSingle的实际执行主体用TransactionTemplate明确包裹现有数据库写，覆盖同类调用和外部调用。将markSeen移至事务成功后的单一确认点；skipImapAck继续生效。保持现有邮件分类和发送逻辑顺序，附件登记失败发生在可能SMTP发送前；不引入数据库异常后的自动SMTP重试。既有“SMTP成功但DB失败”的事务外部副作用风险单独记录，沿用人工核对，不能声称此计划消除该历史风险。
3. [I-2/I-3] MailAttachmentService两保存入口按metadataOnly分流：旧content存在保留旧写法；新模式不mkdir、不Files.write，只在transfer源唯一锁内创建附件(document若已匹配)。原四个匹配调用仍使用相同record owner；confirm函数将保存结果processing.id传入同一索引服务补全桥接。未匹配及无首信在confirm取得processing.id后直接登记processing-owner；后者须建立ExpertDocument。所有分支最终登记一次，unique/CAS+锁attachment防并发创建多个document。
4. [I-3/I-4] UnmatchedInboundMailService.bindToContact在其现有事务中，开关启用时按已有processing-owner附件幂等补ExpertDocument；不重写owner、路径或review状态。重复绑定仍由现有校验拒绝。历史已绑定却未建档的数据不在此做猜测回填，由06显式关联修复入口处理。
5. [I-1..I-4] AutoMailReplyServiceTest按实际UIDVALIDITY改mock，逐项覆盖所有SinglePipelineOutcome中创建processing的分支；MailAttachmentServiceTest覆盖39/1000件索引、同名不合并、失败事务回滚；UnmatchedInboundMailServiceTest覆盖绑定后同attachmentId及不下载；迁移测试目标120。
6. [I-2/I-3] 本阶段AutoMailReplyService构造纳入AttachmentTransferService用于最终bridge核验，AutoMailReplyServiceTest同步；05机器报告登记复用该依赖，不新增范围外构造参数。

任务中的领域文件路径全部以本计划下表为准；类内DTO/辅助函数不另拆文件。若必须增加文件，先修订计划与≤10文件边界。

## 变更文件清单

| # | 精确路径 | 操作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 修改 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt` | 修改 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt` | 修改 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/InboundMailProcessing.kt` | 修改 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt` | 修改 |
| 6 | `src/main/resources/db/migration/V120__scope_inbound_uid_by_validity.sql` | 新增 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` | 修改 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt` | 修改 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt` | 修改 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 修改 |

## 验收标准

- I-1：同UID不同代际能登记，0代际仅严格复核后认领；不伪造source。
- I-2：第20件持久化失败→39件索引不出现部分成功、该UID未确认；成功重试只39行；已确认UID重复检查不再次自动回复。
- I-3：两owner的列表、下载权限链一致；绑定后doc仍同attachmentId且无文件I/O。
- I-4：所有分支attachment数量与metadata一致；bodyTruncated只有人工状态；global off/专家off/推广原规则回归。

仅运行后才能记录通过。先本项测试，再mvn test（Java11，含Node），涉迁移追加MigrationIntegrationTest；协议/数据库IT必须按总计划显式启用，不用默认跳过当证据。

## 人工验收清单

### A-1：两封材料、失败与重试
- 前置条件：测试邮箱2封19/20附件；分别用已有首信专家、无首信专家、未匹配发件人投递；测试SMTP只记录不外发。
- 操作步骤：1. metadata模式检查；2. 未匹配信在后台绑定；3. 故障注入第20件DB失败重跑；4. 再次检查已成功UID。
- 预期结果：各信附件索引数量准确；绑定后document使用原attachmentId；失败UID未推进；重试不重复文件/文档；已成功UID不会再次发自动回复。
- 覆盖：I-1/I-2/I-3/I-4；本项可观察需求与列明的回归/交互。

### A-2：开关与审核回归
- 前置条件：fixture有PENDING_REVIEW和ACCEPTED材料，自动回复全局关闭及单专家关闭各1个。
- 操作步骤：1. 旧模式执行原检查；2. 新模式按相同fixture检查；3. 后续下载其中一个文件。
- 预期结果：两模式均尊重关闭开关，审核状态保持原值；下载不标记来信已处理；无首信来信仍转人工。
- 覆盖：I-2/I-4；本项可观察需求与列明的回归/交互。
