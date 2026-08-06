# 专家画像缺失不再是错误：`/api/experts/profile` 契约收敛与标签区降级

> 用 create-p skill 编写。独立计划，无前置依赖，可独立部署与验证。
> 触发缺陷：收发件箱点「处理」测试专家 `TEST-LUKAI-18014905480` 的来信，整个详情面板不渲染，控制台仅留
> `Uncaught (in promise) Error: 404 NOT_FOUND "Expert not found: TEST-LUKAI-18014905480"`（`app.js:1447`）。
> 姊妹计划：`inbound-message-id-vendor-prefix.md`（入站 Message-ID 供应商前缀匹配）。两者无依赖关系，文件零重叠。

## 需求描述

**可观察结果**

1. 打开一封关联专家在 ES 中**无画像文档**的来信详情，详情面板**完整渲染**（元数据、正文、附件、回复工作台全部可见），仅「专家标签」区域显示不可用提示，控制台无 uncaught 报错。
2. 专家详情页 / 联系人详情页遇到同样情况时，页面其余部分照常渲染，标签区显示同一提示。
3. ES 服务本身故障（连接超时、5xx、解析异常）时，页面**弹出错误状态条**，而不是静默显示成"该专家无标签"。
4. 上述任一异常场景下，点击列表里的「处理」/「查看」按钮，失败信息通过页面顶部状态条呈现，不再只进控制台。

**必须不变（must NOT change）**

- ES 中**存在**画像文档时，标签区的展示、增删标签行为、`level` 取值逐字不变。
- `POST /api/experts/tags/add`、`POST /api/experts/tags/remove` 的入参、返回结构与"专家不存在时返回 `TagMutationResult(success=false, message="Expert not found: ...")`"的软失败语义**完全不变**。
- `GET /api/experts/template-variables`（`ExpertIndexController.kt:285-298`）的 404 行为**不变** —— 它是写前置校验，缺画像必须硬失败。
- `ExpertSearchService.findByOrcidId()` 的查询逻辑、`sourceFields()`、`toExpertProfile()` 零改动。
- `AiReplyContextService.loadProfile()`（`:58-81`）的 `EXPERT_PROFILE_NOT_FOUND` 语义与 APPLICATION→CANDIDATE 回退**零改动**（来源: K-ai-reply-profile-absence-warning, hit_count 14）。
- `renderExpertTagEditor()` 在有画像时产出的 DOM 与 class 逐字不变。
- `styles.css` **零改动**（本计划只复用既有 class）。

**不在范围（out of scope）**

- 给 `/api/experts/profile` 增加 APPLICATION→CANDIDATE 层回退 —— 见 I-2，会造成读写不同源，明确拒绝。
- 修复 `ManualInitialOutreachService.kt:587` 与 `ManualReplySendAttemptService.kt:35` 硬编码的 `@weibo.com` Message-ID 域名（应随发件账号域名）—— 本次审计发现的既有缺陷，与本计划无因果关系，单独立项。
- 退订相关的任何改动（`List-Unsubscribe` / `List-Unsubscribe-Post`）—— 见本文末「关联缺陷移交」。
- 入站 Message-ID 供应商前缀匹配 —— 姊妹计划。
- `expert_contact.current_index_level` 与 ES 实际层级不一致的数据订正 —— 数据问题，非代码问题。

---

## 关键不变量

### Invariant I-1: 画像缺失是正常结果，查询故障是错误，两者不得合流

- Rule: `getExpertProfile()` 中 `expertSearchService.findByOrcidId()` 返回 `null` 时，返回 **HTTP 200** 且 `found = false`、`tags = emptyList()`；`findByOrcidId()` **抛出异常时必须让异常继续向上传播**（由 `GlobalExceptionHandler` 处理），禁止用 `try/catch` 吞掉后伪装成 `found = false`。前端对应地：`found === false` 走降级渲染，`api()` 抛错走 `showStatus(..., "error")`。
- Applies to: `ExpertIndexController.getExpertProfile()`；`app.js` `fetchExpertTagsFromEs()`。
- Violation consequence: 若把异常也降级为 `found=false`，ES 宕机时运营看到的是"该专家无标签"，会据此重复添加标签并做出错误判断，且根因被静音 —— 这正是 K-ai-reply-profile-absence-warning 记录过的同型错误的镜像面（该条要求"未找到"与"异常"统一报警；此处是只读展示场景，要求两者**可区分**，语义不冲突：那条管的是给 LLM 的上下文可用性，本条管的是给人的诊断信息）。
- 来源: original（对照 K-ai-reply-profile-absence-warning）

### Invariant I-2: 标签的读层与写层必须同源，禁止只在读路径加层回退

- Rule: 标签编辑器容器上的 `data-level` 是 `tags/add` 与 `tags/remove` 的**唯一** level 来源（`app.js:8344` `editor.dataset.level`）。`fetchExpertTagsFromEs()` 查询用的 level 必须与该 `data-level` 同值同源。**禁止**在读路径加入 APPLICATION→CANDIDATE（或任何层）回退。
- Applies to: `fetchExpertTagsFromEs()`、`renderExpertTagEditor()`、`renderMailboxExpertTagEditor()`、`mutateExpertTag()`。
- Violation consequence: 显示层回退到 CANDIDATE、写入层仍是 APPLICATION，会出现"看到的标签删不掉""加的标签看不见"，且 `mutateExpertTag()` 末尾的 `refreshExpertTagsFromEs()` 会读到与刚写入不同层的数据，UI 永久不一致。
- 来源: original

### Invariant I-3: `found === false` 时不得渲染任何可触发写操作的控件

- Rule: 画像缺失时渲染的标签区**不得包含** `data-action="expert-add-tag-open"` 或 `data-action="expert-remove-tag"` 的元素。此外 `handleContactAction()` 中 `expert-add-tag-open` 分支（`app.js:8341-8347`）必须在 `found === false` 时 `showStatus` 后 return，**不得**打开 `#actionDialog`。
- Applies to: `renderExpertTagEditor()`；`handleContactAction()` 的 `expert-add-tag-open` 分支。
- Violation consequence: 用户点进对话框、选完标签、提交后才拿到 `success=false`，是无效交互；且 `#actionDialog` 是多业务共用弹窗，异常路径下的 setup/cleanup 不成对会污染后续业务（来源: K-shared-action-dialog-cleanup, severity P1）。
- 来源: K-shared-action-dialog-cleanup

### Invariant I-4: 前端对 `found` 字段缺失必须向后兼容

- Rule: 前端判定一律写作 `found === false` 走降级；`undefined` / `null` 视同"画像存在"，按既有逻辑渲染。**禁止**写成 `if (!found)`。
- Applies to: `fetchExpertTagsFromEs()` 的全部 4 个消费点。
- Violation consequence: 前后端分别部署或浏览器缓存旧 `app.js` / 新 `app.js` 与旧后端组合时，`!undefined === true` 会让**所有**专家的标签区被误判为不可用。
- 来源: original

### Invariant I-5: 详情面板渲染不得被装饰性数据的失败中断

- Rule: `showUnmatchedDetail()`（`app.js:9296`）中标签数据的获取失败**不得**阻断面板其余部分渲染 —— 标签区退化为提示文案，面板照常 `panel.hidden = false` 并写入 `innerHTML`。同理，`#mailboxList`（`app.js:11471`）与 `#monitoringActivityTable`（`app.js:10493`）两个 `async` 事件监听器必须以 `.catch(...)` 收口，写法与既有 `#unmatchedDetailPanel` 监听器（`app.js:11013`）一致。
- Applies to: `showUnmatchedDetail()`、`showMailDetail()` 中的只读详情分支（`app.js:8766` 一带）、上述两个监听器。
- Violation consequence: 当前生产状态即为违反 —— 一个装饰性标签查询把整个来信处理面板拖垮，且错误只进控制台，运营看到的是"点了没反应"。
- 来源: original

---

## 样式契约

> 本计划触及 `src/main/resources/static/app.js`，故必填本节。
> **总原则：本计划只复用既有 class，`styles.css` 零改动，不新增任何 class，不使用 inline style。**

### S-1: 标签区「画像不可用」态

- **复用**（全部来自 Step 1b-fe 盘点，不得自造近似样式）：
  - 容器：`.detail-section` + `.expert-tag-editor` —— 与现状一致，沿用 `styles.css:4483`（`.expert-tag-editor .inbound-tag-editor-chips`）、`styles.css:2047`、`styles.css:2098`、`styles.css:2161` 的既有作用域规则。
  - 标题行：`.inbound-tag-editor-head` + `<h3>` —— 与现状一致。
  - 提示文案：`.muted`（`styles.css:2760-2763`，实值 `color: var(--text-muted); font-size: 12px;`）—— 与现状"暂无标签"占位所用 class **完全相同**（`app.js:3957`）。
- **新增**：无。本契约不引入任何新 CSS 规则，`styles.css` 不在变更文件清单内。
- **DOM 结构**（`found === false` 时 `renderExpertTagEditor()` 的产出，逐字）：

```html
<div class="detail-section expert-tag-editor" id="${editorId}" data-orcid="${orcidId}" data-level="${level}" data-profile-missing="true">
    <div class="inbound-tag-editor-head">
        <h3>专家标签</h3>
    </div>
    <div class="inbound-tag-editor-chips"><span class="muted">该专家在 ES 中无画像文档，标签功能不可用</span></div>
</div>
```

  与现状（`app.js:3958-3969`）的差异**仅两处**：① 删除整个 `<div class="inbound-tag-editor-actions">…</div>` 块（含「+ 添加标签」按钮，由 I-3 要求）；② `.inbound-tag-editor-chips` 内容替换为上述 `.muted` 提示。容器 class、`id`、`data-orcid`、`data-level` **逐字保留**（`updateExpertTagEditor()` 依赖 `editor.dataset.orcid` 做匹配，`app.js:4055`）。新增 `data-profile-missing="true"` 仅供测试断言与后续 grep，不参与样式。

- **禁止项**：inline style；新增 class；修改 `.muted` / `.expert-tag-editor` / `.inbound-tag-editor-head` / `.inbound-tag-editor-chips` 任一既有规则块；用 `disabled` 属性保留按钮代替删除按钮（违反 I-3，`disabled` 按钮仍在 DOM 中会被 `grep data-action` 命中）。

### S-2: 有画像时的标签区（回归基线）

- **复用**：不变。
- **新增**：无。
- **DOM 结构**：`app.js:3958-3969` 现状**逐字不变**，作为回归比对基线：

```html
<div class="detail-section expert-tag-editor" id="${escapeHtml(editorId)}" data-orcid="${escapeHtml(orcidId)}" data-level="${escapeHtml(level)}">
    <div class="inbound-tag-editor-head">
        <h3>专家标签</h3>
        <div class="inbound-tag-editor-actions">
            <button type="button" class="button primary small" data-action="expert-add-tag-open">+ 添加标签</button>
        </div>
    </div>
    <div class="inbound-tag-editor-chips">${chips}</div>
</div>
```

- **禁止项**：借本次改动顺手调整有画像分支的结构、class 或文案。

---

## 现状审计

### CANDIDATE / APPLICATION ES 索引（只读路径）

- Schema/mapping：`tags` 为既有字段，本计划**不新增任何 ES 字段**，不触及 mapping、`ExpertProfile.kt`、`sourceFields()`、`toExpertProfile()`（对照 K-expert-profile-source-sync 的四处同步要求 —— 本计划全部不适用）。
- 读路径（`findByOrcidId`，grep 确认共 3 个调用方）：
  1. `ExpertIndexController.getExpertProfile()`（`:306`）—— 只读 `tags`。**本计划唯一修改点。**
  2. `ExpertIndexController.getTemplateVariables()`（`:292`）—— 写前置校验，404 语义保留，不动。
  3. `AiReplyContextService.loadProfile()`（`:66-71`）—— 已 catch 异常并有层回退，不动。
- 写路径（`tags` 字段）：`ExpertIndexController.addTag()`（`:252`）、`removeTag()`（`:266`）—— 两者均已是 `TagMutationResult(success=false)` 软失败，本计划不动。

### `ExpertProfileTagsResponse`（`ExpertIndexController.kt:347`）

- 定义与构造：**仅** `ExpertIndexController.kt`（`:304` 返回类型、`:311` 构造、`:347` 定义）。grep 全仓确认无第四处命中。
- 运行时消费者：**唯一** —— `app.js:4021` `fetchExpertTagsFromEs()`，只读 `profile.tags`。
- 非运行时消费者：`ExpertIndexControllerTest.kt:361` 直接调用 controller 方法。
- 无 Feign / client / 其他服务端调用方。
- **结论：新增 `found` 字段安全**，现有前端会忽略未知字段（`JSON.parse` 后仅取 `.tags`）。

### `fetchExpertTagsFromEs()` 消费点（`app.js`，grep `fetchExpertTagsFromEs` 全集）

| # | 行 | 函数 | 场景 | 现状是否有 catch |
|---|---|---|---|---|
| 1 | 6585 | `showExpertDetail()` | ES 专家列表 → 专家详情 | ❌ 无 |
| 2 | 6952 | 联系人详情渲染 | 专家联系 → 联系人详情 | ❌ 无 |
| 3 | 8347 | `handleContactAction()` `expert-add-tag-open` | 点「+ 添加标签」 | ❌ 无 |
| 4 | 8766 | `showMailDetail()` 只读详情分支 | 收发件箱 → 查看原文 | ❌ 无 |
| 5 | 9325 | `showUnmatchedDetail()` | 收发件箱 → 处理（**报错现场**） | ❌ 无 |

> 注：`refreshExpertTagsFromEs()`（`:4025`）是 `fetchExpertTagsFromEs` 的同名转发，被 `mutateExpertTag()`（`:4046`）调用；`mutateExpertTag` 的调用方 `:8357` / `:8376` 已在 `try/catch` 内，本计划不改其行为。**消费点共 5 处**（此前口径说 4 处，遗漏 8766，已更正）。

### 事件监听器 catch 覆盖（grep `handleUnmatchedAction` 全集）

| 容器 | 行 | 现状 |
|---|---|---|
| `#unmatchedDetailPanel` | 11013 | ✅ `.catch((error) => showStatus(error.message, "error"))` —— **本计划的写法基准** |
| `#mailboxList` | 11489 | ❌ `await handleUnmatchedAction(target)` 裸 await |
| `#monitoringActivityTable` | 10499 | ❌ `await showUnmatchedDetail(...)` 裸 await |

### 前端样式盘点（Step 1b-fe）

- **可复用 class**
  - `.muted` — `styles.css:2760-2763` — `color: var(--text-muted); font-size: 12px;`。现状已用于标签区"暂无标签"占位（`app.js:3957`）。
  - `.detail-section` + `.expert-tag-editor` — 容器，作用域规则见 `styles.css:2047` / `2098` / `2161` / `4483`。
  - `.inbound-tag-editor-head`、`.inbound-tag-editor-chips`（`styles.css:4483`：`display:flex; flex-wrap:wrap; gap:8px; align-items:center;`）。
  - `.expert-tag`、`.expert-tag-remove`（`styles.css:4489-4500`）、`.button primary small` — 仅有画像分支使用，本计划不动。
- **设计基准 token 实值**：提示文案颜色 `var(--text-muted)`、字号 `12px`、chips 容器间距 `gap: 8px`。本计划不引入任何新 token。
- **DOM 结构约定**：标签编辑器由 `renderExpertTagEditor(tags, orcidId, level, editorId)`（`app.js:3951`）单点产出；`renderMailboxExpertTagEditor()`（`app.js:4449`）是其包装，负责从 `expertRef` 解析 `orcidId` / `level` 后转发。**两个渲染入口，一个真实产出点** —— 改 `renderExpertTagEditor` 即全覆盖（对照 K-mail-body-display-sites 的"多展示点须逐点核对"，本例经 grep 确认收敛于单点，不适用该条的分散风险）。
- **改动前基线**：见 S-2 逐字 DOM。

### Interaction points

1. **`getExpertProfile` 响应形态 × `fetchExpertTagsFromEs` 解析** —— 后端加 `found`，前端必须按 I-4 容错读取。跨模块（expert 后端 ↔ 前端）。
2. **`fetchExpertTagsFromEs` 返回类型变更 × 5 个消费点** —— 由数组变为对象，**每个消费点都必须改**，漏一处即 `tags` 变 `undefined`，标签区渲染成空。
3. **`fetchExpertTagsFromEs` 返回类型变更 × `mutateExpertTag()`（`:4046`）** —— `refreshExpertTagsFromEs()` 的返回值被用于 `.includes(tag)` / `.filter(...)`，若只改 fetch 不改这里会抛 `TypeError`。**这是本计划最易遗漏的一处。**
4. **`fetchExpertTagsFromEs` 返回类型变更 × 既有 JS 测试** —— `src/test/js/expertTagBatchFix.test.js:19-25` 的 `createTagFetchSandbox()` 以 `api: async () => ({ tags: [] })` 打桩并断言返回值，改契约必须同步该测试。
5. **`renderExpertTagEditor` 产出 × `updateExpertTagEditor()`（`:4053`）** —— 后者用 `editor.dataset.orcid !== orcidId` 匹配后 `outerHTML` 替换；S-1 保留 `data-orcid` 即可，无需改动。

---

## 实现方案

### 阶段 1：后端契约收敛（子系统 ①）

**任务 1.1 — `getExpertProfile` 去 404**（I-1）

文件：`src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt`

- `:300-315` 移除 `?: throw ResponseStatusException(NOT_FOUND, ...)`，改为 `val profile = expertSearchService.findByOrcidId(orcidId, level)`，返回 `ExpertProfileTagsResponse(orcidId = orcidId, found = profile != null, tags = profile?.tags.orEmpty())`。
- 注意 `orcidId` 字段的取值来源由 `profile.orcidId` 改为**入参** `orcidId`（画像不存在时无 profile 可取）。有画像时两者等值（`findByOrcidId` 按 term 精确查询）。
- `require(orcidId.isNotBlank())` 保留（`:305`）—— 空入参仍是 400。
- **不加** `try/catch`：ES 异常继续上抛（I-1）。

**任务 1.2 — `ExpertProfileTagsResponse` 加字段**（I-1）

同文件 `:347`，新增 `val found: Boolean`。放在 `orcidId` 之后、`tags` 之前。**不给默认值** —— 唯一构造点显式传值，避免遗漏。

**任务 1.3 — 后端测试**（I-1）

文件：`src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt`

- 修改既有 `getExpertProfile returns tags from searchService findByOrcidId`（`:361`）：补断言 `response.found == true`。
- 新增 `getExpertProfile returns found=false with empty tags when profile absent`：stub `findByOrcidId` 返回 `null`，断言 **不抛异常**、`found == false`、`tags.isEmpty()`、`orcidId` 等于入参。
- 新增 `getExpertProfile propagates search exception`：stub `findByOrcidId` 抛 `RuntimeException`，断言异常向上传播（`assertThrows`）。

### 阶段 2：前端降级渲染（子系统 ②）

**任务 2.1 — `fetchExpertTagsFromEs` 改契约**（I-1, I-4）

文件：`src/main/resources/static/app.js`

- `:4018-4023` 返回 `{ found: profile?.found !== false, tags: profile?.tags || [] }`。
- `orcidId` 为空的早退分支（`:4019` `if (!orcidId) return [];`）改为 `return { found: false, tags: [] }` —— 无 ORCID 与无画像在展示上同义。
- **不加 `try/catch`** —— 异常继续上抛，由各消费点按 I-5 处理（I-1）。

**任务 2.2 — `refreshExpertTagsFromEs` 与 `mutateExpertTag` 适配**（Interaction point 3）

- `:4025-4027` `refreshExpertTagsFromEs` 保持转发，返回对象。
- `:4046` 改为 `const refreshed = await refreshExpertTagsFromEs(orcidId, level); const refreshedTags = refreshed.tags;`，其后 `:4047-4050` 的 `.includes` / `.filter` 逻辑逐字不变。

**任务 2.3 — `renderExpertTagEditor` 增加不可用态**（I-3, S-1, S-2）

- `:3951` 签名增加末位可选参数 `profileMissing = false`（放末位，既有 4 个调用点的位置参数不受影响）。
- `profileMissing === true` 时产出 S-1 的逐字 DOM；否则产出 S-2 的逐字 DOM（现状不变）。
- `:4449` `renderMailboxExpertTagEditor` 增加同名末位参数并透传。

**任务 2.4 — 5 个消费点适配**（I-4, I-5, S-1）

| 行 | 改法 |
|---|---|
| 6585 | `const t = expert.orcidId ? await fetchExpertTagsFromEs(...) : { found:false, tags:[] };` → `:6605` 传 `t.tags` 与 `t.found === false` |
| 6952 | 同上，`:6969` 传参 |
| 8347 | 见任务 2.5 |
| 8766 | 取对象后传 `renderMailboxExpertTagEditor(detail, t.tags, "mailboxExpertTagEditor", t.found === false)` |
| 9325 | 同 8766，editorId 为 `"mailboxProcessingExpertTagEditor"` |

**任务 2.5 — 加标签入口前置拦截**（I-3）

- `:8347` 取到对象后：`if (existing.found === false) { showStatus("该专家在 ES 中无画像文档，标签功能不可用", "warn"); return; }`，**在 `openExpertTagAddDialog()` 之前** return，确保 `#actionDialog` 不被打开（K-shared-action-dialog-cleanup）。
- 其后 `const existingTags = existing.tags;`，`:8349-8354` 逻辑逐字不变。

**任务 2.6 — 两个监听器补 catch**（I-5）

- `:11489` `await handleUnmatchedAction(target);` → `handleUnmatchedAction(target).catch((error) => showStatus(error.message, "error"));`
- `:10499-10503` 分支内的 `await showUnmatchedDetail(target.dataset.id);` 所在链路以 `.catch((error) => showStatus(error.message, "error"))` 收口。
- 写法与 `:11013` 逐字一致，**不引入新的错误提示形态**。

### 阶段 3：前端测试（子系统 ②）

**任务 3.1 — 修既有 JS 测试**（Interaction point 4）

文件：`src/test/js/expertTagBatchFix.test.js`

- `createTagFetchSandbox()`（`:19-25`）的 `api` 桩改为返回 `{ found: true, tags: [] }`，相关断言按新返回对象调整。

**任务 3.2 — 新增 JS 测试**（I-3, I-4, S-1）

文件：`src/test/js/expertProfileAbsence.test.js`（新增，沿用 `expertTagBatchFix.test.js` 的 `vm` + `extractFn` 源码抽取范式）

- `found === false` → `renderExpertTagEditor` 产出**不含** `data-action="expert-add-tag-open"` 与 `data-action="expert-remove-tag"`，**含** `class="muted"` 与文案 `该专家在 ES 中无画像文档，标签功能不可用`，**含** `data-profile-missing="true"`（S-1）。
- `found === true` → 产出与 S-2 逐字一致，含「+ 添加标签」按钮。
- `found` 为 `undefined` → 走**有画像**分支（I-4 回归）。
- `api` 桩抛错 → `fetchExpertTagsFromEs` 向上抛，**不**返回 `{found:false}`（I-1）。
- 按 K-dom-stub-tests-hide-dangling-refs：断言 `.muted` 与 `.inbound-tag-editor-chips` 确实出现在 `styles.css` 源文本中，避免依赖不存在的 class。

---

## 变更文件清单

| # | 文件 | 类型 | 子系统 | 不变量 / 契约 |
|---|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` | 修改 | ① 后端只读端点 | I-1 |
| 2 | `src/main/resources/static/app.js` | 修改 | ② 前端标签区与详情面板 | I-1, I-2, I-3, I-4, I-5, S-1, S-2 |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt` | 修改 | ① | I-1 |
| 4 | `src/test/js/expertTagBatchFix.test.js` | 修改 | ② | I-4 |
| 5 | `src/test/js/expertProfileAbsence.test.js` | 新增 | ② | I-1, I-3, I-4, S-1, S-2 |

**文件数：5 ≤ 10 ✓** ｜ **子系统数：2 ✓** ｜ **共享存储新增字段：0 ✓** ｜ **数据库迁移：无 ✓** ｜ **`styles.css` 改动：无 ✓**

---

## 验证命令（可直接复制执行）

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。以下为**唯一权威的可执行形式**，fix-v / verify-p 直接照抄，不得自行推断或简化。
> `mvn test` 已通过 `pom.xml:199` 的 exec 插件一并运行 `node --test src/test/js/*.test.js`。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关后端测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertIndexControllerTest

# 本计划新增/修改的前端测试（单独运行，无需 JDK）
node --test src/test/js/expertProfileAbsence.test.js
node --test src/test/js/expertTagBatchFix.test.js

# 前端语法检查
node --check src/main/resources/static/app.js

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：全量测试退出码 0 且输出含 `Tests run: N, Failures: 0, Errors: 0`；`node --test` 退出码 0 且输出 `pass N` / `fail 0`；`node --check` 无输出且退出码 0；`git diff --check` 无输出。

来源：`CLAUDE.md` 项目元信息 `test_command` / `build_command`；JS 测试命令取自 `pom.xml:199`。

---

## 验收标准

- **I-1**：① `ExpertIndexControllerTest` 三个用例全绿（返回 `found=true` / `found=false` 不抛异常 / 异常上抛）。② grep `ExpertIndexController.kt` 的 `getExpertProfile` 方法体，确认**不含** `ResponseStatusException` 与 `try`。③ grep `fetchExpertTagsFromEs` 函数体，确认**不含** `catch`。
- **I-2**：grep `app.js` 全文，确认无 `ExpertIndexLevel.CANDIDATE` 类的回退逻辑进入 `fetchExpertTagsFromEs`；确认 `renderExpertTagEditor` 的 `data-level` 仍由入参 `level` 直接产出，且 `handleContactAction` 仍读 `editor.dataset.level`（`:8344`）。
- **I-3**：`expertProfileAbsence.test.js` 断言 `found=false` 产出不含两个 `data-action`；grep 任务 2.5 改动处，确认 `showStatus(...)` + `return` 出现在 `openExpertTagAddDialog` 调用之前。
- **I-4**：`expertProfileAbsence.test.js` 的 `found === undefined` 用例通过；grep `app.js` 确认全文无 `if (!found)` / `!t.found` 形态，全部为 `=== false`。
- **I-5**：grep `app.js:11489` 与 `:10499` 一带，确认均以 `.catch((error) => showStatus(error.message, "error"))` 收口，与 `:11013` 逐字一致；确认 `showUnmatchedDetail` 中标签获取失败不再位于 `panel.hidden = false` 之前的阻断位置。
- **S-1**：diff 断言 `found=false` 分支产出的 HTML 与契约 S-1 代码块**逐字一致**（含 `data-profile-missing="true"`）；grep 确认本次 diff 中 `styles.css` **零改动**、无新增 class、无 `style="` inline 样式。
- **S-2**：diff 断言 `renderExpertTagEditor` 有画像分支的 HTML 与契约 S-2 代码块逐字一致（除新增的 `profileMissing` 参数外无变化）。
- **回归**：执行「验证命令」节的全量测试命令通过。

跨 interaction point 集成断言：

- IP-2：grep `fetchExpertTagsFromEs` 全部 5 个消费点，确认每处都取 `.tags` 而非直接使用返回值。
- IP-3：`mutateExpertTag`（`:4046`）改动后仍能对返回值执行 `.includes` / `.filter`，由 `expertTagBatchFix.test.js` 覆盖。

---

## 人工验收清单

> **执行约定（2026-08-06 需求方确认）**：本节为**建议性清单，非强制门禁**。验收人按实际环境条件挑选执行即可，
> 不要求逐条留痕、不要求导出 `<plan-name>-acceptance.md` 勾选文件。
> 其中 A-4 / A-5 需要人为制造 ES 不可用，若环境不便，可降级为代码审查确认
> `fetchExpertTagsFromEs` 函数体内无 `catch`（等价于「验收标准」的 I-1 第 ③ 项），不必真的停 ES。
> 机器可验证的部分仍以 `## 验收标准` 为准，那一节是强制的。

### A-1: 无画像专家的来信详情可正常处理

- 前置条件：存在联系人 `orcidId = TEST-LUKAI-18014905480`（MySQL `expert_contact` 有记录，ES CANDIDATE 与 APPLICATION 两层均无该 ORCID 文档），且该联系人有一封处于 `MANUAL_REVIEW` 的来信（主题 `Re: Gentle Follow-up on the Requested Materials`）。
- 操作步骤：① 打开「收发件箱」；② 定位该来信；③ 点「查看/处理」；④ 打开浏览器控制台。
- 预期结果：详情面板展开，元数据卡片（时间/方向/邮件类型/邮箱账号/专家邮箱/专家姓名/附件/发送状态/主题）、来信正文、「处理与回复」区、「操作日志」折叠区**全部可见**；「专家标签」区显示灰色小字 `该专家在 ES 中无画像文档，标签功能不可用`，且**没有**「+ 添加标签」按钮；控制台**无** `Uncaught (in promise)` 报错。
- 覆盖：需求 1、I-3、I-5、S-1

### A-2: 无画像专家的专家详情与联系人详情

- 前置条件：同 A-1。
- 操作步骤：① 打开「专家联系」列表；② 点开该联系人详情。
- 预期结果：详情页完整渲染，「专家标签」区显示与 A-1 完全相同的灰色提示文案，无「+ 添加标签」按钮，页面其余区块正常。
- 覆盖：需求 2、S-1

### A-3: 有画像专家的标签增删未被破坏（回归）

- 前置条件：任选一个 ES CANDIDATE 层**存在**画像文档的专家，且其 `expert_contact.current_index_level = 'CANDIDATE'`。
- 操作步骤：① 打开其联系人详情；② 点「+ 添加标签」；③ 选一个预设标签提交；④ 观察标签区；⑤ 点该标签上的 `×` 删除；⑥ 刷新页面。
- 预期结果：② 弹出「添加专家标签」对话框；③ 顶部状态条显示 `标签已添加`（绿色）；④ 新标签以 chip 形式出现在标签区；⑤ 状态条显示 `标签已移除`（或既有文案），chip 消失；⑥ 刷新后标签状态与操作后一致。全过程标签区 DOM 与改动前**肉眼无差异**。
- 覆盖：must-NOT-change 第 1、2 项、I-2、S-2

### A-4: ES 故障必须可见，不得静默降级（回归）

- 前置条件：临时把 `application.yml` 的 ES `base-url` 指向一个不可达地址（或停掉 ES），重启应用。
- 操作步骤：① 打开任一联系人详情。
- 预期结果：页面顶部弹出**红色错误状态条**，内容为后端返回的错误信息；标签区**不得**显示"该专家在 ES 中无画像文档，标签功能不可用"（那是画像缺失的文案，不是故障文案）。
- 覆盖：需求 3、I-1

### A-5: 列表页操作失败有可见反馈（回归）

- 前置条件：同 A-4（ES 不可达）。
- 操作步骤：① 打开「收发件箱」；② 点任一来信的「查看/处理」；③ 打开「监控」→ 活动表，点任一 `处理` 按钮。
- 预期结果：两处均在页面顶部弹出红色错误状态条；控制台**无** `Uncaught (in promise)`。
- 覆盖：需求 4、I-5

### A-6: 加标签入口在无画像时不打开对话框

- 前置条件：同 A-1，且**临时**用浏览器调试工具在标签区注入一个 `data-action="expert-add-tag-open"` 按钮（模拟旧缓存页面），或直接在有画像专家详情打开后手动把 ES 文档删除再点按钮。
- 操作步骤：① 点该按钮。
- 预期结果：顶部状态条显示橙色 `该专家在 ES 中无画像文档，标签功能不可用`；「添加专家标签」对话框**不出现**；随后在**另一个有画像**的专家上点「+ 添加标签」，对话框正常打开并可正常提交（验证共用弹窗未被污染）。
- 覆盖：I-3

### A-7: 跨路径 —— 后端加字段不破坏旧前端（interaction point 1）

- 前置条件：部署新后端，浏览器**强制使用旧版 `app.js`**（禁用缓存刷新前先复制一份旧文件替换，或用 DevTools 覆盖）。
- 操作步骤：① 打开有画像专家的联系人详情。
- 预期结果：标签区正常渲染标签 chips，行为与改动前一致（旧前端忽略 `found` 字段）。
- 覆盖：interaction point 1、I-4

---

## 关联缺陷移交（不在本计划范围，需另行处置）

本次审计顺带确认了两项与本计划无因果关系的缺陷，按范围纪律**只记录不建任务**：

1. **`List-Unsubscribe-Post` 值不合 RFC 8058** —— `SmtpMailDeliveryService.kt:54` 写的是 `List=One-Click`，RFC 8058 §5 ABNF 要求逐字 `List-Unsubscribe=One-Click`；§4 另要求这两个头必须被 DKIM `h=` 覆盖，而腾讯企业邮的签名 `h=Date:From:To:Message-ID:Subject:MIME-Version` 不含它们。**根因是计划缺陷**：`docs/plans/2026-06-20/unsubscribe-suppression-02-list-unsubscribe-oneclick.md:9/26/152/187` 四处均写死了错误值，代码是忠实实现。已按 CLAUDE.md 决策日志协议在该计划追加 `## 修正记录`。代码修正应并入 `material-reminder-02-headers-personalization.md`（该计划已持有 `SmtpMailDeliveryService` 退订头逻辑的所有权与任务 7 的修正记录机制），**不另起新计划**，避免三个计划同时改同一文件。
2. **Message-ID 域名硬编码 `@weibo.com`** —— `ManualInitialOutreachService.kt:587`、`ManualReplySendAttemptService.kt:35`，与 `IntroductionMailComposer.kt:26` / `ManualExpertMailService.kt:198` 按发件账号域名取值的做法不一致。观察项，需单独立项。
