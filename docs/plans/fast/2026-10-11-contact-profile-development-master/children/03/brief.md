# Child Brief — 03 (notes)

- Child ID: `03`；执行 epoch 1。
- Authoritative child plan: `docs/plans/2026-10-11/contact-profile-03-notes.md`（此 worktree 内已提交；**先完整阅读它，它是唯一权威合同**）。
- Master plan（全局合同）: `docs/plans/2026-10-11/contact-profile-development-master.md`。
- child_base_sha: `f7eb96bd6408efd0d9e538ff5ff81e78b1633ecb`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master`
- Branch: `fast/2026-10-11-contact-profile-development-master`
- 要求使用 `execute-p` 技能执行本子计划。

## Authorized files（精确 3 个；其余文件一律不得修改）

| # | 文件 | 操作 |
|---|---|---|
| 1 | `src/main/resources/static/app.js` | 修改：共用详情内共享备注只读读取/刷新 |
| 2 | `src/main/resources/static/index.html` | 修改：11 项资源缓存键同步 `20261011-contact-profile-03` |
| 3 | `src/test/js/expertContactSharedNote.test.js` | 新建：备注归属、错误与隔离 |

## Required commands（全部必须真实运行并记录精确计数/退出码）

- `node --test src/test/js/expertContactSharedNote.test.js src/test/js/expertManualProfile.test.js`
- `node --test src/test/js/*.test.js`（全量回归；当前基线 tests 1592 / pass 1592 / fail 0 / exit 0）
- `node --check src/main/resources/static/app.js`

## 上游 child 02 已交付并验证的集成事实（03 必须在其上工作）

- 共用挂载入口：`mountExpertManualProfile(rootEl, context)`（`rootEl` = contact 容器；context 键集 `{expertId, level, contactId, name, nationality, keyword, generation}`）；区域标记 `/* contact-profile-02-ui:begin */ … :end */`；新增代码请落在自己的区域并遵守既有切分惯例。
- `ec-profile` 宿主是 `data-panel="contact"` 的第一个子节点；其内容顺序：`nav.ec-nav` → 4 个 `section.ec-section`（matching/identity/education/technology）→ 4 个 `dialog.em-upload-dialog.ec-editor`。**03 的备注 section 插在 `nav` 之前**（02 报告已确认该插入点）。
- 会话与守卫：`session.isStale()`（closed / `host.isConnected === false` / `isCurrentContactDetailPresentation(generation)`）+ `readSeq` 防陈旧响应；`unmountExpertManualProfilePanels(rootEl?)` 已接入 `beginContactDetailPresentation` / `returnToMobileContactsList`。03 必须沿用同一 generation+key+contactId 守卫。
- S-2 已声明并可复用：`.ec-section/.ec-heading/.ec-text/.ec-message/.ec-error/.ec-actions`；`.button` 在 styles.css:802。**03 无新增 CSS、不改共享 class**。

## 已知跨计划约束（来自 child 02 轻量验证的 RECORD_ONLY O-1，必须遵守）

- `src/test/js/expertManualProfile.test.js`（02 的静态契约测试）断言：ec-profile 内**恰好 4 个 `.ec-section`、4 个 `.ec-editor`、nav 恰好 4 个按钮**，且 02 的 S-2 CSS 块是 `styles.css` 的**末尾**内容。
- 该文件 **不在你的授权范围内，禁止修改**；且它被你的 plan 列为 required command 的一部分，必须保持通过。
- 兼容方式（本仓库/本文件既有约定）：对「在宿主/02 区域调用 03 新函数」的调用点使用 `typeof mountExpertContactSharedNote === "function"` 式守卫（02 对其宿主集成即用此法，既有先例 `unmountExpertMaterialsHosts`/`mountExpertMaterialsInline`），使切片式既有测试在未定义新函数时可安全跳过；真实页面中函数存在则正常挂载。03 不新增 `.ec-section` 计数（备注 section 会+1）——**因此绝不能把新 section 无条件挂进 02 测试可达的挂载路径**；若无论如何都无法同时满足你的 plan 与 02 测试，**报告 PLAN_CONFLICT 并停止，不要编辑该测试文件**。

## 必须兑现的合同要点（以子计划正文为准，这里是标识索引）

- I-1 单一来源：仅 `GET /api/mail/contact-notes/{contactId}`；本页**无 PUT、无 localStorage 副本、无专家级 note 列**；真实 contactId 为空不请求、不新建联系人。
- I-2 状态与错误五态：加载中 / 成功有内容 / 成功空内容（显示「暂无备注」）/ 失败（显示「备注加载失败，请重试」，**不得显示无备注**）/ 无联系人（显示「尚无收发件箱备注」且不请求）。失败不阻断四组编辑与旧材料。
- I-3 隔离与编码：沿用 02 的 generation+key+contactId 守卫；文本转义（textContent）且换行可见（`.ec-text` 已有 `white-space: pre-wrap`）；每次进入详情读取、手动刷新再读；不混合相同 expertId 不同 contactId 记录。
- S-1：无新增 CSS；按 plan 逐字插入 section 结构（`data-role="expert-shared-note"`、`data-ec-refresh-note`、`data-role="note-content"`、`data-role="note-meta"`）；无 contact 隐藏刷新按钮；加载中禁用刷新；无新图标、inline style、其它 DOM 或 class。
- §实现方案：独立加载备注，**不加入使全部详情一起失败的 Promise.all**；刷新按钮触发一次 GET，仅更新该 section；400/404 显示不可读取而非清空；401 走既有登录处理；失败重试沿用刷新按钮。
- 缓存键：`index.html` 原 **11 个 `?v=`** 统一为 `20261011-contact-profile-03`（不得写死字面量进测试；既有测试从 index.html 动态提取，K-frontend-cache-key-triad）。
- 保持项：收发件箱编辑入口与 2000 字限制、按联系人归属、原档案编辑均不变；不新增表/列（无 schema 变更）。

## 其它约束

- 不改 01/02 已交付行为（19 项档案的显示/编辑/生命周期）；不触碰 04/05 范围（视频）。
- 不修改 plan/审计/docks；不提交 `docs/plans/fast/**`（仅按下方报告路径写入 execution.md，不提交它）；不新增授权外文件；工作区不留未跟踪残留文件。
- 本地提交恰好一次：`feat(fast-p): implement 03`（不得 push/merge/rebase/amend/squash）。

## Downstream interfaces（后续子计划依赖）

- 05 的视频 section 插在 4 个档案 section 之后；03 的备注 section 位置（nav 之前）与 02 的插入点保持稳定，互不冲突。
- 03 不新增接口依赖（复用既有 contact-notes GET）。

## Report

- 报告路径：`docs/plans/fast/2026-10-11-contact-profile-development-master/children/03/execution.md`（写入完整结果：改动文件、命令与结果、偏差、下游事实、遗留风险；不提交）。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径（紧凑 JSON）。
