# Child 03 执行报告 — PLAN_CONFLICT

## Execution Result: PLAN_CONFLICT

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master/docs/plans/2026-10-27/user1-mail-03-readonly-ui.md`
- Plan SHA-256: `15749228a7ae633cc0b88c58af989af78ad40e8ffb4d14eb8a0a24ab843e9823`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master/docs/plans/2026-10-27/user1-mail-03-readonly-ui.md@15749228a7ae633cc0b88c58af989af78ad40e8ffb4d14eb8a0a24ab843e9823`
- Execution epoch: NEW
- Executor: Child03Implementer
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master`
- Target branch: `fast/user1-mail-master`
- Worktree ID: `…-fast-user1-mail-master@fast/user1-mail-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-user1-mail-master`
- Pre-execution code SHA: `86822d1a0179785da23841a8a35fae14fdd9a0e8`
- Post-execution code SHA: N/A（未提交；改动保留在工作区）
- Evidence HEAD: N/A

## 冲突（PLAN_CONFLICT）

计划/任务书要求：

- `index.html` 在 `app.js` 前注册**带版本键**的 `mailbox-readonly.js?v=20261027-user1-readonly`；
- 所有既有 `?v=` 资源同键（数量从 18 变为 19）。

但仓内 6 个**授权清单之外**的测试把 18 个资源列表/数量写死。落地注册后全量 `node --test src/test/js/*.test.js` 出现 8 个新失败，全部是同一原因（18 vs 19 / 数组缺 `mailbox-readonly.js`），无其他失败：

| file:line | 断言 |
|---|---|
| `src/test/js/discoveryPapers.test.js:81` | exact approved HTML/CSS contract, real IDs and asset keys |
| `src/test/js/discoveryReview.test.js:464` | c5 discovery review · scope and confirm (I-2) |
| `src/test/js/discoveryReview.test.js:1073` | keys.length === 18（Apollo logs / cache-busted assets） |
| `src/test/js/mailboxChatBehavior.test.js:6790` | 所有版本化资源顺序数组（缺 `mailbox-readonly.js`） |
| `src/test/js/mailboxGroupPush.test.js:85` | I-4: resources retain order… + `keys.length 18` |
| `src/test/js/meetingConfirmationAssets.test.js:38` | 恰好 18 个带 `?v=` 资源 |
| `src/test/js/meetingConfirmationAssets.test.js:47` | 18 个资源 = 9 旧 + world-clock |
| `src/test/js/trustReplyWorkbenchSharedMount.test.js:180` | exactly eighteen cache-busted asset URLs |

证据（本次运行）：

```
$ node --test src/test/js/*.test.js
ℹ tests 1894
ℹ pass 1886
ℹ fail 8
# 8 个失败均为上述 asset list/count 断言（18 !== 19）
```

基线声明为 1851 pass / 0 fail；本分支已含 03 之前子计划新增测试，故总数增长。若不注册版本化脚本则 8 个失败消失，但违反任务书 “index.html must contain `<script src="mailbox-readonly.js?v=20261027-user1-readonly"></script>` immediately before the versioned app.js” 的下游契约（04 只 bump 所有 `?v=`）。

可直接修复方案（被判定越界，未执行）：在 6 个测试中把 18→19 并在 `app.js` 前插入 `mailbox-readonly.js`；各文件 CACHE_KEY 均由 `styles.css?v=` 派生，无需写死新键。

## 已完成（保留在工作区，未提交）

| 需求 | 状态 | 文件 |
|---|---|---|
| S-1/S-2/S-3、I-2/I-3/I-4 只读模块与宿主分流 | IMPLEMENTED（in-scope） | 见下 |

实现摘要：

1. `mailbox-readonly.js`（新增）：唯一 `window.MailboxReadOnly={mount,unmount}`；S-1 精确 DOM；列表 20/页、详情 `limit=50`、`before` 加载更早页；`source:id` 去重 + 时间正序；AbortController+15s 超时；403/401/网络错误清正文与 cursor；focus/visible 合并为单组在途请求；全部 textContent；无 Storage/innerHTML/inline style。
2. `app.js`：`api()` reader 白名单前置拒绝（`READ_ONLY_FORBIDDEN` 本地 403）；`startAuthenticatedApp(username, mailReadOnly)` 在 admin 启动链前分流；user1 缺 `mailReadOnly===true` → “权限信息异常，请重新登录”；身份变化整页重载；`setView`/`refreshCurrentView`/`loadMailbox`/`loadMailboxAccounts`/`refreshMailboxGroupPush`/`refreshAutoReplySummary`/`updateUnmatchedBadge`/`bindMobileCoreNavigation`/logout 草稿 flush 均 reader 早退（均以 `typeof` 守卫，兼容既有 vm 沙箱）；`stopAuthenticatedApp` 卸载模块并清正文。
3. `index.html`：全部 `?v=` 统一 `20261027-user1-readonly`；`mailbox-readonly.js` 紧随 `discovery-recheck.js`、紧接 `app.js` 之前，`world-clock.js` 仍在其后。
4. `mailbox-chat.css`：末尾逐字追加 S-2（`body[data-mail-read-only="true"]` 作用域 10 条选择器），未改既有规则。
5. `src/test/js/mailboxReadOnly.test.js`（新增，17 用例）：真实模块 + 最小 DOM 模型，覆盖 S-1 骨架、XSS 文本化、403/401/网络、慢 A→B 竞态、unmount abort、focus 合并、分页/更早页去重、搜索 q。
6. `src/test/js/authFlow.test.js`：新增 login/me 双入口、缺元数据、模块缺失、admin 回归、8 个生命周期早退、api 白名单（共 +18 用例）。
7. `src/test/js/mailboxChatStyle.test.js`：新增 S-2 逐字/作用域、脚本顺序与版本、class 白名单、无 inline style、固定文案、关键 CSS 值、真实 index 壳节点；并为多行 reader 规则放宽既有的“全局规则”门禁（`}`/声明行跳过、允许 `body[data-mail-read-only="true"]` 前缀）。

## Commands（本次运行）

| Command | Result | Evidence |
|---|---|---|
| `node --check src/main/resources/static/mailbox-readonly.js` | PASS | exit 0 |
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `node --test src/test/js/mailboxReadOnly.test.js` | PASS | 17 pass / 0 fail |
| `node --test src/test/js/authFlow.test.js` | PASS | 43 pass / 0 fail |
| `node --test src/test/js/mailboxChatStyle.test.js` | PASS | 48 pass / 0 fail |
| `node --test src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxServerDrafts.test.js src/test/js/mailboxOutboundAttachments.test.js` | FAIL | 仅 `mailboxChatBehavior.test.js` 资产数组 1 项失败（同为 18/19），其余通过 |
| `node --test src/test/js/*.test.js` | FAIL | 1894 tests / 1886 pass / 8 fail（全部为上述资产列表/数量断言） |

未运行 `mvn test`（任务书明示跳过，预存在 19 项无关失败）。

## Changed Files（工作区，未提交）

- `src/main/resources/static/mailbox-readonly.js`（新增）
- `src/main/resources/static/app.js`
- `src/main/resources/static/index.html`
- `src/main/resources/static/mailbox-chat.css`
- `src/test/js/mailboxReadOnly.test.js`（新增）
- `src/test/js/authFlow.test.js`
- `src/test/js/mailboxChatStyle.test.js`

工作区另有先前已存在的修改（非本次产生、未触碰）：`docs/plans/fast/user1-mail-master/ledger.md`，以及未跟踪目录 `docs/plans/fast/user1-mail-master/children/03/`（本报告所在）。

## Deviations

- 无越界实现改动；未提交；`docs/plans/fast/**` 未纳入任何提交。
- 浏览器/视口（1440/390px）证据不可用：本环境未启动真实应用（user1 登录仍关闭），样式契约以真实 `index.html`/CSS 文本断言校验；未声称截图验证。

## Remaining Blocker

需要人工裁决：是否把 6 个既有资产清单测试加入本子计划授权范围（18→19 + 在 `app.js` 前插入 `mailbox-readonly.js`）。在裁决前无法同时满足“7 文件范围”和“全量 0 失败”。

## Next Action

- PLAN_CONFLICT → 修改计划授权范围后重跑（`RESUME` 同一 EXECUTION_ID 或新 epoch）。
