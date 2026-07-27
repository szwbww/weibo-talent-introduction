# Verification Blocked

## Divergence report

| Round | P1 count | Status |
|---|---:|---|
| fix-1 | 1 | 原始第 4 问被改写为 `enterprise partners`，掩盖额外 `enterprise.project_types` 命中 |
| fix-2 | 1 | 第 4 问已恢复，但声称“Full original expert-mail fixture”的 `janmedaMail` 仍重写了其余原文与 URL，未落实完整原始邮件 fixture 约束 |

P1 数未严格下降。按 `fix-v` 停止本计划继续修复，不创建常规修复轮。

## Root-cause diagnosis (plan quality gate)

原计划范围不大，且“使用完整原始专家邮件 fixture”已经明确；问题不是计划缺少约束，而是修复只恢复了触发当次缺陷的 G4 句子，把“完整原文”误解成“保留七组等价语义”。当前运行时对真实原文的独立复现已经得到 7 groups、14 intents 与正确标题，但仓库内持久回归仍使用改写 fixture，继续保留同类回归风险。

## Decomposition proposal

1. **原始专家邮件 fixture 单文件回归**
   - Scope: `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
   - 独立新子计划只负责把 `janmedaMail` 固定为用户提供的完整原始邮件正文；catalog、extractor、业务实现与 QA 数据均不改。

