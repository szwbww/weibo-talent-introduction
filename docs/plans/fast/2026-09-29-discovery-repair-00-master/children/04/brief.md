# Child 04 Brief — 页面配置深度发现执行间隔（前端）

Approved child plan: `docs/plans/2026-09-29/discovery-repair-04-schedule-ui.md`
Approved bytes identity: plan-seed commit `70f550658d078b228fe619b735d81f0e740c4db3`; sha256 `ae874a44ff6ec177276e21f248a91c638b5b86dc79ab87cf0e9832fc837573b9`.
Read the full child plan from disk before implementing; it is the complete approved contract (需求描述 / 关键不变量 / 样式契约 S-1、S-2 / 现状审计 / 实现方案 T-1–T-3 / 变更文件清单 / 验收标准 / 人工验收清单).

## Execution context

- Retained worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master`
- Branch: `fast/2026-09-29-discovery-repair-00-master`
- `child_base_sha`: `e94425cb0cd275254f33530548ba98033ddca6b4` — child 03's terminal `Code head` (recorded in `docs/plans/fast/2026-09-29-discovery-repair-00-master/ledger.md`). Intervening commits after it are child-03 fast-p evidence only.
- Master plan: `docs/plans/2026-09-29/discovery-repair-00-master.md` (same seed commit). Master invariant **M-3（只调整后续触发）** is the parent rule; child invariants I-1–I-3 plus style contracts S-1/S-2 below are governing.
- Execution report to write: `docs/plans/fast/2026-09-29-discovery-repair-00-master/children/04/execution.md` (controller commits it as fast-p evidence; never include `docs/plans/fast/**` in the implementation commit).
- Use the `execute-p` skill with this brief plus the exact child plan; return its report shape.

## Hard constraints

- Only the 7 Authorized Files below may change.
- Do not touch the backend: the only API use is `GET/PUT /api/expert-discovery/schedule` delivered by child 03 (identical to the plan's contract: `mode`, `editable`, `source`, `intervalHours`, `anchorAt`, `nextTriggerAt`, `applied`, `reason`; 400/409/503 statuses with `saved` flag).
- Style: use the S-1 CSS block and DOM exactly as written in the child plan (verbatim copy); no new classes/icons, no inline styles, no changes to existing class rules, no extra modal or settings menu.
- Cache keys: all existing versioned assets in `index.html` (currently eleven nodes at `?v=20260929-mailbox-replied`) must be bumped to one single new value. Suggestion `20260929-discovery-schedule`; if by then a later unique key exists use that instead — never reuse an older key. No new JS asset file. Historical-key zero-hit assertions (`src/test/js/taskActivityCenter.test.js` RETIRED_KEYS) must stay green; re-check for any newly added fixed-key assertion before editing and stop with `PLAN_CONFLICT` if one appears outside the authorized files.
- Out of scope: cron输入、定时开关、日历、多份定时查询、自动保存、改动既有任务框架、任何专家数据写入、其他任务弹窗内容.
- Preserve: 原手动立即执行/取消、关键词/来源/范围、任务进度、关闭弹窗后的后台运行、连续模式暂停/恢复。
- Minimal diff; no unrelated refactoring. One local commit with exact subject `feat(fast-p): implement 04`. No push/merge/rebase/amend/squash; do not touch other worktrees.

## Authorized Files (7)

| # | File | Action |
|---|---|---|
| 1 | `src/main/resources/static/index.html` | S-1 DOM、S-2 缓存版本 |
| 2 | `src/main/resources/static/app.js` | 读取/保存/生命周期 |
| 3 | `src/main/resources/static/styles.css` | 仅新增 S-1 样式 |
| 4 | `src/test/js/discoveryScheduleSetting.test.js` | 新行为测试 |
| 5 | `src/test/js/discoveryContinuousRun.test.js` | 打开入口 sandbox 与连续回归 |
| 6 | `src/test/js/taskModalStateMachine.test.js` | 关闭入口 sandbox 与状态回归 |
| 7 | `src/test/js/taskModalLifecycleIntegration.test.js` | 生命周期依赖与回归 |

## Child invariants (verbatim contract)

### Invariant I-1：只有明确保存才改定时
- Rule：打开/关闭弹窗、输入变化、立即执行均不得 PUT schedule。只有"保存定时"发 PUT；不附带关键词/来源参数，不调用 start/resume/discover。成功显示后端返回的有效值与时间，不乐观假定生效。
- Violation consequence：用户只是查看或改关键词便改变后台计划。

### Invariant I-2：状态跟随当前弹窗
- Rule：只在 EXPERT_DISCOVERY 显示区域；以独立打开代次或既有 modal generation 校验异步 GET/PUT 结果。切任务/关闭/重新打开后旧响应不能写新弹窗。正在输入时不被任务进度轮询覆盖。
- Violation consequence：别的任务看到发现设置，或输入被旧请求覆盖。

### Invariant I-3：不能设置时不展示可保存假象
- Rule：加载中禁用；加载失败显示错误并禁止保存；连续/管理员禁用显示说明并隐藏小时控件；400/409/503 均显示服务端原因，503 saved=true 不能显示"已生效"。保存中防重复提交，成功/失败后按最新权限恢复。
- Violation consequence：无效设置误导运营，或保存失败无提示。

## Tasks

- **T-1（I-2、I-3；S-1、S-2）** `index.html`/`styles.css` 严格使用 S-1 代码块：面板作为 `.modal-body` 下、`#taskModalConfigSection` 之前的兄弟节点，初始 `hidden` 且控件 `disabled`；缓存 key 按 S-2 统一更新。
- **T-2（I-1–I-3；S-1）** `app.js` 集中三个小函数（初始化/渲染/保存 + 必要清理）。两个打开入口（`openTaskModal`、`openTaskLaunchModal`）在当前 modal context 建立后调用初始化；打开其他任务立即隐藏并使上一请求失效；关闭同样清理。用现有 api 封装/contextPath/认证处理；文案、禁用与错误处理严格按子计划"显示规则"；只接受十进制整数 1–168（`Number.isInteger`），空白/1.5/0/169/字母不发请求；PUT 期间禁用输入和保存，失败保留输入；打开时只读一次，任务进度 poll 不刷新该输入；服务端 reason/文本用 `textContent` 处理，不使用 innerHTML。
- **T-3（I-1–I-3；S-1、S-2）** 新增 `discoveryScheduleSetting.test.js`（加载实际新函数与真实打开/关闭函数，mock API/DOM：空闲/运行、默认/覆盖、非法值、延迟 GET、连续/禁用、PUT 成功/400/409/503、关闭不取消后台、手动执行不保存）；补三个已有 VM 测试的依赖注入使新辅助函数可运行，不删原断言、不用整个假实现替换生产函数。

验收标准与人工验收清单（A-1–A-4）以子计划原文为准。

## Required commands (fresh, after final implementation state, from the worktree root)

```
node --test src/test/js/discoveryScheduleSetting.test.js src/test/js/discoveryContinuousRun.test.js src/test/js/taskModalStateMachine.test.js src/test/js/taskModalLifecycleIntegration.test.js
node --test src/test/js/*.test.js
```

Also run `node --check src/main/resources/static/app.js` after final edits (the Maven test phase runs it too). Record exit codes and pass/fail counts. Do not run `mvn` at all in this child; the controller runs the single final Java11 `mvn clean package`.

## Downstream interface (terminal child)

- Only `GET/PUT /api/expert-discovery/schedule` from child 03; no direct DB/ES/localStorage access.
- All other task modals must be untouched; the panel must never appear for non-EXPERT_DISCOVERY tasks.
- Manual start/cancel and keyword/source/scope send logic stay byte-identical in behaviour.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.
