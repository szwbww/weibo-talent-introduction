# Child c3 Brief — Emailable 放行结果：收发件箱前端（frontend）

## Identity
- Child: c3（计划第 3/3 片，终片）
- Master plan: `docs/plans/2026-10-01/email-verification-allowlist.md`
- Child plan（完整合同，权威）: `docs/plans/2026-10-01/email-verification-allowlist-frontend.md`（含 S-1..S-3 逐字 DOM/CSS 契约）
- 外观基准（只读参考，位于主工作区，勿修改）：`/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/mockups/email-verification-policy-preview/index.html`；正式实现以计划中的 S-1/S-2 逐字块为准。
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-01-email-verification-allowlist`
- Branch: `fast/2026-10-01-email-verification-allowlist`
- child_base_sha: 见派发消息（= c2 Code head `ac37fcd9fc570897ec42b3ce7745a9a7104cebe7`）。
- 前序 child 输出（均已 LIGHT_PASS）：
  - 配置 API View 字段 `BatchSendTaskConfigView.emailVerificationAllowedStates: List<String>`（旧 SQL NULL 回 `["deliverable","risky","unknown"]`；`[]` 原样）。
  - 执行快照字段 `emailVerificationAllowedStates`；手动覆盖只进快照，不回写来源配置。
  - 原因码 `EMAIL_VERIFICATION_POLICY_SKIP`（中文标签「邮箱验证策略跳过」）；策略跳过四实值 `SKIP / SKIPPED / EMAIL_VERIFICATION_POLICY_SKIP / NOT_REQUIRED`。

## Authorized files（仅此 9 个；超出必须 PAUSE / PLAN_CONFLICT）
1. `src/main/resources/static/index.html`
2. `src/main/resources/static/styles.css`
3. `src/main/resources/static/app.js`
4. `src/test/js/batchEmailVerification.test.js`
5. `src/test/js/batchExpertTypeFilter.test.js`
6. `src/test/js/batchSenderFilter.test.js`
7. `src/test/js/expertTagBatchFix.test.js`
8. `src/test/js/batchManualExecutionLog.test.js`
9. `src/test/js/batchSendTaskConsoleInteraction.test.js`

非产品输出：只允许写执行报告 `docs/plans/fast/2026-10-01-email-verification-allowlist/children/c3/execution.md`（必须用**绝对路径**；你所在会话的默认 cwd 可能是主工作区——所有写操作一律用绝对路径或以本 worktree 为 cwd）。其余路径只读。

## 关键不变量（以 child plan 全文为准）
- I-1 UI 与请求同一选择：固定顺序 `deliverable,risky,unknown`；只发送勾选项，不因空数组回退默认；没有 `undeliverable` 控件；新建与独立手动默认 `[deliverable]`；编辑/选来源按 API 数组回显；旧响应字段缺失/null 兼容三项，不继承上次编辑值。
- I-2 开关与类型边界：验证总开关保持原默认 false；关闭只 `disabled` 三个选择框、保留 checked；切到材料提醒沿用现有关闭行为；切回介绍邮件不自动开启；界面必须写明列表仅在验证开启时生效。
- I-3 手动覆盖不污染配置：来源数组 slice 克隆；差异按集合比较、`[]` 显示「不放行任何结果」；重新选择同一来源恢复来源选择；clear 恢复独立默认；确认页显示有效选择；执行请求写 `snapshot.emailVerificationAllowedStates`，不 PUT 来源配置。
- I-4 策略跳过不展示为地址无效：`sendReason=EMAIL_VERIFICATION_POLICY_SKIP` 的 SKIP 行显示「按策略跳过」和「未勾选该验证结果，本次未发送」；provider state/reason 原样显示；`tagStatus=NOT_REQUIRED` 显示无需处理；历史 `EMAIL_VERIFICATION_REJECTED` 继续原有「未通过」文案；不从当前配置反推历史原因。
- I-5 静态资源与预估回归：`index.html` 现有 11 处版本化资源统一改为 `20261001-email-verification-allowlist`（`styles.css` / `trust-reply-workbench.js` / `app.js` 同值同时在列），仅变更版本引用、不增删资源；预估入参可携带列表但不新发 Emailable 请求；原预估文案/成功额度语义不变。
  - 已实查：当前 11 处为 `20260930-manual-template-reference`；`src/test` 0 命中（无钉死该键的测试）。改动前请再实查一次；若出现固定字面量测试，返回 PLAN_CONFLICT（需计划修订，不得自行扩权）。

## 实施要点
- T-1 真实 DOM/CSS：两处发送控制（编辑 `batchConfigEditor*`、手动 `batchManual*`）加三复选框组；DOM 与 CSS 逐字按计划 S-2（含 ID 映射表、`batch-email-policy-*` 新 class、固定说明与提示文案、禁用态 `.is-disabled`、600px 媒体查询）；现有 Emailable 总开关 DOM/id 保留，仅替换两处说明文案（HTML 与 JS 常量必须一致）。新 checkbox 必须真实存在于 `index.html` 源码（不能只靠 DOM stub）。
- T-2 完整数据往返：按计划补齐 `app.js` 编辑 payload/预估、手动 clone/default/fill/read/snapshot/normalize/diff/fieldMap/clear/confirm/事件；缺字段兼容三项与新建仅一项须显式分支（禁止 `length=0` 当缺省）；新字段加入 `clearAllDiffMarkers` 容器列表；关闭时用 checked 读取保留值；`updateEmailVerificationToggleLabel` 刷新组禁用态，`refreshEmailVerificationState` 的提前 return 覆盖材料提醒；差异字段独立行「允许发送的验证结果」，不与旧开关共用 fieldMap key。
- T-3/T-4 日志与测试：受控原因文案与优先级判断（未知原因原样显示）；扩展现有 vm/DOM sandbox 让新 helpers 真正执行；调整计划点名的旧提示文案断言；新增真实 index ID/checkbox value 数量断言防 stub 掩盖缺 DOM；其余 5 个测试文件仅补新 helper 加载/新字段 fixture，保留其原门禁、范围、日志与差异断言（K-dom-stub-tests-hide-dangling-refs）。
- 任务列表 `batch-task-scope-line` 追加「放行：…」（全空「放行：无」）；确认页在「发送前验证邮箱」行后追加「允许发送的验证结果：…」；有差异时用既有三列表格结构。

## Required commands（必须执行并记录 exit/counts）
```sh
node --check src/main/resources/static/app.js
node --test src/test/js/batchEmailVerification.test.js
node --test src/test/js/*.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B test
```
- JS 基线：`node --test src/test/js/*.test.js` → 1265 pass / 249 suites（改动后应 ≥ 该数且 0 fail）。
- 全量 `mvn test` 为 master 计划的 run 级收尾命令（基线 4448 / 0 / 0 / 13 skipped，含 exec-plugin node-test）；在最终实现态后执行。
- 浏览器级人工验收 A-1..A-6（真实页面/预发）不属机器执行范围；计划 S-2 要求 1280px/390px 目测对照预览，如具备条件可截图记录，不具备则在报告中标注未执行。

## 范围与纪律
- 只改 9 个 Authorized Files；不得新增契约外 class、不得 inline style、不得改全局 label/input/p 规则、不得改其它控制台布局。
- 不 push、不 merge、不 rebase、不 amend；实现提交：`feat(fast-p): implement c3`（逐项 `git add` 指定文件，禁止 `git add -A`；用绝对路径或 worktree cwd）。
- 不提交 `docs/plans/fast/**`、`baseline/**`、报告文件；不触碰主工作区与其他 worktree。
- 若必需改动落在授权文件之外，返回 PLAN_CONFLICT；不得自行扩权。
