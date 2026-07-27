# fix-1：P2-9 逐项缺口确认 UI

复验对象：`ai-reply-09-p2-review-confirmation-ui`

## 约束摘录

- 每个 draft/adopt 持有独立 review snapshot；切换邮件、重新采用、清空编辑器与发送成功必须清理确认状态。（I-1）
- 编辑只能决定 raw template 是否仍可用；所有 `NEEDS_REVIEW` / `BLOCKED` 均必须逐项确认。（I-2）
- 每个 missing/partial intent 唯一 checkbox，全部勾选；`BLOCKED` note trim 后至少 5 字符。（I-3）
- 多请求 `QA_GROUNDED` 发送前必须恰好有 `1..requestCount` 的行首编号，无重复、无越界。（I-4）
- 前端确认不是最终授权；发送使用 `AI_DRAFT` + review confirmation，`SEND_BLOCKED` 上报 best-effort。（I-5）
- 延续轮不新写初稿审计，必须复用首轮的服务端权威 draft identity；客户端不得伪造 readiness/snapshot。（P2-8 修正记录；K-ai-review-server-authoritative-snapshot）

## 当前状态

- 编译/测试：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` — PASS。
- JS：全量 `src/test/js/*.test.js` — PASS，325 passed / 0 failed / 0 skipped。
- 定向 UI 测试当前也会通过；它们只做源码字符串检查，未执行确认 Promise 与编号反例。

## P1-A：确认按钮取消 Promise，永远不会提交

触发频率：每次非 READY 草稿完成全选并点击“确认并发送”。

证据：`src/main/resources/static/app.js:9627` 先调用 `closeReviewModal()`；该函数在 `9593-9599` 清空 review items、调用 `reject()` 并清空 `resolve`。回到 `9628` 时 resolve 已为空，`9717-9723` 的提交分支不会执行。即使改为 resolve，后续分支也会在已清空的 `reviewItems` 上生成空 `confirmedReviewKeys`。

修复规格：

1. 分离“取消/上下文失效清理”和“已确认提交”两条路径；确认不得调用会 reject 的 close 函数。
2. 在清理前，从 checkbox/state 复制 immutable confirmation payload：draft identity、完整 review keys、trim 后 note、record id、request body。
3. 仅取消、切换上下文或明确关闭时 reject；确认时 resolve 该 immutable payload，随后一次性调用 `submitManualRichReply()`。
4. 成功发送后清理 review state；API 失败保留编辑器，且不得把失败当作已发送。
5. 增加运行时 JS 测试：BLOCKED 全选 + note 后，断言 manual-rich-reply 被调用一次，payload keys 与确认项精确一致。

## P1-B：延续轮丢失服务端 draft identity，后端必然 fail-closed

触发频率：操作员对首轮 AI 草稿提出一次修改后，采用该延续轮的非 READY 草稿并确认发送。

证据：后端仅在首轮 `request.turns.isEmpty()` 时创建并返回 identity（`UnmatchedInboundMailController.kt:316-329`），延续轮返回 `null`（`368`）。前端在每轮响应后把 `lastDraftIdentity` 覆盖为 `result.draftIdentity || null`（`app.js:9446-9447`），draft entry 也只保存该轮 response 的 identity（`8884`），最终 payload 的 identity 为 null（`9719`）。后端对非 READY 要求 identity 且精确匹配服务端记录（`AiReplyReviewAuditService.kt:179-184`），因此拒绝发送。

修复规格：

1. 首轮收到非空 `draftIdentity` 后保留为本次 AI workbench 的权威 identity；延续轮的 null 不得覆盖它。
2. 新建延续轮 draft entry 时，继承该 workbench identity；adopt snapshot 原样复制该值。
3. 仅在切换邮件/重置 workbench 时清空 identity；新首轮的非空 identity 可替换旧值。
4. 增加 JS 测试：首轮 identity 后的延续轮 response identity 为 null，采用该草稿仍提交首轮 identity。

## P1-C：编号校验未执行“恰好 1..N、无重复/越界”

触发频率：编辑者删除全部标题、重复任一标题或加入越界标题时；这些均可直接通过当前发送闸门。

证据：`validateSectionNumbering()` 仅将范围内编号放入 Set（`app.js:9547-9553`），`seen.size === 0` 直接返回 null（`9554`），重复及 `<1` / `>requestCount` 被静默忽略。故正文无编号、`1./1./2.`、`1./2./8.` 均可能通过，不符合 I-4。

修复规格：

1. 当 `requestCount >= 2` 时收集全部行首 `N.` heading；零个 heading 即失败。
2. 验证编号列表严格等于 `[1, …, requestCount]`：长度相等、顺序相等、无重复、无 0/负数/越界。
3. 保留现有用户可修正提示，不做正文语义认证。
4. 增加运行时 JS 测试：`2.` 开始、零 heading、重复、越界、正常连续五种输入。

## P1-D：切换邮件/重新采用草稿不会终止既有确认会话

触发频率：确认弹窗打开期间切换详情，或现有 UI 上下文被重新初始化；低频，但会遗留旧 record 的 request body/review items。

证据：`showUnmatchedDetail()` 仅清空 `aiReplyState.adoptContext`（`app.js:8910-8913`）；`resetAiReplyState()` 不处理 `aiReplyReviewState`（`8837-8854`）。只有 `closeReviewModal()` 会清理 review state（`9591-9600`），且它当前还错误 reject Promise。违反 I-1 的 switch/reset 要求。

修复规格：

1. 提供一个单一 reset/cancel helper：隐藏 modal、清空 pending request/review items/readiness/record id，并只在仍待确认时 reject。
2. 在 detail 切换、workbench reset、重新采用草稿、编辑器清空及发送成功路径调用；不得清空编辑器内容。
3. 增加测试：打开确认后触发 switch/reset，断言 modal 关闭、旧 Promise 被取消、旧 record 不会提交。

## 合规审计

- I-1 snapshot/reset：❌ `app.js:8870-8885,9502-9511` 保存 draft/adopt snapshot；但 `8910-8913,8837-8854` 未清理 review confirmation（P1-D）。
- I-2 编辑不是确认：✅ `app.js:9654-9662` 仅用编辑差异决定 raw template；`9679-9716` 非 READY 上报后开 modal，不按编辑差异放行。
- I-3 逐项完整：❌ checkbox 与 BLOCKED note 检查存在（`9573-9585,9602-9625`），但确认路径不提交且会清空 keys（P1-A）。
- I-4 连续编号：❌ `9545-9567` 漏过零 heading、重复与越界（P1-C）。
- I-5 后端最终权威：❌ 首轮可用 identity 传递正确，但延续轮 identity 丢失，必被后端 fail-closed（P1-B）；best-effort 事件在 `9691-9702` 符合要求。
- 样式契约：✅ `index.html:1872-1889` 使用既有 modal/form/checkbox/button classes；未改 `styles.css`。
- 旧响应兼容：✅ `app.js:3731-3766` 使用 `requestIndex:legacy`。
- raw/rendered 边界（来源：K-ai-preview-raw-adoption-boundary）：✅ `9654-9662` 同时比较 text 与 HTML，只有未变更时提交 raw text template。
- 状态不进正文（来源：K-ai-draft-edit-not-review-confirmation）：✅ review/readiness 仅进入控制 payload/DOM，不拼入 `htmlBody` / `textBody`。
- review key 唯一/服务端权威（来源：K-ai-review-canonical-key-uniqueness、K-ai-review-server-authoritative-snapshot）：❌ 服务端唯一性校验存在（`AiReplyReviewAuditService.kt:191-232`），但 P1-A/B 使 UI 无法提交完整的权威确认。

## 语义完整性

- Accumulation check：✅ 无时间窗口累计计数。
- State machine check：❌ `NEEDS_REVIEW/BLOCKED → confirmed send` 路径在 P1-A 被 reject，无法到达发送完成态；P1-D 还允许上下文切换后残留会话。
- Cross-plan check：❌ 延续轮 `draftIdentity` 在 P2-8 写、P2-9 读的契约断裂（P1-B）。正常 READY 路径可直发；非 READY 的错误→人工确认恢复路径当前失败；首轮审计后的延续轮恢复同样失败。

## 范围

仅修复 P2-9 清单内文件：

1. `src/main/resources/static/app.js`
2. `src/test/js/aiReplyReviewConfirmation.test.js`
3. `src/test/js/aiReplyLoadingFeedback.test.js`

`index.html` 与 `styles.css` 无需改动。不得修改后端、迁移或 QA 审计语义。
