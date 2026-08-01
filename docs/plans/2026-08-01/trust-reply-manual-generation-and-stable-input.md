# 可信回复手动生成、整合补全与输入稳定性开发计划

> 使用 `create-p` 编写。计划日期：2026-08-01。执行后使用 `verify-p` 独立验证。

## 需求描述

### 可观察结果

1. AI 训练与收发邮件继续共用 `TrustReplyWorkbench`，两处行为一致：打开详情/挂载工作台时只执行服务端 `bootstrap`、事实匹配和现有无依据问题翻译，不自动发起任何 AI 回答生成。
2. `GROUNDED（依据充分）` 项默认折叠并显示“待生成”。用户可展开后逐项点击“AI 生成回答”，也可在全部人工必处理项完成后点击“服务端整合”，由该次显式点击统一生成尚无可采用版本的 `GROUNDED` 项，再继续服务端整合。
3. 逐项生成按钮永久保留。逐项生成后仍需“采用此版本”；点击“服务端整合”本身可视为对当前有效 `GROUNDED` 活动版本的显式采用。
4. `PARTIAL（部分有据）` 与 `UNSUPPORTED（无依据）` 不因“服务端整合”自动采用 AI 版本；必须由用户逐项生成并采用、按说明生成并采用、确认待补充或省略。未完成时整合入口明确提示并保持不可执行。
5. 收发邮件的“自动回复预览”初始为“未生成”，不随详情打开自动请求；用户点击“生成自动回复预览”后才调用现有只读预览接口。
6. “可信回复”中的回答说明/调整要求输入框连续输入时，不再逐字符重写卡片头部、回答区、操作区和整合摘要；焦点、光标、相邻卡片与翻译展开状态保持稳定。
7. 生成、采用、服务端整合都不直接发送邮件。LIVE 模式仍只把整合结果采用到人工回复编辑器；SIMULATION 模式仍只进入训练评估。

### 必须保持不变

- `bootstrap` 的精确来源校验、`sourceVersion` / `evidenceSetVersion` 身份校验、事实选择与 `request → facts → coverage` 结构不变。
- `ADJUST_ITEM`、`FULL_DRAFT`、SSE 进度、稳定 `generationId`、单次/总 TTL、取消与 stale fail-closed 语义不变。
- `resolvedVersionId` 仍是唯一“已采用”事实；`draftHandling`、输入框文字和 `activeVersionId` 仍只是下一次生成/预览意图。
- 服务端仍按 canonical request 列表校验全部 `lockedItems`、重物化版本、校验 claims 并整合；前端不得本地拼接正文、去重、重排或截断。
- `UNSUPPORTED` 问题的现有自动翻译保留；回答翻译仍由用户点击触发，翻译不得触发回答生成。
- LIVE 的最终采用、人工编辑、发送前检查和人工发送链路不变；禁止把任何生成/预览结果直接送入发送接口。
- SIMULATION 的 `onComplete → 训练评估` 与 LIVE 的 `onComplete → 采用到人工回复` 宿主适配器不互串。
- 自动回复预览仍复用当前只读接口和真实预览管线，不写库、不改处理状态、不发送邮件。
- `operator_action_log` 的 schema、写入服务和读取页面不变；仅改变 LIVE 初始 AI draft 审计产生的时机：打开详情不再产生，用户点击“服务端整合”触发 `FULL_DRAFT` 时才可能产生。

### 范围外

- 不新增或修改 Kotlin Controller、Service、DTO、数据库迁移、表结构与仓储接口。
- 不修改提示词、模型选择规则、QA 事实匹配、coverage 判定、版本/claims 生成算法。
- 不给 `PARTIAL` / `UNSUPPORTED` 增加自动采用或自动省略策略。
- 不新增“全部生成”按钮，不恢复页面打开后的隐式生成。
- 不改邮件发送、自动发送、人工发送、训练评估持久化、操作日志格式。
- 不进行工作台视觉重构，不新增 CSS 类，不清理本次范围外的既有 inline style。

## 关键不变量

### Invariant I-1：挂载只 bootstrap，绝不隐式生成

- Rule：`TrustReplyWorkbench.mount()` 在 SIMULATION 与 LIVE 中只调用 `bootstrap()`；`applyBootstrap()` 只落地服务端快照、渲染并触发既有 `UNSUPPORTED` 问题翻译。它不得调用 `generateAll`、`FULL_DRAFT`、`ADJUST_ITEM` 或任何等价生成入口。
- Applies to：`mount`、`applyBootstrap`、来源/事实刷新后的再次 bootstrap。
- Violation consequence：运营仅查看邮件就消耗模型、产生 LIVE draft 审计，并把尚未确认的内容变成已采用版本。
- 来源：original + `K-shared-workbench-fixed-mode-host-adapter`

### Invariant I-2：AI 回答只有两个显式用户触发点

- Rule：唯一允许调用生成 SSE 的交互是：单项“AI 生成回答”触发 `ADJUST_ITEM`；“服务端整合”触发必要的 `FULL_DRAFT`。翻译、展开/收起、版本选择、输入、详情打开、预览打开均不得触发回答生成。
- Applies to：工作台 click/change/input 事件分发及自动回复预览。
- Violation consequence：出现隐藏成本、不可解释副作用，且 AI 训练与收发邮件不一致。
- 来源：original

### Invariant I-3：整合前自动补全仅限尚缺版本的 GROUNDED

- Rule：点击“服务端整合”时按 canonical request 顺序划分三组：
  1. 已有有效 `resolvedVersionId`：保持不变；
  2. 未 resolved 但有身份有效、结构可序列化的 `GROUNDED activeVersionId`：本次点击视为显式采用，不再生成；
  3. 既无有效 resolved、也无有效 active 的 `GROUNDED`：加入 `missingGroundedKeys`，一次 `FULL_DRAFT` 后仅从结果中按该 allowlist 合并。
- `FULL_DRAFT` 响应即使包含 `PARTIAL` 版本，前端也必须丢弃这些非 allowlist 结果，不写入、不设 active、不设 resolved。每个 `missingGroundedKey` 必须恰有一个身份有效且 handling 被该项允许的版本，否则本次整合失败。
- Applies to：整合 readiness、完整生成结果校验与 merge。
- Violation consequence：覆盖人工选择、自动采用部分有据回答，或用不完整版本进入整合。
- 来源：original + `K-trust-reply-resolved-version-single-source`

### Invariant I-4：人工必处理项是整合硬前置条件

- Rule：任一 `PARTIAL` / `UNSUPPORTED` 缺少可序列化的 resolved version 时，“服务端整合”不可执行；界面显示“待人工处理 N 项”。不得为了让整合继续而自动生成、自动采用或自动省略这些项。
- Applies to：summary readiness、assemble click guard。
- Violation consequence：无充分依据的内容绕过人工决策进入正式回复。
- 来源：original + `K-ai-generation-observability-not-send-gate`

### Invariant I-5：生成与整合必须串行且 fail-closed

- Rule：服务端整合流程固定为 `readiness → 锁定有效 GROUNDED active version →（必要时）FULL_DRAFT → allowlist merge → 重新构造完整 lockedItems → POST /assemble`。生成失败、取消、stale、身份不匹配、缺失/重复版本或 LLM 不可用时，禁止调用 `/assemble`；保留既有人工版本并允许重试。
- Applies to：`assemble`、完整生成 helper、取消与错误分支。
- Violation consequence：服务端收到不完整/混源的锁定项，或用户取消后仍继续整合。
- 来源：original + `K-llm-attempt-total-budget-cancel`

### Invariant I-6：整合输入仍由 resolved version 单源派生

- Rule：完成补全后，`lockedItems` 必须由 `state.requests.map(serializeResolvedVersion)` 按原顺序生成；每项携带原 `versionId`、handling、answer、claims、model、generationKind、source/evidence version、operator instruction/hash。不得从 textarea、active version 或 `FULL_DRAFT.rawDraftText` 直接构造整合请求。
- Applies to：`serializeResolvedVersion`、assemble payload。
- Violation consequence：预览状态与真正被服务端重物化的版本不一致。
- 来源：`K-trust-reply-resolved-version-single-source` + `K-locked-item-assembly-list-not-set`

### Invariant I-7：两种宿主共享同一行为，只保留固定适配差异

- Rule：生成触发、折叠、输入稳定性、readiness、merge 和整合全部只实现于 `trust-reply-workbench.js`。`app.js` 中两个 mount 点只保留固定 mode/source/context/auth/onChange/onComplete；不得复制一套训练专用或邮件专用生成逻辑。
- Applies to：`mountAiTrainingTrustReply`、`mountLiveTrustReply`、共享组件。
- Violation consequence：同一操作在 AI 训练与收发邮件产生不同副作用。
- 来源：`K-shared-workbench-fixed-mode-host-adapter`

### Invariant I-8：输入状态每键更新，DOM 只在真实失效时更新一次

- Rule：每个 `input` 事件都同步截断后的 `request.instruction`；只有该项原本存在 active/resolved version 或当前存在 assembly 时，才执行一次决策失效和必要的局部 UI 同步。第一个字符清掉旧 active/resolved/assembly 后，后续字符不得继续写 `item-header.innerHTML`、`answer.innerHTML`、`item-actions.innerHTML`、`summary.innerHTML`，也不得替换 textarea 节点。
- `invalidateAssembly()` 只有在 assembly 确实从非空变为空时才调用宿主 `onChange`。
- Applies to：`onInput`、`invalidateDecision`、`invalidateAssembly`、`syncInstructionUi`。
- Violation consequence：逐键闪烁、光标/焦点抖动、训练评估面板反复清空。
- 来源：original

### Invariant I-9：默认折叠策略与状态文案可区分风险

- Rule：bootstrap 和 stale/reset 后均为：`GROUNDED` 默认折叠、未 resolved 时标“待生成”；`PARTIAL` / `UNSUPPORTED` 默认展开、未 resolved 时标“待处理”；resolved/omit 仍标“已处理/已省略”。
- Applies to：`requestFromCoverage`、`resetVersions`、`renderRequestHeader`。
- Violation consequence：高风险项被隐藏，或依据充分项看起来已经处理。
- 来源：original

### Invariant I-10：自动回复预览必须手动触发且保持只读

- Rule：`showUnmatchedDetail` 不得调用 `loadAutoReplyPreview`。初始状态固定为“未生成”；只有 `preview-auto-reply` click handler 调用现有 GET。请求期间/成功/失败只更新当前详情页对应的预览 DOM，不改变工作台状态、邮件状态或发送状态。
- Applies to：收发邮件详情 HTML、`handleUnmatchedAction`、`loadAutoReplyPreview`。
- Violation consequence：查看详情产生额外计算，且用户无法辨认预览是否由自己触发。
- 来源：`K-preview-mirrors-pipeline` + `K-preview-runtime-gates-visible`

### Invariant I-11：审计时机随显式 FULL_DRAFT 移动，内容与读写契约不变

- Rule：不得新增操作日志写入口。LIVE `FULL_DRAFT` 仍由 `TrustReplyWorkbenchService.generate → AiReplyReviewAuditService.recordInitialDraft → OperatorActionLogService.record` 写 `AI_REPLY_DRAFT_READY/NEEDS_REVIEW/BLOCKED`；本改动只保证该链不再因 mount 发生，而只可能由用户点击服务端整合发生。预览与 bootstrap 仍无写路径。
- Applies to：前端生成调用时机及回归测试/人工审计。
- Violation consequence：打开邮件即产生“AI 草稿”操作记录，日志无法代表用户动作。
- 来源：`K-ai-generation-observability-not-send-gate`

### Invariant I-12：静态资源版本必须同值更新

- Rule：`index.html` 中 `styles.css`、`trust-reply-workbench.js`、`app.js` 三个 query version 必须统一改为 `20260801-trust-reply-manual-generation-01`，对应契约测试同步更新。
- Applies to：静态资源加载与缓存测试。
- Violation consequence：浏览器混用旧组件/新宿主，表现与测试环境不一致。
- 来源：`K-ui-removal-retires-obsolete-contract-tests`

## 样式契约

本计划是交互状态修改，不新增视觉体系；`styles.css` 不进入变更清单。

### Style S-1：工作台卡片

- 复用：`.compose-panel.trust-reply-item`、`.trust-reply-item-head`、`.trust-reply-coverage`、`.badge`、`.button.small.secondary`、`.trust-reply-item-actions`、`.button.primary`。
- DOM 形态保持：

```html
<article class="compose-panel trust-reply-item" data-role="item" data-coverage="GROUNDED">
  <div class="trust-reply-item-head" data-role="item-header">
    <span class="trust-reply-item-index">1</span>
    <div class="trust-reply-item-title">…<button class="button small secondary">展开</button></div>
    <span class="badge">待生成</span>
  </div>
  <div data-role="item-body" hidden>…<button class="button primary">AI 生成回答</button></div>
</article>
```

- 状态：GROUNDED 初始 `hidden`；PARTIAL/UNSUPPORTED 初始不 hidden。resolved 使用既有 `data-locked="true"` 与 `.badge.ok`；pending 使用既有“生成中…”和禁用态。
- 禁止：新增颜色、阴影、圆角、图标、动画、卡片层级或 inline style。

### Style S-2：整合摘要

- 复用：`.trust-reply-summary.compose-panel`、`.trust-reply-lock-hint`、`.trust-reply-progress`、`.trust-reply-final-actions`、`.button.primary/.secondary`。
- 摘要文案固定提供三类计数：`已处理 X/Y · 待生成 G 项 · 待人工处理 M 项`；为 0 的类别可省略尾段，但不得把“待生成”计入“已处理”。
- 主按钮状态：
  - `M > 0`：文案“服务端整合”，disabled，hint 明确待人工处理数量；
  - `M = 0, G > 0`：文案“生成有据回答并整合”；
  - `M = 0, G = 0`：文案“服务端整合”；
  - FULL_DRAFT 期间：“生成并整合中…”；assemble POST 期间：“整合中…”。
- 进度条只按 resolved 数量计算，继续使用真实计数；不得增加模拟百分比或倒计时。
- 来源：`K-ai-stream-progress-no-fake-percent`。

### Style S-3：输入稳定性

- textarea 继续使用 `.trust-reply-field textarea` 的既有边框、focus ring、字号、间距和 `maxlength=500`。
- 输入时不得增加 loading、debounce 动画或闪烁提示；必要的一次决策失效沿用现有回答区“尚未生成版本”和按钮状态。
- textarea DOM 节点、selectionStart/selectionEnd、focus 必须保留。

### Style S-4：自动回复预览初始态

- 复用现有 `.auto-reply-preview-section`、`.reply-workflow-status`、`.text-muted`、`.button`、`#autoReplyPreviewResult`；本计划不新增 class。
- 初始文案：meta=`点击按钮后分析来信意图与回复规则`，status=`未生成`，button=`生成自动回复预览`，result=`尚未生成自动回复预览`。
- 点击后沿用现有 loading/result/error 视觉；成功后按钮变为“重新生成预览”。现有两处 inline style 保留但不扩散，不在本计划内重构。

### 既有视觉基线

- 主色/hover：`#2563eb` / `#1d4ed8`；success/error/warning：`#059669` / `#e11d48` / `#d97706`。
- 圆角：`--radius-sm: 7px`、`--radius-md: 10px`；边框使用既有 `rgba(15,23,42,...)`；阴影继续复用 `--shadow-sm`。
- 响应式继续使用现有 `.trust-reply-*` media rules；不新增断点。

## 现状审计

### 1. 浏览器内共享工作台状态

- 资源：`src/main/resources/static/trust-reply-workbench.js` 的 `createInstance` closure；每个 mount 独立保存 source/evidence/model/TTL/requests/generation/assembly/controller。
- 当前写路径：
  - `applyBootstrap` 重建 `state.requests`；随后对 UNSUPPORTED 调翻译；目前还通过 `initialFullDraftSourceVersions` 自动调用 `generateAll()`。
  - `generateAll → applyGenerationResult → applyInitialVersions` 会替换每项 `versions`，并自动 resolved GROUNDED/PARTIAL。
  - `adjustItem` 追加单项 version；除 OMIT 外不自动 resolved。
  - `toggleResolve` 负责采用/取消采用；`invalidateDecision` 清 resolved；`invalidateAssembly` 清 assembly 并无条件调用 host `onChange`。
  - `assemble` 仅在 `canAssemble()` 为真时发送完整 lockedItems。
- 当前读路径：`renderRequest*`、`renderSummary`、`canAssemble`、`serializeResolvedVersion`、两个宿主 `onComplete`。
- 本计划交互点：删除 mount 后生成；把必要的完整生成纳入 `assemble` 的显式点击事务；由 allowlist merge 防止覆写已有人工版本。

### 2. 输入事件与 DOM 写路径（闪烁根因）

- `onInput` 每个字符都执行：更新 instruction → 清 `activeVersionId` → `invalidateDecision` → `syncInstructionUi`。
- `syncInstructionUi` 每次分别重写 `item-header.innerHTML`、`answer.innerHTML`、`item-actions.innerHTML`、`summary.innerHTML`；`invalidateAssembly` 又每次调用训练宿主 `onChange`。
- 根因不是外部监听器或后端请求，而是逐字符的重复 innerHTML 写入和宿主回调。
- 修复边界：input state 仍逐键同步；仅“第一次使现有 decision/assembly 失效”时局部同步一次，后续字符零 innerHTML 写入。

### 3. 服务端工作台 API 与版本权威

- `POST /api/trust-reply/workbench/bootstrap`：读取精确 source，确定 facts/coverage/sourceVersion/evidenceSetVersion；不做 AI 回答生成。
- `POST /api/trust-reply/workbench/generations/stream`：`ADJUST_ITEM` 生成单项，`FULL_DRAFT` 生成完整草稿；沿用 generationId、SSE progress、attempt/total TTL、取消与 stale 检查。
- `POST /api/trust-reply/workbench/generations/{id}/cancel`：best-effort 取消。
- `POST /api/trust-reply/workbench/assemble`：要求 canonical requests 与 lockedItems 一一对应，服务端重物化 version 并校验 claims，再按原顺序整合。
- 决策：不改 API。前端在用户点击“服务端整合”后复用现有 `FULL_DRAFT`，但只消费 `missingGroundedKeys` 对应版本。

### 4. `operator_action_log` 持久化与审计影响

- 表：`operator_action_log`，迁移源 `V19__add_operator_status_and_action_log.sql`；关键字段含 target/expert/inbound/action type/summary/before/after/operator/note/created_at。
- 唯一通用写 seam：`OperatorActionLogService.record → operatorActionLogRepository.save`。
- 与本计划相关的写路径：LIVE、无 turns 的 `FULL_DRAFT` 由 `TrustReplyWorkbenchService.generate` 调 `AiReplyReviewAuditService.recordInitialDraft`，写 `AI_REPLY_DRAFT_READY`、`AI_REPLY_DRAFT_NEEDS_REVIEW` 或 `AI_REPLY_DRAFT_BLOCKED`。
- 读路径：操作日志搜索接口/收发邮件详情日志、`findLatestAiDraftByInboundProcessingId`、QA audit 读取。
- 影响：不改字段和内容；只把写入时机从“详情 mount 后自动 FULL_DRAFT”移动到“用户点击服务端整合后 FULL_DRAFT”。若无需 FULL_DRAFT（全部已有有效版本），本次整合不新增初始 draft 日志。

### 5. AI 训练与 LIVE 宿主

- `mountAiTrainingTrustReply`：固定 `SIMULATION + TRAINING_MAIL`；`onChange` 清训练评估面板；`onComplete` 渲染评估。
- `mountLiveTrustReply`：固定 `LIVE + LIVE_INBOUND`；`onComplete` 只把 assembly 采用到人工回复编辑器。
- 两者都依赖同一 shared component。本计划不在宿主复制 readiness/generation/merge 逻辑。

### 6. 自动回复预览资源

- 详情 HTML 当前初始就显示“生成中…/加载预览中…/重新预览”，`showUnmatchedDetail` 末尾无条件调用 `loadAutoReplyPreview(id)`。
- 手动入口已经存在：`handleUnmatchedAction` 的 `preview-auto-reply` 分支。
- 只读接口：`GET /api/mail/unmatched-inbound/{id}/auto-reply-preview`；前端仍通过 `loadAutoReplyPreview` 处理 loading/result/error 和 detailContext 防串页。
- 影响：仅删除自动调用并改初始/按钮状态；不改服务端预览管线。

### 7. 前端样式与静态缓存

- 工作台所需卡片、coverage、字段、textarea focus、actions、summary、progress、responsive 样式均已存在于 `styles.css`，无需新增选择器。
- `index.html` 当前对 CSS、共享工作台 JS、app.js 使用同一个旧版本键；契约测试要求三者一致。本计划统一更新为 I-12 指定值。

### 8. 现有测试与测试入口

- Maven `pom.xml` 已通过 Node test runner 执行 `src/test/js/*.test.js`；仓库无 npm test script，本计划直接使用 `node --test` 与 `mvn test`。
- `trustReplyWorkbenchSharedMount.test.js` 当前锁定“mount 自动生成并采用 GROUNDED”，必须改成新语义，并扩展输入 DOM 写入计数。
- `trustReplyWorkbench.test.js` 是共享组件静态契约，需移除旧自动生成断言并锁定显式整合触发。
- `unmatchedQaReplySource.test.js` 当前锁定详情自动加载预览，必须反转为手动触发。
- `batchSendTaskConsoleVisualFix.test.js` 锁定三项静态资源版本键，必须同步更新。
- `aiReplyLoadingFeedback.test.js` 等其余测试不改源码，但由全量 JS 与 Maven 回归验证。

### 9. 跨模块交互点

| 交互点 | 上游 | 下游 | 本计划约束 |
|---|---|---|---|
| IP-1 | bootstrap response | 浏览器 request/coverage state | 只初始化和翻译，不生成（I-1） |
| IP-2 | 服务端整合 click | FULL_DRAFT SSE | 仅显式触发，保留 TTL/cancel/stale（I-2/I-5） |
| IP-3 | FULL_DRAFT itemVersions | resolved versions | 按 missing GROUNDED allowlist 合并，不覆盖人工项（I-3） |
| IP-4 | resolved versions | `/assemble` lockedItems | canonical list、单源序列化、服务端重物化（I-6） |
| IP-5 | LIVE FULL_DRAFT | `operator_action_log` | 打开不写；显式生成才沿用旧写链（I-11） |
| IP-6 | shared assembly | training/live onComplete | 训练评估与人工编辑各自固定（I-7） |
| IP-7 | instruction input | active/resolved/assembly + DOM | 首次真实失效才局部同步（I-8） |
| IP-8 | preview click | 只读 GET + preview DOM | 无自动调用，无状态/发送副作用（I-10） |

## 实现方案

### Task 1：先改共享组件行为测试，建立失败基线（I-1～I-9）

文件：`src/test/js/trustReplyWorkbenchSharedMount.test.js`

1. 删除/重写“mount 自动完整生成并采用 GROUNDED”的旧用例。
2. 分别 mount SIMULATION 与 LIVE，bootstrap 返回含 GROUNDED/PARTIAL/UNSUPPORTED；断言：
   - 只出现 bootstrap 与 UNSUPPORTED 翻译请求；
   - `generations/stream` 调用数为 0；
   - GROUNDED collapsed + “待生成”，PARTIAL/UNSUPPORTED expanded + “待处理”；
   - 单项“AI 生成回答”仍存在。
3. 保留并强化单项路径：点击 GROUNDED 的按钮只发送一次 `ADJUST_ITEM`；结果成为 active 但未 resolved，点击“采用此版本”后才 resolved。
4. 新增整合路径：先手动 resolved PARTIAL/UNSUPPORTED；点击主按钮后断言 `FULL_DRAFT` 先发生、随后 `/assemble` 发生，且 lockedItems 顺序等于 bootstrap canonical order。
5. 让 FULL_DRAFT 同时返回 GROUNDED 与 PARTIAL 版本；断言只合并 `missingGroundedKeys`，原 PARTIAL/UNSUPPORTED versions、active、resolved、instruction 全部不变。
6. 覆盖已有 active GROUNDED：点击整合直接采用其有效 active version，不为该 key 再生成；已有 resolved GROUNDED 完全不变。
7. 覆盖 fail-closed：人工项未完成、LLM 失败、cancel、stale、返回缺项、重复项、错误 requestKey、错误 source/evidence version 时不调用 `/assemble`，且既有人工版本不丢失。
8. 扩展 FakeElement，记录指定节点 `innerHTML` setter 次数和 textarea identity/focus/selection；连续输入至少 20 个字符，断言 instruction 最终值正确、首次失效最多一次局部写入、后续逐键零写入、textarea 与相邻卡片未替换。

### Task 2：重构共享工作台的生成触发与整合事务（I-1～I-7、I-9）

文件：`src/main/resources/static/trust-reply-workbench.js`

1. 删除 `state.initialFullDraftSourceVersions` 及 `applyBootstrap` 中自动 `generateAll()` 分支；保留 UNSUPPORTED question translation。
2. 统一初始/重置折叠：`request.expanded = request.coverage !== "GROUNDED"`；header badge 对 unresolved GROUNDED 输出“待生成”，其余 unresolved 输出“待处理”。
3. 提取纯 readiness helper，按 I-3/I-4 返回：
   - `resolvedItems`；
   - `adoptableGrounded`；
   - `missingGroundedKeys`；
   - `unresolvedManualKeys`；
   - `canStartAssembly` 与禁用原因。
4. 把结构/身份校验抽成可供 active/resolved 共用的 version validator/serializer；`serializeResolvedVersion` 继续是最终 lockedItems 唯一入口。
5. 将现有 `generateAll` 改成内部“为本次整合生成缺失 GROUNDED”步骤：
   - 复用原 `makeGenerationPayload(null, ..., generationId)`、SSE、progress、TTL、controller、cancel/stale；
   - 调用前冻结 `missingGroundedKeys`、sourceVersion、evidenceSetVersion、bootSeq；
   - 响应先做全局 source/evidence/requestKey 身份校验，再只对 allowlist 取每项唯一有效 version；
   - append（按 versionId 防重复）而非 replace 现有 `request.versions`；只给 allowlist 项设 active/resolved/collapsed；忽略响应中的 PARTIAL 与不在 allowlist 的合法版本；
   - 任一 allowlist key 缺失/重复/handling 不允许即抛错，停止流程。
6. 重写 `assemble()` 为 I-5 串行事务；在 FULL_DRAFT 成功后重新计算 readiness 和 canonical lockedItems，确认全部可序列化才调用 `/assemble`。生成阶段与整合阶段分别设置真实 stage/message；错误、取消与 stale 路径不递归、不继续 POST。
7. `renderSummary` 使用 readiness 计数与 S-2 文案/禁用态；保留逐项按钮和现有 complete 按钮。
8. 不在宿主新增生成逻辑，不改 API path/payload schema，不改 `onComplete`。

### Task 3：消除逐键 DOM 重写（I-8）

文件：`src/main/resources/static/trust-reply-workbench.js`

1. 让 `invalidateAssembly()` 返回 `hadAssembly`，仅 `hadAssembly=true` 时调用 `state.onChange`。
2. 让 `invalidateDecision(request)` 返回本次是否真的清除了 resolved/assembly；调用者据此决定是否同步 UI。
3. `onInput` 每次先写入截断后的 instruction；记录 `hadActive` / `hadResolved` / `hadAssembly`：
   - 若三者全无，只更新内存，不调用 `syncInstructionUi`；
   - 若任一存在，清 active/resolved/assembly，并仅当次调用 `syncInstructionUi`；
   - 第一次清空后后续 keystroke 只写内存。
4. `syncInstructionUi` 继续只更新当前卡片必要区域和 summary，不触碰 textarea；确保 focused node 与 selection 不变。
5. handling/version/fact 变化仍按现有 change/click 路径立即重渲染，因为它们不是逐键输入。

### Task 4：把自动回复预览改为纯手动加载（I-10）

文件：`src/main/resources/static/app.js`

1. 将详情 HTML 的 meta/status/button/result 改成 S-4 初始文案。
2. 删除 `showUnmatchedDetail` 末尾的 `loadAutoReplyPreview(id).catch(...)`。
3. 保留 `preview-auto-reply` click handler 作为唯一调用入口。
4. `loadAutoReplyPreview` 开始时禁用当前记录按钮并显示“生成中…”；成功/失败后恢复，成功文案改“重新生成预览”。继续用 `detailContext.id` 丢弃串页结果。
5. 不改 preview GET、response render、阻断提示、附件提示及任何后端代码。

### Task 5：更新静态契约测试与缓存版本（I-1、I-2、I-10、I-12）

文件：

- `src/test/js/trustReplyWorkbench.test.js`
- `src/test/js/unmatchedQaReplySource.test.js`
- `src/test/js/batchSendTaskConsoleVisualFix.test.js`
- `src/main/resources/static/index.html`

步骤：

1. 共享组件静态契约断言：没有独立“全部生成”按钮；bootstrap 不调用完整生成；FULL_DRAFT 只位于用户触发的 assemble 流程；固定 mode/source mount 仍成立。
2. 收发邮件静态契约断言：初始“未生成/生成自动回复预览”；`showUnmatchedDetail` 无自动 preview call；click handler 是唯一调用点。
3. 将三项静态资源 query version 同时更新为 `20260801-trust-reply-manual-generation-01`，同步缓存契约期望。
4. 删除只服务旧隐式生成/隐式预览语义的断言，不保留“兼容旧行为”的分支。

### Task 6：自动验证与回归（I-1～I-12、S-1～S-4）

1. 语法检查：

```bash
node --check src/main/resources/static/trust-reply-workbench.js
node --check src/main/resources/static/app.js
```

2. 定向测试：

```bash
node --test \
  src/test/js/trustReplyWorkbenchSharedMount.test.js \
  src/test/js/trustReplyWorkbench.test.js \
  src/test/js/unmatchedQaReplySource.test.js \
  src/test/js/batchSendTaskConsoleVisualFix.test.js
```

3. 全量前端契约：

```bash
node --test src/test/js/*.test.js
```

4. 项目回归：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH \
mvn test
```

5. 补丁卫生：

```bash
git diff --check
```

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/resources/static/trust-reply-workbench.js` | 修改 | 去掉 mount 自动生成；整合时按需补全 GROUNDED；修复逐键 DOM 重写；更新状态文案 |
| 2 | `src/main/resources/static/app.js` | 修改 | 自动回复预览改为手动加载及按钮状态管理 |
| 3 | `src/main/resources/static/index.html` | 修改 | 三项静态资源统一缓存版本 |
| 4 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 修改 | 两种模式、逐项生成、整合补全、fail-closed、输入稳定性行为测试 |
| 5 | `src/test/js/trustReplyWorkbench.test.js` | 修改 | 共享组件静态生成触发契约 |
| 6 | `src/test/js/unmatchedQaReplySource.test.js` | 修改 | 收发邮件预览手动触发契约 |
| 7 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改 | 静态资源缓存键契约 |

范围：7 个文件，均为前端静态运行时/测试，1 个子系统；≤10 文件、≤2 子系统。无后端、数据库、CSS 变更。

## 验收标准

### 自动验收

- **AC-1（I-1/I-2/I-7）**：SIMULATION、LIVE mount 后 fetch 记录均为 bootstrap + 必要翻译，`generations/stream` 为 0；点击单项或服务端整合前不得出现 generation request。
- **AC-2（I-9/S-1）**：两种模式中 GROUNDED 初始 hidden 且徽标“待生成”；PARTIAL/UNSUPPORTED 展开且“待处理”；逐项“AI 生成回答”存在。
- **AC-3（I-2/I-6）**：单项点击发送 `operation=ADJUST_ITEM` 和对应 requestKey；生成后未 resolved，采用后 `serializeResolvedVersion` 才可输出该项。
- **AC-4（I-3/I-5/I-6）**：人工项全部 resolved 后，服务端整合点击按顺序产生一次必要的 `operation=FULL_DRAFT` 与一次 `/assemble`；lockedItems 与 canonical request 一一对应、顺序不变。
- **AC-5（I-3/I-4）**：FULL_DRAFT 返回的非 allowlist PARTIAL 版本不进入任何 request state；人工 versions/active/resolved/instruction 深比较不变。
- **AC-6（I-5）**：生成失败、取消、stale、身份错误、缺/重版本场景下 `/assemble` 调用数为 0，状态可重试，既有版本保留。
- **AC-7（I-8/S-3）**：连续 20 次 input 后 instruction 等于最终文本；旧 decision 的局部清理最多发生一次；textarea identity/focus/selection 与相邻卡片 identity 不变；宿主 onChange 只在真实 assembly 被清除时调用一次。
- **AC-8（I-10/S-4）**：详情渲染后 preview GET 为 0；初始文案正确；点击后恰好一次 GET，loading/success/error/重试文案正确。
- **AC-9（I-11）**：源码/行为测试证明 mount 与 preview 无 FULL_DRAFT；LIVE 服务端整合需要补全时仍走现有 FULL_DRAFT endpoint。后端 audit 文件无 diff。
- **AC-10（I-12）**：index 三项静态资源版本和契约测试期望全部等于 `20260801-trust-reply-manual-generation-01`。
- **AC-11（S-1～S-4）**：`styles.css` 无 diff；新增 DOM 不引入 class、inline style、颜色、动画或断点。
- **AC-12（全回归）**：两项 `node --check`、定向 Node tests、全量 `src/test/js/*.test.js`、`mvn test`、`git diff --check` 全部通过。

### 状态/副作用验收矩阵

| 场景 | 允许请求 | 禁止副作用 | 期望结果 |
|---|---|---|---|
| 打开 AI 训练邮件 | bootstrap、必要翻译 | AI 生成、audit、评估写入 | 工作台待处理 |
| 打开收发邮件详情 | bootstrap、必要翻译 | AI 生成、preview GET、audit、发送 | 工作台与预览均待用户操作 |
| 点击单项 AI 生成 | 单项 ADJUST_ITEM | 自动采用、assemble、发送 | 生成 active version，等待采用 |
| 点击服务端整合，仍有人工项 | 无生成/assemble | 自动处理 PARTIAL/UNSUPPORTED | disabled + 数量提示 |
| 点击服务端整合，仅缺 GROUNDED | 一次 FULL_DRAFT，随后 assemble | 覆盖人工项、直接发送 | 补全 GROUNDED 并返回 assembly |
| 生成取消/失败/stale | cancel/失败响应 | assemble、complete、发送 | fail-closed，可重试 |
| 点击预览 | 一次只读 GET | DB 写入、状态改变、发送 | 展示现有预览结果 |
| LIVE 点击“采用到人工回复” | 无发送请求 | 自动发送 | 仅填入人工编辑器 |

## 人工验收清单

执行环境：浏览器 DevTools 打开 Network，分别进入“AI 训练”和“收发邮件”。准备一封 coverage 同时含 GROUNDED、PARTIAL、UNSUPPORTED 的邮件；另准备一封仅含 GROUNDED 的邮件。

- [ ] **M-1 打开无隐式生成**：分别打开两处详情；Network 中只有 bootstrap 与必要 `/api/translate`，没有 `/generations/stream`；等待 10 秒仍不出现生成。
- [ ] **M-2 默认折叠/翻译**：GROUNDED 折叠、徽标“待生成”；PARTIAL/UNSUPPORTED 展开、“待处理”；UNSUPPORTED 问题译文仍自动出现；无 AI 回答。
- [ ] **M-3 逐项生成**：展开一个 GROUNDED，点击“AI 生成回答”；确认只有该项 ADJUST_ITEM，按钮随后为“采用此版本”，未点击采用前“已处理”计数不增加。
- [ ] **M-4 人工项闸门**：PARTIAL/UNSUPPORTED 尚未采用/省略时，服务端整合 disabled，摘要显示准确的“待人工处理 N 项”；不能通过点击绕过。
- [ ] **M-5 统一生成并整合**：完成所有人工项后点击“生成有据回答并整合”；观察一次 FULL_DRAFT 的真实 SSE 阶段，再观察 `/assemble`；原人工项文本、handling、版本选择不变，缺失 GROUNDED 变“已处理”。
- [ ] **M-6 无需重复生成**：给一个 GROUNDED 逐项生成但不点采用，再点击服务端整合；该 active 版本被本次显式操作采用，不为它重复生成。全部已有 resolved 时点击只调用 `/assemble`。
- [ ] **M-7 失败/取消**：生成阶段点“取消生成”或模拟失败；确认没有 `/assemble`，已有人工项仍在，可再次点击重试。
- [ ] **M-8 stale**：生成前后改变事实/来源版本；确认提示刷新、清理 stale 版本且不整合；确认后重新 bootstrap 仍不自动生成。
- [ ] **M-9 输入不闪烁**：在“回答说明/AI 调整要求”连续输入至少 20 个中文字符并移动光标插入；卡片头、回答区、按钮、摘要、相邻卡片不逐键闪烁，焦点与光标不跳，翻译展开状态不变。
- [ ] **M-10 决策失效正确**：先生成并采用版本/完成一次 assembly，再修改 instruction；第一次输入立即取消该项采用并清 assembly，后续输入不反复触发；训练评估面板仅清一次。
- [ ] **M-11 预览手动触发**：打开收发邮件详情，预览显示“未生成”，Network 无 preview GET；点击按钮后才请求，成功显示结果且按钮变“重新生成预览”。
- [ ] **M-12 预览串页/失败**：请求中切换另一封邮件或模拟失败；旧结果不写入新详情，失败状态可手动重试，邮件状态与操作日志不变。
- [ ] **M-13 LIVE 安全边界**：整合完成点击“采用到人工回复”，仅人工编辑器被填充；未出现发送请求，仍需人工发送和既有 preflight。
- [ ] **M-14 SIMULATION 边界**：整合完成点击“完成模拟并评估”，只出现训练评估；不填 LIVE 编辑器、不发送。
- [ ] **M-15 审计时机**：LIVE 仅打开详情时操作日志无新增 AI draft；需要 FULL_DRAFT 的服务端整合完成后按既有规则出现一条 draft audit；无需生成的纯 assemble 不伪造新 draft audit。
- [ ] **M-16 视觉回归**：桌面和窄屏检查卡片、摘要、按钮无溢出；颜色、圆角、间距、focus ring 与现有页面一致；页面没有新动画或布局跳动。

## 自审清单

- [x] 用户可观察结果、必须不变、范围外均已明确。
- [x] 关键不变量覆盖 mount、两类生成、merge、人工闸门、版本单源、输入稳定、预览、audit、缓存。
- [x] 已审计浏览器 state、工作台 API、`operator_action_log` 写/读链、宿主、preview 资源、样式与测试入口。
- [x] 每条实现任务引用约束不变量；每条不变量都有自动或人工验收。
- [x] 前端计划含具体 DOM、class、状态、文案、禁止项；不新增 CSS。
- [x] 变更文件 7 ≤ 10；子系统 1 ≤ 2；所有文件均已具名。
- [x] 已明确 `FULL_DRAFT` 可能返回 PARTIAL，但客户端只允许消费 missing GROUNDED，避免实现歧义。
- [x] 已明确取消/失败/stale/身份错误均不得继续 assemble。
- [x] 不生成 acceptance 派生文件；等待计划批准和执行后再由验证技能产出。

## Phase 0 知识使用记录

- `K-shared-workbench-fixed-mode-host-adapter`：共享组件拥有行为，两宿主只保留固定 adapter → I-1/I-7。
- `K-trust-reply-resolved-version-single-source`：resolvedVersionId 与服务端重物化是唯一采用事实 → I-3/I-6。
- `K-ai-reply-loading-panel`、`K-ai-stream-progress-no-fake-percent`：保留稳定状态区、真实 SSE 阶段和真实计数 → I-5/S-2。
- `K-ui-removal-retires-obsolete-contract-tests`：旧隐式行为移除时同步删除旧契约并更新缓存测试 → Task 5。
- `K-locked-item-assembly-list-not-set`：canonical list、不重排、不本地拼接 → I-6。
- `K-llm-attempt-total-budget-cancel`：复用 generationId、双 TTL、取消与 COMMITTING/结束语义 → I-5。
- `K-ai-generate-single-freeform-seam`、`K-request-facts-not-flat-pool`：不新增后端生成入口，不扁平化 request/facts/coverage → 范围外与现状审计。
- `K-ai-generation-observability-not-send-gate`：生成状态不能扩大发送权限，audit 时机必须可解释 → I-4/I-11。
- `K-preview-mirrors-pipeline`、`K-preview-runtime-gates-visible`：仅改变触发时机，预览语义与阻断标记不变 → I-10。
- `K-training-evaluation-bounded-action-log`：SIMULATION complete 后仍走受限评估记录，不把正文写入 action log → 必须保持不变。
