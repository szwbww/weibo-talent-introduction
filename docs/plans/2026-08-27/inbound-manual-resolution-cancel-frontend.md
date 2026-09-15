# 收件“取消处理”前端接入计划

> 顺序计划 2/2。依赖 `inbound-manual-resolution-cancel-backend.md` 已实现并部署；本计划只消费其 API，不改变后端协议。

## 需求描述

### 可观察结果

1. 邮箱平铺视图和按专家视图中，只有人工标记完成的收件卡片显示“取消处理”按钮。
2. 点击按钮弹出“取消处理”对话框，必填“操作人姓名”、可填“取消原因”；确认成功后提示“已取消处理，可重新处理”，列表刷新为“查看/处理 + 标记已处理”。
3. 该收件详情的操作日志显示中文“取消处理”及 `PROCESSED → MANUAL_REVIEW` 转移。

### 必须保持不变

- `MANUAL_REVIEW` 卡片继续只显示“查看/处理 + 标记已处理”；普通发件、自动处理收件、人工绑定收件不显示取消按钮。
- 现有“查看专家”“查看”“标记已处理”行为、邮箱筛选/分页/平铺与专家分组切换保持不变。
- 点击对话框“取消”不得发请求、刷新列表或改变卡片。
- API 失败时不得显示成功提示；现有全局错误提示必须展示后端错误。
- 不修改现有 CSS 规则，不新增 class 或 inline style。

### 明确不做

- 不在收件详情面板内再放第二个取消按钮；入口限定邮箱卡片操作区。
- 不允许取消绑定/自动处理结果，不增加批量取消。
- 不改后端、数据库、状态语义或操作日志写入。
- 不重构共享 action dialog、邮箱 renderer 或事件委托框架。
- 不覆盖规划期间已存在的无关未提交改动：`app.js` 的 `showBatchConfigEditor` 调用顺序调整，以及 `batchSendTaskConsoleInteraction.test.js` 对应测试；执行前后必须逐段核对并保留。

## 关键不变量

### Invariant I-1: 按钮资格与后端条件完全一致

- Rule: `canCancel` 必须同时满足 `row.source === "INBOUND_PROCESSING"`、`row.processStatus === "PROCESSED"`、`row.reasonType === "MANUAL_RESOLVED"`、`row.inboundProcessingId` 有值；其他任何组合不渲染 `cancel-unmatched-resolved`。
- Applies to: `renderMailboxActions`、其 JS 测试。
- Violation consequence: 用户看到注定 409 的按钮，或能从 UI 尝试取消错误类型邮件。
- 来源: original；邮箱 DTO 已提供三个判断字段，见 `MailboxController.kt:12-32`，SQL 投影见 `MailRecordRepository.kt:438-459,701-722`。

### Invariant I-2: 单一请求与成功后刷新

- Rule: 对话框确认后只 POST 一次 `/api/mail/unmatched-inbound/{id}/cancel-resolved`，body 只含 `operatorName`、`note`；仅请求成功后显示精确文案“已取消处理，可重新处理”并调用一次 `refreshMailboxAfterPendingAction()`。对话框取消时零请求；失败时不执行成功分支。
- Applies to: `ACTION_DIALOG_SCHEMAS`、`handleUnmatchedAction`、邮箱列表事件白名单。
- Violation consequence: 重复取消触发 409、错误成功提示、列表状态滞后。
- 来源: original；现有 mark 流程的请求/刷新顺序见 `app.js:10248-10260`，刷新函数复用 `loadMailbox + refreshUnmatchedBadge` 见 `app.js:9645-9648`。

### Invariant I-3: 后端状态驱动重新处理 UI

- Rule: 成功后不在浏览器内手改 row；必须重新读取后端。后端返回的 `MANUAL_REVIEW` 经现有 `renderMailboxActions` 自动显示“查看/处理 + 标记已处理”，经 `MailboxService.computeTags` 自动带“待处理”。
- Applies to: 成功处理器、`renderMailboxActions`。
- Violation consequence: UI 与数据库分叉，刷新前后按钮不一致。
- 来源: K-inbound-processing-write-paths；现有按钮条件见 `app.js:9514-9531`，标签条件见 `MailboxService.kt:357-386`。

### Invariant I-4: 取消日志可读但不改变协议

- Rule: 前端只增加 `CANCEL_INBOUND_RESOLVED -> "取消处理"` 标签，并让 `renderLogDetail` 与 `MARK_INBOUND_RESOLVED` 共用 processStatus 转移 renderer；不得依赖未在后端日志契约中的额外字段。
- Applies to: `actionTypeLabel`、`renderLogDetail`。
- Violation consequence: 后端已审计但 UI 显示原始枚举或不可读 JSON。
- 来源: original；既有 MARK renderer 见 `app.js:8023-8027,8069-8080`。

### Invariant I-5: 静态资源缓存键三项同值

- Rule: `index.html` 中 `styles.css?v=`、`trust-reply-workbench.js?v=`、`app.js?v=` 必须同时改为精确值 `20260827-v3-inbound-cancel-resolved`；仓库内所有对旧键 `20260827-v2-trust-reply-preview` 的测试常量/断言必须同步。
- Applies to: `index.html` 与四个硬编码测试文件。
- Violation consequence: 浏览器继续加载旧 app.js，或 Maven test phase 的 Node 测试失败。
- 来源: K-frontend-cache-key-triad；当前旧键的全部命中由 `rg --fixed-strings "20260827-v2-trust-reply-preview"` 证明仅在 `index.html` 三处和四个测试文件。

## 样式契约

### S-1: 邮箱卡片“取消处理”按钮

- 复用: `.button` 与 `.button.secondary`，现有完整规则及交互态位于 `styles.css:655-713`。基础实值：高度 32px、横向 padding 12px、字号 12px、圆角 `--radius-sm=7px`；secondary 背景 `rgba(30,64,175,0.07)`、边框 `rgba(30,64,175,0.12)`、文字 `#1e40af`，hover 背景 `rgba(30,64,175,0.1)`（token 见 `styles.css:1-30,70-83`）。
- 新增: 无 CSS、无新 class。
- DOM 结构必须逐字为：

  ```html
  <button class="button secondary" data-action="cancel-unmatched-resolved" data-id="${row.inboundProcessingId}">取消处理</button>
  ```

- 插入位置: `renderMailboxActions` 的非 `canProcess` 分支中，在既有“查看”按钮之后；`renderMailboxCard` 的 `.mailbox-card-actions` 继续消费返回字符串，不改卡片骨架（`app.js:12992-13038`）。
- 禁止项: 新 class、inline style、修改 `.button`/`.button.secondary`、danger 样式、图标、二次 DOM 容器。

### S-2: 取消处理对话框

- 复用: 现有 `#actionDialog.action-dialog` 骨架 `index.html:1980-1990`、`.action-dialog*` 规则 `styles.css:2625-2673`、`openActionDialog` 渲染/清理 `app.js:12297-12404`。（来源: K-shared-action-dialog-cleanup）
- 新增: 只在 `ACTION_DIALOG_SCHEMAS` 增加 schema；无 CSS、无新 class、无新的事件监听器。
- Schema 必须逐字为：

  ```javascript
  "cancel-unmatched-resolved": {
      title: "取消处理",
      fields: [
          { name: "operatorName", label: "操作人姓名", type: "text", required: true },
          { name: "note", label: "取消原因", type: "textarea", required: false }
      ]
  },
  ```

- 运行时 DOM 继续由共享 renderer 生成，footer 继续是现有 `取消(.button.secondary)`、`确认执行(.button.primary)`；不得复制 dialog HTML。
- 禁止项: 改 `index.html` 对话框骨架、改 dialog CSS、增加自定义 submit/cancel listener、遗留 listener 或 disabled 状态。

## 现状审计

### 前端数据与事件路径

- Backend feed:
  1. `MailboxController.kt:12-32` 的 item DTO 已含 `processStatus`、`reasonType`、`inboundProcessingId`；无需新增前端字段。
  2. `MailRecordRepository.kt:438-459,701-722` 在平铺/专家邮件查询中投影这些字段。
  3. `MailboxService.kt:228-253,324-354` 透传这些字段；`:357-386` 按 `MANUAL_REVIEW` 生成“待处理”。
- Render reads:
  1. `app.js:9514-9531` 的 `renderMailboxActions` 是按钮唯一生成点；当前 `MANUAL_REVIEW` 走处理分支，其余走查看分支。
  2. `app.js:12992-13038` 的 `renderMailboxCard` 被平铺和专家分组共同复用，故一个资格判断覆盖两种视图。
  3. `app.js:9455-9468` 已有 `MANUAL_RESOLVED: "已人工处理"` 标签；后端取消后的 `reasonType=NULL` 不需要新原因标签。
- Event/write path:
  1. `app.js:12542-12556` 在 `#mailboxList` 统一事件委托中维护 action 白名单；新 action 必须加入。
  2. `app.js:10230-10260` 的 `handleUnmatchedAction` 已有 mark endpoint、dialog、成功提示和刷新范式。
  3. `app.js:9645-9648` 的 `refreshMailboxAfterPendingAction` 同时刷新邮箱与未匹配 badge。
- Audit read:
  1. `app.js:7951-7981` 渲染日志列表。
  2. `app.js:7984-8061` 按 action type 渲染细节；MARK 已有状态转移。
  3. `app.js:8069-8080` 是 action 中文标签表。
- Dialog lifecycle:
  1. `app.js:12235-12295` 是共享 schema 注册点。
  2. `app.js:12297-12404` 每次打开创建局部 submit/cancel handler，并在成功或取消时移除；新增 schema 无自定义 listener，不改变清理契约。（来源: K-shared-action-dialog-cleanup）
- Interaction points:
  - 后端计划写 `MANUAL_REVIEW` → `loadMailbox()` 读取 → 既有处理按钮与“待处理”标签恢复（I-3）。
  - 后端计划写 `CANCEL_INBOUND_RESOLVED` → 收件详情 logs API → 新标签/共用转移 renderer（I-4）。
  - app.js 内容变化 → HTML 缓存键三项同时 bump → 浏览器加载新代码且 Node 契约测试通过（I-5）。

### 前端样式盘点

- 可复用 class:
  - `.button`、`.button:hover`、`.button:active` — `styles.css:655-689`。
  - `.button.primary`/hover — `styles.css:691-703`；仅由对话框既有确认按钮使用。
  - `.button.secondary`/hover — `styles.css:705-713`；新卡片按钮复用。
  - `.action-dialog`、backdrop、title/body/footer — `styles.css:2625-2673`。
- 设计基准 token:
  - primary `#1e40af`、hover `#1e3a8a`、bright `#3b82f6`；border `rgba(15,23,42,0.11)`；text `#1e293b`；radius-sm `7px`、radius-lg `18px`；transition `all 0.15s ease`，见 `styles.css:1-83`。
  - dialog 宽 `min(500px,90vw)`、padding 20px、body gap 12px、footer gap 8px，见 `styles.css:2625-2673`。
- DOM 结构约定:
  - 卡片 action 由 `renderMailboxActions(row)` 返回同级 `<button>` 字符串，见 `app.js:9514-9531,13021`。
  - 事件通过 `#mailboxList` 的 `data-action` 委托，不给按钮单独绑定 listener，见 `app.js:12542-12556`。
  - 共享对话框固定骨架见 `index.html:1980-1990`，字段由 schema renderer 注入。
- 改动前基线:

  ```javascript
  function renderMailboxActions(row) {
      const actions = [];
      const canProcess = row.source === "INBOUND_PROCESSING"
          && row.processStatus === "MANUAL_REVIEW"
          && row.inboundProcessingId;

      if (canProcess) {
          actions.push(`<button class="button primary" data-action="open-pending" data-id="${row.inboundProcessingId}">查看/处理</button>`);
          actions.push(`<button class="button" data-action="mark-unmatched-resolved" data-id="${row.inboundProcessingId}">标记已处理</button>`);
      } else {
          if (row.expertContactId) {
              actions.push(`<button class="button" data-action="open-monitoring-contact" data-id="${row.expertContactId}">查看专家</button>`);
          }
          actions.push(`<button class="button" data-action="view-mail" data-source="${escapeHtml(row.source || "")}" data-id="${escapeHtml(row.id)}">查看</button>`);
      }
      return actions.join(" ") || "-";
  }
  ```

  基线来源 `app.js:9514-9531`。本计划不修改任何 CSS 规则块，因此无需枚举 `.button.secondary` 的全部使用点。

### 静态资源缓存契约

- 当前 `index.html:11,2097-2098` 三项键均为 `20260827-v2-trust-reply-preview`。
- 全仓精确 grep 证明旧键还硬编码于：
  1. `checkRepliesRelocation.test.js:11`；
  2. `overlayAndDialogContrast.test.js:15`；
  3. `manualReplySubjectPrefill.test.js:13`；
  4. `batchSendTaskConsoleVisualFix.test.js:49-51`。
- `trustReplyWorkbenchSharedMount.test.js:344-346` 只断言三项存在且相等，无固定值，不需修改。
- Maven test phase 会执行全部 `src/test/js/*.test.js` 并 `node --check app.js`，见 `pom.xml:184-217`。

## 实现方案

### 阶段 1：用前端测试锁定资格、请求与日志

1. 扩展 `unmatchedDetailResolvedAction.test.js`，覆盖 I-1 至 I-4、S-1、S-2：
   - 精确 eligible row 渲染 S-1 按钮；source/status/reasonType/id 任一不符均不渲染；
   - schema 标题/字段名/required 与 S-2 逐字一致；
   - 邮箱 action 白名单包含 `cancel-unmatched-resolved`；
   - handler 调用精确 endpoint、POST、精确 body，成功文案和刷新调用各一次；dialog 返回 null 时零请求；API reject 时零成功提示/刷新；
   - `actionTypeLabel("CANCEL_INBOUND_RESOLVED") === "取消处理"`，日志细节显示 `PROCESSED → MANUAL_REVIEW`；
   - 原 mark 按钮/endpoint 断言继续保留，防回归。

### 阶段 2：接入按钮、对话框和事件

1. 在 `app.js` 的 `renderMailboxActions` 新增 I-1 的 `canCancel`，只在非 canProcess 分支、现有“查看”按钮之后追加 S-1 DOM。
2. 在 `ACTION_DIALOG_SCHEMAS` 按 S-2 原样增加 schema。
3. 在 `handleUnmatchedAction` 增加 I-2 分支；使用 `openActionDialog("cancel-unmatched-resolved")`，POST backend plan 的 endpoint，成功后只提示并调用 `refreshMailboxAfterPendingAction()`；不主动关闭/改写当前不存在的详情上下文。
4. 在 `#mailboxList` action 白名单加入新 action，继续走统一 `handleUnmatchedAction(...).catch(showStatus)`。
5. 在 `renderLogDetail` 让 `CANCEL_INBOUND_RESOLVED` 与 `MARK_INBOUND_RESOLVED` 共用状态转移 case；在 `actionTypeLabel` 增加中文标签。覆盖 I-4。

### 阶段 3：同步缓存键

1. 把 `index.html` 三项缓存键同时改为 `20260827-v3-inbound-cancel-resolved`。
2. 同步修改 `checkRepliesRelocation.test.js`、`overlayAndDialogContrast.test.js`、`manualReplySubjectPrefill.test.js` 的 `CACHE_KEY` 和 `batchSendTaskConsoleVisualFix.test.js` 三条固定断言。覆盖 I-5。
3. 修改后运行 `rg --fixed-strings "20260827-v2-trust-reply-preview"`，生产/测试范围必须为零命中；运行正则提取确认 `index.html` 仍恰好 3 个 `?v=` 且只有一个值。

### 阶段 4：验证

1. `node --test src/test/js/unmatchedDetailResolvedAction.test.js`。
2. `node --test src/test/js/checkRepliesRelocation.test.js src/test/js/overlayAndDialogContrast.test.js src/test/js/manualReplySubjectPrefill.test.js src/test/js/batchSendTaskConsoleVisualFix.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js`。
3. `node --check src/main/resources/static/app.js`。
4. `mvn test package`，覆盖全量 Kotlin/Node 与打包。
5. `git diff --check`；确认 `styles.css` 零 diff。
6. 用 `git diff -- src/main/resources/static/app.js src/test/js/batchSendTaskConsoleInteraction.test.js` 核对规划时已有的 batch gate 改动仍存在且内容未被本计划改写；后者不在本计划文件清单内。

## 变更文件清单

| # | 文件 | 变更 | 所属子系统 |
|---|---|---|---|
| 1 | `src/main/resources/static/app.js` | 按钮资格、schema、handler、白名单、日志展示 | 邮箱前端 |
| 2 | `src/main/resources/static/index.html` | 三项静态资源缓存键同步 bump | 前端交付 |
| 3 | `src/test/js/unmatchedDetailResolvedAction.test.js` | 取消处理 UI/请求/日志回归测试 | 邮箱前端 |
| 4 | `src/test/js/checkRepliesRelocation.test.js` | 更新固定缓存键常量 | 前端交付 |
| 5 | `src/test/js/overlayAndDialogContrast.test.js` | 更新固定缓存键常量 | 前端交付 |
| 6 | `src/test/js/manualReplySubjectPrefill.test.js` | 更新固定缓存键常量 | 前端交付 |
| 7 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 更新三项固定缓存键断言 | 前端交付 |

文件数：7；独立子系统：2（邮箱前端、静态资源交付契约）；共享 store 新字段数：0。`styles.css` 明确不在变更清单内。

## 验收标准

- I-1: JS 测试构造资格矩阵，只有 `INBOUND_PROCESSING + PROCESSED + MANUAL_RESOLVED + inboundProcessingId` 命中一次新 action；其他组合零命中。
- I-2: JS 测试精确断言 endpoint、method、JSON 字段、成功文案、一次刷新；dialog cancel/API reject 分支断言零成功副作用。
- I-3: 测试证明 handler 不直接修改 row/DOM；成功路径调用现有刷新。集成测试/人工场景确认刷新后由后端 `MANUAL_REVIEW` 驱动既有两个处理按钮和“待处理”标签。
- I-4: JS 测试精确断言中文标签与 `PROCESSED → MANUAL_REVIEW`；既有 MARK 转移仍通过。
- I-5: 四个固定键测试与 `trustReplyWorkbenchSharedMount` 全通过；grep 旧键零命中；HTML 三项键精确同值 `20260827-v3-inbound-cancel-resolved`。
- S-1: app.js 中新按钮 DOM 与契约逐字一致；`styles.css` 零 diff；不存在 `cancel-unmatched-resolved` 新 CSS selector 或 inline style。
- S-2: schema 与契约逐字一致；`index.html:1980-1990` 对话框骨架除文件尾缓存键外零 diff；共享 `openActionDialog` 函数零 diff。
- 回归: mark resolved、查看、查看专家、平铺/专家切换、筛选、分页相关既有 Node 测试全通过。
- 构建: 定向 Node、`node --check`、`mvn test package`、`git diff --check` 全通过。

## 人工验收清单

### A-1: 平铺视图资格矩阵

- 前置条件: 邮箱中各有一条人工标记完成收件、自动处理完成收件、人工绑定收件、待处理收件和普通发件。
- 操作步骤: 打开邮箱平铺视图，逐条查看操作区。
- 预期结果: 只有人工标记完成收件显示“取消处理”；待处理收件显示“查看/处理”“标记已处理”；其他三类不显示“取消处理”。
- 覆盖: I-1、S-1、可观察结果 1、必须保持不变第 1 项。

### A-2: 按专家视图资格一致

- 前置条件: A-1 的有关联专家收件仍存在。
- 操作步骤: 切换“按专家”视图并展开/查看邮件卡片。
- 预期结果: 与 A-1 相同的记录显示“取消处理”，其他记录资格不变；切回平铺视图结果一致。
- 覆盖: I-1、S-1、可观察结果 1、必须保持不变第 2 项。

### A-3: 对话框取消零副作用

- 前置条件: 一条显示“取消处理”的卡片。
- 操作步骤: 点击“取消处理”；检查标题和字段；不填写并点击 footer“取消”。
- 预期结果: 标题“取消处理”；字段标签为“操作人姓名”“取消原因”；对话框关闭；无网络取消请求、无成功提示、卡片不变。
- 覆盖: I-2、S-2、必须保持不变第 3 项。

### A-4: 必填校验

- 前置条件: 同 A-3。
- 操作步骤: 打开对话框，操作人留空，点击“确认执行”。
- 预期结果: 浏览器 required 校验阻止提交；无 cancel-resolved 请求；对话框保持打开。
- 覆盖: I-2、S-2、可观察结果 2。

### A-5: 成功取消并刷新为可重新处理

- 前置条件: 后端顺序计划已部署；一条显示“取消处理”的卡片。
- 操作步骤: 输入操作人“验收员”、取消原因“误标”，点击“确认执行”。
- 预期结果: 只发一次 POST，body 为 `{"operatorName":"验收员","note":"误标"}`；出现“已取消处理，可重新处理”；同一卡片刷新后带“待处理”，操作区变为“查看/处理”“标记已处理”，不再显示“取消处理”；普通优先级 badge 数增加 1，高优先级 badge 不变。
- 覆盖: I-2、I-3、S-1、可观察结果 2、后端交互点。

### A-6: API 失败不伪成功

- 前置条件: 打开两个浏览器窗口指向同一条可取消收件；窗口 1 先成功取消。
- 操作步骤: 窗口 2 不刷新，点击旧“取消处理”并提交。
- 预期结果: 后端 409 错误通过现有错误提示展示；不出现“已取消处理，可重新处理”；页面不执行成功刷新分支。
- 覆盖: I-2、必须保持不变第 4 项。

### A-7: 取消日志中文展示

- 前置条件: A-5 已完成。
- 操作步骤: 打开该收件“查看/处理”，展开“操作日志”。
- 预期结果: 新日志标题为“取消处理”，显示 `PROCESSED → MANUAL_REVIEW`，操作人“验收员”、备注“误标”；原“标记已处理”日志仍显示。
- 覆盖: I-4、可观察结果 3。

### A-8: 既有邮箱操作回归

- 前置条件: 各准备一条待处理、有专家的普通收件、普通发件。
- 操作步骤: 依次执行“标记已处理”“查看专家”“查看”，切换筛选、分页、平铺/专家视图。
- 预期结果: 原按钮文案、跳转/详情、筛选结果、页码变化均与改动前一致；仅符合 I-1 的行增加“取消处理”。
- 覆盖: 必须保持不变第 1、2 项。

### A-9: 样式与缓存生效

- 前置条件: 部署新构建；浏览器曾访问旧版本页面。
- 操作步骤: 普通刷新页面；检查 HTML 资源 URL 和新按钮/对话框视觉。
- 预期结果: 三个资源 URL 均带 `?v=20260827-v3-inbound-cancel-resolved`；按钮高 32px、字号 12px、圆角 7px，蓝色浅底，hover 背景加深；对话框宽不超过 500px、padding 20px、footer 间距 8px；无布局抖动。
- 覆盖: I-5、S-1、S-2、必须保持不变第 5 项。
