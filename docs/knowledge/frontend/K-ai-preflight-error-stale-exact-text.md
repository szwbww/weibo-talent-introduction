---
id: K-ai-preflight-error-stale-exact-text
domain: frontend
created: 2026-07-20
last_used: 2026-07-20
hit_count: 1
source: fix-v:ai-reply-06-draft-audit-evidence-preflight-plan:fix-2
severity: P1
---
经验：异步校验若成功路径做 text/draft stale guard、异常路径只看请求序号，debounce 尚未发起新请求时旧失败仍会显示到新正文。
正确做法：成功和异常响应共用同一个 recordId、seq、draftId、exact text、当前详情校验；输入发生时旧响应不得渲染。
反例：src/main/resources/static/app.js:8920-8928。
