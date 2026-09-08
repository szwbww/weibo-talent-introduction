# Child 11 Execution Report — 资源注册、启用与整体验收门禁

## Execution Result: READY_FOR_VERIFICATION

- Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials/docs/plans/2026-09-07/11-release-and-cache-gate.md
- Plan SHA-256: `53b122a35ffb5db509fd2b55cb21c7106736e2b855ab5bd5971c13a9ff2917fa`（Amendment A2 后版本，2026-09-08）
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials/docs/plans/2026-09-07/11-release-and-cache-gate.md@53b122a35ffb5db509fd2b55cb21c7106736e2b855ab5bd5971c13a9ff2917fa`
- Execution epoch: 2（RESUME after HUMAN-approved Amendment A2，+1 授权文件）
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials` (branch `fast/mailbox-materials`)
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials@fast/mailbox-materials@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-mailbox-materials`
- Pre-execution HEAD (epoch 2): `54262d43345f1760cee270c00858a9a306d98f86` (A2 amendment commit)
- Implementation commit: `29ca7349ac1410934900fc0bbdaf181a88ba71d0` — `feat(fast-p): implement 11`（11 授权文件；当前为 branch HEAD，parent 54262d4）
- Evidence HEAD / Post-execution code SHA: `29ca7349ac1410934900fc0bbdaf181a88ba71d0`

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| I-1/S-1..S-5 index.html 注册 7 资源同键 + 顺序 | IMPLEMENTED | `index.html` | 7 × `v=20260907-material-chat`；old key 0 命中；CSS 序 styles→expert-materials→mailbox-chat；脚本序 workbench→expert-materials→mailbox-chat→app；workbench 原位置保留（task-modal-runtime.js 之后）；无业务 DOM/CSS |
| 7 个 JS 固定键测试同步新键 + 顺序（不放宽） | IMPLEMENTED | 7 个 `src/test/js/*.test.js` | 两拼写均同步；改为 7 资源存在/恰好 7 键/全同值/CSS+脚本注册顺序/旧键 0 命中断言；`node --test` 730/730 |
| I-2 MailAttachmentStorageProperties.metadataOnly 默认 true（显式 false 保留） | IMPLEMENTED | `MailAttachmentStorageProperties.kt` | 默认 `true`；显式 false 紧急回退保留；docker-free 全绿（见 A2） |
| I-2 docker-free legacy 表示与默认值解耦（A2） | IMPLEMENTED | `src/test/kotlin/.../ImapMailReceiveServiceTest.kt` | :28 无参构造 → `ImapMailReceiveService(MailAttachmentStorageProperties(metadataOnly = false))`，仅此一处；13/13 PASS |
| I-3 运行手册（发布 + 回退） | IMPLEMENTED | `docs/runbooks/mailbox-material-chat-rollout.md` | 发布 2.1–2.9（HEAD/Flyway V121 记录、测试库备份迁移、兼容后端、dryRun 审计、19+20 与 1000 附件压测、开启 metadataOnly、版本化前端、两入口/检查/分析/发送全链、真实版本+FETCH+截图）；回退 3.0–3.5（停新提交→等 worker 空闲/关连接→metadataOnly=false 仅旧收信并先决策暂停检查→移除新资源注册→保留 nullable/readiness 兼容代码→不 DROP 新表/不删文件→队列继续/暂停由运维显式设置） |
| 完整 mvn test 门禁 | PASS（epoch 2） | — | 见 Commands |

## Commands（全部在 worktree 根目录、epoch 2 本次执行新跑）

| Command | Result | Evidence |
|---|---|---|
| `node --test src/test/js/*.test.js` | PASS (exit 0) | 730 tests / 136 suites / pass 730 / fail 0 / skipped 0 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS (exit 0, BUILD SUCCESS) | fresh surefire（`find target/surefire-reports -newermt` 运行窗口内 236 类）：**Tests run 3218, Failures 0, Errors 0, Skipped 9**；exec-plugin Node 阶段（node --test + node-check ×5）通过 |
| `node --check src/main/resources/static/app.js && node --check src/main/resources/static/expert-materials.js && node --check src/main/resources/static/mailbox-chat.js` | PASS (exit 0) | 三个生产 JS 语法均 OK |
| epoch 1 对照（存档）：翻转后、A2 前 `mvn test` | FAIL（记录在案） | 3218 run / 1 failure：ImapMailReceiveServiceTest legacy mode :78 expected: not <null>；因果对照 HEAD 默认 13/13 PASS → 翻转 13/1 FAIL |

## Changed Files（11 授权文件；commit 29ca734）

- `src/main/resources/static/index.html` — 注册 expert-materials.css/mailbox-chat.css（styles.css 后）与 expert-materials.js/mailbox-chat.js（workbench→app 之间）；7 键 `?v=20260907-material-chat`。
- `src/main/kotlin/com/weibo/talentintroduction/config/MailAttachmentStorageProperties.kt` — `metadataOnly: Boolean = true`。
- `src/test/js/batchSendTaskConsoleVisualFix.test.js` / `checkRepliesRelocation.test.js` / `manualReplySubjectPrefill.test.js` / `overlayAndDialogContrast.test.js` / `ragKnowledgeBasePage.test.js` / `ragWorkbenchRender.test.js` / `trustReplyWorkbenchSharedMount.test.js` — 新键同步 + 7 资源/顺序/旧键 0 命中断言（两拼写）。
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveServiceTest.kt` — A2：legacy fixture 显式 `metadataOnly = false`（1 行）。
- `docs/runbooks/mailbox-material-chat-rollout.md`（新增）— 发布/回退手册。

## Invariant Checks

- I-1：`grep -o 'v=[0-9a-z-]*' index.html | sort | uniq -c` → `7 v=20260907-material-chat`（唯一值）；`grep -c '20260903-bounce-warning' index.html` → `0`；index.html:11-13 CSS 序、2109-2112 脚本序。
- I-2：默认翻转已实施；显式 `metadataOnly=false` 构造/配置仍有效（A2 测试证明 legacy 语义保留）；docker-free 全绿。
- I-3/S-1..S-5：注册仅 4 行 link/script；无新 DOM、无 inline style、未改既有 style/link 作用域；08/10 交付 CSS/JS 逐字未动；迁移无新增（无 DDL 改动，FlywayMigrationIntegrationTest/mysqlIt IT 不受本子计划影响）。

## Deviations

- 按 HUMAN 批准的 Amendment A2（plan 变更文件清单 +1：ImapMailReceiveServiceTest.kt，1 行显式构造，无断言语义/生产改动）。其余无；未 push/merge/amend/rebase；fast-p 证据目录（children/11、ledger.md）由 controller 管理，未纳入实现提交。

## Freshness

- Plan identity rechecked: YES（epoch 2 版本 sha256 `53b122…`，执行前/后一致）
- Worktree identity rechecked: YES（含 commit 前后 --expect-root/branch/git-dir 检查）
- Reported commit reachable from target branch: YES（29ca734 = branch HEAD，parent 54262d4）
- Required commands run this invocation: YES（3 条全部 epoch 2 新跑；epoch 1 记录仅存档对照）
- Historical evidence used only as baseline: YES

## Remaining Blocker

- None。

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`
