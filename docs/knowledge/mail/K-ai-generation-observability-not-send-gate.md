---
id: K-ai-generation-observability-not-send-gate
domain: mail
created: 2026-07-21
last_used: 2026-07-25
hit_count: 32
source: create-p:ai-adopt-direct-manual-send
last_source: fix-v:ai-reply-streaming-dual-ttl-cancel-plan:blocked-after-fix-1
severity: P1
---
经验：AI 生成状态不是纯人工邮件的历史发送审批，但产品可以在“采用 AI 结果”边界明确拒绝未经过 LLM 的 fallback；两种门禁不能混为一谈。
正确做法：采用入口按当前草稿自身 `usedLlm/generationState` 决定能否复制到编辑器；LLM 失败参考不可采用。运营自行撰写的编辑器正文仍不读取历史 READY/NEEDS_REVIEW/BLOCKED、draft identity 或审计结果，最终外发只走当前变量渲染、事实复验与发送链。
