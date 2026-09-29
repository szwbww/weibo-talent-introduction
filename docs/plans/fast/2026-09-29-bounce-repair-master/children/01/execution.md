# Child 01 Execution Report — 发件账号硬退告警可核对

## Execution Result: PLAN_CONFLICT

实现已在授权文件内完成并通过本阶段全部要求命令，但**落盘后会使一个未授权文件中的既有测试失败**，需要一次人工批准的计划修订（把该文件加入白名单）才能宣称本子计划完整落地。详见文末「阻塞与所需修订」。

| 项 | 值 |
|---|---|
| Plan | `docs/plans/2026-09-29/bounce-alert-observability.md` |
| Plan SHA-256 | `7db733f98f985a6e83b28d7776c236a6ae7d7dcb00a59cc077041f945ef3f614` |
| Execution ID | `docs/plans/2026-09-29/bounce-alert-observability.md@sha256:7db733f9…f614` |
| Execution epoch | NEW（无同 identity 的在先执行记录） |
| Executor | `ImplBounce01`（execute-p） |
| Target worktree | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-bounce-repair-master` |
| Target branch | `fast/2026-09-29-bounce-repair-master` |
| Worktree ID | `…fast-2026-09-29-bounce-repair-master@fast/2026-09-29-bounce-repair-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-29-bounce-repair-master` |
| Pre-execution code SHA | `18c79797ef87022d0fd134d7890759993e377059`（= child_base_sha） |
| Post-execution code SHA | `ff0d1ebc52eae3812c994cb8764e04ef455455c0` |
| Evidence HEAD | N/A（本阶段的 `docs/plans/fast/**` 由控制器提交） |
| Implementation boundary | `18c7979..ff0d1eb`（7 个授权文件，1 个本地提交） |

门禁脚本回执：

```text
$ python3 <skill-dir>/execute-p/scripts/plan_identity.py docs/plans/2026-09-29/bounce-alert-observability.md
{"canonical_path": ".../docs/plans/2026-09-29/bounce-alert-observability.md", "sha256": "7db733f98f985a6e83b28d7776c236a6ae7d7dcb00a59cc077041f945ef3f614", "size_bytes": 13003}   # exit 0
$ python3 <skill-dir>/execute-p/scripts/worktree_identity.py docs/plans/2026-09-29/bounce-alert-observability.md
{"branch": "fast/2026-09-29-bounce-repair-master", "head": "18c79797ef87022d0fd134d7890759993e377059", "git_dir": ".../.git/worktrees/weibo-talent-introduction-fast-2026-09-29-bounce-repair-master", "git_common_dir": "/Users/lukai/IdeaProjects/weibo-talent-introduction/.git"}   # exit 0
```

## Task Status

| 需求/不变量 | 状态 | 文件 | 证据 |
|---|---|---|---|
| I-1 一次计算、同一 cutoff | IMPLEMENTED | `BounceRateMonitorService.kt`、`MailSenderAccountController.kt` | service `getStats` 只用 `since` 一次（`BounceRateMonitorService.kt:37-39`）；controller 先取一次快照（`MailSenderAccountController.kt:98`）再填 DTO（`:125-130`）；`BounceRateMonitorServiceTest` 用例 `9 of 160 with one count per repository and one shared cutoff` 断言两次调用各 1 次且 `hardCalls[0] == sentCalls[0]`；MVC 用例断言 `getStats` 每次响应恰 1 次（列表 2 账号 → 各 1 次） |
| I-2 返回值与门槛 | IMPLEMENTED | `BounceRateMonitorService.kt` | `:40-48`（`sentCount >= MIN_SAMPLE_SIZE` → rate，否则 null；`high = rate != null && rate > DEFAULT_THRESHOLD`）；6 个新用例覆盖 0/0、2/19、1/20、2/20、30/20、9/160 |
| I-3 兼容与无副作用 | IMPLEMENTED | `BounceRateMonitorService.kt` | `calculateHardBounceRate` 仍 `Double` 且低样本 `-1.0`（`:56-59`+`:87`）；`isHardBounceRateHigh`（`:61-62`）与 `checkAndWarn`（`:64-70`）签名/语义未改；旧 5 个用例全绿；无任何 save/update/pause/resume 调用（service 仅依赖两个只读 repository，MVC 用例 `verifyNoMoreInteractions(service)` 证明 GET 只有读） |
| I-4 接口与展示契约 | IMPLEMENTED | `MailSenderAccountController.kt`、`app.js` | DTO 新字段 `:265-273`；9/160 → 9、160、0.05625（service 用例 + MVC `jsonPath` 断言）；空/缺字段回退原徽标（`app.js:3289-3291`）；旧 payload 回退原文案（JS 用例） |
| I-5 口径与资源版本 | IMPLEMENTED | `app.js`、`index.html` | tooltip 文案「近7天退信事件N条 / 近7天成功发信M封」「两者可能不是同一批邮件」「阈值>5%，至少20封」「仅提示，不影响自动发送」（`app.js:3291-3294`）；`index.html` 11 处（`:11-15`、`:2323-2328`）统一 `?v=20260929-bounce-alert`，`task-modal-runtime.js` 未带版本未改（`:2322`） |
| S-1 状态徽标原位替换 | IMPLEMENTED | `app.js` | 仍是同一 `span.badge.warn`（`app.js:3290`、`:3294`），无新 class、无 inline style、无新表格列（JS 用例断言行内恰 7 个 `<td>` 且 HTML 无 `style=`）；`styles.css` 无改动（`git status` 无该文件）；title 走 `escapeHtml`（`:3294`）；前两段「启用/禁用」「自动暂停」未动（`:3298-3301`）；低样本无第三段 |
| IP-1/2/3/4（读取口径、无新写路径） | IMPLEMENTED | 同上 | 分子仍是 `countHardBouncesSince`（HARD + received_at，无 contact 过滤）、分母仍是 `countSentByAccountSince`（OUTBOUND + SENT + sent_at）；本阶段零 DDL、零新持久化字段、无新写路径；`checkAndWarn` 仍只日志 |

## Commands（均在 worktree 内、JDK 11）

| 命令 | 结果 | 证据 |
|---|---|---|
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn -DskipNodeTests=true -Dtest=BounceRateMonitorServiceTest,MailSenderAccountControllerMvcTest test` | PASS | `Tests run: 23, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS`（Total time: 02:03 min）。分类计数：`MailSenderAccountControllerMvcTest` 9（基线 8，新增 1），`BounceRateMonitorServiceTest` 14（基线 5，新增 9）；exec 绑定的三个 Node 任务按 `-DskipNodeTests=true` 跳过 |
| `node --test src/test/js/senderBindingDisplay.test.js` | PASS | `tests 13 / suites 4 / pass 13 / fail 0`，exit 0（基线 8 pass；新增 5 例） |
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| （诊断，未在要求清单内）`node --test src/test/js/providerUndeliveredColumn.test.js` | FAIL | `tests 13 / pass 12 / fail 1`：既有断言 `appJsSource.includes("hardBounceCount")` 为假；见「阻塞与所需修订」 |

三条要求命令均在上表的最终提交状态上执行：Java 命令在本轮全部 Kotlin 编辑之后运行；两条 JS 命令在提交 `ff0d1eb` 之后复跑（app.js/index.html 在 Java 命令启动后未再改动，两条 JS 命令结果即提交态结果）。

## 逐文件改动

| 文件 | 改动 |
|---|---|
| `src/main/kotlin/.../mail/service/BounceRateMonitorService.kt` | 新增顶层 `data class HardBounceStats`（`:14-21`）与 `getStats(accountCode, windowDays = 7)`（`:36-50`）；`calculateHardBounceRate` 改为委托 `getStats(...).rate ?: NO_SAMPLE_RATE`（`:56-59`）；新增常量 `NO_SAMPLE_RATE = -1.0`（`:87`）；`isHardBounceRateHigh`/`checkAndWarn` 原样保留 |
| `src/main/kotlin/.../mail/controller/MailSenderAccountController.kt` | `toResponse` 由表达式体改为块体，取一次 `getStats`（`:98`）并填入原有字段 + 5 个新字段（`:125-130`）；`MailSenderAccountResponse` 追加 `hardBounceCount:Long`、`sentCount:Long`、`hardBounceRate:Double?`、`hardBounceSampleSufficient:Boolean`、`hardBounceWindowDays:Int`（`:265-273`，无默认值，强制显式装配）；`list/get/create/update/enable/disable/reset/resume` 八个复用点未改，自动继承 |
| `src/main/resources/static/app.js` | 仅 `loadAccounts`：新增内联 `hardBounceBadge(account)`（`:3283-3295`），校验 `hardBounceSampleSufficient === true` 且三个数字均为 finite `number`；可用时输出 `永久退信偏高 N/M（P%）` + 完整 tooltip（`escapeHtml`），不可用时原位回退原徽标；第三段改为 `+ hardBounceBadge(account)`（`:3302`）；删除原 `hardBounceRateHigh` 局部变量 |
| `src/main/resources/static/index.html` | 11 个带版本资源统一 `20260929-discovery-schedule` → `20260929-bounce-alert`（`:11-15`、`:2323-2328`）；未带版本的 `task-modal-runtime.js`（`:2322`）未动；无 DOM 改动 |
| `src/test/kotlin/.../service/BounceRateMonitorServiceTest.kt` | 新增 9 例：9/160 值+单次 COUNT+同一 cutoff、0/0、2/19、1/20、2/20、30/20（不封顶）、自定义 windowDays 的 cutoff、`checkAndWarn` 自定义阈值/天数、`checkAndWarn` 低样本 -1.0；新增两个计数 stub 助手 |
| `src/test/kotlin/.../controller/MailSenderAccountControllerMvcTest.kt` | 所有既有 toResponse 路径用例改 stub `getStats`（4 处）；`listAccounts` 增补 5 个新字段的字段名/类型/数值断言与 `getStats` 恰 1 次、GET 只读断言；新增 `detail and management endpoints reuse the same bounce statistics response`（get/enable/disable/reset + 低样本 null/false/false + `getStats` 恰 4 次）；新增 `stubBounceStats` 助手 |
| `src/test/js/senderBindingDisplay.test.js` | 新增 `bounce alert badge` 三例（9/160 数值+tooltip+无 inline style+7 列；低样本无第三段；缺字段/非数值/null → 回退原徽标且无 NaN）与 `static resource cache keys` 两例（11 处同值、旧键 0 处、`task-modal-runtime` 不带版本）；原「硬退率过高」用例补注为旧 payload 回退分支并加 2 条否定断言 |

## 逐条不变量证据（file:line）

- **I-1**：`BounceRateMonitorService.kt:37` 计算一次 `since`；`:38-39` 两次 COUNT 复用该 `since`；`MailSenderAccountController.kt:98` 每账号一次 `getStats`，`:125-130` 五字段全部取自同一 `bounceStats`。`BounceRateMonitorServiceTest.kt`（9/160 用例）以计数 stub 断言 `hardCalls.size == 1 && sentCalls.size == 1 && hardCalls[0] == sentCalls[0]`，并断言 cutoff 落在 `[before-7d, after-7d]`。`MailSenderAccountControllerMvcTest.kt` 断言 `getStats` 在列表请求中按账号各 1 次、在 get/enable/disable/reset 四请求中共 4 次。
- **I-2**：门槛常量原样（`DEFAULT_THRESHOLD = 0.05`、`MIN_SAMPLE_SIZE = 20`，`:82-84`）；`:40-41` 决定 null/比率；`:48` 决定 high。测试矩阵：0/0 → `null/false/false`；2/19 → `null/false/false`；1/20 → `0.05/true/false`；2/20 → `0.1/true/true`；30/20 → `1.5`（无 100% 封顶）；9/160 → `0.05625`。
- **I-3**：`:56-59` 保留 `Double` 与 `-1.0`；`:61-62`、`:64-70` 未改；`AutoMailReplyService.kt:955` 的 `checkAndWarn` 调用点不需要改动。无副作用：`BounceRateMonitorService` 只注入两个只读 repository（`:24-27`），类内无 `save/update/pause/resume` 调用；MVC GET 用例 `verify(service).bindingCountsByAccount()/listAccounts()/effectiveDailyLimitFor(…)` + `verifyNoMoreInteractions(service)` 证明只读路径未新增任何写。
- **I-4**：`MailSenderAccountController.kt:265-273` 字段名/类型与契约一致；`hardBounceRateHigh` 保留（`:263`，`:125` 取 `bounceStats.high`）。展示：`app.js:3292-3294`（`percent = (rate*100).toFixed(2)` 仅用于展示，告警由 `hardBounceRateHigh` 决定；9/160 → `5.63%`）。`app.js:3285-3288` 对 `null`/字符串/NaN 一律判为不可用 → 回退（`:3290`），不会产生 `NaN`/`Infinity`/伪造 `0/0`。
- **I-5**：tooltip（`app.js:3292-3293`）含「近7天退信事件N条 / 近7天成功发信M封＝P%」「两者可能不是同一批邮件」「阈值>5%，至少20封」「仅提示，不影响自动发送」；`index.html` 11 处同值（`grep -c "?v=20260929-bounce-alert" → 11`），旧键 `grep -c "20260929-discovery-schedule" → 0`。
- **S-1**：`git status` 无 `styles.css`；DOM 仍是 `span.badge.warn`（`app.js:3290/3294`）；JS 用例断言渲染行 `<td` 恰 7 个、HTML 无 `style=`、无新 class（仅 `badge`+`warn`）；低样本无第三段。

## 提交

```text
$ git log --oneline -1
ff0d1eb feat(fast-p): implement 01
$ git show --stat --oneline HEAD
 7 files changed, 495 insertions(+), 30 deletions(-)
   src/main/kotlin/.../mail/controller/MailSenderAccountController.kt  |  24 +-
   src/main/kotlin/.../mail/service/BounceRateMonitorService.kt        |  51 +-
   src/main/resources/static/app.js                                   |  20 +-
   src/main/resources/static/index.html                               |  22 +-
   src/test/js/senderBindingDisplay.test.js                           | 130 +
   src/test/kotlin/.../MailSenderAccountControllerMvcTest.kt           | 112 +-
   src/test/kotlin/.../BounceRateMonitorServiceTest.kt                 | 166 +
$ git merge-base --is-ancestor HEAD fast/2026-09-29-bounce-repair-master && echo ANCESTOR_OK
ANCESTOR_OK
$ git status --porcelain      # 提交后
 M docs/plans/fast/2026-09-29-bounce-repair-master/ledger.md    # 控制器既有未提交改动，未被我触碰
?? docs/plans/fast/2026-09-29-bounce-repair-master/children/01/execution.md   # 本报告，按要求不提交
```

提交只含 7 个授权文件；未 push / merge / rebase / amend / reset；未触碰 `docs/plans/fast/**`（报告除外）与主检出。

## 偏差

1. **无产品行为偏差**：7 个授权文件的改动逐条对应子计划「实现方案」1–4。
2. tooltip 中的 `>` 经 `escapeHtml`（S-1 要求）后源码为 `&gt;`；浏览器解析出的属性值与子计划骨架逐字相同（`阈值>5%，至少20封`）。JS 用例据此断言转义形式。
3. 计划未规定统计 data class 名称，实现取 `HardBounceStats`（`BounceRateMonitorService.kt:14`，与 service 同文件、顶层 `public`，供 controller 引用）。
4. **阻塞性偏差（见下节）**：落盘会使 `src/test/js/providerUndeliveredColumn.test.js:232-235` 失败，该文件不在本子计划白名单内。

## 阻塞与所需修订（PLAN_CONFLICT 的依据）

**事实链（可复现）**

1. 本子计划 I-4 强制账号响应字段名 `hardBounceCount`，S-1 强制 `app.js` 的 `loadAccounts` 渲染 `永久退信偏高 9/160（5.63%）` → `app.js` 必须读取 `account.hardBounceCount`（`app.js:3285-3294`）。
2. 既有未授权文件 `src/test/js/providerUndeliveredColumn.test.js:231-236` 断言 `!appJsSource.includes("hardBounceCount")`（全局源码子串否定），该文件属于 `mvn test` 的 exec 绑定 Node 全量套件。
3. 实测：`node --test src/test/js/providerUndeliveredColumn.test.js` → `✖ app.js 不再引用 hardBounceCount / softBounceCount`，断言 `assert.ok(!appJsSource.includes("hardBounceCount"))` 为假（12 pass / 1 fail）。
4. 该断言的来源是已批准计划 `docs/plans/2026-09-02/provider-undelivered-column.md` 的 I-6 验收（该文件 `:561` 逐字要求 app.js 零命中）。但同一计划的 I-6 正文（`:106-113`「范围限定（必读）」）把该不变量**限定在服务商分布链路**（`ProviderStatRow` + `providerDistribution` + `app.js` 中的两处消费点），并明确禁止把验收扩大解释。
5. 本子计划与总计划的变更清单（7 文件 / 19 文件）与代码审计回执都未包含该测试文件；审计回执里没有任何 `hardBounceCount` 的 grep 回执，即「7 文件足够」这一数量结论未被 grep 验证。

**为什么无法在授权范围内解决**：`app.js` 只要读取契约字段名 `hardBounceCount`，字面量必然出现；任何绕开（拼接属性名、从 `entries` 里按名字查找、由后端预格式化字符串）都是为躲避旧测试而扭曲实现，均被禁止。反向也不能在不改该文件的前提下放宽旧断言。

**所需最小修订（请人工批准并记入 Amendments 行）**：

- 在白名单中加入 `src/test/js/providerUndeliveredColumn.test.js`，把该全局源码断言改为返回 I-6 原本的**范围限定**语义，例如：断言 `extractFn("renderMonitoringProviderDistribution")` 与 `renderMonitoringCards` 的 `worstUndeliveredProvider` 链内不再出现 `hardBounceCount`/`softBounceCount`（保留「监控表格字段已删除且消费点同步」的原意，同时不再禁止账号页新增同名字段）。
- 备选（需同时改 DTO 契约并影响下游子计划的精确字段名）：把账号页统计字段改名，但这会偏离 I-4 已冻结的下游字段名，不推荐。

**在修订获批前，本子计划不具备「全机器验证通过」的落地条件**；子计划自身列出的三条命令已全部通过，实现本身可按原样保留（修订只需调整该守卫断言的范围）。

## Freshness

- Plan identity rechecked：YES（执行结束后复算仍为 `sha256:7db733f98f985a6e83b28d7776c236a6ae7d7dcb00a59cc077041f945ef3f614`，`size_bytes 13003`，未变）
- Worktree identity rechecked：YES（提交前后各跑一次 `worktree_identity.py --expect-root/--expect-branch/--expect-git-dir`，均 exit 0；HEAD 由 `18c7979` 前进到 `ff0d1eb`，branch 仍为 `fast/2026-09-29-bounce-repair-master`；`docs/plans/fast/**` 与 `ledger.md` 的既有未提交改动未被我触碰）
- Reported commits reachable from target branch：YES（`git merge-base --is-ancestor ff0d1eb fast/2026-09-29-bounce-repair-master` → ANCESTOR_OK；`ff0d1eb` 即 worktree HEAD）
- Required commands run this invocation：YES（三条命令均在本轮、最终实现状态之后运行）
- Historical evidence used only as baseline：YES（`children/01/baseline.md` 仅用于对比计数）

## Remaining Blocker

- 需要一次人工批准的计划修订：把 `src/test/js/providerUndeliveredColumn.test.js` 加入本子计划白名单，并把其全局源码断言收敛为服务商分布链路范围（或明确判定该断言作废）。

## Next Action

- PLAN_CONFLICT → 由人工裁决上述修订；批准后仅需调整该守卫断言的范围，本子计划的产品实现无需返工，随后重新执行 `verify-p`/轻量验证。
