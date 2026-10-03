# 02 — 挂起交互、默认 Tab 与完整 CSS

> 2026-10-03 v6 定稿：用户已确认中性灰预览。本文已替换旧弹窗方案，完整 CSS、行内确认及当前登录身份契约均已同步；正式代码尚未实施。

状态：待评审。依赖：01及01b完成并分别验证。实施范围：6文件，邮箱前端1子系统。本文CSS为正式页面完整新增样式，不复用预览的模拟读写适配器。

## 需求描述

1. 固定六个Tab顺序；普通首次进入按未挂起待处理队列是否有数据选待处理/关注。
2. 列表与详情都有纯文字挂起/取消按钮；挂起按钮下方行内填写原因，空白也可确认；已挂起详情显示原因。
3. 最后一条处理完成的消息下方出现“所有消息已处理，是否结束挂起？”提示行；继续挂起不写数据，明确结束才调用01取消接口；取消后按剩余未处理及已有关注/已回复规则归类。
4. 消息“待处理”按钮点击后原位改为“取消 / 确认”；确认成功显示“已处理”，本次成功回包带账号时显示“已处理 · admin”。不输入操作人，不弹框。
5. 挂起使用中性灰#64748b、浅灰底#f1f5f9、灰色描边按钮；关注星标沿用现有黄色，确认按钮保留蓝色。

必须保持：①关注与移出已回复；②材料/排期/标签/地区时间/专家状态信息；③回复正文编辑与草稿、滚动位置、最新回复时间；④待匹配/高级筛选/分页/深链接；⑤当前移动端导航与其它页面样式。

范围外：改写收信/发信服务、全站公共操作弹窗（仅本收发件箱消息处理改为行内；身份接口由01b负责）、改全站主题/导航、复制预览页包装条、隐藏生产工作台、全局轮询、自动删除挂起、批量功能/原因编辑。

## 关键不变量

### Invariant I-1: Tab与默认进入
- Rule: 固定全部/关注/待处理/已挂起/已回复/待匹配。普通新mount首先查询当前列表筛选范围的pendingOnly页；total>0保留待处理，否则仅这一次切关注。挂起会话和未关联待匹配不算可处理专家。已有focus深链接优先定位原专家，不被默认探测覆盖。
- Applies to: FILTER_CHIPS/createInstance/loadList/applyOptions/mount。
- Violation consequence: 默认空待处理页，刷新抢回用户Tab或破坏深链接。
- 来源: 用户指定；K-dom-stub-tests-hide-dangling-refs。

### Invariant I-2: 后端状态权威
- Rule: 禁止从可见时间线长度或pendingCount自减决定结束。只能GET01状态判断跨账号suspensionPendingCount=0；标记处理成功后重新读取。写失败不移动卡片、不关闭行内原因表单、不清空原因。已挂起0条仍出现在挂起页。
- Applies to: 挂起/取消/处理回调/状态刷新/列表详情同步。
- Violation consequence: 漏掉另一账号或旧页消息，假成功与状态丢失。
- 来源: K-mailbox-inbound-source-authority；K-group-before-pagination。

### Invariant I-3: 只提示不自动结束
- Rule: suspended=true且跨账号计数=0时，在使计数归零的已处理来信 footer 下追加唯一提示行，不浮层、不抢焦点。继续挂起后该行变“已继续挂起”，保留“结束挂起”；刷新同一mount保留该选择。新pending>0时移除提示并重置保留标记；新挂起周期或重新mount可重新显示询问。重新加载没有本次处理锚点时，放在当前已加载时间线最后一条PROCESSED入站消息下；窗口无此消息则只保留顶部“取消挂起”入口，不凭空生成邮件。
- Applies to: 处理成功、选择会话、读取刷新、行内选择、unmount。
- Violation consequence: 自动结束或重复提示行。
- 来源: 用户最新澄清。

### Invariant I-4: 状态变更与现有规则分离
- Rule: 显式取消接口回包count>0→待处理；count=0且followed=true→关注；其余→已回复列表，由服务器原资格规则决定是否可见。不得拼入专家或强行取消关注；原规则不满足时显示原空列表，可通过全部继续查阅。
- Applies to: 取消/确认结束成功回调，列表刷新。
- Violation consequence: 用户明确保留的已回复语义被改变。
- 来源: 用户澄清；MailboxConversationRepository.expertPredicates。

### Invariant I-5: 异步与会话隔离
- Rule: 全部请求绑定contactId、listSeq/convEpoch/paneEpoch、当前user及disposed；切换专家/Tab或unmount后，旧回包不覆盖新会话。等待默认探测时用户点击Tab/筛选即取消该探测决策。发请求时置busy禁止重复提交；同一组件只保留一份行内原因表单；处理确认以contactId+source:id绑定。草稿保留，不重建整个详情编辑区。
- Applies to: 默认加载、行内表单、列表/状态刷新、处理回调、unmount。
- Violation consequence: 跨专家误操作、重复提交、草稿消失。
- 来源: K-mailbox-latest-inbound-display-refresh；现有序号保护。

### Invariant I-6: 原因与样式边界
- Rule: 原因trim、最多500个UTF-16单位；以escapeText/textContent展示，换行保留；纯文字操作按钮无暂停伪元素。只追加S-4，mailbox-chat.css字节不动，无inline style、无未声明class。
- Applies to: 新DOM、styles.css、样式测试。
- Violation consequence: XSS、样式失真、破坏冻结CSS契约。
- 来源: K-mailbox-chat-css-byte-contract；用户图标要求。

### Invariant I-7: 处理确认与真实身份
- Rule: “待处理”第一次点击只进入原位确认，取消/Escape回到待处理，不发POST；确认才POST现有mark-resolved，body为`{note:null}`，不传resolvedBy/operatorName。01b从Session取身份，沿既有权限及服务执行；收到成功才改已处理并GET挂起状态。失败保留确认和行内错误，不能假装成功。操作人展示只取01b成功回包；历史时间线没有resolvedBy字段，不伪造当前账号为历史处理人。
- Applies to: renderMessage、onClick、markResolvedByKey、挂起状态刷新。
- Violation consequence: 点击即写、冒用身份、未处理先提示结束。
- 来源: 用户要求；实际源码证据revision-v6-source.md。

## 样式契约

### S-1: Tab与既有组件
- 复用：`.mc-filters/.mc-filter`（mailbox-chat.css:9–14、86–88）、`.mc-person/.mc-person-main`（16–22）、`.mc-actions`（38）、`.mc-text-button`（109–112）、`.button`（styles.css既有定义）、`.mailbox-reply-list/detail`（styles.css:12466–12473）。
- 派生覆盖：只覆盖`#view-mailbox.mc-refined .mc-filters/.mc-filter`，不就地修改原CSS。全部使用位置见 evidence/frontend-usage.txt：专家与待匹配共用筛选栏；其它页面没有此宿主。原mc-person/actions规则不改，新增独立footer布局。
- DOM：保留原按钮生成与aria-pressed，顺序由FILTER_CHIPS数组决定；待处理/已挂起各追加`<span class="mailbox-suspend-count">数字</span>`，计数不可用时不伪造0（保留标签，隐藏该span）。
- 新class全部由S-4给出；Tab计数为专家数，不是消息数。普通Tab active主色#3762d8/计数底#e9efff；已挂起active文字#475569/下划线#64748b/计数底#e2e8f0。字号见S-4。

### S-2: 卡片、详情与状态说明
- 复用：原`.mc-person`网格、主选择按钮、person-actions关注/移出区域，原`.mc-header/.mc-actions`与`.mc-timeline-head`。
- 新增DOM：以下代码中的旧内容完整保留，注释不是要求重造原内容。footer是主选择button的兄弟，不嵌套button。

```html
<div class="mc-person" data-contact-id="{id}" data-active="{true|false}">
  <!-- 原 mc-person-main 与 person-actions 原样保留 -->
  <div class="mailbox-suspend-card-footer">
    <span class="mailbox-suspend-state" data-pending="{true|false}">{状态文字}</span>
    <button type="button" class="mailbox-suspend-card-action" data-action="mc-suspension" data-contact-id="{id}">{挂起|取消挂起}</button>
  </div>
</div>
<!-- mc-header 内 mc-actions 原按钮之前 -->
<button type="button" class="button mailbox-suspend-action" data-action="mc-suspension" data-contact-id="{id}">{挂起|取消挂起}</button>
<!-- mc-header 后、mc-timeline-head 前；仅suspended=true时渲染 -->
<div class="mailbox-suspend-banner" role="status">
  <span class="mailbox-suspend-symbol" aria-hidden="true">Ⅱ</span>
  <div class="mailbox-suspend-banner-content">
    <strong>{状态标题}</strong>
    <small>{原因文字}</small>
    <small>{引导文字}</small>
  </div>
</div>
```

- 未挂起且pending=0：不显示挂起按钮/footer；未挂起且pending>0：状态`N 条待处理`；挂起：`已挂起 · N 条待处理`。这里N使用跨账号suspensionPendingCount，与banner一致。data-pending=true仅用于未挂起且N>0，已挂起即使还有未处理也必须为false，始终显示中性灰。
- 非零banner标题：`此会话已挂起 · N 条待处理`；零标题：`消息已全部处理 · 等待结束挂起`。
- 原因：有值`挂起原因：{文本}`，无值`未填写挂起原因`。非零引导`全部处理完成后，可在消息下方确认是否结束挂起。`；零引导`挂起仍然保留，可点击「取消挂起」结束。`
- 禁止：隐藏contact-timing、ORCID/专家标签、工作台、材料、原移出按钮；从预览复制absolute整卡片覆盖层；给任何挂起操作按钮添加Ⅱ/SVG/pseudo icon。

### S-3: 三类行内交互（禁止弹框）
- 复用：消息article与footer（mailbox-chat.js:3070–3123），错误复用`.mc-inline-error`和setInlineError/clearInlineError；原消息内容、翻译、标签、附件不改。挂起原因与完成提示新增class逐字定义于S-4。
- 创建挂起：列表入口在被点击`.mc-person`之后插入section；详情入口在`.mc-header`之后插入section。保持主button兄弟结构，禁止把输入嵌进选择专家button。关闭旧表单后打开新表单，聚焦textarea；取消/Escape移除该表单并恢复触发按钮焦点，按钮已消失则回选中Tab。没有dialog、showModal、backdrop、alert、confirm或prompt。动态文字转义；表单中的按钮均type=button，Enter在textarea只换行。

```html
<section class="mailbox-suspend-inline-reason" aria-label="填写挂起原因" data-contact-id="{id}">
  <div class="mailbox-suspend-inline-reason-head"><strong>挂起此会话</strong><span>原因选填</span></div>
  <textarea aria-label="挂起原因（选填）" maxlength="500" rows="2" placeholder="例如：等待专家补充材料，稍后跟进"></textarea>
  <div class="mailbox-suspend-inline-reason-bottom">
    <small>仅内部可见 · <span class="mailbox-suspend-reason-count">0 / 500</span></small>
    <div>
      <button type="button" class="mailbox-suspend-inline-secondary" data-action="mc-suspension-reason-cancel">取消</button>
      <button type="button" class="mailbox-suspend-inline-primary" data-action="mc-suspension-reason-confirm" data-contact-id="{id}">确认挂起</button>
    </div>
  </div>
  <div class="mailbox-suspend-error" role="alert" hidden></div>
</section>
```

- input更新长度；trim后空白合法；提交时disabled textarea与两个按钮，主按钮“正在挂起…”；失败恢复按钮、保留原输入，section内显示安全错误文本。普通列表刷新期间保留同目标未提交表单与输入；切专家/Tab/账号、手机返回或unmount则清除。PUT成功才关闭表单。
- 处理控件：三种互斥状态，仅替换原footer处理控件槽，不改变正文和草稿。未确认时原处理按钮无✓图标；已处理后不是可点击按钮。

```html
<!-- MANUAL_REVIEW / 未确认 -->
<button class="mc-text-button mc-process mailbox-suspend-pending-action" type="button" data-action="mc-mark-resolved" data-message-key="{source:id}">待处理</button>
<!-- MANUAL_REVIEW / 确认中，原位替换上面按钮 -->
<span class="mailbox-suspend-process-confirm">
  <span>确认标记为已处理？</span>
  <button type="button" class="mailbox-suspend-inline-secondary" data-action="mc-process-cancel" data-message-key="{source:id}">取消</button>
  <button type="button" class="mailbox-suspend-inline-primary" data-action="mc-process-confirm" data-message-key="{source:id}">确认</button>
</span>
<!-- PROCESSED：账号只在本次01b成功回包提供时附加 -->
<span class="mailbox-suspend-processed-label">已处理{ · 服务端resolvedBy}</span>
```

- 确认期间“取消 / 确认”都disabled，确认文字“处理中…”；失败恢复确认按钮并使用原article的mc-inline-error；不调用hostShowStatus产生处理弹出提示。取消只清本条确认；未提交时Escape等同取消。成功后保留现有badge/list/timeline刷新，但必须遵守I-5；本条控件消失后焦点留在当前article，不跳到结束按钮。
- 完成行位于**使最后一个未处理消息完成的article内部、footer之后**；一位专家最多一行。按I-3的重载回退规则选择锚点；visible timeline只决定放置位置，不决定“全部处理”。没有可见锚点时不加独立悬浮提示。

```html
<div class="mailbox-suspend-completion-line" role="status" data-contact-id="{id}">
  <div>
    <strong>所有消息已处理，是否结束挂起？</strong>
    <small>{followed ? 结束后仍保留关注，可在「关注」查看。 : 结束后按现有「已回复」规则归类。}</small>
    <div class="mailbox-suspend-error" role="alert" hidden></div>
  </div>
  <div class="mailbox-suspend-completion-actions">
    <button type="button" class="mailbox-suspend-inline-secondary" data-action="mc-suspension-keep" data-contact-id="{id}">继续挂起</button>
    <button type="button" class="mailbox-suspend-inline-primary" data-action="mc-suspension-end" data-contact-id="{id}">结束挂起</button>
  </div>
</div>
```

- 继续挂起：strong改`已继续挂起`，small改`会话仍保留在「已挂起」，可随时结束。`，移除继续按钮，保留结束按钮；不发写请求。结束等待时两按钮disabled，主按钮`正在结束…`；失败在该行显示错误并恢复按钮；成功才移除挂起行。顶部/卡片取消失败也在相邻独立mailbox-suspend-error槽显示错误，不弹框。
- 此三类交互不修改其它已有材料/标签/排期弹窗，不删除全站ACTION_DIALOG_SCHEMAS。新状态只存组件内存；挂起真值和原因只存01后端。

### S-4: 完整CSS（必须逐字复制）

在styles.css末尾追加下面**整个代码块**，包括起止注释；禁止改数值、删状态、额外添加全局label/p/dialog规则。它是正式落地唯一CSS合同；预览外层导航/提示条不在正式迁移范围。未修改的既有CSS由S-1引用，完整基线存于evidence/baseline-mailbox-chat.css。中性灰实值取自已确认v6；正式页保留现有网格/功能，类名前缀改为mailbox-suspend-，追加busy/错误/focus状态与≤760px的44px触控尺寸，不复制预览隐藏工作台/材料的规则。

```css
/* mailbox-suspension-contract:start */
#view-mailbox.mc-refined .mc-filters{display:flex;flex-wrap:nowrap;gap:4px;overflow-x:auto;scrollbar-width:thin}
#view-mailbox.mc-refined .mc-filter{flex:0 0 auto;min-height:36px;padding:7px 5px 10px;border:0;border-bottom:2px solid transparent;border-radius:0;background:transparent;color:#7d8ca2;font-size:12px;line-height:1.5;white-space:nowrap}
#view-mailbox.mc-refined .mc-filter:hover{background:#f4f7ff;color:#3762d8}
#view-mailbox.mc-refined .mc-filter:active{background:#e9efff}
#view-mailbox.mc-refined .mc-filter[aria-pressed=true]{background:transparent;border-bottom-color:#3762d8;color:#3762d8;font-weight:600}
#view-mailbox.mc-refined .mc-filter:disabled{opacity:.45;cursor:not-allowed}
#view-mailbox.mc-refined .mc-filter:focus-visible{outline:2px solid #9eb9ff;outline-offset:-2px}
.mailbox-suspend-count{display:inline-flex;align-items:center;justify-content:center;min-width:16px;height:16px;margin-left:3px;padding:0 4px;border-radius:5px;background:#f0f3f8;color:#8a98ab;font-size:10px;font-weight:600;line-height:16px}
#view-mailbox.mc-refined .mc-filter[aria-pressed=true] .mailbox-suspend-count{background:#e9efff;color:#3762d8}
.mailbox-suspend-card-footer{grid-column:1 / -1;display:flex;align-items:center;justify-content:space-between;gap:8px;min-width:0;padding:0 12px 12px 10px}
.mailbox-suspend-state{display:inline-flex;align-items:center;gap:5px;min-width:0;color:#64748b;font-size:11px;line-height:1.6;overflow-wrap:anywhere}
.mailbox-suspend-state::before{content:'';flex:none;width:5px;height:5px;border-radius:50%;background:#94a3b8}
.mailbox-suspend-state[data-pending=true]{color:#8195b6}
.mailbox-suspend-state[data-pending=true]::before{background:#91aad7}
.mailbox-suspend-card-action{flex:none;min-height:30px;padding:4px 9px;border:1px solid #cbd5e1;border-radius:6px;background:#fff;color:#64748b;font:inherit;font-size:11px;font-weight:500;line-height:1.5;white-space:nowrap;cursor:pointer}
.mailbox-suspend-card-action:hover:not(:disabled){border-color:#94a3b8;background:#f1f5f9;color:#334155}
.mailbox-suspend-card-action:active:not(:disabled){background:#e2e8f0}
#view-mailbox.mc-refined .mc-actions .mailbox-suspend-action{order:-1;min-height:32px;padding:6px 13px;border:1px solid #b8c4d3;border-radius:7px;background:#fff;color:#475569;font:inherit;font-size:12px;font-weight:500;line-height:1.5;box-shadow:none;cursor:pointer}
#view-mailbox.mc-refined .mc-actions .mailbox-suspend-action:hover:not(:disabled){border-color:#94a3b8;background:#f1f5f9;color:#334155;transform:none}
#view-mailbox.mc-refined .mc-actions .mailbox-suspend-action:active:not(:disabled){background:#e2e8f0}
.mailbox-suspend-card-action::before,.mailbox-suspend-action::before{content:none;display:none}
.mailbox-suspend-card-action:disabled,#view-mailbox.mc-refined .mc-actions .mailbox-suspend-action:disabled{opacity:.5;cursor:not-allowed;transform:none;box-shadow:none}
.mailbox-suspend-card-action:focus-visible,.mailbox-suspend-action:focus-visible{outline:3px solid #9eb9ff;outline-offset:3px}
.mailbox-suspend-banner{display:flex;align-items:flex-start;gap:11px;flex:none;margin:16px 22px 3px;padding:12px 14px;border:1px solid #dde4ec;border-radius:9px;background:#f1f5f9;color:#475569;font-size:12px}
.mailbox-suspend-symbol{display:flex;align-items:center;justify-content:center;flex:none;width:29px;height:29px;border-radius:8px;background:#e2e8f0;color:#64748b;font-size:14px;font-weight:600;line-height:29px}
.mailbox-suspend-banner-content{flex:1;min-width:0}
.mailbox-suspend-banner strong{display:block;font-size:12px;font-weight:600;line-height:1.6}
.mailbox-suspend-banner small{display:block;margin-top:4px;color:#7c8a9d;font-size:11px;line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere}
#view-mailbox.mc-refined .mc-filter[data-chip=suspended][aria-pressed=true]{color:#475569;border-bottom-color:#64748b}
#view-mailbox.mc-refined .mc-filter[data-chip=suspended][aria-pressed=true] .mailbox-suspend-count{color:#475569;background:#e2e8f0}
.mailbox-suspend-inline-reason{flex:none;margin:12px 18px;padding:15px;border:1px solid #dde4ec;border-radius:10px;background:#f8fafc;color:#33465f;text-align:left}
.mailbox-suspend-inline-reason-head{display:flex;align-items:center;gap:9px;margin-bottom:10px}.mailbox-suspend-inline-reason-head strong{font-size:12px;font-weight:600}.mailbox-suspend-inline-reason-head span{color:#8a99ad;font-size:10px}
.mailbox-suspend-inline-reason textarea{display:block;box-sizing:border-box;width:100%;min-height:64px;margin:0;padding:9px 11px;resize:vertical;border:1px solid #dce5f0;border-radius:7px;background:#fff;color:#435773;font:inherit;font-size:12px;line-height:1.65;box-shadow:none}.mailbox-suspend-inline-reason textarea:focus{outline:2px solid #bdd0ff;outline-offset:1px}.mailbox-suspend-inline-reason textarea::placeholder{color:#96a5b9}
.mailbox-suspend-inline-reason-bottom{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:9px;margin-top:11px}.mailbox-suspend-inline-reason-bottom small{font-size:10px;color:#91a0b5}.mailbox-suspend-inline-reason-bottom>div{display:flex;gap:7px}
.mailbox-suspend-inline-secondary,.mailbox-suspend-inline-primary{display:inline-flex;align-items:center;justify-content:center;min-height:30px;margin:0;padding:5px 11px;border:1px solid #dce5f0;border-radius:6px;background:#fff;color:#72849d;font:inherit;font-size:11px;font-weight:500;line-height:1.5;white-space:nowrap;box-shadow:none;cursor:pointer}
.mailbox-suspend-inline-secondary:hover:not(:disabled){border-color:#bbcae2;background:#f4f7fc}.mailbox-suspend-inline-primary{border-color:#3762d8;background:#3762d8;color:#fff}.mailbox-suspend-inline-primary:hover:not(:disabled){border-color:#2954c8;background:#2954c8}.mailbox-suspend-inline-secondary:active:not(:disabled),.mailbox-suspend-inline-primary:active:not(:disabled){transform:translateY(1px)}.mailbox-suspend-inline-secondary:disabled,.mailbox-suspend-inline-primary:disabled{opacity:.5;cursor:not-allowed;transform:none}.mailbox-suspend-inline-secondary:focus-visible,.mailbox-suspend-inline-primary:focus-visible{outline:3px solid #9eb9ff;outline-offset:2px}
.mailbox-suspend-process-confirm{display:inline-flex;align-items:center;flex-wrap:wrap;gap:7px;margin-left:auto}.mailbox-suspend-process-confirm>span{color:#8a9bb2;font-size:11px;margin-right:3px}#view-mailbox.mc-refined .mc-message footer .mc-process{margin-left:auto}.mailbox-suspend-processed-label{margin-left:auto;color:#679481;font-size:11px;line-height:30px}
#view-mailbox.mc-refined .mc-message .mailbox-suspend-completion-line{display:flex;align-items:center;justify-content:space-between;gap:14px;margin:14px 0 0;padding:14px;border:1px solid #dde4ec;border-radius:8px;background:#f1f5f9;text-align:left}.mailbox-suspend-completion-line>div:first-child{min-width:0}.mailbox-suspend-completion-line strong{display:block;color:#475569;font-size:12px;font-weight:600;line-height:1.6}.mailbox-suspend-completion-line small{display:block;color:#7c8a9d;font-size:10px;line-height:1.65;margin-top:3px}.mailbox-suspend-completion-actions{display:flex;flex:none;align-items:center;gap:7px}.mailbox-suspend-completion-line .mailbox-suspend-inline-secondary{border-color:#cbd5e1;color:#64748b;background:#ffffff}.mailbox-suspend-completion-line .mailbox-suspend-inline-secondary:hover:not(:disabled){background:#e9eef4}
#view-mailbox.mc-refined .mc-message footer .mailbox-suspend-pending-action{margin-left:auto;padding:4px 10px;border:1px solid #dbe4f4;border-radius:6px;background:#f7faff;color:#6c87b1;font-size:11px;line-height:1.5;cursor:pointer}
#view-mailbox.mc-refined .mc-message footer .mailbox-suspend-pending-action:hover:not(:disabled){background:#edf3ff;color:#3762d8;border-color:#bfd0f4}
#view-mailbox.mc-refined .mc-message footer .mailbox-suspend-pending-action:active:not(:disabled){background:#e1ebff}
#view-mailbox.mc-refined .mc-message footer .mailbox-suspend-pending-action:disabled{opacity:.5;cursor:not-allowed}
#view-mailbox.mc-refined .mc-message footer .mailbox-suspend-pending-action:focus-visible{outline:3px solid #9eb9ff;outline-offset:2px}
.mailbox-suspend-inline-reason,.mailbox-suspend-inline-reason *,.mailbox-suspend-completion-line,.mailbox-suspend-completion-line *{box-sizing:border-box}
.mailbox-suspend-inline-reason [hidden],.mailbox-suspend-completion-line [hidden]{display:none!important}
.mailbox-suspend-error{margin:10px 0 0;color:#b34740;font-size:12px;line-height:1.6;overflow-wrap:anywhere}
.mailbox-suspend-reason-count{white-space:nowrap}
@media(max-width:760px){.mailbox-suspend-banner{margin:12px 12px 3px;padding:11px 12px}#view-mailbox.mc-refined .mc-filter{min-height:44px;padding-left:6px;padding-right:6px}.mailbox-suspend-card-action,#view-mailbox.mc-refined .mc-actions .mailbox-suspend-action,.mailbox-suspend-inline-secondary,.mailbox-suspend-inline-primary,#view-mailbox.mc-refined .mc-message footer .mailbox-suspend-pending-action{min-height:44px}.mailbox-suspend-inline-reason textarea{font-size:16px}.mailbox-suspend-inline-reason{margin:10px 12px}#view-mailbox.mc-refined .mc-message .mailbox-suspend-completion-line{flex-direction:column;align-items:flex-start;gap:10px}.mailbox-suspend-completion-actions{align-self:flex-end}.mailbox-suspend-process-confirm>span{flex-basis:100%;text-align:right}}
/* mailbox-suspension-contract:end */
```

## 现状审计

### 前端状态与读写路径

- `mailbox-chat.js:51–62`原顺序全部/关注/已回复/待处理/待匹配；:496 chipParams；:605初值仅pendingOnly=true→pending，否则all；:7390 applyOptions再次将false变all。三个位置都须核对，不能只调整数组。
- `app.js:16800–16815`宿主首次传完整筛选快照，其中pendingOnly恒为boolean；:16817已mount时只传`{}`；:3308离开页面unmount；:17078加载邮箱。所以不能把外部false当作“用户明确选择全部”。本片不改app.js，只让组件首次默认决策接管false。
- 全部chip写路径：createInstance、applyOptions、onClick mc-filter:6917。新增初始化/挂起回调必须走相同syncChipButtons和分页重置；每个入口受I-1/I-5约束。
- 列表读路径：conversationsParams:1328、fetchList:1480（listSeq guard）、loadList:1533、refreshListWithFallback:1543。现有fetchList只更新详情最新回复槽，不能为了挂起重建编辑器。（来源: K-mailbox-latest-inbound-display-refresh）
- 选择/焦点：resolveFocusAndSelection:1705、locateFocusExpert:1743用contactId/email查询，深链接优先；unmount保存草稿并移除portal/宿主事件。state/sessionStore现有存储只存草稿与滚动，不写挂起业务状态。
- 处理写路径：markResolvedByKey现位于:3487–3540，先openDialog("mark-unmatched-resolved")再POST；app.js:16090 schema强制操作人姓名；控制器:171–184信任body操作人，故需要01b。时间线DTO当前无resolvedBy。处理后的本地pending自减只能即时显示，挂起提示必须再GET全局状态。取消已处理/手动发信保留原流程，在已有成功刷新钩子读状态，不改写其业务请求。
- 详情渲染：renderPerson:1370、renderHeader:1958、renderHeaderMeta:1993。新增操作与banner独立槽更新；保留身份、关注、排期、材料、meta和contact-timing。
- 挂起新字段写者只有01；挂起写请求只有01的PUT/DELETE；邮件处理继续POST原mark-resolved并由01b绑定身份。全部read包括列表摘要、选中会话GET、处理完成GET、原刷新GET。没有localStorage/cookie业务状态，没有新定时器。
- 交互点X1：首次列表响应→默认Tab（用户点击/深链接冲突）；X2：01 PUT/DELETE→列表/详情/计数同步；X3：邮件处理成功→状态GET→确认提示；X4：刷新/切换/销毁→异步守卫与草稿；X5：新CSS→原材料/排期/地区时间/移动端。X6：手机返回列表或切登录用户→清理行内确认/请求；X7：01b Session→原markResolved写入及审计→成功回包→已处理账号标签。

### 并行移动端变更复核（写计划期间实际发生）

- 初始审计后，工作区新增mobile-core-02；`git diff`证据已保存为evidence/concurrent-mobile-mailbox.diff。该轮以evidence/final-working-tree-sha256.txt留档；本次v6以revision-v6-sha256.txt为更新基线，前述行号是初始定位锚点，执行按函数名复核。
- 新增mobilePane/paneEpoch、conversationVisible、saveCurrentConversation、returnToMobileList、setMobilePane；mc-filter切换已调用returnToMobileList，unmount/applyOptions改用saveCurrentConversation。**这些代码全部保留**，挂起成功的切Tab必须复用它们；状态行只在详情可见时挂载，点击手机“返回会话列表”后旧请求不得打开详情或重建行内表单。
- `styles.css`末尾mobile-core-02在≤760px要求控件44px、输入16px，并使用data-mobile-pane切换单栏。S-4已经对应给新增控件44px/原因16px；不得恢复预览的移动端上下双栏，也不覆盖display隐藏规则。
- 新有mailboxChatStyle.test.js:331移动端字节断言；本片不改这个测试，完整运行并保留其期望。

### 前端样式盘点

- 基准实值：既有chat文字#475569/12px/1.6，卡片圆角10px，原选中#eaf1ff/左边框#3c65cd，主Tab underline#2e59c7。新交互依S-4统一为#3762d8；挂起灰#64748b，状态条底#f1f5f9/边框#dde4ec/文字#475569，间距11px；原因区底#f8fafc/圆角10px；完成行padding14px/圆角8px。所有hover/active/disabled/focus值完整列出。
- `mailbox-chat.css`字节冻结测试见mailboxChatStyle.test.js:33–38；mc-*字面class须在该CSS出现，其它class须在styles.css。故采用mailbox-suspend-*并追加styles.css，不更新历史目标文件掩盖变动。（来源: K-mailbox-chat-css-byte-contract）
- 既有label有全局布局属性；本表单用section/strong及textarea aria-label，不继承全局label布局；错误用div且明确字号色值。（来源: K-panel-bg-token-is-translucent；K-global-p-is-muted-in-dialogs）
- 全部受影响class使用清单见evidence/frontend-usage.txt；派生样式不影响view-mailbox之外的页面。待匹配只共用Tab样式，没有挂起footer/banner。
- 改动前基线（逐字摘录，含真正生产DOM模板；不是预览模板）：

```js
const FILTER_CHIPS = [
        { key: CHIP_ALL, label: "全部" },
        { key: CHIP_FOLLOWED, label: "关注" },
        { key: CHIP_REPLIED, label: "已回复" },
        { key: CHIP_PENDING, label: "待处理" },
        { key: CHIP_UNMATCHED, label: "待匹配" }
    ];
```

```html
                    <span data-role="person-actions">
<button class="mc-follow" type="button" data-action="mc-toggle-follow" data-contact-id="${escapeText(item.contactId)}" aria-label="${item.followed ? "取消关注该专家" : "关注该专家"}" aria-pressed="${item.followed ? "true" : "false"}">${item.followed ? "★" : "☆"}</button>
                        ${instance.chip === CHIP_REPLIED ? `<button class="mc-text-button" type="button" data-action="mc-dismiss-replied" data-contact-id="${escapeText(item.contactId)}" aria-label="将${escapeText(item.name || item.email || "该专家")}移出已回复" title="移出已回复，仍可在全部查看">移出</button>` : ""}
</span>
```

```css
.mail-chat .mc-filters{display:flex;flex-wrap:wrap;gap:6px}
.mail-chat .mc-filter{min-height:28px;padding:3px 8px;border:1px solid #dce4ef;border-radius:7px;background:#f8faff;color:#64748b;font:inherit;cursor:pointer}
.mail-chat .mc-filter:hover{border-color:#93b4ec;background:#eff5ff}
.mail-chat .mc-filter:active{background:#dbeafe}
.mail-chat .mc-filter[aria-pressed=true]{border-color:#1e40af;background:#eff5ff;color:#1e40af;font-weight:600}
.mail-chat .mc-filter:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .mc-filters{gap:12px;flex-wrap:nowrap}
.mail-chat .mc-filter{border:0;border-bottom:2px solid transparent;border-radius:0;background:transparent;padding:6px 5px 10px}
.mail-chat .mc-filter[aria-pressed=true]{border-bottom-color:#2e59c7;background:transparent;color:#244ca9}
```

- 预览v2图标来自`.sp-action::before,.sp-card-action::before`两条竖边框。v3已经移除；正式实现S-4额外保证`content:none;display:none`，不移除其它按钮图标。
- v6复核index有11处（5CSS+6JS），10处键为`20261003-mobile-core-03`，mailbox-chat.js为`20261003-mobile-core-03-generic-followup`；这是并行工作快照，不是统一发布契约。证据revision-v6-grep.txt；对两个当前完整键精确反查src/test无固定值命中。执行时将这11处全部改为`20261003-mailbox-suspension`；执行时若并行工作更新了键，以当时完整清单为准重新取证，不能只换两个文件。相关测试从index动态取键，不写死。（来源: K-frontend-cache-key-triad）

## 实现方案

### T1 — 参数、默认进入与统计（I-1/I-2/I-5；S-1）

文件：mailbox-chat.js、mailboxChatBehavior.test.js。

1. 新增CHIP_SUSPENDED与固定顺序；chipParams只对suspended返回suspendedOnly；待匹配仍使用原邮件队列API。
2. 普通新实例默认暂定pending，第一次fetchList复用实际第一页请求（保留q/account/date等当前筛选）；total=0才发followed查询并选关注。请求失败保留错误与重试，不能把失败当0。focus存在时沿原定位流程，不作默认跳转。
3. 增加实例级初始化完成标记，不存localStorage。applyOptions在初始化完成后不根据pendingOnly=false重选Tab；用户点击任何Tab/查询马上冻结默认决策并推进listSeq。刷新现有mount不执行默认规则；离开重新进入新mount重新判断。
4. Tab计数从同一筛选范围的GET列表total获取，page=0/size=1，分别pendingOnly和suspendedOnly；可复用当前列表结果，另一个只作一次请求。在挂起/取消/处理成功/显式刷新后更新；无轮询、无拉取全库。失败隐藏数字，不显示假0；用独立请求序号避免乱序。统计是专家数。

### T2 — 行内原因与显式挂起/取消（I-2/I-4/I-5/I-6；S-2/S-3/S-4）

文件：mailbox-chat.js、styles.css。

1. 按S-2新增footer/详情按钮/banner；存在挂起时即便pending=0仍显示取消。原因用escapeText，500字符/长单词按CSS换行。
2. 点击挂起先GET该contact状态，仍可挂起才插入S-3原因section；确认PUT `{reason: textarea.value.trim() || null}`。重复点击busy防重。失效/409显示真实提示并刷新，不假设成功。
3. 已挂起点“取消挂起”直接DELETE（用户已经明确操作）；完成提示的“结束挂起”也是DELETE。成功后先调用现有returnToMobileList/saveCurrentConversation保留草稿及返回列表，再按I-4切Tab、page=0，并重新查询列表/计数。保留被操作contactId；不把当前选中的另一专家当成操作对象。
4. 详情只更新挂起按钮槽/banner；列表重绘用现有renderList，最新回复槽照常更新。不要调用renderHeader重建所有按钮/地理时间/编辑器来偷懒。
5. 如目标未满足已回复旧规则则不显示“已移入已回复”成功文案，只说`已结束挂起`；有未处理说`已取消挂起，回到「待处理」`；已关注零未处理说`已结束挂起，可在「关注」查看`。

### T3 — 原位处理确认与完成行（I-2/I-3/I-5/I-7；S-2/S-3/S-4）

文件：mailbox-chat.js。

1. onClick的mc-mark-resolved只写组件确认状态；新增mc-process-cancel和mc-process-confirm；只在确认handler调markResolvedByKey的实际POST部分，去掉该函数的openDialog调用。本改动覆盖聊天视图中已关联专家消息，不改待匹配管理页公共弹窗。保留PendingMailOperationService原有业务副作用。
2. POST `{note:null}`；消费01b回包`{id,processStatus,resolvedBy}`，校验id与目标一致。账号从服务器返回，不从operatorName()/localStorage/输入框拼出。历史处理人无数据时只显示“已处理”；组件内本次成功标签按message key保存，unmount清理，不扩展时间线DTO或持久化缓存。
3. 当前宿主mountOptions未传sessionUser，sessionUserFromOptions实际回退localStorage operatorName（app.js:16855、mailbox-chat.js:503）。为挂起交互在新mount通过现有GET `/api/auth/me`获取一次authenticated username，身份就绪前禁用新挂起/处理按钮；失败在既有错误区域显示“登录状态读取失败，请刷新重试”，复用已有刷新入口重试，不能用console/UNKNOWN兜底。用独立authenticatedUser及生命周期序号守卫新请求，不改老草稿键与全站身份模块。服务端权限仍由AuthInterceptor和01/01b Session校验执行。
4. 状态GET统一入口：选中专家完成加载、显式刷新成功、原标记/撤销处理成功、已有会话刷新成功。全部使用01真实跨账号计数；没有轮询。POST成功先更新本条已处理，再GET；GET失败不回滚已经成功的处理，只隐藏未核实的完成提示并提供状态重试。
5. 仅同contactId/convEpoch/paneEpoch/用户且未disposed的回包更新UI。当本次处理后GET首次归零，保存该消息source:id为完成锚点；只有其仍在已加载时间线且PROCESSED才插入提示。无本次锚点时按时间线现有顺序取最后一条PROCESSED入站。没有合格消息只显示banner与取消入口；切换账号/重新加载窗口后重算位置。已知pending>0必须移除旧完成行。
6. 继续挂起仅记录本mount选择并改S-3文案；刷新不重复建行。新pending周期或新挂起清除保留标记。unmount、切用户、手机返回清理表单/未提交确认/异步UI回调；不新增任何dialog。若提交已在服务端成功，不能因页面切换再发撤销，只在下次真实GET中反映状态。
7. 结束期间又收到消息仍允许DELETE；以回包count>0回待处理；不增加强制结束二次弹框。完成提示行不因蓝色按钮或零计数自动触发点击。

### T4 — 样式、缓存与测试（I-1–I-7；S-1–S-4）

文件：styles.css、index.html、mailboxChatBehavior.test.js、mailboxSuspension.test.js、mailboxSuspensionStyle.test.js。

- S-4逐字追加styles.css；index统一缓存键。无需增加静态script/css引用，无需改变app.js。
- 现有测试中默认all的断言改为fixture明确的pending或followed，只调整受需求影响断言；原“用户选择后不可覆盖”测试保留。
- 新功能测试覆盖真实交互与请求序列，不只grep常量；新样式测试从本计划S-4提取CSS，与styles.css注释范围字节比较；DOM class映射、无inline/按钮pseudo图标检查。
- 真实浏览器至少1280×800、390×844；键盘Tab/Escape、500字符换行、行内失败重试、focus返回、页面切换与草稿回归。DOM stub不能替代真浏览器。（来源: K-dom-stub-tests-hide-dangling-refs）

## 变更文件清单

| # | 文件 | 变化 |
|---|---|---|
|1|src/main/resources/static/mailbox-chat.js|Tab/默认/挂起与确认/独立DOM槽|
|2|src/main/resources/static/styles.css|仅追加S-4完整CSS|
|3|src/main/resources/static/index.html|统一缓存键|
|4|src/test/js/mailboxChatBehavior.test.js|更新默认语义、保持旧回归|
|5|src/test/js/mailboxSuspension.test.js|新功能交互/请求与竞态|
|6|src/test/js/mailboxSuspensionStyle.test.js|逐字CSS和DOM样式契约|

app.js、mailbox-chat.css、已有计划CSS基线、业务服务不在修改范围。测试确有外部耦合失败须先给代码证据并修订清单，不盲目删旧断言。

## 验收标准

- I-1：pending total=1→pending；0→followed；网络失败→错误不跳关注；显式focus可定位；点击Tab或更新筛选后迟到探测不能覆盖；重新mount再判断。
- I-2：只剩时间线外/另一账号未处理仍不提示；PUT/DELETE失败卡片不移走；0条挂起仍可见；处理成功必须GET状态后决定。
- I-3：结束之前DELETE请求数0；继续后仍0且行内显示“已继续挂起”；重复刷新最多一行；新pending>0行消失，再到0恢复询问；明确结束DELETE恰1。
- I-4：关注保留、原已回复条件不变；网络响应新pending>0返回待处理；不向返回items手动塞行。
- I-5：A请求迟到B不被更新；快速双击仅1请求；切Tab不被旧列表覆盖；草稿正文与滚动仍保留；unmount无残留表单/确认行。
- I-6：`<img src=x onerror=...>`显示纯文字；空/500/501长度分支；动作按钮无图标；全部新增class被S-4覆盖；已挂起且N>0仍为灰色，不能误设data-pending=true。
- I-7：点待处理和取消均0 POST；确认只有1 POST且不带操作人；接口成功前仍MANUAL_REVIEW，失败能重试；成功显示服务端账号；已有历史消息不伪造admin；全流程无新dialog/openActionDialog/alert/confirm/prompt调用。
- S-1：DOM六Tab完整顺序；普通active色#3762d8，已挂起#475569且underline#64748b；390px时局部横向滚动、不导致整页横向溢出；计数来源为API total。
- S-2：独立footer、按钮无嵌套；banner显示正确原因/0条状态；原关注、移出、排期/材料/地区时间仍可点。
- S-3：原因section位于触发卡片/详情头之后；处理确认在原footer；完成行位于归零消息footer之后且唯一；取消/Escape/busy/错误/焦点逐项验证，无操作人输入；原因500单位换行不溢出。
- S-4：styles新增区与本文CSS字节一致，mailbox-chat.css与旧目标字节不变；无新增inline。

```sh
node --check src/main/resources/static/mailbox-chat.js
node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxSuspension.test.js src/test/js/mailboxSuspensionStyle.test.js src/test/js/mailboxChatStyle.test.js
node --test src/test/js/*.test.js
```

按K-js-test-invocation-surface，JS测试单独运行，不能以Maven通过代替。当前计划未实施，这些测试不是本轮执行结果。先完成功能小集，再全量JS一次；无新修改不重复跑。

## 人工验收清单

### A-1: Tab与默认进入
- 前置条件: 01测试环境已有1位未挂起且MANUAL_REVIEW专家；从其它菜单进入收发件箱。
- 操作步骤: 1. 看Tab顺序。2. 确认待处理选中。3. 挂起所有待处理专家。4. 离开再进入。5. 点击全部再刷新列表。6. 打开某专家既有深链接。
- 预期结果: 六项依次全部/关注/待处理/已挂起/已回复/待匹配；步骤2待处理、步骤4关注、步骤5仍全部；深链接仍打开目标专家。
- 覆盖: I-1/I-5/S-1，X1，需求1，必须保持④。

### A-2: 原因、图标与持久化
- 前置条件: 存在2位未挂起待处理专家。
- 操作步骤: 1. 对第一位点挂起，原因留空确认。2. 第二位填“等待材料\n下周跟进”。3. 确认后刷新/重新登录查看已挂起。4. 检查列表和详情操作按钮。
- 预期结果: 第一位“未填写挂起原因”；第二位显示两行原因；挂起记录刷新后仍在；两个操作按钮只有“取消挂起”文字，没有Ⅱ图标。
- 覆盖: I-2/I-6/S-2/S-3/S-4，X2，需求2。

### A-3: 全部完成但继续挂起
- 前置条件: 一位挂起专家有2条MANUAL_REVIEW，原因为“等待材料”。
- 操作步骤: 1. 每条消息点击“待处理”再点“确认”，先处理第一条、再处理第二条。2. 查看第二条消息下方。3. 点击“继续挂起”。4. 刷新当前列表两次。5. 点击该提示行“结束挂起”。
- 预期结果: 第一条确认后还剩1条，没有完成提示；第二条确认后其footer下显示“所有消息已处理，是否结束挂起？”；继续后已挂起0条且原因保留；两次刷新仅一行“已继续挂起”，仍有结束按钮；最后明确点击结束才移出挂起。
- 覆盖: I-2/I-3/I-5/S-2/S-3，X3/X4，需求3。

### A-4: 取消去向与关注回归
- 前置条件: E挂起且1条未处理；F挂起且0条、已关注；G挂起且0条、未关注并符合旧已回复规则。
- 操作步骤: 1. 分别取消E/F/G。2. 查看目标Tab。3. 对G执行原“移出”。4. 查看F关注星标。
- 预期结果: E→待处理，F→关注且仍已关注，G→已回复；G移出后在已回复不可见；无挂起接口取消关注。
- 覆盖: I-4/S-2，X2，必须保持①。

### A-5: 多账号、错误与乱序
- 前置条件: 同专家A账号0待处理、B账号1待处理；测试工具可对状态GET/PUT模拟延迟/500。
- 操作步骤: 1. 筛选A账号并查看挂起专家。2. 挂起PUT设为500。3. 输入原因提交。4. 延迟A状态GET后切专家B。5. 恢复网络重试。
- 预期结果: 跨账号显示1条，不显示全部完成提示；500不移走卡片且原因输入保留；迟到结果不更改B；重试成功只提交一份并显示真实状态。
- 覆盖: I-2/I-5/I-6/S-3，X2/X3/X4。

### A-6: 视觉、键盘、窄屏与原业务
- 前置条件: 桌面1280×800、移动390×844；测试会话有材料/排期/标签/地区时间；已输入未发送草稿。
- 操作步骤: 1. 查看六Tab、列表/详情按钮与状态条。2. 原因填500字符并含换行。3. 键盘Tab浏览行内原因表单、Escape取消。4. 切专家再返回。5. 打开材料/排期/标签/地区时间及待匹配高级筛选，翻页；手机详情点击“返回会话列表”，再切至其它页面。
- 预期结果: 挂起状态文字#64748b，banner底#f1f5f9/边框#dde4ec，详情取消按钮白底/边框#b8c4d3、卡片按钮边框#cbd5e1；已挂起Tab文字#475569/下划线#64748b；完成行浅灰底/圆角8px，确认按钮#3762d8；关注星仍为原黄色；原因区#f8fafc/圆角10px，换行无溢出；焦点回触发按钮；动作图标为空；草稿与最新回复时间仍在；材料/排期/标签/地区时间可见可用；待匹配与分页使用原流程；其它页和移动导航没有新样式变化；手机返回后保留列表滚动，不被旧状态请求重新打开详情或行内表单；触控按钮≥44px。
- 覆盖: I-5/I-6/S-1/S-2/S-3/S-4，X4/X5/X6，必须保持②③④⑤。

### A-7: 处理确认、身份与失败恢复
- 前置条件: 以admin登录独立测试环境；专家有一条MANUAL_REVIEW；浏览器网络面板可观察POST并对一次请求模拟500；localStorage operatorName故意设为other。
- 操作步骤: 1. 点消息“待处理”。2. 点“取消”。3. 再点“待处理”，将确认POST设为500后点“确认”。4. 恢复网络，再点“确认”。5. 刷新该会话，检查原审计记录中的操作人。
- 预期结果: 步骤1只原位出现“取消 / 确认”，步骤2仍待处理，两步均无POST；500后原位显示错误、按钮可重试；成功显示“已处理 · admin”，数据库resolved_by与审计操作人均为admin，不能为other；不出现操作人输入或确认弹框。刷新历史时间线可只显示“已处理”，不得给其它历史处理消息伪造admin。
- 覆盖: I-2/I-5/I-7/S-3/S-4，X7，需求4。

### A-8: 提示锚点、长时间线与新来信
- 前置条件: 挂起专家有2条待处理且跨两账号；时间线有50条以上，选中的账号窗口没有最后处理的另一账号来信；测试环境可接收example.test模拟来信。
- 操作步骤: 1. 在另一测试窗口处理全部来信。2. 当前窗口刷新。3. 切换账号使已处理来信可见。4. 选择继续挂起。5. 注入一条新MANUAL_REVIEW，再刷新。6. 确认处理该消息。
- 预期结果: 全量计数为0时保留挂起；有合格已加载消息时仅其最后一条footer下出现提示，无合格消息则只保留banner/取消入口；新来信后计数1且完成提示消失；最后确认后新消息footer下重新出现询问，没有自动取消。
- 覆盖: I-2/I-3/I-5/S-2/S-3，X3/X4，需求3。

### v6 证据与执行边界

用户确认的[中性灰截图](mailbox-suspension-evidence/approved-slate-v6.png)、[预览完整CSS](mailbox-suspension-evidence/preview-v6.css)、[交互源码](mailbox-suspension-evidence/preview-v6.js)已固定。新增正式样式唯一权威为S-4及字节相同的target-suspension.css。完整审计增量见revision-v6-source.md、revision-v6-grep.txt、revision-v6-sha256.txt；旧v3.css仅历史证据，不得执行旧dialog规则。正式生产文件本次未修改；本计划机器/人工验收均须实施后执行。
