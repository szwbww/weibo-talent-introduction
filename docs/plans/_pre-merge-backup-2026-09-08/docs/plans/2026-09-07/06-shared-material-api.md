# 06 · 材料统一查询、所有权和文件就绪 API

状态：待审阅/未执行。前置：05子计划通过独立验证。 范围：10个文件；不超过2子系统。共同契约见[总计划](00-mailbox-materials-master.md)。

## 需求描述

收发件箱和专家列表使用同一份attachmentId、来源、存储状态和下载任务；旧下载/预览与AI提取也使用同一就绪判断。

不得改变：专家归属与基目录保护、历史资料链接、原AI结果字段保存方式、GET不触发网络下载。

范围外：不合并资料审核与存储状态、不扩展OCR、不迁移旧文件目录。

## 关键不变量

### Invariant I-1：唯一读模型
- Rule：两个host都GET /api/expert-contacts/{id}/materials；附件状态仅来自transfer+已验证文件；GET不登记任务或写审核。所有权是document→attachment→其唯一owner→contact；历史缺document另走显式修复。
- Applies to：新列表、旧文件服务、提取器
- Violation consequence：两个入口各自计算状态、GET副作用
- 来源：K-expert-document-ownership-chain

### Invariant I-2：精确来源
- Rule：新附件通过transfer.inbound_processing_id关联来信；旧processing附件直接owner；旧mailRecord附件仅同账号/同专家/INBOUND方向/非空messageId唯一匹配，歧义拒绝猜测。sourceInboundId不作processing关联。
- Applies to：MailboxService、新材料查询
- Violation consequence：展示另一封信附件
- 来源：K-mail-record-source-inbound-id

### Invariant I-3：显式动作与全部校验
- Rule：POST transfers只获取所选ID，先全量校验再排队；旧GET download/preview未就绪返回409 MATERIAL_NOT_READY，不暗中获取；AI同样所有所选就绪后才读取/写结果。
- Applies to：Controller、旧浏览与提取器
- Violation consequence：悄悄下载全专家材料或部分分析冒充完成
- 来源：original

### Invariant I-4：列表与状态口径
- Rule：page从0、size固定默认10上限100；分页后返回total及全专家状态统计；unknown实际size=null、encodedSize独立；审核是独立documentStatus，传输失败不丢列表行。
- Applies to：材料列表DTO/查询
- Violation consequence：统计受当前页影响或unknown=0
- 来源：original

## 现状审计

[A审计E1–E7](audit.md)与[代码检索全集](code-search-evidence.md)为本计划组成部分：包括表schema、全部生产写/读入口及跨模块交互。执行时先按符号复核，不以旧行号代替源码。 本项直接核对：E2/E3（所有权/旧链接/资料解析）。

新增/改动读写关系：本项实现方案逐任务明确生产者和消费者；只允许表中列出的文件引入这些写路径。迁移/源定位/任务字段的完整类型与默认值见总计划持久化契约，禁止再自增冗余状态。

## 实现方案

1. [I-1/I-2/I-4] 新增ExpertMaterialService与Controller，使用现有JdbcTemplate参数化查询（不拼用户SQL）；DTO与查询放service同文件，避免无必要分层。按专家关联doc/attachment，左联transfer，仅查当前页并聚合计数，不N+1全量加载文件。完整API见master；搜索LIKE转义，来源筛选接受source/id二元组，状态枚举校验，排序receivedAt DESC,attachmentId DESC。
2. [I-1/I-3] 在同一个ExpertMaterialService中提供resolveReadyFile集中两种owner的归属校验与realpath/就绪判定。ExpertDocumentBrowseService、DocumentTextExtractor、MailboxAttachmentService委托该统一方法；旧接口路径不变，下载资源实际size仍Long。只有提供有效联系归属的文档可AI；匿名附件不能借ID越权。
3. [I-2] 在ExpertMaterialService内提供resolveMessageAttachments，按新bridge/直接owner/严格唯一旧关系查询；材料API本阶段直接使用它。旧MailboxService在07接入，避免本项漏列其构造与回归测试。
4. [I-1] 显式POST /api/expert-contacts/{id}/materials/reconcile 仅用于管理员修复历史“已经绑定但未建ExpertDocument”的附件：只接纳processing.expert_contact_id=id且attachment的processing owner一致的行，在事务锁下补缺document，继承原文件与attachmentId，默认PENDING_REVIEW、类型沿用filename分类。无IMAP、无审核覆盖、幂等。普通GET不能调用它；发布前先dryRun返回候选ID，再由授权运维执行apply。本期不把歧义mailRecord强行补链。
5. [I-3/I-4] POST transfers通过现有Session验证登录与每个ID归属，调用02队列；GET materials提供状态和能力，不返回磁盘path/IMAP密码/远端凭据。旧download返回409时JSON结构含attachmentId/state，不抛500栈；ExpertMaterialController.kt内声明只作用本controller及既有MailboxAttachmentController/ExpertDocumentBrowseController/ExpertDocumentAnalysisController的高优先级RestControllerAdvice，仅映射新MaterialNotReadyException为409、传输容量异常为429；异常类型放统一service文件。不能仅抛ResponseStatusException，现有GlobalExceptionHandler.catch(Exception)会抢先返回500。禁止改全局异常规则；用MockMvc同时挂真实global advice验证409确实生效。
6. [I-1..I-4] 新API测试覆盖两host请求同快照、分页/搜索/状态、cross-contact拒绝、GET无I/O、显式历史修复；旧服务测试覆盖兼容与null；现有AnalysisService测试证明读取前失败不会delete旧结果。

任务中的领域文件路径全部以本计划下表为准；类内DTO/辅助函数不另拆文件。若必须增加文件，先修订计划与≤10文件边界。

## 变更文件清单

| # | 精确路径 | 操作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/document/controller/ExpertMaterialController.kt` | 新增 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt` | 修改 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractor.kt` | 修改 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentService.kt` | 修改 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialServiceTest.kt` | 新增 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt` | 修改 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisServiceTest.kt` | 修改 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt` | 修改 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentServiceTest.kt` | 修改 |

## 验收标准

- I-1：两个host同endpoint；锁下reconcile重复执行不重复doc；旧三服务调用同readiness，无路径旁路。
- I-2：两个账号相同messageId不串件；多候选回退拒绝；sourceInboundId没有被当processing ID。
- I-3：仅所选2/40入队，恶意混入1个其他专家ID整批拒绝；未就绪GET409、AI结果原值不变。
- I-4：40条4页，搜索/来源/状态统计准确；未知size=null，实际0B保留0；GET网络FETCH=0。

仅运行后才能记录通过。先本项测试，再mvn test（Java11，含Node），涉迁移追加MigrationIntegrationTest；协议/数据库IT必须按总计划显式启用，不用默认跳过当证据。

## 人工验收清单

### A-1：两入口同一资源
- 前置条件：测试库专家A的1个旧文件+39个metadata，新旧owner均有；专家B有1件。
- 操作步骤：1. 两个入口调用A材料API；2. 选A两件POST；3. 轮询两入口；4. 混入B附件再POST；5. 对未就绪件调旧下载与AI。
- 预期结果：两边attachmentId/状态一致，仅2件排队；混入B整批拒绝；未就绪下载409 MATERIAL_NOT_READY；AI不覆盖已有结果，未选37件无FETCH。
- 覆盖：I-1/I-2/I-3/I-4；本项可观察需求与列明的回归/交互。

### A-2：历史绑定修复和旧功能
- 前置条件：构造processing已绑定A且附件已落盘、无ExpertDocument的历史行；另保留正常旧PDF和同messageId歧义行。
- 操作步骤：1. 调修复dryRun；2. apply两次；3. 打开资料并下载/预览正常旧PDF；4. 查看歧义来源。
- 预期结果：dryRun只返回候选，不写DB；两次apply只建1条doc且仍原attachmentId；PDF原链接可用；歧义不展示其他账号文件；审核未自动变ACCEPTED。
- 覆盖：I-1/I-2/I-3；本项可观察需求与列明的回归/交互。
