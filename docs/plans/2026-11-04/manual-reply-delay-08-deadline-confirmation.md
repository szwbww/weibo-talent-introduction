# 08 已打开撤回确认跨截止时间闭环

2026-11-06 补充子计划。前置为07-activation的独立轻核验与清洁制品检查点；以其最终Code head为产品基线。五个产品/测试文件，一个前端子系统。用户在总A-5与06既有到期关闭契约冲突裁决中选择「按总 A-5 补齐」；本计划仅落实该选择，不重写已完成06的计划、代码边界或证据。07与08组成尚不可分批启用的交付单元；08未通过前不标全功能完成、不启生产开关。

## 需求描述

截止前已合法打开的撤回确认，在同一可写上下文、同job/revision且当前所见仍WAITING时，不因本地倒计时归零而自行消失；明确确认后由既有服务端返回迟到409，并回读实际状态。总A-5的59秒打开、61秒后确认、409「已超过撤回期限」原标准不减少。

必须保持：新的撤回入口仍仅截止前可打开；确认不暂停时间；权限、owner、job/revision、已观察状态变化、隐藏/切换/卸载清理；重发与UNKNOWN策略；原正文/附件/审核APPROVED不可编辑；一个显式请求、无自动重投；原footer/样式、轮询、接口及缓存资源集合。

范围外：后端/数据库/worker/取消CAS修改，新权限或响应字段，新的确认弹窗，重试策略，普通发送/审核context-confirm逻辑，CSS布局重做，以及无关旧测试清理。

## 关键不变量

### Invariant I-1 — 初次打开与继续确认分离
- Rule — 初次cancel-open须当前可写、WAITING、canCancel=true且剩余时间>0；迟到或伪造旧open事件不能创建确认。只有已通过该入口建立的同owner/id/revision cancel确认可以跨截止时间继续。取消确认后不能在期限外重新打开。
- Applies to — delayActions、delayAction开启/确认、delayTick。
- Violation consequence — 新的迟到动作被误放行，或原A-5仍无法触达服务端。
- 来源 — 总A-5/O-2/I-1/I-2及2026-11-06人工裁决。

### Invariant I-2 — 权限与状态不由时间或历史能力证明
- Rule — 继续确认必须当前delayWritable、owner一致、job存在、id/revision一致且cancel仍WAITING；已有公开错误/已知无效来源不得因到期绕过。已观察到READ/NONE、accessLocked、auth失效、状态非WAITING、代次/上下文变化立即清理或阻止提交。captured确认只是用户意图，不是发送/撤回权限；真实服务端的owner/FULL/source/revision/deadline/CAS全保留。
- Rule — canCancel同时含时间和权限，false不证明仅到期。截止前能力失效仍拒绝；截止后同一已打开确认可面对新鲜WAITING/canCancel=false投影继续，但必须保留上述当前可写/错误/身份检查，不能把曾经canCancel=true当当前服务端授权。未观察到的撤权仍由既有真实403闸门拒绝，401/403按原路径卸载；不新增任何客户端权限证明或后端字段。
- Applies to — render/tick/submit共享判断，admin REVIEW及delegated FULL消费者。
- Violation consequence — 错误跨会话提交、READ展示可写能力或把状态变化误当到期例外。
- 来源 — 总I-7/I-9，K-shared-workbench-fixed-mode-host-adapter。

### Invariant I-3 — 服务端409及单次回读
- Rule — 显式确认仅POST既有cancel路由，body仍仅captured expectedRevision；busy防重复。409后清确认、按原路径GET当前job并显示公开错误/真实状态，不显示撤回成功，不推断SENT，不自动再次POST。后台轮询GET与本次动作的回读在证据中分开计数。
- Applies to — delayAction及delayRefreshJob、hostApi现有transport。
- Violation consequence — 伪造完成、重复动作或独立API409冒充真实UI闭环。
- 来源 — 总A-5/I-1/I-2/I-9。

### Invariant I-4 — 其余生命周期保持
- Rule — retry仍只按既有FAILED/canRetry；UNKNOWN、永久失败、历史缺快照不增能力。hidden、切专家/账号范围/用户、generation变化、unmount/logout、access lock仍清确认/终止旧读。保留1秒倒计时、2/5秒detail、5秒list、serverNow+performance.now及零时「等待发送」。普通稿锁和审核APPROVED正文/版本/当前context确认不改。
- Applies to — timer/refresh/cleanup、共享MailboxChat、reader FULL委托与READ展示。
- Violation consequence — 幽灵提交、旧回包污染、倒计时冒充发送或审核权限外扩。
- 来源 — 总I-6/I-7/I-9/I-10，K-mailbox-draft-cache-owner-capture。

### Invariant I-5 — 原资源与现有样式
- Rule — 无新DOM id/class/CSS/模块；既有20条带版本资源保持集合、顺序和URL形状，同步换为20261106-manual-reply-deadline；task-modal-runtime.js保持无版本。测试/产品JS不硬钉新key。真实运行必须实际提供本次静态源码，不以旧target资源冒充。
- Applies to — index.html版本替换、实际浏览器验证。
- Violation consequence — 缓存旧行为、共享UI变形或假验证。
- 来源 — K-frontend-cache-key-triad、K-dom-stub-tests-hide-dangling-refs。

## 样式契约

### S-1 — 邮件footer及原确认行逐字保留
- 复用：mailbox-chat.css:319–327的mc-delay-footer/actions/confirm/status；:329–335错误图标合同；styles.css:12905–12906、12918的mailbox-suspend-inline-secondary/primary。不改这些文件。
- DOM：article.mc-message下的p.mc-inline-error[role=alert]与footer.mc-delay-footer；footer内span.mc-delay-actions[data-delay-id]、span.mc-delay-status[data-role=delay-countdown]、span.mc-delay-confirm[role=group]，两个既有button及mc-delay-confirm-dismiss/submit动作保持。aria标签「撤回发送二次确认」/「重新发送二次确认」，文案「确认撤回这封邮件？」/「确认重新发送？确认后将等待 60 秒。」/「取消」/「确认撤回」/「确认重发」/busy「处理中…」保持。
- 实值：actions gap12px；confirm gap7px；status/confirm说明#8a9bb2、11px/1.5；focus outline2px #3b82f6/offset2px；<=760px按钮命中高度44px。共享按钮桌面30px、padding5px11px、border1px #dce5f0、radius6px、primary#3762d8、hover#2954c8、disabled opacity.5。错误图标17px/#c53e32。新增样式：无。
- 禁止：inline style、新class/overlay/dialog、修改既有literal CSS或以新的native dialog替代inline role=group；不能扩大样式/焦点重构。

### S-2 — 缓存和DOM登记不变
- index.html:11–17及2547–2559的既有7CSS+13JS同时换key；2546无版本task-modal-runtime.js及749–824邮件宿主、2425–2434共享actionDialog全部原样。
- mailboxChatStyle既有literal CSS、真实DOM id、资源20/同键断言保持；当前key的全src/test字面量搜索无命中，动态派生测试不因本次更新被改写或重钉日期。

## 现状审计

### 已批准契约冲突及真实负例
- 06计划:27–30要求到期关闭确认，其终态证据保持历史事实；总A-5:225–229要求迟到确认409，用户2026-11-06明确选择后者。本计划是后继修订，不把06原断言伪称当时错误，也不复开其已关闭F06-01。
- 07真实负例A5-resume-native-local-expiry.json：due−954ms打开，真实GET响应被延迟，due+1.1s确认已被本地timer移除、POST0；独立迟到API409并非native/UI通过。完整归档local://resume07-activation-epoch1-final-evidence.tar.gz（SHA25646581d5c44bbcd39710a1c973637d97775a05386f173d7f3a23cc3b4a3ab9d2a）。
- 完整只读审计及literalgrep回执：local://08-deadline-confirmation-native-audit.md、local://08-deadline-confirmation-assets-audit.md；来源为AuditNativeDeadlineSeam/AuditDeadlineTestsAssets，无产品编辑或测试通过声明。controller真实xd://lsp status返回No language servers configured for this project；使用grep/逐点读，不称LSP通过。

### closure-local confirmation与读写路径
- Store：mailbox-chat.js:998的delayState.confirm，非DB/localStorage/draft；owner为user|selectedContactId|conversation.accountScope|convEpoch。字段owner/id/revision/action/busy不扩张；无新增共享持久字段。
- 写者：1063 revision刷新清理；1070 dismiss；1079无效提交清理；1083 busy=true；1087成功清理；1094错误清理；1110合法开启；1223 stopDelayPolling；1241 tick失效；3919 selectExpert。完整`delayState\\.confirm\\s*=|c\\.busy\\s*=|const delayState\\s*=`实际grep输出已逐行保存在native audit，非凭印象穷举。
- 读者：delayActions1031–1044、delayAction1074–1112、delayTick1236–1243。三处独立到期阻断分别1033、1078、1240；只改一处不能实现A-5。开新动作1039/1109仍须限期内。
- 刷新：delayRefreshJob1054–1064，refreshConversationQuiet9994–10035按contact epoch/account scope/FULL校验后mergeServerIntoWindow并重画；serverNow单独变化通常不重画，canCancel/status变化可能重画。stop/hidden/unmount/access lock/selectExpert及间接convEpoch清理全保留。
- 交互点：普通DRAFT/LEGACY、admin REVIEW均使用5459 common footer及10120事件分发；reader FULL由mailbox-readonly1127–1151委托同MailboxChat，READ1013–1039无动作。app/readonly行为不需修改。review context native dialog6330–6379不是本次inline cancel，禁止混改。

### 后端与transport只读边界
- POST /api/mail/mailbox/conversations/<contactId>/delayed-replies/<jobId>/cancel，body为expectedRevision；随后GET同job。app21259–21322 numeric method/path allowlist不动。
- DelayedManualReplyService473–474和MailboxConversationService471–472将deadline包含进canCancel；controller136又按canAct限制。因此须覆盖新鲜到期WAITING/canCancel=false，不可只测试永真fixture。
- DelayedManualReplyController106–117在owned/action/FULL及revision解析后，以DB currentTimeMs>=dueAt返回409 DELAYED_REPLY_TOO_LATE/「已超过撤回期限」，包括worker已领取的迟到点击；WAITING实际取消仍过lifecycle事务CAS。后端无修改授权。

### 前端样式盘点与资源回执
- 现有HTML/CSS完整逐字摘录见assets audit第4节；S-1/S-2冻结上述DOM及实值。root token为--primary#1e40af、--primary-bright#3b82f6、--panel-bg rgba(255,255,255,.55)、--text-main#1e293b；无新增surface或token。
- 实际grep `20261105-manual-reply-source`于index输出11–17和2547–2559全部20行；全src/test原键与`20261105|manual-reply-source`（关闭ignore）均No matches found。`?v=|CACHE_KEY|cache-key|cache-bust`与`[?]v=`动态写法均查至最后页，完整列表在assets audit第1–2节。只改index既有key，不增改动态消费者。

### 测试审计
- mailboxDelayedReply41–45直接断言到期移除；按本次明确新契约拆改此断言，保留零不等于SENT；47–49旧409仅验证存在GET，不能冒充迟到/确切请求顺序证据。复用现有可控monotonic clock，不新增共享宿主约定。
- mailReviewAdmin505–525覆盖APPROVED只读/取消后revision；expertAccessWorkspace26–135运行真实reader→MailboxChat→app allowlist并覆盖FULL/READ变化。仅在这两本地fixture按需传既有clock/performance能力，保留原断言；不修改mailboxChatBehavior共享宿主。
- 采用K-master-plan-shared-file-sequential-gates：08只基于07终态执行，不和07并行修改共享树，不提前宣称联合发布。知识条目只读复用，fast-p受保护范围内不做无关知识计数/归档变更。

## 实现方案

### T1 三处统一已打开确认生命周期（I-1～I-4，S-1）
仅改mailbox-chat.js。明确区分新open admission与现有confirmation有效性；在render、timer、submit三处一致使用现有owner/job/revision/current-writable/status检查。可以在本closure内用一个小的私有predicate去重复，不新建模块/框架。已打开cancel不因归零清理；新开仍canCancel且剩余>0。处理新鲜到期canCancel=false时遵守I-2，不把false解释为权限证明，不跳过当前可写/公开错误/已知状态变化；保留显式服务端复核。retry保持原限制。

保留busy防重复、captured revision POST、失败409→现有GET/公开错误、401/403卸载、全部清理和1/2/5秒节奏。只改本次相关判断，不顺手修delayRefreshJob其它旧细节，不改任何body/draft/review/attachment路径。所有消费者走共享实现，无app/readonly修改。

### T2 行为回归（I-1～I-4，S-1）
仅改三个列明测试：59秒开启→61秒保持→显式确认一次POST及原revision→409→动作归属GET回读真实WAITING/SENDING/SENT，无成功/自动重发；覆盖stale canCancel=true与fresh同代WAITING/canCancel=false、footer/full timeline redraw和timer。没有已打开确认或dismiss后迟到不能再开。

覆盖same-revision非WAITING、代次变化、job缺失、owner/expert/account/generation变更、hidden/unmount/logout、FULL→READ/NONE/lock/401/403、busy连点和网络失败；不得以到期例外保留失效动作。普通DRAFT/LEGACY、REVIEW只读正文、delegated FULL均有直接消费回归；READ无cancel/retry、user1无REVIEW mutation权。保留已有retry/UNKNOWN/永久失败/历史失败断言。新测试只验可见行为/边界，不钉源码字符串或helper命名。

### T3 同键切换（I-5，S-2）
仅改index.html既有20个版本值为20261106-manual-reply-deadline；实际执行前按当前key复查src/test，若出现新固定值冲突先报证据，不能越授权编辑。资源集合/顺序、无版本task runtime、DOM/CSS全保持。

### T4 实际迟到确认与交接（I-1～I-5，S-1/S-2）
复用07归档的真实Spring/MySQL/受控SMTP隔离启动基础，不复用旧A5结果为新PASS；真实浏览器加载当前静态源并留响应hash。原延迟60000ms不改，真实59秒打开/61秒后明确确认；可仅延迟真实poll GET到POST之后以保持尚未观察状态，禁止伪造响应、DOM、clock或业务状态。服务端可以已claim，原controller仍返迟到409；收到最新非WAITING/revision变更必须清理，不强制制造POST。另以真实仍WAITING的适当隔离调度条件或确定性行为回归覆盖fresh过期canCancel=false。

记录真实409 body、唯一cancel POST/captured revision、随后GET/实际卡片、无撤回成功、无新的重复SMTP、正文/附件/REVIEW不受影响。普通与REVIEW/delegated模式的当前变更路径均需实际surface proof；READ/权限变化不得有新可写控件。证据后清理owned依赖、归档；在08 execution/verify记录A5补齐，并由controller以新证据更新总验收索引，不改07历史失败记录。

## 变更文件清单

|#|文件|操作|
|---|---|---|
|1|src/main/resources/static/mailbox-chat.js|三处已打开cancel有效性/期限判断，保留原其他守卫|
|2|src/main/resources/static/index.html|仅既有20引用统一cache key|
|3|src/test/js/mailboxDelayedReply.test.js|替换旧expiry关闭断言，新增真实行为边界/请求顺序回归|
|4|src/test/js/mailReviewAdmin.test.js|共享REVIEW迟到确认/只读/代次直接回归及本地clock fixture|
|5|src/test/js/expertAccessWorkspace.test.js|delegated FULL/READ/撤权隔离直接回归及本地clock fixture|

## 验收标准

- I-1：开启/取消0POST；59→61已有确认可继续，不能新开或dismiss后重开；倒计时不停且零只显示等待发送。
- I-2：同job/rev的WAITING可继续，fresh canCancel=false与stale true均覆盖；实际已知权限/错误/来源/owner/status/revision失效均不能被到期绕过，server403仍权威。
- I-3：显式点击恰好一POST，原revision，409后动作回读无自动POST，显示真实状态/公开期限错误，无取消成功；与后台poll区分计数。
- I-4：三消费者及READ负例、busy/网络/切换/隐藏/卸载/权限/迟到回包、retry/UNKNOWN完整回归；稿/附件/审核内容不变，1/2/5秒和performance clock不变。
- I-5/S-1/S-2：CSS literal原断言通过；真实1440/390footer无新增overlay/inline样式，手机44px/无横溢；20资源集合/顺序保持、同新key、无JS硬钉key，真实网络源为本次文件。
- 必跑，全部编辑结束后一次执行并记真实exit/数量/skips：
  1. `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailReviewAdmin.test.js src/test/js/expertAccessWorkspace.test.js src/test/js/mailboxReadOnly.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxServerDrafts.test.js`
  2. `node --test src/test/js/*.test.js`
  3. `node --check src/main/resources/static/mailbox-chat.js`
  4. 真实浏览器A-1～A-4；源码/DOM stub测试不替代实际表面与服务端响应。采用正常资源构建/既有file静态配置提供当前源，记录实际方法；不要求无关全Kotlin套件或重跑已知旧失败。
- 本阶段只有五文件子计划轻核验，不执行review-fast-p/全计划review，不宣称总体PASS。总master完成仍需所有子计划和最终制品validator。

## 人工验收清单

### A-1 总A-5原验收闭环
- 前置条件：07隔离完整应用/真实MySQL/受控SMTP及启用开关，一封真实WAITING；浏览器真实GET可延迟，不改响应或时间。
- 操作步骤：59秒实际点击撤回；超过61秒实际确认；释放真实GET并观察回读。
- 预期结果：单个cancel POST返回409「已超过撤回期限」，原revision；真实WAITING/SENDING/SENT显示，无撤回成功/自动再次POST/重复SMTP。若先观察到状态/代次变化则正确清理，不能用该不同分支冒充本项通过。
- 覆盖：I-1/I-2/I-3/I-4，总A-5/I-1/I-2/I-9。

### A-2 开新动作与身份隔离
- 前置条件：到期未开确认、到期已开确认各一封；另一专家/账号/真实FULL与READ会话。
- 操作步骤：到期后尝试新开；对已开确认取消后再试；切专家/账号/隐藏/退出；真实降权并刷新/直接请求。
- 预期结果：新开/重开0POST，旧上下文0POST/旧回包不改新视图；READ无操作，真实未观察撤权的请求被服务端403且卸载，不出现本地成功。
- 覆盖：I-1/I-2/I-4。

### A-3 REVIEW与delegated FULL共享路径
- 前置条件：admin APPROVED稿排队、user1 FULL普通稿排队、独立READ访问；真实接口与可延迟GET。
- 操作步骤：分别实际操作已开确认跨期；观察REVIEW正文/版本与普通稿附件；READ查看并尝试越权请求。
- 预期结果：同一共享footer/既有cancel路由和期限409；APPROVED不改正文/版本，普通稿/附件不丢；READ无可执行动作，user1不能审核撤回/重发。
- 覆盖：I-2/I-3/I-4/S-1。

### A-4 样式、轮询与退化保护
- 前置条件：1440与390视口，WAITING/FAILED/UNKNOWN，浏览器network及真实静态源。
- 操作步骤：观察footer与倒计时；键盘聚焦确认/错误；dismiss/连点；隐藏/恢复/卸载；核对新资源版本。
- 预期结果：原DOM/颜色/17px图标与手机44px目标无横溢，无新大卡/弹窗；1/2/5秒节奏不变，busy单POST，UNKNOWN/永久失败/历史失败无新增重发；20资源集合/顺序与同键保持。
- 覆盖：I-4/I-5/S-1/S-2。

## 修正记录

- A31/A32（2026-11-06用户选择「按总 A-5 补齐」）：总A-5优先于历史06到期关闭的相冲突部分；只给截止前已开、上下文仍合法的cancel确认窄例外。07保留其余完整集成/实际运行门禁，把A5的frontend修订与native409收口明确移交本后置子计划，原master十二项不删减。06终态计划/证据不改，07/08顺序独立核验；所有子计划通过及最终制品检查前不READY，不启生产，不执行review-fast-p。
