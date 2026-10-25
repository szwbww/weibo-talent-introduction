# 微软系邮箱标签写入回执

时间：2026-10-25。用户授权：给已人工识别的一批筛选层未联系专家追加“微软系邮箱”标签。

- 来源：服务器 `/tmp/qf-microsoft-audit-20261025.json`。
- 口径：CANDIDATE，email存在、operatorStatus不存在；全部MX为微软（消费者邮箱也核对MX）。混合MX的739位不纳入。
- 4,834个域名、22,215位专家；写前每个域名人数与原审计一致；原已有该标签0位。
- 备份：服务器 `/opt/talent/microsoft-tag-20261025/manifest.json`，权限由umask077限制，含docId/原email/tags/seq_no/primary_term。
- 写入：500一批，原子脚本只追加tags；if_seq_no/if_primary_term并发保护；updated=22,215，错误0。
- 回查：22,215条全部存在新标签，原有标签均保留，failures=0。
- 明细：同目录apply.jsonl、verification.json；脚本 `/tmp/tag_microsoft_experts.py`。
- 未更改operatorStatus、未发送邮件、未改既有任务配置。标签只是本次审计快照，不自动随MX或邮箱变更。
- 准备备份曾遇服务器ASCII编码错误，保留manifest-incomplete.json并重建UTF-8完整备份后才写入；回查_source参数已修正，无重复写入。
