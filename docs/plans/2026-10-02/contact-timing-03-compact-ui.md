# 03：收发信箱紧凑所在地与推荐时间

依赖：02 的真实 API 已验证。范围：5 个文件，前端 1 个子系统。以已确认的紧凑预览为视觉方向；下面逐字契约为实际实施边界。

## 需求描述

O-1：在现有状态行右侧显示所在地与北京时间建议，未配置时显示“配置所在地 ▾”，占用必要行高。
O-2：点击国家打开配置弹窗；选择国家，可选具体时区，保存后更新推荐。
O-3：点击 ⓘ 查看默认/历史依据、当地时间、日期与样本；选择专家、现有刷新、保存成功后读取最新结果。

必须保持 N-1：现有会话头、状态/标签、详情、关注、材料、管理、排期入口不变。
必须保持 N-2：草稿、发送目标、已加载邮件、滚动位置、原有弹窗开关及会话切换不被新功能重置。
范围外：左列表增列、布局重构、推荐热力图、实时轮询、独立新页面、浏览器持久化配置、复制整个预览壳。

## 关键不变量

### Invariant I-1: 头部只追加一组内容
- Rule: renderHeaderMeta 保留原有所有节点语义，只在其末尾追加 `.contact-timing`；主视图不增加卡片、标题区或独立统计行。需要换行时在既有 flex 行内自然换行。
- Applies to: header 模板、标签/状态更新触发的重新渲染。
- Violation consequence: 再次占用大量空间，或刷新后配置入口消失。
- 来源: original；用户已批准的紧凑预览。

### Invariant I-2: 真值在后台
- Rule: 前端不推断国家、不重算习惯、不写 localStorage；只显示 timing 响应。所有日期按明确时区格式化，不能依赖操作者设备默认时区。日期详见依据；北京区间结束跨日时显示“次日”。
- Applies to: header、配置表单、依据弹窗、接口错误状态。
- Violation consequence: 页面与后端结果不一致，或浏览器换时区导致时间改变。
- 来源: original。

### Invariant I-3: 异步结果绑定 contact 与请求代次
- Rule: GET/PUT 及弹窗目录加载都捕获 contactId、请求序号和实例身份；GET 使用 timing.seq，表单加载/保存使用独立 dialogSeq，后台刷新不得使仍打开的保存回调永久失效。只有仍是对应当前请求/联系人/未 disposed 才渲染。切换专家、账号上下文、unmount 关闭自有弹窗并使旧响应失效。已经发出的 A 的保存可以在服务器完成，但不能把 A 的结果写入 B 界面。
- Applies to: selectExpert、refreshConversationQuiet、保存、teardownConversationSubViews、meetingCloseDisposeOnAccountScopeChange、unmount。
- Violation consequence: 快速切换后国家/时间串专家，迟到响应重新打开旧弹窗。
- 来源: K-mailbox-popover-scope-and-fixed-containing-block。

### Invariant I-4: 表单取消零写入，保存有明确状态
- Rule: 国家/时区变化仅修改弹窗草稿；取消/Escape 不 PUT。换国家时 zoneId 重置为空；空值请求传 null。保存期间禁重复提交；失败保留草稿与错误，成功 PUT 后 GET timing；GET 失败不能继续把旧时间标成新推荐。
- Applies to: 配置弹窗、保存错误/成功回调。
- Violation consequence: 取消仍改配置、错误跨国时区残留、连续保存发生重复操作。
- 来源: original。

### Invariant I-5: 自有 dialog 生命周期
- Rule: 使用原生 dialog 直接挂 document.body，最多一个本功能弹窗；所有关闭只移除该实例拥有的节点，绝不清空共享 portal。支持 Escape、关闭按钮、取消；打开聚焦首个可操作元素，关闭恢复仍存在的触发按钮或当前国家按钮。unmount 不再抢焦点。
- Applies to: 配置/依据弹窗与关闭/卸载。
- Violation consequence: 面板被裁剪、遮罩偏移、误删其他业务弹窗、焦点丢失。
- 来源: K-mailbox-popover-scope-and-fixed-containing-block。

### Invariant I-6: CSS 与静态资源契约
- Rule: mailbox-chat.css 字节不变；新样式只在 styles.css 尾部按 S-1/S-2/S-3 逐字追加；无 inline style/未声明新 class。index.html 当前 11 个带版本资源统一改为 `20261002-contact-timing`，不新增 bundle。
- Applies to: JS/样式/HTML/样式测试。
- Violation consequence: 原 CSS 守卫失败、污染全站元素或浏览器混用新旧资源。
- 来源: K-mailbox-chat-css-byte-contract、K-frontend-cache-key-triad。

## 样式契约

以下新增 CSS 三块必须逐字复制到 styles.css。复用 `.mc-text-button`（mailbox-chat.css:109–112）、`.button/.button.primary`（styles.css:802–850），不修改它们的既有规则。所有新增 DOM 元素包含在三个骨架中；属性、文本与重复 option/li 动态填充，层级与 class 不自由改造。没有改动既有 CSS selector，因此全局其他使用点保持原规则。

### S-1: 状态行紧凑信息

插入位置：现有 `.mc-header-meta` 的“查看专家详情 ↗”之后。原骨架详见审计；不加新的 header。小时数字颜色 #285ac0、12px、600；文字11px，组内gap9px，高度至少22px。

```html
<div class="contact-timing" data-role="contact-timing" aria-live="polite">
  <button class="mc-text-button contact-timing-location" type="button" data-action="mc-contact-location" aria-haspopup="dialog">巴西 ▾</button>
  <span class="contact-timing-recommend">建议北京 <strong title="北京时间完整日期区间">01:00–03:00</strong></span>
  <button class="mc-text-button contact-timing-info" type="button" data-action="mc-contact-timing-evidence" aria-label="查看推荐依据" aria-haspopup="dialog">ⓘ</button>
</div>
```

未配置：只保留国家按钮，文案“配置所在地 ▾”。加载/错误：用下面的 span 替换推荐和 ⓘ；加载为“推荐计算中…”，错误为“推荐暂不可用”，错误时追加下面的重试按钮。国家按钮在配置可读但推荐失败时仍可使用；未知配置时打开弹窗必须先读配置，不能假设未配置。

```html
<span class="contact-timing-note">推荐暂不可用</span>
<button class="mc-text-button" type="button" data-action="mc-contact-timing-retry">重试</button>
```

```css
/* contact-timing S-1 */
.mail-chat .contact-timing{display:inline-flex;align-items:center;flex-wrap:wrap;gap:9px;margin-left:auto;padding-left:10px;border-left:1px solid #e1e8f2;min-height:22px;max-width:100%;font-size:11px;line-height:1.6}
.mail-chat .contact-timing-location{max-width:140px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#61748e}
.mail-chat .contact-timing-recommend{display:inline-flex;align-items:center;gap:5px;white-space:nowrap;color:#8494aa;font-size:11px}
.mail-chat .contact-timing-recommend strong{font-size:12px;font-weight:600;color:#285ac0;font-variant-numeric:tabular-nums}
.mail-chat .contact-timing-info{justify-content:center;width:20px;height:22px;padding:0;font-size:14px;color:#8a9bb2}
.mail-chat .contact-timing-note{color:#8494aa;font-size:11px}
.mail-chat .contact-timing .mc-text-button:hover{color:#244ca9;background:#edf3ff}
.mail-chat .contact-timing .mc-text-button:active{background:#dbeafe}
.mail-chat .contact-timing .mc-text-button:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .contact-timing .mc-text-button:focus-visible{outline:2px solid #6389d3;outline-offset:2px}
```

### S-2: 所在地与依据弹窗

直接 body 子节点；不加 `.mail-chat` class（该 class 本身有 grid/高度规则）。dialog 必须通过 showModal 打开，避免正文祖先 overflow/backdrop-filter 的包含块。id 的前缀使用本实例唯一值替换示例 ct-，label/aria-labelledby 同步；user text 必须 escapeText。

配置骨架：

```html
<dialog class="contact-timing-dialog" data-kind="location" aria-labelledby="ct-location-title">
  <header>
    <div><h3 id="ct-location-title">配置所在地</h3><p>仅用于联系时间推荐</p></div>
    <button class="button contact-timing-close" type="button" data-action="mc-contact-dialog-close" aria-label="关闭所在地配置">×</button>
  </header>
  <form data-role="contact-location-form">
    <div class="contact-timing-body">
      <label class="contact-timing-field" for="ct-country">国家 / 地区<select id="ct-country" name="countryCode" required><option value="">请选择国家 / 地区</option></select></label>
      <label class="contact-timing-field" for="ct-zone" data-role="contact-zone-field">具体时区（可选）<select id="ct-zone" name="zoneId"><option value="">使用默认时区</option></select></label>
      <p class="contact-timing-help" data-role="contact-default-zone">默认时区：America/Sao_Paulo</p>
      <p class="contact-timing-help">样本不足时按当地 08:00–17:00 推荐，保存后显示北京时间。</p>
      <p class="contact-timing-error" role="alert" hidden></p>
    </div>
    <footer><button class="button" type="button" data-action="mc-contact-dialog-close">取消</button><button class="button primary" type="submit">保存</button></footer>
  </form>
</dialog>
```

国家、时区 option 使用01目录，不手抄一份国家列表。多时区显示具体时区字段；只有一个 zone 时 hidden 并使用 null。默认 option 显示 `使用默认时区 · {labelZh}（{id}）`，具体 option 显示 `{labelZh}（{id}）`；若 labelZh 已等于 id 则只显示一次。保存中主按钮文案“保存中…”，字段和保存按钮 disabled；关闭/取消仍可用，迟到保存按 I-3 处理。目录/配置加载失败时显示错误，保存 disabled，footer 可追加复用 `.button` 的“重试”按钮。

依据骨架：

```html
<dialog class="contact-timing-dialog" data-kind="evidence" aria-labelledby="ct-evidence-title">
  <header><div><h3 id="ct-evidence-title">推荐依据</h3><p>巴西 · 默认时区 America/Sao_Paulo</p></div><button class="button contact-timing-close" type="button" data-action="mc-contact-dialog-close" aria-label="关闭推荐依据">×</button></header>
  <div class="contact-timing-body">
    <div class="contact-timing-range"><strong>北京 10月3日 00:00–02:00</strong><span>当地 10月2日 13:00–15:00</span></div>
    <p class="contact-timing-help">结合历史回复 · 3 次来信 · 3 个回复日</p>
    <p class="contact-timing-help">基于全部业务账号的已关联来信；最近 180 天，近期记录权重更高。</p>
    <p class="contact-timing-help" data-role="contact-history-limit" hidden>仅使用最近 1000 条范围内的去重来信。</p>
    <ul class="contact-timing-history" aria-label="最近回复时间"><li><span>北京 10月1日 01:15</span><span>当地 9月30日 14:15</span></li></ul>
  </div>
  <footer><button class="button" type="button" data-action="mc-contact-dialog-close">关闭</button></footer>
</dialog>
```

WORK_HOURS 时依据文案为“样本不足，使用当地工作时间 08:00–17:00”；样本数和天数仍如实显示，0样本隐藏 ul；最近时间最多8条，不显示邮件内容。日期跨年时包括年份；起止日期不同，两端均显示日期。截断提示由 historyTruncated 控制。

```css
/* contact-timing S-2 */
.contact-timing-dialog{position:fixed;inset:0;margin:auto;padding:0;width:440px;max-width:calc(100vw - 32px);height:fit-content;max-height:calc(100dvh - 48px);overflow:auto;border:1px solid #d9e3f1;border-radius:14px;background:#fff;color:#475d79;box-shadow:0 20px 90px #17325730;font-family:var(--font-body);font-size:12px;line-height:1.6}
.contact-timing-dialog[open]{display:flex;flex-direction:column}
.contact-timing-dialog[data-kind=evidence]{width:360px}
.contact-timing-dialog,.contact-timing-dialog *{box-sizing:border-box}
.contact-timing-dialog [hidden]{display:none!important}
.contact-timing-dialog::backdrop{background:#172c4738;backdrop-filter:blur(2px)}
.contact-timing-dialog header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;padding:18px 20px;border-bottom:1px solid #edf1f7}
.contact-timing-dialog h3{margin:0;color:#475d79;font-size:16px;font-weight:600;line-height:1.6}
.contact-timing-dialog header p{margin:6px 0 0;color:#8799b0;font-size:11px;line-height:1.6;overflow-wrap:anywhere}
.contact-timing-dialog form{margin:0;min-width:0}
.contact-timing-body{display:flex;flex-direction:column;gap:12px;padding:20px;min-width:0}
.contact-timing-field{display:flex;flex-direction:column;gap:8px;color:#61748e;font-size:12px}
.contact-timing-field select{width:100%;min-width:0;height:37px;min-height:37px;margin:0;padding:0 10px;border:1px solid #dce4ef;border-radius:7px;background:#fcfdff;color:#475d79;font:inherit;font-size:12px}
.contact-timing-field select:hover{border-color:#93b4ec}
.contact-timing-field select:focus-visible{outline:2px solid #6389d3;outline-offset:2px}
.contact-timing-field select:disabled{opacity:.55;cursor:not-allowed}
.contact-timing-dialog .contact-timing-help{margin:0;color:#71849f;font-size:11px;line-height:1.7;overflow-wrap:anywhere}
.contact-timing-dialog .contact-timing-error{margin:0;color:#be123c;font-size:12px;line-height:1.6;overflow-wrap:anywhere}
.contact-timing-range{display:flex;flex-direction:column;gap:4px;color:#61748e;font-size:12px;font-variant-numeric:tabular-nums}
.contact-timing-range strong{color:#285ac0;font-size:13px;font-weight:600}
.contact-timing-history{display:flex;flex-direction:column;gap:6px;margin:0;padding:10px 0 0;list-style:none;border-top:1px solid #edf1f7;color:#71849f;font-size:11px;font-variant-numeric:tabular-nums}
.contact-timing-history li{display:flex;justify-content:space-between;flex-wrap:wrap;gap:4px 12px}
.contact-timing-dialog footer{display:flex;justify-content:flex-end;gap:9px;padding:14px 20px;border-top:1px solid #edf1f7}
.contact-timing-dialog .button{min-height:33px;height:33px;padding:0 13px;font-size:12px}
.contact-timing-dialog .button:focus-visible{outline:2px solid #6389d3;outline-offset:2px}
.contact-timing-dialog .button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.contact-timing-dialog .contact-timing-close{width:28px;height:28px;min-height:28px;padding:0;border:0;background:transparent;color:#91a1b7;font-size:23px}
.contact-timing-dialog .contact-timing-close:hover{background:#edf3ff;color:#2451b9}
.contact-timing-dialog .contact-timing-close:active{background:#dbeafe}
```

普通按钮 hover/active 与 primary 状态复用 styles.css:825–850；disabled/focus 补充如上。不添加全局 p/select/button 覆盖。（来源: K-global-p-is-muted-in-dialogs）

### S-3: 窄屏

```css
/* contact-timing S-3 */
@media(max-width:760px){.mail-chat .contact-timing{margin-left:0;padding-left:0;border-left:0}.contact-timing-dialog{max-height:calc(100dvh - 24px)}.contact-timing-dialog header{padding:14px 16px}.contact-timing-body{padding:16px}.contact-timing-dialog footer{padding:12px 16px}}
```

保留现有 `.mc-header-meta{flex-wrap:wrap}`；不挤掉已有状态、ORCID、标签和详情按钮，不缩整个应用字号。所有新 class 必须来自这三个契约，禁止 inline style 和基于 JS 测量写 style 的浮层定位。

## 现状审计

### 前端样式盘点

- 既有 `.mc-header`：mailbox-chat.css:34 是 flex/wrap/gap12；102 的最终常规 padding 为17px 22px 14px；1100px媒体规则会改padding15px。
- `.mc-header-meta`：mailbox-chat.css:103，flex/wrap/gap8、font11px、color #8a9bb2。
- `.mc-text-button`：109–112，font11px、line-height1.5、padding3px 0、radius4px；hover #244ca9/#edf3ff，active #dbeafe，disabled opacity .45。
- `.mc-badge`：105，10px/1.6、radius5px、padding2px 7px。
- `.button/.button.primary`：styles.css:802–850，height32px、padding0 12px、font12px；保持原渐变/状态，由S-2局部定义弹窗尺寸。
- 全站 token：styles.css:3 主色#1e40af、15 panel-bg rgba(255,255,255,.55)、68 glass-blur blur(16px)；新弹窗背景明确#fff，不继承半透明panel。
- `.panel` 在 styles.css:948–959 带 backdrop-filter；`.mc-conversation` 在 mailbox-chat.css:5 带 overflow:hidden。这两个事实决定新弹窗直接挂body。（来源: K-mailbox-popover-scope-and-fixed-containing-block）

改动前 DOM（mailbox-chat.js:2278–2285，逐字）：

```html
                <header class="mc-header">
                    <div class="mc-identity"></div>
                    <div class="mc-actions"></div>
                    <div class="mc-header-meta"></div>
                </header>
```

改动前状态行赋值（mailbox-chat.js:1819–1822，逐字）：

```javascript
            metaEl.innerHTML = `
                ${statusBadge}${levelBadge}${expertTagSpans}${orcid ? `<span>ORCID ${escapeText(orcid)}</span>` : ""}<button class="mc-text-button" type="button" data-action="mc-open-expert" data-contact-id="${escapeText(Number(instance.selectedContactId))}">查看专家详情 ↗</button>
            `;
```

改动前对应 CSS（逐字）：

```css
.mail-chat .mc-header-meta{display:flex;align-items:center;flex-wrap:wrap;gap:8px;flex-basis:100%;font-size:11px;color:#8a9bb2}
.mail-chat .mc-text-button{display:inline-flex;align-items:center;gap:4px;border:0;border-radius:4px;background:transparent;color:#6482b2;font:inherit;font-size:11px;line-height:1.5;padding:3px 0;cursor:pointer}
.mail-chat .mc-text-button:hover{color:#244ca9;background:#edf3ff}
.mail-chat .mc-text-button:active{background:#dbeafe}
.mail-chat .mc-text-button:disabled{opacity:.45;cursor:not-allowed}
```

以上只追加子节点，不就地修改这些 class 规则。全部 `.mc-header-meta` 使用位置已在 grep-receipts 的 header-all-sites 保留：renderHeader、refreshHeaderExpertTags、conversationContentHtml、管理回调、refreshAfterExpertTagChange。IP-1：这些入口重新渲染原状态→新信息也必须保留。

### store/生命周期/调用入口

- 新后台 store 的完整写/读审计由01/02提供；前端唯一新 writer 为配置保存PUT，reader 为配置GET/目录GET/timingGET。
- 当前实例包含 selectedContactId、seq、convEpoch、disposed、conversation/accountScope；selectExpert:1646附近先 teardown 再换身份；renderConversationContent:2297会重建头部；refreshConversationQuiet:5925合并时间线；unmount:6569清理监听器和portal。
- 原共享 portalRoot 在body，是管理/跟进/材料/模板弹窗复用节点；某些旧close会清空内容。本功能直接拥有独立dialog，不把新节点塞入其innerHTML。IP-2：旧弹窗生命周期→不能删除新弹窗，新弹窗关闭也不能删除旧节点。
- 新增 timing 状态只在实例内保存，建议结构 `{contactId,seq,data,loading,error,dialog,dialogSeq,trigger}`；不写草稿Map/localStorage/sessionStorage，不新增长期cache。IP-3：A请求迟到→B会话不能接收；IP-4：PUT→新GET→主行/依据一致；IP-5：成功刷新来信→新timing读取，原草稿/滚动不变。
- index.html 当前11个?v资源都为20260930-manual-template-reference；该固定字面量在src/test无命中，现有测试从index提取key，无须批量改旧测试。（来源: K-frontend-cache-key-triad）
- mailboxChatStyle.test.js 强制聊天CSS与既有目标文件逐字相等，并从 styles.css 接纳已声明class；因此只新增作用域样式，不放宽守卫。（来源: K-mailbox-chat-css-byte-contract）

## 实现方案

### T-1：实例状态、读取与主行（I-1/I-2/I-3/I-6；S-1/S-3）

文件：mailbox-chat.js、styles.css、mailboxChatBehavior.test.js。

- 在组件实例增加临时timing状态；选择会话后异步GET timing，调用已存在的host API约定，错误捕获不影响会话加载。
- renderHeaderMeta末尾使用统一函数生成S-1，不在每次render里发请求；读取完成只重绘metadata，不重建时间线或草稿。
- 成功刷新当前会话后重新GET timing；保存后强制GET；重复触发用本实例序号覆盖结果，不新增轮询/按列表批量取推荐。
- 明确三个展示状态：未配置、加载/错误、有推荐。错误时隐藏旧时间；重试只重读当前contact，不自动保存。
- 只从ISO offset字段转换，格式化显式指定Asia/Shanghai与effectiveZoneId；主行时间保留两位小时/分钟，跨北京日期的末端写“次日05:00”；title与依据显示完整日期。不得把ISO字符串交给设备默认时区格式化。

### T-2：配置表单与依据（I-2/I-3/I-4/I-5；S-2/S-3）

文件：mailbox-chat.js、styles.css、mailboxChatBehavior.test.js。

- 独立dialog节点绑定自身submit/change/cancel/close/click，不借共享portal事件委托；关闭移除自有节点与引用。
- 第一次打开配置读取目录及当前配置；目录可在本实例内保留成功结果，失败可重试。初始化/回填不产生PUT。已有data可作展示，但提交基于当前打开对象，不能读取别人的selectedContactId代替捕获id。
- 国家变更清空zone草稿；取消关闭。保存处理disabled和错误，只有PUT成功才重新GET timing；若GET失败，表明“所在地已保存，推荐更新失败”，主行不保留旧推荐，并提供重试。
- 点击ⓘ只展示已经成功取得的该contact响应，不额外查正文；无推荐不渲染ⓘ。
- teardownConversationSubViews、账号scope变化的清理入口和unmount统一调用自有close+invalidate；retargetManual若仍同contact且账号上下文未变，保留本功能弹窗与未保存表单；它只改变发信目标，不改变所在地身份。不清除草稿，不重复保存。原生dialog的浏览器焦点行为需真实浏览器验收，不能仅凭DOM stub宣称通过。

### T-3：资源与回归（I-1–I-6；S-1/S-2/S-3）

文件：index.html、mailboxChatBehavior.test.js、contactTimingStyle.test.js。

- 统一更新当前11个资源key；其他标签/注册顺序保持。不给app.js新增入口，不复制world-clock算法。
- 扩展现有mailboxChatBehavior测试harness的配置/timing路由响应以及必要的dialog行为；测试真实组件挂载、点击、提交、异步迟到，不仅抽取新函数做字符串断言。
- 新style测试携带S-1/S-2/S-3完整期望文本，与styles.css追加块对比；检查新class有声明、模板无inline style、聊天CSS字节不变、11个版本键一致。不要删改旧CSS守卫来迁就实现；唯一例外是 A2（2026-10-02 人工批准，见变更文件清单 6/7）的两处守卫最小收窄：① `taskActivityCenter.test.js` S-0 把「end 标记之后必须为空」改为「end 标记后不得再出现第二个 task-center 契约块」（start/end 唯一性与整块逐字内容断言保留）；② `mailboxCalendarIntegration.test.js` 草稿卡用例把 `zoneId`/`startLocal` 的全文件扫描收窄到草稿卡渲染代码路径（保留「必须走 formatBeijingMeetingRange」等断言）。禁止字符串拼接等规避；mailbox-chat.css 字节守卫与其余断言一律不动。

## 变更文件清单

| # | 路径 | 操作 |
|---:|---|---|
| 1 | src/main/resources/static/mailbox-chat.js | 当前组件接入 |
| 2 | src/main/resources/static/styles.css | 尾部逐字追加S-1/S-2/S-3 |
| 3 | src/main/resources/static/index.html | 统一11个资源版本键 |
| 4 | src/test/js/mailboxChatBehavior.test.js | 扩展行为用例与harness |
| 5 | src/test/js/contactTimingStyle.test.js | 新增样式契约验证 |
| 6 | src/test/js/taskActivityCenter.test.js | A2 最小收窄：S-0 尾部空断言改为「end 标记后不得再出现第二个 task-center 契约块」 |
| 7 | src/test/js/mailboxCalendarIntegration.test.js | A2 最小收窄：草稿卡 zoneId/startLocal 扫描收窄到草稿卡渲染代码路径 |

## 验收标准

- I-1/IP-1：状态/标签变化及quiet refresh后，新组各只有1个且保留原节点语义；没有MutationObserver注入，没有独立推荐大卡片。
- I-2：stub后端返回有/无推荐；断言不调用前端学习算法/不写localStorage。设备时区UTC与Asia/Tokyo时，统一API返回的北京时间文字相同。
- I-3/IP-3：延迟A的GET、切B并先返回B、最后返回A；最终仅B。保存A过程中切B；A保存完成不重绘B、不再打开A弹窗。unmount后响应无DOM操作。
- I-4/IP-4：取消、Escape=0 PUT；换国家清空zone；null/显式值请求JSON准确；双击保存=1 PUT；PUT失败留草稿；PUT成功GET失败显示指定文案，不展示旧时间。
- I-5/IP-2：只移除自有dialog；真实浏览器验证showModal、Escape、Tab焦点、关闭恢复、视口内居中与不被overflow裁剪；既有管理/材料/模板弹窗仍能依次打开。
- I-6：git diff确认mailbox-chat.css未改；11个资源键一致；无新增构建依赖、app.js变更。
- S-1：逐字CSS相等、骨架层级与新组位置一致；蓝色小时12px/600、gap9px、min-height22px，重试/禁用/focus状态有覆盖。
- S-2：逐字CSS相等；dialog直接body、背景#fff、配置宽440px/依据360px；p文本按作用域颜色显示；所有新元素在契约内，无inline style。
- S-3：760px断点规则逐字相等；窄屏主行自然换行，新弹窗不超过视口宽-32px。
- IP-5/N-1/N-2：编辑草稿与滚动后刷新、查看推荐/保存所在地，草稿、发送目标与滚动锚点保持；原头部功能回归。
- 命令：`node --check src/main/resources/static/mailbox-chat.js`；`node --test src/test/js/mailboxChatBehavior.test.js src/test/js/contactTimingStyle.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxTemplateReferenceStyle.test.js src/test/js/mailboxCalendarIntegration.test.js`。通过后运行 `node --test src/test/js/*.test.js`；最终按总计划 Maven 回归。不重复跑已通过检查，除非有新改动/失败。

## 人工验收清单

### A-1: 紧凑行与默认配置
- 前置条件: 已登录测试环境，有无来信且未配置的测试专家；浏览器宽1440px。
- 操作步骤: 1. 选中专家。2. 点“配置所在地 ▾”。3. 选印度、保存。4. 刷新整个页面。5. 点ⓘ。
- 预期结果: 所在地与建议在状态行，显示“印度”“建议北京10:30–19:30”；没有蓝色推荐大卡片；弹窗宽440px、白底；印度只有一个时区，不显示多时区选择；重载仍为印度；依据显示当地08:00–17:00与样本不足。
- 覆盖: O-1/O-2/O-3；I-1/I-2/I-6；S-1/S-2；IP-4。

### A-2: 多时区、取消与失败
- 前置条件: 测试专家配置US/null；浏览器开发者工具可以临时阻断PUT请求。
- 操作步骤: 1. 打开配置，选America/Los_Angeles，点取消。2. 再开，确认原值。3. 改国家为BR，确认时区重置，选America/Manaus。4. 阻断PUT后保存。5. 恢复网络并保存。6. 点ⓘ。
- 预期结果: 取消后仍US/默认America/New_York；换国后不会残留美国时区；失败时保留BR/Manaus草稿并显示错误，不能假装保存；成功后头部巴西，依据America/Manaus且为手动时区。
- 覆盖: O-2；I-2/I-4；S-2；IP-4。

### A-3: 快速切换与原功能回归
- 前置条件: 两名测试专家A=印度、B=巴西；A有已编辑未发送草稿；网络限速。
- 操作步骤: 1. 打开A后马上切B，等待旧请求返回。2. 打开/关闭B的依据，再回A。3. 检查草稿与原发送目标。4. 操作关注、管理状态、标签、材料、排期入口，并刷新会话。5. 滚到一封旧信后查看/关闭依据。
- 预期结果: B始终显示巴西；A草稿文字和发送目标保留；原状态/标签更新后新时间组仍只出现一次；材料/排期打开原入口；查看推荐不跳到最新邮件、不增加发信。
- 覆盖: N-1/N-2；I-1/I-3/I-5；IP-1/IP-2/IP-3/IP-5。

### A-4: 窄屏、日期与键盘
- 前置条件: 测试专家为纽约，0回复；测试时间处于夏令时；浏览器宽760px，再切到390px。可通过浏览器时区模拟改为UTC。
- 操作步骤: 1. 查看主行与ⓘ日期。2. 开配置，用Tab遍历、Escape关闭。3. 开依据、关闭。4. 改浏览器时区为UTC，重载并复查。
- 预期结果: 北京区间为20:00–次日05:00，详情展示两端实际日期；改设备时区后文字不变；新内容不造成横向页面溢出；弹窗不被聊天边界裁剪；关闭后焦点回当前国家或ⓘ按钮；窄屏组左边框/左padding取消，gap仍9px。
- 覆盖: O-1/O-3；I-2/I-5/I-6；S-1/S-2/S-3。
