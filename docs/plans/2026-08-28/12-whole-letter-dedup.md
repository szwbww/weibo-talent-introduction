# 作废：本文件已被 `12-letter-closer.md` 取代（2026-08-28）

**不要执行本文件的任何内容。**

作废原因：本文件假设「一键预判」的正文由 `AiReplyDraftService.generate()` 的
`GroundedContentPlan` → `composeFromPlan` 路径产出，因此把改动落在
`AiReplyGroundedContentPlanner` / `AiReplyGroundedDraftMaterializer` /
`AiReplyHighRiskClaimValidator` 上。

2026-08-28 实测推翻该假设：一键预判走的是工作台的逐条生成 + `assemble` 拼接链路，
最终正文由 `TrustReplyWorkbenchService.verifyAssembly:1472` 的
`composeLockedItems` 决定，**完全不经过** `composeFromPlan`。按本文件实施，
一键预判的输出一个字都不会变。

- 正确的接缝与计划：`12-letter-closer.md`
- 证据与链路全文：`docs/knowledge/llm/K-oneclick-assembles-by-concatenation.md`

（本仓库的挂载目录不允许 device_bash 删除文件，故以此存根代替删除。确认后可手工删除本文件。）
