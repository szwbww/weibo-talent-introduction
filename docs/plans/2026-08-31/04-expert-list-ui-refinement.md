# 专家列表联系摘要、筛选布局与邮件操作入口调整（前端）

> 依赖：先完成 `03-expert-mail-summary-backend.md`，前端读取其 `receivedCount/sentCount/failedCount` 及 `expertContactId` 精确过滤能力。

## 需求描述

对专家列表做五组可见调整：联系详情不再展示邮件正文时间线，改为收/发/失败数量并可跳到该专家的“按专家聚合、全部邮件、全部时间”收发件箱状态；漏斗层级只显示“原始/筛选/有效”；显示行数移动到专家列表分页条；研发类型与数据完整度改为真实站点风格的整行 chip 布局且保留所有其他筛选项；全局自动回复按钮移到收发件箱标题栏，与“检查回复/批量发送”放在一起。

必须不变：

1. 漏斗 option 的 value 仍为 `RAW/CANDIDATE/APPLICATION`，默认仍为 `CANDIDATE`；筛选请求语义不变。
2. 显示行数 id、选项和默认值仍为 `expertIndexSize`、10/20/50/100、默认 50；改变页大小仍重置到第 1 页并重新加载，两条查询路径的分页语义不变。
3. `expertTypeTagSelect`、`hasFieldTagSelect` 的 id、所有 `data-value`、`.tag-chip.active` 及点击后刷新逻辑不变；H-Index、引用数、近 N 年发表、按模板门禁和其他筛选控件不得遗漏。
4. `bulkAutoReplyBtn` 的 id/class/文案状态、确认框、summary/bulk API、点击绑定和更新后 `loadContacts()` 行为不变；只改变宿主与进入收发件箱时的首次刷新位置。
5. `checkRepliesBtn` 必须继续紧邻并位于 `bulkOutreachBtn` 左侧，以满足现有 `batchEntryRelocation.test.js` 契约；新增自动回复按钮放在批量发送右侧。
6. 联系详情中的材料状态、会议安排、阶段状态、推荐下一步、文档/别名、标签、头部发件与状态操作均保留；只替换 contact panel 内的 `.mail-timeline`。收发件箱“查看邮件”正文仍保留。
7. 普通进入收发件箱、普通筛选、按邮件模式、待处理模式和任务执行 drilldown 行为不变；专家精确焦点只在“查看收发邮件”入口设置，用户主动查询/切换筛选或普通导航进入时清除。

范围外：删除后端专家详情 DTO 的 `mails`、重做专家卡片/整个筛选栏、改变自动回复开关语义、改变邮箱统计口径、增加新页面或弹窗、调整收发件箱邮件卡片样式。

## 关键不变量

### Invariant I-1: 漏斗展示文案与既有状态映射唯一一致
- Rule: 筛选框三个 option 必须逐字显示“原始/筛选/有效”，其 value 仍为 RAW/CANDIDATE/APPLICATION；不得出现英文 enum 或“数据层/待筛选层/已回复意向层”。
- Applies to: `index.html#expertIndexLevel`；现有 `app.js:indexLevelLabels` 和 `indexLevelOptions` 不改。
- Violation consequence: 筛选框与详情状态修改下拉文案不一致，或请求层级值错误。
- 来源: original；源码证据 `app.js:579-583,652-656` 已是目标中文映射。

### Invariant I-2: 页大小属于分页而非筛选
- Rule: `#expertIndexSize` 必须只出现在 `#contactPager` 内；pager 即使只有 0/1 页也保持可见，页码显示 `第 1 / 1 页` 且上下页禁用。页大小不计入筛选 badge，但 change 仍执行 `state.contactsPage=0` 后加载。
- Applies to: `index.html` pager DOM；`renderContactListSkeleton`、`renderContactPager`、加载失败分支、`updateFilterBadge`、change 注册。
- Violation consequence: 用户在单页数据时无法改变行数，或筛选角标错误把分页设置算作筛选条件。
- 来源: K-list-pager-skeleton-reuse、K-expert-filter-registration-sites；源码证据 `app.js:4331-4348` 当前在 `totalHits<=size` 时隐藏，`:12060-12088` 当前把 size 算入 badge 并注册 change。

### Invariant I-3: 联系详情只替换邮件时间线
- Rule: `loadContactDetail` 的 contact panel 不得调用 `detail.mails...map(renderMailItem)`，改为后端计划提供的 `receivedCount/sentCount/failedCount` 摘要；其余联系详情块原样保留。`renderMailItem` 及其他邮件正文展示点不得删除或改写。
- Applies to: `loadContactDetail`、详情标题文案、现有 contact panel DOM。
- Violation consequence: 联系详情仍泄露长正文，或误删收发件箱/未匹配邮件等其他正文能力。
- 来源: K-mail-body-display-sites、K-ui-removal-retires-obsolete-contract-tests；源码证据 `app.js:7730-7735` 是本次唯一删除的时间线宿主。

### Invariant I-4: 联系摘要和跳转使用同一邮箱口径
- Rule: 联系详情摘要必须请求 `/api/mail/mailbox/by-expert?expertContactId=<id>&page=0&size=1` 且不带日期；跳转必须设置 EXPERT + ALL、清空账号/方向/标签/关键字/日期/任务执行过滤、把收件人输入框显示为当前专家邮箱、设置同一 `focusExpertContactId`，然后进入 mailbox。`loadMailbox` 在焦点存在时追加 `expertContactId`；匹配 `<details>` 自动展开。
- Applies to: `state.mailbox`、`loadContactDetail`、新增跳转 helper、`loadMailbox`、`renderMailboxExpertGroups`、普通导航/筛选清焦点。
- Violation consequence: 摘要是全量但跳转只有近 7 天，或跳到邮箱子串匹配的其他专家，或目标组不展开。
- 来源: K-group-before-pagination；源码证据 `app.js:13117-13124` 默认自动填近 7 天，`:13174-13179` 调 by-expert，`:13268` 当前 details 默认收起。

### Invariant I-5: chip 筛选只改布局不改行为
- Rule: 两组 chip 的 id、按钮 class、data-value 和点击注册逐字保留；只增加共同外层 `.toolbar-chip-row`。所有截图中曾被遗漏的 H-Index、引用数、近 N 年发表、按模板门禁继续存在且可操作。
- Applies to: `index.html:535-583`、新增 CSS；`expertTypeActiveValues` 和 `bindEvents` 两个 chip 初始化器不改。
- Violation consequence: 视觉调整导致筛选字段丢失、查询参数缺失或 chip 点击不刷新。
- 来源: K-expert-filter-registration-sites。

### Invariant I-6: 自动回复入口只迁宿主
- Rule: `bulkAutoReplyBtn` 在 DOM 中仍恰好一次；从 contacts toolbar 删除，插到 mailbox `.panel-head-actions` 的 `bulkOutreachBtn` 后。`refreshAutoReplySummary/initBulkAutoReply` 不改 API和切换语义；首次进入 mailbox 必须刷新 summary，不能长期显示“加载中...”。
- Applies to: `index.html`、`refreshCurrentView`/`loadContacts` 的刷新调用点、现有 init 函数、回归测试。
- Violation consequence: 按钮重复绑定、收发件箱显示永久加载态，或全局开关行为改变。
- 来源: K-dom-stub-tests-hide-dangling-refs、K-ui-removal-retires-obsolete-contract-tests；源码证据 `index.html:605` 当前在 contacts，`:747-753` 是目标容器，`app.js:5253` 当前只在联系人加载后刷新。

### Invariant I-7: 静态资源缓存键三项同步
- Rule: `styles.css`、`trust-reply-workbench.js`、`app.js` 三个 URL 的 `?v=` 必须统一改为 `20260831-expert-list-mailbox-ui`；4 个固定值测试同步同一字符串。
- Applies to: `index.html` 与变更清单中的 4 个固定缓存键 JS 测试。
- Violation consequence: Maven test phase失败或浏览器继续使用旧 JS/CSS。
- 来源: K-frontend-cache-key-triad。

## 样式契约

### S-1: 漏斗与专家分页控件
- 复用：`.toolbar-label`（`styles.css:467-477`）、`.list-pager`（`:1112-1120`）、`.list-pager-info`（`:1122-1125`）、`.button.small`（`:2323-2328`）。这些规则块不修改，不自造近似 class。
- 新增：无。
- DOM 结构：必须逐字采用以下层级（option 可按此完整列出）：
```html
<label class="toolbar-label">
    漏斗层级:
    <select id="expertIndexLevel">
        <option value="RAW">原始</option>
        <option value="CANDIDATE" selected>筛选</option>
        <option value="APPLICATION">有效</option>
    </select>
</label>

<div id="contactPager" class="list-pager">
    <label class="toolbar-label">
        显示行数:
        <select id="expertIndexSize">
            <option value="10">10 条/页</option>
            <option value="20">20 条/页</option>
            <option value="50" selected>50 条/页</option>
            <option value="100">100 条/页</option>
        </select>
    </label>
    <button class="button small" id="contactPrevPage">上一页</button>
    <span id="contactPageInfo" class="list-pager-info"></span>
    <button class="button small" id="contactNextPage">下一页</button>
</div>
```
- 禁止项：`#expertIndexSize` 残留在 `#contactsFilterGroup`；pager 上 `hidden` 静态属性；修改上述共用 CSS；新增 inline style。

### S-2: 研发类型与数据完整度整行 chip
- 复用：`.toolbar-label`（`styles.css:467-477`）、`.tag-select`（`:576-581`）、`.tag-chip`（`:583-600`）、`.tag-chip:hover`（`:602-606`）、`.tag-chip.active`（`:608-613`）。既有规则不修改。
- 新增：以下规则必须逐字追加到现有 tag-chip 区域，不得改值：
```css
.toolbar-chip-row {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    flex: 1 0 100%;
    min-width: 0;
}

.toolbar-chip-row > .toolbar-label {
    flex: 0 0 72px;
    padding-top: 7px;
}

.toolbar-chip-row > .tag-select {
    flex: 1 1 auto;
    min-width: 0;
}
```
- DOM 结构：两个组只套同一外层；内部现有按钮逐字保留：
```html
<div class="toolbar-chip-row">
    <span class="toolbar-label">研发类型:</span>
    <span class="tag-select" id="expertTypeTagSelect">
        <button type="button" class="tag-chip" data-value="PRODUCTION_RND">生产研发</button>
        <button type="button" class="tag-chip" data-value="ACADEMIC_RND">学术科研</button>
        <button type="button" class="tag-chip" data-value="HYBRID_RND">混合研发</button>
        <button type="button" class="tag-chip" data-value="SERVICE_ONLY">纯服务</button>
        <button type="button" class="tag-chip" data-value="OUT_OF_SCOPE">医学越界</button>
        <button type="button" class="tag-chip" data-value="UNKNOWN">未知</button>
        <button type="button" class="tag-chip" data-value="UNCLASSIFIED">未分类</button>
    </span>
</div>
<div class="toolbar-chip-row">
    <span class="toolbar-label">数据完整度:</span>
    <span class="tag-select" id="hasFieldTagSelect">
        <button type="button" class="tag-chip" data-value="employment">有职位</button>
        <button type="button" class="tag-chip" data-value="degree">有学历</button>
        <button type="button" class="tag-chip" data-value="institution">有机构</button>
        <button type="button" class="tag-chip" data-value="researchFields">有研究方向</button>
        <button type="button" class="tag-chip" data-value="recentWorkTitles">有近期论文</button>
        <button type="button" class="tag-chip" data-value="patentTitles">有专利</button>
    </span>
</div>
```
- 禁止项：卡片背景、额外边框、标题条、inline style、第二套 chip class；不得移动/删除 H-Index、引用数、近 N 年发表、按模板门禁控件。

### S-3: 联系邮件摘要
- 复用：`.metadata-grid`（`styles.css:1505-1510`）、`.metadata-card`/`:hover`（`:1512-1524`）、`.metadata-card-header`（`:1526-1536`）、`.metadata-card-value`（`:1543-1549`）、`.metadata-card.span-all`（`:1563-1565`）、`.button.small`（`:2323-2328`）。规则不修改。
- 新增：无。
- DOM 结构：
```html
<div class="metadata-grid">
    <div class="metadata-card span-all">
        <div class="metadata-card-header"><span>邮件往来</span></div>
        <div class="metadata-card-value">
            <span>收到 N 封</span>
            <span>｜成功发出 N 封</span>
            <span>｜发送失败 N 封</span>
            <button type="button" class="button small" data-action="open-contact-mailbox" data-id="CONTACT_ID" data-email="EXPERT_EMAIL">查看收发邮件</button>
        </div>
    </div>
</div>
```
- 加载失败结构：统计请求失败时，三个数字位置改为逐字文本 `<span>邮件统计加载失败</span>`，仍保留同一个“查看收发邮件”按钮；不得用 `0/0/0` 冒充成功响应。
- 禁止项：`.mail-timeline`、`renderMailItem`、主题/正文预览、“查看完整正文”；不得增加新摘要 class 或 inline style。

### S-4: 收发件箱标题栏操作组
- 复用：`.panel-head`（`styles.css:815-822`）、`.panel-head-actions`（`:832-837`）、`.button` 全状态（`:655-710`）。既有规则不修改。
- 新增：无。
- DOM 结构：按钮顺序必须如下；三个 id 各恰好一次：
```html
<div class="panel-head-actions">
    <button class="button" id="checkRepliesBtn" onclick="handleCheckReplies()">检查回复</button>
    <button class="button primary" id="bulkOutreachBtn" onclick="handleBulkOutreach()">批量发送</button>
    <button class="button" id="bulkAutoReplyBtn">自动回复：加载中...</button>
</div>
```
- 禁止项：改按钮 class/id/onclick/初始文案；把自动回复留在 contacts toolbar；修改 `.panel-head-actions`。

## 现状审计

### 收发统计 API 读取契约
- Schema/mapping、写路径和完整读取路径已在前置计划 `03-expert-mail-summary-backend.md` 审计；本计划不新增数据库读写，只消费其只读 DTO。
- `loadContactDetail` 当前并发请求专家详情、发送选项、文档、操作日志、材料状态（`app.js:7617-7627`），随后在 contact panel 对 `detail.mails` 逆序并 `renderMailItem`（`:7730-7733`）。
- 前端需增加第 6 个并发只读请求 `/api/mail/mailbox/by-expert?expertContactId=<id>&page=0&size=1`；后端所有事实写路径均由前置计划覆盖。
- Interaction points:
  1. 后端聚合 DTO → 联系摘要三个数字。（I-3/I-4）
  2. 摘要按钮 → mailbox state/filters → 同一 by-expert endpoint → 自动展开目标 group。（I-4）

### 专家筛选与分页
- `index.html:455-584` 是筛选组；当前漏斗 option 在 `:465-469` 混用英文 enum 和长中文；页大小在 `:471-479`。
- `index.html:535-546`、`:564-574` 是研发类型/数据完整度 chip；H-Index、引用数、近 N 年、模板门禁在 `:547-583`，真实 DOM 均存在，不能按此前预览遗漏。
- `index.html:670-674` 是专家 pager，当前带 `hidden` 且无页大小控件。
- `app.js:5018-5249` 两条联系人读取路径都读取同一 `#expertIndexSize`；只移动 DOM 不改参数。
- `renderContactPager`（`:4331-4348`）在单页隐藏 pager；loading/error 分支（`:4320-4329`, `:5224-5233`）也隐藏。
- `updateFilterBadge`（`:12161-12178`）当前把非 50 页大小算作筛选；change 注册（`:12188-12193`）负责重置页码/刷新。
- Interaction points: 页大小 DOM → `loadContacts` 的 DB/ES 双读取路径；页大小 change → badge/reload/pager。来源 K-expert-filter-registration-sites。

### 联系详情两套渲染
- `renderDetailSubTabs`（`app.js:7065-7079`）被 `showExpertDetail` 与 `loadContactDetail` 共用；contact panel 只在已落库 contact 的 `loadContactDetail` 默认激活。
- `showExpertDetail` 面向无 contactId 的 ES 专家，不能调用收发统计；本计划不改其 panel 集合。
- `handleContactAction`（`:9196-...`）是 contact detail 的统一 action 分发，`#contactDetail` click 委托在 `:11790-11806`；新按钮沿用 `data-action`，不新增监听器。（来源 K-expert-detail-two-panel-render-sites）
- `renderMailItem` 仍被其他邮件正文位置使用；只删除本宿主，不能删函数或 `.mail-item/.mail-timeline` CSS。（来源 K-mail-body-display-sites）

### 收发件箱跳转状态
- state 在 `app.js:57-70` 当前没有目标专家字段。
- `setView`（`:1659-1680`）会切换视图并调用 `refreshCurrentView`；`refreshCurrentView` 的 mailbox 分支当前只调 `loadMailbox`（`:1682-1692`）。
- `loadMailbox`（`:13205-...`）在全量模式首次自动填近 7 天（`:13221-13229`），构造 by-expert 参数但无 contact id。
- `renderMailboxExpertGroups`（`:13350-13382`）渲染 `<details>`，无 contact id data 属性且默认不展开。
- 普通 mailbox 筛选/模式监听集中在 `bindEvents`（`:12665-12758`）；普通 nav 共用 `:11423`。隐藏精确焦点若不在这些入口清除，会污染后续普通收发件箱浏览。
- Interaction points: contact jump state ↔ 默认日期；focus id ↔ by-expert query；focus id ↔ details open；normal nav/filter ↔ focus cleanup。（I-4）

### 自动回复按钮
- 当前 DOM：`index.html:605` 位于 contacts toolbar；目标容器 `:747-753` 已有检查回复和批量发送。
- 行为：`refreshAutoReplySummary`/`initBulkAutoReply`（当前 `app.js:12717-12767`）按 id 绑定，调用 `/api/expert-contacts/auto-reply/summary` 和 `/bulk`；无需改函数内容。
- 刷新入口当前仅 `loadContacts`（`:5253`）；若只搬 DOM，直接进入 mailbox 会一直显示“加载中...”。应把视图首次刷新责任移到 `refreshCurrentView` mailbox 分支。
- `checkRepliesRelocation.test.js:81-90` 明确断言旧位置，必须同步改写；`batchEntryRelocation.test.js:31-39` 要求 check→bulk 相邻，因此自动回复放在 bulk 后可保留该文件不变。（来源 K-ui-removal-retires-obsolete-contract-tests）

### 前端样式盘点
- 可复用 class：
  - toolbar：`.contacts-toolbar` `styles.css:449-453`；`.toolbar-group` `:455-460`；`.toolbar-label` `:467-477`；filters open `:535-558`。
  - chip：`.tag-select` `:576-581`；`.tag-chip`/hover/active `:583-613`。
  - panel/action：`.panel-head` `:815-822`；`.panel-head-actions` `:832-837`；`.button`/hover/active/primary `:655-710`。
  - list/pager：`.contacts-list-panel` `:1089-1097`；`.list-pager` `:1112-1120`；`.list-pager-info` `:1122-1125`；`.button.small` `:2323-2328`。
  - detail：`.metadata-grid`/card/header/value/span-all `:1505-1565`。
- 设计基准 token（`styles.css:1-83`）：主色 `#1e40af`、hover `#1e3a8a`、bright `#3b82f6`；主文字 `#1e293b`、弱文字 `#94a3b8`；border `rgba(15,23,42,.11)`、line `rgba(15,23,42,.055)`；圆角 7/10/18px；基础 transition 0.15s；toolbar label 11px，button 12px，small button 11px，chip 高 26px。
- DOM 结构约定：静态宿主在 `index.html`；动态 contact/mailbox 内容在 app.js template string；交互走既有容器委托或稳定 id；不得用仅 VM DOM stub 证明真实宿主存在（来源 K-dom-stub-tests-hide-dangling-refs）。
- 改动前基线（逐字关键片段）：
```html
<span class="toolbar-label">
    研发类型:
    <span class="tag-select" id="expertTypeTagSelect">...</span>
</span>
<span class="toolbar-label">
    数据完整度:
    <span class="tag-select" id="hasFieldTagSelect">...</span>
</span>
<div id="contactPager" class="list-pager" hidden>...</div>
<button class="button" id="bulkAutoReplyBtn">自动回复：加载中...</button>
```
```css
.contacts-toolbar .toolbar-filters.open {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px 8px;
}
```
- 新增 `.toolbar-chip-row` 为派生容器，不就地修改任何既有 class；grep 当前零使用点。

### 静态资源缓存键
- `index.html:11,2098-2099` 三键当前均为 `20260831-expert-material-tags`。
- 固定值断言全集：`checkRepliesRelocation.test.js:11`、`overlayAndDialogContrast.test.js:15`、`manualReplySubjectPrefill.test.js:13`、`batchSendTaskConsoleVisualFix.test.js:49-51`。（来源 K-frontend-cache-key-triad）

## 实现方案

### 阶段 1：静态 DOM 与真实站点样式

1. 修改 `src/main/resources/static/index.html`（I-1/I-2/I-5/I-6/I-7；S-1/S-2/S-4）：
   - 漏斗 option 改为三个纯中文目标文案。
   - 从 filter group 删除整块页大小 label，按 S-1 原样插入 `contactPager` 首位并去掉 pager 的 `hidden`。
   - 按 S-2 重包研发类型/数据完整度；按钮和其他筛选器逐字保留。
   - 删除 contacts toolbar 的 auto reply 按钮，按 S-4 插到 mailbox 标题栏 bulk 后。
   - 详情标题 `引进状态演进与往来邮件时间线` 改为 `专家引进状态与联系详情`，避免已移除时间线后文案失真。
   - 三个资源键统一 bump 为 I-7 值。
2. 修改 `src/main/resources/static/styles.css`（I-5；S-2）：只追加 S-2 的 3 个逐字规则块；不改任何现有 selector。

### 阶段 2：分页、摘要与精确跳转行为

1. 修改 `src/main/resources/static/app.js` 的分页逻辑（I-2；S-1）：
   - `renderContactListSkeleton` 和加载失败分支不再隐藏 pager。
   - `renderContactPager` 总是显示，至少 1 页，始终写 page info，并按边界禁用两按钮。
   - `updateFilterBadge` 删除 page size 条件；change 注册保留 `expertIndexSize`。
2. 修改 `loadContactDetail`（I-3/I-4；S-3）：
   - Promise.all 加入全时间、精确 contact id、size=1 的 by-expert 请求；请求成功但 groups 为空才显示 0/0/0，请求异常按 S-3 显示“邮件统计加载失败”并提示错误，不阻断其他详情。
   - 只把 `:7731-7733` 时间线换成 S-3 摘要；材料状态、meeting/metadata/后续模板 panels 不动。
3. 在 `handleContactAction` 前新增聚焦 helper，并增加 `open-contact-mailbox` 分支（I-4；S-3）：
   - helper 写入 `state.mailbox.focusExpertContactId/focusExpertEmail`，选择 EXPERT+ALL，清空明列过滤和任务执行过滤，日期清空且 `dateDefaultsApplied=true`，page=0，再 `setView('mailbox')`。
   - `loadMailbox` 在 EXPERT 模式且 focus id 非空时添加 `expertContactId`。
   - `renderMailboxExpertGroups` 给 details 添加 `data-expert-contact-id`，目标 id 加 `open`。
   - 普通点击 mailbox nav，以及查询、账号/方向/标签、scope/view 的用户操作先清 focus；刷新按钮和分页保留当前 focus。
4. 迁移自动回复刷新责任（I-6；S-4）：
   - 删除 `loadContacts` 内 `refreshAutoReplySummary` 调用。
   - `refreshCurrentView` 的 mailbox 分支同时等待 `loadMailbox()` 和 `refreshAutoReplySummary()`；`refreshAutoReplySummary/initBulkAutoReply` 函数内容不改。

### 阶段 3：前端契约与运行测试

1. 修改 `src/test/js/checkRepliesRelocation.test.js`（I-1/I-2/I-3/I-5/I-6/I-7；S-1-S-4）：
   - 反转旧 S-2：断言 contacts 无 `bulkAutoReplyBtn`，mailbox actions 顺序为 check→bulk→auto，三个 id 各一次。
   - 增加漏斗文案/value、page size 唯一宿主与 pager 常显、两个 chip row 和所有未遗漏筛选 id、详情不再含 `detail.mails...renderMailItem` 的源码契约。
   - 增加真实 index.html 宿主存在性断言，不能只靠 DOM stub。
   - 更新 I-7 key。
2. 修改 `src/test/js/mailboxExpertGrouping.test.js`（I-4；S-3）：
   - sandbox 支持 focus fields/radio checked 状态。
   - 运行验证 jump helper 清理过滤、设置 EXPERT/ALL/全时间、exact id；`loadMailbox` 请求含 `expertContactId`；目标 details 带 `open`；普通筛选清 focus。
   - 验证摘要 DOM显示给定的 2/3/1，按钮带 contact id/email。
3. 仅同步缓存键：`overlayAndDialogContrast.test.js`、`manualReplySubjectPrefill.test.js`、`batchSendTaskConsoleVisualFix.test.js`（I-7）；除 key 字符串外原测试不改。

## 变更文件清单

| # | 文件 | 类型 | 用途 |
|---|---|---|---|
| 1 | `src/main/resources/static/index.html` | 修改 | DOM 迁移、文案、缓存键 |
| 2 | `src/main/resources/static/styles.css` | 修改 | 两组 chip 的唯一新增布局规则 |
| 3 | `src/main/resources/static/app.js` | 修改 | pager、摘要、跳转、auto summary 刷新 |
| 4 | `src/test/js/checkRepliesRelocation.test.js` | 修改 | 综合静态 DOM/回归契约与缓存键 |
| 5 | `src/test/js/mailboxExpertGrouping.test.js` | 修改 | 精确跳转与目标组展开运行测试 |
| 6 | `src/test/js/overlayAndDialogContrast.test.js` | 修改 | 同步固定缓存键 |
| 7 | `src/test/js/manualReplySubjectPrefill.test.js` | 修改 | 同步固定缓存键 |
| 8 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改 | 同步固定缓存键三项断言 |

共 8 个文件；1 个子系统（静态前端）；0 个共享存储新字段。

## 验收标准

- I-1: HTML 源码断言三个 value/中文配对且不存在 `RAW -`、`CANDIDATE -`、`APPLICATION -`；`indexLevelLabels/indexLevelOptions` 保持原值。
- I-2: `expertIndexSize` 在 index.html 恰好一次且位于 `contactPager`；0/1 页运行测试断言 pager 不 hidden、文案 `第 1 / 1 页`、两按钮 disabled；filter badge 不计 page size，change 仍归零加载。
- I-3: `loadContactDetail` 片段不含 `detail.mails.slice().reverse().map(renderMailItem)`/`.mail-timeline`，含三个数字和查看按钮；全局 `renderMailItem` 函数及 mailbox 正文 renderer 仍存在。
- I-4: JS 运行测试断言跳转后请求 query 精确为目标 contact id、无 start/end、EXPERT+ALL、page=0；目标 details 有 `open`；普通筛选清除 focus。
- I-5: 两个 tag container 的 data-value 集合与实施前完全相等；`expertHIndexMinFilter/expertCitationMinFilter/expertRecentYearsFilter/expertGateTemplateFilter` 各恰好一次；chip 点击测试继续通过。
- I-6: `bulkAutoReplyBtn` HTML 计数为 1、contacts 片段为 0、mailbox actions 顺序正确；app.js 的 summary/bulk endpoint 及 init handler 不变；直接进入 mailbox 后按钮不再停在“加载中...”。
- I-7: index.html 三个 `?v=` 与 4 个固定测试全部等于 `20260831-expert-list-mailbox-ui`；`trustReplyWorkbenchSharedMount.test.js` 等值测试通过。
- S-1: DOM 与契约骨架一致；复用 CSS 无 diff；无 pager inline style/新 class。
- S-2: 新增 CSS 与契约代码块逐字一致，`.toolbar-chip-row` 仅 2 个 DOM 使用点；无卡片/边框/背景新增。
- S-3: 摘要只使用列出的既有 class，DOM 与骨架一致，无新 class/inline style。
- S-4: actions DOM 与骨架逐字一致，`.panel-head-actions` CSS 无 diff。
- 目标测试：`node --check src/main/resources/static/app.js`；`node --test src/test/js/checkRepliesRelocation.test.js src/test/js/mailboxExpertGrouping.test.js src/test/js/expertTypeFilter.test.js src/test/js/gateTemplateFilter.test.js src/test/js/batchEntryRelocation.test.js src/test/js/overlayAndDialogContrast.test.js src/test/js/manualReplySubjectPrefill.test.js src/test/js/batchSendTaskConsoleVisualFix.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js`。
- 全量门禁：`mvn test`（`pom.xml:186-216` 会执行 `node --test src/test/js/*.test.js` 和语法检查）；`git diff --check`。

## 人工验收清单

### A-1: 漏斗纯中文文案
- 前置条件: 登录后台并进入“专家列表”，展开筛选。
- 操作步骤: 打开“漏斗层级”下拉，依次选择三项。
- 预期结果: 仅显示“原始”“筛选”“有效”；默认是“筛选”；每次选择后列表自动回到第 1 页并刷新；页面不出现 RAW/CANDIDATE/APPLICATION 英文文案。
- 覆盖: I-1/S-1；observable outcome 2、must-NOT-change 1。

### A-2: 页大小与单页分页
- 前置条件: 当前筛选结果少于 10 位专家。
- 操作步骤: 1. 查看专家列表底部；2. 将“显示行数”从 50 改为 10；3. 再改回 50。
- 预期结果: 页大小控件只出现在专家列表底部，与上一页/`第 1 / 1 页`/下一页同排；两翻页按钮禁用；筛选工具栏无“显示行数”；顶部筛选数字不因 10/50 切换增加。
- 覆盖: I-2/S-1；observable outcome 3。

### A-3: 多页分页回归
- 前置条件: 当前筛选结果至少 21 位，显示行数选 10。
- 操作步骤: 1. 点击“下一页”；2. 确认显示第 2 页；3. 改显示行数为 20。
- 预期结果: 第 2 页文案为 `第 2 / 3 页`（以总数 21 为例）；改为 20 后回到 `第 1 / 2 页`；列表显示最多 20 位；两条查询模式均无报错。
- 覆盖: I-2；must-NOT-change 2、interaction point page-size→DB/ES read path。

### A-4: 研发类型和数据完整度真实样式
- 前置条件: 展开专家筛选，浏览器宽度约 2048px。
- 操作步骤: 查看并点击“生产研发”和“有职位”，再取消选择。
- 预期结果: “研发类型”“数据完整度”各占一整行；标签列宽 72px、顶部内边距 7px、标签与 chip 间距 8px；chip 高 26px，未选为透明背景+弱文字，hover 为蓝色浅底，选中为 `#1e40af` 蓝底白字；点击即刷新且 active 状态正确。
- 覆盖: I-5/S-2；observable outcome 4。

### A-5: 所有筛选项完整保留
- 前置条件: 筛选已展开。
- 操作步骤: 从上到下检查并分别操作 H-Index、引用数、近 N 年发表、按模板门禁。
- 预期结果: 四个控件均存在；H-Index/引用数可输入，近 N 年有 3/5/10 年选项，模板门禁可选；没有任何一个因 chip 整行布局消失或被遮挡。
- 覆盖: I-5/S-2；must-NOT-change 3。

### A-6: 联系详情只显示邮件数量
- 前置条件: 专家 A 在前置后端口径下有收到 2 封、成功发出 3 封、发送失败 1 封，并有会议/阶段状态数据。
- 操作步骤: 在专家列表选择 A，打开“联系详情”。
- 预期结果: 页面显示“收到 2 封｜成功发出 3 封｜发送失败 1 封”和“查看收发邮件”；不显示任何邮件主题、正文摘要、“查看完整正文”或纵向邮件时间线；会议安排、阶段状态、推荐下一步仍显示。
- 覆盖: I-3/I-4/S-3；observable outcome 1、must-NOT-change 6、interaction point DTO→摘要。

### A-7: 从摘要精确跳到全量邮件
- 前置条件: 专家 A 有超过 7 天的历史邮件，另有邮箱字符串相近的专家 B；A 的统计同 A-6。
- 操作步骤: 1. 在 A 联系详情点击“查看收发邮件”；2. 查看收发件箱顶部模式、日期和列表。
- 预期结果: 自动进入“收发件箱”；选中“按专家聚合”和“全部邮件”；账号/方向/标签/关键字/起止日期为空；收件人框显示 A 邮箱；只出现 A 一组且自动展开；组内包含超过 7 天的历史邮件，B 不出现。
- 覆盖: I-4/S-3；observable outcome 1、interaction point 摘要按钮→mailbox query/render。

### A-8: 精确焦点清理回归
- 前置条件: 已完成 A-7，当前仍只显示 A。
- 操作步骤: 1. 在收件人框输入 B 邮箱并点击“查询”；2. 切回专家列表；3. 再通过顶部导航普通进入收发件箱。
- 预期结果: 第 1 步不再受隐藏的 A contact id 限制；第 3 步按当前普通筛选加载，不会继续强制只显示 A；页面刷新按钮在未主动改筛选时仍保持当前焦点。
- 覆盖: I-4/I-7；must-NOT-change 7。

### A-9: 自动回复按钮迁移与状态加载
- 前置条件: 全局自动回复已开启；本次登录尚未访问专家列表。
- 操作步骤: 1. 直接进入收发件箱；2. 查看邮件记录标题栏；3. 点击自动回复按钮并在确认框取消；4. 再确认关闭，最后重新开启。
- 预期结果: 标题栏按钮顺序为“检查回复”“批量发送”“自动回复：全部开启 ✓”；专家列表工具栏没有自动回复按钮；取消不改变状态；关闭后文案“自动回复：全部关闭”，重新开启后恢复“自动回复：全部开启 ✓”；API 行为和原入口一致。
- 覆盖: I-6/S-4；observable outcome 5、must-NOT-change 4/5。

### A-10: 收发件箱正文与普通模式回归
- 前置条件: 收发件箱存在一封可查看正文的邮件和一条待处理来信。
- 操作步骤: 1. 普通导航进入收发件箱；2. 切“按邮件”；3. 点击邮件“查看”；4. 切“仅待处理”。
- 预期结果: 普通列表、邮件正文、处理动作均仍存在；联系详情移除时间线不影响这里；待处理模式不带日期参数并显示原待处理数据。
- 覆盖: I-3/I-4；must-NOT-change 6/7。

### A-11: 样式与缓存实机检查
- 前置条件: 用浏览器强制刷新部署后的页面，桌面宽度 2048px，再缩至 1024px。
- 操作步骤: 对照 A-2/A-4/A-6/A-9 区域，查看 Network 中 `styles.css`、`app.js`、`trust-reply-workbench.js` URL。
- 预期结果: 三个 URL 均带 `?v=20260831-expert-list-mailbox-ui`；无旧布局闪回；2048px 与 1024px 下 chip 自动换行但不溢出，分页和三个邮件操作按钮可见；无新增卡片背景、额外边框或 inline 样式造成的视觉失真。
- 覆盖: I-7/S-1/S-2/S-3/S-4；全部 UI 目测项。
