# 验证未收敛

## 发散报告

- Round 1：5 个 P1
- Round 2：4 个 P1
- Round 3：4 个 P1

Verification is not converging. Round 2 had 4 P1s; Round 3 has 4 P1s. 已达到第 3 轮上限，停止修复轮次。

## 根因诊断（计划质量门）

原计划把动作识别、候选修复、fallback readiness 与自动回复原因归类放在同一闭环内；前两轮修复只覆盖了单个分支，未以该闭环的端到端语义作为收敛单位，导致相同安全边界在相邻分支反复遗留。原计划不超过 10 个文件、未新增共享字段；根因是该闭环缺少可独立复验的拆分边界。

## 拆分建议

等待人工批准后，拆成新的独立子计划并各自重新开始 fix-v 周期：

1. 动作语义子计划：`AiReplyActionPolicy.kt`、`AiReplyActionPolicyTest.kt`。
2. Grounded fallback/readiness 子计划：`AiReplyDraftService.kt`、`AiReplyDraftServiceTest.kt`。
3. 自动决策 warning 归类子计划：`GroundedAutoReplyDecisionService.kt`、`GroundedAutoReplyDecisionServiceTest.kt`。
