# AI 回复补强第 10 步：历史上下文边界与收件人真实身份

## 需求描述

- 可观察结果：LLM 成功生成时能看到该专家最近的有效往来，理解“前面已经解释过什么、专家仍在质疑什么、上次约定的下一步”，回复更连续、少重复。
- 可观察结果：当前正在回复的入站邮件不再同时出现在 `Mail history` 和 `Inbound email`；失败/待发送的出站记录、退信与内部记录不进入 prompt。
- 可观察结果：历史邮件只能影响语气、承接和问题去重，不能成为身份、资金、合同、政策、知识产权等事实的依据。
- 可观察结果：专家没有真人姓名时，`${expertName|Professor}` 使用 `Professor`；页面预览、人工发送和 HTML 发送均不再产生 `Dear EMAIL-*`、ORCID 或邮箱称呼。

必须保持不变：

1. 当前入站正文仍是本轮请求的唯一问题来源；历史内容不产生新的 requestFacts、rule IDs 或 claims。（来源：K-request-facts-not-flat-pool）
2. Grounded 模式事实仍只来自当前审核 `answerBody` 和研究档案允许字段；历史邮件、旧出站草稿和运营话术都不是事实授权。（来源：K-answerbody-source-exclusive、K-ai-research-profile-authority-parity）
3. 子计划 8/9 的 LLM 失败 banner、不可采用、fallback BLOCKED/内部参考边界不变；fallback 不消费历史邮件。
4. 自动发送现有 fail-closed 门禁不放宽；本计划不新增发送动作、不修改 readiness 或 CTA 权限。
5. 变量 placeholder/fallback 语法、模板配置和 unsubscribe 变量保持不变；只修正 `expertName/expertFamilyName` 的值来源。

不在本计划范围：

- 不做向量检索、对话摘要持久化、长期记忆表或邮件线程重建。
- 不把失败出站邮件标记为已发送，不修复历史数据，不修改 `mail_record` schema。
- 不把历史邮件交给 fallback 拼接，不让模型复述旧邮件中的未经审核承诺。
- 不修改专家 ES 文档、`ExpertProfile.displayName` 全局语义或联系人主数据；只在 AI prompt/邮件渲染边界过滤技术标识。
- 不改固定写死在模板正文里的称呼；验收前须确认标准 salutation 使用 `${expertName|Professor}`。

## 关键不变量

### Invariant I-1：历史集合只包含真实往来
- Rule：eligible record 仅为 `direction=INBOUND`，或 `direction=OUTBOUND && sendStatus=SENT`；比较大小写时统一大写。其他方向、FAILED/PENDING/UNKNOWN/null 出站一律排除。
- Applies to：`AiReplyContextBuilder.buildMailHistory`。
- Violation consequence：模型把未发出的内容当成已经向专家承诺过，产生错误承接。
- 来源：original。

### Invariant I-2：当前入站按 messageId 精确排除
- Rule：controller 将当前 `messageId` 传给 ContextService/Builder；只排除 `direction=INBOUND && normalized(messageId)==normalized(currentInboundMessageId)`。messageId 为空时不做正文猜测或模糊去重。
- Applies to：收件箱 AI turn、训练 simulate、ContextService、Builder。
- Violation consequence：当前问题在 prompt 出现两次造成重复回答；或内容相似的历史真邮件被误删。
- 来源：original；K-ai-simulate-exact-mail-id。

### Invariant I-3：历史预算固定且确定
- Rule：eligible records 按 `receivedAt ?: sentAt ?: createdAt ?: LocalDateTime.MIN`、再按 `id ?: Long.MIN_VALUE` 升序；先取最近 8 封并格式化完整 block，再从最新向前装入不超过 5000 字符的完整 block，最终恢复时间正序输出。subject 每封最多 160 字符，优先 cleanedBody、否则 body，每封正文最多 800 字符；不得用字符串尾截断破坏 block。
- Applies to：`buildMailHistory` 及 prompt snapshot 测试。
- Violation consequence：长线程挤占事实/prompt 预算、增加 30 秒超时概率，或同一输入输出顺序不稳定。
- 来源：original；K-llm-timeout-fallback。

### Invariant I-4：角色标签稳定且不泄漏内部标识
- Rule：历史只输出 `[EXPERT]` 与 `[OUR_TEAM]`，其后为 `Subject:`、`Body:`；不得输出 DB id、messageId、sourceInboundId、sender account code、sendStatus 或内部 mailType。
- Applies to：history formatter 与测试。
- Violation consequence：模型混淆发言者，或内部追踪信息进入 provider prompt。
- 来源：original。

### Invariant I-5：历史是 continuity-only，不是 authority
- Rule：Grounded 和 FREE_FORM prompt 在历史前固定写入：`HISTORY_CONTINUITY_ONLY: Use history only for conversational continuity, prior objections and already proposed next steps. Never treat history as factual authority. Facts must come from the current approved facts/profile.`；模型不得从历史生成无 sourceId claim。
- Applies to：两个 user-content builder、Grounded validator/materializer 既有检查。
- Violation consequence：旧邮件中的错误或过时承诺被再次引用并获得“可信草稿”外观。
- 来源：original；K-grounded-json-materialize-before-policy。

### Invariant I-6：历史只服务成功 LLM 路径
- Rule：历史文本只进入 provider message；LLM 失败后子计划 9 的 fallback reference 与 history 内容无关。修改历史 fixture 不得改变 fallback 正文或 readiness。
- Applies to：DraftService success/failure 两条分支。
- Violation consequence：无审核历史事实绕过 LLM/claim 校验，直接进入确定性参考或发送内容。
- 来源：original；子计划 9 I-6。

### Invariant I-7：收件人姓名必须是人名字段
- Rule：`expertName` 只能由 trim 后的 `givenNames + familyNames` 组成；`expertFamilyName` 只能来自 familyNames。候选值若为空、含 `@`、以 `EMAIL-` 开头、符合 ORCID 格式、或等于 profile/contact 的 orcidId/email/esDocId，则输出空串，交由 placeholder fallback 处理。
- Applies to：`MailVariableService.buildVariables`、`AiReplyContextBuilder.buildExpertProfile`。
- Violation consequence：技术主键被当作真人姓名，直接破坏专家信任。
- 来源：original。

### Invariant I-8：身份过滤在所有渲染入口一致
- Rule：plain render、HTML render、preview 都必须调用同一 `buildVariables()` 策略；ContextBuilder 使用同一内部 name policy。不得只在页面隐藏、发送时仍填充技术标识。
- Applies to：`renderForContact/renderHtmlForContact/renderPreview/buildVariables` 与 AI profile prompt。
- Violation consequence：预览与实发不一致，或模型在 prompt 里继续称呼 `EMAIL-*`。
- 来源：K-ai-preview-raw-adoption-boundary、original。

## 现状审计

### `mail_record` schema、写路径与读路径
- Schema/mapping：V1 建表；V6 cleaned body；V15 sender/trigger/source；V23 error/send-attempt；V24 attempt FK；V31 created_at index；`MailRecord` 映射 direction/messageId/subject/body/cleanedBody/sendStatus/receivedAt/sentAt/createdAt。无需 schema 变更。
- Write paths（全部生产 repository save）：`ManualOutreachTxHelper`、`MeetingScheduleService`、`ManualExpertMailService`、`ManualReplySendAttemptService`、`AutoMailReplyService`、`MailboxService`；V24 另有一次性历史 attempt link UPDATE。本计划不改这些 writer，也不回写 history selection。
- Read paths（全部主要 consumer）：`MailboxService`、`UnmatchedInboundMailService`、`UnmatchedInboundMailController`、`InboundMailSummaryController`、`AiTrainingController`、`AiQaExtractionService`、`PendingMailOperationService`、`ManualInitialOutreachService`、`ExpertContactManagementService`、`AutoMailReplyService`、`AutomaticApplicationPromotionService`、bounce/monitoring services。本计划只改变 AiReplyContextService 这一条读后格式化路径。
- Interaction points：同一 contact 的 records 含当前 inbound 和所有发送尝试；必须先用 direction/sendStatus/messageId 过滤，再做最近 8 条与字符预算，不能先 takeLast 后过滤。

### 专家档案与变量
- Schema/mapping：RAW/CANDIDATE/APPLICATION 三份 `src/main/resources/es/orcid_info_*.json` 均为 `dynamic:false`，`orcidId` keyword、`email` keyword、`givenNames/familyNames` text；`ExpertProfile.displayName` 当前在 given/family 都空时 fallback 到 orcidId。email-only 专家由 `ExpertIdGenerator` 生成 `EMAIL-<hash>`。这适合索引身份，不适合邮件称呼。
- Read paths：`MailVariableService.resolveExpertProfile()` 从 `ExpertSearchService` 读取；`AiReplyContextService.loadProfile()` 同样只读；ContextBuilder 还读取 `ExpertContact.expertName`。
- Write paths：ES 全量/晋升/降级/partial update/bulk/delete 收口于 `ExpertIndexWriterService`，由 discovery、enrichment、revalidation、promotion 和 operator sync caller 触发。本计划不改 writer、mapping 或 `_source`，不清洗源档案。
- Render interaction：`renderForContact`、`renderHtmlForContact`、`renderPreview` 都汇入 `buildVariables()`，因此策略必须在这里一次收口；模板 fallback 只有变量值为空时才生效。

### `expert_contact`
- Schema/mapping：V1 建表，`orcid_id` 非空且与 campaign 唯一，`expert_name` 可空，`expert_email` 非空；V11/V12/V14/V19/V48/V51 追加自动回复、首次回复、索引层、运营状态、国家和跟进字段。`ExpertContact` 直接映射 `orcidId/expertEmail/expertName`。
- Write paths（全部 CrudRepository save）：`ConversationStateService`、`ExpertContactManagementService`、`ExpertIndexLevelOperationService`、`ExpertOperatorStatusService`、`InitialOutreachService`、`ManualInitialOutreachService`、`ExpertIndexController`、`AutoMailReplyService`、`AutomaticApplicationPromotionService`、`PendingMailOperationService`、`UnmatchedInboundMailService`；`ExpertContactRepository.updateCountryById()` 是额外直接 UPDATE；V13/V14/V19 是历史 migration write。本计划不改任一 writer。
- Read paths（全部主要 consumer）：contact management/email alias/index sync、训练与未匹配 controller、mailbox/auto/manual/pending/bounce/monitoring/preview、QA 管理和模板服务。本计划只在 ContextBuilder 读取后过滤 expertName，不改变其他页面显示或检索。
- Interaction points：email-only writer 可以合法把 EMAIL 技术键写入 orcidId/contact name；AI profile 和邮件变量必须在消费边界过滤，不得反向覆盖联系人，也不得改变列表搜索/展示身份。

### 当前差距
- `buildMailHistory()` 直接 takeLast(20)，每封 1500、总 8000；未过滤失败出站、未排除当前 inbound、使用原始 direction 标签。
- 收件箱与训练 controller 已持有当前 messageId，但 ContextService API 未接收。
- Grounded/FREE_FORM prompt 都只写 `Mail history:`，没有明确历史非事实授权。
- `MailVariableService` 把 `ExpertProfile.displayName` 填入 expertName；无真人姓名时 displayName 恰好返回 ORCID/EMAIL 技术 ID。
- `AiReplyContextBuilder` 直接信任 `ExpertContact.expertName`，同一技术 ID 也可能进入模型 profile。

## 实现方案

### T1：定义有界历史格式化合同
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextBuilder.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextServiceTest.kt`
- `buildMailHistory(records, currentInboundMessageId)` 按 I-1/I-2 过滤，messageId 只 trim 并移除首尾 `< >` 后大小写无关比较。（I-1、I-2）
- 以固定 effective time + id 排序，过滤后 takeLast(8)，逐封施加 160/800；从最新 block 向旧 block 累加，加入下一个会超过 5000 时停止，最后 reverse 回正序。不得对总字符串直接 `take(5000)`。（I-3）
- 输出固定 `[EXPERT]\nSubject: ...\nBody: ...` 或 `[OUR_TEAM]...`；清理 subject/body 首尾空白，内部换行保留，禁止其他 metadata。（I-4）
- 单测覆盖 12 封截断、乱序输入、时间相同 id tie-break、current messageId `<id>` 归一化、current id 为空、failed/pending outbound、cleanedBody 优先、单封/总预算。

### T2：把当前入站身份传入两个人工生成入口
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextService.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`
- ContextService `build()` 增加尾部可选参数 `currentInboundMessageId: String? = null`，传给 Builder；默认值保持 `PendingMailOperationService` 只检查 research sufficiency 的既有调用兼容。（I-2）
- 收件箱使用 `detail.messageId`，训练模拟使用精确选中的 `inboundMail.messageId`；不得使用“最新邮件”的 messageId 代替显式 mailRecordId。（I-2）
- controller 捕获 DraftService 入参，断言 history 有旧 inbound/已发送 outbound、无当前 inbound/失败 outbound；训练入口由全量编译测试和 A-2 人工验收覆盖。

### T3：在两个 prompt 声明历史权限
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- `buildGroundedUserContent()` 与 `buildFreeFormUserContent()` 在非空历史前逐字加入 I-5 标记，再输出 `Mail history:`；无 history 时两行都不输出。（I-5）
- Grounded 测试让历史包含未经审核的 `We guarantee USD 500,000`，断言 prompt 中虽保留上下文但 plan 无对应 claim/source；LLM 返回该 claim 时既有 validator 拒绝。（I-5）
- fallback 测试分别传不同 history，断言子计划 9 的 reference 文本和 BLOCKED 状态完全相同。（I-6）

### T4：收口真人姓名策略
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextBuilder.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailVariableServiceTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextServiceTest.kt`
- 在 `MailVariableService.kt` 同文件定义 module-internal `ExpertRecipientNamePolicy`，提供 profile full/family 与 contact candidate 过滤，执行 I-7 的统一判定；不新增文件或修改 `ExpertProfile.displayName`。（I-7）
- `buildVariables()` 不再读取 displayName，改为 policy 的 full/family 结果；三个 render 入口自然共享。（I-8）
- ContextBuilder import 同一 policy；非法 contact.expertName 时省略 `Name:` 行，但保留 Email/Country/Status 及研究字段。prompt 的 Email 只作联系上下文，不作为称呼。（I-7、I-8）
- 测试矩阵：正常 full name、仅 given、仅 family、EMAIL-*、ORCID、等于 email、含 @、等于 esDocId、首尾空白；断言非法时 expertName/familyName 为空、preview `usedFallback=true`、plain/HTML 均渲染 `Professor`。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextBuilder.kt` | 历史过滤/预算/角色格式、contact 姓名过滤 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextService.kt` | currentInboundMessageId 透传 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | continuity-only prompt 权限声明 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 当前入站 messageId 传入 context |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt` | 精确训练邮件 messageId 传入 context |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt` | 真人姓名 policy 与所有渲染入口收口 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextServiceTest.kt` | 历史集合/预算/排序/姓名上下文测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | history authority 与 fallback 隔离测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt` | 收件箱当前邮件去重集成测试 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailVariableServiceTest.kt` | 技术 ID 过滤与三类渲染测试 |

边界：10 个文件，2 个子系统（AI 历史上下文、邮件收件人变量），0 个 schema/共享 store 新字段。执行中若需 history 表、摘要持久化、模板变更或第 11 个文件，必须停下修订计划。

## 验收标准

- I-1：history fixture 仅保留 INBOUND 与 SENT OUTBOUND；FAILED/PENDING/UNKNOWN/null 出站和其他 direction 全部消失。
- I-2：收件箱/训练构建历史时 current messageId 消失；messageId 空不做正文去重；旧内容相同但 id 不同的邮件保留。
- I-3：超过 8 封只考虑最近 8；subject<=160、body<=800、total<=5000；预算不足时丢最旧完整 block、保留最新完整 block；乱序输入得到相同输出。
- I-4：snapshot 只含 `[EXPERT]/[OUR_TEAM]/Subject:/Body:`；无 id/messageId/sourceInboundId/account/sendStatus/mailType。
- I-5：Grounded 与 FREE_FORM 两个 prompt 非空历史时都含逐字 marker；历史里的高风险承诺不能产生无 source claim。
- I-6：history A/B 的 fallback text、qaRuleIds、requestFacts、readiness 完全相同。
- I-7：正常姓名按 given+family；EMAIL/ORCID/email/esDocId/含@候选全部输出空；不再读取 displayName fallback。
- I-8：preview/plain/HTML 三条入口对同一 profile 结果一致；ContextBuilder 不写非法 Name 行。
- 交互集成：`mail_record writer states → history eligibility → current id exclusion → bounded prompt → LLM claim validator` 与 `ES profile → name policy → placeholder fallback → preview/plain/HTML` 两链均有测试。
- 运行：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyContextServiceTest,AiReplyDraftServiceTest,UnmatchedInboundAiReplyTurnKnowledgeTest,MailVariableServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean test
npm test
git diff --check
```

## 人工验收清单

### A-1：历史连续性
- 前置条件：同一专家存在旧入站“我担心项目真实性”、已发送出站“可先核验公司信息”和当前入站“合同何时提供”；LLM 正常。
- 操作步骤：打开当前入站，生成可信草稿；查看测试环境 provider request 或受控 prompt snapshot。
- 预期结果：history 依次包含旧质疑和已发送答复，当前邮件只出现在 Inbound email；生成内容承接核验顾虑但只用当前合同事实回答。
- 覆盖：I-1、I-2、I-5；需求第 1、2、3 条。

### A-2：训练模拟精确邮件
- 前置条件：同一专家有两封正文相似但 messageId 不同的入站；训练页明确选择较早 mailRecordId。
- 操作步骤：模拟生成并检查 prompt/history。
- 预期结果：只排除所选邮件的 messageId；另一封仍在 history，不按正文或“最新一封”误删。
- 覆盖：I-2；K-ai-simulate-exact-mail-id interaction point。

### A-3：失败/待发送记录过滤
- 前置条件：同一专家有 SENT、FAILED、PENDING 和 null sendStatus 的四封 OUTBOUND。
- 操作步骤：生成草稿并查看受控 prompt snapshot。
- 预期结果：只有 SENT 出站以 `[OUR_TEAM]` 出现；模型不说“as we already confirmed”来引用其他三封。
- 覆盖：I-1、I-4。

### A-4：长线程预算
- 前置条件：准备 12 封 eligible 邮件，每封 subject>160、body>800，时间/id 可验证。
- 操作步骤：生成并记录 history 长度、首尾邮件。
- 预期结果：只考虑时间最近 8 封并按时间正序；若 8 封超过 5000 字符，则删除最旧完整 block，最新 block 保留且没有半截 Subject/Body；单项及总长不越界；当前邮件仍排除。
- 覆盖：I-2、I-3。

### A-5：历史无事实授权
- 前置条件：旧 SENT 邮件故意含“Guaranteed USD 500,000”，当前审核事实没有金额。
- 操作步骤：询问具体报酬金额并生成；查看 coverage、草稿与校验结果。
- 预期结果：该问题仍缺失；成功草稿不引用金额；若测试模型输出金额则校验失败并进入子计划 8/9 的 BLOCKED 参考，而非可信草稿。
- 覆盖：I-5、I-6；必须保持不变第 2、3 条。

### A-6：EMAIL 技术 ID 称呼
- 前置条件：专家 profile 的 givenNames/familyNames 为空，orcidId 与 contact.expertName 均为 `EMAIL-6b9d5416e939bbe8ea0`；标准 salutation 为 `Dear ${expertName|Professor},`。
- 操作步骤：分别执行变量预览、plain 人工发送预览、HTML 发送预览和 AI 生成。
- 预期结果：三种邮件渲染均为 `Dear Professor,`；fallbackKeys 含 expertName；AI profile 无 `Name: EMAIL-*`；任何正文无 `Dear EMAIL-*`。
- 覆盖：I-7、I-8；需求第 4 条。

### A-7：真实姓名回归
- 前置条件：profile 为 givenNames=`Ada`、familyNames=`Lovelace`。
- 操作步骤：重复 A-6 三种渲染并生成。
- 预期结果：expertName=`Ada Lovelace`、expertFamilyName=`Lovelace`，salutation 为 `Dear Ada Lovelace,`，不触发 fallback。
- 覆盖：I-7、I-8。

### A-8：自动与人工发送回归
- 前置条件：准备一条成功 LLM READY 自动场景、一条失败 fallback 场景、一条纯人工编辑场景。
- 操作步骤：依次执行 decision/preview/人工发送。
- 预期结果：成功自动场景仍受既有全部门禁；fallback 仍 BLOCKED；纯人工正文不受历史生成状态阻断；三条发送内容都不出现技术 ID 称呼。
- 覆盖：必须保持不变第 3、4、5 条。
