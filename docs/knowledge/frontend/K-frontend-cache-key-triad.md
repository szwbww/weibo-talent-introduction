---
id: K-frontend-cache-key-triad
domain: frontend
created: 2026-09-14
last_used: 2026-09-14
hit_count: 19
source: create-p:v6-topnav-glass-navy-restyle
severity: P1
---

经验：`index.html` 中 `styles.css?v=`、`trust-reply-workbench.js?v=`、`app.js?v=` 三个缓存键**必须同值、同时 bump**。

- `src/test/js/trustReplyWorkbenchSharedMount.test.js` 既检查三键一致，也包含固定CACHE_KEY；不可将它排除在固定值更新名单外。
- **固化该字符串的测试文件数量不是常量，必须按当前键反查。** 2026-09-03 对键
  `20260902-legacy-retire` 实测为 7 个：
  - `src/test/js/batchSendTaskConsoleVisualFix.test.js:49-51` — `assert.ok(html.includes('<res>?v=<literal>'))`
  - `src/test/js/checkRepliesRelocation.test.js:11` — `const CACHE_KEY = "<literal>";`
  - `src/test/js/manualReplySubjectPrefill.test.js:13` — 同上
  - `src/test/js/overlayAndDialogContrast.test.js:22` — 同上
  - `src/test/js/ragKnowledgeBasePage.test.js:333-339` — 测试名、三项 includes、提取值均固定 literal
  - `src/test/js/ragWorkbenchRender.test.js:20` — `const CACHE_KEY = "<literal>";`
  - `src/test/js/trustReplyWorkbenchSharedMount.test.js:5/22/175` — 注释、常量、测试名均固定 literal

### 本条的教训：不要按拼写 grep，要按当前键值反查

本条目曾记为"4 个文件"，又据 `grep 'styles.css?v='` "订正"为"只剩 1 个"
——**那次订正是错的**，因为 `const CACHE_KEY = "..."` 这种写法里根本不出现 `styles.css?v=`。
2026-09-03 又增长到 7 个。结论：禁止在知识里把文件数量当永久事实；每次按当前键反查。

唯一可靠的做法是从 `index.html` 取出当前键值，再全仓反查该值：

```bash
KEY=$(grep -o 'v=[0-9a-z-]*' src/main/resources/static/index.html | sort -u | head -1 | cut -d= -f2)
grep -rln "$KEY" src/test/
```

bump 前后各跑一次：bump 前的结果就是必须同步的文件全集；bump 后该命令对**新键**的结果应与之相同。

只 bump 部分键 → 构建期 node 测试直接失败（2026-08-13 发布 eda4853 时实测踩坑,WAR 构建中止）。任何涉及静态资源变更的计划，必须先对当前键做全仓精确 grep，并把 `index.html` 三项引用及全部固定值测试文件列入变更文件清单。

2026-09-08复核当前键20260903-bounce-warning仍命中上述7文件。数字是本次快照，不作为永久常量。

2026-09-14 当前生产键 `20260910-meeting-generic-template` 已扩展到 9 个版本化资源，并被 9 个测试文件固定：

- `batchSendTaskConsoleVisualFix.test.js`
- `checkRepliesRelocation.test.js`
- `mailboxChatStyle.test.js`
- `manualReplySubjectPrefill.test.js`
- `meetingConfirmationAssets.test.js`
- `overlayAndDialogContrast.test.js`
- `ragKnowledgeBasePage.test.js`
- `ragWorkbenchRender.test.js`
- `trustReplyWorkbenchSharedMount.test.js`

当前发布契约是 4 个 CSS 与 5 个 JS 同键；`task-modal-runtime.js` 不带键。数量仍只是快照，每次变更继续按 `index.html` 的当前键反查。
