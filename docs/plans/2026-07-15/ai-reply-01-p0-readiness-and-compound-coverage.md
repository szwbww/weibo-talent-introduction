# P0-1：草稿就绪状态与复合问题临时覆盖判定

## 需求描述

在不等待 QA 表结构升级的前提下，先阻止当前 4/5/6 等复合问题被误判为完整，并由后端返回统一的 `draftReadiness`。本计划只修控制状态，不伪造缺失答案，不改变现有正文组装协议。

Out of scope：QA coverage key 表结构、intent 级模型 JSON、公司规则内容、前端确认弹窗、审计指标。

## 关键不变量

### I-1：后端 readiness 单源
- `READY`：requestFacts 为空，或所有 request 都 GROUNDED。
- `NEEDS_REVIEW`：无 UNSUPPORTED，但至少一个 PARTIAL。
- `BLOCKED`：至少一个 UNSUPPORTED。
- 两个 controller 只映射 service 结果，不自行推导。

### I-2：复合覆盖逐 facet 检查
- request 命中多个已知 facet 时，每个 facet 都必须在该 request 自己的 `factRuleIds` 正文中有证据。
- 不得从其他 request 的规则或 prompt fallback 全集借证据。

### I-3：临时启发式不发明事实
- 只把状态从 GROUNDED 降为 PARTIAL，不把 PARTIAL/UNSUPPORTED 升为 GROUNDED。
- 不修改 `factRuleIds`、`sendQaRuleIds` 或 draftText。

### I-4：兼容旧统计
- `groundedRequestCount` 暂时继续统计 GROUNDED + PARTIAL，避免现有 UI/API 破坏；readiness 才表示是否可直接发送。

### I-5：P1 可删除
- 临时 facet 表必须集中在 `AiReplyDraftService` companion/internal helper，计划 6 建立 intent catalog 后完整删除，禁止形成第二事实源。

## 现状审计

- 写路径：无数据库写入；`AiReplyDraftService.generate()` 返回内存 DTO。
- 读路径：`AiTrainingController.simulate()` 与 `UnmatchedInboundMailController.aiReplyTurn()` 映射同一结果。
- 当前误判点：`isPartialCoverage()` 只检查 `deliverables/full name/financial arrangements` 等短语是否原样出现在任一 rule text；“selected”与“selection process”等同义表达无法稳定判定。
- 当前正文风险：UNSUPPORTED 被 composer 跳过，可能形成编号缺口；本计划通过 BLOCKED 显式暴露，计划 7/9再解决最终版式与发送确认。

## 临时 facet 规则

| request facet | 请求触发词示例 | 证据至少包含 |
|---|---|---|
| researcher selection | selected, selection, criteria, eligibility | select/criteria/eligible/evaluation/shortlist 中至少一类真实流程事实 |
| enterprise matching | matched, matching, partner enterprise | match + research direction/enterprise 事实 |
| responsibilities | responsibilities, duties | guide/advisor/R&D/responsibility/duty |
| deliverables | deliverables, outputs, milestones, reports | deliverable/output/milestone/report/result；仅“use expertise”不算 |
| contract | contractual, contract terms | contract/agreement/party |
| finance | financial, compensation, salary, funding | funding/salary/RMB/housing allowance/enterprise compensation；仅“terms set later”不算完整财务安排 |
| IP | intellectual property, IP rights | IP/intellectual property/ownership/rights |
| enterprise project types | enterprise projects, types of projects | industry/sector/project type/product/R&D domain；仅 matching process 不算 |

规范化仅 lower-case、空白折叠和常见连字符统一；不得使用模糊语义模型。

## 实现任务

### T1：新增 readiness 枚举和结果字段
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

- 新增 `AiReplyDraftReadiness { READY, NEEDS_REVIEW, BLOCKED }`。
- `AiReplyDraftResult.draftReadiness` 默认 READY；在 `enforceActionPolicy()` 最终返回时由 `resolved.requestFacts` 统一计算。
- 增加 internal `resolveDraftReadiness()` 单元测试 seam。

### T2：把短语检查升级为 facet group 检查
文件：同上。

- 用集中表替换 `PARTIAL_DETAIL_PHRASES`。
- 先识别 request 请求了哪些 facet，再在该 request 的所有 rule subject/body 合集中验证每个 facet。
- 任一请求 facet 缺证据即 `isPartialCoverage=true`。
- 保留公司 `full name + registered location` 的现有判定；计划 2 再治理正文相关性。

### T3：两个 API 暴露 readiness
文件：
- `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`

- `AiTrainingSimulateResponse`、`AiReplyTurnResponse` 添加 `draftReadiness`。
- 直接使用 `result.draftReadiness.name`；不得根据计数重新计算。

### T4：服务测试
文件：`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`

- selection 有 matching、无 selection → PARTIAL。
- responsibilities 有 advisor、无 deliverables → PARTIAL。
- contract/IP 有据、仅提 compensation terms、无财务事实 → PARTIAL。
- matching 有据、enterprise project types 无据 → PARTIAL。
- 三项全部有对应证据 → GROUNDED。
- 任一 UNSUPPORTED → BLOCKED；仅 PARTIAL → NEEDS_REVIEW；全 GROUNDED → READY。
- 明确断言状态变化不扩大 sendQaRuleIds。

### T5：controller 契约测试
文件：
- `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`

- 两入口对相同 service result 返回相同 readiness。
- 保持 requestCoverage、model、generationState 现有字段不变。

## 变更文件清单（6）

1. `AiReplyDraftService.kt`
2. `AiTrainingController.kt`
3. `UnmatchedInboundMailController.kt`
4. `AiReplyDraftServiceTest.kt`
5. `AiTrainingSimulateTest.kt`
6. `UnmatchedInboundAiReplyTurnKnowledgeTest.kt`

## 验收标准

- 最新样例的第 4/5/6 项不得为 GROUNDED，除非各自 request 的 rule bodies 真正覆盖全部 facet。
- 第一项完全无据时 response=`BLOCKED`；正文即使仍有编号缺口，也不能被表示成 READY。
- 无新增数据库、无 QA 内容、无前端修改。
- 定向测试：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyDraftServiceTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest test
```

## 人工验收清单

### A-1：最新七项邮件
- 操作：训练模拟与收发件分别生成一次。
- 预期：两个响应 readiness 相同；4/5/6 至少显示 PARTIAL；缺第一项依据时整体 BLOCKED。

### A-2：完整 fixture
- 操作：测试环境给 selection/deliverables/finance/project types 提供逐项 QA 正文。
- 预期：相应 request 才能升级 GROUNDED，整体可变 READY。

### A-3：边界回归
- 操作：单问题 QA_MATCHED、无 QA FREE_FORM 各生成一次。
- 预期：原模式、正文、模型选择不变；无 requestFacts 的旧路径 readiness=READY。
