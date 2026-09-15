# 作废：本文件已被 `13-letter-orchestrator.md` 取代（2026-08-28）

**不要执行本文件的任何内容。**

作废原因同 `12-whole-letter-dedup.md`：本文件把 `claims → paragraphs` 协议改造落在
`AiReplyGroundedDraftMaterializer` 与 `AiReplyDraftService` 的提示词上，
而一键预判的最终正文由 `TrustReplyWorkbenchService.verifyAssembly:1472` 的
`composeLockedItems` 决定，不经过那条链路。

- 正确的接缝与计划：`13-letter-orchestrator.md`（编排是 assemble 处的第二次调用）
- 证据与链路全文：`docs/knowledge/llm/K-oneclick-assembles-by-concatenation.md`

（挂载目录不允许 device_bash 删除文件，故以此存根代替删除。）
