# Child 01 执行报告 — 收发件箱查询、邮件标签投影与主题兼容

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-refinement/docs/plans/2026-09-09/01-mailbox-refinement-data.md`
- Plan SHA-256: `825885c98c85927a80804749ebff8a0cdfcefc0ff86d8fe917e074a82a13ddea`（执行前/后两次校验一致，未变化）
- Execution ID: `<plan>@825885c98c85927a80804749ebff8a0cdfcefc0ff86d8fe917e074a82a13ddea`
- Execution epoch: NEW
- Approval basis: 控制器下发的 child 01 brief（docs/plans/fast/mailbox-refinement/children/01/brief.md）+ 本计划文件全文
- Executor: Implementer01（fast-p child 01）
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-refinement`
- Target branch: `fast/mailbox-refinement`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-refinement@fast/mailbox-refinement@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-mailbox-refinement`
- Child base SHA（pre-execution code SHA）: `af25bf54df2bc70dd0fe9e3254b246a49395219c`（brief 声明）
- Implementation boundary: `af25bf54df2bc70dd0fe9e3254b246a49395219c..cf257779ae420ab4c745b20aa4de6e6942a66b18`
- Evidence HEAD: 提交 `cf257779ae420ab4c745b20aa4de6e6942a66b18`（`feat(fast-p): implement 01`，9 文件；docs/plans/fast/** 未纳入，由控制器另行提交）

## 环境确认（mysqlIt 门禁）

- 3306 监听确认是隔离容器：`docker ps` 显示 `mailbox-refinement-mysql`（mysql:8.0.36）映射 `0.0.0.0:3306->3306/tcp`；lsof 显示监听进程为 OrbStack（容器运行时）。
- 容器内探针：`docker exec mailbox-refinement-mysql mysql -uroot -proot -N -e "SELECT @@hostname, VERSION(); SHOW DATABASES;"` → `5016f88e1b86 / 8.0.36`，库 `talent_introduction` 存在于容器内。未对任何业务/开发库执行 IT。
- JDK：每次 mvn 均带 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`。
- MySQL 8 caching_sha2_password 非 SSL 需要 JDBC URL 带 `allowPublicKeyRetrieval=true`；mysql-it 两轮与 ControllerTest mysqlIt 轮通过 `DB_URL=...&allowPublicKeyRetrieval=true` 指向 `127.0.0.1:3306/talent_introduction`（test application.yml 的 `${DB_URL:...}` 覆盖口，库名仍为容器本地 `talent_introduction`，先确认容器再运行）。首次不带该参数运行报 `Public Key Retrieval is not allowed`（连接层，非用例失败），加参后全绿。
- 未运行 FlywayMigrationIntegrationTest docker-java 路径（brief 明示不需要）。

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T1 查询排序与筛选（I-1/I-2/I-5） | IMPLEMENTED | Repository/Service/Controller/IT/SqlCompat | cmd1、cmd2（21 IT）、cmd3 全绿；I-1 四专家矩阵 IT + 括号回归 IT + keyword/recipientEmail/日期验证用例 |
| T2 当前窗口标签（I-2/I-3/I-5） | IMPLEMENTED | Service/Controller/ControllerTest | ControllerTest mysqlIt 轮 21/21：20 封一窗口仅 1 次 listTagsBatch、同数值 id 不串标签、OUTBOUND 空 tags、落库→读取→删除→读取 |
| T3 MIME 主题（I-4/I-5） | IMPLEMENTED | MailSubjectDecoder.kt(新)/ImapMailReceiveService/Service/测试 x2 | DecoderTest 12/12、ImapMailReceiveServiceTest 16/16；header 假消息证明解码不新增内容访问 |
| T4 本页专家标签投影（I-2/I-5/I-6） | IMPLEMENTED | Service/Controller/ControllerTest | ControllerTest mysqlIt 轮：同层 20 人 1 次、跨三层 ≤3 次、0 次场景、失败层隔离、null/[] 语义、与邮件标签分离 |

## 实现内容（与计划逐条对应）

### T1
- `ConversationFilter` 增 `recipientEmail`/`keyword`；service trim→null、≤255、超长 400、`startDate 晚于 endDate → 400`。
- page/explain 共用私有 `orderByClause(filter)`：全部视图 `(pending?0:1, NULL来信?1:0, latest_reply DESC, contactId DESC)`；关注/待处理/旧 waitingReply 省略 pending 首项。`latest_reply_at = MAX(CASE WHEN u.source='INBOUND_PROCESSING' THEN u.event_at END)` 纯 SQL 投影，无表字段、无新响应字段；`latest_event_at` 映射保留。
- membership 扩展 keyword（subject/cleaned_body/body，仅 EXISTS 内，NULL 列按 SQL 空值）与 recipientEmail（出站 `ec.expert_email`、入站 `impi.from_email`，同旧 mailbox 口径），与方向/日期/主题/label 全部 AND 在同一封消息 EXISTS。
- 修复审计 X1：outbound/inbound 两个方向 EXISTS 的 OR 组外加整层括号再与 q/followed AND（RepositoryIT `q and followed can never be bypassed...` 用例证明）。
- 无 ROW_NUMBER/OVER/CTE（MySQL 5.7 兼容），无新索引/迁移；EXPLAIN 测量见下。

### T2
- `ConversationMessageItemResponse.tags: List<TagView> = emptyList()`；service 取页后仅对真实 INBOUND_PROCESSING id 调 `InboundMailTagService.listTagsBatch` 一次（窗口内无 processing 行则不发请求）；OUTBOUND 恒空，绝不按 source_inbound_id/数值巧合映射。标签写/删仍走既有端点与服务（未新增写路径）。

### T3
- 新纯函数 `MailSubjectDecoder.decode(String?):String?`：null/普通文本不变；合法 folding 先 `MimeUtility.unfold` 一次再 `decodeText` 单次；无 HTML unescape/循环；未知 charset/损坏输入回退原串不抛。
- 应用点：① Imap `fetchEnvelopeHeaders` Subject 读取（新收信唯一行为差异，落库即解码）；② 会话 service `latestMessage.subject`（summary）与 timeline `subject`（含历史 OUTBOUND）读兼容解码——旧库不 UPDATE。
- 测试矩阵按计划逐字断言（UTF-8 Q 两段折叠、Windows-1252 Q、UTF-8 B、折叠空白、中文/英文普通文本、null/empty、未知 charset、损坏 encoded-word、普通下划线不被误改、HTML 字符原样）；`ContentBlockedMessage` 假消息证明解码不新增 getContent/附件流访问（contentAccesses=0、streamAccesses 保持 walk 既有 1 次）。

### T4
- `ConversationItemResponse.expertTags: List<String>? = null`（与 timeline.tags 完全分离）。
- service `currentPageExpertTags`：只对本页 id `ExpertContactRepository.findAllById` 一次；按合法层级分组、非空 ORCID 去重，每层 ≤1 次 `ExpertSearchService.searchByOrcidIds`（全页 ≤3 次，页空/无合法键 0 次）；按 (level, orcidId) 回填原 SQL 行序；tags trim/去空/去重保 ES 序、不截断；画像无 tags → []；无 ORCID/层级非法/画像缺失/该层异常 → null（不伪称 []、不跨层猜测）；异常按层捕获记 log，其它层与整页 200/排序/分页不受影响。只读 ES，不触发发现/补全/晋级，不改 ExpertSearchService。

## 变更文件清单（= 白名单 9 文件，无越界）

| # | 路径 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/.../mail/repository/MailboxConversationRepository.kt` | T1（filter/orderByClause/membership 括号与 keyword/recipientEmail/params） |
| 2 | `src/main/kotlin/.../mail/service/MailboxConversationService.kt` | T1 参数/校验 + T2 tags 批量 + T3 subject 解码 + T4 expertTags 投影（新增 InboundMailTagService/ExpertSearchService 依赖） |
| 3 | `src/main/kotlin/.../mail/controller/MailboxConversationController.kt` | recipientEmail/keyword 参数；DTO：`tags`、`expertTags` |
| 4 | `src/main/kotlin/.../mail/service/ImapMailReceiveService.kt` | fetchEnvelopeHeaders Subject 解码（T3，唯一新收信差异） |
| 5 | `src/main/kotlin/.../mail/service/MailSubjectDecoder.kt` | 新增纯函数 |
| 6 | `src/test/kotlin/.../mail/repository/MailboxConversationRepositoryIT.kt` | 新 IT x8（I-1 四专家矩阵/空页/keyword/recipientEmail/同信合取/括号 bypass/全范围聚合）；2 个旧排序 IT 按 I-1 改造；SqlCompat 增 ORDER BY 契约断言 |
| 7 | `src/test/kotlin/.../mail/controller/MailboxConversationControllerTest.kt` | @MockBean InboundMailTagService/ExpertSearchService + 默认投影桩；新用例 x11；参数校验扩展 |
| 8 | `src/test/kotlin/.../mail/service/ImapMailReceiveServiceTest.kt` | 新用例 x3（header 解码/写回往返/未知 charset）+ ContentBlockedMessage 假消息 |
| 9 | `src/test/kotlin/.../mail/service/MailSubjectDecoderTest.kt` | 新增纯函数矩阵 12 用例 |

## Commands（均为最终实现状态下新鲜执行）

| 命令 | 结果 | 证据 |
|---|---|---|
| `JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn test -Dtest=MailSubjectDecoderTest,ImapMailReceiveServiceTest,MailboxConversationControllerTest,MailboxConversationRepositorySqlCompatTest` | PASS exit 0，BUILD SUCCESS | Tests run: 31, Failures: 0, Errors: 0, Skipped: 1（Skipped=MailboxConversationControllerTest 类级 mysqlIt=false 门禁，既有设计；该类在下方 -Pmysql-it 轮全量执行） |
| `DB_URL='jdbc:mysql://127.0.0.1:3306/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true' JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn test -Pmysql-it -Dtest=MailboxConversationRepositoryIT` | PASS exit 0，BUILD SUCCESS | Tests run: 21, Failures: 0, Errors: 0, Skipped: 0（隔离容器 5016f88e1b86 / MySQL 8.0.36） |
| `JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn test`（全量） | PASS exit 0，BUILD SUCCESS（3:27） | JVM: Tests run 3255 / 0 / 0 / Skipped 9（238 类；9 skipped 为 mysqlIt/migrationIt/其它门禁类）；node: tests 733 / pass 733 / fail 0 |
| 追加证据轮：`DB_URL=...同上... mvn test -Pmysql-it -Dtest=MailboxConversationControllerTest` | PASS exit 0，BUILD SUCCESS | Tests run: 21, Failures: 0, Errors: 0, Skipped: 0（mysqlIt 门禁内的 T2/T3/T4 控制器用例全部执行） |

## EXPLAIN 测量（同版本隔离容器 8.0.36，30 专家 fixture=60 行 union，测量后已清理）

- 无筛选 page 查询：derived(u) ALL rows=60、ec eq_ref、mr ALL rows=30、imp ALL rows=30（整表扫描分支为既有 rangeUnion 形态，未新增索引/迁移）；EXPLAIN 墙钟 ~0.12s。
- keyword='meeting-z9'（正文命中 1 封）page 查询：EXISTS 子查询沿既有外键走 ref——`mro` ref fk_mail_record_contact rows≈2 filtered 29.76%；`impi` ref fk_inbound_mail_processing_contact rows≈1 filtered 29.76%；LIKE 条件在 per-expert 候选内过滤。EXPLAIN 墙钟 ~0.16s。
- 说明：测量为与生产同形 SQL 的观察记录；按计划不引入索引/迁移，若大数据量不达预算须以实测修订（后续验证轮可复核）。

## Deviations

- 无计划偏离。仅测试实现层面的适配：
  1. mysql-it/ControllerTest 需 JDBC URL `allowPublicKeyRetrieval=true`（容器 MySQL 8 caching_sha2，环境连接参数，非行为变更）；
  2. ControllerTest 中 Mockito 对 Kotlin 非空参数不能使用返回 null 的 `any()`/`capture()` 匹配器（触发 NPE），改用精确参数/`anyList`/`anyLevel()`（注册 any(Class) 后返回非空哨兵）等非空匹配；
  3. mockMvc 响应按 UTF-8 读取（`String(contentAsByteArray, UTF_8)`），规避 MockHttpServletResponse 默认 ISO-8859-1 对中文/é 的显示乱码（仅测试读取层）。

## 不变量与下游接口核对

- I-1..I-6 均落到自动化用例（见 Task Status 与 IT/ControllerTest 用例名）；P1 来源/账号范围/计数/游标、P2 followed+waitingReply 兼容（旧 DTO/参数保留）、P3 附件仅元信息（resolveMessageAttachments 未动）、P4 既有处理/发件写服务零改动。GET 只读：无状态写、无 IMAP 调用、无 ES 写。
- child 02 契约已按计划逐字落地：summary `expertTags: List<String>?`（null=不可得、[]=已读无标签）；timeline 消息 `tags: List<TagView>`（OUTBOUND=[]，直出免 /thread）；无任意 orderBy 参数（服务端排序，全部=待处理优先+来信倒序+NULL 置底+id 稳定，关注/待处理=来信倒序）；recipientEmail/keyword 语义；waitingReply 参数与 DTO 保留。
- 主题解码仅影响：新收信落库 subject（头读取处）+ 会话 summary/timeline subject 读兼容。未回填历史库、未新增 getContent/附件访问、未改其它页面主题。

## Freshness

- Plan identity rechecked: YES（SHA-256 与执行前一致）
- Worktree identity rechecked: YES（root/branch/git-dir 未变，HEAD 351d69a）
- 变更文件全部在 9 文件白名单内：YES（git status 核对）
- Required commands run this invocation（最终状态）：YES（上表三条 + 追加 mysqlIt ControllerTest 轮）
- 未执行：push/merge/history 重写、后续 child 评审、格式化/全仓评审、未授权文件改动。

## Remaining Blocker

- None。

## Next Action

- READY_FOR_VERIFICATION → 运行 verify-p（child 01 独立验证）。

---
（提交信息见执行提交：`feat(fast-p): implement 01`；docs/plans/fast/** 未纳入该提交）
