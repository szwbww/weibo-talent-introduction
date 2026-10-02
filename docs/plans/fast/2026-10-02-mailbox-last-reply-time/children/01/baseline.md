# Child 01 — Controller Baseline Command Evidence

- 运行边界：seed 提交 `5d1789f90716a27e265b63340a9aef562a0035d9` 之上（identity 提交 `801146845b5e00098b288df42c35b587e2e5cdd5` 亦为 docs/plans-only）；产品代码与 master base `bf19fdf` 字节一致（worktree 自 `bf19fdf` 干净检出，记录 SHA256 与 `mailbox-last-reply-evidence/code-baseline.txt` 全部一致）。
- 运行时间：2026-10-02（本地）。运行者：控制器（fast-p controller）。Node v25.7.0。

## 基线命令与结果（fresh，exit code 逐条记录）

1. `node --check src/main/resources/static/mailbox-chat.js` → exit 0
2. `node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js` → exit 0，tests 134 / suites 21 / pass 134 / fail 0 / skipped 0（日志：`/tmp/fp-mailbox-targeted.log`）
3. `TZ=UTC node --test --test-name-pattern='上次回复' src/test/js/mailboxChatBehavior.test.js` → exit 0，tests 1 / suites 0 / pass 1（当前无名称含「上次回复」的用例，仅文件级通过；实施后该命令必须匹配到实际用例，不能只剩文件级）
4. `TZ=America/Los_Angeles node --test --test-name-pattern='上次回复' src/test/js/mailboxChatBehavior.test.js` → exit 0，tests 1 / suites 0 / pass 1（同上）
5. `node --test src/test/js/*.test.js` → exit 0，tests 1298 / suites 255 / pass 1298 / fail 0 / cancelled 0 / skipped 0 / todo 0（日志：`/tmp/fp-mailbox-full.log`）
6. `cmp src/main/resources/static/mailbox-chat.css docs/plans/2026-09-09/mailbox-refinement-evidence/mailbox-chat.target.css` → exit 0
7. `git diff --check` → exit 0；`git status --porcelain` 为空

## 实施前复核（计划 T-3.2）

- `index.html` 当前 11 个版本化资源键均为 `20261002-location-search`（实际位置：CSS 14–18 行、JS 2346–2352 行；与计划审计的 11–15/2345–2350 有行漂移，按内容定位）。
- `grep -F '20261002-location-search' src/test`（内置 grep 工具）→ 无命中：无固定旧键的测试文件需要随 S-4 修改，不需要扩充授权文件清单。

## 基线结论

- 既有 1298 用例全绿，无基线红。实施后 targeted（含新增用例）与全量必须仍为 fail 0；任何新失败均归因于本 child 改动。
- 本 child 无 Kotlin/SQL 改动；按计划不要求 Maven/启动生产/重跑迁移。
