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
