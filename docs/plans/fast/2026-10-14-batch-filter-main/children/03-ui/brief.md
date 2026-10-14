# Fast-P Child Brief — 03-ui

- 唯一契约（子计划）: `docs/plans/2026-10-14/batch-filter-03-ui.md`
- 全局约束（主计划）: `docs/plans/2026-10-14/batch-filter-main.md`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-14-batch-filter-main`
- Branch: `fast/2026-10-14-batch-filter-main`
- Base SHA: `9c9457cadda70e95dc605f720d652d3e4617b803`（02-diagnostics 的终止 Code head）
- 依赖: 02-diagnostics（已 LIGHT_PASS，含 1 轮修复；其响应 DTO 是本阶段唯一数据源）
- 实施提交信息（必须逐字）: `feat(fast-p): implement 03-ui`

## 交付范围

只允许修改/新增以下 5 个文件（子计划「变更文件清单」逐字）：

|序号|精确路径|变更|
|---|---|---|
|1|src/main/resources/static/app.js|诊断与正确文案|
|2|src/main/resources/static/index.html|静态文案/统一 cache key|
|3|src/test/js/recipientPreviewDiagnostics.test.js|新增诊断契约测试|
|4|src/test/js/batchEmailVerification.test.js|邮箱提示回归|
|5|src/test/js/gateTemplateFilter.test.js|模板开关回归|

不得修改 `styles.css`（CSS 差异必须为零）、不得新增 class 或 inline style、不得改动其他静态资源文件或后端代码。

## 必须保持的不变量

- **I-1（展示事实与推测分开）**：直接显示后端 `baseline`、`excludedUnique`、`eligibleBeforeLiveVerification`、`awaitingVerification` 及 `asOf`；`reasonHits` 允许重叠、不得求和冒充排除人数；无缓存显示「待执行验证」，不得显示可投递或供应商 unknown；样本标明「仅显示前 N 条」；`partial` 显示「统计未完成」。
- **I-2（模板提示与开关一致）**：gate 关闭显示「不因个性化缺项排除」；开启显示「因模板缺项排除 N 人」并列 missingKeys；不出现「计入失败」；普通配置错误仍显示错误。
- **I-3（交互安全与兼容）**：500ms 防抖与 seq 检查保留；关闭表单后不写旧 DOM；错误显示「预估不可用」且不弹窗；姓名/邮箱/原因/模板 key 均用既有 HTML 转义；只读展开不发发送/验证请求、不修改原任务；16 个版本化静态资源引用统一改为新 key `20261014-batch-filter`。
- 旧响应（缺新字段）只显示现有数量并注明「详细解释不可用」，**禁止补 0**。
- MAIN S-M1：只复用 03 子计划的 S-1/S-2 DOM 骨架与既有 class（`batch-config-editor-hint`、`batch-gate-excluded`、`batch-gate-warnline`、`batch-gate-hint`）；无新增 CSS；容器与提示保留原 ID；绑定账号提示保留。
- MAIN M-5：不得只依赖 DOM stub；新增「按 id 取元素」的渲染路径须在测试中断言该 id 出现在真实 `index.html` 源文本里。

## 必须运行的命令

```bash
cd /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-14-batch-filter-main
node --test src/test/js/recipientPreviewDiagnostics.test.js
node --test src/test/js/batchEmailVerification.test.js
node --test src/test/js/gateTemplateFilter.test.js
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
```

- 最后两条为关联批量页面契约回归与语法检查（仓库既有命令，见 `pom.xml` exec-maven-plugin）。
- 不得为本计划扩展测试框架或更新无关快照。

## 上游接口（直接消费 02 实际响应，禁止前端重算）

- 字段：`baseline`、`excludedUnique`、`eligibleBeforeLiveVerification`、`awaitingVerification`、`reasonHits`（每原因命中数 + ≤20 样本 + `truncated`）、`asOf`、`partial`、`verificationPhase`；`baseline = excludedUnique + eligibleBeforeLiveVerification`。
- 共同 fixture（MAIN G-2）：两处均显示 10/4/6/3；原因命中 6 次（地区 2 + 状态 2 + 模板 1 + 邮箱 1），排除仍为 4 人。
- 前端不得重算地区、准入、历史邮箱有效期或原因人数。

## 报告与返回

- 完整执行报告写入：`docs/plans/fast/2026-10-14-batch-filter-main/children/03-ui/execution.md`。
- 只提交产品代码为 `feat(fast-p): implement 03-ui`；**不得**把 `docs/plans/fast/` 下任何 fast-p 证据文件纳入该提交。
- 不 push、不 merge、不 rebase、不 amend、不 squash、不改写历史。

返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径。
