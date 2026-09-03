# c6 Execution Report — 05 可信工作台前端替换：正文 / 用到哪些事实 / 未识别的提问

- Result: **READY_FOR_VERIFICATION**
- Plan: `docs/plans/2026-09-02/05-workbench-frontend-replace.md`
- Plan identity: `commit:46cc5c46395814b1ef03e52ab8b8bfb5197f372c`（`git diff 46cc5c46 -- <plan>` 为空，已复核）
- Master plan: `docs/plans/2026-09-02/00-execution-order.md`（identity `commit:92b0519a18a3a46989f8733259af4649f7748a72`；G-1..G-9 遵守）
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-02-execution-order`
- Branch: `fast/2026-09-02-execution-order`
- Child base (product boundary): `db89054f32a51f79f4cc86f5b21a9871a8dac729`（c5 代码头）；实施前 HEAD `4f11bc6b53306d96bc0f11c50e002654978f6459`（c5 证据头）
- Implementation commit: `60efcbaaa14e919ff8b4cfa9539cca41fd6a6d62`（`feat(fast-p): implement c6`，14 个授权文件；docs/plans/fast/** 未纳入）
- Task status: COMPLETE（全部门禁：新/改写前端测试 22/0、JS 全量 641/0、node --check 双文件、mvn test 3108/0/0/8、clean package、app.js diff 恒空、residue greps 全空、git diff --check 无输出）

## 控制器裁决登记（fast-p 进程内批准，非计划修订）

向 Main（controller）上报两处计划缺口并取得裁决（master G-5/G-7 权威，child plan 表格过期）：

1. **G-7 退役清单超出计划 T5 表**：计划表只列 trustReplyWorkbench.test.js / ThreeStep 删除 + SharedMount 改写，但 G-5/S-5 预 grep（HEAD 态先跑）证明另有 3 个测试文件整体断言旧按条目工作台表面（被本计划删除/重写后必红）：
   - `autoRunOrchestration.test.js`（1008 行）—— vm 跑旧工作台源码，点 `data-action="auto-run"`，断言 /assemble+/state 调用、一键预判/机器代填/汇总已完成判定行、`busyOverlayState` 等函数切片，以及 S-5 删除的 `.trust-reply-autorun{-hint}`/`.trust-reply-autofilled` CSS 块。**控制器裁决：DELETE**（主题整体退役）。
   - `aiReplyLoadingFeedback.test.js`（1276 行）—— extractFn 旧工作台 `data-role="attempt-timeout"/"total-timeout"`（SSE 时代 TTL 控件，已移除）。**裁决：DELETE**。
   - `overlayAndDialogContrast.test.js`（510 行）—— 其中 I-1..I-6 与整段「rendered behavior」vm/切片旧 `renderMarkup/renderBusyOverlay/busyOverlayState/cancel-generation`；**裁决：优先外科改写保留仍有效用例**。已改写为只保留 I-7（.action-dialog/.trust-reply-busy-* 不透明白底 + dark 配对）、I-8（缓存键三联）、S-1（.reply-workflow-content 逐字块）、S-2（busy 四规则逐字 + spinner/keyframes 唯一）、S-3（dialog-body 对比度作用域）——全部断言 CSS 块不在 05 S-5 处置表内，逐字未动，全绿。
2. **G-5 钉值文件集合超出计划「只有 batchSendTaskConsoleVisualFix.test.js:49-51」**：HEAD 预 grep 实测 5 个文件钉 `20260902-rag-knowledge-base`：batchSendTaskConsoleVisualFix:49-51（计划内）+ checkRepliesRelocation:11、manualReplySubjectPrefill:13（CACHE_KEY 常量）+ overlayAndDialogContrast:15（CACHE_KEY）+ ragKnowledgeBasePage:332-338（c5 自己的 G-5 用例，钉 c5 值）。5 处全部单字符串同步为 `20260902-rag-workbench`（batchSend 在计划清单内；其余 4 处为 master-G-5 授权同步，与 c5 deviation #1 同类）。
3. `aiReplyReviewConfirmation.test.js` **未动且保持绿**：新工作台保留两种完成态文案 `完成模拟并评估` / `采用到人工回复`，且不含 `manual-rich-reply`/`/send` 字符串。

## 变更文件（13 = 计划清单 9 有效动作 + 4 个授权追加；另 2 个清单文件零改动说明）

计划 `## 变更文件清单`（10 行）：

| # | 文件 | 动作 | 状态 |
|---|---|---|---|
| 1 | `src/main/resources/static/trust-reply-workbench.js` | 重写 | **完成**：3122 行 → 579 行；IIFE + `window.TrustReplyWorkbench` + mount/unmount + options 键集原样（I-24）；单一 compose 流程 + 四操作 + 完成动作 |
| 2 | `src/main/resources/static/index.html` | 修改（三处缓存键） | **完成**：11/2093/2094 → `20260902-rag-workbench`（G-5） |
| 3 | （app.js 不在范围） | — | 未触碰：`git diff --stat app.js` 恒空（实施后复核） |
| 4 | `src/main/resources/static/styles.css` | 修改 | **完成**：S-5 四处删除 + S-2..S-4 逐字追加（见 §样式） |
| 5 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改（缓存键 49-51） | **完成** → `20260902-rag-workbench` |
| 6 | `src/test/js/trustReplyWorkbench.test.js` | 删除 | **完成** |
| 7 | `src/test/js/trustReplyWorkbenchThreeStep.test.js` | 删除 | **完成** |
| 8 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 改写 | **完成**：3062 → 269 行，仅 I-24 挂载契约 + G-5 三联 + I-25 unmount（abort/解绑/late response）三组断言 |
| 9 | `src/test/js/autoPreviewWorkbenchHost.test.js` | 改写（按需） | **复核后无需改写，零 diff**：现有 5 用例全部断言 app.js/index.html/styles.css 的 AUTO_PREVIEW 退役令牌与 LIVE 宿主存在性，本计划不引入任何被断言令牌，文件在最终态 5/5 全绿 |
| 10 | `src/test/js/ragWorkbenchRender.test.js` | 新增 | **完成**：7 用例（G-8 + I-26 + I-27 + I-28×2 + I-29 + S-2..S-4） |

控制器授权追加（§控制器裁决）：

| # | 文件 | 动作 |
|---|---|---|
| 11 | `src/test/js/autoRunOrchestration.test.js` | 删除（G-7；主题退役） |
| 12 | `src/test/js/aiReplyLoadingFeedback.test.js` | 删除（G-7；SSE 时代表面退役） |
| 13 | `src/test/js/overlayAndDialogContrast.test.js` | 外科改写（保留 I-7/I-8/S-1/S-2/S-3；退役 I-1..I-6 与 rendered behavior） |
| 14 | `src/test/js/checkRepliesRelocation.test.js` | 修改（CACHE_KEY → 20260902-rag-workbench，G-5） |
| 15 | `src/test/js/manualReplySubjectPrefill.test.js` | 修改（CACHE_KEY → 同上） |
| 16 | `src/test/js/ragKnowledgeBasePage.test.js` | 修改（G-5 用例 332-338 → 同上） |

（overlayAndDialogContrast 第 13 行同时含 CACHE_KEY 同步。）

未触碰：`docs/plans/fast/**`、后端任何文件、`app.js`（diff 恒空）、c1-c5 已提交文件、`ReplySnippetService`、旧 `/api/trust-reply/workbench/*` 端点（07 退役）、主 checkout（styles.css 一次误写已 `git checkout --` 还原并复核）。

## 关键实现（对照不变量）

- **I-24**：重写文件保留幂等 IIFE `(function (global){ if (global.TrustReplyWorkbench) return; … })(window)`、`window.TrustReplyWorkbench.mount(host, options)` → `{ unmount }`；options 键 `mode/source/contextPath/autoBootstrap/onUnauthorized/onChange/onComplete` 逐键消费（SharedMount 断言两宿主挂载点键并集 == 契约键集、无契约外键、运行时逐键读取）。app.js 四个锚点（requireTrustReplyWorkbenchRuntime ~206、unmount ~173/186、mountAiTrainingTrustReply ~4214、mountLiveTrustReply ~10574、adoptTrustReplyAssembly ~10523）一行未改。
- **I-25**：每个实例独立 `requestSeq` 计数器 + `AbortController` Set；`compose()` 先 `seq++` 并 abort 旧控制器再发新请求；响应落地守卫 `disposed || mySeq !== requestSeq`；`unmount()` abort 全部 + 逐对 removeEventListener + seq 失效。测试断言：unmount 后 signal.aborted === true、宿主监听器数归零、late response 不改变 host.innerHTML。
- **I-26**：`renderParagraph` 只按 `renderMode==="VERBATIM"` 输出 `class="trust-reply-para verbatim"`（COMPOSE → 无修饰；frame 段 → `frame`）；逐字段 tag「逐字」，手改后「逐字 · 已脱离事实原文」；全文件 `answer ===` grep 为空；不改 text 比对。响应里 `bodyParagraphs[].renderMode` 服务端值 ∈ VERBATIM|COMPOSE（03 契约），逐字样式不靠猜。
- **I-27**：编辑正文切 `editMode`（每段 contenteditable）；input 实时红框（classList.add edited），focusout 提交 state（文本、edited=VERBATIM 且已变、dirty=true）并重绘；send 区出现可见「已手工编辑」标记（data-role="dirty-flag"）。dirty 后点重新生成先 `global.confirm(...)`，取消不发请求且保留手改，确认才 compose（测试注入 confirm 双态验证）。
- **I-28**：`applyRemoveFact` 只把 code 移入 `excludedFactCodes`（并从 forced 移除）、`applyAddFact` 追加 `forcedFactCodes`（并从 excluded 移除），随后重调 compose；绝无本地段落 splice（测试断言 paragraphs 长度不变 + 下一请求体携带 excludedFactCodes；flow 用例断言 pending 期间草稿段数不变）。
- **I-29**：发送区 complete 按钮只受「无草稿 || busy」影响；未识别提问/事实数/REVIEW 事实永不 disabled（flow 用例：unaddressed 3 项时按钮无 disabled），右侧仅旁注「未识别提问 N 项」。
- **onComplete 载荷**：`{ text, usedFactCodes, ragCorpusFingerprint, unaddressed }`（text = 框架四段 + 正文各段以 `\n\n` 拼接的当前全文，含手改；usedFactCodes 来自最近 compose 的 usedFacts 顺序）——与 c4 `adoptTrustReplyAssembly` 形态分流逐字吻合（ragShape = Array.isArray(usedFactCodes)），app.js 未改即自动接上。
- **请求面**：全部指向 `${contextPath}/api/rag-reply/compose`（body = sourceType/sourceId/model/forcedFactCodes/excludedFactCodes/frameSelection）；框架选项只读 GET `${contextPath}/api/reply-snippets`（非旧端点；供四个框架 select 选项；失败不阻断 compose，服务端默认框架兜底）。旧端点零调用、零字面量（头注释已改写避开路径字符串）。
- **四段框架**：compose 结果 `frame.salutation/greeting/acknowledgement/closing` 渲染为首尾各两段虚线灰 `frame` 段落（tag「回复框架 · 尊语/…」）；本地切换 select 立即替换对应段落文本并更新 frameSelection（下次 compose 携带）；正文中间段（模型产出）不动（A-8 语义）。
- **错误面**：400/422/502 服务端 code → COMPOSE_ERROR_TEXT 中文；401/403 走 onUnauthorized；错误横幅（ai-reply-error）只在无历史草稿时替换正文区，有草稿时置于其上方，不丢已生成内容。

## 样式（S-1..S-5）

- S-1：复用 c5 的 `--verbatim*` token 与 `.rag-badge`/`.rag-badge.verbatim`/`.risk-high`/`.status-review`（零重定义）。
- S-2..S-4：逐字追加到 styles.css EOF（.trust-reply-layout 两栏 + .trust-reply-doc/.trust-reply-para{,.verbatim,.frame,.edited}/.trust-reply-para-tag + .trust-reply-facts/-fact*/-unaddressed* + .trust-reply-frame-bar/.trust-reply-send）；与计划代码栅格逐字节一致（python 抽取计划 css fence 与 EOF 尾巴比对，内容全等、仅栅格间空行数差异）；`.trust-reply-send` background 字面 `rgba(255, 255, 255, .96)` + `backdrop-filter: blur(8px)`，不含 `var(--panel-bg)`（测试断言）。
- S-5 处置（删前先跑 residue grep，见 §命令 3-7）：
  - 保留不改：`.trust-reply-workbench`（含 `.reply-workflow-content` 后代块——不在 S-5 表内，未动）、`.trust-reply-busy-overlay/-busy-card/-busy-text/-busy-hint`、`.trust-reply-readonly*`、`.trust-reply-mode-note{,.::before}`（两处位置复核，HEAD 只在 7388/7402 一处）。
  - 就地修改：`.trust-reply-toolbar` 容器规则保留；删除 `.ai-reply-model-row` 三条后代规则组（label/select/[data-action]）。
  - 删除：`.trust-reply-autorun`/`-autorun-hint`/`-preanalysis`(+strong)/`-autofilled`、`.trust-reply-gate-list`/`-gate-item`/`:empty`、`.trust-reply-factset*`（连同其上方过时注释行）。表外 `.trust-reply-*` 死规则（item/page/picker/preview 等）按要求不动。
- 禁止项复核：新 JS 无 inline style（测试断言 `style=` 不存在）、无契约外新 class（唯一复用旧名 .ai-reply-model-row 的决定被放弃，工具行用无 class 的 div 内联流式排布）。

## 命令与结果（JDK 11 zulu-11；最终代码态新鲜执行）

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | G-5 预 grep（HEAD 态）：`grep -rn "v=$(grep -o 'styles.css?v=[^"]*' index.html \| cut -d= -f3)" src/test/js/` | 0 | HEAD 钉值 `20260902-rag-knowledge-base` 5 文件（batchSend 49-51 / checkRepliesRelocation 11 / manualReplySubjectPrefill 13 / overlayAndDialogContrast 15 / ragKnowledgeBasePage 332-338）—— 计划清单过期，已上报并同步（§控制器裁决 2） |
| 2 | S-5 残留预 grep（HEAD 态，`trust-reply-factset/-autorun/-gate-list/-preanalysis/-autofilled` in static+test） | — | 残留 = 旧 JS + 待删测试 + 待删 CSS 块自身；删除后（命令 5-7）全空 |
| 3 | `grep -rn "trust-reply-factset" src/main/resources/static/ src/test/js/`（终态） | 1 | 无输出 |
| 4 | `grep -rn "trust-reply-autorun" …` / `-gate-list` / `-preanalysis` / `-autofilled`（终态） | 1 | 全部无输出 |
| 5 | `grep -rn "trust-reply/workbench" src/test/js/`（终态，G-7） | 1 | 无输出 |
| 6 | `grep -rn 'answer ===' trust-reply-workbench.js`（终态，I-26） | 1 | 无输出 |
| 7 | `grep -rn "20260902-rag-knowledge-base" src/`（终态，G-5 收口） | 1 | 无输出；index.html 三处 = `20260902-rag-workbench` |
| 8 | `node --test src/test/js/ragWorkbenchRender.test.js` | 0 | **tests 7, pass 7, fail 0** |
| 9 | `node --test src/test/js/trustReplyWorkbenchSharedMount.test.js` | 0 | **tests 5, pass 5, fail 0** |
| 10 | `node --test src/test/js/autoPreviewWorkbenchHost.test.js` | 0 | **tests 5, pass 5, fail 0**（零 diff 保留） |
| 11 | `node --test src/test/js/overlayAndDialogContrast.test.js` | 0 | **tests 5, pass 5, fail 0**（外科改写后） |
| 12 | `node --test src/test/js/*.test.js` | 0 | **tests 641, pass 641, fail 0** |
| 13 | `node --check src/main/resources/static/trust-reply-workbench.js` | 0 | SYNTAX_OK |
| 14 | `node --check src/main/resources/static/app.js` | 0 | SYNTAX_OK |
| 15 | `JAVA_HOME=…/zulu-11.jdk mvn test`（全量回归） | 0 | **Tests run: 3108, Failures: 0, Errors: 0, Skipped: 8**，BUILD SUCCESS（c5 基线 3108/0/0/8 持平；前端 JS 用例经 exec 插件在 test 阶段全绿 641/0） |
| 16 | `JAVA_HOME=…/zulu-11.jdk mvn clean package` | 0 | **Tests run: 3108, Failures: 0, Errors: 0, Skipped: 8**，BUILD SUCCESS，WAR 构建（target/weibo-talent-introduction-1.0.0-SNAPSHOT.war） |
| 17 | `git diff --check` | 0 | 无输出 |
| 18 | `git diff --stat src/main/resources/static/app.js` | — | **空输出**（app.js 零改动，I-24/T2） |

补充：styles.css hunk 结构复核 —— 4 处 S-5 删除 + EOF 追加一块（S-2..S-4 共 200 行 + 1 分隔空行）；改动前基线已按计划留档命令执行：
`git show HEAD:…/styles.css | sed -n '5515,5625p;7393,7620p'` → `/tmp/c6_css_baseline_5515_5625_7393_7620.txt`（339 行；S-5 删除后不再适用行号，仅作留档）。

## 偏离（登记；均经控制器授权，无计划修订 commit）

1. **G-7 追加 3 个测试文件退役/改写**（超出计划清单）：autoRunOrchestration.test.js、aiReplyLoadingFeedback.test.js 删除；overlayAndDialogContrast.test.js 外科改写保留 I-7/I-8/S-1/S-2/S-3。master G-7 权威授权（控制器裁决记录见上；与 c4 A3/A4 增补先例同构）。
2. **G-5 追加 4 个钉值文件同步**（batchSend 之外）：checkRepliesRelocation:11、manualReplySubjectPrefill:13、overlayAndDialogContrast:15、ragKnowledgeBasePage.test.js:332-338 → `20260902-rag-workbench`（master-G-5 授权；c5 deviation #1 同类的本轮实例，集合以预 grep 实测为准）。
3. **autoPreviewWorkbenchHost.test.js 零改动**：计划行 9 动作为「改写（按需）」，复核结论为无需改写（全部断言与 c6 变更正交），最终态 5/5 绿。
4. **设计注记（非修订）**：`RagBodyParagraph` 仅 text+renderMode，无 fact_code 字段，逐字段落 tag 显示「逐字」（不显示 fact_code 角标）；事实代码在右栏列表展示。verbatim 段落与事实的对应无法由响应推导（I-26 又禁止文本比对），角标显示留给后续若服务端在 bodyParagraphs 补充 factCode 再启用。A-9 四项目测契约全部由 CSS 字面保证。
5. **工具栏排布**：S-5 删除 .ai-reply-model-row 后代规则后，工具条动作行（模型 select + 重新生成/编辑正文/复制）以无 class 的普通块承载内联流式排布（未引入契约外 class、未改 .trust-reply-toolbar 容器规则）。
6. 训练宿主 onComplete 仍触发既有 `renderAiTrainingEvaluationPanel`（app.js 未改，文案为旧按条目口径）——本计划只按契约产出新载荷形状，评估面板的演进不在 05 范围。

## 新鲜度

- Plan identity 复算: YES（46cc5c46 未变，plan diff 为空；master 92b0519 未变）
- Worktree identity 复算: YES（branch `fast/2026-09-02-execution-order`；实施前 HEAD 4f11bc6 → 实施后 60efcba，db89054 为 ancestor）
- 实现提交不含 fast-p 证据: YES（仅授权文件；docs/plans/fast/** 未纳入；git status 仅余 c6 证据目录 untracked）
- 必需命令最终代码态新鲜执行: YES（命令 1-18 全部在提交前最终态执行/复核）
- 环境副作用清理: 主 checkout 误写已还原（`git checkout -- styles.css` + `git status --porcelain` 复核无 src 残留）；/tmp 基线文件仅为留档
