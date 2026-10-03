# 02 邮箱、阅读与人工回复

状态：待审阅。依赖：01 已通过；子系统：邮箱呈现；5 个变更文件。

## 需求描述

手机以“专家会话列表→单屏会话→返回列表”阅读和人工回复。待匹配来信也有可见返回入口。编辑正文、上传已有支持的附件、选择已有模板参考可在手机完成；返回与旋转保持草稿和阅读位置。

保持：user/accountScope/contactId 草稿归属、processingId/accountCode 回复目标、既有附件与发送幂等机制、异步所有权校验、未匹配详情唯一节点、服务器筛选排序与分页、桌面分栏、原AI/会议组件业务语义。

不做：云端/硬刷新草稿恢复、重新设计AI工作台、邮件发送协议修改、批量外发控制台优化、后台定时器增改。

## 关键不变量

### I-1：同一个会话实例
- Rule：新增实例内 `mobilePane=list|detail` 只控制呈现。返回不unmount、不调用selectExpert、不清空selectedContactId，不新建第二份回复表单；桌面忽略手机pane。
- Applies to：skeletonHtml、onClick、selectExpert、selectUnmatched、focus定位、unmount。
- Violation consequence：返回销毁草稿、组件双挂或重复发送。
- 来源：original。

### I-2：先采集草稿，再隐藏
- Rule：返回、切专家和unmount前，若当前存在人工编辑器，先saveDraftFromInputs，再saveConversationState。账号scope改变必须在覆盖filters前以旧owner采集保存，再走原清理流程。前者从DOM合并到当前target草稿；不得用打开会话时的旧快照覆盖附件、meeting或followUpAnchorMailRecordId。
- Applies to：新增返回、显式选择、applyOptions/筛选应用的accountScope切换、既有unmount及恢复；异步发送完成仍使用捕获的owner/map/target/revision。
- Violation consequence：看似返回成功但正文或上传结果丢失、串专家。
- 来源：K-mailbox-draft-cache-owner-capture。

### I-3：隐藏几何信息不得覆盖位置
- Rule：隐藏前捕获当前有效anchor与scrollTop（0有效）。隐藏期间save/quiet-refresh允许更新数据、草稿，但保留最后有效几何；重新展示后rAF恢复anchor，缺失再用scrollTop。不得把display:none时的零高度/零坐标写回缓存。
- Applies to：saveConversationState、onScroll、refreshConversationQuiet、selectExpert和恢复返回。
- Violation consequence：返回同一专家跳到错误位置，已有历史窗口被重置。
- 来源：original；沿用既有500条/10会话内存淘汰边界，不扩大缓存。

### I-4：页面呈现与请求分别失效
- Rule：列表详情切换有呈现代次；用户返回后，select/focus的迟到响应可按原owner更新数据，但不得把pane改回detail或抢焦点。列表刷新不自动选首项。显式点同一专家可只显示现有DOM，不重挂编辑器；点其他专家仍走原选择链。
- Applies to：selectExpert、locateFocusExpert、resolveFocusAndSelection、selectUnmatched、空列表/消失项、rAF、matchMedia监听。
- Violation consequence：返回后跳屏、草稿被新来信静默改目标。
- 来源：original + K-mailbox-draft-cache-owner-capture。

### I-5：新布局不增加业务动作
- Rule：旋转、切pane、返回只改变UI；不触发发送/处理/状态更改。待匹配selectedUnmatchedId不得写到contact会话缓存；唯一#unmatchedDetailPanel的归还流程不变。任务钻取所用旧邮箱模式保留。
- Applies to：onClick、unmatched节点租约、host切页/unmount、legacy邮箱路径。
- Violation consequence：重复发信、错误工单归属、任务钻取失效。
- 来源：K-mailbox-popover-scope-and-fixed-containing-block。

### I-6：原CSS与资源合同继续成立
- Rule：mailbox-chat.css及历史target.css字节不变；新字面class不以mc-开头，全部声明于styles.css；11资源统一换键。
- Applies to：样式追加、skeletonHtml、index、mailboxChatStyle测试。
- Violation consequence：字节守卫或class白名单失败、样式缓存混用。
- 来源：K-mailbox-chat-css-byte-contract、K-frontend-cache-key-triad。

## 样式契约

### S-1：唯一列表、唯一会话与独立返回条

将 skeletonHtml 根从 `<div class="mail-chat">` 改为下方根；aside的内部原样保留。在原aside与原section之间插入返回按钮，不能放入经常被innerHTML替换的mc-header里。`data-mobile-pane`必须与实例状态同步。

```html
<div class="mail-chat mobile-core-mailbox" data-mobile-pane="list">
    <!-- 原 aside.mc-experts 及全部内容保留 -->
    <button type="button" class="button mobile-mailbox-back" data-action="mobile-mailbox-back">返回会话列表</button>
    <!-- 原 section.mc-conversation 保留 -->
</div>
```

注释仅标识插入位置，实施不以注释替换原节点。返回按钮在加载、错误、无权限与待匹配详情下都可见。class复用：mailbox-chat.css:1–16、38–71、133–164；具体原规则与全部命中见audit。

### S-2：输入、筛选与现有浮层

手机更多筛选改为列表工具栏内展开，复用原七字段和应用按钮，不再依赖位于backdrop-filter祖先中的fixed定位；桌面原popover不变。管理浮层仍使用现有body portal，会议/模板仍用原组件；不建立新portal。

下面CSS整块原样追加styles.css末尾；不改mailbox-chat.css。已有button状态和focus-visible继承mailbox-chat.css:66–68，新增返回使用同一按钮规则。与01不重复定义class。

```css
/* mobile-core-02:start */
.mobile-mailbox-back { display: none; }
@media (max-width: 760px) {
    #view-mailbox .panel-head,
    #view-mailbox .panel-head-actions,
    #view-mailbox .toolbar { flex-wrap: wrap; gap: 8px; min-width: 0; }
    #view-mailbox .panel-head .button { min-height: 44px; height: auto; white-space: normal; }
    #view-mailbox .mailbox-list { min-width: 0; }
    #view-mailbox .table-wrap { max-width: 100%; overflow-x: auto; }
    .mail-chat.mobile-core-mailbox { display: flex; flex-direction: column; height: auto; min-height: 0; gap: 12px; }
    .mail-chat.mobile-core-mailbox[data-mobile-pane="list"] > .mc-conversation,
    .mail-chat.mobile-core-mailbox[data-mobile-pane="list"] > .mobile-mailbox-back,
    .mail-chat.mobile-core-mailbox[data-mobile-pane="detail"] > .mc-experts { display: none; }
    .mail-chat.mobile-core-mailbox[data-mobile-pane="detail"] > .mobile-mailbox-back {
        display: inline-flex; align-self: flex-start; min-height: 44px; height: auto;
    }
    .mail-chat.mobile-core-mailbox .mc-experts { min-height: 0; max-height: none; }
    .mail-chat.mobile-core-mailbox .mc-expert-list { min-height: 120px; max-height: 65dvh; overflow: auto; }
    .mail-chat.mobile-core-mailbox .mc-conversation { min-height: 0; }
    .mail-chat.mobile-core-mailbox .mc-scroll { flex: none; height: 65dvh; min-height: 240px; max-height: none; overflow: auto; }
    .mail-chat.mobile-core-mailbox .mc-header,
    .mail-chat.mobile-core-mailbox .mc-actions,
    .mail-chat.mobile-core-mailbox .mc-compose-footer,
    .mail-chat.mobile-core-mailbox .mc-message footer { flex-wrap: wrap; gap: 8px; }
    .mail-chat.mobile-core-mailbox .mc-header-meta { overflow-wrap: anywhere; }
    .mail-chat.mobile-core-mailbox :is(button, .button) { min-height: 44px; height: auto; white-space: normal; }
    .mail-chat.mobile-core-mailbox .mc-icon { min-width: 44px; }
    .mail-chat.mobile-core-mailbox .mc-search-row { flex-wrap: wrap; }
    .mail-chat.mobile-core-mailbox .mc-filter-popover {
        position: static; inset: auto; flex: 1 0 100%;
        width: 100%; max-width: 100%; max-height: none;
    }
    .mail-chat.mobile-core-mailbox .mc-filter-fields { grid-template-columns: minmax(0, 1fr); }
    .mail-chat.mobile-core-mailbox :is(input:not([type="checkbox"]):not([type="radio"]), select, textarea, [contenteditable="true"]) {
        min-width: 0; max-width: 100%; min-height: 44px; font-size: 16px;
    }
    .mail-chat.mobile-core-mailbox .mc-editor { min-height: 160px; max-height: 40dvh; }
    .mail-chat.mobile-core-mailbox .mc-body { font-size: 14px; line-height: 1.8; }
    .mail-chat.mobile-core-mailbox .mc-compose-footer > [data-role="target-info"] { width: 100%; overflow-wrap: anywhere; }
    .mail-chat.mobile-core-mailbox .outbound-file { min-width: 0; }
    body .mail-chat.mc-overlay-root .mc-manage-dialog { width: calc(100vw - 24px); max-height: calc(100dvh - 24px); }
    body .mail-chat.mc-overlay-root .mc-manage-dialog .button,
    body .reply-template-dialog .button,
    body .meeting-dialog .button,
    body .meeting-dialog .meeting-close { min-height: 44px; height: auto; }
    body .meeting-dialog .meeting-form :is(input, select, textarea),
    body .reply-template-dialog :is(input:not([type="radio"]):not([type="checkbox"]), select, textarea) {
        min-height: 44px; font-size: 16px; max-width: 100%;
    }
}
/* mobile-core-02:end */
```

不新增浮动发送条，不将编辑器fixed到底部；在既有滚动容器中正常输入、滚动到发送按钮。软键盘遮挡必须实机验收，不能单靠dvh断言解决。当前其他现有浮层的小屏规则继续复用；发现其阻断核心流程时先更新本计划对应规则及文件清单再改。

## 现状审计

### 内存会话/草稿
- schema：sessionStore Map，外键user|accountScope|contactId；内部drafts Map键contactId:processingId:accountCode。draft保存subject/html/text、QA上下文、附件、会议、跟进锚点及版本；不写本地持久化。
- 写路径：getDraft/setDraft/deleteDraft（753/759/765）、saveDraftFromInputs（4676）、saveConversationState（3279）、setOutboundAttachmentItems（4409）、invalidateOutboundRequestId（4422），以及发送成功的捕获Map清理、retargetManual的迁移。完整grep回执见audit，实施不得只搜setDraft。
- 读路径：manualComposeHtml/人工编辑器渲染、发送捕获payload、会议/模板对话框打开、restoreFromRecord、quiet refresh与新来信目标检测。返回仅增加一次现有采集调用，不扩展schema。
- saveConversationState可由selectExpert、滚动、loadOlder、quiet refresh、unmount、host refresh触发，统一在函数内部保护隐藏几何，不能只保护返回按钮调用点。
- selectedUnmatchedId另有租约：selectUnmatched→mountUnmatchedDetail；记录消失/切tab/unmount→release。保持原读写路径，不混入contactId。
- applyOptions（7387附近）在替换filters后调用meetingCloseDisposeOnAccountScopeChange；新增草稿采集必须前置到替换前。高级筛选应用的同类scope切换入口也按旧owner采集，不仅覆盖selectExpert。

### 前端样式盘点
- skeletonHtml:1022根下只有aside与section；selectExpert:1815先存旧状态再销毁子视图；selectUnmatched:1615有“同一项提前return”；重复选同一项恢复屏幕须发生在该return之前。
- focus路径有列表命中和异步locateFocusExpert两类；只在onClick设置pane会漏掉从专家页/日历进入邮箱。
- 邮箱正文原12px/1.8；编辑器原12px、最小100px（后定义覆盖早期160px）；手机本方案分别14px/16px及最小160px。查看源码不能只看首次声明。
- 色值：会话白底#fff，发件#eff5ff，边框#dce4ef，错误#be123c；按钮disabled opacity .45。保持现有颜色/状态。
- 历史CSS字节守卫与class白名单在mailboxChatStyle.test.js:1–135；本段新增移动规则按更高作用域覆盖，原CSS源和历史证据均不变。
- `.panel`及topnav带backdrop-filter；管理portal保持body位置，移动筛选用static避免固定包含块。（来源：K-mailbox-popover-scope-and-fixed-containing-block、K-panel-bg-token-is-translucent）

## 实现方案

1. **T-1 呈现状态**：mailbox-chat.js中扩展实例私有mobilePane、paneEpoch与列表滚动/触发元素；修改skeletonHtml/onClick，新增setMobilePane/return helper；外部focus缺失/失败也提供返回；tab切换/选中项消失回list。只新增S-1节点。遵循 I-1/I-4/I-5/S-1。
2. **T-2 草稿和位置**：同文件，在返回/切专家/unmount前调用两步保存，scope切换在替换filters之前采集；saveConversationState统一保护隐藏几何；同专家重开复用DOM并在可见后恢复，不重新选目标。quiet refresh隐藏状态保持锚点，不额外停启轮询。遵循 I-2/I-3/I-4/S-1。
3. **T-3 小屏样式**：styles.css追加S-1/S-2完整块，保留原邮箱CSS；index.html统一缓存键。bind一次matchMedia change仅用于状态/焦点恢复，unmount解除；没有每次resize重绘编辑器。遵循 I-1/I-5/I-6/S-1/S-2。
4. **T-4 回归**：在mailboxChatBehavior.test.js复用真实模块挂载及现有DOM模拟器，扩展手机pane与隐藏几何模拟；在mailboxChatStyle.test.js增加新CSS/DOM契约，不删除原字节与白名单断言。遵循全部I/S。浏览器几何、触屏和输入另做真实验收。

## 变更文件清单

| 文件 | 用途 |
|---|---|
| src/main/resources/static/mailbox-chat.js | pane、返回、隐藏位置与草稿采集保护 |
| src/main/resources/static/styles.css | 追加手机邮箱CSS |
| src/main/resources/static/index.html | 11资源缓存键 |
| src/test/js/mailboxChatBehavior.test.js | 行为、竞态、隐藏位置回归 |
| src/test/js/mailboxChatStyle.test.js | 增加新块契约，保留原守卫 |

app.js、mailbox-chat.css、历史target.css、共享workbench与meeting组件代码不在本段修改清单。

## 验收标准

- I-1/I-4：初访list；显式select/focus detail；返回list；旧focus响应不跳屏；同专家重开不新建编辑器；平板/桌面两个pane仍显示；挂载/卸载不重复监听。
- I-2：A输入正文+subject+上传附件→返回→A，值一致；A发送中切B，完成只清A提交版本；返回不清QA/会议/锚点。覆盖两个账号scope和退出重登录隔离。
- I-3：锚点在0和非0两种情况，隐藏quiet refresh后不被零坐标覆盖；展开后同消息同相对位置（浏览器误差≤8px）；超过既有缓存上限仍按原规则降级。
- I-5：纯返回/旋转/筛选取消没有业务写请求；待匹配选中/返回/项消失/绑定完成没有重复ID和泄漏租约；任务钻取旧模式可用。
- I-6/S-1/S-2：原CSS与历史证据byte equal；新CSS逐字一致，class白名单通过；11资源同键；核心按钮44px，输入16px；没有新增fixed输入栏或inline style。
- 命令：`node --check src/main/resources/static/mailbox-chat.js`；`node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxOutboundAttachments.test.js src/test/js/mailboxCalendarIntegration.test.js src/test/js/mailboxTemplateReferenceStyle.test.js`；最后全量JS测试。

## 人工验收清单

### A-1：草稿、目标和返回
- 前置条件：测试专家A/B各有一封来信；A待回复目标的账号已知；390px手机。
- 操作：选A→正文输入“手机A草稿”→上传一个测试PDF→滚到历史邮件→返回→选B→返回→选A。
- 预期：同屏单pane；A草稿仍为“手机A草稿”，附件文件名/数量不变，账号和目标来信ID未换成B；最后阅读消息与位置恢复，误差≤8px。
- 覆盖：I-1/I-2/I-3/S-1。

### A-2：慢响应与隐藏刷新
- 前置条件：浏览器限速；A有足够长历史；后台在测试环境向A添加一封测试来信。
- 操作：打开A立刻返回；等待旧请求完成；再进A写草稿，返回后等待既有刷新，再打开A。
- 预期：迟到请求不抢回会话屏；有草稿时新来信不会静默替换回复目标；旧阅读位置仍可恢复。
- 覆盖：I-2/I-3/I-4。

### A-2b：账号范围隔离
- 前置条件：同一测试专家在两个测试发件账号范围均有往来信件。
- 操作：账号范围A写入“A账号草稿”→返回→筛选到B→写入“B账号草稿”→返回A；退出后用另一测试用户登录再打开该专家。
- 预期：A仍显示“A账号草稿”，B显示“B账号草稿”，回复账号不交叉；另一个用户看不到前一用户草稿。
- 覆盖：I-2/I-4/I-5、scope/user隔离。

### A-3：发送、键盘与跨专家完成
- 前置条件：自有测试收件人A/B；一次发送延迟响应；两类真实手机。
- 操作：A写“手机发送验证”并上传附件→唤起键盘→滚到发送按钮→点击一次→返回打开B写“B保留”；等待A完成。
- 预期：A仅收到一封含附件邮件；B正文仍为“B保留”；按钮在键盘弹出时可经滚动触达；请求失败时A草稿可重试，未提示成功。
- 覆盖：I-2/I-5/S-2、既有发送/附件不变。

### A-4：待匹配与旧模式
- 前置条件：一条未匹配来信、一条可钻取邮件的任务记录。
- 操作：手机打开待匹配详情→返回→再开→关联测试专家；从任务进入邮件记录。
- 预期：返回按钮一直可见；待匹配项更新按原逻辑；页面只有一个详情面板；任务邮件列表保留正确执行ID筛选。
- 覆盖：I-4/I-5/S-1。

### A-5：浮层、视觉与旋转
- 前置条件：已有草稿；390px/844px横竖屏与1440px桌面。
- 操作：展开更多筛选→修改→取消/应用；打开管理、模板参考、会议邀请；滚动、关闭；旋转；桌面复查。
- 预期：筛选手机内嵌单列，取消零筛选请求、应用一次；浮层在视口内、关闭按钮可点；主按钮44px、正文14px、输入16px；草稿仍在；桌面保持原两栏与popover。
- 覆盖：I-1/I-5/I-6/S-1/S-2、服务器筛选与组件语义不变。
