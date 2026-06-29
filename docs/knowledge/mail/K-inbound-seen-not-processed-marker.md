---
id: K-inbound-seen-not-processed-marker
domain: mail
created: 2026-06-29
last_used: 2026-06-29
hit_count: 1
source: create-p:inbound-uid-cursor-and-backfill
severity: P1
---
经验：IMAP `\Seen` 标志是用户行为（手机/网页端打开即置位），**不可**当作「该入站邮件已被系统处理」的标记。`ImapMailReceiveService.fetchUnread`（旧）用 `filterNot{SEEN}` 拉取，导致专家回复在被拉取前若已被标记已读会永久漏处理（实例：Sandra UID 22，18:41 到达、检查前已读、收发信箱无记录）。
正确做法：入站「是否已处理」的唯一真相源是 `inbound_mail_processing(sender_account_code, imap_uid)` 唯一键（`processSingle` :70 已据此去重）；拉取范围用 **UID 游标**（按账号存 `last_uid` + `uid_validity`，UIDVALIDITY 变更则从 0 重扫），不依赖 SEEN。`markSeen` 仅作降噪副作用保留。游标推进必须「停在批次内首个抛异常 UID 的下方」以防丢信（不可无条件推进到 max UID）。
关联：补抓漏邮件须走同一条 `processSingle` 路径（写 mail_record + inbound_mail_processing）才能在收发信箱可见且被去重。注意 `fetchUnseenMessages`（退信 DSN 路径）是另一条独立通道，仍按 SEEN 过滤。
