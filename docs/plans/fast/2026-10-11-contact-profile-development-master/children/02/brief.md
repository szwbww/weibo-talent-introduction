# Child Brief — 02 (ui)

- Child ID: `02`；执行 epoch 1。
- Authoritative child plan: `docs/plans/2026-10-11/contact-profile-02-ui.md`（此 worktree 内已提交；**先完整阅读它，它是唯一权威合同**）。
- Master plan（全局合同）: `docs/plans/2026-10-11/contact-profile-development-master.md`（保持项/范围外/执行门槛）。
- child_base_sha: `bd7053979bea2cc123c58ffb070abf7092917e98`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master`
- Branch: `fast/2026-10-11-contact-profile-development-master`
- 要求使用 `execute-p` 技能执行本子计划。

## Authorized files（精确 4 个；其余文件一律不得修改）

| # | 文件 | 操作 |
|---|---|---|
| 1 | `src/main/resources/static/app.js` | 修改：两个宿主、分组展示/编辑及清理 |
| 2 | `src/main/resources/static/styles.css` | 修改：只追加契约 CSS（S-2 逐字） |
| 3 | `src/main/resources/static/index.html` | 修改：原 11 项资源统一缓存键 `20261011-contact-profile-02` |
| 4 | `src/test/js/expertManualProfile.test.js` | 新建：真实节点、交互、异步与契约测试 |

## Required commands（全部必须真实运行并记录精确计数/退出码）

- `node --test src/test/js/expertManualProfile.test.js`
- `node --test src/test/js/*.test.js`（全量回归；基线 tests 1568 / pass 1568 / fail 0 / exit 0）
- `node --check src/main/resources/static/app.js`（语法门禁；本仓前端 JS 用例还有 exec-maven-plugin 绑定，语法检查同样适用）

## 上游 child 01 已交付并验证的接口事实（02 必须按此对接）

- `GET /api/experts/manual-profile?expertId=…&level=RAW`（登录会话必需）返回顶层恰好 8 键：
  `{expertId, version, identity{5键}, education{8键}, technology{3键}, matching{3键}, updatedBy, updatedAt}`；
  无行时 `version=0`、`companyMatchStatus:"NOT_MATCHED"`、`matchedCompanies:[]`（空数组对应库 NULL，不用空串），**GET 不插入**。
  `updatedAt` 为服务端 Asia/Shanghai 毫秒 `yyyy-MM-dd'T'HH:mm:ss.SSSXXX`。
- `PATCH /api/experts/manual-profile` body `{expertId, level, group, expectedVersion, values}`：
  `group ∈ identity|education|technology|matching`（大小写敏感）；`values` 必须恰好包含该组全部键（缺/多 400）；
  `null`/空白=清空（`companyMatchStatus` 例外：空白→`NOT_MATCHED`）；成功 200 返回完整最新 DTO。
- 错误：401/400/404/409（`PROFILE_CONFLICT`，message 固定「资料已被更新，请重新加载后再编辑」）/503/500，响应恒 `{code,message,detail}`。409 时不自动覆盖重试。
- `documentNumber`：GET 返回持久化原文（保留前导零）。**遮罩是 02 的展示职责**：详情非编辑态仅显示末四位（≤4 位全遮罩 `•`）；编辑弹窗内显明文；PATCH 必须提交原文，绝不能用遮罩值回写。
- `level` 可省略（缺省 RAW），只接受 `RAW|CANDIDATE|APPLICATION`。
- 02 不调用 01 的服务端代码；只消费上述 HTTP 合同。

## 必须兑现的合同要点（以子计划正文为准，这里是标识索引）

- I-1 双宿主同契约：`showExpertDetail`（app.js:11213，无 contact 分支，默认 academic）与 `loadContactDetail`（app.js:11656，有 contact 分支，默认 contact）都调用同一个 render/mount，不能只改一条路径；复用 01 全部字段 I-5–I-23，不新增第二份资料来源。
- I-2 请求与节点生命周期：每次挂载捕获 expertId、contactId、generation、宿主引用；GET/PATCH 每次 await 后检查 generation、宿主连接性与 key；切专家/返回列表清理监听、关闭弹窗、中止读请求；已发出的保存不能因 abort 被声称回滚，下次 GET 确认。
- I-3 保存与错误：按组提交全部白名单字段及 expectedVersion；只收到 200 后更新显示；400 保留输入；409 提示「资料已被更新，请重新加载后再编辑」且不自动覆盖重试；GET 失败显示重试且禁编辑，不把失败当空档案；取消或 Escape 不发 PATCH。
- I-4 既有字段与凭证：姓名用现有 displayName/联系人 expertName；国籍只用 nationality（缺失不得拿 country 代替）；keyword 只读；country/当前机构/阶段保留原区域；documentNumber 仅编辑弹窗显明文、详情遮罩；用户文本用 textContent 或 escapeHtml，禁止原样 innerHTML。
- S-1 宿主与复用组件：两个 `data-panel="contact"` 内最前面各插入 `<div class="ec-profile" data-role="expert-manual-profile"></div>`，保留其后所有原有节点/事件；动态内容按 S-1 逐字结构（nav/ec-section/ec-heading/metadata-*/ec-message/ec-error/dialog.em-upload-dialog.ec-editor 等）；nav 四按钮按 matching/identity/education/technology 跳转对应组，不增顶层标签，组顺序同此；姓名/国籍/keyword 在 identity 用同一卡片只读；长文本卡片加 span-all，输入 label 加 ec-span；多企业 textarea 每行一家；科研能力 select 六档加「未评估」（null）；进度三选一；弹窗 showModal、焦点归还触发按钮、Escape 取消；保存期间禁保存与关闭。
- S-2 唯一新增 CSS：逐字追加所列规则；禁止 inline style、未声明 class、修改既有 class。
- 缓存键：`index.html` 原 **11 个 `?v=`** 统一为 `20261011-contact-profile-02`（当前全部为 `20261008-mailbox-server-drafts`）；不增加 script/link 资产，保持原 11 项资源顺序与数量。既有前端测试从 index.html 动态提取键并断言三件套一致，不得写死字面量。
- 计划 §4：`expertManualProfile.test.js` 用真实挂载 DOM 与可控延迟请求覆盖 I-1–I-4/S-1/S-2（含：两宿主实际构造、19 项出现且可保存、慢响应/切人隔离、200/400/409/401/503、请求失败重试、取消/Escape、双击至多一个 PATCH、HTML 转义、证件末四位）。
- 仓库约定：本仓 JS 测试用 DOM stub，`document.getElementById` 永远返回元素；若新增「按 id 取元素再写入」的渲染函数，测试必须额外断言该 id 出现在真实 `index.html` 源文本中。CSS 逐字契约用 Node 测试比对 `styles.css` 源文本时按既有约定（见 styles-baseline 的证据格式）。

## 保持项与范围外（不得触碰）

- 左右分栏、四个原标签、默认激活标签、列表筛选、原邮件/会议/材料操作与旧 materials 上传不变；不改变原字段权威来源（ES 响应）。
- 不改 app.js 中与本次无关的既有函数/接口；不新增导航页；不做 Excel 导入、视频与备注写入（那是 03/05）。
- 01 后端不修改；`docs/plans/fast/**` 不提交（仅按报告路径写入 execution.md，不提交它）；不新增授权外文件；工作区不留未跟踪残留文件。
- 本地提交恰好一次：`feat(fast-p): implement 02`（不得 push/merge/rebase/amend/squash）。

## Downstream interfaces（后续子计划依赖）

- 03 在 `ec-profile` 最前面（nav 之前）插入备注 section，复用 02 的 S-1/S-2 的 `ec-section/ec-heading/ec-text/ec-message/ec-error` 与 02 的挂载/generation/key/contactId 守卫。
- 05 在四组档案后、原邮件/材料区域前插入视频 section，复用 02 的 `ec-section/ec-action*` 与 `.em-upload-dialog`/`.ec-editor` 骨架、`.button` 及 02 的挂载生命周期。
- 因此 02 必须让「共用挂载函数 + generation/key 守卫 + ec-profile 插入点 + ec-editor 弹窗骨架 + S-2 class 清单」稳定可复用（后续子计划只改 app.js 内同一区域）。

## Report

- 报告路径：`docs/plans/fast/2026-10-11-contact-profile-development-master/children/02/execution.md`（写入完整结果：改动文件、命令与结果、偏差、下游接口事实、遗留风险；不提交）。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径（紧凑 JSON）。
