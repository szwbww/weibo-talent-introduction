# 03：真实应用中的 user1 只读邮件视图

状态：待审阅。依赖：01/02机器验证通过。后续：04。子系统：前端邮件宿主（1）；实施文件13个。页面使用现有index、CSS、真实API，不使用先前localhost静态预览作为生产实现。

## 需求描述

user1登录态仅显示“收发件箱”业务tab，可搜索授权专家、分页、查看收/发正文及更早邮件；不显示发送/附件/草稿/材料/备注/管理入口。admin继续现有完整MailboxChat。保留现有品牌壳、会话布局和收发气泡；不重写整个app，不开放用户管理页、自动翻译或其他业务tab。

## 关键不变量

### Invariant I-1: 身份先分流，再加载业务
- Rule: login/me均传username+mailReadOnly给宿主；固定user1若缺失或不为true的元数据，显示“权限信息异常，请重新登录”，不得进入admin分支。reader默认state.view=mailbox；desktop/mobile/navigation函数都仅允许mailbox。
- Applies to: checkAuth/login回调/startAuthenticatedApp、bindMobileCoreNavigation、setView、refreshCurrentView、loadMailbox。
- Violation consequence: 启动读取发件账号/任务数据或脚本失败降级到管理员旧表格。
- 来源: original；K-dom-stub-tests-hide-dangling-refs。

### Invariant I-2: 只读模块无写路径
- Rule: 新MailboxReadOnly仅GET列表/详情；注销是独立auth POST。不能mount MailboxChat/工作台/专家材料/联系人详情；不加载草稿、发件账号、群推/自动回复摘要；不写localStorage/sessionStorage正文。旧模块缺失不回退；新模块缺失显示“只读邮件视图加载失败，请刷新”。
- Applies to: 新模块、app加载/刷新/退出/visibility回调、api防御性限制。
- Violation consequence: 暗中保存草稿、泄露账号资料或可发送。
- 来源: K-shared-workbench-fixed-mode-host-adapter（选择独立只读模块，不接交互工作台）；K-ui-removal-retires-obsolete-contract-tests（保留admin DOM，不删除其契约）。

### Invariant I-3: 失权与异步隔离
- Rule: 当前正文仅存本实例内存；切专家、刷新、重新聚焦/visible和翻更早页都请求后端。403立即清空所选正文及分页cursor；401清空并回登录；网络错误不恢复旧正文。请求带实例/会话generation，销毁或身份变化后晚响应不能渲染。身份切换硬刷新页面，禁止沿用admin模块缓存。
- Applies to: 列表/详情/更早页、focus/visibility、unmount、stopAuthenticatedApp和login切换。
- Violation consequence: 撤销后从缓存继续看、前账号内容串给user1。
- 来源: K-mailbox-draft-cache-owner-capture。

### Invariant I-4: 文本渲染与范围明确
- Rule: 显示summary姓名/邮箱及message主题、方向、时间、cleanedBody非空否则body；全部textContent或统一escape，不插入未经净化HTML，不生成附件/内部下载链接、远程图片或自动可点击URL。本版搜索仅q（专家姓名/邮箱）；不提供账号/标签/任务筛选。
- Applies to: 新列表/气泡DOM、URL参数编码。
- Violation consequence: XSS、附件/外部资源绕路、调用未开放API。
- 来源: original。

### Invariant I-5: admin回归及资源一致
- Rule: admin保留原启动/导航/chat/草稿/附件；新增分支位于这些调用前。所有index本地?v=资源用同一新版本`20261027-user1-readonly`；保持既有脚本相对顺序，新模块在app之前。
- Applies to: app/index/CSS与测试。
- Violation consequence: admin回归或缓存混用导致只读模块缺失。
- 来源: K-frontend-cache-key-triad；K-js-test-invocation-surface。

## 样式契约

### S-1: 双栏、列表与正文
- 复用：mailbox-chat.css:1–59、73–78、102–115、150–151；具体规则及后置覆盖全文见审计附件14。`.mail-chat/.mc-experts/.mc-list-tools/.mc-search-row/.mc-expert-list/.mc-person/.mc-person-main/.mc-pager/.mc-conversation/.mc-header/.mc-identity/.mc-actions/.mc-scroll/.mc-timeline/.mc-message/.mc-body/.mc-empty/.mc-error`；按钮复用styles.css:802–875及mailbox-chat.css:65–68、109–112。不可替换为近似卡片。
- DOM结构（动态文案均纯文本；列表/消息节点可重复；无选中时正文区域放mc-empty；出错放mc-error）：

```html
<div class="mail-chat" data-role="readonly-mailbox">
  <aside class="mc-experts" aria-label="已授权专家">
    <div class="mc-list-tools">
      <div class="mc-search-row">
        <input type="search" aria-label="搜索已授权专家" placeholder="搜索姓名或邮箱">
        <button type="button" class="button" data-action="search">搜索</button>
      </div>
      <button type="button" class="button" data-action="refresh">刷新</button>
      <p class="mc-note">只读：仅显示管理员授权的专家邮件</p>
    </div>
    <div class="mc-expert-list">
      <div class="mc-person" data-active="false">
        <button type="button" class="mc-person-main" data-action="select-expert">
          <strong>专家姓名</strong><small>专家邮箱</small><small>最新邮件主题</small>
        </button>
      </div>
    </div>
    <div class="mc-pager">
      <button type="button" class="button" data-action="previous">上一页</button>
      <span aria-live="polite">第 1 页，共 0 位专家</span>
      <button type="button" class="button" data-action="next">下一页</button>
    </div>
  </aside>
  <section class="mc-conversation" aria-label="往来邮件">
    <header class="mc-header"><div class="mc-identity"><h2>专家姓名</h2><p>专家邮箱</p></div><span class="mc-badge">只读</span></header>
    <div class="mc-scroll">
      <div class="mc-timeline">
        <button type="button" class="mc-text-button mc-load-older" data-action="older">加载更早邮件</button>
        <article class="mc-message" data-direction="INBOUND">
          <header><span>收件</span><time>邮件时间</time></header>
          <h3>邮件主题</h3><div class="mc-body">邮件正文</div>
        </article>
      </div>
    </div>
  </section>
</div>
```

发件article使用`data-direction="OUTBOUND"`和“发件”。无授权列表用`<p class="mc-empty">暂无已授权的专家邮件</p>`；搜索无结果用“没有匹配的已授权专家”；未选择详情用“请选择左侧专家”；403详情用`<p class="mc-error" role="alert">暂无权限查看该专家邮件</p>`；网络错误同class，文字“加载失败，请刷新重试”。加载态复用mc-empty“加载中…”。无邮件专家详情“暂无往来邮件”。重复/空态/错误节点全部归S-1。

### S-2: reader作用域规则（新增原文）
- 在mailbox-chat.css末尾原样追加。使用body的`data-mail-read-only="true"`防止全局样式影响admin；未声明其他新增规则或class。

```css
body[data-mail-read-only="true"] .nav-tab[data-view]:not([data-view="mailbox"]),
body[data-mail-read-only="true"] .view:not(#view-mailbox),
body[data-mail-read-only="true"] #mailboxLegacyToolbar,
body[data-mail-read-only="true"] #mailboxExecutionFilterBar,
body[data-mail-read-only="true"] #mailboxConversationPanel > .panel-head .panel-head-actions,
body[data-mail-read-only="true"] #mailboxPagination,
body[data-mail-read-only="true"] #unmatchedDetailPanel,
body[data-mail-read-only="true"] #worldClockTrigger,
body[data-mail-read-only="true"] #worldClockPanel {
    display: none !important;
}
body[data-mail-read-only="true"] .mail-chat .mc-person {
    grid-template-columns: minmax(0, 1fr);
}
```

- reader卡片没有关注星号，第二条只在reader去掉空列；原mc-person使用点见证据16，原规则不修改。
- 新规则无新增交互色；hover/active/disabled/focus继承S-1全规则。禁止inline style及未声明class。既有index中的inline style不在本轮清理范围。

### S-3: 应用壳与菜单
- 复用index.html:79–163、750–815现有DOM；reader仅保留原`data-view="mailbox"`按钮可见，文案“收发件箱”；`#mobileCoreView`的option重建为`<option value="mailbox">收发件箱</option>`，保留label/select原样式。
- `#mailboxConversationPanel > .panel-head h2` reader文字改为“收发邮件 · 只读”；body标记reader；`#view-mailbox`追加已有`mc-refined` class，挂载点为原`#mailboxList`。logout及当前用户显示保留；不新增独立假页面。
- 所有修改/新增节点限定S-1/S-3。admin模式恢复原标题/菜单，模式切换以整页重载清除历史缓存。不得修改既有class规则来影响管理员。

## 现状审计

共同审计前端章节与证据04/09/14/16为本节组成部分。重点实际调用链：

- app.startAuthenticatedApp:21051自动启动变量/徽标/任务；refreshCurrentView:5283还刷新自动回复/群推；loadMailbox:21843先loadMailboxAccounts，模块缺失会退旧表格；这些都需在reader分支之前截断。
- **额外生命周期交叉点**：initMailboxGroupPush:20739注册visibilitychange，页面回前台会直接refreshMailboxGroupPush；仅修改refreshCurrentView不足。其当前原文已补在14中。
- bootstrap:22750及末尾调用绑定全局事件；reader不删除admin DOM，以免既有DOM契约断裂；不把隐藏元素当安全边界。
- **缓存存储**：原chat Map按user/account/contact缓存并支持错误恢复；reader不使用该Map，不触碰其写读路径；新实例内存只有当前页/选中专家/消息窗口/cursor/generation，unmount全清。原localStorage联系人布局等不改；03禁止新增正文持久存储。
- **跨身份**：login/me→宿主模式；visibility→额外群推请求；logout→旧draft flush；后端撤销→reader403。均有I/A覆盖。
- 样式先看最终覆盖：气泡13px/1.55而非文件前段12px/1.8；header后段padding17px22px14px；不以截图猜颜色。S-1精确规则见14。

## 实现方案

1. **模块（I-2/I-3/I-4，S-1）**：新增`F/mailbox-readonly.js`，IIFE导出唯一`window.MailboxReadOnly`，API `mount(host,{api,sessionUser}) / unmount(host)`，handle可refresh。使用现有列表返回`items,total,page,size`；列表size20；详情limit50，`nextBefore/hasMore`加载更早页，按`source:id`去重并保留时间线正序。每次选中重新请求，不复用上次专家正文；前端按server结果展示，不自造邮件数据。
2. **身份与路由（I-1/I-5，S-3）**：app维护仅内存auth元数据；checkAuth和login成功均显式传入。reader启动强制mailbox且在变量/账号/任务加载前return；setView拒绝非mailbox；手机选项重建；refreshCurrentView/loadMailbox最前面只调用新handle，绝不loadMailboxAccounts和fallback。模块未加载展示固定错误。
3. **额外入口（I-1/I-2，S-2/S-3）**：refreshMailboxGroupPush、refreshAutoReplySummary、updateUnmatchedBadge及其可重入触发点reader早退；原logout仍使groupPush失效，但reader跳过flushMailboxChatDrafts。stopAuthenticatedApp立即unmount新模块、移除监听、清正文。首次新身份不能复用上一身份state，检测到不同身份后整页重载。
4. **防御性前端请求界限（I-2/I-3）**：app.api在已知reader时，fetch前只允许GET两条会话路径及必要auth login/me/logout；其它调用本地返回带status403/code READ_ONLY_FORBIDDEN的错误，避免遗漏生命周期产生请求噪声。这个检查仅防误调用，真实安全仍是01/02。不要限制admin或未确定身份的原认证流程；禁止将403全局当强制登出，详情失权只清本会话。
5. **刷新/错误/竞态（I-3，S-1）**：显式刷新和document visible/window focus都重新读列表及已选详情；合并同一时刻重复事件，只保留一组在途请求。刷新前清正文；更早页若403/网络失败也清正文，禁止旧缓存回退。每请求AbortController+15秒超时，沿现有api的signal传递，不叠加会替换signal的timeoutMs选项；unmount abort并递增generation。晚响应只能在instance/user/contact/request序号全部匹配时应用。选中后首次定位最新消息，更早页加载保留滚动位置；消息数量仅当前窗口，重选全部重读。
6. **样式与版本（I-5，S-1–S-3）**：新增S-2原文，现有规则不改。index在app.js前增加版本化mailbox-readonly.js，所有已有?v=同改`20261027-user1-readonly`，保持其余顺序。来源K-frontend-cache-key-triad：重新扫描当前index+测试，不按旧知识猜脚本数量。
7. **测试（I-1–I-5，S-1–S-3）**：新增行为测试；authFlow扩充login/me两入口；mailboxChatStyle扩充readerDOM/CSS和版本断言，保留admin原断言。用真实index提取检查节点，不用万能querySelector stub掩盖缺元素。index新增第19个资源后，6个既有测试的18资源清单/计数须同步为19（仅在`app.js`前插入`mailbox-readonly.js`，不改其它行为断言）。

## 变更文件清单

|#|路径|动作|
|---|---|---|
|1|src/main/resources/static/mailbox-readonly.js|新增只读模块|
|2|src/main/resources/static/app.js|身份/导航/生命周期分流|
|3|src/main/resources/static/index.html|注册脚本、统一版本|
|4|src/main/resources/static/mailbox-chat.css|仅追加S-2|
|5|src/test/js/mailboxReadOnly.test.js|新增行为与网络测试|
|6|src/test/js/authFlow.test.js|双身份/缺元数据/退出回归|
|7|src/test/js/mailboxChatStyle.test.js|真实DOM/规则/资源版本|
|8|src/test/js/discoveryPapers.test.js|仅18→19资源清单/计数同步|
|9|src/test/js/discoveryReview.test.js|仅18→19资源清单/计数同步|
|10|src/test/js/mailboxChatBehavior.test.js|仅18→19资源清单/计数同步|
|11|src/test/js/mailboxGroupPush.test.js|仅18→19资源清单/计数同步|
|12|src/test/js/meetingConfirmationAssets.test.js|仅18→19资源清单/计数同步|
|13|src/test/js/trustReplyWorkbenchSharedMount.test.js|仅18→19资源清单/计数同步|

## 验收标准

- I-1：user1 login/me路径都仅mailbox；缺mailReadOnly不进admin；任意setView/手机选项/旧task筛选状态不能换tab。初始无账号/任务请求，模块缺失不调用MailboxChat或旧mailbox API。
- I-2：fetch spy精确断言允许路径集合；聚焦、visible、刷新、搜索、翻页、退出前无草稿GET/PUT/DELETE、无sender-accounts/材料/位置/群推/自动回复请求。程序调用被本地guard拒绝，绕过JS直接请求仍由01拒绝。
- I-3：A慢响应→切B、读途中unmount/换身份、撤销后403、超时/500、401分别断言无旧正文；撤销后更早页也清；未写local/sessionStorage；重复focus/visible不产生两组请求；dispose解除全部监听。
- I-4：含`<img onerror>`/script/链接样本均只作为文本，无DOM执行/远程加载；body缺省显示空文案而非undefined；分页source:id不重复。
- I-5：admin原authFlow/mailboxChatBehavior/mailboxServerDrafts/mailboxOutboundAttachments回归；脚本顺序和所有资源version一致。
- S-1/S-2/S-3：逐字比较新增CSS；真实DOM校验已声明class/层级，无新增inline style；1440/390px浏览器截图核对最终值及hidden菜单不可键盘聚焦；admin截图不受S-2影响。
- 命令：`node --check src/main/resources/static/mailbox-readonly.js`、`node --check src/main/resources/static/app.js`；`node --test src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxServerDrafts.test.js src/test/js/mailboxOutboundAttachments.test.js`。本步最后运行全部`node --test src/test/js/*.test.js`，只有新失败/修改才重复扩大。

## 人工验收清单

### A-1: 真实页面与权限菜单
- 前置条件: 01/02/03完成的真实测试应用；fixture建立user1 session，A授权/B未授权；1440px和390px窗口。
- 操作步骤: 1. 打开真实应用index。2. 看桌面tab/手机下拉。3. 搜索A/B。4. 开A并加载更早页。
- 预期结果: 仅收发件箱，A可读B无结果；无发送/附件按钮；列表左栏306px、卡片激活色#eaf1ff；正文13px/1.55，发件浅蓝#eff5ff；390px上下布局无横向溢出。
- 覆盖: I-1/I-2/I-4；S-1/S-2/S-3。

### A-2: 撤销与失败不保留正文
- 前置条件: user1已打开A；admin另浏览器登录；开发工具可模拟offline。
- 操作步骤: 1. admin用01接口撤销A。2. user1切出再回窗口。3. 重授权后打开A，再offline刷新。4. 模拟慢A响应后快速点B。
- 预期结果: 失权提示“暂无权限查看该专家邮件”，旧正文清空；offline提示“加载失败，请刷新重试”，不恢复A；晚A响应不覆盖B。网络无草稿/群推等请求。
- 覆盖: I-2/I-3；S-1。

### A-3: admin与身份切换回归
- 前置条件: admin有草稿与附件；reader前后均可通过测试登录态访问。
- 操作步骤: 1. admin查看材料并保存草稿。2. 登出换user1。3. 返回admin并打开原草稿。4. 删除/阻断新脚本后看reader。
- 预期结果: admin原功能保留；user1无旧admin正文/草稿和额外tab；admin草稿仍可恢复；reader脚本缺失只显示加载失败，不出现旧管理列表。
- 覆盖: I-1/I-3/I-5；S-2/S-3。
