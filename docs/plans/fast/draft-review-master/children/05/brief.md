# Child 05 — Approved Execution Brief

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master
- Branch: fast/draft-review-master
- Exact plan: docs/plans/2026-10-29/draft-review-05-admin.md
- Plan identity: sha256:1874295ce3c15424326c215636f4c9efd1f58cc525318f601d2cc339712b2c95
- Approval: User invocation `/fast-p docs/plans/2026-10-29/draft-review-master.md` on 2026-10-29 authorizes execution of these exact plan bytes despite historical DRAFT headings. Do not amend plan bytes.
- Dependencies: 01,02,03,04; dispatch only after predecessor checkpoint.
- Product base: controller supplies the exact child_base_sha at dispatch; evidence HEAD may be later.
- Execution report: docs/plans/fast/draft-review-master/children/05/execution.md

## Global Constraints
Read the exact child plan completely from disk and use execute-p. Its authorized file table is exhaustive; no changes outside that table. Read docs/design.md before mail/conversation changes and applicable repository guidance. Master invariants I-23/I-24 apply: sequential incremental work; existing shared tables gain zero columns; preserve existing mail, admin drafts, restricted reader access, and unrelated user changes. No deployment, push, merge, history rewriting, or extra worktrees. No plan amendments without human approval. Evidence docs are separately authorized only in the designated fast-p report files; exclude them from product commits.

One writer at a time. Do not run build/lint/tests/formatters mid-flight; collect all required fresh commands and smoke proof once implementation is complete. Do not fix unlisted files or silently narrow requirements. Report PLAN_CONFLICT/BLOCKED with exact evidence if necessary. Full verification is deferred; do not invoke verify-p, review-p, repair-p or fix-v inside this loop. The controller will dispatch an isolated four-gate verifier.

## Runtime and Commands
JDK11: /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home. Docker daemon is available via OrbStack. For MySQL/Flyway ITs set DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock and pass -DargLine=-Dapi.version=1.41 when needed. Run every command from the child plan freshly, all mandatory ITs enabled and non-skipped. Product smoke must exercise the changed API/UI, not only tests; use throwaway scenarios and clean those afterward. Browser evidence for 04/05 must use actual UI; do not substitute source inspection or fake localStorage. Test SMTP only; never production recipients.

## Authorized Requirements and Downstream Interface
The exact plan below defines all tasks, invariants, authorized files, public DTOs/methods, tests, styles, and acceptance. Its quoted historical source line numbers are pointers, not permission to replace newer predecessor code. Preserve exact planned DTO and API names for downstream children. Necessary predecessor execution evidence is available in earlier child artifact directories; read only the dependency outputs needed by this child, not later plans. Required source evidence is in docs/plans/2026-10-29/draft-review-evidence/.

# 05 · admin待审核筛选与共用编辑框

状态：DRAFT / 待用户批准实施。仅开发计划，不代表代码已实现或测试已通过。
基线：`main@b15bb069f43f89c45ee6859dba04a0baff5d774d`，2026-10-29 工作树；原有脏文件见 [状态快照](draft-review-evidence/worktree-status.txt)，不得覆盖。
证据：[逐字源码 E01–E31](draft-review-evidence/source-excerpts.md)、[数据库迁移链](draft-review-evidence/schema-chain.md)、[读写调用全集](draft-review-evidence/06-repository-call-sites.txt)、[SQL/存储引用全集](draft-review-evidence/05-all-store-read-write.txt)、[来源 SHA256](draft-review-evidence/sha256.json)。文中“新增/拟”都是设计，不声称已存在。

前置：01–04门禁通过；在04产物上执行，不并行修改app.js/styles.css/index.html。

## 需求描述
1. 顶部“待审核 N”按钮切换为待审专家列表，每专家显示待审数量；点专家只切右侧真实会话。
2. 审核与人工回复共用原富文本框，区分admin自己草稿和user1提交草稿；审核前无发送按钮，批准后仍须手动发送。
3. 正文下方按历史邮件样式翻译、评论、查看变动历史；已审核且对话更新时弹确认框，确认不发送。
必须保持：管理员原富文本工具/草稿/附件/人工发送，原时间线/筛选/chip；user1仍受限且不显示admin工具。
不做：草稿列表弹窗、独立审核中心、第二富文本框、前端localStorage持久化、自动发送。
本步6文件、1子系统（admin邮箱前端）；后端依赖01–03；已有共享存储新增字段0。

## 关键不变量
继承01 I-1–I-7、02 I-8–I-10、03 I-11–I-15；新增：
### Invariant I-19: toolbar筛专家
- Rule: 只有一个待审核按钮，文字待审核 N，N取服务端reviewPendingTotal；点击激活reviewPendingOnly，先保存原草稿、将chip置全部、页码归0；保留搜索/账号/显式条件。左侧仍是专家卡，每卡待审核M，选卡只切该专家右侧。点任一旧chip退出待审模式。
- Applies to: toolbar/app bridge/conversationsParams/renderExpertList/refresh。
- Violation consequence: 退回草稿弹窗设计或计数/列表口径不同。
- 来源: original

### Invariant I-20: 共用一个编辑器且不污染原草稿
- Rule: 复用原manual-compose中唯一mc-editor。mode=manual或review(draftId/version)，不能把用户正文塞进管理员draftMap。进入review前flush原dirty草稿，失败不切换；review模式屏蔽原input autosave、发送、附件/会议/模板/签名写入回调；退出从捕获的owner/target恢复原草稿，原附件保持，review不继承附件。
- Applies to: renderManualSectionInto/manualInputs/saveDraftFromInputs/sendManualReply及所有工具回调。
- Violation consequence: 覆盖admin正文、串附件或把未审核内容从旧发送口发出。
- 来源: K-mailbox-draft-cache-owner-capture；E15/E25

### Invariant I-21: 审核正文及发送按钮矩阵
- Rule: manual模式保持原发送；review的SUBMITTED/RETURNED/DRAFT没有发送按钮（hidden/不渲染，非仅disabled）。APPROVED显示“发送已审核回复”，只调用03专用send；SENT后显示已发送且不再显示发送按钮；UNKNOWN显示待确认不可重发。批准操作只更新状态/计数，不能自动调用send。
- Applies to: 模式渲染、approve、send handler及键盘快捷键。
- Violation consequence: 未审发送或审批自动发信。
- 来源: original

### Invariant I-22: 对话确认、翻译与异步安全
- Rule: 打开已审核尚未发送草稿时context变更弹确认框；点击发送前再查context，服务端409也弹；确认只POST confirm-context并保存eventId，不调用send；取消/Escape清凭据、返回最新对话，下次发送仍确认。切专家/草稿/session清确认与翻译、评论面板；所有回包校验owner/target/draft/version/epoch。翻译使用现有小文字按钮，结果只作为译文，不写原正文。
- Applies to: selectDraft/context/send/confirm/translation/comments/events/unmount。
- Violation consequence: 取消却允许发送、旧译文覆盖正文或旧确认串专家。
- 来源: K-mailbox-draft-cache-owner-capture；E22

## 样式契约
### S-3：toolbar与专家卡
复用 `index.html:799` 的 `.panel-head-actions`，插在检查回复按钮之前，**不删改现有按钮**。仅admin邮箱聊天模式显示；未挂载/任务钻取/reader隐藏。按钮切换primary类，不新增按钮颜色。
```html
<button class="button" id="mailboxReviewPendingBtn" type="button" aria-pressed="false" hidden>待审核 <span data-role="review-pending-count">0</span></button>
```
数值加载失败显示“待审核 · 加载失败”，允许点刷新重试，禁止伪装0。初次加载显示“待审核 …”并disabled。专家卡内沿原 `.mc-person-heading` 追加：
```html
<span class="mc-badge" data-tone="pending" data-role="review-pending-count">待审核 2</span>
```
只有>0才显示，name/meta原位置不改。aria-pressed=true时button增加primary；hover/active沿styles.css:802–872，卡片active沿mailbox-chat.css:16–23。不新增CSS。

### S-4：原编辑器的模式、审核与消息式翻译
原DOM完整基线E15；保留一个 `[data-role="manual-compose"]` 和一个 `[data-role="mc-editor"]`。在主题label之前插下列controls（复用04 S-1逐字CSS，不再重复定义）：
```html
<div class="mail-review-controls">
  <span class="mc-badge" data-role="review-state">admin 自己撰写</span>
  <label>当前内容
    <select class="mail-review-select" aria-label="切换回复草稿" data-role="review-selection">
      <option value="manual">我的人工回复（admin）</option>
      <option value="review:ID">已提交 · 草稿 #编号（user1）</option>
    </select>
  </label>
  <button class="mc-text-button" type="button" data-review-action="more-drafts">加载更多草稿</button>
  <button class="mc-text-button" type="button" data-review-action="history">草稿历史</button>
</div>
<p class="mc-note" data-role="review-context" hidden>user1 提交 · 审核通过后才显示发送按钮</p>
```
在既有editor之后、原附件区域之前插footer及同一讨论区（review模式才显示）：
```html
<div class="mail-review-footer" data-role="review-footer" hidden>
  <button class="mc-text-button" type="button" data-review-action="translate">翻译</button>
  <button class="mc-text-button" type="button" data-review-action="comments">评论 0</button>
  <button class="mc-text-button" type="button" data-review-action="events">变动历史</button>
</div>
<div class="mc-translation" data-role="review-translation" hidden></div>
<div class="mail-review-panel" data-role="review-discussion" hidden>
  <article class="mail-review-entry"><strong>操作者 · 时间 · 动作</strong><div class="mc-body">评论或历史文字</div></article>
  <div class="mc-body" data-role="review-version-body"></div>
  <textarea class="mail-review-input" aria-label="评论内容" maxlength="2000"></textarea>
  <button class="button" type="button" data-review-action="comment">发布评论</button>
  <button class="mc-text-button" type="button" data-review-action="more-events">加载更早历史</button>
</div>
<div class="mail-review-panel" data-role="review-history" hidden>
  <article class="mail-review-entry"><span>草稿 #编号 · 状态 · 时间</span><button class="mc-text-button" type="button" data-review-action="select">打开草稿</button><button class="mc-text-button" type="button" data-review-action="events">变动历史</button></article>
</div>
<div class="mail-review-actions" data-role="review-actions" hidden>
  <button class="button" type="button" data-review-action="return">退回修改</button>
  <button class="button primary" type="button" data-review-action="approve">审核通过</button>
</div>
<div class="mail-review-panel" data-role="review-return" hidden>
  <label>退回原因<textarea class="mail-review-input" aria-label="退回原因" maxlength="2000"></textarea></label>
  <div class="mail-review-actions"><button class="button" type="button" data-review-action="cancel-return">取消</button><button class="button primary" type="button" data-review-action="confirm-return">确认退回</button></div>
</div>
```
讨论区/草稿历史/退回均inline默认隐藏；删除草稿只给历史入口，不可加载为可发送编辑器。SUBMITTED显示审核按钮，RETURNED显示退回原因，APPROVED显示“已审核 · 尚未发送”或真实发送状态。主题input在review模式disabled，editor contenteditable=false，原富文本工具disabled并拦事件；原附件/放弃草稿/保存状态行在review隐藏。manual切回恢复原DOM行为。
原footer发送按钮沿用button primary，但review APPROVED使用 `data-review-action="send"`，移除原 `data-action="mc-send-manual"`；manual恢复原属性。待审模式不渲染该button。翻译按钮位于正文下方，mc-text-button :109–112原样复用，译文mc-translation :119；文案“翻译 / 翻译中… / 收起译文 / 翻译失败，重试”。禁止换大按钮或弹出翻译窗口。
禁止新增第二个富文本框、草稿审核弹框、inline style、未声明class、改原mc规则。

### S-5：仅对话更新使用确认框
用原生dialog.showModal()，挂在当前.mail-chat中继承按钮样式；动态内容用textContent。不是草稿列表弹窗。
```html
<dialog class="mail-review-confirm" aria-labelledby="mailReviewConfirmTitle" data-role="review-context-confirm">
  <h3 id="mailReviewConfirmTitle">对话已有更新</h3>
  <p data-role="review-confirm-expert">该专家的往来邮件在审核后发生了变化。</p>
  <p>请查看最新对话，确认这份回复仍然适用。确认后仍需手动点击发送。</p>
  <p class="mc-error" role="alert" data-role="review-confirm-error" hidden></p>
  <div class="mail-review-actions">
    <button class="button" type="button" data-review-action="context-cancel">返回查看最新对话</button>
    <button class="button primary" type="button" data-review-action="context-confirm">确认回复仍适用</button>
  </div>
</dialog>
```
首次焦点“返回查看最新对话”；Tab限制dialog；Escape等同取消；关闭后恢复触发按钮/草稿选择器焦点。不得把SHA摘要显示为“v2/v3”虚构版本；文案用“审核后有更新”。更新确认中按钮disabled并显示请求状态，失败仍停留且无凭据。
新增CSS逐字追加styles.css：
```css
.mail-chat .mail-review-confirm{width:min(480px,calc(100vw - 32px));max-height:85dvh;overflow:auto;margin:auto;padding:20px;border:1px solid #d9e3f1;border-radius:14px;background:#fff;color:#475d79;box-shadow:0 20px 90px #17325730}
.mail-chat .mail-review-confirm::backdrop{background:#172c4738;backdrop-filter:blur(2px)}
.mail-chat .mail-review-confirm h3{margin:0 0 12px;font-size:16px;font-weight:600;line-height:1.5}
.mail-chat .mail-review-confirm p{margin:0 0 12px;font-size:12px;line-height:1.8;overflow-wrap:anywhere}
.mail-chat .mail-review-confirm .mail-review-actions{margin-top:16px}
@media(max-width:760px){.mail-chat .mail-review-confirm{padding:16px}.mail-chat .mail-review-confirm .button{min-height:44px;height:auto;white-space:normal}}

```
交互控件沿button现有hover/active/disabled/focus，dialog布局无交互变体。背景必须#fff不透明，不能复用--panel-bg。S-3/S-4类全为复用，没有其它新CSS。

## 现状审计
### 前端样式盘点 / 改动前逐字基线
- HTML原文：E14=`mailbox-readonly.js:137–222`，E15=`mailbox-chat.js:5800–5870`，E16=`index.html:788–815`；CSS完整原文E17/E18/E26，并附 `styles.css:802–872/979–984` 到证据。不是重画原型壳，也不导入原型inline CSS。
- 样式覆盖链：`mailbox-chat.css:60–65` 是composer/editor原定义，`:130`把editor高度覆盖为min100/max240；`:145–146`将按钮覆为32px高、11px横padding、12px字、7px角；移动断点760px来自同文件及 `styles.css:12586–12588` 的44px按钮覆盖。
- 复用：`.mc-text-button` :109–112，正文 `.mc-body` :115，译文 `.mc-translation` :119，badge :29–32/:105–106，列表active :16–23，`.mc-note/.mc-error` :58–59，`.mc-compose` :60–65，`.button` styles.css:802–872，toolbar actions :979/12868。不得修改这些规则及其它使用点；新增均派生mail-review-*类。
- token实值：primary #1e40af，hover #1e3a8a，active #172554，bright #3b82f6；text #1e293b，muted #94a3b8；border rgba(15,23,42,.11)，surface rgba(15,23,42,.022)，radius-sm 7px；panel-bg rgba(255,255,255,.55)透明，不用作确认框背景。翻译按钮11px/1.5，3px 0 padding；译文12px/1.85、12px 14px padding、背景#f3f7ff。
- 三键及全部既有版本资源当前均为20261029-user1-mail-access；`rg -n -F '20261029-user1-mail-access' src/test`无命中（exit1）；执行前重查。没有新增script/link，无需调整资源数量断言。来源 K-frontend-cache-key-triad、K-js-test-invocation-surface。
- `mailboxChatStyle.test.js:166`检查class归属，不是全文件CSS字节锁；新增mail-review-*放styles.css，原mc-*只复用。来源 K-mailbox-chat-css-byte-contract。

### 前端状态与触发点
- E15与`renderManualSectionInto:5633`：现有唯一manual compose；E25 `sendManualReply/sendSavedManualReply`会flush原持久化草稿并携带draftRef，不可让review模式继续触发它。
- `mailbox-chat.js:6869` saveDraftFromInputs读取当前DOM后写getDraft/setDraft，保留meeting/attachments/QA。review内容若混入原DOM而不在此及调用者守卫，会被自动保存成admin原草稿。
- 已有Map的全部写入口检索见UI收据：draftOwners/draftSaves、saveDraftFromInputs、附件更新/发送清理、retargetManual、applyOptions、destroy；review维护独立instance.review对象（mode/draftId/version/detail/context/confirmationEventId/translation/requestSeq），不复用旧draftRec.readOnly充当权限。
- `mailbox-chat.js:4986/5095/5542`已有历史翻译按钮与状态函数，POST /api/translate由E22实现。审核正文翻译走01有归属/版本检查的wrapper，结果样式复用而非直传任意用户HTML。
- `app.js:21501`已挂载refresh不会重放filters，因此待审toolbar不能只改宿主state等待mount自动生效；需明确传options.reviewPendingOnly或增加专用handle方法，并在applyOptions处理。
- X15：顶栏按键→组件filter→02服务端count/page→专家卡；X16：admin原draftMap→review只读模式→还原，含在途保存/附件/模板回调；X17：审批→02数字/列表刷新→03手动发送；X18：新消息/确认/再次消息→发送409→重弹；X19：换用户/换专家→隔离翻译/评论/详情响应。

## 实现方案
### T1：toolbar与专家列表（I-19，S-3）
文件：`index.html`、`app.js`、`mailbox-chat.js`。
原panel-head-actions前插唯一按钮；MailboxChat通过窄回调 `onReviewPendingSummary({count,loading,error,active})`更新宿主，宿主点击用已捕获handle的setReviewPendingOnly切换；不把全文状态共享到app。unmount移除绑定并隐藏按钮，reader壳本就隐藏toolbar仍须回归。
conversationsParams加reviewPendingOnly；进入时chip=all，退出恢复all，点击其它chip则退出。搜索/账号/弹出筛选值沿现有逻辑，页归0。左侧继续renderExpertList，添加每专家badge。不得走renderDraftList。审批最后一条导致当前专家退出过滤列表时，右侧保留本次已审核草稿供手动发送，并显示“该专家暂无待审核草稿”；不能自动切下一专家导致发送对象变更。

### T2：共用编辑器与草稿选择（I-20/I-21，S-4）
文件：`mailbox-chat.js`。
点击待审专家先加载真实timeline，读取该expert已提交过草稿，默认选最新SUBMITTED；普通专家默认manual，选择器可切用户草稿。选择列表分页加载，不只取前50条后丢其余；deleted仅在草稿历史里显示。
切入review前await既有draftNavigation/flush，捕获原owner/target与attachments；不得把完整过期快照覆盖最新原草稿。review模式render原composer的同一editor，赋值前sanitize；manualInputs/saveDraftFromInputs/sendManualReply、input事件、会议/材料/模板/签名/附件/跟进的异步应用回调统一检查mode仍manual且owner/epoch一致。迟到原save仍可更新原记录，但不能重绘review编辑器或启用原发送按钮。
切回manual从当前正确owner/target的原持久化记录重绘，保留原附件/会议。review附件恒不显示、不传，不触发旧autosave/丢弃。API失败留只读错误，不能回退原发送按钮。

### T3：审核/翻译/评论/历史（I-21/I-22，S-4）
文件：`mailbox-chat.js`。
approve发送expectedVersion及打开时服务端context token，409先刷新真实对话提示重新审阅；成功只更新Detail/计数，status“已审核 · 尚未发送”。退回inline必填原因；comment、events与translate用01接口，不碰原邮件标签/处理状态。
所有请求捕获draftId/version/user/owner/contact/epoch；换草稿后旧翻译不能填当前正文。翻译只在mc-translation展开，缓存仅instance+draftVersion；不写DB/rawdraft。正文历史带actor/time/action，按事件游标加载；版本内容只读，安全清洗。

### T4：更新确认与显式发送（I-21/I-22，S-5）
文件：`mailbox-chat.js`。
选择APPROVED且尚未成功发送的草稿时GET context；changed且无本次当前上下文有效event时showModal。已经SENT的草稿只显示真实已发送结果，不对自己发送产生的更新再要求待发确认。送出前再GET context；变化则弹框、return；后端send返回REVIEW_CONTEXT_CHANGED也丢弃旧event重弹。确认POST /confirm-context成功后关闭并提示“已确认最新对话，请再次手动点击发送”；没有任何promise.then(send)链。取消或Escape刷新最新timeline并清event，不改变approvedToken。
发送按钮只POST /review-drafts/{id}/send，带version/eventId与既有安全确认字段。需复用现有安全提示的交互协议，回调重试仍为此review/id/version，不能fallback到普通manual-rich endpoint。SENT重读真实detail关闭按钮；UNKNOWN提示待确认禁重发；失败允许原状态许可的重试；不自动换requestId。

### T5：样式与验证（I-19–I-22，S-3/S-4/S-5）
文件：`styles.css`、`index.html`、`mailReviewAdmin.test.js`、`mailboxServerDrafts.test.js`。
仅新增S-5 CSS；04的共享块不改。统一所有版本资源为 `20261029-review-admin`；执行前按04当时键反查测试字面量。测试覆盖真实mode事件、旧回调、fetch序列，禁止仅搜“审核通过”字符串算通过。浏览器1440/390两宽实测native dialog、单editor、工具栏布局、取消/确认/重新确认。

## 变更文件清单
| # | 文件（相对仓库根目录） | 操作 / 边界 |
|---|---|---|
| 1 | `src/main/resources/static/mailbox-chat.js` | 待审筛选、现有编辑器模式与审批/确认/发送UI |
| 2 | `src/main/resources/static/app.js` | toolbar与MailboxChat窄接口连接，维持reader隐藏 |
| 3 | `src/main/resources/static/index.html` | 唯一toolbar按钮和统一资源键 |
| 4 | `src/main/resources/static/styles.css` | 仅追加S-5确认框CSS，复用04共享样式 |
| 5 | `src/test/js/mailReviewAdmin.test.js` | 新增计数/模式/确认/翻译/生命周期行为和样式测试 |
| 6 | `src/test/js/mailboxServerDrafts.test.js` | 原草稿与审核模式切换回归 |

## 验收标准
- I-19：3条/2人fixture；top3、卡2/1、分页expert total2；点击按钮不出现草稿弹框；退出恢复原chip；错误不显0。
- I-20：原admin正文A+附件1→review正文B→manual仍A+附件1；review发请求无附件/draftRef；旧保存/附件完成不覆盖B；DOM只有1个mc-editor。
- I-21：未审核DOM内无可发送按钮/快捷键入口；approve后的网络请求不含send；APPROVED仅显式send调用专用路由；UNKNOWN/SENT矩阵；普通manual未审仍可正常发送。
- I-22：取消后再次发送重弹；确认后SMTP/API send调用0；再点击1次；再来信旧event失效；翻译/评论迟到不串；translate结果不改subject/html。
- S-3：按钮在原toolbar，badge用mc-badge pending；S-4：一editor，正文footer为mc-text-button/11px，译文12px/1.85；S-5：CSS逐字、白色dialog背景、键盘焦点/ESC。

```sh
node --check src/main/resources/static/mailbox-chat.js
node --check src/main/resources/static/app.js
node --test src/test/js/mailReviewAdmin.test.js src/test/js/mailboxServerDrafts.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js
```
完成后全量 `node --test src/test/js/*.test.js` 与JDK11 `mvn test`；mysqlIt/migrationIt仍需按01–03明确开启，默认mvn不算数据库门禁。

## 人工验收清单
### A-1: 顶部待审筛专家
- 前置条件: 甲2待审/乙1待审/丙0；admin打开收发邮件。
- 操作步骤:
  1. 点击顶部待审核3。
  2. 点甲再点乙；观察右侧姓名与实际往来邮件。
  3. 点原“草稿”chip。
- 预期结果: 待审列表仅甲/乙，数量2/1；右侧切对应专家，不开草稿弹框；原草稿chip仍显示admin草稿。
- 覆盖: 需求1、保持项；I-19；S-3；X15。
### A-2: 原富文本与附件保留
- 前置条件: admin在甲原草稿输入“管理员原稿”，加1个测试附件并保存；甲有user草稿“用户审核稿”。
- 操作步骤:
  1. 选用户待审草稿，检查编辑器、附件及发送按钮。
  2. 切回我的人工回复；再切用户草稿批准。
  3. 批准后不点发送，查看发件记录。
- 预期结果: 编辑框始终只有1个；用户待审只读、无附件/发送；切回原稿文字及附件1都在；批准后出现发送已审核回复；发件记录不增加。
- 覆盖: 需求2、保持项；I-20/I-21；S-4；X16/X17。
### A-3: 更新确认必须再点击
- 前置条件: 甲已审核未发送；新来信已进入时间线。
- 操作步骤:
  1. 重新选已审草稿，在确认框点返回查看最新对话；再点发送。
  2. 点确认回复仍适用，检查发件记录。
  3. 再次点发送；对另一已审草稿重复并在确认后再收新来信。
- 预期结果: 取消后再次弹框；确认后发件0；再次点发送才增加1；第二份遇后续更新再次要求确认；没有v2/v3模拟版本字样。
- 覆盖: 需求3；I-21/I-22；S-5；X18。
### A-4: 翻译、评论、历史与跨专家竞态
- 前置条件: 甲/乙均有user草稿；甲草稿至少修改过一次。
- 操作步骤:
  1. 甲点正文下翻译；评论“已看”；点变动历史。
  2. 模拟慢网发起甲翻译后迅速切乙。
  3. 手机390宽检查toolbar、编辑器和确认框。
- 预期结果: 翻译按钮为小文字，译文在正文下方，原文不变；历史含各版本及评论；甲迟到译文不出现于乙；白色确认框、按钮可触达、ESC等同取消。
- 覆盖: 需求3；I-22；S-3/S-4/S-5；X19。
### A-5: 全链路与旧功能
- 前置条件: 两浏览器分别admin/user1；测试账号SMTP可观察；原人工回复功能可用。
- 操作步骤:
  1. user1新建提交→admin筛选查看并退回→user1修改重提→admin批准→手动发送。
  2. admin切回原草稿进行一次普通回复；user1刷新。
  3. admin审批当前专家最后一条待审后观察右侧。
- 预期结果: 审核链只在最后显式发送新增1封；普通回复无需审核；user1只有邮箱tab、姓名、无发送/附件；最后一条审批后左侧数量减、右侧保留已审正文可发送，不跳到其他专家。
- 覆盖: 需求1/2/3、保持项；I-19/I-20/I-21/I-22；X12/X15/X16/X17。

人工验收开始时才导出同前缀 `-acceptance.md`。

## Authoritative Dispatch Boundary

Child05 product base `a02477be34b3308899820fcc9bd3060ca5bccb70`; predecessor child04 evidence `368984cbe5550f9353887ce91f02e40a5d961e32`; complete five-child manifest through04 validated both artifact-dirty and clean at checkpoint `f27775f40bfb040827f4b2cd18218bb234581a28`. Child04 closed F-01/F-02 in one repair; new reader flush handles must remain compatible.

Governing master `commit:c17c9013ece5bf7967d6233cd97f5bfed47d1cab`, amendments A1–A8. Child05 unchanged exact approved sha256 above; original six-file whitelist applies until controller records a necessary bounded amendment. User continuous authority: “授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p”; message controller exact evidence/file boundary BEFORE unlisted edit. Do not pause for repeat user approval, weaken assertions, fix unrelated baseline code, or alter applied migrations.

Every planned command fresh after final implementation, including full node suite, JDK11 mvn test and explicit non-skipped01–03 MySQL/Flyway gates; record actual failures rather than suppress/skip. Use default Asia/Shanghai datasource timezone. Docker OrbStack DOCKER_HOST and api.version1.41 as above. Actual1440/390 browser UI must use real authenticated backend and test SMTP/IMAP with disposable data, two authenticated contexts for complete cross-user lifecycle and ordinary manual path; preserve original draft/attachments, single editor, dialog confirm-no-send then explicit send and stale-response isolation. No fake frontend/localStorage/SMTP call counter as substitute for actual production paths. Machine smoke is not human acceptance.


## Epoch 2 — Cross-step Reader Contract Compatibility

Current governing child05/master identity `commit:0c6111bb749050352bd675fabdd7dcbe415e6145`, ledger A9/A10, supersedes original seed identity. Seven authorized files: original six plus mailReviewReader.test.js only within amended T5. Retire obsolete exact resource revision, file-tail CSS placement, fixed title source-wording assertions; do not re-pin replacements. Preserve uniform resource versions, exact S-1 CSS block coexistence with appended S-5, real DOM IDs, all behavior/safety/permission/browser/full-suite requirements. Existing pre-implementation discovery is retained; reconcile execute-p identity as epoch2, fix_round0. Release only after controller message.


## Epoch 3 — Proven Full-node Compatibility

Current child05/master identity `commit:babdb2df52a3fe6f7e93d42bf2d1b97dd80d2fa5`, ledgerA11/A12 supersedes prior epoch2 identity. Scope10 files: prior7 plus batchEntryRelocation.test.js (only first-child coupling; preserve original action relative order/container), discoveryRecheck.test.js (only EOF coupling; retain exact old module CSS/DOM), mailboxMailReadAccess.test.js (retire old fixed key suffix; retain uniform assets/adjacency/permissions). No old product behavior/style changes and no re-pinning. Initial fullnode receipt1951/1947/4fail/0skip retained. New mailReviewAdmin test literal is original authorized file, remove literal without changing taskActivityCenter guard. Still initial implementation/fix_round0, all commands fresh after final state and actual browser unchanged.


## Epoch 4 — Mandatory Backend Test Compatibility and JDK11 tzdb

Current child05/master identity `commit:08aa433770bdb59143b9f446834feefa1e75e814`, ledgerA13/A14; scope12-file bounded exception,2 subsystems, supersedes prior epoch3. Only added AuthInterceptorTest.kt exact handler/verb matrix compatibility with already-approved01 reader routes (all other denial checks preserved), and four stub matcher arity corrections in UnmatchedInboundTrustWorkbenchTest.kt for03 current seam (422/503/409/dedup behavior preserved). No production backend changes.

Original fullmvn5715/1fail/24errors/24defaultopt-in-skips retained. Twenty errors attributable to genuine installed oldZulu11 tzdb absence America/Coyhaique: use temporary currentZulu11 patch runtime, official Azul archive, no global overwrite, no source/fixture workaround. Record exact JAVA_HOME/java-version/archivechecksum/real ZoneId smoke; leave temporaryJDK accessible for fresh verifier/reviewer, reportpath. Fresh all mandatoryfullmvn + explicitIT gates after final state, no skips/weakening. Still initial implementation/fix_round0.


## Epoch 5 — Inherited I-5 Malformed Placeholder Admission

Current child05/master identity `commit:59eb60cf00534a342c1019827a62f2b7b1db0f2b`, ledgerA15/A16 supersedes epoch4;13-file/3-subsystem exact narrow exception. Only new path MailReviewDraftService.kt, existing normalization validator call must reuse existing strict requireValidTemplatePlaceholders to reject broken tokens; unchanged subsequent complete-variable ban, HTML sanitation and final-length check. No global MailPlaceholderService edits, no test changes to failing fixture, no migration/backend authority/send behavior changes. This explicitly amends earlier no-backend-product condition only for this single inheritedI-5 correction; not new scope.

First fresh currentZulu11 enabled MailReviewDraftIT12/1fail/0error/0skip receipt1224/raw1238 retained. Reconcile approved identity/epoch5/fix_round0; run all final required commands fresh, actual changed safety-path smoke. Temporary genuineZulu11 home `/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home`, archiveSHA256 `4947b01fcb220ee69710dbf63375f4628688d84750fccd289f5a6d62b494aa94`; preserve for verifier/reviewer. Follow required code-intelligence references before exported symbol changes when available.


## Epoch 6 — F-01 Retarget Closure and O-01 Mandatory Gate Arbitration

Current child05/master identity `commit:32407a46d5b05971fc913cda1802b6ba002e8dc7`, ledgerA17/A18 supersedesEpoch5. Previous candidateae6898ffe92e6e31b45819e5edbde8ac77e0ebff and exactLIGHT_FAIL/PAUSE preserved in7a3f075b5a16355ad5df3d0a20c2a16b31dbefba. Same productbasea02477be34b3308899820fcc9bd3060ca5bccb70; new executionepoch6/fix_round0 under standinghumanauthority, not alteration of historical verdict.

Fix exactF-01 I-20 delayedretarget in existingmailbox-chat.js and approvedbehavioraltest; verify oldGET/dialog cannot change capturedmanualtarget/owner afterreview/sourcechange, no lateconfirmation/newdraft/copiedattachment, restoreoriginaltarget/body/attachment. Prior diagnostic local://draft05-retarget-probe.cjs andartifact1412 document failure.

Scope14files/3subsystems, adding onlyDiscoveryVerificationWorkerTest.kt firstcase63–65 removal of incidental JVMstate busy-wait/assert; paperEntered synchronization and all substantive interruption/lease/evidence/budget/reclaim/owner/cache assertions unchanged. Do not change Discovery production, sleeps/timeouts/othercases or suppressfailedtest. This adjudicates historicalO-01 mandatoryfullmvn receipt1407 without treating historicalsuccess as freshproof. No blind error-confirmation rerun.

Read full amendedplan first, resumeexisting implementation with narrowcorrections, run everyoriginalrequiredcommand freshly finalstate inclfullmvn andexplicitnon-skippedITs. Actualchangedretarget browser/backend smoke required; prior fullUI/SMTP proof remainshistorical, not humanacceptance. Commit authorizedproductonly as `feat(fast-p): implement 05 epoch 6`, appendcompleteepoch6executionreport; controller laterfreshverifier. ExistingJDK11/runtimeenv unchanged.

