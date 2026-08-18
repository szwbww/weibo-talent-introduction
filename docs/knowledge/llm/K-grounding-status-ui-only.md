---
id: K-grounding-status-ui-only
domain: llm
created: 2026-07-13
last_used: 2026-08-18
hit_count: 19
source: create-p:ai-reply-03-structured-answer-materialization
last_source: create-p:trust-reply-unsupported-answer-v1
severity: P1
---
经验：`GROUNDED/PARTIAL/UNSUPPORTED` 是操作端审核控制数据，不是对外邮件文案；fallback composer 若直接把 confirmation/unsupported 常量拼入 section，会把内部状态原样外发。
正确做法：状态留在 requestCoverage；PARTIAL 正文只输出有据事实；UNSUPPORTED 在系统自动/QA-grounded 路径不生成事实答案。若产品提供显式 `ANSWER_FROM_OPERATOR_INPUT`，只能由操作员非空说明授权后逐项生成，保持 UNSUPPORTED、空 claims、显式采用，且不得进入自动回复或 QA evidence。后端最终 materializer 始终拒绝 raw 状态 token 和内部提示句。
