# Fast-P Execution — 07-attachment-ui

## Epoch 1 — IMPLEMENTATION

- Executor: isolated implementer `Implementer07`.
- Product base: `82a46dcc32d50cbc352165656842417bc0a569f2` (child 06 terminal code head); child 06 evidence commit precedes this implementation in Git ancestry.
- Plan: `docs/plans/fast/meeting-mail-master/children/07-attachment-ui/brief.md` (byte-identical copy of the approved `docs/plans/2026-09-16/meeting-mail-07-attachment-ui.md`).
- Upstream interfaces inherited: child 04 upload/draft-download endpoints and child 06's conversation timeline `outboundAttachments` DTO plus the sent-message download endpoint; both manual send entries accept `attachmentIds`.
- Required commands: `node --check src/main/resources/static/mailbox-chat.js`; `node --test src/test/js/*.test.js`. Style contract: the S-2 `outbound-*` CSS block appended verbatim to `styles.css`; `mailbox-chat.css` must stay byte-identical.
- Dispatch: fresh implementer acquired through the Required Agent Gate.

### 执行结果：PLAN_CONFLICT

- Plan（已批准）：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master/docs/plans/fast/meeting-mail-master/children/07-attachment-ui/brief.md`
- Plan SHA-256：`18b9e748f60af2789286fcc43da647a8f9f93c413468098d43803f259c39be8f`（15296 bytes；与 `docs/plans/2026-09-16/meeting-mail-07-attachment-ui.md` `shasum -a 256` 双值相同、node 逐字节 `===` 为 true）
- Execution ID：`…/children/07-attachment-ui/brief.md@18b9e748…be8f`；Epoch：`NEW`
- Approval basis：本次调用（controller 派发的 07 批次契约）
- Executor：`Implementer07`
- Target worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master`
- Target branch：`fast/meeting-mail-master`；worktree git dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-meeting-mail-master`
- Worktree ID：`<root>@fast/meeting-mail-master@…/worktrees/weibo-talent-introduction-fast-meeting-mail-master`
- Pre-execution code SHA：`82a46dcc32d50cbc352165656842417bc0a569f2`（child 06 终端码头）；执行前 HEAD：`1b07c5d0e05853ca4163814dfa9c6d45d4e775b9`（child 06 证据提交）
- Post-execution code SHA：`b003e4e`（`feat(fast-p): implement 07-attachment-ui`，恰好 4 个授权文件，可达目标分支）
- Implementation boundary：`1b07c5d..b003e4e`
- Evidence HEAD：N/A（证据提交由控制器单独执行；本 epoch 未提交 `docs/plans/**`）

### 任务状态

| 需求 | 状态 | 文件 | 证据 |
|---|---|---|---|
| 变更清单 1 mailbox-chat.js（图标入口、多附件草稿、发送参数、会话下载卡） | IMPLEMENTED | `src/main/resources/static/mailbox-chat.js` | S-1 按钮/input 逐字插入 链接 与 会议确认 之间；`outboundAttachmentDraft={revision,items}` 单一真值；上传顺序队列 + 捕获 owner 写回；两分支 `attachmentIds`；I-4 已发卡只读 06 快照 |
| 变更清单 2 styles.css（仅追加 outbound-*） | IMPLEMENTED | `src/main/resources/static/styles.css` | S-2 CSS 块由脚本从计划 ```css 围栏整块提取后逐字追加（18 行 + 前导空行），`stylesSource.endsWith(S2_BLOCK)` 为真；`mailbox-chat.css` sha256 `0fd35402…` 与 child_base 两侧一致（未改、未 bump index.html 键） |
| 变更清单 3 mailboxOutboundAttachments.test.js（新增） | IMPLEMENTED | `src/test/js/mailboxOutboundAttachments.test.js` | 21 tests / 21 pass；真实链路 adapter、受控 pending Promise、切 A/B 归属、原文件下载链接 |
| 变更清单 4 meetingConfirmationIntegration.test.js（工具栏 6→7） | IMPLEMENTED | `src/test/js/meetingConfirmationIntegration.test.js` | `tools.length === 7`；`tools[4]=mc-upload-attachment`（textContent.trim()===""）、`tools[5]=mc-open-meeting`、`tools[6]=mc-open-followup`；会议/跟进旧用例全部保留（30/30 pass） |
| I-1 图标唯一入口、任意类型 | IMPLEMENTED | mailbox-chat.js + 新测试 | 按钮 `textContent.trim()===""`、title/aria=上传附件、`outbound-upload`+复用 `.button`、S-1 svg path 逐字；input `multiple` 无 `accept`、hidden；取消选择 0 请求 0 草稿变化 |
| I-2 上传状态与草稿归属 | IMPLEMENTED | mailbox-chat.js + 新测试 | 顺序队列（一次 1 个文件）；切专家/迟到回包只落捕获 owner 的 Map+key；移除中上传不复活；LRU 淘汰后迟到回包无落点；saveDraftFromInputs/writeDraftWithMeeting/ensureOutboundRequestId/removeMeetingFromDraft(Object.assign)/retargetManual(Object.assign)/发送回调全部显式保留字段 |
| I-3 发送使用已上传快照 | IMPLEMENTED | mailbox-chat.js + 新测试 | 仅 `ready` 有序提交；uploading/failed 禁发；失败/取消/UNKNOWN 保留；成功按「捕获 owner + 主题/正文/有序附件 id 全等」才清；用户改附件使 requestId 失效、上传落定与未变化重存不换 |
| I-4 会话原件下载 | IMPLEMENTED | mailbox-chat.js + 新测试 | 已发卡只消费 `message.outboundAttachments`；草稿/已发各用自己的 downloadUrl；href = `instance.options.contextPath` + 相对 URL（`/talent` 前缀断言）；名称 escapeText、a 带 download、无移除按钮、无 Blob 合成 |
| I-5 旧能力与缓存界限 | IMPLEMENTED | mailbox-chat.js + 新测试 | ICS 与通用附件字段/卡片独立（各删各的）；session Map 容量常量未动（≤10/≤500）；无 localStorage 文件/正文；工具栏新顺序；会议/跟进/采用后附件仍在 |

### 命令（本调用内全新执行，提交 `b003e4e` 之后复跑）

| 命令 | 结果 | 证据 |
|---|---|---|
| `node --check src/main/resources/static/mailbox-chat.js` | PASS | exit 0 |
| `node --test src/test/js/*.test.js` | **FAIL** | exit 1；tests 944 / suites 183 / pass 943 / **fail 1**（唯一失败为**未授权文件**，见下） |
| （附加）`node --test src/test/js/mailboxOutboundAttachments.test.js` | PASS | exit 0；21/21 |
| （附加）`node --test src/test/js/meetingConfirmationIntegration.test.js` | PASS | exit 0；30/30 |
| （附加）`git diff --check` | PASS | exit 0（无空白错误） |
| （附加）sha256 双值比对 `mailbox-chat.css` | PASS | `0fd354027e54ae69f0a2ba76451801b85c6b74cd2792e98a3852cbb14bade17d`（工作区 = `82a46dc` blob） |

唯一失败（**授权范围外文件，未修改**）：

```
src/test/js/mailboxChatBehavior.test.js:3531  suite "followup 01：人工选择引用邮件与自然正文（I-1..I-8/S-1/S-3）"
  ✖ I-7/S-3：取消不改状态；填入是全文替换并写入锚点提示
    AssertionError [ERR_ASSERTION]: 提示位于会议附件/底部操作区之前
      actual: 'outbound-files'   expected: 'mc-compose-footer'
      assert.strictEqual(kids[kids.indexOf(note) + 1].getAttribute("class"), "mc-compose-footer", "提示位于会议附件/底部操作区之前");
```

### 阻塞根因与最小修复

- 根因：该断言把「锚点提示的**紧邻**下一个元素必须是 `.mc-compose-footer`」钉死（`composeEl.children` 的相邻性），而本 child 的批准样式契约 S-2 明确要求「DOM草稿（`.outbound-files`）放在现有会议附件卡之后、发送 footer 之前」。该用例的 compose 处于 outbound 模式（沙箱未加载会议组件 → `ui=false`、会议卡为空串），因此提示之后、footer 之前的唯一元素就是新的附件卡容器，相邻性断言必然失败。S-2 的位置规则是批准合同；该用例是 over-constrained 的相邻性钉子（其断言文案本身的语义「提示位于会议附件/底部操作区之前」在新布局下依然成立）。
- 不存在「同时满足两者」的落码：附件卡必须出现在 footer 之前（S-2），且会议卡在提示之后，所以附件卡无法放到提示之前；把容器放进 footer 或条件性跳过容器都会分别违反 S-2 与 I-1/I-3（附件必须在 outbound/无会议组件部署下可用）。
- 最小修复（一行，保留原意、不再钉相邻性）：把
  `assert.strictEqual(kids[kids.indexOf(note) + 1].getAttribute("class"), "mc-compose-footer", "提示位于会议附件/底部操作区之前");`
  改为
  `assert.ok(kids.indexOf(note) < kids.indexOf(composeEl.querySelector(".mc-compose-footer")), "提示位于会议附件/底部操作区之前");`
- 该文件其余断言（含紧跟编辑器的 `kids[indexOf(editor) + 1] === note`）无需改动；全仓仅此 1 处 `mc-compose-footer` 相邻性断言（`grep -rn "mc-compose-footer" src/test/js/*.test.js` → 3 命中：本条 + 本 child 新测试 1 条 + meeting 集成测试 1 处 `const footer` 查询，后两条不涉相邻性）。该文件范围证据：`node --test src/test/js/mailboxChatBehavior.test.js` → exit 1 / tests 80 / pass 79 / fail 1；其中 followup 套件 tests 16 / pass 15 / fail 1 —— 修好这一行后该文件回到 80/80。
- 需要人工决定：把 `src/test/js/mailboxChatBehavior.test.js` 加入 child 07 变更清单（授权一行修正），或由控制器在证据提交阶段授权该行修改。与 A1（child 03 同型冲突）处理方式一致。

### 不变量覆盖（实现要点 + 证据）

- **I-1**：`manualComposeHtml` 在 链接 之后、`meetingTrigger` 之前插入 S-1 逐字按钮 + `<input type="file" data-role="outbound-file-input" multiple hidden>`；点击经 `openOutboundFilePicker` 只触发同一 input 的 `click()`，选择结果经 host `change` 委托进入 `handleOutboundFileSelection`；`input.value=""` 立即释放候选引用（同文件可移除后重选），空 `files` 直接返回（零请求、零草稿变化、不发邮件）。
- **I-2**：`outboundAttachmentDraft={revision,items}` 唯一真值，item = `{key,state,filename,byteLength[,id/downloadUrl/error]}`，`key` 为模块级单调序号（同草稿内不重用）。`uploadOutboundFile` 在异步前经 `captureOutboundOwner` 捕获 `{targetKey,contactId,accountScope,ownerKey,draftsMap}`；`setOutboundAttachmentItems` 只写捕获的 `draftsMap.get(targetKey)`，草稿已被删除/已迁走时返回 false（`applyOutboundItemChange` 对不存在 key 直接返回 false → 已移除项与 LRU 淘汰后的迟到回包都无落点、不复活）；落定项用**新对象**替换（不含 `file`），原件随落定释放。所有重建写点显式保留字段：`saveDraftFromInputs`（setDraft 新对象）、`writeDraftWithMeeting`（新对象）、`ensureOutboundRequestId`（新对象兜底），`removeMeetingFromDraft` 的 else 分支与 `retargetManual` 走 `Object.assign` 天然保留；会议填入/采用/跟进/换目标用例均断言附件仍在。
- **I-3**：`sendManualReply` 提交前校验「任一条目非 ready → 提示并 return」，然后 `outboundAttachmentIds` 按选择顺序取值并**仅在非空时**写入两个分支的 payload（旧接口形态不变）；成功回调按捕获 `draftsMap` + 「主题/正文/有序附件 id 全等」判定清稿（`outboundDraftMatchesSnapshot`），会议分支清稿时同步重建附件卡；失败/返回 false 走原保留路径。`invalidateOutboundRequestId` 只在用户选择/移除/替换附件时置 `requestId=null`，上传落定与未变化重存不调用它；发送期间 `setManualComposeSending(true)` 扩展到附件移除按钮与下载锚点（`aria-disabled` + 点击拦截），其他专家不受影响。
- **I-4**：`outboundSentFilesHtml` 只读 `message.outboundAttachments`（空数组 → 整块不渲染），卡片用各自 `downloadUrl` 经 `contextPathValue()` 前缀、`download` 属性、固定「下载」文案；名称与 meta 全走 `escapeText`（恶意 HTML 文件名的转义与「不得成为元素」均有断言）；不调用 preview/IndexedDB/Blob 合成。
- **I-5**：ICS 卡（03）与通用附件卡字段/卡片/移除动作完全独立；`SESSION_CACHE_LIMIT`/`MESSAGE_CACHE_LIMIT` 未改；未新增任何 localStorage 写入；工具栏顺序 B/I/列表/链接/回形针/会议确认/跟进（会议/跟进的条件显示逻辑未动）。

### 样式契约符合性

- **S-1**：按钮块与 input 为计划逐字文本（含 `viewBox="0 0 24 24"` 与 `m21.44 11.05…` path、`aria-hidden="true" focusable="false"`）；复用 styles.css 既有 `.button` 与原 hover/active，未新增按钮 class 之外的类（`outbound-upload` 由 S-2 声明）；未改 `mailbox-chat.css` 的 `.mc-editor-tools` gap 规则。
- **S-2**：CSS 块以脚本从已批准计划的 ```css 围栏提取后整块追加到 `styles.css` 末尾（无手抄），测试以 `endsWith` 逐字锁定；DOM 骨架（`outbound-files`/`outbound-file`/`outbound-file-icon`/`outbound-file-main`/`outbound-file-name`/`outbound-file-meta`/`outbound-file-actions`/`outbound-file-link`、`data-role="outbound-draft-files"`+`aria-live="polite"`、`data-role="outbound-sent-files"`+`data-state="sent"`）逐字落码；uploading/failed/ready/sent 文案为 S-2 逐字串；无文件时容器 `hidden` 且不渲染空卡；已发卡不渲染移除按钮；无 inline style、无未声明 class（`mailboxChatStyle.test.js` 的字面 class 白名单在 944 例全量运行中通过，未出现 unknown class）。
- 未改 `mailbox-chat.css`（sha256 双值一致）、未改 `meeting-confirmation.css`、未 bump `index.html` 缓存键（属 child 08）。

### 未验证 / 阻塞项

1. **【阻塞，授权范围外】`src/test/js/mailboxChatBehavior.test.js:3531` 钉住被本计划 S-2 位置契约取代的「提示紧邻 footer」相邻性**（详见上文根因/最小修复）。因该文件不在 4 个授权文件内，按要求 STOP，未修改。
2. **【未验证，需人工验收】真实浏览器下载字节一致性（A-4「每份 SHA 等于原文件」）与 `/talent` 部署下的真实下载请求路径**：本 epoch 只在 node 沙箱断言 href = contextPath + 相对 URL、`download` 属性与无 Blob 合成；真实 HTTP 响应体与浏览器下载未跑（node 测试无网络、批量约束禁止起服务）。服务端侧原件/快照一致性由 child 04/06 的 Kotlin 测试覆盖。
3. **【未验证，需人工验收】760px 窄屏布局与焦点蓝框等 S-2 视觉实值**：未做真实浏览器像素测量（child 07 批次未要求浏览器冒烟；S-2 值以逐字 CSS 断言锁定）。
4. 未运行 Maven/JUnit 全量（07 仅改前端静态资源与 JS 测试；批量约束禁止项目级验证），未跑 `mvn test` 的 exec-plugin 前端阶段。
5. **【设计层声明，非直接观测】「原始 File 只在上传期间临时持有」**：实现上 uploading item 持有 `file`、落定后整项换成不含 `file` 的新对象；测试可观测的部分是「选择后立即清空 input.value」（断言）与落定项的构造（源码）。进程级内存释放未有直接测量（无反直观测点暴露给测试）。

### 偏差

- 除上文第 1 项（无法在不改未授权文件的前提下满足 `node --test src/test/js/*.test.js` 全绿）外无偏差；未新增/删除文件、未改 `docs/plans/**` 的实现内容、未推送/合并/rebase/amend/改写历史、未 bump 缓存键。

### Freshness

- Plan identity rechecked: YES（执行前后 `18b9e748…` 不变；与 2026-09-16 已批准计划逐字节相同）
- Worktree identity rechecked: YES（`--expect-root/--expect-branch/--expect-git-dir` 在 `git add`/`git commit` 前后一致）
- Reported commit reachable from target branch: YES（`b003e4e` = `fast/meeting-mail-master` HEAD，`git merge-base --is-ancestor` 通过）
- Required commands run this invocation: YES（提交后复跑，见命令表）
- Historical evidence used only as baseline: YES（child 06 的 923/0 基线仅作对照）

### Epoch 1 — PAUSED_FOR_HUMAN (PLAN_CONFLICT)

- Implementer `Implementer07` committed `b003e4e` covering all 4 authorized files, then returned `PLAN_CONFLICT` instead of editing an unauthorized test file.
- Command evidence at `b003e4e`：`node --check mailbox-chat.js` exit 0；`node --test src/test/js/*.test.js` exit 1 with tests 944 / pass 943 / fail 1（唯一失败 = 上面的相邻性断言）；`node --test src/test/js/mailboxOutboundAttachments.test.js` 21/21；`node --test src/test/js/meetingConfirmationIntegration.test.js` 30/30。
- Minimal repair：把 `src/test/js/mailboxChatBehavior.test.js` 加入 child 07 授权文件，并只放宽 `:3531` 一处相邻性断言（`kids.indexOf(note) < kids.indexOf(footer)`），保留其余 51 例覆盖。
- Controller action：pause for a HUMAN-approved plan amendment; no verifier dispatched.
- Resume from：`b003e4e`。

### Epoch 1 — PAUSED_FOR_HUMAN (PLAN_CONFLICT)

- Implementer `Implementer07` committed `b003e4ea7aacb27767f59689392252fe28b3381e` covering all 4 authorized files, then returned `PLAN_CONFLICT` instead of editing an unauthorized file.
- Cause: style contract S-2 mandates the `.outbound-files` draft container between the existing meeting attachment card and the send footer. `src/test/js/mailboxChatBehavior.test.js:3531` pins the stronger sibling adjacency `kids[kids.indexOf(note) + 1].class === "mc-compose-footer"` for the outbound-follow-up case where no meeting card exists, so the only element between the anchor note and the footer is now the new files container. No in-scope layout satisfies both S-2 and that pin.
- Command evidence at `b003e4e`: `node --check mailbox-chat.js` exit 0; `node --test src/test/js/*.test.js` exit 1 with tests 944 / pass 943 / fail 1 (the adjacency pin only); `mailboxOutboundAttachments.test.js` 21/21; `meetingConfirmationIntegration.test.js` 30/30; `git diff --check` exit 0; `mailbox-chat.css` sha256 unchanged (`0fd354027e54ae69f0a2ba76451801b85c6b74cd2792e98a3852cbb14bade17d`, identical to base `82a46dc`).
- Minimal repair: add `src/test/js/mailboxChatBehavior.test.js` to child 07's authorized files and relax that single line to an ordering assertion (`kids.indexOf(note) < kids.indexOf(composeEl.querySelector('.mc-compose-footer'))`); every other assertion in the file, including `kids[indexOf(editor)+1] === note`, stays unchanged and the file reaches 80/80.
- Unlike amendment A1, this file is NOT in the master plan's aggregate 变更文件清单, so widening child 07 is a real scope addition and needs explicit human approval.
- Controller action: paused for a HUMAN-approved plan amendment; no verifier was dispatched for this child.
- Resume from: `b003e4ea7aacb27767f59689392252fe28b3381e`.

## Epoch 2 — RESUMED UNDER AMENDMENT A2

- Approval: HUMAN ask answer 2026-09-17T12:19+08:00 「批准：加该测试文件并放宽那 1 行邻接断言」.
- Amendment A2: child plan 07 变更文件清单 widened from 4 to 5 files by adding `src/test/js/mailboxChatBehavior.test.js` (修改); plan identity `commit:59e909070529b4b1e8ae62e61d03e67855f479ba` -> `commit:4010bc0074b8534afaf31e968cfd37a6d05dd9a5`; brief re-synced byte-identical.
- Preflight: branch/worktree/ledger identities match; no staged index changes; product base for this epoch is `82a46dcc32d50cbc352165656842417bc0a569f2` with epoch-1 implementation `b003e4ea7aacb27767f59689392252fe28b3381e` already committed.
- Scope of this epoch: relax the single adjacency assertion at `src/test/js/mailboxChatBehavior.test.js:3531` to an ordering assertion, then re-run the required commands and commit the epoch-2 implementation.

## Epoch 2 — IMPLEMENTATION

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master/docs/plans/2026-09-16/meeting-mail-07-attachment-ui.md`（与 `children/07-attachment-ui/brief.md` 逐字节相同）
- Plan SHA-256: `4d598349ecb2e97ffd1342c2098484653ed0c9d1f7eaa5cfb51cb88039510f9e`
- Execution ID: 上述路径 `@` 上述 SHA-256
- Execution epoch: RESUME（同路径新内容；A2 提交 `4010bc0` 之前 epoch-1 报告的 `18b9e748…` 属于旧 plan 身份，不构成本身份的执行证据）
- Approval basis: 人工批准 2026-09-17T12:19+08:00「批准：加该测试文件并放宽那 1 行邻接断言」+ A2 修订提交 `4010bc0`
- Executor: `Implementer07b`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master`
- Target branch: `fast/meeting-mail-master`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master@fast/meeting-mail-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-meeting-mail-master`
- Pre-execution code SHA: `4010bc0074b8534afaf31e968cfd37a6d05dd9a5`（本 epoch 起点的 HEAD，其上为 epoch-1 产品提交 `b003e4ea7aacb27767f59689392252fe28b3381e`）
- Post-execution code SHA: `ef836c3e13f57bc14696318ec0f8a5c89ab06874`
- Evidence HEAD: N/A（controller 另行提交 evidence；`execution.md` 与 `ledger.md` 保持未暂存/未提交）
- Implementation boundary: `82a46dcc32d50cbc352165656842417bc0a569f2..ef836c3e13f57bc14696318ec0f8a5c89ab06874`（child 06 末端代码头 → epoch-2 提交）

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| A2 唯一授权改动：`mailboxChatBehavior.test.js:3531` 兄弟邻接断言 → 顺序断言 | IMPLEMENTED | `src/test/js/mailboxChatBehavior.test.js` | 提交 `ef836c3`，+3/-1；该文件由 epoch-1 的 79/80（唯一失败即此断言）变为 80/80 |
| I-1 仅图标按钮 + 逐字 S-1 DOM | IMPLEMENTED（epoch 1，未改动，本 epoch 复验） | `src/main/resources/static/mailbox-chat.js` | grep 计数 1 处 `data-action="mc-upload-attachment" title="上传附件" aria-label="上传附件"`、1 处 `data-role="outbound-file-input" multiple hidden`；`mailboxOutboundAttachments.test.js` 21/21 内含 I-1 用例 |
| I-2 `outboundAttachmentDraft` per-draft，owner/item 捕获，移除项不复活 | IMPLEMENTED（epoch 1，未改动） | `src/main/resources/static/mailbox-chat.js` | 全量 JS 944/944；`mailboxOutboundAttachments.test.js` 21/21 |
| I-3 仅 `ready` 有序提交，uploading/failed 阻断发送，语义变更才失效 requestId | IMPLEMENTED（epoch 1，未改动） | `src/main/resources/static/mailbox-chat.js` | 同上 |
| I-4 已发仅消费 06 `outboundAttachments` + `contextPath` 前缀 + 转义 | IMPLEMENTED（epoch 1，未改动） | `src/main/resources/static/mailbox-chat.js` | `contextPathValue()` 用在 :2076（ICS 已发卡）与 :3448（通用附件卡）；已发容器 `data-role="outbound-sent-files"` 于 :3488 |
| I-5 ICS 与通用附件独立卡片、session Map 上限不变、工具栏顺序 B/I/列表/链接/回形针/会议确认/跟进 | IMPLEMENTED（epoch 1，未改动） | `src/main/resources/static/mailbox-chat.js`、`styles.css` | 工具栏 DOM 见 `manualComposeHtml`（B/I/列表/链接/回形针/input/会议/跟进）；`meetingConfirmationIntegration.test.js` 30/30 |
| S-1/S-2 逐字样式追加、`mailbox-chat.css` 字节不变、`index.html` 缓存键未 bump | IMPLEMENTED（epoch 1，未改动，本 epoch 复验） | `src/main/resources/static/styles.css` | `outbound-upload{width:32px` 命中 1 处；`mailbox-chat.css` sha256 `0fd354027e54ae69f0a2ba76451801b85c6b74cd2792e98a3852cbb14bade17d` 未变；`index.html` 本 epoch 与 epoch 1 均未被修改 |

### Commands（本 epoch 在最终代码状态上全新复跑）

| Command | Result | Evidence |
|---|---|---|
| `node --check src/main/resources/static/mailbox-chat.js` | PASS | exit 0（`CHECK_EXIT=0`） |
| `node --test src/test/js/*.test.js` | PASS | exit 0；tests 944 / suites 183 / pass 944 / fail 0（epoch-1 基线为 944/943/1） |
| `node --test src/test/js/mailboxChatBehavior.test.js` | PASS | exit 0；tests 80 / pass 80 / fail 0（epoch-1 为 79/80） |
| `node --test src/test/js/mailboxOutboundAttachments.test.js` | PASS | exit 0；tests 21 / pass 21 / fail 0 |
| `node --test src/test/js/meetingConfirmationIntegration.test.js` | PASS | exit 0；tests 30 / pass 30 / fail 0 |
| `git diff --check -- src/` | PASS | exit 0（无空白/冲突标记） |
| `shasum -a 256 src/main/resources/static/mailbox-chat.css` | PASS | `0fd354027e54ae69f0a2ba76451801b85c6b74cd2792e98a3852cbb14bade17d` == 基线 |
| `git merge-base --is-ancestor ef836c3 fast/meeting-mail-master` | PASS | exit 0 |

### Changed Files（相对 `4010bc0`）

- `src/test/js/mailboxChatBehavior.test.js` — A2 授权的唯一改动：:3531 邻接断言放宽为顺序断言（+3/-1）。

改动逐字：

```diff
-        assert.strictEqual(kids[kids.indexOf(note) + 1].getAttribute("class"), "mc-compose-footer", "提示位于会议附件/底部操作区之前");
+        const footerEl = composeEl.querySelector(".mc-compose-footer");
+        assert.ok(footerEl, "底部操作区存在");
+        assert.ok(kids.indexOf(note) < kids.indexOf(footerEl), "提示位于会议附件/底部操作区之前");
```

- 同文件其余断言全部保留，包括上一行 `assert.strictEqual(kids[kids.indexOf(editorEl) + 1], note, "提示紧随编辑器")`。
- 未新增/未删除其它任何文件；4 个 epoch-1 产品文件与 1 个 A2 测试文件合计 5 个授权文件，与 brief 变更文件清单一致。
- `docs/plans/**` 与本 epoch 的 evidence 文件未纳入实现提交；实现提交树仅含上述 1 个文件。

### Invariant / Acceptance Coverage

- I-1：S-1 逐字 DOM（含 `multiple` 且无 `accept`）在源码中唯一存在，仅图标按钮无可见文字。
- I-2/I-3：由 `mailboxOutboundAttachments.test.js`（21 例，含 pending Promise 切 A/B、上传中移除、迟到回包不复活、两分支 payload、requestId 失效边界）与全量 944 例覆盖。
- I-4：`contextPathValue()` 前缀同时用于 ICS 已发卡与通用附件卡；已发卡无移除动作、`download` 属性、名称走转义。
- I-5：ICS 与通用附件字段/卡片独立；工具栏顺序与会议/跟进入口不变；`mailbox-chat.css` 字节不变；未改 `index.html` 缓存键（child 08 职责）。
- S-2：`outbound-*` 规则仅追加在 `styles.css`；草稿卡位于会议附件卡之后、footer 之前；已发卡位于正文与 ICS 之后。

### Deviations

- 无。唯一改动即 A2 授权的那 1 处断言；未触碰任何其他文件，未改 `mailbox-chat.css`，未 bump `index.html` 缓存键，未 push/merge/rebase/amend。

### Freshness

- Plan identity rechecked: YES（执行前后均为 `4d598349ecb2e97ffd1342c2098484653ed0c9d1f7eaa5cfb51cb88039510f9e`；brief 与 plan 逐字节相同）
- Worktree identity rechecked: YES（root/branch/git-dir 在 `git add`/`git commit` 前后一致）
- Reported commits reachable from target branch: YES（`ef836c3` = `fast/meeting-mail-master` HEAD，`merge-base --is-ancestor` 通过；`b003e4e` 仍为其祖先且未被改写）
- Required commands run this invocation: YES（见命令表，全部在最终代码状态上复跑）
- Historical evidence used only as baseline: YES（epoch-1 的 944/943/1 仅作对照）

### 未验证（本执行无法覆盖）

- A-1/A-2/A-3/A-4 人工验收清单全部未执行：需要部署中的 `/talent` 实例 + MySQL/ES + 可用 SMTP 沙箱 + 1440px/760px 浏览器会话与 Slow 3G 限速；本执行环境的授权范围仅为上述 JS 命令，故按计划设计留给人工验收。
- 「浏览器真实下载文件 SHA 与原文件一致」与「刷新后已发附件仍可下载」两条验收：同上，未在真实部署上执行；本 epoch 仅以单测断言 `contextPathValue()` 前缀拼接覆盖其 URL 构造侧。
- 上述未验证项没有具体报错信息可记录——它们是本执行环境的既有能力边界（无运行中的后端与信源），不是命令失败。
