# Child 01 Execution Report — 发件账号硬退告警可核对（Epoch 2）

## Execution Result: READY_FOR_VERIFICATION

Epoch 1 的产品实现（`ff0d1eb`）按修订 A1 保留不动；本 epoch 只做两处测试修复（F-1 守卫断言收敛、F-2 缓存键字面量移除），落盘后全量 JS 套件 0 失败。

| 项 | 值 |
|---|---|
| 子计划（A1 后） | `docs/plans/2026-09-29/bounce-alert-observability.md`（8 文件，修订记录含 A1） |
| Plan SHA-256 | `efde381c2d42469d5220d93830c228da48734fbc4a84b425c85823acfcecd668`（13985 bytes；A1 已并入正文。控制器 ledger 记录的 identity 为 `commit:9f5eb6818502c9b7079b771ee56464e8c4bff485` = `docs(fast-p): amend 01 plan (A1)`） |
| Execution epoch | 2（epoch 1 = PLAN_CONFLICT，见 `children/01/pause.md`） |
| Executor | `ImplBounce01E2`（execute-p，resume after A1） |
| Target worktree | `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-bounce-repair-master` |
| Target branch | `fast/2026-09-29-bounce-repair-master` |
| 本 epoch 起始 HEAD | `e5a5d28`（`docs(fast-p): record amendment A1`） |
| Epoch-1 产品提交（未改写） | `ff0d1ebc52eae3812c994cb8764e04ef455455c0` |
| 最终提交（epoch 2） | `4bc9f11956fe77d87063afc8bc393689f0ec3487`（`feat(fast-p): implement 01 epoch 2`） |
| Evidence HEAD | N/A（`docs/plans/fast/**` 由控制器提交；本报告不提交） |

## 本 epoch 改动（2 文件，恰好两处修复）

| 文件 | 修复 | 改动 |
|---|---|---|
| `src/test/js/providerUndeliveredColumn.test.js` | F-1（A1 新增授权） | `后端字段已彻底移除` 用例中的全局源码否定 `!appJsSource.includes("hardBounceCount")` / `…"softBounceCount"…` 收敛为服务商分布链路范围：断言 `extractFn("renderMonitoringProviderDistribution")` 不含两字段，且 `extractFn("renderMonitoringCards")` 的 `worstUndeliveredProvider → const cards` 片段不含两字段；新增定位守卫断言（`start >= 0 && end > start`），避免切片退化为空串而使断言空转。其余用例一字未改（`ProviderStatRow`/列数/表尾/最高未送达卡片 I-9 保护全部保留）。 |
| `src/test/js/senderBindingDisplay.test.js` | F-2 | 删除写死的缓存键字面量：改为从 `index.html` 解析 `styles.css?v=<key>`（与 `taskActivityCenter.test.js:16-20` 同法）得到 `CACHE_KEY`；`static resource cache keys (I-5)` 三条用例分别断言 ①`/\?v=[^"']+/g` 恰 11 个且全部等于 `?v=${CACHE_KEY}`、键格式匹配 `^[0-9]{8}-[a-z0-9-]+$`；②退役键 `20260929-discovery-schedule` 在 `index.html` 零命中；③`task-modal-runtime.js` 仍为 `<script src="task-modal-runtime.js"></script>` 且无 `?v=`。测试内不再出现当前键字面量。 |

产品主代码（`BounceRateMonitorService.kt`、`MailSenderAccountController.kt`、`app.js`、`index.html`）与 4 个 Kotlin 测试文件本 epoch **零改动**：F-1 只需收敛守卫范围，无需改契约字段名；`index.html` 已是 11 处同键。故无 `PLAN_CONFLICT`。

## Commands（worktree 内、JDK 11；全部在最终状态 `4bc9f11` 上执行）

| # | 命令 | 退出码 | 计数 |
|---|---|---|---|
| 1 | `mvn -DskipNodeTests=true -Dtest=BounceRateMonitorServiceTest,MailSenderAccountControllerMvcTest test` | 0（`BUILD SUCCESS`） | `Tests run: 23, Failures: 0, Errors: 0, Skipped: 0`（MvcTest 9 + ServiceTest 14；baseline 13） |
| 2 | `node --test src/test/js/senderBindingDisplay.test.js` | 0 | `tests 14 / pass 14 / fail 0`（epoch-1 为 13；本 epoch 缓存键用例由 2 条拆为 3 条） |
| 3 | `node --check src/main/resources/static/app.js` | 0 | 无输出 |
| 4 | `node --test src/test/js/*.test.js` | 0 | `tests 1233 / suites 243 / pass 1233 / fail 0`（基线 1227 pass；epoch-1 落盘态为 1230 pass / 2 fail） |
| 诊断 | `node --test src/test/js/providerUndeliveredColumn.test.js` | 0 | `tests 21 / pass 21 / fail 0`（epoch-1 该文件 12 pass / 1 fail） |
| 诊断 | `node --test src/test/js/taskActivityCenter.test.js` | 0 | `tests 54 / pass 54 / fail 0`（F-2 的原始红点） |

Java 复跑（命令 1）在最终提交 `4bc9f11` 上再次执行：`BUILD SUCCESS`（Total time 01:58，2026-09-29T20:24:46+08:00）；surefire 回执 `target/surefire-reports/` 同一轮产物：`BounceRateMonitorServiceTest tests=14 errors=0 skipped=0 failures=0`、`MailSenderAccountControllerMvcTest tests=9 errors=0 skipped=0 failures=0` → 合计 23 / 0。

## 不变量与两处修复的证据

- **F-1 非空转证明**（脚本回执）：`renderMonitoringProviderDistribution` 提取体 1725 字符、`worstUndeliveredProvider → const cards` 片段 322 字符（``chain.includes("undeliveredCount") === true``），且两片段对 `hardBounceCount|softBounceCount` 的匹配为 **false**——即断言真实覆盖了原 I-6 的两个消费点，而非退化切片。牙齿证明（对 app.js 内存副本注入，不落盘）：真实文件 → `clean`；在 provider 函数内注入 `row.hardBounceCount` → `provider-chain`（用例失败）；在 `worstUndeliveredProvider` 链内注入 `softBounceCount` → `cards-chain`（用例失败）。
- **F-2 键字面量隔离**：`grep -rn "20260929-bounce-alert" src/test src/main/resources/static` 在本 epoch 修复后仅命中 `index.html` 11 处（修复前另有 `senderBindingDisplay.test.js:386`）；`taskActivityCenter.test.js` 的落单扫描（`static/*.js` + `src/test/js/*.js` 全文件含键即失败）因此转绿。
- I-1…I-5、S-1、IP-1…IP-4 的证据与 epoch 1 报告一致且本 epoch 未触碰相关文件（`ff0d1eb` 的产品行为不变）：epoch-1 报告已由控制器归档于提交 `e5a5d28`（`git show e5a5d28:docs/plans/fast/2026-09-29-bounce-repair-master/children/01/execution.md`，138 行，含逐条 file:line 证据）。

## 偏差

1. 无实质偏差：两处修复均在 A1 授权文件内完成，无产品行为变化，无未授权文件改动。
2. `node --check src/main/resources/static/app.js` 单独执行（任务书第 3 条的笔误片段 `node --unused 2>/dev/null;` 按控制器更正忽略）。
3. 本 epoch 未运行全量 `mvn test`（按 child 03 的门禁延后；任务书仅要求 JS 全量 + 两条 Kotlin 类）。

## 提交

```text
$ git log --oneline -1
4bc9f11 feat(fast-p): implement 01 epoch 2
$ git show --stat --oneline HEAD | tail -3
 src/test/js/providerUndeliveredColumn.test.js | 17 ++++++++++++++---
 src/test/js/senderBindingDisplay.test.js      | 22 ++++++++++++++++++----
 2 files changed, 32 insertions(+), 7 deletions(-)
$ git status --porcelain
 M docs/plans/fast/2026-09-29-bounce-repair-master/children/01/execution.md   # 本报告，按要求不提交
```

提交只含 2 个授权测试文件；未 push / merge / rebase / amend / reset；未改写 `ff0d1eb`；未触碰 `docs/plans/fast/**`（本报告除外）与主检出。
