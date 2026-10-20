# 02 补全称呼与签名及发送确认

状态：详细计划，待实施。依赖：03 全局设置、01 校验与预览已通过验证。代码依据为 [审计回执](reply-identity-evidence.md) E12–E20、E24；基线HEAD `b0d2f278a9dddc0be0dcf6fbd2e9b5a947c8a135`。用户最终口径：提醒可确认继续。

## 需求描述

人工回复编辑器「跟进邮件」右侧增加「补全称呼与签名」；没有跟进按钮但有可用人工编辑器时仍显示在工具栏末尾。只补缺失首尾，已有的不覆盖。点击发送遇到缺失或不一致时弹窗对比，确认原样发送，取消保留草稿。

新增可观察结果：工具栏下方「默认设置」可同时选择并编辑两个全局片段、预览当前邮件效果、保存后供所有专家使用；保存不自动改写现有正文。

必须保持：正文富文本/链接/列表、附件/会议块/历史引用；当前草稿所有权及持久化、发送幂等；跟进锚点/主题；其他共享弹窗及原有强风险确认。
不包含：自动发送、首封批量/模板发送入口、自动纠正已有错名、重写 AI 生成链、模板管理整体改版、全站 CSS 调整；本轮包含窄范围的全局称呼与签名设置。

## 关键不变量

### I-1: 缺项补全、已有不覆盖
- Rule：只采用 01 返回 canInsert=true 的 MISSING slot；称呼插到本次正文第一段前，结束语/签名插到本次正文最后、引文前。不处理 GREETING/ACK，不替换现有姓名；MISMATCH/UNCERTAIN/重复错位只展示提示。
- Rule：以 createElement/textContent/br 创建内容，保留原 DOM 节点与富文本。不得全文 innerText 覆写、不得直接执行接口返回 HTML。连续点击第二次不插入相同内容。
- Applies to：新补全操作。
- Violation consequence：误覆盖正文或重复签名。
- 来源：用户需求。

### I-2: 当前专家与编辑器隔离
- Rule：发起前捕获 username/accountScope/contactId/targetKey/processingId/anchorId/convEpoch/editorRevision/主体快照与请求 seq；回包全部一致才应用。编辑、切专家、切发件范围、换跟进锚点、卸载、登录人变化使旧结果失效。
- Rule：识别出可信引文边界才在引文前插签名；DOM 与服务端纯文本边界不能对应时不修改，提示“无法确定签名位置，请手动补全”。不能把签名放到整封引文之后。
- Applies to：预览、异步应用、组件清理。
- Violation consequence：把 A 专家的姓名插入 B 草稿。
- 来源：既有 templateReferenceIdentity/SeqMatches 约定。

### I-3: 软确认而非硬拦截
- Rule：用户点击发送后，服务端 NORMAL identity findings 显示“称呼与签名需确认”，展示收到的当前值/建议值或不可判定原因。按钮为“返回修改”“确认发送”。确认将同一已冻结请求体带 safetyWarningConfirmed=true 重提；不自动修正文、不重生成草稿、不更换 requestId/draftRef/附件。
- Rule：取消/ESC 返回 false，保持草稿、不提示“发送失败”、不投递。确认只属于当前点击发送的请求，不写入草稿、localStorage 或全局默认；下一次独立发送重新确认。确认期间已有发送锁不得释放成可更改正文再沿用旧确认的状态。
- Rule：姓名提醒不要求输入“确认发送”文字。合并存在的其他 NORMAL 问题避免连弹；如果还有原 STRONG，保留其独立二次确认。
- Applies to：app.js 全局 submitManualRichReply 及 unmatched 事件处理器内同名局部函数。
- Violation consequence：旧入口漏提醒、误发或重复投递。
- 来源：用户明确修正；01 I-3。

### I-4: 保存与恢复使用原草稿链
- Rule：成功插入后只经 handleManualComposeInput→saveDraftFromInputs 使 revision 更新和草稿排队保存；保留跟进锚点、QA/RAG 上下文、附件集合和未改变的会议块。不要手工清空这些字段，不新增持久化键。
- Rule：MATCH/MISMATCH/UNCERTAIN 且没有实际插入时，不制造正文变更或无谓保存。保存冲突/失败沿用现有处理，禁止误显示已保存。
- Applies to：补全后的输入事件、草稿恢复、发送。
- Violation consequence：看似补全成功，刷新丢失或附件脱落。
- 来源：mailbox_reply_draft 写读审计。

### I-5: 身份仍由后端决定
- Rule：请求只给既有 target/anchor/账号范围和当前正文，不自行拼姓名/从历史邮件推断正确签名。返回值只用于本次编辑建议；最终发送仍让 01 重新检查，不信任“前端已校验”标志。
- Applies to：新增 inspect 调用、发送桥接。
- Violation consequence：绕过最终实际邮件校验。
- 来源：K-explicit-outbound-anchor-server-revalidation。

### I-6: 全局设置与草稿隔离
- Rule：每次打开「默认设置」重新 GET 03 配置，候选按类型列出；允许修改选中片段内容，实时渲染当前专家效果（使用01 defaults-preview，不在浏览器替换变量）。保存两个选中 id/content/baseFingerprint，一次 PUT 成功后更新全局摘要；409/失败保留编辑并提示，不假报成功。取消不写；保存不自动补全/覆盖当前草稿。成功后state.replySnippetsLoaded=false，再按既有loadReplySnippets刷新旧管理缓存；此缓存刷新失败不能谎报PUT未成功。
- Rule：切换选择前保留弹窗内草稿；保存只提交最后选中的两个片段，不意外保存未选中片段编辑；关闭后丢弃未保存修改。预览复用01当前发送身份，不读历史发件正文推断姓名。
- Applies to：app.js 设置宿主、mailbox-chat.js 设置入口。
- Violation consequence：不同专家使用不同设置，或切选择意外改了未选片段。
- 来源：用户最新补充。

## 样式契约

### S-1: 工具栏按钮
- 复用：styles.css:802–841 `.button`、mailbox-chat.css:63 `.mc-editor-tools`、:66–71 状态；不修改规则，不新增 CSS class。
- 逐字目标片段，插在原 `${followUpButton}` 后：
```html
<button class="button" type="button" data-action="mc-complete-reply-identity">补全称呼与签名</button>
```
- 外层保持 `<div class="mc-editor-tools">`，工具顺序 B/I/列表/链接/附件/会议/材料/引用模板/跟进/补全；没有跟进时只缺其按钮。
- 状态：请求中按钮 disabled，文字“检查中…”；完成恢复。复用现有发送锁、草稿未加载等可用性控制；点击处理中不能二次发起。
- 禁止：inline style、专属新颜色、新容器 class、改任何既有 CSS。

### S-2: 确认与结果提示
- 复用 index.html:2302–2311 现有 `.action-dialog`、`.action-dialog-body`、`.action-dialog-footer` 和两个按钮；样式引用 styles.css:2873–2922。通过 app.js 新增 `reply-identity-confirm` schema；仅该 schema 覆盖取消/提交文字，cleanup 后恢复原文，不能更改其他调用的默认文字。
- 新增动态消息 DOM（变量必须 escapeHtml；不是接口 HTML）：
```html
<p>本次邮件的称呼或签名需要核对。确认后将按当前正文发送。</p>
<div class="ai-reply-feedback">
  <div class="ai-reply-warning">称呼与当前专家不一致</div>
  <div class="ai-reply-coverage">当前：Dear Alice；建议：Dear Dr Morgan,</div>
</div>
```
- 多条 finding 按相同结构重复两子节点，不新造 class。身份问题不加“命中原句”前缀；其他原风险仍沿用原展示。`sentence` 是后端短摘要，不能解析为 HTML。
- 复用 styles.css:6387–6412 的反馈样式。按钮完成提示用既有 hostShowStatus：“已补全称呼与签名”/“已补全称呼”/“已补全签名”/“称呼与签名已存在，未修改”；有异常追加“存在不一致，请核对”，不能把已存在等同正确。
- 新补全节点严格为 `<p>称呼文本</p>` / `<p>签名第一行<br>签名第二行</p>`，多段拆为多个p；所有文本用textContent，无class/inline style。继承`.mc-editor`；禁止修改共享CSS。S-2同时覆盖这些新增节点。
- openActionDialog对新增schema设置按钮文案后，cleanup恢复原文；注册dialog的cancel和close事件（E13当前没有），所有退出路径用settled标志确保Promise只resolve一次。ESC走取消，不得留下发送锁。既有confirm/confirm-typed仍为原文案。

### S-3: 全局默认设置
- 入口：工具栏下方新增 `<div class="reply-identity-defaults"><span>全局默认</span><strong data-role="reply-identity-default-summary"></strong><button class="reply-identity-settings-link" type="button" data-action="mc-reply-identity-settings">默认设置</button></div>`。
- 弹窗骨架：`<dialog class="action-dialog reply-identity-settings" aria-labelledby="replyIdentitySettingsTitle"><h3 id="replyIdentitySettingsTitle">称呼与签名默认设置</h3><p>所有专家共用这两个片段。修改内容也会影响引用这些片段的模板；已有草稿不会自动改变。</p><div class="reply-identity-settings-grid"><div><label>称呼片段<select name="salutationSnippetId"></select></label><textarea name="salutationContent" aria-label="称呼片段内容" rows="2"></textarea><label>签名片段<select name="signatureSnippetId"></select></label><textarea name="signatureContent" aria-label="签名片段内容" rows="4"></textarea></div><aside class="reply-identity-paper"><h4>当前邮件效果</h4><p data-role="identity-context"></p><div data-role="identity-salutation"></div><p>邮件正文内容保持不变……</p><div data-role="identity-signature"></div></aside></div><p role="status" data-role="settings-result"></p><div class="action-dialog-footer"><button class="button" type="button" data-action="close-identity-settings">取消</button><button class="button primary" type="button" data-action="save-identity-settings">保存默认设置</button></div></dialog>`。动态值用 textContent；原 footer/button 样式仅复用。保存中disabled，关闭/ESC视为取消。
- 新样式须逐字追加：
```css
.reply-identity-defaults{display:flex;align-items:center;flex-wrap:wrap;gap:8px;font-size:11px;color:#94a3b8}
.reply-identity-defaults strong{font-weight:500;color:#64748b;overflow-wrap:anywhere}
.reply-identity-settings-link{border:0;background:none;font-size:11px;color:#315eaf;cursor:pointer;padding:2px 0}
.reply-identity-settings-link:hover{color:#1e40af;text-decoration:underline}
.reply-identity-settings-link:active{color:#172554}
.reply-identity-settings-link:disabled{opacity:.45;cursor:not-allowed}
.reply-identity-settings-link:focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
.reply-identity-settings{width:min(900px,94vw);max-height:90vh;background:#fff;overflow:auto}
.reply-identity-settings .button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.reply-identity-settings .button:focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
.reply-identity-settings>p{font-size:12px;color:#64748b;line-height:1.7;margin-bottom:16px}
.reply-identity-settings-grid{display:grid;grid-template-columns:1.1fr 1fr;gap:24px;margin:20px 0}
.reply-identity-settings-grid label{display:block;font-size:12px;font-weight:600;margin-bottom:8px}
.reply-identity-settings-grid select,.reply-identity-settings-grid textarea{display:block;width:100%;border:1px solid #dce4ef;border-radius:7px;padding:9px 10px;background:#fff;color:#475569;font-family:inherit;font-size:12px;line-height:1.7}
.reply-identity-settings-grid select{margin-top:8px}
.reply-identity-settings-grid textarea{margin:8px 0 20px;resize:vertical;min-height:74px}
.reply-identity-settings-grid :is(select,textarea):focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
.reply-identity-settings-grid :is(select,textarea):disabled{opacity:.45;cursor:not-allowed}
.reply-identity-paper{padding:18px;border:1px solid #e0e7f0;border-radius:10px;background:#f8faff;font-size:12px;line-height:1.9;color:#50637d}
.reply-identity-paper h4{font-size:12px;margin:0 0 5px}
.reply-identity-paper p{font-size:11px;color:#94a3b8;margin:10px 0 20px}
.reply-identity-paper div{white-space:pre-wrap}
@media(max-width:760px){.reply-identity-settings-grid{grid-template-columns:1fr}.reply-identity-settings{width:94vw}}
```
- 视觉参考：线上交互预览和 docs/mockups/reply-identity-preview/settings-preview.jpg；参考是预览，实施以本契约为准，不把 demo 脚本复制为生产业务。


### S-4: 状态与新增DOM覆盖表
- 复用：S-1 `.button`；S-2 `.action-dialog`/`.ai-reply-feedback`/`.ai-reply-warning`/`.ai-reply-coverage`；S-3已声明CSS。不增加本节以外class/inline style，不改既有class规则，因此无需变更共享class使用点。
- DOM归属：工具栏新button→S-1；动态确认p/div及现有footer两按钮文字→S-2；插入编辑器的p/br/text→S-2；全局摘要div/span/strong/button、dialog/h3/p/grid/div/label/select/option/textarea/aside/h4/status/footer→S-3。option动态值一律textContent/value。
- 新settings dialog由app.js挂载到document.body，单实例；关闭remove并清理事件，不能嵌进原actionDialog的form。`index.html`本阶段只改缓存键。原共享dialog结构逐字基线为index.html:2302–2311（见E26）。
- 文案状态：摘要初始“全局默认：加载中…”；失败“默认设置加载失败”但仍可点设置重试；没有默认“称呼未设置 / 签名未设置”；设置预览加载“预览中…”；无法解析“无法核实当前身份，请检查资料”；409使用03固定文案；保存成功“默认设置已保存，已有草稿未改变”。结果写role=status文本节点，不借color单独表达状态。
- 设置关闭/ESC在未发送PUT时视为取消；PUT已发出后禁用关闭/ESC并显示“保存中…”，直至服务端响应，不把关闭说成撤销已提交操作。网络结果未知时保留编辑并提示“保存结果未确认，请重新加载核对”，重新GET后才能再提交，避免重复覆盖。

## 现状审计

### 编辑器与发送
- mailbox-chat.js:5510–5534 当前工具栏末段 `${meetingTrigger}${materialTrigger}${templateReferenceTrigger}${followUpButton}`；跟进按钮判断 sentCount>0。
- followupGreeting:6715 直接复用所选历史邮件第一行 Dear/Hi，否则 Dear Professor；followupBodyText 拼 Best regards 和宿主 senderName。此计划不改生成器，补全/发送判断不能把这些输出当权威。
- followupQuoteText:6707 为 On 时间/accountCode wrote + 历史纯文本，applyFollowUpFromDialog:6903 用 innerText 写整封跟进草稿；因此只识别 blockquote 不足，需要与实际 anchor 对应的纯文本引用边界。
- 普通来信 mcHostSendRichReply 与会话 mcHostSendConversationRichReply 都进 app.js 全局 submitManualRichReply:21278；旧 unmatched 内另有同名局部函数:18171，必须同改提示文案/取消语义。
- 后端异常已有 code/findings/severity/sentence；sentence 上限 200 字，UI 如实展示短差异，不假设完整签名都在异常里。

### 草稿存储读写（不改 schema；SQL E19/E27，Map写点E28）
- 前端临时草稿：currentTargetKey/currentDraftsMap；正文由 readManualValues 采集。手输、格式编辑、AI 采用、跟进全文替换、模板引用、材料插入、会议插入/移除都最终进入 saveDraftFromInputs 或其统一 handleManualComposeInput。附件集合经自己的 setOutboundAttachmentItems 写入同一 owner 的草稿。
- 读取：render 人工编辑器、会议块恢复、附件卡、sendCurrentManualReply 发送快照；目标切换后的恢复也从该 map/服务端 draft 读取。
- 服务端 mailbox_reply_draft schema 和所有写读入口见 01 现状审计；本阶段只使用现有保存链，新增补全没有额外 API 写路径。
- interaction P1：inspect 服务端片段+真实账号 → 当前编辑器；迟到回包必须丢弃。
- interaction P2：补全 → 草稿保存 → 切换/恢复 → 最终发送，保持 html/text/版本一致。
- interaction P3：两类发送宿主 + 旧 unmatched 局部发送 → 同一确认语义；取消不会误清状态。

### 前端样式盘点
- 改前 DOM 原文：
```html
<div class="mc-editor-tools">
```
```js
${meetingTrigger}${materialTrigger}${templateReferenceTrigger}${followUpButton}
```
```html
<button class="button" type="button" data-action="mc-open-followup">↗ 跟进邮件</button>
```
- 改前 CSS 原文（mailbox-chat.css:63/66/67）：
```css
.mail-chat .mc-editor-tools{display:flex;flex-wrap:wrap;gap:6px}
.mail-chat .button{white-space:nowrap}
.mail-chat .button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
```
- 基准：button 高度32px、字号12px、水平padding11px（mailbox-chat.css:147覆盖全局12px）、radius-sm=7px；主文字#1e293b、border rgba(15,23,42,.11)、hover 背景rgba(15,23,42,.022)，hover translateY(-1px)；全局hover shadow为0 2px 6px rgba(15,23,42,.08)，但邮箱非primary按钮被mailbox-chat.css:148覆盖为none，active translateY(0) scale(.97)/opacity .85。focus outline 2px #3b82f6、offset 2px。
- 弹窗 padding20px、width min(500px,90vw)、背景 rgba(255,255,255,.97)，footer gap8px；warning 字号12px、line-height1.5、padding8px 10px、颜色#d97706、背景rgba(217,119,6,.08)、边框rgba(217,119,6,.2)。全部仅复用，CSS 使用点无需修改。
- index 当前 16 项版本化 CSS/JS 为 `20261020-apollo-matching-v2`；精确搜索该键在 src/test 无匹配（命令与exit=1见E18；不是测试执行结果）。发布统一为同一新键，不照旧知识固定资源数量。（来源：K-frontend-cache-key-triad）

新增写路径：设置宿主 PUT 03 endpoint → 现有 reply_snippet 默认/内容 → 01/既有 frame；必须一并覆盖新的 I-6/S-3。线上片段核对详见03；已有草稿不随保存改写。

### 证据修正与交互补充

- E13记录`openActionDialog:20423–20466`只监听按钮click/form submit，没有native cancel；初稿只写“ESC取消”并非已有能力。必须实现并测试，不以浏览器默认关闭冒充Promise完成。
- E13记录两个submitManualRichReply；旧局部取消会throw并alert失败，成功尾部还刷新详情。新实现取消必须return false且不经过刷新/清QA成功分支。
- E16完整覆盖规则证明邮箱按钮padding11px、非primary shadow:none；初稿只摘全局12px并不代表最终样式。css完整逐字规则见E15/E16，实施按实际级联验收。
- E14/E19证明已有草稿保存与发送捕获所有权；仅捕获contactId不足。沿用ownerKey、draft map引用、targetKey、convEpoch和editorRevision。（来源：K-mailbox-draft-cache-owner-capture）
- P4：设置PUT→全局默认、旧片段cache及按ID模板预览；与当前草稿保持独立。P5：未保存内容→defaults-preview→设置纸面效果，必须隔离迟到响应与另一个专家/选择。

## 实现方案

1. mailbox-chat.js 增加 S-1 按钮、事件和 seq/owner 快照；按 source 分支调用 01 inspect。校验当前上下文不变后局部插入 MISSING 的纯文本节点；复用既有输入保存链（I-1/I-2/I-4/I-5）。在卸载/切目标时废弃结果。
2. app.js 注册 S-2 schema、warning code 中文标签和统一 findings 渲染；两个 submitManualRichReply 的身份提示共享此 helper。只有本次明确确认后克隆当前冻结 body 加确认字段重提，取消保留草稿并返回，不标发送失败（I-3）。保留原 STRONG 分支。
3. index.html 统一 E17 列出的16项资源缓存键为 `20261021-reply-identity`；实施时按当前键重查固定字符串测试，若已变更先修订文件清单。保持资源数量、顺序、原弹窗 DOM 不变（S-1/S-2）。
4. app.js 增加全局设置宿主 GET/PUT、选择/编辑/预览和冲突处理；mailbox-chat.js 仅挂载入口，styles.css 仅追加 S-3。按 I-6 只保存选中片段。
5. 新建 mailboxReplyIdentity.test.js：测试真实按钮绑定、inspect 请求上下文、局部插入和版本保存、所有迟到响应场景、重复点击、引文前插入、确认/取消/二次发送、旧 unmatched 入口；DOM stub 必须支持 Range/节点分割等实现真实使用的行为，不能只做源码 contains 断言。现有 mailboxChatBehavior/mailboxChatStyle 和全量 JS 回归必须通过（I-1..I-6/S-1..S-4）。


### T6 异步与局部插入落地步骤（I-1/I-2/I-4/I-5；S-1/S-2/S-4）

文件：mailbox-chat.js、app.js。

1. 增加宿主桥 `mcHostInspectReplyIdentity(payload)`、`mcHostPreviewReplyIdentityDefaults(payload)`，分别POST01两个接口；`mcHostOpenReplyIdentitySettings(context)`接设置弹窗。sourceKind与实际发送分支同源：有followUpAnchor时CONVERSATION，即便当前选中有来信；否则outbound为CONVERSATION，inbound为INBOUND。账号使用已有templateReferenceSenderAccountCode/发送解析规则，不从列表第一账号猜。
2. 补全开始先readManualValues与saveDraftFromInputs采集现稿；捕获当前owner/draftMap/target/epoch/revision/subject/html/text/anchor/accountScope及seq。设置preview另有dialogSeq+editSeq；弹窗失活即丢弃回包。全局配置请求无永久缓存；跨专家切换不套用上次渲染文本。
3. 收到结果先比较捕获快照。若不同，显示“正文或会话已变化，请重新检查”，不写DOM。构造待插节点在离线fragment，先确定两个位置都合法，再一次同步应用；不出现第一项已插而第二项定位失败却无提示。可单独补称呼而签名不可判定时，需明确结果“已补全称呼；无法确定签名位置，请手动补全”。
4. 无引文：首端insertBefore首个有效节点；签名append到当前body末尾。有引文：对正文DOM执行不改动的TreeWalker/Range文本映射，找到服务端canonicalQuotedTail对应唯一尾部，先验证quote正文一致，再在引文起始节点前插入；若起点在文本节点中，用Range.insertNode做局部分割，保留其他节点/属性。如跨不支持节点/有多个候选/和textBody边界不等价则不补签名。不重建整封innerHTML或innerText。
5. 有插入才调用handleManualComposeInput(editor)一次；由其触发revision/meeting检测/saveDraftFromInputs。重新读取当前草稿以保留插入等待期间完成的附件上传。没有插入不保存；结束在finally恢复按钮，仅对仍属于原目标的UI恢复状态。
6. 会话切换、retargetManual、unmount使seq失效；保存PUT是已提交的全局动作，切会话不把保存响应套到新草稿，仍提示实际保存结果。正文不可编辑/草稿未加载/发送锁中禁用补全与设置触发（全局设置以后可在独立入口开放，本轮不扩大）。

### T7 确认行为与冻结请求（I-3；S-2/S-4）

文件：app.js、mailbox-chat.js。

- 两发送入口使用同一finding-label/render/confirm helper；识别REPLY_*身份code，schema为reply-identity-confirm；仅其它风险时保留现有confirm文案。所有动态片段escapeHtml；sentence不得作为HTML。
- 发起前深拷贝可JSON序列化requestBody，冻结数组与嵌套对象，保持requestId/draftRef/templateTextBody/templateHtmlBody/附件顺序。确认重试只添加safetyWarningConfirmed及既有strongConfirmationText；不得重新读取编辑器或生成新的requestId。本轮确认是确认当前提交快照，后台对原变量的现有渲染语义不变。
- mailbox路径维持既有发送锁；旧unmatched路径为该次提交设置局部busy并禁用重复发送直到finally，不能用永久全局“已确认”。旧局部取消return false、不执行18235附近详情刷新；成功按原流程执行。两个入口独立重复点击不能并行发送。
- 第一/第二弹窗取消或ESC均无失败alert且原请求停止；第二层STRONG仍按原逻辑显示。真实网络/校验错误仍报错，不能误把所有异常当取消。取消后的会话draft刷新只允许读取状态，不能关闭草稿。

### T8 全局设置操作（I-6；S-3/S-4）

文件：app.js、mailbox-chat.js、styles.css。

- 打开GET03；选中当前默认。无默认时select显示“请选择”，不自动提交第一项；缺少该类型候选时显示“请先在回复片段管理中创建并启用片段”，保存disabled。
- 内容输入采用300ms debounce调用defaults-preview；将选中id/content及当前上下文传后端。只渲染最新dialogSeq/editSeq，预览失败保留输入、显示原因。预览不是保存前硬门槛；语法合法但资料缺失仍可保存全局模板供其他专家使用。
- 弹窗按id保存局部文本草稿以支持切换回来；PUT只发送最终两个选中项+打开时baseFingerprint。409不替换输入，只提示重新加载核对；重新加载通过关闭再打开弹窗重新GET，由操作者重新编辑，不隐式合并覆盖。
- PUT成功更新摘要，invalidate旧replySnippets缓存并调用既有刷新；保留正文。UI说明同时影响引用所选片段的模板。保存成功后的预览以服务端规范化内容为准；取消未保存修改不进入任何请求。

## 变更文件清单

共 5 个文件；只追加 S-3 样式，不改数据库。

|文件|用途|
|---|---|
|src/main/resources/static/mailbox-chat.js|按钮、请求隔离、局部补全|
|src/main/resources/static/app.js|确认提示与发送重提、取消语义|
|src/main/resources/static/index.html|统一资源缓存键|
|src/main/resources/static/styles.css|仅追加 S-3 设置弹窗样式|
|src/test/js/mailboxReplyIdentity.test.js|交互与集成回归|

## 验收标准

- I-1：两个缺失/单项缺失/均已有/错名/重复/空白和 HTML 格式矩阵；再次点击无变化，原节点未被全文替换。
- I-2：切专家、账号范围、锚点、编辑、组件卸载、退出登录后旧结果全部不落地；错引用边界不自动插入。
- I-3：确认后重提恰一次、内容及 requestId 不变；取消/ESC 无重提、保留草稿、无失败 alert；下次独立发送再次提示；身份问题没有 typed confirm，原 STRONG 保留。
- I-4：补全后刷新可恢复，附件/会议/引用/主题/跟进锚点未变；保存冲突仍按原有 UI 显示。
- I-5：无身份客户端覆盖字段，最终 POST 不包含“校验通过”永久标志；检查和最终片段变更以最终发送提示为准。
- S-1/S-2/S-4：DOM class、按钮顺序与文案逐字核对，无新 CSS/inline style；共享其他弹窗按钮文字恢复；全部资源同键。
- 运行 `node --check src/main/resources/static/mailbox-chat.js`、`node --check src/main/resources/static/app.js`、`node --test src/test/js/*.test.js`。真实浏览器执行下列人工场景验证 DOM 与布局，纯 stub 通过不等于验收完成。

- I-6/S-3：两专家/两登录会话共用设置；选择/编辑/取消/保存冲突矩阵；未选片段不写；CSS逐字核对。

## 人工验收清单

### A-1: 双缺项与单缺项
- 前置条件：测试专家 Daniel Morgan、发件人 Alex Lee、正确默认片段；先写普通正文 Thank you for your reply.。
- 操作步骤：1. 点补全。2. 再点。3. 分别删除称呼/签名后再点。
- 预期结果：首次只增加首尾，第二次字符数不变；单项删除只补一项。已有正文内容不变。
- 覆盖：I-1、P1、按钮需求。

### A-2: 已有错名不覆盖
- 前置条件：正文首行 Dear Alice，签名 Bob。
- 操作步骤：1. 点补全。2. 点发送。3. 返回修改。4. 再点发送，确认发送至自有测试邮箱。
- 预期结果：补全不覆盖 Alice/Bob；弹窗显示当前与建议值；取消保留正文；确认后邮箱收到原 Alice/Bob 正文 1 封，不要求输入文字。
- 覆盖：I-1/I-3、P3、软确认需求。

### A-3: 富文本与引用
- 前置条件：草稿含粗体、链接、列表、上传附件、一个已核验跟进锚点及历史引用，缺当前签名；另准备带会议块的普通回复。
- 操作步骤：1. 补全签名。2. 切专家再切回。3. 刷新页面。4. 对会议草稿补全。
- 预期结果：签名位于本次正文末尾、引用之前；原格式、引用、主题、锚点、附件保留；刷新仍存在；会议块未改动、不误失效。
- 覆盖：I-1/I-2/I-4、P2、正文/附件/草稿/线程保持项。

### A-4: 迟到请求
- 前置条件：浏览器网络节流，专家 A/B 两份草稿。
- 操作步骤：1. A 点补全立即切 B。2. 重复但改为编辑 A 正文。3. 重复但换锚点/账号范围。
- 预期结果：各旧请求回来均不写入当前编辑器，不覆盖刚输入内容；可重新点补全获得当前内容。
- 覆盖：I-2/I-5、P1。

### A-5: 不可判定及确认范围
- 前置条件：禁用默认签名片段，另准备旧 unmatched 详情入口草稿。
- 操作步骤：1. 点补全。2. 在聊天和旧详情分别点发送并确认。3. 新建另一封相同问题邮件再发送。
- 预期结果：补全说明无法核实，不虚构内容；两个入口均能确认继续；新邮件仍提示，不永久记住忽略。
- 覆盖：I-3/I-5、P3。

### A-6: UI 与共用弹窗回归
- 前置条件：桌面及760px宽度；有跟进/无跟进两种可编辑会话。
- 操作步骤：1. 检查按钮位置及 hover/disabled/focus。2. ESC 取消身份提示。3. 打开既有“确认操作”。4. 触发原 STRONG 测试场景。
- 预期结果：按钮12px/高32px/工具栏6px间隔，窄屏自然换行；跟进右侧或工具栏末尾；ESC 保留草稿；其他弹窗仍显示原“取消/确认执行”；STRONG 仍有原二次确认。
- 覆盖：S-1/S-2、I-3、共享界面保持项。

### A-7: 全局修改入口
- 前置条件：测试环境存在线上同名的「称呼」「称呼-企业」「致敬-签名」三个片段，两名测试专家。
- 操作步骤：1. 点默认设置。2. 选择称呼-企业并编辑正文。3. 保存。4. 切换另一专家补全。5. 再打开修改后取消。
- 预期结果：当前效果随选择/内容变化；保存后两名专家共用新片段，各自变量不同；原草稿不被覆盖；取消不保存。只保存最后选中两项。
- 覆盖：I-6/S-3、全局配置新增写读路径。


### A-8: 设置冲突、预览延迟与内容范围
- 前置条件：两个测试浏览器打开设置；测试网速限为Slow 3G；称呼甲乙均可选。
- 操作步骤：1. 修改甲内容，切乙并修改乙。2. 等待预览。3. 另一浏览器保存全局默认。4. 第一浏览器保存。5. 重新加载核对，重新编辑只保存乙。
- 预期结果：预览显示最后选中乙，不被甲迟到响应覆盖；步骤4提示“默认设置已被修改，请重新加载后核对”，输入保留；步骤5只改乙和当前签名，甲未选编辑未落库；已有正文未改变。
- 覆盖：I-2/I-6、P4/P5、S-3/S-4。

### A-9: 无默认、保存失败与弹窗样式
- 前置条件：测试环境禁用默认称呼；桌面1200px及手机390px；关闭网络可模拟失败。
- 操作步骤：1. 打开设置。2. 检查无默认显示。3. 选择启用候选、修改签名，断网保存。4. 关闭弹窗重新联网打开。5. 检查窄屏布局与焦点。
- 预期结果：无默认不会悄悄选中并保存；失败保留输入且不显示成功；重新打开读服务端值；390px单列、弹窗94vw，背景#fff，表单12px，焦点2px #3b82f6，禁用opacity .45；摘要长名称可换行，不撑破编辑器。
- 覆盖：I-6、S-3/S-4、设置可观察结果。

### A-10: 两发送入口取消与改稿
- 前置条件：新邮箱会话和旧未匹配邮件详情各准备一份错名测试草稿；提交目标均为自有邮箱。
- 操作步骤：1. 在每个入口发送、按ESC。2. 再发送、点返回修改。3. 修改正文后再发送并确认。4. 开新的独立草稿再触发错误。
- 预期结果：前两步测试邮箱0封、无“发送失败”alert、输入与QA上下文保留、发送按钮恢复；步骤3恰1封且为本次提交正文；步骤4再次提醒，不沿用旧确认。STRONG分支仍要求原文字确认。
- 覆盖：I-3/I-4、P3、S-2/S-4、原共享弹窗保持项。
