## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master/docs/plans/2026-10-27/user1-mail-04-admin-grant-ui.md`
Plan SHA-256: `5549ee326657db040e0866824fb957f91bfb0fbf215d4117074a538e1a8840ac`
Plan identity (brief reference): `commit:4d1698bb7c51120e98cfe8f6a412a4589fc9defd`
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master/docs/plans/2026-10-27/user1-mail-04-admin-grant-ui.md@5549ee32…840ac`
Execution epoch: NEW
Approval basis: fast-p controller assignment (child 04 of 5) + brief `docs/plans/fast/user1-mail-master/children/04/brief.md`
Executor: Child04Implementer
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master`
Target branch: `fast/user1-mail-master`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master@fast/user1-mail-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-user1-mail-master`
Pre-execution code SHA: `917707f20adb64b02cce761e70a207baada29076` (brief base/product boundary `5c104aa301505e9db8a84e1649d60cbf290f3f00`; no product commits between)
Post-execution code SHA: `419f49ad6182dd1f62a1445c0f3cc582c21e1d5b`
Evidence HEAD: N/A (report left untracked per brief)
Implementation boundary: `917707f..419f49a` (3 files)

### Task Status
| Requirement | Status | Files | Evidence |
|---|---|---|---|
| 04-1 状态生命周期（I-1/I-2/I-3/S-1）：`readAccess{contactId,phase,canRead,generation}`，selectExpert/清空选择/身份就绪/unmount 参与代次 | IMPLEMENTED | `src/main/resources/static/mailbox-chat.js` | instance state + `beginReadAccess`/`invalidateReadAccess`/`readAccessContextAlive`; tests I-3 三条 |
| 04-2 UI 与事件（I-1/I-2/I-4/S-1）：admin actions 末尾追加两节点、点击分派、失败只 GET 重试 | IMPLEMENTED | `mailbox-chat.js` | `readAccessNodesHtml`/`toggleReadAccess`/`onClick` 分支；S-1/I-1/I-2 测试 |
| 04-3 竞态（I-3/S-1）：晚到读/写回包不写新专家，回旧专家重新 GET | IMPLEMENTED | `mailbox-chat.js` | I-3 慢 GET/慢 PUT 用例、unmount 用例 |
| 04-4 版本与测试（I-4/S-1）：index 全部本地 `?v=` 统一 `20261027-user1-mail-access`，保留 03 的 `mailbox-readonly.js` 紧邻 `app.js` | IMPLEMENTED | `index.html`, `src/test/js/mailboxMailReadAccess.test.js` | I-4 资源版本用例（键从 index 派生，避免与 taskActivityCenter I-8 键字面量守卫冲突） |

### Commands
| Command | Result | Evidence |
|---|---|---|
| `node --check src/main/resources/static/mailbox-chat.js` | PASS | exit 0 |
| `node --test src/test/js/mailboxMailReadAccess.test.js`（首次落地自检） | PASS | 17 tests / 17 pass / 0 fail |
| `node --test src/test/js/mailboxMailReadAccess.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxReadOnly.test.js src/test/js/mailboxServerDrafts.test.js` | PASS | exit 0；285 tests / 285 pass / 0 fail |
| `node --test src/test/js/*.test.js` | PASS | exit 0；1911 tests / 1911 pass / 0 fail（基线 1894 + 新增 17） |

未运行：`mvn test`（brief 明确该命令在本 base 为预存红、超出本子步范围）。

### Browser probe（S-1 布局，真实 Chrome 156 headless + CDP）
环境无后端会话，故用**生成的 header DOM 副本**（原 5 个动作按钮 + S-1 两个新节点，加载真实 `styles.css`/`mailbox-chat.css`）在 `/tmp/g04/layout.html` 实测，非整站会话：

| 视口 | 节点数 | 重叠 | 越界 | flex-wrap | disabled opacity | inline style |
|---|---|---|---|---|---|---|
| 1440×900 | 7 | 无 | 无 | wrap | 0.45 | 0 |
| 390×844 | 7 | 无 | 无 | wrap | 0.45 | 0 |

徽标 `aria-live="polite"`、按钮 `disabled` 均按加载态渲染。端到端 A-1/A-2/A-3（reader 刷新、断网重试、草稿/受控发信）仍需人工按清单执行。

### Changed Files
- `src/main/resources/static/mailbox-chat.js` — 新增 `READ_ACCESS_*` 常量、实例 `readAccess` 状态、`readAccessUrl/readAccessAdmin/readAccessTargetContactId/readAccessView/readAccessNodesHtml/applyReadAccessView/loadReadAccess/beginReadAccess/toggleReadAccess`、`renderHeader` 末尾追加节点、`onClick` 分派，以及 selectExpert/clearSelectedConversation/loadAuthenticatedUser/unmount 四处理代次。
- `src/main/resources/static/index.html` — 19 处本地 `?v=` 统一为 `20261027-user1-mail-access`；`mailbox-readonly.js` 仍紧邻 `app.js`（未改注册顺序）。
- `src/test/js/mailboxMailReadAccess.test.js`（新增）— 真实 `mailbox-chat.js` + 最小真实 DOM 的行为测试（17 用例）与 S-1/I-4 契约断言。

### Invariant notes
- I-1：仅 `instance.user === "admin"` 产出节点；`data-contact-id` 由点击按钮提供，`toggleReadAccess` 使用该 id 且要求其等于当前选择，不读回请求后的选择；PUT/DELETE 无请求体。
- I-2：`readAccessPayload` 校验 `username=user1`、`contactId`、`canRead:boolean`；PUT 还要求返回的 `canRead === grant`，否则错误态；错误态点击只 `loadReadAccess()`（GET），不重发写。
- I-3：`generation` 随换专家/清空/身份就绪/unmount 递增；写回门槛为 disposed/身份/contact/generation/当前选择全匹配。
- I-4：original `mc-open-materials`/`mc-manage-expert`/`mc-add-schedule`/`mc-edit-schedule`/`mc-cancel-schedule` 全部保留；授权点击只命中 `.../read-access/user1`；`mailbox-readonly.js` 与 `app.js` 未改动、无 grant 引用。
- S-1：无新增 CSS/内联样式，只用既有 `.mc-actions`/`.button`/`.mc-badge`；文案矩阵与计划表逐字一致；`renderHeader` 重建时仅从内存状态回填（不触发 GET），写入/读取结果仅改 `textContent`/`disabled`。

### Deviations
- 未设置 `aria-busy`（计划允许但不是必需），状态只改 `textContent`/`disabled`；加载/保存仍以 `disabled` 表达。
- 身份就绪（`/api/auth/me` 成功）时对已选专家追加一次对账 GET（`beginReadAccess`），用于“身份变化作废代次”；正常挂载只在 selectExpert 触发一次 GET。
- 首次实现的注释含字面「关注」，触发 `mailboxChatStyle.test.js` 既有守卫（`!/关注/.test(chatSource)`），已改写注释文字，未改任何测试或行为。
- 新增测试的缓存键从 `index.html` 源文本派生（`taskActivityCenter.test.js` I-8 禁止测试/脚本内固定键字面量），仍断言 `^[0-9]{8}-user1-mail-access$` 与全量一致性。

### Freshness
- Plan identity rechecked: YES（执行前后同一 SHA-256）
- Worktree identity rechecked: YES（root/branch/git-dir 与提交前一致；提交后 `HEAD` 可达 `fast/user1-mail-master`）
- Reported commits reachable from target branch: YES（`merge-base --is-ancestor` 通过）
- Required commands run this invocation: YES（全部在最终代码状态后重跑）
- Historical evidence used only as baseline: YES（仅 1894 pass 基线）
- `docs/plans/fast/**` 未纳入本次提交（`git show --stat` 仅 3 个授权文件）

### Remaining Blocker
- None.

### Next Action
- READY_FOR_VERIFICATION → run `verify-p`
