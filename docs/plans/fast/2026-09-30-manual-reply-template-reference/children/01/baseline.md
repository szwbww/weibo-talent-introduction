# Child 01 — Controller Baseline Command Evidence

- 运行边界：seed 提交 `6974418` 之后、identity 提交 `ed408b2` 之上（两者均为 docs/plans-only）；产品代码与 master base `a37efe9` 字节一致（本 worktree 自 `a37efe9` 干净检出）。
- 运行时间：2026-09-30T04:20Z。运行者：控制器（fast-p controller）。
- 说明：`src/test/js/mailboxTemplateReferenceStyle.test.js` 为本 child 新增文件，基线时尚不存在，故基线 targeted 命令只跑既有 8 个文件。

## 基线命令与结果（fresh，exit code 逐条记录）

1. `node --check src/main/resources/static/mailbox-chat.js` → exit 0（node v25.7.0）
2. `node --test src/test/js/mailboxChatBehavior.test.js src/test/js/materialRequestIntegration.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxOutboundAttachments.test.js src/test/js/composeTemplatePreview.test.js src/test/js/meetingConfirmationAssets.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js` → exit 0，tests 202 / suites 44 / pass 202 / fail 0 / skipped 0（日志：`/tmp/fastp-manual-template-baseline-targeted.log`）
3. `node --test src/test/js/*.test.js` → exit 0，tests 1233 / suites 243 / pass 1233 / fail 0 / cancelled 0 / skipped 0 / todo 0（日志：`/tmp/fastp-manual-template-baseline-full.log`）
4. `git diff --check` → exit 0；`git status --porcelain` 为空

## 实施前复核（计划 T-5）

- `index.html` 当前 11 个版本化资源键均为 `20260929-bounce-alert`。
- `grep -F '20260929-bounce-alert' src/test`（grep 工具，worktree 内）无命中：`trustReplyWorkbenchSharedMount.test.js` 的 G-5 用例从 index.html 的 `styles.css?v=` 动态派生键值并断言 11 项同值，另用正则 `\?v=([0-9a-z-]+)` 计数；无写死旧键的测试文件需要随 T-5 修改。

## 基线结论

- 既有 1233 用例全绿，无基线红。实施后 targeted（含新增 style 用例）与全量必须仍为 fail 0；任何新失败均归因于本 child 改动。
- 本 child 无 Kotlin/SQL 改动；按计划不要求 Maven/启动生产/重跑迁移。
