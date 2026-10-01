# Child c3 Execution — Emailable 放行结果：收发件箱前端（frontend，终片）

## Execution Result: READY_FOR_VERIFICATION

- Child plan（权威合同，含 S-1..S-3 逐字 DOM/CSS）: `docs/plans/2026-10-01/email-verification-allowlist-frontend.md`
  - canonical path: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-01-email-verification-allowlist/docs/plans/2026-10-01/email-verification-allowlist-frontend.md`
  - SHA-256: `57c4bf507baf1a8471e543f4d8259dbf6ef03c6a0e28af0e1feab29e9e0be5d6`（执行前重读并计算，执行后复算一致）
  - EXECUTION_ID: `…-frontend.md@57c4bf507baf1a8471e543f4d8259dbf6ef03c6a0e28af0e1feab29e9e0be5d6`（epoch: NEW）
- Master plan: `docs/plans/2026-10-01/email-verification-allowlist.md`（SHA-256 `49d61d1ebb0babee9517010c245827d406916bf2040a2558c0717ccbc2a94b73`）
- Brief: `docs/plans/fast/2026-10-01-email-verification-allowlist/children/c3/brief.md`（SHA-256 `58bf8f767295441d13b91765dcc9d7ec54335ed56358b0932a5b7b3a36251a8f`）
- Worktree（TARGET_WORKTREE）: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-01-email-verification-allowlist`
- Branch: `fast/2026-10-01-email-verification-allowlist`
- Worktree git dir: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-01-email-verification-allowlist`
- child_base_sha（c2 Code head）: `ac37fcd9fc570897ec42b3ce7745a9a7104cebe7`
- Pre-execution HEAD: `c67e5703b87904cf18f4d7b3a057881240b17f3d`（c2 light verification docs 提交；`ac37fcd9` 为其祖先）
- Implementation commit: `e6c36e294cb26ce36684a96a0908c305f871e4b4` — `feat(fast-p): implement c3`（8 个授权文件，808 insertions / 106 deletions；本报告的 `docs/plans/fast/**` 未入库）
- Post-execution code SHA: `e6c36e294cb26ce36684a96a0908c305f871e4b4`（= HEAD；已核对为 `fast/2026-10-01-email-verification-allowlist` 的 tip，且 `ac37fcd`/`c67e570` 为其祖先）
- Executor: C3Impl（无 push / merge / rebase / amend / reset）

### 执行前实查（计划 I-5 要求的复核）

| 检查 | 命令 | 结果 |
|---|---|---|
| index 版本键数量 | `grep -c '20260930-manual-template-reference' src/main/resources/static/index.html` | 11（与计划逐字一致） |
| 测试是否钉死旧键 | `grep -rn '20260930-manual-template-reference' src/test \| wc -l` | 0 |
| 任何 `*-manual-template-reference` 残留 | `grep -rln 'manual-template-reference' src/test \| wc -l` | 0 |
| 旧键命中测试（全仓） | `grep -rn '20261001-email-verification-allowlist\|20260930-manual-template-reference' src/test \| wc -l` | 0（无钉死键的测试，无需 PLAN_CONFLICT） |

结论：**无固定字面量测试**，按契约把 11 处统一改为 `20261001-email-verification-allowlist`，未增删任何静态资源。

## Task Status（9 个授权文件）

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 真实 DOM/CSS（I-1/I-2；S-1/S-2） | IMPLEMENTED | `index.html`, `styles.css` | 两处放行组按 S-2 逐字插入（脚本比对：`editor block verbatim in index: True` / `manual block + diff DOM verbatim in index: True` / `css block verbatim in styles.css: True`）；总开关 DOM/id 保留，仅替换两处说明文案；无 `undeliverable` 控件 |
| T-2 完整数据往返（I-1/I-2/I-3/I-5） | IMPLEMENTED | `app.js`, `index.html` | 编辑 payload/预估、手动 clone/default/fill/read/snapshot/normalize/diff/fieldMap/clear/confirm 全部接入；新字段进 `clearAllDiffMarkers`；关闭时仍读 `checked`；`updateEmailVerificationToggleLabel` 收敛组禁用态；11 处版本键统一 |
| T-3 日志文案与 sandbox 扩展（I-3/I-4） | IMPLEMENTED | `app.js`, `batchEmailVerification.test.js` | `EMAIL_VERIFICATION_POLICY_SKIP: "未勾选该验证结果，本次未发送"`；`emailVerificationDecisionText` 策略跳过优先分支；sandbox 加载新 helper；旧提示文案断言更新；新增真实 index DOM/id/value 断言 |
| T-4 既有抽取函数测试的直接依赖 | IMPLEMENTED | `batchExpertTypeFilter.test.js`, `batchSenderFilter.test.js`, `expertTagBatchFix.test.js`, `batchSendTaskConsoleInteraction.test.js`（+ `batchManualExecutionLog.test.js` 无需改动） | 仅注入新 helper 加载 + 新字段 fixture；原门禁/范围/日志/差异断言未改（全量 JS 1273 pass / 0 fail） |

### 关键不变量落实

- **I-1**：`batchEmailVerificationAllowedStates()` 是唯一固定顺序（deliverable,risky,unknown）；`readEmailVerificationAllowedStates` 只按勾选（固定顺序）产出，`[]` 原样进 payload/snapshot（`saveBatchConfigEditor`、`buildConfigEditorRecipientSnapshot`、`readManualFormValues`、`buildManualExecutionSnapshot`）；新建任务与独立手动手动默认 `["deliverable"]`；编辑按 View 数组回显；缺字段/null → 三态兼容；三个框每次全量重写（不继承上次编辑值）；DOM 无 undeliverable。
- **I-2**：总开关默认仍 `false`（index 无 checked）；`updateEmailVerificationPolicyState` 只按「总开关开启且模板支持」设置三个 checkbox 的 `disabled` 与组 `.is-disabled`，**不写 checked**；材料提醒沿用既有禁用+置回 false；切回介绍邮件不自动开启；两处说明文案写明「仅在发送前验证开启时生效…全不选时不放行任何验证结果」。
- **I-3**：`deepCloneConfig` 用 `normalizeEmailVerificationAllowedStates` 做固定顺序克隆（新数组，不与来源共享引用）；差异按集合比较（归一化固定顺序）；`[]` 显示「不放行任何结果」；差异字段独立容器 `manualFieldEmailVerificationAllowedStates`（不与旧开关共用 fieldMap key）；clear/重新选来源恢复来源或独立默认；确认页显示有效选择，关闭显示「未启用」；执行仅 `POST /api/mail/batch-send/manual-executions`（真实页面实测只发该请求）。
- **I-4**：`sendReason=EMAIL_VERIFICATION_POLICY_SKIP` 的 SKIP 行 → 决策列「按策略跳过」、发送列「…（未勾选该验证结果，本次未发送（EMAIL_VERIFICATION_POLICY_SKIP））」；provider state/reason 原样（含转义）；`tagStatus=NOT_REQUIRED` → 「无需处理」；历史 `EMAIL_VERIFICATION_REJECTED` 文案与「已标记邮箱异常」不变；未知原因原样显示。
- **I-5**：`index.html` 11 处版本化资源同值 `20261001-email-verification-allowlist`（CSS/workbench/app 三件同在），仅改版本、未增删资源；预估入参携带列表但不新增 Emailable 请求（后端 `POST /api/mail/batch-send/recipients/preview` 绑定 `BatchExecutionSnapshot`，只读）；原预估文案/成功额度语义未动。

## Changed Files（8 个改动，均在 9 个授权文件内）

1. `src/main/resources/static/index.html` — 11 处版本键 bump；两处总开关说明文案替换；插入编辑/手动两个 S-2 放行组（手动组含既有差异 DOM）。
2. `src/main/resources/static/styles.css` — 纯追加 S-2 CSS 块（+55 行，0 删除；未改任何既有规则）。
3. `src/main/resources/static/app.js` — 新增放行组 helper（列表/标签/ID 映射/归一化/读取/回填/禁用态/文案）+ 全部写读路径接线 + 列表 scope 文案 + 确认页行 + I-4 文案与优先级（+127/−7）。
4. `src/test/js/batchEmailVerification.test.js` — 加载新 helper、更新旧提示断言、新增 8 个 c3 用例。
5. `src/test/js/batchExpertTypeFilter.test.js` — 注入新 helper 加载（1 处 sandbox 工厂）。
6. `src/test/js/batchSenderFilter.test.js` — 注入新 helper 加载 + `SOURCE_CONFIG` 三态 fixture + 手动表单三态 fixture。
7. `src/test/js/expertTagBatchFix.test.js` — 注入新 helper 加载（7 处 sandbox 工厂）。
8. `src/test/js/batchSendTaskConsoleInteraction.test.js` — 注入新 helper 加载（75 处 sandbox）。

未改动：`src/test/js/batchManualExecutionLog.test.js`（授权但无需变更：其 `readManualFormValues` 为 stub，快照新增字段透传 `undefined`，既有断言不含新字段）。

提交纪律：逐项 `git add` 上述 8 个文件（无 `git add -A`）；不提交 `docs/plans/fast/**`、`baseline/**`、本报告。

## Commands（均在最终实现状态下、本次调用内新跑）

| 命令 | 结果 | 证据 |
|---|---|---|
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `node --test src/test/js/batchEmailVerification.test.js` | PASS | exit 0；`tests 41 / pass 41 / fail 0`（基线该文件 33 通过，本 child +8） |
| `node --test src/test/js/*.test.js` | PASS | exit 0；`tests 1273 / suites 250 / pass 1273 / fail 0 / cancelled 0 / skipped 0 / todo 0`（run 级基线 `baseline/js-full.txt` = 1265 / 249 suites / 0 fail，改动后 1273 ≥ 1265） |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B test` | PASS | exit 0 / BUILD SUCCESS（04:50 min，日志 `/tmp/c3_mvn.txt` 会话内产物）；`Tests run: 4483, Failures: 0, Errors: 0, Skipped: 13`；其中 exec-plugin 的 `node --test src/test/js/*.test.js` = `1273 pass / 0 fail`，`node --check` 两步均执行通过 |

基线对照（只读）：`baseline/js-full.txt` = 1265 pass / 249 suites / 0 fail；`baseline/mvn-full.txt` = `Tests run: 4448, Failures: 0, Errors: 0, Skipped: 13` + BUILD SUCCESS。本次 `mvn` 的 4483 = 基线 4448 + c1/c2 新增 Java 用例（本 child **未新增 Java 测试**，+35 与前端改动无关）；JS 侧 1265 → 1273 全部为本 child 新增的 8 个 c3 用例。

## 浏览器实测（真实 index.html + 真实 DOM/CSS，本 child 内自建证据）

计划 A-1..A-6 的浏览器人工验收不在机器范围内，但 S-2 要求的「1280px / 390px 目测」与 I-1/I-2/I-3 的真实 DOM 行为已用无头 Chromium 打开 `index.html`（file://）实测：

| 检查 | 结果 |
|---|---|
| S-2 计算样式（1280px） | 选项 `font-size: 13px`、`display: inline-flex` / `flex-direction: row`；checkbox `16px × 16px`（`min-height: 16px`）；说明 11px；`small` 11px |
| S-2 无横向溢出（390px） | 组与 `.batch-email-policy-options` 的 `scrollWidth === clientWidth`（306/306）；`document.documentElement.scrollWidth === 390 === innerWidth`；`gap` 命中媒体查询 `4px 10px`；三个选项中前两项同行、第三项自然换行 |
| I-2 真实行为 | 总开关关闭 → 三个 checkbox `disabled=true`、已勾选的 risky `checked` 保留、组 class 含 `is-disabled`、`opacity: 0.52`；开启 → 三个 `disabled=false`、勾选保留、`opacity: 1` |
| I-1 真实编辑往返 | 勾 risky+unknown → `POST /api/mail/batch-send/configs` payload `emailVerificationAllowedStates: ["risky","unknown"]`，`buildConfigEditorRecipientSnapshot()` 同值；全部取消 → payload `[]`；缺字段回填 → 三项全勾；新建回填 → 仅 deliverable |
| I-3 真实手动往返 | 独立手动默认仅 deliverable、总开关关、三框禁用；开启并加选 unknown → 确认页「发送前验证邮箱: 开启 / 允许发送的验证结果：可投递、未知」；`confirmManualExecution` 只发 `POST /api/mail/batch-send/manual-executions`，`snapshot.emailVerificationAllowedStates = ["deliverable","unknown"]`；选来源 `["deliverable"]` → 仅 deliverable 勾选，加选 risky 后 `manualFieldEmailVerificationAllowedStates` 标记 `is-config-diff` 且「原：可投递」，旧开关容器未被标记 |

（截图与度量通过本会话浏览器工具取得，未落盘到仓库；如需留档可在人工验收 A-5 时重跑。）

## Deviations

- **S-3 scope 行在总开关关闭时不输出「放行：…」**：计划文字为「原『邮箱验证 · 开』pill 后…追加…；关时维持『邮箱验证 · 关』」。本 child 判定为：关闭时列表不参与本次执行，故维持原「关」pill 不追加放行文案（开启时全空才显示「放行：无」）。开启态的三态/空集/缺字段文案均有测试固定。
- **策略跳过文案落在既有发送原因映射**：I-4 要求显示「按策略跳过」和「未勾选该验证结果，本次未发送」，实现为决策列「按策略跳过」+ 发送列 `未发送（未勾选该验证结果，本次未发送（EMAIL_VERIFICATION_POLICY_SKIP））`，沿用既有 label+受控码格式，未新增 DOM/class/CSS。
- **测试注入为统一机械注入**：4 个既有测试文件按「`vm.createContext(x)` 后注入 10 个新 helper」统一注入（含个别不需要该 helper 的 sandbox），避免只补失败点后未来抽取宿主函数再次静默缺依赖；不动任何既有断言。
- 未做范围外改动：无新 class、无 inline style、未改全局 label/input/p 规则、未改其它控制台布局、未新增资源或接口。
- 观察（不属任务）：真实控制台里 `small` 中的 `deliverable/risky/unknown` 因全局 label 规则呈现全大写（与全站 label 一致）；预览页无该全局规则故为小写。S-2 逐字 CSS 未声明 `text-transform`，本 child 未扩权覆盖，留待人工验收 A-5 判定是否需要。

## Freshness

- Plan identity rechecked：YES（执行前重读 brief + child plan；执行后复算 SHA-256 不变）
- Worktree identity rechecked：YES（所有读写/测试/提交均在本 worktree，未触碰主工作区）
- Reported commits reachable from target branch：YES（`e6c36e294cb26ce36684a96a0908c305f871e4b4` 为 `fast/2026-10-01-email-verification-allowlist` tip；`git show --stat` 仅含 8 个授权文件）
- Required commands run this invocation：YES（4 条必需命令均在最终实现状态下于本次调用新跑）
- Historical evidence used only as baseline：YES（`baseline/*.txt` 仅作对照，未当通过证据）
- Working tree after commit：源/测试文件干净（无未提交改动）；仅剩本报告 `docs/plans/fast/**/children/c3/execution.md` 为未跟踪（按纪律不入库）

## Remaining Blocker

- None

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`
