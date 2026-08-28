# 15 工作台三步界面与运营事实

顺序权威：`10-reply-orchestration-order.md`。依赖 `13-letter-orchestrator.md`（消费其 `paragraphPlan` / `facts` / `paragraphs` 协议）与 `14-workbench-concurrency.md`（复用其条目级持久化与局部遮罩，否则本计划的高频交互会把工作台锁死）。

## 需求描述

**Observable outcome**
1. 工作台的页签从两页（摘要与事实 / 回复框架与整合）扩为三步：**01 逐问处理 → 02 事实集 → 03 编排预览**，复用既有页签组件。
2. 步骤 02 展示**去重后的全信事实集**：一条事实一行，标出被哪几个来问触发、在正文里用了几次、是否受控、是否运营事实；可取消采用、可改主题。
3. 步骤 03 展示按主题分段的正文，每段带 `topic` 与 `factIds`；可编辑、可**锁定**（锁定段在重排时原样带过去）、可上下移、可「并入上段」。
4. 「按回答说明生成」的产出成为一条**逐字运营事实** `op<n>`，出现在步骤 02 的事实集里，与 QA 事实同列，受 13 的第 3 道校验（受控/逐字）保护。

**What must NOT change**
- 步骤 01 的逐问处理界面与七种处理方式恒定开放的口径（`TrustReplyWorkbenchService.kt:2332-2333` `allowedHandlings` 返回 enum 全量）不变；选项从不隐藏或置灰。
- 「回答说明」字段的 500 字上限与 `OPERATOR_INSTRUCTION_HANDLINGS`（`trust-reply-workbench.js:37-40`：`ANSWER_FROM_OPERATOR_INPUT`、`ANSWER_EVIDENCE_WITH_OPERATOR_INPUT`）的适用集合不变。
- 逐问覆盖的审计视图（每问 → factRuleIds → 状态徽标）保留，不被事实集取代。
- `composeLockedItems(orderedAnswers, resolvedFrame)`（`AiReplyPointByPointComposer.kt:34-44`）这条确定性拼装逃生舱保留：运营可逐段定稿并全部锁定，LLM 完全不参与。
- 14 建立的局部遮罩与条目级持久化不被回退。
- `requestKey` 哈希构成不变（G-7）。

**Out of scope**
- 句子级 pinned（需求方明确「先按段落做」）。
- 无依据回答索引的入库与回流 → 16。本计划只在发送后把运营事实交给既有归档入口，不改归档条件。
- 步骤 01 界面本身的重构。

## 关键不变量

### Invariant I-1: 运营事实有独立 id 空间，绝不进 `requestKey` 哈希
- Rule: 运营事实 id 形如 `op1`、`op2`…（本信内递增，与 `requestKey` 无关）。`requestKey = sha256(sourceVersion, index, requestText, intentKeys)`（`TrustReplyWorkbenchService.kt:1702-1715`）的四个输入**一个都不能变**。
- Violation consequence: `trust_reply_workbench_state`（V83）里的历史 requestKey 全部失配，运营已保存的状态整批丢失。
- 来源: G-7 / K-request-key-includes-intent-keys

### Invariant I-2: 运营事实是逐字插槽
- Rule: 运营确认的答案文本以 `PlanFact(id="op<n>", verbatim=true, required=true, body=<运营文本>)` 注入 13 的事实集，走与受控事实**完全相同**的第 3 道校验（`CONTROLLED_BODY_MISSING`）——LLM 只能写它周围的过渡句，不得改动其字。
- Applies to: 步骤 01 的生成产出 → 事实集注入；13 的 `parseUnifiedJson` 逐字子串断言。
- Violation consequence: 运营写的定稿被模型改写，运营以为自己定了稿其实没定。
- 来源: original（13 的 I-4 的下游）

### Invariant I-3: 锁定段的失效判据是它自己的 factIds 版本，不是全信标量
- Rule: 段落 `pinned` 的有效性，必须由**该段所引用的 factIds 各自的证据版本**决定；只要这些事实没变，其他事实的变动不得使该段失效。
- 现状证据：`evidenceSetVersion` 是全矩阵单标量（`TrustReplyWorkbenchService` 的 `assemblyIdentityMatches` 用 `assembly.evidenceSetVersion === state.evidenceSetVersion` 整体比对，`trust-reply-workbench.js:1207-1211`）；条目级的 `TrustReplyRequestCoverage.evidenceSetVersion`（`TrustReplyWorkbenchService.kt:154`，注释「03a (I-1): per-request evidence version for this coverage item」）已经存在，是可用的细粒度接缝。
- Applies to: 段落 pinned 的失效判定；重排请求的构造。
- Violation consequence: 「锁一段改其余」当场退化成「全部重跑」，observable outcome 3 白做。
- 来源: original（`TrustReplyWorkbenchService.kt:152-154`、`trust-reply-workbench.js:1207-1211` 实读）

### Invariant I-4: 步骤 02/03 的高频操作不落库
- Rule: 改主题、取消采用、锁定/解锁、并入上段、上下移，**全部只改前端本地的 `paragraphPlan` 草稿**，不发任何持久化请求。只有点「重排」（触发一次编排调用）与点「整合」时才与服务端交互。
- Applies to: 步骤 02/03 的全部交互处理。
- Violation consequence: 每次点击都产生保存 pending；即使有 14 的条目级持久化，工作台仍会一直在转圈。这是本计划最容易做错的一处。
- 来源: original（14 的 I-1 的下游）

### Invariant I-5: 事实集是派生视图，不是新的事实来源
- Rule: 步骤 02 的表格数据**全部**从 13 的 `plan.facts` + `plan.paragraphPlan` + 既有 `requestFacts` 矩阵派生。前端不得自己拼装事实正文，也不得引入第二份事实清单。
- Applies to: 步骤 02 的渲染。
- Violation consequence: 前端显示的事实与实际进提示词的事实不一致，运营基于错误信息做决策。
- 来源: K-request-facts-not-flat-pool

### Invariant I-6: 逐问覆盖视图保留且语义不变
- Rule: 步骤 01 的每问覆盖徽标（`GROUNDED · 依据充分` / `PARTIAL · 部分有据` / `UNSUPPORTED · 无依据`，`trust-reply-workbench.js:41-45`）与其 `factRuleIds` 列表保持现状。事实集是**新增**的一层，不是它的替代。
- Violation consequence: 审计视图消失，运营无法回答「这一问到底绑了哪些事实」。
- 来源: original

## 样式契约

### S-1: 三步页签（纯复用，零新增 CSS）
- 复用：`.trust-reply-page-nav`（`styles.css:7766`）、`.trust-reply-page-tab`（`:7776`，含 `:hover` `:7796`、`[aria-selected="true"]` `:7802`、`:focus-visible` `:7809`、`:disabled` `:7814`）、`.trust-reply-page-step`（`:7819`，含选中态 `:7833`）、`.trust-reply-page-head`（`:7849`，含 `h3` `:7857`、`small` `:7864`）；窄屏覆盖在 `:8277`/`:8283`、减动效在 `:8306`。
- DOM 结构：沿用 `renderPageTabs()` 现有骨架，把两个 tab 扩为三个，`data-page-panel` 取值 `facts` / `factset` / `compose`：
```html
<nav class="trust-reply-page-nav" role="tablist" aria-label="工作台页面">
  <button type="button" class="trust-reply-page-tab" role="tab" aria-selected="true">
    <span class="trust-reply-page-step">01</span>逐问处理
  </button>
  <button type="button" class="trust-reply-page-tab" role="tab" aria-selected="false">
    <span class="trust-reply-page-step">02</span>事实集
  </button>
  <button type="button" class="trust-reply-page-tab" role="tab" aria-selected="false">
    <span class="trust-reply-page-step">03</span>编排预览
  </button>
</nav>
```
- 禁止项：修改 `.trust-reply-page-*` 任一既有规则块；为第三个 tab 另造 class。

### S-2: 事实集表格（新增，逐字给出）
- 新增：以下规则块**原样复制**到 `styles.css`，插在 `.trust-reply-item-list` 规则块（`styles.css:7390-7395`）之前：
```css
.trust-reply-factset {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.trust-reply-factset th {
  padding: 0 8px 7px;
  border-bottom: 1px solid var(--panel-border);
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
}

.trust-reply-factset td {
  padding: 8px;
  border-bottom: 1px solid var(--panel-border);
  color: var(--text-main);
  vertical-align: middle;
}

.trust-reply-factset tr[data-origin="OPERATOR"] td {
  background: var(--primary-light);
}

.trust-reply-factset tr[data-adopted="false"] td {
  opacity: 0.5;
}

.trust-reply-factset-source {
  color: var(--text-muted);
  font-size: 11px;
}

.trust-reply-factset-source[data-origin="OPERATOR"] {
  color: var(--primary);
  font-weight: 600;
}

.trust-reply-factset-usage {
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
```
- 说明：`--panel-border`（`styles.css:16`）、`--text-main`（`:21`）、`--text-muted`（`:22`）、`--primary`（`:3`）、`--primary-light`（`:8`，`rgba(var(--primary-rgb), 0.07)`）均为既有 token，暗色主题已在 `:9602` 起重定义，故本块无需另写暗色规则。
- DOM 结构：
```html
<table class="trust-reply-factset">
  <thead><tr><th>采用</th><th>事实</th><th>来源</th><th>主题</th><th>用量</th></tr></thead>
  <tbody>
    <tr data-origin="QA" data-adopted="true" data-fact-id="f23">
      <td><input type="checkbox" checked data-action="factset-adopt" data-fact-id="f23"></td>
      <td>企业匹配原则</td>
      <td class="trust-reply-factset-source" data-origin="QA">QA 规则 23</td>
      <td><select data-action="factset-topic" data-fact-id="f23"><option>enterprise</option></select></td>
      <td class="trust-reply-factset-usage">1×</td>
    </tr>
  </tbody>
</table>
```
- 禁止项：inline style；用 `.compose-panel h4` 之类的既有 class 承载表格样式；为「受控事实」另造颜色 class（用 `data-controlled` 属性 + 既有 `.trust-reply-fact-*` 徽标，若不存在则本契约补充块另议）。

### S-3: 段落卡片与锁定态（复用 + 一个修饰符，逐字给出）
- 复用：`.trust-reply-item`（`styles.css:7399-7407`）作为段落卡片容器——它已有 `--item-accent` 自定义属性、`border-left: 3px solid var(--item-accent)`、`position: relative`（局部遮罩定位需要）与 hover 过渡。段落卡片直接复用它，不新建容器 class。
- 新增：以下规则块**原样复制**到 `styles.css`，紧跟在 `.trust-reply-item` 规则块之后：
```css
.trust-reply-item[data-pinned="true"] {
  --item-accent: var(--primary);
  background: var(--primary-light);
}

.trust-reply-paragraph-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0 0 6px;
}

.trust-reply-paragraph-ctl {
  display: flex;
  gap: 4px;
  margin-left: auto;
}

.trust-reply-rerun-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 0 0;
  padding: 9px 12px;
  border: 1px dashed var(--primary);
  border-radius: var(--radius-sm);
  color: var(--primary);
  font-size: 11.5px;
  line-height: 1.5;
}
```
- DOM 结构：
```html
<article class="trust-reply-item" data-pinned="true" data-role="paragraph" data-topic="ip">
  <div class="trust-reply-paragraph-head">
    <span class="trust-reply-page-step">ip</span>
    <span class="trust-reply-paragraph-ctl">
      <button type="button" class="button small secondary" data-action="paragraph-edit">编辑</button>
      <button type="button" class="button small secondary" data-action="paragraph-pin">已锁定</button>
      <button type="button" class="button small secondary" data-action="paragraph-merge-up">并入上段</button>
      <button type="button" class="button small secondary" data-action="paragraph-move-up">↑</button>
      <button type="button" class="button small secondary" data-action="paragraph-move-down">↓</button>
    </span>
  </div>
  <p class="pre" data-role="paragraph-text">…</p>
</article>
<div class="trust-reply-rerun-hint" role="status">重排时仅重写未锁定段落 5 / 6</div>
```
  按钮复用既有 `.button.small.secondary`（`trust-reply-workbench.js:2098` 附近已在用 `button small secondary`）；正文复用既有 `.pre`（`K-mail-body-display-sites` 记录的统一正文类）。
- 禁止项：inline style；修改 `.trust-reply-item` 的既有规则块（只允许通过 `[data-pinned="true"]` 属性选择器加修饰）；为控件按钮另造 class。

> `.trust-reply-item` 的既有使用点（grep `class="compose-panel trust-reply-item"`，`trust-reply-workbench.js:2263`）只有摘要卡片一处。本计划**派生使用**（同 class + 新 data 属性），不修改其规则块，因此不影响摘要卡片。

## 现状审计

### 工作台页面结构（`trust-reply-workbench.js`）
- `renderMarkup()`（`:2031`）产出：只读横幅 → `.trust-reply-toolbar` → `.trust-reply-page-nav`（`renderPageTabs()`）→ 两个 `<section class="trust-reply-page" role="tabpanel">`（`data-page-panel="facts"` 与 `"frame"`）→ `renderBusyOverlay()`。
- `state.activePage` 控制哪一页 `hidden`；`panelId(name)` / `tabId(name)` 生成 id。
- 摘要卡片 `renderItem`（`:2263`）：`<article class="compose-panel trust-reply-item" data-role="item" data-request-key data-coverage data-locked>`，内含 `renderRequestHeader` / `renderFactSection` / 处理方式 `<select>` / 版本 `<select>` / 回答说明 `<textarea maxlength="500">` / `renderItemActions` / `renderItemBusyOverlay`。
- 处理方式标签 `HANDLING_LABELS`（`:19-29`）七项：依据完整回答 / 回答有依据部分 / 按事实原文回答 / 依据+说明混合 / 按回答说明生成 / 确认待补充 / 省略此项。
- 覆盖标签 `COVERAGE_LABELS`（`:41-45`）三项。
- 整合状态 `:77-79`：`LOCAL: 配置预览 · 尚未服务端整合` / `CURRENT: 服务端整合完成` / `STALE: 配置已变化 · 请重新整合`。

### 服务端接口（`TrustReplyWorkbenchController.kt`）
`POST /bootstrap`（:42）、`POST /generations/stream`（:53，SSE）、`POST /generations/{id}/cancel`（:86）、`POST /assemble`（:102）、`PUT /state`（:106）、`DELETE /state`（:110）、`POST /state/reset`（:114）。14 会新增 `PATCH /state/item`。
**本计划需要新增一个「重排」端点**（在既有 `/assemble` 之外，或以参数区分）——它接受运营编辑后的 `paragraphPlan` 草稿 + pinned 段落，触发一次 13 的编排调用并返回新的 `paragraphs`。

### 数据来源
- `TrustReplyRequestCoverage`（`TrustReplyWorkbenchService.kt:140-163`）已含 `factRuleIds`、`intents`、`requestKey`、`allowedHandlings`、`recommendedHandling`、`suggestedInstruction`、`unrecognizedAsks`、`evidenceSetVersion`、`intentMatchedFactRuleIds`、`intentMismatchFactRuleIds`。**步骤 02 的「触发来问」列可直接由 `factRuleIds` 反查得到，无需新增后端字段。**
- 13 的 `plan.facts` / `plan.paragraphPlan` / `plan.topicOrder` 是步骤 02/03 的主数据源（I-5）。

### Interaction points
- **IP-1**：步骤 01 的「按回答说明生成」产出 × 13 的事实集注入 —— 运营文本必须以 `verbatim` 事实进入 `paragraphPlan`，而不是作为一段独立文字直接拼接（I-2）。
- **IP-2**：步骤 02 的 topic 改动 × 13 的 `paragraphPlan` 生成 —— 前端改的是草稿，重排时整体提交；服务端据此重算分组（I-4）。
- **IP-3**：段落 pinned × `evidenceSetVersion` —— 必须走条目级版本，不走全信标量（I-3）。
- **IP-4**：本计划的高频交互 × 14 的持久化粒度 —— 若 14 未先行，步骤 02/03 每次点击都可能触发整封保存（I-4）。

### 前端样式盘点
- 可复用 class：`.trust-reply-page-nav`（:7766）、`.trust-reply-page-tab`（:7776）、`.trust-reply-page-step`（:7819）、`.trust-reply-page-head`（:7849）、`.trust-reply-item-list`（:7390）、`.trust-reply-item`（:7399）、`.compose-panel`（:5473）、`.button.small.secondary`（`.button` :655）、`.pre`（正文统一类，见 K-mail-body-display-sites）、`.trust-reply-item-busy-overlay`（:7410）。
- 设计基准 token 实值：`--primary: #1e40af`、`--primary-light: rgba(var(--primary-rgb), 0.07)`、`--panel-bg: rgba(255,255,255,0.55)`、`--panel-border: rgba(15,23,42,0.08)`、`--text-main: #1e293b`、`--text-muted: #94a3b8`、`--radius-sm: 7px`、`--radius-md: 10px`；暗色覆盖 `:9602-9608`。
- DOM 结构约定：页签由 `renderPageTabs()` 生成、面板由 `renderMarkup()` 的两个 `<section class="trust-reply-page">` 承载、按钮走 `data-action` 委托。
- 改动前基线：`renderMarkup()` 当前恰有两个 `<section class="trust-reply-page" role="tabpanel">`，`data-page-panel` 取值为 `facts` 与 `frame`；标题分别是「摘要与事实」（small：按原邮件顺序展示摘要卡片，每张卡片绑定对应事实；可添加或删除事实。）与「回复框架与整合」（small：选择尊语、开场白、致谢语与结束语；只有服务端整合完成的结果才能完成本页。）。

## 实现方案

### T-1：三步页签（S-1 / I-6）
`renderPageTabs()` 与 `renderMarkup()` 扩为三页；`state.activePage` 增加 `"factset"`。步骤 01 面板内容**完全不动**（保留摘要卡片与逐问覆盖，I-6）。步骤 03 面板取代原「回复框架与整合」页的预览区，框架选择器保留在步骤 03 顶部。

### T-2：事实集视图（S-2 / I-5）
从 13 的 `plan.facts` + `plan.paragraphPlan` + 既有 `requestFacts` 派生表格行；「触发来问」列由 `requestFacts` 中 `factRuleIds` 包含该事实的 request index 列表得到。勾选/主题下拉只改本地草稿（I-4）。

### T-3：运营事实 `op*`（I-1 / I-2 / IP-1）
步骤 01 的「按回答说明生成」成功后，把该条的 `answerText` 包装为 `PlanFact(id="op<n>", topic=<该问主题>, verbatim=true, required=true, body=answerText)` 加入本地事实集草稿。`op<n>` 的编号在本信内递增，**不进入任何哈希**（I-1）。重排时随 `paragraphPlan` 一并提交。

### T-4：段落编辑与 pinned（S-3 / I-3 / I-4）
段落卡片的编辑/锁定/并入上段/上下移只改本地草稿。pinned 段落在重排请求中以 `{topic, factIds, text, pinned: true}` 提交，服务端原样回填并要求 LLM 不改动它。pinned 失效判定读该段 factIds 对应的条目级 `evidenceSetVersion`（I-3）。

### T-5：重排端点
新增服务端端点接受 `{source, sourceVersion, paragraphPlanDraft, pinnedParagraphs, operatorFacts}`，调用 13 的编排链路，返回新的 `paragraphs` 与六道校验结果。**不落库**——落库仍走整合（I-4）。

### T-6：测试
1. 前端：事实集行数 == 去重后事实数；同一 ruleId 被多问触发时只有一行且「触发来问」列含全部 index。
2. 前端：勾选/改主题/锁定/并入上段各触发 0 次网络请求（以 fetch 桩计数断言，I-4）。
3. 前端：`mount` 后三个 tab 均可切换，步骤 01 的摘要卡片与覆盖徽标渲染不变（I-6 回归）。
4. 后端：重排端点对 pinned 段落原样回填；对非 pinned 段落重新编排。
5. 后端：运营事实 `op*` 注入后，13 的第 3 道校验（逐字）对其生效——改一个字即校验失败。

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/resources/static/trust-reply-workbench.js` | 修改（T-1～T-4） |
| 2 | `src/main/resources/static/styles.css` | 修改（S-2、S-3 的新增规则块） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt` | 修改（T-5 新端点） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 修改（T-5、T-3 的 op* 装配） |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedContentPlanner.kt` | 修改（接受 `operatorFacts` 与 `paragraphPlanDraft` 覆盖值） |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | 修改（T-6.4、T-6.5） |
| 7 | `src/test/js/trustReplyWorkbenchThreeStep.test.js` | 新增（T-6.1、T-6.2：事实集去重行、四类交互零请求） |
| 8 | `src/test/js/trustReplyWorkbench.test.js` | 修改（T-6.3：三页签切换 + 步骤 01 渲染回归） |

合计 8 个文件，2 个子系统（静态前端 / 工作台后端）。

测试落点的依据（实测）：`trustReplyWorkbench.test.js`（975 行，17 处 `mount(`、21 处 `bootstrap`）是工作台渲染契约的现有归属地，三页签与步骤 01 回归写在这里；事实集与段落交互是全新表面，新建 `trustReplyWorkbenchThreeStep.test.js` 承载，避免把 975 行的既有文件撑成两千行。

## 验证命令

> 本项目是 Kotlin + Spring Boot 2.7 (Java 11) Maven 工程，**必须用 JDK 11 (zulu-11)**，裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关后端测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchServiceTest,AiReplyGroundedContentPlannerTest

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 前端 JS 用例（node:test；本计划的权威门禁）
node --test src/test/js/trustReplyWorkbench.test.js
node --test src/test/js/trustReplyWorkbenchThreeStep.test.js

# 前端 JS 全量回归
node --test src/test/js/*.test.js

# 空白/换行卫生
git diff --check
```



> **JS 门禁与 `mvn test` 的关系**：`pom.xml:190-201` 的 exec-maven-plugin 把
> `bash -lc 'node --test src/test/js/*.test.js'` 绑在 `test` phase，因此 `mvn test`
> 名义上覆盖 JS 用例；但该 execution 带 `<skip>${skipNodeTests}</skip>` 而
> `skipNodeTests` 在 `pom.xml:19-25` 的 `<properties>` 中**未定义**（K-js-test-invocation-surface
> 记为推断）。**本计划的权威门禁是上面的 `node --test <file>` 单跑命令**，`mvn test`
> 作为全量回归另列。首次执行须确认 `mvn test` 输出里出现 `node --test` 记录。
> `verify.sh` 只跑 `normalizeDiscoveryResultSummary.test.js` 一个文件，**不可用作前端门禁**。

通过判据：`mvn test` 退出码 0，输出 `Tests run: N, Failures: 0, Errors: 0`。
来源：Maven 命令取自项目根 `CLAUDE.md` 的 `## Commands`（逐字照抄）；JS 命令取自 `pom.xml:190-201` 的 exec 绑定与 `docs/knowledge/frontend/K-js-test-invocation-surface.md`，目标文件名经 `ls src/test/js/` 实测确认。

## 验收标准

- **I-1**：`grep -rn "requestKey(" src/main/kotlin/.../TrustReplyWorkbenchService.kt` 的哈希输入仍为四项；测试断言加入 `op*` 事实前后同一封信的 requestKey 完全相同。
- **I-2**：T-6.5 通过（改一个字即校验失败）。
- **I-3**：T-6.4 通过；且重排请求体中 pinned 段落携带的是条目级 `evidenceSetVersion` 而非全信标量（grep 断言）。
- **I-4**：T-6.2 通过（四类交互各 0 次请求）。
- **I-5**：T-6.1 通过；前端代码中不存在第二份事实正文来源（grep 无硬编码 answerBody 字面量）。
- **I-6**：T-6.3 通过。
- **S-1**：`git diff styles.css` 不含 `.trust-reply-page-*` 任一规则块改动。
- **S-2 / S-3**：`styles.css` 新增规则块与契约代码块**逐字一致**（diff 比对）；`git diff` 中 `.trust-reply-item` / `.trust-reply-item-list` / `.button` / `.compose-panel` 的既有规则块无改动；全文无新增 `style="`。
- **回归**：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 三步页签可用且步骤 01 未变
- 前置条件：一封已点「开始分析」的多问来信。
- 操作步骤：依次点三个页签，回到 01 检查摘要卡片。
- 预期结果：三个页签标号为 01/02/03，标题「逐问处理」「事实集」「编排预览」；步骤 01 的摘要卡片、七种处理方式下拉、回答说明框（500 字上限）、覆盖徽标与改造前**完全一致**。
- 覆盖：observable outcome 1；I-6；What must NOT change 第 1、2、3 项

### A-2: 事实集去重且标出触发来问
- 前置条件：一封中至少有一条事实被两个不同问题触发（如「申请流程」同时被第 1 问和第 3 问命中）。
- 操作步骤：切到步骤 02，找到该事实行。
- 预期结果：该事实**只有一行**；「触发来问」列显示两个来问编号（如 `R1 · R3`）；「用量」列显示 `1×`。
- 覆盖：observable outcome 2；I-5

### A-3: 运营事实进入事实集并逐字保护
- 前置条件：一封含至少一个 `UNSUPPORTED` 摘要的来信。
- 操作步骤：
  1. 步骤 01 对该摘要选「按回答说明生成」，填写说明，点生成
  2. 切到步骤 02
  3. 切到步骤 03，找到引用该事实的段落，与第 1 步的产出逐字比对
- 预期结果：步骤 02 出现一行 `op1`，来源列显示「运营 · 逐字」且底色区别于 QA 事实；步骤 03 的段落中，运营产出的文字**一字不差**地出现（前后可有过渡句）。
- 覆盖：observable outcome 4；I-2；IP-1

### A-4: 锁定一段后重排不改动它
- 前置条件：步骤 03 已有 ≥3 段。
- 操作步骤：
  1. 记下第 2 段的完整文字
  2. 点第 2 段的「锁定」
  3. 回步骤 02 取消采用某条**不属于第 2 段**的事实
  4. 点「重排」
- 预期结果：第 2 段文字**逐字未变**；提示条显示「重排时仅重写未锁定段落 N / M」；其余段落重新衔接。
- 覆盖：observable outcome 3；I-3

### A-5: 改 topic 即可改分段
- 前置条件：步骤 02 有一条事实当前落在 A 主题。
- 操作步骤：把它的主题下拉改到 B，点「重排」。
- 预期结果：该事实的内容出现在 B 主题的段落里；改动只需一次重排，无校验失败。
- 覆盖：observable outcome 2、3；IP-2

### A-6（回归）: 步骤 02/03 的操作不发请求
- 前置条件：devtools Network 面板打开并清空。
- 操作步骤：在步骤 02 连续做 3 次勾选/取消、2 次改主题；在步骤 03 做 1 次锁定、1 次并入上段、2 次上下移。**期间不点「重排」。**
- 预期结果：Network 中**没有任何**发往 `/api/trust-reply/workbench/*` 的请求；页面没有任何遮罩。
- 覆盖：I-4；IP-4

### A-7（回归）: 全手动逃生舱仍可用
- 前置条件：步骤 03 已有段落。
- 操作步骤：逐段编辑并全部锁定，然后整合。
- 预期结果：整合产出的正文由锁定段按顺序逐字拼接（加上尊语/开场/致谢/结束语框架），LLM 未参与改写。
- 覆盖：What must NOT change 第 4 项

### A-8（回归）: 并发与遮罩未回退
- 前置条件：14 已上线。
- 操作步骤：重做 14 的 A-1（两条摘要同时操作）。
- 预期结果：与 14 的 A-1 预期一致——局部遮罩，其余摘要可操作。
- 覆盖：What must NOT change 第 5 项；IP-4

### A-9（回归）: 样式未失真
- 前置条件：三步界面已渲染。
- 操作步骤：对照「前端样式盘点 · 改动前基线」与样式契约逐项目测：页签的 01/02/03 标号样式与原两页一致；段落卡片的左侧 3px 竖条在锁定态变为主色 `#1e40af` 且底色变为 `--primary-light`；事实集表头 11px 灰色、行高与内边距 8px；再切暗色主题重看一遍。
- 预期结果：无白底黑字/黑底黑字；既有摘要卡片外观与改造前无差异。
- 覆盖：S-1；S-2；S-3
