---
id: K-explicit-fact-selection-must-match-request
domain: qa
created: 2026-07-17
last_used: 2026-08-26
hit_count: 11
source: fix-v:qa-refactor-04-grounded-engine:fix-1
severity: P1
---
经验：人工显式选择若跳过 request 的关键词匹配，会让同一 intent 下无关事实被浏览器注入 grounded 草稿。
正确做法：显式规则先做 enabled/policy/answerBody 校验，再与每个 request 匹配；不匹配的选择必须拒绝或不成为 evidence，之后才按 intent 唯一分配。

2026-08-24 产品决策修订：上述“必须匹配”仅保留给自动/legacy/普通 QA 路径。`TrustReplyWorkbench` 的显式人工矩阵改为运营最终权威：存在/enabled/policy/answerBody 仍硬校验，关键词/intent mismatch 只记录诊断，不得删除人工事实或阻断 handling/发送。来源：`02-manual-fact-authority-workbench`。
