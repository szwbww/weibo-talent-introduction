# P5：前端可见性（专家列表徽标 / 详情换绑 / 账号列表绑定数）

> 依赖 P1（绑定列）、P3（`countBindingsByAccount` 查询）、P4（换绑接口与标记列）。
> 主计划（跨计划约束 M-1..M-8、全局不变量 G-1..G-3）见 [00-main-plan-sender-binding.md](00-main-plan-sender-binding.md)。

## 需求描述

**Observable outcome**

1. 专家列表每一项显示当前绑定的发件账号；被运营主动换过绑的专家额外显示
   「发送账号已变更」标签。
2. 专家详情新增「绑定发件账号」信息卡，运营可在此直接改绑（下拉选 enabled 账号 + 保存），
   并可在已变更时清除标记。
3. 发件账号池表格新增「绑定专家数」列。

**What must NOT change**

- 专家列表的**两条查询路径**（ES `/api/experts` 与 DB `/api/expert-contacts`）
  各自的筛选、排序、分页行为（K-contact-list-dual-query-path）。
- 既有 `.expert-tag` / `.badge` / `.metadata-card` 三组 class 的 CSS 规则块
  （只新增派生 class，不就地修改）。
- 账号池表格既有 6 列的表头文案与顺序。
- 现有专家标签（ES tags）的渲染与编辑逻辑。

**Out of scope**

- 按「已变更」筛选专家列表（新增筛选控件需同步注册 5 处，
  见 K-expert-filter-registration-sites，单独成计划）。
- 账号池的「批量迁移」入口 UI（P4 的迁移接口先以 API 形式交付，
  UI 待运营流程确认后另做）。
- 账号列表按绑定数排序。
- ES 索引写入绑定字段。

## 关键不变量

### I-1: 绑定字段必须同时出现在两条列表路径的响应里
- Rule: `boundSenderAccountCode` 与 `senderAccountChanged` 必须同时加入
  `ExpertIndexResponse`（ES 路径）与 `ExpertContactResponse`（DB 路径），
  且前端两处 map（`app.js` 的 DB 分支与 ES 分支）都要取值。
- Applies to: `ExpertIndexController.kt` 的 `ExpertIndexResponse` + `from(...)`、
  `ExpertContactManagementController.kt` 的 `ExpertContactResponse` + `toResponse(...)`、
  `app.js` `loadContacts()` 的两个 `contacts = ...map(...)` 分支。
- Violation consequence: 只加 ES 路径 → 运营一旦启用「需人工关注 / 回复模式」筛选
  就切到 DB 路径，徽标与账号列静默消失（`app.js` 的 `useDbContactPath` 分叉）。
  这正是 `tags` 字段已经踩过的坑：`app.js` DB 分支写了 `tags: c.tags || []`，
  但 `ExpertContactResponse` 根本没有 `tags`，导致 DB 路径标签恒为空。
- 来源: K-contact-list-dual-query-path、K-detail-es-backed-fields-need-authoritative-read

### I-2: 绑定是 MySQL-only 事实，不得从 ES 文档读
- Rule: ES 路径的绑定值必须来自 `ExpertIndexController.kt:68-73` 已有的
  `expertContactRepository.findByOrcidIdIn(...)` join 结果（`contact?.xxx`），
  **禁止**读 `expert.xxx`（ES `ExpertProfile`）。
- Applies to: `ExpertIndexResponse.from(...)` 的新参数来源。
- Violation consequence: ES 索引没有这两个字段，会恒为 null，
  且给人"字段存在于 ES"的错误印象，后续会有人去写 ES mapping。
- 来源: K-contact-list-dual-query-path

### I-3: 账号绑定数一次查询装配，不得 per-account 查库
- Rule: `MailSenderAccountController.listAccounts()` 必须先取一次
  `ExpertContactRepository.countBindingsByAccount()`（P3 已建）转成 Map，
  再逐账号 lookup。`getAccount(code)` 单条端点可用 Map lookup 或
  单独的 `countByBoundSenderAccountCode(code)`。
- Applies to: `MailSenderAccountService` 新增的装配方法、`MailSenderAccountController.toResponse`。
- Violation consequence: `listAccounts` 是全表遍历，逐账号 COUNT 即 N+1。

### I-4: 详情页的绑定值必须来自详情接口，不得读列表缓存
- Rule: 详情卡片的绑定账号与变更标记取自 `loadContactDetail` 返回的
  `detail.contact.*`，**禁止** `state.contacts.find(...)`。
  换绑成功后必须 `await loadContactDetail(contactId)` 重新拉取，
  再 `await loadContacts()` 刷新列表。
- Applies to: `app.js` 详情渲染与换绑动作处理器。
- Violation consequence: 列表缓存可能来自 DB 路径或 ES 路径，字段口径不一；
  换绑后不重拉会显示旧值。
- 来源: K-detail-es-backed-fields-need-authoritative-read（severity P1，
  反例即 `app.js:4569-4587` 用列表缓存渲染标签）

### I-5: 换绑下拉只列 enabled 且非模拟器的账号
- Rule: 下拉选项来自 `GET /api/mail/sender-accounts` 结果的
  `.filter(a => a.enabled && a.accountCode !== "SIMULATOR_NOOP")`。
- Applies to: `app.js` 的下拉渲染。
- Violation consequence: 选到禁用账号会被 P4 的 I-3 以 400 拒绝，
  运营得到一个本可在前端避免的报错；选到模拟器则违反全局 G-3。

### I-6: 换绑失败必须展示服务端原文
- Rule: 换绑/清标接口的错误必须通过既有 `showStatus(error.message, "error")` 展示，
  不得替换为前端自造文案。P4 的异常已映射为 400 + 可读 `message`。
- Applies to: `app.js` 的换绑动作处理器。
- Violation consequence: 「目标发件账号已禁用：D」这类可操作信息被吞掉，
  运营只看到"操作失败"。

## 样式契约

### S-1: 专家列表「发送账号已变更」标签
- **复用**：`.expert-tag`（`styles.css:4485-4495`，`display:inline-block; font-size:10px;
  font-weight:600; padding:1px 6px; border-radius:4px; line-height:16px;`）
  与容器 `.expert-row-tags`（`styles.css:1247-1251`）。
  禁止自造"近似"标签样式替代 `.expert-tag`。
- **新增**：一个派生修饰 class，逐字复制到 `styles.css`，
  紧跟 `.expert-tag.tag-discovered`（`:4509-4513`）之后：

```css
.expert-tag.tag-sender-changed {
    background: rgba(217, 115, 13, 0.08);
    color: var(--warning);
    border-color: rgba(217, 115, 13, 0.18);
}
```

  > 取值依据：与同为"提示性"的 `.expert-tag.tag-discovered`（`:4509-4513`）
  > 逐字同色（`rgba(217,115,13,*)` + `var(--warning)`），保证视觉层级一致。
  > `.expert-tag` 基类已提供尺寸/圆角/字重，此处只覆盖三个颜色属性，
  > **不得**追加 padding / font-size / border-radius。

- **DOM 结构**（插入 `renderContactListItems` 的 `expert-row-sub` 内，
  紧接在既有 `${tagsHtml ? ...}` 之后）：

```html
<span class="expert-row-tags">
    <span class="expert-tag tag-sender-changed">发送账号已变更</span>
</span>
```

- **禁止项**：inline style；修改 `.expert-tag` / `.expert-tag.tag-discovered`
  的既有规则块；使用 `.badge` 系列（`.badge` 尺寸更大，会与同行的状态徽标混淆）。

### S-2: 专家列表的绑定账号文本
- **复用**：`.expert-row-sub`（`styles.css:1231-1240`）内的裸 `<span>`，
  其规则 `.expert-row-sub span`（`:1227-1229` + `:1253-1258`：
  `min-width:0; max-width:100%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;`）
  已提供省略号截断。
- **新增**：无。
- **DOM 结构**（与 `${contact.employment ? ... : ""}` 同级，置于其后）：

```html
<span>账号：{{accountCode 或 "未绑定"}}</span>
```

- **禁止项**：新增 class；inline style；把账号放进 `expert-row-main`
  （该行已有姓名块 + 状态 badge，`.expert-list-item .badge`（`:1260`）对其有专门规则）。

### S-3: 专家详情「绑定发件账号」信息卡
- **复用**：`.metadata-card`（`styles.css:1465-1473`）、
  `.metadata-card-header`（`:1479-1489`）、`.metadata-card-value`（`:1496-1502`）、
  `.badge.warn`（`:882-886`）、`.button`（表单按钮基类，
  用法见 `app.js:1667-1675` 的账号池操作按钮）。
- **新增**：一条布局规则，逐字复制到 `styles.css` 紧跟 `.metadata-card-value a:hover`
  （`:1512-1514`）之后：

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

- **DOM 结构**（插入 `loadContactDetail` 渲染的 `.metadata-grid` 内，
  紧跟「阶段状态」卡片之后）：

```html
<div class="metadata-card">
    <div class="metadata-card-header">
        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16v16H4z"/><polyline points="22,6 12,13 2,6"/></svg>
        <span>绑定发件账号</span>
    </div>
    <div class="metadata-card-value">
        <span>{{boundSenderAccountCode 或 "未绑定"}}</span>
        {{senderAccountChanged 时追加 <span class="badge warn">已变更</span>}}
        <div class="sender-binding-editor">
            <select id="senderBindingSelect"></select>
            <button class="button" data-action="rebind-sender-account" data-id="{{contactId}}">保存</button>
            {{senderAccountChanged 时追加：}}
            <button class="button" data-action="clear-sender-change-mark" data-id="{{contactId}}">清除标记</button>
        </div>
    </div>
</div>
```

- **禁止项**：新建 modal；inline style；对 `.metadata-card` 系列既有规则块的任何修改。
  该 SVG 图标沿用详情页其他卡片的规格（`viewBox="0 0 24 24" width="12" height="12"
  stroke="currentColor" stroke-width="2" fill="none"`，见 `app.js:7011-7014`、`:7020-7023`）。

### S-4: 账号池表格「绑定专家数」列
- **复用**：`index.html:292-303` 的既有 `<table>` 结构与 `app.js:1681-1693` 的
  `<td>` 写法，无 class。
- **新增**：无 CSS。
- **DOM 结构**：`index.html` 表头在「当前状态」（`:299`）之后、
  「管理操作」（`:300`）之前插入 `<th>绑定专家数</th>`；
  `app.js` 的行模板在 `<td>${statusCell}</td>` 之后、
  `<td class="actions">` 之前插入 `<td>${account.boundExpertCount ?? 0}</td>`。
  表头与表体必须同改（现为 6 列，改后 7 列）。
- **禁止项**：只改一侧导致列错位；给新列加 class 或 inline style。

### 既有 class 使用点核对

| class | 全部使用点（grep 结果） | 处置 |
|---|---|---|
| `.expert-tag` | `app.js:4719`（列表标签）、`app.js` 标签编辑器若干、`styles.css:4485/4497/4503/4509/4515/4522` | **派生新 class**，不就地修改 |
| `.metadata-card*` | `app.js:2047/2051/2055/2059/2066/2067/6497-6523/6633-6683/7011+/8819`、`styles.css:1465-1514` | **只新增子选择器** `.metadata-card-value .sender-binding-editor`，不改既有规则块 |
| `.badge.warn` | `app.js` 多处 + `styles.css:882-886` | 直接复用，零修改 |

## 现状审计

### 专家列表的两条查询路径（`app.js`）

- `loadContacts()`（`:4470`）：`const useDbContactPath = needsAttention || replyMode`（`:4480`）
  - **DB 路径**：`/api/expert-contacts` → `contacts = ...map(c => ({...}))`（`~:4595-4615`）。
    该 map 已写 `tags: c.tags || []`，但 `ExpertContactResponse` 无 `tags` 字段 →
    DB 路径标签恒为空（既有缺陷，本计划不修，但绝不重蹈）。
  - **ES 路径**：`/api/experts` → `contacts = rawExperts.map(e => ({...}))`（`~:4620-4665`）。
  - DB 路径下标签/地区/学科/学术筛选被强制禁用（`:4487-4520`）。
- `renderContactListItems()`（`:4698-4757`）：
  - `expert-row-main`：姓名块（`:4736-4743`）+ `${badge(status, statusType)}`（`:4744`）
  - `expert-row-sub`（`:4746-4751`）：`employment` / `hIndexBadge` / `enrichedBadge` /
    `tagsHtml`；整块由 `${contact.employment || tagsHtml || hIndexBadge || enrichedBadge ? ... : ""}`
    条件渲染 —— **新增绑定文本后该条件必须扩容**，否则纯绑定信息的专家不显示这一行。
  - 末尾 `staggerListItems("#contactList .list-item")`（`:4756`）。

### 后端列表 DTO

- `ExpertIndexResponse`（`ExpertIndexController.kt:350-380`）+ `from(...)`（`:381-419`）。
  contact 侧字段（`contactId` / `contactStatus` / `needsManualAttention` /
  `autoReplyEnabled` / `operatorStatus`）已由 `:74-84` 从 `contactMap` join 注入 ——
  新字段照此模式，**不碰 `ExpertProfile`**（I-2）。
- `ExpertContactResponse`（`ExpertContactManagementController.kt:376-397`）。
  **无 `tags` 字段**，印证 I-1 的风险。

### 专家详情

- `loadContactDetail(contactId)`（`app.js:6926`）→ `GET /api/expert-contacts/{id}`
  → `ExpertContactDetailResponse`（`ExpertContactManagementController.kt:295-304`），
  其 `contact` 为 `ExpertContactResponse`，因此 I-1 的字段扩展**同时**服务列表与详情。
- 详情的 `.metadata-grid` 卡片群从 `app.js:~7008` 起：
  阶段状态 → 推荐下一步 → 人工处理需求 → ORCID → 国家/地区。
- 详情内既有的动作按钮统一走 `data-action` + 事件委托（如 `send-manual-mail`，
  `app.js:8347-8361`），`element.dataset.id` 取 contactId。

### 账号池

- 后端 `MailSenderAccountController`（`mail/controller/`）：
  `listAccounts():29-30` → `service.listAccounts().map { toResponse(it) }`；
  `toResponse(account):80-107` 逐字段映射；`MailSenderAccountResponse:202-229`（28 个字段）。
  `MailSenderAccountService.listAccounts():20-21` = `repository.findAllByOrderByAccountCodeAsc()`。
- 前端 `loadAccounts()`（`app.js:1659-1694`）：
  `state.accounts = await api("/api/mail/sender-accounts")` → 生成 6 列 `<tr>`；
  表头在 `index.html:292-303`。
- P3 已提供 `ExpertContactRepository.countBindingsByAccount(): List<AccountBindingCount>`
  （单次 GROUP BY，已排除 NULL / 空串 / `SIMULATOR_NOOP`）。

### 前端 JS 测试入口（K-js-test-invocation-surface）

- `mvn test` 通过 `exec-maven-plugin`（`pom.xml:185-234`）执行
  `node --test src/test/js/*.test.js`，另有两条 `node --check` 语法检查。
- `verify.sh` **只跑 `normalizeDiscoveryResultSummary.test.js` 一个文件**，
  不可作为本计划的前端门禁。
- 既有同类用例可参照 `src/test/js/loadContactsFilter.test.js`、
  `src/test/js/expertTagBatchFix.test.js`（`node:test` + `vm` 抽取 `app.js` 函数 + DOM stub）。

### Interaction points

- **IP-1**：P4 的换绑（写 `sender_account_changed`）× 本计划两条列表路径（读）——
  换绑后必须两条路径都能看到标记，验收需分别在"默认 ES 路径"和
  "启用需人工关注筛选后的 DB 路径"下各看一次。
- **IP-2**：P4 的迁移（写 `bound_sender_account_code`，不动标记）× 列表显示——
  迁移后账号列变化但**标签不出现**，这是决策 ④ 的可观察证据。
- **IP-3**：P3 的 `countBindingsByAccount`（读）× P4 的迁移（写）——
  迁移后账号池的「绑定专家数」应立刻反映（无缓存）。

## 实现方案

### 阶段 1 — 后端 DTO 扩展

**T1.1 `ExpertIndexController.kt`**（遵 I-1/I-2）

`ExpertIndexResponse`（`:350-380`）加两字段（置于 `autoReplyEnabled` 之后）：

```kotlin
    val boundSenderAccountCode: String? = null,
    val senderAccountChanged: Boolean = false,
```

`from(...)`（`:381-419`）加两个同名形参（默认值同上），在返回体中赋值。
`:74-84` 的调用处传 `contact?.boundSenderAccountCode` /
`contact?.senderAccountChanged ?: false`（**取自 `contact`，不取 `expert`**）。

**T1.2 `ExpertContactManagementController.kt`**（遵 I-1）

`ExpertContactResponse`（`:376-397`）加同名两字段；
控制器内的 `ExpertContact → ExpertContactResponse` 映射函数同步赋值。

**T1.3 `MailSenderAccountService.kt` + `MailSenderAccountController.kt`**（遵 I-3）

`MailSenderAccountService` 注入 `expertContactRepository`，新增：

```kotlin
    fun bindingCountsByAccount(): Map<String, Long> =
        expertContactRepository.countBindingsByAccount()
            .associate { it.accountCode to it.boundCount }
```

`MailSenderAccountController`：
- `MailSenderAccountResponse` 加 `val boundExpertCount: Long = 0`（置于 `enabled` 之后）。
- `toResponse(account, boundExpertCount: Long = 0)` 增加形参并赋值。
- `listAccounts()` 改为先取一次 Map 再 lookup：

```kotlin
    @GetMapping
    fun listAccounts(): List<MailSenderAccountResponse> {
        val counts = service.bindingCountsByAccount()
        return service.listAccounts().map { toResponse(it, counts[it.accountCode] ?: 0L) }
    }
```

- 其余返回 `MailSenderAccountResponse` 的端点（`getAccount` / `createAccount` /
  `updateAccount` / `enableAccount` / `disableAccount` / `resetTodaySentCount` /
  `resumeAutoSend`，共 7 处）统一传
  `service.bindingCountsByAccount()[accountCode] ?: 0L`。
  **不要**在 `toResponse` 内部查库（I-3）。

### 阶段 2 — 专家列表（遵 I-1、S-1、S-2）

**T2.1 `app.js` 两个 map 分支同步取值**

DB 分支（`~:4595-4615`）与 ES 分支（`~:4620-4665`）各加两行：

```javascript
            boundSenderAccountCode: c.boundSenderAccountCode || null,
            senderAccountChanged: c.senderAccountChanged === true,
```

（ES 分支变量名为 `e`。）

**T2.2 `renderContactListItems()`（`:4708-4753`）**

在 `enrichedBadge` 定义之后新增两个片段变量：

```javascript
        const bindingText = `<span>账号：${escapeHtml(contact.boundSenderAccountCode || "未绑定")}</span>`;
        const senderChangedTag = contact.senderAccountChanged
            ? `<span class="expert-row-tags"><span class="expert-tag tag-sender-changed">发送账号已变更</span></span>`
            : "";
```

`expert-row-sub` 的条件与内容改为（S-1/S-2 的 DOM 骨架）：

```javascript
                ${contact.employment || tagsHtml || hIndexBadge || enrichedBadge || bindingText ? `
                <div class="expert-row-sub">
                    ${contact.employment ? `<span>${escapeHtml(contact.employment)}</span>` : ""}
                    ${bindingText}
                    ${hIndexBadge}${enrichedBadge}
                    ${tagsHtml ? `<span class="expert-row-tags">${tagsHtml}</span>` : ""}
                    ${senderChangedTag}
                </div>` : ""}
```

> `bindingText` 恒非空，因此条件表达式实际恒真 —— 保留完整条件是为了让
> 后续删除该字段时行为可回退，且避免 diff 里出现"看似无意义"的常量条件被误删。

**T2.3 `styles.css` 加 S-1 的规则块**（逐字复制，位置见 S-1）。

### 阶段 3 — 专家详情换绑（遵 I-4/I-5/I-6、S-3）

**T3.1 详情渲染加卡片**

在 `loadContactDetail` 渲染的 `.metadata-grid` 内、「阶段状态」卡片之后，
插入 S-3 的 DOM 骨架，值取自 `contact.boundSenderAccountCode` /
`contact.senderAccountChanged`（即 `detail.contact.*`，**不读 `state.contacts`**，I-4）。

**T3.2 下拉填充**（遵 I-5）

详情渲染完成后填充：

```javascript
    const sel = $("#senderBindingSelect");
    if (sel) {
        const accounts = state.accounts && state.accounts.length
            ? state.accounts
            : await api("/api/mail/sender-accounts").catch(() => []);
        const options = accounts.filter(a => a.enabled && a.accountCode !== "SIMULATOR_NOOP");
        sel.innerHTML = options.map(a =>
            `<option value="${escapeHtml(a.accountCode)}"${
                a.accountCode === contact.boundSenderAccountCode ? " selected" : ""
            }>${escapeHtml(a.accountCode)} · ${escapeHtml(a.senderEmail)}</option>`
        ).join("");
    }
```

**T3.3 动作处理器**（遵 I-4/I-6，照抄 `send-manual-mail`（`:8347-8361`）的形状）

在同一事件委托链里新增两个分支：

```javascript
    if (action === "rebind-sender-account") {
        const code = $("#senderBindingSelect")?.value;
        if (!code) { showStatus("请选择发件账号", "error"); return; }
        await api(`/api/expert-contacts/${id}/sender-account`, {
            method: "POST",
            body: JSON.stringify({ senderAccountCode: code, operatorName: null, note: null })
        });
        showStatus("发件账号已变更");
        await loadContactDetail(id);   // I-4
        await loadContacts();
        return;
    }
    if (action === "clear-sender-change-mark") {
        await api(`/api/expert-contacts/${id}/sender-account/clear-change-mark`, {
            method: "POST",
            body: JSON.stringify({ operatorName: null, note: null })
        });
        showStatus("已清除变更标记");
        await loadContactDetail(id);
        await loadContacts();
        return;
    }
```

`api(...)` 的既有错误处理会把服务端 `message` 抛成 `Error`，由外层
`catch (error) { showStatus(error.message, "error") }` 展示（I-6）——
**不要**在此加 try/catch 自造文案。

**T3.4 `styles.css` 加 S-3 的两条布局规则**（逐字复制）。

### 阶段 4 — 账号池表格（遵 S-4）

**T4.1 `index.html:299` 之后插入 `<th>绑定专家数</th>`。**

**T4.2 `app.js` `loadAccounts()`（`:1681-1693`）的行模板**，
在 `<td>${statusCell}</td>` 之后插入：

```javascript
            <td>${account.boundExpertCount ?? 0}</td>
```

### 阶段 5 — 测试

**T5.1 新建 `src/test/js/senderBindingDisplay.test.js`**

参照 `src/test/js/loadContactsFilter.test.js` 的 `vm` 抽取 + DOM stub 范式。用例：

| 用例 | 断言 |
|---|---|
| `renders binding account text for bound contact` | 输出含 `账号：ACC_A` |
| `renders 未绑定 when no binding` | `boundSenderAccountCode: null` → 输出含 `账号：未绑定` |
| `renders sender-changed tag only when flag is true` | `senderAccountChanged: true` → 含 `expert-tag tag-sender-changed` 与 `发送账号已变更`；`false` → 均不含 |
| `escapes account code` | `boundSenderAccountCode: '<img src=x>'` → 输出含 `&lt;img` 不含 `<img` |
| `expert-row-sub renders even when only binding exists` | 无 employment/tags/hIndex/enrichedAt 时仍输出 `expert-row-sub`（T2.2 条件扩容） |
| `account row renders bound expert count` | `loadAccounts` 行模板对 `boundExpertCount: 12` 输出 `<td>12</td>`；缺字段时输出 `<td>0</td>` |

> **DOM stub 陷阱**：K-dom-stub-tests-hide-dangling-refs 记载 stub 会掩盖悬空引用。
> 本用例必须让 stub 覆盖 `#senderBindingSelect`，
> 并断言 T3.3 两个 action 分支中调用的 `loadContactDetail` / `loadContacts`
> 均为已定义函数（否则运行时才暴露）。

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` | 修改 | ES 路径 DTO + from() |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt` | 修改 | DB 路径 DTO + 映射 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailSenderAccountController.kt` | 修改 | 账号 DTO + 8 处 toResponse |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt` | 修改 | `bindingCountsByAccount()` |
| 5 | `src/main/resources/static/app.js` | 修改 | 两个 map、列表渲染、详情卡片、两个 action |
| 6 | `src/main/resources/static/index.html` | 修改 | 账号表加 1 个 `<th>` |
| 7 | `src/main/resources/static/styles.css` | 修改 | S-1 一块 + S-3 两块 |
| 8 | `src/test/js/senderBindingDisplay.test.js` | 新增 | 6 例 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountServiceTest.kt` | 修改 | 编译修复：5 处位置传参构造（:25/:681/:717/:752/:792）追加 1 个 `Mockito.mock(ExpertContactRepository::class.java)` 实参（A8 授权；M-4 锁定测试 :35-46/:48-57 逐字不变） |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountContextTest.kt` | 修改 | 装配修复：ApplicationContextRunner 补 1 个 `.withBean(ExpertContactRepository::class.java, Supplier { Mockito.mock(...) })` 注册（A10 授权；不改断言） |

文件数 10 ≤ 10 ✓　子系统 2（后端 DTO / 前端）≤ 2 ✓　新增存储字段 0 ✓

## 验证命令

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。
> 前端 JS 用例的权威门禁是对目标文件的 `node --test` 单跑；
> `verify.sh` 只跑 `normalizeDiscoveryResultSummary.test.js`，**不可**用作本计划门禁
> （来源：K-js-test-invocation-surface）。

```bash
# 前端权威门禁（本计划新增用例）
node --test src/test/js/senderBindingDisplay.test.js

# 前端全量 JS 用例
node --test src/test/js/*.test.js

# app.js 语法检查（与 pom.xml:203-216 的 node-check-app 同命令）
node --check src/main/resources/static/app.js

# 全量测试（后端回归 + 上述 node 用例，经 exec-maven-plugin 触发）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：`node --test` 输出 `# fail 0`；`mvn` 输出
`Tests run: N, Failures: 0, Errors: 0` 与 `BUILD SUCCESS`；`node --check` 无输出且退出码 0。
来源：`CLAUDE.md` 项目元信息（`test_command` / `build_command`）+
`pom.xml:185-234`（node 执行绑定）+ K-js-test-invocation-surface。

## 验收标准

- **I-1**: `grep -n "boundSenderAccountCode" src/main/kotlin` 同时命中
  `ExpertIndexController.kt` 与 `ExpertContactManagementController.kt`；
  `grep -n "boundSenderAccountCode" src/main/resources/static/app.js`
  在 `loadContacts` 内命中**两处**（DB 分支与 ES 分支）。
- **I-2**: `ExpertIndexController.kt` 中新字段的取值语句为 `contact?.` 前缀，
  `git diff` 不含 `expert.boundSenderAccountCode` 或对 `ExpertProfile` 的改动。
- **I-3**: `MailSenderAccountController.listAccounts()` 内
  `service.bindingCountsByAccount()` 恰调用 1 次且在 `.map {}` 之外；
  `toResponse` 方法体不含 `expertContactRepository` 或 `bindingCountsByAccount`。
- **I-4**: 详情卡片与换绑处理器中不出现 `state.contacts.find`；
  两个 action 分支都以 `await loadContactDetail(id)` 结尾（在 `loadContacts()` 之前）。
- **I-5**: 下拉填充语句含 `.filter(a => a.enabled && a.accountCode !== "SIMULATOR_NOOP")`。
- **I-6**: 两个 action 分支内无 `try {` / `catch`，无硬编码的失败文案字符串。
- **S-1**: `styles.css` 中 `.expert-tag.tag-sender-changed` 规则块与契约**逐字一致**
  （三条属性，无增删）；`.expert-tag`（`:4485-4495`）与
  `.expert-tag.tag-discovered`（`:4509-4513`）在 diff 中零改动。
- **S-2**: 列表模板中绑定文本为裸 `<span>`，无 class、无 style 属性。
- **S-3**: `.metadata-card-value .sender-binding-editor` 两条规则块与契约逐字一致；
  `.metadata-card` / `.metadata-card-header` / `.metadata-card-value`
  的既有规则块（`styles.css:1465-1514`）在 diff 中零改动；
  新卡片 SVG 属性与 `app.js:7011-7014` 同规格。
- **S-4**: `index.html` 表头 `<th>` 数量为 7，`app.js` 行模板 `<td>` 数量为 7。
- **全局**: `git diff -- src/main/resources/static/` 中不出现 `style="` 新增行；
  不出现契约未声明的新 class。
- **回归**: 执行「验证命令」节的全部命令通过。

## 人工验收清单

### A-1: 专家列表显示绑定账号（ES 路径）
- 前置条件: 清空「需人工关注」「回复模式」两个筛选（保证走 ES 路径）；
  库中至少一位专家 `bound_sender_account_code='ACC_A'`、至少一位为 NULL。
- 操作步骤: 打开专家漏斗视图，刷新列表，观察列表项第二行。
- 预期结果: 已绑定的显示 `账号：ACC_A`；未绑定的显示 `账号：未绑定`。
  文本与「机构」同一行，超长时以省略号截断而非换行撑破。
- 覆盖: I-1、S-2、需求描述第 1 条

### A-2: 专家列表显示绑定账号（DB 路径）
- 前置条件: 同 A-1 的数据。
- 操作步骤: 把「回复模式」筛选设为「人工」（触发 `useDbContactPath`），刷新列表。
- 预期结果: 绑定账号文本**依然显示**，与 A-1 中同一位专家的账号值一致。
  （若此处变成「未绑定」，即为 I-1 违反 —— 与 `tags` 字段已有的缺陷同型。）
- 覆盖: I-1、IP-1、K-contact-list-dual-query-path

### A-3: 主动换绑后出现「发送账号已变更」标签
- 前置条件: 一位绑定 `ACC_A` 的专家；另有 enabled 账号 `ACC_B`。
- 操作步骤:
  1. 打开该专家详情，在「绑定发件账号」卡片的下拉里选 `ACC_B`，点「保存」。
  2. 观察详情卡片；返回列表观察该专家条目。
  3. 切到 DB 路径（设「回复模式=人工」）再看一次。
- 预期结果:
  - 详情卡片账号变为 `ACC_B`，旁边出现橙色 `已变更` 徽标，并多出「清除标记」按钮。
  - 列表条目出现橙色小标签「发送账号已变更」，与既有「新发现」标签同尺寸同色系。
  - DB 路径下标签同样显示。
- 覆盖: I-1、I-4、S-1、S-3、需求描述第 1、2 条

### A-4: 批量迁移后账号变了但不打标（决策 ④ 的可观察证据）
- 前置条件: `ACC_C` 名下有 ≥3 位 `sender_account_changed=0` 的专家；另有 enabled 账号 `ACC_D`。
- 操作步骤:
  1. 调用 P4 的迁移接口：`POST /api/expert-contacts/sender-account/migrate`
     body `{"fromAccountCode":"ACC_C","toAccountCode":"ACC_D"}`。
  2. 刷新专家列表，找到这几位专家。
- 预期结果: 账号文本变为 `账号：ACC_D`；**不出现**「发送账号已变更」标签。
- 覆盖: IP-2、决策 ④

### A-5: 换绑下拉只列可用账号
- 前置条件: 系统内存在 `enabled=0` 的账号 `ACC_X` 与 `SIMULATOR_NOOP`。
- 操作步骤: 打开任一专家详情，展开「绑定发件账号」的下拉。
- 预期结果: 选项中**不含** `ACC_X`，**不含** `SIMULATOR_NOOP`；
  当前绑定账号处于选中态。
- 覆盖: I-5、全局 G-3

### A-6: 换绑失败展示服务端原文
- 前置条件: 先在下拉里选中某 enabled 账号，然后在**另一个浏览器标签页**
  把该账号禁用（制造前后端状态不一致）。
- 操作步骤: 回到详情页点「保存」。
- 预期结果: 顶部错误提示的文案包含「目标发件账号已禁用」与该账号 code，
  而不是泛化的「操作失败」；专家的账号显示未变。
- 覆盖: I-6

### A-7: 清除标记后标签消失、账号不变
- 前置条件: A-3 已完成（该专家带标签，绑定为 `ACC_B`）。
- 操作步骤: 在详情卡片点「清除标记」，然后刷新列表。
- 预期结果: 详情的 `已变更` 徽标与「清除标记」按钮消失，账号仍为 `ACC_B`；
  列表条目的「发送账号已变更」标签消失，`账号：ACC_B` 保留。
- 覆盖: I-4、需求描述第 2 条

### A-8: 账号池显示绑定专家数
- 前置条件: 执行 `SELECT bound_sender_account_code, COUNT(*) FROM expert_contact
  WHERE bound_sender_account_code IS NOT NULL GROUP BY 1;` 记录各账号绑定数。
- 操作步骤: 打开「发件账号池」页，查看新增的「绑定专家数」列。
- 预期结果: 每行数值与 SQL 结果逐一相等；无绑定的账号显示 `0`；
  表头 7 列与表体 7 列**对齐无错位**。
- 覆盖: I-3、S-4、需求描述第 3 条

### A-9: 迁移后账号池计数即时更新
- 前置条件: A-4 已完成。
- 操作步骤: 刷新「发件账号池」页。
- 预期结果: `ACC_C` 的绑定专家数变为 0，`ACC_D` 增加了相应数量；两者之和不变。
- 覆盖: IP-3

### A-10（回归）: 既有标签与徽标样式未变
- 前置条件: 一位同时带 ES 标签（如「新发现」「重点关注」）、H-Index 徽标、
  状态徽标，且已换过绑的专家。
- 操作步骤: 在列表中找到该专家，逐项目测。
- 预期结果: 「新发现」标签的颜色/尺寸/圆角与本计划上线前一致；
  H-Index 与「已补充」徽标位置不变；右上角状态徽标不变；
  新增的「发送账号已变更」标签与「新发现」**同尺寸同色系**，排在 ES 标签之后。
- 覆盖: must-NOT-change 第 2 条、S-1 的「禁止修改既有 class」

### A-11（回归）: 列表筛选与分页未受影响
- 前置条件: 无。
- 操作步骤: 依次切换漏斗层级、设置国家/学科/标签筛选、翻页、切换排序。
- 预期结果: 各项行为与上线前一致；「筛选结果: N 位专家，当前显示 M 位」计数正确；
  DB 路径下标签/地区/学科筛选仍被灰化禁用。
- 覆盖: must-NOT-change 第 1 条
