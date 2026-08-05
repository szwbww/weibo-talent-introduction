# 可信回复工作台可配置化总计划

> 使用 `create-p` 编写。本文件是执行总纲与跨计划契约，不直接授权修改产品代码。具体实施严格按 01→02→03 顺序进入对应子计划；每个子计划均满足 ≤10 文件、≤2 子系统、每共享 store 每计划 ≤1 新字段。

## 需求描述

### 总体可观察结果

可信回复工作台改为两个横向切换页面：第 1 页“摘要与事实”允许逐摘要增删事实并展示绑定关系，同一事实在一封回复内只能属于一个摘要；第 2 页“回复框架与整合”允许选择整封邮件的尊语、开场白、致谢语、结束语，并以服务端整合结果完成训练评估或采用到人工回复。

事实与框架选择从 bootstrap、逐项生成、durable state、assemble 一直传递到训练评估和正式发送的最终服务端重整合。最终正文不得回退为 flat facts 或默认 frame。

### 必须保持不变

- 原邮件 request 的提取顺序、requestKey、intent 与 grounding status 语义不改。
- QA `answerBody` 是唯一事实正文；displayName/前端预览不成为事实 authority。
- active version 与 resolved version 分离；只有 resolved version 可保存、预览、整合或采用。
- locked answer 按 canonical request 顺序逐字、恰好一次进入 assembly；composer 不重写、不去重、不调用 LLM。
- 训练入口固定 SIMULATION，正式入口固定 LIVE；共享组件内不能切换 mode/source。
- 自动补齐只走逐项 ADJUST_ITEM 与 frozen grounded allowlist；不恢复 FULL_DRAFT 扁平生成。
- 最终训练评估和正式发送继续服务端重整合；本地配置预览不可直接采用或发送。
- 回复片段管理、自动回复、matched/FREE_FORM、邮件模板的默认 frame 行为不改。
- 权限、乐观并发、过期、payload 上限、审计与历史已发送邮件不改写。

### 明确不做

- 不做左右分栏、拖拽、事实搜索、事实排序或跨邮件事实占用。
- 不在工作台编辑 QA 事实或 reply snippet 内容。
- 不选择 reply snippet content variants，不支持 CUSTOM frame slot。
- 不做逐摘要局部保留旧 version；事实矩阵变化整体刷新 evidence identity。
- 不新增数据库表/列，不修改已应用 migration。
- 不删除 legacy `requestedFactIds`；只在新客户端停止发送。
- 不生成 acceptance 衍生文件；人工验收开始时再从各子计划导出。

## 关键不变量

### Global G-1：完整配置由事实矩阵与 frame snapshot 共同构成

- Rule：同一工作台实例的 canonical config = `requestFactSelections + frameSnapshot`。任何生成、state、assemble、评估或发送复验不得只携带其中一部分。
- Child ownership：01 定义事实矩阵；02 定义 frame snapshot；03 负责全链路透传。
- Violation consequence：工作台展示与最终发送使用不同配置。

### Global G-2：事实唯一性是服务端边界

- Rule：每个事实 ID 在完整 `requestFactSelections` 中最多出现一次；前端 disabled 只改善体验，bootstrap/state/generation/assemble 均由服务端重新校验。
- Child ownership：01 后端 authority；03 前端 owner 可见性。
- Violation consequence：同一事实跨摘要重复，或 DOM 篡改绕过限制。
- 来源：`K-request-fact-assignment-version-must-include-mapping`

### Global G-3：客户端只提交 ID，服务端解析正文

- Rule：事实正文由 enabled QA `answerBody` 解析；frame 正文由 enabled、类型匹配的 reply snippet ID 解析。客户端不能提交 authoritative fact/frame text。
- Child ownership：01 facts；02 frame；03 只展示服务端返回的文本。
- Violation consequence：浏览器注入未经审核正文。
- 来源：`K-answerbody-source-exclusive`、`K-selectable-reply-frame-server-resolved-snapshot`

### Global G-4：evidence identity 与 frame identity 分离

- Rule：事实矩阵进入 deterministic evidenceSetVersion 和 item versionId；frame selection 进入独立 deterministic frame version 和 assembly draftHash。frame 改变只失效 assembly，事实改变使全部旧 item versions stale。
- Child ownership：01 mapping-sensitive evidence；02 frame version；03 差异化 invalidation。
- Violation consequence：换事实仍接受旧回答，或换问候清空全部摘要答案。

### Global G-5：最终重整合携带同一 canonical config

- Rule：SIMULATION evaluation、LIVE adopt snapshot、未编辑人工发送必须携带同一 matrix/frame/lockedItems，服务端 fresh resolve 后再 assemble。不能从 claims/canonicalFactIds 反推，也不能丢 frame 后回退 default。
- Child ownership：02 提供 revalidation；03 完整透传。
- Violation consequence：预览通过但评估/正式邮件正文变化。
- 来源：`K-workbench-config-propagates-to-final-reassembly`

### Global G-6：状态升级顺序固定且向后兼容

- Rule：01 把 durable payload v1→v2，仅新增 `requestFactSelections`；02 把 v2→v3，仅新增 `frameSnapshot`；03 只消费 v3，不再改变 store schema。v1/v2 必须可兼容读取，未知 schema INVALID。
- Child ownership：01、02；03 更新客户端 schema 字面量。
- Violation consequence：单计划新增多个共享字段，或后端先部署导致旧前端不可用。

### Global G-7：双页只是视图切换，不复制状态机

- Rule：两个页面共享同一组件 state/transport；切页不 bootstrap、不复制 requests/versions/locks。instruction input 不逐键重建 DOM；tabs 具备完整 ARIA/键盘行为。
- Child ownership：03。
- Violation consequence：两页配置漂移、IME 丢焦或多实例串状态。

## 样式契约

本总计划不直接拥有 DOM/CSS 变更，禁止绕过子计划 03 单独实施样式。唯一权威样式契约是：

- [03 双页前端计划的 S-1～S-6](./trust-reply-configurable-workbench-03-two-page-workbench-ui.md#样式契约)。
- 新 class 的完整逐字 CSS、retire selectors、token、640px 和 reduced-motion 规则均在 03；执行时不得在主计划中另行推断。
- 全局 `.tabs/.tab`、`.mailbox-segmented-control`、`.button`、`.compose-panel` 必须无 diff；新规则全部 scoped 到 `.trust-reply-workbench`。

## 现状审计

### 当前事实配置断点

- 前端已有 `requestCoverage[].factRuleIds`，但 state authority 是全局 `selectedFactIds`；所有请求只发送 `requestedFactIds`。
- `QaFactSelectionService.select` 让每个 request 从同一个 pool 选事实，同一 rule 可进入多个 request。
- evidenceSetVersion 只表达事实并集；相同并集换绑摘要时 identity 不变。
- 交互点：selection → evidence version → locked versionId → durable restore → assemble → evaluation/send reassemble。01 必须一次收口全部后端读写路径。

### 当前 frame 配置断点

- `reply_snippet` 已有 SALUTATION/GREETING/ACK/CLOSING 数据和管理写路径，但工作台无选择入口。
- locked composer 自动读取默认 salutation/greeting/closing，ACK 未进入工作台 assembly。
- assembly/state/final reassemble 没有 frame identity；片段编辑或禁用无法精确判 stale。
- 交互点：reply snippet 管理写入 → bootstrap options → state snapshot → assembly → evaluation/send fresh resolve。02 定义后端契约，03 负责透传。

### 当前 UI 与最终复验断点

- 工作台当前是 toolbar global facts + summary 横条 + item list 单页布局。
- 训练评估与 LIVE assembly snapshot 只复制 requestedFactIds/lockedItems；`AiTrainingController` 也只构造 flat assembly。
- 本地组件、训练 adapter、LIVE adapter、人工发送必须同步升级，否则形成双事实源。
- exact DOM、CSS selectors/tokens、state helpers、测试基线已在 03 `## 现状审计` 和 `## 样式契约` 逐项记录。

### 共享存储审计归属

- `qa_rule` 全部 migration/runtime 写路径与所有生产读者：01 审计；本轮只读。
- `trust_reply_workbench_state` 的 insert/update/delete/prune/load/decode/restore：01、02 分别按 v2/v3 审计；无物理 schema 变化。
- `reply_snippet/content_variant` 的 migration、CRUD/default/enable/variant 写路径与全部 frame/template reader：02 审计；新工作台只读主 snippet。
- 前端 state、bootstrap/generation/state/assemble transport、evaluation/LIVE/send snapshot：03 审计。

## 实现方案

### Stage 1：建立摘要—事实唯一矩阵（G-1～G-4、G-6）

执行：[01 摘要—事实唯一分配](./trust-reply-configurable-workbench-01-request-fact-assignment.md)

- 新增 `requestFactSelections` domain/HTTP/state v2 契约。
- 工作台专用选择器保证同一事实只消费一次、显式事实匹配指定摘要。
- mapping 加入 deterministic evidence identity。
- 保留 legacy flat 兼容，矩阵与 flat 同时出现 fail closed。
- 发布门：01 的全部自动测试和 A-1～A-7 通过；fix-v PASS 后才进入 Stage 2。

### Stage 2：建立服务端可选 frame snapshot（G-1、G-3～G-6）

执行：[02 可选择回复框架](./trust-reply-configurable-workbench-02-selectable-reply-frame.md)

- enabled 主 snippet options、严格 slot/type 解析、deterministic frame version。
- locked composer 新 overload 按 frame + canonical answers 组装。
- durable state v2→v3；frame stale 保留 locks、阻止旧 assembly。
- default frame 的其他生产消费者保持不变。
- 发布门：02 的全部自动测试和 A-1～A-7 通过；fix-v PASS 后才进入 Stage 3。

### Stage 3：落实双页 UI 与最终透传（G-1～G-7、S-1～S-6）

执行：[03 双页切换前端与全链路透传](./trust-reply-configurable-workbench-03-two-page-workbench-ui.md)

- “摘要与事实”显示 chips/picker/owner；“回复框架与整合”选择四类 slot。
- facts 变化全量刷新 evidence/versions；frame 变化只失效 assembly。
- 本地配置预览与服务端 CURRENT assembly 明确分离。
- 训练评估、LIVE adopt、未编辑正式发送完整透传 matrix/frame/locks。
- 发布门：03 的自动测试、S-1～S-6 及 A-1～A-11 全部通过。

### Stage 4：全系统复验与人工验收（G-1～G-7）

1. 依次对 01、02、03 调用独立 `fix-v`；后序不能替代前序验证。
2. 三份均 PASS 后，执行本主计划的端到端 A-M 清单。
3. 人工验收开始时，分别从三份子计划导出 acceptance 衍生文件；主计划只记录总体验收结果，不复制子计划清单。
4. 任一失败回到所属子计划修订/repair；禁止在主计划追加未审计实施文件。

## 变更文件清单

本文件只编排以下三份实施计划；产品代码文件以各子计划清单为唯一 authority：

| 顺序 | 子计划 | 文件数 | 子系统 | shared store 新字段 | 依赖 |
|---|---|---:|---:|---|---|
| 01 | `trust-reply-configurable-workbench-01-request-fact-assignment.md` | 9 | 2 | state v2：`requestFactSelections` | 无 |
| 02 | `trust-reply-configurable-workbench-02-selectable-reply-frame.md` | 10 | 2 | state v3：`frameSnapshot` | 01 PASS |
| 03 | `trust-reply-configurable-workbench-03-two-page-workbench-ui.md` | 10 | 2 | 无 | 01、02 PASS |

主计划自身新增文件：`docs/plans/2026-08-05/trust-reply-configurable-workbench-00-master.md`。不直接增加产品实施文件。

## 验收标准

- G-1：bootstrap/ADJUST_ITEM/state/assemble/evaluation/send 捕获的 matrix/frame 完全一致。
- G-2：同一事实跨摘要重复在所有服务端入口返回 422；前端其他摘要显示 owner 并禁用。
- G-3：篡改客户端 fact/frame 文本不影响服务端 raw；ID disabled/type/match 无效时 fail closed。
- G-4：换绑 fact 改变 evidence/versionId；切 frame 不改变 locked versionId，但改变 frame version/draftHash。
- G-5：训练评估与未编辑正式发送均 fresh reassemble；stale fact/frame 不保存评估、不发送。
- G-6：v1→v2→v3 兼容 fixtures 通过；后端 01/02 可先于新前端部署；未知 schema INVALID。
- G-7：双页共享 state，切页无 bootstrap；instruction 输入节点稳定；双 mount 不串状态。
- S-1～S-6：以 03 的逐字 CSS/DOM contract 为 authority，自动与人工验收全部通过。
- 三份子计划各自 `fix-v` PASS；全量 `mvn test`、`node --test src/test/js/*.test.js`、`git diff --check` 通过。

## 人工验收清单

### A-M1：三问四事实唯一绑定

- 前置条件：训练邮件包含 IP/保密、正式合同、参与费用三个摘要；存在四条对应 enabled 原子事实。
- 操作步骤：在第 1 页给摘要 1 分配 IP+保密、摘要 2 分配合同、摘要 3 分配费用；逐个打开其他 picker。
- 预期结果：每卡显示对应 chips；四个 ID 全局各出现一次；已占用事实在其他摘要显示“已用于摘要 N”并禁用。
- 覆盖：G-1、G-2。

### A-M2：事实变更使旧回答整体 stale

- 前置条件：三项均已生成/锁定并完成 assembly。
- 操作步骤：返回第 1 页删除一条事实，先取消确认，再确认并重新分配。
- 预期结果：取消时无变化；确认后全部旧 versions/locks/assembly 清空，新 evidenceSetVersion 不同，旧 snapshot 返回 `TRUST_REPLY_EVIDENCE_STALE`。
- 覆盖：G-2、G-4、G-6。

### A-M3：frame 切换只影响整合

- 前置条件：所有摘要已锁定；第 2 页当前使用默认 frame。
- 操作步骤：选择非默认 greeting、ACK，并把 salutation 设为“不使用”；重新 assemble。
- 预期结果：locked versionId/answerText/claims/evidenceSetVersion 不变；raw 顺序为 greeting→ACK→三个 answers→closing；frame version 和 draftHash 变化。
- 覆盖：G-3、G-4。

### A-M4：本地预览不可直接采用

- 前置条件：已有 CURRENT assembly。
- 操作步骤：修改任一 frame select，但不点击服务端整合。
- 预期结果：状态显示“配置已变化 · 请重新整合”，完成/采用按钮禁用；编辑器和 evaluation 均未收到本地预览。
- 覆盖：G-3、G-5、G-7、S-4。

### A-M5：训练评估与正式发送复验一致

- 前置条件：分别在 SIMULATION/LIVE 使用相同非默认 frame 和事实矩阵完成 assembly。
- 操作步骤：SIMULATION 保存评估；LIVE 采用后不编辑直接发送；检查服务端捕获 assembly request 和最终正文。
- 预期结果：两条请求携带相同 canonical matrix/frame/locks；两次服务端 raw 与各自 CURRENT assembly 逐字一致；无 flat/default 回退。
- 覆盖：G-1、G-5。

### A-M6：事实或片段 stale 阻止最终副作用

- 前置条件：已完成 assembly 但未评估/发送。
- 操作步骤：后台禁用一条 selected fact，再尝试评估；恢复后禁用 selected frame snippet，再尝试发送。
- 预期结果：事实返回 evidence stale，片段返回 frame stale；评估记录数和 outbound mail 记录数均不增加。
- 覆盖：G-2～G-5。

### A-M7：兼容、双页、窄屏与旧路径回归

- 前置条件：v1/v2/v3 state fixtures、390px 浏览器、FREE_FORM/matched/自动回复/邮件模板基线。
- 操作步骤：分别恢复三种 state；用键盘切双页并连续中文输入；检查窄屏；执行四条旧 frame 消费路径。
- 预期结果：v1/v2 归一化、v3 精确恢复；tab/IME/双页状态稳定且无横向滚动；旧路径继续使用原 default frame，正文与基线一致。
- 覆盖：G-6、G-7、S-1～S-6、全部 must-NOT-change 回归。
