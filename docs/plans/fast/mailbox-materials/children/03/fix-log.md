# Child 03 Fix Log

## Epoch 1 — Round 1/3
- Findings:
  1. ImapMetadataFetchIT fixture 服务端多 item+literal 复合 FETCH 应答被 JavaMail 1.6.2
     响应解析器整条丢弃（BODYSTRUCTURE parse error / headers 全 null），需逐 literal 单应答。
  2. 服务端响应 item 名若为 `BODY.PEEK[...]`，JavaMail parseItem 的 BODY 分支（match("BODY")
     后要求紧跟 `[`）会误走 BODYSTRUCTURE 分支并抛错 → 应答统一用裸 `BODY[section]`。
  3. 头字段请求需精确回 `HEADER.FIELDS (X)` 对应字段（而非整块头）；另需 `TEXT`、`n.MIME`
     section 服务（单 part 正文/part 头）；attachment 正文请求即抛错仅限 metadata 用例
     （legacy 用例需正常回传 → failOnAttachmentRequest 标志）。
  4. 附件/正文内容须回传线上字节（含 CTE 编码），客户端按 Content-Transfer-Encoding 解码，
     否则 base64 附件解码结果为空；BODYSTRUCTURE 的 text 叶需 body-fld-lines（缺则
     “bad lines element” 解析错）。
  5. 正文截断（超 metadataMaxBodyBytes）发生在字节读取期且确实置 bodyTruncated；legacy
     测试原先误断言 source==null（实际 I-1 以 content 区分模式）。
  6. 元数据时限需在每次 read 之后也 checkDeadline（慢源阻塞 read 返回后到点），且
     MetadataTimeoutException/StructureLimitException 不得在 readBoundedText 的 catch-all
     中被吞成空正文 → 显式重抛。
- Before: 2b8c3d65a3f72e4ddfb3e3be6e7bd0744064fb09
- Fix commit: 95d8661ed3d9e0557fe69cce6f6dde24dbea5e79
- Authorized files changed:
  - src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt
  - src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMetadataFetchIT.kt
- Commands:
  - `mvn test -Dtest=ImapMailReceiveServiceTest,DmarcReportParserTest` -> PASS (17+4, 0 failures)
  - `mvn -Pmysql-it -Dtest=ImapMetadataFetchIT test` -> PASS (6/6, 0 failures; 本地脚本式 IMAP 协议
    证据：命令日志无附件 BODY[]/BODY.PEEK[n] 内容 FETCH，正文只读 BODY[TEXT]/BODY[1]，legacy 回传
    原附件字节，bodyTruncated/时限/1000 附件用例全绿)
  - `mvn test`（全量）-> PASS (3133 tests, 0 failures, 8 skipped; BUILD SUCCESS)
- Result: FIXED
- Notes: fixture 服务端修复均在授权测试文件内；生产仅 2 处语义收紧（From 头先于正文读取、
  时限/结构异常显式重抛），已随 fix 提交。
