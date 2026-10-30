# 专家分级权限、个人待处理与标签只读 · 开发总计划

状态：已获用户持续实施授权；不代表实现/测试已通过。执行基线：`59eb60cf00534a342c1019827a62f2b7b1db0f2b`，含已独立验证的draft-review 01–04及其已提交计划修订；原研究基线为`main@b15bb069f43f89c45ee6859dba04a0baff5d774d`。

证据：[审计总表](expert-access-evidence/audit.md)、[逐字源码E01–E37](expert-access-evidence/source-excerpts.md)、[schema迁移链](expert-access-evidence/schema-chain.md)、[检索收据](expert-access-evidence/store-usage.txt)、[SHA256](expert-access-evidence/sha256.json)。

## 需求描述
1. 按专家给固定user1授予NONE/READ/FULL；READ可看标签不能改，继续看邮件和提交审核稿；FULL可以直接人工发邮件、操作附件、维护专家人工资料/标签/排期。用户仍只展示姓名和收发件箱入口。
2. 管理员能看到、筛选哪些专家给了可写权限。授权下拉直接在专家头部；“专家筛选”集成原更多筛选最上方，原字段不丢。⋯角标显示管理员当前待处理邮件数量，有数显示、0隐藏；待审草稿数是另一个明确的单位。
3. FULL之后**新持久化的来信**归user1，历史admin待处理不搬；user1有自己的待处理列表。撤权/降级时，尚未处理的user1待办回admin，避免无主。管理员仍可从全部查看完整往来。
4. 已有审核稿不因FULL升级自动发送或解除APPROVED锁；待审仍按专家筛选，右侧原编辑框审核，批准不自动发，批准后对话更新确认继续保留。

必须保持：现有admin人工回复、富文本、附件、原始草稿和发送幂等；原筛选七字段、分页、搜索、挂起；READ姓名/仅邮箱tab/附件禁用；原自动收信/分类/标签/IMAP去重/后台监控；资料与排期并发版本校验。

不做：通用RBAC、多用户分配页面、独立审批中心、实时推送、批量转派历史、改自动回复策略、全局邮箱/任务/模板/AI训练授权、会议视频管理、邮件正文邮箱脱敏、部署上线。FULL是上述单专家业务操作范围，不能等价为管理员账号。

两项明确设计选择（待本计划整体批准，并非猜测现状）：处理归属按首次落库时权限分配，不按receivedAt回溯；FULL降级后未完成待办回admin。本轮不会执行这些数据库修改。

## 关键不变量
### Invariant I-1: 三态单一事实
- Rule: 无授权行=NONE；有行access_level仅READ/FULL，NOT NULL默认READ。旧授权迁移为READ。canRead由等级派生，不增send/attachment/tag等冗余布尔列；既有无body PUT只确保可读，不把FULL降为READ；显式新body {accessLevel:"READ"|"FULL"}才切等级。非法值400。
- Applies to: ExpertMailReadAccessService全部读写、列表批量投影、授权controller
- Violation consequence: 旧授权被意外升级或旧页面把FULL降级
- 来源: original

### Invariant I-2: 精确接口与资源权限同时成立
- Rule: user1仍是user1，仅邮箱导航。认证开启时默认拒绝未列出的HandlerMethod+HTTP method；允许handler后仍查目标专家当前权限。READ只能读授权邮件/标签与审核草稿；FULL只操作该专家。不得按URL前缀、按钮可见性或请求体operatorName授权。管理员授权接口永不向user1放行。
- Applies to: AuthInterceptor/Policy、目标守卫、body专用校验、app请求闸门
- Violation consequence: 横向越权或变相全局管理员
- 来源: K-session-auth-does-not-imply-mail-authorization

### Invariant I-3: 标签可见不等于可写
- Rule: READ显示summary.expertTags和真实来信tags；空数组不显示占位，expertTags=null显示“标签暂不可用”。全部标签文字转义。READ无新增/删除/自动打标控件，手工调用写API返回403且DB/ES不变；FULL才可改当前专家标签。
- Applies to: 用户列表、头部、邮件标签、ExpertIndexController、InboundMailSummaryController
- Violation consequence: 只读用户修改数据或标签丢失
- 来源: K-expert-tag-editor-shared-render-contract

### Invariant I-4: 由真实实体解析授权对象
- Rule: processing→contact、mailRecord→contact、tag→processing→contact、attachment三种owner→contact、schedule→contact、草稿owner+target均由DB解析。请求contactId必须与解析结果一致。expertId经现有Normalizer/IdentityResolver规范化后匹配FULL contact的orcid与当前层级；无匹配拒绝。同一专家共享档案/标签不是新增contact邮箱授权。
- Applies to: 所有新放行详情、编辑、文件与发送入口
- Violation consequence: 换id访问别人的邮件、附件或资料
- 来源: K-expert-document-ownership-chain

### Invariant I-5: 来信归属一次分配
- Rule: 新增inbound_mail_processing.pending_owner_username VARCHAR(64) NOT NULL DEFAULT 'admin'；历史行全部admin，不用receivedAt猜归属。两个新建sink在同一事务锁contact并读取当前FULL：FULL→user1，否则admin；无contact→admin。以首次持久化时权限为准，重试、UID补收重复命中不重分配。物理邮箱mailbox_owner_code原语义不变。
- Applies to: 两个new sink、迁移、grant/revoke contact锁
- Violation consequence: 历史待处理被搬动或物理邮箱错乱
- 来源: K-inbound-processing-write-paths

### Invariant I-6: 撤权不产生孤儿待办
- Rule: FULL→READ/NONE时，持同一contact锁，在同一事务把仍MANUAL_REVIEW且owner=user1的行移给admin；READ授权、升FULL、重复FULL不搬历史admin待办。已完成行暂不改，撤销完成时如果owner=user1而当前不再FULL，回admin。这是本计划明确提出的撤权兜底策略，待整份计划批准；非声称线上现状。
- Applies to: 等级变更/revoke、reopenManualResolved、队列读取
- Violation consequence: 失权后无人可处理或重新授权偷走旧待办
- 来源: original

### Invariant I-7: 计数按处理人、数量单位明确
- Rule: 我的待处理=MANUAL_REVIEW且pending_owner_username=session，排除SIMULATOR_NOOP/不存在账号，保留禁用真实邮箱；user1再受可读/FULL准入约束。图标角标复用当前账号pendingBadge.total（邮件条数，含既有挂起排除），不受列表搜索/分页影响；0隐藏，失败显示不可用而非0。待处理tab数字仍为专家数，待审核是SUBMITTED草稿数。count/page/explain共用条件，All保留历史邮件。监控、来信汇总不变。
- Applies to: 会话聚合/排序/分页、挂起、旧队列、顶栏及更多图标
- Violation consequence: 列表与角标不一致或user1邮件仍进admin待处理
- 来源: K-mailbox-inbound-source-authority

### Invariant I-8: FULL发送复用现有事务链
- Rule: FULL可直接人工发送，无需草稿审核；仍复用两个现有rich-reply入口及attempt幂等、附件/锚点归属、安全确认。鉴权位于业务副作用前，服务入口用authenticatedUsername再次校验；请求体operatorName不代表身份，user1审计写session名。已获准开始的SMTP请求不承诺被后续撤权召回；撤权后的新请求403。
- Applies to: PendingMailOperationService两个入口、旧草稿绑定、下载
- Violation consequence: 重复发送或绕过安全/资源校验
- 来源: K-manual-send-success-transaction-and-dedup

### Invariant I-9: 最终筛选和授权控件位置
- Rule: 管理员原更多筛选完整保留，第一项新增专家筛选（全部/user1可写/待审核）；原七字段顺序随后、Reset/Apply各一个，与原筛选AND，应用重置页码。header只用直接select切NONE/READ/FULL，保存中禁用，失败回滚。刷新旁不新增筛选。
- Applies to: mailbox-chat原FILTER_FIELDS、节点迁移、授权控件
- Violation consequence: 再次覆盖原面板或出现大型权限卡
- 来源: K-relocated-control-refresh-owner

### Invariant I-10: 上下文隔离与最小宿主
- Rule: 保留user1邮箱壳/姓名显示；READ继续独立只读详情，FULL右栏复用conversation-only MailboxChat。加载权限失败默认不可写；切换专家、focus/visible/刷新、降级/撤权、logout/unmount时作废在途结果及可写编辑器/附件/游标/权限缓存。无正文localStorage，无admin全局启动链。
- Applies to: MailboxReadOnly、MailboxChat受限挂载、app宿主
- Violation consequence: 晚响应恢复旧授权或串专家草稿
- 来源: K-mailbox-readonly-needs-lifecycle-audit

### Invariant I-11: 审核是明确依赖，不伪造已实现
- Rule: READ审核流程沿既有draft-review计划；FULL新人工稿走原mailbox_reply_draft。已提交审核稿不因升级自动发送/自动撤回；已审核稿对user1始终不可改（即使FULL），要发送新内容需新建人工稿。管理员仍在同一富文本框审核/人工回复，审核通过不自动发，批准后对话更新须确认。待审入口只搬进原popover顶部，原admin草稿tab含义不变。
- Applies to: MailReviewDraftService、两端宿主、pendingReviewOnly查询
- Violation consequence: 绕过审批锁或把原型当生产功能
- 来源: original

### Invariant I-12: 存储写路径不覆盖归属
- Rule: 新归属列只由新建分配、撤权兜底、撤销完成兜底写；其余四个已有copy-save改用保持其原字段集合的窄UPDATE，禁止陈旧实体save覆盖归属。新写读由同一MySQL事务处理，不挂best-effort。contact.needsManualAttention仍按专家所有待处理数维护。
- Applies to: AutoMailReplyService重分类、Unmatched绑定/完成、Pending完成、Repository重开
- Violation consequence: 并发撤权后旧save恢复user1导致待办丢失
- 来源: K-inbound-processing-write-paths

### Invariant I-13: 不扩大成全局管理
- Rule: FULL范围为当前专家人工邮件/附件资料/人工档案/标签/备注位置/排期/现有单专家状态管理；全局邮箱账号配置、批量发送、任务/AI训练、模板配置、全局专家搜索、人工接管分派、会议视频库不开放。自动收发/晋级/标签流程不受user1交互授权守卫影响。READ附件仍不可查看下载，邮件正文邮箱不做脱敏承诺。
- Applies to: 精确路由表、用户宿主菜单、后台任务
- Violation consequence: 无关功能扩权或后台任务中断
- 来源: original

### Invariant I-14: 阶段边界与证据
- Rule: 按子计划顺序实施；默认每步≤10文件、≤2子系统、每张已有表每步≤1新增字段。明确有界例外仅为前置review05继承13文件边界、03的41文件（原8加14直接构造fixture及19个必需MVC/manual context接线；仅明确退役02临时FULL全局写禁用pin，稳定安全断言保留）、06的12文件（原10加03暂缓owner路由的policy/guard精确激活）。不扩大例外的产品行为或弱化稳定断言。新增表/字段/API是拟实现而非现状；先核对SHA/迁移号/已有脏改。没有通过真实MySQL门禁不能把跳过算PASS；原规划阶段不上线，授权实施仍不授权部署。
- Applies to: 实施/验证/发布报告
- Violation consequence: 范围漂移、迁移冲突、无证据的完成声明
- 来源: K-master-plan-shared-file-sequential-gates

## 样式契约
### S-1：只读标签
- 复用：`styles.css:3828–3861`的inbound-tag-chip/qa/custom/inactive，`:4766–4800`的expert-tag；`mailbox-chat.css:120–121`的mc-tag-row和标签尺寸；完整原文见E21–E23。复用class，不改任何既有规则，无新CSS。
- DOM（每个label用textContent；列表和头部相同；消息行只用于INBOUND_PROCESSING）：
```html
<div class="mc-tag-row" data-role="readonly-expert-tags"><span class="expert-tag" title="完整标签">标签</span></div>
<div class="mc-tag-row" data-role="readonly-mail-tags"><span class="inbound-tag-chip qa">邮件标签</span></div>
```
- 空数组不生成行，null在相同区域用`<p class="mc-note">标签暂不可用</p>`；未知标签显示原值。无x、input、button、data-action写入口。禁止inline style、未声明新class；不得调用共享可编辑tag renderer再用CSS隐藏删除按钮。
### S-2：管理员直接授权select
- 替换E20的按钮+badge，仍在`.mc-actions`末尾。原头部/姓名/排期/材料DOM保留。
```html
<select class="mc-access-select" data-action="mc-set-user1-access" aria-label="user1专家权限">
  <option value="NONE">user1：未授权</option>
  <option value="READ">user1：查看＋草稿</option>
  <option value="FULL">user1：可写</option>
</select>
```
- 新CSS逐字复制；全局既有class不修改：
```css
.mail-chat .mc-access-select{max-width:180px;height:34px;padding:0 9px;border:1px solid #b8cff5;border-radius:7px;background:#fff;color:#3564b9;font:inherit;font-size:12px;cursor:pointer}
.mail-chat .mc-access-select:hover{background:#edf3ff;border-color:#93b4ec}
.mail-chat .mc-access-select:focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
.mail-chat .mc-access-select:disabled{opacity:.45;cursor:not-allowed}
```
- 加载中/保存中disabled；失败用既有mc-inline-error提示并恢复服务器已确认值。无需确认/保存按钮；授权本身不发送邮件。列表FULL标记复用`<span class="mc-badge">user1：可写</span>`。禁止大卡片、额外权限面板、inline style、未列class。
### S-3：原更多筛选第一项与邮件角标
- 完整保留E18/E19：七个原字段及原Reset/Apply/关闭按钮；复用mailbox-chat.css:78–100的mc-icon、mc-filter-popover、mc-filter-fields、mc-field、mc-field-wide规则。430px面板/两列/gap14px/padding18px 20px/select34px/圆角7px均不改，手机沿原媒体规则。
- 在`#mailboxFilterFields`第一个子元素插入；选项数字来自服务器，未知不显示伪0：
```html
<label class="mc-field mc-field-wide">专家筛选
  <select id="mailboxFilterExpertScope" aria-label="专家筛选">
    <option value="ALL">全部专家</option>
    <option value="USER1_FULL">user1可写（2位）</option>
    <option value="PENDING_REVIEW">待审核（3条）</option>
  </select>
</label>
```
- 数字2/3仅说明文案格式，不写死。当前view的真实筛选状态仍用原mc-filter-summary呈现，不同时显示原active-filter数字角标。
```html
<button class="mc-icon mc-pending-trigger" type="button" data-action="mc-more-filters" aria-label="更多筛选，3条待处理邮件" aria-expanded="false" aria-controls="mcFilterPopover">⋯<span class="mc-pending-count" aria-hidden="true">3</span></button>
```
- 新CSS逐字复制：
```css
.mail-chat .mc-pending-trigger{position:relative}
.mail-chat .mc-pending-count{position:absolute;top:-5px;right:-5px;min-width:18px;height:18px;box-sizing:border-box;padding:0 4px;border:2px solid #fff;border-radius:9px;background:#ea8a23;color:#fff;font-size:10px;font-weight:600;line-height:14px;text-align:center;pointer-events:none}
.mail-chat .mc-pending-count[hidden]{display:none}
```
- 0设hidden；1–99显示原数，>99显示99+且aria-label/title含精确数。刷新/加载失败不冒充0，不清掉筛选面板。既有mc-filter-count规则保留供旧使用点，不就地改其颜色/意义。禁止inline style、第二Apply、刷新旁下拉、未列class。
### S-4：FULL仅复用右侧会话
- 原列表继续MailboxReadOnly；仅FULL选中时卸载READ详情/审核编辑器，将MailboxChat以`mode:"delegated-conversation"`挂入现有右栏。它只生成如下外壳，内部使用现有renderConversationScaffold、邮件、管理、compose、排期/材料控件，不复制整套富文本实现。
```html
<div class="mail-chat mc-delegated-conversation" data-role="delegated-conversation">
  <section class="mc-conversation" aria-label="专家往来信件"></section>
</div>
```
- 新CSS全文：
```css
.mail-chat.mc-delegated-conversation{display:block;width:100%;height:100%;min-width:0;min-height:0}
.mail-chat.mc-delegated-conversation>.mc-conversation{width:100%;height:100%;min-width:0}
```
- 复用E21现有mc-header/mc-actions/mc-compose/mc-compose-footer/mc-body；按钮的hover/active/disabled/focus沿mailbox-chat.css:65–71、78–82、109–112，不增另一套尺寸。邮件标签行沿S-1；FULL可编辑时沿现有专家管理overlay/tag editor与原材料drawer，**仅限定当前专家**。
- 原“查看专家详情”链接在user1模式替换为既有button class文案“专家资料”，不跳专家列表；资料宿主复用app.js的人工档案字段DOM/版本保存（原合同见expertManualProfile测试），只挂当前专家的人工资料表单，移除邮件地址展示、全局导航和会议视频/全局任务入口。不得整页mount联系人详情的重组器。
- user1姓名保持原renderIdentity逻辑，FULL右栏不渲染email与account身份行。READ没有FULL编辑器；FULL人工发送与审核历史明确区分。禁止inline style、新编辑器框架、未声明class、重画材料/会议表单。
- 人工资料DOM逐字复用E33的expertManualProfileSectionTemplate/EditorTemplate，仅容器新增`<div class="ec-profile" data-role="expert-manual-profile"></div>`放在当前会话管理overlay中；保留原`ec-section/ec-card/ec-editor`规则（app.js:11626–11698生成；styles.css内同名规则），不得复制字段表。给mountExpertManualProfile/context增加明确delegated标记：shell不生成视频nav/panel，mount不调用视频/ContactHistory；contact-details.js包装器在此标记下直接返回原session，不执行reorganizeContactPanel。所有已有admin调用默认值保持原行为。
- 人工档案复用class的精确样式位置：styles.css:12938–12953，包含ec-profile的gap12px、ec-section白底/padding14px/radius10px、ec-edit-grid两列/gap12px及720px一列媒体规则；禁止改这些共享规则。
### S-5：用户个人待处理入口
- 用户独立列表原搜索/刷新下增加两个既有chip，不挂管理员全局筛选popover。复用mailbox-chat.css:85–87的mc-filters/mc-filter，active沿原aria-pressed样式；无新CSS。
```html
<div class="mc-filters" aria-label="专家会话范围">
  <button class="mc-filter" type="button" data-action="reader-all" aria-pressed="true">全部</button>
  <button class="mc-filter" type="button" data-action="reader-pending" aria-pressed="false">待处理 <span class="mailbox-suspend-count" data-role="reader-pending-experts">1</span></button>
</div>
```
- 数字为服务端个人待处理**专家数**，1只是格式示例，0仍显示0；切换重新请求列表并page=0，不在当前页过滤。卡片若有pendingCount，用已有`<small>3条待处理</small>`呈现实际邮件条数。不新增class/inline style。

## 现状审计
[完整审计](expert-access-evidence/audit.md)包含store schema、全部检索命中的写/读路径分类及X1–X12交互点；以下为决定实施范围的证据摘要。

| 现状事实 | 源码证据 | 方案依据 |
|---|---|---|
| 授权只有“行存在” | V166；ExpertMailReadAccessService:37/62；E02/E03 | 加一个access_level，旧行READ，不建角色系统 |
| user1两层全局写禁用 | MailReadOnlyPolicy:49；app.js:21094；E01/E24 | 后端精确handler+资源校验和前端请求宿主同时改 |
| 标签已返回但user1未渲染 | MailboxConversationService:166/207/322；mailbox-readonly.js:249/296；E04/E17 | READ标签先行小切片，不新增标签表/API |
| 附件按username一律剔除 | MailboxConversationService:273/299/327/329 | 改为当前专家等级；下载仍单独守卫 |
| 待处理没有处理人 | InboundMailProcessing.kt；rangeUnionSql:611；pendingBadge:122；E05/E06 | 新增独立归属列，不拿物理邮箱owner代替 |
| 除新建还有状态写 | 六save+reopen UPDATE；E07–E11 | 新列必须防旧copy-save并发覆盖 |
| 原文件下载有unscoped入口 | MailboxAttachmentService.download→resolveReadyFileUnscoped；E12 | 三种真实owner解析后才读字节 |
| 收发箱排期走新日历 | app.js:27294；MeetingCalendarController:23；V125；E30–E32 | 守卫meeting_calendar_event而非只改旧MeetingSchedule |
| 资料编辑键不是contactId | ExpertManualProfileController:45/55；V153；E14 | 规范身份映射FULL，保留version，防catch转500 |
| 原⋯本来包含七字段 | mailbox-chat.js:95/1644；E18/E19 | 只插第一项，不替换原panel |
| 当前用户模块独立 | app.js:21158/21210；E25/E27 | 保留独立列表，FULL仅挂受限右栏，不运行admin bootstrap |
| 审核仍未实现 | no-review.txt无命中，draft-review-master.md为DRAFT | 明确前置计划；不把原型功能当已完成 |

前端逐字CSS/DOM在E17–E27/E33/E34；新class样式在S-1–S-5冻结。无共享class规则修改；只声明新增局部class，因而不扩大到其他页面样式。

存储增量只有两列：expert_mail_read_access.access_level、inbound_mail_processing.pending_owner_username，分两子计划。其他库/ES/文件继续调用现有业务方法；鉴权读取不改变其schema。旧审核计划的两个新store单列为前置依赖，不重复建设。

## 实现方案
### 顺序、依赖与发布边界（I-14）
- 可先独立执行01，让现有READ用户看标签。
- 本轮研究的main没有审核实现。审核功能按[既有总计划](draft-review-master.md)01→05完成，尤其V169先执行；本计划02才使用V170、05使用V171。若基线迁移编号变化，实施前修订白名单，不改已应用migration、不依赖outOfOrder。
- 既有审核计划在**实施前必须采用本总计划的最终UI修正**：取消顶部mailboxReviewPendingBtn；用户READ原流程保留；最终admin筛选入口由08实现。旧review05独立测试阶段可通过API/内部状态验证筛选，最终人验以08更多筛选入口为准，不先实现旧按钮再返工。
- 此后02→03→04a→04b→05→06→07→08串行。后一步只基于前一步验证通过的代码，禁止多个执行者并改app.js/mailbox-chat.js/同一service。
- 各步可构建、独立验证和部署为受限增量；02–06期间默认user1仍沿受限UI，新放行方法需先具备后端守卫。FULL完整功能到08整体验收才对外启用。仅后端新增列不代表功能已交付。
- 迁移向前兼容：旧字段/端点保留；业务回滚不能删除归属数据。若回滚到不识别owner的版本，必须先禁用新的FULL授权入口并明确admin会恢复看到所有待处理，不能宣称旧版本继续保证分流。

| 顺序 | 子计划 | 文件数 |
|---|---|---|
| review05 | [前置审核管理员闭环](draft-review-05-admin.md) | 13（继承已批准的有界门禁例外） |
| 1 | [只读标签展示](expert-access-01-read-labels.md) | 3 |
| 2 | [授权等级与能力投影](expert-access-02-access-level.md) | 10 |
| 3 | [精确资源守卫与人工发送](expert-access-03-resource-guard.md) | 41（原8+14直接构造+19必要context fixture接线例外） |
| 4a | [专家资料与标签目标守卫](expert-access-04a-profile-tags.md) | 5 |
| 4b | [日历、个人草稿与审核权限联动](expert-access-04b-calendar-drafts.md) | 7 |
| 5 | [来信归属写入与撤权兜底](expert-access-05-pending-owner.md) | 9 |
| 6 | [待处理查询与筛选范围](expert-access-06-pending-query.md) | 12（原10+2个owner路由激活文件例外） |
| 7 | [用户可写会话复用与能力隔离](expert-access-07-user-workspace.md) | 10 |
| 8 | [管理端最终下拉筛选与待处理角标](expert-access-08-admin-controls.md) | 7 |

### 精确准入表（03/04共同执行，默认拒绝其余）
以下是**拟开放名单**，不是声称现在已允许。每条同时需要method精确匹配、session和实际资源归属；目标解析后失败立即403/404，不先调用返回内容的方法。controller同名方法须以declaringClass区分。

| Handler/范围 | 最低条件及目标 | 边界 |
|---|---|---|
| MailboxConversationController.list/messages | READ或FULL；现有SQL授权与详情校验 | 不允许NONE；messages响应实时等级 |
| MailboxConversationController.pendingBadge | 当前session | 06的个人owner SQL；不接受user query |
| MailboxConversationController.conversationManualRichReply | FULL path contact + 锚点/附件关系 | service再验session |
| MailboxConversationController.follow/unfollow/setProgressStatus/dismissReplied/suspension/suspend/updateSuspensionReason/resumeSuspension | FULL path contact | 写当前session自己的状态；挂起只能处理本人的pending |
| UnmatchedInboundMailController.getUnmatchedDetail/sendManualRichReply/markResolved/cancelResolved | FULL，id→processing→contact | 无contact拒绝；详情user1 candidates为空；list/search/bind/AI/QA旁路不开放 |
| MailboxAttachmentController.listAttachments/download | FULL，source+id或attachmentId解析真实contact | 下载先解析三种owner；保留原路径安全 |
| OutboundAttachmentController.upload/downloadDraft/downloadSentAttachment；CalendarAttachmentController.download | FULL path contact且原有资源所有权全通过 | 草稿上传者检查、已发快照检查不取消 |
| ExpertMaterialController.listMaterials/requestTransfers/uploadMaterial/downloadZip/reconcile；ExpertDocumentBrowseController.listDocuments/download/preview | FULL path contact，所有附件成员同contact | 文件/zip逐项验证，不接受一个合法id夹带非法id；AI批量分析不开放 |
| ExpertContactManagementController.getContactDetail/changeOperatorStatus/changeIndexLevel | FULL path contact | 注册键逐一使用当前声明（见controllers收据），不得用模糊前缀；旧meeting-schedules/mail-send/batch/account/handoff不开放 |
| ExpertContactNoteController.get/save；ExpertContactLocationController.get/save/timing | FULL path contact | countries只读字典可允许；无全局数据修改 |
| ExpertIndexController.addTag/removeTag/getExpertProfile | FULL，规范化orcid/expertId+真实currentIndexLevel | body/query由04 controller二次守卫；不开放全局搜索/聚合 |
| InboundMailSummaryController.addTag/deleteTag/autoApplyTags | FULL，processingId或tagId反查 | 操作者取session；READ仅已有消息tags |
| InboundMailSummaryController.tagOptions | 登录user1只读选项 | 返回既有标签选项，非邮件内容/专家列表；不放stats/listMails/getThread |
| ExpertManualProfileController.get/patch | FULL，规范expert_key映射授权contact | 保留version CAS，403独立映射 |
| MeetingCalendarController.events/summaries/create/event/update/cancel | 当前contact FULL；query数组逐项核对；create body查contact；event id查DB | events不带contactId时user1拒绝；不开放全局日历导航 |
| MeetingConfirmationController.options/preview | FULL processing→contact，保留query/body一致性 | timeZones是日期字典，不包含专家内容，可只读开放 |
| MailReplyDraftController.target/save/detail/discard/list/summaries | FULL+owner，04服务/SQL控制 | 列表与count都先授权后分页；READ一律拒绝 |
| MailReviewDraftController既有计划定义的用户方法 | READ或FULL，owner/state规则不变 | approve/return等管理员方法仍仅admin；APPROVED对user1不可改 |
| TranslationController翻译、AuthController.logout | 原审核计划精确方法/登录身份 | 不扩大成全局API prefix；翻译只处理提交文本 |

请求body的权限不能在HandlerInterceptor里偷读inputStream；04用已反序列化的DTO检查。普通path/id归属在固定守卫里统一处理。新增守卫只读MySQL/既有身份解析器，不加入一个可配置权限引擎。现有全局只读AI、模板、sender-account元数据若不在表内，FULL右栏不生成依赖这些接口的控件；不能为了消除403直接放开整类接口。
分阶段规则：getUnmatchedDetail由04b第3项完成user1 candidates=[]/跳过suggestCandidates后开放；03不提前加入名单。pendingBadge及suspension/suspend/updateSuspensionReason/resumeSuspension由06完成个人owner SQL后精确开放，03保持拒绝。已有FULL path contact且原服务在任何副作用前验证全部成员同contact的材料transfer/ZIP沿既有校验在03可开放，必须覆盖混入外专家id的拒绝；真正缺失DTO目标授权的方法仍等04。上述均不删除最终准入表能力。

### 数据、接口和前端交接
- 02提供accessLevel；NONE无行，READ/FULL有行。保持canRead兼容字段；list和messages实时能力由后端提供，不在localStorage记权限。
- 05写owner，06统一消费：All不隐藏已委派专家；pendingOnly/排序/pendingCount/挂起个人数/旧队列按owner；needsManualAttention及全局历史统计维持专家整体语义。
- 06提供expertScope及filterCounts，08只绑定现有popover第一项。filterCounts的可写单位是专家、待审单位是草稿；⋯复用pendingBadge.total单位是邮件。不复用mc-filter-count原“筛选项数”。
- 07复用右侧现有发送/附件/管理/排期UI，只有一个富文本。READ标签可见但操作缺席；FULL服务端准入同样必须通过，即使前端旧页面还显示按钮。
- 当前只读用户名/认证响应中的mailReadOnly用于识别受限账号壳，保持兼容；不把它改false来“让user1进入admin”。具体是否可写取当前专家accessLevel。

## 变更文件清单
本总计划仅协调，不可用它一次性越过子计划白名单。总计划自身列出的规划文档如下；业务代码的穷尽名单见各子计划表。

| # | 文档 | 用途 |
|---|---|---|
| 1 | `docs/plans/2026-10-29/expert-access-01-read-labels.md` | 只读标签展示 |
| 2 | `docs/plans/2026-10-29/expert-access-02-access-level.md` | 授权等级与能力投影 |
| 3 | `docs/plans/2026-10-29/expert-access-03-resource-guard.md` | 精确资源守卫与人工发送 |
| 4a | `docs/plans/2026-10-29/expert-access-04a-profile-tags.md` | 专家资料与标签目标守卫 |
| 4b | `docs/plans/2026-10-29/expert-access-04b-calendar-drafts.md` | 日历、个人草稿与审核权限联动 |
| 5 | `docs/plans/2026-10-29/expert-access-05-pending-owner.md` | 来信归属写入与撤权兜底 |
| 6 | `docs/plans/2026-10-29/expert-access-06-pending-query.md` | 个人待处理查询、计数与筛选 |
| 7 | `docs/plans/2026-10-29/expert-access-07-user-workspace.md` | 用户可写会话复用与能力隔离 |
| 8 | `docs/plans/2026-10-29/expert-access-08-admin-controls.md` | 管理端最终下拉筛选与待处理角标 |

共9个执行子计划，业务/测试/迁移/静态资源合计 **46 个不同路径**（共享文件按阶段重复列出），既有审核计划范围另计。唯一业务增量字段2个、无新权限业务表。完整去重清单见[执行文件索引](expert-access-evidence/implementation-files.json)。没有列出的文件不能顺手改；若真实测试证明需要额外文件，先调整对应子计划而不是偷偷扩大范围。

## 验收标准
- I-1：真实MySQL升级旧grant，断言均READ；并发重复PUT仅一行；旧PUT不降FULL；非法等级400且无写入；非admin管理403。
- I-2：真实AuthWebConfig+MVC逐个运行路由矩阵：NONE/READ/FULL/跨专家/admin；403之前SMTP、ES和文件流读取调用为0；未列端点仍403。
- I-3：同一专家含ES标签和QA/CUSTOM邮件标签；断言READ可见且不存在编辑/删除节点，直接POST/DELETE均403；空/null/恶意HTML分别验证。
- I-4：用A的路径搭配B的processing/tag/attachment/schedule/draft/expertId；均无副作用。三种附件owner各测一次，保留原件路径和快照校验。
- I-5：旧库upgrade、两个新建sink、UID重复、无contact、并发grant/新建，断言只产生确定的admin或user1归属；同物理邮件重复不改owner。
- I-6：FULL→READ/NONE与收信并发，未完成user1行回admin；完成行重开时失权回admin；升FULL不转历史admin行。事务失败完整回滚。
- I-7：真实SQL验证count/page/explain同口径；两人混合来信、禁用真实邮箱、SIMULATOR、挂起、跨页；admin/user1角标分别等于各自未挂起邮件数，专家数另算。
- I-8：SMTP mock证明FULL一次发送、READ/NONE/换专家0次；重试/DEDUP_SENT/DELIVERY_UNKNOWN维持原语义；user1审计不是body伪造的admin。
- I-9：真浏览器验收七原字段+首项、仅1个Apply，AND组合/Reset/退出重进；select成功/失败/迟到响应；图标邮件计数不变成筛选数。
- I-10：延迟A请求后切B/降级/注销；晚回包不挂载可写框、不回填正文。首次mount/刷新/focus/visible均不请求admin全局API。姓名UI无邮箱字段。
- I-11：审核四态/评论/历史/退回/撤销与旧方案回归；升级FULL不能改APPROVED；新人工稿可发送；审核通过与确认对话更新均不自动SMTP。
- I-12：并发撤权与四个窄UPDATE/重开；owner不被陈旧copy覆盖，其他字段原语义保留；另一处理人仍有pending时needsManualAttention不得清零。
- I-13：管理员全路径回归；user1无法打开其他tab及全局端点。定时收信与自动标签仍执行，READ附件响应仍空且直链403。
- I-14：逐项核对文件清单/迁移号/SHA/测试exit及skip；不改变application.yml、部署脚本与现有未提交改动。
- S-1：逐字CSS/DOM、复用规则无diff，真实浏览器桌面1440px/手机390px检查；与对应子计划人工UI项一起验。
- S-2：逐字CSS/DOM、复用规则无diff，真实浏览器桌面1440px/手机390px检查；与对应子计划人工UI项一起验。
- S-3：逐字CSS/DOM、复用规则无diff，真实浏览器桌面1440px/手机390px检查；与对应子计划人工UI项一起验。
- S-4：逐字CSS/DOM、复用规则无diff，真实浏览器桌面1440px/手机390px检查；与对应子计划人工UI项一起验。
- S-5：逐字CSS/DOM、复用规则无diff，真实浏览器桌面1440px/手机390px检查；与对应子计划人工UI项一起验。

最终执行：JDK11 `mvn test`；`node --test src/test/js/*.test.js`；隔离MySQL的授权/归属/会话/挂起/审核IT（显式`-DmysqlIt=true`）；`FlywayMigrationIntegrationTest -DmigrationIt=true`。本轮仅产出计划，未运行这些实现验收，不能写测试已通过。

范围自检：原规划9步均≤10文件；授权实施以I-14明确列出的03/06有界例外为准，其他步骤上限不变。02和05各只改一张共享store的一列；frontend步骤具备逐字样式与DOM；X1–X12分别映射A-1～A-15。原脏文件见baseline.txt，不能覆盖；无部署/配置修改授权。

## 人工验收清单
### A-1: READ标签可见且不可写
- 前置条件: 测试库有授权READ专家甲，ES标签“新发现”“瑞典”，一封来信带QA标签“薪资”和CUSTOM标签“补充材料”。
- 操作步骤:
  1. user1登录，选甲。
  2. 查看列表/头部与来信。
  3. 用同会话请求新增/删除专家标签和邮件标签。
- 预期结果: 上述四个标签出现在对应区域；无新增/×控件；两类写接口403，admin刷新标签未变；无附件下载按钮。
- 覆盖: I-2/I-3/I-13/S-1/X2/X8

### A-2: 等级切换与旧授权兼容
- 前置条件: 升级前已有甲READ授权；管理员与user1各开一浏览器。
- 操作步骤:
  1. admin选甲的权限select为可写。
  2. 刷新user1。
  3. 发送一次旧无body PUT，再刷新。
  4. 非admin请求授权接口。
- 预期结果: 旧授权初始READ；设置后FULL；旧PUT后仍FULL；user1改授权403。加载中select禁用，保存失败恢复上一个确认值。
- 覆盖: I-1/I-2/I-9/S-2/X1

### A-3: 新旧待处理分开
- 前置条件: 甲有2封旧MANUAL_REVIEW归admin；乙有1封归admin。用测试IMAP/测试入口导入，不向真实专家发送。
- 操作步骤:
  1. admin授予甲FULL。
  2. 给甲新增3封测试来信并重复补收其中1封。
  3. 两账号看待处理与全部。
- 预期结果: admin待处理仍3封；user1为3封；admin All可看甲全部5封；重复补收不增行/不改归属；待处理专家数admin=2、user1=1。
- 覆盖: I-5/I-7/I-12/X3

### A-4: 降级、撤权和撤销完成
- 前置条件: 甲FULL且有2封归user1的未处理、1封已完成；留一封历史admin待处理。
- 操作步骤:
  1. admin把甲降READ。
  2. 刷新两端；admin撤销那封已完成邮件的完成状态。
  3. 再升FULL。
- 预期结果: 降级后user1待处理0，admin新增2；重开再增1；旧admin待办始终保留；再升FULL不把已有admin待办迁给user1。READ仍能读甲邮件和标签。
- 覆盖: I-6/I-7/I-12/X4/X5

### A-5: 同专家直接发送及越权拒绝
- 前置条件: 甲FULL、乙READ、丙未授权；测试SMTP收集器启用；准备各自附件/排期/来信id。
- 操作步骤:
  1. user1对甲写人工回复并显式发送。
  2. 把请求中的来信/附件/锚点/排期id分别换成乙或丙。
  3. 对乙尝试发送。
- 预期结果: 甲收到一次测试发送记录；审计user1；越权请求403或既有资源不匹配错误，无新增SMTP/文件内容；乙没有发送能力。
- 覆盖: I-2/I-4/I-8/X6/X7

### A-6: 三种附件归属与材料操作
- 前置条件: 甲FULL，准备record-owner/processing-owner/manual-upload-owner各一附件；丙未授权，准备其附件。
- 操作步骤:
  1. user1从甲材料drawer预览/下载三类文件，上传一个测试附件。
  2. 尝试丙文件直链、把甲path搭配丙attachmentId。
  3. 降READ后刷新并重试甲直链。
- 预期结果: FULL甲三类原件可读；丙和错配不返回文件字节；降READ后甲直链403、附件投影为空；上传本身不产生SMTP。
- 覆盖: I-4/I-8/I-13/X6

### A-7: 资料、标签、排期管理
- 前置条件: 甲FULL且有currentIndexLevel和有效ORCID；丙未授权；甲人工档案version已记录。
- 操作步骤:
  1. user1修改甲人工资料与标签，新增/改期/取消一个测试排期。
  2. admin回读。
  3. 用旧version保存，换丙expertId或scheduleId请求。
- 预期结果: admin看到同一甲资料/标签/排期；旧version409；跨专家无修改；普通保存/改期/取消不发送邮件；user1仍只有收发件箱tab。
- 覆盖: I-2/I-3/I-4/I-13/X8/X9

### A-8: 原更多筛选完整保留
- 前置条件: 有不同账号、标签、日期和方向的测试邮件，甲FULL；至少一份SUBMITTED审核草稿。
- 操作步骤:
  1. admin点搜索右侧⋯。
  2. 核对专家筛选位于最上。
  3. 选择user1可写并叠加账号/方向/标签/邮箱/关键词/日期。
  4. 应用、Reset、切tab再返回。
- 预期结果: 原七字段均存在；Apply只有一个；筛选AND并回第1页；Reset恢复全部专家范围并保留原tab/q语义；刷新旁无新下拉。
- 覆盖: I-9/S-2/S-3/X11

### A-9: 角标不是筛选数或草稿数
- 前置条件: admin有3封未挂起待处理邮件、2位待处理专家、5份待审草稿；user1有1封待处理。
- 操作步骤:
  1. admin看⋯角标，user1看待处理入口。
  2. admin设置两个普通筛选，切页。
  3. admin处理完3封后刷新。
- 预期结果: admin图标3；user1待处理显示1位专家；admin待处理tab2、待审选项5条；设置两个筛选后图标仍3；清零后角标隐藏。
- 覆盖: I-7/I-9/S-3/X3/X11

### A-10: 待审核筛选与同框审核
- 前置条件: 已有草稿审核计划落地；甲有2份SUBMITTED、乙1份；admin有未发个人草稿。
- 操作步骤:
  1. admin在更多筛选选待审核并应用。
  2. 选甲、选择一份用户草稿，翻译并批准。
  3. 导入一封甲新来信，再点击发送。
- 预期结果: 左侧甲/乙各标2/1条；右侧对应专家，只有一个编辑框；批准不发送；对话更新先确认且仍需手动发送；原admin个人草稿不被覆盖。
- 覆盖: I-9/I-11/S-3/X12

### A-11: FULL升级不解锁旧审核稿
- 前置条件: 甲READ，有UNSUBMITTED/SUBMITTED/RETURNED/APPROVED审核稿各一份。
- 操作步骤:
  1. admin升级甲FULL。
  2. user1打开旧APPROVED稿尝试改删。
  3. 新建人工稿并在测试SMTP显式发送。
- 预期结果: APPROVED仍不可改删；SUBMITTED仍待审，不自动发送；FULL新人工稿可以直接发送；降READ后旧人工稿不可再通过发送API绕过审核。
- 覆盖: I-8/I-11/X7/X12

### A-12: 晚响应与撤权清理
- 前置条件: 甲FULL、乙READ；浏览器网络工具延迟甲消息/权限/保存回包。
- 操作步骤:
  1. user1打开甲后立即切乙。
  2. admin撤销甲授权，释放延迟响应。
  3. user1刷新、切后台再回来、注销重登。
- 预期结果: 乙不出现甲正文/附件/发送按钮；甲在重新校验后消失；注销后无正文；不请求邮箱配置/任务列表；无恢复旧权限。
- 覆盖: I-2/I-10/S-4/X10

### A-13: 管理员与自动流程不回归
- 前置条件: admin现有人工草稿/挂起/筛选与测试自动收信可用；准备禁用真实邮箱和SIMULATOR。
- 操作步骤:
  1. admin按原流程保存并发测试草稿、查看附件、筛选挂起。
  2. 执行测试收信、自动标签、UID补收。
  3. 查看全局来信汇总及个人待处理。
- 预期结果: 旧人工草稿/幂等/附件保持；自动流程不因无user1 session失败；禁用真实邮箱邮件仍可见、SIMULATOR不计；全局汇总保留所有人，个人待处理分归属。
- 覆盖: I-5/I-7/I-8/I-12/I-13/X3/X4/X7

### A-14: UI尺寸及空错状态
- 前置条件: 桌面1440px和手机390px；准备长姓名、长标签、无标签和模拟接口失败。
- 操作步骤:
  1. 对照本计划S-1–S-4观察。
  2. 打开popover，切权限，模拟失败与无标签。
- 预期结果: popover桌面430px、手机不溢出；首项select34px；header权限select最大180px；角标18px；无大权限卡；null标签显示“标签暂不可用”，[]不显示；失败不伪造授权成功。
- 覆盖: I-3/I-9/I-10/S-1/S-2/S-3/S-4

### A-15: 并发归属不被旧保存覆盖
- 前置条件: 测试库准备甲FULL、user1待处理及可重开的完成行；用测试屏障延迟状态更新事务。
- 操作步骤:
  1. 并发执行甲降级与标完成/绑定/重开/自动响应重分类。
  2. 查看两账号待处理，重试失败事务。
- 预期结果: 无owner被陈旧save恢复user1；每封尚未完成且已失权邮件都能在admin待处理找到；另一人仍有pending时专家needsManualAttention保持true。
- 覆盖: I-5/I-6/I-12/X4/X5

A-1覆盖最新“可读看标签不可改”；A-2/5/6/7覆盖FULL；A-3/4/9/15覆盖个人待处理；A-8/14覆盖最终UI；A-10/11覆盖审核交接；A-12/13覆盖权限生命周期和原业务回归。进入人工验收时再导出expert-access-master-acceptance.md；本轮不生成衍生清单。

## 修正记录

- 2026-10-30：用户“授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p”解除前置基线/范围修订阻塞。另一工作树draft-review 05仍在途，故本轮从已验证01–04的确定提交链`59eb60cf00534a342c1019827a62f2b7b1db0f2b`建立唯一分支`fast/expert-access-master`，不改写另一分支或复制其未提交代码；先在本轮独立实施并验证review05，再01→02→03→04a→04b→05→06→07→08。review05的依赖为none（01–04已在基线），01依赖review05，后续仍各依赖上一项。
- review05仅复用其现有已提交13文件白名单及原机器门禁，继承此前有证据的13文件/3子系统有界例外；本轮九个专家权限子计划的文件上限、不变量、权限与真实MySQL门禁不变。前置审核store/V169已随基线01落地，不新增第二套实现。
- 按本总计划I-9/I-11及§顺序、依赖与发布边界的最终UI裁决，review05不创建顶部mailboxReviewPendingBtn，暂用API/内部setReviewPendingOnly覆盖真实筛选；最终入口只由08的原popover提供。审核正文、单编辑框、评论、翻译、确认和显式发送均不减免。
- 初始规划快照传输曾截断master末段，已在产品工作前以完整源字节恢复；恢复SHA256为`9143669bb109f81699ef7d4fa553ac4a19217347778c47c6b6da8625f1e0deb9`，所有原人工验收条目保留。恢复及本次基线/前置变更均在fast-p Amendments逐项留痕。
- 全部子计划与最终artifact validator通过后，自动执行独立review-fast-p；机器审核不代替人工验收，不授权合并、推送或部署。
- 2026-10-30：I-1真实等级投影需要MailboxConversationService注入非空等级服务；完整constructor grep收据在fast-p children/02/execution.md。按持续授权，02追加OutboundAttachmentFlowTest.kt、MailReviewQueryIT.kt两个直接构造fixture文件，仅接续新依赖/真实等级fixture与原行为回归，7→9文件，仍≤10/2子系统，不使用nullable/default兼容shim、不扩大权限与产品行为；对应子计划修正及fast-p Amendments留痕。
- 2026-10-30：02实际门禁发现01继承的mailboxReadOnly.test.js固定缓存键与现有taskActivityCenter全局guard冲突（完整证据见children/02/execution.md Epoch2），按持续授权将该测试加入02第10文件；仅退役固定版本字面量，保留动态同键/实际module契约，guard/index与前端产品不动。原整命令失败如实保留，最终门禁必须通过；不消耗light-verifier修复轮次（仍是实现阶段，尚未首次提交/验证）。
- 2026-10-30：03完整构造grep显示非空安全依赖需要14个原名单外fixture（收据见children/03/execution.md Epoch1）；持续授权下I-14为03的8→22仅构造接线授予例外，不改原产品范围/断言。04b原第3项已负责unmatched候选隐藏，故03暂拒getUnmatchedDetail而不提前扩产品controller。个人pendingBadge/挂起原SQL未按owner，03暂拒，06以既定owner SQL后精确激活；06增加policy/guard两个文件，10→12有界例外，真实AuthWebConfig回归。材料已有全成员同contact校验沿用且测越权混入。最终能力/人工验收均不删减，原例外请求23由已有04b职责消解为22。
- 2026-10-30：03在产品变更前完成Spring全消费者核查，另19个必需context fixture（17个WebMvcTest/2个无扫描manual context）缺新增guard，完整grep与最小import/注册证明见children/03/execution.md Epoch2；持续授权下例外22→41，原5个产品文件不扩大。仅退役02 ExpertMailReadAccessIT临时FULL follow/rich-send全局拒绝pin及被精确资源守卫取代的旧全局文字，不削弱NONE/READ/跨资源/admin/撤权/拒绝副作用断言；每个新增fixture执行原门禁、不得以skip冒充通过。前次22身份保留，epoch3按新身份接续，产品尚未变更。
