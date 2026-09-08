# Execution Report — Child 01 附件元数据存储兼容

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials/docs/plans/2026-09-07/01-attachment-storage-compat.md`
- Plan SHA-256: `64e43cf93a76e8285dea7b69a63d9e22c0db3dd94fdc25491249d44ef71177d3`
- Execution ID: `…/01-attachment-storage-compat.md@64e43cf9…`
- Execution epoch: NEW
- Executor: Impl01
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials`
- Target branch: `fast/mailbox-materials`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials@fast/mailbox-materials@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-mailbox-materials`
- Child base SHA: `8a0c5360e25e875e52800d17797a7b1ea4bd452c`
- Pre-execution code SHA: `a61ecb543668532317bebd7864286352dc3359c7` (docs seed; src/ tree = base 8a0c536)
- Post-execution code SHA: `8779d71b1bf17a8cbe1f9e41a1fd2e3d35f74f94` (impl commit, HEAD of branch)
- Implementation boundary: `8a0c536..8779d71` (src/ delta only; docs/plans/fast/** excluded, untracked)
- V118 numbering re-verified free at execution (max present `V117__convert_bounce_rate_pause_to_warning.sql`)

## Task Status

| 计划任务 | Status | Files | Evidence |
|---|---|---|---|
| 1. 实体 nullable + V118 迁移（I-1/I-3；保留 V36 CHECK/FK/历史值，不新增状态列） | IMPLEMENTED | MailAttachment.kt、V118__allow_attachment_metadata_only.sql | Flyway IT 13/13（含新 V118 用例） |
| 2. 三个服务 null 语义（list DTO null、download/浏览/提取拒绝空路径、Files.size 实际长度） | IMPLEMENTED | MailboxAttachmentService.kt、ExpertDocumentBrowseService.kt、DocumentTextExtractor.kt | 3 个服务测试 7+14+5 全绿 |
| 3. ExpertContactManagementController.MailAttachmentResponse nullable、保留 mailRecordId 约束与旧 attachments 数组（不插 processing-owner） | IMPLEMENTED | ExpertContactManagementController.kt | 全量 suite 编译+3101 测试通过；diff 仅 2 字段类型 |
| 4. 三项服务测试扩展 + FlywayMigrationIntegrationTest 目标 118（V117 升级数据值、XOR、300 字中文名、0B 合法文件） | IMPLEMENTED | 3 个测试文件 + FlywayMigrationIntegrationTest.kt | 见命令 1/2 计数 |

## 不变量核对

- **I-1 空值语义**：`MailAttachment.fileSize: Long?` / `storagePath: String?`；V118 `file_size BIGINT NULL DEFAULT NULL`、`storage_path VARCHAR(1024) NULL`、`file_name TEXT NOT NULL`。列表 DTO（`AttachmentMetaResponse.fileSize: Long?`、`ExpertDocumentFile.fileSize: Long?`）null 直通不转 0；下载/提取入口先 `requireNotNull(storagePath)`（消息 "no local file (storage_path is null)"）再碰文件系统，测试断言 null 路径不打开流（`download rejects metadata-only attachment…`、`resolveForDownload rejects metadata-only…`、`extract rejects attachment with no storage path…`）。真实 0 字节文件：`download accepts a real zero-byte file as size zero`（size=0 且文件存在）；Flyway IT 断言 `file_size=0 + storage_path IS NOT NULL` 行为合法。
- **I-2 旧文件兼容与路径**：下载/浏览响应 `fileSize = Files.size(已验证 realpath)`（stored 值不一致时取实际值——两个服务各有 mismatch 测试：`download reports actual size…`、`resolveForDownload reports actual file size…`）；跨专家、越界、符号链接既有拒绝测试原样保留全绿；`resolveForPreview` 复用 `resolveForDownload`，无本地文件时同被拒。
- **I-3 文件名与审核**：`file_name` 放宽 TEXT；Flyway IT 300 字中文名（"研"×300）插入后 `CHAR_LENGTH=300` 且逐字往返一致；V23 时代 fixture 的 `expert_document.document_status='PENDING_REVIEW'` 升到 V118 后不变；历史 file_name/file_size/storage_path 值原样保留。
- **持久化契约（总计划）**：仅 3 个既有列改可空/容量；未新增任何状态列；Flyway IT 断言 `chk_mail_attachment_owner`（V36 XOR）、`fk_mail_attachment_record`、`fk_mail_attachment_inbound` 在 V118 后仍存在，且双 NULL owner 插入仍被 CHECK 拒绝；历史数据不批量重写。

## 实现要点（与现状审计复核）

- 符号级复核了 audit E2 读/写路径全集：主代码中读取 `fileSize/storagePath` 的只有本计划 3 个服务文件（grep `.fileSize|.storagePath` 全 src/main 命中即此 3 处 + 2 个 controller 的 `resource.fileSize` 消费，controller 不可改且 `DocumentFileResource.fileSize` 保持 Long，故无需动 controller）；`MailAttachment` 构造点（MailAttachmentService 两处写入口）传非空值，nullable 化不破坏编译。
- 列表 DTO 规则：`ExpertDocumentBrowseService.listDocuments` 对 `storagePath == null` 的行输出 `fileSize=null`、`downloadUrl=null`、`previewUrl=null`、`previewable=false`；已落地行保持原 URL 与 content-type 推导（`previewable = hasLocalFile && isPreviewable(contentType)` 对旧行恒等于原行为）。
- Controller DTO：`ExpertContactManagementController.MailAttachmentResponse.fileSize/storagePath` 改 nullable；`toResponse()` 保留 `requireNotNull(mailRecordId)`（旧约束）与旧 attachments 数组结构；未向 payload 引入 processing-owner。
- FlywayMigrationIntegrationTest：既有全链目标断言 "117"→"118"（fresh、V23/V24 升级、V23 历史、ambiguous rerun、V117 数据用例），用例名同步改为 through/to V118；新增 `V118 allows metadata-only attachments preserving historical values and owner XOR`（V23 fixture 起步 → 全链升 118 → 历史值/审核状态/列 nullable/DATA_TYPE='text'/XOR+FK/元数据登记/300 字中文名/0B 断言）。

## Commands

全部在 worktree 根、JAVA_HOME=zulu-11 下新鲜执行（最终代码态）：

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test -Dtest=MailboxAttachmentServiceTest,ExpertDocumentBrowseServiceTest,DocumentTextExtractorTest` | PASS (exit 0) | MailboxAttachmentServiceTest Tests run: 7, F:0, E:0, S:0；ExpertDocumentBrowseServiceTest Tests run: 14, F:0, E:0, S:0；DocumentTextExtractorTest Tests run: 5, F:0, E:0, S:0；同 build exec node-test 671/671 pass；BUILD SUCCESS |
| `JAVA_HOME=… mvn -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true test`（裸命令） | BLOCKED (exit 1) — 环境 | Testcontainers 无法连接 daemon：docker-java 固定 client API 1.32，本机 daemon（OrbStack 29.4.0）MinAPIVersion 1.40 → `Tests run: 1, Errors: 1, IllegalStateException: Docker is required for Flyway migration tests`；与仓库知识 K-flyway-gate-must-pass-on-fresh-db 及既往 fast 轮次（2026-09-02 c1/c4/c5/c7、batch-send 02b/03/04a）记录的同一环境基线一致；非实现缺陷，不伪装通过 |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=… mvn -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40 test`（知识文档记录的 workaround，同仓库先例） | PASS (exit 0) | FlywayMigrationIntegrationTest **Tests run: 13, Failures: 0, Errors: 0, Skipped: 0**（真实 mysql:8.0.36 容器，迁移实际执行 ~104s；含新 V118 用例与全部 117→118 升级用例）；BUILD SUCCESS |
| `JAVA_HOME=… mvn test`（全量，docker-free） | PASS (exit 0) | **Tests run: 3101, Failures: 0, Errors: 0, Skipped: 8**（skip 8 = 既有 7 个 docker/IT 门控 + 1 @Disabled，均为预存）；exec node-test/node-check 阶段全绿（node 失败会令 exec 插件非零退出）；BUILD SUCCESS |
| `git diff --check`（提交前） | PASS (exit 0) | 无空白错误 |

## Changed Files（commit 8779d71，仅 10 个授权文件，435+/30−）

- `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailAttachment.kt` — fileSize/storagePath 改 nullable（I-1）
- `src/main/resources/db/migration/V118__allow_attachment_metadata_only.sql`（新增）— 列可空/容量契约，保留 CHECK/FK/历史值
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentService.kt` — 列表 DTO fileSize nullable；下载先拒 null 路径、fileSize=Files.size(实际)
- `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt` — 列表 DTO fileSize/downloadUrl/previewUrl nullable、未落地 previewable=false；下载/解析拒 null 路径、fileSize=Files.size(实际)
- `src/main/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractor.kt` — resolveAttachment 读文件前拒 null 路径（不静默跳过）
- `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt` — MailAttachmentResponse size/path nullable，保留 mailRecordId 约束与数组结构
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentServiceTest.kt` — +4 用例（list null 不转 0、null 路径拒、实际大小、0B 文件）
- `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt` — +3 用例 + 既有 URL 断言改精确断言（nullable 化后编译需要）
- `src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt` — +1 用例（null 路径读文件前拒绝）
- `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` — 目标版本 117→118（含用例名）、+1 V118 用例（历史值/XOR/长中文名/0B/列契约）

未触碰：docs/plans/fast/**、docs/plans/2026-09-07/**（均在提交外；docs/plans/fast 保持 untracked，归 controller）。

## Deviations

- 无实现偏离。环境注记（非偏离、既往同因）：裸 Flyway IT 命令在 OrbStack daemon min API 1.40 vs docker-java 1.32 下无法启动容器，需 `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock -Dapi.version=1.40`（仓库知识 K-flyway-gate-must-pass-on-fresh-db 与多轮 fast 先例同款）；带 workaround 后 13/13 真跑通过，V82 fresh 门禁在本基线上未再触发（既往轮次记录的漂移门禁不适用于当前树）。
- 既有 `ExpertDocumentBrowseServiceTest.lists documents for expert contact` 的 2 组 `downloadUrl/previewUrl contains` 断言改为精确 `assertEquals` URL（downloadUrl 变 nullable 后的编译要求，语义等值且更强）。

## Freshness

- Plan identity rechecked: YES（sha256 未变 64e43cf9…）
- Worktree identity rechecked: YES（root/branch/git-dir 匹配 expect）
- Reported commit reachable from target branch: YES（8779d71 为 worktree HEAD，父 a61ecb5）
- Required commands run this invocation: YES（命令 1/3 裸环境通过；命令 2 裸环境阻塞 + workaround 全绿，均为本调用新鲜执行）
- Historical evidence used only as baseline: YES（环境根因核对用既往记录，未替代本调用命令结果）

## Remaining Blocker

- None。

## Next Action

- READY_FOR_VERIFICATION → 运行 verify-p
