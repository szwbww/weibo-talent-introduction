---
id: K-manual-send-final-validation-complete
domain: mail
created: 2026-07-20
last_used: 2026-07-20
hit_count: 4
source: fix-v:ai-reply-07-final-send-integrity-plan:fix-2
last_source: fix-v:ai-reply-07-final-send-integrity-plan:stop-after-fix-3
severity: P1
---
经验：只校验正文或只抽取 http(s) 链接，且在空 QA 时跳过 claim 校验，会让最终 subject、非 http(s) href 或纯人工无依据高风险内容绕过 SMTP 前门禁。
正确做法：先渲染并校验最终 subject/text/HTML，枚举全部 href；空 QA 仅免除“缺少 QA”本身，不能免除确定性无依据风险检查。
反例：PendingMailOperationService.kt:539-546（漏 rendered subject，且仅匹配带引号 href）。
