# Fast-P Child Brief — trust-reply-configurable-workbench-02

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/trust-reply-configurable-workbench (branch fast/trust-reply-configurable-workbench)
- Child base SHA: ed944d1 (child 01 implementation, LIGHT_PASS_WITH_NOTES)
- This brief = global constraints + the exact approved child plan (verbatim below).

## Global constraints (from master plan 00)

- Authorized files: EXACTLY the 10 files in the plan's 变更文件清单. No other product/test file may be touched.
- Master must-NOT-change list applies verbatim (request extraction/requestKey/intent/grounding; QA answerBody sole fact body; active vs resolved separation; locked answers canonical-order verbatim once; SIMULATION/LIVE fixed; ADJUST_ITEM-only autofill; server-side final reassembly; reply snippet/autoreply/matched/FREE_FORM/template default frame behavior; permissions, optimistic concurrency, expiry, payload limit, audit, history unchanged).
- Global invariants in force for this child: G-1 (canonical config = requestFactSelections + frameSnapshot; 02 owns frame snapshot), G-3 (server resolves frame text from enabled type-matched snippet IDs; client submits only IDs), G-4 (frame identity separate from evidence identity; frame change only invalidates assembly), G-5 (02 provides revalidation for final reassembly), G-6 (v2->v3 durable upgrade, single new shared field, v1/v2 readable, unknown INVALID).
- No DB table/column changes, no new Flyway migration, no modification of reply_snippet/content_variant write paths (ReplySnippetService.create/update/setEnabled/setDefault/delete unchanged).
- Downstream interfaces (must stay constructible for later children): new domain fields get defaults so existing Kotlin construction points compile; child 01's requestFactSelections contract must remain intact; plan 03 client consumes frameOptions + frameSnapshot and sends v3 state.
- Required commands (run freshly, record exit codes in execution report):
  - JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test
  - node --test src/test/js/*.test.js
  - git diff --check
- Baseline at child base ed944d1: mvn test exit 0 (2092 run, 4 pre-existing skipped), node tests exit 0 (413 pass), git diff --check clean.
- Commit the implementation locally as: feat(fast-p): implement trust-reply-configurable-workbench-02
- Exclude docs/plans/fast/** from the implementation commit (controller commits evidence separately).
- Write the full execution result to docs/plans/fast/trust-reply-configurable-workbench/children/02/execution.md (this file is OUTSIDE the implementation commit too).
- Use skill execute-p against the plan below. Do not review later children, repair unrelated behavior, push, merge, or rewrite history.

---

# 可信回复工作台 02：可选择回复框架开发计划

> 使用 `create-p` 编写。总纲：`trust-reply-configurable-workbench-00-master.md`。前置：`trust-reply-configurable-workbench-01-request-fact-assignment.md` 已执行并通过独立验证。后续：`trust-reply-configurable-workbench-03-two-page-workbench-ui.md`。

## 需求描述

### 可观察结果

可信回复工作台 bootstrap 返回启用的整封邮件框架选项。操作员可分别选择“尊语（SALUTATION）/开场白（GREETING）/致谢语（ACK）/结束语（CLOSING）”，每个位置也可明确选择“不使用”。服务端整合严格按 `SALUTATION → GREETING → ACK → canonical locked answers → CLOSING` 组装。框架选择变化只使 assembly 失效，不清空、重写或重新生成任何摘要回答。

### 必须保持不变

- `reply_snippet` 仍由现有管理接口维护；本计划只读可选片段，不新增或修改片段内容。
- `ReplySnippetService.create/update/setEnabled/setDefault/delete` 的校验、默认规则、variant 写入和 placeholder 校验不改。
- `resolveManualFrame()` 与 `resolveAck()` 的现有语义保留，避免改变 `AiReplyDraftService` matched/FREE_FORM prompt/fallback 和 `AiReplyPointByPointComposer` 其他 Grounded 调用点。
- 只有可信工作台 `assemble` 使用显式选择 overload；其他回复入口继续使用默认 frame。
- 被选中的文本必须由服务端按 snippet ID 重读；客户端不得提交或覆盖片段正文。
- locked answer 的 canonical 顺序、字节内容、claims、versionId、sourceVersion、evidenceSetVersion 不变。
- 框架选择不进入 evidenceSetVersion 和 item versionId；只进入独立 frame snapshot/version 与最终 draftHash。
- 最终训练评估和正式发送会再次调用 `TrustReplyWorkbenchService.assemble`；snippet 已改变/禁用时必须 fail closed，而不是发送旧前端预览。
- `trust_reply_workbench_state` 的乐观锁、过期、大小限制和 locked snapshot 重验不变。

### 范围外

- 不在工作台内新增、编辑、删除 reply snippet；仍使用现有片段管理页。
- 不选择 `content_variant`，只选择 `reply_snippet.content` 主版本；variant 轮换另行计划。
- `CUSTOM` 不进入四个 frame slot。
- 不允许 ACK 成为全局 default，不修改现有 `is_default` 约束。
- 不为不同摘要分别配置 frame；frame 属于整封邮件。
- 不修改自动回复、matched/FREE_FORM、邮件模板的 frame 语义。
- 不新增数据库表或字段，不修改 V47/V64/V67/V83。
- 本计划不实现前端控件；仅提供后端选项、选择、持久化和整合契约。

## 关键不变量

### Invariant I-1：片段 ID 是输入，服务端解析后的 frame 是 authority

- Rule：请求只能携带四个 slot 的 snippet ID 和 expected frame version；正文、display label 或客户端拼接结果不得进入 assemble authority。每个非空 ID 必须存在、enabled、content 非空且类型与 slot 完全一致，否则返回 HTTP 422 `TRUST_REPLY_FRAME_SELECTION_INVALID`。
- Applies to：bootstrap caller selection、state save/restore、assemble、训练评估/正式发送重整合。
- Violation consequence：客户端可把任意文本伪装成已审核片段，或把 closing 用在 salutation 位置。
- 来源：original + `K-manual-frame-three-consumers`

### Invariant I-2：缺失 frame 与明确全空 frame 语义不同

- Rule：整个 `frameSnapshot` 缺失表示旧客户端兼容，服务端解析当前默认 SALUTATION/GREETING/CLOSING，ACK 为空；`frameSnapshot.selection` 存在但四个 ID 都为 null 表示操作员明确“不使用任何框架”，不得回退默认。
- Applies to：所有请求 DTO、v1/v2 state 迁移、bootstrap defaults、assemble。
- Violation consequence：用户明确删除片段后，服务端又静默补回默认文本。
- 来源：original

### Invariant I-3：frame version 必须确定且可重验

- Rule：服务端 frame version 由固定 slot 顺序、每个 ID/NULL、类型、enabled、updatedAt、content SHA-256 决定；不得加入 observed time。assemble/state 提供的 expected version 与当前解析结果不一致时返回 HTTP 409 `TRUST_REPLY_FRAME_STALE`。
- Applies to：`ReplySnippetService` 解析、bootstrap、state restore、assemble、最终发送复验。
- Violation consequence：片段编辑或禁用后仍能发送旧正文，或未变化片段每次刷新都误报 stale。
- 来源：`K-ai-reply-evidence-version-deterministic` 的确定性版本原则扩展

### Invariant I-4：框架变化只失效 assembly，不失效 item versions

- Rule：frame snapshot/version 不参与 evidenceSetVersion、requestKey 或 item versionId；改变 frame 后 lockedItems 必须仍可恢复和重验，只清除 raw/rendered assembly 与 draftHash。重新 assemble 生成新的 draftHash。
- Applies to：state save/restore、assemble 响应、计划 03 前端状态机。
- Violation consequence：换一句问候导致所有摘要答案重生成，或旧 assembly 被错误采用。
- 来源：original + `K-trust-reply-resolved-version-single-source`

### Invariant I-5：工作台 composer 只添加 frame，不改 locked answers

- Rule：新 overload 接收已解析 frame；只在 canonical answer list 前后插入非空 frame block，并以单个空行分隔。每个非 OMIT answer 必须按原顺序逐字出现且恰好一次；不得 trim、distinct、重排、翻译或再次调用 LLM。
- Applies to：`AiReplyPointByPointComposer.composeLockedItems(orderedAnswers, resolvedFrame)`。
- Violation consequence：选择框架时破坏已锁定版本身份或丢失重复但合法的回答。
- 来源：`K-locked-item-assembly-list-not-set`

### Invariant I-6：只改变可信工作台 frame 消费点

- Rule：`AiReplyDraftService` 对 `resolveManualFrame/resolveAck` 的 matched/FREE_FORM prompt/fallback 读取不改；`AiReplyPointByPointComposer` 的 `composeFromPlan/composeFromSections/composeFromAnswers` 默认 frame 行为不改。仅 `TrustReplyWorkbenchService.assemble` 调显式 overload。
- Applies to：frame 全部生产消费者。
- Violation consequence：工作台的产品选择意外改变自动回复或普通 AI 草稿语气。
- 来源：`K-manual-frame-three-consumers`

### Invariant I-7：durable state 只新增一个 frame snapshot 字段

- Rule：payload 升级为 `trust-reply-workbench-state-v3`，在计划 01 的 v2 上只新增 `frameSnapshot` 一个共享 store 字段；snapshot 存 IDs + deterministic version，不存重复正文。v1/v2 缺失字段按 I-2 的兼容默认解析。
- Applies to：state codec、save/restore、schema version 校验。
- Violation consequence：状态形成客户端正文副本或升级后旧锁定回答无法恢复。
- 来源：`K-workbench-lock-replay-needs-dedicated-state-store`

## 现状审计

### `reply_snippet` 与 `content_variant`

- `reply_snippet` schema：`id`、`snippet_type`、`content`、`display_order`、`is_default`、`enabled`、`variant_group`、timestamps；V47 创建并 seed SALUTATION/GREETING/CLOSING 默认与多个 ACK，V64 增加 variant_group。
- `content_variant` 由 V67 创建；owner type 可为 REPLY_SNIPPET。本计划明确不读 variant 内容作为选项。
- Runtime write paths：
  1. `ReplySnippetService.create`：类型、正文、顺序、default、placeholder、variants 校验并 save；
  2. `update`：更新主内容/顺序/variant group/default/enabled 并 replace variants；
  3. `setEnabled`、`setDefault`、`delete`；
  4. `ContentVariantService.replaceForOwner/deleteForOwner` 为 create/update/delete 的联动写入。
- Runtime read paths：
  1. `ReplySnippetService.listAll/listByType/resolveManualFrame/resolveAck`；
  2. `AiReplyPointByPointComposer` 的 locked/Grounded/Natural frame；
  3. `AiReplyDraftService` 的 matched/FREE_FORM prompt 与 deterministic fallback；
  4. `MailComposeTemplateService` 的回复片段 block；
  5. 片段管理 controller/UI。
- 本计划新增窄 read API，不修改任何 write path 或其他 reader 的默认行为。

### 当前 frame 组装

- `ReplySnippetService.resolveManualFrame()` 只解析默认 SALUTATION/GREETING/CLOSING，并返回所有 enabled ACK options；`resolveAck(id)` 可按 ID 读 ACK，但工作台当前未调用。
- `AiReplyPointByPointComposer.composeLockedItems(orderedAnswers)` 自动读取默认 frame，仅加入 salutation、greeting、answers、closing；ACK 未进入 locked assembly。
- `TrustReplyWorkbenchService.assemble` 校验所有 locked items 后直接调用上述默认 composer；request/response/state 均没有 frame identity。
- 因此片段在生成后、正式发送前被编辑时，最终重整合会静默使用新 default，客户端无法确认自己选择的内容。

### `trust_reply_workbench_state` 交互

- Physical schema：一 source 一行，`state_version`、`payload_json LONGTEXT`、`expires_at`、timestamps，unique `(source_type,source_id)`；不改 SQL。
- 全部 write paths：`TrustReplyWorkbenchService.saveState` 调 `encodePayload` 后，`TrustReplyWorkbenchStateStore.save` 走 expected=0 INSERT 或带 expected version UPDATE；lockedItems 为空走 `delete`；save/delete 后及 load expiry 走 `pruneExpired`。
- 全部 read paths：bootstrap 调 `load/decodePayload`，业务层重验 source、evidence、fact mapping、lockedItems；assemble 不直接读 store，只重验请求 snapshot。
- 计划 01 后 v2 payload 保存 canonical fact matrix 与 lockedItems。
- 普通 frame 变更不应改变 source/evidence；但若没有独立 version，已保存的选择无法检测 snippet 改动。
- 正常流程在所有摘要已锁定后进入 frame 页，因此 frame 选择可与 lockedItems 一起持久化；无 lockedItems 的纯 UI frame 草稿不进入 durable store，保持“只持久化可恢复决策”的边界。

## 实现方案

### Task 1：新增 selectable frame resolver（I-1～I-3、I-6）

文件：`src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt`

1. 新增只读 DTO：
   - `ReplyFrameSelection(salutationSnippetId, greetingSnippetId, ackSnippetId, closingSnippetId)`；
   - `ResolvedReplyFrame(selection, version, salutation, greeting, acknowledgement, closing)`；
   - `ReplyFrameOption(id, snippetType, content, displayOrder, isDefault)`。
2. 新增 `listSelectableFrameOptions()`：只返回 enabled、content 非空、type 属于四个 slot 的主 snippet；按 slot 固定顺序、displayOrder、id 排序；排除 CUSTOM 和 variants。
3. 新增 `resolveDefaultSelectableFrame()`：SALUTATION/GREETING/CLOSING 取现有默认，ACK=null；不存在 enabled default 时该 slot=null。
4. 新增 `resolveSelectableFrame(selection)`：逐 ID fresh `findById`，严格校验 expected type/enabled/content；null 明确省略。不得复用 `resolveAck` 的“非法即 null”宽松语义，因为显式选择必须 fail closed。
5. 以固定 slot 序列计算 SHA-256 version；包含 ID/NULL、type、enabled、updatedAt、content hash。解析结果保留正文只用于本次响应/组装，不写入 durable payload。
6. 保留现有 `resolveManualFrame/resolveAck` 方法签名与实现语义，所有旧 reader 无需改动。

### Task 2：扩展工作台 frame domain/HTTP 契约（I-1～I-4）

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt`

1. 定义工作台 transport：
   - `TrustReplyFrameSelection` 四个 nullable IDs；
   - `TrustReplyFrameSnapshot(selection, version)`；
   - `TrustReplyFrameOption` 响应列表。
2. bootstrap request 可选带 caller frame snapshot；response 返回 `frameOptions` 与服务端 canonical `frameSnapshot`。
3. state save/response、assemble request/response 增加 nullable `frameSnapshot`；domain 新字段有默认值，保持旧 Kotlin 构造点编译。
4. 统一 `resolveFrameSnapshot`：
   - 整体缺失→当前默认；
   - 整体存在→按 selection 严格解析；
   - 若携带 expected version，必须与 fresh 解析一致；
   - invalid→422，stale→409。
5. bootstrap 优先级：caller 显式 selection > 可恢复 saved selection > 当前默认。saved frame stale 时使用当前默认作为 top-level canonical frame，但仍返回可恢复 locked items 和专门状态。

### Task 3：让 locked composer 接受显式 frame（I-4～I-6）

文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt`

1. 保留 `composeLockedItems(orderedAnswers)`，其行为和调用者不变。
2. 新增 overload `composeLockedItems(orderedAnswers, resolvedFrame)`；组装顺序固定为：salutation、greeting、acknowledgement、orderedAnswers、closing。
3. frame block 可过滤 blank；locked answer 继续要求非空且不 trim。OMIT 仍在 service 层排除，不传空 answer。
4. `TrustReplyWorkbenchService.assemble` 在 locked item、claims、版本全部验证通过后解析 frame，再调用新 overload。
5. assemble response 回传 canonical frame snapshot；draftHash 继续基于服务端 raw；preview 继续通过 `AiReplyDraftPreviewService` 渲染变量。

### Task 4：升级 durable state 为 v3 并隔离 frame stale（I-3、I-4、I-7）

文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStore.kt`

1. `SCHEMA_VERSION` 升为 `trust-reply-workbench-state-v3`；payload 在 v2 基础上只新增 `frameSnapshot`。
2. encode 只写 v3；decode 支持 v1/v2/v3：v1/v2 缺失 frame 归一化为当前 default 兼容语义，未知 schema INVALID。
3. state save 在 lockedItems 非空时保存 canonical frame IDs/version；不保存 resolved text。lockedItems 为空仍沿用 delete，不扩大 store 为 UI 草稿库。
4. restore 分开判定：
   - source/evidence/fact mapping stale：返回 `STALE`，不恢复 lockedItems；
   - frame ID 失效或 version stale：返回 `FRAME_STALE`，恢复已通过重验的 lockedItems，top-level frame 使用当前默认并要求重新选择/整合；
   - 全部有效：`RESTORED`。
5. frame-only stale 不改变 evidenceSetVersion 和 locked versionId；下一次有效 state save 用 optimistic stateVersion 覆盖旧 frame snapshot。

### Task 5：测试 frame 读写、组装和隔离（I-1～I-7）

文件：

- `src/test/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt`

覆盖：

1. options 只含 enabled 四类主 snippet，顺序确定，CUSTOM/disabled/blank/variant 不进入。
2. 整体 frame 缺失走 defaults；全 null selection 生成空 frame；非法 ID、禁用、类型错配返回 INVALID。
3. 同输入 version 稳定；正文、updatedAt、enabled、ID 或 slot 变化使 version 变化；不受时钟影响。
4. composer 顺序包含 ACK；每个 locked answer 原字节、原顺序、恰好一次；旧 overload 的默认行为回归。
5. frame 变化后 assemble raw/rendered/hash 变化，但 requestKey/evidence/versionId/locked answer 不变。
6. v1/v2 state 按 default 兼容；v3 恢复选择；frame stale 恢复 locks 但不恢复 assembly；source/evidence stale 仍不恢复 locks。
7. 正式 reassemble 使用 fresh snippet，expected frame version stale 时 composer/preview 不调用。

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt` | 修改 | 可选 frame options、严格解析、deterministic version |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt` | 修改 | 显式 frame locked composer overload |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 修改 | frame DTO、解析、state/assemble 集成 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStore.kt` | 修改 | v3 payload 与 v1/v2/v3 decode |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt` | 修改 | frame HTTP DTO 与错误边界 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetServiceTest.kt` | 修改 | options、严格类型、版本测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt` | 修改 | frame 顺序与 locked bytes 保真 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | 修改 | bootstrap/state/frame stale 测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | 修改 | assemble/reassemble 最终链路测试 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt` | 修改 | frame HTTP 转换和错误码测试 |

范围：10 个文件；reply snippet 读取 + 可信回复后端两个子系统；state JSON 每计划只新增一个字段；无数据库 migration、无前端。

## 验收标准

- I-1：请求只传 IDs/version；服务端 fresh resolve；客户端传正文无法影响 raw draft。
- I-2：缺失 frame 使用 defaults；显式四 null 的 raw 只含 locked answers，不出现默认片段。
- I-3：任一 selected snippet 编辑、禁用或类型变化后旧 expected version 返回 409 `TRUST_REPLY_FRAME_STALE`；未变化重复 bootstrap version 相同。
- I-4：切换 frame 后所有 locked item 的 versionId、answerText、claims、evidenceSetVersion 不变；旧 assembly 不可采用；新 assembly draftHash 改变。
- I-5：raw 顺序严格为 SALUTATION/GREETING/ACK/answers/CLOSING；合法重复 answer 不被去重。
- I-6：`AiReplyDraftService` 和 composer 其他方法的 default frame 测试无变化；邮件模板与自动回复输出不变。
- I-7：v1/v2/v3 durable state 均有覆盖；frame stale 可恢复 locks，source/evidence stale 不恢复。
- 回归：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test`、`node --test src/test/js/*.test.js`、`git diff --check` 通过。

## 人工验收清单

### A-1：四类选项与“不使用”

- 前置条件：片段管理中每类至少一条 enabled；另有 disabled 和 CUSTOM fixture。
- 操作步骤：调用 bootstrap 查看 frameOptions，并分别提交四个 slot 与全 null selection。
- 预期结果：只返回 enabled 四类主片段；CUSTOM/disabled 不出现；全 null assembly 不回退默认。
- 覆盖：I-1、I-2。

### A-2：组装顺序与 ACK

- 前置条件：两个摘要均已锁定，四个 frame slot 均选择。
- 操作步骤：调用 assemble，检查 rawDraftText。
- 预期结果：尊语、开场白、致谢语、摘要 1、摘要 2、结束语按固定顺序出现，块间一个空行；两个摘要正文逐字不变。
- 覆盖：I-5。

### A-3：切换框架不清空摘要回答

- 前置条件：已有两个 locked versions 和 assembly。
- 操作步骤：只把 greeting 换成另一 enabled snippet，再保存状态和 assemble。
- 预期结果：locked versionId/answerText 不变；旧 assembly 被视为失效；新 raw/hash 只在 frame 部分变化。
- 覆盖：I-3、I-4。

### A-4：片段编辑后的最终发送复验

- 前置条件：用 snippet A 完成 assembly，但尚未训练评估/正式发送。
- 操作步骤：在片段管理页编辑或禁用 A，再用旧 frame snapshot 走最终重整合。
- 预期结果：返回 `TRUST_REPLY_FRAME_STALE`；不调用 composer，不保存评估、不发送邮件。
- 覆盖：I-1、I-3、必须保持不变第 8 项。

### A-5：刷新恢复与 frame stale 隔离

- 前置条件：v3 state 含有效 locks 和 frame A。
- 操作步骤：先直接刷新；再修改 A 后刷新。
- 预期结果：首次恢复 locks 和 A；修改后状态提示 FRAME_STALE，locks 仍恢复，frame 回到当前默认/待重新选择，assembly 不恢复。
- 覆盖：I-4、I-7。

### A-6：其他回复路径回归

- 前置条件：准备 FREE_FORM、matched、Grounded 默认 composer 和邮件模板基线。
- 操作步骤：不走可信工作台显式 frame，执行各路径。
- 预期结果：继续使用现有 defaults/ACK 语义，输出与变更前一致。
- 覆盖：I-6。

### A-7：reply snippet 管理写路径回归

- 前置条件：片段管理页可创建测试 SALUTATION，已存在同类 default；为测试片段配置一个 content variant。
- 操作步骤：创建片段、编辑正文/顺序/variant、设为 default、disable/enable，最后删除；另尝试把 ACK 设为 default、提交非法 placeholder。
- 预期结果：CRUD/variant 与变更前一致；同类只有一个 default；ACK default 和非法 placeholder 仍被拒绝；工作台 options 只随 enabled 主片段变化，不写入或改写任何 snippet/variant。
- 覆盖：必须保持不变第 1、2 项，I-6。
