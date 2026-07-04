---
id: K-deliverability-dns-live-verification
domain: mail
created: 2026-07-03
last_used: 2026-07-03
hit_count: 0
source: fix-v:google-spam-mitigation:fix-1
severity: P1
---
经验：发信信誉修复计划不能只写 SPF/DKIM/DMARC 目标值，必须对每个实际发信子域名做 live DNS 查询；主域名有 SPF 不代表子域名已有显式 SPF。
正确做法：计划验收必须逐个 `dig +short TXT <sender-domain>`，并把所有启用账号的 `sender_email` 域名覆盖到 DNS checklist。
反例：`docs/plans/2026-07-03/google-spam-mitigation.md:129-143` 要求补 `talents.szwebotech.cn` 与 `mail.szwebotech.cn` SPF，但复验时两个子域名 TXT 均为空。
