# 开发计划：跳转「查看专家」后左侧列表同步并高亮目标专家

## 需求描述

**可观察结果**：在监控活动表 / 邮箱列表 / 未匹配处理 / 邮件记录表中点击「查看专家」（或专家名链接）跳转到专家页（contacts 视图）后，左侧专家列表必须显示该专家这一项，并将其高亮为选中态（`active`），与在列表内直接点击 `select-expert` 的视觉结果一致。

**不可改变的行为**：
- 右侧详情面板的加载内容与现状一致（`loadContactDetail` 渲染的 DOM 不变）。
- 列表内直接点击专家（`select-expert`，app.js:4044）的现有行为不变。
- 列表的筛选/分页查询逻辑（`loadContacts`，app.js:1673）不变。
- 后端 API、Kotlin 代码、数据库不改动。

**超出范围（明确不做）**：
- 不调整后端 `/api/experts`、`/api/expert-contacts/{id}` 的返回结构。
- 不为跳转目标做「自动翻页定位到它在筛选结果中的真实页码」——本期采用「置顶注入合成行」策略即可满足可见+高亮。
- 不新增滚动高亮脉冲动画（如需可后续单独做，属 styles.css 美化）。
- 不重构其它列表渲染。

## 关键不变量

### Invariant I-1：列表高亮以 orcidId 为唯一键
- Rule：左侧列表项的选中态判定恒为 `state.selectedExpertOrcid === contact.orcidId`。任何跳转入口在加载详情后，必须把 `state.selectedExpertOrcid` 设为目标专家的 `orcidId`（来源：`detail.contact.orcidId`），不得保留 `null`，也不得用 `contactId` 作为高亮键。
- Applies to：所有跳转入口处理器——`open-monitoring-contact`（app.js:5150 监控表、app.js:5811 邮箱列表）、`open-contact-from-unmatched`（app.js:4667）。
- Violation consequence：列表项渲染时 `active` 判定恒为 false，目标专家不高亮（即当前 bug）。

### Invariant I-2：跳转后目标专家必在左侧列表中可见
- Rule：跳转完成后，`state.contacts` 中必须存在 `orcidId === detail.contact.orcidId` 的一项。若加载后的筛选结果不含该专家，则用 `detail.contact` 构造一条合成列表项并置于 `state.contacts` 首位后重渲染。
- Applies to：跳转统一入口 `openContactInList(contactId)`。
- Violation consequence：左侧列表为空或不含该专家，用户看不到「对应专家」（当前 `open-monitoring-contact` 路径完全不加载列表，即现象之一）。

### Invariant I-3：列表渲染逻辑单一来源
- Rule：列表 DOM 的生成只能有一处实现。从 `loadContacts` 内联 `map`（app.js:1804-1844）抽出为 `renderContactListItems()`，`loadContacts` 与 `openContactInList` 都调用它；不得复制一份渲染模板。
- Applies to：`loadContacts`、`openContactInList`、新函数 `renderContactListItems`。
- Violation consequence：两处模板漂移，高亮 class、`data-orcid`/`data-contact-id` 属性不一致，导致后续点击/高亮再次错位。

## 现状审计

### 前端状态 `state`（app.js:1 起）
- `state.selectedExpertOrcid`（app.js:22，初始 `null`）：列表高亮键。
- `state.contacts`（app.js:1787 赋值）：左侧列表数据数组，元素形如 `{orcidId,email,displayName,indexLevel,indexLevelName,contactId,contactStatus,operatorStatus,needsManualAttention,country,employment,keyword,tags,updatedAt}`（构造见 app.js:1721-1736 与 1750-1765）。

### 左侧列表渲染
- **写路径（渲染）**：`loadContacts`（app.js:1673）末尾内联 `state.contacts.map(...)` 生成 `#contactList` innerHTML（app.js:1804-1844）。高亮判定在 app.js:1820：`state.selectedExpertOrcid === contact.orcidId ? "active" : ""`；列表项带 `data-orcid` 与 `data-contact-id`。
- **读/交互路径**：
  1. `select-expert`（app.js:4044-4056）：点击列表项，设 `state.selectedExpertOrcid = orcidId`，手动 toggle `.active`，再按 `expert.contactId` 调 `loadContactDetail` 或 `showExpertDetail`。**这是正确范式**。
  2. 跳转入口（见下）调用 `loadContactDetail` 但未同步列表/高亮。

### 详情加载 `loadContactDetail(contactId)`（app.js:3627-3840）
- 入参仅 `contactId`；内部 `const contact = detail.contact`，`detail.contact` 为后端 `ExpertContactResponse`，**含 `orcidId` 字段**（确认：`ExpertContactManagementController.kt:349`）。
- 当前**无返回值**（app.js:3840 直接结束）。其内部用 `state.contacts.find(item => item.orcidId === state.selectedExpertOrcid)`（app.js:3635）取列表里的 `expert` 作为 name 兜底——跳转场景下该 find 取不到，但 name 已优先用 `contact.expertName`，不影响。

### 跳转入口（写路径，全部需修正）
1. **监控活动表** `#monitoringActivityTable` 处理器（app.js:5147-5155）：
   ```js
   if (target.dataset.action === "open-monitoring-contact") {
       state.selectedExpertOrcid = null;        // ← 置 null，违反 I-1
       setView("contacts");
       await loadContactDetail(Number(target.dataset.id));  // ← 不加载列表，违反 I-2
   }
   ```
   触发按钮/链接来源：`monitoringContactCell`（app.js:5074）、未匹配 actions（app.js:4393）。
2. **邮箱列表** `#mailboxList` 处理器（app.js:5808-5823）：同样的 `open-monitoring-contact` 分支（app.js:5811-5815），同病。触发来源含邮件记录表链接（app.js:6209）。
3. **未匹配处理** `handleUnmatchedAction` 的 `open-contact-from-unmatched`（app.js:4667-4671）：已调 `loadContacts()` 再 `loadContactDetail()`，但**未设 `selectedExpertOrcid`**（违反 I-1），且若目标不在筛选结果内仍不可见（违反 I-2）。触发来源：app.js:4513。

### 交互点
- 「列表渲染写路径（loadContacts/新 openContactInList）」× 「高亮读取（app.js:1820 的 `active` 判定）」：三处跳转入口都会改 `state.selectedExpertOrcid` 与 `state.contacts`，必须经统一渲染函数保证一致——由 I-3 覆盖。

## 实现方案

全部改动集中在单文件 `src/main/resources/static/app.js`。

### 任务 1：抽出列表渲染函数（遵循 I-3）
- 将 `loadContacts` 内 app.js:1804-1846 的 `state.contacts.map(...)` 渲染与 `staggerListItems` 调用，抽为函数 `renderContactListItems()`：读取 `state.contacts` 与 `state.selectedExpertOrcid`，写入 `#contactList`，保留现有空态分支（app.js:1795-1802）逻辑。
- `loadContacts` 在赋值 `state.contacts` 后改为调用 `renderContactListItems()`，保持现有 `#contactCountInfo`/分页等副作用不变。
- 渲染模板（含 app.js:1820 的 `active` 判定、`data-orcid`、`data-contact-id`）保持逐字不变，仅迁移位置。

### 任务 2：让 `loadContactDetail` 返回 `detail.contact`（支撑 I-1）
- 在 `loadContactDetail`（app.js:3627）函数体末尾（app.js:3839 之后、3840 `}` 之前）`return contact;`（即 `detail.contact`）。
- 不改动其渲染逻辑，仅补返回值；现有所有 `await loadContactDetail(...)` 调用忽略返回值，向后兼容。

### 任务 3：新增统一跳转入口 `openContactInList(contactId)`（遵循 I-1、I-2、I-3）
逻辑顺序：
1. `setView("contacts")`。
2. 若 `!state.contacts || state.contacts.length === 0`，先 `await loadContacts()`（保证有基础列表；已在列表页时可跳过以省一次请求——以 `state.contacts.length === 0` 为判据）。
3. `const contact = await loadContactDetail(contactId);`
4. `state.selectedExpertOrcid = contact?.orcidId || null;`（I-1）
5. 若 `state.contacts` 中不存在 `orcidId === contact.orcidId` 的项（I-2），用 `contact` 构造合成列表项并 `unshift` 进 `state.contacts`：
   ```js
   {
     orcidId: contact.orcidId,
     email: contact.expertEmail,
     displayName: contact.expertName,
     indexLevel: contact.currentIndexLevel,
     indexLevelName: indexLevelLabels[contact.currentIndexLevel] || contact.currentIndexLevel,
     contactId: contact.id,
     contactStatus: contact.currentStatus,
     operatorStatus: contact.operatorStatus,
     needsManualAttention: contact.needsManualAttention,
     country: "", employment: "", keyword: "",
     tags: contact.tags || [], updatedAt: contact.updatedAt || null
   }
   ```
   （字段对齐 app.js:1721-1736 的形状。）
6. `renderContactListItems()` 重渲染（I-3）。
7. 渲染后定位并滚动到目标项：`document.querySelector('#contactList .list-item.active')?.scrollIntoView({block:"nearest"})`。

### 任务 4：三处跳转入口改为调用统一入口（遵循 I-1、I-2）
- app.js:5150-5154（监控表）`open-monitoring-contact` 分支体替换为 `await openContactInList(Number(target.dataset.id));`。
- app.js:5811-5815（邮箱列表）`open-monitoring-contact` 分支体替换为 `await openContactInList(Number(target.dataset.id));`。
- app.js:4667-4671（未匹配）`open-contact-from-unmatched` 分支体替换为 `await openContactInList(Number(id));`（移除原 `loadContacts()`+`loadContactDetail()` 两行，统一入口已内含）。

## 变更文件清单

| 文件 | 改动 |
|------|------|
| `src/main/resources/static/app.js` | 任务1 抽出 `renderContactListItems()` 并改 `loadContacts` 调用；任务2 `loadContactDetail` 末尾 `return contact`；任务3 新增 `openContactInList(contactId)`；任务4 三处跳转分支改调统一入口 |

文件数：1（≤10 ✅）。子系统数：1（前端静态 UI，≤2 ✅）。无新增共享存储字段。

## 验收标准

- **I-1**：从监控活动表、邮箱列表、邮件记录表、未匹配详情四个入口分别点击「查看专家」/专家链接跳转后，断言 `state.selectedExpertOrcid ===` 目标专家 `orcidId`，且左侧对应 `.list-item` 含 `active` class（DOM 检查 `#contactList .list-item.active` 唯一且 `data-orcid` 等于目标）。
- **I-2**：在左侧列表当前筛选结果**不含**目标专家的场景（例如先把层级筛选切到不含该专家的层级再触发跳转），断言跳转后 `#contactList` 内存在 `data-contact-id` 等于目标的列表项（合成行置顶）。
- **I-3**：全局搜索 app.js 确认列表项模板（`class="list-item expert-list-item ...active..."` 字符串、`data-action="select-expert"`）只存在于 `renderContactListItems` 一处。
- **集成（跨交互点）**：跳转高亮后，再在左侧点击另一专家（`select-expert`），断言旧高亮移除、新项高亮、右侧详情切换——验证统一渲染与既有 `select-expert` 范式共存无冲突。
- **回归**：列表页直接刷新（`loadContacts`）后，空态文案、分页 `#contactCountInfo`、stagger 入场动画表现与改动前一致。

## 自检清单

- [x] `关键不变量` 存在，新增交互行为均有不变量（I-1/I-2/I-3）
- [x] `现状审计` 列出所有跳转写路径（grep 确认 4 处触发来源、3 个处理器）
- [x] 无未被不变量覆盖的新写路径
- [x] 文件数 ≤ 10（1 个）
- [x] 子系统数 ≤ 2（1 个）
- [x] 每个任务引用其治理不变量编号
- [x] 验收标准每个不变量至少一条检查
- [x] 文件清单无「及相关文件」等模糊措辞
- [x] 超出范围明确列出（自动翻页定位、滚动脉冲动画、后端改动）
- [x] 计划保存至 `docs/plans/2026-06-26/`
