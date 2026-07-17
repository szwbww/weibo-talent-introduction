---
id: K-explicit-fact-selection-must-match-request
domain: qa
created: 2026-07-17
last_used: 2026-07-17
hit_count: 5
source: fix-v:qa-refactor-04-grounded-engine:fix-1
severity: P1
---
经验：人工显式选择若跳过 request 的关键词匹配，会让同一 intent 下无关事实被浏览器注入 grounded 草稿。
正确做法：显式规则先做 enabled/policy/answerBody 校验，再与每个 request 匹配；不匹配的选择必须拒绝或不成为 evidence，之后才按 intent 唯一分配。
