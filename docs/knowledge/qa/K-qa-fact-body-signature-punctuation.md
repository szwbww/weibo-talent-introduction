---
id: K-qa-fact-body-signature-punctuation
domain: qa
created: 2026-07-17
last_used: 2026-07-17
hit_count: 5
source: fix-v:qa-refactor-02-fact-card-foundation:fix-1
severity: P1
---
经验：邮件签名黑名单若只匹配词本身，常见的逗号形式会绕过事实正文边界。
正确做法：结尾短语校验覆盖常见英文/中文标点与尾随空白，并为每种签名形式写回归测试。
反例：`QaFactBodyPolicy.kt:11` 不匹配 `Best regards,`。
