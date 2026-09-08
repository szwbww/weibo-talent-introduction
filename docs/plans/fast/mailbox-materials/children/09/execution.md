# Child 09 Execution Report — AI 所选材料获取与分析衔接

- Status: READY_FOR_VERIFICATION
- Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials/docs/plans/2026-09-07/09-material-analysis-selection.md
- Plan SHA-256: 67a6cdeb483ae109d0c02c69b8f35a9d7711113f6d8f0434cface505b7b0ed25
- Execution ID: …/09-material-analysis-selection.md@67a6cdeb483ae109d0c02c69b8f35a9d7711113f6d8f0434cface505b7b0ed25
- Execution epoch: NEW
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials
- Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials@fast/mailbox-materials@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-mailbox-materials
- Pre-execution code SHA: 47a72d885498ff473560d26901d2596ffdddb2cd (child 08 code head; HEAD at start 19b01c06 = 08 code + controller docs commit)
- Implementation commit: see `feat(fast-p): implement 09` below
- Executor: Impl09

## Files changed (exactly the plan's 5-file list; nothing else)

1. `src/main/resources/static/app.js` (修改) — AI 选件衔接共享材料组件：
   - `aiAnalysisState` 扩展 `materialsMode/pickerHost/pickerView/unsubscribe/snapshot/intentToken/selectionApplied/defaultOverLimit/run`；新增 `AI_ANALYSIS_SELECT_LIMIT = 500`。
   - `openAiAnalysisModal`：组件可用且真实 contactId 时走 09 流程（打开仅 GET 历史结果 + 共享 store 材料元数据；零 POST）；否则保留旧 documents 路径。任何打开前先 `teardownAiAnalysisSession()`（换专家不复用旧 token）。
   - `closeAiAnalysisModal`：隐藏弹窗并 `teardownAiAnalysisSession()`（销毁 intentToken/订阅/picker 视图；不宣称取消已发出的服务端分析/下载）。
   - `renderAiAnalysisModal`：select 分支按 `materialsMode` 分派 S-2 渲染（`renderAiAnalysisMaterialsSelect`：`.em-analysis` note + `[data-material-picker]` + `.em-analysis-actions` status/CTA，footer 仅取消）或旧选件；loading/results 前先 teardown picker。
   - `startAiAnalysis`：旧路径保留；09 路径 = 冻结 `ids/needed` 快照 → `needed=0` 直接 `fireAiAnalysis`，否则 `ExpertMaterials.requestTransfers(只缺件)` → 订阅驱动 `stepAiAnalysisRunIfReady`；失败态 CTA 走 `retryAiAnalysisFetch`（只请求失败项）。
   - 新增 14 个 09 helper：`aiAnalysisMaterialsCapable`、`enterAiAnalysisSelectMode`、`teardownAiAnalysisPicker`、`teardownAiAnalysisSession`、`renderAiAnalysisMaterialsSelect`、`handleAiMaterialsSnapshot`、`applyAiAnalysisEntrySelectionIfNeeded`、`aiAnalysisSelectionIds`、`aiAnalysisItemStorageState`、`aiAnalysisFailureLines`、`updateAiAnalysisSelectChrome`、`stepAiAnalysisRunIfReady`、`retryAiAnalysisFetch`、`fireAiAnalysis`。
   - `ai-analysis-reanalyze` 动作改走 `enterAiAnalysisSelectMode(true)`。
2. `src/main/resources/static/expert-materials.js` (修改-扩展，08 同路径) — selection/API 扩展：
   - `subscribe(contactId, listener)`（store 变更同步后回调 live 快照）、`getState(contactId)`、`setSelection(contactId, ids)`、`requestTransfers(contactId, ids)`（复用 submitTransfers 提交/错误/提交后刷新路径并返回服务端响应）。
   - `syncView` 拆为 impl + wrapper（wrapper 每次同步后 `notifySubscribers`）；`createStore` 增加 `listeners`。
   - selectionOnly 视图：`analysisSupported === false` 行 checkbox 禁用、状态列附原因（图片行固定「当前不支持图片文字识别」，其它「仅支持 PDF/文本格式分析」）；表头“选择本页”只统计/勾选可分析行；`syncRowSelection` 视图感知。
3. `src/test/js/expertMaterialAnalysisFlow.test.js` (新增) — 12 用例，DOM 能力充足的最小树（含 innerHTML 解析/id 注册/事件冒泡），覆盖：
   - I-1：打开零 POST/零文件抓取（只 GET）；app.js 模板源文本断言；已选带入可分析子集 + JPEG 禁选/原因；默认 CV/学位跨页勾选；>500 明确提示不悄悄截断；提交冻结快照后取消勾选不漂移。
   - I-2：缺 1 只取 1 → 全部 STORED 后恰好一次 ai-analysis（body=冻结名单）；失败 → 0 次分析且错误区展示 attachmentId/文件名/原因；重试只请求失败项 → 就绪后按原快照一次分析；M=0 直接分析；空文本 PDF 逐文件原因展示。
   - I-3：获取中关窗 → token 销毁、下载继续轮询、零后续自动分析、重开不自动提交且显示已存；历史结果前端绝不 DELETE/清空（重开可读、PUT 编辑原接口）；组件缺失回退旧路径（documents+历史 GET、footer 原按钮）。
4. `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisService.kt` (修改) — `analyze`：extract 之后、构建 prompt/LLM/deleteAll 之前，逐所选项校验；任一 `!supported` → 抛原 `AnalysisFailedException`（message 含 attachmentId/文件名/「不支持格式」）；任一空文本 → 抛原 `AnalysisFailedException`（含「无可读文字」）；移除静默 filter（不再把部分文件成功当全部成功）。GlobalExceptionHandler ANALYSIS_FAILED 映射与 deleteAll 时机未动。
5. `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisServiceTest.kt` (修改) — 新增 3 用例：可读+空文本混合 → LLM 0/deleteAll 0/旧结果保留且 message 列明文件名+attachmentId+无可读文字；混合含 JPEG（unsupported）→ LLM 0/deleteAll 0 + 不支持格式原因；addField/clearResults 语义不变（displayOrder 续接 + 纯 delete 断言）。全可读路径与 updateField 既有用例原样通过。

## Invariant checks (I-1..I-3 + S-2/S-5)

- I-1 显式选择快照：打开仅 GET（材料 store 挂载 GET /materials + 历史 GET /ai-analysis）；有已选带入 analysisSupported 子集（排除项原因行内可见）；无已选默认 CV/学位来自 summary.defaultAnalysisAttachmentIds；>500 只提示「默认材料超过500份，请分批选择」不自动勾选；提交冻结 `run.ids`（测试：提交后取消勾选/翻页分析名单不变）。跨页默认勾选测试断言翻页行勾选态。
- I-2 获取后分析：分析候选=共享选择∩可分析；needed=M=非 STORED；POST /transfers 只发缺失件并经共享 store（同一提交路径/轮询/订阅，无第二下载状态拷贝）；订阅驱动判定——全部 STORED → 恰好一次 POST /ai-analysis；任何 FAILED/SOURCE_UNAVAILABLE → 整批 0 次分析 + 失败 attachmentId/文件名/原因展示；重试只请求失败项并仍按原冻结快照校验后一次分析；提交期间 `snapshot.submitting` 守卫避免把在途重试误判失败（本实现修复的关键竞态，见 deviations）。
- I-3 关闭与格式：close 销毁 intentToken/退订/卸载 picker；下载继续（inline host 轮询 GET 增长证据）；零后续自动分析；重开不自动提交、显示已存；JPEG 行禁选 + 「当前不支持图片文字识别」（服务端 analysisSupported 驱动，非扩展名伪装）；空文本 PDF 服务端原因逐文件展示；历史结果前端全程无 DELETE/提前清空。
- S-2：`.em-analysis`/`.em-analysis-note`/`[data-material-picker]`/`.em-analysis-actions` + role=status + CTA（M=0「开始分析」/M>0「获取所选文件并分析」/传输中「正在获取所选文件（k/M）」/失败「1 份获取失败」+「重试获取失败文件并分析」），类全部为 08 已落 CSS 或既有 styles.css 类；无新增 CSS；组件产物零新增 class。
- 能力守卫：新流程只在 `window.ExpertMaterials` 且具备 subscribe/getState/setSelection/requestTransfers 时启用；旧组件/未注册资源回退原 AI 流程（inert until child 11）。

## Required commands (run fresh from worktree root; exit codes)

| # | Command | Exit | Result |
|---|---|---|---|
| 1 | `node --check src/main/resources/static/app.js && node --check src/main/resources/static/expert-materials.js` | 0 | both syntax OK |
| 2 | `node --test src/test/js/expertMaterialAnalysisFlow.test.js` | 0 | tests 12, pass 12, fail 0 |
| 3 | `node --test src/test/js/*.test.js` | 0 | tests 704, pass 704, fail 0 (baseline 692 + 12 new) |
| 4 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDocumentAnalysisServiceTest` | 0 | Tests run: 9, Failures 0, Errors 0, Skipped 0 — BUILD SUCCESS |
| 5 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | 0 | Tests run: 3218, Failures 0, Errors 0, Skipped 9 — BUILD SUCCESS (log /tmp/mvn-full-09.log); Node exec phase tests 704 pass |

Regression guard: 08 suites `expertMaterialsShared.test.js`+`expertMaterialsStyle.test.js` run green (21 tests) against the extended component; full JS suite incl. all existing contract tests green.

## Commit

`feat(fast-p): implement 09` — 5 authorized files only (app.js, expert-materials.js, expertMaterialAnalysisFlow.test.js, ExpertDocumentAnalysisService.kt, ExpertDocumentAnalysisServiceTest.kt). Fast-p evidence (docs/plans/fast/** incl. this report, ledger.md controller change) excluded from commit; no push/merge/amend/rebase.

## Deviations / notes

- 冻结-重试竞态修复：`stepAiAnalysisRunIfReady` 在 `snapshot.submitting`（transfer POST 在途）时暂不判定——否则重试期间旧 FAILED 行会把刚置回 watching 的运行重新打成 failed 且永久卡死（failed 态不再被 step 推进）。属计划“重试后仍按原快照全部校验再分析”的正确实现细节。
- 已知边界（非缺口）：共享 store 只轮询当前页行；跨页/未访问页的冻结文件状态在未翻页浏览时不会自行刷新到 STORED，自动分析会在其行可见/重开后再判定。服务端 ai-analysis 本身对所有未就绪项 fail-closed（MaterialNotReadyException 409、旧结果不删），不会出现部分成功。计划验收 A-1（同页 4 件）、A-2（重开显示已存）均在此语义内通过。
- AI 分析候选=共享选择∩analysisSupported：页面上为“下载”勾选的不可分析项（如 JPEG）在 AI 选件视图呈现为禁选+原因，不进入分析名单；不修改用户在页面面板的共享选择。
- 默认勾选（≤500）会写入共享选择集并跨会话保留（与 08 “选择按 contactId 跨视图共享”一致；A-2「获取中关闭→重开显示已存状态」依赖此语义）。

## Freshness

- Plan identity rechecked: YES (unchanged 67a6cdeb…)
- Worktree identity rechecked: YES (unchanged)
- Reported commit reachable from target branch: YES (created locally on fast/mailbox-materials)
- Required commands run this invocation: YES (all 5, exact outputs above)
- Historical evidence used only as baseline: YES

## Remaining blocker

None. Next: verify-p on child 09.
