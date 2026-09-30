# Child 01 Pause — Epoch 1 PLAN_CONFLICT

- Child: 01（`docs/plans/2026-09-30/manual-reply-template-reference.md`，单子计划 run）
- Plan identity: `commit:697441829efdb7b438db71a786e5123faf94681c`
- Epoch: 1；fix_round: 0
- Writer: agent `ImplTemplateRef01`（execute-p），2026-09-30
- Base: `697441829efdb7b438db71a786e5123faf94681c`
- Product commit: `2df9170`（`feat(fast-p): implement 01`，7/7 授权文件；`src/main/resources/static/mailbox-chat.js` +722、`styles.css` +66、`index.html` 22 行键改动、4 个测试文件）
- Execution report: `children/01/execution.md`（由控制方随本暂停证据提交）
- 视觉证据（构建产物，未入库）：`/tmp/fastp01-dialog-1280.png`、`/tmp/fastp01-dialog-740.png`、`/tmp/fastp01-compose-filled.png`、`/tmp/fastp01-applied-1280.png`、`/tmp/fastp01-toolbar.png`；`/tmp/fastp01-red.log`（修复前红：158 tests / 128 pass / 30 fail，实施后 158/158）

## Required commands on `2df9170`（控制器复跑，日志 `/tmp/fastp01-targeted-ctrl.log`、`/tmp/fastp01-full-ctrl.log`）

| Command | Result |
|---|---|
| `node --check src/main/resources/static/mailbox-chat.js` | exit 0 |
| 计划 9 文件 targeted `node --test …` | exit 1 — tests 234 / pass 233 / fail 1 |
| `node --test src/test/js/*.test.js` | exit 1 — tests 1265 / pass 1264 / fail 1 |
| `git diff --check` | exit 0 |

唯一失败：`src/test/js/mailboxOutboundAttachments.test.js:1559`「按钮无可见文字、title/aria=上传附件；input multiple 无 accept；工具栏顺序 B/I/列表/链接/回形针/会议/跟进」——`deepStrictEqual` 期望 8 项 action 顺序，实际 9 项（多出 `mc-open-template-reference`）。

## Why the child paused

### F-1（plan conflict — 未授权文件）：`mailboxOutboundAttachments.test.js:1558–1564`

- 该用例用 `assert.deepStrictEqual` 把整条工具栏 action 序列钉死：`["mc-rich-command" ×4, "mc-upload-attachment", "mc-open-meeting", "mc-open-material-request", "mc-open-followup"]`。计划 S-1 要求插入「引用模板」（材料之后、跟进之前），该断言必然失败。
- 计划审计 §4 只枚举了两处 toolbar 精确顺序断言（`materialRequestIntegration.test.js:815`、`meetingConfirmationIntegration.test.js:1423`），遗漏本文件；T-4 的授权测试文件也不含它。
- 该文件不在 7 个授权文件内；计划文本禁止执行者静默扩文件（T-5：「若产生新的硬编码测试文件，先修订文件清单并检查10文件上限，不静默扩文件」）。→ 需要计划修订并由人工批准。
- 控制器复核（grep `mc-open-followup`）：全仓精确顺序断言正好 3 处，除本处外其余 2 处已按计划更新；`mailboxChatBehavior.test.js` 新增用例断言 9 项新顺序并通过。

## Required amendment (needs human approval)

**A1**：把 `src/test/js/mailboxOutboundAttachments.test.js` 追加为授权文件 #8（8 ≤ 计划上限 10），并只改该文件的顺序断言：在 `"mc-open-material-request"` 与 `"mc-open-followup"` 之间插入 `"mc-open-template-reference"`，同步更新断言消息文本（与 T-4 已对另两个 integration 测试的处理一致）。产品实现无需返工；无其他失败。

## Resume plan (after A1 approval)

1. 单独提交修订后的 child 计划（master/child 同文件）+ 追加 Amendments 行 A1（Before/After 提交标识、master rule、理由、HUMAN 批准）。
2. 新执行 epoch 2，fix_round = 0：writer 仅改上述断言行，跑计划必需命令（targeted + 全量 JS + `node --check` + `git diff --check`），提交 `fix(fast-p): repair 01 round 1`（或按 epoch 语义的实现提交）。
3. 全新 verifier（区别于所有 writer）做四门禁轻量验证；之后进入 finalization。

## Paused-at state

- Worktree/branch retained：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-30-manual-reply-template-reference` @ `fast/2026-09-30-manual-reply-template-reference`
- Product code head：`2df9170`；无 fix commits；计划未编辑。
- 工作树仅 `children/01/execution.md`（writer 报告）随本暂停提交入库后即干净。
