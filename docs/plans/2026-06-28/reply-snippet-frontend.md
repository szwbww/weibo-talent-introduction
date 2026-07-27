# 人工拼装回复前缀片段 — Plan B：前端（配置页 + 致谢勾选 + 预览取数）

> 子计划 B。**依赖 Plan A 已交付**：`/api/reply-snippets` CRUD、`suggest` 响应含 `salutation/greeting/closing/ackOptions`、`composed-reply` 与 `polish` 接受 `ackSnippetId`。
> mockup 参考：`assets/compose-workbench-mockup.png`、`assets/reply-snippet-config-mockup.png`。

## 需求描述

- 可观察结果：
  1. 「人工拼装回复工作台」中列新增「致谢语（单选可不选）」chip 区，并只读显示尊语；组装预览按 尊语→致谢→开场白→sections→结束语→自由文本 呈现。
  2. 新增「回复片段配置」页：按 尊语/致谢语/开场白/结束语 四组管理（增删改、启用开关、设默认），挂在「模板管理」分组下。
- 必须不变：
  - 规则勾选/拖拽排序的既有行为与 `selectedRuleIds` 顺序契约不变。
  - `previewEdited` 后不被规则/致谢变更覆盖的既有逻辑不变。
  - 发送/润色既有参数（`qaRuleIds`/`overrideTextBody`/`freeTextBody`）不变，仅新增 `ackSnippetId`。
- 不在范围：尊语个性化姓名；致谢多选；自动回复预览改动。

## 关键不变量

### Invariant I-1: 预览 frame 取自服务端，删除前端硬编码常量
- Rule: `buildDeterministicComposedPreview` 的尊语/开场白/结束语必须取自 `composedReplyState.suggest`（来自 suggest 响应），删除/停用全局 `QA_COMPOSE_GREETING`、`QA_COMPOSE_CLOSING` 在该函数中的使用。
- Applies to: `app.js:buildDeterministicComposedPreview`(:4652)、`app.js:56-57` 常量。
- Violation consequence: 预览与后端外发漂移（违反 Plan A I-1）。
- 来源: K-preview-mirrors-pipeline, K-composed-reply-order-contract

### Invariant I-2: 致谢单选可空，按 id 传递
- Rule: 致谢区为单选（radio 语义）+「不添加」；选中写 `composedReplyState.ackSnippetId`，发送与润色 payload 携带 `ackSnippetId`（非文本）；未选传 null。
- Applies to: 致谢 chip 渲染与点击、`send-composed-reply`/`polish-composed-reply` 的 fetch body。
- Violation consequence: 与 Plan A I-4 不一致 / 预览失真。
- 来源: 原创（对齐 Plan A I-4）

### Invariant I-3: 预览顺序与后端一致
- Rule: 预览拼装顺序 = 尊语→致谢→开场白→sections(`selectedRuleIds` 序)→结束语→freeText；sections 仍按 `selectedRuleIds`，致谢/frame 不重排 sections。
- Applies to: `buildDeterministicComposedPreview`。
- Violation consequence: 违反 Plan A I-2/I-7。
- 来源: K-composed-reply-order-contract

### Invariant I-4: 配置页默认语义对齐后端
- Rule: 配置页对 SALUTATION/GREETING/CLOSING 展示「默认」徽标且设默认走 `/{id}/default`（同类型唯一）；ACK 不显示默认列（库）。
- Applies to: 回复片段配置视图渲染与操作绑定。
- Violation consequence: UI 与 Plan A I-5 语义错配。
- 来源: 原创（对齐 Plan A I-5）

## 现状审计

### app.js 拼装工作台
- 常量 `QA_COMPOSE_GREETING/CLOSING`(:56-57)；`buildDeterministicComposedPreview`(:4652-4668) 用之。
- `composedReplyState`(:59) 字段：recordId/suggest/selectedRuleIds/freeText/previewEdited/baselinePreview/activeGapIndex。**新增** `ackSnippetId`。
- `renderComposedReplyWorkbenchHtml`(~:4900) 输出中列：致谢区需插在「补充自由文本」之上、selected-list 之下。
- `refreshComposedPreviewFromRules`(:4804) 在勾选/拖拽后刷新；致谢变更也须触发（且遵守 `previewEdited` 守卫）。
- 发送 `send-composed-reply`(:5291) payload(:5304-5309) 加 `ackSnippetId`；润色 `polish-composed-reply`(:5269) payload(:5277-5280) 加 `ackSnippetId`。
- suggest 取数：`showUnmatchedDetail`(:4939) `GET .../composed-reply/suggest` → Plan A 已加 frame 字段。

### index.html
- 侧栏 `nav-tab data-view=...`(:75-115)；视图 `section.view#view-*`(:175+)。新增 `data-view="reply-snippets"` 与 `#view-reply-snippets`。
- 现有 QA 视图 `#view-qa`(:256) 可作为表格/操作交互参考。

### 测试
- `src/test/js/composedReplyOrder.test.js`：sandbox 注入 `QA_COMPOSE_GREETING/CLOSING`(:26-27) 并抽取 `buildDeterministicComposedPreview` 等函数（node:test + vm）。frame 改为来自 suggest 后，需更新 sandbox 与断言。

### 交互点
- IP-1：suggest 响应(frame/ackOptions) × 预览渲染 —— I-1/I-3。
- IP-2：致谢选择 × send/polish payload × 后端按 id 解析 —— I-2。
- IP-3：配置页 CRUD × `/api/reply-snippets` —— I-4。

## 实现方案

**Task B.1 — 预览取数改造**（I-1/I-3）
- `buildDeterministicComposedPreview(selectedRuleIds, suggest, freeText, ackContent)`：尊语/开场白/结束语取 `suggest.salutation/greeting/closing`（缺省省略）；顺序按 I-3；致谢用传入 `ackContent`（由 `ackSnippetId` 在 `suggest.ackOptions` 查得）。
- 移除该函数对全局 `QA_COMPOSE_GREETING/CLOSING` 的依赖（常量可保留作兜底或一并删除——优先删除，单源）。
- `refreshComposedPreviewFromRules` 传入当前致谢内容。

**Task B.2 — 致谢 chip UI + 状态**（I-2）
- `composedReplyState.ackSnippetId = null`；初始化于 `setupComposedReplyWorkbench`/state 重置处。
- `renderComposedReplyWorkbenchHtml` 增致谢区：遍历 `suggest.ackOptions` 渲染单选 chip + 「不添加」chip；只读显示尊语行。
- 点击 chip：设 `ackSnippetId`，`previewEdited` 守卫下刷新预览。

**Task B.3 — 发送/润色 payload 带 ackSnippetId**（I-2）
- `send-composed-reply`、`polish-composed-reply` 的 fetch body 增 `ackSnippetId: composedReplyState.ackSnippetId`。

**Task B.4 — 回复片段配置页**（I-4）
- index.html：加 `nav-tab data-view="reply-snippets"`（模板管理分组下）+ `section#view-reply-snippets`（四组表格容器 + 「新建片段」按钮）。
- app.js：视图加载函数 `loadReplySnippets()`——`GET /api/reply-snippets`，按 type 分四组渲染表格（内容/排序/默认/启用/操作）；绑定创建、编辑、启用切换、设默认、删除（调对应 Plan A 端点）。ACK 组不渲染「默认」列。
- 复用既有 `.button`/badge/toggle/表格样式（不改 styles.css）。

**Task B.5 — JS 测试更新/新增**（I-1/I-3）
- 更新 `composedReplyOrder.test.js`：sandbox 不再注入 frame 常量，改为在 `suggest` 对象提供 `salutation/greeting/closing/ackOptions`；断言预览顺序含尊语在最前、结束语在末、sections 保持 `selectedRuleIds` 序。
- 新增用例：选中某 ackOption → 预览尊语后出现该致谢文本；未选 → 不出现。

## 变更文件清单（3 ≤ 10）

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/resources/static/app.js` | 预览取数/致谢 UI 与状态/payload/配置页逻辑；删除/停用 frame 常量 |
| 2 | `src/main/resources/static/index.html` | 新增 nav-tab 与 `#view-reply-snippets` |
| 3 | `src/test/js/composedReplyOrder.test.js` | sandbox frame 来源改造 + 致谢断言 |

> styles.css 不改（复用既有表格/开关/徽标样式）——若渲染明显错位再追加，记为决策。

## 验收标准

- I-1：`composedReplyOrder.test.js`——sandbox 不含 frame 常量，frame 来自 suggest；预览首段为 suggest.salutation、末段为 suggest.closing。
- I-2：用例——设 `ackSnippetId` → payload 含该值且预览出现对应 ackOptions.content；点「不添加」→ null 且预览无致谢行。
- I-3：用例——`selectedRuleIds`=[B,A] 时预览 Body B 在 Body A 前，且尊语/致谢/开场白不改变该相对顺序。
- I-4：手动/快照——配置页 ACK 组无默认列，其余三组有默认徽标；设默认调用 `/{id}/default`。
- 集成：`node --test src/test/js`（或既有 JS 测试命令）通过；页面手测四组 CRUD + 工作台致谢链路。

## 自检清单
- [x] 关键不变量覆盖新状态（ackSnippetId）与 frame 来源
- [x] 现状审计列全 app.js 预览/发送/润色/视图注册路径
- [x] 文件数 3 ≤ 10
- [x] 子系统 1（前端）≤ 2
- [x] 每 task 标注不变量
- [x] 验收每条不变量 ≥1 检查
- [x] 文件清单无"等/相关文件"
- [x] 依赖 Plan A 的契约已显式声明
- [x] 知识 K-preview-mirrors-pipeline/K-composed-reply-order-contract 映射到 I-1/I-3
- [x] 保存至 docs/plans/2026-06-28/
