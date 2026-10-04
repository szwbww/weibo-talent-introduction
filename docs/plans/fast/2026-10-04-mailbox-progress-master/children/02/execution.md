# Child 02 Execution Report — 收发件箱三态标记：状态菜单与完整Tab

## Execution Result: READY_FOR_VERIFICATION

Plan（批准版）: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-mailbox-progress-master/docs/plans/2026-10-04/mailbox-progress-02-frontend.md`
Plan SHA-256: `589b96ac9b4e37460dcbb10c36790f55e3afb0764800fcd299dc3545224803da`
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-mailbox-progress-master/docs/plans/2026-10-04/mailbox-progress-02-frontend.md@589b96ac…`
Execution epoch: NEW（本 worktree 首次执行 child 02；plan-bytes 来自本 invocation 磁盘读取）
Approval basis: 用户显式 `/fast-p docs/plans/2026-10-04/mailbox-progress-master.md`（2026-10-04）+ 派发消息授权单个本地实现提交
Executor: `ImplMailboxProgress02`（fast-p child 02 implementer）
Master plan: `docs/plans/2026-10-04/mailbox-progress-master.md`（identity `commit:9594d4b3b3c3022b2be2bc04b24d90ee40a58ad4`）
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-mailbox-progress-master`
Target branch: `fast/2026-10-04-mailbox-progress-master`
Worktree Git dir: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-04-mailbox-progress-master`
Worktree ID: `<root>@fast/2026-10-04-mailbox-progress-master@.git/worktrees/weibo-talent-introduction-fast-2026-10-04-mailbox-progress-master`
child_base_sha: `dbe79c2bfb466bedb2c70d767bdd34082307579c`（child 01 Code head）
Pre-execution HEAD: `68023917ff24c7eee71021f0dd2d0862f5156c32`
Post-execution code SHA: `cc69a8f0649f025bd69e80ecac4c5097fc8343b6`（`feat(fast-p): implement 02`）
Evidence HEAD: N/A（本报告由控制方单独提交，不进实现提交）
Implementation boundary: `dbe79c2..cc69a8f`，仅 6 个授权文件。

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 Tab 与卡片菜单 | IMPLEMENTED | `mailbox-chat.js`, `styles.css` | 6-tab→7-tab 数组断言；卡片菜单三态；`progressActionsHtml`；S-2 块字节断言 |
| T-2 真实保存与失败 | IMPLEMENTED | `mailbox-chat.js` | PUT `…/progress-status`、busy、`refreshListWithFallback` null 短路、写成功/刷新失败分离提示 |
| T-3 挂起归类与文字 | IMPLEMENTED | `mailbox-chat.js` | `afterSuspensionRemoved` 四分支、`completionLineHtml`、旧 followed 兼容 |
| T-4 缓存与测试 | IMPLEMENTED | `index.html`, 3 个 `src/test/js/*.test.js` | 11 键统一；行为/样式/挂起用例新增 |
| I-1～I-6 | IMPLEMENTED | 见「不变量证据」 | 逐条机器断言 |
| S-1～S-4 | IMPLEMENTED | 见「样式契约证据」 | 逐字块字节断言 + class 白名单 + 资源键断言 |

## Commands（fresh，本 invocation 实际运行）

环境：`node v25.x`（本机）；JDK `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`。

| # | Command | Result | exit | 计数 | 日志 |
|---|---|---|---:|---|---|
| C0 | `node --check src/main/resources/static/mailbox-chat.js` | PASS | 0 | — | 终端 |
| C1 | `node --check` 三个测试 JS | PASS | 0 | — | 终端 |
| C2 | `node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxSuspension.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxSuspensionStyle.test.js src/test/js/mailboxSuspensionFollowup.test.js src/test/js/mobileCoreNavigation.test.js` | PASS | 0 | tests **270**, pass **270**, fail 0 | 终端 |
| C3 | `node --test src/test/js/*.test.js` | PASS | 0 | tests **1451**, suites **277**, pass **1451**, fail 0 | 终端 |
| C4 | `JAVA_HOME=…/zulu-11… mvn test` | 与基线一致的唯一既有失败 | 1 | surefire Tests run **4547**, F **0**, E **19**, Skipped 13 | `/tmp/fastp-mbp-02-mvn.log` |

### 与 baseline.md 对照

| 项 | 基线 | 本次 | 结论 |
|---|---|---|---|
| B0 `node --check mailbox-chat.js` | exit 0 | exit 0 | 不变 |
| B4 全量 JS | tests 1434 / pass 1434 / fail 0 | tests 1451 / pass 1451 / fail 0 | +17 新增用例全通过，fail 0 |
| B5 `mvn test` | exit 1，Tests run 4547，F0，E19，Skipped 13 | exit 1，Tests run 4547，F0，E19，Skipped 13 | 逐字一致，无新增失败类 |
| B5 唯一失败类 | `ExpertContactLocationServiceTest` 19× `IllegalStateException: 国家时区目录配置错误：国家 CL 的时区…` | 同 | 不变（并行 meeting-country-timezone 既有环境失败，非本计划引入） |
| B5 node-test 阶段 | surefire 先失败，exec node-test 未执行 | 同（日志无 exec-maven-plugin/node-test 记录） | 不变，JS 全量以 C3 为证 |

新增 17 用例分布：`mailboxChatBehavior.test.js` 净 +8（删 1 星标乐观用例、加 9 三态用例）；`mailboxChatStyle.test.js` +5；`mailboxSuspension.test.js` +4。

## Changed Files（= 授权 6 文件）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/resources/static/mailbox-chat.js` | 新增 `CHIP_PROVIDED`/`PROGRESS_*` 常量与 `PROGRESS_MENU` 表、`progressStatusOf` 等纯函数；FILTER_CHIPS 七项与「跟进中」label；chipParams/conversationsParams 增 `providedOnly`；卡片 `mailbox-progress-card` + 三态状态按钮/菜单（`progressActionsHtml`）；删除星标 `mc-follow`、详情 `mc-toggle-follow`、`toggleFollow`/`renderFollowButtons`/`setFollowControlsDisabled` 与其 onClick 分支；新增菜单开关/键盘/外部点击/卸载解绑、busy 集合、`setProgress` 真实 PUT 与刷新失败分离；挂起 state/view/apply/reset/afterSuspensionRemoved/completionLine 传 `progressStatus`（旧 `followed` 仅缺字段兼容）。 |
| 2 | `src/main/resources/static/styles.css` | 逐字插入 S-2 合同块（`mailbox-progress-contract:start…end`），5 个新 class。 |
| 3 | `src/main/resources/static/index.html` | 11 个版本化资源键统一 `20261004-mailbox-suspension-followup` → `20261004-mailbox-progress`；资源名/顺序/数量不变。 |
| 4 | `src/test/js/mailboxChatBehavior.test.js` | 两处 Tab 数组/中文名/索引更新；stub 增 `progress-status` 端点与挂起 `progressStatus` 响应；DOM 加 focus/activeElement；新增 9 个三态用例；删除旧星标乐观用例。 |
| 5 | `src/test/js/mailboxSuspension.test.js` | stub 增 progress-status 端点与 `progressStatus` 响应；I-4 归类矩阵加 PROVIDED 与 PROVIDED 提示文案；新增 3 个状态↔挂起交互用例。 |
| 6 | `src/test/js/mailboxChatStyle.test.js` | 新增 S-2 逐字块字节断言、5 class 白名单、无旧星标/关注控件、S-1 换行规则与七 Tab 断言。 |

`git diff --stat`：6 files changed, 834 insertions(+), 115 deletions(-)。`git status --porcelain` 提交前仅这 6 项（另加本报告文件，属 `docs/plans/fast/**`，不进入实现提交）。

## 不变量证据（I-1～I-6）

- **I-1（UI 消费唯一真实状态）**：`progressStatusOf` 缺字段从 followed 派生、非法返回 INVALID；`PROGRESS_MENU` 表逐字三态（NONE→跟进中/已提供；FOLLOWING→取消跟进/已提供；PROVIDED→跟进中/取消提供，无「进入」字样）。行为用例「未标记入口六条转换」「字段缺失从 followed 派生；非法显示状态不可用并禁用」断言 label/选项逐字与非法禁用无菜单。
- **I-2（服务端分页与用户上下文）**：`providedOnly=true` 与 `followed=true` 互斥（行为用例断言 query 互斥）；标记后 chip/搜索保留（同一用例）；末页唯一行移出 → 空页回退一页（「状态页最后一条移出」用例）；改卡片 A 不切选中、B 正文不变（「修改卡片 A 不当作选中 A」用例）。
- **I-3（失败与异步保护）**：写入成功前不改显示、busy 禁用重复、失败保留旧状态可重试（「写入成功前不改状态…」用例）；写成功而列表失败显示「状态已保存，列表刷新失败，请重试」且不报写入失败、不回滚（「写成功但列表重查失败」用例）；过期列表 null 短路不回退不误报（「过期列表回包 null」用例）。
- **I-4（挂起返回包含已提供）**：归类矩阵四分支（pending/PROVIDED/FOLLOWING/replied）+ PROVIDED 提示文案用例；状态变更后旧挂起 GET 迟到不覆盖（「状态变更后旧挂起 GET 迟到不覆盖当前状态」用例，断言 completion 行仍为已提供）；缺字段兼容为跟进中（「挂起响应缺 progressStatus」用例）；`setProgress` 递增 `suspension.seq` 并 `loadSuspensionState` 重查，不改 reason/锚点/处理确认。
- **I-5（边界与资源生命周期）**：菜单仅 `mc-progress-menu`/`mc-set-progress` 两个新动作，不调 resolve/send/标签/材料；待匹配不渲染控件（「菜单…详情无控件、待匹配无菜单」用例）；再点/Escape/外部点击关闭、焦点回触发按钮（同用例）；`renderExpertList`/卸载/用户切换关闭菜单；无新增 document 监听（复用既有 `onOutsideFilterClick` 并随卸载解绑）。
- **I-6（原流程与缓存不变）**：仅统一更新 11 个资源键，不新增资源/注册顺序（样式用例断言 11 键同值 + 顺序）；`sessionStore` 未新增状态字段；删除旧 follow UI 后旧 follow 端点不再被前端调用（行为用例断言）。

## 样式契约证据（S-1～S-4）

- **S-1**：`#view-mailbox.mc-refined .mail-chat .mc-filters{flex-wrap:wrap;overflow-x:visible;row-gap:0}` 与 `@media(min-width:761px){…380px…}` 逐字保留；七 `.mc-filter[data-action=mc-filter]` data-chip=`all/provided/followed/pending/suspended/replied/unmatched`，pending/suspended 保留 `.mailbox-suspend-count`。
- **S-2**：`styles.css` 逐字包含计划 S-2 合同块（测试从计划文件提取块并 `includes` 断言，`block in css byte-exact: True`）；新增 5 class（mailbox-progress-card/-progress/-progress-status/-progress-menu/-progress-option）均有字面声明；无 inline style；字节锁定 `mailbox-chat.css` 未改（`mailboxSuspensionStyle` 字节冻结断言仍绿）。
- **S-3**：详情 `mc-toggle-follow` 控件删除，材料/管理/排期按钮保留；挂起提示文案更新，仍复用既有 `.mailbox-suspend-*` DOM/样式，未复制菜单进详情。
- **S-4**：`index.html` 仍 5 CSS+6 JS，11 键同值 `20261004-mailbox-progress`；`src/test` 旧键 `20261004-mailbox-suspension-followup` 精确反查 **0 命中**（实施前已复核），故无固定键测试需改；`mailbox-chat.css` 保持字节冻结。

## Deviations

- **样式块位置（1 处，必要偏差）**：计划 S-2 文字为「下列块追加至 styles.css 末尾」。本实现将 S-2 块插入到字节锁定的 `/* mailbox-suspension-contract:start */` 之前（即 `mailbox-suspension-followup` 规则块之后），而非文件最末端。理由：既有且在本 child 必需命令集内的 `mailboxSuspensionStyle.test.js` 断言 `stylesSource.trimEnd().endsWith("/* mailbox-suspension-contract:end */")`（「contract 块位于文件末尾」），把新块追加到真正末尾会使其转红；该测试文件不在本 child 6 个授权文件内，不得修改。同样先前的 `mailbox-suspension-followup` 规则块（styles.css:12646–12652）即以此方式插入在冻结块之前，属既有仓库约定。计划 S-2 的机器可验证要求为「字节包含」（T-4：「Style 新增 S-2 完整块字节包含断言」），已满足：块与计划逐字字节一致。S-1「不改后面挂起逐字合同块」保持不变。
- 实现层其余：无。未新增白名单外文件；未改 `docs/plans/**`；未改 `pom.xml`/依赖；未联网、未连生产库/ES、未发信；未 push/merge/rebase/amend/reset。

## Unrun Items

- 浏览器几何检查（A-4 的 320/393/760/1024/1440/1920px 真实布局、`filters.scrollWidth<=clientWidth+1`、菜单 `scrollIntoView`）与人工验收 A-1～A-7：属人工验收，未执行，不得宣称通过。
- `mvn test` 尾部 node-test 阶段未执行（surefire 先失败，与基线一致）；JS 全量门禁以 C3 为准。
- 未生成 `mailbox-progress-02-frontend-acceptance.md`（计划规定人工验收开始时才导出）。

## Freshness

- Plan identity rechecked: YES（本 invocation 从磁盘重读 brief + 02 计划 + master 计划；提交前再次核对计划文件 sha256）
- Worktree identity rechecked: YES（`--show-toplevel`/`--abbrev-ref`/`--git-dir`/`--git-common-dir`）
- Reported commits reachable from target branch: YES（`git merge-base --is-ancestor`）
- Required commands run this invocation: YES（C0–C4 全部 fresh）
- Historical evidence used only as baseline: YES（baseline.md 仅作对照）

## Remaining Blocker

- None（实现与必需命令均已完成；唯一偏差已说明且不改变验收语义）。

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`

## Fast-P Archive

本报告随 02 证据提交归档（fast-p 控制方）。实现提交仅含 6 个授权文件：`feat(fast-p): implement 02`。修复轮 1 由 `fix(fast-p): repair 02 round 1` 落地（F-1：`refreshListWithFallback` 双 null 短路 + 2 个覆盖用例），详见 fix-log.md 与 verify-log.md。
