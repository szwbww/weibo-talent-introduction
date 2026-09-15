# 子计划 02：专家联系下方的材料状态标签

> 依赖：先完成并发布 [`01-expert-material-status-backend.md`](./01-expert-material-status-backend.md)。
> 交付边界：只消费子计划 01 的材料 GET/PUT API；不修改后端、邮件模板或变量正文。

---

## 需求描述

**Observable outcome**

1. 选择已有联系人后，在专家详情顶部“专家联系”操作区下方常驻展示 7 个标签：简历、护照、学位、工作、出版、专利、研究。
2. 点击任一标签弹出“待提供 / 已提供 / 暂不愿提供”三项；点击状态立即保存，无“编辑材料”按钮、无二次保存按钮、无成功 toast。
3. “已提供”显示绿色标签与 `✓`；“暂不愿提供”显示置灰、删除线与 `⊘`；“待提供”显示中性标签。刷新/切换专家后显示服务端持久状态。

**What must NOT change**

1. 不在页面展示 7 条英文材料正文、`${pendingExpertMaterials}` 或变量预览。
2. 不显示第八项，不允许自定义增删标签，不显示说明图例或提示文案。
3. 不改变现有发件账号下拉、模板选择、“发送邮件”、“更多”、三项状态 select 与“保存变更”的行为。
4. 不在保存材料成功后调用 `showStatus()`；API 失败时允许且只显示一次错误提示，失败前的标签状态保持不变。
5. 仅对有 `contactId` 的专家显示材料行；`showExpertDetail()` 展示的未建联专家继续隐藏整个 `#contactHeadActions`。
6. 不引入自动文件识别、上传入口、编辑模式、批量操作或前端本地持久化。

**Out of scope**

- 邮件变量/模板编辑器展示（已由子计划 01 注册变量，本计划不改模板页面）。
- 移动端专用弹窗、拖拽排序、键盘方向键菜单导航。
- 乐观更新、离线缓存、失败自动重试。
- 修改共享 `.dropdown-menu`、`.dropdown-item`、`.contact-head-status-row` 或 `.contact-head-label` 的既有规则。

---

## 关键不变量

### Invariant I2-1：UI 精确 7 项且顺序服从 API
- Rule：页面只遍历 GET API 返回数组，不在前端补第八项或英文正文；正常响应必须呈现 7 个中文标签，顺序与 API 一致。
- Applies to：`loadContactDetail()`、`renderExpertMaterialRow()`、JS 测试。
- Violation consequence：前后端目录双真源，或显示已删除的第八项。
- 来源：original

### Invariant I2-2：单击状态即唯一保存动作
- Rule：标签点击只开/关其菜单；状态项点击立即发送一次 PUT。成功后用 PUT 返回的完整 7 项重绘材料行；不得新增编辑/保存按钮，也不得复用 `#saveContactChangesBtn`。
- Applies to：材料事件委托、API 调用、DOM 重绘。
- Violation consequence：用户无法判断何时落库，或出现状态 select 与材料状态混合保存。
- 来源：original

### Invariant I2-3：成功静默，失败不伪装成功
- Rule：PUT 成功不调用 `showStatus`；请求完成前不改变标签视觉状态。PUT 失败时保留旧 DOM，恢复菜单按钮可用，并调用一次 `showStatus("材料状态保存失败: " + error.message, "error")`。
- Applies to：`saveExpertMaterialStatus()`。
- Violation consequence：过多成功提示；失败时界面显示未落库的新状态。
- 来源：original

### Invariant I2-4：三态视觉与语义一一对应
- Rule：`PENDING -> is-pending/无 mark/中性`；`PROVIDED -> is-provided/✓/绿色`；`DECLINED -> is-declined/⊘/灰色+删除线`。button 的 `aria-label` 必须同时含中文材料名和中文状态。
- Applies to：渲染函数、CSS、UI 测试。
- Violation consequence：置灰无法表达“暂不愿提供”，颜色与邮件变量排除语义不一致。
- 来源：original

### Invariant I2-5：材料加载失败不阻断既有详情
- Rule：`loadContactDetail()` 对材料 GET 单独 `.catch()`；失败时不渲染材料行、显示一次错误状态，但 detail、发件操作、邮件时间线和其他并行数据继续渲染。
- Applies to：详情 Promise.all、错误处理。
- Violation consequence：新增子资源故障导致整个专家联系页面不可用。
- 来源：K-expert-detail-two-panel-render-sites

### Invariant I2-6：事件不串入既有 action handler
- Rule：材料按钮使用 `data-material-action`，不得使用 `data-action`；`#contactHeadActions` 现有点击委托先识别材料按钮、`stopPropagation()` 并 return，再处理既有 `button[data-action]`。点击外部或 Escape 关闭所有材料菜单，不影响 sender binding 菜单。
- Applies to：顶栏事件注册、菜单关闭函数。
- Violation consequence：材料点击被 `handleContactAction()` 当未知 action，或菜单刚打开就被 document click 立即关闭。
- 来源：original；现状证据 `app.js:11739-11839`

### Invariant I2-7：静态资源缓存键三项一致
- Rule：修改 `app.js/styles.css` 时，`index.html` 的 `styles.css?v=`、`trust-reply-workbench.js?v=`、`app.js?v=` 必须同时改为精确值 `20260831-expert-material-tags`；4 个固定值测试同步更新。
- Applies to：index 与 4 个缓存测试。
- Violation consequence：浏览器继续使用旧 JS/CSS，或 Node 测试构建失败。
- 来源：K-frontend-cache-key-triad

### Invariant I2-8：未建联专家不出现材料 UI
- Rule：`showExpertDetail()` 的 `#contactHeadActions.hidden=true` 与 `innerHTML=""` 保持不变；材料请求只能从 `loadContactDetail(contactId)` 发出。
- Applies to：两个详情渲染入口。
- Violation consequence：无 contactId 时无法持久化却展示可编辑标签。
- 来源：K-expert-detail-two-panel-render-sites

---

## 样式契约

### S2-1：材料行布局
- 复用：`contact-head-status-row`（`styles.css:1373-1380`；响应式 `:4102-4107`、`:4168-4198`）与 `contact-head-label`（`:1390-1399`），不修改规则块。
- DOM 结构：材料行必须作为 `#contactHeadMoreRow` 的下一个兄弟节点，且自身不带 `hidden`；骨架逐字为：

```html
<div class="contact-head-status-row" id="expertMaterialRow" data-contact-id="${escapeHtml(contactId)}">
    <span class="contact-head-label">材料</span>
    <div class="expert-material-tags" aria-label="专家材料状态">
        <!-- 七个由 API 数组生成的 dropdown -->
    </div>
</div>
```

- 禁止项：inline style；新增“编辑材料”或“保存材料”按钮；修改共享 row/label CSS。

### S2-2：三态材料标签
- 新增：以下 CSS 必须逐字追加到 `styles.css`，不得增删属性或改值：

```css
.expert-material-tags {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    min-width: 0;
}

.expert-material-tag {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    height: 26px;
    padding: 0 10px;
    border: 1px solid var(--border);
    border-radius: 999px;
    background: var(--bg-main);
    color: var(--text-secondary);
    font-family: var(--font-body);
    font-size: 11px;
    font-weight: 600;
    line-height: 1;
    white-space: nowrap;
    cursor: pointer;
    transition: var(--transition);
}

.expert-material-tag:hover {
    border-color: var(--primary);
    color: var(--primary);
}

.expert-material-tag:focus-visible {
    border-color: var(--primary);
    outline: none;
    box-shadow: 0 0 0 2px rgba(var(--primary-rgb), 0.18);
}

.expert-material-tag.is-provided {
    border-color: var(--success-border);
    background: var(--success-bg);
    color: var(--success);
}

.expert-material-tag.is-declined {
    border-color: rgba(148, 163, 184, 0.35);
    background: rgba(148, 163, 184, 0.12);
    color: var(--text-muted);
    text-decoration: line-through;
    opacity: 0.72;
}

.expert-material-tag-mark {
    font-size: 10px;
    font-weight: 700;
}

.expert-material-tag.is-pending .expert-material-tag-mark {
    display: none;
}

.expert-material-tag-caret {
    color: currentColor;
    font-size: 9px;
    opacity: 0.7;
}
```

- DOM 结构：每个 tag button 逐字遵循下列层级；状态 class/mark/label/aria 文案只从固定三态映射取得：

```html
<button type="button" class="expert-material-tag is-provided"
        data-material-action="toggle" data-material-code="CV"
        aria-haspopup="true" aria-expanded="false" aria-label="简历：已提供，点击修改">
    <span class="expert-material-tag-mark" aria-hidden="true">✓</span>
    <span>简历</span>
    <span class="expert-material-tag-caret" aria-hidden="true">▾</span>
</button>
```

- 禁止项：inline style；未列出的 tag 子元素 class；用蓝色 active 代替绿色 provided；给 declined 去掉删除线。

### S2-3：状态菜单
- 复用：外层 `dropdown`（`styles.css:480-482`）；菜单 `dropdown-menu`（`:484-498`）；选项 `dropdown-item`（`:500-524`，含 hover/disabled）。不修改这些共享规则，也不新增菜单 CSS。
- DOM 结构：每个 tag 与菜单处在同一 `.dropdown` 内，逐字为：

```html
<span class="dropdown">
    <!-- S2-2 的 expert-material-tag -->
    <div class="dropdown-menu" hidden>
        <button type="button" class="dropdown-item" data-material-action="set-status" data-material-status="PENDING">待提供</button>
        <button type="button" class="dropdown-item" data-material-action="set-status" data-material-status="PROVIDED">✓ 已提供</button>
        <button type="button" class="dropdown-item" data-material-action="set-status" data-material-status="DECLINED">⊘ 暂不愿提供</button>
    </div>
</span>
```

- 禁止项：提示段落、图例、确认按钮、第三方 popover、inline 定位样式。

### S2-4：静态资源缓存键
- 复用/修改：`index.html:11,2098-2099` 三个现有 URL，只替换 `?v=` 后的值。
- 最终值逐字为：

```html
<link rel="stylesheet" href="styles.css?v=20260831-expert-material-tags">
<script src="trust-reply-workbench.js?v=20260831-expert-material-tags"></script>
<script src="app.js?v=20260831-expert-material-tags"></script>
```

- 禁止项：只 bump 两项；改资源顺序；给 URL 增加第二个 query 参数。

---

## 现状审计

### 专家详情 DOM 与读取路径

- `index.html:683-695`：右侧详情 panel 的固定头部含 `<div class="contact-head-actions" id="contactHeadActions" hidden></div>`；新 DOM 必须由 JS 动态渲染，不修改固定骨架。
- Contact-backed render：`loadContactDetail()`（`app.js:7532-7604`）并行请求详情、发信选项、documents、logs，然后在 `:7545-7591` 一次设置 `#contactHeadActions.innerHTML`。它是材料行唯一插入点。
- No-contact render：`showExpertDetail()`（`:7195-7209`）明确隐藏并清空 `#contactHeadActions`，必须保持（来源：K-expert-detail-two-panel-render-sites）。
- Existing writes：顶栏现有写操作集中在 `handleContactAction()` 与 `#contactHeadActions` click/change 委托（`:9106+`、`:11739-11848`）。材料写入必须使用独立 `data-material-action`，避免进入 `button[data-action]`。
- New reads：`loadContactDetail(contactId)` 增加 `GET /api/expert-contacts/{id}/materials`；仅它读取材料状态。
- New writes：材料状态菜单增加唯一 PUT；成功仅用响应重绘材料行。
- Interaction points：GET 响应 → 标签三态；菜单点击 → PUT → 响应 → 标签重绘；API 失败 → 保留既有详情。

### 前端样式盘点

- 可复用 class：
  - `.contact-head-actions` — `styles.css:1365-1371`，纵向三行容器，`gap:10px`。
  - `.contact-head-status-row` — `:1373-1380`，flex/wrap/gap 8px；响应式见 S2-1。
  - `.contact-head-label` — `:1390-1399`，`font-size:11px`、`font-weight:600`、`color:var(--text-muted)`、宽 85px。
  - `.dropdown/.dropdown-menu/.dropdown-item` — `:480-524`，见 S2-3。
- 设计基准 token：`--primary:#1e40af`、`--success:#059669`、`--success-bg:rgba(5,150,105,.08)`、`--success-border:rgba(5,150,105,.18)`、`--text-main:#1e293b`、`--text-muted:#94a3b8`、`--text-secondary:#475569`、`--border:rgba(15,23,42,.11)`、`--bg-main:#f5f7fb`、`--font-body:Inter/...`、`--transition:all .15s ease`（`styles.css:1-83`）。
- DOM 命名约定：详情头部新增节点使用 `contact-head-*` 行级名称、功能内部使用 `expert-material-*`；事件使用 data attribute + 容器委托，与现有结构一致。
- 不复用既有 `renderExpertTagEditor/updateExpertTagEditor`：该编辑器读写 ES 专家 tags、带增删语义和共享双挂载契约；材料项是固定目录并写 MySQL 子资源，复用会错误耦合两种 store（有意识拒绝：K-expert-tag-editor-shared-render-contract）。
- 改动前基线（`app.js:7571-7591`）：

```html
            <span class="contact-head-divider"></span>
            <button type="button" class="button contact-head-more-toggle" id="contactHeadMoreToggle"
                    data-action="toggle-contact-head-more" aria-expanded="${state.contactHeadExpanded === true}">⚙ 更多</button>
        </div>
        <div class="contact-head-status-row" id="contactHeadMoreRow" ${state.contactHeadExpanded === true ? "" : "hidden"}>
            <span class="contact-head-label">状态</span>
            <select id="operatorStatusSelect" data-contact-id="${contact.id}" data-original="${contact.operatorStatus || ""}" aria-label="专家状态">
                ${optionsFromArray(operatorStatusOptions, false, "请选择状态", contact.operatorStatus || "")}
            </select>
            <select id="indexLevelSelect" data-contact-id="${contact.id}" data-original="${contact.currentIndexLevel || ""}" aria-label="专家层级">
                ${optionsFromArray(indexLevelOptions, false, "请选择层级", contact.currentIndexLevel || "")}
            </select>
            <select id="autoReplySelect" data-contact-id="${contact.id}" data-original="${contact.autoReplyEnabled ? "auto" : "manual"}" aria-label="回复模式">
                <option value="auto" ${contact.autoReplyEnabled ? "selected" : ""}>自动回复</option>
                <option value="manual" ${!contact.autoReplyEnabled ? "selected" : ""}>人工回复</option>
            </select>
            <button class="button primary" id="saveContactChangesBtn" data-contact-id="${contact.id}" disabled>
                保存变更
            </button>
        </div>
```

- Existing CSS use sites：本计划不就地修改任何共享 class，故无需迁移其他使用点；新增 `.expert-material-*` 在全仓当前零命中。

### 静态缓存键与测试

- 当前三键均为 `20260827-v3-inbound-cancel-resolved`：`index.html:11,2098-2099`。
- 固定旧值的全部测试已由精确 grep 确认：
  1. `checkRepliesRelocation.test.js:11`
  2. `overlayAndDialogContrast.test.js:15`
  3. `manualReplySubjectPrefill.test.js:13`
  4. `batchSendTaskConsoleVisualFix.test.js:49-51`
- `trustReplyWorkbenchSharedMount.test.js` 只断言三键相等，不固定值，无需修改但必须运行（来源：K-frontend-cache-key-triad）。
- `pom.xml:186-216` 已把 `node --test src/test/js/*.test.js` 与 `node --check app.js` 绑定到 Maven test phase；最终必须跑全量 `mvn test`，不能只跑目标 JS 文件（来源：K-js-tests-run-via-exec-plugin）。
- Interaction point：静态文件修改 → index cache key → 4 个固定值测试和 1 个相等性测试。

---

## 实现方案

### Task 1：渲染材料行并容错加载（I2-1、I2-4、I2-5、I2-8；S2-1、S2-2、S2-3）

修改 `app.js`：

1. 在 `loadContactDetail` 前新增纯函数 `renderExpertMaterialRow(materials, contactId)`；三态映射固定为：
   - `PENDING -> { className:"is-pending", mark:"", label:"待提供" }`
   - `PROVIDED -> { className:"is-provided", mark:"✓", label:"已提供" }`
   - `DECLINED -> { className:"is-declined", mark:"⊘", label:"暂不愿提供" }`
2. 所有服务端 code/label/status 与 contactId 进入模板前调用 `escapeHtml()`；未知 status 不渲染为第四态，而是抛错让材料加载失败分支处理。
3. `loadContactDetail()` 的 Promise.all 加第五项材料 GET，并单独 catch：调用一次 `showStatus("材料状态加载失败: " + error.message, "error")` 后返回 null。
4. 在现有 `#contactHeadActions` 模板的 `#contactHeadMoreRow` 后插入 `${Array.isArray(materials) ? renderExpertMaterialRow(materials, contact.id) : ""}`。不得移动或重写 S2-1 的改动前基线。
5. `showExpertDetail()` 不请求材料、不渲染材料、不改现有清空逻辑。

### Task 2：即时保存与菜单关闭（I2-2、I2-3、I2-6；S2-2、S2-3）

修改 `app.js`：

1. 新增 `closeExpertMaterialMenus(exceptMenu = null)`：遍历 `#expertMaterialRow .dropdown-menu`，关闭非 except 菜单，并同步其前一个 tag 的 `aria-expanded=false`。
2. 新增 `toggleExpertMaterialMenu(button)`：计算目标菜单原 hidden 值，先关闭其他菜单，再设置目标 hidden/aria-expanded；不发 API。
3. 新增 `saveExpertMaterialStatus(button)`：从最近的 `#expertMaterialRow` 与 `.dropdown` 读取 contactId/materialCode/status；请求期间禁用该菜单 3 个 `.dropdown-item`；PUT 成功用完整数组替换 `#expertMaterialRow.outerHTML`；不调用 success `showStatus`；失败由调用处按 I2-3 提示，finally 恢复仍连接 DOM 的按钮。
4. 在 `#contactHeadActions` 现有 click listener 最前面处理 `button[data-material-action]`，调用 `event.stopPropagation()` 后分流 toggle/set-status 并 return；set-status 的 Promise 只在 catch 调一次错误 `showStatus`。
5. 在现有 document click 与 Escape listener 中追加 `closeExpertMaterialMenus()`；不得删除或合并 sender binding 的原逻辑。

### Task 3：逐字落地标签 CSS（I2-4；S2-1、S2-2、S2-3）

修改 `styles.css`：

1. 逐字追加 S2-2 的所有规则。
2. 不修改 S2-1/S2-3 的共享规则。
3. 不新增 media query：现有 `.contact-head-status-row` 在 768px 以下已把 label 与 tag 容器纵向排列，`.expert-material-tags` 自身负责换行。

### Task 4：缓存键原子 bump（I2-7；S2-4）

1. 修改 `index.html` 三个 URL 为 S2-4 精确值。
2. 同步修改 4 个固定值测试；不改 `trustReplyWorkbenchSharedMount.test.js`。
3. 全仓 grep 旧 key 必须零命中；新 key 在 index 三次并在固定测试按各自现有断言结构出现。

### Task 5：前端回归测试（I2-1 至 I2-8；S2-1 至 S2-4）

修改 `contactHeadLayout.test.js`：

1. 断言 `loadContactDetail` 请求 materials，失败 catch 不阻断详情，且材料行位于 `contactHeadMoreRow` 后。
2. VM 执行 `renderExpertMaterialRow`，传入固定 7 项，断言精确 7 个 tag、中文顺序、三态 class/mark/aria、无英文正文、无第八项、无编辑/保存按钮。
3. 用 stub API 执行 `saveExpertMaterialStatus`：断言 URL/method/body 精确；成功替换材料行且 `showStatus` 零调用；失败保留旧 outerHTML、按钮恢复并由 click handler 产生一次 error 提示。
4. 断言材料 DOM 无 `data-action` 与 `style=`；CSS 新规则与 S2-2 代码块逐字存在；共享 class 规则块未被改写。
5. 保留现有 sender binding、更多、状态保存测试全部通过。

---

## 变更文件清单

| # | 文件 | 操作 | 作用 |
|---|---|---|---|
| 1 | `src/main/resources/static/app.js` | 修改 | 材料加载、渲染、菜单、即时 PUT |
| 2 | `src/main/resources/static/styles.css` | 修改 | 三态标签视觉 |
| 3 | `src/main/resources/static/index.html` | 修改 | 三项静态缓存键同步 bump |
| 4 | `src/test/js/contactHeadLayout.test.js` | 修改 | 材料 UI/交互/回归测试 |
| 5 | `src/test/js/checkRepliesRelocation.test.js` | 修改 | 固定缓存键同步 |
| 6 | `src/test/js/overlayAndDialogContrast.test.js` | 修改 | 固定缓存键同步 |
| 7 | `src/test/js/manualReplySubjectPrefill.test.js` | 修改 | 固定缓存键同步 |
| 8 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改 | 固定缓存键同步 |

文件数：8。子系统数：1（静态管理前端）。不允许执行阶段修改表外文件。

---

## 验收标准

- I2-1：VM 渲染断言恰好 7 个 tag，顺序为简历/护照/学位/工作/出版/专利/研究；无第八项和英文正文。
- I2-2：点击一个状态只产生一次 PUT；成功后重绘完整 7 项；全仓新增 DOM 不含 `编辑材料` 或新增材料保存 button。
- I2-3：成功路径 `showStatus` 调用数为 0；失败路径为 1，旧状态不变且 menu button 恢复 enabled。
- I2-4：三态输出 class、mark、aria-label 精确断言；CSS 与 S2-2 逐字一致。
- I2-5：materials GET reject 后 `#contactHeadActions` 主行、`#contactDetail` 内容仍被填充；仅材料行缺席。
- I2-6：材料 HTML 不含 `data-action=`；click handler 源码/行为断言材料分支先于 generic action，外部 click/Escape 关闭菜单，sender binding 测试保持通过。
- I2-7：旧 cache key 全仓零命中；index 三 URL 都为 `20260831-expert-material-tags`；5 个 cache 相关测试通过。
- I2-8：`showExpertDetail` 仍清空并隐藏 `#contactHeadActions`，且函数源码不含 `/materials`。
- S2-1：DOM 为 `contact-head-status-row > contact-head-label + expert-material-tags`；无 inline style；共享规则 diff 为零。
- S2-2：新增 CSS 与契约代码块逐字一致，含 hover/focus/provided/declined/pending mark/caret 全状态。
- S2-3：菜单只用 `dropdown/dropdown-menu/dropdown-item`；无新菜单 CSS 和定位 inline style。
- S2-4：三项资源 URL 与契约逐字一致、顺序不变。
- Integration：GET 状态 → 标签；菜单 PUT → 响应重绘；刷新详情 → 同一状态；材料 API 失败 → 原详情仍可操作。
- 自动命令：
  1. `node --check src/main/resources/static/app.js`
  2. `node --test src/test/js/contactHeadLayout.test.js src/test/js/checkRepliesRelocation.test.js src/test/js/overlayAndDialogContrast.test.js src/test/js/manualReplySubjectPrefill.test.js src/test/js/batchSendTaskConsoleVisualFix.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js`
  3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test`

---

## 人工验收清单

### A2-1：默认标签位置与数量
- 前置条件：选择一个已有 contactId、7 项均 `PENDING` 的专家。
- 操作步骤：打开专家列表并点击该专家。
- 预期结果：专家联系主操作行下方显示“材料”及 7 个中性标签，顺序为简历、护照、学位、工作、出版、专利、研究；页面不出现英文正文、第八项、提示图例、“编辑材料”按钮、上传入口或批量操作入口。
- 覆盖：I2-1、S2-1、S2-2、Observable 1、Must NOT change 1/2/6

### A2-2：点击标签选择三态
- 前置条件：A2-1 页面。
- 操作步骤：1. 点击“简历”。2. 观察菜单。3. 点击“✓ 已提供”。4. 点击“工作”。5. 点击“⊘ 暂不愿提供”。
- 预期结果：菜单精确三项“待提供 / ✓ 已提供 / ⊘ 暂不愿提供”；简历变为绿色且有 `✓`；工作变灰、有删除线且有 `⊘`；页面没有额外保存按钮。
- 覆盖：I2-2、I2-4、S2-2、S2-3、Observable 2/3

### A2-3：保存成功静默且刷新不丢
- 前置条件：A2-2 已完成，状态栏当前隐藏。
- 操作步骤：1. 把护照设为“已提供”。2. 等待请求结束。3. 刷新浏览器或切换到另一专家再切回。
- 预期结果：步骤 2 不出现“保存成功/变更成功”提示；步骤 3 护照仍为绿色 `✓ 已提供`。
- 覆盖：I2-2、I2-3、Observable 2/3、Interaction PUT→GET

### A2-4：改回待提供
- 前置条件：简历当前为“已提供”。
- 操作步骤：点击简历，选择“待提供”，然后刷新详情。
- 预期结果：简历恢复中性、无 `✓/⊘`、无删除线；刷新后仍为待提供。
- 覆盖：I2-2、I2-4

### A2-5：保存失败不伪装成功
- 前置条件：浏览器开发者工具临时阻断材料 PUT；简历当前为待提供。
- 操作步骤：点击简历并选择“已提供”。
- 预期结果：简历仍显示待提供；只出现一次 `材料状态保存失败: <服务端错误>`；恢复网络后菜单仍可再次点击。
- 覆盖：I2-3、Must NOT change 4

### A2-6：材料 GET 失败不阻断详情
- 前置条件：只阻断 materials GET，不阻断详情 GET。
- 操作步骤：重新打开专家详情。
- 预期结果：发件账号、模板选择、发送邮件、更多、邮件时间线仍显示并可操作；材料行不显示；状态栏只出现一次材料加载失败。
- 覆盖：I2-5、Interaction GET failure→detail regression

### A2-7：既有顶栏操作回归
- 前置条件：选择一个已绑定发件账号的联系人。
- 操作步骤：1. 打开发件账号菜单再关闭。2. 切换模板并发送一封测试邮件。3. 展开“更多”，修改专家状态并点原“保存变更”。
- 预期结果：发件菜单正常开关；发送邮件沿原路径执行；状态保存仍显示原有“变更已保存”；材料状态没有被上述动作改变。
- 覆盖：I2-6、Must NOT change 3

### A2-8：未建联专家无材料行
- 前置条件：专家列表中存在没有 contactId 的搜索结果。
- 操作步骤：点击该专家。
- 预期结果：顶部专家联系操作区保持隐藏，不出现材料标签，也不发起 `/materials` 请求。
- 覆盖：I2-8、Must NOT change 5

### A2-9：窄屏换行目测
- 前置条件：打开有材料行的联系人详情。
- 操作步骤：把浏览器 viewport 调为 768px 宽，再调为 600px 宽。
- 预期结果：“材料”label 在标签组上方；7 个 pill 在容器内自然换行，无横向溢出；每个 pill 高 26px，间距 6px；declined 仍为灰色删除线，provided 仍为绿色。
- 覆盖：S2-1、S2-2、Observable 3

### A2-10：缓存更新生效
- 前置条件：浏览器曾访问旧版本页面，正常刷新而非清空缓存。
- 操作步骤：部署后直接刷新并打开联系人。
- 预期结果：Network 中 `styles.css`、`trust-reply-workbench.js`、`app.js` 的 query 均为 `v=20260831-expert-material-tags`；材料标签与点击菜单立即可用。
- 覆盖：I2-7、S2-4、Interaction 静态文件→浏览器加载
