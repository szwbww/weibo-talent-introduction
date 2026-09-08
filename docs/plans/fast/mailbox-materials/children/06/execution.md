# Child 06 执行报告 — 材料统一查询、所有权和文件就绪 API

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials/docs/plans/2026-09-07/06-shared-material-api.md`
- Plan SHA-256: `06f068972ee325e309a98f3764151cfcc71b0ae11e5690da92265930c24026fa`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials/docs/plans/2026-09-07/06-shared-material-api.md@06f068972ee325e309a98f3764151cfcc71b0ae11e5690da92265930c24026fa`
- Execution epoch: NEW
- Executor: Impl06
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials` @ branch `fast/mailbox-materials` @ git-dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-mailbox-materials`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials@fast/mailbox-materials@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-mailbox-materials`
- Base SHA (child 05 code head): `21dad8bf0573c21eec52cd9783967ae97222ce3d`；执行前 HEAD `8713ed11f948d5372eb6517834656e20aaf16843`（05 验证记录，docs-only）
- Result: **READY_FOR_VERIFICATION**
- Commit: `feat(fast-p): implement 06`（10 个授权文件；docs/plans/fast/** 未纳入提交）

## 变更文件（= 计划的 10 文件清单，逐字核对无增删）

| # | 文件 | 操作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt` | 新增（统一查询/所有权/就绪/transfers/reconcile/resolveMessageAttachments + 全部 DTO + 异常类型） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/document/controller/ExpertMaterialController.kt` | 新增（GET materials / POST transfers / POST reconcile + 限定 4 controller 的高优先级 advice） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt` | 修改（listDocuments 双 owner + mailRecordId 可空；download/preview 委托 resolveReadyFile，删除私有 realpath 旁路） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractor.kt` | 修改（委托 resolveReadyFile；两阶段：先全量就绪校验再读内容） |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentService.kt` | 修改（download 委托 resolveReadyFileUnscoped） |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialServiceTest.kt` | 新增（26 个 service 用例 + 同文件第二 top-level 类 ExpertMaterialControllerHttpTest 7 个 MockMvc 用例） |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt` | 修改（委托/双 owner/409 断言） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisServiceTest.kt` | 修改（新增：读取前失败不删除既有结果） |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt` | 修改（委托/两阶段/409） |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentServiceTest.kt` | 修改（委托/409） |

未触碰：MailboxService / MailRecordRepository / ExpertContactRepository / GlobalExceptionHandler / 任何迁移 / docs/plans/fast/**（controller-owned，未提交）。

## 命令证据（全部在工作树根，JAVA_HOME=zulu-11，最终代码状态）

| 命令 | 结果 | 证据 |
|---|---|---|
| `mvn test -Dtest=ExpertMaterialServiceTest,ExpertDocumentBrowseServiceTest,ExpertDocumentAnalysisServiceTest,DocumentTextExtractorTest,MailboxAttachmentServiceTest` | PASS（exit 0，BUILD SUCCESS） | ExpertMaterialServiceTest 33、ExpertDocumentBrowseServiceTest 15、ExpertDocumentAnalysisServiceTest 6、DocumentTextExtractorTest 6、MailboxAttachmentServiceTest 7 → 合计 **67 run / 0 fail / 0 err / 0 skip**（注：-Dtest 只匹配类名，同文件的 ExpertMaterialControllerHttpTest 单独 7 用例在另一次独立 -Dtest 运行与全量中通过） |
| `mvn test`（全量，docker-free） | PASS（exit 0，BUILD SUCCESS） | **3211 run / 0 fail / 0 err / 8 skipped**（8 skipped = 既有 mysqlIt/migrationIt 门禁 IT，按设计跳过；plain `mvn test` 全程无 Docker 依赖） |
| `mvn -o test -Dtest=ExpertMaterialControllerHttpTest` | PASS | 7 run / 0 fail / 0 err —— 409/429 走真实 advice 链（@WebMvcTest 同时装载 scoped advice 与 GlobalExceptionHandler） |

修复过程中曾出现并已闭环的失败（最终态全绿）：state 枚举校验入口在 listMaterials 而非装配层（测试改走入口断言）；歧义场景需 record 无账号 + ≥2 候选（测试夹具修正并新增「两账号同 messageId 不串件」用例）；Kotlin 非空参数把 any()/eq() 平台 null 判空导致 verify/stub NPE（沿用仓内 `?: LocalDateTime.now()` elvis 惯例与裸值 stub）。全部为本项测试自身的修正，无生产行为回退。

## 不变量核对

- **I-1 唯一读模型**：GET materials 只读 SQL 投影 + 文件核验；同一快照内过滤/排序/分页/统计（summary 恒为全体材料，40 条 4 页用例断言 summary.total=40 不受页影响）；GET 零副作用用例断言从未 enqueue/markRequested/写 doc；所有权链 = doc→attachment→唯一 owner→contact（resolveOwnerContact/resolveReadyFile）；两 host 同快照：重复调用返回相同 attachmentId/storageState，旧服务与新材料共用 resolveReadyFile 无旁路。
- **I-2 精确来源**：bridge（transfer.inbound_processing_id）→ 直接 owner（attachment.inbound_processing_id）→ 旧 record 严格唯一匹配（同账号/同专家/INBOUND/非空 messageId）；歧义拒绝（source=null + error SOURCE_AMBIGUOUS「来源待核对」），绝不展示其它账号文件；「两账号同 messageId 不串件」用例：仅归属本账号唯一候选，另一账号筛选 0 命中；sourceInboundId 永不作为 processing id（MAIL_RECORD 分支测试只按 record FK 取附件）。
- **I-3 显式动作与全部校验**：POST transfers 先登录（Session username）逐 ID 归属全量校验，任一外来 ID 整批拒绝（400，不入队）；1..500 去重；容量耗尽 429（AttachmentTransferQueueFullException）；入队委托 child-02 enqueueMaterial（事务内再次核验）。旧 download/preview/AI 未就绪统一 409 MATERIAL_NOT_READY（MaterialNotReadyException 带 attachmentId/state），MockMvc 通过真实 advice 链证明 409 生效（若被 GlobalExceptionHandler 抢先会是 500）；AI 两阶段：全所选就绪后才读任何文件内容；AnalysisServiceTest 新用例证明读取前失败（MaterialNotReady）从不 deleteAll/save 旧结果。
- **I-4 列表与状态口径**：page 从 0、size 默认 10 上限 100（入口校验）；total 受筛选；summary 恒全体；unknown actualSize=null（真实 0B 保留 0 用例）；encodedSize 独立于 actualSize；排序 receivedAt DESC, attachmentId DESC；传输失败不丢列表行（FAILED/SOURCE_UNAVAILABLE 行照常展示 + error{code,message}）。

## 关键设计决策/偏差（写入执行记录，供 verify-p 对照）

1. **@Service 显式 bean 名 `expertMaterialQueryService`（必要偏差）**：campaign 模块已存在同名 `campaign.service.ExpertMaterialService`（@Service，默认 bean 名同为 `expertMaterialService`）。若新类使用默认 bean 名，两个同名 BeanDefinition 会静默互相覆盖，导致 ExpertContactManagementController（campaign 类型注入）或新材料 controller 在启动/全 context 测试时缺 bean。类名/文件名保持计划字面（document/service/ExpertMaterialService.kt）；显式 bean 名不影响按类型注入（新 controller 按类型注入 document 类型）。这是为了满足计划的类名/文件名而必须的最小命名消歧，未改任何既有 bean。
2. **列表查询策略**：采用「一次参数化只读投影（本专家全部 doc/attachment/transfer/来源消息列）→ 内存装配（状态=transfer 行或真实文件核验、来源、能力）→ 过滤/排序/分页/统计」而非 SQL LIMIT/OFFSET 窗口。原因：存储状态对无 transfer 历史行依赖**真实文件存在性**（文件系统事实无法成为 SQL 谓词），SQL 窗口 + SQL 状态过滤会在「旧文件被外部删除」等边界产生计数/展示不一致；专家级材料行有界（≤1000 目录级压力），每行仅元数据不读文件字节，全部查询参数化、无 N+1。已在代码注释与本节记录。默认分析候选 defaultAnalysisAttachmentIds = 全体材料中 documentType∈{CV,PHD_DEGREE,MASTER_DEGREE,BACHELOR_DEGREE} 且 PDF/text 可分析格式且 storageState=METADATA_ONLY 的完整 ID 集（>500 不截断，UI 依据长度自行决定不默认勾选）。
3. **旧三服务的 content-type 私有解析保持不变**（计划只要求就绪判定委托统一方法）；新材料服务内部持有同一语义的私有 row 版解析（与三处既有副本同源）。未新增跨服务共享 API，未删既有副本（避免无谓测试churn）。
4. **resolveMessageAttachments 在本项提供并被 07 使用**（计划任务 3）：实现 bridge/直接 owner/严格唯一旧关系；旧关系候选查询与桥接附件查询使用服务内 NamedParameterJdbcTemplate 参数化 SQL（MailRecordRepository/MailAttachmentTransferRepository 均不在授权文件清单，未加方法）。旧 MailboxService 本阶段未改（07 接线）。
5. 409/429 响应体：`{code:"MATERIAL_NOT_READY",attachmentId,state,message}` / `{code:"TRANSFER_QUEUE_FULL",message}`；advice `@Order(Ordered.HIGHEST_PRECEDENCE)` + assignableTypes 限定 4 controller，GlobalExceptionHandler 零改动。
6. 修复语义：scoped resolveReadyFile 要求 doc.expertContactId==contact；owner（record/processing）归属为空（未绑定 processing）或等于 contact 时放行——绑定由 04 流程先于 doc 建立，且 doc 本身就是 contact 证据；错位（owner 是其它 contact）→ IllegalArgumentException（400，旧 browse 语义测试保持）。listDocuments 对 processing-owned doc 兼容（mailRecordId 输出 null，旧前端不消费该字段，已在 app.js 核验）。
7. 状态口径细节：有 transfer 行 → 显示该行 state 原样（STORED 后文件被外部删除的罕见情形 canDownload=false 但 storageState 仍 STORED，能力位诚实）；无 transfer 行 → 磁盘核验 STORED / SOURCE_UNAVAILABLE。MaterialNotReadyException.state 与列表口径一致。

## 剩余担忧

- 未做真实 MySQL 集成测试（本 child 授权文件无 mysqlIt-gated IT；master G-1 的 06 跨账号/归属 SQL 证明依赖 07 的 MySQL IT 与本计划的单元/受控 JDBC 覆盖）。SQL 方言按 MySQL（NamedParameterJdbcTemplate、FOR UPDATE、COALESCE），均在代码审查 + 参数断言层面覆盖，未在真实 MySQL 执行。
- `ExpertMaterialServiceTest.kt` 文件内含第二个 top-level 类 `ExpertMaterialControllerHttpTest`（@WebMvcTest）以满足「MockMvc 走真实 global advice 链」且不突破 10 文件边界；按类名运行两个类各自独立。
- eq(...) matcher + Kotlin 非空参数在 mock 打桩时的判空 NPE（仓内既有惯例）已用裸值/elvis 规避，未影响生产代码。

## Freshness

- Plan identity rechecked: YES（SHA 未变）
- Worktree identity rechecked: YES（branch/HEAD/git-dir 未变）
- Reported commit reachable from target branch: YES（提交后核对）
- Required commands run this invocation at final state: YES（两条均最新跑过，exit 0）
- Historical evidence used only as baseline: YES

## 提交后核对

- 提交 SHA：见提交输出；提交文件 = 上表 10 个（`git show --stat` 核对），docs/plans/fast/** 未包含。
- 未 push / merge / amend / rebase；HEAD 仍指向新提交且仅含本实现。
