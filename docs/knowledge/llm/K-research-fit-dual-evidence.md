---
id: K-research-fit-dual-evidence
domain: llm
created: 2026-07-13
last_used: 2026-07-16
hit_count: 9
source: create-p:ai-reply-02-grounding-evidence-semantics
severity: P1
---
经验：“专家研究方向是否符合项目范围”是双边比较：专家画像只提供研究侧，命中 QA 只提供项目侧。仅因画像存在就标 GROUNDED，或研究分支强制清空 candidateRuleIds，都会产生无依据匹配结论。
正确做法：研究请求仍保留 `candidateRuleIds ∩ promptRuleIds` 的有效 QA 正文；画像不足或项目依据为空任一成立即 UNSUPPORTED，全程只读现有画像，不触发 enrichment。
