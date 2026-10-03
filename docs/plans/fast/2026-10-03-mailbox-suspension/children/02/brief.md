# Fast-P Child Brief — 02（挂起交互、默认 Tab 与完整 CSS）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-10-03/mailbox-suspension.md`，identity `commit:c486c5c44806b5b4c4db654606c358efb94fec5a`。
- 本 child 批准计划（完整合同，必须先通读）：`docs/plans/2026-10-03/mailbox-suspension-02-frontend.md`，identity `commit:c486c5c44806b5b4c4db654606c358efb94fec5a`。「需求描述」「关键不变量」I-1～I-7、「样式契约」S-1～S-4（含逐字 CSS 块）、「实现方案」T1～T4、「变更文件清单」「验收标准」逐条生效。
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-03-mailbox-suspension`；branch `fast/2026-10-03-mailbox-suspension`；`child_base_sha` 见派发消息（= child 01b 的 code head；01 与 01b 已实现并通过轻量验证）。
- 依赖：01（真实挂起 API 与列表摘要）、01b（mark-resolved Session 身份与成功回包）。
- 取证材料（worktree 内只读）：`docs/plans/2026-10-03/mailbox-suspension-evidence/`（frontend-usage.txt、baseline-mailbox-chat.css/js、target-suspension.css、preview-v6.css/js、approved-slate-v6.png、revision-v6-*、concurrent-mobile-mailbox.diff 等）。

## 全局约束

1. 只允许修改「Authorized Files」表内 6 个文件；其余全部只读，特别是 `app.js`、`mailbox-chat.css`、`trust-reply-workbench.js`、其它静态资源、其它 `src/test/js/*.test.js`（除表内 3 个）、全部 Kotlin/SQL/迁移。
2. 不得修改 `docs/plans/**`（计划与证据由控制方提交）；执行报告写到本 child 目录 `execution.md`，不进入产品提交。
3. 不得 push、merge、rebase、squash、amend、reset。产品代码只提交一次：`feat(fast-p): implement 02`。
4. 计划冲突、白名单外文件、新行为、需修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`，不自行扩范围。
5. 禁止联网、连线上 MySQL/ES、发信、部署；不得新增依赖；不改 `pom.xml`；不得新增静态资源引用。
6. 既有并行移动端改动（mobile pane/saveCurrentConversation/returnToMobileList/paneEpoch 等）**全部保留**，不回退、不替换整文件；挂起成功的切 Tab 必须复用它们。
7. 不得使用预览页的模拟读写适配器、全局对象、示例数据、顶部开关或轮询逻辑；S-1～S-4 是唯一实现依据。
8. 新增"按 id 取元素再写入"的渲染函数时，测试必须额外断言该 id 出现在 `index.html` 源文本里（K-dom-stub-tests-hide-dangling-refs）。
9. 基线命令结果见 `docs/plans/fast/2026-10-03-mailbox-suspension/children/02/baseline.md`（全量 JS 基线计数在此对照）。

## Authorized Files（6）

| # | 精确路径 | 改动 |
|---|---|---|
| 1 | `src/main/resources/static/mailbox-chat.js` | Tab/默认进入/挂起与确认/独立 DOM 槽/异步守卫 |
| 2 | `src/main/resources/static/styles.css` | 仅逐字追加 S-4 完整 CSS 块（含起止注释） |
| 3 | `src/main/resources/static/index.html` | 统一缓存键为 `20261003-mailbox-suspension` |
| 4 | `src/test/js/mailboxChatBehavior.test.js` | 更新默认语义、保持旧回归 |
| 5 | `src/test/js/mailboxSuspension.test.js` | 新功能交互/请求与竞态（新文件） |
| 6 | `src/test/js/mailboxSuspensionStyle.test.js` | 逐字 CSS 和 DOM 样式契约（新文件） |

## 关键不变量（计划 I-1～I-7；冲突时以计划原文为准）

- I-1 Tab 与默认进入：固定 全部/关注/待处理/已挂起/已回复/待匹配。普通新 mount 先查询当前列表筛选范围的 pendingOnly 页；total>0 保留待处理，否则仅这一次切关注。挂起会话和未关联待匹配不算可处理专家。已有 focus 深链接优先，不被默认探测覆盖。失败保留错误与重试，不把失败当 0。
- I-2 后端状态权威：禁止从可见时间线长度或 pendingCount 自减决定结束。只能 GET 01 状态判断跨账号 `suspensionPendingCount=0`；标记处理成功后重新读取。写失败不移动卡片、不关闭行内原因表单、不清空原因。已挂起 0 条仍出现在挂起页。
- I-3 只提示不自动结束：suspended=true 且跨账号计数=0 时，在使计数归零的已处理来信 footer 下追加唯一提示行，不浮层、不抢焦点。继续挂起后该行变"已继续挂起"，保留"结束挂起"；刷新同一 mount 保留选择。新 pending>0 时移除提示并重置保留标记；新挂起周期或重新 mount 可重新显示。无本次处理锚点时放在当前已加载时间线最后一条 PROCESSED 入站消息下；窗口无此消息则只保留顶部"取消挂起"入口，不凭空生成邮件。
- I-4 状态变更与现有规则分离：显式取消接口回包 count>0→待处理；count=0 且 followed=true→关注；其余→已回复列表（服务器原资格规则决定可见性）。不得拼入专家或强行取消关注；不满足原规则时显示原空列表。
- I-5 异步与会话隔离：全部请求绑定 contactId、listSeq/convEpoch/paneEpoch、当前 user 及 disposed；切换专家/Tab 或 unmount 后旧回包不覆盖新会话。等待默认探测时用户点击 Tab/筛选即取消该探测决策。发请求置 busy 禁止重复提交；同一组件只保留一份行内原因表单；处理确认以 contactId+source:id 绑定。草稿保留，不重建整个详情编辑区。
- I-6 原因与样式边界：原因 trim、最多 500 个 UTF-16 单位；以 escapeText/textContent 展示，换行保留；纯文字操作按钮无暂停伪元素。只追加 S-4，`mailbox-chat.css` 字节不动，无 inline style、无未声明 class。
- I-7 处理确认与真实身份："待处理"第一次点击只进入原位确认（取消/Escape 回到待处理，不发 POST）；确认才 POST 现有 mark-resolved，body 为 `{note:null}`，不传 resolvedBy/operatorName。01b 从 Session 取身份。收到成功才改已处理并 GET 挂起状态；失败保留确认和行内错误。操作人展示只取 01b 成功回包；历史时间线没有 resolvedBy 字段，不伪造当前账号为历史处理人。全流程无新 dialog/openActionDialog/alert/confirm/prompt。

## 样式契约（计划 S-1～S-4；S-4 CSS 为唯一权威）

- S-1：复用 `.mc-filters/.mc-filter/.mc-person/.mc-actions/.mc-text-button` 等；只对 `#view-mailbox.mc-refined .mc-filters/.mc-filter` 派生覆盖；六个 Tab 顺序由 FILTER_CHIPS 数组决定；待处理/已挂起各追加 `<span class="mailbox-suspend-count">`（计数不可用时隐藏，不伪造 0）。普通 active 主色 #3762d8/计数底 #e9efff；已挂起 active 文字 #475569/下划线 #64748b/计数底 #e2e8f0。
- S-2：卡片 footer（主选择 button 的兄弟，不嵌套 button）、详情头取消/挂起按钮、banner（仅 suspended=true）；`data-pending=true` 仅用于未挂起且 N>0，已挂起即使有未处理也必须 false、始终中性灰。原因有值 `挂起原因：{文本}`，无值 `未填写挂起原因`。N 一律用跨账号 suspensionPendingCount。
- S-3：三类行内交互（挂起原因 section、原位处理确认、完成行），DOM 结构逐字见计划；完成行位于使最后一个未处理消息完成的 article 内部、footer 之后，每位专家最多一行；无 dialog/backdrop/alert/confirm/prompt；动态文字转义；表单按钮 type=button；Enter 在 textarea 只换行。
- S-4：把计划中的 `/* mailbox-suspension-contract:start */ … /* mailbox-suspension-contract:end */` 整块**逐字**追加到 `styles.css` 末尾，与 `docs/plans/2026-10-03/mailbox-suspension-evidence/target-suspension.css` 字节一致；禁止改数值、删状态、额外添加全局 label/p/dialog 规则；≤760px 规则含 44px 触控尺寸与原因 16px。

## 实现要点（计划 T1～T4 摘要）

- T1 参数、默认进入与统计：`mailbox-chat.js` 新增 CHIP_SUSPENDED 与固定顺序；chipParams 只对 suspended 返回 suspendedOnly；待匹配仍用原队列 API。实例级初始化完成标记（不存 localStorage）；applyOptions 初始化完成后不据 pendingOnly=false 重选 Tab；用户点击任何 Tab/查询马上冻结默认决策并推进 listSeq；刷新现有 mount 不执行默认规则。Tab 计数用同筛选范围 GET page=0/size=1 的 total（pendingOnly 与 suspendedOnly 各一次请求，可复用当前列表结果）；失败隐藏数字；独立请求序号避免乱序。
- T2 行内原因与显式挂起/取消：点击挂起先 GET 该 contact 状态，可挂起才插入 S-3 section；确认 PUT `{reason: textarea.value.trim() || null}`；busy 防重；失效/409 显示真实提示并刷新。取消挂起/结束挂起直接 DELETE。成功后先调用现有 returnToMobileList/saveCurrentConversation 保留草稿及返回列表，再按 I-4 切 Tab、page=0，重新查询列表/计数。详情只更新挂起按钮槽/banner，不整段重建。成功文案按计划第 5 条。
- T3 原位处理确认与完成行：mc-mark-resolved 只写组件确认状态；新增 mc-process-cancel/mc-process-confirm；只在确认 handler 调 markResolvedByKey 的实际 POST 部分，去掉该函数的 openDialog 调用（只覆盖聊天视图已关联专家消息，不改待匹配管理页公共弹窗）。POST `{note:null}`，消费 01b 回包并校验 id 与目标一致。通过现有 GET `/api/auth/me` 获取一次 authenticated username（身份就绪前禁用新按钮；失败显示"登录状态读取失败，请刷新重试"，用独立 authenticatedUser 与生命周期序号守卫）。状态 GET 统一入口=选中专家加载/显式刷新/原标记或撤销成功/已有会话刷新成功；无轮询。仅同 contactId/convEpoch/paneEpoch/用户且未 disposed 的回包更新 UI。继续挂起仅记录本 mount 选择并改文案；unmount/切用户/手机返回清理。结束期间又收到消息仍允许 DELETE。
- T4 样式、缓存与测试：S-4 逐字追加；index 统一缓存键。当前基线 index.html 的 11 处资源版本键为 10×`20261003-mobile-core-03` + mailbox-chat.js 一处 `20261003-mobile-core-03-generic-followup`；执行时以当时完整清单为准（`rg -n '\?v=' src/main/resources/static/index.html`），统一改为 `20261003-mailbox-suspension`；并在 `src/test` 内反查这两个旧键的固定值命中，命中新硬编码文件须报告请求修订清单，不静默扩文件。现有测试中默认 all 的断言改为 fixture 明确的 pending 或 followed；新功能测试覆盖真实交互与请求序列；新样式测试从计划 S-4 提取 CSS 与 styles.css 注释范围字节比较、DOM class 映射、无 inline/按钮 pseudo 图标检查。真实浏览器至少 1280×800、390×844；键盘 Tab/Escape、500 字符换行、行内失败重试、focus 返回、页面切换与草稿回归；DOM stub 不能替代真浏览器。

## 下游接口（消费方，已在 01/01b 实现）

- `GET/PUT/DELETE /api/mail/mailbox/conversations/{contactId}/suspension`，状态对象 `{contactId, suspended, suspendReason, suspensionPendingCount, followed}`；400/401/404/409 语义。
- 列表 `GET /api/mail/mailbox/conversations?...&suspendedOnly=true`，列表项含 `suspended/suspendReason/suspensionPendingCount`。
- `POST /api/mail/unmatched-inbound/{id}/mark-resolved`（body `{note:null}`）→ `{id, processStatus:"PROCESSED", resolvedBy}`。

## 必需命令（fresh 运行，逐条记录 exit code 与计数；与 baseline.md 对照）

```sh
node --check src/main/resources/static/mailbox-chat.js
node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxSuspension.test.js src/test/js/mailboxSuspensionStyle.test.js src/test/js/mailboxChatStyle.test.js
node --test src/test/js/*.test.js
cmp src/main/resources/static/mailbox-chat.css docs/plans/2026-10-03/mailbox-suspension-evidence/baseline-mailbox-chat.css
```

- 按 K-js-test-invocation-surface：JS 测试单独运行，不以其它方式代替；先功能小集，再全量 JS 一次（无新修改不重复跑）。
- `mvn test` 的 JS 阶段（exec-maven-plugin）不作为本 child 门禁，但不得因改动使其必然失败；如运行需记录。
- 真浏览器验证（T-4）必须执行并记录视口、步骤、观察结果；工具不可用须显式报告为缺口而非跳过。

## 交付物

- 一个本地实现提交：`feat(fast-p): implement 02`。
- 执行报告：`docs/plans/fast/2026-10-03-mailbox-suspension/children/02/execution.md`（命令与计数、基线对照、真浏览器证据、验收标准逐条对照、S-4 字节校验、缓存键清单与 test 反查、偏差；不进入实现提交）。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
