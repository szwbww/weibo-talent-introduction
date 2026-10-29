# Child 04 — Approved Execution Brief

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master
- Branch: fast/draft-review-master
- Exact plan: docs/plans/2026-10-29/draft-review-04-reader.md
- Plan identity: sha256:6f7bca6a57737934f983bd69ecc4834b0267fb109fc0b49f7e89c6b9cf3feae8
- Approval: User invocation `/fast-p docs/plans/2026-10-29/draft-review-master.md` on 2026-10-29 authorizes execution of these exact plan bytes despite historical DRAFT headings. Do not amend plan bytes.
- Dependencies: 01,02,03; dispatch only after predecessor checkpoint.
- Product base: controller supplies the exact child_base_sha at dispatch; evidence HEAD may be later.
- Execution report: docs/plans/fast/draft-review-master/children/04/execution.md

## Global Constraints
Read the exact child plan completely from disk and use execute-p. Its authorized file table is exhaustive; no changes outside that table. Read docs/design.md before mail/conversation changes and applicable repository guidance. Master invariants I-23/I-24 apply: sequential incremental work; existing shared tables gain zero columns; preserve existing mail, admin drafts, restricted reader access, and unrelated user changes. No deployment, push, merge, history rewriting, or extra worktrees. No plan amendments without human approval. Evidence docs are separately authorized only in the designated fast-p report files; exclude them from product commits.

One writer at a time. Do not run build/lint/tests/formatters mid-flight; collect all required fresh commands and smoke proof once implementation is complete. Do not fix unlisted files or silently narrow requirements. Report PLAN_CONFLICT/BLOCKED with exact evidence if necessary. Full verification is deferred; do not invoke verify-p, review-p, repair-p or fix-v inside this loop. The controller will dispatch an isolated four-gate verifier.

## Runtime and Commands
JDK11: /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home. Docker daemon is available via OrbStack. For MySQL/Flyway ITs set DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock and pass -DargLine=-Dapi.version=1.41 when needed. Run every command from the child plan freshly, all mandatory ITs enabled and non-skipped. Product smoke must exercise the changed API/UI, not only tests; use throwaway scenarios and clean those afterward. Browser evidence for 04/05 must use actual UI; do not substitute source inspection or fake localStorage. Test SMTP only; never production recipients.

## Authorized Requirements and Downstream Interface
The exact plan below defines all tasks, invariants, authorized files, public DTOs/methods, tests, styles, and acceptance. Its quoted historical source line numbers are pointers, not permission to replace newer predecessor code. Preserve exact planned DTO and API names for downstream children. Necessary predecessor execution evidence is available in earlier child artifact directories; read only the dependency outputs needed by this child, not later plans. Required source evidence is in docs/plans/2026-10-29/draft-review-evidence/.

# 04 · user1富文本草稿与历史

状态：DRAFT / 待用户批准实施。仅开发计划，不代表代码已实现或测试已通过。
基线：`main@b15bb069f43f89c45ee6859dba04a0baff5d774d`，2026-10-29 工作树；原有脏文件见 [状态快照](draft-review-evidence/worktree-status.txt)，不得覆盖。
证据：[逐字源码 E01–E31](draft-review-evidence/source-excerpts.md)、[数据库迁移链](draft-review-evidence/schema-chain.md)、[读写调用全集](draft-review-evidence/06-repository-call-sites.txt)、[SQL/存储引用全集](draft-review-evidence/05-all-store-read-write.txt)、[来源 SHA256](draft-review-evidence/sha256.json)。文中“新增/拟”都是设计，不声称已存在。

前置：01–03机器门禁通过。基于03结果执行；此时发送权限仍仅admin。

## 需求描述
1. user1在授权专家邮件下编辑多条富文本草稿，新增/保存/选择历史、提交/取消提交/退回重提/审核前删除。
2. 双方评论与完整内容版本可读；user1已审核后不再修改或评论。
必须保持：只有收发件箱tab、专家只显示姓名、不能发送/看或下载附件；已有邮件分页与失权清理。
不做：图片/附件上传、字体字号工具、自动提交、admin界面改版、浏览器存储正文。
本步7文件、1子系统（reader前端）；服务端依赖01；可独立部署使用已有API，admin仍可经API审核。

## 关键不变量
继承01 I-1–I-7（客户端不能放宽），本步额外约束：
### Invariant I-16: user1仍是独立受限邮箱
- Rule: 保留MailboxReadOnly，不挂载admin MailboxChat、不读sender-accounts/附件/管理员草稿/审核发送/翻译API。仅新增01允许的精确草稿method+路径；其他接口在api fetch前仍拒绝，服务端另有权限防线。专家卡/头/草稿历史只显示姓名，不把email藏在title/data/搜索提示里。
- Applies to: app.js请求guard、模块mount/render/actions。
- Violation consequence: user1因加编辑器获得admin能力。
- 来源: K-mailbox-readonly-needs-lifecycle-audit；E13/E14

### Invariant I-17: 一个活跃草稿、可验证保存
- Rule: 每个专家可多份，编辑器只装当前draftId/version。显式保存；提交先等待dirty内容保存成功，使用最新version提交；失败/409保留本地输入并提示，不自动覆盖服务器。切专家/切草稿/新增/页面刷新按钮/退出登录前先保存当前dirty草稿，失败中止动作。browser beforeunload仅dirty或保存中提示，已保存不提示。
- Applies to: save/submit/selectExpert/selectDraft/create/refresh/logout。
- Violation consequence: 切换丢字、提交旧正文或新增覆盖已有草稿。
- 来源: original

### Invariant I-18: 失权与异步隔离
- Rule: 每个请求捕获sessionUser/generation/contactId/draftId/version/requestSeq，只回填相同上下文；401/403立即清除时间线、编辑器、评论历史、待保存缓存与确认状态，停止重试；unmount abort请求/移除监听/清空内存，不写localStorage。网络失败保留当前未保存文本但显示未保存，不能用旧服务器Detail冒充当前授权。
- Applies to: 全部请求/页面恢复/切换/卸载。
- Violation consequence: 跨专家串正文或撤权后残留内容。
- 来源: K-mailbox-draft-cache-owner-capture；K-mailbox-readonly-needs-lifecycle-audit

## 样式契约
### S-1：用户草稿编辑区与历史
复用 E14 的 .mail-chat/.mc-conversation/.mc-scroll；在时间线之后追加下列结构，不替换历史消息DOM。仅新区域允许安全清洗后的 HTML，历史邮件仍textContent。
```html
<section class="mc-section" data-role="review-drafts">
  <div class="mc-section-content">
    <div class="mc-compose" data-role="review-compose">
      <div class="mail-review-controls">
        <button class="button" type="button" data-review-action="history" aria-expanded="false">草稿历史</button>
        <button class="button" type="button" data-review-action="new">新增草稿</button>
        <span class="mc-badge" data-role="review-state">未提交</span>
      </div>
      <div class="mail-review-panel" data-role="review-history" hidden>
        <article class="mail-review-entry"><span>草稿 #编号 · 状态 · 时间</span><button class="mc-text-button" type="button" data-review-action="select">打开草稿</button><button class="mc-text-button" type="button" data-review-action="events">变动历史</button></article>
        <button class="mc-text-button" type="button" data-review-action="more-drafts">加载更多草稿</button>
      </div>
      <label>主题<input aria-label="草稿主题" data-role="review-subject"></label>
      <div class="mc-editor-tools">
        <button class="button" type="button" data-review-command="bold">B</button>
        <button class="button" type="button" data-review-command="italic">I</button>
        <button class="button" type="button" data-review-command="insertUnorderedList">列表</button>
        <button class="button" type="button" data-review-command="createLink">链接</button>
      </div>
      <div class="mc-editor" contenteditable="true" role="textbox" aria-multiline="true" aria-label="草稿正文" data-role="review-editor"></div>
      <p class="mc-note" data-role="review-return-reason" hidden></p>
      <p class="mc-error" data-role="review-error" role="alert" hidden></p>
      <div class="mail-review-footer">
        <button class="mc-text-button" type="button" data-review-action="comments">评论 0</button>
        <button class="mc-text-button" type="button" data-review-action="events">变动历史</button>
      </div>
      <div class="mail-review-panel" data-role="review-discussion" hidden>
        <article class="mail-review-entry"><strong>操作者 · 时间 · 动作</strong><div class="mc-body">评论或事件文字</div></article>
        <div class="mc-body" data-role="review-version-body"></div>
        <textarea class="mail-review-input" aria-label="评论内容" maxlength="2000"></textarea>
        <button class="button" type="button" data-review-action="comment">发布评论</button>
        <button class="mc-text-button" type="button" data-review-action="more-events">加载更早历史</button>
      </div>
      <div class="mc-compose-footer">
        <span class="mc-note" data-role="review-save-state" role="status">尚未保存</span>
        <div class="mail-review-actions">
          <button class="button danger" type="button" data-review-action="delete">删除草稿</button>
          <button class="button" type="button" data-review-action="save">保存草稿</button>
          <button class="button" type="button" data-review-action="withdraw">取消提交</button>
          <button class="button primary" type="button" data-review-action="submit">提交审核</button>
        </div>
      </div>
    </div>
  </div>
</section>
```
动态规则：无草稿仅显示历史/新增及“暂无草稿”；DRAFT/RETURNED编辑器editable，显示保存/提交/删除；SUBMITTED只读且只有取消提交/删除，保留评论；APPROVED只读且没有上述写按钮/评论输入。已删除仅历史显示“已删除”，可读事件，不显示打开编辑动作。历史与评论默认折叠且互斥。变动历史详情使用mc-body，不创建第二个编辑器。
新增CSS **逐字**追加styles.css（S-1唯一新增类）：
```css
.mail-chat .mail-review-controls{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:10px}
.mail-chat .mail-review-select{min-width:180px;max-width:100%;height:32px;padding:0 10px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font:inherit;font-size:12px}
.mail-chat .mail-review-select:focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
.mail-chat .mail-review-select:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .mail-review-footer{display:flex;align-items:center;flex-wrap:wrap;gap:12px;margin-top:12px;padding-top:10px;border-top:1px solid #e7edf5}
.mail-chat .mail-review-panel{display:flex;flex-direction:column;gap:10px;padding:12px;border:1px solid #dce4ef;border-radius:7px;background:#fff}
.mail-chat .mail-review-entry{display:flex;flex-direction:column;gap:6px;padding:10px 0;border-bottom:1px solid #e7edf5;color:#475569;font-size:12px;line-height:1.6;overflow-wrap:anywhere}
.mail-chat .mail-review-input{width:100%;min-height:80px;resize:vertical;padding:10px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font:inherit;font-size:12px;line-height:1.8}
.mail-chat .mail-review-input:focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
.mail-chat .mail-review-input:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .mail-review-actions{display:flex;align-items:center;justify-content:flex-end;flex-wrap:wrap;gap:8px}
.mail-chat .mail-review-panel[hidden],.mail-chat .mail-review-footer[hidden],.mail-chat .mail-review-actions[hidden]{display:none}
@media(max-width:760px){.mail-chat .mail-review-controls{align-items:stretch}.mail-chat .mail-review-select{width:100%;min-width:0}.mail-chat .mail-review-input{font-size:16px}.mail-chat .mail-review-actions .button{min-height:36px;height:auto}}

```
禁止inline style、新编辑器依赖、未声明class、修改既有全局button/mc规则、附件入口。按钮hover/active/disabled/focus复用上述既有规则；新增select/textarea各状态以本段为准。

### S-2：用户壳与反馈
沿 E13/E18 的隐藏范围，只更新既有面板标题为“收发邮件”、身份badge为“可查看 · 可提交草稿”；桌面tab及手机menu仍只有收发件箱。姓名原DOM不变。所有错误和空态落入S-1的mc-note/mc-error，文案明确“尚未保存 / 已保存 / 保存失败，内容尚未同步 / 草稿已被更新，请重新加载 / 已审核，不能修改”。不造toast容器。无新增CSS。

## 现状审计
### 前端样式盘点 / 改动前逐字基线
- HTML原文：E14=`mailbox-readonly.js:137–222`，E15=`mailbox-chat.js:5800–5870`，E16=`index.html:788–815`；CSS完整原文E17/E18/E26，并附 `styles.css:802–872/979–984` 到证据。不是重画原型壳，也不导入原型inline CSS。
- 样式覆盖链：`mailbox-chat.css:60–65` 是composer/editor原定义，`:130`把editor高度覆盖为min100/max240；`:145–146`将按钮覆为32px高、11px横padding、12px字、7px角；移动断点760px来自同文件及 `styles.css:12586–12588` 的44px按钮覆盖。
- 复用：`.mc-text-button` :109–112，正文 `.mc-body` :115，译文 `.mc-translation` :119，badge :29–32/:105–106，列表active :16–23，`.mc-note/.mc-error` :58–59，`.mc-compose` :60–65，`.button` styles.css:802–872，toolbar actions :979/12868。不得修改这些规则及其它使用点；新增均派生mail-review-*类。
- token实值：primary #1e40af，hover #1e3a8a，active #172554，bright #3b82f6；text #1e293b，muted #94a3b8；border rgba(15,23,42,.11)，surface rgba(15,23,42,.022)，radius-sm 7px；panel-bg rgba(255,255,255,.55)透明，不用作确认框背景。翻译按钮11px/1.5，3px 0 padding；译文12px/1.85、12px 14px padding、背景#f3f7ff。
- 三键及全部既有版本资源当前均为20261029-user1-mail-access；`rg -n -F '20261029-user1-mail-access' src/test`无命中（exit1）；执行前重查。没有新增script/link，无需调整资源数量断言。来源 K-frontend-cache-key-triad、K-js-test-invocation-surface。
- `mailboxChatStyle.test.js:166`检查class归属，不是全文件CSS字节锁；新增mail-review-*放styles.css，原mc-*只复用。来源 K-mailbox-chat-css-byte-contract。

### 前端状态 / 请求读写
- E13：`app.js.isMailReadOnlyRequestAllowed`目前非GET草稿写一律拒绝；只改模块不改这里会在fetch前失败。注销 `app.js:21388–21410`当前reader跳过flush，必须集成reader自己的flush而非调用admin flush。
- E14：reader的requestJson目前写死GET；renderConversation每次重建timeline，不能把新增editor放在会被邮件轮询重建的子树内。在该section持久挂载composer，与timeline分开更新。
- `mailbox-readonly.js:352/376/452/533/538/556`分别列表、详情、选择、focus、visibility、destroy。所有这些入口都是新的dirty编辑状态交互点。已有详情失败先清时间线；草稿的403也要清全文。
- `mailboxReadOnly.test.js:655`历史“两GET”断言需定向更新为允许精确草稿请求，不能整体删除权限用例。用户姓名隐藏仍须回归。
- 新前端内存仅当前用户/专家/草稿的数据及dirty内容；所有写者是编辑input、API成功、选择/重置、注销/销毁；读者是S-1渲染、保存/提交/评论。数据库新存储读写详01；前端不直接写DB/旧draftMap。
- X12：用户编辑→保存版本→admin审批→reader刷新锁定；X13：grant/revoke/401/403→清全部新状态；X14：focus/visibility/select/logout→dirty flush及旧回包隔离。

## 实现方案
### T1：精确网络与生命周期（I-16/I-17/I-18，S-2）
文件：`app.js`、`mailbox-readonly.js`。
requestJson支持method/body但调用点枚举固定；app白名单逐项对齐01 user1列，ID路径限定正整数；严禁`startsWith('/review-drafts')`。reader handle新增flushDrafts/hasPendingDrafts供刷新及logout调用；401/403立即清理，不能为了保存重试延长失权访问。
选择/新增/关闭页面前的未保存内容策略沿I-17。focus/visibility只刷新消息/状态，不无条件重建dirty editor；若发现server version变更则锁提交并提示冲突，由用户先复制本地文字再“重新加载”，不自动覆盖。

### T2：草稿与历史UI（I-16/I-17/I-18，S-1/S-2）
文件：`mailbox-readonly.js`、`styles.css`。
新增按钮明确POST创建，按新id写；create重试保留同clientRequestId。草稿历史调用list(includeDeleted=true)按更新时间分页，默认关闭；打开草稿再读detail及状态，不靠上次列表权限。保存结果使用服务器canonical html/text/version更新已保存基线，用户继续输入期间晚到save只推进已提交revision，不能抹掉新输入。
仅调用已有 `MailboxMeeting.sanitizeDraftHtml` 进行渲染第二道清洗；模块缺失时editor转纯文本只读并提示“编辑组件加载失败”，不直接innerHTML未清洗正文。createLink通过inline输入或浏览器现有提示收集URL，只允许http/https/mailto；无富文本工具外部库。评论/原因/姓名全textContent，版本html清洗后展示。事件分页以id游标，切草稿清空历史和翻译状态。
所有草稿动作从服务端回包改变状态；APPROVED禁止user1全部写入口；已删除仍在历史列表可查事件但不能编辑。提交/取消/删除/评论busy期间禁用重复按钮，冲突保留输入；禁止乐观显示“已提交/已审核”。

### T3：资源与验证（I-16–I-18，S-1/S-2）
文件：`index.html`、`mailboxReadOnly.test.js`、`authFlow.test.js`、`mailReviewReader.test.js`。
当前缓存键精确反查0固定测试，所有已版本化资源统一改为 `20261029-review-reader`；不得顺手新增资源或替换旧模块。执行前若并行任务改变键/测试固定值，先重审文件白名单，不直接改白名单外文件。
测试真实模块状态和DOM节点/网络请求；不可用永远返回元素的querySelector stub证明编辑器存在。除node测试，还需真实浏览器运行S-1粗体/列表/安全粘贴与光标保持验收；不声称VM用例能覆盖原生contenteditable行为。

## 变更文件清单
| # | 文件（相对仓库根目录） | 操作 / 边界 |
|---|---|---|
| 1 | `src/main/resources/static/mailbox-readonly.js` | 扩展既有user1独立模块，草稿UI/API/生命周期 |
| 2 | `src/main/resources/static/app.js` | 精确请求白名单、user1文案及保存/注销集成 |
| 3 | `src/main/resources/static/styles.css` | 仅追加本计划逐字CSS |
| 4 | `src/main/resources/static/index.html` | 全部已版本化资源统一bump，不增新资源 |
| 5 | `src/test/js/mailboxReadOnly.test.js` | 更新“两GET”旧期望，保留权限/失权/隔离检查 |
| 6 | `src/test/js/authFlow.test.js` | reader新白名单与注销保存回归 |
| 7 | `src/test/js/mailReviewReader.test.js` | 新增草稿交互、HTML安全、并发与样式合同测试 |

## 验收标准
- I-16：新API白名单逐项与01一致，send/approve/translate/attachment仍在fetch前拒绝；只有一个tab；姓名DOM无邮箱属性。
- I-17：保存失败切专家/退出被阻止；提交先保存再用新version；新建与选择不同id；保存回包晚于再次输入不覆盖新内容。
- I-18：延迟旧请求后换专家/退出，旧回包无渲染；401/403彻底清空；所有input/save/event状态无Storage写。
- S-1：CSS块与合同逐字一致；DOM树与data-review-action对齐；没有新class/inline style；四态动作矩阵实际渲染测试，APPROVED无评论输入。
- S-2：桌面/手机仅邮箱，壳文案更新，无管理工具出现。
- 跨路径：01权限/状态测试与reader HTTP调用字段一致，不拿localStorage原型测试代替后端连接。

```sh
node --check src/main/resources/static/mailbox-readonly.js
node --check src/main/resources/static/app.js
node --test src/test/js/mailReviewReader.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js src/test/js/mailboxChatStyle.test.js
```

## 人工验收清单
### A-1: 新建、保存及多草稿
- 前置条件: 测试环境甲已授权user1，有往来信；登录user1。
- 操作步骤:
  1. 在甲邮件下新增草稿，输入主题A、粗体文本和两项列表，保存。
  2. 再新增主题B并保存；点草稿历史切回A。
  3. 刷新页面，再打开A。
- 预期结果: A/B两个不同编号；A粗体/列表保持；编辑框只有一个；历史默认折叠；页面没有发送/附件按钮；专家仅姓名。
- 覆盖: 需求1、保持项；I-16/I-17；S-1/S-2；X12。
### A-2: 提交、退回、评论、审核锁定
- 前置条件: user1已有甲草稿；另一个浏览器admin登录，可用01 API审核。
- 操作步骤:
  1. user1提交并取消提交，再重提；双方各评论一次。
  2. admin退回原因“补充时间”，user1修改并重提，admin批准。
  3. user1刷新并打开历史；再尝试找编辑/删除/撤回/评论入口。
- 预期结果: 状态依次已提交/未提交/已提交/已退回/已提交/已审核；退回原因可见；两条评论及各版内容在历史；已审核正文只读、四种写入口均无。
- 覆盖: 需求1/2；I-17/I-18；S-1；X12。
### A-3: 网络失败、快速切换和撤权
- 前置条件: 甲草稿正文已输入未保存；乙也是已授权专家；浏览器可切离线。
- 操作步骤:
  1. 离线后点击乙/退出登录。
  2. 恢复网络保存，再延迟甲请求并切乙，释放甲响应。
  3. admin撤销乙授权，user1刷新。
- 预期结果: 离线显示保存失败且留在甲；恢复后正常切换；甲迟到正文不进入乙；乙失权后时间线/草稿/评论历史清空。
- 覆盖: 保持项；I-16/I-17/I-18；X13/X14。
### A-4: 删除历史与样式
- 前置条件: 甲有未审核草稿与至少一次修改；桌面1440宽及手机390宽。
- 操作步骤:
  1. 删除草稿后打开草稿历史并查看该条变动历史。
  2. 检查粗体/列表、按钮状态与评论区；缩到390宽。
- 预期结果: 历史显示已删除且仍可看到各版本，不可恢复编辑；桌面编辑器最小100最大240px、边框#dce4ef、圆角7px；正文不横向溢出；手机保留一个邮箱tab且新增按钮可点。
- 覆盖: 需求1/2、保持项；I-16/I-17；S-1/S-2。

人工验收开始时才导出同前缀 `-acceptance.md`。
