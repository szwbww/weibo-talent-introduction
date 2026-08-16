# 计划 04 — 专家列表可达性徽章展示

> 依赖：计划 03（需有数据）。与计划 05 可并行。共享证据见主计划。
>
> **2026-08-16 口径修正**：UNKNOWN 判据与悬停文案已按主计划「修正记录」A-4 更新。

## 需求描述

**Observable outcome**

1. 专家列表每行在 h 指数徽章旁显示一个可达性徽章，四档文案：`可达 高` / `可达 低` / `可达 未知` / `已退订 · 停发` / `邮箱失效 · 停发`。
2. 徽章带 `title` 悬停提示，说明判定依据（由已有字段拼装，不新增后端字段）。
3. BLOCKED 两档的行复选框禁用（不可被批量选中），但**行本身仍然显示**。

**What must NOT change**

- N-1 列表默认查询语义：不新增任何默认过滤，BLOCKED 专家仍在列表中（主计划 N-1）。
- N-2 既有 `hIndexBadge`（app.js:4760）、`enrichedBadge`（`:4763`）、`tagsHtml`、`bindingText`、`senderChangedTag` 的文案、顺序与样式。
- N-3 `.academic-badge` / `.academic-hindex` / `.academic-enriched` 三个既有 class 的规则块（styles.css:8033-8050）—— **派生新 class，不就地修改**。
- N-4 复选框既有的 `!contact.contactId ? 'disabled' : ''` 逻辑（app.js:4779）—— 新增禁用条件与其取并集，不替换。

**Out of scope**

- O-1 筛选与排序控件（属计划 05）。
- O-2 专家详情页的可达性展示。
- O-3 手动触发全量回填的按钮。

## 关键不变量

### Invariant I-4-1: 前端不重新判定，只映射已落库值
- Rule: 前端只读 `contact.reachability` 字符串并映射到文案/样式，禁止在 JS 中根据 `enrichedAt` / `lastPublicationYear` / `emailSource` 重算档位。
- Applies to: `renderContactListItems()`、`loadContacts()`。
- Violation consequence: 违反计划 02 的 I-2-1，前后端口径漂移，表现为「筛出来的和显示的不一致」。
- 来源: 计划 02 I-2-1

### Invariant I-4-2: 字段缺失渲染为「未知」，不渲染为「低」
- Rule: `contact.reachability` 为 `null` / `undefined` / 空串时，一律渲染 `可达 未知` + 中性灰样式。禁止 `||` 兜底到 `LOW`。
- Applies to: `renderContactListItems()` 的映射表与默认分支。
- Violation consequence: 违反主计划 I-3。
- 来源: 主计划 I-3

### Invariant I-4-3: 两个 BLOCKED 子档文案分离
- Rule: `BLOCKED_UNSUBSCRIBED` → `已退订 · 停发`；`BLOCKED_BOUNCED` → `邮箱失效 · 停发`。禁止合并为单一「停发」文案。
- Applies to: `renderContactListItems()` 的映射表。
- Violation consequence: 两者后续动作不同——退订不可复活（合规），硬退更换邮箱来源后可重新判定。合并文案后运营在列表上无法区分该做什么。
- 来源: original（需求方 2026-08-16 决策）

## 现状审计

### 前端锚点（主计划 R-14）

```bash
grep -n "async function loadContacts\|function renderContactListItems\|const hIndexBadge\|const enrichedBadge" src/main/resources/static/app.js
```
```
4507:async function loadContacts()
4739:function renderContactListItems()
4760:        const hIndexBadge = contact.hIndex != null
4763:        const enrichedBadge = contact.enrichedAt
```

`loadContacts` 有**两条数据路径**（`K-contact-list-dual-query-path`）：
- MySQL 路径（`:4630-4654`）：按状态/需人工介入筛选时走 `expert_contact`，学术字段全部硬编码为 null
- ES 路径（`:4655-4705`）：走 `/api/experts`

**两条路径都必须补 `reachability` 键**：MySQL 路径补 `reachability: null`（该路径无 ES 数据，
按 I-4-2 渲染为「未知」是正确表现），ES 路径补 `reachability: e.reachability ?? null`。
漏掉 MySQL 路径会导致按状态筛选时该键为 `undefined` —— 虽然 I-4-2 下渲染结果相同，
但显式写出以避免后续误判为「忘了传」。

### 后端响应字段（主计划 R-18）

`ExpertIndexResponse`（`ExpertIndexController.kt:380-402`）与其 `from()`（`:406-449`）
需各加一行；`expert.reachability` 由计划 02 的 `ExpertProfile` 提供。

### 前端样式盘点（Step 1b-fe）

**可复用 class**

| class | styles.css 行号 | 用途 |
|-------|----------------|------|
| `.academic-badge` | 8033-8041 | 列表项学术徽章的形状基座（圆角胶囊、11px、600 字重、`padding: 1px 7px`） |
| `.academic-hindex` | 8042-8046 | 主色徽章配色范例 |
| `.academic-enriched` | 8047-8051 | 成功色徽章配色范例 |
| `.expert-row-sub` | 1271-1279 | 徽章所在的第二行容器（flex / wrap / `gap: 4px 10px`） |
| `.expert-list-item` | 1240-1245 | 行容器 |
| `.expert-checkbox` | — | 复选框容器（`app.js:4778` 使用） |

**设计基准 token 实值**（styles.css `:root` 1-84，逐字）

```
--success: #059669;  --success-bg: rgba(5,150,105,0.08);   --success-border: rgba(5,150,105,0.18)
--warning: #d97706;  --warning-bg: rgba(217,119,6,0.08);   --warning-border: rgba(217,119,6,0.2)
--warning-strong: #b45309
--error:   #e11d48;  --error-bg:   rgba(225,29,72,0.07);   --error-border:   rgba(225,29,72,0.16)
--error-strong: #be123c
--surface: rgba(15,23,42,0.022);  --border: rgba(15,23,42,0.11);  --text-muted: #94a3b8
```

**既有 `.academic-badge` 规则块（改动前基线，styles.css:8033-8051 逐字）**

```css
.academic-badge {
    display: inline-flex;
    align-items: center;
    font-size: 11px;
    font-weight: 600;
    padding: 1px 7px;
    border-radius: 999px;
    line-height: 1.6;
}
.academic-hindex {
    background: var(--primary-light);
    color: var(--primary-hover);
    border: 1px solid rgba(var(--primary-rgb), 0.25);
}
.academic-enriched {
    background: var(--success-bg);
    color: #15803d;
    border: 1px solid var(--success-border);
    font-weight: 500;
}
```

**改动前 DOM 基线（app.js:4790-4798 逐字）**

```html
<div class="expert-row-sub">
    ${contact.employment ? `<span>${escapeHtml(contact.employment)}</span>` : ""}
    ${bindingText}
    ${hIndexBadge}${enrichedBadge}
    ${tagsHtml ? `<span class="expert-row-tags">${tagsHtml}</span>` : ""}
    ${senderChangedTag}
</div>
```

## 样式契约

### S-1: 可达性徽章基座与四档配色

**复用**：`.expert-row-sub`（styles.css:1271）作为容器，不修改其规则块。

**新增**：以下 CSS **逐字**追加到 `styles.css` 末尾的 `/* === 学术徽章（列表项） === */` 区块之后
（即 `.academic-enriched` 规则块之后、`/* === 详情 sub-tab === */` 注释之前）。
执行 agent 必须原样复制，不得增删属性或改值：

```css
/* === 可达性徽章（列表项） === */
.reach-badge {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 11px;
    font-weight: 600;
    padding: 1px 7px;
    border-radius: 999px;
    line-height: 1.6;
    cursor: help;
}
.reach-badge::before {
    content: '';
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: currentColor;
    flex-shrink: 0;
}
.reach-high {
    background: var(--success-bg);
    color: #15803d;
    border: 1px solid var(--success-border);
}
.reach-low {
    background: var(--warning-bg);
    color: var(--warning-strong);
    border: 1px solid var(--warning-border);
}
.reach-unknown {
    background: var(--surface);
    color: var(--text-muted);
    border: 1px solid var(--border);
}
.reach-blocked {
    background: var(--error-bg);
    color: var(--error-strong);
    border: 1px solid var(--error-border);
}
```

**DOM 结构**：徽章插入位置在 `hIndexBadge` **之前**（可达性优先于学术指标被读到）：

```html
<div class="expert-row-sub">
    ${contact.employment ? `<span>${escapeHtml(contact.employment)}</span>` : ""}
    ${bindingText}
    ${reachBadge}${hIndexBadge}${enrichedBadge}
    ${tagsHtml ? `<span class="expert-row-tags">${tagsHtml}</span>` : ""}
    ${senderChangedTag}
</div>
```

单个徽章的骨架：

```html
<span class="reach-badge reach-high" title="判定依据文本">可达 高</span>
```

**禁止项**：inline style；本契约未声明的新 class；对 `.academic-badge` / `.academic-hindex` /
`.academic-enriched` / `.expert-row-sub` / `.list-item` 任一既有规则块的修改。

### S-2: BLOCKED 行的复选框禁用态

**复用**：不新增任何 CSS。浏览器原生 `disabled` 复选框样式即可，与既有
「无 contactId 时禁用」（app.js:4779）视觉一致。

**DOM 结构**：

```html
<input type="checkbox" class="expert-select-cb" data-contact-id="${contact.contactId || ""}" ${(!contact.contactId || isBlockedReach(contact.reachability)) ? 'disabled' : ''}>
```

**禁止项**：不得为禁用行加行级置灰、降透明度或删除线——需求方决策是「打标但仍显示」，
视觉降级会读作「已删除」。

## 实现方案

### T1 — 后端响应透传（遵 I-4-1）
`ExpertIndexController.kt`：`ExpertIndexResponse` 加 `val reachability: String? = null`；
`from()` 加 `reachability = expert.reachability`。

### T2 — 前端两条路径补键（遵 I-4-1、I-4-2）
`app.js` `loadContacts()`：MySQL 路径（`:4630-4654` 区块）加 `reachability: null`；
ES 路径（`:4680-4704` 区块）加 `reachability: e.reachability ?? null`。

### T3 — 映射表与徽章渲染（遵 I-4-2、I-4-3、S-1）
`app.js` 在 `renderContactListItems()` 之前新增常量与 helper：

```js
const reachabilityMeta = {
    HIGH:                  { label: "可达 高",       cls: "reach-high" },
    LOW:                   { label: "可达 低",       cls: "reach-low" },
    BLOCKED_UNSUBSCRIBED:  { label: "已退订 · 停发", cls: "reach-blocked" },
    BLOCKED_BOUNCED:       { label: "邮箱失效 · 停发", cls: "reach-blocked" }
};
```
未命中键（含 null / undefined / 空串 / 未来新增的未知值）→ `{ label: "可达 未知", cls: "reach-unknown" }`。
`title` 文本由已有字段拼装：HIGH / LOW 档为 `邮箱来源 ${emailSourceLabel} · 域名 ${domain}`
（`emailSourceLabel`：`PAPER_FULLTEXT` → `论文通讯邮箱`，`ORCID_PUBLIC` → `ORCID 公开邮箱`）；
UNKNOWN 档固定为 `缺少邮箱来源信息，无法判定可达性`；
BLOCKED 两档分别为 `该专家已退订，不再发送` 与 `该邮箱曾硬退（收件人不存在），不再发送`。

### T4 — 复选框禁用（遵 N-4、S-2）
新增 `isBlockedReach(v)` helper（`v` 以 `BLOCKED_` 开头即为真），与既有条件取或。

### T5 — 样式落地（遵 S-1）
`styles.css` 追加 S-1 契约中的完整 CSS 块。

## 变更文件清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` | T1 |
| 2 | `src/main/resources/static/app.js` | T2/T3/T4 |
| 3 | `src/main/resources/static/styles.css` | T5 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt` | 补响应字段断言 |

文件数 4 ≤ 10。子系统 2（后端响应 / 前端）。新增 ES 字段 0。

## 验证命令

见主计划「验证命令」节。本计划专属：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertIndexControllerTest
git diff --check
```

## 验收标准

- I-4-1：`grep -n "emailSource\|enrichedAt" src/main/resources/static/app.js` 在 `renderContactListItems` 函数体范围内，仅出现在 `title` 文本拼装中，不出现在任何决定档位的条件判断中。
- I-4-2：`grep -n "reachabilityMeta" src/main/resources/static/app.js` 的默认分支返回 `reach-unknown`；`grep -n 'reachability || "LOW"\|reachability ?? "LOW"' src/main/resources/static/app.js` 零命中。
- I-4-3：`grep -c "已退订 · 停发\|邮箱失效 · 停发" src/main/resources/static/app.js` ≥ 2。
- S-1：`git diff src/main/resources/static/styles.css` 的新增块与本契约代码块逐字一致（可用 `diff <(sed -n '/=== 可达性徽章/,/^}/p' styles.css) <契约片段>` 核对）；`git diff` 中 `.academic-badge` / `.academic-hindex` / `.academic-enriched` / `.expert-row-sub` / `.list-item` 规则块零改动行。
- S-2：`grep -n "style=" src/main/resources/static/app.js` 在 `renderContactListItems` 函数体内无新增；无 `opacity` / `text-decoration: line-through` 相关新增。
- N-2：`git diff src/main/resources/static/app.js` 中 `hIndexBadge` / `enrichedBadge` / `tagsHtml` / `bindingText` / `senderChangedTag` 的定义行零改动。
- 回归：执行主计划「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 四档徽章正确渲染
- 前置条件: 计划 03 全量回填已完成；在候选层分别找到 4 类专家各 1 位（HIGH / LOW / 无 emailSource / 已退订）。
- 操作步骤: 1) 打开专家列表。2) 逐一定位这 4 位，观察第二行徽章。
- 预期结果: 依次显示绿色 `可达 高`、琥珀色 `可达 低`、灰色 `可达 未知`、红色 `已退订 · 停发`；徽章位于 h 指数徽章左侧；每个徽章前有一个 5px 圆点。
- 覆盖: Observable outcome 1 / I-4-2 / S-1

### A-2: 悬停提示可读
- 前置条件: 同 A-1。
- 操作步骤: 鼠标悬停在 4 类徽章上各停留 2 秒。
- 预期结果: 出现原生 tooltip；HIGH/LOW 档显示邮箱来源与域名；UNKNOWN 档显示「缺少邮箱来源信息，无法判定可达性」；BLOCKED 两档分别显示退订与硬退说明。
- 覆盖: Observable outcome 2

### A-3: BLOCKED 行仍显示且不可勾选
- 前置条件: 找到 1 位已退订专家。
- 操作步骤: 1) 在列表中确认该行存在。2) 尝试点击该行左侧复选框。3) 点击列表顶部「全选」。
- 预期结果: 行正常显示（无置灰、无透明度降低、无删除线）；复选框为禁用态无法勾选；全选后该行未被选中，底部计数不含它。
- 覆盖: Observable outcome 3 / N-1 / S-2

### A-4: 回归 —— 既有徽章与筛选未受影响
- 前置条件: 无。
- 操作步骤: 1) 对比改动前后同一位专家行的 h 指数徽章、「已补充」徽章、标签、账号文案。2) 使用「地区」「学科」「近 N 年」筛选各一次。3) 切换三个漏斗层级。
- 预期结果: 既有元素文案、顺序、配色、间距完全一致；筛选与层级切换结果与改动前一致；控制台无报错。
- 覆盖: N-2 / N-3

### A-5: 回归 —— 按状态筛选路径不报错
- 前置条件: 无。
- 操作步骤: 在列表使用「需人工介入」或任一联系人状态筛选（触发 MySQL 数据路径）。
- 预期结果: 列表正常渲染；该路径下全部行显示灰色 `可达 未知`（该路径无 ES 学术数据，属预期）；控制台无 `undefined` 相关报错。
- 覆盖: 现状审计「loadContacts 两条数据路径」
