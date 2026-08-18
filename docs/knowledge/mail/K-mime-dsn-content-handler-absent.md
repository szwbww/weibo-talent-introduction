---
id: K-mime-dsn-content-handler-absent
domain: mail
created: 2026-08-18
last_used: 2026-08-18
hit_count: 0
source: create-p:bounce-dsn-classification-and-email-invalid-writeback
severity: P1
---
经验：本仓依赖只有 `spring-boot-starter-mail`（`pom.xml:56`），**没有 `com.sun.mail:dsn`**
（`find ~/.m2 -iname "*dsn*.jar"` 零结果）。因此 `message/delivery-status` 分段
**没有注册 DataContentHandler**：从 IMAP 拉取的真实邮件里 `part.getContent()` 返回
`InputStream` 而非 `String`，`part.content as? String` 恒为 null。

后果链（2026-08-18 线上实测确认）：`BounceDetector.findDeliveryStatusBody()` 用
`part.content as? String` → null → `extractDsnStatus()` 恒 null →
`classifyBounceType()` 落 `heuristicBounce -> "SOFT"` 兜底 → **所有标准硬退信被记成 SOFT**。
实测 `bounce_record` SOFT 39 条 `dsn_status` **39/39 全为 NULL**（真软退信带 `4.x.x`，
不可能一条都没有），HARD 仅 2 条。

正确做法：读任何非 text/* 分段的文本时，`part.content as? String` 之后**必须**回退
`part.inputStream`（`getInputStream()` 返回解码后内容流，不经 mailcap 查表，任意 MIME 类型可用）。

**测试陷阱（本 bug 通过全绿测试上线的原因）**：`MimeBodyPart.setContent(String, "message/delivery-status")`
把 String 对象直接存入 DataHandler，`getContent()` 原样返回、**不查 handler**，
所以内存构造的夹具恒成功。任何 MIME 解析测试必须
`writeTo(ByteArrayOutputStream)` + `MimeMessage(session, ByteArrayInputStream)` **往返一次**，
才走真实 InputStream 路径。反例：`BounceDetectorTest:113-140` 的 `neutralMimeDsn()` / `dsnBounce()`。

关联：K-mime-dsn-before-heuristic（只规定了"MIME DSN 优先于启发式"的**顺序**，
未覆盖"MIME DSN 根本读不出来"这一失败模式，本条为其补强）。
