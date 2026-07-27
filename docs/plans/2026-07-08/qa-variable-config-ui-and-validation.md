# QA 占位符配置化：白名单校验 + 变量插入条 + 真数据预览

> 创建日期: 2026-07-08
> 前置依赖: Plan A `qa-reply-personalization-backend.md`（`MailVariableService` 与渲染 seam 必须先合入）。
> 触发原因: 占位符靠人工手打必然拼错；`renderText` 对未知 key 原样保留，拼错的占位符会字面外发给专家。需要"点击插入 + 保存拦截 + 真数据预览"三层防线，且全部显式、无黑盒。

## 需求描述

**可观测结果**：
1. 运营保存含非法占位符（未知变量名 / 可空变量无兜底）的 QA 规则或回复片段时被 400 拒绝并提示原因。
2. QA 规则编辑框、片段编辑框、组装台自由文本框上方出现变量 chip 插入条（数据来自后端元数据接口），点击在光标处插入；可空变量插入 `${key|}` 且光标停在 `|` 后。
3. 编辑器失焦时前端校验占位符并标红非法项；"以某专家预览"按钮调后端渲染端点回显最终文本，走了兜底的变量以黄底标出。

**不可变更**：
1. 存量 qa_rule / reply_snippet 数据不回溯校验（只拦截新的保存动作）。
2. `renderText` 语义不变；前端**不得**自实现 `${}` 替换（来源: K-composed-reply-order-contract 同源契约）。
3. 现有 QA 管理 / 片段管理 / 组装台视图结构不变，不新增侧栏 Tab（不触发 K-view-registration-triad 四处注册）。
4. `QaRuleManagementService` 现有校验（keywords/matchMode/priority/replyBody 非空）不变，新校验追加其后。

**不在范围**：
- 富文本/contenteditable 编辑器（textarea + 校验提示条即可）。
- 模板管理（mail_compose_template）编辑器的同款增强（后续复用组件时单独做）。
- AI 草稿链路的占位符处理。

## 关键不变量

### Invariant I-1: 变量元数据单源
- Rule: `GET /api/qa/template-variables-meta` 的返回由 `MailVariableService.variableMetadata()` 生成（Plan A 已建）；前端 chip 清单、校验白名单全部来自该接口，`app.js` 中不得出现硬编码变量名数组。
- Applies to: 新 controller 端点、`app.js` 变量插入条组件。
- Violation consequence: 变量清单双源漂移——后端加变量前端不显示，或前端校验放行后端不存在的 key。
- 来源: original（模式同 K-pending-qa-reply-rule-source 的单源教训）

### Invariant I-2: 保存校验规则
- Rule: 保存 qa_rule / reply_snippet 时，正文中每个 `${...}` token 必须满足：(a) key 在白名单内；(b) `nullable=true` 的 key 必须为 `${key|fb}` 形式且 fb 非空。违反任一 → 抛 `IllegalArgumentException`（走全局异常处理返回 400），消息包含违规 token 原文。转义写法不支持（本系统正文不存在字面 `${` 的合法场景）。
- Applies to: `QaRuleManagementService.createRule/updateRule`、`ReplySnippetService.create/update`。
- Violation consequence: 拼错的占位符入库并字面外发；可空变量无兜底导致句子断裂（线上 researchFields 大量为空）。
- 来源: original

### Invariant I-3: 预览同源
- Rule: 前端所有"渲染后效果"展示必须经 `POST /api/qa/render-preview`（入参 `contactId` 或 `orcidId+level` + `text`，实现直接调 `MailVariableService.renderForContact`），与发送 seam 同一实现；预览端点纯只读，无 `@Transactional`、无 save/send（来源: K-preview-mirrors-pipeline）。
- Applies to: 新端点、`app.js` 预览逻辑。
- Violation consequence: 预览与实发漂移，运营据错误预览改文案。
- 来源: K-composed-reply-order-contract, K-preview-mirrors-pipeline

### Invariant I-4: 前端校验只是体验层
- Rule: 前端失焦标红与保存按钮禁用不替代后端校验；后端 I-2 是最终闸门，接口直调（绕过 UI）同样被拒。
- Applies to: 两个后端 service 校验 + `app.js` 编辑器组件。
- Violation consequence: API 直调或前端 bug 绕过校验，脏占位符入库。
- 来源: original

## 现状审计

### qa_rule 写路径
1. `QaRuleManagementService.createRule()`（57 行）/ `updateRule()`（63 行）— 唯一运行时写入口，共用 `validate`（92–99 行现有校验）。
2. Flyway 迁移（V3/V38/V52/V57/V63/V65…）— 种子与修订，不走运行时校验（I-2 不覆盖，靠评审）。
- 读路径: `QaMatchService`（匹配+组装）、`MailComposeTemplateService.resolveBlocks`（QA_RULE block）、`LlmStitchService`、前端 QA 管理视图 `/api/qa/rules`。

### reply_snippet 写路径
1. `ReplySnippetService.create()`（45 行）/ `update()` — 唯一运行时写入口。
- 读路径: `resolveManualFrame()`（组装台 frame 三消费者，K-manual-frame-three-consumers）、`MailComposeTemplateService.resolveBlocks`（REPLY_SNIPPET block，含 variantGroup 变体选择）。
- 交互点: SALUTATION 片段将承载 `Dear Dr. ${expertFamilyName|Professor},` —— 变体组内每个片段都要各自过 I-2 校验。

### 前端编辑点（app.js）
1. QA 管理视图规则编辑表单（reply_body textarea）。
2. 片段管理编辑表单（content textarea）。
3. 组装台自由文本框（K-audit-free-text-topic 记录的 free-text 路径）。
- 现有预览: 组装台确定性预览 `buildDeterministicComposedPreview` 为 JS 拼接（顺序契约已治理），**不含**变量渲染 → 本计划在其输出上追加调用 render-preview 端点（I-3），JS 不做替换。

## 实现方案

### Task 1: 后端元数据 + 预览端点 [I-1, I-3]
- `QaRuleManagementController`:
  - `GET /api/qa/template-variables-meta` → `mailVariableService.variableMetadata()`。
  - `POST /api/qa/render-preview` body `{text, orcidId?, contactId?}` → 解析 contact/profile 后 `renderForContact`；返回 `{rendered, fallbackKeys: [走了兜底的 key]}`（fallbackKeys 供前端黄底标注；实现：渲染前后对比各 nullable key 值是否为空）。只读，无副作用。

### Task 2: 保存校验 [I-2, I-4]
- 新增共享校验函数（放 `MailVariableService.validatePlaceholders(text)`，返回违规列表）；`QaRuleManagementService.validate` 与 `ReplySnippetService.create/update` 各加一行调用。
- 正则提取 `\$\{([^}]*)\}`，按 I-2 (a)(b) 判定。

### Task 3: 前端变量插入条组件 [I-1, I-4]
- `index.html`: 三个编辑点 textarea 上方各加 `<div class="var-chip-bar" data-var-target="...">`。
- `app.js`: 组件初始化时拉一次 meta 接口缓存到 `state.variableMeta`；渲染 chip（label 显示中文名，title 显示 key/example）；点击按光标位置 `setRangeText` 插入 `${key}` 或 `${key|}`（nullable，`selectionStart` 定位到 `|` 后）。
- 失焦校验: 正则扫 token 对照 `state.variableMeta`，非法项在提示条列出并禁用保存按钮。

### Task 4: 真数据预览 [I-3]
- 编辑器旁"以专家预览"：输入/选择 orcidId（默认取当前组装台上下文的 contact），调 render-preview，弹层回显 `rendered`，`fallbackKeys` 对应文案黄底提示"该专家此字段为空，将使用兜底文案"。
- 组装台确定性预览输出追加同一端点渲染（保持 K-composed-reply-order-contract：拼接顺序仍由既有 JS 逻辑决定，渲染只做变量替换）。

### Task 5: 样式与测试
- `styles.css`: chip 条、非法 token 标红提示、fallback 黄底。
- `QaRuleManagementServiceTest` / `ReplySnippetServiceTest`: 非法 key 拒绝、nullable 无兜底拒绝、合法通过、存量不回溯（update 仅校验新文本）。

## 变更文件清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `qa/controller/QaRuleManagementController.kt` | meta + render-preview 端点 |
| 2 | `qa/service/QaRuleManagementService.kt` | 保存校验 |
| 3 | `reply/service/ReplySnippetService.kt` | 保存校验 |
| 4 | `mail/service/MailVariableService.kt` | validatePlaceholders + fallbackKeys 支持 |
| 5 | `src/main/resources/static/app.js` | chip 条/校验/预览 |
| 6 | `src/main/resources/static/index.html` | 三个编辑点挂载 |
| 7 | `src/main/resources/static/styles.css` | 组件样式 |
| 8 | `qa/service/QaRuleManagementServiceTest.kt`（test） | 校验用例 |
| 9 | `reply/service/ReplySnippetServiceTest.kt`（test） | 校验用例 |

（9 文件，2 个子系统：后端校验/端点 + 前端编辑器）

## 验收标准

- I-1: `app.js` 中 grep 不到硬编码变量名数组；停用某变量（改 `variableMetadata`）后前端 chip 与校验自动同步。
- I-2: 单测四象限（非法 key / nullable 无兜底 / 合法含兜底 / 无占位符）；API 直调（curl）保存非法占位符返回 400 且消息含违规 token。
- I-3: 预览端点返回值与 Plan A 发送 seam 对同一 (text, expert) 的渲染输出逐字节一致（单测直接断言两个调用）；端点无写操作。
- I-4: 前端禁用保存的同时后端仍独立拒绝（测试绕过 UI 直调）。
- 手工回归: 三个编辑点插入→校验→预览→保存全流程；组装台预览与实发一致（发一封测试信对比）。
