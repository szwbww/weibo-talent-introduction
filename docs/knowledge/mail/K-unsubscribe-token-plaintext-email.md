---
id: K-unsubscribe-token-plaintext-email
domain: mail
created: 2026-09-25
last_used: 2026-09-25
hit_count: 2
source: create-p:mail-open-tracking-00-master
severity: P1
---

2026-09-25重新审计：旧“现行退订URL直接base64邮箱”结论已失效；仅兼容分支仍保留该格式。

- UnsubscribeTokenService:17–43装配repository时用SecureRandom32字节Base64URL token存表，按规范化email查找/复用；新URL不编码邮箱。
- repository=null的测试/兼容分支走legacySign；verify先查表再verifyLegacy，secret为空时旧格式校验直接返回null。
- enabled在有repository时只要求baseUrl；无repository才同时要求secret。不能用只装配fallback的单元测试代表生产行为。

可复用规则：Base64不是隐私保护；新公网token优先独立随机标识，不编码邮箱。不要为缩减测试改动主动加入生产可空依赖；先grep构造点，再为实际生产装配测试。
证据：src/main/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenService.kt:17–71。
