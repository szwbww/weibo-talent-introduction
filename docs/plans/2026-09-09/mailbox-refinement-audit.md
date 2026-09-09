# 收发件箱修复：代码与数据路径审计

审计日期：2026-09-09；仓库 HEAD：`af25bf54df2bc70dd0fe9e3254b246a49395219c`。源码哈希见 `mailbox-refinement-evidence/source-sha256.txt`。仓库存在其他未提交改动，本次不清理、不覆盖。

## 证据等级与设计基准

1. 线上现状视觉证据：用户提供的三张截图（原始文件名分别为 `codex-clipboard-4cba2695-03e6-4f37-ac0c-207eadc7a789.png`、`codex-clipboard-0b552956-ca4a-46c9-bf35-8024fb6d0438.png`、`codex-clipboard-8b0fceca-96d1-46db-acd0-04e5f7b87b75.png`）。可直接确认大块专家设置挤入消息区、原文/技术信息嵌套、顶部筛选两行占位。
2. 代码证据：当前仓库的生产源文件。没有验证部署服务器 Git SHA；不得写“已确认当前线上与本地完全同版”。
3. 目标效果：用户确认的 `artifacts/mailbox-chat-refinement/` HTML/CSS/JS 实际浏览器预览；截图备份于本目录 evidence。专家/邮件部分为截图内容，其他记录及交互是示例，并非线上查询结果。
4. 后续明确指令覆盖旧截图：只显示 `⋯`；删除“待专家回复”；全部 tab 待处理优先、组内按最近来信倒序。旧截图中的“更多”文字、四个 tab、原排序均不是实现依据。
5. 生产导航、登录区、页面标题、全局按钮的 DOM/行为保留生产代码。禁止整页复制预览的 `.topnav`、`body`、全局 CSS 或 mock fetch；预览仅作为目标局部视觉参考。

## 数据存储与所有相关写入/读取边界

### D-1 inbound_mail_processing（来信与待处理权威）

- Schema：V5__create_inbound_mail_processing.sql:1 建表，V10 增加正文/回复链/处理人，V14/V15 增加分类字段，V120 当前唯一键 `(sender_account_code, uid_validity, imap_uid)`；`uid_validity=0` 仅旧代际未知。主键 id；`received_at` 非空；`process_status` 区分 MANUAL_REVIEW/PROCESSED。
- 生产写入全文检索命中：AutoMailReplyService `confirmManualReviewWithBody` :1221、`confirmProcessed` :1274 新建；UnmatchedInboundMailService `bindToContact` :178、`markResolved` :223；PendingMailOperationService :1066 标记处理，:1137 调用 `reopenManualResolved`。Repository :35 的条件 UPDATE 只允许撤销人工处理；所有这些路径均不以浏览动作更新状态。V14/V15 历史分类回填、V120 唯一键迁移不修改本计划代码。
- 读者：MailboxConversationRepository `rangeUnionSql` :454-469、`latestInboundByContacts` :269、`timelineMessages` :360；旧 MailRecordRepository 的 mailbox UNION；InboundMailProcessingRepository 队列/来信汇总/计数/UID finder；UnmatchedInboundMailController、InboundMailSummaryController、PendingMailOperationService、Auto/BatchAutoMailReplyService、AutoReplyPreviewService、GroundedAutoReplyDecisionService、AutomaticApplicationPromotionService、ExpertContactManagementService、ExpertDocumentBrowseService、ExpertMaterialService、AttachmentTransferService、AiTrainingController、AiQaExtractionService、TrustReplyWorkbenchService、RagReplyController、RagProcessContextResolver。以上消费者仍读原字段，不迁移语义。
- 本计划改变读取排序及展示、新收信 subject 字符解码；不改处理状态写者。交互 X1：任一 mark/reopen 写者 → 会话 pendingCount → 全部 tab 排序/待处理 tab membership。
- 来源：K-mailbox-inbound-source-authority、K-inbound-processing-write-paths、K-inbound-seen-not-processed-marker。旧知识 account+uid 描述被 V120 取代，不作为新判重约束。

### D-2 mail_record（发件、线程历史）

- Schema：V1 :97 建表，V6 `cleaned_body`，V15 `source_inbound_id`，V24 send-attempt 链接。本轮不加字段、不迁移。
- 当前生产保存入口全集：ManualOutreachTxHelper :59/:110（成功/失败）、MeetingScheduleService :144、ManualExpertMailService :69、ManualReplySendAttemptService :249/:328、AutoMailReplyService :346/:686/:961/:1159。最后者同时包含 INBOUND 历史镜像及自动发件。V24 历史关联回填不动。
- 读者：MailRecordRepository 的 mailbox/专家历史/监控统计；MailboxConversationRepository；ExpertContactManagementService；ManualInitialOutreachService；MeetingSchedule/AutoMailReply/BatchAutoMailReply/ManualExpertMail/ManualReplySendAttempt/PendingMailOperation/AutomaticApplicationPromotion/AutoReplyPreview/GroundedAutoReplyDecision 服务；BounceBackfill/BounceCollection/BounceRateMonitor；MailMonitoringService；TaskExecutionController；ExpertDocumentBrowse/ExpertMaterial/AttachmentTransfer；InboundMailSummaryController；AI/RAG 上下文与回复消费者。这些消费者不换源、不改发件流程。
- 只将 OUTBOUND UNION 进 mailbox；不得重复纳入 INBOUND mail_record。最近发件影响 latestMessage 摘要与时间线，不影响专家排序。X2：新发件保存 → latestMessage 更新但专家排序不提升。
- `source_inbound_id` 指历史 INBOUND mail_record.id，不能拿去调用标签/mark-resolved（来源 K-mail-record-source-inbound-id）。

### D-3 inbound_mail_tag

- Schema：V53 完整 SQL 已读：id 主键；inbound_processing_id 非空；tag_type VARCHAR(16)，qa_rule_id 可空，label VARCHAR(255)，source/created_by/created_at；唯一键 `(inbound_processing_id,qa_rule_id)`；custom 去重由 service 检查，无数据库 custom 唯一键。本轮不补索引/约束。
- 唯一业务写服务 InboundMailTagService：autoApplyQaTags :58、addQaTag :81、addCustomTag :106、deleteTag :123；AutoMailReplyService :1249 的落库后自动标签调用进入同服务；HTTP InboundMailSummaryController :154/:169/:194 调用该服务。
- 读者：该服务 listTags :126、listTagsBatch :149、stats；InboundMailSummaryController list/thread/options；MailboxService 详情；MailboxConversationRepository membership :519；AI 训练筛选与来信统计。新 timeline 标签字段复用 listTagsBatch；不创建标签仓库或专家标签映射。
- X3：聊天卡片标签 POST/DELETE → timeline/旧详情/来信汇总/更多筛选可读。mailbox-chat.js :690 当前为每封邮件请求整个 thread，且 removable:false；恢复直显标签时必须避免这个 N+1 全线程读取。
- 约束：只有 INBOUND_PROCESSING 才有邮件标签存储。发件没有标签写 API，预览的发件“添加标签”是假按钮，不得照搬；本次只恢复真实来信邮件标签，发件显示翻译。不要新造发件标签表。

### D-4 expert_follow

- V121 复合主键 `(username,expert_contact_id)`，contact FK；唯一写者 ExpertFollowService.setFollowed :31（INSERT IGNORE / DELETE）；读者会话 repository 的 followed EXISTS。用户来自 session，不接受前端 username。
- X4：关注写入 → 关注 tab、头部和列表星标；关注不影响待处理状态。原接口与存储完全保留。

### D-5 专家状态、层级、ES 标签（仅重接现有入口）

- mailbox-chat.js :1399-1446 使用 `/api/expert-contacts/{id}/operator-status` 与 `/index-level`；app.js :4936 `mutateExpertTag` 使用现有 ES 标签 API。此次不新增这些存储字段、不改变写服务。完整三层/状态写入体系以现有管理服务为准，不把 UI 选择值直接 patch ES。
- 专家标签物理写 seam：ExpertIndexWriterService.addTag :641 / removeTag :659，脚本维护 tags 数组；ExpertIndexController 从真实画像读取。画像层级迁移继续走 ExpertIndexLevelOperationService；状态继续走既有 operator-status 服务，禁止自行调用 ConversationStateService 或新增直接 DB 写。
- app.js :4830 附近共享 renderExpertTagEditor、:5364 renderMailboxExpertTagEditor、:4925 fetchExpertTagsFromEs、:4936 mutateExpertTag；共享默认输出和其他使用处不改。mc 的小型呈现只消费相同数据和写函数。X5：管理 UI 写 → GET 专家详情/ES tags → 头部摘要与专家列表详情一致。
- 此处为既有写路径宿主迁移，ES全局写者不纳入实施变更；不得借此增加全站画像重构。来源 K-expert-tag-editor-shared-render-contract。

### D-6 会话内前端缓存（新增非持久化 UI 状态）

- 当前 instance.drafts :173 保存人工草稿；instances Map :132 管理 host。当前没有 scrollTop/scrollHeight 保存恢复；选择专家清空消息 :463，静默刷新以最新窗口覆盖 :415，加载更早请求仅查 disposed :1698，存在跨专家旧响应风险。
- 新缓存只存当前页已加载窗口/游标/消息锚点与滚动偏移；key 包含 session 用户、contactId、accountScope。模块内 Map，切出 mailbox 保留、刷新浏览器清除；最多 10 个最近会话，每会话最多 500 条已加载元数据/正文，超出时丢弃该会话缓存并下次定位最新，不自动遍历远端历史。
- 写路径必须覆盖：scroll、选择专家前、unmount 前、成功加载更早、静默刷新前；读路径：selectExpert、专家列表 focus 进入、renderTimeline 后。清理：登出/账号身份变更、达到上限；缓存无效不可无限拉页找锚点。
- X6：外部专家详情“查看邮件”→ app.js :10105 focus → MailboxChat.selectExpert → 恢复或最新定位。X7：加载更早/翻译/标签/新来信 → 守住当前锚点、草稿和最新真实回复目标。

## 查询与接口已确认的问题

- `MailboxConversationRepository.pageConversations:169`、`explainConversationsPage:209` 都是 `ORDER BY latest_event_at DESC`；来自入站+出站 MAX，不符合最新需求。
- pending_count :157 来自 `MANUAL_REVIEW`；不能用专家 operatorStatus 或第一/最近发信推断 pending。
- membership :478 的日期/方向/主题/标签是单消息 EXISTS；count/page 共享。:554 `membershipClauses.joinToString(OR)` 外缺一层括号，组合到 q/followed 的 AND 时可被 OR 绕过；修正必须由混合方向+关注测试证明。
- :503/:516 关键词只查 subject；app.js :14359 快照没有 recipient。界面称“主题/内容”却未搜正文，纳入本次修复。
- 邮箱语义沿旧接口 `MailRecordRepository:509/:542`：出站匹配 expert_contact.expert_email，入站匹配 inbound_mail_processing.from_email；界面文案改“专家邮箱”避免来信方向下误称收件人。
- `waitingReply` 目前横跨 DTO/filter/service/SQL；本次 UI 删除全部判断/按钮，旧参数与 DTO 暂留兼容，计划不借删除一个 tab 扩大 API 破坏性变更。新 UI 不发送、不读取该字段。
- `ImapMailReceiveService:336` 直接读取 Subject header，:311 原样进 ReceivedMail；附件 filename 已有 MimeUtility.decodeText :501。对 subject 解码不能调用 getContent/download。会话 service :133/:183 直接投影旧 subject，旧库也需读取兼容。

## 前端样式盘点

逐字 HTML、CSS、基础 token/按钮规则见 `mailbox-refinement-evidence/frontend-before.md`，它是本审计组成部分。

- `.button` styles.css :802、hover :825、active :832、primary :838/:847；圆角7px、32px高、12px字，主色 #1e40af、亮色 #3b82f6。
- `.panel` :948、`.panel-head` :962、`.panel-head-actions` :979；只给 mailbox 局部派生选择器，不改共享 class。
- `.pre/.translatable-body-block/.translation-text` :1875/:1890/:1917；翻译 API app.js :1600，输出escapeHtml。
- `.inbound-tag-chip` :3727、QA/CUSTOM :3742/:3748、chip-x :3772；专家 tags :4624。新 mc 局部改尺寸，不改其他页面。
- modal token：styles.css :60-64 z-overlay50 / drawer60 / modal1000 / confirm1200；`.panel-bg` 为透明白0.55，浮层必须用不透明白。（K-panel-bg-token-is-translucent）
- `.mail-chat/.mc-*` 的所有实际使用点：只在 mailbox-chat.js + mailbox-chat.css；app.js 挂载于 #mailboxList；JS Style/Behavior 两测试直接锁旧结构。禁止把生产 shell 或共享 workbench 的内部 DOM 改成 mock。
- 旧筛选 index.html :714-752；syncMailboxChatChrome app.js :14352 只隐藏模式和分页，没有处理独立 toolbar。
- 专家管理旧 DOM mailbox-chat.js :751-789 在 `.mc-scroll`；邮件 extras :653-679 是被投诉折叠栏；manual/workbench :845/:891 已具备需保留的默认规则。
- 本轮所有新 CSS 必须来自 02 计划逐字全文；任何新增/修改 DOM 都在 S-1..S-6 表中归属，禁止 inline style、全局覆盖、抄预览 mock。

## 检索边界与复核命令

所有运行时读写检索范围为 `src/main/kotlin`，并检查 `src/main/resources/db/migration` 和 scripts 的 SQL 写入；测试 fixture 写入仅供验证，不视为生产业务写者。执行前以 source-sha256 检测漂移，有变化重新定位证据，不按旧行号硬改。

```sh
rg -n 'inboundMailProcessingRepository\.(save|delete)|mailRecordRepository\.(save|delete)|reopenManualResolved' src/main/kotlin
rg -n 'UPDATE inbound_mail_processing|INSERT INTO inbound_mail_processing|DELETE FROM inbound_mail_processing|UPDATE mail_record|INSERT INTO mail_record|DELETE FROM mail_record' src/main/kotlin src/main/resources/db/migration scripts
rg -n 'inboundMailTagRepository\.|INSERT IGNORE INTO expert_follow|DELETE FROM expert_follow' src/main/kotlin
rg -n 'MailboxConversationService\(|listConversations\(' src
rg -n 'mailboxFilter|renderMailboxInboundTagEditor|submitInboundAddTag|mc-' src/main/resources/static src/test/js
```

没有新的处理状态写入、新附件下载入口、数据库迁移或 ES 字段，因此本轮不得修改这些写服务的业务策略。历史主题修复只做读兼容，不批量覆盖数据库。

## X8 新增：列表专家标签读取与显示（2026-09-09补充）

- 用户新截图 `codex-clipboard-186aeba3-ca8b-4627-801a-26654c8dfff9.png` 红框为mc-person-meta的waiting badge。实际renderPerson在mailbox-chat.js:255-284，当前只有收发数量、waiting/pending；summary DTO也没有专家tags，因此不能仅换文案。
- `ExpertContact.kt:23` currentIndexLevel，repository继承CrudRepository提供findAllById；按SQL当前页id批量读即可，不需新查询/新表。
- `ExpertSearchService.searchByOrcidIds:631-660` 已有terms(orcidId)查询，size=当前ids数，_source=sourceFields；sourceFields:591含tags，toExpertProfile:494把ES tags读为列表。已有profile接口ExpertIndexController:325-335调用findByOrcidId返回同一画像tags。
- 新读取只复用批量search；不改ES mapping、writer、字段；现有ExpertIndexWriterService.addTag:641/removeTag:659仍为人工标签写seam，层级迁移沿原服务。关联K-expert-tag-editor-shared-render-contract。
- 写→读：管理标签写入ES → 刷新summary按当前层批量读取 → 左列表专家标签及右头部一致。与D3邮件标签完全分开；不可用pending或inbound tag代替。
- API仅增加expertTags nullable投影；null表示未取得有效画像结果，不假装空数组；[]才是已读取且没有标签。ES失败仅标签降级，不改变MySQL分页排序，不触发画像补全。
- 新CSS为S-7并并入02/S-6全文；使用原生title展示完整标签，不新增tooltip挂载/定位/逐人hover请求。

- 最新指令：专家列表不显示待处理badge，姓名行也不补标；pendingCount继续供tab和排序使用。邮件卡片现有处理状态/标记已处理操作不在本次删除范围。
