# 03：草稿入口、自动保存与跨设备恢复

状态：待评审，未实施。研究日期：2026-10-08。代码基线：`7c86599f85f6462e00a3fcd2c5a74f1ca57f013d`。

本文中的“现状”有仓库证据；表结构、API、800ms 防抖、1MiB 文本上限为本计划提出的设计值，不能当作已经存在的功能。证据目录：[源码摘录](mailbox-server-drafts-evidence/source-excerpts.md)、[文件 SHA256](mailbox-server-drafts-evidence/source-manifest.json)。

前置：01/02 已实施并独立验证。不得让本步 UI 接入只有存储、没有发送事务关闭的后端。

## 需求描述

O-1：收发件箱增加“草稿”入口和专家卡片草稿标记，点击恢复保存的真实回复目标、主题、富文本及可恢复上下文。
O-2：编辑后自动存服务器，明确显示保存中/成功/失败；刷新、切专家、切账号、退出再登录、换设备可恢复已确认保存的内容。
O-3：明确放弃才删除；成功发送只关闭对应版本，失败/未知/取消确认/新来信/空稿不清除。

必须保留 N-1：现有布局、邮件筛选/分页、移动端列表↔会话操作、关注/挂起/处理状态。N-2：原编辑工具、模板、QA/RAG、会议、附件、发送安全确认与线程语义。

不做：新富文本编辑器、历史版本、自动冲突合并、localStorage/IndexedDB 持久化兜底、新弹窗框架、独立草稿页面、迁移静态演示数据。

## 关键不变量

### Invariant I-1: 服务器是真值
- Rule: Map 只作当前编辑缓存；不写 localStorage/IndexedDB。GET 成功之前不放开该目标编辑，失败显示“草稿加载失败，请重试”并禁发，不能把加载失败当无稿。只有服务器确认当前本地序号对应的版本才显示“已保存到服务器”。
- Applies to: 进入会话/草稿、恢复、自动保存、列表计数
- Violation consequence: 把空表单覆盖服务器稿，或向用户虚报已保存。
- 来源: original

### Invariant I-2: 快照与异步归属
- Rule: 持久化所有内容写点：输入、QA采用、模板/跟进、requestId、会议写入/移除/失效、附件上传状态/移除；每次捕获 username、规范target、draftId、version、localSeq、快照。串行每目标请求，旧响应只能确认旧序号，不能覆盖新输入。dirty/inflight 状态独立于会话 LRU，不能随其被淘汰；用户身份变化立即停止旧owner任务派发。
- Applies to: setDraft 和直接 Map 写点、promise 回调、unmount、session change
- Violation consequence: 串稿、迟到响应覆盖新输入、缓存淘汰丢稿。
- 来源: K-mailbox-draft-cache-owner-capture

### Invariant I-3: 保存与离开
- Rule: 用户真实修改后800ms防抖；首次纯浏览默认主题不建稿；已有稿清空仍 PUT。切专家/目标/scope/视图前捕获并 flush；主动退出登录必须 await flush 后才 logout。保存失败留在编辑界面，显示失败并可重试；标签页关闭/刷新有未确认输入时使用 beforeunload 浏览器提示，不声称断网强关仍可保住未上传字节。
- Applies to: 输入事件、selectExpert/saveBeforeScopeChange/applyOptions、setView/logout/unmount
- Violation consequence: 最后输入未落服务器却无提示消失。
- 来源: original

### Invariant I-4: 版本冲突不盲重试
- Rule: 409 保留本地输入，显示“草稿已在其他窗口更新，请先处理冲突”，禁止发送/自动覆盖；只提供显式“重新加载”并确认丢弃本地未保存修改。若重读服务器快照与本次已提交快照相同，仅认领其ACK，不再覆盖保存。终态不自动 reopen；只有加载终态后用户新一次真实编辑才允许显式重开。
- Applies to: PUT失败、并发窗口、响应丢失、reopen
- Violation consequence: 覆盖另一设备内容、自动复活已发送/放弃稿。
- 来源: original

### Invariant I-5: 目标与内容完整恢复
- Rule: 草稿列表按draftId选择，恢复保存的scope/processingId/跟进anchor，不用latestInbound替换。富文本在INBOUND/OUTBOUND都走现有sanitizeDraftHtml；qa/meeting/requestId/附件引用保留。服务端上传完成项重建下载地址；未完成项显示失败要求重新选择；重置附件本地key序列防碰撞。新来信仅提示，可显式新开目标稿；原稿不自动删，目标已存在稿时不覆盖。
- Applies to: 草稿打开、manualComposeHtml、retargetManual、上传恢复
- Violation consequence: 恢复为纯文本、发错来信、会议附件丢失、覆盖目标已有稿。
- 来源: original

### Invariant I-6: 发送前保存，发送后重读
- Rule: 发送前生成原需要的requestId并落库，flush确认本次快照后才携带draftRef发原API。异步捕获发送版，成功/失败/离开后的结果均以服务器详情重读核实；取消原成功处理中的无条件Map删除。发送期间更高localSeq即使尚未落库也不能被清除；终态后该明确的新输入需要保留、提示并经显式继续编辑重开保存。
- Applies to: sendManualReply、host布尔适配器返回、finalize后刷新
- Violation consequence: 发的是A存的是B、发送成功删除新输入、HTTP异常复活旧稿。
- 来源: original

### Invariant I-7: 仅明确放弃
- Rule: 点击“放弃草稿”→原确认框→DELETE expectedVersion；确认前、取消、DELETE失败均保留。服务器返回终态后再移除列表/缓存；发送中的放弃禁用，UNKNOWN 放弃必须说明不能撤回可能已发送的邮件。收到新来信、unmount、搜索/筛选不调用 DELETE。
- Applies to: 放弃按钮、retarget、deleteDraft/sessionStore清理
- Violation consequence: 未经同意清稿。
- 来源: original

### Invariant I-8: 计数与隔离
- Rule: 草稿列表用01服务端分页/total；普通专家列表只批量读取当前页contactIds的summary，不本地过滤替代原分页。草稿tab只按账号与搜索筛选，明确提示；用户名变化清掉可见旧稿，旧响应不得渲染到新用户。
- Applies to: loadList/fetchList/renderPerson/草稿入口/session change
- Violation consequence: 数量错误、泄露其他用户稿、破坏原列表。
- 来源: original

## 样式契约

新增 CSS 只追加到 `src/main/resources/static/styles.css`，下列全文为执行合同。禁止 inline style、未声明新 class、改动原 mailbox-chat.css 规则。现有 class 的全使用点在 [frontend-class-use.txt](mailbox-server-drafts-evidence/frontend-class-use.txt)；本步仅派生新 class，不全局改写已有规则。（来源: K-mailbox-chat-css-byte-contract，已按实际测试纠正）

### S-1：草稿筛选与专家标记
- 复用：`.mc-filter` 基础规则在 mailbox-chat.css:10～14/:86～88，最终覆盖在 styles.css:12823～12829；`.mc-filters` 最终换行覆盖在 styles.css:12648；`.mc-person-meta` :23。新增“草稿”在“全部”之后，原按钮顺序不变。
- 新增（逐字复制）：

```css
.mail-chat .mailbox-draft-count{display:inline-flex;align-items:center;margin-left:4px;padding:0 5px;border:1px solid #bfdbfe;border-radius:5px;background:#eff6ff;color:#1e40af;font-size:10px;line-height:16px;white-space:nowrap}
```

- DOM：既有 `.mc-filters` 不改 class 或样式；计数为0也在草稿tab显示0，普通专家卡无稿时不渲染标记。

```html
<div class="mc-filters">
  <!-- 原“全部”按钮 -->
  <button class="mc-filter" type="button" data-action="mc-filter" data-chip="drafts" aria-pressed="false">草稿<span class="mailbox-draft-count" data-role="draft-total">0</span></button>
  <!-- 原其他按钮 -->
</div>
<!-- 原专家卡 .mc-person-meta 内追加，有稿时才渲染 -->
<span class="mailbox-draft-count" data-role="contact-draft-count">草稿 1</span>
```

### S-2：草稿列表
- 复用：`.mc-expert-list` :15、`.mc-person` :16～18、`.mc-person-main` :19～22、`.mc-pager` :33、`.mc-empty` :58、`.mc-note` :57，均在 mailbox-chat.css。保持原容器与分页器，只切换数据源。
- 新增（逐字复制）：

```css
.mail-chat .mc-person.mailbox-draft-card{grid-template-columns:minmax(0,1fr)}
```

- DOM：所有动态文本 escapeText，data-draft-id 为服务端数字ID，选中状态用原 data-active。卡片按“专家→主题→纯文本预览→目标/保存时间”显示；无主题显示“无主题”，无正文显示“空白草稿”。

```html
<div class="mc-note" data-role="draft-scope-note">草稿仅按账号和搜索条件筛选</div>
<div class="mc-person mailbox-draft-card" data-active="false" data-draft-id="123">
  <button class="mc-person-main" type="button" data-action="mc-open-draft" data-draft-id="123">
    <strong>测试专家</strong>
    <small data-role="draft-subject">草稿验收主题</small>
    <small data-role="draft-preview">正文预览</small>
    <small data-role="draft-target">回复来信 #456 · 账号 A</small>
    <small data-role="draft-updated">保存于 10-08 15:30</small>
  </button>
</div>
<!-- 空列表/加载失败复用现有 mc-empty/mc-error 文案容器 -->
```

- 草稿摘要纯文本≤120字符，保存时间由返回时间按当前浏览器时区格式化，tooltip可显示完整时间；不存在专家显示“原专家已不存在”，点开仍能看稿/放弃。

### S-3：编辑器保存状态与操作
- 复用：`.mc-compose` :60、`.mc-editor` :64/:130、`.mc-compose-footer` :65、`.mc-text-button` :109～112；`.button` / `.button.danger` 在 styles.css:802 / :862；禁用与focus由 mailbox-chat.css:67～68 控制。原主题、富文本工具、附件和发送按钮 DOM 不重排。移动端继承 styles.css:12565～12576 的按钮至少44px、输入/编辑字号16px与编辑区160px～40dvh规则，不用桌面数值覆盖。
- 新增（逐字复制）：

```css
.mail-chat .mailbox-draft-status-row{display:flex;align-items:center;flex-wrap:wrap;gap:8px;min-height:32px}
.mail-chat .mailbox-draft-status{flex:1;min-width:0;color:#64748b;font-size:11px;line-height:1.6;overflow-wrap:anywhere}
.mail-chat .mailbox-draft-status[data-state=saved]{color:#059669}
.mail-chat .mailbox-draft-status[data-state=error],.mail-chat .mailbox-draft-status[data-state=conflict]{color:#be123c}
.mail-chat .mailbox-draft-status[data-state=saving],.mail-chat .mailbox-draft-status[data-state=dirty]{color:#b45309}
```

- DOM：插入在既有附件区之后、`.mc-compose-footer` 之前；按钮显隐用hidden、禁用用disabled，不新增style。

```html
<div class="mailbox-draft-status-row">
  <span class="mailbox-draft-status" data-role="draft-save-status" data-state="saved" role="status" aria-live="polite">已保存到服务器</span>
  <button class="mc-text-button" type="button" data-action="mc-retry-draft" hidden>重试保存</button>
  <button class="mc-text-button" type="button" data-action="mc-reload-draft" hidden>重新加载</button>
  <button class="button" type="button" data-action="mc-continue-draft" hidden>继续编辑并保存</button>
  <button class="button danger" type="button" data-action="mc-discard-draft">放弃草稿</button>
</div>
```

状态实文案：`尚未保存`、`正在保存…`、`已保存到服务器`、`保存失败，内容尚未同步到服务器`、`草稿已在其他窗口更新，请先处理冲突`。发送未知另用现有 `.mc-note` 显示“发送结果待确认，草稿已保留，请勿重复发送”；不得把保存成功显示成邮件发送成功。

### S-4：恢复失败、目标失效与确认框
- 无新增 CSS。复用 `.mc-note` / `.mc-error` / `.button`，及 `index.html:2283` 的 `#actionDialog` 原骨架、`app.js:17882` 的 openActionDialog("confirm")。仅传固定确认文字或已 escapeText 的文本，不把稿件 HTML 注入 options.message；不改共享弹窗样式或事件清理。
- 恢复失败 DOM：

```html
<div class="mc-error" data-role="draft-load-error" role="alert">草稿加载失败，请重试</div>
<button class="button" type="button" data-action="mc-reload-draft">重试加载</button>
```

- 目标失效 DOM：

```html
<div class="mc-note" data-role="draft-target-warning">原专家已不存在，草稿仍为你保留，可查看或放弃。</div>
```

- 仅有新来信时同一 note 容器显示“有新来信，当前仍在编辑原来信草稿”，保留原目标；原 retarget 操作先确认。新目标已有稿时给出复用 `.button` 的“打开已有草稿”按钮（data-action="mc-open-existing-draft"），不提供覆盖按钮。
- 放弃确认文字：“确定放弃这份草稿？放弃后无法恢复。”；UNKNOWN 增加“这不会撤回可能已发送的邮件。”。重新加载冲突确认：“重新加载将丢弃本窗口尚未保存的修改，是否继续？”；新目标复制确认：“为新来信另建草稿，原草稿会保留，是否继续？”；确认/取消复用已有按钮，不创建第二个 dialog。

## 现状审计

### sessionStore / drafts Map（将降为编辑缓存）
- Schema：`mailbox-chat.js:550` 会话key=user|accountScope|contactId；`:584` drafts 为 Map；`:628` LRU 上限10。目标key在`:4790`，入站contactId:processingId:accountCode，出站contactId:OUTBOUND:scope。
- 写路径：setDraft(:853)、deleteDraft(:859)；saveDraftFromInputs(:5979)；requestId生成(:6060)；附件直接 Map 写点(:5717/:5729)；会议写入(:7641)、移除(:7748)；发送成功删除(:7984/:8008)；retarget复制+删除(:8140/:8141)；会话保存/恢复/淘汰与unmount。调用清单见 [draft-map-paths](mailbox-server-drafts-evidence/draft-map-paths.txt)，不能只替换 setDraft。（来源: K-mailbox-draft-cache-owner-capture）
- 读路径：manualComposeHtml(:4910)、manual target恢复、sendManualReply(:7835)、outboundAttachmentDraftOf(:5594)、会议卡与send availability、异步owner捕获、refreshConversationQuiet/retarget。getDraft/Map引用完整位置在同收据。
- IP-1：全部内容写点→自动保存→服务器恢复；IP-2：上传回调/发送回调→用户已切专家或LRU淘汰；IP-3：新来信目标重定向→旧/目标草稿共存。

### 发送与页面生命周期
- app.js:5031 setView 同步调用 unmount；原注释明确称草稿内存态销毁清空。:18577 logout 当前直接请求注销并reload。:18633 unmountMailboxChatHosts 管理聊天mount；mailbox-chat.js:9027 导出API目前只有mount/unmount/isMounted/version。
- app.js:18779/18785 两个 host 发送适配器统一调用 submitManualRichReply，返回 Boolean；安全取消/失败返回false。保留该契约，其余调用不必升级。新UI在返回后调用草稿GET确认终态。
- IP-4：保存flush→导航/注销；IP-5：01/02服务端version/SENT→UI状态与计数；IP-6：原模板/会议/附件写点→刷新恢复→现有发送API。

### 富文本与附件
- manualComposeHtml 目前只在 ui=meetingEnabled&&!isOutbound 条件下恢复 HTML；否则仅text。meetingRestoreEditorHtml(:4841) 已调用 meeting-confirmation.js:245 的白名单清洗器；允许 div/p/br/b/strong/i/em/u/ul/ol/li/a/span，剔除脚本/事件与不安全链接。无需引入新富文本库。
- uploadOutboundFile(:5819) 上传中暂存file；完成后替换为不含File的ready项，字段为key/state/filename/contentType/byteLength/sha256/id/downloadUrl；failOutboundItem(:5890)含error。恢复不能序列化file或复用blob链接；本地of-N计数需推进。

### 前端样式盘点
- 可复用 class与完整规则原文：见 [source-excerpts.md](mailbox-server-drafts-evidence/source-excerpts.md) 的 mailbox-chat.css 全文摘录和 styles.css:801～890；DOM基线逐字摘录为 mailbox-chat.js:1117～1172（筛选/列表骨架）、:4910～4998（编辑器）。以下节选同源码：

```html
<div class="mc-filters">
    ${chipButtons}
</div>
<div class="mc-filter-summary" hidden></div>
```

```css
.mail-chat .mc-compose{display:flex;flex-direction:column;gap:10px;min-width:0}
.mail-chat .mc-compose-footer{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px}
.mail-chat .button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
```

- token实值：主色#1e40af、hover#1e3a8a、active#172554；正文#475569；编辑正文12px/1.8；输入32px、border#dce4ef、radius7px；按钮32px、padding0 12px、gap6px、font12px/500、radius7px；按钮hover shadow0 2px 6px rgba(15,23,42,.08)，active scale(.97)/opacity.85；mc禁用opacity.45，无transform/shadow；focus2px #3b82f6 offset2px。筛选最终覆盖色#3762d8、底边2px #3762d8、gap4px；styles.css:12648 的高优先级规则使 filters 为wrap/overflow-x:visible/row-gap:0，不能只读mailbox-chat.css便推断最终外观。--panel-bg 是rgba(255,255,255,.55)，本步不用它新建弹层。（来源: K-panel-bg-token-is-translucent）
- class注册规则：实际 mailboxChatStyle.test.js 检查 mc-*在mailbox-chat.css，其他类在styles.css，无新增inline style。该文件旧注释中的target.css全字节比较已不存在，不据此修改历史target文件。
- 层叠补充原文：见 [additional-source-excerpts.md](mailbox-server-drafts-evidence/additional-source-excerpts.md) 中 styles.css:12547～12582、:12646～12657、:12823～12831；上述高优先级/移动规则是实际复用基线。
- cache：当前index全部带?v资源共享`20261006-wecom-inbound-notification`，检索测试无字面键命中，见 [asset-keys.json](mailbox-server-drafts-evidence/asset-keys.json)。执行时重取，统一更新11处资源键，保持相等，不新加脚本。（来源: K-frontend-cache-key-triad、K-dom-stub-tests-hide-dangling-refs）

## 实现方案

### T-1：服务器草稿缓存与保存协调（I-1～I-4/I-7，S-3/S-4）
文件：`src/main/resources/static/mailbox-chat.js`。

在现有模块内增加一个按真实user+规范target索引的保存协调Map；不独立建草稿框架。内容缓存、pending操作不可只挂会被LRU移除的conversation record。INBOUND的服务器目标按真实来信账号，不按页面“全部/账号”筛选分裂；两个会话缓存命中同一目标必须映射到同一个draftId/version。

统一dirty标记入口，接入审计中每个写点；附件捕获owner的异步更新直接传捕获身份，不能运行时重新取currentTargetKey。还在上传的条目先存描述，上传完成再次保存ready；离开mount后仍由协调Map保存该owner完成结果，不重绘新专家；账号注销/变更后不再发旧owner请求。

每目标最多一个PUT在途，后续修改合并为下一次快照；localSeq不是数据库version，两者独立。ACK仅推进确认序号，新的本地编辑保持dirty。普通网络失败停止重试风暴，保留内容，显示重试；网络恢复可重试相同快照，但409必须走冲突处理。加载远程失败不自动新建空稿。未完成稿允许空主题/正文保存，发送仍受原验证。

### T-2：导航与登录生命周期（I-1～I-4/I-8，S-3/S-4）
文件：`mailbox-chat.js`、`app.js`。

在 MailboxChat 现有export扩展 `flushDrafts(host)` 与 `hasPendingDrafts(host)`；flush必须先同步采集当前DOM，再等待当前owner保存链（包括requestId和已开始的附件上传状态落定）。失败reject且保留输入，不把错误吞掉。对无mount返回已完成promise，兼容非收发件箱。

- 内部selectExpert、scope变更、retarget先flush；请求序号确保快速多次切换只应用最后一次目标。
- app.js `setView` 离开mailbox前调用flush；保留现有同步切页主体为内部函数，只在确有未保存稿时延迟执行，其他视图零新增异步语义。用导航序号防旧flush完成后跳回过期目标。
- 主动logout先flush成功再调用原logout/reload；失败不触发logout，不把finally reload留在flush异常外层。
- unmount先采集并flush；保存协调Map不随DOM/会话缓存销毁。无法await的既有unmount调用仍不能取消持久化请求；正常路由出口由上面的可等待门禁保证。
- beforeunload只在dirty/在途写/未完成上传时设置浏览器原生提示。pagehide尽力flush不当作成功证据；不用sendBeacon绕过认证/返回值，也不保证断网或操作系统强杀后的未提交输入可恢复。
- Session变更时清可见旧数据、使旧响应失效；每次实际派发校验当前登录user与捕获user一致，不能在B登录后将A的队列以B Session写入。重新登录需先GET/CAS比对，不自动重放旧owner数据。

### T-3：草稿入口与恢复（I-1/I-5/I-8，S-1/S-2/S-4）
文件：`mailbox-chat.js`、`styles.css`。

FILTER_CHIPS增加drafts，只该tab走01列表API，分页size20，搜索防抖沿用现有机制；原tab仍走原conversation list。普通卡通过一批summary查询追加标记，不改现有query含义。总数来自服务端scope下total，不取当前页长度；切tab时保存各自page，搜索变化回第0页。

草稿点击以draftId GET取回快照，再加载联系人/原消息，覆盖manual目标为保存的真实target，不能在loadMessages后被latestInbound重新覆盖。原专家/来信不存在时展示只读主题/正文（复用input readonly和contenteditable=false）及警告，可放弃，发送禁用。

manualComposeHtml无论inbound/outbound都用现有清洗器恢复html，不具备清洗器时仅显示escapeText后的纯文本并禁用富文本恢复覆盖写，提示加载失败重试，避免把HTML转纯文本后自动回存。恢复qa/meeting/anchor/附件元数据；会议账号/目标变化标stale，仍要求原预览确认；恢复附件key计数大于当前最大of-N。failed/uploading卡不允许发送。

retarget：先flush原稿；读取目标稿；已有则显示“打开已有草稿”并保留双方；不存在则用户确认后复制为新稿（新目标subject按原目标默认规则，requestId清空、会议stale），原稿不delete。发送成功与显式放弃之外的 deleteDraft 不可触发服务器DELETE。

### T-4：发送与放弃（I-2/I-4～I-7，S-3/S-4）
文件：`mailbox-chat.js`；`app.js`仅复用其原host发送/confirm函数，不重写bool合同。

发送：读取原字段→生成需要的requestId并标dirty→flush→从已确认同一快照取id/version→加draftRef→调用原host API。flush期间又编辑则重取最新快照，不发送与保存版本不同的内容。安全确认继续传同一body/ref。

返回true/false、网络断开、mount销毁均不得直接以HTTP结果删除server稿。重新GET捕获draftId：SENT且本地无更新才删除对应缓存；ACTIVE新版本恢复/保留；原binding UNKNOWN显示提醒并禁二次send。发送过程中 localSeq 更高但PUT恰好被SENT终态拒绝时，保留该新输入，显示“邮件已发送，新修改尚未保存”及“继续编辑并保存”；用户明确点击后按当前终态版本reopen，不自动丢弃或偷偷恢复原已发正文。其他窗口修改造成冲突仍走I-4。

放弃：原确认框→停止该目标后续自动保存派发并等待已在途请求→取当前ACK版本DELETE；本地还有未保存修改时不必先保存即可明确放弃，但确认应覆盖它们。版本冲突显示冲突并保留编辑器，不自动升版本再删；成功终态再清本地输入。发送中按钮disabled；unknown确认附加不能撤回文案。

### T-5：资源与测试（I-1～I-8，S-1～S-4）
文件：`index.html`与文件表的三个JS测试。

统一当前index中的资源cache key为新的本次发布值（建议`20261008-mailbox-server-drafts`，若并发发布占用则先更新计划基线）；不动资源顺序，不添加重复宿主。执行前重新grep旧字面键是否新出现于测试；只修改清单内确需改变的断言，不删除原测试以凑通过。

新增测试使用受控fetch/定时器/Promise，断言PUT/DELETE真实请求body、版本与调用次数、HTML清洗、DOM绑定位置；不是只匹配函数名的源码测试。保留现有mailboxChatStyle、Calendar、TemplateReference、Suspension测试，并增加真实浏览器验收；DOM stub的恒真getElementById不能证明页面注册成功。（来源: K-dom-stub-tests-hide-dangling-refs）

## 变更文件清单

| 序号 | 精确路径 | 改动 |
|---|---|---|
| 1 | `src/main/resources/static/mailbox-chat.js` | 原模块内草稿协调/入口/恢复/发送/放弃 |
| 2 | `src/main/resources/static/app.js` | 导航与logout等待flush，更新内存态旧注释 |
| 3 | `src/main/resources/static/styles.css` | 逐字追加S-1～S-3新CSS |
| 4 | `src/main/resources/static/index.html` | 统一资源缓存键 |
| 5 | `src/test/js/mailboxServerDrafts.test.js` | 新增请求时序/DOM/契约测试 |
| 6 | `src/test/js/mailboxChatBehavior.test.js` | 更新加载与旧内存草稿行为断言 |
| 7 | `src/test/js/mailboxOutboundAttachments.test.js` | 异步owner/持久化/恢复附件断言 |

共7文件、1前端子系统；mailbox-chat.css、meeting-confirmation.js、弹窗实现、后端均不在本步改动边界。

## 验收标准

- I-1：GET失败无PUT；服务端ACK前不显示saved；刷新在第二浏览器读取相同内容；无localStorage/IndexedDB写入。
- I-2：输入/采用QA/模板/会议/附件/请求ID各写点均有持久化行为测试；A专家上传迟到回包不改B；跨10个以上会话缓存淘汰仍能保存原稿；旧ACK不覆盖新localSeq。
- I-3：800ms防抖连续输入合并；navigation/logout等待最后PUT；失败留在当前页；新默认表单不自动建稿、已有稿清空仍ACTIVE；有pending时beforeunload触发。
- I-4：双窗口409不盲写；确认reload才丢本地；网络响应丢失但服务器快照相同可认领ACK；终态无自动reopen。
- I-5：入站/出站富文本与安全链接恢复；脚本/事件不执行；真实目标不被latestInbound覆盖；旧/新目标都有稿时双方原文保持；上传中恢复failed、key不重复。
- I-6：flush失败不send；请求ref对齐版本；成功仅移除已发版；已发送后新输入未保存仍显示并可明确重开；HTTP异常后GET已SENT不复活。
- I-7：取消放弃0 DELETE；成功1 DELETE；DELETE冲突/失败不清输入；新来信/筛选/unmount不发DELETE。
- I-8：分页第二页不因本地过滤短页；summary批量查询不N+1；用户名变化与旧响应隔离。
- S-1：CSS全文逐字、tab顺序/计数DOM匹配；原筛选按钮交互不变，复用既有wrap覆盖规则。
- S-2：卡片骨架及CSS逐字；动态内容转义；无主题/空白/失效专家文案覆盖。
- S-3：状态/按钮DOM和全文CSS逐字；saving/saved/error/conflict颜色精确；aria-live不重复刷整页。
- S-4：复用原confirm；无新增dialog、inline style或未声明class；失败/目标失效/新来信提示与既有DOM对应。
- 运行：`node --check src/main/resources/static/mailbox-chat.js`、`node --check src/main/resources/static/app.js`、`node --test src/test/js/*.test.js`；联合01/02测试均PASS后再浏览器验收。生产发布前按仓库既有构建门禁，不用静态演示替代实际服务器测试。

## 人工验收清单

### A-1: 同用户跨设备恢复
- 前置条件: 测试环境上线01/02/03；两个浏览器A/B登录同一测试用户；选择有真实来信专家。
- 操作步骤: 1. A输入主题“服务器草稿”、正文加粗“跨设备”。2. 等待“已保存到服务器”。3. 刷新A，退出再登录。4. B进入“草稿”点该稿。
- 预期结果: 两端主题均“服务器草稿”，正文保留加粗；草稿数量1；出现服务器保存文案；邮件数不增加。
- 覆盖: O-1/O-2、N-1、I-1/I-2/I-3/I-5/I-8、S-1/S-2/S-3、IP-1/IP-4

### A-2: 离线与导航
- 前置条件: A-1已保存；开发者工具Network切Offline。
- 操作步骤: 1. 修改正文。2. 等待800ms。3. 点另一导航页、退出登录。4. 尝试刷新观察浏览器离开提示并取消。5. 恢复Online点重试。
- 预期结果: 显示“保存失败，内容尚未同步到服务器”；当前输入保留，主动导航/注销未完成；刷新有浏览器原生提示；重试成功后才显示“已保存到服务器”。
- 覆盖: O-2、I-1/I-2/I-3、S-3/S-4、IP-4

### A-3: 多窗口版本冲突
- 前置条件: 两个窗口都打开同一版本草稿。
- 操作步骤: 1. A改“窗口A”并保存。2. B改“窗口B”触发保存。3. B点重新加载后取消。4. 再点并确认。
- 预期结果: B显示冲突、仍有“窗口B”，发送禁用；取消不丢输入；确认后读到“窗口A”；不会出现自动覆盖请求。
- 覆盖: O-2/O-3、I-4/I-7、S-3/S-4、IP-1/IP-5

### A-4: 切专家与迟到上传
- 前置条件: 准备11个可编辑测试会话及测试小附件；Network限速令A专家上传延迟。
- 操作步骤: 1. 在A写稿/上传附件。2. 快速切其他专家，累计超过10个会话。3. 等上传完成。4. 回A并刷新。5. 切换另一登录用户。
- 预期结果: A原文与ready附件恢复；其他专家没有A附件；缓存淘汰不丢稿；另一用户看不到A用户草稿。未完成上传重启后显示“上传未完成，请重新选择文件”，禁发送。
- 覆盖: O-2、N-2、I-2/I-3/I-5/I-8、S-3、IP-1/IP-2/IP-6

### A-5: 新来信与空稿
- 前置条件: 为专家原来信保存草稿，测试投递一封新来信；新目标也准备一份不同内容稿。
- 操作步骤: 1. 刷新会话。2. 点击原稿继续编辑。3. 操作切换新来信目标。4. 打开已有目标稿。5. 将该稿主题正文清空并保存。
- 预期结果: 原稿仍指向原processingId，出现新来信提示；新目标已有稿不被覆盖；两稿都在草稿列表；清空后仍有“空白草稿”。
- 覆盖: O-1/O-3、I-5/I-7/I-8、S-2/S-4、IP-3

### A-6: 明确放弃
- 前置条件: 存在已保存草稿及未保存新修改，草稿total已记录。
- 操作步骤: 1. 点放弃后取消。2. 再点并确认。3. 刷新两浏览器。4. 用开发者工具重放放弃前的旧PUT。
- 预期结果: 取消时内容/计数不变；确认后total减少1；两端无该有效稿；旧PUT为409，草稿不复活。
- 覆盖: O-3、I-2/I-4/I-7、S-3/S-4、IP-5

### A-7: 发送前保存与新版本
- 前置条件: 测试SMTP可暂停及失败；正文“待发送版本”已保存。
- 操作步骤: 1. 断网点击发送。2. 恢复后发信，SMTP暂停期间改“发送中的新版本”。3. 放行SMTP成功。4. 刷新/另一浏览器重读。5. 对另一稿模拟失败/UNKNOWN和取消安全确认。
- 预期结果: 断网不调用发送API；成功只发送原快照；已保存的新版本仍在；若新输入尚未保存，保留编辑器并提示“邮件已发送，新修改尚未保存”，明确继续后可保存；失败/UNKNOWN/取消的稿均保留，UNKNOWN禁重复发送。
- 覆盖: O-3、N-2、I-3/I-4/I-6/I-7、S-3/S-4、IP-2/IP-5

### A-8: 会议、模板、QA、附件与视觉回归
- 前置条件: 有已预览会议、RAG证据、通用附件、无来信但有成功发件的专家；桌面1440px和手机393px视口。
- 操作步骤: 1. 应用模板/会议、选择QA证据、上传附件，保存并刷新。2. 分别发送入站、出站、显式跟进测试稿。3. 切草稿与原筛选，翻页、搜索、关注/挂起。4. 对照S-1～S-4检查视图与Tab键焦点。
- 预期结果: 会议仍按原预览校验，RAG/附件/线程未串目标，出站富文本保留；原筛选总数不受草稿标记改变；关注/挂起状态不因存稿改变；新增tab活动底边2px #3762d8，保存状态11px/绿色#059669，失败#be123c，桌面操作按钮高32px/圆角7px；手机操作按钮按既有移动规则至少44px（筛选tab仍遵循其更高优先级36px规则）；手机无新增横向溢出、按钮可点击、焦点可见。
- 覆盖: O-1/O-2/O-3、N-1/N-2、I-1/I-5/I-6/I-8、S-1/S-2/S-3/S-4、IP-1/IP-5/IP-6

