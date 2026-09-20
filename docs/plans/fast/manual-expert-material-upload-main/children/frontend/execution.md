# Fast-P 子计划执行报告 — frontend（手动上传材料）

## Execution Result: READY_FOR_VERIFICATION

- Plan（权威子计划）: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-expert-material-upload-main/docs/plans/2026-09-20/manual-expert-material-upload-frontend.md`
- Plan SHA-256: `0269ec2a3b4e072502d95e4ed64d0096aacbafed17bf739ac653534bc6b6169e`
- Child brief SHA-256: `01b0213d214264b1dd4016dc63b42cb4c1be84a83925de0774399e2efc833590`
- Execution ID: `docs/plans/2026-09-20/manual-expert-material-upload-frontend.md@0269ec2a…b6169e`
- Execution epoch: NEW（本次调用首次执行该子计划；执行前后重算 plan/brief 哈希未变）
- Approval basis: 本次调用指令 + 已批准的主计划/子计划（backend 子计划已 LIGHT_PASS_WITH_NOTES，`c591668`）
- Executor: FrontendImplementer
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-expert-material-upload-main`
- Target branch: `fast/manual-expert-material-upload-main`
- Worktree ID: `…/weibo-talent-introduction-fast-manual-expert-material-upload-main@fast/manual-expert-material-upload-main@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-manual-expert-material-upload-main`
- Pre-execution code SHA: `c591668cb0b833aea1a73975a19019339940910f`
- Post-execution code SHA: `5f4967b8d5f94663266c095e6a2e9ec69f570505`（= HEAD = 目标分支 tip）
- Evidence HEAD: N/A（本子计划不要求证据提交；execution.md 按 brief 要求只写不提交）
- Implementation boundary: `c591668..5f4967b`（6 个授权文件，1447 insertions / 32 deletions）

## 任务状态

| 要求 | 状态 | 文件 | 证据 |
|---|---|---|---|
| 阶段 1：inline/drawer header 动作组 + `manual-upload` 入口；selectionOnly 无入口（I-6/S-1） | IMPLEMENTED | `expert-materials.js` | `buildPanel` S-1 DOM；shared「手动上传入口」suite（含 AI 按钮仍冒泡、重复点击不建第二个 dialog、unmount 释放）；style suite「入口与 dialog 渲染契约」 |
| 阶段 1：`openManualUploadDialog` + S-2 完整 DOM、多选、清空 input.value、超限 blocked、移除白名单 | IMPLEMENTED | `expert-materials.js` | style suite 逐字断言 dialog 结构/文案/`multiple`/状态白名单/无 id、无 inline style |
| 阶段 2：串行队列、一请求一文件、`headers:{}`（I-1） | IMPLEMENTED | `expert-materials.js` | shared「串行队列」suite：`maxConcurrentUploads===1`、`partNames===["file"]`、`headers` 深等于 `{}`、POST 顺序=选择顺序 |
| 阶段 2：100 MiB 前端预检 + 后端 413 message 落行（I-2/I-4） | IMPLEMENTED | `expert-materials.js` | `MAX_MANUAL_MATERIAL_BYTES=100*1024*1024`；104857600 提交 / 104857601 blocked 零请求；413 message 逐字 |
| 阶段 2：部分失败继续 + 重试只重发 failed + success 释放 File（I-4） | IMPLEMENTED | `expert-materials.js` | shared「部分失败重试」suite：第二项失败后第三项仍上传；重试仅重发 failed；再次点击开始 0 新请求 |
| 阶段 2：成功后 `store.page=0` + 既有 `fetchPage(store,{page:0,reason:"manual-upload"})`，保留筛选/selection，三视图同步（I-3） | IMPLEMENTED | `expert-materials.js` | shared「经共享 GET 进入列表」suite：上传成功前列表不变；仅 1 次 page=0 GET（保留 q/state）；summary 13 份；selection 保留；inline/drawer/selectionOnly 同行；清筛选后手动行恰好 1 行 |
| 阶段 2：上传中禁用选择/移除/取消/关闭/开始，Esc 被拦截（I-4） | IMPLEMENTED | `expert-materials.js` | shared suite + Chromium 实测（`:modal` dialog 中 Esc 不关闭、五处控件 disabled、progress 无 value/max） |
| 阶段 3：MANUAL_UPLOAD 行来源三段式、筛选单选项、query 成对（I-5） | IMPLEMENTED | `expert-materials.js` | shared「I-5 手动来源与既有来源并存」suite + Chromium 实测 |
| 阶段 3：S-3 policy 文案 / `aria-label=材料来源` / `全部来源` | IMPLEMENTED | `expert-materials.js` | style suite「S-3」；Chromium 实测 DOM 文本 |
| 阶段 4：S-1/S-2 逐字 CSS（S-1、S-2） | IMPLEMENTED | `styles.css` | style suite 从子计划抽取两个 ```css 块与 `styles.css` 逐字比对；`git diff --numstat` = 232/0（仅新增，零改动既有行） |
| 阶段 4：11 个静态资源统一键 `20260920-manual-material-upload`（I-7） | IMPLEMENTED | `index.html` | 11 处 `?v=` 全同值、注册顺序不变；5 个缓存契约测试 49/49 pass |
| 阶段 4：`sharepointFileCardDisplay.test.js` 从 `styles.css?v=` 派生键 | IMPLEMENTED | `sharepointFileCardDisplay.test.js` | 移除写死键，新增「旧键 0 命中」断言；6/6 pass（SharePoint 行为断言保留） |
| 阶段 4：保留 SharePoint 文件卡 WIP（不回滚、不改其实现） | IMPLEMENTED | — | 仅改 `index.html` 的 `?v=` 与测试取键方式；`app.js`/`mailbox-chat.js` 未出现在本提交 |

## 命令证据（全部在本调用内、对最终提交内容 `5f4967b` 的新鲜运行）

| 命令 | 结果 | 证据（退出码 / 计数） |
|---|---|---|
| `node --check src/main/resources/static/expert-materials.js` | PASS | exit 0 |
| `node --test src/test/js/expertMaterialsShared.test.js src/test/js/expertMaterialsStyle.test.js src/test/js/sharepointFileCardDisplay.test.js` | PASS | exit 0；tests 39 / suites 16 / pass 39 / fail 0（shared 23、style 10、sharepoint 6） |
| `node --test src/test/js/meetingConfirmationAssets.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js src/test/js/ragKnowledgeBasePage.test.js src/test/js/checkRepliesRelocation.test.js src/test/js/overlayAndDialogContrast.test.js` | PASS | exit 0；tests 49 / suites 7 / pass 49 / fail 0 |
| `git diff --check` | PASS | exit 0（无空白错误） |
| `node --test src/test/js/*.test.js` | **FAIL(1 旧红)** | exit 1；tests 1035 / suites 207 / pass 1034 / **fail 1** |
| `JAVA_HOME=…zulu-11… mvn test` | **FAIL(node-test 步骤)** | exit 1；Java surefire `Tests run: 3503, Failures: 0, Errors: 0, Skipped: 13`（绿）；失败仅为 `exec-maven-plugin:exec (node-test)`，其内容即上一条 JS 红 |

### 唯一红灯的归属（变更前基线，非本子计划引入）

失败测试：`src/test/js/meetingCalendar.test.js:188` —「styles.css 逐字追加 S-2 CSS 块；mailbox-chat.css 未吸收 calendar-* 规则」，断言 `stylesSource.includes(briefCss)` 为 false。

证据链（三条互相独立）：

1. 用 `git archive HEAD | tar -x -C /tmp/fp-baseline` 取得 **HEAD 纯净快照**并跑全量 JS：`# tests 1023 / # pass 1006 / # fail 17`（12 个文件），其中就包含该 meetingCalendar 用例 —— 与 brief 记录的基线计数完全一致，说明它是**基线既有红灯**，不是本次改动引入。
2. 直接对 `git show HEAD:src/main/resources/static/styles.css` 求值：`includes(briefCss) === false` —— 与本次改动无关（我未修改任何既有 selector，`styles.css` diff 为 232 插入 / 0 删除，且 `styles.css` 去掉新增块后与 HEAD 字节相等）。
3. 逐行定位差异：`briefCss`（`docs/plans/fast/meeting-mail-master/children/03-calendar-ui/brief.md`）里 `.calendar-dialog{width:…}` 不含 `inset:0;margin:auto;`，而 `styles.css:11195` 的同一规则含之 —— 这是 meeting-mail 并行工作的后续修正与该 fast 子 brief 文本漂移，与手动上传无关。

结论：brief 中「17 个基线红灯全部是缓存键断言、统一键后全量 JS 必须 0 fail」的判断对本用例不成立（该用例与缓存键无关）。修复它必须改动 `.calendar-dialog` 既有规则（S-1/S-2 明令「不改任何既有 selector」，且属于覆盖并行工作）或改 `docs/plans/**`（明令禁止），两者都超出本子计划 6 文件授权范围，故**保留并如实上报**，不隐藏、不绕过、不伪造修复。

## 变更文件

| 文件 | 作用 | numstat |
|---|---|---|
| `src/main/resources/static/expert-materials.js` | S-1 动作组入口、S-2 dialog/队列状态机、串行上传、错误与重试、共享 GET 刷新、MANUAL_UPLOAD 来源展示、S-3 文案/aria | 371 / 18 |
| `src/main/resources/static/styles.css` | 逐字插入 S-1/S-2 CSS 块（插在既有末尾块 `/* meeting-mail-07: outbound files */` 之前） | 232 / 0 |
| `src/main/resources/static/index.html` | 11 个版本化资源统一键 `20260920-manual-material-upload`，顺序不变 | 11 / 11 |
| `src/test/js/expertMaterialsShared.test.js` | FakeServer 上传支持、FormData/event stub、5 个新 suite（入口/串行/边界与重试/共享刷新/I-5） | 484 / 0 |
| `src/test/js/expertMaterialsStyle.test.js` | MiniElement `matches/closest`、FormData stub、POST 支持、4 个新 suite（CSS 逐字、入口与 dialog、S-3） | 338 / 2 |
| `src/test/js/sharepointFileCardDisplay.test.js` | 缓存键改为从 `styles.css?v=` 派生 | 11 / 1 |

提交：`5f4967b8d5f94663266c095e6a2e9ec69f570505` `feat(fast-p): implement frontend`（authorized 6 文件显式 `git add`；无 `git add -A/-u`；`docs/plans/**` 未进入提交；未 push/merge/rebase/amend）。

## 真实浏览器验证（非 Node DOM stub）

在 `/tmp/fp-smoke`（软链真实 `styles.css`/`expert-materials.css`/`expert-materials.js`，页面仅注入 `ExpertMaterials.configure({api})` 的假 transport）用 Chromium 实测：

- S-1：header 动作组 `["手动上传","AI 智能分析"]`（drawer 为 `["手动上传","关闭"]`，按钮 class `button small primary`；drawer 仍为 `:modal`）。
- S-3：policy 逐字、来源筛选 `aria-label=材料来源`、选项 `["全部来源","手动上传","材料补充请求"]`、行来源 `手动上传 · op1 · 2026-09-20 14:17` / 邮件行原样。
- dialog：`:modal` 顶层、宽 640px、底色 `rgb(255,255,255)`、圆角 14px、边框 `rgb(220,228,239)`、flex column、无 id、无 inline style；文案逐字（标题/副标题/选择文件/100 MB 说明/`×`）。
- 真实 File 选择（`ok.pdf` / `fail.pdf` / 104857601 字节 sparse 文件）：POST 记录 = `ok.pdf → fail.pdf`（各 1 个 `file` part、`headers` 为 `{}`），超限文件 **0 请求**；状态落定 `success / failed(服务端 message) / blocked(超过 100 MB，未上传)`；成功行不可移除、失败/阻断行可移除；成功 `rgb(5,150,105)`、失败/阻断 `rgb(190,18,60)`。
- 上传中：`start/cancel/close/input/remove` 全 disabled，按钮与摘要均「正在上传 0/1」，`<progress>` 可见且 **无 `value`/`max`**（不伪造百分比）；此时按真实 Escape 键 dialog 仍 open；空闲时 Escape 关闭并从 DOM 移除；重开为全新空队列；重复点击入口始终只有 1 个 dialog。
- 移动宽度 390px：dialog 宽 `374px`（视口−16px）、`max-height 828px`、picker/actions `flex-direction: column`、summary `margin-right: 0`。
- 截图（会话临时路径，已由视觉模型复核：白色圆角模态、背景遮罩、绿色「上传成功」、灰化主按钮、无百分比条）：`/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/omp-sshots-1586bb7e2d9069ee.webp`（临时文件，可能已被清理）。

## 偏差（均为实现细节，不改动契约语义）

1. **CSS 插入位置**：S-1/S-2 两个逐字块插入在 `/* meeting-mail-07: outbound files */` 之前，而**不是**文件物理末尾。原因：仓库既有契约测试要求 outbound 块仍是 `styles.css` 最后一块 —— `mailboxOutboundAttachments.test.js:2194`（`endsWith(S2_BLOCK)`）与 `materialRequestIntegration.test.js:839`（「S-2 不得追加到 styles.css 末尾（meeting-mail-07 的块必须仍在最后）」）。放在末尾会让前者由绿转红（该文件不在本子计划 6 文件授权内）。块内容仍逐字（含计划代码围栏的 2 空格列表缩进原样保留，不做归一化）；`styles.css` 去掉新增块后与 HEAD 字节相等，既有 selector 一字未改。
2. `expertMaterialsStyle.test.js` 的逐字比对以**原文围栏内容**（不 de-indent）为准，与仓库既有抽取写法（`/```css\n([\s\S]*?)```/`）一致；断言形式为 `includes(块)` + S-2 位于 S-1 之后 + 新块在既有末尾块之前。
3. 为让 style 测试能真实驱动点击，给其独立 DOM stub 补了 `matches`/`closest`/`preventDefault`/`stopPropagation`；这些只补齐真实 DOM 能力，不改变任何既有断言。
4. `uploadedBy` 缺失时省略来源中段（I-5 明确规定），无其它裁剪。

## 未解决风险

1. **全量 JS 门禁仍有 1 个基线红灯**（meetingCalendar 的 fast brief 文本漂移，见上）。它使 `node --test src/test/js/*.test.js` 与 `mvn test` 退出码为 1（Java 部分全绿）。需要人工/控制者决策：更新 `docs/plans/fast/meeting-mail-master/children/03-calendar-ui/brief.md` 的 `.calendar-dialog` 规则，或在授权范围内把 styles.css 对齐该 brief；本子计划 scope 内无法两全。
2. **未执行真实后端 + 真实页面的联调与人工验收 A-1..A-9**（需 MySQL/登录账号/运营浏览器）。浏览器冒烟用假 transport 覆盖了 A-1/A-2/A-3/A-5/A-6/A-8 的 UI 侧可观察结果；A-4/A-7/A-9 的端到端（真实 201→GET→下载/预览/AI、邮件附件链路隔离、跨专家安全、缓存强刷）留给主计划阶段 3 联合验证。
3. 真实浏览器中 `dialog.close()` 的 `close` 事件按规范是**异步派发**（DOM stub 为同步，二者存在固有差异）；组件在 `close()` 之后立即清理、并在 `close` 事件上再次幂等清理，两种时序都已覆盖（Chromium 实测关闭/移除正常）。

## Freshness

- Plan identity rechecked: YES（执行前后哈希一致）
- Worktree identity rechecked: YES（root/branch/git-dir 与本报告一致；HEAD == 目标分支 tip）
- Reported commits reachable from target branch: YES（`5f4967b` 是 `fast/manual-expert-material-upload-main` tip）
- Required commands run this invocation: YES（全部 6 条，且在最终实现态之后重跑）
- Historical evidence used only as baseline: YES（baseline 仅用 `git archive HEAD` 快照重放，未用于充当 required command 结果）

## Next Action

- READY_FOR_VERIFICATION → 运行 `verify-p`（请把上文第 1 条基线红灯作为「既有、已归属」项核对，而非本子计划新引入的失败）。
