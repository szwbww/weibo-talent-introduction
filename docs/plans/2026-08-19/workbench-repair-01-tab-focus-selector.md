# 01 可信化回复台页签焦点选择器修复

> 本计划由 create-p 生成。所有计数与全称判断均附 grep 回执（K-plan-quantified-claims-need-grep-receipts）。

## 需求描述

**Observable outcome**

1. 在收发件箱/AI 训练的可信化回复台里点击「摘要与事实」/「回复框架与整合」页签，或用 ←/→/Home/End 键在页签间移动，浏览器控制台不再出现 `Uncaught SyntaxError: Failed to execute 'querySelector' on 'Element'`。
2. 切换页签后，键盘焦点落在**目标页签按钮**上；继续按 Tab 从该页签往后走，而不是从文档顶部重新开始。

**What must NOT change**

1. 页签切换的可见行为：切到 `frame` 时 `data-page-panel="facts"` 带 `hidden`，反之亦然。
2. 页签与面板的 `id` 属性仍然逐实例唯一，`aria-controls` / `aria-labelledby` 关联不变。
3. 切换页签**不得**触发 `/bootstrap` 重新请求。
4. 事实 chip 拖拽排序后的焦点恢复行为（`host.querySelector` 的 fact-grip 分支，:1724）不变。
5. 任何视觉样式不变。

**Out of scope**

- `gripHintId`（:1571）同样以 `instanceId` 开头，但只用于 `id` / `aria-describedby` 属性、不进选择器，**本计划不动**。
- `makeId()`（:58-74）的 UUID 生成方式不改。
- `.pre` / `.trust-reply-assembly` 的 `max-height` 观感问题（styles.css:1730 / 7580）不在本计划。
- 组装正文分段（计划 02）、版本模型粒度（计划 03a/03b）。

## 关键不变量

### Invariant I-1: 禁止以 instanceId 开头的裸 id 选择器
- Rule: 工作台内任何 `querySelector` / `querySelectorAll` 的选择器**不得**为 `` `#${state.instanceId}...` `` 形式。`makeId()`（:58-74）返回 UUID v4，首字符在 `0-f` 上均匀分布，**10/16 = 62.5%** 的挂载首字符是数字；CSS 标识符不能以数字开头，`#2fb6073e-...` 会抛 `SyntaxError`。
- Applies to: `setActivePage`（trust-reply-workbench.js:1512-1515）——全仓唯一命中点，见现状审计 A-1。
- Violation consequence: `querySelector` 抛异常，`onClick`（:2014-2016）/ `onKeydown`（:1543）的处理链中断，焦点不移动，控制台出现未捕获错误。
- 来源: original

### Invariant I-2: id 属性与 ARIA 关联必须原样保留
- Rule: 修复只改**查询方式**，不得删除或改写 `id="${tabId(page.key)}"`（:1553）、`aria-controls="${panelId(page.key)}"`（:1553）、`id="${panelId(...)}"` / `aria-labelledby="${tabId(...)}"`（:1712、:1737）。`tabId` / `panelId` 两个函数（:1497-1503）必须保留且仍被这三行使用。
- Applies to: `renderPageTabs`（:1549-1556）、`renderShell` 内联模板（:1712）、`renderMarkup` 内联模板（:1737）。
- Violation consequence: `trustReplyWorkbenchSharedMount.test.js:1934-1936` 的「panel ids must be instance-unique」与 `aria-controls` 断言失败；屏幕阅读器的 tab↔panel 关联断裂。
- 来源: original

### Invariant I-3: 焦点必须真正落到目标元素
- Rule: `setActivePage(page, "tab")` 在 `render()` 之后必须取到**当前 DOM 中**的目标页签按钮并调用 `focus()`。`render()` 走 `host.innerHTML = renderMarkup()`（:1718）整体重绘，旧元素已被替换，因此必须在 `render()` **之后**查询。
- Applies to: `onClick` 的 `set-page` / `next-page` / `prev-page` 三个分支（:2014、:2015、:2016）；`onKeydown` 的 ←/→/Home/End 分支（:1543）。
- Violation consequence: roving tabindex（`tabindex="0"` / `"-1"`，:1553）失效，焦点掉回 `<body>`。
- 来源: original

### Invariant I-4: 选择器必须唯一命中
- Rule: 页签用 `[role="tab"][data-page="<page>"]`，面板用 `[data-page-panel="<page>"]`。**不得**只用 `[data-page="<page>"]`——`data-page=` 在本文件有 3 个输出点，其中 2 个是翻页按钮（:1559 `next-page`、:1561 `prev-page`），只有 :1553 带 `role="tab"`。
- Applies to: `setActivePage`（:1512-1515）。
- Violation consequence: 焦点错落到「下一页：回复框架与整合」按钮上，键盘导航语义错乱。
- 来源: original

## 样式契约

### S-1: 无样式变更
- 复用：不涉及。本计划**不新增、不修改、不删除任何 class、CSS 规则或 DOM 结构**。
- 新增：无。
- DOM 结构：`renderPageTabs`（:1553）与两处面板模板（:1712、:1737）产出的 HTML **逐字不变**。
- 禁止项：本计划中出现任何 `styles.css` 改动、任何新增/删除 class、任何 DOM 属性增删（`id` / `aria-*` / `data-*` / `class` / `tabindex`），均视为越界。
- 验证依据：变更文件清单中不含 `styles.css` 或 `index.html`；`git diff src/main/resources/static/trust-reply-workbench.js` 的改动必须全部落在 `setActivePage` 函数体内。

## 现状审计

### 前端模块：`src/main/resources/static/trust-reply-workbench.js`

**A-1. 裸 id 选择器命中点（全集）**

```
$ grep -rn 'querySelector([`"'"'"']#' src/main/resources/static/
src/main/resources/static/trust-reply-workbench.js:1514:            const element = host.querySelector ? host.querySelector(`#${id}`) : null;
src/main/resources/static/app.js:7335:    document.querySelector("#contactList .list-item.active")?.scrollIntoView({ block: "nearest" });
```

共 2 处。`app.js:7335` 是静态字符串 `#contactList`，不含 `instanceId`，合法，**不改**。唯一需修的是 `trust-reply-workbench.js:1514`。

**A-2. 当前 `setActivePage` 逐字现状（:1508-1516）**

```js
        // I-1/I-7: switching pages only toggles DOM visibility; business state
        // (requests, matrix, frame, versions, locks, assembly) is shared.
        function setActivePage(page, focusTarget) {
            if (page !== "facts" && page !== "frame") return;
            state.activePage = page;
            render();
            if (!focusTarget || state.destroyed) return;
            const id = focusTarget === "tab" ? tabId(page) : focusTarget === "panel" ? panelId(page) : null;
            if (!id) return;
            const element = host.querySelector ? host.querySelector(`#${id}`) : null;
            if (element && typeof element.focus === "function") element.focus();
        }
```

注意执行顺序：`render()` 在抛异常**之前**已经完成，因此页签视觉上切换成功，只有 `focus()` 丢失。

**A-3. `setActivePage` 的调用点（全集）**

```
$ grep -n "setActivePage" src/main/resources/static/trust-reply-workbench.js
1510:        function setActivePage(page, focusTarget) {
1543:            if (page) setActivePage(page, "tab");
2014:            if (action === "set-page") setActivePage(button.dataset.page, "tab");
2015:            if (action === "next-page") setActivePage(button.dataset.page || "frame", "tab");
2016:            if (action === "prev-page") setActivePage(button.dataset.page || "facts", "tab");
```

生产调用点 4 个，`focusTarget` **恒为 `"tab"`**；`"panel"` 分支当前无调用方，但保留（防御性，且 I-4 覆盖它）。

**A-4. 选择器候选属性的输出点**

```
$ grep -n 'data-page=' src/main/resources/static/trust-reply-workbench.js
1553:...role="tab" data-action="set-page" data-page="${page.key}" id="${tabId(page.key)}" aria-controls="${panelId(page.key)}"...
1559:                return `<button type="button" class="button primary" data-action="next-page" data-page="frame">下一页：回复框架与整合</button>`;
1561:            return `<button type="button" class="button secondary" data-action="prev-page" data-page="facts">上一页：摘要与事实</button>`;
```

3 个输出点，只有 :1553 带 `role="tab"` → `[role="tab"][data-page="..."]` 唯一（I-4）。

```
$ grep -n 'data-page-panel=' src/main/resources/static/trust-reply-workbench.js
1712:...data-page-panel="facts" id="${panelId("facts")}"...data-page-panel="frame" id="${panelId("frame")}"...
1737:...data-page-panel="facts" id="${panelId("facts")}"...data-page-panel="frame" id="${panelId("frame")}"...
```

2 行（:1712 `renderShell` / :1737 `renderMarkup`），**互斥渲染**，任一时刻 DOM 中每个 `page` 恰有 1 个面板。

**A-5. `instanceId` 派生 id 的其他使用点**

```
$ grep -n "instanceId" src/main/resources/static/trust-reply-workbench.js
183:            instanceId: makeId(),
1498:            return `${state.instanceId}-tab-${page}`;
1502:            return `${state.instanceId}-panel-${page}`;
1571:            const gripHintId = `${state.instanceId}-fact-grip-hint-${request.requestKey}`;
```

`gripHintId`（:1571）只在 :1582 `aria-describedby` 与 :1612 `id` 属性中使用，**不进任何选择器**，因此不抛异常，本计划不动（Out of scope）。

**A-6. 引入来源（确认非本轮拖拽排序改动引入）**

```
$ git log --oneline -S 'querySelector(`#${id}`)' -- src/main/resources/static/trust-reply-workbench.js
82a23b4 feat(fast-p): implement trust-reply-configurable-workbench-03
$ git log -1 --format='%h %ad %s' --date=short 82a23b4
82a23b4 2026-08-05 feat(fast-p): implement trust-reply-configurable-workbench-03
```

### 测试现状：为什么全绿却漏掉

**A-7. DOM stub 不校验选择器语法**（`src/test/js/trustReplyWorkbench.test.js:165-173`）

```js
    querySelector(selector) {
        const match = selector.match(/^\[data-fact-id="(\d+)"\] \[data-role="fact-grip"\]$/);
        if (match && this._innerHTML.includes(`data-fact-id="${match[1]}"`)) {
            const grip = new FakeElement(this.ownerDocument);
            grip.focus = () => { this.ownerDocument.lastFocusedFactId = match[1]; };
            return grip;
        }
        return null;
    }
```

只正则匹配 fact-grip 一个选择器，其余一律 `return null`，**从不校验语法、永不抛异常**。项目无 jsdom（`ls package.json` 无此文件；pom 的 JS 测试执行器是 `node --test`）。这正是 `K-dom-stub-tests-hide-dangling-refs`（domain: frontend, severity P1）描述的失明模式。

**A-8. 现有页签测试确实走了这条路径但抓不到**

`src/test/js/trustReplyWorkbenchSharedMount.test.js`：
- :1918-1951「两页签渲染与切换」——`click(host, "set-page", ..., "frame")` → `setActivePage(page, "tab")`
- :1953-1990「箭头/Home/End 导航」——:1975 只 stub 了 `querySelectorAll`，`querySelector` 仍是 FakeElement 的实现

### Interaction points

| # | 写入侧 | 读取侧 | 本计划影响 |
|---|---|---|---|
| IP-1 | `renderPageTabs`(:1553) 输出 `role="tab"` + `data-page` | `setActivePage`(:1514) 查询目标页签 | 查询方式从 id 改为属性，写入侧必须保持 `role="tab"` 与 `data-page` 不变（I-2/I-4） |
| IP-2 | `renderMarkup`(:1737) / `renderShell`(:1712) 输出 `data-page-panel` | `setActivePage` 的 `"panel"` 分支 | 同上；该分支当前无调用方但须正确 |
| IP-3 | `onKeydown`(:1543) 与 `onClick`(:2014-2016) 传入 `focusTarget="tab"` | `setActivePage` 的焦点逻辑 | I-3：两个入口都必须真正获得焦点 |

## 实现方案

### T1 — 改写 `setActivePage` 的元素查询方式（I-1 / I-3 / I-4 / S-1）

文件：`src/main/resources/static/trust-reply-workbench.js`

把 :1510-1516 的函数体替换为以下**逐字**内容（仅函数体内改动，上方 :1508-1509 注释保留）：

```js
        function setActivePage(page, focusTarget) {
            if (page !== "facts" && page !== "frame") return;
            state.activePage = page;
            render();
            if (!focusTarget || state.destroyed) return;
            // I-1: state.instanceId is a UUID v4 — 62.5% of mounts start with a
            // digit, and a CSS identifier may not start with a digit, so
            // `#${tabId(page)}` throws SyntaxError. Query by the stable
            // role/data attributes instead; the id attributes stay on the
            // elements for aria-controls / aria-labelledby (I-2).
            const selector = focusTarget === "tab"
                ? `[role="tab"][data-page="${page}"]`
                : focusTarget === "panel"
                    ? `[data-page-panel="${page}"]`
                    : null;
            if (!selector) return;
            const element = host.querySelector ? host.querySelector(selector) : null;
            if (element && typeof element.focus === "function") element.focus();
        }
```

约束：
- `page` 已在函数首行被白名单收窄为 `"facts"` / `"frame"`（:1511），因此插值进选择器无注入风险；**不得**移除该白名单判断。
- `tabId` / `panelId`（:1497-1503）**保留**，它们仍被 :1553 / :1712 / :1737 使用（I-2）。不得因"看似无用"删除。
- 不改动本函数以外的任何一行。

### T2 — 补测试（覆盖 I-1 / I-3 / I-4）

文件：`src/test/js/trustReplyWorkbenchSharedMount.test.js`

新增一个 `it(...)` 用例，放在「navigates the two tabs with arrow and home/end keys」（:1953）之后：

1. **语法真实性断言（I-1，防 A-7 失明）**：在挂载后，用真实的 `CSS`-无关方式验证——把 `host.querySelector` 临时替换为一个记录 selector 的 spy，断言 `setActivePage` 传入的 selector **不以 `#` 开头**，且匹配 `/^\[role="tab"\]\[data-page="(facts|frame)"\]$/`。
2. **源文本存在性断言（I-1，K-dom-stub-tests-hide-dangling-refs 要求的补充）**：`fs.readFileSync` 读取 `src/main/resources/static/trust-reply-workbench.js`，断言全文**不再包含** 字符串 `` querySelector(`#${ ``，且包含 `` [role="tab"][data-page=" ``。
3. **焦点断言（I-3）**：spy 返回一个带 `focus()` 的假元素，点击 `set-page` 后断言 `focus()` 被调用恰好 1 次。
4. **唯一性断言（I-4）**：断言 selector 含 `[role="tab"]` 前缀（否则会命中 :1559/:1561 的翻页按钮）。

同时**保留**既有的 :1918-1951 与 :1953-1990 两个用例不改——它们覆盖 must-NOT-change 第 1、3 条。

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/resources/static/trust-reply-workbench.js` | T1：仅 `setActivePage`（:1510-1516）函数体 |
| 2 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | T2：新增 1 个用例，不改既有用例 |

合计 **2** 个文件（上限 10）。子系统 **1** 个（前端工作台组件，上限 2）。

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。JS 测试由 `exec-maven-plugin` 的 `node-test` execution 在 `test` 阶段执行（`pom.xml:186-202`，`bash -lc "node --test src/test/js/*.test.js"`），`skipNodeTests` 在 `<properties>` 中未声明（`grep -n skipNodeTests pom.xml` 仅命中 :201/:216/:231 三处 `<skip>`），因此默认不跳过。

```bash
# 本计划相关测试（快速迭代用，实测可用；node v22.23.2）
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js

# 全部前端测试
node --test src/test/js/*.test.js

# 语法检查（pom 的 node-check-app / node-check-task-modal-runtime 同款）
node --check src/main/resources/static/trust-reply-workbench.js

# 全量测试（回归门禁，含上面的 node --test）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：
- `node --test`：退出码 0，输出含 `# fail 0`。
- `mvn test`：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`（Kotlin 侧）且 node-test execution 未报错。
- `mvn clean package`：退出码 0，`BUILD SUCCESS`。
- `git diff --check`：无输出。

来源：`CLAUDE.md` 项目元信息的 `test_command` / `build_command`；`pom.xml:186-232` 的 exec-maven-plugin；`node --test src/test/js/trustReplyWorkbench.test.js` 于 2026-08-19 实测通过（`# tests 16 / # pass 16 / # fail 0`）。

## 验收标准

- **I-1**：`grep -n 'querySelector(`#' src/main/resources/static/trust-reply-workbench.js` **无输出**；`grep -c 'instanceId' src/main/resources/static/trust-reply-workbench.js` 仍为 4（:183/1498/1502/1571，证明未误删 tabId/panelId/gripHintId）。T2 的源文本断言通过。
- **I-2**：`grep -n 'id="${tabId(' src/main/resources/static/trust-reply-workbench.js` 命中 :1553。
  `grep -o "panelId(" src/main/resources/static/trust-reply-workbench.js | wc -l` **改动前实测 = 7**
  （:1501 函数定义、:1512 被 T1 删除的用法、:1553 `aria-controls`、:1712 ×2、:1737 ×2），
  **改动后必须 = 6**（恰少 :1512 那一处，其余 6 处一个都不能少）。
  `grep -o "tabId(" src/main/resources/static/trust-reply-workbench.js | wc -l` 同理：改动前 7 → 改动后 6。
  既有用例 sharedMount:1934-1936 仍通过。
- **I-3**：T2 的 focus spy 断言被调用恰好 1 次。
- **I-4**：T2 断言 selector 匹配 `/^\[role="tab"\]\[data-page="(facts|frame)"\]$/`。
- **S-1**：`git diff --stat` 只含变更文件清单里的 2 个文件；`git diff src/main/resources/static/trust-reply-workbench.js` 的 hunk 全部落在 `setActivePage` 函数体内（行号区间 1508-1530）；无 `styles.css` / `index.html` 改动。
- **回归**：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 点击页签不再报错且焦点正确
- 前置条件：以 admin 登录控制台 →「收发件箱」→ 打开任意一封已进入可信化回复台的来信（回复台面板可见「摘要与事实 / 回复框架与整合」两个页签）。**先打开浏览器开发者工具的 Console 面板并清空**。
- 操作步骤：
  1. 刷新页面（确保重新挂载，`instanceId` 重新生成）。
  2. 点击「回复框架与整合」页签。
  3. 点击「摘要与事实」页签。
  4. 重复步骤 1-3 共 **5 次**（`instanceId` 首字符为数字的概率是 62.5%，5 次全部躲开的概率约 0.9%）。
- 预期结果：Console 中**零条**红色 `Uncaught SyntaxError: Failed to execute 'querySelector'` 记录。每次点击后，被点击的那个页签按钮出现浏览器默认焦点框（或按一次 Tab 键，焦点落到该页签**之后**的元素，而不是页面顶部的「Talent Console」导航）。
- 覆盖：I-1、I-3、需求描述 observable outcome 1 与 2

### A-2: 键盘方向键导航
- 前置条件：同 A-1，且焦点已在「摘要与事实」页签按钮上（用 Tab 键移动过去）。
- 操作步骤：
  1. 按 `→`。
  2. 按 `←`。
  3. 按 `End`。
  4. 按 `Home`。
- 预期结果：依次切换到「回复框架与整合」→「摘要与事实」→「回复框架与整合」→「摘要与事实」；每次切换后焦点仍在**当前选中的页签按钮**上（连续按 `→` `←` 能来回切，不需要重新 Tab）。Console 无报错。
- 覆盖：I-1、I-3、IP-3

### A-3: 回归 — 切换页签不重新加载数据（must-NOT-change 第 3 条）
- 前置条件：同 A-1，打开开发者工具 Network 面板并清空，筛选 `bootstrap`。
- 操作步骤：在两个页签之间来回点击 6 次。
- 预期结果：Network 面板中 `/api/trust-reply/workbench/bootstrap` 的请求数为 **0**。已在「摘要与事实」页填好的内容（展开的卡片、已选事实）保持原样。
- 覆盖：must-NOT-change 第 3 条、IP-1

### A-4: 回归 — 事实拖拽排序后的焦点恢复（must-NOT-change 第 4 条）
- 前置条件：进入「摘要与事实」页，找到一条绑定了 **≥2 个事实** 的摘要卡片。
- 操作步骤：
  1. 用 Tab 键把焦点移到第一个事实 chip 的 `⋮⋮` 拖拽把手上。
  2. 按 `→`。
  3. 在确认框中点「确定」。
- 预期结果：该事实与后一个事实交换位置；交换完成后焦点**仍在同一个事实的 `⋮⋮` 把手上**（可以连续按 `→` 继续移动）。Console 无报错。
- 覆盖：must-NOT-change 第 4 条

### A-5: 回归 — 视觉无变化（S-1）
- 前置条件：修改前对回复台页签区域截图一张作为基线（或用 `git stash` 对比）。
- 操作步骤：修改后打开同一个回复台，对比页签区域。
- 预期结果：页签的底色、选中态下划线/高亮、步骤序号圆点、字号、两个页签的间距**逐项与基线一致**；面板内容区无位移。
- 覆盖：S-1、must-NOT-change 第 5 条
