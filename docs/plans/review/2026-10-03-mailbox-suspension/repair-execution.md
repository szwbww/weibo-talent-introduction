# Repair Execution — mailbox-suspension V-1

## Approval Source

- Human invocation (this session): `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-03-mailbox-suspension/docs/plans/fix/mailbox-suspension/repair.md`，2026-10-03T23:08+0800 执行。
- 计划授权条款：repair.md「Review-Fast-P Execution Handoff」——显式人工 `$execute-p <repair.md>` 调用授权：仅本计划 Authorized Files 与验证命令、恰一个产品提交 `fix(mailbox): guard stale suspension UI callbacks`、本文件追加、恰一个 docs-only 证据提交 `docs(review-fast-p): record repair execution`、在调用要求时同任务返回 `$review-fast-p`。

## Repair Identity

- Plan（仓库相对）：`docs/plans/fix/mailbox-suspension/repair.md`
- Canonical path：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-03-mailbox-suspension/docs/plans/fix/mailbox-suspension/repair.md`
- Plan SHA-256：`cd2ab0dd24c91a3d8f4e93bcfdea12deccbef6f96eef8dc048261aadbc3fc592`（执行前后重算一致）
- Execution ID：`<canonical path>@cd2ab0dd24c91a3d8f4e93bcfdea12deccbef6f96eef8dc048261aadbc3fc592`
- Execution epoch：`NEW`（此前无同一 EXECUTION_ID 的执行证据）
- Finding：`V-1`（P1，02 I-5 / T3；root cause：`loadSuspensionState`、`onSuspensionClick`、`confirmSuspensionReason`、`resumeSuspension`、`endSuspension` 只检查部分状态或仅 `disposed`；`returnToMobileList` 不作废在途挂起工作）
- 基线审查：`docs/plans/review/2026-10-03-mailbox-suspension/machine-verification.md`（epoch 1，`FAIL` / `INITIAL` / `DRAFT_READY`）

## Worktree / SHAs

- Worktree root：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-03-mailbox-suspension`
- Branch：`fast/2026-10-03-mailbox-suspension`
- Worktree ID：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-03-mailbox-suspension@fast/2026-10-03-mailbox-suspension@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-03-mailbox-suspension`
- Pre-execution code SHA：`58d6869bb2f74afb73ce17600aa89b4920987d6a`（分支 HEAD；被审产品代码 head 为 `0837c372f45d69374084113d8258d8c3320d748c`）
- Post-execution code SHA：`1c65a2c47f25c3783d8e158120496a133596e951`
- Implementation boundary：`0837c372f45d69374084113d8258d8c3320d748c..1c65a2c47f25c3783d8e158120496a133596e951`（在被审代码之上只叠加本次修复）

## Changed Files（恰 2 个授权文件）

| 文件 | 目的 |
|---|---|
| `src/main/resources/static/mailbox-chat.js` | 挂起回调上下文守卫（`workEpoch`/`paneEpoch`/`convEpoch`/`user`/disposal）、移动端返回作废在途挂起工作、迟到回包 no-op 自清理 |
| `src/test/js/mailboxSuspension.test.js` | 5 个判别性回归用例（状态 GET / 原因 GET / PUT / 取消 DELETE / 结束 DELETE） |

## Commands（本 invocation 新鲜运行）

| 命令（cwd = worktree root） | 结果 |
|---|---|
| `node --check src/main/resources/static/mailbox-chat.js` | exit 0 |
| `node --test src/test/js/mailboxSuspension.test.js` | exit 0；tests 30 / pass 30 / fail 0 |
| `node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxSuspension.test.js src/test/js/mailboxSuspensionStyle.test.js src/test/js/mailboxChatStyle.test.js` | exit 0；tests 212 / pass 212 / fail 0 |
| `node --test src/test/js/*.test.js` | exit 0；tests 1426 / pass 1426 / fail 0 / skipped 0 |

TDD 证据：修复前 5 个新用例全红且各自失败在 V-1 断言（`/tmp/repair-t1-red2.log`：30/25/5——「迟到的挂起状态不得写入详情」「迟到的原因 GET 不得为隐藏详情打开表单」「A 的迟到 PUT 不得撤下 B 的表单」「迟到 DELETE 不得切换 Tab」×2）；修复后全绿（`/tmp/repair-t1-green.log`）。全量 JS 由 1421 增至 1426（+5）。

## Repair Semantics

- 新增 `captureSuspensionContext(contactId)` / `suspensionContextAlive(ctx)`：挂起状态 GET、原因 GET、PUT、取消 DELETE、结束 DELETE 的回包只在发起时 `convEpoch` + `paneEpoch` + `user` + `suspension.workEpoch` + 未 disposed 时生效。
- `returnToMobileList()` 追加 `invalidateSuspensionWork()`（`workEpoch`/`seq` 递增、清 `loading` 与 `completion.busy`、撤下未提交原因表单）；换专家/换用户/卸载原有 `resetSuspensionState()` 同步递增 `workEpoch`。
- 迟到分支只做自身控件清理，不产生跨上下文效果：PUT 迟到仅解除同一表单 busy；取消迟到仅恢复同一触发按钮；结束迟到仅解除同一完成行 busy；状态/原因 GET 迟到直接 no-op（不渲染、不切 Tab、不刷新列表、不改选中专家）。

## Deviations

- 审查 finding 的 I-5 绑定集合列出 `listSeq`；本修复按挂起相关上下文（contact/conv/pane/user/workEpoch/disposal）绑定，未纳入 `listSeq`：`listSeq` 在每次例行列表刷新时都会变化，用它门禁用户发起的 PUT/DELETE 完成回调会把表单/按钮永久卡在 busy 态。R-1 回归规格中的全部迟到场景均由 5 个用例覆盖，验收标准未削弱。
- `instance.suspension.loading` / `error` 只作状态、从不渲染；`invalidateSuspensionWork` 仍清 `loading`。
- 移动端返回不重置 `processConfirm`（原位处理确认）：其 POST 已由既有 `convEpoch` 守卫，且仅重置状态而不重绘会让「取消」按钮变死；不在 R-1 列出的五类回调内。

## Executor Identity

- 单一本地 omp 会话（controller 直接执行）；本任务未派发子代理，无更细的 agent identity 可暴露。

## Clean-State Evidence

- 产品提交后 `git status --porcelain` 为空；证据提交后再次为空。
- 产品提交 `1c65a2c47f25c3783d8e158120496a133596e951` 为 `fast/2026-10-03-mailbox-suspension` 的 HEAD，且从目标分支可达；提交仅含上表 2 个文件。
- 证据提交 `docs(review-fast-p): record repair execution` 仅含本文件。
