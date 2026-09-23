---
id: K-frontend-cache-key-triad
domain: frontend
created: 2026-09-22
last_used: 2026-09-23
hit_count: 29
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

2026-09-16复核：当前键已为`20260914-followup-email`，仍命中上述9份测试。
`meetingConfirmationAssets.test.js`和`trustReplyWorkbenchSharedMount.test.js`还精确断言资源总数为9；新增script/link时须同步数量、名字与顺序断言，不能只换版本字符串。本次会议日历计划复用app.js，不新增资源。

2026-09-20 复核：`index.html` 已扩为 5 个 CSS + 6 个 JS，共 11 个版本化资源；大多数测试改为从 `styles.css?v=` 派生键。当前工作树因 SharePoint 文件卡 WIP 暂时存在 20260918/20260919 两组键，不能把这个分裂状态当发布契约。执行静态资源计划时仍须先读当前 diff，再统一全部 11 项；当前唯一额外写死键的是 `sharepointFileCardDisplay.test.js`，应改为派生而不是继续扩散字面量。

2026-09-22较早快照：11项键统一为`20260920-manual-material-upload`；按该精确字符串搜索`src/test`为0命中。sharepointFileCardDisplay.test.js现已从styles.css派生CACHE_KEY，历史“唯一固定字面量测试”已解决。不要依据旧快照扩大测试文件修改清单；继续按当前键反查。

2026-09-22本轮详细计划复核：工作区11项键已统一为`20260922-task-activity-center`，精确反查命中`src/test/js/taskActivityCenter.test.js:11`的固定CACHE_KEY。上述较早“0命中”不再适用；新静态资源计划必须包含该测试，优先改为从styles.css引用派生键。仍须实施前重查，不能将WIP快照当永久清单。

2026-09-23 回复片段计划快照：index.html:11–15/2195–2200 的 11 项键为 `20260923-discovery-traffic`；`rg -n -F '20260923-discovery-traffic' src/test` 无命中（exit=1）。当前不需要依据旧记录修改 taskActivityCenter 固定值。执行前仍须重查。

同轮收尾复核：并行任务提交 `13fb91e` 后键为 `20260923-discovery-traffic-v2`，精确反查 src/test 仍无命中（exit=1）。保留较早快照用于说明并行工作会改变缓存键，执行时不能照抄计划时旧值。
