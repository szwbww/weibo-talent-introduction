# P2 Execution Record — p2-head-layout-c

- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-detail-head/docs/plans/2026-08-14/expert-detail-head-p2-head-layout-c.md@38ad07791572938e39fbcffe87f43d72db1b451afa7a665870ad1049deb85840`
- Epoch: NEW
- Executor: P2Implementer (subagent)
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-detail-head`
- Target branch: `fast/expert-detail-head`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-detail-head@fast/expert-detail-head@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/expert-detail-head`
- Pre-execution code SHA (branch HEAD before this run): `209f184e8f826060660fd22f187465fb0ff427a9`（P1 产品代码停在 `111180741ec46bea796e81a60e513769d2de534c`，其后仅 3 个 docs 提交：c51b2d6 / 9839ed0 / 209f184）
- Implementation commit: `708dd28bb394204605826c13be883c51465d27e5` `feat(fast-p): implement p2-head-layout-c`（4 files，678 insertions / 59 deletions）
- Post-execution code SHA: `708dd28bb394204605826c13be883c51465d27e5`（Evidence HEAD = Implementation HEAD，同一提交）
- 本记录不提交（append-only, excluded from commit）。

## 变更明细（T1-T11）

### T1 — state 折叠态（I-4）
`app.js` `state` 字面量：`mailSendOptions: [],` 之后插入 `contactHeadExpanded: false,`。命中数：`contactHeadExpanded` 全文件 6 处（state 声明 / loadContactDetail 渲染 2 处 / toggle 分支 2 处 / 测试内断言外）。

### T2 — 重写 `#contactHeadActions` 模板（S-8）+ 上移 select 填充块
- 旧「状态与层级行 + contact-head-mail-row 行」整体替换为 S-8 骨架：`contact-head-main-row`（账号 pill `senderBindingToggle` + 浮层 `senderBindingPop`/`senderBindingSelect`(data-original=contact.boundSenderAccountCode||"") + dirty note `senderBindingDirtyNote` + divider + `manualMailOption` + `sendManualMailBtn` + divider + `contactHeadMoreToggle`）+ 折叠行 `contactHeadMoreRow`（原三个 select 的 option 生成逐字保留，仅 label 改为「状态」）。
- `EXPANDED` 读 `state.contactHeadExpanded === true`。
- select 填充块（`state.accounts` 兜底 + `SIMULATOR_NOOP` 过滤逐字保留）上移到 `innerHTML` 写入之后、`const banner` 之前；原位置（函数尾）的旧填充块删除。
- 填充完成后立即调用 `updateSenderBindingDirtyState();`。
- 删除 Sender Binding Card（app.js 原 :7061-7076，含 `<!-- Sender Binding Card -->` 与「已变更」badge/清除标记按钮，后二者迁入浮层 footer 的 S-8 骨架）。

### T3 — 删除 metadata 卡片 + 死 CSS
- `app.js`：「Sender Binding Card」整块删除。
- `styles.css:1556-1567` 两条 `.metadata-card-value .sender-binding-editor` 死规则删除。
- grep 验证：`sender-binding-editor` 在 app.js 与 styles.css 中均为 0 命中。

### T4 — 未保存闸门（I-2 / I-3）
- 新增 `updateSenderBindingDirtyState()`（置于 `updateSaveButtonState` 之前）：`dirty = select.value !== (select.dataset.original || "")`，一个函数同时驱动 note.hidden / sendBtn.disabled / pill.dataset.dirty。
- change 委托内追加 `if (select.id === "senderBindingSelect") { updateSenderBindingDirtyState(); }`；既有三 id 分支未动。
- 未引入 `selectedIndex`（全文件 0 命中）。

### T5 — 账号浮层开合（S-2/S-3）
- bindEvents 内、`#contactHeadActions` click 委托之后追加 pill/浮层 click、document click、document Escape 三个监听器，逐字对齐计划 T5 代码（范式 app.js:11405-11424）。与既有 `button[data-action]` 委托为独立 listener。

### T6 — 折叠开关（I-4）
- `handleContactAction` 内、`select-expert` 分支之前插入 `toggle-contact-head-more` 分支：翻转 `state.contactHeadExpanded`，同步 `#contactHeadMoreRow.hidden` 与 `#contactHeadMoreToggle` 的 aria-expanded。

### T7 — `renderExpertTagEditor` inline 形态（I-5 / I-6，S-6 / S-7）
- 签名加第 6 参 `layout = "section"`（参数表内无 `)`，expertProfileAbsence.test.js 的 `extractFn` 正则不受影响）。
- 函数体最前加 `if (layout === "inline") { ... }` 早退分支：profileMissing=true → `expert-tag-editor is-inline` + `expert-tag-nodoc` pill「ES 无画像」（无 data-action）；否则 chips（第 4 项起带 ` hidden`，空列表为「暂无标签」）+ `expert-tag-add-btn`「＋」+ tags>3 时 `expert-tag-more-btn`「+N」。根元素带 `data-layout="inline"`。
- `if` 之后的既有代码一字未改；非 inline 输出与 S1/S2_EXPECTED 逐字相等（4 个新用例 + 既有 11 用例全绿）。

### T8 — 重渲染保持形态（I-7）
- `updateExpertTagEditor` 改为先从 `editor.dataset.layout` 读回（`"inline" ? "inline" : "section"`）再传给 `renderExpertTagEditor`（profileMissing=false）。

### T9 — 标签「+N」展开
- `handleContactAction` 内、`expert-add-tag-open` 之前插入 `expert-tags-expand` 分支：`editor.dataset.expanded = "true"`，把 `[hidden]` chips 全部显示，移除 +N 按钮。

### T10 — 两处姓名行（S-9，I-10）
- `loadContactDetail` 与 `showExpertDetail` 的 `.expert-profile-header` 内、`.expert-header-info` 之后追加 `renderExpertTagEditor(..., "inline")` 调用（showExpertDetail 保留 `expert.orcidId ?` 守卫）；姓名行与子标签行之间的独立 `${renderExpertTagEditor(...)}` 行删除。
- `renderDetailSubTabs` 与四个 detail-tab-panel 一字未改；`data-panel="mail-preview"` 仍 2 处且与 `data-panel="template"` 总数相等（用例 7 断言）。

### T11 — 测试
- `src/test/js/expertProfileAbsence.test.js`：追加 `describe("expertProfileAbsence: inline layout (P2 S-7)")` 4 个用例（S-7 骨架正反两态 / I-6 三要素 / I-5 负向断言），CSS 存在性断言（`.expert-tag-editor.is-inline`、`.expert-tag-nodoc`、`.expert-tag-add-btn`）折入用例 1（保持 11+4=15）。既有 11 用例与 S1/S2_EXPECTED 一字未改；仅 `createRendererSandbox` 增加一行抽取 `updateSenderBindingDirtyState`（P2 后 loadContactDetail 调用它，属实现必需，非用例改动）。
- `src/test/js/contactHeadLayout.test.js`（新建，9 用例）：S-8 源码断言（7+1 个 id、无 contact-head-mail-row / sender-binding-editor、新模板区无 `style="`、data-original 来源）/ I-2 三处联动正反两态 / I-3 未绑定判脏 / I-1 send-manual-mail body null + 分支不含 senderBindingSelect / I-4 toggle 翻转 + loadContactDetail 读 state.contactHeadExpanded + 命中数 ≥3 / I-7 layout 透传（inline 与 section 回退）/ I-10 data-panel 计数 / S-1..S-6 全部 class + I-8 恰好 1 处 min-height:0 / DOM-stub 陷阱防护（index.html 容器 + loadContactDetail 生成点 + 初始化调用）。
- `src/test/js/senderBindingDisplay.test.js` 未改（git diff 为空）。

## 样式契约

- 全部新增 CSS 追加在 `styles.css` 末尾 `/* === 专家详情头部 C 布局 === */` 段（+218 行），S-1..S-6 六个规则块与计划代码块逐字一致（脚本比对 6/6 True）；`git diff` 中 `.dropdown`、`.dropdown-menu`、`.expert-tag*`、`.inbound-tag-editor-chips`、`.mail-expert-overview .expert-tag-editor`、`.contact-head-status-row`、`.contact-head-mail-row` 既有规则块零改动（styles.css 仅 2 个 hunk：1553 删除 + 9285 追加）。
- **S-4 追加说明**：`styles.css` 原本无 `.button[disabled]` 规则，故按契约在本段追加 `.contact-head-actions .button[disabled] { opacity: 0.5; cursor: not-allowed; }`（已在 PR/本记录注明）。
- app.js 未新增任何 `style="`（改动前后命中集不变：182 = 182）。

## 验证命令（全部实跑，2026-08-14）

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | `node --test src/test/js/contactHeadLayout.test.js` | 0 | `# tests 9 # pass 9 # fail 0` |
| 2 | `node --test src/test/js/expertProfileAbsence.test.js` | 0 | `# tests 15 # pass 15 # fail 0`（11 既有 + 4 新增） |
| 3 | `node --test src/test/js/contactHeadLayout.test.js src/test/js/expertProfileAbsence.test.js src/test/js/senderBindingDisplay.test.js src/test/js/expertMailPreviewTab.test.js` | 0 | `# tests 43 # pass 43 # fail 0`（15+6+13+9；expertMailPreviewTab 13 含 P1 +3） |
| 4 | `node --check src/main/resources/static/app.js` | 0 | 无输出 |
| 5 | `node --test src/test/js/*.test.js` | 0 | `# tests 537 # pass 537 # fail 0` |
| 6 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | 0 | `BUILD SUCCESS`；node --test 执行记录出现在构建输出中（537/537/0）；Surefire 汇总 `Tests run: 2421, Failures: 0, Errors: 0, Skipped: 4`（4 个 skip 为既有） |
| 7 | `git diff --check` | 0 | 无输出 |

## 验收 grep（全部通过）

| 检查 | 期望 | 实得 |
|---|---|---|
| `grep -c 'contact-head-mail-row' app.js` | 0 | 0 |
| `grep -c 'sender-binding-editor' app.js styles.css` | 0/0 | 0/0 |
| `grep -c 'class="expert-profile-header"' app.js` | 2 | 2 |
| `grep -c 'is-inline.tag-editor-loading' styles.css` | 1 | 1（规则体 `min-height: 0;`） |
| `未绑定` 在 loadContactDetail 模板内 | 命中 | app.js:7009（pill label `${...|| "未绑定"}`） |
| `grep -c 'is-unbound' styles.css` | ≥1 | 1 |
| `selectedIndex` in app.js | 0 | 0 |
| `contactHeadExpanded` in app.js | ≥3 | 6 |
| `send-manual-mail` 分支 `senderAccountCode: null` | 未被 diff 触碰 | diff 中无该行 |
| app.js `style="` 命中集 | 与改动前相同 | 182 = 182（HEAD 对照） |

## 未改区（must-NOT-change / zero-diff）

- `renderMailboxExpertTagEditor` 及其调用（app.js:9031 / 9600 附近）零 diff。
- `updateSaveButtonState`（原 :8799-8824）函数体零 diff（仅其前方插入新函数）。
- 列表项标签 chips（原 :4734 / :4744）零 diff。
- `index.html`（`#contactHeadActions` 容器 :676）零 diff。
- 四个子标签面板（`data-panel` 计数 2=2，用例断言）。
- 收发件箱标签区输出（M-3）：默认 section 输出与 S1/S2_EXPECTED 逐字相等（既有 11 用例全绿）。
- 手动发送请求体仍为 `{optionType, optionValue, senderAccountCode: null}`（I-1，用例 4 + diff 双重验证）。

## Deviations

- 无功能性偏差。唯一说明性偏差：计划 T11 用例 1 / S-8 验收中「loadContactDetail 源码不含 `style="`」与仓库现状（metadata 网格 5 处既有 inline style）矛盾，按计划「全局」验收（`style="` 命中集与改动前完全相同）执行：新 S-8 模板区断言无 `style="`（contactHeadLayout 用例 1 对该区域切片断言），全局命中集 182=182。S-4 的 `.contact-head-actions .button[disabled]` 因 styles.css 无 `.button[disabled]` 而按契约追加。

## Freshness

- Plan identity rechecked: YES（sha256 38ad0779… 不变）
- Worktree identity rechecked: YES（HEAD 由 209f184 → 708dd28，root@branch@git-dir 不变）
- Implementation commit reachable from target branch: YES（708dd28 即分支 HEAD）
- Required commands run this invocation: YES（7/7 全绿）
- Historical evidence used only as baseline: YES（P1 基线仅作对照）

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`（注意 A2 修正：判据以实跑输出为准）。
