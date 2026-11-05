# 06 前端倒计时、异常图标与二次确认

前置05通过。前端工作台与窄恢复接口契约两个子系统，20个文件（A15/A16特批11＋A17–A22既有测试兼容9）。以下CSS为最终合同，不是示意。未开启后端入队时兼容原SENT响应。

## 需求描述

普通与已审核人工回复显示服务端倒计时，邮件底部「翻译」操作行提供撤回/重新发送，均需同位置二次确认。列表与邮件用红色感叹号表示异常，悬停/聚焦给出原因。保留原编辑器、翻译、待处理标记、账号筛选、只读权限及390px移动布局；不改全局主题、不用前端定时器代发邮件。

## 关键不变量

### Invariant I-6 — 草稿保留与恢复
- Rule — WAITING/SENDING不可编辑/放弃，原稿不清；SENT只有owner/target/seq匹配才清当前稿。取消回读普通草稿，审核稿仍APPROVED且只读；LEGACY通过原编辑器恢复完整输入，遇到新内容必须保留新内容。
- Rule (A16) — 刷新后的LEGACY恢复必须使用服务端restore-only返回并校验的原inboundProcessingId选择编辑器；禁止从job.id、正文、Message-ID或最新来信猜测。公共只读任务投影保持原字段，不增加恢复身份或能力。
- Applies to — compose、server draft map、legacy host、review panel。
- Violation consequence — 自动保存覆盖或旧回包清除新稿。
- 来源 — K-mailbox-draft-cache-owner-capture。

### Invariant I-7 — 不以UI冒充授权
- Rule — 动作显示同时要求服务器canCancel/canRetry和当前写权限；READ无操作及恢复附件。API失败403触发原失权清理，不能靠旧能力继续操作。
- Applies to — reader whitelist、render、action dispatch、poll。
- Violation consequence — 只读越权或停留时泄露。
- 来源 — K-mailbox-readonly-needs-lifecycle-audit。

### Invariant I-9 — 服务器权威与确认隔离
- Rule — WAITING不是发送成功；显示remaining=max(0,ceil((dueAt-serverEstimatedNow)/1000))，0显示等待发送。确认按user/account/contact/job/revision/action绑定，第一击不POST，取消不POST，第二击才POST一次；超期或代次变化关闭确认并回读。
- Applies to — 两个submit helper、review send、timer、cancel/retry。
- Violation consequence — 假成功、重复重发、切会话误操作。
- 来源 — K-mailbox-draft-cache-owner-capture。

### Invariant I-10 — 失败图标与文案
- Rule — 列表failedCount>0显示图标，不依赖latest；邮件FAILED/UNKNOWN用图标，UNKNOWN明确待确认且禁止重发。历史FAILED无job只显示通用原因，不合成重发按钮。所有动态文案escapeText/textContent。
- Applies to — admin/reader列表、message badge、tooltip。
- Violation consequence — 异步失败无提示、XSS或未知结果重投。
- 来源 — K-manual-send-error-response-opaque。

## 样式契约

### S-1 邮件底部操作与确认

复用 `.mc-text-button`（mailbox-chat.css:109–112）、`.mailbox-suspend-inline-secondary/.mailbox-suspend-inline-primary`（styles.css:12905–12906、12918）。既有footer完整规则位置为mailbox-chat.css:48、116。只追加派生类，不修改旧规则；全体使用位置见证据05-style-sites.txt。

新增规则逐字追加到mailbox-chat.css：

```css
/* manual-reply-delay-contract:start */
.mail-chat .mc-message footer.mc-delay-footer{flex-wrap:wrap}
.mail-chat .mc-delay-actions{display:inline-flex;align-items:center;flex-wrap:wrap;gap:12px;min-width:0}
.mail-chat .mc-delay-confirm{display:inline-flex;align-items:center;flex-wrap:wrap;gap:7px;min-width:0}
.mail-chat .mc-delay-confirm>span{color:#8a9bb2;font-size:11px;line-height:1.5;overflow-wrap:anywhere}
.mail-chat .mc-delay-status{color:#8a9bb2;font-size:11px;line-height:1.5;overflow-wrap:anywhere}
.mail-chat .mc-delay-actions .mc-text-button:focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
@media(max-width:760px){.mail-chat .mc-delay-actions .mc-text-button{min-height:44px}.mail-chat .mc-delay-confirm>span{flex-basis:100%}.mail-chat .mc-delay-actions{max-width:100%}}
/* manual-reply-delay-contract:end */
```

DOM骨架（变量均转义；有job才加mc-delay-footer；没有翻译能力时仅省略翻译按钮）：

```html
<footer class="mc-delay-footer">
  <button class="mc-text-button" type="button" data-action="mc-translate" data-message-key="DELAYED_MANUAL_REPLY:ID">翻译</button>
  <span class="mc-delay-actions" data-delay-id="ID">
    <span class="mc-delay-status" data-role="delay-countdown">将在 59 秒后发送</span>
    <button class="mc-text-button" type="button" data-action="mc-delay-cancel-open" data-message-key="DELAYED_MANUAL_REPLY:ID">撤回</button>
  </span>
</footer>
```

打开确认时只替换mc-delay-actions内的动作部分，倒计时继续显示：

```html
<span class="mc-delay-confirm" role="group" aria-label="撤回发送二次确认">
  <span>确认撤回这封邮件？</span>
  <button class="mailbox-suspend-inline-secondary" type="button" data-action="mc-delay-confirm-dismiss">取消</button>
  <button class="mailbox-suspend-inline-primary" type="button" data-action="mc-delay-confirm-submit">确认撤回</button>
</span>
```

重发复用同一骨架，action为mc-delay-retry-open，文案为「确认重新发送？确认后将等待 60 秒。」「确认重发」，aria-label同步。提交中两按钮disabled，主按钮文案「处理中…」。不能添加dialog遮罩、window.confirm或额外悬浮工具条。动作失败复用卡内已有`p.mc-inline-error[role=alert]`，该元素原有样式mailbox-chat.css:122。

### S-2 异常图标

不修改`.mc-badge`、`.mc-person-heading`、`.mc-person-main`。新增逐字CSS：

```css
/* manual-reply-error-contract:start */
.mail-chat .mc-send-error{position:relative;display:inline-flex;align-items:center;justify-content:center;flex:none;width:17px;height:17px;color:#c53e32;vertical-align:middle;cursor:help}
.mail-chat .mc-send-error-icon{display:block;width:17px;height:17px;fill:none;stroke:currentColor;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round}
.mail-chat .mc-send-error-tooltip{display:none;position:absolute;right:0;top:calc(100% + 6px);z-index:20;width:max-content;max-width:min(260px,70vw);padding:7px 9px;border:1px solid #e2e8f0;border-radius:6px;background:#fff;color:#475569;box-shadow:0 2px 6px #334b7210;font-size:11px;font-weight:400;line-height:1.5;white-space:normal;text-align:left;overflow-wrap:anywhere}
.mail-chat .mc-send-error:hover .mc-send-error-tooltip,.mail-chat .mc-send-error:focus .mc-send-error-tooltip,.mail-chat .mc-person-main:focus-visible .mc-send-error-tooltip{display:block}
.mail-chat .mc-send-error:focus-visible{outline:2px solid #3b82f6;outline-offset:2px;border-radius:3px}
/* manual-reply-error-contract:end */
```

图标为固定圆形感叹号SVG，不用emoji，不给用户显示大段红字：

```html
<span class="mc-send-error" role="img" tabindex="0" aria-label="发送异常：发送暂时失败，可安全重试" aria-describedby="delay-error-ID">
  <svg class="mc-send-error-icon" viewBox="0 0 20 20" aria-hidden="true" focusable="false"><circle cx="10" cy="10" r="7.5"></circle><path d="M10 5.5v5M10 14h.01"></path></svg>
  <span class="mc-send-error-tooltip" id="delay-error-ID" role="tooltip">发送暂时失败，可安全重试</span>
</span>
```

邮件图标替换header中的FAILED文字badge。列表图标放`span.mc-person-heading`中strong后、既有待审核badge前；列表父节点是button，因此图标去掉tabindex，不嵌套button，父button的aria-label增加「存在发送异常」，aria-describedby引用唯一tooltip id。列表提示固定「有 N 封邮件发送异常，打开会话查看」；图标17px不变。两种列表均用同一SVG和转义函数，reader用createElementNS构造SVG。

### S-3 状态与恢复

WAITING/SENDING/CANCELLED的header复用`span.mc-badge`，pending采用既有data-tone=pending，取消用默认；SENT保持data-tone=success。footer状态文字统一mc-delay-status；编辑区锁定说明复用既有`.mc-note`（mailbox-chat.css:56）、稿错误用既有`.mc-inline-error`。不新增未声明class。

```html
<span class="mc-badge" data-tone="pending">等待发送</span>
<div class="mc-note" role="status">邮件已加入发送队列，可在邮件底部撤回。</div>
```

LEGACY取消卡footer允许`button.mc-text-button[data-action=mc-delay-restore]`文案「恢复草稿」，放在mc-delay-actions内。点击打开原详情编辑器；如该编辑器已有不同输入，使用现有openActionDialog确认能力，默认保留当前输入，不覆盖。确认框只换文本，不改既有DOM/CSS。普通DRAFT直接回读已保存草稿，无新恢复面板。REVIEW取消后去掉等待提示，原APPROVED面板保持不可编辑。

### S-4 布局与资源约束

保留card宽度min(92%,820px)、padding14px17px、radius11px、正文13px/1.55、footer gap12px/mt12px/pt10px与原border。新增内容全部在现有message/footer/heading内，未增独立区域。手机760px以下新动作命中高44px；确认按钮沿styles.css:12918。index只改资源版本参数为统一`20261104-manual-reply-delay`，所有现有带版本静态引用一起更新，引用数量不变。

禁止inline style、未声明class、修改既有class规则、复制近似按钮样式、运行时注入预览脚本。凡既有复用节点新增交互属性都归S-1/S-2/S-3，index归S-4。

## 现状审计

### 前端样式盘点

- 逐字改动前DOM和CSS见证据`frontend-baseline.md`，包括mailbox-chat.js:5118–5168完整footerButtons/article及mailbox-chat.css:109–116完整规则。S-1列的现有按钮base/hover/active/disabled、S-2列的heading、S-4布局原值均经源码读取。
- admin列表mailbox-chat.js:2231、reader列表mailbox-readonly.js:828分别构造DOM；后者不能只改chat而漏图标。
- 原错误badge为mailbox-chat.js:5176，原footer与status完全分离，符合用户指定底部按钮位置。
- 样式全使用回执05-style-sites.txt；派生新增类不影响原待处理二次确认。

### 状态写读与交互

- `app.js:18434`局部submit成功即清manualReplyQaContext/aiReplyState并showUnmatchedDetail；`:22038`全局submit返回boolean、非空即alert成功。其宿主调用`:21752/21761`把bool交mailbox-chat。这两处必须一起改。
- `mailbox-chat.js:9337–9556`先flush草稿→发出→回读详情，当前只阻止DELIVERY_UNKNOWN/IN_PROGRESS；owner/seq隔离不可删除。`:6100`review发送读取context与确认，`:5853`PREPARED被视为可发。
- `mailbox-chat.js:9595`静默会话刷新、`:10454`unmount，reader的loadList/background与delegated onChanged是另一生命周期。计时器必须按两宿主分别清理，不能仅选中会话时刷新列表。
- `app.js:21180–21230`reader请求白名单。仅放行04的数字ID GET状态与FULL动作路由，不能放行整个delayed-replies前缀任意方法。
- 当前index实际有20处版本引用（CSS7、JS13，Impl06Epoch2 grep `\?v=` 回执）；原计划19为计数笔误，不得删资源凑数。08-current-cache-key.txt是原计划历史审计，当前执行须保留全部20项并统一版本值、运行资源引用断言。
- 本地存储写者仍为原draft map/autosave、review state与本次confirmation/poll state；不新建localStorage邮件副本。服务端数据写者03/04，经05读取。交互X-2草稿、X-3审核、X-6权限、X-7HTTP/poll，全部纳入任务与验收。
- A16执行前审计：06 epoch1 `../fast/manual-reply-delay-master/children/06-frontend/execution.md:17-35`确认04 Controller:55–67解包originalInput时丢弃已持久的DelayedLegacyRestore.inboundProcessingId；LEGACY originId为空，原request不含路由processingId，05消息id必须仍是job.id且未发送任务无真实mail_record锚点。写链已在快照持久原ID；缺口仅为restore响应→刷新后app原编辑器消费链，不需要改DB/快照schema。

## 实现方案

### T0 恢复来源契约补全（I-6/I-7，A16）

在新增授权的DelayedManualReplyController.kt给既有DelayedReplyRestoreResponse末尾增加可空默认null的 `inboundProcessingId`，仅LEGACY从已持久restore身份填充。返回前验证正ID、快照内部同语义的processing来源一致、当前inbound_mail_processing的contact/account归属仍匹配job；不得把mail_record.id或source_inbound_id按数值相等当processing身份，不猜替代来源。保持原actor/admin与FULL授权、REVIEW禁止编辑恢复、DRAFT既有来源逻辑。无法恢复/非法快照沿用422 SNAPSHOT_INVALID固定映射，现行403/404/409边界不改变。旧缺originalInput仍422；公共DelayedManualReplyView不变。

在新增授权的DelayedManualReplyControllerTest.kt断言精确原processingId及缺失/不匹配当前来源/快照身份拒绝，保留READ/非owner拒绝、REVIEW不可编辑、完整原输入和历史422。前端T1仅消费该权威字段，刷新后无本地状态也能打开原编辑器；再校验当前宿主user/contact/source后才允许恢复，遇到新内容保留冲突确认。

### T1 提交响应与草稿（I-6/I-9；S-3/S-4）

修改app.js两个helper：仅SENT显示原成功提示、处理archive信息；WAITING显示「已加入发送队列，60 秒内可撤回」。全局helper新增可选onResult回调以传递结构化response，保持既有boolean约定；两host adapter接受/转交回调，mailbox-chat从回调取job/revision，不把true当SENT。

旧详情局部helper在WAITING不清QA/assembly/RAG/adopt上下文、不按成功重建详情；锁定本次编辑并刷新会话入口，撤回通过GET restore与showUnmatchedDetail恢复原始请求。服务器快照权威，本地状态只做显示缓存。app新增restore宿主回调供chat调用，输入冲突使用现有对话框，确认之前不写当前编辑器。恢复来源/用户不一致直接拒绝。

chat当前草稿详情WAITING/SENDING/UNKNOWN都锁编辑、发送、放弃及自动保存；WAITING回读不清map。任务成功poll回读对应源稿后，仅相同owner/target/seq清理。CANCELLED重新提交须携带delayExpectedRevision；若刷新丢失本地revision，通过消息/GET job恢复，不自动用缺省值重排。review发送同样处理WAITING，原审核按钮禁用；取消后保持APPROVED，重新发送传当前revision，不复制成普通稿。

### T2 渲染与二次确认（I-7/I-9/I-10；S-1/S-2/S-3）

mailbox-chat renderMessage/renderStatusBadge及两列表渲染按S契约实现；列表failedCount来自05，对历史异常也显示图标。footer actions只出现于job，且复核capability。确认状态对象包含完整ownerKey/job/revision/action/busy，第一次点击只渲染；第二次校验仍为原对象后POST；取消恢复原按钮。

成功响应更新同一job主键，并刷新列表/草稿/审核详情。409不重POST，显示公开冲突原因并GET权威状态；403/401沿原授权失效流程；网络失败保留原稿，GET查状态后再允许用户明确操作。不得把确认打开本身暂停倒计时或重新计60秒。

### T3 状态刷新（I-6/I-7/I-9；S-1/S-2/S-3）

每个挂载宿主建立单个轮询调度：可见页面列表最长5秒刷新一次，即使没有选中会话；选中含WAITING/SENDING时消息每2秒，其他每5秒；计时显示每1秒，只更新倒计时textContent避免整表重绘/焦点丢失。无重叠请求，慢请求完成后再安排下一次。隐藏暂停显示轮询，visibilitychange/focus立即拉取，服务端继续发。

估算serverNow使用响应serverNowMs加performance.now经过时间，不直接信任浏览器Date.now。网络失败显示已有状态+原状态提示渠道，不猜测SENT。请求捕获mountGeneration/user/contact/account/query/page序号，回包不匹配丢弃；dispose/登出/失权清计时器、AbortController与confirmation，reader同样执行。真实状态改变才通过onChanged通知，避免list↔detail无限刷新。

### T4 回归与资源（I-6/I-7/I-9/I-10；S-1～S-4）

新增mailboxDelayedReply.test.js覆盖假时钟、两个helper的WAITING/SENT分支、旧详情恢复、reader列表、2次点击、并发点击/超时/过期代次、切专家/账号、visibility/unmount。修改draft和review测试覆盖锁定及取消不清稿；样式测试从本计划两个CSS block提取并逐字比较，只检查新增块，不重写历史合同。

index统一更新缓存键；原样执行现有reader/附件/会议/身份/行为测试。若发现其他测试确需修改，先给出失配证据调整计划文件清单，不无声扩大范围。

## 变更文件清单

|#|文件|操作|
|---|---|---|
|1|src/main/resources/static/app.js|响应/宿主/旧编辑器/reader白名单|
|2|src/main/resources/static/mailbox-chat.js|状态/操作/轮询/草稿/审核|
|3|src/main/resources/static/mailbox-readonly.js|列表图标/轮询生命周期|
|4|src/main/resources/static/mailbox-chat.css|仅追加两合同块|
|5|src/main/resources/static/index.html|统一缓存键|
|6|src/test/js/mailboxDelayedReply.test.js|新增行为测试|
|7|src/test/js/mailboxServerDrafts.test.js|排队与恢复|
|8|src/test/js/mailReviewAdmin.test.js|已审核发送排队|
|9|src/test/js/mailboxChatStyle.test.js|新增CSS/DOM契约断言|
|10|src/main/kotlin/com/weibo/talentintroduction/mail/controller/DelayedManualReplyController.kt|A16：restore-only原processing身份与当前来源校验|
|11|src/test/kotlin/com/weibo/talentintroduction/mail/controller/DelayedManualReplyControllerTest.kt|A16：原来源精确返回与缺失/错配拒绝，保留原授权断言|
|12|src/test/js/mailboxChatBehavior.test.js|A18：既有宿主补performance单调时钟与周期定时器清理，保留断言|
|13|src/test/js/mailboxReadOnly.test.js|A18/A20：宿主兼容；删除被新focus刷新合同替代的旧“不刷新”断言，其余业务/DOM/scroll/权限coalescing断言保留|
|14|src/test/js/mailboxMailReadAccess.test.js|A20：既有performance/单macrotask timer drain兼容，保留断言|
|15|src/test/js/mailboxOutboundAttachments.test.js|A20：既有performance/单macrotask timer drain兼容，保留断言|
|16|src/test/js/mailboxReplyIdentity.test.js|A20：extractFn宿主仅补新增直接closure依赖，保留身份/载荷断言|
|17|src/test/js/mailboxSuspension.test.js|A20：既有performance/单macrotask timer drain兼容，保留断言|
|18|src/test/js/meetingConfirmationIntegration.test.js|A20：既有performance/单macrotask timer drain兼容，保留断言|
|19|src/test/js/authFlow.test.js|A22：extractFn既有sandbox补legacyDelayedReplies Map依赖，保留auth断言|
|20|src/test/js/mailReviewReader.test.js|A22：删除被T3替代的focus唯一access请求断言，保留编辑器身份/正文/CAS/reload断言|

## 验收标准

- I-6 — WAITING无清稿；SENT只清原seq；取消附件/context仍在；审核APPROVED不变；旧详情新输入冲突不会被替换。
- I-7 — reader READ无操作，FULL有对应动作；403和logout清理DOM/轮询；精确白名单拒绝异形路径。
- I-9 — 第一次点击POST0，取消POST0，确认连点POST1；原deadline不变；0秒等待发送；WAITING不出现成功alert；多宿主/无selected列表照常刷新。
- I-10 — 列表不是最新的失败也显示图标；UNKNOWN/永久失败禁重发；历史失败无动作；HTML错误文案被转义。
- S-1 — 逐字比对manual-reply-delay-contract块；确认DOM与骨架一致；动作都在邮件footer；原footer规则无diff。
- S-2 — 逐字比对manual-reply-error-contract块；SVG固定17px/#c53e32；列表无嵌套button；键盘焦点可见原因、aria关联唯一。
- S-3 — 新增/修改节点均复用声明class；无新恢复面板；恢复冲突默认保留当前内容。
- S-4 — 1440px/390px截图和键盘验证，移动44px、无横向溢出；20个现有资源版本参数一致且数量不变（A18更正原19计数笔误）。前端代码无inline style或未声明class。
- 执行 `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailboxServerDrafts.test.js src/test/js/mailReviewAdmin.test.js src/test/js/mailboxChatStyle.test.js`，再执行上述既有受影响功能测试。浏览器验收用真实05/04接口及测试SMTP，不能注入假数据宣称集成通过。
- A16 — 运行JDK11 `mvn test -Dtest=DelayedManualReplyControllerTest`，原及新增控制器断言均实际执行；真实浏览器A-3刷新后从会话撤回恢复到原processing编辑器，使用与job.id不同的源ID，缺失/错配源不能恢复，READ无来源载荷/能力。该命令与Node/浏览器验收均在全部改动完成后执行，不以测试stub代替界面证据。
- A18/A20 — 既有宿主补齐performance/SVG namespace/直接closure依赖与周期定时器清理，单次drain执行当前macrotask集合而非无限追逐新周期；不得禁用生产轮询或丢弃待验证回调。除已被新focus刷新合同明确替代的mailboxReadOnly“不刷新”断言删除外，原业务/DOM/scroll/权限coalescing断言保留，不将旧计数4重钉为6。所有授权兼容测试进入受影响必跑集且Node正常退出；新刷新合同由行为测试/真实浏览器证明。资源20项回执保留。
- A22 — authFlow仅补直接closure Map依赖，原认证行为断言保留；mailReviewReader仅删除与T3冲突的focus唯一access请求断言及专用start变量，不重钉调用集合/计数，保留编辑器身份/正文/CAS/reload验证。两文件纳入必跑受影响集；既有失败/中断记录不算通过，最终完整JS/控制器/浏览器必须重新取得实际证据。

## 人工验收清单

### A-1 操作位置与确认
- 前置条件 — 05环境，测试启动器产生WAITING和可重发FAILED各一封，admin登录，1440px与390px。
- 操作步骤 — 1. 查看底部翻译行。2. 点撤回再取消。3. 再点并确认。4. 对FAILED重复重发步骤。5. 用Tab走全部控件。
- 预期结果 — 处理按钮都在footer，第一击不改服务器状态；确认后才取消/重排；手机命中区44px，确认可换行。图标17px/#c53e32，focus展示原因；翻译和原待处理操作仍可用。
- 覆盖 — I-9/I-10、S-1～S-4，X-7，保留既有操作与移动布局。

### A-2 切换、失败与恢复
- 前置条件 — FULL普通草稿含附件，测试SMTP第一次451；另一个专家有未保存草稿。
- 操作步骤 — 1. 提交WAITING立即切专家。2. 等失败查看列表。3. 返回重发并取消。4. 再确认重发，等待期间切账号。5. 新一轮撤回，检查原稿。
- 预期结果 — 未保存稿不被旧回包清除；列表5秒内（正常网络）出现图标；取消确认不POST；新轮60秒；原正文/附件保持。退出后不再发轮询请求。
- 覆盖 — I-6/I-7/I-9/I-10、S-1/S-2/S-3，X-2/X-6/X-7。

### A-3 审核与旧详情
- 前置条件 — APPROVED稿一份；旧详情编辑器可发送含QA/assembly的人工回复。
- 操作步骤 — 1. 发送审核稿并撤回。2. 旧详情发送后刷新，去会话撤回并恢复。3. 已有不同新输入时再尝试恢复。4. READ用户查看相同会话。
- 预期结果 — 审核仍APPROVED只读；旧输入和附件/证据可恢复且不覆盖新输入；READ仅状态图标，无撤回/重发/附件。待发从未提示「发送成功」。
- 覆盖 — I-6/I-7/I-9/I-10、S-2/S-3，X-2/X-3/X-6。

## 修正记录

- A16（2026-11-05 用户选择「授权本次及同类契约补齐」）：04的生产restore响应遗漏原processing标识，导致06T1/A-3刷新后无法安全定位原编辑器。新增控制器/测试授权并将06文件数9→11特批，T0补齐读取契约和直接验证；不改公共READ投影/数据库/快照schema/发送权限，不重写完成子计划证据。Master A15同步例外及后续同类遗漏契约补齐的严格边界。证据见06 epoch1报告；修订不代表实现或验收已通过。

- A18（适用已批准A5与A15）：既有mailboxChatBehavior.test.js:1100–1102、mailboxReadOnly.test.js:184–187的sandbox缺performance，后者缺createElementNS；新必须周期定时器需清理以免Node挂起。授权两个既有宿主兼容修订，保持业务断言，文件数11→13，仅测试扩大、无产品范围变化。另据实际index `\?v=` 回执（CSS lines11–17、JS lines2547–2559）更正资源总数19→20，保留“全部资源、数量不变”的原合同。完整回执由执行报告保存；不构成样式/行为/安全降级。

- A20（适用已批准A5）：artifact2329真实报告436项/432通过/4失败，mailboxMailReadAccess:739–741/778/795–796与mailboxOutboundAttachments:951–953/1145的复制宿主缺performance且无限drain，mailboxReplyIdentity:379报state undefined。全JS定向grep另确认mailboxSuspension:954–956/1107和meetingConfirmationIntegration:930–932/1105相同缺口；新增五个既有测试文件授权，限宿主clock/单turn drain/直接closure依赖，文件数13→18。已有mailboxReadOnly:844–865旧focus不刷列表断言与T3冲突，删除该过时断言、不改计数凑绿，保留其他业务断言。证据/命令/中断记录由execution保存；同epoch2、修复轮0，无产品范围扩张或新job UNKNOWN锁放宽。

- A22（适用已批准A5）：artifact2339实际controller13/13、JS2037/2054通过，authFlow sandbox110/286抽取stopAuthenticatedApp却缺legacyDelayedReplies Map，引发15项ReferenceError；mailReviewReader:147–157的旧focus唯一access请求断言与新刷新合同冲突。新增两既有测试文件授权，限上述直接fixture依赖/过时断言删除，保留其他业务断言，文件数18→20。taskActivityCenter的重复key检查不改；自有新增样式测试在原授权范围消除重复literal。无新产品范围、权限或安全变化；最终必跑证据仍要求全部实际完成。

