# P2 · 详情头部布局方向 C + 账号上移 + 标签 C-1

> 前置：`expert-detail-head-main.md`（共享不变量 M-1 / M-3 / M-4、共享验证命令）。
> **必须在 `expert-detail-head-p1-preview-sender-account.md` 之后实施**（理由见「顺序依赖」）。
> 本计划**纯前端**，零后端改动。

## 需求描述

**可观察结果**

1. 「绑定发件账号」不再是详情区的 metadata 卡片；它出现在顶部操作栏主行，形态为一颗 pill（`⬤ 发件 LiLei ▾`），点击展开浮层，浮层内是账号下拉 + 「保存绑定」。
2. 操作栏主行常驻「账号 pill｜模板下拉｜发送邮件｜⚙ 更多」；「状态 / 层级 / 回复模式 / 保存变更」默认折叠在「更多」里，点击展开；展开状态在切换专家后保持不变。
3. 账号下拉被改动但未保存时：pill 变琥珀色、旁边出现「⚠ 账号未保存」、「发送邮件」按钮置灰不可点。保存后三者复位。
4. 未绑定账号的专家，pill 显示灰点 + 「发件 未绑定」。
5. 「专家标签」不再是独立区块；标签 chips 与「＋」按钮出现在专家姓名行右侧。标签多于 3 个时只显示前 3 个 + 「+N」按钮，点击展开全部。
6. ES 无画像的专家，姓名行右侧显示一颗虚线 pill「ES 无画像」（悬停给完整说明），不再占用一整个区块说明"标签功能不可用"。

**必须不变（must NOT change）**

1. 手动发送请求体仍为 `{optionType, optionValue, senderAccountCode: null}`，实际账号仍由后端读绑定解析（共享不变量 M-1）。
2. `renderExpertTagEditor` 在**不传 `layout` 参数**时输出逐字不变（共享不变量 M-3）；收发件箱两处调用（`app.js:9031`、`app.js:9600`）视觉与 DOM 不变。
3. 专家详情四个子标签的键名、顺序与 `data-panel` 在 `app.js` 中各出现 2 次这一事实不变。
4. 专家列表项里的标签 chips 与「发送账号已变更」标记（`app.js:4734` / `:4744`，共用 `.expert-tag` 基础规则）视觉不变。
5. 「保存变更」按钮对状态/层级/回复模式三个 select 的既有脏检查行为（`app.js:8799-8824 updateSaveButtonState`，含其内联样式实现）不变。
6. 标签的增删仍按 `orcidId + level` 读写 ES（`app.js:4041 fetchExpertTagsFromEs` / `mutateExpertTag`），不改为读列表缓存。

**Out of scope**

- 方向 A / B / D 的任何形态。
- `updateSaveButtonState` 的内联样式重构（改成 class）—— 属既有实现，本轮不动。
- 账号 pill 浮层内**不做**账号搜索/过滤，就是一个 `<select>`。
- 标签「+N」展开**不做**浮层，就是把被 `hidden` 的 chips 显示出来。
- 「清除标记」按钮的位置调整以外的任何行为变化。
- `#contactHeadActions` 在 `showExpertDetail()` 路径下仍然被清空隐藏（`app.js:6642-6643`），本计划不给该路径新增操作栏。

## 顺序依赖

P2 的验收项 **A-9**（改绑保存后，邮件预览标签页的签名随之改变）只有在 P1 落地后才存在可观察差异 —— P1 之前预览的 sender 变量恒为空串，改绑前后都一样。P2 的其余部分不依赖 P1。

## 关键不变量

### I-1: 发送请求体不携带账号码

- Rule: `send-manual-mail` 分支的请求体第三个字段恒为 `senderAccountCode: null`，**不得**改为读取 `#senderBindingSelect` 的值。
- Applies to: `app.js:8554-8571` `handleContactAction` 的 `send-manual-mail` 分支。
- Violation consequence: `ManualExpertMailService.resolveAccount`（`ManualExpertMailService.kt:162-167`）的 I-3 校验规定「显式值与绑定值都非空且不等 → `IllegalArgumentException`」。运营改了下拉未保存就点发送，会收到一个 500/400 而不是预期的发送成功；即便值相等也只是碰巧不炸。
- 来源: original（证据：逐字读取 `ManualExpertMailService.kt:159-177`）

### I-2: 未保存闸门

- Rule: 当 `#senderBindingSelect.value !== #senderBindingSelect.dataset.original` 时，必须同时满足三条：① `#senderBindingDirtyNote` 的 `hidden` 为 false；② `#sendManualMailBtn.disabled === true`；③ 账号 pill 带 `data-dirty="true"`。三者由**同一个函数**（`updateSenderBindingDirtyState()`）设置，不得分散。值相等时三者全部复位。
- Applies to: `app.js` 新增 `updateSenderBindingDirtyState()`；`#contactHeadActions` 的 change 委托（`app.js:11142-11148`）；`loadContactDetail` 渲染后的初始化调用。
- Violation consequence: 闸门是方案 A 的全部安全性所在。缺 ② 则运营可以在脏态下发信，发出去的是旧账号且没有任何提示 —— 这正是把两个控件放同一行引入的新风险。
- 来源: original

### I-3: `dataset.original` 是绑定值的唯一真值来源

- Rule: `#senderBindingSelect` 的 `data-original` 属性在渲染时写入 `contact.boundSenderAccountCode || ""`，此后**只读**。任何脏检查都以它为基准，不得改用"第一个 option"或 `selectedIndex === 0` 之类的推断。
- Applies to: `app.js:6979` 起的 `#contactHeadActions` 模板；`updateSenderBindingDirtyState()`。
- Violation consequence: 未绑定专家的 select 因无 option 命中 `selected`，浏览器默认选中第一项（现状 `app.js:7215-7219` 的 `selected` 比较对 `null` 恒不成立）。若以 `selectedIndex` 推断"未改动"，未绑定专家会被永久判为干净，闸门形同虚设。
- 来源: original（证据：`app.js:7214-7219` 的 option 渲染逻辑）

### I-4: 折叠态持久化在 `state`，切专家不重置

- Rule: 折叠态存于 `state.contactHeadExpanded`（布尔，初值 `false`）。`loadContactDetail` 渲染时据它决定 `#contactHeadMoreRow` 的 `hidden` 与 `#contactHeadMoreToggle` 的 `aria-expanded`。切换到另一位专家不得重置该值。
- Applies to: `app.js:5` 起的 `state` 字面量；`loadContactDetail`（`app.js:6967`）；`handleContactAction` 新增的 `toggle-contact-head-more` 分支。
- Violation consequence: 若存在 DOM 上，每次 `loadContactDetail` 重写 `#contactHeadActions.innerHTML`（`app.js:6979`）都会丢失，运营每点一位专家都要重新展开。
- 来源: original

### I-5: `renderExpertTagEditor` 的默认输出逐字不变

- Rule: 新增的第 6 个参数 `layout` 默认值必须是 `"section"`，且 `layout !== "inline"` 时函数返回**与改动前逐字相同**的字符串（含缩进与换行）。
- Applies to: `app.js:3964-3992 renderExpertTagEditor`。
- Violation consequence: `expertProfileAbsence.test.js:77 / :93 / :114` 三处 `assert.strictEqual(normalizeWhitespace(html), S*_EXPECTED)` 失败；收发件箱专家概览的 `.mail-expert-overview .expert-tag-editor` 专门样式（`styles.css:2132 / 2183 / 2246`）失配。
- 来源: 共享不变量 M-3（证据：逐字读取 `expertProfileAbsence.test.js:46-67`）

### I-6: 内联编辑器必须保留标签动作赖以定位的三件事

- Rule: `layout === "inline"` 的输出根元素必须同时带 `class` 含 `expert-tag-editor`、`id="${editorId}"`、`data-orcid`、`data-level`，并额外带 `data-layout="inline"`。
- Applies to: `renderExpertTagEditor` 的 inline 分支。
- Violation consequence: `handleContactAction` 的 `expert-add-tag-open`（`app.js:8596`）与 `expert-remove-tag`（`app.js:8625`）都用 `element.closest(".expert-tag-editor")` 定位，再读 `editor.dataset.orcid` / `dataset.level` / `editor.id`。缺任一项 → 加删标签静默 return 或用错 orcid。
- 来源: original（证据：逐字读取 `app.js:8595-8641`）

### I-7: 重渲染必须保持布局形态

- Rule: `updateExpertTagEditor(orcidId, tags, level, editorId)`（`app.js:4077-4081`）在把 `editor.outerHTML` 换掉之前，必须先从 `editor.dataset.layout` 读回当前形态并原样传给 `renderExpertTagEditor`。
- Applies to: `app.js:4077-4081`。
- Violation consequence: 现有实现调用时**不传第 5、6 个参数**（`app.js:4080`）。加完/删完一个标签后，内联编辑器会被替换成 `.detail-section` 块级形态，姓名行瞬间炸开成两行 —— 而且只在"加删标签之后"复现，是最难在自测中发现的一类回归。
- 来源: original（证据：逐字读取 `app.js:4077-4081`）

### I-8: 加载遮罩不得撑高内联行

- Rule: `.expert-tag-editor.is-inline` 处于加载态时，`.tag-editor-loading` 的 `min-height: 72px`（`styles.css:3715-3718`）必须被覆盖为 `0`。
- Applies to: `styles.css` 新增规则（见 S-6）。
- Violation consequence: `setTagEditorLoading`（`app.js:4083-4102`）给编辑器加 `.tag-editor-loading` 类，内联形态下姓名行在加删标签的一瞬间从 40px 撑到 72px 再弹回。
- 来源: original（证据：逐字读取 `styles.css:3715-3718` 与 `app.js:4083-4102`）

### I-9: 未绑定态必须可见

- Rule: `contact.boundSenderAccountCode` 为空/空白时，pill 必须渲染为灰点（`.sender-binding-dot.is-unbound`）+ 文本 `未绑定`。不得只显示一个空的账号名。
- Applies to: `app.js` `#contactHeadActions` 模板。
- Violation consequence: 现状里"未绑定"三个字由被删除的 metadata 卡片（`app.js:7068`）承担。若不在 pill 上补回，未绑定这一状态在新布局中**完全不可见**，而未绑定专家点「发送邮件」会走 `selectAccountForManualSending()` 自动选号（`ManualExpertMailService.kt:173-176`），选中哪个账号运营完全不知情。
- 来源: original（证据：`app.js:7068` 与 `ManualExpertMailService.kt:168-177`）

### I-10: 子标签面板计数不变

- Rule: 改动后 `grep -c 'data-panel="mail-preview"' app.js` 与 `grep -c 'data-panel="template"' app.js` 必须仍然相等，且 `<div class="detail-tab-panel" data-panel="mail-preview"` 仍出现 2 次。
- Applies to: `app.js` 的 `showExpertDetail`（`:6629`）与 `loadContactDetail`（`:6967`）两处面板 DOM。
- Violation consequence: `expertMailPreviewTab.test.js:322-329` 直接失败；且按 `K-expert-detail-two-panel-render-sites`，漏改一处是**静默空白面板**，`activateDetailSubTab` 的 `p.hidden = p.dataset.panel !== tabKey` 不会抛错。
- 来源: K-expert-detail-two-panel-render-sites（本轮重新验证，行号已按主计划的漂移表修正）

## 样式契约

> 设计基准逐字取自本仓库 `src/main/resources/static/styles.css` 的 `:root`（`styles.css:1-80`）。
> **不适用** `K-qingfei-site-design-tokens-source`（那是公网页面对齐官网的基准，见 `K-public-page-not-admin-css`）。
> 所有新增规则一律追加在 `styles.css` 末尾的新注释段 `/* === 专家详情头部 C 布局 === */` 下，**不得**插入到既有规则块中间。

### S-1: 操作栏主行容器

- **复用**：`.contact-head-actions`（`styles.css:1358-1364`，`flex-direction: column; gap: 10px`）保持不变，继续作为外层。
- **新增**：

```css
/* === 专家详情头部 C 布局 === */
.contact-head-main-row {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
    flex-wrap: wrap;
    flex: 1 1 auto;
}

.contact-head-divider {
    width: 1px;
    height: 18px;
    background: var(--border);
    flex-shrink: 0;
    margin: 0 2px;
}
```

- **DOM 结构**：见 S-8 的完整骨架。
- **禁止项**：inline style；不得修改 `.contact-head-status-row` / `.contact-head-mail-row` 的既有规则块（`styles.css:1366-1381`）—— 折叠行继续复用 `.contact-head-status-row`。

### S-2: 账号 pill

- **复用**：`.dropdown`（`styles.css:480-482`，仅 `position: relative`）作为定位容器。
- **复用**：`[hidden] { display: none !important; }`（`styles.css:91-93`）作为浮层显隐机制 —— 与「发现专家」下拉的既有做法一致（`index.html:583` + `app.js:11405-11424`）。
- **新增**：

```css
.sender-binding-pill {
    height: 28px;
    min-height: 28px;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 0 9px;
    flex-shrink: 0;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    background: var(--bg-main);
    color: var(--text-secondary);
    font-size: 11px;
    font-family: inherit;
    cursor: pointer;
    transition: var(--transition);
}

.sender-binding-pill:hover {
    border-color: var(--border-strong);
    color: var(--text-main);
}

.sender-binding-pill b {
    color: var(--text-main);
    font-weight: 600;
}

.sender-binding-pill .sender-binding-caret {
    color: var(--text-muted);
    font-size: 9px;
}

.sender-binding-pill[data-dirty="true"] {
    background: var(--warning-bg);
    border-color: var(--warning-border);
    color: var(--warning-strong);
}

.sender-binding-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--success);
    flex-shrink: 0;
}

.sender-binding-dot.is-unbound {
    background: var(--text-muted);
}

.sender-binding-pill[data-dirty="true"] .sender-binding-dot {
    background: var(--warning);
}
```

- **DOM 结构**：见 S-8。
- **禁止项**：不得给 `.dropdown` 或 `.dropdown-menu`（`styles.css:480-498`）的既有规则块增删属性 —— 它们被「发现专家」下拉（`index.html:583-588`）共用。

### S-3: 账号浮层

- **复用**：`.dropdown-menu`（`styles.css:484-498`）提供定位/阴影/圆角骨架；`.dropdown-menu:not([hidden])` 的入场动画（`styles.css:3431-3433`）自动继承。
- **复用**：`.button.primary.small`（`styles.css:2329-2334` + `.button` 基础规则 `:655`）作为浮层内的保存按钮。
- **新增**（`.sender-binding-pop` 是 `.dropdown-menu` 的修饰类，只覆盖必要属性）：

```css
.dropdown-menu.sender-binding-pop {
    right: auto;
    left: 0;
    width: 258px;
    min-width: 258px;
    padding: 10px;
    gap: 8px;
    background: var(--bg-sidebar);
    cursor: default;
    text-align: left;
}

.sender-binding-pop-label {
    font-size: 10.5px;
    font-weight: 700;
    color: var(--text-muted);
    text-transform: uppercase;
    letter-spacing: 0.3px;
}

.sender-binding-pop #senderBindingSelect {
    width: 100%;
    flex: none;
}

.sender-binding-pop-hint {
    margin: 0;
    font-size: 10.5px;
    line-height: 1.45;
    color: var(--warning-strong);
    background: var(--warning-bg);
    border: 1px solid var(--warning-border);
    border-radius: var(--radius-sm);
    padding: 5px 7px;
}

.sender-binding-pop-foot {
    display: flex;
    align-items: center;
    gap: 6px;
}
```

- **禁止项**：不得把 `right: auto; left: 0;` 写成 inline style（`index.html:583` 是既有技术债，**不作为范例**）。

### S-4: 未保存提示

- **新增**：

```css
.contact-head-dirty-note {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    height: 28px;
    padding: 0 8px;
    flex-shrink: 0;
    white-space: nowrap;
    font-size: 11px;
    color: var(--warning-strong);
    background: var(--warning-bg);
    border: 1px solid var(--warning-border);
    border-radius: var(--radius-sm);
}
```

- **禁止项**：置灰「发送邮件」不得新增 class —— 用原生 `disabled` 属性，`.button[disabled]` 已在既有规则中处理（若 `styles.css` 无 `.button[disabled]` 规则，落地时在本段追加 `.contact-head-actions .button[disabled] { opacity: 0.5; cursor: not-allowed; }`，并在 PR 说明中注明是新增）。

### S-5: 「更多」折叠

- **复用**：`.button`（`styles.css:655`）+ `.contact-head-actions .button`（`styles.css:1430-1436`，`height: 28px; padding: 0 10px; font-size: 11px`）。
- **复用**：`.contact-head-status-row`（`styles.css:1366-1377`）原样作为折叠行的容器 class，配 `hidden` 属性显隐。
- **新增**：

```css
.contact-head-more-toggle[aria-expanded="true"] {
    background: var(--primary-tint);
    border-color: rgba(var(--primary-rgb), 0.2);
    color: var(--primary);
}
```

- **禁止项**：不得用 `display: none` 的自定义 class 做折叠 —— 用 `hidden` 属性，`styles.css:91-93` 的 `[hidden] { display: none !important; }` 已覆盖。

### S-6: 标签内联形态

- **复用**：`.expert-tag` 及其 4 个变体（`styles.css:4486-4520`）、`.expert-tag .expert-tag-remove`（`:4529-4543`）、`.inbound-tag-editor-chips`（`:4522-4527`）—— **全部就地不改**。
  该 class 的使用点全集（grep `class="expert-tag`）：`app.js:3976`（编辑器 chips）、`app.js:4734`（列表标签）、`app.js:4744`（列表「发送账号已变更」）。因存在列表使用点，**一律派生新 class，禁止就地修改**。
- **新增**：

```css
.expert-tag-editor.is-inline {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    flex-wrap: nowrap;
    min-width: 0;
    margin-left: auto;
}

.expert-tag-editor.is-inline .inbound-tag-editor-chips {
    gap: 6px;
    flex-wrap: nowrap;
}

.expert-tag-editor.is-inline[data-expanded="true"],
.expert-tag-editor.is-inline[data-expanded="true"] .inbound-tag-editor-chips {
    flex-wrap: wrap;
}

.expert-tag-add-btn,
.expert-tag-more-btn {
    height: 22px;
    min-height: 22px;
    padding: 0 7px;
    font-size: 11px;
    line-height: 1;
    border-radius: 6px;
    flex-shrink: 0;
}

.expert-tag-add-btn {
    width: 22px;
    padding: 0;
    justify-content: center;
    font-size: 13px;
    color: var(--text-muted);
}

.expert-tag-add-btn:hover {
    color: var(--primary);
    border-color: rgba(var(--primary-rgb), 0.25);
    background: var(--primary-light);
}

.expert-tag-nodoc {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 1px 8px;
    line-height: 16px;
    font-size: 10.5px;
    color: var(--text-muted);
    background: var(--surface);
    border: 1px dashed var(--border-strong);
    border-radius: var(--radius-sm);
    white-space: nowrap;
    cursor: help;
}

.expert-tag-nodoc::before {
    content: '';
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: var(--text-muted);
    flex-shrink: 0;
}

/* I-8：内联形态下加载遮罩不得撑高姓名行 */
.expert-tag-editor.is-inline.tag-editor-loading {
    min-height: 0;
}
```

- **DOM 结构**：见 S-7。
- **禁止项**：不得修改 `.expert-tag`、`.expert-tag-remove`、`.inbound-tag-editor-chips`、`.detail-section`、`.tag-editor-loading`（除上面那条 `.is-inline` 后代覆盖外）、`.mail-expert-overview .expert-tag-editor`（`styles.css:2132 / 2183 / 2246`）的任何既有规则块。

### S-7: 标签编辑器 DOM 骨架（逐字）

**`layout === "inline"` 且 `profileMissing !== true`**：

```html
<span class="expert-tag-editor is-inline" id="${editorId}" data-orcid="${orcidId}" data-level="${level}" data-layout="inline">
    <span class="inbound-tag-editor-chips">${chips}</span>
    <button type="button" class="button small expert-tag-add-btn" data-action="expert-add-tag-open" title="添加标签" aria-label="添加标签">＋</button>
</span>
```

其中 `${chips}` 的每一项与既有 `app.js:3975-3980` 逐字相同（`<span class="expert-tag tag-XXX">…<button class="expert-tag-remove" …>×</button></span>`），仅在 `tags.length > 3` 时给**第 4 项及之后**追加 ` hidden` 属性，并在 chips 末尾追加：

```html
<button type="button" class="button small expert-tag-more-btn" data-action="expert-tags-expand">+${tags.length - 3}</button>
```

`tags` 为空时 `${chips}` 为 `<span class="muted">暂无标签</span>`（与既有一致）。

**`layout === "inline"` 且 `profileMissing === true`**：

```html
<span class="expert-tag-editor is-inline" id="${editorId}" data-orcid="${orcidId}" data-level="${level}" data-layout="inline" data-profile-missing="true">
    <span class="expert-tag-nodoc" title="该专家在 ES 中无画像文档，标签功能不可用">ES 无画像</span>
</span>
```

**该分支不得包含 `data-action="expert-add-tag-open"` 或 `data-action="expert-remove-tag"`**（与既有 S-1 契约同规则）。

**`layout` 不为 `"inline"` 时**：输出与改动前**逐字相同**（I-5），即 `expertProfileAbsence.test.js:46-67` 的 `S1_EXPECTED` / `S2_EXPECTED`。

### S-8: 操作栏 DOM 骨架（逐字）

`loadContactDetail` 中 `$("#contactHeadActions").innerHTML` 的完整结构：

```html
<div class="contact-head-main-row">
    <span class="dropdown sender-binding">
        <button type="button" class="sender-binding-pill" id="senderBindingToggle"
                aria-haspopup="true" aria-expanded="false" data-dirty="false">
            <span class="sender-binding-dot${BOUND ? "" : " is-unbound"}"></span>
            <span>发件</span>
            <b id="senderBindingCurrentLabel">${BOUND || "未绑定"}</b>
            <span class="sender-binding-caret" aria-hidden="true">▾</span>
        </button>
        <div class="dropdown-menu sender-binding-pop" id="senderBindingPop" hidden>
            <span class="sender-binding-pop-label">绑定发件账号</span>
            <select id="senderBindingSelect" data-original="${BOUND || ""}" aria-label="绑定发件账号"></select>
            <p class="sender-binding-pop-hint">改绑会记录一条审计并给该专家打「已变更」标记；会话进行中时，旧账号仍负责接收该专家的回信。</p>
            <div class="sender-binding-pop-foot">
                <button type="button" class="button primary small" data-action="rebind-sender-account" data-id="${contact.id}">保存绑定</button>
                ${CHANGED ? `<button type="button" class="button small" data-action="clear-sender-change-mark" data-id="${contact.id}">清除标记</button>` : ""}
            </div>
        </div>
    </span>
    <span class="contact-head-dirty-note" id="senderBindingDirtyNote" hidden>⚠ 账号未保存</span>
    <span class="contact-head-divider"></span>
    <select id="manualMailOption" aria-label="选择要发送的邮件">${renderMailSendOptionGroups(options)}</select>
    <button class="button primary" id="sendManualMailBtn" data-action="send-manual-mail" data-id="${contact.id}">
        <span>发送邮件</span>
    </button>
    <span class="contact-head-divider"></span>
    <button type="button" class="button contact-head-more-toggle" id="contactHeadMoreToggle"
            data-action="toggle-contact-head-more" aria-expanded="${EXPANDED}">⚙ 更多</button>
</div>
<div class="contact-head-status-row" id="contactHeadMoreRow" ${EXPANDED ? "" : "hidden"}>
    <span class="contact-head-label">状态</span>
    <select id="operatorStatusSelect" data-contact-id="${contact.id}" data-original="${contact.operatorStatus || ""}" aria-label="专家状态">…</select>
    <select id="indexLevelSelect" data-contact-id="${contact.id}" data-original="${contact.currentIndexLevel || ""}" aria-label="专家层级">…</select>
    <select id="autoReplySelect" data-contact-id="${contact.id}" data-original="${contact.autoReplyEnabled ? "auto" : "manual"}" aria-label="回复模式">…</select>
    <button class="button primary" id="saveContactChangesBtn" data-contact-id="${contact.id}" disabled>保存变更</button>
</div>
```

三个 select 的 `<option>` 生成方式、`data-original` 取值、`#saveContactChangesBtn` 的 id 与初始 `disabled` **与改动前逐字相同**（`app.js:6980-6995`）—— 只是从第一行搬到折叠行。

**禁止项**：inline style；未在 S-1..S-6 声明的新 class；`.contact-head-mail-row` 这个 class 在改动后**不应再出现在 `app.js` 中**（其 CSS 规则块保留不删，避免波及 `styles.css:4111 / 4177 / 4187 / 4197` 四处响应式规则的改写）。

### S-9: 姓名行 DOM 骨架（逐字）

`loadContactDetail`（`app.js:7025-7035`）与 `showExpertDetail`（`app.js:6649-6661`）两处的姓名行改为：

```html
<div class="expert-profile-header">
    <div class="expert-avatar">${initial}</div>
    <div class="expert-header-info">
        <h2>${name}</h2>
        <p>…既有内容逐字不变…</p>
    </div>
    ${renderExpertTagEditor(tags, orcidId, level, "expertTagEditor", profileMissing, "inline")}
</div>
```

- **复用**：`.expert-profile-header`（`styles.css:1447-1452`，`display:flex; align-items:center; gap:12px`）、`.expert-avatar`（`:1454-1468`）、`.expert-header-info`（`:1470-1495`）—— 全部就地不改。内联编辑器靠 S-6 的 `margin-left: auto` 靠右。
- 原先位于姓名行与子标签行之间的 `${renderExpertTagEditor(...)}` 整行（`app.js:7035` / `app.js:6661`）删除。

## 现状审计

### `#contactHeadActions`（DOM 容器）

- 宿主：`index.html:676` `<div class="contact-head-actions" id="contactHeadActions" hidden></div>`（在 `.panel-head.contact-detail-head` 内）。
- **写路径（grep `#contactHeadActions` 全集，5 处）**
  1. `app.js:6642-6643` `showExpertDetail()` —— `hidden = true` + `innerHTML = ""`。**本计划不改**（该路径无操作栏）。
  2. `app.js:6978-7005` `loadContactDetail()` —— `hidden = false` + 写入两行模板。**本计划改这里。**
- **读/事件路径（grep 全集，2 处委托）**
  3. `app.js:11070-11141` click 委托 —— `button[data-action]` → `handleContactAction`；`#saveContactChangesBtn` 单独分支。**新增的 `rebind-sender-account` / `clear-sender-change-mark` / `send-manual-mail` / `toggle-contact-head-more` 全部走 `button[data-action]` 通道，无需新增监听器。**
  4. `app.js:11142-11148` change 委托 —— 仅对 `operatorStatusSelect` / `indexLevelSelect` / `autoReplySelect` 调 `updateSaveButtonState()`。**本计划在此新增 `senderBindingSelect` 分支。**
- **Interaction points**
  - IP-1：写路径 2（渲染）↔ 事件路径 3。`rebind-sender-account` 分支（`app.js:8573-8584`）读 `$("#senderBindingSelect")?.value` —— 全局 id 查询，浮层内也能取到，**无需改该分支**。
  - IP-2：写路径 2 ↔ 事件路径 4。脏检查入口。
  - IP-3：写路径 2 ↔ `app.js:7209-7220` 的 select 填充块。填充用全局 `$("#senderBindingSelect")`，而 `#contactHeadActions` 在 `:6979` 已写入、早于 `:7209`，**填充逻辑可原样保留**（本计划把它移到紧跟 `#contactHeadActions` 写入之后，理由见 T2）。

### `renderExpertTagEditor`（共享渲染函数）

- 定义：`app.js:3964-3992`。
- **调用点全集（grep `renderExpertTagEditor` 5 处）**
  1. `app.js:4080` `updateExpertTagEditor` —— 加删标签后重渲染，**当前不传第 5、6 参**。→ I-7 必须改。
  2. `app.js:4477` `renderMailboxExpertTagEditor` —— 收发件箱转发层，被 `app.js:9031` 与 `app.js:9600` 调用。**必须走默认 section 形态**（must-NOT-change 2）。
  3. `app.js:6661` `showExpertDetail` —— 专家（无 contact）详情。→ 改为 inline。
  4. `app.js:7035` `loadContactDetail` —— 联系人详情。→ 改为 inline。
- **依赖该函数输出结构的消费者**
  - `app.js:8596` / `:8625`：`element.closest(".expert-tag-editor")` + `dataset.orcid` / `dataset.level` / `editor.id`（→ I-6）
  - `app.js:4078`：`document.getElementById(editorId)` + `editor.dataset.orcid !== orcidId` 守卫
  - `app.js:4083-4102 setTagEditorLoading`：`editor.classList.toggle("tag-editor-loading")` + `editor.querySelectorAll("button")` + `editor.querySelector(":scope > .tag-editor-loading-overlay")`（→ I-8）
  - `src/test/js/expertProfileAbsence.test.js:46-67`：`S1_EXPECTED` / `S2_EXPECTED` 逐字断言（→ I-5）
  - `styles.css:2132 / 2183 / 2246`：`.mail-expert-overview .expert-tag-editor` 专门规则（→ must-NOT-change 2）
- **Interaction points**
  - IP-4：调用点 1（重渲染）↔ 调用点 3/4（inline 首渲染）。形态必须一致，否则加删标签后布局炸开。
  - IP-5：调用点 2（收发件箱）↔ I-5。默认形态必须逐字不变。

### 前端样式盘点

**可复用 class（本计划直接引用，不重写）**

| class | 位置 | 用途 |
|---|---|---|
| `.contact-head-actions` | `styles.css:1358-1364` | 操作栏外层 flex column |
| `.contact-head-status-row` | `styles.css:1366-1377` | 折叠行容器 |
| `.contact-head-label` | `styles.css:1383-1392` | 折叠行的「状态」标签 |
| `.contact-head-actions select` | `styles.css:1394-1404` | 28px 高、11px 字号的 select 基线 |
| `#operatorStatusSelect` / `#indexLevelSelect` / `#autoReplySelect` / `#manualMailOption` | `styles.css:1406-1428` | 四个 select 的宽度约束 |
| `.contact-head-actions .button` | `styles.css:1430-1436` | 28px 高按钮 |
| `.dropdown` | `styles.css:480-482` | `position: relative` |
| `.dropdown-menu` | `styles.css:484-498` | 浮层骨架 |
| `.dropdown-menu:not([hidden])` | `styles.css:3431-3433` | 浮层入场动画 |
| `[hidden]` | `styles.css:91-93` | `display: none !important` |
| `.button` / `.button.primary` / `.button.small` | `styles.css:655` / `:2329-2334` | 按钮基线（`.small` 为 26px 高） |
| `.expert-profile-header` / `.expert-avatar` / `.expert-header-info` | `styles.css:1447-1495` | 姓名行 |
| `.expert-tag` + 4 变体 / `.expert-tag-remove` / `.inbound-tag-editor-chips` | `styles.css:4486-4543` | 标签 chips |
| `.muted` | `styles.css:2845-2848` | 「暂无标签」文本 |
| `.tag-editor-loading` / `-overlay` / `-spinner` | `styles.css:3715-3750` | 标签加载态 |
| `.badge` / `.badge.warn` | `styles.css:900-924` | 「已变更」徽标（若保留） |

**设计基准 token 实值（`styles.css:1-80` 的 `:root`）**

- 主色 `--primary: #1e40af`；`--primary-rgb: 30, 64, 175`；`--primary-tint: rgba(var(--primary-rgb), 0.1)`；`--primary-light: rgba(var(--primary-rgb), 0.07)`
- 背景 `--bg-main: #f5f7fb`；`--bg-sidebar: #ffffff`；`--surface: rgba(15, 23, 42, 0.022)`
- 边框 `--border: rgba(15, 23, 42, 0.11)`；`--border-strong: #cbd5e1`；`--panel-border: rgba(15, 23, 42, 0.08)`；`--line: rgba(15, 23, 42, 0.055)`
- 文字 `--text-main: #1e293b`；`--text-secondary: #475569`；`--text-muted: #94a3b8`
- 语义色 `--success: #059669`；`--warning: #d97706`；`--warning-bg: rgba(217,119,6,.08)`；`--warning-border: rgba(217,119,6,.2)`；`--warning-strong: #b45309`
- 圆角 `--radius-sm: 7px`；`--radius-md: 10px`
- 过渡 `--transition: all 0.15s ease`
- **控件基线**：操作栏内 select 与 button 一律 `height: 28px; min-height: 28px; font-size: 11px`（`styles.css:1394-1404` / `:1430-1436`）；行内 gap `8px`（`:1366-1373`）

**DOM 结构约定**

- 浮层显隐用 `hidden` 属性 + `document.addEventListener("click", ...)` 外部点击关闭，范式见 `app.js:11405-11424`（发现专家下拉）。
- `#contactHeadActions` 内的按钮一律 `data-action` + `data-id`，由 `app.js:11071-11075` 的委托转 `handleContactAction`，**不新增监听器**。
- 详情区面板内元素用 `data-role` + `panel.querySelector` 作用域查询（`K-expert-detail-two-panel-render-sites`）；操作栏因只有一处渲染，沿用全局 id。

**改动前基线（逐字摘录）**

`app.js:6979-7005`（`#contactHeadActions` 现有两行模板）与 `app.js:7061-7076`（待删除的「绑定发件账号」metadata 卡片）与 `app.js:3964-3992`（`renderExpertTagEditor` 全文）—— 三处请在实施前 `git show HEAD:src/main/resources/static/app.js | sed -n '3964,3992p;6979,7005p;7061,7076p'` 存档，作为回归比对基线。

`styles.css:1556-1567`（待删除）：

```css
.metadata-card-value .sender-binding-editor {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
    margin-top: 6px;
}

.metadata-card-value .sender-binding-editor select {
    flex: 1 1 140px;
    min-width: 0;
}
```

该 class 使用点全集（grep `sender-binding-editor`）：`app.js:7070` 一处 + `styles.css:1556/1564` 两条规则。删除 DOM 后两条规则成为死规则 → **一并删除**。

## 实现方案

### T1 —— `state` 增加折叠态（I-4）

文件：`app.js`。在 `state` 字面量（`:5` 起）中 `mailSendOptions: [],`（`:25`）之后插入：

```javascript
    contactHeadExpanded: false,
```

### T2 —— 重写 `#contactHeadActions` 模板（I-1 / I-3 / I-4 / I-9，S-1..S-5、S-8）

文件：`app.js`，`loadContactDetail`（`:6967`）。

1. 把 `:6979-7005` 的 `innerHTML` 整体替换为 S-8 的骨架。
2. **把 `:7209-7220` 的 select 填充块整体上移**到 `$("#contactHeadActions").innerHTML = ...` 之后、`const banner = ...`（`:7007`）之前。理由：填充是 `await` 的，放在原位会让浮层在首次打开前一直是空 select；上移后与模板写入相邻，语义清晰。填充块内部逻辑逐字不变（含 `state.accounts` 兜底与 `SIMULATOR_NOOP` 过滤）。
3. 填充完成后立即调用一次 `updateSenderBindingDirtyState()`（初始化闸门为干净态）。
4. `EXPANDED` 取 `state.contactHeadExpanded === true`。

### T3 —— 删除 metadata 卡片（S-8 禁止项）

文件：`app.js`，删除 `:7061-7076` 的「Sender Binding Card」整块（含注释行 `<!-- Sender Binding Card -->`）。
文件：`styles.css`，删除 `:1556-1567` 两条死规则。

> 「已变更」badge（原 `app.js:7069`）与「清除标记」按钮（原 `:7073`）迁入浮层 footer（S-8）。

### T4 —— 未保存闸门（I-2 / I-3）

文件：`app.js`。新增函数（建议紧邻 `updateSaveButtonState`，`:8799` 之前）：

```javascript
function updateSenderBindingDirtyState() {
    const select = $("#senderBindingSelect");
    const pill = $("#senderBindingToggle");
    const note = $("#senderBindingDirtyNote");
    const sendBtn = $("#sendManualMailBtn");
    if (!select) return;
    const dirty = select.value !== (select.dataset.original || "");
    if (pill) pill.dataset.dirty = dirty ? "true" : "false";
    if (note) note.hidden = !dirty;
    if (sendBtn) sendBtn.disabled = dirty;
}
```

在 `#contactHeadActions` 的 change 委托（`:11142-11148`）内追加：

```javascript
        if (select.id === "senderBindingSelect") {
            updateSenderBindingDirtyState();
        }
```

**不得**修改该委托内既有的三 id 分支。

### T5 —— 账号浮层开合（S-2 / S-3）

文件：`app.js`。在 `bindEvents` 内、`#contactHeadActions` 的 click 委托（`:11070`）之后追加一段，**范式逐字对齐 `app.js:11405-11424` 的发现专家下拉**（因为 pill 与浮层是每次 `loadContactDetail` 重建的，监听器必须挂在稳定祖先 `#contactHeadActions` 与 `document` 上，不能挂在 pill 本身）：

```javascript
    $("#contactHeadActions").addEventListener("click", (event) => {
        const toggle = event.target.closest("#senderBindingToggle");
        const pop = $("#senderBindingPop");
        if (!pop) return;
        if (toggle) {
            event.stopPropagation();
            pop.hidden = !pop.hidden;
            toggle.setAttribute("aria-expanded", String(!pop.hidden));
            return;
        }
        if (event.target.closest("#senderBindingPop")) {
            event.stopPropagation();
        }
    });
    document.addEventListener("click", () => {
        const pop = $("#senderBindingPop");
        if (pop && !pop.hidden) {
            pop.hidden = true;
            $("#senderBindingToggle")?.setAttribute("aria-expanded", "false");
        }
    });
    document.addEventListener("keydown", (event) => {
        if (event.key !== "Escape") return;
        const pop = $("#senderBindingPop");
        if (pop && !pop.hidden) {
            pop.hidden = true;
            $("#senderBindingToggle")?.setAttribute("aria-expanded", "false");
        }
    });
```

⚠ 该 click 监听器与既有 `:11070` 的监听器是**两个独立 listener 挂在同一元素**上，互不干扰；不要把浮层逻辑塞进既有那个（它的 `button[data-action]` 早退分支会吞掉 pill 的点击 —— pill 没有 `data-action`，所以实际不会，但保持分离更安全）。

### T6 —— 折叠开关（I-4）

文件：`app.js`，`handleContactAction`（`:8533`）内、`select-expert` 分支之前插入：

```javascript
    if (action === "toggle-contact-head-more") {
        state.contactHeadExpanded = !state.contactHeadExpanded;
        const row = $("#contactHeadMoreRow");
        const toggle = $("#contactHeadMoreToggle");
        if (row) row.hidden = !state.contactHeadExpanded;
        if (toggle) toggle.setAttribute("aria-expanded", String(state.contactHeadExpanded));
        return;
    }
```

### T7 —— `renderExpertTagEditor` 增加 inline 形态（I-5 / I-6，S-6 / S-7）

文件：`app.js:3964-3992`。签名改为：

```javascript
function renderExpertTagEditor(tags, orcidId, level, editorId = "expertTagEditor", profileMissing = false, layout = "section") {
```

函数体最前面加一个 `if (layout === "inline") { ... }` 早退分支，输出 S-7 的两种骨架；**`if` 之后的既有代码一字不改**（保证 I-5）。

⚠ 参数默认值中不得出现 `)` —— `expertProfileAbsence.test.js:13` 的 `extractFn` 用正则 `function\s+NAME\s*\([^)]*\)` 抽取函数源码，参数表内出现右括号会导致抽取失败、整个测试文件报 `Could not find renderExpertTagEditor`。

### T8 —— 重渲染保持形态（I-7）

文件：`app.js:4077-4081`，`updateExpertTagEditor` 改为：

```javascript
function updateExpertTagEditor(orcidId, tags, level, editorId = "expertTagEditor") {
    const editor = document.getElementById(editorId);
    if (!editor || editor.dataset.orcid !== orcidId) return;
    const layout = editor.dataset.layout === "inline" ? "inline" : "section";
    editor.outerHTML = renderExpertTagEditor(tags, orcidId, level, editorId, false, layout);
}
```

### T9 —— 标签「+N」展开

文件：`app.js`，`handleContactAction` 内、`expert-add-tag-open` 分支（`:8595`）之前插入：

```javascript
    if (action === "expert-tags-expand") {
        const editor = element.closest(".expert-tag-editor");
        if (!editor) return;
        editor.dataset.expanded = "true";
        editor.querySelectorAll(".expert-tag[hidden]").forEach((chip) => { chip.hidden = false; });
        element.remove();
        return;
    }
```

### T10 —— 两处姓名行改造（S-9，I-10）

文件：`app.js`。
- `loadContactDetail`：`:7025-7033` 的 `.expert-profile-header` 内追加 inline 编辑器调用；删除 `:7035` 独立那行。
- `showExpertDetail`：`:6649-6659` 同上；删除 `:6661` 独立那行。
- 两处的 `renderDetailSubTabs(...)` 与四个 `detail-tab-panel` 一字不改（I-10）。

### T11 —— 测试

**改**：`src/test/js/expertProfileAbsence.test.js`
- 现有 11 个用例、`S1_EXPECTED` / `S2_EXPECTED` 常量**一字不改**。
- 追加一个 `describe("expertProfileAbsence: inline layout (P2 S-7)")`，含 4 个用例：
  1. `layout="inline"` + `profileMissing=false` → 输出含 `class="expert-tag-editor is-inline"`、`data-layout="inline"`、`data-action="expert-add-tag-open"`，且**不含** `detail-section`、不含 `<h3>`。
  2. `layout="inline"` + `profileMissing=true` → 含 `data-profile-missing="true"`、含 `expert-tag-nodoc`、**不含**任何 `data-action=`。
  3. 两种 inline 输出均含 `id="expertTagEditor"`、`data-orcid="0000-0001"`、`data-level="CANDIDATE"`（I-6）。
  4. `layout` 传 `undefined` / `"section"` / 任意其他值 → 输出等于 `S2_EXPECTED`（I-5 的负向断言）。
- 追加 CSS 存在性断言：`stylesCssSource` 含 `.expert-tag-editor.is-inline`、`.expert-tag-nodoc`、`.expert-tag-add-btn`。

**新建**：`src/test/js/contactHeadLayout.test.js`（沿用 `expertMailPreviewTab.test.js` 的 `extractFn` + `vm` + DOM stub 范式）
1. `loadContactDetail 源码含 S-8 的全部关键 id`：断言函数源码包含 `id="senderBindingToggle"`、`id="senderBindingPop"`、`id="senderBindingSelect"`、`id="senderBindingDirtyNote"`、`id="sendManualMailBtn"`、`id="contactHeadMoreRow"`、`id="contactHeadMoreToggle"`，且**不含** `contact-head-mail-row`、不含 `sender-binding-editor`、不含 `style="`。
2. `updateSenderBindingDirtyState 三处联动`（I-2）：DOM stub 造出四个元素，`select.value !== dataset.original` 时断言 `note.hidden === false`、`sendBtn.disabled === true`、`pill.dataset.dirty === "true"`；相等时断言全部复位。
3. `未绑定专家的 select 值不等于空 original 时判脏`（I-3）：`dataset.original = ""`、`value = "ACC_A"` → 判脏。
4. `send-manual-mail 请求体 senderAccountCode 恒为 null`（I-1）：抽取 `handleContactAction`，stub `api` 捕获 body，断言 `JSON.parse(body).senderAccountCode === null`；并对函数源码断言不含 `senderBindingSelect` 出现在 `send-manual-mail` 分支内。
5. `toggle-contact-head-more 翻转 state 且不重置`（I-4）：连续调用两次，断言 `state.contactHeadExpanded` 为 `true` → `false`；再断言 `loadContactDetail` 源码中读的是 `state.contactHeadExpanded`。
6. `updateExpertTagEditor 透传 layout`（I-7）：stub 一个 `dataset.layout === "inline"` 的编辑器，断言重渲染结果含 `is-inline`。
7. `data-panel 计数未变`（I-10）：`assert.equal((appJsSource.match(/<div class="detail-tab-panel" data-panel="mail-preview"/g)||[]).length, 2)`，且 `data-panel="mail-preview"` 与 `data-panel="template"` 总数相等。
8. `styles.css 含 S-1..S-6 声明的全部新 class`：逐个断言。
9. **DOM stub 陷阱防护**（`K-dom-stub-tests-hide-dangling-refs`）：断言 `index.html` 源文本仍含 `id="contactHeadActions"`；并断言 `app.js` 中 `#senderBindingSelect` 的**生成点**存在（`loadContactDetail` 源码含 `id="senderBindingSelect"`），而不只是 stub 能取到。

**不改**：`src/test/js/senderBindingDisplay.test.js` —— 经逐行核对（`:85-196`），其 6 个用例全部针对 `renderContactListItems`（列表副行「账号：XXX」、`tag-sender-changed` 标记、转义）与 `loadAccounts`（账号表 `boundExpertCount`），**没有任何一条断言详情区的 metadata 卡片**；`:95-106` 只是强制 stub 覆盖 `#senderBindingSelect` 这个 id 并验证 `loadContactDetail` / `loadContacts` / `handleContactAction` 可被抽取，这三点在改动后依然成立。

## 变更文件清单

| # | 文件 | 改动 | 子系统 |
|---|---|---|---|
| 1 | `src/main/resources/static/app.js` | T1-T10 | 前端 static |
| 2 | `src/main/resources/static/styles.css` | 新增 S-1..S-6 规则段；删除 `:1556-1567` | 前端 static |
| 3 | `src/test/js/expertProfileAbsence.test.js` | +1 describe / 4 用例 + CSS 断言 | 前端 static |
| 4 | `src/test/js/contactHeadLayout.test.js` | **新建**，9 个用例 | 前端 static |

文件数 4 ≤ 10 ✓　子系统数 1 ≤ 2 ✓　新增共享存储字段 0 ✓　后端改动 0 ✓

## 验证命令

> **前提**：本节 JS 命令**不需要 JDK**，可直接运行（2026-08-14 于本仓库实测，`node v22.22.3`）。
> 全量 Maven 回归**必须 JDK 11（zulu-11）**，裸 `mvn` 会构建失败（`CLAUDE.md:7`）。
> `verify.sh` **不可**用作本计划门禁 —— 它只跑 `normalizeDiscoveryResultSummary.test.js` 一个文件（`verify.sh:16`，共享不变量 M-4）。

```bash
# 本计划新建的测试类（单跑）
node --test src/test/js/contactHeadLayout.test.js

# 本计划改动的测试类（单跑）
node --test src/test/js/expertProfileAbsence.test.js

# 本计划相关的全部前端用例（权威门禁）
node --test \
  src/test/js/contactHeadLayout.test.js \
  src/test/js/expertProfileAbsence.test.js \
  src/test/js/senderBindingDisplay.test.js \
  src/test/js/expertMailPreviewTab.test.js

# app.js 语法检查（pom.xml:205-218 同款）
node --check src/main/resources/static/app.js

# 全量 JS 用例
node --test src/test/js/*.test.js

# 全量回归（含 JS，经 pom.xml:188-203 的 exec-maven-plugin 绑定在 test phase）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 空白/换行卫生
git diff --check
```

**通过判据**

- 前四条：退出码 0 且输出含 `# fail 0`。第三条的 `# tests` 应为「基线 24（`expertProfileAbsence` 11 + `senderBindingDisplay` 6 + `expertMailPreviewTab` 13，其中 `expertMailPreviewTab` 若已含 P1 的 +3 则为 16）+ 本计划新增 13」。**落地时以实跑输出为准，不得倒推**。
- `node --check`：退出码 0，无输出。
- `mvn test`：`Tests run: N, Failures: 0, Errors: 0`；输出中须出现 `node --test` 的执行记录（确认 `skipNodeTests` 未生效）。
- `git diff --check`：无输出。

**来源**：JS 命令来自 `pom.xml:188-231` + `K-js-test-invocation-surface`，并于 2026-08-14 在本仓库实测通过（四文件基线 `# tests 34 # pass 34 # fail 0`）；Maven 命令逐字引自 `CLAUDE.md:5-30`（JDK 路径未在研究环境实测，见主计划说明）。

## 验收标准

**不变量**

- **I-1**：`contactHeadLayout.test.js` 用例 4 通过；且 `git diff` 中 `send-manual-mail` 分支的 `senderAccountCode: null` 一行未被修改。
- **I-2**：`contactHeadLayout.test.js` 用例 2 通过（三处联动的正反两态）。
- **I-3**：`contactHeadLayout.test.js` 用例 3 通过；`grep -n "selectedIndex" src/main/resources/static/app.js` 在新增代码中无命中。
- **I-4**：`contactHeadLayout.test.js` 用例 5 通过；`grep -n "contactHeadExpanded" src/main/resources/static/app.js` 命中 ≥ 3 处（state 声明、渲染读取、toggle 分支）。
- **I-5**：`expertProfileAbsence.test.js:77 / :93 / :114` 三条 `strictEqual` 原样通过；新增用例 4（layout 传其他值 → 等于 `S2_EXPECTED`）通过。
- **I-6**：`expertProfileAbsence.test.js` 新增用例 3 通过。
- **I-7**：`contactHeadLayout.test.js` 用例 6 通过。
- **I-8**：`grep -n "is-inline.tag-editor-loading" src/main/resources/static/styles.css` 有且仅有 1 处命中，规则体为 `min-height: 0;`。
- **I-9**：`grep -n "未绑定" src/main/resources/static/app.js` 在 `loadContactDetail` 的模板内有命中；`grep -c "is-unbound" src/main/resources/static/styles.css` ≥ 1。
- **I-10**：`contactHeadLayout.test.js` 用例 7 通过；`expertMailPreviewTab.test.js:322-329` 原样通过。

**样式契约**

- **S-1 / S-4 / S-5**：`git diff src/main/resources/static/styles.css` 中新增的 `.contact-head-main-row`、`.contact-head-divider`、`.contact-head-dirty-note`、`.contact-head-more-toggle[aria-expanded="true"]` 四个规则块与本契约代码块**逐字一致**（属性名、值、顺序）。
- **S-2 / S-3**：新增的 `.sender-binding-*` 与 `.dropdown-menu.sender-binding-pop` 规则块与契约逐字一致；`git diff` 中 `.dropdown`（`:480-482`）与 `.dropdown-menu`（`:484-498`）两个既有规则块**无任何改动**。
- **S-6**：新增的 `.expert-tag-editor.is-inline` 系列与 `.expert-tag-nodoc` / `.expert-tag-add-btn` / `.expert-tag-more-btn` 与契约逐字一致；`git diff` 中 `.expert-tag`（`:4486`）、`.expert-tag.tag-*`（`:4498-4520`）、`.expert-tag .expert-tag-remove`（`:4529-4543`）、`.inbound-tag-editor-chips`（`:4522-4527`）、`.mail-expert-overview .expert-tag-editor`（`:2132/2183/2246`）**均无改动**。
- **S-7**：`renderExpertTagEditor` 的 inline 分支输出与契约骨架逐字一致（由 `expertProfileAbsence.test.js` 新增用例断言）。
- **S-8**：`grep -n 'contact-head-mail-row' src/main/resources/static/app.js` **无输出**；`grep -n 'sender-binding-editor' src/main/resources/static/app.js src/main/resources/static/styles.css` **无输出**；`loadContactDetail` 源码不含 `style="`。
- **S-9**：`grep -c 'class="expert-profile-header"' src/main/resources/static/app.js` 仍为 2；`git diff` 中 `.expert-profile-header` / `.expert-avatar` / `.expert-header-info`（`styles.css:1447-1495`）无改动。
- **全局**：`grep -n 'style="' src/main/resources/static/app.js` 的命中集合与改动前**完全相同**（本计划不新增 inline style；`updateSaveButtonState` 的既有 `sel.style.*` 属 must-NOT-change 5，不在此列）。

**回归**

- 执行「验证命令」节的全部命令，均达到该节判据。

## 人工验收清单

### A-1: 账号 pill 出现在操作栏、卡片消失（覆盖 需求 1）

- 前置条件：一位已绑定账号（例如 `LiLei`）的专家。
- 操作步骤：
  1. 进入「专家联系」，点开该专家。
  2. 观察面板顶部操作栏。
  3. 向下滚动详情区，查看原「绑定发件账号」卡片所在位置（在「阶段状态」与「推荐下一步」之间）。
- 预期结果：顶部出现一颗 pill，内容逐字为 `发件 LiLei ▾`，左侧一个绿色小圆点；详情区的 metadata 卡片网格中**不再有**「绑定发件账号」这张卡。
- 覆盖：需求描述 可观察结果 1

### A-2: 浮层可开可关，保存生效（覆盖 需求 1，I-2）

- 前置条件：至少两个启用的发件账号 A、B（`mail_sender_account.enabled = 1` 且非 `SIMULATOR_NOOP`）。
- 操作步骤：
  1. 点开一位绑定 A 的专家，点击账号 pill。
  2. 在浮层的下拉里选 B，点「保存绑定」。
  3. 点 pill 打开浮层，按 `Esc`。
  4. 再点 pill 打开浮层，点页面空白处。
- 预期结果：步骤 1 浮层展开，内含「绑定发件账号」小标题、一个下拉、一段琥珀色提示文字、一个「保存绑定」按钮；步骤 2 后出现「发件账号已变更」提示，pill 文本变为 `发件 B`，且浮层里出现「清除标记」按钮；步骤 3、4 浮层均关闭。
- 覆盖：需求描述 可观察结果 1

### A-3: 未保存闸门三处联动（覆盖 需求 3，I-2/I-3）

- 前置条件：同 A-2（两个启用账号）。
- 操作步骤：
  1. 点开一位绑定 A 的专家，点 pill 打开浮层。
  2. 下拉改选 B，**不点保存**，点页面空白处关闭浮层。
  3. 观察 pill、pill 右侧、「发送邮件」按钮。
  4. 重新打开浮层，把下拉改回 A，关闭浮层。
  5. 再次打开浮层，改选 B 并点「保存绑定」。
- 预期结果：
  - 步骤 3：pill 底色变琥珀、圆点变琥珀；pill 右侧出现文本 `⚠ 账号未保存`；「发送邮件」按钮置灰且点击无反应。
  - 步骤 4：三者全部复位（pill 回白底绿点、提示消失、「发送邮件」恢复可点）。
  - 步骤 5：保存成功提示出现，三者亦为复位态。
- 覆盖：需求描述 可观察结果 3；I-2、I-3

### A-4: 未绑定态可见（覆盖 需求 4，I-9）

- 前置条件：一位 `bound_sender_account_code` 为 NULL 的专家（左侧列表副行显示「账号：未绑定」）。
- 操作步骤：点开该专家，观察操作栏 pill。
- 预期结果：pill 文本逐字为 `发件 未绑定`，左侧圆点为**灰色**（不是绿色）。
- 覆盖：需求描述 可观察结果 4；I-9

### A-5: 折叠区默认收起、可展开、切专家不重置（覆盖 需求 2，I-4）

- 前置条件：至少两位专家。
- 操作步骤：
  1. 刷新页面，点开专家甲，观察操作栏。
  2. 点「⚙ 更多」。
  3. 在左侧列表点开专家乙，观察操作栏。
  4. 再点「⚙ 更多」，然后点开专家甲。
- 预期结果：
  - 步骤 1：操作栏只有一行（账号 pill｜模板下拉｜发送邮件｜⚙ 更多），看不到状态/层级/回复模式。
  - 步骤 2：下方出现第二行，含「状态」标签 + 三个下拉 + 置灰的「保存变更」；「⚙ 更多」按钮变为蓝底高亮。
  - 步骤 3：第二行**仍然展开**（未因切专家而收起）。
  - 步骤 4：第二行收起，且切到专家甲后仍是收起态。
- 覆盖：需求描述 可观察结果 2；I-4

### A-6: 标签并入姓名行（覆盖 需求 5）

- 前置条件：一位 ES 中有画像且有 1-3 个标签的专家。
- 操作步骤：
  1. 点开该专家。
  2. 观察姓名/邮箱行。
  3. 观察姓名行与「学术档案 / 联系详情 / 模板预览 / 邮件预览」标签行之间。
  4. 依次点击「学术档案」「联系详情」「模板预览」「邮件预览」四个子标签。
- 预期结果：标签 chips 与一个「＋」小方块按钮出现在**姓名行最右侧**，与姓名同一水平线；姓名行与子标签行之间**不再有**「专家标签」这个带标题的独立区块；步骤 4 中四个子标签按此顺序排列、数量为 4，每个点击后对应面板都有内容，**不出现空白面板**。
- 覆盖：需求描述 可观察结果 5；must-NOT-change 3

### A-7: 标签超过 3 个时折叠与展开（覆盖 需求 5）

- 前置条件：给某位专家打满 5 个标签（用「＋」逐个添加：承诺回复材料、重点关注、待补充信息，再加两个自定义）。
- 操作步骤：
  1. 从列表重新点开该专家。
  2. 数一数姓名行右侧可见的 chips 数量。
  3. 点「+2」按钮。
- 预期结果：步骤 2 只显示 3 个 chips 加一个 `+2` 按钮；步骤 3 后 5 个 chips 全部显示，`+2` 按钮消失，姓名行允许换行容纳。
- 覆盖：需求描述 可观察结果 5

### A-8: 无画像专家退化为内联 pill（覆盖 需求 6）

- 前置条件：一位 ES 中无画像文档的专家（现象：改动前该位置显示「该专家在 ES 中无画像文档，标签功能不可用」）。
- 操作步骤：点开该专家，观察姓名行右侧；把鼠标悬停在该处停留 2 秒。
- 预期结果：姓名行右侧只有一颗**灰色虚线边框**的小 pill，文本逐字为 `ES 无画像`；悬停后浏览器原生 tooltip 显示 `该专家在 ES 中无画像文档，标签功能不可用`；页面上**没有**独立的「专家标签」区块，也没有「＋」按钮。
- 覆盖：需求描述 可观察结果 6

### A-9: 改绑后邮件预览签名随之改变（跨路径，覆盖 IP-1；依赖 P1）

- 前置条件：两个启用账号 A、B，且二者 `sender_name` 不同；一位绑定 A 的专家；启用的组装模板正文含 `${senderName}`。
- 操作步骤：
  1. 点开该专家，切到「邮件预览」标签页，记下签名中的姓名（应为 A 的）。
  2. 点账号 pill，改选 B，点「保存绑定」。
  3. 切到「邮件预览」标签页。
- 预期结果：步骤 2 后页面回到「联系详情」标签页（`rebind` 后走 `loadContactDetail` 重渲染，属预期）；步骤 3 的签名姓名变为 **B** 的。
- 覆盖：现状审计 IP-1；顺序依赖的验证点
- 备注：若 P1 未落地，签名两次都是空白 —— 该项应判为**阻塞**，先补 P1。

### A-10: 加删标签后姓名行不炸开（跨路径回归，覆盖 IP-4 / I-7 / I-8）

- 前置条件：一位 ES 有画像、当前 1 个标签的专家。
- 操作步骤：
  1. 点开该专家，记住姓名行的高度（可与相邻专家对比）。
  2. 点「＋」添加一个标签，观察添加过程中与完成后的姓名行。
  3. 点某个标签上的「×」删除它，同样观察。
  4. 浏览器整页刷新（F5），重新点开该专家。
- 预期结果：整个过程中姓名行**始终保持单行高度**（约 40px，与头像等高），不出现瞬间撑高到 72px 的跳动；添加/删除完成后标签仍在**姓名行右侧**，**没有**变回带「专家标签」标题的独立区块；步骤 4 刷新后标签集合与步骤 3 结束时**完全一致**（证明写的是 ES 而非前端缓存）。
- 覆盖：现状审计 IP-4；I-7、I-8；must-NOT-change 6

### A-11: 收发件箱的标签区未受影响（回归，覆盖 must-NOT-change 2 / IP-5）

- 前置条件：「收发件箱」中至少有一封来信，其专家在 ES 中有画像。
- 操作步骤：进入「收发件箱」，展开一条来信的专家概览区，观察标签区。
- 预期结果：仍然是带「专家标签」标题（13px 加粗）的**块级区块**，右侧是「+ 添加标签」**文字按钮**（不是「＋」方块），与改动前逐字一致。
- 覆盖：must-NOT-change 2；现状审计 IP-5

### A-12: 发送仍用已保存绑定（回归，覆盖 must-NOT-change 1 / I-1）

- 前置条件：一位绑定账号 A 的专家；操作栏「模板下拉」中有可用模板。
- 操作步骤：
  1. 点开该专家，**不动**账号 pill。
  2. 选一个模板，点「发送邮件」。
  3. 到「收发件箱」找到这封外发记录，查看其发件账号。
- 预期结果：发送成功（不出现「发件账号与专家绑定不一致」之类的错误）；外发记录的发件账号为 **A**。
- 覆盖：must-NOT-change 1；I-1

### A-13: 状态/层级/回复模式的保存行为未变（回归，覆盖 must-NOT-change 5）

- 前置条件：任一专家。
- 操作步骤：
  1. 点「⚙ 更多」展开折叠区。
  2. 把「状态」下拉改成另一个值。
  3. 观察该下拉与「保存变更」按钮。
  4. 点「保存变更」，在确认弹窗中确认。
- 预期结果：步骤 3 该下拉出现琥珀色描边、「保存变更」由灰变蓝可点；步骤 4 弹出确认框，文案含 `状态: 旧值 → 新值`；确认后提示「变更已保存」，详情页刷新。
- 覆盖：must-NOT-change 5

### A-14: 专家列表的标签与「已变更」标记未受影响（回归，覆盖 must-NOT-change 4）

- 前置条件：列表中至少有一位带标签的专家、一位 `sender_account_changed = 1` 的专家。
- 操作步骤：进入「专家联系」，观察左侧列表项的副行。
- 预期结果：标签 chips 与「发送账号已变更」标记的颜色、圆角、字号与改动前一致（琥珀色标记、11px、圆角 7px）；「账号：XXX」文本仍在。
- 覆盖：must-NOT-change 4

### A-15: 窄面板下操作栏可用（UI 目测，覆盖 样式契约整体）

- 前置条件：任一已绑定专家。
- 操作步骤：
  1. 拖动「专家列表」与详情区之间的分栏条，把详情区收窄到约屏幕的一半。
  2. 观察操作栏。
  3. 打开账号浮层。
- 预期结果：操作栏控件换行排列但**不重叠、不被裁剪**；所有控件高度仍为 28px；账号浮层宽度 258px、**左对齐**于 pill（不是右对齐），完整可见不被面板边缘裁掉。
- 覆盖：样式契约 S-1 / S-2 / S-3
