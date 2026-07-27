# fix-1：ai-reply-05 rendered preview 跨计划 raw 传递

- 原计划：`docs/plans/2026-07-12/ai-reply-05-rendered-preview.md`
- 本轮：fix-1/3
- 类型：计划级接口缺口；未修改业务代码。

## 约束摘录

- I-1：`draftText` 是 raw；`renderedDraftText` 只用于显示、复制、采用；续轮必须使用 raw。
- I-2：预览按当前入站 sender account/contact 渲染；缺账号保留 raw。
- I-4：bubble、copy、adopt 显示 rendered；turn payload 使用 raw，两个值分开存储。
- 总索引跨计划接口：计划 5 只生成 rendered preview；计划 6 必须按实际 sender account/contact 最终重渲染。
- I-5：preview/read path 不得产生业务写入。

## P1

| ID | 问题 | 触发频率 |
|---|---|---|
| P1-1 | AI 草稿采用后，前端只把 rendered 文本写入编辑器，发送请求只含 editor 的 html/text；raw 模板没有跨越采用边界。最终 sender 与预览 sender 不同（或预览缺账号）时，计划 6 没有输入可重新渲染，账号 B 签名无法替代账号 A。 | 正常可触发：任何 AI 采用后最终账号与入站账号不同；缺失预览账号时稳定触发。 |

## 修复规格

### P1-1：保留 raw 至最终发送，而不覆盖用户编辑

文件范围：

- `src/main/resources/static/app.js`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
- `src/test/js/aiReplyLoadingFeedback.test.js`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailControllerTest.kt` 或现有 manual-rich reply controller 测试
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`

要求：

1. AI adopt 继续把 rendered 文本写入编辑器；同时按 draftId/recordId 保存 raw 模板和对应 rendered baseline，切换邮件/重置时清除。
2. 仅当编辑器内容仍等于该 baseline 时，manual-rich 请求携带 raw text/html 模板（或等价的、可验证的 raw 字段）；用户编辑过则不携带，最终发送必须以用户编辑内容为准，绝不以陈旧 raw 覆盖。
3. Phase 6 的最终发送链只在收到有效 raw 模板时，使用最终 `resolvePendingReplyAccount` 与当前 contact 重渲染；否则保持普通人工富文本路径。未知 token 校验、HTML 转义、mail record/operator log 均沿用计划 6 的约束。
4. 覆盖：账号 A 预览→账号 B 发送使用 B；编辑后不回退到 raw；缺账号预览保留 token 但发送可最终渲染；非 AI 人工富文本请求不变。

## 当前状态

- 编译：PASS（`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`）
- Kotlin 定向测试：PASS — 32 passed, 0 failed, 0 skipped
- JS 定向测试：PASS — 12 passed, 0 failed, 0 skipped

## 合规审计

- I-1 raw/rendered response：✅ `AiTrainingController.kt:217-236`、`UnmatchedInboundMailController.kt:308-323` 保留 `draftText` 且新增独立 `renderedDraftText`；`app.js:9236-9238`、`9275-9288` 续轮用 raw。
- I-2 精确 account 与缺失降级：✅ `AiReplyDraftPreviewService.kt:18-30` 按 `senderAccountCode` 查账号，缺失返回 raw + warning；两入口传入各自 inbound sender，见 `AiTrainingController.kt:217-221`、`UnmatchedInboundMailController.kt:308-312`。
- I-3 共享 preview service：✅ 两 controller 只调用 `AiReplyDraftPreviewService.preview`，见上述行；实际变量解析唯一在 `AiReplyDraftPreviewService.kt:25`。
- I-4 显示/复制/采用 rendered，续轮 raw：❌ `app.js:9284-9288`、`9325-9334` 正确分离即时显示与采用；但 `app.js:9363-9368` 发送请求仅携带 rendered editor 内容，raw 未跨越采用边界，违反总索引→计划 6 最终重渲染接口。
- I-5 API 只读：✅ `AiReplyDraftPreviewService.kt:18-30` 仅读取账号并调用纯 render；无 save、HTTP、enrichment。
- S-1：✅ `git diff --numstat -- src/main/resources/static/styles.css src/main/resources/static/index.html` 无输出；展示仍经 `translatableBody`，见 `app.js:2900`、`8736`。
- Deleted code：✅ 旧 `lastDraft` 无存活引用；替代 state 见 `app.js:145-146`。
- No extras：✅ Phase 5 文件范围内无 CSS/DOM 新增；跨计划 P1 修复范围如上。

## 语义完整性

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ 无新增状态机；既有 requestSeq/record/model guard 未改变，见 `app.js:9263-9269`。
- Cross-plan check：❌ Phase 5→6 的 raw 模板接口断裂：`app.js:9333-9334` 写入 rendered，`app.js:9363-9368` 只发送 rendered，不能满足计划 6 I-1。
