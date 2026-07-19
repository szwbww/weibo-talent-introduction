---
id: K-ai-preflight-version-input-charset
domain: mail
created: 2026-07-19
last_used: 2026-07-20
hit_count: 2
source: fix-v:ai-reply-06-draft-audit-evidence-preflight-plan:fix-1
severity: P1
---
经验：版本 token 仅限制长度会接受控制字符和任意文本，破坏 API 输入契约及稳定 4xx 语义。
正确做法：expectedEvidenceSetVersion 在服务端按长度和明确字符集校验；非法 token 返回 BAD_REQUEST，不参与事实判断。
反例：PendingMailOperationService.kt:543-551。
