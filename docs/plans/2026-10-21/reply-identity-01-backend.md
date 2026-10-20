# 01 人工回复首尾校验与补全预览

状态：详细计划，待实施；依赖：03 全局设置完成。基线 `b0d2f278a9dddc0be0dcf6fbd2e9b5a947c8a135`，2026-10-21。代码证据：[审计回执](reply-identity-evidence.md) E06–E12b、E19–E22。用户明确要求软确认，禁止改成不可继续的身份门禁。

## 需求描述

对普通来信人工回复、会话回复和显式跟进邮件，以当前专家、真实发送账号、回复片段核对称呼与签名。检测到问题返回可确认提醒；确认后允许原样发送。提供只读接口，给下一阶段的补全按钮返回预期首尾和缺失判定。

必须保持：现有发送账号/线程归属规则；SMTP 幂等与草稿状态；附件、会议校验、QA/RAG 审计及原有内容安全确认；不更改任何已写正文。
不包含：自动回复、批量首封、模板直接发信、历史邮件修复、模板管理界面改版、姓名模糊搜索、LLM 判断、新 DB/ES 字段。

## 关键不变量

### I-1: 身份来自真实发送上下文
- Rule：预览与发送复用 PendingMailOperationService 的来信账号/会话锚点解析。来信模式允许既有显式 senderAccountCode 语义；跟进模式必须重读同联系人 SENT OUTBOUND 锚点和账号范围。不得拿登录人、上一个专家、邮件正文或任意浏览器显示姓名替代。
- Applies to：新增预览入口、sendManualRichReply、sendConversationManualRichReply、executeManualRichSend。
- Violation consequence：预览正确但实际签名属于他人。
- 来源：K-explicit-outbound-anchor-server-revalidation。

### I-2: 回复片段是格式来源，身份另行验证
- Rule：首尾取03单次查询的默认快照；选择排序与 resolveDefaultSelectableFrame 的 SALUTATION/CLOSING 一致，不能调用该方法跨多次查询组装新期望值。其他 slot 不使用，不随机选变体。每次分析用一个 contact/account/profile/variables 快照生成两项期望值。姓名按 ExpertRecipientNamePolicy 规则；资料缺失为不可判定，不靠姓名检索其他专家，也不把 contact.expertName 与 profile 名称字符串差异直接判为身份冒用（旧代码无这种事实校验）。
- Rule：含身份变量的片段按本次资料渲染；被引用变量为空时即使有 fallback 也标注不可判定，不用预览假数据填补。通用称呼/团队签名不强制个人姓名；写死个人姓名仅能依下文确定性规则核对。无法识别的字面配置返回不可判定，可由人确认发送。默认缺失/禁用、未知占位符、残余占位符同样不可判定。
- Rule：完整格式与当前默认不同只称“不一致”，不宣称一定冒用。其他非默认合法格式可以人工确认继续，本轮不扩大到全库变体匹配。
- Applies to：预览、发送校验；不改模板存储。
- Violation consequence：错误片段自证正确，或凭空补入姓名。
- 来源：K-selectable-reply-frame-server-resolved-snapshot；原始需求。

### I-3: 软提醒可确认通过
- Rule：新增 finding 均为 SafetySeverity.NORMAL：REPLY_SALUTATION_MISSING、REPLY_SIGNATURE_MISSING、REPLY_SALUTATION_MISMATCH、REPLY_SIGNATURE_MISMATCH、REPLY_IDENTITY_UNCERTAIN、REPLY_IDENTITY_MULTIPLE_OR_MISPLACED。按 slot 去重，最多两条主问题加一条总体不可判定。
- Rule：合并到既有 findings，复用 MANUAL_SEND_SAFETY_BLOCKED / safetyWarningConfirmed。未确认时返回现有 422，尚不 claim/SMTP；收到用户本次确认后，即便问题仍存在也继续原有发送流程。不得新增永不可确认的异常，不使用 STRONG/输入文字确认处理姓名问题。
- Rule：仅身份校验的读取或解析异常转为不可判定提示，确认可继续；既有认证、真实账号不存在、附件损坏、SMTP 等错误不得吞掉。原有 STRONG 内容风险规则保持不变。
- Applies to：executeManualRichSend；所有新增 finding。
- Violation consequence：把建议误做成硬门槛，或绕过原有业务校验。
- 来源：用户 2026-10-21 修正。

### I-4: 比较最终本次正文
- Rule：在变量渲染及规范化后，对 finalTextBody 与 finalHtmlBody 可见文本分别检查，随后 SendPayload/SMTP/落库仍使用原来的 final body。不能仅检查客户端 textBody 而漏掉 HTML 的错误姓名。
- Rule：仅本次回复区首尾有效；正文中间、隐藏 HTML 节点、引用中出现正确姓名都不能抵消首尾异常。MATCH 仅表示配置比较一致，不证明邮箱实际所有人身份。规范化只用于比较：实体解码、CRLF、NBSP、首尾空白、同一行连续空白；保留大小写、姓名字符、段落顺序及标点差异为提醒，不用模糊子串匹配。
- Rule：引文排除必须有服务端真实源正文依据；容器标签本身不构成信任。接受与真实来信/出站锚点正文匹配的尾部容器，或与真实锚点生成的 `On YYYY-MM-DD HH:mm, <accountCode> wrote:` 及实际引用内容一致的尾部；不能以任意客户端 marker/任意正文中的 On 或 From 截断。边界无法核实则不可判定。纯文本和 HTML 分区不一致也提示。
- Applies to：新服务 analyze、预览、最终发送。
- Violation consequence：历史错误姓名误报，或引用中正确姓名掩盖当前错名。
- 来源：原始；本次 followupQuoteText 审计。

### I-5: 只补明确缺失
- Rule：预览结果每项只有 MATCH/MISSING/MISMATCH/UNCERTAIN/MULTIPLE_OR_MISPLACED，并返回 canInsert、expectedText、actualText、reasonCode；这些是响应状态，不落库。
- Rule：MATCH 不建议插入；识别出已有 Dear/Hi/Hello/尊敬/您好等称呼或 Best regards/Kind regards/Regards/Sincerely/此致等结束段，即便姓名不符也不算缺失。重复、错位、裸姓名等无法明确分区的格式返回不可判定/错位，不自动叠加。正文正常段落且确无首尾结构才为 MISSING。空编辑器两项均可缺失，只有预期内容可信时 canInsert=true。
- Rule：接口返回文本及可核验的引用边界描述，不返回可直接执行的 HTML、也不自动保存/发送。
- Applies to：预览响应；02 消费契约。
- Violation consequence：补全反而产生两个姓名/两份签名。
- 来源：原始需求。

### I-6: 保持发送与持久化契约
- Rule：复用既有确认参数、requestId、发送 attempt、草稿 version；确认提示不创建发送成功记录、不关闭草稿、不增加计数。已 SENT 的幂等请求继续返回原结果，不因后来片段修改要求重发。审计沿用当前 findings confirmed 机制。
- Rule：检查只读当前数据；预览不得调用可能生成unsubscribe_token的完整renderForContact链，显式以unsubscribeEmail=null构建变量（E30）。不以历史 AI READY/BLOCKED 作为门禁；不修改原邮件、附件、业务状态或其他模块模板解析。
- Applies to：所有本轮入口。
- Violation consequence：重复投递、误清草稿或跨模块回归。
- 来源：K-ai-generation-observability-not-send-gate。

## 现状审计

### 只读格式与身份数据
- reply_snippet：V47__create_reply_snippet.sql + V64（variant_group）+ V96（name）；id/type/content/display_order/is_default/enabled/updated_at，数据库没有“每类型唯一默认”约束。ReplySnippetService.defaultSnippetId 以 displayOrder/id 排序。
- 检索范围的运行时写入口在 ReplySnippetService.create/update/setEnabled/setDefault/delete/clearOtherDefaults（E02/E03）；初始化迁移 V47、后续列迁移 V64/V96。本轮无新增/修改写路径。
- 读取：ReplySnippetService list/resolve*；MailComposeTemplateService 按 ref_id 读；AiReplyDraftService、AiReplyPointByPointComposer 读 manual frame；RagLetterComposer 与 TrustReplyWorkbenchService 读 selectable frame。本阶段不改全局 resolve；03修改默认数据会自然影响这些消费者，不能宣称生成行为完全不受数据更新影响。（来源：K-manual-frame-three-consumers，已重查并订正）
- mail_sender_account/expert_contact：V1 与当前 domain 定义，关键身份为 account_code/sender_name/sender_email、contact id/orcid_id/expert_email/expert_name；后续账号共享收件箱字段见 V134。此计划只读，账号维护/专家录入/绑定/同步的存储写路径不改。
- MailVariableService.resolveExpertProfileFor 先查当前 index level，APPLICATION 不存在回退 CANDIDATE；异常返回 null。buildVariables 的 expertName 来源 profile，不能把“变量渲染调用完成”当身份核实完成。

### 发送与存储交互
- UnmatchedInboundMailController.sendManualRichReply → PendingMailOperationService.sendManualRichReply；MailboxConversationController 的会话接口 → sendConversationManualRichReply。两者进入 executeManualRichSend。
- executeManualRichSend 在最终变量渲染/正文规范化后 collectSafetyFindings，再 claim、mailDeliveryService.send、finalize；共用 finalTextBody/finalHtmlBody。
- GlobalExceptionHandler 当前统一输出 findings，最多 20 条，每条 sentence 最多 200 字；新问题排在列表前部，短摘要完整包含实际/期望身份，长 CLOSING 全文只放预览响应。不可依赖新增未声明的响应字段。
- mail_record/attempt/audit：本轮不新增写法；仍由 ManualReplySendAttemptService 现有 finalize 路径和 PendingMailOperationService 审计消费既有载荷。取消确认不得到达这些写入。
- mailbox_reply_draft（V152__create_mailbox_reply_draft.sql，E29）：username/contact/target/inbound/account_scope 唯一键，version/state/html_body/text_body/context_json/send_attempt_id/send_version。E19/E20检索到的 SQL 写实现 MailReplyDraftRepository.insert/save/discard/bindAttempt/closeSent/releaseCompletedBinding；MailReplyDraftService 对应 save/discard/bind/closeSent/releaseBinding 调用。读取 getTarget/get/list/summaries/validate*Send/completedBinding。本轮不改该表及任何 repository。
- interaction P1：片段管理修改 → 下一次预览/发送读取新默认；不能缓存跨专家正文。
- interaction P2：来信/跟进锚点解析 → 同一账户签名；普通、显式锚点、无来信会话都覆盖。
- interaction P3：最终 MIME 校验 → 确认 → 既有 claim/SMTP/草稿关闭；引用和正文两个表示均覆盖。


### 逐项代码证明与实现边界

|结论|源码位置（完整摘录见回执）|对本计划的约束|
|---|---|---|
|最终text、HTML先渲染再规范化|PendingMailOperationService.kt:575–605（E06）|在final body之后检查；不改变渲染顺序、不把检查结果当新正文|
|NORMAL已有可确认流程|同文件:690–696（E06）|identity findings进入现有集合；confirmed不再卡身份；原STRONG仍需原文字确认|
|提醒在claim之前|同文件:698–740、:782（E06）|未确认不得创建attempt或投递|
|SENT重放先返回|同文件:200–204、:417–451（E06）|不要把新检查搬到这些早退之前|
|会话账号来源为SENT锚点|同文件:458–501、:528–539（E06）|inspect不能直接采纳客户端senderName/account冒充锚点|
|profile名与contact名不是同一fallback口径|MailVariableService.kt:53–96、:133–155、:284–299（E08）|姓名缺失显式提示；不自行从邮箱推断姓名|
|现有htmlToPlainText不是DOM解析器|MailContentService.kt:25–33（E10）|它不处理div边界/hidden属性；不能把它用于本轮结构判断并声称支持隐藏节点|
|仓库已有JDK HTML解析先例|SourceAuthorEmailResolver.kt:200（E10b）|新服务可用ParserDelegator；不假定项目已有Jsoup，不新增依赖|
|跟进引文来自cleanedBody优先、日期截取|mailbox-chat.js:346–354、:521–571、:6699–6710；MailboxConversationRepository.kt:534–552（E12/E12b）|重建依据为anchor.cleanedBody非空否则body；eventAt=sentAt否则createdAt，无时区转换|
|通用buildVariables可能隐式写token|MailVariableService.kt:257–266→UnsubscribeTokenService.kt:27–36（E30）|inspect必须显式禁用该写入；新增只读测试验证token表无变化|
|两类MIME现有渲染分别读取资料|MailVariableService.kt:187–207（E08）|本轮不声称整个发送是DB+ES原子快照；两MIME差异被检查为提醒|

只读资料 `expert_contact`、`mail_sender_account`、画像索引、mail_record/inbound_processing 的既有生产者不变，持久化调用检索见 E25；本阶段新增写路径为零。SMTP、mail_send_attempt、mail_record、mailbox_reply_draft 的现有发送副作用仍由原发送链负责（E06/E19/E20）；不能让inspect调用finalize/bind/closeSent。既有前置业务校验可能先于identity提示失败，保留此顺序。

## 实现方案

1. 新建 ReplyIdentityService.kt（含 DTO/响应状态）：注入现有 snippet、变量、正文解析服务；纯分析入口接收服务端解析的 contact/account/anchor 及正文。按 I-2/I-4/I-5 实现预期值解析、首尾/引文分区、两种 MIME 比较。不给正文作隐式修改。
2. PendingMailOperationService.kt 抽取最小上下文解析 seam，让预览与发送重用相同来信/锚点规则，保持现有 SENT 重放早退顺序（I-1/I-6）。新增只读 inspectReplyIdentity；发送路径最终规范化后汇入 NORMAL findings（I-3/I-4）。不挪动其他既有业务校验。
3. 新建 ReplyIdentityController.kt：POST `/api/mail/reply-identity/inspect`；请求 sourceKind=INBOUND/CONVERSATION、contactId、inboundProcessingId（INBOUND 必填）、senderAccountCode（仅 INBOUND）、accountScope、anchorMailRecordId（仅 CONVERSATION）、htmlBody/textBody。严格禁止混合两种身份；contact 必须与 processing/anchor 一致；沿用 /api 登录保护。无 DB 写、无 SMTP。响应包含真实 recipient/sender 摘要、两个 slot、引用边界描述。身份权限错误保持原失败；可选校验资料缺失返回 UNCERTAIN（I-1..I-5）。
4. 新建服务测试并在同文件包含独立 Controller 契约测试类（启动真实MVC并注册全局advice；不能只直接调用controller方法）。将 6 处 PendingMailOperationService 构造测试显式接入 guard mock/fixture；生产依赖必须实际注入，不能加默认 null 静默跳过校验。旧业务回归场景提供匹配 fixture；专门新增不匹配未确认、已确认继续、取消无副作用、HTML/text 分歧、真实历史引文、配置失效等测试（I-1..I-6）。


### T5 接口与确定性算法明细（拟新增；I-1–I-6）

文件仅限清单中的 ReplyIdentityService.kt、ReplyIdentityController.kt、PendingMailOperationService.kt 和测试文件。03提供数据快照，本阶段不增加任何 store 写入。

**接口契约**

- `POST /api/mail/reply-identity/inspect`：`sourceKind`为INBOUND/CONVERSATION；`contactId`必填正整数；INBOUND需要inboundProcessingId、允许senderAccountCode，禁止anchorMailRecordId；CONVERSATION允许accountScope/anchorMailRecordId，禁止inboundProcessingId/senderAccountCode。`htmlBody`、`textBody`可为空，以支持空编辑器。每个正文最多100000字符，超限400，不把超限转为已验证。
- `POST /api/mail/reply-identity/defaults-preview`：同一身份上下文 + `salutation{id,content}` / `signature{id,content}`。允许尚未保存的内容；校验id/type/enabled及模板语法；每项10000字符。仅返回 renderedTexts、缺失变量和实际账号摘要，不改全局默认、不用于最终发送authority。没有这个单独入口，02的“编辑内容实时预览”无法由inspect默认内容实现。
- 两接口必须认证；contact/processing/anchor不对应时按原业务错误拒绝，不当作普通identity提示。预览不存在SMTP成功/草稿SENT路径。新controller请求验证失败使用400，返回的UNCERTAIN仅用于配置/解析/可选资料问题。
- inspect响应：`context{contactId,inboundProcessingId,anchorMailRecordId,accountCode,recipientEmail,senderName}`、`defaultsFingerprint`、`salutation/signature{status,canInsert,expectedText,actualText,reasonCode}`、`quoteBoundary{kind:NONE|VERIFIED_TAIL|UNCERTAIN,canonicalQuotedTail}`、`warnings[]`。正文型响应字段均为普通文本。新的状态/字段只在响应中，不持久化。

**预期值**

1. 新service读取03的一份defaults快照；profile只调用一次resolveExpertProfileFor；通过公开buildVariables(account, profile, unsubscribeEmail=null, previewFallbacks=false, contact=contact)构建变量，再调用现有MailComposeTemplateService.renderWithVariables（:856）。这些依赖放新service，不扩大Pending构造改动范围。必须显式传unsubscribeEmail=null：E30证明默认路径可能写unsubscribe_token，不能把buildVariables误当无副作用。若模板/待检查正文含unsubscribeUrl，返回BODY_VARIABLE_REQUIRES_WRITE不可判定，不伪造URL也不生成token。模板仅比较使用的变量，不因无关profile字段为空提示。
2. 配置使用 `${expertName}`/`${expertFamilyName}` 则缺画像或对应值为空→UNCERTAIN；senderName/teamName同理；`${key|fallback}`的fallback可以显示建议，但身份变量为空时canInsert=false。在真实变量齐全时比较渲染全文，不把substring相似当一致。
3. 对字面称呼：完全等于 `Dear Professor,` / `Dear Colleague,` / `Hi,` / `Hello,` / `您好` / `尊敬的专家，` 的通用配置允许比较；`Dear`/`Hi`后去掉可选`Dr`/`Dr.`/`Professor`及末尾逗号后的完整名字等于当前family/fullName时允许；否则`UNVERIFIED_LITERAL`。字面签名包含当前完整senderName或teamName的独立行允许比较；只有`Best regards,`/`Kind regards,`/`Regards,`/`Sincerely,`/`此致`的通用结尾允许；其他无法核实的配置提示而不补。它是有限规则，不能推断任意语言的人名。用户手写其他格式仍可确认发送。
4. inspect编辑正文用上述无token写入的变量map渲染后分析（HTML map逐值HtmlUtils.htmlEscape，文本map原值）；禁止调用renderForContact/renderHtmlForContact/renderPreview，因为其内部会使用真实邮箱生成退订token。采用上述仅用于建议的解析器，空值返回可说明的reason。最终发送仍检查已经由原链生成的finalTextBody/finalHtmlBody。设置preview与inspect均不调用renderPreview的示例fallback。

**引文及HTML边界**

1. 新服务内部用JDK ParserDelegator的callback收集可见文本与block边界：br→换行，p/div/li/blockquote/h1–h6结束→段落边界；script/style/head忽略；hidden/aria-hidden=true或内联display:none、visibility:hidden节点不作为匹配文本。样式表外链/class/复杂CSS导致可见性无法确定时标为UNCERTAIN，禁止假称浏览器级视觉还原。不执行CSS/脚本，不重序列化发送HTML。
2. 有blockquote/type=cite/gmail_quote仅作为候选；仅尾部候选内容按标准化后与当前服务端来源匹配才排除。INBOUND来源为record.cleanedBody非空否则body；CONVERSATION来源为验证过的anchor同字段。任意客户端属性、`On ... wrote:`、`From:`不是信任凭据。
3. 无容器的现有跟进文本：依E12的转换规则重建“On 日期 时间, accountCode wrote:”+空行+源正文。两种MIME各自尾部必须包含完整等价引用正文；只能匹配唯一尾部。日期按E12b的sentAt/createdAt格式至分钟，不取本机时区时间。emoji/实体/HTML源转换结果对不上→UNCERTAIN，不截断猜测。
4. 返回可核验的canonicalQuotedTail，不返回HTML offset。前端以自身DOM节点文本映射唯一尾部；不能映射就不插入签名。HTML/text分区互相矛盾→UNCERTAIN、canInsert=false。没有任何引文候选则NONE。
5. 比较规范化：CRLF→LF、HTML实体解码、NBSP→普通空格、每行横向空格压1个、行首尾trim、去首尾空行、连续空行压1个；不改变大小写/姓名/标点，不在实际发送正文上做这些比较变换。

**slot状态决策，按下列顺序执行**

|条件|status / canInsert|说明|
|---|---|---|
|预期无法渲染、引用/可见性/两个MIME边界不可靠|UNCERTAIN / false|携带具体reasonCode|
|相同期望出现在中间、重复出现；可识别称呼/结束语重复|MULTIPLE_OR_MISPLACED / false|不把中间正确姓名当首尾正确|
|标准化后的完整期望段落位于首部/尾部，两MIME相符|MATCH / false|允许多行、多段签名；要求段落边界而非子串|
|首部已存在 Dear/Hi/Hello/尊敬/您好；尾部已有 Best regards/Kind regards/Regards/Sincerely/此致 开始的结束段，但与期望不等|MISMATCH / false|保留错误原文|
|首尾无marker但为独立短姓名式块（1–2行，每行至多6个由空格/连接号分隔的词，无句末标点），或不支持结构|UNCERTAIN / false|例如Bob，不能当作缺签名再叠加|
|空正文，或普通句子/段落且无上述现存结构|MISSING / expected可用时true|fixture正文固定 `Thank you for your reply.`；保守规则无法保证识别任意文体|

同一slot的text/HTML仅一边MATCH，另一边不匹配或缺失：提醒；canInsert=false，避免叠加已有另一种表示。空编辑器两边为空例外，两项MISSING。判断无法明确时优先UNCERTAIN而非自动改稿。

**发送接入**

在finalValidationText、会议校验完成后、原collectSafetyFindings附近调用analyzeFinalBody；新findings置前+旧findings，保持requiresStrong对完整集合计算。每个slot最多一条主要finding，再加至多一条总体不可判定；摘要形如`当前：…；建议：…`，两个字段各最多70字符并有省略标识，总句长不超过200，避免E07截断后丢掉建议。完整文本在inspect响应，发送弹窗只承诺摘要。

只有新增identity分析读取/解析发生异常时捕获为REPLY_IDENTITY_UNCERTAIN；认证/账号/发送源校验、原占位符、会议、附件及SMTP异常不吞。若第一次未确认返回422，第二次confirmed即使配置改了也不重新做不可继续的身份门槛；这是用户授权的本次软确认，不声称“确认过的期望配置版本永久有效”。原正文变量在第二次请求仍按现有渲染机制解析，不承诺两次ES查询的原子一致性。

## 变更文件清单

共 10 个文件，均相对仓库根；未列文件不在实施范围。

| # | 文件 | 作用 |
|---|---|---|
|1|src/main/kotlin/com/weibo/talentintroduction/mail/service/ReplyIdentityService.kt|新增分析与 DTO|
|2|src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt|共享上下文、预览、NORMAL 提醒|
|3|src/main/kotlin/com/weibo/talentintroduction/mail/controller/ReplyIdentityController.kt|只读 POST 接口|
|4|src/test/kotlin/com/weibo/talentintroduction/mail/service/ReplyIdentityServiceTest.kt|规则、HTTP 契约测试|
|5|src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt|双发送入口及确认|
|6|src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt|既有工作台回归|
|7|src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftSendIntegrationTest.kt|草稿与幂等回归|
|8|src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt|会议回归|
|9|src/test/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentFlowTest.kt|附件回归|
|10|src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt|RAG 回归|

## 验收标准

- I-1：两入口实际账号一致；他人 contact/anchor、越范围锚点失败，SMTP=0；不采纳客户端姓名。
- I-2：默认变更即时生效，禁用/无默认/资料缺失为 UNCERTAIN；不修改其他 frame 解析实现，默认数据修改的共享影响按03验收。
- I-3：未确认返回 422；同正文 safetyWarningConfirmed=true 时通过身份提醒并实际调用投递一次。配置无法核实也可确认继续；仅身份问题不要求强确认。混入原 STRONG 风险仍保留原流程。
- I-4：text 对 HTML 错、历史引用含他人姓名、正文中间正确姓名、伪造引用标记、隐藏节点分别验证；不发与校验不同的正文。
- I-5：MATCH/MISSING/MISMATCH/UNCERTAIN/重复错位状态矩阵；仅可信 MISSING 可插入，预览表写入次数和投递次数均为0；启用真实退订配置且该邮箱尚无token时，inspect/defaults-preview不得新增unsubscribe_token。
- I-6：取消无发送 attempt/记录/计数；已完成重试仍只一次 SMTP；附件、草稿、QA/RAG 测试通过。
- 构造点6项逐行回执见E11。使用 JDK11 跑新测试和表内6个现有测试类；新Controller契约测试须由Surefire实际发现。运行 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmysqlIt=true -Dtest=ReplyIdentityServiceTest,PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest,MailReplyDraftSendIntegrationTest,MeetingCalendarSendIntegrationTest,OutboundAttachmentFlowTest,RagSendBridgeTest test`；若Controller类是同文件另一顶层类，必须加入-Dtest列表。随后运行项目既有完整 `mvn test`。任何未列文件被证实必须改时先修订清单，禁止用跳过测试掩盖。

## 人工验收清单

仅测试环境及自有测试邮箱；无需向真实专家试发。

### A-1: 不一致确认可发送
- 前置条件：测试联系人 Daniel Morgan，发件账户 senderName=Alex Lee，后台设置含真实姓名变量的默认尊语/结束语；正文首行 Dear Alice，签名 Bob。
- 操作步骤：1. 在已有人工回复界面发送。2. 取消提示。3. 再次发送并确认。
- 预期结果：第一次取消后测试邮箱收信 0 封、草稿保留；确认后收到 1 封，正文仍为 Alice/Bob。仅身份提醒不要求输入“确认发送”。
- 覆盖：I-2/I-3/I-6、P1/P3。

### A-2: 发件账号与锚点一致
- 前置条件：同测试专家分别存在账户 A、B 的成功发件，senderName 不同。
- 操作步骤：1. 选 B 的历史发件跟进。2. 调用 inspect。3. 将账户范围切 A 后提交 B 锚点。
- 预期结果：第二步期望签名属于 B；第三步拒绝，投递 0 次。普通来信 inspect 返回该来信既有账号。
- 覆盖：I-1、P2、账号/线程回归。

### A-3: 引用与 MIME
- 前置条件：准备正确当前首尾、真实历史引用含 Alice/Bob；另备 text 正确但 HTML 首尾错误的请求。
- 操作步骤：1. 预览/发送前者。2. 提交后者。3. 提交伪造 On...wrote 引文截断请求。
- 预期结果：前者不因引文错名告警；后两者提醒且可确认，不把隐藏/引用中的正确姓名当匹配。
- 覆盖：I-4/I-5、P3。

### A-4: 配置缺失仍可确认
- 前置条件：后台禁用默认 CLOSING，保留手写正文。
- 操作步骤：1. inspect。2. 发送并确认提醒。
- 预期结果：签名 UNCERTAIN、canInsert=false；确认后仍能向测试邮箱投递一次，不生成虚构签名。
- 覆盖：I-2/I-3/I-5、P1。

### A-5: 原有发送能力
- 前置条件：测试附件、带会议快照草稿、RAG/QA 草稿各一份，正确首尾。
- 操作步骤：1. 各发送一次。2. 重提已成功请求。3. 提交一个原本会命中 STRONG 的测试正文并取消。
- 预期结果：附件可读、会议信息一致、原有审计保留；重提不新增邮件；STRONG 原确认流程仍生效，取消不发送。
- 覆盖：I-6、全部保持项。


### A-6: 只读接口与未保存配置预览
- 前置条件：测试专家Daniel Morgan、实际发件人Alex Lee；有默认`Dear Dr ${expertFamilyName},`与`Best regards,`换行`${senderName}`。记录草稿版本与发送列表数量。
- 操作步骤：1. 调inspect空HTML/text。2. 调defaults-preview临时称呼`Hi ${expertName},`。3. GET全局设置并重新查看草稿/发件列表。
- 预期结果：步骤1两项MISSING，建议分别为`Dear Dr Morgan,`、`Best regards,`换行`Alex Lee`；步骤2预览`Hi Daniel Morgan,`；步骤3保存默认、草稿版本及发送数量均未改变。
- 覆盖：只读预览需求、I-1/I-2/I-5/I-6、P1。

### A-7: 非标准已有签名不叠加
- 前置条件：当前正文最后独立一行为Bob；另一份正文首部正确称呼重复两次。
- 操作步骤：1. inspect两份正文。2. 使用旧界面发送第一份并确认。
- 预期结果：第一份签名UNCERTAIN/canInsert=false；第二份MULTIPLE_OR_MISPLACED/canInsert=false；确认第一份可送达自有测试邮箱1封，Bob原文保留。
- 覆盖：I-3/I-5。
