---
id: K-validation-reason-before-no-match
domain: mail
created: 2026-07-19
last_used: 2026-07-19
hit_count: 3
source: fix-v:ai-reply-05-trust-boundary-readiness-plan:fix-1
severity: P1
---
经验：自动回复 reason 若先按空 evidence 返回 QA_NO_MATCH，会掩盖同一草稿已有的结构、claim、trust 或 action 校验失败。
正确做法：validation warning 必须是 resolveReason 的首个业务判断；仅在无 validation 时再映射 no-match、grounding gap 与 policy review。
反例：GroundedAutoReplyDecisionService.kt:99-104。
