# Repair Plan: trust-reply-unsupported-answer-v1-02-shared-workbench-ux

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `docs/plans/2026-07-29/trust-reply-unsupported-answer-v1-02-shared-workbench-ux.md`
Verification report: review-p phase2 re-verification, 2026-07-29, `FAIL` / `DIVERGING`
Implementation boundary: `HEAD 6b3b92c` to the reviewed phase2 working-tree delta (eight authorized, unstaged files); unrelated staged documentation relocations excluded.
Human scope decision: 2026-07-29 approved targeted local synchronization of the version selector and answer body without replacing the focused textarea.

## Objective

操作员修改无依据回答说明后，文本框保持焦点，旧 active/resolved 版本与 assembly 立即失效，版本下拉、回答正文、动作和摘要在同一交互周期内与状态机一致。

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-2 | P1 | I-2：`activeVersionId` 必须代表版本下拉当前预览，说明变化必须清除不再匹配的 active/resolved/assembly；I-5：可见状态必须与状态机同步 | `onInput` 已清空 `activeVersionId`，但 `syncInstructionUi` 只更新 item 属性、header、actions 和 summary，未清空版本下拉或旧回答正文。 |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-1 | 最新复核已证明输入不再全量重绘，连续输入焦点回归测试通过；已解决。 |

## Unchanged Contract

- 保持 `TrustReplyWorkbench.mount/unmount` API、训练/真实固定 host adapter 和统一内部状态机。
- 说明变化仍须清除 `activeVersionId`、`resolvedVersionId`、assembly 并触发 `onChange`；其他 request 状态不得改变。
- 不替换当前 textarea 或包含它的 item body；连续输入和 IME 焦点必须保持。
- 不修改 `app.js`、`styles.css`、后端生成/assemble/翻译合同或发送链路。
- 翻译仍为易失展示状态，不能触发决定变化。

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/resources/static/trust-reply-workbench.js` | 补全说明输入后的局部 UI 同步，同时保持 textarea 焦点。 |
| `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 扩展现有焦点测试，证明下拉、回答、动作、摘要和相邻项同步正确。 |

## Repair Tasks

### R-1: 补全说明变化后的局部 UI 同步

- Resolves: V-2
- Root cause: `src/main/resources/static/trust-reply-workbench.js:741-747` 清空 active decision 后调用的 `syncInstructionUi`（934-946）没有更新 `renderRequest` 中的版本下拉和回答区域（955-962）。
- Files: 仅 Authorized Files。
- Change: 保留现有 active/resolved/assembly 失效逻辑；在不替换 textarea 或 item body 的前提下，局部把目标项版本下拉置空、回答区域切换为“尚未生成版本”，并同步 header、主动作与 summary。其它 request 的版本、采用状态、译文和展开状态不变。
- Regression test: 扩展现有连续输入测试；第二次输入后立即断言 textarea 仍为 `activeElement`、目标项版本下拉为空、旧回答不可见、回答区显示“尚未生成版本”、主动作显示“AI 生成回答”、旧 assembly 不可见、第二项仍为已采用；随后 `ADJUST_ITEM.operatorInstruction` 等于完整连续文本。
- Existing verification: 运行 focused JS suite，然后执行本基线全部命令。
- Must not change: I-1～I-9、S-1～S-4；尤其是 textarea 焦点、连续输入、disabled gate、escape、翻译和 canonical assemble 语义。
- Prohibited: 不重绘 textarea/item body，不添加 CSS、全局 DOM 选择器、页面专属状态机或后端变更；不在修复中实施其它计划项。

## Verification Commands

1. `node --test src/test/js/trustReplyWorkbenchSharedMount.test.js`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=TrustReplyWorkbenchItemFlowTest test`
3. `node --check src/main/resources/static/trust-reply-workbench.js`
4. `node --test src/test/js/trustReplyWorkbench.test.js`
5. `node --test src/test/js/aiReplyLoadingFeedback.test.js`
6. `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js`
7. `node --test src/test/js/*.test.js`
8. `git diff --check`

## Completion Criteria

- 连续键盘输入不丢失 textarea 焦点，生成请求携带完整说明。
- 同一项说明变化立即使其 active/resolved version 与 assembly 失效。
- 同一交互周期内，目标项版本下拉为空、旧回答不可见、回答区显示“尚未生成版本”、主动作显示“AI 生成回答”。
- 其它项的版本、采用状态、译文和展开状态不变。
- 回归测试能在当前 V-2 实现失败、修复后通过。
- 仅修改 Authorized Files（及本 repair artifact），且所有 Verification Commands 通过。

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
