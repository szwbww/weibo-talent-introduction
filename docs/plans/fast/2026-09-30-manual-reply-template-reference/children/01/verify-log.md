# Child 01 — Verification Log

（由 light verifier 按 references/light-verifier.md 的四门禁报告格式追加；append-only。）

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 01 (docs/plans/2026-09-30/manual-reply-template-reference.md)
Boundary: 697441829efdb7b438db71a786e5123faf94681c..9606433
Verifier: VerifyTemplateRef01

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-only 6974..9606` 仅有 docs/plans/**（计划修订 A1 提交 5adacbb + fast-p 证据，均预期）与 8 个授权产品/测试文件：index.html、mailbox-chat.js、styles.css、mailboxChatBehavior.test.js、mailboxTemplateReferenceStyle.test.js（新增）、materialRequestIntegration.test.js、meetingConfirmationIntegration.test.js、mailboxOutboundAttachments.test.js（A1 #8）。逐类 grep（mailbox-chat.css、meeting-confirmation.js、app.js、.kt、.sql、db/migration）对同一 diff 无命中（exit 1）。numstat：mailbox-chat.js 721+/1-（唯一删除行=2918 旧工具栏插值行）、styles.css 66+/0-、index.html 11+/11-（仅版本键）；2df9170 只含授权 1–7，9606433 只含 A1 文件。 |
| Plan and invariants | PASS | S-1 入口：mailbox-chat.js:2893（仅 `!isOutbound` 渲染，不依赖 meetingEnabled）、2918 顺序 `${meetingTrigger}${materialTrigger}${templateReferenceTrigger}${followUpButton}`；按钮逐字 `button reply-template-trigger` / `mc-open-template-reference`；styles.css:11912-11916 逐字 S-1。S-2：mailbox-chat.js:4611-4672 dialog 与计划 S-2 层级一致，16 个 data-role、aria-pressed 选定项、`[hidden]` 全局规则；styles.css:11917-11977 与计划 S-2 fenced block 经 `diff` 完全一致（66 行 = S-1 5 行 + S-2 61 行）。S-4：index.html:11-15/2323-2328 恰好 11 个 `?v=20260930-manual-template-reference`，无新增 script/link。I-1/I-2：4951-4960 payload（subject、subjectSnippetId??null、blocks 四字段、contactId、真实 senderAccountCode、strictPlaceholders:false、variantIndex:0）；4657-4665 账号解析（无锚点取 manual.targetAccountCode；有锚点必须在 followupCandidates 命中同 id，否则 5030 固定文案且零请求）；4953 每次打开重读 GET。I-4：5110-5127 逐行 text node + `<br>` + 无 class div，无 innerHTML 注入（新增行仅 3 处：list 清空、静态 dialog 模板、注释）；预览用 textContent（4849-4855）。I-5：5165-5171 replace 先 `instance.manual.qa=null`、append 不动 qa；5167 主题仅勾选才原样写入；4866-4872 会议快照禁 replace + 5163 应用回查；5174-5175 经既有 handleManualComposeInput/saveConversationState 保存，附件与跟进锚点由 saveDraftFromInputs 读当前 draft 保留（3851-3898）。I-6：4667-4681 seq+owner/contact/target/convEpoch 复核，5129-5161 应用前正文/主题快照复核与固定 stale 文案。I-7：5078-5105 close 只删自身 `.reply-template-dialog`（无 portalRoot.innerHTML=""）；挂接点 1631/3120/4128/4429/5414/5992/6577；监听 6633-6635 走 listenPortal 随 portalHandlers 解绑（6578-6584）。I-8：未改 mailbox-chat.css；无后端/DB/ES/发送 adapter 改动——mailbox-chat.js 全文件仅删除 1 行（工具栏插值），sendManualReply/send adapter 区域无 hunk。附注：styles.css 新增块落在 `/* task-center-contract:start */` 之前（writer 已在 execution.md 声明，见 RECORD_ONLY O-1）。 |
| Required commands | PASS | 本次 fresh（node v25.7.0，HEAD=9606433）：`node --check src/main/resources/static/mailbox-chat.js` → exit 0；计划 9 文件 targeted `node --test …` → exit 0，tests 234 / suites 50 / pass 234 / fail 0 / skipped 0；`node --test src/test/js/*.test.js` → exit 0，tests 1265 / suites 249 / pass 1265 / fail 0；`git diff --check` → exit 0。与基线对照：实施前 1233/1233（baseline.md），本 child 净增 32 用例；epoch 1 的 234/233/1 与 1265/1264/1 唯一失败（mailboxOutboundAttachments.test.js:1559 顺序断言）已由 A1 消除，与 epoch 2 声明 234/234、1265/1265 逐条一致。工作树仅 execution.md 报告未提交（按 brief 归控制方）。 |
| Downstream interfaces | N/A | 单子计划 run：计划与 brief 均声明无下游 child、人工验收 A-1～A-12 在 run 外。产品改动无 DTO/接口/DB 变更（diff 无 .kt/.sql）；新增跨边界值 `mc-open-template-reference` 与三个 portal 动作的消费点唯一且齐全：onClick 6234、onClickPortal 6336-6350、onPortalKeyDown 6357-6359；app.js 两处 document 级 click 监听（3105、11631）选择器限定 `.btn-translate` / `.snippet-subject-combobox`，不会误吞新动作。 |

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1：`styles.css` 新增 66 行插在 `/* task-center-contract:start */`（styles.css:11978）之前，而非文件末尾。writer 已在 `children/01/execution.md`「与计划的偏差」第 1 条声明原因（未授权用例 `taskActivityCenter.test.js` 断言 `task-center-contract:end` 之后不得有任何内容）。独立复核：纯新增 0 删除（旧字节基线未变）、S-1/S-2 块与计划逐字一致、新 class 仅在新块声明（style 测试断言）、taskActivityCenter 用例仍绿；对四门禁无影响，供人工知悉，无需动作。

### Required Action
- COMPLETE_CHILD
