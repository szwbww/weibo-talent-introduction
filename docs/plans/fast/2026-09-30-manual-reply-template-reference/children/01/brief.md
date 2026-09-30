# Fast-P Child Brief — 01（人工回复「引用邮件模板」）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-09-30/manual-reply-template-reference.md`，identity `commit:<seed>`。
- 本 child 批准计划（完整合同，必须先通读）：同一路径 `docs/plans/2026-09-30/manual-reply-template-reference.md`（单子计划 run：master 计划即本 child 计划；「关键不变量」I-1～I-8、「样式契约」S-1～S-4、「实现方案」T-1～T-5、「变更文件清单」、「验收标准」逐条生效）。
- Worktree / branch / `child_base_sha`：见派发消息。
- 依赖：none。无下游 child；人工验收 A-1～A-12 在本 run 之外，不要求执行。
- 计划声明的范围决策：只做「有真实来信的人工回复」；无来信续信扩展未获批，禁止实现或猜测其账号规则。

## 全局约束

1. 只允许修改「Authorized Files」表内 7 个文件；不得新建白名单外文件（含测试 fixture、静态资源）。`mailbox-chat.css`、`meeting-confirmation.js`、`app.js`、`index.html` 之外的静态资源、Kotlin、SQL、迁移、历史计划、`docs/mockups/**` 全部只读（index.html 仅改 11 个缓存键）。
2. 不得修改 `docs/plans/**`（fast-p 证据与计划由控制方提交）；不得 push、merge、rebase、squash、amend、reset；不得改写已有提交。
3. 不得读写本 worktree 之外的仓库工作区。唯一例外：可只读查看 mockup 视觉参考 `/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/mockups/template-reference-preview/index.html`（及 `preview.png`），仅用于对照布局；禁止复制 preview.js 的示例数据、全局变量、MutationObserver 或测试用禁发按钮，禁止把 mockup 当生产依赖；S-1/S-2 是唯一实现依据。
4. 产品代码提交格式：`feat(fast-p): implement 01`；把 fast-p 报告/日志（`docs/plans/fast/**`）排除在该提交之外，报告写完留在工作树由控制方提交。
5. 若计划与代码冲突、需要白名单外文件、需要新行为或需要修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`，不要自行扩范围或改计划。
6. 禁止联网、连线上 MySQL/ES、发信、部署；不得新增依赖；不得新增后端接口、DB/ES 字段、迁移、发送 adapter 改动；不得执行历史运维 SQL。
7. 实施前重新核对（计划 T-5）：`index.html` 当前 11 个 `?v=` 资源键与 `rg -l -F '20260929-bounce-alert' src/test` 的命中；若出现新的硬编码缓存键测试文件，返回报告请求修订文件清单，不静默扩文件、不按历史知识猜数量。
8. 计划明确不做：无来信续信扩展；模板编辑器/分类/收藏/最近使用；光标位置插入；Markdown/HTML 模板解析；模板富文本化；新附件/日历生成；自动发送；模板使用统计；草稿持久化；修复既有无来信富文本恢复；QA/RAG 通用重构；后端门禁改造。
9. 纯文本语义：模板正文按纯文本处理（textContent/文本节点 + `<br>`），把字面 `<img>`/`<script>`/`&` 当字符展示；不得 innerHTML 直插、不得解析 HTML/Markdown、不得自动链接。

## Authorized Files（7）

| # | 精确路径 | 改动 |
| --- | --- | --- |
| 1 | `src/main/resources/static/mailbox-chat.js` | 入口、dialog、本地临时状态、只读请求、填入、生命周期；不改发送路由 |
| 2 | `src/main/resources/static/styles.css` | 仅逐字追加 S-1/S-2 声明的 CSS |
| 3 | `src/main/resources/static/index.html` | 仅 11 个资源缓存键改为 `20260930-manual-template-reference`；不新增 script/link |
| 4 | `src/test/js/mailboxChatBehavior.test.js` | 复用既有 MiniDOM/bootChat harness 增加模板行为/竞态/草稿/发送断言 |
| 5 | `src/test/js/materialRequestIntegration.test.js` | 工具栏顺序契约更新 + 材料→模板互操作 |
| 6 | `src/test/js/meetingConfirmationIntegration.test.js` | 工具栏顺序、会议/附件/RAG 互操作回归 |
| 7 | `src/test/js/mailboxTemplateReferenceStyle.test.js` | 新增：CSS 逐字合同、DOM/资源登记验证 |

## 关键不变量（计划 I-1～I-8 摘要；冲突时以计划原文为准）

- I-1 入口与只读引用：入口仅由 `manualComposeHtml(..., outbound=false)` 渲染，不依赖 `meetingEnabled()`；仅 `manual.mode === "inbound"`、真实正整数 processingId/contactId、有效 targetKey 且非 busy 可打开/应用；只调用 `GET /api/compose-templates` 与 `POST /api/compose-templates/preview-draft`。
- I-2 真实上下文与后端渲染：每次打开重读列表、只呈现 `enabled === true`；POST 明确映射 subject/subjectSnippetId/四字段 blocks，携带实际 contactId、实际回复账号、`strictPlaceholders:false`、`variantIndex:0`；账号：普通来信取 `manual.targetAccountCode`，有跟进锚点时从 `followupCandidates()` 找相同 id 的成功发件取 accountCode，找不到显示固定失败文案，不退回联系人绑定账号或数组首项；前端不替换变量、不重拼模板；预览快照唯一，填入只用该快照，换模板作废旧快照。
- I-3 可填入与提示状态分开：无选中/请求中/失败/空 body/无 toEmail/toEmail≠contact.expertEmail/正文残留 `${`/存在预览专用退订值（精确值 `https://example.com/u/unsubscribe?token=preview`）→ 禁用「填入回复」；主题残留 `${` 仅在勾选「同时替换回复主题」时阻止填入；fallback/missing 分别提示，`blocks.included=false` 显示块名与 skipReason；这些只是人工编辑提示，不新建发送门禁。
- I-4 文本安全与富文本保存：预览用 `textContent` + `white-space:pre-wrap`；写编辑器先 `normalizeManualTextLineBreaks`，逐行建 text node 与 `<br>`，可包无 class `<div>`，禁止 innerHTML 直插；追加只向末尾追加新节点，替换只清空正文子节点；应用后调用 `handleManualComposeInput(editor)` 与 `saveConversationState()`；不另建草稿 Map、不写 localStorage。
- I-5 用户选择与草稿附属信息：默认不替换主题，勾选用预览 subject 原样采用；正文空则填入、非空默认追加、显式 radio 才替换；追加保留 `manual.qa`，替换清除并同步 draft.qa；不从模板 blocks.refId 推导 qaRuleIds/ragFactCodes；两种模式都保留 `outboundAttachmentDraft` 与 `followUpAnchorMailRecordId`；会议快照（ready 或 stale）存在时禁用「替换正文」并显示固定文案，应用时重查；不绕过 `detectMeetingChange`；requestId 只随 `saveDraftFromInputs` 的现有规则失效。
- I-6 异步响应与草稿身份：新增 `instance.templateReference` 仅为单次弹框临时状态（open/seq/identity/items/selectedId/preview/loading/error/trigger），不加 draft 字段；打开捕获 ownerKey/contactId/expertEmail/expertName/targetKey/convEpoch/跟进锚点/实际账号/editorRevision/主题与正文快照；每次加载、选择、重试、关闭递增 seq；异步回包仅在 open+seq+owner/target/epoch 匹配时更新；应用前复核正文/主题快照与 busy，变化则禁用并显示「回复目标或草稿已变化，请关闭后重新选择模板」；应用保存读取当前 draft（保留晚到附件），不用打开时整份 draft 覆盖。
- I-7 弹框所有权与生命周期：复用现有 body portal，原生 dialog + showModal（不嵌入带 backdrop-filter 的 panel）；事件走现有 host/portal 委托，原生 cancel 走同一关闭函数；关闭只移除自己拥有的 `.reply-template-dialog` 并清状态、使 seq 失效，禁止无条件 `portalRoot.innerHTML=""`；打开管理/材料/跟进、切专家、切账号、采用新回复目标、unmount 时关闭引用弹框；取消/关闭/Esc 恢复触发按钮焦点，应用后聚焦正文。
- I-8 发布与范围边界：只追加声明样式；不改 mailbox-chat.css 及其字节基线；index.html 现有 11 个版本化资源统一改为 `20260930-manual-template-reference`；实现文件仅限变更清单；无后端/DB/ES/发送 adapter 修改。

## 样式契约（计划 S-1～S-4，计划原文为唯一依据）

- S-1：工具栏复用 `.mc-editor-tools`；按钮 `<button class="button reply-template-trigger" type="button" data-action="mc-open-template-reference">引用模板</button>`；插入位置 `${meetingTrigger}${materialTrigger}${templateReferenceTrigger}${followUpButton}`（不改变既有三个按钮条件）；仅追加 S-1 的 5 条 CSS。
- S-2：左列表/右预览/底部操作的原生 dialog，`data-role` 节点与 HTML 层级严格按计划合同；template 选项用普通 button + aria-pressed；动态列表只替换 `data-role="template-list"` 内部，动态正文只改 textContent；CSS 全量逐字落盘，不新增未声明 class、不加 inline style；`[hidden]` 用全局规则，不新造工具类。
- S-3：加载/空/搜索空/失败/预览加载/警告/预览已生成/会议限制文案与 aria-busy/禁用状态按计划；重试按钮只重试当前失败阶段；不显示请求栈或 SMTP 信息。
- S-4：只改 index.html 已有 11 处 `?v=`，不新增 script/link、不调整顺序；新增 class 均为 `reply-template-*`，兼容 `mailboxChatStyle.test.js` 的字面 class 检查。

## 实现要点（计划 T-1～T-5 摘要）

- T-1（mailbox-chat.js + styles.css）：新增 `instance.templateReference`；建议内部函数名 `templateReferenceDialogHtml/openTemplateReferenceDialog/closeTemplateReferenceDialog/renderTemplateReferenceList/renderTemplateReferencePreview/applyTemplateReference`（模块局部，不导出新公共 API）；onClick/onClickPortal 增加打开/选择/重试/关闭/应用分支，portal 增加 input/change 委托（搜索、checkbox/radio），监听随 portalHandlers 解绑；搜索按 templateName+description 小写包含，纯本地；默认选首个已启用模板并预览，搜索后选择保留/回退首个，空结果清选中；不重建搜索输入节点；生命周期逐项挂接 `teardownConversationSubViews`、`meetingCloseDisposeOnAccountScopeChange`、`retargetManual`、管理/材料/跟进打开前、unmount；发送期间与 open/apply 都检查 `manual.busy`；`manualComposeHtml` 不改闭包签名（入参三参，inbound 判断用 `!outbound`）——若计划与代码不一致，以计划文本为准并报告偏差。
- T-2（mailbox-chat.js）：打开立即 GET list；Array 响应与选中项字段（正整数 id、string subject、Array blocks）校验失败进失败态；不调用 app.js 的 `loadComposeTemplates`/`ensureComposeTemplatesLoaded`；payload 严格按计划四字段 blocks + subjectSnippetId ?? null + contactId + senderAccountCode + strictPlaceholders:false + variantIndex:0；回包经 seq/identity 校验后存快照渲染；fallbackKeys/variables/未包含 blocks 变 UI 文本；正文唯一使用 `result.body`（不用 textPreview）；应用按钮有效性集中一个函数供渲染与点击复核；主题勾选只更新适用校验，不重发预览。
- T-3（mailbox-chat.js）：应用前复核身份、DOM 快照、preview、busy、会议替换禁用条件；正文空直接填/追加保留原节点/替换只清正文；勾选才写 subject；替换时先置 `manual.qa=null`；不把 templateId 存 draft；调用 `handleManualComposeInput(editor)` + `saveConversationState()`；close 弹框、聚焦正文、提示「模板已填入回复，可继续编辑」；不改 sendManualReply/mcHostSendRichReply/submitManualRichReply/后端 DTO。
- T-4（测试）：mailboxChatBehavior.test.js 复用既有 harness，加模板 fixture 与可控延迟 Promise；需真实 sanitizer 的用例局部加载真实 meeting-confirmation.js；materialRequestIntegration/meetingConfirmationIntegration 只更新原顺序断言并加「引用按钮位于材料之后/跟进之前」，保留原断言意义；新 style 测试从本计划 S-1/S-2 的 css fenced block 逐字读取并断言 styles.css 包含、button/DOM role/class、资源键统一、无新增 inline style、mailbox-chat.css 字节不变。
- T-5（index.html + 收尾）：11 个键全改 `20260930-manual-template-reference`；执行下方必需命令并附真实页面截图/录屏证据（可用 node 或本地静态服务打开页面截图；不部署、不发信）；不标「人工验收完成」。

## 必需命令（fresh 运行，逐条记录 exit code 与计数）

```sh
node --check src/main/resources/static/mailbox-chat.js
node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxTemplateReferenceStyle.test.js src/test/js/materialRequestIntegration.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxOutboundAttachments.test.js src/test/js/composeTemplatePreview.test.js src/test/js/meetingConfirmationAssets.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js
node --test src/test/js/*.test.js
git diff --check
```

- 基线红/绿对照见 `docs/plans/fast/2026-09-30-manual-reply-template-reference/children/01/baseline.md`；不得修改范围外文件去消除基线失败。
- 本次无 Kotlin/SQL 改动；不要求启动生产或重跑迁移；不以 JS 通过宣称后端集成已实测。
- 视觉证据（截图/录屏）建议输出到 `target/` 或 `/tmp` 构建产物目录，不入库。

## 交付物

- 一个本地实现提交：`feat(fast-p): implement 01`。
- 执行报告：`docs/plans/fast/2026-09-30-manual-reply-template-reference/children/01/execution.md`（不进入实现提交）。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
