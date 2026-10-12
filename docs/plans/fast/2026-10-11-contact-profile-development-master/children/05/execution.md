# Child 05 — Execution Log

（实现完成并本地提交；完整结果见下。）

## 执行报告（会议视频界面与文件命名）

- 状态：实现完成、4 个授权文件已本地提交；处于 controller HOLD 中（见 §7 跨计划冲突与 §2 排序要求），GO 后按最终冻结态重跑必需命令并更新本文命令证据。
- 技能：`skill://execute-p`（可用，已按其 Plan Identity Gate / Target Worktree Gate / 三阶段执行）。
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master`
- Branch：`fast/2026-10-11-contact-profile-development-master`
- child_base_sha：`f248ebef68a80d8794e81b8a4cc5ff5c53d7edf3`
- 执行的计划：`docs/plans/2026-10-11/contact-profile-05-video-ui.md`
  - EXECUTION_ID：`.../docs/plans/2026-10-11/contact-profile-05-video-ui.md@b411616fb5d2523d20ef53f308063f75c7e4348f5ab702233003b0761ce387f8`（epoch 1 / NEW）
  - 计划正文内的 A2/A3 修正已生效（S-2 落位=插入 02 块之前；11 键目标=`20261011-contact-profile-05`）。
- 实现提交：`b94bd289cb571ecd7a80f0d657ada516d3573a9c` = `feat(fast-p): implement 05`（parent `52db510`）；未 push/merge/rebase/amend/squash。
- 报告路径（本文件）：`docs/plans/fast/2026-10-11-contact-profile-development-master/children/05/execution.md`（未提交）。

## 1. 改动文件（严格 4 个，全部在授权清单内）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/resources/static/app.js` | 新增 `/* contact-profile-05-video:begin…end */` 区域（约 1280 行）：常量/纯函数、S-1 模板、列表分页渲染、七态队列 + 串行 XHR 上传引擎、重命名、播放器、挂载/卸载；02 区域内 4 处 typeof 守卫调用点接入 |
| 2 | `src/main/resources/static/styles.css` | 逐字插入 05 S-2 块（8 行规则 + 1 行定位注释）到 02 S-2 块**之前**；02 块仍是文件末尾 CSS |
| 3 | `src/main/resources/static/index.html` | 原 11 项资源 `?v=` 统一 `20261011-contact-profile-05`（数量/顺序/引用方式不变，`task-modal-runtime.js` 仍无键） |
| 4 | `src/test/js/expertMeetingVideo.test.js` | 新建 25 个用例（真实挂载 + 可控 XHR/Response/DOM） |

未授权文件零改动：`expertManualProfile.test.js`、`expertContactSharedNote.test.js`、pom、迁移、后端、文档与计划均未触碰。

提交锚点（`b94bd289cb571ecd7a80f0d657ada516d3573a9c`，4 files changed / 3045 insertions(+) / 12 deletions(-)）：

| 文件 | blob SHA @ b94bd28 |
|---|---|
| `src/main/resources/static/app.js` | `c663b3cd5c10130a943c7b1d879c879e9b91f503` |
| `src/main/resources/static/index.html` | `7192fbec8d7b309918ecb27df904354e61d60bc3` |
| `src/main/resources/static/styles.css` | `648baf037dd4697c822086bc69e35c92b020d1a9` |
| `src/test/js/expertMeetingVideo.test.js` | `b3408bc64320d861d0d00852f65ade9a5d11e539` |

- 之后 controller 又落了 `5a06ecc docs(fast-p): pause 05`（fast-p 产物，不改上述 4 个文件的 blob）；工作区除本报告外干净。

### 1.1 02 区域内的 4 个守卫调用点（沿既有 typeof 约定）

1. `expertManualProfileShellTemplate()`：`videoNav`（`expertMeetingVideoNavTemplate`）拼在 4 个分组按钮之后、`videoPanel`（`expertMeetingVideoPanelTemplate`）拼在 `sections + editors` 之后 → 未加载 05 区域时输出与改动前逐字一致。
2. `mountExpertManualProfile()`：`if (typeof mountExpertMeetingVideos === "function") mountExpertMeetingVideos(session);`（紧随 03 守卫，独立于资料 Promise.all）。
3. `unmountExpertManualProfileSession()`：`if (typeof unmountExpertMeetingVideos === "function") unmountExpertMeetingVideos(session);`（紧随 03 守卫）。
4. 05 区域自身整体位于 `/* contact-profile-02-ui:end */` 之后；02 测试的 `MODULE_REGION` 切片看不到任何 05 代码。

自检断言（05 测试内）：`REGION_02.split("expertMeetingVideo").length - 1 === 4`（02 区域只通过这 4 处接触 05）。

## 2. 实现要点（按不变量）

- **I-1 列表与名字**：绑定 `session.expertId`（canonical；无则不发请求、不猜 ID）；`GET /api/experts/meeting-videos?expertId&page&size=20`；每项 `article.metadata-card` 显示自定义完整名（textContent）、`原始名 X · 大小 · 上传人 Y · 2026-10-11 10:00 北京时间`；名称主体规则与 04 同算法：NFC + trim、空主体、控制字符（`\u0000-\u001f\u007f-\u009f`）、`/ \ : * ? " < > |`、尾部句点、完整名（含点与扩展名）≤100 code point；超长默认名直接判 failed，必须人工改后才能上传；不使用 `maxlength` 冒充 code point 计数。
- **I-2 原始文件与进度**：`xhr.open("POST", …)` + `setRequestHeader("Content-Type","application/octet-stream")` + `xhr.timeout = 0` + `xhr.send(entry.file)`（原始 `File` 引用）；`URLSearchParams` 一次编码（`originalName/nameStem/expectedSize/expertId/level`）；串行只跑 1 个 XHR；`File.size > 1073741824` 与 0 字节、非法扩展名、非法名在**发请求前**判 failed；`upload.onprogress` 只在 `lengthComputable && total>0` 时把 `loaded/total` 与 `<progress>` 置为可计量，量到 100% 只进入 `processing`（“上传完成，正在保存”），**仅 201 且 JSON 可解析**才 `success`。
- **I-3 状态与重试**：状态集合 `EXPERT_MEETING_VIDEO_STATES` 12→ 唯一写入口 `expertMeetingVideoSetEntryState`（未知值收敛为 failed）；pending 可改名/移除，活动项按钮变“取消”，failed/uncertain 按钮变“重试”（显式，无自动重传），success 隐藏按钮且输入禁用；任何失败/未知/鉴权/容量错误都 `running=false` 暂停余队列并即时刷新列表；刷新后对 failed/uncertain 追加“列表中已有同名视频，请确认是否已上传成功”。
  - §实现方案 3「status0/network/error/abort 统一区分未开始与未知提交」的落地口径：**已量到字节（`uploadedBytes>0`，或已进 processing）→ `uncertain` + 固定文案「结果未确认，请刷新视频列表」**；**未量到任何字节 → 未开始**（用户取消 → `cancelled`；断网/超时 → 回到 `pending` + 「上传未开始，可重试」）。取消/断网一律不声称服务端撤销，不自动重传。
- **I-4 编辑与生命周期**：rename 用 `expectedVersion`（CAS）；409 保留输入、提示刷新（版本冲突）/修改名称（同名）、不自动覆盖；上传在发送时捕获 `entry.expertId`；`unmountExpertMeetingVideos` 停队列 → abort 在途 XHR → 关两个 dialog → `pause()+removeAttribute("src")+load()` 释放播放器 → 清 File/DOM 引用；所有异步回调先过 `state.closed || session.isStale()`，晚响应不写当前专家 DOM（含列表 GET 的 readSeq）。
- **I-5 鉴权与播放**：401/403 用 `new Response(xhr.responseText, {status, headers:{"Content-Type":"application/json"}})` 交 `handleAuthResponse`（`Promise.resolve(...).catch()` 收口其抛错），并停队列；`status 0` 不构造 Response；`video.src`/下载 `href` 只接受 `expertMeetingVideoApiUrl()` 白名单同源相对路径（拒绝绝对/协议相对/空白/反斜杠/`..`）；`<video controls preload="metadata" hidden>`，同一列表一次只挂一个播放器，播放/释放只走 src+load（不 fetch 整文件）；`error` 事件 → 「浏览器不支持此视频编码，请下载观看」，下载入口保留。

## 3. 必需命令与结果（最终实现态；GO 后将再跑一次并在本节更新）

| 命令 | 结果 |
|---|---|
| `node --test src/test/js/expertMeetingVideo.test.js` | **PASS** exit 0；`tests 25 / suites 5 / pass 25 / fail 0` |
| `node --check src/main/resources/static/app.js` | **PASS** exit 0 |
| `node --test src/test/js/*.test.js` | exit 1；`tests 1641 / suites 315 / pass 1640 / fail 1` —— 唯一失败为 **03** 静态键 pin（见 §7），02 静态测试已转绿 |
| （补充证据）`node --test src/test/js/expertManualProfile.test.js` | **PASS** exit 0；`tests 24 / pass 24 / fail 0`（含 A2 后的键断言） |
| （基线对比）`node --test src/test/js/*.test.js` @ `52db510` | `tests 1616 / pass 1615 / fail 1`（当时唯一失败=02 键 pin；本轮 bump 已修复） |

基线口径说明：brief 写的「基线 1616/1616」与实际不符——A2 落地后 05 开工前的基线是 **1616 / 1615 pass / 1 fail**（失败即 02 的键 pin，属预期待 05 bump 修复）。05 新增 25 个用例后总数 1641。

## 4. S-2 / 缓存键合同核对

- `styles.css`：05 块逐字来自 05 计划 ```css 块（`.ec-video-player … @media (max-width: 760px) {…}`），出现次数=1，位置早于 02 块；02 块仍满足 `replace(/\n+$/,'').endsWith(02 block)`（A3；02 测试 24/24 已实测）。视频上限 420px/小屏 260px；进度条复用既有 `.em-upload-progress`（5px、`accent-color:#3b82f6`），未新增任何既有规则改动。
- `index.html`：11 键同值同序（`styles.css → … → world-clock.js`），无新增/改序资产；`task-modal-runtime.js` 仍无 `?v=`。
- 隐藏语义：`.ec-video-player` 为作者样式 `display:block`，依赖既有全局 `[hidden] { display: none !important }`（styles.css:96）保证 `hidden` 生效——已在测试中断言该全局规则存在。

## 5. 新测试覆盖映射（`expertMeetingVideo.test.js`，25 用例）

- 静态：S-2 逐字+落位（A3，含 02 块末尾断言）、11 键（键值从 index.html 派生、目标值从 05 计划正文解析，**不固化字面量**）、区域边界（05 区域在 02 之外/无全局监听/无 inline style/02 仅 4 处守卫/`class="ec-profile"` 仍为 2 处）、I-2 源文本（`new XMLHttpRequest()`、`send(entry.file)`、octet-stream、`timeout = 0`、无 `FormData/FileReader/readAsDataURL/arrayBuffer/btoa/multipart`）。
- 真实挂载：`showExpertDetail` 与 `loadContactDetail` 两宿主都挂载 section（位于 4 个 `.ec-section[data-ec-group]` 之后）与 nav「会议视频」按钮；各发一次 `page=0&size=20` 列表 GET（分别绑定 ES expertId / canonical expertId）。
- 列表：空态「暂无会议视频」、上限提示文案不变、条目名/原始名/1 GiB/上传人/北京时间、分页仅 `total>size` 时显示、失败保留上次内容、section 无 id/无 inline style。
- 队列与请求：默认名主体去末扩展名、扩展 span 只读、每行仅一个 input；超限/非法名/非法扩展名/超长默认名 4 类网络前拦截（`xhr.calls === 0`）；octet-stream/timeout 0/一次编码（`%26` 且无 `%2526`）/串行；进度可计量显示 `2 KiB / 4 KiB` 与 `value=50`、不可计量事件不跳 processing、100% 文案且未 201 不显示成功。
- 状态机：201（success+刷新+继续下一个+禁止移除/重发）、201 但 JSON 不可解析→uncertain、409（failed+同名提示+暂停+显式重试）、429/507/413/415（pending/failed + 固定文案 + 暂停）、断网 0 字节→pending“未开始”、超时已发送→uncertain（不自动重传）、取消（abort 恰好一次；已发送→uncertain、未发送→cancelled）、pending 移除只影响队列、活动期间关闭禁用/Escape 阻止并提示/取消后焦点归还。
- 鉴权与播放：401 → `handleAuthResponse` 收到 `status 401 + Content-Type: application/json + 原 body` 且停队列；status 0 不构造 Response（StubResponse 对 status 0 抛错，断言不抛且未交给 auth）；播放只设白名单 previewUrl、一次一个播放器、非白名单不设 src、编码失败提示+下载仍在、协议相对 downloadUrl 被拒。
- 改名与生命周期：预填主体+扩展只读+原始文件名、PATCH URL 带 expertId 且 body=`{nameStem, expectedVersion:4}`、409 保留输入且只发 1 次 PATCH、成功后列表项更新且 URL 不变、Escape 焦点归还；卸载（切专家）abort 在途上传+关两个 dialog+停队列+清播放器 src/load+清 File 引用+晚回调不重发；晚列表响应不写新宿主。

## 6. 实现中的设计取舍（供核实）

1. S-1 上传弹窗**无 `<form>`**（照抄计划骨架），开始上传由 `[data-ec-video-start]` 触发；重命名弹窗沿用 02 `<form>` 骨架。
2. 队列行**只有一个动作按钮**（同 S-1 骨架）：pending=「移除」、活动=「取消」、failed/uncertain=「重试」、success=隐藏 → 满足“pending 可改名移除 / 活动可取消 / success 禁止重发移除 / 失败显式重试”。
3. 需要挂载期定位的两个 dialog 各加 1 个 hook 属性（`data-ec-video-upload-dialog`、`data-ec-video-rename-dialog`），其余 class/tag 与 S-1 逐字一致；测试断言无 inline style、section 无 id。
4. 重命名弹窗 label 采用唯一 `id`/`for`（`ec-video-rename-name-stem-<mountSeq>`），扩展名永远是 `span`（不可编辑）。
5. 重命名成功后沿用 02 语义：弹窗保持打开、`.em-upload-summary` 显示「已保存」、列表项就地更新（不做自动关闭）。
6. section 的 `.ec-message` 是 S-1 的静态上限提示（`role=status` 初值逐字保留），不当作状态槽；运行反馈走 dialog 的 `.ec-upload-error/.em-upload-summary` 与 section 的 `.ec-error`（失败+刷新按钮仍可重试）。
7. `level` 仅在 02 校验值非空时随请求发送（缺省由服务端按 RAW）。

## 7. 偏差与跨计划冲突（需人工/控制面处理）

**唯一未达成项**：brief 要求「02/03 静态契约测试必须保持全绿」，实测 **03 静态键 pin 与 05 计划要求的 11 键 bump 相互冲突**：

- 断言位置：`src/test/js/expertContactSharedNote.test.js`（`缓存键：index.html 11 项统一为 03 计划键…`）→ `CACHE_KEY === PLANNED_CACHE_KEY`，其中 `PLANNED_CACHE_KEY` 从 `docs/plans/2026-10-11/contact-profile-03-notes.md:59` 解析（当前字面量 `20261011-contact-profile-03`，文件 SHA-256 `9e5cbc3c…`）。
  - 失败原文（实测，`expertContactSharedNote.test.js:766`）：`AssertionError: index.html must carry the key required by the 03 plan` / `+ actual '20261011-contact-profile-05'` / `- expected '20261011-contact-profile-03'`。
- 该测试文件与 03 计划均**不在**本子计划授权的 4 个文件内且被 brief 明令禁改 → 子计划内无法修复。
- 这是与 A1/A2 完全同类的问题（R1「计划字面量随阶段键同步」口径），只是这次是 03 计划字面量（R1 批准文本只覆盖 02 计划字面量）。
- 已按 controller 指令 HOLD：`contact-profile-03-notes.md:59` 的字面量同步=controller-side 修正（A4 类），需落在实现提交之前。
- 我的实现提交 `b94bd28` 已在收到 HOLD 之前创建（HOLD 到达时提交已完成；未 push）。若需满足「计划修改先于实现提交」的排序，请授权 `git reset --soft HEAD~1`（仅撤销我这一个本地提交、保留全部改动），随后按最终态重跑命令并重新提交 `feat(fast-p): implement 05`。
- 结论：本子计划在授权范围内的全部交付物已完成并有实测证据（§1–§6、§8）；唯一未达成项（§7 第一段）要求修改授权外文件 → 本子计划返回 **PLAN_CONFLICT**，待控制面/人工按 A/B 口径处置后再进入 verify-p。

其它偏差：无。`docs/` 下仅新增本报告（未提交）；工作区无未跟踪残留（`git status --porcelain` 干净）。

## 8. 下游事实（供 verify-p / 后续验收直接引用）

- app.js 05 区域标记：`/* contact-profile-05-video:begin */ … /* contact-profile-05-video:end */`（位于 03 区域之后、`showExpertDetail` 之前）。
- 关键选择器：`[data-role="expert-meeting-videos"]`（section）、`[data-ec-video-jump|refresh|upload|prev|next|start|cancel|remove|play|rename|download]`、`[data-ec-video-upload-dialog]`、`[data-ec-video-rename-dialog]`、`[data-role="video-name"|"video-meta"|"original-name"|"name-stem"|"extension"]`、`.em-upload-row[data-upload-state]`。
- 固定文案：`结果未确认，请刷新视频列表`、`上传完成，正在保存`、`已上传`、`暂无会议视频`、`浏览器不支持此视频编码，请下载观看`、`请先取消当前上传，再关闭窗口`、`可取消；断网需重新上传`。
- 上传请求：`POST ${contextPath}/api/experts/meeting-videos?expertId&[level]&originalName&nameStem&expectedSize`，`Content-Type: application/octet-stream`，body=原始 File；`XHR.timeout=0`。
- 内存/网络契约：不构造 FormData/Blob，不 fetch 整段视频，只有 04 的 `previewUrl/downloadUrl` 会进 `video.src`/`href`。

## 9. 遗留风险 / 未执行项

1. §7 的 03 键 pin（阻塞「全量绿」；不影响 05 实现本身）。
2. 未执行：真实浏览器 1366/390px 目测与 A-1–A-5 人工验收项（计划要求人工阶段执行）；本机未做。
3. 未执行：`mvn test`（前端 JS 用例经 `exec-maven-plugin` 绑在 test 阶段，但本 brief/计划要求的命令是上述 node 命令）；未跑全量 Maven 构建。注意：pom 绑定的正是 `node --test src/test/js/*.test.js`，因此在这些前端用例上下文中 `mvn test` 会继承同一个 03 键 pin 失败——03 计划字面量同步（§7）是任何“全量绿 / mvn test 绿”结论的前置条件。
4. 未执行：04 的真实 HTTP / 1 GiB / Range 后端验收（属 04 A-1/A-3；05 侧只消费其契约）。
5. 焦点归还仅在列表未重绘时生效（重命名成功后列表重绘，旧触发节点已断连 → 安全跳过，不再聚焦）。
6. 断网/取消的“服务端是否已入库”仍以刷新列表为准（设计如此，不承诺撤销）。

## 10. Epoch 2（A4 落地后的重锚与最终态证据）

- 控制面处置（人工批准的 Option A，一次性）：`git reset --soft 52db510` 撤销了我 epoch 1 的实现提交 `b94bd28` 与随后 controller 的 `5a06ecc docs(fast-p): pause 05`（内容零改动、未 push、无已提交证据引用过二者）；A4 修正落地为 `a8a7b88 docs(plans): A4 sync 03 cache-key literal to 05 stage value`（`contact-profile-03-notes.md:59` 字面量同步为 `20261011-contact-profile-05`），其上为 `3da1126 docs(fast-p): record amendment A4 and resume 05 epoch 2`。
- 内容等价性（重锚无损）证明——4 个文件在 epoch 2 工作区的 `git hash-object` 与 epoch 1 提交内的 blob 完全一致：

| 文件 | blob SHA（epoch 1 == epoch 2） |
|---|---|
| `src/main/resources/static/app.js` | `c663b3cd5c10130a943c7b1d879c879e9b91f503` |
| `src/main/resources/static/index.html` | `7192fbec8d7b309918ecb27df904354e61d60bc3` |
| `src/main/resources/static/styles.css` | `648baf037dd4697c822086bc69e35c92b020d1a9` |
| `src/test/js/expertMeetingVideo.test.js` | `b3408bc64320d861d0d00852f65ade9a5d11e539` |

- 最终实现提交：`0e4b5a89507e03350cddb883f7266c494317e25f` = `feat(fast-p): implement 05`（`4 files changed, 3045 insertions(+), 12 deletions(-)`），父提交为 `3da1126`，即 **A4（`a8a7b88`）先于实现提交** 的排序要求已满足；只 stage 了 4 个授权文件，`docs/`（ledger 与本报告）未被提交；未 push / 未 amend / 未 rebase。
- 必需命令（epoch 2 冻结最终态，全新运行）：

| 命令 | 结果 |
|---|---|
| `node --test src/test/js/expertMeetingVideo.test.js` | **PASS** exit 0；`tests 25 / suites 5 / pass 25 / fail 0` |
| `node --test src/test/js/*.test.js` | **PASS** exit 0；`tests 1641 / suites 315 / pass 1641 / fail 0`（A2+A4 同步后 02/03 键 pin 全绿，全量无失败） |
| `node --check src/main/resources/static/app.js` | **PASS** exit 0 |

- 结论：§7 的跨计划冲突已由 A4 消除；本子计划在授权范围内的全部交付物完成且全量前端回归绿 → 返回 **READY_FOR_VERIFICATION**（epoch 1 的 §7 记录保留为历史证据）。
- Epoch 2 剩余风险（与此前 §9 合并后仍有效）：真实浏览器 1366/390px 目测与 A-1–A-5 人工验收未执行；未跑全量 `mvn test`（其绑定的 `node --test src/test/js/*.test.js` 现已全绿，但 Maven/Kotlin 编译与后端用例未在本子计划内运行）；04 的真实 HTTP / 1 GiB / Range 后端验收未跑（属 04 A-1/A-3）；焦点归还仅在列表未重绘时生效（重命名成功后列表重绘、旧触发器断连 → 安全跳过）；断网/取消的服务端结果始终以刷新列表为准，不承诺撤销。
