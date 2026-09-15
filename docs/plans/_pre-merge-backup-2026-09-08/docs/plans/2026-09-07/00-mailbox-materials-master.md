# 收发件箱聊天化与专家资料按需获取：开发总计划

创建：2026-09-07；完成核对：2026-09-08（跨日续写，目录保持创建日期）。状态：**待用户审阅，未执行、未上线**。使用 create-p；本文件为顺序编排与跨计划契约，不是一次性执行全部文件的许可。11个子计划各自≤10文件、≤2子系统；每项必须先独立验证，再进入后项。仅本次写计划与知识审计，不修改生产业务代码。

## 需求描述

1. 收发件箱增加专家关注。左侧仅专家列表，右侧全部来往信件；仅发件、尚未回信的专家也出现，显示“待专家回复”和收0·发N，不能混进待处理。
2. 单封邮件移除“查看/处理”流程，处理动作只保留“标记已处理”。保留原文/清洗正文/翻译、邮件标签、QA标签、技术信息的原位查看与编辑能力。
3. 可信工作台直接放在来往信件下方、默认折叠；人工回复默认展开；无右侧切换tab。真实生产导航、全局检查/批量发送/自动回复控制、专家状态/层级/标签及保存、工作台事实/模型/采用、人工富文本发送完整保留。
4. 检查回复只取邮件头、必要正文、附件文件名与元数据，不取附件二进制。大材料不拖住后续账号；当前账号和阶段可见、接收有总时限。
5. 专家列表资料区和收发件箱资料管理共用一个前端组件、一套查询/存储状态和下载服务。仅在明确点击获取、或“获取所选文件并分析”时请求所选附件到服务器；查看页面、展开名称、标记已处理、生成普通回复不隐式下载。
6. 大量材料：完整索引、10条分页、名称搜索、来源/存储状态筛选、跨页勾选、批量获取、失败重试与来源不可用解释。存储状态与资料审核分开；旧文件兼容。
7. AI选件延续真实专家页入口与已有结果编辑功能。打开不下载；所选未就绪先获取、全部就绪再分析；不假设JPEG已有OCR能力。

**不得改变**：专家现有状态机/推广政策、发信安全/QA审计、真实导航和专家页其他操作、任务记录邮件钻取、未匹配来信绑定、旧附件权限与路径防护。附件名仅可触发现有意图路由，不等于内容已验证。

**明确不做**：React/Vue迁移、聊天消息表、WebSocket、新消息中间件、通用任务平台、对象存储、OCR、分块续传、批量自动下载所有专家、自动分析所有材料、以本次计划授权直接发布生产。

**已核实的预览差异**：当前 `ManualExpertMailService.kt:322` 仅COMPOSE_TEMPLATE，人工发件command无自由subject/body。因此无来信专家的人工区提供“选择模板发送跟进邮件”，复用现有发送流程；不能把预览的模拟自由跟进误当已有后端能力。无来信自由富文本若必须纳入，需追加独立发送链路计划，不由实现者私自扩展。

## 关键不变量

- G-1：专家/邮件/附件一律用真实ID关联；sourceInboundId不是processing ID；先分组再分页，来信authority只有processing。
- G-2：检查回复、GET列表、展开、标已处理、可信生成，附件内容FETCH必须为0；用户选件POST才获取。DMARC是已存在机器报表能力的显式SYSTEM队列例外，独立purpose、不归专家。
- G-3：远端身份包含UIDVALIDITY与partPath；元数据登记先完成再确认UID；下载失败不回退游标或再次自动回复。
- G-4：两host共享ExpertMaterials + ExpertMaterialService + AttachmentTransferService；存储、处理、审核三个状态不相互代替。
- G-5：工作台固定真实最新来信，草稿绑定目标，切换专家/账号不串上下文；服务端现有发送校验保持authority。
- G-6：新增样式按逐字契约，不复制mock全局样式；全部生产操作有明确入口，未声明扩展不可“自由发挥”。

上述分别由子计划I项细化并测试；不能以总计划的简写替代子计划约束。

## 样式契约

权威完整稿：[ui-style-contract.md](ui-style-contract.md)，含全部新增CSS、DOM层级、S-1至S-5、按钮文案/状态、四视口、focus/disabled/hover/active、错误/空态、窄屏规则。各前端子计划复制自己落地的CSS全文。改动前逐字事实：[frontend-baseline.md](frontend-baseline.md)。

生产页面/预览截图为布局依据，不当作接口事实：

- [已核对的聊天预览](../../../artifacts/mailbox-chat-preview/chat-materials-v4.png)
- [专家资料区预览](../../../artifacts/mailbox-chat-preview/expert-materials-v5.png)
- [专家AI选件预览](../../../artifacts/mailbox-chat-preview/expert-analysis-v5.png)

两种host的布局关系：专家资料卡内部内嵌同组件；聊天头部“材料N”原生dialog抽屉承载同组件。AI选件用selectionOnly模式。不要实现三份列表。

## 现状审计

详见 [audit.md](audit.md) E1–E7与[原始代码命中全集](code-search-evidence.md)。关键代码证明：

| 结论 | 当前代码 |
|---|---|
| 检查回复同步读附件，整批返回前等待 | ImapMailReceiveService:127/190/205；MailReceiveService:31 |
| 单改extractAttachments不够 | ImapMailReceiveService:150对part.content的泛化访问 |
| 空路径/大小未被现有类型允许 | MailAttachment实体、V7；ExpertContactManagementController:482 |
| 资料只有两处文件写入口，且绑定未补文档 | MailAttachmentService:22/64；UnmatchedInboundMailService:138 |
| 专家浏览/解析拒绝processing-owner | ExpertDocumentBrowseService:100；DocumentTextExtractor:44 |
| 进度显示的是刚完成账号 | BatchAutoMailReplyService:pollAccounts；MailAutomationController:170 |
| 只有发件也能参与现有分组，无需聊天表 | MailRecordRepository的OUTBOUND+processing UNION分组SQL |
| 工作台已有共享挂载，不应重写 | app.js:10650；trust-reply-workbench.js |
| 专家资料目前全量行+独立选件渲染 | app.js:8363/8408/8430/8541 |

### 持久化契约（新增设计，不是现有schema）

迁移顺序V118→V119→V120→V121；现有最高V117已核对。执行时若有新迁移占号，先修订本计划中的文件名/测试版本，不能覆盖现有迁移。

**mail_attachment（01）**：仅修改已有字段可空/容量：file_size BIGINT NULL DEFAULT NULL、storage_path VARCHAR(1024) NULL、file_name TEXT NOT NULL。不新增download_status/专家状态冗余字段；保留owner XOR和FK。历史值原样。

**mail_attachment_transfer（02，新表）**：一行一个远端part与传输状态，不再另建任务表/索引表。

| 字段 | 类型/默认/约束 | 写入与读取规则 |
|---|---|---|
| id | BIGINT自增PK | 内部传输ID，不取代attachmentId |
| attachment_id | BIGINT NULL UNIQUE，FK mail_attachment | MATERIAL必须非空；DMARC必须空 |
| purpose | VARCHAR(16) NOT NULL | 仅MATERIAL/DMARC，登记后不变 |
| account_code | VARCHAR(64) NOT NULL | 从真实收信账号写，禁止客户端指定凭据 |
| folder | VARCHAR(255) NOT NULL，区分大小写 | 本期仅实际INBOX，不做用户任意文件夹 |
| uid_validity / imap_uid | BIGINT NOT NULL | 实际正值，登记后不可更改 |
| part_path | VARCHAR(255) ASCII NOT NULL | 点分1-based路径；不能按文件名定位 |
| message_id | VARCHAR(255) NULL | 可空，仅用于下载复核；空不补猜值 |
| file_name / content_type | TEXT NOT NULL / VARCHAR(255) NULL | 原始解码名与类型；DMARC亦需，不因获取重写 |
| encoded_size | BIGINT NULL | BODYSTRUCTURE编码大小，未知null，非实际文件长度 |
| disposition | VARCHAR(32) NULL | 元数据分类；正文与附件规则见03 |
| inbound_processing_id | BIGINT NULL，FK processing | MATERIAL由04终结登记桥接；DMARC空，不建立假来信 |
| state | VARCHAR(24) NOT NULL DEFAULT 'METADATA_ONLY' | 仅METADATA_ONLY/QUEUED/DOWNLOADING/STORED/FAILED/SOURCE_UNAVAILABLE |
| requested_by | VARCHAR(64) NULL | METADATA_ONLY空；用户动作取Session；DMARC固定SYSTEM |
| queued_at / started_at | DATETIME(3) NULL | 首次入队/本次领取，重试更新queuedAt与startedAt，不伪造成功时间 |
| lease_until / worker_token | DATETIME(3) NULL / VARCHAR(36) NULL | DOWNLOADING有值，其他状态清空；所有worker提交CAS匹配token |
| bytes_downloaded | BIGINT NOT NULL DEFAULT0 | 本次尝试已写实际字节，不能当未下载原文件大小；重试归0 |
| attempt | INT NOT NULL DEFAULT0 | 每次实际领取+1，重复点击不增 |
| error_code / error_message | VARCHAR(64) NULL / VARCHAR(500) NULL | 失败写脱敏原因；显式重试清除，不输出stack/凭据 |
| created_at / updated_at | DATETIME(3) NOT NULL | 登记时间和最新状态时间，用于排序/观测 |

唯一键(account_code,folder,uid_validity,imap_uid,part_path)；队列索引(state,queued_at,id)、账号活动索引(account_code,state,lease_until)、processing索引(inbound_processing_id)。CHECK限制purpose与attachment_id组合；不将MATERIAL限定processing_id必填，因为必须兼容同事务创建记录的顺序，提交前由04校验bridge完整。

所有上述字段/状态受02 I-1~I-4、04 I-2/I-3约束。旧attachment没有transfer行时，通过实际文件验证判就绪；文件丢失且无可靠source显示SOURCE_UNAVAILABLE，不能伪造UID。新MATERIAL的STORED必须本地最终文件存在；DMARC的STORED表示原聚合报表已成功导入，临时文件已清理，永不出现在专家材料API。

队列领取须在同一数据库连接持有短期MySQL命名锁 `talent-attachment-claim` 时检查全局活动<2、账号活动<1并CAS领取；finally释放锁，禁止连接池归还时遗留锁。网络获取不持有DB锁/事务。每2秒续租，租约15秒；续租失败/失去token必须停止流与文件写，watchdog最迟在lease到期关闭连接。expired lease恢复仅限已请求项，旧worker提交影响行数0。总时限10分钟不能被续租延长。上述是拟定保守默认值，可配置，不声称由线上压测得出。

**inbound_mail_processing（04）**：只新增uid_validity一个字段，默认0代表历史未知，实际新接收必须正值；替换唯一键。不新增第二个业务状态字段；BODY_TRUNCATED/LEGACY_UID_UNVERIFIABLE使用现有MANUAL_REVIEW+reason字段。

**任务进度（05）**：只新增现有details JSON中的一个 `accountProgress` 对象键，内部accountCode/phase/startedAt/updatedAt；现有accountsPolled/fetched计数保持原键。不增加TaskProgressLog DB列、不把文件进度混入检查数。

**expert_follow（07，新表）**：username VARCHAR(64)、expert_contact_id BIGINT FK、created_at DATETIME；复合PK。唯一写者ExpertFollowService；读者会话summary。当前只admin，不引入新的用户管理。关注不写ES/ExpertContact/来信处理状态。

**其他存储**：expert_document仅增加“元数据即建档”和“显式历史绑定修复”写路径，字段/审核枚举不变；dmarc_report仍由原IngestService写；mail_record不加列不改发送语义；本地文件仍在现有basePath，只有worker增加受控生成路径。

### API契约（06/07消费方共同遵守）

所有路径均在应用contextPath下；身份沿现有Session，不拼生产域名。方法/字段如下是拟定契约。

| 接口 | 请求 | 响应/语义 |
|---|---|---|
| GET /api/expert-contacts/{id}/materials | page=0,size=10,q?,source?,sourceId?,state? | items,total,page,size,summary；GET绝无IMAP/下载入队 |
| POST /api/expert-contacts/{id}/materials/transfers | {attachmentIds:[1,2]}，1..500个去重ID | 202 {items:[{attachmentId,transferId,state}],acceptedCount,alreadyReadyCount}；同专家全部校验后入队 |
| POST /api/expert-contacts/{id}/materials/reconcile | {dryRun:true}或{dryRun:false} | {candidateAttachmentIds,createdCount}；仅明确修复已绑定历史processing-owner，不下载 |
| GET 原附件 download/preview | 原现有URL | 200就绪文件；409 MATERIAL_NOT_READY；不存在/越权不返回文件；不隐式创建任务 |
| POST 原ai-analysis | 原所选attachmentIds payload | 全所选就绪才提取，未就绪拒绝，旧结果不提前删 |
| GET /api/mail/mailbox/conversations | page=0,size=20(1..100),q?,followed?,pendingOnly?,waitingReply?,accountCode?,原邮件筛选参数 | items,total,page,size；按专家分组后分页 |
| GET /api/mail/mailbox/conversations/{contactId}/messages | limit=50(1..100),before?,accountCode? | items,nextBefore,hasMore；只当前专家，返回正序最新窗口 |
| PUT /api/mail/mailbox/conversations/{contactId}/follow | 空body | {followed:true}，幂等 |
| DELETE 同follow路径 | 无body | {followed:false}，幂等 |

材料items固定字段：attachmentId、documentId、source:{type,id,subject,receivedAt,accountCode}、fileName、contentType、documentType、documentStatus、actualSize(nullable)、encodedSize(nullable)、storageState、bytesDownloaded、error:{code,message}(nullable)、canFetch/canDownload/canPreview/analysisSupported/canAnalyze、downloadUrl/previewUrl(nullable)。不输出storagePath、远端密码或workerToken。summary含total/stored/metadataOnly/active/failed/sourceUnavailable以及defaultAnalysisAttachmentIds（全部支持格式CV/学位的ID，仅元数据）；默认候选>500返回完整count并让UI不默认勾选，不能静默只选前500。analysisSupported只表示PDF/text格式可解析，未落地PDF仍可勾选后获取；canAnalyze=analysisSupported且STORED。前端选件用analysisSupported，立即分析用canAnalyze，不能混用导致未下载PDF被禁选。扫描PDF是否有文字只能解析后判定。

列表total受当前筛选；summary统计该专家全体材料，文案明确“全部资料”；选择计数独立，不因筛选隐藏丢勾选。旧源关联歧义用明确SOURCE_AMBIGUOUS诊断（不新造transfer state），材料仍按已确认expert_document归属展示，source信息为空并标“来源待核对”。

会话summary items：contactId/name/email/orcid/institution、accountCodes、followed、receivedCount/sentCount/failedCount/pendingCount、waitingReply、latestMessage:{source,id,direction,subject,preview,time,sendStatus}、latestInbound:{processingId,accountCode,messageId,receivedAt}或null、materialCount。旧专家状态/层级/标签按原接口加载，仅对选中专家读取。

SQL归一化：OUTBOUND mail_record UNION ALL 已绑定processing（排除未匹配与独立机器信）；text列沿现有SQL显式统一utf8mb4_unicode_ci以避免UNION排序字符集冲突。收到计数=processing行数；发出计数=OUTBOUND AND send_status='SENT'；失败=OUTBOUND AND send_status='FAILED'；待处理=processing.process_status='MANUAL_REVIEW'（已核对当前Repository谓词）。

聚合发生在账号范围全历史；q匹配专家姓名/邮箱。原direction/日期/主题内容/收件人/标签只决定是否有匹配消息的专家membership；默认聊天没有隐式7天截止。待处理和待专家回复互斥选项；关注可与其中之一叠加。列表排序latestEvent DESC,contactId DESC。timeline游标由(time,source,id)编码并校验，与contact/account范围绑定，防不同scope复用；相同时间不丢/重复。右侧显示“该专家全部往来（当前账号范围）”，不要把列表筛选误当完整历史。

消息DTO：source/id/contactId/direction/accountCode/subject/body/cleanedBody/eventAt/sendStatus/processStatus/attachmentCount/firstAttachmentNames(最多3)/messageId/inReplyTo；主体通过既有清洗展示逻辑渲染。处理按钮只接受source=INBOUND_PROCESSING且MANUAL_REVIEW，使用原标已处理API，不能用mailRecord.id替代。

### 性能与错误契约

- 目录支持1000附件测试，不限制“超过10件就拒绝”；节点/正文/单文件字节限额必须明确报错，不能丢清单假成功。
- UI一次只渲染10资料行、20专家行、50消息；材料/消息不随全部专家一起加载。附件获取全局2、每账号1；一个失败其他继续。
- 接收单信60秒、账号接收窗口120秒；这不等于整个含LLM/SMTP自动处理任务的总时长承诺。
- 传输100MiB/文件、10分钟、64KiB buffer、connect/read10秒；未知远端长度显示已取字节不显示虚假百分比。超限FAILED/LIMIT_EXCEEDED，保留metadata供提高配置后显式重试。
- 来源丢失、UIDVALIDITY变更、part不匹配→SOURCE_UNAVAILABLE；socket/总时限→FAILED/TIMEOUT；权限/磁盘异常→FAILED可见；机器解析失败→FAILED/DMARC_PARSE_FAILED。不要以发现39件等同39件已归档。

## 实现方案

严格顺序如下；前一子计划未验证不执行后一项。01–07提供兼容后端，metadataOnly默认false；08–10实现组件与guard但不在index注册；11才统一注册资源及启用默认值。每一步可部署旧功能不变、独立用测试入口验证。由于前端资源缓存约束，08–10的用户可见开关由11集中完成，而非中途把新HTML发给旧app.js。

| 顺序 | 子计划 | 文件数 | 独立交付/验证 |
|---|---|---:|---|
| 01 | [附件元数据存储兼容](01-attachment-storage-compat.md) | 10 | 允许附件仅登记名称、尚无实际大小和本地路径；历史文件仍可下载、预览和分析。 |
| 02 | [有界、持久化附件传输服务](02-attachment-transfer-core.md) | 10 | 提供独立于检查回复的可恢复文件下载基础服务；重复请求同一附件仅一个活动任务。 |
| 03 | [MIME 元数据读取与正文白名单](03-mime-metadata-reader.md) | 8 | 具备不读取附件内容的收信模式，保留完整附件名称/定位并正确取得邮件正文；默认开关暂不启用。 |
| 04 | [全部收信分支的元数据登记与幂等](04-inbound-metadata-persistence.md) | 10 | 新收信完整登记附件索引；未匹配绑定后两入口可引用同一附件；UID重复或下载失败不导致重复登记。 |
| 05 | [检查回复进度与机器邮件隔离](05-reply-check-progress-and-machine-mail.md) | 10 | 进度明确显示真正正在处理的账号；慢账号/附件任务不无限拖住后续账号；DMARC附件另行有界获取解析。 |
| 06 | [材料统一查询、所有权和文件就绪 API](06-shared-material-api.md) | 10 | 收发件箱和专家列表使用同一份attachmentId、来源、存储状态和下载任务；旧下载/预览与AI提取也使用同一就绪判断。 |
| 07 | [专家会话查询与关注持久化](07-expert-conversations-follow.md) | 10 | 按专家显示完整往来摘要，保留只有发件的专家；关注可刷新保留，待处理和待专家回复分开。 |
| 08 | [共享材料组件与专家页内嵌](08-shared-materials-frontend.md) | 5 | 专家上传资料区变为可搜索、分页、跨页选择的统一材料面板；提供同一个抽屉挂载能力供聊天页使用。 |
| 09 | [AI 所选材料获取与分析衔接](09-material-analysis-selection.md) | 5 | AI选件窗复用材料组件；仅明确确认后获取缺失所选文件，全部就绪后才分析；失败与不支持格式明确显示。 |
| 10 | [收发件箱专家聊天布局](10-mailbox-chat-frontend.md) | 10 | 左侧只显示专家，右侧按时间显示来往信件；关注、材料共享入口、折叠工作台和展开人工回复完整可操作。 |
| 11 | [资源注册、启用与整体验收门禁](11-release-and-cache-gate.md) | 10 | 所有后端链路和前端host就绪后统一启用；浏览器不会混用新HTML和旧脚本；提供可执行发布与回退步骤。 |


测试代码新增/修改已计入各子计划10文件上限；新表分别只有“来源传输”和“关注”两项必要职责。若执行时编译证明还必须改未列文件，先修订该子计划或再拆兼容小片，不能以“相关文件”扩展范围。

## 变更文件清单

本总计划无直接业务文件执行清单；唯一执行边界是上表每个子计划的穷尽表。合并统计78个不同目标文件，分11轮验证；不是一次修改78文件的单计划。计划附件audit/baseline/evidence/style是只读依据，不是发布资源；生产CSS只能来自指定契约块，不能加载artifacts目录。

## 验收标准

- G-1：07真实MySQL分组测试，06跨账号同messageId/归属测试，不能用mock证明SQL。
- G-2：03本地IMAP协议FETCH证据；06/08/09 GET无POST；所选2/40只下载2；05DMARC独立队列。
- G-3：02失败/重启/重复/源失效；04事务与游标缺口，所有接收入口回归。
- G-4：08/10同导出组件、同API/store；06旧下载/提取器统一readiness；审核与存储分离断言。
- G-5：10真实processing上下文、草稿切换、可信采用到人工发送；无来信不伪造工作台。
- G-6：S-1~S-5 CSS字节比对/DOM白名单/四视口截图、全部真实操作入口点验。

执行时必须记录的命令（此计划阶段未运行，不声称通过）：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
node --check src/main/resources/static/expert-materials.js
node --check src/main/resources/static/mailbox-chat.js
node --check src/main/resources/static/app.js
node --test src/test/js/*.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Pmysql-it -Dtest=MailboxConversationRepositoryIT test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Pmysql-it -Dtest=ImapMetadataFetchIT,AttachmentTransferWorkerIT test
```

本地IMAP测试夹具置于各新IT文件内，复用JDK socket/项目已有依赖，不为本计划引入新中间件依赖。MySQL库必须独立测试库，使用已有测试配置，不对生产运行清表fixtures。每个子计划先跑本项用例，再跑mvn test约束编译/全部Node注册；所有阶段结束11执行整体验证。新测试如需真实MySQL须使用项目mysqlIt gate并在指定命令开启，不能悄悄跳过后报告成功。

**已知边界**：当前自动处理跨SMTP/数据库提交不具备可证明的exactly-once。本计划保证附件下载重试不调用自动回复、成功UID去重；不宣称网络发送成功但事务失败也绝无重发。发现此类日志必须按原人工核对流程处理，不能自动全邮箱重跑。

## 人工验收清单

本节整体清单与各子计划A项都需完成。仅开始人工验收时导出对应-acceptance.md；现在不生成、不默认勾选。

### A-1：真实页面与完整操作
- 前置条件：预发部署01–11，生产同款导航/专家详情模板，测试专家含来信/发件/资料/标签。
- 操作步骤：1. 1440×900打开专家页和收发件箱；2. 对照baseline核对所有全局与专家操作；3. 按S-4逐按钮操作；4. 在其他三个规定视口复验。
- 预期结果：专家列表左/往来右；无右侧tab；工作台折叠、人工展开；状态/层级/标签保存、可信事实/模型/采用、富文本发送全部有入口；未动区域布局不变，无横向溢出。
- 覆盖：需求1/2/3；不得改变全部UI项；G-5/G-6/S-1~S-5。

### A-2：无回信与关注
- 前置条件：A有2封SENT无来信、B仅1FAILED、C有10天前来信及今日发件。
- 操作步骤：1. 选待回复；2. 关注A并刷新；3. 过滤最近7天；4. 选A写跟进。
- 预期结果：A收0发2/待专家回复且关注保留；B/C不误判待回复；A工作台不能生成，能打开原模板发件流程。
- 覆盖：需求1；G-1/G-5；07/10全部等待状态交互。

### A-3：19+20大量附件
- 前置条件：测试邮箱投递19/20附件两封，另设1000件压力邮件；本地IMAP日志可记录分段读取。
- 操作步骤：1. 点击检查回复；2. 查看当前账号/阶段；3. 打开聊天/专家资料并展开附件；4. 查看协议日志。
- 预期结果：39/1000条目录完整；附件内容FETCH=0；每页10资料；当前账号不是刚完成账号；打开页面不下载。
- 覆盖：需求4/5/6；G-2/G-3/G-4。

### A-4：按需获取与同源状态
- 前置条件：专家40件中1旧文件/39未存。
- 操作步骤：1. 专家页勾2件获取；2. 立即到聊天材料抽屉；3. 关闭再开；4. 下载已存件到电脑。
- 预期结果：仅2件排队，两入口状态相同；关闭不中断任务；最终文件完整，原39件未选部分保持仅文件信息；审核不改变。
- 覆盖：需求5/6；G-2/G-4；旧文件回归。

### A-5：故障与重启
- 前置条件：测试慢传输/网络断开/文件删除/UIDVALIDITY改变及metadata登记DB失败夹具。
- 操作步骤：1. 分别触发故障；2. 重启服务；3. 对失败项点击重试；4. 重复检查已成功UID。
- 预期结果：超时连接真正关闭、后续账号继续；来源无效明确SOURCE_UNAVAILABLE；下载失败不重复自动回复；DB登记失败UID未跨过；重启不自动获取从未选过的附件。
- 覆盖：需求4/6；G-2/G-3；全部传输/收信交互。

### A-6：AI与人工处理独立
- 前置条件：2所选PDF仅1已存、另有JPEG与扫描PDF、历史分析结果。
- 操作步骤：1. 打开AI；2. 确认获取所选并分析；3. 模拟失败并重试；4. 检查图片/空文本提示；5. 标记来信已处理。
- 预期结果：打开不下载；只取缺失1件；失败不冒充分析完成；图片明确不支持；结果编辑/新增/重新评估可用；标处理不下载、不改变材料审核。
- 覆盖：需求2/5/7；G-2/G-4；既有AI与审核回归。

### A-7：机器信与既有入口
- 前置条件：DMARC/DSN/自检/未匹配邮件以及任务执行ID夹具，测试SMTP。
- 操作步骤：1. 检查回复；2. 查看机器报告任务和报表；3. 绑定未匹配信；4. 从任务记录钻取；5. 完成可信采用→人工发送。
- 预期结果：DMARC独立获取入原报表；DSN/自检不变专家材料；绑定资料同ID；任务批次仍准确；原发信安全与QA审计有效，校验失败保留草稿。
- 覆盖：需求3/4/5；全部不得改变条目；G-1/G-3/G-5。

### A-8：版本与回退
- 前置条件：记录实际预发HEAD、Flyway121和浏览器资源版本，有已存新文件与metadata-only数据。
- 操作步骤：1. 重启浏览器；2. 核对7项资源同键；3. 按11手册回退兼容UI/开关；4. 恢复新功能。
- 预期结果：无缺函数/混合缓存；不删除新表/文件；旧附件可读，恢复后任务与关注仍在；无伪称已生产修复。
- 覆盖：G-4/G-6；发布及历史兼容。
