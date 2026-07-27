# fix-2：P2-9 逐项缺口确认 UI

复验对象：`ai-reply-09-p2-review-confirmation-ui`

## 约束摘录

- 每个 draft/adopt 持有独立 review snapshot；切换邮件、重新采用、清空编辑器与发送成功必须清理确认状态。（I-1）
- 编辑只能决定 raw template 是否仍可用；所有 `NEEDS_REVIEW` / `BLOCKED` 均必须逐项确认。（I-2）
- 每个 missing/partial intent 唯一 checkbox，全部勾选；`BLOCKED` note trim 后至少 5 字符。（I-3）
- 多请求 `QA_GROUNDED` 发送前必须恰好有 `1..requestCount` 的行首编号，无重复、无越界。（I-4）
- 前端确认不是最终授权；发送使用 `AI_DRAFT` + review confirmation，`SEND_BLOCKED` 上报 best-effort。（I-5）
- 延续轮必须复用首轮服务端权威 draft identity，客户端不得伪造 readiness/snapshot。（P2-8 修正记录；K-ai-review-server-authoritative-snapshot）

## 修正记录

| 编号 | P1 问题 | 触发频率 |
|---|---|---|
| P1-A | 确认 payload 从尚未建立的 `requestBody.aiReviewConfirmation` 取 identity，所有非 READY 草稿提交的 identity 都是 `null`，后端必定 fail-closed。 | 每次 `NEEDS_REVIEW` / `BLOCKED` 草稿全选确认并发送。 |
| P1-B | 重新采用草稿没有终止已打开的确认会话，旧草稿的 pending body/review items 可以残留。 | 低频：确认会话存续时发生重新采用（事件重入、自动化或后续 UI 流程扩展）。 |

## 修复规格

### P1-A：从采用快照传递服务端 identity

文件：`src/main/resources/static/app.js`

1. 开启确认会话时将 `adopt.draftIdentity` 保存为确认状态的不可变字段，或在确认 payload 创建时从该采用快照复制；不得从尚未赋值的 `requestBody.aiReviewConfirmation` 读取。
2. `confirmReview()` 清理状态前复制该 identity，随后 `.then()` 构造的 `aiReviewConfirmation.draftIdentity` 必须等于首轮服务端返回的 identity（延续轮 response 为 `null` 时也一样）。
3. 保持后端权威校验；不回退为客户端伪造 identity 或放宽后端 fail-closed。
4. 增加实际执行的 JS 行为测试：首轮 identity、延续轮 `null`、采用非 READY、全选确认后，断言 `manual-rich-reply` 调用一次且 payload identity 等于首轮 identity。

### P1-B：重新采用前取消旧确认会话

文件：`src/main/resources/static/app.js`

1. `ai-adopt-draft` 入口在替换 `adoptContext` 前调用现有 `cancelReviewSession()`；仅关闭/清理确认会话，不清空编辑器。
2. 取消应 reject 旧 Promise 并清空 `pendingRequestBody`、review items、readiness、record id、resolve/reject；新采用草稿随后建立自己的独立快照。
3. 增加行为测试：开启草稿 A 的确认会话后重新采用草稿 B，断言 modal 关闭、A 的 Promise 被取消，且 A 的 request body 永不提交。

## 当前状态

- 编译/测试：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` — PASS；Surefire 报告：1697 tests，0 failures，0 errors，3 skipped。
- JS：`node --test src/test/js/*.test.js` — PASS；331 passed / 0 failed / 0 skipped。
- Phase 9 定向测试与 batch-send visual 回归包含在上述 JS 全量测试中。

## 合规审计

- I-1 snapshot/reset：❌ `src/main/resources/static/app.js:9487-9526` 重新采用草稿直接替换 `adoptContext`，未调用 `cancelReviewSession()`（P1-B）；切换详情与 workbench reset 已在 `8837-8845, 8911-8915` 取消会话。
- I-2 编辑不是确认：✅ `src/main/resources/static/app.js:9688-9696` 编辑仅决定 raw template；`9708-9750` 全部非 READY 路径先上报再开启 modal。
- I-3 逐项完整：✅ `src/main/resources/static/app.js:9628-9648` 要求所有 checkbox；BLOCKED note 至少 5 字符。❌ 完整确认无法携带权威 identity（P1-A）。
- I-4 连续编号：✅ `src/main/resources/static/app.js:9549-9581` 拒绝零 heading、越界、数量不符、非连续与重复。
- I-5 后端最终权威：❌ `src/main/resources/static/app.js:9649-9654` 从尚不存在的 `pendingRequestBody.aiReviewConfirmation` 取 identity；该字段只在确认后 `.then()` 的 `9741-9745` 才写入（P1-A）。`SEND_BLOCKED` best-effort 上报符合要求（`9716-9730`）。
- 样式契约：✅ `src/main/resources/static/index.html:1872-1889` 使用既有 modal/form/checkbox/button 类；`styles.css` 未改。
- 旧响应兼容：✅ `src/main/resources/static/app.js:3728-3741` 使用 `requestIndex:legacy`。
- raw/rendered 边界（来源：K-ai-preview-raw-adoption-boundary）：✅ `src/main/resources/static/app.js:9681-9690` 同时比较 text 与 HTML。
- 状态不进正文（来源：K-ai-draft-edit-not-review-confirmation）：✅ `src/main/resources/static/app.js:9673-9678, 9741-9745` readiness/review data仅进入控制 payload，不拼接正文。
- review key 唯一/服务端权威（来源：K-ai-review-canonical-key-uniqueness、K-ai-review-server-authoritative-snapshot）：❌ 前端本轮不能提交服务端 identity（P1-A）。
- No extras：✅ 本轮修复范围仍限 `app.js` 与其 JS 测试；`index.html` 为原计划 T1，未修改 `styles.css`。工作区其余未提交的 P0-P2 改动不归属本子计划。

## 语义完整性

- Accumulation check：✅ 无时间窗口累计计数。
- State machine check：❌ `NEEDS_REVIEW/BLOCKED → confirmed send` 在 P1-A 后端拒绝，不能到达发送完成态；P1-B 违反重新采用时的会话重置。
- Cross-plan check：❌ P2-8 写入的首轮 draft identity 未由 P2-9 确认 payload 读出；正常 READY 路径可直发，但非 READY 的人工恢复路径仍失败。

## 范围

仅修复 P2-9 清单内文件：

1. `src/main/resources/static/app.js`
2. `src/test/js/aiReplyReviewConfirmation.test.js`
3. `src/test/js/aiReplyLoadingFeedback.test.js`

不得修改后端、迁移、QA 审计语义或样式。
