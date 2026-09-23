# 回复片段变体 01：统一随机解析与主题引用

状态：待实施。2026-09-23。前置：无；后续：02 前端计划。本文是设计，不代表代码已实现。

## 需求描述

O-1：模板正文、邮件主题引用回复片段时，在生成邮件阶段各自从“原文 + 启用变体”随机取一个，再替换变量。抽取能力放在公共片段变体服务，其他模板调用方无需自己写随机算法。

O-2：主题能够保存回复片段 ID；创建、编辑、列表回显、预览、正式生成、必填变量检查贯通。旧自定义主题继续工作。

必须保持：P-1 原文和变体的现有 CRUD、正文块顺序、默认片段与启用语义；P-2 QA 的既有确定性算法及 AI/RAG 回复框架的原文选择与版本校验；P-3 必填变量门禁、账号选择、退订、线程主题优先规则、SMTP/重试流程；P-4 旧客户端未传新字段仍可使用自定义主题，旧显式索引预览在前端升级前可用。

范围外：不拆段、不创建生产片段、不填充十份文案、不接 AI 生成、不治理垃圾邮件、不增去重历史或邮件快照、不改变批量发送策略、不将 AI 框架槽位改造成随机模板引用。

“统一引用”的本期边界是邮件模板的 `REPLY_SNIPPET` 正文块和新增主题引用；既有 AI/RAG 框架是独立的原文及版本契约，证据见审计。未来采用本期引用能力的调用者复用公共入口，不复制抽样逻辑。

## 关键不变量

### I-1：每个引用独立抽取
- Rule：候选池为原文加按既有顺序读取的启用变体，等概率抽取；原文 + 10 个变体就是 11 个候选。每次新的邮件生成中，每个引用出现位置独立调用抽取入口，允许重复，同一片段在主题和正文出现也分别抽取。无变体返回原文。正式生成不再用专家 ORCID/email、片段 ID 或池长度派生固定选择。
- Applies to：`ContentVariantService` 回复片段入口，`render`、`renderByCode`、GET preview、无显式索引的 previewDraft。
- Violation consequence：同长度片段池联动，或同一专家永远同一版本。
- 来源：original；K-variant-seed-call-sites（旧算法调用审计，不能继续当新算法规范）。

### I-2：主题引用的唯一身份
- Rule：新增一个可空字段 `subjectSnippetId: Long?` / `subject_snippet_id BIGINT NULL`。null 表示 `subject` 是自定义文本；非 null 表示 ID 是主题唯一权威，实时读取该片段。引用保存时 `subject` 保存该片段原文作为旧读取方的显示快照，不能作为发送失败回退。禁止用名称识别后端引用、复活 `subjectVariants`、增加 subjectMode 字段。
- Applies to：迁移、实体、Request→Command→create/update→Detail、previewDraft。
- Violation consequence：同名选错片段、清空引用后旧 ID 残留、失效引用偷偷发送旧文案。
- 来源：original；K-variant-pool-dto-chain。

### I-3：主题必须可用
- Rule：主题引用保存和生成时，ID 必须存在且启用；候选池每项必须非空、原始长度不超过 255、无 CR/LF。变量替换后选中主题仍须非空、长度不超过 255、无 CR/LF；违反时拒绝，不截断、不去换行、不重抽直到成功。自定义主题保持原有占位符语法检查并执行同一单行长度约束。缺变量仍交给现有门禁/严格预览报告，不借主题检查放宽必填规则。
- Applies to：create/update、render/renderByCode、两类 preview，以及引用池读取。
- Violation consequence：随机抽到正文段落作为标题、失效片段静默降级、输出与审核内容不一致。
- 来源：original。

### I-4：先选原始文本，再渲染与检查
- Rule：一次生成中，主题及每个正文位置只选一次。`rawTexts`、正文、HTML 来源、预览块说明必须使用同一组选中原始文本。`effectiveRequiredKeys` 对主题和正文所有可用候选取并集；`alwaysRequiredKeys/requiredEsFields` 对各位置候选分别取交集后合并；发送门禁仍按选中 rawTexts 精确检查。列候选、算必填项、列表显示不触发抽样。
- Applies to：renderTemplate、resolveBlocks、previewDraft、renderTextPool、两类 requiredKeys。
- Violation consequence：显示 A 却校验 B，或者错误排除适合另一变体的专家。
- 来源：original；K-preview-mirrors-pipeline。

### I-5：生成与发送边界
- Rule：抽取发生在生成 `ComposeTemplateRenderResult` 之前，已有 `ComposedMail` 在 SMTP、记录写入及同一对象的再次投递中不重抽。重新执行批量任务会再次 compose，属于新的生成，允许不同；本期不承诺跨任务重试复现旧内容。预览属于独立样本，不能声称与稍后正式发送逐字相同。
- Applies to：公共抽取调用点及对现有发送链的保持性验证。
- Violation consequence：投递记录与实际内容不一致，或对跨任务重试作出代码不支持的承诺。
- 来源：original；ManualInitialOutreachService:699、731、751、759；SmtpMailDeliveryService:17、46、110。

### I-6：兼容与边界
- Rule：保留 `render/renderByCode` 的 variantSeed 签名以免改遍调用方，但该值不再固定回复片段的正式生成；QA_RULE 仍按原算法。保留 previewDraft 的可选 `variantIndex`：显式提供时仅作为旧客户端的确定性预览兼容输入；缺省时独立随机。该兼容分支仍走公共片段选取入口，不传入正式生成。`variantPoolSize` 暂保留旧的最大候选池大小语义，不能宣称笛卡尔组合总数。新前端不再依赖两字段。
- Applies to：ContentVariantService、模板服务、旧调用点及 DTO。
- Violation consequence：QA 行为意外变化，老界面失效，错误显示组合覆盖数。
- 来源：original；K-variant-seed-call-sites。

## 现状审计

证据附件：[reply-snippet-variants-evidence.md](reply-snippet-variants-evidence.md)，E-1/E-2 保留 grep 命令、完整命中和退出码；E-4 给原代码；E-5 给工作树文件哈希。证据来自本地现有代码，不冒充线上最新部署状态。以下行号以此快照为准。

### mail_compose_template / mail_compose_template_block

- Schema：V61 创建 `subject VARCHAR(255) NOT NULL`；block 的 template_id 有级联删除外键，ref_id 为无外键的可空 ID。V62 增 template_code 唯一键和 mail_type；V64 增历史 subject_variants；V84 增 required_keys。实体见 `template/domain/MailComposeTemplate.kt:7`。本期只增 subject_snippet_id，不改旧迁移、正文块结构和旧字段。
- 运行期写：`MailComposeTemplateService.create:59/save:62/saveBlocks:500`；`update:84/save:88/deleteAllByTemplateId:103/saveBlocks`；`setEnabled:109/save:112`；`delete:129/deleteById:132/deleteAllByTemplateId:139`。repository 的块删除 SQL 在 `MailComposeTemplateRepository.kt:21`。
- 历史数据写：V62 模板迁移和块迁移；V71 材料提醒主题及块；V78 QA 块脱钩；V87/V88 退订正文修改；V122 会议确认模板和块。V72 读取模板创建任务配置，并持有模板 ID 外键。这些旧迁移不重写。本期迁移不写业务内容。
- 读：`listAll:47/toDetail:434`、`listEnabled:50`、`findTemplate:431`、`render:153`、`renderByCode:159`、`renderTextPool:225`、`preview:275`、`previewDraft:287`、`resolveRefDisplayName:465`、`resolveBlocks:541`。另 `BatchSendConfigController:415` 直接读取模板检查可用性；`ManualExpertMailService:39` 通过 listEnabled 读模板，菜单 subject 目前直接取 `template.subject:46`，所以引用写入时保留可读显示快照，正式发信仍实时解析 ID。
- 原代码明确 `create:72/update:98` 写 `subjectVariants = null`；`renderTemplate:266` 仅 renderText(template.subject)。旧知识 K-variant-pool-dto-chain 所称 subjectVariants 已贯通不能当功能事实。
- 交互 X-1：主题保存→列表回显/再次编辑→正式渲染；X-2：片段变体变更→主题/正文候选→必填字段/ES 预筛→精确门禁；X-3：引用片段删除/停用→读取报错；X-4：预览原始片段→变量→输出说明；X-5：新 nullable 列→Spring Data JDBC 读写及旧测试手工建表。

### reply_snippet / content_variant

- Schema：V47 片段 content 非空、type、order、default、enabled；V64 历史 variant_group；V96 name 可空且不唯一。V67 变体 owner_type/owner_id、variant_order、content、enabled、idx_owner；原文在 reply_snippet，变体在 content_variant，不新增第三套存储。
- 片段运行期写：`ReplySnippetService.create:182`、`update:221`，分别验证后保存并 replaceForOwner；`setEnabled:254`；`setDefault:260` 清旧默认再设新默认；`clearOtherDefaults:295`；`delete:275` 先删变体再删片段。历史写是 V47 初始内容、V64/V96 结构变更。
- 变体写：`ContentVariantService.replaceForOwner:41` 事务内校验、删除旧值、顺序写入，启用为 true；`deleteForOwner:64`。调用方是 ReplySnippetService.create/update/delete；QA 管理的 delete:130 还会删除旧 QA 变体。新功能保留这些写路径。
- 变体读：`ContentVariantService.resolveBody:16/buildPool:89` 读启用池；poolSize 和 listByOwner；`ReplySnippetService.toDetail:281` 读全部变体；`QaRuleManagementService:166` 读旧 QA 列表；`QaMatchService:105` 使用确定性 resolveBody；`MailComposeTemplateService.possibleRenderTexts:235`、`resolveBlocks:614` 读变体。详见 E-1。
- 现有算法原文：`Math.floorMod(seed + ownerId!!, pool.size)`（ContentVariantService:27）。相同 seed、等长 n 的多个池，其索引只相差固定 ID 偏移，并不产生各池独立选择；这正是本期修改点。
- 片段其他读者：ReplySnippetService 的 listAll/listByType、resolveManualFrame:29、resolveAck:42、listSelectableFrameOptions:57、resolveDefaultSelectableFrame:83、resolveSelectableFrame:100、默认/版本计算。AIReplyDraftService、AiReplyPointByPointComposer、TrustReplyWorkbenchService、RagLetterComposer 消费这些“原文框架”接口。其注释明确排除 content variants，并用原文散列计算 frameVersion（:147）；本期不改变这些独立契约，不能顺手随机化。

### 生成、预览与真实发送

- 正式模板调用：IntroductionMailComposer:22/24、ManualExpertMailService:227、MeetingInvitationMailComposer:14、AutoMailReplyService:1156、MeetingScheduleService:119、MeetingConfirmationService:247；镜像预览 AutoReplyPreviewService:93。完整调用回执在 E-2。无需给这些调用者复制选择算法。
- IntroductionMailComposer:28 对选中的 rawTexts 和全候选 requiredKeys 做门禁，:40/43 从一次渲染构建 subject/text/html。PersonalizationGateService.evaluate:51 对 rawTexts 的裸变量做精确过滤，不能拿全候选并集直接要求每人全部具备。
- ManualInitialOutreachService:699 新 messageId、:700 compose、:731 PREPARED、:751 send、:759 记录同一 mail；该 PREPARED 写入没有正文快照，尽管 MailSendAttempt 实体已有可空 subject/body 字段。buildRetryableTargets:1077 筛出重试对象后仍回到 compose。不能说现有系统已保证跨执行重试同文。
- SmtpMailDeliveryService:17 接收 ComposedMail，:46/52 取其主题/正文，:110 调 sender.send；没有模板重渲染入口。本期不修改发送服务或 mail_send_attempt。
- previewDraft:307 先 resolveBlocks 一次，:336 后续引用 rawTextsByOrder；新主题须加入同一流程。原 variantPoolSize 在 :620 为 maxOf，原前端显示“组合 x/N”不是独立组合总数。
- 现有 Bean 链：模板服务对 MailVariableService 使用 @Lazy（:34），MailVariableService:112 反向依赖模板服务，ReplySnippetService 也依赖 MailVariableService。本期复用已注入的 ContentVariantService，不新添循环依赖。
- 测试耦合：MailComposeTemplateServiceTest:370/485 的确定性片段断言必须改成可控制抽样测试；ContentVariantServiceTest 的 QA 确定性断言保留。MailComposeTemplateBlockRepositoryIT:73 手写模板表且已漏 subject_variants/required_keys，本期与实体对齐；FlywayMigrationIntegrationTest:59 固定迁移版本 135，需同步新增版本断言。

## 实现方案

### T-1 统一片段池与抽取（I-1/I-4/I-6）

文件：`variant/service/ContentVariantService.kt`。

增加 `replySnippetBodies(ownerId, mainBody)` 与 `resolveReplySnippetBody(ownerId, mainBody, previewIndex: Int? = null, random: Random = Random.Default)`，沿用现有 repository 和启用顺序，不加 Spring Bean/配置/缓存。前者纯列候选；后者默认 `pool[random.nextInt(pool.size)]`；previewIndex 非 null 时用旧取余规则，仅兼容旧预览。允许传 Random 是可重复单测的接缝，不添加运行期随机种子设置。

既有 resolveBody 的 REPLY_SNIPPET 分支转到新入口且不传 seed；QA_RULE 与 useVariants=false/未知类型/无 ID 回退保持原行为。模板正文和主题都显式调用新入口；不在模板服务里再写第二份随机算法。保留 replace/delete/验证逻辑。

### T-2 主题字段与持久化（I-2/I-3/I-6）

文件：`template/domain/MailComposeTemplate.kt`、`template/controller/MailComposeTemplateController.kt`、`template/service/MailComposeTemplateService.kt`、`db/migration/V136__add_compose_subject_snippet_id.sql`。

新增列 SQL 固定为：

```sql
ALTER TABLE mail_compose_template
    ADD COLUMN subject_snippet_id BIGINT NULL COMMENT '邮件主题引用的回复片段 ID；NULL 为自定义主题';
```

本次审计最高迁移为 V135；执行前再次核对，若 V136 被其他任务占用，先修订本文文件名及测试版本，不覆盖别人的迁移。无新索引、外键或回填任务。

实体、Request、Command、Detail、PreviewDraftRequest 追加 nullable 默认 null 字段；Request.toCommand、create、update、toDetail 全链显式传递。`subjectVariants` 继续忽略/清空，不复活。引用保存写 subjectSnippetId 和已验证的主文本显示快照；自定义保存写 null + 用户文本。PUT 未传 ID 与显式 null 都表示自定义，属于完整编辑语义，不做“null 保留旧引用”。setEnabled 的 copy 保留新 ID。

主题引用的名称由前端现有片段列表按 ID 取得，无需第二个持久字段/接口；列表服务本身不能抽样。旧菜单的 subject 快照可读，但不代表最终随机主题，刷新快照仅发生在模板再次保存。

### T-3 主题与正文共用一次生成（I-1/I-3/I-4/I-5/I-6）

文件：`template/service/MailComposeTemplateService.kt`。

1. 增加窄范围私有辅助方法解析主题候选、验证单行约束、从公共服务选出 rawSubject。不新增独立框架。用于保存的候选验证与用于生成的候选验证必须重用。
2. render/renderByCode 的正式流程忽略旧 expert seed 对回复片段的选择；主题与每个正文引用分别调用一次抽取，rawSubject 进入 rawTexts，选中正文进入现有 rawTextsByOrder，renderText 在抽取后执行。无引用的纯文本无需随机调用。
3. GET preview 与 previewDraft 共用候选/选择方法；previewDraft 有 explicit variantIndex 才使用旧预览兼容选择，无值则随机。旧返回 variantPoolSize 保持兼容并纳入主题候选大小；不增加组合总数、新枚举或采样历史。保持专家/发件账号解析顺序、strictPlaceholders、缺变量提示、预览无 save/send。
4. RenderTextPool.subject 改为 subjectTexts: List<String>，主题并集/交集分别接入 effectiveRequiredKeys/alwaysRequiredKeys；复用 replySnippetBodies 取正文候选，避免生成和门禁使用不同启用规则。
5. 失效主题报 `主题引用的回复片段不存在或已停用（ID: n）`；多行/空值/过长提示 `主题片段第 n 个候选必须为 1–255 字的单行文本`，n=1 为原文。生成后长度/换行错误提示 `渲染后的邮件主题必须为 1–255 字的单行文本`。正文已有缺失/禁用片段跳过语义保持，不借此改成整个模板失败。
6. 旧源代码行为受已有测试约束；旧自定义主题含缺失变量时仍使用原门禁/预览占位符路径。不能把所有 renderText 调用改成严苛主题校验，校验只接主题出口。

### T-4 有意义的验证（I-1 至 I-6）

文件：变更清单的四个测试文件。ContentVariantService 使用可控 Random 返回指定索引，逐个验证池中原文/变体可被选择；模板服务用 spy/stub 控制公共入口的返回序列，断言主题和两个正文引用各取一次，同 ID 也不缓存成一次。

不要写“重复 100 次一定不同”之类概率脆弱断言。补充 DTO 往返、subject 引用到 custom 清空、候选更新后渲染、失效引用、单行长度、变量替换后换行、rawTexts 与输出同源、主题并集/交集和选中门禁、无上下文/有上下文两类预览测试。

MySQL 集成用例验证 V135→V136 后旧行 subject 不变且新列为 null，再验证新 ID 能通过 JDBC 保存读取和清空；修复此表手工测试建表的既有实体列缺口，不扩成其他表治理。

## 变更文件清单

共 9 个文件；一个模板/片段生成后端子系统；共享模板表只加一个字段。路径均相对仓库根。

| # | 文件 | 修改 |
|---|---|---|
| 1 | src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt | 公共池及抽取入口 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/template/domain/MailComposeTemplate.kt | nullable ID |
| 3 | src/main/kotlin/com/weibo/talentintroduction/template/controller/MailComposeTemplateController.kt | Request 与映射 |
| 4 | src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt | 主题解析、生成、预览、门禁、DTO |
| 5 | src/main/resources/db/migration/V136__add_compose_subject_snippet_id.sql | 新增迁移 |
| 6 | src/test/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantServiceTest.kt | 公共抽取及 QA 回归 |
| 7 | src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt | 主题/正文/预览/门禁测试 |
| 8 | src/test/kotlin/com/weibo/talentintroduction/template/repository/MailComposeTemplateBlockRepositoryIT.kt | 手工表字段对齐及 ID JDBC 往返 |
| 9 | src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt | V136 升级及旧行保持 |

未列出的业务文件不修改。证据附件中的其他模块属于检查边界，不是实施授权。

## 验收标准

- I-1：指定 Random 索引 0/1/2 返回 MAIN/A/B；正式 render 的 seed=0、任意数或 Int.MIN_VALUE 都不能恢复旧固定选择；控制公共入口返回 S1/B2/B3，断言结果正好对应三个位置且调用数为三。
- I-2：Request→Command→存储→Detail→previewDraft 的 ID 不丢；ID=null 且 subject=Custom 后重新读取为自定义；旧请求仅 subject 能保存。迁移只新增一列；旧 subjectVariants 忽略测试保持。
- I-3：主文、启用变体分别为空/256 字/含 CR/LF，保存和引用生成拒绝；停用变体不参与。变量值带换行时最终主题拒绝；原引用删除/停用不能改用显示快照。合法单行正文片段可被主题引用，不按片段名称或类型猜适用性。
- I-4：主题候选 `${institution}` / `${institution|your institution}` 的 union 含 institution、intersection 不含；受控选择第一项且 institution 缺失门禁阻止，第二项使用默认值且不误阻。对应主题 rawTexts、preview.subject 一致。正文交集规则回归。
- I-5：diff 证明发送服务、重试流程和记录写入未修改；使用已有 composer/send 回归测试，不新增快照功能。预览只读验证无 save/send 调用。
- I-6：QA 原确定性用例、legacy QA block 主文用例、TemplateVariantContextTest 保持通过；旧 explicit variantIndex 预览测试通过；缺省预览走随机入口。旧不带新字段 CRUD 和正文块缺失/停用回归通过。

实施后运行（此计划阶段未运行、未宣称通过）：

```bash
mvn -DskipNodeTests=true -Dtest=ContentVariantServiceTest,MailComposeTemplateServiceTest,TemplateVariantContextTest,ComposeTemplateGateControllerTest test
mvn -DmigrationIt=true -DskipNodeTests=true -Dtest=MailComposeTemplateBlockRepositoryIT,FlywayMigrationIntegrationTest test
```

第二条要求 Docker/MySQL Testcontainers；环境不可用须记“未验证”，不能以 mock 替代迁移成功。检查现有发送/QA/框架测试并运行本次调用链相关用例，不用整体重构换测试通过。两个子计划完成后再做一次组合回归。

## 人工验收清单

以下在测试环境执行，使用后台创建测试片段；需要指定 API 时通过已登录浏览器开发者工具发送同源请求。不得对真实专家发信作为验收。

### A-1：引用主题往返与旧自定义（O-2/P-4）
- 前置条件：后台新建 CUSTOM 片段，名 `主题测试`，原文 `Research discussion`，变体 `A research question`；记其实际 ID 为 s。新建模板有一个 CUSTOM_TEXT 内容块 `Hello`。
- 操作步骤：1. POST `/api/compose-templates` 传 templateName=`引用测试`、subject=`ignored`、subjectSnippetId=s、blocks=[{blockOrder:0,blockType:"CUSTOM_TEXT",customText:"Hello"}]；记录 t。2. GET 列表。3. PUT t 传相同名称和块、subject=`Custom subject`、subjectSnippetId=null。4. 再 GET。
- 预期结果：步骤 2 的 ID=s、subject=`Research discussion`；步骤 4 ID=null、subject=`Custom subject`。创建一份不传 subjectSnippetId 的旧请求也得到 null。
- 覆盖：I-2/I-6；X-1。

### A-2：引用读最新内容，独立抽取（O-1/P-1）
- 前置条件：新建片段 s 原文 `S-main`、变体 `S-alt`；正文片段 b 原文 `B-main`、变体 `B-alt`。模板主题引用 s，两个正文块均引用 b，后面接自定义文本 `END`。
- 操作步骤：1. POST preview-draft，带 subjectSnippetId=s 及上述块，不带 variantIndex。2. 多次预览观察输出集合。3. 将 s 的原文/变体改为 `S-new-main`/`S-new-alt`，再预览。
- 预期结果：主题只在对应 S 集合，正文位置各在 B 集合，末尾始终 END；同次两段允许相同，也允许不同；步骤 3 不再出现旧 S 文案。人工观察不以“几次必须不同”判断独立性，独立调用由机器测试验收。
- 覆盖：I-1/I-4；X-1/X-2；原文/变体 CRUD、正文顺序。

### A-3：坏主题明确报错
- 前置条件：A-1 的引用模板保留；通过后台依次把 s 停用、重新启用后写入两行原文，或删除 s。只用测试片段。
- 操作步骤：1. 每种状态请求该模板 GET preview。2. 新建一个 256 字主题片段并尝试作为主题保存。
- 预期结果：停用/删除报告 `主题引用的回复片段不存在或已停用（ID: s）`；多行/超长报告具体候选约束，均不返回旧显示快照作为有效主题；正文测试中的缺失片段仍按原有方式标明跳过。
- 覆盖：I-2/I-3；X-3；P-1 正文缺失语义。

### A-4：变量门禁与只读预览
- 前置条件：测试库通过现有片段 API 建立可接受的变量候选；若片段 API 当前强制 nullable 变量带默认值，使用测试库 SQL 将 s 原文设为 `${institution}`、启用变体设为 `${institution|your institution}`。选择 institution 缺失的测试专家和已有发件账号；不启动发送任务。
- 操作步骤：1. GET 模板 gate-fields。2. 带专家、账号、strictPlaceholders=true 预览两个候选；后端 01 阶段可用兼容 variantIndex 控制选择。3. 核对预览收件人、发件变量和发送记录数量。
- 预期结果：requiredKeys 含 institution、esFields 不因这个可选候选强制 institution；缺失必填时主题显示 `占位符未满足，无法预览`，有默认值时显示 `your institution`；预览不新增发送记录、不发邮件。
- 覆盖：I-4/I-5/I-6；X-2/X-4；P-3。

### A-5：既有流程回归与重试边界
- 前置条件：测试环境保留无变体自定义主题模板、QA 固定事实、已配置的 AI/RAG 框架，以及 SMTP 捕获器；测试发件账号仅连接捕获器。正式发件不使用此用例。
- 操作步骤：1. 预览无变体模板，查看旧 QA 和 AI/RAG 框架。2. 用模板向测试收件人生成并发送一封，查看捕获主题/正文和发送记录。3. 对需延续线程的旧邮件做原有回复操作。4. 若模拟失败后重跑批量任务，记录它是一次新生成。
- 预期结果：无变体输出保持原文；QA 和框架仍是已有固定事实/原文；捕获内容与同封记录一致；旧线程 `Re:` 规则和退订内容不变；新任务的样本文案允许变化，不声称复用上次失败正文。
- 覆盖：I-1/I-5/I-6；P-1/P-2/P-3。

### A-6：旧库迁移
- 前置条件：隔离 MySQL 测试库迁移到 V135，创建 subject=`Old subject` 的模板，记录 ID；备份只针对该测试库。
- 操作步骤：1. 应用 V136。2. 查询旧行 subject 与 subject_snippet_id。3. 经模板 API 设为引用 s，再切回自定义。4. 启用、禁用模板后重查 ID。
- 预期结果：旧行仍为 `Old subject` 且新列 null；ID 能保存读取并清空；启用/禁用不改变已有引用值。生产部署与回滚不属于本次计划执行授权。
- 覆盖：I-2/I-6；X-5；P-4。
