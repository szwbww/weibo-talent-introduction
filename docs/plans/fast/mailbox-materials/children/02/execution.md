# Child 02 Execution Report — 有界、持久化附件传输服务

状态：**READY_FOR_VERIFICATION**
- Plan: docs/plans/2026-09-07/02-attachment-transfer-core.md
- Plan SHA-256: fb8d6a10e602841c8beac841d17c8c5fe5532f3802da7f8d7d5cb455697328ec（执行中未变）
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials @ fast/mailbox-materials
- Base SHA: 8779d71f00567625ebebe206801994aefcc5725b；实施提交：**7c2420e80f98775c1fcf4970598617faca378c0b** `feat(fast-p): implement 02`（恰 10 个授权文件；docs/plans/fast 未纳入提交）
- 执行者: Impl02（续作轮：原 BLOCKED 报告后完成 3 项修复）

## 变更文件（10/10 授权文件）
1. `src/main/resources/db/migration/V119__create_mail_attachment_transfer.sql` 新增：26 列、唯一键 (account_code,folder,uid_validity,imap_uid,part_path) + attachment_id、5 索引、3 CHECK、2 FK、folder utf8mb4_bin
2. `.../mail/domain/MailAttachmentTransfer.kt` 新增（实体 + purpose/state 常量）
3. `.../mail/repository/MailAttachmentTransferRepository.kt` 新增（幂等查找、CAS 领取容量子查询、续租、commit/fail CAS、租约恢复、GET_LOCK/RELEASE_LOCK）
4. `.../mail/service/AttachmentTransferService.kt` 新增（幂等登记含 MATERIAL/DMARC 校验与并发冲突新事务回查、入队 500/5000 上限、DMARC SYSTEM 规则、归属与就绪判定）
5. `.../mail/service/AttachmentTransferWorker.kt` 新增（2 驱动线程 + 独立续租/watchdog、命名锁领取、总时限硬关、.part→原子转正→事务提交、崩溃收敛、purpose consumer 注册 + AttachmentTransferTransactionConfig(REQUIRES_NEW)）
6. `.../mail/service/ImapAttachmentContentFetcher.kt` 新增（READ_ONLY 校验链、partial fetch 只取目标 section、forceClose 硬关、close 时同时关 store、错误脱敏）
7. `.../config/MailAttachmentStorageProperties.kt` 修改（metadataOnly=false + 限额/超时/缓冲/租约默认）
8. `src/test/.../mail/service/AttachmentTransferServiceTest.kt` 新增（20 单测）
9. `src/test/.../mail/service/AttachmentTransferWorkerIT.kt` 新增（mysqlIt + 本地 JDK-socket IMAP fixture，12 场景）
10. `src/test/.../campaign/repository/FlywayMigrationIntegrationTest.kt` 修改（118→119 + V119 契约测试）

## 必需命令（全部于最终状态下新鲜执行）
| 命令 | 结果 |
|---|---|
| `mvn test -Dtest=AttachmentTransferServiceTest` | PASS — Tests run: 20, Failures: 0, Errors: 0 |
| `mvn -Pmysql-it -Dtest=AttachmentTransferWorkerIT test` | PASS — Tests run: 12, Failures: 0, Errors: 0（真实 MySQL 127.0.0.1:3306） |
| 裸 `mvn -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true test` | 复现环境块：`BadRequestException (Status 400: client version 1.32 is too old. Minimum supported API version is 1.40)` → `IllegalStateException: Docker is required`（Tests run: 1, Errors: 1） |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40 test` | PASS — Tests run: 14, Failures: 0, Errors: 0 |
| `mvn test`（全量） | PASS — Tests run: 3121, Failures: 0, Errors: 0, Skipped: 8 — BUILD SUCCESS |

## 续作轮修复（自上一轮 BLOCKED 起）
1. **STORED 提交恒回滚**（mail_attachment 无 updated_at 列 → 附件回写 SQL 报 Unknown column → 整事务回滚、CAS 看似 0 行）：删除回写中的 updated_at。
2. **fetcher 未关 IMAP Store**：每条下载泄漏连接、fixture 会话不结束（并发观测被污染）：close() 同时关 store；forceClose 语义保留。
3. **JavaMail 分块属性名错误**：`mail.imap.fetchblocksize` 应为 `mail.imap.fetchsize`（1.6.7 读 fetchsize；默认 16KiB）。修复后按配置 64KiB 分块；慢源 fixture 改为「每响应前延迟 + 给全量请求块」（JavaMail 将中途短给视为 EOF）。
4. **并发同源登记**：Spring Data JDBC 把 INSERT 冲突包成 DbActionExecutionException（非 DataAccessException）；REPEATABLE READ 快照使同事务回查不可见 → TransactionTemplate 统一 REQUIRES_NEW + 新事务轮询回查；register 去 @Transactional（单 INSERT 天然原子；冲突后旧事务 rollback-only 会抛 UnexpectedRollbackException）。
5. **并发观测**：fixture 会话改为 startedContent 落账（成功下载也关账）+ 区间重叠法计算最大并发；测试用 900KiB/150ms 块延迟保证两账号窗口确定重叠（maxGlobal=2、同账号=1）。
6. **V119 迁移测试**：V23 阶段种子 mail_attachment 需带 NOT NULL 的 file_size/storage_path。

## 覆盖（worker IT 12 场景 = I-1..I-4 验收）
重复提交 10 次仅 1 下载且 attempt=1；同源 8 线程并发登记仅 1 行；同名跨 UID 不同行 + 重复登记幂等；两账号全局 2/同账号 1；1.5s/64KiB 慢源在 5s 总时限内 FAILED/TIMEOUT 且连接被硬关闭、其余文件继续；1.5MiB 超限立即 FAILED/LIMIT_EXCEEDED 且 .part 不残留；重启只恢复已请求项（METADATA_ONLY 不动、已完成不重下）；转正与 DB 提交间崩溃收敛（不重复下载、expert_document 不复制、storage_path 提交后才可见）；旧租约/旧 token 不能提交但恢复可完成（attempt=2、下载 1 次）；SOURCE_MISSING/UIDVALIDITY_MISMATCH/MESSAGE_ID_MISMATCH/PART_NOT_FOUND/PART_TYPE_MISMATCH 全链 SOURCE_UNAVAILABLE 且零内容读取、不自动重试；DMARC 规则（无附件/禁止 attachment/仅 SYSTEM/未知 purpose 保持 QUEUED 并报配置错误）；已就绪旧附件 already-ready 不排队。单元 20 项覆盖登记校验/幂等/入队/上限/重试/归属。V119 迁移契约测试覆盖列/索引/CHECK/FK/大小写唯一键/默认值/组合规则。

## 偏差
- 无授权文件外改动；无计划修订；不做修复轮计数（本轮为 Main 认可的续作轮，未消耗 fix_round）。
- 慢源 fixture 采用块间延迟而非字面 1B/s（JavaMail 1.6.7 synchronized 读 literal 使逐字节慢流无法被 forceClose 抢占；见 worker IT 注释）。

## 遗留
- 无已知 in-scope 失败。verify-p 可开始；MySQL scratch 库与 OrbStack Docker 按本报告命令复跑。
