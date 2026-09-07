# Child 03 Execution Report — MIME 元数据读取与正文白名单

- Status: **FIXED（round 1/3 后全部命令绿）**
- Implementation commit: `2b8c3d6 feat(fast-p): implement 03`
- Fix commit: `95d8661 fix(fast-p): repair 03 round 1`（branch `fast/mailbox-materials`）
- Base SHA: `7c2420e80f98775c1fcf4970598617faca378c0b`
- Plan SHA-256: `9ea2c74cc40125d55f3a97e9a6de6f2c4e9cb8d47c3c2e84729390be6b8a7a4b`

## 文件变更（授权 8 个 + fix 轮 2 个同授权文件）

| 文件 | 变更 |
|---|---|
| `mail/service/MailReceiveService.kt` | content 可空；`ImapAttachmentSource`；ReceivedMail 增 uidValidity/bodyTruncated（默认兼容）；三个显式异常。 |
| `mail/service/ImapMailReceiveService.kt` | profile 只预取 ENVELOPE/CONTENT_INFO/UID；白名单 walk（附件零读取）；有界正文 + bodyTruncated；节点/时限上限；From 头先行解析；时限/结构异常显式重抛。 |
| `mail/service/MailAttachmentService.kt` / `DmarcReportParser.kt` | content=null 抛显式 metadata-content-unavailable。 |
| `config/MailAttachmentStorageProperties.kt` | metadataMaxBodyBytes=2MiB / metadataMaxMimeNodes=10000 / metadataTotalTimeoutSeconds=60。 |
| `test/.../ImapMailReceiveServiceTest.kt`（17 用例）| legacy 字节回传、metadata null content+source 完整、中文/超长名、text 附件、无名附件、嵌套 rfc822、DSN、alternative、正文截断、节点爆炸、时限超时。 |
| `test/.../DmarcReportParserTest.kt` | +null content 显式错误。 |
| `test/.../ImapMetadataFetchIT.kt`（新增，mysqlIt）| 本地 JDK-socket 脚本式 IMAP fixture：命令日志证明无附件内容 FETCH；1000 附件完整 + 附件流请求即抛错；legacy 原字节回传；text 附件不进正文；正文有界截断（字节读取期）+ 分块请求日志；慢源触发元数据时限显式错误。 |

## 命令证据（fix 后最终复跑）

| 命令 | 结果 |
|---|---|
| `JAVA_HOME=… mvn test -Dtest=ImapMailReceiveServiceTest,DmarcReportParserTest` | **PASS** — 17+4 tests, 0 failures |
| `JAVA_HOME=… mvn -Pmysql-it -Dtest=ImapMetadataFetchIT test` | **PASS** — 6/6, 0 failures |
| `JAVA_HOME=… mvn test`（全量） | **PASS** — 3133 tests, 0 failures, 8 skipped, BUILD SUCCESS |

## I-1..I-3 证据摘要

- I-1：metadata 模式 content 严格 null + source 完整（accountCode/folder/uidValidity/uid/
  partPath/messageId/encodedSize/disposition）；legacy 模式原附件字节回传（base64 CTE 经线上
  字节正确往返）；encodedSize 仅为估算；构造默认兼容旧调用。
- I-2：协议命令日志证明：头字段/正文 section（HEADER.FIELDS/TEXT/BODY[n]）之外无任何
  BODY[]/BODY.PEEK[n] 附件内容请求（19+20 与 1000 附件用例；附件流被请求即抛错）。
- I-3：19/20 与 1000 附件目录完整无截断；正文超 metadata cap 保留有界正文并 bodyTruncated
  （分块 <0.N> 读取日志证明限制作用于字节读取期）；MIME 节点爆炸/元数据时限（慢源）抛明确
  可重试错误（单元 + IT 双覆盖）。

## 修复轮变更说明（95d8661）

fixture 服务端协议修正（均在授权测试文件内）：多 item+literal 分拆单应答；响应 item 用裸
`BODY[section]`（JavaMail 1.6.2 对 `BODY.PEEK[...]` item 名解析缺陷）；HEADER.FIELDS 精确回
请求字段；TEXT/n.MIME section 服务；附件正文抛错改为 metadata 用例可选项；正文回传线上编码
字节；BODYSTRUCTURE text 叶补 body-fld-lines。生产侧：From 头先行解析 + 元数据时限/结构异常
显式重抛。详见 `fix-log.md`。

## 偏差与遗留

- 无。8 授权文件已提交；未做迁移；未 push/merge/rebase；fast-p 证据（docs/plans/fast）不入
  实现提交。
