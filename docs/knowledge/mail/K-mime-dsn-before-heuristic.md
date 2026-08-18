---
id: K-mime-dsn-before-heuristic
domain: mail
created: 2026-06-26
last_used: 2026-08-18
hit_count: 2
source: fix-v:inbound-selfcheck-bounce-visibility:fix-1
severity: P1
---
经验：退信 MIME 标准结构是比 from/subject/body 关键词更强的信号；如果先跑启发式并在未命中时返回，会漏掉中性主题或中性发件人的标准 DSN。
正确做法：`parseBounceDetails(Message)` 先读取 `message/delivery-status` 的 `Status:` 和 `Original-Message-ID:`；只要 MIME DSN 存在就构造退信信号，再用文本启发式补充失败收件人和原因。
反例：`BounceDetector.kt:55` 在读取 MIME DSN 前执行 `detect(...) ?: return null`，导致 `BounceDetector.kt:57-65` 的标准 DSN 解析被跳过。

关联补强：K-mime-dsn-content-handler-absent —— 本条只规定了"MIME DSN 优先于启发式"的**顺序**；
无 `com.sun.mail:dsn` 依赖时 `part.content as? String` 恒为 null，MIME DSN 根本读不出来，
顺序正确也无效。改退信解析必须同时读该条。
