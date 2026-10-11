# Child 02 — Execution Log

（epoch 1 执行结果，由 implementer 追加；本文件不进入实现提交，由控制器单独提交证据。）

## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master/docs/plans/2026-10-11/contact-profile-02-ui.md
Plan SHA-256: f7b483a65f8cdcb9a31ca6939efe7cc6f9cd3c70dd45edf608089e8eb8646245
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master/docs/plans/2026-10-11/contact-profile-02-ui.md@f7b483a65f8cdcb9a31ca6939efe7cc6f9cd3c70dd45edf608089e8eb8646245
Execution epoch: NEW
Approval basis: 本次调用（child 02 brief：`docs/plans/fast/2026-10-11-contact-profile-development-master/children/02/brief.md`）+ 已批准的 02 子计划 + master 计划（I-5 串行门槛：01 已交付/验证）
Executor: Impl02（fast-p child 02）
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master
Target branch: fast/2026-10-11-contact-profile-development-master
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master@fast/2026-10-11-contact-profile-development-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master
Pre-execution code SHA: 02ae8b54273411a9b07e7f7d13d71bddbdf1a255（= child_base_sha bd7053979bea2cc123c58ffb070abf7092917e98 + 两个 docs 提交）
Post-execution code SHA: f7eb96bd6408efd0d9e538ff5ff81e78b1633ecb
Evidence HEAD: f7eb96bd6408efd0d9e538ff5ff81e78b1633ecb（本子计划只有一次实现提交，无独立 evidence 提交）
Implementation boundary: 02ae8b5..f7eb96b（4 个文件，+2515/-11）

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| I-1 双宿主同契约（showExpertDetail / loadContactDetail 共用 render/mount；19 项；不新增第二份资料来源） | IMPLEMENTED | app.js | 两个 `data-panel="contact"` 最前面各插入 `<div class="ec-profile" data-role="expert-manual-profile"></div>`；两处均调用同一 `mountExpertManualProfile(contactDetail, context)`；测试真实执行两条宿主并断言 context 键集一致、expertId/contactId/level/name/nationality 各自正确、GET URL 各自正确 |
| I-2 请求与节点生命周期（捕获 expertId/contactId/generation/宿主；await 后校验；切人清理；保存不被中止） | IMPLEMENTED | app.js | `createExpertManualProfileSession` 捕获四要素；`session.isStale()`（closed / `host.isConnected === false` / `isCurrentContactDetailPresentation(generation)`）在每次 await 后判定；`unmountExpertManualProfilePanels()` 接入 `beginContactDetailPresentation` 与 `returnToMobileContactsList`（abort 读请求、关闭弹窗、注销会话）；PATCH 不挂 abort；同会话 `readSeq` 防旧读覆盖新读 |
| I-3 保存与错误（按组白名单 + expectedVersion；仅 200 更新；400/409/401/503；GET 失败重试且禁编辑；取消/Escape 不发 PATCH） | IMPLEMENTED | app.js | `expertManualProfileCollectValues`（组白名单键集 + 空白=清空 + MATCHED 至少一家 + 01 长度/日期边界）、`expertManualProfileSubmit`（saving 守卫、200 后渲染/刷新 version、错误保留输入）、`expertManualProfileErrorMessage`（409 固定文案、401 登录失效、503 服务端消息、网络错误=结果未知）；测试覆盖 200/400/409/401/503/网络、重试、Escape/取消/关闭、双击 |
| I-4 既有字段与凭证（姓名 displayName/expertName；国籍只用 nationality；keyword 只读；证件末四位；textContent） | IMPLEMENTED | app.js | 只读卡片 `data-ec-readonly="expertName|nationality|keyword"` + `data-ec-value`；`expertManualProfileMaskDocument`（≤4 位全遮罩）；编辑弹窗显原文、PATCH 提交原文；全部值经 `textContent` / `.value` 写入；测试含恶意 HTML、mask、country≠nationality |
| S-1 宿主与复用组件（nav/ec-section/ec-heading/metadata-*/ec-message/ec-error/dialog.em-upload-dialog.ec-editor；组顺序 matching/identity/education/technology） | IMPLEMENTED | app.js | 骨架模板函数 `expertManualProfileShellTemplate/SectionTemplate/EditorTemplate`；测试断言四组顺序、四 section、四 dialog、19 项 + 3 项只读各有一个展示位与（除只读外）一个编辑控件、长文本卡片 span-all / label ec-span、maxlength 来自 01、企业「每行一家」、六档+未评估、进度三选一、只用契约标签/class、无 id、无 inline style |
| S-2 唯一新增 CSS（逐字追加） | IMPLEMENTED | styles.css | 计划 ```css 代码块逐字追加在文件末尾（唯一一次出现，其后无其它规则）；测试断言逐字、末尾、每行存在、class 均已声明 |
| 缓存键（index.html 原 11 个 `?v=` 统一为 `20261011-contact-profile-02`，不增资产/不改顺序） | IMPLEMENTED | index.html | 11 个键同值；11 项资源顺序与数量不变；`task-modal-runtime.js` 仍无版本键 |
| 测试文件（新建：真实节点、交互、异步与契约） | IMPLEMENTED | src/test/js/expertManualProfile.test.js | 24 个用例 / 6 suite 全绿，真实解析 innerHTML 的小型 DOM + 可控延迟 fetch |

### Commands

| Command | Result | Evidence |
|---|---|---|
| `node --test src/test/js/expertManualProfile.test.js` | PASS | exit 0；tests 24 / suites 6 / pass 24 / fail 0 / cancelled 0 / skipped 0 / todo 0 |
| `node --test src/test/js/*.test.js` | PASS | exit 0；tests 1592 / suites 304 / pass 1592 / fail 0 / cancelled 0 / skipped 0 / todo 0 |
| `node --check src/main/resources/static/app.js` | PASS | exit 0（语法门禁） |
| 基线复核（临时目录 `/tmp/cp02-baseline`：HEAD 版 app.js/styles.css/index.html + 删除新增测试文件） | PASS | exit 0；tests 1568 / pass 1568 / fail 0 —— 与 brief 基线一致，且 1592 − 24 = 1568 说明无既有用例回归 |

### Changed Files

- `src/main/resources/static/app.js` — 02 区域（`/* contact-profile-02-ui:begin */` … `:end */`，695 行）：19 项字段元数据、显示/遮罩/企业规范化、S-1 骨架模板、会话与生命周期（mount/unmount/isStale/readSeq）、加载与重试、按组保存与错误映射、事件绑定；两个宿主插入 profile 宿主并调用同一挂载入口；`beginContactDetailPresentation` / `returnToMobileContactsList` 接入统一销毁。
- `src/main/resources/static/styles.css` — 仅追加计划 S-2 的 18 行规则（+18/-0），未改动任何既有规则。
- `src/main/resources/static/index.html` — 11 个 `?v=` 键统一为 `20261011-contact-profile-02`（+11/-11），资源数量与顺序不变。
- `src/test/js/expertManualProfile.test.js` — 新建 24 用例：契约/静态门禁、S-1 骨架、I-1 双宿主、I-2 生命周期、I-3 加载与保存、I-4 字段与凭证边界。

### Deviations

1. **两处宿主调用加了 `typeof mountExpertManualProfile === "function"` 守卫**（app.js 两处，附注释）：本仓既有测试（`mobileCoreNavigation.test.js`、`expertMaterialsShared.test.js`、`expertProfileAbsence.test.js`）用 `extractFn` 把 `showExpertDetail`/`loadContactDetail` 抽到沙箱执行且不提供新函数，裸调用会抛 `ReferenceError`（实测 12 个既有用例失败）。守卫沿用本文件既有约定（`unmountExpertMaterialsHosts` / `mountExpertMaterialsInline` 同样带 `typeof` 守卫），生产路径（同文件定义）恒定调用，I-1 的「同一挂载入口」不变；未修改任何非授权文件。
2. **测试不固化缓存键字面量**：键从 `index.html`（`styles.css?v=`）动态取，计划目标值从 02 计划正文 `统一(更新)为 \`…\`` 解析。原因：`taskActivityCenter.test.js` 的 I-8 断言「键字面量只允许出现在 index.html」，实测新测试文件写死字面量会让该用例失败（K-frontend-cache-key-triad）。
3. **新增 `data-ec-editor` / `data-ec-retry` / `data-ec-readonly` 三个 data 属性**（计划只逐字给出 `data-ec-jump|edit|value|group|cancel`）：同属计划的 `data-ec-*` 挂钩惯例，用于稳定定位弹窗、运行期重试按钮与只读卡片；未新增 class、未新增标签类型。
4. **失败重试按钮的位置**：计划只说「失败重试是同 `.button`」，未指定位置；实现为「GET 失败时在每组 section 的 `.ec-error` 内用 DOM API 追加一个 `.button[data-ec-retry]`，成功加载后移除」，四组各自可重试（同一 GET）。
5. **同会话读序号 `readSeq`（超出 I-2 字面）**：重复触发重试/重读时先 abort 旧读并丢弃旧响应，避免旧慢响应覆盖新结果（I-2 同理延伸）；已加用例「同一会话内重复读取：旧读响应不得覆盖新读结果」。
6. **level 传递策略**：GET/PATCH 只在 level ∈ {RAW,CANDIDATE,APPLICATION} 时携带，否则省略（服务端缺省 RAW）；避免把列表页文案/未知值当层级上报导致 400。无 contact 宿主也按 I-2 传 `contactId: String(expert.contactId ?? "")`（通常为空串）。

### 下游接口事实（03/05 复用）

- **区域标记**：`app.js` 内 `/* contact-profile-02-ui:begin */` … `/* contact-profile-02-ui:end */`；测试用同一标记切片执行（新增代码请落在区域内）。
- **共用挂载入口**：`mountExpertManualProfile(rootEl, context)` —— `rootEl` 为 contact 容器（innerHTML 写入后调用），返回 session；context 键集固定 `{expertId, level, contactId, name, nationality, keyword, generation}`（03/05 追加键需同步 02 测试的键集断言）。
- **卸载/生命周期**：`unmountExpertManualProfilePanels(rootEl?)`（无参=全部；带容器=容器内 + 已断连宿主），已接入 `beginContactDetailPresentation` / `returnToMobileContactsList`。
- **会话字段**：`host / generation / expertId / level / contactId / facts{expertName,nationality,keyword} / profile / version / loaded / saving / closed / readSeq / ready / editors(Map group→editor) / controllers(Set)`；守卫统一走 `session.isStale()`；`session.editors.get(group).section` 即该组 `section.ec-section[data-ec-group]`。
- **插入点**：`ec-profile` 是 `data-panel="contact"` 的第一个子节点，其内容顺序为 `nav.ec-nav` → 4 个 `section.ec-section`（matching/identity/education/technology）→ 4 个 `dialog.em-upload-dialog.ec-editor`。03 备注 section 可插在 `nav` 之前；05 视频 section 可插在第 4 个 section 之后、弹窗之前（或之后）。
- **弹窗骨架**：`dialog.em-upload-dialog.ec-editor[data-ec-editor=<group>][aria-label=编辑<组名>] > form > header.em-upload-head(.em-upload-title>h3+.em-upload-subtitle, button.modal-close-btn) + div.em-upload-body(> div.ec-edit-grid > label[.ec-span] > input/textarea/select[+option], span.ec-error) + footer.em-upload-actions(span.em-upload-summary + button.button[data-ec-cancel] + button.button.primary[type=submit])`。
- **状态位约定**：每组 `span.ec-message[role=status][aria-live=polite]` 承载「已保存」；`span.ec-error[role=alert]` 承载错误（`textContent` 写文本、`hidden` 切换、需要重试时用 `document.createElement("button").className = "button"` + `data-ec-retry` 追加，恢复时 `remove()`）。
- **S-2 class 清单（可复用）**：`ec-profile / ec-section / ec-heading / ec-actions / ec-nav / ec-text / ec-message / ec-error / ec-edit-grid / ec-span / ec-editor`（其余复用 `metadata-grid|metadata-card|metadata-card-header|metadata-card-value|span-all|button|primary|em-upload-*|modal-close-btn`）。
- **测试脚手架**：`src/test/js/expertManualProfile.test.js` 内的小型 DOM（innerHTML 解析 + 选择器 + 事件冒泡 + dataset + dialog showModal/close）与 `createRouter()` 可控延迟 fetch 可直接复制给 03/05 的单文件测试（本仓惯例为独立副本）。

### Freshness

- Plan identity rechecked: YES（执行前后同为 f7b483a…，未变化）
- Worktree identity rechecked: YES（提交前后 `--expect-root/--expect-branch/--expect-git-dir` 一致）
- Reported commits reachable from target branch: YES（f7eb96b 为 HEAD，`git merge-base --is-ancestor` 通过）
- Required commands run this invocation: YES（三条命令均在最终代码状态上重跑，计数见上）
- Historical evidence used only as baseline: YES（brief 的 1568 基线用独立临时目录复核，不替代任何本次命令）

### 影响面 / 保持项核对

- 保持：左右分栏、四个原标签与默认激活标签、列表筛选、原邮件/会议/材料操作与旧 materials 上传、ES 权威来源、`showExpertDetail` 默认 academic、`loadContactDetail` 默认 contact —— 均未改动（diff 仅 7 个 hunk：2 处生命周期 + 1 个模块区 + 2 处宿主节点 + 2 处宿主挂载）。
- 范围外未做：Excel 导入、新导航页、视频/备注写入（03/05）、后端 01 改动、`docs/plans/fast/**` 提交。
- 未提交产物：本报告（`docs/plans/fast/.../children/02/execution.md`）保持未跟踪；工作区除它之外干净（`git status --porcelain` 仅此一项）。

### Remaining Blocker

- None（无阻塞；下述为待人工/后续完成的验证项，不是执行阻塞）。

### Remaining Risks（未验证项）

1. **无真实浏览器验收**：02 计划的 A-1–A-5（1366px/390px 溢出、焦点可见性、缓存键实际生效、双窗口 409、四种原标签回归）尚未执行；DOM 夹具通过不能替代浏览器（计划 §验收标准明确要求）。
2. **未运行 `mvn test`**：02 的白名单/必备命令只含 3 条 node 命令；`pom.xml` 的 exec-maven-plugin 会在 `mvn test` 的 test 阶段跑同一批 JS 用例（新文件已通过 `node --test`，但其在 Maven 阶段的绑定未实测）。
3. `nationality`/`keyword` 依赖列表响应中的 ES 专家行（`state.contacts`）；深链接打开详情且该行不在列表页时只读卡片显示「未填写」（符合 I-4「缺失不得拿 country 代替」，属 UX 表现）。
4. 证件遮罩按 Unicode code point 计数（含代理对）；如需按 UTF-16 计数的确切视觉长度需另议（当前实现与 01 的 code point 语义一致）。
5. GET 失败时四个分组各出现一个「重试」按钮（同一次 GET 重载），属实现选择（计划未指定位置）；若验收偏好单一入口需在 03/05 前调整。
6. 关键源文件哈希在 01 审计后未变（本次仅前端 4 文件），未重跑 01 的 Kotlin 侧核对。

### Next Action

- READY_FOR_VERIFICATION → run `verify-p`
