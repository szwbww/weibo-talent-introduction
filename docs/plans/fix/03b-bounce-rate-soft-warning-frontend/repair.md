# Repair Plan: 03b-bounce-rate-soft-warning-frontend

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-09-03/03b-bounce-rate-soft-warning-frontend.md
Verification report: review-p 2026-09-03 (this handoff; see session output above)
Implementation boundary: HEAD 29ca5d8 → working tree, plan-scope files only

## Objective
Restore `senderBindingDisplay.test.js` to the plan-mandated structure: three sibling `it()` cases under `describe("senderBindingDisplay accounts table")`, with the pre-existing "account row renders bound expert count" assertions (`<td>12</td>`, `<td>0</td>`) intact, and the two new warning tests added alongside — no test nesting, no lost coverage.

## Findings in Scope
| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | Plan Task 1 "在 `senderBindingDisplay accounts table` describe 内新增用例"（新增，不改动既有用例）；What must NOT change #1 绑定专家数展示的回归防御；I-3/验收回归标准 | 单一编辑把两个新 `it()` 插进既有 "account row renders bound expert count" 的回调内部并删除了该用例原有的两条断言 `assert.ok(html.includes("<td>12</td>"))` 与 `assert.ok(html.includes("<td>0</td>"))`，在文件末尾补了额外的 `});` 闭合。结果是：该用例变成零断言容器，`boundExpertCount` 单元格渲染（12 / 缺失字段 0）失去全部自动化防御；新用例成为其嵌套子测试，结构偏离计划的 describe 同级新增。 |

## Findings Excluded
| Finding | Reason |
|---|---|
| 无 | — |

## Unchanged Contract
- 生产代码、index.html、其余 6 个缓存键测试文件不动（app.js / index.html 本次验证全 PASS，无修复项）。
- 两个新用例的沙箱数据与断言逐字保留（WARN_ONLY、FAULT 两行与 8 条断言）。
- 不改 `.badge` 相关、不改 `loadAccounts` 行为、不新增 CSS/接口/字段。
- 修复只发生在 `src/test/js/senderBindingDisplay.test.js` 一个文件。

## Authorized Files
| File | Purpose |
|---|---|
| src/test/js/senderBindingDisplay.test.js | 恢复断言 + 把新用例提到 describe 同级（纯结构调整与两行断言还原） |

## Repair Tasks

### R-1: 还原 account-row 用例并解除测试嵌套
- Resolves: V-1
- Root cause: 实现编辑将新 `it()` 嵌入既有用例回调并删除其断言（见基线 diff：`-assert.ok(html.includes("<td>12</td>")); -assert.ok(html.includes("<td>0</td>"));`，新用例在其后开始且多出一对闭合 `});`）。
- Files: `src/test/js/senderBindingDisplay.test.js`（唯一授权文件）
- Change: 使 `describe("senderBindingDisplay accounts table")` 下恰好三个平级 `it()`：
  1. `"account row renders bound expert count"` — 回调在 `await sandbox.loadAccounts(); const html = ...` 后以原两条断言结束并正常闭合：`assert.ok(html.includes("<td>12</td>"));`、`assert.ok(html.includes("<td>0</td>"));`
  2. `"renders 硬退率过高 warn badge without resume action when only warning"` — 现有实现逐字保留
  3. `"keeps 自动暂停 and resume action orthogonal to 硬退率过高 warning"` — 现有实现逐字保留
  describe 后仅保留一层顶层闭合。删除因嵌套而多出的末尾 `});`。
- Regression test: 恢复的断言即判别性回归测试——`loadAccounts` 渲染破坏 `boundExpertCount`（12 或缺失字段 0）时 `node --test src/test/js/senderBindingDisplay.test.js` 必须失败；结构判别：测试输出中三个 `it` 必须平级出现在 `▶ senderBindingDisplay accounts table` 之下（每个 `✔` 各占一行），不得嵌套在 `▶ account row renders bound expert count` 内部。
- Existing verification: `node --check src/test/js/senderBindingDisplay.test.js`；`node --test src/test/js/senderBindingDisplay.test.js`（预期 8 条用例全绿：5 list + 3 accounts）；`node --test src/test/js/*.test.js`（预期 671 全绿）。
- Must not change: 不改动两个新用例的内容/沙箱/断言；不改 app.js、index.html、styles.css、其余测试文件；不动任何生产行为。
- Prohibited: 不允许改用例语义、删除或弱化断言、把恢复断言替换成等价新写法以外的结构；不新增文件。

## Verification Commands
1. `node --check src/test/js/senderBindingDisplay.test.js`
2. `node --test src/test/js/senderBindingDisplay.test.js` — 预期 `# fail 0`，且输出中三个 accounts-table `it` 平级（无嵌套缩进）
3. `node --test src/test/js/*.test.js` — 预期 671 pass / 0 fail
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=$JAVA_HOME/bin:$PATH mvn test package` — 预期 BUILD SUCCESS（PASS 前完整门禁）

## Completion Criteria
- `senderBindingDisplay.test.js` 中 `html.includes("<td>12</td>")` 与 `html.includes("<td>0</td>")` 断言恢复且位于 account-row 用例内。
- `describe("senderBindingDisplay accounts table")` 下恰有三个平级 `it()`，无嵌套；新增用例文本与现实现逐字一致。
- 上述 1–4 全部通过；变更文件仍只在授权清单内。

## Human Approval
Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
