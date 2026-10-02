# Child 01 — Verification Log

（由 light verifier 按 references/light-verifier.md 的四门禁报告格式追加；append-only。）

## Light Verification: LIGHT_FAIL
Child: 01 (docs/plans/2026-10-02/mailbox-last-reply-time.md)
Boundary: 5d1789f90716a27e265b63340a9aef562a0035d9..135558e762ef8f0f3bbfdba38a23cfce2eb56810
Verifier: VerifyMailboxReply01

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-status 5d1789f..135558e` = 6 个控制方 docs（docs/plans/fast/2026-10-02-mailbox-last-reply-time/**：ledger/brief/baseline/execution/fix-log/verify-log）+ 恰为 5 个授权文件；排除 docs/plans/ 与 5 个授权路径后无剩余项（grep -v → exit 1）；`\.(kt\|sql\|xml\|yml)$` → exit 1。numstat：index.html 11+/11-（仅版本值）、mailbox-chat.js 146+/5-、styles.css 10+/0-、mailboxChatBehavior.test.js 332+/0-、mailboxChatStyle.test.js 79+/0-。`mailbox-chat.css` 与 `docs/plans/2026-09-09/mailbox-refinement-evidence/mailbox-chat.target.css` 均不在 diff 内且 cmp exit 0；`src/` 内旧键 `20261002-location-search` 命中 0（干净切换）。工作树仅 `children/01/execution.md` 未提交（按 brief 留在工作树）。 |
| Plan and invariants | FAIL | FAIL 仅因 F-1（I-3 异常分支）。其余逐项：I-1 `mailbox-chat.js:302-320` 取值只来自 `inbound.receivedAt`，无 latestMessage/firstReplyAt/timeline；I-2 `:262` 显式 `+08:00`、`:266-276` Intl zh-CN/Asia-Shanghai/h23 同组 parts、`:285-287` 分量对照使 2-30 不进位且秒只校验不显示、`:289` 日期与星期同源、`:326` 文本与属性值均 escapeText，`datePart/timePart/contactZoneParts` 未改；I-4 `:1943-1951` 四项守卫 + `:1498-1504` 位于 disposed/listSeq 检查与 renderList 之后、仅非待匹配分支 + `:1964-1967` 建槽后优先当前行、回退 selectedSummary，未改 `selectedSummary`/草稿/锚点、未调 selectExpert/renderConversationContent/renderTimeline；I-5 diff 内无 fetch/setInterval/localStorage/sessionStorage（grep 无命中），新增 CSS 全部 `.mail-chat .mailbox-reply-*` 作用域；S-1/S-2 结构 `:1390`（latestLine 后、mc-person-meta 前）与 `:1964`（h2→p→槽→calendar-summary）+ `:1383` aria 后缀；S-3 `styles.css` 尾部与计划 S-3 fenced 块逐字一致（906B，sha256 dd7e184a256462628564a959ea9f1c390c4b2e523fab689d51d6af7659731024，前缀 299282B 未变）；S-4 index.html 11 个 `?v=` 同值 `20261002-mailbox-last-reply`、注册顺序不变、task-modal-runtime.js 无版本键。 |
| Required commands | PASS | fresh @135558e（node v25.7.0）：`node --check src/main/resources/static/mailbox-chat.js` exit 0；targeted 两文件 exit 0 tests 151/suites 23/pass 151/fail 0（基线 134/134）；`TZ=UTC node --test --test-name-pattern='上次回复'` exit 0 tests 10/suites 1/pass 10/fail 0（基线仅 1 个文件级、0 suites；本次为真实命名用例）；`TZ=America/Los_Angeles …` exit 0 同 10/10；`node --test src/test/js/*.test.js` exit 0 tests 1315/suites 257/pass 1315/fail 0/skipped 0（基线 1298/1298）；`cmp` exit 0；`git diff --check` exit 0。与 execution.md 计数逐条一致，无新增失败。 |
| Downstream interfaces | PASS | N/A（单子计划 run，无下游 child）：diff 无 .kt/.sql/DTO/迁移/接口变更，仅 5 个静态/测试文件，无跨 child 契约。 |

### AUTO_FIX
- F-1：计划 I-3（plan:55「`latestInbound === null && receivedCount === 0` 才显示“尚未回复”…字段缺失/结构异常同样显示后者」，plan:56 明示异常分支是本需求明确规定的防御行为）要求字段缺失/结构异常一律“回复时间暂不可用”。实现 `mailbox-chat.js:305-311` 把 `undefined`/数组/非对象与 `null` 合并为同一分支，只要 `receivedCount===0` 即返回 kind `none`（尚未回复），与自身注释 `:300-301`（none = 仅 `latestInbound===null && receivedCount===0`）矛盾。复现（既有 harness 副本 + 符号链接驱动未改动的生产 `mailbox-chat.js`，仅 /tmp 临时文件，无仓库写入）：`latestInbound:"oops"` + `receivedCount:0` → 列表 `上次回复尚未回复`、time 元素 0；删除 `latestInbound` 键 + `receivedCount:0` → 同上；`null+0` → 尚未回复（正确）。最小授权修正：把 count 条件收窄到精确 `null`（仅 `inbound === null` 时按 `receivedCount===0` 走 none，否则 unavailable），`undefined`/数组/非对象一律 unavailable，`receivedCount>0` 行为不变；现有用例无 anomaly+count0 组合（B-3 的“结构异常”用 expertA 默认 `receivedCount=2`），修正后测试仍全绿；可选在授权测试文件 B-3 补一行 count-0 异常断言。可达性说明：仓库无 Jackson NON_NULL 配置，summary 恒带显式 `"latestInbound": null`，且 `instance.list.items`/`selectedSummary` 只来自接口行（`:1486`/`:1723`/`:1816`/`:4226`），该组合线上不可达、无实际影响；仍按计划字面契约记为门禁违规。

### RECORD_ONLY
- O-1：范围级 `git diff --check 5d1789f..135558e` exit 2，仅报控制方自建 docs 存根 `execution.md:4` / `fix-log.md:4` / `verify-log.md:4` 的 “new blank line at EOF”；产品/测试文件干净，brief 要求的命令是工作树级 `git diff --check`（exit 0）。属控制方文档、非本 child 授权文件，无产品影响。

### Required Action
- AUTO_FIX

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 01 (docs/plans/2026-10-02/mailbox-last-reply-time.md)
Boundary: 5d1789f90716a27e265b63340a9aef562a0035d9..bb0b9f1be4abd1fbd7b63aacaec333d4ec3e6019
Verifier: ReVerifyMailboxReply01

### Four Gates
|Gate|Result|Evidence|
|---|---|---|
|Authorized scope|PASS|`git diff --name-status 5d1789f..bb0b9f1` = 6 控制方 docs（children/01/{baseline,brief,execution,fix-log,verify-log}.md + ledger.md）+ 恰为 5 个授权文件；排除 docs/plans/ 与 5 个授权路径后无剩余项（grep -v → exit 1）；`\.(kt\|sql\|xml\|yml\|yaml)$` → exit 1。numstat：index.html 11+/11-（仅版本值）、mailbox-chat.js 151+/5-、styles.css 10+/0-、mailboxChatBehavior.test.js 358+/0-、mailboxChatStyle.test.js 79+/0-。修复提交 bb0b9f1 只动 mailbox-chat.js(8/3)+mailboxChatBehavior.test.js(26/0)。`mailbox-chat.css` 不在 diff 内且 `cmp mailbox-chat.css docs/plans/2026-09-09/mailbox-refinement-evidence/mailbox-chat.target.css` exit 0；`git grep 20261002-location-search -- src/` exit 1（旧键零残留），index.html 新键命中 11。工作树仅 children/01/{execution,fix-log,verify-log}.md 三个控制方文档未提交，无 untracked。|
|Plan and invariants|PASS|F-1 已闭合：`mailbox-chat.js:304-317` 严格以 `inbound === null` 作 none/unavailable 计数分支（`null+0`→none；`null+count>0`→unavailable；`undefined`/数组/非对象在任何 receivedCount 下→unavailable），注释 :300-303 与实现一致。I-1 :302-318 取值只来自 `inbound.receivedAt`（diff 内无 latestMessage/firstReplyAt/timeline/createdAt/resolvedAt grep exit 1）；I-2 :262 显式 `+08:00`、:266-276 Intl zh-CN/Asia-Shanghai/h23 同组 parts、:285-287 分量对照使 2-30 不进位、:289 日期与星期同源、:326 文本与属性值均 escapeText，`datePart/timePart/contactZoneParts` 未改；I-4 :1948-1956 四项守卫（非待匹配/selectedContactId 非空/contactId 相同/槽存在）、:1504-1508 位于 disposed/listSeq 检查与 renderList 之后且仅非待匹配分支、:1972-1973 建槽后优先当前行再回退 selectedSummary，diff 新增行无 selectedSummary 赋值/selectExpert/renderConversationContent/renderTimeline；I-5 diff 内无 fetch( / setInterval / setTimeout / localStorage / sessionStorage（grep exit 1），新增 CSS 全部 `.mail-chat .mailbox-reply-*` 作用域；S-1/S-2/S-3/S-4 由 mailboxChatStyle.test.js 逐字/结构/缓存键断言覆盖。|
|Required commands|PASS|fresh @bb0b9f1（node v25.7.0）：`node --check src/main/resources/static/mailbox-chat.js` exit 0；`node --test mailboxChatBehavior.test.js mailboxChatStyle.test.js` exit 0 tests 152/suites 23/pass 152/fail 0/skipped 0（基线 134/134）；`TZ=UTC node --test --test-name-pattern='上次回复' mailboxChatBehavior.test.js` exit 0 tests 11/suites 1/pass 11/fail 0（11 条真实命名用例执行，非仅文件级）；`TZ=America/Los_Angeles …` exit 0 tests 11/pass 11（同上）；`node --test src/test/js/*.test.js` exit 0 tests 1316/suites 257/pass 1316/fail 0/cancelled 0/skipped 0/todo 0（基线 1298/1298）；`cmp` exit 0；`git diff --check` exit 0。计数与 fix-log.md 逐条一致，无新增失败。|
|Downstream interfaces|PASS|N/A（单子计划 run，无下游 child）：diff 无 .kt/.sql/.xml/.yml、DTO、迁移或接口变更（kt/sql/xml/yml grep exit 1），仅 5 个静态/测试文件，无跨 child 契约。|

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1（沿用上一轮，未变）：范围级 `git diff --check 5d1789f..bb0b9f1` exit 2，仅报控制方自建 docs 存根 `execution.md:4`/`fix-log.md:4`/`verify-log.md:4` 的 "new blank line at EOF"；产品/测试文件干净，brief 要求的门禁是工作树级 `git diff --check`（exit 0）。属控制方文档、非授权文件，无产品影响，不构成四门禁违规。

### Required Action
- COMPLETE_CHILD

