# 2026-08-20 回复台三刀 · 执行顺序（权威）

本目录下 2026-08-20 共 **5 份**计划，分属两条互不相干的线。**本文件是顺序权威**，与任何单份计划正文冲突时以本文件为准。

## 基线声明（所有计划共用）

- Git 基线：`main` @ `08a25fe`
- **工作树非干净**：`workbench-operator-instruction-authorizes-actions.md` 正在实施中，以下文件为 `M` 且未提交：
  `AiReplyActionPolicy.kt`、`AiReplyDraftService.kt`、`TrustReplyWorkbenchService.kt`、`PendingMailOperationService.kt`、
  `AiReplyDraftServiceTest.kt`、`TrustReplyWorkbenchItemFlowTest.kt`、`PendingMailOperationServiceTrustWorkbenchTest.kt`
- **本目录 P0/P1/P2a/P2b 四份计划的行号取自 2026-08-20 07:34 UTC 的工作树。**
  该实施仍在进行，行号会继续漂移。**每份计划的落点都同时给了函数/符号名，执行 agent 必须先用符号名重新 grep 定位，行号只作交叉验证。** 行号对不上不是缺陷，符号找不到才是。

## 两条线

```
线 A（已在实施，非本目录新增）
  workbench-operator-instruction-authorizes-actions.md   ← 运营回答说明作为动作授权来源

线 B（本次新增，四份）
  P0 → P1 → P2a → P2b
```

**线 A 与线 B 可以并行**，文件交集只有 `AiReplyDraftService.kt` 与 `TrustReplyWorkbenchService.kt`，且改动区段不重叠：

| 文件 | 线 A 改哪里 | 线 B 改哪里 |
|---|---|---|
| `AiReplyDraftService.kt` | `generateOperatorDirectedAnswer` 的 `deriveAllowed` 行与 system prompt | `data class RequestFactItem`（P2a）、`generateItem` 的 `ResolvedQaRules` 构造（P2b） |
| `TrustReplyWorkbenchService.kt` | `validateLockedItem` 的 `ANSWER_FROM_OPERATOR_INPUT` 分支、新增 `operatorAuthorizedActions` | `deleteState` 邻域（P0）、`resolveCanonicalSelection` / `canonicalMatrix` / `toCoverage`（P1/P2a） |

> 并行的前提：**线 A 先提交**。线 B 的任何一刀都不要在线 A 未提交时开始，否则两边的未提交改动混在同一棵工作树里，`git diff --name-only` 的越界核对（各计划的最后一条人工验收）会失效。

## 线 B 四刀的顺序与理由

### P0 — `P0-sse-error-code-and-state-reset.md`
**必须最先。** 它不修任何业务缺陷，但把两件事从"不可诊断"变成"可诊断"：
1. SSE 逐条生成失败时把真实 `code` 透出来并记日志（现在一律 `"AI generation failed"` 且零日志）。
2. bootstrap 失败时给出一个不依赖 `savedStateVersion` 的强制重置入口（现在 UI 无自救路径，只能删库或等 30 天 TTL）。

P1 与 P2a 的人工验收都要求"看到具体错误码"，没有 P0 就只能靠猜。

### P1 — `P1-fact-binding-drop-not-fatal.md`
**止血，不是修复。** 把「手动绑定的事实未被采纳」从致命 422（工作台整个打不开）降级成"丢弃 + 逐条目可见提示"。
手动绑定仍然不生效，但运营不会再把工作台搞崩，且能看懂原因。

### P2a — `P2a-bound-vs-evidence-split.md`
**真修的第一半。** 给 `RequestFactItem` 引入 `boundRuleIds`（运营绑了什么），与 `factRuleIds`（系统认可什么作为证据）分开；只切换"运营视角"的 4 个消费点。做完这一刀，手动绑定的事实**不再被丢弃**，chips 保留，版本身份跟随绑定。

**注意 P1 提示的语义迁移**：P1 引入的那条提示（`data-role="item-facts-dropped"`）在 P2a 之后**触发条件不变、语义与文案改变**——从"未被采纳（已丢弃）"变成"已绑定但不作为依据"。P2a 的 I-6 与 S-1 负责重写它，**不得**删除该提示，也**不得**保留 P1 的旧文案（chips 里明明有、下面写着"未被采纳"会自相矛盾）。

### P2b — `P2b-bound-facts-into-prompt.md`
**真修的第二半。** 让绑定但未成为证据的事实真正进入 AI 上下文，同时**不进** `sendQaRuleIds`（外发审计只记真证据）。

**P2b 是四刀里唯一跨线的一刀，必须最后做。** 两个原因，都写在该计划的现状审计里：
1. 只改 `promptRuleIds` 对 `UNSUPPORTED` 条目**零效果**——`generateItem` 里 `OMIT` / `ACKNOWLEDGE_PENDING` / `ANSWER_FROM_OPERATOR_INPUT` 三个分支全部在构造 `ResolvedQaRules` **之前**就 return，而这三者正是 `UNSUPPORTED` 的全部允许集。因此 P2b 必须**额外**给 operator-directed 的 prompt 加一条事实通道。
2. 加事实通道会**修订线 A 已落地的 system message 契约**（把"事实只能来自 answer basis"放宽为"answer basis 或已绑定事实"），并需要**更新线 A 的一个既有测试用例**。
   线 A 的动作约束（`Do not introduce any outbound action that the answer basis does not state.`）**必须原样保留**——事实通道不得成为动作的授权来源。

→ **P2b 开工前必须确认线 A 已合并且测试全绿。**

### 为什么 P2 必须拆成 a/b

`.factRuleIds` 在 `src/main` 有 **30 处**读点，跨 10 个文件（实测：
`grep -rn "\.factRuleIds" --include=*.kt src/main | wc -l` → 30）。一刀全改会超过 create-p 的 10 文件上限，
且把"矩阵/版本身份"与"prompt/审计/打分"两类语义混在同一次验证里。
P2a 只动前者（行为可独立验收），P2b 只动后者。

## 需求方已拍板的决策（不得在实施中重新讨论）

1. **手动绑定不改变条目 status。** 绑了事实的 `UNSUPPORTED` 条目**仍然是 `UNSUPPORTED`**。
   理由：改 status 会连带改 `allowedHandlings`，使「按回答说明生成」从该条目消失，与线 A 正面冲突。
   → 这条是 P2a 的 I-2 与 P2b 的 I-2。
2. **外发审计只记真证据。** 绑定但未成为证据的事实进 `promptRuleIds`，不进 `sendQaRuleIds`。
   → 这条是 P2b 的 I-1，来源 [[K-ai-reply-prompt-vs-send-rule-ids]]。

## 验证命令（四份计划共用，此处为唯一权威文本）

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。来源：项目根 `CLAUDE.md` 「Commands」（`:5-28`）与项目元信息 `test_command:`（`:140`）/ `build_command:`（`:142`）。
> JS 测试来源：`docs/knowledge/build/K-js-tests-run-via-exec-plugin.md`（依据 `pom.xml:186-232`，2026-08-19 实测）。

```bash
# 全量测试
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 前端全部用例
node --test src/test/js/*.test.js

# 单个前端文件
node --test src/test/js/trustReplyWorkbench.test.js

# app.js / workbench js 语法检查
node --check src/main/resources/static/app.js
node --check src/main/resources/static/trust-reply-workbench.js

# 空白/换行卫生
git diff --check
```

**通过判据**
- Maven：退出码 0，`Tests run: N, Failures: 0, Errors: 0`；构建 `BUILD SUCCESS`。
- node --test：退出码 0，输出含 `# fail 0`。
- node --check：退出码 0，无输出。
- `git diff --check`：无输出。

各计划的 `## 验证命令` 只列**本刀新增测试类/用例的单独运行命令**，全量与构建一律引用本文件。
