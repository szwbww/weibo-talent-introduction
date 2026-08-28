# 12 整封收口（确定性）：在 assemble 处去重、按主题重排、单 CTA

顺序权威：`10-reply-orchestration-order.md`。依赖 `11-fact-supply.md`。
**本计划零新增 LLM 调用**——全部是 assemble 处的确定性后处理。它先拿掉「重复 / 段落=条目 / 双 CTA」这三样，`13-letter-orchestrator.md` 再用一次编排调用补上连接词与因果链。

## 前提更正（2026-08-28 实测，推翻本轮早期判断）

「一键预判」的正文**不经过** `AiReplyDraftService` 的 `GroundedContentPlan` / `composeFromPlan` 路径。真实链路：

```
一键预判 = 工作台 data-action="auto-run"
  → autoRun()                       trust-reply-workbench.js:1363
  → runItemSequence(keys)           :894   逐条串行，每条一次 POST /workbench/generations/stream
      → TrustReplyWorkbenchService.generate()          :1102
  → assemble() → POST /workbench/assemble
      → verifyAssembly()                               :1396
          → orderedAnswers = versions.mapNotNull { answerText }   :1466-1468
          → composeLockedItems(orderedAnswers, resolvedFrame)     :1472
```

`AiReplyPointByPointComposer.composeLockedItems`（`:34-44`）是
`salutation / greeting / acknowledgement / 逐条 answerText / closing` 用 `"\n\n"` 拼接——**零去重、零动作对账、零整封视角**。

而 `TrustReplyWorkbenchService.kt:1462-1464` 写着：
> 计划 02 (I-6): 跨 item 重复 claim 查重**已删除**——同一事实可合法绑定多个 request

绑定层面允许多对多是对的；**文本层面生成多遍**是另一件事，当时被并作一件处理了。这就是重复的成文根因。

因此本计划的落点是 `verifyAssembly` 的 `:1425-1472`，不是 `AiReplyDraftService`。
（详见 `docs/knowledge/llm/K-oneclick-assembles-by-concatenation.md`）

## 需求描述

**Observable outcome**
1. 同一条 QA 事实在一封回信里最多出现一次。当前「申请流程三步」出现 3 次、「salary and funding support」出现 3 次。
2. 正文段落按**主题**归并，不再是「一条摘要一段」。
3. 全信恰好一处 CTA。当前逐条各自可带 CTA，assemble 不做任何动作对账。

**What must NOT change**
- 逐条生成链路（`runItemSequence` → `/generations/stream` → `generate()`）一行不改。
- 逐条锁定语义不变：`TrustReplyItemVersion` 的字段、`versionId` 的算法、`requestKey` 的哈希构成（G-7）全部不动。
- `canonicalFactIds = selection.sendQaRuleIds`（`:1488`）——审计规则集来自**选择**而非文本形态，本计划不得改动它（K-ai-reply-prompt-vs-send-rule-ids）。
- `draftHash = sha256Hex(raw)`（`:1485`）仍对最终 `raw` 求值。
- `validateGroundedTrustBoundary(selection.requestFacts, groundedSections)`（`:1461`）在收口**之前**执行，判据不变。
- 全部条目 handling 为 `OMIT` 或全部锁定时的行为：见 I-6 的逃生舱条款。
- `resolveFrameForAssemble` 与 frame 的插入位置、顺序不变（K-manual-frame-three-consumers）。

**Out of scope**
- 编排 LLM 调用、`paragraphs` 协议、六道校验 → 13。
- 前端 → 14 / 15。
- `Application process` / `Agency credentials and government cooperation` / `Partner company information` 三条的正文改写——需求方尚未提供这三条的 `reply_body` 逐字基线（提供的是冻结三条 1/3/21 的正文），无基线不得改写（G-3）。

## 关键不变量

### Invariant I-1: 收口的输入是 claims，不是 answerText 字符串
- Rule: 整封收口必须基于 `TrustReplyItemVersion.claims: List<AiReplyItemClaim>`（每项含 `intentKey` / `text` / `sourceRuleIds`，`TrustReplyWorkbenchService.kt:287`），**不得**对 `answerText` 做字符串级去重。
- 理由：同一事实被两条摘要各写一遍时措辞不同，字符串比对抓不到；`sourceRuleIds` 才是身份。
- Applies to: `verifyAssembly` 新增的收口步骤。
- Violation consequence: 去重形同虚设——这正是 `composeFromSections`（`AiReplyPointByPointComposer.kt:71`）的 `linkedSetOf<String>()` 已经踩过的坑，它只能去掉逐字相同的句子。
- 来源: original（`TrustReplyWorkbenchService.kt:282-299`、`AiReplyPointByPointComposer.kt:69-88` 实读）

### Invariant I-2: 去重身份是 `sourceRuleIds` 集合，保留首次出现
- Rule: 两条 claim 的 `sourceRuleIds` 集合相等时视为同一事实，**保留 canonical order 中首次出现的那条**，丢弃后续。`sourceRuleIds` 为空的 claim（无依据/运营自撰）**不参与去重**，一律保留。
- canonical order = `versions` 的顺序，即 `orderedItems` 的顺序，即摘要按原邮件顺序（`selection.requestFacts.sortedBy { it.index }`，`:1430`）。
- Violation consequence: 丢弃了后出现但措辞更好的那条，或误删无依据条目。
- 来源: original

### Invariant I-3: 主题归并不得改变事实的相对顺序
- Rule: 段落按主题聚合，主题顺序 = 该主题**首个存活 claim** 在 canonical order 中的位置；主题内 claim 顺序 = canonical order。主题取 `intentKey.substringBefore('.')`。
- 理由：运营在步骤 01 看到的是按来信顺序排列的摘要；输出顺序与之保持单调一致，运营才能对得上。
- Violation consequence: 运营无法把输出段落对应回哪一问，审计与人工复核都变难。
- 来源: original

### Invariant I-4: CTA 收口保留最后一处，且必须是被授权的动作
- Rule: 收口时用 `AiReplyActionPolicy.detectActions(text)` 逐段检测动作句；保留**最后一个**出现的动作句所在的那一处，其余动作句从文本中移除。保留的动作必须在该次 assemble 的授权集合内，否则整体移除并记 warning。
- 现状：`AiReplyDraftService.kt:1707` 的 `declaredActions.size != 1` 与 `:1978` 的动作对账**只在 `composeFromPlan` 路径生效**，assemble 路径完全不经过。本计划补上这一层。
- Applies to: `verifyAssembly` 的收口步骤。
- Violation consequence: 一封信出现两个「请给简历 / 约个电话」，读起来像群发模板。
- 来源: original（`AiReplyDraftService.kt:1700-1710` 与 assemble 链路的差异实读）

### Invariant I-5: 冻结事实的正文里自带 CTA，必须特殊处理
- Rule: `qa_rule` id 21 `Meeting arrangement` 的 `reply_body` 末尾含 `Could you please let us know when you would be available?`——**这是一条被冻结、不可改正文的事实，而它自身包含动作句**。当该事实被选中时：
  - 若它是全信唯一的动作来源 → 它就是那个 CTA，保留整段，不再另加 CTA。
  - 若还有其他动作句 → 按 I-4 保留最后一处；**若被保留的是别处**，`Meeting arrangement` 的动作句仍不得删改（它是受冻结保护的事实正文），此时应放弃删除并记 warning，交人工。
- 同类风险：id 1 `About the talent program` 的正文末尾含 `Would you be open to learning more about the program and the possible cooperation format?`，同样是动作句，且其正文含 `${researchFields|your field}` / `${recentWorkTitle|your recent research}` 变量占位符——收口时**不得**对含 `${...}` 的文本做任何切分或改写，否则占位符可能被截断（K-intro-mail-fallback）。
- Violation consequence: 删改冻结事实的正文（违反 G-1），或截断变量占位符导致渲染出畸形文本。
- 来源: original（需求方 2026-08-28 提供的 id 1 / 21 正文逐字基线）

### Invariant I-6: 全锁定时退化为现状，逃生舱不被破坏
- Rule: 当全部存活条目的 `generationKind` 为非 AI 生成（运营手写/安全模板），或运营在 15 中把全部段落锁定时，收口**跳过**去重与重排，直接走现有 `composeLockedItems`。
- 理由：`composeLockedItems` 的既有语义是「已锁定答案逐字拼接，不 trim、不 dedupe、不 reorder、不调 LLM」（`AiReplyPointByPointComposer.kt:26-33` 的文档注释）。这是运营完全接管时的确定性逃生舱，必须保留。
- Violation consequence: 运营逐字定过稿的内容被系统重排或删句。
- 来源: K-locked-item-assembly-list-not-set

### Invariant I-7: 收口发生在全部校验之后、frame 解析之前
- Rule: 收口步骤插在 `validateGroundedTrustBoundary`（`:1461`）之后、`resolveFrameForAssemble`（`:1471`）之前。`versions`、`groundedSections`、`selection.sendQaRuleIds` 的构造与校验**全部先完成且不受影响**。
- Violation consequence: 审计集合或信任边界校验读到被收口改过的文本，口径漂移。
- 来源: K-audit-selected-source

## 现状审计

### assemble 链路（`TrustReplyWorkbenchService.verifyAssembly`，`:1396-1495`）
1. `:1397-1404` `resolveSource` + `requireCurrentSourceVersion` + `resolveCanonicalSelection`
2. `:1429-1459` 逐条 `materializeVersion`，同时为 `ANSWER_WITH_EVIDENCE` / `ANSWER_SUPPORTED_PART` 收集 `groundedSections`（`canonicalizeClaims(item, locked.claims, locked.answerText)`）
3. `:1461` `validateGroundedTrustBoundary(selection.requestFacts, groundedSections)`
4. `:1462-1464` 注释：跨 item 查重已删除
5. `:1466-1468` `orderedAnswers = versions.mapNotNull { it.answerText.takeIf { handling != OMIT } }`
6. `:1471` `resolveFrameForAssemble(request.frameSnapshot)`
7. `:1472` `composeLockedItems(orderedAnswers, resolvedFrame)`
8. `:1473-1477` `aiReplyDraftPreviewService.preview(...)`
9. `:1479-1495` 装配 `TrustReplyAssembleResponse`：`rawDraftText` / `renderedDraftText` / `draftHash` / `canonicalFactIds = selection.sendQaRuleIds` / `itemVersions = versions`

**Write path（唯一）**：`assemble` HTTP 端点（`TrustReplyWorkbenchController.kt:102-104`）→ `verifyAssembly`。
`verifyAssembly` 另有 internal 调用方（发送服务、诊断），见 `:1390-1395` 的文档注释——**收口改的是 `raw`，因此这些调用方看到的正文也会变**，这是预期行为（发送的就是收口后的正文），但 `PendingMailOperationService.kt:675` 的「发送正文 == assembly 产物」判定必须仍然成立（它比的是同一次 assemble 的产物，不受影响）。

**Read paths**：`rawDraftText` → 前端预览、发送、`draftHash`；`itemVersions` → 16 的归档。

### 数据可用性（本计划无需新增任何字段）
`TrustReplyItemVersion`（`:282-299`）已含 `claims: List<AiReplyItemClaim>`、`handling`、`requestIndex`、`requestKey`、`answerText`。`AiReplyItemClaim` 含 `intentKey` / `text` / `sourceRuleIds`（由 `canonicalizeClaims` 保证与 answerText 一致）。**整封收口所需的全部输入在 `:1466` 处已经就位。**

### Interaction points
- **IP-1**：收口（改 `raw`）× `draftHash`（`:1485`）——哈希对收口后的文本求值，前端 preflight 的 draft identity 随之改变；这是预期，但 `K-ai-preflight-stale-response-draft-identity` 记录过该字段的消费者，实现时须复核。
- **IP-2**：收口（删动作句）× `canonicalFactIds`（`:1488`）——审计集合来自 selection，**不得**因文本删改而变化（I-7）。
- **IP-3**：收口 × 冻结事实正文（I-5）——id 1 / 21 的正文含动作句与变量占位符。
- **IP-4**：收口 × 16 的归档 `itemVersions`——归档的是逐条 version（未收口的 answerText），与最终正文不同。16 若要记 `finalParagraphText`，取的是收口后的段落，两者须分开存放，不得互相覆盖。

### 前端样式盘点
不适用——本计划不触及任何前端文件。

## 实现方案

### T-1：新增收口器 `AiReplyLetterCloser`（新文件，I-1～I-4）
纯函数式服务，输入 `(versions: List<TrustReplyItemVersion>, allowedActions: Set<AiReplyAction>)`，输出 `List<String>`（按主题归并、去重、单 CTA 后的段落列表）。步骤：
1. 展开为 canonical order 的 claim 列表（跳过 `handling == OMIT` 的 version）。
2. 按 I-2 去重（`sourceRuleIds` 集合相等；空集合不参与）。
3. 按 I-3 归并主题，产出段落文本（同主题 claim 的 `text` 用单空格连接）。
4. 按 I-4 收口 CTA，遵守 I-5 的冻结事实豁免。
5. 若触发 I-6 的逃生舱条件，直接返回原 `orderedAnswers`。

放在独立文件而非塞进 `TrustReplyWorkbenchService`（2473 行），便于单测与后续 13 的替换。

### T-2：接入 `verifyAssembly`（I-7）
`:1466-1468` 的 `orderedAnswers` 构造改为调用 `AiReplyLetterCloser`；`:1471-1472` 的 frame 解析与 `composeLockedItems` **保持原样**（收口器的输出仍然是「有序答案列表」，`composeLockedItems` 的契约不变）。

### T-3：CTA 检测复用既有策略（I-4）
使用 `AiReplyActionPolicy.detectActions`（`AiReplyActionPolicy.kt`）——**不得**另写一套动作正则。授权集合取本次 assemble 的可用动作；取不到时按「保留最后一处、不校验授权」降级并记 warning。

### T-4：测试（新增 `AiReplyLetterCloserTest`）
1. 两条 version 的 claim 具有相同 `sourceRuleIds` → 输出中该事实文本只出现一次，且是首次出现的那条（I-2）。
2. `sourceRuleIds` 为空的两条 claim → 都保留（I-2 的例外）。
3. 三条 version 分属两个主题 → 输出两段，主题顺序与首次出现顺序一致，段内顺序为 canonical order（I-3）。
4. 两条 version 各带一个动作句 → 输出只剩最后一处（I-4）。
5. 动作句来自 `Meeting arrangement` 正文（用真实逐字文本作 fixture）且是唯一动作 → 整段保留，不另加 CTA（I-5）。
6. 文本含 `${researchFields|your field}` → 收口不切分、不改写该段（I-5）。
7. 全部 version 的 `generationKind` 非 AI 生成 → 输出等于输入，逐字不变（I-6）。
8. `TrustReplyWorkbenchServiceTest` 新增：收口后 `canonicalFactIds` 与收口前**完全相同**（I-7 / IP-2）。

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterCloser.kt` | 新增（T-1） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 修改（T-2，仅 `:1466-1468` 一段） |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterCloserTest.kt` | 新增（T-4.1～T-4.7） |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | 修改（T-4.8） |

合计 4 个文件，1 个子系统（llm 编排层）。
**`AiReplyDraftService.kt` / `AiReplyGroundedContentPlanner.kt` / `AiReplyGroundedDraftMaterializer.kt` 均不在清单内**——它们服务于 `AiReplyDraftService.generate()` 的单条产出，与一键预判的最终正文无关（见「前提更正」）。

## 验证命令

> 本项目是 Kotlin + Spring Boot 2.7 (Java 11) Maven 工程，**必须用 JDK 11 (zulu-11)**，裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyLetterCloserTest,TrustReplyWorkbenchServiceTest

# 单条用例
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyLetterCloserTest#'same source rule ids collapse to one'

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：`mvn test` 退出码 0，输出 `Tests run: N, Failures: 0, Errors: 0`。
来源：项目根 `CLAUDE.md` 的 `## Commands` 章节（逐字照抄）。

## 验收标准

- **I-1**：`AiReplyLetterCloser` 的入参类型是 `List<TrustReplyItemVersion>`；`grep -c "linkedSetOf<String>" src/main/kotlin/.../AiReplyLetterCloser.kt` == 0（不做字符串级去重）。
- **I-2**：T-4.1、T-4.2 通过。
- **I-3**：T-4.3 通过。
- **I-4**：T-4.4 通过；`grep -c "detectActions" src/main/kotlin/.../AiReplyLetterCloser.kt` ≥ 1 且该文件无自定义动作正则。
- **I-5**：T-4.5、T-4.6 通过；两个 fixture 的文本取自需求方 2026-08-28 提供的 id 1 / id 21 逐字正文，**不得自造替身**（K-named-fixture-must-use-real-row）。
- **I-6**：T-4.7 通过。
- **I-7**：T-4.8 通过；`git diff` 中 `TrustReplyWorkbenchService.kt` 的改动仅限 `:1466-1468` 一段。
- **回归**：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 同一事实只出现一次
- 前置条件：11 已上线；一封同时问「申请流程」与「入选后怎么走」的来信（两问都会命中同一条规则）。
- 操作步骤：工作台点「一键预判」，等待整合完成，通读正文。
- 预期结果：`you submit your materials` / `submitted for review` 这组措辞全文**只出现一次**。改造前基线是 2–3 次。
- 覆盖：observable outcome 1；I-2

### A-2: 段落按主题而非按摘要
- 前置条件：一封含 ≥4 个分属不同主题问题的来信。
- 操作步骤：同 A-1，数段落并判断每段主题。
- 预期结果：段落数少于摘要数；同一主题的内容集中在同一段；段落顺序与来信中各主题首次出现的顺序一致。
- 覆盖：observable outcome 2；I-3

### A-3: 全信一处 CTA
- 前置条件：同 A-2。
- 操作步骤：数正文中「请提供简历 / 约通话 / 请回复」类动作句。
- 预期结果：**恰好一处**。改造前可能出现两处。
- 覆盖：observable outcome 3；I-4

### A-4: 会议安排事实自带的 CTA 不被删改
- 前置条件：构造一封明确要求安排会议的来信，使 `Meeting arrangement`（id 21）被选为事实。
- 操作步骤：点「一键预判」，把正文中该段与下列逐字文本比对：
  `We would like to arrange a brief Zoom meeting to learn more about your professional background and research interests, and to introduce ourselves briefly.` 与
  `The meeting will take approximately 15–20 minutes. Could you please let us know when you would be available? We will arrange the meeting according to your time zone.`
- 预期结果：该事实的正文**一字未删未改**；全信不再另加一处 CTA。
- 覆盖：I-5；G-1

### A-5（回归）: 审计规则集不变
- 前置条件：对同一封来信，记录本计划上线前 assemble 响应中的 `canonicalFactIds`。
- 操作步骤：上线后对同一封信重跑一次 assemble，对比该字段。
- 预期结果：`canonicalFactIds` **完全相同**（正文变了，选中的事实集合不变）。
- 覆盖：What must NOT change 第 3 项；I-7；IP-2

### A-6（回归）: 逐条锁定与版本身份未变
- 前置条件：一封已逐条生成并锁定的来信。
- 操作步骤：观察每条摘要的版本下拉与 `versionId`；重新整合一次。
- 预期结果：各条 `versionId` 与本计划上线前完全相同；整合不报 `TRUST_REPLY_ITEM_VERSION_INVALID`。
- 覆盖：What must NOT change 第 2 项

### A-7（回归）: 全手动逃生舱
- 前置条件：一封所有条目都由运营手写（非 AI 生成）的来信。
- 操作步骤：整合并通读正文。
- 预期结果：正文是各条手写文本按顺序逐字拼接（加 frame），**未被合并、未被删句、未被重排**。
- 覆盖：What must NOT change 第 6 项；I-6

### A-8（回归）: frame 位置与顺序未变
- 前置条件：已配置尊语 / 开场白 / 致谢语 / 结束语。
- 操作步骤：整合后观察正文首尾。
- 预期结果：顺序仍为 尊语 → 开场白 → 致谢语 → 正文段落 → 结束语，各块之间一个空行。
- 覆盖：What must NOT change 第 7 项
