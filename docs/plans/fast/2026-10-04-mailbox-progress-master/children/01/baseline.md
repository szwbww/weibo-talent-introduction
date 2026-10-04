# Child 01 Baseline — 收发件箱三态标记后端

- 运行位置：worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-mailbox-progress-master`；起始 HEAD `5b5b092fdb496ed7c2ebf075d1f8375d4717341c`（seed + artifacts 提交；源码与 master base `e28e53fd898edd62905a0d45a6bf90396b18b1bf` 一致，source-manifest 10 文件 SHA256 全一致）。
- 环境：JDK `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`；MySQL 容器 `ti-mysql-it`（localhost:3306，root/root，库 `talent_introduction`，连接串带 `allowPublicKeyRetrieval=true`）；Docker=OrbStack（`DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock` + `-Dapi.version=1.40`）。
- 日志：`/tmp/fastp-mbp-b1.log`、`/tmp/fastp-mbp-b2.log`、`/tmp/fastp-mbp-b3.log`。

## 命令与基线结果

| # | 命令 | exit | 计数 | 结果 |
|---|---|---:|---|---|
| B1 | `mvn -DskipTests test-compile` | 0 | BUILD SUCCESS | 编译基线干净 |
| B2 | `mvn -DmysqlIt=true -DskipNodeTests=true -Dtest=MailboxConversationControllerTest,MailboxConversationRepositoryIT,MailboxSuspensionServiceIT test` | 1 | Tests run **67**, Failures **0**, Errors **1** | 唯一既有失败见下 |
| B3 | `DOCKER_HOST=... mvn -DmigrationIt=true -Dapi.version=1.40 -DskipNodeTests=true -Dtest=FlywayMigrationIntegrationTest test` | 0 | Tests run **35**, F0, E0 | 全绿 |

B2 分文件：

| 测试类 | Tests | F | E |
|---|---:|---:|---:|
| MailboxConversationRepositoryIT | 25 | 0 | 1 |
| MailboxConversationControllerTest | 33 | 0 | 0 |
| MailboxSuspensionServiceIT | 9 | 0 | 0 |

唯一既有错误（实施后必须保持不变，不得隐藏或"顺手修复"）：

- `MailboxConversationRepositoryIT.replied filter excludes followed and dismissed experts until a new inbound arrives`（`MailboxConversationRepositoryIT.kt:225`）
- `DataIntegrityViolationException: Field 'dismissed_at' doesn't have a default value`（INSERT `expert_replied_dismissal (username, expert_contact_id, last_inbound_id)` 未提供 `dismissed_at`）；属 V143 之后的既有 fixture 缺陷。

## 判定规则

- 实施后 B1 必须 exit 0；B3 必须 35/0/0（任何 Failures/Errors 即回归）。
- B2 只允许保留上述 1 个既有 error；新增任何 F/E 即回归。新增用例通过数单独记录。
