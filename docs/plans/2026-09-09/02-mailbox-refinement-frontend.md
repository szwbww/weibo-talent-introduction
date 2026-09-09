# 02 收发件箱布局与交互修复

状态：待实施。依赖01接口；03负责缓存键升级。本计划7文件、一个前端子系统；不改styles.css/专家列表组件/可信工作台内部。

## 需求描述

- R1 三个tab：全部、关注、待处理；仅⋯展开高级筛选，删除顶部重复筛选。
- R2 固定头部显示专家摘要及关注/材料/管理/详情，管理表单移出消息区。
- R3 来信底部翻译/添加标签/标记已处理，无原始正文/技术信息栏，译文原位展开；发件仅翻译。
- R4 使用01服务端排序；专家列表跳转有缓存恢复、无缓存定位最新；普通刷新/加载更早/局部操作不乱跳。
- R6 列表收发数量右侧显示真实专家标签；单行溢出省略，悬停显示完整标签；卡片不额外显示“待处理”标记。
- R5 工作台默认折叠、人工回复默认展开；目标局部样式在生产页面壳内落地。
- 必须保留：P1导航/登录/全局操作；P2关注用户隔离；P3回复账号/目标/QA审计/草稿；P4共享资料组件与按需获取；P5任务钻取旧列表；P6状态/层级及两类标签的真实写入。
- 不做：mock数据/假接口、全站换肤、发件标签新表、下载重构、框架迁移。

## 关键不变量

### Invariant I-1: tab和服务端顺序
- Rule：删除CHIP_WAITING/badge/请求判断；全部不传followed/pendingOnly，关注传followed=true，待处理传pendingOnly=true；不发送waitingReply。全部不得被旧onlyPending状态覆盖。所有专家列表遵守服务端分页顺序，禁止本页sort；处理最后待处理信后重新查询服务端列表，必要时回退空页。
- Applies to：chipParams/conversationsParams/applyOptions/markResolved/fetchList。
- Violation consequence：假全部、跨页顺序错误。
- 来源：用户，总计划I-1。

### Invariant I-2: 单一筛选源
- Rule：保留mailboxFilter*唯一id和动态账号刷新；聊天模式迁入popover，退出聊天/任务钻取恢复旧toolbar。修改字段为草稿，应用/Enter才生效；关闭未应用恢复生效值；重置立即清空高级筛选并查询但保持tab/q。快照完整替换，不能合并残留旧值。
- Applies to：index/app/filter snapshot/mount/unmount。
- Violation consequence：清空无效、筛选没传API、旧模式损坏。
- 来源：K-relocated-control-refresh-owner。

### Invariant I-3: 邮件操作身份与标签批量投影
- Rule：来信操作只用source=INBOUND_PROCESSING和真实processing.id；标签直读timeline.tags，新增用POST回包tags，删除按tagId。无每封/thread请求。晚响应不得写入另一专家。发件无标签/mark动作。
- Applies to：卡片、标签modal adapter、markResolved。
- Violation consequence：N+1拉全历史、串标签、误处理。
- 来源：K-mail-record-source-inbound-id、审计D3。

### Invariant I-4: 按需翻译与局部更新
- Rule：cleanedBody非空优先否则body，翻译同一显示正文；点击才POST /api/translate {text}，输出escapeText，收起再开复用缓存，失败可重试；空正文无翻译。标签/翻译不重建manual DOM。
- Applies to：renderMessage/translate/局部缓存。
- Violation consequence：大量隐式请求、XSS、丢草稿。
- 来源：app.js:1550/1574/1600。

### Invariant I-5: 位置有界且按上下文隔离
- Rule：缓存key=sessionUser/contactId/accountScope，模块Map，最多10会话、每会话最多500条已加载消息；不写localStorage正文。缓存含窗口、游标、首个可见消息source:id及相对top、fallback scrollTop，0有效。无缓存/失效→最后一封邮件顶部8px（受可滚动范围约束），不是编辑器底。普通刷新、加载更早守住锚点；账号范围/用户变化不能复用。
- Applies to：select/focus/renderTimeline/quietRefresh/loadOlder/scroll/unmount/退出登录。
- Violation consequence：缓存位置和消息窗口错位、跨专家串信、无限拉历史。
- 来源：总计划I-4，审计D6。

### Invariant I-6: 管理迁移不改变写入含义
- Rule：管理overlay挂到body下独立mc-overlay-root，不在mc-scroll或带backdrop-filter的panel内；标签沿原即时保存，明确提示。状态/层级取消不写，保存沿现有两端点；部分成功要逐项提示并回读，不能伪装事务。共享专家标签默认渲染输出不改，仅mc作用域CSS调整。
- Applies to：header/管理/saveSettings/专家标签。
- Violation consequence：误称取消能撤销即时标签、其他详情样式被破坏。
- 来源：K-expert-tag-editor-shared-render-contract。

### Invariant I-7: 保留生产业务
- Rule：工作台仍固定LIVE_INBOUND、切换unmount；材料仍mcHostOpenMaterials；人工发送仍原服务/目标/账号/QA/富文本。新来信不静默替换已编辑草稿目标。任务钻取仍旧列表，所有全局按钮保留真实id/handler。
- Applies to：app.js及workbench/manual/materials宿主。
- Violation consequence：漂亮页面下业务不可用。
- 来源：K-shared-workbench-fixed-mode-host-adapter。

### Invariant I-8: 专家标签单行展示，语义不混用
- Rule：卡片只读summary.expertTags，不使用message.tags/状态标签；名称按现有expertTagLabels映射，未知标签显示原值。收发数量右侧剩余宽度给标签，固定单行ellipsis，不能增高卡片。悬停用原生title展示完整名称列表（无需JS宽度测量/新浮层）；键盘聚焦卡片通过aria-label读到完整标签。空数组不显示标签占位；null显示“标签暂不可用”，不得显示“无标签”。卡片不渲染“待处理”badge；pendingCount仍用于tab筛选和服务端排序，不删除业务状态。
- Applies to：renderPerson、列表刷新、专家管理加删后的summary更新。
- Violation consequence：布局换行、截断后无法查看、将业务状态/邮件标签误当专家标签。
- 来源：用户新截图；01 I-6。

## 样式契约

S-6完整CSS是唯一执行合同。真实导航/登录/页面标题保留生产结构；禁止复制预览body/topnav及mock fetch。文本/状态选项从真实数据填充并escape。旧截图“更多”文字、四tab被后续需求覆盖。

### S-1: 宿主与两栏
- 复用styles.css:802-858按钮、948-984面板/panel-head。现有toolbar加id=mailboxLegacyToolbar，主panel加id=mailboxConversationPanel；chatOn时view-mailbox加mc-refined。
- chat模式把mailboxRefreshBtn移到原panel-head-actions最前，检查回复/批量发送/自动回复随后；保留“已激活账号收发邮件记录”标题。退出时还原。其他页面不变。
- 306px左栏/16px栏距；≤1100px为275px/12px；右栏头部固定、内部消息滚动。

```html
<div class="mail-chat">
  <aside class="mc-experts" aria-label="专家会话列表">
    <div class="mc-list-tools"><!-- S-2 --></div>
    <div class="mc-expert-list" aria-live="polite"><!-- 原mc-person结构，无waiting badge --></div>
    <div class="mc-pager"><!-- 原分页与计数 --></div>
  </aside>
  <section class="mc-conversation" aria-label="专家往来信件">
    <header class="mc-header"><!-- S-3 --></header>
    <div class="mc-timeline-head"><span>往来信件</span><span class="mc-position-hint"></span><button class="mc-text-button" type="button" data-action="mc-latest">↓ 最新消息</button></div>
    <div class="mc-scroll" tabindex="0" aria-label="往来信件滚动区"><!-- S-4/S-5 --></div>
  </section>
</div>
```

### S-2: 搜索与更多筛选
- 原mailboxFilter*七字段id、账号动态填充复用；删除原inline widths；字段组移到popover，不复制DOM。只有⋯可见，title/aria-label=更多筛选；Escape/外部点击关闭并回焦点。
- 标签选项GET /api/inbound-summary/tags/options的items真实label去重；不把旧“专家/首发/收件”等类别发为label。退出聊天还原旧类别选项。无数据只有全部标签，加载失败可重试，不伪造选项。
- 下方是最终合成DOM；字段组与mailboxSearchBtn仅来自原index的唯一节点。计数示例2按实际非空字段数生成。

```html
<div class="mc-search-row">
  <input type="search" aria-label="搜索专家" placeholder="搜索专家姓名、邮箱">
  <button class="mc-icon" type="button" data-action="mc-more-filters" title="更多筛选" aria-label="更多筛选" aria-expanded="false" aria-controls="mcFilterPopover">⋯<span class="mc-filter-count" hidden></span></button>
  <div class="mc-filter-popover" id="mcFilterPopover" role="dialog" aria-label="更多筛选" hidden>
    <header><strong>更多筛选</strong><button class="mc-close" type="button" data-action="mc-close-filters" aria-label="关闭筛选">×</button></header>
    <div class="mc-filter-fields" id="mailboxFilterFields">
      <label class="mc-field">邮箱账号<select id="mailboxFilterAccountCode"></select></label>
      <label class="mc-field">收发方向<select id="mailboxFilterDirection"></select></label>
      <label class="mc-field">邮件标签<select id="mailboxFilterTag"></select></label>
      <label class="mc-field">专家邮箱<input id="mailboxFilterRecipient" placeholder="输入邮箱关键词"></label>
      <label class="mc-field mc-field-wide">主题 / 内容关键词<input id="mailboxFilterKeyword" placeholder="搜索邮件主题或正文"></label>
      <label class="mc-field">开始日期<input type="date" id="mailboxFilterStartDate"></label>
      <label class="mc-field">结束日期<input type="date" id="mailboxFilterEndDate"></label>
    </div>
    <p class="mc-inline-error" role="alert" hidden></p>
    <footer><button class="mc-text-button" type="button" data-action="mc-reset-filters">重置</button><button class="button primary" type="button" id="mailboxSearchBtn">应用筛选</button></footer>
  </div>
</div>
<div class="mc-filters">
  <button class="mc-filter" type="button" data-action="mc-filter" data-chip="all" aria-pressed="true">全部</button>
  <button class="mc-filter" type="button" data-action="mc-filter" data-chip="followed" aria-pressed="false">关注</button>
  <button class="mc-filter" type="button" data-action="mc-filter" data-chip="pending" aria-pressed="false">待处理</button>
</div>
<div class="mc-filter-summary" hidden><span>2 项筛选已生效</span><button class="mc-text-button" type="button" data-action="mc-clear-filters">清除</button></div>
```

### S-3: 专家摘要与管理
- 头部第一行身份与操作，第二行状态/层级/标签/ORCID；17/11px字体，padding17px22px14px。选项用window.operatorStatusOptions/indexLevelOptions，不照预览写死。
- 管理480px、两列16px间距；保存按钮32px高，不整行。普通role=dialog overlay挂body下独立`.mail-chat.mc-overlay-root`，层级990，现有共享标签弹层1000可显示于上方；不用native dialog阻挡共享弹层。焦点陷阱只对当前顶层弹层生效。
- 专家标签沿原renderMailboxExpertTagEditor输出及data-orcid/data-level/id；加删仍即时保存。明确提示与状态层级保存的区别。

```html
<header class="mc-header">
  <div class="mc-identity"><h2><!-- 姓名 --></h2><p><!-- 邮箱 · 账号 --></p></div>
  <div class="mc-actions">
    <button class="button" type="button" data-action="mc-toggle-follow" data-contact-id="真实contactId">☆ 关注</button>
    <button class="button" type="button" data-action="mc-open-materials" data-contact-id="真实contactId">材料 0</button>
    <button class="button" type="button" data-action="mc-manage-expert">管理</button>
  </div>
  <div class="mc-header-meta"><span class="mc-badge" data-tone="success"><!-- 状态 --></span><span class="mc-badge"><!-- 层级 --></span><!-- 真实专家标签 --><span><!-- ORCID --></span><button class="mc-text-button" type="button" data-action="mc-open-expert" data-contact-id="真实contactId">查看专家详情 ↗</button></div>
</header>
<div class="mail-chat mc-overlay-root"><div class="mc-manage-overlay" hidden>
  <section class="mc-manage-dialog" role="dialog" aria-modal="true" aria-labelledby="mcManageTitle" tabindex="-1">
    <header><div><h3 id="mcManageTitle">专家管理</h3><p><!-- 姓名 --></p></div><button class="mc-close" type="button" data-action="mc-close-manage" aria-label="关闭专家管理">×</button></header>
    <div class="mc-status-grid"><label class="mc-field">专家状态<select data-role="status-select" data-current-value="真实原状态"></select></label><label class="mc-field">专家层级<select data-role="level-select" data-current-value="真实原层级"></select></label></div>
    <div class="mc-settings-tags" data-role="expert-tags"><!-- 原共享标签editor --></div>
    <p class="mc-manage-note">状态和层级点击保存；标签修改即时生效。</p>
    <p class="mc-inline-error" role="alert" hidden></p>
    <footer class="mc-dialog-actions"><button class="button" type="button" data-action="mc-close-manage">取消</button><button class="button primary" type="button" data-action="mc-save-settings">保存变更</button></footer>
  </section>
</div></div>
```

缺画像保留“该专家在 ES 中无画像文档，标签功能不可用”。关闭恢复触发按钮焦点。共享标签重渲染后仍在mc-settings-tags作用域，不变回原大块。

### S-4: 邮件卡片
- 来信白底/发件#eff5ff；内边距14px17px，圆角11px；正文13px/1.85，主题12px/1.55。按钮一行，处理最右；已处理来信仍能加标签。发件不提供不存在的标签接口。
- 原附件renderAttachmentSummary保留最多3文件名和共享资料入口，mc-mail-extras仅用于附件，不保留data-role=mail-extras原文技术块。
- 复用styles.css:3727/3742/3748/3772的真实inbound-tag-chip/QA/CUSTOM/删除；新标签继续原QA/custom选择弹层，不抄预览示例chips。

```html
<article class="mc-message" data-direction="INBOUND" data-source="INBOUND_PROCESSING" data-id="真实id" data-message-key="INBOUND_PROCESSING:真实id">
  <header><span>专家来信 · 时间 · 账号</span><span class="mc-badge" data-tone="pending">待处理</span></header>
  <h3><!-- 解码后主题escapeText --></h3>
  <div class="mc-body"><!-- 非空cleanedBody否则body，escapeText --></div>
  <!-- 原附件文件信息details，可选 -->
  <div class="mc-translation" hidden></div>
  <div class="mc-tag-row" data-role="mail-tags"><!-- 原tag chips，自有removeAction --></div>
  <p class="mc-inline-error" role="alert" hidden></p>
  <footer>
    <button class="mc-text-button" type="button" data-action="mc-translate" data-message-key="INBOUND_PROCESSING:真实id">翻译</button>
    <button class="mc-text-button" type="button" data-action="mc-add-mail-tag" data-message-key="INBOUND_PROCESSING:真实id">＋ 添加标签</button>
    <button class="mc-text-button mc-process" type="button" data-action="mc-mark-resolved" data-message-key="INBOUND_PROCESSING:真实id">✓ 标记已处理</button>
  </footer>
</article>
```

翻译状态：翻译→翻译中…(disabled)→收起译文，失败“翻译失败，重试”；空正文隐藏翻译。空tags隐藏该行，不留“暂无标签”大块。mark成功变mc-done文本“✓ 已处理”；错误原位提示，按钮恢复。保留既有标记确认动作，不新增单信查看处理入口。

### S-5: 可信工作台与人工回复
- mc-scroll顺序：mc-timeline→workbench details默认关闭→manual details默认open→原logs折叠。
- 共享TrustReplyWorkbench内部DOM/CSS不改；人工manualComposeHtml:935的真实账号/目标/QA信息/富文本工具/发送按钮全部保留，只使用S-6局部外观；editor min100/max240px。

```html
<details class="mc-section" data-section="workbench"><summary>可信回复工作台</summary><div class="mc-section-content"><!-- 原固定LIVE_INBOUND宿主 --></div></details>
<details class="mc-section" data-section="manual" open><summary>人工回复</summary><div class="mc-section-content"><!-- 原manualComposeHtml的mc-compose/mc-editor-tools/mc-editor/mc-compose-footer --></div></details>
```

### S-7: 左侧专家标签行（替换截图红框）

- 原renderPerson:255-284的mc-person-meta内“待专家回复”彻底去掉；数量保留。移除卡片上的“待处理”badge，不迁移到姓名行；待处理tab与全部tab优先排序保持不变。
- 专家标签为轻量蓝色chips，字号10px、单行20px行高；整组容器flex:1/min-width:0/nowrap/ellipsis，count不压缩；不设标签个数硬阈值，不换行。不用flex-wrap来排标签。
- 全部标签名称通过escapeText后加入span和title属性。title=`专家标签：学术科研、重点关注、…`为完整未截断文本，显示多少都不改变title；长单标签也能悬停看全。
- 使用原生title，不新增绝对定位tooltip，不逐行绑定mouseenter读取ES，不增加浮层CSS；共享手机/键盘可通过卡片aria-label或现有专家管理查看完整标签。
- expertTags=[]：不渲染mc-person-tags；null：单行灰色“标签暂不可用”，title同文案，点击全局刷新可重试；不能从邮件tag临时兜底。

```html
<button class="mc-person-main" type="button" data-action="mc-select-expert" data-contact-id="真实contactId" aria-label="查看专家姓名往来邮件；专家标签：完整标签名称">
  <span class="mc-person-heading"><strong><!-- 姓名 --></strong></span>
  <small><!-- 发件账号 --></small>
  <small><!-- 最近来信/发件摘要 --></small>
  <span class="mc-person-meta">
    <span class="mc-person-counts">收 0 · 发 1</span>
    <span class="mc-person-tags" title="专家标签：学术科研、重点关注"><span class="mc-person-tag">学术科研</span><span class="mc-person-tag">重点关注</span></span>
  </span>
</button>
```

以上是原mc-person-main的替换骨架，外层mc-person与独立关注星标不变。所有新class的精确规则已纳入S-6完整CSS末段“S-7”；本节不是额外第二份CSS。名单多少、名称长度均只影响截断，不改变行数。

### S-6: 完整CSS与所有状态

下方为mailbox-chat.css完整最终内容，逐字复制；同内容位于evidence/mailbox-chat.target.css。既有规则和局部覆盖顺序均属于合同，不允许只摘新增几条。所有新节点归属S-1..S-5及S-7；hover/active/disabled/focus、响应式由该全文和已引用生产button规则覆盖。不得新增未列class、inline style或修改全局styles.css。

```css
.mail-chat{display:grid;grid-template-columns:306px minmax(0,1fr);gap:16px;min-height:480px;height:calc(100dvh - 216px);color:#475569;font-size:12px;line-height:1.6}
.mail-chat *{box-sizing:border-box}
.mail-chat [hidden]{display:none!important}
.mail-chat :is(h2,h3,p){margin:0}
.mail-chat .mc-experts,.mail-chat .mc-conversation{display:flex;flex-direction:column;min-width:0;min-height:0;border:1px solid rgba(15,23,42,.11);border-radius:14px;background:#f8faff;overflow:hidden}
.mail-chat .mc-list-tools{display:flex;flex-direction:column;gap:10px;padding:14px;border-bottom:1px solid #e2e8f0}
.mail-chat .mc-list-tools input{width:100%;height:32px;min-height:32px;margin:0;padding:0 10px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#475569;font:inherit}
.mail-chat .mc-list-tools input::placeholder{color:#94a3b8}
.mail-chat .mc-filters{display:flex;flex-wrap:wrap;gap:6px}
.mail-chat .mc-filter{min-height:28px;padding:3px 8px;border:1px solid #dce4ef;border-radius:7px;background:#f8faff;color:#64748b;font:inherit;cursor:pointer}
.mail-chat .mc-filter:hover{border-color:#93b4ec;background:#eff5ff}
.mail-chat .mc-filter:active{background:#dbeafe}
.mail-chat .mc-filter[aria-pressed=true]{border-color:#1e40af;background:#eff5ff;color:#1e40af;font-weight:600}
.mail-chat .mc-filter:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .mc-expert-list{flex:1;min-height:0;overflow:auto;padding:8px;overscroll-behavior:contain}
.mail-chat .mc-person{position:relative;display:grid;grid-template-columns:minmax(0,1fr) 28px;gap:8px;margin-bottom:6px;border:1px solid transparent;border-left:3px solid transparent;border-radius:10px;background:transparent}
.mail-chat .mc-person:hover{background:#eff5ff}
.mail-chat .mc-person[data-active=true]{border-color:#c2d3f2;border-left-color:#3c65cd;background:#eaf1ff}
.mail-chat .mc-person-main{display:flex;flex-direction:column;align-items:stretch;min-width:0;gap:5px;padding:12px 0 12px 10px;border:0;background:transparent;color:#475569;text-align:left;font:inherit;cursor:pointer}
.mail-chat .mc-person-main:active{opacity:.85}
.mail-chat .mc-person-main strong{font-size:13px;font-weight:600;color:#1e293b;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.mail-chat .mc-person-main small{font-size:11px;color:#64748b;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.mail-chat .mc-person-meta{display:flex;align-items:center;flex-wrap:wrap;gap:6px;font-size:11px}
.mail-chat .mc-follow{align-self:start;margin:10px 4px 0 0;padding:0;width:24px;height:28px;border:0;border-radius:7px;background:transparent;color:#94a3b8;font-size:20px;line-height:1;cursor:pointer}
.mail-chat .mc-follow:hover{background:#fef3c7;color:#b45309}
.mail-chat .mc-follow:active{background:#fde68a}
.mail-chat .mc-follow[aria-pressed=true]{color:#d97706}
.mail-chat .mc-follow:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .mc-badge{display:inline-flex;align-items:center;padding:1px 7px;border:1px solid #dce4ef;border-radius:12px;background:#f1f5f9;color:#64748b;font-size:11px;white-space:nowrap}
.mail-chat .mc-badge[data-tone=pending]{background:#fff7ed;border-color:#fed7aa;color:#b45309}
.mail-chat .mc-badge[data-tone=success]{background:#ecfdf5;border-color:#a7f3d0;color:#059669}
.mail-chat .mc-badge[data-tone=error]{background:#fff1f2;border-color:#fecdd3;color:#e11d48}
.mail-chat .mc-pager{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px;padding:10px 12px;border-top:1px solid #e2e8f0;color:#64748b;font-size:11px}
.mail-chat .mc-header{display:flex;align-items:flex-start;justify-content:space-between;flex-wrap:wrap;gap:12px;padding:16px 18px;border-bottom:1px solid #e2e8f0}
.mail-chat .mc-identity{flex:1;min-width:180px}
.mail-chat .mc-identity h2{font-size:16px;font-weight:600;color:#1e293b;overflow-wrap:anywhere}
.mail-chat .mc-identity p{margin-top:4px;color:#64748b;font-size:11px;overflow-wrap:anywhere}
.mail-chat .mc-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
.mail-chat .mc-scroll{flex:1;min-height:0;overflow:auto;padding:16px 18px;overscroll-behavior:contain;scrollbar-gutter:stable}
.mail-chat .mc-timeline{display:flex;flex-direction:column;gap:14px;margin-bottom:16px}
.mail-chat .mc-load-older{align-self:center}
.mail-chat .mc-day{align-self:center;color:#94a3b8;font-size:11px;padding:2px 8px}
.mail-chat .mc-message{align-self:flex-start;width:min(88%,820px);min-width:0;padding:12px 14px;border:1px solid #dce4ef;border-radius:10px;background:#fff}
.mail-chat .mc-message[data-direction=OUTBOUND]{align-self:flex-end;background:#eff5ff;border-color:#cbdcf7}
.mail-chat .mc-message header{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;margin-bottom:8px;color:#64748b;font-size:11px}
.mail-chat .mc-message h3{font-size:13px;color:#334155;font-weight:600;margin-bottom:8px;overflow-wrap:anywhere}
.mail-chat .mc-body{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px;line-height:1.8;color:#334155}
.mail-chat .mc-message footer{display:flex;justify-content:flex-end;align-items:center;gap:8px;margin-top:10px;color:#64748b;font-size:11px}
.mail-chat .mc-mail-extras{margin-top:10px;padding-top:8px;border-top:1px solid #e2e8f0;color:#64748b;font-size:11px}
.mail-chat .mc-mail-extras summary{cursor:pointer}
.mail-chat .mc-attachment-names{padding-top:6px;overflow-wrap:anywhere;line-height:1.8}
.mail-chat .mc-attachment-names>div{overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.mail-chat .mc-section{margin-top:14px;border:1px solid #dce4ef;border-radius:10px;background:#f8faff;overflow:hidden}
.mail-chat .mc-section>summary{padding:12px 14px;cursor:pointer;color:#334155;font-size:13px;font-weight:600}
.mail-chat .mc-section[open]>summary{border-bottom:1px solid #e2e8f0}
.mail-chat .mc-section-content{padding:14px;min-width:0}
.mail-chat .mc-note{padding:12px;border:1px solid #dbe7fa;border-radius:7px;background:#eff5ff;color:#64748b;font-size:12px;line-height:1.7}
.mail-chat .mc-empty{padding:40px 20px;color:#64748b;text-align:center}
.mail-chat .mc-error{padding:12px;border:1px solid #fecdd3;border-radius:7px;background:#fff1f2;color:#be123c}
.mail-chat .mc-compose{display:flex;flex-direction:column;gap:10px;min-width:0}
.mail-chat .mc-compose label{display:flex;flex-direction:column;gap:6px;color:#64748b;font-size:12px}
.mail-chat .mc-compose input{width:100%;height:32px;min-height:32px;padding:0 10px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font:inherit}
.mail-chat .mc-editor-tools{display:flex;flex-wrap:wrap;gap:6px}
.mail-chat .mc-editor{min-height:160px;max-height:360px;overflow:auto;padding:12px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font-size:12px;line-height:1.8;overflow-wrap:anywhere}
.mail-chat .mc-compose-footer{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px}
.mail-chat .button{white-space:nowrap}
.mail-chat .button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.mail-chat :is(button,a,input,select,summary,[contenteditable=true]):focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
@media(max-width:1100px){.mail-chat{grid-template-columns:280px minmax(0,1fr);gap:12px}.mail-chat .mc-header{padding:14px}.mail-chat .mc-scroll{padding:14px}.mail-chat .mc-message{width:94%}}
@media(max-width:760px){.mail-chat{display:flex;flex-direction:column;height:auto;min-height:0;gap:12px}.mail-chat .mc-experts{max-height:320px;min-height:240px}.mail-chat .mc-expert-list{min-height:100px}.mail-chat .mc-conversation{min-height:560px}.mail-chat .mc-scroll{max-height:none;overflow:visible;padding:12px}.mail-chat .mc-message{width:100%}.mail-chat .mc-header{padding:12px}.mail-chat .mc-identity{min-width:0;width:100%;flex-basis:100%}.mail-chat .mc-section-content{padding:12px}.mail-chat .mc-actions{width:100%}}
@media(prefers-reduced-motion:reduce){.mail-chat .button{transition:none}.mail-chat .button:hover,.mail-chat .button:active{transform:none}}
/* S-1..S-6: mailbox only; retain production shell. */
#view-mailbox.mc-refined #mailboxLegacyToolbar{display:none}
#view-mailbox.mc-refined #mailboxConversationPanel{overflow:visible}
#view-mailbox.mc-refined #mailboxList{padding:16px}
#view-mailbox:not(.mc-refined) .mc-filter-fields{display:contents}
.mail-chat .mc-experts{overflow:visible;position:relative}
.mail-chat .mc-search-row{position:relative;display:flex;align-items:center;gap:7px}
.mail-chat .mc-search-row input{flex:1;min-width:0;height:36px;min-height:36px}
.mail-chat .mc-icon{display:inline-flex;align-items:center;justify-content:center;flex:none;min-width:36px;height:36px;padding:0 8px;border:1px solid #d8e1ef;border-radius:8px;background:#fff;color:#6482b2;font:inherit;font-size:20px;cursor:pointer;gap:4px}
.mail-chat .mc-icon:hover{background:#edf3ff;border-color:#93b4ec}
.mail-chat .mc-icon:active{background:#dbeafe}
.mail-chat .mc-icon[aria-expanded=true]{background:#eaf1ff;border-color:#7396df;color:#2451b9}
.mail-chat .mc-icon:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .mc-filter-count{padding:0 4px;border-radius:8px;background:#2553c5;color:#fff;font-size:10px;line-height:16px}
.mail-chat .mc-filters{gap:12px;flex-wrap:nowrap}
.mail-chat .mc-filter{border:0;border-bottom:2px solid transparent;border-radius:0;background:transparent;padding:6px 5px 10px}
.mail-chat .mc-filter[aria-pressed=true]{border-bottom-color:#2e59c7;background:transparent;color:#244ca9}
.mail-chat .mc-filter-summary{display:flex;align-items:center;gap:8px;color:#7890b3;font-size:11px}
.mail-chat .mc-filter-popover{position:absolute;top:44px;left:0;z-index:var(--z-dropdown);width:430px;max-width:calc(100vw - 48px);max-height:calc(100dvh - 210px);overflow:auto;background:#fff;border:1px solid #dce5f1;border-radius:12px;box-shadow:0 14px 50px #20395d26}
.mail-chat .mc-filter-popover header{display:flex;align-items:center;justify-content:space-between;padding:16px 20px;border-bottom:1px solid #edf1f7;font-size:14px;color:#475d79}
.mail-chat .mc-filter-popover .mc-filter-fields{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:14px;padding:18px 20px}
.mail-chat .mc-field{display:flex;flex-direction:column;gap:7px;min-width:0;color:#8494aa;font-size:11px}
.mail-chat .mc-field-wide{grid-column:1/-1}
.mail-chat .mc-field input,.mail-chat .mc-field select{width:100%;min-width:0;height:34px;min-height:34px;margin:0;padding:0 9px;border:1px solid #dce4ef;border-radius:7px;background:#fcfdff;color:#5f7390;font:inherit;font-size:12px}
.mail-chat .mc-filter-popover footer{display:flex;justify-content:flex-end;align-items:center;gap:8px;padding:14px 20px;border-top:1px solid #edf1f7}
.mail-chat .mc-filter-popover footer .mc-text-button{margin-right:auto}
.mail-chat .mc-close{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border:0;border-radius:5px;background:transparent;color:#91a1b7;font-size:21px;cursor:pointer}
.mail-chat .mc-close:hover{background:#edf3ff;color:#2451b9}
.mail-chat .mc-close:active{background:#dbeafe}
.mail-chat .mc-close:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .mc-header{flex:none;background:#fff;padding:17px 22px 14px}
.mail-chat .mc-header-meta{display:flex;align-items:center;flex-wrap:wrap;gap:8px;flex-basis:100%;font-size:11px;color:#8a9bb2}
.mail-chat .mc-identity h2{font-size:17px;font-weight:600;letter-spacing:-.25px}
.mail-chat .mc-badge{border-radius:5px;font-size:10px;line-height:1.6;padding:2px 7px}
.mail-chat .mc-badge[data-tone=pending]{background:#fff5e9;border-color:#f6dfc6;color:#bb7838}
.mail-chat .mc-timeline-head{flex:none;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 22px;color:#8b9bb1;font-size:11px}
.mail-chat .mc-position-hint{margin-left:auto;font-size:10px;color:#8b9bb1}
.mail-chat .mc-text-button{display:inline-flex;align-items:center;gap:4px;border:0;border-radius:4px;background:transparent;color:#6482b2;font:inherit;font-size:11px;line-height:1.5;padding:3px 0;cursor:pointer}
.mail-chat .mc-text-button:hover{color:#244ca9;background:#edf3ff}
.mail-chat .mc-text-button:active{background:#dbeafe}
.mail-chat .mc-text-button:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .mc-message{width:min(92%,820px);padding:14px 17px;border-radius:11px;box-shadow:0 2px 6px #334b7210}
.mail-chat .mc-message h3{font-size:12px;color:#4d617d;font-weight:600;margin-bottom:11px;line-height:1.55}
.mail-chat .mc-body{font-size:13px;line-height:1.85;color:#465974}
.mail-chat .mc-message footer{justify-content:flex-start;gap:12px;margin-top:12px;padding-top:10px;border-top:1px solid #e7edf5}
.mail-chat .mc-process{margin-left:auto;border:1px solid #dce4ef;border-radius:7px;padding:3px 9px;white-space:nowrap}
.mail-chat .mc-done{margin-left:auto;color:#4c927b;font-size:11px}
.mail-chat .mc-translation{white-space:pre-wrap;overflow-wrap:anywhere;padding:12px 14px;margin-top:12px;border-left:2px solid #b8ccef;border-radius:0 7px 7px 0;background:#f3f7ff;color:#59708f;font-size:12px;line-height:1.85}
.mail-chat .mc-tag-row{display:flex;align-items:center;flex-wrap:wrap;gap:6px;margin-top:12px}
.mail-chat .mc-tag-row .inbound-tag-chip,.mail-chat .mc-header-meta .expert-tag{font-size:10px;line-height:1.6;padding:2px 6px;border-radius:5px;margin:0}
.mail-chat .mc-inline-error{padding:8px 0;color:#be123c;font-size:11px;line-height:1.6}
.mail-chat .mc-section{background:#fff;border-radius:10px}
.mail-chat .mc-section>summary{display:flex;align-items:center;gap:8px;list-style:none;font-size:12px;font-weight:600;color:#506783}
.mail-chat .mc-section>summary::-webkit-details-marker{display:none}
.mail-chat .mc-section>summary::after{content:'⌄';margin-left:auto;color:#91a1b7}
.mail-chat .mc-section[open]>summary::after{content:'⌃'}
.mail-chat .mc-section>summary:hover{background:#f5f8ff}
.mail-chat .mc-section>summary:active{background:#edf3ff}
.mail-chat .mc-editor{min-height:100px;max-height:240px}
.mail-chat .mc-manage-overlay{position:fixed;inset:0;z-index:990;display:flex;align-items:center;justify-content:center;padding:16px;background:#172c4738;backdrop-filter:blur(2px)}
.mail-chat .mc-manage-dialog{width:480px;max-width:100%;max-height:85dvh;overflow:auto;background:#fff;color:#475d79;border:1px solid #d9e3f1;border-radius:14px;box-shadow:0 20px 90px #17325730}
.mail-chat .mc-manage-dialog header{display:flex;align-items:flex-start;justify-content:space-between;padding:18px 20px;border-bottom:1px solid #edf1f7}
.mail-chat .mc-manage-dialog h3{font-size:16px;font-weight:600}
.mail-chat .mc-manage-dialog header p{margin-top:6px;color:#96a6ba;font-size:11px}
.mail-chat .mc-status-grid{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:16px;padding:22px 20px}
.mail-chat .mc-settings-tags{padding:0 20px 20px}
.mail-chat .mc-settings-tags .expert-tag-editor{margin:0;padding:0;border:0;background:transparent;box-shadow:none}
.mail-chat .mc-settings-tags .tag-editor-loading{min-height:0}
.mail-chat .mc-settings-tags .inbound-tag-editor-head{display:flex;align-items:center;justify-content:space-between;gap:8px;margin:0 0 12px}
.mail-chat .mc-settings-tags .inbound-tag-editor-head h3{font-size:12px}
.mail-chat .mc-settings-tags .inbound-tag-editor-chips{display:flex;flex-wrap:wrap;gap:6px}
.mail-chat .mc-settings-tags .expert-tag{font-size:11px;line-height:1.6;padding:2px 7px;border-radius:5px;margin:0}
.mail-chat .mc-manage-note{padding:0 20px 16px;color:#8b9bb1;font-size:11px;line-height:1.7}
.mail-chat .mc-dialog-actions{display:flex;justify-content:flex-end;gap:8px;padding:14px 20px;border-top:1px solid #edf1f7}
.mail-chat .mc-dialog-actions .button{width:auto;flex:none}
.mail-chat .button{height:32px;min-height:32px;padding:0 11px;font-size:12px;border-radius:7px}
.mail-chat .button:not(.primary){box-shadow:none}
.mail-chat .mc-manage-dialog .mc-inline-error,.mail-chat .mc-filter-popover .mc-inline-error{padding:0 20px 12px}
@media(max-width:1100px){.mail-chat{grid-template-columns:275px minmax(0,1fr);gap:12px}.mail-chat .mc-header{padding:15px}.mail-chat .mc-timeline-head{padding:10px 15px}.mail-chat .mc-message{width:96%}.mail-chat .mc-header .button{font-size:11px;padding:0 8px}}
@media(max-width:760px){#view-mailbox.mc-refined #mailboxList{padding:12px}.mail-chat{height:auto;min-height:0}.mail-chat .mc-search-row{position:static}.mail-chat .mc-filter-popover{position:fixed;top:100px;left:16px;width:calc(100vw - 32px);max-width:none;max-height:calc(100dvh - 116px)}.mail-chat .mc-scroll{max-height:65dvh;overflow:auto}.mail-chat .mc-message{width:100%}.mail-chat .mc-timeline-head{padding:10px 12px}.mail-chat .mc-position-hint{display:none}.mail-chat .mc-status-grid{grid-template-columns:1fr}.mail-chat .mc-manage-dialog{max-height:calc(100dvh - 32px)}}
@media(prefers-reduced-motion:reduce){.mail-chat *{scroll-behavior:auto!important;transition:none!important}}

.mail-chat.mc-overlay-root{display:contents}

/* S-7: expert tags in the list, one line only. */
.mail-chat .mc-person-heading{display:flex;align-items:center;gap:6px;min-width:0}
.mail-chat .mc-person-heading strong{flex:1;min-width:0}
.mail-chat .mc-person-meta{flex-wrap:nowrap;min-width:0;gap:6px;max-width:100%}
.mail-chat .mc-person-counts{flex:none;white-space:nowrap;font-size:11px;color:#79899f}
.mail-chat .mc-person-tags{display:block;flex:1;min-width:0;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;line-height:20px;color:#6482b2;cursor:help}
.mail-chat .mc-person-tag{display:inline;padding:2px 5px;margin-right:4px;border:1px solid #d5e2fb;border-radius:5px;background:#edf3ff;color:#5476ba;font-size:10px;line-height:16px;white-space:nowrap}
.mail-chat .mc-person-tags:hover .mc-person-tag{background:#e6efff;border-color:#b8cef3}
.mail-chat .mc-person-tags-unavailable{color:#94a3b8;font-size:10px}
```

DOM映射全集：宿主panel/list/sidebar/pager=S-1，姓名行与单行专家标签=S-7；search/filter/label/input/select/count/error=S-2；header/meta/manage/tag-editor=S-3；article/body/translation/tags/footer/attachment/error=S-4；details/manual/tools/editor/logs=S-5。既有共享组件内部样式按源码复用，不授权修改。`.mc-*`只被mailbox-chat.js/css使用；styles.css共享button/panel/tag全局使用处不修改，只以mail-chat后代派生。

## 现状审计

共同审计[mailbox-refinement-audit.md](mailbox-refinement-audit.md) D1..D6、X1..X8以及[逐字基线](mailbox-refinement-evidence/frontend-before.md)属于本节。app.js:14352/14359/13890/13930是过滤与宿主接点；mailbox-chat:653/690是应删extras与thread读取，:751设置在scroll，:415/463/1698为窗口覆盖/选专家/更早消息晚响应。旧标签editor依赖detailContext及固定id，不可复制进每封卡片。

## 实现方案

### T1 宿主/筛选/列表（I-1/I-2/I-7，S-1/S-2/S-6）

文件：index.html、app.js、mailbox-chat.js、mailbox-chat.css、mailboxChatBehavior.test.js、mailboxChatStyle.test.js。

1. 按S-1/S-2迁移唯一DOM节点；chat下account/direction/tag change只记筛选草稿，原非chat模式仍立即刷新。账号options由原初始化入口填充；重复mount不重复绑定，unmount还原父节点/顺序/文案/选项。
2. `mailboxChatFilterSnapshot`新增recipientEmail/keyword映射01接口，不再keyword→subject；label用真实标签。applyOptions中filters完整替换，清空值也传递；q独立。组件tab初次可用外部onlyPending初始化，用户操作后tab为唯一权威，防每次app刷新把all切回pending。
3. 全部保留显式高级筛选但取消followed/pendingOnly；重置清空高级筛选并立即查询、保持tab/q；关闭未应用恢复已生效字段值。日期非法不发请求，显示“开始日期不能晚于结束日期”。
4. mark成功重新取当前页与summary；空页回退最后有效页。当前专家因处理完成移出pending列表时，右侧现有会话/草稿保留至主动切换；不得因membership变化立即清空编辑器。只有明确切换搜索/过滤使目标不可用才展示说明，先保存草稿。
5. 外部focus新的contactId必须响应，即便已有selectedContactId；不存在/无权限给明确空态，不伪造专家。不用前端sort修补SQL分页。

### T2 管理/翻译/标签（I-3/I-4/I-6/I-7，S-3/S-4/S-6）

文件：app.js、mailbox-chat.js、mailbox-chat.css、三个前端测试文件。

1. renderSettingsBlock替换为头部摘要和独立overlay。overlay必须portal到body（生产.panel有backdrop-filter，会改变fixed定位包含块），单独委托动作并在unmount移除/解绑；保存查询select、标签loading/refetch必须改为显式管理portal根节点（原conversationBody查询不到portal），动作统一捕获当前contactId；开启读真实status/level/tags，取消status/level零请求；标签沿现有fetchExpertTagsFromEs/mutateExpertTag即时写并刷新头部。
2. saveSettings只请求变化字段，原status与level两接口分别收集结果并回读；失败显示具体错误/“部分变更未保存”，按钮恢复，不引入假事务。切专家关modal，回包校验原contactId。
3. 为原标签modal增加可选宿主adapter `{inboundId, source, contactId, onTagsChanged}`。提交前捕获adapter上下文；新chat目标不从旧detailContext/selectedId兜底。关闭/成功/unmount清理adapter；无adapter时旧来信汇总/详情沿原分支。callback传服务器POST返回tags，不依赖固定#mailboxInboundTagEditor。
4. DELETE成功移除指定tagId，局部更新消息缓存，再静默校验窗口tags；失败保留原chips并显示错误。新增/删除后刷新真实标签选项及服务器列表membership。不能在一个页面重复生成旧editor固定id。
5. 删除原renderExtrasBlock和mail-extras toggle标签装载；初次tags直接来自01响应，不按每封GET thread。发件不得以source_inbound_id尝试加标签。
6. 翻译由mc专属action调用既有/api/translate，不同时绑定全局btn-translate避免双请求。缓存key含message source:id与正文；仅变对应卡片，保留editor节点identity。译文escape，不处理HTML。

### T3 缓存/滚动/异步（I-4/I-5/I-7，S-1/S-4/S-5/S-6）

文件：mailbox-chat.js、app.js、mailboxChatBehavior.test.js。

1. 按审计D6建立有界模块Map。每次scroll、切专家前、unmount前、加载更早后保存窗口和锚点；缓存不是只有scrollTop。用户变化/登出清空；超过10会话LRU淘汰，单会话超500消息不再保存、下次进入取最新窗口。此上限不截断当前屏已加载数据。
2. 复进先恢复缓存窗口/草稿，再静默查最新窗口并按source:id合并，保留已加载历史和原keyset；新窗口中的相同key用服务器新状态/tags覆盖。render后下一帧恢复锚点，0有效；锚点缺失用同窗口fallback scrollTop，窗口不可用时定位最新。禁止自动循环取完整历史找锚点。
3. 每个select/quiet/older/translate/tag回调捕获session/contact/account/seq；loadOlder补seq校验和busy防重复。插入更早前后用首个可见消息与相对位置恢复，偏差≤2px；不调用整页scrollIntoView。
4. 无缓存首次focus须等真实消息和sections渲染后定位最后一封顶部8px（夹在可滚动范围内），极长邮件也从头显示。最新按钮不标记处理、不发送。
5. 原refreshConversationQuiet不再把旧已加载窗口直接丢掉；普通刷新守住用户位置与editor。最新来信变化仍走原提示/确认/切回复目标逻辑，不因列表排序变化替换draft targetKey或QA快照。
6. app切出mailbox前保证unmount能保存；同标签页返回可恢复，浏览器整页刷新按无缓存处理。本轮不加入持久化邮箱正文缓存。

### T4 自动检查与真实渲染（I-1..I-8，S-1..S-7）

文件：三份测试。Style读取真实index文件检查唯一id，不能仅依赖getElementById总返回stub；CSS与S-6逐字一致。Behavior检查A慢响应晚于B选中、≥60消息历史、all/pending切换、filter清空、标签同数字不同来源、草稿节点不被替换。旧mailboxInboundTags测试覆盖无adapter路径与adapter清理。

在隔离验收环境运行生产index/app/mailbox-chat，记录1440×900、1920×1080、760/390宽实际浏览器截图。比较目标局部布局与原生产壳；不可只跑独立mock预览后声称生产UI验证通过。不新建前端框架或测试依赖。

### T5 专家标签行（I-1/I-6/I-8，S-1/S-6/S-7）

文件：mailbox-chat.js、mailbox-chat.css、mailboxChatBehavior.test.js、mailboxChatStyle.test.js（原7文件白名单不变）。

1. renderPerson按S-7渲染01.expertTags，使用已有global.expertTagLabels显示名，未知值原样escape。不得从右侧选中专家的缓存拿标签填所有行。
2. 移除列表pending badge，姓名行也不显示；收发数后只放专家标签。移除waiting相关分支，不改变全部/关注/待处理tab或服务器排序。
3. 加删专家标签成功后静默刷新会话summary及当前行/头部标签；切换专家后晚响应按contactId守卫，列表仍保持服务器顺序。邮件标签修改不能改专家标签行。
4. Style断言nowrap/min-width:0/ellipsis、counts flex:none及title完整名称；Behavior覆盖0/1/10标签、长中文/英文单标签、带引号与尖括号标签、null/[]差异、标签改动跨入口读取。真实浏览器窄左栏/大字体下检查行高、截图与悬停完整内容。

## 变更文件清单

| # | 精确路径 | 用途 |
|---|---|---|
| 1 | src/main/resources/static/index.html | 唯一筛选节点、宿主标识；缓存键留03 |
| 2 | src/main/resources/static/app.js | 宿主/filter/modal adapter/身份清理 |
| 3 | src/main/resources/static/mailbox-chat.js | 布局、局部操作、上下文缓存 |
| 4 | src/main/resources/static/mailbox-chat.css | S-6全文 |
| 5 | src/test/js/mailboxChatBehavior.test.js | 异步/分页/状态/请求行为 |
| 6 | src/test/js/mailboxChatStyle.test.js | DOM/CSS契约 |
| 7 | src/test/js/mailboxInboundTags.test.js | 标签新旧宿主兼容 |

## 验收标准

- I-8/S-7：列表专家标签来源隔离；0/1/10及超长标签均单行，收发数字完整；title包含全部显示名，HTML正确escape；管理后刷新同步，邮件标签操作不污染。标签行20px，增加标签不改变卡片高度；renderPerson输出不含waiting/pending badge，pendingCount>0仍受tab与排序规则约束。

- I-1：三tab参数无waitingReply，all无pendingOnly/followed；不sort专家；mark后重查得到01顺序。
- I-2：id唯一、关闭/应用/重置快照准确、任务钻取恢复、动态账号选项保留；旧类别不能被发为label。
- I-3：无每封thread读取，POST回包直显，source:id隔离，失败/晚响应不污染；旧两个标签入口无adapter残留。
- I-4：点一次翻译一次请求、再开零请求、错误可重试；空正文处理；editor identity与文本保留。
- I-5：首次最新top≈8px，0恢复，>50条缓存窗口不丢，加载更早锚点偏差≤2px；A晚响应不写B，普通刷新不跳底。
- I-6：overlay不在scroll；取消status/level零请求；标签即时提示；部分失败显示真实结果，缺画像不可用。
- I-7：原LIVE_INBOUND/目标/账号/QA payload/共享资料/旧任务路径回归通过。
- S-1..S-7：CSS与合同全文一致，新结构逐一映射；无新增inline style/全局规则；尺寸颜色不得随意改，只允许平台字体抗锯齿及真实文本换行差异。样式调整必须先修订合同。

```sh
node --check src/main/resources/static/mailbox-chat.js
node --check src/main/resources/static/app.js
node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxInboundTags.test.js
node --test src/test/js/*.test.js
```

## 人工验收清单

### A-1: 排序与tab
- 前置条件：01的四专家矩阵，均已关注，使用隔离环境。
- 操作步骤：1 全部；2 关注；3 待处理；4 再全部；5 处理D最后待处理信。
- 预期结果：仅3tab；全部D,A,B,C；关注B,D,A,C；待处理D,A；再全部仍D,A,B,C；处理D后A,B,D,C，草稿不清空；给C发件仍C最后。
- 覆盖：R1/R4、I-1/I-3、S-1/S-2；X1/X2/X4。

### A-2: 筛选
- 前置条件：A正文meeting-z9，有“会议安排”标签；无生效筛选。
- 操作步骤：1 ⋯；2 填账号/关键词/标签并应用；3 改日期后关闭不应用；4 重置；5 开始晚于结束应用。
- 预期结果：默认无顶部筛选块且按钮只有⋯；展开白底430px两列，关键词跨两列；应用匹配A、角标3；未应用不改结果；重置保留tab/q；非法日期提示且不查询。
- 覆盖：R1、I-2、S-2/S-6；X3。

### A-3: 管理保存
- 前置条件：真实画像测试专家与缺画像专家各一；隔离环境能模拟一次保存失败。
- 操作步骤：1 滚历史；2 管理；3 改状态/层级取消；4 改后保存；5 加专家标签“会议测试”；6 专家列表详情回读；7 模拟部分失败。
- 预期结果：管理不在邮件区；480px居中、两列，保存非整行；取消状态层级不变；保存头部/详情一致；标签即时保存提示可见；失败不报全成功；缺画像不可加tag。
- 覆盖：R2、P6、I-6、S-3；X5。

### A-4: 邮件翻译/标签
- 前置条件：英文已处理来信、待处理来信、发件各一。
- 操作步骤：1 翻译；2 收起再开；3 给已处理来信加QA和自定义标签；4 刷新页面；5 删custom标签；6 来信汇总回读。
- 预期结果：无原文/技术折叠；译文原位、二次不请求；标签刷新保留、删除跨入口同步；发件无加标签/标记按钮；人工草稿不变。
- 覆盖：R3、P6、I-3/I-4、S-4；X3。

### A-5: 首次进入
- 前置条件：刷新浏览器清缓存；专家≥60封来往、最后一封很长。
- 操作步骤：1 从专家列表查看邮件；2 观察初始位置；3 最新消息。
- 预期结果：最新窗口最后一封顶部约8px，不跳编辑器底；不标记处理；主题为可读值。
- 覆盖：R4、I-4/I-5、S-1/S-4；X6。

### A-6: 缓存与竞态
- 前置条件：A/B各≥60封，A网络可限速。
- 操作步骤：1 A加载更早停旧邮件并写草稿；2 B再A；3 切专家列表再回A；4 普通刷新；5 A更早请求未完切B；6 A到顶部0后切换回来。
- 预期结果：2/3/4同邮件同位置、草稿保留；5不混入A邮件；6仍为0；不会自动拉完远端历史。
- 覆盖：R4、P3、I-5/I-7、S-1/S-5；X6/X7。

### A-7: 工作台与发送
- 前置条件：隔离专家有来信，发件仅测试邮箱。
- 操作步骤：1 进入会话；2 展开工作台生成采用；3 编辑富文本；4 导入新来信刷新；5 确认/不确认切目标分别验证；6 测试发送。
- 预期结果：初次工作台关闭、人工打开；采用不自动发送；新来信不覆盖编辑；目标确认后准确；测试邮箱收到最终正文，原账号/QA审计存在。
- 覆盖：R5、P3、I-7、S-5；X7。

### A-8: 全局/关注/资料
- 前置条件：未访问专家列表直进mailbox；两个用户；测试专家40份仅元信息资料。
- 操作步骤：1 核对导航、登录、刷新/检查回复/批量发送/自动回复；2 用户一关注，用户二查看；3 开材料；4 展开邮件文件名；5 仅选一个文件获取。
- 预期结果：全局按钮有真实状态不永久加载；关注隔离；原共享资料组件；前两次查看无40份内容下载，第5步只有所选文件获取。
- 覆盖：P1/P2/P4、I-7、S-1/S-5；X4。

### A-9: 任务钻取
- 前置条件：测试任务有邮件记录。
- 操作步骤：1 任务记录钻取；2 旧方向/日期/邮箱过滤；3 清任务过滤回聊天；4 打开⋯。
- 预期结果：旧列表仍按任务过滤；返回聊天字段唯一、账号齐全、无重复请求；全局入口保留。
- 覆盖：P5、I-2/I-7、S-1/S-2。

### A-10: 视觉逐项检查
- 前置条件：隔离生产源码页面，1440×900/1920×1080/760/390px宽，默认浏览器缩放。
- 操作步骤：1 对照S-6检查尺寸字号；2 hover/active/disabled/键盘焦点；3 开管理/筛选；4 展开译文/人工；5 窄屏滚动。
- 预期结果：桌面左306px、gap16px；≤1100左275/gap12；正文13px/1.85，卡片圆角11px；管理480px白底、保存高32px；筛选430px不透字不裁切；窄屏无横向溢出；管理始终不在消息区。
- 覆盖：R1..R6、S-1..S-7、I-6/I-7。

### A-11: 专家标签单行及悬停
- 前置条件：测试专家A在专家管理添加10个专家标签（包含长中文名、超过60字符的英文名、引号/尖括号文本），B无标签，C读取标签失败；A另有一封待处理邮件和独立邮件标签“会议安排”。
- 操作步骤：1 查看左侧A/B/C卡片；2 在A标签区域悬停；3 缩窄到275px左栏；4 键盘Tab聚焦A；5 在管理中删除一标签并刷新；6 给来信加邮件标签。
- 预期结果：原待专家回复区域为专家标签；收发数量完整，标签只一行20px、过长出现省略且卡片不增高；悬停显示完整专家标签名称；键盘可读完整名称；B无标签占位，C显示“标签暂不可用”；A卡片及姓名行均无待处理标记，但仍出现在待处理tab且在全部tab优先；删除专家标签后列表同步，邮件标签不会出现在此处。三个tab及排序不变。
- 覆盖：R6、I-1/I-8、S-7；X8。
