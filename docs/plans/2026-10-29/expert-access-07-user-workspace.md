# 专家分级权限 · 用户可写会话复用与能力隔离

状态：DRAFT，待批准实施。基线：`main@b15bb069f43f89c45ee6859dba04a0baff5d774d`。仅计划，不代表实现/测试已通过。

证据：[审计总表](expert-access-evidence/audit.md)、[逐字源码E01–E37](expert-access-evidence/source-excerpts.md)、[schema迁移链](expert-access-evidence/schema-chain.md)、[检索收据](expert-access-evidence/store-usage.txt)、[SHA256](expert-access-evidence/sha256.json)。

前置：06及既有draft-review-04-reader；旧审核用户模块接入仍保持本计划APPROVED锁。本文件是可执行边界，先读[总计划](expert-access-master.md)的协议/最终UI与依赖修正。

## 需求描述
在user1同一个收发件箱中，READ看邮件标签并提交审核稿；FULL复用现有富文本、附件、资料、标签与排期操作。

必须保持：user1只邮箱tab/姓名、READ附件禁止、审核状态锁、管理员原工作台/人工编辑器/表单不变。

不做：不复制MailboxChat整套实现，不给user1挂载全局专家列表、AI工作台、模板配置与批量发送；不重写既有表单。

## 关键不变量
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

### Invariant I-8: FULL发送复用现有事务链
- Rule: FULL可直接人工发送，无需草稿审核；仍复用两个现有rich-reply入口及attempt幂等、附件/锚点归属、安全确认。鉴权位于业务副作用前，服务入口用authenticatedUsername再次校验；请求体operatorName不代表身份，user1审计写session名。已获准开始的SMTP请求不承诺被后续撤权召回；撤权后的新请求403。
- Applies to: PendingMailOperationService两个入口、旧草稿绑定、下载
- Violation consequence: 重复发送或绕过安全/资源校验
- 来源: K-manual-send-success-transaction-and-dedup

### Invariant I-7: 计数按处理人、数量单位明确
- Rule: 我的待处理=MANUAL_REVIEW且pending_owner_username=session，排除SIMULATOR_NOOP/不存在账号，保留禁用真实邮箱；user1再受可读/FULL准入约束。图标角标复用当前账号pendingBadge.total（邮件条数，含既有挂起排除），不受列表搜索/分页影响；0隐藏，失败显示不可用而非0。待处理tab数字仍为专家数，待审核是SUBMITTED草稿数。count/page/explain共用条件，All保留历史邮件。监控、来信汇总不变。
- Applies to: 会话聚合/排序/分页、挂起、旧队列、顶栏及更多图标
- Violation consequence: 列表与角标不一致或user1邮件仍进admin待处理
- 来源: K-mailbox-inbound-source-authority

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

### Invariant I-13: 不扩大成全局管理
- Rule: FULL范围为当前专家人工邮件/附件资料/人工档案/标签/备注位置/排期/现有单专家状态管理；全局邮箱账号配置、批量发送、任务/AI训练、模板配置、全局专家搜索、人工接管分派、会议视频库不开放。自动收发/晋级/标签流程不受user1交互授权守卫影响。READ附件仍不可查看下载，邮件正文邮箱不做脱敏承诺。
- Applies to: 精确路由表、用户宿主菜单、后台任务
- Violation consequence: 无关功能扩权或后台任务中断
- 来源: original

## 样式契约
### S-1：只读标签
- 复用：`styles.css:3828–3861`的inbound-tag-chip/qa/custom/inactive，`:4766–4800`的expert-tag；`mailbox-chat.css:120–121`的mc-tag-row和标签尺寸；完整原文见E21–E23。复用class，不改任何既有规则，无新CSS。
- DOM（每个label用textContent；列表和头部相同；消息行只用于INBOUND_PROCESSING）：
```html
<div class="mc-tag-row" data-role="readonly-expert-tags"><span class="expert-tag" title="完整标签">标签</span></div>
<div class="mc-tag-row" data-role="readonly-mail-tags"><span class="inbound-tag-chip qa">邮件标签</span></div>
```
- 空数组不生成行，null在相同区域用`<p class="mc-note">标签暂不可用</p>`；未知标签显示原值。无x、input、button、data-action写入口。禁止inline style、未声明新class；不得调用共享可编辑tag renderer再用CSS隐藏删除按钮。
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
E17/E24–E27/E31/E33/E34及audit§5：独立模块是已实现事实；完整MailboxChat会请求contact/note/suspension/timing，不能直接mount后靠403兜底。资料原adapter会跳专家列表；contact-details会重组全页并mount ContactHistory，应显式跳过。当前排期复用meeting-calendar表单，资料使用现有四组字段，不新增store。X1/X6/X7/X9/X10/X12。

全量读写清单是本计划的组成部分：[审计§1–§5](expert-access-evidence/audit.md)。其中每张表列明schema、显式写者、FK/后台写入、读取与X交互点；未列为本步变更的writer保持原逻辑，不能因grant扩权改变后台任务。

### 前端样式盘点
逐字DOM/CSS基线见E17–E27/E33/E34；复用class、实值token与目标DOM在上节S条款中逐项固定。本步不修改任何既有共享CSS规则，不需要扩改其其他使用点；新增规则仅作用声明class。新增/修改DOM均须归属于本步S条款。

## 实现方案
1. MailboxReadOnly保持列表控制和只读渲染，message响应的本次accessLevel为准；FULL在现有右侧host调用MailboxChat新增受限模式，READ沿既有审核子模块（先卸载FULL），NONE/403清正文。列表标签沿S-1。FULL上下文不得从前端选项推断授权（I-2/I-3/I-10）。
2. MailboxChat新增一个明确mode分支、固定contactId，S-4只渲染会话右栏；跳过列表加载/自动tab探测/高级筛选节点迁移/标签全局统计。保留发送、附件、管理、排期、备注/位置操作；只挂已授权当前专家对应的宿主。hide AI工作台/跟进群发/模板配置/全局默认设置入口，且不发后台预加载请求。原admin默认模式分支零语义变化（I-10/I-13）。
3. app.isMailReadOnlyRequestAllowed按精确method+path扩展到准入表，body/query目标继续后端验证；不得放行泛/api/expert或/api/mail。保留user1启动提前return和邮箱壳。富文本发送走原submitManualRichReply安全确认；FULL草稿save/flush仅刷本用户当前FULL目标，logout先可选保存当前有效人工稿再销毁，禁止调用admin全局flush（I-2/I-8/I-10）。
4. app增加限定资料宿主，使用S-4/E33既有四组人工资料template/session；给session传delegated，shell不生成视频模块、ContactDetails包装器不reorganizeContactPanel。资料身份由选中contact解析；user1名字展示不输出email。排期先ensureMeetingCalendarBound后固定contactId打开原form，不能加载全局contact下拉；材料沿现有ExpertMaterials.drawer（I-4/I-10/I-13）。
5. FULL→READ/切专家/logout：卸载当前compose、材料drawer、资料session、排期dialog及所有portal，清权限/附件/正文内存并使回调epoch失效。focus/visible刷新先取权限再恢复控件，不用旧缓存显示发送按钮；403原文案并停止写（I-10）。
6. 审核模块只在READ/审核历史上下文工作；FULL新人工稿绝不复用APPROVED正文对象；调用时不自动发送、不把SUBMITTED改状态。old reviewer新增字段按前置计划复核，保持I-11。
7. 四个JS测试按上述真实动作验证，全部现有JS回归；index缓存键统一，CSS仅S-4给定新增规则（I-3/I-10/I-11、S-1/S-4）。
8. MailboxReadOnly增加S-5个人待处理入口，复用conversation pendingOnly查询及total返回专家数；卡片pendingCount显示邮件条数；首次和每次动作后刷新，与ALL各自保留搜索但切换重置page。此数字不代替admin⋯邮件角标（I-7/S-5）。

仅以下文件可变更；每个任务同时受其引用的不变量/样式契约约束。共享文件按本步前置版本接续，不覆盖上一阶段改动。

## 变更文件清单

| # | 路径 | 工作 |
|---|---|---|
| 1 | `src/main/resources/static/mailbox-readonly.js` | READ与FULL右栏切换、角标和卸载 |
| 2 | `src/main/resources/static/mailbox-chat.js` | 受限conversation-only模式及能力门控 |
| 3 | `src/main/resources/static/app.js` | 限定请求/资料/材料/会议/发送宿主与logout |
| 4 | `src/main/resources/static/contact-details.js` | delegated资料session跳过整页重组/历史模块 |
| 5 | `src/main/resources/static/mailbox-chat.css` | S-4外壳规则 |
| 6 | `src/main/resources/static/index.html` | 统一缓存键，不新增用户导航 |
| 7 | `src/test/js/mailboxReadOnly.test.js` | READ回归与权限切换 |
| 8 | `src/test/js/mailboxChatBehavior.test.js` | 受限模式/发送/取消挂载 |
| 9 | `src/test/js/expertManualProfile.test.js` | delegated资料DOM与禁止旁路 |
| 10 | `src/test/js/expertAccessWorkspace.test.js` | 新增跨两模块真实请求白名单与生命周期 |

合计 **10 文件**；最多2个紧耦合子系统。新增共享store字段：0。

## 验收标准
- I-2：真实AuthWebConfig+MVC逐个运行路由矩阵：NONE/READ/FULL/跨专家/admin；403之前SMTP、ES和文件流读取调用为0；未列端点仍403。
- I-3：同一专家含ES标签和QA/CUSTOM邮件标签；断言READ可见且不存在编辑/删除节点，直接POST/DELETE均403；空/null/恶意HTML分别验证。
- I-4：用A的路径搭配B的processing/tag/attachment/schedule/draft/expertId；均无副作用。三种附件owner各测一次，保留原件路径和快照校验。
- I-8：SMTP mock证明FULL一次发送、READ/NONE/换专家0次；重试/DEDUP_SENT/DELIVERY_UNKNOWN维持原语义；user1审计不是body伪造的admin。
- I-7：真实SQL验证count/page/explain同口径；两人混合来信、禁用真实邮箱、SIMULATOR、挂起、跨页；admin/user1角标分别等于各自未挂起邮件数，专家数另算。
- I-10：延迟A请求后切B/降级/注销；晚回包不挂载可写框、不回填正文。首次mount/刷新/focus/visible均不请求admin全局API。姓名UI无邮箱字段。
- I-11：审核四态/评论/历史/退回/撤销与旧方案回归；升级FULL不能改APPROVED；新人工稿可发送；审核通过与确认对话更新均不自动SMTP。
- I-13：管理员全路径回归；user1无法打开其他tab及全局端点。定时收信与自动标签仍执行，READ附件响应仍空且直链403。
- S-1：新增CSS与上节逐字一致；实际DOM符合骨架，复用规则无diff；无新inline style/未声明class；按本步UI人工项核对桌面/手机/disabled。
- S-4：新增CSS与上节逐字一致；实际DOM符合骨架，复用规则无diff；无新inline style/未声明class；按本步UI人工项核对桌面/手机/disabled。
- S-5：新增CSS与上节逐字一致；实际DOM符合骨架，复用规则无diff；无新inline style/未声明class；按本步UI人工项核对桌面/手机/disabled。

门禁：node --test src/test/js/mailboxReadOnly.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/expertManualProfile.test.js src/test/js/expertAccessWorkspace.test.js；修改JS逐个node --check；真实浏览器双账号网络记录A-5/6/7/12/14。

测试环境使用隔离MySQL/测试SMTP与临时附件目录；不得把mysqlIt连生产库。跳过/未运行必须记NOT_RUN，不能写PASS。

## 人工验收清单
### A-1: READ标签可见且不可写
- 前置条件: 测试库有授权READ专家甲，ES标签“新发现”“瑞典”，一封来信带QA标签“薪资”和CUSTOM标签“补充材料”。
- 操作步骤:
  1. user1登录，选甲。
  2. 查看列表/头部与来信。
  3. 用同会话请求新增/删除专家标签和邮件标签。
- 预期结果: 上述四个标签出现在对应区域；无新增/×控件；两类写接口403，admin刷新标签未变；无附件下载按钮。
- 覆盖: I-2/I-3/I-13/S-1/X2/X8

### A-3: 新旧待处理分开
- 前置条件: 甲有2封旧MANUAL_REVIEW归admin；乙有1封归admin。用测试IMAP/测试入口导入，不向真实专家发送。
- 操作步骤:
  1. admin授予甲FULL。
  2. 给甲新增3封测试来信并重复补收其中1封。
  3. 两账号看待处理与全部。
- 预期结果: admin待处理仍3封；user1为3封；admin All可看甲全部5封；重复补收不增行/不改归属；待处理专家数admin=2、user1=1。
- 覆盖: I-5/I-7/I-12/X3

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

### A-9: 角标不是筛选数或草稿数
- 前置条件: admin有3封未挂起待处理邮件、2位待处理专家、5份待审草稿；user1有1封待处理。
- 操作步骤:
  1. admin看⋯角标，user1看待处理入口。
  2. admin设置两个普通筛选，切页。
  3. admin处理完3封后刷新。
- 预期结果: admin图标3；user1待处理显示1位专家；admin待处理tab2、待审选项5条；设置两个筛选后图标仍3；清零后角标隐藏。
- 覆盖: I-7/I-9/S-3/X3/X11

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

本节为权威清单；进入人工验收时才导出同名前缀-acceptance.md，本次不生成衍生表。涉及尚未到本步的UI，使用同会话API执行等价动作并在最终08再次通过UI验证。

跨阶段人工项引用的其他I/S条款，以总计划同编号定义为准；本步实现仍严格限定本文件清单。
