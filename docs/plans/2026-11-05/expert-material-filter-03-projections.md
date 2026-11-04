# 03：旧邮箱投影、预览提示与只读状态对账

状态：DRAFT。依赖：[01](expert-material-filter-01-read.md) 的规则/消息附件过滤；执行顺序排在 [02](expert-material-filter-02-ingress.md) 后。9 文件（生产 4、测试 5），无 schema/frontend 变更。

## 需求描述

O1：历史仅签名邮件在旧邮箱列表、任务邮件列表和自动回复预览中不再被当作含有效材料。O2：运营对账排除签名证据，准确报告历史差异，但不自动改写历史运营状态。

保持：正常材料 hasAttachment/提示保留；已有邮箱查询范围、分页、账号/权限和消息匹配不变；对账人工覆盖、COMPLETED/INVITED/EMAIL_INVALID 优先级不变；GET/预览不发邮件，不改人工数据。

范围外：原有 has_attachment 的来源覆盖不足/消息匹配改造、自动修复运营状态、ES 重建、历史意图/AI 结果重算、其他只读字段清理。不能把“纠正签名存在性”扩成旧邮箱 JOIN 重构。

## 关键不变量

### Invariant I-1：四个 SQL 位点与实体投影同规则
- Rule：两条仓储查询各两个 has_attachment EXISTS，全部使用 01 固定 alias `a` 的 `MATERIAL_SQL`；任务实体路径 `.any(isMaterial)`；不复制不同规则。不改变原 WHERE 的归属/账号/消息匹配和分页条件。
- Applies to：MailRecordRepository.listMailbox/listMailboxByExpertContactIds；MailboxService.toMailboxRow。
- Violation consequence：普通列表和按专家/任务列表显示矛盾。
- 来源：K-attachment-metadata-consumer-chain。

### Invariant I-2：预览只改“忽略了有效附件”提示
- Rule：attachmentIntentIgnored 从 raw 非空变为有效附件存在；原本就忽略附件意图的预览仍不根据附件强行换 intent/autoAction，不发信、不登记 transfer。
- Applies to：AutoReplyPreviewService.preview。
- Violation consequence：一个布尔提示修复扩大为自动回复策略变化。
- 来源：original。

### Invariant I-3：对账是报告，人工权威保持
- Rule：attachedMailRecordIds 构建前过滤签名；其余 deriveExpectedStatus、CHANGE_OPERATOR_STATUS 人工覆盖和 COMPLETED 处理不变。正常材料仍构成 MATERIALS_RECEIVED；INVITED 仍优先；仅签名来信可以报告 expected=REPLIED、db=MATERIALS_RECEIVED，但不得 UPDATE/save 或同步 ES。
- Applies to：OperatorStatusReconcileService.reconcile，scheduler/controller 既有调用。
- Violation consequence：清除签名却意外把人工或高级里程碑回退。
- 来源：K-operator-status-single-writer。

### Invariant I-4：守卫只机械同步
- Rule：允许更新 OperatorStatusWriteSeamGuardTest 中 MailRecordRepository/MailboxService 的现有 NoiseSite 行号，并注释本计划的位移；path/context 与 ALLOWED_WRITE_SITES 不变，其他噪声点不改。
- Applies to：03 行号守卫。
- Violation consequence：误把纯行号漂移当业务失败，或为过测试放宽实际写入白名单。
- 来源：K-line-number-guard-breaks-on-any-insertion。

## 现状审计

源码前缀 `src/main/kotlin/com/weibo/talentintroduction/`。完整表 schema/读写闭包见[主计划 S1–S4](expert-material-filter-master.md)。本计划不新增写路径。

### mail_attachment / expert_document / transfer 的来源

- Schema：V7/V36/V118/V130 的 file_name/content_type/owner；V119 transfer 中 disposition 不足以识别图片内容。复用现有字段，不改表。
- attachment 全部写方：MailAttachmentService metadata、legacy record、legacy processing；ManualExpertMaterialUploadService.upload；AttachmentTransferWorker 更新路径/大小。
- document 全部写方：MailAttachmentService legacy record/ensureExpertDocument/ensureDocumentsForProcessingAttachments，ManualExpertMaterialUploadService.upload，ExpertMaterialService.reconcileApply。
- transfer 全部写方：MailAttachmentService 登记/桥接；AttachmentTransferService 登记与两种入队；repository/worker 六类生命周期写入。
- 01/02 已负责新旧材料读写过滤；历史原始行仍在，03 必须在直接读 raw attachment 的旧路径显式筛选。不能用“新写方已修”代替历史兼容。
- 文件系统：本计划四个生产文件均不读写附件文件；已有原件读写清单见主计划 S3，全部保持。

### 具体读取证据

| 读者 | 真实位置 | 当前表达式 |
|---|---|---|
| listMailbox 的 record 分支 | `mail/repository/MailRecordRepository.kt:514` | EXISTS ma.mail_record_id=mr.id |
| listMailbox 的 processing 分支 | 同文件 `:545–548` | ma JOIN mr2 按 Message-ID 和 INBOUND |
| listMailboxByExpertContactIds record | 同文件 `:782` | EXISTS ma.mail_record_id=mr.id |
| listMailboxByExpertContactIds processing | 同文件 `:821–824` | ma JOIN mr2 按 Message-ID 和 INBOUND |
| task-execution 实体投影 | `mail/service/MailboxService.kt:205–209` | repository list.isNotEmpty() |
| 消息详情 | 同文件 `:313/:331` | resolveAttachments 委托 01，已有过滤 |
| 自动回复预览 | `mail/service/AutoReplyPreviewService.kt:56–58` | processing-owner list.isNotEmpty() |
| 状态对账 | `campaign/service/OperatorStatusReconcileService.kt:57/70/207` | raw findAll→recordIds→期望材料状态 |
| UI 持久标签 | `static/app.js:661/670` | MATERIALS_RECEIVED→“已回复材料”，不是上述 count 的动态结果 |

对账上游只读：`ExpertIndexController.kt:248` 的 `/api/experts/reconcile-operator-status`，`MailAutomationScheduler.kt:96`。对账读取 `expert_contact`、mail_record、bounce_record、operator_action_log 与 ES 层级状态；仅过滤 attachment 事件输入，其他 schema、reader/writer 不改，**不授权新增状态写方**。正常状态写入仍由 ExpertOperatorStatusService，具体单调语义 `:46–66`。

守卫证据：`src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt:71/118/122` 固定了 MailboxService:168、MailRecordRepository:628/685。仅增加 import 都可能造成位移，不能遗漏。

Interaction points：IP-6 历史 attachment→两 SQL/任务投影/预览，IP-7 历史事件→只读对账；A-1～A-4 覆盖。

### 补充离线入口审计

`tmp/lucio-dorneles/register_materials.sh:34/39–51/69–87/96` 在当前 checkout 中直接查重、复制 PDF 并写 manual_upload/attachment/document，提交成功后清暂存、失败清本次原件。文件名与 MIME 固定 PDF，属于一次性离线写方，按 I-1 全部保留；本计划不修改/执行。证据见主计划额外可执行脚本回执。不要把“scripts/ 无命中”扩展成“仓库无离线写入”。

## 实现方案

### T1：四处 SQL + 实体路径（I-1）

文件：`mail/repository/MailRecordRepository.kt`、`mail/service/MailboxService.kt`、`mail/service/MailboxServiceTest.kt`、新 `mail/repository/MailboxSignatureProjectionIT.kt`。

1. 四个 EXISTS 的局部附件 alias 从 ma 改为 a，所有同子查询引用同步；添加 `AND ${ExpertMaterialEligibility.MATERIAL_SQL}`。常量必须是编译期 const，能用于 @Query；不接收动态 alias，不重写整个 UNION。
2. `toMailboxRow` 中 `.isNotEmpty()` 改为 `.any { isMaterial(it.fileName,it.contentType) }`，recordId null 行继续 false。
3. count/list 之外的 SQL，特别账号权限、过滤、正文截断、排序、分页与 legacy 关系保留。上述旧 processing 分支的来源模型不是本计划要修的内容；测试只证明原有查询可见集合中签名被排除。
4. 新真实 SQL IT 自管 Testcontainers 临时 MySQL，使用生产 Spring Data JDBC repository 代理执行两条原 @Query；按同一 fixture 各读取 record/processing 两分支。不得只字符串 contains 断言 SQL。
5. fixture：专家 A 的签名-only 邮件和正常 PDF 邮件；专家 B 的隔离邮件；两个账号；null MIME+.p7s、大写带参数签名 MIME、普通图片、无附件。断言两查询命中的 hasAttachment 真值一致且 contactIds/accountCodes 范围、total/排序未变。原代码不保证的桥接覆盖不能写成新增断言。

### T2：预览提示（I-2）

文件：`mail/service/AutoReplyPreviewService.kt`、`mail/service/AutoReplyPreviewServiceTest.kt`。

仅替换 attachmentIntentIgnored 的 `.isNotEmpty()` 为有效附件 `.any`。不改 previewKind、classification、wouldBeBlockedBy、推荐答案或发送逻辑；用同正文 + 仅签名/签名+PDF/空附件三种 fixture 断言分类结果一致，布尔值分别 false/true/false，零 transfer 登记、零 SMTP。

### T3：只读对账（I-3）

文件：`campaign/service/OperatorStatusReconcileService.kt`、`campaign/service/OperatorStatusReconcileServiceTest.kt`。

- `findAll()` 得到 raw 行后，在 mapNotNull(mailRecordId) 前按规则过滤；保持 manual/processing-owner 没有 recordId 时不加入当前事件集合的既有行为，本计划不新增其里程碑意义。
- record-owner 普通材料仍进入 attachedMailRecordIds；相同 contact 含签名+普通材料仍保持 MATERIALS_RECEIVED。
- 保留人工覆盖单列、COMPLETED 只免 expected 比较、INVITED 优先、EMAIL_INVALID 地址证据规则。服务不新增 save/update/index 调用。
- 测试使用捕获的 report，证明只有签名且历史 DB=MATERIALS_RECEIVED 时 expected=REPLIED、差异数+1；存在 CHANGE_OPERATOR_STATUS 的相同数据归 humanOverride，不计 dbVsExpected；已邀请仍 INVITED；普通材料仍 MATERIALS_RECEIVED；检查无 writer 依赖新增、ES 只读请求保持。

### T4：守卫同步（I-4）

文件：`campaign/OperatorStatusWriteSeamGuardTest.kt`。

改完生产文件后重新 rg 实际 NoiseSite；仅更新受本计划位移的行号，并记录原行→新行。禁止清空 exclude、加宽白名单、删闭包断言或忽略测试。若 context 文本变化说明发生额外业务改动，先修正超范围实现，不能把文本也一并改掉。

## 变更文件清单

| # | 文件 | 操作 | 用途 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt` | 修改 | 四处 has_attachment SQL 投影 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt` | 修改 | 任务列表实体投影 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt` | 修改 | 附件忽略提示的判断 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt` | 修改 | 只读材料事件筛选 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt` | 修改 | 任务列表与消息详情口径 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt` | 修改 | 预览提示回归 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt` | 修改 | 只读期望状态及人工覆盖 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxSignatureProjectionIT.kt` | 新增 | 两条真实仓储查询的四处分支 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 机械同步 | 仅既有噪声点行号 |


9 文件；不修改 frontend、ExpertOperatorStatusService 或 ES writer；其他测试文件不隐式加入。

## 验收标准

- I-1：四处 EXISTS 均消费共享常量，两个生产仓储方法在真实 MySQL 上跑 record/processing 分支；task 路径同真值；普通附件、空集合、权限/账号/分页不变。
- I-2：相同正文三 fixture 的 intentCode/autoAction/previewKind 相等，attachmentIntentIgnored 为 false/true/false；没有新增发送、登记或状态写入。
- I-3：签名-only 导出正确差异；正常材料、邀请、人工覆盖和终态回归；检查 reconcile 仍只读（控制器外层的 task_execution 日志属于既有任务记录，不混同运营状态修复）。
- I-4：行号守卫通过，diff 仅受影响行号与说明，ALLOWED_WRITE_SITES/path/context 不变。
- 联合：01+02+03 后同一个签名-only 消息的材料页 total=0、会话 materialCount=0、消息 attachmentCount=0、旧列表 hasAttachment=false、预览提示false；历史 MATERIALS_RECEIVED 标签允许保留，报告差异可见。

执行命令（未来执行，当前未运行）：

```bash
JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=MailboxServiceTest,AutoReplyPreviewServiceTest,OperatorStatusReconcileServiceTest,OperatorStatusWriteSeamGuardTest -DskipNodeTests=true
JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=MailboxSignatureProjectionIT -DmysqlIt=true -DskipNodeTests=true
```

新 IT 使用隔离容器，与 01 同时覆盖 MySQL 5.7/8.0 查询合同；不开默认数据源，不运行会清空默认库的旧 RepositoryIT。若容器不可用报告未验证，不把跳过当通过。

## 人工验收清单

### A-1：旧邮箱与任务列表
- 前置条件：隔离测试环境旧版登记两封测试专家来信：第一封只有p7s，第二封PDF+p7s；有对应 mail_record 与 processing，关联原测试收信任务。另有其他账号/专家的正常PDF来信。记录查询条件与邮件ID。
- 操作步骤：1. 部署三个子计划；2. 打开旧收发件箱普通列表；3. 按同专家查看；4. 打开对应任务邮件记录；5. 打开两封邮件详情；6. 切换账号筛选。
- 预期结果：第一封 hasAttachment=false、详情附件0；第二封 true、详情仅PDF1份；各入口数量标记一致，邮件本身仍在，账号筛选不会串出另一专家的文件。
- 覆盖：O1、查询范围/普通材料保持、I-1、IP-6。

### A-2：自动回复预览
- 前置条件：旧版创建同正文的三条 processing-owner 测试来信：仅p7s、p7s+PDF、无附件；均在可预览状态，全局发送关闭。
- 操作步骤：1. 分别打开自动回复预览；2. 比较预览响应中的 attachmentIntentIgnored、intentCode、autoAction、previewKind；3. 查看发送记录。
- 预期结果：布尔值分别false/true/false；同正文分类/动作保持一致；没有新增已发邮件、transfer或材料文档。
- 覆盖：O1、预览只读保持、I-2、IP-6。

### A-3：历史状态差异不自动回退
- 前置条件：隔离测试环境旧版的一位专家仅有已确认来信和p7s、无邀请；DB 状态已自动推进 MATERIALS_RECEIVED；不存在 CHANGE_OPERATOR_STATUS 人工覆盖日志。记录 DB/ES 状态。
- 操作步骤：1. 调用 POST `/api/experts/reconcile-operator-status`；2. 查看该专家样本；3. 刷新专家页面；4. 再次对账。
- 预期结果：样本 expectedStatus=REPLIED、dbStatus=MATERIALS_RECEIVED、category=DB_VS_EXPECTED；两次报告一致；数据库/ES原状态不变，“已回复材料”持久标签可以保留；材料页始终0份。
- 覆盖：O2、历史状态保持、I-3、IP-7。

### A-4：人工和高里程碑保护
- 前置条件：四位隔离测试专家：①仅签名且通过后台人工设置 MATERIALS_RECEIVED（产生审计日志）；②已发邀请成功且仅签名；③仅签名但人工终态COMPLETED；④普通PDF+签名且DB=MATERIALS_RECEIVED。准备原状态快照。
- 操作步骤：1. 调用同一只读对账API；2. 查看四人的分类/期望状态；3. 刷新页面并比对原状态快照。
- 预期结果：①humanOverride计数增加且不进dbVsExpected；②expected=INVITED；③不因expected比较计入DB异常；④expected=MATERIALS_RECEIVED；四人DB/ES均不因本次对账改变。
- 覆盖：O2、人工/终态/普通材料保持、I-3、IP-7。
