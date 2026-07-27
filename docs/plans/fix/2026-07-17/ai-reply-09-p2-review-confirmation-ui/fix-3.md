# fix-3：P2-9 逐项缺口确认 UI

复验对象：`ai-reply-09-p2-review-confirmation-ui`

## 约束摘录

- 每个 draft/adopt 持有独立 review snapshot；切换邮件、重新采用、清空编辑器与发送成功必须清理确认状态。（I-1）
- 编辑不能代替确认；所有 `NEEDS_REVIEW` / `BLOCKED` 必须走逐项确认。（I-2）
- 每个 missing/partial intent 都有唯一 checkbox，必须全选；`BLOCKED` 的 trim note 至少 5 字符。（I-3）
- 多请求 `QA_GROUNDED` 的标题编号必须严格为 `1..requestCount`。（I-4）
- 服务端以 draft identity 与 canonical snapshot 为最终权威；`SEND_BLOCKED` 上报 best-effort。（I-5；P2-8 修正记录）
- fix-2 已关闭：确认 payload 在清理前复制；续轮继承首轮 identity；重新采用取消旧会话。

## 修正记录表

| P1 | 问题 | 触发频率 |
|---|---|---|
| P1-1 | review modal helpers 被定义在 `handleUnmatchedAction()` 内部，却从顶层 `resetAiReplyState()`、`showUnmatchedDetail()` 和 `bindEvents()` 调用/引用。页面初始化绑定 modal 事件时即 `ReferenceError`，打开邮件详情也会在 reset 时抛错，审核流程不可用。 | 每次 `bindEvents()` 初始化；每次加载支持 AI 回复的邮件详情。 |

## 修复规格

### P1-1：将 modal helpers 放到所有调用点可见的作用域

文件：

1. `src/main/resources/static/app.js`
2. `src/test/js/aiReplyReviewConfirmation.test.js`

1. 将 `openReviewModal`、`closeReviewModal`、`cancelReviewSession`、`clearReviewState`、`updateReviewConfirmButton`、`confirmReview` 移到顶层（或改为同等的共享模块作用域）；不得在 `handleUnmatchedAction()` 内保留局部定义。
2. 保持当前状态和 Promise 语义：取消只 reject 未决会话，确认先复制 immutable payload 再 resolve，清理不清 editor/adopt 正文。
3. `bindEvents()`、`resetAiReplyState()`、`showUnmatchedDetail()` 与 `handleUnmatchedAction()` 必须共用同一组 helpers；不得复制第二套状态机。
4. 增加可执行的 JS 行为测试：以最小 DOM stub 执行共享 helpers 与 `bindEvents()`/reset 路径，断言无 `ReferenceError`；再覆盖 BLOCKED 全选+note 后仍只提交一次正确 identity/key payload。

## 当前状态

- 编译：PASS — `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`。
- 后端测试：PASS — 1697 tests，0 failures，0 errors，3 skipped。
- JS：PASS — `node --check src/main/resources/static/app.js && node --test src/test/js/*.test.js`，335 passed / 0 failed / 0 skipped。
- 现有 Phase 9 JS 测试仅做源码字符串匹配，未执行 helper 作用域或 `bindEvents()` 初始化路径，故未捕获本问题。

## 合规审计

- I-1 snapshot/reset：❌ `src/main/resources/static/app.js:8837-8838` 与 `8911-8914` 从顶层调用 `cancelReviewSession()`，但定义位于 `handleUnmatchedAction()` 的局部作用域（`:9153`, `:9609`）；详情加载/重置不可执行（P1-1）。
- I-2 编辑不是确认：✅ `src/main/resources/static/app.js:9686-9694` 仅用 text+HTML 基线决定 raw template；`:9711-9756` 非 READY 必经 modal 并携带确认。
- I-3 逐项完整：✅ `src/main/resources/static/app.js:9627-9663` 要求全选，BLOCKED note 至少 5 字符，并在清理前复制 review keys。
- I-4 连续编号：✅ `src/main/resources/static/app.js:9550-9581` 拒绝零标题、越界、数量不符、重复及非连续编号。
- I-5 后端最终权威：✅ `src/main/resources/static/app.js:9740-9755` 传 adopt snapshot identity；`AiReplyReviewAuditService.kt:174-232` 以服务端 canonical snapshot 严格校验；`:9723-9734` 的 SEND_BLOCKED 上报为 best-effort。❌ modal 的全局事件绑定本身不可解析（P1-1）：`app.js:10570-10579` 在 `bindEvents()` 中引用局部 helpers。
- 样式契约：✅ `src/main/resources/static/index.html:1872-1889` 仅使用既有 modal/form/checkbox/button 类；未修改 `styles.css`。
- 旧响应兼容：✅ `src/main/resources/static/app.js:3752-3764` 使用 `requestIndex:legacy`。
- raw/rendered 边界（来源：K-ai-preview-raw-adoption-boundary）：✅ `src/main/resources/static/app.js:9686-9694` 同时比较 text 与 HTML。
- 状态不进正文（来源：K-ai-draft-edit-not-review-confirmation）：✅ readiness/review data 只进入控制 payload/DOM，未拼入 `htmlBody` / `textBody`。
- No extras：✅ P2-9 范围内；本轮只要求 `app.js` 和原计划测试文件。

## 语义完整性

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：❌ `NEEDS_REVIEW/BLOCKED → modal confirmation` 的全局事件入口不可用；P1-1 修复后现有 confirm→submit 路径可达。
- Cross-plan check：✅ fix-2 的首轮 identity 延续/服务端 canonical snapshot 契约一致；P1-1 是前端作用域错误，不改变接口契约。
